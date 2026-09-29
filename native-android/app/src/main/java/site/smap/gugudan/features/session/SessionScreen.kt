package site.smap.gugudan.features.session

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.KoreanReading
import site.smap.gugudan.core.VoiceLines
import site.smap.gugudan.core.ModeKind
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.features.home.GaugeBar
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Speech
import site.smap.gugudan.store.LocalAuth
import site.smap.gugudan.store.LocalGame
import site.smap.gugudan.store.LocalPrefs
import site.smap.gugudan.store.LocalSync

// 세션 화면 (iOS SessionView.swift 이식) — 6개 모드 공용

@Composable
fun SessionScreen(mode: GameMode, table: Int?, review: Boolean = false, onExit: () -> Unit) {
    val game = LocalGame.current
    val gg = LocalGG.current
    val sync = LocalSync.current
    val auth = LocalAuth.current
    val engine = remember(mode, table, review) {
        // 학습 원장에 적재하고 바로 올려 본다.
        // 보호자 검증 전이면 flush 가 요청 없이 큐에 남긴다.
        SessionEngine(mode, table, game, review) { result ->
            sync.record(result)
            sync.flush(auth)
        }.also { it.start() }
    }

    DisposableEffect(engine) {
        onDispose {
            engine.teardown()
            Speech.stop()
        }
    }

    Box(Modifier.fillMaxSize().background(gg.bg)) {
        val done = engine.done
        if (done != null) {
            // 결과 화면에서 뒤로가기 = 닫기
            BackHandler(onBack = onExit)
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
    val prefs = LocalPrefs.current
    var confirmExit by remember { mutableStateOf(false) }
    /** 마지막으로 소리 내어 읽은 문제 번호 — 확인 시트 동안 넘어간 문제를 재개할 때 읽기 위해서다 */
    var readSerial by remember { mutableStateOf(-1) }

    fun readQuestion() {
        // 확인 시트가 떠 있는 동안 넘어온 문제는 시트 뒤에서 읽지 않는다 — 재개할 때 읽는다
        if (!prefs.readAloud || engine.pausedAt != null) return
        readSerial = engine.problemSerial
        Speech.speak(VoiceLines.question(engine.problem, engine.mode, engine.statement))
    }
    LaunchedEffect(engine.problemSerial) { readQuestion() }

    // 닫기 — 푼 문제가 있으면 시간을 멈추고 한 번 묻는다. 없으면 잃을 것이 없으니 바로 닫는다.
    fun requestExit() {
        if (engine.answeredCount == 0) {
            engine.abandon()
            onExit()
            return
        }
        engine.pause()
        Speech.stop()
        confirmExit = true
    }
    // 시스템 뒤로가기도 닫기 버튼과 같다 (확인 시트가 떠 있으면 시트가 먼저 받는다)
    BackHandler(enabled = !confirmExit) { requestExit() }

    // 게임 화면은 키패드·식 배치가 고정이라 아주 큰 글꼴에서는 더 키우지 않는다
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, 1.3f))) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize()
                    // 확인 시트가 떠 있는 동안 뒤 화면을 스크린리더에서 비운다 — 초점이 시트 밖으로 새지 않게(모달)
                    .then(if (confirmExit) Modifier.clearAndSetSemantics {} else Modifier)
                    .statusBarsPadding().navigationBarsPadding()
                    .readableWidth(560.dp)
                    .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp),
            ) {
                // 상단 바 — 모드별 진행 위젯
                Row(
                    Modifier.fillMaxWidth().height(44.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PressableCard(onClick = { requestExit() }) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "그만하기", tint = gg.textMuted, modifier = Modifier.size(22.dp))
                        }
                    }
                    when (engine.modeKind) {
                        ModeKind.FIXED -> {
                            Box(Modifier.weight(1f).clearAndSetSemantics { }) {
                                GaugeBar(engine.idx.toDouble() / maxOf(1, engine.total))
                            }
                            if (engine.mode == GameMode.TIME_ATTACK) {
                                SpeedTimer(engine)
                            } else {
                                Text("${engine.idx + 1}/${engine.total}",
                                    style = suite(FontWeight.Bold, 14), color = gg.textMuted,
                                    modifier = Modifier.semantics {
                                        contentDescription = "${engine.total}문제 중 ${engine.idx + 1}번째"
                                    })
                            }
                        }
                        ModeKind.TIMED -> {
                            engine.timeLimitMs?.let { limit ->
                                CountdownTimer(engine, limit, Modifier.weight(1f))
                            }
                        }
                        ModeKind.LIVES -> {
                            Spacer(Modifier.weight(1f))
                            HeartsView(engine.lives, engine.maxLives)
                        }
                    }
                    // 문제 읽어주기 — 켜면 지금 문제부터 바로 읽는다
                    PressableCard(onClick = {
                        prefs.updateReadAloud(!prefs.readAloud)
                        readQuestion()
                    }) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                if (prefs.readAloud) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                if (prefs.readAloud) "문제 읽어주기 끄기" else "문제 읽어주기 켜기",
                                tint = if (prefs.readAloud) gg.accent else gg.textMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // 점수(무제한) + 콤보
                Box(Modifier.fillMaxWidth().height(28.dp)) {
                    if (engine.modeKind != ModeKind.FIXED) {
                        Row(
                            Modifier.align(Alignment.CenterStart).clip(CircleShape)
                                .background(gg.surface2).padding(horizontal = 10.dp, vertical = 5.dp)
                                .clearAndSetSemantics { contentDescription = "맞힌 문제 ${engine.score}개" },
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

                // 문제 — 식 위(정답/오답 표시)와 아래(오답 설명)에 같은 높이의 자리를 항상 예약해
                // 식이 영역 한가운데에서 움직이지 않게 한다. 코치 힌트는 영역 바닥에 겹쳐 띄운다.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        Modifier.align(Alignment.Center).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.height(52.dp), contentAlignment = Alignment.BottomCenter) {
                            androidx.compose.animation.AnimatedVisibility(engine.feedback != null, enter = fadeIn(), exit = fadeOut()) {
                                val correct = engine.feedback == AnswerFeedback.CORRECT
                                Icon(
                                    if (correct) Icons.Filled.CheckCircle else Icons.Filled.Cancel, null,
                                    tint = if (correct) gg.success else gg.danger,
                                    modifier = Modifier.padding(bottom = 12.dp).size(32.dp),
                                )
                            }
                        }
                        Equation(engine)
                        Box(Modifier.height(52.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            if (engine.feedback == AnswerFeedback.WRONG) {
                                Text(
                                    wrongExplanation(engine),
                                    style = suite(FontWeight.Bold, 15),
                                    color = gg.textMuted,
                                    textAlign = TextAlign.Center,
                                    // 식의 상태 설명("오답. …")에 이미 들어 있다 — 두 번 읽히지 않게 숨긴다
                                    modifier = Modifier.padding(top = 10.dp, start = 16.dp, end = 16.dp)
                                        .clearAndSetSemantics {},
                                )
                            }
                        }
                    }
                    val hint = engine.coachHint
                    if (hint != null && engine.feedback == null) {
                        Row(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(gg.warning.copy(alpha = 0.12f))
                                .border(1.dp, gg.warning.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .clearAndSetSemantics { contentDescription = "힌트, ${hint.title}, ${hint.scaffold}" },
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Lightbulb, null, tint = gg.warning, modifier = Modifier.size(18.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(hint.title, style = suite(FontWeight.ExtraBold, 13), color = gg.text)
                                Text(hint.scaffold, style = suite(FontWeight.Bold, 15), color = gg.textMuted)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

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

            ConfirmSheet(
                visible = confirmExit,
                title = "그만할까요?",
                message = "지금까지 푼 ${engine.answeredCount}문제는 기록돼요.",
                cancelLabel = "계속하기",
                confirmLabel = "그만하기",
                onCancel = {
                    confirmExit = false
                    engine.resume()
                    // 확인하는 사이에 다음 문제로 넘어갔으면 그 문제를 이제 읽어 준다
                    if (readSerial != engine.problemSerial) readQuestion()
                },
                onConfirm = {
                    engine.abandon()
                    onExit()
                },
            )
        }
    }
}

/** 식 — 색이 다른 조각을 하나의 Text 로 이어 붙여 한 덩어리로 배치한다 */
@Composable
private fun Equation(engine: SessionEngine) {
    val gg = LocalGG.current
    val p = engine.problem
    val answer = p.a * p.b
    val showAnswer = engine.feedback == AnswerFeedback.WRONG
    // 정답이면 입력한 수를 초록으로 — 위치는 그대로 두고 색으로만 알린다
    val inputColor = when {
        engine.feedback == AnswerFeedback.CORRECT -> gg.success
        engine.input.isEmpty() -> gg.border
        else -> gg.accent
    }
    val slot = engine.input.ifEmpty { "?" }
    val st = engine.statement

    val text = buildAnnotatedString {
        fun num(s: String, c: Color) = withStyle(SpanStyle(color = c)) { append(s) }
        fun op(s: String) = withStyle(SpanStyle(color = gg.textMuted)) { append(s) }
        when {
            engine.mode == GameMode.TRUEFALSE && st != null -> {
                num("${p.a}", gg.text); op(" × "); num("${p.b}", gg.text); op(" = ")
                num("${st.shown}", when (engine.feedback) {
                    AnswerFeedback.CORRECT -> gg.success
                    AnswerFeedback.WRONG -> gg.danger
                    null -> gg.text
                })
            }
            engine.mode == GameMode.MISSING -> {
                num("${p.a}", gg.text); op(" × ")
                num(if (showAnswer) "${p.b}" else slot, if (showAnswer) gg.success else inputColor)
                op(" = "); num("$answer", gg.text)
            }
            else -> {
                num("${p.a}", gg.text); op(" × "); num("${p.b}", gg.text); op(" = ")
                num(if (showAnswer) "$answer" else slot, if (showAnswer) gg.success else inputColor)
            }
        }
    }
    val stateText = when (engine.feedback) {
        AnswerFeedback.CORRECT -> "정답"
        AnswerFeedback.WRONG -> "오답. ${wrongExplanation(engine)}"
        null -> if (engine.input.isEmpty()) "" else "입력 ${engine.input}"
    }
    Text(
        text,
        style = suite(FontWeight.ExtraBold, 52),
        maxLines = 1, softWrap = false,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = KoreanReading.question(p, engine.mode, st)
            stateDescription = stateText
        },
    )
}

private fun wrongExplanation(engine: SessionEngine): String {
    val p = engine.problem
    val answer = p.a * p.b
    val st = engine.statement
    if (engine.mode == GameMode.TRUEFALSE && st != null) {
        return if (st.isTrue) "맞는 식이었어요 — ${p.a} × ${p.b} = $answer"
        else "${p.a} × ${p.b} = $answer — 틀린 식이에요"
    }
    val expected = if (engine.mode == GameMode.MISSING) p.b else answer
    return engine.lastGiven?.let { KoreanReading.notButIs(it, expected) }
        ?: "정답은 ${KoreanReading.withCopula(expected)}"
}

// MARK: 진행 위젯

@Composable
private fun SpeedTimer(engine: SessionEngine) {
    val gg = LocalGG.current
    var elapsed by remember { mutableStateOf(0L) }
    LaunchedEffect(engine) {
        while (true) { elapsed = engine.elapsedMs(); delay(100) }
    }
    Text("%.1fs".format(elapsed / 1000.0), style = suite(FontWeight.Bold, 14), color = gg.textMuted)
}

@Composable
private fun CountdownTimer(engine: SessionEngine, limitMs: Int, modifier: Modifier = Modifier) {
    val gg = LocalGG.current
    var left by remember { mutableStateOf(limitMs.toLong()) }
    LaunchedEffect(engine) {
        // 만료 판정은 엔진이 한다 (일시정지 중에는 표시만 멈춘다)
        while (true) {
            left = maxOf(0L, limitMs - engine.elapsedMs())
            delay(100)
        }
    }
    val sec = ((left + 999) / 1000).toInt()
    val urgent = sec <= 10
    Row(
        modifier.clearAndSetSemantics { contentDescription = "남은 시간 ${sec}초" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            GaugeBar(left.toDouble() / limitMs, color = if (urgent) gg.danger else gg.accent)
        }
        Text("${sec}s", style = suite(FontWeight.Bold, 14), color = if (urgent) gg.danger else gg.textMuted)
    }
}

@Composable
private fun HeartsView(lives: Int, max: Int) {
    val gg = LocalGG.current
    Row(
        Modifier.clearAndSetSemantics { contentDescription = "남은 하트 ${lives}개" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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
        // PressableCard 가 가벼운 탭 진동을 내장한다
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
            PressableCard(
                modifier = Modifier.weight(1f),
                enabled = canSubmit,
                onClick = { Haptics.impactMedium(); onSubmit() },
            ) {
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
        PressableCard(
            modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = "맞아요, 맞는 식" },
            onClick = { onAnswer(true) },
        ) {
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
        PressableCard(
            modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = "아니에요, 틀린 식" },
            onClick = { onAnswer(false) },
        ) {
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
