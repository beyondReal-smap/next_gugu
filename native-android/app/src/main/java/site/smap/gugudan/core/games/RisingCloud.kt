package site.smap.gugudan.core.games

import kotlin.math.max

// A 구구 계단의 시간 압박: 아래에서 계속 차오르는 먹구름 (Unity 시험판 Core/RisingCloud.cs 이식, iOS RisingCloud.swift 와 1:1)
// 높이 단위는 "계단 칸"(출발 발판 = 0, 한 칸 오르면 +1).
// - 구름 윗면(top)이 캐릭터가 서 있는 칸에 닿으면 잡힘 → 판 끝
// - 오를수록 빨라짐(5초/칸 → 최소 1.8초/칸). 정답 = 한 칸 벌기, 오답 = 정답 공개 0.6초 + 다시 푸는 시간만큼 잃기
// - 너무 멀어지면(MAX_GAP 칸 초과) 빠르게 따라옴 — 빨리 푸는 아이도 압박이 사라지지 않게
// 수치는 아이 관찰 전 초안이다(게임-고도화 docs/AB_PLAYTEST.md §2).

class RisingCloud {
    var top: Double = -START_GAP
        private set

    /** 서 있는 칸과 구름 윗면 사이 거리(칸). 0 이하 = 잡힘 */
    fun gap(standing: Int): Double = standing.toDouble() - top

    /** dt(초) 동안 차오르고, 이번에 잡혔으면 true. 잡히면 top 은 서 있는 칸에 멈춘다 */
    fun tick(dt: Double, standing: Int): Boolean {
        require(dt >= 0) { "dt 는 0 이상이어야 함: $dt" }
        check(top < standing.toDouble()) { "이미 잡힌 구름 (top $top ≥ standing $standing)" }
        var speed = 1.0 / secPerStep(standing)
        if (gap(standing) > MAX_GAP) speed *= CATCH_UP_FACTOR
        top += speed * dt
        if (top < standing.toDouble()) return false
        top = standing.toDouble()
        return true
    }

    /** 값 복사본 — Swift struct 와 같게, 세션이 내보낸 구름을 밖에서 바꿔도 세션 구름은 그대로 */
    fun copy(): RisingCloud = RisingCloud().also { it.top = top }

    companion object {
        /** 시작할 때 구름은 출발 발판 2칸 아래 */
        const val START_GAP: Double = 2.0
        const val MAX_GAP: Double = 3.0
        const val CATCH_UP_FACTOR: Double = 3.0
        const val START_SEC_PER_STEP: Double = 5.0
        const val MIN_SEC_PER_STEP: Double = 1.8
        /** 한 칸 오를 때마다 칸당 시간이 이만큼 줄어듦 */
        const val SPEEDUP_PER_STEP: Double = 0.07

        /** 구름이 한 칸 오르는 데 걸리는 시간(초) */
        fun secPerStep(standing: Int): Double =
            max(MIN_SEC_PER_STEP, START_SEC_PER_STEP - SPEEDUP_PER_STEP * max(0, standing).toDouble())
    }
}
