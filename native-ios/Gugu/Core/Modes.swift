import Foundation

// 게임 모드 레지스트리 — 모드별 규칙/메타데이터의 단일 출처 (modes.ts 이식)

enum ModeKind: String {
    case fixed    // 문제 수 고정
    case timed    // 제한 시간
    case lives    // 목숨제
}

struct ModeDef {
    let id: GameMode
    let name: String
    let tagline: String          // 모드 카드 한 줄 소개
    let detail: String           // 모드 선택 시 하단 설명
    let kind: ModeKind           // fixed/timed/lives
    let total: Int               // fixed: 세션 문제 수
    let timeLimitMs: Int?        // timed 전용
    let lives: Int?              // lives 전용
    let supportsTable: Bool      // 단 선택 지원 (false면 전체 랜덤 전용)
    let scored: Bool             // 최고 기록(점수=정답 수) 추적
    let xpBonus: Int             // 정답당 추가 XP
}

enum Modes {
    static let all: [GameMode: ModeDef] = [
        .practice: ModeDef(
            id: .practice,
            // 2.7.0 "학습" → "차근차근" — 탭 이름 '학습'과 겹쳐 헷갈렸다. 내부 id(practice)·저장값은 그대로
            name: String(localized: "mode.practice.name", defaultValue: "차근차근", comment: "모드 이름 — 단을 골라 천천히 푸는 연습 모드 (학습 탭 이름과 다름)"),
            tagline: String(localized: "천천히 또박또박", comment: "모드 카드 한 줄 소개 — 짧게"),
            detail: String(localized: "압박 없이 또박또박 — 틀린 문제는 다시 나와요.", comment: "모드 선택 시 하단 설명"),
            kind: .fixed, total: 10, timeLimitMs: nil, lives: nil,
            supportsTable: true, scored: false, xpBonus: 0),
        .timeAttack: ModeDef(
            id: .timeAttack, name: String(localized: "스피드런", comment: "모드 이름"),
            tagline: String(localized: "10문제 최고 속도", comment: "모드 카드 한 줄 소개 — 짧게"),
            detail: String(localized: "속도가 곧 점수! 빠르게 풀수록 XP가 올라가요.", comment: "모드 선택 시 하단 설명"),
            kind: .fixed, total: 10, timeLimitMs: nil, lives: nil,
            supportsTable: true, scored: false, xpBonus: 4),
        .challenge: ModeDef(
            id: .challenge, name: String(localized: "60초 챌린지", comment: "모드 이름"),
            tagline: String(localized: "제한 시간 최대 점수", comment: "모드 카드 한 줄 소개 — 짧게"),
            detail: String(localized: "60초 동안 몇 문제나 풀 수 있을까요? 최고 기록에 도전!", comment: "모드 선택 시 하단 설명"),
            kind: .timed, total: 0, timeLimitMs: 60_000, lives: nil,
            supportsTable: false, scored: true, xpBonus: 4),
        .survival: ModeDef(
            id: .survival, name: String(localized: "서바이벌", comment: "모드 이름"),
            tagline: String(localized: "기회는 단 3번", comment: "모드 카드 한 줄 소개 — 짧게"),
            detail: String(localized: "목숨 3개로 버티기! 틀릴 때마다 하트가 하나씩 사라져요.", comment: "모드 선택 시 하단 설명"),
            kind: .lives, total: 0, timeLimitMs: nil, lives: 3,
            supportsTable: false, scored: true, xpBonus: 3),
        .missing: ModeDef(
            id: .missing, name: String(localized: "빈칸 추리", comment: "모드 이름"),
            tagline: String(localized: "? 에 들어갈 수는", comment: "모드 카드 한 줄 소개 — 짧게"),
            detail: String(localized: "곱셈을 거꾸로! 빈칸의 수를 찾으면 XP 보너스가 커요.", comment: "모드 선택 시 하단 설명"),
            kind: .fixed, total: 10, timeLimitMs: nil, lives: nil,
            supportsTable: true, scored: false, xpBonus: 6),
        .truefalse: ModeDef(
            id: .truefalse, name: String(localized: "OX 퀴즈", comment: "모드 이름"),
            tagline: String(localized: "맞으면 O, 틀리면 X", comment: "모드 카드 한 줄 소개 — 짧게"),
            detail: String(localized: "식이 맞는지 순간 판단! 헷갈리는 한 끗 차이를 가려내요.", comment: "모드 선택 시 하단 설명"),
            kind: .fixed, total: 10, timeLimitMs: nil, lives: nil,
            supportsTable: true, scored: false, xpBonus: 0),
        .adventure: ModeDef(
            id: .adventure, name: String(localized: "어드벤처", comment: "모드 이름"),
            tagline: String(localized: "월드에서 대결", comment: "모드 카드 한 줄 소개 — 짧게"),
            detail: String(localized: "3D 월드 주민과 구구단 대결. 학습 모드 기록과는 따로 쌓여요.", comment: "모드 선택 시 하단 설명"),
            kind: .fixed, total: 0, timeLimitMs: nil, lives: nil,
            supportsTable: true, scored: false, xpBonus: 3),
        // 게임 판의 학습 기록용 — 이름·소개는 기록·알림에서 모드를 가리킬 때만 쓴다(카드 문구는 Features/Games/GameStyle).
        // 최고 기록은 게임 보상 저장소(PlayRewardsStore)가 따로 맡아 scored 는 false. 문제 수가 정해지지 않아 total 0.
        // XP 보너스는 0 — 게임은 별 코인으로 따로 보상하고, 빠르게 많이 푸는 판이라 정답당 보너스를 얹지 않는다
        .stairs: ModeDef(
            id: .stairs, name: String(localized: "구구 계단", comment: "게임 이름"),
            tagline: String(localized: "발판 점프", comment: "모드 카드 한 줄 소개 — 짧게"),
            detail: String(localized: "정답 발판으로 점프해 먹구름보다 높이 올라가요.", comment: "모드 선택 시 하단 설명"),
            kind: .fixed, total: 0, timeLimitMs: nil, lives: nil,
            supportsTable: true, scored: false, xpBonus: 0),
        .monster: ModeDef(
            id: .monster, name: String(localized: "구구 몬스터", comment: "게임 이름"),
            tagline: String(localized: "카드 2장 곱셈", comment: "모드 카드 한 줄 소개 — 짧게"),
            detail: String(localized: "곱해서 몬스터 숫자가 되는 카드 2장을 골라요.", comment: "모드 선택 시 하단 설명"),
            kind: .timed, total: 0, timeLimitMs: 60_000, lives: nil,
            supportsTable: true, scored: false, xpBonus: 0),
    ]

    /// 학습 탭에 노출하는 모드 (MODE_LIST). adventure 는 옛 3D 어드벤처 기록(2.9.0 제거, 저장 호환용), stairs·monster 는 게임 전용이라 제외.
    /// 업적 '모드 탐험가'의 목표치도 이 목록 수라 게임을 넣으면 기존 사용자 업적 기준이 바뀐다.
    static let list: [ModeDef] = [
        all[.practice]!, all[.timeAttack]!, all[.challenge]!,
        all[.survival]!, all[.missing]!, all[.truefalse]!,
    ]

    static func def(_ mode: GameMode) -> ModeDef { all[mode]! }

    /// 단 시트에 보여 줄 모드 — table nil('섞어서')이면 list 전체, 단을 골랐으면 단을 지원하는 모드만 (순서는 list 그대로)
    static func options(table: Int?) -> [ModeDef] {
        guard table != nil else { return list }
        return list.filter(\.supportsTable)
    }
}
