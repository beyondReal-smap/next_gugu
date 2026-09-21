#!/usr/bin/env bash
# 이 저장소의 api/ 를 운영 서버로 내려보낸다. 저장소가 원본, 서버는 사본이다.
#
# .env 는 서버에만 있고 절대 덮어쓰지 않는다 (시크릿이 저장소에 들어가지 않는다).
# 재시작 전에 원격에서 임포트·미정의 이름 검사를 돌린다 — 과거에 함수 본문의
# 누락된 이름이 임포트 검사를 통과해 운영이 크래시 루프에 빠진 적이 있다.
set -euo pipefail

HOST="${GUGU_API_HOST:-aws}"
REMOTE="${GUGU_API_PATH:-~/projects/gugu/api}"
PORT="${GUGU_API_PORT:-5106}"
LOCAL="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DRY_RUN=0

for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=1 ;;
    *) echo "알 수 없는 인자: $arg" >&2; exit 2 ;;
  esac
done

# 전송에서 빼고(exclude), 서버에서도 지우지 않는다(protect).
# --delete 는 exclude 만으로는 수신측 파일을 보호해 주지 않는다 — 실제로 dry-run 에서
# .env.bak.* 와 *.bak.* 를 지우려 했다. 그래서 P 규칙을 따로 준다.
FILTERS=(
  --filter 'P .env'         # 시크릿 — 서버에만 둔다
  --filter 'P .env.*'       # 이전 .env 백업
  --filter 'P *.bak'
  --filter 'P *.bak.*'
  --filter 'P .venv/'
  --filter 'P __pycache__/'
  --filter 'P .archive/'
  --exclude '.env'
  --exclude '.venv/'
  --exclude '__pycache__/'
  --exclude '*.pyc'
  --exclude '.archive/'
  --exclude 'deploy.sh'     # 배포 도구 자체는 서버에 둘 필요가 없다
)

echo "▶ 대상: $HOST:$REMOTE (포트 $PORT)"

echo "▶ 배포 전 상태 확인"
ssh "$HOST" "curl -sf --max-time 5 http://127.0.0.1:$PORT/api/health/ready" \
  || echo "  경고: 배포 전 ready 검사 실패 — 이미 비정상일 수 있다"
echo

if [[ $DRY_RUN -eq 1 ]]; then
  echo "▶ 변경 예정 (dry-run)"
  rsync -nai --delete "${FILTERS[@]}" "$LOCAL/" "$HOST:$REMOTE/"
  exit 0
fi

echo "▶ 전송"
rsync -a --delete --itemize-changes "${FILTERS[@]}" "$LOCAL/" "$HOST:$REMOTE/"

echo "▶ 원격 정적 검사"
ssh "$HOST" "cd $REMOTE && uv sync --quiet && uvx pyflakes *.py && .venv/bin/python -c 'import main'"

echo "▶ 재시작"
ssh "$HOST" "pm2 restart gugu-api --update-env" > /dev/null

echo "▶ 기동 대기"
for i in $(seq 1 20); do
  if ssh "$HOST" "curl -sf --max-time 3 http://127.0.0.1:$PORT/api/health/ready" 2>/dev/null; then
    echo; echo "✅ 배포 완료"
    ssh "$HOST" "pm2 describe gugu-api | grep -E 'status|restarts|uptime' | head -3"
    exit 0
  fi
  sleep 2
done

echo "❌ 기동 확인 실패 — 로그를 확인하고 필요하면 이전 커밋으로 되돌려 재배포한다"
ssh "$HOST" "pm2 logs gugu-api --nostream --lines 30"
exit 1
