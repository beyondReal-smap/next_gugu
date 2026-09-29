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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import site.smap.gugudan.core.Hints
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
import site.smap.gugudan.features.paywall.MinigameTrial
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
    /** 표 보기 시트로 연 단 */
    var sheetTable by remember { mutableStateOf<Int?>(null) }
    val def = Modes.def(mode)
    val totalStars = Achievements.totalStars(game.state)
    val tables = (Problems.MIN_TABLE..Problems.MAX_TABLE).toList()
    // 로드맵상 다음에 익힐 단 — 모두 별 3개면 null
    val recommended = Hints.nextRoadmapTable(game.state.tableMastery)

    Column(
        Modifier.fillMaxSize().background(gg.bg)
            // 상태바 영역은 스크롤 밖에 둔다 — 스크롤한 내용이 시계·배터리 밑으로 비치지 않게
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .readableWidth()
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

        // 모드를 고르면 바로 단을 고르게 — 미니게임 링크가 이 사이에 끼어 흐름이 끊기던 것을 하단으로 옮겼다
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

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("단 선택", style = suite(FontWeight.Bold, 14), color = gg.textMuted,
                    modifier = Modifier.semantics { heading() })
                Text(
                    "추천 순서 ${Hints.ROADMAP_TABLES.joinToString(" → ")}단 · 책 버튼을 누르면 표를 볼 수 있어요",
                    style = suite(FontWeight.Medium, 11), color = gg.textMuted.copy(alpha = 0.85f),
                )
            }
            tables.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { t ->
                        TableCard(
                            t, mode, recommended = t == recommended, modifier = Modifier.weight(1f),
                            onStart = { session.start(mode, t) },
                            onOpenTable = { sheetTable = t },
                        )
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

        // 달리기·받기 미니게임 (웹 Learn 의 게임 링크 대응)
        Spacer(Modifier.height(4.dp))
        Text("움직이며 연습하기", style = suite(FontWeight.Bold, 14), color = gg.textMuted,
            modifier = Modifier.semantics { heading() })
        GameLink("구구 점프", "정답을 골라 장애물 넘기", Icons.Filled.DirectionsRun) { router.runnerOpen = true }
        GameLink("구구 레인", "길을 바꿔 피하고 정답 길로", Icons.Filled.ViewStream) { router.laneOpen = true }
        GameLink(
            "구구 바구니", "정답 열매를 바구니로 쏙", Icons.Filled.ShoppingBasket,
            iconBg = Color(0xFF794124), iconFg = Color(0xFFFFE7A3), tint = LocalGG.current.warning,
        ) { router.basketOpen = true }
    }

    sheetTable?.let { t ->
        TableSheet(
            table = t,
            onDismiss = { sheetTable = null },
            // 시트를 먼저 닫아야 세션 화면이 시트 창 밑에 깔리지 않는다
            onPractice = { sheetTable = null; session.start(GameMode.PRACTICE, t) },
        )
    }
}

@Composable
private fun TableCard(
    table: Int,
    mode: GameMode,
    recommended: Boolean,
    modifier: Modifier,
    onStart: () -> Unit,
    onOpenTable: () -> Unit,
) {
    val gg = LocalGG.current
    val game = LocalGame.current
    val stars = game.state.tableMastery[table]?.stars ?: 0
    Box(modifier) {
        PressableCard(
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "${table}단 ${Modes.def(mode).name} 시작, 별 ${stars}개" + if (recommended) ", 추천" else ""
                onClick { onStart(); true }
            },
            onClick = onStart,
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (recommended) gg.accent.copy(alpha = 0.08f) else gg.surface)
                    .border(if (recommended) 1.5.dp else 1.dp,
                        if (recommended) gg.accent.copy(alpha = 0.6f) else gg.border, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$table", style = suite(FontWeight.ExtraBold, 30), color = gg.text)
                    Text("단", style = suite(FontWeight.Bold, 18), color = gg.textMuted,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    StarsView(stars, size = 16.dp)
                    if (recommended) {
                        Text("추천", style = suite(FontWeight.ExtraBold, 10), color = gg.accentFg,
                            modifier = Modifier.clip(CircleShape).background(gg.accent)
                                .padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                }
            }
        }
        // 표 보기
        PressableCard(
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            onClick = onOpenTable,
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(gg.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, "${table}단 표 보기", tint = gg.accent, modifier = Modifier.size(18.dp))
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
    // 무료 사용자에게 체험이라는 것을 미리 알린다
    val trialBadge = MinigameTrial.badge(LocalPremium.current.isPremium)
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = suite(FontWeight.ExtraBold, 14), color = gg.text)
                    trialBadge?.let {
                        Text(
                            it, style = suite(FontWeight.ExtraBold, 10), color = tint,
                            modifier = Modifier.clip(CircleShape).background(tint.copy(alpha = 0.12f))
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(desc, style = suite(FontWeight.Medium, 12), color = gg.textMuted)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = gg.textMuted, modifier = Modifier.size(16.dp),
            )
        }
    }
}
