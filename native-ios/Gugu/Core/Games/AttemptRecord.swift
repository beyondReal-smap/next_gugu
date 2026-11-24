import Foundation

// 채점 1회의 기록 (A·B 공통, Unity 시험판 Core/AttemptRecord.cs 이식)
// 학습 기록 연결: answerRecord 로 앱의 AnswerRecord(a, b, correct, ms, given) 로 바꿔 commitSession 에 넘긴다.

struct AttemptRecord: Equatable {
    /// 이 판에서 몇 번째 채점인지 (0부터)
    let id: Int
    /// 채점한 문제(StairsQuestion·MonsterQuestion)의 id
    let questionId: Int
    /// A: 단 × 칸, B: 몬스터의 목표 식
    let problem: Problem
    /// A: 고른 발판 숫자, B: 고른 두 카드의 곱
    let given: Int
    /// given 이 problem 의 곱과 같은지 — B 는 다른 카드 조합이라도 곱이 같으면 정답
    let correct: Bool
    /// 채점 때 판 시계(RoundClock.elapsed, 초)
    let elapsed: Double
    /// 문제가 나온 뒤 채점까지 걸린 시간(초) — 첫 동작 전 대기 포함, 쉬는 동안(tick 없음) 제외
    let responseSec: Double
    /// B: 고른 카드 값(고른 순서대로). A 는 nil
    let pickA: Int?
    let pickB: Int?

    init(id: Int, questionId: Int, problem: Problem, given: Int, elapsed: Double, responseSec: Double,
         pickA: Int? = nil, pickB: Int? = nil) {
        precondition(responseSec >= 0, "응답 시간은 0 이상이어야 함: \(responseSec)")
        precondition((pickA == nil) == (pickB == nil), "카드는 두 장을 함께 기록해야 함")
        self.id = id
        self.questionId = questionId
        self.problem = problem
        self.given = given
        self.correct = given == problem.a * problem.b
        self.elapsed = elapsed
        self.responseSec = responseSec
        self.pickA = pickA
        self.pickB = pickB
    }

    /// 응답 시간(ms) — 학습 기록용, 반올림
    var ms: Int { Int((responseSec * 1000).rounded()) }

    /// 앱 학습 기록 형식. given 은 아이가 낸 수(A: 고른 발판 숫자, B: 고른 두 카드의 곱)
    var answerRecord: AnswerRecord {
        AnswerRecord(a: problem.a, b: problem.b, correct: correct, ms: ms, given: .number(given))
    }

    /// 종료 화면에 보여 줄 틀린 식 하나: 가장 많이 틀린 식, 같으면 가장 최근. 틀린 것이 없으면 nil
    static func mostMissed(_ attempts: [AttemptRecord]) -> AttemptRecord? {
        var counts: [String: Int] = [:]
        var best: AttemptRecord?
        var bestCount = 0
        for attempt in attempts where !attempt.correct {
            let key = Problems.key(attempt.problem.a, attempt.problem.b)
            let count = (counts[key] ?? 0) + 1
            counts[key] = count
            if count >= bestCount {
                best = attempt
                bestCount = count
            }
        }
        return best
    }
}
