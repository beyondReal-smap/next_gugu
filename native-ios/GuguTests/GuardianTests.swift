import XCTest
@testable import Gugu

/// 보호자 리포트(단별 실력·한마디 경우)와 보호자 흐름(문·잠김·알림 이동) — 계획서 redesign-v1 2-2, 2-4, 6-1.
/// Android(GuardianTests.kt)와 같은 케이스를 미러링한다
final class GuardianTests: XCTestCase {

    /// 2026-11-24(화) — 14일 기간 첫날은 2026-11-11
    private let today = "2026-11-24"

    /// 단별 합계만 주면 그날 합계는 그 합으로 채운다
    private func day(_ date: String, _ tables: [Int: TableTally]?, misses: [String: Int] = [:],
                     correct: Int? = nil, wrong: Int? = nil) -> DayLogEntry {
        let values = Array((tables ?? [:]).values)
        return DayLogEntry(date: date,
                           correct: correct ?? values.map(\.correct).reduce(0, +),
                           wrong: wrong ?? values.map(\.wrong).reduce(0, +),
                           msSum: values.map(\.msSum).reduce(0, +),
                           misses: misses, tables: tables)
    }

    private func tally(_ correct: Int, of attempts: Int, ms: Int = 2000) -> TableTally {
        TableTally(correct: correct, wrong: attempts - correct, msSum: ms * attempts)
    }

    private func state(_ log: [DayLogEntry], mastery: [Int: TableMastery] = [:]) -> GameState {
        var s = GameState()
        s.dayLog = log
        s.tableMastery = mastery
        return s
    }

    // MARK: - TableSkills

    /// 오늘 포함 14일째(11-11)는 들어가고 15일째(11-10)와 오늘 뒤의 날(시계를 되돌린 기록)은 빠진다
    func testSkillWindowIsFourteenDaysIncludingToday() {
        XCTAssertEqual(TableSkills.windowStart(today: today), "2026-11-11")
        let log = [
            day("2026-11-10", [7: tally(100, of: 100)]),
            day("2026-11-11", [7: tally(3, of: 4, ms: 1000)]),
            day(today, [7: tally(2, of: 2, ms: 1500)]),
            day("2026-11-25", [7: tally(50, of: 50)]),
        ]
        let skill = TableSkills.build(log, today: today)[7]
        XCTAssertEqual(skill, TableSkill(table: 7, attempts: 6, correct: 5, msSum: 7000))
        XCTAssertEqual(skill?.avgMs, 1167)
    }

    /// 2~19단이 모두 나오고, 단별 합계가 없는 날(이 필드 전 저장본)과 범위 밖 단은 더하지 않는다
    func testSkillsCoverEveryTableAndSkipDaysWithoutTables() {
        let log = [
            day("2026-11-20", nil, misses: ["3x4": 2], correct: 20, wrong: 2),
            day("2026-11-21", [3: tally(4, of: 5), 1: tally(9, of: 9), 25: tally(9, of: 9)]),
        ]
        let skills = TableSkills.build(log, today: today)
        XCTAssertEqual(skills.keys.sorted(), Array(2...19))
        XCTAssertEqual(skills[3]?.attempts, 5)
        XCTAssertEqual(skills.values.map(\.attempts).reduce(0, +), 5)
        XCTAssertNil(skills[2]?.accuracy)
        XCTAssertNil(skills[2]?.avgMs)
        XCTAssertTrue(TableSkills.build(nil, today: today).values.allSatisfy { $0.status == .noRecord })
    }

    /// 문제 수 10개 미만은 판정하지 않고, 90% 이상 잘해요 · 70% 미만 약해요 · 그 사이 연습 중
    func testSkillStatusBoundaries() {
        func status(_ correct: Int, of attempts: Int) -> SkillStatus {
            TableSkill(table: 6, attempts: attempts, correct: correct, msSum: 0).status
        }
        XCTAssertEqual(status(0, of: 0), .noRecord)
        XCTAssertEqual(status(9, of: 9), .practicing, "9문제는 다 맞아도 연습 중")
        XCTAssertEqual(status(0, of: 9), .practicing, "9문제는 다 틀려도 약해요가 아니다")
        XCTAssertEqual(status(9, of: 10), .strong)
        XCTAssertEqual(status(89, of: 100), .practicing)
        XCTAssertEqual(status(7, of: 10), .practicing)
        XCTAssertEqual(status(14, of: 20), .practicing)
        XCTAssertEqual(status(69, of: 100), .weak)
        XCTAssertEqual(status(10, of: 10), .strong)
    }

    /// 약한 순 — 정확도 오름차순, 같으면 단 오름차순. 연습 중·잘해요·문제 수 부족은 빠진다
    func testWeakTablesSortedWeakestFirst() {
        let log = [day(today, [
            8: tally(6, of: 10), 7: tally(6, of: 10), 6: tally(5, of: 10),
            9: tally(9, of: 10), 4: tally(0, of: 5), 5: tally(8, of: 10),
        ])]
        XCTAssertEqual(TableSkills.weak(TableSkills.build(log, today: today)), [6, 7, 8])
        XCTAssertEqual(TableSkills.weak(TableSkills.build(nil, today: today)), [])
    }

    // MARK: - GuardianReport

    /// 2~9단은 늘 행이 있고, 10~19단은 기간 안에 기록이 있을 때만 나온다
    func testReportRowsShowTwoToNineAndRecordedHigherTables() {
        let log = [
            day("2026-11-01", [15: tally(5, of: 5)]),   // 기간 밖
            day(today, [12: tally(3, of: 4), 3: tally(2, of: 2)]),
        ]
        let report = GuardianReport.build(state(log), today: today)
        XCTAssertEqual(report.rows.map(\.table), [2, 3, 4, 5, 6, 7, 8, 9, 12])
        XCTAssertEqual(report.rows.first(where: { $0.table == 12 })?.attempts, 4)
        XCTAssertEqual(GuardianReport.build(state([]), today: today).rows.map(\.table), Array(2...9))
    }

    /// 자주 틀린 문제 — 같은 14일(단별 합계가 없는 날 포함), 많이 틀린 순(같으면 키 순), 최대 5개, 읽을 수 없는 키 제외
    func testReportTopMissesUseWindowAndLimit() {
        let log = [
            day("2026-11-10", nil, misses: ["2x2": 50], correct: 1, wrong: 50),   // 기간 밖
            day("2026-11-12", nil, misses: ["7x8": 2, "6x9": 1, "bad": 9], correct: 1, wrong: 3),
            day(today, [7: tally(1, of: 9)], misses: ["7x8": 2, "8x7": 3, "6x7": 1, "9x9": 1, "3x4": 1, "5x5": 1]),
        ]
        let report = GuardianReport.build(state(log), today: today)
        XCTAssertEqual(report.topMisses, [
            MissCount(key: "7x8", count: 4), MissCount(key: "8x7", count: 3),
            MissCount(key: "3x4", count: 1), MissCount(key: "5x5", count: 1), MissCount(key: "6x7", count: 1),
        ])
    }

    /// 한마디 경우 5가지 — 기록 없음, 약한 단 2개, 약한 단 1개, 계속(다음 섬), 모두 잘함(섞어서)
    func testReportHeadlineCases() {
        // 기록 없음: 비었거나 기간 밖 기록만
        XCTAssertEqual(GuardianReport.build(state([]), today: today).headline, .noRecord)
        XCTAssertEqual(GuardianReport.build(state([day("2026-11-01", [7: tally(1, of: 10)])]), today: today).headline,
                       .noRecord)

        // 약한 단 2개 이상 → 가장 약한 둘 (V1 시제품: 7단 58%·8단 62%)
        let twoWeak = state([day(today, [7: tally(58, of: 100), 8: tally(62, of: 100), 6: tally(71, of: 100),
                                         2: tally(98, of: 100)])])
        XCTAssertEqual(GuardianReport.build(twoWeak, today: today).headline, .weak(first: 7, second: 8))
        XCTAssertEqual(GuardianReport.build(twoWeak, today: today).weak, [7, 8])
        let threeWeak = state([day(today, [7: tally(58, of: 100), 8: tally(62, of: 100), 5: tally(60, of: 100)])])
        XCTAssertEqual(GuardianReport.build(threeWeak, today: today).headline, .weak(first: 7, second: 5))
        // 약한 단 1개
        let oneWeak = state([day(today, [6: tally(5, of: 10), 3: tally(10, of: 10)])])
        XCTAssertEqual(GuardianReport.build(oneWeak, today: today).headline, .weak(first: 6, second: nil))

        // 약한 단 없음 → 오늘의 섬: 2단이 잘해요라 로드맵 다음인 5단
        let noWeak = state([day(today, [2: tally(10, of: 10), 3: tally(3, of: 4)])])
        XCTAssertEqual(GuardianReport.build(noWeak, today: today).headline, .keepGoing(next: 5))

        // 약한 단 없음 + 남은 단이 모두 별 3개 → 섞어서
        var full: [Int: TableMastery] = [:]
        for t in Hints.roadmapTables { full[t] = TableMastery(stars: 3, bestAccuracy: 1, bestAvgMs: 900, plays: 2) }
        let allStrong = state([day(today, [4: tally(9, of: 10)])], mastery: full)
        XCTAssertEqual(GuardianReport.build(allStrong, today: today).headline, .allStrong)
    }

    /// 리포트가 권하는 단(약한 단 첫째)과 아이 말풍선(오늘의 섬)이 같은 단이고, 이번 주는 WeeklyReport 와 같다
    func testReportAgreesWithTodayIslandAndWeek() {
        let log = [day("2026-11-23", [9: tally(4, of: 10), 7: tally(5, of: 10)], misses: ["9x6": 3])]
        let s = state(log)
        let report = GuardianReport.build(s, today: today)
        XCTAssertEqual(report.headline, .weak(first: 9, second: 7))
        XCTAssertEqual(TodayIsland.pick(mastery: s.tableMastery, skills: TableSkills.build(s.dayLog, today: today)),
                       .table(9, reason: .weak))
        XCTAssertEqual(report.week, WeeklyReport.build(log, today: today))
    }

    // MARK: - GuardianFlow

    private let steps: [GuardianStep] = [
        .idle, .kidLocked(.missing), .holding(.report), .holding(.paywall),
        .open(.report), .open(.settings(focus: .notifications)), .open(.paywall),
    ]
    private let events: [GuardianEvent] = [
        .tapKey, .tapLocked(.truefalse), .callGuardian, .holdCompleted, .birthYearPassed,
        .route(.report), .route(.settings(focus: .notifications)), .route(.paywall),
        .dismiss, .resumed(awaySeconds: 0), .resumed(awaySeconds: 299), .resumed(awaySeconds: 300), .resumed(awaySeconds: 3600),
    ]

    /// 계획서 2-2 표의 줄 — (지금, 사건, 다음)
    private var specRows: [(GuardianStep, GuardianEvent, GuardianStep)] {
        var rows: [(GuardianStep, GuardianEvent, GuardianStep)] = [
            (.idle, .tapKey, .holding(.report)),
            (.idle, .tapLocked(.truefalse), .kidLocked(.truefalse)),
            (.idle, .callGuardian, .holding(.paywall)),
            (.kidLocked(.missing), .callGuardian, .holding(.paywall)),
        ]
        for d in [GuardianDestination.report, .settings(focus: .notifications), .paywall] {
            rows.append((.idle, .route(d), .holding(d)))
        }
        for d in [GuardianDestination.report, .paywall] {
            rows.append((.holding(d), .holdCompleted, .open(d)))
            rows.append((.holding(d), .birthYearPassed, .open(d)))
        }
        for open in steps {
            guard case .open = open else { continue }
            for d in [GuardianDestination.report, .settings(focus: .notifications), .paywall] {
                rows.append((open, .route(d), .open(d)))
            }
        }
        for s in steps where s != .idle {
            rows.append((s, .dismiss, .idle))
            rows.append((s, .resumed(awaySeconds: 300), .idle))
            rows.append((s, .resumed(awaySeconds: 3600), .idle))
        }
        return rows
    }

    func testFlowFollowsSpecTable() {
        for (step, event, next) in specRows {
            XCTAssertEqual(GuardianFlow.reduce(step, event), next, "\(step) + \(event)")
        }
    }

    /// 표에 없는 조합은 모두 그대로 — 빠른 두 번 누르기 같은 정상 입력이라 무시한다
    func testFlowIgnoresEveryOtherCombination() {
        let rows = specRows
        var checked = 0
        for step in steps {
            for event in events where !rows.contains(where: { $0.0 == step && $0.1 == event }) {
                XCTAssertEqual(GuardianFlow.reduce(step, event), step, "\(step) + \(event)")
                checked += 1
            }
        }
        XCTAssertGreaterThan(checked, 40)
    }

    /// 앱을 떠났다 오면 300초 이상일 때만 잠근다 (299초 유지, 300초 잠금)
    func testFlowRelocksAfterFiveMinutesAway() {
        XCTAssertEqual(GuardianFlow.relockAfterAwaySeconds, 300)
        for step in [GuardianStep.kidLocked(.survival), .holding(.paywall), .open(.settings(focus: nil))] {
            XCTAssertEqual(GuardianFlow.reduce(step, .resumed(awaySeconds: 299)), step)
            XCTAssertEqual(GuardianFlow.reduce(step, .resumed(awaySeconds: 300)), .idle)
        }
        XCTAssertEqual(GuardianFlow.reduce(.idle, .resumed(awaySeconds: 9999)), .idle)
    }

    /// 열쇠 → 3초 → 리포트 → 알림으로 설정 이동(다시 묻지 않음) → 닫기 → 잠긴 타일 → 보호자 부르기 → 3초 → 결제 화면
    func testFlowWalkthrough() {
        var s = GuardianStep.idle
        for event: GuardianEvent in [.tapKey, .tapKey, .holdCompleted] { s = GuardianFlow.reduce(s, event) }
        XCTAssertEqual(s, .open(.report))
        s = GuardianFlow.reduce(s, .route(.settings(focus: .notifications)))
        XCTAssertEqual(s, .open(.settings(focus: .notifications)))
        for event: GuardianEvent in [.dismiss, .tapLocked(.challenge)] { s = GuardianFlow.reduce(s, event) }
        XCTAssertEqual(s, .kidLocked(.challenge))
        for event: GuardianEvent in [.callGuardian, .birthYearPassed] { s = GuardianFlow.reduce(s, event) }
        XCTAssertEqual(s, .open(.paywall))
    }
}
