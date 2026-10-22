package site.smap.gugudan.core.adventure

import androidx.annotation.StringRes
import site.smap.gugudan.R

// 어드벤처 전용 업적 (AdventureAchievements.swift 이식)

data class AdvAchievementDef(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: String,
    val check: (AdventureProgress) -> Boolean,
)

object AdvAchievements {
    fun clearedRegions(p: AdventureProgress): Int =
        World.regions.count { World.bossId(it.table) in p.defeatedNpcs }

    fun defeatedBySlot(p: AdventureProgress, slot: String): Boolean =
        p.defeatedNpcs.any { it.endsWith("-$slot") }

    val all: List<AdvAchievementDef> = listOf(
        AdvAchievementDef("adv_first_win", R.string.first_victory, R.string.won_your_first_adventure_battle, "Swords") { it.battlesWon >= 1 },
        AdvAchievementDef("adv_speed_win", R.string.speed_racer, R.string.won_a_speed_race, "Gauge") { defeatedBySlot(it, "n2") },
        AdvAchievementDef("adv_counter_win", R.string.cool_counter, R.string.won_a_counterattack_battle, "Timer") { defeatedBySlot(it, "n3") },
        AdvAchievementDef("adv_first_boss", R.string.boss_hunter, R.string.defeated_your_first_boss, "Crown") { clearedRegions(it) >= 1 },
        AdvAchievementDef("adv_wins_10", R.string.battle_veteran, R.string.won_10_battles, "Medal") { it.battlesWon >= 10 },
        AdvAchievementDef("adv_half_world", R.string.halfway_there, R.string.cleared_4_regions, "Flag") { clearedRegions(it) >= 4 },
        AdvAchievementDef("adv_world_clear", R.string.times_table_conqueror, R.string.defeated_all_8_regional_bosses, "Trophy") { clearedRegions(it) >= World.regions.size },
        AdvAchievementDef("adv_all_npcs", R.string.total_conquest, R.string.defeated_every_resident_and_boss, "Gem") { p ->
            World.regions.all { r -> r.npcs.all { it.id in p.defeatedNpcs } }
        },
    )

    fun newlyUnlocked(p: AdventureProgress): List<String> {
        val have = p.achievements.toSet()
        return all.filter { it.id !in have && it.check(p) }.map { it.id }
    }

    fun get(id: String): AdvAchievementDef? = all.firstOrNull { it.id == id }
}
