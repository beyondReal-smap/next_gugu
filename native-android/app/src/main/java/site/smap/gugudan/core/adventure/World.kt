package site.smap.gugudan.core.adventure

import androidx.annotation.StringRes
import site.smap.gugudan.R
import kotlin.math.hypot

// 어드벤처 월드 — 지역/NPC 정적 데이터의 단일 출처 (World.swift 이식)

object World {
    const val FIELD_BOUND = 16.0
    const val ENCOUNTER_DIST = 2.6
    const val ENCOUNTER_EXIT_DIST = 3.6

    fun bossId(table: Int): String = "r$table-boss"

    // 난이도 스케일 — 단이 높을수록 튼튼하고 아프게
    fun npcHp(kind: NpcKind, table: Int): Int =
        if (kind == NpcKind.BOSS) 190 + (table - 2) * 8 else 110 + (table - 2) * 6

    fun npcAttack(kind: NpcKind, table: Int): Int =
        if (kind == NpcKind.BOSS) 30 + (table - 2) else 22 + (table - 2)

    // 슬롯별 배틀 방식 — 보스는 항상 체력전
    val normalStyles = listOf(BattleStyle.HP, BattleStyle.SPEED, BattleStyle.COUNTER)

    // MARK: 지역별 지형 레이아웃

    data class RegionLayout(
        val spawn: Vec2,
        val slots: List<Vec2>,   // 일반 NPC 3
        val boss: Vec2,
        val landmark: Landmark,
    )

    private fun pt(x: Double, z: Double) = Vec2(x, z)

    val layouts: Map<Int, RegionLayout> = mapOf(
        2 to RegionLayout(pt(0.0, 12.0), listOf(pt(-8.0, 3.0), pt(7.0, -1.0), pt(-2.0, -8.0)), pt(0.0, -14.0), Landmark(LandmarkKind.FLOWERBED, pt(6.0, 5.0))),
        3 to RegionLayout(pt(-10.0, 12.0), listOf(pt(-11.0, 1.0), pt(-2.0, -4.0), pt(7.0, -9.0)), pt(12.0, -13.0), Landmark(LandmarkKind.OASIS, pt(8.0, 4.0))),
        4 to RegionLayout(pt(10.0, 12.0), listOf(pt(6.0, 2.0), pt(-1.0, -5.0), pt(8.0, -8.0)), pt(-4.0, -13.0), Landmark(LandmarkKind.SEA, pt(-17.0, 0.0))),
        5 to RegionLayout(pt(0.0, 13.0), listOf(pt(-9.0, 6.0), pt(9.0, 0.0), pt(-9.0, -7.0)), pt(6.0, -13.0), Landmark(LandmarkKind.ANCIENT_TREE, pt(2.0, 3.0))),
        6 to RegionLayout(pt(0.0, 12.0), listOf(pt(-11.0, -2.0), pt(0.0, -6.0), pt(11.0, -2.0)), pt(0.0, -13.0), Landmark(LandmarkKind.SNOWMAN, pt(6.0, 4.0))),
        7 to RegionLayout(pt(-8.0, 13.0), listOf(pt(6.0, 7.0), pt(-7.0, -1.0), pt(7.0, -8.0)), pt(-6.0, -13.0), Landmark(LandmarkKind.POND, pt(0.0, 4.0))),
        8 to RegionLayout(pt(0.0, 13.0), listOf(pt(-4.0, 6.0), pt(4.0, -1.0), pt(-4.0, -7.0)), pt(2.0, -14.0), Landmark(LandmarkKind.VOLCANO, pt(-11.0, -11.0))),
        9 to RegionLayout(pt(0.0, 13.5), listOf(pt(-6.0, 6.0), pt(6.0, 1.0), pt(-6.0, -5.0)), pt(0.0, -13.0), Landmark(LandmarkKind.PILLARS, pt(0.0, -9.0))),
    )

    // MARK: 결정적 시드 난수 (mulberry32) — JS/Swift와 비트 단위 동일

    fun mulberry32(seed: UInt): () -> Double {
        var a = seed
        return {
            a += 0x6d2b79f5u
            var t = (a xor (a shr 15)) * (1u or a)
            t = (t + ((t xor (t shr 7)) * (61u or t))) xor t
            (t xor (t shr 14)).toDouble() / 4294967296.0
        }
    }

    // 지역 소품 생성 — NPC/스폰/랜드마크를 피해 12~16개 배치
    fun genDecoItems(table: Int, layout: RegionLayout): List<DecoItem> {
        val rand = mulberry32((table * 1013 + 77).toUInt())
        val blocked = layout.slots.map { it to 3.4 } +
            listOf(layout.boss to 3.8, layout.spawn to 3.4, layout.landmark.pos to 5.0)

        val items = mutableListOf<DecoItem>()
        val count = 12 + (rand() * 5).toInt()   // 12~16
        var guard = 0
        while (items.size < count && guard++ < 400) {
            val x = (rand() * 2 - 1) * 15
            val z = (rand() * 2 - 1) * 15
            if (blocked.any { (p, r) -> hypot(p.x - x, p.z - z) < r }) continue
            if (items.any { hypot(it.x - x, it.z - z) < 2.2 }) continue
            items.add(DecoItem(
                Math.round(x * 10) / 10.0,
                Math.round(z * 10) / 10.0,
                Math.round((0.7 + rand() * 0.8) * 100) / 100.0,
            ))
        }
        return items
    }

    // 별 조각 배치 — NPC/스폰/랜드마크를 피해 5개
    fun genStarSpots(table: Int, layout: RegionLayout): List<Vec2> {
        val rand = mulberry32((table * 777 + 13).toUInt())
        val blocked = layout.slots.map { it to 2.6 } +
            listOf(layout.boss to 3.0, layout.spawn to 2.6, layout.landmark.pos to 4.5)

        val spots = mutableListOf<Vec2>()
        var guard = 0
        while (spots.size < 5 && guard++ < 300) {
            val x = (rand() * 2 - 1) * 13
            val z = (rand() * 2 - 1) * 13
            if (blocked.any { (p, r) -> hypot(p.x - x, p.z - z) < r }) continue
            if (spots.any { hypot(it.x - x, it.z - z) < 3.0 }) continue
            spots.add(Vec2(Math.round(x * 10) / 10.0, Math.round(z * 10) / 10.0))
        }
        return spots
    }

    // MARK: 지역/NPC 빌드

    data class NpcSeed(@StringRes val nameRes: Int, val color: String, @StringRes val greetingRes: Int? = null)

    private fun buildNpcs(table: Int, layout: RegionLayout, normals: List<NpcSeed>, boss: NpcSeed): List<NpcDef> {
        val list = normals.mapIndexed { i, seed ->
            NpcDef(
                id = "r$table-n${i + 1}",
                nameRes = seed.nameRes,
                kind = NpcKind.NORMAL,
                battle = normalStyles[i],
                table = table,
                pos = layout.slots[i],
                hp = npcHp(NpcKind.NORMAL, table),
                attack = npcAttack(NpcKind.NORMAL, table),
                color = seed.color,
                greetingRes = seed.greetingRes ?: R.string.let_s_battle_with_the_times_table,
            )
        }
        return list + NpcDef(
            id = bossId(table),
            nameRes = boss.nameRes,
            kind = NpcKind.BOSS,
            battle = BattleStyle.HP,
            table = table,
            pos = layout.boss,
            hp = npcHp(NpcKind.BOSS, table),
            attack = npcAttack(NpcKind.BOSS, table),
            color = boss.color,
            greetingRes = boss.greetingRes ?: R.string.i_m_the_boss_of_this_area_ready_for_the,
        )
    }

    private data class RegionSeed(
        val table: Int, @StringRes val nameRes: Int, val deco: DecoKind,
        val theme: RegionTheme, val normals: List<NpcSeed>, val boss: NpcSeed,
    )

    private fun buildRegion(seed: RegionSeed): RegionDef {
        val layout = layouts.getValue(seed.table)
        return RegionDef(
            table = seed.table,
            nameRes = seed.nameRes,
            deco = seed.deco,
            decoItems = genDecoItems(seed.table, layout),
            spawn = layout.spawn,
            landmark = layout.landmark,
            theme = seed.theme,
            npcs = buildNpcs(seed.table, layout, seed.normals, seed.boss),
            starSpots = genStarSpots(seed.table, layout),
        )
    }

    val regions: List<RegionDef> = listOf(
        buildRegion(RegionSeed(2, R.string.sprout_meadow, DecoKind.TREE,
            RegionTheme("#7ec850", "#3e9b4f", "#bfe3ff"),
            listOf(NpcSeed(R.string.hoppy_the_bunny, "#f4f1ea"), NpcSeed(R.string.peanut_the_squirrel, "#c98a4b"), NpcSeed(R.string.digby_the_mole, "#8a6f5c")),
            NpcSeed(R.string.meadow_keeper_bruin, "#7a5230", R.string.to_cross_this_meadow_the_2_times_table))),
        buildRegion(RegionSeed(3, R.string.sunset_desert, DecoKind.ROCK,
            RegionTheme("#e8c07a", "#c98d52", "#ffd9a0"),
            listOf(NpcSeed(R.string.sandy_the_fox, "#e07b39"), NpcSeed(R.string.ziggy_the_lizard, "#a3b86c"), NpcSeed(R.string.peeky_the_meerkat, "#d9b38c")),
            NpcSeed(R.string.scorch_king_of_the_desert, "#b3541e", R.string.a_3_times_table_battle_hotter_than_the))),
        buildRegion(RegionSeed(4, R.string.wave_beach, DecoKind.ROCK,
            RegionTheme("#f2e2b8", "#35a7c2", "#a7dcf0"),
            listOf(NpcSeed(R.string.pinchy_the_crab, "#e2593b"), NpcSeed(R.string.squawk_the_seagull, "#eef2f5"), NpcSeed(R.string.shelly_the_turtle, "#4a9b6e")),
            NpcSeed(R.string.tidemaster_octo, "#5a6ec7", R.string.can_you_withstand_the_4_times_table))),
        buildRegion(RegionSeed(5, R.string.mushroom_forest, DecoKind.TREE,
            RegionTheme("#4e8f57", "#c96f4a", "#a8d8b0"),
            listOf(NpcSeed(R.string.button_the_mushroom_kid, "#d95d4e"), NpcSeed(R.string.hoot_the_owl, "#8d7a63"), NpcSeed(R.string.dash_the_deer, "#b98a5a")),
            NpcSeed(R.string.mossbeard_sage_of_the_forest, "#5b7a4a", R.string.i_shall_test_you_with_the_forest_s))),
        buildRegion(RegionSeed(6, R.string.snowflake_fields, DecoKind.CRYSTAL,
            RegionTheme("#e8f1f7", "#9cc3d5", "#d7e9f5"),
            listOf(NpcSeed(R.string.waddles_the_penguin, "#3c4652"), NpcSeed(R.string.slick_the_seal, "#9aa7b5"), NpcSeed(R.string.snowy_the_hare, "#ffffff")),
            NpcSeed(R.string.frost_the_ice_witch, "#6fa8dc", R.string.answer_before_the_6_times_table_freezes))),
        buildRegion(RegionSeed(7, R.string.firefly_swamp, DecoKind.TREE,
            RegionTheme("#4f6b52", "#86c06c", "#55666e"),
            listOf(NpcSeed(R.string.pip_the_frog, "#6cbf5a"), NpcSeed(R.string.glimmer_the_firefly, "#ffe066"), NpcSeed(R.string.splash_the_otter, "#8a6f52")),
            NpcSeed(R.string.grimble_swamp_wizard, "#4a5d7a", R.string.want_out_of_the_swamp_recite_the_7_times))),
        buildRegion(RegionSeed(8, R.string.lava_volcano, DecoKind.ROCK,
            RegionTheme("#6b4a44", "#e2593b", "#f0a884"),
            listOf(NpcSeed(R.string.ember_the_salamander, "#e2593b"), NpcSeed(R.string.inky_the_bat, "#3c3744"), NpcSeed(R.string.flicker_the_fire_sprite, "#ff8c42")),
            NpcSeed(R.string.lava_chief_bubbleboil, "#b3341e", R.string.care_for_a_taste_of_my_boiling_hot_8))),
        buildRegion(RegionSeed(9, R.string.starlight_castle, DecoKind.CRYSTAL,
            RegionTheme("#5a5d8a", "#ffd166", "#2e3160"),
            listOf(NpcSeed(R.string.sir_silverstar, "#c0c8d8"), NpcSeed(R.string.luna_the_sorceress, "#8a6fd1"), NpcSeed(R.string.whoosh_the_ghost, "#dfe6f0")),
            NpcSeed(R.string.the_times_table_overlord, "#4a3670", R.string.so_you_ve_finally_come_the_9_times_table))),
    )

    fun region(table: Int): RegionDef? = regions.firstOrNull { it.table == table }
}
