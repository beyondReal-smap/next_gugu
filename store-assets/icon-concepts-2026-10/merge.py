"""E-2(입체 타일·굵은 숫자) + G-1(커진 노란 ×) 합친 시안 (gpt-image-2.5-sunburst, 두 시안 참조)."""
import base64, os
import openai
from openai import OpenAI

HERE = os.path.dirname(os.path.abspath(__file__))
PROMPT = (
    "Combine these two app icon designs into one. Use the FIRST image as the base and keep it almost identical: the same "
    "3x3 grid of glossy, thick 3D white tiles with bold indigo digits 2 4 6 / 3 × 6 / 4 8 9, the same lighting, the same "
    "indigo-violet gradient background and the same overall size and margins. Change only the center: like the SECOND "
    "image, make the yellow tile with the white × about 1.3x larger than the other tiles, slightly tilted (about 8 degrees), "
    "raised toward the viewer with a soft shadow, gently overlapping the edges of its four neighbors so it is the clear "
    "focal point — but keep every digit fully visible and readable. Square 1024x1024, full-bleed, no rounded outer corners, "
    "no frame, opaque background, no text other than the digits and ×."
)
if not os.environ.get("OPENAI_API_KEY"):
    raise SystemExit("OPENAI_API_KEY 가 없습니다")
client = OpenAI(timeout=300, max_retries=2)
done = 0
for batch in range(2):
    try:
        with open(os.path.join(HERE, "dev-E-depth-2.png"), "rb") as base, open(os.path.join(HERE, "dev-G-hero-times-1.png"), "rb") as hero:
            res = client.images.edit(model="gpt-image-2.5-sunburst", image=[base, hero], prompt=PROMPT,
                                     size="1024x1024", quality="high", n=2)
    except openai.APIStatusError as e:
        raise SystemExit(f"API error {e.status_code}: {e.message}")
    for im in res.data:
        done += 1
        path = os.path.join(HERE, f"merge-EG-{done}.png")
        open(path, "wb").write(base64.b64decode(im.b64_json))
        print("saved", os.path.basename(path))
