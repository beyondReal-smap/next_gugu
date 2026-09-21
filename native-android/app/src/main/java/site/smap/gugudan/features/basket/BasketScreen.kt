package site.smap.gugudan.features.basket

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.ShoppingBasket
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import site.smap.gugudan.core.Basket
import site.smap.gugudan.core.BasketOutcome
import site.smap.gugudan.core.RunnerPhase
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Persistence

// 구구 바구니 화면 (BasketScreen.tsx / iOS BasketView 이식)
// 무대를 좌우로 끌거나 방향 버튼을 눌러 바구니를 옮겨 정답 열매를 받는다.
// 웹의 키보드 조작(← →)은 웹 전용이라 제외했다.

private val HUD_BG = Color(0xFF794124)
private val HUD_FG = Color(0xFFFFF5DB)
private val HUD_MUTED = Color(0xFFFFE1AA)
private val HEART_ON = Color(0xFFFFBD9B)
private val HEART_OFF = Color(0xFFB0866C)
private val PAUSE_BG = Color(0xFFFFF8E9)
private val CONTROL_BG = Color(0xFFF6E8C9)

@Composable
fun BasketScreen(onExit: () -> Unit) {
    val gg = LocalGG.current
    val context = LocalContext.current
    val engine = remember { BasketEngine(Persistence(context.applicationContext)) }
    val game = engine.game

    val ready = game.phase == RunnerPhase.READY
    val over = game.phase == RunnerPhase.OVER
    val paused = game.phase == RunnerPhase.PAUSED
    val playing = !ready && !over

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
                Icon(Icons.Filled.ShoppingBasket, null, Modifier.size(14.dp))
                Text("토끼의 작은 과수원")
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 무대 (HUD + 문제 + 과수원)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(gg.surface)) {
                Hud(engine, playing)
                if (playing) QuestionBar(engine)

                Box(
                    Modifier.fillMaxWidth().aspectRatio(6f / 5f)
                        .pointerInput(game.phase) {
                            if (game.phase != RunnerPhase.RUNNING) return@pointerInput
                            val width = size.width.toFloat()
                            detectDragGestures { change, _ ->
                                engine.drag(change.position.x / width)
                            }
                        }
                        .pointerInput(game.phase) {
                            if (game.phase != RunnerPhase.RUNNING) return@pointerInput
                            val width = size.width.toFloat()
                            detectTapGestures { offset -> engine.drag(offset.x / width) }
                        },
                ) {
                    OrchardScene(game = game)

                    if (paused) {
                        Column(
                            Modifier.fillMaxSize().background(PAUSE_BG.copy(alpha = 0.95f))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("잠깐 쉬어가요", style = suite(FontWeight.ExtraBold, 20), color = HUD_BG)
                            Spacer(Modifier.height(12.dp))
                            GGButton(
                                variant = GGButtonVariant.PRIMARY, size = GGButtonSize.MD,
                                onClick = { engine.togglePause() },
                            ) {
                                Text("이어서 받기")
                            }
                        }
                    }
                }
            }

            if (playing) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MoveButton(-1, !paused, Modifier.weight(1f), engine)
                        MoveButton(1, !paused, Modifier.weight(1f), engine)
                    }
                    Text(
                        "화면을 좌우로 끌거나 방향 버튼을 꾹 눌러요",
                        style = suite(FontWeight.Medium, 11), color = gg.textMuted,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
            } else {
                Column(Modifier.fillMaxWidth().ggCard(20.dp).padding(16.dp)) {
                    SetupPanel(engine, over)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Hud(engine: BasketEngine, playing: Boolean) {
    val game = engine.game
    Row(
        Modifier.fillMaxWidth().background(HUD_BG).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ShoppingBasket, null, tint = HUD_FG, modifier = Modifier.size(18.dp))
            Text("${game.score}개", style = suite(FontWeight.ExtraBold, 16), color = HUD_FG)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.EmojiEvents, null, tint = HUD_MUTED, modifier = Modifier.size(13.dp))
            Text("${engine.displayBest}", style = suite(FontWeight.Bold, 13), color = HUD_MUTED)
        }
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.semantics { contentDescription = "남은 하트 ${game.lives}개" },
        ) {
            for (heart in 1..Basket.MAX_LIVES) {
                Icon(
                    if (heart <= game.lives) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    null,
                    tint = if (heart <= game.lives) HEART_ON else HEART_OFF,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (playing) {
            PressableCard(onClick = { engine.togglePause() }) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (game.phase == RunnerPhase.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (game.phase == RunnerPhase.PAUSED) "게임 계속하기" else "일시정지",
                        tint = HUD_FG, modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuestionBar(engine: BasketEngine) {
    val gg = LocalGG.current
    val game = engine.game
    val answer = game.question.answer
    val feedback = when (game.outcome) {
        BasketOutcome.CORRECT ->
            if (game.combo >= 3) "${game.combo}연속! 별이 반짝반짝!" else "쏙! 정답 열매를 받았어요!"
        BasketOutcome.WRONG -> "괜찮아요! ${game.question.a} × ${game.question.b} = $answer"
        BasketOutcome.MISSED -> "다음엔 받아봐요! 정답은 $answer"
        null -> "정답 열매 아래로 바구니를 옮겨요!"
    }

    Column(
        Modifier.fillMaxWidth().background(gg.surface).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "${if (game.table == null) "전체 구구단" else "${game.table}단"} · ${Basket.level(game.score)}단계",
            style = suite(FontWeight.Bold, 12), color = gg.textMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${game.question.a}", style = suite(FontWeight.ExtraBold, 28), color = gg.text)
            Text("×", style = suite(FontWeight.ExtraBold, 28), color = gg.textMuted)
            Text("${game.question.b}", style = suite(FontWeight.ExtraBold, 28), color = gg.text)
            Text("=", style = suite(FontWeight.ExtraBold, 28), color = gg.textMuted)
            Text(
                if (game.outcome != null) "$answer" else "?",
                style = suite(FontWeight.ExtraBold, 28),
                color = if (game.outcome == BasketOutcome.CORRECT) gg.success else gg.textMuted,
            )
        }
        Text(
            feedback, style = suite(FontWeight.Bold, 12),
            color = if (game.outcome == BasketOutcome.WRONG || game.outcome == BasketOutcome.MISSED)
                gg.warning else gg.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** 누르고 있는 동안 계속 움직이고, 손을 떼면 멈춘다 */
@Composable
private fun MoveButton(direction: Int, enabled: Boolean, modifier: Modifier, engine: BasketEngine) {
    Box(
        modifier.height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CONTROL_BG)
            .alpha(if (enabled) 1f else 0.4f)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        engine.direction = direction
                        engine.nudge(direction)
                        Haptics.selection()
                        tryAwaitRelease()
                        engine.direction = 0
                    },
                )
            }
            .semantics { contentDescription = if (direction < 0) "바구니 왼쪽으로" else "바구니 오른쪽으로" },
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (direction < 0) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                null, tint = HUD_BG, modifier = Modifier.size(22.dp),
            )
            Text(if (direction < 0) "왼쪽" else "오른쪽", style = suite(FontWeight.ExtraBold, 15), color = HUD_BG)
        }
    }
}

@Composable
private fun SetupPanel(engine: BasketEngine, over: Boolean) {
    val gg = LocalGG.current
    val game = engine.game

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (over) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("열매 ${game.score}개를 모았어요!", style = suite(FontWeight.ExtraBold, 19), color = gg.text)
                Text(
                    "최고 ${game.maxCombo}연속 정답 · " +
                        if (game.score > 0 && game.score >= engine.runBest) "나의 최고 기록이에요!"
                        else "토끼와 다시 놀아볼까요?",
                    style = suite(FontWeight.Medium, 13), color = gg.textMuted, textAlign = TextAlign.Center,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("정답 열매 아래로, 쏙!", style = suite(FontWeight.ExtraBold, 15), color = gg.text)
                Text(
                    "바구니를 직접 움직여 정답 열매를 받아요. 다른 열매를 받거나 놓치면 하트가 하나 줄어요. 처음에는 2단부터 천천히!",
                    style = suite(FontWeight.Medium, 13), color = gg.textMuted,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("연습할 단", style = suite(FontWeight.ExtraBold, 12), color = gg.textMuted)
            val options: List<Int?> = listOf(null) + BasketEngine.TABLES
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
            Text(if (over) "다시 받기" else "열매 받기 시작")
        }

        Text(
            "하트 3개 · 정답 3개마다 조금씩 빨라져요. 단별 최고 기록은 이 기기에 저장돼요.",
            style = suite(FontWeight.Medium, 12), color = gg.textMuted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TableChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val gg = LocalGG.current
    PressableCard(modifier = modifier, onClick = onClick) {
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
                .background(if (selected) HUD_BG else gg.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label, style = suite(FontWeight.ExtraBold, 13),
                color = if (selected) HUD_FG else gg.text,
            )
        }
    }
}
