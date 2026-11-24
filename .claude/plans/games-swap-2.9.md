# 2.9.0 게임 교체 계획 — 3D 어드벤처·놀이 4종 → A 계단 점프·B 곱셈 몬스터 (네이티브)

> 상태: **확정(2026-11-23 대표님 답변 반영) — 구현 중** · 작성 2026-11-23 · 워크트리 `/Users/genie/SmapSource/gugu-games` · 브랜치 `feature/2.9.0-stairs-monster`(기준 `feature/2.8.2-multiplier` 3c4b297)
> 원본 시험판: `/Users/genie/orca/workspaces/gugu/게임-고도화/unity/GuguPinball`(규칙·화면·테스트), 설명 `게임-고도화/docs/AB_PLAYTEST.md`

## 확정된 결정 (2026-11-23 대표님)
1. 구현: Unity 내장 없이 iOS SwiftUI·Android Compose 네이티브 재구현(Unity 규칙·그림·테스트 이식)
2. 제거: 3D 어드벤처 + 놀이 4종(팡팡 Pop·바구니 Basket·점프 Runner·레인 LaneRunner). 학습 모드(60초 챌린지·서바이벌·빈칸·OX·스피드런·학습·복습)는 유지
3. 무료/유료: A·B 둘 다 3문제 체험 후 결제 화면(`MinigameTrial` 재사용)
4. 2.8.2 미커밋분은 `feature/2.8.2-multiplier` 에 커밋 완료(3c4b297)

## 기준선 (변경 전, 2026-11-23)
- iOS `xcodebuild test`(iPhone 17 시뮬): 258 실행·실패 0·건너뜀 1 → TEST SUCCEEDED
- Android `testDebugUnitTest`: 289/289 통과

## 반드시 지킬 것 (검증 완료)
- **`GameMode.adventure` 값은 지우지 않음**: 배틀 결과가 `.adventure` 로 커밋(`Core/Adventure/Battle.swift:40`)되어 기존 사용자 저장본 `modesPlayed`·`bestScores` 에 들어 있음. 값을 지우면 GameState 디코딩 실패 → `Persistence.load` 가 격리 후 nil(`Services/Persistence.swift:28-40`) → **진행도 초기화**. Android `Persistence.kt:146-153` 동일. `Modes.all[.adventure]`(강제 언래핑 `Modes.swift:79`)·`ModeStyle` switch(`Components.swift:138`)도 유지. Phase 0 회귀 테스트로 고정
- `LearningRecord` 의 adventure → practice 매핑(`LearningRecord.swift:39`) 유지(서버 `LearningMode` 6값 계약)
- `gugu.adventure.v1` 저장 키는 지우지 않음(이관 여부는 Q2)

## 1. 제거 목록

| 구분 | iOS (`native-ios/Gugu/`) | Android (`.../site/smap/gugudan/`) |
|---|---|---|
| 어드벤처 화면 | `Features/Adventure/**` | `features/adventure/**` |
| 어드벤처 규칙 | `Core/Adventure/**` | `core/adventure/**` |
| 어드벤처 저장소 | `Store/AdventureStore.swift` | `store/Stores.kt:64-137` AdventureStore, `:264` LocalAdventure |
| 놀이 4종 | `Features/{Pop,Basket,Runner,LaneRunner}/**`, `Core/{Pop,Basket,Runner,LaneRunner}.swift` | `features/{pop,basket,runner,lanerunner}/**`, `core/{Pop,Basket,Runner,LaneRunner}.kt` |
| 놀이 표시 | `Features/Minigame/MinigameStyle.swift`(교체) | `features/home/Minigames.kt`(교체) |
| 테스트 | `PopTests`, `MinigameTrialTests`(재작성), `CoreTests`·`LearnStartTests` 해당 부분 | `PopTests`, `MinigameEntryTests`·`MinigameTrialTests`(재작성), `CoreTests`·`LearnStartTests` 해당 부분 |
| 의존성 | (RealityKit 시스템 프레임워크) | `build.gradle.kts:141` sceneview 4.24.0 제거(Filament .so 빠짐 → 용량 감소 예상), minSdk·ndk 주석 정리 |

고칠 참조: `App/RootView.swift`(adventure 주입·오버레이·minigameView·isImmersive), `Store/Router.swift`(Minigame → `.stairs, .monster`, 타입 이름 유지), `Features/Home/HomeView.swift`(놀이 묶음·어드벤처 히어로 카드), `Features/Learn/PracticeSheet.swift`·`LearnView.swift`(단 시트 놀이 타일), `Features/Paywall/MinigameTrial.swift`(PopTrial)·`PaywallView.swift`(혜택 문구), `Features/Profile/ProfileView.swift:311`("3D 어드벤처"), `DesignSystem/Feedback.swift:39`(AdvAchievements), `Core/PremiumConfig.swift`(popTrialWaves), `Services/Persistence.swift`(놀이 최고 기록 키 4개). Android 대응: `MainActivity.kt`, `Stores.kt`, `Persistence.kt`, `Feedback.kt`, `HomeScreen.kt`, `LearnScreen.kt`, `PracticeSheet.kt`, `PaywallScreen.kt`, `ProfileScreen.kt`. 현지화: `Localizable.xcstrings`, `res/values*/strings.xml`(6개 언어), `tools/i18n/android-keys.json`.

서버: `api/reports.py` AdventureSummary(앱 미사용), `entitlements.py` adventure_pack, push 발신자 "구구 어드벤처" — 2.9.0 에서 서버 변경 불필요. 놀이 4종 언급 없음.

## 2. 옮길 것
- 규칙(UI 무의존): RoundClock·AttemptRecord·StarRules·RisingCloud·SkyZones·Altimeter·StairsSession·MonsterSession·MonsterDex·Wardrobe → Swift `Core/Games/`, Kotlin `core/games/`. ProblemRules 는 앱 `Problems` 재사용(StairsSession 의 MakeChoices 만 이식). IRandom → 앱 관례 `random: () -> Double`. 핀볼 전용(BonusRules·LauncherGesture·RoundSession) 제외. SkyZones·Wardrobe 이름 하드코딩 → 현지화 키
- 테스트: EditMode 36개(PrototypeSessionTests 25·RewardRulesTests 6·Round2RulesTests 5) → XCTest·JUnit. PlayMode 중 규칙성(1,000회 채점 무결성·쉬는 창 시계 정지·결과 때 별 저장·도감 순환)은 엔진 단위 테스트로, 배치·그림은 시뮬레이터 확인 목록으로
- 화면: 기존 놀이 방식 — `@Observable` 엔진 + 60fps 루프 + `Canvas`(iOS `RunnerEngine.swift:102-110`·`RunnerScene.swift:17`, Android `withFrameMillis` + Compose Canvas). 배지·쉬는 창·결과 창은 뷰로 위에 얹음. 빼는 것: PlaytestLog·PrototypeMenu·관찰자 버튼·SafeAreaFitter·TapInput
- 입력: 닿는 순간 반응 + 손가락마다 인정 → iOS `touchesBegan` UIViewRepresentable, Android `pointerInput` 포인터별 down
- 9분할(`card_jelly`·`badge_pill`): iOS `resizable(capInsets:)`, Android 9칸 drawImage 함수
- 그림: PNG 57개 ~5.8MB → iOS `Assets.xcassets/Games/`(universal 1배), Android `drawable-nodpi/game_*.webp`. 압축 후 용량 확인

## 3. 앱 기능 연결
- 저장: `Store/PlayRewardsStore`(키 `gugu.play.v1`, Codable 하나: stars·owned·equipped·dexTotal·dexSeen·stairsBest·monsterBest). 별·기록은 결과 화면 때, 도감은 처치 순간(시험판 그대로)
- 체험: `MinigameTrial`·`TrialNotice`·`TrialProgressPill`·`TrialEndPanel` 재사용, PopTrial 삭제
- 효과음 `Sound.shared` 재사용 + `coin(step:)` 추가, 햅틱 `Haptics` 재사용
- 백그라운드로 가면 자동 쉬는 창(놀이 4종처럼 scenePhase)
- 새 문자열 약 60~80개 × 6개 언어(`tools/i18n` 절차)

## 4. 파일 트리
```
native-ios/Gugu/
├─ Core/Games/            [신규] RoundClock · AttemptRecord · StarRules · RisingCloud · SkyZones
│                          · Altimeter · StairsSession · MonsterSession · MonsterDex · Wardrobe
├─ Store/PlayRewardsStore.swift            [신규]   (AdventureStore.swift 삭제)
├─ Features/Games/        [신규]
│  ├─ GameStyle.swift      (MinigameStyle 대체)
│  ├─ Shared/  GameArt · HudBadge · ComboBadge · CoinFlight · Sparkles · Banner
│  │           · PausePanel · GameResultPanel · JellyBackground · TouchDownCatcher
│  ├─ Stairs/  StairsEngine · StairsView · StairsScene · AltimeterView
│  ├─ Monster/ MonsterEngine · MonsterView · MonsterScene · DexStrip
│  └─ Wardrobe/WardrobeView.swift
├─ Resources/Assets.xcassets/Games/*.imageset   [신규]
├─ Features/{Adventure,Pop,Basket,Runner,LaneRunner,Minigame}/   [삭제]
└─ Core/{Adventure/, Pop, Basket, Runner, LaneRunner}            [삭제]
GuguTests/  StairsTests · MonsterTests · RewardRulesTests · PlayRewardsStoreTests
            · GameTrialTests · LegacySaveTests [신규] / PopTests [삭제]

native-android/app/src/main/java/site/smap/gugudan/
├─ core/games/*.kt                 [신규]
├─ store/PlayRewardsStore.kt       [신규]
├─ features/games/{GameStyle.kt, shared/, stairs/, monster/, wardrobe/}   [신규]
├─ features/{adventure,pop,basket,runner,lanerunner}/, core/adventure/,
│  core/{Pop,Basket,Runner,LaneRunner}.kt, features/home/Minigames.kt     [삭제]
res/drawable-nodpi/game_*.webp     [신규]
src/test/.../  StairsTests · MonsterTests · RewardRulesTests · PlayRewardsStoreTests
               · GameTrialTests · LegacySaveTests [신규] / PopTests · MinigameEntryTests [삭제·교체]
```

## 5. 단계 (추가 먼저, 제거 나중 — 매 단계 실행 가능 상태 유지)

| Phase | 작업 | 검증 기준 |
|---|---|---|
| 0 | 저장 호환 테스트: adventure 든 GameState 읽힘(+ 별 조각 이관, Q2 따라) | 양쪽 테스트 통과 |
| 1 | `Core/Games`·`core/games` + 테스트 36개 이식 | 양쪽 단위 테스트 통과, A·B 시드 5개 무작위 판 채점 중복·누락 0 |
| 2 | iOS 공통 부품 + A + PlayRewardsStore + 그림 | 시뮬레이터에서 Unity 캡처 A1~A11 대응 화면, 쉬는 창 먹구름 정지, 결과 때 별 저장, 두 손가락 연타 |
| 3 | iOS B + 도감 + 옷장 | B1~B7·M1~M4 대응 화면, 8종 순환, 사기 연타 잠금, 벗기 → 맨머리 |
| 4 | iOS 진입점 교체(홈·단 시트·라우터·페이월) + 3문제 체험 + 기록 연결(Q1) | 무료: 3문제 후 체험 종료 패널([다시 하기] 없음), 이용권: 무제한 |
| 5 | iOS 제거 + 현지화 6개 언어 | 빌드 경고 0, LocalizationTests 통과, 기존 저장본 덮어 설치 시 진행도 유지 |
| 6 | Android 2~5 반복 + SceneView 제거 | 단위 테스트, assembleRelease, Pixel 에뮬레이터, AAB 용량 비교 |
| 7 | 스토어·버전(iOS 2.9.0(40), Android vc30) | 설명·IAP 설명·스크린샷·심사 메모·출시 노트 교체 |

## 6. 스토어 영향
- 앱 이름 "구구 어드벤처"/"Gugu Adventure: Times Tables" — 검색·Apple Ads 키워드·알림 발신자·Firebase 에 묶임 → 유지 추천
- 설명(Play en-US·en-GB·ja-JP full/short, ASC listing promotional text), IAP 설명("All Adventure regions…"), 스크린샷(02-world·03-battle·06-minigame·play 02-battle) → 교체 필수(없어진 기능 노출 금지, 가이드라인 2.3)
- 심사 메모 4,000자 한도 임박 → 놀이·어드벤처 문단 정리
- 기존 구매자: 출시 노트에 "3D 어드벤처와 놀이 4종이 새 게임 2종으로, 평생 이용권으로 무제한" 안내

## 7. 위험
| 위험 | 대응 |
|---|---|
| `.adventure` 삭제로 진행도 초기화 | Phase 0 회귀 테스트 |
| 아이 관찰 전 수치(먹구름 속도·별 가격) | 상수 한곳에 모아 2.9.x 조정 |
| SwiftUI 짧은 탭·멀티터치 누락 | touchesBegan 브리지 + 회귀 시나리오 엔진 테스트 |
| 구매자 불만 | 출시 노트·앱 안 안내 |
| 작업량(화면 ~4,100줄 × 2) | iOS 확정 후 Android 이식 |

## 8. 결정 (2026-11-23)
- Q1 학습 기록: **A·B 둘 다** commitSession(XP·오늘 목표·wrongPool·서버는 practice 로 매핑, 서버 변경 없음). A 는 table nil(단이 바뀜), B 는 목표 식을 factId 로 하고 다른 조합으로 맞혀도 정답
- Q2 별 조각: **새 별로 1:1 한 번 이관**(이관 표시 저장, `gugu.adventure.v1` 키는 남김)
- Q3 문제 범위: **A 는 설정 따름**(홈 시작 = 2~9단 무작위, 단 시트 시작 = 고른 단(2~19)부터, 계단 수 = 「곱하는 수」, 보물 뒤 다음 단은 2~9 무작위, 보기 최대값 = 단×계단수+단), **B 는 2~9단 × 1~9 고정**(단 시트에서 10단 이상이면 B 잠금·안내)
- Q10 이름: **구구 계단 · 구구 몬스터**(6개 언어 번역)
- 기본안 그대로: Q4 체험 3문제 = 맞힌 3문제(A 3칸·B 3마리), 체험 중 별도 저장 / Q5 옷장·도감 무료에게도 열림 / Q6 앱 이름 유지 / Q7 Android 뒤로 가기 = 쉬는 창 / Q8 어드벤처 업적은 표시처가 없어 그대로 사라짐 / Q9 A 오답 낭독은 다음 버전

## (참고) 결정 전 질문 목록
- Q1 학습 기록·오늘 목표 연결 / Q2 어드벤처 별 조각 / Q3 문제 범위 / Q10 게임 이름 → 질문 중
- 기본안(이견 없으면 진행): Q4 체험 3문제 = 맞힌 3문제(A 3칸·B 3마리), 체험 중 받은 별도 저장 / Q5 옷장·도감은 무료 사용자에게도 열림 / Q6 앱 이름 유지 / Q7 Android 뒤로 가기 = 쉬는 창 / Q8 어드벤처 업적 8개는 표시되던 곳이 없어 그대로 사라짐, 새 업적은 다음 버전 / Q9 A 오답 낭독은 다음 버전

## 진행 기록
- 2026-11-23 Phase 1 iOS 완료: `Core/Games/` 11개 + `Core/SeededRandom.swift`, 테스트 64개 추가 → 322 실행·실패 0·건너뜀 1. B 출제는 앱 `Problems.pick` 대신 `MonsterSession.pickTarget`(전역 설정·19단·오답 가중과 분리, 시험판 72문항 그대로). 잘못된 인자는 precondition
  - 조정 후보: 「곱하는 수」19 면 꼭대기가 멀어 보물·별이 덜 나옴 — 옷장 가격은 "A 한 판 별 20~40" 기준. 아이 관찰 후 2.9.x 에서 판단
- 2026-11-23 Phase 1 Android 완료: `core/games/` 11개 + `core/SeededRandom.kt`, 테스트 65개 → 354/354, 골든 벡터 7개 iOS 와 일치(Kotlin 은 예외 단언 추가 — require/check)
- 2026-11-23 Phase 2 iOS 완료: 공통 부품 14·구구 계단 7·PlayRewardsStore(별 조각 1:1 이관)·그림 53장(5.7MB, 미압축)·테스트 25개 → 347 실행·실패 0(직접 재실행 확인). 시뮬 스크린숏 `scratchpad/games-ios-a/`. 디버그 훅 GUGU_GAME=stairs·GUGU_STAIRS_AUTOPLAY·GUGU_PLAY_STARS·GUGU_PLAY_EQUIP·GUGU_PLAY_STAIRS_BEST. 체험 끝나도 별·기록 저장
- 2026-11-23 Phase 3 iOS 완료: 구구 몬스터(Monster/ 7개)·옷장(Wardrobe/ 2개)·테스트 23개 → 370 실행·실패 0. B 단 지정(`MonsterSession(table:)`, nil 이면 골든 그대로, `testGoldenTable7Seed42`) — **Android 미반영**. 벗기 → 맨머리, 0.4초 잠금 실제 탭(axe)으로 확인. 스크린숏 `scratchpad/games-ios-b/`
- 2026-11-23 Phase 2 Android 완료: shared 14·stairs 7·PlayRewardsStore·webp 53장(5.7MB→1.06MB)·문자열 `tools/i18n/android-only.d/games.json` 28개·테스트 34 → 388/388. 뒤로 가기 = 쉬는 창(쉬는 창에서 또 누르면 머묾). 스크린숏 `scratchpad/games-android-a/`
- 2026-11-23 Phase 4 iOS 코드 완료(빌드 성공, **테스트·시뮬 확인 미완** — 맥 coreaudiod 멈춤으로 Sound.setupEngine 에서 앱이 멈춤, afplay 도 시간 초과. 대표님 `sudo killall coreaudiod` 필요): 홈 "게임" 묶음(계단·몬스터·꾸미기 카드), 단 시트 몬스터 10단+ 잠금, 체험 연결, `GameMode.stairs/.monster`(학습 목록 제외), 결과·체험 끝 완료 커밋·나가기 partial 커밋, 결과에 "+N XP", 게임 XP 보너스 0, 삭제 목록 `.claude/scratch/ios-removal-list.md`
  - 제 결정: **게임 판은 단 마스터리 별 미반영**(`GameMode.isGame`, Commit 마스터리 조건) — 3마리 체험만으로 별 3개 나오던 문제. 단 번호는 서버 tableNo 로 유지. Android Phase 4 에서 같게
- 2026-11-23 Phase 3 Android 완료: monster/ 7·wardrobe/ 2·MonsterSession `table`(골든 `goldenTable7Seed42` iOS 와 일치)·테스트 31 → 419/419(메인 재실행 확인). 옷장 빈 곳 탭이 아래 홈으로 새던 버그 → `blockTouches` 층. 0.17초 연타 1회 처리·벗기 맨머리 실제 탭 확인. 비교 시트 `scratchpad/games-android-b/compare_android_ios_B.png`. 공용 패널 한국어 낱말 중간 줄바꿈 → Phase 4 에서 `WordBreak.Phrase`
- 2026-11-23 Phase 5 iOS 완료(빌드 성공, **테스트 미실행** — 오디오): Swift 35개(약 8,300줄)를 `.claude/removed-2.9/native-ios/` 로 이동(목록 `MOVED.md`), MinigameStyle → `Features/Games/GameStyle.swift`, 리소스 이동 0(전부 코드 그림·합성음), .app 243→236MB. 카탈로그 731→517 키, 새 76개 × 5개 언어 needs_review, `catalog.py check` 503/503(메인 재확인, fontTools 는 scratchpad venv). 오디오 복구 뒤 기대 테스트 수 330. 잠재 문제(미수정·기존): 프로필 "업적 (n/전체)" 가 정의 안 된 id 도 셈
  - 부작용: iOS 카탈로그에서 281개 키가 빠져 Android `android.py gen` 이 깨짐 → 메인이 `tools/i18n/android-only.d/legacy-2.9.json`(291개, HEAD 번역 그대로) 임시 생성, games.json 중복 55개는 Android Phase 4 작업자가 정리. Android Phase 5 에서 legacy-2.9.json 을 코드와 함께 삭제
- 2026-11-23 Phase 4 Android 완료: 홈 "게임" 묶음·단 시트 몬스터 잠금·3문제 체험·학습 기록(isGame 마스터리 제외, partial)·공용 패널 `WordBreak.Phrase` 줄바꿈 → 447/447·check 0(메인 재실행 확인), 에뮬 흐름 6개 실제 탭. games.json 은 `{}`(카탈로그로 이동). 라우터가 막아야 할 조합(몬스터 13단)은 오류로 실패. 제거 목록 `.claude/scratch/android-removal-list.md`
  - Android 작업자 지적으로 iOS 테스트 기대값 2건 수정(메인): `GameLearningTests` 13×1 은 factId 범위(2~19 × 1~19) 안이라 서버에 보냄, `LegacySaveTests` 몬스터 7단 판 뒤 plays 2(isGame). build-for-testing 성공
- 2026-11-23 Phase 5 Android 완료: Kotlin 31개(8,650줄) → `.claude/removed-2.9/native-android/`(MOVED.md Android 절), Minigames → `features/games/GameStyle.kt`, SceneView 제거(minSdk 28 유지·주석만), APK debug 308→268MB·release 238→208MB·AAB 222→205MB, legacy-2.9.json 비워 이동·android-keys 819→527, 테스트 447→379(메인 재실행 379/379·check·lint 0). 별 조각 이관은 원본 JSON 의 starShards 만 읽음(iOS 와 같은 수준)
  - 5개 언어 게임 화면 넘침 없음(시트 `scratchpad/games-android-i18n/sheet_*.png`). 범위 밖 발견: 학습 탭 단 타일 제목 잘림 vi "Bảng nhâ"·id "Perkalian"(`LearnScreen.kt:206` 24sp 한 줄, 2.8.0 부터 — iOS 는 minimumScaleFactor 0.6 이라 괜찮음) → 대표님 결정 대기
- 2026-11-23 verifier(코드 점검, `.claude/scratch/verify-games-2.9.md`): P0 없음. **P1 서버 계약** — 이 워크트리의 `api/learning.py` 는 factId 2~9×1~9·tableNo ≤9, 메인 작업 폴더(feature/2.7.0-learn-pop)에는 2~19 판 + `api/migrations/20261030_nineteen_tables.sql` 이 미커밋. 배포 서버가 19 판인지 미확인(아래 장애로 확인 불가). 422 는 영구 오류로 안 쳐 아웃박스가 막힐 수 있음 → 출시 전 확인 필수
  - 메인이 반영: 고아 `purple` 색 토큰 삭제(양쪽), `Gugu.storekit` 설명 "3D 어드벤처" → "구구 계단·구구 몬스터", LearningRecord 범위 주석 2~19 로. Android 379/379·iOS build-for-testing 성공
  - 그대로 둔 P2: 음수 별 조각 처리 차이(iOS 로그+0, Android 격리+0 — 결과 같음), 결과 1초 전 종료 시 별 미저장(문서화), 3칸 전 잡혀도 체험 끝(옛 점프와 같은 규칙), `tools/i18n/__pycache__` pyc·빈 games.json 커밋 제외
- 2026-11-24 대표님 요청 "게임이 맨 아래라 접근 어려움 — 초딩이 바로 접근": iOS 홈 순서를 헤더 → **게임 묶음(큰 카드 2장 나란히: 그림 96pt·이름·배지, 소개는 접근성 도움말로)** → 꾸미기 → 오늘의 목표 → 레벨 → 빠른 학습 → … 로 변경(HomeView.swift, 새 문자열 없음). ko·en·vi 첫 화면 확인(`games-ios-entry/11~13`). Android 는 같은 배치 + 학습 탭 단 타일 자동 축소(대표님 "같이 고쳐줘") 진행 중 `.claude/scratch/brief-android-home-games-top.md`
- 2026-11-24 대표님 결정 "3문제는 아쉽네 게임은 10문제로": 무료 체험 3 → **10문제**(계단 10칸·몬스터 10마리). iOS `PremiumConfig.minigameTrialQuestions = 10`(문구는 값 끼워 쓰기라 번역 그대로), 테스트 330 통과, 배지 "10문제 체험"·안내 "무료 체험 · 10문제" 확인. Android 는 홈 작업자 끝난 뒤 반영
- ⚠️ 2026-11-23 19:13 운영 장애 관측: gugu.aiagnt.kr(/·/privacy·/api/health)·aiagnt.kr 모두 Cloudflare 530/1033(터널 끊김), `ssh aws` 시간 초과 — 서버 호스트 다운 추정. 대표님 확인 필요
- 2026-11-24 오디오 복구 → **iOS 전체 테스트 330 실행·실패 0·건너뜀 1**(iPhone 17e, 경고는 기존 deprecation 4건뿐). 시뮬 흐름(iPhone 17e, 실제 탭): 홈 "게임" 묶음 → 구구 계단 시작 패널(무료 체험·3문제) → 실제 탭 2칸 뒤 먹구름에 잡힘 → 체험 끝 패널(+20 XP, [다시 하기] 없음) → [평생 이용권 보기] 페이월 → 닫으면 패널 복귀 → 나가기 → 홈 오늘 목표 0→2·XP 0→20·연속 1일·모은 별 2, 학습 탭 마스터리 0/54 그대로 → 7단 시트 두 게임 가능·몬스터 "구구 몬스터 · 7단" ?×?=28 → 13단 몬스터 회색 "9단까지"·눌러도 무반응 → 구구 꾸미기 열기·[메뉴로] 닫기 → GUGU_PREMIUM=1: "새 게임" 배지·체험 표시 없음·5칸 진행·결과 "새 기록"·레벨 업. 스크린숏 `scratchpad/games-ios-entry/`. 대표님용으로 iPhone 17 에 개발 빌드 실행해 둠
- 2026-11-24 Android 홈 반영 완료: 헤더 → 게임 큰 카드 2장 → 꾸미기 → 오늘의 목표 → …(HomeScreen.kt), 공용 한 줄 자동 축소 `FitText`(designsystem/Components.kt, TextAutoSize) → 학습 탭 단 타일(24sp→약 14sp) vi·id·hi 10~19단 잘림 해소. 비교 시트 `scratchpad/games-android-home/sheet_{home,learn_tiles,flow}.png`. 379/379·check/lint 0
- 2026-11-24 Android 체험 10문제 반영: `PremiumConfig.MINIGAME_TRIAL_QUESTIONS = 10`, 상수 기대 테스트(MinigameTrial·GameEntry)·학습 기록 체험 테스트(N 반복) 갱신, 장치 테스트(trial = 3 명시)는 그대로. 379/379, 에뮬레이터에 설치
- 2026-11-24 대표님 요청 "순서대로 / 무작위 선택" → 별도 계획 `.claude/plans/game-order-mode.md`(둘 다 적용·무작위는 단·곱하는 수 모두·게임 시작 창에서 선택·기본 순서대로). iOS 먼저 구현 중(`.claude/scratch/brief-ios-order-mode.md`), Android 는 iOS 골든 확정 뒤
- 2026-11-24 iOS 문제 순서 완료(미커밋): `ProblemOrder`·`GameOrderStore`(키 gugu.gameOrder.v1, 게임별 기억·기본 순서대로)·시작 창 [순서대로]/[무작위](고르면 세션 재생성), 계단 무작위(2~9단 × 1~곱하는 수, 직전 식 다시 뽑기, 발판 = 직전 정답, 곱하는 수 칸마다 보물, "N번째 계단"·"N칸 돌파! 보물 +1"), 몬스터 순서대로(×1→×9, 단 지정이면 같은 단 반복·없으면 다른 단). 대표님 결정으로 (무작위 + 단 지정) 경로와 테스트 2개(`testGoldenTable7Seed42`·`testTableLimitsTargetsToThatTable`) 삭제 → 순서대로 단 7 골든으로 대체. **테스트 354 실행·실패 0·건너뜀 1**, 새 경고 0, 문자열 7개 en·ja·hi·vi·id(needs_review), catalog lint/check 통과. 시뮬(iPhone 17e) `scratchpad/games-ios-order/`. 새 골든·Android 할 일은 `.claude/scratch/games-core-api.md` 「문제 순서」·「Android 할 일」
- 남은 순서: 양쪽 전체 검증(오디오 복구 뒤 iOS 테스트 330개 기대·시뮬 흐름·언어별 화면) → verifier → 스토어(Phase 7)
