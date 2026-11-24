package site.smap.gugudan.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import site.smap.gugudan.R

// 재디자인 V1 "구구의 모험 지도" 아이 화면 색 토큰·젤리 판·FlowRow·그림 이름 (계획서 3-0, 5-3 — iOS WorldTheme.swift 대응)

data class WorldColors(
    /** 외곽선(3dp)과 블러 없는 아래 그림자(5dp) */
    val line: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    /** 홈·지도 하늘 바탕 세로 그라데이션 */
    val sky1: Color,
    val sky2: Color,
    val sky3: Color,
    /** 홈 언덕과 땅 */
    val grass: Color,
    val grass2: Color,
    /** 판, 시트, 탭 바 */
    val paper: Color,
    val paper2: Color,
    /** 보물 탭 바탕 (위 그라데이션 creamTop → 아래 cream) */
    val cream: Color,
    val creamTop: Color,
    /** 진행 고리·XP 막대·점선 트랙 */
    val track: Color,
    /** 누르면 시작하는 것에만 (게임 이름판, ▶, 오늘의 섬 숫자, 켜진 탭) */
    val sun: Color,
    /** 미션 진행 고리·완료 표시 */
    val ok: Color,
    /** 이번 주 도장 */
    val pink: Color,
    /** 착용 중 옷장 칸 */
    val mint: Color,
    /** 레벨·XP */
    val lav: Color,
    /** 오답·경고 배지 */
    val danger: Color,
    /** 구구 계단 문 바탕 그라데이션 */
    val stairs1: Color,
    val stairs2: Color,
    /** 구구 몬스터 문 바탕 그라데이션 */
    val monster1: Color,
    val monster2: Color,
    /** sun·lav·흰 숫자 원 등 밝은 고정 색 위 고정 남색 글자 */
    val ink: Color,
    val isDark: Boolean,
)

val DayWorldColors = WorldColors(
    line = Color(0xFF2B2D5E),
    text = Color(0xFF2B2D5E),
    text2 = Color(0xFF5B6092),
    text3 = Color(0xFF98A0C6),
    sky1 = Color(0xFF79CBFF),
    sky2 = Color(0xFFBDE7FF),
    sky3 = Color(0xFFEDF9FF),
    grass = Color(0xFF93DE72),
    grass2 = Color(0xFF62C760),
    paper = Color(0xFFFFFFFF),
    paper2 = Color(0xFFF3F7FD),
    cream = Color(0xFFFFF5DC),
    creamTop = Color(0xFFFFE9B8),
    track = Color(0xFFE1E8F4),
    sun = Color(0xFFFFD45C),
    ok = Color(0xFF1FAE6E),
    pink = Color(0xFFFF9DBB),
    mint = Color(0xFF7FE3B4),
    lav = Color(0xFFB3B1F7),
    danger = Color(0xFFD03B3B),
    stairs1 = Color(0xFFBFE6FF),
    stairs2 = Color(0xFFEAF7FF),
    monster1 = Color(0xFFBFF0D2),
    monster2 = Color(0xFFE9FBF0),
    ink = Color(0xFF2B2D5E),
    isDark = false,
)

/** 밤 테마 — line 은 바탕에 묻히지 않게 판(#283170)보다 어두운 #0F1236 (계획서 3-0) */
val NightWorldColors = WorldColors(
    line = Color(0xFF0F1236),
    text = Color(0xFFF4F6FF),
    text2 = Color(0xFFC3C9F2),
    text3 = Color(0xFF8C94CC),
    sky1 = Color(0xFF161A4A),
    sky2 = Color(0xFF2A3379),
    sky3 = Color(0xFF4352A6),
    grass = Color(0xFF3E9A6C),
    grass2 = Color(0xFF2B7756),
    paper = Color(0xFF283170),
    paper2 = Color(0xFF333E86),
    cream = Color(0xFF333E86),
    creamTop = Color(0xFF1E2360),
    track = Color(0xFF3D4892),
    sun = Color(0xFFFFD45C),
    ok = Color(0xFF1FAE6E),
    pink = Color(0xFFFF9DBB),
    mint = Color(0xFF7FE3B4),
    lav = Color(0xFFB3B1F7),
    danger = Color(0xFFD03B3B),
    stairs1 = Color(0xFF2F4FA0),
    stairs2 = Color(0xFF4366B8),
    monster1 = Color(0xFF256B58),
    monster2 = Color(0xFF318469),
    ink = Color(0xFF2B2D5E),
    isDark = true,
)

val LocalWorld = staticCompositionLocalOf { DayWorldColors }

/**
 * 젤리 판 모양 (계획서 3-0) — line 3dp 테두리 + 아래로 5dp 어긋난 블러 없는 line 그림자, 모서리 22~28dp.
 * drawBehind 를 clip 앞에 두어 아래쪽 오프셋 그림자가 잘리지 않는다.
 */
@Composable
fun Modifier.worldCard(
    radius: Dp = 24.dp,
    fill: Color? = null,
    brush: Brush? = null,
    borderWidth: Dp = 3.dp,
    shadowOffset: Dp = 5.dp,
): Modifier {
    val world = LocalWorld.current
    val bg = fill ?: world.paper
    val line = world.line
    val shape = RoundedCornerShape(radius)
    val surface = if (brush != null) {
        Modifier.background(brush, shape)
    } else {
        Modifier.background(bg, shape)
    }
    return this
        .drawBehind {
            val r = radius.toPx()
            val dy = shadowOffset.toPx()
            drawRoundRect(
                color = line,
                topLeft = Offset(0f, dy),
                size = size,
                cornerRadius = CornerRadius(r, r),
            )
        }
        .clip(shape)
        .then(surface)
        .border(borderWidth, line, shape)
}

/**
 * 넘침 방지용 공용 감싸기 줄 (계획서 3-0) — 글자·칩이 한 줄에 안 들어가면 줄을 줄이거나 자르지 않고 다음 줄로 감싼다.
 * Android 는 Compose 기본 FlowRow 를 쓴다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorldFlowRow(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

/** 재디자인 V1 그림 자원 (계획서 5-3 — res/drawable-nodpi/world_<이름>.webp) */
object WorldArt {
    val ALL_NAMES: List<String> = listOf(
        "tab_home", "tab_map", "tab_treasure",
        "icon_key", "icon_book", "icon_trophy",
        "house_gugu", "portal_stairs", "portal_forest",
        "isle_1", "isle_2", "isle_3", "isle_4", "isle_5", "isle_6",
    )

    @DrawableRes
    fun res(name: String): Int = when (name) {
        "tab_home" -> R.drawable.world_tab_home
        "tab_map" -> R.drawable.world_tab_map
        "tab_treasure" -> R.drawable.world_tab_treasure
        "icon_key" -> R.drawable.world_icon_key
        "icon_book" -> R.drawable.world_icon_book
        "icon_trophy" -> R.drawable.world_icon_trophy
        "house_gugu" -> R.drawable.world_house_gugu
        "portal_stairs" -> R.drawable.world_portal_stairs
        "portal_forest" -> R.drawable.world_portal_forest
        "isle_1" -> R.drawable.world_isle_1
        "isle_2" -> R.drawable.world_isle_2
        "isle_3" -> R.drawable.world_isle_3
        "isle_4" -> R.drawable.world_isle_4
        "isle_5" -> R.drawable.world_isle_5
        "isle_6" -> R.drawable.world_isle_6
        else -> throw IllegalArgumentException("알 수 없는 WorldArt 그림 이름: $name")
    }

    @Composable
    fun image(name: String): Painter = painterResource(res(name))

    /** 단 번호(2~19) → 6종 섬 그림 대응 (V1 시안 2→1, 3→3, 4→2, 5→3, 6→4, 7→5, 8→6, 9→1; 10~19 순환) */
    @DrawableRes
    fun isle(table: Int): Int {
        val index = when (table) {
            2 -> 1
            3 -> 3
            4 -> 2
            5 -> 3
            6 -> 4
            7 -> 5
            8 -> 6
            9 -> 1
            else -> ((table - 2).mod(6)) + 1
        }
        return res("isle_$index")
    }
}
