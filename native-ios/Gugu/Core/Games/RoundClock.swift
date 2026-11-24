import Foundation

// A·B 게임 공통 시계 (Unity 시험판 Core/RoundClock.cs 이식)
// 첫 동작에 시작, 오답 공개 0.6초 동안 입력 잠금, 종료 순간 확정.
// B 는 60초 제한. A 는 제한 없이(unlimited) 돌다가 먹구름에 잡히는 순간 end() 로 끝난다.
// 시간 단위는 초. 쉬는 창이 열린 동안에는 화면이 tick 을 부르지 않아 시계가 멈춘다.

enum RoundPhase: Equatable {
    case waiting   // 첫 동작 전 — 시간이 아직 흐르지 않음
    case running
    case over
}

/// 입력을 채점하지 않은 이유 (받아들였으면 nil)
enum ActionReject: Equatable {
    case over       // 판이 끝난 뒤
    case revealing  // 오답 뒤 정답을 보여 주는 중
    case invalid    // 없는 칸·카드
}

struct RoundClock {
    static let durationSec: Double = 60
    static let revealSec: Double = 0.6
    static let unlimited: Double = .infinity

    let limitSec: Double
    private(set) var phase: RoundPhase = .waiting
    private(set) var elapsed: Double = 0
    private(set) var revealRemaining: Double = 0

    init(limitSec: Double = RoundClock.durationSec) {
        precondition(limitSec > 0, "limitSec 는 0보다 커야 함: \(limitSec)")
        self.limitSec = limitSec
    }

    /// 남은 시간(초) — 무제한이면 +∞
    var remaining: Double { max(0, limitSec - elapsed) }
    var isRevealing: Bool { revealRemaining > 0 }

    /// 이 입력을 받을 수 있는지. 순서: 종료 → 없는 입력 → 공개 중
    func check(valid: Bool) -> ActionReject? {
        if phase == .over { return .over }
        if !valid { return .invalid }
        if isRevealing { return .revealing }
        return nil
    }

    mutating func markAction() {
        if phase == .waiting { phase = .running }
    }

    mutating func beginReveal() {
        revealRemaining = Self.revealSec
    }

    /// 시간과 무관한 종료(A: 먹구름에 잡힘). 진행 중에만 부를 수 있다 — 그 밖의 호출은 규칙 쪽 버그
    mutating func end() {
        precondition(phase == .running, "end() 는 진행 중에만 부를 수 있음: \(phase)")
        revealRemaining = 0
        phase = .over
    }

    /// dt(초) 만큼 흘린다. 오답 공개가 이번 tick 에 끝났으면 true (시간 종료로 끝난 경우는 false)
    @discardableResult
    mutating func tick(_ dt: Double) -> Bool {
        precondition(dt >= 0, "dt 는 0 이상이어야 함: \(dt)")  // NaN 도 여기서 걸린다
        guard phase == .running, dt > 0 else { return false }

        elapsed += dt
        if elapsed >= limitSec {
            elapsed = limitSec
            revealRemaining = 0
            phase = .over
            return false
        }
        guard revealRemaining > 0 else { return false }
        revealRemaining -= dt
        if revealRemaining > 0 { return false }
        revealRemaining = 0
        return true
    }
}
