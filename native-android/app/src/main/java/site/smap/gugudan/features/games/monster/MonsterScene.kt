package site.smap.gugudan.features.games.monster

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import site.smap.gugudan.R
import site.smap.gugudan.features.games.shared.GameArt
import site.smap.gugudan.features.games.shared.GameImages
import site.smap.gugudan.features.games.shared.GamePalette
import site.smap.gugudan.features.games.shared.SparklesState
import site.smap.gugudan.features.games.shared.drawGameImage
import site.smap.gugudan.features.games.shared.drawGameText
import site.smap.gugudan.features.games.shared.drawSparkles
import site.smap.gugudan.features.games.shared.multiplyFilter
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

// 구구 몬스터 무대 그리기 (Unity 시험판 ForestStageView·MonsterView·Sparkles 의 그림 부분, iOS MonsterScene.swift 대응)
// 엔진이 프레임마다 움직인 상태(MonsterStage·SparklesState)를 Canvas 한 장에 그린다.
// 그리는 순서 = Unity 정렬 순서: 숲 배경 → 반딧불 → 그루터기 → 터짐 고리 → 기절 몬스터 → 몬스터 → 체력 숫자 → 반짝이
// 처치 화면 흔들림은 숲 배경을 뺀 무대만 옮긴다 — 숲 그림은 세로로 화면에 꼭 맞아(가로만 넘침) 옮기면 위아래 끝이 비어 보임

object MonsterScene {
    /** 그루터기 폭(유닛)과 윗면(나이테) 중심 위치(그림 위 끝에서, 높이 대비 — 몬스터 발이 여기에 닿게) */
    const val STUMP_WIDTH = 4.2
    const val STUMP_TOP_FACE = 0.27
    const val FIREFLY_COUNT = 7
    const val FIREFLY_WIDTH = 0.22
    val FIREFLY_COLOR = Color(1f, 0.95f, 0.55f)
    /** 오답 때 몸을 붉게(그림 색에 곱함 — 초록·파랑을 이만큼 뺌) */
    const val HIT_TINT = 0.5
    /** 처치 순간 기절 그림을 하얗게(그림 모양대로 흰색으로 덮음) */
    val WHITE_FLASH = ColorFilter.tint(Color.White)

    /** 미리 풀어 둘 무대 그림 */
    val IMAGES: List<Int> = listOf(
        R.drawable.game_forest, R.drawable.game_stump, R.drawable.game_sparkle, R.drawable.game_star,
    ) + MonsterArt.ALL_MONSTERS
}

/** time = 통통·반딧불용 흐른 시간(초) */
fun DrawScope.drawMonsterScene(
    stage: MonsterStage,
    sparkles: SparklesState,
    time: Double,
    images: GameImages,
    measurer: TextMeasurer,
) {
    val camera = MonsterCamera(size)
    drawForest(camera, images)
    val shake = stage.impact
    translate(shake.dx.toFloat() * camera.unitPx, -shake.dy.toFloat() * camera.unitPx) {
        drawFireflies(camera, images, time)
        drawStump(camera, images)
        drawPop(stage, camera, images)
        drawMonster(stage, camera, images, measurer)
        drawSparkles(sparkles, images, camera.unitPx, camera::point)
    }
}

// MARK: 숲·반딧불·그루터기

/** 화면을 꽉 덮도록(비율 유지, 넘치는 쪽은 잘림). 숲 그림 가운데 빈터가 몬스터 자리와 맞도록 화면 가운데 */
private fun DrawScope.drawForest(camera: MonsterCamera, images: GameImages) {
    val px = MonsterArt.FOREST_PIXELS
    val cover = max(camera.size.width / px.width, camera.size.height / px.height)
    drawGameImage(
        images[R.drawable.game_forest], Offset(camera.size.width / 2, camera.size.height / 2),
        Size(px.width * cover, px.height * cover),
    )
}

/** 화면 양옆 위주로 천천히 맴돌며 깜빡임(가운데 몬스터·문제 판을 가리지 않게) */
private fun DrawScope.drawFireflies(camera: MonsterCamera, images: GameImages, t: Double) {
    val image = images[R.drawable.game_sparkle]
    val w = MonsterScene.FIREFLY_WIDTH.toFloat() * camera.unitPx
    val h = w * GameArt.SPARKLE_PIXELS.height / GameArt.SPARKLE_PIXELS.width
    val halfW = camera.halfWidth
    val halfH = camera.halfHeight
    val filter = multiplyFilter(MonsterScene.FIREFLY_COLOR)
    for (i in 0 until MonsterScene.FIREFLY_COUNT) {
        val k = i.toDouble()
        val side = if (i % 2 == 0) -1.0 else 1.0
        val baseX = side * halfW * (0.55 + 0.3 * frac(k * 0.37))
        val baseY = -halfH * 0.35 + (halfH * 0.45 + halfH * 0.35) * frac(k * 0.61 + 0.2)
        val x = baseX + sin(t * (0.35 + 0.05 * k) + k) * 0.45
        val y = baseY + sin(t * (0.5 + 0.07 * k) + k * 2.1) * 0.35
        val alpha = 0.25 + 0.75 * max(0.0, sin(t * (1.3 + 0.2 * k) + k * 1.3))
        drawGameImage(image, camera.point(x, y), Size(w, h), alpha = alpha.toFloat(), colorFilter = filter)
    }
}

private fun DrawScope.drawStump(camera: MonsterCamera, images: GameImages) {
    val px = MonsterArt.STUMP_PIXELS
    val height = MonsterScene.STUMP_WIDTH * (px.height / px.width)
    // 윗면 중심 = 중심 + (0.5 − 윗면 비율)·h  →  중심 = 발 − (0.5 − 윗면 비율)·h
    val p = camera.point(0.0, MonsterStage.feetY - (0.5 - MonsterScene.STUMP_TOP_FACE) * height)
    drawGameImage(
        images[R.drawable.game_stump], p,
        Size(MonsterScene.STUMP_WIDTH.toFloat() * camera.unitPx, height.toFloat() * camera.unitPx),
    )
}

// MARK: 몬스터

/** 처치: 옅은 고리가 퍼지고, 그 위로 기절한 몬스터가 커지고 기울며 떠오르다 사라진다 */
private fun DrawScope.drawPop(stage: MonsterStage, camera: MonsterCamera, images: GameImages) {
    val pop = stage.pop ?: return
    val u = camera.unitPx
    val t = pop.t
    val origin = camera.point(pop.x, pop.y)
    rotate((12 * t).toFloat(), pivot = origin) {   // Unity Z −12° = 화면에서 시계 방향
        // 고리는 옅게 — 진하면 기절한 몬스터 뒤를 원판으로 덮어 그림이 탁해짐
        val ring = (MonsterStage.BODY_SIZE * (1 + 0.8 * t)).toFloat() * u
        drawCircle(MonsterArt.tint(pop.kind), radius = ring / 2, center = origin, alpha = (0.4 * (1 - t)).toFloat())
        // 기절한 몬스터: 처음엔 또렷하게 보이도록 제곱으로 흐려짐
        val px = MonsterArt.pixels(pop.kind, defeated = true)
        val h = (MonsterStage.BODY_SIZE * (1 + 0.25 * t)).toFloat() * u
        val w = h * px.width / px.height
        val lift = (0.8 * t).toFloat() * u
        val image = images[MonsterArt.res(pop.kind, defeated = true)]
        val alpha = (1 - t * t).toFloat()
        drawGameImage(image, Offset(origin.x, origin.y - lift), Size(w, h), alpha = alpha)
        // 처치 순간 하얗게 번쩍 — 같은 그림을 흰색으로 덮어 그리고 빠르게 걷어 낸다
        val flash = stage.popFlash.toFloat()
        if (flash > 0) {
            drawGameImage(image, Offset(origin.x, origin.y - lift), Size(w, h), alpha = alpha * flash, colorFilter = MonsterScene.WHITE_FLASH)
        }
    }
}

/** 몬스터 + 배 패널 위 체력 숫자. 숨 쉬듯 찌그러짐·깡총 찌그러짐(발 기준)은 숫자까지 함께, 붉어짐은 몸에만 */
private fun DrawScope.drawMonster(stage: MonsterStage, camera: MonsterCamera, images: GameImages, measurer: TextMeasurer) {
    if (!stage.visible || stage.hp <= 0) return
    val u = camera.unitPx
    val center = camera.point(stage.x, stage.y)
    val squash = stage.squash.toFloat()
    val h = MonsterStage.BODY_SIZE.toFloat() * u
    val hop = stage.hop
    val feet = Offset(center.x, center.y + h * (0.5f - GameArt.FEET_PAD))
    scale(hop.sx.toFloat(), hop.sy.toFloat(), pivot = feet) {
        scale(squash, 2 - squash, pivot = center) {
            val px = MonsterArt.pixels(stage.kind)
            val w = h * px.width / px.height
            val filter = if (stage.flash > 0) {
                val k = (1 - MonsterScene.HIT_TINT * stage.flash).toFloat()
                multiplyFilter(Color(1f, k, k))
            } else {
                null
            }
            drawGameImage(images[MonsterArt.res(stage.kind)], center, Size(w, h), colorFilter = filter)

            val panel = Offset(center.x + MonsterArt.PANEL_CENTER.x * h, center.y - MonsterArt.PANEL_CENTER.y * h)
            drawGameText(measurer, "${stage.hp}", panel, MonsterStage.HP_EM.toFloat() * u, GamePalette.background)
        }
    }
}

private fun frac(v: Double): Double = v - floor(v)
