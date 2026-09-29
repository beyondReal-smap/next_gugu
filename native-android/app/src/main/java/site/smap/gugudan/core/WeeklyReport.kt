package site.smap.gugudan.core

import java.time.LocalDate
import java.time.format.DateTimeParseException

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
    val parentTip: String get() {
        if (solved == 0) return "이번 주는 아직 기록이 없어요. 하루 5분, 한 판부터 함께 시작해 보세요."
        val miss = topMisses.firstOrNull()
        val p = miss?.let { Problems.parseKey(it.key) }
        if (miss != null && miss.count >= 2 && p != null) {
            return "자주 헷갈리는 문제는 ${p.a} × ${KoreanReading.withCopula(p.b)}. ${p.a}단 표를 소리 내어 함께 외워 보세요."
        }
        if (activeDays < 3) return "짧게라도 매일 하는 습관이 가장 중요해요. 학습 알림을 켜 두면 도움이 돼요."
        val acc = accuracy
        if (acc != null && acc >= 90) return "정확도가 아주 좋아요! 60초 챌린지로 속도에도 도전해 보세요."
        return "꾸준히 하고 있어요. 취약 문제 복습으로 헷갈리는 문제를 정리해 보세요."
    }

    companion object {
        const val SPAN = 7
        const val TOP_MISS_LIMIT = 3
        private val WEEKDAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")   // ISO: 월=1 … 일=7

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

        /** 요일 한 글자 — "2026-09-29" → "화" */
        fun weekdayLabel(date: String): String = WEEKDAY_LABELS[parse(date).dayOfWeek.value - 1]
    }
}
