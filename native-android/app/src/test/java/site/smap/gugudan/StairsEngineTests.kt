package site.smap.gugudan

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Before
import org.junit.Test
import site.smap.gugudan.core.AppLanguage
import site.smap.gugudan.core.SeededRandom
import site.smap.gugudan.core.games.MissionKind
import site.smap.gugudan.core.games.PlayGame
import site.smap.gugudan.core.games.ProblemOrder
import site.smap.gugudan.core.games.RoundPhase
import site.smap.gugudan.core.games.StairsSession
import site.smap.gugudan.features.games.shared.GameInsets
import site.smap.gugudan.features.games.shared.GameResultContent
import site.smap.gugudan.features.games.shared.GameViewport
import site.smap.gugudan.features.games.shared.PauseLayout
import site.smap.gugudan.features.games.stairs.StairsCamera
import site.smap.gugudan.features.games.stairs.StairsEngine
import site.smap.gugudan.services.Sound
import site.smap.gugudan.store.MinigameStart
import site.smap.gugudan.store.PlayRewardsStorage
import site.smap.gugudan.store.PlayRewardsStore
import site.smap.gugudan.store.PlayRoundRecord
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 구구 계단 화면 엔진(StairsEngine) — iOS StairsEngineTests.swift 와 같은 의미.
 * 프레임 루프 없이 advance 를 직접 불러 확인한다: 쉬는 동안 시간 정지, 재개·다시 하기 때 쌓인 탭 폐기, [⏸] 위 탭 제외,
 * 체험 3문제 끝, 별·기록은 결과 때만 저장, Android 뒤로 가기 = 쉬는 창.
 */
class StairsEngineTests {

    /** 에뮬레이터(1280×2856 px) 크기·안전 영역 */
    private val viewport = GameViewport(Size(1280f, 2856f), GameInsets(top = 156f, bottom = 63f))
    private val frame = 1.0 / 60
    private val strings = TestStrings(AppLanguage.KO)

    @Before fun quiet() {
        Sound.setEnabled(false)   // JVM 테스트에는 AudioTrack 이 없다
    }

    // MARK: 도우미

    private class MemoryRewardsStorage : PlayRewardsStorage {
        val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            values[key] = value
        }
        override fun quarantine(key: String, raw: String, cause: Throwable) = error("격리될 저장본이 없어야 함: $key")
    }

    private fun makeStore() = PlayRewardsStore(MemoryRewardsStorage(), key = "gugu.test.play", adventureKey = "gugu.test.adventure")

    private fun makeEngine(store: PlayRewardsStore, trial: Int? = null, seed: UInt = 7u, start: Boolean = true): StairsEngine {
        val engine = StairsEngine(
            MinigameStart.Table(2), ProblemOrder.SEQUENTIAL, store, strings, LearningSpy()::commit, topStep = 9,
            random = SeededRandom.mulberry32(seed),
        )
        engine.updateViewport(viewport)
        if (start) engine.start(trial)
        return engine
    }

    /** 칸 가운데(문제 판 아래 아무 높이) — 실제 손가락과 같은 큐 경로로 넣는다 */
    private fun point(lane: Int) = Offset(StairsCamera(viewport.size, 0.0).laneCenterX(lane), 1500f)

    /** 정답 칸을 누르고 한 프레임 진행 */
    private fun climb(engine: StairsEngine) {
        val before = engine.session.climbed
        engine.enqueueTap(point(engine.session.current.correctLane))
        engine.advance(frame)
        assertEquals(before + 1, engine.session.climbed)
    }

    /** 결과(또는 체험 종료)가 뜰 때까지 흘린다 — 먹구름이 잡을 때까지 포함, 상한으로 무한 반복 방지 */
    private fun advanceUntilResult(engine: StairsEngine) {
        var i = 0
        while (engine.result == null && i < 10_000) {
            engine.advance(0.05)
            i += 1
        }
        assertNotNull(engine.result, "결과가 뜨지 않음")
    }

    // MARK: 시작 전

    /** 시작 전 패널·첫 탭 전에는 먹구름이 멈춰 있다 */
    @Test fun cloudFrozenBeforeFirstTap() {
        val engine = makeEngine(makeStore())
        val top = engine.session.cloud.top
        assertTrue(engine.hintVisible)
        assertEquals("출발", engine.stage.rows[0]?.lanes?.get(1)?.label)
        repeat(120) { engine.advance(frame) }
        assertEquals(top, engine.session.cloud.top)
        assertEquals(RoundPhase.WAITING, engine.session.clock.phase)
    }

    /** 시작 전 패널에서는 프레임이 돌아도 아무것도 진행하지 않는다 */
    @Test fun readyPanelDoesNothing() {
        val engine = makeEngine(makeStore(), start = false)
        assertEquals(StairsEngine.Phase.READY, engine.phase)
        assertFalse(engine.wantsFrames)
        engine.enqueueTap(point(engine.session.current.correctLane))
        repeat(10) { engine.advance(frame) }
        assertEquals(0.0, engine.time)
        assertEquals(0, engine.session.climbed)
    }

    /** Table(n) = n단부터, 문제 판 글자 */
    @Test fun startsAtChosenTable() {
        val engine = makeEngine(makeStore())
        assertEquals(2, engine.session.current.problem.a)
        assertEquals("2 × 1", engine.problemText)
        assertEquals("2단 · 1번째 계단", engine.tableLabel)
    }

    // MARK: 쉬는 창

    /** 쉬는 동안 먹구름·판 시간·반짝임 시간이 모두 멈추고, 이어 하면 다시 흐른다 */
    @Test fun pauseFreezesTime() {
        val engine = makeEngine(makeStore())
        climb(engine)
        repeat(30) { engine.advance(frame) }
        engine.openPause()
        assertTrue(engine.paused)
        assertFalse(engine.wantsFrames)

        val top = engine.session.cloud.top
        val elapsed = engine.session.clock.elapsed
        val time = engine.time
        repeat(600) { engine.advance(0.1) }

        assertEquals(top, engine.session.cloud.top)
        assertEquals(elapsed, engine.session.clock.elapsed)
        assertEquals(time, engine.time)
        assertFalse(engine.session.caught)

        engine.resume()
        assertTrue(engine.wantsFrames)
        engine.advance(0.05)
        assertTrue(engine.session.clock.elapsed > elapsed)
        assertTrue(engine.session.cloud.top > top)
    }

    /** 쉬기 직전에 쌓인 탭·쉬는 동안의 탭은 이어 한 뒤 점프로 새지 않는다 */
    @Test fun queuedTapsDiscardedAcrossPause() {
        val engine = makeEngine(makeStore())
        climb(engine)
        engine.enqueueTap(point(engine.session.current.correctLane))   // 같은 프레임에 [⏸] 를 누름
        engine.openPause()
        engine.enqueueTap(point(engine.session.current.correctLane))   // 창 뒤 탭(창이 막지만 엔진도 받지 않음)
        engine.resume()
        engine.advance(frame)

        assertEquals(1, engine.session.climbed)
        assertEquals(0, engine.session.wrongCount)
    }

    /** 앱이 백그라운드로 가면 쉬는 창이 열린다 — 돌아와도 아이가 [계속 하기]를 누를 때까지 멈춰 있음 */
    @Test fun sceneInactiveOpensPause() {
        val engine = makeEngine(makeStore())
        climb(engine)
        engine.sceneBecameInactive()
        assertTrue(engine.paused)
        assertFalse(engine.wantsFrames)
        engine.sceneBecameActive()
        assertTrue(engine.paused)
        assertFalse(engine.wantsFrames)
    }

    /** 결과를 기다리거나 결과 창일 때 백그라운드에 가면 쉬는 창 없이 루프만 멈추고, 돌아오면 다시 돈다 */
    @Test fun sceneInactiveAfterRoundOnlyStopsLoop() {
        val engine = makeEngine(makeStore())
        climb(engine)
        advanceUntilResult(engine)
        engine.sceneBecameInactive()
        assertFalse(engine.paused)
        assertFalse(engine.wantsFrames)
        engine.sceneBecameActive()
        assertTrue(engine.wantsFrames)
    }

    /** [⏸] 위 탭은 왼쪽 칸 점프로 새지 않는다 */
    @Test fun tapOnPauseButtonIsIgnored() {
        val engine = makeEngine(makeStore())
        val pause = PauseLayout.buttonFrame(viewport)
        engine.enqueueTap(pause.center)
        engine.advance(frame)

        assertEquals(0, engine.session.climbed)
        assertEquals(0, engine.session.wrongCount)
        assertEquals(RoundPhase.WAITING, engine.session.clock.phase)
    }

    /** [⏸] 버튼은 안전 영역(상태 표시줄) 아래에 있다 */
    @Test fun pauseButtonSitsBelowStatusBar() {
        val pause = PauseLayout.buttonFrame(viewport)
        assertTrue(pause.top >= viewport.insets.top, "⏸ 위 끝 ${pause.top} < 안전 영역 ${viewport.insets.top}")
        assertTrue(pause.left >= 0f)
    }

    // MARK: Android 뒤로 가기

    /** 시작 전·결과 창 = 나가기, 진행 중 = 쉬는 창, 쉬는 창 = 그대로(나가기는 [메뉴로 나가기]로만) */
    @Test fun backOpensPauseWhilePlaying() {
        val ready = makeEngine(makeStore(), start = false)
        assertFalse(ready.pressBack())

        val engine = makeEngine(makeStore())
        climb(engine)
        assertTrue(engine.pressBack())
        assertTrue(engine.paused)
        assertTrue(engine.pressBack())
        assertTrue(engine.paused)
        engine.resume()
        assertFalse(engine.paused)

        advanceUntilResult(engine)
        assertFalse(engine.pressBack())
    }

    /** 잡힌 뒤 결과가 뜨기 전 1초에는 뒤로 가기가 쉬는 창을 열지 않고(멈추지 않게) 화면에 남는다 */
    @Test fun backDuringCaughtDelayStays() {
        val engine = makeEngine(makeStore())
        climb(engine)
        var i = 0
        while (!engine.session.caught && i < 10_000) {
            engine.advance(0.05)
            i += 1
        }
        assertTrue(engine.session.caught)
        assertNull(engine.result)
        assertTrue(engine.caughtTextVisible)
        assertTrue(engine.pressBack())
        assertFalse(engine.paused)
    }

    // MARK: 체험

    /** 무료 체험은 3칸을 오르면 끝 — 더 눌러도 오르지 않고, 1초 뒤 결과(체험 종료)와 함께 별·기록 저장 */
    @Test fun trialEndsAfterThreeClimbs() {
        val store = makeStore()
        val engine = makeEngine(store, trial = 3)
        assertEquals(3, engine.trialLeft)
        climb(engine)
        climb(engine)
        assertEquals(1, engine.trialLeft)
        climb(engine)

        assertTrue(engine.trialFinished)
        assertTrue(engine.roundOver)
        assertFalse(engine.pauseButtonVisible)
        engine.enqueueTap(point(engine.session.current.correctLane))
        engine.advance(frame)
        assertEquals(3, engine.session.climbed)
        assertNull(engine.result)
        assertEquals(0, store.stars)   // 결과 전에는 저장 안 함

        advanceUntilResult(engine)
        val earned = engine.session.stars
        assertTrue(earned > 0)
        assertEquals(earned, store.stars)
        assertEquals(3, store.best(PlayGame.STAIRS))
        assertEquals(3, engine.result?.mainValue)
        assertEquals(earned, engine.result?.starTotal)
    }

    // MARK: 결과·저장

    /** 판 도중에는 별이 저장소에 쌓이지 않고, 결과를 띄우는 순간 한 번만 쌓인다 */
    @Test fun starsSavedOnlyAtResult() {
        val store = makeStore()
        val engine = makeEngine(store)
        repeat(5) { climb(engine) }
        repeat(10) { engine.advance(frame) }
        assertTrue(engine.session.stars > 0)
        assertEquals(0, store.stars)
        assertEquals(0, store.best(PlayGame.STAIRS))

        advanceUntilResult(engine)   // 먹구름에 잡힘 → 1초 뒤 결과
        assertTrue(engine.session.caught)
        val earned = engine.session.stars
        assertEquals(earned, store.stars)
        assertEquals(5, store.best(PlayGame.STAIRS))
        assertEquals(GameResultContent.note(PlayRoundRecord(0, true, earned, emptyList(), false), strings), engine.result?.note)
        assertEquals("새 기록!", engine.result?.note)

        // 결과 창이 떠 있는 동안 프레임이 더 돌아도 다시 쌓지 않음
        repeat(120) { engine.advance(frame) }
        assertEquals(earned, store.stars)
    }

    /** 판이 끝나면 오른 계단이 오늘의 계단 미션에 쌓이고, 이번 판으로 넘었으면 결과 창에 "미션 완료!" 한 줄 */
    @Test fun roundEndRecordsDailyMission() {
        val store = makeStore()
        val stairs = store.missionBoard().items[0].mission
        assertEquals(MissionKind.STAIRS_STEPS, stairs.kind)
        store.recordRound(PlayGame.STAIRS, score = stairs.target - 2, stars = 0, bestStreak = 0)   // 앞선 판들
        val engine = makeEngine(store)
        repeat(2) { climb(engine) }

        advanceUntilResult(engine)
        val item = store.missionBoard().items[0]
        assertEquals(stairs.target, item.progress)
        assertTrue(item.done)
        assertEquals("미션 완료! 계단 ${stairs.target}칸 오르기", engine.result?.missionNote)
    }

    /** 판 중간에 쉬는 창에서 나가면(엔진은 결과를 만들지 않음) 별·기록이 남지 않는다 */
    @Test fun quitFromPauseSavesNothing() {
        val store = makeStore()
        val engine = makeEngine(store)
        repeat(3) { climb(engine) }
        engine.openPause()

        assertNull(engine.result)
        assertEquals(0, store.stars)
        assertEquals(0, store.best(PlayGame.STAIRS))
        assertEquals(0, store.missionBoard().items[0].progress)   // 오늘의 미션에도 안 쌓임
        val stars = engine.session.stars
        assertTrue(stars > 0)
        assertEquals("지금 나가면 이번 판 별 ${stars}개와\n기록이 사라져요", PauseLayout.message(strings, stars))
        assertEquals("지금 나가면 이번 판 기록이 사라져요", PauseLayout.message(strings, 0))
    }

    /** [다시 하기]는 결과가 뜬 뒤 0.6초부터. 새 판은 0칸·첫 탭 전 상태에서, 지난 판 탭 없이 시작한다 */
    @Test fun restartArmsAfterDelayAndStartsClean() {
        val store = makeStore()
        val engine = makeEngine(store)
        climb(engine)
        advanceUntilResult(engine)
        val round = engine.round

        engine.restart(trial = null)
        assertEquals(round, engine.round, "0.6초 전에는 무시")
        engine.enqueueTap(point(0))   // 결과 창 위 탭 — 받지 않음
        repeat(40) { engine.advance(frame) }
        assertTrue(engine.restartArmed)

        engine.restart(trial = null)
        assertEquals(round + 1, engine.round)
        engine.advance(frame)
        assertNull(engine.result)
        assertFalse(engine.restartArmed)
        assertEquals(0, engine.session.climbed)
        assertEquals(0, engine.session.wrongCount)
        assertEquals(RoundPhase.WAITING, engine.session.clock.phase)
        assertEquals(1, engine.bestAtStart)   // 지난 판 기록이 깃발 높이로
        assertTrue(engine.pauseButtonVisible)
    }

    /** 학습 기록 연결용 값 — 채점 기록과 판 길이(ms) */
    @Test fun exposesAnswerRecordsAndDuration() {
        val engine = makeEngine(makeStore())
        climb(engine)
        val wrong = (engine.session.current.correctLane + 1) % StairsSession.LANES
        engine.enqueueTap(point(wrong))
        engine.advance(0.5)

        assertEquals(2, engine.answerRecords.size)
        assertEquals(listOf(true, false), engine.answerRecords.map { it.correct })
        assertEquals((engine.session.clock.elapsed * 1000).roundToInt(), engine.roundDurationMs)
        assertTrue(engine.roundDurationMs > 0)
    }

    /** 오답 공개 동안 문제 판은 정답 식(초록), 공개가 끝나면 같은 칸을 다시 낸다 */
    @Test fun wrongAnswerRevealsThenRepeatsStep() {
        val engine = makeEngine(makeStore())
        climb(engine)
        val step = engine.session.current.problem
        val wrong = (engine.session.current.correctLane + 1) % StairsSession.LANES
        engine.enqueueTap(point(wrong))
        engine.advance(frame)

        assertTrue(engine.problemRevealing)
        assertEquals("${step.a} × ${step.b} = ${step.a * step.b}", engine.problemText)
        repeat(60) { engine.advance(frame) }
        assertFalse(engine.problemRevealing)
        assertEquals(step, engine.session.current.problem)
        assertEquals("${step.a} × ${step.b}", engine.problemText)
    }

    /** DEBUG 자동 정답: 화면 크기를 알면 바로 시작하고, 실제 탭 경로로 N칸을 오른다 */
    @Test fun autoplayClimbsThroughTapQueue() {
        val engine = StairsEngine(
            MinigameStart.Table(2), ProblemOrder.SEQUENTIAL, makeStore(), strings, LearningSpy()::commit, topStep = 9,
            random = SeededRandom.mulberry32(7u), autoplay = 4,
        )
        engine.updateViewport(viewport)
        assertEquals(StairsEngine.Phase.PLAYING, engine.phase)
        assertNull(engine.trial)
        repeat(240) { engine.advance(frame) }
        assertEquals(4, engine.session.climbed)
        assertEquals(0, engine.session.wrongCount)
    }
}
