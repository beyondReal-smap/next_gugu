package site.smap.gugudan.features.games.shared

import androidx.compose.ui.geometry.Offset

// A·B 공통 보상 표시 (Unity 시험판 RewardHud.cs 이식, iOS RewardHud.swift 대응): 위쪽 별 코인 배지 + 날아가는 코인 + 연속 정답 불꽃.
// 배지 숫자는 규칙의 별을 바로 보여 주지 않고, 코인이 배지에 닿을 때마다 하나씩 올린다("내가 모았다"는 느낌).
// 날아가는 코인이 없으면 규칙 값에 맞춰 둔다(한 번에 8개 넘게 받은 나머지 포함).

class RewardHudState {
    val coins = HudBadgeState()
    val flight = CoinFlightState()
    val combo = ComboState()
    /** 배지에 보이는 별 */
    var shown = 0
        private set
    /** 규칙의 별(이번 판에 받은 별) */
    var target = 0
        private set

    fun reset() {
        flight.clear()
        combo.reset()
        shown = 0
        target = 0
        coins.reset(0)
    }

    /** 정답으로 별을 받았을 때: from 에서 earned 개(최대 8개)가 배지(to)로 날아간다 */
    fun earn(from: Offset, to: Offset, earned: Int, total: Int, unit: Float) {
        target = total
        if (earned > 0) flight.launch(from, to, earned, unit)
    }

    fun setStreak(streak: Int) = combo.setStreak(streak)

    fun followCombo(point: Offset) = combo.follow(point)

    /** 매 프레임: 코인을 날리고(닿은 순번을 돌려줌 — Sound.coin 용), 날아가는 코인이 없으면 배지를 규칙 값에 맞춤 */
    fun update(dt: Double, badgeIcon: Offset): List<Int> {
        val arrived = flight.update(dt, badgeIcon)
        val counted = mutableListOf<Int>()
        for (index in arrived) {
            if (shown >= target) break
            shown += 1
            coins.setValue(shown)
            counted.add(index)
        }
        if (flight.inFlight == 0 && shown != target) {
            shown = target
            coins.setValue(shown)
        }
        coins.update(dt)
        combo.update(dt)
        return counted
    }
}
