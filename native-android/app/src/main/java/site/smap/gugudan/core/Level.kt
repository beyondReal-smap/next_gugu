package site.smap.gugudan.core

// 레벨 / 경험치(XP) 시스템 (Level.swift 이식)

data class LevelInfo(
    val level: Int,
    val totalXp: Int,
    val currentLevelXp: Int,
    val xpForNextLevel: Int,
    val progress: Double,   // 0~1
)

object Level {
    /** 레벨 L에 도달하기 위한 누적 XP */
    fun xpToReach(level: Int): Int {
        var total = 0
        var l = 1
        while (l < level) {
            total += 60 + (l - 1) * 35
            l += 1
        }
        return total
    }

    fun xpForSpan(level: Int): Int = 60 + (level - 1) * 35

    fun info(totalXp: Int): LevelInfo {
        val xp = maxOf(0, totalXp)
        var level = 1
        while (xp >= xpToReach(level + 1)) {
            level += 1
            if (level > 999) break
        }
        val base = xpToReach(level)
        val span = xpForSpan(level)
        return LevelInfo(
            level = level,
            totalXp = xp,
            currentLevelXp = xp - base,
            xpForNextLevel = span,
            progress = minOf(1.0, (xp - base).toDouble() / span),
        )
    }

    /** 정답 1개의 XP (모드/콤보/속도 보너스) */
    fun xpForAnswer(mode: GameMode, combo: Int, ms: Int): Int {
        val base = 10
        val comboBonus = minOf(15, maxOf(0, combo - 2) * 2)
        val speedBonus = if (ms in 1..1999) 5 else if (ms < 3500) 2 else 0
        return base + comboBonus + speedBonus + Modes.def(mode).xpBonus
    }

    fun title(level: Int): String = when {
        level >= 30 -> "구구단 레전드"
        level >= 22 -> "연산 마스터"
        level >= 15 -> "곱셈 챔피언"
        level >= 10 -> "숫자 전략가"
        level >= 6 -> "구구단 러너"
        level >= 3 -> "성장하는 학습자"
        else -> "구구단 입문자"
    }
}
