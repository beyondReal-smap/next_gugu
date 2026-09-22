package site.smap.gugudan.features.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import site.smap.gugudan.core.ModeKind
import site.smap.gugudan.core.Modes
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.core.ReminderPlanner
import site.smap.gugudan.features.reminder.rememberEnableReminder
import site.smap.gugudan.store.LocalGame
import site.smap.gugudan.store.LocalReminder
import androidx.compose.material.icons.filled.NotificationsActive
import java.time.LocalTime

// 세션 결과 화면 (iOS ResultView.swift 이식)

@Composable
fun ResultView(engine: SessionEngine, done: SessionDone, onClose: () -> Unit) {
    val gg = LocalGG.current
    val game = LocalGame.current

    val result = done.result
    val commit = done.commit
    val def = Modes.def(result.mode)
    val total = result.answers.size
    val correct = total - done.wrongCount
    val accuracy = if (total > 0) Math.round(correct * 100.0 / total).toInt() else 0
    val avgMs = if (total > 0) Math.round(result.answers.sumOf { it.ms }.toDouble() / total).toInt() else 0
    val best = commit.score?.let { maxOf(game.state.bestScores[result.mode] ?: 0, it) }

    val headline = when {
        def.scored -> if (commit.isNewBest) "신기록 달성! 🏆" else "수고했어요!"
        accuracy >= 95 -> "완벽해요! 🎯"
        accuracy >= 70 -> "잘했어요!"
        else -> "좋은 시도예요!"
    }

    var confetti by remember { mutableStateOf(0) }
    var levelUp by remember { mutableStateOf(false) }

    // 학습 알림 권유 — 첫 실행이 아니라 한 판을 끝낸 뒤에 묻는다 (iOS 와 같은 흐름)
    val reminder = LocalReminder.current
    // 권유 시각 = 지금 시각. "이 시간에" 공부한 아이는 내일도 이 시간이 편하다 (야간은 8~20시로 당긴다)
    val offerHour = remember { ReminderPlanner.clampHour(LocalTime.now().hour) }
    var reminderNotice by remember { mutableStateOf<String?>(null) }
    val enableReminder = rememberEnableReminder { granted ->
        reminderNotice = if (granted) {
            "매일 ${ReminderPlanner.hourLabel(offerHour)}에 알려드릴게요. 프로필 > 설정에서 바꿀 수 있어요."
        } else {
            "알림이 꺼져 있어요. 휴대폰 설정에서 구구 어드벤처 알림을 켤 수 있어요."
        }
    }

    LaunchedEffect(Unit) {
        if (accuracy >= 80 || commit.isNewBest) confetti += 1
        if (commit.leveledUp) { delay(600); levelUp = true }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${def.name} 완료", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
                Text(headline, style = suite(FontWeight.ExtraBold, 28), color = gg.text)
            }

            // 점수형 모드 히어로
            if (def.scored && commit.score != null) {
                Column(
                    Modifier.fillMaxWidth().ggCard().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (commit.isNewBest) {
                        Pill(bg = gg.warning.copy(alpha = 0.15f), fg = gg.warning) {
                            Icon(Icons.Filled.EmojiEvents, null, Modifier.size(14.dp))
                            Text("신기록!")
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${commit.score}", style = suite(FontWeight.ExtraBold, 56), color = gg.text)
                        Text("점", style = suite(FontWeight.Bold, 20), color = gg.textMuted,
                            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp))
                    }
                    best?.let { Text("최고 기록 ${it}점", style = suite(FontWeight.Bold, 14), color = gg.textMuted) }
                }
            }

            // 핵심 수치 2×2
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Modifier.weight(1f), Icons.Filled.Check, gg.success, "정확도", "$accuracy%")
                    StatCard(Modifier.weight(1f), Icons.Filled.Speed, gg.accent, "평균 속도", "%.1f초".format(avgMs / 1000.0))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Modifier.weight(1f), Icons.Filled.AutoAwesome, gg.warning, "획득 XP", "+${commit.xpEarned}")
                    StatCard(Modifier.weight(1f), Icons.Filled.LocalFireDepartment, gg.danger, "최고 콤보", "${result.maxCombo}")
                }
            }

            // 마스터리 (단 집중)
            commit.table?.let { table ->
                Row(
                    Modifier.fillMaxWidth().ggCard(16.dp).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${table}단 마스터리", style = suite(FontWeight.Bold, 14), color = gg.text)
                        if (commit.improvedStars) {
                            Text("새 기록 달성!", style = suite(FontWeight.Bold, 12), color = gg.accent)
                        }
                    }
                    StarsView(commit.newStars, size = 24.dp)
                }
            }

            if (commit.goalReached) {
                Text(
                    "🎉 오늘의 목표를 달성했어요!",
                    style = suite(FontWeight.Bold, 14), color = gg.success, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(gg.success.copy(alpha = 0.15f)).padding(vertical = 12.dp),
                )
            }

            if ((reminder.shouldOfferAfterSession && !result.partial) || reminderNotice != null) {
                Column(
                    Modifier.fillMaxWidth().ggCard(16.dp).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val notice = reminderNotice
                    if (notice != null) {
                        Text(notice, style = suite(FontWeight.Bold, 14), color = gg.text)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.NotificationsActive, null, tint = gg.accent,
                                modifier = Modifier.size(18.dp))
                            Text("내일도 이 시간에 알려드릴까요?",
                                style = suite(FontWeight.ExtraBold, 15), color = gg.text)
                        }
                        Text(
                            "매일 ${ReminderPlanner.hourLabel(offerHour)}에 오늘의 구구단을 알려드려요. 밤에는 보내지 않아요.",
                            style = suite(FontWeight.Medium, 13), color = gg.textMuted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GGButton(variant = GGButtonVariant.SURFACE, size = GGButtonSize.MD,
                                modifier = Modifier.weight(1f), onClick = { reminder.declineOffer() }) {
                                Text("괜찮아요")
                            }
                            GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.MD,
                                modifier = Modifier.weight(1f), onClick = { enableReminder(offerHour) }) {
                                Text("알림 받기")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f, fill = false))

            // 액션
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (def.kind == ModeKind.FIXED && done.wrongCount > 0) {
                    GGButton(variant = GGButtonVariant.SURFACE, size = GGButtonSize.LG,
                        onClick = { engine.restart(retryWrong = true) }) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(20.dp))
                        Text("틀린 문제만 다시 (${done.wrongCount})")
                    }
                }
                GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG,
                    onClick = { engine.restart(retryWrong = false) }) {
                    Text("한 판 더")
                }
                GGButton(variant = GGButtonVariant.GHOST, size = GGButtonSize.MD, onClick = onClose) {
                    Icon(Icons.Filled.Close, null, Modifier.size(16.dp))
                    Text("닫기")
                }
            }
        }

        ConfettiView(confetti)
        AchievementToast(commit.unlocked,
            Modifier.statusBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp))
        LevelUpOverlay(levelUp, commit.newLevel) { levelUp = false }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color, label: String, value: String,
) {
    val gg = LocalGG.current
    Column(
        modifier.ggCard(16.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Text(label, style = suite(FontWeight.Bold, 12), color = gg.textMuted)
        }
        Text(value, style = suite(FontWeight.ExtraBold, 22), color = gg.text)
    }
}
