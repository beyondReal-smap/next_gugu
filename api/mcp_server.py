"""구구단 콘텐츠 MCP 서버 — AI 클라이언트가 바로 쓸 수 있는 형태로 학습 데이터를 제공한다.

읽기 전용·인증 없음. main.py에서 /api/mcp 에 마운트되어
https://gugu.smap.site/api/mcp/ (streamable HTTP)로 공개된다.

데이터 원천은 web·api 공용 단일 소스인 ../content/*.json 이다.
facts.json은 `node content/build.mjs` 생성물이며, 낭독·난이도 규칙은 그쪽에만 있다
(여기서 다시 구현하면 두 곳이 어긋나므로 계산하지 않고 읽기만 한다).
"""

import json
import logging
import random
import re
from pathlib import Path
from typing import Any, Literal, Optional

from mcp.server import MCPServer
from pydantic import BaseModel, Field

log = logging.getLogger("gugu-api")

CONTENT_DIR = Path(__file__).resolve().parent.parent / "content"
SITE_URL = "https://gugu.smap.site"

MIN_TABLE, MAX_TABLE = 2, 9
MIN_B, MAX_B = 1, 9


def _load(name: str) -> dict[str, Any]:
    """콘텐츠 JSON 로드. 파일이 없으면 기동 시점에 실패시킨다(fail-fast)."""
    path = CONTENT_DIR / name
    with path.open(encoding="utf-8") as f:
        return json.load(f)


# 앱 수명주기 동안 1회 로드 — 요청마다 디스크를 읽지 않는다
SITE = _load("site.json")
TABLES_DOC = _load("tables.json")
MODES_DOC = _load("modes.json")
FAQ_DOC = _load("faq.json")
PATH_DOC = _load("learning-path.json")
FACTS_DOC = _load("facts.json")

TABLES: dict[int, dict[str, Any]] = {t["table"]: t for t in TABLES_DOC["tables"]}
FACTS: dict[str, dict[str, Any]] = {f["id"]: f for f in FACTS_DOC["facts"]}
FACTS_BY_TABLE: dict[int, list[dict[str, Any]]] = {
    n: [f for f in FACTS_DOC["facts"] if f["a"] == n] for n in TABLES
}

ATTRIBUTION = SITE["license"]["attribution"]

mcp = MCPServer(
    name="gugu-content",
    title="구구 어드벤처 콘텐츠",
    version="1.0.0",
    website_url=SITE_URL,
    instructions=(
        "구구단(한국 초등 곱셈구구) 학습 데이터를 제공하는 읽기 전용 서버입니다. "
        "다루는 범위는 2단~9단, 곱하는 수 1~9로 총 72개 곱셈식입니다.\n\n"
        "무엇부터 부를지: 구구단에 대한 일반 질문은 search_gugu로 시작하세요. "
        "특정 단의 학습법·오답 패턴이 필요하면 get_table, 개별 곱셈식은 get_fact, "
        "연습 문제를 만들어야 하면 make_practice_set을 씁니다.\n\n"
        f"내용을 인용할 때는 출처를 {ATTRIBUTION} 로 표기해 주세요."
    ),
)


# ─────────────────────────────────────────────── 입력 검증


def _check_table(table: int) -> int:
    if not MIN_TABLE <= table <= MAX_TABLE:
        raise ValueError(f"table은 {MIN_TABLE}~{MAX_TABLE} 사이여야 합니다 (받은 값: {table})")
    return table


def _check_b(b: int) -> int:
    if not MIN_B <= b <= MAX_B:
        raise ValueError(f"곱하는 수는 {MIN_B}~{MAX_B} 사이여야 합니다 (받은 값: {b})")
    return b


# ─────────────────────────────────────────────── 출력 모델


class SearchHit(BaseModel):
    """검색 결과 한 건."""

    kind: Literal["fact", "table", "faq", "mode", "learning_step", "strategy", "mistake"]
    title: str
    content: str
    score: float = Field(description="관련도 점수. 높을수록 질의에 가깝습니다.")
    url: Optional[str] = Field(default=None, description="사람이 읽는 원문 페이지 주소")
    ref: Optional[str] = Field(default=None, description="다른 도구에 넘길 수 있는 식별자 (예: '7x8', 'table:7')")


class SearchResult(BaseModel):
    query: str
    hits: list[SearchHit]
    total: int
    attribution: str = ATTRIBUTION


class PracticeItem(BaseModel):
    index: int
    prompt: str = Field(description="학습자에게 보여 줄 문제 문장")
    answer: str = Field(description="정답. truefalse 모드에서는 'O' 또는 'X'")
    fact_id: str
    expression: str
    reading: str
    difficulty: str


class PracticeSet(BaseModel):
    mode: str
    table: Optional[int]
    count: int
    seed: int = Field(description="같은 seed를 넣으면 같은 문제 세트가 다시 나옵니다.")
    items: list[PracticeItem]
    note: str


# ─────────────────────────────────────────────── 검색

_TOKEN_RE = re.compile(r"[0-9]+|[가-힣]+|[a-zA-Z]+")
# "7x8", "7 × 8", "7*8" 형태의 곱셈식 질의
_FACT_RE = re.compile(r"(\d)\s*[x×*X곱]\s*(\d)")
# "7단", "7 단" 형태의 단 질의
_TABLE_RE = re.compile(r"(\d)\s*단")


def _tokens(text: str) -> list[str]:
    return [t for t in _TOKEN_RE.findall(text.lower()) if len(t) >= 1]


def _score(query_tokens: list[str], text: str, weight: float = 1.0) -> float:
    """토큰 포함 여부 기반 점수. 형태소 분석 없이 부분 문자열 일치로 계산한다.

    한국어는 조사가 붙어 완전 일치가 잘 안 되므로 부분 일치를 허용하되,
    2글자 이상 토큰만 부분 일치를 인정해 과매칭을 막는다.
    """
    low = text.lower()
    hit = 0.0
    for tok in query_tokens:
        if len(tok) >= 2 and tok in low:
            hit += 1.0
        elif tok in low.split():
            hit += 0.6
    return hit * weight


def _table_url(n: int) -> str:
    return f"{SITE_URL}/guide/{n}-dan"


def _search(query: str, limit: int) -> SearchResult:
    toks = _tokens(query)
    hits: list[SearchHit] = []

    # 1) 곱셈식 직접 질의 — "7x8", "7 × 8"
    for a_s, b_s in _FACT_RE.findall(query):
        a, b = int(a_s), int(b_s)
        f = FACTS.get(f"{a}x{b}") or FACTS.get(f"{b}x{a}")
        if f:
            hits.append(
                SearchHit(
                    kind="fact",
                    title=f["expression"],
                    content=f"{f['expression']} — 읽는 법 '{f['reading']}'. 난이도 {f['difficulty']}. "
                    f"교환법칙으로 {f['commutativePair'].replace('x', ' × ')}와 답이 같습니다.",
                    score=100.0,
                    url=_table_url(f["a"]),
                    ref=f["id"],
                )
            )

    # 2) 단 직접 질의 — "7단"
    for n_s in _TABLE_RE.findall(query):
        n = int(n_s)
        if n in TABLES:
            t = TABLES[n]
            hits.append(
                SearchHit(
                    kind="table",
                    title=f"{n}단",
                    content=f"{t['summary']} {t['pattern']}",
                    score=90.0,
                    url=_table_url(n),
                    ref=f"table:{n}",
                )
            )

    if toks:
        # 3) 단별 콘텐츠 — 요약·규칙·전략·오답 패턴·Q&A
        for n, t in TABLES.items():
            base = _score(toks, f"{t['name']} {t['summary']} {t['whyMatters']} {t['pattern']}", 3.0)
            if base > 0:
                hits.append(
                    SearchHit(kind="table", title=f"{n}단", content=f"{t['summary']} {t['pattern']}",
                               score=base, url=_table_url(n), ref=f"table:{n}")
                )
            for s in t["strategies"]:
                sc = _score(toks, f"{t['name']} {s['title']} {s['detail']}", 2.5)
                if sc > 0:
                    hits.append(
                        SearchHit(kind="strategy", title=f"{n}단 · {s['title']}", content=s["detail"],
                                   score=sc, url=_table_url(n), ref=f"table:{n}")
                    )
            for m in t["commonMistakes"]:
                f = FACTS[m["fact"]]
                sc = _score(toks, f"{f['expression']} {m['why']} {m['fix']} 오답 실수 헷갈", 2.5)
                if sc > 0:
                    hits.append(
                        SearchHit(
                            kind="mistake",
                            title=f"자주 틀리는 식 · {f['expression']}",
                            content=f"흔한 오답 {m['confusedWith']}. {m['why']} {m['fix']}",
                            score=sc, url=_table_url(n), ref=f["id"],
                        )
                    )
            for q in t["questions"]:
                sc = _score(toks, f"{q['q']} {q['a']}", 3.0)
                if sc > 0:
                    hits.append(
                        SearchHit(kind="faq", title=q["q"], content=q["a"], score=sc,
                                   url=_table_url(n), ref=f"table:{n}")
                    )

        # 4) 사이트 전역 FAQ
        for item in FAQ_DOC["faqs"]:
            sc = _score(toks, f"{item['q']} {item['a']}", 3.5)
            if sc > 0:
                hits.append(
                    SearchHit(kind="faq", title=item["q"], content=item["a"], score=sc,
                               url=f"{SITE_URL}/guide/faq", ref=item["id"])
                )

        # 5) 게임 모드
        for m in MODES_DOC["modes"]:
            sc = _score(toks, f"{m['name']} {m['tagline']} {m['detail']} {m['rule']} {m['bestFor']}", 2.0)
            if sc > 0:
                hits.append(
                    SearchHit(kind="mode", title=f"{m['name']} 모드", content=f"{m['rule']} {m['bestFor']}",
                               score=sc, url=f"{SITE_URL}/guide/modes", ref=m["id"])
                )

        # 6) 학습 순서
        for s in PATH_DOC["steps"]:
            sc = _score(toks, f"{s['table']}단 {s['goal']} {s['keyIdea']} 순서 로드맵", 2.0)
            if sc > 0:
                hits.append(
                    SearchHit(
                        kind="learning_step",
                        title=f"학습 순서 {s['step']}단계 · {s['table']}단",
                        content=f"{s['goal']} 핵심: {s['keyIdea']}. 예상 기간 {s['estimatedDays']}.",
                        score=sc, url=f"{SITE_URL}/guide", ref=f"table:{s['table']}",
                    )
                )

    # 같은 내용이 여러 경로로 잡히면 점수가 높은 쪽만 남긴다
    best: dict[tuple[str, str], SearchHit] = {}
    for h in hits:
        key = (h.kind, h.title)
        if key not in best or h.score > best[key].score:
            best[key] = h

    ordered = sorted(best.values(), key=lambda h: h.score, reverse=True)
    return SearchResult(query=query, hits=ordered[:limit], total=len(ordered))


# ─────────────────────────────────────────────── 도구


@mcp.tool(
    title="구구단 콘텐츠 검색",
    description=(
        "구구단 학습 콘텐츠 전체(단별 전략, 자주 틀리는 식, FAQ, 게임 모드, 학습 순서)를 검색합니다. "
        "'7단 외우는 법', '7x8', '구구단 순서', '아이가 못 외워요' 같은 자연어 질의를 그대로 넣으세요. "
        "구구단 관련 질문이면 이 도구부터 부르는 것이 가장 빠릅니다."
    ),
)
def search_gugu(query: str, limit: int = 5) -> SearchResult:
    if not query.strip():
        raise ValueError("query가 비어 있습니다")
    if not 1 <= limit <= 30:
        raise ValueError(f"limit은 1~30 사이여야 합니다 (받은 값: {limit})")
    return _search(query, limit)


@mcp.tool(
    title="단별 학습 데이터",
    description=(
        "특정 단(2~9)의 모든 학습 데이터를 반환합니다. 곱셈식 9개와 읽는 법, 규칙, 학습 전략, "
        "자주 틀리는 식과 해결법, 실생활 예시, 관련 단, Q&A, 권장 학습 단계가 포함됩니다."
    ),
)
def get_table(table: int) -> dict[str, Any]:
    _check_table(table)
    t = TABLES[table]
    return {
        **t,
        "facts": FACTS_BY_TABLE[table],
        "learningStep": next((s for s in PATH_DOC["steps"] if s["table"] == table), None),
        "readingRule": TABLES_DOC["readingRule"],
        "url": _table_url(table),
        "attribution": ATTRIBUTION,
    }


@mcp.tool(
    title="곱셈식 하나 조회",
    description=(
        "곱셈식 하나의 답, 한국어 읽는 법, 난이도, 교환법칙 짝을 반환합니다. "
        "그 식이 자주 틀리는 식으로 등록되어 있으면 흔한 오답과 해결법도 함께 옵니다."
    ),
)
def get_fact(a: int, b: int) -> dict[str, Any]:
    _check_table(a)
    _check_b(b)
    f = FACTS[f"{a}x{b}"]
    mistake = next(
        (m for m in TABLES[a]["commonMistakes"] if m["fact"] == f["id"]),
        None,
    )
    return {
        **f,
        "table": a,
        "tableSummary": TABLES[a]["summary"],
        "tablePattern": TABLES[a]["pattern"],
        "commonMistake": mistake,
        "url": _table_url(a),
        "attribution": ATTRIBUTION,
    }


@mcp.tool(
    title="연습 문제 세트 생성",
    description=(
        "구구단 연습 문제를 만들어 정답과 함께 반환합니다. "
        "quiz는 '7 × 8 = ?', missing은 '7 × □ = 56', truefalse는 참·거짓 판별 문제입니다. "
        "table을 지정하면 그 단만, 비우면 2~9단 전체에서 뽑습니다. "
        "min_difficulty를 4 이상으로 주면 오답률이 높은 어려운 식만 골라냅니다. "
        "seed를 넘기면 같은 세트를 다시 만들 수 있습니다."
    ),
)
def make_practice_set(
    count: int = 10,
    table: Optional[int] = None,
    mode: Literal["quiz", "missing", "truefalse"] = "quiz",
    min_difficulty: int = 1,
    max_difficulty: int = 5,
    seed: Optional[int] = None,
) -> PracticeSet:
    if not 1 <= count <= 72:
        raise ValueError(f"count는 1~72 사이여야 합니다 (받은 값: {count})")
    if not 1 <= min_difficulty <= 5 or not 1 <= max_difficulty <= 5:
        raise ValueError("난이도는 1~5 사이여야 합니다 (1=가장 쉬움, 5=가장 어려움)")
    if min_difficulty > max_difficulty:
        raise ValueError("min_difficulty가 max_difficulty보다 큽니다")

    pool = FACTS_BY_TABLE[_check_table(table)] if table is not None else FACTS_DOC["facts"]
    pool = [f for f in pool if min_difficulty <= f["difficultyScore"] <= max_difficulty]
    if not pool:
        raise ValueError("조건에 맞는 곱셈식이 없습니다. 난이도 범위나 단을 넓혀 주세요.")

    actual_seed = seed if seed is not None else random.randrange(1_000_000)
    rng = random.Random(actual_seed)

    # 풀보다 많이 요청하면 순환해서 채운다 (중복 출제 허용)
    chosen: list[dict[str, Any]] = []
    while len(chosen) < count:
        batch = pool[:]
        rng.shuffle(batch)
        chosen.extend(batch[: count - len(chosen)])

    items: list[PracticeItem] = []
    for i, f in enumerate(chosen, start=1):
        if mode == "quiz":
            prompt, answer = f"{f['a']} × {f['b']} = ?", str(f["answer"])
        elif mode == "missing":
            # 앞수와 뒷수 중 한쪽을 가린다
            if rng.random() < 0.5:
                prompt, answer = f"□ × {f['b']} = {f['answer']}", str(f["a"])
            else:
                prompt, answer = f"{f['a']} × □ = {f['answer']}", str(f["b"])
        else:  # truefalse — 절반은 값이 가까운 그럴듯한 오답을 보여 준다
            if rng.random() < 0.5:
                prompt, answer = f"{f['a']} × {f['b']} = {f['answer']}", "O"
            else:
                offset = rng.choice([-6, -4, -3, -2, 2, 3, 4, 6])
                shown = max(1, f["answer"] + offset)
                prompt = f"{f['a']} × {f['b']} = {shown}"
                answer = "O" if shown == f["answer"] else "X"
        items.append(
            PracticeItem(
                index=i, prompt=prompt, answer=answer, fact_id=f["id"],
                expression=f["expression"], reading=f["reading"], difficulty=f["difficulty"],
            )
        )

    return PracticeSet(
        mode=mode,
        table=table,
        count=len(items),
        seed=actual_seed,
        items=items,
        note=(
            "정답이 함께 들어 있으니 학습자에게 그대로 보여 주지 마세요. "
            f"같은 문제를 다시 만들려면 seed={actual_seed}를 넘기세요."
        ),
    )


@mcp.tool(
    title="게임 모드 목록",
    description="구구 어드벤처의 게임 모드 6종과 규칙, 그리고 레벨·별·스트릭·오답 가중 출제 등 진행 시스템을 반환합니다.",
)
def list_modes() -> dict[str, Any]:
    return {
        "modes": MODES_DOC["modes"],
        "progression": MODES_DOC["progression"],
        "url": f"{SITE_URL}/guide/modes",
        "attribution": ATTRIBUTION,
    }


@mcp.tool(
    title="학습 로드맵",
    description=(
        "2~9단을 난이도와 상호 의존 관계에 따라 재배열한 8단계 학습 순서를 반환합니다. "
        "단 번호 순서가 아니라 2→5→3→4→6→9→7→8 순서이며, 단계별 목표·핵심 아이디어·예상 기간과 "
        "보호자를 위한 조언이 포함됩니다."
    ),
)
def get_learning_path() -> dict[str, Any]:
    return {**PATH_DOC, "url": f"{SITE_URL}/guide", "attribution": ATTRIBUTION}


@mcp.tool(
    title="자주 묻는 질문",
    description="구구단 학습 관련 자주 묻는 질문을 반환합니다. query를 주면 관련된 항목만 골라 냅니다.",
)
def search_faq(query: Optional[str] = None, limit: int = 10) -> dict[str, Any]:
    if not 1 <= limit <= 50:
        raise ValueError(f"limit은 1~50 사이여야 합니다 (받은 값: {limit})")
    items = FAQ_DOC["faqs"]
    if query and query.strip():
        toks = _tokens(query)
        scored = [(item, _score(toks, f"{item['q']} {item['a']}")) for item in items]
        items = [i for i, s in sorted(scored, key=lambda x: x[1], reverse=True) if s > 0]
    return {
        "categories": FAQ_DOC["categories"],
        "faqs": items[:limit],
        "total": len(items),
        "url": f"{SITE_URL}/guide/faq",
        "attribution": ATTRIBUTION,
    }


@mcp.tool(
    title="앱 정보",
    description="구구 어드벤처 앱의 소개, 지원 플랫폼, 가격, 주요 기능, 대상 사용자, 데이터 접근 경로를 반환합니다.",
)
def get_app_info() -> dict[str, Any]:
    return {
        "name": SITE["name"],
        "tagline": SITE["tagline"],
        "summary": SITE["summary"],
        "description": SITE["description"],
        "url": SITE["url"],
        "audience": SITE["audience"],
        "platforms": SITE["platforms"],
        "pricing": SITE["pricing"],
        "features": SITE["features"],
        "scope": SITE["scope"],
        "dataEndpoints": SITE["dataEndpoints"],
        "attribution": ATTRIBUTION,
    }


# ─────────────────────────────────────────────── 리소스


@mcp.resource(
    "gugu://dataset",
    name="구구단 전체 데이터셋",
    description="사이트 정보, 8개 단의 콘텐츠, 72개 곱셈식, 게임 모드, 학습 로드맵, FAQ를 모두 담은 단일 JSON.",
    mime_type="application/json",
)
def resource_dataset() -> str:
    payload = {
        "version": SITE["version"],
        "updated": SITE["updated"],
        "site": SITE,
        "readingRule": TABLES_DOC["readingRule"],
        "tables": [
            {**t, "facts": FACTS_BY_TABLE[t["table"]], "url": _table_url(t["table"])}
            for t in TABLES_DOC["tables"]
        ],
        "facts": FACTS_DOC["facts"],
        "modes": MODES_DOC["modes"],
        "progression": MODES_DOC["progression"],
        "learningPath": PATH_DOC,
        "faq": FAQ_DOC["faqs"],
    }
    return json.dumps(payload, ensure_ascii=False, indent=2)


@mcp.resource(
    "gugu://tables/{table}",
    name="단별 데이터",
    description="특정 단(2~9)의 곱셈식과 학습 전략, 자주 틀리는 식을 담은 JSON.",
    mime_type="application/json",
)
def resource_table(table: str) -> str:
    n = _check_table(int(table))
    return json.dumps(get_table(n), ensure_ascii=False, indent=2)


@mcp.resource(
    "gugu://learning-path",
    name="학습 로드맵",
    description="2~9단 8단계 학습 순서와 단계별 목표·기간.",
    mime_type="application/json",
)
def resource_learning_path() -> str:
    return json.dumps(PATH_DOC, ensure_ascii=False, indent=2)


@mcp.resource(
    "gugu://faq",
    name="자주 묻는 질문",
    description="구구단 학습 관련 질문과 답변 모음.",
    mime_type="application/json",
)
def resource_faq() -> str:
    return json.dumps(FAQ_DOC, ensure_ascii=False, indent=2)


log.info(
    "MCP 콘텐츠 로드 완료 — %d개 단, %d개 곱셈식, %d개 FAQ",
    len(TABLES), len(FACTS), len(FAQ_DOC["faqs"]),
)
