#!/usr/bin/python3
"""재디자인 V1 그림과 앱 캡처를 같은 높이로 나란히 붙인다 (계획서 .claude/plans/redesign-v1.md 6-3).

사용: /usr/bin/python3 tools/redesign/compare.py <ios|android> <캡처 폴더> [이름 …]
  캡처 폴더의 <이름>.png 마다 V1 의 .claude/scratch/redesign/proto/v1/shots/<이름>.png 를 찾아
  .claude/scratch/redesign/compare/<플랫폼>/<이름>.png 로 저장한다(왼쪽 V1, 오른쪽 앱).
  이름을 안 주면 캡처 폴더에서 V1 짝이 있는 그림을 전부 붙인다. V1 짝이 없는 캡처는 실패로 알린다.
  같은 폴더의 notes.md 에 짝마다 메모 칸을 만든다 — 이미 있는 칸(제목 줄)은 건드리지 않고 없는 칸만 뒤에 붙인다.

판정(6-3): 블록 순서, 누를 곳의 수와 자리, 색 역할(sun 은 시작하는 것에만), 그림 자리가 V1 과 같은지 — 픽셀 일치는 보지 않는다.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
V1_SHOTS = ROOT / ".claude/scratch/redesign/proto/v1/shots"
OUT_ROOT = ROOT / ".claude/scratch/redesign/compare"
HEIGHT = 1688          # V1 그림 높이(390pt × 2배 프레임) — 앱 캡처를 여기에 맞춘다
GAP = 24
LABEL_H = 56
BG = (240, 242, 248)
INK = (43, 45, 94)


def fit_height(image: Image.Image, height: int) -> Image.Image:
    image = image.convert("RGB")
    width = round(image.width * height / image.height)
    return image.resize((width, height), Image.LANCZOS)


def label_font() -> ImageFont.ImageFont:
    for path in ("/System/Library/Fonts/Supplemental/Arial Bold.ttf", "/System/Library/Fonts/Helvetica.ttc"):
        if Path(path).exists():
            return ImageFont.truetype(path, 30)
    return ImageFont.load_default()


def side_by_side(v1: Path, app: Path, platform: str, out: Path) -> None:
    left = fit_height(Image.open(v1), HEIGHT)
    right = fit_height(Image.open(app), HEIGHT)
    sheet = Image.new("RGB", (GAP + left.width + GAP + right.width + GAP, LABEL_H + HEIGHT + GAP), BG)
    sheet.paste(left, (GAP, LABEL_H))
    sheet.paste(right, (GAP + left.width + GAP, LABEL_H))
    draw = ImageDraw.Draw(sheet)
    font = label_font()
    draw.text((GAP, 12), f"V1  {v1.name}", fill=INK, font=font)
    draw.text((GAP + left.width + GAP, 12), f"APP {platform}  {app.name}", fill=INK, font=font)
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)


def ensure_notes(out_dir: Path, names: list[str], platform: str) -> Path:
    notes = out_dir / "notes.md"
    text = notes.read_text(encoding="utf-8") if notes.exists() else (
        f"# V1 비교 메모 ({platform})\n\n"
        "판정: 블록 순서 · 누를 곳의 수와 자리 · 색 역할(sun 은 시작하는 것에만) · 그림 자리. "
        "다른 점은 계획서 결정으로 설명되는지 적는다.\n"
    )
    for name in names:
        heading = f"## {name}"
        if heading not in text.splitlines():
            text += f"\n{heading}\n\n- 같음:\n- 다름:\n- 설명(계획서 결정):\n"
    notes.write_text(text, encoding="utf-8")
    return notes


def main(argv: list[str]) -> int:
    if len(argv) < 2 or argv[0] not in ("ios", "android"):
        print(__doc__)
        return 2
    platform, shots = argv[0], Path(argv[1])
    if not shots.is_dir():
        print(f"캡처 폴더가 없다: {shots}", file=sys.stderr)
        return 1
    names = argv[2:] or sorted(p.stem for p in shots.glob("*.png") if (V1_SHOTS / p.name).exists())
    if not names:
        print(f"V1 짝이 있는 캡처가 없다: {shots}", file=sys.stderr)
        return 1
    out_dir = OUT_ROOT / platform
    failed = []
    for name in names:
        v1, app = V1_SHOTS / f"{name}.png", shots / f"{name}.png"
        missing = [str(p) for p in (v1, app) if not p.exists()]
        if missing:
            failed.append(f"{name}: 없음 {', '.join(missing)}")
            continue
        out = out_dir / f"{name}.png"
        side_by_side(v1, app, platform, out)
        print(out)
    print(ensure_notes(out_dir, [n for n in names if not any(f.startswith(f"{n}:") for f in failed)], platform))
    if failed:
        print("짝을 못 만든 것:\n  " + "\n  ".join(failed), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
