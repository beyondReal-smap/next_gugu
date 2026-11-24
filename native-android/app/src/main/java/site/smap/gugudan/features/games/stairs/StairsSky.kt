package site.smap.gugudan.features.games.stairs

import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import site.smap.gugudan.R
import site.smap.gugudan.core.games.SkyZone
import site.smap.gugudan.features.games.shared.GameArt
import site.smap.gugudan.features.games.shared.GameImages
import site.smap.gugudan.features.games.shared.drawGameImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// 구구 계단 하늘 배경 (Unity 시험판 SkyView.cs 이식, iOS StairsSky.swift 대응): 오를수록 바뀌는 그라데이션(풀밭 → 하늘 마을 → 무지개 → 달나라) +
// 천천히 흐르는 장식(구름·섬·풍선·새·무지개·달·행성·별) + 출발 풀 언덕.
// "높이 올라간다"를 단색 배경 대신 눈으로 보여 주는 것이 목적이다. 발판·캐릭터보다 항상 뒤에 그린다.
// 장식 배치는 칸 번호로 만든 고정 의사 난수라 다시 와도 같은 그림이다(Unity 해시를 비트 단위로 옮김 — 같은 자리에 같은 장식).

object StairsSky {
    /** 장식은 앞쪽의 30% 속도로 흐름(멀리 있는 느낌) */
    const val PARALLAX = 0.3
    /** 장식 칸 간격(장식 층 좌표, 유닛) */
    const val SLOT_SPACING = 2.4
    /** 빈 칸 비율 — 너무 빽빽하면 발판 숫자를 가림 */
    const val SKIP_CHANCE = 0.25
    /** 다음 구간 색으로 미리 물드는 높이(칸) */
    const val BLEND_ROWS = 4.0
    /** 살짝 비쳐 보여 발판·숫자보다 뒤에 있는 느낌 */
    const val DECOR_ALPHA = 0.8
    const val HILL_TILE_WIDTH = 3.6
    /** 출발 발판 아래 끝에서 언덕 꼭대기까지(유닛) — 젤리 방울이 언덕에 닿지 않게 */
    const val HILL_GAP = 0.3
    const val HILL_TILES = 5
    const val FILL_DEPTH = 60.0
    /** 언덕 그림 아래 가장자리 색(실측) — 아래 사각형과 이음매가 안 보이게 */
    val HILL_FILL = Color(0xFFDAE67C)

    /** 구간별 하늘 위·아래 색 (SkyZone 순서) */
    val SKY_TOP = listOf(GameRGB.hex(0x74C6FF), GameRGB.hex(0x8DB4FF), GameRGB.hex(0xB4A6FF), GameRGB.hex(0x10153A))
    val SKY_BOTTOM = listOf(GameRGB.hex(0xDDF5FF), GameRGB.hex(0xFFDCEB), GameRGB.hex(0xFFF1C4), GameRGB.hex(0x3A3478))

    /** res = 그림, width = 월드 폭(유닛), pixels = 원본 px 크기(가로세로 비율) */
    enum class Decor(@DrawableRes val res: Int, val width: Double, val pixels: Size) {
        CLOUD(R.drawable.game_sky_cloud_puff, 2.4, Size(292f, 162f)),
        ISLAND(R.drawable.game_sky_island, 2.3, Size(246f, 235f)),
        BALLOON(R.drawable.game_sky_balloon, 1.1, Size(169f, 227f)),
        BIRD(R.drawable.game_sky_bird, 0.9, Size(196f, 146f)),
        RAINBOW(R.drawable.game_sky_rainbow, 3.0, Size(292f, 191f)),
        MOON(R.drawable.game_sky_moon, 1.5, Size(178f, 179f)),
        PLANET(R.drawable.game_sky_planet, 1.7, Size(234f, 168f)),
    }

    /** 미리 풀어 둘 그림 */
    val IMAGES: List<Int> = Decor.entries.map { it.res } + listOf(R.drawable.game_star, R.drawable.game_grass_hill)

    /** 구간별 장식 후보(같은 칸에는 하나). 달나라는 큰 장식 + 작은 별 여러 개 */
    fun options(zone: SkyZone): List<Decor> = when (zone) {
        SkyZone.MEADOW -> listOf(Decor.CLOUD, Decor.BIRD, Decor.CLOUD)
        SkyZone.SKY_VILLAGE -> listOf(Decor.ISLAND, Decor.BALLOON, Decor.CLOUD, Decor.BIRD)
        SkyZone.RAINBOW -> listOf(Decor.RAINBOW, Decor.CLOUD, Decor.BALLOON)
        SkyZone.SPACE -> listOf(Decor.MOON, Decor.PLANET)
    }

    /** 카메라가 비추는 높이(칸) */
    fun height(cameraY: Double): Double = (cameraY - StairsStage.CAMERA_LEAD) / StairsStage.ROW_HEIGHT

    /** 높이(칸) → 하늘 위·아래 색. 다음 구간 시작 BLEND_ROWS 칸 전부터 서서히 물듦 */
    fun colors(height: Double): Pair<GameRGB, GameRGB> {
        val zone = SkyZone.at(floor(height).toInt())
        val top = SKY_TOP[zone.ordinal]
        val bottom = SKY_BOTTOM[zone.ordinal]
        val next = SkyZone.entries.getOrNull(zone.ordinal + 1) ?: return top to bottom
        val end = next.startHeight.toDouble()
        val t = min(1.0, max(0.0, (height - (end - BLEND_ROWS)) / BLEND_ROWS))
        return top.lerp(SKY_TOP[next.ordinal], t) to bottom.lerp(SKY_BOTTOM[next.ordinal], t)
    }

    // MARK: 장식 칸

    class Item(
        @DrawableRes val res: Int,
        val pixels: Size,
        /** 장식 층 좌표(유닛) */
        val x: Double,
        val y: Double,
        val width: Double,
        val flip: Boolean,
        val alpha: Double,
        /** 달나라 작은 별 — 별마다 고정 위상으로 반짝임 */
        val twinklePhase: Double?,
    )

    /** 장식 칸 k: 화면 가운데에 보일 때의 높이로 구간을 정하고, k 로 만든 수로 종류·위치를 고정 */
    fun slot(k: Int, halfWidth: Double): List<Item> {
        val localY = k * SLOT_SPACING
        val h = hash(k)
        if (k < 0 || unit(h, 0) < SKIP_CHANCE) return emptyList()   // 출발 아래(언덕 근처)는 비움
        val heightSeen = (localY / PARALLAX - StairsStage.CAMERA_LEAD) / StairsStage.ROW_HEIGHT
        val zone = SkyZone.at(floor(heightSeen).toInt())
        val choices = options(zone)
        val decor = choices[(unit(h, 1) * choices.size).toInt() % choices.size]
        val x = lerp(-halfWidth + 1, halfWidth - 1, unit(h, 2))
        val width = decor.width * lerp(0.85, 1.15, unit(h, 3))
        val flip = decor == Decor.BIRD && unit(h, 4) < 0.5
        val items = mutableListOf(Item(decor.res, decor.pixels, x, localY, width, flip, DECOR_ALPHA, null))
        if (zone == SkyZone.SPACE) {
            for (i in 0 until 3) {
                val sx = lerp(-halfWidth + 0.4, halfWidth - 0.4, unit(h, 5 + i))
                val sy = localY + lerp(-SLOT_SPACING * 0.5, SLOT_SPACING * 0.5, unit(h, 8 + i))
                items.add(
                    Item(
                        R.drawable.game_star, GameArt.STAR_PIXELS, sx, sy, lerp(0.18, 0.32, unit(h, 11 + i)),
                        flip = false, alpha = 0.9, twinklePhase = unit(h, 14 + i) * PI * 2,
                    ),
                )
            }
        }
        return items
    }

    // MARK: 고정 의사 난수 (Unity SkyView.Hash·Unit 와 비트 단위로 같음, 그림 배치용 — 규칙과 무관)

    fun hash(k: Int): UInt {
        var x = k.toUInt() * 2_654_435_761u + 0x9E37_79B9u
        x = x xor (x shr 16)
        x *= 0x7FEB_352Du
        x = x xor (x shr 15)
        x *= 0x846C_A68Bu
        x = x xor (x shr 16)
        return x
    }

    fun unit(h: UInt, salt: Int): Double {
        var x = h xor (salt.toUInt() * 0x9E37_79B9u)
        x = x xor (x shr 13)
        x *= 0x5BD1_E995u
        x = x xor (x shr 15)
        return (x and 0xFF_FFFFu).toDouble() / 0x100_0000.toDouble()
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}

// MARK: 그리기 (StairsSky 값으로)

/** 하늘·장식·언덕을 그린다(발판보다 먼저). time = 반짝임용 흐른 시간(초) */
fun DrawScope.drawStairsSky(camera: StairsCamera, images: GameImages, time: Double) {
    val (top, bottom) = StairsSky.colors(StairsSky.height(camera.centerY))
    drawRect(Brush.verticalGradient(listOf(top.color, bottom.color), startY = 0f, endY = size.height))

    // 장식 층: 카메라의 (1 - PARALLAX) 만큼 따라 움직여, 화면에서는 PARALLAX 속도로 흐름
    val layerY = camera.centerY * (1 - StairsSky.PARALLAX)
    val center = camera.centerY * StairsSky.PARALLAX
    val lo = floor((center - camera.halfHeight - 2) / StairsSky.SLOT_SPACING).toInt()
    val hi = ceil((center + camera.halfHeight + 2) / StairsSky.SLOT_SPACING).toInt()
    for (k in lo..hi) {
        for (item in StairsSky.slot(k, camera.halfWidth)) {
            val w = item.width.toFloat() * camera.unitPx
            val hgt = w * item.pixels.height / item.pixels.width
            val p = camera.point(item.x, layerY + item.y)
            // 우주의 작은 별은 반짝반짝
            val alpha = item.twinklePhase?.let { 0.55 + 0.45 * abs(sin(time * 2.2 + it)) } ?: item.alpha
            val image = images[item.res]
            if (item.flip) {
                scale(-1f, 1f, pivot = p) { drawGameImage(image, p, Size(w, hgt), alpha.toFloat()) }
            } else {
                drawGameImage(image, p, Size(w, hgt), alpha.toFloat())
            }
        }
    }

    drawStairsHill(camera, images)
}

/** 출발 발판 아래 풀 언덕: 화면 폭을 덮도록 여러 장 이어 붙이고, 그 아래는 같은 색 사각형(먹구름이 덮으며 올라옴) */
private fun DrawScope.drawStairsHill(camera: StairsCamera, images: GameImages) {
    val px = GameArt.GRASS_HILL_PIXELS
    val tileH = StairsSky.HILL_TILE_WIDTH * px.height / px.width
    val topY = -StairsStage.PLATFORM_HEIGHT * 0.5 - StairsSky.HILL_GAP
    val bottomY = topY - tileH
    val image = images[R.drawable.game_grass_hill]
    val w = StairsSky.HILL_TILE_WIDTH.toFloat() * camera.unitPx
    val h = tileH.toFloat() * camera.unitPx
    for (i in 0 until StairsSky.HILL_TILES) {
        val x = (i - (StairsSky.HILL_TILES - 1) * 0.5) * (StairsSky.HILL_TILE_WIDTH * 0.92)   // 살짝 겹쳐 틈 방지
        drawGameImage(image, camera.point(x, bottomY + tileH * 0.5), Size(w, h))
    }
    val fillTop = camera.point(0.0, bottomY + 0.02)
    val fillWidth = (StairsSky.HILL_TILE_WIDTH * StairsSky.HILL_TILES).toFloat() * camera.unitPx
    drawRect(
        StairsSky.HILL_FILL, Offset(fillTop.x - fillWidth / 2, fillTop.y),
        Size(fillWidth, StairsSky.FILL_DEPTH.toFloat() * camera.unitPx),
    )
}
