import Foundation

// 어드벤처 진행 — 격파/해금 계산 (adventure/progress.ts 이식, 순수 함수)

enum AdvProgress {
    static let defaultProgress = AdventureProgress()

    static func isNpcDefeated(_ p: AdventureProgress, _ npcId: String) -> Bool {
        p.defeatedNpcs.contains(npcId)
    }

    /// 해금은 단조 증가: 구규칙(번호-1 보스) ∪ 로드맵 직전 보스 ∪ 해당 단 별≥1
    static func isRegionUnlocked(_ p: AdventureProgress, table: Int, tableStars: Int = 0) -> Bool {
        if table == Hints.roadmapTables[0] || table <= World.regions[0].table { return true }
        if isNpcDefeated(p, World.bossId(for: table - 1)) { return true }
        if let idx = Hints.roadmapTables.firstIndex(of: table), idx > 0,
           isNpcDefeated(p, World.bossId(for: Hints.roadmapTables[idx - 1])) { return true }
        if tableStars >= 1 { return true }
        return false
    }

    /// 로드맵 상 직전 단. 첫 단이거나 로드맵에 없으면 nil.
    static func roadmapPrevTable(_ table: Int) -> Int? {
        guard let idx = Hints.roadmapTables.firstIndex(of: table), idx > 0 else { return nil }
        return Hints.roadmapTables[idx - 1]
    }

    /// 배틀 결과 반영 + 어드벤처 전용 업적 판정 → 새 진행 상태와 신규 해금 업적 id
    static func applyBattle(_ p: AdventureProgress, npcId: String, won: Bool) -> (next: AdventureProgress, unlocked: [String]) {
        var next = p
        if won {
            next.battlesWon += 1
            if !isNpcDefeated(p, npcId) { next.defeatedNpcs.append(npcId) }
        } else {
            next.battlesLost += 1
        }
        let unlocked = AdvAchievements.newlyUnlocked(next)
        if !unlocked.isEmpty { next.achievements.append(contentsOf: unlocked) }
        return (next, unlocked)
    }

    struct RegionStats {
        var defeated: Int
        var total: Int
        var bossDefeated: Bool
    }

    static func regionStats(_ p: AdventureProgress, _ region: RegionDef) -> RegionStats {
        let defeated = region.npcs.filter { isNpcDefeated(p, $0.id) }.count
        return RegionStats(
            defeated: defeated,
            total: region.npcs.count,
            bossDefeated: isNpcDefeated(p, World.bossId(for: region.table))
        )
    }

    /// 전체 격파 현황 (홈 카드 표시용)
    static func totalStats(_ p: AdventureProgress) -> (defeated: Int, total: Int) {
        let total = World.regions.reduce(0) { $0 + $1.npcs.count }
        return (min(p.defeatedNpcs.count, total), total)
    }
}
