# Meta Pixel 이벤트 정의 (구구 어드벤처)

Pixel ID: `2105591250833042` (PersonaFit 데이터셋 재사용)  
오버라이드: `NEXT_PUBLIC_META_PIXEL_ID`

## 구현 범위 (이 PR)

| 이벤트 | 유형 | 발화 시점 | 파라미터 |
|---|---|---|---|
| `PageView` | 표준 | 웹 로드 (MetaPixelLoader) | — |
| `ViewContent` | 표준 | `/play/` 홈 진입 | `content_name=web_play` |
| `play_start` | 커스텀 | 웹 게임 세션 실제 시작 (`SessionProvider.start` 성공 시) | `mode`, `table`, `content_name` |
| `app_store_click` | 커스텀 | 랜딩 스토어 버튼 클릭 | `store=ios\|android`, `content_name` |

## 아직 미구현 (승인·연동 후)

- `CompleteRegistration` — 보호자 이메일/소셜 가입 완료
- `Purchase` / 앱 설치 — Meta App Events 또는 MMP 필요 (웹 IAP 없음)
- 최적화 목표를 전환 이벤트로 변경 — **보류**

## 검증

1. 배포 후 https://gugu.smap.site/ → Events Manager Test Events에 `PageView`
2. `/play/` 진입 → `ViewContent`
3. 「빠른 학습 시작」→ `play_start`
4. App Store/Play 버튼 → `app_store_click`
