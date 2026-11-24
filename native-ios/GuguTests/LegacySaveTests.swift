import XCTest
@testable import Gugu

/// 어드벤처를 해 본 사용자의 학습 저장본(gugu.progress.v1).
/// modesPlayed·bestScores 에 "adventure" 가 들어 있다 — 어드벤처를 지운 뒤(다음 단계)에도 이 저장본이 읽혀야
/// 진행도가 통째로 격리·초기화되지 않는다. GameMode.adventure 는 지우지 않는다(2.9.0 결정) — 지우면 이 테스트가 깨져 알려 준다.
/// 2.9.0 의 게임 판 기록(stairs·monster)도 같은 저장본에 함께 쌓여 다시 읽혀야 한다.
final class LegacySaveTests: XCTestCase {

    /// 2.8.x 앱이 실제로 쓰던 모양: [GameMode: Int] 는 키·값을 번갈아 둔 배열, [Int: TableMastery] 는 문자열 키 객체
    private let legacyJSON = """
    {"version":1,"totalXp":840,"streak":3,"lastPlayedDate":"2026-11-20","dailyGoal":20,"dailyDate":"2026-11-20",
     "dailyCorrect":12,"tableMastery":{"2":{"stars":3,"bestAccuracy":1,"bestAvgMs":1800,"plays":4},
     "7":{"stars":1,"bestAccuracy":0.6,"bestAvgMs":3200,"plays":2}},
     "unlockedAchievements":["first_win","adv_first_boss"],"totalCorrect":210,"totalWrong":31,"maxCombo":14,
     "recentAccuracy":[80,90],"recentAvgMs":[2100,1900],"wrongPool":{"7 x 8":2},
     "bestScores":["challenge",15,"adventure",4],"modesPlayed":["practice","adventure","challenge"],
     "onboarded":true}
    """

    private func makeKey() -> String {
        let key = "gugu.test.legacy.\(UUID().uuidString)"
        addTeardownBlock {
            UserDefaults.standard.removeObject(forKey: key)
            Persistence.clearQuarantine(key: key)
        }
        return key
    }

    /// 어드벤처 기록이 든 저장본이 읽히고, 다른 진행도가 그대로 남는다
    func testGameStateWithAdventureDecodes() throws {
        let state = try JSONDecoder().decode(GameState.self, from: Data(legacyJSON.utf8))

        XCTAssertEqual(state.totalXp, 840)
        XCTAssertEqual(state.streak, 3)
        XCTAssertEqual(state.tableMastery[2]?.stars, 3)
        XCTAssertEqual(state.tableMastery[7]?.plays, 2)
        XCTAssertEqual(state.totalCorrect, 210)
        XCTAssertEqual(state.wrongPool["7 x 8"], 2)
        XCTAssertEqual(state.bestScores[.challenge], 15)
        XCTAssertTrue(state.modesPlayed.contains(.challenge))
        XCTAssertTrue(state.onboarded)
        XCTAssertNil(state.dayLog)
    }

    /// 실제 읽기 경로(Persistence.load)로도 읽히고 격리되지 않는다
    func testGameStateWithAdventureLoadsWithoutQuarantine() throws {
        let key = makeKey()
        UserDefaults.standard.set(Data(legacyJSON.utf8), forKey: key)

        let state = try XCTUnwrap(Persistence.load(GameState.self, key: key))

        XCTAssertEqual(state.totalXp, 840)
        XCTAssertEqual(state.bestScores[.challenge], 15)
        XCTAssertTrue(Persistence.quarantinedFiles(key: key).isEmpty)
    }

    /// 2.9.0 에서 GameMode 에 stairs·monster 를 더해도 원래 값(adventure 포함)의 저장 이름은 그대로다
    func testGameModeRawValuesAreStable() {
        XCTAssertEqual(GameMode.allCases.map(\.rawValue),
                       ["practice", "timeAttack", "challenge", "survival", "missing", "truefalse", "adventure", "stairs", "monster"])
    }

    /// 단별 합계(tables)가 생기기 전 저장본의 날짜 기록 — 읽히고(nil), 그날 다음 판부터는 새 답만 단별로 쌓인다.
    /// 그날 합계(correct·wrong·msSum·misses)는 예전 값에 이어서 더한다
    func testDayLogWithoutTablesDecodesAndTalliesFromNextRound() throws {
        let noon = try XCTUnwrap(Calendar.current.date(from: DateComponents(year: 2026, month: 11, day: 20, hour: 12)))
        let json = """
        {"totalXp":840,"onboarded":true,"dayLog":[
         {"date":"2026-11-19","correct":9,"wrong":3,"msSum":20000,"misses":{"7x8":2,"6x9":1}},
         {"date":"2026-11-20","correct":4,"wrong":1,"msSum":8000,"misses":{"7x8":1}}]}
        """
        let legacy = try JSONDecoder().decode(GameState.self, from: Data(json.utf8))
        XCTAssertEqual(legacy.dayLog?.map(\.date), ["2026-11-19", "2026-11-20"])
        XCTAssertEqual(legacy.dayLog?.map(\.tables), [nil, nil])

        let round = SessionResult(mode: .practice, table: 7, answers: [
            AnswerRecord(a: 7, b: 8, correct: true, ms: 1500),
            AnswerRecord(a: 7, b: 9, correct: false, ms: 2500),
        ], maxCombo: 1, durationMs: 4000)
        let next = Commit.applySession(legacy, result: round, now: noon).next
        let log = try XCTUnwrap(next.dayLog)
        XCTAssertNil(log[0].tables, "지난 날 기록은 건드리지 않는다")
        XCTAssertEqual(log[1].correct, 5)
        XCTAssertEqual(log[1].wrong, 2)
        XCTAssertEqual(log[1].msSum, 12000)
        XCTAssertEqual(log[1].misses, ["7x8": 1, "7x9": 1])
        XCTAssertEqual(log[1].tables, [7: TableTally(correct: 1, wrong: 1, msSum: 4000)])

        // 저장 → 다시 읽기에서 nil(구버전 날)과 값(새 날)이 그대로 남는다
        let key = makeKey()
        Persistence.save(next, key: key)
        XCTAssertEqual(try XCTUnwrap(Persistence.load(GameState.self, key: key)).dayLog, log)
        XCTAssertTrue(Persistence.quarantinedFiles(key: key).isEmpty)
    }

    /// 어드벤처 기록이 든 저장본에 게임 판을 기록해 저장하고 다시 읽어도 둘 다 남는다(새 값도 읽히고 격리 없음)
    func testLegacyStateKeepsAdventureAfterGameCommit() throws {
        let key = makeKey()
        let legacy = try JSONDecoder().decode(GameState.self, from: Data(legacyJSON.utf8))
        let stairs = SessionResult(mode: .stairs, table: nil, answers: [
            AnswerRecord(a: 4, b: 3, correct: true, ms: 1400, given: .number(12)),
            AnswerRecord(a: 4, b: 4, correct: false, ms: 2600, given: .number(20)),
        ], maxCombo: 1, durationMs: 8000)
        let monster = SessionResult(mode: .monster, table: 7, answers: [
            AnswerRecord(a: 7, b: 6, correct: true, ms: 1800, given: .number(42)),
        ], maxCombo: 1, durationMs: 60_000)
        var state = Commit.applySession(legacy, result: stairs).next
        state = Commit.applySession(state, result: monster).next
        Persistence.save(state, key: key)

        let loaded = try XCTUnwrap(Persistence.load(GameState.self, key: key))
        XCTAssertEqual(loaded, state)
        XCTAssertEqual(loaded.modesPlayed, [.practice, .adventure, .challenge, .stairs, .monster])
        XCTAssertEqual(loaded.bestScores[.challenge], 15)
        XCTAssertEqual(loaded.totalCorrect, 212)
        XCTAssertNotNil(loaded.wrongPool[Problems.key(4, 4)])
        XCTAssertEqual(loaded.tableMastery[7]?.plays, 2, "게임 판은 단 마스터리에 반영하지 않는다(GameMode.isGame)")
        XCTAssertTrue(Persistence.quarantinedFiles(key: key).isEmpty)
    }
}
