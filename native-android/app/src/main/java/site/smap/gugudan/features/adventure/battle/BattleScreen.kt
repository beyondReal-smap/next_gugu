package site.smap.gugudan.features.adventure.battle

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
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
import site.smap.gugudan.core.adventure.Battle
import site.smap.gugudan.core.adventure.BattleStyle
import site.smap.gugudan.core.adventure.NpcDef
import site.smap.gugudan.core.adventure.World
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.features.home.GaugeBar
import site.smap.gugudan.features.session.AnswerFeedback
import site.smap.gugudan.features.session.Keypad
import site.smap.gugudan.store.LocalAdventure
import site.smap.gugudan.store.LocalGame

// 배틀 화면 (iOS BattleView.swift 이식) — 3종 방식 + 보스 분노 + 인트로/결과 오버레이

@Composable
fun BattleScreen(npc: NpcDef, onWorld: () -> Unit, onRetry: () -> Unit, onFlee: () -> Unit) {
    val gg = LocalGG.current
    val game = LocalGame.current
    val adventure = LocalAdventure.current
    val engine = remember { BattleEngine(npc, game, adventure).also { it.start() } }
    var confetti by remember { mutableStateOf(0) }
    var levelUp by remember { mutableStateOf(false) }

    DisposableEffect(engine) { onDispose { engine.teardown() } }

    LaunchedEffect(engine.end) {
        engine.end?.let { end ->
            if (end.won) confetti += 1
            if (end.commit.leveledUp) { delay(700); levelUp = true }
        }
    }

    Box(Modifier.fillMaxSize().background(gg.bg)) {
        BattleBody(engine, onFlee)
        when (engine.phase) {
            BattlePhase.INTRO -> IntroOverlay(engine)
            BattlePhase.END -> engine.end?.let { ResultOverlay(engine, it, onWorld, onRetry) }
            else -> {}
        }
        ConfettiView(confetti)
        engine.end?.let {
            AchievementToast(it.commit.unlocked + it.advUnlocked,
                Modifier.statusBarsPadding().padding(horizontal = 20.dp).padding(top = 12.dp))
        }
        LevelUpOverlay(levelUp, engine.end?.commit?.newLevel ?: 0) { levelUp = false }
    }
}

@Composable
private fun BattleBody(engine: BattleEngine, onFlee: () -> Unit) {
    val gg = LocalGG.current
    val adventure = LocalAdventure.current

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp),
    ) {
        // 상단 바
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PressableCard(onClick = onFlee) {
                Icon(Icons.Filled.Close, "도망가기", tint = gg.textMuted, modifier = Modifier.size(22.dp))
            }
            Text(
                "${if (engine.isBoss) "보스 대결" else Battle.styleName(engine.style)} · ${engine.npc.table}단",
                style = suite(FontWeight.Bold, 14), color = gg.textMuted, modifier = Modifier.weight(1f),
            )
            if (engine.combo >= 2) {
                Pill(bg = gg.danger.copy(alpha = 0.15f), fg = gg.danger) {
                    Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(14.dp))
                    Text("${engine.combo} 콤보")
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // NPC 패널
        Panel(
            avatarColor = engine.npc.color, avatarSize = 52.dp, boss = engine.isBoss,
            enraged = engine.enraged, floater = engine.npcFloat,
            title = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(engine.npc.name, style = suite(FontWeight.ExtraBold, 14), color = gg.text, maxLines = 1)
                    if (engine.isBoss) Chip("보스", gg.danger, gg.danger.copy(alpha = 0.15f))
                    if (engine.enraged) Chip("분노", Color.White, gg.danger)
                }
            },
            bar = {
                if (engine.style == BattleStyle.SPEED) {
                    StatBar(engine.npcScore.toDouble() / Battle.RACE_TARGET, "${engine.npcScore}/${Battle.RACE_TARGET}", gg.warning)
                } else {
                    val pct = engine.npcHp.toDouble() / engine.npcMaxHp
                    StatBar(pct, "${maxOf(0, engine.npcHp)}/${engine.npcMaxHp}", if (pct > 0.4) gg.danger else gg.warning)
                }
            },
        )

        // 반격전 카운트다운 — COUNTER 모드에서 자리를 항상 예약(정답/오답 시 바가 사라져도
        // 아래 문제 영역이 위로 밀리지 않도록). 내용만 조건부로 표시.
        if (engine.style == BattleStyle.COUNTER) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
                if (engine.phase == BattlePhase.PLAY && engine.feedback == null) {
                    CounterTimer(engine.qStartClock, engine.counterLimit)
                }
            }
        }

        // 문제 — 식(Equation)은 항상 세로 중앙에 고정. 오답 설명은 그 아래 오버레이로 띄워
        // 레이아웃을 밀지 않게 한다(정답/오답 시 문제가 위로 점프하던 현상 제거).
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val p = engine.problem
            val answer = p.a * p.b
            val showAnswer = engine.feedback == AnswerFeedback.WRONG
            val big = suite(FontWeight.ExtraBold, 48)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${p.a}", style = big, color = gg.text)
                Text("×", style = big, color = gg.textMuted)
                Text("${p.b}", style = big, color = gg.text)
                Text("=", style = big, color = gg.textMuted)
                Text(if (showAnswer) "$answer" else engine.input.ifEmpty { "?" }, style = big,
                    color = if (showAnswer) gg.success else if (engine.input.isEmpty()) gg.border else gg.accent)
            }
            if (showAnswer) {
                Text(
                    when {
                        engine.timedOut -> "시간 초과! 정답은 $answer — ${engine.npc.name}의 반격!"
                        engine.style == BattleStyle.SPEED -> "정답은 $answer 이에요"
                        else -> "정답은 $answer — ${engine.npc.name}의 반격!"
                    },
                    style = suite(FontWeight.Bold, 13), color = gg.textMuted, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).offset(y = 48.dp).padding(horizontal = 16.dp),
                )
            }
        }

        // 플레이어 패널
        Panel(
            avatarColor = adventure.equippedColorHex, avatarSize = 44.dp, boss = false,
            enraged = false, floater = engine.playerFloat,
            title = { Text("나", style = suite(FontWeight.ExtraBold, 14), color = gg.text) },
            bar = {
                if (engine.style == BattleStyle.SPEED) {
                    StatBar(engine.playerScore.toDouble() / Battle.RACE_TARGET, "${engine.playerScore}/${Battle.RACE_TARGET}", gg.accent)
                } else {
                    StatBar(engine.playerHp.toDouble() / Battle.PLAYER_MAX_HP,
                        "${maxOf(0, engine.playerHp)}/${Battle.PLAYER_MAX_HP}", gg.success)
                }
            },
        )
        Spacer(Modifier.height(12.dp))

        Keypad(
            onInput = { engine.handleInput(it) },
            onDelete = { engine.handleDelete() },
            onSubmit = { engine.handleManualSubmit() },
            canSubmit = engine.input.isNotEmpty(),
        )
    }
}

@Composable
private fun Chip(label: String, fg: Color, bg: Color) {
    Box(Modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(label, style = suite(FontWeight.ExtraBold, 10), color = fg)
    }
}

@Composable
private fun Panel(
    avatarColor: String, avatarSize: androidx.compose.ui.unit.Dp, boss: Boolean,
    enraged: Boolean, floater: Floater?,
    title: @Composable () -> Unit, bar: @Composable () -> Unit,
) {
    val gg = LocalGG.current
    Box {
        Row(
            Modifier.fillMaxWidth().ggCard(16.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BattleAvatar(avatarColor, avatarSize, boss, enraged)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                title()
                bar()
            }
        }
        floater?.let { f ->
            key(f.id) { FloatText(f.text, Modifier.align(Alignment.TopEnd).padding(end = 16.dp)) }
        }
    }
}

@Composable
fun BattleAvatar(colorHex: String, size: androidx.compose.ui.unit.Dp, boss: Boolean, enraged: Boolean) {
    val gg = LocalGG.current
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(CircleShape).background(hexColor(colorHex))
                .then(if (enraged) Modifier.border(2.dp, gg.danger, CircleShape) else Modifier)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(size * 0.12f)) {
            repeat(2) {
                Box(Modifier.size(size * 0.18f).clip(CircleShape).background(Color(0xFF20242C)),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(size * 0.07f).clip(CircleShape).background(Color.White))
                }
            }
        }
        if (boss) {
            Icon(Icons.Filled.EmojiEvents, null, tint = gg.warning,
                modifier = Modifier.size(size * 0.32f).offset(y = -size * 0.62f))
        }
    }
}

@Composable
private fun FloatText(text: String, modifier: Modifier = Modifier) {
    val gg = LocalGG.current
    var up by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { up = true }
    val offsetY by androidx.compose.animation.core.animateDpAsState(
        if (up) (-26).dp else 0.dp, androidx.compose.animation.core.tween(700), label = "float")
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        if (up) 0f else 1f, androidx.compose.animation.core.tween(700), label = "floatA")
    Text(text, style = suite(FontWeight.ExtraBold, 18), color = gg.danger.copy(alpha = alpha),
        modifier = modifier.offset(y = offsetY))
}

@Composable
private fun CounterTimer(startClock: Long, limitMs: Int) {
    val gg = LocalGG.current
    var left by remember(startClock) { mutableStateOf(limitMs.toLong()) }
    LaunchedEffect(startClock) {
        while (left > 0) {
            left = maxOf(0, limitMs - (SystemClock.elapsedRealtime() - startClock))
            delay(100)
        }
    }
    val urgent = left <= 2000
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            GaugeBar(left.toDouble() / limitMs, height = 8.dp, color = if (urgent) gg.danger else gg.accent)
        }
        Text("%.1fs".format(left / 1000.0), style = suite(FontWeight.ExtraBold, 11),
            color = if (urgent) gg.danger else gg.textMuted)
    }
}

// MARK: 인트로 / 결과 오버레이

@Composable
private fun IntroOverlay(engine: BattleEngine) {
    val gg = LocalGG.current
    val rule = when (engine.style) {
        BattleStyle.SPEED -> "먼저 ${Battle.RACE_TARGET}문제를 맞히면 승리!"
        BattleStyle.COUNTER -> "${engine.counterLimit / 1000}초 안에 못 풀면 반격당해요!"
        BattleStyle.HP -> if (engine.isBoss) "HP가 절반이 되면 분노해요 — 조심!" else "정답이면 공격, 오답이면 반격!"
    }
    Column(
        Modifier.fillMaxSize().background(gg.bg.copy(alpha = 0.95f)).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BattleAvatar(engine.npc.color, 88.dp, engine.isBoss, false)
        Text(engine.npc.name, style = suite(FontWeight.ExtraBold, 20), color = gg.text)
        Text("“${engine.npc.greeting}”", style = suite(FontWeight.Bold, 14), color = gg.textMuted,
            textAlign = TextAlign.Center)
        Box(Modifier.clip(CircleShape).background(gg.surface2).padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text("${Battle.styleName(engine.style)} — $rule", style = suite(FontWeight.ExtraBold, 12), color = gg.textMuted)
        }
        Row(
            Modifier.clip(CircleShape).background(gg.danger).padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Shield, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text("대결 시작!", style = suite(FontWeight.ExtraBold, 18), color = Color.White)
        }
    }
}

@Composable
private fun ResultOverlay(engine: BattleEngine, end: BattleEnd, onWorld: () -> Unit, onRetry: () -> Unit) {
    val gg = LocalGG.current
    val adventure = LocalAdventure.current
    val worldCleared = engine.isBoss && engine.npc.table == 9
    val nextRegion = if (engine.isBoss) World.region(engine.npc.table + 1) else null

    Box(Modifier.fillMaxSize().background(gg.bg.copy(alpha = 0.95f)).padding(24.dp),
        contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(gg.surface).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BattleAvatar(
                if (end.won) adventure.equippedColorHex else engine.npc.color,
                72.dp, engine.isBoss && !end.won, false)
            Text(
                if (end.won) (if (worldCleared) "월드 클리어! 👑" else "승리! 🎉") else "아쉬운 패배…",
                style = suite(FontWeight.ExtraBold, 24), color = gg.text)
            Text(
                if (end.won) {
                    if (worldCleared) "모든 지역을 정복했어요 — 진정한 구구단 정복자!"
                    else "${engine.npc.name}을(를) 물리쳤어요!"
                } else "괜찮아요, 답을 익히고 다시 도전해요",
                style = suite(FontWeight.Bold, 14), color = gg.textMuted, textAlign = TextAlign.Center)

            if (end.won && engine.isBoss && !worldCleared && nextRegion != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(gg.accent.copy(alpha = 0.12f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LockOpen, null, tint = gg.accent, modifier = Modifier.size(14.dp))
                    Text("${nextRegion.name}(${nextRegion.table}단)이 열렸어요!",
                        style = suite(FontWeight.ExtraBold, 14), color = gg.accent)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ResultStat(Modifier.weight(1f), Icons.Filled.AutoAwesome, gg.warning, "획득 XP", "+${end.commit.xpEarned}")
                ResultStat(Modifier.weight(1f), Icons.Filled.LocalFireDepartment, gg.danger, "최고 콤보", "${engine.maxCombo}")
            }

            end.commit.table?.let { table ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(gg.surface2)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${table}단 마스터리", style = suite(FontWeight.Bold, 14), color = gg.text)
                        if (end.commit.improvedStars) Text("새 기록!", style = suite(FontWeight.Bold, 12), color = gg.accent)
                    }
                    StarsView(end.commit.newStars, size = 22.dp)
                }
            }

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!end.won) {
                    GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG, onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        Text("다시 도전")
                    }
                }
                GGButton(
                    variant = if (end.won) GGButtonVariant.PRIMARY else GGButtonVariant.SURFACE,
                    size = GGButtonSize.LG, onClick = onWorld,
                ) { Text("월드로 돌아가기") }
            }
        }
    }
}

@Composable
private fun ResultStat(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector,
                       tint: Color, label: String, value: String) {
    val gg = LocalGG.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(gg.surface2)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
            Text(label, style = suite(FontWeight.Bold, 11), color = gg.textMuted)
        }
        Text(value, style = suite(FontWeight.ExtraBold, 20), color = gg.text)
    }
}

@Composable
private fun StatBar(value: Double, label: String, color: Color) {
    val gg = LocalGG.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { GaugeBar(value, height = 12.dp, color = color) }
        Text(label, style = suite(FontWeight.ExtraBold, 11), color = gg.textMuted,
            modifier = Modifier.width(60.dp), textAlign = TextAlign.End)
    }
}
