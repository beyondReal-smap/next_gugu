import XCTest
@testable import Gugu

/// A·B 게임 공통 규칙 — 시계(RoundClock)·채점 기록(AttemptRecord)·시드 난수.
/// Unity 시험판 PrototypeSessionTests.RoundClockTests 이식 + 학습 기록 변환 + 난수 골든 벡터(Android 와 같은 값).
final class GameClockTests: XCTestCase {

    private func rng(_ seed: UInt32) -> () -> Double { SeededRandom.mulberry32(seed) }

    // MARK: - RoundClock (원본 RoundClockTests)

    func testWaitsForFirstActionThenEndsAtSixtySeconds() {
        var c = RoundClock()
        c.tick(30)
        XCTAssertEqual(c.phase, .waiting)
        c.markAction()
        c.tick(59.5)
        XCTAssertEqual(c.phase, .running)
        c.tick(0.5)
        XCTAssertEqual(c.phase, .over)
        XCTAssertEqual(c.remaining, 0)
        XCTAssertEqual(c.check(valid: true), .over)
    }

    func testRevealLocksForRevealSecAndReportsEndOnce() {
        var c = RoundClock()
        c.markAction()
        c.beginReveal()
        XCTAssertEqual(c.check(valid: true), .revealing)
        XCTAssertEqual(c.check(valid: false), .invalid, "없는 입력은 공개 중이어도 invalid")
        XCTAssertFalse(c.tick(RoundClock.revealSec - 0.01))
        XCTAssertTrue(c.tick(0.02))
        XCTAssertFalse(c.tick(0.02))
        XCTAssertNil(c.check(valid: true))
    }

    func testTimeUpDuringRevealClearsRevealWithoutReissueSignal() {
        var c = RoundClock()
        c.markAction()
        c.tick(59.8)
        c.beginReveal()
        XCTAssertFalse(c.tick(0.5))
        XCTAssertEqual(c.phase, .over)
        XCTAssertFalse(c.isRevealing)
    }

    /// 원본은 시작 전·두 번 end() 가 예외였다 — Swift 에서는 precondition(규칙 쪽 버그)이라 정상 경로만 검사한다
    func testUnlimitedNeverTimesOutUntilEnd() {
        var c = RoundClock(limitSec: RoundClock.unlimited)
        c.markAction()
        c.tick(3600)
        XCTAssertEqual(c.phase, .running)
        XCTAssertEqual(c.remaining, .infinity)
        c.beginReveal()
        c.end()
        XCTAssertEqual(c.phase, .over)
        XCTAssertFalse(c.isRevealing)
        XCTAssertEqual(c.check(valid: true), .over)
    }

    func testZeroTickChangesNothing() {
        var c = RoundClock()
        c.markAction()
        c.beginReveal()
        XCTAssertFalse(c.tick(0))
        XCTAssertEqual(c.elapsed, 0)
        XCTAssertEqual(c.revealRemaining, RoundClock.revealSec)
    }

    // MARK: - AttemptRecord

    func testMostMissedPicksMostFrequentTiesGoToLatest() {
        let p1 = Problem(a: 7, b: 8)
        let p2 = Problem(a: 6, b: 9)
        func rec(_ id: Int, _ p: Problem, _ given: Int) -> AttemptRecord {
            AttemptRecord(id: id, questionId: id, problem: p, given: given, elapsed: Double(id + 1), responseSec: 1)
        }
        let list = [rec(0, p1, 54), rec(1, p2, 55), rec(2, p1, 56), rec(3, p2, 45), rec(4, p1, 63)]
        XCTAssertTrue(list[2].correct)
        XCTAssertEqual(AttemptRecord.mostMissed(list)?.id, 4, "p1·p2 둘 다 2번 → 최근 것")
        XCTAssertNil(AttemptRecord.mostMissed([list[2]]))
    }

    func testAnswerRecordConversion() {
        let wrong = AttemptRecord(id: 0, questionId: 3, problem: Problem(a: 7, b: 8), given: 54, elapsed: 12,
                                  responseSec: 1.5, pickA: 6, pickB: 9)
        XCTAssertEqual(wrong.answerRecord, AnswerRecord(a: 7, b: 8, correct: false, ms: 1500, given: .number(54)))
        let right = AttemptRecord(id: 1, questionId: 4, problem: Problem(a: 19, b: 12), given: 228, elapsed: 0,
                                  responseSec: 0.0004)
        XCTAssertEqual(right.answerRecord, AnswerRecord(a: 19, b: 12, correct: true, ms: 0, given: .number(228)))
    }

    // MARK: - 시드 난수 골든 벡터 (Android 와 같은 값 — 다르면 아래 게임 골든 벡터도 전부 어긋난다)

    func testMulberry32GoldenValues() {
        let r42 = rng(42)
        XCTAssertEqual((0..<3).map { _ in r42() }, [0.6011037519201636, 0.44829055899754167, 0.8524657934904099])
        let r2026 = rng(2026)
        XCTAssertEqual((0..<3).map { _ in r2026() }, [0.45540769933722913, 0.30849614599719644, 0.6611574492417276])
    }

    func testGameRandomGoldenValues() {
        // Fisher–Yates: i = 5…1 마다 index(i + 1) 하나씩 소비
        var order = Array(0..<6)
        GameRandom.shuffle(&order, random: rng(42))
        XCTAssertEqual(order, [1, 0, 4, 5, 2, 3])
        let r = rng(2026)
        XCTAssertEqual((0..<8).map { _ in GameRandom.index(8, random: r) }, [3, 2, 5, 4, 1, 1, 5, 6])
        // 부동소수 반올림으로 count 가 나와도 count - 1 에서 멈춤
        XCTAssertEqual(GameRandom.index(3, random: { 0.9999999999999999 }), 2)
        XCTAssertEqual(GameRandom.index(1, random: { 0.5 }), 0)
    }
}
