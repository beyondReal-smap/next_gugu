import Foundation

// 보호자 흐름 — 아이 화면에서 보호자 구역(리포트·설정·결제)으로 가는 길 하나 (계획서 redesign-v1 2-2, 2-3, 2-9).
// 화면은 사건(GuardianEvent)만 보내고 다음 상태는 reduce 가 정한다. 3초 누르기 진행률은 화면 지역 값이고 흐름에는 끝났다는 사건만 온다.
// iOS 는 떠 있는 시트 위에 루트 겹침이 보이지 않아 같은 GuardianStep 을 쓰는 곳(탭 화면·섬 시트·게임 화면)마다 값을 하나씩 든다. 규칙은 reduce 하나.

/// 보호자 설정을 열면서 내려갈 곳
enum SettingsFocus: Equatable {
    case notifications
}

/// 보호자 문을 통과하면 열 화면
enum GuardianDestination: Equatable {
    case report
    case settings(focus: SettingsFocus?)
    /// 아이가 보호자를 부른 경우(잠김 카드·게임 체험 끝)와 결제 알림 이동
    case paywall
}

enum GuardianStep: Equatable {
    case idle
    /// "어른이랑 같이 열어요" 카드 — 잠긴 모드를 눌렀다
    case kidLocked(GameMode)
    /// 3초 꾹 누르기 카드
    case holding(GuardianDestination)
    /// 보호자 화면 또는 결제 화면
    case open(GuardianDestination)
}

enum GuardianEvent: Equatable {
    /// 탭 머리 열쇠
    case tapKey
    /// 잠긴 타일
    case tapLocked(GameMode)
    /// [보호자 부르기] (잠김 카드, 게임 체험 끝 창)
    case callGuardian
    /// 3초를 다 누름
    case holdCompleted
    /// 화면 읽기 사용자 대안 — 출생연도 확인 통과
    case birthYearPassed
    /// 알림 이동
    case route(GuardianDestination)
    /// 닫기·다음에 할래요·아이 화면으로·뒤로 가기
    case dismiss
    /// 앱으로 돌아옴
    case resumed(awaySeconds: Int)
}

enum GuardianFlow {
    /// 문 카드를 누르고 있어야 하는 시간
    static let holdSeconds = 3.0
    /// 앱을 이만큼(초) 이상 떠났다 오면 다시 잠근다. 보호자가 메일 앱에서 확인 코드를 보고 오거나 결제 창을 거치는 흐름이
    /// 백그라운드를 지나므로, 떠나는 즉시 잠그면 입력이 날아간다
    static let relockAfterAwaySeconds = 300

    static func reduce(_ step: GuardianStep, _ event: GuardianEvent) -> GuardianStep {
        switch (step, event) {
        case (.idle, .tapKey):
            return .holding(.report)
        case (.idle, .tapLocked(let mode)):
            return .kidLocked(mode)
        case (.idle, .callGuardian), (.kidLocked, .callGuardian):
            return .holding(.paywall)
        case (.idle, .route(let destination)):
            return .holding(destination)
        case (.holding(let destination), .holdCompleted), (.holding(let destination), .birthYearPassed):
            return .open(destination)
        case (.open, .route(let destination)):
            // 이미 열려 있으면 다시 묻지 않는다
            return .open(destination)
        case (.kidLocked, .dismiss), (.holding, .dismiss), (.open, .dismiss):
            return .idle
        case (.kidLocked, .resumed(let away)), (.holding, .resumed(let away)), (.open, .resumed(let away)):
            return away >= relockAfterAwaySeconds ? .idle : step
        default:
            // 빠른 두 번 누르기처럼 정상 입력이라 무시한다
            return step
        }
    }
}

/// 보호자 구역 안에서도 실행 직전에 출생연도 확인(GuardianGate.isValidBirthYear)을 한 번 더 거치는 동작.
/// 통과 값은 기억하지 않고 동작마다 묻는다
enum GuardedAction: Equatable {
    case purchase
    case resetProgress
    case deleteAccount
}

/// 알림 이동(PushRoute)이 갈 곳. 보호자 대상은 GuardianEvent.route 로 넘어가 3초 문부터 시작한다 — 알림은 아이가 누를 수도 있다
enum NavTarget: Equatable {
    case tab(AppTab)
    case guardian(GuardianDestination)

    /// 알림 이동 문자열은 서버 계약이라 그대로 두고 앱 안 대응만 바꾼다(계획서 2-9)
    static func from(_ route: PushRoute, isPremium: Bool) -> NavTarget {
        switch route {
        case .home: return .tab(.home)
        case .learn: return .tab(.map)
        case .profile, .profileReport: return .guardian(.report)
        case .settingsNotifications: return .guardian(.settings(focus: .notifications))
        case .paywall: return isPremium ? .tab(.home) : .guardian(.paywall)
        }
    }
}
