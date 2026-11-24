package site.smap.gugudan.core.games

import kotlin.math.max
import kotlin.math.min

// A 고도계(화면 왼쪽 세로 막대)의 눈금 (Unity 시험판 Core/Altimeter.cs 이식, iOS Altimeter.swift 와 1:1)
// 막대 위 끝이 몇 칸인지 정하고, 높이를 0~1 위치로 바꾼다.
// 처음부터 달나라(40칸)까지 보여 "저 위까지 갈 수 있다"를 알려 주고, 현재·최고 기록이 가까워지면 위 끝을 늘린다.

object Altimeter {
    /** 달나라 시작(40) 위 여유까지 */
    const val MIN_TOP = 45
    /** 현재·최고 기록 위로 남길 칸 */
    const val HEADROOM = 8
    /** 위 끝은 5칸 단위로만 바뀜(막대가 자주 출렁이지 않게) */
    const val STEP = 5

    fun top(climbed: Int, best: Int): Int {
        require(climbed >= 0) { "오른 칸은 0 이상이어야 함: $climbed" }
        require(best >= 0) { "최고 기록은 0 이상이어야 함: $best" }
        val need = max(climbed, best) + HEADROOM
        val top = (need + STEP - 1) / STEP * STEP
        return max(MIN_TOP, top)
    }

    /**
     * 높이(칸, 소수 가능 — 먹구름 윗면) → 막대 아래 0 ~ 위 1. 막대 밖은 끝에 붙인다.
     * top 은 화면이 부드럽게 따라가는 중간값일 수 있어 실수로 받는다
     */
    fun fraction(height: Double, top: Double): Double {
        require(top > 0) { "위 끝은 0보다 커야 함: $top" }
        return min(1.0, max(0.0, height / top))
    }
}
