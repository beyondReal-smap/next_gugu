package site.smap.gugudan.features.profile

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import site.smap.gugudan.core.Achievements
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.Level
import site.smap.gugudan.core.Modes
import site.smap.gugudan.core.PremiumConfig
import site.smap.gugudan.core.Theme
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.features.home.GaugeBar
import site.smap.gugudan.features.home.modeIcon
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Sound
import site.smap.gugudan.store.LocalGame
import site.smap.gugudan.store.LocalPremium
import site.smap.gugudan.store.LocalTheme

// 프로필 탭 (iOS ProfileView 이식) — 레벨/통계/업적/이용권/설정

private val SCORED_MODES = listOf(GameMode.CHALLENGE, GameMode.SURVIVAL)

@Composable
fun ProfileScreen() {
    val gg = LocalGG.current
    val game = LocalGame.current
    val theme = LocalTheme.current
    val premium = LocalPremium.current
    val context = LocalContext.current

    var soundOn by remember { mutableStateOf(Sound.enabled) }
    var confirmReset by remember { mutableStateOf(false) }

    val state = game.state
    val level = game.levelInfo
    val accuracyTotal = state.totalCorrect + state.totalWrong
    val accuracy = if (accuracyTotal > 0) Math.round(state.totalCorrect * 100.0 / accuracyTotal).toInt() else 0
    val unlocked = state.unlockedAchievements.toSet()
    val weakProblems = state.wrongPool.entries.sortedByDescending { it.value }.take(6).map { it.key }

    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    Column(
        Modifier.fillMaxSize().background(gg.bg)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 헤더
        Text("프로필", style = suite(FontWeight.ExtraBold, 24), color = gg.text)

        // 레벨 헤더
        Row(
            Modifier.fillMaxWidth().ggCard().padding(20.dp),
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
                Text("${level.totalXp} XP", style = suite(FontWeight.Bold, 12), color = gg.textMuted)
            }
        }

        // 통계 3열
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat(Modifier.weight(1f), "정답", "${state.totalCorrect}")
            MiniStat(Modifier.weight(1f), "정확도", "$accuracy%")
            MiniStat(Modifier.weight(1f), "최고 콤보", "${state.maxCombo}")
        }

        // 최고 기록
        Text("최고 기록", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SCORED_MODES.forEach { id ->
                val best = state.bestScores[id] ?: 0
                val tint = ModeStyle.tint(id)
                Row(
                    Modifier.weight(1f).ggCard(16.dp).padding(horizontal = 16.dp, vertical = 12.dp),
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
            Column(Modifier.fillMaxWidth().ggCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("최근 정확도 추이", style = suite(FontWeight.Bold, 14), color = gg.text)
                Sparkline(state.recentAccuracy, Modifier.fillMaxWidth().height(48.dp))
            }
        }

        // 집중 공략 문제
        if (weakProblems.isNotEmpty()) {
            Text("집중 공략 문제", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                weakProblems.forEach { key ->
                    Box(
                        Modifier.clip(RoundedCornerShape(12.dp))
                            .background(gg.danger.copy(alpha = 0.1f))
                            .border(1.dp, gg.danger.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(key.replace("x", " × "), style = suite(FontWeight.ExtraBold, 14), color = gg.danger)
                    }
                }
            }
        }

        // 업적 그리드 4열
        Text("업적 (${unlocked.size}/${Achievements.all.size})", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
        Achievements.all.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a ->
                    val on = a.id in unlocked
                    Column(
                        Modifier.weight(1f).aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) gg.accent.copy(alpha = 0.1f) else gg.surface)
                            .border(1.dp, if (on) gg.accent.copy(alpha = 0.3f) else gg.border, RoundedCornerShape(16.dp))
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(achievementIcon(a.icon), null,
                            tint = (if (on) gg.accent else gg.textMuted).copy(alpha = if (on) 1f else 0.5f),
                            modifier = Modifier.size(18.dp))
                        Text(a.name, style = suite(FontWeight.Bold, 9),
                            color = (if (on) gg.accent else gg.textMuted).copy(alpha = if (on) 1f else 0.5f),
                            textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // 이용권
        Text("이용권", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
        Column(Modifier.fillMaxWidth().ggCard(16.dp)) {
            if (premium.isPremium) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = gg.accent, modifier = Modifier.size(20.dp))
                    Text("평생 이용권", style = suite(FontWeight.Bold, 15), color = gg.text, modifier = Modifier.weight(1f))
                    Box(Modifier.clip(CircleShape).background(gg.accent.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("이용 중", style = suite(FontWeight.ExtraBold, 12), color = gg.accent)
                    }
                }
            } else {
                SettingRow(Icons.Filled.AutoAwesome, "평생 이용권 · 커피 한 잔 값", premium.price) { premium.openPaywall() }
            }
            Divider()
            SettingRow(Icons.Filled.Description, "이용약관", "") { open(PremiumConfig.Legal.TERMS) }
            Divider()
            SettingRow(Icons.Filled.VerifiedUser, "개인정보처리방침", "") { open(PremiumConfig.Legal.PRIVACY) }
        }

        // 설정
        Text("설정", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
        Column(Modifier.fillMaxWidth().ggCard(16.dp)) {
            SettingRow(
                if (theme.theme == Theme.DARK) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                "다크 모드", if (theme.theme == Theme.DARK) "켜짐" else "꺼짐",
            ) { theme.toggle() }
            Divider()
            SettingRow(
                if (soundOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                "효과음", if (soundOn) "켜짐" else "꺼짐",
            ) {
                soundOn = !soundOn
                Sound.setEnabled(soundOn)
            }
        }

        // 초기화
        if (!confirmReset) {
            GGButton(variant = GGButtonVariant.GHOST, onClick = { confirmReset = true }) {
                Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                Text("기록 초기화", color = gg.danger)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GGButton(variant = GGButtonVariant.SURFACE, modifier = Modifier.weight(1f),
                    onClick = { confirmReset = false }) { Text("취소") }
                GGButton(variant = GGButtonVariant.DANGER, modifier = Modifier.weight(1f),
                    onClick = { game.resetProgress(); confirmReset = false }) { Text("초기화 확인") }
            }
        }
    }
}

@Composable
private fun MiniStat(modifier: Modifier, label: String, value: String) {
    val gg = LocalGG.current
    Column(
        modifier.ggCard(16.dp).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = suite(FontWeight.ExtraBold, 20), color = gg.text)
        Text(label, style = suite(FontWeight.Bold, 12), color = gg.textMuted)
    }
}

@Composable
private fun Divider() {
    val gg = LocalGG.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(gg.border))
}

@Composable
private fun SettingRow(icon: ImageVector, label: String, action: String, onTap: () -> Unit) {
    val gg = LocalGG.current
    PressableCard(onClick = { Haptics.impactLight(); onTap() }) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = gg.textMuted, modifier = Modifier.size(20.dp))
            Text(label, style = suite(FontWeight.Bold, 15), color = gg.text, modifier = Modifier.weight(1f))
            Text(action, style = suite(FontWeight.Bold, 14), color = gg.accent)
        }
    }
}

// 스파크라인 (iOS Sparkline.swift 이식)
@Composable
fun Sparkline(data: List<Int>, modifier: Modifier = Modifier, color: Color? = null) {
    val gg = LocalGG.current
    val c = color ?: gg.accent
    Canvas(modifier) {
        if (data.size < 2) return@Canvas
        val maxV = (data.max()).toFloat()
        val minV = (data.min()).toFloat()
        val range = maxOf(1f, maxV - minV)
        val stepX = size.width / (data.size - 1)
        val pts = data.mapIndexed { i, v ->
            Offset(i * stepX, size.height - (v - minV) / range * size.height)
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
    }
}
