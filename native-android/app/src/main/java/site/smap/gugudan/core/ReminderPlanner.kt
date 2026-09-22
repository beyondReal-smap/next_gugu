package site.smap.gugudan.core

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

// 로컬 학습 알림 계획 (iOS Core/ReminderPlanner.swift 이식) — 무엇을 언제 보낼지 계산만 한다.
// 예약·취소는 services/LocalNotifications 가 맡는다. 시각을 주입받는 순수 로직이라 단위 테스트로 규칙을 고정한다.
//
// 규칙 (2026-09-22 합의)
// - 하루 1회, 사용자가 고른 시각. 야간(21~08시)에는 보내지 않으므로 8~20시로 제한한다.
// - 오늘 목표를 달성했으면 오늘 알림은 보내지 않는다. 일부만 했으면 남은 개수를 알려 준다.
// - 마지막 학습 후 7일까지는 매일, 그 뒤로는 주 1회씩 최대 4번. 계속 보내면 알림을 끈다.
// - 학습 알림만 보낸다. 이용권 구매 권유 같은 광고성 문구는 넣지 않는다
//   (정보통신망법 §50 광고성 정보는 별도 사전 동의 대상, Google Play 가족 정책).

@Serializable
data class ReminderSettings(
    val enabled: Boolean = false,
    /** 알림 시각(시). ReminderPlanner.ALLOWED_HOURS 범위로 제한된다. */
    val hour: Int = ReminderPlanner.DEFAULT_HOUR,
    /** 결과 화면에서 권한을 한 번이라도 물었는지 — 거절한 사용자에게 반복해서 묻지 않는다 */
    val askedPermission: Boolean = false,
)

data class PlannedReminder(
    val id: String,
    val fireAtEpochMs: Long,
    val title: String,
    val body: String,
)

object ReminderPlanner {
    const val DEFAULT_HOUR = 18
    /** 야간 발송 금지(21~08시)를 지키는 선택 가능 범위 */
    val ALLOWED_HOURS = 8..20
    /** 마지막 학습 후 이 기간까지는 매일 보낸다 */
    const val DAILY_WINDOW_DAYS = 7
    /** 그 뒤로 주 1회씩 보내는 최대 횟수 */
    const val WEEKLY_MAX_COUNT = 4
    /** 예약 식별자 접두어 — 이 앱이 예약한 알림만 골라 교체하기 위함 */
    const val ID_PREFIX = "gugu.reminder."

    fun clampHour(hour: Int): Int = hour.coerceIn(ALLOWED_HOURS)

    /** "오전 9시" · "낮 12시" · "오후 6시" */
    fun hourLabel(hour: Int): String = when {
        hour == 12 -> "낮 12시"
        hour > 12 -> "오후 ${hour - 12}시"
        else -> "오전 ${hour}시"
    }

    /** 앞으로 보낼 알림 목록. 앱을 열거나 학습할 때마다 통째로 다시 계산해 교체한다. */
    fun plan(state: GameState, settings: ReminderSettings, now: ZonedDateTime): List<PlannedReminder> {
        if (!settings.enabled) return emptyList()

        val hour = clampHour(settings.hour)
        val today = now.toLocalDate()
        val todayKey = Commit.todayStr(today)
        val studiedToday = state.dailyDate == todayKey && state.dailyCorrect > 0
        val goalMetToday = studiedToday && state.dailyCorrect >= state.dailyGoal
        // 학습 기록이 없으면 오늘을 기준으로 삼는다
        val anchor = parseDate(state.lastPlayedDate) ?: today
        val weakFacts = weakestFacts(state.wrongPool)

        val lastWeeklyDay = DAILY_WINDOW_DAYS + WEEKLY_MAX_COUNT * 7
        val horizon = lastWeeklyDay + maxOf(0, ChronoUnit.DAYS.between(anchor, today).toInt())

        val result = mutableListOf<PlannedReminder>()
        for (offset in 0..horizon) {
            val day = today.plusDays(offset.toLong())
            val fire = day.atTime(hour, 0).atZone(now.zone)
            if (!fire.isAfter(now)) continue
            if (offset == 0 && goalMetToday) continue

            val since = maxOf(0, ChronoUnit.DAYS.between(anchor, day).toInt())
            if (since > lastWeeklyDay) break
            // 7일이 지나면 마지막 학습일 기준 7일 배수 날에만 보낸다
            if (since > DAILY_WINDOW_DAYS && since % 7 != 0) continue

            val (title, body) = message(
                state = state,
                isToday = offset == 0,
                studiedToday = studiedToday,
                daysSinceLastPlay = since,
                weakFacts = weakFacts,
                index = result.size,
            )
            result += PlannedReminder(
                id = ID_PREFIX + Commit.todayStr(day),
                fireAtEpochMs = fire.toInstant().toEpochMilli(),
                title = title,
                body = body,
            )
        }
        return result
    }

    private fun message(
        state: GameState,
        isToday: Boolean,
        studiedToday: Boolean,
        daysSinceLastPlay: Int,
        weakFacts: List<String>,
        index: Int,
    ): Pair<String, String> {
        // 오늘 조금 했지만 목표 전 — 남은 개수가 가장 구체적인 동기다
        if (isToday && studiedToday) {
            val remaining = maxOf(1, state.dailyGoal - state.dailyCorrect)
            return "조금만 더!" to "오늘 목표까지 정답 ${remaining}개 남았어요."
        }
        // 어제 학습했다면 오늘 하면 연속 기록이 이어진다
        if (daysSinceLastPlay == 1 && state.streak > 0) {
            return "연속 기록 이어가기" to
                "${state.streak}일 연속 중이에요! 오늘도 한 판 하면 ${state.streak + 1}일째예요."
        }
        // 한동안 쉬었다면 부담 없이 다시 시작하자는 말로 — 죄책감을 주는 문구는 쓰지 않는다
        if (daysSinceLastPlay > DAILY_WINDOW_DAYS) {
            return "다시 시작해요" to "오랜만이에요! 구구단 한 판으로 가볍게 시작해 볼까요?"
        }
        // 약점 복습과 일반 권유를 번갈아 보내 같은 문구가 반복되지 않게 한다
        if (weakFacts.isNotEmpty() && index % 2 == 0) {
            val key = weakFacts[(index / 2) % weakFacts.size]
            return "약점 복습" to "${key.replace("x", " × ")}, 다시 도전해 볼까요?"
        }
        return "오늘의 구구단" to "오늘도 한 판 어때요? 목표는 정답 ${state.dailyGoal}개예요."
    }

    /** 많이 틀린 순서(같으면 식 이름순) — 순서가 결정적이어야 테스트가 안정된다 */
    private fun weakestFacts(pool: Map<String, Int>): List<String> =
        pool.filterValues { it > 0 }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

    fun parseDate(key: String): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()
}
