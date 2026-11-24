package site.smap.gugudan.features.games.shared

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import site.smap.gugudan.R
import site.smap.gugudan.core.games.Accessory
import kotlin.math.min

// A·B 게임 그림과 색 (Unity 시험판 GameArt.cs·Palette.cs 이식, iOS GameArt.swift 와 같은 수치)
// 그림은 res/drawable-nodpi/game_<원본 이름>.webp (Unity Resources/Art PNG 를 WebP 로 — 밀도 배율 없이 원본 px 그대로).
// 크기 계산에 쓰는 원본 px 크기도 여기 둔다(그림을 바꾸면 함께 고칠 것).

object GameArt {
    /** 캐릭터 그림(512px 정사각형) 아래쪽 투명 여백 비율 — 발을 발판 위에 붙일 때 뺀다 */
    const val FEET_PAD = 0.04f

    // MARK: 구구(주인공)

    /** headTop = 머리 윗면 가운데 — 그림 크기 대비, 그림 중심 기준(y 위가 +). 자세마다 실측(점프는 웅크려 낮음) */
    enum class GuguPose(@DrawableRes val res: Int, val headTop: Offset) {
        IDLE(R.drawable.game_gugu_idle, Offset(0.001f, 0.299f)),
        JUMP(R.drawable.game_gugu_jump, Offset(-0.007f, 0.230f)),
        HAPPY(R.drawable.game_gugu_happy, Offset(-0.02f, 0.309f)),
        OOPS(R.drawable.game_gugu_oops, Offset(0.006f, 0.307f)),
    }

    // MARK: 장신구

    @DrawableRes
    fun res(item: Accessory): Int = when (item) {
        Accessory.RIBBON -> R.drawable.game_acc_ribbon
        Accessory.FLOWER_CROWN -> R.drawable.game_acc_flower_crown
        Accessory.PARTY_HAT -> R.drawable.game_acc_party_hat
        Accessory.PIRATE_HAT -> R.drawable.game_acc_pirate_hat
        Accessory.WIZARD_HAT -> R.drawable.game_acc_wizard_hat
        Accessory.CROWN -> R.drawable.game_acc_crown
    }

    /** 장신구 원본 px 크기 — 머리에 얹을 때 가로세로 비율 */
    fun pixelSize(item: Accessory): Size = when (item) {
        Accessory.RIBBON -> Size(243f, 150f)
        Accessory.FLOWER_CROWN -> Size(252f, 138f)
        Accessory.PARTY_HAT -> Size(199f, 235f)
        Accessory.PIRATE_HAT -> Size(253f, 146f)
        Accessory.WIZARD_HAT -> Size(248f, 199f)
        Accessory.CROWN -> Size(244f, 170f)
    }

    /** 머리 맞춤: width = 구구 그림 폭 대비 장신구 폭(머리 폭 ≈ 0.48), sink = 장신구 높이 중 머리 윗면 아래로 덮는 비율 */
    private fun fit(item: Accessory): Pair<Float, Float> = when (item) {
        Accessory.RIBBON -> 0.34f to 0.30f
        Accessory.FLOWER_CROWN -> 0.50f to 0.45f
        Accessory.PARTY_HAT -> 0.30f to 0.12f
        Accessory.PIRATE_HAT -> 0.56f to 0.35f
        Accessory.WIZARD_HAT -> 0.56f to 0.22f
        Accessory.CROWN -> 0.44f to 0.25f
    }

    /** 장신구 자리 — 구구 그림 크기 대비, 그림 중심 기준(y 위가 +)의 중심과 크기 */
    data class Placement(val center: Offset, val size: Size)

    /**
     * 게임 속 구구·고도계 아이콘·옷장 미리 보기가 모두 이 계산을 써서 같은 자리에 얹힌다
     */
    fun accessoryPlacement(item: Accessory, pose: GuguPose): Placement {
        val px = pixelSize(item)
        val (width, sink) = fit(item)
        val height = width * px.height / px.width
        val top = pose.headTop
        return Placement(Offset(top.x, top.y + height * (0.5f - sink)), Size(width, height))
    }

    // MARK: 원본 px 크기 (크기·비율 계산용)

    val PLATFORM_PIXELS = Size(512f, 200f)
    val STORM_CLOUD_PIXELS = Size(1200f, 398f)
    val GRASS_HILL_PIXELS = Size(290f, 71f)
    val FLAG_PIXELS = Size(212f, 283f)
    val BOARD_PIXELS = Size(800f, 240f)
    val COMBO_FLAME_PIXELS = Size(230f, 266f)
    val SPARKLE_PIXELS = Size(161f, 171f)
    val STAR_PIXELS = Size(176f, 168f)
}

/**
 * 구구(+장신구)를 그림 중심·한 변 크기(px)로 그린다. 장신구 자리는 GameArt.accessoryPlacement —
 * 게임 속 구구·고도계 아이콘·옷장 미리 보기가 같은 모양
 */
fun DrawScope.drawGugu(images: GameImages, pose: GameArt.GuguPose, accessory: Accessory?, center: Offset, side: Float) {
    drawGameImage(images[pose.res], center, Size(side, side))
    if (accessory == null) return
    val place = GameArt.accessoryPlacement(accessory, pose)
    drawGameImage(
        images[GameArt.res(accessory)],
        Offset(center.x + place.center.x * side, center.y - place.center.y * side),
        Size(place.size.width * side, place.size.height * side),
    )
}

/** 게임 화면 색 (Unity Palette) */
object GamePalette {
    /** 결과·쉬는 창 버튼 글자 등 가장 어두운 남색 */
    val background = Color(0xFF141A33)
    val wall = Color(0xFF47538A)
    val correct = Color(0xFF3DDC84)
    val wrong = Color(0xFFFF5A5F)
    val text = Color(0xFFF4F6FF)
    val textDim = Color(0xFF9AA3C7)
    /** 젤리 창(쉬는 창·결과 창) 몸 색 */
    val card = Color(0xFF232C57)
    val gold = Color(0xFFFFD45C)
    /** 그림 외곽선 색 — 밝은 배지·카드 위 글자, 글자 테두리 */
    val ink = Color(0xFF2B2350)
    /** 쉬는 창 [메뉴로 나가기] 회색 젤리 */
    val quit = Color(0.72f, 0.74f, 0.80f)
}

/**
 * 게임 그림 묶음 — 처음 쓸 때 한 번 풀어(decode) 들고 있는다(프레임마다 풀지 않게).
 * 축소해 그릴 때 계단 현상이 없도록 밉맵을 켠다(iOS 처럼 큰 원본을 작게 그려도 매끈하게).
 */
class GameImages(private val resources: Resources) {
    private val cache = HashMap<Int, ImageBitmap>()

    operator fun get(@DrawableRes res: Int): ImageBitmap = cache.getOrPut(res) {
        ImageBitmap.imageResource(resources, res).also { it.asAndroidBitmap().setHasMipMap(true) }
    }

    /** 첫 프레임이 끊기지 않게 미리 풀어 둔다 */
    fun preload(ids: Iterable<Int>) {
        ids.forEach { get(it) }
    }
}

@Composable
fun rememberGameImages(): GameImages {
    val context = LocalContext.current
    return remember(context) { GameImages(context.resources) }
}

/** 무채색 그림에 색을 곱해 입힌다(Unity SpriteRenderer.color·iOS colorMultiply) — 투명한 곳은 그대로 투명 */
fun multiplyFilter(color: Color): ColorFilter = ColorFilter.tint(color, BlendMode.Modulate)

/**
 * 그림을 가운데·크기(px)로 그린다. 정수 좌표로 반올림하지 않도록 변환으로 그려 움직임이 매끈하다
 */
fun DrawScope.drawGameImage(
    image: ImageBitmap,
    center: Offset,
    size: Size,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
) {
    if (size.width <= 0f || size.height <= 0f || alpha <= 0f) return
    translate(center.x - size.width / 2, center.y - size.height / 2) {
        scale(size.width / image.width, size.height / image.height, pivot = Offset.Zero) {
            drawImage(image, alpha = alpha.coerceAtMost(1f), colorFilter = colorFilter)
        }
    }
}

/** 사각형 버전 */
fun DrawScope.drawGameImage(image: ImageBitmap, rect: Rect, alpha: Float = 1f, colorFilter: ColorFilter? = null) =
    drawGameImage(image, rect.center, rect.size, alpha, colorFilter)

/** 그림을 상자 안에 비율 유지로 맞춰(scaledToFit) 가운데에 그린다. pop = 가운데 기준 배율 */
fun DrawScope.drawGameImageFit(
    image: ImageBitmap,
    center: Offset,
    box: Size,
    pop: Float = 1f,
    alpha: Float = 1f,
) {
    val k = min(box.width / image.width, box.height / image.height) * pop
    drawGameImage(image, center, Size(image.width * k, image.height * k), alpha)
}
