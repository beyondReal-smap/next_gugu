import XCTest
@testable import Gugu

/// P1 Core 도메인 로직 검증 — TS 원본(lib/)과의 기능 패리티 확인
final class CoreTests: XCTestCase {

    // 이 클래스의 기대값은 곱하는 수 상한 19 기준이다 — 테스트 호스트 앱이 설정값을 읽어 바꿔 두므로 매번 고정한다
    override func setUp() {
        super.setUp()
        Problems.maxB = 19
    }

    override func tearDown() {
        Problems.maxB = Problems.defaultMaxB
        super.tearDown()
    }

    func testNewLanguagesReadEveryNineteenTableProblem() {
        for language in [AppLanguage.hi, .vi, .id] {
            for problem in Problems.candidatesAll() {
                for mode in [GameMode.practice, .missing, .truefalse] {
                    let line = VoiceLines.question(problem, mode: mode, statement: Statement(shown: problem.a * problem.b, isTrue: true), language: language)
                    XCTAssertFalse(line.text.isEmpty)
                    XCTAssertFalse(VoiceLines.usesRecordedAudio(line, language: language))
                }
                XCTAssertTrue(VoiceLines.chant(problem.a, problem.b, language: language).text.contains(String(problem.a * problem.b)))
            }
        }
        XCTAssertEqual(SpokenMath.questionLabel(19, 19, language: .vi), "19 nhân 19 bằng bao nhiêu?")
        XCTAssertEqual(SpokenMath.questionLabel(19, 19, language: .id), "Berapa 19 kali 19?")
        XCTAssertEqual(SpokenMath.questionLabel(19, 19, language: .hi), "19 गुणा 19 कितना होता है?")
        XCTAssertTrue(VoiceLines.usesRecordedAudio(VoiceLines.chant(9, 9, language: .ko), language: .ko))
        XCTAssertTrue(VoiceLines.usesRecordedAudio(VoiceLines.chant(9, 19, language: .ko), language: .ko))
    }

    func testNineteenTimesTableRange() {
        let pool = Problems.candidatesAll()
        XCTAssertEqual(pool.count, 342)
        XCTAssertEqual(pool.last, Problem(a: 19, b: 19))
        XCTAssertEqual(Problems.parseKey("19x19"), Problem(a: 19, b: 19))
        XCTAssertNil(Problems.parseKey("20x19"))
        XCTAssertNil(Problems.parseKey("19x20"))
        XCTAssertEqual(Problems.candidates(table: 19).count, 19)
    }

    /// 설정 「곱하는 수」 9 — 19단도 19 × 9까지만 나오고, 이미 쌓인 × 10 이상 기록은 복원·표시만 한다
    func testMaxMultiplierNineLimitsQuestionsButKeepsRecords() {
        Problems.maxB = 9
        let pool = Problems.candidatesAll()
        XCTAssertEqual(pool.count, 18 * 9)
        XCTAssertEqual(pool.last, Problem(a: 19, b: 9))
        XCTAssertEqual(Problems.candidates(table: 19).map(\.b), Array(1...9))
        XCTAssertEqual(Problems.candidates(table: 9).map(\.b), Array(1...9))

        // 오답 가중이 큰 범위 밖 문제가 있어도 출제는 상한 안에서만
        let heavy = ["19x15": 6, "19x19": 6]
        let seed = SeededRandom.mulberry32(7)
        for _ in 0..<300 {
            XCTAssertLessThanOrEqual(Problems.pick(table: 19, wrongPool: heavy, recentKeys: [], random: { seed() }).b, 9)
            XCTAssertLessThanOrEqual(Problems.pick(table: nil, wrongPool: heavy, recentKeys: [], random: { seed() }).b, 9)
        }

        // 복습 큐는 상한 밖 문제를 건너뛰고, 기록 계약(parseKey)은 19까지 그대로
        XCTAssertEqual(Problems.reviewQueue(["7x8": 2, "7x15": 6, "19x19": 6], count: 10, random: { 0.5 }), [Problem(a: 7, b: 8)])
        XCTAssertEqual(Problems.parseKey("7x15"), Problem(a: 7, b: 15))
        XCTAssertEqual(Problems.parseKey("19x19"), Problem(a: 19, b: 19))

        Problems.maxB = 12
        XCTAssertEqual(Problems.candidates(table: 19).last, Problem(a: 19, b: 12))
        XCTAssertEqual(Problems.candidatesAll().count, 18 * 12)
    }

    func testNineteenTimesTableChoicesAndReading() {
        // 구구 계단 19단 꼭대기(「곱하는 수」 19) 발판 — 정답 361 포함 서로 다른 3개, 보기 최대값(19 × 19 + 19) 안
        let choices = StairsSession.makeChoices(Problem(a: 19, b: 19), standing: 19 * 18, topStep: 19, random: { 0.999 })
        XCTAssertTrue(choices.contains(361))
        XCTAssertEqual(Set(choices).count, 3)
        XCTAssertTrue(choices.allSatisfy { (1...StairsSession.maxChoice(table: 19, topStep: 19)).contains($0) })
        XCTAssertEqual(EnglishReading.cardinal(361), "three hundred sixty-one")
        XCTAssertEqual(JapaneseReading.number(361), "さんびゃくろくじゅういち")
        XCTAssertEqual(JapaneseReading.chant(19, 19), "じゅうきゅう かける じゅうきゅう は さんびゃくろくじゅういち")
        XCTAssertTrue(LearningContract.isFactId("19x19"))
        XCTAssertFalse(LearningContract.isFactId("20x19"))
        XCTAssertFalse(LearningContract.isFactId("019x19"))
        XCTAssertTrue(LearningContract.isInFactRange(a: 19, b: 19))
        XCTAssertEqual(sessionAnswerSlotDigits(problem: Problem(a: 19, b: 19), mode: .practice, input: ""), 3)
    }


    // MARK: - Level / XP

    func testLevelThresholds() {
        // xpToReach(2)=60, xpToReach(3)=60+95=155
        XCTAssertEqual(Level.xpToReach(2), 60)
        XCTAssertEqual(Level.xpToReach(3), 155)
        XCTAssertEqual(Level.info(totalXp: 0).level, 1)
        XCTAssertEqual(Level.info(totalXp: 59).level, 1)
        XCTAssertEqual(Level.info(totalXp: 60).level, 2)
        XCTAssertEqual(Level.info(totalXp: 155).level, 3)
    }

    func testLevelProgressBounds() {
        let info = Level.info(totalXp: 60)
        XCTAssertEqual(info.currentLevelXp, 0)
        XCTAssertEqual(info.xpForNextLevel, Level.xpForSpan(2)) // 95
        XCTAssertGreaterThanOrEqual(info.progress, 0)
        XCTAssertLessThanOrEqual(info.progress, 1)
    }

    func testXpForAnswer() {
        // practice, combo 1, ms 1000 → 10 + 0 + 5(속도) + 0 = 15
        XCTAssertEqual(Level.xpForAnswer(mode: .practice, combo: 1, ms: 1000), 15)
        // combo 3 → comboBonus min(15, 1*2)=2 → 10+2+5 = 17
        XCTAssertEqual(Level.xpForAnswer(mode: .practice, combo: 3, ms: 1000), 17)
        // missing 모드 xpBonus 6 추가
        XCTAssertEqual(Level.xpForAnswer(mode: .missing, combo: 1, ms: 1000), 21)
        // 느린 답(4000ms) → 속도 보너스 0
        XCTAssertEqual(Level.xpForAnswer(mode: .practice, combo: 1, ms: 4000), 10)
    }

    // MARK: - Commit (세션 반영)

    func testApplySessionBasic() {
        let state = Commit.defaultState
        let result = SessionResult(mode: .practice, table: 2,
            answers: [AnswerRecord(a: 2, b: 3, correct: true, ms: 1000)],
            maxCombo: 1, durationMs: 1000)
        let (next, commit) = Commit.applySession(state, result: result)
        XCTAssertEqual(commit.xpEarned, 15)
        XCTAssertEqual(next.totalCorrect, 1)
        XCTAssertEqual(next.totalWrong, 0)
        XCTAssertEqual(next.dailyCorrect, 1)
        XCTAssertTrue(commit.unlocked.contains("first_correct"))
        // 정답 1개(정확도 1.0, 1000ms) → 3별
        XCTAssertEqual(commit.newStars, 3)
        XCTAssertEqual(next.tableMastery[2]?.stars, 3)
    }

    func testApplySessionScoredMode() {
        let state = Commit.defaultState
        let answers = (0..<5).map { _ in AnswerRecord(a: 3, b: 4, correct: true, ms: 800) }
        let result = SessionResult(mode: .challenge, table: nil, answers: answers, maxCombo: 5, durationMs: 60000)
        let (next, commit) = Commit.applySession(state, result: result)
        XCTAssertEqual(commit.score, 5)
        XCTAssertTrue(commit.isNewBest)
        XCTAssertEqual(next.bestScores[.challenge], 5)
    }

    /// 방문만으로는 스트릭이 오르지 않는다 (학습 자격은 applySession 이 부여)
    func testApplyVisitDoesNotTouchStreak() {
        let today = Date()
        var s = Commit.defaultState
        s.lastPlayedDate = Commit.todayStr(Calendar.current.date(byAdding: .day, value: -1, to: today)!)
        s.streak = 4
        s.dailyDate = Commit.todayStr(Calendar.current.date(byAdding: .day, value: -1, to: today)!)
        s.dailyCorrect = 7
        let visited = Commit.applyVisit(s, now: today)
        XCTAssertEqual(visited.streak, 4)                       // 소급 증가 없음
        XCTAssertEqual(visited.lastPlayedDate, s.lastPlayedDate) // 소급 삭감 없음
        XCTAssertEqual(visited.dailyDate, Commit.todayStr(today))
        XCTAssertEqual(visited.dailyCorrect, 0)                  // 날짜 경계에서 일일 정답 초기화
    }

    /// 세션을 한 판 끝내면 스트릭 자격 — 어제 학습했으면 +1, 끊겼으면 1로 리셋
    func testQualifyStreakOnSessionComplete() {
        let today = Date()
        let answers = [AnswerRecord(a: 2, b: 3, correct: true, ms: 900)]
        let result = SessionResult(mode: .practice, table: 2, answers: answers, maxCombo: 1, durationMs: 1000)

        var s = Commit.defaultState
        s.lastPlayedDate = Commit.todayStr(Calendar.current.date(byAdding: .day, value: -1, to: today)!)
        s.streak = 4
        XCTAssertEqual(Commit.applySession(s, result: result, now: today).next.streak, 5)

        var s2 = Commit.defaultState
        s2.lastPlayedDate = Commit.todayStr(Calendar.current.date(byAdding: .day, value: -2, to: today)!)
        s2.streak = 9
        XCTAssertEqual(Commit.applySession(s2, result: result, now: today).next.streak, 1)
    }

    /// 부분 커밋은 한 판 완료 통계를 건드리지 않는다. 단 일일 정답 하한을 넘기면 스트릭 자격은 준다.
    func testPartialCommitSkipsCompletionStats() {
        let today = Date()
        var s = Commit.defaultState
        // dailyDate 를 오늘로 맞춰야 날짜 경계 초기화에 걸리지 않는다. streakFloor = min(20, 10) = 10
        s.dailyGoal = 20
        s.dailyDate = Commit.todayStr(today)
        let answers = (0..<3).map { _ in AnswerRecord(a: 3, b: 4, correct: true, ms: 800) }
        let partial = SessionResult(mode: .practice, table: 3, answers: answers,
                                    maxCombo: 3, durationMs: 3000, partial: true)
        let (next, commit) = Commit.applySession(s, result: partial, now: today)

        XCTAssertTrue(commit.partial)
        XCTAssertGreaterThan(commit.xpEarned, 0)          // XP 는 반영
        XCTAssertEqual(next.dailyCorrect, 3)              // 일일 정답도 반영
        XCTAssertEqual(next.wrongPool.isEmpty, true)
        XCTAssertTrue(next.recentAccuracy.isEmpty)        // 추이 미반영
        XCTAssertNil(next.tableMastery[3])                // 별점 미반영
        XCTAssertTrue(next.modesPlayed.isEmpty)           // 모드 탐험 미반영
        XCTAssertEqual(next.streak, 0)                    // 하한(10) 미달 → 자격 없음

        // 하한 도달 시엔 부분 커밋도 스트릭 자격
        s.dailyCorrect = 9
        let (next2, _) = Commit.applySession(s, result: partial, now: today)
        XCTAssertEqual(next2.streak, 1)
        XCTAssertEqual(next2.lastPlayedDate, Commit.todayStr(today))
    }

    /// XP 배율(xpScale) — 0.3배면 XP 도 그만큼 줄어든다 (예전 어드벤처 재대결이 쓰던 값)
    func testRematchXpScale() {
        let xpScale = 0.3
        let answers = (0..<4).map { _ in AnswerRecord(a: 6, b: 7, correct: true, ms: 1200) }
        let full = SessionResult(mode: .practice, table: 6, answers: answers, maxCombo: 4, durationMs: 5000)
        let scaled = SessionResult(mode: .practice, table: 6, answers: answers, maxCombo: 4,
                                   durationMs: 5000, xpScale: xpScale)
        let base = Commit.applySession(Commit.defaultState, result: full).commit.xpEarned
        let cut = Commit.applySession(Commit.defaultState, result: scaled).commit.xpEarned
        XCTAssertGreaterThan(base, cut)
        // 문항별 반올림 누적 오차를 감안해 ±문항 수 범위로 검증
        XCTAssertLessThanOrEqual(abs(cut - Int((Double(base) * xpScale).rounded())), answers.count)
    }

    /// 날짜별 링버퍼 — 같은 날은 한 버킷에 누적, 오답은 식별 키로 집계
    func testDayLogAccumulates() {
        let today = Date()
        let answers = [
            AnswerRecord(a: 2, b: 3, correct: true, ms: 1000),
            AnswerRecord(a: 4, b: 5, correct: false, ms: 2000),
        ]
        let result = SessionResult(mode: .practice, table: nil, answers: answers, maxCombo: 1, durationMs: 3000)
        let (first, _) = Commit.applySession(Commit.defaultState, result: result, now: today)
        let (second, _) = Commit.applySession(first, result: result, now: today)

        let log = second.dayLog ?? []
        XCTAssertEqual(log.count, 1)
        XCTAssertEqual(log[0].date, Commit.todayStr(today))
        XCTAssertEqual(log[0].correct, 2)
        XCTAssertEqual(log[0].wrong, 2)
        XCTAssertEqual(log[0].misses["4x5"], 2)
        XCTAssertEqual(log[0].msSum, 6000)
        // 단별 합계도 같은 날 두 판을 더한다
        XCTAssertEqual(log[0].tables, [2: TableTally(correct: 2, wrong: 0, msSum: 2000),
                                       4: TableTally(correct: 0, wrong: 2, msSum: 4000)])
    }

    /// 단별 합계 — 세션·게임·중도 이탈 부분 기록 모두 답의 a(단)로 나뉘어 쌓이고, 합은 그날 합계와 같다
    func testDayLogTalliesEveryRoundByTable() throws {
        let now = Date()
        let session = SessionResult(mode: .practice, table: 7, answers: [
            AnswerRecord(a: 7, b: 3, correct: true, ms: 1000),
            AnswerRecord(a: 7, b: 8, correct: false, ms: 2000),
        ], maxCombo: 1, durationMs: 3000)
        // 구구 계단 섞어서 — 답마다 단이 다르다(단 × 칸)
        let stairs = SessionResult(mode: .stairs, table: nil, answers: [
            AnswerRecord(a: 3, b: 4, correct: true, ms: 500, given: .number(12)),
            AnswerRecord(a: 7, b: 2, correct: true, ms: 700, given: .number(14)),
        ], maxCombo: 2, durationMs: 9000)
        // 구구 몬스터 판 도중 나감 — 부분 기록도 쌓인다
        let monsterQuit = SessionResult(mode: .monster, table: nil, answers: [
            AnswerRecord(a: 6, b: 7, correct: false, ms: 900, given: .number(48)),
        ], maxCombo: 0, durationMs: 4000, partial: true)

        var s = Commit.defaultState
        for result in [session, stairs, monsterQuit] { s = Commit.applySession(s, result: result, now: now).next }

        let day = try XCTUnwrap(s.dayLog?.first)
        XCTAssertEqual(day.tables, [
            7: TableTally(correct: 2, wrong: 1, msSum: 3700),
            3: TableTally(correct: 1, wrong: 0, msSum: 500),
            6: TableTally(correct: 0, wrong: 1, msSum: 900),
        ])
        let tallies = Array(try XCTUnwrap(day.tables).values)
        XCTAssertEqual(tallies.map(\.correct).reduce(0, +), day.correct)
        XCTAssertEqual(tallies.map(\.wrong).reduce(0, +), day.wrong)
        XCTAssertEqual(tallies.map(\.msSum).reduce(0, +), day.msSum)
    }

    /// 같은 날짜가 둘 이상인 저장본은 날짜별로 합칠 때 단별 합계도 더한다. 한쪽만 합계가 있으면(구버전 날) 그쪽을 쓴다
    func testDayLogByDateMergesTables() {
        let log = [
            DayLogEntry(date: "2026-11-24", correct: 2, wrong: 1, msSum: 300, misses: ["7x8": 1],
                        tables: [7: TableTally(correct: 2, wrong: 1, msSum: 300)]),
            DayLogEntry(date: "2026-11-24", correct: 1, wrong: 1, msSum: 200, misses: ["7x8": 1],
                        tables: [7: TableTally(correct: 0, wrong: 1, msSum: 120), 3: TableTally(correct: 1, wrong: 0, msSum: 80)]),
            DayLogEntry(date: "2026-11-23", correct: 4, wrong: 0, msSum: 400, misses: [:]),
            DayLogEntry(date: "2026-11-23", correct: 1, wrong: 0, msSum: 90, misses: [:],
                        tables: [5: TableTally(correct: 1, wrong: 0, msSum: 90)]),
        ]
        let byDate = Commit.dayLogByDate(log)
        XCTAssertEqual(byDate["2026-11-24"]?.correct, 3)
        XCTAssertEqual(byDate["2026-11-24"]?.misses, ["7x8": 2])
        XCTAssertEqual(byDate["2026-11-24"]?.tables, [7: TableTally(correct: 2, wrong: 2, msSum: 420),
                                                      3: TableTally(correct: 1, wrong: 0, msSum: 80)])
        XCTAssertEqual(byDate["2026-11-23"]?.correct, 5)
        XCTAssertEqual(byDate["2026-11-23"]?.tables, [5: TableTally(correct: 1, wrong: 0, msSum: 90)])
        XCTAssertTrue(Commit.dayLogByDate(nil).isEmpty)
    }

    /// 구버전 저장본(dayLog 키 없음)도 디코딩되고, 방문 시 빈 배열로 정리된다
    func testDayLogMigrationFromLegacyPayload() throws {
        let legacy = #"{"version":1,"totalXp":120,"streak":3,"lastPlayedDate":"2026-01-01","dailyGoal":20,"dailyDate":"2026-01-01","dailyCorrect":5,"tableMastery":{},"unlockedAchievements":[],"totalCorrect":10,"totalWrong":2,"maxCombo":4,"recentAccuracy":[90],"recentAvgMs":[1200],"wrongPool":{"2x3":2},"bestScores":[],"modesPlayed":["practice"],"onboarded":true}"#
        let decoded = try JSONDecoder().decode(GameState.self, from: Data(legacy.utf8))
        XCTAssertNil(decoded.dayLog)
        XCTAssertEqual(decoded.totalXp, 120)
        XCTAssertEqual(decoded.streak, 3)   // 구기록 보존
        let visited = Commit.applyVisit(decoded)
        XCTAssertEqual(visited.dayLog, [])
        XCTAssertEqual(visited.streak, 3)
    }

    // MARK: - Achievements

    /// 모드 탐험가 — 학습 탭 모드 전부를 플레이해야 해금. adventure 는 조건에 포함되지 않는다.
    func testModeExplorerRequiresEveryLearningMode() {
        var s = Commit.defaultState
        s.modesPlayed = [.practice, .timeAttack, .challenge, .survival, .missing, .adventure]
        XCTAssertFalse(Achievements.newlyUnlocked(s).contains("mode_explorer"))  // truefalse 미플레이

        s.modesPlayed = Modes.list.map { $0.id }
        XCTAssertTrue(Achievements.newlyUnlocked(s).contains("mode_explorer"))
    }

    // MARK: - Problems

    func testUpdateWrongPool() {
        var pool: [String: Int] = [:]
        pool = Problems.updateWrongPool(pool, a: 2, b: 3, correct: false) // +2
        XCTAssertEqual(pool["2x3"], 2)
        pool = Problems.updateWrongPool(pool, a: 2, b: 3, correct: false) // +2 → 4
        XCTAssertEqual(pool["2x3"], 4)
        pool = Problems.updateWrongPool(pool, a: 2, b: 3, correct: true)  // -1 → 3
        XCTAssertEqual(pool["2x3"], 3)
        // cap 6
        pool["2x3"] = 6
        pool = Problems.updateWrongPool(pool, a: 2, b: 3, correct: false)
        XCTAssertEqual(pool["2x3"], 6)
    }

    /// 오답 가중치가 가장 많이 쌓인 단 — 범위 밖/깨진 키는 무시
    func testDominantWrongTable() {
        XCTAssertNil(Problems.dominantWrongTable([:]))
        XCTAssertEqual(Problems.dominantWrongTable(["2x3": 2, "7x8": 4, "7x2": 2]), 7)
        XCTAssertNil(Problems.dominantWrongTable(["x3": 5, "99x9": 6, "3x4": 0]))
    }

    /// 먹구름이 뜨는 단 — 오답 가중치가 남은 문제가 있는 단. 0·형식 오류·범위 밖 키는 무시
    func testTablesWithWrong() {
        XCTAssertEqual(Problems.tablesWithWrong([:]), [])
        XCTAssertEqual(Problems.tablesWithWrong(["7x8": 2, "7x3": 1, "8x9": 0, "bad": 3, "1x5": 2, "19x19": 1, "3x20": 4]),
                       [7, 19])
    }

    func testPickDeterministicWithInjectedRandom() {
        // random()=0 → 첫 후보 (2단이면 2x1)
        let p = Problems.pick(table: 2, wrongPool: [:], recentKeys: [], random: { 0 })
        XCTAssertEqual(p.a, 2)
        XCTAssertEqual(p.b, 1)
    }

    func testMakeStatementTrueBranch() {
        // random()=0 (<0.5) → 참인 식
        let st = Problems.makeStatement(Problem(a: 3, b: 4), random: { 0 })
        XCTAssertTrue(st.isTrue)
        XCTAssertEqual(st.shown, 12)
    }

    func testSessionAnswerSlotDigitsStayAtAnswerWidth() {
        let twoDigit = Problem(a: 9, b: 9)
        XCTAssertEqual(sessionAnswerSlotDigits(problem: twoDigit, mode: .practice, input: ""), 2)
        XCTAssertEqual(sessionAnswerSlotDigits(problem: twoDigit, mode: .practice, input: "8"), 2)
        XCTAssertEqual(sessionAnswerSlotDigits(problem: twoDigit, mode: .practice, input: "81"), 2)
        XCTAssertEqual(sessionAnswerSlotDigits(problem: twoDigit, mode: .practice, input: "100"), 3)

        let missing = Problem(a: 9, b: 9)
        XCTAssertEqual(sessionAnswerSlotDigits(problem: missing, mode: .missing, input: ""), 1)
    }

    // MARK: - 영속화 라운드트립

    func testGameStateCodableRoundTrip() throws {
        var s = Commit.defaultState
        s.totalXp = 500
        s.tableMastery[2] = TableMastery(stars: 3, bestAccuracy: 1, bestAvgMs: 900, plays: 4)
        s.bestScores[.challenge] = 15
        s.modesPlayed = [.practice, .challenge]
        s.wrongPool = ["2x3": 4]
        s.dayLog = [DayLogEntry(date: "2026-09-18", correct: 8, wrong: 2, msSum: 12000, misses: ["4x5": 2],
                                tables: [4: TableTally(correct: 8, wrong: 2, msSum: 12000)]),
                    DayLogEntry(date: "2026-09-19", correct: 1, wrong: 0, msSum: 900, misses: [:])]
        let data = try JSONEncoder().encode(s)
        let decoded = try JSONDecoder().decode(GameState.self, from: data)
        XCTAssertEqual(decoded, s)
    }

    /// 필드가 일부만 있는 저장본도 디코딩된다 — 나머지는 기본값 (합성 디코더는 여기서 실패해 진행도가 초기화됐다)
    func testGameStateDecodesPartialPayloadWithDefaults() throws {
        let decoded = try JSONDecoder().decode(GameState.self, from: Data(#"{"totalXp":7}"#.utf8))
        XCTAssertEqual(decoded.totalXp, 7)
        XCTAssertEqual(decoded.dailyGoal, 20)
        XCTAssertEqual(decoded.streak, 0)
        XCTAssertFalse(decoded.onboarded)
        XCTAssertNil(decoded.dayLog)
    }

    /// 타입이 틀린 값은 기본값으로 덮지 않고 실패시킨다 — 손상은 Persistence 가 원본 격리로 표면화한다
    func testGameStateRejectsWrongTypedField() {
        XCTAssertThrowsError(try JSONDecoder().decode(GameState.self, from: Data(#"{"totalXp":"abc"}"#.utf8)))
    }

    /// 모든 필드를 기본값과 다르게 채워 라운드트립 — 커스텀 디코더에서 필드가 빠지면 여기서 걸린다
    func testGameStateRoundTripPreservesEveryField() throws {
        let s = GameState(
            version: 2, totalXp: 321, streak: 4, lastPlayedDate: "2026-09-28", dailyGoal: 30,
            dailyDate: "2026-09-29", dailyCorrect: 12,
            tableMastery: [3: TableMastery(stars: 2, bestAccuracy: 0.9, bestAvgMs: 1500, plays: 3)],
            unlockedAchievements: ["first_step"], totalCorrect: 100, totalWrong: 7, maxCombo: 9,
            recentAccuracy: [80, 90], recentAvgMs: [1400, 1300], wrongPool: ["6x7": 2],
            bestScores: [.challenge: 12], modesPlayed: [.practice, .truefalse], onboarded: true,
            dayLog: [DayLogEntry(date: "2026-09-29", correct: 5, wrong: 1, msSum: 7000, misses: ["6x7": 1],
                                 tables: [6: TableTally(correct: 3, wrong: 1, msSum: 4000),
                                          9: TableTally(correct: 2, wrong: 0, msSum: 3000)])])
        let data = try JSONEncoder().encode(s)

        // 픽스처가 모든 필드를 기본값과 다르게 채웠는지 확인 — 필드를 추가하고 픽스처를 안 고치면 여기서 걸린다
        let filled = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? NSDictionary)
        let blank = try XCTUnwrap(
            JSONSerialization.jsonObject(with: JSONEncoder().encode(GameState())) as? NSDictionary)
        for (key, blankValue) in blank {
            XCTAssertNotEqual(filled[key] as? NSObject, blankValue as? NSObject,
                              "필드 \(key) 가 기본값과 같다 — 픽스처를 채워 주세요")
        }
        XCTAssertNotNil(filled["dayLog"])
        // 날짜 기록 항목도 모든 필드가 저장된다 — DayLogEntry 에 필드를 더하면 픽스처도 채울 것
        let day = try XCTUnwrap((filled["dayLog"] as? [NSDictionary])?.first)
        XCTAssertEqual(Set(day.allKeys.compactMap { $0 as? String }), ["date", "correct", "wrong", "msSum", "misses", "tables"])

        XCTAssertEqual(try JSONDecoder().decode(GameState.self, from: data), s)
    }

    // 손상 저장본 격리 (Persistence) — 테스트 키는 고유하게 만들고, 끝나면 저장본·격리 파일을 정리한다

    private func makeQuarantineTestKey() -> String {
        let key = "gugu.test.quarantine.\(UUID().uuidString)"
        addTeardownBlock {
            UserDefaults.standard.removeObject(forKey: key)
            Persistence.clearQuarantine(key: key)
        }
        return key
    }

    /// 못 읽는 저장본은 nil 로 돌려주되, 덮어쓰기 전에 원본 바이트를 격리 폴더에 그대로 남긴다
    func testLoadQuarantinesUndecodablePayload() throws {
        let key = makeQuarantineTestKey()
        let corrupt = Data("{손상된 저장본".utf8)
        UserDefaults.standard.set(corrupt, forKey: key)

        XCTAssertNil(Persistence.load(GameState.self, key: key))

        let files = Persistence.quarantinedFiles(key: key)
        XCTAssertEqual(files.count, 1)
        XCTAssertEqual(try Data(contentsOf: XCTUnwrap(files.first)), corrupt)
        // 원본 키는 건드리지 않는다 — 덮어쓰기는 호출부가 (격리 뒤에) 한다
        XCTAssertEqual(UserDefaults.standard.data(forKey: key), corrupt)
        // 백업에서 제외돼 있다 — 손상 원문(학습 기록·식별자)이 iCloud/iTunes 백업으로 새 기기에 복원되지 않게
        let values = try Persistence.quarantineDirectory.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true)
    }

    /// 키가 없는 것(신규 설치)은 손상이 아니다 — 격리하지 않는다
    func testLoadDoesNotQuarantineMissingKey() {
        let key = makeQuarantineTestKey()
        XCTAssertNil(Persistence.load(GameState.self, key: key))
        XCTAssertTrue(Persistence.quarantinedFiles(key: key).isEmpty)
    }

    /// 실패가 반복돼도 키당 3개까지만 남기고, 앞선(원본에 가까운) 것을 보존한다
    func testQuarantineKeepsFirstThreeOnly() throws {
        let key = makeQuarantineTestKey()
        for i in 1...4 {
            UserDefaults.standard.set(Data("corrupt-\(i)".utf8), forKey: key)
            XCTAssertNil(Persistence.load(GameState.self, key: key))
        }
        let kept = try Persistence.quarantinedFiles(key: key)
            .map { String(decoding: try Data(contentsOf: $0), as: UTF8.self) }
            .sorted()
        XCTAssertEqual(kept, ["corrupt-1", "corrupt-2", "corrupt-3"])
    }

    /// 보존 기한(30일)이 지난 격리본만 지운다 — 기한 안쪽은 남긴다 (경계: 29일 23시간은 남고 31일은 지워진다)
    func testPurgeExpiredQuarantineRemovesOnlyOldFiles() throws {
        let key = makeQuarantineTestKey()
        for raw in ["expired", "edge", "recent"] {
            UserDefaults.standard.set(Data(raw.utf8), forKey: key)
            XCTAssertNil(Persistence.load(GameState.self, key: key))
        }
        let now = Date()
        let hour: TimeInterval = 60 * 60
        let modified: [String: Date] = [
            "expired": now.addingTimeInterval(-31 * 24 * hour),
            "edge": now.addingTimeInterval(-(29 * 24 + 23) * hour),
            "recent": now.addingTimeInterval(-24 * hour),
        ]
        let files = Persistence.quarantinedFiles(key: key)
        XCTAssertEqual(files.count, 3)
        for file in files {
            let raw = try String(contentsOf: file, encoding: .utf8)
            try FileManager.default.setAttributes([.modificationDate: try XCTUnwrap(modified[raw])],
                                                  ofItemAtPath: file.path)
        }

        Persistence.purgeExpiredQuarantine(now: now)

        let kept = try Persistence.quarantinedFiles(key: key)
            .map { try String(contentsOf: $0, encoding: .utf8) }
            .sorted()
        XCTAssertEqual(kept, ["edge", "recent"])
    }

    /// 방금 격리한 파일은 기본 보존 기한(30일)으로 정리해도 남는다
    func testPurgeExpiredQuarantineKeepsFreshFiles() {
        let key = makeQuarantineTestKey()
        UserDefaults.standard.set(Data("fresh".utf8), forKey: key)
        XCTAssertNil(Persistence.load(GameState.self, key: key))

        Persistence.purgeExpiredQuarantine()

        XCTAssertEqual(Persistence.quarantinedFiles(key: key).count, 1)
    }

    /// 의도적 삭제(remove)와 기록 초기화는 격리 사본도 지운다 — 다른 키의 격리본은 그대로
    func testRemoveAndClearDropQuarantineForThatKeyOnly() {
        let keyA = makeQuarantineTestKey()
        let keyB = makeQuarantineTestKey()
        for key in [keyA, keyB] {
            UserDefaults.standard.set(Data("corrupt".utf8), forKey: key)
            _ = Persistence.load(GameState.self, key: key)
        }
        XCTAssertEqual(Persistence.quarantinedFiles(key: keyA).count, 1)
        XCTAssertEqual(Persistence.quarantinedFiles(key: keyB).count, 1)

        Persistence.remove(keyA)
        XCTAssertTrue(Persistence.quarantinedFiles(key: keyA).isEmpty)
        XCTAssertEqual(Persistence.quarantinedFiles(key: keyB).count, 1)

        Persistence.clearQuarantine(key: keyB)
        XCTAssertTrue(Persistence.quarantinedFiles(key: keyB).isEmpty)
    }
}
