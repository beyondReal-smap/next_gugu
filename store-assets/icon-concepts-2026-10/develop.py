"""기존 아이콘(3×3 숫자 타일 + 가운데 노란 ×)을 발전시킨 시안 (gpt-image-2.5-sunburst, 기존 아이콘 참조).

사용: OPENAI_API_KEY=... python3 develop.py [방향...]
"""
import base64, os, sys, time
import openai
from openai import OpenAI

HERE = os.path.dirname(os.path.abspath(__file__))
REF = os.path.join(HERE, "ref-current-icon.png")
COMMON = ("Evolve this existing app icon while keeping its identity: a 3x3 grid of rounded white tiles with indigo digits "
          "2 4 6 / 3 × 6 / 4 8 9 (multiplication-table numbers) and the center tile replaced by a yellow tile with a white ×, "
          "on an indigo-violet gradient. Keep exactly these digits in these positions, keep the palette (#4F46E5→#7C3AED "
          "background, white tiles, indigo digits, #FBBF24 yellow). Square 1024x1024, full-bleed, no rounded outer corners, "
          "no frame, no text other than the digits and ×, opaque background. ")
DIRECTIONS = {
    "E-depth": "Render it as soft glossy 3D clay tiles with thickness, gentle studio lighting and subtle shadows; the yellow "
               "× tile is slightly raised and tilted, popping out toward the viewer. Polished, premium, kid-friendly.",
    "F-legible": "Optimize for tiny sizes: make the tiles larger with smaller gaps so the grid fills about 85% of the icon, "
                 "make the digits much bolder and larger, increase contrast, keep the yellow × tile tilted. Clean and flat "
                 "with a very subtle depth.",
    "G-hero-times": "Keep the 3x3 grid but make the yellow × tile about 1.6x larger, tilted, overlapping the surrounding "
                    "tiles as the clear focal point; the white digit tiles slightly smaller and softer behind it. Soft 3D.",
    "H-eyes": "Keep the layout as is, but give the yellow × tile two big round googly eyes (white with black pupils) above "
              "the ×, so it looks like a friendly character peeking out of the grid. Soft 3D, cheerful.",
}

def run(client, name, prompt):
    for attempt in range(3):
        try:
            with open(REF, "rb") as ref:
                res = client.images.edit(model="gpt-image-2.5-sunburst", image=ref, prompt=COMMON + prompt,
                                         size="1024x1024", quality="high", n=2)
            for i, im in enumerate(res.data, 1):
                path = os.path.join(HERE, f"dev-{name}-{i}.png")
                open(path, "wb").write(base64.b64decode(im.b64_json))
                print("  saved", os.path.basename(path))
            return
        except openai.RateLimitError as e:
            print(f"  rate limited; retry in {20 * (attempt + 1)}s"); time.sleep(20 * (attempt + 1))
        except openai.APIStatusError as e:
            raise SystemExit(f"API error {e.status_code}: {e.message}")
    raise SystemExit(f"{name}: 재시도 초과")

if __name__ == "__main__":
    if not os.environ.get("OPENAI_API_KEY"):
        raise SystemExit("OPENAI_API_KEY 가 없습니다")
    client = OpenAI(timeout=300, max_retries=1)
    for name in (sys.argv[1:] or list(DIRECTIONS)):
        print("generating", name); run(client, name, DIRECTIONS[name])
