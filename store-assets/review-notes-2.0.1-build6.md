# 재심사 노트 — 구구 어드벤처 v2.0.1 (Build 6)

- **Submission ID (이전 리젝):** 6bd79641-52e5-4951-b7b3-2a34be2344e7
- **이전 리젝 버전:** 2.0.0 (5)
- **재제출 버전:** 2.0.1 (6)
- **해결 대상:** Guideline 5.1.2(i) — Privacy / Data Use, Guideline 2.3.2 — Accurate Metadata

---

## ① App Store Connect > 앱 버전 > "심사 정보(Review Notes)" 칸에 붙여넣기 (영문)

```
This build addresses the two issues from the previous rejection (Submission ID: 6bd79641-52e5-4951-b7b3-2a34be2344e7).

[Guideline 5.1.2(i) — App Tracking Transparency]
We have added the App Tracking Transparency (ATT) framework. On the first launch, the app presents the system tracking permission prompt on the main screen, right after the app loads (NSUserTrackingUsageDescription is defined in Info.plist). If the user denies tracking, analytics tracking is disabled at runtime, so no data used for tracking is collected. To reproduce: launch the app for the first time (or reset "Allow Apps to Request to Track" in Settings > Privacy & Security > Tracking) — the permission prompt appears on the home screen.

[Guideline 2.3.2 — Promotional Image]
We have disabled App Store promotion for the affected In-App Purchase so that the promotional image is no longer displayed.

Thank you for the review.
```

---

## ② 리젝 메시지에 대한 "답장(Reply)" 문구 (App Store Connect 메시지 스레드)

### 5.1.2(i)에 대한 답장 (영문)
```
The app now uses the App Tracking Transparency framework and requests the user's permission via ATTrackingManager before any tracking. The permission prompt is shown on the first launch, on the app's main screen. If the user declines, analytics tracking is turned off at runtime. The tracking usage description (NSUserTrackingUsageDescription) has been added to Info.plist. Our App Privacy information in App Store Connect is up to date and consistent with this behavior.
```

### 2.3.2에 대한 답장 (영문)
```
We have disabled the App Store promotion for the In-App Purchase in question, so the promotional image that duplicated the app icon is no longer shown on the App Store.
```

---

## ③ 대표님 체크리스트 (제출 전)

- [ ] **개인정보 라벨**: Purchase History의 "추적에 사용(Used to Track You) = 예" 유지 → 이제 ATT가 있으므로 정합
- [ ] **이슈 2 조치**: 수익 창출 > 인앱 구입 > 해당 항목 > **App Store 프로모션 비활성화**
- [ ] **빌드 번호 6** 아카이브 (코드에서 이미 6으로 설정됨)
- [ ] Xcode Archive → 업로드 → 새 빌드(6) 선택
- [ ] 위 ① Review Notes 붙여넣기
- [ ] (선택) 실기/시뮬레이터에서 최초 실행 시 ATT 팝업 1회 확인

---

## 변경 요약 (이번 빌드에 포함된 코드)

- `capacitor-plugin-app-tracking-transparency@2.0.5` 도입
- `Info.plist`: `NSUserTrackingUsageDescription` 추가 (팝업 문구, 한국어)
- `src/utils/att.ts`: ATT 권한 요청 + 거부 시 GA(`ga-disable`) 비활성화
- `components/AppShell.tsx`: 최초 실행 시 iOS에서 ATT 팝업 호출
- 빌드 번호 5 → 6 (`CURRENT_PROJECT_VERSION`)
