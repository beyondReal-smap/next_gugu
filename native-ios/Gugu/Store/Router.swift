import Foundation
import Observation

// 탭 네비게이션 — 홈에서 "학습" 탭으로 전환 등 프로그래매틱 이동용

enum AppTab: Hashable {
    case home, learn, profile
}

@Observable
final class Router {
    var tab: AppTab = .home
    /// 구구 점프 전체화면 오버레이 (홈·학습 카드에서 진입)
    var runnerOpen: Bool = false
    /// 구구 레인 전체화면 오버레이
    var laneOpen: Bool = false
    /// 구구 바구니 전체화면 오버레이
    var basketOpen: Bool = false
}
