package site.smap.gugudan.core.games

import site.smap.gugudan.core.AnswerRecord
import site.smap.gugudan.core.Problem
import kotlin.math.max
import kotlin.random.Random

// B 구구 몬스터 — 곱셈 몬스터 규칙 (Unity 시험판 Core/MonsterSession.cs·ProblemRules.Pick 이식, iOS MonsterSession.swift 와 1:1)
// 몬스터 체력(곱)과 같아지는 카드 2장을 고르면 처치. 두 번째 카드를 고르는 순간 한 번만 채점한다.
// 같은 곱의 다른 카드 조합도 정답(12 = 3×4 = 2×6). 기록은 몬스터의 목표 식으로 남긴다.
// 출제는 2~9단 × 1~9 고정(설정 「곱하는 수」와 무관). 카드는 1~9.
// 틀리면 정답을 0.6초 보여 준 뒤 같은 몬스터(같은 목표)를 카드만 섞어 다시 낸다. 60초 제한.
//
// 문제 순서(order, 2.9.0 시작 창에서 고름):
//  - 무작위(RANDOM): 72문항에서 가중 무작위, 직전 3문제 가중치 ×0.15 (원본 규칙). 단 지정을 받지 않는다(table 은 null 이어야 함)
//  - 순서대로(SEQUENTIAL): 목표 = 단 × 1, × 2 … × 9 차례로.
//    단 지정(table: 2~9 — 단 시트에서 고른 단)이면 × 9 다음은 그 단 × 1 로 다시,
//    null 이면 시작 단을 2~9 에서 무작위로 고르고 × 9 다음은 직전과 다른 2~9단 × 1
//  (2026-11-24 iOS 와 함께 "무작위 + 단 지정(그 단 9문항에서 가중 무작위)" 경로는 지웠다 — 화면에서 만들 수 없는 조합)
//
// 난수 소비 순서 — 골든 벡터가 기대는 계약(iOS 와 같아야 함):
//  무작위
//   새 몬스터: 목표 고르기(pickTarget, 난수 1개) → 카드
//  순서대로
//   생성: [table 이 null 이면 시작 단 index(8)] → 카드
//   처치: [× 9 였고 table 이 null 이면 다음 단 index(8)(직전 단이 나오면 다시 뽑음)] → 카드 — 그 밖에는 카드만
//  공통
//   카드(makeCards): 근처 수 섞기 → 나머지 수 섞기 → 자리 순서 섞기
//   오답 공개가 끝난 tick: 같은 목표로 카드만

data class MonsterQuestion(
    val id: Int,
    /** 몬스터 체력 = target 의 곱 */
    val target: Problem,
    val cards: List<Int>,
    /** 정답으로 보여 줄 카드 자리 (다른 정답 조합도 인정) */
    val pairA: Int,
    val pairB: Int,
    /** 문제가 나온 시각(세션 playTime, 초) — 응답 시간 계산용 */
    val issuedAt: Double,
) {
    init {
        require(cards.size == MonsterSession.CARD_COUNT) { "카드 수가 다름: $cards" }
        require(pairA != pairB && cards[pairA] * cards[pairB] == target.a * target.b) {
            "정답 자리는 곱이 목표인 서로 다른 두 장이어야 함"  // i18n:skip
        }
    }
}

/** makeCards 결과 (Swift 는 튜플) — pairA·pairB = 원래 0번(a)·1번(b) 카드가 간 자리 */
data class MonsterCards(val cards: List<Int>, val pairA: Int, val pairB: Int)

sealed class CardTapResult {
    data class Rejected(val reason: ActionReject) : CardTapResult()
    data object Selected : CardTapResult()
    data object Deselected : CardTapResult()
    data class Judged(override val attempt: AttemptRecord) : CardTapResult()

    open val attempt: AttemptRecord? get() = null
    val reject: ActionReject? get() = (this as? Rejected)?.reason
}

/**
 * table: 순서대로의 단 지정(2~9 — 10단 이상은 카드 1~9 로 만들 수 없어 진입 화면이 막는다).
 * null = 순서대로면 시작 단 2~9 무작위, 무작위면 2~9단 섞어서. 무작위 순서는 단 지정을 받지 않는다
 */
class MonsterSession(
    /** 문제 순서 — 무작위(72문항 가중 무작위) / 순서대로(단 × 1 → × 9) */
    val order: ProblemOrder,
    /** 단 지정(2~9, 순서대로만). null = 순서대로면 단을 바꿔 가며, 무작위면 2~9단 섞어서 */
    val table: Int? = null,
    private val random: () -> Double = { Random.nextDouble() },
) {
    // 시계는 Swift struct 처럼 값으로 내보낸다(복사본) — 화면이 세션 상태를 바꾸지 못하게
    private val roundClock = RoundClock()
    val clock: RoundClock get() = roundClock.copy()

    var current: MonsterQuestion
        private set
    var currentJudged = false
        private set
    /** 먼저 고른 카드 자리 (없으면 null) */
    var selected: Int? = null
        private set
    var issuedCount = 0
        private set
    /** 지금까지 나온 몬스터 수(오답 뒤 같은 몬스터 재출제는 세지 않음) — 화면이 몬스터 종을 바꿀 때 쓴다 */
    var monstersIssued = 0
        private set
    var defeated = 0
        private set
    var wrongCount = 0
        private set
    /** 지금 이어지는 연속 처치(오답이면 0) */
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

    /** 무작위 — 최근 목표(가중치 낮춤) */
    private val recent = mutableListOf<Problem>()

    init {
        if (table != null) {
            require(order == ProblemOrder.SEQUENTIAL) { "무작위 순서는 단 지정을 받지 않음: $table" }
            require(table in TABLES) { "단 지정은 $TABLES 만: $table" }
        }
        val target = when (order) {
            ProblemOrder.RANDOM -> pickTarget(recent = emptyList(), random = random).also { recent.add(it) }
            ProblemOrder.SEQUENTIAL -> Problem(table ?: GameRandom.pick(TABLES, excluding = null, random = random), 1)
        }
        monstersIssued = 1
        current = makeQuestion(id = 0, target = target, issuedAt = 0.0, random = random)
        issuedCount = 1
    }

    /** card 자리 카드를 누름. 첫 장은 고르기, 같은 장은 취소, 다른 장은 채점(정확히 한 번) */
    fun tap(card: Int): CardTapResult {
        roundClock.check(valid = card in 0 until CARD_COUNT)?.let { return CardTapResult.Rejected(it) }
        check(!currentJudged) { "문제 ${current.id} 는 이미 채점됨" }

        roundClock.markAction()
        val first = selected
        if (first == null) {
            selected = card
            return CardTapResult.Selected
        }
        if (first == card) {
            selected = null
            return CardTapResult.Deselected
        }

        val a = current.cards[first]
        val b = current.cards[card]
        val attempt = AttemptRecord(
            id = attemptList.size, questionId = current.id, problem = current.target, given = a * b,
            elapsed = roundClock.elapsed, responseSec = playTime - current.issuedAt, pickA = a, pickB = b,
        )
        attemptList.add(attempt)
        currentJudged = true
        selected = null

        if (attempt.correct) {
            defeated += 1
            streak += 1
            bestStreak = max(bestStreak, streak)
            stars += StarRules.forCorrect(streak)
            nextMonster()
        } else {
            wrongCount += 1
            streak = 0
            roundClock.beginReveal()
        }
        return CardTapResult.Judged(attempt)
    }

    /** dt(초) 만큼 진행. 오답 공개가 끝나면 같은 몬스터를 카드만 섞어 다시 낸다 */
    fun tick(dt: Double) {
        require(dt >= 0) { "dt 는 0 이상이어야 함: $dt" }
        if (roundClock.phase != RoundPhase.OVER) playTime += dt
        if (roundClock.tick(dt)) issue(current.target)
    }

    /** 결과 화면에 보여 줄 틀린 식 하나 */
    val missToShow: AttemptRecord? get() = AttemptRecord.mostMissed(attemptList)

    /** 학습 기록(commitSession)으로 넘길 채점 기록 — 문제 = 목표 식, given = 고른 두 카드의 곱 */
    val answerRecords: List<AnswerRecord> get() = attemptList.map { it.answerRecord }

    private fun nextMonster() {
        val target = when (order) {
            ProblemOrder.RANDOM -> {
                val picked = pickTarget(recent = recent, random = random)
                recent.add(picked)
                // removeFirst() 는 Android 15 미만에서 Java 21 메서드로 묶여 죽을 수 있어 removeAt(0) 을 쓴다
                if (recent.size > RECENT_WINDOW) recent.removeAt(0)
                picked
            }
            ProblemOrder.SEQUENTIAL -> nextInOrder(after = current.target, table = table, random = random)
        }
        monstersIssued += 1
        issue(target)
    }

    private fun issue(target: Problem) {
        current = makeQuestion(id = issuedCount, target = target, issuedAt = playTime, random = random)
        issuedCount += 1
        currentJudged = false
        selected = null
    }

    companion object {
        const val CARD_COUNT = 6
        val CARD_VALUES = 1..9
        val TABLES = 2..9
        val MULTIPLIERS = 1..9
        const val RECENT_WINDOW = 3
        const val RECENT_WEIGHT = 0.15
        /** 무작위 출제 후보 72개 — 단 순서 → 곱하는 수 순서. 누적 분포의 순서이므로 바꾸지 말 것 */
        val TARGETS: List<Problem> = TABLES.flatMap { a -> MULTIPLIERS.map { Problem(a, it) } }

        private fun makeQuestion(id: Int, target: Problem, issuedAt: Double, random: () -> Double): MonsterQuestion {
            val made = makeCards(target, random)
            return MonsterQuestion(id, target, made.cards, made.pairA, made.pairB, issuedAt)
        }

        /**
         * 순서대로의 다음 목표 — × 9 가 아니면 같은 단 다음 곱하는 수(난수 없음),
         * × 9 다음은 단 지정이면 그 단 × 1, 없으면 직전과 다른 2~9단 × 1(index(8), 직전 단이면 다시 뽑음)
         */
        fun nextInOrder(after: Problem, table: Int?, random: () -> Double): Problem {
            require(after.a in TABLES && after.b in MULTIPLIERS) { "순서대로 목표 범위 밖: $after" }
            if (table != null) require(after.a == table) { "단 지정 $table 인데 목표가 $after" }
            if (after.b != MULTIPLIERS.last) return Problem(after.a, after.b + 1)
            val next = table ?: GameRandom.pick(TABLES, excluding = after.a, random = random)
            return Problem(next, MULTIPLIERS.first)
        }

        /**
         * 가중 무작위 출제 (기존 pickProblem 규칙) — TARGETS 72개 중, recent 의 마지막 3개는 가중치 ×0.15.
         * 누적 분포 — 난수 × 합계에서 가중치를 빼다가 0 이하가 되는 첫 항목(난수 1개)
         */
        fun pickTarget(recent: List<Problem>, random: () -> Double): Problem {
            val avoid = recent.takeLast(RECENT_WINDOW)
            val weights = TARGETS.map { if (it in avoid) RECENT_WEIGHT else 1.0 }
            var total = 0.0
            for (w in weights) total += w  // 앞에서부터 더한다(플랫폼 간 같은 합계)
            var r = random() * total
            for ((i, w) in weights.withIndex()) {
                r -= w
                if (r <= 0) return TARGETS[i]
            }
            return TARGETS[TARGETS.size - 1]
        }

        /**
         * 정답 두 장(a, b — 같으면 같은 수 두 장) + 근처 숫자(±1) 위주의 오답, 나머지 무작위로 6장. 1~9 카드만 쓴다.
         * pairA·pairB = 원래 0번(a)·1번(b) 카드가 간 자리
         */
        fun makeCards(p: Problem, random: () -> Double): MonsterCards {
            require(p.a in CARD_VALUES && p.b in CARD_VALUES) { "카드로 만들 수 없는 식: $p" }
            val near = mutableListOf<Int>()
            for (v in listOf(p.a - 1, p.a + 1, p.b - 1, p.b + 1)) {
                if (v in CARD_VALUES && v != p.a && v != p.b && v !in near) near.add(v)
            }
            val rest = mutableListOf<Int>()
            for (v in CARD_VALUES) {
                if (v != p.a && v != p.b && v !in near) rest.add(v)
            }
            GameRandom.shuffle(near, random)
            GameRandom.shuffle(rest, random)

            val cards = mutableListOf(p.a, p.b)
            for (v in near) if (cards.size < CARD_COUNT) cards.add(v)
            for (v in rest) if (cards.size < CARD_COUNT) cards.add(v)

            val order = (0 until CARD_COUNT).toMutableList()
            GameRandom.shuffle(order, random)
            val shuffled = MutableList(CARD_COUNT) { 0 }
            for (i in 0 until CARD_COUNT) shuffled[order[i]] = cards[i]
            return MonsterCards(shuffled, order[0], order[1])
        }
    }
}
