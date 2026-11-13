#!/usr/bin/env python3
"""Android 문자열 현지화 — iOS 카탈로그(Localizable.xcstrings)를 원본으로 strings.xml 을 만든다.

원본은 두 곳이다. 번역·검수 상태는 이 둘에만 둔다 (strings.xml 은 생성물, 직접 고치지 않는다).
  - native-ios/Gugu/Resources/Localizable.xcstrings  양 플랫폼 공통 문구
  - tools/i18n/android-only.json                      Android 에만 있는 문구 {"한국어 원문": {"en", "ja", "comment"}}
    (xcstrings 에 넣으면 catalog.py sync 가 Swift 소스에 없는 키로 보고 지운다)
리소스 이름은 tools/i18n/android-keys.json {"한국어 원문(=iOS 키)": "resource_name"} 에 고정한다.

  extract  Kotlin 소스의 한글 문자열 리터럴을 모은다 (로그·단언·KoreanReading.kt 제외)
  match    리터럴을 카탈로그 키와 대조해 일치 / 근사 / 없음 으로 나눈다 (M0 매칭률 측정)
  name     match 의 일치 키와 android-only.json 키에 리소스 이름을 붙여 android-keys.json 에 더한다
           (한 번 정한 이름은 바꾸지 않는다 — 코드가 R.string.<이름> 으로 참조한다)
  table    Kotlin 리터럴 → 리소스 이름·종류(string/plurals)·인자 순서 표 (코드 전환 작업용)
  gen      values/(en) · values-ko/ · values-ja/ 의 strings.xml 을 만든다
  check    세 언어 키 집합·빈 값·서식 지정자를 대조한다 (출시 게이트, 하나라도 어긋나면 실패)
  lint     Kotlin 에 남은 한글 리터럴(= 번역되지 않고 한국어로 새는 것)을 찾는다. 일부러 남기는 줄은 `// i18n:skip`

기본 values/ 는 영어다 — 미지원 언어 기기는 영어로 보인다. values-ko 에 키가 빠지면 한국어 기기에 영어가 나오므로
gen 은 세 파일을 항상 같은 키 집합으로 만든다.

사용
  python3 tools/i18n/android.py match [--detail]
  python3 tools/i18n/android.py name && python3 tools/i18n/android.py gen && python3 tools/i18n/android.py check
"""

from __future__ import annotations

import argparse
import difflib
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "native-ios/Gugu/Resources/Localizable.xcstrings"
ANDROID_ONLY = ROOT / "tools/i18n/android-only.json"
# 작업 묶음별 Android 전용 문구 — 병렬 작업이 같은 파일을 고치지 않게 나눠 둔다. 형식은 android-only.json 과 같고
# 항목에 "name"(리소스 이름)을 직접 적는다
ANDROID_ONLY_DIR = ROOT / "tools/i18n/android-only.d"
KEYS = ROOT / "tools/i18n/android-keys.json"
SRC = ROOT / "native-android/app/src/main/java"
RES = ROOT / "native-android/app/src/main/res"
OUT_DIRS = {"en": "values", "ko": "values-ko", "ja": "values-ja", "hi": "values-hi", "vi": "values-vi", "id": "values-in"}
# 코드가 쓰는 고정 리소스 — 카탈로그 밖 (언어 판별·앱 이름)
FIXED = {
    "app_name": {"ko": "구구 어드벤처", "en": "Gugu Adventure", "ja": "九九アドベンチャー", "hi": "गुगु एडवेंचर", "vi": "Gugu Phiêu lưu", "id": "Petualangan Gugu"},
    "app_language_code": {"ko": "ko", "en": "en", "ja": "ja", "hi": "hi", "vi": "vi", "id": "id"},
}

HANGUL = re.compile(r"[가-힣]")
# 번역 대상이 아닌 호출 — 개발 로그·단언 메시지
SKIP_CALLS = ("Log.", "log.", "println(", "check(", "require(", "error(", "checkNotNull(", "requireNotNull(",
              "IllegalStateException(", "IllegalArgumentException(")
# 한국어 전용 규칙 파일 (언어별 분기에서 ko 일 때만 쓰인다)
SKIP_FILES = {"KoreanReading.kt"}
SPECIFIER = r"%(?:\d+\$)?(?:lld|ld|d|@|f|\.\d+f)"


@dataclass
class Literal:
    path: Path
    line: int
    text: str        # 리터럴 본문 그대로 (템플릿 $x · ${...} 포함)

    @property
    def where(self) -> str:
        return f"{self.path.relative_to(SRC)}:{self.line}"


def kotlin_literals(line: str) -> list[str]:
    """한 줄의 Kotlin 문자열 리터럴 본문 — 템플릿 ${...} 는 중괄호 짝을 맞춰 통째로 둔다"""
    out, i = [], 0
    while i < len(line):
        if line.startswith("//", i):
            break
        if line[i] == "'":          # 문자 리터럴 '"' 건너뛰기
            end = line.find("'", i + 2 if line.startswith("'\\", i) else i + 1)
            i = end + 1 if end > 0 else i + 1
            continue
        if line[i] != '"':
            i += 1
            continue
        i += 1
        body = []
        while i < len(line) and line[i] != '"':
            if line.startswith("${", i):
                depth, j = 1, i + 2
                while j < len(line) and depth:
                    depth += {"{": 1, "}": -1}.get(line[j], 0)
                    j += 1
                body.append(line[i:j])
                i = j
            elif line[i] == "\\":
                body.append(line[i:i + 2])
                i += 2
            else:
                body.append(line[i])
                i += 1
        out.append("".join(body))
        i += 1
    return out


def extract() -> list[Literal]:
    found = []
    for path in sorted(SRC.rglob("*.kt")):
        if path.name in SKIP_FILES:
            continue
        for no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith(("//", "*", "/*")) or "i18n:skip" in line or any(c in line for c in SKIP_CALLS):
                continue
            found += [Literal(path, no, t) for t in kotlin_literals(line) if HANGUL.search(t)]
    return found


TEMPLATE = re.compile(r"\$\{(?:[^{}]|\{[^{}]*\})*\}|\$[A-Za-z_][A-Za-z0-9_.]*")


def unescape(text: str) -> str:
    return text.replace("\\n", "\n").replace('\\"', '"').replace("\\$", "$").replace("\\\\", "\\")


def literal_pattern(text: str) -> re.Pattern:
    """리터럴 → 카탈로그 키와 비교할 정규식 (템플릿 자리는 서식 지정자 아무거나)"""
    parts = TEMPLATE.split(text)
    # 카탈로그 키에서 글자 % 는 %% 로 적힌다
    return re.compile(SPECIFIER.join(re.escape(unescape(p)).replace("%", "%%") for p in parts))


def normalized(text: str, kotlin: bool) -> str:
    return re.sub(SPECIFIER, "◇", TEMPLATE.sub("◇", unescape(text)) if kotlin else text)


def catalog_keys() -> list[str]:
    strings = json.loads(CATALOG.read_text(encoding="utf-8"))["strings"]
    return [k for k, v in strings.items() if v.get("extractionState") != "stale"]


def match(detail: bool) -> None:
    keys = catalog_keys()
    norm_keys = {k: normalized(k, kotlin=False) for k in keys}
    exact, near, missing = [], [], []
    for lit in extract():
        pattern = literal_pattern(lit.text)
        hits = [k for k in keys if pattern.fullmatch(k)]
        if hits:
            exact.append((lit, hits))
            continue
        target = normalized(lit.text, kotlin=True)
        best = difflib.get_close_matches(target, norm_keys.values(), n=1, cutoff=0.75)
        if best:
            key = next(k for k, v in norm_keys.items() if v == best[0])
            near.append((lit, key))
        else:
            missing.append(lit)
    total = len(exact) + len(near) + len(missing)
    unique = lambda items: len({(lit.text if isinstance(lit, Literal) else lit[0].text) for lit in items})
    print(f"리터럴 {total}개 — 일치 {len(exact)}({unique(exact)}종) · 근사 {len(near)}({unique(near)}종) · "
          f"없음 {len(missing)}({unique(missing)}종)")
    ambiguous = [(lit, hits) for lit, hits in exact if len(hits) > 1]
    print(f"여러 키와 일치(모호) {len(ambiguous)}개")
    if detail:
        print("\n## 근사")
        for lit, key in near:
            print(f"{lit.where}: {lit.text!r}  ≈  {key!r}")
        print("\n## 없음")
        for lit in missing:
            print(f"{lit.where}: {lit.text!r}")
        print("\n## 모호")
        for lit, hits in ambiguous:
            print(f"{lit.where}: {lit.text!r} → {hits}")


# ---------------------------------------------------------------------------
# 원본 읽기

@dataclass
class Entry:
    key: str                       # 한국어 원문(=iOS 키) 또는 의미 키
    values: dict[str, object]      # 언어 → str 또는 {"one": str, "other": str} (복수형)
    translatable: bool = True
    comment: str = ""

    @property
    def plural(self) -> bool:
        return any(isinstance(v, dict) for v in self.values.values())


def _unit(loc: dict) -> object:
    if "stringUnit" in loc:
        return loc["stringUnit"]["value"]
    plural = loc.get("variations", {}).get("plural")
    if plural:
        return {q: f["stringUnit"]["value"] for q, f in plural.items()}
    raise SystemExit(f"알 수 없는 카탈로그 형식: {loc}")


def sources() -> dict[str, Entry]:
    """카탈로그 + android-only.json → 키별 ko·en·ja 값"""
    out: dict[str, Entry] = {}
    for key, v in json.loads(CATALOG.read_text(encoding="utf-8"))["strings"].items():
        if v.get("extractionState") == "stale":
            continue
        locs = v.get("localizations", {})
        translatable = v.get("shouldTranslate", True)
        values = {"ko": _unit(locs["ko"]) if "ko" in locs else key}
        for lang in ("en", "ja", "hi", "vi", "id"):
            if translatable and lang not in locs:
                raise SystemExit(f"번역 누락: {lang}: {key}")
            values[lang] = _unit(locs[lang]) if lang in locs else values["ko"]
        out[key] = Entry(key, values, translatable, v.get("comment", ""))
    for key, v in android_only().items():
        if key in out:
            raise SystemExit(f"android-only 키가 카탈로그에도 있습니다: {key!r}")
        out[key] = Entry(key, {"ko": v.get("ko", key), **{lang: v[lang] for lang in ("en", "ja", "hi", "vi", "id")}},
                         v.get("translatable", True), v.get("comment", ""))
    return out


def android_only() -> dict[str, dict]:
    files = ([ANDROID_ONLY] if ANDROID_ONLY.exists() else []) + sorted(ANDROID_ONLY_DIR.glob("*.json"))
    merged: dict[str, dict] = {}
    for path in files:
        for key, v in json.loads(path.read_text(encoding="utf-8")).items():
            if key in merged:
                raise SystemExit(f"Android 전용 문구가 두 번 정의됨: {key!r} ({path.name})")
            merged[key] = v
    return merged


def load_keys() -> dict[str, str]:
    return json.loads(KEYS.read_text(encoding="utf-8")) if KEYS.exists() else {}


def save_keys(keys: dict[str, str]) -> None:
    KEYS.write_text(json.dumps(dict(sorted(keys.items())), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


# ---------------------------------------------------------------------------
# 리소스 이름

def slug(text: str) -> str:
    text = re.sub(SPECIFIER, " ", text).lower()
    text = re.sub(r"[^a-z0-9]+", "_", text).strip("_")
    words, out = text.split("_"), ""
    for w in words:
        if len(out) + len(w) + 1 > 40:
            break
        out = f"{out}_{w}" if out else w
    return out


# Java·Kotlin 예약어는 리소스 이름이 될 수 없다 (R.string.false)
RESERVED = set("""abstract assert boolean break byte case catch char class const continue default do double else enum
extends false final finally float for goto if implements import instanceof int interface long native new null package
private protected public return short static strictfp super switch synchronized this throw throws transient true try
void volatile while as fun in is object typealias val var when""".split())


def resource_name(entry: Entry, taken: set[str]) -> str:
    en = entry.values["en"]
    base = slug(entry.key) if re.fullmatch(r"[a-z0-9_.]+", entry.key) else slug(en["other"] if isinstance(en, dict) else en)
    if not base or base[0].isdigit():
        base = f"s_{base}".rstrip("_")
    if base in RESERVED:
        base = f"{base}_label"
    name, n = base, 2
    while name in taken or name in FIXED:
        name, n = f"{base}_{n}", n + 1
    return name


def name() -> None:
    """카탈로그 전체 키와 Android 전용 문구에 리소스 이름을 정한다. 이름은 한 번 정하면 바꾸지 않는다 —
    코드에서 실제로 참조하는 이름만 gen 이 내보낸다."""
    keys, entries = load_keys(), sources()
    for key, v in android_only().items():     # 직접 적은 이름이 먼저
        if "name" in v:
            if keys.get(key, v["name"]) != v["name"]:
                raise SystemExit(f"리소스 이름 충돌: {key!r} = {keys[key]} / {v['name']}")
            keys[key] = v["name"]
    taken = set(keys.values())
    added = 0
    for key, entry in entries.items():
        if key in keys:
            continue
        keys[key] = resource_name(entry, taken)
        taken.add(keys[key])
        added += 1
    dupes = {n for n in keys.values() if list(keys.values()).count(n) > 1}
    if dupes:
        raise SystemExit(f"같은 리소스 이름이 여러 키에 붙었습니다: {sorted(dupes)}")
    save_keys(keys)
    print(f"리소스 이름 {added}개 추가 (전체 {len(keys)}개)")


REF = re.compile(r"\bR\.(string|plurals)\.([A-Za-z0-9_]+)")


def referenced() -> set[str]:
    """Kotlin 소스(앱·테스트)가 참조하는 리소스 이름"""
    names: set[str] = set()
    for path in [*SRC.rglob("*.kt"), *(SRC.parent.parent / "test").rglob("*.kt")]:
        names |= {m.group(2) for m in REF.finditer(path.read_text(encoding="utf-8"))}
    return names


def exported(keys: dict[str, str]) -> dict[str, str]:
    used = referenced()
    return {k: n for k, n in keys.items() if n in used}


# ---------------------------------------------------------------------------
# 서식 지정자 변환 · XML 이스케이프

SPEC = re.compile(r"%(?:(\d+)\$)?(lld|ld|d|@|\.\d+f|f)|%%")


def android_format(text: str) -> tuple[str, list[str]]:
    """iOS 서식 → Android 서식. 반환: (문자열, 위치별 인자 형식 목록)
    인자가 2개 이상이면 위치를 붙인다 (AAPT2 는 위치 없는 다중 치환을 빌드 오류로 본다)."""
    found = [m for m in SPEC.finditer(text) if m.group(0) != "%%"]
    multi = len(found) > 1
    types: dict[int, str] = {}
    out, last, seq = [], 0, 0
    for m in SPEC.finditer(text):
        out.append(text[last:m.start()])
        last = m.end()
        if m.group(0) == "%%":
            out.append("%%")
            continue
        seq += 1
        pos = int(m.group(1)) if m.group(1) else seq
        kind = {"lld": "d", "ld": "d", "d": "d", "@": "s"}.get(m.group(2), m.group(2))
        types[pos] = "s" if kind == "s" else ("d" if kind == "d" else "f")
        out.append(f"%{pos}${kind}" if multi else f"%{kind}")
    out.append(text[last:])
    result = "".join(out)
    if not found:
        result = result.replace("%%", "%")   # 인자 없이 부르면 서식 처리를 하지 않는다
    return result, [types[i] for i in sorted(types)]


def xml_text(text: str) -> str:
    text = text.replace("\\", "\\\\").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n").replace("\t", "\\t")
    if text.startswith(("@", "?")):
        text = "\\" + text
    # 앞뒤 공백·연속 공백은 aapt 가 접는다 — 큰따옴표로 감싸 보존
    if text != text.strip() or "  " in text:
        text = f'"{text}"'
    return text


# ---------------------------------------------------------------------------
# 생성

HEADER = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!-- 자동 생성 — 직접 수정 금지. tools/i18n/android.py gen -->\n"


def render(lang: str, keys: dict[str, str], entries: dict[str, Entry]) -> str:
    lines = [HEADER + "<resources>"]
    for res, values in FIXED.items():
        lines.append(f'    <string name="{res}">{xml_text(values[lang])}</string>')
    for key, res in sorted(keys.items(), key=lambda kv: kv[1]):
        entry = entries[key]
        if not entry.translatable and lang != "en":
            continue
        attrs = "" if entry.translatable else ' translatable="false"'
        if entry.comment and lang == "en":
            lines.append(f"    <!-- {entry.comment.replace('--', '—')} -->")
        value = entry.values[lang]
        if entry.plural:
            forms = value if isinstance(value, dict) else {"other": value}
            if lang != "en":
                forms = {"other": forms.get("other", next(iter(forms.values())))}
            lines.append(f'    <plurals name="{res}">')
            for q in ("zero", "one", "two", "few", "many", "other"):
                if q in forms:
                    text, _ = android_format(forms[q])
                    lines.append(f'        <item quantity="{q}">{xml_text(text)}</item>')
            lines.append("    </plurals>")
        else:
            text, types = android_format(value)
            if not types and "%" in text:
                attrs += ' formatted="false"'
            lines.append(f'    <string name="{res}"{attrs}>{xml_text(text)}</string>')
    lines.append("</resources>")
    return "\n".join(lines) + "\n"


def gen() -> None:
    all_keys, entries = load_keys(), sources()
    missing = [k for k in all_keys if k not in entries]
    if missing:
        raise SystemExit(f"android-keys.json 키가 원본에 없습니다 (name 을 다시 실행하거나 키를 지우세요): {missing[:10]}")
    unnamed = [k for k in entries if k not in all_keys]
    if unnamed:
        raise SystemExit(f"리소스 이름이 없는 키 {len(unnamed)}개 — name 을 먼저 실행하세요: {unnamed[:5]}")
    keys = exported(all_keys)
    for lang, folder in OUT_DIRS.items():
        path = RES / folder / "strings.xml"
        path.parent.mkdir(exist_ok=True)
        path.write_text(render(lang, keys, entries), encoding="utf-8")
        print(f"{path.relative_to(ROOT)}")


def check() -> None:
    all_keys, entries = load_keys(), sources()
    keys = exported(all_keys)
    problems = []
    known = set(all_keys.values()) | set(FIXED)
    problems += [f"코드가 참조하는 리소스가 원본에 없음: {n}" for n in sorted(referenced() - known)]
    for key, res in keys.items():
        entry = entries.get(key)
        if entry is None:
            problems.append(f"{res}: 원본에 키가 없음 {key!r}")
            continue
        base = None
        for lang in OUT_DIRS:
            value = entry.values[lang]
            forms = value.values() if isinstance(value, dict) else [value]
            for form in forms:
                if not form.strip():
                    problems.append(f"{res}: {lang} 빈 값")
                types = android_format(form)[1]
                if base is None:
                    base = types
                elif entry.translatable and types != base:
                    problems.append(f"{res}: {lang} 서식 지정자 {types} ≠ {base}")
            if entry.translatable and lang != "ko" and value == entry.values["ko"] and HANGUL.search(str(value)):
                problems.append(f"{res}: {lang} 번역 없음(원문 그대로)")
    # 생성물이 원본과 같은지 — gen 을 빠뜨리고 커밋하면 실패
    for lang, folder in OUT_DIRS.items():
        path = RES / folder / "strings.xml"
        if not path.exists() or path.read_text(encoding="utf-8") != render(lang, keys, entries):
            problems.append(f"{path.relative_to(ROOT)}: 생성물이 원본과 다름 — gen 을 다시 실행하세요")
    print("\n".join(problems))
    print(f"리소스 {len(keys)}개 · 문제 {len(problems)}개")
    if problems:
        sys.exit(1)


def table() -> None:
    keys, entries = load_keys(), sources()
    catalog = [k for k in keys if k in entries]
    for lit in extract():
        pattern = literal_pattern(lit.text)
        hits = [k for k in catalog if pattern.fullmatch(k)]
        if not hits:
            print(f"{lit.where}\t{lit.text}\t-")
            continue
        entry = entries[hits[0]]
        kind = "plurals" if entry.plural else "string"
        args = TEMPLATE.findall(lit.text)
        print(f"{lit.where}\t{lit.text}\tR.{kind}.{keys[hits[0]]}\targs={args}")


def lint() -> None:
    leaks = extract()
    for lit in leaks:
        print(f"{lit.where}: {lit.text}")
    print(f"번역되지 않는 한글 문자열 {len(leaks)}개")
    if leaks:
        sys.exit(1)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("extract")
    p_match = sub.add_parser("match")
    p_match.add_argument("--detail", action="store_true")
    for cmd in ("name", "table", "gen", "check", "lint"):
        sub.add_parser(cmd)
    args = parser.parse_args()
    if args.cmd == "extract":
        lits = extract()
        for lit in lits:
            print(f"{lit.where}: {lit.text}")
        print(f"한글 리터럴 {len(lits)}개", file=sys.stderr)
    elif args.cmd == "match":
        match(args.detail)
    else:
        {"name": name, "table": table, "gen": gen, "check": check, "lint": lint}[args.cmd]()


if __name__ == "__main__":
    main()
