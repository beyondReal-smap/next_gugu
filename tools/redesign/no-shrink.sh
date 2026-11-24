#!/bin/bash
# 재디자인 V1 넘침 규칙 검사 (계획서 .claude/plans/redesign-v1.md 3-0) — iOS·Android 같이 본다.
#
# 규칙: 글자를 줄이거나 잘라서 상자에 맞추지 않는다. 안 들어가면 개수를 줄이거나, 두 줄로 감싸거나, 배치를 바꾼다.
# 1단계에서 새로 만들거나 고친 폴더에서 아래 모양을 찾아 0건이어야 통과(종료 코드 0)한다.
#   iOS      .minimumScaleFactor(…)   .lineLimit(1)
#   Android  FitText(…)   maxLines = 1   TextOverflow.Ellipsis   softWrap = false (한 줄 강제 — 넘치면 잘린다)
# 꼭 필요한 줄은 끝에 `// fit:ok <이유>` 를 달면 허용으로 세고 목록에 보여 준다(검토용). 이유 없는 fit:ok 는 위반이다.
# 주석(// 뒤, * 로 시작하는 문서 줄) 안의 글은 보지 않는다.
#
# 사용: tools/redesign/no-shrink.sh            보류 목록(PENDING)의 기존 파일은 세기만 하고 실패시키지 않는다
#       tools/redesign/no-shrink.sh --strict   보류 목록도 위반으로 센다(U11 마무리 기준)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
STRICT=0
[[ "${1:-}" == "--strict" ]] && STRICT=1

IOS=native-ios/Gugu
AND=native-android/app/src/main/java/site/smap/gugudan

# 검사 대상 — 계획서 3-0 의 폴더(섬 시트·표 시트는 Map/map 안) + 아이 화면 토큰 파일
TARGETS=(
  "$IOS/Features/Home" "$IOS/Features/Map" "$IOS/Features/Treasure" "$IOS/Features/Guardian" "$IOS/Features/Shell"
  "$IOS/DesignSystem/WorldTheme.swift"
  "$AND/features/home" "$AND/features/map" "$AND/features/treasure" "$AND/features/guardian" "$AND/features/shell"
  "$AND/designsystem/WorldTheme.kt"
)

# 보류 — 대상 폴더에 있지만 아직 다시 만들지 않은 기존 파일과 그 파일을 고치는 단위.
# 그 단위에서 파일을 고치면 줄을 지운다(지우지 않으면 새 위반도 보류로 숨는다). U11 은 --strict 로 0건이어야 한다
PENDING=(
  "$IOS/Features/Home/HomeView.swift U8"
  "$IOS/Features/Home/DailyMissionsCard.swift U8"
  "$IOS/Features/Home/DailyGoalSheet.swift U8"
  "$AND/features/home/HomeScreen.kt U8"
  "$AND/features/home/DailyMissionsCard.kt U8"
)

pending_unit() {
  local file="$1" entry
  for entry in "${PENDING[@]}"; do
    [[ "${entry% *}" == "$file" ]] && { echo "${entry##* }"; return; }
  done
  echo ""
}

files=()
missing=()
for t in "${TARGETS[@]}"; do
  if [[ -d "$t" ]]; then
    while IFS= read -r f; do files+=("$f"); done < <(find "$t" -type f \( -name '*.swift' -o -name '*.kt' \) | sort)
  elif [[ -f "$t" ]]; then
    files+=("$t")
  else
    missing+=("$t")
  fi
done

violations=0; held=0; allowed=0
report_v=(); report_h=(); report_a=()
for f in ${files[@]+"${files[@]}"}; do
  unit="$(pending_unit "$f")"
  while IFS=$'\t' read -r kind loc what src; do
    case "$kind" in
      OK) allowed=$((allowed + 1)); report_a+=("  $loc  $src") ;;
      HIT)
        if [[ -n "$unit" && $STRICT -eq 0 ]]; then
          held=$((held + 1)); report_h+=("  $loc  [$what] ($unit 에서 고침)")
        else
          violations=$((violations + 1)); report_v+=("  $loc  [$what]  $src")
        fi ;;
    esac
  done < <(awk -v f="$f" '
    BEGIN { pat = "minimumScaleFactor|\\.lineLimit\\([[:space:]]*1[[:space:]]*[,)]|FitText\\(|maxLines[[:space:]]*=[[:space:]]*1([^0-9]|$)|TextOverflow\\.Ellipsis|softWrap[[:space:]]*=[[:space:]]*false" }
    {
      line = $0; code = line
      sub(/\/\/.*$/, "", code)
      if (code ~ /^[[:space:]]*\*/ || code ~ /^[[:space:]]*\/\*/) next
      if (!match(code, pat)) next
      if (code ~ /minimumScaleFactor/) what = "minimumScaleFactor"
      else if (code ~ /\.lineLimit/) what = "lineLimit(1)"
      else if (code ~ /FitText\(/) what = "FitText"
      else if (code ~ /TextOverflow\.Ellipsis/) what = "TextOverflow.Ellipsis"
      else if (code ~ /maxLines/) what = "maxLines = 1"
      else what = "softWrap = false"
      src = line; gsub(/^[[:space:]]+/, "", src); gsub(/\t/, " ", src)
      if (line ~ /\/\/[[:space:]]*fit:ok[[:space:]]+[^[:space:]]/) { printf "OK\t%s:%d\t%s\t%s\n", f, NR, what, src; next }
      if (line ~ /fit:ok/) what = what " + 이유 없는 fit:ok"
      printf "HIT\t%s:%d\t%s\t%s\n", f, NR, what, src
    }' "$f")
done

echo "no-shrink: 검사 파일 ${#files[@]}개$([[ $STRICT -eq 1 ]] && echo ' (--strict)')"
if (( ${#missing[@]} )); then
  echo "아직 없는 대상(건너뜀): ${missing[*]}"
fi
if (( allowed )); then printf '허용(fit:ok) %d건\n' "$allowed"; printf '%s\n' "${report_a[@]}"; fi
if (( held )); then printf '보류 %d건 — 다시 만들 기존 파일\n' "$held"; printf '%s\n' "${report_h[@]}"; fi
if (( violations )); then
  printf '위반 %d건\n' "$violations"; printf '%s\n' "${report_v[@]}"
  echo "실패: 글자를 줄이거나 자르지 말고 개수 줄이기 → 두 줄 감싸기 → 배치 바꾸기 순으로 푸세요(계획서 3-0)"
  exit 1
fi
echo "통과: 위반 0건"
