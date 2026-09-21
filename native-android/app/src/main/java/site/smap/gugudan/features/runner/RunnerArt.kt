package site.smap.gugudan.features.runner

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import site.smap.gugudan.core.LaneObstacleKind
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

// 구구 점프·구구 레인이 함께 쓰는 무대 아트 (웹 features/runner/art.tsx 대응)
// 웹은 SVG path 로 그린다. 여기서는 같은 팔레트·실루엣을 Canvas 도형으로 재구성했다
// (패스 단위 1:1 복제는 아니다). 좌표는 모두 웹 viewBox 기준.

internal val SKY = listOf(Color(0xFFBDE5E6), Color(0xFFE4F0D9), Color(0xFFF9EDBC))
internal val HILL_FAR = Color(0xFF8FC79B)
internal val HILL_NEAR = Color(0xFF6FAE7F)
internal val GRASS = Color(0xFFB4CA80)
internal val SOIL = listOf(Color(0xFFDEB886), Color(0xFFBF9267))
internal val DINO_BODY = listOf(Color(0xFF8BCE78), Color(0xFF4EAB72), Color(0xFF237B63))
internal val DINO_LINE = Color(0xFF28634E)
internal val BELLY = Color(0xFFF3F1B2)
internal val ROCK = Color(0xFFA98A73)
internal val STUMP = Color(0xFFA46D47)

/** 하늘 + 해. groundY 위쪽을 채운다. */
internal fun DrawScope.drawBackdrop(width: Float, groundY: Float) {
    drawRect(
        brush = Brush.verticalGradient(SKY, startY = 0f, endY = groundY),
        size = Size(width, groundY + 1),
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFFFFF7CE).copy(alpha = 0.95f), Color(0xFFFFF7CE).copy(alpha = 0f)),
            center = Offset(602f, 76f), radius = 54f,
        ),
        radius = 54f, center = Offset(602f, 76f),
    )
}

/** 원경/근경 언덕을 서로 다른 속도로 흘려 시차(parallax)를 만든다 */
internal fun DrawScope.drawHills(width: Float, groundY: Float, travel: Double) {
    fun band(offset: Double, step: Float, height: Float, color: Color) {
        val path = Path()
        var x = -(offset % step).toFloat() - step
        while (x < width + step) {
            path.moveTo(x, groundY)
            path.quadraticTo(x + step / 2, groundY - height, x + step, groundY)
            x += step
        }
        drawPath(path, color)
    }
    band(travel * 0.18, 300f, 96f, HILL_FAR)
    band(travel * 0.34, 210f, 62f, HILL_NEAR)
}

/** 잔디 띠 + 흙바닥 */
internal fun DrawScope.drawGroundBase(width: Float, groundY: Float, height: Float) {
    drawRect(GRASS, topLeft = Offset(0f, groundY), size = Size(width, 12f))
    drawRect(
        brush = Brush.verticalGradient(SOIL, startY = groundY, endY = groundY + height),
        topLeft = Offset(0f, groundY + 1), size = Size(width, height),
    )
}

/**
 * 공룡. 호출 전에 몸통 로컬 원점으로 translate 해 둔다.
 * @param stride -1~1 보행 위상, @param airborne 점프/레인 전환 중이면 다리를 접는다, @param tilt 기울기(도)
 */
internal fun DrawScope.drawDinoBody(stride: Double, airborne: Boolean, hurt: Boolean, tilt: Float = 0f) {
    rotate(tilt, pivot = Offset(32f, 32f)) {
        // 꼬리
        val tail = Path().apply {
            moveTo(19f, 39f)
            quadraticTo(3f, 43f, -7f, 30f)
            quadraticTo(-7f, 48f, 15f, 53f)
            close()
        }
        drawPath(tail, Color(0xFF388D65))
        drawPath(tail, DINO_LINE, style = Stroke(1.5f))

        // 뒷다리 (보행 위상 반대)
        drawLeg(18f, 44f, (-stride * 24 - if (airborne) 18.0 else 0.0).toFloat(), Color(0xFF2B795C))

        // 몸통
        val torso = Path().apply {
            addRoundRect(RoundRect(Rect(14f, 14f, 60f, 56f), CornerRadius(18f, 18f)))
        }
        drawPath(torso, Brush.linearGradient(DINO_BODY, start = Offset(14f, 14f), end = Offset(55f, 56f)))
        drawPath(torso, DINO_LINE, style = Stroke(1.8f))

        // 배
        drawRoundRect(BELLY, topLeft = Offset(30f, 30f), size = Size(20f, 22f), cornerRadius = CornerRadius(10f, 10f))

        // 머리
        val head = Path().apply {
            addRoundRect(RoundRect(Rect(36f, 2f, 62f, 26f), CornerRadius(11f, 11f)))
        }
        drawPath(head, Brush.linearGradient(DINO_BODY.take(2), start = Offset(36f, 2f), end = Offset(62f, 26f)))
        drawPath(head, DINO_LINE, style = Stroke(1.8f))

        // 눈 — 피격 시 X 표시
        if (hurt) {
            drawLine(Color(0xFF294E3C), Offset(44f, 11f), Offset(50f, 17f), strokeWidth = 2f, cap = StrokeCap.Round)
            drawLine(Color(0xFF294E3C), Offset(50f, 11f), Offset(44f, 17f), strokeWidth = 2f, cap = StrokeCap.Round)
        } else {
            drawOval(Color(0xFF243F32), topLeft = Offset(45f, 10f), size = Size(6.6f, 9.6f))
            drawOval(Color.White, topLeft = Offset(46.5f, 11f), size = Size(2.4f, 2.4f))
        }

        // 입 — 피격 시 아래로
        val mouth = Path().apply {
            moveTo(49f, if (hurt) 29f else 28f)
            quadraticTo(52.5f, if (hurt) 26f else 31f, 56f, if (hurt) 28f else 27f)
        }
        drawPath(mouth, Color(0xFF2E6449), style = Stroke(1.4f, cap = StrokeCap.Round))

        // 등 무늬
        val spikes = Path().apply {
            moveTo(21f, 27f)
            quadraticTo(21f, 20f, 28f, 19f)
        }
        drawPath(spikes, Color(0xFFB2E598).copy(alpha = 0.8f), style = Stroke(3f, cap = StrokeCap.Round))

        // 앞다리
        drawLeg(25f, 46f, (stride * 25 + if (airborne) 14.0 else 0.0).toFloat(), Color(0xFF4EAA70))
    }
}

private fun DrawScope.drawLeg(hipX: Float, hipY: Float, angle: Float, fill: Color) {
    rotate(angle, pivot = Offset(hipX + 4, hipY + 3)) {
        drawRoundRect(fill, topLeft = Offset(hipX, hipY), size = Size(11f, 19f), cornerRadius = CornerRadius(4f, 4f))
        drawRoundRect(fill, topLeft = Offset(hipX, hipY + 14), size = Size(16f, 6f), cornerRadius = CornerRadius(3f, 3f))
        drawRoundRect(
            DINO_LINE, topLeft = Offset(hipX, hipY), size = Size(11f, 19f),
            cornerRadius = CornerRadius(4f, 4f), style = Stroke(1.5f),
        )
    }
}

/** 달릴 때 발밑 먼지 (무대 좌표 기준) */
internal fun DrawScope.drawDust(footX: Float, footY: Float, travel: Double, alpha: Float = 0.45f) {
    for (i in 0..2) {
        val age = ((travel + i * 16) % 48) / 48
        val r = (2 + age * 4).toFloat()
        val ry = (1 + age * 2).toFloat()
        drawOval(
            Color(0xFFF7E2B6).copy(alpha = ((1 - age).toFloat() * alpha).coerceIn(0f, 1f)),
            topLeft = Offset(footX + 8f - (age * 37).toFloat() - r, footY - (sin(age * PI) * 5).toFloat() - ry),
            size = Size(r * 2, ry * 2),
        )
    }
}

/** 바위/그루터기. 호출 전에 장애물 x 로 translate 해 두고, 발이 닿는 y 를 baseY 로 준다. */
internal fun DrawScope.drawObstacleShape(kind: LaneObstacleKind, baseY: Float) {
    drawOval(Color(0xFF655845).copy(alpha = 0.2f), topLeft = Offset(-7f, baseY), size = Size(48f, 8f))

    when (kind) {
        LaneObstacleKind.ROCK -> {
            val rock = Path().apply {
                moveTo(0f, baseY)
                lineTo(4f, baseY - 32)
                quadraticTo(13f, baseY - 46, 23f, baseY - 36)
                lineTo(34f, baseY)
                close()
            }
            drawPath(
                rock,
                Brush.linearGradient(
                    listOf(Color(0xFFC4A68D), ROCK),
                    start = Offset(0f, baseY - 46), end = Offset(34f, baseY),
                ),
            )
            drawPath(rock, Color(0xFF785444), style = Stroke(1.8f))
            val shine = Path().apply {
                moveTo(9f, baseY - 26)
                lineTo(16f, baseY - 34)
                lineTo(21f, baseY - 27)
                close()
            }
            drawPath(shine, Color(0xFFEBC69A).copy(alpha = 0.85f))
        }
        LaneObstacleKind.STUMP -> {
            drawRoundRect(STUMP, topLeft = Offset(4f, baseY - 30), size = Size(30f, 30f), cornerRadius = CornerRadius(4f, 4f))
            drawRoundRect(
                Color(0xFF775137), topLeft = Offset(4f, baseY - 30), size = Size(30f, 30f),
                cornerRadius = CornerRadius(4f, 4f), style = Stroke(1.8f),
            )
            drawOval(Color(0xFFEAC596), topLeft = Offset(5.5f, baseY - 35), size = Size(26f, 12f))
            drawOval(Color(0xFFBC935E), topLeft = Offset(11.5f, baseY - 32), size = Size(14f, 6f), style = Stroke(1.3f))
            drawLine(
                Color(0xFF527D46), Offset(29f, baseY - 31), Offset(29f, baseY - 40),
                strokeWidth = 1.5f, cap = StrokeCap.Round,
            )
            drawOval(Color(0xFF84B269), topLeft = Offset(22f, baseY - 44), size = Size(8f, 6f))
        }
    }
}

/** 피격 반짝임 */
internal fun DrawScope.drawSparkles(centerX: Float, centerY: Float) {
    for (i in -1..1) {
        drawCircle(
            Color(0xFFF8D170), radius = 4f,
            center = Offset(centerX + i * 19f, centerY - if (i == 0) 8f else 0f),
        )
    }
}

/** 그림자 — 높이에 따라 작아진다 */
internal fun DrawScope.drawCharacterShadow(centerX: Float, baseY: Float, lift: Float) {
    val rx = 24f - lift / 9f
    val ry = max(1f, 4f - lift / 60f)
    drawOval(
        Color(0xFF355F45).copy(alpha = max(0.04f, 0.23f - lift / 850f)),
        topLeft = Offset(centerX - rx, baseY - ry), size = Size(rx * 2, ry * 2),
    )
}

/** 무대 밖에서도 쓰는 보조 — translate 헬퍼 재노출 */
internal inline fun DrawScope.at(x: Float, y: Float, block: DrawScope.() -> Unit) = translate(x, y) { block() }
