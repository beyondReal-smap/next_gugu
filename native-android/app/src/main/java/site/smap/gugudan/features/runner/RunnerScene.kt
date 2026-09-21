package site.smap.gugudan.features.runner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import site.smap.gugudan.core.LaneObstacleKind
import site.smap.gugudan.core.Runner
import site.smap.gugudan.core.RunnerOutcome
import site.smap.gugudan.core.RunnerPhase
import site.smap.gugudan.core.RunnerState
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

// 구구 점프 무대 렌더링 (iOS RunnerScene 이식)
// 웹 SVG viewBox(720×260) 좌표계를 그대로 쓰고, 공룡·장애물·배경은 구구 레인과 공유하는
// RunnerArt 가 그린다.

private const val SCENE_W = 720f
private const val SCENE_H = 260f
private const val GROUND_Y = 220f
private const val DINO_X = 104f

@Composable
fun RunnerScene(game: RunnerState, reducedMotion: Boolean = false, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val s = min(size.width / SCENE_W, size.height / SCENE_H)
        translate((size.width - SCENE_W * s) / 2, (size.height - SCENE_H * s) / 2) {
            scale(s, s, Offset.Zero) {
                val travel = if (reducedMotion) 0.0 else game.distance * 20
                val jump = Runner.jump(game)
                val walking = game.phase == RunnerPhase.RUNNING && game.hitMs == 0.0

                drawBackdrop(SCENE_W, GROUND_Y)
                drawHills(SCENE_W, GROUND_Y, travel)
                drawGround(travel)
                translate(game.obstacleX.toFloat(), 0f) {
                    drawObstacleShape(
                        if (game.round % 2 == 0) LaneObstacleKind.ROCK else LaneObstacleKind.STUMP,
                        GROUND_Y,
                    )
                }
                drawDino(game, jump, travel, walking, reducedMotion)
                drawEffects(game, jump, reducedMotion)
            }
        }
    }
}

private fun DrawScope.drawGround(travel: Double) {
    drawGroundBase(SCENE_W, GROUND_Y, 39f)
    // 흙길 위 자국 — 120 간격으로 반복해 달리는 느낌을 준다
    var x = -(travel % 120.0).toFloat()
    while (x < SCENE_W + 120) {
        drawRect(
            Color(0xFFA98362).copy(alpha = 0.35f),
            topLeft = Offset(x, GROUND_Y + 16), size = Size(46f, 4f),
        )
        x += 120f
    }
}

private fun DrawScope.drawDino(
    game: RunnerState, jump: Double, travel: Double, walking: Boolean, reducedMotion: Boolean,
) {
    val stride = if (walking && !reducedMotion) sin(travel / 16) else 0.0
    val bob = if (walking && !reducedMotion) abs(sin(travel / 32)) * 2 else 0.0
    val baseY = GROUND_Y - 63f - jump.toFloat() - bob.toFloat()

    drawCharacterShadow(centerX = 133f, baseY = GROUND_Y + 1, lift = jump.toFloat())
    translate(DINO_X, baseY) {
        drawDinoBody(stride, airborne = jump > 0, hurt = game.hitMs > 0)
    }
    if (walking && !reducedMotion) drawDust(DINO_X, GROUND_Y, travel)
}

private fun DrawScope.drawEffects(game: RunnerState, jump: Double, reducedMotion: Boolean) {
    if (reducedMotion) return
    // 점프 중 속도선
    if (jump > 15) {
        val y = GROUND_Y - 22f - jump.toFloat()
        drawLine(Color(0xFFFFF6CE), Offset(96f, y), Offset(81f, y), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(Color(0xFFFFF6CE), Offset(90f, y + 8), Offset(71f, y + 8), strokeWidth = 3f, cap = StrokeCap.Round)
    }
    if (game.hitMs > 0) drawSparkles(136f, GROUND_Y - 77f)
}

/** 화면 쪽에서 정답 여부 표시에 쓰는 보조 (outcome 별 색 결정은 화면 책임) */
internal fun RunnerState.isRevealed(): Boolean = outcome != null
internal fun RunnerState.isCorrect(): Boolean = outcome == RunnerOutcome.CORRECT
