package site.smap.gugudan.core.games

import site.smap.gugudan.core.AnswerRecord
import site.smap.gugudan.core.GivenAnswer
import site.smap.gugudan.core.Problem
import site.smap.gugudan.core.Problems
import kotlin.math.roundToInt

// 채점 1회의 기록 (A·B 공통, Unity 시험판 Core/AttemptRecord.cs 이식, iOS AttemptRecord.swift 와 1:1)
// 학습 기록 연결: answerRecord 로 앱의 AnswerRecord(a, b, correct, ms, given) 로 바꿔 commitSession 에 넘긴다.

data class AttemptRecord(
    /** 이 판에서 몇 번째 채점인지 (0부터) */
    val id: Int,
    /** 채점한 문제(StairsQuestion·MonsterQuestion)의 id */
    val questionId: Int,
    /** A: 단 × 칸, B: 몬스터의 목표 식 */
    val problem: Problem,
    /** A: 고른 발판 숫자, B: 고른 두 카드의 곱 */
    val given: Int,
    /** 채점 때 판 시계(RoundClock.elapsed, 초) */
    val elapsed: Double,
    /** 문제가 나온 뒤 채점까지 걸린 시간(초) — 첫 동작 전 대기 포함, 쉬는 동안(tick 없음) 제외 */
    val responseSec: Double,
    /** B: 고른 카드 값(고른 순서대로). A 는 null */
    val pickA: Int? = null,
    val pickB: Int? = null,
) {
    init {
        require(responseSec >= 0) { "응답 시간은 0 이상이어야 함: $responseSec" }
        require((pickA == null) == (pickB == null)) { "카드는 두 장을 함께 기록해야 함" }
    }

    /** given 이 problem 의 곱과 같은지 — B 는 다른 카드 조합이라도 곱이 같으면 정답 */
    val correct: Boolean = given == problem.a * problem.b

    /** 응답 시간(ms) — 학습 기록용, 반올림 (responseSec ≥ 0 이라 Swift rounded() 와 같은 값) */
    val ms: Int get() = (responseSec * 1000).roundToInt()

    /** 앱 학습 기록 형식. given 은 아이가 낸 수(A: 고른 발판 숫자, B: 고른 두 카드의 곱) */
    val answerRecord: AnswerRecord
        get() = AnswerRecord(problem.a, problem.b, correct, ms, GivenAnswer.Number(given))

    companion object {
        /** 종료 화면에 보여 줄 틀린 식 하나: 가장 많이 틀린 식, 같으면 가장 최근. 틀린 것이 없으면 null */
        fun mostMissed(attempts: List<AttemptRecord>): AttemptRecord? {
            val counts = mutableMapOf<String, Int>()
            var best: AttemptRecord? = null
            var bestCount = 0
            for (attempt in attempts) {
                if (attempt.correct) continue
                val key = Problems.key(attempt.problem.a, attempt.problem.b)
                val count = (counts[key] ?: 0) + 1
                counts[key] = count
                if (count >= bestCount) {
                    best = attempt
                    bestCount = count
                }
            }
            return best
        }
    }
}
