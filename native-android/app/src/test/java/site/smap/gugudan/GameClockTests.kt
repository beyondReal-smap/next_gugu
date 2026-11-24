package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.AnswerRecord
import site.smap.gugudan.core.GivenAnswer
import site.smap.gugudan.core.Problem
import site.smap.gugudan.core.SeededRandom
import site.smap.gugudan.core.games.ActionReject
import site.smap.gugudan.core.games.AttemptRecord
import site.smap.gugudan.core.games.GameRandom
import site.smap.gugudan.core.games.RoundClock
import site.smap.gugudan.core.games.RoundPhase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A·B 게임 공통 규칙 — 시계(RoundClock)·채점 기록(AttemptRecord)·시드 난수.
 * Unity 시험판 PrototypeSessionTests.RoundClockTests 이식 + 학습 기록 변환 + 난수 골든 벡터.
 * iOS GameClockTests.swift 와 같은 기대값 — 한쪽을 바꾸면 양쪽을 함께 바꿀 것.
 */
class GameClockTests {

    private fun rng(seed: Int): () -> Double = SeededRandom.mulberry32(seed.toUInt())

    // RoundClock (원본 RoundClockTests)

    @Test fun waitsForFirstActionThenEndsAtSixtySeconds() {
        val c = RoundClock()
        c.tick(30.0)
        assertEquals(RoundPhase.WAITING, c.phase)
        c.markAction()
        c.tick(59.5)
        assertEquals(RoundPhase.RUNNING, c.phase)
        c.tick(0.5)
        assertEquals(RoundPhase.OVER, c.phase)
        assertEquals(0.0, c.remaining)
        assertEquals(ActionReject.OVER, c.check(valid = true))
    }

    @Test fun revealLocksForRevealSecAndReportsEndOnce() {
        val c = RoundClock()
        c.markAction()
        c.beginReveal()
        assertEquals(ActionReject.REVEALING, c.check(valid = true))
        assertEquals(ActionReject.INVALID, c.check(valid = false), "없는 입력은 공개 중이어도 invalid")
        assertFalse(c.tick(RoundClock.REVEAL_SEC - 0.01))
        assertTrue(c.tick(0.02))
        assertFalse(c.tick(0.02))
        assertNull(c.check(valid = true))
    }

    @Test fun timeUpDuringRevealClearsRevealWithoutReissueSignal() {
        val c = RoundClock()
        c.markAction()
        c.tick(59.8)
        c.beginReveal()
        assertFalse(c.tick(0.5))
        assertEquals(RoundPhase.OVER, c.phase)
        assertFalse(c.isRevealing)
    }

    /** 원본은 시작 전·두 번 end() 가 예외였다 — Kotlin 은 check 라 예외로 확인할 수 있어 원본 단언도 되살린다 */
    @Test fun unlimitedNeverTimesOutUntilEnd() {
        val c = RoundClock(RoundClock.UNLIMITED)
        assertFailsWith<IllegalStateException>("시작 전 end()") { c.end() }
        c.markAction()
        c.tick(3600.0)
        assertEquals(RoundPhase.RUNNING, c.phase)
        assertEquals(Double.POSITIVE_INFINITY, c.remaining)
        c.beginReveal()
        c.end()
        assertEquals(RoundPhase.OVER, c.phase)
        assertFalse(c.isRevealing)
        assertEquals(ActionReject.OVER, c.check(valid = true))
        assertFailsWith<IllegalStateException>("두 번 end()") { c.end() }
    }

    @Test fun zeroTickChangesNothing() {
        val c = RoundClock()
        c.markAction()
        c.beginReveal()
        assertFalse(c.tick(0.0))
        assertEquals(0.0, c.elapsed)
        assertEquals(RoundClock.REVEAL_SEC, c.revealRemaining)
        assertFailsWith<IllegalArgumentException>("음수 dt") { c.tick(-0.1) }
        assertFailsWith<IllegalArgumentException>("NaN dt") { c.tick(Double.NaN) }
    }

    // AttemptRecord

    @Test fun mostMissedPicksMostFrequentTiesGoToLatest() {
        val p1 = Problem(7, 8)
        val p2 = Problem(6, 9)
        fun rec(id: Int, p: Problem, given: Int) =
            AttemptRecord(id = id, questionId = id, problem = p, given = given, elapsed = (id + 1).toDouble(), responseSec = 1.0)
        val list = listOf(rec(0, p1, 54), rec(1, p2, 55), rec(2, p1, 56), rec(3, p2, 45), rec(4, p1, 63))
        assertTrue(list[2].correct)
        assertEquals(4, AttemptRecord.mostMissed(list)?.id, "p1·p2 둘 다 2번 → 최근 것")
        assertNull(AttemptRecord.mostMissed(listOf(list[2])))
    }

    @Test fun answerRecordConversion() {
        val wrong = AttemptRecord(
            id = 0, questionId = 3, problem = Problem(7, 8), given = 54, elapsed = 12.0,
            responseSec = 1.5, pickA = 6, pickB = 9,
        )
        assertEquals(AnswerRecord(7, 8, correct = false, ms = 1500, given = GivenAnswer.Number(54)), wrong.answerRecord)
        val right = AttemptRecord(
            id = 1, questionId = 4, problem = Problem(19, 12), given = 228, elapsed = 0.0, responseSec = 0.0004,
        )
        assertEquals(AnswerRecord(19, 12, correct = true, ms = 0, given = GivenAnswer.Number(228)), right.answerRecord)
    }

    // 시드 난수 골든 벡터 (iOS 와 같은 값 — 다르면 아래 게임 골든 벡터도 전부 어긋난다)

    @Test fun mulberry32GoldenValues() {
        val r42 = rng(42)
        assertEquals(listOf(0.6011037519201636, 0.44829055899754167, 0.8524657934904099), List(3) { r42() })
        val r2026 = rng(2026)
        assertEquals(listOf(0.45540769933722913, 0.30849614599719644, 0.6611574492417276), List(3) { r2026() })
    }

    @Test fun gameRandomGoldenValues() {
        // Fisher–Yates: i = 5…1 마다 index(i + 1) 하나씩 소비
        val order = MutableList(6) { it }
        GameRandom.shuffle(order, rng(42))
        assertEquals(listOf(1, 0, 4, 5, 2, 3), order)
        val r = rng(2026)
        assertEquals(listOf(3, 2, 5, 4, 1, 1, 5, 6), List(8) { GameRandom.index(8, r) })
        // 부동소수 반올림으로 count 가 나와도 count - 1 에서 멈춤
        assertEquals(2, GameRandom.index(3) { 0.9999999999999999 })
        assertEquals(0, GameRandom.index(1) { 0.5 })
        // 0..<1 밖의 난수·0개는 규칙 쪽 버그
        assertFailsWith<IllegalArgumentException> { GameRandom.index(3) { 1.0 } }
        assertFailsWith<IllegalArgumentException> { GameRandom.index(0) { 0.5 } }
    }
}
