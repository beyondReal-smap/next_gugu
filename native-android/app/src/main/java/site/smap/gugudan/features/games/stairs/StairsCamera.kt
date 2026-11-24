package site.smap.gugudan.features.games.stairs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min

// 구구 계단 월드 ↔ 화면 변환 (Unity 직교 카메라 + TableLayout.OrthoSizeFor 이식, iOS StairsCamera.swift 대응)
// 가로 9유닛이 늘 보이고(세로로 긴 폰은 위아래가 더 보임), 화면이 9:16 보다 넓으면 세로 16유닛에 맞춘다.
// 카메라 가운데 = (0, centerY). 월드 y 는 위가 +, 화면 y 는 아래가 +.

data class StairsCamera(
    /** 전체 화면 크기(px) */
    val size: Size,
    /** 카메라 가운데 높이(월드) */
    val centerY: Double,
) {
    /** 월드 1유닛의 화면 px */
    val unitPx: Float get() = min(size.width / MIN_VISIBLE_WIDTH, size.height / MIN_VISIBLE_HEIGHT)

    /** 화면 반폭·반높이(월드 유닛) */
    val halfWidth: Double get() = (size.width / 2 / unitPx).toDouble()
    val halfHeight: Double get() = (size.height / 2 / unitPx).toDouble()

    fun point(x: Double, y: Double): Offset =
        Offset(size.width / 2 + x.toFloat() * unitPx, size.height / 2 - (y - centerY).toFloat() * unitPx)

    fun screenX(x: Double): Float = size.width / 2 + x.toFloat() * unitPx

    /** 화면 x → 칸. 화면이 9유닛보다 넓어도 실제 발판 사이 경계로 나눈다 */
    fun lane(atScreenX: Float): Int {
        val half = StairsStage.LANE_SPACING * 0.5
        if (atScreenX < screenX(-half)) return 0
        if (atScreenX < screenX(half)) return 1
        return 2
    }

    /** 칸 가운데의 화면 x — 자동 정답(스크린숏용)·테스트가 실제 탭 경로로 누를 자리 */
    fun laneCenterX(lane: Int): Float = screenX(StairsStage.laneX(lane))

    companion object {
        const val MIN_VISIBLE_WIDTH = 9f
        const val MIN_VISIBLE_HEIGHT = 16f
    }
}
