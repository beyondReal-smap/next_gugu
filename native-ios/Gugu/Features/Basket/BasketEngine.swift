import Foundation
import QuartzCore
import Observation

// 구구 바구니 진행 상태 관리 (BasketScreen.tsx 의 상태/루프 부분 이식)
// 게임 규칙은 Core/Basket.swift 순수 함수가 담당하고, 여기서는 프레임 루프와 기록 저장만 맡는다.

@Observable
final class BasketEngine {
    private(set) var game: BasketState
    private(set) var bests: [String: Int]
    /// 선택한 단 (nil = 전체). 처음에는 2단부터 천천히.
    var table: Int?

    /// 방향 버튼을 누르고 있는 동안의 이동 방향 (-1 왼쪽 / 1 오른쪽 / 0 정지)
    var direction: Int = 0

    private var frameTimer: Timer?
    private var lastTick: Double?

    /// 표시용 — 단 선택 목록
    static let tables = Array(Problems.minTable...Problems.maxTable)
    /// 방향 버튼을 누르고 있을 때의 이동 속도(px/ms) — 웹과 동일
    private static let holdSpeed: Double = 0.28

    init() {
        self.table = 2
        self.game = Basket.create(table: 2, phase: .ready)
        self.bests = BasketEngine.loadBests()
    }

    // MARK: - 기록

    private static func loadBests() -> [String: Int] {
        let raw = Persistence.load([String: Int].self, key: Persistence.basketBestKey) ?? [:]
        let allowed = Set(["all"] + tables.map(String.init))
        return raw.filter { allowed.contains($0.key) && $0.value >= 0 }
    }

    private func bestKey(_ table: Int?) -> String { table.map(String.init) ?? "all" }

    /// 시작 화면은 고른 단, 진행 중에는 그 판의 단 기준 최고 기록을 보여 준다
    var displayBest: Int {
        let playing = game.phase != .ready && game.phase != .over
        return bests[bestKey(playing ? game.table : table)] ?? 0
    }
    var runBest: Int { bests[bestKey(game.table)] ?? 0 }

    private func recordBestIfNeeded() {
        let key = bestKey(game.table)
        guard game.score > (bests[key] ?? 0) else { return }
        bests[key] = game.score
        Persistence.save(bests, key: Persistence.basketBestKey)
    }

    // MARK: - 조작

    func start() {
        direction = 0
        game = Basket.create(table: table)
        Sound.shared.tap()
        startLoop()
    }

    /// 무대를 끌어서 옮기기 — 0~1 비율을 바구니 좌표로 바꾼다
    func drag(ratio: Double) {
        game = Basket.move(game, x: ratio * Basket.width)
    }

    /// 방향 버튼 한 번 탭 (보조 입력)
    func nudge(_ delta: Int) {
        game = Basket.move(game, x: game.x + Double(delta) * 24)
    }

    func togglePause() {
        switch game.phase {
        case .running:
            direction = 0
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
        direction = 0
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
        let dt = lastTick.map { min(now - $0, 80) } ?? 0
        lastTick = now
        guard dt > 0 else { return }

        let before = game
        if direction != 0 {
            game = Basket.move(game, x: game.x + Double(direction) * dt * BasketEngine.holdSpeed)
        }
        game = Basket.advance(game, dt: dt)

        if before.outcome == nil, let outcome = game.outcome {
            if outcome == .correct {
                Sound.shared.correct()
                Haptics.impact(.light)
                if game.combo >= 3 { Sound.shared.combo(game.combo) }
                recordBestIfNeeded()
            } else {
                Sound.shared.wrong()
                Haptics.error()
            }
        }
        if game.phase == .over {
            recordBestIfNeeded()
            Sound.shared.complete()
            stopLoop()
        }
    }
}
