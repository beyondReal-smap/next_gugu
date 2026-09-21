import Foundation

// 구구 레인 — 레인 3개를 오가며 장애물을 피하고, 정답 숫자가 적힌 레인으로 통과한다 (lib/laneRunner.ts 이식)
// 좌표계는 웹 SVG viewBox(720×360) 기준을 그대로 쓴다. 렌더러가 비율만 맞춰 그린다.

enum LaneSegment: String { case dodge, quiz }
enum LaneOutcome: String { case correct, wrong, hit }
enum LaneObstacleKind: String { case rock, stump }

struct LaneObstacle: Identifiable, Equatable {
    var id: Int
    var lane: Int
    var x: Double
    var kind: LaneObstacleKind
}

struct LaneGate: Equatable {
    var x: Double
    var startX: Double
    /// values[lane]
    var values: [Int]
}

struct LaneRunnerState {
    var phase: RunnerPhase
    var segment: LaneSegment
    var table: Int?
    var lane: Int
    /// 전환 시작 시점의 화면상 레인 위치(실수 — 전환 중 재전환도 이어서 보간)
    var laneFrom: Double
    var laneAnimMs: Double
    var obstacles: [LaneObstacle]
    var nextId: Int
    var spawnInMs: Double
    var spawned: Int
    var lastFree: [Int]
    var question: RunnerQuestion?
    var gate: LaneGate?
    var recentKeys: [String]
    var outcome: LaneOutcome?
    var given: Int?
    var score: Int
    var dodged: Int
    var combo: Int
    var maxCombo: Int
    var lives: Int
    var distance: Double
    var hitMs: Double
}

enum LaneRunner {
    /// 0 = 위(먼 쪽), 2 = 아래(가까운 쪽)
    static let lanes = [0, 1, 2]
    /// 장애물·게이트가 나타나는 x
    static let spawnX: Double = 740
    /// 공룡 몸통과 겹쳐 판정하는 x
    static let judgeX: Double = 150
    /// 화면 밖으로 사라지는 x
    static let removeX: Double = -90
    /// 충돌·오답 뒤 월드 정지 시간
    static let hitMs: Double = 1400
    static let laneAnimMs: Double = 140
    /// 판정한 게이트가 이만큼 지나가면 다음 장애물을 내보낸다
    static let nextCycleX: Double = judgeX - 120
    /// 정답 길 3개마다 한 단계 빨라진다
    static let scorePerLevel = 3
    static let maxLives = 3

    static func level(score: Int) -> Int { score / scorePerLevel }

    /// 방금 통과한 정답으로 속도 단계가 올랐는지
    static func leveledUp(_ state: LaneRunnerState) -> Bool {
        state.outcome == .correct && state.score > 0 && state.score % scorePerLevel == 0
    }

    /// 장애물이 나타나서 판정선에 닿기까지 걸리는 시간
    static func travelMs(score: Int) -> Double {
        max(1900, 3400 - Double(level(score: score)) * 250)
    }

    /// 문제가 뜬 뒤 게이트가 판정선에 닿기까지 걸리는 시간 (항상 travelMs 이상이라 게이트는 화면 밖에서 등장)
    static func quizWindowMs(score: Int) -> Double {
        max(3500, 5000 - Double(level(score: score)) * 200)
    }

    static func spawnGapMs(score: Int) -> Double {
        max(750, 1200 - Double(level(score: score)) * 70)
    }

    static func obstaclesPerCycle(score: Int) -> Int {
        min(4, 2 + level(score: score) / 2)
    }

    static func speed(score: Int) -> Double {
        (spawnX - judgeX) / travelMs(score: score)
    }

    static func create(table: Int?, phase: RunnerPhase = .running) -> LaneRunnerState {
        LaneRunnerState(
            phase: phase, segment: .dodge, table: table,
            lane: 1, laneFrom: 1, laneAnimMs: 0,
            obstacles: [], nextId: 0, spawnInMs: 800, spawned: 0, lastFree: lanes,
            question: nil, gate: nil, recentKeys: [], outcome: nil, given: nil,
            score: 0, dodged: 0, combo: 0, maxCombo: 0, lives: maxLives, distance: 0, hitMs: 0
        )
    }

    static func setLane(_ state: LaneRunnerState, lane: Int) -> LaneRunnerState {
        guard state.phase == .running, lanes.contains(lane), lane != state.lane else { return state }
        var next = state
        next.laneFrom = offset(state)
        next.lane = lane
        next.laneAnimMs = laneAnimMs
        return next
    }

    static func move(_ state: LaneRunnerState, delta: Int) -> LaneRunnerState {
        setLane(state, lane: state.lane + delta)
    }

    /// 화면에 그릴 공룡의 레인 위치(0~2 사이 실수). 레인 전환을 부드럽게 보간한다.
    static func offset(_ state: LaneRunnerState) -> Double {
        let t = state.laneAnimMs / laneAnimMs
        return Double(state.lane) + (state.laneFrom - Double(state.lane)) * t * t
    }

    private static func pick<T>(_ values: [T], random: () -> Double) -> T {
        values[min(Int(random() * Double(values.count)), values.count - 1)]
    }

    /// 한 열의 장애물 배치. 빈 레인은 직전 열의 빈 레인에서 한 칸 이내로만 둔다.
    private static func spawnColumn(_ state: LaneRunnerState, random: () -> Double) -> LaneRunnerState {
        let double = level(score: state.score) >= 2 && random() < 0.25
        let blocked: [Int]
        if double {
            let reachable = lanes.filter { lane in state.lastFree.contains { abs($0 - lane) <= 1 } }
            let free = pick(reachable, random: random)
            blocked = lanes.filter { $0 != free }
        } else {
            // 절반 남짓은 지금 레인을 막아 직접 움직이게 한다
            blocked = [random() < 0.55 ? state.lane : pick(lanes, random: random)]
        }
        let kind: LaneObstacleKind = state.spawned % 2 == 0 ? .rock : .stump
        var next = state
        next.obstacles += blocked.enumerated().map { LaneObstacle(id: state.nextId + $0.offset, lane: $0.element, x: spawnX, kind: kind) }
        next.nextId = state.nextId + blocked.count
        next.spawned = state.spawned + 1
        next.spawnInMs = spawnGapMs(score: state.score)
        next.lastFree = lanes.filter { !blocked.contains($0) }
        return next
    }

    private static func startQuiz(_ state: LaneRunnerState, random: () -> Double) -> LaneRunnerState {
        let question = Runner.question(table: state.table, recentKeys: state.recentKeys, random: random)
        let startX = judgeX + speed(score: state.score) * quizWindowMs(score: state.score)
        var next = state
        next.segment = .quiz
        next.question = question
        next.recentKeys = Array((state.recentKeys + [Problems.key(question.a, question.b)]).suffix(3))
        next.gate = LaneGate(x: startX, startX: startX, values: question.choices)
        next.outcome = nil
        next.given = nil
        return next
    }

    /// 판정이 끝난 게이트는 화면 밖으로 나갈 때까지 보여 주고, 장애물은 바로 이어서 내보낸다.
    private static func startDodge(_ state: LaneRunnerState) -> LaneRunnerState {
        var next = state
        next.segment = .dodge
        next.spawned = 0
        next.spawnInMs = 0
        next.lastFree = lanes
        return next
    }

    static func advance(_ state: LaneRunnerState, dt: Double, random: () -> Double = { Double.random(in: 0..<1) }) -> LaneRunnerState {
        guard state.phase == .running, dt.isFinite, dt > 0 else { return state }
        let animMs = max(0, state.laneAnimMs - dt)

        if state.hitMs > 0 {
            var next = state
            next.laneAnimMs = animMs
            next.hitMs = max(0, state.hitMs - dt)
            if next.hitMs > 0 { return next }
            if state.lives == 0 {
                next.phase = .over
                return next
            }
            if state.outcome == .hit { next.outcome = nil }
            return next
        }

        let movement = speed(score: state.score) * dt
        var next = state
        next.laneAnimMs = animMs
        next.distance = state.distance + movement / 20

        // 장애물: 판정선을 넘는 프레임에 한 번만 판정한다(dt와 무관)
        var hit = false
        var dodged = 0
        var obstacles: [LaneObstacle] = []
        for obstacle in state.obstacles {
            let x = obstacle.x - movement
            if obstacle.x > judgeX && x <= judgeX {
                if obstacle.lane == state.lane && !hit {
                    hit = true
                    continue // 부딪힌 장애물은 치운다
                }
                dodged += 1
            }
            if x > removeX {
                var moved = obstacle
                moved.x = x
                obstacles.append(moved)
            }
        }
        next.obstacles = obstacles
        // 부딪힌 열의 나머지 장애물은 피한 것으로 세지 않는다
        next.dodged = state.dodged + (hit ? 0 : dodged)
        if hit {
            next.lives = next.lives - 1
            next.combo = 0
            next.outcome = .hit
            next.hitMs = hitMs
            return next
        }

        if let gate = next.gate {
            let x = gate.x - movement
            if gate.x > judgeX && x <= judgeX, let question = next.question, next.segment == .quiz {
                let given = gate.values[next.lane]
                next.given = given
                if given == question.answer {
                    next.gate = LaneGate(x: x, startX: gate.startX, values: gate.values)
                    next.outcome = .correct
                    next.score = next.score + 1
                    next.combo = next.combo + 1
                    next.maxCombo = max(next.maxCombo, next.combo)
                    return next
                }
                next.gate = LaneGate(x: judgeX, startX: gate.startX, values: gate.values)
                next.outcome = .wrong
                next.lives = next.lives - 1
                next.combo = 0
                next.hitMs = hitMs
                return next
            }
            if x < removeX {
                next.gate = nil
                next.question = nil
                next.given = nil
                next.outcome = next.outcome == .hit ? .hit : nil
            } else {
                next.gate = LaneGate(x: x, startX: gate.startX, values: gate.values)
            }
            if next.segment == .quiz && x < nextCycleX { return startDodge(next) }
        }

        if next.segment == .dodge {
            let spawnInMs = next.spawnInMs - dt
            if spawnInMs > 0 {
                next.spawnInMs = spawnInMs
                return next
            }
            // 마지막 장애물 뒤 한 간격이 지나면 곧바로 문제를 낸다
            return next.spawned < obstaclesPerCycle(score: next.score)
                ? spawnColumn(next, random: random)
                : startQuiz(next, random: random)
        }
        return next
    }
}
