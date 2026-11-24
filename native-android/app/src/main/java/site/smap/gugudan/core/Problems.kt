package site.smap.gugudan.core

import kotlin.random.Random

// 출제 엔진 — 오답 가중 + 최근 출제 회피 (Problems.swift 이식)

// OX 퀴즈 — 화면에 표시할 식 (맞는 식 또는 그럴듯한 오답)
data class Statement(val shown: Int, val isTrue: Boolean)

object Problems {
    const val MIN_TABLE = 2
    const val MAX_TABLE = 19
    const val MIN_B = 1
    /** 기록·검증 계약의 곱하는 수 상한 — 학습 기록 키(2~19단 × 1~19)와 낭독 범위. 설정과 무관하게 고정 */
    const val SUPPORTED_MAX_B = 19
    /** 설정 「곱하는 수」에서 고를 수 있는 상한 */
    val MAX_B_OPTIONS = listOf(9, 12, 15, 19)
    const val DEFAULT_MAX_B = 9

    /**
     * 지금 출제·학습표·게임에 쓰는 곱하는 수 상한 (9면 19단도 19 × 9까지).
     * PrefsStore 가 저장된 설정을 읽어 바꾼다 — Haptics.enabled 와 같은 방식
     */
    var maxB: Int = DEFAULT_MAX_B
        set(value) {
            require(value in MAX_B_OPTIONS) { "곱하는 수 상한 범위 밖: $value" }
            field = value
        }

    fun key(a: Int, b: Int): String = "${a}x${b}"

    /** 한 단(table)의 후보 식 */
    fun candidates(table: Int): List<Problem> = (MIN_B..maxB).map { Problem(table, it) }

    /** 혼합(전 범위) 후보 */
    fun candidatesAll(): List<Problem> =
        (MIN_TABLE..MAX_TABLE).flatMap { a -> (MIN_B..maxB).map { b -> Problem(a, b) } }

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

    /** 오답 가중치가 남은 문제가 있는 단 집합 (지도 먹구름 표시용, 계획서 2-7). 가중치 0 이하·깨진 키·범위 밖은 무시. */
    fun tablesWithWrong(wrongPool: Map<String, Int>): Set<Int> {
        val tables = mutableSetOf<Int>()
        for ((key, weight) in wrongPool) {
            if (weight <= 0) continue
            val problem = parseKey(key) ?: continue
            tables.add(problem.a)
        }
        return tables
    }

    /**
     * "7x8" → Problem(7, 8). 형식이 깨졌거나 기록 계약 범위(2~19단 × 1~19) 밖이면 null.
     * 설정 상한(maxB)으로 자르지 않는다 — 상한을 낮춰도 이미 쌓인 기록은 그대로 복원·표시한다
     */
    fun parseKey(key: String): Problem? {
        val parts = key.split('x')
        if (parts.size != 2) return null
        val a = parts[0].toIntOrNull() ?: return null
        val b = parts[1].toIntOrNull() ?: return null
        if (a !in MIN_TABLE..MAX_TABLE || b !in MIN_B..SUPPORTED_MAX_B) return null
        return Problem(a, b)
    }

    /**
     * 취약 문제 복습 출제 큐 — 오답 가중치가 큰 문제부터 최대 count 개를 고르고(동률은 무작위),
     * 고른 뒤 순서를 섞어 가중치 큰 문제가 늘 맨 앞에 몰리지 않게 한다.
     * 가중치 0 이하·형식이 깨진 키·설정 상한(maxB)을 넘는 곱하는 수는 건너뛴다.
     */
    fun reviewQueue(
        wrongPool: Map<String, Int>,
        count: Int,
        random: () -> Double = { Random.nextDouble() },
    ): List<Problem> {
        data class Ranked(val p: Problem, val w: Int, val tie: Double)
        val ranked = wrongPool.entries
            .sortedBy { it.key }   // 맵 순회 순서에 기대지 않는다 (주입 난수 재현성)
            .mapNotNull { e ->
                val p = parseKey(e.key)
                if (e.value <= 0 || p == null || p.b > maxB) null else Ranked(p, e.value, random())
            }
            .sortedWith(compareByDescending<Ranked> { it.w }.thenBy { it.tie })
        val picked = ranked.take(maxOf(0, count)).map { it.p }.toMutableList()
        // Fisher–Yates
        for (i in picked.size - 1 downTo 1) {
            val j = minOf(i, (random() * (i + 1)).toInt())
            val t = picked[i]; picked[i] = picked[j]; picked[j] = t
        }
        return picked
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

    /** 흔히 헷갈리는 "한 끗 차이" 오답 후보 (순서 유지, 중복 가능) — 음성 파일 목록도 이 규칙을 따른다 */
    fun statementCandidates(p: Problem): List<Int> {
        val answer = p.a * p.b
        return listOf(p.a * (p.b + 1), p.a * (p.b - 1), (p.a + 1) * p.b, (p.a - 1) * p.b)
            .filter { it > 0 && it != answer }
    }

    fun makeStatement(p: Problem, random: () -> Double = { Random.nextDouble() }): Statement {
        val answer = p.a * p.b
        if (random() < 0.5) return Statement(answer, true)
        val candidates = statementCandidates(p)
        val idx = (random() * candidates.size).toInt()
        return Statement(candidates[minOf(idx, candidates.size - 1)], false)
    }
}
