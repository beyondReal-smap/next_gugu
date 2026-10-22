#!/usr/bin/env python3
"""일본어 UI 폰트(M PLUS Rounded 1c, OFL) 서브셋을 만들어 iOS 앱 번들에 넣는다.

시스템 Hiragino 는 SwiftUI 한 줄 글자 상자가 글자 크기와 거의 같아(행간 leading 이 빠진다) 위아래로 쌓인
글이 붙어 보였다. M+ Rounded 는 줄 높이가 1.395em 으로 SUITE(1.25em)와 비슷하고 둥근 글꼴이라 브랜드에도 맞는다.
전체 폰트는 굵기당 약 3.5MB 라 앱이 쓰는 글자만 남긴다 — 일본어 번역 문자 + 가나·전각 기호 전부 + ASCII.

일본어 번역을 바꾸면 다시 실행한다 (catalog.py check 가 빠진 글자를 잡아낸다).

사용
  python3 tools/i18n/subset_ja_font.py
"""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "native-ios/Gugu/Resources/Localizable.xcstrings"
OUT = ROOT / "native-ios/Gugu/Resources/Fonts"
# 원본 고정 — google/fonts 저장소의 이 커밋 (2026-05-01)
SOURCE = "https://raw.githubusercontent.com/google/fonts/84efd8ad78c3710ad14bd909e3bc407151885628/ofl/mplusrounded1c"
WEIGHTS = ("Light", "Regular", "Medium", "Bold", "ExtraBold", "Black")
# 번역에 없어도 화면에 나올 수 있는 글자 — 코드 안 일본어(九九 표·○×)는 Swift 파일에서 모은다
CODE_FILES = ("Core/JapaneseReading.swift", "Features/Session/OxPad.swift", "Core/AppLanguage.swift")


def ja_chars() -> set[str]:
    chars: set[str] = set()

    def walk(node) -> None:
        if isinstance(node, dict):
            for key, value in node.items():
                if key == "value" and isinstance(value, str):
                    chars.update(value)
                else:
                    walk(value)

    for entry in json.loads(CATALOG.read_text(encoding="utf-8"))["strings"].values():
        walk(entry.get("localizations", {}).get("ja", {}))
    for name in CODE_FILES:
        chars.update(c for c in (ROOT / "native-ios/Gugu" / name).read_text(encoding="utf-8") if ord(c) > 0x2FFF)
    blocks = [range(0x20, 0x7F), range(0x3000, 0x3100), range(0xFF01, 0xFF5F)]  # ASCII · 일본어 기호·가나 · 전각
    chars.update(chr(c) for block in blocks for c in block)
    chars.update("¥©®°±×÷−·•—–…→←↑↓★☆✓✗")
    return chars


def main() -> None:
    chars = ja_chars()
    with tempfile.TemporaryDirectory() as tmp:
        text_file = Path(tmp) / "chars.txt"
        text_file.write_text("".join(sorted(chars)), encoding="utf-8")
        for weight in WEIGHTS:
            name = f"MPLUSRounded1c-{weight}.ttf"
            source = Path(tmp) / name
            urllib.request.urlretrieve(f"{SOURCE}/{name}", source)
            subprocess.run([sys.executable, "-m", "fontTools.subset", str(source), f"--text-file={text_file}",
                            "--layout-features=*", f"--output-file={OUT / name}"], check=True)
            print(f"{name}: {(OUT / name).stat().st_size / 1024:.0f}KB")
    print(f"글자 {len(chars)}개")


if __name__ == "__main__":
    main()
