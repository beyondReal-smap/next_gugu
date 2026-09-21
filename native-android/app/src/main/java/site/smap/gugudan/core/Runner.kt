package site.smap.gugudan.core

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// 구구 점프 — 달리기 미니게임 순수 로직 (lib/runner.ts / Core/Runner.swift 이식)
// 좌표계는 웹 SVG viewBox(720×260) 기준을 그대로 쓴다. 렌더러가 비율만 맞춰 그린다.

enum class RunnerPhase { READY, RUNNING, PAUSED, OVER }
enum class RunnerOutcome { CORRECT, WRONG, MISSED }

data class RunnerQuestion(val a: Int, val b: Int, val choices: List<Int>) {
    val answer: Int get() = a * b
}

data class RunnerState(
    val phase: RunnerPhase,
    val table: Int?,              // null = 전체 구구단
    val question: RunnerQuestion,
    val recentKeys: List<String> = emptyList(),
    val round: Int = 0,
    val score: Int = 0,
    val lives: Int = Runner.MAX_LIVES,
    val combo: Int = 0,
    val maxCombo: Int = 0,
    val distance: Double = 0.0,
    val obstacleX: Double = Runner.OBSTACLE_START,
    val outcome: RunnerOutcome? = null,
    val given: Int? = null,
    val hitMs: Double = 0.0,
)

object Runner {
    const val OBSTACLE_START = 690.0
    const val COLLISION_X = 154.0
    const val JUMP_START = 280.0
    const val JUMP_END = 30.0

    /** 피격 연출/정답 공개 유지 시간 */
    const val HIT_DURATION_MS = 1400.0
    const val MAX_LIVES = 3

    /** 장애물 3개를 넘을 때마다 한 단계 빨라진다 */
    const val SCORE_PER_LEVEL = 3

    /** 정답 뒤 장애물을 넘어가는 속도(px/ms) */
    private const val CLEAR_SPEED = 0.45

    fun shuffled(values: List<Int>, random: () -> Double = { Random.nextDouble() }): List<Int> {
        val result = values.toMutableList()
        var i = result.size - 1
        while (i > 0) {
            val j = min((random() * (i + 1)).toInt(), i)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
            i -= 1
        }
        return result
    }

    /** 정답 1개 + 그럴듯한 오답 2개를 섞은 보기 */
    fun question(
        table: Int?,
        recentKeys: List<String>,
        random: () -> Double = { Random.nextDouble() },
    ): RunnerQuestion {
        val problem = Problems.pick(table, emptyMap(), recentKeys, random)
        val answer = problem.a * problem.b
        val alternatives = listOf(
            answer - problem.a, answer + problem.a, answer - problem.b,
            answer + problem.b, answer - 1, answer + 1,
        ).distinct().filter { it in 1..81 && it != answer }
        val picked = shuffled(alternatives, random).take(2)
        return RunnerQuestion(problem.a, problem.b, shuffled(listOf(answer) + picked, random))
    }

    fun create(
        table: Int?,
        phase: RunnerPhase = RunnerPhase.RUNNING,
        random: () -> Double = { Random.nextDouble() },
    ): RunnerState = RunnerState(
        phase = phase,
        table = table,
        // READY 상태의 문제는 화면 예시용 — 시작 시 새로 뽑는다
        question = if (phase == RunnerPhase.READY) RunnerQuestion(2, 3, listOf(4, 6, 8))
        else question(table, emptyList(), random),
    )

    fun level(score: Int): Int = score / SCORE_PER_LEVEL

    /** 정답 제한 시간 — 한 단계 오를 때마다 500ms 짧아지고 2.8초가 하한 */
    fun answerWindowMs(score: Int): Double = max(2800.0, 5000.0 - level(score) * 500.0)

    /** 방금 넘은 장애물로 속도 단계가 올랐는지 (다음 장애물이 막 나온 순간) */
    fun leveledUp(state: RunnerState): Boolean =
        state.combo > 0 && state.outcome == null && state.score > 0 && state.score % SCORE_PER_LEVEL == 0

    fun answer(state: RunnerState, choice: Int): RunnerState {
        if (state.phase != RunnerPhase.RUNNING || state.outcome != null || choice !in state.question.choices) return state
        return state.copy(
            given = choice,
            outcome = if (choice == state.question.answer) RunnerOutcome.CORRECT else RunnerOutcome.WRONG,
        )
    }

    private fun nextObstacle(state: RunnerState, random: () -> Double): RunnerState {
        val recentKeys = (state.recentKeys + Problems.key(state.question.a, state.question.b)).takeLast(3)
        return state.copy(
            recentKeys = recentKeys,
            question = question(state.table, recentKeys, random),
            round = state.round + 1,
            obstacleX = OBSTACLE_START,
            outcome = null,
            given = null,
            hitMs = 0.0,
        )
    }

    /** dt(ms) 만큼 진행. 피격 연출 중이면 연출 시간만 흘리고, 통과하면 점수·콤보를 올린다. */
    fun advance(state: RunnerState, dt: Double, random: () -> Double = { Random.nextDouble() }): RunnerState {
        if (state.phase != RunnerPhase.RUNNING || !dt.isFinite() || dt <= 0) return state

        if (state.hitMs > 0) {
            val hitMs = max(0.0, state.hitMs - dt)
            if (hitMs > 0) return state.copy(hitMs = hitMs)
            return if (state.lives == 0) state.copy(hitMs = 0.0, phase = RunnerPhase.OVER)
            else nextObstacle(state, random)
        }

        // 정답을 고르기 전에는 제한 시간에 맞춘 속도, 정답 후에는 느리게 흘려보낸다
        val speed = if (state.outcome == null) (OBSTACLE_START - COLLISION_X) / answerWindowMs(state.score) else CLEAR_SPEED
        val movement = speed * dt
        val next = state.copy(
            obstacleX = state.obstacleX - movement,
            distance = state.distance + movement / 20,
        )

        if (next.obstacleX <= COLLISION_X && next.outcome != RunnerOutcome.CORRECT) {
            return next.copy(
                obstacleX = COLLISION_X,
                outcome = next.outcome ?: RunnerOutcome.MISSED,
                lives = next.lives - 1,
                combo = 0,
                hitMs = HIT_DURATION_MS,
            )
        }
        if (next.obstacleX < -40) {
            val combo = next.combo + 1
            return nextObstacle(
                next.copy(score = next.score + 1, combo = combo, maxCombo = max(next.maxCombo, combo)),
                random,
            )
        }
        return next
    }

    /** 장애물 위치에 점프 높이를 연결해 속도가 올라가도 같은 궤적으로 넘는다 */
    fun jump(state: RunnerState): Double {
        if (state.outcome != RunnerOutcome.CORRECT || state.obstacleX > JUMP_START || state.obstacleX < JUMP_END) return 0.0
        return sin(PI * (JUMP_START - state.obstacleX) / (JUMP_START - JUMP_END)) * 96
    }

    /** 남은 시간 비율 (1 = 방금 등장, 0 = 충돌 지점) */
    fun remaining(state: RunnerState): Double =
        min(1.0, max(0.0, (state.obstacleX - COLLISION_X) / (OBSTACLE_START - COLLISION_X)))
}
