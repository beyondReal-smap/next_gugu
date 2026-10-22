package site.smap.gugudan.core

import site.smap.gugudan.R
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

// 보호자 주간 리포트 (iOS Core/WeeklyReport.swift 이식) — GameState.dayLog(최근 56일 링버퍼)로
// 최근 7일을 요약한다. 서버 없이 기기 기록만으로 만든다 (웹 lib/report/weekly.ts 대응).
// 날짜는 "YYYY-MM-DD" 문자열로 다뤄 기기 시간대와 무관하게 하루씩 센다.

data class WeeklyDay(
    val date: String,   // YYYY-MM-DD
    val correct: Int,
    val wrong: Int,
    val msSum: Int,
) {
    val solved: Int get() = correct + wrong
}

data class MissCount(val key: String, val count: Int)   // key = "7x8"

data class WeeklyReport(
    /** 7일 — 6일 전 → 오늘 순 */
    val days: List<WeeklyDay>,
    /** 직전 7일(13일 전 ~ 7일 전) 정답 수 — 지난주 대비 비교용 */
    val prevCorrect: Int,
    /** 이번 7일 자주 틀린 문제 (많이 틀린 순, 최대 3개) */
    val topMisses: List<MissCount>,
) {
    val correct: Int get() = days.sumOf { it.correct }
    val wrong: Int get() = days.sumOf { it.wrong }
    val solved: Int get() = correct + wrong
    val studyMs: Int get() = days.sumOf { it.msSum }
    /** 1문제 이상 푼 날 수 */
    val activeDays: Int get() = days.count { it.solved > 0 }
    /** 정확도(%) — 푼 문제가 없으면 null */
    val accuracy: Int? get() = if (solved > 0) Math.round(correct * 100.0 / solved).toInt() else null
    val correctDelta: Int get() = correct - prevCorrect

    /** 보호자에게 건네는 한마디 — 이번 주 기록에서 가장 도움이 될 행동 하나를 고른다 */
    fun parentTip(strings: Strings): String {
        if (solved == 0) return strings.get(R.string.no_activity_yet_this_week_start_together)
        val miss = topMisses.firstOrNull()
        val p = miss?.let { Problems.parseKey(it.key) }
        if (miss != null && miss.count >= 2 && p != null) {
            // 한국어는 식 끝에 받침 맞춘 서술격 조사("7 × 8이에요")를 붙인다 — 다른 언어는 식만
            val fact = if (strings.language == AppLanguage.KO) "${p.a} × ${KoreanReading.withCopula(p.b)}" else "${p.a} × ${p.b}"
            return strings.get(R.string.the_trickiest_one_this_week_is_try, fact, p.a)
        }
        if (activeDays < 3) return strings.get(R.string.a_short_daily_habit_matters_most_turning)
        val acc = accuracy
        if (acc != null && acc >= 90) return strings.get(R.string.great_accuracy_try_the_60_second)
        return strings.get(R.string.nice_consistency_use_review_weak_spots)
    }

    companion object {
        const val SPAN = 7
        const val TOP_MISS_LIMIT = 3
        private val WEEKDAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")   // ISO: 월=1 … 일=7  // i18n:skip

        /** today 를 마지막 날로 하는 최근 7일 리포트 */
        fun build(dayLog: List<DayLogEntry>, today: String): WeeklyReport {
            // 같은 날짜가 둘 이상 저장돼 있으면(손상된 저장본) 합쳐서 센다 — 한쪽을 버리면 기록이 사라진다
            val byDate = mutableMapOf<String, DayLogEntry>()
            for (e in Commit.normalizeDayLog(dayLog)) {
                val prev = byDate[e.date]
                byDate[e.date] = if (prev == null) e else prev.copy(
                    correct = prev.correct + e.correct,
                    wrong = prev.wrong + e.wrong,
                    msSum = prev.msSum + e.msSum,
                    misses = (prev.misses.keys + e.misses.keys).associateWith {
                        (prev.misses[it] ?: 0) + (e.misses[it] ?: 0)
                    },
                )
            }

            val dates = (SPAN - 1 downTo 0).map { shift(today, -it) }
            val days = dates.map { date ->
                val e = byDate[date]
                WeeklyDay(date, e?.correct ?: 0, e?.wrong ?: 0, e?.msSum ?: 0)
            }
            val prevCorrect = (SPAN until SPAN * 2).sumOf { byDate[shift(today, -it)]?.correct ?: 0 }

            val misses = mutableMapOf<String, Int>()
            for (date in dates) {
                byDate[date]?.misses?.forEach { (k, v) -> misses[k] = (misses[k] ?: 0) + v }
            }
            val topMisses = misses.map { MissCount(it.key, it.value) }
                .sortedWith(compareByDescending<MissCount> { it.count }.thenBy { it.key })
                .take(TOP_MISS_LIMIT)

            return WeeklyReport(days, prevCorrect, topMisses)
        }

        private fun parse(date: String): LocalDate = try {
            LocalDate.parse(date)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("날짜 형식이 아님: $date", e)
        }

        /** "2026-09-29" 를 days 만큼 옮긴 날짜 */
        fun shift(date: String, days: Int): String = Commit.todayStr(parse(date).plusDays(days.toLong()))

        /** 요일 한 글자 — "2026-09-29" → "화" (en "T", ja "火"). 한국어는 기존 표 그대로, 그 밖은 그 언어의 가장 짧은 요일 */
        fun weekdayLabel(
            date: String,
            language: AppLanguage = AppLanguage.current,
            locale: Locale = AppLanguage.formatLocale,
        ): String {
            val day = parse(date).dayOfWeek
            if (language == AppLanguage.KO) return WEEKDAY_LABELS[day.value - 1]
            return day.getDisplayName(TextStyle.NARROW_STANDALONE, locale)
        }

        /** 요일 전체 이름 (화면 낭독용) — "화요일" / "Tuesday" / "火曜日" (iOS weekdayName 과 같다) */
        fun weekdayName(date: String, locale: Locale = AppLanguage.formatLocale): String =
            parse(date).dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale)
    }
}
