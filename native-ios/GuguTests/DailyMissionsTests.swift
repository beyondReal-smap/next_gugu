import XCTest
@testable import Gugu

/// 그 날짜(기기 현지 달력) 정오 — Commit.todayStr 가 그 날짜를 내도록 테스트가 쓰는 시각
func localNoon(_ day: String) -> Date {
    let p = day.split(separator: "-").map { Int($0)! }
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = .current
    return cal.date(from: DateComponents(year: p[0], month: p[1], day: p[2], hour: 12))!
}

/// 오늘의 미션 + 보물상자 + 이번 주 도장 (계획서 .claude/plans/daily-missions.md).
/// 미션 계획은 날짜 문자열만으로 정해지고(같은 날 = 같은 미션, 이틀 연속 다름), 진행도는 결과 저장(recordRound) 때 쌓이며,
/// 보물상자는 3개를 다 한 날 한 번만 열리고(별 +10·도장), 구버전 저장본은 미션 없이 읽히고 규칙 밖 저장본은 격리된다.
final class DailyMissionsTests: XCTestCase {

    // MARK: - 도우미

    private func makeKey() -> String {
        let key = "gugu.test.missions.\(UUID().uuidString)"
        addTeardownBlock {
            UserDefaults.standard.removeObject(forKey: key)
            Persistence.clearQuarantine(key: key)
        }
        return key
    }

    private func makeStore(_ key: String) -> PlayRewardsStore {
        // 어드벤처 키도 고유하게 — 이관할 조각 0
        let adventureKey = makeKey()
        return PlayRewardsStore(key: key, adventureKey: adventureKey)
    }

    private func saved(_ key: String) throws -> PlayRewards {
        try XCTUnwrap(Persistence.load(PlayRewards.self, key: key))
    }

    /// 2026-11-24(화, 1970 부터 20781일 — 홀수): 계단 10 · 몬스터 5 · 별 15
    private let tue = "2026-11-24"
    private let stairs10 = Mission(kind: .stairsSteps, target: 10)
    private let monsters5 = Mission(kind: .monstersDefeated, target: 5)
    private let stars15 = Mission(kind: .starsEarned, target: 15)

    // MARK: - 미션 계획

    /// 계획서의 공식 그대로 — n = 1970-01-01 부터 일 수
    func testPlanMatchesFormulaOnKnownDays() {
        XCTAssertEqual(DailyMissionPlan.missions(day: "1970-01-01"),   // n = 0
                       [Mission(kind: .stairsSteps, target: 10), Mission(kind: .monstersDefeated, target: 5),
                        Mission(kind: .bestStreak, target: 5)])
        XCTAssertEqual(DailyMissionPlan.missions(day: tue), [stairs10, monsters5, stars15])   // n = 20781
        XCTAssertEqual(DailyMissionPlan.missions(day: "2026-11-25"),   // n = 20782
                       [Mission(kind: .stairsSteps, target: 15), Mission(kind: .monstersDefeated, target: 5),
                        Mission(kind: .bestStreak, target: 7)])
        XCTAssertEqual(DailyMissionPlan.missions(day: "2026-11-23"),   // n = 20780
                       [Mission(kind: .stairsSteps, target: 20), Mission(kind: .monstersDefeated, target: 10),
                        Mission(kind: .bestStreak, target: 5)])
        XCTAssertEqual(DailyMissionPlan.missions(day: "2026-11-30"),   // n = 20787
                       [Mission(kind: .stairsSteps, target: 10), Mission(kind: .monstersDefeated, target: 10),
                        Mission(kind: .starsEarned, target: 25)])
    }

    /// 3년 동안 매일: 항상 3개(계단·몬스터·연속|별 순서), 목표는 풀 안, 같은 날은 같은 미션, 어제와는 다른 미션
    func testPlanIsDeterministicThreeFromPoolsAndChangesDaily() {
        var previous: [Mission]?
        for n in 20_000..<(20_000 + 365 * 3) {
            let day = DailyMissionPlan.dayString(n)
            let missions = DailyMissionPlan.missions(day: day)
            XCTAssertEqual(missions, DailyMissionPlan.missions(day: day), day)
            XCTAssertEqual(missions.count, 3, day)
            XCTAssertEqual(missions[0].kind, .stairsSteps, day)
            XCTAssertTrue([10, 15, 20].contains(missions[0].target), day)
            XCTAssertEqual(missions[1].kind, .monstersDefeated, day)
            XCTAssertTrue([5, 8, 10].contains(missions[1].target), day)
            switch missions[2].kind {
            case .bestStreak: XCTAssertTrue([5, 7].contains(missions[2].target), day)
            case .starsEarned: XCTAssertTrue([15, 25].contains(missions[2].target), day)
            default: XCTFail("셋째 미션은 연속 또는 별: \(missions[2]) \(day)")
            }
            if let previous { XCTAssertNotEqual(missions, previous, day) }
            previous = missions
        }
    }

    /// 일 수는 달력(UTC 그레고리력)으로 센 값과 같고, 날짜 문자열로 되돌아온다(윤년·세기 경계 포함)
    func testDayNumberMatchesCalendarAndRoundTrips() {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let epoch = utc.date(from: DateComponents(year: 1970, month: 1, day: 1))!
        for day in ["1970-01-01", "1999-12-31", "2000-02-29", "2024-02-29", "2026-03-01", "2026-11-24", "2100-03-01", "1969-12-31"] {
            let p = day.split(separator: "-").map { Int($0)! }
            let date = utc.date(from: DateComponents(year: p[0], month: p[1], day: p[2]))!
            let expected = utc.dateComponents([.day], from: epoch, to: date).day!
            XCTAssertEqual(DailyMissionPlan.dayNumber(day), expected, day)
            XCTAssertEqual(DailyMissionPlan.dayString(expected), day)
        }
        XCTAssertEqual(DailyMissionPlan.missions(day: "1969-12-31").count, 3)   // 1970 년 전도 풀 안에서 고른다
    }

    /// 형식이 틀리거나 없는 날짜는 일 수가 없다
    func testInvalidDayStringsHaveNoNumber() {
        for bad in ["", "2026-11-31", "2026-02-29", "2026-13-01", "2026-00-10", "2026-1-05", "26-11-24", "2026/11/24",
                    "2026-11-24 ", "abcd-ef-gh", "２０２６-11-24", "+026-11-24"] {
            XCTAssertNil(DailyMissionPlan.dayNumber(bad), bad)
        }
    }

    /// 이번 주 = 그 날이 든 주의 월~일(달 경계 포함)
    func testWeekRunsMondayToSunday() {
        let week = ["2026-11-23", "2026-11-24", "2026-11-25", "2026-11-26", "2026-11-27", "2026-11-28", "2026-11-29"]
        XCTAssertEqual(DailyMissionPlan.week(of: "2026-11-23"), week)   // 월
        XCTAssertEqual(DailyMissionPlan.week(of: tue), week)
        XCTAssertEqual(DailyMissionPlan.week(of: "2026-11-29"), week)   // 일
        XCTAssertEqual(DailyMissionPlan.week(of: "2026-12-02").first, "2026-11-30")
        XCTAssertEqual(DailyMissionPlan.week(of: "2026-12-02").last, "2026-12-06")
    }

    // MARK: - 미션 구슬 → 게임

    /// 계단·몬스터 미션은 그 게임. 연속·별 미션은 아직 자기 미션을 못 끝낸 첫 게임(계단 → 몬스터), 둘 다 끝났으면 계단
    func testMissionOrbOpensGame() {
        func board(stairs: Int = 0, monsters: Int = 0, day: String) -> MissionBoard {
            var log = DailyMissionLog(day: day)
            log.stairsSteps = stairs
            log.monstersDefeated = monsters
            return MissionBoard(log: log, chestDays: [])
        }
        XCTAssertEqual(board(day: tue).game(for: stairs10), .stairs)
        XCTAssertEqual(board(day: tue).game(for: monsters5), .monster)
        XCTAssertEqual(board(day: tue).game(for: stars15), .stairs)
        XCTAssertEqual(board(stairs: 10, day: tue).game(for: stars15), .monster)
        XCTAssertEqual(board(monsters: 5, day: tue).game(for: stars15), .stairs)
        XCTAssertEqual(board(stairs: 10, monsters: 5, day: tue).game(for: stars15), .stairs)
        XCTAssertEqual(board(stairs: 10, monsters: 5, day: tue).game(for: monsters5), .monster, "끝난 미션도 자기 게임")

        // 짝수 날의 세 번째 미션(연속 정답)도 같은 규칙 — 2026-11-25: 계단 15 · 몬스터 5 · 연속 7
        let streak7 = Mission(kind: .bestStreak, target: 7)
        XCTAssertEqual(board(day: "2026-11-25").game(for: streak7), .stairs)
        XCTAssertEqual(board(stairs: 15, day: "2026-11-25").game(for: streak7), .monster)
    }

    // MARK: - 결과 저장 → 미션 진행

    /// 계단·몬스터·별은 판마다 더하고, 연속은 판들 중 최고. 새로 목표를 넘은 미션만 미션 순서대로 돌려준다
    func testRecordRoundAccumulatesAndReportsNewCompletions() throws {
        let key = makeKey()
        let store = makeStore(key)
        let now = localNoon(tue)

        let first = store.recordRound(.stairs, score: 6, stars: 8, bestStreak: 6, now: now)
        XCTAssertEqual(first.missionsCompleted, [])
        XCTAssertFalse(first.allMissionsDone)

        // 계단 6+6=12 ≥ 10, 별 8+9=17 ≥ 15 — 한 판에 둘이 새로 끝나면 미션 순서대로
        let second = store.recordRound(.stairs, score: 6, stars: 9, bestStreak: 2, now: now)
        XCTAssertEqual(second.missionsCompleted, [stairs10, stars15])
        XCTAssertFalse(second.allMissionsDone)

        let third = store.recordRound(.monster, score: 3, stars: 3, bestStreak: 3, now: now)
        XCTAssertEqual(third.missionsCompleted, [])   // 이미 끝낸 미션은 다시 넣지 않음

        let board = store.missionBoard(now: now)
        XCTAssertEqual(board.items.map(\.mission), [stairs10, monsters5, stars15])
        XCTAssertEqual(board.items.map(\.progress), [10, 3, 15])   // 목표에서 자름(실제 12·20)
        XCTAssertEqual(board.items.map(\.done), [true, false, true])
        XCTAssertEqual(board.doneCount, 2)
        XCTAssertFalse(board.chestReady)

        let log = try XCTUnwrap(try saved(key).missionLog)
        XCTAssertEqual(log.day, tue)
        XCTAssertEqual(log.stairsSteps, 12)
        XCTAssertEqual(log.monstersDefeated, 3)
        XCTAssertEqual(log.starsEarned, 20)
        XCTAssertEqual(log.bestStreak, 6)   // 더하지 않고 최고
    }

    /// 3개가 처음 다 되는 판에서만 allMissionsDone. 그 뒤 판은 false
    func testAllMissionsDoneReportedOnlyOnce() {
        let store = makeStore(makeKey())
        let now = localNoon(tue)
        store.recordRound(.stairs, score: 10, stars: 15, bestStreak: 4, now: now)

        let finishing = store.recordRound(.monster, score: 5, stars: 0, bestStreak: 5, now: now)
        XCTAssertEqual(finishing.missionsCompleted, [monsters5])
        XCTAssertTrue(finishing.allMissionsDone)
        XCTAssertTrue(store.missionBoard(now: now).chestReady)

        let after = store.recordRound(.monster, score: 5, stars: 5, bestStreak: 5, now: now)
        XCTAssertEqual(after.missionsCompleted, [])
        XCTAssertFalse(after.allMissionsDone)
    }

    /// 날짜가 바뀌면 어제 진행은 0 으로 보이고, 다음 결과 저장 때 새 날 기록으로 바뀐다
    func testNewDayStartsFromZero() throws {
        let key = makeKey()
        let store = makeStore(key)
        store.recordRound(.stairs, score: 12, stars: 3, bestStreak: 9, now: localNoon(tue))

        let wed = "2026-11-25"   // 계단 15 · 몬스터 5 · 연속 7
        let board = store.missionBoard(now: localNoon(wed))
        XCTAssertEqual(board.items.map(\.mission), DailyMissionPlan.missions(day: wed))
        XCTAssertEqual(board.items.map(\.progress), [0, 0, 0])
        XCTAssertEqual(try saved(key).missionLog?.day, tue)   // 읽기는 저장본을 바꾸지 않음

        let record = store.recordRound(.stairs, score: 4, stars: 1, bestStreak: 2, now: localNoon(wed))
        XCTAssertEqual(record.missionsCompleted, [])
        let log = try XCTUnwrap(try saved(key).missionLog)
        XCTAssertEqual(log.day, wed)
        XCTAssertEqual(log.stairsSteps, 4)
        XCTAssertEqual(log.bestStreak, 2)
    }

    // MARK: - 보물상자

    /// 준비 전엔 오류·별 그대로. 다 하면 별 +10·열림·오늘 도장. 두 번째는 오류. 다시 열어도(재실행) 그대로
    func testOpenChestRules() throws {
        let key = makeKey()
        let store = makeStore(key)
        let now = localNoon(tue)
        store.recordRound(.stairs, score: 10, stars: 15, bestStreak: 0, now: now)

        XCTAssertThrowsError(try store.openChest(now: now)) { error in
            XCTAssertEqual(error as? ChestError, .missionsLeft)
        }
        XCTAssertEqual(store.stars, 15)
        XCTAssertEqual(store.missionBoard(now: now).week.filter(\.stamped), [])

        store.recordRound(.monster, score: 5, stars: 0, bestStreak: 0, now: now)
        XCTAssertEqual(try store.openChest(now: now), 10)
        XCTAssertEqual(store.stars, 25)
        let board = store.missionBoard(now: now)
        XCTAssertTrue(board.chestOpened)
        XCTAssertFalse(board.chestReady)
        XCTAssertEqual(board.week.filter(\.stamped).map(\.date), [tue])
        XCTAssertEqual(board.week.filter(\.isToday).map(\.date), [tue])

        XCTAssertThrowsError(try store.openChest(now: now)) { error in
            XCTAssertEqual(error as? ChestError, .alreadyOpened)
        }
        XCTAssertEqual(store.stars, 25)

        let reopened = makeStore(key)
        XCTAssertEqual(reopened.stars, 25)
        XCTAssertEqual(reopened.missionBoard(now: now), board)
        XCTAssertEqual(try saved(key).chestDays, [tue])
    }

    /// 다음 날: 새 미션·상자 닫힘, 어제 도장은 이번 주 칸에 남는다
    func testStampStaysForTheWeekAfterNewDay() throws {
        let store = makeStore(makeKey())
        fillAll(store, day: tue)
        try store.openChest(now: localNoon(tue))

        let board = store.missionBoard(now: localNoon("2026-11-25"))
        XCTAssertFalse(board.chestOpened)
        XCTAssertFalse(board.allDone)
        XCTAssertEqual(board.week.filter(\.stamped).map(\.date), [tue])
        XCTAssertEqual(board.week.filter(\.isToday).map(\.date), ["2026-11-25"])

        // 다음 주에는 지난주 도장이 보이지 않는다
        XCTAssertEqual(store.missionBoard(now: localNoon("2026-11-30")).week.filter(\.stamped), [])
    }

    /// 도장은 최근 14개만, 오름차순. 기기 시계를 되돌려 앞 날짜에 열어도 순서가 깨지지 않는다(다시 읽힘)
    func testChestDaysCappedAndSorted() throws {
        let key = makeKey()
        let store = makeStore(key)
        let start = try XCTUnwrap(DailyMissionPlan.dayNumber("2026-11-01"))
        let days = (0..<15).map { DailyMissionPlan.dayString(start + $0) }
        for day in days {
            fillAll(store, day: day)
            try store.openChest(now: localNoon(day))
        }
        XCTAssertEqual(try saved(key).chestDays, Array(days.dropFirst()))
        XCTAssertEqual(store.stars, try saved(key).stars)

        // 시계를 되돌림: 2026-11-10 은 이미 도장 — 그날 새 기록으로 다시 열어도 중복 없이 정렬 유지
        fillAll(store, day: "2026-11-10")
        try store.openChest(now: localNoon("2026-11-10"))
        let after = try saved(key).chestDays
        XCTAssertEqual(after, after.sorted())
        XCTAssertEqual(after.count, DailyMissionPlan.chestDayCap)
        XCTAssertEqual(Set(after).count, after.count)
        XCTAssertEqual(makeStore(key).stars, store.stars)   // 다시 읽어도 격리되지 않음
    }

    /// 그날 미션 3개를 모두 채운다
    private func fillAll(_ store: PlayRewardsStore, day: String) {
        let missions = DailyMissionPlan.missions(day: day)
        let now = localNoon(day)
        store.recordRound(.stairs, score: missions[0].target, stars: 25, bestStreak: 7, now: now)
        store.recordRound(.monster, score: missions[1].target, stars: 0, bestStreak: 0, now: now)
        XCTAssertTrue(store.missionBoard(now: now).chestReady, day)
    }

    // MARK: - 저장본

    /// 미션 필드가 없는 구버전 저장본은 기록 없음·도장 없음으로 읽힌다(별·기록은 그대로)
    func testLegacySaveWithoutMissionFieldsLoads() throws {
        let key = makeKey()
        UserDefaults.standard.set(Data(#"{"version":1,"stars":7,"stairsBest":4,"adventureMigrated":true}"#.utf8), forKey: key)

        let store = makeStore(key)

        XCTAssertEqual(store.stars, 7)
        XCTAssertEqual(store.best(.stairs), 4)
        XCTAssertNil(store.rewards.missionLog)
        XCTAssertEqual(store.rewards.chestDays, [])
        XCTAssertEqual(store.missionBoard(now: localNoon(tue)).items.map(\.progress), [0, 0, 0])
        XCTAssertTrue(Persistence.quarantinedFiles(key: key).isEmpty)
    }

    /// 미션 기록·도장이 규칙 밖(날짜 형식·음수·순서·중복·개수)이면 조용히 고치지 않고 원본을 격리한다
    func testRuleBreakingMissionSavesAreQuarantined() {
        let fifteen = (0..<15).map { "\"\(DailyMissionPlan.dayString(20_000 + $0))\"" }.joined(separator: ",")
        let cases = [
            #"{"stars":3,"missionLog":{"day":"2026-11-31"}}"#,
            #"{"stars":3,"missionLog":{"day":"20261124"}}"#,
            #"{"stars":3,"missionLog":{"stairsSteps":2}}"#,
            #"{"stars":3,"missionLog":{"day":"2026-11-24","starsEarned":-1}}"#,
            #"{"stars":3,"chestDays":["2026-11-24","2026-11-23"]}"#,
            #"{"stars":3,"chestDays":["2026-11-24","2026-11-24"]}"#,
            #"{"stars":3,"chestDays":["2026-11-24","어제"]}"#,
            "{\"stars\":3,\"chestDays\":[\(fifteen)]}",
        ]
        for json in cases {
            let key = makeKey()
            UserDefaults.standard.set(Data(json.utf8), forKey: key)

            let store = makeStore(key)

            XCTAssertEqual(store.stars, 0, json)
            XCTAssertEqual(Persistence.quarantinedFiles(key: key).count, 1, json)
        }
    }

    /// 형식에 맞는 미션 기록은 그대로 읽히고 누락 필드는 0
    func testValidMissionLogLoads() throws {
        let key = makeKey()
        UserDefaults.standard.set(Data(#"""
        {"stars":3,"adventureMigrated":true,"missionLog":{"day":"2026-11-24","stairsSteps":11,"chestOpened":false},
         "chestDays":["2026-11-20","2026-11-23"]}
        """#.utf8), forKey: key)

        let board = makeStore(key).missionBoard(now: localNoon(tue))

        XCTAssertEqual(board.items.map(\.progress), [10, 0, 0])
        XCTAssertEqual(board.week.filter(\.stamped).map(\.date), ["2026-11-23"])   // 11-20 은 지난주
        XCTAssertTrue(Persistence.quarantinedFiles(key: key).isEmpty)
    }

    // MARK: - 결과 창 문구

    /// 3개가 처음 다 되면 보물상자 안내, 새로 끝낸 미션이 있으면 첫 미션, 아니면 줄 없음
    func testResultMissionNote() {
        func record(_ completed: [Mission], all: Bool) -> PlayRoundRecord {
            PlayRoundRecord(previousBest: 0, isRecord: false, starTotal: 0, missionsCompleted: completed, allMissionsDone: all)
        }
        XCTAssertEqual(GameResultContent.missionNote(record([monsters5], all: true)), "오늘의 미션 완료! 홈에서 보물상자를 열어 봐요")
        XCTAssertEqual(GameResultContent.missionNote(record([stairs10, stars15], all: false)), "미션 완료! 계단 10칸 오르기")
        XCTAssertNil(GameResultContent.missionNote(record([], all: false)))
    }
}
