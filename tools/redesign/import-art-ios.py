"""재디자인 V1 그림을 iOS 자산 목록(Assets.xcassets/World)에 넣는다. 다시 돌려도 같은 결과가 나온다.

사용: /usr/bin/python3 tools/redesign/import-art-ios.py [--check]
- 원본: .claude/scratch/redesign/art-final/<이름>.png (2000px 안팎, RGBA)
- 크기: 화면에 쓰는 가장 큰 크기(pt)의 3배 px 로 줄인다(계획서 redesign-v1 5-3, U3). 원본 2000px 를 그대로 넣지 않는다.
  기준 변(가로 w / 세로 h)과 pt 는 아래 SIZES 표 — 시안 V1(proto/v1/index.html) 의 가장 큰 쓰임새다.
  더 크게 쓰는 화면이 생기면 표의 pt 를 고치고 다시 돌린다.
- 줄일 때 알파를 곱한 값(RGBa)으로 리샘플링해 투명 가장자리에 색 번짐이 생기지 않게 한다.
- 이미지 세트는 기존 Games 폴더와 같은 방식(universal, 1x 한 장, 폴더 이름공간 World/) — 코드는 WorldArt 로 부른다.
- --check: 파일을 쓰지 않고 자산 목록의 그림이 표의 크기와 같은지만 확인한다(다르면 종료 코드 1).
"""
import json
import os
import sys

from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SRC = os.path.join(ROOT, ".claude", "scratch", "redesign", "art-final")
DST = os.path.join(ROOT, "native-ios", "Gugu", "Resources", "Assets.xcassets", "World")
SCALE = 3

# 이름: (기준 변, 가장 큰 pt, 어디서)
SIZES = {
    "tab_home": ("h", 36, "탭 바 켜진 탭 그림 높이 36"),
    "tab_map": ("h", 36, "탭 바 켜진 탭 그림 높이 36"),
    "tab_treasure": ("h", 36, "탭 바 켜진 탭 그림 높이 36"),
    "icon_key": ("h", 46, "보호자 문 카드 머리 46 (열쇠 단추 18, 보호자 부르기 28)"),
    "icon_book": ("h", 42, "섬 시트 배우기 타일 42"),
    "icon_trophy": ("h", 30, "보물 배지 메달 30 (머리 26, 게임 문 최고 기록 18)"),
    "house_gugu": ("w", 118, "홈 마당 집 폭 118"),
    "portal_stairs": ("w", 150, "홈 구구 계단 문 폭 150"),
    "portal_forest": ("w", 158, "홈 구구 몬스터 문 폭 158"),
    "isle_1": ("w", 112, "지도 섬 폭 112 (섬 시트 머리 76, 말풍선 64)"),
    "isle_2": ("w", 112, "지도 섬 폭 112"),
    "isle_3": ("w", 112, "지도 섬 폭 112 (섬 시트 머리 76, 말풍선 64)"),
    "isle_4": ("w", 112, "지도 섬 폭 112"),
    "isle_5": ("w", 112, "지도 섬 폭 112"),
    "isle_6": ("w", 112, "지도 섬 폭 112"),
}

FOLDER_JSON = {"info": {"author": "xcode", "version": 1}, "properties": {"provides-namespace": True}}


def target_size(src_w, src_h, side, pt):
    """기준 변을 pt × 3 px 로 맞추고 다른 변은 비율대로(반올림)"""
    px = pt * SCALE
    if side == "w":
        return px, max(1, round(src_h * px / src_w))
    return max(1, round(src_w * px / src_h)), px


def imageset_json(name):
    return {"images": [{"filename": f"{name}.png", "idiom": "universal", "scale": "1x"}],
            "info": {"author": "xcode", "version": 1}}


def write_json(path, value):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(value, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main(check):
    rows, bad = [], []
    if not check:
        os.makedirs(DST, exist_ok=True)
        write_json(os.path.join(DST, "Contents.json"), FOLDER_JSON)
    for name, (side, pt, where) in SIZES.items():
        src = Image.open(os.path.join(SRC, f"{name}.png"))
        if src.mode != "RGBA":
            raise SystemExit(f"{name}: RGBA 가 아님 ({src.mode})")
        w, h = target_size(src.width, src.height, side, pt)
        out_dir = os.path.join(DST, f"{name}.imageset")
        out_png = os.path.join(out_dir, f"{name}.png")
        if check:
            if not os.path.exists(out_png):
                bad.append(f"{name}: 없음")
                continue
            got = Image.open(out_png).size
            if got != (w, h):
                bad.append(f"{name}: {got} ≠ {(w, h)}")
        else:
            os.makedirs(out_dir, exist_ok=True)
            small = src.convert("RGBa").resize((w, h), Image.LANCZOS).convert("RGBA")
            small.save(out_png, optimize=True)
            write_json(os.path.join(out_dir, "Contents.json"), imageset_json(name))
        size_kb = os.path.getsize(out_png) / 1024 if os.path.exists(out_png) else 0
        rows.append(f"{name:14} {src.width}x{src.height} → {w}x{h} px ({side}={pt}pt×{SCALE}) {size_kb:6.1f}KB  {where}")
    print("\n".join(rows))
    if bad:
        print("크기 불일치:\n  " + "\n  ".join(bad))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main("--check" in sys.argv[1:]))
