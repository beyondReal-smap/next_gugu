# 인증(Supabase) + 구매이력 계정 연동 설정 가이드

> 작성일 2026-07-06. 소셜(Apple/Google)·이메일 로그인과 "앱 IAP 구매 → 웹 프리미엄 인식"을 위한 설정 절차.
> 코드는 구현 완료 — 이 문서는 **대시보드/콘솔 설정과 네이티브 빌드 단계**를 다룬다.

> **⚠️ 2026-07-06 결정: 결제는 iOS/Android IAP 전용, 웹 결제 없음. 소셜(Apple/Google) 로그인 보류 — 이메일 로그인만 노출.**
> - §2(Apple)·§3(Google) 콘솔 설정은 **당장 불필요** — 소셜 로그인을 다시 열 때만 수행.
> - 재활성화 방법: 콘솔 설정 완료 후 `features/auth/AuthScreen.tsx`의 `SOCIAL_LOGIN_ENABLED = true` + (네이티브는) `.env.local`의 Google 클라이언트 ID 채우고 재빌드.
> - 완료된 설정: Supabase 프로젝트 `lhwsxzzqcqvvpreriult` 연결, `api/.env`(URL·service_role), `web/.env.local`(URL·anon), 이메일 로그인 활성.

## 아키텍처 한눈에

```
[앱(iOS/Android)]                         [웹 브라우저 gugu.smap.site]
  네이티브 소셜 로그인(@capgo)                signInWithOAuth (PKCE)
    → idToken                                 → /auth/callback/ 세션 교환
    → supabase.auth.signInWithIdToken   ─┐  ┌─
                                          ▼  ▼
                                   [Supabase Auth]  ← 이메일/비밀번호 공통
                                          │ JWT (Bearer)
                                          ▼
[IAP 구매/복원] ──receipt──▶ [gugu-api FastAPI :5106]
                              ├ 영수증 검증(Apple/Google)
                              ├ JWT 검증(JWKS) → purchases.user_id 연결
                              ├ GET /api/premium/status  ← 웹·타 기기 프리미엄 조회
                              └ DELETE /api/account      ← 계정 삭제(5.1.1(v))
                                          │
                                          ▼
                                 [MariaDB gugu.purchases (+user_id)]
```

- 프리미엄 판정: **로컬 영수증 OR 계정 연동** (`PremiumProvider`) — 게스트/비로그인 구매도 계속 인식
- DB 스키마: API 기동 시 `ensure_schema()`가 `purchases.user_id` 컬럼을 멱등 추가 (수동 마이그레이션 불필요)

## 1. Supabase 프로젝트

1. https://supabase.com/dashboard → 새 프로젝트 생성
2. **Authentication → Sign In / Up**:
   - Email 활성화 (Confirm email 권장: 켬)
   - Apple 활성화 → 아래 2번의 Services ID/Key 입력
   - Google 활성화 → 아래 3번의 클라이언트 ID 입력 + **Authorized Client IDs**에 iOS/웹/Android 클라이언트 ID 모두 추가 (네이티브 idToken 허용에 필요)
3. **Authentication → URL Configuration**:
   - Site URL: `https://gugu.smap.site`
   - Redirect URLs: `https://gugu.smap.site/auth/callback/`
4. **Settings → JWT Keys**: 신규 프로젝트는 ES256(JWKS) 기본 — 그대로 사용.
   레거시 HS256 프로젝트면 `api/.env`에 `SUPABASE_JWT_SECRET` 설정.
5. 키 복사 → `api/.env`(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY), `web/.env.local`(URL, ANON_KEY)

## 2. Apple 설정 (developer.apple.com)

| 항목 | 값 |
|------|-----|
| App ID | `site.smap.gugudan` → **Sign in with Apple** capability 체크 |
| Services ID (웹 로그인용) | 예: `site.smap.gugudan.web` → Sign in with Apple 활성화, Return URL: `https://<project-ref>.supabase.co/auth/v1/callback` |
| Key | Sign in with Apple 키 생성 → Supabase Apple provider에 Key ID·Team ID·.p8 입력 |

Xcode(실제 출시 프로젝트): 타깃 → Signing & Capabilities → **+ Sign in with Apple** 추가.

## 3. Google 설정 (console.cloud.google.com)

OAuth 클라이언트 3개 생성 (APIs & Services → Credentials):

| 유형 | 용도 | 비고 |
|------|------|------|
| Web | Supabase provider + Android idToken audience | `web/.env.local`의 `NEXT_PUBLIC_GOOGLE_WEB_CLIENT_ID` |
| iOS | 네이티브 iOS 로그인 | Bundle ID `site.smap.gugudan`. `NEXT_PUBLIC_GOOGLE_IOS_CLIENT_ID` |
| Android | 네이티브 Android 로그인 | 패키지 `site.smap.gugudan` + 서명 **SHA-1** 등록 (Play Console → 앱 서명 키 SHA-1 포함) |

- iOS: `Info.plist`에 URL Scheme 추가 — iOS 클라이언트의 **reversed client ID** (`com.googleusercontent.apps.xxxx`)
- Supabase Google provider의 Authorized Client IDs에 **3개 모두** 추가

## 4. 네이티브 빌드 (Capacitor 프로젝트 기준)

```bash
cd web
yarn build            # dist/ 정적 export (env는 .env.local에서 주입)
npx cap sync          # @capgo/capacitor-social-login 네이티브 반영
```

- iOS: `npx cap open ios` → Sign in with Apple capability + Google URL Scheme 확인 → 빌드 번호 올려 아카이브
- Android: `npx cap open android` → 서명 후 빌드

### ⚠️ 출시 빌드가 별도(Mac 수제 셸) 프로젝트인 경우

이 저장소의 웹 코드는 그대로 쓰되, 네이티브 쪽에 두 가지가 필요하다:

1. **소셜 로그인**: 네이티브 Apple/Google 로그인 구현 후 idToken을 웹뷰 JS로 전달 →
   JS에서 `supabase.auth.signInWithIdToken({ provider, token })` 호출
   (또는 capgo 플러그인 대신 커스텀 브릿지 함수를 `lib/auth/socialLogin.ts`의 네이티브 분기에 연결)
2. **IAP 계정 연결**: 네이티브가 `/api/iap/verify`(또는 cdv-validate) 호출 시
   웹뷰의 `window.__GUGU_AUTH_TOKEN__` 값을 읽어 `Authorization: Bearer <token>` 헤더로 첨부.
   토큰이 없으면(비로그인) 기존대로 헤더 없이 호출 — 서버는 두 경우 모두 처리한다.
   cordova-plugin-purchase v13 사용 시: `store.validator = { url, headers: { Authorization: ... } }`

## 5. 검증 체크리스트

- [ ] 웹: Apple/Google/이메일 로그인 → 새로고침 후 세션 유지
- [ ] 앱: 네이티브 소셜 로그인 성공
- [ ] 앱에서 로그인 상태로 구매/복원 → DB `purchases.user_id` 채워짐
- [ ] 같은 계정 웹 로그인 → 프로필 계정 섹션에 "프리미엄 · 계정 연동됨"
- [ ] 계정 삭제 → Supabase 사용자 제거 + `user_id` NULL + 게스트 복귀
- [ ] 비로그인 게스트: 학습 기능 전부 동작 (회귀 없음)

## 6. App Store 심사 메모

- **4.8**: Google 로그인을 제공하므로 Sign in with Apple을 같은 화면 최상단에 노출 (구현됨)
- **5.1.1(i)**: 로그인은 선택 — 게스트로 전 기능 사용 가능 (구현됨)
- **5.1.1(v)**: 앱 내 계정 삭제 제공 (프로필 → 계정 → 계정 삭제)
- App Privacy: 이메일 주소 수집이 추가되므로 라벨에 "Email Address — App Functionality, Linked to You" 반영 필요 (추적 아님)
