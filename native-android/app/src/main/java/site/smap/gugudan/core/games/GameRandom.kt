package site.smap.gugudan.core.games

import kotlin.math.min

// A·B 게임 규칙의 난수 도우미 (Unity 시험판 Core/IRandom.cs 의 Index·Shuffle 이식, iOS GameRandom.swift 와 1:1)
// 난수원은 앱 관례대로 `random: () -> Double`(0..<1)을 받는다 — 테스트는 SeededRandom.mulberry32 를 넣는다.
// 골든 벡터(iOS 와 같은 시드·같은 기대값)가 난수를 꺼내는 횟수·순서에 기대므로 바꾸지 말 것.

object GameRandom {
    /** [0, count) 정수 — floor(random() × count). 부동소수 반올림으로 count 가 나와도 count - 1 에서 멈춘다 */
    fun index(count: Int, random: () -> Double): Int {
        require(count > 0) { "count 는 1 이상이어야 함: $count" }
        val r = random()
        require(r >= 0 && r < 1) { "random 은 0..<1 이어야 함: $r" }
        return min((r * count).toInt(), count - 1)
    }

    /**
     * range 에서 하나(index 한 번). excluding 이 나오면 다시 뽑는다(excluding 이 범위 밖이면 첫 뽑기가 곧 결과).
     * 보물 뒤 다음 단(구구 계단)·×9 뒤 다음 단(구구 몬스터 순서대로)이 쓴다
     */
    fun pick(range: IntRange, excluding: Int?, random: () -> Double): Int {
        val count = range.last - range.first + 1
        require(count > 1 || excluding == null || excluding !in range) { "$range 에서 ${excluding ?: "-"} 말고 고를 수가 없음" }
        while (true) {
            val v = range.first + index(count, random)
            if (v != excluding) return v
        }
    }

    /** Fisher–Yates — 뒤에서부터 i 번째를 [0, i] 중 하나와 바꾼다 (원본 Shuffle 과 같은 순서) */
    fun <T> shuffle(list: MutableList<T>, random: () -> Double) {
        var i = list.size - 1
        while (i > 0) {
            val j = index(i + 1, random)
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
            i -= 1
        }
    }
}
