"""구구 어드벤처 앱 아이콘 시안 생성 (OpenAI gpt-image-2.5).

캐릭터 참조가 필요한 안은 참조 충실도가 높은 sunburst, 나머지는 flare(2026-09 출시, gpt-image-2 보다 품질↑·속도↑).

사용: OPENAI_API_KEY=... python3 generate.py
출력: concept-<방향>-<n>.png (1024x1024, 불투명)
"""
import base64
import os
import sys
import time

import openai
from openai import OpenAI

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL_REF = "gpt-image-2.5-sunburst"   # 참조 이미지(캐릭터) 유지가 중요한 안
MODEL_GEN = "gpt-image-2.5-flare"      # 참조 없이 새로 그리는 안
REF = os.path.join(HERE, "ref-character.png")

COMMON = (
    "Square iOS app icon artwork, 1024x1024, full-bleed edge to edge. Do NOT draw rounded corners, borders, "
    "device frames or a drop shadow around the icon — the OS applies the mask. Opaque background, no transparency. "
    "Bold, simple, centered composition that stays readable at 60x60 pixels. Friendly, kid-safe (rated 4+), "
    "soft 3D clay / vinyl-toy rendering with gentle studio lighting. Brand palette: indigo-violet gradient background "
    "(#4F46E5 to #7C3AED), warm yellow accent (#FBBF24), white. No words, no letters, no app name; "
    "the only symbols allowed are a multiplication sign × and digits. "
)

CONCEPTS = {
    "A-character-tile": (True,
        "The game's player character from the reference image — a small rounded purple capsule body with big round "
        "googly eyes and a tiny green sprout on top — stands cheerfully next to (and slightly leaning on) a large "
        "glossy yellow rounded-square tile with a thick white × on it. Character and tile fill most of the frame."),
    "B-bold-tile": (False,
        "A single large glossy yellow rounded-square tile, slightly tilted, with a thick white multiplication sign × "
        "in the middle, floating over the indigo gradient. Two small white rounded tiles with indigo digits 2 and 9 "
        "peek out behind it at the top-left and bottom-right, partly hidden. Very minimal and graphic."),
    "C-adventure-island": (True,
        "A tiny floating grassy island seen from a 3/4 view, on it the game's player character from the reference "
        "image (purple capsule body, big googly eyes, green sprout) next to a yellow rounded tile with a white × that "
        "stands like a signpost or portal. A few small sparkles/stars. Indigo sky gradient behind. Island centered "
        "and large in the frame."),
    "D-times-mascot": (False,
        "A cute mascot made from a glossy yellow rounded-square tile whose face is a thick white × used as its mouth/"
        "nose area, with two big round googly eyes (white with black pupils) on top, tiny stubby arms raising a "
        "small white tile with the digit 9. Centered, large, cheerful expression, indigo-violet gradient background."),
}


def save(images, name):
    for i, im in enumerate(images, 1):
        path = os.path.join(HERE, f"concept-{name}-{i}.png")
        with open(path, "wb") as f:
            f.write(base64.b64decode(im.b64_json))
        print("  saved", os.path.basename(path))


def run(client, name, use_ref, prompt):
    for attempt in range(3):
        try:
            if use_ref:
                with open(REF, "rb") as ref:
                    res = client.images.edit(model=MODEL_REF, image=ref, prompt=COMMON + prompt + " Keep the character's "
                                             "look faithful to the reference.", size="1024x1024", quality="high", n=2)
            else:
                res = client.images.generate(model=MODEL_GEN, prompt=COMMON + prompt, size="1024x1024", quality="high",
                                             background="opaque", n=2)
            save(res.data, name)
            return
        except openai.RateLimitError as e:
            wait = 20 * (attempt + 1)
            print(f"  rate limited ({e}); retry in {wait}s")
            time.sleep(wait)
        except openai.APIStatusError as e:
            print(f"  API error {e.status_code}: {e.message}")
            raise
    raise SystemExit(f"{name}: 재시도 초과")


if __name__ == "__main__":
    if not os.environ.get("OPENAI_API_KEY"):
        raise SystemExit("OPENAI_API_KEY 가 없습니다")
    client = OpenAI(timeout=300, max_retries=1)
    only = sys.argv[1:] or list(CONCEPTS)
    for name in only:
        use_ref, prompt = CONCEPTS[name]
        print("generating", name, "(ref)" if use_ref else "")
        run(client, name, use_ref, prompt)
