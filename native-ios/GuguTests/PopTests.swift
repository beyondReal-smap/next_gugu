import XCTest
@testable import Gugu

/// 구구 팡팡 순수 규칙 — Android PopTests.kt 와 같은 시드·같은 기대값(골든 벡터)으로 두 플랫폼 규칙이 같은지 지킨다.
/// 한쪽 규칙을 바꾸면 양쪽 테스트의 기대값을 함께 바꿔야 한다.
final class PopTests: XCTestCase {

    // MARK: - 도우미

    private func rng(_ seed: UInt32) -> () -> Double { World.mulberry32(seed) }

    /// 미션 안내를 건너뛴 상태
    private func started(_ s: PopState) -> PopState { Pop.advance(s, dt: Pop.introMs) }

    private func balloon(_ id: Int, _ value: Int, correct: Bool, x: Double = 0.5, y: Double = 0.5,
                         kind: PopBalloonKind = .normal) -> PopBalloon {
        PopBalloon(id: id, value: value, kind: kind, correct: correct, x: x, y: y)
    }

    /// 손으로 꾸민 배수 사냥 장면 — 새 풍선이 나오지 않게 queue 를 비운다
    private func scene(table: Int, balloons: [PopBalloon]) -> PopState {
        var s = started(Pop.create(table: table, random: rng(1)))
        s.queue = []
        s.balloons = balloons
        return s
    }

    /// 손으로 꾸민 짝꿍 보스 장면
    private func bossScene(target: Int, values: [Int]) -> PopState {
        var s = started(Pop.create(table: 4, random: rng(1)))
        s.wave = 3
        s.mission = .pairBoss(target: target)
        s.boss = PopBoss(value: target, hitsLeft: Pop.bossHits, y: 0)
        s.queue = []
        s.balloons = values.enumerated().map { i, v in
            balloon(101 + i, v, correct: Pop.isPairMember(v, target: target),
                    x: Pop.smallSlots[i].x, y: Pop.smallSlots[i].y, kind: .small)
        }
        return s
    }

    private enum Policy { case perfect, sloppy, lazy }

    private struct Run: Equatable {
        var score = 0, wave = 0, hearts = 0, maxCombo = 0, pops = 0, wrongTaps = 0, bossesDefeated = 0
        var over = false
        var trialLeft: Int?
        /// id 순서로 처음 나온 풍선 값 30개
        var values: [Int] = []
        var missions: [PopMission] = []
    }

    /// 50ms 프레임으로 돌리는 자동 플레이어. 200ms 마다 한 번 누른다.
    /// perfect: 지붕에 가장 가까운 정답 / 보스면 짝꿍, sloppy: 2초마다 오답 하나를 섞음, lazy: 누르지 않음
    private func play(table: Int?, seed: UInt32, frames: Int, policy: Policy, trial: Int? = nil,
                      check: ((PopState) -> Void)? = nil) -> (run: Run, state: PopState) {
        let random = rng(seed)
        var s = Pop.create(table: table, trial: trial, random: random)
        var run = Run()
        var lastId = 0
        func record() {
            for b in s.balloons.sorted(by: { $0.id < $1.id }) where b.id > lastId {
                run.values.append(b.value)
                lastId = b.id
            }
            if run.missions.last != s.mission { run.missions.append(s.mission) }
            check?(s)
        }
        record()
        for f in 0..<frames {
            s = Pop.advance(s, dt: 50, random: random)
            record()
            if s.phase != .running { break }
            if policy == .lazy || f % 4 != 0 { continue }
            var targetId: Int?
            if policy == .sloppy, f % 40 == 0, let wrong = s.balloons.first(where: { !$0.correct }) {
                targetId = wrong.id
            } else if let boss = s.boss {
                if let first = s.pending {
                    targetId = s.balloons.first { $0.value * first == boss.value }?.id
                } else {
                    targetId = s.balloons.first { $0.correct }?.id
                }
            } else {
                targetId = s.balloons
                    .filter { $0.correct && $0.y >= 0.35 }
                    .sorted { $0.y != $1.y ? $0.y > $1.y : $0.id < $1.id }
                    .first?.id
            }
            if let id = targetId {
                s = Pop.tap(s, id: id, random: random)
                record()
                if s.phase != .running { break }
            }
        }
        run.score = s.score; run.wave = s.wave; run.hearts = s.hearts; run.maxCombo = s.maxCombo
        run.pops = s.pops; run.wrongTaps = s.wrongTaps; run.bossesDefeated = s.bossesDefeated
        run.over = s.phase == .over; run.trialLeft = s.trialLeft
        run.values = Array(run.values.prefix(30))
        return (run, s)
    }

    // MARK: - 난이도

    func testDifficultyCurve() {
        XCTAssertEqual((1...8).map { Pop.fallMs(wave: $0) }, [7000, 6000, 5500, 5000, 4500, 4000, 3500, 3500])
        XCTAssertEqual(Pop.fallMs(wave: 40), 3500, "하한 3.5초")
        XCTAssertEqual((1...5).map { Pop.concurrent(wave: $0) }, [3, 4, 5, 6, 6])
        XCTAssertEqual((1...4).map { Pop.wrongPercent(wave: $0) }, [40, 50, 60, 60])
        XCTAssertEqual((1...4).map { Pop.distractorCount(wave: $0) }, [4, 6, 9, 9])
        XCTAssertEqual((1...9).map { Pop.isBossWave($0) }, [false, false, true, false, false, true, false, false, true])
    }

    func testComboMultiplier() {
        XCTAssertEqual([0, 1, 4, 5, 9, 10, 11, 30].map { Pop.multiplier(combo: $0) }, [1, 1, 1, 2, 2, 3, 3, 3])
    }

    func testMixedWaveTablesFollowRoadmap() {
        XCTAssertEqual((1...9).map { Pop.waveTable(wave: $0, table: nil) }, [2, 5, 3, 4, 6, 9, 7, 8, 10])
        XCTAssertEqual((1...4).map { Pop.waveTable(wave: $0, table: 7) }, [7, 7, 7, 7])
    }

    // MARK: - 풍선 값

    func testDistractorPoolForSeven() {
        let pool = Pop.distractorPool(table: 7)
        XCTAssertEqual(pool.near, [6, 8, 13, 15, 20, 22, 27, 29, 34, 36, 41, 43, 48, 50, 55, 57, 62, 64, 69, 71, 76, 78, 83, 85, 90, 92, 97, 99, 104, 106, 111, 113, 118, 120, 125, 127, 132, 134])
        XCTAssertEqual(pool.neighbour, [12, 16, 18, 24, 30, 32, 40, 54, 60, 66, 72, 80, 88, 96, 102, 108, 114, 128, 136, 144, 152])
        XCTAssertEqual(pool.reversal, [19, 53, 65, 82, 89, 94, 211, 331])
    }

    func testDistractorPoolsNeverContainMultiples() {
        for t in 2...19 {
            let pool = Pop.distractorPool(table: t)
            let all = pool.near + pool.neighbour + pool.reversal + pool.far
            XCTAssertEqual(Set(all).count, all.count, "\(t)단: 후보끼리 겹침")
            XCTAssertTrue(all.allSatisfy { $0 >= 1 && $0 <= 361 && $0 % t != 0 }, "\(t)단")
            XCTAssertFalse(pool.near.isEmpty || pool.neighbour.isEmpty || pool.far.isEmpty, "\(t)단")
        }
    }

    func testSpawnedBalloonsMatchMission() {
        for t in [nil] + (2...19).map(Optional.some) {
            for seed in UInt32(1)...12 {
                _ = play(table: t, seed: seed, frames: 1600, policy: .sloppy) { s in
                    // 보스 작은 풍선은 제곱 짝꿍(6·6)이 있어 같은 수가 둘일 수 있다
                    guard s.boss == nil else { return }
                    let values = s.balloons.map(\.value)
                    XCTAssertEqual(Set(values).count, values.count, "화면에 같은 수가 둘: \(values)")
                    for b in s.balloons {
                        if b.correct {
                            XCTAssertEqual(b.value % s.waveTable, 0)
                            XCTAssertTrue((1...19).contains(b.value / s.waveTable))
                        } else {
                            XCTAssertNotEqual(b.value % s.waveTable, 0, "오답에 배수: \(b.value) / \(s.waveTable)단")
                            XCTAssertTrue((1...361).contains(b.value))
                        }
                    }
                }
            }
        }
    }

    func testFirstWaveUsesFarDistractors() {
        let far = Set(Pop.distractorPool(table: 7).far)
        _ = play(table: 7, seed: 5, frames: 400, policy: .perfect) { s in
            guard s.wave == 1 else { return }
            for b in s.balloons where !b.correct { XCTAssertTrue(far.contains(b.value), "\(b.value)") }
        }
    }

    // MARK: - 짝꿍 보스

    func testPairs() {
        XCTAssertEqual(Pop.pairs(of: 36), [PopPair(a: 2, b: 18), PopPair(a: 3, b: 12), PopPair(a: 4, b: 9), PopPair(a: 6, b: 6)])
        XCTAssertEqual(Pop.pairs(of: 24), [PopPair(a: 2, b: 12), PopPair(a: 3, b: 8), PopPair(a: 4, b: 6)])
        XCTAssertEqual(Pop.pairs(of: 8), [PopPair(a: 1, b: 8), PopPair(a: 2, b: 4)])
        XCTAssertEqual(Pop.pairs(of: 81), [PopPair(a: 9, b: 9)])
        XCTAssertEqual(Pop.pairs(of: 11), [PopPair(a: 1, b: 11)])
    }

    func testBossAlwaysHasCompletablePair() {
        for t in 2...19 {
            let random = rng(UInt32(t * 31))
            for _ in 0..<40 {
                let target = Pop.bossTarget(table: t, random: random)
                XCTAssertFalse(Pop.pairs(of: target).isEmpty, "보스 \(target)")
                let values = Pop.smallValues(target: target, random: random)
                XCTAssertEqual(values.count, Pop.smallSlots.count)
                let hasPair = values.indices.contains { i in
                    values.indices.contains { j in i != j && values[i] * values[j] == target }
                }
                XCTAssertTrue(hasPair, "보스 \(target): \(values)")
            }
        }
    }

    func testEveryPairIsAcceptedInAnyOrder() {
        let values = [3, 8, 4, 6, 5, 7]
        var s = bossScene(target: 24, values: values)
        s = Pop.tap(s, id: 101, random: rng(2))          // 3
        XCTAssertEqual(s.pending, 3)
        XCTAssertEqual(s.events, [.bossPick(id: 101, value: 3, points: 10)])
        XCTAssertEqual(s.balloons.count, 6, "빈자리를 채운다")
        s = Pop.tap(s, id: 102, random: rng(2))          // 8
        XCTAssertEqual(s.boss?.hitsLeft, 2)
        XCTAssertEqual(s.events, [.bossHit(a: 3, b: 8, points: 10, hitsLeft: 2)])
        XCTAssertNil(s.pending)

        // 다른 짝꿍, 거꾸로 (6 → 4)
        var again = bossScene(target: 24, values: values)
        again.boss = s.boss
        again = Pop.tap(again, id: 104, random: rng(3))  // 6
        again = Pop.tap(again, id: 103, random: rng(3))  // 4
        XCTAssertEqual(again.boss?.hitsLeft, 1)

        // 마지막 짝꿍(8 → 3)으로 보스를 물리치면 다음 웨이브
        var last = bossScene(target: 24, values: values)
        last.boss = again.boss
        last = Pop.tap(last, id: 102, random: rng(4))
        last = Pop.tap(last, id: 101, random: rng(4))
        XCTAssertNil(last.boss)
        XCTAssertEqual(last.bossesDefeated, 1)
        XCTAssertEqual(last.wave, 4)
        XCTAssertTrue(last.events.contains(.bossDefeated))
        XCTAssertTrue(last.events.contains(.waveCleared(wave: 3)))
    }

    func testSquarePairNeedsTwoBalloons() {
        var s = bossScene(target: 36, values: [6, 1, 2, 3, 5, 7])
        s = Pop.tap(s, id: 101, random: rng(5))          // 6 — 짝 6 이 화면에 없으니 빈자리를 6 으로
        XCTAssertEqual(s.pending, 6)
        XCTAssertEqual(s.balloons[0].value, 6)
        s = Pop.tap(s, id: s.balloons[0].id, random: rng(5))
        XCTAssertEqual(s.boss?.hitsLeft, 2)
    }

    func testBossWrongTaps() {
        var s = bossScene(target: 24, values: [3, 8, 4, 6, 5, 7])
        s = Pop.tap(s, id: 105, random: rng(6))          // 5 — 짝꿍에 안 듦
        XCTAssertEqual(s.events, [.wrongTap(id: 105, value: 5)])
        XCTAssertEqual(s.reloadMs, Pop.reloadMs)
        XCTAssertEqual(s.balloons.count, 6, "오답 풍선은 남는다")
        s = Pop.advance(s, dt: Pop.reloadMs)
        s = Pop.tap(s, id: 103, random: rng(6))          // 4
        s = Pop.tap(s, id: 101, random: rng(6))          // 3 — 4 × 3 ≠ 24
        XCTAssertEqual(s.events, [.wrongTap(id: 101, value: 3)])
        XCTAssertEqual(s.pending, 4, "첫 짝꿍은 그대로")
        XCTAssertEqual(s.combo, 0)
    }

    func testBossEscapeCostsHeartAndEndsWave() {
        var s = bossScene(target: 24, values: [3, 8, 4, 6, 5, 7])
        s = Pop.advance(s, dt: Pop.bossFallMs)
        XCTAssertEqual(s.hearts, Pop.maxHearts - 1)
        XCTAssertEqual(s.events.first, .bossEscaped)
        XCTAssertTrue(s.events.contains(.waveCleared(wave: 3)))
        XCTAssertEqual(s.wave, 4)
    }

    // MARK: - 판정

    func testComboScoring() {
        var s = scene(table: 7, balloons: (0..<5).map { balloon(10 + $0, 7 * ($0 + 1), correct: true, x: Double($0) * 0.2 + 0.1) })
        var points: [Int] = []
        for id in 10..<15 {
            s = Pop.tap(s, id: id)
            if case let .popped(_, _, p, _) = s.events.first { points.append(p) }
        }
        XCTAssertEqual(points, [10, 10, 10, 10, 20])
        XCTAssertEqual(s.score, 60)
        XCTAssertEqual(s.combo, 5)

        var tenth = scene(table: 7, balloons: [balloon(1, 14, correct: true)])
        tenth.combo = 9
        tenth = Pop.tap(tenth, id: 1)
        XCTAssertEqual(tenth.events, [.popped(id: 1, value: 14, points: 30, golden: false)])
    }

    func testHeartLostOnlyWhenCorrectBalloonReachesRoof() {
        var s = scene(table: 7, balloons: [
            balloon(50, 14, correct: true, x: 0.1, y: 0.999),
            balloon(51, 15, correct: false, x: 0.3, y: 0.999),
            balloon(52, 21, correct: true, x: 0.5, y: 0.2),
        ])
        s.combo = 3
        s = Pop.advance(s, dt: 100)
        XCTAssertEqual(s.hearts, Pop.maxHearts - 1)
        XCTAssertEqual(s.events, [.missed(id: 50, value: 14), .passed(id: 51, value: 15)])
        XCTAssertEqual(s.handled, 1)
        XCTAssertEqual(s.combo, 0)
        XCTAssertEqual(s.balloons.map(\.id), [52])

        // 오답 탭은 하트를 깎지 않는다 — 콤보 초기화 + 재장전
        var w = scene(table: 7, balloons: [balloon(60, 15, correct: false), balloon(61, 21, correct: true, x: 0.1)])
        w.combo = 4
        w = Pop.tap(w, id: 60)
        XCTAssertEqual(w.hearts, Pop.maxHearts)
        XCTAssertEqual(w.combo, 0)
        XCTAssertEqual(w.reloadMs, 800)
        XCTAssertEqual(w.events, [.wrongTap(id: 60, value: 15)])
        w = Pop.tap(w, id: 61)
        XCTAssertEqual(w.events, [.reloading], "재장전 중 탭은 무시")
        XCTAssertEqual(w.score, 0)
        w = Pop.advance(w, dt: 800)
        w = Pop.tap(w, id: 61)
        XCTAssertEqual(w.score, 10)
    }

    func testGameOverWhenHeartsRunOut() {
        var s = scene(table: 3, balloons: [balloon(1, 6, correct: true, y: 0.999), balloon(2, 9, correct: true, x: 0.1, y: 0.999)])
        s.hearts = 1
        s = Pop.advance(s, dt: 50)
        XCTAssertEqual(s.phase, .over)
        XCTAssertEqual(s.hearts, 0, "0 아래로 내려가지 않는다")
        XCTAssertEqual(s.events.filter { $0 == .gameOver }.count, 1)
    }

    func testGoldenBalloonSlowsTime() {
        var s = scene(table: 5, balloons: [
            balloon(1, 10, correct: true, kind: .golden),
            balloon(2, 15, correct: true, x: 0.1, y: 0.1),
        ])
        s.wave = 2
        s = Pop.tap(s, id: 1)
        XCTAssertEqual(s.events, [.popped(id: 1, value: 10, points: 10, golden: true), .slowMoStarted])
        XCTAssertEqual(s.slowMs, Pop.slowDurationMs)
        s = Pop.advance(s, dt: 100)
        XCTAssertEqual(s.balloons[0].y, 0.1 + 50.0 / 6000.0, accuracy: 1e-12, "느려짐 동안 절반 속도")
        s = Pop.advance(s, dt: Pop.slowDurationMs)
        XCTAssertEqual(s.slowMs, 0)
    }

    func testGoldenOnlyFromWaveTwo() {
        let random = rng(77)
        let first = Pop.create(table: 6, random: random)
        XCTAssertEqual(first.queue.count, 6 + 4)
        XCTAssertFalse(first.queue.contains(.golden))

        // 웨이브 1 마지막 정답을 터뜨려 웨이브 2 로 — 정답 6(그중 황금 1) + 오답 6
        var s = started(first)
        s.handled = Pop.waveGoal - 1
        s.queue = []
        s.balloons = [balloon(900, 6, correct: true)]
        s = Pop.tap(s, id: 900, random: random)
        XCTAssertEqual(s.wave, 2)
        XCTAssertEqual(s.queue.count, 12)
        XCTAssertEqual(s.queue.filter { $0 == .golden }.count, 1)
    }

    // MARK: - 체험

    func testTrialEndsAfterFirstWave() {
        let (run, s) = play(table: 3, seed: 11, frames: 3000, policy: .perfect, trial: PremiumConfig.popTrialWaves)
        XCTAssertEqual(PremiumConfig.popTrialWaves, 1)
        XCTAssertTrue(run.over)
        XCTAssertEqual(run.wave, 1)
        XCTAssertEqual(run.trialLeft, 0)
        XCTAssertEqual(s.events.suffix(2), [.waveCleared(wave: 1), .trialEnded])
        XCTAssertEqual(run.pops, Pop.waveGoal)

        let (free, _) = play(table: 3, seed: 11, frames: 1200, policy: .perfect)
        XCTAssertNil(free.trialLeft)
        XCTAssertGreaterThanOrEqual(free.wave, 2, "체험이 아니면 다음 웨이브로")
    }

    func testHeartsRunningOutDuringTrialIsGameOver() {
        let (run, s) = play(table: 3, seed: 11, frames: 3000, policy: .lazy, trial: 1)
        XCTAssertTrue(run.over)
        XCTAssertEqual(run.hearts, 0)
        XCTAssertEqual(run.trialLeft, 1, "체험을 다 하기 전에 하트로 끝남")
        XCTAssertEqual(s.events.last, .gameOver)
    }

    // MARK: - 탭 판정

    func testTapResolution() {
        let s = scene(table: 7, balloons: [
            balloon(1, 14, correct: true, x: 0.5, y: 0.5),
            balloon(2, 21, correct: true, x: 0.5, y: 0.6),
            balloon(3, 28, correct: true, x: 0.1, y: 0.3),
            balloon(4, 35, correct: true, x: 0.2, y: 0.3),
        ])
        XCTAssertEqual(Pop.target(s, x: 0.5, y: 0.55, aspect: 1), 2, "겹치면 지붕에 가까운 풍선")
        XCTAssertEqual(Pop.target(s, x: 0.5, y: 0.51, aspect: 1), 1, "확실히 가까우면 가까운 풍선")
        XCTAssertEqual(Pop.target(s, x: 0.15, y: 0.3, aspect: 1), 3, "같은 높이면 id 작은 풍선")
        XCTAssertNil(Pop.target(s, x: 0.9, y: 0.1, aspect: 1), "반경 밖")
        XCTAssertEqual(Pop.target(s, x: 0.5, y: 0.4, aspect: 1), 1)
        XCTAssertNil(Pop.target(s, x: 0.5, y: 0.4, aspect: 2), "세로 차이는 aspect 만큼 커진다")
        XCTAssertNil(Pop.target(s, x: .nan, y: 0.5, aspect: 1))

        let tapped = Pop.tap(s, x: 0.5, y: 0.55, aspect: 1)
        XCTAssertEqual(tapped.events, [.popped(id: 2, value: 21, points: 10, golden: false)])
        XCTAssertEqual(Pop.tap(s, x: 0.9, y: 0.1, aspect: 1).events, [])
    }

    func testBossIsNotATapTarget() {
        let s = bossScene(target: 24, values: [3, 8, 4, 6, 5, 7])
        XCTAssertNil(Pop.target(s, x: Pop.bossX, y: 0, aspect: 1.4))
        XCTAssertEqual(Pop.target(s, x: 0.27, y: 0.58, aspect: 1.4), 102)
    }

    func testIntroBlocksTapsAndMovement() {
        var s = Pop.create(table: 4, random: rng(3))
        XCTAssertEqual(s.introMs, Pop.introMs)
        XCTAssertEqual(s.mission, .multiples(table: 4))
        s = Pop.advance(s, dt: Pop.introMs - 1)
        XCTAssertTrue(s.balloons.isEmpty)
        s = Pop.advance(s, dt: 1)
        XCTAssertEqual(s.events, [.waveStarted(wave: 1)])
        s = Pop.advance(s, dt: 16, random: rng(3))
        XCTAssertEqual(s.balloons.count, 1, "안내가 끝나면 첫 풍선")
    }

    // MARK: - 기록

    func testBestKeys() {
        XCTAssertEqual(Pop.bestKey(nil), "all")
        XCTAssertEqual(Pop.bestKey(7), "7")
        XCTAssertEqual(Pop.sanitizeBests(["all": 120, "7": 80, "1": 5, "x": 9, "3": -1]), ["all": 120, "7": 80])
        XCTAssertEqual(Persistence.popBestKey, "gugu.pop.best.v1")
    }

    // MARK: - 골든 벡터 (Android PopTests.kt 와 같은 값)

    func testGoldenTable7Perfect() {
        let (run, _) = play(table: 7, seed: 42, frames: 2400, policy: .perfect)
        XCTAssertEqual(run, Run(
            score: 1640, wave: 10, hearts: 3, maxCombo: 59, pops: 59, wrongTaps: 0, bossesDefeated: 3,
            over: false, trialLeft: nil,
            values: [35, 289, 28, 98, 7, 119, 244, 150, 28, 228, 91, 42, 89, 43, 102, 19, 63, 20, 49, 136, 98, 42, 1, 7, 19, 14, 8, 11, 3, 18],
            missions: [.multiples(table: 7), .pairBoss(target: 98), .multiples(table: 7), .pairBoss(target: 77),
                       .multiples(table: 7), .pairBoss(target: 49), .multiples(table: 7)]))
    }

    func testGoldenMixedSloppy() {
        let (run, _) = play(table: nil, seed: 2026, frames: 2400, policy: .sloppy)
        XCTAssertEqual(run, Run(
            score: 610, wave: 10, hearts: 3, maxCombo: 7, pops: 55, wrongTaps: 45, bossesDefeated: 3,
            over: false, trialLeft: nil,
            values: [75, 177, 8, 183, 22, 8, 295, 36, 12, 18, 65, 35, 53, 28, 20, 108, 60, 78, 55, 57, 11, 90, 17, 15, 1, 9, 14, 19, 18, 15],
            missions: [.multiples(table: 2), .multiples(table: 5), .pairBoss(target: 15), .multiples(table: 4),
                       .multiples(table: 6), .pairBoss(target: 135), .multiples(table: 7), .multiples(table: 8),
                       .pairBoss(target: 190), .multiples(table: 11)]))
    }

    func testGoldenTable9Lazy() {
        let (run, _) = play(table: 9, seed: 9, frames: 2400, policy: .lazy)
        XCTAssertEqual(run, Run(
            score: 0, wave: 1, hearts: 0, maxCombo: 0, pops: 0, wrongTaps: 0, bossesDefeated: 0,
            over: true, trialLeft: nil,
            values: [273, 54, 63, 256, 99, 153, 144],
            missions: [.multiples(table: 9)]))
    }
}
