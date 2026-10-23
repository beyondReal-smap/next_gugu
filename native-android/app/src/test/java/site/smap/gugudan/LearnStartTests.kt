package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.Modes
import site.smap.gugudan.features.basket.BasketEngine
import site.smap.gugudan.features.lanerunner.LaneRunnerEngine
import site.smap.gugudan.features.runner.RunnerEngine
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 학습 탭 '단 먼저' 진입 — 단 시트에 보여 줄 모드, 미니게임 시작 단 기본값 (iOS LearnStartTests 미러).
 * 엔진은 Persistence(Context) 가 있어야 만들 수 있어 JVM 테스트에서는 기본값 상수만 확인한다 (iOS 는 엔진 생성까지 확인).
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

    @Test fun minigameEnginesKeepDefaultTable() {
        assertEquals(2, BasketEngine.DEFAULT_TABLE)
        assertNull(RunnerEngine.DEFAULT_TABLE)
        assertNull(LaneRunnerEngine.DEFAULT_TABLE)
    }
}
