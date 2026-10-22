package site.smap.gugudan.core

import androidx.annotation.StringRes
import site.smap.gugudan.R

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

    /** 레벨 칭호 — 화면에서 stringResource 로 읽는다 */
    @StringRes
    fun titleRes(level: Int): Int = when {
        level >= 30 -> R.string.times_table_legend
        level >= 22 -> R.string.math_master
        level >= 15 -> R.string.multiplication_champ
        level >= 10 -> R.string.number_strategist
        level >= 6 -> R.string.times_table_runner
        level >= 3 -> R.string.growing_learner
        else -> R.string.times_table_beginner
    }
}
