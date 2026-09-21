package site.smap.gugudan.features.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.Level
import site.smap.gugudan.core.ModeKind
import site.smap.gugudan.core.Modes
import site.smap.gugudan.core.adventure.AdvProgress
import site.smap.gugudan.core.adventure.World
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.store.*

// 홈 (iOS HomeView 이식)

// 놀이 방식별 묶음 (웹 /play 구성과 동일) — 세션 모드는 전체 랜덤으로 바로 시작한다
private val LEARN_MODES = listOf(GameMode.MISSING, GameMode.TRUEFALSE)
private val RECORD_MODES = listOf(GameMode.TIME_ATTACK, GameMode.CHALLENGE, GameMode.SURVIVAL)

fun modeIcon(mode: GameMode): ImageVector = when (mode) {
    GameMode.PRACTICE -> Icons.AutoMirrored.Filled.MenuBook
    GameMode.TIME_ATTACK -> Icons.Filled.Timer
    GameMode.CHALLENGE -> Icons.Filled.Bolt
    GameMode.SURVIVAL -> Icons.Filled.Favorite
    GameMode.MISSING -> Icons.Filled.Extension
    GameMode.TRUEFALSE -> Icons.Filled.Scale
    GameMode.ADVENTURE -> Icons.Filled.Shield
}

@Composable
fun HomeScreen() {
    val gg = LocalGG.current
    val game = LocalGame.current
    val session = LocalSession.current
    val adventure = LocalAdventure.current
    val premium = LocalPremium.current
    val router = LocalRouter.current

    val state = game.state
    val level = game.levelInfo
    val goalPct = if (state.dailyGoal > 0) state.dailyCorrect.toDouble() / state.dailyGoal else 0.0
    val weakCount = state.wrongPool.size

    Column(
        Modifier.fillMaxSize().background(gg.bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 헤더
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("안녕하세요 👋", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
                Text("오늘도 구구단 한 판!", style = suite(FontWeight.ExtraBold, 20), color = gg.text)
            }
            Pill(bg = gg.danger.copy(alpha = 0.12f), fg = gg.danger) {
                Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(14.dp))
                Text("${state.streak}일")
            }
        }

        // 데일리 골
        Row(
            Modifier.fillMaxWidth().ggCard().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(goalPct, size = 104.dp, stroke = 11.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.dailyCorrect}", style = suite(FontWeight.ExtraBold, 24), color = gg.text)
                    Text("/ ${state.dailyGoal}", style = suite(FontWeight.Bold, 11), color = gg.textMuted)
                }
            }
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.TrackChanges, null, tint = gg.accent, modifier = Modifier.size(16.dp))
                    Text("오늘의 목표", style = suite(FontWeight.Bold, 14), color = gg.text)
                }
                Text(
                    if (goalPct >= 1) "목표 달성! 멋져요 🎉"
                    else "정답 ${maxOf(0, state.dailyGoal - state.dailyCorrect)}개 더 풀면 달성!",
                    style = suite(FontWeight.Medium, 14), color = gg.textMuted,
                )
            }
        }

        // 레벨/XP
        Column(Modifier.fillMaxWidth().ggCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(gg.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${level.level}", style = suite(FontWeight.ExtraBold, 14), color = gg.accentFg)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Lv.${level.level}", style = suite(FontWeight.ExtraBold, 14), color = gg.text)
                    Text(Level.title(level.level), style = suite(FontWeight.Bold, 12), color = gg.textMuted)
                }
                Text("${level.currentLevelXp}/${level.xpForNextLevel} XP", style = suite(FontWeight.Bold, 12), color = gg.textMuted)
            }
            GaugeBar(level.progress)
        }

        // 빠른 학습 시작
        GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG,
            onClick = { session.start(GameMode.PRACTICE, null) }) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(22.dp))
            Text("빠른 학습 시작")
        }

        // 취약 문제 복습
        if (weakCount > 0) {
            PressableCard(onClick = { session.start(GameMode.PRACTICE, null) }) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(gg.danger.copy(alpha = 0.1f))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(gg.danger.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = gg.danger, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("취약 문제 복습", style = suite(FontWeight.Bold, 15), color = gg.text)
                        Text("헷갈렸던 문제 ${weakCount}개가 우선 출제돼요", style = suite(FontWeight.Normal, 13), color = gg.textMuted)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = gg.textMuted)
                }
            }
        }

        // 어떻게 놀아볼까요? — 놀이 방식별 묶음
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("어떻게 놀아볼까요?", style = suite(FontWeight.ExtraBold, 17), color = gg.text)
            Text(
                "배우기부터 달리기까지, 모두 구구단 연습이 돼요.",
                style = suite(FontWeight.Medium, 12), color = gg.textMuted,
            )
        }

        // 차근차근 배우기
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader(Icons.AutoMirrored.Filled.MenuBook, "차근차근 배우기", "시간 제한 없이 원리부터", gg.accent)
            ModeRow(
                icon = modeIcon(GameMode.PRACTICE), tint = ModeStyle.tint(GameMode.PRACTICE),
                name = "학습", tagline = "원하는 단을 골라 또박또박",
                meta = "${Modes.def(GameMode.PRACTICE).total}문제 · 단 선택", locked = false,
                onClick = { router.tab = AppTab.LEARN },
            )
            LEARN_MODES.forEach { mode ->
                SessionModeRow(mode) { if (premium.gate(mode)) session.start(mode, null) }
            }
        }

        // 기록 도전
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader(Icons.Filled.EmojiEvents, "기록 도전", "속도와 집중력으로 최고 기록", gg.warning)
            RECORD_MODES.forEach { mode ->
                SessionModeRow(mode) { if (premium.gate(mode)) session.start(mode, null) }
            }
        }

        // 직접 움직이며 놀기
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader(Icons.Filled.DirectionsRun, "직접 움직이며 놀기", "달리고, 피하고, 정답을 받아요", GGColors.emerald)
            GameCard(
                title = "구구 바구니", badge = "새 모드", desc = "좌우로 움직여 정답 열매를 쏙 받아요!",
                icon = Icons.Filled.ShoppingBasket, iconBg = Color(0xFF794124), iconFg = Color(0xFFFFE7A3),
                tint = gg.warning, onClick = { router.basketOpen = true },
            )
            GameCard(
                title = "구구 점프", badge = null, desc = "정답을 맞히면 폴짝! 장애물을 넘어 달려요.",
                icon = Icons.Filled.DirectionsRun, iconBg = Color(0xFF153F35), iconFg = Color(0xFFDBEF9E),
                tint = GGColors.emerald, onClick = { router.runnerOpen = true },
            )
            GameCard(
                title = "구구 레인", badge = "새 모드", desc = "길을 바꿔 피하고, 정답 길로 쏙!",
                icon = Icons.Filled.ViewStream, iconBg = Color(0xFF153F35), iconFg = Color(0xFFDBEF9E),
                tint = GGColors.emerald, onClick = { router.laneOpen = true },
            )
        }

        // 모험
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader(Icons.Filled.Map, "모험", "3D 월드를 탐험하며 대결", GGColors.indigo)
            AdventureHero(
                onClick = { if (premium.isPremium) adventure.openAdventure() else premium.openPaywall() },
            )
        }

        // 단 선택 학습 링크
        PressableCard(onClick = { router.tab = AppTab.LEARN }) {
            Row(
                Modifier.fillMaxWidth().ggCard(16.dp).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("단 선택해서 학습하기", style = suite(FontWeight.Bold, 15), color = gg.text, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = gg.textMuted)
            }
        }
    }
}

@Composable
private fun GroupHeader(icon: ImageVector, title: String, desc: String, tint: Color) {
    val gg = LocalGG.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Text(title, style = suite(FontWeight.Bold, 13), color = gg.textMuted)
        Text(desc, style = suite(FontWeight.Medium, 11), color = gg.textMuted.copy(alpha = 0.75f), maxLines = 1)
    }
}

/** 러너·레인·바구니 진입 카드 */
@Composable
private fun GameCard(
    title: String,
    badge: String?,
    desc: String,
    icon: ImageVector,
    iconBg: Color,
    iconFg: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    val gg = LocalGG.current
    PressableCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(tint.copy(alpha = 0.06f))
                .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconFg, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = suite(FontWeight.ExtraBold, 16), color = gg.text)
                    if (badge != null) {
                        Text(
                            badge, style = suite(FontWeight.ExtraBold, 10), color = tint,
                            modifier = Modifier.clip(CircleShape).background(tint.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(desc, style = suite(FontWeight.Medium, 13), color = gg.textMuted)
            }
            Icon(Icons.Filled.ArrowOutward, null, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

/** 세션 모드 한 줄 카드 (웹 모바일의 목록형과 동일) */
@Composable
private fun SessionModeRow(mode: GameMode, onClick: () -> Unit) {
    val game = LocalGame.current
    val premium = LocalPremium.current
    val def = Modes.def(mode)
    val best = if (def.scored) game.state.bestScores[mode] ?: 0 else 0
    val meta = if (best > 0) "최고 ${best}점 · ${modeMeta(mode)}" else "${modeMeta(mode)} · 전체 랜덤"

    ModeRow(
        icon = modeIcon(mode), tint = ModeStyle.tint(mode), name = def.name, tagline = def.tagline,
        meta = meta, locked = !premium.isPremium && premium.isPremiumMode(mode), onClick = onClick,
    )
}

@Composable
private fun ModeRow(
    icon: ImageVector,
    tint: Color,
    name: String,
    tagline: String,
    meta: String,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val gg = LocalGG.current
    PressableCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().ggCard(16.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, style = suite(FontWeight.ExtraBold, 16), color = gg.text)
                Text(tagline, style = suite(FontWeight.Medium, 13), color = gg.textMuted, maxLines = 1)
                Text(meta, style = suite(FontWeight.Bold, 12), color = gg.textMuted.copy(alpha = 0.8f))
            }
            if (locked) {
                Icon(Icons.Filled.Lock, null, tint = gg.accent, modifier = Modifier.size(14.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = gg.textMuted, modifier = Modifier.size(16.dp))
        }
    }
}

/** 카드에 적는 규칙 요약 (문제 수 / 제한 시간 / 하트) */
private fun modeMeta(mode: GameMode): String {
    val def = Modes.def(mode)
    def.timeLimitMs?.let { if (def.kind == ModeKind.TIMED) return "${it / 1000}초 제한" }
    def.lives?.let { if (def.kind == ModeKind.LIVES) return "하트 ${it}개" }
    return "${def.total}문제"
}

@Composable
fun GaugeBar(progress: Double, height: androidx.compose.ui.unit.Dp = 10.dp, color: Color? = null) {
    val gg = LocalGG.current
    val p by animateFloatAsState(progress.coerceIn(0.0, 1.0).toFloat(), spring(stiffness = 120f), label = "gauge")
    Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(gg.surface2)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(p).clip(CircleShape).background(color ?: gg.accent))
    }
}

@Composable
private fun ModeCard(mode: GameMode, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val gg = LocalGG.current
    val game = LocalGame.current
    val premium = LocalPremium.current
    val def = Modes.def(mode)
    val tint = ModeStyle.tint(mode)
    val best = if (def.scored) game.state.bestScores[mode] ?: 0 else 0

    PressableCard(modifier = modifier, onClick = onClick) {
        Box(Modifier.fillMaxWidth().ggCard(16.dp).padding(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center) {
                    Icon(modeIcon(mode), null, tint = tint, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(def.name, style = suite(FontWeight.ExtraBold, 14), color = gg.text)
                    Text(def.tagline, style = suite(FontWeight.Normal, 12), color = gg.textMuted, maxLines = 1)
                }
            }
            if (!premium.isPremium) {
                Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape)
                    .background(gg.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, null, tint = gg.accent, modifier = Modifier.size(11.dp))
                }
            } else if (def.scored && best > 0) {
                Box(Modifier.align(Alignment.TopEnd).clip(CircleShape)
                    .background(gg.warning.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("최고 $best", style = suite(FontWeight.ExtraBold, 10), color = gg.warning)
                }
            }
        }
    }
}

@Composable
private fun AdventureHero(onClick: () -> Unit) {
    val adventure = LocalAdventure.current
    val premium = LocalPremium.current
    val (defeated, total) = AdvProgress.totalStats(adventure.progress)
    val nextRegion = World.regions.firstOrNull {
        World.bossId(it.table) !in adventure.progress.defeatedNpcs
    }

    PressableCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(GGColors.indigo, GGColors.violet, GGColors.purple)))
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Map, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Text("어드벤처", style = suite(FontWeight.ExtraBold, 11), color = Color.White)
                    if (!premium.isPremium) Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
                Text("3D 월드를 탐험하며\n구구단 대결!",
                    style = suite(FontWeight.ExtraBold, 20).copy(lineHeight = 26.sp), color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(12.dp))
                    Text(
                        "격파 $defeated/$total" + (nextRegion?.let { " · 다음 모험 ${it.name}" } ?: ""),
                        style = suite(FontWeight.Bold, 12), color = Color.White.copy(alpha = 0.9f), maxLines = 1,
                    )
                }
                // 지역 진행 도트
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                    World.regions.forEach { r ->
                        val cleared = World.bossId(r.table) in adventure.progress.defeatedNpcs
                        Box(
                            Modifier.height(6.dp).width(if (cleared) 16.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (cleared) Color.White else Color.White.copy(alpha = 0.35f))
                        )
                    }
                }
            }
            // 마스코트 — 캡슐+눈
            Mascot()
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun Mascot() {
    Box(Modifier.size(width = 52.dp, height = 74.dp).clip(RoundedCornerShape(50)).background(Color.White),
        contentAlignment = Alignment.TopCenter) {
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(2) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF20242C)),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
    }
}
