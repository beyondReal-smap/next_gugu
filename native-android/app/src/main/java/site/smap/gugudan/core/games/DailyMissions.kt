package site.smap.gugudan.core.games

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException

// 오늘의 미션 + 보물상자 + 이번 주 도장 (2.9.0, iOS DailyMissions.swift 와 같은 규칙 — .claude/plans/daily-missions.md)
// 날마다 바뀌는 미션 3개 → 다 하면 보물상자(별 +10) → 이번 주 도장 1칸. 무료·이용권 모두 같다.
// 미션은 날짜 문자열만으로 정해진다(같은 날 = 같은 미션, 기기·시간대 무관). 진행도는 PlayRewards.missionLog 에 쌓는다.

/** 미션 종류 — 진행도를 무엇으로 세는지 */
enum class MissionKind {
    /** 구구 계단에서 오른 계단(판마다 누적) */
    STAIRS_STEPS,
    /** 구구 몬스터에서 물리친 몬스터(판마다 누적) */
    MONSTERS_DEFEATED,
    /** 오늘 게임 판들 가운데 한 판의 최고 연속 정답 */
    BEST_STREAK,
    /** 게임에서 받은 별(판마다 누적 — 보물상자 별은 세지 않음) */
    STARS_EARNED,
}

data class Mission(val kind: MissionKind, val target: Int)

object DailyMissionPlan {
    /** 보물상자에서 받는 별 */
    const val CHEST_STARS = 10

    /** 보물상자를 연 날을 몇 개까지 남기는지(이번 주 도장 7칸 + 지난주 여유) */
    const val CHEST_DAYS_CAP = 14

    private val STAIRS_TARGETS = intArrayOf(10, 15, 20)
    private val MONSTER_TARGETS = intArrayOf(5, 8, 10)
    private val STREAK_TARGETS = intArrayOf(5, 7)
    private val STARS_TARGETS = intArrayOf(15, 25)
    private val DAY_RE = Regex("""^\d{4}-\d{2}-\d{2}$""")

    /**
     * "yyyy-MM-dd" → 날짜. 형식이 틀리거나 없는 날(2026-02-30)이면 던진다 — 저장본 검사(init)와 계획이 같은 기준을 쓴다.
     * Commit.todayStr 가 만드는 모양만 받는다(부호·자릿수 다른 연도는 거부)
     */
    fun parseDay(day: String): LocalDate {
        require(DAY_RE.matches(day)) { "날짜는 yyyy-MM-dd: $day" }
        return try {
            LocalDate.parse(day)
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("없는 날짜: $day", e)
        }
    }

    /**
     * 그 날의 미션 3개 — n = 1970-01-01 부터 센 일 수(연·월·일로만 계산하므로 시간대 영향 없음).
     * 나눗셈·나머지는 내림(floorDiv·floorMod) — 1970 년 전 날짜도 풀 안에서 고르게(iOS 와 같은 규칙)
     */
    fun missions(day: String): List<Mission> {
        val n = parseDay(day).toEpochDay()
        fun pick(pool: IntArray, k: Long): Int = pool[Math.floorMod(k, pool.size.toLong()).toInt()]
        val half = Math.floorDiv(n, 2L)
        val third = if (Math.floorMod(n, 2L) == 0L) {
            Mission(MissionKind.BEST_STREAK, pick(STREAK_TARGETS, half))
        } else {
            Mission(MissionKind.STARS_EARNED, pick(STARS_TARGETS, half))
        }
        return listOf(
            Mission(MissionKind.STAIRS_STEPS, pick(STAIRS_TARGETS, n)),
            Mission(MissionKind.MONSTERS_DEFEATED, pick(MONSTER_TARGETS, Math.floorDiv(n, 3L))),
            third,
        )
    }

    /** 이번 주(월~일) 날짜 7개 */
    fun week(today: LocalDate): List<LocalDate> {
        val monday = today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
        return (0L until 7L).map { monday.plusDays(it) }
    }
}

/**
 * 하루치 미션 진행도(PlayRewards 안, 키 gugu.play.v1). day 가 오늘이 아니면 저장소가 읽을 때 0 으로 보고, 쓸 때 새 log 로 바꾼다.
 * 날짜 형식이 틀리거나 음수면 init 에서 던져 저장본째 격리된다(PlayRewards 관례 — 조용히 고치지 않는다)
 */
@Serializable
data class DailyMissionLog(
    val day: String,
    /** 오른 계단 누적 */
    val stairsSteps: Int = 0,
    /** 물리친 몬스터 누적 */
    val monstersDefeated: Int = 0,
    /** 게임에서 받은 별 누적 */
    val starsEarned: Int = 0,
    /** 오늘 판들 가운데 최고 연속 정답(max) */
    val bestStreak: Int = 0,
    /** 오늘 보물상자를 열었는지 */
    val chestOpened: Boolean = false,
) {
    init {
        DailyMissionPlan.parseDay(day)
        require(stairsSteps >= 0 && monstersDefeated >= 0 && starsEarned >= 0 && bestStreak >= 0) {
            "음수 미션 진행도: $this"  // i18n:skip — 단언 메시지(다음 줄로 넘긴 require)
        }
    }

    fun progress(kind: MissionKind): Int = when (kind) {
        MissionKind.STAIRS_STEPS -> stairsSteps
        MissionKind.MONSTERS_DEFEATED -> monstersDefeated
        MissionKind.BEST_STREAK -> bestStreak
        MissionKind.STARS_EARNED -> starsEarned
    }

    fun reached(mission: Mission): Boolean = progress(mission.kind) >= mission.target

    /** 결과 화면 한 판을 더한다 — 누적은 +=(넘치면 던짐), 연속은 max */
    fun adding(game: PlayGame, score: Int, stars: Int, bestStreak: Int): DailyMissionLog = copy(
        stairsSteps = if (game == PlayGame.STAIRS) Math.addExact(stairsSteps, score) else stairsSteps,
        monstersDefeated = if (game == PlayGame.MONSTER) Math.addExact(monstersDefeated, score) else monstersDefeated,
        starsEarned = Math.addExact(starsEarned, stars),
        bestStreak = maxOf(this.bestStreak, bestStreak),
    )
}

/** 홈 카드 한 줄 — progress 는 목표에서 자른 값("15/15" 를 넘겨 "18/15" 로 그리지 않게) */
data class MissionItem(val mission: Mission, val progress: Int, val done: Boolean)

/** 이번 주 도장 한 칸 */
data class WeekStamp(val date: LocalDate, val stamped: Boolean, val isToday: Boolean)

/** 홈 카드가 그리는 읽기 전용 값(PlayRewardsStore.missionBoard) */
data class MissionBoard(
    val items: List<MissionItem>,
    val chestOpened: Boolean,
    /** 이번 주 월~일 7칸 */
    val week: List<WeekStamp>,
) {
    val doneCount: Int get() = items.count { it.done }
    val allDone: Boolean get() = items.all { it.done }
    val chestReady: Boolean get() = allDone && !chestOpened

    /**
     * 미션 구슬이 열 게임 (계획서 2-7).
     * 계단 미션 → 구구 계단, 몬스터 미션 → 구구 몬스터.
     * 세 번째 미션(연속 정답·코인 모으기) → 아직 미션을 못 끝낸 첫 게임(계단 → 몬스터 순), 둘 다 끝났으면 구구 계단.
     */
    fun game(kind: MissionKind): PlayGame = when (kind) {
        MissionKind.STAIRS_STEPS -> PlayGame.STAIRS
        MissionKind.MONSTERS_DEFEATED -> PlayGame.MONSTER
        MissionKind.BEST_STREAK, MissionKind.STARS_EARNED -> {
            val stairsDone = items.firstOrNull { it.mission.kind == MissionKind.STAIRS_STEPS }?.done == true
            val monsterDone = items.firstOrNull { it.mission.kind == MissionKind.MONSTERS_DEFEATED }?.done == true
            when {
                !stairsDone -> PlayGame.STAIRS
                !monsterDone -> PlayGame.MONSTER
                else -> PlayGame.STAIRS
            }
        }
    }

    fun game(mission: Mission): PlayGame = game(mission.kind)
    fun game(item: MissionItem): PlayGame = game(item.mission.kind)
}
