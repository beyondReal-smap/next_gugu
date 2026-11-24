package site.smap.gugudan.features.games.stairs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import site.smap.gugudan.R
import site.smap.gugudan.features.games.shared.GameArt
import site.smap.gugudan.features.games.shared.GameImages
import site.smap.gugudan.features.games.shared.GamePalette
import site.smap.gugudan.features.games.shared.SparklesState
import site.smap.gugudan.features.games.shared.drawGameImage
import site.smap.gugudan.features.games.shared.drawGameText
import site.smap.gugudan.features.games.shared.drawGugu
import site.smap.gugudan.features.games.shared.drawSparkles
import site.smap.gugudan.features.games.shared.multiplyFilter
import kotlin.math.sin

// 구구 계단 무대 그리기 (Unity 시험판 StairsView·SkyView·StormCloudView·BestFlagView·Sparkles 의 그림 부분, iOS StairsScene.swift 대응)
// 엔진이 프레임마다 움직인 상태(StairsStage·SparklesState)를 Canvas 한 장에 그린다.
// 그리는 순서 = Unity 정렬 순서: 하늘·장식·언덕 → 깃발 점선 → 발판 → 깃발 → 숫자 → 보물 상자 → 착지 먼지 → 구구 → 장신구·반짝이 → 먹구름

object StairsScene {
    /** 발판 숫자 글자 크기(유닛) — Unity TextMesh 글자 크기 96 × 0.075 */
    const val LABEL_EM = 0.72
    /** 먹구름 띠: 흔들려도 화면 양옆이 비지 않게 더 넓게(유닛)·좌우 일렁임(유닛) */
    const val CLOUD_SIDE_MARGIN = 1.2
    const val CLOUD_DRIFT = 0.25
    /** 띠 그림에서 "구름 윗면 선"의 위치(띠 높이 대비, 위에서부터) — 이 선이 발에 닿으면 잡힘 */
    const val CLOUD_TOP_LINE = 0.25
    /** 띠 아래 단색(그림 아래 가장자리 색과 같아야 이음매가 안 보임) */
    val CLOUD_FILL = Color(0xFF5E667C)
    val DUST_FILL = Color(0xFFFFF3D6)
    const val FLAG_HEIGHT = 1.1
    const val FLAG_EDGE_INSET = 0.3
    const val DASHES = 16
    const val DASH_WIDTH = 0.32
    const val DASH_HEIGHT = 0.07

    /** 미리 풀어 둘 무대 그림 */
    val IMAGES: List<Int> = listOf(
        R.drawable.game_platform_jelly, R.drawable.game_flag, R.drawable.game_chest_closed, R.drawable.game_chest_open,
        R.drawable.game_storm_cloud, R.drawable.game_sparkle, R.drawable.game_star,
    ) + GameArt.GuguPose.entries.map { it.res } + StairsSky.IMAGES

    /** 최고 기록 깃발 가운데(월드) — 엔진이 신기록 반짝이를 터뜨릴 자리 */
    fun flagCenter(camera: StairsCamera, best: Int, pop: Double = 1.0): Pair<Double, Double> {
        val height = FLAG_HEIGHT * pop
        val width = height * GameArt.FLAG_PIXELS.width / GameArt.FLAG_PIXELS.height
        return (camera.halfWidth - FLAG_EDGE_INSET - width * 0.5) to (StairsStage.feetY(best.toDouble()) + height * 0.5)
    }
}

/** cloudTop = 먹구름 윗면(칸), time = 반짝임·일렁임용 흐른 시간(초) */
fun DrawScope.drawStairsScene(
    stage: StairsStage,
    sparkles: SparklesState,
    cloudTop: Double,
    time: Double,
    images: GameImages,
    measurer: TextMeasurer,
) {
    val camera = StairsCamera(size, stage.cameraY)
    drawStairsSky(camera, images, time)
    drawFlagLine(stage, camera)
    drawPlatforms(stage, camera, images)
    drawFlag(stage, camera, images)
    drawLabels(stage, camera, measurer)
    drawChests(stage, camera, images)
    drawDust(stage, camera)
    drawCharacter(stage, camera, images)
    drawSparkles(sparkles, images, camera.unitPx, camera::point)
    drawStormCloud(stage, camera, images, cloudTop, time)
}

// MARK: 발판·숫자

private fun DrawScope.drawPlatforms(stage: StairsStage, camera: StairsCamera, images: GameImages) {
    val image = images[R.drawable.game_platform_jelly]
    val size = Size(StairsStage.PLATFORM_WIDTH.toFloat() * camera.unitPx, StairsStage.PLATFORM_HEIGHT.toFloat() * camera.unitPx)
    for (row in stage.rows.values.sortedBy { it.height }) {
        row.lanes.forEachIndexed { i, lane ->
            if (!lane.visible || lane.alpha <= 0) return@forEachIndexed
            val p = camera.point(StairsStage.laneX(i), StairsStage.platformY(row.height) - StairsStage.fallDrop(lane))
            // 무채색 발판에 단 색·정답 초록·오답 빨강을 곱해 입힌다
            drawGameImage(image, p, size, lane.alpha.toFloat(), multiplyFilter(lane.color.color))
        }
    }
}

private fun DrawScope.drawLabels(stage: StairsStage, camera: StairsCamera, measurer: TextMeasurer) {
    val px = StairsScene.LABEL_EM.toFloat() * camera.unitPx
    for (row in stage.rows.values) {
        row.lanes.forEachIndexed { i, lane ->
            if (!lane.visible || lane.alpha <= 0 || lane.label.isEmpty()) return@forEachIndexed
            val p = camera.point(StairsStage.laneX(i), StairsStage.labelY(row.height) - StairsStage.fallDrop(lane))
            drawGameText(measurer, lane.label, p, px, GamePalette.text, alpha = lane.alpha.toFloat())
        }
    }
}

// MARK: 보물 상자·구구

private fun DrawScope.drawChests(stage: StairsStage, camera: StairsCamera, images: GameImages) {
    for (chest in stage.chests) {
        val p = camera.point(StairsStage.chestCenterX(chest), StairsStage.chestCenterY(chest))
        val s = (StairsStage.CHEST_SIZE * chest.scale).toFloat() * camera.unitPx
        if (s <= 0) continue
        val image = images[if (chest.open) R.drawable.game_chest_open else R.drawable.game_chest_closed]
        drawGameImage(image, p, Size(s, s))
    }
}

/** 착지 먼지 — 발판 위 작은 크림색 원(구구 뒤). 흰색은 밝은 하늘에 묻혀 크림색 */
private fun DrawScope.drawDust(stage: StairsStage, camera: StairsCamera) {
    for (d in stage.dust) {
        if (!d.alive) continue
        drawCircle(
            StairsScene.DUST_FILL, radius = d.radius.toFloat() * camera.unitPx, center = camera.point(d.x, d.y), alpha = d.alpha.toFloat(),
        )
    }
}

/**
 * 구구의 원점 = 발. 그림은 발이 원점에 닿도록 위로 올려 두어, 착지 찌그러짐·숨쉬기·점프 늘어남이 발판에 붙은 채 일어난다.
 * 점프 기울기는 몸 가운데를 축으로(공중에서 몸이 가는 쪽으로 쏠림). 장신구도 같은 변환 안
 */
private fun DrawScope.drawCharacter(stage: StairsStage, camera: StairsCamera, images: GameImages) {
    val side = StairsStage.CHARACTER_SIZE.toFloat() * camera.unitPx
    val feet = camera.point(stage.characterX, stage.characterY)
    val squash = stage.characterScale
    // 그림 중심 = 발 위 (0.5 - FEET_PAD) × 크기
    val body = Offset(0f, -side * (0.5f - GameArt.FEET_PAD))
    withTransform({
        translate(feet.x, feet.y)
        rotate(stage.characterTilt.toFloat(), pivot = body)
        scale(squash.x.toFloat(), squash.y.toFloat(), pivot = Offset.Zero)
    }) {
        drawGugu(images, stage.pose, stage.accessory, body, side)
    }
}

// MARK: 최고 기록 깃발

private fun DrawScope.drawFlagLine(stage: StairsStage, camera: StairsCamera) {
    val flag = stage.flag
    if (!flag.visible || flag.lineAlpha <= 0) return
    val y = StairsStage.feetY(flag.best.toDouble())
    val step = camera.halfWidth * 2 / StairsScene.DASHES
    val w = StairsScene.DASH_WIDTH.toFloat() * camera.unitPx
    val h = StairsScene.DASH_HEIGHT.toFloat() * camera.unitPx
    val color = Color.White.copy(alpha = flag.lineAlpha.toFloat())
    for (i in 0 until StairsScene.DASHES) {
        val p = camera.point(-camera.halfWidth + step * (i + 0.5), y)
        drawRect(color, Offset(p.x - w / 2, p.y - h / 2), Size(w, h))
    }
}

/** 깃발: 깃대 아래 끝이 선에 닿게, 화면 오른쪽 끝에서 살짝 안쪽. 깃발이 화면 안쪽(왼쪽)으로 펄럭이게 뒤집음 */
private fun DrawScope.drawFlag(stage: StairsStage, camera: StairsCamera, images: GameImages) {
    val flag = stage.flag
    if (!flag.visible) return
    val pop = flag.popScale
    val height = StairsScene.FLAG_HEIGHT * pop
    val width = height * GameArt.FLAG_PIXELS.width / GameArt.FLAG_PIXELS.height
    val (x, y) = StairsScene.flagCenter(camera, flag.best, pop)
    val p = camera.point(x, y)
    scale(-1f, 1f, pivot = p) {
        drawGameImage(images[R.drawable.game_flag], p, Size(width.toFloat() * camera.unitPx, height.toFloat() * camera.unitPx))
    }
}

// MARK: 먹구름

/** 화면 폭 전체 띠(몽실몽실한 윗부분 + 얼굴·번개) + 그 아래 같은 색 사각형. 잡히면 조금 더 차올라 구구를 삼킨다 */
private fun DrawScope.drawStormCloud(stage: StairsStage, camera: StairsCamera, images: GameImages, cloudTop: Double, time: Double) {
    val swallow = stage.swallow
    val lineY = StairsStage.feetY(cloudTop) + StairsStage.SWALLOW_RISE * (1 - (1 - swallow) * (1 - swallow))
    val px = GameArt.STORM_CLOUD_PIXELS
    val width = camera.halfWidth * 2 + StairsScene.CLOUD_SIDE_MARGIN * 2
    val height = width * px.height / px.width
    val x = sin(time * 0.7) * StairsScene.CLOUD_DRIFT
    // 띠 위 끝 = 중심 + h/2, 윗면 선 = 위 끝 − f·h  →  중심 = 선 + h·(f − 0.5)
    val bandCenterY = lineY + height * (StairsScene.CLOUD_TOP_LINE - 0.5)
    val p = camera.point(x, bandCenterY)
    val w = width.toFloat() * camera.unitPx
    val h = height.toFloat() * camera.unitPx
    drawGameImage(images[R.drawable.game_storm_cloud], p, Size(w, h))
    // 띠 아래 가장자리에서 아래로 화면 끝까지. 살짝 겹쳐 이음매 틈 방지
    val fillTop = p.y + h / 2 - 0.02f * camera.unitPx
    if (fillTop < size.height) {
        drawRect(StairsScene.CLOUD_FILL, Offset(0f, fillTop), Size(size.width, size.height - fillTop))
    }
}
