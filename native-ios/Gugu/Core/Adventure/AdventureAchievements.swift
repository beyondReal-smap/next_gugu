import Foundation

// 어드벤처 전용 업적 — AdventureProgress 기반 판정 (adventure/achievements.ts 이식)

struct AdvAchievementDef: Identifiable {
    let id: String
    let name: String
    let description: String
    let icon: String
    let check: (AdventureProgress) -> Bool
}

enum AdvAchievements {
    static func clearedRegions(_ p: AdventureProgress) -> Int {
        World.regions.filter { p.defeatedNpcs.contains(World.bossId(for: $0.table)) }.count
    }
    static func defeatedBySlot(_ p: AdventureProgress, _ slot: String) -> Bool {
        p.defeatedNpcs.contains { $0.hasSuffix("-\(slot)") }
    }

    static let all: [AdvAchievementDef] = [
        AdvAchievementDef(id: "adv_first_win", name: "첫 승리", description: "어드벤처에서 첫 대결 승리", icon: "Swords", check: { $0.battlesWon >= 1 }),
        AdvAchievementDef(id: "adv_speed_win", name: "스피드 레이서", description: "스피드 레이스에서 승리", icon: "Gauge", check: { defeatedBySlot($0, "n2") }),
        AdvAchievementDef(id: "adv_counter_win", name: "침착한 반격", description: "반격전에서 승리", icon: "Timer", check: { defeatedBySlot($0, "n3") }),
        AdvAchievementDef(id: "adv_first_boss", name: "보스 사냥꾼", description: "첫 보스 격파", icon: "Crown", check: { clearedRegions($0) >= 1 }),
        AdvAchievementDef(id: "adv_wins_10", name: "백전노장", description: "대결 10승 달성", icon: "Medal", check: { $0.battlesWon >= 10 }),
        AdvAchievementDef(id: "adv_half_world", name: "절반의 정복", description: "지역 4개 클리어", icon: "Flag", check: { clearedRegions($0) >= 4 }),
        AdvAchievementDef(id: "adv_world_clear", name: "구구단 정복자", description: "8개 지역 보스를 모두 격파", icon: "Trophy", check: { clearedRegions($0) >= World.regions.count }),
        AdvAchievementDef(id: "adv_all_npcs", name: "완전 정복", description: "모든 주민과 보스를 격파", icon: "Gem", check: { p in World.regions.allSatisfy { r in r.npcs.allSatisfy { p.defeatedNpcs.contains($0.id) } } }),
    ]

    static func newlyUnlocked(_ p: AdventureProgress) -> [String] {
        let have = Set(p.achievements)
        return all.filter { !have.contains($0.id) && $0.check(p) }.map { $0.id }
    }

    static func get(_ id: String) -> AdvAchievementDef? {
        all.first { $0.id == id }
    }
}
