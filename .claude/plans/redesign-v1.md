# 홈·학습·프로필 재디자인 V1 「구구의 모험 지도」 구현 계획

- 작성 2026-11-24. 코드는 아직 고치지 않았다. 이 문서는 구현 에이전트가 단위(U0~U11)별로 읽고 그대로 따른다.
- 입력: 시안 `.claude/scratch/redesign/proto/v1/` (`DESIGN.md`, `index.html`, `shots/`), 현황 점검 `.claude/scratch/redesign/audit.md`, 앱 코드 `native-ios/Gugu`, `native-android/app/src/main/java/site/smap/gugudan`.
- 작업 위치: 워크트리 `/Users/genie/SmapSource/gugu-games`. 커밋·푸시·`rm` 금지. 걷어 내는 파일은 `.claude/removed-redesign-v1/<원래 경로>` 로 옮기고 `MOVED.md` 에 적는다(2.9.0 의 `.claude/removed-2.9/` 와 같은 방식).
- 경로 줄임: `ios/` = `native-ios/Gugu/`, `and/` = `native-android/app/src/main/java/site/smap/gugudan/`, `iosT/` = `native-ios/GuguTests/`, `andT/` = `native-android/app/src/test/java/site/smap/gugudan/`.

---

## 1. 목표와 범위

### 1-1. 목표

아이는 홈에서 "오늘 뭐 하지"만 보고 누른다. 부모는 오른쪽 위 열쇠를 3초 눌러 약한 단부터 본다. 탭은 세 개(홈·지도·보물)이고, 부모용은 모두 열쇠 뒤로 간다. audit 상위 문제 1~7(입구 9곳, 글 의존, 첫 실행 다크, 약한 단을 볼 곳 없음, 아이·부모 정보 섞임, 같은 정보 반복, 18칸 격자)을 V1 그대로 푼다.

### 1-2. 1단계와 2단계의 경계

| 항목 | 1단계 | 2단계 |
|---|---|---|
| 탭 구조 | 홈·지도·보물, 모든 탭 오른쪽 위 보호자 열쇠 | 그대로 |
| 홈(구구네 마당) | 전부 | 없음 |
| 지도 탭 | **임시 화면**: 2~9단 큰 숫자 칸, 섞어서, 10~19, 먹구름 쫓기 | V1 섬 지도(섬 8개, 점선 길, 구구 위치, 먹구름, 무지개·열기구, 하늘 지도) |
| 섬 시트(배우기·풀기·놀기, 문제 모양 타일) | 전부. 섞어서 시트, 10~19 고르기 포함 | 그대로 다시 씀 |
| 보물 탭 | 전부 | 배지 그림(17종)을 넣을지 그때 정함 |
| 보호자(3초 문, 리포트, 설정·계정, 위험 동작 출생연도 확인) | 전부 | 없음 |
| 잠긴 기능 "어른이랑 같이 열어요" | 섬 시트·섞어서 시트·게임 체험 끝 | 그대로 |
| 라이트 기본, 밤 테마 | 홈·지도(임시)·보물·섬 시트·잠김 카드·문 카드 | 섬 지도 밤 그림 |
| 용어 정리(별=단 실력, 코인=옷장 화폐, 배지) | 전부 | 없음 |

### 1-3. 지도 탭 1단계 임시안 판단: 채택

대표님이 물으신 "1단계는 V1 지도 대신 2~9단 큰 숫자 + 섬 시트로 가도 되는가"는 **된다**고 판단해 계획에 넣었다. 이유는 셋이다.

1. 아이가 실제로 고르는 곳은 섬 시트다. 섬 시트·추천 규칙·먹구름 표시는 1단계에 만들고 2단계에서 그대로 쓴다. 2단계는 칸 배치를 지도로 바꾸는 일만 남는다.
2. 지도는 V1 이 "중상"으로 매긴 가장 위험한 화면이다. 섬 좌표 비율, 점선 길, SE·iPad·큰 글꼴 배치, 화면 읽기 순서, 밤 그림이 모두 걸리고, 섬 그림(`isle_1`~`isle_6`)은 아직 다시 만드는 중이다.
3. 임시안도 V1 의 뜻을 지킨다. 숫자 순서(2→9), 큰 숫자 원, 별이 있는 섬에만 별 줄, 오늘의 섬 강조, 먹구름 표시, 10~19 접기가 같다.

조건이 하나 있다. 탭 이름·아이콘·머리 제목은 1단계부터 최종값("지도", `tab_map`, "모험 지도")을 쓴다. 탭 이름을 1단계·2단계에 두 번 바꾸면 문구·테스트·알림 이동을 두 번 고쳐야 하기 때문이다. 아이는 탭을 그림으로 고르므로 숫자 칸 화면에 "지도"라는 이름이 붙어도 막히지 않는다. 반대하시면 U7 하나만 바뀐다.

### 1-4. 빼는 것, 이유, 기존 사용자 영향

| 빼는 것 | 이유 | 대신 가는 곳 | 기존 사용자 영향 |
|---|---|---|---|
| 프로필 탭 | 아이·부모 정보가 번갈아 섞인 원인(audit P2) | 아이 것은 보물 탭, 부모 것은 보호자 | 탭 위치 습관이 바뀐다. 알림 이동 `profile`·`profile/report` 는 보호자 리포트로 간다(2-9) |
| 홈 인사 줄 | 정보 없음 | 없음 | 없음 |
| 홈 오늘 목표 링·목표 시트 | 미션과 "오늘 할 일"이 겹친다. 목표는 부모가 정하는 값 | 보호자 설정(하루 목표 칩, 이미 있음), 리포트 이번 주 막대의 목표선 | `dailyGoal` 값은 그대로다. 학습 알림 쉬기·연속 일수 하한·결과 화면 "목표 달성"은 지금처럼 이 값을 쓴다. 아이가 홈에서 목표 진행을 못 보게 되는 점은 잃는다(V1 이 받아들인 손해) |
| 홈 레벨 카드, 프로필 레벨 카드 | 같은 값을 두 형식으로 보여 줬다(C1) | 보물 탭 한 곳 + 홈 구구 발밑 Lv 딱지 | 없음 |
| 홈 빠른 학습 시작 | 입구 9곳의 하나 | 오늘의 섬 말풍선 | 전체 범위 차근차근 한 번 누르기는 사라진다. 섞어서 시트 → 차근차근으로 두 번 |
| 오늘 추천 카드(홈·학습) | 추천이 두 곳 | 말풍선 + 섬 시트 "추천" 딱지 | 추천 규칙이 바뀐다(2-6). 기록이 쌓이기 전에는 지금 규칙과 같은 단이 나온다 |
| 홈 취약 문제 복습 카드, 프로필 집중 공략 | 복습이 세 곳 | 지도 탭 "먹구름 쫓기" 한 곳 | `wrongPool` 그대로. 복습 세션 그대로 |
| 홈 기록 도전 3줄(스피드런·60초·서바이벌) | 입구 9곳의 하나, 모드 목록이 두 탭에 있음(C2) | 섞어서 시트(스피드런은 단 시트에도) | 60초 챌린지·서바이벌 입구가 한 단계 깊어진다. 업적 「모드 탐험가」 목표는 `Modes.list.count`(6)로 **그대로**다. 6개 모드가 모두 섬·섞어서 시트에 남기 때문이다 |
| 홈 "어떻게 놀아볼까요?" 머리글 | 위계가 거꾸로였다(C3) | 없음 | 없음 |
| 홈 구구 꾸미기 카드 | 옷걸이 단추와 보물 탭 옷장 줄로 충분 | 홈 옷걸이, 보물 탭 옷장 | 없음 |
| 학습 탭 18칸 격자, 빈 별 54개, 🏆 n/54 | 2~9단 학습자에게 10~19단이 같은 무게(K5) | 2~9단 칸 + "10~19" 한 칸, 별은 받은 단에만 | 10~19단 별은 그 고르기 시트에서 본다 |
| 프로필 통계 3칸(누적 정답·정확도·최고 콤보) | 부모에게는 이번 주·단별 값이 쓸모 있고, 최고 콤보는 아이 값 | 리포트 이번 주 숫자 | 값은 저장에 남는다(업적이 쓴다). 화면에서만 빠진다 |
| 프로필 최고 기록 2칸 | 아이 자랑거리 | 보물 탭 최고 기록 줄(계단·몬스터·60초·서바이벌) | 없음 |
| 프로필 계정 권유·이용권 권유 | 아이 화면의 결제·로그인 권유(K6) | 보호자 설정·계정 | 무료 사용자가 결제 화면을 보는 입구가 줄어든다. 매출 쪽 손해 가능성이 있다(7절 결정 사항은 아님. V1 이 고른 방향) |
| 설정 톱니(시트) | 부모 입구가 숨어 있었다(P3) | 열쇠 → 보호자 설정·계정 | 없음 |
| 다크 첫값 | 그림과 안 맞음(K4, C5) | 낮(라이트) 첫값 | 2-10 참고 |
| 잠긴 항목 → 결제 화면 바로 열림 | 아이 손에 부모 문구(K6) | "어른이랑 같이 열어요" → 보호자 문 → 결제 화면 | 결제까지 3초 누르기가 더해진다 |
| 단 시트 "표·외우기" 알약 | 작은 글 단추 | 섬 시트 ① 배우기 타일 "표 보고 외우기" | 없음 |

### 1-5. 저장값과 기존 사용자

- **저장 키 이름은 하나도 바꾸지 않는다.** `GameState`, `PlayRewards`(`gugu.play.v1`), 테마 키, 게임 순서 키 그대로. 이관 코드는 없다.
- 더하는 저장 필드는 하나다. `DayLogEntry.tables`(날짜별·단별 정답·오답·시간 합, 2-4). 선택 필드라 구버전 저장본은 `nil` 로 읽힌다. iOS 는 합성 디코더가 `Optional` 을 `decodeIfPresent` 로 읽고, Android 는 `Json { ignoreUnknownKeys = true; encodeDefaults = true }` 에 기본값 `null` 이라 둘 다 실패하지 않는다. 테스트로 확인한다(U1).
- 탭 선택(`AppTab`)은 저장하지 않는다. 이름을 바꿔도 이관이 필요 없다.
- 업적: 「모드 탐험가」 목표 6 그대로. 「구구단 완전정복」 설명 "2~9단 모두 마스터"는 실제 목표(18단)와 어긋나 있어 U10 에서 문구만 고친다. 판정은 그대로다.
- 알림 이동 문자열(`home`, `learn`, `profile`, `profile/report`, `paywall`, `settings/notifications`)은 서버 계약이라 그대로 두고 앱 안 대응만 바꾼다(2-9).

---

## 2. 데이터 모양 (Core 순수 로직, 화면보다 먼저)

화면에서 분기하던 것을 Core 의 값과 함수로 옮긴다. 문구 없는 계산은 iOS·Android 가 같은 이름, 같은 의미로 만든다. Android 이름은 Kotlin 관례(`UPPER_SNAKE` 상수, `camelCase` 함수)로 바꾼다.

### 2-1. 탭과 라우터

```swift
// ios/Store/Router.swift, and/store/Stores.kt
enum AppTab { case home, map, treasure }

/// 지도 탭에서 열 시트. 홈 말풍선·알림 이동이 Router 를 거쳐 연다
enum IslandPick: Hashable { case table(Int), mixed, sky }   // sky = 10~19 고르기

final class Router {
    var tab: AppTab = .home
    var islandSheet: IslandPick?          // 지도 탭 시트 (예전 LearnView 지역 상태를 Router 로 올림)
    var guardian: GuardianStep = .idle    // 탭 화면 위의 보호자 흐름 (2-2)
    // 그대로: minigame, wardrobeOpen, pendingRoute
    // 지움: settingsOpen, settingsFocusNotifications (보호자 흐름이 맡음)
    func openIsland(_ pick: IslandPick)   // tab = .map; islandSheet = pick
}
```

섬 시트 상태를 Router 로 올리는 이유는 둘이다. 홈 말풍선이 다른 탭의 시트를 열어야 하고, 알림 이동이 떠 있는 시트를 닫아야 한다(iOS 에서는 떠 있는 시트 위에 루트 겹침이 보이지 않는다).

### 2-2. 보호자 흐름 (상태 기계)

`ios/Core/Guardian/GuardianFlow.swift`, `and/core/guardian/GuardianFlow.kt`. 화면은 사건만 보내고 다음 상태는 `reduce` 가 정한다. 3초 누르기 진행률은 화면 지역 값이고 흐름에는 끝났다는 사건만 온다.

```swift
enum GuardianDestination: Equatable {
    case report
    case settings(focus: SettingsFocus?)   // SettingsFocus = .notifications
    case paywall                           // 아이가 보호자를 부른 경우·결제 알림 이동
}

enum GuardianStep: Equatable {
    case idle
    case kidLocked(GameMode)               // "어른이랑 같이 열어요" 카드
    case holding(GuardianDestination)      // 3초 꾹 누르기 카드
    case open(GuardianDestination)         // 보호자 화면 또는 결제 화면
}

enum GuardianEvent: Equatable {
    case tapKey                            // 탭 머리 열쇠
    case tapLocked(GameMode)               // 잠긴 타일
    case callGuardian                      // [보호자 부르기] (잠김 카드, 게임 체험 끝 창)
    case holdCompleted                     // 3초를 다 누름
    case birthYearPassed                   // 화면 읽기 사용자 대안 (3-6)
    case route(GuardianDestination)        // 알림 이동
    case dismiss                           // 닫기·다음에 할래요·아이 화면으로·뒤로 가기
    case resumed(awaySeconds: Int)         // 앱으로 돌아옴
}

enum GuardianFlow {
    static let holdSeconds = 3.0
    static let relockAfterAwaySeconds = 300
    static func reduce(_ s: GuardianStep, _ e: GuardianEvent) -> GuardianStep
}
```

| 지금 | 사건 | 다음 |
|---|---|---|
| idle | tapKey | holding(.report) |
| idle | tapLocked(m) | kidLocked(m) |
| idle 또는 kidLocked | callGuardian | holding(.paywall) |
| idle | route(d) | holding(d) |
| holding(d) | holdCompleted, birthYearPassed | open(d) |
| open(_) | route(d) | open(d) (이미 열려 있으면 다시 묻지 않는다) |
| kidLocked, holding, open | dismiss | idle |
| kidLocked, holding, open | resumed(away ≥ 300) | idle |
| 그 밖의 조합 | 아무 사건 | 그대로 (빠른 두 번 누르기 같은 정상 입력이라 무시) |

결정과 이유:

- 앱을 떠났다 오면 300초 이상일 때만 잠근다. 보호자가 기록 지키기에서 메일 앱으로 가 확인 코드를 보고 돌아오는 흐름(`AccountSettingsCard`)이 백그라운드를 거치기 때문이다. 떠나는 즉시 잠그면 코드 입력이 날아간다. StoreKit·Play 결제 창도 같은 이유로 끊지 않는다.
- 다시 열 때마다 3초를 누른다. 유예 시간은 두지 않는다. 드문 동작이라 단순함이 낫다.
- 흐름 값의 주인. Android 는 루트 겹침(`MainActivity` 의 `AnimatedVisibility`)이 모든 화면 위에 그려지므로 `Router.guardian` 하나가 전부 맡는다. iOS 는 떠 있는 시트 위에 루트 겹침이 보이지 않으므로, 같은 `GuardianStep` 타입을 쓰는 곳마다 하나씩 든다. 탭 화면은 `Router.guardian`, 섬 시트·구구 계단·구구 몬스터 화면은 자기 지역 값(목적지는 늘 `.paywall`)이다. 규칙은 `reduce` 하나다.
- Android 섬 시트는 `ModalBottomSheet`(별도 창)라서 잠긴 타일을 누르면 지금처럼 시트를 닫고 루트에서 잠김 카드를 띄운다. iOS 는 시트 위에 띄우고 결제 뒤 시트로 돌아온다. 지금도 있는 플랫폼 차이를 그대로 둔다.
- `PremiumStore.paywallOpen`, `openPaywall()`, `gate(_:)` 는 부르는 곳을 모두 흐름으로 옮긴 뒤 지운다(U9). 결제 화면이 열리는 길이 흐름 하나가 된다.

### 2-3. 위험 동작과 출생연도 확인

```swift
enum GuardedAction { case purchase, resetProgress, deleteAccount }
```

- 보호자 구역 안에서도 이 셋은 실행 직전에 출생연도 확인(`GuardianGate.isValidBirthYear`, `ios/Core/PushModels.swift`, Android 같은 이름)을 한 번 더 거친다. 통과 값은 기억하지 않는다. 동작마다 묻는다.
- 확인 화면은 `PushConsentSheet` ① 단계를 `BirthYearGateView`(`ios/Features/Guardian/BirthYearGateView.swift`, `and/features/guardian/BirthYearGate.kt`)로 빼서 같이 쓴다. 문구는 기존 `PushCopy.gateTitle`·`gateBody`·`gateInvalid` 그대로다. 광고 동의 흐름은 새 뷰를 부르도록 바꾸고 동작은 같다.
- 결제는 결제 화면의 구매 단추를 누른 뒤 확인한다. 결제 화면을 여는 길은 이미 보호자 문 뒤에만 있으므로 문 앞에서 한 번 더 묻지 않는다. 되돌릴 수 없는 일(돈이 나감) 바로 앞에만 둔다. 구매 복원은 묻지 않는다.
- 초기화·계정 삭제는 출생연도 확인 뒤 지금 있는 확인 단계("레벨·별·업적·기록이 모두 처음으로…", 계정 삭제 확인)를 그대로 보인다. 출생연도는 "누가", 확인 단계는 "무엇을"을 묻는다.
- 로그아웃은 넣지 않는다. 다시 로그인하면 되돌릴 수 있고 지금도 확인 단계가 있다.

### 2-4. 단별 리포트 계산

**쓸 수 있는 데이터부터 따졌다.**

- `tableMastery[단]` 은 최고 정확도·최고 평균 시간·별(최댓값)이다. 한 번 잘하면 영원히 잘한다로 남아 "약해요"를 못 낸다. 게임 판은 별을 주지 않아(`GameMode.isGame`) 게임만 하는 아이는 값이 비어 있다.
- `dayLog[날].misses` 는 문제별 오답 수뿐이다. 단별 정답 수가 없어 정확도를 낼 수 없다.
- 그래서 날짜별 기록에 단별 합계를 더한다. 모든 판(세션·게임·중도 이탈 부분 기록 포함)이 `Commit.applySession → appendDayLog` 를 지나므로 한 곳만 고치면 된다. 단은 `AnswerRecord.a` 다. 세션·빈칸·OX 는 `a` 가 고른 단이고, 구구 계단은 "단 × 칸", 구구 몬스터는 목표 식이라 역시 `a` 가 단이다(`ios/Core/Games/AttemptRecord.swift`).

```swift
// ios/Core/Models.swift (DayLogEntry 옆), and/core/Models.kt
struct TableTally: Codable, Equatable { var correct: Int; var wrong: Int; var msSum: Int }
struct DayLogEntry { /* 기존 필드 */ var tables: [Int: TableTally]? = nil }   // nil = 이 필드 전 저장본의 날

// ios/Core/Guardian/GuardianReport.swift, and/core/guardian/GuardianReport.kt
enum SkillStatus { case noRecord, practicing, strong, weak }

struct TableSkill: Equatable {
    let table: Int
    let attempts: Int, correct: Int, msSum: Int
    var accuracy: Double?          // attempts 0 이면 nil
    var avgMs: Int?
    var status: SkillStatus
}

enum TableSkills {
    static let windowDays = 14         // 오늘 포함 최근 14일
    static let minAttempts = 10        // 판정에 필요한 최소 문제 수
    static let strongAccuracy = 0.90   // 이상이면 잘해요
    static let weakAccuracy = 0.70     // 미만이면 약해요
    static func build(_ dayLog: [DayLogEntry]?, today: String) -> [Int: TableSkill]   // 2...19 전부, 기록 없는 단은 attempts 0
    static func weak(_ skills: [Int: TableSkill]) -> [Int]   // 약한 순: 정확도 오름차순, 같으면 단 오름차순
}

struct GuardianReport: Equatable {
    let rows: [TableSkill]          // 2~9단 항상 + 기간 안에 기록이 있는 10~19단
    let weak: [Int]
    let topMisses: [MissCount]      // 같은 14일, 많이 틀린 순, 최대 5개
    let headline: ReportHeadline
    let week: WeeklyReport          // 이번 주(월~일), 2-5
    static func build(_ state: GameState, today: String) -> GuardianReport
}

enum ReportHeadline: Equatable {
    case noRecord                    // 14일 동안 한 문제도 없음
    case weak(first: Int, second: Int?)   // 약한 단 1~2개. 오늘 권하는 단 = first
    case keepGoing(next: Int)        // 약한 단 없음. next = 오늘의 섬(2-6)
    case allStrong                   // 약한 단 없음, 오늘의 섬이 섞어서
}
```

상태 판정(`TableSkill.status`):

| 조건 | 상태 | 화면 글 |
|---|---|---|
| attempts == 0 | noRecord | 기록 없음 |
| 1 ≤ attempts < 10 | practicing | 연습 중 |
| attempts ≥ 10, 정확도 ≥ 90% | strong | 잘해요 |
| attempts ≥ 10, 정확도 < 70% | weak | 약해요 |
| 그 밖 | practicing | 연습 중 |

결정과 이유:

- 기간 14일. 주 2~3번 하는 아이도 단마다 10문제 넘게 쌓이고, 연습해서 나아지면 2주 안에 "약해요"가 풀린다.
- 상태는 정확도로만 정한다. 평균 시간은 칸에 숫자로만 보인다. 게임의 응답 시간은 발판을 찾는 시간까지 들어가 흔들림이 커서 판정에 넣으면 부모가 이유를 알기 어렵다. 카드에 "잘해요 기준 90%" 를 적어 기준을 숨기지 않는다.
- 문제 수가 10개 미만이면 "약해요"를 내지 않는다. 세 문제 틀린 단을 약하다고 부르지 않기 위해서다.
- 기록이 없는 단은 "아직 안 함"(V1) 대신 "기록 없음"이라고 쓴다. 기간 판정이라 몇 주 전에 한 단도 기록이 없게 되기 때문이다. 업데이트 직후 기존 사용자의 표가 비어 보이는 것도 같은 말로 설명된다. 전환기 전용 문구를 따로 두지 않는다.
- 이 숫자들은 상수 한 곳에 둔다. 아이 관찰 뒤 바꾸기 쉽다.
- 기존 `WeeklyReport.parentTip`(한마디)은 `ReportHeadline` 이 대신하므로 부르는 곳(`WeeklyReportCard`)을 걷어 낼 때 같이 지운다(U4). 문구 함수는 지금처럼 Core 에 둔다(iOS `String(localized:)`, Android `Strings` 통로). 언어별 문장 테스트(`CoreLocalizationTests`)를 그대로 쓸 수 있기 때문이다.

### 2-5. 이번 주 (월~일)

`WeeklyReport.build(dayLog, today)` 를 최근 7일에서 **이번 주 월~일**로 바꾼다. 도장(`DailyMissionPlan.week(of:)`)과 같은 주를 써서 audit P5("이번 주" 두 뜻)를 없앤다. 주 계산은 `DailyMissionPlan.week(of:)` 를 그대로 부른다.

- `days`: 월~일 7개. 오늘 뒤의 날은 0.
- `prevCorrect`: 지난주 월요일부터 지난주의 같은 요일까지. 이번 주가 화요일이면 지난주 월·화만 비교한다. 월요일에 꽉 찬 지난주와 비교하면 늘 지는 문제를 막는다. 기존 문구 "지난주보다 정답 +%lld" 등은 그대로 쓴다.
- 날짜 중복 병합은 `tables` 도 합친다.

### 2-6. 오늘의 섬 (홈 말풍선, 지도 강조, 리포트 권유가 같이 쓴다)

`ios/Core/TodayIsland.swift`, `and/core/TodayIsland.kt`.

```swift
enum TodayIsland: Equatable {
    case table(Int, reason: Reason)    // Reason = .weak, .next
    case mixed
    static func pick(mastery: [Int: TableMastery], skills: [Int: TableSkill]) -> TodayIsland
}
```

1. 약한 단이 있으면 가장 약한 단(`TableSkills.weak(skills).first`). 이유 `.weak`.
2. 없으면 로드맵 순서(`Hints.roadmapTables`: 2,5,3,4,6,9,7,8,10~19)에서 **최근 기록이 "잘해요"인 단을 뺀 뒤** 별이 가장 적은 단(같으면 로드맵 순서). 이유 `.next`.
3. 남은 단이 모두 별 3개면 `.mixed`.

결정과 이유:

- 2번은 지금 `Hints.nextRoadmapTable` 과 같은 규칙에 "잘해요 제외" 하나만 더했다. 게임만 하는 아이는 별이 생기지 않아 지금 규칙으로는 영원히 "2단 가 볼까?"에 머문다. 기록이 쌓이기 전에는 결과가 지금과 같아 기존 테스트 기대값이 유지된다.
- 저장하지 않고 상태에서 매번 계산한다. 아이가 5단을 연습해 잘해요가 되면 홈 말풍선이 다음 섬으로 넘어간다. 진도가 보이는 쪽이 낫다고 봤다.
- `Hints.nextRoadmapTable` 은 마지막 호출부(`RecommendCard`, `LearnView`, `PracticeSheet`)를 걷어 내는 U7 에서 지우고, 그 테스트(`iosT/UxCoreTests.swift`, `andT/UxCoreTests.kt`)는 `TodayIsland` 테스트로 옮긴다.
- 리포트 `ReportHeadline.weak(first:)` 의 `first` 와 오늘의 섬이 같은 단이다. 부모 문장과 아이 말풍선이 어긋나지 않는다.

### 2-7. 섬 시트 모델

```swift
// ios/Core/IslandPlan.swift, and/core/IslandPlan.kt
enum IslandMethod: Equatable { case tableStudy, mode(GameMode) }     // tableStudy = 표 보고 외우기

enum IslandPlan {
    /// "추천" 딱지. 약한 단이거나 처음 가는 단(별 0, 최근 기록 0)이면 표 보고 외우기, 아니면 차근차근
    static func recommended(table: Int, stars: Int, skill: TableSkill) -> IslandMethod
    /// 타일에 그릴 예시 식. 단이면 (단, 3), 섞어서면 (7, 8). OX 타일의 틀린 답은 곱 + 1
    static func sample(table: Int?) -> (a: Int, b: Int)
    /// 배우기 타일의 식 칩 하나: 단 × 2 = 곱
}
```

- 풀기 타일 목록은 지금 `Modes.options(table:)` 그대로다(단이면 차근차근·스피드런·빈칸 추리·OX 퀴즈, 섞어서면 6개). 모드 목록을 바꾸지 않으므로 업적 기준도 그대로다.
- 미션 구슬이 열 게임: `MissionBoard` 에 `game(for:)` 를 더한다. 계단 미션은 구구 계단, 몬스터 미션은 구구 몬스터, 세 번째 미션(연속 정답·코인 모으기)은 **아직 미션을 못 끝낸 첫 게임**(계단 → 몬스터 순), 둘 다 끝났으면 구구 계단. V1 이 열어 둔 질문이다. 아이가 아직 할 일이 남은 게임으로 가게 했다.
- 먹구름 표시: `Problems.tablesWithWrong(_ wrongPool:) -> Set<Int>` 를 더한다(오답 가중치가 남은 문제가 있는 단). 기존 `dominantWrongTable` 옆.

### 2-8. 잠김 카드

`GuardianStep.kidLocked(GameMode)` 하나다. 잠긴 모드(`PremiumConfig.premiumModes`: 60초 챌린지·서바이벌·빈칸 추리·OX 퀴즈)를 누르면 뜬다. 게임 체험 끝은 체험 끝 창이 이미 아이용 설명을 하므로 잠김 카드를 건너뛰고 [보호자 부르기]가 바로 `callGuardian` 을 보낸다.

### 2-9. 알림 이동 대응표

`RootView.applyPendingRoute` 와 Android `Router.open` 에 흩어진 분기를 순수 함수 하나로 모은다. iOS 는 지금 이 분기에 테스트가 없다.

```swift
enum NavTarget: Equatable { case tab(AppTab), guardian(GuardianDestination) }
extension NavTarget { static func from(_ route: PushRoute, isPremium: Bool) -> NavTarget }
```

| 알림 이동 | 대상 |
|---|---|
| `home` | `.tab(.home)` |
| `learn` | `.tab(.map)` |
| `profile`, `profile/report` | `.guardian(.report)` |
| `settings/notifications` | `.guardian(.settings(focus: .notifications))` |
| `paywall` | 이용권 있음 `.tab(.home)`, 없음 `.guardian(.paywall)` |

보호자 대상은 `GuardianEvent.route` 로 넘어가 3초 문부터 시작한다. 알림은 아이가 누를 수도 있기 때문이다. 적용 시점 규칙(온보딩 전·세션·게임·옷장 중에는 보관)은 그대로이고, iOS 는 떠 있는 섬 시트(`Router.islandSheet`)를 먼저 닫는다.

### 2-10. 테마 첫값

```swift
extension Theme { static func initial(saved: String?) -> Theme }   // ios/Core/Models.swift, and/core/Models.kt
```

| 저장값 | 지금 | 바뀐 뒤 |
|---|---|---|
| 없음(테마를 고른 적 없음) | 다크 | **낮(light)** |
| `dark` | 다크 | 밤(dark). 직접 고른 사람은 그대로 |
| `light`, `system` | 그대로 | 그대로 |
| 그 밖의 문자열(손상) | 다크(Android `else`) | 낮, 로그 한 줄 |

- 고른 적 없는 기존 사용자는 업데이트 뒤 낮으로 바뀐다. V1 이 "라이트 기본"을 정했고, 이 사람들은 다크를 고른 것이 아니라 첫값을 받은 것이다. 저장값을 건드리지 않으므로 코드를 되돌리면 원래대로 돌아간다.
- iOS `ThemeStore` 는 `didSet` 에서만 저장해서 "없음"과 "다크를 고름"이 구별된다. Android `ThemeStore.set` 도 고를 때만 쓴다. 둘 다 확인했다.
- 테마 이름은 "낮·밤·기기 따라"로 바꾼다(저장값 `light`·`dark`·`system` 그대로).
- 바뀌는 범위는 앱 전체다. 세션·결과·결제·온보딩 화면도 낮으로 보인다. 이 화면들은 이미 라이트 팔레트가 있어 새로 그릴 것은 없고, U11 캡처 검수에 넣는다. 게임 화면은 지금처럼 고정 팔레트다.

---

## 3. 화면별 명세 (1단계)

### 3-0. 모든 화면 공통

**글자가 든 상자는 고정 폭을 쓰지 않는다** (대표님 지적: V1 섬 시트 ① 배우기 줄의 "5×1=5", "5×2=10" 칩이 네모 밖으로 넘침, `shots/03-island-sheet.png`).

- 칩·타일·버튼·배지·말풍선·탭 이름·표 행은 내용에 맞춰 커진다. 고정하는 것은 최소 크기와 안쪽 여백뿐이다.
- 글자를 줄이거나 잘라서 맞추지 않는다. 새로 만들거나 고치는 화면에서 iOS `minimumScaleFactor`, 잘림을 전제로 한 `lineLimit(1)`, Android `FitText`, `maxLines = 1` + `TextOverflow.Ellipsis` 를 쓰지 않는다.
- 한 줄에 안 들어가면 이 순서로 푼다. ① 개수를 줄인다(칩 2개 → 1개). ② 두 줄로 감싼다(`FlowRow`, `fixedSize(horizontal: false, vertical: true)`, Compose `FlowRow`). ③ 배치를 바꾼다(iOS `ViewThatFits`, Compose 폭 측정으로 가로 → 세로, 2열 → 1열).
- 크기를 고정해도 되는 것은 그림(캐릭터·아이콘·섬)과 원형 숫자 딱지의 **최소** 지름뿐이다. 숫자가 커지면 원도 커진다.
- 이 규칙은 스크립트로 막는다(U3 에서 만듦). `tools/redesign/no-shrink.sh` 가 1단계에서 새로 만들거나 고친 폴더(`ios/Features/{Home,Map,Treasure,Guardian,Shell}`, `and/features/{home,map,treasure,guardian,shell}`, 섬 시트·표 시트 파일)에서 위 패턴을 찾아 0건이어야 통과한다. 꼭 필요한 줄은 끝에 `// fit:ok <이유>` 를 달아 검토받는다.
- 기존 공용 부품 `FlowRow`(지금 `ios/Features/Profile/ProfileView.swift` 안)는 `ios/DesignSystem/Layout.swift` 로 옮긴다. Android 는 Compose `FlowRow` 를 쓴다.

**색과 모양.** 아이 화면(홈·지도·보물·섬 시트·잠김 카드·문 카드·탭 바)은 V1 `DESIGN.md` 색 토큰을 새 토큰 묶음으로 만든다. `ios/DesignSystem/WorldTheme.swift`(`Color.world.*`), `and/designsystem/WorldTheme.kt`(`LocalWorld`). 낮·밤 두 벌이고 테마 선택(`colorScheme`)을 따른다.

| 토큰 | 낮 | 밤 | 쓰는 곳 |
|---|---|---|---|
| line | #2B2D5E | #0F1236 | 테두리 3, 아래 그림자 5. 밤은 V1 값(#2B2D5E)이 바탕에 묻혀서(V1 알려진 약점) 판보다 어두운 값으로 바꿨다. U11 캡처로 확인 |
| text / text2 / text3 | #2B2D5E / #5B6092 / #98A0C6 | #F4F6FF / #C3C9F2 / #8C94CC | 본문, 보조, 꺼진 탭 |
| sky1 → sky3 | #79CBFF → #BDE7FF → #EDF9FF | #161A4A → #2A3379 → #4352A6 | 홈·지도 바탕 |
| grass / grass2 | #93DE72 / #62C760 | #3E9A6C / #2B7756 | 홈 언덕 |
| paper / paper2 | #FFFFFF / #F3F7FD | #283170 / #333E86 | 판, 시트, 탭 바 |
| cream | #FFF5DC | #333E86 | 보물 탭 바탕 |
| sun | #FFD45C | 같음 | 누르면 시작하는 것에만(게임 이름판, ▶, 오늘의 섬 숫자, 켜진 탭) |
| ok / pink / lav | #1FAE6E / #FF9DBB / #B3B1F7 | 같음 | 미션 고리·완료 / 도장 / 레벨 |
| 계단 문, 몬스터 문 | #BFE6FF→#EAF7FF, #BFF0D2→#E9FBF0 | #2F4FA0→#4366B8, #256B58→#318469 | 게임 문 바탕(지금 짙은 초록 받침 #1E3A2C 대신) |

- 젤리 판 모양 하나: `line` 3pt 테두리 + 아래로 5pt 어긋난 `line` 그림자, 모서리 22~28. 블러 없음. `.worldCard()` / `Modifier.worldCard()`.
- 보호자 구역(문 카드 제외)은 새 토큰을 만들지 않고 기존 `GG` 토큰(밝은 회청색, 다크 팔레트 있음)을 쓴다. V1 보호자 색(#F5F6FA, #FFFFFF, #1C2140)과 거의 같고, 지금 설정 화면을 그대로 옮겨 올 수 있다. 상태 색도 `GG.success`·`warning`·`danger` 를 쓰고 늘 기호(✔ ● ▲)와 글을 같이 둔다. 칩 글자색은 `GG.text`, 바탕은 상태색 15%.
- 세션·결과·게임 화면 색은 이번 범위 밖이다.

**탭 바.** iOS 는 시스템 `TabView` 대신 세 화면을 `ZStack` 에 살려 두고(선택 안 된 화면은 숨김, 누르기·화면 읽기 꺼짐) 아래에 `WorldTabBar` 를 둔다. 스크롤 위치가 지금처럼 남고, iPadOS 18 의 위쪽 탭 바 변형이 끼어들지 않는다. Android 는 지금 `MainTabs` 의 `TabItem` 줄을 같은 모양으로 바꾼다. 탭 세 개는 같은 폭, 그림 `tab_home`·`tab_map`·`tab_treasure` 위에 이름. 켜진 탭은 sun 바탕 둥근 판 + line 테두리, 이름 굵게. 이름이 안 들어가면 두 줄. 누르면 가벼운 진동(지금과 같음). 화면 읽기: 탭 역할 + 선택됨. Android 뒤로 가기는 지금처럼 홈이 아니면 홈으로.

**보호자 열쇠 단추.** `GuardianKeyButton`. 모든 탭 머리 오른쪽 위, `icon_key` + "보호자", paper2 바탕 알약, 최소 높이 44. 한 번 누르면 `tapKey`. 화면 읽기 이름 "보호자", 도움말 "눌러서 보호자 문 열기".

**읽기 폭.** iPad 와 큰 Android 화면에서 내용 폭 640(섬 시트 560, 문·잠김 카드 360). 홈·지도 하늘 바탕과 보물 크림 바탕은 화면 끝까지.

**동작 줄이기.** iOS `accessibilityReduceMotion`, Android 기존 `ReduceMotion`. 꺼지는 것은 말풍선 둥실, 보물상자 통통, 게임 문 그림 숨쉬기(기존 `cardBreathing` 규칙), 오늘의 섬 반짝임, 탭 전환 움직임이다. 3초 고리 채움은 기능이라 남기되 튕김 없이 일정한 속도로 채운다.

**화면 읽기 공통.** 그림은 모두 숨기고(`accessibilityHidden`) 단추 하나에 이름 + 도움말을 단다. 일본어 문장부호 규칙대로 두 문장을 ". "로 잇지 않고 이름·값·도움말로 나눈다. 쉼표로 잇는 구(예 "7단 섬, 별 2개")는 지금 코드처럼 쓴다.

### 3-1. 탭 틀

- 순서: 홈 · 지도 · 보물. 처음 탭은 홈.
- 머리는 탭마다 다르다. 홈은 칩 둘 + 열쇠, 지도는 "모험 지도" + 별 칩 + 열쇠, 보물은 "보물" + 열쇠.
- 루트 겹침 순서(아래 → 위): 탭 화면 → 잠김 카드(`kidLocked`) → 문 카드(`holding`) → 보호자 화면(`open(.report/.settings)`) → 결제 화면(`open(.paywall)`). 세션·게임·옷장 전체 화면은 지금처럼 그 위.
- 앱 복귀(`scenePhase` / `ON_START`)에 떠나 있던 초를 `resumed(awaySeconds:)` 로 보낸다.

### 3-2. 홈 (구구네 마당) (V1 비교: `shots/01-home.png`, `01-home-night.png`, `01-home-vi.png`)

블록 순서(위 → 아래):

1. 머리 줄: 연속 칩(`combo_flame` + "3" + "일째"), 코인 칩(`icon_coin` + 잔고), 빈칸, 열쇠.
2. 마당 장면: 하늘 바탕, 구름(`sky_cloud_puff`), 새(`sky_bird`), 무지개(`sky_rainbow`)와 집(`house_gugu`), 언덕(grass). 왼쪽에 옷 입은 구구(`gugu_happy` + 쓰고 있는 장신구, `DressedGugu`), 발밑 "Lv.8" 딱지, 옆에 옷걸이 단추(`icon_hanger`). 오른쪽 위에 말풍선(섬 그림 + 단 숫자 원 + "오늘의 섬" + "5단 가 볼까?" + sun ▶).
3. 미션 판(`DailyMissionsCard` 를 구슬 모양으로 고쳐 씀): "오늘의 미션" + "1 / 3", 구슬 3개(그림 = 계단 `icon_stairs`, 몬스터 `monster_green`, 코인 `icon_coin` 또는 연속 `combo_flame`, ok 색 진행 고리, 아래 "3 / 5", 다 하면 초록 ✓, 아니면 sun ▶ 딱지), 보물상자(`chest_closed`/`chest_open` + "+10" + 코인), 점선, "이번 주 도장" + 월~일 7칸(도장 = pink ✓, 오늘 = line 테두리).
4. 게임 문 두 장: 구구 계단(`portal_stairs`), 구구 몬스터(`portal_forest`). 왼쪽 위 칩, 아래 sun 이름판 "▶ 구구 계단".
5. 탭 바.

| 누르는 곳 | 가는 곳 |
|---|---|
| 말풍선 | `router.openIsland(.table(t))` (섞어서면 `.mixed`) → 지도 탭 + 섬 시트 |
| 구슬 | `MissionBoard.game(for:)` 의 게임을 `router.open(game)` 으로 시작(다 한 미션도 시작) |
| 보물상자 | 준비됐을 때만 단추. 기존 `openChest` + 꽃가루 + 소리 + 진동 그대로 |
| 게임 문 | `router.open(.stairs / .monster)` |
| 옷걸이 | `router.openWardrobe()` |
| 연속 칩, 코인 칩, Lv 딱지 | 단추 아님 |

- 빈 상태(첫 실행): 연속 칩 "0일째"는 불 꺼진 그림(지금 규칙), 코인 0, 말풍선 "2단 가 볼까?", 미션 0 / n, 도장 빈칸, 게임 문 칩 없음(기록 0이면 숨김).
- 무료와 이용권: 게임 문 왼쪽 위 칩만 다르다. 무료는 "10문제 체험"(`MinigameTrial.badge`), 이용권은 🏆 최고 기록(`icon_trophy` + 칸·마리 수, 0이면 숨김). 가격·권유 글은 없다.
- 스크롤: 390×844 기본 글꼴에서 스크롤 없이 들어가게 짠다. 안 들어가는 기기(SE)와 큰 글꼴에서는 세로 스크롤로 넘친다. 장면을 줄여 맞추지 않는다.
- 말풍선 글은 내용만큼 키가 커진다(vi 3줄). 게임 이름판은 두 줄까지 감싼다(V1 vi 캡처와 같음).
- 화면 읽기 순서와 이름:

| 요소 | 이름 | 값·도움말 |
|---|---|---|
| 연속 칩 | 기존 "%lld일 연속 학습 중" / "연속 학습 기록 없음, 오늘 시작해요" | |
| 코인 칩 | "코인 %lld개" | |
| 열쇠 | "보호자" | "눌러서 보호자 문 열기" |
| 말풍선 | "오늘의 섬, %lld단" (섞어서면 "오늘의 섬, 섞어서") | 기존 "눌러서 연습 방법 고르기" |
| Lv 딱지 | "레벨 %lld" | |
| 옷걸이 | 기존 "옷장 열기" | |
| 미션 판 머리 | 기존 "오늘의 미션, %lld개 중 %lld개 완료" | |
| 구슬 | 미션 문구(기존 `Mission.title`) | 값 "%lld / %lld", 도움말 "눌러서 %@ 시작"(게임 이름) |
| 보물상자 | 준비 "보물상자 열기!", 전 "미션 3개를 다 하면 보물상자가 열려요", 연 뒤 "내일 새 미션이 와요!"(모두 기존) | |
| 도장 | 기존 카드 낭독 그대로 | |
| 게임 문 | 게임 이름 + ", 최고 %lld칸"/", 최고 %lld마리" 또는 ", %lld문제 체험" | 기존 `Minigame.desc` |

- 동작 줄이기: 말풍선 둥실(위아래 2pt, 2.4초)·상자 통통·문 숨쉬기 끔.
- iPad: 장면과 판은 폭 640 가운데, 하늘·언덕 바탕은 끝까지. iPad 가로에서 높이가 모자라면 스크롤.
- 밤: sky·grass·paper 밤 값, 그림은 같은 것. `01-home-night.png` 와 비교.

### 3-3. 지도 탭 (1단계 임시) (V1 비교 그림 없음, 머리만 `02-map.png` 와 같음)

블록 순서:

1. 머리: "모험 지도" + 별 칩(`star` + "6 / 24", 2~9단 별 합 / 24) + 열쇠.
2. 위 줄: 섞어서 칸(`sky_rainbow` + "섞어서"), 10~19 칸(`sky_balloon` + "10~19").
3. 2~9단 칸 8개, 숫자 순서(2→9). 칸 = 섬 그림(`WorldArt.isle(for:)`, 단→섬 대응은 화면 상수) + 큰 숫자 원(최소 지름 64, 숫자 40pt 이상) + 별 줄(별이 1개 이상일 때만) + 먹구름 딱지(`icon_cloud`, 그 단에 헷갈린 문제가 있을 때). 오늘의 섬은 숫자 원 sun 채움 + 옆에 작은 구구 + "오늘은 여기!" 꼬리표.
4. 오른쪽 아래 떠 있는 "먹구름 쫓기" 단추(`icon_cloud` + 글 + 빨간 숫자 = `wrongPool` 문제 수). 헷갈린 문제가 없으면 숨김.

- 칸 배치는 적응형 격자(최소 칸 폭 140). 폰 2열, iPad 3~4열. 큰 글꼴에서 칸이 안 들어가면 1열.
- 누르면: 숫자 칸 → 섬 시트(`.table(t)`), 섞어서 → `.mixed`, 10~19 → `.sky`(10~19 숫자 칸 10개가 있는 시트, 누르면 그 단 섬 시트로 바뀜), 먹구름 쫓기 → 기존 `session.startReview()`.
- 빈 상태: 별 줄 없음, 먹구름 없음, 오늘의 섬 = 2단, 먹구름 쫓기 숨김.
- 무료와 이용권: 차이 없음.
- 화면 읽기: 칸 순서 2→9 → 섞어서 → 10~19 → 먹구름 쫓기(화면 위치와 달라도 이 순서로 정렬). 칸 이름 "%lld단 섬, 별 %lld개" + 붙는 말 ", 오늘의 섬", ", 헷갈린 문제가 있어요". 섞어서 "섞어서, 모든 단 골고루"(지금 낭독 "2~9단 골고루"는 실제 출제 범위 2~19단과 틀려서 고친다, audit C6). 10~19 "하늘 섬, 10단부터 19단까지". 먹구름 "먹구름 쫓기, 헷갈린 문제 %lld개 복습". 별 칩 "단 별 %lld개, 모두 %lld개".
- 동작 줄이기: 오늘의 섬 반짝임 끔.
- 밤: sky 밤 값.

### 3-4. 섬 시트 (V1 비교: `shots/03-island-sheet.png`, `03-island-sheet-vi.png`, `08-locked-kid.png`)

`ios/Features/Map/IslandSheet.swift`(지금 `PracticeSheet` 를 대신함), `and/features/map/IslandSheet.kt`. 열기·시작 흐름(시트가 다 닫힌 뒤 세션·게임 시작, `PracticeStart`)은 지금 코드 그대로다.

블록 순서(단 섬):

1. 머리: 섬 그림 + 숫자 원, "5단 섬", 별 3칸(받은 만큼 채움), 닫기(✕).
2. ① 배우기: **한 줄 전체 폭 타일 하나.** 책 그림(`icon_book`) + 식 칩 **1개**("5 × 2 = 10", `IslandPlan` 배우기 칩) + "표 보고 외우기". 칩과 글은 내용 폭. 한 줄에 안 들어가면(19단 + vi + 130%) 글이 칩 아래로 내려간다. V1 의 칩 2개 + "…" 는 넘침의 원인이라 쓰지 않는다. 누르면 지금처럼 시트 안쪽 단계 `TableSheet`(표, 소리로 외우기, N단 연습)로 간다.
3. ② 풀기: 2×2 타일. 위 칸은 실제 문제 모양 그림, 아래 칸은 모드 이름.
   - 차근차근 `[5] × [3] = [?]`
   - 스피드런 `icon_hourglass` + `[5] × [3]`
   - 빈칸 추리 `[5] × [?] = [15]`
   - OX 퀴즈 `[5×3=16]` + O 원(ok) + X 원(빨강)
   - 숫자 상자는 내용 폭(최소 정사각). 타일 안에 식이 안 들어가면 1열로 바꾼다(식을 두 줄로 쪼개지 않는다).
   - "추천" 딱지는 `IslandPlan.recommended` 의 타일(차근차근 또는 ① 배우기)에 붙는다.
4. ③ 놀기: 게임 타일 두 장(구구 계단·구구 몬스터 그림 + 이름 + 아래 줄 "5단으로"). 구구 몬스터는 10단 이상이면 회색 + "9단까지", 누르기 꺼짐(지금 규칙).

섞어서 시트: 머리 "섞어서"(무지개 그림), ① 배우기 없음, ② 풀기 6개(위 4개 + 60초 챌린지 `[60]` 시계 원 + 식, 서바이벌 하트 3개 + 식, 최고 기록이 있으면 "최고 %lld" 딱지), ③ 놀기 아래 줄 "섞어서". 예시 식은 7 × 8.

하늘 섬 시트(10~19): 머리 "하늘 섬", 10~19 숫자 칸 10개(지도 칸과 같은 부품, 별 줄 규칙 같음). 누르면 같은 시트가 그 단의 섬 시트로 바뀐다.

- 무료와 이용권: 무료는 빈칸 추리·OX 퀴즈(섞어서는 60초·서바이벌도) 타일이 흐려지고 오른쪽 위에 `icon_lock`. 누르면 `tapLocked(mode)` → 잠김 카드(3-9). 게임 타일 아래 줄은 무료면 "10문제 체험", 이용권이면 "5단으로". 값·권유 글은 없다.
- 화면 읽기: 머리 "5단 섬, 별 %lld개"(+ ", 오늘의 섬"), 타일 이름 = 모드 이름(+ ", 추천", 잠김이면 ", 잠김"), 도움말 "눌러서 바로 시작" 또는 "눌러서 보호자 부르기". 배우기 타일 "표 보고 외우기", 도움말 기존 "구구단 표를 보고 소리로 외워요". 게임 타일 기존 규칙.
- 동작 줄이기: 표 단계로 넘어가는 이동은 지금처럼 페이드.
- iPad: 폭 560 그대로.
- 밤: paper 밤 값.
- `TableSheet` 도 넘침 점검 대상이다. 곱하는 수 19 설정에서 "19 × 19 = 361" 줄과 한글 읽기 줄이 안 잘려야 한다(지금 `minimumScaleFactor` 1곳 제거).

### 3-5. 보물 탭 (V1 비교: `shots/04-treasure.png`)

`ios/Features/Treasure/TreasureView.swift`, `and/features/treasure/TreasureScreen.kt`. 바탕 cream, 세로 스크롤.

블록 순서:

1. 머리: "보물" + 열쇠.
2. 나 판: 옷 입은 구구(`DressedGugu`, 자세 happy), "Lv.8" 딱지(lav), 칭호(`Level.title`), XP 막대, "38/305 XP"(기존 문구 "%lld/%lld XP").
3. 옷장 판: 머리 `icon_hanger` + "옷장" + 오른쪽 코인 잔고. 장신구 6칸(적응형 격자, 최소 칸 폭 56, 안 들어가면 3칸 2줄). 가진 것 = 흰 칸, 쓰는 중 = ok 바탕 + ✓, 살 수 있음 = sun 바탕 + 코인 + 값, 못 삼 = 회색 + 코인 + 값. 판 전체가 단추 하나 → `router.openWardrobe()`(사기·쓰기는 옷장 화면 한 곳에서만).
4. 몬스터 도감 판: 머리 `monster_green` + "몬스터 도감" + "5 / 8". 8칸 4×2. 만난 종 = 그림, 해금됐지만 못 만남 = 그림자 + "?", 해금 전 = 점선 테두리 그림자 + "32/45"(누적 처치 / 해금 기준, `MonsterDex.unlockAt`). 단추 아님.
5. 배지 판: 머리 `icon_trophy` + "배지" + "7 / 17 ›". 동그란 메달 5개(받은 것 먼저 정의 순서, 이어서 못 받은 것). 메달 = sun 원 + 기존 `IconMap` 기호, 못 받은 것 = 점선 원 + 회색. 메달 누르면 기존 `AchievementDetailSheet`, 머리 줄 누르면 `BadgeListSheet`(지금 프로필 3열 격자를 옮김).
6. 최고 기록 판: 머리 "최고 기록". 4칸(구구 계단 "%lld칸", 구구 몬스터 "%lld마리", 60초 챌린지 "%lld점", 서바이벌 "%lld점"). 기록 0은 "—". 단추 아님.

- 빈 상태: Lv.1, 옷장 전부 회색 + 값, 도감 4칸 그림자 "?" + 4칸 "0/10" 등, 배지 0 / 17 점선 5개, 최고 기록 "—".
- 무료와 이용권: 차이 없음.
- 화면 읽기: 판마다 한 덩어리. 나 판 "레벨 %lld, %@, 다음 레벨까지 %lld XP". 옷장 "옷장, 장신구 %lld개 중 %lld개 가짐", 도움말 기존 "눌러서 옷장 열기". 도감 "몬스터 도감, %lld종 중 %lld종 만남". 배지 "배지, %lld개 중 %lld개", 도움말 "눌러서 모든 배지 보기". 메달은 기존 "%@, 달성"/"%@, 진행 중". 최고 기록 칸은 이름 + 값.
- 동작 줄이기: 움직임 없음.
- iPad: 폭 640.
- 밤: cream 밤 값.

### 3-6. 보호자 문 (3초 꾹 누르기) (V1 비교: `shots/05-guardian-gate.png`)

`ios/Features/Guardian/GuardianGateCard.swift`, `and/features/guardian/GuardianGateCard.kt`. `holding(d)` 일 때 뜬다.

- 어둡게 덮기(rgba(20,22,55,.62)) 위 가운데 카드(최대 폭 360): `icon_key`, "보호자 전용", "아래 버튼을 3초 동안 꾹 눌러 주세요.", "리포트·설정·이용권이 열려요.", 누르기 원(지름 132, 안에 `icon_lock` + "꾹 누르기"/"꾹 누르는 중…", 바깥 고리 = 진행률), 작은 글 "결제·초기화·계정 삭제는 안에서 한 번 더 확인해요.", 흐린 단추 "아이 화면으로 돌아가기".
- 누르기: 손가락을 대는 동안 고리가 3.0초에 걸쳐 일정하게 찬다. 1초·2초에 가벼운 진동, 다 차면 성공 진동 + `holdCompleted`. 중간에 떼면 고리가 0.2초 동안 0으로 돌아간다. 카드 밖 누르기·돌아가기 단추·Android 뒤로 가기 → `dismiss`.
- 결제 목적(`holding(.paywall)`)일 때 둘째 줄은 "평생 이용권을 볼 수 있어요."로 바뀐다.
- 화면 읽기: 누르기 원 이름 "보호자 문 열기", 도움말 "3초 동안 길게 누르세요". iOS VoiceOver 는 두 번 탭 후 누르고 있기가 그대로 전달된다. 이와 별개로 사용자 지정 동작 "출생연도로 열기"를 단다 → `BirthYearGateView` 통과 시 `birthYearPassed`. Android TalkBack 도 같은 사용자 지정 동작(`CustomAccessibilityAction`)을 단다. 길게 누르기가 TalkBack 에서 전달되는지는 U4 에서 실기로 확인한다. 막혀 있어도 이 대안으로 들어갈 수 있고 보호 수준은 같다.
- 동작 줄이기: 고리 채움은 남기고, 성공 때 튕김 없음.
- 밤: paper 밤 값.

### 3-7. 보호자 리포트 (V1 비교: `shots/06-guardian-report.png`)

`ios/Features/Guardian/GuardianView.swift`(머리·쪽 고르기) + `ReportPage.swift`, Android `GuardianScreen.kt` + `ReportPage.kt`. 전체 화면, `GG` 토큰.

머리(두 쪽 공통): `icon_key` + "보호자", 오른쪽 "✕ 아이 화면으로"(`dismiss`). 아래 쪽 고르기 [리포트 | 설정·계정]. 처음 쪽은 목적지(`.report` / `.settings`)를 따른다.

블록 순서:

1. 한마디 판: `gugu_idle` + `ReportHeadline` 문장.
2. 단별 실력 판: "단별 실력", 아래 작은 글 "최근 2주 · 정확도 · 한 문제 평균 시간". "약한 단" + ▲ 딱지들(없으면 줄 숨김), 오른쪽 "잘해요 기준 90%". 행 = "N단" | 정확도 막대(90% 자리 점선) | "58%" | "5.6초"(기존 `Seconds.text`) | 상태 칩(기호 + 글). 행이 한 줄에 안 들어가면(큰 글꼴, 긴 언어) 두 줄 행으로 바꾼다(1줄 단 이름 + 막대, 2줄 숫자 + 칩). 아래 "자주 틀린 문제" + 칩(최대 5개, 감싸기).
3. 이번 주 판: "이번 주" + 작은 글 "정답 수". 월~일 막대(위에 숫자, 오늘 강조, 미래 날 빈칸), 하루 목표 점선 + "하루 목표 %lld". 지난주 비교 한 줄(기존 문구). 아래 네 칸: 학습한 날, 정답, 정확도, 푼 시간(기존 문구).
4. 최근 정확도 추이 판(최근 판이 2개 이상일 때, 기존 `Sparkline`·문구 그대로).

- 누르는 곳 없음(리포트는 읽기 전용). 복습 시작 단추는 두지 않는다. 아이의 복습 입구는 지도 탭 먹구름 쫓기 한 곳이다.
- 빈 상태: 한마디 `.noRecord`, 행 모두 "기록 없음", 막대 비어 있음, 추이 판 숨김.
- 기존 사용자: 업데이트 직후 2주 동안은 새로 쌓인 판만 표에 들어간다(2-4). 자주 틀린 문제와 이번 주 판은 기존 기록으로 바로 채워진다.
- 화면 읽기: 행 한 덩어리 "%lld단, 정확도 %lld%, 한 문제 평균 %@, %@"(상태), 기록 없으면 "%lld단, 최근 기록 없음". 막대 그래프는 기존 `WeeklyReportCard` 낭독 방식(요일 이름 + 정답 수)을 그대로.
- iPad: 폭 640.
- 밤(다크): `GG` 다크.

`ReportHeadline` 문장:

| 경우 | 한국어 원문 |
|---|---|
| noRecord | 최근 2주 동안 기록이 없어요. 아이와 함께 한 판 해 보세요. |
| weak(first, nil) | %lld단이 약해요. 오늘은 %lld단 「표 보고 외우기」부터 권해요. |
| weak(first, second) | %lld단·%lld단이 약해요. 오늘은 %lld단 「표 보고 외우기」부터 권해요. |
| keepGoing(next) | 약한 단은 없어요. 오늘은 %lld단을 연습해 보세요. |
| allStrong | 모든 단을 잘해요! 섞어서 60초 챌린지에 도전해 보세요. |

"N단" 뒤 조사는 늘 "이"·"을"이라 한국어 조사 처리가 필요 없다. 다른 언어는 번역문이 순서를 정한다.

### 3-8. 보호자 설정·계정 (V1 비교: `shots/07-guardian-settings.png`)

`ios/Features/Settings/SettingsView.swift` 를 시트에서 쪽 내용(`SettingsPage`)으로 바꾸고, 지금 섹션 코드를 옮겨 묶는다. Android `SettingsScreen.kt` 도 같다. 안쪽 쪽은 iOS `NavigationStack`, Android 쪽 상태 + 뒤로 가기.

| 묶음 | 줄 | 누르면 |
|---|---|---|
| 학습 | 하루 목표(10·20·30·50 칩), 곱하는 수(9·12·15·19 칩), 문제 읽어주기(스위치, ko·en·ja 만) | 지금과 같음 |
| 화면·소리·알림 | 테마(낮·밤·기기 따라 칩), 언어(행, iOS 는 시스템 설정 열기·Android 는 앱 안 고르기 그대로), 효과음(스위치), 진동(스위치), 알림 › | 알림 쪽: 지금 `reminderSection` 전체(학습 알림·시각, 새 기능 안내 띠, 공지, 보호자 학습 소식, 광고) |
| 계정·이용권 | 평생 이용권 ›(이용 중 / 무료면 "평생 이용권 · 커피 한 잔 값" + 값), 기록 지키기 ›, 버전 | 이용권 쪽: 결제 화면 열기(무료), 구매 복원, 이용약관, 개인정보처리방침. 기록 지키기 쪽: `AccountSettingsCard`(로그인·로그아웃, 계정 삭제 칸은 뺌) |
| 되돌릴 수 없는 일 | 학습 기록 초기화, 계정 삭제(이메일 계정일 때만) | 둘 다 오른쪽에 `icon_lock` + "보호자 확인" 칩 → 출생연도 → 기존 확인 단계 → 실행 |

- 효과음·진동은 V1 처럼 합치지 않는다. 저장값 두 개가 따로 있고 따로 끄는 사람이 있다.
- 알림 이동 `settings/notifications` 는 알림 쪽을 바로 연다(`focus: .notifications`).
- 계정 삭제 칸은 `AccountSettingsCard.deleteAccountSection` 을 `DeleteAccountRow` 로 빼서 이 묶음에 둔다. 다시 로그인이 필요한 상태 문구 등 지금 규칙은 그대로.
- 이용권 쪽의 결제 화면은 보호자 화면 위에 뜬다(iOS 보호자 화면 안의 `.sheet`, Android 보호자 화면 안 겹침). 닫으면 보호자 화면으로 돌아온다.
- 무료와 이용권: 이용권 줄 글과 값만 다르다.
- 화면 읽기: 기존 설정 낭독 그대로, 묶음 제목은 머리글 속성.
- iPad: 폭 640. 밤(다크): `GG` 다크. 지금 설정 시트처럼 `deviceScheme` 을 넘겨 '기기 따라'일 때도 즉시 반영한다.

### 3-9. 잠김 카드, 게임 체험 끝, 결제 직전 확인 (V1 비교: `shots/08-locked-kid.png`)

잠김 카드(`ios/Features/Guardian/KidLockedCard.swift`, Android `KidLockedCard.kt`): 어둡게 덮기 위 카드(최대 폭 360). 구구(`gugu_idle`) + 자물쇠(`icon_lock`), "어른이랑 같이 열어요", "%@ 잠겨 있어요."(한국어는 `KoreanReading.topic(모드 이름)` 을 넣어 "빈칸 추리는", 다른 언어는 이름 그대로), "보호자에게 이 화면을 보여 주세요.", sun 단추 `icon_key` + "보호자 부르기"(`callGuardian`), 흰 단추 "다음에 할래요"(`dismiss`). 가격·이용권 이름은 없다.

게임 체험 끝 창(`TrialEndPanel`, `ios/Features/Paywall/MinigameTrial.swift`): 제목 "무료 체험은 여기까지!"는 그대로, 설명을 "더 놀고 싶으면 어른이랑 같이 열어요."로, 단추 "평생 이용권 보기"를 "보호자 부르기"(`icon_key`)로 바꾼다. 누르면 그 게임 화면의 흐름 값에 `callGuardian` → 문 카드 → 결제 화면. 시작 전 안내 `TrialNotice` 의 "평생 이용권이 있으면 제한 없이 놀 수 있어요."도 아이 화면이라 "체험이 끝나면 어른이랑 같이 열 수 있어요."로 바꾼다.

결제 직전 확인: `PaywallView`·`PaywallScreen` 의 구매 단추 → `BirthYearGateView` → 통과하면 `premium.purchase()`. 취소하면 결제 화면에 그대로 있다.

---

## 4. 문구 (한국어 원문 = 키)

순서: iOS 단위가 문구를 소스에 넣고 `python3 tools/i18n/catalog.py sync && lint`, 번역 JSON(en·ja·hi·vi·id, 상태 needs_review)을 `apply`, `check` 통과 → Android 가 같은 한국어 키로 `python3 tools/i18n/android.py name && gen && check`. 번역은 V1 사전(`index.html` 311~312줄 en·vi)을 출발점으로 쓴다. 말풍선·탭 이름은 짧게 잡는다.

### 4-1. 새 문구

| 화면 | 원문 | 비고 |
|---|---|---|
| 탭 | 지도 | "홈"·"보물"은 기존 키 재사용(보물 en "Treasures") |
| 열쇠·보호자 | 보호자 | 열쇠 단추, 보호자 머리 |
| | 눌러서 보호자 문 열기 | 화면 읽기 도움말 |
| 문 카드 | 보호자 전용 / 아래 버튼을 3초 동안 꾹 눌러 주세요. / 리포트·설정·이용권이 열려요. / 평생 이용권을 볼 수 있어요. / 꾹 누르기 / 꾹 누르는 중… / 결제·초기화·계정 삭제는 안에서 한 번 더 확인해요. / 아이 화면으로 돌아가기 | |
| | 보호자 문 열기 / 3초 동안 길게 누르세요 / 출생연도로 열기 | 화면 읽기 이름·도움말·사용자 지정 동작 |
| 보호자 머리 | 아이 화면으로 / 리포트 / 설정·계정 | |
| 리포트 | 단별 실력 / 최근 2주 · 정확도 · 한 문제 평균 시간 / 약한 단 / 잘해요 기준 %lld% / 잘해요 / 연습 중 / 약해요 / 기록 없음 | 상태 글 |
| | %lld단, 정확도 %lld%, 한 문제 평균 %@, %@ / %lld단, 최근 기록 없음 | 행 낭독 |
| | 3-7 표의 한마디 5개 | `ReportHeadline` |
| | 이번 주 / 정답 수 / 하루 목표 %lld | |
| 설정 | 화면·소리·알림 / 계정·이용권 / 되돌릴 수 없는 일 / 낮 / 밤 / 기기 따라 | "알림" 은 기존 키 |
| 잠김 카드 | 어른이랑 같이 열어요 / %@ 잠겨 있어요. / 보호자에게 이 화면을 보여 주세요. / 보호자 부르기 / 다음에 할래요 | |
| | %@, 잠김 / 눌러서 보호자 부르기 | 타일 낭독 |
| 체험 | 더 놀고 싶으면 어른이랑 같이 열어요. / 체험이 끝나면 어른이랑 같이 열 수 있어요. | |
| 홈 | %lld일째 / 코인 %lld개 / 오늘의 섬 / %lld단 가 볼까? / 섞어서 가 볼까? / 오늘의 섬, %@ / 레벨 %lld / 눌러서 %@ 시작 / 최고 %lld칸 / 최고 %lld마리 | |
| 지도 | 모험 지도 / 단 별 %lld개, 모두 %lld개 / %lld단 섬, 별 %lld개 / 오늘의 섬 (붙는 말) / 헷갈린 문제가 있어요 / 섞어서, 모든 단 골고루 / 하늘 섬 / 하늘 섬, 10단부터 19단까지 / 먹구름 쫓기 / 먹구름 쫓기, 헷갈린 문제 %lld개 복습 / 오늘은 여기! | |
| 섬 시트 | %lld단 섬 / 배우기 / 풀기 / 놀기 / 표 보고 외우기 / %lld단으로 | |
| 보물 | 옷장 / 몬스터 도감 / 배지 / 레벨 %lld, %@, 다음 레벨까지 %lld XP / 옷장, 장신구 %lld개 중 %lld개 가짐 / 몬스터 도감, %lld종 중 %lld종 만남 / 배지, %lld개 중 %lld개 / 눌러서 모든 배지 보기 / %lld칸 / %lld마리 | "%lld점", "최고 기록", "Lv.%lld", "%lld/%lld XP" 는 기존 키 |

U4~U9 가 자기 문구를 넣고, 표의 문구 중 다른 화면 문구와 겹치는 것은 먼저 넣는 단위가 넣는다.

### 4-2. 고쳐 쓰는 기존 문구

| 지금 | 바뀐 뒤 | 이유 |
|---|---|---|
| 섞어서, 2~9단 골고루 | 섞어서, 모든 단 골고루 | 실제 출제 범위는 2~19단(audit C6) |
| 2~9단 모두 마스터 (업적 설명) | 2~19단 모두 마스터 | 실제 목표 18단 |
| 무료 체험 창 설명·단추, `TrialNotice` 둘째 줄 | 3-9 | 아이 화면의 이용권 권유 제거 |

### 4-3. 지워지는 문구

코드에서 쓰지 않게 되면 `catalog.py sync` 가 카탈로그에서 지운다. 손으로 지우지 않는다. 아래는 검토용 목록이다(다른 곳에서 쓰면 남는다).

- 홈: 안녕하세요 👋 / 오늘도 구구단 한 판! / 오늘의 목표 / 목표 달성! 멋져요 🎉 / 정답 %lld개 더 풀면 달성! / 목표 바꾸기 / 오늘의 목표, 정답 %lld개 중 %lld개 / 눌러서 하루 목표 바꾸기 / 빠른 학습 시작 / 헷갈렸던 문제 %lld개를 모아 다시 풀어요 / 어떻게 놀아볼까요? / 배우기부터 게임까지, 모두 구구단 연습이 돼요. / 오늘 추천 단 / 로드맵 순서대로 한 단씩 / 기록 도전 / 속도와 집중력으로 최고 기록 / 오르고, 물리치며 구구단 연습 / 구구 꾸미기, 모은 별 %lld개 / 모은 별 %lld개 / 최고 %lld점 · %@ / %@ · 전체 랜덤, 하루 목표 시트 문구
- 학습: 학습(탭 이름, 설정 묶음 제목으로 남으면 유지) / 단 고르기 / 단을 누르면 연습 방법을 고를 수 있어요 / 어떻게 연습할까요? / 표·외우기 / 2~9단을 골고루 섞어서 / 오늘 추천 / 모든 단 별 3개! %@ / 오늘 추천 %@, %@ / %lld단, 별 %lld개 / %lld단, 별 %lld개, 추천 / %lld단으로 시작해요 / 섞어서 시작해요 / %@, %@, 잠김, 평생 이용권 필요 / 눌러서 평생 이용권 보기
- 프로필: 프로필 / 정답·정확도·최고 콤보 칸 / %lld XP · 다음 레벨까지 %lld XP / 집중 공략 문제 / 복습 시작 / 업적 (%lld/%lld) / 눌러서 목표 보기 / 다시 로그인이 필요해요 / 보호자 이메일로 확인해 주세요 / 이메일로 로그인하면 새 기기에서도 이어서 쓸 수 있어요 / 모든 모드와 구구 계단·구구 몬스터 · 한 번 구매로 계속 / 이번 주 학습 / 주간 리포트 한마디 5개(`parentTip`)
- 설정: 설정 / 완료(설정 머리 단추, 다른 곳에서 쓰면 유지) / 라이트 / 다크 / 기기 설정(언어 등에서 쓰면 유지)
- 체험: 평생 이용권 보기 / 평생 이용권이 있으면 제한 없이 계속 놀면서 연습할 수 있어요. / 평생 이용권이 있으면 제한 없이 놀 수 있어요.

**Android 순서 함정**(메모리 `games-swap-2.9`): iOS 가 문구를 지우고 sync 하면 아직 그 문구를 쓰는 Android 의 `android.py gen` 이 깨진다. iOS 단위가 지운 문구 중 Android 가 아직 쓰는 것은 `tools/i18n/android-only.d/redesign-pending.json` 으로 옮겨 두고, Android 같은 단위가 끝나면 그 파일에서 지운다. 1단계 끝(U11)에 이 파일은 비어 있어야 한다.

### 4-4. 용어 정리 (U10)

| 뜻 | 1단계 뒤 이름 | 바꾸는 문구 |
|---|---|---|
| 단 실력(마스터리) | 별 | 그대로 |
| 옷장 화폐 | **코인** | 게임에서 별 %lld개 모으기 → 게임에서 코인 %lld개 모으기 / 모은 별 %lld → 모은 코인 %lld / 받은 별 → 받은 코인 / 별 %lld개 더 모아요 → 코인 %lld개 더 모아요 / 별 %lld개로 사기 → 코인 %lld개로 사기 / 별 %lld개를 받았어요! → 코인 %lld개를 받았어요! / 지금 나가면 이번 판 별 %lld개와\n기록이 사라져요 → 코인 |
| 업적 | **배지** (아이 화면) | 업적 달성 → 배지를 받았어요 / 보물 탭 "배지" |
| 테마 | 낮·밤·기기 따라 | 4-1 |

- 그림은 이미 `icon_coin` 이라 그림은 안 바뀐다. 저장 필드 이름(`PlayRewards.stars`)도 그대로다. 화면 글만 바꾼다.
- "별·레벨·코인은 이 기기에만 저장돼요." 처럼 이미 코인을 쓰는 계정 안내 3개는 그대로 맞는다.
- `tools/i18n/glossary.csv` 에 "코인 (옷장 화폐), coin, コイン" 과 "배지, badge, バッジ" 를 더하고 "별" 줄 메모에 "단 실력 전용"을 적는다.

---

## 5. 파일 계획, 작업 순서

### 5-1. iOS 파일

| 구분 | 파일 | 내용 |
|---|---|---|
| 새 | `ios/Core/Guardian/GuardianFlow.swift` | 2-2 흐름, 2-3 `GuardedAction`, 2-9 `NavTarget` |
| 새 | `ios/Core/Guardian/GuardianReport.swift` | 2-4 `TableSkill`·`TableSkills`·`GuardianReport`·`ReportHeadline`(+ 문장 함수) |
| 새 | `ios/Core/TodayIsland.swift`, `ios/Core/IslandPlan.swift` | 2-6, 2-7 |
| 새 | `ios/DesignSystem/WorldTheme.swift` | 3-0 토큰, `.worldCard()`, 칩, `WorldArt` 그림 이름 |
| 새 | `ios/Features/Shell/WorldTabBar.swift`, `GuardianKeyButton.swift` | 3-0, 3-1 (`MainTabView` 를 `RootView.swift` 에서 이리로) |
| 새 | `ios/Features/Guardian/{GuardianGateCard, GuardianView, ReportPage, BirthYearGateView, KidLockedCard, GuardianPresenter}.swift` | 3-6~3-9. `GuardianPresenter` = 흐름 값 하나를 받아 잠김 카드·문 카드·결제 화면을 그 자리에 띄우는 모디파이어(루트·섬 시트·게임이 같이 씀) |
| 새 | `ios/Features/Treasure/{TreasureView, BadgeListSheet}.swift` | 3-5 |
| 새 | `ios/Features/Map/{MapView, IslandSheet, MethodTile}.swift` | 3-3, 3-4 |
| 새 | `ios/Features/Home/{TodayBubble, GameDoor}.swift` | 3-2 |
| 새 | `ios/Features/Games/Wardrobe/DressedGugu.swift` | `WardrobeView.preview` 의 구구 + 장신구 그리기를 빼냄(홈·보물·옷장이 같이 씀) |
| 새 | `ios/Resources/Assets.xcassets/World/*.imageset` | 5-3 그림(폴더 namespace `World/`) |
| 수정 | `ios/App/RootView.swift` | 탭 틀, 루트 `GuardianPresenter`, 알림 이동을 `NavTarget` 으로, `resumed` 보내기 |
| 수정 | `ios/Store/Router.swift` | 2-1 |
| 수정 | `ios/Store/ThemeStore.swift`, `ios/Core/Models.swift` | 2-10, 2-4 `TableTally`·`DayLogEntry.tables` |
| 수정 | `ios/Core/Commit.swift` | `appendDayLog` 단별 합계 |
| 수정 | `ios/Core/WeeklyReport.swift` | 2-5, `tables` 병합, `parentTip` 삭제(U4) |
| 수정 | `ios/Core/Hints.swift` | `nextRoadmapTable` 삭제(U7) |
| 수정 | `ios/Core/Problems.swift` | `tablesWithWrong` |
| 수정 | `ios/Core/Games/DailyMissions.swift` | `MissionBoard.game(for:)` |
| 수정 | `ios/Features/Home/HomeView.swift`, `DailyMissionsCard.swift` | 3-2 |
| 수정 | `ios/Features/Settings/{SettingsView, AccountSettingsCard, PushConsentSheet}.swift` | 3-8, `DeleteAccountRow` 분리, 출생연도 뷰 재사용 |
| 수정 | `ios/Features/Paywall/{PaywallView, MinigameTrial}.swift` | 3-9 |
| 수정 | `ios/Features/Games/Stairs/StairsView.swift`, `Monster/MonsterView.swift` | 체험 끝 → 지역 흐름 값 + `GuardianPresenter` |
| 수정 | `ios/Features/Games/Wardrobe/WardrobeView.swift` | `DressedGugu` 사용 |
| 수정 | `ios/Store/PremiumStore.swift` | `paywallOpen`·`openPaywall`·`closePaywall`·`gate` 삭제(U9) |
| 수정 | `ios/DesignSystem/Layout.swift` | `FlowRow` 옮겨 옴 |
| 옮김 | `ios/Features/Learn/TableSheet.swift` → `ios/Features/Map/TableSheet.swift` | 넘침 수정 포함 |
| 옮김 | `ios/Features/Profile/AchievementDetailSheet.swift` → `ios/Features/Treasure/` | |
| 걷어 냄 | `ios/Features/Profile/{ProfileView, WeeklyReportCard}.swift`, `ios/Features/Learn/{LearnView, PracticeSheet, RecommendCard}.swift`, `ios/Features/Home/DailyGoalSheet.swift` | `.claude/removed-redesign-v1/native-ios/...` + `MOVED.md` |
| 테스트 새 | `iosT/GuardianTests.swift` | 6-1 |
| 테스트 수정 | `iosT/{UxCoreTests, CoreTests, LegacySaveTests, PushTests, DailyMissionsTests}.swift` | 6-1 |

파일을 더하거나 옮긴 뒤 `cd native-ios && xcodegen generate`(`project.yml` 이 `Gugu` 폴더를 통째로 읽는다).

### 5-2. Android 파일

| 구분 | 파일 | 내용 |
|---|---|---|
| 새 | `and/core/guardian/{GuardianFlow, GuardianReport}.kt`, `and/core/{TodayIsland, IslandPlan}.kt` | iOS 와 같음 |
| 새 | `and/designsystem/WorldTheme.kt` | `LocalWorld`, `Modifier.worldCard()`, `WorldArt` |
| 새 | `and/features/shell/{WorldTabBar, GuardianKeyButton}.kt` | |
| 새 | `and/features/guardian/{GuardianGateCard, GuardianScreen, ReportPage, BirthYearGate, KidLockedCard}.kt` | |
| 새 | `and/features/treasure/{TreasureScreen, BadgeListSheet}.kt` | |
| 새 | `and/features/map/{MapScreen, IslandSheet, MethodTile}.kt` | |
| 새 | `and/features/home/{TodayBubble, GameDoor}.kt`, `and/features/games/wardrobe/DressedGugu.kt` | |
| 새 | `app/src/main/res/drawable-nodpi/world_*.webp` | 5-3 (기존 `game_*` 처럼 접두어) |
| 수정 | `and/MainActivity.kt` | `MainTabs` → 탭 틀, 루트 겹침에 잠김 카드·문 카드·보호자 화면·결제 화면(흐름 값 기준), 설정 겹침 삭제, `ON_START` 에서 `resumed` |
| 수정 | `and/store/Stores.kt` | `AppTab`, `Router`(2-1, `open(route)` → `NavTarget`), `ThemeStore`(2-10) |
| 수정 | `and/core/{Models, Commit, WeeklyReport, Hints, Problems}.kt`, `and/core/games/DailyMissions.kt` | iOS 와 같음 |
| 수정 | `and/features/home/{HomeScreen, DailyMissionsCard}.kt` | 3-2. `GaugeBar` 는 `and/designsystem/Components.kt` 로 옮김(세션 화면이 씀) |
| 수정 | `and/features/settings/{SettingsScreen, AccountSettingsCard, PushConsentSheet}.kt` | 3-8 |
| 수정 | `and/features/paywall/{PaywallScreen, MinigameTrial}.kt`, `and/features/games/{stairs/StairsScreen, monster/MonsterScreen, wardrobe/WardrobeScreen}.kt` | 3-9, `DressedGugu` |
| 수정 | `and/store/PremiumStore.kt` | `paywallOpen` 계열 삭제(U9) |
| 옮김 | `and/features/learn/TableSheet.kt` → `and/features/map/` | |
| 옮김 | `Sparkline`(지금 `ProfileScreen.kt`) → `and/designsystem/Components.kt` | 리포트가 씀 |
| 걷어 냄 | `and/features/profile/{ProfileScreen, WeeklyReportCard}.kt`, `and/features/learn/{LearnScreen, PracticeSheet}.kt` | `.claude/removed-redesign-v1/native-android/...` |
| 테스트 | `andT/GuardianTests.kt`(새), `andT/{UxCoreTests, CoreTests, LegacySaveTests, PushStoreTests, CoreLocalizationTests, DailyMissionsTests, MinigameEntryTests}.kt`(수정) | 6-1 |

### 5-3. 그림

그림 에이전트가 `.claude/scratch/redesign/art-final/` 에 만드는 중이다. 이 계획은 이름과 용도만 정한다. iOS 는 `Assets.xcassets/World/<이름>`, Android 는 `drawable-nodpi/world_<이름>.webp`, 코드는 `WorldArt.image("<이름>")` 로 부른다.

| 이름 | 용도 | 단계 | 2026-11-24 raw 상태 |
|---|---|---|---|
| `tab_home` | 탭 바 홈 | 1 | 있음 |
| `tab_map` | 탭 바 지도 | 1 | 없음 |
| `tab_treasure` | 탭 바 보물(가방) | 1 | 있음 |
| `icon_key` | 열쇠 단추, 문 카드, 보호자 머리, 보호자 부르기 단추 | 1 | 있음 |
| `icon_book` | 섬 시트 ① 배우기(책 + 소리) | 1 | 있음 |
| `icon_trophy` | 게임 문 최고 기록 칩, 보물 배지 머리 | 1 | 있음 |
| `house_gugu` | 홈 마당 집 | 1 | 있음 |
| `portal_stairs` | 홈 구구 계단 문 | 1 | 있음 |
| `portal_forest` | 홈 구구 몬스터 문 | 1 | 없음 |
| `isle_1` ~ `isle_6` | 1단계: 말풍선 섬, 섬 시트 머리, 임시 지도 칸. 2단계: 지도 섬 | 1·2 | 있음 |

다시 쓰는 기존 그림: `gugu_happy`, `gugu_idle`, `acc_*`, `icon_coin`, `combo_flame`, `icon_hanger`, `icon_lock`, `icon_hourglass`, `icon_stairs`, `icon_cloud`, `chest_closed`, `chest_open`, `star`, `sky_rainbow`, `sky_balloon`, `sky_cloud_puff`, `sky_bird`, `monster_*`.

그림이 늦으면 그 단위는 기존 그림 자리표시(같은 크기)로 진행하고 U11 전에 바꾼다. 자리표시는 `WorldArt` 한 곳에서만 고친다.

### 5-4. 작업 단위와 검증 포인트

한 단위는 "알려진 정상 상태 → 바꾸기 → 검사 통과"로 끝난다. 다음 단위는 앞 단위 검사가 초록일 때만 시작한다. 모든 단위 공통 검사: iOS 전체 테스트(XcodeBuildMCP `test_sim`, iPhone 17e), Android `cd native-android && ./gradlew testDebugUnitTest`, 문구를 건드렸으면 4절 i18n 게이트, U3 뒤로는 `tools/redesign/no-shrink.sh`.

| 단위 | 내용 | 플랫폼 | 검증 포인트 |
|---|---|---|---|
| U0 준비 | 미션·생동감 에이전트 작업 끝 확인, 두 플랫폼 전체 테스트 기준값 기록, `.claude/removed-redesign-v1/MOVED.md` 만들기, 디버그 훅 계획 확인(6-3) | 둘 | 테스트 초록·개수 기록 |
| U1 날짜 기록 단별 합계 | 2-4 저장 부분: `TableTally`, `DayLogEntry.tables`, `appendDayLog`, 병합 | iOS ∥ Android | 구버전 저장본 읽기, 왕복 보존(`testGameStateRoundTripPreservesEveryField` 갱신), 세션·게임·부분 기록이 단별로 쌓임 |
| U2 Core 계산 | `TableSkills`, `GuardianReport`(문장 함수 빼고), 이번 주 월~일, `TodayIsland`, `IslandPlan`, `MissionBoard.game(for:)`, `tablesWithWrong`, `GuardianFlow`, `NavTarget`, `Theme.initial` | iOS ∥ Android | 6-1 의 Core 테스트 전부 |
| U3 디자인 토대 | `WorldTheme`, `.worldCard()`, `FlowRow` 이동, 그림 넣기, `ThemeStore` 가 `Theme.initial` 사용, `no-shrink.sh`, 캡처·비교 스크립트(6-3) | iOS ∥ Android | 새로 설치 → 낮, 테마 키 `dark` 넣고 실행 → 밤(시뮬·에뮬 실제 확인) |
| U4 보호자 | `BirthYearGateView` 빼기(광고 동의 동작 같음), 문 카드, 보호자 화면(리포트·설정·계정), 위험 동작 확인, 한마디 문장 함수, `parentTip`·`WeeklyReportCard` 걷어 냄. 들어가는 길은 임시로 지금 프로필 톱니 | iOS → Android | 실제 탭: 3초 누르면 열림, 2초에 떼면 안 열림, 초기화 → 출생연도 틀림 → 막힘 → 맞음 → 확인 단계 → 초기화. 광고 동의 흐름 그대로. VoiceOver 사용자 지정 동작으로 열림. `GUGU_SEED=v1` 리포트 캡처 ↔ `06`, 설정 ↔ `07`, 문 카드 ↔ `05` |
| U5 보물 탭 | `TreasureView`, `BadgeListSheet`, 업적 상세 이동, `DressedGugu`, 프로필 화면 걷어 냄(톱니 → 열쇠) | iOS → Android | 캡처 ↔ `04`. 옷장 판 → 옷장, 메달 → 상세, 머리 → 목록 |
| U6 탭 틀 | `AppTab`, `WorldTabBar`, 세 탭 열쇠, 알림 이동 `NavTarget`, `Router.guardian` 루트 겹침, `resumed` | iOS → Android | 알림 이동 6종 실제 확인(iOS `xcrun simctl push`, Android 알림 테스트 경로), 탭 오가도 스크롤 유지, Android 뒤로 가기 |
| U7 지도·섬 시트·잠김 | `MapView`(임시), `IslandSheet`·섞어서·하늘 섬, `MethodTile`, 잠김 카드, `TableSheet` 옮김·넘침 수정, `LearnView`·`PracticeSheet`·`RecommendCard`·`Hints.nextRoadmapTable` 걷어 냄 | iOS → Android | 캡처 ↔ `03`, `03-vi`, `08`. 무료: 빈칸 추리 → 잠김 카드 → 보호자 부르기 → 3초 → 결제 화면 → 닫기 → 섬 시트(iOS). 19단 섬 시트 넘침 0 |
| U8 홈 | `HomeView`, 미션 구슬, 말풍선, 게임 문, 옷걸이, `DailyGoalSheet` 걷어 냄 | iOS → Android | 캡처 ↔ `01`, `01-night`, `01-vi`. 말풍선 → 지도 탭 + 그 단 섬 시트, 구슬 → 게임 시작, `GUGU_PLAY_MISSIONS=ready` 에서 상자 열기 → 코인 +10 |
| U9 체험 끝·결제 확인 | `TrialEndPanel`·`TrialNotice` 문구, 게임 화면 지역 흐름, 결제 직전 출생연도, `PremiumStore.paywallOpen` 계열 삭제 | iOS → Android | 무료로 게임 10문제 → 보호자 부르기 → 3초 → 결제 화면 → 구매 → 출생연도 → StoreKit 시험 결제(`Gugu.storekit`)·Play 시험 결제 |
| U10 용어 정리 | 4-4, 4-2 업적 설명, glossary | iOS → Android | `catalog.py check`, `android.py check`, 게임 결과 창·옷장·일시정지 창 캡처에 "별"(화폐)이 남지 않음 |
| U11 마무리 | 6절 전체, `redesign-pending.json` 비었는지, 사용자 규칙대로 verifier | 둘 | 6절 기준 전부 |

### 5-5. 문구 의존, 병렬, 다른 작업과의 순서

- **iOS → Android 문구 의존**: U4~U10 의 Android 단위는 같은 번호 iOS 단위의 카탈로그 반영(`sync → apply → check`)이 끝나야 시작한다. Android 는 iOS 를 한 단위 늦게 따라간다.
- **병렬로 되는 것**: U1~U3 은 문구가 없어 iOS·Android 를 동시에 한다. 그림 넣기(U3 일부)는 그림이 나오는 대로 따로 한다. U4 이후는 iOS 레인(U k+1)과 Android 레인(U k)이 동시에 돈다.
- **병렬로 안 되는 것**: 한 플랫폼 안에서는 한 번에 한 에이전트. iOS 는 `Localizable.xcstrings` 와 `project.pbxproj`(xcodegen), Android 는 `android-keys.json`·`strings.xml` 을 통째로 다시 쓰기 때문이다.
- **지금 돌고 있는 에이전트와의 순서**: 미션(ios-missions, android-missions)은 `DailyMissionsCard`, `DailyMissions`, `PlayRewardsStore`, `GameResultPanel`, `MinigameTrial` 과 카탈로그를, 생동감 작업은 `Motion`, 계단·몬스터 화면, `WardrobeView` 를 고친다. U1~U3 은 이 파일들을 건드리지 않아 지금 시작해도 된다. 문구를 쓰는 U4 이후, 그리고 그 파일들을 고치는 U5(옷장)·U8(미션 판)·U9(체험 창·게임 화면)는 두 작업이 끝났다고 보고한 뒤 시작한다.

### 5-6. 2단계 (섬 지도)

- `MapView`(iOS)·`MapScreen`(Android) 본문만 바꾼다. 섬 8개 좌표는 화면 폭·높이 비율로 잡고, 점선 길(`Path`), 오늘의 섬에 구구, 먹구름(`tablesWithWrong`), 무지개 = 섞어서, 열기구 = 하늘 지도(같은 구조로 10~19), 밤 그림.
- 화면 읽기 순서는 2→9 로 직접 지정한다. SE·iPad 가로·큰 글꼴 배치를 캡처로 확인한다.
- 데이터(`TodayIsland`, `IslandPick`, 섬 시트)는 1단계 것 그대로 쓴다.
- 캡처 비교 대상: `02-map.png`, `02-map-night.png`, `02-map-vi.png`.

---

## 6. 성공 기준

### 6-1. 테스트 (두 플랫폼 같은 내용)

- 날짜 기록: 세션·게임·부분 기록 답이 `a` 단별로 쌓임, 같은 날 두 판 합산, 날짜 중복 병합에 `tables` 포함, `tables` 없는 구버전 JSON 읽기, 왕복 보존.
- `TableSkills`: 14일 경계(오늘 포함 14일째 포함, 15일째 제외), `tables` 없는 날 무시, 상태 경계값(9문제 → 연습 중, 10문제 90% → 잘해요, 89% → 연습 중, 70% → 연습 중, 69% → 약해요, 0문제 → 기록 없음), 약한 순 정렬.
- `GuardianReport`: 2~9단 항상, 10~19단은 기록 있을 때만, 자주 틀린 문제 14일·최대 5개·정렬, 한마디 5경우.
- 한마디 문장: ko·en·ja 문장(기존 `parentTip` 테스트 자리, Android `CoreLocalizationTests`).
- 이번 주: 월요일·일요일인 날의 7일, 미래 날 0, 지난주 같은 요일까지 비교.
- `TodayIsland`: 기존 `nextRoadmapTable` 기대값 전부 유지(기록 없을 때), 약한 단 우선, 잘해요 단 제외, 전부 별 3개 → 섞어서.
- `IslandPlan.recommended`, `sample`, `MissionBoard.game(for:)`(세 번째 미션 규칙), `tablesWithWrong`.
- `GuardianFlow.reduce`: 2-2 표의 모든 줄 + "그 밖의 조합은 그대로" + 300초 경계(299 유지, 300 잠금).
- `NavTarget.from`: 6개 이동 × 이용권 유무. Android `PushStoreTests` 의 탭 기대값(`AppTab.PROFILE`)을 새 대상으로.
- `Theme.initial`: 없음 → 낮, `dark` → 밤, `system`, `light`, 손상 → 낮.
- 잠김 카드 한국어 조사: 빈칸 추리는, OX 퀴즈는, 60초 챌린지는, 서바이벌은.
- 기존 테스트 전부 통과. 개수는 U0 기준값보다 늘어야 한다.

### 6-2. i18n 게이트

`catalog.py sync`·`lint`·`check` 통과(6개 언어 빈 값 없음), `android.py check` 통과, `tools/i18n/android-only.d/redesign-pending.json` 비어 있음.

### 6-3. 화면 캡처 비교

도구(U3 에서 만듦):

- 디버그 전용 훅. iOS `SIMCTL_CHILD_GUGU_*`, Android `adb shell setprop debug.gugu.*`. 출시 빌드에는 없다.
  - `GUGU_SEED=v1`: V1 시제품 숫자 그대로(Lv.8 38/305, 연속 3일, 코인 97, 2단 별 3·3단 별 2·4단 별 1, 도감 5/8, 배지 7/17, 헷갈린 문제 11개(7·8단), 14일 단별 기록 2단 98%·3단 92%·4단 85%·5단 88%·6단 71%·7단 58%·8단 62%·9단 없음, 이번 주 월 32·화 45, 오늘 = 2026-11-24 화요일).
  - `GUGU_SEED=max`: 가장 긴 내용. Lv.99, 코인 99999, 연속 365일, 최고 기록 999, 곱하는 수 19, 10~19단 모두 기록, 헷갈린 문제에 19×19, 약한 단 18·19단, 도감·배지 전부.
  - `GUGU_SCREEN=home|map|island:5|island:19|island:mixed|sky|treasure|gate|gate:62|report|settings|locked:missing|trialEnd`: 그 화면을 바로 띄움.
  - 기존 `GUGU_PREMIUM`(무료/이용권), `GUGU_PLAY_MISSIONS` 를 같이 쓴다. 테마는 테마 키를 미리 넣는다.
- `tools/redesign/shoot.sh`: 위 조합을 iOS 시뮬(iPhone 17e, V1 프레임과 같은 390pt 폭)과 Android 에뮬(Pixel 9 Pro)에서 찍는다.
- `tools/redesign/compare.py`: V1 그림과 앱 캡처를 같은 높이로 나란히 붙여 `.claude/scratch/redesign/compare/` 에 저장한다.

나란히 비교할 짝(1단계):

| V1 | 앱 캡처 조건 |
|---|---|
| `01-home.png` | seed v1, 이용권, 낮, ko |
| `01-home-night.png` | seed v1, 밤 |
| `01-home-vi.png` | seed v1, vi |
| `03-island-sheet.png`, `03-island-sheet-vi.png` | island:5, ko / vi |
| `04-treasure.png` | treasure |
| `05-guardian-gate.png` | gate:62 |
| `06-guardian-report.png` | report |
| `07-guardian-settings.png` | settings |
| `08-locked-kid.png` | 무료, locked:missing |

판정: 블록 순서, 누를 곳의 수와 자리, 색 역할(sun 은 시작하는 것에만), 그림 자리가 V1 과 같다. 픽셀 일치는 요구하지 않는다(그림 해상도·글꼴 렌더가 다르다). 다른 점은 비교 이미지 옆 메모에 적고 이 계획의 결정(예: 배우기 칩 1개, "기록 없음")으로 설명되는지 확인한다.

### 6-4. 넘침 점검 (대표님 지시)

아래 조건마다 1단계 모든 화면(홈, 지도, 섬 시트 5단·19단·섞어서·하늘 섬, 표 시트 19단, 보물, 문 카드, 잠김 카드, 리포트, 설정·계정, 체험 끝 창, 탭 바)을 iOS·Android 로 찍고 잘림·넘침·겹침 **0건**이어야 한다.

1. 가장 긴 내용: `GUGU_SEED=max`(19×19=361 같은 세 자리 답, 19단 섬 시트, 코인 99999, 365일째).
2. 긴 언어: vi, hi(그리고 id).
3. 글꼴 크게: iOS 큰 텍스트 xxxLarge(약 130%), Android 글꼴 크기 1.3.
4. 1과 2와 3을 함께(vi + max + 130%).
5. 좁은 화면: iPhone SE(3세대) 시뮬.

판정 방법: 캡처를 눈으로 보고, 화면 읽기 트리(iOS `snapshot_ui`, Android `uiautomator dump`)에서 글 요소의 사각형이 부모 상자를 벗어나지 않는지 확인한다. `no-shrink.sh` 0건도 같이 본다.

### 6-5. 실제 동작 확인 (시뮬·에뮬 실제 탭)

- 테마: 새로 설치 → 낮. 기존 다크 선택 사용자(키 `dark`) → 밤. 키 없는 기존 사용자 → 낮.
- 저장 이관: 구버전 저장본(`tables` 없음)을 넣고 실행 → 진행도 유지, 리포트 표 "기록 없음", 7단 차근차근 한 판 → 7단 행이 채워짐.
- 보호자 문: 3초 → 열림, 2초 떼기 → 닫힌 채, 밖 누르기 → 닫힘, 보호자 화면에서 백그라운드 6분 → 돌아오면 잠김, 1분 → 그대로.
- 위험 동작 세 개 모두 출생연도 없이는 실행 안 됨.
- 무료 잠김 흐름, 게임 체험 끝 흐름, 시험 결제.
- 알림 이동 6종.
- 화면 읽기: VoiceOver·TalkBack 로 홈 → 지도 → 섬 시트 → 시작, 열쇠 → 사용자 지정 동작으로 보호자 열기.
- 동작 줄이기 켬: 말풍선·상자·문이 멈춤, 3초 고리는 동작.
- iPad(11인치) 세로·가로: 읽기 폭 640, 장면 가운데.

---

## 7. 열린 결정

계획 안에서 정한 것은 모두 되돌릴 수 있다(상수·문구·배치). 대표님께 여쭐 것은 하나다.

1. **출시 묶음.** 이 작업을 지금 미커밋 2.9.0(게임 교체 + 미션) 위에서 같이 낼지, 2.9.0 을 먼저 커밋·출시하고 새 브랜치(2.10.0)에서 할지.
   - 추천: 2.9.0 먼저. 2.9.0 은 구현·테스트가 끝나 스토어 단계만 남았다(메모리 `games-swap-2.9`). 1단계는 같은 파일(`HomeView`, `RootView`, `MinigameTrial`, 카탈로그)을 크게 바꾸므로 섞으면 2.9.0 출시가 1단계 끝까지 밀리고, 문제가 생겨도 한쪽만 되돌릴 수 없다.
   - 커밋·브랜치는 대표님만 하시므로 에이전트가 정할 수 없다. 답을 받기 전에도 U1~U3(Core·토대)은 진행할 수 있다. 이 부분은 2.9.0 화면을 바꾸지 않는다(U3 의 테마 첫값만 화면에 보인다).

참고로 정해 둔 것(반대하실 때만 알려 주세요): 지도 탭 1단계 임시안 채택(1-3), "약해요" 기준 14일·10문제·70%/90%(2-4), 결제는 3초 문 + 구매 직전 출생연도 두 번(2-3), 고른 적 없는 사용자는 낮으로 전환(2-10), 세 번째 미션 구슬은 아직 안 끝낸 게임으로(2-7), 화폐 이름 코인·업적 이름 배지(4-4). 스토어 분류: App Store 는 교육 4+ 로 키즈 카테고리가 아니고, Play 는 대상 연령 6~8·9~12세라 가족 정책 대상이다. 이번 변경은 아이 화면에서 결제 권유를 빼고 결제 앞에 보호자 확인을 더하는 쪽이라 두 정책과 부딪히지 않는다고 판단했다. 정책 원문 대조는 하지 않았으므로 스토어 제출 때 심사 메모에 보호자 문을 적는다.
