package site.smap.gugudan.features.runner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import site.smap.gugudan.R
import site.smap.gugudan.core.Runner
import site.smap.gugudan.core.RunnerOutcome
import site.smap.gugudan.core.RunnerPhase
import site.smap.gugudan.core.Seconds
import site.smap.gugudan.core.SpokenMath
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.features.paywall.*
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Persistence
import site.smap.gugudan.services.rememberStrings
import site.smap.gugudan.store.LocalPremium
import site.smap.gugudan.store.MinigameStart

// 구구 점프 화면 (RunnerScreen.tsx / iOS RunnerView 이식)
// 보기 3개 중 정답을 고르면 자동으로 점프한다. 틀리거나 시간이 지나면 하트가 하나 줄어든다.

private val STAGE_BG = Color(0xFF153F35)
private val STAGE_BORDER = Color(0xFF294E3D)
private val STAGE_FG = Color(0xFFF3F6E8)
private val STAGE_MUTED = Color(0xFFC1D4C2)
private val STAGE_ACCENT = Color(0xFFDBEF9E)
private val OVERLAY_BG = Color(0xFFEFF3DF)
private val OVERLAY_TEXT = Color(0xFF153F35)
private val OVERLAY_MUTED = Color(0xFF486146)
private val HEART_ON = Color(0xFFF3A88C)
private val HEART_OFF = Color(0xFF6C897B)

@Composable
fun RunnerScreen(start: MinigameStart, onExit: () -> Unit) {
    val gg = LocalGG.current
    val context = LocalContext.current
    // 단 시트·'섞어서' 시트에서 열면 그 단을 골라 둔 채로, 홈에서 열면 게임 기본값으로 준비한다
    val engine = remember {
        val persistence = Persistence(context.applicationContext)
        when (start) {
            MinigameStart.Default -> RunnerEngine(persistence)
            is MinigameStart.Table -> RunnerEngine(persistence, initialTable = start.table)
        }
    }
    // 시스템 뒤로가기 — 달리는 중이면 일시정지(실수로 판을 날리지 않게), 멈춰 있거나 끝났으면 홈으로
    BackHandler {
        if (engine.game.phase == RunnerPhase.RUNNING) engine.pause() else onExit()
    }
    val game = engine.game

    val ready = game.phase == RunnerPhase.READY
    val over = game.phase == RunnerPhase.OVER
    val paused = game.phase == RunnerPhase.PAUSED
    val active = game.phase == RunnerPhase.RUNNING
    val premium = LocalPremium.current
    // 무료 체험 판이 끝났다 — 다시 하기 대신 결제 화면으로 안내한다(구매하면 바로 일반 종료 화면으로 바뀐다)
    val trialEnded = over && game.trialLeft != null && !premium.isPremium

    // 프레임 루프 — RUNNING 일 때만 돌고, 화면을 벗어나면 자동 정지
    LaunchedEffect(game.phase) {
        if (game.phase != RunnerPhase.RUNNING) return@LaunchedEffect
        var previous = 0L
        while (true) {
            withFrameMillis { now ->
                val dt = if (previous == 0L) 0.0 else (now - previous).toDouble()
                previous = now
                engine.tick(dt)
            }
            if (engine.game.phase != RunnerPhase.RUNNING) break
        }
    }

    // 백그라운드 전환 시 일시정지
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) engine.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier.fillMaxSize().background(gg.bg).statusBarsPadding(),
    ) {
        // 헤더
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressableCard(onClick = { engine.pause(); onExit() }) {
                Row(
                    Modifier.height(44.dp).padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = gg.textMuted, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.home_2), style = suite(FontWeight.Bold, 14), color = gg.textMuted)
                }
            }
            Spacer(Modifier.weight(1f))
            Pill(bg = gg.surface, fg = gg.textMuted) {
                Icon(Icons.Filled.DirectionsRun, null, Modifier.size(14.dp))
                Text(stringResource(R.string.learn_times_tables_on_the_run))
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 무대 (점수판 + 씬)
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(STAGE_BG)
            ) {
                Scoreboard(engine, onTogglePause = { engine.togglePause() }, enabled = !ready && !over)

                Box(Modifier.fillMaxWidth().aspectRatio(36f / 13f)) {
                    RunnerScene(game = game)

                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            if (ready) stringResource(R.string.today_s_little_adventure)
                            else stringResource(R.string.running_m, game.distance.toInt()),
                            style = suite(FontWeight.ExtraBold, 11), color = OVERLAY_MUTED,
                        )
                        Text(
                            if (game.combo >= 2) {
                                stringResource(R.string.in_a_row_speed, game.combo, Runner.level(game.score) + 1)
                            } else {
                                stringResource(R.string.speed_level, Runner.level(game.score) + 1)
                            },
                            style = suite(FontWeight.ExtraBold, 11), color = OVERLAY_MUTED,
                        )
                    }

                    if (paused || over) {
                        Box(
                            Modifier.fillMaxSize().background(OVERLAY_BG.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    stringResource(if (paused) R.string.taking_a_break else R.string.great_run),
                                    style = suite(FontWeight.ExtraBold, 22), color = OVERLAY_TEXT,
                                )
                                Text(
                                    if (paused) stringResource(R.string.keep_running_when_you_re_ready)
                                    else stringResource(R.string.obstacles_cleared_best_streak, game.score, game.maxCombo),
                                    style = suite(FontWeight.Bold, 13), color = OVERLAY_MUTED,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            // 하단 조작부
            Column(Modifier.fillMaxWidth().ggCard(20.dp).padding(16.dp)) {
                when {
                    trialEnded -> TrialEndPanel(game.score, onUnlock = { premium.openPaywall() }, onExit = onExit)
                    ready || over -> SetupPanel(engine, over)
                    paused -> PausePanel(engine, onExit)
                    else -> PlayPanel(engine, active)
                }
            }

            Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    pluralStringResource(
                        R.plurals.every_time_you_clear_obstacles_the_speed, Runner.SCORE_PER_LEVEL, Runner.SCORE_PER_LEVEL,
                    ),
                    style = suite(FontWeight.Medium, 12), color = gg.textMuted,
                )
                Text(
                    stringResource(R.string.best_scores_for_each_table_are_saved_on),
                    style = suite(FontWeight.Medium, 12), color = gg.textMuted,
                )
            }
        }
    }
}

@Composable
private fun Scoreboard(engine: RunnerEngine, onTogglePause: () -> Unit, enabled: Boolean) {
    val game = engine.game
    // 개수 단위 — 영어는 숫자만 (iOS SpokenMath.countSuffix)
    val countSuffix = SpokenMath.countSuffix(rememberStrings()).orEmpty()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(stringResource(R.string.obstacles_cleared), style = suite(FontWeight.Bold, 11), color = STAGE_MUTED)
            Text("${game.score}$countSuffix", style = suite(FontWeight.ExtraBold, 22), color = STAGE_FG)
        }
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.EmojiEvents, null, tint = STAGE_MUTED, modifier = Modifier.size(10.dp))
                Text(
                    engine.table?.let { stringResource(R.string.table_best, it) } ?: stringResource(R.string.overall_best),
                    style = suite(FontWeight.Bold, 11), color = STAGE_MUTED,
                )
            }
            Text("${engine.selectedBest}$countSuffix", style = suite(FontWeight.ExtraBold, 22), color = STAGE_ACCENT)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (heart in 1..Runner.MAX_LIVES) {
                Icon(
                    if (heart <= game.lives) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    null,
                    tint = if (heart <= game.lives) HEART_ON else HEART_OFF,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        PressableCard(enabled = enabled, onClick = onTogglePause) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).alpha(if (enabled) 1f else 0.3f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (game.phase == RunnerPhase.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = stringResource(if (game.phase == RunnerPhase.PAUSED) R.string.keep_running else R.string.pause),
                    tint = STAGE_FG, modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SetupPanel(engine: RunnerEngine, over: Boolean) {
    val gg = LocalGG.current
    val premium = LocalPremium.current
    val game = engine.game

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (over) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(
                        if (game.score > 0 && game.score >= engine.runBest) R.string.new_personal_best
                        else R.string.one_more_time_can_you_go_farther,
                    ),
                    style = suite(FontWeight.ExtraBold, 17), color = gg.text, textAlign = TextAlign.Center,
                )
                Text(
                    pluralStringResource(R.plurals.you_cleared_obstacles_learn_the_answers, game.score, game.score),
                    style = suite(FontWeight.Medium, 13), color = gg.textMuted, textAlign = TextAlign.Center,
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(GGColors.emerald.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.DirectionsRun, null, tint = GGColors.emerald, modifier = Modifier.size(20.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.get_it_right_to_jump_automatically), style = suite(FontWeight.ExtraBold, 15), color = gg.text)
                    Text(
                        stringResource(R.string.pick_the_right_answer_from_3_choices),
                        style = suite(FontWeight.Medium, 13), color = gg.textMuted,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.table_to_practice_while_running), style = suite(FontWeight.ExtraBold, 12), color = gg.textMuted)
            // 전체 + 2~9단 = 9개를 5열로 배치 (웹과 동일)
            val options: List<Int?> = listOf(null) + RunnerEngine.TABLES
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { value ->
                            TableChip(
                                label = if (value == null) stringResource(R.string.all) else stringResource(R.string.table, value),
                                selected = engine.table == value,
                                modifier = Modifier.weight(1f),
                                onClick = { Haptics.selection(); engine.table = value },
                            )
                        }
                        // 마지막 줄의 빈 칸을 채워 폭을 맞춘다
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        if (!premium.isPremium) TrialNotice()

        GGButton(
            variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG,
            onClick = { engine.start(MinigameTrial.limit(premium.isPremium)) },
        ) {
            Icon(
                if (over) Icons.Filled.RotateLeft else Icons.Filled.PlayArrow, null,
                modifier = Modifier.size(18.dp),
            )
            Text(stringResource(if (over) R.string.run_again else R.string.start_running))
        }
    }
}

@Composable
private fun TableChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val gg = LocalGG.current
    PressableCard(modifier = modifier, onClick = onClick) {
        Box(
            Modifier.fillMaxWidth().height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) STAGE_BG else gg.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label, style = suite(FontWeight.ExtraBold, 13),
                color = if (selected) Color(0xFFE6F5C1) else gg.text,
            )
        }
    }
}

@Composable
private fun PausePanel(engine: RunnerEngine, onExit: () -> Unit) {
    val gg = LocalGG.current
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.the_obstacles_and_timer_are_paused), style = suite(FontWeight.Medium, 13), color = gg.textMuted)
        GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG, onClick = { engine.togglePause() }) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
            Text(stringResource(R.string.keep_running_2))
        }
        PressableCard(onClick = { engine.pause(); onExit() }) {
            Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.back_to_practice), style = suite(FontWeight.Bold, 13), color = gg.textMuted)
            }
        }
    }
}

@Composable
private fun PlayPanel(engine: RunnerEngine, active: Boolean) {
    val gg = LocalGG.current
    val game = engine.game
    val answer = game.question.answer
    val remaining = Runner.remaining(game).toFloat()

    val feedback = when (game.outcome) {
        RunnerOutcome.CORRECT -> stringResource(R.string.correct_over_the_obstacle_you_go)
        RunnerOutcome.WRONG -> stringResource(R.string.so_close, game.question.a, game.question.b, answer)
        RunnerOutcome.MISSED -> stringResource(R.string.out_of_time, game.question.a, game.question.b, answer)
        null -> stringResource(R.string.pick_the_right_answer_before_the)
    }
    val strings = rememberStrings()
    val timeLabel = when (game.outcome) {
        null -> Seconds.text(strings, remaining * Runner.answerWindowMs(game.score) / 1000)
        RunnerOutcome.CORRECT -> stringResource(R.string.jump)
        else -> stringResource(R.string.remember_the_answer)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 한 줄 고정 — 상태 문구가 길어져도(예: "정답을 기억해요") 줄이 늘어 아래 식이 밀리지 않게 한다.
        // 상태 문구를 먼저 온전히 두고, 왼쪽 문구는 남는 폭 안에서 말줄임된다
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        R.string.obstacle,
                        engine.table?.let { stringResource(R.string.table, it) } ?: stringResource(R.string.all_tables),
                        game.round + 1,
                    ),
                    style = suite(FontWeight.Bold, 12), color = gg.textMuted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                game.trialLeft?.let { left ->
                    Spacer(Modifier.width(8.dp))
                    TrialProgressPill(left)
                }
            }
            Text(timeLabel, style = suite(FontWeight.Bold, 12), color = gg.textMuted,
                maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 8.dp))
        }

        // 남은 시간 게이지 — 25% 아래로 떨어지면 주의색
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(gg.surface2)) {
            Box(
                Modifier.fillMaxWidth(remaining).fillMaxSize()
                    .background(if (remaining < 0.25f && game.outcome == null) gg.warning else GGColors.emerald)
            )
        }

        Column(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${game.question.a}", style = suite(FontWeight.ExtraBold, 36), color = gg.text)
                Text("×", style = suite(FontWeight.ExtraBold, 36), color = gg.textMuted)
                Text("${game.question.b}", style = suite(FontWeight.ExtraBold, 36), color = gg.text)
                Text("=", style = suite(FontWeight.ExtraBold, 36), color = gg.textMuted)
                Text(
                    if (game.outcome != null) "$answer" else "?",
                    style = suite(FontWeight.ExtraBold, 36),
                    color = if (game.outcome == RunnerOutcome.CORRECT) gg.success else gg.textMuted,
                )
            }
            Text(
                feedback, style = suite(FontWeight.Bold, 13),
                color = if (game.outcome == RunnerOutcome.WRONG || game.outcome == RunnerOutcome.MISSED)
                    gg.warning else gg.textMuted,
                textAlign = TextAlign.Center,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            game.question.choices.forEachIndexed { index, choice ->
                ChoiceButton(
                    index = index, choice = choice, answer = answer,
                    given = game.given, revealed = game.outcome != null,
                    enabled = active && game.outcome == null,
                    modifier = Modifier.weight(1f),
                    onClick = { engine.answer(index) },
                )
            }
        }
    }
}

@Composable
private fun ChoiceButton(
    index: Int,
    choice: Int,
    answer: Int,
    given: Int?,
    revealed: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val gg = LocalGG.current
    val correct = revealed && choice == answer
    val wrong = revealed && choice == given && choice != answer
    val border = if (correct) gg.success else if (wrong) gg.warning else gg.border
    val fill = when {
        correct -> gg.success.copy(alpha = 0.1f)
        wrong -> gg.warning.copy(alpha = 0.1f)
        else -> gg.surface
    }
    val fg = when {
        correct -> gg.success
        wrong -> gg.warning
        else -> gg.text
    }

    PressableCard(modifier = modifier, enabled = enabled, onClick = onClick) {
        Box(
            Modifier.fillMaxWidth().height(76.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(fill)
                .alpha(if (revealed && !correct && !wrong) 0.4f else 1f),
        ) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("$choice", style = suite(FontWeight.ExtraBold, 30), color = fg)
            }
            Text(
                "${index + 1}", style = suite(FontWeight.Bold, 10), color = gg.textMuted,
                modifier = Modifier.padding(8.dp),
            )
            if (correct || wrong) {
                Icon(
                    if (correct) Icons.Filled.Check else Icons.Filled.Close, null,
                    tint = fg,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(14.dp),
                )
            }
            // 테두리 — 배경 위에 겹쳐 그린다
            Box(
                Modifier.fillMaxSize()
                    .border(2.dp, border, RoundedCornerShape(16.dp))
            )
        }
    }
}
