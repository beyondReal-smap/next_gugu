import XCTest
@testable import Gugu

/// 학습 탭 '단 먼저' 진입 — 단 시트에 보여 줄 모드, 미니게임 시작 단 전달 (Android LearnStartTests.kt 미러)
final class LearnStartTests: XCTestCase {

    func testModeOptionsForTable() {
        XCTAssertEqual(Modes.options(table: 7).map(\.id), [.practice, .timeAttack, .missing, .truefalse])
        XCTAssertTrue(Modes.options(table: 2).allSatisfy(\.supportsTable))
    }

    func testModeOptionsForMixedIsWholeList() {
        XCTAssertEqual(Modes.options(table: nil).map(\.id), Modes.list.map(\.id))
        XCTAssertEqual(Modes.list.count, 6, "업적 mode_explorer 목표치 — list 는 그대로 둔다")
    }

    func testMinigameEnginesKeepDefaultTable() {
        XCTAssertEqual(BasketEngine().table, 2)
        XCTAssertEqual(BasketEngine().game.table, 2)
        XCTAssertNil(RunnerEngine().table)
        XCTAssertNil(LaneRunnerEngine().table)
        XCTAssertEqual(BasketEngine.defaultTable, 2)
        XCTAssertNil(RunnerEngine.defaultTable)
        XCTAssertNil(LaneRunnerEngine.defaultTable)
    }

    func testMinigameEnginesAcceptStartTable() {
        let basket = BasketEngine(table: 7)
        XCTAssertEqual(basket.table, 7)
        XCTAssertEqual(basket.game.table, 7)
        XCTAssertEqual(basket.game.phase, .ready)
        XCTAssertNil(BasketEngine(table: nil).table, "섞어서 = 전체")

        let runner = RunnerEngine(table: 5)
        XCTAssertEqual(runner.table, 5)
        XCTAssertEqual(runner.game.table, 5)

        let lane = LaneRunnerEngine(table: 9)
        XCTAssertEqual(lane.table, 9)
        XCTAssertEqual(lane.game.table, 9)
    }
}
