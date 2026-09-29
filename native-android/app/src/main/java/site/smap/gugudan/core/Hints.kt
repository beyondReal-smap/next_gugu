package site.smap.gugudan.core

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

    /** a × b 에 맞는 암산 전략.
     *  1·2를 곱하는 문제는 단과 상관없이 더 쉬운 방법(그대로/순서 바꿔 두 번 더하기)을 먼저 쓴다. */
    fun coach(a: Int, b: Int): CoachHint {
        val p = a * b
        val head = "$a × $b"
        fun steps(title: String, work: String) = CoachHint(title, "$work = ?", "$head = $work = $p")

        if (b == 1) {
            return CoachHint("1을 곱하면 그대로예요", "어떤 수에 1을 곱하면 그 수 그대로예요", "$head = $a")
        }
        if (b == 2 && a >= 3) return steps("순서를 바꿔 두 번 더하기", "$a + $a")

        return when (a) {
            2 -> steps("두 번 더하기", "$b + $b")
            3 -> steps("2단에 한 번 더 더하기", "${2 * b} + $b")
            4 -> steps("2단을 두 배로", "${2 * b} + ${2 * b}")
            5 -> CoachHint(
                "5씩 뛰어 세기",
                "5씩 ${b}번 세어 봐요",
                "$head → " + (1..b).joinToString(", ") { (5 * it).toString() },
            )
            6 -> steps("5단에 한 번 더 더하기", "${5 * b} + $b")
            7 -> steps("5단과 2단 더하기", "${5 * b} + ${2 * b}")
            8 -> steps("4단을 두 배로", "${4 * b} + ${4 * b}")
            9 -> steps("10단에서 한 번 빼기", "${10 * b} − $b")
            else -> CoachHint("${a}씩 ${b}번 더하기", "${a}씩 ${b}번 더해 봐요", "$head = $p")
        }
    }

    /** 단 전체를 대표하는 비법 — 1·2 특수 규칙에 걸리지 않는 ×7 로 예시를 든다 */
    fun tableTip(table: Int): CoachHint = coach(table, 7)
}
