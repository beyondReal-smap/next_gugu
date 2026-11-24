package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.store.Minigame
import site.smap.gugudan.store.MinigameStart
import site.smap.gugudan.store.Router
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 게임 진입(시작 단 전달·표시 순서) (Android UI 전용).
 * 2.9.0 에서 진입점이 놀이 4종 → 구구 계단·구구 몬스터로 바뀌었다.
 */
class MinigameEntryTests {

    @Test fun openMinigameCarriesStartTable() {
        val router = Router()
        router.openMinigame(Minigame.MONSTER, MinigameStart.Table(7))
        assertEquals(Minigame.MONSTER, router.minigame?.game)
        assertEquals(MinigameStart.Table(7), router.minigame?.start)

        // '섞어서'(null)와 홈 진입(기본값)은 다른 값이다
        router.openMinigame(Minigame.STAIRS, MinigameStart.Table(null))
        assertEquals(Minigame.STAIRS, router.minigame?.game)
        assertEquals(MinigameStart.Table(null), router.minigame?.start)
        assertNotEquals<MinigameStart>(MinigameStart.Default, MinigameStart.Table(null))

        // 홈에서 다시 열면 이전 진입의 단이 남지 않는다
        router.openMinigame(Minigame.MONSTER)
        assertEquals(MinigameStart.Default, router.minigame?.start)
    }

    /** 홈 "게임" 묶음·단 시트 타일이 같은 순서(선언 순)를 쓴다 — 구구 계단 먼저 */
    @Test fun minigameOrderIsStairsFirst() {
        assertEquals(listOf(Minigame.STAIRS, Minigame.MONSTER), Minigame.entries)
        assertTrue(Minigame.entries.chunked(2).single().size == 2, "단 시트는 한 줄에 두 개")
    }
}
