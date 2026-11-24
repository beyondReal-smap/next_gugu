package site.smap.gugudan.features.games.shared

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import site.smap.gugudan.designsystem.phraseBreak
import site.smap.gugudan.designsystem.suite
import kotlin.math.min

// 게임 캔버스 글자 (Unity TextMeshPro·iOS Text 대응) — 크기는 설계 px × GameViewport.unit = 화면 px 로 받는다.
// 기기 글꼴 배율(fontScale)과 무관하게 같은 크기로 그린다(Unity 캔버스처럼 배지·판 칸에 맞춘 크기라서).
// 밝은 하늘·숲 위 글자를 또렷하게: 진한 남보라 테두리 (Unity UiFactory.AddOutline — 네 대각선으로 한 번씩 더 그림, iOS OutlinedText)

/** 프레임마다 같은 글자를 다시 재지 않게 넉넉한 캐시(발판 숫자·배지·판 글자 ≈ 30개) */
@Composable
fun rememberGameTextMeasurer(): TextMeasurer = rememberTextMeasurer(cacheSize = 64)

/** 화면 px 크기의 게임 글자 모양 — 앱 언어 글꼴(SUITE·M PLUS·시스템), 기본 굵기 ExtraBold */
fun DrawScope.gameTextStyle(sizePx: Float, color: Color, weight: FontWeight = FontWeight.ExtraBold): TextStyle =
    suite(weight, 1).copy(fontSize = (sizePx / density).sp, color = color, textAlign = TextAlign.Center)

/** 글꼴 배율을 1로 고정한 밀도 — sp 를 px 로 바꿀 때 기기 글꼴 크기 설정을 타지 않게 */
private fun DrawScope.fixedDensity(): Density = Density(density, 1f)

fun DrawScope.measureGameText(measurer: TextMeasurer, text: String, style: TextStyle): TextLayoutResult =
    measurer.measure(text, style, softWrap = false, maxLines = 1, density = fixedDensity())

/**
 * 한 줄 글자를 center 에 가운데 맞춰 그린다. maxWidth 보다 넓으면 minScale 까지 줄인다(iOS minimumScaleFactor).
 * pop = 통통 튀는 배율(가운데 기준)
 */
fun DrawScope.drawGameText(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    sizePx: Float,
    color: Color,
    maxWidth: Float = Float.POSITIVE_INFINITY,
    minScale: Float = 1f,
    pop: Float = 1f,
    alpha: Float = 1f,
) {
    if (text.isEmpty() || alpha <= 0f) return
    val layout = measureGameText(measurer, text, gameTextStyle(sizePx, color))
    val w = layout.size.width.toFloat()
    val fit = if (w > maxWidth) maxOf(minScale, maxWidth / w) else 1f
    scale(fit * pop, pivot = center) {
        drawText(layout, topLeft = Offset(center.x - w / 2, center.y - layout.size.height / 2f), alpha = alpha)
    }
}

/**
 * 여러 줄 글자(쉬는 창 안내처럼 \n 이 든 문구)를 width 안에서 가운데 정렬로 그린다.
 * 상자(width × height)를 넘으면 상자에 맞게 줄인다(긴 번역 대비). 자동 줄바꿈은 낱말 단위(phraseBreak) — 한국어가 낱말 중간에서 끊기지 않게
 */
fun DrawScope.drawGameParagraph(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    sizePx: Float,
    color: Color,
    width: Float,
    height: Float,
) {
    if (text.isEmpty()) return
    val layout = measurer.measure(
        text, gameTextStyle(sizePx, color).phraseBreak(), constraints = Constraints(maxWidth = width.toInt()), density = fixedDensity(),
    )
    val w = layout.size.width.toFloat()
    val h = layout.size.height.toFloat()
    val fit = min(1f, height / h)
    scale(fit, pivot = center) {
        drawText(layout, topLeft = Offset(center.x - w / 2, center.y - h / 2))
    }
}

/**
 * 테두리 글자. 투명도를 주면(깜빡이는 안내) 테두리·글자가 한 장처럼 함께 옅어지게 레이어로 묶는다 —
 * 겹친 자리가 탁해지지 않음(iOS compositingGroup)
 */
fun DrawScope.drawOutlinedText(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    sizePx: Float,
    color: Color = GamePalette.gold,
    outline: Color = GamePalette.ink,
    outlineWidth: Float,
    alpha: Float = 1f,
    maxWidth: Float = Float.POSITIVE_INFINITY,
) {
    if (text.isEmpty() || alpha <= 0f) return
    val fill = measureGameText(measurer, text, gameTextStyle(sizePx, color))
    val edge = measureGameText(measurer, text, gameTextStyle(sizePx, outline))
    val w = fill.size.width.toFloat()
    val h = fill.size.height.toFloat()
    val fit = min(1f, maxWidth / (w + outlineWidth * 2))
    val topLeft = Offset(center.x - w / 2, center.y - h / 2)
    val draw = {
        scale(fit, pivot = center) {
            for (i in 0 until 4) {
                val dx = if (i % 2 == 0) outlineWidth else -outlineWidth
                val dy = if (i < 2) outlineWidth else -outlineWidth
                drawText(edge, topLeft = topLeft + Offset(dx, dy))
            }
            drawText(fill, topLeft = topLeft)
        }
    }
    if (alpha >= 1f) {
        draw()
        return
    }
    val pad = outlineWidth * 2
    val bounds = Rect(topLeft.x - pad, topLeft.y - pad, topLeft.x + w + pad, topLeft.y + h + pad)
    drawIntoCanvas { canvas ->
        canvas.saveLayer(bounds, Paint().apply { this.alpha = alpha })
        draw()
        canvas.restore()
    }
}
