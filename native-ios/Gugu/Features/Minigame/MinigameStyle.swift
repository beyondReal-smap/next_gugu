import SwiftUI

// 움직이며 놀기 미니게임의 이름·소개·아이콘·색 — 홈·학습 탭·단 시트가 같은 정의를 쓴다.
// (예전에는 홈과 학습 탭의 순서·문구가 달랐다)

extension Minigame {
    var title: String {
        switch self {
        case .pop: return String(localized: "구구 팡팡")
        case .basket: return String(localized: "구구 바구니")
        case .jump: return String(localized: "구구 점프")
        case .lane: return String(localized: "구구 레인")
        }
    }

    var desc: String {
        switch self {
        case .pop: return String(localized: "미션에 맞는 숫자 풍선만 팡!")
        case .basket: return String(localized: "좌우로 움직여 정답 열매를 쏙 받아요!")
        case .jump: return String(localized: "정답을 고르면 폴짝! 장애물을 넘어요.")
        case .lane: return String(localized: "길을 바꿔 피하고, 정답 길로 쏙!")
        }
    }

    var icon: String {
        switch self {
        case .pop: return "balloon.2.fill"
        case .basket: return "basket.fill"
        case .jump: return "figure.run"
        case .lane: return "rectangle.split.1x2.fill"
        }
    }

    var iconBg: Color {
        switch self {
        case .pop: return Color(hex: "#2b3a7a")
        case .basket: return Color(hex: "#794124")
        case .jump, .lane: return Color(hex: "#153f35")
        }
    }

    var iconFg: Color {
        switch self {
        case .pop: return Color(hex: "#ffd6e7")
        case .basket: return Color(hex: "#ffe7a3")
        case .jump, .lane: return Color(hex: "#dbef9e")
        }
    }

    var tint: Color {
        switch self {
        case .pop: return Color.gg.sky
        case .basket: return Color.gg.warning
        case .jump, .lane: return Color.gg.emerald
        }
    }

    /// 카드 배지 — 무료 사용자에게는 체험 범위, 이용권 사용자에게는 새 게임 표시만 (예전의 "새 모드" 배지는 없앴다)
    func badge(isPremium: Bool) -> String? {
        switch self {
        case .pop: return isPremium ? String(localized: "새 게임") : PopTrial.badge
        case .basket, .jump, .lane: return MinigameTrial.badge(isPremium: isPremium)
        }
    }
}
