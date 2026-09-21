package site.smap.gugudan.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

// 구구 레인 — 레인 3개를 오가며 장애물을 피하고, 정답 숫자가 적힌 레인으로 통과한다
// (lib/laneRunner.ts / Core/LaneRunner.swift 이식)
// 좌표계는 웹 SVG viewBox(720×360) 기준을 그대로 쓴다. 렌더러가 비율만 맞춰 그린다.

enum class LaneSegment { DODGE, QUIZ }
enum class LaneOutcome { CORRECT, WRONG, HIT }
enum class LaneObstacleKind { ROCK, STUMP }

data class LaneObstacle(val id: Int, val lane: Int, val x: Double, val kind: LaneObstacleKind)

/** values[lane] */
data class LaneGate(val x: Double, val startX: Double, val values: List<Int>)

data class LaneRunnerState(
    val phase: RunnerPhase,
    val segment: LaneSegment = LaneSegment.DODGE,
    val table: Int?,
    val lane: Int = 1,
    /** 전환 시작 시점의 화면상 레인 위치(실수 — 전환 중 재전환도 이어서 보간) */
    val laneFrom: Double = 1.0,
    val laneAnimMs: Double = 0.0,
    val obstacles: List<LaneObstacle> = emptyList(),
    val nextId: Int = 0,
    val spawnInMs: Double = 800.0,
    val spawned: Int = 0,
    val lastFree: List<Int> = LaneRunner.LANES,
    val question: RunnerQuestion? = null,
    val gate: LaneGate? = null,
    val recentKeys: List<String> = emptyList(),
    val outcome: LaneOutcome? = null,
    val given: Int? = null,
    val score: Int = 0,
    val dodged: Int = 0,
    val combo: Int = 0,
    val maxCombo: Int = 0,
    val lives: Int = LaneRunner.MAX_LIVES,
    val distance: Double = 0.0,
    val hitMs: Double = 0.0,
)

object LaneRunner {
    /** 0 = 위(먼 쪽), 2 = 아래(가까운 쪽) */
    val LANES = listOf(0, 1, 2)

    /** 장애물·게이트가 나타나는 x */
    const val SPAWN_X = 740.0

    /** 공룡 몸통과 겹쳐 판정하는 x */
    const val JUDGE_X = 150.0

    /** 화면 밖으로 사라지는 x */
    const val REMOVE_X = -90.0

    /** 충돌·오답 뒤 월드 정지 시간 */
    const val HIT_MS = 1400.0
    const val LANE_ANIM_MS = 140.0

    /** 판정한 게이트가 이만큼 지나가면 다음 장애물을 내보낸다 */
    const val NEXT_CYCLE_X = JUDGE_X - 120

    /** 정답 길 3개마다 한 단계 빨라진다 */
    const val SCORE_PER_LEVEL = 3
    const val MAX_LIVES = 3

    fun level(score: Int): Int = score / SCORE_PER_LEVEL

    /** 방금 통과한 정답으로 속도 단계가 올랐는지 */
    fun leveledUp(state: LaneRunnerState): Boolean =
        state.outcome == LaneOutcome.CORRECT && state.score > 0 && state.score % SCORE_PER_LEVEL == 0

    /** 장애물이 나타나서 판정선에 닿기까지 걸리는 시간 */
    fun travelMs(score: Int): Double = max(1900.0, 3400.0 - level(score) * 250.0)

    /** 문제가 뜬 뒤 게이트가 판정선에 닿기까지 걸리는 시간 (항상 travelMs 이상이라 게이트는 화면 밖에서 등장) */
    fun quizWindowMs(score: Int): Double = max(3500.0, 5000.0 - level(score) * 200.0)

    fun spawnGapMs(score: Int): Double = max(750.0, 1200.0 - level(score) * 70.0)

    fun obstaclesPerCycle(score: Int): Int = min(4, 2 + level(score) / 2)

    fun speed(score: Int): Double = (SPAWN_X - JUDGE_X) / travelMs(score)

    fun create(table: Int?, phase: RunnerPhase = RunnerPhase.RUNNING): LaneRunnerState =
        LaneRunnerState(phase = phase, table = table)

    fun setLane(state: LaneRunnerState, lane: Int): LaneRunnerState {
        if (state.phase != RunnerPhase.RUNNING || lane !in LANES || lane == state.lane) return state
        return state.copy(lane = lane, laneFrom = offset(state), laneAnimMs = LANE_ANIM_MS)
    }

    fun move(state: LaneRunnerState, delta: Int): LaneRunnerState = setLane(state, state.lane + delta)

    /** 화면에 그릴 공룡의 레인 위치(0~2 사이 실수). 레인 전환을 부드럽게 보간한다. */
    fun offset(state: LaneRunnerState): Double {
        val t = state.laneAnimMs / LANE_ANIM_MS
        return state.lane + (state.laneFrom - state.lane) * t * t
    }

    private fun <T> pick(values: List<T>, random: () -> Double): T =
        values[min((random() * values.size).toInt(), values.size - 1)]

    /** 한 열의 장애물 배치. 빈 레인은 직전 열의 빈 레인에서 한 칸 이내로만 둔다. */
    private fun spawnColumn(state: LaneRunnerState, random: () -> Double): LaneRunnerState {
        val double = level(state.score) >= 2 && random() < 0.25
        val blocked: List<Int> = if (double) {
            val reachable = LANES.filter { lane -> state.lastFree.any { abs(it - lane) <= 1 } }
            val free = pick(reachable, random)
            LANES.filter { it != free }
        } else {
            // 절반 남짓은 지금 레인을 막아 직접 움직이게 한다
            listOf(if (random() < 0.55) state.lane else pick(LANES, random))
        }
        val kind = if (state.spawned % 2 == 0) LaneObstacleKind.ROCK else LaneObstacleKind.STUMP
        return state.copy(
            obstacles = state.obstacles + blocked.mapIndexed { i, lane -> LaneObstacle(state.nextId + i, lane, SPAWN_X, kind) },
            nextId = state.nextId + blocked.size,
            spawned = state.spawned + 1,
            spawnInMs = spawnGapMs(state.score),
            lastFree = LANES.filter { it !in blocked },
        )
    }

    private fun startQuiz(state: LaneRunnerState, random: () -> Double): LaneRunnerState {
        val question = Runner.question(state.table, state.recentKeys, random)
        val startX = JUDGE_X + speed(state.score) * quizWindowMs(state.score)
        return state.copy(
            segment = LaneSegment.QUIZ,
            question = question,
            recentKeys = (state.recentKeys + Problems.key(question.a, question.b)).takeLast(3),
            gate = LaneGate(startX, startX, question.choices),
            outcome = null, given = null,
        )
    }

    /** 판정이 끝난 게이트는 화면 밖으로 나갈 때까지 보여 주고, 장애물은 바로 이어서 내보낸다. */
    private fun startDodge(state: LaneRunnerState): LaneRunnerState =
        state.copy(segment = LaneSegment.DODGE, spawned = 0, spawnInMs = 0.0, lastFree = LANES)

    fun advance(state: LaneRunnerState, dt: Double, random: () -> Double = { Random.nextDouble() }): LaneRunnerState {
        if (state.phase != RunnerPhase.RUNNING || !dt.isFinite() || dt <= 0) return state
        val laneAnimMs = max(0.0, state.laneAnimMs - dt)

        if (state.hitMs > 0) {
            val hitMs = max(0.0, state.hitMs - dt)
            if (hitMs > 0) return state.copy(hitMs = hitMs, laneAnimMs = laneAnimMs)
            if (state.lives == 0) return state.copy(hitMs = 0.0, laneAnimMs = laneAnimMs, phase = RunnerPhase.OVER)
            return state.copy(
                hitMs = 0.0, laneAnimMs = laneAnimMs,
                outcome = if (state.outcome == LaneOutcome.HIT) null else state.outcome,
            )
        }

        val movement = speed(state.score) * dt
        var next = state.copy(laneAnimMs = laneAnimMs, distance = state.distance + movement / 20)

        // 장애물: 판정선을 넘는 프레임에 한 번만 판정한다(dt와 무관)
        var hit = false
        var dodged = 0
        val obstacles = mutableListOf<LaneObstacle>()
        for (obstacle in state.obstacles) {
            val x = obstacle.x - movement
            if (obstacle.x > JUDGE_X && x <= JUDGE_X) {
                if (obstacle.lane == state.lane && !hit) {
                    hit = true
                    continue // 부딪힌 장애물은 치운다
                }
                dodged += 1
            }
            if (x > REMOVE_X) obstacles += obstacle.copy(x = x)
        }
        // 부딪힌 열의 나머지 장애물은 피한 것으로 세지 않는다
        next = next.copy(obstacles = obstacles, dodged = next.dodged + if (hit) 0 else dodged)
        if (hit) {
            return next.copy(lives = next.lives - 1, combo = 0, outcome = LaneOutcome.HIT, hitMs = HIT_MS)
        }

        val gate = next.gate
        if (gate != null) {
            val x = gate.x - movement
            val question = next.question
            if (gate.x > JUDGE_X && x <= JUDGE_X && question != null && next.segment == LaneSegment.QUIZ) {
                val given = gate.values[next.lane]
                if (given == question.answer) {
                    val combo = next.combo + 1
                    return next.copy(
                        gate = gate.copy(x = x), given = given, outcome = LaneOutcome.CORRECT,
                        score = next.score + 1, combo = combo, maxCombo = max(next.maxCombo, combo),
                    )
                }
                return next.copy(
                    gate = gate.copy(x = JUDGE_X), given = given, outcome = LaneOutcome.WRONG,
                    lives = next.lives - 1, combo = 0, hitMs = HIT_MS,
                )
            }
            next = if (x < REMOVE_X) {
                next.copy(
                    gate = null, question = null, given = null,
                    outcome = if (next.outcome == LaneOutcome.HIT) LaneOutcome.HIT else null,
                )
            } else {
                next.copy(gate = gate.copy(x = x))
            }
            if (next.segment == LaneSegment.QUIZ && x < NEXT_CYCLE_X) return startDodge(next)
        }

        if (next.segment == LaneSegment.DODGE) {
            val spawnInMs = next.spawnInMs - dt
            if (spawnInMs > 0) return next.copy(spawnInMs = spawnInMs)
            // 마지막 장애물 뒤 한 간격이 지나면 곧바로 문제를 낸다
            return if (next.spawned < obstaclesPerCycle(next.score)) spawnColumn(next, random)
            else startQuiz(next, random)
        }
        return next
    }
}
