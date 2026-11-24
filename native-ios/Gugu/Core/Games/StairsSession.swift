import Foundation

// A 구구 계단 — 계단 점프 규칙 (Unity 시험판 Core/StairsSession.cs 이식)
// 한 단을 ×1 → ×topStep 순서로 오른다. 맞히면 한 칸 위·바로 다음 문제, 틀리면 정답을 0.6초 보여 준 뒤
// 같은 칸을 보기만 섞어 다시 낸다. 꼭대기(×topStep)에 닿으면 보물 +1, 2~9단 중 다른 단의 ×1 부터 이어서 오른다.
// 시간 제한은 없고, 아래에서 차오르는 먹구름(RisingCloud)에 잡히면 판이 끝난다.
//
// 원본과 달라진 점(2.9.0 확정):
//  - 시작 단 firstTable: nil = 2~9 무작위, 값이 있으면 2~19 (단 시트에서 고른 단)
//  - 꼭대기 칸 topStep = 설정 「곱하는 수」(9·12·15·19), 생성 때 주입 (원본 9 고정)
//  - 보기 최대값 = 단 × topStep + 단 (원본 99 고정)
//
// 문제 순서(order, 2.9.0 시작 창에서 고름):
//  - 순서대로(sequential): 위 설명 그대로(원본 규칙)
//  - 무작위(random): 문제마다 2~9단 × 1~topStep 에서 하나(직전 문제와 같은 식이면 다시 뽑음).
//    서 있는 발판 = 직전 정답(첫 칸은 0 = "출발"). topStep 칸을 오를 때마다 보물 +1(단은 바꾸지 않음 — 문제마다 바뀜).
//    시작 단은 받지 않는다(firstTable 은 nil 이어야 함 — 단 시트에서 고른 단도 쓰지 않음)
//
// 난수 소비 순서 — 골든 벡터가 기대는 계약(Android 도 같아야 함):
//  순서대로
//   생성: [firstTable 이 nil 이면 단 고르기] → 첫 문제 보기
//   정답: [꼭대기였으면 다음 단 고르기(현재 단이 나오면 다시 뽑음)] → 다음 문제 보기
//  무작위
//   생성: 식 고르기 → 첫 문제 보기
//   정답: 식 고르기(직전 식이 나오면 다시 뽑음) → 다음 문제 보기 — 보물을 얻어도 난수를 더 쓰지 않는다
//   식 고르기(randomProblem): 단 index(8) → 곱하는 수 index(topStep), 직전 식과 같으면 둘 다 다시
//  공통
//   보기(makeChoices): 오답 후보 섞기 → 정답 + 후보 앞 2개 섞기
//   오답 공개가 끝난 tick: 같은 칸(같은 식·같은 발판) 보기

struct StairsQuestion: Equatable {
    let id: Int
    /// 단 × 칸
    let problem: Problem
    /// 지금 서 있는 발판 숫자 — 순서대로: 단 × (칸 - 1), 무작위: 직전 정답(첫 칸 0)
    let standing: Int
    /// 칸(0 왼쪽·1 가운데·2 오른쪽)마다 발판 숫자 하나
    let choices: [Int]
    let correctLane: Int
    /// 문제가 나온 시각(세션 playTime, 초) — 응답 시간 계산용
    let issuedAt: Double

    init(id: Int, problem: Problem, standing: Int, choices: [Int], issuedAt: Double) {
        precondition(choices.count == StairsSession.lanes, "칸마다 숫자 하나씩이어야 함: \(choices)")
        guard let lane = choices.firstIndex(of: problem.a * problem.b) else {
            preconditionFailure("보기에 정답이 없음: \(problem) \(choices)")
        }
        self.id = id
        self.problem = problem
        self.standing = standing
        self.choices = choices
        self.correctLane = lane
        self.issuedAt = issuedAt
    }
}

enum StairsJumpResult: Equatable {
    case rejected(ActionReject)
    /// clearedTable: 이 점프로 꼭대기(순서대로 ×topStep, 무작위 topStep 칸째)에 닿아 보물을 얻었는지
    case judged(AttemptRecord, clearedTable: Bool)

    var attempt: AttemptRecord? {
        if case let .judged(attempt, _) = self { return attempt }
        return nil
    }

    var reject: ActionReject? {
        if case let .rejected(reason) = self { return reason }
        return nil
    }

    var accepted: Bool { attempt != nil }

    var clearedTable: Bool {
        if case let .judged(_, cleared) = self { return cleared }
        return false
    }
}

final class StairsSession {
    static let lanes = 3
    /// 무작위로 고르는 단(시작 단이 nil 일 때·보물 뒤) — 2~9단
    static let randomTables = 2...9
    /// 시작 단으로 고를 수 있는 범위 — 2~19단 (단 시트)
    static let firstTables = Problems.minTable...Problems.maxTable

    /// 꼭대기 칸 = 설정 「곱하는 수」 (9·12·15·19)
    let topStep: Int
    /// 문제 순서 — 순서대로(한 단씩 ×1 → ×topStep) / 무작위(문제마다 2~9단 × 1~topStep)
    let order: ProblemOrder
    private(set) var clock = RoundClock(limitSec: RoundClock.unlimited)
    private(set) var cloud = RisingCloud()
    /// 먹구름에 잡혀 끝났는지
    private(set) var caught = false
    /// 지금 문제의 단 — 순서대로면 오르는 단, 무작위면 문제마다 바뀐다
    private(set) var table: Int
    /// 지금 풀어야 하는 칸 (1~topStep) — 무작위면 보물까지 몇 번째 칸인지
    private(set) var step = 1
    private(set) var current: StairsQuestion
    private(set) var currentJudged = false
    private(set) var issuedCount = 0
    /// 오른 계단 수 = 맞힌 문제 수 (구름 높이와 같은 단위)
    private(set) var climbed = 0
    private(set) var treasures = 0
    private(set) var wrongCount = 0
    /// 지금 이어지는 연속 정답(오답이면 0)
    private(set) var streak = 0
    private(set) var bestStreak = 0
    /// 이번 판에 받은 별 코인(StarRules)
    private(set) var stars = 0
    private(set) var attempts: [AttemptRecord] = []
    /// 응답 시간 재기용 시계(초): 판을 연 뒤 tick 으로 흘린 시간. 첫 동작 전 대기 포함, 쉬는 동안(tick 없음)·판 끝 뒤 제외
    private(set) var playTime: Double = 0

    private let random: () -> Double

    /// firstTable: 순서대로의 시작 단(nil = 2~9 무작위). 무작위 순서는 시작 단을 받지 않는다(nil 이어야 함)
    init(topStep: Int, order: ProblemOrder, firstTable: Int? = nil,
         random: @escaping () -> Double = { Double.random(in: 0..<1) }) {
        precondition(Problems.maxBOptions.contains(topStep), "꼭대기 칸은 \(Problems.maxBOptions) 중 하나여야 함: \(topStep)")
        if let firstTable {
            precondition(order == .sequential, "무작위 순서는 시작 단을 받지 않음: \(firstTable)")
            precondition(Self.firstTables.contains(firstTable), "시작 단 범위 밖: \(firstTable)")
        }
        self.topStep = topStep
        self.order = order
        self.random = random
        let first: Problem
        switch order {
        case .sequential:
            first = Problem(a: firstTable ?? Self.pickTable(excluding: nil, random: random), b: 1)
        case .random:
            first = Self.randomProblem(topStep: topStep, previous: nil, random: random)
        }
        table = first.a
        current = Self.makeQuestion(id: 0, problem: first, standing: 0, topStep: topStep, issuedAt: 0, random: random)
        issuedCount = 1
    }

    /// 보기 최댓값 = 단 × topStep + 단 (꼭대기 다음 배수까지 자연스러움)
    static func maxChoice(table: Int, topStep: Int) -> Int {
        table * topStep + table
    }

    /// lane 칸 발판으로 점프. 받아들이면 정확히 한 번 채점한다
    func jump(lane: Int) -> StairsJumpResult {
        if let reject = clock.check(valid: (0..<Self.lanes).contains(lane)) { return .rejected(reject) }
        precondition(!currentJudged, "문제 \(current.id) 는 이미 채점됨")

        clock.markAction()
        let attempt = AttemptRecord(
            id: attempts.count, questionId: current.id, problem: current.problem,
            given: current.choices[lane], elapsed: clock.elapsed, responseSec: playTime - current.issuedAt
        )
        attempts.append(attempt)
        currentJudged = true

        guard attempt.correct else {
            wrongCount += 1
            streak = 0
            clock.beginReveal()  // tick 에서 공개가 끝나면 같은 칸을 섞어서 다시 낸다
            return .judged(attempt, clearedTable: false)
        }

        climbed += 1
        streak += 1
        bestStreak = max(bestStreak, streak)
        stars += StarRules.forCorrect(streak: streak)
        let cleared = step == topStep
        if cleared {
            treasures += 1
            stars += StarRules.treasureBonus
        }
        step = cleared ? 1 : step + 1
        switch order {
        case .sequential:
            if cleared { table = Self.pickTable(excluding: table, random: random) }
            issue(Problem(a: table, b: step), standing: table * (step - 1))
        case .random:
            let answered = current.problem
            let next = Self.randomProblem(topStep: topStep, previous: answered, random: random)
            table = next.a
            issue(next, standing: answered.a * answered.b)   // 방금 밟은 발판 = 직전 정답
        }
        return .judged(attempt, clearedTable: cleared)
    }

    /// dt(초) 만큼 진행. 순서: 시계 → 구름. 첫 동작 전(waiting)·종료 뒤에는 구름도 멈춰 있다.
    /// 같은 프레임의 입력은 tick 보다 먼저 처리하므로, 잡히기 직전에 누른 정답은 한 칸을 벌어 살아남는다
    func tick(_ dt: Double) {
        precondition(dt >= 0, "dt 는 0 이상이어야 함: \(dt)")
        if clock.phase != .over { playTime += dt }
        let revealEnded = clock.tick(dt)
        if clock.phase == .running && cloud.tick(dt, standing: climbed) {
            caught = true
            clock.end()
            return  // 끝났으니 공개가 끝났어도 새 문제를 내지 않음
        }
        if revealEnded { issue(current.problem, standing: current.standing) }   // 같은 칸, 보기만 다시
    }

    /// 결과 화면에 보여 줄 틀린 식 하나
    var missToShow: AttemptRecord? { AttemptRecord.mostMissed(attempts) }

    /// 학습 기록(commitSession)으로 넘길 채점 기록 — 문제 = 단 × 칸, given = 고른 발판 숫자
    var answerRecords: [AnswerRecord] { attempts.map(\.answerRecord) }

    private func issue(_ problem: Problem, standing: Int) {
        current = Self.makeQuestion(id: issuedCount, problem: problem, standing: standing, topStep: topStep,
                                    issuedAt: playTime, random: random)
        issuedCount += 1
        currentJudged = false
    }

    private static func makeQuestion(id: Int, problem: Problem, standing: Int, topStep: Int, issuedAt: Double,
                                     random: () -> Double) -> StairsQuestion {
        let choices = makeChoices(problem, standing: standing, topStep: topStep, random: random)
        return StairsQuestion(id: id, problem: problem, standing: standing, choices: choices, issuedAt: issuedAt)
    }

    /// 무작위 순서의 식 하나 — 단(2~9) → 곱하는 수(1~topStep) 순서로 난수 2개. 직전 식과 같으면 둘 다 다시 뽑는다
    static func randomProblem(topStep: Int, previous: Problem?, random: () -> Double) -> Problem {
        precondition(Problems.maxBOptions.contains(topStep), "꼭대기 칸은 \(Problems.maxBOptions) 중 하나여야 함: \(topStep)")
        while true {
            let a = randomTables.lowerBound + GameRandom.index(randomTables.count, random: random)
            let b = 1 + GameRandom.index(topStep, random: random)
            let p = Problem(a: a, b: b)
            if p != previous { return p }
        }
    }

    /// 정답 1개 + 오답 2개를 섞은 3칸. 오답 후보: 한 칸 건너뛴 배수(+단), ±1, ±곱하는 수.
    /// 지금 서 있는 발판 숫자는 빼서 헷갈림을 줄이고, 1 ~ maxChoice 밖의 수는 뺀다
    static func makeChoices(_ p: Problem, standing: Int, topStep: Int, random: () -> Double) -> [Int] {
        precondition((1...topStep).contains(p.b), "칸은 1~\(topStep) 이어야 함: \(p.b)")
        let answer = p.a * p.b
        let maxValue = maxChoice(table: p.a, topStep: topStep)
        let raw = [answer + p.a, answer - 1, answer + 1, answer - p.b, answer + p.b]
        var alternatives: [Int] = []
        for v in raw where v > 0 && v <= maxValue && v != answer && v != standing && !alternatives.contains(v) {
            alternatives.append(v)
        }
        precondition(alternatives.count >= lanes - 1, "\(p) 의 오답 후보가 부족함 (\(alternatives.count)개)")
        GameRandom.shuffle(&alternatives, random: random)
        var choices = [answer, alternatives[0], alternatives[1]]
        GameRandom.shuffle(&choices, random: random)
        return choices
    }

    /// 2~9단에서 하나. excluding(보물 직전 단)이 나오면 다시 뽑는다 — 10단 이상에서 시작했으면 첫 뽑기가 곧 결과
    private static func pickTable(excluding: Int?, random: () -> Double) -> Int {
        GameRandom.pick(randomTables, excluding: excluding, random: random)
    }
}
