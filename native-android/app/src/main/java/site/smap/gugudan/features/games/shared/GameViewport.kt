package site.smap.gugudan.features.games.shared

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

// 게임 화면 좌표 (iOS GameViewport.swift 이식) — Unity 시험판의 캔버스(CanvasScaler 1080×1920, match width)를 그대로 옮긴다.
// 설계 좌표 1px = 화면 폭 / 1080 화면 px. 위치는 Unity 처럼 기준점(안전 영역 위 가운데·가운데, 화면 가운데)에서의
// 오프셋(오른쪽 +x, 위 +y)으로 적고, 여기서 화면 좌표(왼쪽 위 원점, 아래 +y, 화면 px)로 바꾼다.
// 모든 좌표는 안전 영역을 무시한 전체 화면 기준이다(배경·먹구름·결과 창 어두운 막은 시스템 영역까지 덮음).
// 안전 영역 = WindowInsets.safeDrawing(상태 표시줄·카메라 컷아웃·내비게이션 바) — 배지 줄이 상태 표시줄과 겹치지 않게.

/** 안전 영역 여백(화면 px) */
data class GameInsets(val left: Float = 0f, val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f)

data class GameViewport(
    /** 전체 화면 크기(px) */
    val size: Size,
    /** 안전 영역 여백(px) — 상태 표시줄·내비게이션 바를 뺀 곳 */
    val insets: GameInsets,
) {
    init {
        require(size.width > 0 && size.height > 0) { "화면 크기는 0보다 커야 함: $size" }
    }

    /** 설계 px 하나가 화면 몇 px 인지 */
    val unit: Float get() = size.width / REFERENCE_WIDTH

    val safeRect: Rect
        get() = Rect(insets.left, insets.top, size.width - insets.right, size.height - insets.bottom)

    fun px(value: Float): Float = value * unit

    fun px(width: Float, height: Float): Size = Size(width * unit, height * unit)

    /** 안전 영역 위 가운데 기준(Unity 앵커 (0.5, 1)) — 배지 줄·문제 판 */
    fun safeTop(x: Float, y: Float): Offset {
        val safe = safeRect
        return Offset(safe.center.x + x * unit, safe.top - y * unit)
    }

    /** 안전 영역 아래 가운데 기준(Unity 앵커 (0.5, 0)) — y 는 위로 + . 카드·버튼처럼 엄지 영역에 붙일 것 */
    fun safeBottom(x: Float, y: Float): Offset {
        val safe = safeRect
        return Offset(safe.center.x + x * unit, safe.bottom - y * unit)
    }

    /** 안전 영역 가운데 기준(Unity 앵커 (0.5, 0.5)) — 알림·"잡혔어요!" */
    fun safeCenter(x: Float, y: Float): Offset {
        val safe = safeRect
        return Offset(safe.center.x + x * unit, safe.center.y - y * unit)
    }

    /** 전체 화면 가운데 기준 — 쉬는 창·결과 창(Unity 에서 안전 영역 밖 캔버스에 둔 것) */
    fun screenCenter(x: Float, y: Float): Offset = Offset(size.width / 2 + x * unit, size.height / 2 - y * unit)

    /** 설계 px 로 적은 상자(가운데·크기)의 화면 사각형 */
    fun rect(center: Offset, designWidth: Float, designHeight: Float): Rect {
        val w = designWidth * unit
        val h = designHeight * unit
        return Rect(center.x - w / 2, center.y - h / 2, center.x + w / 2, center.y + h / 2)
    }

    companion object {
        /** Unity 기준 해상도 폭(설계 px) */
        const val REFERENCE_WIDTH = 1080f
    }
}

/** 지금 창의 안전 영역 여백(px) — 게임 화면은 전체 화면에 그리고 이 값으로 배지 줄 등을 안쪽에 둔다 */
@Composable
fun rememberGameInsets(): GameInsets {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing
    return GameInsets(
        left = safe.getLeft(density, direction).toFloat(),
        top = safe.getTop(density).toFloat(),
        right = safe.getRight(density, direction).toFloat(),
        bottom = safe.getBottom(density).toFloat(),
    )
}

/**
 * 전체 화면 Box 안에서 화면 좌표의 가운데·px 크기로 놓는다 (iOS View.placed(at:size:)).
 * 부모 크기(전체 화면)를 그대로 차지했다고 보고하고 그 안의 절대 위치에 둔다 — 부모 정렬과 무관하게 같은 자리
 */
fun Modifier.placed(center: Offset, size: Size): Modifier = layout { measurable, constraints ->
    require(constraints.hasBoundedWidth && constraints.hasBoundedHeight) { "placed 는 크기가 정해진(전체 화면) 부모 안에서만" }
    val w = size.width.roundToInt()
    val h = size.height.roundToInt()
    val placeable = measurable.measure(Constraints.fixed(w, h))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place((center.x - w / 2f).roundToInt(), (center.y - h / 2f).roundToInt())
    }
}

/** 전체 화면 Box 안에서 내용 크기 그대로 화면 좌표 point 에 가운데를 맞춰 놓는다(앱 부품 — 체험 알약 등) */
fun Modifier.centeredAt(point: Offset): Modifier = layout { measurable, constraints ->
    require(constraints.hasBoundedWidth && constraints.hasBoundedHeight) { "centeredAt 은 크기가 정해진(전체 화면) 부모 안에서만" }
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place((point.x - placeable.width / 2f).roundToInt(), (point.y - placeable.height / 2f).roundToInt())
    }
}
