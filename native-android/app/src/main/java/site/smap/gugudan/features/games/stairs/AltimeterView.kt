package site.smap.gugudan.features.games.stairs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import site.smap.gugudan.R
import site.smap.gugudan.core.games.Accessory
import site.smap.gugudan.core.games.Altimeter
import site.smap.gugudan.core.games.SkyZone
import site.smap.gugudan.features.games.shared.GameArt
import site.smap.gugudan.features.games.shared.GameImages
import site.smap.gugudan.features.games.shared.GameViewport
import site.smap.gugudan.features.games.shared.drawGameImageFit
import site.smap.gugudan.features.games.shared.drawGugu
import site.smap.gugudan.features.games.shared.drawPill
import kotlin.math.exp

// 구구 계단 고도계 (Unity 시험판 AltimeterView.cs 이식, iOS AltimeterView.swift 대응) — 화면 왼쪽 세로 막대.
// 아래 풀밭에서 위로 구간 아이콘(구름 = 하늘 마을, 무지개, 달)을 눈금으로 두고, 구구(지금 높이)·먹구름(구름 윗면)·
// 빨간 깃발(지난 최고 기록)이 오르내린다. "얼마나 올라왔고 구름이 얼마나 쫓아오나"를 한눈에.
// 막대 위 끝 높이는 Altimeter.top(5칸 단위로 늘어남) — 늘어날 때 눈금이 부드럽게 내려앉는다.

class AltimeterState {
    /** 화면에 보이는 위 끝(칸) — 목표로 부드럽게 따라감 */
    var top: Double = Altimeter.MIN_TOP.toDouble()
        private set
    /** 구구 표시 높이(점프하듯 부드럽게) */
    var guguHeight: Double = 0.0
        private set

    /** 새 판: 위 끝·구구를 바로 제자리에 */
    fun reset(best: Int) {
        top = Altimeter.top(climbed = 0, best = best).toDouble()
        guguHeight = 0.0
    }

    fun update(dt: Double, climbed: Int, best: Int) {
        top += (Altimeter.top(climbed, best) - top) * (1 - exp(-4 * dt))
        guguHeight += (climbed - guguHeight) * (1 - exp(-10 * dt))
    }

    /** 높이(칸) → 막대 아래 0 ~ 위 1 */
    fun fraction(height: Double): Double = Altimeter.fraction(height, top)
}

object AltimeterLayout {
    /** 안전 영역 왼쪽 끝에서 막대 중심까지(설계 px) — 왼쪽 발판 바깥 */
    const val CENTER_X = 40f
    /** 안전 영역 위 끝에서 막대 위 끝까지(문제 판·안내 글자 아래) */
    const val TOP_INSET = 600f
    /** 안전 영역 아래 끝에서 막대 아래 끝까지 */
    const val BOTTOM_INSET = 170f
    const val TRACK_WIDTH = 22f
    const val ZONE_ICON = 50f
    const val GUGU_ICON = 72f
    const val CLOUD_ICON = 60f
    const val FLAG_ICON = 58f

    /** 구간 아이콘(구간이 시작되는 높이에 놓임) */
    fun zoneArt(zone: SkyZone): Int = when (zone) {
        SkyZone.MEADOW -> R.drawable.game_grass_hill
        SkyZone.SKY_VILLAGE -> R.drawable.game_sky_cloud_puff
        SkyZone.RAINBOW -> R.drawable.game_sky_rainbow
        SkyZone.SPACE -> R.drawable.game_sky_moon
    }
}

/** cloudTop = 먹구름 윗면(칸), best = 지난 최고 기록(0 = 깃발 없음) */
fun DrawScope.drawAltimeter(
    state: AltimeterState,
    images: GameImages,
    cloudTop: Double,
    best: Int,
    accessory: Accessory?,
    viewport: GameViewport,
) {
    val u = viewport.unit
    val safe = viewport.safeRect
    val x = safe.left + AltimeterLayout.CENTER_X * u
    val top = safe.top + AltimeterLayout.TOP_INSET * u
    val bottom = safe.bottom - AltimeterLayout.BOTTOM_INSET * u
    val height = bottom - top
    fun y(h: Double): Float = bottom - state.fraction(h).toFloat() * height
    fun icon(res: Int, center: Offset, side: Float) = drawGameImageFit(images[res], center, Size(side, side))

    // 크림 알약을 세로로 세운 막대
    val mid = Offset(x, (top + bottom) / 2)
    rotate(90f, pivot = mid) {
        drawPill(images, mid, Size(height + AltimeterLayout.TRACK_WIDTH * u, AltimeterLayout.TRACK_WIDTH * u), alpha = 0.85f)
    }
    for (zone in SkyZone.entries) {
        icon(AltimeterLayout.zoneArt(zone), Offset(x, y(zone.startHeight.toDouble())), AltimeterLayout.ZONE_ICON * u)
    }
    if (best > 0) icon(R.drawable.game_flag, Offset(x, y(best.toDouble())), AltimeterLayout.FLAG_ICON * u)
    icon(R.drawable.game_icon_cloud, Offset(x, y(cloudTop)), AltimeterLayout.CLOUD_ICON * u)
    // 고도계 구구(맨 앞) — 옷장에서 고른 장신구도 씀(월드 구구와 같은 계산을 아이콘 크기로)
    drawGugu(images, GameArt.GuguPose.IDLE, accessory, Offset(x, y(state.guguHeight)), AltimeterLayout.GUGU_ICON * u)
}
