import XCTest
@testable import Gugu

/// UI/UX 개선에 쓰는 Core 로직 — Android(UxCoreTests.kt)와 같은 케이스를 미러링한다
final class UxCoreTests: XCTestCase {

    // 이 클래스의 기대값은 곱하는 수 상한 19 기준이다 — 테스트 호스트 앱이 설정값을 읽어 바꿔 두므로 매번 고정한다
    override func setUp() {
        super.setUp()
        Problems.maxB = 19
    }

    override func tearDown() {
        Problems.maxB = Problems.defaultMaxB
        super.tearDown()
    }

    // MARK: - KoreanReading

    func testExpandedKoreanChantsKeepFactorsDistinct() {
        let cases = [(3, 19, "삼 곱하기 십구는 오십칠"),
                     (10, 2, "십 곱하기 이는 이십"),
                     (9, 10, "구 곱하기 십은 구십"),
                     (19, 19, "십구 곱하기 십구는 삼백육십일")]
        for (a, b, expected) in cases {
            XCTAssertEqual(KoreanReading.chant(a, b), expected)
            XCTAssertEqual(VoiceLines.chant(a, b, language: .ko).text, expected + ".")
        }
        for a in 2...9 {
            for b in 1...9 {
                let first = KoreanReading.sino(a)
                let tail = "\(KoreanReading.topic(KoreanReading.sino(b))) \(KoreanReading.sino(a * b))"
                XCTAssertEqual(KoreanReading.chant(a, b), "\(first) \(tail)")
                XCTAssertEqual(VoiceLines.chant(a, b, language: .ko).text,
                               "\(first)\(b == 2 ? "," : "") \(tail).")
            }
        }
    }

    func testSinoNumbers() {
        XCTAssertEqual(KoreanReading.sino(0), "영")
        XCTAssertEqual(KoreanReading.sino(1), "일")
        XCTAssertEqual(KoreanReading.sino(10), "십")
        XCTAssertEqual(KoreanReading.sino(12), "십이")
        XCTAssertEqual(KoreanReading.sino(56), "오십육")
        XCTAssertEqual(KoreanReading.sino(81), "팔십일")
        XCTAssertEqual(KoreanReading.sino(90), "구십")
        XCTAssertEqual(KoreanReading.sino(100), "백")
        XCTAssertEqual(KoreanReading.sino(1234), "천이백삼십사")
    }

    func testTopicParticle() {
        // 받침 있는 수: 일(ㄹ) 삼(ㅁ) 육(ㄱ) 칠(ㄹ) 팔(ㄹ) → 은 / 없는 수: 이 사 오 구 → 는
        XCTAssertEqual(KoreanReading.topic("일"), "일은")
        XCTAssertEqual(KoreanReading.topic("삼"), "삼은")
        XCTAssertEqual(KoreanReading.topic("육"), "육은")
        XCTAssertEqual(KoreanReading.topic("칠"), "칠은")
        XCTAssertEqual(KoreanReading.topic("팔"), "팔은")
        XCTAssertEqual(KoreanReading.topic("이"), "이는")
        XCTAssertEqual(KoreanReading.topic("사"), "사는")
        XCTAssertEqual(KoreanReading.topic("오"), "오는")
        XCTAssertEqual(KoreanReading.topic("구"), "구는")
        XCTAssertEqual(KoreanReading.toward("새싹 들판"), "새싹 들판으로")
        XCTAssertEqual(KoreanReading.toward("학교"), "학교로")
        XCTAssertEqual(KoreanReading.toward("서울"), "서울로")
        XCTAssertFalse(KoreanReading.hasBatchim("abc"))
        XCTAssertFalse(KoreanReading.hasBatchim(""))
    }

    func testQuestionPhrases() {
        let p = Problem(a: 4, b: 9)
        XCTAssertEqual(KoreanReading.question(p, mode: .practice, statement: nil), "사 곱하기 구는?")
        XCTAssertEqual(KoreanReading.question(p, mode: .missing, statement: nil), "삼십육은 사 곱하기 몇일까요?")
        XCTAssertEqual(KoreanReading.question(Problem(a: 4, b: 3), mode: .missing, statement: nil), "십이는 사 곱하기 몇일까요?")
        XCTAssertEqual(KoreanReading.question(Problem(a: 4, b: 5), mode: .missing, statement: nil), "이십은 사 곱하기 몇일까요?")
        XCTAssertEqual(KoreanReading.question(p, mode: .truefalse, statement: Statement(shown: 32, isTrue: false)),
                       "사 곱하기 구는 삼십이. 맞을까요?")
        XCTAssertEqual(KoreanReading.question(Problem(a: 7, b: 1), mode: .challenge, statement: nil), "칠 곱하기 일은?")
    }

    func testParticlesForDigits() {
        XCTAssertEqual(KoreanReading.withTopic(1), "1은")
        XCTAssertEqual(KoreanReading.withTopic(2), "2는")
        XCTAssertEqual(KoreanReading.withTopic(36), "36은")
        XCTAssertEqual(KoreanReading.withSubject(35), "35가")
        XCTAssertEqual(KoreanReading.withSubject(36), "36이")
        XCTAssertEqual(KoreanReading.withSubject(10), "10이")
        XCTAssertEqual(KoreanReading.withCopula(20), "20이에요")
        XCTAssertEqual(KoreanReading.withCopula(12), "12예요")
        XCTAssertEqual(KoreanReading.withCopula(0), "0이에요")
        XCTAssertEqual(KoreanReading.notButIs(given: 35, answer: 36), "35가 아니라 36이에요")
        XCTAssertEqual(KoreanReading.notButIs(given: 28, answer: 24), "28이 아니라 24예요")
    }

    func testChantLines() {
        XCTAssertEqual(KoreanReading.chant(7, 8), "칠 팔은 오십육")
        XCTAssertEqual(KoreanReading.chant(2, 1), "이 일은 이")
        XCTAssertEqual(KoreanReading.chant(2, 5), "이 오는 십")
        XCTAssertEqual(KoreanReading.chant(9, 9), "구 구는 팔십일")
    }

    // MARK: - Hints

    func testCoachHintStrategies() {
        let nine = Hints.coach(a: 9, b: 7)
        XCTAssertEqual(nine.title, "10단에서 한 번 빼기")
        XCTAssertEqual(nine.scaffold, "70 − 7 = ?")
        XCTAssertEqual(nine.full, "9 × 7 = 70 − 7 = 63")

        let swapped = Hints.coach(a: 6, b: 2)
        XCTAssertEqual(swapped.title, "순서를 바꿔 두 번 더하기")
        XCTAssertEqual(swapped.scaffold, "6 + 6 = ?")
        XCTAssertEqual(swapped.full, "6 × 2 = 6 + 6 = 12")

        XCTAssertEqual(Hints.coach(a: 2, b: 2).title, "두 번 더하기")
        XCTAssertEqual(Hints.coach(a: 2, b: 2).scaffold, "2 + 2 = ?")

        let one = Hints.coach(a: 8, b: 1)
        XCTAssertEqual(one.title, "1을 곱하면 그대로예요")
        XCTAssertEqual(one.full, "8 × 1 = 8")

        let five = Hints.coach(a: 5, b: 3)
        XCTAssertEqual(five.scaffold, "5씩 3번 세어 봐요")
        XCTAssertEqual(five.full, "5 × 3 → 5, 10, 15")

        XCTAssertEqual(Hints.coach(a: 7, b: 6).scaffold, "30 + 12 = ?")
        XCTAssertEqual(Hints.coach(a: 7, b: 6).full, "7 × 6 = 30 + 12 = 42")
        XCTAssertEqual(Hints.coach(a: 3, b: 4).full, "3 × 4 = 8 + 4 = 12")
        XCTAssertEqual(Hints.coach(a: 4, b: 6).scaffold, "12 + 12 = ?")
        XCTAssertEqual(Hints.coach(a: 8, b: 7).scaffold, "28 + 28 = ?")
        XCTAssertEqual(Hints.coach(a: 6, b: 7).scaffold, "35 + 7 = ?")
        XCTAssertEqual(Hints.tableTip(9), Hints.coach(a: 9, b: 7))
    }

    /// 전 범위에서 풀이의 답이 맞고, 푸는 중에 보여 주는 scaffold 에는 정답이 드러나지 않는다
    func testCoachHintsNeverLeakAnswerAndAreCorrect() {
        for a in Problems.minTable...Problems.maxTable {
            for b in Problems.minB...Problems.maxB {
                let hint = Hints.coach(a: a, b: b)
                XCTAssertTrue(hint.full.hasSuffix(String(a * b)), "\(a)×\(b) full=\(hint.full)")
                let numbers = hint.scaffold.split(whereSeparator: { !$0.isNumber }).compactMap { Int($0) }
                XCTAssertFalse(numbers.contains(a * b), "\(a)×\(b) scaffold 에 정답 노출: \(hint.scaffold)")
            }
        }
    }

    func testNextRoadmapTable() {
        XCTAssertEqual(Hints.nextRoadmapTable([:]), 2)
        let m1 = TableMastery(stars: 1, bestAccuracy: 0.8, bestAvgMs: 3000, plays: 1)
        XCTAssertEqual(Hints.nextRoadmapTable([2: m1]), 5)

        // 7단만 별이 없다 → 7
        var allButSeven: [Int: TableMastery] = [:]
        for t in (2...19).filter({ $0 != 7 }) { allButSeven[t] = m1 }
        XCTAssertEqual(Hints.nextRoadmapTable(allButSeven), 7)

        // 전부 별이 있으면 가장 적은 단(로드맵 순) — 3단·4단이 1개 → 로드맵상 3이 먼저
        func m(_ s: Int) -> TableMastery { TableMastery(stars: s, bestAccuracy: 1, bestAvgMs: 1000, plays: 1) }
        var mixed: [Int: TableMastery] = [2: m(3), 5: m(2), 3: m(1), 4: m(1), 6: m(3), 9: m(3), 7: m(3), 8: m(3)]
        XCTAssertEqual(Hints.nextRoadmapTable(mixed), 10)
        for t in 10...19 { mixed[t] = m(3) }
        XCTAssertEqual(Hints.nextRoadmapTable(mixed), 3)

        var full: [Int: TableMastery] = [:]
        for t in Hints.roadmapTables { full[t] = m(3) }
        XCTAssertNil(Hints.nextRoadmapTable(full))
    }

    // MARK: - 오늘의 섬

    /// 기록이 쌓이기 전에는 예전 규칙(Hints.nextRoadmapTable)과 같은 단이 나온다 — 그 테스트의 기대값 그대로
    func testTodayIslandKeepsRoadmapRuleWithoutRecords() {
        let none = TableSkills.build(nil, today: "2026-11-24")
        func pick(_ mastery: [Int: TableMastery]) -> TodayIsland { TodayIsland.pick(mastery: mastery, skills: none) }
        XCTAssertEqual(pick([:]), .table(2, reason: .next))
        XCTAssertEqual(TodayIsland.pick(mastery: [:], skills: [:]), .table(2, reason: .next))
        let m1 = TableMastery(stars: 1, bestAccuracy: 0.8, bestAvgMs: 3000, plays: 1)
        XCTAssertEqual(pick([2: m1]), .table(5, reason: .next))

        var allButSeven: [Int: TableMastery] = [:]
        for t in (2...19).filter({ $0 != 7 }) { allButSeven[t] = m1 }
        XCTAssertEqual(pick(allButSeven), .table(7, reason: .next))

        func m(_ s: Int) -> TableMastery { TableMastery(stars: s, bestAccuracy: 1, bestAvgMs: 1000, plays: 1) }
        var mixed: [Int: TableMastery] = [2: m(3), 5: m(2), 3: m(1), 4: m(1), 6: m(3), 9: m(3), 7: m(3), 8: m(3)]
        XCTAssertEqual(pick(mixed), .table(10, reason: .next))
        for t in 10...19 { mixed[t] = m(3) }
        XCTAssertEqual(pick(mixed), .table(3, reason: .next))

        var full: [Int: TableMastery] = [:]
        for t in Hints.roadmapTables { full[t] = m(3) }
        XCTAssertEqual(pick(full), .mixed)
    }

    private func skills(_ tallies: [Int: TableTally]) -> [Int: TableSkill] {
        let total = tallies.values.reduce(TableTally.zero, +)
        return TableSkills.build([DayLogEntry(date: "2026-11-24", correct: total.correct, wrong: total.wrong,
                                              msSum: total.msSum, misses: [:], tables: tallies)], today: "2026-11-24")
    }

    /// 약한 단이 있으면 별과 상관없이 가장 약한 단
    func testTodayIslandPrefersWeakestTable() {
        let recent = skills([8: TableTally(correct: 6, wrong: 4, msSum: 0), 7: TableTally(correct: 5, wrong: 5, msSum: 0)])
        let starred = [7: TableMastery(stars: 3, bestAccuracy: 1, bestAvgMs: 900, plays: 5)]
        XCTAssertEqual(TodayIsland.pick(mastery: starred, skills: recent), .table(7, reason: .weak))
    }

    /// 최근 기록이 잘해요인 단은 건너뛴다 — 게임만 해서 별이 없는 아이도 다음 섬으로 넘어간다
    func testTodayIslandSkipsStrongTables() {
        let ten = TableTally(correct: 10, wrong: 0, msSum: 0)
        XCTAssertEqual(TodayIsland.pick(mastery: [:], skills: skills([2: ten])), .table(5, reason: .next))
        XCTAssertEqual(TodayIsland.pick(mastery: [:], skills: skills([2: ten, 5: ten])), .table(3, reason: .next))
        var twoToNine: [Int: TableTally] = [:]
        for t in 2...9 { twoToNine[t] = ten }
        XCTAssertEqual(TodayIsland.pick(mastery: [:], skills: skills(twoToNine)), .table(10, reason: .next))
        var all: [Int: TableTally] = [:]
        for t in 2...19 { all[t] = ten }
        XCTAssertEqual(TodayIsland.pick(mastery: [:], skills: skills(all)), .mixed, "남은 단이 없으면 섞어서")
    }

    // MARK: - 섬 시트

    func testIslandPlanRecommendedMethod() {
        func skill(_ correct: Int, of attempts: Int) -> TableSkill {
            TableSkill(table: 5, attempts: attempts, correct: correct, msSum: 0)
        }
        XCTAssertEqual(IslandPlan.recommended(table: 5, stars: 0, skill: skill(0, of: 0)), .tableStudy, "처음 가는 단")
        XCTAssertEqual(IslandPlan.recommended(table: 5, stars: 2, skill: skill(4, of: 10)), .tableStudy, "약한 단")
        XCTAssertEqual(IslandPlan.recommended(table: 5, stars: 0, skill: skill(3, of: 5)), .mode(.practice), "게임으로 풀어 봤다")
        XCTAssertEqual(IslandPlan.recommended(table: 5, stars: 2, skill: skill(0, of: 0)), .mode(.practice), "별이 있다")
        XCTAssertEqual(IslandPlan.recommended(table: 5, stars: 3, skill: skill(10, of: 10)), .mode(.practice))
    }

    func testIslandPlanSamples() {
        XCTAssertEqual(IslandPlan.sample(table: 5), Problem(a: 5, b: 3))
        XCTAssertEqual(IslandPlan.sample(table: 19), Problem(a: 19, b: 3))
        XCTAssertEqual(IslandPlan.sample(table: nil), Problem(a: 7, b: 8))
        XCTAssertEqual(IslandPlan.oxShown(Problem(a: 5, b: 3)), 16)
        XCTAssertEqual(IslandPlan.oxShown(Problem(a: 7, b: 8)), 57)
        XCTAssertEqual(IslandPlan.studyChip(table: 19), Problem(a: 19, b: 2))
    }

    // MARK: - 테마 첫값

    /// 고른 적 없으면 낮, 다크를 고른 사람은 밤 그대로, 손상된 값은 낮
    func testThemeInitial() {
        XCTAssertEqual(Theme.initial(saved: nil), .light)
        XCTAssertEqual(Theme.initial(saved: "dark"), .dark)
        XCTAssertEqual(Theme.initial(saved: "light"), .light)
        XCTAssertEqual(Theme.initial(saved: "system"), .system)
        XCTAssertEqual(Theme.initial(saved: "Dark"), .light)
        XCTAssertEqual(Theme.initial(saved: ""), .light)
    }

    // MARK: - Problems.reviewQueue

    func testParseKey() {
        XCTAssertEqual(Problems.parseKey("7x8"), Problem(a: 7, b: 8))
        XCTAssertNil(Problems.parseKey("1x5"))
        XCTAssertNil(Problems.parseKey("7x0"))
        XCTAssertNil(Problems.parseKey("ab"))
        XCTAssertNil(Problems.parseKey("7x"))
        XCTAssertNil(Problems.parseKey("x8"))
    }

    func testReviewQueuePicksHeaviest() {
        let pool = ["7x8": 6, "6x9": 4, "2x3": 2, "9x9": 0, "bad": 5]
        let top2 = Problems.reviewQueue(pool, count: 2, random: { 0 })
        XCTAssertEqual(Set(top2.map { Problems.key($0.a, $0.b) }), ["7x8", "6x9"])

        // 가중치 0·형식 오류는 제외 — 남는 것은 3개뿐
        let all = Problems.reviewQueue(pool, count: 10, random: { 0.5 })
        XCTAssertEqual(Set(all.map { Problems.key($0.a, $0.b) }), ["7x8", "6x9", "2x3"])
        XCTAssertTrue(Problems.reviewQueue(pool, count: 0).isEmpty)
        XCTAssertTrue(Problems.reviewQueue([:], count: 10).isEmpty)
    }

    func testReviewQueueTieBreakUsesRandom() {
        // 키 정렬 순서(2x2, 3x3, 4x4)대로 난수가 배정된다 → 가장 작은 난수(0.1)의 3x3 이 뽑힌다
        var seq = [0.9, 0.1, 0.5]
        let picked = Problems.reviewQueue(["2x2": 3, "3x3": 3, "4x4": 3], count: 1, random: { seq.removeFirst() })
        XCTAssertEqual(picked, [Problem(a: 3, b: 3)])
    }

    // MARK: - WeeklyReport

    func testDateShiftAndWeekday() {
        XCTAssertEqual(WeeklyReport.shift("2026-03-01", by: -1), "2026-02-28")
        XCTAssertEqual(WeeklyReport.shift("2024-03-01", by: -1), "2024-02-29")
        XCTAssertEqual(WeeklyReport.shift("2026-01-01", by: -6), "2025-12-26")
        XCTAssertEqual(WeeklyReport.weekdayLabel("2026-09-29"), "화")
        XCTAssertEqual(WeeklyReport.weekdayLabel("2026-03-01"), "일")
        XCTAssertEqual(WeeklyReport.weekdayLabel("2024-02-29"), "목")
    }

    /// 이번 주 = 오늘이 든 주의 월~일. 오늘 뒤의 날은 0(시계를 되돌려 생긴 기록도), 지난주는 같은 요일까지만 비교한다
    func testWeeklyReportAggregates() {
        let log = [
            DayLogEntry(date: "2026-09-21", correct: 4, wrong: 0, msSum: 1, misses: [:]),     // 지난주 월 — 비교
            DayLogEntry(date: "2026-09-22", correct: 7, wrong: 1, msSum: 1, misses: ["2x2": 1]), // 지난주 화 — 비교
            DayLogEntry(date: "2026-09-23", correct: 100, wrong: 0, msSum: 1, misses: [:]),   // 지난주 수 — 오늘(화) 뒤라 비교 안 함
            DayLogEntry(date: "2026-09-27", correct: 50, wrong: 5, msSum: 1, misses: ["9x9": 5]), // 지난주 일
            DayLogEntry(date: "2026-09-28", correct: 3, wrong: 0, msSum: 9_000, misses: [:]), // 이번 주 월
            DayLogEntry(date: "2026-09-29", correct: 10, wrong: 2, msSum: 30_000, misses: ["7x8": 2]), // 오늘(화)
            DayLogEntry(date: "2026-09-30", correct: 99, wrong: 9, msSum: 1, misses: ["6x9": 9]), // 오늘 뒤 — 0
        ]
        let r = WeeklyReport.build(log, today: "2026-09-29")
        XCTAssertEqual(r.days.map(\.date), ["2026-09-28", "2026-09-29", "2026-09-30", "2026-10-01",
                                            "2026-10-02", "2026-10-03", "2026-10-04"])
        XCTAssertEqual(r.days.map(\.correct), [3, 10, 0, 0, 0, 0, 0])
        XCTAssertEqual(r.days[2].wrong, 0)
        XCTAssertEqual(r.correct, 13)
        XCTAssertEqual(r.wrong, 2)
        XCTAssertEqual(r.activeDays, 2)
        XCTAssertEqual(r.studyMs, 39_000)
        XCTAssertEqual(r.accuracy, 87)   // 13/15 = 86.7%
        XCTAssertEqual(r.prevCorrect, 11)
        XCTAssertEqual(r.correctDelta, 2)
        XCTAssertEqual(r.topMisses, [MissCount(key: "7x8", count: 2)])
    }

    /// 월요일이면 이번 주는 오늘 하루와 남은 6일(0), 지난주 비교는 지난 월요일 하루. 일요일이면 둘 다 꽉 찬 7일
    func testWeeklyReportOnMondayAndSunday() {
        let log = (0..<14).map { i -> DayLogEntry in
            // 2026-11-16(월) ~ 2026-11-29(일), 하루 정답 = i + 1
            DayLogEntry(date: WeeklyReport.shift("2026-11-16", by: i), correct: i + 1, wrong: 0, msSum: 0, misses: [:])
        }
        let monday = WeeklyReport.build(log, today: "2026-11-23")
        XCTAssertEqual(monday.days.first?.date, "2026-11-23")
        XCTAssertEqual(monday.days.last?.date, "2026-11-29")
        XCTAssertEqual(monday.days.map(\.correct), [8, 0, 0, 0, 0, 0, 0])
        XCTAssertEqual(monday.prevCorrect, 1)

        let sunday = WeeklyReport.build(log, today: "2026-11-29")
        XCTAssertEqual(sunday.days.map(\.date), DailyMissionPlan.week(of: "2026-11-29"))
        XCTAssertEqual(sunday.days.map(\.correct), [8, 9, 10, 11, 12, 13, 14])
        XCTAssertEqual(sunday.prevCorrect, 28)   // 1 + … + 7
        XCTAssertEqual(sunday.activeDays, 7)
    }

    func testWeeklyReportMergesDuplicateDatesAndHandlesEmpty() {
        let dup = [
            DayLogEntry(date: "2026-09-29", correct: 2, wrong: 1, msSum: 100, misses: ["3x4": 1]),
            DayLogEntry(date: "2026-09-29", correct: 3, wrong: 1, msSum: 200, misses: ["3x4": 1]),
        ]
        let r = WeeklyReport.build(dup, today: "2026-09-29")
        XCTAssertEqual(r.correct, 5)
        XCTAssertEqual(r.wrong, 2)
        XCTAssertEqual(r.topMisses, [MissCount(key: "3x4", count: 2)])

        let empty = WeeklyReport.build(nil, today: "2026-09-29")
        XCTAssertEqual(empty.days.count, 7)
        XCTAssertNil(empty.accuracy)
        XCTAssertEqual(empty.activeDays, 0)
        XCTAssertTrue(empty.topMisses.isEmpty)
    }

    func testWeeklyParentTip() {
        let today = "2026-10-01"   // 목 — 이번 주 월(09-28)부터 셈
        XCTAssertEqual(WeeklyReport.build(nil, today: today).parentTip,
                       "이번 주는 아직 기록이 없어요. 하루 5분, 한 판부터 함께 시작해 보세요.")
        // 같은 문제를 2번 이상 틀렸으면 그 단 외우기를 권한다 (8=팔 → "8이에요")
        let missed = [DayLogEntry(date: today, correct: 5, wrong: 2, msSum: 1, misses: ["7x8": 2])]
        XCTAssertEqual(WeeklyReport.build(missed, today: today).parentTip,
                       "자주 헷갈리는 문제는 7 × 8이에요. 7단 표를 소리 내어 함께 외워 보세요.")
        // 하루만 했으면 습관을 권한다
        let once = [DayLogEntry(date: today, correct: 9, wrong: 1, msSum: 1, misses: ["6x9": 1])]
        XCTAssertTrue(WeeklyReport.build(once, today: today).parentTip.hasPrefix("짧게라도 매일"))
        // 사흘 이상 + 정확도 90% 이상이면 속도 도전을 권한다
        let steady = ["2026-09-29", "2026-09-30", "2026-10-01"].map {
            DayLogEntry(date: $0, correct: 10, wrong: 0, msSum: 1, misses: [:])
        }
        XCTAssertTrue(WeeklyReport.build(steady, today: today).parentTip.hasPrefix("정확도가 아주 좋아요"))
    }

    // MARK: - Commit — 빈 판

    /// 한 문제도 풀지 않고 끝난 판은 정확도 추이·스트릭·모드 탐험에 남지 않는다
    func testEmptySessionIsNotCompletion() {
        var s = Commit.defaultState
        s.streak = 4
        s.lastPlayedDate = Commit.todayStr(Calendar.current.date(byAdding: .day, value: -1, to: Date())!)
        let empty = SessionResult(mode: .challenge, table: nil, answers: [], maxCombo: 0, durationMs: 60_000)
        let (next, commit) = Commit.applySession(s, result: empty)
        XCTAssertTrue(next.recentAccuracy.isEmpty)
        XCTAssertEqual(next.streak, 4)   // 오늘 자격 없음 → 그대로
        XCTAssertFalse(next.modesPlayed.contains(.challenge))
        XCTAssertNil(commit.score)
        XCTAssertFalse(commit.isNewBest)
    }

    /// 끊긴 스트릭은 0 으로 보여 준다 — 저장값은 그대로 둔다
    func testActiveStreak() {
        let now = Date()
        func day(_ offset: Int) -> String { Commit.todayStr(Calendar.current.date(byAdding: .day, value: offset, to: now)!) }
        var s = Commit.defaultState
        s.streak = 4
        s.lastPlayedDate = day(0)
        XCTAssertEqual(Commit.activeStreak(s, now: now), 4)
        s.lastPlayedDate = day(-1)
        XCTAssertEqual(Commit.activeStreak(s, now: now), 4)
        s.lastPlayedDate = day(-2)
        XCTAssertEqual(Commit.activeStreak(s, now: now), 0)
        XCTAssertEqual(s.streak, 4)
        XCTAssertEqual(Commit.activeStreak(Commit.defaultState, now: now), 0)
    }

    // MARK: - Achievements.progress

    func testAchievementProgress() {
        var s = Commit.defaultState
        s.totalCorrect = 142
        s.modesPlayed = [.practice, .challenge]
        s.tableMastery = [2: TableMastery(stars: 3, bestAccuracy: 1, bestAvgMs: 1000, plays: 2),
                          3: TableMastery(stars: 2, bestAccuracy: 0.9, bestAvgMs: 2000, plays: 1)]
        XCTAssertEqual(Achievements.progress("correct_500", s), AchievementProgress(current: 142, target: 500))
        XCTAssertEqual(Achievements.progress("first_correct", s), AchievementProgress(current: 1, target: 1))
        XCTAssertEqual(Achievements.progress("mode_explorer", s), AchievementProgress(current: 2, target: 6))
        XCTAssertEqual(Achievements.progress("stars_12", s), AchievementProgress(current: 5, target: 12))
        XCTAssertNil(Achievements.progress("unknown", s))
    }

    /// 판정과 진행도는 같은 기준 — 진행도가 목표에 닿은 업적만 해금 대상이다
    func testAchievementCheckMatchesProgress() {
        var s = Commit.defaultState
        s.totalCorrect = 120
        s.maxCombo = 12
        s.streak = 7
        s.bestScores = [.challenge: 15]
        for def in Achievements.all {
            let p = Achievements.progress(def.id, s)!
            XCTAssertEqual(def.check(s), p.current >= p.target, def.id)
        }
        XCTAssertEqual(Set(Achievements.newlyUnlocked(s)),
                       ["first_correct", "correct_100", "combo_10", "streak_3", "streak_7", "challenge_15"])
    }
}
