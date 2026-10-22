"""B-1 시안에서 배경만 지운 Android 적응형 아이콘 전경(투명 PNG)을 만든다 (gpt-image-2.5-sunburst)."""
import base64, os
import openai
from openai import OpenAI

HERE = os.path.dirname(os.path.abspath(__file__))
PROMPT = ("Reproduce this exact app icon artwork with the purple gradient background removed: keep the yellow tile with "
          "the white ×, the white tile with the digit 2 and the white tile with the digit 9 exactly as they are — same "
          "shapes, angles, colors, lighting and relative positions. Output only those three tiles on a fully transparent "
          "background. Keep only a very soft contact shadow between the tiles; no background color, no glow, no frame.")
if not os.environ.get("OPENAI_API_KEY"):
    raise SystemExit("OPENAI_API_KEY 가 없습니다")
client = OpenAI(timeout=300, max_retries=2)
try:
    with open(os.path.join(HERE, "concept-B-bold-tile-1.png"), "rb") as src:
        res = client.images.edit(model="gpt-image-2.5-sunburst", image=src, prompt=PROMPT, size="1024x1024",
                                 quality="high", background="transparent", output_format="png", n=2)
except openai.APIStatusError as e:
    raise SystemExit(f"API error {e.status_code}: {e.message}")
for i, im in enumerate(res.data, 1):
    path = os.path.join(HERE, f"B1-foreground-{i}.png")
    open(path, "wb").write(base64.b64decode(im.b64_json))
    print("saved", os.path.basename(path))
