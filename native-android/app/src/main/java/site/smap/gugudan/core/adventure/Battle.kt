package site.smap.gugudan.core.adventure

import site.smap.gugudan.core.AnswerRecord
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.SessionResult

// 배틀 순수 로직 — HP/SPEED/COUNTER 공통 상수·계산 (Battle.swift 이식)

object Battle {
    const val PLAYER_MAX_HP = 100

    /** 정답 데미지: 기본 20 + 속도 보너스(2초 미만 +8, 3.5초 미만 +4) + 콤보 보너스(콤보당 +2, 최대 +10) */
    fun damage(ms: Int, combo: Int): Int {
        val speedBonus = if (ms in 1..1999) 8 else if (ms < 3500) 4 else 0
        val comboBonus = minOf(10, maxOf(0, combo - 1) * 2)
        return 20 + speedBonus + comboBonus
    }

    // --- 스피드 레이스: 먼저 목표 정답 수에 도달하면 승리 ---
    const val RACE_TARGET = 7
    /** NPC가 1문제를 "푸는" 간격 — 단이 높을수록 빨라짐 (2단 6.5초 → 9단 3.7초) */
    fun racePaceMs(table: Int): Int = 6500 - (table - 2) * 400

    // --- 반격전: 제한시간 안에 못 풀면 오답 취급 --- (2단 8초 → 9단 5.2초)
    fun counterLimitMs(table: Int): Int = 8000 - (table - 2) * 400

    // --- 보스 분노: HP가 비율 이하로 떨어지면 공격력 1.5배 ---
    const val BOSS_ENRAGE_RATIO = 0.5
    fun enragedAttack(attack: Int): Int = Math.round(attack * 1.5).toInt()

    fun styleName(style: BattleStyle): String = when (style) {
        BattleStyle.HP -> "체력전"
        BattleStyle.SPEED -> "스피드 레이스"
        BattleStyle.COUNTER -> "반격전"
    }

    /** 재대결 XP 배율 — 막지는 않되 무한 농장을 줄인다 */
    const val REMATCH_XP_SCALE = 0.3

    /** 배틀 결과 커밋 모드 — 학습 모드 기록과 섞이지 않도록 adventure 전용 */
    fun mode(npc: NpcDef): GameMode = GameMode.ADVENTURE

    fun toSessionResult(
        npc: NpcDef,
        answers: List<AnswerRecord>,
        maxCombo: Int,
        durationMs: Int,
        partial: Boolean = false,
        rematch: Boolean = false,
    ) = SessionResult(
        mode = mode(npc),
        table = npc.table,
        answers = answers,
        maxCombo = maxCombo,
        durationMs = durationMs,
        partial = partial,
        xpScale = if (rematch) REMATCH_XP_SCALE else null,
    )
}
