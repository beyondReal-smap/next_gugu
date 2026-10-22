# Supabase 인증 메일 템플릿

앱 이메일 OTP 로그인·이메일 연결 메일의 원본. Supabase 대시보드(Authentication > Emails > Templates)에
적용된 내용과 같아야 한다 — 대시보드만 고치지 말고 여기를 먼저 고친 뒤 적용한다.

| 파일 | 대시보드 템플릿 | 쓰는 곳 |
|---|---|---|
| `magic_link.html` | Magic link or OTP | 기존 계정 로그인 (`/auth/v1/otp`, create_user false) |
| `email_change.html` | Change email address | 익명 계정에 이메일 연결 (`PUT /auth/v1/user`) |
| `subjects.txt` | 각 템플릿 제목 | |

- 2026-10-22: 다국어 출시(2.6.0)에 맞춰 한국어·영어·일본어 3개 언어를 한 메일에 함께 적는다
  (Supabase 템플릿은 받는 사람 언어로 분기할 수 없어서). 이전 한국어 전용판은 `previous-ko/` (되돌릴 때 사용).
- `{{ .Token }}`(6자리 코드)·`{{ .Email }}`·`{{ .NewEmail }}` 은 Supabase Go 템플릿 변수다. 지우면 코드가 안 간다.
