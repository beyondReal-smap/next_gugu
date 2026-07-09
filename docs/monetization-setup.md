# 수익화 설정 가이드 (자체 서버 검증 방식)

> RevenueCat 없이 **자체 서버(aws ~/smap/smap_gugu/api)에서 영수증을 검증**합니다. 수수료 없음, 데이터 자체 보유.

## 구성 요약

- **모델**: **단건 결제 ₩4,400 평생 이용권** (정기 결제 아님, 1회 구매로 영구 해제)
- **상품 ID**: `site.smap.next.premium` — iOS 비소모성(Non-Consumable) / Android 1회성 인앱 상품
- **결제 플러그인**: cordova-plugin-purchase v13 (iOS StoreKit / Android Billing 9 통합)
- **영수증 검증**: 앱 → `https://gugu.smap.site/api/iap/verify` (FastAPI) → Apple/Google 서버에 재확인 → MariaDB `gugu.purchases` 기록
- **무료 경계**: 학습·스피드런·전체랜덤·단선택 = 무료 / 3D 어드벤처·60초 챌린지·서바이벌·빈칸 추리·OX 퀴즈 = 프리미엄
- **웹(gugu.smap.site)**: 자동 데모 모드 — 프리미엄 진입 시 "앱에서 구매" 안내

## 서버 구성 (구축 완료 ✅)

| 항목 | 값 |
|---|---|
| 위치 | aws `~/smap/smap_gugu/api` (FastAPI + uv) |
| 프로세스 | pm2 `gugu-api` (127.0.0.1:5106) |
| 라우팅 | Cloudflare Tunnel: `gugu.smap.site/api/*` → 5106, 나머지 → 5104(정적 웹) |
| DB | MariaDB `gugu.purchases` (계정 gugu@localhost, 비번은 서버 `api/.env`) |
| 엔드포인트 | `GET /api/health`, `POST /api/iap/cdv-validate`(플러그인 validator), `POST /api/iap/verify`(수동) |
| iOS 검증 | Apple verifyReceipt — 프로덕션→샌드박스(21007) 폴백, **별도 키 불필요** |
| Android 검증 | Play Developer API — **서비스 계정 JSON 필요** (아래 3번) |

서버 코드 수정 시: 로컬 스크래치 → `scp main.py aws:~/smap/smap_gugu/api/` → `pm2 restart gugu-api`

## 1. App Store Connect (영어 UI)

1. **Business > Agreements** → **Paid Apps Agreement** 활성화 (+은행·세금 정보)
2. **Apps > Monetization > In-App Purchases** → 생성:
   - Type: **Non-Consumable** / Product ID: `site.smap.next.premium` / Price: ₩4,400
   - Display Name(ko): 평생 이용권 / 심사용 스크린샷: 페이월 화면
3. 앱 버전 페이지 **In-App Purchases and Subscriptions** 섹션에 상품 첨부 (첫 심사 동시 진행)
4. **Users and Access > Sandbox > Testers** 에서 테스트 계정 생성 → 실기기 테스트

> RevenueCat용 In-App Purchase Key(.p8)는 **불필요** (자체 검증은 verifyReceipt 사용)

## 2. Google Play Console

1. **수익 창출 설정** → 판매자 계정 등록
2. **수익 창출 > 상품 > 인앱 상품** → 생성: `site.smap.next.premium`, ₩4,400
3. 내부 테스트 트랙에 AAB 업로드 + 라이선스 테스터 등록

## 3. Google 서비스 계정 (Android 검증에 필수)

1. Google Cloud Console → 서비스 계정 생성 → JSON 키 다운로드
2. Play Console > **API 액세스**에서 해당 서비스 계정 연결 + "재무 데이터 보기" 권한
3. 서버에 배치: `scp 키.json aws:~/smap/smap_gugu/api/google-service-account.json && ssh aws chmod 600 ...`
4. `pm2 restart gugu-api`
> 미설정 상태에서 Android 검증 요청은 503으로 명확히 실패합니다 (iOS는 즉시 동작).

## 4. 출시 후 할 일

- `lib/premiumConfig.ts`의 `STORE_URLS`에 실제 스토어 링크 입력 → 웹 데모 CTA 활성화

## 법적 고지 페이지 (배포 완료 ✅)

| 용도 | URL |
|---|---|
| 이용약관 | https://gugu.smap.site/terms |
| 개인정보처리방침 | https://gugu.smap.site/privacy — 양 스토어 등록 시 입력 |
| 데이터 삭제 안내 | https://gugu.smap.site/delete-account — Play 데이터 보안 폼에 입력 |

## 5. 테스트

- **iOS**: Sandbox 계정으로 실기기 구매 → 서버 로그(`pm2 logs gugu-api`)에서 "검증 성공" 확인 → 잠금 해제
- **Android**: 내부 테스트 트랙 + 라이선스 테스터 → 동일 확인
- 확인 항목: 구매 → 즉시 해제 / 재설치 후 **구매 복원** / 조작 영수증 거부(서버가 차단)

## 기술 메모

- iOS 최소 버전 14.0 / Android minSdk 23·compile/targetSdk 35 (Billing 9 요구로 상향)
- 게이트: `features/home/Home.tsx`, `features/learn/Learn.tsx` / 페이월: `features/paywall/Paywall.tsx` / 상태: `lib/state/PremiumProvider.tsx`
- 프리미엄 캐시: localStorage `gugu.premium.v1` (오프라인 대비) + 스토어 owned 상태로 매 실행 보정
- 검증 흐름: 구매 → approved → `store.validator`(자체 서버) → 서버가 Apple/Google 재확인 → verified → finish + 프리미엄 부여. 검증 거부 시 미부여(조작 방어)
- **환불 회수**: 기기 영수증 로딩 완료 시점(`receiptsReady`)에 owned=false면 프리미엄 회수 + "구매 복원" 실행 시에도 스토어 결과 그대로 반영(회수 포함). 오프라인/로딩 중에는 판정하지 않아 정상 구매자가 잘못 잠기지 않음. Android는 환불 후 다음 실행에 즉시 회수, iOS는 기기 영수증 갱신 특성상 복원·재검증 시점에 회수될 수 있음
