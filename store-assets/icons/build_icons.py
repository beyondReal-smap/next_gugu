"""앱 아이콘 v2(EG-1: 입체 3×3 타일 + 큰 노란 ×) → iOS·Android·스토어용 파일 생성.

사용: python3 build_icons.py
원본: ../icon-concepts-2026-10/merge-EG-1.png (전체 아트), EG1-foreground-1.png (배경 제거 전경, gpt-image-2.5-sunburst)
"""
import os
from PIL import Image, ImageDraw

ROOT = "/Users/genie/SmapSource/gugu"
SRC = os.path.join(ROOT, "store-assets/icon-concepts-2026-10")
ART = Image.open(os.path.join(SRC, "merge-EG-1.png")).convert("RGB")
FG = Image.open(os.path.join(SRC, "EG1-foreground-1.png")).convert("RGBA")
ANDROID_RES = os.path.join(ROOT, "native-android/app/src/main/res")
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

# 그라데이션 색 — 아트 네 귀퉁이에서 추출(위 왼쪽 → 아래 오른쪽 대각선)
def avg(box):
    return tuple(round(v) for v in ART.crop(box).resize((1, 1), Image.BOX).getpixel((0, 0)))
TOP = avg((0, 0, 40, 40))
BOTTOM = avg((984, 984, 1024, 1024))

def gradient(size):
    # 대각선(왼쪽 위 → 오른쪽 아래) 선형 그라데이션
    g = Image.new("RGB", (size, size))
    px = g.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * (size - 1))
            px[x, y] = tuple(round(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3))
    return g

def tiles(side):
    """전경 타일 묶음을 투명 테두리 없이 잘라 side×side 로 맞춘다 (반투명 잔여 픽셀은 경계 계산에서 제외)"""
    alpha = FG.split()[3].point(lambda a: 255 if a > 40 else 0)
    box = alpha.getbbox()
    crop = FG.crop(box)
    s = side / max(crop.size)
    return crop.resize((round(crop.width * s), round(crop.height * s)), Image.LANCZOS)

def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    print(os.path.relpath(path, ROOT), img.size, img.mode)

# 1) iOS — 1024 불투명 RGB (모서리는 OS 가 깎는다)
save(ART, os.path.join(ROOT, "native-ios/Gugu/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"))

# 2) 스토어·문서용 (Play 512, 웹/문서 공용 크기)
for size, name in ((1024, "icon-1024.png"), (512, "icon-512x512.png"), (192, "icon-192x192.png"), (180, "apple-touch-icon.png")):
    save(ART.resize((size, size), Image.LANCZOS), os.path.join(ROOT, "store-assets/icons", name))

# 3) Android 적응형 전경 — 108dp 캔버스, 3×3 격자가 원형 마스크의 보이는 영역(지름 72dp) 안에 들어가게 52dp 로 축소
for d, k in DENSITIES.items():
    canvas = round(108 * k)
    t = tiles(round(52 * k))
    fg = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
    fg.alpha_composite(t, ((canvas - t.width) // 2, (canvas - t.height) // 2))
    save(fg, os.path.join(ANDROID_RES, f"mipmap-{d}", "ic_launcher_foreground.png"))

    # 4) 레거시 사각 아이콘(API 25 이하) — 48dp, 전체 아트
    legacy = round(48 * k)
    save(ART.resize((legacy, legacy), Image.LANCZOS), os.path.join(ANDROID_RES, f"mipmap-{d}", "ic_launcher.png"))

    # 5) 레거시 원형 아이콘 — 그라데이션 원 + 원 안에 들어가는 타일
    bg = gradient(legacy).convert("RGBA")
    t = tiles(round(legacy * 0.66))
    bg.alpha_composite(t, ((legacy - t.width) // 2, (legacy - t.height) // 2))
    mask = Image.new("L", (legacy, legacy), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, legacy - 1, legacy - 1), fill=255)
    round_icon = Image.new("RGBA", (legacy, legacy), (0, 0, 0, 0))
    round_icon.paste(bg, (0, 0), mask)
    save(round_icon, os.path.join(ANDROID_RES, f"mipmap-{d}", "ic_launcher_round.png"))

print("gradient", "#%02X%02X%02X" % TOP, "→", "#%02X%02X%02X" % BOTTOM)
