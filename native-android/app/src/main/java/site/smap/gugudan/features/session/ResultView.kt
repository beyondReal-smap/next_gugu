package site.smap.gugudan.features.session

import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import site.smap.gugudan.BuildConfig
import site.smap.gugudan.core.ReviewMoment
import site.smap.gugudan.core.ReviewPolicy
import site.smap.gugudan.core.ReviewPromptLog
import site.smap.gugudan.services.InAppReview
import site.smap.gugudan.services.Persistence
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
import androidx.compose.material.icons.filled.Replay
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextDecoration
import site.smap.gugudan.core.GivenAnswer
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
import site.smap.gugudan.R
import site.smap.gugudan.core.ModeKind
import site.smap.gugudan.core.Modes
import site.smap.gugudan.core.Seconds
import site.smap.gugudan.core.SpokenMath
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.core.ReminderPlanner
import site.smap.gugudan.features.reminder.rememberEnableReminder
import site.smap.gugudan.services.rememberStrings
import site.smap.gugudan.store.LocalGame
import site.smap.gugudan.store.LocalPush
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
        def.scored -> stringResource(if (commit.isNewBest) R.string.new_record else R.string.nice_work)
        accuracy >= 95 -> stringResource(R.string.perfect)
        accuracy >= 70 -> stringResource(R.string.great_job)
        else -> stringResource(R.string.nice_try)
    }
    val strings = rememberStrings()

    var confetti by remember { mutableStateOf(0) }
    var levelUp by remember { mutableStateOf(false) }

    // 학습 알림 권유 — 첫 실행이 아니라 한 판을 끝낸 뒤에 묻는다 (iOS 와 같은 흐름)
    val reminder = LocalReminder.current
    val push = LocalPush.current
    // 권유 시각 = 지금 시각. "이 시간에" 공부한 아이는 내일도 이 시간이 편하다 (야간은 8~20시로 당긴다)
    val offerHour = remember { ReminderPlanner.clampHour(LocalTime.now().hour) }
    var reminderNotice by remember { mutableStateOf<String?>(null) }
    val enableReminder = rememberEnableReminder { granted ->
        // 학습 알림을 허용하면 공지 알림도 함께 켠다 (카드 부제에서 안내)
        if (granted) push.setService(true)
        reminderNotice = if (granted) {
            strings.get(R.string.we_ll_remind_you_every_day_at_you_can, ReminderPlanner.hourLabel(offerHour))
        } else {
            strings.get(R.string.notifications_are_off_you_can_turn_on)
        }
    }

    LaunchedEffect(Unit) {
        if (accuracy >= 80 || commit.isNewBest) confetti += 1
        if (commit.leveledUp) { delay(600); levelUp = true }
    }

    // 잘 해낸 판이 끝났을 때 앱 평가를 요청한다 (조건은 ReviewPolicy, iOS 와 같다).
    // 알림 권유 카드·레벨업 연출과 겹치면 이번 판은 건너뛴다 — 한 화면에 요청이 둘 이상 뜨지 않게
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (reminder.shouldOfferAfterSession || commit.leveledUp) return@LaunchedEffect
        val activity = context as? Activity ?: return@LaunchedEffect
        val persistence = Persistence(context.applicationContext)
        val moment = ReviewMoment(partial = result.partial, accuracy = accuracy, isNewBest = commit.isNewBest,
                                  improvedStars = commit.improvedStars, goalReached = commit.goalReached)
        val log = persistence.loadReviewLog() ?: ReviewPromptLog()
        val now = System.currentTimeMillis()
        if (!ReviewPolicy.shouldAsk(moment, game.state, log, BuildConfig.VERSION_NAME, now)) return@LaunchedEffect
        // Play 가 카드를 실제로 띄웠는지는 알 수 없다(할당량) — 요청한 사실을 먼저 남겨 같은 버전에서 반복하지 않는다
        persistence.saveReviewLog(ReviewPolicy.recordAsk(log, BuildConfig.VERSION_NAME, now))
        // 결과·축하 효과를 먼저 보여 준 뒤에 띄운다
        delay(1500)
        InAppReview.request(activity)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .statusBarsPadding().navigationBarsPadding()
                .readableWidth(560.dp)
                .padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.complete, stringResource(engine.modeNameRes)), style = suite(FontWeight.Bold, 14), color = gg.textMuted)
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
                            Text(stringResource(R.string.new_record_2))
                        }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${commit.score}", style = suite(FontWeight.ExtraBold, 56), color = gg.text)
                        Text(stringResource(R.string.pts_2), style = suite(FontWeight.Bold, 20), color = gg.textMuted,
                            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp))
                    }
                    best?.let { Text(pluralStringResource(R.plurals.best_points, it, it), style = suite(FontWeight.Bold, 14), color = gg.textMuted) }
                }
            }

            // 핵심 수치 2×2
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Modifier.weight(1f), Icons.Filled.Check, gg.success, stringResource(R.string.accuracy), "$accuracy%")
                    StatCard(Modifier.weight(1f), Icons.Filled.Speed, gg.accent, stringResource(R.string.avg_speed), Seconds.text(strings, avgMs / 1000.0))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(Modifier.weight(1f), Icons.Filled.AutoAwesome, gg.warning, stringResource(R.string.xp_earned), "+${commit.xpEarned}")
                    StatCard(Modifier.weight(1f), Icons.Filled.LocalFireDepartment, gg.danger, stringResource(R.string.best_combo), "${result.maxCombo}")
                }
            }

            // 마스터리 (단 집중)
            commit.table?.let { table ->
                val masteryLabel = stringResource(
                    if (commit.improvedStars) R.string.table_mastery_stars_new_record else R.string.table_mastery_stars,
                    table, commit.newStars,
                )
                Row(
                    Modifier.fillMaxWidth().ggCard(16.dp).padding(horizontal = 20.dp, vertical = 16.dp)
                        .clearAndSetSemantics {
                            contentDescription = masteryLabel
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.table_mastery, table), style = suite(FontWeight.Bold, 14), color = gg.text)
                        if (commit.improvedStars) {
                            Text(stringResource(R.string.new_record_3), style = suite(FontWeight.Bold, 12), color = gg.accent)
                        }
                    }
                    StarsView(commit.newStars, size = 24.dp)
                }
            }

            // 틀린 문제 (같은 식은 한 번만, 처음 틀린 순서대로) — 아이가 쓴 답과 정답을 나란히 보여 준다
            val missed = result.answers.filter { !it.correct }.distinctBy { it.a to it.b }
            if (missed.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().ggCard(16.dp).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Replay, null, tint = gg.danger, modifier = Modifier.size(16.dp))
                        Text(stringResource(R.string.problems_to_review), style = suite(FontWeight.Bold, 14), color = gg.text)
                        Text("${missed.size}", style = suite(FontWeight.ExtraBold, 13), color = gg.danger)
                    }
                    missed.forEach { a ->
                        val mine = when (val g = a.given) {
                            is GivenAnswer.Number -> stringResource(R.string.my_answer, g.value)
                            is GivenAnswer.Boolean -> stringResource(R.string.my_pick, oxMark(g.value))
                            else -> null
                        }
                        Row(
                            Modifier.fillMaxWidth().clearAndSetSemantics {
                                contentDescription = SpokenMath.sentences(SpokenMath.equationLabel(a.a, a.b), mine)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${a.a} × ${a.b} = ${a.a * a.b}", style = suite(FontWeight.ExtraBold, 18),
                                color = gg.text, modifier = Modifier.weight(1f))
                            if (mine != null) {
                                Text(mine, style = suite(FontWeight.Bold, 13).copy(
                                    textDecoration = TextDecoration.LineThrough), color = gg.danger)
                            }
                        }
                    }
                }
            }

            if (commit.goalReached) {
                Text(
                    stringResource(R.string.you_reached_today_s_goal),
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
                            Text(stringResource(R.string.want_a_reminder_at_this_time_tomorrow),
                                style = suite(FontWeight.ExtraBold, 15), color = gg.text)
                        }
                        Text(
                            stringResource(R.string.we_ll_send_today_s_times_tables_every, ReminderPlanner.hourLabel(offerHour)) +
                                "\n" + stringResource(R.string.we_ll_also_send_important_announcements),
                            style = suite(FontWeight.Medium, 13), color = gg.textMuted,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GGButton(variant = GGButtonVariant.SURFACE, size = GGButtonSize.MD,
                                modifier = Modifier.weight(1f), onClick = { reminder.declineOffer() }) {
                                Text(stringResource(R.string.no_thanks))
                            }
                            GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.MD,
                                modifier = Modifier.weight(1f), onClick = { enableReminder(offerHour) }) {
                                Text(stringResource(R.string.turn_on_reminders))
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
                        Text(stringResource(R.string.retry_mistakes, done.wrongCount))
                    }
                }
                GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG,
                    onClick = { engine.restart(retryWrong = false) }) {
                    Text(stringResource(R.string.play_again))
                }
                GGButton(variant = GGButtonVariant.GHOST, size = GGButtonSize.MD, onClick = onClose) {
                    Icon(Icons.Filled.Close, null, Modifier.size(16.dp))
                    Text(stringResource(R.string.close))
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
