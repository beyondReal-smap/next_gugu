package site.smap.gugudan.core

import kotlin.random.Random

// 출제 엔진 — 오답 가중 + 최근 출제 회피 (Problems.swift 이식)

// OX 퀴즈 — 화면에 표시할 식 (맞는 식 또는 그럴듯한 오답)
data class Statement(val shown: Int, val isTrue: Boolean)

object Problems {
    const val MIN_TABLE = 2
    const val MAX_TABLE = 9
    const val MIN_B = 1
    const val MAX_B = 9

    fun key(a: Int, b: Int): String = "${a}x${b}"

    /** 한 단(table)의 후보 식 */
    fun candidates(table: Int): List<Problem> = (MIN_B..MAX_B).map { Problem(table, it) }

    /** 혼합(전 범위) 후보 */
    fun candidatesAll(): List<Problem> =
        (MIN_TABLE..MAX_TABLE).flatMap { a -> (MIN_B..MAX_B).map { b -> Problem(a, b) } }

    /**
     * 가중 무작위 선택. 오답 풀 가중치 ↑, 최근 출제는 가중치 ↓.
     * random: 0..<1 균등 난수 (테스트 주입 가능)
     */
    fun pick(
        table: Int?,
        wrongPool: Map<String, Int>,
        recentKeys: List<String>,
        random: () -> Double = { Random.nextDouble() },
    ): Problem {
        val pool = if (table == null) candidatesAll() else candidates(table)
        val recent = recentKeys.takeLast(3).toSet()

        val weighted = pool.map { p ->
            val k = key(p.a, p.b)
            var w = 1 + (wrongPool[k] ?: 0) * 2.2   // 오답 가중
            if (k in recent) w *= 0.15              // 직전 출제 회피
            p to w
        }

        val total = weighted.sumOf { it.second }
        var r = random() * total
        for ((p, w) in weighted) {
            r -= w
            if (r <= 0) return p
        }
        return weighted.last().first
    }

    /** 오답 가중치가 가장 많이 쌓인 단. 없으면 null. */
    fun dominantWrongTable(wrongPool: Map<String, Int>): Int? {
        val byTable = mutableMapOf<Int, Int>()
        for ((key, weight) in wrongPool) {
            if (weight <= 0) continue
            val sep = key.indexOf('x')
            if (sep <= 0) continue
            val table = key.substring(0, sep).toIntOrNull() ?: continue
            if (table !in MIN_TABLE..MAX_TABLE) continue
            byTable[table] = (byTable[table] ?: 0) + weight
        }
        return byTable.entries.sortedBy { it.key }.maxByOrNull { it.value }?.key
    }

    /** 오답 풀 갱신 */
    fun updateWrongPool(pool: Map<String, Int>, a: Int, b: Int, correct: Boolean): Map<String, Int> {
        val k = key(a, b)
        val cur = pool[k] ?: 0
        return if (correct) {
            val v = maxOf(0, cur - 1)
            if (v == 0) pool - k else pool + (k to v)
        } else {
            pool + (k to minOf(6, cur + 2))
        }
    }

    fun makeStatement(p: Problem, random: () -> Double = { Random.nextDouble() }): Statement {
        val answer = p.a * p.b
        if (random() < 0.5) return Statement(answer, true)
        // 흔히 헷갈리는 "한 끗 차이" 오답 후보
        val candidates = listOf(p.a * (p.b + 1), p.a * (p.b - 1), (p.a + 1) * p.b, (p.a - 1) * p.b)
            .filter { it > 0 && it != answer }
        val idx = (random() * candidates.size).toInt()
        return Statement(candidates[minOf(idx, candidates.size - 1)], false)
    }
}
