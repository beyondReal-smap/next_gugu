package site.smap.gugudan.core

import androidx.annotation.StringRes
import site.smap.gugudan.R

// 업적 정의 및 판정 (Achievements.swift 이식)
// 모든 업적은 "측정값이 목표치에 도달"하는 형태다. 판정(check)과 진행도(progress)가
// 같은 metric/target 을 쓰게 해 두 곳의 기준이 어긋나지 않게 한다.

data class AchievementDef(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: String,   // 아이콘 키 (UI에서 매핑)
    val target: Int,
    val metric: (GameState) -> Int,
) {
    fun check(s: GameState): Boolean = metric(s) >= target
}

/** 업적 진행도 — current 는 target 을 넘지 않게 자른다 */
data class AchievementProgress(val current: Int, val target: Int) {
    val fraction: Double get() = if (target > 0) current.toDouble() / target else 0.0
}

object Achievements {
    fun totalStars(s: GameState): Int = s.tableMastery.values.sumOf { it.stars }
    fun masteredTables(s: GameState): Int = s.tableMastery.values.count { it.stars >= 1 }
    /** 학습 탭 모드 중 한 번이라도 플레이한 모드 수 */
    fun modesExplored(s: GameState): Int = Modes.list.count { it.id in s.modesPlayed }

    val all: List<AchievementDef> = listOf(
        AchievementDef("first_correct", R.string.first_correct, R.string.got_your_first_answer_right, "Check", 1) { it.totalCorrect },
        AchievementDef("correct_100", R.string.s_100_club, R.string.s_100_correct_answers_in_total, "Target", 100) { it.totalCorrect },
        AchievementDef("correct_500", R.string.s_500_club, R.string.s_500_correct_answers_in_total, "Award", 500) { it.totalCorrect },
        AchievementDef("combo_10", R.string.combo_10, R.string.s_10_in_a_row_in_one_session, "Zap", 10) { it.maxCombo },
        AchievementDef("combo_20", R.string.combo_20, R.string.s_20_correct_in_a_row, "Flame", 20) { it.maxCombo },
        AchievementDef("streak_3", R.string.s_3_day_streak, R.string.practiced_3_days_in_a_row, "CalendarCheck", 3) { it.streak },
        AchievementDef("streak_7", R.string.perfect_week, R.string.practiced_7_days_in_a_row, "CalendarHeart", 7) { it.streak },
        AchievementDef("streak_30", R.string.perfect_month, R.string.practiced_30_days_in_a_row, "Crown", 30) { it.streak },
        AchievementDef("first_master", R.string.first_mastery, R.string.mastered_a_times_table_earned_a_star, "Star", 1) { masteredTables(it) },
        AchievementDef("master_all", R.string.times_table_champion, R.string.mastered_every_table_from_2_to_9, "GraduationCap", 8) { masteredTables(it) },
        AchievementDef("stars_12", R.string.star_collector, R.string.collected_12_stars, "Sparkles", 12) { totalStars(it) },
        AchievementDef("stars_24", R.string.all_star, R.string.s_3_stars_on_every_table, "Trophy", 24) { totalStars(it) },
        AchievementDef("level_5", R.string.level_5, R.string.reached_level_5, "TrendingUp", 5) { Level.info(it.totalXp).level },
        AchievementDef("level_15", R.string.level_15, R.string.reached_level_15, "Rocket", 15) { Level.info(it.totalXp).level },
        AchievementDef("challenge_15", R.string.lightning_math, R.string.scored_15_in_the_60_second_challenge, "Timer", 15) { it.bestScores[GameMode.CHALLENGE] ?: 0 },
        AchievementDef("survival_20", R.string.survivor, R.string.survived_20_problems_in_survival, "HeartPulse", 20) { it.bestScores[GameMode.SURVIVAL] ?: 0 },
        AchievementDef("mode_explorer", R.string.mode_explorer, R.string.played_every_game_mode, "Compass", Modes.list.size) { modesExplored(it) },
    )

    fun newlyUnlocked(state: GameState): List<String> {
        val have = state.unlockedAchievements.toSet()
        return all.filter { it.id !in have && it.check(state) }.map { it.id }
    }

    fun get(id: String): AchievementDef? = all.firstOrNull { it.id == id }

    /** 업적 진행도의 단일 기준 — 화면(프로필 타일·상세 시트)도 이것만 쓴다 */
    fun progress(def: AchievementDef, s: GameState): AchievementProgress =
        AchievementProgress(minOf(def.metric(s), def.target), def.target)

    /** 업적 진행도. 알 수 없는 id 면 null */
    fun progress(id: String, s: GameState): AchievementProgress? = get(id)?.let { progress(it, s) }
}
