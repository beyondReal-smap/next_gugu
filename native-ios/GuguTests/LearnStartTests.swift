import XCTest
@testable import Gugu

/// 학습 탭 '단 먼저' 진입 — 단 시트에 보여 줄 모드, 게임(구구 계단·구구 몬스터) 시작 단 전달 (Android LearnStartTests.kt 미러)
final class LearnStartTests: XCTestCase {

    func testModeOptionsForTable() {
        XCTAssertEqual(Modes.options(table: 7).map(\.id), [.practice, .timeAttack, .missing, .truefalse])
        XCTAssertTrue(Modes.options(table: 2).allSatisfy(\.supportsTable))
    }

    func testModeOptionsForMixedIsWholeList() {
        XCTAssertEqual(Modes.options(table: nil).map(\.id), Modes.list.map(\.id))
        XCTAssertEqual(Modes.list.count, 6, "업적 mode_explorer 목표치 — list 는 그대로 둔다")
    }

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

    /// 홈 진입(.standard)·섞어서(.table(nil)) — 두 게임 모두 2~9단에서 고른다(몬스터는 섞어서)
    func testGameEnginesKeepDefaultTable() {
        let store = makeStore()
        let learning = LearningSpy()
        for seed: UInt32 in [1, 2, 3, 4, 5] {
            let stairs = StairsEngine(start: .standard, order: .sequential, topStep: 9, rewards: store, commit: learning.commit,
                                      random: SeededRandom.mulberry32(seed), runsLoop: false)
            XCTAssertTrue(StairsSession.randomTables.contains(stairs.session.table))
            let mixed = StairsEngine(start: .table(nil), order: .sequential, topStep: 9, rewards: store, commit: learning.commit,
                                     random: SeededRandom.mulberry32(seed), runsLoop: false)
            XCTAssertTrue(StairsSession.randomTables.contains(mixed.session.table), "섞어서 = 2~9단 무작위")
        }
        XCTAssertNil(MonsterEngine(start: .standard, order: .random, rewards: store, commit: learning.commit, runsLoop: false).chosenTable)
        XCTAssertNil(MonsterEngine(start: .table(nil), order: .random, rewards: store, commit: learning.commit, runsLoop: false).chosenTable)
    }

    /// 단 시트에서 고른 단 — 계단은 그 단부터(2~19단), 몬스터는 그 단만(2~9단)
    func testGameEnginesAcceptStartTable() {
        let store = makeStore()
        let learning = LearningSpy()
        for table in [2, 7, 9, 13, 19] {
            XCTAssertTrue(Minigame.stairs.supports(table: table))
            let engine = StairsEngine(start: .table(table), order: .sequential, topStep: 9, rewards: store, commit: learning.commit, runsLoop: false)
            XCTAssertEqual(engine.session.table, table)
            XCTAssertEqual(engine.session.current.problem, Problem(a: table, b: 1))
            XCTAssertEqual(engine.phase, .ready)
        }
        for table in [2, 7, 9] {
            XCTAssertTrue(Minigame.monster.supports(table: table))
            let engine = MonsterEngine(start: .table(table), order: .sequential, rewards: store, commit: learning.commit, runsLoop: false)
            XCTAssertEqual(engine.chosenTable, table)
            XCTAssertEqual(engine.session.current.target.a, table)
            XCTAssertEqual(engine.phase, .ready)
        }
    }
}
