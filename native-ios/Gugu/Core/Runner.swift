import Foundation

// 구구 점프 — 달리기 미니게임 순수 로직 (lib/runner.ts 이식)
// 좌표계는 웹 SVG viewBox(720×260) 기준을 그대로 쓴다. 렌더러가 비율만 맞춰 그린다.

enum RunnerPhase: String { case ready, running, paused, over }
enum RunnerOutcome: String { case correct, wrong, missed }

struct RunnerQuestion: Equatable {
    var a: Int
    var b: Int
    var choices: [Int]

    var answer: Int { a * b }
}

struct RunnerState {
    var phase: RunnerPhase
    var table: Int?              // nil = 전체 구구단
    var question: RunnerQuestion
    var recentKeys: [String]
    var round: Int
    var score: Int
    var lives: Int
    var combo: Int
    var maxCombo: Int
    var distance: Double
    var obstacleX: Double
    var outcome: RunnerOutcome?
    var given: Int?
    var hitMs: Double
}

enum Runner {
    static let obstacleStart: Double = 690
    static let collisionX: Double = 154
    static let jumpStart: Double = 280
    static let jumpEnd: Double = 30
    /// 피격 연출/정답 공개 유지 시간
    static let hitDurationMs: Double = 1400
    static let maxLives = 3
    /// 장애물 3개를 넘을 때마다 한 단계 빨라진다
    static let scorePerLevel = 3
    /// 정답 뒤 장애물을 넘어가는 속도(px/ms)
    private static let clearSpeed: Double = 0.45

    static func shuffled(_ values: [Int], random: () -> Double = { Double.random(in: 0..<1) }) -> [Int] {
        var result = values
        var i = result.count - 1
        while i > 0 {
            let j = Int(random() * Double(i + 1))
            result.swapAt(i, min(j, i))
            i -= 1
        }
        return result
    }

    /// 정답 1개 + 그럴듯한 오답 2개를 섞은 보기
    static func question(table: Int?, recentKeys: [String], random: () -> Double = { Double.random(in: 0..<1) }) -> RunnerQuestion {
        let problem = Problems.pick(table: table, wrongPool: [:], recentKeys: recentKeys, random: random)
        let answer = problem.a * problem.b
        var seen = Set<Int>()
        let alternatives = [
            answer - problem.a, answer + problem.a, answer - problem.b,
            answer + problem.b, answer - 1, answer + 1,
        ].filter { value in
            guard value > 0, value <= 81, value != answer else { return false }
            return seen.insert(value).inserted
        }
        let picked = Array(shuffled(alternatives, random: random).prefix(2))
        return RunnerQuestion(a: problem.a, b: problem.b, choices: shuffled([answer] + picked, random: random))
    }

    static func create(table: Int?, phase: RunnerPhase = .running, random: () -> Double = { Double.random(in: 0..<1) }) -> RunnerState {
        RunnerState(
            phase: phase,
            table: table,
            // ready 상태의 문제는 화면 예시용 — 시작 시 새로 뽑는다
            question: phase == .ready
                ? RunnerQuestion(a: 2, b: 3, choices: [4, 6, 8])
                : question(table: table, recentKeys: [], random: random),
            recentKeys: [], round: 0, score: 0, lives: maxLives, combo: 0, maxCombo: 0,
            distance: 0, obstacleX: obstacleStart, outcome: nil, given: nil, hitMs: 0
        )
    }

    static func level(score: Int) -> Int { score / scorePerLevel }

    /// 정답 제한 시간 — 한 단계 오를 때마다 500ms 짧아지고 2.8초가 하한
    static func answerWindowMs(score: Int) -> Double {
        max(2800, 5000 - Double(level(score: score)) * 500)
    }

    /// 방금 넘은 장애물로 속도 단계가 올랐는지 (다음 장애물이 막 나온 순간)
    static func leveledUp(_ state: RunnerState) -> Bool {
        state.combo > 0 && state.outcome == nil && state.score > 0 && state.score % scorePerLevel == 0
    }

    static func answer(_ state: RunnerState, choice: Int) -> RunnerState {
        guard state.phase == .running, state.outcome == nil, state.question.choices.contains(choice) else { return state }
        var next = state
        next.given = choice
        next.outcome = choice == state.question.answer ? .correct : .wrong
        return next
    }

    private static func nextObstacle(_ state: RunnerState, random: () -> Double) -> RunnerState {
        var next = state
        next.recentKeys = Array((state.recentKeys + [Problems.key(state.question.a, state.question.b)]).suffix(3))
        next.question = question(table: state.table, recentKeys: next.recentKeys, random: random)
        next.round = state.round + 1
        next.obstacleX = obstacleStart
        next.outcome = nil
        next.given = nil
        next.hitMs = 0
        return next
    }

    /// dt(ms) 만큼 진행. 피격 연출 중이면 연출 시간만 흘리고, 통과하면 점수·콤보를 올린다.
    static func advance(_ state: RunnerState, dt: Double, random: () -> Double = { Double.random(in: 0..<1) }) -> RunnerState {
        guard state.phase == .running, dt.isFinite, dt > 0 else { return state }

        if state.hitMs > 0 {
            let hitMs = max(0, state.hitMs - dt)
            if hitMs > 0 {
                var next = state
                next.hitMs = hitMs
                return next
            }
            if state.lives == 0 {
                var next = state
                next.hitMs = 0
                next.phase = .over
                return next
            }
            return nextObstacle(state, random: random)
        }

        // 정답을 고르기 전에는 제한 시간에 맞춘 속도, 정답 후에는 느리게 흘려보낸다
        let speed = state.outcome == nil
            ? (obstacleStart - collisionX) / answerWindowMs(score: state.score)
            : clearSpeed
        let movement = speed * dt
        var next = state
        next.obstacleX = state.obstacleX - movement
        next.distance = state.distance + movement / 20

        if next.obstacleX <= collisionX && next.outcome != .correct {
            next.obstacleX = collisionX
            next.outcome = next.outcome ?? .missed
            next.lives = next.lives - 1
            next.combo = 0
            next.hitMs = hitDurationMs
            return next
        }
        if next.obstacleX < -40 {
            next.score = next.score + 1
            next.combo = next.combo + 1
            next.maxCombo = max(next.maxCombo, next.combo)
            return nextObstacle(next, random: random)
        }
        return next
    }

    /// 장애물 위치에 점프 높이를 연결해 속도가 올라가도 같은 궤적으로 넘는다
    static func jump(_ state: RunnerState) -> Double {
        guard state.outcome == .correct, state.obstacleX <= jumpStart, state.obstacleX >= jumpEnd else { return 0 }
        return sin(Double.pi * (jumpStart - state.obstacleX) / (jumpStart - jumpEnd)) * 96
    }

    /// 남은 시간 비율 (1 = 방금 등장, 0 = 충돌 지점)
    static func remaining(_ state: RunnerState) -> Double {
        min(1, max(0, (state.obstacleX - collisionX) / (obstacleStart - collisionX)))
    }
}
