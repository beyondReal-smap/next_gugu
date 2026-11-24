package site.smap.gugudan.features.games.stairs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import site.smap.gugudan.R
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.games.PlayGame
import site.smap.gugudan.core.games.ProblemOrder
import site.smap.gugudan.designsystem.rememberReduceMotion
import site.smap.gugudan.features.games.shared.GameCommit
import site.smap.gugudan.features.games.shared.GameCommitFeedback
import site.smap.gugudan.features.games.shared.GameImages
import site.smap.gugudan.features.games.shared.GamePalette
import site.smap.gugudan.features.games.shared.GamePanelCard
import site.smap.gugudan.features.games.shared.GameResultPanel
import site.smap.gugudan.features.games.shared.GameStartPanel
import site.smap.gugudan.features.games.shared.GameViewport
import site.smap.gugudan.features.games.shared.HudBadgeContent
import site.smap.gugudan.features.games.shared.HudBadgeState
import site.smap.gugudan.features.games.shared.LocalGameImages
import site.smap.gugudan.features.games.shared.PauseButton
import site.smap.gugudan.features.games.shared.PauseLayout
import site.smap.gugudan.features.games.shared.PausePanel
import site.smap.gugudan.features.games.shared.drawBanner
import site.smap.gugudan.features.games.shared.drawCoinFlight
import site.smap.gugudan.features.games.shared.drawComboBadge
import site.smap.gugudan.features.games.shared.drawGameImageFit
import site.smap.gugudan.features.games.shared.drawGameText
import site.smap.gugudan.features.games.shared.drawHudBadge
import site.smap.gugudan.features.games.shared.drawOutlinedText
import site.smap.gugudan.features.games.shared.centeredAt
import site.smap.gugudan.features.games.shared.onTouchDown
import site.smap.gugudan.features.games.shared.rememberGameImages
import site.smap.gugudan.features.games.shared.rememberGameInsets
import site.smap.gugudan.features.games.shared.rememberGameTextMeasurer
import site.smap.gugudan.features.paywall.MinigameTrial
import site.smap.gugudan.features.paywall.TrialEndPanel
import site.smap.gugudan.features.paywall.TrialProgressPill
import site.smap.gugudan.services.DevProps
import site.smap.gugudan.services.rememberStrings
import site.smap.gugudan.store.LocalAuth
import site.smap.gugudan.store.LocalGame
import site.smap.gugudan.store.LocalGameOrders
import site.smap.gugudan.store.LocalPlayRewards
import site.smap.gugudan.store.LocalPremium
import site.smap.gugudan.store.LocalSync
import site.smap.gugudan.store.MinigameStart
import kotlin.math.abs
import kotlin.math.sin

// 구구 계단 화면 (Unity 시험판 A 계단 점프 — StairsGame.BuildHud·PrototypeShell 화면 구성 이식, iOS StairsView.swift 대응)
// 무대(Canvas) 위에 위 배지 줄 [⏸]·계단·먹구름 거리·보물·별, 문제 판, 왼쪽 고도계, 알림·불꽃·코인을 얹고,
// 시작 전 패널·쉬는 창·결과 창(체험이면 체험 종료 패널)을 맨 위에 띄운다.
// 화면 아무 곳이나 닿는 순간 그 칸(왼쪽·가운데·오른쪽) 발판으로 점프 — [⏸] 위는 빼고(엔진), 창이 열리면 창이 입력을 막는다.
// Android 뒤로 가기 = 쉬는 창(StairsEngine.pressBack). 전체 화면(상태 표시줄·내비게이션 바 뒤까지)에 그리고,
// 배지 줄·문제 판은 안전 영역(GameViewport) 안에 둔다.

/** start: 홈·섞어서 = 2~9단 무작위, 단 시트에서 고른 단(Table(n)) = n단부터 */
@Composable
fun StairsScreen(start: MinigameStart, onExit: () -> Unit) {
    val rewards = LocalPlayRewards.current
    val premium = LocalPremium.current
    val strings = rememberStrings()
    val game = LocalGame.current
    val sync = LocalSync.current
    val auth = LocalAuth.current
    val orders = LocalGameOrders.current
    // 동작 줄이기면 반복·장식 움직임을 끈다(판 동안 고정 — 홈 보물상자와 같은 판단)
    val reduceMotion = rememberReduceMotion()
    val engine = remember {
        // 학습 기록 — 진행도(XP·오늘 목표·틀린 문제)에 반영하고, 학습 원장에 적재해 바로 올려 본다.
        // 보호자 검증 전이면 flush 가 요청 없이 큐에 남긴다(학습 세션과 같음)
        val commit: GameCommit = { result ->
            val done = game.commitSession(result)
            sync.record(result)
            sync.flush(auth)
            done
        }
        StairsEngine(
            start, orders.order(PlayGame.STAIRS), rewards, strings, commit, topStep = Problems.maxB,
            autoplay = debugAutoplayCount(), reduceMotion = reduceMotion,
        )
    }
    val images = rememberGameImages()
    // 첫 프레임이 끊기지 않게 무대·배지 그림을 미리 풀어 둔다
    remember(images) { images.preload(StairsScene.IMAGES + HUD_IMAGES) }

    // 나가기 — 판 중간이면 푼 답을 부분 기록하고 닫는다(결과·체험 끝 뒤에는 이미 기록함). 모든 나가는 길이 이 함수를 지난다
    val exit = {
        engine.abandon()
        onExit()
    }
    BackHandler { if (!engine.pressBack()) exit() }
    // 다른 길로 화면이 사라질 때(Activity 재생성 등)도 푼 답을 잃지 않게 — 이미 기록한 판이면 아무것도 안 함
    DisposableEffect(engine) { onDispose { engine.abandon() } }

    // 백그라운드 → 진행 중이면 쉬는 창(루프 멈춤), 돌아오면 루프만 다시(쉬는 창은 아이가 닫음)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> engine.sceneBecameInactive()
                Lifecycle.Event.ON_RESUME -> engine.sceneBecameActive()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 프레임 루프 — 첫 프레임은 시각만 잡고(멈췄던 시간을 흘리지 않게) 그다음부터 실제 간격으로 진행
    val running = engine.wantsFrames
    LaunchedEffect(engine, running) {
        if (!running) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0) engine.advance((now - last) / 1_000_000_000.0)
                last = now
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(StairsSky.SKY_TOP[0].color)) {
        val density = LocalDensity.current
        val size = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val insets = rememberGameInsets()
        val viewport = remember(size, insets) { GameViewport(size, insets) }
        SideEffect { engine.updateViewport(viewport) }

        CompositionLocalProvider(LocalGameImages provides images) {
            StairsPlayfield(
                engine, viewport, images, isPremium = premium.isPremium,
                onOrder = onOrder@{ order ->
                    // 두 손가락으로 [시작]과 같이 눌러 판이 이미 시작됐으면 무시(엔진은 시작 뒤 순서 변경을 버그로 보고 멈춘다)
                    if (engine.phase != StairsEngine.Phase.READY) return@onOrder
                    // 엔진이 뒤의 첫 문제를 새 순서로 다시 만들고, 선택은 다음에 열 때를 위해 기억한다
                    engine.setOrder(order)
                    orders.setOrder(order, PlayGame.STAIRS)
                },
                onExit = exit, onUnlock = { premium.openPaywall() },
            )
        }
    }
}

/** 엔진이 있는 동안의 화면 — 그림은 프레임마다(engine.frame), 창은 상태가 바뀔 때만 다시 구성 */
@Composable
private fun StairsPlayfield(
    engine: StairsEngine,
    viewport: GameViewport,
    images: GameImages,
    isPremium: Boolean,
    /** 시작 전 패널의 [순서대로]·[무작위] */
    onOrder: (ProblemOrder) -> Unit,
    onExit: () -> Unit,
    onUnlock: () -> Unit,
) {
    val measurer = rememberGameTextMeasurer()
    val hint = stringResource(R.string.stairs_hint)
    val caught = stringResource(R.string.stairs_caught)
    Box(Modifier.fillMaxSize()) {
        // 무대 — 손가락이 닿는 순간 그 칸으로(떼기·끌기를 기다리지 않음, 여러 손가락 각각)
        Canvas(Modifier.fillMaxSize().onTouchDown(engine::enqueueTap)) {
            engine.frame   // 프레임마다 다시 그리기
            drawStairsScene(engine.stage, engine.sparkles, engine.session.cloud.top, engine.time, images, measurer)
        }
        // 배지 줄·문제 판·고도계·알림 — 입력은 받지 않음(아래 무대로 통과)
        Canvas(Modifier.fillMaxSize()) {
            engine.frame
            drawStairsHud(engine, viewport, images, measurer, hint, caught)
        }
        val left = engine.trialLeft
        if (left != null && engine.phase == StairsEngine.Phase.PLAYING && !engine.roundOver) {
            Box(Modifier.centeredAt(viewport.safeTop(0f, -610f))) { TrialProgressPill(left) }
        }
        if (engine.pauseButtonVisible) PauseButton(viewport) { engine.openPause() }
        Overlays(engine, viewport, isPremium, onOrder, onExit, onUnlock)
        // 학습 기록 알림(새 업적·레벨 업) — 결과·체험 종료 창 위
        GameCommitFeedback(engine.learning.commit, viewport)
    }
}

@Composable
private fun Overlays(
    engine: StairsEngine,
    viewport: GameViewport,
    isPremium: Boolean,
    onOrder: (ProblemOrder) -> Unit,
    onExit: () -> Unit,
    onUnlock: () -> Unit,
) {
    val result = engine.result
    if (engine.phase == StairsEngine.Phase.READY) {
        GameStartPanel(
            title = stringResource(R.string.stairs_title),
            summary = stringResource(R.string.stairs_summary),
            icon = Icons.Filled.Stairs, isPremium = isPremium,
            order = engine.order, onOrder = onOrder,
            onStart = {
                // 여러 손가락·연타로 [시작]이 두 번 와도 한 번만(엔진은 두 번째 시작을 버그로 보고 멈춘다)
                if (engine.phase == StairsEngine.Phase.READY) engine.start(MinigameTrial.limit(isPremium))
            },
            onExit = onExit,
        )
    } else if (result != null) {
        if (engine.trialFinished && !isPremium) {
            // 무료 체험 수를 다 채움 — 다시 하기 대신 결제 화면으로(구매하면 바로 일반 결과 창으로 바뀜).
            // 체험 수 전에 먹구름에 잡혔으면 일반 결과 창([다시 하기]는 체험 수가 처음부터) — 2026-11-24 대표님 결정
            GamePanelCard {
                TrialEndPanel(
                    correct = result.mainValue, onUnlock = onUnlock, onExit = onExit, xpEarned = result.xpEarned,
                    missionNote = result.missionNote,
                )
            }
        } else {
            GameResultPanel(
                viewport, result, engine.restartArmed,
                onRestart = { engine.restart(MinigameTrial.limit(isPremium)) },
                onExit = onExit,
            )
        }
    }
    if (engine.paused) {
        PausePanel(viewport, engine.session.stars, onResume = { engine.resume() }, onQuit = onExit)
    }
}

// MARK: 배지 줄·문제 판·고도계·알림

private fun DrawScope.drawStairsHud(
    engine: StairsEngine,
    viewport: GameViewport,
    images: GameImages,
    measurer: TextMeasurer,
    hint: String,
    caught: String,
) {
    val u = viewport.unit
    drawProblemBoard(engine, viewport, images, measurer, hint)
    drawAltimeter(engine.altimeter, images, engine.session.cloud.top, engine.bestAtStart, engine.accessory, viewport)
    drawBanner(engine.banner, measurer, viewport.safeCenter(0f, 300f), u)

    fun badge(state: HudBadgeState, icon: Int, slot: StairsEngine.HudSlot, content: HudBadgeContent = HudBadgeContent.NUMBER, iconScale: Float = 1f) =
        drawHudBadge(state, images, measurer, icon, viewport.safeTop(slot.x, PauseLayout.HUD_ROW_Y), slot.width, u, content, iconScale)
    badge(engine.stairsBadge, R.drawable.game_icon_stairs, StairsEngine.HUD_STAIRS)
    badge(engine.cloudBadge, R.drawable.game_icon_cloud, StairsEngine.HUD_CLOUD, HudBadgeContent.BAR)
    badge(engine.chestBadge, R.drawable.game_chest_closed, StairsEngine.HUD_CHEST, iconScale = 1.2f)
    // 보상(별 배지·불꽃·코인)은 판·글자 위에
    badge(engine.reward.coins, R.drawable.game_icon_coin, StairsEngine.HUD_COIN)
    drawComboBadge(engine.reward.combo, images, measurer, u * StairsEngine.COMBO_SCALE)
    drawCoinFlight(engine.reward.flight, images, u)
    if (engine.caughtTextVisible) {
        drawOutlinedText(
            measurer, caught, viewport.safeCenter(0f, 200f), 170 * u, GamePalette.gold, GamePalette.ink, 7 * u,
            maxWidth = size.width * 0.96f,
        )
    }
}

/** 문제 판(별 테두리 보드): 안쪽 남색 면에 단 안내와 문제를 함께 담는다. 판 아래에 첫 점프 안내 */
private fun DrawScope.drawProblemBoard(
    engine: StairsEngine,
    viewport: GameViewport,
    images: GameImages,
    measurer: TextMeasurer,
    hint: String,
) {
    val u = viewport.unit
    drawGameImageFit(images[R.drawable.game_board_star], viewport.safeTop(0f, -320f), viewport.px(1000f, 300f))
    drawGameText(
        measurer, engine.tableLabel, viewport.safeTop(0f, -255f), 48 * u, GamePalette.textDim,
        maxWidth = 1000 * u, minScale = 0.5f,
    )
    drawGameText(
        measurer, engine.problemText, viewport.safeTop(0f, -360f), 150 * u,
        if (engine.problemRevealing) GamePalette.correct else GamePalette.text,
        maxWidth = 1060 * u, minScale = 0.6f, pop = 1f + 0.12f * engine.problemPulse.toFloat(),
    )
    if (engine.hintVisible) {
        drawOutlinedText(
            measurer, hint, viewport.safeTop(0f, -525f), 56 * u, Color.White, GamePalette.ink, 4 * u,
            alpha = (0.55 + 0.45 * abs(sin(engine.time * 3))).toFloat(), maxWidth = size.width * 0.96f,
        )
    }
}

/** 배지 줄 그림 — 미리 풀어 둔다 */
private val HUD_IMAGES = listOf(
    R.drawable.game_board_star, R.drawable.game_icon_stairs, R.drawable.game_icon_cloud, R.drawable.game_icon_coin,
    R.drawable.game_chest_closed, R.drawable.game_badge_pill, R.drawable.game_card_jelly, R.drawable.game_combo_flame,
    R.drawable.game_flag, R.drawable.game_grass_hill, R.drawable.game_sky_cloud_puff, R.drawable.game_sky_rainbow,
    R.drawable.game_sky_moon,
)

/** DEBUG debug.gugu.stairs_autoplay=N (없으면 0). 잘못된 값은 조용히 넘기지 않고 멈춘다 */
private fun debugAutoplayCount(): Int {
    val raw = DevProps.get("debug.gugu.stairs_autoplay") ?: return 0
    val n = raw.toIntOrNull()
    require(n != null && n >= 0) { "debug.gugu.stairs_autoplay 는 0 이상 정수: $raw" }
    return n
}
