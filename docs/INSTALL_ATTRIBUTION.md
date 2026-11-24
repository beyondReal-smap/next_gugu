# 설치 클릭 귀속 · 서버 로그 · 실제 설치 검증

> 이 문서의 범위는 **"광고 → 랜딩 → 스토어 이동(설치 클릭)"까지**다. 클릭은 설치가 아니다.
> 실제 설치·구매 확정은 아래 [남은 작업](#남은-작업-실제-설치구매-검증) 이후에 가능하다.

## 1. 동작

1. 첫 진입(`MetaPixelLoader`)에서 URL의 `fbclid`, `utm_source|medium|campaign|content|term`을 localStorage(`gugu_attr_v1`, 30일, 마지막 유입 우선)에 저장.
2. 스토어 버튼(`StoreButtons`, 위치 `placement`: `hero` / `bottom_cta` / `install_prompt_limit` / `install_prompt_adventure`) 클릭 시
   - Pixel: `Lead`(표준) + `install_click`(커스텀) + `app_store_click`(기존명) — 파라미터 `store, placement, click_id, fbclid, utm_*`. `eventID`(`lead_<click_id>`, `ic_<click_id>`)로 추후 CAPI 중복제거 가능.
   - 서버: `navigator.sendBeacon('/api/install-click')`(실패 시 `fetch keepalive`). `preventDefault`를 쓰지 않아 스토어 이동(새 탭)을 막지 않는다.
3. 스토어 URL 전달
   - **Android**: `&referrer=utm_source=…&utm_medium=…&utm_campaign=…&click_id=…&placement=…&fbclid=…` (URL 인코딩). 앱이 Play Install Referrer API로 읽어야 의미가 있다.
   - **iOS**: App Store는 임의 파라미터를 앱에 넘기지 않는다. `NEXT_PUBLIC_APPSTORE_PROVIDER_TOKEN`(App Store Connect 공급자 토큰 `pt`)을 **빌드 시점에** 설정한 경우에만 `pt/ct(캠페인)/mt=8`을 붙인다(App Analytics 캠페인 집계용). 미설정이면 URL 불변.

## 2. 서버 로그 엔드포인트

랜딩은 정적 export(`output: 'export'`)라 API 라우트가 없다 → `scripts/install-click-server.mjs`(의존성 없음)를 별도 프로세스로 띄운다.

| 엔드포인트 | 설명 |
|---|---|
| `POST /api/install-click` | 클릭 1건 기록(204). 본문은 text/plain JSON |
| `GET /api/install-click/summary?days=7` | 집계(스토어·위치·캠페인·일자). `Authorization: Bearer $INSTALL_CLICK_ADMIN_TOKEN` |
| `GET /api/install-click/recent?limit=50` | 최근 N건(fbc/fbp 제외). 동일 인증 |
| `GET /api/install-click/health` | 상태 |

기록 필드: 시각(UTC+KST), store, placement, click_id, fbclid, utm_*, fbc/fbp, UA, referer, page, href, 국가(cf-ipcountry), IP 일별 솔트 해시(원문 미저장).
저장: `data/install-clicks/install-clicks-YYYY-MM-DD.jsonl` (git 제외).

조회 (서버에서):
```bash
yarn install-click:report --days 7 --recent 20         # 토큰 불필요, 로그 직접 집계
curl -H "Authorization: Bearer $INSTALL_CLICK_ADMIN_TOKEN" https://gugu.smap.site/api/install-click/summary?days=7
```

### 배포 절차 (승인 후, AWS)
```bash
cd /home/jin/projects/gugu/web && git pull
yarn install --frozen-lockfile && yarn build          # NEXT_PUBLIC_* 는 빌드 타임
# 로그 서버 (토큰은 서버 셸에서 직접 생성·설정, 채팅/커밋에 노출 금지)
INSTALL_CLICK_ADMIN_TOKEN="$(openssl rand -hex 24)" INSTALL_CLICK_LOG_DIR=/home/jin/projects/gugu/data/install-clicks \
  pm2 start scripts/install-click-server.mjs --name gugu-install-click --update-env && pm2 save
pm2 restart gugu-web
```
라우팅: `/api/install-click*` → `127.0.0.1:4318` (Cloudflare Tunnel ingress 또는 nginx에서 경로 규칙 추가). 이 라우팅이 없으면 sendBeacon이 404가 되어 서버 로그가 쌓이지 않는다(Pixel 이벤트는 영향 없음). 대안: `NEXT_PUBLIC_INSTALL_CLICK_ENDPOINT`로 별도 도메인 지정 + `INSTALL_CLICK_ORIGINS` 허용.

### 배포 후 확인
1. `https://gugu.smap.site/?fbclid=TEST123&utm_source=facebook&utm_campaign=test` → 스토어 버튼 href에 `referrer=…` 포함(Android UA).
2. Events Manager → Test Events에서 `Lead`, `install_click` 수신(파라미터 `placement`, `fbclid`).
3. 버튼 클릭 후 `yarn install-click:report --recent 5`에 행 추가.
4. `/play/` 설치 유도 모달(InstallPrompt) 버튼도 동일하게 동작.

## 남은 작업 (실제 설치·구매 검증)

클릭 로그는 "관심"이지 설치가 아니다. 아래가 끝나야 광고 → 설치 → 구매 퍼널이 닫힌다.

- [ ] **Meta 앱 등록**: developers.facebook.com에 앱(iOS/Android) 등록, App ID 확보, Events Manager에서 앱을 광고 계정·픽셀과 연결
- [ ] **앱에 Meta SDK 또는 MMP 연동** (택1)
  - Meta SDK(Capacitor 플러그인 또는 네이티브): `fb_mobile_activate_app`(설치/첫 실행) 자동 수집
  - 또는 MMP(AppsFlyer/Adjust/Singular): 설치 어트리뷰션 + Meta 포스트백 (SKAN 지원 포함). 스토어 링크를 MMP 원링크로 교체하면 iOS 귀속도 가능
- [ ] **Android**: 앱에서 Play Install Referrer API로 `referrer`(fbclid/utm/click_id) 읽어 첫 실행 시 서버/SDK에 전달 → 서버 클릭 로그의 `click_id`와 조인
- [ ] **iOS**: SKAdNetwork/AdAttributionKit(+ATT 동의 시 IDFA) 설정. 웹 fbclid는 앱으로 이어지지 않음 → MMP 또는 Meta의 앱 설치 광고(App Install 캠페인) 사용
- [ ] **앱 이벤트 정의·전송**: `fb_mobile_activate_app`(설치), `fb_mobile_complete_registration`(가입), `fb_mobile_purchase`(구매; 금액·통화·상품 ID 포함). iOS StoreKit / Google Play Billing 성공 콜백에서 발화. 어린이 대상 앱(4세+)이므로 **COPPA/아동 개인정보·Apple Kids/Google Families 정책, 광고 ID 수집 제한 검토 필수** (법적 검토)
- [ ] **서버 검증(권장)**: 영수증/구독 검증 후 Conversions API(앱 이벤트)로 Purchase 전송, `eventID` 중복제거
- [ ] **Events Manager 검증**: 앱 Test Events로 설치·구매 수신 확인 → 충분한 볼륨 후에만 캠페인 최적화 목표를 App Install/Purchase로 변경 (지금은 **보류** 유지)
- [ ] **스토어 측 교차검증**: App Store Connect App Analytics(캠페인 링크 pt/ct), Play Console → 설치 소스(referrer) 리포트와 Meta/클릭 로그 수치 비교
- [ ] **개인정보 처리방침/동의**: 클릭 로그(IP 해시·UA·fbclid) 수집 고지 반영, 보존 기간 정책(예: 90일 후 삭제 cron)
- [ ] **로그 서버 운영**: pm2 상시화, 로그 로테이션/보관, 토큰 로테이션, 라우팅 헬스체크
