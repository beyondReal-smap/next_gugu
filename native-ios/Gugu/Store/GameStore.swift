import Foundation
import Observation

// 게임 진행 상태 (GameProvider.tsx 이식) — @Observable + UserDefaults 영속화

@Observable
final class GameStore {
    private(set) var state: GameState
    private(set) var loaded: Bool = false

    var levelInfo: LevelInfo { Level.info(totalXp: state.totalXp) }

    init() {
        // 로드 + 방문(스트릭/데일리) 갱신
        var s = Persistence.load(GameState.self, key: Persistence.progressKey) ?? Commit.defaultState
        s = Commit.applyVisit(s)
        self.state = s
        self.loaded = true
        persist()
    }

    private func persist() {
        Persistence.save(state, key: Persistence.progressKey)
    }

    @discardableResult
    func commitSession(_ result: SessionResult) -> CommitResult {
        let (next, commit) = Commit.applySession(state, result: result)
        state = next
        persist()
        return commit
    }

    /// 세션 외 소량 XP 지급 (별 조각 등) — 통계(정확도/오답풀)는 건드리지 않음
    @discardableResult
    func grantXp(_ amount: Int) -> Bool {
        let prevLevel = Level.info(totalXp: state.totalXp).level
        state.totalXp += amount
        persist()
        return Level.info(totalXp: state.totalXp).level > prevLevel
    }

    func setDailyGoal(_ goal: Int) {
        state.dailyGoal = max(5, goal)
        persist()
    }

    func setOnboarded(_ v: Bool) {
        state.onboarded = v
        persist()
    }

    func resetProgress() {
        var s = Commit.defaultState
        s.onboarded = true
        state = Commit.applyVisit(s)
        persist()
    }
}
