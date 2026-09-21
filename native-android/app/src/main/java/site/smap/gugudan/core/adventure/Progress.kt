package site.smap.gugudan.core.adventure

import site.smap.gugudan.core.Hints

// 어드벤처 진행 — 격파/해금 계산 (Progress.swift 이식, 순수 함수)

object AdvProgress {
    val defaultProgress = AdventureProgress()

    fun isNpcDefeated(p: AdventureProgress, npcId: String): Boolean = npcId in p.defeatedNpcs

    /** 해금은 단조 증가: 구규칙(번호-1 보스) ∪ 로드맵 직전 보스 ∪ 해당 단 별≥1 */
    fun isRegionUnlocked(p: AdventureProgress, table: Int, tableStars: Int = 0): Boolean {
        if (table == Hints.ROADMAP_TABLES.first() || table <= World.regions.first().table) return true
        if (isNpcDefeated(p, World.bossId(table - 1))) return true
        val idx = Hints.ROADMAP_TABLES.indexOf(table)
        if (idx > 0 && isNpcDefeated(p, World.bossId(Hints.ROADMAP_TABLES[idx - 1]))) return true
        return tableStars >= 1
    }

    /** 로드맵 상 직전 단. 첫 단이거나 로드맵에 없으면 null. */
    fun roadmapPrevTable(table: Int): Int? {
        val idx = Hints.ROADMAP_TABLES.indexOf(table)
        return if (idx <= 0) null else Hints.ROADMAP_TABLES[idx - 1]
    }

    /** 배틀 결과 반영 + 어드벤처 전용 업적 판정 → 새 진행 상태와 신규 해금 업적 id */
    fun applyBattle(p: AdventureProgress, npcId: String, won: Boolean): Pair<AdventureProgress, List<String>> {
        var next = if (won) {
            p.copy(
                battlesWon = p.battlesWon + 1,
                defeatedNpcs = if (isNpcDefeated(p, npcId)) p.defeatedNpcs else p.defeatedNpcs + npcId,
            )
        } else {
            p.copy(battlesLost = p.battlesLost + 1)
        }
        val unlocked = AdvAchievements.newlyUnlocked(next)
        if (unlocked.isNotEmpty()) next = next.copy(achievements = next.achievements + unlocked)
        return next to unlocked
    }

    data class RegionStats(val defeated: Int, val total: Int, val bossDefeated: Boolean)

    fun regionStats(p: AdventureProgress, region: RegionDef): RegionStats {
        val defeated = region.npcs.count { isNpcDefeated(p, it.id) }
        return RegionStats(defeated, region.npcs.size, isNpcDefeated(p, World.bossId(region.table)))
    }

    /** 전체 격파 현황 (홈 카드 표시용) */
    fun totalStats(p: AdventureProgress): Pair<Int, Int> {
        val total = World.regions.sumOf { it.npcs.size }
        return minOf(p.defeatedNpcs.size, total) to total
    }
}
