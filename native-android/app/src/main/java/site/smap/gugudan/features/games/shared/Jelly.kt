package site.smap.gugudan.features.games.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import site.smap.gugudan.R
import site.smap.gugudan.services.Haptics
import kotlin.math.roundToInt

// 둥근 젤리 판·알약 (Unity 시험판 JellyUi.cs·HudBadge.MakePill 이식, iOS JellyBackground.swift 대응)
// 그림을 9분할로 늘려 어떤 크기에서도 모서리가 찌그러지지 않게 하고, 색은 곱해 입힌다(무채색 몸 + 남색 외곽선).
// 테두리는 "원본 px × 배율" 크기로 그린다 — Unity pixelsPerUnitMultiplier 와 같은 효과(iOS 는 scaleEffect 로 같은 결과).

/** 게임 화면이 한 번 만든 그림 묶음 — 젤리 버튼처럼 캔버스 밖 부품이 꺼내 쓴다(게임 화면이 제공) */
val LocalGameImages = staticCompositionLocalOf<GameImages> { error("GameImages 미제공 — 게임 화면 안에서만 쓴다") }

object Jelly {
    /** 젤리 판(card_jelly 271×211) 테두리 원본 px(Unity spriteBorder 왼100·아래72·오른70·위75) */
    const val CAP_LEFT = 100
    const val CAP_TOP = 75
    const val CAP_RIGHT = 70
    const val CAP_BOTTOM = 72
    /** 아래 입술 + 외곽선 높이(원본 px) — 창 안 내용은 아래 끝에서 이만큼(× 배율) 위에 둬야 입술에 걸리지 않음 */
    const val LIP_HEIGHT = 72f
    /** 입술 때문에 앞면 가운데가 그림 가운데보다 위 — 버튼 글자를 이만큼(원본 px × 배율) 올린다 */
    const val LABEL_LIFT = 20f

    /** 크림 알약(badge_pill 298×108) 양 끝 반원 원본 px */
    const val PILL_CAP = 54
    const val PILL_PIXEL_HEIGHT = 108f
}

/**
 * 9분할 그리기 — 모서리(원본 cap px × scale)는 그대로, 가운데는 늘인다. 판이 테두리 합보다 작으면 테두리를 비율대로 줄인다
 * (Unity 9분할이 테두리를 줄이는 것과 같은 모양). 칸 경계는 정수 px 로 맞춰 이음매가 생기지 않게 한다
 */
fun DrawScope.drawNineSlice(
    image: ImageBitmap,
    rect: Rect,
    capLeft: Int,
    capTop: Int,
    capRight: Int,
    capBottom: Int,
    scale: Float,
    color: Color,
    alpha: Float = 1f,
) {
    if (rect.width <= 0f || rect.height <= 0f || alpha <= 0f) return
    var l = capLeft * scale
    var r = capRight * scale
    var t = capTop * scale
    var b = capBottom * scale
    if (l + r > rect.width) {
        val f = rect.width / (l + r)
        l *= f
        r *= f
    }
    if (t + b > rect.height) {
        val f = rect.height / (t + b)
        t *= f
        b *= f
    }
    val dx = intArrayOf(rect.left.roundToInt(), (rect.left + l).roundToInt(), (rect.right - r).roundToInt(), rect.right.roundToInt())
    val dy = intArrayOf(rect.top.roundToInt(), (rect.top + t).roundToInt(), (rect.bottom - b).roundToInt(), rect.bottom.roundToInt())
    val sx = intArrayOf(0, capLeft, image.width - capRight, image.width)
    val sy = intArrayOf(0, capTop, image.height - capBottom, image.height)
    val filter = multiplyFilter(color)
    for (i in 0 until 3) {
        for (j in 0 until 3) {
            val dw = dx[i + 1] - dx[i]
            val dh = dy[j + 1] - dy[j]
            val sw = sx[i + 1] - sx[i]
            val sh = sy[j + 1] - sy[j]
            if (dw <= 0 || dh <= 0 || sw <= 0 || sh <= 0) continue
            drawImage(
                image,
                srcOffset = IntOffset(sx[i], sy[j]), srcSize = IntSize(sw, sh),
                dstOffset = IntOffset(dx[i], dy[j]), dstSize = IntSize(dw, dh),
                alpha = alpha, colorFilter = filter,
            )
        }
    }
}

/** 젤리 판 — 쉬는 창·결과 창·버튼. pixelScale = 원본 px 하나를 화면 몇 px 로 그릴지(= Unity JellyUi scale × GameViewport.unit) */
fun DrawScope.drawJelly(images: GameImages, rect: Rect, color: Color, pixelScale: Float, alpha: Float = 1f) =
    drawNineSlice(
        images[R.drawable.game_card_jelly], rect,
        Jelly.CAP_LEFT, Jelly.CAP_TOP, Jelly.CAP_RIGHT, Jelly.CAP_BOTTOM, pixelScale, color, alpha,
    )

/**
 * 크림 알약 — 배지·[⏸]·막대. 반원이 정확히 높이의 절반이 되게 그린다.
 * 높이보다 좁으면(막대가 거의 빈 때) 동그라미를 가로로 눌러 그림 — Unity 9분할이 테두리를 줄이는 것과 같은 모양
 */
fun DrawScope.drawPill(images: GameImages, rect: Rect, color: Color = Color.White, alpha: Float = 1f) {
    val k = rect.height / Jelly.PILL_PIXEL_HEIGHT
    drawNineSlice(images[R.drawable.game_badge_pill], rect, Jelly.PILL_CAP, 0, Jelly.PILL_CAP, 0, k, color, alpha)
}

/** 가운데·크기 버전 */
fun DrawScope.drawPill(images: GameImages, center: Offset, size: Size, color: Color = Color.White, alpha: Float = 1f) =
    drawPill(images, Rect(center.x - size.width / 2, center.y - size.height / 2, center.x + size.width / 2, center.y + size.height / 2), color, alpha)

/**
 * 둥근 젤리 버튼(Unity JellyUi.MakeButton): 젤리 판 + 가운데 글자(입술만큼 위로). 크기는 modifier 가 정한다.
 * fontSize = 설계 px, jellyScale = 모서리·외곽선·입술 배율(Unity JellyUi scale), unit = GameViewport.unit.
 * 아직 누를 수 없을 때(결과 창 0.6초) Unity 버튼처럼 흐리게
 */
@Composable
fun JellyButton(
    title: String,
    color: Color,
    textColor: Color,
    fontSize: Float,
    jellyScale: Float,
    unit: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val images = LocalGameImages.current
    val measurer = rememberGameTextMeasurer()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .semantics { contentDescription = title }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                Haptics.impactLight()
                onClick()
            }
            .drawBehind {
                val alpha = if (enabled) 1f else 0.55f
                val k = jellyScale * unit
                drawJelly(images, Rect(Offset.Zero, size), color, k, alpha)
                drawGameText(
                    measurer, title, Offset(size.width / 2, size.height / 2 - Jelly.LABEL_LIFT * k),
                    fontSize * unit, textColor, maxWidth = size.width * 0.85f, minScale = 0.6f, alpha = alpha,
                )
            },
    )
}
