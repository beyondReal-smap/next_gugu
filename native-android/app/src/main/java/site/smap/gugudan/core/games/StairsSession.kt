package site.smap.gugudan.core.games

import site.smap.gugudan.core.AnswerRecord
import site.smap.gugudan.core.Problem
import site.smap.gugudan.core.Problems
import kotlin.math.max
import kotlin.random.Random

// A 구구 계단 — 계단 점프 규칙 (Unity 시험판 Core/StairsSession.cs 이식, iOS StairsSession.swift 와 1:1)
// 한 단을 ×1 → ×topStep 순서로 오른다. 맞히면 한 칸 위·바로 다음 문제, 틀리면 정답을 0.6초 보여 준 뒤
// 같은 칸을 보기만 섞어 다시 낸다. 꼭대기(×topStep)에 닿으면 보물 +1, 2~9단 중 다른 단의 ×1 부터 이어서 오른다.
// 시간 제한은 없고, 아래에서 차오르는 먹구름(RisingCloud)에 잡히면 판이 끝난다.
//
// 원본과 달라진 점(2.9.0 확정):
//  - 시작 단 firstTable: null = 2~9 무작위, 값이 있으면 2~19 (단 시트에서 고른 단)
//  - 꼭대기 칸 topStep = 설정 「곱하는 수」(9·12·15·19), 생성 때 주입 (원본 9 고정)
//  - 보기 최대값 = 단 × topStep + 단 (원본 99 고정)
//
// 문제 순서(order, 2.9.0 시작 창에서 고름):
//  - 순서대로(SEQUENTIAL): 위 설명 그대로(원본 규칙)
//  - 무작위(RANDOM): 문제마다 2~9단 × 1~topStep 에서 하나(직전 문제와 같은 식이면 다시 뽑음).
//    서 있는 발판 = 직전 정답(첫 칸은 0 = "출발"). topStep 칸을 오를 때마다 보물 +1(단은 바꾸지 않음 — 문제마다 바뀜).
//    시작 단은 받지 않는다(firstTable 은 null 이어야 함 — 단 시트에서 고른 단도 쓰지 않음)
//
// 난수 소비 순서 — 골든 벡터가 기대는 계약(iOS 와 같아야 함):
//  순서대로
//   생성: [firstTable 이 null 이면 단 고르기] → 첫 문제 보기
//   정답: [꼭대기였으면 다음 단 고르기(현재 단이 나오면 다시 뽑음)] → 다음 문제 보기
//  무작위
//   생성: 식 고르기 → 첫 문제 보기
//   정답: 식 고르기(직전 식이 나오면 다시 뽑음) → 다음 문제 보기 — 보물을 얻어도 난수를 더 쓰지 않는다
//   식 고르기(randomProblem): 단 index(8) → 곱하는 수 index(topStep), 직전 식과 같으면 둘 다 다시
//  공통
//   보기(makeChoices): 오답 후보 섞기 → 정답 + 후보 앞 2개 섞기
//   오답 공개가 끝난 tick: 같은 칸(같은 식·같은 발판) 보기

data class StairsQuestion(
    val id: Int,
    /** 단 × 칸 */
    val problem: Problem,
    /** 지금 서 있는 발판 숫자 — 순서대로: 단 × (칸 - 1), 무작위: 직전 정답(첫 칸 0) */
    val standing: Int,
    /** 칸(0 왼쪽·1 가운데·2 오른쪽)마다 발판 숫자 하나 */
    val choices: List<Int>,
    /** 문제가 나온 시각(세션 playTime, 초) — 응답 시간 계산용 */
    val issuedAt: Double,
) {
    val correctLane: Int = choices.indexOf(problem.a * problem.b)

    init {
        require(choices.size == StairsSession.LANES) { "칸마다 숫자 하나씩이어야 함: $choices" }
        require(correctLane >= 0) { "보기에 정답이 없음: $problem $choices" }
    }
}

sealed class StairsJumpResult {
    data class Rejected(val reason: ActionReject) : StairsJumpResult()

    /** clearedTable: 이 점프로 꼭대기(순서대로 ×topStep, 무작위 topStep 칸째)에 닿아 보물을 얻었는지 */
    data class Judged(override val attempt: AttemptRecord, override val clearedTable: Boolean) : StairsJumpResult()

    open val attempt: AttemptRecord? get() = null
    open val clearedTable: Boolean get() = false
    val reject: ActionReject? get() = (this as? Rejected)?.reason
    val accepted: Boolean get() = attempt != null
}

class StairsSession(
    /** 꼭대기 칸 = 설정 「곱하는 수」 (9·12·15·19) */
    val topStep: Int,
    /** 문제 순서 — 순서대로(한 단씩 ×1 → ×topStep) / 무작위(문제마다 2~9단 × 1~topStep) */
    val order: ProblemOrder,
    /** 순서대로의 시작 단(null = 2~9 무작위). 무작위 순서는 시작 단을 받지 않는다(null 이어야 함) */
    firstTable: Int? = null,
    private val random: () -> Double = { Random.nextDouble() },
) {
    // 시계·구름은 Swift struct 처럼 값으로 내보낸다(복사본) — 화면이 세션 상태를 바꾸지 못하게
    private val roundClock = RoundClock(RoundClock.UNLIMITED)
    private val risingCloud = RisingCloud()
    val clock: RoundClock get() = roundClock.copy()
    val cloud: RisingCloud get() = risingCloud.copy()

    /** 먹구름에 잡혀 끝났는지 */
    var caught = false
        private set
    /** 지금 문제의 단 — 순서대로면 오르는 단, 무작위면 문제마다 바뀐다 */
    var table: Int
        private set
    /** 지금 풀어야 하는 칸 (1~topStep) — 무작위면 보물까지 몇 번째 칸인지 */
    var step = 1
        private set
    var current: StairsQuestion
        private set
    var currentJudged = false
        private set
    var issuedCount = 0
        private set
    /** 오른 계단 수 = 맞힌 문제 수 (구름 높이와 같은 단위) */
    var climbed = 0
        private set
    var treasures = 0
        private set
    var wrongCount = 0
        private set
    /** 지금 이어지는 연속 정답(오답이면 0) */
    var streak = 0
        private set
    var bestStreak = 0
        private set
    /** 이번 판에 받은 별 코인(StarRules) */
    var stars = 0
        private set
    private val attemptList = mutableListOf<AttemptRecord>()
    /** 채점 기록(복사본 — Swift 배열처럼 값으로 내보낸다) */
    val attempts: List<AttemptRecord> get() = attemptList.toList()
    /** 응답 시간 재기용 시계(초): 판을 연 뒤 tick 으로 흘린 시간. 첫 동작 전 대기 포함, 쉬는 동안(tick 없음)·판 끝 뒤 제외 */
    var playTime = 0.0
        private set

    init {
        require(topStep in Problems.MAX_B_OPTIONS) { "꼭대기 칸은 ${Problems.MAX_B_OPTIONS} 중 하나여야 함: $topStep" }
        if (firstTable != null) {
            require(order == ProblemOrder.SEQUENTIAL) { "무작위 순서는 시작 단을 받지 않음: $firstTable" }
            require(firstTable in FIRST_TABLES) { "시작 단 범위 밖: $firstTable" }
        }
        val first = when (order) {
            ProblemOrder.SEQUENTIAL -> Problem(firstTable ?: pickTable(excluding = null, random = random), 1)
            ProblemOrder.RANDOM -> randomProblem(topStep, previous = null, random = random)
        }
        table = first.a
        current = makeQuestion(id = 0, problem = first, standing = 0, topStep = topStep, issuedAt = 0.0, random = random)
        issuedCount = 1
    }

    /** lane 칸 발판으로 점프. 받아들이면 정확히 한 번 채점한다 */
    fun jump(lane: Int): StairsJumpResult {
        roundClock.check(valid = lane in 0 until LANES)?.let { return StairsJumpResult.Rejected(it) }
        check(!currentJudged) { "문제 ${current.id} 는 이미 채점됨" }

        roundClock.markAction()
        val attempt = AttemptRecord(
            id = attemptList.size, questionId = current.id, problem = current.problem,
            given = current.choices[lane], elapsed = roundClock.elapsed, responseSec = playTime - current.issuedAt,
        )
        attemptList.add(attempt)
        currentJudged = true

        if (!attempt.correct) {
            wrongCount += 1
            streak = 0
            roundClock.beginReveal()  // tick 에서 공개가 끝나면 같은 칸을 섞어서 다시 낸다
            return StairsJumpResult.Judged(attempt, clearedTable = false)
        }

        climbed += 1
        streak += 1
        bestStreak = max(bestStreak, streak)
        stars += StarRules.forCorrect(streak)
        val cleared = step == topStep
        if (cleared) {
            treasures += 1
            stars += StarRules.TREASURE_BONUS
        }
        step = if (cleared) 1 else step + 1
        when (order) {
            ProblemOrder.SEQUENTIAL -> {
                if (cleared) table = pickTable(excluding = table, random = random)
                issue(Problem(table, step), standing = table * (step - 1))
            }
            ProblemOrder.RANDOM -> {
                val answered = current.problem
                val next = randomProblem(topStep, previous = answered, random = random)
                table = next.a
                issue(next, standing = answered.a * answered.b)   // 방금 밟은 발판 = 직전 정답
            }
        }
        return StairsJumpResult.Judged(attempt, clearedTable = cleared)
    }

    /**
     * dt(초) 만큼 진행. 순서: 시계 → 구름. 첫 동작 전(WAITING)·종료 뒤에는 구름도 멈춰 있다.
     * 같은 프레임의 입력은 tick 보다 먼저 처리하므로, 잡히기 직전에 누른 정답은 한 칸을 벌어 살아남는다
     */
    fun tick(dt: Double) {
        require(dt >= 0) { "dt 는 0 이상이어야 함: $dt" }
        if (roundClock.phase != RoundPhase.OVER) playTime += dt
        val revealEnded = roundClock.tick(dt)
        if (roundClock.phase == RoundPhase.RUNNING && risingCloud.tick(dt, standing = climbed)) {
            caught = true
            roundClock.end()
            return  // 끝났으니 공개가 끝났어도 새 문제를 내지 않음
        }
        if (revealEnded) issue(current.problem, current.standing)   // 같은 칸, 보기만 다시
    }

    /** 결과 화면에 보여 줄 틀린 식 하나 */
    val missToShow: AttemptRecord? get() = AttemptRecord.mostMissed(attemptList)

    /** 학습 기록(commitSession)으로 넘길 채점 기록 — 문제 = 단 × 칸, given = 고른 발판 숫자 */
    val answerRecords: List<AnswerRecord> get() = attemptList.map { it.answerRecord }

    private fun issue(problem: Problem, standing: Int) {
        current = makeQuestion(
            id = issuedCount, problem = problem, standing = standing, topStep = topStep, issuedAt = playTime, random = random,
        )
        issuedCount += 1
        currentJudged = false
    }

    companion object {
        const val LANES = 3
        /** 무작위로 고르는 단(시작 단이 null 일 때·보물 뒤) — 2~9단 */
        val RANDOM_TABLES = 2..9
        /** 시작 단으로 고를 수 있는 범위 — 2~19단 (단 시트) */
        val FIRST_TABLES = Problems.MIN_TABLE..Problems.MAX_TABLE

        /** 보기 최댓값 = 단 × topStep + 단 (꼭대기 다음 배수까지 자연스러움) */
        fun maxChoice(table: Int, topStep: Int): Int = table * topStep + table

        private fun makeQuestion(
            id: Int, problem: Problem, standing: Int, topStep: Int, issuedAt: Double, random: () -> Double,
        ): StairsQuestion {
            val choices = makeChoices(problem, standing, topStep, random)
            return StairsQuestion(id, problem, standing, choices, issuedAt)
        }

        /** 무작위 순서의 식 하나 — 단(2~9) → 곱하는 수(1~topStep) 순서로 난수 2개. 직전 식과 같으면 둘 다 다시 뽑는다 */
        fun randomProblem(topStep: Int, previous: Problem?, random: () -> Double): Problem {
            require(topStep in Problems.MAX_B_OPTIONS) { "꼭대기 칸은 ${Problems.MAX_B_OPTIONS} 중 하나여야 함: $topStep" }
            while (true) {
                val a = RANDOM_TABLES.first + GameRandom.index(RANDOM_TABLES.count(), random)
                val b = 1 + GameRandom.index(topStep, random)
                val p = Problem(a, b)
                if (p != previous) return p
            }
        }

        /**
         * 정답 1개 + 오답 2개를 섞은 3칸. 오답 후보: 한 칸 건너뛴 배수(+단), ±1, ±곱하는 수.
         * 지금 서 있는 발판 숫자는 빼서 헷갈림을 줄이고, 1 ~ maxChoice 밖의 수는 뺀다
         */
        fun makeChoices(p: Problem, standing: Int, topStep: Int, random: () -> Double): List<Int> {
            require(p.b in 1..topStep) { "칸은 1~$topStep 이어야 함: ${p.b}" }
            val answer = p.a * p.b
            val maxValue = maxChoice(p.a, topStep)
            val raw = listOf(answer + p.a, answer - 1, answer + 1, answer - p.b, answer + p.b)
            val alternatives = mutableListOf<Int>()
            for (v in raw) {
                if (v > 0 && v <= maxValue && v != answer && v != standing && v !in alternatives) alternatives.add(v)
            }
            check(alternatives.size >= LANES - 1) { "$p 의 오답 후보가 부족함 (${alternatives.size}개)" }
            GameRandom.shuffle(alternatives, random)
            val choices = mutableListOf(answer, alternatives[0], alternatives[1])
            GameRandom.shuffle(choices, random)
            return choices
        }

        /** 2~9단에서 하나. excluding(보물 직전 단)이 나오면 다시 뽑는다 — 10단 이상에서 시작했으면 첫 뽑기가 곧 결과 */
        private fun pickTable(excluding: Int?, random: () -> Double): Int =
            GameRandom.pick(RANDOM_TABLES, excluding, random)
    }
}
