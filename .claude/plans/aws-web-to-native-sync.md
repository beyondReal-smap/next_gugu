# AWS 웹 최신본 → 네이티브 iOS/Android 반영 계획 (초안)

> 작성 2026-09-18 · 기준: `aws:~/projects/gugu/web` 작업트리 (HEAD `d788b4b` + 미커밋 변경 92개)
> 비교 대상: 로컬 `/Users/genie/SmapSource/gugu` (HEAD `a5280d2`) 와 `native-ios/`, `native-android/`
> AWS 소스 사본: 스크래치 `aws-gugu/web/` (rsync, node_modules·dist 제외)

## 규모

| 구분 | 수치 |
|---|---|
| AWS 작업트리 변경 파일 | 92개 (앱 소스 78개) |
| 기존 파일 변경 | 35개 / 약 2,200줄 증감 |
| 신규 모듈 | 26개 파일 / 약 2,400줄 (`lib/learning`, `lib/api`, `features/auth` 등) |
| 네이티브 기존 구현 | iOS 45 Swift 파일 / Android 40 Kotlin 파일 |

## A. 도메인 로직 변경 — 네이티브 Core 필수 반영

| 웹 파일 | 변경 내용 | iOS 대상 | Android 대상 |
|---|---|---|---|
| `lib/types.ts` | `GameMode`에 `adventure` 추가, `AnswerRecord.given`, `SessionResult.partial/xpScale`, `CommitResult.partial`, `GameState.dayLog`, `DayLogEntry`/`WeeklyReport`, `UserRole`/`FontScale`/`GraphicsQuality` | `Core/Models.swift` | `core/Models.kt` |
| `lib/state/commit.ts` | **스트릭 규칙 변경**: 방문만으로 상승 → 세션 완료 또는 일일 정답 하한(`min(dailyGoal,10)`) 도달 시 `qualifyStreak`. 부분 커밋(중도 이탈) 시 별/신기록/추이/모드탐험 스킵. `dayLog` 56일 링버퍼 누적. XP에 `xpScale` 배율 | `Core/Commit.swift` | `core/Commit.kt` |
| `lib/modes.ts` | `adventure` 모드 정의 추가(학습 탭 목록에선 제외, `xpBonus: 3`) | `Core/Modes.swift` | `core/Modes.kt` |
| `lib/problems.ts` | `dominantWrongTable()` 신규 (오답 가중치 최다 단) | `Core/Problems.swift` | `core/Problems.kt` |
| `lib/achievements.ts` | `mode_explorer` 판정을 `MODE_LIST` 전수 포함으로 변경 | `Core/Achievements.swift` | `core/Achievements.kt` |
| `lib/adventure/battle.ts` | `battleMode()` → 항상 `adventure`, `REMATCH_XP_SCALE = 0.3` (재대결 XP 체감) | `Core/Adventure/Battle.swift` | `core/adventure/Battle.kt` |
| `lib/adventure/progress.ts` | 지역 해금 규칙 변경: 구규칙 ∪ 로드맵 직전 보스 ∪ 해당 단 별≥1 (단조 증가) | `Core/Adventure/Progress.swift` | `core/adventure/Progress.kt` |
| `lib/hints.ts` (신규) | `ROADMAP_TABLES = [2,5,3,4,6,9,7,8]`, `hintForTable()` 암산 전략, `parentTipOfWeek()` | `Core/Hints.swift` (신규) | `core/Hints.kt` (신규) |

## B. 앱 기능 신규 — 네이티브 신규 구현

| 기능 | 웹 모듈 | 네이티브 작업 | 외부 의존 |
|---|---|---|---|
| 접근성/환경설정 (글자 크기 3단, 그래픽 품질, 역할) | `lib/state/PrefsProvider.tsx` | `Store/PrefsStore` + Profile UI | 없음 |
| 문제 TTS 낭독 | `lib/a11y/speak.ts` | `AVSpeechSynthesizer` / Android `TextToSpeech` | OS 내장 |
| 중도 이탈 확인 + 부분 커밋 | `components/ui/ConfirmSheet.tsx`, Session/Battle | 확인 시트 컴포넌트 + 부분 커밋 연결 | 없음 |
| 구구 점프(러너) 미니게임 | `features/runner/RunnerScreen.tsx`(435줄), `lib/runner.ts` | 신규 화면 (SVG → SwiftUI Canvas / Compose Canvas) | 없음 |
| 코치 힌트 (오답 2회 시 쉬운 설명) | `features/session/CoachHint.tsx`, `lib/api` | 신규 UI + API 클라이언트 | 서버 API |
| 보호자 주간 리포트 | `features/profile/ParentReport.tsx`, `lib/report/weekly.ts` | 신규 화면 + 로컬 파생 집계 | 서버 API(장기) |
| 학습 리마인더 알림 | `lib/native/reminders.ts`, `components/ReminderSync.tsx` | `UNUserNotificationCenter` / `WorkManager` | 없음 |
| 계정/소셜 로그인 | `features/auth/*`, `lib/auth/socialLogin.ts`, `lib/supabase/client.ts`, `lib/state/AuthProvider.tsx` | 로그인 화면 + Supabase 세션 | Supabase, Apple/Google 로그인 |
| 게스트 기록 귀속(claim) | `features/auth/ClaimScreen.tsx`, `lib/learning/merge.ts` | 귀속 선택 UI + 병합 로직 | 서버 API |
| 서버 학습 동기화 + 오프라인 아웃박스 | `lib/learning/*`(7파일), `lib/sync/outbox.ts`, `lib/api/learning.ts` | 이벤트 큐/재시도/id 리맵 | 서버 API |
| 적응형 복습 계획 (5~30문항) | `lib/learning/remote.ts`, `planQueue.ts` | 출제 큐 대체 경로 | 서버 API |
| 프리미엄 서버 검증 | `lib/api/premium.ts`, `PremiumProvider.tsx`(350줄 변경) | StoreKit 2 ↔ 서버 검증 연동 | 서버 API |

## C. 웹 전용 — 네이티브 반영 제외

`features/landing/*`(마케팅 랜딩), `features/webTrial/*`(웹 체험 배너/설치 유도), `components/AnalyticsLoader.tsx`(GA 웹 한정), `app/play`, `/guide`, `llms.txt`, SEO 메타데이터.

## D. 단계 제안

1. **Phase 1 — Core 동기화** (A 전체): 순수 로직이라 위험 낮고 iOS/Android 동일 이식. 기존 `CoreTests.kt`/`GuguTests` 확장으로 검증.
2. **Phase 2 — 서버 없이 되는 앱 기능**: Prefs/TTS/부분커밋+확인시트/알림/러너.
3. **Phase 3 — 서버 연동**: 계정·학습 동기화·복습 계획·코치·보호자 리포트·프리미엄 검증. 엔드포인트 계약 확정 필요(`aws:~/projects/gugu/api`).

## E. 선결 확인 사항

1. AWS 작업트리가 **미커밋 상태** — 기준 스냅샷을 커밋/태그로 고정할지 (이후 재동기화 diff 기준 확보)
2. 로컬 `master` ↔ `origin/master` 갈라짐 + AWS 작업트리 3중 상태 정리 방향
3. Phase 3 서버 API 계약 문서 위치 (`aws:~/projects/gugu/api`, `docs/`) 확인

---

## Phase 1 진행 기록 (2026-09-18)

### 완료 — A(도메인 로직) iOS/Android 동시 이식

| 변경 | iOS | Android |
|---|---|---|
| `adventure` 모드 + 모드 정의 | `Core/Models.swift`, `Core/Modes.swift` | `core/Models.kt`, `core/Modes.kt` |
| 스트릭 자격 규칙(`qualifyStreak`) + 부분 커밋 + `dayLog` + XP 배율 | `Core/Commit.swift` | `core/Commit.kt` |
| `dominantWrongTable` | `Core/Problems.swift` | `core/Problems.kt` |
| `mode_explorer` 판정 | `Core/Achievements.swift` | `core/Achievements.kt` |
| `battleMode`→adventure, `REMATCH_XP_SCALE` | `Core/Adventure/Battle.swift` | `core/adventure/Battle.kt` |
| 지역 해금 규칙 + `roadmapPrevTable` | `Core/Adventure/Progress.swift` | `core/adventure/Progress.kt` |
| `ROADMAP_TABLES` | `Core/Hints.swift` (신규) | `core/Hints.kt` (신규) |
| 재대결 판정 연결 | `Features/Adventure/Battle/BattleEngine.swift` | `features/adventure/battle/BattleEngine.kt` |
| 해금 판정에 별점 전달 | `Features/Adventure/RegionMap.swift` | `features/adventure/AdventureScreen.kt` |
| adventure 아이콘/틴트(웹: Swords/indigo) | `DesignSystem/Components.swift` | `designsystem/Components.kt`, `features/home/HomeScreen.kt` |

### 검증

- Android `:app:testDebugUnitTest` — **30/30 통과** (신규 9케이스 포함)
- iOS Core `swiftc -typecheck` 통과. 전체 빌드/테스트는 Xcode 라이선스 미동의로 대기
  (`sudo xcodebuild -license accept` 필요)

### Phase 1에서 의도적으로 제외 (해당 기능 단계에서 함께 이식)

- `AnswerRecord.given` — 서버 학습 동기화(Phase 3)용 필드
- `WeeklyReport` 타입 / `lib/report/weekly.ts` — 보호자 리포트(Phase 3)
- `UserRole` / `FontScale` / `GraphicsQuality` — Prefs(Phase 2)
- `hints.ts` 의 `hintForTable` / `parentTipOfWeek` — 코치 힌트·보호자 팁 UI(Phase 2/3)
- 부분 커밋의 **UI 트리거**(중도 이탈 확인 시트) — Phase 2. 현재 Core는 `partial` 을 받을 준비만 된 상태

### 마이그레이션 주의

`GameState` 에 `dayLog` 가 추가됐다. iOS는 `JSONDecoder` 가 키 누락 시 실패하므로 `[DayLogEntry]?` 옵셔널로 두어
구버전 저장본이 초기화되지 않게 했고(테스트 `testDayLogMigrationFromLegacyPayload`), Android는 kotlinx 기본값으로 흡수한다.

---

## Phase 2 진행 기록 — 구구 점프(러너) 이식 (2026-09-18)

웹 `features/runner/RunnerScreen.tsx`(435줄) + `lib/runner.ts`(105줄) → 네이티브 양쪽 이식 완료.

| 계층 | iOS | Android |
|---|---|---|
| 게임 규칙 (순수 로직) | `Core/Runner.swift` | `core/Runner.kt` |
| 진행 상태·기록 저장 | `Features/Runner/RunnerEngine.swift` | `features/runner/RunnerEngine.kt` |
| 무대 렌더링 | `Features/Runner/RunnerScene.swift` (SwiftUI Canvas) | `features/runner/RunnerScene.kt` (Compose Canvas) |
| 화면 UI | `Features/Runner/RunnerView.swift` | `features/runner/RunnerScreen.kt` |
| 진입 카드 | `Features/Home/HomeView.swift`, `Features/Learn/LearnView.swift` | `features/home/HomeScreen.kt`, `features/learn/LearnScreen.kt` |
| 오버레이 연결 | `App/RootView.swift` + `Store/Router.swift` | `MainActivity.kt` + `store/Stores.kt` |
| 기록 키 | `Persistence.runnerBestKey` | `Persistence.RUNNER_BEST_KEY` |

기록 키는 웹 localStorage 와 동일한 `gugu.runner.best.v1`, 단별 최고 기록 맵(`"all"`/`"2"`~`"9"`) 구조도 그대로 유지했다.

### 웹과 의도적으로 다른 점

- **무대 아트워크**: 웹은 SVG path 60여 개로 공룡·바위·그루터기를 그린다. 네이티브는 같은 좌표계(720×260)와
  팔레트를 쓰되 Canvas 도형으로 재구성했다 (패스 단위 1:1 복제 아님). 실루엣·색·애니메이션 타이밍은 동일.
- **키보드 조작(1·2·3, Space, Esc)**: 웹 전용. 네이티브는 터치만 제공.
- **웹 체험판 판 수 차감(`tryPlay`)**: 웹 전용 로직이라 제외.

### 검증

- Android 단위 테스트 **38/38** (러너 8케이스 신규), iOS **38/38** (동일 8케이스 미러링)
- Android 에뮬레이터 실기: 홈 카드 노출 → 진입 → 시작 → 오답/시간초과 시 하트 감소 → 게임 오버 → 재시작 → 최고 기록 표시 확인
- **무입력 35초 관찰**: 점수 0 유지, 하트 3개 소진 후 OVER (점수 증가 로그 0건) — 자동 득점 없음 확인
- 정답 탭 1회 = `answer()` 1회 호출 확인 (임시 로그로 검증 후 로그 제거)
- iOS 시뮬레이터 실기: 홈 카드 → 진입 → 시작 → 무대 렌더링/타이머/하트 감소 확인.
  정답 탭 경로는 애니메이션 중 UI 스냅샷이 안정되지 않아 **기기 탭으로는 미검증**, 단위 테스트로만 확인

---

## Phase 2 추가 진행 기록 — 구구 레인·구구 바구니 이식 + 홈 재구성 (2026-09-21)

기준: AWS `~/projects/gugu/web` HEAD `7d37b57` + 미커밋 변경(바구니 모드).
2026-09-18 이후 웹에 추가된 게임 모드 2종과 러너 템포 개선, /play 묶음 재구성을 네이티브 양쪽에 반영했다.

### 이식 대상 (웹 → 네이티브)

| 웹 | iOS | Android |
|---|---|---|
| `lib/laneRunner.ts` (219줄) | `Core/LaneRunner.swift` | `core/LaneRunner.kt` |
| `lib/basket.ts` (81줄) | `Core/Basket.swift` | `core/Basket.kt` |
| `lib/runner.ts` 템포 개선 | `Core/Runner.swift` | `core/Runner.kt` |
| `features/runner/art.tsx` (공유 아트 추출) | `Features/Runner/RunnerArt.swift` | `features/runner/RunnerArt.kt` |
| `features/runner/lane/*` | `Features/LaneRunner/{Engine,Scene,View}` | `features/lanerunner/{Engine,Scene,Screen}` |
| `features/basket/BasketScreen.tsx` | `Features/Basket/{BasketEngine,OrchardScene,BasketView}` | `features/basket/{BasketEngine,OrchardScene,BasketScreen}` |
| `features/home/Home.tsx` 묶음 재구성 | `Features/Home/HomeView.swift` | `features/home/HomeScreen.kt` |
| 학습 탭 게임 링크 | `Features/Learn/LearnView.swift` | `features/learn/LearnScreen.kt` |
| 오버레이/라우팅 | `Store/Router.swift`, `App/RootView.swift` | `store/Stores.kt`, `MainActivity.kt` |

기록 키는 웹 localStorage 와 동일한 `gugu.lane.best.v1` / `gugu.basket.best.v1`,
단별 최고 기록 맵(`"all"`/`"2"`~`"9"`) 구조도 그대로 유지했다.

### 러너 템포 변경 (웹 `6846cb1` 반영)

- `answerWindowMs`: 5개마다 450ms↓·하한 3.2초 → **3개(`SCORE_PER_LEVEL`)마다 500ms↓·하한 2.8초**
- 정답 후 통과 속도 0.3 → `CLEAR_SPEED = 0.45`
- `level()` / `leveledUp()` 추가 — 무대에 "속도 N단계" 표시

### 홈 재구성 (웹 `7d37b57` 반영)

기존 2열 모드 그리드를 놀이 방식별 4묶음으로 교체했다.
차근차근 배우기(학습·빈칸 추리·OX) / 기록 도전(스피드런·60초·서바이벌) /
직접 움직이며 놀기(바구니·점프·레인) / 모험(3D 어드벤처).
웹의 모바일 목록형과 같이 한 줄 카드로 배치하고, 카드마다 규칙 요약(문제 수·제한 시간·하트)과 최고 기록을 적는다.

### 웹과 의도적으로 다른 점

- **키보드 조작**: 레인(↑↓·W/S·1~3·Space·Esc), 바구니(← →)는 웹 전용. 네이티브는 스와이프·드래그와 방향 버튼만 제공
- **무대 아트워크**: 웹 SVG path 를 같은 좌표계·팔레트의 Canvas 도형으로 재구성 (패스 단위 1:1 복제 아님)
- **바구니 무대 높이**: 웹은 ResizeObserver 로 가변. 네이티브는 360×380 고정 좌표계로 그린다
- **웹 체험판 판 수 차감(`tryPlay`)**: 웹 전용이라 제외
- 웹 미커밋 변경 중 `app/globals.css`·`AppShell`·`Keypad`·`SessionScreen` 의 세이프에어리어/낮은 높이 대응은
  **웹 레이아웃 한정 수정**이라 네이티브 대상 아님

### 검증

- Android 단위 테스트 **48/48** (레인 5·바구니 4 케이스 신규), iOS **48/48** (동일 케이스 미러링)
- 기존 `runnerAnswerWindow` 테스트는 새 템포 값으로 갱신
- iOS 시뮬레이터(iPhone 17 Pro) 실기: 홈 4묶음 노출 → 구구 레인 진입·시작·장애물 판정·게임 오버 →
  구구 바구니 진입·시작·열매 낙하·정답 획득(1개)·하트 감소 확인

### 배포

| | 버전 | 업로드 |
|---|---|---|
| iOS | 2.1.0 (build 12) | App Store Connect 업로드 성공 (Delivery UUID `02e38b60-e2e3-4d24-8209-87f60fad2410`) — 심사 미제출 |
| Android | 2.1.0 (vc15) | `:app:publishReleaseBundle` → Play **internal** 트랙 **DRAFT** |
