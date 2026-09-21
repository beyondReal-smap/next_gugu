package site.smap.gugudan.features.basket

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import site.smap.gugudan.core.Basket
import site.smap.gugudan.core.BasketOutcome
import site.smap.gugudan.core.BasketState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

// 구구 바구니 무대 렌더링 (웹 BasketScreen.tsx 의 Orchard SVG 대응)
// 웹은 360×(가변 높이) viewBox 를 쓴다. 네이티브는 360×380 고정 좌표계로 그리고
// 토끼·열매는 같은 팔레트·실루엣의 Canvas 도형으로 재구성했다(패스 단위 1:1 복제는 아니다).

private const val SCENE_W = 360f
private const val SCENE_H = 380f

private val SKY_BG = Color(0xFFE6F4EC)
private val LEAF_DARK = Color(0xFF548D61)
private val LEAF_LIGHT = Color(0xFF77A875)
private val TRUNK = Color(0xFF90734E)
private val FUR = Color(0xFFFFF7E5)
private val FRUIT_FILL = listOf(Color(0xFFFFCC75), Color(0xFFF6AD92), Color(0xFFD5DE87))
private val FRUIT_LINE = listOf(Color(0xFFD89A43), Color(0xFFD48B73), Color(0xFFA3B45C))

@Composable
fun OrchardScene(game: BasketState, reducedMotion: Boolean = false, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxSize()) {
        val s = min(size.width / SCENE_W, size.height / SCENE_H)
        translate((size.width - SCENE_W * s) / 2, (size.height - SCENE_H * s) / 2) {
            scale(s, s, Offset.Zero) {
                drawOrchardBackground()
                drawTrees()
                drawRabbit(game)
                drawFruits(game, measurer)
                drawBasketSparkles(game, reducedMotion)
            }
        }
    }
}

private fun DrawScope.drawOrchardBackground() {
    drawRect(SKY_BG, size = Size(SCENE_W, SCENE_H))
    // 해
    drawCircle(Color(0xFFFFDB78), radius = 30f, center = Offset(292f, 57f))
    // 구름
    drawCloud(23f, 80f, 1f)
    drawCloud(222f, 130f, 0.8f)

    // 잔디 언덕 3겹
    val far = Path().apply {
        moveTo(0f, 286f)
        quadraticTo(70f, 239f, 156f, 281f)
        quadraticTo(260f, 323f, 360f, 267f)
        lineTo(360f, 380f); lineTo(0f, 380f); close()
    }
    drawPath(far, Color(0xFFB5D7A4))

    val mid = Path().apply {
        moveTo(0f, 322f)
        quadraticTo(117f, 269f, 245f, 318f)
        quadraticTo(310f, 332f, 360f, 304f)
        lineTo(360f, 380f); lineTo(0f, 380f); close()
    }
    drawPath(mid, Color(0xFF8FBD85))

    val near = Path().apply {
        moveTo(0f, 350f)
        quadraticTo(140f, 310f, 360f, 352f)
        lineTo(360f, 380f); lineTo(0f, 380f); close()
    }
    drawPath(near, Color(0xFFDAEBBB))
}

private fun DrawScope.drawCloud(x: Float, y: Float, scale: Float) {
    val color = Color(0xFFFFFDF3).copy(alpha = 0.85f)
    drawOval(color, topLeft = Offset(x, y - 14 * scale), size = Size(46 * scale, 28 * scale))
    drawOval(color, topLeft = Offset(x + 24 * scale, y - 24 * scale), size = Size(44 * scale, 38 * scale))
    drawOval(color, topLeft = Offset(x + 50 * scale, y - 12 * scale), size = Size(34 * scale, 26 * scale))
}

private fun DrawScope.drawTrees() {
    drawLine(TRUNK, Offset(13f, 0f), Offset(13f, 223f), strokeWidth = 17f)
    drawLine(TRUNK, Offset(346f, 0f), Offset(346f, 221f), strokeWidth = 17f)
    drawLine(TRUNK, Offset(13f, 87f), Offset(48f, 63f), strokeWidth = 9f, cap = StrokeCap.Round)
    drawLine(TRUNK, Offset(346f, 71f), Offset(307f, 39f), strokeWidth = 9f, cap = StrokeCap.Round)

    for ((cx, cy, r) in listOf(
        Triple(0f, 19f, 59f), Triple(64f, 0f, 46f), Triple(316f, -2f, 55f), Triple(366f, 35f, 53f),
    )) {
        drawCircle(LEAF_DARK, radius = r, center = Offset(cx, cy))
    }
    for ((cx, cy, r) in listOf(Triple(-8f, 63f, 34f), Triple(33f, 24f, 34f), Triple(307f, 9f, 32f))) {
        drawCircle(LEAF_LIGHT, radius = r, center = Offset(cx, cy))
    }

    // 바닥의 작은 꽃
    listOf(32f, 90f, 266f, 328f).forEachIndexed { i, x ->
        val y = SCENE_H - 25f + (i % 2) * 12f
        drawLine(LEAF_DARK, Offset(x, y), Offset(x, y - 12f), strokeWidth = 2f)
        drawCircle(
            if (i % 2 == 1) Color(0xFFFFF9DE) else Color(0xFFF4BD97),
            radius = 5f, center = Offset(x, y - 13f),
        )
        drawCircle(Color(0xFFD99139), radius = 2f, center = Offset(x, y - 13f))
    }
}

private fun DrawScope.drawRabbit(game: BasketState) {
    val celebrating = game.outcome == BasketOutcome.CORRECT
    val sad = game.outcome == BasketOutcome.WRONG || game.outcome == BasketOutcome.MISSED
    val x = game.x.toFloat()

    // 그림자
    drawOval(Color(0xFF4F754D).copy(alpha = 0.18f), topLeft = Offset(x - 38f, 356f), size = Size(76f, 14f))

    // 귀
    for (side in listOf(-1f, 1f)) {
        rotate(side * 12f, pivot = Offset(x + side * 13f, 280f)) {
            drawOval(FUR, topLeft = Offset(x + side * 13f - 8f, 258f), size = Size(16f, 44f))
            drawOval(Color(0xFFEFC0AD), topLeft = Offset(x + side * 13f - 3.5f, 265f), size = Size(7f, 28f))
        }
    }

    translate(x, 0f) {
        // 몸통 · 머리
        drawOval(FUR, topLeft = Offset(-24f, 308f), size = Size(48f, 52f))
        drawOval(FUR, topLeft = Offset(-27f, 285f), size = Size(54f, 46f))
        drawOval(Color(0xFFEFB39F), topLeft = Offset(-23f, 311f), size = Size(10f, 6f))
        drawOval(Color(0xFFEFB39F), topLeft = Offset(13f, 311f), size = Size(10f, 6f))

        // 눈 — 정답이면 웃는 눈
        if (celebrating) {
            val eyes = Path().apply {
                moveTo(-14f, 307f); lineTo(-10f, 303f); lineTo(-6f, 307f)
                moveTo(6f, 307f); lineTo(10f, 303f); lineTo(14f, 307f)
            }
            drawPath(eyes, Color(0xFF543D2B), style = Stroke(2.5f, cap = StrokeCap.Round))
        } else {
            drawOval(Color(0xFF543D2B), topLeft = Offset(-12.5f, 302.5f), size = Size(5f, 7f))
            drawOval(Color(0xFF543D2B), topLeft = Offset(7.5f, 302.5f), size = Size(5f, 7f))
        }

        // 입
        val mouth = Path().apply {
            moveTo(-5f, if (sad) 320f else 316f)
            quadraticTo(0f, if (sad) 314f else 323f, 5f, if (sad) 320f else 316f)
        }
        drawPath(mouth, Color(0xFF543D2B), style = Stroke(2f, cap = StrokeCap.Round))

        // 발
        drawOval(FUR, topLeft = Offset(-27f, 353f), size = Size(22f, 12f))
        drawOval(FUR, topLeft = Offset(5f, 353f), size = Size(22f, 12f))

        // 바구니 손잡이
        val handle = Path().apply {
            moveTo(-25f, 332f)
            quadraticTo(0f, 300f, 25f, 332f)
        }
        drawPath(handle, Color(0xFF966137), style = Stroke(5f))

        // 바구니
        val basket = Path().apply {
            moveTo(-35f, 329f)
            lineTo(-28f, 356f)
            quadraticTo(0f, 366f, 28f, 356f)
            lineTo(35f, 329f)
            close()
        }
        drawPath(basket, Color(0xFFC8914D))
        drawPath(basket, Color(0xFF8E5B32), style = Stroke(2f))

        val weave = Path().apply {
            moveTo(-29f, 338f); lineTo(29f, 338f)
            moveTo(-26f, 347f); lineTo(26f, 347f)
            moveTo(-16f, 332f); lineTo(-13f, 357f)
            moveTo(0f, 332f); lineTo(0f, 360f)
            moveTo(13f, 332f); lineTo(10f, 357f)
        }
        drawPath(weave, Color(0xFFE6B973), style = Stroke(3f))

        drawLine(Color(0xFF794B2A), Offset(-35f, 329f), Offset(35f, 329f), strokeWidth = 6f, cap = StrokeCap.Round)
        drawOval(FUR, topLeft = Offset(-38f, 323f), size = Size(14f, 10f))
        drawOval(FUR, topLeft = Offset(24f, 323f), size = Size(14f, 10f))
    }
}

private fun DrawScope.drawFruits(game: BasketState, measurer: TextMeasurer) {
    val duration = Basket.fallDurationMs(game.score)
    val y = if (game.outcome != null) {
        Basket.FRUIT_CATCH_Y
    } else {
        Basket.FRUIT_START_Y +
            (Basket.FRUIT_CATCH_Y - Basket.FRUIT_START_Y) * min(1.0, game.elapsedMs / duration)
    }

    game.question.choices.forEachIndexed { index, value ->
        val caught = game.caughtIndex == index && game.outcome != null
        val correct = value == game.question.answer
        val x = Basket.fruitX(index, game.elapsedMs)
        val dim = if (game.outcome != null && !caught) 0.35f else 1f

        translate(x.toFloat(), y.toFloat()) {
            if (caught) {
                drawCircle(
                    (if (correct) Color(0xFF3D8256) else Color(0xFFB36140)),
                    radius = 31f, center = Offset.Zero,
                    style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))),
                )
            }
            // 꼭지 · 잎
            val stem = Path().apply {
                moveTo(0f, -21f)
                quadraticTo(-4f, -36f, 5f, -40f)
            }
            drawPath(stem, Color(0xFF735237).copy(alpha = dim), style = Stroke(3f, cap = StrokeCap.Round))
            val leaf = Path().apply {
                moveTo(2f, -26f)
                quadraticTo(7f, -43f, 21f, -38f)
                quadraticTo(18f, -23f, 2f, -26f)
                close()
            }
            drawPath(leaf, Color(0xFF598E56).copy(alpha = dim))

            // 열매 몸통
            val fruit = Path().apply {
                moveTo(0f, -23f)
                cubicTo(-32f, -36f, -37f, -1f, -15f, 20f)
                cubicTo(-6f, 28f, 6f, 28f, 16f, 20f)
                cubicTo(38f, -3f, 30f, -36f, 0f, -23f)
                close()
            }
            drawPath(fruit, FRUIT_FILL[index % 3].copy(alpha = dim))
            drawPath(fruit, FRUIT_LINE[index % 3].copy(alpha = dim), style = Stroke(2f))

            val shine = Path().apply {
                moveTo(-16f, -12f)
                quadraticTo(-24f, -7f, -23f, 2f)
            }
            drawPath(shine, Color(0xFFFFF8DD).copy(alpha = 0.8f * dim), style = Stroke(4f, cap = StrokeCap.Round))

            val layout = measurer.measure(
                value.toString(),
                TextStyle(
                    color = Color(0xFF553C27).copy(alpha = dim),
                    fontSize = TextUnit(25f, TextUnitType.Sp), fontWeight = FontWeight.Black,
                ),
            )
            drawText(layout, topLeft = Offset(-layout.size.width / 2f, -layout.size.height / 2f))
        }
    }
}

private fun DrawScope.drawBasketSparkles(game: BasketState, reducedMotion: Boolean) {
    if (game.outcome != BasketOutcome.CORRECT) return
    val burst = 1 - game.feedbackMs / Basket.CORRECT_FEEDBACK_MS
    for (i in -2..2) {
        val spread = if (reducedMotion) 17.0 else 15 + burst * 16
        val x = (game.x + i * spread).toFloat()
        val y = (260 - (if (reducedMotion) 0.0 else sin(burst * PI) * 45) + abs(i) * 9).toFloat()
        val star = Path().apply {
            moveTo(x, y - 8f); lineTo(x + 2.5f, y - 3f); lineTo(x + 8f, y - 2.2f)
            lineTo(x + 4f, y + 1.8f); lineTo(x + 4.9f, y + 7.3f); lineTo(x, y + 4.7f)
            lineTo(x - 4.9f, y + 7.3f); lineTo(x - 4f, y + 1.8f); lineTo(x - 8f, y - 2.2f)
            lineTo(x - 2.5f, y - 3f); close()
        }
        drawPath(star, Color(0xFFFFF5BC))
        drawPath(star, Color(0xFFDCA84A), style = Stroke(1.5f))
    }
}
