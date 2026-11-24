import XCTest
@testable import Gugu

/// 생동감(juice) 공식(Motion)과 그것을 쓰는 무대(StairsStage·MonsterStage).
/// 공식: 경계값(점프 0·1 에서 늘어남 1·기울기 0, 깡총 시작·끝 제자리, 흔들림 남은 시간 0 이면 0)과 범위.
/// 무대: 서 있으면 숨쉬고 점프 중엔 가는 쪽으로 기울며 착지에 먼지, 몬스터는 기다리면 깡총·처치에 흔들림 —
/// 동작 줄이기면 반복·장식 움직임(숨쉬기·늘어남·기울기·깡총·화면 흔들림)이 멈추고 먼지·번쩍은 남는다.
final class MotionTests: XCTestCase {

    private let frame = 1.0 / 60
    private let eps = 1e-9

    // MARK: - 공식: 점프

    func testJumpStretchIsIdentityAtTakeoffLandingAndWhileFalling() {
        for t in [0.0, 0.5, 0.75, 1.0] {
            let s = Motion.jumpStretch(t)
            XCTAssertEqual(s.sx, 1, accuracy: eps, "t=\(t)")
            XCTAssertEqual(s.sy, 1, accuracy: eps, "t=\(t)")
        }
    }

    func testJumpStretchPeaksWhileRising() {
        // 계획서: 세로 최대 +18%, 가로 −10%
        let peak = Motion.jumpStretch(0.25)
        XCTAssertEqual(peak.sy, 1.18, accuracy: eps)
        XCTAssertEqual(peak.sx, 0.90, accuracy: eps)
        for i in 1..<50 {
            let s = Motion.jumpStretch(Double(i) / 100)
            XCTAssertGreaterThan(s.sy, 1)
            XCTAssertLessThan(s.sx, 1)
            XCTAssertLessThanOrEqual(s.sy, 1.18 + eps)
        }
    }

    func testJumpTiltLeansTowardDirectionAndIsZeroAtEnds() {
        for dir in [-1, 0, 1] {
            XCTAssertEqual(Motion.jumpTilt(0, dir: dir), 0, accuracy: eps)
            XCTAssertEqual(Motion.jumpTilt(1, dir: dir), 0, accuracy: eps)
        }
        XCTAssertEqual(Motion.jumpTilt(0.5, dir: 1), 10, accuracy: eps)
        XCTAssertEqual(Motion.jumpTilt(0.5, dir: -1), -10, accuracy: eps)
        XCTAssertEqual(Motion.jumpTilt(0.5, dir: 0), 0, accuracy: eps)
        for i in 1..<100 {
            let tilt = Motion.jumpTilt(Double(i) / 100, dir: 1)
            XCTAssertGreaterThan(tilt, 0)
            XCTAssertLessThanOrEqual(tilt, 10 + eps)
        }
    }

    // MARK: - 공식: 숨쉬기

    func testBreathStaysInRangeWithOppositeAxesAndRepeatsEvery1_6s() {
        let start = Motion.breath(time: 0)
        XCTAssertEqual(start.sx, 1, accuracy: eps)
        XCTAssertEqual(start.sy, 1, accuracy: eps)
        var maxY = 0.0
        var minY = 2.0
        for i in 0..<320 {
            let t = Double(i) * 0.01
            let b = Motion.breath(time: t)
            XCTAssertGreaterThanOrEqual(b.sy, 1 - 0.035 - eps)
            XCTAssertLessThanOrEqual(b.sy, 1 + 0.035 + eps)
            XCTAssertGreaterThanOrEqual(b.sx, 1 - 0.025 - eps)
            XCTAssertLessThanOrEqual(b.sx, 1 + 0.025 + eps)
            // 세로가 늘면 가로는 준다
            XCTAssertLessThanOrEqual((b.sy - 1) * (b.sx - 1), eps)
            let later = Motion.breath(time: t + 1.6)
            XCTAssertEqual(later.sy, b.sy, accuracy: 1e-9)
            maxY = max(maxY, b.sy)
            minY = min(minY, b.sy)
        }
        XCTAssertEqual(maxY, 1.035, accuracy: 1e-4)
        XCTAssertEqual(minY, 0.965, accuracy: 1e-4)
    }

    // MARK: - 공식: 깡총

    func testHopRestsAtStartAndEndAndRisesToQuarterUnit() {
        for t in [-0.1, 0.0, 1.0, 1.5] {
            let h = Motion.hop(t)
            XCTAssertEqual(h.dy, 0, accuracy: eps, "t=\(t)")
            XCTAssertEqual(h.sx, 1, accuracy: eps, "t=\(t)")
            XCTAssertEqual(h.sy, 1, accuracy: eps, "t=\(t)")
        }
        XCTAssertEqual(Motion.hop(0.5).dy, 0.25, accuracy: eps)
        for i in 0...100 {
            XCTAssertGreaterThanOrEqual(Motion.hop(Double(i) / 100).dy, 0)
            XCTAssertLessThanOrEqual(Motion.hop(Double(i) / 100).dy, 0.25 + eps)
        }
    }

    func testHopSquashesBeforeTakeoffStretchesInAirSquashesOnLanding() {
        let crouch = Motion.hop(0.1)
        XCTAssertEqual(crouch.dy, 0, accuracy: eps)
        XCTAssertGreaterThan(crouch.sx, 1)
        XCTAssertLessThan(crouch.sy, 1)
        let air = Motion.hop(0.5)
        XCTAssertGreaterThan(air.sy, 1)
        XCTAssertLessThan(air.sx, 1)
        let landing = Motion.hop(0.9)
        XCTAssertEqual(landing.dy, 0, accuracy: eps)
        XCTAssertLessThan(landing.sy, 1)
    }

    // MARK: - 공식: 처치 흔들림·번쩍

    func testImpactShakeIsZeroWhenNoTimeLeftAndBoundedOtherwise() {
        for left in [0.0, -0.05] {
            let s = Motion.impactShake(left: left)
            XCTAssertEqual(s.dx, 0)
            XCTAssertEqual(s.dy, 0)
        }
        var moved = 0.0
        for i in 1...12 {
            let s = Motion.impactShake(left: Double(i) * 0.01)
            XCTAssertLessThanOrEqual(abs(s.dx), 0.12 + eps)
            XCTAssertLessThanOrEqual(abs(s.dy), 0.12 + eps)
            moved = max(moved, abs(s.dx), abs(s.dy))
        }
        XCTAssertGreaterThan(moved, 0.05, "처치 직후에는 눈에 띄게 흔들려야 함")
    }

    func testImpactFlashFadesFromWhiteToNothingIn80ms() {
        XCTAssertEqual(Motion.impactFlash(elapsed: 0), 1)
        XCTAssertEqual(Motion.impactFlash(elapsed: 0.04), 0.5, accuracy: eps)
        XCTAssertEqual(Motion.impactFlash(elapsed: 0.08), 0)
        XCTAssertEqual(Motion.impactFlash(elapsed: 0.3), 0)
        XCTAssertEqual(Motion.impactFlash(elapsed: -0.01), 0)
    }

    // MARK: - 공식: 그림 숨쉬기(홈 카드·옷장)

    func testCardBreathMoves2ptAndNeighbourCardsAreOutOfStep() {
        for i in 0..<240 {
            let b = Motion.cardBreath(time: Double(i) * 0.01, phase: 0)
            XCTAssertLessThanOrEqual(abs(b.lift), 2 + eps)
            XCTAssertGreaterThanOrEqual(b.scale, 0.98 - eps)
            XCTAssertLessThanOrEqual(b.scale, 1.02 + eps)
        }
        XCTAssertEqual(Motion.cardBreath(time: 0.6, phase: 0).lift, 2, accuracy: eps)   // 주기 2.4초의 1/4
        // 계단 카드(위상 0)가 제자리를 지날 때 몬스터 카드(0.25)는 가장 높이 떠 있음
        XCTAssertEqual(Motion.cardBreath(time: 0, phase: 0).lift, 0, accuracy: eps)
        XCTAssertEqual(Motion.cardBreath(time: 0, phase: Motion.cardBreathPhaseStep).lift, 2, accuracy: eps)
    }

    // MARK: - 구구 계단 무대

    private func makeStairs(reduceMotion: Bool = false) -> StairsStage {
        var stage = StairsStage()
        stage.reduceMotion = reduceMotion
        stage.reset(startLabel: "출발", accessory: nil, best: 0)
        return stage
    }

    /// 2 × 1 = 2, 정답 칸을 고른다
    private func nextRow(_ stage: inout StairsStage, correctLane: Int) {
        var choices = [3, 4, 5]
        choices[correctLane] = 2
        stage.showNextRow(StairsQuestion(id: 1, problem: Problem(a: 2, b: 1), standing: 0, choices: choices, issuedAt: 0),
                          color: .wall)
    }

    private func run(_ stage: inout StairsStage, sec: Double) {
        for _ in 0..<Int((sec / frame).rounded()) { stage.update(frame, caught: false) }
    }

    func testStandingGuguBreathesFromFeet() {
        var stage = makeStairs()
        var ys: [Double] = []
        for _ in 0..<96 {   // 1.6초 = 한 주기
            stage.update(frame, caught: false)
            ys.append(stage.characterScale.y)
        }
        XCTAssertGreaterThan(ys.max()!, 1.03)
        XCTAssertLessThan(ys.min()!, 0.97)
        XCTAssertEqual(stage.characterTilt, 0)
    }

    func testJumpLeansTowardLaneThenLandsUprightWithDust() {
        var stage = makeStairs()
        nextRow(&stage, correctLane: 2)
        stage.land(lane: 2)   // 가운데 → 오른쪽
        run(&stage, sec: 0.14)
        XCTAssertGreaterThan(stage.characterTilt, 5, "오른쪽으로 기울어야 함")
        XCTAssertTrue(stage.dust.allSatisfy { !$0.alive }, "공중에서는 먼지 없음")
        run(&stage, sec: 0.16)
        XCTAssertEqual(stage.characterTilt, 0, "착지하면 똑바로")
        let alive = stage.dust.filter(\.alive)
        XCTAssertEqual(alive.count, 6)
        XCTAssertTrue(alive.contains { $0.x < stage.characterX } && alive.contains { $0.x > stage.characterX }, "발 양옆으로")
        run(&stage, sec: 0.32)
        XCTAssertTrue(stage.dust.allSatisfy { !$0.alive }, "0.3초 뒤 사라짐")
    }

    func testJumpLeftLeansLeftAndStraightUpDoesNotLean() {
        var left = makeStairs()
        nextRow(&left, correctLane: 0)
        left.land(lane: 0)
        run(&left, sec: 0.14)
        XCTAssertLessThan(left.characterTilt, -5)

        var up = makeStairs()
        nextRow(&up, correctLane: 1)
        up.land(lane: 1)
        run(&up, sec: 0.07)
        XCTAssertEqual(up.characterTilt, 0)
        XCTAssertGreaterThan(up.characterScale.y, 1.1, "바로 위로 뛰어도 늘어남은 있음")
    }

    func testReduceMotionStopsBreathingStretchAndTiltButKeepsDust() {
        var stage = makeStairs(reduceMotion: true)
        for _ in 0..<96 {
            stage.update(frame, caught: false)
            XCTAssertEqual(stage.characterScale.x, 1)
            XCTAssertEqual(stage.characterScale.y, 1)
        }
        nextRow(&stage, correctLane: 2)
        stage.land(lane: 2)
        run(&stage, sec: 0.14)
        XCTAssertEqual(stage.characterTilt, 0)
        XCTAssertEqual(stage.characterScale.y, 1)
        run(&stage, sec: 0.16)
        XCTAssertFalse(stage.dust.filter(\.alive).isEmpty, "먼지는 짧은 사건 반응이라 남김")
    }

    // MARK: - 구구 몬스터 무대

    private func makeMonster(reduceMotion: Bool = false) -> MonsterStage {
        var stage = MonsterStage()
        stage.reduceMotion = reduceMotion
        stage.spawn(hp: 12, kind: .green, drop: false)
        return stage
    }

    /// 몬스터 높이에서 지금 있는 통통(sin) 몫을 뺀 것 = 깡총 높이
    private func hopHeight(_ stage: MonsterStage, time: Double) -> Double {
        stage.y - (MonsterStage.monsterY + sin(time * 2.6) * 0.12)
    }

    /// time 부터 sec 동안 흘리며 가장 높은 깡총을 잰다
    private func maxHop(_ stage: inout MonsterStage, from time: inout Double, sec: Double) -> Double {
        var best = 0.0
        for _ in 0..<Int((sec / frame).rounded()) {
            time += frame
            stage.update(frame, time: time)
            best = max(best, hopHeight(stage, time: time))
        }
        return best
    }

    func testWaitingMonsterHopsAboutEvery3Seconds() {
        var stage = makeMonster()
        var time = 0.0
        XCTAssertEqual(maxHop(&stage, from: &time, sec: 2.9), 0, accuracy: eps, "3초 전에는 안 뜀")
        XCTAssertGreaterThan(maxHop(&stage, from: &time, sec: 0.5), 0.2, "3초쯤 한 번 뜀")
        // 다시 3초는 깡총이 끝난 뒤부터 센다(3.35초쯤 다음 깡총)
        XCTAssertEqual(maxHop(&stage, from: &time, sec: 2.4), 0, accuracy: eps, "다음 3초까지 쉼")
        XCTAssertGreaterThan(maxHop(&stage, from: &time, sec: 1.2), 0.2)
    }

    func testWrongAnswerShakeCancelsAndRestartsHopWait() {
        var stage = makeMonster()
        var time = 0.0
        _ = maxHop(&stage, from: &time, sec: 2.5)
        stage.hit()
        XCTAssertEqual(maxHop(&stage, from: &time, sec: 3.2), 0, accuracy: eps, "흔들림이 끝난 뒤 다시 3초를 셈")
        XCTAssertGreaterThan(maxHop(&stage, from: &time, sec: 0.6), 0.2)
    }

    func testDefeatShakesCameraBrieflyAndFlashesWhite() {
        var stage = makeMonster()
        stage.update(frame, time: frame)
        stage.defeat()
        XCTAssertEqual(stage.pop?.whiteFlash, 1)
        stage.update(frame, time: 2 * frame)
        let shake = stage.cameraShake
        XCTAssertGreaterThan(max(abs(shake.x), abs(shake.y)), 0.01, "처치 직후 화면이 흔들림")
        for i in 0..<8 { stage.update(frame, time: Double(3 + i) * frame) }
        XCTAssertEqual(stage.cameraShake.x, 0)
        XCTAssertEqual(stage.cameraShake.y, 0)
        XCTAssertEqual(stage.pop?.whiteFlash, 0, "번쩍은 0.08초 안에 끝남")
    }

    func testWrongAnswerDoesNotShakeCamera() {
        var stage = makeMonster()
        stage.hit()
        stage.update(frame, time: frame)
        XCTAssertEqual(stage.cameraShake.x, 0)
        XCTAssertEqual(stage.cameraShake.y, 0)
    }

    func testReduceMotionStopsHopAndScreenShakeButKeepsFlash() {
        var stage = makeMonster(reduceMotion: true)
        var time = 0.0
        XCTAssertEqual(maxHop(&stage, from: &time, sec: 7), 0, accuracy: eps)
        stage.defeat()
        stage.update(frame, time: time + frame)
        XCTAssertEqual(stage.cameraShake.x, 0)
        XCTAssertEqual(stage.cameraShake.y, 0)
        XCTAssertGreaterThan(stage.pop?.whiteFlash ?? 0, 0)
    }

    func testRoundResetKeepsReduceMotion() {
        var stage = makeMonster(reduceMotion: true)
        stage.reset()
        XCTAssertTrue(stage.reduceMotion)
    }
}
