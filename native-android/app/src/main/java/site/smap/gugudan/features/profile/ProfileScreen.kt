package site.smap.gugudan.features.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import site.smap.gugudan.core.AchievementDef
import site.smap.gugudan.core.AchievementProgress
import site.smap.gugudan.core.Achievements
import site.smap.gugudan.core.Commit
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.Level
import site.smap.gugudan.core.Modes
import site.smap.gugudan.core.WeeklyReport
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.features.home.GaugeBar
import site.smap.gugudan.features.home.modeIcon
import site.smap.gugudan.store.AuthStore
import site.smap.gugudan.store.LocalAuth
import site.smap.gugudan.store.LocalGame
import site.smap.gugudan.store.LocalPremium
import site.smap.gugudan.store.LocalRouter
import site.smap.gugudan.store.LocalSession

// 프로필 탭 (iOS ProfileView 이식) — 레벨/주간 리포트/통계/업적.
// 설정·계정·이용권은 톱니바퀴 → SettingsScreen 으로 옮겼다 (한 화면에 기능이 너무 많이 섞여 있었다).

private val SCORED_MODES = listOf(GameMode.CHALLENGE, GameMode.SURVIVAL)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val gg = LocalGG.current
    val game = LocalGame.current
    val premium = LocalPremium.current
    val auth = LocalAuth.current
    val router = LocalRouter.current
    val session = LocalSession.current

    var selectedAchievement by remember { mutableStateOf<AchievementDef?>(null) }

    val state = game.state
    val level = game.levelInfo
    val accuracyTotal = state.totalCorrect + state.totalWrong
    val accuracy = if (accuracyTotal > 0) Math.round(state.totalCorrect * 100.0 / accuracyTotal).toInt() else 0
    val unlocked = state.unlockedAchievements.toSet()
    val weakProblems = state.wrongPool.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(6).map { it.key }
    val report = WeeklyReport.build(state.dayLog, Commit.todayStr())
    // 이메일을 아직 안 붙인 익명 계정 — 기기를 잃으면 기록도 잃는다
    val needsAccountLink = auth.state !is AuthStore.State.Disabled && !auth.isPermanent

    Column(
        Modifier.fillMaxSize().background(gg.bg)
            // 상태바 영역은 스크롤 밖에 둔다 — 스크롤한 내용이 시계·배터리 밑으로 비치지 않게
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .readableWidth()
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 헤더 + 설정
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("프로필", style = suite(FontWeight.ExtraBold, 24), color = gg.text,
                modifier = Modifier.weight(1f).semantics { heading() })
            PressableCard(onClick = { router.settingsOpen = true }) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(gg.surface2), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Settings, "설정", tint = gg.textMuted, modifier = Modifier.size(22.dp))
                }
            }
        }

        // 레벨 헤더
        Row(
            Modifier.fillMaxWidth().ggCard().padding(20.dp)
                .clearAndSetSemantics {
                    contentDescription = "레벨 ${level.level}, ${Level.title(level.level)}, " +
                        "다음 레벨까지 ${level.xpForNextLevel - level.currentLevelXp} XP"
                },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(gg.accent),
                contentAlignment = Alignment.Center) {
                Text("${level.level}", style = suite(FontWeight.ExtraBold, 24), color = gg.accentFg)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Lv.${level.level} · ${Level.title(level.level)}",
                    style = suite(FontWeight.ExtraBold, 17), color = gg.text)
                GaugeBar(level.progress, height = 8.dp)
                Text("${level.totalXp} XP · 다음 레벨까지 ${level.xpForNextLevel - level.currentLevelXp} XP",
                    style = suite(FontWeight.Bold, 12), color = gg.textMuted)
            }
        }

        // 통계 3열
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat(Modifier.weight(1f), "정답", "${state.totalCorrect}")
            MiniStat(Modifier.weight(1f), "정확도", "$accuracy%")
            MiniStat(Modifier.weight(1f), "최고 콤보", "${state.maxCombo}")
        }

        // 이번 주 학습 리포트
        WeeklyReportCard(report, onReview = if (state.wrongPool.isEmpty()) null else ({ session.startReview() }))

        // 최고 기록
        Text("최고 기록", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SCORED_MODES.forEach { id ->
                val best = state.bestScores[id] ?: 0
                val tint = ModeStyle.tint(id)
                Row(
                    Modifier.weight(1f).ggCard(16.dp).padding(horizontal = 16.dp, vertical = 12.dp)
                        .clearAndSetSemantics {
                            contentDescription = "${Modes.def(id).name} 최고 기록 ${if (best > 0) "${best}점" else "없음"}"
                        },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) {
                        Icon(modeIcon(id), null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(Modes.def(id).name, style = suite(FontWeight.Bold, 12), color = gg.textMuted)
                        Text(if (best > 0) "${best}점" else "—", style = suite(FontWeight.ExtraBold, 17), color = gg.text)
                    }
                }
            }
        }

        // 정확도 추이
        if (state.recentAccuracy.size >= 2) {
            Column(Modifier.fillMaxWidth().ggCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("최근 정확도 추이", style = suite(FontWeight.Bold, 14), color = gg.text)
                        Text("최근 ${state.recentAccuracy.size}판 · 점선은 50%",
                            style = suite(FontWeight.Medium, 12), color = gg.textMuted)
                    }
                    // 그래프만으로는 현재 수준을 읽을 수 없어 최신값을 숫자로 집어 준다
                    Text("${state.recentAccuracy.last()}%",
                        style = suite(FontWeight.ExtraBold, 24), color = gg.accent)
                }
                Row(
                    Modifier.clearAndSetSemantics {
                        contentDescription = "최근 정확도 " + state.recentAccuracy.joinToString(", ") { "$it%" }
                    },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // y축이 0~100 고정임을 눈으로 확인할 수 있게 눈금을 적는다
                    Column(
                        Modifier.width(24.dp).height(64.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End,
                    ) {
                        listOf("100", "50", "0").forEach {
                            Text(it, style = suite(FontWeight.Bold, 9), color = gg.textMuted)
                        }
                    }
                    Sparkline(
                        state.recentAccuracy,
                        Modifier.weight(1f).height(64.dp),
                        lowerBound = 0f, upperBound = 100f, baseline = 50f, markLast = true,
                    )
                }
            }
        }

        // 집중 공략 문제 → 바로 복습
        if (weakProblems.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("집중 공략 문제", style = suite(FontWeight.Bold, 14), color = gg.textMuted, modifier = Modifier.weight(1f))
                PressableCard(onClick = { session.startReview() }) {
                    Row(
                        Modifier.height(32.dp).clip(CircleShape).background(gg.accent).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = gg.accentFg, modifier = Modifier.size(14.dp))
                        Text("복습 시작", style = suite(FontWeight.ExtraBold, 13), color = gg.accentFg)
                    }
                }
            }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                weakProblems.forEach { key ->
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(gg.danger.copy(alpha = 0.1f))
                            .border(1.dp, gg.danger.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clearAndSetSemantics { contentDescription = key.replace("x", " 곱하기 ") }
                    ) {
                        Text(key.replace("x", " × "), style = suite(FontWeight.ExtraBold, 14), color = gg.danger)
                    }
                }
            }
        }

        // 업적 그리드 3열 — 눌러서 목표·진행도를 본다
        Row(verticalAlignment = Alignment.Bottom) {
            Text("업적 (${unlocked.size}/${Achievements.all.size})", style = suite(FontWeight.Bold, 14),
                color = gg.textMuted, modifier = Modifier.weight(1f))
            Text("눌러서 목표 보기", style = suite(FontWeight.Medium, 11), color = gg.textMuted.copy(alpha = 0.8f))
        }
        Achievements.all.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a ->
                    val on = a.id in unlocked
                    val tint = (if (on) gg.accent else gg.textMuted).copy(alpha = if (on) 1f else 0.7f)
                    val fraction = Achievements.progress(a, state).fraction.toFloat()
                    PressableCard(
                        modifier = Modifier.weight(1f).clearAndSetSemantics {
                            contentDescription = "${a.name}, ${if (on) "달성" else "진행 중"}"
                        },
                        onClick = { selectedAchievement = a },
                    ) {
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (on) gg.accent.copy(alpha = 0.1f) else gg.surface)
                                .border(1.dp, if (on) gg.accent.copy(alpha = 0.3f) else gg.border, RoundedCornerShape(16.dp))
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(achievementIcon(a.icon), null, tint = tint, modifier = Modifier.size(22.dp))
                            Text(a.name, style = suite(FontWeight.Bold, 11), color = tint,
                                textAlign = TextAlign.Center, minLines = 2, maxLines = 2)
                            // 잠긴 업적은 얼마나 왔는지 가는 막대로 보여 준다
                            Box(Modifier.width(44.dp).height(3.dp).clip(CircleShape)
                                .background(if (on) Color.Transparent else gg.surface2)) {
                                if (!on) Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(gg.accent.copy(alpha = 0.7f)))
                            }
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // 계정 연결 권유 — 전체 흐름은 설정에 있고, 여기서는 존재를 알린다
        if (needsAccountLink) {
            PressableCard(onClick = { router.settingsOpen = true }) {
                Row(
                    Modifier.fillMaxWidth().ggCard(16.dp).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(gg.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Shield, null, tint = gg.accent, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("기록 지키기", style = suite(FontWeight.ExtraBold, 15), color = gg.text)
                        Text("보호자 이메일을 연결하면 기기를 바꿔도 기록이 남아요",
                            style = suite(FontWeight.Medium, 12), color = gg.textMuted)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = gg.textMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        // 이용권 권유
        if (!premium.isPremium) {
            PressableCard(onClick = { premium.openPaywall() }) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(GGColors.indigo, GGColors.violet)))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("평생 이용권", style = suite(FontWeight.ExtraBold, 15), color = Color.White)
                        Text("모든 모드와 3D 어드벤처 · 한 번 구매로 계속",
                            style = suite(FontWeight.Medium, 12), color = Color.White.copy(alpha = 0.85f))
                    }
                    Text(premium.price, style = suite(FontWeight.ExtraBold, 14), color = Color.White)
                }
            }
        }
    }

    // 업적 상세 — 무엇을 하면 얻는지, 지금 어디까지 왔는지
    selectedAchievement?.let { a ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedAchievement = null },
            sheetState = sheetState,
            containerColor = gg.bg,
        ) {
            AchievementDetail(a, a.id in unlocked, Achievements.progress(a, state))
        }
    }
}

@Composable
private fun AchievementDetail(def: AchievementDef, unlocked: Boolean, progress: AchievementProgress) {
    val gg = LocalGG.current
    val tint = if (unlocked) gg.accent else gg.textMuted
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 28.dp).padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(achievementIcon(def.icon), null, tint = tint, modifier = Modifier.size(32.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(def.name, style = suite(FontWeight.ExtraBold, 22), color = gg.text)
            Text(def.description, style = suite(FontWeight.Medium, 15), color = gg.textMuted, textAlign = TextAlign.Center)
        }
        if (unlocked) {
            Pill(bg = gg.success.copy(alpha = 0.15f), fg = gg.success) {
                Icon(Icons.Filled.Verified, null, Modifier.size(14.dp))
                Text("달성했어요!")
            }
        } else {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    .clearAndSetSemantics { contentDescription = "진행도 ${progress.target} 중 ${progress.current}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GaugeBar(progress.fraction)
                Text("${progress.current} / ${progress.target}", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
            }
        }
    }
}

@Composable
private fun MiniStat(modifier: Modifier, label: String, value: String) {
    val gg = LocalGG.current
    Column(
        modifier.ggCard(16.dp).padding(vertical = 12.dp).clearAndSetSemantics { contentDescription = "$label $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = suite(FontWeight.ExtraBold, 20), color = gg.text)
        Text(label, style = suite(FontWeight.Bold, 12), color = gg.textMuted)
    }
}

// 스파크라인 (iOS Sparkline.swift 이식)
@Composable
fun Sparkline(
    data: List<Int>,
    modifier: Modifier = Modifier,
    color: Color? = null,
    // 고정 y축 범위. null 이면 데이터의 min~max 에 맞춘다.
    //
    // 정확도처럼 눈금 자체에 의미가 있는 값은 반드시 고정해야 한다. 자동 스케일은
    // 90% 로 꾸준한 기록을 바닥에 붙여 0% 처럼 보이게 하고, 88·90·92 의 미세한
    // 차이를 천장까지 과장한다 — 둘 다 사실과 다른 인상을 준다.
    lowerBound: Float? = null,
    upperBound: Float? = null,
    // 해석 기준선 (예: 정확도 50%). 범위 안에 있을 때만 그린다.
    baseline: Float? = null,
    // 마지막 값에 점을 찍어 "현재"를 집어 준다
    markLast: Boolean = false,
) {
    val gg = LocalGG.current
    val c = color ?: gg.accent
    val borderColor = gg.border
    Canvas(modifier) {
        if (data.size < 2) return@Canvas
        val low = lowerBound ?: data.min().toFloat()
        val high = maxOf(low + 1f, upperBound ?: data.max().toFloat())
        fun yFor(value: Float) =
            size.height - ((value - low) / (high - low)).coerceIn(0f, 1f) * size.height
        val stepX = size.width / (data.size - 1)
        val pts = data.mapIndexed { i, v -> Offset(i * stepX, yFor(v.toFloat())) }
        // 기준선
        if (baseline != null && baseline > low && baseline < high) {
            val y = yFor(baseline)
            drawLine(
                borderColor, Offset(0f, y), Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            )
        }
        // 채움
        val fill = Path().apply {
            moveTo(pts.first().x, size.height)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, size.height)
            close()
        }
        drawPath(fill, c.copy(alpha = 0.12f))
        // 라인
        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(line, c, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (markLast) {
            drawCircle(c, radius = 3.5.dp.toPx(), center = pts.last())
        }
    }
}
