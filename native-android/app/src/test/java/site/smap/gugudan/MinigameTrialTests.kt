package site.smap.gugudan

import org.junit.After
import org.junit.Before
import org.junit.Test
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import site.smap.gugudan.core.*
import site.smap.gugudan.core.games.MonsterSession
import site.smap.gugudan.core.games.ProblemOrder
import site.smap.gugudan.core.games.RoundPhase
import site.smap.gugudan.core.games.StairsSession
import site.smap.gugudan.features.games.monster.MonsterEngine
import site.smap.gugudan.features.games.shared.GameInsets
import site.smap.gugudan.features.games.shared.GameViewport
import site.smap.gugudan.features.games.stairs.StairsCamera
import site.smap.gugudan.features.games.stairs.StairsEngine
import site.smap.gugudan.features.paywall.MinigameTrial
import site.smap.gugudan.services.Sound
import site.smap.gugudan.store.MinigameStart
import site.smap.gugudan.store.PlayRewardsStorage
import site.smap.gugudan.store.PlayRewardsStore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 게임 무료 체험 (iOS MinigameTrialTests 미러) — 2.9.0 진입점은 구구 계단·구구 몬스터:
 * 맞힌 문제(계단 칸·물리친 몬스터)가 정한 수가 되면 판을 끝낸다. 체험 수(trialLeft)는 맞힐 때만 줄고(오답은 그대로),
 * 이용권(trial null)이면 끝없이 이어진다.
 * 체험 수 전에 잡히거나 시간이 다 되면 체험 끝이 아니라 일반 결과(화면은 trialFinished 일 때만 체험 종료 패널).
 */
class MinigameTrialTests {

    // 이 클래스의 기대값은 곱하는 수 상한 19 기준이다 — 다른 테스트가 바꿔 둔 전역 설정에 기대지 않게 매번 고정한다
    @Before fun fixMaxMultiplier() {
        Problems.maxB = 19
        Sound.setEnabled(false)   // JVM 테스트에는 AudioTrack 이 없다
    }

    @After fun restoreMaxMultiplier() { Problems.maxB = Problems.DEFAULT_MAX_B }

    @Test fun trialLengthIsSingleSourced() {
        assertEquals(10, PremiumConfig.MINIGAME_TRIAL_QUESTIONS)
        assertEquals(10, MinigameTrial.questions)
        assertEquals(10, MinigameTrial.limit(isPremium = false))
        assertNull(MinigameTrial.limit(isPremium = true), "이용권이면 제한 없음")
        assertEquals("10문제 체험", MinigameTrial.badge(strings, isPremium = false))
        assertNull(MinigameTrial.badge(strings, isPremium = true))
        assertEquals(10, stairs(MinigameTrial.limit(isPremium = false)).trialLeft)
        assertNull(stairs(null).trialLeft, "trial 을 주지 않으면 제한 없음")
    }

    // 구구 계단·구구 몬스터 — 화면 엔진(StairsEngine·MonsterEngine)으로 확인

    private val viewport = GameViewport(Size(1280f, 2856f), GameInsets(top = 156f, bottom = 63f))
    private val frame = 1.0 / 60
    private val strings = TestStrings(AppLanguage.KO)

    private class MemoryRewardsStorage : PlayRewardsStorage {
        val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            values[key] = value
        }
        override fun quarantine(key: String, raw: String, cause: Throwable) = error("격리될 저장본이 없어야 함: $key")
    }

    private fun makeStore() = PlayRewardsStore(MemoryRewardsStorage(), key = "gugu.test.play", adventureKey = "gugu.test.adventure")

    private fun stairs(trial: Int?): StairsEngine {
        val engine = StairsEngine(
            MinigameStart.Table(3), ProblemOrder.SEQUENTIAL, makeStore(), strings, LearningSpy()::commit, topStep = 9,
            random = SeededRandom.mulberry32(11u),
        )
        engine.updateViewport(viewport)
        engine.start(trial)
        return engine
    }

    private fun monster(trial: Int?): MonsterEngine {
        val engine = MonsterEngine(
            MinigameStart.Default, ProblemOrder.RANDOM, makeStore(), strings, LearningSpy()::commit, random = SeededRandom.mulberry32(11u),
        )
        engine.updateViewport(viewport)
        engine.start(trial)
        return engine
    }

    private fun jump(engine: StairsEngine, correct: Boolean) {
        val q = engine.session.current
        val lane = if (correct) q.correctLane else (q.correctLane + 1) % StairsSession.LANES
        engine.enqueueTap(Offset(StairsCamera(viewport.size, 0.0).laneCenterX(lane), 1500f))
        engine.advance(frame)
        if (!correct) repeat(40) { engine.advance(frame) }   // 정답 공개 0.6초
    }

    private fun pick(engine: MonsterEngine, correct: Boolean) {
        val q = engine.session.current
        val second = if (correct) q.pairB else (0 until MonsterSession.CARD_COUNT).first {
            it != q.pairA && q.cards[q.pairA] * q.cards[it] != q.target.a * q.target.b
        }
        engine.enqueueTap(MonsterEngine.Cards.center(q.pairA, viewport))
        engine.enqueueTap(MonsterEngine.Cards.center(second, viewport))
        engine.advance(frame)
        if (!correct) repeat(40) { engine.advance(frame) }
    }

    /** 오답은 체험 수를 줄이지 않고, 세 번째로 맞힌 칸에서 판이 끝난다 */
    @Test fun stairsTrialCountsOnlyCorrectClimbs() {
        val engine = stairs(3)
        jump(engine, correct = true)
        jump(engine, correct = false)
        assertEquals(2, engine.trialLeft)
        assertFalse(engine.roundOver)
        jump(engine, correct = true)
        assertEquals(1, engine.trialLeft)
        jump(engine, correct = true)
        assertEquals(0, engine.trialLeft)
        assertTrue(engine.trialFinished)
        assertTrue(engine.roundOver)
        assertEquals(3, engine.session.climbed)
        assertEquals(1, engine.session.wrongCount)
    }

    /** 이용권이면 3칸을 넘어 계속 오른다 */
    @Test fun stairsWithoutTrialKeepsClimbing() {
        val engine = stairs(null)
        repeat(5) { jump(engine, correct = true) }
        assertFalse(engine.roundOver)
        assertEquals(5, engine.session.climbed)
        assertNull(engine.trialLeft)
    }

    /**
     * 체험 수 전에 먹구름에 잡히면 체험 끝이 아님(trialFinished false) → 화면은 일반 결과 창을 띄우고,
     * [다시 하기]는 같은 체험 수로 새 판을 연다 (2026-11-24 대표님 결정 "잡히면 다시 하기 나오게")
     */
    @Test fun stairsCaughtBeforeTrialLimitIsNormalResult() {
        val limit = MinigameTrial.questions
        val engine = stairs(limit)
        jump(engine, correct = true)
        var frames = 0
        while (!engine.restartArmed && frames < 60 * 60) {   // 가만히 있으면 먹구름이 따라잡는다
            engine.advance(frame)
            frames += 1
        }

        assertTrue(engine.session.caught)
        assertNotNull(engine.result)
        assertFalse(engine.trialFinished, "체험 수를 다 쓰지 않았으면 체험 종료 패널이 아니다")
        assertTrue(engine.restartArmed)
        engine.restart(limit)
        assertNull(engine.result)
        assertEquals(limit, engine.trialLeft, "새 판은 체험 수가 처음부터")
        assertFalse(engine.roundOver)
    }

    /** 체험 수 전에 60초가 다 되면 체험 끝이 아님 → 일반 결과 창, [다시 하기]는 같은 체험 수로 새 판 */
    @Test fun monsterTimeUpBeforeTrialLimitIsNormalResult() {
        val limit = MinigameTrial.questions
        val engine = monster(limit)
        pick(engine, correct = true)
        var frames = 0
        while (!engine.restartArmed && frames < 70 * 60) {
            engine.advance(frame)
            frames += 1
        }

        assertEquals(RoundPhase.OVER, engine.session.clock.phase)
        assertNotNull(engine.result)
        assertFalse(engine.trialFinished, "체험 수를 다 쓰지 않았으면 체험 종료 패널이 아니다")
        assertTrue(engine.restartArmed)
        engine.restart(limit)
        assertNull(engine.result)
        assertEquals(limit, engine.trialLeft, "새 판은 체험 수가 처음부터")
        assertFalse(engine.roundOver)
    }

    @Test fun monsterTrialCountsOnlyDefeats() {
        val engine = monster(3)
        pick(engine, correct = true)
        pick(engine, correct = false)
        assertEquals(2, engine.trialLeft)
        pick(engine, correct = true)
        pick(engine, correct = true)
        assertEquals(0, engine.trialLeft)
        assertTrue(engine.trialFinished)
        assertTrue(engine.roundOver)
        assertEquals(3, engine.session.defeated)
        assertEquals(1, engine.session.wrongCount)
    }

    @Test fun monsterWithoutTrialKeepsGoing() {
        val engine = monster(null)
        repeat(5) { pick(engine, correct = true) }
        assertFalse(engine.roundOver)
        assertEquals(5, engine.session.defeated)
        assertNull(engine.trialLeft)
    }
}
