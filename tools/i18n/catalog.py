#!/usr/bin/env python3
"""iOS 문자열 카탈로그(Localizable.xcstrings) 관리 — 원문 ko, 번역 en·ja.

  sync   앱을 빌드해 소스의 현지화 문자열(.stringsdata)을 카탈로그에 반영한다.
         명령줄 빌드는 Xcode 편집기와 달리 카탈로그 파일을 고치지 않으므로 이 단계가 따로 필요하다.
         소스에서 사라진 키는 지우고, 원문(ko) 항목은 모두 translated 로 채운다 — 원문 항목이 없으면 ko.lproj 표가 만들어지지 않고,
         iOS 는 표에 없는 키를 개발 지역(en) 표에서 찾으므로 한국어 기기에 영어가 나온다.
  apply  번역 JSON {"키": {"en": "...", "ja": "..."}} 을 카탈로그에 넣는다 (상태 needs_review —
         원어민 검수가 끝나면 translated 로 바꾼다). 카탈로그에 없는 키는 오류로 멈춘다.
  check  en·ja 번역이 빠진 키를 나열하고, 하나라도 있으면 실패한다 (출시 게이트).
  lint   Swift 소스의 한글 문자열 중 카탈로그 키와 맞지 않는 것(= 번역되지 않고 한국어로 새는 것)을 찾는다.
         String 타입 인자로 넘긴 문구는 자동 추출되지 않아 영어 화면에 한글이 남는다. 로그·개발용 오류는
         제외하고, 일부러 번역하지 않는 줄은 끝에 `// i18n:skip` 을 단다.

사용
  python3 tools/i18n/catalog.py sync
  python3 tools/i18n/catalog.py apply 번역.json
  python3 tools/i18n/catalog.py check
  python3 tools/i18n/catalog.py lint
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
IOS = ROOT / "native-ios"
CATALOG = IOS / "Gugu/Resources/Localizable.xcstrings"
LANGS = ("en", "ja", "hi", "vi", "id")
SIMULATOR_DEST = "generic/platform=iOS Simulator"


def load() -> dict:
    return json.loads(CATALOG.read_text(encoding="utf-8"))


def save(catalog: dict) -> None:
    # Xcode 가 쓰는 형식(" : " 구분, 2칸 들여쓰기)에 맞춰 diff 를 줄인다
    text = json.dumps(catalog, ensure_ascii=False, indent=2, sort_keys=True, separators=(",", " : "))
    CATALOG.write_text(text + "\n", encoding="utf-8")


def sync(derived: Path) -> None:
    build = subprocess.run(
        ["xcodebuild", "build", "-project", str(IOS / "Gugu.xcodeproj"), "-scheme", "Gugu",
         "-destination", SIMULATOR_DEST, "-derivedDataPath", str(derived), "-quiet"],
        capture_output=True, text=True)
    if build.returncode != 0:
        raise SystemExit(f"빌드 실패:\n{build.stdout[-3000:]}{build.stderr[-3000:]}")
    # 앱 타깃만 — 테스트 타깃 문자열은 번역 대상이 아니다
    data = sorted(derived.glob("Build/Intermediates.noindex/Gugu.build/*/Gugu.build/**/*.stringsdata"))
    if not data:
        raise SystemExit(".stringsdata 가 없습니다 — SWIFT_EMIT_LOC_STRINGS 설정을 확인하세요")
    before = set(load()["strings"])
    subprocess.run(["xcrun", "xcstringstool", "sync", str(CATALOG), "--stringsdata", *map(str, data)], check=True)
    catalog = load()
    # 소스에서 사라진 키는 지운다 — 남겨 두면 번역 검수 목록에 쓰지 않는 문구가 섞인다
    stale = sorted(k for k, v in catalog["strings"].items() if v.get("extractionState") == "stale")
    for key in stale:
        del catalog["strings"][key]
    fill_source(catalog)
    save(catalog)
    after = set(catalog["strings"])
    print(f"키 {len(after)}개 (추가 {len(after - before)}, 소스에서 사라져 삭제 {len(stale)})")
    for key in stale:
        print(f"  삭제: {key}")


def format_only(key: str) -> bool:
    """글자 없이 숫자·기호·서식 지정자뿐인 키 ("%lld / %lld", " = ") — 번역할 것이 없다.
    따옴표가 든 키는 언어마다 기호가 달라(“” / 「」) 번역 대상으로 둔다"""
    rest = SPECIFIER.sub("", key)
    return not re.search(r"[A-Za-z\uac00-\ud7a3\u3040-\u30ff\u4e00-\u9fff“”\"]", rest)


def fill_source(catalog: dict) -> None:
    """원문 언어 항목을 빠짐없이 translated 로 — 값이 없으면 키 자체가 원문이다.
    서식뿐인 키는 번역하지 않음(shouldTranslate false)으로 표시한다"""
    source = catalog["sourceLanguage"]
    for key, entry in catalog["strings"].items():
        if format_only(key):
            entry["shouldTranslate"] = False
            entry.pop("localizations", None)
            continue
        if entry.get("extractionState") == "stale" or not entry.get("shouldTranslate", True):
            continue
        loc = entry.setdefault("localizations", {})
        unit = loc.get(source, {}).get("stringUnit")
        if unit is None:
            if "variations" in loc.get(source, {}):
                raise SystemExit(f"원문 복수형은 아직 지원하지 않습니다: {key}")
            loc[source] = {"stringUnit": {"state": "translated", "value": key}}
        else:
            unit["state"] = "translated"


def apply(path: Path) -> None:
    translations = json.loads(path.read_text(encoding="utf-8"))
    catalog = load()
    strings = catalog["strings"]
    missing = [k for k in translations if k not in strings]
    if missing:
        raise SystemExit("카탈로그에 없는 키 (먼저 sync):\n" + "\n".join(f"  {k}" for k in missing))
    for key, values in translations.items():
        unknown = set(values) - set(LANGS)
        if unknown:
            raise SystemExit(f"지원하지 않는 언어 {sorted(unknown)}: {key}")
        entry = strings[key].setdefault("localizations", {})
        for lang, value in values.items():
            if isinstance(value, dict):
                # 복수형 — {"one": "...", "other": "..."}
                entry[lang] = {"variations": {"plural": {
                    form: {"stringUnit": {"state": "needs_review", "value": text}} for form, text in value.items()}}}
            else:
                entry[lang] = {"stringUnit": {"state": "needs_review", "value": value}}
    save(catalog)
    print(f"{len(translations)}개 키에 번역 반영")


def untranslated(entry: dict, lang: str) -> bool:
    """번역 없음 — 빈 값("")도 포함한다. iOS 는 빈 번역을 없는 것으로 보고 한국어 원문을 보여 준다"""
    loc = entry.get("localizations", {}).get(lang)
    if loc is None or ("stringUnit" not in loc and "variations" not in loc):
        return True
    return any(not text.strip() for text in translations(entry, lang))


SPECIFIER = re.compile(r"%(?:(\d+)\$)?(lld|ld|d|@|f|\.\d+f)")


def specifiers(text: str) -> list[str]:
    """서식 지정자 목록 — 위치 지정(%2$@)이 있으면 그 순서로 정렬해 원문과 비교한다"""
    found = [(int(pos) if pos else i + 1, kind) for i, (pos, kind) in enumerate(SPECIFIER.findall(text))]
    return [kind for _, kind in sorted(found)]


def translations(entry: dict, lang: str) -> list[str]:
    loc = entry.get("localizations", {}).get(lang, {})
    if "stringUnit" in loc:
        return [loc["stringUnit"]["value"]]
    plural = loc.get("variations", {}).get("plural", {})
    return [form["stringUnit"]["value"] for form in plural.values()]


def ja_font_gaps(live: dict) -> set[str]:
    """일본어 번역 글자 중 앱에 넣은 M+ 서브셋 폰트(굵기 전부)에 없는 것 — 있으면 그 글자만 시스템 폰트로 섞여 보인다"""
    from fontTools.ttLib import TTFont  # 검사할 때만 필요한 의존성

    fonts = sorted((IOS / "Gugu/Resources/Fonts").glob("MPLUSRounded1c-*.ttf"))
    if not fonts:
        raise SystemExit("일본어 폰트 서브셋이 없습니다 — tools/i18n/subset_ja_font.py 를 실행하세요")
    # 그림 문자(이모지·딩뱃)는 어떤 글꼴이든 iOS 가 Apple Color Emoji 로 그린다 — 검사하지 않는다
    def emoji(c: str) -> bool:
        cp = ord(c)
        return cp >= 0x1F000 or 0x2600 <= cp <= 0x27BF or 0x2B00 <= cp <= 0x2BFF

    chars = {c for entry in live.values() for text in translations(entry, "ja") for c in text
             if not c.isspace() and not emoji(c)}
    gaps: set[str] = set()
    for path in fonts:
        cmap = TTFont(path)["cmap"].getBestCmap()
        gaps |= {c for c in chars if ord(c) not in cmap}
    return gaps


def check() -> None:
    strings = load()["strings"]
    live = {k: v for k, v in strings.items()
            if v.get("extractionState") != "stale" and v.get("shouldTranslate", True)}
    gaps = {lang: sorted(k for k, v in live.items() if untranslated(v, lang)) for lang in LANGS}
    source_gaps = sorted(k for k, v in live.items()
                         if v.get("localizations", {}).get("ko", {}).get("stringUnit", {}).get("state") != "translated")
    if source_gaps:
        print(f"ko 원문 항목 누락 {len(source_gaps)}개 — sync 를 다시 실행하세요")
    for lang, keys in gaps.items():
        print(f"{lang}: {len(live) - len(keys)}/{len(live)} 번역됨")
        for key in keys:
            print(f"  빠짐: {key}")
    # 번역의 서식 지정자가 원문과 다르면 실행 중 값이 엉뚱하게 들어가거나 앱이 죽는다
    mismatched = []
    for key, entry in live.items():
        source = entry.get("localizations", {}).get("ko", {}).get("stringUnit", {}).get("value", key)
        for lang in LANGS:
            for text in translations(entry, lang):
                if specifiers(text) != specifiers(source):
                    mismatched.append(f"  {lang} 서식 불일치: {key!r} → {text!r}")
    print("\n".join(mismatched))
    missing_glyphs = ja_font_gaps(live)
    if missing_glyphs:
        print(f"일본어 폰트 서브셋에 없는 글자 {''.join(sorted(missing_glyphs))} — tools/i18n/subset_ja_font.py 를 다시 실행하세요")
    if any(gaps.values()) or mismatched or source_gaps or missing_glyphs:
        sys.exit(1)


HANGUL = re.compile(r"[\uac00-\ud7a3]")
# 번역 대상이 아닌 호출 — 개발 로그·단언 메시지
LINT_SKIP_CALLS = ("print(", "assertionFailure(", "preconditionFailure(", "fatalError(", "precondition(")
# 한국어 전용 규칙 파일 (언어별 분기에서 ko 일 때만 쓰인다)
LINT_SKIP_FILES = {"KoreanReading.swift"}


def swift_literals(line: str) -> list[str]:
    r"""한 줄의 Swift 문자열 리터럴 본문 — 보간 \(...) 는 괄호 짝을 맞춰 통째로 둔다"""
    out, i = [], 0
    while i < len(line):
        if line.startswith("//", i):
            break
        if line[i] != '"':
            i += 1
            continue
        i += 1
        body = []
        while i < len(line) and line[i] != '"':
            if line.startswith("\\(", i):
                depth, j = 1, i + 2
                while j < len(line) and depth:
                    depth += {"(": 1, ")": -1}.get(line[j], 0)
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


def literal_pattern(literal: str) -> re.Pattern:
    """리터럴 → 카탈로그 키와 비교할 정규식 (보간 자리는 서식 지정자 아무거나)"""
    # 보간 \\(...) 를 괄호 짝을 맞춰 잘라 낸다 (중첩 괄호 포함)
    parts, i, start = [], 0, 0
    while i < len(literal):
        if literal.startswith("\\(", i):
            depth, j = 1, i + 2
            while j < len(literal) and depth:
                depth += {"(": 1, ")": -1}.get(literal[j], 0)
                j += 1
            parts.append(literal[start:i])
            i = start = j
        else:
            i += 1
    parts.append(literal[start:])
    # 카탈로그 키에서 글자 % 는 %% 로 적힌다
    text = [re.escape(p.replace("\\n", "\n").replace('\\"', '"').replace("\\\\", "\\")).replace("%", "%%")
            for p in parts]
    return re.compile(r"%(?:\d+\$)?(?:lld|ld|d|@|f|\.\d+f)".join(text))


def lint() -> None:
    keys = [k for k, v in load()["strings"].items() if v.get("extractionState") != "stale"]
    leaks = []
    for path in sorted((IOS / "Gugu").rglob("*.swift")):
        if path.name in LINT_SKIP_FILES:
            continue
        for no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith("//") or "i18n:skip" in line or any(c in line for c in LINT_SKIP_CALLS):
                continue
            # 번역자용 설명(comment:)과 의미 키의 원문(defaultValue:)은 키가 아니다
            line = re.sub(r'(comment|defaultValue): "(?:[^"\\]|\\.)*"', "", line)
            for literal in swift_literals(line):
                if not HANGUL.search(literal):
                    continue
                pattern = literal_pattern(literal)
                if not any(pattern.fullmatch(k) for k in keys):
                    leaks.append(f"{path.relative_to(IOS)}:{no}: {literal}")
    print("\n".join(leaks))
    print(f"번역되지 않는 한글 문자열 {len(leaks)}개")
    if leaks:
        sys.exit(1)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    p_sync = sub.add_parser("sync")
    p_sync.add_argument("--derived-data", type=Path, help="DerivedData 경로 (기본: 임시 폴더)")
    p_apply = sub.add_parser("apply")
    p_apply.add_argument("translations", type=Path)
    sub.add_parser("check")
    sub.add_parser("lint")
    args = parser.parse_args()

    if args.cmd == "sync":
        if args.derived_data:
            sync(args.derived_data)
        else:
            with tempfile.TemporaryDirectory() as tmp:
                sync(Path(tmp))
    elif args.cmd == "apply":
        apply(args.translations)
    elif args.cmd == "lint":
        lint()
    else:
        check()


if __name__ == "__main__":
    main()
