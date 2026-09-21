package site.smap.gugudan.features.lanerunner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import site.smap.gugudan.core.LaneOutcome
import site.smap.gugudan.core.LaneRunner
import site.smap.gugudan.core.RunnerPhase
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Persistence
import kotlin.math.abs

// 구구 레인 화면 (LaneRunnerScreen.tsx / iOS LaneRunnerView 이식)
// 위아래로 길을 옮겨 장애물을 피하고, 문제가 나오면 정답 숫자가 적힌 길로 들어간다.
// 웹의 키보드 조작(↑↓·1~3·Space·Esc)은 웹 전용이라 제외하고, 스와이프와 ▲▼ 버튼만 제공한다.

private val STAGE_BG = Color(0xFF153F35)
private val STAGE_FG = Color(0xFFF3F6E8)
private val STAGE_MUTED = Color(0xFFC1D4C2)
private val STAGE_ACCENT = Color(0xFFDBEF9E)
private val OVERLAY_BG = Color(0xFFEFF3DF)
private val OVERLAY_TEXT = Color(0xFF153F35)
private val OVERLAY_MUTED = Color(0xFF486146)
private val HEART_ON = Color(0xFFF3A88C)
private val HEART_OFF = Color(0xFF6C897B)

private val LANE_NAMES = listOf("위", "가운데", "아래")

/** 스와이프로 인정하는 세로 이동 거리(px) */
private const val SWIPE_PX = 28f

@Composable
fun LaneRunnerScreen(onExit: () -> Unit) {
    val gg = LocalGG.current
    val context = LocalContext.current
    val engine = remember { LaneRunnerEngine(Persistence(context.applicationContext)) }
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) engine.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().background(gg.bg).statusBarsPadding()) {
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
                Icon(Icons.Filled.ViewStream, null, Modifier.size(14.dp))
                Text("길을 바꾸며 배우는 구구단")
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 무대 (점수판 + 씬)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(STAGE_BG)) {
                Scoreboard(engine, enabled = !ready && !over)

                Box(
                    Modifier.fillMaxWidth().aspectRatio(8f / 5f)
                        .pointerInput(active) {
                            if (!active) return@pointerInput
                            // 한 번의 제스처는 한 칸만 이동한다
                            var moved = 0f
                            var handled = false
                            detectVerticalDragGestures(
                                onDragStart = { moved = 0f; handled = false },
                                onDragEnd = { moved = 0f; handled = false },
                                onDragCancel = { moved = 0f; handled = false },
                            ) { _, dragAmount ->
                                if (handled) return@detectVerticalDragGestures
                                moved += dragAmount
                                if (abs(moved) < SWIPE_PX) return@detectVerticalDragGestures
                                handled = true
                                engine.move(if (moved < 0) -1 else 1)
                            }
                        },
                ) {
                    LaneScene(game = game)

                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            if (ready) "오늘의 작은 모험" else "${game.distance.toInt()}m 달리는 중",
                            style = suite(FontWeight.ExtraBold, 11), color = OVERLAY_MUTED,
                        )
                        Text(
                            if (game.combo >= 2) "${game.combo}연속 정답 길! · 속도 ${LaneRunner.level(game.score) + 1}단계"
                            else "속도 ${LaneRunner.level(game.score) + 1}단계",
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
                                    else "정답 길 ${game.score}개 · 피한 장애물 ${game.dodged}개 · 최고 ${game.maxCombo}연속",
                                    style = suite(FontWeight.Bold, 13), color = OVERLAY_MUTED,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().ggCard(20.dp).padding(16.dp)) {
                when {
                    ready || over -> SetupPanel(engine, over)
                    paused -> PausePanel(engine, onExit)
                    else -> PlayPanel(engine, active)
                }
            }

            Column(Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "화면을 위아래로 밀거나 ▲▼ 버튼으로 길을 옮겨요.",
                    style = suite(FontWeight.Medium, 12), color = gg.textMuted,
                )
                Text(
                    "정답 길 ${LaneRunner.SCORE_PER_LEVEL}개를 지날 때마다 속도가 한 단계 빨라져요. 앱을 벗어나면 자동으로 일시정지해요.",
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
private fun Scoreboard(engine: LaneRunnerEngine, enabled: Boolean) {
    val game = engine.game
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("통과한 정답 길", style = suite(FontWeight.Bold, 11), color = STAGE_MUTED)
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
            for (heart in 1..LaneRunner.MAX_LIVES) {
                Icon(
                    if (heart <= game.lives) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    null,
                    tint = if (heart <= game.lives) HEART_ON else HEART_OFF,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        PressableCard(enabled = enabled, onClick = { engine.togglePause() }) {
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
private fun SetupPanel(engine: LaneRunnerEngine, over: Boolean) {
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
                    "정답 길을 ${game.score}개 지났어요. 정답을 익히면 다음엔 더 멀리!",
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
                    Icon(Icons.Filled.ViewStream, null, tint = GGColors.emerald, modifier = Modifier.size(20.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("길을 직접 바꾸며 달려요!", style = suite(FontWeight.ExtraBold, 15), color = gg.text)
                    Text(
                        "위아래로 길을 옮겨 장애물을 피하고, 문제가 나오면 정답 숫자가 적힌 길로 들어가요. 부딪히거나 틀리면 하트가 하나 줄어요.",
                        style = suite(FontWeight.Medium, 13), color = gg.textMuted,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("달리면서 연습할 단", style = suite(FontWeight.ExtraBold, 12), color = gg.textMuted)
            // 전체 + 2~9단 = 9개를 5열로 배치 (웹과 동일)
            val options: List<Int?> = listOf(null) + LaneRunnerEngine.TABLES
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
                        repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG, onClick = { engine.start() }) {
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
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
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
private fun PausePanel(engine: LaneRunnerEngine, onExit: () -> Unit) {
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
private fun PlayPanel(engine: LaneRunnerEngine, active: Boolean) {
    val gg = LocalGG.current
    val game = engine.game
    val question = game.question
    val gate = game.gate
    // 판정 뒤에도 게이트가 화면을 벗어날 때까지는 문제와 결과를 보여 준다
    val quiz = question != null && gate != null
    val reveal = game.outcome == LaneOutcome.CORRECT || game.outcome == LaneOutcome.WRONG
    val answer = question?.answer

    val remaining = if (gate != null && gate.startX > LaneRunner.JUDGE_X) {
        ((gate.x - LaneRunner.JUDGE_X) / (gate.startX - LaneRunner.JUDGE_X)).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
    val statusLabel = when {
        !quiz || gate == null -> "피한 장애물 ${game.dodged}개"
        reveal -> if (game.outcome == LaneOutcome.CORRECT) "통과!" else "정답을 기억해요"
        else -> "%.1f초".format(
            (gate.x - LaneRunner.JUDGE_X).coerceAtLeast(0.0) / LaneRunner.speed(game.score) / 1000,
        )
    }
    val feedback = when (game.outcome) {
        LaneOutcome.CORRECT ->
            if (LaneRunner.leveledUp(game)) "정답! 이제 속도 ${LaneRunner.level(game.score) + 1}단계로 빨라져요."
            else "정답! $answer 길로 통과했어요."
        LaneOutcome.WRONG ->
            if (question != null) "아쉬워요! ${question.a} × ${question.b} = ${question.answer}" else "아쉬워요!"
        LaneOutcome.HIT -> "쿵! 장애물에 부딪혔어요."
        null -> if (quiz) "정답 숫자가 있는 길로 옮겨요!" else "위아래로 길을 옮겨 장애물을 피해요."
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${if (engine.table == null) "전체 구구단" else "${engine.table}단"} · ${if (quiz) "정답 길 찾기" else "장애물 피하기"}",
                style = suite(FontWeight.Bold, 12), color = gg.textMuted,
            )
            Text(statusLabel, style = suite(FontWeight.Bold, 12), color = gg.textMuted)
        }

        // 게이트가 판정선에 닿기까지 남은 비율
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(gg.surface2)) {
            Box(
                Modifier.fillMaxWidth(remaining).fillMaxSize()
                    .background(if (remaining < 0.25f && !reveal) gg.warning else GGColors.emerald)
            )
        }

        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (quiz && question != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${question.a}", style = suite(FontWeight.ExtraBold, 34), color = gg.text)
                    Text("×", style = suite(FontWeight.ExtraBold, 34), color = gg.textMuted)
                    Text("${question.b}", style = suite(FontWeight.ExtraBold, 34), color = gg.text)
                    Text("=", style = suite(FontWeight.ExtraBold, 34), color = gg.textMuted)
                    Text(
                        if (reveal) "${question.answer}" else "?",
                        style = suite(FontWeight.ExtraBold, 34),
                        color = if (game.outcome == LaneOutcome.CORRECT) gg.success else gg.textMuted,
                    )
                }
            } else {
                Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                    Text("장애물을 피해요!", style = suite(FontWeight.ExtraBold, 20), color = gg.text)
                }
            }
            Text(
                feedback, style = suite(FontWeight.Bold, 13),
                color = if (game.outcome == LaneOutcome.WRONG || game.outcome == LaneOutcome.HIT)
                    gg.warning else gg.textMuted,
                textAlign = TextAlign.Center,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (lane in 0..2) {
                    LaneRow(
                        name = LANE_NAMES[lane],
                        value = gate?.values?.get(lane),
                        current = lane == game.lane,
                        correct = reveal && gate?.values?.get(lane) == answer,
                        wrong = reveal && gate?.values?.get(lane) == game.given &&
                            gate?.values?.get(lane) != answer,
                        dimmed = reveal,
                    )
                }
            }
            Column(Modifier.width(80.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MoveButton(up = true, enabled = active && game.lane > 0) { engine.move(-1) }
                MoveButton(up = false, enabled = active && game.lane < 2) { engine.move(1) }
            }
        }
    }
}

@Composable
private fun LaneRow(
    name: String, value: Int?, current: Boolean, correct: Boolean, wrong: Boolean, dimmed: Boolean,
) {
    val gg = LocalGG.current
    val border = when {
        correct -> gg.success
        wrong -> gg.warning
        current -> STAGE_BG
        else -> gg.border
    }
    val fill = when {
        correct -> gg.success.copy(alpha = 0.1f)
        wrong -> gg.warning.copy(alpha = 0.1f)
        current -> STAGE_ACCENT.copy(alpha = 0.4f)
        else -> gg.surface
    }

    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .border(2.dp, border, RoundedCornerShape(12.dp))
            .alpha(if (dimmed && !correct && !wrong) 0.4f else 1f)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = suite(FontWeight.ExtraBold, 12), color = gg.textMuted, modifier = Modifier.width(40.dp))
        if (value != null) {
            Text("$value", style = suite(FontWeight.ExtraBold, 22), color = gg.text)
        } else {
            Text("·", style = suite(FontWeight.Bold, 16), color = gg.textMuted)
        }
        Spacer(Modifier.weight(1f))
        if (current) Text("공룡", style = suite(FontWeight.ExtraBold, 11), color = Color(0xFF2B6A4F))
    }
}

@Composable
private fun MoveButton(up: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val gg = LocalGG.current
    PressableCard(enabled = enabled, onClick = onClick) {
        Box(
            Modifier.fillMaxWidth().height(51.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(gg.surface)
                .border(2.dp, gg.border, RoundedCornerShape(16.dp))
                .alpha(if (enabled) 1f else 0.35f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (up) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (up) "위 길로 이동" else "아래 길로 이동",
                tint = gg.text, modifier = Modifier.size(28.dp),
            )
        }
    }
}
