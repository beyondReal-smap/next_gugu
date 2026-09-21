import Foundation

// 구구 바구니 — 떨어지는 열매 중 정답을 바구니로 받는다 (lib/basket.ts 이식)
// 좌표계는 웹 SVG viewBox(360×380) 기준. 렌더러가 비율만 맞춰 그린다.

enum BasketOutcome: String { case correct, wrong, missed }

struct BasketState {
    var phase: RunnerPhase
    var table: Int?
    var question: RunnerQuestion
    var recentKeys: [String]
    /// 바구니 가로 위치
    var x: Double
    var round: Int
    var elapsedMs: Double
    var feedbackMs: Double
    var outcome: BasketOutcome?
    var caughtIndex: Int?
    var score: Int
    var lives: Int
    var combo: Int
    var maxCombo: Int
}

enum Basket {
    static let width: Double = 360
    static let minX: Double = 36
    static let maxX: Double = width - minX
    static let fruitStartY: Double = 58
    static let fruitCatchY: Double = 304
    static let correctFeedbackMs: Double = 450
    static let wrongFeedbackMs: Double = 1500
    /// 열매 중심과 바구니 중심이 이만큼 안쪽이면 받은 것으로 본다
    static let catchRadius: Double = 40
    static let maxLives = 3

    static func create(table: Int?, phase: RunnerPhase = .running, random: () -> Double = { Double.random(in: 0..<1) }) -> BasketState {
        BasketState(
            phase: phase, table: table,
            // ready 상태의 문제는 화면 예시용 — 시작 시 새로 뽑는다
            question: phase == .ready
                ? RunnerQuestion(a: 2, b: 3, choices: [4, 6, 8])
                : Runner.question(table: table, recentKeys: [], random: random),
            recentKeys: [], x: width / 2, round: 0, elapsedMs: 0, feedbackMs: 0,
            outcome: nil, caughtIndex: nil, score: 0, lives: maxLives, combo: 0, maxCombo: 0
        )
    }

    static func level(score: Int) -> Int { score / 3 + 1 }

    static func fallDurationMs(score: Int) -> Double {
        max(2200, 3000 - Double(level(score: score) - 1) * 300)
    }

    /// 열매 index 의 가로 위치 — 살짝 좌우로 흔들린다
    static func fruitX(index: Int, elapsedMs: Double) -> Double {
        64 + Double(index) * 116 + sin(elapsedMs / 750 + Double(index) * 2) * 12
    }

    static func move(_ state: BasketState, x: Double) -> BasketState {
        guard state.phase == .running, state.outcome == nil, x.isFinite else { return state }
        var next = state
        next.x = max(minX, min(maxX, x))
        return next
    }

    static func advance(_ state: BasketState, dt: Double, random: () -> Double = { Double.random(in: 0..<1) }) -> BasketState {
        guard state.phase == .running, dt.isFinite, dt > 0 else { return state }

        if state.outcome != nil {
            var next = state
            next.feedbackMs = max(0, state.feedbackMs - dt)
            if next.feedbackMs > 0 { return next }
            if state.lives == 0 {
                next.phase = .over
                return next
            }
            next.recentKeys = Array((state.recentKeys + [Problems.key(state.question.a, state.question.b)]).suffix(4))
            next.question = Runner.question(table: state.table, recentKeys: next.recentKeys, random: random)
            next.round = state.round + 1
            next.elapsedMs = 0
            next.outcome = nil
            next.caughtIndex = nil
            return next
        }

        let duration = fallDurationMs(score: state.score)
        var next = state
        next.elapsedMs = min(duration, state.elapsedMs + dt)
        if next.elapsedMs < duration { return next }

        // 열매가 바구니 높이를 지나는 순간 한 번만 판정한다
        let caught = state.question.choices.indices.first { abs(fruitX(index: $0, elapsedMs: duration) - state.x) <= catchRadius }
        let correct = caught.map { state.question.choices[$0] == state.question.answer } ?? false
        next.caughtIndex = caught
        next.outcome = correct ? .correct : (caught != nil ? .wrong : .missed)
        next.feedbackMs = correct ? correctFeedbackMs : wrongFeedbackMs
        next.score = state.score + (correct ? 1 : 0)
        next.lives = state.lives - (correct ? 0 : 1)
        next.combo = correct ? state.combo + 1 : 0
        next.maxCombo = max(state.maxCombo, next.combo)
        return next
    }
}
