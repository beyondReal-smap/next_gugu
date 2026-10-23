"""스토어 소개 이미지 합성 — 헤드라인 + 기기 목업 + 브랜드 배경 (기존 스토어 이미지 스타일 계승).

사용: python3 compose.py <출력폴더>
원본: raw/{ios,ipad,android}/*.png  →  <출력>/{ios-phone,ios-ipad,play-phone}/NN-이름.png
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "raw")
FONTS = "/Users/genie/SmapSource/gugu/native-ios/Gugu/Resources/Fonts"
ICON = "/Users/genie/SmapSource/gugu/store-assets/icons/icon-1024.png"
OUT = sys.argv[1]

# 장별 카피·배경 (헤드라인 12자 이내, 과장 표현 금지)
SLIDES = {
    "home":    dict(title="매일 1분, 게임 한 판", sub="오늘의 목표와 연속 기록으로 습관이 돼요",
                    bg=("#6366F1", "#4338CA"), logo=True, glyphs="×9★"),
    "world":   dict(title="구구단이 모험이 된다", sub="3D 월드를 걸으며 주민과 구구단 대결!",
                    bg=("#34D399", "#15803D"), glyphs="2★×"),
    "battle":  dict(title="정답이 곧 공격!", sub="맞힐수록 콤보가 쌓이고 데미지가 커져요",
                    bg=("#FB923C", "#E11D48"), glyphs="×!3"),
    "session": dict(title="선생님이 읽어 줘요", sub="문제를 목소리로 들려줘요 · 인터넷 없이도",
                    bg=("#A78BFA", "#6D28D9"), glyphs="?=♪"),
    "table":   dict(title="표 보고, 소리로 외워요", sub="단별 외우는 비법과 소리로 외우기",
                    bg=("#FCD34D", "#F59E0B"), dark=True, glyphs="7×★"),
    "minigame": dict(title="달리며 푸는 구구단", sub="점프 · 레인 · 바구니, 움직이며 놀아요",
                     bg=("#2DD4BF", "#0F766E"), glyphs="×5?"),
    "learn":   dict(title="단 고르고 바로 시작", sub="오늘 추천 단부터 2~9단까지 한눈에",
                    bg=("#FDE68A", "#FBBF24"), dark=True, glyphs="60×"),
    "pop":     dict(title="풍선 팡팡, 구구단!", sub="미션에 맞는 풍선만 터뜨리고 보스를 물리쳐요",
                    bg=("#38BDF8", "#2563EB"), glyphs="★×8"),
    "report":  dict(title="성장이 눈에 보여요", sub="주간 리포트로 자주 틀린 문제까지 한눈에",
                    bg=("#334155", "#0F172A"), glyphs="★17"),
}

SETS = {
    # 출력명: (캔버스, 원본폴더, [(순번-이름, 원본파일, 슬라이드)], 기기 종류)
    "ios-phone": ((1320, 2868), "ios", [
        ("01-home", "01-home", "home"), ("02-world", "02-world", "world"), ("03-battle", "03-battle", "battle"),
        ("04-pop", "09-pop", "pop"), ("05-read-aloud", "04-session", "session"), ("06-table", "05-table", "table"),
        ("07-learn", "06-learn", "learn"), ("08-minigame", "07-minigame", "minigame"), ("09-report", "07-report", "report"),
    ], "phone"),
    "ios-ipad": ((2064, 2752), "ipad", [
        ("01-home", "01-home", "home"), ("02-world", "02-world", "world"), ("03-battle", "02b-battle", "battle"),
        ("04-pop", "07-pop", "pop"), ("05-read-aloud", "03-session", "session"), ("06-table", "05-table", "table"),
        ("07-report", "06-report", "report"),
    ], "tablet"),
    # Play 폰은 최대 8장 — 2.7.0 부터 미니게임 장 대신 구구 팡팡
    "play-phone": ((1080, 2400), "android", [
        ("01-home", "01-home", "home"), ("02-battle", "03-battle", "battle"), ("03-pop", "09-pop", "pop"),
        ("04-read-aloud", "04-session", "session"), ("05-table", "05-table", "table"), ("06-world", "02-world", "world"),
        ("07-learn", "06-learn", "learn"), ("08-report", "08-report", "report"),
    ], "android"),
}


def font(weight, size):
    return ImageFont.truetype(os.path.join(FONTS, f"SUITE-{weight}.ttf"), size)


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def gradient(w, h, top, bottom):
    a, b = hex_rgb(top), hex_rgb(bottom)
    col = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / (h - 1)
        col.putpixel((0, y), tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3)))
    return col.resize((w, h))


def tile(size, glyph, fill, glyph_fill, angle):
    """배경 모티프 — 둥근 타일 + 기호 (앱 아이콘의 3×3 타일 모티프)"""
    pad = size // 3
    im = Image.new("RGBA", (size + 2 * pad, size + 2 * pad), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((pad, pad, pad + size, pad + size), radius=size // 4, fill=fill)
    f = font("Heavy", int(size * 0.55))
    d.text((pad + size / 2, pad + size / 2 + size * 0.03), glyph, font=f, fill=glyph_fill, anchor="mm")
    return im.rotate(angle, resample=Image.BICUBIC, expand=False)


def rounded(im, radius):
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, im.width - 1, im.height - 1), radius=radius, fill=255)
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    out.paste(im, (0, 0), mask)
    return out


def device(shot, screen_w, kind):
    """스크린샷을 비율 그대로 기기 베젤 안에 넣는다 (왜곡 없음, 모서리 라운딩만)"""
    shot = shot.convert("RGB")
    screen_h = round(screen_w * shot.height / shot.width)
    screen = shot.resize((screen_w, screen_h), Image.LANCZOS)
    bezel = round(screen_w * (0.035 if kind != "tablet" else 0.028))
    r_screen = round(screen_w * (0.12 if kind == "phone" else 0.09 if kind == "android" else 0.04))
    body = Image.new("RGBA", (screen_w + 2 * bezel, screen_h + 2 * bezel), (0, 0, 0, 0))
    ImageDraw.Draw(body).rounded_rectangle((0, 0, body.width - 1, body.height - 1),
                                           radius=r_screen + bezel, fill=(17, 20, 32, 255))
    body.alpha_composite(rounded(screen.convert("RGBA"), r_screen), (bezel, bezel))
    if kind == "android":   # 펀치홀 카메라
        cx, cy, rr = body.width // 2, bezel + round(screen_w * 0.035), round(screen_w * 0.016)
        ImageDraw.Draw(body).ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=(17, 20, 32, 255))
    return body


def shadow(im, blur, alpha):
    sh = Image.new("RGBA", (im.width + 4 * blur, im.height + 4 * blur), (0, 0, 0, 0))
    a = im.split()[3].point(lambda v: v * alpha // 255)
    sh.paste((0, 0, 0, 255), (2 * blur, 2 * blur), a)
    return sh.filter(ImageFilter.GaussianBlur(blur))


def wrap_fit(text, weight, size, max_w):
    f = font(weight, size)
    while f.getlength(text) > max_w and size > 20:
        size -= 2
        f = font(weight, size)
    return f


def compose(canvas, shot_path, slide, kind):
    W, H = canvas
    s = SLIDES[slide]
    dark = s.get("dark", False)
    ink = (22, 27, 51) if dark else (255, 255, 255)
    sub_ink = (55, 62, 92) if dark else (255, 255, 255, 225)
    img = gradient(W, H, *s["bg"]).convert("RGBA")
    u = W / 1320 if kind != "tablet" else W / 2064 * 1.25   # 글자 크기 기준 단위

    # 배경 타일 모티프
    tint = (255, 255, 255, 46) if not dark else (255, 255, 255, 110)
    gtint = hex_rgb(s["bg"][1]) + (150,)
    spots = [(0.02, 0.50, 170, -14), (0.90, 0.40, 150, 12), (0.93, 0.78, 190, -8), (-0.02, 0.85, 150, 10)]
    for (fx, fy, size, ang), g in zip(spots, (s["glyphs"] * 2)[:4]):
        t = tile(round(size * u), g, tint, gtint, ang)
        img.alpha_composite(t, (round(fx * W - t.width / 2 + size * u / 2), round(fy * H - t.height / 2)))

    # 헤드라인
    y = round(150 * u) if kind != "tablet" else round(120 * u)
    if s.get("logo"):
        icon = rounded(Image.open(ICON).convert("RGBA").resize((round(96 * u),) * 2, Image.LANCZOS), round(22 * u))
        name_f = font("ExtraBold", round(50 * u))
        total = icon.width + round(20 * u) + name_f.getlength("구구 어드벤처")
        x0 = round((W - total) / 2)
        img.alpha_composite(icon, (x0, y))
        ImageDraw.Draw(img).text((x0 + icon.width + round(20 * u), y + icon.height / 2), "구구 어드벤처",
                                 font=name_f, fill=ink, anchor="lm")
        # 포인트 타일 (앱 아이콘의 노란 × 타일) — 헤드라인과 겹치지 않게 로고 줄 오른쪽 위
        acc = tile(round(110 * u), "×", (251, 191, 36, 255), (255, 255, 255, 255), -8)
        img.alpha_composite(acc, (round(W * 0.86 - acc.width / 2), round(y - acc.height * 0.25)))
        y += icon.height + round(44 * u)
    d = ImageDraw.Draw(img)
    tf = wrap_fit(s["title"], "Heavy", round(118 * u), W - round(140 * u))
    d.text((W / 2, y), s["title"], font=tf, fill=ink, anchor="ma")
    y += round(tf.size * 1.25)
    sf = wrap_fit(s["sub"], "SemiBold", round(52 * u), W - round(160 * u))
    d.text((W / 2, y), s["sub"], font=sf, fill=sub_ink, anchor="ma")
    y += round(sf.size * 1.9)

    # 기기 목업 — 아래로 조금 흘러내리게 배치
    shot = Image.open(shot_path)
    avail_h = H - y + (round(0.10 * H) if kind != "tablet" else round(0.06 * H))
    screen_w = round(min(W * (0.78 if kind != "tablet" else 0.80),
                         avail_h / (shot.height / shot.width) / 1.07))
    dev = device(shot, screen_w, kind)
    blur = round(40 * u)
    sh = shadow(dev, blur, 90)
    dx = round((W - dev.width) / 2)
    img.alpha_composite(sh, (dx - 2 * blur, y - 2 * blur + round(24 * u)))
    img.alpha_composite(dev, (dx, y))
    return img.convert("RGB")


if __name__ == "__main__":
    only = sys.argv[2] if len(sys.argv) > 2 else None
    for set_name, (canvas, src, items, kind) in SETS.items():
        if only and only != set_name:
            continue
        os.makedirs(os.path.join(OUT, set_name), exist_ok=True)
        for out_name, raw_name, slide in items:
            im = compose(canvas, os.path.join(RAW, src, raw_name + ".png"), slide, kind)
            path = os.path.join(OUT, set_name, out_name + ".png")
            im.save(path, optimize=True)
            print(set_name, out_name, im.size)
