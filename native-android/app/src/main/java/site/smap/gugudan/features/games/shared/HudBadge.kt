package site.smap.gugudan.features.games.shared

import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// 위쪽 아이콘 배지 (Unity 시험판 HudBadge.cs 이식, iOS HudBadge.swift 대응): 크림 알약 + 왼쪽 아이콘 + 숫자(또는 막대).
// "계단 12" 같은 글자 대신 그림으로 읽히게 하고, 값이 바뀌면 통통 튀어 "방금 올랐다"를 보여 준다.
// 상태(HudBadgeState)는 엔진이 프레임마다 update 하고, 그리기(drawHudBadge)는 그 값을 그리기만 한다.

class HudBadgeState {
    var value: Int? = null
        private set
    /** 막대형(A 먹구름 거리): 0~1 */
    var fill = 0.0
        private set
    /** 위험(빨강) 동안 계속 두근거림 */
    var danger = false
        private set
    private var pop = 0.0
    private var pulse = 0.0

    /** 새 판: 통통 없이 값만 맞춤 */
    fun reset(value: Int?) {
        this.value = value
        fill = 0.0
        danger = false
        pop = 0.0
        pulse = 0.0
    }

    fun setValue(newValue: Int) {
        if (value == newValue) return
        val first = value == null
        value = newValue
        if (!first) pop = 1.0
    }

    fun setBar(fill: Double, danger: Boolean) {
        this.fill = min(1.0, max(0.0, fill))
        setDanger(danger)
    }

    /** 숫자형도 위험 표시(B 남은 10초): 숫자를 빨갛게 + 두근거림 */
    fun setDanger(on: Boolean) {
        danger = on
        if (!on) pulse = 0.0
    }

    fun update(dt: Double) {
        pop = max(0.0, pop - dt / POP_SEC)
        if (danger) pulse += dt
    }

    /** 아이콘·숫자 크기 배율(커졌다 제자리 + 위험 두근거림) */
    val scale: Float
        get() {
            var s = 1 + 0.18 * sin(pop * PI)
            if (danger) s *= 1 + 0.06 * abs(sin(pulse * 7))
            return s.toFloat()
        }

    companion object {
        /** 알약 높이(설계 px) — 배지 폭은 이 2배 이상이어야 아이콘 옆 숫자 칸이 남음 */
        const val HEIGHT = 110f
        const val POP_SEC = 0.28
    }
}

enum class HudBadgeContent { NUMBER, BAR }

private const val BADGE_ICON_SIZE = 124f   // 알약보다 살짝 크게 — 아이콘이 테두리 밖으로 조금 나와 또렷함
private const val BADGE_BAR_HEIGHT = 38f

/**
 * 배지 하나. center = 화면 px 가운데, width = 배지 폭(설계 px), unit = 설계 px → 화면 px.
 * iconScale: 캐릭터 그림처럼 여백이 큰 정사각형 그림은 키워서 다른 아이콘과 크기를 맞춤
 */
fun DrawScope.drawHudBadge(
    state: HudBadgeState,
    images: GameImages,
    measurer: TextMeasurer,
    @DrawableRes icon: Int,
    center: Offset,
    width: Float,
    unit: Float,
    content: HudBadgeContent = HudBadgeContent.NUMBER,
    iconScale: Float = 1f,
) {
    val h = HudBadgeState.HEIGHT
    val left = -width / 2
    // 아이콘 오른쪽 ~ 오른쪽 끝 반원 안쪽까지가 숫자(또는 막대) 자리
    val x0 = left + h + 2
    val x1 = width / 2 - h * 0.3f
    val s = state.scale
    val pillScale = if (content == HudBadgeContent.BAR && state.danger) s else 1f
    scale(pillScale, pivot = center) {
        drawPill(images, center, Size(width * unit, h * unit))
    }
    val iconSide = BADGE_ICON_SIZE * iconScale * unit
    drawGameImageFit(images[icon], Offset(center.x + (left + h / 2) * unit, center.y), Size(iconSide, iconSide), s)
    val slotCenter = Offset(center.x + (x0 + x1) / 2 * unit, center.y)
    when (content) {
        HudBadgeContent.NUMBER -> drawGameText(
            measurer, state.value?.toString().orEmpty(), Offset(slotCenter.x, slotCenter.y - 2 * unit), 66 * unit,
            if (state.danger) GamePalette.wrong else GamePalette.ink,
            maxWidth = (x1 - x0) * unit, minScale = 34f / 66f, pop = s,   // 세 자리(별 100+)는 칸에 맞게 줄임
        )
        HudBadgeContent.BAR -> {
            val barW = (x1 - x0) * unit
            val barH = BADGE_BAR_HEIGHT * unit
            val track = Rect(slotCenter.x - barW / 2, slotCenter.y - barH / 2, slotCenter.x + barW / 2, slotCenter.y + barH / 2)
            drawPill(images, track, GamePalette.ink, alpha = 0.22f)
            if (state.fill > 0) {
                val fillW = (barW * state.fill).toFloat()
                drawPill(
                    images, Rect(track.left, track.top, track.left + fillW, track.bottom),
                    if (state.danger) GamePalette.wrong else GamePalette.correct,
                )
            }
        }
    }
}
