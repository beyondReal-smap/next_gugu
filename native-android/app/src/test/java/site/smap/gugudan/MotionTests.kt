package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.Problem
import site.smap.gugudan.core.games.MonsterKind
import site.smap.gugudan.core.games.StairsQuestion
import site.smap.gugudan.features.games.monster.MonsterStage
import site.smap.gugudan.features.games.shared.Motion
import site.smap.gugudan.features.games.stairs.GameRGB
import site.smap.gugudan.features.games.stairs.StairsStage
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 게임 생동감(계획서 game-juice) — 움직임 공식(Motion)의 경계값과, 그 공식을 쓰는 무대(Stage)가 언제 움직이고 언제 멈추는지.
 * iOS MotionTests.swift 와 같은 의미. 동작 줄이기(애니메이터 길이 배율 0)는 Stage 생성 인자로 확인한다.
 */
class MotionTests {

    private val frame = 1.0 / 60
    private val eps = 1e-9

    /** 0 → end 를 프레임 간격으로 */
    private fun sweep(end: Double, step: Double = frame): List<Double> =
        generateSequence(0.0) { it + step }.takeWhile { it <= end + eps }.toList()

    // MARK: 공식 — 2. 점프 늘어남·기울기

    @Test fun jumpStretchIsIdentityAtTakeoffAndLanding() {
        for (t in listOf(0.0, 1.0, Motion.JUMP_STRETCH_END, -0.1, 1.2)) {
            val s = Motion.jumpStretch(t)
            assertEquals(1.0, s.x, eps, "jumpT=$t")
            assertEquals(1.0, s.y, eps, "jumpT=$t")
        }
    }

    @Test fun jumpStretchPeaksOnTheWayUp() {
        val peak = Motion.jumpStretch(Motion.JUMP_STRETCH_END / 2)
        assertEquals(1 + Motion.JUMP_STRETCH_Y, peak.y, eps)
        assertEquals(1 - Motion.JUMP_STRETCH_X, peak.x, eps)
        for (t in sweep(1.0, 0.01)) {
            val s = Motion.jumpStretch(t)
            assertTrue(s.y in 1.0 - eps..1 + Motion.JUMP_STRETCH_Y + eps, "jumpT=$t y=${s.y}")
            assertTrue(s.x in 1 - Motion.JUMP_STRETCH_X - eps..1.0 + eps, "jumpT=$t x=${s.x}")
        }
    }

    @Test fun jumpTiltLeansTowardTravelAndIsZeroAtEnds() {
        for (dir in -1..1) {
            assertEquals(0.0, Motion.jumpTilt(0.0, dir), eps)
            assertEquals(0.0, Motion.jumpTilt(1.0, dir), eps)
        }
        assertEquals(Motion.JUMP_TILT_DEG, Motion.jumpTilt(0.5, 1), eps)    // 오른쪽 = 시계 방향
        assertEquals(-Motion.JUMP_TILT_DEG, Motion.jumpTilt(0.5, -1), eps)
        for (t in sweep(1.0, 0.01)) {
            assertEquals(0.0, Motion.jumpTilt(t, 0), eps)                    // 위로만 오르면 기울지 않음
            assertTrue(abs(Motion.jumpTilt(t, 1)) <= Motion.JUMP_TILT_DEG + eps)
        }
    }

    // MARK: 공식 — 1. 숨쉬기

    @Test fun breathStaysInRangeAndStretchesOppositeWays() {
        var minY = 2.0
        var maxY = 0.0
        for (t in sweep(Motion.BREATH_SEC * 2)) {
            val b = Motion.breath(t)
            assertTrue(abs(b.y - 1) <= Motion.BREATH_Y + eps, "t=$t y=${b.y}")
            assertTrue(abs(b.x - 1) <= Motion.BREATH_X + eps, "t=$t x=${b.x}")
            // 세로로 늘면 가로로 줄어든다
            assertTrue((b.y - 1) * (b.x - 1) <= eps, "t=$t")
            minY = minOf(minY, b.y)
            maxY = maxOf(maxY, b.y)
        }
        assertTrue(maxY > 1 + Motion.BREATH_Y * 0.99 && minY < 1 - Motion.BREATH_Y * 0.99, "한 주기 안에 끝까지 숨 쉼")
    }

    @Test fun breathRepeatsEveryPeriodAndPhaseShifts() {
        for (t in listOf(0.0, 0.3, 1.1)) {
            assertEquals(Motion.breath(t).y, Motion.breath(t + Motion.BREATH_SEC).y, 1e-9)
            // 반 주기 위상 = 반대로
            assertEquals(2 - Motion.breath(t).y, Motion.breath(t, phase = 0.5).y, 1e-9)
        }
    }

    // MARK: 공식 — 5. 깡총

    @Test fun hopStartsAndEndsOnTheGround() {
        for (t in listOf(0.0, 1.0, -0.5, 1.5)) {
            val h = Motion.hop(t)
            assertEquals(0.0, h.dy, eps, "t=$t")
            assertEquals(1.0, h.sx, eps, "t=$t")
            assertEquals(1.0, h.sy, eps, "t=$t")
        }
    }

    @Test fun hopCrouchesThenStretchesInAirThenSquashesOnLanding() {
        val crouch = Motion.hop(Motion.HOP_CROUCH_END / 2)
        assertEquals(0.0, crouch.dy, eps)
        assertEquals(1 - Motion.HOP_SQUASH_Y, crouch.sy, eps)
        val apex = Motion.hop((Motion.HOP_CROUCH_END + Motion.HOP_AIR_END) / 2)
        assertEquals(Motion.HOP_HEIGHT, apex.dy, eps)
        assertEquals(1 + Motion.HOP_STRETCH_Y, apex.sy, eps)
        val land = Motion.hop((Motion.HOP_AIR_END + 1) / 2)
        assertEquals(0.0, land.dy, eps)
        assertEquals(1 - Motion.HOP_SQUASH_Y, land.sy, eps)
        // 이어지는 움직임 — 구간이 바뀌는 곳에서도 튀지 않음
        var prev = Motion.hop(0.0)
        for (t in sweep(1.0, 0.005)) {
            val h = Motion.hop(t)
            assertTrue(abs(h.dy - prev.dy) < 0.02 && abs(h.sy - prev.sy) < 0.01, "t=$t")
            assertTrue(h.dy in -eps..Motion.HOP_HEIGHT + eps)
            prev = h
        }
    }

    // MARK: 공식 — 4. 처치 타격감

    @Test fun impactShakeIsZeroWhenNothingLeftAndStaysWithinAmplitude() {
        for (left in listOf(0.0, -0.05)) {
            val s = Motion.impactShake(left)
            assertEquals(0.0, s.dx, eps)
            assertEquals(0.0, s.dy, eps)
        }
        for (left in sweep(Motion.IMPACT_SEC, 0.001)) {
            val s = Motion.impactShake(left)
            assertTrue(abs(s.dx) <= Motion.IMPACT_AMPLITUDE + eps && abs(s.dy) <= Motion.IMPACT_AMPLITUDE + eps, "left=$left")
            // 남은 시간에 비례해 줄어듦(감쇠)
            assertTrue(hypot(s.dx, s.dy) <= Motion.IMPACT_AMPLITUDE * left / Motion.IMPACT_SEC * 1.5 + eps, "left=$left")
        }
    }

    @Test fun impactFlashFadesOverFlashTime() {
        assertEquals(1.0, Motion.impactFlash(0.0), eps)
        assertEquals(0.5, Motion.impactFlash(Motion.IMPACT_FLASH_SEC / 2), eps)
        assertEquals(0.0, Motion.impactFlash(Motion.IMPACT_FLASH_SEC), eps)
        assertEquals(0.0, Motion.impactFlash(1.0), eps)
    }

    // MARK: 공식 — 6. 홈 카드·옷장 숨쉬기

    @Test fun cardBreathStaysInRangeAndCardsAreOutOfPhase() {
        for (t in sweep(Motion.CARD_BREATH_SEC)) {
            val b = Motion.cardBreath(t)
            assertTrue(abs(b.lift) <= Motion.CARD_BREATH_LIFT + eps)
            assertTrue(abs(b.scale - 1) <= Motion.CARD_BREATH_SCALE + eps)
        }
        assertEquals(0.0, Motion.cardBreath(0.0).lift, eps)
        // 두 번째 카드는 같은 순간 다른 자리
        assertEquals(Motion.CARD_BREATH_LIFT, Motion.cardBreath(0.0, Motion.CARD_BREATH_PHASE_STEP).lift, eps)
    }

    // MARK: 구구 계단 무대

    private fun stairs(reduceMotion: Boolean = false): StairsStage =
        StairsStage(reduceMotion).apply { reset("출발", accessory = null, best = 0) }

    private fun StairsStage.nextRow() =
        showNextRow(StairsQuestion(id = standingHeight + 1, problem = Problem(2, 1), standing = 0, choices = listOf(1, 2, 3), issuedAt = 0.0), GameRGB.WALL)

    private fun StairsStage.run(seconds: Double) {
        repeat((seconds / frame).toInt()) { update(frame, caught = false) }
    }

    @Test fun standingGuguBreathesFromTheFeet() {
        val stage = stairs()
        val ys = (0 until (Motion.BREATH_SEC / frame).toInt()).map {
            stage.update(frame, caught = false)
            stage.characterScale.y
        }
        assertTrue(ys.max() > 1.03 && ys.min() < 0.97, "서 있는 동안 세로 1±0.035 로 숨 쉼: ${ys.min()}..${ys.max()}")
        assertEquals(0.0, stage.characterTilt, eps)
    }

    @Test fun jumpStretchesAndTiltsTowardTheLaneThenSettles() {
        for ((lane, sign) in listOf(2 to 1.0, 0 to -1.0, 1 to 0.0)) {
            val stage = stairs()
            stage.nextRow()
            stage.land(lane)
            stage.run(StairsStage.JUMP_SEC * 0.25)   // 올라가는 중
            assertTrue(stage.characterScale.y > 1.1, "lane=$lane 늘어남 ${stage.characterScale.y}")
            stage.run(StairsStage.JUMP_SEC * 0.25)
            val tilt = stage.characterTilt
            if (sign == 0.0) assertEquals(0.0, tilt, eps) else assertTrue(tilt * sign > 5, "lane=$lane 기울기 $tilt")
            stage.run(StairsStage.JUMP_SEC)          // 착지
            assertEquals(0.0, stage.characterTilt, eps)
        }
    }

    @Test fun landingPuffsDustThatFadesAway() {
        val stage = stairs()
        stage.nextRow()
        assertEquals(0, stage.dust.count { it.alive })
        stage.land(2)
        stage.run(StairsStage.JUMP_SEC + frame * 2)
        val puffs = stage.dust.filter { it.alive }
        assertEquals(StairsStage.DUST_COUNT, puffs.size)
        // 발 양옆으로 퍼짐
        assertTrue(puffs.any { it.x < stage.characterX } && puffs.any { it.x > stage.characterX })
        stage.run(StairsStage.DUST_SEC)
        assertEquals(0, stage.dust.count { it.alive })
    }

    @Test fun reduceMotionStopsBreathingStretchAndTiltButKeepsDust() {
        val stage = stairs(reduceMotion = true)
        repeat(60) {
            stage.update(frame, caught = false)
            assertEquals(1.0, stage.characterScale.y, eps)
        }
        stage.nextRow()
        stage.land(2)
        repeat((StairsStage.JUMP_SEC / frame).toInt() - 1) {
            stage.update(frame, caught = false)
            assertEquals(1.0, stage.characterScale.y, eps)
            assertEquals(0.0, stage.characterTilt, eps)
        }
        stage.run(frame * 3)
        assertEquals(StairsStage.DUST_COUNT, stage.dust.count { it.alive })   // 착지 먼지는 사건 반응이라 둠
    }

    @Test fun noBreathingWhileShaking() {
        val stage = stairs()
        stage.nextRow()
        stage.run(0.4)   // 숨쉬기 위상을 0 이 아닌 곳으로
        stage.miss(lane = 0, correctLane = 1)
        stage.update(frame, caught = false)
        assertEquals(1.0, stage.characterScale.y, eps)
    }

    // MARK: 구구 몬스터 무대

    private fun monster(reduceMotion: Boolean = false): MonsterStage =
        MonsterStage(reduceMotion).apply { spawn(hp = 12, kind = MonsterKind.GREEN, drop = false) }

    /** 깡총 높이를 seconds 동안 프레임마다 모은다 */
    private fun MonsterStage.hops(seconds: Double, from: Double = 0.0): List<Double> =
        (0 until (seconds / frame).toInt()).map {
            update(frame, time = from + it * frame)
            hop.dy
        }

    @Test fun waitingMonsterHopsAboutEveryThreeSeconds() {
        val stage = monster()
        val before = stage.hops(Motion.HOP_EVERY_SEC - 0.1)
        assertTrue(before.all { it == 0.0 }, "3초 전에는 안 뜀")
        val during = stage.hops(0.1 + Motion.HOP_SEC + 0.05)
        assertTrue(during.max() > Motion.HOP_HEIGHT * 0.95, "깡총 높이 ${during.max()}")
        assertEquals(0.0, during.last(), eps)   // 다시 그루터기 위
        val next = stage.hops(Motion.HOP_EVERY_SEC + 0.1)
        assertTrue(next.max() > 0, "다음 깡총")
    }

    @Test fun monsterDoesNotHopWhileDroppingShakingOrPopping() {
        val stage = monster()
        stage.spawn(hp = 6, kind = MonsterKind.GREEN, drop = true)
        // 오답이 계속 오면 흔들림 동안 깡총 시계가 흐르지 않는다
        val dys = (0 until 20).flatMap {
            stage.hit()
            stage.hops(MonsterStage.SHAKE_SEC)
        }
        assertTrue(dys.all { it == 0.0 })
        // 처치 연출 동안도
        stage.defeat()
        assertTrue(stage.hops(MonsterStage.POP_SEC - frame).all { it == 0.0 })
    }

    @Test fun defeatShakesTheScreenBrieflyAndFlashesWhite() {
        val stage = monster()
        stage.defeat()
        stage.update(frame, time = 0.0)
        assertTrue(hypot(stage.impact.dx, stage.impact.dy) > 0, "처치 순간 흔들림")
        assertTrue(stage.popFlash > 0.5, "처치 순간 하얗게 ${stage.popFlash}")
        stage.hops(Motion.IMPACT_SEC)
        assertEquals(0.0, stage.impact.dx, eps)
        assertEquals(0.0, stage.impact.dy, eps)
        assertEquals(0.0, stage.popFlash, eps)
    }

    @Test fun wrongAnswerDoesNotShakeTheScreen() {
        val stage = monster()
        stage.hit()
        repeat(10) {
            stage.update(frame, time = it * frame)
            assertEquals(0.0, stage.impact.dx, eps)
            assertEquals(0.0, stage.impact.dy, eps)
        }
    }

    @Test fun reduceMotionStopsHopAndScreenShakeButKeepsFlash() {
        val stage = monster(reduceMotion = true)
        assertTrue(stage.hops(Motion.HOP_EVERY_SEC * 3).all { it == 0.0 })
        stage.defeat()
        stage.update(frame, time = 0.0)
        assertEquals(0.0, stage.impact.dx, eps)
        assertEquals(0.0, stage.impact.dy, eps)
        assertTrue(stage.popFlash > 0.5)
    }
}
