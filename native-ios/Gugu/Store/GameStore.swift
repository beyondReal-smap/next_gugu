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
        #if DEBUG
        // 캡처용 진행 상태(SIMCTL_CHILD_GUGU_SEED=v1|max, App/DebugSeed) — 저장본을 덮어쓴다
        if let seed = ProcessInfo.processInfo.environment["GUGU_SEED"] { s = DebugSeed.state(seed, today: Commit.todayStr()) }
        #endif
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

    /// 로그인한 계정의 서버 기록으로 약한 문제를 채운다 (기기 값과 문제별 max 병합)
    func mergeRestoredWrongPool(_ facts: [String: LearningFactStats]) {
        state.wrongPool = LearningRestore.merge(local: state.wrongPool, server: facts)
        persist()
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
        // 사용자가 기록을 지웠다면 손상 저장본의 격리 사본도 남기지 않는다
        Persistence.clearQuarantine(key: Persistence.progressKey)
    }
}
