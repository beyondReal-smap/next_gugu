package site.smap.gugudan.core

// 업적 정의 및 판정 (Achievements.swift 이식)

data class AchievementDef(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,   // 아이콘 키 (UI에서 매핑)
    val check: (GameState) -> Boolean,
)

object Achievements {
    fun totalStars(s: GameState): Int = s.tableMastery.values.sumOf { it.stars }
    fun masteredTables(s: GameState): Int = s.tableMastery.values.count { it.stars >= 1 }

    val all: List<AchievementDef> = listOf(
        AchievementDef("first_correct", "첫 정답", "처음으로 정답을 맞혔어요", "Check") { it.totalCorrect >= 1 },
        AchievementDef("correct_100", "백 문제 돌파", "누적 100문제 정답", "Target") { it.totalCorrect >= 100 },
        AchievementDef("correct_500", "오백 문제 돌파", "누적 500문제 정답", "Award") { it.totalCorrect >= 500 },
        AchievementDef("combo_10", "콤보 10", "한 세션에서 10연속 정답", "Zap") { it.maxCombo >= 10 },
        AchievementDef("combo_20", "콤보 20", "20연속 정답 달성", "Flame") { it.maxCombo >= 20 },
        AchievementDef("streak_3", "3일 연속", "3일 연속 학습", "CalendarCheck") { it.streak >= 3 },
        AchievementDef("streak_7", "일주일 개근", "7일 연속 학습", "CalendarHeart") { it.streak >= 7 },
        AchievementDef("streak_30", "한 달 개근", "30일 연속 학습", "Crown") { it.streak >= 30 },
        AchievementDef("first_master", "첫 마스터", "한 단을 마스터(별 획득)", "Star") { masteredTables(it) >= 1 },
        AchievementDef("master_all", "구구단 완전정복", "2~9단 모두 마스터", "GraduationCap") { masteredTables(it) >= 8 },
        AchievementDef("stars_12", "별 수집가", "별 12개 수집", "Sparkles") { totalStars(it) >= 12 },
        AchievementDef("stars_24", "올스타", "모든 단 별 3개", "Trophy") { totalStars(it) >= 24 },
        AchievementDef("level_5", "레벨 5", "레벨 5 도달", "TrendingUp") { Level.info(it.totalXp).level >= 5 },
        AchievementDef("level_15", "레벨 15", "레벨 15 도달", "Rocket") { Level.info(it.totalXp).level >= 15 },
        AchievementDef("challenge_15", "번개 계산", "60초 챌린지에서 15점 달성", "Timer") { (it.bestScores[GameMode.CHALLENGE] ?: 0) >= 15 },
        AchievementDef("survival_20", "생존왕", "서바이벌에서 20문제 생존", "HeartPulse") { (it.bestScores[GameMode.SURVIVAL] ?: 0) >= 20 },
        AchievementDef("mode_explorer", "모드 탐험가", "모든 게임 모드를 플레이", "Compass") { s -> Modes.list.all { it.id in s.modesPlayed } },
    )

    fun newlyUnlocked(state: GameState): List<String> {
        val have = state.unlockedAchievements.toSet()
        return all.filter { it.id !in have && it.check(state) }.map { it.id }
    }

    fun get(id: String): AchievementDef? = all.firstOrNull { it.id == id }
}
