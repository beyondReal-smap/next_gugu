import Foundation

// 배틀 순수 로직 — 체력전(hp)/스피드 레이스(speed)/반격전(counter) 공통 상수·계산 (adventure/battle.ts 이식)

enum Battle {
    static let playerMaxHp = 100

    /// 정답 데미지: 기본 20 + 속도 보너스(2초 미만 +8, 3.5초 미만 +4) + 콤보 보너스(콤보당 +2, 최대 +10)
    static func damage(ms: Int, combo: Int) -> Int {
        let speedBonus = (ms > 0 && ms < 2000) ? 8 : (ms < 3500 ? 4 : 0)
        let comboBonus = min(10, max(0, combo - 1) * 2)
        return 20 + speedBonus + comboBonus
    }

    // --- 스피드 레이스: 먼저 목표 정답 수에 도달하면 승리 ---
    static let raceTarget = 7
    // NPC가 1문제를 "푸는" 간격 — 단이 높을수록 빨라짐 (2단 6.5초 → 9단 3.7초)
    static func racePaceMs(_ table: Int) -> Int { 6500 - (table - 2) * 400 }

    // --- 반격전: 제한시간 안에 못 풀면 오답 취급(반격) --- (2단 8초 → 9단 5.2초)
    static func counterLimitMs(_ table: Int) -> Int { 8000 - (table - 2) * 400 }

    // --- 보스 분노: HP가 비율 이하로 떨어지면 공격력 1.5배 ---
    static let bossEnrageRatio: Double = 0.5
    static func enragedAttack(_ attack: Int) -> Int { Int((Double(attack) * 1.5).rounded()) }

    // UI 공용 배틀 방식 이름
    static func styleName(_ style: BattleStyle) -> String {
        switch style {
        case .hp: return "체력전"
        case .speed: return "스피드 레이스"
        case .counter: return "반격전"
        }
    }

    /// 재대결 XP 배율 — 막지는 않되 무한 농장을 줄인다
    static let rematchXpScale: Double = 0.3

    /// 배틀 결과 커밋 모드 — 학습 모드 기록과 섞이지 않도록 adventure 전용
    static func mode(_ npc: NpcDef) -> GameMode { .adventure }

    static func toSessionResult(
        _ npc: NpcDef,
        answers: [AnswerRecord],
        maxCombo: Int,
        durationMs: Int,
        partial: Bool = false,
        rematch: Bool = false
    ) -> SessionResult {
        SessionResult(
            mode: mode(npc),
            table: npc.table,
            answers: answers,
            maxCombo: maxCombo,
            durationMs: durationMs,
            partial: partial,
            xpScale: rematch ? rematchXpScale : nil
        )
    }
}
