package site.smap.gugudan.core.adventure

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

    data class NpcSeed(val name: String, val color: String, val greeting: String? = null)

    private fun buildNpcs(table: Int, layout: RegionLayout, normals: List<NpcSeed>, boss: NpcSeed): List<NpcDef> {
        val list = normals.mapIndexed { i, seed ->
            NpcDef(
                id = "r$table-n${i + 1}",
                name = seed.name,
                kind = NpcKind.NORMAL,
                battle = normalStyles[i],
                table = table,
                pos = layout.slots[i],
                hp = npcHp(NpcKind.NORMAL, table),
                attack = npcAttack(NpcKind.NORMAL, table),
                color = seed.color,
                greeting = seed.greeting ?: "${table}단으로 승부하자!",
            )
        }
        return list + NpcDef(
            id = bossId(table),
            name = boss.name,
            kind = NpcKind.BOSS,
            battle = BattleStyle.HP,
            table = table,
            pos = layout.boss,
            hp = npcHp(NpcKind.BOSS, table),
            attack = npcAttack(NpcKind.BOSS, table),
            color = boss.color,
            greeting = boss.greeting ?: "이 지역의 보스다. ${table}단, 각오는 됐지?",
        )
    }

    private data class RegionSeed(
        val table: Int, val name: String, val deco: DecoKind,
        val theme: RegionTheme, val normals: List<NpcSeed>, val boss: NpcSeed,
    )

    private fun buildRegion(seed: RegionSeed): RegionDef {
        val layout = layouts.getValue(seed.table)
        return RegionDef(
            table = seed.table,
            name = seed.name,
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
        buildRegion(RegionSeed(2, "새싹 들판", DecoKind.TREE,
            RegionTheme("#7ec850", "#3e9b4f", "#bfe3ff"),
            listOf(NpcSeed("토끼 로로", "#f4f1ea"), NpcSeed("다람쥐 콩이", "#c98a4b"), NpcSeed("두더지 두두", "#8a6f5c")),
            NpcSeed("들판지기 곰곰", "#7a5230", "들판을 지나가려면 2단쯤은 술술 나와야지!"))),
        buildRegion(RegionSeed(3, "노을 사막", DecoKind.ROCK,
            RegionTheme("#e8c07a", "#c98d52", "#ffd9a0"),
            listOf(NpcSeed("여우 사샤", "#e07b39"), NpcSeed("도마뱀 리지", "#a3b86c"), NpcSeed("미어캣 모모", "#d9b38c")),
            NpcSeed("사막의 왕 카오", "#b3541e", "사막의 태양보다 뜨거운 3단 대결이다!"))),
        buildRegion(RegionSeed(4, "파도 해변", DecoKind.ROCK,
            RegionTheme("#f2e2b8", "#35a7c2", "#a7dcf0"),
            listOf(NpcSeed("게 집게", "#e2593b"), NpcSeed("갈매기 끼룩", "#eef2f5"), NpcSeed("거북 토토", "#4a9b6e")),
            NpcSeed("파도술사 옥토", "#5a6ec7", "파도처럼 밀려오는 4단을 견뎌봐라!"))),
        buildRegion(RegionSeed(5, "버섯 숲", DecoKind.TREE,
            RegionTheme("#4e8f57", "#c96f4a", "#a8d8b0"),
            listOf(NpcSeed("버섯돌이 뽀삐", "#d95d4e"), NpcSeed("올빼미 부엉", "#8d7a63"), NpcSeed("사슴 다다", "#b98a5a")),
            NpcSeed("숲의 현자 무무", "#5b7a4a", "숲의 지혜를 시험하마. 5단이다!"))),
        buildRegion(RegionSeed(6, "눈꽃 설원", DecoKind.CRYSTAL,
            RegionTheme("#e8f1f7", "#9cc3d5", "#d7e9f5"),
            listOf(NpcSeed("펭귄 핑핑", "#3c4652"), NpcSeed("물개 살살", "#9aa7b5"), NpcSeed("눈토끼 하양", "#ffffff")),
            NpcSeed("얼음마녀 서리", "#6fa8dc", "6단이 얼어붙기 전에 답해 보시지!"))),
        buildRegion(RegionSeed(7, "반딧불 늪", DecoKind.TREE,
            RegionTheme("#4f6b52", "#86c06c", "#55666e"),
            listOf(NpcSeed("개구리 팝팝", "#6cbf5a"), NpcSeed("반딧불 반반", "#ffe066"), NpcSeed("수달 찰랑", "#8a6f52")),
            NpcSeed("늪의 마법사 그림", "#4a5d7a", "늪에서 빠져나가고 싶다면 7단을 외워라!"))),
        buildRegion(RegionSeed(8, "용암 화산", DecoKind.ROCK,
            RegionTheme("#6b4a44", "#e2593b", "#f0a884"),
            listOf(NpcSeed("도롱뇽 라바", "#e2593b"), NpcSeed("박쥐 까망", "#3c3744"), NpcSeed("불꽃정령 화르", "#ff8c42")),
            NpcSeed("용암대장 부글", "#b3341e", "펄펄 끓는 8단 맛 좀 볼래?"))),
        buildRegion(RegionSeed(9, "별빛 성", DecoKind.CRYSTAL,
            RegionTheme("#5a5d8a", "#ffd166", "#2e3160"),
            listOf(NpcSeed("기사 은별", "#c0c8d8"), NpcSeed("마법사 루나", "#8a6fd1"), NpcSeed("유령 휘휘", "#dfe6f0")),
            NpcSeed("구구단 대마왕", "#4a3670", "드디어 왔군. 9단… 최후의 대결이다!"))),
    )

    fun region(table: Int): RegionDef? = regions.firstOrNull { it.table == table }
}
