import XCTest
import SwiftUI
@testable import Gugu

/// 게임 문제 순서 [순서대로]·[무작위] (2.9.0) — 선택 기억(GameOrderStore), 시작 창에서 바꿀 때 엔진이 첫 문제를 다시 만드는지,
/// 학습 기록 단 규칙, 무료 체험(맞힌 수로 끝)과의 조합. 세션 규칙·골든은 StairsTests·MonsterTests 에 있다.
final class GameOrderTests: XCTestCase {

    /// iPhone 17 Pro 크기·안전 영역(pt)
    private let viewport = GameViewport(size: CGSize(width: 402, height: 874),
                                        safeArea: EdgeInsets(top: 62, leading: 0, bottom: 34, trailing: 0))
    private let frame = 1.0 / 60

    // MARK: - 도우미

    /// 테스트마다 고유 키 — 실제 저장본과 섞이지 않게, 끝나면 지운다(격리본 포함)
    private func uniqueKey(_ prefix: String) -> String {
        let key = "gugu.test.\(prefix).\(UUID().uuidString)"
        addTeardownBlock {
            UserDefaults.standard.removeObject(forKey: key)
            Persistence.clearQuarantine(key: key)
        }
        return key
    }

    private func makeRewards() -> PlayRewardsStore {
        PlayRewardsStore(key: uniqueKey("play"), adventureKey: uniqueKey("adventure"))
    }

    private func stairs(start: MinigameStart = .table(7), order: ProblemOrder, seed: UInt32 = 7,
                        learning: LearningSpy = LearningSpy()) -> StairsEngine {
        let engine = StairsEngine(start: start, order: order, topStep: 9, rewards: makeRewards(), commit: learning.commit,
                                  random: SeededRandom.mulberry32(seed), runsLoop: false)
        engine.setViewport(viewport)
        return engine
    }

    private func monster(start: MinigameStart = .table(7), order: ProblemOrder, seed: UInt32 = 7,
                         learning: LearningSpy = LearningSpy()) -> MonsterEngine {
        let engine = MonsterEngine(start: start, order: order, rewards: makeRewards(), commit: learning.commit,
                                   random: SeededRandom.mulberry32(seed), runsLoop: false)
        engine.setViewport(viewport)
        return engine
    }

    /// 계단 칸 가운데를 누르고 한 프레임 — 오답이면 정답 공개(0.6초)가 끝날 때까지 흘린다
    private func jump(_ engine: StairsEngine, correct: Bool) {
        let lane = correct ? engine.session.current.correctLane
                           : (engine.session.current.correctLane + 1) % StairsSession.lanes
        engine.enqueueTap(CGPoint(x: StairsCamera(size: viewport.size, centerY: 0).laneCenterX(lane), y: 600))
        engine.advance(frame)
        if !correct { for _ in 0..<40 { engine.advance(frame) } }
    }

    /// 몬스터 카드 두 장 — 정답 짝 또는 곱이 목표가 아닌 짝. 오답이면 공개가 끝날 때까지 흘린다
    private func pick(_ engine: MonsterEngine, correct: Bool) {
        let q = engine.session.current
        let other: Int? = correct ? q.pairB : (0..<MonsterSession.cardCount).first(where: {
            $0 != q.pairA && q.cards[q.pairA] * q.cards[$0] != q.target.a * q.target.b
        })
        guard let second = other else { return XCTFail("오답 카드가 없음 \(q.cards)") }
        engine.enqueueTap(MonsterEngine.Cards.center(q.pairA, viewport))
        engine.enqueueTap(MonsterEngine.Cards.center(second, viewport))
        engine.advance(frame)
        if !correct { for _ in 0..<40 { engine.advance(frame) } }
    }

    private func advanceUntilResult(_ step: (Double) -> Void, done: () -> Bool,
                                    file: StaticString = #filePath, line: UInt = #line) {
        var i = 0
        while !done() && i < 20_000 {
            step(0.05)
            i += 1
        }
        XCTAssertTrue(done(), "결과가 뜨지 않음", file: file, line: line)
    }

    // MARK: - 선택 기억 (GameOrderStore)

    /// 처음은 두 게임 모두 순서대로(지금 동작)
    func testDefaultIsSequentialForBothGames() {
        let store = GameOrderStore(key: uniqueKey("order"), environment: [:])
        XCTAssertEqual(store.order(.stairs), .sequential)
        XCTAssertEqual(store.order(.monster), .sequential)
    }

    /// 게임마다 따로 기억하고, 다시 열어도(새 저장소) 마지막 선택 그대로
    func testChoiceIsPerGameAndSurvivesReopen() {
        let key = uniqueKey("order")
        let store = GameOrderStore(key: key, environment: [:])
        store.setOrder(.random, for: .stairs)
        XCTAssertEqual(store.order(.stairs), .random)
        XCTAssertEqual(store.order(.monster), .sequential, "다른 게임은 그대로")

        let reopened = GameOrderStore(key: key, environment: [:])
        XCTAssertEqual(reopened.order(.stairs), .random)
        XCTAssertEqual(reopened.order(.monster), .sequential)

        reopened.setOrder(.random, for: .monster)
        reopened.setOrder(.sequential, for: .stairs)
        let again = GameOrderStore(key: key, environment: [:])
        XCTAssertEqual(again.order(.stairs), .sequential)
        XCTAssertEqual(again.order(.monster), .random)
    }

    /// 보상·진행도 저장본과 다른 키 — 형식이 바뀌어도 서로 영향 없음
    func testUsesItsOwnKey() {
        XCTAssertEqual(Persistence.gameOrderKey, "gugu.gameOrder.v1")
        XCTAssertNotEqual(Persistence.gameOrderKey, Persistence.playRewardsKey)
        XCTAssertNotEqual(Persistence.gameOrderKey, Persistence.progressKey)
        XCTAssertEqual(ProblemOrder.allCases.map(\.rawValue), ["sequential", "random"], "저장값·Android 와 같은 문자열")
    }

    /// 못 읽는 저장본(모르는 값·빠진 항목)은 조용히 덮어쓰지 않고 원본을 격리한 뒤 순서대로로 시작한다
    func testUnreadableSavedChoiceIsQuarantined() throws {
        for raw in [#"{"stairs":"zigzag","monster":"random"}"#, #"{"stairs":"random"}"#] {
            let key = uniqueKey("order")
            UserDefaults.standard.set(Data(raw.utf8), forKey: key)
            let store = GameOrderStore(key: key, environment: [:])
            XCTAssertEqual(store.order(.stairs), .sequential, raw)
            XCTAssertEqual(store.order(.monster), .sequential, raw)
            XCTAssertEqual(Persistence.quarantinedFiles(key: key).count, 1, "원본 격리: \(raw)")
            XCTAssertEqual(UserDefaults.standard.data(forKey: key), Data(raw.utf8), "고르기 전에는 덮어쓰지 않음")
        }
    }

    /// DEBUG 훅은 저장된 선택보다 우선하지만 저장하지는 않는다
    func testDebugOverrideWinsOverSavedAndIsNotSaved() {
        let key = uniqueKey("order")
        GameOrderStore(key: key, environment: [:]).setOrder(.random, for: .monster)
        let forced = GameOrderStore(key: key, environment: ["GUGU_STAIRS_ORDER": "random", "GUGU_MONSTER_ORDER": "sequential"])
        XCTAssertEqual(forced.order(.stairs), .random)
        XCTAssertEqual(forced.order(.monster), .sequential)
        XCTAssertEqual(forced.saved, GameOrderChoice(stairs: .sequential, monster: .random))
        let plain = GameOrderStore(key: key, environment: [:])
        XCTAssertEqual(plain.order(.stairs), .sequential)
        XCTAssertEqual(plain.order(.monster), .random)
    }

    // MARK: - 시작 창에서 바꾸기 (엔진)

    /// 계단: 순서를 바꾸면 새 세션 — 뒤에 비치는 첫 줄 발판·문제 판 글자도 새 순서. 무작위는 고른 단(7)을 쓰지 않는다
    func testStairsOrderSwitchRebuildsFirstQuestion() {
        let engine = stairs(order: .sequential)
        XCTAssertEqual(engine.session.current.problem, Problem(a: 7, b: 1))
        XCTAssertEqual(engine.tableLabel, "7단 · 1번째 계단")

        let before = engine.session
        engine.setOrder(.sequential)   // 같은 쪽을 다시 누름 → 그대로
        XCTAssertTrue(engine.session === before)

        engine.setOrder(.random)
        XCTAssertEqual(engine.order, .random)
        XCTAssertFalse(engine.session === before, "새 세션")
        XCTAssertEqual(engine.session.order, .random)
        XCTAssertTrue(StairsSession.randomTables.contains(engine.session.current.problem.a))
        XCTAssertEqual(engine.session.current.standing, 0)
        XCTAssertEqual(engine.tableLabel, "1번째 계단", "무작위는 단 없이")
        XCTAssertEqual(engine.stage.rows[1]?.lanes.map(\.label), engine.session.current.choices.map(String.init),
                       "뒤에 비치는 첫 줄도 새 문제")
        XCTAssertEqual(engine.stage.rows[0]?.lanes[1].label, "출발")
        XCTAssertEqual(engine.phase, .ready)

        engine.setOrder(.sequential)
        XCTAssertEqual(engine.session.current.problem, Problem(a: 7, b: 1), "순서대로로 돌아오면 고른 단부터")
    }

    /// 계단 무작위: 문제 판 윗줄은 "N번째 계단"(보물까지 몇 번째 칸), 꼭대기 칸에서 칸 기준 보물 알림, [다시 하기]도 무작위
    func testStairsRandomLabelsTreasureBannerAndRestartKeepsOrder() {
        let engine = stairs(start: .standard, order: .random)
        engine.start(trial: nil)
        for step in 1...9 {
            XCTAssertEqual(engine.tableLabel, "\(step)번째 계단")
            jump(engine, correct: true)
        }
        XCTAssertEqual(engine.session.treasures, 1)
        XCTAssertEqual(engine.banner.text, "9칸 돌파! 보물 +1")
        XCTAssertEqual(engine.tableLabel, "1번째 계단")

        advanceUntilResult({ engine.advance($0) }, done: { engine.restartArmed })
        engine.restart(trial: nil)
        XCTAssertEqual(engine.order, .random)
        XCTAssertEqual(engine.session.order, .random)
        XCTAssertEqual(engine.session.climbed, 0)
    }

    /// 몬스터: 순서대로 + 7단 → 제목 단·학습 기록 단 7. 무작위로 바꾸면 고른 단을 쓰지 않고(단 nil) 다시 순서대로면 7×1 부터
    func testMonsterOrderSwitchRebuildsFirstMonsterAndTable() {
        let engine = monster(order: .sequential)
        XCTAssertEqual(engine.session.current.target, Problem(a: 7, b: 1))
        XCTAssertEqual(engine.playedTable, 7)
        XCTAssertEqual(engine.learning.table, 7)
        XCTAssertEqual(engine.stage.hp, 7)

        let before = engine.session
        engine.setOrder(.random)
        XCTAssertFalse(engine.session === before)
        XCTAssertEqual(engine.session.order, .random)
        XCTAssertNil(engine.session.table)
        XCTAssertNil(engine.playedTable, "무작위는 제목에 단을 붙이지 않음")
        XCTAssertEqual(engine.chosenTable, 7, "고른 단은 그대로 기억(순서대로로 돌아올 때)")
        XCTAssertNil(engine.learning.table)
        XCTAssertEqual(engine.stage.hp, engine.session.current.target.a * engine.session.current.target.b,
                       "뒤에 비치는 몬스터 체력도 새 문제")

        engine.setOrder(.sequential)
        XCTAssertEqual(engine.session.current.target, Problem(a: 7, b: 1))
        XCTAssertEqual(engine.learning.table, 7)
    }

    /// 몬스터 순서대로 7단 판: 7 → 14 → 21 … 차례로, [다시 하기]도 순서대로 7단
    func testMonsterSequentialTablePlaysInOrderAndRestartKeepsOrder() {
        let engine = monster(order: .sequential)
        engine.start(trial: nil)
        for b in 1...4 {
            XCTAssertEqual(engine.stage.hp, 7 * b)
            pick(engine, correct: true)
        }
        advanceUntilResult({ engine.advance($0) }, done: { engine.restartArmed })
        engine.restart(trial: nil)
        XCTAssertEqual(engine.order, .sequential)
        XCTAssertEqual(engine.session.current.target, Problem(a: 7, b: 1))
    }

    // MARK: - 학습 기록 단

    /// 몬스터 순서대로 + 단 지정만 그 단, 단 없는 순서대로·무작위(고른 단 무시)는 nil. 계단은 순서와 상관없이 nil
    func testLearningTableRule() {
        func monsterTable(_ start: MinigameStart, _ order: ProblemOrder) -> Int?? {
            let learning = LearningSpy()
            let engine = monster(start: start, order: order, learning: learning)
            engine.start(trial: nil)
            pick(engine, correct: true)
            engine.openPause()
            engine.abandon()
            return learning.results.first.map(\.table)
        }
        XCTAssertEqual(monsterTable(.table(7), .sequential), .some(7))
        XCTAssertEqual(monsterTable(.table(7), .random), .some(nil))
        XCTAssertEqual(monsterTable(.standard, .sequential), .some(nil))
        XCTAssertEqual(monsterTable(.standard, .random), .some(nil))

        for order in ProblemOrder.allCases {
            let learning = LearningSpy()
            let engine = stairs(order: order, learning: learning)
            engine.start(trial: nil)
            jump(engine, correct: true)
            engine.openPause()
            engine.abandon()
            XCTAssertEqual(learning.results.map(\.table), [nil], "\(order)")
        }
    }

    // MARK: - 무료 체험과의 조합

    /// 무작위 계단·순서대로 몬스터도 체험은 맞힌 수로 끝난다(오답은 세지 않음)
    func testTrialEndsByCorrectCountInEveryOrder() {
        let n = MinigameTrial.questions
        for order in ProblemOrder.allCases {
            let s = stairs(start: .standard, order: order)
            s.start(trial: MinigameTrial.limit(isPremium: false))
            for i in 0..<n {
                XCTAssertFalse(s.roundOver, "\(order) \(i)")
                if i == 2 { jump(s, correct: false) }
                jump(s, correct: true)
            }
            XCTAssertTrue(s.trialFinished, "계단 \(order)")
            XCTAssertEqual(s.session.climbed, n)
            XCTAssertEqual(s.session.wrongCount, 1)

            let m = monster(start: .standard, order: order)
            m.start(trial: MinigameTrial.limit(isPremium: false))
            for i in 0..<n {
                XCTAssertFalse(m.roundOver, "\(order) \(i)")
                if i == 4 { pick(m, correct: false) }
                pick(m, correct: true)
            }
            XCTAssertTrue(m.trialFinished, "몬스터 \(order)")
            XCTAssertEqual(m.session.defeated, n)
            XCTAssertEqual(m.session.wrongCount, 1)
        }
    }
}
