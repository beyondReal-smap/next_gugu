package site.smap.gugudan.features.games.monster

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import site.smap.gugudan.R
import site.smap.gugudan.core.games.MonsterKind
import site.smap.gugudan.core.games.MonsterSession
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
import site.smap.gugudan.features.games.shared.HudBadgeState
import site.smap.gugudan.features.games.shared.LocalGameImages
import site.smap.gugudan.features.games.shared.PauseButton
import site.smap.gugudan.features.games.shared.PauseLayout
import site.smap.gugudan.features.games.shared.PausePanel
import site.smap.gugudan.features.games.shared.centeredAt
import site.smap.gugudan.features.games.shared.drawBanner
import site.smap.gugudan.features.games.shared.drawCoinFlight
import site.smap.gugudan.features.games.shared.drawComboBadge
import site.smap.gugudan.features.games.shared.drawGameImage
import site.smap.gugudan.features.games.shared.drawGameImageFit
import site.smap.gugudan.features.games.shared.drawGameText
import site.smap.gugudan.features.games.shared.drawHudBadge
import site.smap.gugudan.features.games.shared.drawOutlinedText
import site.smap.gugudan.features.games.shared.multiplyFilter
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

// 구구 몬스터 화면 (Unity 시험판 B 곱셈 몬스터 — MonsterGame.BuildHud·PrototypeShell 화면 구성 이식, iOS MonsterView.swift 대응)
// 숲 무대(Canvas) 위에 위 배지 줄 [⏸]·몬스터·남은 시간·별, 도감 띠, 문제 판, 젤리 카드 6장, 알림·불꽃·코인을 얹고,
// 시작 전 패널·쉬는 창·결과 창(체험이면 체험 종료 패널)을 맨 위에 띄운다.
// 카드는 닿는 순간 반응 — 무대 전체 onTouchDown 이 받고 엔진이 카드 칸 안인지 가린다(카드는 그리기만).
// Android 뒤로 가기 = 쉬는 창(MonsterEngine.pressBack). 전체 화면(상태 표시줄·내비게이션 바 뒤까지)에 그리고,
// 배지 줄·문제 판·카드는 안전 영역(GameViewport) 안에 둔다.

/** 숲 그림이 뜨기 전 바탕(숲 아래쪽 어두운 초록) */
private val BACKDROP = Color(0xFF1E3A2C)

/** start: 홈·섞어서 = 2~9단 섞어서, 단 시트에서 고른 단(Table(n), n 은 2~9) = 그 단만 */
@Composable
fun MonsterScreen(start: MinigameStart, onExit: () -> Unit) {
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
        MonsterEngine(
            start, orders.order(PlayGame.MONSTER), rewards, strings, commit, autoplay = debugAutoplayCount(),
            reduceMotion = reduceMotion,
        )
    }
    val images = rememberGameImages()
    // 첫 프레임이 끊기지 않게 무대·배지 그림을 미리 풀어 둔다
    remember(images) { images.preload(MonsterScene.IMAGES + HUD_IMAGES) }

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

    BoxWithConstraints(Modifier.fillMaxSize().background(BACKDROP)) {
        val density = LocalDensity.current
        val size = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val insets = rememberGameInsets()
        val viewport = remember(size, insets) { GameViewport(size, insets) }
        val trial = MinigameTrial.limit(premium.isPremium)
        SideEffect {
            engine.updateViewport(viewport)
            engine.debugAutoStart(trial)
        }

        CompositionLocalProvider(LocalGameImages provides images) {
            MonsterPlayfield(
                engine, viewport, images, isPremium = premium.isPremium,
                onOrder = onOrder@{ order ->
                    // 두 손가락으로 [시작]과 같이 눌러 판이 이미 시작됐으면 무시(엔진은 시작 뒤 순서 변경을 버그로 보고 멈춘다)
                    if (engine.phase != MonsterEngine.Phase.READY) return@onOrder
                    // 엔진이 뒤의 첫 몬스터를 새 순서로 다시 만들고, 선택은 다음에 열 때를 위해 기억한다
                    engine.setOrder(order)
                    orders.setOrder(order, PlayGame.MONSTER)
                },
                onExit = exit, onUnlock = { premium.openPaywall() },
            )
        }
    }
}

/** 엔진이 있는 동안의 화면 — 그림은 프레임마다(engine.frame), 창은 상태가 바뀔 때만 다시 구성 */
@Composable
private fun MonsterPlayfield(
    engine: MonsterEngine,
    viewport: GameViewport,
    images: GameImages,
    isPremium: Boolean,
    /** 시작 전 패널의 [순서대로]·[무작위] */
    onOrder: (ProblemOrder) -> Unit,
    onExit: () -> Unit,
    onUnlock: () -> Unit,
) {
    val measurer = rememberGameTextMeasurer()
    val hint = stringResource(R.string.monster_hint)
    val timeUp = stringResource(R.string.game_time_up)
    Box(Modifier.fillMaxSize()) {
        // 무대 — 손가락이 닿는 순간 큐로(떼기·끌기를 기다리지 않음, 여러 손가락 각각). 카드 칸 판정은 엔진이 한다
        Canvas(Modifier.fillMaxSize().onTouchDown(engine::enqueueTap)) {
            engine.frame   // 프레임마다 다시 그리기
            drawMonsterScene(engine.stage, engine.sparkles, engine.time, images, measurer)
        }
        // 문제 판·카드·배지 줄·도감·알림 — 입력은 받지 않음(아래 무대로 통과)
        Canvas(Modifier.fillMaxSize()) {
            engine.frame
            drawMonsterHud(engine, viewport, images, measurer, hint, timeUp)
        }
        val left = engine.trialLeft
        if (left != null && engine.phase == MonsterEngine.Phase.PLAYING && !engine.roundOver) {
            // 카드 아랫줄 아래 빈 숲 — 문제 판·몬스터·안내 글자와 겹치지 않는 자리
            Box(Modifier.centeredAt(viewport.safeBottom(0f, 130f))) { TrialProgressPill(left) }
        }
        if (engine.pauseButtonVisible) PauseButton(viewport) { engine.openPause() }
        Overlays(engine, viewport, isPremium, onOrder, onExit, onUnlock)
        // 학습 기록 알림(새 업적·레벨 업) — 결과·체험 종료 창 위
        GameCommitFeedback(engine.learning.commit, viewport)
    }
}

@Composable
private fun Overlays(
    engine: MonsterEngine,
    viewport: GameViewport,
    isPremium: Boolean,
    onOrder: (ProblemOrder) -> Unit,
    onExit: () -> Unit,
    onUnlock: () -> Unit,
) {
    val result = engine.result
    if (engine.phase == MonsterEngine.Phase.READY) {
        val table = engine.playedTable
        GameStartPanel(
            // 순서대로 + 단 시트에서 고른 단이면 이름 옆에 단을 붙인다(무작위는 고른 단을 쓰지 않음)
            title = if (table == null) stringResource(R.string.monster_title) else stringResource(R.string.monster_title_table, table),
            summary = stringResource(R.string.monster_summary),
            icon = Icons.Filled.Pets, isPremium = isPremium,
            order = engine.order, onOrder = onOrder,
            onStart = {
                // 여러 손가락·연타로 [시작]이 두 번 와도 한 번만(엔진은 두 번째 시작을 버그로 보고 멈춘다)
                if (engine.phase == MonsterEngine.Phase.READY) engine.start(MinigameTrial.limit(isPremium))
            },
            onExit = onExit,
        )
    } else if (result != null) {
        if (engine.trialFinished && !isPremium) {
            // 무료 체험 수를 다 채움 — 다시 하기 대신 결제 화면으로(구매하면 바로 일반 결과 창으로 바뀜).
            // 체험 수 전에 60초가 끝났으면 일반 결과 창([다시 하기]는 체험 수가 처음부터) — 2026-11-24 대표님 결정
            GamePanelCard {
                TrialEndPanel(
                    correct = result.mainValue, onUnlock = onUnlock, onExit = onExit,
                    lead = { pluralStringResource(R.plurals.monster_trial_defeated, it, it) },
                    xpEarned = result.xpEarned,
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

// MARK: 문제 판·카드·배지 줄·도감·알림

private fun DrawScope.drawMonsterHud(
    engine: MonsterEngine,
    viewport: GameViewport,
    images: GameImages,
    measurer: TextMeasurer,
    hint: String,
    timeUp: String,
) {
    val u = viewport.unit
    drawProblemBoard(engine, viewport, images, measurer, hint)
    for (i in 0 until MonsterSession.CARD_COUNT) drawCard(engine, i, viewport, images, measurer)

    fun badge(state: HudBadgeState, icon: Int, slot: MonsterEngine.HudSlot, iconScale: Float = 1f) =
        drawHudBadge(state, images, measurer, icon, viewport.safeTop(slot.x, PauseLayout.HUD_ROW_Y), slot.width, u, iconScale = iconScale)
    badge(engine.monsterBadge, MonsterArt.res(MonsterKind.GREEN), MonsterEngine.HUD_MONSTER, iconScale = 1.2f)
    badge(engine.timeBadge, R.drawable.game_icon_hourglass, MonsterEngine.HUD_TIME)
    drawDexStrip(engine.dex, images, viewport.safeTop(0f, MonsterEngine.DEX_Y), u)
    // 알림: 몬스터 배 숫자를 가리지 않게 몬스터 아래(그루터기 근처, 카드 줄 위)
    drawBanner(engine.banner, measurer, viewport.safeCenter(0f, -165f), u, fontSize = 76f)
    if (engine.timeUpVisible) {
        drawOutlinedText(
            measurer, timeUp, viewport.safeCenter(0f, 200f), 170 * u, GamePalette.gold, GamePalette.ink, 7 * u,
            maxWidth = size.width * 0.96f,
        )
    }
    // 보상(별 배지·불꽃·코인)은 판·카드 위에
    badge(engine.reward.coins, R.drawable.game_icon_coin, MonsterEngine.HUD_COIN)
    drawComboBadge(engine.reward.combo, images, measurer, u)
    drawCoinFlight(engine.reward.flight, images, u)
}

/**
 * 문제 판(별 테두리 보드, 10:3): 안쪽 남색 면에 문제(-352)와 보조 식(-452)을 함께 담는다.
 * 몬스터 귀 꼭대기(약 -540)와 겹치지 않는 높이. 첫 카드 전 안내는 카드 윗줄 위
 */
private fun DrawScope.drawProblemBoard(
    engine: MonsterEngine,
    viewport: GameViewport,
    images: GameImages,
    measurer: TextMeasurer,
    hint: String,
) {
    val u = viewport.unit
    drawGameImageFit(images[R.drawable.game_board_star], viewport.safeTop(0f, -375f), viewport.px(1000f, 262f))
    val revealing = engine.problemRevealing
    drawGameText(
        measurer, engine.problemText, viewport.safeTop(0f, -352f), 132 * u,
        if (revealing) GamePalette.correct else GamePalette.text,
        maxWidth = 1060 * u, minScale = 0.6f, pop = 1f + 0.12f * engine.problemPulse.toFloat(),
    )
    drawGameText(
        measurer, engine.subText, viewport.safeTop(0f, -452f), 54 * u,
        if (revealing) GamePalette.wrong else GamePalette.correct,
        maxWidth = 1000 * u, minScale = 0.6f,
    )
    if (engine.hintVisible) {
        drawOutlinedText(
            measurer, hint, viewport.safeBottom(0f, 800f), 52 * u, Color.White, GamePalette.ink, 4 * u,
            alpha = (0.55 + 0.45 * abs(sin(engine.time * 3))).toFloat(), maxWidth = size.width * 0.96f,
        )
    }
}

/** 카드마다 다른 젤리 색(무채색 그림에 곱함) — 민트·하늘·보라·코랄·연두·살구. 어두운 숫자가 잘 읽히는 밝은 색만 */
private val CARD_COLORS = listOf(
    Color(0xFF7FE3A8), Color(0xFF7CC6FF), Color(0xFFC8A8FF), Color(0xFFFF9D8F), Color(0xFFA9E27B), Color(0xFFFFC18A),
)
/** 젤리 카드 밝은 몸통 중심이 그림 중심보다 이만큼(높이 대비, 실측) 위 */
private const val CARD_LABEL_LIFT = 0.072f
private const val CARD_SELECTED_SCALE = 1.08f
/** 고른 카드 둘레 흰 테두리 두께(양쪽 합, 설계 px) */
private const val CARD_GLOW_PAD = 34f

/**
 * 젤리 카드 한 장(원래 비율 그림을 카드 크기로 — 9분할 없이) + 숫자.
 * 고르면 흰 테두리가 생기고 살짝 커진다(색은 그대로). 공개 중에는 정답 두 장 초록·고른 두 장 빨강
 */
private fun DrawScope.drawCard(
    engine: MonsterEngine,
    index: Int,
    viewport: GameViewport,
    images: GameImages,
    measurer: TextMeasurer,
) {
    val u = viewport.unit
    val center = MonsterEngine.Cards.center(index, viewport)
    val w = MonsterEngine.Cards.SIZE.width * u
    val h = MonsterEngine.Cards.SIZE.height * u
    val selected = engine.cardSelected(index)
    val color = when (engine.cardLook(index)) {
        MonsterEngine.CardLook.NORMAL -> CARD_COLORS[index]
        MonsterEngine.CardLook.CORRECT -> GamePalette.correct
        MonsterEngine.CardLook.WRONG -> GamePalette.wrong
    }
    val jelly = images[R.drawable.game_card_jelly]
    scale(if (selected) CARD_SELECTED_SCALE else 1f, pivot = center) {
        if (selected) drawGameImage(jelly, center, Size(w + CARD_GLOW_PAD * u, h + CARD_GLOW_PAD * u))
        drawGameImage(jelly, center, Size(w, h), colorFilter = multiplyFilter(color))
        drawGameText(
            measurer, "${engine.cardValue(index)}", Offset(center.x, center.y - h * CARD_LABEL_LIFT), 130 * u, GamePalette.ink,
        )
    }
}

/** 배지 줄·카드 그림 — 미리 풀어 둔다 */
private val HUD_IMAGES = listOf(
    R.drawable.game_board_star, R.drawable.game_icon_hourglass, R.drawable.game_icon_coin, R.drawable.game_icon_lock,
    R.drawable.game_badge_pill, R.drawable.game_card_jelly, R.drawable.game_combo_flame,
)

/** DEBUG debug.gugu.monster_autoplay=N (없으면 0). 잘못된 값은 조용히 넘기지 않고 멈춘다 */
private fun debugAutoplayCount(): Int {
    val raw = DevProps.get("debug.gugu.monster_autoplay") ?: return 0
    val n = raw.toIntOrNull()
    require(n != null && n >= 0) { "debug.gugu.monster_autoplay 는 0 이상 정수: $raw" }
    return n
}
