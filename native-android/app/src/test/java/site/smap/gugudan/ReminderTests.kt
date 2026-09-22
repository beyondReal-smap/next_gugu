package site.smap.gugudan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import site.smap.gugudan.core.GameState
import site.smap.gugudan.core.PlannedReminder
import site.smap.gugudan.core.ReminderPlanner
import site.smap.gugudan.core.ReminderSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// 로컬 학습 알림 규칙 (iOS ReminderTests 와 같은 사례) — 시각을 고정해 결정적으로 검증한다
class ReminderTests {
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, seoul)
    private fun ms(day: Int, hour: Int) = at(day, hour).toInstant().toEpochMilli()

    private fun state(
        lastPlayed: String = "", streak: Int = 0,
        dailyDate: String = "", dailyCorrect: Int = 0, dailyGoal: Int = 20,
        wrongPool: Map<String, Int> = emptyMap(),
    ) = GameState(
        lastPlayedDate = lastPlayed, streak = streak, dailyDate = dailyDate,
        dailyCorrect = dailyCorrect, dailyGoal = dailyGoal, wrongPool = wrongPool,
    )

    private val on = ReminderSettings(enabled = true, hour = 18, askedPermission = true)
    private fun plan(s: GameState, settings: ReminderSettings = on, now: ZonedDateTime) =
        ReminderPlanner.plan(s, settings, now)
    private fun dayOf(r: PlannedReminder): LocalDate =
        Instant.ofEpochMilli(r.fireAtEpochMs).atZone(seoul).toLocalDate()
    private fun hourOf(r: PlannedReminder) = Instant.ofEpochMilli(r.fireAtEpochMs).atZone(seoul).hour

    @Test fun disabledPlansNothing() {
        assertTrue(plan(state(lastPlayed = "2026-09-22"), on.copy(enabled = false), at(22, 10)).isEmpty())
    }

    /** 오늘 목표를 달성했으면 오늘은 보내지 않고, 내일 연속 기록을 이어가자고 알린다 */
    @Test fun goalMetTodaySkipsTodayAndNudgesStreakTomorrow() {
        val s = state(lastPlayed = "2026-09-22", streak = 3,
                      dailyDate = "2026-09-22", dailyCorrect = 20, dailyGoal = 20)
        val r = plan(s, now = at(22, 10))
        assertEquals(ms(23, 18), r.first().fireAtEpochMs)
        assertEquals("연속 기록 이어가기", r.first().title)
        assertTrue(r.first().body.contains("3일 연속"))
        assertTrue(r.first().body.contains("4일째"))
        assertFalse(r.any { dayOf(it) == LocalDate.of(2026, 9, 22) })
    }

    /** 오늘 일부만 했다면 오늘 알림에 남은 개수를 알려 준다 */
    @Test fun partialTodayRemindsRemainingCount() {
        val s = state(lastPlayed = "2026-09-21", streak = 1,
                      dailyDate = "2026-09-22", dailyCorrect = 12, dailyGoal = 20)
        val first = plan(s, now = at(22, 10)).first()
        assertEquals(ms(22, 18), first.fireAtEpochMs)
        assertEquals("오늘 목표까지 정답 8개 남았어요.", first.body)
    }

    /** 알림 시각이 이미 지났으면 내일부터 보낸다 */
    @Test fun pastFireTimeStartsTomorrow() {
        assertEquals(ms(23, 18), plan(state(lastPlayed = "2026-09-21", streak = 1), now = at(22, 19)).first().fireAtEpochMs)
    }

    /** 야간(21~08시)에는 보내지 않는다 — 범위 밖 시각은 8~20시로 당겨진다 */
    @Test fun hourIsClampedOutOfQuietHours() {
        assertEquals(ms(22, 20), plan(state(), on.copy(hour = 23), at(22, 10)).first().fireAtEpochMs)
        assertEquals(ms(22, 8), plan(state(), on.copy(hour = 5), at(22, 6)).first().fireAtEpochMs)
        assertTrue(plan(state(), on.copy(hour = 23), at(22, 10)).all { hourOf(it) in ReminderPlanner.ALLOWED_HOURS })
    }

    /** 마지막 학습 후 7일까지 매일, 그 뒤 7일 배수 날에 주 1회씩 최대 4번 */
    @Test fun dailyThenWeeklyCadence() {
        val r = plan(state(lastPlayed = "2026-09-19"), now = at(22, 10))
        val offsets = r.map { ChronoUnit.DAYS.between(LocalDate.of(2026, 9, 19), dayOf(it)).toInt() }
        assertEquals(listOf(3, 4, 5, 6, 7, 14, 21, 28, 35), offsets)
    }

    /** 5주 넘게 쉬었다면 더 보내지 않는다 */
    @Test fun stopsAfterWeeklyWindow() {
        assertTrue(plan(state(lastPlayed = "2026-08-10"), now = at(22, 10)).isEmpty())
    }

    /** 한동안 쉰 뒤에는 부담 없는 복귀 문구 */
    @Test fun comebackMessageAfterDailyWindow() {
        assertEquals("다시 시작해요", plan(state(lastPlayed = "2026-09-10"), now = at(22, 10)).first().title)
    }

    /** 약점 복습 문구는 가장 많이 틀린 식을 쓴다 */
    @Test fun weakFactMessage() {
        val bodies = plan(state(lastPlayed = "2026-09-19", wrongPool = mapOf("7x8" to 3, "6x9" to 1)),
                          now = at(22, 10)).map { it.body }
        assertTrue(bodies.contains("7 × 8, 다시 도전해 볼까요?"))
    }

    /** 학습 알림만 보낸다 — 광고성 문구(구매·결제·이용권)가 끼어들면 안 된다 */
    @Test fun noPromotionalWording() {
        val text = plan(state(lastPlayed = "2026-09-19", streak = 2, wrongPool = mapOf("7x8" to 2)),
                        now = at(22, 10)).joinToString("") { it.title + it.body }
        listOf("구매", "결제", "이용권", "할인", "프리미엄").forEach {
            assertFalse("광고성 단어 포함: $it", text.contains(it))
        }
    }

    /** 식별자는 날짜별로 고유하고 앱 접두어를 가진다 */
    @Test fun identifiersAreUniqueAndPrefixed() {
        val r = plan(state(lastPlayed = "2026-09-19"), now = at(22, 10))
        assertEquals(r.size, r.map { it.id }.toSet().size)
        assertTrue(r.all { it.id.startsWith(ReminderPlanner.ID_PREFIX) })
    }

    @Test fun hourLabels() {
        assertEquals("오전 9시", ReminderPlanner.hourLabel(9))
        assertEquals("낮 12시", ReminderPlanner.hourLabel(12))
        assertEquals("오후 6시", ReminderPlanner.hourLabel(18))
    }
}
