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
        AdvAchievementDef(id: "adv_first_win", name: String(localized: "첫 승리", comment: "어드벤처 업적 이름"), description: String(localized: "어드벤처에서 첫 대결 승리", comment: "어드벤처 업적 설명"), icon: "Swords", check: { $0.battlesWon >= 1 }),
        AdvAchievementDef(id: "adv_speed_win", name: String(localized: "스피드 레이서", comment: "어드벤처 업적 이름"), description: String(localized: "스피드 레이스에서 승리", comment: "어드벤처 업적 설명"), icon: "Gauge", check: { defeatedBySlot($0, "n2") }),
        AdvAchievementDef(id: "adv_counter_win", name: String(localized: "침착한 반격", comment: "어드벤처 업적 이름"), description: String(localized: "반격전에서 승리", comment: "어드벤처 업적 설명"), icon: "Timer", check: { defeatedBySlot($0, "n3") }),
        AdvAchievementDef(id: "adv_first_boss", name: String(localized: "보스 사냥꾼", comment: "어드벤처 업적 이름"), description: String(localized: "첫 보스 격파", comment: "어드벤처 업적 설명"), icon: "Crown", check: { clearedRegions($0) >= 1 }),
        AdvAchievementDef(id: "adv_wins_10", name: String(localized: "백전노장", comment: "어드벤처 업적 이름"), description: String(localized: "대결 10승 달성", comment: "어드벤처 업적 설명"), icon: "Medal", check: { $0.battlesWon >= 10 }),
        AdvAchievementDef(id: "adv_half_world", name: String(localized: "절반의 정복", comment: "어드벤처 업적 이름"), description: String(localized: "지역 4개 클리어", comment: "어드벤처 업적 설명"), icon: "Flag", check: { clearedRegions($0) >= 4 }),
        AdvAchievementDef(id: "adv_world_clear", name: String(localized: "구구단 정복자", comment: "어드벤처 업적 이름"), description: String(localized: "8개 지역 보스를 모두 격파", comment: "어드벤처 업적 설명"), icon: "Trophy", check: { clearedRegions($0) >= World.regions.count }),
        AdvAchievementDef(id: "adv_all_npcs", name: String(localized: "완전 정복", comment: "어드벤처 업적 이름"), description: String(localized: "모든 주민과 보스를 격파", comment: "어드벤처 업적 설명"), icon: "Gem", check: { p in World.regions.allSatisfy { r in r.npcs.allSatisfy { p.defeatedNpcs.contains($0.id) } } }),
    ]

    static func newlyUnlocked(_ p: AdventureProgress) -> [String] {
        let have = Set(p.achievements)
        return all.filter { !have.contains($0.id) && $0.check(p) }.map { $0.id }
    }

    static func get(_ id: String) -> AdvAchievementDef? {
        all.first { $0.id == id }
    }
}
