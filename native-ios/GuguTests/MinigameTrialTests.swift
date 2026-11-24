import XCTest
import SwiftUI
@testable import Gugu

/// 게임(구구 계단·구구 몬스터) 무료 체험 — 맞힌 문제(계단 칸·물리친 몬스터)가 정한 수가 되면 판을 끝낸다.
/// 체험 수(trialLeft)는 맞힐 때만 줄어들고(오답은 그대로), 이용권(trial nil)이면 끝없이 이어진다.
/// 체험 수 전에 잡히거나 시간이 다 되면 체험 끝이 아니라 일반 결과(화면은 trialFinished 일 때만 체험 종료 패널).
final class MinigameTrialTests: XCTestCase {

    private let viewport = GameViewport(size: CGSize(width: 402, height: 874),
                                        safeArea: EdgeInsets(top: 62, leading: 0, bottom: 34, trailing: 0))
    private let frame = 1.0 / 60

    private func makeStore() -> PlayRewardsStore {
        let key = "gugu.test.play.\(UUID().uuidString)"
        let adventureKey = "gugu.test.adventure.\(UUID().uuidString)"
        addTeardownBlock {
            for k in [key, adventureKey] {
                UserDefaults.standard.removeObject(forKey: k)
                Persistence.clearQuarantine(key: k)
            }
        }
        return PlayRewardsStore(key: key, adventureKey: adventureKey)
    }

    private func stairs(trial: Int?) -> StairsEngine {
        let engine = StairsEngine(start: .table(3), order: .sequential, topStep: 9, rewards: makeStore(), commit: LearningSpy().commit,
                                  random: SeededRandom.mulberry32(11), runsLoop: false)
        engine.setViewport(viewport)
        engine.start(trial: trial)
        return engine
    }

    private func monster(trial: Int?) -> MonsterEngine {
        let engine = MonsterEngine(start: .standard, order: .random, rewards: makeStore(), commit: LearningSpy().commit,
                                   random: SeededRandom.mulberry32(11), runsLoop: false)
        engine.setViewport(viewport)
        engine.start(trial: trial)
        return engine
    }

    private func jump(_ engine: StairsEngine, correct: Bool) {
        let lane = correct ? engine.session.current.correctLane
                           : (engine.session.current.correctLane + 1) % StairsSession.lanes
        engine.enqueueTap(CGPoint(x: StairsCamera(size: viewport.size, centerY: 0).laneCenterX(lane), y: 600))
        engine.advance(frame)
        if !correct { for _ in 0..<40 { engine.advance(frame) } }   // 정답 공개 0.6초
    }

    private func pick(_ engine: MonsterEngine, correct: Bool) {
        let q = engine.session.current
        let second = correct ? q.pairB : (0..<MonsterSession.cardCount).first {
            $0 != q.pairA && q.cards[q.pairA] * q.cards[$0] != q.target.a * q.target.b
        }!
        engine.enqueueTap(MonsterEngine.Cards.center(q.pairA, viewport))
        engine.enqueueTap(MonsterEngine.Cards.center(second, viewport))
        engine.advance(frame)
        if !correct { for _ in 0..<40 { engine.advance(frame) } }
    }

    func testTrialLengthIsSingleSourced() {
        XCTAssertEqual(PremiumConfig.minigameTrialQuestions, 10)
        XCTAssertEqual(MinigameTrial.questions, 10)
        XCTAssertEqual(MinigameTrial.limit(isPremium: false), 10)
        XCTAssertNil(MinigameTrial.limit(isPremium: true), "이용권이면 제한 없음")
        XCTAssertEqual(MinigameTrial.badge(isPremium: false), "10문제 체험")
        XCTAssertNil(MinigameTrial.badge(isPremium: true))
        XCTAssertEqual(stairs(trial: MinigameTrial.limit(isPremium: false)).trialLeft, 10)
        XCTAssertNil(stairs(trial: nil).trialLeft, "trial 을 주지 않으면 제한 없음")
    }

    // MARK: - 구구 계단

    /// 오답은 체험 수를 줄이지 않고, 세 번째로 맞힌 칸에서 판이 끝난다
    func testStairsTrialCountsOnlyCorrectClimbs() {
        let engine = stairs(trial: 3)
        jump(engine, correct: true)
        jump(engine, correct: false)
        XCTAssertEqual(engine.trialLeft, 2)
        XCTAssertFalse(engine.roundOver)
        jump(engine, correct: true)
        XCTAssertEqual(engine.trialLeft, 1)
        jump(engine, correct: true)
        XCTAssertEqual(engine.trialLeft, 0)
        XCTAssertTrue(engine.trialFinished)
        XCTAssertTrue(engine.roundOver)
        XCTAssertEqual(engine.session.climbed, 3)
        XCTAssertEqual(engine.session.wrongCount, 1)
    }

    /// 이용권이면 3칸을 넘어 계속 오른다
    func testStairsWithoutTrialKeepsClimbing() {
        let engine = stairs(trial: nil)
        for _ in 0..<5 { jump(engine, correct: true) }
        XCTAssertFalse(engine.roundOver)
        XCTAssertEqual(engine.session.climbed, 5)
        XCTAssertNil(engine.trialLeft)
    }

    /// 체험 수 전에 먹구름에 잡히면 체험 끝이 아님(trialFinished false) → 화면은 일반 결과 창을 띄우고,
    /// [다시 하기]는 같은 체험 수로 새 판을 연다 (2026-11-24 대표님 결정 "잡히면 다시 하기 나오게")
    func testStairsCaughtBeforeTrialLimitIsNormalResult() {
        let limit = MinigameTrial.questions
        let engine = stairs(trial: limit)
        jump(engine, correct: true)
        for _ in 0..<(60 * 60) where !engine.restartArmed { engine.advance(frame) }   // 가만히 있으면 먹구름이 따라잡는다

        XCTAssertTrue(engine.session.caught)
        XCTAssertNotNil(engine.result)
        XCTAssertFalse(engine.trialFinished, "체험 수를 다 쓰지 않았으면 체험 종료 패널이 아니다")
        XCTAssertTrue(engine.restartArmed)
        engine.restart(trial: limit)
        XCTAssertNil(engine.result)
        XCTAssertEqual(engine.trialLeft, limit, "새 판은 체험 수가 처음부터")
        XCTAssertFalse(engine.roundOver)
    }

    // MARK: - 구구 몬스터

    /// 체험 수 전에 60초가 다 되면 체험 끝이 아님 → 일반 결과 창, [다시 하기]는 같은 체험 수로 새 판
    func testMonsterTimeUpBeforeTrialLimitIsNormalResult() {
        let limit = MinigameTrial.questions
        let engine = monster(trial: limit)
        pick(engine, correct: true)
        for _ in 0..<(70 * 60) where !engine.restartArmed { engine.advance(frame) }

        XCTAssertEqual(engine.session.clock.phase, .over)
        XCTAssertNotNil(engine.result)
        XCTAssertFalse(engine.trialFinished, "체험 수를 다 쓰지 않았으면 체험 종료 패널이 아니다")
        XCTAssertTrue(engine.restartArmed)
        engine.restart(trial: limit)
        XCTAssertNil(engine.result)
        XCTAssertEqual(engine.trialLeft, limit, "새 판은 체험 수가 처음부터")
        XCTAssertFalse(engine.roundOver)
    }

    func testMonsterTrialCountsOnlyDefeats() {
        let engine = monster(trial: 3)
        pick(engine, correct: true)
        pick(engine, correct: false)
        XCTAssertEqual(engine.trialLeft, 2)
        pick(engine, correct: true)
        pick(engine, correct: true)
        XCTAssertEqual(engine.trialLeft, 0)
        XCTAssertTrue(engine.trialFinished)
        XCTAssertTrue(engine.roundOver)
        XCTAssertEqual(engine.session.defeated, 3)
        XCTAssertEqual(engine.session.wrongCount, 1)
    }

    func testMonsterWithoutTrialKeepsGoing() {
        let engine = monster(trial: nil)
        for _ in 0..<5 { pick(engine, correct: true) }
        XCTAssertFalse(engine.roundOver)
        XCTAssertEqual(engine.session.defeated, 5)
        XCTAssertNil(engine.trialLeft)
    }
}
