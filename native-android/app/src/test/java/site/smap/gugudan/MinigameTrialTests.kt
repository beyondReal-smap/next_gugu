package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 움직이며 놀기 무료 체험 — 정한 문제 수를 다 풀면 다음 문제를 내지 않고 판을 끝낸다 (iOS MinigameTrialTests 미러).
 * 체험 수(trialLeft)는 "다음 문제로 넘어가는 순간" 줄어들고, 하트를 다 쓰면 그 자리에서 끝난다.
 */
class MinigameTrialTests {

    @Test fun trialLengthIsSingleSourced() {
        assertEquals(3, PremiumConfig.MINIGAME_TRIAL_QUESTIONS)
        assertEquals(3, Runner.create(2, trial = PremiumConfig.MINIGAME_TRIAL_QUESTIONS).trialLeft)
        assertNull(Runner.create(2).trialLeft, "trial 을 주지 않으면 제한 없음")
    }

    // 구구 점프

    /** 정답을 골라 장애물을 넘긴다 (정답 뒤 속도로 한 번에 지나가도록 큰 dt) */
    private fun clear(s: RunnerState): RunnerState =
        Runner.advance(Runner.answer(s, s.question.answer), 10_000.0)

    /** 오답을 골라 부딪히고 피격 연출까지 흘린다 */
    private fun miss(s: RunnerState): RunnerState {
        val wrong = s.question.choices.first { it != s.question.answer }
        val hit = Runner.advance(Runner.answer(s, wrong), 10_000.0)
        return Runner.advance(hit, Runner.HIT_DURATION_MS)
    }

    @Test fun runnerTrialEndsAfterLastQuestion() {
        var s = Runner.create(2, trial = 3)
        s = clear(s)
        assertEquals(RunnerPhase.RUNNING, s.phase)
        assertEquals(2, s.trialLeft)
        s = miss(s)
        assertEquals(RunnerPhase.RUNNING, s.phase)
        assertEquals(1, s.trialLeft)
        s = clear(s)
        assertEquals(RunnerPhase.OVER, s.phase)
        assertEquals(0, s.trialLeft)
        assertEquals(2, s.score)
        assertEquals(Runner.MAX_LIVES - 1, s.lives)
        assertEquals(2, s.round, "마지막 문제에서 멈춘다 — 새 문제를 뽑지 않는다")
        assertEquals(RunnerPhase.OVER, Runner.advance(s, 16.0).phase)
    }

    @Test fun runnerHeartsOutEndsBeforeTrialCount() {
        val s = miss(miss(miss(Runner.create(2, trial = 3))))
        assertEquals(RunnerPhase.OVER, s.phase)
        assertEquals(0, s.lives)
        assertEquals(1, s.trialLeft, "하트를 다 쓴 판은 체험 수를 줄이지 않고 그 자리에서 끝난다")
    }

    @Test fun runnerWithoutTrialKeepsRunning() {
        var s = Runner.create(2)
        repeat(4) { s = clear(s) }
        assertEquals(RunnerPhase.RUNNING, s.phase)
        assertEquals(4, s.score)
        assertNull(s.trialLeft)
    }

    // 구구 바구니

    /** 판정이 난 상태를 만들고 피드백 시간을 흘린다 */
    private fun settle(s: BasketState, outcome: BasketOutcome): BasketState {
        val judged = s.copy(
            outcome = outcome, feedbackMs = 10.0,
            lives = if (outcome == BasketOutcome.CORRECT) s.lives else s.lives - 1,
        )
        return Basket.advance(judged, 20.0)
    }

    @Test fun basketTrialEndsAfterLastQuestion() {
        var s = Basket.create(2, trial = 2)
        s = settle(s, BasketOutcome.CORRECT)
        assertEquals(RunnerPhase.RUNNING, s.phase)
        assertEquals(1, s.trialLeft)
        assertEquals(1, s.round)
        s = settle(s, BasketOutcome.MISSED)
        assertEquals(RunnerPhase.OVER, s.phase)
        assertEquals(0, s.trialLeft)
        assertEquals(1, s.round, "마지막 문제에서 멈춘다 — 새 열매를 내지 않는다")
    }

    @Test fun basketHeartsOutEndsBeforeTrialCount() {
        val s = settle(Basket.create(2, trial = 3).copy(lives = 1), BasketOutcome.WRONG)
        assertEquals(RunnerPhase.OVER, s.phase)
        assertEquals(3, s.trialLeft)
    }

    @Test fun basketWithoutTrialKeepsRunning() {
        var s = Basket.create(2)
        repeat(4) { s = settle(s, BasketOutcome.CORRECT) }
        assertEquals(RunnerPhase.RUNNING, s.phase)
        assertNull(s.trialLeft)
    }

    // 구구 레인

    /** 판정을 마친 게이트가 다음 장애물 구간 경계(NEXT_CYCLE_X)를 넘게 한다 */
    private fun passGate(s: LaneRunnerState): LaneRunnerState {
        val question = RunnerQuestion(3, 4, listOf(12, 10, 14))
        return LaneRunner.advance(
            s.copy(
                segment = LaneSegment.QUIZ, question = question, outcome = LaneOutcome.CORRECT,
                gate = LaneGate(LaneRunner.NEXT_CYCLE_X + 1, 600.0, question.choices),
            ),
            16.0,
        )
    }

    @Test fun laneTrialCountsGatesOnly() {
        // 장애물 피하기는 문제가 아니다 — 체험 수가 줄지 않는다
        var s = LaneRunner.advance(
            LaneRunner.create(3, trial = 2).copy(
                obstacles = listOf(LaneObstacle(0, 0, LaneRunner.JUDGE_X + 1, LaneObstacleKind.ROCK)),
            ),
            16.0,
        )
        assertEquals(1, s.dodged)
        assertEquals(2, s.trialLeft)

        s = passGate(s)
        assertEquals(RunnerPhase.RUNNING, s.phase)
        assertEquals(LaneSegment.DODGE, s.segment)
        assertEquals(1, s.trialLeft)
        s = passGate(s)
        assertEquals(RunnerPhase.OVER, s.phase)
        assertEquals(0, s.trialLeft)
    }

    @Test fun laneWithoutTrialKeepsRunning() {
        var s = LaneRunner.create(3)
        repeat(4) { s = passGate(s) }
        assertEquals(RunnerPhase.RUNNING, s.phase)
        assertNull(s.trialLeft)
    }
}
