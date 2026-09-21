import Foundation

// 업적 정의 및 판정 (achievements.ts 이식)

struct AchievementDef: Identifiable {
    let id: String
    let name: String
    let description: String
    let icon: String   // SF Symbols/에셋 아이콘 이름 (P2에서 매핑)
    let check: (GameState) -> Bool
}

enum Achievements {
    static func totalStars(_ s: GameState) -> Int {
        s.tableMastery.values.reduce(0) { $0 + $1.stars }
    }
    static func masteredTables(_ s: GameState) -> Int {
        s.tableMastery.values.filter { $0.stars >= 1 }.count
    }

    static let all: [AchievementDef] = [
        AchievementDef(id: "first_correct", name: "첫 정답", description: "처음으로 정답을 맞혔어요", icon: "Check", check: { $0.totalCorrect >= 1 }),
        AchievementDef(id: "correct_100", name: "백 문제 돌파", description: "누적 100문제 정답", icon: "Target", check: { $0.totalCorrect >= 100 }),
        AchievementDef(id: "correct_500", name: "오백 문제 돌파", description: "누적 500문제 정답", icon: "Award", check: { $0.totalCorrect >= 500 }),
        AchievementDef(id: "combo_10", name: "콤보 10", description: "한 세션에서 10연속 정답", icon: "Zap", check: { $0.maxCombo >= 10 }),
        AchievementDef(id: "combo_20", name: "콤보 20", description: "20연속 정답 달성", icon: "Flame", check: { $0.maxCombo >= 20 }),
        AchievementDef(id: "streak_3", name: "3일 연속", description: "3일 연속 학습", icon: "CalendarCheck", check: { $0.streak >= 3 }),
        AchievementDef(id: "streak_7", name: "일주일 개근", description: "7일 연속 학습", icon: "CalendarHeart", check: { $0.streak >= 7 }),
        AchievementDef(id: "streak_30", name: "한 달 개근", description: "30일 연속 학습", icon: "Crown", check: { $0.streak >= 30 }),
        AchievementDef(id: "first_master", name: "첫 마스터", description: "한 단을 마스터(별 획득)", icon: "Star", check: { masteredTables($0) >= 1 }),
        AchievementDef(id: "master_all", name: "구구단 완전정복", description: "2~9단 모두 마스터", icon: "GraduationCap", check: { masteredTables($0) >= 8 }),
        AchievementDef(id: "stars_12", name: "별 수집가", description: "별 12개 수집", icon: "Sparkles", check: { totalStars($0) >= 12 }),
        AchievementDef(id: "stars_24", name: "올스타", description: "모든 단 별 3개", icon: "Trophy", check: { totalStars($0) >= 24 }),
        AchievementDef(id: "level_5", name: "레벨 5", description: "레벨 5 도달", icon: "TrendingUp", check: { Level.info(totalXp: $0.totalXp).level >= 5 }),
        AchievementDef(id: "level_15", name: "레벨 15", description: "레벨 15 도달", icon: "Rocket", check: { Level.info(totalXp: $0.totalXp).level >= 15 }),
        AchievementDef(id: "challenge_15", name: "번개 계산", description: "60초 챌린지에서 15점 달성", icon: "Timer", check: { ($0.bestScores[.challenge] ?? 0) >= 15 }),
        AchievementDef(id: "survival_20", name: "생존왕", description: "서바이벌에서 20문제 생존", icon: "HeartPulse", check: { ($0.bestScores[.survival] ?? 0) >= 20 }),
        AchievementDef(id: "mode_explorer", name: "모드 탐험가", description: "모든 게임 모드를 플레이", icon: "Compass", check: { s in Modes.list.allSatisfy { s.modesPlayed.contains($0.id) } }),
    ]

    static func newlyUnlocked(_ state: GameState) -> [String] {
        let have = Set(state.unlockedAchievements)
        return all.filter { !have.contains($0.id) && $0.check(state) }.map { $0.id }
    }

    static func get(_ id: String) -> AchievementDef? {
        all.first { $0.id == id }
    }
}
