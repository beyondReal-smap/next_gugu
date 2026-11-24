import Foundation

// 게임 한 판의 학습 기록 — 채점 기록을 앱 학습 기록(GameStore.commitSession: XP·오늘 목표·틀린 문제·서버 원장)에 한 번 넘긴다.
// 판이 끝날 때(결과·체험 끝) 완료로 한 번, 판 중간에 나가면 부분(partial)으로 한 번 — 어느 쪽이든 판마다 한 번뿐이다.
// 답이 하나도 없으면 넘기지 않는다(세션의 중도 이탈과 같은 규칙).
// 실제 커밋(저장·서버 적재)은 화면이 넘겨 준 commit 클로저가 맡고, 여기서는 언제·무엇을 넘길지만 정한다.

/// 학습 기록 커밋 — 화면이 GameStore.commitSession + SyncStore 적재를 묶어 넘긴다. 테스트는 결과를 모아 확인한다
typealias GameCommit = (SessionResult) -> CommitResult

struct RoundLearningLog {
    let mode: GameMode
    /// 단 지정 판이면 그 단(서버 tableNo — 마스터리 별은 주지 않음, GameMode.isGame), 단이 바뀌거나 섞인 판은 nil
    let table: Int?

    /// 이번 판에 넘긴 결과 — 넘기기 전이면 nil
    private(set) var commit: CommitResult?
    /// 이번 판을 이미 넘겼는지(답이 없어 넘길 것이 없던 경우 포함)
    private(set) var closed = false

    init(mode: GameMode, table: Int?) {
        precondition(mode == .stairs || mode == .monster, "게임 판 학습 기록은 stairs·monster 만: \(mode)")
        self.mode = mode
        self.table = table
    }

    /// 새 판 — 다시 하기
    mutating func reset() {
        commit = nil
        closed = false
    }

    /// 판을 닫으며 한 번 넘긴다. partial: 판이 끝나기 전에 나감. 이미 닫았으면 아무것도 하지 않는다
    @discardableResult
    mutating func close(answers: [AnswerRecord], maxCombo: Int, durationMs: Int, partial: Bool,
                        using commitSession: GameCommit) -> CommitResult? {
        guard !closed else { return commit }
        closed = true
        guard !answers.isEmpty else { return nil }
        let result = SessionResult(mode: mode, table: table, answers: answers, maxCombo: maxCombo,
                                   durationMs: durationMs, partial: partial)
        let done = commitSession(result)
        commit = done
        return done
    }
}
