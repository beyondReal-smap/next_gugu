package site.smap.gugudan.core.guardian

import site.smap.gugudan.core.Commit
import site.smap.gugudan.core.DayLogEntry
import site.smap.gugudan.core.GameState
import site.smap.gugudan.core.MissCount
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.TodayIsland
import site.smap.gugudan.core.WeeklyReport
import kotlin.math.roundToInt

// 보호자 단별 실력·리포트 순수 계산 (계획서 2-4 — iOS Core/Guardian/GuardianReport.swift 대응)
// 문구 생성 함수는 U4 에서 새 문자열 키와 함께 추가한다.

/** 최근 14일 단별 실력 상태 */
enum class SkillStatus { NO_RECORD, PRACTICING, STRONG, WEAK }

/** 최근 14일 한 단의 집계와 상태 */
data class TableSkill(
    val table: Int,
    val attempts: Int,
    val correct: Int,
    val msSum: Int,
) {
    /** attempts == 0 이면 null */
    val accuracy: Double?
        get() = if (attempts > 0) correct.toDouble() / attempts else null

    /** 한 문제 평균 응답 시간(ms). attempts == 0 이면 null */
    val avgMs: Int?
        get() = if (attempts > 0) (msSum.toDouble() / attempts).roundToInt() else null

    /**
     * 상태 판정:
     * - attempts == 0 → NO_RECORD
     * - 1 ≤ attempts < 10 → PRACTICING
     * - attempts ≥ 10, 정확도 ≥ 90% → STRONG
     * - attempts ≥ 10, 정확도 < 70% → WEAK
     * - 그 밖 → PRACTICING
     */
    val status: SkillStatus
        get() {
            if (attempts == 0) return SkillStatus.NO_RECORD
            if (attempts < TableSkills.MIN_ATTEMPTS) return SkillStatus.PRACTICING
            val acc = accuracy!!
            return when {
                acc >= TableSkills.STRONG_ACCURACY -> SkillStatus.STRONG
                acc < TableSkills.WEAK_ACCURACY -> SkillStatus.WEAK
                else -> SkillStatus.PRACTICING
            }
        }
}

object TableSkills {
    /** 오늘 포함 최근 14일 */
    const val WINDOW_DAYS = 14
    /** 판정에 필요한 최소 문제 수 */
    const val MIN_ATTEMPTS = 10
    /** 이상이면 잘해요 */
    const val STRONG_ACCURACY = 0.90
    /** 미만이면 약해요 */
    const val WEAK_ACCURACY = 0.70

    /**
     * 2..19 단 전부 반환 — 기록 없는 단은 attempts = 0 (NO_RECORD).
     * tables 가 null 인 구버전 날과 14일 밖(15일 전 이상·미래) 날은 무시한다.
     */
    fun build(dayLog: List<DayLogEntry>?, today: String): Map<Int, TableSkill> {
        val byDate = Commit.mergeDayLog(dayLog.orEmpty())
        val dates = (0 until WINDOW_DAYS).map { WeeklyReport.shift(today, -it) }
        val correctByTable = IntArray(Problems.MAX_TABLE + 1)
        val wrongByTable = IntArray(Problems.MAX_TABLE + 1)
        val msByTable = IntArray(Problems.MAX_TABLE + 1)
        for (date in dates) {
            val tables = byDate[date]?.tables ?: continue
            for ((table, tally) in tables) {
                if (table !in Problems.MIN_TABLE..Problems.MAX_TABLE) continue
                correctByTable[table] += tally.correct
                wrongByTable[table] += tally.wrong
                msByTable[table] += tally.msSum
            }
        }
        return (Problems.MIN_TABLE..Problems.MAX_TABLE).associateWith { table ->
            val c = correctByTable[table]
            val w = wrongByTable[table]
            TableSkill(table = table, attempts = c + w, correct = c, msSum = msByTable[table])
        }
    }

    /** 약한 단 목록 — 정확도 오름차순, 같으면 단 오름차순 */
    fun weak(skills: Map<Int, TableSkill>): List<Int> =
        skills.values
            .filter { it.status == SkillStatus.WEAK }
            .sortedWith(compareBy<TableSkill> { it.accuracy!! }.thenBy { it.table })
            .map { it.table }
}

/** 보호자 리포트 맨 위 한마디 분류 */
sealed interface ReportHeadline {
    /** 14일 동안 단별 기록이 한 문제도 없음 */
    data object NoRecord : ReportHeadline
    /** 약한 단 1~2개. 오늘 권하는 단 = first */
    data class Weak(val first: Int, val second: Int?) : ReportHeadline
    /** 약한 단 없음. next = 오늘의 섬 */
    data class KeepGoing(val next: Int) : ReportHeadline
    /** 약한 단 없고 오늘의 섬이 섞어서 */
    data object AllStrong : ReportHeadline
}

data class GuardianReport(
    /** 2~9단 항상 + 최근 14일 안에 기록이 있는 10~19단 */
    val rows: List<TableSkill>,
    /** 약한 단 목록 (정확도 오름차순, 동률이면 단 오름차순) */
    val weak: List<Int>,
    /** 같은 14일 자주 틀린 문제 (많이 틀린 순, 최대 5개) */
    val topMisses: List<MissCount>,
    val headline: ReportHeadline,
    /** 이번 주 월~일 요약 */
    val week: WeeklyReport,
) {
    companion object {
        const val TOP_MISS_LIMIT = 5
        private const val BASE_MAX_TABLE = 9

        fun build(state: GameState, today: String): GuardianReport {
            val skills = TableSkills.build(state.dayLog, today)
            val rows = (Problems.MIN_TABLE..Problems.MAX_TABLE).mapNotNull { table ->
                val skill = skills.getValue(table)
                if (table <= BASE_MAX_TABLE || skill.attempts > 0) skill else null
            }
            val weak = TableSkills.weak(skills)

            // 자주 틀린 문제 — 같은 14일 창, 구버전 날(tables == null)의 misses 도 집계하되 읽을 수 있는 식 키만 남긴다
            val byDate = Commit.mergeDayLog(state.dayLog)
            val windowEntries = (0 until TableSkills.WINDOW_DAYS).mapNotNull { byDate[WeeklyReport.shift(today, -it)] }
            val misses = mutableMapOf<String, Int>()
            for (entry in windowEntries) {
                for ((k, v) in entry.misses) {
                    if (Problems.parseKey(k) != null) misses[k] = (misses[k] ?: 0) + v
                }
            }
            val topMisses = misses.map { MissCount(it.key, it.value) }
                .sortedWith(compareByDescending<MissCount> { it.count }.thenBy { it.key })
                .take(TOP_MISS_LIMIT)

            val hasRecord = skills.values.any { it.attempts > 0 } ||
                windowEntries.any { it.correct + it.wrong > 0 }
            val headline = when {
                !hasRecord -> ReportHeadline.NoRecord
                weak.isNotEmpty() -> ReportHeadline.Weak(first = weak[0], second = weak.getOrNull(1))
                else -> when (val island = TodayIsland.pick(state.tableMastery, skills)) {
                    is TodayIsland.Table -> ReportHeadline.KeepGoing(next = island.table)
                    TodayIsland.Mixed -> ReportHeadline.AllStrong
                }
            }

            return GuardianReport(
                rows = rows,
                weak = weak,
                topMisses = topMisses,
                headline = headline,
                week = WeeklyReport.build(state.dayLog, today),
            )
        }
    }
}
