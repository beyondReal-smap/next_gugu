import Foundation

// 업적 정의 및 판정 (achievements.ts 이식)
// 모든 업적은 "측정값이 목표치에 도달"하는 형태다. 판정(check)과 진행도(progress)가
// 같은 metric/target 을 쓰게 해 두 곳의 기준이 어긋나지 않게 한다.

struct AchievementDef: Identifiable {
    let id: String
    let name: String
    let description: String
    let icon: String   // SF Symbols/에셋 아이콘 이름 (P2에서 매핑)
    let target: Int
    let metric: (GameState) -> Int

    func check(_ s: GameState) -> Bool { metric(s) >= target }
}

/// 업적 진행도 — current 는 target 을 넘지 않게 자른다
struct AchievementProgress: Equatable {
    let current: Int
    let target: Int

    var fraction: Double { target > 0 ? Double(current) / Double(target) : 0 }
}

enum Achievements {
    static func totalStars(_ s: GameState) -> Int {
        s.tableMastery.values.reduce(0) { $0 + $1.stars }
    }
    static func masteredTables(_ s: GameState) -> Int {
        s.tableMastery.values.filter { $0.stars >= 1 }.count
    }
    /// 학습 탭 모드 중 한 번이라도 플레이한 모드 수
    static func modesExplored(_ s: GameState) -> Int {
        Modes.list.filter { s.modesPlayed.contains($0.id) }.count
    }

    static let all: [AchievementDef] = [
        AchievementDef(id: "first_correct", name: "첫 정답", description: "처음으로 정답을 맞혔어요", icon: "Check", target: 1, metric: { $0.totalCorrect }),
        AchievementDef(id: "correct_100", name: "백 문제 돌파", description: "누적 100문제 정답", icon: "Target", target: 100, metric: { $0.totalCorrect }),
        AchievementDef(id: "correct_500", name: "오백 문제 돌파", description: "누적 500문제 정답", icon: "Award", target: 500, metric: { $0.totalCorrect }),
        AchievementDef(id: "combo_10", name: "콤보 10", description: "한 세션에서 10연속 정답", icon: "Zap", target: 10, metric: { $0.maxCombo }),
        AchievementDef(id: "combo_20", name: "콤보 20", description: "20연속 정답 달성", icon: "Flame", target: 20, metric: { $0.maxCombo }),
        AchievementDef(id: "streak_3", name: "3일 연속", description: "3일 연속 학습", icon: "CalendarCheck", target: 3, metric: { $0.streak }),
        AchievementDef(id: "streak_7", name: "일주일 개근", description: "7일 연속 학습", icon: "CalendarHeart", target: 7, metric: { $0.streak }),
        AchievementDef(id: "streak_30", name: "한 달 개근", description: "30일 연속 학습", icon: "Crown", target: 30, metric: { $0.streak }),
        AchievementDef(id: "first_master", name: "첫 마스터", description: "한 단을 마스터(별 획득)", icon: "Star", target: 1, metric: { masteredTables($0) }),
        AchievementDef(id: "master_all", name: "구구단 완전정복", description: "2~9단 모두 마스터", icon: "GraduationCap", target: 8, metric: { masteredTables($0) }),
        AchievementDef(id: "stars_12", name: "별 수집가", description: "별 12개 수집", icon: "Sparkles", target: 12, metric: { totalStars($0) }),
        AchievementDef(id: "stars_24", name: "올스타", description: "모든 단 별 3개", icon: "Trophy", target: 24, metric: { totalStars($0) }),
        AchievementDef(id: "level_5", name: "레벨 5", description: "레벨 5 도달", icon: "TrendingUp", target: 5, metric: { Level.info(totalXp: $0.totalXp).level }),
        AchievementDef(id: "level_15", name: "레벨 15", description: "레벨 15 도달", icon: "Rocket", target: 15, metric: { Level.info(totalXp: $0.totalXp).level }),
        AchievementDef(id: "challenge_15", name: "번개 계산", description: "60초 챌린지에서 15점 달성", icon: "Timer", target: 15, metric: { $0.bestScores[.challenge] ?? 0 }),
        AchievementDef(id: "survival_20", name: "생존왕", description: "서바이벌에서 20문제 생존", icon: "HeartPulse", target: 20, metric: { $0.bestScores[.survival] ?? 0 }),
        AchievementDef(id: "mode_explorer", name: "모드 탐험가", description: "모든 게임 모드를 플레이", icon: "Compass", target: Modes.list.count, metric: { modesExplored($0) }),
    ]

    static func newlyUnlocked(_ state: GameState) -> [String] {
        let have = Set(state.unlockedAchievements)
        return all.filter { !have.contains($0.id) && $0.check(state) }.map { $0.id }
    }

    static func get(_ id: String) -> AchievementDef? {
        all.first { $0.id == id }
    }

    /// 업적 진행도의 단일 기준 — 화면(프로필 타일·상세 시트)도 이것만 쓴다
    static func progress(_ def: AchievementDef, _ s: GameState) -> AchievementProgress {
        AchievementProgress(current: min(def.metric(s), def.target), target: def.target)
    }

    /// 업적 진행도. 알 수 없는 id 면 nil
    static func progress(_ id: String, _ s: GameState) -> AchievementProgress? {
        get(id).map { progress($0, s) }
    }
}
