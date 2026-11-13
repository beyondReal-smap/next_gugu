import XCTest
@testable import Gugu

/// 앱 평가 요청 시점(ReviewPolicy)과 일본 ¥500 문구 판정(PremiumConfig.isOneCoinPrice)
final class ReviewPolicyTests: XCTestCase {

    private let now = Date(timeIntervalSince1970: 1_800_000_000)
    private let good = ReviewMoment(partial: false, accuracy: 95, isNewBest: false, improvedStars: false, goalReached: false)

    /// 끝까지 푼 판 `sessions`개, 서로 다른 날 `days`일을 쓴 상태
    private func state(sessions: Int, days: Int) -> GameState {
        var s = GameState()
        s.recentAccuracy = Array(repeating: 90, count: sessions)
        s.dayLog = (0..<days).map { DayLogEntry(date: "2026-11-0\($0 + 1)", correct: 10, wrong: 1, msSum: 0, misses: [:]) }
        return s
    }

    func testAsksAfterEnoughUseOnAGoodRound() {
        XCTAssertTrue(ReviewPolicy.shouldAsk(moment: good, state: state(sessions: 4, days: 2),
                                             log: ReviewPromptLog(), appVersion: "2.9.0", now: now))
    }

    func testNeverAsksOnPartialOrPoorRound() {
        var partial = good; partial.partial = true
        XCTAssertFalse(ReviewPolicy.isHappyMoment(partial))
        var poor = good; poor.accuracy = 70
        XCTAssertFalse(ReviewPolicy.isHappyMoment(poor))
        // 정확도가 낮아도 신기록·별 상승·오늘 목표 달성은 잘 해낸 순간이다
        var best = poor; best.isNewBest = true
        XCTAssertTrue(ReviewPolicy.isHappyMoment(best))
        var stars = poor; stars.improvedStars = true
        XCTAssertTrue(ReviewPolicy.isHappyMoment(stars))
        var goal = poor; goal.goalReached = true
        XCTAssertTrue(ReviewPolicy.isHappyMoment(goal))
    }

    func testWaitsUntilEnoughSessionsAndDays() {
        let log = ReviewPromptLog()
        XCTAssertFalse(ReviewPolicy.shouldAsk(moment: good, state: state(sessions: 3, days: 3), log: log, appVersion: "2.9.0", now: now))
        XCTAssertFalse(ReviewPolicy.shouldAsk(moment: good, state: state(sessions: 10, days: 1), log: log, appVersion: "2.9.0", now: now))
        // 구버전 저장본(dayLog nil)은 아직 쓴 날이 없는 것으로 본다
        var legacy = state(sessions: 10, days: 0); legacy.dayLog = nil
        XCTAssertFalse(ReviewPolicy.shouldAsk(moment: good, state: legacy, log: log, appVersion: "2.9.0", now: now))
    }

    func testActiveDaysIgnoresEmptyAndDuplicateDates() {
        var s = GameState()
        s.dayLog = [DayLogEntry(date: "2026-11-01", correct: 3, wrong: 0, msSum: 0, misses: [:]),
                    DayLogEntry(date: "2026-11-01", correct: 1, wrong: 0, msSum: 0, misses: [:]),
                    DayLogEntry(date: "2026-11-02", correct: 0, wrong: 0, msSum: 0, misses: [:])]
        XCTAssertEqual(ReviewPolicy.activeDays(s), 1)
    }

    func testOncePerVersionAndSpacedOut() {
        let s = state(sessions: 5, days: 3)
        let asked = ReviewPolicy.recordAsk(ReviewPromptLog(), appVersion: "2.9.0", now: now)
        XCTAssertEqual(asked.askCount, 1)
        // 같은 버전에서는 다시 묻지 않는다
        XCTAssertFalse(ReviewPolicy.shouldAsk(moment: good, state: s, log: asked, appVersion: "2.9.0",
                                              now: now.addingTimeInterval(365 * 86_400)))
        // 새 버전이어도 간격(60일) 안이면 묻지 않는다
        XCTAssertFalse(ReviewPolicy.shouldAsk(moment: good, state: s, log: asked, appVersion: "2.10.0",
                                              now: now.addingTimeInterval(59 * 86_400)))
        XCTAssertTrue(ReviewPolicy.shouldAsk(moment: good, state: s, log: asked, appVersion: "2.10.0",
                                             now: now.addingTimeInterval(61 * 86_400)))
    }

    func testPromptLogRoundTrip() throws {
        let log = ReviewPromptLog(lastAskedAt: now, lastAskedVersion: "2.9.0", askCount: 2)
        let data = try JSONEncoder().encode(log)
        XCTAssertEqual(try JSONDecoder().decode(ReviewPromptLog.self, from: data), log)
    }

    // MARK: - ¥500 ワンコイン 판정

    func testOneCoinOnlyForExactly500Yen() {
        XCTAssertTrue(PremiumConfig.isOneCoinPrice(500, currencyCode: "JPY"))
        XCTAssertFalse(PremiumConfig.isOneCoinPrice(600, currencyCode: "JPY"))   // 가격 자동 조정으로 바뀌면 문구도 원래대로
        XCTAssertFalse(PremiumConfig.isOneCoinPrice(500, currencyCode: "KRW"))
        XCTAssertFalse(PremiumConfig.isOneCoinPrice(4400, currencyCode: "KRW"))
    }
}
