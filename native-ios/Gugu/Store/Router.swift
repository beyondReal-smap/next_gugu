import Foundation
import Observation

// 탭 네비게이션 — 홈에서 "학습" 탭으로 전환 등 프로그래매틱 이동용

/// 탭 — 재디자인 V1 의 최종 이름(홈·지도·보물). 화면은 단계마다 바뀌어도(지금 지도 = 학습 화면, 보물 = 프로필 화면) 이름은 한 번만 바꾼다.
/// 저장하지 않는 값이라 이관이 없다
enum AppTab: Hashable {
    case home, map, treasure
}

/// 게임(구구 계단·구구 몬스터) — 홈·단 시트 어디서나 이 순서로 보여 준다
enum Minigame: String, CaseIterable, Identifiable {
    case stairs, monster
    var id: String { rawValue }

    /// 단 시트에서 고른 단으로 시작할 수 있는지 (nil = 섞어서는 둘 다 가능).
    /// 구구 계단은 2~19단 어디서나 시작한다. 구구 몬스터는 카드가 1~9 라 2~9단만 만들 수 있다(대표님 결정)
    func supports(table: Int?) -> Bool {
        guard let table else { return true }
        switch self {
        case .stairs: return StairsSession.firstTables.contains(table)
        case .monster: return MonsterSession.tables.contains(table)
        }
    }
}

/// 미니게임을 열 때 처음 골라 둘 단.
/// 단을 정하지 않은 진입(홈)과 '섞어서'(nil)를 구분해야 해서 Int? 하나로 두지 않는다 — 엔진 기본값이 게임마다 다르다
enum MinigameStart: Equatable {
    /// 단을 정하지 않음 — 각 엔진의 기본값 (두 게임 모두 2~9단 섞어서)
    case standard
    /// 단 시트에서 고른 단 (nil = 섞어서)
    case table(Int?)

    /// 엔진에 넘길 시작 단 — 단을 정하지 않았으면 그 게임의 기본값
    func table(defaultTable: Int?) -> Int? {
        if case .table(let table) = self { return table }
        return defaultTable
    }
}

/// 전체화면으로 띄울 미니게임 한 판 — 같은 게임을 다시 열어도 새 화면이 되도록 id 를 따로 둔다
struct MinigameLaunch: Identifiable, Equatable {
    let game: Minigame
    let start: MinigameStart
    let id = UUID()
}

@Observable
final class Router {
    var tab: AppTab = .home
    /// 게임(구구 계단·구구 몬스터) 전체화면 오버레이 — 홈·학습 탭 단 시트에서 진입
    var minigame: MinigameLaunch?
    /// 구구 꾸미기(옷장) 전체화면 — 홈 "게임" 묶음에서 진입
    var wardrobeOpen: Bool = false
    /// 설정 시트 (프로필 톱니바퀴·알림 딥링크 settings/notifications)
    var settingsOpen: Bool = false
    /// 설정 시트를 열면서 알림 섹션으로 내려갈지
    var settingsFocusNotifications: Bool = false
    /// 알림을 눌러 들어온 이동 — 온보딩·세션·게임·옷장이 끝난 뒤 RootView 가 적용하고 비운다
    var pendingRoute: PushRoute?

    /// 단 시트가 막아야 하는 조합(구구 몬스터 10단 이상)이 들어오면 화면 쪽 버그 — 엔진 precondition 보다 먼저 알린다
    func open(_ game: Minigame, start: MinigameStart = .standard) {
        if case .table(let table) = start {
            precondition(game.supports(table: table), "\(game) 은 \(table.map(String.init) ?? "섞어서")단으로 시작할 수 없음")
        }
        minigame = MinigameLaunch(game: game, start: start)
    }

    func openWardrobe() { wardrobeOpen = true }

    #if DEBUG
    /// 캡처용 시작 화면 — SIMCTL_CHILD_GUGU_SCREEN=home|map|treasure (계획서 redesign-v1 6-3).
    /// 섬 시트·보호자 문·리포트·설정·잠김 카드·체험 끝 값은 그 화면을 만드는 단위(U4~U9)에서 더한다. 모르는 값은 멈춘다
    init(environment env: [String: String] = ProcessInfo.processInfo.environment) {
        guard let screen = env["GUGU_SCREEN"] else { return }
        switch screen {
        case "home": tab = .home
        case "map": tab = .map
        case "treasure": tab = .treasure
        default: preconditionFailure("GUGU_SCREEN 은 지금 home|map|treasure 만 지원: \(screen)")
        }
    }
    #endif
}
