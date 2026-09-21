package site.smap.gugudan.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import site.smap.gugudan.core.Achievements
import site.smap.gugudan.core.Level
import site.smap.gugudan.core.adventure.AdvAchievements
import kotlin.random.Random

// 피드백 오버레이 — Confetti / LevelUpOverlay / AchievementToast (iOS Feedback.swift 이식)

// 업적 아이콘 키 → Material 아이콘
fun achievementIcon(key: String): ImageVector = when (key) {
    "Check" -> Icons.Filled.Check
    "Target" -> Icons.Filled.TrackChanges
    "Award" -> Icons.Filled.EmojiEvents
    "Zap" -> Icons.Filled.Bolt
    "Flame" -> Icons.Filled.LocalFireDepartment
    "CalendarCheck", "CalendarHeart" -> Icons.Filled.Event
    "Crown" -> Icons.Filled.EmojiEvents
    "Star" -> Icons.Filled.Star
    "GraduationCap" -> Icons.Filled.School
    "Sparkles" -> Icons.Filled.AutoAwesome
    "Trophy" -> Icons.Filled.EmojiEvents
    "TrendingUp" -> Icons.Filled.TrendingUp
    "Rocket" -> Icons.Filled.RocketLaunch
    "Timer" -> Icons.Filled.Timer
    "HeartPulse" -> Icons.Filled.Favorite
    "Compass" -> Icons.Filled.Explore
    "Swords" -> Icons.Filled.Shield
    "Gauge" -> Icons.Filled.Speed
    "Medal" -> Icons.Filled.MilitaryTech
    "Flag" -> Icons.Filled.Flag
    "Gem" -> Icons.Filled.Diamond
    else -> Icons.Filled.Star
}

/** 업적 id → (이름, 아이콘) — 코어/어드벤처 통합 조회 */
fun achievementInfo(id: String): Pair<String, ImageVector>? {
    Achievements.get(id)?.let { return it.name to achievementIcon(it.icon) }
    AdvAchievements.get(id)?.let { return it.name to achievementIcon(it.icon) }
    return null
}

// MARK: Confetti

private data class ConfettiPiece(
    val x: Float, val color: Color, val delay: Float, val rotation: Float, val size: Float)

@Composable
fun ConfettiView(trigger: Int, modifier: Modifier = Modifier) {
    val colors = listOf(GGColors.indigo, GGColors.amber, GGColors.emerald, GGColors.rose, GGColors.sky, GGColors.violet)
    var pieces by remember { mutableStateOf<List<ConfettiPiece>>(emptyList()) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        pieces = List(40) {
            ConfettiPiece(
                x = Random.nextFloat(), color = colors.random(),
                delay = Random.nextFloat() * 0.3f,
                rotation = Random.nextFloat() * 360f,
                size = 6f + Random.nextFloat() * 5f)
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1800))
        pieces = emptyList()
    }

    if (pieces.isNotEmpty()) {
        Canvas(modifier.fillMaxSize()) {
            val h = size.height
            for (p in pieces) {
                val t = ((progress.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                if (t <= 0f) continue
                val y = -40f + (h + 80f) * t
                val alpha = 1f - t
                rotate(p.rotation + t * 360f, pivot = Offset(p.x * size.width, y)) {
                    drawRect(
                        p.color.copy(alpha = alpha),
                        topLeft = Offset(p.x * size.width - p.size / 2, y - p.size),
                        size = Size(p.size.dp.toPx() / 2.5f, p.size.dp.toPx() / 1.6f),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rotate(
    degrees: Float, pivot: Offset, block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) = drawContext.transform.let { tf ->
    tf.rotate(degrees, pivot); block(); tf.rotate(-degrees, pivot)
}

// MARK: LevelUpOverlay

@Composable
fun LevelUpOverlay(show: Boolean, level: Int, onClose: () -> Unit) {
    if (!show) return
    val gg = LocalGG.current
    LaunchedEffect(Unit) { site.smap.gugudan.services.Haptics.success() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(gg.surface).padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.AutoAwesome, null, tint = gg.warning, modifier = Modifier.size(44.dp))
            Text("레벨 업!", style = suite(FontWeight.ExtraBold, 28), color = gg.text)
            Text("Lv.$level", style = suite(FontWeight.Black, 48), color = gg.accent)
            Text(Level.title(level), style = suite(FontWeight.Bold, 15), color = gg.textMuted)
        }
    }
}

// MARK: AchievementToast

@Composable
fun AchievementToast(ids: List<String>, modifier: Modifier = Modifier) {
    var visible by remember(ids) { mutableStateOf(ids) }
    val gg = LocalGG.current

    LaunchedEffect(ids) {
        if (ids.isEmpty()) return@LaunchedEffect
        site.smap.gugudan.services.Haptics.success()
        delay(2800)
        visible = emptyList()
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.forEach { id ->
            achievementInfo(id)?.let { (name, icon) ->
                Row(
                    Modifier.fillMaxWidth().ggCard(16.dp).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(gg.warning.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = gg.warning, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("업적 달성", style = suite(FontWeight.Bold, 11), color = gg.textMuted)
                        Text(name, style = suite(FontWeight.ExtraBold, 15), color = gg.text)
                    }
                }
            }
        }
    }
}
