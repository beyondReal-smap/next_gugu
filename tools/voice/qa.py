#!/usr/bin/env python3
"""음성 팩 기계 검수 — 모든 문장을 사람이 다 들을 수 없으니 이상한 줄만 골라낸다.

  1. 길이: 같은 종류 문장(q·m·ox·c) 안에서 음절당 길이가 튀는 줄 (말을 더 하거나 삼킴)
  2. 음량: 통합 음량(LUFS)이 가운데값보다 3LU 넘게 크거나 작은 줄
  3. 받아쓰기: Whisper 로 받아 쓴 문장이 원래 문장과 다른 줄 (숫자는 한글 수사로 바꿔 비교)

사용 (Apple Silicon + mlx-whisper 필요, 일본어는 pykakasi 도 — 저장소 의존성에는 넣지 않는다)
  python3 -m venv <venv> && <venv>/bin/pip install mlx-whisper pykakasi
  <venv>/bin/python tools/voice/qa.py [--lang en] [--report 결과.json]
hi·vi·id는 숫자 단어를 아라비아 숫자로 변환하지 않는다. 해당 표기 차이는 오류로 단정할 수 없다.
걸러진 줄은 직접 들어 보고 판단한다. 받아쓰기 차이는 인식 쪽 표기 차이일 수도 있다.
"""

from __future__ import annotations

import argparse
import json
import re
import statistics
import subprocess
import unicodedata
from pathlib import Path

from phrases import LANGS, cardinal, ja_number, sino

ROOT = Path(__file__).resolve().parents[2]
VOICE = ROOT / "native-ios/Gugu/Resources/Voice"
ASR_MODEL = "mlx-community/whisper-large-v3-turbo"
DURATION_Z = 3.5      # 음절당 길이의 robust z 한계
LOUDNESS_LU = 3.0     # 가운데값에서 벗어나도 되는 음량 폭


def run(cmd: list[str]) -> str:
    p = subprocess.run(cmd, capture_output=True, text=True)
    if p.returncode != 0:
        raise RuntimeError(f"{cmd[0]} 실패: {p.stderr[-500:]}")
    return p.stdout + p.stderr


def duration(path: Path) -> float:
    return float(run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)]))


def loudness(path: Path) -> float:
    out = run(["ffmpeg", "-hide_banner", "-nostats", "-i", str(path), "-af", "ebur128=framelog=quiet", "-f", "null", "-"])
    return float(re.findall(r"I:\s+(-?[\d.]+) LUFS", out)[-1])


def hangul(text: str) -> str:
    return re.sub(r"[^가-힣]", "", text)


def letters(text: str) -> str:
    return re.sub(r"[^a-z]", "", text.lower())


def kana(text: str) -> str:
    return re.sub(r"[^ぁ-ゖー]", "", text)


def units(text: str, lang: str) -> str:
    """길이·비교에 쓰는 글자 — 신규 언어는 숫자·유니코드 문자·결합 부호를 보존한다."""
    if lang in ("hi", "vi", "id"):
        return "".join(c for c in unicodedata.normalize("NFC", text.lower())
                       if unicodedata.category(c)[0] in "LMN")
    return {"ko": hangul, "en": letters, "ja": kana}[lang](text)


def _digits(text: str, read, limit: int) -> str:
    text = re.sub(r"(?<=\d),(?=\d{3})", "", text)
    return re.sub(r"\d+", lambda m: read(int(m.group())) if int(m.group()) < limit else m.group(), text)


_kakasi = None


def to_hiragana(text: str) -> str:
    """인식 결과(한자·가타카나 섞임)를 히라가나로 — 七 같은 한자 읽기는 사전 기준이라 九九 줄은 사람이 다시 듣는다"""
    global _kakasi
    if _kakasi is None:
        import pykakasi  # 선택 의존성 — 일본어 검수할 때만 필요
        _kakasi = pykakasi.kakasi()
    return "".join(item["hira"] for item in _kakasi.convert(text))


def normalize_heard(text: str, lang: str = "ko") -> str:
    """인식 결과의 숫자·곱셈 기호를 앱 문장과 같은 표기로 바꾼다."""
    if lang in ("hi", "vi", "id"):
        return units(text, lang)
    if lang == "en":
        t = re.sub(r"(?<=\d)\s*[xX*×]\s*(?=\d)", " times ", text)
        return letters(_digits(t, cardinal, 1000))
    if lang == "ja":
        # 숫자 사이의 × 만 "かける" — "×かな"(○×퀴즈)의 × 는 "ばつ"
        t = re.sub(r"(?<=\d)\s*[xX*×]\s*(?=\d|何)", " かける ", text)
        t = t.replace("×", "ばつ").replace("○", "まる").replace("〇", "まる")
        return kana(to_hiragana(_digits(t, ja_number, 1000)))
    t = text.replace("×", " 곱하기 ")
    t = re.sub(r"(?<=\d)\s*[xX*]\s*(?=\d)", " 곱하기 ", t)
    return hangul(_digits(t, sino, 10_000))


def edit_distance(a: str, b: str) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def robust_z(values: list[float]) -> list[float]:
    med = statistics.median(values)
    mad = statistics.median(abs(v - med) for v in values)
    if mad == 0:
        return [0.0 for _ in values]
    return [0.6745 * (v - med) / mad for v in values]


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--lang", choices=LANGS, default="ko")
    ap.add_argument("--report", type=Path, help="줄별 측정값 전체를 JSON 으로 저장")
    args = ap.parse_args()
    pack = VOICE / args.lang
    if args.lang in ("hi", "vi", "id"):
        print("검수 한계: 숫자 단어와 아라비아 숫자의 표기 차이는 직접 청취로 확인해야 합니다.")

    import mlx_whisper  # 선택 의존성 — 검수할 때만 필요

    lines: dict[str, str] = json.loads((pack / "voice-manifest.json").read_text())["lines"]
    rows = []
    for key, text in sorted(lines.items()):
        path = pack / f"{key}.m4a"
        rows.append({"key": key, "kind": key.split("-")[0], "text": text,
                     "dur": duration(path), "lufs": loudness(path)})
    print(f"측정 {len(rows)}개 — 받아쓰기 시작")

    for kind in sorted({r["kind"] for r in rows}):
        group = [r for r in rows if r["kind"] == kind]
        rates = [r["dur"] / len(units(r["text"], args.lang)) for r in group]
        for r, rate, z in zip(group, rates, robust_z(rates)):
            r["sec_per_syllable"], r["dur_z"] = round(rate, 4), round(z, 2)
    median_lufs = statistics.median(r["lufs"] for r in rows)

    for i, r in enumerate(rows, 1):
        out = mlx_whisper.transcribe(str(pack / f"{r['key']}.m4a"), path_or_hf_repo=ASR_MODEL, language=args.lang,
                                     temperature=0.0, condition_on_previous_text=False, verbose=None)
        r["heard"] = out["text"].strip()
        expected, heard = units(r["text"], args.lang), normalize_heard(r["heard"], args.lang)
        r["cer"] = round(edit_distance(expected, heard) / len(expected), 3)
        if i % 100 == 0:
            print(f"  받아쓰기 {i}/{len(rows)}")

    flagged = []
    for r in rows:
        reasons = []
        if abs(r["dur_z"]) > DURATION_Z:
            reasons.append(f"길이 z={r['dur_z']}")
        if abs(r["lufs"] - median_lufs) > LOUDNESS_LU:
            reasons.append(f"음량 {r['lufs']:.1f}LUFS (가운데 {median_lufs:.1f})")
        if r["cer"] > 0:
            reasons.append(f"받아쓰기 차이 {r['cer']:.0%}")
        if reasons:
            flagged.append((r, reasons))

    lufs = sorted(r["lufs"] for r in rows)
    print(f"\n음량 가운데 {median_lufs:.1f} LUFS · 범위 {lufs[0]:.1f} ~ {lufs[-1]:.1f}")
    print(f"받아쓰기 일치 {sum(r['cer'] == 0 for r in rows)}/{len(rows)}")
    print(f"걸러진 줄 {len(flagged)}개")
    for r, reasons in flagged:
        print(f"- {r['key']:12} {' · '.join(reasons)}\n    원문: {r['text']}\n    인식: {r['heard']}")
    if args.report:
        args.report.write_text(json.dumps(rows, ensure_ascii=False, indent=1))
        print(f"\n전체 측정값 → {args.report}")


if __name__ == "__main__":
    main()
