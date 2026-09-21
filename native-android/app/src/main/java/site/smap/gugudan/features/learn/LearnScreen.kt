package site.smap.gugudan.features.learn

import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import site.smap.gugudan.core.Achievements
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.Modes
import site.smap.gugudan.core.PremiumConfig
import site.smap.gugudan.core.Problems
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.features.home.modeIcon
import site.smap.gugudan.store.LocalGame
import site.smap.gugudan.store.LocalRouter
import site.smap.gugudan.store.LocalPremium
import site.smap.gugudan.store.LocalSession

// 학습 탭 (iOS LearnView 이식) — 모드 선택 + 단 맵/마스터리

@Composable
fun LearnScreen() {
    val gg = LocalGG.current
    val game = LocalGame.current
    val router = LocalRouter.current
    val session = LocalSession.current
    val premium = LocalPremium.current

    var mode by remember { mutableStateOf(GameMode.PRACTICE) }
    val def = Modes.def(mode)
    val totalStars = Achievements.totalStars(game.state)
    val tables = (Problems.MIN_TABLE..Problems.MAX_TABLE).toList()

    Column(
        Modifier.fillMaxSize().background(gg.bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 헤더
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("학습", style = suite(FontWeight.ExtraBold, 24), color = gg.text, modifier = Modifier.weight(1f))
            Pill(bg = gg.warning.copy(alpha = 0.12f), fg = gg.warning) {
                Icon(Icons.Filled.EmojiEvents, null, Modifier.size(14.dp))
                Text("$totalStars/24")
            }
        }

        // 모드 카드 2열
        Modes.list.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m ->
                    val active = m.id == mode
                    val locked = PremiumConfig.isPremiumMode(m.id) && !premium.isPremium
                    val best = if (m.scored) game.state.bestScores[m.id] ?: 0 else 0
                    val tint = ModeStyle.tint(m.id)
                    PressableCard(modifier = Modifier.weight(1f), onClick = {
                        if (locked) premium.openPaywall() else mode = m.id
                    }) {
                        Box(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (active) gg.accent.copy(alpha = 0.1f) else gg.surface)
                                .border(1.dp, if (active) gg.accent else gg.border, RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center) {
                                    Icon(modeIcon(m.id), null, tint = tint, modifier = Modifier.size(20.dp))
                                }
                                Text(m.name, style = suite(FontWeight.ExtraBold, 14), color = gg.text)
                                Text(m.tagline, style = suite(FontWeight.Normal, 12), color = gg.textMuted, maxLines = 1)
                            }
                            if (locked) {
                                Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape)
                                    .background(gg.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Lock, null, tint = gg.accent, modifier = Modifier.size(11.dp))
                                }
                            } else if (m.scored && best > 0) {
                                Box(Modifier.align(Alignment.TopEnd).clip(CircleShape)
                                    .background(gg.warning.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text("최고 $best", style = suite(FontWeight.ExtraBold, 10), color = gg.warning)
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(def.detail, style = suite(FontWeight.Medium, 14), color = gg.textMuted)

        // 구구 점프 진입 (웹 Learn 의 러너 링크 대응)
        GameLink("구구 점프", "정답을 골라 장애물 넘기", Icons.Filled.DirectionsRun) { router.runnerOpen = true }
        GameLink("구구 레인", "길을 바꿔 피하고 정답 길로", Icons.Filled.ViewStream) { router.laneOpen = true }
        GameLink(
            "구구 바구니", "정답 열매를 바구니로 쏙", Icons.Filled.ShoppingBasket,
            iconBg = Color(0xFF794124), iconFg = Color(0xFFFFE7A3), tint = LocalGG.current.warning,
        ) { router.basketOpen = true }

        if (def.supportsTable) {
            // 전체 랜덤
            PressableCard(onClick = { session.start(mode, null) }) {
                Row(
                    Modifier.fillMaxWidth().ggCard(16.dp).padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(gg.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Shuffle, null, tint = gg.accent, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("전체 랜덤", style = suite(FontWeight.Bold, 15), color = gg.text)
                        Text("2~9단을 골고루 섞어서", style = suite(FontWeight.Normal, 13), color = gg.textMuted)
                    }
                }
            }

            Text("단 선택", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
            tables.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { t ->
                        val stars = game.state.tableMastery[t]?.stars ?: 0
                        PressableCard(modifier = Modifier.weight(1f), onClick = { session.start(mode, t) }) {
                            Column(
                                Modifier.fillMaxWidth().ggCard(16.dp).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text("$t", style = suite(FontWeight.ExtraBold, 30), color = gg.text)
                                    Text("단", style = suite(FontWeight.Bold, 18), color = gg.textMuted,
                                        modifier = Modifier.padding(bottom = 4.dp))
                                }
                                StarsView(stars, size = 16.dp)
                            }
                        }
                    }
                }
            }
        } else {
            // 전체 랜덤 전용 모드 — 기록 + 시작 CTA
            val best = game.state.bestScores[mode] ?: 0
            val tint = ModeStyle.tint(mode)
            Column(
                Modifier.fillMaxWidth().ggCard().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("내 최고 기록", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
                        Text(if (best > 0) "${best}점" else "—", style = suite(FontWeight.ExtraBold, 36), color = gg.text)
                    }
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) {
                        Icon(modeIcon(mode), null, tint = tint, modifier = Modifier.size(28.dp))
                    }
                }
                GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG,
                    onClick = { session.start(mode, null) }) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp))
                    Text("도전 시작")
                }
            }
        }
    }
}

@Composable
private fun GameLink(
    title: String,
    desc: String,
    icon: ImageVector,
    iconBg: Color = Color(0xFF153F35),
    iconFg: Color = Color(0xFFDBEF9E),
    tint: Color = GGColors.emerald,
    onClick: () -> Unit,
) {
    val gg = LocalGG.current
    PressableCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(tint.copy(alpha = 0.06f))
                .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconFg, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = suite(FontWeight.ExtraBold, 14), color = gg.text)
                Text(desc, style = suite(FontWeight.Medium, 12), color = gg.textMuted)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = gg.textMuted, modifier = Modifier.size(16.dp),
            )
        }
    }
}
