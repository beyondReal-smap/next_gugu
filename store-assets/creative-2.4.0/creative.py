"""App Store 크리에이티브 자산 — 제품 페이지 헤더(21:9) · 검색 결과(3:2).

사용: python3 creative.py
규격(App Store Connect Help, Creative assets specifications):
  헤더 21:9 3840x1646, 검색 결과 3:2 3840x2560 — JPEG/PNG, 알파 채널 금지.
가이드: 실제 UI, 초점은 가운데(기기별로 좌우가 잘림), 짧은 문구, 가격·URL·수상 표기 금지, 4+ 등급.
"""
import os
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(HERE, "..", "screenshots-2.3.0")
sys.argv = [sys.argv[0], HERE]          # compose.py 가 출력 폴더 인자를 읽는다 — 함수만 빌려 쓴다
sys.path.insert(0, SHOTS)
from compose import font, gradient, tile, rounded, device, shadow, ICON  # noqa: E402

RAW = os.path.join(SHOTS, "raw", "ios")
BG = ("#6366F1", "#3730A3")             # 홈 슬라이드·앱 아이콘과 같은 인디고 계열
TITLE = "구구단이 모험이 된다"
SUB = "3D 월드 · 배틀 · 미니게임으로 매일 1분"


def phone(name, screen_w):
    dev = device(Image.open(os.path.join(RAW, name + ".png")), screen_w, "phone")
    return dev


def place(img, dev, cx, top, u, angle=0):
    if angle:
        dev = dev.rotate(angle, resample=Image.BICUBIC, expand=True)
    blur = round(36 * u)
    sh = shadow(dev, blur, 100)
    x = round(cx - dev.width / 2)
    img.alpha_composite(sh, (x - 2 * blur, top - 2 * blur + round(26 * u)))
    img.alpha_composite(dev, (x, top))


def motifs(img, W, H, u, spots):
    tint, gtint = (255, 255, 255, 40), (55, 48, 163, 150)
    for fx, fy, size, ang, g in spots:
        t = tile(round(size * u), g, tint, gtint, ang)
        img.alpha_composite(t, (round(fx * W - t.width / 2), round(fy * H - t.height / 2)))


def headline(img, W, y, u, title_size, sub_size):
    d = ImageDraw.Draw(img)
    icon = rounded(Image.open(ICON).convert("RGBA").resize((round(88 * u),) * 2, Image.LANCZOS), round(20 * u))
    name_f = font("ExtraBold", round(46 * u))
    total = icon.width + round(18 * u) + name_f.getlength("구구 어드벤처")
    x0 = round((W - total) / 2)
    img.alpha_composite(icon, (x0, y))
    d.text((x0 + icon.width + round(18 * u), y + icon.height / 2), "구구 어드벤처", font=name_f,
           fill=(255, 255, 255), anchor="lm")
    y += icon.height + round(30 * u)
    tf = font("Heavy", round(title_size * u))
    d.text((W / 2, y), TITLE, font=tf, fill=(255, 255, 255), anchor="ma")
    y += round(tf.size * 1.22)
    sf = font("SemiBold", round(sub_size * u))
    d.text((W / 2, y), SUB, font=sf, fill=(224, 231, 255), anchor="ma")
    return y + round(sf.size * 1.7)


def header():
    """21:9 — 가운데 좁은 영역만 보여도 성립하게: 문구·주인공 화면(3D 월드)을 중앙에, 나머지는 양옆 장식"""
    W, H = 3840, 1646
    u = 1.0
    img = gradient(W, H, *BG).convert("RGBA")
    motifs(img, W, H, u, [(0.06, 0.30, 190, -12, "×"), (0.94, 0.26, 170, 10, "9"),
                          (0.12, 0.82, 150, 8, "★"), (0.89, 0.80, 200, -8, "2")])
    y = headline(img, W, round(70 * u), u, 112, 48)
    w = 470
    # 양옆 → 가운데 순으로 겹쳐 그린다 (가운데가 맨 위)
    for name, dx, dy, ang in (("07-report", -1260, 150, 6), ("07-minigame", 1260, 150, -6),
                              ("01-home", -660, 60, 3), ("03-battle", 660, 60, -3)):
        place(img, phone(name, w), W / 2 + dx, y + dy, u, ang)
    place(img, phone("02-world", 540), W / 2, y - 10, u)
    return img.convert("RGB")


def search():
    """3:2 — 검색 결과 카드: 문구 + 기기 3대(배틀 · 3D 월드 · 리포트)"""
    W, H = 3840, 2560
    u = 1.3
    img = gradient(W, H, *BG).convert("RGBA")
    motifs(img, W, H, u, [(0.07, 0.40, 170, -12, "×"), (0.93, 0.36, 160, 10, "9"),
                          (0.08, 0.86, 150, 8, "★"), (0.92, 0.88, 180, -8, "2")])
    y = headline(img, W, round(110 * u), u, 120, 52)
    for name, dx, dy, ang, w in (("03-battle", -980, 120, 4, 760), ("07-report", 980, 120, -4, 760),
                                 ("02-world", 0, 0, 0, 860)):
        place(img, phone(name, w), W / 2 + dx, y + dy, u, ang)
    return img.convert("RGB")


if __name__ == "__main__":
    for name, im in (("header-21x9-3840x1646.png", header()), ("search-3x2-3840x2560.png", search())):
        path = os.path.join(HERE, name)
        im.save(path, optimize=True)
        print(name, im.size, im.mode, os.path.getsize(path) // 1024, "KB")
