package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.AnswerRecord
import site.smap.gugudan.core.GivenAnswer
import site.smap.gugudan.core.Problem
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.SeededRandom
import site.smap.gugudan.core.games.ActionReject
import site.smap.gugudan.core.games.GameRandom
import site.smap.gugudan.core.games.ProblemOrder
import site.smap.gugudan.core.games.RisingCloud
import site.smap.gugudan.core.games.RoundClock
import site.smap.gugudan.core.games.RoundPhase
import site.smap.gugudan.core.games.StairsJumpResult
import site.smap.gugudan.core.games.StairsQuestion
import site.smap.gugudan.core.games.StairsSession
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A 구구 계단(계단 점프) 순수 규칙.
 * Unity 시험판 PrototypeSessionTests.StairsSessionTests 이식 + PlayMode 규칙성(1,000회 채점·쉬는 동안 정지)
 * + 2.9.0 달라진 규칙(시작 단 2~19·topStep·보기 최댓값·보물 뒤 2~9) + 골든 벡터
 * + 2.9.0 문제 순서(순서대로 = 기존 규칙, 무작위 = 문제마다 2~9단 × 1~topStep).
 * iOS StairsTests.swift 와 같은 기대값 — 한쪽을 바꾸면 양쪽을 함께 바꿀 것.
 * 이 규칙은 설정 「곱하는 수」(Problems.maxB)를 읽지 않는다 — topStep 을 생성 때 받는다.
 */
class StairsTests {

    // 도우미

    private fun rng(seed: Int): () -> Double = SeededRandom.mulberry32(seed.toUInt())

    private fun session(seed: Int, topStep: Int = 9, firstTable: Int? = null): StairsSession =
        StairsSession(topStep, ProblemOrder.SEQUENTIAL, firstTable, rng(seed))

    private fun randomSession(seed: Int, topStep: Int = 9): StairsSession =
        StairsSession(topStep, ProblemOrder.RANDOM, random = rng(seed))

    private fun wrongLane(q: StairsQuestion): Int = (q.correctLane + 1) % StairsSession.LANES

    private fun correct(s: StairsSession): StairsJumpResult {
        val r = s.jump(s.current.correctLane)
        assertEquals(true, r.attempt?.correct)
        return r
    }

    /** 오답 → 공개가 끝날 때까지 흘림(같은 칸이 다시 나옴) */
    private fun wrong(s: StairsSession) {
        assertEquals(false, s.jump(wrongLane(s.current)).attempt?.correct)
        s.tick(RoundClock.REVEAL_SEC + 0.01)
    }

    /** 구름이 서 있는 칸에 닿을 때까지 시간을 흘린다(무한 반복 방지 상한 포함). 흘린 시간을 돌려준다 */
    private fun tickUntilCaught(s: StairsSession, step: Double = 0.05): Double {
        var t = 0.0
        var i = 0
        while (i < 100_000 && !s.caught) {
            s.tick(step)
            t += step
            i += 1
        }
        assertTrue(s.caught, "먹구름에 잡혀야 함")
        return t
    }

    /** 끝난 판의 채점 무결성: 문제마다 최대 1번 채점, 마지막 하나 말고는 빠짐없이 채점, 채점 값이 규칙과 일치 */
    private fun assertIntegrity(s: StairsSession) {
        val attempts = s.attempts
        val ids = attempts.map { it.questionId }
        assertEquals(ids.size, ids.toSet().size, "같은 문제 두 번 채점")
        assertEquals(attempts.size, s.climbed + s.wrongCount)
        val unjudged = (0 until s.issuedCount).toSet() - ids.toSet()
        assertTrue(unjudged.size <= 1, "채점 없이 사라진 문제 ${unjudged.sorted()}")
        unjudged.firstOrNull()?.let { assertEquals(s.current.id, it) }
        for ((i, a) in attempts.withIndex()) {
            assertEquals(i, a.id)
            assertEquals(a.given == a.problem.a * a.problem.b, a.correct)
            assertTrue(a.responseSec >= 0)
            assertEquals(
                AnswerRecord(a.problem.a, a.problem.b, a.correct, a.ms, GivenAnswer.Number(a.given)), a.answerRecord,
            )
        }
        assertEquals(attempts.size, s.answerRecords.size)
    }

    // 보기

    @Test fun choicesAllFactsAllTopStepsAreValid() {
        for (seed in 0 until 40) {
            val random = rng(seed)
            for (topStep in Problems.MAX_B_OPTIONS) {
                for (a in StairsSession.FIRST_TABLES) {
                    for (b in 1..topStep) {
                        val p = Problem(a, b)
                        val standing = a * (b - 1)
                        val c = StairsSession.makeChoices(p, standing, topStep, random)
                        assertEquals(3, c.size)
                        assertEquals(3, c.toSet().size, "$p 중복 $c")
                        assertTrue(a * b in c)
                        assertFalse(standing in c, "$p 서 있는 숫자 $standing 가 보기에 있음")
                        val maxValue = StairsSession.maxChoice(a, topStep)
                        assertTrue(c.all { it in 1..maxValue }, "$p top $topStep: $c")
                    }
                }
            }
        }
    }

    /** 보기 최댓값 = 단 × topStep + 단 (원본 99 고정 대체). 2 × 9 = 18 의 후보 27(+곱하는 수)은 최댓값에 따라 들어오고 빠진다 */
    @Test fun maxChoiceFollowsTableAndTopStep() {
        assertEquals(70, StairsSession.maxChoice(7, 9))
        assertEquals(26, StairsSession.maxChoice(2, 12))
        assertEquals(380, StairsSession.maxChoice(19, 19))

        val p = Problem(2, 9)
        fun everSeen(value: Int, topStep: Int): Boolean =
            (0 until 200).any { seed -> value in StairsSession.makeChoices(p, 16, topStep, rng(seed)) }
        assertFalse(everSeen(27, 9), "최댓값 20")
        assertFalse(everSeen(27, 12), "최댓값 26")
        assertTrue(everSeen(27, 15), "최댓값 32 — 후보로 들어옴")
        assertTrue(everSeen(20, 9), "다음 배수(단 × 10)는 최댓값과 같아 들어옴")
    }

    // 오르기·보물 (원본)

    @Test fun correctClimbsOneStepAndNextQuestionIsImmediate() {
        val s = session(3, firstTable = 7)
        assertEquals(Problem(7, 1), s.current.problem)
        assertEquals(0, s.current.standing)
        val r = s.jump(s.current.correctLane)
        assertTrue(r.accepted)
        assertEquals(true, r.attempt?.correct)
        assertEquals(1, s.climbed)
        assertEquals(Problem(7, 2), s.current.problem)
        assertEquals(7, s.current.standing)
        assertFalse(s.currentJudged)
        assertEquals(RoundPhase.RUNNING, s.clock.phase)
    }

    @Test fun climbingToTopGivesTreasureAndSwitchesTable() {
        val s = session(5, firstTable = 4)
        for (step in 1..9) {
            assertEquals(step == 9, correct(s).clearedTable, "×$step")
        }
        assertEquals(1, s.treasures)
        assertNotEquals(4, s.table)
        assertTrue(s.table in StairsSession.RANDOM_TABLES)
        assertEquals(1, s.step)
        assertEquals(0, s.current.standing)
        assertEquals(9, s.climbed)
    }

    @Test fun wrongRevealsThenRetriesSameStepWithNewQuestionId() {
        val s = session(9, firstTable = 6)
        correct(s)  // 6×1 → 6×2
        val q = s.current
        val r = s.jump(wrongLane(q))
        assertTrue(r.accepted)
        assertEquals(false, r.attempt?.correct)
        assertEquals(1, s.wrongCount)
        assertEquals(ActionReject.REVEALING, s.jump(q.correctLane).reject, "공개 중 입력은 채점하지 않음")
        assertEquals(2, s.attempts.size)
        s.tick(RoundClock.REVEAL_SEC + 0.01)
        assertNotEquals(q.id, s.current.id)
        assertEquals(q.problem, s.current.problem, "같은 칸 재도전")
        assertEquals(2, s.step)
        assertEquals(1, s.climbed)
    }

    // 먹구름 (원본)

    @Test fun invalidLaneAndAfterCaughtAreRejected() {
        val s = session(1)
        assertEquals(ActionReject.INVALID, s.jump(3).reject)
        assertEquals(ActionReject.INVALID, s.jump(-1).reject)
        assertEquals(RoundPhase.WAITING, s.clock.phase, "잘못된 입력은 시계를 시작하지 않음")
        correct(s)  // 1칸, 구름은 3칸 아래
        s.tick(RisingCloud.secPerStep(1) * 3 - 0.01)
        assertEquals(RoundPhase.RUNNING, s.clock.phase)
        assertTrue(s.jump(s.current.correctLane).accepted, "잡히기 직전 입력은 채점")
        tickUntilCaught(s)
        assertEquals(RoundPhase.OVER, s.clock.phase)
        assertEquals(ActionReject.OVER, s.jump(s.current.correctLane).reject)
        assertEquals(2, s.climbed)
    }

    @Test fun cloudStaysStillUntilFirstAction() {
        val s = session(2)
        s.tick(30.0)
        assertEquals(-RisingCloud.START_GAP, s.cloud.top)
        assertEquals(RoundPhase.WAITING, s.clock.phase)
    }

    @Test fun noAnswerCaughtWhenCloudReachesStandingStep() {
        val s = session(4)
        correct(s)  // 서 있는 칸 1, 구름 -2 → 거리 3칸(따라잡기 없음)
        val expected = 3 * RisingCloud.secPerStep(1)
        s.tick(expected - 0.05)
        assertFalse(s.caught)
        s.tick(0.1)
        assertTrue(s.caught)
        assertEquals(RoundPhase.OVER, s.clock.phase)
        assertEquals(1.0, s.cloud.top, "잡히면 구름은 서 있는 칸에서 멈춤")
        val issued = s.issuedCount
        s.tick(10.0)
        assertEquals(1.0, s.cloud.top, "끝난 뒤에는 움직이지 않음")
        assertEquals(issued, s.issuedCount)
    }

    /** 입력 → 시간 순서: 잡히기 직전 누른 정답은 한 칸을 벌어 살아남는다 */
    @Test fun correctAnswerJustBeforeCatchSurvives() {
        val s = session(6)
        correct(s)
        s.tick(3 * RisingCloud.secPerStep(1) - 0.001)
        assertTrue(s.jump(s.current.correctLane).accepted)
        s.tick(0.1)
        assertFalse(s.caught)
        assertTrue(s.cloud.gap(s.climbed) > 0.9)
    }

    @Test fun wrongAnswerRevealDoesNotPauseCloud() {
        val s = session(8)
        correct(s)
        s.jump(wrongLane(s.current))
        val before = s.cloud.top
        s.tick(RoundClock.REVEAL_SEC * 0.5)
        assertTrue(s.clock.isRevealing)
        assertTrue(s.cloud.top > before, "오답 공개 중에도 구름은 차오름 = 오답의 대가")
    }

    @Test fun fastClimberCloudCatchesUpToMaxGap() {
        val s = session(10, firstTable = 3)
        repeat(10) { correct(s) }  // 10칸, 구름 -2 → 거리 12칸
        var t = 0.0
        while (s.cloud.gap(s.climbed) > RisingCloud.MAX_GAP) {
            s.tick(0.05)
            t += 0.05
        }
        val expected = (12 - RisingCloud.MAX_GAP) * RisingCloud.secPerStep(10) / RisingCloud.CATCH_UP_FACTOR
        assertEquals(expected, t, 0.1, "멀어진 만큼은 3배 속도로 따라옴")
        assertFalse(s.caught)
    }

    @Test fun steadyClimberKeepsGoingPastSixtySeconds() {
        val s = session(12)
        for (i in 0 until 60) {
            assertTrue(s.jump(s.current.correctLane).accepted, "${i}번째")
            s.tick(1.5)
        }
        assertEquals(RoundPhase.RUNNING, s.clock.phase, "시간 제한 없음 — 꾸준히 맞히면 계속")
        assertTrue(s.clock.elapsed > RoundClock.DURATION_SEC)
        assertEquals(6, s.treasures)
    }

    @Test fun cloudSpeedsUpWithHeightDownToFloor() {
        assertEquals(RisingCloud.START_SEC_PER_STEP, RisingCloud.secPerStep(0))
        assertTrue(RisingCloud.secPerStep(10) < RisingCloud.secPerStep(9))
        assertEquals(RisingCloud.MIN_SEC_PER_STEP, RisingCloud.secPerStep(1000))
    }

    // 2.9.0 달라진 규칙

    @Test fun randomStartIsTwoToNine() {
        val tables = mutableSetOf<Int>()
        for (seed in 0 until 300) {
            val s = session(seed)
            assertTrue(s.table in StairsSession.RANDOM_TABLES, "시작 단 ${s.table}")
            assertEquals(Problem(s.table, 1), s.current.problem)
            tables.add(s.table)
        }
        assertEquals((2..9).toSet(), tables)
    }

    @Test fun startAtNineteenWithTopStepTwelve() {
        val s = session(31, topStep = 12, firstTable = 19)
        for (step in 1..12) {
            assertEquals(Problem(19, step), s.current.problem)
            assertTrue(s.current.choices.all { it <= 19 * 12 + 19 })
            assertEquals(step == 12, correct(s).clearedTable, "×$step")
        }
        assertEquals(1, s.treasures)
        assertTrue(s.table in StairsSession.RANDOM_TABLES, "보물 뒤 다음 단은 2~9: ${s.table}")
        assertEquals(Problem(s.table, 1), s.current.problem)
    }

    @Test fun topStepNineteenClimbsToTimesNineteen() {
        val s = session(32, topStep = 19, firstTable = 2)
        for (step in 1..19) {
            assertEquals(Problem(2, step), s.current.problem)
            assertEquals(step == 19, correct(s).clearedTable, "×$step")
        }
        assertEquals(1, s.treasures)
        assertEquals(19, s.climbed)
        assertNotEquals(2, s.table)
    }

    /** 보물 뒤 다음 단: 2~9 무작위, 방금 끝낸 단은 빼고(원본 PickTable). 10단 이상에서 시작해도 다음은 2~9 */
    @Test fun afterTreasureNextTableIsTwoToNineAndDifferent() {
        for (first in StairsSession.FIRST_TABLES) {
            for (topStep in Problems.MAX_B_OPTIONS) {
                val s = session(first * 100 + topStep, topStep = topStep, firstTable = first)
                repeat(4) {
                    val finished = s.table
                    repeat(topStep) { correct(s) }
                    assertTrue(s.table in StairsSession.RANDOM_TABLES, "${first}단 시작: 다음 ${s.table}")
                    assertNotEquals(finished, s.table)
                    assertEquals(1, s.step)
                }
                assertEquals(4, s.treasures)
            }
        }
    }

    /** 잘못된 생성 인자는 조용히 고치지 않고 거절한다(Swift 는 precondition) */
    @Test fun invalidTopStepOrFirstTableIsRejected() {
        assertFailsWith<IllegalArgumentException> { StairsSession(10, ProblemOrder.SEQUENTIAL, null, rng(1)) }
        assertFailsWith<IllegalArgumentException> { StairsSession(9, ProblemOrder.SEQUENTIAL, 1, rng(1)) }
        assertFailsWith<IllegalArgumentException> { StairsSession(9, ProblemOrder.SEQUENTIAL, 20, rng(1)) }
        assertFailsWith<IllegalArgumentException>("무작위 순서는 시작 단을 받지 않음") { StairsSession(9, ProblemOrder.RANDOM, 7, rng(1)) }
        assertFailsWith<IllegalArgumentException> { StairsSession(10, ProblemOrder.RANDOM, null, rng(1)) }
        assertFailsWith<IllegalArgumentException> { StairsSession.randomProblem(10, null, rng(1)) }
        assertFailsWith<IllegalArgumentException> { StairsSession.makeChoices(Problem(3, 10), 27, 9, rng(1)) }
    }

    // 학습 기록 연결

    /** 응답 시간 = 문제가 나온 뒤 tick 으로 흘린 시간(첫 동작 전 대기 포함). 오답 뒤 재도전은 다시 나온 때부터 잰다 */
    @Test fun answerRecordsForLearning() {
        val s = session(40, firstTable = 7)
        s.tick(2.0)  // 첫 동작 전 2초 생각
        correct(s)
        s.tick(0.3)
        val shown = s.current
        val given = shown.choices[wrongLane(shown)]
        s.jump(wrongLane(shown))
        s.tick(RoundClock.REVEAL_SEC + 0.01)  // 공개 끝 → 같은 칸 다시
        s.tick(0.4)
        correct(s)
        assertEquals(
            listOf(
                AnswerRecord(7, 1, correct = true, ms = 2000, given = GivenAnswer.Number(7)),
                AnswerRecord(7, 2, correct = false, ms = 300, given = GivenAnswer.Number(given)),
                AnswerRecord(7, 2, correct = true, ms = 400, given = GivenAnswer.Number(14)),
            ),
            s.answerRecords,
        )
        assertEquals(Problem(7, 2), s.missToShow?.problem)
    }

    /** 쉬는 창 = 화면이 tick 을 부르지 않음. 그동안 먹구름·시계·응답 시간이 모두 멈춘다(원본 PausePlayTests) */
    @Test fun pauseFreezesCloudClockAndResponseTime() {
        val s = session(41)
        correct(s)  // 첫 점프 → 먹구름 출발
        s.tick(1.0)
        val top = s.cloud.top
        val elapsed = s.clock.elapsed
        // (쉬는 중: tick 없음 — 화면 첫 프레임의 dt 0 도 아무것도 바꾸지 않음)
        s.tick(0.0)
        assertEquals(top, s.cloud.top, "쉬는 동안 먹구름 멈춤")
        assertEquals(elapsed, s.clock.elapsed)
        s.tick(0.5)  // 계속 하기
        assertTrue(s.cloud.top > top, "계속 하면 다시 차오름")
        val r = correct(s)
        assertEquals(1500, r.attempt?.ms, "쉬는 동안은 응답 시간에 들어가지 않음")
        assertEquals(2, s.stars)
    }

    // 무작위 채점 무결성

    /**
     * 무작위 탭·시간 진행 수천 번(원본 RandomPlay): 받아들인 탭마다 채점 정확히 1번, 한 문제는 최대 1번 채점, 빠진 문제 없음.
     * 판마다 topStep·시작 단을 바꿔 달라진 규칙도 함께 돌린다
     */
    @Test fun randomPlayNoDuplicateOrMissingGrades() {
        for (seed in 1..5) {
            val driver = rng(seed * 7919)
            var accepted = 0
            var rejected = 0
            for (round in 0 until 10) {
                val firsts = listOf<Int?>(null, 19, 2, 13, null)
                val s = session(
                    seed * 100 + round, topStep = Problems.MAX_B_OPTIONS[round % 4],
                    firstTable = firsts[round % firsts.size],
                )
                var roundAccepted = 0
                var played = 0.0
                // 판에 시간 제한이 없으므로 120초 동안 무작위로 누른 뒤 손을 떼고 구름에 잡힐 때까지 흘린다
                var guardCount = 0
                while (s.clock.phase != RoundPhase.OVER && guardCount < 200_000) {
                    guardCount += 1
                    if (played < 120) {
                        val lane = -1 + GameRandom.index(5, driver)  // -1…3 (없는 칸 포함)
                        if (s.jump(lane).accepted) {
                            accepted += 1
                            roundAccepted += 1
                        } else {
                            rejected += 1
                        }
                    }
                    val dt = driver() * 0.25
                    s.tick(dt)
                    played += dt
                }
                assertTrue(s.caught)
                assertEquals(roundAccepted, s.attempts.size)
                assertIntegrity(s)
            }
            assertTrue(accepted > 1000, "충분히 많은 채점 (seed $seed)")
            assertTrue(rejected > 0)
        }
    }

    /**
     * 고정 프레임(1/15초)에 한 프레임 0~2번 탭, 1,000회 이상 채점(원본 PrototypePlayTests.ThousandTaps):
     * 탭마다 채점 0 또는 1번, 거절은 오답 공개 중일 때만, 문제당 최대 1번
     */
    @Test fun framePlayThousandJudgementsNoDuplicateOrMissing() {
        data class Config(val seed: Int, val topStep: Int, val first: Int?)
        val configs = listOf(Config(7, 9, null), Config(11, 12, 19), Config(2026, 19, 5))
        for (config in configs) {
            val driver = rng(config.seed)
            var judged = 0
            var rounds = 0
            var rejected = 0
            var s = session(config.seed + 500, topStep = config.topStep, firstTable = config.first)
            while (judged < 1000) {
                val before = s.attempts.size
                var acceptedThisFrame = 0
                repeat(GameRandom.index(3, driver)) {
                    val lane = if (driver() < 0.7) s.current.correctLane else GameRandom.index(3, driver)
                    val r = s.jump(lane)
                    if (r.accepted) {
                        acceptedThisFrame += 1
                    } else {
                        assertEquals(ActionReject.REVEALING, r.reject, "거절은 오답 공개 중일 때만")
                        rejected += 1
                    }
                }
                assertEquals(acceptedThisFrame, s.attempts.size - before)
                judged += acceptedThisFrame
                s.tick(1.0 / 15.0)
                // 시간 제한이 없으니 한 판에 120번 채점하면 손을 떼고 구름에 잡히게 해 판을 넘긴다
                if (s.attempts.size >= 120 && s.clock.phase != RoundPhase.OVER) tickUntilCaught(s)
                if (s.clock.phase == RoundPhase.OVER) {
                    assertIntegrity(s)
                    rounds += 1
                    s = session(config.seed + 500 + rounds, topStep = config.topStep, firstTable = config.first)
                }
            }
            assertTrue(rounds >= 1, "seed ${config.seed}")
            assertTrue(rejected > 0, "seed ${config.seed}")
        }
    }

    // 골든 벡터 (iOS StairsTests.swift 와 같은 시드·같은 기대값 — 한쪽을 바꾸면 양쪽을 함께)
    //
    // 스크립트: "c" = 정답 칸으로 점프, "w" = 정답 오른쪽 칸((correctLane + 1) % 3)으로 점프 → tick(0.61) 으로 공개 끝.
    // 기록: 생성 직후 + 글자마다 그 뒤의 지금 문제를 "단x칸 [왼쪽, 가운데, 오른쪽]" 으로.
    // 기대값은 C# 원본을 그대로 옮긴 참조 구현(2.9.0 규칙 반영)과 대조해 확인했다(iOS 쪽 기록).

    private fun trace(seed: Int, topStep: Int, firstTable: Int?, script: String): Pair<List<String>, StairsSession> =
        trace(session(seed, topStep, firstTable), script) { "${it.problem.a}x${it.problem.b} ${it.choices}" }

    /** 무작위 순서 — 서 있는 발판(st)이 문제마다 직전 정답이라 줄에 함께 적는다: "단x곱하는수 st=발판 [보기]" */
    private fun randomTrace(seed: Int, topStep: Int, script: String): Pair<List<String>, StairsSession> =
        trace(randomSession(seed, topStep), script) { "${it.problem.a}x${it.problem.b} st=${it.standing} ${it.choices}" }

    private fun trace(s: StairsSession, script: String, format: (StairsQuestion) -> String): Pair<List<String>, StairsSession> {
        val lines = mutableListOf(format(s.current))
        for (ch in script) {
            if (ch == 'c') correct(s) else wrong(s)
            lines.add(format(s.current))
        }
        return lines to s
    }

    private fun stats(s: StairsSession): List<Int> =
        listOf(s.climbed, s.treasures, s.wrongCount, s.stars, s.streak, s.bestStreak)

    /** 홈 시작: 2~9단 무작위, 곱하는 수 9. 8칸 맞힘 → ×9 에서 한 번 틀림 → 보물 → 다음 단 */
    @Test fun goldenRandomStartTopStep9() {
        val (questions, s) = trace(seed = 42, topStep = 9, firstTable = null, script = "ccccccccwccc")
        assertEquals(
            listOf(
                "6x1 [12, 6, 7]", "6x2 [14, 12, 18]", "6x3 [18, 15, 17]", "6x4 [25, 28, 24]", "6x5 [30, 25, 31]",
                "6x6 [35, 36, 37]", "6x7 [48, 43, 42]", "6x8 [40, 56, 48]", "6x9 [55, 60, 54]",
                "6x9 [54, 45, 55]",  // 오답 뒤 같은 칸, 보기만 다시
                "2x1 [1, 2, 4]", "2x2 [5, 4, 3]", "2x3 [7, 6, 5]",
            ),
            questions,
        )
        assertEquals(listOf(11, 1, 1, 18, 3, 8), stats(s))
    }

    /** 단 시트 시작: 19단, 곱하는 수 12. 꼭대기 ×12 → 보물 → 2~9단 */
    @Test fun goldenStart19TopStep12() {
        val (questions, s) = trace(seed = 2026, topStep = 12, firstTable = 19, script = "cccccccccccccc")
        assertEquals(
            listOf(
                "19x1 [19, 38, 20]", "19x2 [36, 37, 38]", "19x3 [54, 60, 57]", "19x4 [80, 95, 76]",
                "19x5 [114, 94, 95]", "19x6 [120, 133, 114]", "19x7 [126, 133, 134]", "19x8 [171, 144, 152]",
                "19x9 [180, 171, 170]", "19x10 [189, 190, 209]", "19x11 [209, 228, 208]", "19x12 [228, 240, 229]",
                "3x1 [4, 3, 2]", "3x2 [6, 5, 7]", "3x3 [8, 9, 10]",
            ),
            questions,
        )
        assertEquals(listOf(14, 1, 0, 23, 14, 14), stats(s))
    }

    /** 단 시트 시작: 13단, 곱하는 수 19 */
    @Test fun goldenStart13TopStep19() {
        val (questions, s) = trace(seed = 7, topStep = 19, firstTable = 13, script = "cwcc")
        assertEquals(
            listOf(
                "13x1 [13, 12, 14]", "13x2 [26, 39, 28]", "13x2 [24, 28, 26]", "13x3 [42, 36, 39]", "13x4 [52, 53, 51]",
            ),
            questions,
        )
        assertEquals(listOf(3, 0, 1, 3, 2, 2), stats(s))
    }

    // 무작위 순서 (2.9.0 시작 창 — iOS StairsTests.swift 와 같은 시드·같은 기대값)

    /** 무작위 골든: 문제마다 2~9단 × 1~9, 서 있는 발판 = 직전 정답(첫 칸 0), 오답 뒤 같은 식·같은 발판에 보기만 다시 (참조 구현과 대조) */
    @Test fun goldenRandomOrderSeed42TopStep9() {
        val (questions, s) = randomTrace(seed = 42, topStep = 9, script = "cccwccc")
        assertEquals(
            listOf(
                "6x5 st=0 [29, 25, 30]", "8x5 st=30 [48, 45, 40]", "7x6 st=40 [48, 42, 36]", "4x1 st=42 [3, 4, 5]",
                "4x1 st=42 [4, 3, 5]",  // 오답 뒤 같은 식, 보기만 다시
                "4x5 st=4 [21, 15, 20]", "3x3 st=20 [12, 8, 9]", "4x1 st=9 [4, 8, 5]",
            ),
            questions,
        )
        assertEquals(listOf(6, 0, 1, 6, 3, 3), stats(s))
        assertEquals(7, s.step)
    }

    /** 무작위 골든: 곱하는 수 12 — 12칸째에 보물(+5), 단은 그대로 문제마다 무작위 (참조 구현과 대조) */
    @Test fun goldenRandomOrderSeed2026TopStep12() {
        val (questions, s) = randomTrace(seed = 2026, topStep = 12, script = "cccccccccccc")
        assertEquals(
            listOf(
                "5x4 st=0 [20, 19, 24]", "4x1 st=20 [3, 4, 5]", "3x4 st=4 [16, 15, 12]", "8x12 st=12 [95, 104, 96]",
                "4x8 st=96 [24, 36, 32]", "8x9 st=32 [81, 72, 80]", "9x3 st=72 [28, 26, 27]", "6x11 st=27 [66, 72, 77]",
                "6x9 st=66 [55, 45, 54]", "9x3 st=54 [27, 28, 30]", "2x12 st=27 [24, 26, 23]", "2x11 st=24 [21, 22, 23]",
                "9x12 st=22 [117, 109, 108]",
            ),
            questions,
        )
        assertEquals(listOf(12, 1, 0, 21, 12, 12), stats(s))
        assertEquals(1, s.step, "보물 뒤 1번째 칸부터 다시")
    }

    /** 무작위 식 고르기: 단 index(8) → 곱하는 수 index(topStep) 순서로 난수 2개, 직전 식과 같으면 둘 다 다시 */
    @Test fun randomProblemDrawOrderAndRedrawOfSameProblem() {
        var used = 0
        val values = ArrayDeque(listOf(0.26, 0.23, 0.26, 0.23, 0.39, 0.23))   // 4×3 → 4×3(직전과 같음) → 5×3
        val random: () -> Double = {
            used += 1
            values.removeFirst()
        }
        assertEquals(Problem(4, 3), StairsSession.randomProblem(9, previous = null, random = random))
        assertEquals(2, used)
        assertEquals(Problem(5, 3), StairsSession.randomProblem(9, previous = Problem(4, 3), random = random))
        assertEquals(6, used)
    }

    /** 무작위: 단 2~9·곱하는 수 1~topStep 안에서만, 직전과 같은 식은 바로 이어서 안 나오고, 범위가 모두 나온다 */
    @Test fun randomOrderRangeNoImmediateRepeatAndCoverage() {
        for (topStep in Problems.MAX_B_OPTIONS) {
            val s = randomSession(300 + topStep, topStep)
            val tables = mutableSetOf<Int>()
            val multipliers = mutableSetOf<Int>()
            var previous: Problem? = null
            repeat(topStep * 40) {
                val p = s.current.problem
                assertTrue(p.a in StairsSession.RANDOM_TABLES, "top $topStep: $p")
                assertTrue(p.b in 1..topStep, "top $topStep: $p")
                assertNotEquals(previous, p, "직전과 같은 식")
                tables.add(p.a)
                multipliers.add(p.b)
                previous = p
                correct(s)
            }
            assertEquals(StairsSession.RANDOM_TABLES.toSet(), tables, "top $topStep")
            assertEquals((1..topStep).toSet(), multipliers, "top $topStep")
            assertEquals(s.current.problem.a, s.table, "table 은 지금 문제의 단")
        }
    }

    /** 무작위: 서 있는 발판 = 직전 정답(첫 칸 0), 보기에 서 있는 숫자는 없음(정답과 같을 때 말고), 오답이면 같은 식·같은 발판 */
    @Test fun randomOrderStandingIsPreviousAnswer() {
        val s = randomSession(17)
        assertEquals(0, s.current.standing, "첫 칸은 출발(0)")
        for (i in 0 until 200) {
            val q = s.current
            val answer = q.problem.a * q.problem.b
            if (q.standing != answer) assertFalse(q.standing in q.choices, "${q.problem} 발판 ${q.standing} ${q.choices}")
            assertTrue(q.choices.all { it in 1..StairsSession.maxChoice(q.problem.a, 9) })
            if (i % 7 == 3) {
                wrong(s)
                assertEquals(q.problem, s.current.problem)
                assertEquals(q.standing, s.current.standing)
                assertNotEquals(q.id, s.current.id)
            } else {
                correct(s)
                assertEquals(answer, s.current.standing, "서 있는 발판 = 방금 맞힌 답")
            }
        }
    }

    /** 무작위: topStep 칸을 맞힐 때마다 보물 +1(별 +5) — 틀린 칸은 세지 않고, step 은 1~topStep 을 돈다 */
    @Test fun randomOrderTreasureEveryTopStepClimbs() {
        for (topStep in Problems.MAX_B_OPTIONS) {
            val s = randomSession(topStep, topStep)
            for (i in 1..topStep * 3) {
                if (i % 4 == 0) wrong(s)
                assertEquals((i - 1) % topStep + 1, s.step)
                val r = correct(s)
                assertEquals(i % topStep == 0, r.clearedTable, "top $topStep ${i}칸째")
                assertEquals(i / topStep, s.treasures)
            }
            assertEquals(topStep * 3, s.climbed)
            assertEquals(1, s.step)
            assertIntegrity(s)
        }
    }

    /** 무작위 판도 무작위로 누르면 채점 중복·누락 없음(먹구름에 잡힐 때까지) */
    @Test fun randomOrderRandomPlayNoDuplicateOrMissingGrades() {
        for (seed in 1..4) {
            val driver = rng(seed * 104_729)
            var accepted = 0
            for (round in 0 until 8) {
                val s = randomSession(seed * 1000 + round, topStep = Problems.MAX_B_OPTIONS[round % 4])
                var roundAccepted = 0
                var played = 0.0
                var guardCount = 0
                while (s.clock.phase != RoundPhase.OVER && guardCount < 200_000) {
                    guardCount += 1
                    if (played < 90) {
                        val lane = -1 + GameRandom.index(5, driver)  // -1…3 (없는 칸 포함)
                        if (s.jump(lane).accepted) {
                            accepted += 1
                            roundAccepted += 1
                        }
                    }
                    val dt = driver() * 0.25
                    s.tick(dt)
                    played += dt
                }
                assertTrue(s.caught)
                assertEquals(roundAccepted, s.attempts.size)
                assertIntegrity(s)
            }
            assertTrue(accepted > 500, "seed $seed")
        }
    }
}
