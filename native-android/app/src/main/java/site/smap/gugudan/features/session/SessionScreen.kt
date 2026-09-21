package site.smap.gugudan.features.session

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalFireDepartment
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
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.ModeKind
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.features.home.GaugeBar
import site.smap.gugudan.store.LocalGame

// 세션 화면 (iOS SessionView.swift 이식) — 6개 모드 공용

@Composable
fun SessionScreen(mode: GameMode, table: Int?, onExit: () -> Unit) {
    val game = LocalGame.current
    val gg = LocalGG.current
    val engine = remember(mode, table) { SessionEngine(mode, table, game).also { it.start() } }

    DisposableEffect(engine) { onDispose { engine.teardown() } }

    Box(Modifier.fillMaxSize().background(gg.bg)) {
        val done = engine.done
        if (done != null) {
            ResultView(engine, done, onExit)
        } else {
            SessionPlayView(engine, onExit)
        }
    }
}

// MARK: 플레이 화면

@Composable
private fun SessionPlayView(engine: SessionEngine, onExit: () -> Unit) {
    val gg = LocalGG.current

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp),
    ) {
        // 상단 바 — 모드별 진행 위젯
        Row(
            Modifier.fillMaxWidth().height(28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressableCard(onClick = onExit) {
                Icon(Icons.Filled.Close, "세션 종료", tint = gg.textMuted, modifier = Modifier.size(22.dp))
            }
            when (engine.modeKind) {
                ModeKind.FIXED -> {
                    Box(Modifier.weight(1f)) {
                        GaugeBar(engine.idx.toDouble() / maxOf(1, engine.total))
                    }
                    if (engine.mode == GameMode.TIME_ATTACK) {
                        SpeedTimer(engine.sessionStartClock)
                    } else {
                        Text("${engine.idx + 1}/${engine.total}",
                            style = suite(FontWeight.Bold, 14), color = gg.textMuted)
                    }
                }
                ModeKind.TIMED -> {
                    engine.timeLimitMs?.let { limit ->
                        CountdownTimer(engine.sessionStartClock, limit, Modifier.weight(1f)) { engine.expire() }
                    }
                }
                ModeKind.LIVES -> {
                    Spacer(Modifier.weight(1f))
                    HeartsView(engine.lives, engine.maxLives)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // 점수(무제한) + 콤보
        Box(Modifier.fillMaxWidth().height(28.dp)) {
            if (engine.modeKind != ModeKind.FIXED) {
                Row(
                    Modifier.align(Alignment.CenterStart).clip(CircleShape)
                        .background(gg.surface2).padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Check, null, tint = gg.success, modifier = Modifier.size(13.dp))
                    Text("${engine.score}", style = suite(FontWeight.ExtraBold, 14), color = gg.text)
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = engine.combo >= 2,
                modifier = Modifier.align(Alignment.Center),
                enter = scaleIn() + fadeIn(), exit = fadeOut(),
            ) {
                Pill(bg = gg.danger.copy(alpha = 0.15f), fg = gg.danger) {
                    Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(14.dp))
                    Text("${engine.combo} 콤보")
                }
            }
        }

        // 문제 — Equation은 항상 세로 중앙에 고정. 오답 설명은 그 아래에 오버레이로 띄워
        // 레이아웃을 밀지 않게 한다(정답/오답 시 문제가 위로 점프하던 현상 제거).
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Equation(engine)
            if (engine.feedback == AnswerFeedback.WRONG) {
                Text(
                    wrongExplanation(engine),
                    style = suite(FontWeight.Bold, 14),
                    color = gg.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).offset(y = 56.dp)
                        .padding(horizontal = 16.dp),
                )
            }
        }

        // 입력 패드
        if (engine.mode == GameMode.TRUEFALSE) {
            OxPad { engine.handleOX(it) }
        } else {
            Keypad(
                onInput = { engine.handleInput(it) },
                onDelete = { engine.handleDelete() },
                onSubmit = { engine.handleManualSubmit() },
                canSubmit = engine.input.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun Equation(engine: SessionEngine) {
    val gg = LocalGG.current
    val p = engine.problem
    val answer = p.a * p.b
    val showAnswer = engine.feedback == AnswerFeedback.WRONG
    val big = suite(FontWeight.ExtraBold, 52)

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val st = engine.statement
        when {
            engine.mode == GameMode.TRUEFALSE && st != null -> {
                Text("${p.a}", style = big, color = gg.text)
                Text("×", style = big, color = gg.textMuted)
                Text("${p.b}", style = big, color = gg.text)
                Text("=", style = big, color = gg.textMuted)
                Text("${st.shown}", style = big, color = when (engine.feedback) {
                    AnswerFeedback.CORRECT -> gg.success
                    AnswerFeedback.WRONG -> gg.danger
                    null -> gg.text
                })
            }
            engine.mode == GameMode.MISSING -> {
                Text("${p.a}", style = big, color = gg.text)
                Text("×", style = big, color = gg.textMuted)
                Text(if (showAnswer) "${p.b}" else engine.input.ifEmpty { "?" }, style = big,
                    color = if (showAnswer) gg.success else if (engine.input.isEmpty()) gg.border else gg.accent)
                Text("=", style = big, color = gg.textMuted)
                Text("$answer", style = big, color = gg.text)
            }
            else -> {
                Text("${p.a}", style = big, color = gg.text)
                Text("×", style = big, color = gg.textMuted)
                Text("${p.b}", style = big, color = gg.text)
                Text("=", style = big, color = gg.textMuted)
                Text(if (showAnswer) "$answer" else engine.input.ifEmpty { "?" }, style = big,
                    color = if (showAnswer) gg.success else if (engine.input.isEmpty()) gg.border else gg.accent)
            }
        }
    }
}

private fun wrongExplanation(engine: SessionEngine): String {
    val p = engine.problem
    val answer = p.a * p.b
    val st = engine.statement
    return when {
        engine.mode == GameMode.TRUEFALSE && st != null ->
            if (st.isTrue) "맞는 식이었어요 — ${p.a} × ${p.b} = $answer"
            else "${p.a} × ${p.b} = $answer — 틀린 식이에요"
        engine.mode == GameMode.MISSING -> "빈칸은 ${p.b} — ${p.a} × ${p.b} = $answer"
        else -> "정답은 $answer 이에요"
    }
}

// MARK: 진행 위젯

@Composable
private fun SpeedTimer(startClock: Long) {
    val gg = LocalGG.current
    var elapsed by remember { mutableStateOf(0L) }
    LaunchedEffect(startClock) {
        while (true) { elapsed = SystemClock.elapsedRealtime() - startClock; delay(100) }
    }
    Text("%.1fs".format(elapsed / 1000.0), style = suite(FontWeight.Bold, 14), color = gg.textMuted)
}

@Composable
private fun CountdownTimer(startClock: Long, limitMs: Int, modifier: Modifier = Modifier, onExpire: () -> Unit) {
    val gg = LocalGG.current
    var left by remember { mutableStateOf(limitMs.toLong()) }
    LaunchedEffect(startClock) {
        while (left > 0) {
            left = maxOf(0, limitMs - (SystemClock.elapsedRealtime() - startClock))
            delay(100)
        }
        onExpire()
    }
    val sec = ((left + 999) / 1000).toInt()
    val urgent = sec <= 10
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            GaugeBar(left.toDouble() / limitMs, color = if (urgent) gg.danger else gg.accent)
        }
        Text("${sec}s", style = suite(FontWeight.Bold, 14), color = if (urgent) gg.danger else gg.textMuted)
    }
}

@Composable
private fun HeartsView(lives: Int, max: Int) {
    val gg = LocalGG.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(max) { i ->
            val alive = i < lives
            Icon(
                if (alive) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                null,
                tint = if (alive) gg.danger else gg.textMuted.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// MARK: 키패드 (iOS Keypad.swift 이식)

@Composable
fun Keypad(onInput: (Int) -> Unit, onDelete: () -> Unit, onSubmit: () -> Unit, canSubmit: Boolean) {
    val gg = LocalGG.current

    @Composable
    fun key(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
        PressableCard(modifier = modifier, onClick = onClick) {
            Box(
                Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).background(gg.surface2),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { n ->
                    key(Modifier.weight(1f), { onInput(n) }) {
                        Text("$n", style = suite(FontWeight.Bold, 24), color = gg.text)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            key(Modifier.weight(1f), onDelete) {
                Icon(Icons.AutoMirrored.Filled.Backspace, "지우기", tint = gg.textMuted, modifier = Modifier.size(22.dp))
            }
            key(Modifier.weight(1f), { onInput(0) }) {
                Text("0", style = suite(FontWeight.Bold, 24), color = gg.text)
            }
            PressableCard(modifier = Modifier.weight(1f), enabled = canSubmit, onClick = onSubmit) {
                Box(
                    Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (canSubmit) gg.accent else gg.accent.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("확인", style = suite(FontWeight.Bold, 18), color = gg.accentFg)
                }
            }
        }
    }
}

// MARK: OX 패드 (iOS OxPad.swift 이식)

@Composable
fun OxPad(onAnswer: (Boolean) -> Unit) {
    val gg = LocalGG.current

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PressableCard(modifier = Modifier.weight(1f), onClick = { onAnswer(true) }) {
            Column(
                Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(16.dp))
                    .background(gg.success.copy(alpha = 0.12f)),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(44.dp).border(5.dp, gg.success, CircleShape))
                Text("맞아요", style = suite(FontWeight.ExtraBold, 14), color = gg.success)
            }
        }
        PressableCard(modifier = Modifier.weight(1f), onClick = { onAnswer(false) }) {
            Column(
                Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(16.dp))
                    .background(gg.danger.copy(alpha = 0.12f)),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Close, null, tint = gg.danger, modifier = Modifier.size(48.dp))
                Text("아니에요", style = suite(FontWeight.ExtraBold, 14), color = gg.danger)
            }
        }
    }
}
