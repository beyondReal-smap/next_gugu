package site.smap.gugudan.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.services.Haptics

// 공용 UI 컴포넌트 (iOS Components.swift 이식)

enum class GGButtonVariant { PRIMARY, SURFACE, GHOST, DANGER }
enum class GGButtonSize(val height: Dp, val font: Int) {
    SM(36.dp, 14), MD(44.dp, 15), LG(56.dp, 16)
}

/** 눌림 스케일(0.97) + 탭 햅틱이 내장된 기본 버튼 */
@Composable
fun GGButton(
    variant: GGButtonVariant = GGButtonVariant.PRIMARY,
    size: GGButtonSize = GGButtonSize.MD,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val gg = LocalGG.current
    val bg = when (variant) {
        GGButtonVariant.PRIMARY -> gg.accent
        GGButtonVariant.SURFACE -> gg.surface2
        GGButtonVariant.GHOST -> Color.Transparent
        GGButtonVariant.DANGER -> gg.danger
    }
    val fg = when (variant) {
        GGButtonVariant.PRIMARY -> gg.accentFg
        GGButtonVariant.SURFACE -> gg.text
        GGButtonVariant.GHOST -> gg.textMuted
        GGButtonVariant.DANGER -> Color.White
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, spring(dampingRatio = 0.6f), label = "press")

    Row(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .height(size.height)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                Haptics.impactLight()
                onClick()
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides fg,
            LocalTextStyle provides suite(FontWeight.Bold, size.font).copy(color = fg),
        ) { content() }
    }
}

/** 눌림 스케일 탭 컨테이너 (카드형 탭 영역) */
@Composable
fun PressableCard(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, spring(dampingRatio = 0.6f), label = "press")
    Box(
        modifier
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                Haptics.impactLight()
                onClick()
            }
    ) { content() }
}

/** 카드형 표면 — border + surface + 라운드 (iOS ggCard) */
@Composable
fun Modifier.ggCard(radius: Dp = 24.dp): Modifier {
    val gg = LocalGG.current
    return this
        .clip(RoundedCornerShape(radius))
        .background(gg.surface)
        .border(1.dp, gg.border, RoundedCornerShape(radius))
}

/** 원형 진행 링 */
@Composable
fun ProgressRing(
    value: Double,
    size: Dp = 120.dp,
    stroke: Dp = 10.dp,
    track: Color? = null,
    bar: Color? = null,
    content: @Composable () -> Unit = {},
) {
    val gg = LocalGG.current
    val trackC = track ?: gg.surface2
    val barC = bar ?: gg.accent
    val progress by animateFloatAsState(value.coerceIn(0.0, 1.0).toFloat(), spring(stiffness = 90f), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2
            drawArc(trackC, 0f, 360f, false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - sw, this.size.height - sw),
                style = Stroke(sw))
            drawArc(barC, -90f, 360f * progress, false,
                topLeft = Offset(inset, inset),
                size = Size(this.size.width - sw, this.size.height - sw),
                style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

/** 마스터리 별점 */
@Composable
fun StarsView(count: Int, max: Int = 3, size: Dp = 14.dp) {
    val gg = LocalGG.current
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(max) { i ->
            Icon(
                if (i < count) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = null,
                tint = if (i < count) gg.warning else gg.border,
                modifier = Modifier.size(size),
            )
        }
    }
}

/** 알약 칩 */
@Composable
fun Pill(bg: Color, fg: Color, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(bg).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides fg,
            LocalTextStyle provides suite(FontWeight.ExtraBold, 12).copy(color = fg),
        ) { content() }
    }
}

/** 모드별 틴트 (modeIcons.ts 대응) */
object ModeStyle {
    @Composable
    fun tint(mode: GameMode): Color = when (mode) {
        GameMode.PRACTICE -> LocalGG.current.accent
        GameMode.TIME_ATTACK -> GGColors.sky
        GameMode.CHALLENGE -> GGColors.amber
        GameMode.SURVIVAL -> GGColors.rose
        GameMode.MISSING -> GGColors.violet
        GameMode.TRUEFALSE -> GGColors.emerald
        GameMode.ADVENTURE -> GGColors.indigo
    }
}
