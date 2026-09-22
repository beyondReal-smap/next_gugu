# gugu-api

구구 어드벤처 백엔드 — 인앱결제 검증 · 학습 원장 · 공개 MCP 서버.

`https://gugu.smap.site/api/*` → cloudflared → 이 프로세스(127.0.0.1:5106, pm2 `gugu-api`).

## 원본은 이 저장소다

2026-09-21 까지 서버(`aws:~/projects/gugu/api`)에만 존재하고 버전 관리가 없었다.
이력이 `*.bak.<타임스탬프>` 파일뿐이라 변경 추적과 되돌리기가 불가능했다.
이제 이 디렉토리가 원본이고 서버는 사본이다. **서버에서 직접 고치지 않는다.**

## 배포

```bash
./api/deploy.sh --dry-run   # 무엇이 바뀌는지만 확인
./api/deploy.sh             # 전송 → 정적 검사 → 재시작 → 기동 확인
```

되돌리기는 이전 커밋을 체크아웃하고 다시 배포한다.

환경변수는 `GUGU_API_HOST`(기본 `aws`), `GUGU_API_PATH`, `GUGU_API_PORT` 로 바꿀 수 있다.

## 시크릿

`.env` 는 **서버에만** 있고 배포가 덮어쓰지 않는다. 키 목록과 형식은 `.env.example` 를 본다.
새 설정을 추가할 때는 두 가지를 같이 한다.

1. `settings.py` 에 **기본값이 있는** 필드로 추가한다.
2. `.env.example` 에 키와 설명을 적는다.

필수(기본값 없는) 필드를 추가하고 서버 `.env` 에 값을 넣지 않으면 재시작이 크래시 루프에 빠진다.
2026-08-18 에 `DB_SOCKET` 으로 실제 장애가 났다.

## 마이그레이션

Supabase Postgres 의 스키마·함수 변경은 `migrations/` 에 `YYYYMMDD_설명.sql` 로 남긴다.
2026-09-21 까지는 파일 없이 즉석 적용해서 무엇이 언제 바뀌었는지 알 수 없었다.

적용 전에 **반드시 트랜잭션 안에서 검증하고 롤백한다.** 함수는 `CREATE OR REPLACE`
가 트랜잭션 안에서도 동작하므로, 같은 트랜잭션에서 바꾼 뒤 실제 호출로 결과를
확인하고 `rollback` 하면 운영 데이터를 건드리지 않고 검증할 수 있다.

```bash
# SUPABASE_DB_URL 은 세션 풀러(5432) — 트랜잭션 풀러(6543)로는 DDL 이 실패한다
uv run --with "psycopg[binary]" python -c '...'   # 트랜잭션 + rollback 검증
```

되돌리기는 git 이력에서 이전 함수 정의를 꺼내 다시 적용한다.

## 구성

| 파일 | 역할 |
|---|---|
| `main.py` | FastAPI 앱, 결제 검증(`/api/iap/*`), 프리미엄·보호자(`/api/premium/status`, `/api/account/guardian`) |
| `apple_jws.py` | StoreKit 2 서명 트랜잭션(JWS) 검증 — Apple 공식 `app-store-server-library` |
| `apple_certs/` | Apple 루트 CA(공개 인증서). JWS 체인 검증에 필요하다 |
| `settings.py` | 환경설정 로드·검증 (기동 시 실패하면 즉시 중단) |
| `db.py` | MariaDB — 구매 원장(`purchases`) |
| `learning.py` | 학습 원장 API (Supabase Postgres) |
| `auth.py` | Supabase JWT 검증 |
| `mcp_server.py` | 공개 읽기 전용 MCP 서버 (`/api/mcp`) |
| `coach.py` `adaptive.py` `reports.py` | AI 코치 · 적응 출제 · 리포트 |

## 알려진 부채

- iOS 레거시 경로가 Apple `verifyReceipt` 를 쓴다(사용 중단 예고됨). 신규 클라이언트는
  JWS 를 보내므로, 심사 중인 2.1.0 Capacitor 빌드가 내려가면 레거시 경로를 제거할 수 있다.
- `ensure_schema()` 가 기동 시 DDL 을 돌린다. 정식 마이그레이션 체계로 옮겨야 한다.
- 샌드박스 거래도 `raw_status='verified'` 로 기록된다(TestFlight 테스트용).
  `purchases.environment` 로 식별할 수 있으니 정식 출시 전에 정리한다.
- `answer_events` 가 REST 로 노출되지 않아 원시 이벤트를 대시보드/REST 로 조회할 수 없다.
  집계(`progress_snapshots`)만 읽을 수 있어 오답 내용 분석은 DB 직접 접속이 필요하다.
