package site.smap.gugudan.core.adventure

// 어드벤처 전용 업적 (AdventureAchievements.swift 이식)

data class AdvAchievementDef(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val check: (AdventureProgress) -> Boolean,
)

object AdvAchievements {
    fun clearedRegions(p: AdventureProgress): Int =
        World.regions.count { World.bossId(it.table) in p.defeatedNpcs }

    fun defeatedBySlot(p: AdventureProgress, slot: String): Boolean =
        p.defeatedNpcs.any { it.endsWith("-$slot") }

    val all: List<AdvAchievementDef> = listOf(
        AdvAchievementDef("adv_first_win", "첫 승리", "어드벤처에서 첫 대결 승리", "Swords") { it.battlesWon >= 1 },
        AdvAchievementDef("adv_speed_win", "스피드 레이서", "스피드 레이스에서 승리", "Gauge") { defeatedBySlot(it, "n2") },
        AdvAchievementDef("adv_counter_win", "침착한 반격", "반격전에서 승리", "Timer") { defeatedBySlot(it, "n3") },
        AdvAchievementDef("adv_first_boss", "보스 사냥꾼", "첫 보스 격파", "Crown") { clearedRegions(it) >= 1 },
        AdvAchievementDef("adv_wins_10", "백전노장", "대결 10승 달성", "Medal") { it.battlesWon >= 10 },
        AdvAchievementDef("adv_half_world", "절반의 정복", "지역 4개 클리어", "Flag") { clearedRegions(it) >= 4 },
        AdvAchievementDef("adv_world_clear", "구구단 정복자", "8개 지역 보스를 모두 격파", "Trophy") { clearedRegions(it) >= World.regions.size },
        AdvAchievementDef("adv_all_npcs", "완전 정복", "모든 주민과 보스를 격파", "Gem") { p ->
            World.regions.all { r -> r.npcs.all { it.id in p.defeatedNpcs } }
        },
    )

    fun newlyUnlocked(p: AdventureProgress): List<String> {
        val have = p.achievements.toSet()
        return all.filter { it.id !in have && it.check(p) }.map { it.id }
    }

    fun get(id: String): AdvAchievementDef? = all.firstOrNull { it.id == id }
}
