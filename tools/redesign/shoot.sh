#!/bin/bash
# 재디자인 V1 화면 캡처 (계획서 .claude/plans/redesign-v1.md 6-3) — iOS 시뮬(iPhone 17e, V1 프레임과 같은 390pt 폭)·Android 에뮬(Pixel 9 Pro).
# 앱은 미리 설치돼 있어야 한다(디버그 빌드 — 훅은 출시 빌드에 없다). 저장본을 덮어쓰는 훅(GUGU_SEED)이 있으니 캡처 전용 기기에서 쓴다.
#
# 한 장:  tools/redesign/shoot.sh <ios|android> <저장 폴더> <이름> [옵션…] [GUGU_X=값 …]
#   --theme light|dark|system  테마 저장값(gugu.theme)을 미리 넣는다 — 설정 키는 iOS 앱 설정 plist, Android shared_prefs/gugu.xml 에 쓴다
#   --lang ko|en|ja|vi|hi|id   앱 언어(iOS -AppleLanguages, Android 앱별 언어)
#   --seed v1|max              GUGU_SEED + 같이 넣을 옷장·도감·미션 값(v1: 코인 97·도감 5/8·미션 half,
#                              max: 코인 99999·도감 8/8·계단 999·미션 opened + 설정 키 gugu.maxMultiplier=19)
#   --screen <값>              GUGU_SCREEN (지금 home|map|treasure, 나머지는 그 화면 단위에서 더한다)
#   --device <udid|serial>     기본 iOS FAA29B9B-…(iPhone 17e) / Android 연결된 첫 기기
#   --wait <초>                실행 뒤 찍기까지 기다림(기본 3)
# 짝 전부: tools/redesign/shoot.sh <ios|android> <저장 폴더> pairs   — 6-3 비교 표의 조건 그대로 찍는다(compare.py 가 쓰는 이름)
#
# iOS 설정 키 넣기는 lessons(2026-09-30) 대로: 앱 종료 → cfprefsd 재시작 → plist 직접 수정 → cfprefsd 재시작 → 실행.
# Android 설정 키는 lessons(2026-09-30) 대로 shared_prefs/gugu.xml 을 run-as cp 로 바꾼다(기기의 /data/local/tmp/gugu-shoot-prefs.xml 를 거친다 — 덮어쓰기만 하고 지우지 않는다).
# Android 훅 값은 debug.gugu.<GUGU_ 뒤 소문자 이름> 시스템 속성으로 넘긴다(기존 debug.gugu.premium 과 같은 방식). 넘기지 않은 훅은 빈 값으로 되돌린다.
# adb 는 PATH 에 없으면 ~/Library/Android/sdk/platform-tools/adb 를 쓴다(ADB=<경로> 로 바꿀 수 있다).
set -euo pipefail

BUNDLE=site.smap.gugudan
IOS_DEFAULT=FAA29B9B-2A44-4219-BF47-EDF1E249E5EE
ADB="${ADB:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"
HOOKS=(GUGU_SEED GUGU_SCREEN GUGU_PREMIUM GUGU_PLAY_STARS GUGU_PLAY_DEX GUGU_PLAY_STAIRS_BEST GUGU_PLAY_MISSIONS GUGU_PLAY_EQUIP)

usage() { sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
[[ $# -ge 3 ]] || usage
PLATFORM="$1"; OUT="$2"; NAME="$3"; shift 3
[[ "$PLATFORM" == ios || "$PLATFORM" == android ]] || usage
mkdir -p "$OUT"

shoot_one() {   # shoot_one <이름> [옵션…]
  local name="$1"; shift
  local theme="" lang="" device="" wait=3
  local env=() prefs=()
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --theme) theme="$2"; prefs+=("gugu.theme=$2"); shift 2 ;;
      --lang) lang="$2"; shift 2 ;;
      --device) device="$2"; shift 2 ;;
      --wait) wait="$2"; shift 2 ;;
      --screen) env+=("GUGU_SCREEN=$2"); shift 2 ;;
      --seed)
        case "$2" in
          v1) env+=("GUGU_SEED=v1" "GUGU_PLAY_STARS=97" "GUGU_PLAY_DEX=10" "GUGU_PLAY_MISSIONS=half") ;;
          max) env+=("GUGU_SEED=max" "GUGU_PLAY_STARS=99999" "GUGU_PLAY_DEX=70" "GUGU_PLAY_STAIRS_BEST=999" "GUGU_PLAY_MISSIONS=opened")
               prefs+=("gugu.maxMultiplier=19") ;;
          *) echo "--seed 는 v1|max: $2" >&2; exit 2 ;;
        esac
        shift 2 ;;
      GUGU_*=*) env+=("$1"); shift ;;
      *) echo "모르는 옵션: $1" >&2; usage ;;
    esac
  done
  [[ -z "$theme" || "$theme" =~ ^(light|dark|system)$ ]] || { echo "--theme 는 light|dark|system: $theme" >&2; exit 2; }

  local png="$OUT/$name.png"
  if [[ "$PLATFORM" == ios ]]; then
    local udid="${device:-$IOS_DEFAULT}"
    xcrun simctl terminate "$udid" "$BUNDLE" 2>/dev/null || true
    if (( ${#prefs[@]} )); then
      local plist
      plist="$(xcrun simctl get_app_container "$udid" "$BUNDLE" data)/Library/Preferences/$BUNDLE.plist"
      xcrun simctl spawn "$udid" launchctl kickstart -k system/com.apple.cfprefsd.xpc.daemon
      /usr/bin/python3 - "$plist" "${prefs[@]}" <<'PY'
import os, plistlib, sys
path, pairs = sys.argv[1], sys.argv[2:]
data = {}
if os.path.exists(path):
    with open(path, "rb") as f:
        data = plistlib.load(f)
for pair in pairs:
    key, value = pair.split("=", 1)
    data[key] = value          # 앱은 두 키 모두 문자열로 쓴다(Persistence.setString)
os.makedirs(os.path.dirname(path), exist_ok=True)
with open(path, "wb") as f:
    plistlib.dump(data, f, fmt=plistlib.FMT_BINARY)
PY
      xcrun simctl spawn "$udid" launchctl kickstart -k system/com.apple.cfprefsd.xpc.daemon
    fi
    local args=()
    [[ -n "$lang" ]] && args+=(-AppleLanguages "($lang)")
    local kv
    for kv in ${env[@]+"${env[@]}"}; do export "SIMCTL_CHILD_$kv"; done
    xcrun simctl launch "$udid" "$BUNDLE" ${args[@]+"${args[@]}"} >/dev/null
    for kv in ${env[@]+"${env[@]}"}; do unset "SIMCTL_CHILD_${kv%%=*}"; done
    sleep "$wait"
    xcrun simctl io "$udid" screenshot "$png" >/dev/null 2>&1
  else
    local adb=("$ADB"); [[ -n "$device" ]] && adb=("$ADB" -s "$device")
    "${adb[@]}" shell am force-stop "$BUNDLE"
    if (( ${#prefs[@]} )); then
      local work="${TMPDIR:-/tmp}/gugu-shoot"; mkdir -p "$work"
      : > "$work/gugu.xml"
      if "${adb[@]}" shell "run-as $BUNDLE ls shared_prefs/gugu.xml" >/dev/null 2>&1; then
        "${adb[@]}" exec-out run-as "$BUNDLE" cat shared_prefs/gugu.xml > "$work/gugu.xml"
      fi
      /usr/bin/python3 - "$work/gugu.xml" "${prefs[@]}" <<'XML'
import sys
import xml.etree.ElementTree as ET
path, pairs = sys.argv[1], sys.argv[2:]
raw = open(path, "rb").read().strip()
root = ET.fromstring(raw) if raw else ET.Element("map")
if root.tag != "map":
    sys.exit(f"shared_prefs/gugu.xml 의 맨 위가 <map> 이 아니다: {root.tag}")
for pair in pairs:
    key, value = pair.split("=", 1)
    node = next((e for e in root if e.get("name") == key), None)
    if node is None:
        node = ET.SubElement(root, "string", name=key)
    if node.tag != "string":
        sys.exit(f"{key} 가 문자열이 아니다: <{node.tag}>")
    node.text = value
ET.ElementTree(root).write(path, encoding="utf-8", xml_declaration=True)
XML
      "${adb[@]}" push "$work/gugu.xml" /data/local/tmp/gugu-shoot-prefs.xml >/dev/null
      "${adb[@]}" shell chmod 644 /data/local/tmp/gugu-shoot-prefs.xml
      "${adb[@]}" shell "run-as $BUNDLE sh -c 'mkdir -p shared_prefs && cp /data/local/tmp/gugu-shoot-prefs.xml shared_prefs/gugu.xml'"
    fi
    local hook val kv
    for hook in "${HOOKS[@]}"; do
      val=""
      for kv in ${env[@]+"${env[@]}"}; do [[ "${kv%%=*}" == "$hook" ]] && val="${kv#*=}"; done
      local prop="debug.gugu.$(echo "${hook#GUGU_}" | tr '[:upper:]' '[:lower:]')"
      "${adb[@]}" shell setprop "$prop" "'$val'"
    done
    [[ -n "$lang" ]] && "${adb[@]}" shell cmd locale set-app-locales "$BUNDLE" --locales "$lang"
    local activity
    activity="$("${adb[@]}" shell cmd package resolve-activity --brief "$BUNDLE" | tail -1 | tr -d '\r')"
    "${adb[@]}" shell am start -n "$activity" >/dev/null
    sleep "$wait"
    "${adb[@]}" exec-out screencap -p > "$png"
  fi
  echo "$png"
}

if [[ "$NAME" == pairs ]]; then
  # 계획서 6-3 비교 짝 — 이름은 V1 shots/ 와 같다(compare.py 가 짝을 찾는다)
  shoot_one 01-home --seed v1 --screen home GUGU_PREMIUM=1 --theme light --lang ko "$@"
  shoot_one 01-home-night --seed v1 --screen home GUGU_PREMIUM=1 --theme dark --lang ko "$@"
  shoot_one 01-home-vi --seed v1 --screen home GUGU_PREMIUM=1 --theme light --lang vi "$@"
  shoot_one 03-island-sheet --seed v1 --screen island:5 GUGU_PREMIUM=1 --theme light --lang ko "$@"
  shoot_one 03-island-sheet-vi --seed v1 --screen island:5 GUGU_PREMIUM=1 --theme light --lang vi "$@"
  shoot_one 04-treasure --seed v1 --screen treasure GUGU_PREMIUM=1 --theme light --lang ko "$@"
  shoot_one 05-guardian-gate --seed v1 --screen gate:62 GUGU_PREMIUM=1 --theme light --lang ko "$@"
  shoot_one 06-guardian-report --seed v1 --screen report GUGU_PREMIUM=1 --theme light --lang ko "$@"
  shoot_one 07-guardian-settings --seed v1 --screen settings GUGU_PREMIUM=1 --theme light --lang ko "$@"
  shoot_one 08-locked-kid --seed v1 --screen locked:missing GUGU_PREMIUM=0 --theme light --lang ko "$@"
else
  shoot_one "$NAME" "$@"
fi
