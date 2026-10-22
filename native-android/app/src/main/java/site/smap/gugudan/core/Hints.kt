package site.smap.gugudan.core

import site.smap.gugudan.R

// 학습 로드맵 순서 + 암산 힌트 (hints.ts / Core/Hints.swift 이식·확장)
// ROADMAP_TABLES 는 content/learning-path.json steps 순서의 읽기 전용 사본이다.

/** 문제 하나에 적용한 암산 전략 */
data class CoachHint(
    /** 전략 이름 — "10단에서 한 번 빼기" */
    val title: String,
    /** 정답을 뺀 풀이 — 푸는 도중에 보여 줘도 답이 드러나지 않는다 ("70 − 7 = ?") */
    val scaffold: String,
    /** 정답까지 쓴 풀이 — 표 보기처럼 답을 보여 줘도 되는 곳에서 쓴다 ("9 × 7 = 70 − 7 = 63") */
    val full: String,
)

object Hints {
    val ROADMAP_TABLES = listOf(2, 5, 3, 4, 6, 9, 7, 8)

    /** 다음에 배울 단 — 로드맵 순서에서 아직 별이 없는 첫 단.
     *  모든 단에 별이 있으면 별이 가장 적은 단(동률이면 로드맵 순), 전부 3개면 null. */
    fun nextRoadmapTable(mastery: Map<Int, TableMastery>): Int? {
        val stars = ROADMAP_TABLES.map { mastery[it]?.stars ?: 0 }
        val fewest = stars.minOrNull() ?: return null
        if (fewest >= 3) return null
        return ROADMAP_TABLES[stars.indexOf(fewest)]
    }

    /** a × b 에 맞는 암산 전략. 전략 이름·말 풀이는 [strings] 언어로, 식은 언어와 무관하게 같다.
     *  1·2를 곱하는 문제는 단과 상관없이 더 쉬운 방법(그대로/순서 바꿔 두 번 더하기)을 먼저 쓴다. */
    fun coach(strings: Strings, a: Int, b: Int): CoachHint {
        val p = a * b
        val head = "$a × $b"
        fun steps(title: String, work: String) = CoachHint(title, "$work = ?", "$head = $work = $p")

        if (b == 1) {
            return CoachHint(strings.get(R.string.times_1_stays_the_same),
                strings.get(R.string.any_number_times_1_is_just_that_number), "$head = $a")
        }
        if (b == 2 && a >= 3) return steps(strings.get(R.string.swap_the_order_and_add_it_twice), "$a + $a")

        return when (a) {
            2 -> steps(strings.get(R.string.add_it_twice), "$b + $b")
            3 -> steps(strings.get(R.string.the_2s_plus_one_more), "${2 * b} + $b")
            4 -> steps(strings.get(R.string.double_the_2s), "${2 * b} + ${2 * b}")
            5 -> CoachHint(
                strings.get(R.string.skip_count_by_5s),
                strings.get(R.string.count_by_5s_times, b),
                "$head → " + (1..b).joinToString(", ") { (5 * it).toString() },
            )
            6 -> steps(strings.get(R.string.the_5s_plus_one_more), "${5 * b} + $b")
            7 -> steps(strings.get(R.string.add_the_5s_and_the_2s), "${5 * b} + ${2 * b}")
            8 -> steps(strings.get(R.string.double_the_4s), "${4 * b} + ${4 * b}")
            9 -> steps(strings.get(R.string.times_10_take_one_away), "${10 * b} − $b")
            else -> CoachHint(strings.get(R.string.add_times, a, b), strings.get(R.string.try_adding_times, a, b), "$head = $p")
        }
    }

    /** 단 전체를 대표하는 비법 — 1·2 특수 규칙에 걸리지 않는 ×7 로 예시를 든다 */
    fun tableTip(strings: Strings, table: Int): CoachHint = coach(strings, table, 7)
}
