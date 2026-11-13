package site.smap.gugudan

import kotlinx.serialization.json.Json
import org.junit.Test
import site.smap.gugudan.core.DayLogEntry
import site.smap.gugudan.core.GameState
import site.smap.gugudan.core.PremiumConfig
import site.smap.gugudan.core.ReviewMoment
import site.smap.gugudan.core.ReviewPolicy
import site.smap.gugudan.core.ReviewPromptLog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 앱 평가 요청 시점(ReviewPolicy)과 일본 ¥500 문구 판정 — iOS ReviewPolicyTests.swift 와 같은 케이스 */
class ReviewPolicyTests {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private val good = ReviewMoment(partial = false, accuracy = 95, isNewBest = false, improvedStars = false, goalReached = false)

    /** 끝까지 푼 판 [sessions]개, 서로 다른 날 [days]일을 쓴 상태 */
    private fun state(sessions: Int, days: Int) = GameState(
        recentAccuracy = List(sessions) { 90 },
        dayLog = List(days) { DayLogEntry(date = "2026-11-0${it + 1}", correct = 10, wrong = 1) },
    )

    @Test fun asksAfterEnoughUseOnAGoodRound() {
        assertTrue(ReviewPolicy.shouldAsk(good, state(4, 2), ReviewPromptLog(), "2.9.0", now))
    }

    @Test fun neverAsksOnPartialOrPoorRound() {
        assertFalse(ReviewPolicy.isHappyMoment(good.copy(partial = true)))
        val poor = good.copy(accuracy = 70)
        assertFalse(ReviewPolicy.isHappyMoment(poor))
        // 정확도가 낮아도 신기록·별 상승·오늘 목표 달성은 잘 해낸 순간이다
        assertTrue(ReviewPolicy.isHappyMoment(poor.copy(isNewBest = true)))
        assertTrue(ReviewPolicy.isHappyMoment(poor.copy(improvedStars = true)))
        assertTrue(ReviewPolicy.isHappyMoment(poor.copy(goalReached = true)))
    }

    @Test fun waitsUntilEnoughSessionsAndDays() {
        val log = ReviewPromptLog()
        assertFalse(ReviewPolicy.shouldAsk(good, state(3, 3), log, "2.9.0", now))
        assertFalse(ReviewPolicy.shouldAsk(good, state(10, 1), log, "2.9.0", now))
        assertFalse(ReviewPolicy.shouldAsk(good, state(10, 0), log, "2.9.0", now))
    }

    @Test fun activeDaysIgnoresEmptyAndDuplicateDates() {
        val s = GameState(dayLog = listOf(
            DayLogEntry(date = "2026-11-01", correct = 3),
            DayLogEntry(date = "2026-11-01", correct = 1),
            DayLogEntry(date = "2026-11-02"),
        ))
        assertEquals(1, ReviewPolicy.activeDays(s))
    }

    @Test fun oncePerVersionAndSpacedOut() {
        val s = state(5, 3)
        val asked = ReviewPolicy.recordAsk(ReviewPromptLog(), "2.9.0", now)
        assertEquals(1, asked.askCount)
        // 같은 버전에서는 다시 묻지 않는다
        assertFalse(ReviewPolicy.shouldAsk(good, s, asked, "2.9.0", now + 365 * day))
        // 새 버전이어도 간격(60일) 안이면 묻지 않는다
        assertFalse(ReviewPolicy.shouldAsk(good, s, asked, "2.10.0", now + 59 * day))
        assertTrue(ReviewPolicy.shouldAsk(good, s, asked, "2.10.0", now + 61 * day))
    }

    @Test fun promptLogRoundTrip() {
        val log = ReviewPromptLog(lastAskedAtMillis = now, lastAskedVersion = "2.9.0", askCount = 2)
        val json = Json.encodeToString(ReviewPromptLog.serializer(), log)
        assertEquals(log, Json.decodeFromString(ReviewPromptLog.serializer(), json))
    }

    @Test fun oneCoinOnlyForExactly500Yen() {
        assertTrue(PremiumConfig.isOneCoinPrice(500_000_000L, "JPY"))
        assertFalse(PremiumConfig.isOneCoinPrice(600_000_000L, "JPY"))   // 가격이 바뀌면 문구도 원래대로
        assertFalse(PremiumConfig.isOneCoinPrice(500_000_000L, "KRW"))
        assertFalse(PremiumConfig.isOneCoinPrice(4_400_000_000L, "KRW"))
    }
}
