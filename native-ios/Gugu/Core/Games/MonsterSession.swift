import Foundation

// B 구구 몬스터 — 곱셈 몬스터 규칙 (Unity 시험판 Core/MonsterSession.cs·ProblemRules.Pick 이식)
// 몬스터 체력(곱)과 같아지는 카드 2장을 고르면 처치. 두 번째 카드를 고르는 순간 한 번만 채점한다.
// 같은 곱의 다른 카드 조합도 정답(12 = 3×4 = 2×6). 기록은 몬스터의 목표 식으로 남긴다.
// 출제는 2~9단 × 1~9 고정(설정 「곱하는 수」와 무관). 카드는 1~9.
// 틀리면 정답을 0.6초 보여 준 뒤 같은 몬스터(같은 목표)를 카드만 섞어 다시 낸다. 60초 제한.
//
// 문제 순서(order, 2.9.0 시작 창에서 고름):
//  - 무작위(random): 72문항에서 가중 무작위, 직전 3문제 가중치 ×0.15 (원본 규칙). 단 지정을 받지 않는다(table 은 nil 이어야 함)
//  - 순서대로(sequential): 목표 = 단 × 1, × 2 … × 9 차례로.
//    단 지정(table: 2~9 — 단 시트에서 고른 단)이면 × 9 다음은 그 단 × 1 로 다시,
//    nil 이면 시작 단을 2~9 에서 무작위로 고르고 × 9 다음은 직전과 다른 2~9단 × 1
//
// 난수 소비 순서 — 골든 벡터가 기대는 계약(Android 도 같아야 함):
//  무작위
//   새 몬스터: 목표 고르기(pickTarget, 난수 1개) → 카드
//  순서대로
//   생성: [table 이 nil 이면 시작 단 index(8)] → 카드
//   처치: [× 9 였고 table 이 nil 이면 다음 단 index(8)(직전 단이 나오면 다시 뽑음)] → 카드 — 그 밖에는 카드만
//  공통
//   카드(makeCards): 근처 수 섞기 → 나머지 수 섞기 → 자리 순서 섞기
//   오답 공개가 끝난 tick: 같은 목표로 카드만

struct MonsterQuestion: Equatable {
    let id: Int
    /// 몬스터 체력 = target 의 곱
    let target: Problem
    let cards: [Int]
    /// 정답으로 보여 줄 카드 자리 (다른 정답 조합도 인정)
    let pairA: Int
    let pairB: Int
    /// 문제가 나온 시각(세션 playTime, 초) — 응답 시간 계산용
    let issuedAt: Double

    init(id: Int, target: Problem, cards: [Int], pairA: Int, pairB: Int, issuedAt: Double) {
        precondition(cards.count == MonsterSession.cardCount, "카드 수가 다름: \(cards)")
        precondition(pairA != pairB && cards[pairA] * cards[pairB] == target.a * target.b,
                     "정답 자리는 곱이 목표인 서로 다른 두 장이어야 함")  // i18n:skip — 개발용 단언 메시지
        self.id = id
        self.target = target
        self.cards = cards
        self.pairA = pairA
        self.pairB = pairB
        self.issuedAt = issuedAt
    }
}

enum CardTapResult: Equatable {
    case rejected(ActionReject)
    case selected
    case deselected
    case judged(AttemptRecord)

    var attempt: AttemptRecord? {
        if case let .judged(attempt) = self { return attempt }
        return nil
    }

    var reject: ActionReject? {
        if case let .rejected(reason) = self { return reason }
        return nil
    }
}

final class MonsterSession {
    static let cardCount = 6
    static let cardValues = 1...9
    static let tables = 2...9
    static let multipliers = 1...9
    static let recentWindow = 3
    static let recentWeight = 0.15
    /// 무작위 출제 후보 72개 — 단 순서 → 곱하는 수 순서. 누적 분포의 순서이므로 바꾸지 말 것
    static let targets: [Problem] = tables.flatMap { a in multipliers.map { Problem(a: a, b: $0) } }

    private(set) var clock = RoundClock()
    private(set) var current: MonsterQuestion
    private(set) var currentJudged = false
    /// 먼저 고른 카드 자리 (없으면 nil)
    private(set) var selected: Int?
    private(set) var issuedCount = 0
    /// 지금까지 나온 몬스터 수(오답 뒤 같은 몬스터 재출제는 세지 않음) — 화면이 몬스터 종을 바꿀 때 쓴다
    private(set) var monstersIssued = 0
    private(set) var defeated = 0
    private(set) var wrongCount = 0
    /// 지금 이어지는 연속 처치(오답이면 0)
    private(set) var streak = 0
    private(set) var bestStreak = 0
    /// 이번 판에 받은 별 코인(StarRules)
    private(set) var stars = 0
    private(set) var attempts: [AttemptRecord] = []
    /// 응답 시간 재기용 시계(초): 판을 연 뒤 tick 으로 흘린 시간. 첫 동작 전 대기 포함, 쉬는 동안(tick 없음)·판 끝 뒤 제외
    private(set) var playTime: Double = 0

    /// 문제 순서 — 무작위(72문항 가중 무작위) / 순서대로(단 × 1 → × 9)
    let order: ProblemOrder
    /// 단 지정(2~9, 순서대로만). nil = 순서대로면 단을 바꿔 가며, 무작위면 2~9단 섞어서
    let table: Int?

    /// 무작위 — 최근 목표(가중치 낮춤)
    private var recent: [Problem] = []
    private let random: () -> Double

    /// table: 순서대로의 단 지정(2~9 — 10단 이상은 카드 1~9 로 만들 수 없어 진입 화면이 막는다).
    /// nil = 순서대로면 시작 단 2~9 무작위, 무작위면 2~9단 섞어서. 무작위 순서는 단 지정을 받지 않는다
    init(order: ProblemOrder, table: Int? = nil, random: @escaping () -> Double = { Double.random(in: 0..<1) }) {
        if let table {
            precondition(order == .sequential, "무작위 순서는 단 지정을 받지 않음: \(table)")
            precondition(Self.tables.contains(table), "단 지정은 \(Self.tables) 만: \(table)")
        }
        self.order = order
        self.table = table
        self.random = random
        let target: Problem
        switch order {
        case .random:
            target = Self.pickTarget(recent: [], random: random)
            recent = [target]
        case .sequential:
            target = Problem(a: table ?? GameRandom.pick(Self.tables, excluding: nil, random: random), b: 1)
        }
        monstersIssued = 1
        current = Self.makeQuestion(id: 0, target: target, issuedAt: 0, random: random)
        issuedCount = 1
    }

    /// index 자리 카드를 누름. 첫 장은 고르기, 같은 장은 취소, 다른 장은 채점(정확히 한 번)
    func tap(card index: Int) -> CardTapResult {
        if let reject = clock.check(valid: (0..<Self.cardCount).contains(index)) { return .rejected(reject) }
        precondition(!currentJudged, "문제 \(current.id) 는 이미 채점됨")

        clock.markAction()
        guard let first = selected else {
            selected = index
            return .selected
        }
        if first == index {
            selected = nil
            return .deselected
        }

        let a = current.cards[first]
        let b = current.cards[index]
        let attempt = AttemptRecord(
            id: attempts.count, questionId: current.id, problem: current.target, given: a * b,
            elapsed: clock.elapsed, responseSec: playTime - current.issuedAt, pickA: a, pickB: b
        )
        attempts.append(attempt)
        currentJudged = true
        selected = nil

        if attempt.correct {
            defeated += 1
            streak += 1
            bestStreak = max(bestStreak, streak)
            stars += StarRules.forCorrect(streak: streak)
            nextMonster()
        } else {
            wrongCount += 1
            streak = 0
            clock.beginReveal()
        }
        return .judged(attempt)
    }

    /// dt(초) 만큼 진행. 오답 공개가 끝나면 같은 몬스터를 카드만 섞어 다시 낸다
    func tick(_ dt: Double) {
        precondition(dt >= 0, "dt 는 0 이상이어야 함: \(dt)")
        if clock.phase != .over { playTime += dt }
        if clock.tick(dt) { issue(current.target) }
    }

    /// 결과 화면에 보여 줄 틀린 식 하나
    var missToShow: AttemptRecord? { AttemptRecord.mostMissed(attempts) }

    /// 학습 기록(commitSession)으로 넘길 채점 기록 — 문제 = 목표 식, given = 고른 두 카드의 곱
    var answerRecords: [AnswerRecord] { attempts.map(\.answerRecord) }

    private func nextMonster() {
        let target: Problem
        switch order {
        case .random:
            target = Self.pickTarget(recent: recent, random: random)
            recent.append(target)
            if recent.count > Self.recentWindow { recent.removeFirst() }
        case .sequential:
            target = Self.nextInOrder(after: current.target, table: table, random: random)
        }
        monstersIssued += 1
        issue(target)
    }

    private func issue(_ target: Problem) {
        current = Self.makeQuestion(id: issuedCount, target: target, issuedAt: playTime, random: random)
        issuedCount += 1
        currentJudged = false
        selected = nil
    }

    private static func makeQuestion(id: Int, target: Problem, issuedAt: Double, random: () -> Double) -> MonsterQuestion {
        let made = makeCards(target, random: random)
        return MonsterQuestion(id: id, target: target, cards: made.cards, pairA: made.pairA, pairB: made.pairB,
                               issuedAt: issuedAt)
    }

    /// 순서대로의 다음 목표 — × 9 가 아니면 같은 단 다음 곱하는 수(난수 없음),
    /// × 9 다음은 단 지정이면 그 단 × 1, 없으면 직전과 다른 2~9단 × 1(index(8), 직전 단이면 다시 뽑음)
    static func nextInOrder(after target: Problem, table: Int?, random: () -> Double) -> Problem {
        precondition(tables.contains(target.a) && multipliers.contains(target.b), "순서대로 목표 범위 밖: \(target)")
        if let table { precondition(target.a == table, "단 지정 \(table) 인데 목표가 \(target)") }
        guard target.b == multipliers.upperBound else { return Problem(a: target.a, b: target.b + 1) }
        let next = table ?? GameRandom.pick(tables, excluding: target.a, random: random)
        return Problem(a: next, b: multipliers.lowerBound)
    }

    /// 가중 무작위 출제 (기존 pickProblem 규칙) — targets 72개 중, recent 의 마지막 3개는 가중치 ×0.15.
    /// 누적 분포 — 난수 × 합계에서 가중치를 빼다가 0 이하가 되는 첫 항목(난수 1개)
    static func pickTarget(recent: [Problem], random: () -> Double) -> Problem {
        let avoid = recent.suffix(recentWindow)
        let weights = targets.map { avoid.contains($0) ? recentWeight : 1.0 }
        var total = 0.0
        for w in weights { total += w }  // 앞에서부터 더한다(플랫폼 간 같은 합계)
        var r = random() * total
        for (i, w) in weights.enumerated() {
            r -= w
            if r <= 0 { return targets[i] }
        }
        return targets[targets.count - 1]
    }

    /// 정답 두 장(a, b — 같으면 같은 수 두 장) + 근처 숫자(±1) 위주의 오답, 나머지 무작위로 6장. 1~9 카드만 쓴다.
    /// pairA·pairB = 원래 0번(a)·1번(b) 카드가 간 자리
    static func makeCards(_ p: Problem, random: () -> Double) -> (cards: [Int], pairA: Int, pairB: Int) {
        precondition(cardValues.contains(p.a) && cardValues.contains(p.b), "카드로 만들 수 없는 식: \(p)")
        var near: [Int] = []
        for v in [p.a - 1, p.a + 1, p.b - 1, p.b + 1]
        where cardValues.contains(v) && v != p.a && v != p.b && !near.contains(v) {
            near.append(v)
        }
        var rest: [Int] = []
        for v in cardValues where v != p.a && v != p.b && !near.contains(v) {
            rest.append(v)
        }
        GameRandom.shuffle(&near, random: random)
        GameRandom.shuffle(&rest, random: random)

        var cards = [p.a, p.b]
        for v in near where cards.count < cardCount { cards.append(v) }
        for v in rest where cards.count < cardCount { cards.append(v) }

        var order = Array(0..<cardCount)
        GameRandom.shuffle(&order, random: random)
        var shuffled = [Int](repeating: 0, count: cardCount)
        for i in 0..<cardCount { shuffled[order[i]] = cards[i] }
        return (shuffled, order[0], order[1])
    }
}
