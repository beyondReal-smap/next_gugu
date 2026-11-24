package site.smap.gugudan

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Before
import org.junit.Test
import site.smap.gugudan.core.AppLanguage
import site.smap.gugudan.core.SeededRandom
import site.smap.gugudan.core.games.MissionKind
import site.smap.gugudan.core.games.MonsterDex
import site.smap.gugudan.core.games.MonsterKind
import site.smap.gugudan.core.games.MonsterQuestion
import site.smap.gugudan.core.games.MonsterSession
import site.smap.gugudan.core.games.ProblemOrder
import site.smap.gugudan.core.games.PlayGame
import site.smap.gugudan.core.games.RoundClock
import site.smap.gugudan.core.games.RoundPhase
import site.smap.gugudan.features.games.monster.DexStripState
import site.smap.gugudan.features.games.monster.MonsterEngine
import site.smap.gugudan.features.games.monster.MonsterStage
import site.smap.gugudan.features.games.shared.GameInsets
import site.smap.gugudan.features.games.shared.GameViewport
import site.smap.gugudan.features.games.shared.PauseLayout
import site.smap.gugudan.services.Sound
import site.smap.gugudan.store.MinigameStart
import site.smap.gugudan.store.PlayRewardsStorage
import site.smap.gugudan.store.PlayRewardsStore
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 구구 몬스터 화면 엔진(MonsterEngine) — iOS MonsterEngineTests.swift 와 같은 의미.
 * 프레임 루프 없이 advance 를 직접 불러 확인한다: 쉬는 동안 시계 정지, 재개·다시 하기 때 쌓인 탭 폐기, [⏸] 위 탭 제외,
 * 체험 3마리 끝, 처치 순간 도감 저장·결과 때 별 저장, 단 지정이면 목표가 그 단, 새 종 해금 알림, Android 뒤로 가기 = 쉬는 창.
 */
class MonsterEngineTests {

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

    private fun makeEngine(
        store: PlayRewardsStore,
        start: MinigameStart = MinigameStart.Default,
        trial: Int? = null,
        seed: UInt = 7u,
        begin: Boolean = true,
        order: ProblemOrder = ProblemOrder.RANDOM,
    ): MonsterEngine {
        val engine = MonsterEngine(start, order, store, strings, LearningSpy()::commit, random = SeededRandom.mulberry32(seed))
        engine.updateViewport(viewport)
        if (begin) engine.start(trial)
        return engine
    }

    /** 카드 가운데 — 실제 손가락과 같은 큐 경로로 넣는다 */
    private fun point(card: Int): Offset = MonsterEngine.Cards.center(card, viewport)

    /** 정답 두 장을 같은 프레임에 누르고 한 프레임 진행 */
    private fun defeat(engine: MonsterEngine) {
        val before = engine.session.defeated
        val q = engine.session.current
        engine.enqueueTap(point(q.pairA))
        engine.enqueueTap(point(q.pairB))
        engine.advance(frame)
        assertEquals(before + 1, engine.session.defeated)
    }

    /** pairA 와 곱이 목표가 아닌 카드 중 가장 앞 자리 */
    private fun wrongCard(q: MonsterQuestion): Int =
        (0 until MonsterSession.CARD_COUNT).firstOrNull {
            it != q.pairA && q.cards[q.pairA] * q.cards[it] != q.target.a * q.target.b
        } ?: fail("오답 카드가 없음 ${q.cards}")

    /** 프레임 상한(0.1초)에 걸리지 않게 sec 만큼 60fps 로 흘린다 */
    private fun run(engine: MonsterEngine, sec: Double) {
        repeat(ceil(sec / frame).toInt()) { engine.advance(frame) }
    }

    /** 결과(또는 체험 종료)가 뜰 때까지 흘린다 — 60초 시계 포함, 상한으로 무한 반복 방지 */
    private fun advanceUntilResult(engine: MonsterEngine) {
        var i = 0
        while (engine.result == null && i < 10_000) {
            engine.advance(0.05)
            i += 1
        }
        assertNotNull(engine.result, "결과가 뜨지 않음")
    }

    // MARK: 시작·카드 고르기

    /** 시작 전 패널에서는 프레임이 돌아도 아무것도 진행하지 않는다 */
    @Test fun readyPanelDoesNothing() {
        val engine = makeEngine(makeStore(), begin = false)
        assertEquals(MonsterEngine.Phase.READY, engine.phase)
        assertFalse(engine.wantsFrames)
        engine.enqueueTap(point(engine.session.current.pairA))
        repeat(10) { engine.advance(frame) }
        assertEquals(0.0, engine.time)
        assertNull(engine.session.selected)
    }

    /** 첫 카드 전에는 시계가 멈춰 있고, 카드 한 장을 고르면 흐른다. 고른 카드는 문제 판 "a × ? = 체력" */
    @Test fun clockStartsOnFirstCardAndProblemShowsPick() {
        val engine = makeEngine(makeStore())
        assertTrue(engine.hintVisible)
        repeat(120) { engine.advance(frame) }
        assertEquals(RoundPhase.WAITING, engine.session.clock.phase)
        val q = engine.session.current
        assertEquals("? × ? = ${q.target.a * q.target.b}", engine.problemText)

        engine.enqueueTap(point(q.pairA))
        engine.advance(frame)
        assertEquals(q.pairA, engine.session.selected)
        assertTrue(engine.cardSelected(q.pairA))
        assertEquals("${q.cards[q.pairA]} × ? = ${q.target.a * q.target.b}", engine.problemText)
        engine.advance(0.5)
        assertTrue(engine.session.clock.elapsed > 0)
        assertFalse(engine.hintVisible)
    }

    /** 카드 사이 틈·카드 밖은 카드로 치지 않는다 */
    @Test fun tapOutsideCardsIsIgnored() {
        val engine = makeEngine(makeStore())
        val a = MonsterEngine.Cards.frame(0, viewport)
        val b = MonsterEngine.Cards.frame(1, viewport)
        engine.enqueueTap(Offset((a.right + b.left) / 2, a.center.y))   // 틈
        engine.enqueueTap(Offset(viewport.size.width / 2, viewport.size.height * 0.4f))   // 몬스터
        engine.advance(frame)
        assertNull(engine.session.selected)
        assertEquals(RoundPhase.WAITING, engine.session.clock.phase)
    }

    /** 오답: 고른 두 장 빨강·정답 두 장 초록, 문제 판에 정답 식·아래 줄에 고른 식. 공개가 끝나면 같은 몬스터, 카드만 다시 */
    @Test fun wrongPairRevealsThenSameMonster() {
        val engine = makeEngine(makeStore())
        val q = engine.session.current
        val wrong = wrongCard(q)
        engine.enqueueTap(point(q.pairA))
        engine.enqueueTap(point(wrong))
        engine.advance(frame)

        assertTrue(engine.problemRevealing)
        assertEquals("${q.target.a} × ${q.target.b} = ${q.target.a * q.target.b}", engine.problemText)
        assertEquals("${q.cards[q.pairA]} × ${q.cards[wrong]} = ${q.cards[q.pairA] * q.cards[wrong]}", engine.subText)
        assertEquals(MonsterEngine.CardLook.CORRECT, engine.cardLook(q.pairA))
        assertEquals(MonsterEngine.CardLook.CORRECT, engine.cardLook(q.pairB))
        assertEquals(MonsterEngine.CardLook.WRONG, engine.cardLook(wrong))
        val hp = engine.stage.hp

        run(engine, RoundClock.REVEAL_SEC + 0.05)
        assertFalse(engine.problemRevealing)
        assertEquals(q.target, engine.session.current.target)
        assertEquals(hp, engine.stage.hp)
        assertEquals(List(6) { MonsterEngine.CardLook.NORMAL }, (0 until MonsterSession.CARD_COUNT).map(engine::cardLook))
    }

    // MARK: 쉬는 창

    /** 쉬는 동안 60초 시계·그림 시간이 모두 멈추고, 이어 하면 다시 흐른다 */
    @Test fun pauseFreezesClock() {
        val engine = makeEngine(makeStore())
        defeat(engine)
        repeat(30) { engine.advance(frame) }
        engine.openPause()
        assertTrue(engine.paused)
        assertFalse(engine.wantsFrames)

        val elapsed = engine.session.clock.elapsed
        val time = engine.time
        repeat(1200) { engine.advance(0.1) }   // 120초 — 멈추지 않았다면 판이 끝났을 시간
        assertEquals(elapsed, engine.session.clock.elapsed)
        assertEquals(time, engine.time)
        assertNotEquals(RoundPhase.OVER, engine.session.clock.phase)
        assertNull(engine.result)

        engine.resume()
        engine.advance(0.05)
        assertTrue(engine.session.clock.elapsed > elapsed)
    }

    /** 쉬기 직전에 쌓인 탭·쉬는 동안의 탭은 이어 한 뒤 카드로 새지 않는다 */
    @Test fun queuedTapsDiscardedAcrossPause() {
        val engine = makeEngine(makeStore())
        defeat(engine)
        engine.enqueueTap(point(engine.session.current.pairA))   // 같은 프레임에 [⏸] 를 누름
        engine.openPause()
        engine.enqueueTap(point(engine.session.current.pairA))   // 창 뒤 탭(창이 막지만 엔진도 받지 않음)
        engine.resume()
        engine.advance(frame)

        assertNull(engine.session.selected)
        assertEquals(1, engine.session.attempts.size)
    }

    /** 앱이 백그라운드로 가면 쉬는 창이 열리고, 돌아와도 [계속 하기]를 누를 때까지 멈춰 있다 */
    @Test fun sceneInactiveOpensPause() {
        val engine = makeEngine(makeStore())
        defeat(engine)
        engine.sceneBecameInactive()
        assertTrue(engine.paused)
        assertFalse(engine.wantsFrames)
        engine.sceneBecameActive()
        assertTrue(engine.paused)
        assertFalse(engine.wantsFrames)
    }

    /** [⏸] 위 탭은 카드·시계에 닿지 않는다 */
    @Test fun tapOnPauseButtonIsIgnored() {
        val engine = makeEngine(makeStore())
        val pause = PauseLayout.buttonFrame(viewport)
        engine.enqueueTap(pause.center)
        engine.advance(frame)
        assertNull(engine.session.selected)
        assertEquals(RoundPhase.WAITING, engine.session.clock.phase)
    }

    /** Android 뒤로 가기: 시작 전 패널 → 나감 / 진행 중 → 쉬는 창 / 쉬는 창 → 그대로 / 결과 창 → 나감 */
    @Test fun backPressOpensPauseAndStays() {
        val ready = makeEngine(makeStore(), begin = false)
        assertFalse(ready.pressBack())

        val engine = makeEngine(makeStore())
        defeat(engine)
        assertTrue(engine.pressBack())
        assertTrue(engine.paused)
        assertTrue(engine.pressBack(), "쉬는 창에서 또 눌러도 판에 머묾")
        assertTrue(engine.paused)

        engine.resume()
        advanceUntilResult(engine)
        assertFalse(engine.pressBack())
    }

    // MARK: 체험

    /** 무료 체험은 3마리를 물리치면 끝 — 더 눌러도 채점하지 않고, 1초 뒤 결과(체험 종료)와 함께 별·기록 저장 */
    @Test fun trialEndsAfterThreeDefeats() {
        val store = makeStore()
        val engine = makeEngine(store, trial = 3)
        assertEquals(3, engine.trialLeft)
        defeat(engine)
        defeat(engine)
        assertEquals(1, engine.trialLeft)
        defeat(engine)

        assertTrue(engine.trialFinished)
        assertTrue(engine.roundOver)
        assertFalse(engine.pauseButtonVisible)
        assertFalse(engine.timeUpVisible)
        val q = engine.session.current
        engine.enqueueTap(point(q.pairA))
        engine.enqueueTap(point(q.pairB))
        engine.advance(frame)
        assertEquals(3, engine.session.defeated)
        assertEquals(3, engine.session.attempts.size)
        assertNull(engine.result)
        assertEquals(0, store.stars)   // 결과 전에는 별 저장 안 함

        advanceUntilResult(engine)
        assertEquals(3, store.stars)
        assertEquals(3, store.best(PlayGame.MONSTER))
        assertEquals(3, engine.result?.mainValue)
        assertEquals(3, engine.result?.starTotal)
    }

    // MARK: 저장 시점

    /** 도감은 처치하는 순간 저장, 별·최고 기록은 시간이 다 돼 결과를 띄우는 순간 한 번 */
    @Test fun dexSavedOnDefeatStarsSavedAtResult() {
        val store = makeStore()
        val engine = makeEngine(store)
        val firstKind = engine.stage.kind
        assertEquals(MonsterKind.GREEN, firstKind)   // 처음 4종 중 아직 못 만난 첫 종
        defeat(engine)
        assertEquals(1, store.dexTotal)
        assertTrue(MonsterDex.isSeen(firstKind, store.dexSeenMask))
        assertEquals("도감에 등록!", engine.banner.text)
        assertEquals(DexStripState.Look.SEEN, engine.dex.look(firstKind))
        assertEquals(firstKind, engine.dex.popKind)
        assertEquals(firstKind, engine.stage.pop?.kind)   // 기절 몬스터 연출
        assertFalse(engine.stage.visible)                  // 새 몬스터는 0.2초 기다렸다가 떨어짐
        assertEquals(MonsterKind.ORANGE, engine.stage.kind)   // 다음 종: 아직 못 만난 종 먼저
        run(engine, MonsterStage.NEXT_DROP_DELAY + 0.02)
        assertTrue(engine.stage.visible)

        repeat(4) { defeat(engine) }
        assertEquals(5, store.dexTotal)
        assertEquals(0, store.stars)
        assertEquals(0, store.best(PlayGame.MONSTER))

        advanceUntilResult(engine)   // 60초 → "타임업!" 1초 → 결과
        assertEquals(RoundPhase.OVER, engine.session.clock.phase)
        val earned = engine.session.stars
        assertEquals(7, earned)    // 5마리 + 연속 5 보너스 2
        assertEquals(earned, store.stars)
        assertEquals(5, store.best(PlayGame.MONSTER))
        assertEquals(5, engine.result?.subValue)   // 최고 연속

        // 결과 창이 떠 있는 동안 프레임이 더 돌아도 다시 쌓지 않음
        repeat(120) { engine.advance(frame) }
        assertEquals(earned, store.stars)
    }

    /** 60초가 다 되면 결과 전 1초 동안 "타임업!" — 알림은 지우고, 쉬는 창은 열리지 않는다 */
    @Test fun timeUpShowsBeforeResult() {
        val engine = makeEngine(makeStore())
        defeat(engine)
        while (engine.session.clock.phase != RoundPhase.OVER) engine.advance(0.05)
        assertTrue(engine.timeUpVisible)
        assertNull(engine.banner.text)
        assertFalse(engine.pauseButtonVisible)
        engine.openPause()
        assertFalse(engine.paused)
        advanceUntilResult(engine)
        assertFalse(engine.timeUpVisible)
    }

    /** 쉬는 창에서 나가면(엔진은 결과를 만들지 않음) 별·기록은 남지 않고 도감만 남는다 */
    @Test fun quitFromPauseKeepsDexOnly() {
        val store = makeStore()
        val engine = makeEngine(store)
        repeat(2) { defeat(engine) }
        engine.openPause()
        assertNull(engine.result)
        assertEquals(0, store.stars)
        assertEquals(0, store.best(PlayGame.MONSTER))
        assertEquals(2, store.dexTotal)
        assertEquals(0, store.missionBoard().items[1].progress)   // 오늘의 미션에도 안 쌓임
    }

    /** 판이 끝나면 물리친 몬스터가 오늘의 몬스터 미션에 쌓이고, 이번 판으로 넘었으면 결과 창에 "미션 완료!" 한 줄 */
    @Test fun roundEndRecordsDailyMission() {
        val store = makeStore()
        val monsters = store.missionBoard().items[1].mission
        assertEquals(MissionKind.MONSTERS_DEFEATED, monsters.kind)
        store.recordRound(PlayGame.MONSTER, score = monsters.target - 1, stars = 0, bestStreak = 0)   // 앞선 판들
        val engine = makeEngine(store)
        defeat(engine)

        advanceUntilResult(engine)
        val item = store.missionBoard().items[1]
        assertEquals(monsters.target, item.progress)
        assertTrue(item.done)
        assertEquals("미션 완료! 몬스터 ${monsters.target}마리 물리치기", engine.result?.missionNote)
    }

    /** 결과 다시 볼 식 — 고른 두 카드를 함께 보여 준다 */
    @Test fun resultShowsMissWithPickedCards() {
        val engine = makeEngine(makeStore())
        val q = engine.session.current
        val wrong = wrongCard(q)
        engine.enqueueTap(point(q.pairA))
        engine.enqueueTap(point(wrong))
        engine.advance(frame)
        advanceUntilResult(engine)
        assertEquals(q.target, engine.result?.miss)
        assertEquals(
            "고른 카드 ${q.cards[q.pairA]} × ${q.cards[wrong]} = ${q.cards[q.pairA] * q.cards[wrong]}",
            engine.result?.missGiven,
        )
        assertEquals("물리친 몬스터", engine.result?.mainLabel)
        assertEquals("최고 연속", engine.result?.subLabel)
    }

    /** [다시 하기]는 결과가 뜬 뒤 0.6초부터. 새 판은 첫 카드 전 상태에서, 지난 판 탭 없이 시작한다 */
    @Test fun restartArmsAfterDelayAndStartsClean() {
        val engine = makeEngine(makeStore())
        defeat(engine)
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
        assertNull(engine.session.selected)
        assertEquals(0, engine.session.attempts.size)
        assertEquals(RoundPhase.WAITING, engine.session.clock.phase)
        assertEquals(60, engine.timeBadge.value)
    }

    // MARK: 단 지정·도감 해금

    /** 단 시트에서 고른 단(Table(n))이면 몇 마리를 잡아도 목표는 그 단. Table(null) 은 섞어서 */
    @Test fun chosenTableTargetsOnlyThatTable() {
        val engine = makeEngine(makeStore(), start = MinigameStart.Table(7), order = ProblemOrder.SEQUENTIAL)
        assertEquals(7, engine.chosenTable)
        repeat(12) {
            assertEquals(7, engine.session.current.target.a)
            assertEquals(0, engine.stage.hp % 7)
            defeat(engine)
        }
        assertTrue(engine.answerRecords.all { it.a == 7 })

        val mixed = makeEngine(makeStore(), start = MinigameStart.Table(null), seed = 3u)
        assertNull(mixed.chosenTable)
        val tables = mutableSetOf<Int>()
        repeat(12) {
            tables.add(mixed.session.current.target.a)
            defeat(mixed)
        }
        assertTrue(tables.size > 1)
    }

    /** 10단 이상은 카드 1~9 로 만들 수 없다 — 진입 화면이 막아야 하므로 조용히 넘기지 않고 던진다 */
    @Test fun tableAboveNineIsRejected() {
        assertFailsWith<IllegalArgumentException> { makeEngine(makeStore(), start = MinigameStart.Table(12), begin = false) }
    }

    /** 누적 처치 9 → 10번째 처치로 분홍이 해금되면 바로 다음 몬스터로 등장 + "새 몬스터 등장!" */
    @Test fun unlockedKindAppearsNextWithBanner() {
        val store = makeStore()
        for (kind in listOf(
            MonsterKind.GREEN, MonsterKind.ORANGE, MonsterKind.PURPLE, MonsterKind.SKY,
            MonsterKind.GREEN, MonsterKind.ORANGE, MonsterKind.PURPLE, MonsterKind.SKY, MonsterKind.GREEN,
        )) {
            store.recordDefeat(kind)
        }
        assertEquals(9, store.dexTotal)
        val engine = makeEngine(store)
        assertEquals(DexStripState.Look.LOCKED, engine.dex.look(MonsterKind.PINK))
        val before = engine.stage.kind
        defeat(engine)

        assertEquals(10, store.dexTotal)
        assertEquals(MonsterKind.PINK, engine.stage.kind)
        assertNotEquals(MonsterKind.PINK, before)
        assertEquals("새 몬스터 등장!", engine.banner.text)
        assertEquals(DexStripState.Look.SILHOUETTE, engine.dex.look(MonsterKind.PINK))   // 자물쇠가 풀린 실루엣

        run(engine, MonsterStage.NEXT_DROP_DELAY + 0.02)
        defeat(engine)
        assertTrue(MonsterDex.isSeen(MonsterKind.PINK, store.dexSeenMask))
        assertEquals(DexStripState.Look.SEEN, engine.dex.look(MonsterKind.PINK))
    }

    /** 학습 기록 연결용 값 — 채점 기록(목표 식·두 카드의 곱)과 판 길이(ms) */
    @Test fun exposesAnswerRecordsAndDuration() {
        val engine = makeEngine(makeStore())
        defeat(engine)
        val q = engine.session.current
        engine.enqueueTap(point(q.pairA))
        engine.enqueueTap(point(wrongCard(q)))
        engine.advance(0.5)

        assertEquals(2, engine.answerRecords.size)
        assertEquals(listOf(true, false), engine.answerRecords.map { it.correct })
        assertEquals(q.target.a, engine.answerRecords[1].a)
        assertEquals(q.target.b, engine.answerRecords[1].b)
        assertEquals((engine.session.clock.elapsed * 1000).roundToInt(), engine.roundDurationMs)
        assertTrue(engine.roundDurationMs > 0)
    }

    /** DEBUG 자동 처치: 시작 패널을 건너뛰고(체험 수는 화면이 넘김) N마리를 실제 탭 경로로 물리친 뒤 멈춘다 */
    @Test fun debugAutoplayStartsAndStopsAfterN() {
        val engine = MonsterEngine(
            MinigameStart.Default, ProblemOrder.RANDOM, makeStore(), strings, LearningSpy()::commit, random = SeededRandom.mulberry32(5u),
            autoplay = 2,
        )
        engine.debugAutoStart(trial = 3)
        assertEquals(MonsterEngine.Phase.READY, engine.phase, "화면 크기를 알기 전에는 시작하지 않음")
        engine.updateViewport(viewport)
        engine.debugAutoStart(trial = 3)
        assertEquals(MonsterEngine.Phase.PLAYING, engine.phase)
        assertEquals(3, engine.trial)
        run(engine, 5.0)
        assertEquals(2, engine.session.defeated)
        assertEquals(0, engine.session.wrongCount)
    }
}
