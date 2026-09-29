"""앱이 소리 내어 읽는 모든 문장 목록 (음성 파일 생성용).

Core/KoreanReading(Swift·Kotlin)과 같은 규칙으로 만든다. 두 앱의 단위 테스트가
이 목록으로 만든 manifest.json 과 앱 쪽 문장을 대조하므로, 규칙이 어긋나면 테스트가 실패한다.

키 규칙
  q-{a}x{b}          문제 (학습·스피드런·챌린지·서바이벌)   "칠 곱하기 팔은?"
  m-{a}x{b}          빈칸 추리                              "오십육은 칠 곱하기 몇일까요?"
  ox-{a}x{b}-{shown} OX 퀴즈 (보여 주는 값별)                "칠 곱하기 팔은 오십육. 맞을까요?"
  c-{a}x{b}          구구단 외우기 한 줄 (끝에 마침표 — 억양)   "칠 팔은 오십육." (×2 는 "삼, 이는 육.")
"""

DIGITS = ["", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"]
UNITS = [(1000, "천"), (100, "백"), (10, "십")]
TABLES = range(2, 10)
MULTIPLIERS = range(1, 10)


def sino(n: int) -> str:
    if not 0 <= n < 10_000:
        raise ValueError(f"읽을 수 없는 수: {n}")
    if n == 0:
        return "영"
    s, rest = "", n
    for value, name in UNITS:
        q = rest // value
        if q > 0:
            s += ("" if q == 1 else DIGITS[q]) + name
        rest %= value
    if rest > 0:
        s += DIGITS[rest]
    return s


def has_batchim(word: str) -> bool:
    if not word:
        return False
    v = ord(word[-1])
    return 0xAC00 <= v <= 0xD7A3 and (v - 0xAC00) % 28 != 0


def topic(word: str) -> str:
    return word + ("은" if has_batchim(word) else "는")


def ox_shown_values(a: int, b: int) -> list[int]:
    """OX 퀴즈에 나올 수 있는 값 — 정답 + "한 끗 차이" 오답 후보 (Problems.makeStatement 와 같은 규칙)."""
    answer = a * b
    wrong = [a * (b + 1), a * (b - 1), (a + 1) * b, (a - 1) * b]
    return sorted({answer, *(w for w in wrong if w > 0 and w != answer)})


def phrases() -> dict[str, str]:
    out: dict[str, str] = {}
    for a in TABLES:
        for b in MULTIPLIERS:
            A, B, P = sino(a), sino(b), sino(a * b)
            out[f"q-{a}x{b}"] = f"{A} 곱하기 {topic(B)}?"
            # 숫자 바로 뒤에 '이'로 시작하는 말을 붙이지 않는다 — "십이일까요"는 "십일까요"로,
            # "십이 될까요"(10+이)는 12로 들린다 (음성 검수에서 확인)
            out[f"m-{a}x{b}"] = f"{topic(P)} {A} 곱하기 몇일까요?"
            for shown in ox_shown_values(a, b):
                out[f"ox-{a}x{b}-{shown}"] = f"{A} 곱하기 {topic(B)} {sino(shown)}. 맞을까요?"
            # 곱하는 수가 2면 쉼표로 끊는다 — 붙여 읽으면 '이'가 앞 숫자와 섞여 "삼위는"으로 들린다
            out[f"c-{a}x{b}"] = f"{A}{',' if b == 2 else ''} {topic(B)} {P}."
    return out


if __name__ == "__main__":
    items = phrases()
    kinds = {}
    for k in items:
        kinds[k.split("-")[0]] = kinds.get(k.split("-")[0], 0) + 1
    print("총", len(items), "문장", kinds, "글자수", sum(len(t) for t in items.values()))
    for k in ["q-7x8", "m-7x8", "ox-7x8-56", "ox-7x8-49", "c-7x8", "c-2x1", "q-4x9"]:
        print(k, "→", items[k])
