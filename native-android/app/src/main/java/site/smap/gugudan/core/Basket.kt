package site.smap.gugudan.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// 구구 바구니 — 떨어지는 열매 중 정답을 바구니로 받는다 (lib/basket.ts / Core/Basket.swift 이식)
// 좌표계는 웹 SVG viewBox(360×380) 기준. 렌더러가 비율만 맞춰 그린다.

enum class BasketOutcome { CORRECT, WRONG, MISSED }

data class BasketState(
    val phase: RunnerPhase,
    val table: Int?,
    val question: RunnerQuestion,
    val recentKeys: List<String> = emptyList(),
    /** 바구니 가로 위치 */
    val x: Double = Basket.WIDTH / 2,
    val round: Int = 0,
    val elapsedMs: Double = 0.0,
    val feedbackMs: Double = 0.0,
    val outcome: BasketOutcome? = null,
    val caughtIndex: Int? = null,
    val score: Int = 0,
    val lives: Int = Basket.MAX_LIVES,
    val combo: Int = 0,
    val maxCombo: Int = 0,
)

object Basket {
    const val WIDTH = 360.0
    const val MIN_X = 36.0
    const val MAX_X = WIDTH - MIN_X
    const val FRUIT_START_Y = 58.0
    const val FRUIT_CATCH_Y = 304.0
    const val CORRECT_FEEDBACK_MS = 450.0
    const val WRONG_FEEDBACK_MS = 1500.0

    /** 열매 중심과 바구니 중심이 이만큼 안쪽이면 받은 것으로 본다 */
    const val CATCH_RADIUS = 40.0
    const val MAX_LIVES = 3

    fun create(
        table: Int?,
        phase: RunnerPhase = RunnerPhase.RUNNING,
        random: () -> Double = { Random.nextDouble() },
    ): BasketState = BasketState(
        phase = phase,
        table = table,
        // READY 상태의 문제는 화면 예시용 — 시작 시 새로 뽑는다
        question = if (phase == RunnerPhase.READY) RunnerQuestion(2, 3, listOf(4, 6, 8))
        else Runner.question(table, emptyList(), random),
    )

    fun level(score: Int): Int = score / 3 + 1

    fun fallDurationMs(score: Int): Double = max(2200.0, 3000.0 - (level(score) - 1) * 300.0)

    /** 열매 index 의 가로 위치 — 살짝 좌우로 흔들린다 */
    fun fruitX(index: Int, elapsedMs: Double): Double =
        64 + index * 116 + sin(elapsedMs / 750 + index * 2.0) * 12

    fun move(state: BasketState, x: Double): BasketState {
        if (state.phase != RunnerPhase.RUNNING || state.outcome != null || !x.isFinite()) return state
        return state.copy(x = max(MIN_X, min(MAX_X, x)))
    }

    fun advance(state: BasketState, dt: Double, random: () -> Double = { Random.nextDouble() }): BasketState {
        if (state.phase != RunnerPhase.RUNNING || !dt.isFinite() || dt <= 0) return state

        if (state.outcome != null) {
            val feedbackMs = max(0.0, state.feedbackMs - dt)
            if (feedbackMs > 0) return state.copy(feedbackMs = feedbackMs)
            if (state.lives == 0) return state.copy(feedbackMs = 0.0, phase = RunnerPhase.OVER)
            val recentKeys = (state.recentKeys + Problems.key(state.question.a, state.question.b)).takeLast(4)
            return state.copy(
                recentKeys = recentKeys,
                question = Runner.question(state.table, recentKeys, random),
                round = state.round + 1, elapsedMs = 0.0, feedbackMs = 0.0,
                outcome = null, caughtIndex = null,
            )
        }

        val duration = fallDurationMs(state.score)
        val elapsedMs = min(duration, state.elapsedMs + dt)
        if (elapsedMs < duration) return state.copy(elapsedMs = elapsedMs)

        // 열매가 바구니 높이를 지나는 순간 한 번만 판정한다
        val caughtIndex = state.question.choices.indices
            .firstOrNull { abs(fruitX(it, duration) - state.x) <= CATCH_RADIUS }
        val correct = caughtIndex != null && state.question.choices[caughtIndex] == state.question.answer
        val combo = if (correct) state.combo + 1 else 0
        return state.copy(
            elapsedMs = elapsedMs,
            caughtIndex = caughtIndex,
            outcome = if (correct) BasketOutcome.CORRECT
            else if (caughtIndex != null) BasketOutcome.WRONG else BasketOutcome.MISSED,
            feedbackMs = if (correct) CORRECT_FEEDBACK_MS else WRONG_FEEDBACK_MS,
            score = state.score + if (correct) 1 else 0,
            lives = state.lives - if (correct) 0 else 1,
            combo = combo, maxCombo = max(state.maxCombo, combo),
        )
    }
}
