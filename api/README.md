# gugu-api

구구 어드벤처 백엔드 — 인앱결제 검증 · 학습 원장 · 공개 MCP 서버.

`https://gugu.aiagnt.kr/api/*` → cloudflared → 이 프로세스(127.0.0.1:5106, pm2 `gugu-api`).

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
| `push.py` | 원격 푸시 기기 등록·광고 동의 기록 API (`/api/v1/push/*`) |
| (앱 저장소) `google-services.json` · `GoogleService-Info.plist` | Firebase 클라이언트 설정(gitignore) — Android 빌드 전체·iOS Release 빌드에 필수, 아래 "설정" 참고 |
| `fcm.py` | FCM HTTP v1 발송기 (자격 캐시·재시도·무효 토큰 분류) |
| `push_templates.py` `push_schedule.py` | 정보성 알림 문구·광고 표현 검사·딥링크 허용 목록 / KST 발송 창·2년 확인 계산 |
| `push_delivery.py` | 발송 절차 (중복 선점 → 발송 → 무효 토큰 정리 → 기록) |
| `push_admin.py` `push_jobs.py` | 운영자 개별 발송 CLI / 자동 발송 배치 (pm2 `gugu-push-cron`) |
| `tests/` | `uv run pytest` |

## 원격 푸시 (FCM)

계획서: `.claude/plans/native-push.md`. 공지·광고는 **Firebase 콘솔에서 토픽으로**, 계정별 알림
(운영자 개별 발송·보호자 주간 리포트·광고 수신동의 2년 확인)은 **서버가 FCM HTTP v1 로** 보낸다.
서버는 정보성 알림만 보낸다 — 광고성 표현이 있으면 `push_admin.py` 가 거부한다.

### 설정

| 키 | 설명 |
|---|---|
| `FIREBASE_PROJECT_ID` | `gugu-adventure` |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | 서비스 계정 JSON **절대 경로** (`~` 는 펼쳐지지 않는다) |

- 둘 다 비우면 발송만 꺼지고 API 는 정상 기동한다(`/api/health/ready` 의 `checks.push = disabled_no_config`).
  하나만 넣으면 기동이 중단된다.
- 서비스 계정 역할은 **Firebase Cloud Messaging API Admin** 하나만 준다.
- 앱 저장소는 Firebase 클라이언트 설정 파일을 커밋하지 않는다(gitignore). 체크아웃 직후 빌드하려면 Firebase 콘솔
  (`gugu-adventure`)에서 받아 넣는다 — Android 는 `native-android/app/google-services.json` 이 **모든 빌드에 필수**
  (없으면 `google-services` 플러그인이 빌드를 중단), iOS 는 `native-ios/Gugu/GoogleService-Info.plist` 가
  **Release 빌드에 필수**(빌드 단계 "Check GoogleService-Info.plist (Release)" 가 중단).
- 키 파일은 **배포 디렉터리 밖** `~/secrets/` 에 두고 `chmod 600` 한다. `deploy.sh` 는 `rsync --delete` 라
  `api/` 안에 둔 낯선 파일을 지운다(보호 규칙은 `.env` 류뿐이다).

```bash
ssh aws 'mkdir -p ~/secrets && chmod 700 ~/secrets'
scp gugu-fcm-sender.json aws:~/secrets/ && ssh aws 'chmod 600 ~/secrets/gugu-fcm-sender.json'
# 서버 .env — 경로는 절대 경로로
# FIREBASE_PROJECT_ID=gugu-adventure
# FIREBASE_SERVICE_ACCOUNT_JSON=/home/<user>/secrets/gugu-fcm-sender.json
```

### 마이그레이션 적용 순서

1. `migrations/20261019_push.sql` — 테이블 4개(RLS·권한 회수)와 service_role 전용 RPC
2. `migrations/20261019_purge_owner_data_push.sql` — 계정 삭제 정리 함수에 푸시 3테이블 추가

두 파일 모두 위 "마이그레이션" 절차대로 트랜잭션 안에서 검증하고 롤백한 뒤 적용한다.
2번은 `gugu_purge_owner_data` 를 통째로 바꾸므로 적용 전에 운영 정의
(`select pg_get_functiondef('public.gugu_purge_owner_data(uuid)'::regprocedure)`)가
`20260922_purge_owner_data.sql` 과 같은지 먼저 확인한다. 다르면 운영 정의에 푸시 삭제를 더해 다시 만든다.
API 를 먼저 배포해도 준비 검사는 실패하지 않지만(`push_cron = unavailable`), 앱의 기기 등록은
마이그레이션 전까지 503 이다.

### 자동 발송 배치

```bash
cd ~/projects/gugu/api
uv run python push_jobs.py run-due --dry-run        # 한 번 손으로 돌려 오류가 없는지 본다
pm2 start "uv run python push_jobs.py run-due" --name gugu-push-cron \
  --cron-restart "5 * * * *" --no-autorestart
pm2 save
```

계획서대로 첫 1주일은 위 등록 명령의 `run-due` 를 `run-due --dry-run` 으로 바꿔 등록하고,
`push_job_runs` 의 `dry_run` 기록(`detail.wouldSend`)으로 대상 수와 중복 여부를 확인한 뒤
`pm2 delete gugu-push-cron` → 실제 명령으로 다시 등록한다.

- 매시 5분 실행, 같은 시간에 여러 번 돌아도 `push_sends` 의 dedupe 키로 중복 발송이 없다.
- 작업: `weekly_report`(일 19:00~월 20:00 KST) · `marketing_reconfirm`(10:00~20:59 KST) · `prune`(270일 미사용 기기).
  모든 서버 발송은 08:00~20:59 KST 에만 한다. 서버 학습 공백 알림은 만들지 않는다(로컬 알림이 처리).
- 결과는 `push_job_runs` 에 남는다. `/api/health/ready` 의 `checks.push_cron` 이 2시간 넘게 성공 기록이
  없으면 `stale` 이다(준비 판정에는 넣지 않는다).

### 운영자 개별 발송

```bash
uv run python push_admin.py send-user --email <이메일> --template premium_granted --dry-run
uv run python push_admin.py send-user --email <이메일> --template account_notice --title "계정 안내" --body "..."
uv run python push_admin.py send-token --token <테스트 기기 FCM 토큰> --template test
```

템플릿: `premium_granted` · `premium_revoked` · `account_notice` · `test`. 21:00~07:59 KST 는 `--force` 가 필요하다.
출력에는 이메일·토큰을 마스킹해서만 보여 준다.

### Firebase 콘솔 운영 규칙 (공지·광고)

콘솔 발송은 서버 검사를 거치지 않는다. 아래를 매번 지킨다.

1. **대상은 반드시 주제(토픽)**: `notice`(전체 공지) · `notice-ios` · `notice-android` · `promo`(광고).
   공지 토픽은 **앱 언어별**이다(앱 2.6.0~): `notice*` 는 한국어 기기만, 영어 기기는 `notice-en` · `notice-en-ios` ·
   `notice-en-android`, 일본어 기기는 `notice-ja` · `notice-ja-ios` · `notice-ja-android`. 전체 공지는 언어마다
   그 언어 문구로 한 번씩 보낸다. `promo` 는 한국 App Store 기기만 구독한다(해외는 광고 동의 토글이 없다).
   **"전체 사용자"·앱 세그먼트 대상 금지** — 알림을 끈 사용자·광고 미동의 사용자에게도 간다.
2. **광고는 `promo` 토픽에만**. 서버가 광고 동의 기록을 확인(ack)한 기기만 `promo` 를 구독한다.
3. 광고 제목은 **`(광고)` 로 시작**하고, 본문 끝에 **`구구 어드벤처 · 수신거부: 설정 > 알림`** 을 넣는다
   (정보통신망법 제50조 ④, 시행령 제61조).
4. 광고는 **08:00~20:00 KST** 에만 예약한다. 야간 광고 동의는 받지 않으므로 야간 광고는 보내지 않는다.
5. **광고는 만료(TTL) 1시간 이내, 그리고 21:00 KST 이전에 만료**되게 설정한다 (추가 옵션 > 만료).
   기본값(4주)으로 두면 꺼져 있던 기기가 밤에 켜질 때 광고가 도착한다 — 야간 광고 전송과 같다.
   예) 20:00 예약 → 만료 1시간(21:00 만료), 20:30 예약 → 만료 30분 이하.
6. Android 알림 채널: 광고 `promotion`, 공지 `service_notice` — **둘 다 필수**. 비우면 기본 채널로 가서
   사용자가 끈 채널 설정을 우회한다.
7. 추가 옵션 > 맞춤 데이터에 `kind`(광고 `promo` · 공지 `notice`, **필수**)와 `link`(허용 목록:
   `home` · `learn` · `profile` · `profile/report` · `paywall` · `settings/notifications`)를 넣는다.
8. **테스트 기기로 먼저 보낸다** ("테스트 메시지 전송"에 FCM 토큰 입력) — 문구·딥링크·채널을 확인한 뒤 토픽 발송.
9. 공지에 구매 권유(할인·이벤트·구매 등)가 섞이면 광고다 → 광고 체크리스트와 `promo` 토픽을 따른다.

> **법무 확인 필요**: 정보통신망법 시행령 제61조는 광고 전송자의 명칭과 **연락처**(전화번호·이메일·주소 등)
> 표시를 요구한다. 푸시 본문 길이 제약상 지금 문구는 명칭만 넣는다 — 연락처를 본문에 넣어야 하는지,
> 앱 내 수신거부 화면 표시로 갈음할 수 있는지 법무 확인 후 위 문구를 확정한다.

**광고 발송 전 체크리스트** (복사해서 매번 확인)

```text
[ ] 대상: 주제(토픽) `promo` 하나만 (전체 사용자·앱 세그먼트·notice* 아님)
[ ] 제목: "(광고)" 로 시작
[ ] 본문: 끝이 "구구 어드벤처 · 수신거부: 설정 > 알림"
[ ] 예약: 08:00~20:00 KST
[ ] 만료(TTL): 1시간 이내, 21:00 KST 이전 만료
[ ] Android 알림 채널: promotion
[ ] 맞춤 데이터: kind=promo (필수), link=허용 목록 값
[ ] 테스트 기기 먼저 발송 → 문구·딥링크·채널 확인
```

**공지 발송 전 체크리스트**

```text
[ ] 대상: 주제(토픽) notice / notice-ios / notice-android 중 하나 (영어 notice-en*, 일본어 notice-ja* 는 그 언어 문구로 따로)
[ ] 구매 권유(할인·이벤트·구매) 없음 — 있으면 광고 체크리스트로
[ ] Android 알림 채널: service_notice
[ ] 맞춤 데이터: kind=notice (필수), link=허용 목록 값
[ ] 테스트 기기 먼저 발송 → 문구·딥링크·채널 확인
```

## 알려진 부채

- iOS 레거시 경로가 Apple `verifyReceipt` 를 쓴다(사용 중단 예고됨). 신규 클라이언트는
  JWS 를 보내므로, 심사 중인 2.1.0 Capacitor 빌드가 내려가면 레거시 경로를 제거할 수 있다.
- `ensure_schema()` 가 기동 시 DDL 을 돌린다. 정식 마이그레이션 체계로 옮겨야 한다.
- 샌드박스 거래도 `raw_status='verified'` 로 기록된다(TestFlight 테스트용).
  `purchases.environment` 로 식별할 수 있으니 정식 출시 전에 정리한다.
- `answer_events` 가 REST 로 노출되지 않아 원시 이벤트를 대시보드/REST 로 조회할 수 없다.
  집계(`progress_snapshots`)만 읽을 수 있어 오답 내용 분석은 DB 직접 접속이 필요하다.
