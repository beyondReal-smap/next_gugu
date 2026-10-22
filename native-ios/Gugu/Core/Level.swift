import Foundation

// 레벨 / 경험치(XP) 시스템 (level.ts 이식)

struct LevelInfo {
    var level: Int
    var totalXp: Int
    var currentLevelXp: Int
    var xpForNextLevel: Int
    var progress: Double   // 0~1
}

enum Level {
    /// 레벨 L에 도달하기 위한 누적 XP
    static func xpToReach(_ level: Int) -> Int {
        var total = 0
        var l = 1
        while l < level {
            total += 60 + (l - 1) * 35
            l += 1
        }
        return total
    }

    static func xpForSpan(_ level: Int) -> Int {
        60 + (level - 1) * 35
    }

    static func info(totalXp: Int) -> LevelInfo {
        let xp = max(0, totalXp)
        var level = 1
        while xp >= xpToReach(level + 1) {
            level += 1
            if level > 999 { break }
        }
        let base = xpToReach(level)
        let span = xpForSpan(level)
        return LevelInfo(
            level: level,
            totalXp: xp,
            currentLevelXp: xp - base,
            xpForNextLevel: span,
            progress: min(1, Double(xp - base) / Double(span))
        )
    }

    /// 정답 1개의 XP (모드/콤보/속도 보너스)
    static func xpForAnswer(mode: GameMode, combo: Int, ms: Int) -> Int {
        let base = 10
        let comboBonus = min(15, max(0, combo - 2) * 2)
        let speedBonus = (ms > 0 && ms < 2000) ? 5 : (ms < 3500 ? 2 : 0)
        return base + comboBonus + speedBonus + Modes.def(mode).xpBonus
    }

    static func title(_ level: Int) -> String {
        if level >= 30 { return String(localized: "구구단 레전드", comment: "레벨 칭호") }
        if level >= 22 { return String(localized: "연산 마스터", comment: "레벨 칭호") }
        if level >= 15 { return String(localized: "곱셈 챔피언", comment: "레벨 칭호") }
        if level >= 10 { return String(localized: "숫자 전략가", comment: "레벨 칭호") }
        if level >= 6 { return String(localized: "구구단 러너", comment: "레벨 칭호") }
        if level >= 3 { return String(localized: "성장하는 학습자", comment: "레벨 칭호") }
        return String(localized: "구구단 입문자", comment: "레벨 칭호")
    }
}
