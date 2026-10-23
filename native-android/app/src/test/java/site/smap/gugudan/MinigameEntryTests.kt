package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.Pop
import site.smap.gugudan.core.RunnerPhase
import site.smap.gugudan.features.pop.PopLayout
import site.smap.gugudan.store.Minigame
import site.smap.gugudan.store.MinigameStart
import site.smap.gugudan.store.Router
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 미니게임 진입(시작 단 전달·표시 순서)과 팡팡 화면 좌표 → Core 좌표 변환 (Android UI 전용) */
class MinigameEntryTests {

    @Test fun openMinigameCarriesStartTable() {
        val router = Router()
        router.openMinigame(Minigame.POP, MinigameStart.Table(7))
        assertTrue(router.popOpen)
        assertEquals(MinigameStart.Table(7), router.minigameStart)

        // '섞어서'(null)와 홈 진입(기본값)은 다른 값이다
        router.openMinigame(Minigame.BASKET, MinigameStart.Table(null))
        assertTrue(router.basketOpen)
        assertEquals(MinigameStart.Table(null), router.minigameStart)

        // 홈에서 다시 열면 이전 진입의 단이 남지 않는다
        router.openMinigame(Minigame.RUNNER)
        assertTrue(router.runnerOpen)
        assertEquals(MinigameStart.Default, router.minigameStart)
    }

    @Test fun minigameOrderIsPopFirst() {
        assertEquals(listOf(Minigame.POP, Minigame.BASKET, Minigame.RUNNER, Minigame.LANE), Minigame.entries)
    }

    @Test fun popLayoutTapHitsBalloonWhereItIsDrawn() {
        val layout = PopLayout(width = 1000f, height = 1333f)
        val state = Pop.create(7, RunnerPhase.RUNNING)
        // 안내가 끝나고 풍선이 나올 때까지 진행
        var s = state
        while (s.balloons.isEmpty()) s = Pop.advance(s, 16.0)
        s = Pop.advance(s, 1500.0)
        val b = s.balloons.first()
        val (x, y, aspect) = layout.toCore(layout.px(b.x), layout.py(b.y))
        assertEquals(b.x, x, 1e-4)
        assertEquals(b.y, y, 1e-4)
        assertEquals(b.id, Pop.target(s, x, y, aspect))
    }
}
