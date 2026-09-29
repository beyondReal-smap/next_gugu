package site.smap.gugudan.core

// 업적 정의 및 판정 (Achievements.swift 이식)
// 모든 업적은 "측정값이 목표치에 도달"하는 형태다. 판정(check)과 진행도(progress)가
// 같은 metric/target 을 쓰게 해 두 곳의 기준이 어긋나지 않게 한다.

data class AchievementDef(
    val id: String,
    val name: String,
    val description: String,
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
        AchievementDef("first_correct", "첫 정답", "처음으로 정답을 맞혔어요", "Check", 1) { it.totalCorrect },
        AchievementDef("correct_100", "백 문제 돌파", "누적 100문제 정답", "Target", 100) { it.totalCorrect },
        AchievementDef("correct_500", "오백 문제 돌파", "누적 500문제 정답", "Award", 500) { it.totalCorrect },
        AchievementDef("combo_10", "콤보 10", "한 세션에서 10연속 정답", "Zap", 10) { it.maxCombo },
        AchievementDef("combo_20", "콤보 20", "20연속 정답 달성", "Flame", 20) { it.maxCombo },
        AchievementDef("streak_3", "3일 연속", "3일 연속 학습", "CalendarCheck", 3) { it.streak },
        AchievementDef("streak_7", "일주일 개근", "7일 연속 학습", "CalendarHeart", 7) { it.streak },
        AchievementDef("streak_30", "한 달 개근", "30일 연속 학습", "Crown", 30) { it.streak },
        AchievementDef("first_master", "첫 마스터", "한 단을 마스터(별 획득)", "Star", 1) { masteredTables(it) },
        AchievementDef("master_all", "구구단 완전정복", "2~9단 모두 마스터", "GraduationCap", 8) { masteredTables(it) },
        AchievementDef("stars_12", "별 수집가", "별 12개 수집", "Sparkles", 12) { totalStars(it) },
        AchievementDef("stars_24", "올스타", "모든 단 별 3개", "Trophy", 24) { totalStars(it) },
        AchievementDef("level_5", "레벨 5", "레벨 5 도달", "TrendingUp", 5) { Level.info(it.totalXp).level },
        AchievementDef("level_15", "레벨 15", "레벨 15 도달", "Rocket", 15) { Level.info(it.totalXp).level },
        AchievementDef("challenge_15", "번개 계산", "60초 챌린지에서 15점 달성", "Timer", 15) { it.bestScores[GameMode.CHALLENGE] ?: 0 },
        AchievementDef("survival_20", "생존왕", "서바이벌에서 20문제 생존", "HeartPulse", 20) { it.bestScores[GameMode.SURVIVAL] ?: 0 },
        AchievementDef("mode_explorer", "모드 탐험가", "모든 게임 모드를 플레이", "Compass", Modes.list.size) { modesExplored(it) },
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
