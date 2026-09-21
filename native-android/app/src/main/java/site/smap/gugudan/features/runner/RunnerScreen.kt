package site.smap.gugudan.features.runner

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import site.smap.gugudan.core.Runner
import site.smap.gugudan.core.RunnerOutcome
import site.smap.gugudan.core.RunnerPhase
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Persistence

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
fun RunnerScreen(onExit: () -> Unit) {
    val gg = LocalGG.current
    val context = LocalContext.current
    val engine = remember { RunnerEngine(Persistence(context.applicationContext)) }
    val game = engine.game

    val ready = game.phase == RunnerPhase.READY
    val over = game.phase == RunnerPhase.OVER
    val paused = game.phase == RunnerPhase.PAUSED
    val active = game.phase == RunnerPhase.RUNNING

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
                    Text("홈으로", style = suite(FontWeight.Bold, 14), color = gg.textMuted)
                }
            }
            Spacer(Modifier.weight(1f))
            Pill(bg = gg.surface, fg = gg.textMuted) {
                Icon(Icons.Filled.DirectionsRun, null, Modifier.size(14.dp))
                Text("달리며 배우는 구구단")
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
                            if (ready) "오늘의 작은 모험" else "${game.distance.toInt()}m 달리는 중",
                            style = suite(FontWeight.ExtraBold, 11), color = OVERLAY_MUTED,
                        )
                        Text(
                            if (game.combo >= 2) "${game.combo}연속 성공! · 속도 ${Runner.level(game.score) + 1}단계"
                            else "속도 ${Runner.level(game.score) + 1}단계",
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
                                    if (paused) "잠깐 쉬어가요" else "멋진 달리기였어요!",
                                    style = suite(FontWeight.ExtraBold, 22), color = OVERLAY_TEXT,
                                )
                                Text(
                                    if (paused) "준비되면 이어서 달려요."
                                    else "장애물 ${game.score}개 통과 · 최고 ${game.maxCombo}연속 성공",
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
                    ready || over -> SetupPanel(engine, over)
                    paused -> PausePanel(engine, onExit)
                    else -> PlayPanel(engine, active)
                }
            }

            Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "장애물 ${Runner.SCORE_PER_LEVEL}개를 넘을 때마다 속도가 한 단계 빨라져요. 앱을 벗어나면 자동으로 일시정지해요.",
                    style = suite(FontWeight.Medium, 12), color = gg.textMuted,
                )
                Text(
                    "단별 최고 기록은 이 기기에 저장돼요.",
                    style = suite(FontWeight.Medium, 12), color = gg.textMuted,
                )
            }
        }
    }
}

@Composable
private fun Scoreboard(engine: RunnerEngine, onTogglePause: () -> Unit, enabled: Boolean) {
    val game = engine.game
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("넘은 장애물", style = suite(FontWeight.Bold, 11), color = STAGE_MUTED)
            Text("${game.score}개", style = suite(FontWeight.ExtraBold, 22), color = STAGE_FG)
        }
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.EmojiEvents, null, tint = STAGE_MUTED, modifier = Modifier.size(10.dp))
                Text(
                    if (engine.table == null) "전체 최고" else "${engine.table}단 최고",
                    style = suite(FontWeight.Bold, 11), color = STAGE_MUTED,
                )
            }
            Text("${engine.selectedBest}개", style = suite(FontWeight.ExtraBold, 22), color = STAGE_ACCENT)
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
                    contentDescription = if (game.phase == RunnerPhase.PAUSED) "계속 달리기" else "일시정지",
                    tint = STAGE_FG, modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun SetupPanel(engine: RunnerEngine, over: Boolean) {
    val gg = LocalGG.current
    val game = engine.game

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (over) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (game.score > 0 && game.score >= engine.runBest) "나의 최고 기록을 달성했어요!"
                    else "한 번 더, 더 멀리 가볼까요?",
                    style = suite(FontWeight.ExtraBold, 17), color = gg.text, textAlign = TextAlign.Center,
                )
                Text(
                    "${game.score}개를 넘었어요. 정답을 익히면 다음엔 더 멀리!",
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
                    Text("정답을 맞히면 자동으로 점프!", style = suite(FontWeight.ExtraBold, 15), color = gg.text)
                    Text(
                        "보기 3개 중 정답을 골라요. 틀리거나 시간이 지나면 하트가 하나 줄어요.",
                        style = suite(FontWeight.Medium, 13), color = gg.textMuted,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("달리면서 연습할 단", style = suite(FontWeight.ExtraBold, 12), color = gg.textMuted)
            // 전체 + 2~9단 = 9개를 5열로 배치 (웹과 동일)
            val options: List<Int?> = listOf(null) + RunnerEngine.TABLES
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { value ->
                            TableChip(
                                label = if (value == null) "전체" else "${value}단",
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

        GGButton(
            variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG,
            onClick = { engine.start() },
        ) {
            Icon(
                if (over) Icons.Filled.RotateLeft else Icons.Filled.PlayArrow, null,
                modifier = Modifier.size(18.dp),
            )
            Text(if (over) "다시 달리기" else "달리기 시작")
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
        Text("장애물과 남은 시간이 멈춰 있어요.", style = suite(FontWeight.Medium, 13), color = gg.textMuted)
        GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG, onClick = { engine.togglePause() }) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
            Text("이어서 달리기")
        }
        PressableCard(onClick = { engine.pause(); onExit() }) {
            Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                Text("학습 모드로 돌아가기", style = suite(FontWeight.Bold, 13), color = gg.textMuted)
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
        RunnerOutcome.CORRECT -> "정답! 장애물을 넘어요."
        RunnerOutcome.WRONG -> "아쉬워요! ${game.question.a} × ${game.question.b} = $answer"
        RunnerOutcome.MISSED -> "시간이 다 됐어요. ${game.question.a} × ${game.question.b} = $answer"
        null -> "장애물이 오기 전에 정답을 골라요!"
    }
    val timeLabel = when (game.outcome) {
        null -> "%.1f초".format(remaining * Runner.answerWindowMs(game.score) / 1000)
        RunnerOutcome.CORRECT -> "점프!"
        else -> "정답을 기억해요"
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${if (engine.table == null) "전체 구구단" else "${engine.table}단"} · ${game.round + 1}번째 장애물",
                style = suite(FontWeight.Bold, 12), color = gg.textMuted,
            )
            Text(timeLabel, style = suite(FontWeight.Bold, 12), color = gg.textMuted)
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
