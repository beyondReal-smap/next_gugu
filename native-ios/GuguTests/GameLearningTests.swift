import XCTest
import SwiftUI
@testable import Gugu

/// 게임 엔진 테스트용 학습 기록 — GameStore.commitSession 과 같은 계산(Commit.applySession)을 메모리 상태에 적용한다.
/// (GameStore 는 실제 저장 키를 쓰므로 테스트 호스트 앱의 진행도를 건드리지 않게 상태만 따로 둔다)
final class LearningSpy {
    private(set) var state = Commit.defaultState
    private(set) var results: [SessionResult] = []
    private(set) var commits: [CommitResult] = []

    func commit(_ result: SessionResult) -> CommitResult {
        results.append(result)
        let (next, done) = Commit.applySession(state, result: result)
        state = next
        commits.append(done)
        return done
    }
}

/// 구구 계단·구구 몬스터 판 → 학습 기록(XP·오늘 목표·틀린 문제·서버 원장) 연결.
/// 판이 끝날 때(결과·체험 끝) 완료로 한 번, 판 중간에 나가면 부분으로 한 번, 답이 없으면 기록 없음.
final class GameLearningTests: XCTestCase {

    /// iPhone 17 Pro 크기·안전 영역(pt)
    private let viewport = GameViewport(size: CGSize(width: 402, height: 874),
                                        safeArea: EdgeInsets(top: 62, leading: 0, bottom: 34, trailing: 0))
    private let frame = 1.0 / 60

    // MARK: - 도우미

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

    private func stairs(_ learning: LearningSpy, trial: Int? = nil, start: MinigameStart = .table(2),
                        order: ProblemOrder = .sequential) -> StairsEngine {
        let engine = StairsEngine(start: start, order: order, topStep: 9, rewards: makeStore(), commit: learning.commit,
                                  random: SeededRandom.mulberry32(7), runsLoop: false)
        engine.setViewport(viewport)
        engine.start(trial: trial)
        return engine
    }

    private func monster(_ learning: LearningSpy, start: MinigameStart = .standard, trial: Int? = nil,
                         order: ProblemOrder = .random) -> MonsterEngine {
        let engine = MonsterEngine(start: start, order: order, rewards: makeStore(), commit: learning.commit,
                                   random: SeededRandom.mulberry32(7), runsLoop: false)
        engine.setViewport(viewport)
        engine.start(trial: trial)
        return engine
    }

    /// 계단 칸 가운데를 누르고 한 프레임 — 오답이면 정답 공개(0.6초)가 끝날 때까지 흘린다
    private func jump(_ engine: StairsEngine, correct: Bool) {
        let lane = correct ? engine.session.current.correctLane
                           : (engine.session.current.correctLane + 1) % StairsSession.lanes
        engine.enqueueTap(CGPoint(x: StairsCamera(size: viewport.size, centerY: 0).laneCenterX(lane), y: 600))
        engine.advance(frame)
        if !correct { run(sec: RoundClock.revealSec + 0.02) { engine.advance($0) } }
    }

    /// 몬스터 카드 두 장 — 정답 짝 또는 곱이 목표가 아닌 짝. 오답이면 공개가 끝날 때까지 흘린다
    private func pick(_ engine: MonsterEngine, correct: Bool) {
        let q = engine.session.current
        let second: Int
        if correct {
            second = q.pairB
        } else {
            guard let wrong = (0..<MonsterSession.cardCount).first(where: {
                $0 != q.pairA && q.cards[q.pairA] * q.cards[$0] != q.target.a * q.target.b
            }) else { return XCTFail("오답 카드가 없음 \(q.cards)") }
            second = wrong
        }
        engine.enqueueTap(MonsterEngine.Cards.center(q.pairA, viewport))
        engine.enqueueTap(MonsterEngine.Cards.center(second, viewport))
        engine.advance(frame)
        if !correct { run(sec: RoundClock.revealSec + 0.02) { engine.advance($0) } }
    }

    private func run(sec: Double, _ advance: (Double) -> Void) {
        for _ in 0..<Int((sec / frame).rounded(.up)) { advance(frame) }
    }

    /// 결과(또는 체험 종료)가 뜰 때까지 — 먹구름·60초 시계 포함, 상한으로 무한 반복 방지
    private func untilResult(_ hasResult: () -> Bool, _ advance: (Double) -> Void,
                             file: StaticString = #filePath, line: UInt = #line) {
        var i = 0
        while !hasResult() && i < 10_000 {
            advance(0.05)
            i += 1
        }
        XCTAssertTrue(hasResult(), "결과가 뜨지 않음", file: file, line: line)
    }

    // MARK: - 기록 한 번 규칙

    func testLogClosesOnceAndSkipsEmptyRounds() {
        let learning = LearningSpy()
        var log = RoundLearningLog(mode: .stairs, table: nil)
        XCTAssertNil(log.close(answers: [], maxCombo: 0, durationMs: 0, partial: true, using: learning.commit))
        XCTAssertTrue(log.closed)
        XCTAssertTrue(learning.results.isEmpty, "답이 없으면 기록하지 않는다")

        log.reset()
        let answers = [AnswerRecord(a: 3, b: 4, correct: true, ms: 900, given: .number(12))]
        XCTAssertNotNil(log.close(answers: answers, maxCombo: 1, durationMs: 900, partial: false, using: learning.commit))
        XCTAssertNotNil(log.close(answers: answers, maxCombo: 1, durationMs: 900, partial: true, using: learning.commit))
        XCTAssertEqual(learning.results.count, 1, "한 판에 한 번만")
        XCTAssertFalse(learning.results[0].partial)
    }

    // MARK: - 구구 계단

    /// 오답이 섞인 판이 먹구름에 잡혀 끝나면 결과를 띄우는 순간 완료로 한 번 기록 — XP·오늘 정답·틀린 문제가 오른다
    func testStairsRoundCommitsAtResult() throws {
        let learning = LearningSpy()
        let engine = stairs(learning)
        jump(engine, correct: true)
        jump(engine, correct: false)
        jump(engine, correct: true)
        let wrong = try XCTUnwrap(engine.session.attempts.first { !$0.correct })
        XCTAssertTrue(learning.results.isEmpty, "판 도중에는 기록하지 않는다")

        untilResult({ engine.result != nil }) { engine.advance($0) }
        XCTAssertTrue(engine.session.caught)

        XCTAssertEqual(learning.results.count, 1)
        let result = learning.results[0]
        XCTAssertEqual(result.mode, .stairs)
        XCTAssertNil(result.table, "계단은 판 중에 단이 바뀌어 단을 정하지 않는다")
        XCTAssertFalse(result.partial)
        XCTAssertEqual(result.answers, engine.answerRecords)
        XCTAssertEqual(result.answers.map(\.correct), [true, false, true])
        XCTAssertEqual(result.maxCombo, engine.session.bestStreak)
        XCTAssertEqual(result.durationMs, engine.roundDurationMs)

        let state = learning.state
        XCTAssertGreaterThan(state.totalXp, 0)
        XCTAssertEqual(state.dailyCorrect, 2)
        XCTAssertEqual(state.totalWrong, 1)
        // 틀린 칸은 같은 식으로 다시 나와 맞혔으므로 +2 → -1
        XCTAssertEqual(state.wrongPool[Problems.key(wrong.problem.a, wrong.problem.b)], 1)
        XCTAssertTrue(state.modesPlayed.contains(.stairs))
        XCTAssertTrue(state.tableMastery.isEmpty, "단을 정하지 않은 판은 단 별에 반영하지 않는다")
        XCTAssertEqual(engine.result?.xpEarned, learning.commits[0].xpEarned)
        XCTAssertEqual(engine.learning.commit?.xpEarned, learning.commits[0].xpEarned)

        // 결과 창이 떠 있는 동안 프레임이 더 돌거나 [나가기]로 나가도 다시 기록하지 않는다
        for _ in 0..<60 { engine.advance(frame) }
        engine.abandon()
        XCTAssertEqual(learning.results.count, 1)
    }

    /// 무료 체험(MinigameTrial.questions 칸) — 체험 종료 패널을 띄울 때 완료로 기록
    func testStairsTrialEndCommits() {
        let learning = LearningSpy()
        let n = MinigameTrial.questions
        let engine = stairs(learning, trial: MinigameTrial.limit(isPremium: false))
        for _ in 0..<n { jump(engine, correct: true) }
        XCTAssertTrue(engine.trialFinished)
        untilResult({ engine.result != nil }) { engine.advance($0) }

        XCTAssertEqual(learning.results.count, 1)
        XCTAssertFalse(learning.results[0].partial)
        XCTAssertEqual(learning.results[0].answers.count, n)
        XCTAssertEqual(learning.state.dailyCorrect, n)
        XCTAssertEqual(engine.result?.xpEarned, learning.commits[0].xpEarned)
    }

    /// 판 중간에 쉬는 창에서 나가면 푼 답만 부분 기록(XP·오늘 정답·틀린 문제는 오르고, 한 판 완료 통계는 그대로)
    func testStairsQuitMidRoundCommitsPartial() {
        let learning = LearningSpy()
        let engine = stairs(learning)
        jump(engine, correct: true)
        jump(engine, correct: false)
        engine.openPause()
        engine.abandon()
        engine.abandon()   // [메뉴로 나가기]를 두 번 눌러도 한 번만

        XCTAssertEqual(learning.results.count, 1)
        let result = learning.results[0]
        XCTAssertTrue(result.partial)
        XCTAssertEqual(result.answers.count, 2)
        XCTAssertEqual(learning.state.dailyCorrect, 1)
        XCTAssertEqual(learning.state.wrongPool.count, 1)
        XCTAssertTrue(learning.state.recentAccuracy.isEmpty, "부분 기록은 정확도 추이에 넣지 않는다")
        XCTAssertTrue(learning.state.modesPlayed.isEmpty)
    }

    /// 한 칸도 안 오르고 나가면(시작 전 패널·첫 탭 전) 기록할 것이 없다
    func testStairsQuitWithoutAnswersCommitsNothing() {
        let learning = LearningSpy()
        let ready = StairsEngine(start: .standard, order: .sequential, topStep: 9, rewards: makeStore(), commit: learning.commit,
                                 random: SeededRandom.mulberry32(1), runsLoop: false)
        ready.abandon()   // 시작 전 패널의 [나가기]
        let started = stairs(learning)
        for _ in 0..<30 { started.advance(frame) }
        started.openPause()
        started.abandon()
        XCTAssertTrue(learning.results.isEmpty)
    }

    /// [다시 하기]로 새 판이 되면 그 판도 따로 한 번 기록
    func testStairsRestartCommitsEachRound() {
        let learning = LearningSpy()
        let engine = stairs(learning)
        jump(engine, correct: true)
        untilResult({ engine.result != nil }) { engine.advance($0) }
        for _ in 0..<40 { engine.advance(frame) }
        engine.restart(trial: nil)
        XCTAssertNil(engine.learning.commit)

        jump(engine, correct: true)
        jump(engine, correct: true)
        engine.openPause()
        engine.abandon()
        XCTAssertEqual(learning.results.map(\.partial), [false, true])
        XCTAssertEqual(learning.results.map(\.answers.count), [1, 2])
        XCTAssertEqual(learning.state.dailyCorrect, 3)
    }

    // MARK: - 구구 몬스터

    /// 단 지정(7단) 판이 60초로 끝나면 그 단으로 완료 기록 — 틀린 문제·오늘 정답에 반영, 7단 마스터리 별은 주지 않음
    func testMonsterTableRoundCommitsWithTable() throws {
        let learning = LearningSpy()
        let engine = monster(learning, start: .table(7), order: .sequential)
        pick(engine, correct: true)
        pick(engine, correct: false)
        pick(engine, correct: true)
        let wrong = try XCTUnwrap(engine.session.attempts.first { !$0.correct })
        untilResult({ engine.result != nil }) { engine.advance($0) }

        XCTAssertEqual(learning.results.count, 1)
        let result = learning.results[0]
        XCTAssertEqual(result.mode, .monster)
        XCTAssertEqual(result.table, 7)
        XCTAssertFalse(result.partial)
        XCTAssertEqual(result.answers, engine.answerRecords)
        XCTAssertTrue(result.answers.allSatisfy { $0.a == 7 })
        XCTAssertEqual(result.durationMs, Int((RoundClock.durationSec * 1000).rounded()))

        let state = learning.state
        XCTAssertEqual(state.dailyCorrect, 2)
        XCTAssertGreaterThan(state.totalXp, 0)
        // 틀린 몬스터는 같은 목표로 다시 나와 맞혔으므로 +2 → -1
        XCTAssertEqual(state.wrongPool[Problems.key(7, wrong.problem.b)], 1)
        XCTAssertNil(state.tableMastery[7], "게임 판은 단 마스터리 별에 반영하지 않는다(체험 판만으로 별 3개 방지)")
        XCTAssertTrue(state.modesPlayed.contains(.monster))
        XCTAssertEqual(engine.result?.xpEarned, learning.commits[0].xpEarned)
    }

    /// 섞어서 판은 단 nil, 판 중간에 나가면 부분 기록
    func testMonsterMixedQuitCommitsPartialWithoutTable() {
        let learning = LearningSpy()
        let engine = monster(learning, start: .table(nil))
        pick(engine, correct: true)
        pick(engine, correct: true)
        engine.openPause()
        engine.abandon()

        XCTAssertEqual(learning.results.count, 1)
        XCTAssertNil(learning.results[0].table)
        XCTAssertTrue(learning.results[0].partial)
        XCTAssertEqual(learning.state.dailyCorrect, 2)
        XCTAssertTrue(learning.state.tableMastery.isEmpty)
    }

    /// 무료 체험(MinigameTrial.questions 마리) — 체험 종료 때 완료로 기록, 이후 [나가기]는 다시 기록하지 않음
    func testMonsterTrialEndCommitsOnce() {
        let learning = LearningSpy()
        let n = MinigameTrial.questions
        let engine = monster(learning, trial: MinigameTrial.limit(isPremium: false))
        for _ in 0..<n { pick(engine, correct: true) }
        untilResult({ engine.result != nil }) { engine.advance($0) }
        engine.abandon()

        XCTAssertEqual(learning.results.count, 1)
        XCTAssertFalse(learning.results[0].partial)
        XCTAssertEqual(learning.results[0].answers.count, n)
    }

    /// 시작만 하고 카드를 안 고른 채 나가면 기록 없음
    func testMonsterQuitWithoutAnswersCommitsNothing() {
        let learning = LearningSpy()
        let engine = monster(learning)
        engine.openPause()
        engine.abandon()
        XCTAssertTrue(learning.results.isEmpty)
    }

    // MARK: - 서버 원장

    /// 게임 판은 서버 계약에 없는 모드라 practice 로 보낸다. factId 계약 범위가 2~19단 × 1~19 라서
    /// 계단의 10단 이상 문항(단 시트 최대 19단·「곱하는 수」 최대 19)도 빠지지 않고 그대로 보낸다
    func testGameEventsGoToServerAsPractice() {
        XCTAssertEqual(LearningRecord.apiMode(.stairs), .practice)
        XCTAssertEqual(LearningRecord.apiMode(.monster), .practice)

        let ids = LearningRecord.Ids(learnerId: "0191d0f3-1b2c-7abc-8def-0123456789ab",
                                     deviceId: "0191d0f3-1b2c-7abc-8def-0123456789ac",
                                     sessionId: "0191d0f3-1b2c-7abc-8def-0123456789ad")
        let stairs = SessionResult(mode: .stairs, table: nil, answers: [
            AnswerRecord(a: 6, b: 9, correct: true, ms: 1200, given: .number(54)),
            AnswerRecord(a: 13, b: 1, correct: true, ms: 1500, given: .number(13)),
            AnswerRecord(a: 7, b: 2, correct: false, ms: 2100, given: .number(21)),
        ], maxCombo: 2, durationMs: 9000)
        let events = LearningRecord.events(from: stairs, ids: ids)
        XCTAssertEqual(events.map(\.mode), [.practice, .practice, .practice])
        XCTAssertEqual(events.map(\.factId), [LearningContract.factId(a: 6, b: 9),
                                              LearningContract.factId(a: 13, b: 1),
                                              LearningContract.factId(a: 7, b: 2)])
        XCTAssertEqual(events.map(\.submittedAnswer), [.number(54), .number(13), .number(21)])
        XCTAssertEqual(events.map(\.tableNo), [nil, nil, nil], "단이 섞인 판은 tableNo 없음")

        let monster = SessionResult(mode: .monster, table: 7, answers: [
            AnswerRecord(a: 7, b: 6, correct: true, ms: 1800, given: .number(42)),
            AnswerRecord(a: 7, b: 8, correct: false, ms: 2500, given: .number(54)),
        ], maxCombo: 1, durationMs: 60_000)
        let monsterEvents = LearningRecord.events(from: monster, ids: ids)
        XCTAssertEqual(monsterEvents.map(\.mode), [.practice, .practice])
        XCTAssertEqual(monsterEvents.map(\.tableNo), [7, 7])
    }

    // MARK: - 모드 목록

    /// 게임 모드는 학습 탭 모드 목록·업적 '모드 탐험가' 기준에 끼어들지 않는다(어드벤처와 같은 방식)
    func testGameModesStayOutOfLearningModeList() {
        let ids = Modes.list.map(\.id)
        XCTAssertEqual(ids, [.practice, .timeAttack, .challenge, .survival, .missing, .truefalse])
        XCTAssertFalse(Modes.options(table: nil).contains { $0.id == .stairs || $0.id == .monster })
        XCTAssertFalse(Modes.options(table: 7).contains { $0.id == .stairs || $0.id == .monster })
        for mode in [GameMode.stairs, .monster] {
            XCTAssertEqual(Modes.def(mode).id, mode)
            XCTAssertFalse(Modes.def(mode).scored, "최고 기록은 게임 보상 저장소가 따로 맡는다")
            XCTAssertFalse(PremiumConfig.isPremiumMode(mode), "잠금은 3문제 체험이 맡는다 — 모드 잠금 아님")
            XCTAssertFalse(ModeStyle.icon(mode).isEmpty)
        }
        XCTAssertEqual(Set(GameMode.allCases).subtracting(ids), [.adventure, .stairs, .monster])

        var s = Commit.defaultState
        s.modesPlayed = [.practice, .timeAttack, .challenge, .survival, .missing, .stairs, .monster, .adventure]
        XCTAssertFalse(Achievements.newlyUnlocked(s).contains("mode_explorer"), "OX 퀴즈를 안 했으면 게임을 해도 해금 안 됨")
    }
}
