import XCTest
import SwiftUI
@testable import Gugu

/// 구구 계단 화면 엔진(StairsEngine) — 프레임 루프 없이(runsLoop: false) advance 를 직접 불러 확인한다.
/// 쉬는 동안 시간 정지, 재개·다시 하기 때 쌓인 탭 폐기, [⏸] 위 탭 제외, 체험 3문제 끝, 별·기록은 결과 때만 저장.
final class StairsEngineTests: XCTestCase {

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

    private func makeEngine(_ store: PlayRewardsStore, trial: Int? = nil, seed: UInt32 = 7,
                            learning: LearningSpy = LearningSpy()) -> StairsEngine {
        let engine = StairsEngine(start: .table(2), order: .sequential, topStep: 9, rewards: store, commit: learning.commit,
                                  random: SeededRandom.mulberry32(seed), runsLoop: false)
        engine.setViewport(viewport)
        engine.start(trial: trial)
        return engine
    }

    /// 칸 가운데(문제 판 아래 아무 높이) — 실제 손가락과 같은 큐 경로로 넣는다
    private func point(lane: Int) -> CGPoint {
        CGPoint(x: StairsCamera(size: viewport.size, centerY: 0).laneCenterX(lane), y: 600)
    }

    /// 정답 칸을 누르고 한 프레임 진행
    private func climb(_ engine: StairsEngine, file: StaticString = #filePath, line: UInt = #line) {
        let before = engine.session.climbed
        engine.enqueueTap(point(lane: engine.session.current.correctLane))
        engine.advance(frame)
        XCTAssertEqual(engine.session.climbed, before + 1, file: file, line: line)
    }

    /// 결과(또는 체험 종료)가 뜰 때까지 흘린다 — 먹구름이 잡을 때까지 포함, 상한으로 무한 반복 방지
    private func advanceUntilResult(_ engine: StairsEngine, file: StaticString = #filePath, line: UInt = #line) {
        var i = 0
        while engine.result == nil && i < 10_000 {
            engine.advance(0.05)
            i += 1
        }
        XCTAssertNotNil(engine.result, "결과가 뜨지 않음", file: file, line: line)
    }

    // MARK: - 시작 전

    /// 시작 전 패널·첫 탭 전에는 먹구름이 멈춰 있다
    func testCloudFrozenBeforeFirstTap() {
        let engine = makeEngine(makeStore())
        let top = engine.session.cloud.top
        XCTAssertTrue(engine.hintVisible)
        XCTAssertEqual(engine.stage.rows[0]?.lanes[1].label, "출발")
        for _ in 0..<120 { engine.advance(frame) }
        XCTAssertEqual(engine.session.cloud.top, top)
        XCTAssertEqual(engine.session.clock.phase, .waiting)
    }

    /// .table(n) = n단부터, 문제 판 글자
    func testStartsAtChosenTable() {
        let engine = makeEngine(makeStore())
        XCTAssertEqual(engine.session.current.problem.a, 2)
        XCTAssertEqual(engine.problemText, "2 × 1")
    }

    // MARK: - 쉬는 창

    /// 쉬는 동안 먹구름·판 시간·반짝임 시간이 모두 멈추고, 이어 하면 다시 흐른다
    func testPauseFreezesTime() {
        let engine = makeEngine(makeStore())
        climb(engine)
        for _ in 0..<30 { engine.advance(frame) }
        engine.openPause()
        XCTAssertTrue(engine.paused)

        let top = engine.session.cloud.top
        let elapsed = engine.session.clock.elapsed
        let time = engine.time
        for _ in 0..<600 { engine.advance(0.1) }

        XCTAssertEqual(engine.session.cloud.top, top)
        XCTAssertEqual(engine.session.clock.elapsed, elapsed)
        XCTAssertEqual(engine.time, time)
        XCTAssertFalse(engine.session.caught)

        engine.resume()
        engine.advance(0.05)
        XCTAssertGreaterThan(engine.session.clock.elapsed, elapsed)
        XCTAssertGreaterThan(engine.session.cloud.top, top)
    }

    /// 쉬기 직전에 쌓인 탭·쉬는 동안의 탭은 이어 한 뒤 점프로 새지 않는다
    func testQueuedTapsDiscardedAcrossPause() {
        let engine = makeEngine(makeStore())
        climb(engine)
        engine.enqueueTap(point(lane: engine.session.current.correctLane))   // 같은 프레임에 [⏸] 를 누름
        engine.openPause()
        engine.enqueueTap(point(lane: engine.session.current.correctLane))   // 창 뒤 탭(창이 막지만 엔진도 받지 않음)
        engine.resume()
        engine.advance(frame)

        XCTAssertEqual(engine.session.climbed, 1)
        XCTAssertEqual(engine.session.wrongCount, 0)
    }

    /// 앱이 백그라운드로 가면 쉬는 창이 열린다
    func testSceneInactiveOpensPause() {
        let engine = makeEngine(makeStore())
        climb(engine)
        engine.sceneBecameInactive()
        XCTAssertTrue(engine.paused)
        engine.sceneBecameActive()
        XCTAssertTrue(engine.paused)   // 돌아와도 아이가 [계속 하기]를 누를 때까지 멈춰 있음
    }

    /// [⏸] 위 탭은 왼쪽 칸 점프로 새지 않는다
    func testTapOnPauseButtonIsIgnored() {
        let engine = makeEngine(makeStore())
        let pause = PauseLayout.buttonFrame(viewport)
        engine.enqueueTap(CGPoint(x: pause.midX, y: pause.midY))
        engine.advance(frame)

        XCTAssertEqual(engine.session.climbed, 0)
        XCTAssertEqual(engine.session.wrongCount, 0)
        XCTAssertEqual(engine.session.clock.phase, .waiting)
    }

    // MARK: - 체험

    /// 무료 체험은 3칸을 오르면 끝 — 더 눌러도 오르지 않고, 1초 뒤 결과(체험 종료)와 함께 별·기록 저장
    func testTrialEndsAfterThreeClimbs() {
        let store = makeStore()
        let engine = makeEngine(store, trial: 3)
        XCTAssertEqual(engine.trialLeft, 3)
        climb(engine)
        climb(engine)
        XCTAssertEqual(engine.trialLeft, 1)
        climb(engine)

        XCTAssertTrue(engine.trialFinished)
        XCTAssertTrue(engine.roundOver)
        XCTAssertFalse(engine.pauseButtonVisible)
        engine.enqueueTap(point(lane: engine.session.current.correctLane))
        engine.advance(frame)
        XCTAssertEqual(engine.session.climbed, 3)
        XCTAssertNil(engine.result)
        XCTAssertEqual(store.stars, 0)   // 결과 전에는 저장 안 함

        advanceUntilResult(engine)
        let earned = engine.session.stars
        XCTAssertGreaterThan(earned, 0)
        XCTAssertEqual(store.stars, earned)
        XCTAssertEqual(store.best(.stairs), 3)
        XCTAssertEqual(engine.result?.mainValue, 3)
        XCTAssertEqual(engine.result?.starTotal, earned)
    }

    // MARK: - 결과·저장

    /// 판 도중에는 별이 저장소에 쌓이지 않고, 결과를 띄우는 순간 한 번만 쌓인다
    func testStarsSavedOnlyAtResult() {
        let store = makeStore()
        let engine = makeEngine(store)
        for _ in 0..<5 { climb(engine) }
        for _ in 0..<10 { engine.advance(frame) }
        XCTAssertGreaterThan(engine.session.stars, 0)
        XCTAssertEqual(store.stars, 0)
        XCTAssertEqual(store.best(.stairs), 0)

        advanceUntilResult(engine)   // 먹구름에 잡힘 → 1초 뒤 결과
        XCTAssertTrue(engine.session.caught)
        let earned = engine.session.stars
        XCTAssertEqual(store.stars, earned)
        XCTAssertEqual(store.best(.stairs), 5)
        XCTAssertEqual(engine.result?.note, GameResultContent.note(PlayRoundRecord(previousBest: 0, isRecord: true, starTotal: earned,
                                                                                    missionsCompleted: [], allMissionsDone: false)))

        // 결과 창이 떠 있는 동안 프레임이 더 돌아도 다시 쌓지 않음
        for _ in 0..<120 { engine.advance(frame) }
        XCTAssertEqual(store.stars, earned)
    }

    /// 판 중간에 쉬는 창에서 나가면(엔진은 결과를 만들지 않음) 별·기록이 남지 않는다
    func testQuitFromPauseSavesNothing() {
        let store = makeStore()
        let engine = makeEngine(store)
        for _ in 0..<3 { climb(engine) }
        engine.openPause()
        engine.teardown()

        XCTAssertNil(engine.result)
        XCTAssertEqual(store.stars, 0)
        XCTAssertEqual(store.best(.stairs), 0)
        XCTAssertNil(store.rewards.missionLog)   // 오늘의 미션에도 안 쌓임
        XCTAssertGreaterThan(engine.session.stars, 0)
        XCTAssertEqual(PauseLayout.message(starsThisRound: engine.session.stars),
                       "지금 나가면 이번 판 별 \(engine.session.stars)개와\n기록이 사라져요")
        XCTAssertEqual(PauseLayout.message(starsThisRound: 0), "지금 나가면 이번 판 기록이 사라져요")
    }

    /// 결과를 띄우는 순간 오늘의 미션에 이번 판(오른 계단·받은 별·최고 연속)이 들어가고, 계단 미션을 끝낸 판은 결과 창에 미션 줄
    func testRoundEndFeedsDailyMissions() throws {
        let store = makeStore()
        let engine = makeEngine(store)
        let stairs = store.missionBoard().items[0].mission
        XCTAssertEqual(stairs.kind, .stairsSteps)
        for _ in 0..<stairs.target { climb(engine) }
        XCTAssertEqual(store.missionBoard().items[0].progress, 0)   // 결과 전에는 안 쌓임

        advanceUntilResult(engine)

        let board = store.missionBoard()
        XCTAssertEqual(board.items[0].progress, stairs.target)
        XCTAssertTrue(board.items[0].done)
        XCTAssertEqual(board.items[1].progress, 0)   // 몬스터 미션은 그대로
        let log = try XCTUnwrap(store.rewards.missionLog)
        XCTAssertEqual(log.stairsSteps, stairs.target)
        XCTAssertEqual(log.starsEarned, engine.session.stars)
        XCTAssertEqual(log.bestStreak, engine.session.bestStreak)
        XCTAssertEqual(engine.result?.missionNote, "미션 완료! 계단 \(stairs.target)칸 오르기")
    }

    /// [다시 하기]는 결과가 뜬 뒤 0.6초부터. 새 판은 0칸·첫 탭 전 상태에서, 지난 판 탭 없이 시작한다
    func testRestartArmsAfterDelayAndStartsClean() {
        let store = makeStore()
        let engine = makeEngine(store)
        climb(engine)
        advanceUntilResult(engine)
        let round = engine.round

        engine.restart(trial: nil)
        XCTAssertEqual(engine.round, round, "0.6초 전에는 무시")
        engine.enqueueTap(point(lane: 0))   // 결과 창 위 탭 — 받지 않음
        for _ in 0..<40 { engine.advance(frame) }
        XCTAssertTrue(engine.restartArmed)

        engine.restart(trial: nil)
        XCTAssertEqual(engine.round, round + 1)
        engine.advance(frame)
        XCTAssertNil(engine.result)
        XCTAssertEqual(engine.session.climbed, 0)
        XCTAssertEqual(engine.session.wrongCount, 0)
        XCTAssertEqual(engine.session.clock.phase, .waiting)
        XCTAssertEqual(engine.bestAtStart, 1)   // 지난 판 기록이 깃발 높이로
    }

    /// 학습 기록 연결용 값 — 채점 기록과 판 길이(ms)
    func testExposesAnswerRecordsAndDuration() {
        let engine = makeEngine(makeStore())
        climb(engine)
        let wrong = (engine.session.current.correctLane + 1) % StairsSession.lanes
        engine.enqueueTap(point(lane: wrong))
        engine.advance(0.5)

        XCTAssertEqual(engine.answerRecords.count, 2)
        XCTAssertEqual(engine.answerRecords.map(\.correct), [true, false])
        XCTAssertEqual(engine.roundDurationMs, Int((engine.session.clock.elapsed * 1000).rounded()))
        XCTAssertGreaterThan(engine.roundDurationMs, 0)
    }
}
