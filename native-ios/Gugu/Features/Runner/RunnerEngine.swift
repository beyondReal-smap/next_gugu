import Foundation
import QuartzCore
import Observation

// 구구 점프 진행 상태 관리 (RunnerScreen.tsx 의 상태/루프 부분 이식)
// 게임 규칙은 Core/Runner.swift 순수 함수가 담당하고, 여기서는 프레임 루프와 기록 저장만 맡는다.

@Observable
final class RunnerEngine {
    private(set) var game: RunnerState
    private(set) var bests: [String: Int]
    /// 선택한 단 (nil = 전체)
    var table: Int?

    private var frameTimer: Timer?
    private var lastTick: Double?

    /// 표시용 — 단 선택 목록
    static let tables = Array(Problems.minTable...Problems.maxTable)

    init() {
        self.table = nil
        self.game = Runner.create(table: nil, phase: .ready)
        self.bests = RunnerEngine.loadBests()
    }

    // MARK: - 기록

    private static func loadBests() -> [String: Int] {
        let raw = Persistence.load([String: Int].self, key: Persistence.runnerBestKey) ?? [:]
        let allowed = Set(["all"] + tables.map(String.init))
        return raw.filter { allowed.contains($0.key) && $0.value >= 0 }
    }

    private func bestKey(_ table: Int?) -> String { table.map(String.init) ?? "all" }

    /// 선택한 단의 최고 기록 (시작 화면 표시용)
    var selectedBest: Int { bests[bestKey(table)] ?? 0 }
    /// 진행 중인 판의 단 기준 최고 기록
    var runBest: Int { bests[bestKey(game.table)] ?? 0 }

    private func recordBestIfNeeded() {
        let key = bestKey(game.table)
        guard game.score > (bests[key] ?? 0) else { return }
        bests[key] = game.score
        Persistence.save(bests, key: Persistence.runnerBestKey)
    }

    // MARK: - 조작

    func start() {
        game = Runner.create(table: table)
        Sound.shared.tap()
        startLoop()
    }

    func answer(index: Int) {
        guard game.phase == .running, game.outcome == nil,
              index >= 0, index < game.question.choices.count else { return }
        let before = game.outcome
        game = Runner.answer(game, choice: game.question.choices[index])
        guard before != game.outcome else { return }
        if game.outcome == .correct {
            Sound.shared.correct()
            Haptics.impact(.light)
        } else {
            Sound.shared.wrong()
            Haptics.error()
        }
    }

    func togglePause() {
        switch game.phase {
        case .running:
            game.phase = .paused
            stopLoop()
        case .paused:
            game.phase = .running
            startLoop()
        default:
            break
        }
    }

    /// 백그라운드 전환·화면 이탈 시 자동 일시정지
    func pause() {
        guard game.phase == .running else { return }
        game.phase = .paused
        stopLoop()
    }

    func teardown() { stopLoop() }

    // MARK: - 프레임 루프

    private func startLoop() {
        stopLoop()
        lastTick = nil
        let timer = Timer(timeInterval: 1.0 / 60.0, repeats: true) { [weak self] _ in
            self?.tick()
        }
        RunLoop.main.add(timer, forMode: .common)
        frameTimer = timer
    }

    private func stopLoop() {
        frameTimer?.invalidate()
        frameTimer = nil
        lastTick = nil
    }

    private func tick() {
        guard game.phase == .running else {
            stopLoop()
            return
        }
        let now = CACurrentMediaTime() * 1000
        // 복귀 직후나 긴 프레임 때문에 장애물이 한 번에 통과하지 않도록 제한한다
        let dt = lastTick.map { min(now - $0, 80) } ?? 0
        lastTick = now
        guard dt > 0 else { return }

        let before = game
        game = Runner.advance(game, dt: dt)

        if game.score > before.score {
            Sound.shared.tap()
            recordBestIfNeeded()
        }
        if game.hitMs > 0 && before.hitMs == 0 {
            Haptics.error()
        }
        if game.phase == .over {
            recordBestIfNeeded()
            Sound.shared.complete()
            stopLoop()
        }
    }
}
