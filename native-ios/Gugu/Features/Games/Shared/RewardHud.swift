import SwiftUI

// A·B 공통 보상 표시 (Unity 시험판 RewardHud.cs 이식): 위쪽 별 코인 배지 + 날아가는 코인 + 연속 정답 불꽃.
// 배지 숫자는 규칙의 별을 바로 보여 주지 않고, 코인이 배지에 닿을 때마다 하나씩 올린다("내가 모았다"는 느낌).
// 날아가는 코인이 없으면 규칙 값에 맞춰 둔다(한 번에 8개 넘게 받은 나머지 포함).

struct RewardHudState: Equatable {
    private(set) var coins = HudBadgeState()
    private(set) var flight = CoinFlightState()
    private(set) var combo = ComboState()
    /// 배지에 보이는 별
    private(set) var shown = 0
    /// 규칙의 별(이번 판에 받은 별)
    private(set) var target = 0

    mutating func reset() {
        flight.clear()
        combo.reset()
        shown = 0
        target = 0
        coins.reset(0)
    }

    /// 정답으로 별을 받았을 때: from 에서 earned 개(최대 8개)가 배지(to)로 날아간다
    mutating func earn(from: CGPoint, to: CGPoint, earned: Int, total: Int, unit: CGFloat) {
        target = total
        if earned > 0 { flight.launch(from: from, to: to, count: earned, unit: unit) }
    }

    mutating func setStreak(_ streak: Int) { combo.setStreak(streak) }

    mutating func followCombo(_ point: CGPoint) { combo.follow(point) }

    /// 매 프레임: 코인을 날리고(닿은 순번을 돌려줌 — 효과음용), 날아가는 코인이 없으면 배지를 규칙 값에 맞춤
    mutating func update(_ dt: Double, badgeIcon: CGPoint) -> [Int] {
        let arrived = flight.update(dt, target: badgeIcon)
        var counted: [Int] = []
        for index in arrived where shown < target {
            shown += 1
            coins.setValue(shown)
            counted.append(index)
        }
        if flight.inFlight == 0 && shown != target {
            shown = target
            coins.setValue(shown)
        }
        coins.update(dt)
        combo.update(dt)
        return counted
    }
}
