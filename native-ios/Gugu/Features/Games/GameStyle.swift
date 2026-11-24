import SwiftUI

// 게임(구구 계단·구구 몬스터)의 이름·소개·썸네일·색·배지 — 홈 "게임" 묶음과 학습 탭 단 시트가 같은 정의를 쓴다.
// (예전에는 홈과 학습 탭의 순서·문구가 달랐다)

extension Minigame {
    var title: String {
        switch self {
        case .stairs: return String(localized: "구구 계단")
        case .monster: return String(localized: "구구 몬스터")
        }
    }

    var desc: String {
        switch self {
        case .stairs: return String(localized: "정답 발판으로 점프! 먹구름보다 높이 올라가요.")
        case .monster: return String(localized: "곱해서 몬스터 숫자가 되는 카드 2장을 골라요!")
        }
    }

    /// 카드 썸네일 — 게임 그림(Assets.xcassets/Games). 계단은 점프하는 구구, 몬스터는 처음 만나는 초록 몬스터
    var thumbnail: String {
        switch self {
        case .stairs: return GameArt.GuguPose.jump.file
        case .monster: return GameArt.monsterFile(.green)
        }
    }

    /// 썸네일 받침 색 — 각 게임 배경(하늘·숲)의 첫인상
    var iconBg: Color {
        switch self {
        case .stairs: return Color(hex: "#CFE9FF")
        case .monster: return Color(hex: "#1E3A2C")
        }
    }

    var tint: Color {
        switch self {
        case .stairs: return Color.gg.sky
        case .monster: return Color.gg.emerald
        }
    }

    /// 카드 배지 — 무료 사용자에게는 체험 범위("N문제 체험"), 이용권 사용자에게는 새 게임 표시
    func badge(isPremium: Bool) -> String {
        MinigameTrial.badge(isPremium: isPremium) ?? String(localized: "새 게임")
    }
}
