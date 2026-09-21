package site.smap.gugudan.features.lanerunner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import site.smap.gugudan.features.runner.drawBackdrop
import site.smap.gugudan.features.runner.drawDinoBody
import site.smap.gugudan.features.runner.drawDust
import site.smap.gugudan.features.runner.drawGroundBase
import site.smap.gugudan.features.runner.drawHills
import site.smap.gugudan.features.runner.drawObstacleShape
import site.smap.gugudan.features.runner.drawSparkles
import site.smap.gugudan.core.LaneObstacle
import site.smap.gugudan.features.runner.drawBackdrop
import site.smap.gugudan.features.runner.drawDinoBody
import site.smap.gugudan.features.runner.drawDust
import site.smap.gugudan.features.runner.drawGroundBase
import site.smap.gugudan.features.runner.drawHills
import site.smap.gugudan.features.runner.drawObstacleShape
import site.smap.gugudan.features.runner.drawSparkles
import site.smap.gugudan.core.LaneObstacleKind
import site.smap.gugudan.core.LaneOutcome
import site.smap.gugudan.core.LaneRunner
import site.smap.gugudan.core.LaneRunnerState
import site.smap.gugudan.core.LaneSegment
import site.smap.gugudan.core.RunnerPhase
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// 구구 레인 무대 렌더링 (웹 LaneScene.tsx 대응)
// 웹은 720×360 viewBox 를 preserveAspectRatio="xMinYMax slice" 로 아래 기준 삼아 채운다.
// 여기서도 같은 좌표계를 쓰고, 공룡·장애물·배경은 RunnerArt 를 공유한다.

private const val SCENE_W = 720f
private const val SCENE_H = 360f

/** 레인별 발 기준선(y). 위 레인이 먼 쪽이다. */
private val LANE_BASE = floatArrayOf(252f, 300f, 348f)
private const val LANE_TOP = 214f
private const val LANE_HEIGHT = 48f
private const val DINO_X = 104f
private const val TILE_W = 84f

/** 레인 사이 실수 위치의 기준선 — 전환 애니메이션을 부드럽게 잇는다 */
private fun baseAt(offset: Double): Float {
    val lower = floor(offset).toInt().coerceIn(0, 2)
    val upper = min(2, lower + 1)
    return LANE_BASE[lower] + (LANE_BASE[upper] - LANE_BASE[lower]) * (offset - lower).toFloat()
}

@Composable
fun LaneScene(game: LaneRunnerState, reducedMotion: Boolean = false, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.fillMaxSize()) {
        // 아래를 기준으로 채운다 (xMinYMax slice)
        val s = max(size.width / SCENE_W, size.height / SCENE_H)
        clipRect {
            translate(0f, size.height - SCENE_H * s) {
                scale(s, s, Offset.Zero) {
                    val travel = if (reducedMotion) 0.0 else game.distance * 20
                    val running = game.phase == RunnerPhase.RUNNING && game.hitMs == 0.0
                    val walking = !reducedMotion && running

                    drawBackdrop(SCENE_W, LANE_TOP)
                    drawHills(SCENE_W, LANE_TOP, travel)
                    drawLanes(game, travel)
                    drawGate(game, measurer)
                    drawObstacles(game)
                    drawLaneDino(game, travel, walking, reducedMotion)
                    drawLaneEffects(game, reducedMotion, measurer)
                }
            }
        }
    }
}

private fun DrawScope.drawLanes(game: LaneRunnerState, travel: Double) {
    drawGroundBase(SCENE_W, LANE_TOP, 146f)

    for (lane in 0..2) {
        val current = lane == game.lane && game.phase == RunnerPhase.RUNNING
        val color = if (current) Color(0xFFFFF6D8) else if (lane % 2 == 0) Color.White else Color(0xFF7A5A3C)
        drawRect(
            color.copy(alpha = if (current) 0.22f else 0.06f),
            topLeft = Offset(0f, LANE_TOP + lane * LANE_HEIGHT),
            size = Size(SCENE_W, LANE_HEIGHT),
        )
    }

    // 길 경계선 — 달리는 방향으로 흐른다
    val offset = (travel % 48.0).toFloat()
    for (row in 1..2) {
        val y = LANE_TOP + row * LANE_HEIGHT
        var x = -offset
        while (x < SCENE_W) {
            drawLine(
                Color(0xFFA98362).copy(alpha = 0.45f),
                Offset(x, y), Offset(min(x + 22f, SCENE_W), y),
                strokeWidth = 3f, cap = StrokeCap.Round,
            )
            x += 48f
        }
    }
}

private fun DrawScope.drawGate(game: LaneRunnerState, measurer: TextMeasurer) {
    val gate = game.gate ?: return
    val answer = game.question?.answer
    val reveal = game.outcome == LaneOutcome.CORRECT || game.outcome == LaneOutcome.WRONG

    translate(gate.x.toFloat() - 10f, 0f) {
        for (lane in 0..2) {
            val value = gate.values[lane]
            val correct = reveal && value == answer
            val chosen = reveal && value == game.given && value != answer
            val fill = if (correct) Color(0xFFE3F6C6) else if (chosen) Color(0xFFFBD9C4) else Color(0xFFFFF9E6)
            val stroke = if (correct) Color(0xFF2F8A57) else if (chosen) Color(0xFFC2562B) else Color(0xFFB98A55)
            val dim = if (reveal && !correct && !chosen) 0.45f else 1f
            val top = LANE_TOP + lane * LANE_HEIGHT + 5f

            drawRoundRect(
                fill.copy(alpha = dim), topLeft = Offset(0f, top),
                size = Size(TILE_W, LANE_HEIGHT - 10f), cornerRadius = CornerRadius(10f, 10f),
            )
            drawRoundRect(
                stroke.copy(alpha = dim), topLeft = Offset(0f, top),
                size = Size(TILE_W, LANE_HEIGHT - 10f), cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(3f),
            )
            val layout = measurer.measure(
                value.toString(),
                TextStyle(
                    color = Color(0xFF27463A).copy(alpha = dim),
                    fontSize = TextUnit(28f, TextUnitType.Sp),
                    fontWeight = FontWeight.Black,
                ),
            )
            drawText(
                layout,
                topLeft = Offset(
                    TILE_W / 2 - layout.size.width / 2,
                    top + (LANE_HEIGHT - 10f) / 2 - layout.size.height / 2,
                ),
            )
        }
    }
}

private fun DrawScope.drawObstacles(game: LaneRunnerState) {
    val ready = if (game.phase == RunnerPhase.READY) {
        listOf(
            LaneObstacle(-1, 0, 470.0, LaneObstacleKind.ROCK),
            LaneObstacle(-2, 2, 610.0, LaneObstacleKind.STUMP),
        )
    } else {
        emptyList()
    }
    // 위 레인부터 그려야 가까운 레인이 앞에 온다
    for (obstacle in (ready + game.obstacles).sortedBy { it.lane }) {
        translate(obstacle.x.toFloat(), 0f) {
            drawObstacleShape(obstacle.kind, LANE_BASE[obstacle.lane])
        }
    }
}

private fun DrawScope.drawLaneDino(
    game: LaneRunnerState, travel: Double, walking: Boolean, reducedMotion: Boolean,
) {
    val stride = if (walking) sin(travel / 18) else 0.0
    val bob = if (walking) abs(stride) * 1.5 else 0.0
    val offset = if (reducedMotion) game.lane.toDouble() else LaneRunner.offset(game)
    val base = baseAt(offset)
    val hurt = game.hitMs > 0 || game.phase == RunnerPhase.OVER
    val shifting = if (!reducedMotion && game.laneAnimMs > 0) {
        if (game.lane < game.laneFrom) -6f else 6f
    } else {
        0f
    }
    val impact = if (!reducedMotion && game.hitMs > 0) {
        (sin((LaneRunner.HIT_MS - game.hitMs) / 45) * (game.hitMs / LaneRunner.HIT_MS) * 3).toFloat()
    } else {
        0f
    }

    if (walking) drawDust(DINO_X, base, travel, alpha = 0.65f)
    drawOval(
        Color(0xFF355F45).copy(alpha = 0.23f),
        topLeft = Offset(DINO_X + 29f - 24f, base + 3f - 4f), size = Size(48f, 8f),
    )
    translate(DINO_X + impact, base - 63f - bob.toFloat()) {
        drawDinoBody(stride, airborne = game.laneAnimMs > 0 && !reducedMotion, hurt = hurt, tilt = shifting)
    }
}

private fun DrawScope.drawLaneEffects(game: LaneRunnerState, reducedMotion: Boolean, measurer: TextMeasurer) {
    val offset = if (reducedMotion) game.lane.toDouble() else LaneRunner.offset(game)
    val base = baseAt(offset)

    if (game.hitMs > 0) drawSparkles(DINO_X + 32f, base - 77f)

    // 정답 통과 직후 떠오르는 +1 / 속도 UP
    if (game.outcome != LaneOutcome.CORRECT || reducedMotion) return
    val gate = game.gate ?: return
    val reward = ((LaneRunner.JUDGE_X - gate.x) / 120).coerceIn(0.0, 1.0)
    if (reward <= 0 || reward >= 1) return

    val plusOne = measurer.measure(
        "+1",
        TextStyle(
            color = Color(0xFF316649).copy(alpha = (1 - reward).toFloat()),
            fontSize = TextUnit(20f, TextUnitType.Sp), fontWeight = FontWeight.Black,
        ),
    )
    drawText(
        plusOne,
        topLeft = Offset(
            DINO_X + 40f - plusOne.size.width / 2,
            base - 78f - (reward * 25).toFloat() - plusOne.size.height / 2,
        ),
    )

    if (LaneRunner.leveledUp(game)) {
        val speedUp = measurer.measure(
            "속도 UP!",
            TextStyle(
                color = Color(0xFFC2562B).copy(alpha = min(1.0, (1 - reward) * 2).toFloat()),
                fontSize = TextUnit(36f, TextUnitType.Sp), fontWeight = FontWeight.Black,
            ),
        )
        drawText(
            speedUp,
            topLeft = Offset(
                360f - speedUp.size.width / 2,
                120f - (reward * 12).toFloat() - speedUp.size.height / 2,
            ),
        )
    }
}

/** 화면 쪽에서 쓰는 보조 — 판정 뒤에도 게이트가 남아 있으면 문제를 계속 보여 준다 */
internal fun LaneRunnerState.isQuizVisible(): Boolean = question != null && gate != null
internal fun LaneRunnerState.isDodging(): Boolean = segment == LaneSegment.DODGE
