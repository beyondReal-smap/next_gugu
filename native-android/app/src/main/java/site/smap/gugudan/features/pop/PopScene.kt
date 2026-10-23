package site.smap.gugudan.features.pop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import site.smap.gugudan.core.Pop
import site.smap.gugudan.core.PopBalloon
import site.smap.gugudan.core.PopBalloonKind
import site.smap.gugudan.core.PopState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// 구구 팡팡 무대 렌더링 — 하늘·마을 지붕선·토끼 물총·풍선·보스·연출을 Canvas 도형으로 그린다.
// 좌표는 Core 와 같다: x 0~1 = 가로 비율, y 0(등장선) → 1(지붕선). 흔들림·입자는 화면 연출로만 한다(판정은 Core 중심 좌표).

private val SKY_TOP = Color(0xFFBFE3FF)
private val SKY_BOTTOM = Color(0xFFEFF8FF)
private val SLOW_TINT = Color(0xFFB9A7FF)
private val CLOUD = Color(0xFFFFFFFF)
private val ROOF_LINE = Color(0xFF8C6A4F)
private val ROOF_HURT = Color(0xFFE5484D)
private val HOUSE_WALL = Color(0xFFFFF4E0)
private val HOUSE_ROOFS = listOf(Color(0xFFE98A6B), Color(0xFF7FB7A4), Color(0xFFF2B95E), Color(0xFF9AA6E8))
private val GROUND = Color(0xFFA8D5A2)
private val FUR = Color(0xFFFFF7E5)
private val INK = Color(0xFF3B2F2A)
private val GUN = Color(0xFF4FA3E0)
private val GUN_RELOAD = Color(0xFFA7B0BA)
private val WATER = Color(0xFF6EC6FF)
private val BALLOON_FILLS = listOf(
    Color(0xFFFF9AA2), Color(0xFF8FD3FE), Color(0xFFB5EAD7), Color(0xFFFFDAC1), Color(0xFFC7B8FF), Color(0xFFFFF1A6),
)
private val GOLD = Color(0xFFFFC83D)
private val GOLD_RING = Color(0xFFFFE89A)
private val BOSS_FILL = Color(0xFF7D6BE0)
private val BOSS_SHIELD = Color(0xFF5AC8FA)
private val WRONG = Color(0xFFE5484D)

/** 놀이 영역 좌표 변환 — 화면(탭)과 그리기가 같은 값을 쓴다 */
internal class PopLayout(val width: Float, val height: Float) {
    val diameter = width * Pop.BALLOON_DIAMETER.toFloat()
    /** 등장선(y 0) — 풍선이 위쪽 밖에서 들어오게 화면 위로 반 개쯤 올린다 */
    val top = -diameter * 0.6f
    /** 지붕선(y 1) */
    val roofY = height * 0.8f
    val bossDiameter = width * Pop.BOSS_DIAMETER.toFloat()

    fun px(x: Double): Float = (x * width).toFloat()
    fun py(y: Double): Float = top + (y * (roofY - top)).toFloat()

    /** 보스는 처음부터 다 보이게 두고, y 1 에서 아래 끝이 지붕선에 닿는다 (연출 — 보스는 탭 대상이 아니다) */
    fun bossY(y: Double): Float {
        val start = bossDiameter / 2 + width * 0.025f   // iOS: 보스 반지름 + 10pt
        val end = roofY - bossDiameter / 2
        return start + (y * (end - start)).toFloat()
    }

    /** 화면 px → Core 좌표 (x, y, aspect) — 그리기(px·py)의 역변환 */
    fun toCore(x: Float, y: Float): Triple<Double, Double, Double> = Triple(
        (x / width).toDouble(), ((y - top) / (roofY - top)).toDouble(), ((roofY - top) / width).toDouble(),
    )

    /** 토끼 물총 끝 */
    val gun: Offset get() = Offset(width / 2, roofY + (height - roofY) * 0.28f)
}

@Composable
fun PopScene(
    game: PopState,
    clock: Double,
    fx: List<PopFx>,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxSize()) {
        val layout = PopLayout(size.width, size.height)
        drawSky(layout, clock, reducedMotion, slow = game.slowMs > 0)
        val hurt = fx.filterIsInstance<PopFx.Hurt>().any { clock - it.born < it.lifeMs }
        drawVillage(layout, hurt)
        game.boss?.let { drawBoss(layout, it.value, it.hitsLeft, it.y, measurer) }
        val shakes = fx.filterIsInstance<PopFx.Shake>()
        for (b in game.balloons) {
            val shake = shakes.lastOrNull { it.id == b.id }
            drawBalloon(layout, b, clock, shake, reducedMotion, measurer)
        }
        drawBunny(layout, reloading = game.reloadMs > 0)
        for (f in fx) drawFx(layout, f, clock, reducedMotion, measurer)
    }
}

private fun DrawScope.drawSky(layout: PopLayout, clock: Double, reducedMotion: Boolean, slow: Boolean) {
    drawRect(Brush.verticalGradient(listOf(SKY_TOP, SKY_BOTTOM), endY = layout.roofY))
    // 구름 — 천천히 흘러간다 (동작 줄이기에서는 멈춤)
    val drift = if (reducedMotion) 0f else ((clock / 90.0) % (layout.width * 1.4)).toFloat()
    listOf(0.12f to 0.18f, 0.62f to 0.1f, 0.4f to 0.36f).forEachIndexed { i, (cx, cy) ->
        val w = layout.width * (0.22f - i * 0.03f)
        var x = cx * layout.width + drift * (0.5f + i * 0.2f)
        x = ((x + w) % (layout.width + 2 * w)) - w
        val y = cy * layout.roofY
        drawOval(CLOUD.copy(alpha = 0.8f), Offset(x, y), Size(w, w * 0.42f))
        drawOval(CLOUD.copy(alpha = 0.8f), Offset(x + w * 0.25f, y - w * 0.18f), Size(w * 0.55f, w * 0.48f))
    }
    // 황금 풍선 느려짐 — 화면 톤만 바꾼다
    if (slow) drawRect(SLOW_TINT.copy(alpha = 0.18f))
}

private fun DrawScope.drawVillage(layout: PopLayout, hurt: Boolean) {
    val roof = layout.roofY
    drawRect(GROUND, Offset(0f, roof), Size(layout.width, layout.height - roof))
    // 집 다섯 채 — 지붕 꼭대기가 지붕선에 닿는다
    val houseW = layout.width / 5
    for (i in 0 until 5) {
        val left = i * houseW + houseW * 0.12f
        val w = houseW * 0.76f
        val wallTop = roof + w * 0.32f
        drawRect(HOUSE_WALL, Offset(left, wallTop), Size(w, layout.height - wallTop))
        val tri = Path().apply {
            moveTo(left - w * 0.08f, wallTop)
            lineTo(left + w / 2, roof)
            lineTo(left + w * 1.08f, wallTop)
            close()
        }
        drawPath(tri, HOUSE_ROOFS[i % HOUSE_ROOFS.size])
        drawRect(INK.copy(alpha = 0.25f), Offset(left + w * 0.38f, wallTop + w * 0.25f), Size(w * 0.24f, w * 0.3f))
    }
    // 지붕선 — 정답 풍선이 닿으면 붉게 번쩍인다
    drawLine(
        if (hurt) ROOF_HURT else ROOF_LINE.copy(alpha = 0.6f),
        Offset(0f, roof), Offset(layout.width, roof),
        strokeWidth = if (hurt) 6f else 3f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
    )
}

private fun DrawScope.drawBunny(layout: PopLayout, reloading: Boolean) {
    val gun = layout.gun
    val r = layout.diameter * 0.42f
    val cx = gun.x
    val cy = gun.y + r * 1.25f
    // 귀
    for (side in listOf(-1f, 1f)) {
        drawOval(FUR, Offset(cx + side * r * 0.45f - r * 0.2f, cy - r * 2.0f), Size(r * 0.4f, r * 1.2f))
    }
    // 머리
    drawCircle(FUR, r, Offset(cx, cy))
    drawCircle(INK.copy(alpha = 0.15f), r, Offset(cx, cy), style = Stroke(2f))
    drawCircle(INK, r * 0.1f, Offset(cx - r * 0.35f, cy - r * 0.1f))
    drawCircle(INK, r * 0.1f, Offset(cx + r * 0.35f, cy - r * 0.1f))
    drawCircle(Color(0xFFF2A7A0), r * 0.12f, Offset(cx, cy + r * 0.2f))
    // 물총 — 재장전 중이면 회색
    val color = if (reloading) GUN_RELOAD else GUN
    drawLine(color, Offset(cx, cy - r * 0.6f), gun, strokeWidth = r * 0.45f, cap = StrokeCap.Round)
    drawCircle(color, r * 0.32f, Offset(cx, cy - r * 0.55f))
}

private fun balloonFill(b: PopBalloon): Color =
    if (b.kind == PopBalloonKind.GOLDEN) GOLD else BALLOON_FILLS[b.id % BALLOON_FILLS.size]

private fun DrawScope.drawBalloon(
    layout: PopLayout,
    b: PopBalloon,
    clock: Double,
    shake: PopFx.Shake?,
    reducedMotion: Boolean,
    measurer: TextMeasurer,
) {
    val r = layout.diameter / 2
    // 흔들림은 연출만 — 진폭은 가로 폭의 1.2% (Core 탭 반경이 넉넉해 판정과 어긋나지 않는다)
    val sway = if (reducedMotion) 0f
        else (sin(clock / 650.0 + b.id * 1.7) * 0.012 * layout.width).toFloat()
    val shakeAge = shake?.let { clock - it.born }
    val jiggle = if (shakeAge != null && !reducedMotion && shakeAge < shake.lifeMs) {
        (sin(shakeAge / 22.0) * r * 0.18 * (1 - shakeAge / shake.lifeMs)).toFloat()
    } else 0f
    val c = Offset(layout.px(b.x) + sway + jiggle, layout.py(b.y))

    // 끈
    val string = Path().apply {
        moveTo(c.x, c.y + r)
        quadraticTo(c.x - r * 0.2f, c.y + r * 1.35f, c.x + r * 0.05f, c.y + r * 1.7f)
    }
    drawPath(string, INK.copy(alpha = 0.35f), style = Stroke(2f))
    // 매듭
    val knot = Path().apply {
        moveTo(c.x, c.y + r * 0.95f)
        lineTo(c.x - r * 0.12f, c.y + r * 1.12f)
        lineTo(c.x + r * 0.12f, c.y + r * 1.12f)
        close()
    }
    val fill = balloonFill(b)
    drawPath(knot, fill)
    if (b.kind == PopBalloonKind.GOLDEN) drawCircle(GOLD_RING.copy(alpha = 0.7f), r * 1.18f, c)
    drawOval(fill, Offset(c.x - r, c.y - r * 1.05f), Size(r * 2, r * 2.1f))
    // 반짝임
    drawOval(Color.White.copy(alpha = 0.55f), Offset(c.x - r * 0.62f, c.y - r * 0.72f), Size(r * 0.38f, r * 0.55f))
    if (shakeAge != null && shakeAge < shake.lifeMs) {
        drawOval(WRONG, Offset(c.x - r, c.y - r * 1.05f), Size(r * 2, r * 2.1f), style = Stroke(r * 0.12f))
    }
    drawCentered(measurer, b.value.toString(), c, r * 0.95f, INK)
}

private fun DrawScope.drawBoss(layout: PopLayout, value: Int, hitsLeft: Int, y: Double, measurer: TextMeasurer) {
    val r = layout.bossDiameter / 2
    val c = Offset(layout.width * Pop.BOSS_X.toFloat(), layout.bossY(y))
    // 방패 — 남은 짝꿍 수만큼 조각이 남는다
    val sweep = 360f / Pop.BOSS_HITS
    for (i in 0 until Pop.BOSS_HITS) {
        val alive = i < hitsLeft
        drawArc(
            if (alive) BOSS_SHIELD else BOSS_SHIELD.copy(alpha = 0.15f),
            startAngle = -90f + i * sweep + 4f, sweepAngle = sweep - 8f, useCenter = false,
            topLeft = Offset(c.x - r * 1.16f, c.y - r * 1.16f), size = Size(r * 2.32f, r * 2.32f),
            style = Stroke(r * 0.12f, cap = StrokeCap.Round),
        )
    }
    drawCircle(BOSS_FILL, r, c)
    drawOval(Color.White.copy(alpha = 0.3f), Offset(c.x - r * 0.62f, c.y - r * 0.75f), Size(r * 0.42f, r * 0.6f))
    // 눈썹 — 보스 표정
    drawLine(INK, Offset(c.x - r * 0.55f, c.y - r * 0.5f), Offset(c.x - r * 0.2f, c.y - r * 0.38f), strokeWidth = r * 0.08f, cap = StrokeCap.Round)
    drawLine(INK, Offset(c.x + r * 0.55f, c.y - r * 0.5f), Offset(c.x + r * 0.2f, c.y - r * 0.38f), strokeWidth = r * 0.08f, cap = StrokeCap.Round)
    drawCentered(measurer, value.toString(), Offset(c.x, c.y + r * 0.12f), r * 0.85f, Color.White)
}

private fun DrawScope.drawFx(layout: PopLayout, f: PopFx, clock: Double, reducedMotion: Boolean, measurer: TextMeasurer) {
    val age = clock - f.born
    if (age < 0 || age >= f.lifeMs) return
    val t = (age / f.lifeMs).toFloat()
    when (f) {
        is PopFx.Burst -> {
            val c = Offset(layout.px(f.x), layout.py(f.y).let { if (f.big) layout.bossY(f.y) else it })
            val base = if (f.big) layout.bossDiameter / 2 else layout.diameter / 2
            val color = if (f.golden) GOLD else Color.White
            // 고리 — 동작 줄이기에서도 남기는 색 변화
            drawCircle(color.copy(alpha = 0.8f * (1 - t)), base * (if (reducedMotion) 1f else 0.8f + t * 0.7f), c, style = Stroke(base * 0.12f))
            if (reducedMotion) return
            val count = if (f.big) 14 else 8
            for (i in 0 until count) {
                val a = 2 * PI * i / count
                val d = base * (0.6f + t * 1.4f)
                val p = Offset(c.x + (cos(a) * d).toFloat(), c.y + (sin(a) * d).toFloat())
                drawCircle(
                    (if (f.golden) GOLD else BALLOON_FILLS[i % BALLOON_FILLS.size]).copy(alpha = 1 - t),
                    base * 0.12f * (1 - t * 0.5f), p,
                )
            }
        }
        is PopFx.Label -> {
            val rise = if (reducedMotion) 0f else t * layout.diameter * 0.8f
            // "+10" 은 풍선 자리, 짝꿍 식은 보스 아래 (iOS 와 같은 자리)
            val y = if (f.text.startsWith("+")) layout.py(f.y) else layout.bossY(f.y) + layout.bossDiameter * 0.85f
            drawCentered(
                measurer, f.text, Offset(layout.px(f.x), y - rise),
                layout.diameter * 0.42f, INK.copy(alpha = 1 - t * t), outline = true,
            )
        }
        is PopFx.Shot -> {
            val target = Offset(layout.px(f.x), layout.py(f.y))
            val from = layout.gun
            if (reducedMotion) {
                drawLine(WATER.copy(alpha = 1 - t), from, target, strokeWidth = layout.diameter * 0.08f, cap = StrokeCap.Round)
            } else {
                val head = Offset(from.x + (target.x - from.x) * t, from.y + (target.y - from.y) * t)
                drawLine(WATER.copy(alpha = 0.5f), from, head, strokeWidth = layout.diameter * 0.06f, cap = StrokeCap.Round)
                drawCircle(WATER, layout.diameter * 0.1f, head)
            }
        }
        is PopFx.Shake, is PopFx.Hurt -> Unit   // 풍선·지붕선 그리기에서 처리
    }
}

/** 가운데 맞춘 굵은 숫자 — 크기는 px 로 받아 글꼴 크기 설정과 상관없이 풍선 안에 들어가게 한다 */
private fun DrawScope.drawCentered(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    heightPx: Float,
    color: Color,
    outline: Boolean = false,
) {
    val style = TextStyle(
        color = color, fontWeight = FontWeight.Black,
        fontSize = (heightPx * 0.72f).toSp(),
    )
    val layout = measurer.measure(text, style)
    val topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f)
    if (outline) {
        drawRoundRect(
            Color.White.copy(alpha = 0.75f * color.alpha),
            topLeft = topLeft - Offset(8f, 2f),
            size = Size(layout.size.width + 16f, layout.size.height + 4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(layout.size.height / 2f),
        )
    }
    drawText(layout, topLeft = topLeft)
}
