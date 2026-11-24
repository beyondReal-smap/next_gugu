package site.smap.gugudan.features.games.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import site.smap.gugudan.R
import site.smap.gugudan.core.games.ProblemOrder
import site.smap.gugudan.designsystem.FitText
import site.smap.gugudan.designsystem.GGButton
import site.smap.gugudan.designsystem.GGButtonSize
import site.smap.gugudan.designsystem.GGButtonVariant
import site.smap.gugudan.designsystem.LocalGG
import site.smap.gugudan.designsystem.ggCard
import site.smap.gugudan.designsystem.phraseBreak
import site.smap.gugudan.designsystem.readableWidth
import site.smap.gugudan.designsystem.suite
import site.smap.gugudan.features.paywall.TrialNotice
import site.smap.gugudan.services.Haptics

// 게임 시작 전 패널 (iOS GameStartPanel.swift 대응) — 이름·한 줄 설명·문제 순서 [순서대로]·[무작위]·무료 체험 안내·[시작].
// 게임 화면 위에 어두운 막과 함께 얹는다(뒤로 첫 장면이 비쳐 보임). 체험 종료 패널(TrialEndPanel)도 같은 카드에 담는다.
// 젤리 창(쉬는 창·결과 창)과 달리 앱 표면 색 카드라 dp·sp(글꼴 크기 설정 반영)로 그린다.
// 여러 줄 문구는 낱말 단위 줄바꿈(phraseBreak) — 체험 안내·체험 종료 패널(MinigameTrial)도 같다.

@Composable
fun GameStartPanel(
    title: String,
    summary: String,
    icon: ImageVector,
    isPremium: Boolean,
    /** 지금 고른 문제 순서 — 누르면 onOrder 로 알리고, 엔진이 뒤의 첫 문제를 새 순서로 다시 만든다 */
    order: ProblemOrder,
    onOrder: (ProblemOrder) -> Unit,
    onStart: () -> Unit,
    onExit: () -> Unit,
) {
    val gg = LocalGG.current
    GamePanelCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(gg.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = gg.accent, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 낱말 단위 줄바꿈 — 기본 줄바꿈은 한국어를 글자마다 끊어 "몬스터/를" 처럼 낱말 중간에서 줄이 바뀐다
                Text(title, style = suite(FontWeight.ExtraBold, 20).phraseBreak(), color = gg.text)
                Text(summary, style = suite(FontWeight.Medium, 14).phraseBreak(), color = gg.textMuted)
            }
        }
        GameOrderPicker(order, onOrder)
        if (!isPremium) TrialNotice()
        GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG, onClick = onStart) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
            Text(stringResource(R.string.game_start))
        }
        TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            Text(stringResource(R.string.exit), style = suite(FontWeight.Bold, 14), color = gg.textMuted)
        }
    }
}

/** 문제 순서 두 버튼 — 고른 쪽은 채우고 다른 쪽은 테두리만. 아이 손가락으로 누르기 쉽게 높이 52 */
@Composable
private fun GameOrderPicker(order: ProblemOrder, onOrder: (ProblemOrder) -> Unit) {
    val label = stringResource(R.string.game_order)
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = label }.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GameOrderOption(
            ProblemOrder.SEQUENTIAL, order, onOrder, stringResource(R.string.game_order_sequential),
            Icons.Filled.FormatListNumbered, stringResource(R.string.game_order_sequential_hint),
        )
        GameOrderOption(
            ProblemOrder.RANDOM, order, onOrder, stringResource(R.string.game_order_random),
            Icons.Filled.Shuffle, stringResource(R.string.game_order_random_hint),
        )
    }
}

@Composable
private fun RowScope.GameOrderOption(
    value: ProblemOrder,
    order: ProblemOrder,
    onOrder: (ProblemOrder) -> Unit,
    title: String,
    icon: ImageVector,
    /** TalkBack 동작 이름(iOS accessibilityHint 대응) — 고르면 어떤 문제가 나오는지 */
    hint: String,
) {
    val gg = LocalGG.current
    val selected = value == order
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f), label = "press")
    val fg = if (selected) gg.accentFg else gg.accent
    Row(
        Modifier.weight(1f).heightIn(min = 52.dp).scale(scale).clip(shape)
            .background(if (selected) gg.accent else gg.accent.copy(alpha = 0.06f))
            .border(2.dp, gg.accent, shape)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = hint, role = Role.RadioButton) {
                if (selected) return@clickable   // 이미 고른 쪽을 다시 누르면 아무 일 없음(뒤 문제를 새로 만들지 않음)
                Haptics.selection()
                onOrder(value)
            }
            .semantics { this.selected = selected }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        // 번역이 긴 언어(vi·hi 등)에서도 한 줄 — 0.7배까지 줄인다(iOS minimumScaleFactor 0.7)
        FitText(title, suite(FontWeight.ExtraBold, 16), fg, minScale = 0.7f)
    }
}

/** 게임 화면 위에 앱 표면 색 카드를 가운데 띄운다(어두운 막이 뒤 게임 입력을 막음) */
@Composable
fun GamePanelCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).blockTouches(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp).readableWidth(420.dp).ggCard(24.dp).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}
