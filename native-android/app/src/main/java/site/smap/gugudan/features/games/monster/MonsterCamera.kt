package site.smap.gugudan.features.games.monster

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min

// 구구 몬스터 월드 ↔ 화면 변환 (Unity 직교 카메라 + TableLayout.OrthoSizeFor — 카메라는 (0, 0) 고정, iOS MonsterCamera.swift 대응)
// 가로 9유닛이 늘 보이고(세로로 긴 폰은 위아래가 더 보임), 화면이 9:16 보다 넓으면 세로 16유닛에 맞춘다.
// 월드 y 는 위가 +, 화면 y 는 아래가 +. (구구 계단 StairsCamera 와 같은 계산에서 카메라 이동만 뺀 것)

data class MonsterCamera(
    /** 전체 화면 크기(px) */
    val size: Size,
) {
    /** 월드 1유닛의 화면 px */
    val unitPx: Float get() = min(size.width / MIN_VISIBLE_WIDTH, size.height / MIN_VISIBLE_HEIGHT)

    /** 화면 반폭·반높이(월드 유닛) */
    val halfWidth: Double get() = (size.width / 2 / unitPx).toDouble()
    val halfHeight: Double get() = (size.height / 2 / unitPx).toDouble()

    fun point(x: Double, y: Double): Offset =
        Offset(size.width / 2 + x.toFloat() * unitPx, size.height / 2 - y.toFloat() * unitPx)

    companion object {
        const val MIN_VISIBLE_WIDTH = 9f
        const val MIN_VISIBLE_HEIGHT = 16f
    }
}
