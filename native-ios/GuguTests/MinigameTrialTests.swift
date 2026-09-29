import XCTest
@testable import Gugu

/// 움직이며 놀기 무료 체험 — 정한 문제 수를 다 풀면 다음 문제를 내지 않고 판을 끝낸다.
/// 체험 수(trialLeft)는 "다음 문제로 넘어가는 순간" 줄어들고, 하트를 다 쓰면 그 자리에서 끝난다.
final class MinigameTrialTests: XCTestCase {

    func testTrialLengthIsSingleSourced() {
        XCTAssertEqual(PremiumConfig.minigameTrialQuestions, 3)
        XCTAssertEqual(Runner.create(table: 2, trial: PremiumConfig.minigameTrialQuestions).trialLeft, 3)
        XCTAssertNil(Runner.create(table: 2).trialLeft, "trial 을 주지 않으면 제한 없음")
    }

    // MARK: - 구구 점프

    /// 정답을 골라 장애물을 넘긴다 (정답 뒤 속도로 한 번에 지나가도록 큰 dt)
    private func clear(_ s: RunnerState) -> RunnerState {
        Runner.advance(Runner.answer(s, choice: s.question.answer), dt: 10_000)
    }

    /// 오답을 골라 부딪히고 피격 연출까지 흘린다
    private func miss(_ s: RunnerState) -> RunnerState {
        let wrong = s.question.choices.first { $0 != s.question.answer }!
        let hit = Runner.advance(Runner.answer(s, choice: wrong), dt: 10_000)
        return Runner.advance(hit, dt: Runner.hitDurationMs)
    }

    func testRunnerTrialEndsAfterLastQuestion() {
        var s = Runner.create(table: 2, trial: 3)
        s = clear(s)
        XCTAssertEqual(s.phase, .running)
        XCTAssertEqual(s.trialLeft, 2)
        s = miss(s)
        XCTAssertEqual(s.phase, .running)
        XCTAssertEqual(s.trialLeft, 1)
        s = clear(s)
        XCTAssertEqual(s.phase, .over)
        XCTAssertEqual(s.trialLeft, 0)
        XCTAssertEqual(s.score, 2)
        XCTAssertEqual(s.lives, Runner.maxLives - 1)
        XCTAssertEqual(s.round, 2, "마지막 문제에서 멈춘다 — 새 문제를 뽑지 않는다")
        XCTAssertEqual(Runner.advance(s, dt: 16).phase, .over)
    }

    func testRunnerHeartsOutEndsBeforeTrialCount() {
        var s = Runner.create(table: 2, trial: 3)
        s = miss(miss(miss(s)))
        XCTAssertEqual(s.phase, .over)
        XCTAssertEqual(s.lives, 0)
        XCTAssertEqual(s.trialLeft, 1, "하트를 다 쓴 판은 체험 수를 줄이지 않고 그 자리에서 끝난다")
    }

    func testRunnerWithoutTrialKeepsRunning() {
        var s = Runner.create(table: 2)
        for _ in 0..<4 { s = clear(s) }
        XCTAssertEqual(s.phase, .running)
        XCTAssertEqual(s.score, 4)
        XCTAssertNil(s.trialLeft)
    }

    // MARK: - 구구 바구니

    /// 판정이 난 상태를 만들고 피드백 시간을 흘린다
    private func settle(_ s: BasketState, _ outcome: BasketOutcome) -> BasketState {
        var judged = s
        judged.outcome = outcome
        judged.feedbackMs = 10
        if outcome != .correct { judged.lives -= 1 }
        return Basket.advance(judged, dt: 20)
    }

    func testBasketTrialEndsAfterLastQuestion() {
        var s = Basket.create(table: 2, trial: 2)
        s = settle(s, .correct)
        XCTAssertEqual(s.phase, .running)
        XCTAssertEqual(s.trialLeft, 1)
        XCTAssertEqual(s.round, 1)
        s = settle(s, .missed)
        XCTAssertEqual(s.phase, .over)
        XCTAssertEqual(s.trialLeft, 0)
        XCTAssertEqual(s.round, 1, "마지막 문제에서 멈춘다 — 새 열매를 내지 않는다")
    }

    func testBasketHeartsOutEndsBeforeTrialCount() {
        var s = Basket.create(table: 2, trial: 3)
        s.lives = 1
        s = settle(s, .wrong)
        XCTAssertEqual(s.phase, .over)
        XCTAssertEqual(s.trialLeft, 3)
    }

    func testBasketWithoutTrialKeepsRunning() {
        var s = Basket.create(table: 2)
        for _ in 0..<4 { s = settle(s, .correct) }
        XCTAssertEqual(s.phase, .running)
        XCTAssertNil(s.trialLeft)
    }

    // MARK: - 구구 레인

    /// 판정을 마친 게이트가 다음 장애물 구간 경계(nextCycleX)를 넘게 한다
    private func passGate(_ s: LaneRunnerState) -> LaneRunnerState {
        let question = RunnerQuestion(a: 3, b: 4, choices: [12, 10, 14])
        var s = s
        s.segment = .quiz
        s.question = question
        s.outcome = .correct
        s.gate = LaneGate(x: LaneRunner.nextCycleX + 1, startX: 600, values: question.choices)
        return LaneRunner.advance(s, dt: 16)
    }

    func testLaneTrialCountsGatesOnly() {
        var s = LaneRunner.create(table: 3, trial: 2)
        // 장애물 피하기는 문제가 아니다 — 체험 수가 줄지 않는다
        s.obstacles = [LaneObstacle(id: 0, lane: 0, x: LaneRunner.judgeX + 1, kind: .rock)]
        s = LaneRunner.advance(s, dt: 16)
        XCTAssertEqual(s.dodged, 1)
        XCTAssertEqual(s.trialLeft, 2)

        s = passGate(s)
        XCTAssertEqual(s.phase, .running)
        XCTAssertEqual(s.segment, .dodge)
        XCTAssertEqual(s.trialLeft, 1)
        s = passGate(s)
        XCTAssertEqual(s.phase, .over)
        XCTAssertEqual(s.trialLeft, 0)
    }

    func testLaneWithoutTrialKeepsRunning() {
        var s = LaneRunner.create(table: 3)
        for _ in 0..<4 { s = passGate(s) }
        XCTAssertEqual(s.phase, .running)
        XCTAssertNil(s.trialLeft)
    }
}
