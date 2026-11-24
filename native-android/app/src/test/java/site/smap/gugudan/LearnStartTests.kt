package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.Modes
import site.smap.gugudan.core.AppLanguage
import site.smap.gugudan.core.Problem
import site.smap.gugudan.core.SeededRandom
import site.smap.gugudan.core.games.ProblemOrder
import site.smap.gugudan.core.games.StairsSession
import site.smap.gugudan.features.games.monster.MonsterEngine
import site.smap.gugudan.features.games.stairs.StairsEngine
import site.smap.gugudan.store.Minigame
import site.smap.gugudan.store.MinigameStart
import site.smap.gugudan.store.PlayRewardsStorage
import site.smap.gugudan.store.PlayRewardsStore
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 학습 탭 '단 먼저' 진입 — 단 시트에 보여 줄 모드, 게임(구구 계단·구구 몬스터) 시작 단 전달 (iOS LearnStartTests 미러).
 */
class LearnStartTests {

    @Test fun modeOptionsForTable() {
        assertEquals(
            listOf(GameMode.PRACTICE, GameMode.TIME_ATTACK, GameMode.MISSING, GameMode.TRUEFALSE),
            Modes.options(7).map { it.id },
        )
        assertTrue(Modes.options(2).all { it.supportsTable })
    }

    @Test fun modeOptionsForMixedIsWholeList() {
        assertEquals(Modes.list.map { it.id }, Modes.options(null).map { it.id })
        assertEquals(6, Modes.list.size, "업적 mode_explorer 목표치 — list 는 그대로 둔다")
    }

    // 게임(구구 계단·구구 몬스터) 시작 단 — 엔진이 Context 없이 만들어지므로 iOS 처럼 엔진 생성까지 확인한다

    private class MemoryRewardsStorage : PlayRewardsStorage {
        val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            values[key] = value
        }
        override fun quarantine(key: String, raw: String, cause: Throwable) = error("격리될 저장본이 없어야 함: $key")
    }

    private fun makeStore() = PlayRewardsStore(MemoryRewardsStorage(), key = "gugu.test.play", adventureKey = "gugu.test.adventure")
    private val strings = TestStrings(AppLanguage.KO)

    /** 홈 진입(Default)·섞어서(Table(null)) — 두 게임 모두 2~9단에서 고른다(몬스터는 섞어서) */
    @Test fun gameEnginesKeepDefaultTable() {
        val store = makeStore()
        val learning = LearningSpy()
        for (seed in listOf(1u, 2u, 3u, 4u, 5u)) {
            val stairs = StairsEngine(MinigameStart.Default, ProblemOrder.SEQUENTIAL, store, strings, learning::commit, topStep = 9,
                random = SeededRandom.mulberry32(seed))
            assertTrue(stairs.session.table in StairsSession.RANDOM_TABLES)
            val mixed = StairsEngine(MinigameStart.Table(null), ProblemOrder.SEQUENTIAL, store, strings, learning::commit, topStep = 9,
                random = SeededRandom.mulberry32(seed))
            assertTrue(mixed.session.table in StairsSession.RANDOM_TABLES, "섞어서 = 2~9단 무작위")
        }
        assertNull(MonsterEngine(MinigameStart.Default, ProblemOrder.RANDOM, store, strings, learning::commit).chosenTable)
        assertNull(MonsterEngine(MinigameStart.Table(null), ProblemOrder.RANDOM, store, strings, learning::commit).chosenTable)
    }

    /** 단 시트에서 고른 단 — 계단은 그 단부터(2~19단), 몬스터는 그 단만(2~9단) */
    @Test fun gameEnginesAcceptStartTable() {
        val store = makeStore()
        val learning = LearningSpy()
        for (table in listOf(2, 7, 9, 13, 19)) {
            assertTrue(Minigame.STAIRS.supports(table))
            val engine = StairsEngine(MinigameStart.Table(table), ProblemOrder.SEQUENTIAL, store, strings, learning::commit, topStep = 9)
            assertEquals(table, engine.session.table)
            assertEquals(Problem(table, 1), engine.session.current.problem)
            assertEquals(StairsEngine.Phase.READY, engine.phase)
        }
        for (table in listOf(2, 7, 9)) {
            assertTrue(Minigame.MONSTER.supports(table))
            val engine = MonsterEngine(MinigameStart.Table(table), ProblemOrder.SEQUENTIAL, store, strings, learning::commit)
            assertEquals(table, engine.chosenTable)
            assertEquals(table, engine.session.current.target.a)
            assertEquals(MonsterEngine.Phase.READY, engine.phase)
        }
        assertTrue(learning.results.isEmpty(), "만들기만 한 엔진은 기록하지 않는다")
    }
}
