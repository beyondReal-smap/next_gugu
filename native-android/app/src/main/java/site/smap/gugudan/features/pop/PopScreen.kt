package site.smap.gugudan.features.pop

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import site.smap.gugudan.R
import site.smap.gugudan.core.Pop
import site.smap.gugudan.core.PopMission
import site.smap.gugudan.core.PremiumConfig
import site.smap.gugudan.core.RunnerPhase
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.features.paywall.TrialEndPanel
import site.smap.gugudan.features.paywall.TrialNotice
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Persistence
import site.smap.gugudan.store.LocalPremium
import site.smap.gugudan.store.MinigameStart

// 구구 팡팡 화면 (iOS PopView 대응) — 하늘에서 떨어지는 숫자 풍선 중 미션에 맞는 것만 눌러 터뜨린다.
// 진행 중: HUD(점수·최고·콤보·하트·일시정지) + 고정 위치 미션 막대 + 놀이 영역(PopScene).
// 준비·종료: 꾸밈 풍선 + 단 칩(섞어서 + 2~9단)·최고 기록·무료 체험 안내·시작 버튼 패널.
// 실시간 게임이라 화면 낭독기로는 할 수 없다(다른 미니게임과 같음) — 미션·점수·하트·결과는 읽히게 한다.

private val NAVY = Color(0xFF24306B)
private val CREAM = Color(0xFFFFF5DB)
private val HEART_ON = Color(0xFFFF9AA2)
private val HEART_OFF = Color(0xFF6E7FA8)
private val BOSS_TINT = Color(0xFF6B4FD8)
private val COMBO_HOT = Color(0xFFFFCF4A)
private val SLOW_BG = Color(0xFFFFE28A)
private val SLOW_FG = Color(0xFF8A5A00)
private val SHIELD_ON = Color(0xFFE0A800)
private val PAUSE_BG = Color(0xFFEEF8FF)
private val INK = Color(0xFF1F2544)

/** 시스템 "애니메이션 삭제"(접근성) 또는 개발자 옵션 애니메이터 배율 0 — 흔들림·입자를 끈다 */
@Composable
private fun rememberReducedMotion(): Boolean = remember { !ValueAnimator.areAnimatorsEnabled() }

@Composable
fun PopScreen(start: MinigameStart, onExit: () -> Unit) {
    val gg = LocalGG.current
    val context = LocalContext.current
    // 단 시트·'섞어서' 시트에서 열면 그 단을 골라 둔 채로, 홈에서 열면 기본값(섞어서)으로 준비한다
    val engine = remember {
        val persistence = Persistence(context.applicationContext)
        when (start) {
            MinigameStart.Default -> PopEngine(persistence)
            is MinigameStart.Table -> PopEngine(persistence, initialTable = start.table)
        }
    }
    // 시스템 뒤로가기 — 진행 중이면 일시정지(실수로 판을 날리지 않게), 멈춰 있거나 끝났으면 나가기
    BackHandler {
        if (engine.game.phase == RunnerPhase.RUNNING) engine.pause() else onExit()
    }
    val game = engine.game
    val reducedMotion = rememberReducedMotion()

    val ready = game.phase == RunnerPhase.READY
    val over = game.phase == RunnerPhase.OVER
    val playing = !ready && !over
    val premium = LocalPremium.current
    // 무료 체험 판이 끝나면(첫 웨이브를 깨거나 그 전에 하트를 다 써도) 다시 하기 대신 결제 화면으로 안내한다 —
    // 다른 미니게임과 같은 경계(대표님 결정 2026-10-23, iOS PopView.trialEnded 와 같음). 구매하면 바로 일반 종료 화면
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

    Column(Modifier.fillMaxSize().background(gg.bg).statusBarsPadding().navigationBarsPadding()) {
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
                    Text(stringResource(R.string.exit), style = suite(FontWeight.Bold, 14), color = gg.textMuted)
                }
            }
            Spacer(Modifier.weight(1f))
            Pill(bg = gg.surface, fg = gg.textMuted) {
                Icon(Icons.Filled.BubbleChart, null, Modifier.size(14.dp))
                Text(stringResource(R.string.gugu_pop))
            }
        }

        if (playing) {
            // 진행 중에는 놀이 영역이 남은 높이를 다 쓴다 — 스크롤이 없어 풍선을 누르다 화면이 밀리지 않는다
            Stage(engine, reducedMotion, Modifier.weight(1f).readableWidth(560.dp).padding(horizontal = 12.dp).padding(bottom = 8.dp))
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .readableWidth(560.dp).padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Hero()
                Column(Modifier.fillMaxWidth().ggCard(20.dp).padding(16.dp)) {
                    if (trialEnded) {
                        TrialEndPanel(
                            game.pops, onUnlock = { premium.openPaywall() }, onExit = onExit,
                            lead = { pluralStringResource(R.plurals.you_popped_balloons, it, it) },
                        )
                    } else {
                        SetupPanel(engine, over)
                    }
                }
            }
        }
    }
}

// MARK: - 진행 중 무대

@Composable
private fun Stage(engine: PopEngine, reducedMotion: Boolean, modifier: Modifier) {
    val game = engine.game
    val playArea = stringResource(R.string.gugu_pop_play_area)
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).border(1.dp, NAVY.copy(alpha = 0.2f), RoundedCornerShape(24.dp)),
    ) {
        Hud(engine)
        MissionBar(engine)
        Box(
            Modifier.fillMaxWidth().weight(1f).clipToBounds()
                .semantics { contentDescription = playArea }
                // 키를 phase 로 두면 일시정지를 풀 때 입력 감지를 다시 시작하는 사이 첫 탭이 빠졌다 — 진행 여부는 engine.tap 이 본다
                .pointerInput(Unit) {
                    // 손가락이 닿는 순간 쏜다 — 떼기를 기다리면 빠르게 떨어지는 풍선을 놓친다
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val layout = PopLayout(size.width.toFloat(), size.height.toFloat())
                        val (x, y, aspect) = layout.toCore(down.position.x, down.position.y)
                        engine.tap(x, y, aspect)
                    }
                },
        ) {
            PopScene(game = game, clock = engine.clock, fx = engine.fx, reducedMotion = reducedMotion)
            IntroBanner(engine, reducedMotion)
            if (game.phase == RunnerPhase.PAUSED) PauseOverlay(engine)
        }
    }
}

@Composable
private fun Hud(engine: PopEngine) {
    val game = engine.game
    val paused = game.phase == RunnerPhase.PAUSED
    val scoreLabel = pluralStringResource(R.plurals.score_points, game.score, game.score)
    val bestLabel = pluralStringResource(R.plurals.best_points, engine.displayBest, engine.displayBest)
    val heartsLabel = pluralStringResource(R.plurals.hearts_left, game.hearts, game.hearts)
    val multiplier = Pop.multiplier(game.combo)
    Row(
        Modifier.fillMaxWidth().background(NAVY).padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clearAndSetSemantics { contentDescription = scoreLabel },
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Star, null, tint = CREAM, modifier = Modifier.size(18.dp))
            Text("${game.score}", style = suite(FontWeight.ExtraBold, 16), color = CREAM)
        }
        Row(
            Modifier.clearAndSetSemantics { contentDescription = bestLabel },
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.EmojiEvents, null, tint = CREAM.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
            Text("${engine.displayBest}", style = suite(FontWeight.Bold, 13), color = CREAM.copy(alpha = 0.7f))
        }
        // 콤보 — 2부터 보이고, 배율이 붙으면 ×2·×3 (금색)
        if (game.combo >= 2) {
            Row(
                Modifier.clip(CircleShape).background(if (multiplier > 1) COMBO_HOT else CREAM)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.combo_2, game.combo), style = suite(FontWeight.ExtraBold, 11), color = NAVY, maxLines = 1)
                if (multiplier > 1) Text("×$multiplier", style = suite(FontWeight.Black, 12), color = NAVY)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.clearAndSetSemantics { contentDescription = heartsLabel },
        ) {
            for (heart in 1..Pop.MAX_HEARTS) {
                Icon(
                    if (heart <= game.hearts) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    null,
                    tint = if (heart <= game.hearts) HEART_ON else HEART_OFF,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        PressableCard(onClick = { engine.togglePause() }) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = stringResource(if (paused) R.string.resume_game else R.string.pause),
                    tint = CREAM, modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun missionText(mission: PopMission): String = when (mission) {
    is PopMission.Multiples -> stringResource(R.string.pop_mission_multiples, mission.table)
    is PopMission.PairBoss -> stringResource(R.string.pop_mission_pair, mission.target)
}

/** 미션 막대 — 높이·위치 고정(문제 위치를 움직이지 않는 원칙). 보스 웨이브에도 같은 자리에 같은 크기. 바뀌면 TalkBack 이 읽는다 */
@Composable
private fun MissionBar(engine: PopEngine) {
    val gg = LocalGG.current
    val game = engine.game
    val boss = game.boss
    val isBoss = game.mission is PopMission.PairBoss
    val mission = missionText(game.mission)
    val wave = stringResource(R.string.pop_wave, game.wave)
    // 낭독 — iOS missionLabel 과 같은 구성 (미션, 웨이브, 짝꿍·방패 또는 진행도)
    val parts = mutableListOf(mission, wave)
    if (boss != null) {
        game.pending?.let { parts += stringResource(R.string.picked_for_the_pair, it) }
        parts += pluralStringResource(R.plurals.boss_shields_left, boss.hitsLeft, boss.hitsLeft)
    } else {
        parts += stringResource(R.string.of, Pop.WAVE_GOAL, game.handled)
    }
    val label = parts.joinToString(", ")
    val accent = if (isBoss) BOSS_TINT else GGColors.sky
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(gg.surface).padding(horizontal = 12.dp)
            .clearAndSetSemantics { contentDescription = label; liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(if (isBoss) Icons.Filled.Shield else Icons.Filled.TrackChanges, null, tint = accent, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            // 영어처럼 긴 미션은 한 줄에 맞게 글자를 줄인다
            BasicText(
                mission, style = suite(FontWeight.ExtraBold, 16).copy(color = gg.text), maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 16.sp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(wave, style = suite(FontWeight.Bold, 11), color = gg.textMuted, maxLines = 1)
                if ((game.trialLeft ?: 0) > 0) {
                    Text(
                        stringResource(R.string.trial_wave), style = suite(FontWeight.ExtraBold, 10), color = gg.accent, maxLines = 1,
                        modifier = Modifier.clip(CircleShape).background(gg.accent.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                // 황금 풍선 느려짐 — 놀이 영역 위에 띄우면 위에서 들어오는 풍선을 가려서 미션 막대 안에 둔다
                if (game.slowMs > 0) {
                    Text(
                        stringResource(R.string.pop_slow), style = suite(FontWeight.ExtraBold, 10), color = SLOW_FG, maxLines = 1,
                        modifier = Modifier.clip(CircleShape).background(SLOW_BG).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
        // 오른쪽 — 배수 사냥은 진행도 3/6, 보스는 먼저 고른 짝꿍 "6 × ?" + 남은 방패
        if (boss != null) {
            Text(
                game.pending?.let { "$it × ?" } ?: "? × ?", style = suite(FontWeight.ExtraBold, 18),
                color = if (game.pending == null) gg.textMuted else BOSS_TINT,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(Pop.BOSS_HITS) { i ->
                    Icon(
                        if (i < boss.hitsLeft) Icons.Filled.Shield else Icons.Outlined.Shield, null,
                        tint = if (i < boss.hitsLeft) SHIELD_ON else gg.border, modifier = Modifier.size(13.dp),
                    )
                }
            }
        } else {
            Text("${game.handled}/${Pop.WAVE_GOAL}", style = suite(FontWeight.ExtraBold, 18), color = gg.text)
        }
    }
}

/** 미션 안내 배너 내용 — 사라지는 동안에도 마지막 값을 유지하도록 AnimatedContent 상태로 넘긴다 */
private data class Banner(val wave: Int, val mission: PopMission)

/** 웨이브 시작 전 미션 안내 — 풍선이 멈춰 있는 동안만 보인다 (낭독은 미션 막대가 맡는다) */
@Composable
private fun IntroBanner(engine: PopEngine, reducedMotion: Boolean) {
    val game = engine.game
    val banner = if (game.introMs > 0 && game.phase == RunnerPhase.RUNNING) Banner(game.wave, game.mission) else null
    AnimatedContent(
        targetState = banner,
        transitionSpec = {
            if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
            else fadeIn(tween(200)) togetherWith fadeOut(tween(200))
        },
        // 놀이 영역 전체를 차지해 크기가 바뀌지 않게 한다 — 내용 크기로 두면 사라질 때 상자가 줄며 배너가 옆으로 밀렸다
        modifier = Modifier.fillMaxSize(),
        label = "popBanner",
    ) { b ->
        if (b == null) return@AnimatedContent
        val isBoss = b.mission is PopMission.PairBoss
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.94f)).padding(horizontal = 22.dp, vertical = 16.dp)
                    .clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (isBoss) stringResource(R.string.pair_boss_is_here) else stringResource(R.string.pop_wave, b.wave),
                    style = suite(FontWeight.ExtraBold, 14), color = if (isBoss) BOSS_TINT else GGColors.sky,
                )
                Text(missionText(b.mission), style = suite(FontWeight.Black, 26), color = NAVY, textAlign = TextAlign.Center, maxLines = 2)
                if (isBoss) {
                    Text(stringResource(R.string.pop_a_pair_one_after_the_other), style = suite(FontWeight.Bold, 13),
                        color = LocalGG.current.textMuted, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun PauseOverlay(engine: PopEngine) {
    Column(
        Modifier.fillMaxSize().background(PAUSE_BG.copy(alpha = 0.95f)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.taking_a_break), style = suite(FontWeight.ExtraBold, 20), color = NAVY,
            modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        GGButton(
            variant = GGButtonVariant.PRIMARY, size = GGButtonSize.MD,
            modifier = Modifier.width(200.dp),
            onClick = { engine.togglePause() },
        ) {
            Text(stringResource(R.string.keep_popping))
        }
    }
}

// MARK: - 준비·종료 패널

/** 꾸밈 그림 — 숫자 풍선 세 개 (낭독하지 않는다) */
@Composable
private fun Hero() {
    Row(
        Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFBFE3FF), Color(0xFFEEF8FF))))
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        HeroBalloon("14", Color(0xFFFF8FAB), lift = 10)
        HeroBalloon("21", Color(0xFFFFC93C), lift = 0)
        HeroBalloon("35", Color(0xFF7CC6FE), lift = 16)
    }
}

@Composable
private fun HeroBalloon(value: String, color: Color, lift: Int) {
    Column(Modifier.padding(bottom = lift.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 56.dp, height = 62.dp).clip(RoundedCornerShape(50)).background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(value, style = suite(FontWeight.ExtraBold, 22), color = INK)
        }
        Box(Modifier.size(width = 1.5.dp, height = 18.dp).background(INK.copy(alpha = 0.3f)))
    }
}

@Composable
private fun SetupPanel(engine: PopEngine, over: Boolean) {
    val gg = LocalGG.current
    val premium = LocalPremium.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (over) {
            Results(engine)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.pop_ready_title), style = suite(FontWeight.ExtraBold, 16), color = gg.text,
                    modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.number_balloons_float_down_tap_only_the), style = suite(FontWeight.Medium, 13),
                    color = gg.textMuted)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.table_to_practice), style = suite(FontWeight.ExtraBold, 12), color = gg.textMuted,
                    modifier = Modifier.weight(1f))
                val best = engine.best(engine.table)
                if (best > 0) {
                    val bestLabel = pluralStringResource(R.plurals.best_points, best, best)
                    Row(
                        Modifier.clearAndSetSemantics { contentDescription = bestLabel },
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.EmojiEvents, null, tint = gg.warning, modifier = Modifier.size(12.dp))
                        Text(stringResource(R.string.best, best), style = suite(FontWeight.ExtraBold, 11), color = gg.warning)
                    }
                }
            }
            // 3×3 — 섞어서 + 2~9단
            val options: List<Int?> = listOf(null) + PopEngine.TABLES
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { value ->
                            TableChip(
                                label = if (value == null) stringResource(R.string.mix_tables) else stringResource(R.string.table, value),
                                selected = engine.table == value,
                                modifier = Modifier.weight(1f),
                                onClick = { Haptics.selection(); engine.table = value },
                            )
                        }
                    }
                }
            }
        }

        if (!premium.isPremium) TrialNotice(stringResource(R.string.pop_trial_first_wave))

        GGButton(
            variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG,
            onClick = { engine.start(if (premium.isPremium) null else PremiumConfig.POP_TRIAL_WAVES) },
        ) {
            Icon(if (over) Icons.Filled.RotateLeft else Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
            Text(stringResource(if (over) R.string.pop_again else R.string.pop_start))
        }

        Text(
            stringResource(R.string.s_3_hearts_a_pair_boss_appears_every_3rd),
            style = suite(FontWeight.Medium, 12), color = gg.textMuted,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Results(engine: PopEngine) {
    val gg = LocalGG.current
    val game = engine.game
    val best = game.score > 0 && game.score >= engine.runBest
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(pluralStringResource(R.plurals.points, game.score, game.score), style = suite(FontWeight.Black, 28), color = gg.text)
            Text(
                stringResource(if (best) R.string.that_s_your_best_score else R.string.pop_again_with_the_bunny),
                style = suite(FontWeight.Bold, 13), color = if (best) gg.warning else gg.textMuted, textAlign = TextAlign.Center,
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(stringResource(R.string.balloons_popped), game.pops, Modifier.weight(1f))
            Stat(stringResource(R.string.best_combo), game.maxCombo, Modifier.weight(1f))
            Stat(stringResource(R.string.wave), game.wave, Modifier.weight(1f))
            Stat(stringResource(R.string.bosses_beaten), game.bossesDefeated, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    val gg = LocalGG.current
    Column(
        modifier.fillMaxHeight().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(gg.surface2)
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .clearAndSetSemantics { contentDescription = "$label $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text("$value", style = suite(FontWeight.ExtraBold, 18), color = gg.text)
        Text(label, style = suite(FontWeight.Bold, 10), color = gg.textMuted, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun TableChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val gg = LocalGG.current
    PressableCard(modifier = modifier.semantics { this.selected = selected }, onClick = onClick) {
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
                .background(if (selected) NAVY else gg.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = suite(FontWeight.ExtraBold, 13), color = if (selected) CREAM else gg.text, maxLines = 1)
        }
    }
}
