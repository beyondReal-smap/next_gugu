import XCTest
@testable import Gugu

/// P1 Core 도메인 로직 검증 — TS 원본(lib/)과의 기능 패리티 확인
final class CoreTests: XCTestCase {

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

    /// 재대결 XP 배율 — 원본 XP의 0.3배로 체감
    func testRematchXpScale() {
        let answers = (0..<4).map { _ in AnswerRecord(a: 6, b: 7, correct: true, ms: 1200) }
        let full = SessionResult(mode: .adventure, table: 6, answers: answers, maxCombo: 4, durationMs: 5000)
        let scaled = SessionResult(mode: .adventure, table: 6, answers: answers, maxCombo: 4,
                                   durationMs: 5000, xpScale: Battle.rematchXpScale)
        let base = Commit.applySession(Commit.defaultState, result: full).commit.xpEarned
        let cut = Commit.applySession(Commit.defaultState, result: scaled).commit.xpEarned
        XCTAssertGreaterThan(base, cut)
        // 문항별 반올림 누적 오차를 감안해 ±문항 수 범위로 검증
        XCTAssertLessThanOrEqual(abs(cut - Int((Double(base) * Battle.rematchXpScale).rounded())), answers.count)
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

    // MARK: - Adventure

    func testRegionsStructure() {
        XCTAssertEqual(World.regions.count, 8)
        for r in World.regions {
            XCTAssertEqual(r.npcs.count, 4) // 일반 3 + 보스 1
            XCTAssertEqual(r.npcs.filter { $0.kind == .boss }.count, 1)
            // 소품 12~16개
            XCTAssertGreaterThanOrEqual(r.decoItems.count, 12)
            XCTAssertLessThanOrEqual(r.decoItems.count, 16)
        }
        XCTAssertEqual(World.regions.map { $0.table }, Array(2...9))
    }

    func testRegionUnlockChain() {
        var p = AdvProgress.defaultProgress
        XCTAssertTrue(AdvProgress.isRegionUnlocked(p, table: 2))  // 첫 지역 항상 열림
        XCTAssertFalse(AdvProgress.isRegionUnlocked(p, table: 3)) // 2단 보스 미격파
        p.defeatedNpcs.append(World.bossId(for: 2))
        XCTAssertTrue(AdvProgress.isRegionUnlocked(p, table: 3))  // 2단 보스 격파 → 3단 해금
    }

    /// 로드맵(2,5,3,4,6,9,7,8) 직전 보스 격파 또는 해당 단 별 1개로도 해금된다
    func testRegionUnlockByRoadmapOrStars() {
        var p = AdvProgress.defaultProgress
        XCTAssertFalse(AdvProgress.isRegionUnlocked(p, table: 5))
        // 로드맵상 5단의 직전은 2단 → 2단 보스 격파로 5단 해금
        p.defeatedNpcs.append(World.bossId(for: 2))
        XCTAssertTrue(AdvProgress.isRegionUnlocked(p, table: 5))

        // 별점만으로도 해금 (직전 보스 미격파여도)
        let fresh = AdvProgress.defaultProgress
        XCTAssertFalse(AdvProgress.isRegionUnlocked(fresh, table: 8))
        XCTAssertTrue(AdvProgress.isRegionUnlocked(fresh, table: 8, tableStars: 1))

        XCTAssertEqual(AdvProgress.roadmapPrevTable(5), 2)
        XCTAssertNil(AdvProgress.roadmapPrevTable(2))
    }

    /// 배틀 커밋은 adventure 모드로 들어가 학습 모드 기록과 섞이지 않는다
    func testBattleCommitsAsAdventureMode() {
        let npc = World.regions[0].npcs[0]
        let result = Battle.toSessionResult(npc, answers: [], maxCombo: 0, durationMs: 1000)
        XCTAssertEqual(result.mode, .adventure)
        XCTAssertFalse(Modes.def(.adventure).scored)
        XCTAssertFalse(Modes.list.contains { $0.id == .adventure })  // 학습 탭 목록엔 없음
    }

    func testApplyBattle() {
        let p = AdvProgress.defaultProgress
        let (next, unlocked) = AdvProgress.applyBattle(p, npcId: "r2-n1", won: true)
        XCTAssertEqual(next.battlesWon, 1)
        XCTAssertTrue(next.defeatedNpcs.contains("r2-n1"))
        XCTAssertTrue(unlocked.contains("adv_first_win"))
        // 중복 격파는 목록에 다시 추가되지 않음
        let (again, _) = AdvProgress.applyBattle(next, npcId: "r2-n1", won: true)
        XCTAssertEqual(again.defeatedNpcs.filter { $0 == "r2-n1" }.count, 1)
    }

    func testBattleDamage() {
        // 빠른 답(1000ms) + 콤보 3 → 20 + 8 + min(10,2*2)=4 = 32
        XCTAssertEqual(Battle.damage(ms: 1000, combo: 3), 32)
        // 느린 답 콤보 1 → 20 + 0 + 0
        XCTAssertEqual(Battle.damage(ms: 5000, combo: 1), 20)
    }

    // MARK: - Runner (구구 점프)

    /// 보기는 정답 1개 + 서로 다른 오답 2개, 모두 1...81 범위
    func testRunnerQuestionChoices() {
        for _ in 0..<50 {
            let q = Runner.question(table: nil, recentKeys: [])
            XCTAssertEqual(q.choices.count, 3)
            XCTAssertEqual(Set(q.choices).count, 3)
            XCTAssertTrue(q.choices.contains(q.answer))
            for c in q.choices { XCTAssertTrue(c >= 1 && c <= 81, "보기 범위 이탈: \(c)") }
        }
        for _ in 0..<20 { XCTAssertEqual(Runner.question(table: 7, recentKeys: []).a, 7) }
    }

    /// 제한 시간 — 5개마다 450ms 감소, 하한 3200ms
    func testRunnerAnswerWindow() {
        XCTAssertEqual(Runner.answerWindowMs(score: 0), 5000)
        XCTAssertEqual(Runner.answerWindowMs(score: 3), 5000 - 500)      // 3개마다 한 단계
        XCTAssertEqual(Runner.answerWindowMs(score: 11), 5000 - 500 * 3)
        XCTAssertEqual(Runner.answerWindowMs(score: 200), 2800)
    }

    /// 정답/오답 판정 — 보기에 없는 값이나 이미 판정된 상태는 무시
    func testRunnerAnswerGuards() {
        let s = Runner.create(table: 3)
        let correct = Runner.answer(s, choice: s.question.answer)
        XCTAssertEqual(correct.outcome, .correct)
        XCTAssertEqual(correct.given, s.question.answer)

        let wrongChoice = s.question.choices.first { $0 != s.question.answer }!
        XCTAssertEqual(Runner.answer(s, choice: wrongChoice).outcome, .wrong)

        let absent = (1...81).first { !s.question.choices.contains($0) }!
        XCTAssertNil(Runner.answer(s, choice: absent).outcome)
        XCTAssertEqual(Runner.answer(correct, choice: wrongChoice).outcome, .correct)

        let ready = Runner.create(table: 3, phase: .ready)
        XCTAssertNil(Runner.answer(ready, choice: ready.question.choices[0]).outcome)
    }

    /// 시간 초과 → 하트 1개 감소 + 피격 연출, 콤보 초기화
    func testRunnerMissedCostsLife() {
        var s = Runner.create(table: 2)
        s.combo = 4
        s = Runner.advance(s, dt: Runner.answerWindowMs(score: 0))
        XCTAssertEqual(s.outcome, .missed)
        XCTAssertEqual(s.lives, Runner.maxLives - 1)
        XCTAssertEqual(s.combo, 0)
        XCTAssertEqual(s.obstacleX, Runner.collisionX)
        XCTAssertEqual(s.hitMs, Runner.hitDurationMs)

        s = Runner.advance(s, dt: Runner.hitDurationMs)
        XCTAssertNil(s.outcome)
        XCTAssertEqual(s.obstacleX, Runner.obstacleStart)
        XCTAssertEqual(s.round, 1)
    }

    /// 하트를 모두 잃고 연출이 끝나면 over
    func testRunnerGameOverAfterLastLife() {
        var s = Runner.create(table: 2)
        s.lives = 1
        s = Runner.advance(s, dt: Runner.answerWindowMs(score: 0))
        XCTAssertEqual(s.lives, 0)
        s = Runner.advance(s, dt: Runner.hitDurationMs)
        XCTAssertEqual(s.phase, .over)
    }

    /// 정답 후 장애물이 화면을 벗어나면 점수·콤보 증가 + 최고 콤보 갱신
    func testRunnerClearingObstacleScores() {
        let base = Runner.create(table: 2)
        var s = Runner.answer(base, choice: base.question.answer)
        XCTAssertEqual(s.outcome, .correct)
        var guardCount = 0
        while s.score == 0 && guardCount < 10_000 {
            s = Runner.advance(s, dt: 16)
            guardCount += 1
        }
        XCTAssertEqual(s.score, 1)
        XCTAssertEqual(s.combo, 1)
        XCTAssertEqual(s.maxCombo, 1)
        XCTAssertEqual(s.lives, Runner.maxLives)
        XCTAssertEqual(s.round, 1)
    }

    /// 점프 높이 — 정답일 때만, 구간 안에서만 솟는다
    func testRunnerJumpArc() {
        let base = Runner.create(table: 2)
        XCTAssertEqual(Runner.jump(base), 0)
        var correct = Runner.answer(base, choice: base.question.answer)

        correct.obstacleX = Runner.jumpStart + 1
        XCTAssertEqual(Runner.jump(correct), 0)
        correct.obstacleX = Runner.jumpEnd - 1
        XCTAssertEqual(Runner.jump(correct), 0)
        correct.obstacleX = (Runner.jumpStart + Runner.jumpEnd) / 2
        XCTAssertEqual(Runner.jump(correct), 96, accuracy: 0.001)

        var wrong = Runner.answer(base, choice: base.question.choices.first { $0 != base.question.answer }!)
        wrong.obstacleX = (Runner.jumpStart + Runner.jumpEnd) / 2
        XCTAssertEqual(Runner.jump(wrong), 0)
    }

    /// 일시정지/비정상 dt 는 상태를 바꾸지 않는다
    func testRunnerAdvanceGuards() {
        var paused = Runner.create(table: 2)
        paused.phase = .paused
        XCTAssertEqual(Runner.advance(paused, dt: 16).obstacleX, paused.obstacleX)
        let running = Runner.create(table: 2)
        XCTAssertEqual(Runner.advance(running, dt: 0).obstacleX, running.obstacleX)
        XCTAssertEqual(Runner.advance(running, dt: .nan).obstacleX, running.obstacleX)
    }

    // MARK: - 시드 난수 결정성

    func testMulberry32Deterministic() {
        let r1 = World.mulberry32(12345)
        let r2 = World.mulberry32(12345)
        for _ in 0..<50 {
            XCTAssertEqual(r1(), r2(), accuracy: 0)
        }
        // 소품 배치도 재현 가능
        let a = World.genDecoItems(table: 2, layout: World.layouts[2]!)
        let b = World.genDecoItems(table: 2, layout: World.layouts[2]!)
        XCTAssertEqual(a, b)
    }

    // MARK: - 영속화 라운드트립

    func testGameStateCodableRoundTrip() throws {
        var s = Commit.defaultState
        s.totalXp = 500
        s.tableMastery[2] = TableMastery(stars: 3, bestAccuracy: 1, bestAvgMs: 900, plays: 4)
        s.bestScores[.challenge] = 15
        s.modesPlayed = [.practice, .challenge]
        s.wrongPool = ["2x3": 4]
        s.dayLog = [DayLogEntry(date: "2026-09-18", correct: 8, wrong: 2, msSum: 12000, misses: ["4x5": 2])]
        let data = try JSONEncoder().encode(s)
        let decoded = try JSONDecoder().decode(GameState.self, from: data)
        XCTAssertEqual(decoded, s)
    }

    func testAdventureProgressCodableRoundTrip() throws {
        var p = AdvProgress.defaultProgress
        p.defeatedNpcs = ["r2-n1", "r2-boss"]
        p.battlesWon = 2
        p.achievements = ["adv_first_win"]
        p.starShards = 7
        p.ownedColors = ["rose"]
        p.ownedHats = ["sprout"]
        p.equippedColor = "rose"
        p.equippedHat = "sprout"
        let data = try JSONEncoder().encode(p)
        let decoded = try JSONDecoder().decode(AdventureProgress.self, from: data)
        XCTAssertEqual(decoded, p)
    }

    // 상점 카탈로그 무결성 — id 유일, 기본 색상 무료, 가격 양수
    func testShopCatalog() {
        XCTAssertEqual(Set(Shop.colors.map { $0.id }).count, Shop.colors.count)
        XCTAssertEqual(Set(Shop.hats.map { $0.id }).count, Shop.hats.count)
        let base = Shop.color(Shop.defaultColorID)
        XCTAssertNotNil(base)
        XCTAssertEqual(base?.price, 0)
        for c in Shop.colors where c.id != Shop.defaultColorID {
            XCTAssertGreaterThan(c.price, 0)
        }
        for h in Shop.hats {
            XCTAssertGreaterThan(h.price, 0)
        }
    }

    // 구버전 저장소(starShards 없던 시절) 디코딩 호환
    func testAdventureProgressDecodesLegacyStorage() throws {
        let legacy = #"{"version":1,"defeatedNpcs":["r2-n1"],"battlesWon":1,"battlesLost":0,"achievements":[]}"#
        let p = try JSONDecoder().decode(AdventureProgress.self, from: Data(legacy.utf8))
        XCTAssertEqual(p.defeatedNpcs, ["r2-n1"])
        XCTAssertEqual(p.starShards, 0)
    }

    // 별 조각 배치 — 지역당 5개, 결정적, NPC/스폰과 안전 거리
    func testStarSpots() {
        for r in World.regions {
            XCTAssertEqual(r.starSpots.count, 5, "\(r.name) 별 조각 수")
            for s in r.starSpots {
                XCTAssertLessThanOrEqual(abs(s.x), 13.5)
                XCTAssertLessThanOrEqual(abs(s.z), 13.5)
                for n in r.npcs {
                    XCTAssertGreaterThan(hypot(n.pos.x - s.x, n.pos.z - s.z), 2.0)
                }
            }
        }
        // 결정성 — 같은 시드는 같은 배치
        let a = World.genStarSpots(table: 2, layout: World.layouts[2]!)
        let b = World.genStarSpots(table: 2, layout: World.layouts[2]!)
        XCTAssertEqual(a, b)
    }

    // MARK: - LaneRunner (구구 레인)

    /// 속도 단계 — 정답 길 3개마다 한 단계, 각 구간 값은 하한에서 멈춘다
    func testLaneSpeedSteps() {
        XCTAssertEqual(LaneRunner.level(score: 2), 0)
        XCTAssertEqual(LaneRunner.level(score: 3), 1)
        XCTAssertEqual(LaneRunner.travelMs(score: 0), 3400)
        XCTAssertEqual(LaneRunner.travelMs(score: 3), 3400 - 250)
        XCTAssertEqual(LaneRunner.travelMs(score: 300), 1900)       // 하한 고정
        XCTAssertEqual(LaneRunner.quizWindowMs(score: 0), 5000)
        XCTAssertEqual(LaneRunner.quizWindowMs(score: 300), 3500)   // 하한 고정
        XCTAssertEqual(LaneRunner.spawnGapMs(score: 300), 750)      // 하한 고정
        XCTAssertEqual(LaneRunner.obstaclesPerCycle(score: 300), 4) // 상한 고정
    }

    /// 레인 이동 — 범위 밖/같은 레인/비 running 은 무시하고, 전환 중에는 보간 위치를 남긴다
    func testLaneMoveGuards() {
        let s = LaneRunner.create(table: 2)
        XCTAssertEqual(s.lane, 1)
        XCTAssertEqual(LaneRunner.setLane(s, lane: 1).lane, 1)      // 같은 레인
        XCTAssertEqual(LaneRunner.setLane(s, lane: 3).lane, 1)      // 범위 밖
        XCTAssertEqual(LaneRunner.move(LaneRunner.create(table: 2, phase: .ready), delta: -1).lane, 1)

        var up = LaneRunner.move(s, delta: -1)
        XCTAssertEqual(up.lane, 0)
        XCTAssertEqual(up.laneFrom, 1)
        XCTAssertEqual(up.laneAnimMs, LaneRunner.laneAnimMs)
        // 전환 직후에는 이전 레인에 가깝고, 애니메이션이 끝나면 새 레인에 정확히 놓인다
        XCTAssertEqual(LaneRunner.offset(up), 1, accuracy: 0.001)
        up.laneAnimMs = 0
        XCTAssertEqual(LaneRunner.offset(up), 0, accuracy: 0.001)
    }

    /// 내 레인을 막은 장애물은 하트를 깎고, 다른 레인 장애물은 피한 수로 센다
    func testLaneObstacleJudgement() {
        var base = LaneRunner.create(table: 2)
        base.obstacles = [
            LaneObstacle(id: 0, lane: 1, x: LaneRunner.judgeX + 1, kind: .rock),
            LaneObstacle(id: 1, lane: 0, x: LaneRunner.judgeX + 1, kind: .rock),
        ]
        let hit = LaneRunner.advance(base, dt: 16)
        XCTAssertEqual(hit.outcome, .hit)
        XCTAssertEqual(hit.lives, LaneRunner.maxLives - 1)
        XCTAssertEqual(hit.hitMs, LaneRunner.hitMs)
        // 부딪힌 열의 나머지 장애물은 피한 것으로 세지 않는다
        XCTAssertEqual(hit.dodged, 0)

        var away = base
        away.lane = 2
        let dodged = LaneRunner.advance(away, dt: 16)
        XCTAssertNil(dodged.outcome)
        XCTAssertEqual(dodged.dodged, 2)
        XCTAssertEqual(dodged.lives, LaneRunner.maxLives)
    }

    /// 게이트 판정 — 내 레인의 숫자가 정답이면 점수, 아니면 하트가 줄어든다
    func testLaneGateJudgement() {
        let question = RunnerQuestion(a: 3, b: 4, choices: [12, 10, 14])
        var base = LaneRunner.create(table: 3)
        base.segment = .quiz
        base.question = question
        base.gate = LaneGate(x: LaneRunner.judgeX + 1, startX: 600, values: question.choices)

        var onAnswer = base
        onAnswer.lane = 0
        let correct = LaneRunner.advance(onAnswer, dt: 16)
        XCTAssertEqual(correct.outcome, .correct)
        XCTAssertEqual(correct.score, 1)
        XCTAssertEqual(correct.combo, 1)
        XCTAssertEqual(correct.maxCombo, 1)
        XCTAssertEqual(correct.lives, LaneRunner.maxLives)
        XCTAssertEqual(correct.given, 12)

        var onWrong = base
        onWrong.lane = 1
        onWrong.combo = 2
        let wrong = LaneRunner.advance(onWrong, dt: 16)
        XCTAssertEqual(wrong.outcome, .wrong)
        XCTAssertEqual(wrong.score, 0)
        XCTAssertEqual(wrong.combo, 0)
        XCTAssertEqual(wrong.lives, LaneRunner.maxLives - 1)
        XCTAssertEqual(wrong.given, 10)
    }

    /// 속도 UP 판정 — 정답이고 누적 점수가 단계 배수일 때만
    func testLaneLeveledUp() {
        var s = LaneRunner.create(table: 2)
        s.outcome = .correct
        s.score = 0
        XCTAssertFalse(LaneRunner.leveledUp(s))
        s.score = 2
        XCTAssertFalse(LaneRunner.leveledUp(s))
        s.score = 3
        XCTAssertTrue(LaneRunner.leveledUp(s))
        s.outcome = .wrong
        XCTAssertFalse(LaneRunner.leveledUp(s))
    }

    /// 하트를 모두 잃고 연출이 끝나면 over, 일시정지·비정상 dt 는 무시
    func testLaneGameOverAndGuards() {
        var dying = LaneRunner.create(table: 2)
        dying.lives = 0
        dying.hitMs = 10
        dying.outcome = .hit
        XCTAssertEqual(LaneRunner.advance(dying, dt: 16).phase, .over)

        var paused = LaneRunner.create(table: 2)
        paused.phase = .paused
        XCTAssertEqual(LaneRunner.advance(paused, dt: 16).distance, paused.distance)
        let running = LaneRunner.create(table: 2)
        XCTAssertEqual(LaneRunner.advance(running, dt: 0).distance, running.distance)
        XCTAssertEqual(LaneRunner.advance(running, dt: .nan).distance, running.distance)
    }

    // MARK: - Basket (구구 바구니)

    /// 낙하 시간 — 정답 3개마다 한 단계 빨라지고 2.2초가 하한
    func testBasketFallSteps() {
        XCTAssertEqual(Basket.level(score: 0), 1)
        XCTAssertEqual(Basket.level(score: 3), 2)
        XCTAssertEqual(Basket.fallDurationMs(score: 0), 3000)
        XCTAssertEqual(Basket.fallDurationMs(score: 3), 2700)
        XCTAssertEqual(Basket.fallDurationMs(score: 300), 2200)   // 하한 고정
    }

    /// 바구니 이동 — 좌우 끝을 넘지 않고, 판정 중/비 running 은 무시
    func testBasketMoveGuards() {
        let s = Basket.create(table: 2)
        XCTAssertEqual(Basket.move(s, x: -100).x, Basket.minX)
        XCTAssertEqual(Basket.move(s, x: 9999).x, Basket.maxX)
        XCTAssertEqual(Basket.move(s, x: .nan).x, s.x)
        var judged = s
        judged.outcome = .correct
        XCTAssertEqual(Basket.move(judged, x: 100).x, s.x)
        XCTAssertEqual(Basket.move(Basket.create(table: 2, phase: .ready), x: 100).x, s.x)
    }

    /// 열매 판정 — 정답을 받으면 점수, 다른 열매는 오답, 아무것도 못 받으면 놓침
    func testBasketCatchJudgement() {
        let question = RunnerQuestion(a: 3, b: 4, choices: [12, 10, 14])
        let duration = Basket.fallDurationMs(score: 0)
        var base = Basket.create(table: 2)
        base.question = question
        base.elapsedMs = duration - 1

        var onAnswer = base
        onAnswer.x = Basket.fruitX(index: 0, elapsedMs: duration)
        let correct = Basket.advance(onAnswer, dt: 16)
        XCTAssertEqual(correct.outcome, .correct)
        XCTAssertEqual(correct.score, 1)
        XCTAssertEqual(correct.combo, 1)
        XCTAssertEqual(correct.lives, Basket.maxLives)
        XCTAssertEqual(correct.feedbackMs, Basket.correctFeedbackMs)

        var onWrong = base
        onWrong.x = Basket.fruitX(index: 1, elapsedMs: duration)
        onWrong.combo = 2
        let wrong = Basket.advance(onWrong, dt: 16)
        XCTAssertEqual(wrong.outcome, .wrong)
        XCTAssertEqual(wrong.score, 0)
        XCTAssertEqual(wrong.combo, 0)
        XCTAssertEqual(wrong.lives, Basket.maxLives - 1)

        // 어느 열매와도 겹치지 않는 열매 사이 — 놓침
        var onGap = base
        onGap.x = (Basket.fruitX(index: 0, elapsedMs: duration) + Basket.fruitX(index: 1, elapsedMs: duration)) / 2
        let missed = Basket.advance(onGap, dt: 16)
        XCTAssertEqual(missed.outcome, .missed)
        XCTAssertNil(missed.caughtIndex)
        XCTAssertEqual(missed.lives, Basket.maxLives - 1)
    }

    /// 연출이 끝나면 다음 문제로 넘어가고, 하트가 없으면 over
    func testBasketAdvancesRoundAndEnds() {
        var done = Basket.create(table: 2)
        done.outcome = .correct
        done.feedbackMs = 10
        done.elapsedMs = 500
        let next = Basket.advance(done, dt: 16)
        XCTAssertNil(next.outcome)
        XCTAssertEqual(next.round, 1)
        XCTAssertEqual(next.elapsedMs, 0)
        XCTAssertEqual(next.recentKeys.count, 1)

        var last = done
        last.lives = 0
        XCTAssertEqual(Basket.advance(last, dt: 16).phase, .over)
    }
}
