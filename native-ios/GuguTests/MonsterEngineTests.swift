import XCTest
import SwiftUI
@testable import Gugu

/// 구구 몬스터 화면 엔진(MonsterEngine) — 프레임 루프 없이(runsLoop: false) advance 를 직접 불러 확인한다.
/// 쉬는 동안 시계 정지, 재개·다시 하기 때 쌓인 탭 폐기, [⏸] 위 탭 제외, 체험 3마리 끝,
/// 처치 순간 도감 저장·결과 때 별 저장, 단 지정이면 목표가 그 단, 새 종 해금 알림.
final class MonsterEngineTests: XCTestCase {

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

    private func makeEngine(_ store: PlayRewardsStore, start: MinigameStart = .standard, order: ProblemOrder = .random,
                            trial: Int? = nil, seed: UInt32 = 7, learning: LearningSpy = LearningSpy()) -> MonsterEngine {
        let engine = MonsterEngine(start: start, order: order, rewards: store, commit: learning.commit,
                                   random: SeededRandom.mulberry32(seed), runsLoop: false)
        engine.setViewport(viewport)
        engine.start(trial: trial)
        return engine
    }

    /// 카드 가운데 — 실제 손가락과 같은 큐 경로로 넣는다
    private func point(card: Int) -> CGPoint { MonsterEngine.Cards.center(card, viewport) }

    /// 정답 두 장을 같은 프레임에 누르고 한 프레임 진행
    private func defeat(_ engine: MonsterEngine, file: StaticString = #filePath, line: UInt = #line) {
        let before = engine.session.defeated
        let q = engine.session.current
        engine.enqueueTap(point(card: q.pairA))
        engine.enqueueTap(point(card: q.pairB))
        engine.advance(frame)
        XCTAssertEqual(engine.session.defeated, before + 1, file: file, line: line)
    }

    /// pairA 와 곱이 목표가 아닌 카드 중 가장 앞 자리
    private func wrongCard(_ q: MonsterQuestion, file: StaticString = #filePath, line: UInt = #line) -> Int {
        guard let i = (0..<MonsterSession.cardCount).first(where: {
            $0 != q.pairA && q.cards[q.pairA] * q.cards[$0] != q.target.a * q.target.b
        }) else {
            XCTFail("오답 카드가 없음 \(q.cards)", file: file, line: line)
            return q.pairA
        }
        return i
    }

    /// 프레임 상한(0.1초)에 걸리지 않게 sec 만큼 60fps 로 흘린다
    private func run(_ engine: MonsterEngine, sec: Double) {
        for _ in 0..<Int((sec / frame).rounded(.up)) { engine.advance(frame) }
    }

    /// 결과(또는 체험 종료)가 뜰 때까지 흘린다 — 60초 시계 포함, 상한으로 무한 반복 방지
    private func advanceUntilResult(_ engine: MonsterEngine, file: StaticString = #filePath, line: UInt = #line) {
        var i = 0
        while engine.result == nil && i < 10_000 {
            engine.advance(0.05)
            i += 1
        }
        XCTAssertNotNil(engine.result, "결과가 뜨지 않음", file: file, line: line)
    }

    // MARK: - 시작·카드 고르기

    /// 첫 카드 전에는 시계가 멈춰 있고, 카드 한 장을 고르면 흐른다. 고른 카드는 문제 판 "a × ? = 체력"
    func testClockStartsOnFirstCardAndProblemShowsPick() {
        let engine = makeEngine(makeStore())
        XCTAssertTrue(engine.hintVisible)
        for _ in 0..<120 { engine.advance(frame) }
        XCTAssertEqual(engine.session.clock.phase, .waiting)
        let q = engine.session.current
        XCTAssertEqual(engine.problemText, "? × ? = \(q.target.a * q.target.b)")

        engine.enqueueTap(point(card: q.pairA))
        engine.advance(frame)
        XCTAssertEqual(engine.session.selected, q.pairA)
        XCTAssertTrue(engine.cardSelected(q.pairA))
        XCTAssertEqual(engine.problemText, "\(q.cards[q.pairA]) × ? = \(q.target.a * q.target.b)")
        engine.advance(0.5)
        XCTAssertGreaterThan(engine.session.clock.elapsed, 0)
        XCTAssertFalse(engine.hintVisible)
    }

    /// 카드 사이 틈·카드 밖은 카드로 치지 않는다
    func testTapOutsideCardsIsIgnored() {
        let engine = makeEngine(makeStore())
        let a = MonsterEngine.Cards.frame(0, viewport)
        let b = MonsterEngine.Cards.frame(1, viewport)
        engine.enqueueTap(CGPoint(x: (a.maxX + b.minX) / 2, y: a.midY))   // 틈
        engine.enqueueTap(CGPoint(x: viewport.size.width / 2, y: viewport.size.height * 0.4))   // 몬스터
        engine.advance(frame)
        XCTAssertNil(engine.session.selected)
        XCTAssertEqual(engine.session.clock.phase, .waiting)
    }

    /// 오답: 고른 두 장 빨강·정답 두 장 초록, 문제 판에 정답 식·아래 줄에 고른 식. 공개가 끝나면 같은 몬스터, 카드만 다시
    func testWrongPairRevealsThenSameMonster() {
        let engine = makeEngine(makeStore())
        let q = engine.session.current
        let wrong = wrongCard(q)
        engine.enqueueTap(point(card: q.pairA))
        engine.enqueueTap(point(card: wrong))
        engine.advance(frame)

        XCTAssertTrue(engine.problemRevealing)
        XCTAssertEqual(engine.problemText, "\(q.target.a) × \(q.target.b) = \(q.target.a * q.target.b)")
        XCTAssertEqual(engine.subText, "\(q.cards[q.pairA]) × \(q.cards[wrong]) = \(q.cards[q.pairA] * q.cards[wrong])")
        XCTAssertEqual(engine.cardLook(q.pairA), .correct)
        XCTAssertEqual(engine.cardLook(q.pairB), .correct)
        XCTAssertEqual(engine.cardLook(wrong), .wrong)
        let hp = engine.stage.hp

        run(engine, sec: RoundClock.revealSec + 0.05)
        XCTAssertFalse(engine.problemRevealing)
        XCTAssertEqual(engine.session.current.target, q.target)
        XCTAssertEqual(engine.stage.hp, hp)
        XCTAssertEqual((0..<MonsterSession.cardCount).map(engine.cardLook), Array(repeating: .normal, count: 6))
    }

    // MARK: - 쉬는 창

    /// 쉬는 동안 60초 시계·그림 시간이 모두 멈추고, 이어 하면 다시 흐른다
    func testPauseFreezesClock() {
        let engine = makeEngine(makeStore())
        defeat(engine)
        for _ in 0..<30 { engine.advance(frame) }
        engine.openPause()
        XCTAssertTrue(engine.paused)

        let elapsed = engine.session.clock.elapsed
        let time = engine.time
        for _ in 0..<1200 { engine.advance(0.1) }   // 120초 — 멈추지 않았다면 판이 끝났을 시간
        XCTAssertEqual(engine.session.clock.elapsed, elapsed)
        XCTAssertEqual(engine.time, time)
        XCTAssertNotEqual(engine.session.clock.phase, .over)
        XCTAssertNil(engine.result)

        engine.resume()
        engine.advance(0.05)
        XCTAssertGreaterThan(engine.session.clock.elapsed, elapsed)
    }

    /// 쉬기 직전에 쌓인 탭·쉬는 동안의 탭은 이어 한 뒤 카드로 새지 않는다
    func testQueuedTapsDiscardedAcrossPause() {
        let engine = makeEngine(makeStore())
        defeat(engine)
        engine.enqueueTap(point(card: engine.session.current.pairA))   // 같은 프레임에 [⏸] 를 누름
        engine.openPause()
        engine.enqueueTap(point(card: engine.session.current.pairA))   // 창 뒤 탭(창이 막지만 엔진도 받지 않음)
        engine.resume()
        engine.advance(frame)

        XCTAssertNil(engine.session.selected)
        XCTAssertEqual(engine.session.attempts.count, 1)
    }

    /// 앱이 백그라운드로 가면 쉬는 창이 열리고, 돌아와도 [계속 하기]를 누를 때까지 멈춰 있다
    func testSceneInactiveOpensPause() {
        let engine = makeEngine(makeStore())
        defeat(engine)
        engine.sceneBecameInactive()
        XCTAssertTrue(engine.paused)
        engine.sceneBecameActive()
        XCTAssertTrue(engine.paused)
    }

    /// [⏸] 위 탭은 카드·시계에 닿지 않는다
    func testTapOnPauseButtonIsIgnored() {
        let engine = makeEngine(makeStore())
        let pause = PauseLayout.buttonFrame(viewport)
        engine.enqueueTap(CGPoint(x: pause.midX, y: pause.midY))
        engine.advance(frame)
        XCTAssertNil(engine.session.selected)
        XCTAssertEqual(engine.session.clock.phase, .waiting)
    }

    // MARK: - 체험

    /// 무료 체험은 3마리를 물리치면 끝 — 더 눌러도 채점하지 않고, 1초 뒤 결과(체험 종료)와 함께 별·기록 저장
    func testTrialEndsAfterThreeDefeats() {
        let store = makeStore()
        let engine = makeEngine(store, trial: 3)
        XCTAssertEqual(engine.trialLeft, 3)
        defeat(engine)
        defeat(engine)
        XCTAssertEqual(engine.trialLeft, 1)
        defeat(engine)

        XCTAssertTrue(engine.trialFinished)
        XCTAssertTrue(engine.roundOver)
        XCTAssertFalse(engine.pauseButtonVisible)
        XCTAssertFalse(engine.timeUpVisible)
        let q = engine.session.current
        engine.enqueueTap(point(card: q.pairA))
        engine.enqueueTap(point(card: q.pairB))
        engine.advance(frame)
        XCTAssertEqual(engine.session.defeated, 3)
        XCTAssertEqual(engine.session.attempts.count, 3)
        XCTAssertNil(engine.result)
        XCTAssertEqual(store.stars, 0)   // 결과 전에는 별 저장 안 함

        advanceUntilResult(engine)
        XCTAssertEqual(store.stars, 3)
        XCTAssertEqual(store.best(.monster), 3)
        XCTAssertEqual(engine.result?.mainValue, 3)
        XCTAssertEqual(engine.result?.starTotal, 3)
    }

    // MARK: - 저장 시점

    /// 도감은 처치하는 순간 저장, 별·최고 기록은 시간이 다 돼 결과를 띄우는 순간 한 번
    func testDexSavedOnDefeatStarsSavedAtResult() {
        let store = makeStore()
        let engine = makeEngine(store)
        let firstKind = engine.stage.kind
        XCTAssertEqual(firstKind, .green)   // 처음 4종 중 아직 못 만난 첫 종
        defeat(engine)
        XCTAssertEqual(store.dexTotal, 1)
        XCTAssertTrue(MonsterDex.isSeen(firstKind, seenMask: store.dexSeenMask))
        XCTAssertEqual(engine.banner.text, "도감에 등록!")
        XCTAssertEqual(engine.dex.look(firstKind), .seen)
        XCTAssertEqual(engine.dex.popKind, firstKind)
        XCTAssertEqual(engine.stage.pop?.kind, firstKind)   // 기절 몬스터 연출
        XCTAssertFalse(engine.stage.visible)                // 새 몬스터는 0.2초 기다렸다가 떨어짐
        XCTAssertEqual(engine.stage.kind, .orange)          // 다음 종: 아직 못 만난 종 먼저
        run(engine, sec: MonsterStage.nextDropDelay + 0.02)
        XCTAssertTrue(engine.stage.visible)

        for _ in 0..<4 { defeat(engine) }
        XCTAssertEqual(store.dexTotal, 5)
        XCTAssertEqual(store.stars, 0)
        XCTAssertEqual(store.best(.monster), 0)

        advanceUntilResult(engine)   // 60초 → "타임업!" 1초 → 결과
        XCTAssertEqual(engine.session.clock.phase, .over)
        let earned = engine.session.stars
        XCTAssertEqual(earned, 7)    // 5마리 + 연속 5 보너스 2
        XCTAssertEqual(store.stars, earned)
        XCTAssertEqual(store.best(.monster), 5)
        XCTAssertEqual(engine.result?.subValue, 5)   // 최고 연속

        // 결과 창이 떠 있는 동안 프레임이 더 돌아도 다시 쌓지 않음
        for _ in 0..<120 { engine.advance(frame) }
        XCTAssertEqual(store.stars, earned)
    }

    /// 쉬는 창에서 나가면(엔진은 결과를 만들지 않음) 별·기록은 남지 않고 도감만 남는다
    func testQuitFromPauseKeepsDexOnly() {
        let store = makeStore()
        let engine = makeEngine(store)
        for _ in 0..<2 { defeat(engine) }
        engine.openPause()
        engine.teardown()
        XCTAssertNil(engine.result)
        XCTAssertEqual(store.stars, 0)
        XCTAssertEqual(store.best(.monster), 0)
        XCTAssertEqual(store.dexTotal, 2)
        XCTAssertNil(store.rewards.missionLog)   // 오늘의 미션에도 안 쌓임
    }

    /// 결과를 띄우는 순간 오늘의 미션에 이번 판(물리친 몬스터·받은 별·최고 연속)이 들어가고, 몬스터 미션을 끝낸 판은 결과 창에 미션 줄
    func testRoundEndFeedsDailyMissions() throws {
        let store = makeStore()
        let engine = makeEngine(store)
        let monsters = store.missionBoard().items[1].mission
        XCTAssertEqual(monsters.kind, .monstersDefeated)
        for _ in 0..<monsters.target { defeat(engine) }
        XCTAssertEqual(store.missionBoard().items[1].progress, 0)   // 결과 전에는 안 쌓임

        advanceUntilResult(engine)

        let board = store.missionBoard()
        XCTAssertEqual(board.items[0].progress, 0)   // 계단 미션은 그대로
        XCTAssertEqual(board.items[1].progress, monsters.target)
        XCTAssertTrue(board.items[1].done)
        let log = try XCTUnwrap(store.rewards.missionLog)
        XCTAssertEqual(log.monstersDefeated, monsters.target)
        XCTAssertEqual(log.starsEarned, engine.session.stars)
        XCTAssertEqual(log.bestStreak, engine.session.bestStreak)
        XCTAssertEqual(engine.result?.missionNote, "미션 완료! 몬스터 \(monsters.target)마리 물리치기")
    }

    /// 결과 다시 볼 식 — 고른 두 카드를 함께 보여 준다
    func testResultShowsMissWithPickedCards() {
        let engine = makeEngine(makeStore())
        let q = engine.session.current
        let wrong = wrongCard(q)
        engine.enqueueTap(point(card: q.pairA))
        engine.enqueueTap(point(card: wrong))
        engine.advance(frame)
        advanceUntilResult(engine)
        XCTAssertEqual(engine.result?.miss, q.target)
        XCTAssertEqual(engine.result?.missGiven,
                       "고른 카드 \(q.cards[q.pairA]) × \(q.cards[wrong]) = \(q.cards[q.pairA] * q.cards[wrong])")
    }

    /// [다시 하기]는 결과가 뜬 뒤 0.6초부터. 새 판은 첫 카드 전 상태에서, 지난 판 탭 없이 시작한다
    func testRestartArmsAfterDelayAndStartsClean() {
        let engine = makeEngine(makeStore())
        defeat(engine)
        advanceUntilResult(engine)
        let round = engine.round

        engine.restart(trial: nil)
        XCTAssertEqual(engine.round, round, "0.6초 전에는 무시")
        engine.enqueueTap(point(card: 0))   // 결과 창 위 탭 — 받지 않음
        for _ in 0..<40 { engine.advance(frame) }
        XCTAssertTrue(engine.restartArmed)

        engine.restart(trial: nil)
        XCTAssertEqual(engine.round, round + 1)
        engine.advance(frame)
        XCTAssertNil(engine.result)
        XCTAssertNil(engine.session.selected)
        XCTAssertEqual(engine.session.attempts.count, 0)
        XCTAssertEqual(engine.session.clock.phase, .waiting)
        XCTAssertEqual(engine.timeBadge.value, 60)
    }

    // MARK: - 단 지정·도감 해금

    /// 단 시트에서 고른 단(.table(n))이면 몇 마리를 잡아도 목표는 그 단. .table(nil) 은 섞어서
    func testChosenTableTargetsOnlyThatTable() {
        let engine = makeEngine(makeStore(), start: .table(7), order: .sequential)
        XCTAssertEqual(engine.chosenTable, 7)
        for _ in 0..<12 {
            XCTAssertEqual(engine.session.current.target.a, 7)
            XCTAssertEqual(engine.stage.hp % 7, 0)
            defeat(engine)
        }
        XCTAssertTrue(engine.answerRecords.allSatisfy { $0.a == 7 })

        let mixed = makeEngine(makeStore(), start: .table(nil), seed: 3)
        XCTAssertNil(mixed.chosenTable)
        var tables = Set<Int>()
        for _ in 0..<12 {
            tables.insert(mixed.session.current.target.a)
            defeat(mixed)
        }
        XCTAssertGreaterThan(tables.count, 1)
    }

    /// 누적 처치 9 → 10번째 처치로 분홍이 해금되면 바로 다음 몬스터로 등장 + "새 몬스터 등장!"
    func testUnlockedKindAppearsNextWithBanner() {
        let store = makeStore()
        for kind in [MonsterKind.green, .orange, .purple, .sky, .green, .orange, .purple, .sky, .green] {
            store.recordDefeat(kind)
        }
        XCTAssertEqual(store.dexTotal, 9)
        let engine = makeEngine(store)
        XCTAssertEqual(engine.dex.look(.pink), .locked)
        let before = engine.stage.kind
        defeat(engine)

        XCTAssertEqual(store.dexTotal, 10)
        XCTAssertEqual(engine.stage.kind, .pink)
        XCTAssertNotEqual(before, .pink)
        XCTAssertEqual(engine.banner.text, "새 몬스터 등장!")
        XCTAssertEqual(engine.dex.look(.pink), .silhouette)   // 자물쇠가 풀린 실루엣

        run(engine, sec: MonsterStage.nextDropDelay + 0.02)
        defeat(engine)
        XCTAssertTrue(MonsterDex.isSeen(.pink, seenMask: store.dexSeenMask))
        XCTAssertEqual(engine.dex.look(.pink), .seen)
    }

    /// 학습 기록 연결용 값 — 채점 기록(목표 식·두 카드의 곱)과 판 길이(ms)
    func testExposesAnswerRecordsAndDuration() {
        let engine = makeEngine(makeStore())
        defeat(engine)
        let q = engine.session.current
        engine.enqueueTap(point(card: q.pairA))
        engine.enqueueTap(point(card: wrongCard(q)))
        engine.advance(0.5)

        XCTAssertEqual(engine.answerRecords.count, 2)
        XCTAssertEqual(engine.answerRecords.map(\.correct), [true, false])
        XCTAssertEqual(engine.answerRecords[1].a, q.target.a)
        XCTAssertEqual(engine.answerRecords[1].b, q.target.b)
        XCTAssertEqual(engine.roundDurationMs, Int((engine.session.clock.elapsed * 1000).rounded()))
        XCTAssertGreaterThan(engine.roundDurationMs, 0)
    }
}
