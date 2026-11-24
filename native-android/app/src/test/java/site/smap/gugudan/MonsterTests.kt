package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.AnswerRecord
import site.smap.gugudan.core.GivenAnswer
import site.smap.gugudan.core.Problem
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.SeededRandom
import site.smap.gugudan.core.games.ActionReject
import site.smap.gugudan.core.games.AttemptRecord
import site.smap.gugudan.core.games.CardTapResult
import site.smap.gugudan.core.games.GameRandom
import site.smap.gugudan.core.games.MonsterQuestion
import site.smap.gugudan.core.games.MonsterSession
import site.smap.gugudan.core.games.ProblemOrder
import site.smap.gugudan.core.games.RoundClock
import site.smap.gugudan.core.games.RoundPhase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * B 구구 몬스터(곱셈 몬스터) 순수 규칙.
 * Unity 시험판 PrototypeSessionTests.MonsterSessionTests·ProblemRulesTests(출제) 이식
 * + PlayMode 규칙성(1,000회 채점·쉬는 동안 정지) + 학습 기록 + 골든 벡터
 * + 2.9.0 문제 순서(무작위 = 원본 출제, 순서대로 = 단 × 1 → × 9).
 * iOS MonsterTests.swift 와 같은 기대값 — 한쪽을 바꾸면 양쪽을 함께 바꿀 것.
 * 출제는 2~9단 × 1~9 고정 — 설정 「곱하는 수」(Problems.maxB)와 무관하다.
 */
class MonsterTests {

    // 도우미

    private fun rng(seed: Int): () -> Double = SeededRandom.mulberry32(seed.toUInt())

    private fun session(seed: Int): MonsterSession = MonsterSession(ProblemOrder.RANDOM, random = rng(seed))

    private fun product(p: Problem): Int = p.a * p.b

    /** pairA 와 짝지었을 때 곱이 목표가 아닌 카드 중 가장 앞 자리 */
    private fun wrongCard(q: MonsterQuestion): Int =
        (0 until MonsterSession.CARD_COUNT).firstOrNull {
            it != q.pairA && q.cards[q.pairA] * q.cards[it] != product(q.target)
        } ?: fail("오답 카드가 없음 ${q.cards}")

    private fun defeat(s: MonsterSession) {
        val q = s.current
        assertEquals(CardTapResult.Selected, s.tap(q.pairA))
        assertEquals(true, s.tap(q.pairB).attempt?.correct)
    }

    /** 틀린 짝 → 공개가 끝날 때까지 흘림(같은 몬스터, 카드만 다시) */
    private fun miss(s: MonsterSession): AttemptRecord? {
        val q = s.current
        s.tap(q.pairA)
        val r = s.tap(wrongCard(q))
        assertEquals(false, r.attempt?.correct)
        s.tick(RoundClock.REVEAL_SEC + 0.01)
        return r.attempt
    }

    private fun assertIntegrity(s: MonsterSession) {
        val attempts = s.attempts
        val ids = attempts.map { it.questionId }
        assertEquals(ids.size, ids.toSet().size, "같은 문제 두 번 채점")
        assertEquals(attempts.size, s.defeated + s.wrongCount)
        val unjudged = (0 until s.issuedCount).toSet() - ids.toSet()
        assertTrue(unjudged.size <= 1, "채점 없이 사라진 문제 ${unjudged.sorted()}")
        unjudged.firstOrNull()?.let { assertEquals(s.current.id, it) }
        for ((i, a) in attempts.withIndex()) {
            assertEquals(i, a.id)
            val pickA = assertNotNull(a.pickA, "B 기록에는 고른 카드가 있어야 함")
            val pickB = assertNotNull(a.pickB, "B 기록에는 고른 카드가 있어야 함")
            assertEquals(pickA * pickB, a.given)
            assertEquals(a.given == product(a.problem), a.correct)
            assertTrue(a.problem in MonsterSession.TARGETS)
        }
        assertEquals(attempts.map { it.answerRecord }, s.answerRecords)
    }

    // 출제 (원본 ProblemRulesTests)

    @Test fun targetsAre72FactsTwoToNineByOneToNine() {
        assertEquals(72, MonsterSession.TARGETS.size)
        assertTrue(MonsterSession.TARGETS.all { it.a in 2..9 && it.b in 1..9 })
        assertEquals(72, MonsterSession.TARGETS.map { Problems.key(it.a, it.b) }.toSet().size)
        assertEquals(Problem(2, 1), MonsterSession.TARGETS.first())
        assertEquals(Problem(9, 9), MonsterSession.TARGETS.last())
    }

    @Test fun pickStaysInRangeAndCoversEveryFact() {
        val random = rng(1)
        val recent = mutableListOf<Problem>()
        val seen = mutableSetOf<String>()
        repeat(20_000) {
            val p = MonsterSession.pickTarget(recent, random)
            assertTrue(p.a in 2..9 && p.b in 1..9)
            seen.add(Problems.key(p.a, p.b))
            recent.add(p)
            if (recent.size > 3) recent.removeAt(0)
        }
        assertEquals(72, seen.size)
    }

    /** 직전 3문제 가중치 ×0.15 → 이론값: 3문제 안 반복 0.648% */
    @Test fun pickRecentAvoidanceMatchesSoftWeight() {
        val n = 200_000
        val random = rng(7)
        val history = mutableListOf<Problem>()
        var immediate = 0
        var withinThree = 0
        repeat(n) {
            val p = MonsterSession.pickTarget(history, random)
            if (history.lastOrNull() == p) immediate += 1
            if (p in history) withinThree += 1
            history.add(p)
            if (history.size > 3) history.removeAt(0)
        }
        val expectedWithin = 3 * 0.15 / (69 + 3 * 0.15)
        assertEquals(expectedWithin, withinThree.toDouble() / n, 0.0015)
        assertTrue(immediate.toDouble() / n < 0.005)
    }

    /** 설정 「곱하는 수」를 19로 바꿔도 B 는 2~9단 × 1~9, 카드 1~9 그대로 */
    @Test fun multiplierSettingDoesNotChangeRange() {
        Problems.maxB = 19
        try {
            val s = session(3)
            repeat(300) {
                assertTrue(s.current.target in MonsterSession.TARGETS)
                assertTrue(s.current.cards.all { it in 1..9 })
                defeat(s)
            }
        } finally {
            Problems.maxB = Problems.DEFAULT_MAX_B
        }
    }

    // 카드·채점 (원본 MonsterSessionTests)

    @Test fun cardsAllFactsContainPairAndAreValid() {
        for (seed in 0 until 40) {
            val random = rng(seed)
            for (p in MonsterSession.TARGETS) {
                val (cards, pairA, pairB) = MonsterSession.makeCards(p, random)
                assertEquals(MonsterSession.CARD_COUNT, cards.size)
                assertNotEquals(pairA, pairB)
                assertEquals(product(p), cards[pairA] * cards[pairB], "$p")
                assertTrue(cards.all { it in MonsterSession.CARD_VALUES })
                val expectedDistinct = if (p.a == p.b) MonsterSession.CARD_COUNT - 1 else MonsterSession.CARD_COUNT
                assertEquals(expectedDistinct, cards.toSet().size, "$p: $cards")
            }
        }
        // 카드(1~9)로 만들 수 없는 식은 조용히 고치지 않고 거절한다(Swift 는 precondition)
        assertFailsWith<IllegalArgumentException> { MonsterSession.makeCards(Problem(12, 3), rng(1)) }
    }

    @Test fun selectAndDeselectDoNotJudge() {
        val s = session(2)
        val q = s.current
        assertEquals(CardTapResult.Selected, s.tap(q.pairA))
        assertEquals(q.pairA, s.selected)
        assertEquals(RoundPhase.RUNNING, s.clock.phase, "첫 카드 고르기부터 시간 시작")
        assertEquals(CardTapResult.Deselected, s.tap(q.pairA))
        assertNull(s.selected)
        assertTrue(s.attempts.isEmpty())
    }

    @Test fun correctPairDefeatsOnceAndNextMonsterIsImmediate() {
        val s = session(4)
        val q = s.current
        s.tap(q.pairB)
        val r = s.tap(q.pairA)
        val attempt = (r as? CardTapResult.Judged)?.attempt ?: fail("채점돼야 함: $r")
        assertTrue(attempt.correct)
        assertEquals(q.id, attempt.questionId)
        assertEquals(1, s.defeated)
        assertEquals(q.id + 1, s.current.id)
        assertEquals(2, s.monstersIssued)
        assertNull(s.selected)
    }

    /** 예: 12 = 3×4 = 2×6. 그런 카드 배치가 나오는 시드를 찾아 확인한다. 기록은 몬스터의 목표 식으로 남는다 */
    @Test fun otherPairWithSameProductIsAcceptedAndRecordedAsTarget() {
        for (seed in 0 until 2000) {
            val s = session(seed)
            val q = s.current
            for (i in 0 until MonsterSession.CARD_COUNT) {
                for (j in (i + 1) until MonsterSession.CARD_COUNT) {
                    val isShownPair = (i == q.pairA && j == q.pairB) || (i == q.pairB && j == q.pairA)
                    val isSamePair = setOf(q.cards[i], q.cards[j]) == setOf(q.target.a, q.target.b)
                    if (isShownPair || isSamePair || q.cards[i] * q.cards[j] != product(q.target)) continue
                    s.tap(i)
                    val attempt = s.tap(j).attempt
                    assertEquals(true, attempt?.correct, "${q.target}: ${q.cards[i]}×${q.cards[j]}")
                    assertEquals(1, s.defeated)
                    assertEquals(q.cards[i], attempt?.pickA)
                    assertEquals(q.cards[j], attempt?.pickB)
                    assertEquals(q.target.a, attempt?.answerRecord?.a, "학습 기록은 목표 식")
                    assertEquals(q.target.b, attempt?.answerRecord?.b)
                    assertEquals(GivenAnswer.Number(product(q.target)), attempt?.answerRecord?.given)
                    return
                }
            }
        }
        fail("다른 정답 조합이 나오는 시드를 찾지 못함")
    }

    @Test fun wrongPairRevealsThenSameMonsterWithReshuffledCards() {
        val s = session(6)
        val q = s.current
        s.tap(q.pairA)
        val r = s.tap(wrongCard(q))
        assertEquals(false, r.attempt?.correct)
        assertEquals(1, s.wrongCount)
        assertEquals(ActionReject.REVEALING, s.tap(0).reject)
        assertNull(s.selected, "공개 중 탭은 선택도 하지 않음")
        s.tick(RoundClock.REVEAL_SEC + 0.01)
        assertNotEquals(q.id, s.current.id)
        assertEquals(q.target, s.current.target, "같은 몬스터 재도전")
        assertEquals(1, s.monstersIssued)
    }

    @Test fun tapsAfterTimeUpAreRejectedAndSelectionDoesNotLeak() {
        val s = session(8)
        s.tap(s.current.pairA)
        s.tick(RoundClock.DURATION_SEC - 0.0001)
        val q = s.current
        assertEquals(true, s.tap(q.pairB).attempt?.correct, "종료 직전 두 번째 카드는 채점")
        s.tick(0.001)
        assertEquals(ActionReject.OVER, s.tap(s.current.pairA).reject)
        assertEquals(1, s.defeated)
    }

    @Test fun invalidCardIsRejectedAndDoesNotStartClock() {
        val s = session(9)
        assertEquals(ActionReject.INVALID, s.tap(-1).reject)
        assertEquals(ActionReject.INVALID, s.tap(MonsterSession.CARD_COUNT).reject)
        assertEquals(RoundPhase.WAITING, s.clock.phase)
    }

    // 학습 기록 연결

    @Test fun answerRecordsForLearning() {
        val s = session(10)
        s.tick(1.0)  // 첫 동작 전 1초
        val first = s.current.target
        defeat(s)
        s.tick(0.25)
        val second = s.current.target
        val wrong = miss(s) ?: fail("오답 기록이 있어야 함")  // 탭 뒤 0.61초 공개
        s.tick(0.5)
        defeat(s)
        assertEquals(
            listOf(
                AnswerRecord(first.a, first.b, correct = true, ms = 1000, given = GivenAnswer.Number(product(first))),
                AnswerRecord(second.a, second.b, correct = false, ms = 250, given = GivenAnswer.Number(wrong.given)),
                AnswerRecord(second.a, second.b, correct = true, ms = 500, given = GivenAnswer.Number(product(second))),
            ),
            s.answerRecords,
        )
        assertNotEquals(product(second), wrong.given)
    }

    /** 쉬는 창 = 화면이 tick 을 부르지 않음. 그동안 60초 시계·응답 시간이 멈춘다(원본 PausePlayTests) */
    @Test fun pauseFreezesClockAndResponseTime() {
        val s = session(11)
        s.tap(s.current.pairA)  // 카드 고르기 → 60초 시작
        s.tick(0.2)
        assertEquals(RoundPhase.RUNNING, s.clock.phase)
        val remaining = s.clock.remaining
        s.tick(0.0)  // (쉬는 중: tick 없음 — 첫 프레임 dt 0 도 아무것도 바꾸지 않음)
        assertEquals(remaining, s.clock.remaining, "쉬는 동안 시계 멈춤")
        s.tick(0.5)  // 계속 하기
        assertTrue(s.clock.remaining < remaining)
        assertEquals(700, s.tap(s.current.pairB).attempt?.ms)
    }

    // 무작위 채점 무결성

    /** 원본 RandomPlay: 판마다 60초 끝까지, 정답 쪽을 조금 더 자주 눌러 처치·오답이 둘 다 충분히 나오게 */
    @Test fun randomPlayNoDuplicateOrMissingGrades() {
        for (seed in 1..5) {
            val driver = rng(seed * 7919)
            var judged = 0
            for (round in 0 until 15) {
                val s = session(seed * 100 + round)
                var roundJudged = 0
                while (s.clock.phase != RoundPhase.OVER) {
                    val card = if (driver() < 0.5) {
                        if (GameRandom.index(2, driver) == 0) s.current.pairA else s.current.pairB
                    } else {
                        -1 + GameRandom.index(8, driver)  // -1…6 (없는 카드 포함)
                    }
                    if (s.tap(card) is CardTapResult.Judged) {
                        judged += 1
                        roundJudged += 1
                    }
                    s.tick(driver() * 0.2)
                }
                assertEquals(roundJudged, s.attempts.size)
                assertIntegrity(s)
            }
            assertTrue(judged > 1000, "seed $seed")
        }
    }

    /** 고정 프레임(1/15초)에 한 프레임 0~3번 탭, 1,000회 이상 채점(원본 PrototypePlayTests.ThousandTaps) */
    @Test fun framePlayThousandJudgementsNoDuplicateOrMissing() {
        for (seed in listOf(11, 7, 2026)) {
            val driver = rng(seed)
            var judged = 0
            var rounds = 0
            var rejected = 0
            var s = session(seed + 900)
            while (judged < 1000) {
                val before = s.attempts.size
                var counted = 0
                repeat(GameRandom.index(4, driver)) {
                    val q = s.current
                    val card = if (driver() < 0.6) {
                        if (s.selected == q.pairA) q.pairB else q.pairA
                    } else {
                        GameRandom.index(MonsterSession.CARD_COUNT, driver)
                    }
                    val r = s.tap(card)
                    if (r is CardTapResult.Judged) counted += 1
                    r.reject?.let {
                        assertEquals(ActionReject.REVEALING, it, "거절은 오답 공개 중일 때만")
                        rejected += 1
                    }
                }
                assertEquals(counted, s.attempts.size - before)
                judged += counted
                s.tick(1.0 / 15.0)
                if (s.clock.phase == RoundPhase.OVER) {
                    assertIntegrity(s)
                    rounds += 1
                    s = session(seed + 900 + rounds)
                }
            }
            assertTrue(rounds >= 1, "seed $seed")
            assertTrue(rejected > 0, "seed $seed")
        }
    }

    // 골든 벡터 (iOS MonsterTests.swift 와 같은 시드·같은 기대값 — 한쪽을 바꾸면 양쪽을 함께)
    //
    // 스크립트: "c" = pairA → pairB 순서로 누름(처치), "w" = pairA → wrongCard(pairA 와 곱이 목표가 아닌 가장 앞 자리)
    // → tick(0.61) 으로 공개 끝. 기록: 생성 직후 + 글자마다 그 뒤의 지금 몬스터를 "단x곱하는수 [카드 6장] pairA,pairB" 로.
    // 기대값은 C# 원본을 그대로 옮긴 참조 구현과 대조해 확인했다(iOS 쪽 기록).

    private fun trace(
        seed: Int,
        order: ProblemOrder = ProblemOrder.RANDOM,
        table: Int? = null,
        script: String,
    ): Pair<List<String>, MonsterSession> {
        val s = MonsterSession(order, table, rng(seed))
        fun line(): String {
            val q = s.current
            return "${q.target.a}x${q.target.b} ${q.cards} ${q.pairA},${q.pairB}"
        }
        val lines = mutableListOf(line())
        for (ch in script) {
            if (ch == 'c') defeat(s) else miss(s)
            lines.add(line())
        }
        return lines to s
    }

    private fun stats(s: MonsterSession): List<Int> =
        listOf(s.defeated, s.wrongCount, s.stars, s.streak, s.bestStreak, s.monstersIssued)

    @Test fun goldenSeed42() {
        val (questions, s) = trace(seed = 42, script = "ccccwcc")
        assertEquals(
            listOf(
                "6x8 [8, 4, 6, 7, 9, 5] 2,0", "9x1 [6, 2, 8, 5, 1, 9] 5,4", "6x6 [9, 7, 6, 5, 6, 4] 4,2",
                "4x5 [3, 7, 4, 8, 6, 5] 2,5", "8x8 [7, 8, 8, 3, 4, 9] 2,1",
                "8x8 [9, 8, 3, 2, 7, 8] 5,1",  // 오답 뒤 같은 몬스터, 카드만 다시
                "9x5 [7, 9, 4, 6, 8, 5] 1,5", "6x6 [6, 6, 7, 8, 5, 9] 1,0",
            ),
            questions,
        )
        assertEquals(listOf(48, 9, 36, 20, 56, 64, 45), s.attempts.map { it.given })
        assertEquals(listOf(6, 1, 6, 2, 4, 7), stats(s))
    }

    @Test fun goldenSeed2026() {
        val (questions, s) = trace(seed = 2026, script = "cccccc")
        assertEquals(
            listOf(
                "5x6 [7, 4, 6, 5, 9, 2] 3,2", "6x9 [5, 6, 7, 8, 9, 2] 1,4", "8x3 [3, 2, 4, 7, 8, 9] 4,0",
                "9x4 [4, 3, 1, 9, 8, 5] 3,0", "2x3 [6, 1, 9, 4, 2, 3] 4,5", "8x1 [1, 2, 8, 3, 7, 9] 2,0",
                "3x5 [3, 4, 7, 5, 6, 2] 0,3",
            ),
            questions,
        )
        assertEquals(listOf(6, 0, 8, 6, 6, 7), stats(s))
    }

    // 순서대로 (2.9.0 시작 창 — 단 × 1 → × 9, iOS MonsterTests.swift 와 같은 시드·같은 기대값)
    //
    // 기존 "단 지정 + 가중 무작위"(goldenTable7Seed42·tableLimitsTargetsToThatTable)는 화면이 더 만들지 않는 조합이라
    // 세션에서 지웠다(무작위는 단 지정을 받지 않음). "단을 지정하면 목표가 그 단 안에서만"은 아래 순서대로 + 단 지정 테스트가 맡는다.

    /** 단 지정이면 목표가 그 단 × 1 → × 9 차례이고 × 9 다음은 그 단 × 1 로 다시 — 몇 바퀴를 돌아도 그 단뿐, 1~9 모두 나온다 */
    @Test fun sequentialFixedTableGoesOneToNineThenWraps() {
        for (table in MonsterSession.TABLES) {
            val s = MonsterSession(ProblemOrder.SEQUENTIAL, table, rng(100 + table))
            assertEquals(table, s.table)
            assertEquals(ProblemOrder.SEQUENTIAL, s.order)
            val seen = mutableSetOf<Int>()
            for (i in 0 until 30) {
                assertEquals(Problem(table, i % 9 + 1), s.current.target, "${table}단 ${i}번째")
                seen.add(s.current.target.b)
                defeat(s)
            }
            assertEquals((1..9).toSet(), seen, "${table}단")
            assertEquals(31, s.monstersIssued)
            assertIntegrity(s)
        }
    }

    /** 단 없음: 시작 단은 2~9 중 무작위(× 1 부터), × 9 다음은 직전과 다른 2~9단 × 1. 시드마다 시작 단이 고루 나온다 */
    @Test fun sequentialWithoutTableSwitchesToDifferentTableAfterNine() {
        val starts = mutableSetOf<Int>()
        for (seed in 0 until 40) {
            val s = MonsterSession(ProblemOrder.SEQUENTIAL, random = rng(seed))
            assertNull(s.table)
            assertEquals(1, s.current.target.b)
            starts.add(s.current.target.a)
            var previousTable = s.current.target.a
            for (round in 0 until 4) {
                for (b in 1..9) {
                    assertEquals(Problem(previousTable, b), s.current.target, "seed $seed 바퀴 $round")
                    defeat(s)
                }
                val next = s.current.target
                assertEquals(1, next.b)
                assertTrue(next.a in MonsterSession.TABLES)
                assertNotEquals(previousTable, next.a, "× 9 다음은 다른 단 (seed $seed)")
                previousTable = next.a
            }
            assertIntegrity(s)
        }
        assertEquals(MonsterSession.TABLES.toSet(), starts, "시작 단 2~9 가 모두 나옴")
    }

    /** 순서대로에서도 틀리면 같은 목표(같은 몬스터)를 카드만 섞어 다시 — 다음 곱하는 수로 넘어가지 않는다 */
    @Test fun sequentialWrongKeepsSameTarget() {
        val s = MonsterSession(ProblemOrder.SEQUENTIAL, table = 4, random = rng(5))
        defeat(s)   // 4×1 → 4×2
        val before = s.current
        val issued = s.monstersIssued
        val attempt = miss(s)
        assertEquals(Problem(4, 2), attempt?.problem)
        assertEquals(Problem(4, 2), s.current.target)
        assertNotEquals(before.id, s.current.id)
        assertEquals(issued, s.monstersIssued, "같은 몬스터 재출제는 새 몬스터로 세지 않음")
        defeat(s)
        assertEquals(Problem(4, 3), s.current.target)
    }

    /**
     * 다음 목표 규칙(난수 소비 포함): × 9 가 아니면 난수 없이 다음 곱하는 수, × 9 다음 단 지정은 그 단 × 1(난수 없음),
     * 단 없음은 index(8) 하나 — 직전 단이 나오면 다시 뽑는다
     */
    @Test fun nextInOrderRuleAndRandomUse() {
        var used = 0
        fun scripted(values: List<Double>): () -> Double {
            val rest = ArrayDeque(values)
            return {
                used += 1
                rest.removeFirst()
            }
        }
        assertEquals(Problem(7, 4), MonsterSession.nextInOrder(Problem(7, 3), table = null, random = scripted(emptyList())))
        assertEquals(Problem(7, 1), MonsterSession.nextInOrder(Problem(7, 9), table = 7, random = scripted(emptyList())))
        assertEquals(0, used)
        // 0.6 → 2 + 4 = 6단(직전 단과 같음 → 다시), 0.1 → 2 + 0 = 2단
        assertEquals(Problem(2, 1), MonsterSession.nextInOrder(Problem(6, 9), table = null, random = scripted(listOf(0.6, 0.1))))
        assertEquals(2, used)
    }

    /** 순서대로 판(단 지정·없음)도 무작위로 누르면 채점 중복·누락 없음, × 1 목표의 카드도 규칙대로 */
    @Test fun sequentialRandomPlayNoDuplicateOrMissingGrades() {
        for (seed in 1..4) {
            val driver = rng(seed * 104_729)
            var judged = 0
            for (round in 0 until 10) {
                val tables = listOf<Int?>(null, 2, 7, 9, null)
                val s = MonsterSession(ProblemOrder.SEQUENTIAL, tables[round % tables.size], rng(seed * 1000 + round))
                var roundJudged = 0
                while (s.clock.phase != RoundPhase.OVER) {
                    val card = if (driver() < 0.5) {
                        if (GameRandom.index(2, driver) == 0) s.current.pairA else s.current.pairB
                    } else {
                        -1 + GameRandom.index(8, driver)  // -1…6 (없는 카드 포함)
                    }
                    if (s.tap(card) is CardTapResult.Judged) {
                        judged += 1
                        roundJudged += 1
                    }
                    s.tick(driver() * 0.2)
                }
                assertEquals(roundJudged, s.attempts.size)
                assertIntegrity(s)
                // 맞힌 목표만 차례로 보면 곱하는 수가 1씩 늘고(× 9 다음 × 1), 단 지정이면 그 단뿐
                val defeatedTargets = s.attempts.filter { it.correct }.map { it.problem }
                for ((prev, next) in defeatedTargets.zipWithNext()) {
                    assertEquals(if (prev.b == 9) 1 else prev.b + 1, next.b, "$prev → $next")
                    s.table?.let { assertEquals(it, next.a) }
                    if (prev.b != 9) assertEquals(prev.a, next.a)
                }
            }
            assertTrue(judged > 500, "seed $seed")
        }
    }

    /** 순서대로 · 단 없음 골든: 시작 단 무작위 → × 1 … × 9(× 9 에서 한 번 틀림) → 다른 단 × 1 (참조 구현과 대조) */
    @Test fun goldenSequentialSeed42() {
        val (questions, s) = trace(seed = 42, order = ProblemOrder.SEQUENTIAL, script = "ccccccccwccc")
        assertEquals(
            listOf(
                "6x1 [1, 9, 6, 7, 2, 5] 2,0", "6x2 [5, 7, 2, 1, 3, 6] 5,2", "6x3 [2, 5, 4, 7, 3, 6] 5,4",
                "6x4 [2, 7, 5, 6, 4, 3] 3,4", "6x5 [7, 1, 5, 8, 4, 6] 5,2", "6x6 [7, 6, 2, 5, 3, 6] 1,5",
                "6x7 [7, 5, 8, 6, 1, 2] 3,0", "6x8 [6, 5, 7, 8, 9, 4] 0,3", "6x9 [6, 8, 9, 7, 1, 5] 0,2",
                "6x9 [5, 8, 7, 6, 9, 4] 3,4",  // 오답 뒤 같은 몬스터, 카드만 다시
                "9x1 [7, 8, 9, 4, 1, 2] 2,4", "9x2 [8, 3, 1, 9, 2, 6] 3,4", "9x3 [4, 6, 2, 8, 3, 9] 5,4",
            ),
            questions,
        )
        assertEquals(listOf(6, 12, 18, 24, 30, 36, 42, 48, 48, 54, 9, 18), s.attempts.map { it.given })
        assertEquals(listOf(11, 1, 13, 3, 8, 12), stats(s))
        assertNull(s.table)
    }

    /** 순서대로 · 7단 골든: 7×1 … 7×9 → 7×1 로 다시 (참조 구현과 대조) */
    @Test fun goldenSequentialTable7Seed42() {
        val (questions, s) = trace(seed = 42, order = ProblemOrder.SEQUENTIAL, table = 7, script = "ccccccccccc")
        assertEquals(
            listOf(
                "7x1 [1, 8, 6, 4, 7, 2] 4,0", "7x2 [3, 7, 8, 6, 2, 1] 1,4", "7x3 [4, 2, 7, 8, 3, 6] 2,4",
                "7x4 [3, 8, 6, 7, 5, 4] 3,5", "7x5 [8, 7, 6, 4, 5, 3] 1,4", "7x6 [2, 8, 5, 7, 1, 6] 3,5",
                "7x7 [3, 7, 7, 9, 6, 8] 2,1", "7x8 [8, 6, 7, 2, 5, 9] 2,0", "7x9 [9, 7, 6, 5, 8, 4] 1,0",
                "7x1 [6, 2, 1, 7, 4, 8] 3,2", "7x2 [3, 6, 1, 2, 7, 8] 4,3", "7x3 [8, 4, 6, 2, 3, 7] 5,4",
            ),
            questions,
        )
        assertEquals(listOf(7, 14, 21, 28, 35, 42, 49, 56, 63, 7, 14), s.attempts.map { it.given })
        assertEquals(listOf(11, 0, 15, 11, 11, 12), stats(s))
        assertEquals(7, s.table)
    }

    /** 무작위 순서는 원본 출제 그대로 — 단 지정이 없고 목표가 2~9단에 고루 퍼진다 */
    @Test fun randomOrderHasNoTable() {
        val s = session(1)
        assertEquals(ProblemOrder.RANDOM, s.order)
        assertNull(s.table)
        val tables = mutableSetOf<Int>()
        repeat(60) {
            tables.add(s.current.target.a)
            defeat(s)
        }
        assertEquals(MonsterSession.TABLES.toSet(), tables)
    }

    /**
     * 10단 이상·1단은 카드 1~9 로 만들 수 없거나 범위 밖 — 진입 화면이 막아야 하므로 조용히 넘기지 않고 던진다(순서대로 기준).
     * 무작위 순서는 단 지정을 받지 않는다(화면은 무작위면 고른 단을 넘기지 않음)
     */
    @Test fun tableOutOfRangeIsRejected() {
        assertFailsWith<IllegalArgumentException> { MonsterSession(ProblemOrder.SEQUENTIAL, table = 10, random = rng(1)) }
        assertFailsWith<IllegalArgumentException> { MonsterSession(ProblemOrder.SEQUENTIAL, table = 1, random = rng(1)) }
        assertFailsWith<IllegalArgumentException> { MonsterSession(ProblemOrder.RANDOM, table = 7, random = rng(1)) }
        assertFailsWith<IllegalArgumentException> { MonsterSession.nextInOrder(Problem(10, 1), table = null, random = rng(1)) }
        assertFailsWith<IllegalArgumentException> { MonsterSession.nextInOrder(Problem(6, 3), table = 7, random = rng(1)) }
    }
}
