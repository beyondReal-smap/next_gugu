#!/usr/bin/env python3
"""ElevenLabs 로 앱 낭독 음성 팩을 만든다 (iOS·Android 공용).

사용
  python3 tools/voice/generate.py models                    # 쓸 수 있는 모델 보기
  python3 tools/voice/generate.py voices [--lang en]        # 그 언어 여성 목소리 후보 (라이브러리)
  python3 tools/voice/generate.py sample --lang en --voice ID ...  # 후보별 샘플 → tools/voice/samples/
  python3 tools/voice/generate.py build --lang en --voice ID       # 언어별 전체 문장 생성 → 앱 리소스로 배치
  python3 tools/voice/generate.py build --voice ID --regen c-3x2   # 특정 줄만 새로 녹음
  python3 tools/voice/generate.py sync-android --lang en           # iOS 음성 팩을 Android 로 그대로 복사 (생성·크레딧 없음)
  --lang 기본값은 ko. 두 앱 모두 언어별 폴더에 쓴다 — iOS Voice/{lang}/voice-manifest.json,
  Android assets/voice/{lang}/manifest.json (같은 파일, 매니페스트 이름만 다르다).
  (검수: tools/voice/qa.py — 길이·음량·받아쓰기로 이상한 줄을 골라낸다)

API 키: 환경변수 ELEVENLABS_API_KEY 또는 ~/.config/gugu/elevenlabs.env (ELEVENLABS_API_KEY=...)
생성 결과는 tools/voice/.cache 에 문장·목소리·설정별로 캐시해 다시 실행해도 크레딧을 또 쓰지 않는다.
"""

from __future__ import annotations

import argparse
import concurrent.futures as futures
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import time
from pathlib import Path

import requests

from phrases import LANGS, phrases

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
CACHE = HERE / ".cache"
SAMPLES = HERE / "samples"
IOS_VOICE = ROOT / "native-ios/Gugu/Resources/Voice"
ANDROID_VOICE = ROOT / "native-android/app/src/main/assets/voice"
# 앱마다 매니페스트 파일 이름이 다르다 (iOS 번들 리소스 / Android VoiceLinesTests·assets)
IOS_MANIFEST = "voice-manifest.json"
ANDROID_MANIFEST = "manifest.json"
API = "https://api.elevenlabs.io"

DEFAULT_MODEL = "eleven_v4"
# 밝은 여성 선생님 — 또박또박, 조금 느리게, 문장마다 톤이 크게 흔들리지 않게
BASE_SETTINGS = {"stability": 0.5, "similarity_boost": 0.8, "speed": 0.92}


def settings_for(model: str) -> dict:
    """v3·v4 는 style·speaker boost 를 받지 않는다 (models API 의 can_use_style/can_use_speaker_boost)."""
    if model == "eleven_multilingual_v2":
        return {**BASE_SETTINGS, "style": 0.25, "use_speaker_boost": True}
    return dict(BASE_SETTINGS)


# 앱에서 실제로 읽는 문장 종류별 샘플 (문제·외우기·빈칸·OX)
SAMPLE_TEXT = {
    "ko": "칠 곱하기 팔은?\n\n칠 팔은 오십육.\n\n칠 곱하기 몇이 오십육일까요?",
    "en": "What is seven times eight?\n\nSeven times eight is fifty-six.\n\nFifty-six is seven times what?\n\n"
          "Seven times eight is fifty-four. True or false?",
    "ja": "なな かける はち は？\n\nしちは ごじゅうろく。\n\nごじゅうろく は なな かける なに？\n\n"
          "ににんが し。さざんが く。くく はちじゅういち。",
}
for lang in LANGS:
    sample_lines = phrases(lang)
    keys = (["q-7x8", "c-7x8", "m-7x8", "ox-7x8-56"] if lang not in SAMPLE_TEXT else [])
    keys += ["q-19x19", "c-19x19", "m-19x19", "ox-19x19-380"]
    SAMPLE_TEXT[lang] = "\n\n".join(([SAMPLE_TEXT[lang]] if lang in SAMPLE_TEXT else [])
                                        + [sample_lines[key] for key in keys])

PCM_RATE = 24_000
AAC_BITRATE = 48_000
# 줄마다 체감 음량을 맞춘다 — 최고치 기준으로 맞췄을 때는 순간 튀는 소리 때문에 줄마다 최대 8.7LU 차이가 났다
TARGET_LUFS = -18.0
# 음량을 올린 줄의 순간 최고치는 리미터로 이 아래에 둔다
PEAK_CEILING_DB = -1.5


def api_key() -> str:
    key = os.environ.get("ELEVENLABS_API_KEY")
    if not key:
        env = Path.home() / ".config/gugu/elevenlabs.env"
        if env.exists():
            for line in env.read_text().splitlines():
                if line.strip().startswith("ELEVENLABS_API_KEY="):
                    key = line.split("=", 1)[1].strip().strip("'\"")
    if not key:
        sys.exit("ELEVENLABS_API_KEY 가 없습니다 — ~/.config/gugu/elevenlabs.env 에 저장해 주세요.")
    return key


def session() -> requests.Session:
    s = requests.Session()
    s.headers.update({"xi-api-key": api_key()})
    return s


def request(s: requests.Session, method: str, path: str, **kw) -> requests.Response:
    """429·5xx 는 지수 백오프로 재시도한다. 그 밖의 오류는 바로 실패시킨다."""
    for attempt in range(7):
        r = s.request(method, API + path, timeout=90, **kw)
        if r.status_code == 429 or r.status_code >= 500:
            wait = 2 ** attempt
            print(f"  … {r.status_code} 재시도 {attempt + 1} ({wait}s)", file=sys.stderr)
            time.sleep(wait)
            continue
        if not r.ok:
            raise RuntimeError(f"{method} {path} → {r.status_code}: {r.text[:500]}")
        return r
    raise RuntimeError(f"{method} {path} → 재시도 초과")


# MARK: 목록

def cmd_models(_args) -> None:
    s = session()
    for m in request(s, "GET", "/v1/models").json():
        if not m.get("can_do_text_to_speech"):
            continue
        langs = [l.get("language_id") for l in m.get("languages", [])]
        print(f"{m['model_id']:32} ko={'ko' in langs}  {m.get('name')}")


def candidates_path(lang: str) -> Path:
    return CACHE / ("candidates.json" if lang == "ko" else f"candidates-{lang}.json")


def cmd_voices(args) -> None:
    s = session()
    params = {"language": args.lang, "gender": "female", "page_size": 40, "sort": "trending"}
    if args.search:
        params["search"] = args.search
    if args.use_case:
        params["use_cases"] = args.use_case
    voices = request(s, "GET", "/v1/shared-voices", params=params).json().get("voices", [])
    CACHE.mkdir(parents=True, exist_ok=True)
    candidates_path(args.lang).write_text(json.dumps(voices, ensure_ascii=False, indent=1))
    for v in voices:
        desc = (v.get("description") or "").replace("\n", " ")[:70]
        print(f"{v['voice_id']}  {v.get('name')!s:28} age={v.get('age')} use={v.get('use_case')} "
              f"owner={v.get('public_owner_id')}  {desc}")


def ensure_voice(s: requests.Session, voice_id: str) -> None:
    """라이브러리 목소리는 내 목소리에 추가해야 API 로 쓸 수 있다."""
    r = s.get(f"{API}/v1/voices/{voice_id}", timeout=30)
    if r.ok:
        return
    candidates = [v for lang in LANGS if candidates_path(lang).exists()
                  for v in json.loads(candidates_path(lang).read_text())]
    match = next((v for v in candidates if v["voice_id"] == voice_id), None)
    if match is None:
        raise RuntimeError(f"목소리 {voice_id} 를 찾을 수 없습니다 — 먼저 `voices` 로 후보를 불러오세요.")
    print(f"  라이브러리 목소리 추가: {match.get('name')}")
    request(s, "POST", f"/v1/voices/add/{match['public_owner_id']}/{voice_id}",
            json={"new_name": f"gugu-{match.get('name')}"})


# MARK: 생성

def tts_pcm(s: requests.Session, voice: str, model: str, text: str, settings: dict) -> bytes:
    r = request(
        s, "POST", f"/v1/text-to-speech/{voice}",
        params={"output_format": f"pcm_{PCM_RATE}"},
        json={"text": text, "model_id": model, "voice_settings": settings},
    )
    return r.content


def cache_path(voice: str, model: str, text: str, settings: dict) -> Path:
    digest = hashlib.sha1(json.dumps([voice, model, text, settings], ensure_ascii=False, sort_keys=True).encode()).hexdigest()
    return CACHE / "pcm" / f"{digest}.pcm"


def run(cmd: list[str]) -> str:
    p = subprocess.run(cmd, capture_output=True, text=True)
    if p.returncode != 0:
        raise RuntimeError(f"{' '.join(cmd[:3])} 실패: {p.stderr[-800:]}")
    return p.stderr


def strip_free_atom(path: Path) -> None:
    """afconvert 가 moov 와 mdat 사이에 넣는 빈 공간(free atom, 파일마다 약 3KB)을 걷어 낸다.
    mdat 이 당겨지는 만큼 stco(청크 위치표)를 보정한다 — 음성 데이터·재생 정보(앞 지연 보정)는 그대로다."""
    data = path.read_bytes()
    top, i = [], 0
    while i < len(data):
        size, kind = struct.unpack_from(">I4s", data, i)
        if size < 8 or i + size > len(data):
            raise RuntimeError(f"{path.name}: 읽을 수 없는 atom 크기 {size}")
        top.append((kind.decode(), data[i:i + size]))
        i += size
    kinds = [k for k, _ in top]
    if kinds != ["ftyp", "moov", "free", "mdat"]:
        raise RuntimeError(f"{path.name}: 예상과 다른 m4a 구조 {kinds}")
    shift = len(top[2][1])
    moov = bytearray(top[1][1])
    at = moov.find(b"stco")
    if at < 0 or moov.find(b"stco", at + 4) >= 0:
        raise RuntimeError(f"{path.name}: 청크 위치표(stco)가 정확히 하나가 아닙니다")
    count = struct.unpack_from(">I", moov, at + 8)[0]
    for n in range(count):
        off = at + 12 + 4 * n
        struct.pack_into(">I", moov, off, struct.unpack_from(">I", moov, off)[0] - shift)
    path.write_bytes(top[0][1] + bytes(moov) + top[3][1])


def master(pcm: Path, out_m4a: Path) -> None:
    """앞뒤 무음 정리 → 체감 음량을 TARGET_LUFS 로 맞춤(최고치는 리미터로 제한) → 여백(앞 30ms·뒤 80ms) → AAC(m4a) → 빈 공간 제거."""
    work = out_m4a.with_suffix(".work.wav")
    trim = ("silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.02,"
            "areverse,silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.06,areverse")
    raw = ["-f", "s16le", "-ar", str(PCM_RATE), "-ac", "1", "-i", str(pcm)]
    stats = run(["ffmpeg", "-hide_banner", "-nostats", *raw, "-af", f"{trim},ebur128=framelog=quiet", "-f", "null", "-"])
    found = re.findall(r"I:\s+(-?[\d.]+) LUFS", stats)
    loudness = float(found[-1]) if found else None
    # 무음은 -70 LUFS(측정 하한)로 나온다 — 그대로 키우면 잡음만 커지므로 멈춘다
    if loudness is None or loudness < -50:
        raise RuntimeError(f"{pcm.name}: 음량을 잴 수 없습니다({loudness} LUFS, 무음이거나 깨진 음성) — 이 캐시 파일을 지우고 다시 생성하세요")
    gain = TARGET_LUFS - loudness
    limit = 10 ** (PEAK_CEILING_DB / 20)
    run(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", *raw,
         "-af", f"{trim},volume={gain:.2f}dB,alimiter=limit={limit:.4f}:level=0:latency=1,adelay=30,apad=pad_dur=0.08",
         str(work)])
    run(["afconvert", "-f", "m4af", "-d", "aac", "-b", str(AAC_BITRATE), str(work), str(out_m4a)])
    work.unlink()
    strip_free_atom(out_m4a)


def cmd_sample(args) -> None:
    s = session()
    SAMPLES.mkdir(parents=True, exist_ok=True)
    for voice in args.voice:
        ensure_voice(s, voice)
        r = request(s, "POST", f"/v1/text-to-speech/{voice}", params={"output_format": "mp3_44100_128"},
                    json={"text": SAMPLE_TEXT[args.lang], "model_id": args.model,
                          "voice_settings": settings_for(args.model)})
        out = SAMPLES / f"{args.lang}-{voice}-{args.model}.mp3"
        out.write_bytes(r.content)
        print(out)


def cmd_build(args) -> None:
    settings = settings_for(args.model)
    lines = phrases(args.lang)
    ios_out = IOS_VOICE / args.lang
    android_out = ANDROID_VOICE / args.lang
    outs = [ios_out, android_out]
    unknown = sorted(set(args.regen) - set(lines))
    if unknown:
        raise SystemExit(f"없는 문장 키: {unknown}")
    for out in outs:
        check_stale(out, lines)
    manifest_path = ios_out / IOS_MANIFEST
    existing = json.loads(manifest_path.read_text()) if manifest_path.exists() else None
    matching = existing is not None and (existing["voice"], existing["model"], existing["settings"]) == (
        args.voice, args.model, settings)
    reused = {key for key, text in lines.items()
              if matching and key not in args.regen and existing["lines"].get(key) == text
              and (ios_out / f"{key}.m4a").is_file()}
    # --regen 으로 고른 줄은 기존 파일·캐시를 무시하고 새로 녹음한다
    todo = [(k, t) for k, t in lines.items() if k not in reused
            and (k in args.regen or not cache_path(args.voice, args.model, t, settings).is_file())]
    print(f"총 {len(lines)}문장 · 기존 M4A 재사용 {len(reused)} · "
          f"PCM 캐시 {len(lines) - len(reused) - len(todo)} · "
          f"새로 생성 {len(todo)} ({sum(len(t) for _, t in todo)}자)")
    if args.dry_run:
        return
    work_dir = CACHE / "m4a" / args.voice / args.lang
    work_dir.mkdir(parents=True, exist_ok=True)
    (CACHE / "pcm").mkdir(parents=True, exist_ok=True)
    if todo:
        s = session()
        ensure_voice(s, args.voice)

    def fetch(item):
        key, text = item
        pcm = tts_pcm(s, args.voice, args.model, text, settings)
        path = cache_path(args.voice, args.model, text, settings)
        # 다 쓴 뒤 바꿔 넣는다 — 중간에 끊겨도 잘린 음성이 캐시로 굳지 않게
        part = path.with_suffix(".part")
        part.write_bytes(pcm)
        part.replace(path)
        return key

    done = 0
    with futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        for _ in pool.map(fetch, todo):
            done += 1
            if done % 25 == 0 or done == len(todo):
                print(f"  생성 {done}/{len(todo)}")

    # 모든 마스터링이 성공한 뒤에만 앱 리소스를 교체한다
    staged = {}
    for i, (key, text) in enumerate(lines.items(), 1):
        if key in reused:
            staged[key] = ios_out / f"{key}.m4a"
            continue
        m4a = work_dir / f"{key}.m4a"
        master(cache_path(args.voice, args.model, text, settings), m4a)
        staged[key] = m4a
        if i % 100 == 0:
            print(f"  마스터링 {i}/{len(lines)}")

    for out in outs:
        out.mkdir(parents=True, exist_ok=True)
    for key, m4a in staged.items():
        data = m4a.read_bytes()
        for out in outs:
            target = out / f"{key}.m4a"
            if target != m4a:
                target.write_bytes(data)

    manifest = {"voice": args.voice, "model": args.model, "settings": settings, "lines": lines}
    body = json.dumps(manifest, ensure_ascii=False, indent=1, sort_keys=True)
    (ios_out / IOS_MANIFEST).write_text(body)
    (android_out / ANDROID_MANIFEST).write_text(body)
    size = sum(p.stat().st_size for p in ios_out.glob("*.m4a"))
    print(f"완료 — {len(lines)}개, {size / 1024 / 1024:.1f}MB (앱마다)")


def check_stale(out: Path, lines: dict[str, str]) -> None:
    """목록에 없는 음성 파일이 있으면 멈춘다 — 지우는 판단은 사람이 한다"""
    stale = sorted(p.name for p in out.glob("*.m4a") if p.stem not in lines)
    if stale:
        raise RuntimeError(f"{out} 에 목록에 없는 파일이 있습니다 — 확인 후 지워 주세요: {stale[:10]}")


def cmd_sync_android(args) -> None:
    """이미 만든 iOS 음성 팩을 Android 로 바이트 그대로 복사한다 (다시 생성하지 않으므로 크레딧을 쓰지 않는다).
    iOS 매니페스트의 문장이 지금 phrases.py 와 다르면 멈춘다 — 음성 팩이 낡았다는 뜻이므로 build 를 먼저 다시 돌린다."""
    src = IOS_VOICE / args.lang
    dst = ANDROID_VOICE / args.lang
    manifest_path = src / IOS_MANIFEST
    if not manifest_path.exists():
        raise SystemExit(f"iOS 음성 팩이 없습니다: {manifest_path} — build --lang {args.lang} 를 먼저 실행하세요")
    manifest = json.loads(manifest_path.read_text())
    lines = phrases(args.lang)
    if manifest["lines"] != lines:
        changed = sorted(k for k in set(lines) | set(manifest["lines"]) if lines.get(k) != manifest["lines"].get(k))
        raise SystemExit(f"iOS 음성 팩 문장이 phrases.py 와 다릅니다 ({len(changed)}줄, 예: {changed[:5]}) — build 를 먼저 실행하세요")
    missing = sorted(k for k in lines if not (src / f"{k}.m4a").exists())
    if missing:
        raise SystemExit(f"iOS 음성 파일 없음: {missing[:10]}")
    dst.mkdir(parents=True, exist_ok=True)
    check_stale(dst, lines)
    for key in lines:
        shutil.copyfile(src / f"{key}.m4a", dst / f"{key}.m4a")
    shutil.copyfile(manifest_path, dst / ANDROID_MANIFEST)
    print(f"복사 완료 — {args.lang} {len(lines)}개 → {dst.relative_to(ROOT)}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("models")
    v = sub.add_parser("voices")
    v.add_argument("--lang", choices=LANGS, default="ko")
    v.add_argument("--search")
    v.add_argument("--use-case", help="라이브러리 용도 필터 (예: narrative_story, informative_educational)")
    sm = sub.add_parser("sample")
    sm.add_argument("--lang", choices=LANGS, default="ko")
    sm.add_argument("--voice", action="append", required=True)
    sm.add_argument("--model", default=DEFAULT_MODEL)
    b = sub.add_parser("build")
    b.add_argument("--lang", choices=LANGS, default="ko")
    b.add_argument("--voice", required=True)
    b.add_argument("--model", default=DEFAULT_MODEL)
    b.add_argument("--workers", type=int, default=3)
    b.add_argument("--dry-run", action="store_true", help="API 호출 없이 재사용·캐시·신규 생성 문자 수 확인")
    b.add_argument("--regen", nargs="+", default=[], metavar="KEY",
                   help="캐시를 무시하고 새로 녹음할 문장 키 (발음이 모호한 줄을 다시 받을 때)")
    sa = sub.add_parser("sync-android", help="iOS 음성 팩을 Android assets 로 그대로 복사")
    sa.add_argument("--lang", choices=LANGS, required=True)
    args = ap.parse_args()
    {"models": cmd_models, "voices": cmd_voices, "sample": cmd_sample, "build": cmd_build,
     "sync-android": cmd_sync_android}[args.cmd](args)


if __name__ == "__main__":
    main()
