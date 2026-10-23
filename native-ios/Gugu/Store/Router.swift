import Foundation
import Observation

// 탭 네비게이션 — 홈에서 "학습" 탭으로 전환 등 프로그래매틱 이동용

enum AppTab: Hashable {
    case home, learn, profile
}

/// 움직이며 놀기 미니게임 — 홈·학습 탭·단 시트 어디서나 이 순서로 보여 준다
enum Minigame: String, CaseIterable, Identifiable {
    case pop, basket, jump, lane
    var id: String { rawValue }
}

/// 미니게임을 열 때 처음 골라 둘 단.
/// 단을 정하지 않은 진입(홈)과 '섞어서'(nil)를 구분해야 해서 Int? 하나로 두지 않는다 — 엔진 기본값이 게임마다 다르다
enum MinigameStart: Equatable {
    /// 단을 정하지 않음 — 각 엔진의 기본값 (바구니 2단, 나머지 전체)
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
    /// 미니게임(팡팡·바구니·점프·레인) 전체화면 오버레이 — 홈·학습 탭·단 시트에서 진입
    var minigame: MinigameLaunch?
    /// 설정 시트 (프로필 톱니바퀴·알림 딥링크 settings/notifications)
    var settingsOpen: Bool = false
    /// 설정 시트를 열면서 알림 섹션으로 내려갈지
    var settingsFocusNotifications: Bool = false
    /// 알림을 눌러 들어온 이동 — 온보딩·세션·미니게임이 끝난 뒤 RootView 가 적용하고 비운다
    var pendingRoute: PushRoute?

    func open(_ game: Minigame, start: MinigameStart = .standard) {
        minigame = MinigameLaunch(game: game, start: start)
    }
}
