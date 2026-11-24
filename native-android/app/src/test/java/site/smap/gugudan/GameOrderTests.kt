package site.smap.gugudan

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Before
import org.junit.Test
import site.smap.gugudan.core.AppLanguage
import site.smap.gugudan.core.Problem
import site.smap.gugudan.core.SeededRandom
import site.smap.gugudan.core.games.MonsterSession
import site.smap.gugudan.core.games.PlayGame
import site.smap.gugudan.core.games.ProblemOrder
import site.smap.gugudan.core.games.StairsSession
import site.smap.gugudan.features.games.monster.MonsterEngine
import site.smap.gugudan.features.games.shared.GameInsets
import site.smap.gugudan.features.games.shared.GameViewport
import site.smap.gugudan.features.games.stairs.StairsCamera
import site.smap.gugudan.features.games.stairs.StairsEngine
import site.smap.gugudan.features.paywall.MinigameTrial
import site.smap.gugudan.services.Persistence
import site.smap.gugudan.services.Sound
import site.smap.gugudan.store.GameOrderChoice
import site.smap.gugudan.store.GameOrderStore
import site.smap.gugudan.store.MinigameStart
import site.smap.gugudan.store.PlayRewardsStorage
import site.smap.gugudan.store.PlayRewardsStore
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 게임 문제 순서 [순서대로]·[무작위] (2.9.0, iOS GameOrderTests.swift 미러) — 선택 기억(GameOrderStore),
 * 시작 창에서 바꿀 때 엔진이 첫 문제를 다시 만드는지, 학습 기록 단 규칙, 무료 체험(맞힌 수로 끝)과의 조합.
 * 세션 규칙·골든은 StairsTests·MonsterTests 에 있다.
 */
class GameOrderTests {

    /** 에뮬레이터(1280×2856 px) 크기·안전 영역 */
    private val viewport = GameViewport(Size(1280f, 2856f), GameInsets(top = 156f, bottom = 63f))
    private val frame = 1.0 / 60
    private val strings = TestStrings(AppLanguage.KO)

    @Before fun quiet() {
        Sound.setEnabled(false)   // JVM 테스트에는 AudioTrack 이 없다
    }

    // MARK: 도우미

    /** 메모리 저장소 — 격리한 원문을 모아 둔다(앱은 Persistence 가 noBackupFilesDir/quarantine 에 파일로 남김) */
    private class MemoryStorage : PlayRewardsStorage {
        val values = mutableMapOf<String, String>()
        val quarantined = mutableListOf<Pair<String, String>>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            values[key] = value
        }
        override fun quarantine(key: String, raw: String, cause: Throwable) {
            quarantined += key to raw
        }
    }

    private fun makeRewards() =
        PlayRewardsStore(MemoryStorage(), key = "gugu.test.play", adventureKey = "gugu.test.adventure")

    private fun stairs(
        start: MinigameStart = MinigameStart.Table(7),
        order: ProblemOrder,
        seed: UInt = 7u,
        learning: LearningSpy = LearningSpy(),
    ): StairsEngine {
        val engine = StairsEngine(
            start, order, makeRewards(), strings, learning::commit, topStep = 9, random = SeededRandom.mulberry32(seed),
        )
        engine.updateViewport(viewport)
        return engine
    }

    private fun monster(
        start: MinigameStart = MinigameStart.Table(7),
        order: ProblemOrder,
        seed: UInt = 7u,
        learning: LearningSpy = LearningSpy(),
    ): MonsterEngine {
        val engine = MonsterEngine(start, order, makeRewards(), strings, learning::commit, random = SeededRandom.mulberry32(seed))
        engine.updateViewport(viewport)
        return engine
    }

    /** 계단 칸 가운데를 누르고 한 프레임 — 오답이면 정답 공개(0.6초)가 끝날 때까지 흘린다 */
    private fun jump(engine: StairsEngine, correct: Boolean) {
        val q = engine.session.current
        val lane = if (correct) q.correctLane else (q.correctLane + 1) % StairsSession.LANES
        engine.enqueueTap(Offset(StairsCamera(viewport.size, 0.0).laneCenterX(lane), 1500f))
        engine.advance(frame)
        if (!correct) repeat(40) { engine.advance(frame) }
    }

    /** 몬스터 카드 두 장 — 정답 짝 또는 곱이 목표가 아닌 짝. 오답이면 공개가 끝날 때까지 흘린다 */
    private fun pick(engine: MonsterEngine, correct: Boolean) {
        val q = engine.session.current
        val second = if (correct) q.pairB else (0 until MonsterSession.CARD_COUNT).firstOrNull {
            it != q.pairA && q.cards[q.pairA] * q.cards[it] != q.target.a * q.target.b
        } ?: fail("오답 카드가 없음 ${q.cards}")
        engine.enqueueTap(MonsterEngine.Cards.center(q.pairA, viewport))
        engine.enqueueTap(MonsterEngine.Cards.center(second, viewport))
        engine.advance(frame)
        if (!correct) repeat(40) { engine.advance(frame) }
    }

    private fun advanceUntil(done: () -> Boolean, step: (Double) -> Unit) {
        var i = 0
        while (!done() && i < 20_000) {
            step(0.05)
            i += 1
        }
        assertTrue(done(), "결과가 뜨지 않음")
    }

    // MARK: 선택 기억 (GameOrderStore)

    /** 처음은 두 게임 모두 순서대로(지금 동작) */
    @Test fun defaultIsSequentialForBothGames() {
        val storage = MemoryStorage()
        val store = GameOrderStore(storage, key = "gugu.test.order")
        assertEquals(ProblemOrder.SEQUENTIAL, store.order(PlayGame.STAIRS))
        assertEquals(ProblemOrder.SEQUENTIAL, store.order(PlayGame.MONSTER))
        assertTrue(storage.values.isEmpty(), "열기만 해서는 저장하지 않음")
    }

    /** 게임마다 따로 기억하고, 다시 열어도(새 저장소) 마지막 선택 그대로 */
    @Test fun choiceIsPerGameAndSurvivesReopen() {
        val storage = MemoryStorage()
        val key = "gugu.test.order"
        val store = GameOrderStore(storage, key)
        store.setOrder(ProblemOrder.RANDOM, PlayGame.STAIRS)
        assertEquals(ProblemOrder.RANDOM, store.order(PlayGame.STAIRS))
        assertEquals(ProblemOrder.SEQUENTIAL, store.order(PlayGame.MONSTER), "다른 게임은 그대로")
        assertEquals("""{"stairs":"random","monster":"sequential"}""", storage.values[key], "iOS 와 같은 JSON 모양")

        val reopened = GameOrderStore(storage, key)
        assertEquals(ProblemOrder.RANDOM, reopened.order(PlayGame.STAIRS))
        assertEquals(ProblemOrder.SEQUENTIAL, reopened.order(PlayGame.MONSTER))

        reopened.setOrder(ProblemOrder.RANDOM, PlayGame.MONSTER)
        reopened.setOrder(ProblemOrder.SEQUENTIAL, PlayGame.STAIRS)
        val again = GameOrderStore(storage, key)
        assertEquals(ProblemOrder.SEQUENTIAL, again.order(PlayGame.STAIRS))
        assertEquals(ProblemOrder.RANDOM, again.order(PlayGame.MONSTER))
        assertTrue(storage.quarantined.isEmpty())
    }

    /** iOS 가 쓴 저장본(키 순서가 달라도)도 그대로 읽는다 — 같은 계약 */
    @Test fun readsIosShapedSavedChoice() {
        val storage = MemoryStorage()
        storage.values["gugu.test.order"] = """{"monster":"random","stairs":"sequential"}"""
        val store = GameOrderStore(storage, "gugu.test.order")
        assertEquals(GameOrderChoice(ProblemOrder.SEQUENTIAL, ProblemOrder.RANDOM), store.saved)
    }

    /** 보상·진행도 저장본과 다른 키 — 형식이 바뀌어도 서로 영향 없음. 아이 설정이라 백업 대상 파일("gugu")에 둔다 */
    @Test fun usesItsOwnKey() {
        assertEquals("gugu.gameOrder.v1", Persistence.GAME_ORDER_KEY)
        assertNotEquals(Persistence.PLAY_REWARDS_KEY, Persistence.GAME_ORDER_KEY)
        assertNotEquals(Persistence.PROGRESS_KEY, Persistence.GAME_ORDER_KEY)
        assertFalse(Persistence.GAME_ORDER_KEY in Persistence.DEVICE_KEYS, "기기에 묶인 값(백업 제외)이 아님")
        assertEquals(listOf("sequential", "random"), ProblemOrder.entries.map { it.rawValue }, "저장값·iOS 와 같은 문자열")
    }

    /** 못 읽는 저장본(모르는 값·빠진 항목·깨진 JSON)은 조용히 덮어쓰지 않고 원본을 격리한 뒤 순서대로로 시작한다 */
    @Test fun unreadableSavedChoiceIsQuarantined() {
        for (raw in listOf("""{"stairs":"zigzag","monster":"random"}""", """{"stairs":"random"}""", "{")) {
            val storage = MemoryStorage()
            val key = "gugu.test.order"
            storage.values[key] = raw
            val store = GameOrderStore(storage, key)
            assertEquals(ProblemOrder.SEQUENTIAL, store.order(PlayGame.STAIRS), raw)
            assertEquals(ProblemOrder.SEQUENTIAL, store.order(PlayGame.MONSTER), raw)
            assertEquals(listOf(key to raw), storage.quarantined, "원본 격리: $raw")
            assertEquals(raw, storage.values[key], "고르기 전에는 덮어쓰지 않음")
        }
    }

    /** DEBUG 훅은 저장된 선택보다 우선하지만 저장하지는 않는다. 잘못된 값은 멈춘다 */
    @Test fun debugOverrideWinsOverSavedAndIsNotSaved() {
        val storage = MemoryStorage()
        val key = "gugu.test.order"
        GameOrderStore(storage, key).setOrder(ProblemOrder.RANDOM, PlayGame.MONSTER)
        val props = mapOf("debug.gugu.stairs_order" to "random", "debug.gugu.monster_order" to "sequential")
        val forced = GameOrderStore(storage, key, GameOrderStore.debugOverrides { props[it] })
        assertEquals(ProblemOrder.RANDOM, forced.order(PlayGame.STAIRS))
        assertEquals(ProblemOrder.SEQUENTIAL, forced.order(PlayGame.MONSTER))
        assertEquals(GameOrderChoice(ProblemOrder.SEQUENTIAL, ProblemOrder.RANDOM), forced.saved)
        val plain = GameOrderStore(storage, key)
        assertEquals(ProblemOrder.SEQUENTIAL, plain.order(PlayGame.STAIRS))
        assertEquals(ProblemOrder.RANDOM, plain.order(PlayGame.MONSTER))

        assertTrue(GameOrderStore.debugOverrides { null }.isEmpty(), "릴리스(DevProps 가 늘 null)는 훅 없음")
        assertFailsWith<IllegalArgumentException> {
            GameOrderStore.debugOverrides { if (it == "debug.gugu.stairs_order") "shuffle" else null }
        }
    }

    // MARK: 시작 창에서 바꾸기 (엔진)

    /** 계단: 순서를 바꾸면 새 세션 — 뒤에 비치는 첫 줄 발판·문제 판 글자도 새 순서. 무작위는 고른 단(7)을 쓰지 않는다 */
    @Test fun stairsOrderSwitchRebuildsFirstQuestion() {
        val engine = stairs(order = ProblemOrder.SEQUENTIAL)
        assertEquals(Problem(7, 1), engine.session.current.problem)
        assertEquals("7단 · 1번째 계단", engine.tableLabel)

        val before = engine.session
        engine.setOrder(ProblemOrder.SEQUENTIAL)   // 같은 쪽을 다시 누름 → 그대로
        assertSame(before, engine.session)

        engine.setOrder(ProblemOrder.RANDOM)
        assertEquals(ProblemOrder.RANDOM, engine.order)
        assertNotSame(before, engine.session, "새 세션")
        assertEquals(ProblemOrder.RANDOM, engine.session.order)
        assertTrue(engine.session.current.problem.a in StairsSession.RANDOM_TABLES)
        assertEquals(0, engine.session.current.standing)
        assertEquals("1번째 계단", engine.tableLabel, "무작위는 단 없이")
        assertEquals(
            engine.session.current.choices.map { it.toString() }, engine.stage.rows[1]?.lanes?.map { it.label },
            "뒤에 비치는 첫 줄도 새 문제",
        )
        assertEquals("출발", engine.stage.rows[0]?.lanes?.get(1)?.label)
        assertEquals(StairsEngine.Phase.READY, engine.phase)

        engine.setOrder(ProblemOrder.SEQUENTIAL)
        assertEquals(Problem(7, 1), engine.session.current.problem, "순서대로로 돌아오면 고른 단부터")
    }

    /** 시작한 뒤에는 순서를 바꿀 수 없다(시작 창에만 버튼이 있음) — 화면 쪽 버그를 조용히 넘기지 않는다 */
    @Test fun orderCannotChangeAfterStart() {
        val s = stairs(order = ProblemOrder.SEQUENTIAL)
        s.start(null)
        assertFailsWith<IllegalStateException> { s.setOrder(ProblemOrder.RANDOM) }
        val m = monster(order = ProblemOrder.SEQUENTIAL)
        m.start(null)
        assertFailsWith<IllegalStateException> { m.setOrder(ProblemOrder.RANDOM) }
    }

    /** 계단 무작위: 문제 판 윗줄은 "N번째 계단"(보물까지 몇 번째 칸), 꼭대기 칸에서 칸 기준 보물 알림, [다시 하기]도 무작위 */
    @Test fun stairsRandomLabelsTreasureBannerAndRestartKeepsOrder() {
        val engine = stairs(start = MinigameStart.Default, order = ProblemOrder.RANDOM)
        engine.start(null)
        for (step in 1..9) {
            assertEquals("${step}번째 계단", engine.tableLabel)
            jump(engine, correct = true)
        }
        assertEquals(1, engine.session.treasures)
        assertEquals("9칸 돌파! 보물 +1", engine.banner.text)
        assertEquals("1번째 계단", engine.tableLabel)

        advanceUntil({ engine.restartArmed }) { engine.advance(it) }
        engine.restart(null)
        assertEquals(ProblemOrder.RANDOM, engine.order)
        assertEquals(ProblemOrder.RANDOM, engine.session.order)
        assertEquals(0, engine.session.climbed)
    }

    /** 몬스터: 순서대로 + 7단 → 제목 단·학습 기록 단 7. 무작위로 바꾸면 고른 단을 쓰지 않고(단 null) 다시 순서대로면 7×1 부터 */
    @Test fun monsterOrderSwitchRebuildsFirstMonsterAndTable() {
        val engine = monster(order = ProblemOrder.SEQUENTIAL)
        assertEquals(Problem(7, 1), engine.session.current.target)
        assertEquals(7, engine.playedTable)
        assertEquals(7, engine.learning.table)
        assertEquals(7, engine.stage.hp)

        val before = engine.session
        engine.setOrder(ProblemOrder.RANDOM)
        assertNotSame(before, engine.session)
        assertEquals(ProblemOrder.RANDOM, engine.session.order)
        assertNull(engine.session.table)
        assertNull(engine.playedTable, "무작위는 제목에 단을 붙이지 않음")
        assertEquals(7, engine.chosenTable, "고른 단은 그대로 기억(순서대로로 돌아올 때)")
        assertNull(engine.learning.table)
        assertEquals(
            engine.session.current.target.a * engine.session.current.target.b, engine.stage.hp, "뒤에 비치는 몬스터 체력도 새 문제",
        )

        engine.setOrder(ProblemOrder.SEQUENTIAL)
        assertEquals(Problem(7, 1), engine.session.current.target)
        assertEquals(7, engine.learning.table)
    }

    /** 몬스터 순서대로 7단 판: 7 → 14 → 21 … 차례로, [다시 하기]도 순서대로 7단 */
    @Test fun monsterSequentialTablePlaysInOrderAndRestartKeepsOrder() {
        val engine = monster(order = ProblemOrder.SEQUENTIAL)
        engine.start(null)
        for (b in 1..4) {
            assertEquals(7 * b, engine.stage.hp)
            pick(engine, correct = true)
        }
        advanceUntil({ engine.restartArmed }) { engine.advance(it) }
        engine.restart(null)
        assertEquals(ProblemOrder.SEQUENTIAL, engine.order)
        assertEquals(Problem(7, 1), engine.session.current.target)
    }

    // MARK: 학습 기록 단

    /** 몬스터 순서대로 + 단 지정만 그 단, 단 없는 순서대로·무작위(고른 단 무시)는 null. 계단은 순서와 상관없이 null */
    @Test fun learningTableRule() {
        fun monsterTable(start: MinigameStart, order: ProblemOrder): List<Int?> {
            val learning = LearningSpy()
            val engine = monster(start, order, learning = learning)
            engine.start(null)
            pick(engine, correct = true)
            engine.openPause()
            engine.abandon()
            return learning.results.map { it.table }
        }
        assertEquals(listOf<Int?>(7), monsterTable(MinigameStart.Table(7), ProblemOrder.SEQUENTIAL))
        assertEquals(listOf<Int?>(null), monsterTable(MinigameStart.Table(7), ProblemOrder.RANDOM))
        assertEquals(listOf<Int?>(null), monsterTable(MinigameStart.Default, ProblemOrder.SEQUENTIAL))
        assertEquals(listOf<Int?>(null), monsterTable(MinigameStart.Default, ProblemOrder.RANDOM))

        for (order in ProblemOrder.entries) {
            val learning = LearningSpy()
            val engine = stairs(order = order, learning = learning)
            engine.start(null)
            jump(engine, correct = true)
            engine.openPause()
            engine.abandon()
            assertEquals(listOf<Int?>(null), learning.results.map { it.table }, "$order")
        }
    }

    // MARK: 무료 체험과의 조합

    /** 무작위 계단·순서대로 몬스터도 체험은 맞힌 수로 끝난다(오답은 세지 않음) */
    @Test fun trialEndsByCorrectCountInEveryOrder() {
        val n = MinigameTrial.questions
        for (order in ProblemOrder.entries) {
            val s = stairs(start = MinigameStart.Default, order = order)
            s.start(MinigameTrial.limit(isPremium = false))
            for (i in 0 until n) {
                assertFalse(s.roundOver, "$order $i")
                if (i == 2) jump(s, correct = false)
                jump(s, correct = true)
            }
            assertTrue(s.trialFinished, "계단 $order")
            assertEquals(n, s.session.climbed)
            assertEquals(1, s.session.wrongCount)

            val m = monster(start = MinigameStart.Default, order = order)
            m.start(MinigameTrial.limit(isPremium = false))
            for (i in 0 until n) {
                assertFalse(m.roundOver, "$order $i")
                if (i == 4) pick(m, correct = false)
                pick(m, correct = true)
            }
            assertTrue(m.trialFinished, "몬스터 $order")
            assertEquals(n, m.session.defeated)
            assertEquals(1, m.session.wrongCount)
        }
    }
}
