package site.smap.gugudan.features.home

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import site.smap.gugudan.R
import site.smap.gugudan.core.games.MissionItem
import site.smap.gugudan.core.games.MissionKind
import site.smap.gugudan.core.games.MissionText
import site.smap.gugudan.core.games.WeekStamp
import site.smap.gugudan.designsystem.ConfettiView
import site.smap.gugudan.designsystem.GGButton
import site.smap.gugudan.designsystem.GGButtonSize
import site.smap.gugudan.designsystem.GGButtonVariant
import site.smap.gugudan.designsystem.LocalGG
import site.smap.gugudan.designsystem.ggCard
import site.smap.gugudan.designsystem.phraseBreak
import site.smap.gugudan.designsystem.suite
import site.smap.gugudan.features.games.Minigames
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Sound
import site.smap.gugudan.services.rememberStrings
import site.smap.gugudan.store.LocalPlayRewards
import site.smap.gugudan.store.Minigame
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle

// 홈 "오늘의 미션" 카드 (iOS DailyMissionsCard 대응, .claude/plans/daily-missions.md) — 날마다 바뀌는 미션 3개 /
// 다 하면 [보물상자 열기!](별 +10, 꽃가루) / 이번 주 도장(월~일). 진행도는 PlayRewardsStore.missionBoard 가 rewards 에서 읽어
// 게임에서 돌아오면 바로 바뀐다. "오늘"은 앱으로 돌아올 때마다 다시 읽는다(어제 띄워 둔 앱을 아침에 열어도 새 미션). 낭독은 미션 부분 한 덩어리("오늘의 미션, 3개 중 N개 완료") + 버튼·도장 줄 따로.

@Composable
fun DailyMissionsCard() {
    val gg = LocalGG.current
    val rewards = LocalPlayRewards.current
    val strings = rememberStrings()
    var today by remember { mutableStateOf(LocalDate.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = LocalDate.now()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val board = rewards.missionBoard(today)
    // 이번 화면에서 상자를 열고 받은 별 — 다른 탭에 다녀오거나 날이 바뀌면 "내일 새 미션이 와요!" 만 남는다
    var received by rememberSaveable(today) { mutableStateOf<Int?>(null) }
    var confetti by remember { mutableIntStateOf(0) }
    val summary = stringResource(R.string.mission_board_label, board.items.size, board.doneCount)

    Box(Modifier.fillMaxWidth().ggCard()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier.clearAndSetSemantics { contentDescription = summary },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.mission_title), style = suite(FontWeight.ExtraBold, 17), color = gg.text,
                        modifier = Modifier.weight(1f))
                    Chest(open = board.chestOpened, ready = board.chestReady, popKey = received)
                }
                board.items.forEach { MissionRow(it, MissionText.title(strings, it.mission)) }
            }

            when {
                board.chestReady -> GGButton(
                    variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG,
                    onClick = {
                        received = rewards.openChest(today)
                        confetti += 1
                        Sound.complete()
                        Haptics.success()
                    },
                ) { Text(stringResource(R.string.mission_open_chest)) }
                board.chestOpened -> Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    received?.let {
                        Text(pluralStringResource(R.plurals.mission_chest_received, it, it), style = suite(FontWeight.ExtraBold, 15),
                            color = gg.warning, textAlign = TextAlign.Center)
                    }
                    Text(stringResource(R.string.mission_come_back_tomorrow), style = suite(FontWeight.Bold, 13).phraseBreak(),
                        color = gg.textMuted, textAlign = TextAlign.Center)
                }
                else -> Text(stringResource(R.string.mission_chest_hint), style = suite(FontWeight.Bold, 13).phraseBreak(),
                    color = gg.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            WeekStamps(board.week)
        }
        // 꽃가루는 카드 안에서만 — 카드를 덮어도 터치는 막지 않는다(Canvas 만 그림)
        ConfettiView(confetti, Modifier.matchParentSize())
    }
}

/** 보물상자 — 준비되면 통통 튀고(동작 줄이기 = 애니메이션 길이 0 이면 멈춤), 열면 열린 그림으로 바뀌며 스프링으로 커진다 */
@Composable
private fun Chest(open: Boolean, ready: Boolean, popKey: Int?) {
    val reduceMotion = rememberReduceMotion()
    val bounce = if (ready && !reduceMotion) {
        rememberInfiniteTransition(label = "chest").animateFloat(
            0f, 1f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bounce",
        )
    } else {
        null   // 튀지 않음 — 준비 전·연 뒤·동작 줄이기
    }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(popKey) {
        if (popKey == null) return@LaunchedEffect
        pop.snapTo(0.5f)
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Image(
        painterResource(if (open) R.drawable.game_chest_open else R.drawable.game_chest_closed), null,
        Modifier.size(56.dp).graphicsLayer {
            val b = bounce?.value ?: 0f
            translationY = -b * 8.dp.toPx()
            scaleX = pop.value * (1f + b * 0.05f)
            scaleY = pop.value * (1f - b * 0.03f)
        },
        contentScale = ContentScale.Fit,
    )
}

/** 시스템 "애니메이션 삭제"(접근성) — 애니메이터 길이 배율 0. 설정한 적 없는 기기는 키가 없고 기본 배율은 1 */
@Composable
private fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

@Composable
private fun MissionRow(item: MissionItem, title: String) {
    val gg = LocalGG.current
    val target = item.mission.target
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        MissionIcon(item.mission.kind)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = suite(FontWeight.Bold, 14), color = gg.text, modifier = Modifier.weight(1f))
                Text("${item.progress}/$target", style = suite(FontWeight.ExtraBold, 13),
                    color = if (item.done) gg.success else gg.textMuted, maxLines = 1, softWrap = false)
            }
            GaugeBar(item.progress.toDouble() / target, height = 8.dp, color = if (item.done) gg.success else gg.accent)
        }
        // 다 하면 초록 체크 도장, 아직이면 빈 동그라미
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .background(if (item.done) gg.success else Color.Transparent)
                .border(2.dp, if (item.done) gg.success else gg.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (item.done) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

/** 미션 아이콘 — 계단·몬스터는 게임 카드 썸네일, 연속은 불꽃, 별은 코인 */
@Composable
private fun MissionIcon(kind: MissionKind) {
    val gg = LocalGG.current
    when (kind) {
        MissionKind.STAIRS_STEPS -> IconTile(Minigames.iconBg(Minigame.STAIRS)) { TileImage(Minigames.thumbnail(Minigame.STAIRS)) }
        MissionKind.MONSTERS_DEFEATED -> IconTile(Minigames.iconBg(Minigame.MONSTER)) { TileImage(Minigames.thumbnail(Minigame.MONSTER)) }
        MissionKind.BEST_STREAK -> IconTile(gg.danger.copy(alpha = 0.12f)) {
            Icon(Icons.Filled.LocalFireDepartment, null, tint = gg.danger, modifier = Modifier.size(22.dp))
        }
        MissionKind.STARS_EARNED -> IconTile(gg.warning.copy(alpha = 0.15f)) { TileImage(R.drawable.game_icon_coin) }
    }
}

@Composable
private fun IconTile(bg: Color, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(4.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun TileImage(@DrawableRes res: Int) {
    Image(painterResource(res), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
}

/** 이번 주 도장 — 월~일 동그라미 7개(요일 글자는 앱 언어의 요일 약자), 도장 찍힌 날은 별, 오늘은 테두리 강조 */
@Composable
private fun WeekStamps(week: List<WeekStamp>) {
    val gg = LocalGG.current
    val locale = LocalConfiguration.current.locales[0]
    val title = stringResource(R.string.mission_week_stamps)
    // 낭독: "이번 주 도장, 월요일, 화요일" — 도장 찍힌 요일만(요일 이름은 OS 현지 이름이라 새 문구가 아님)
    val label = (listOf(title) + week.filter { it.stamped }.map { it.date.dayOfWeek.getDisplayName(DateTextStyle.FULL_STANDALONE, locale) })
        .joinToString(", ")
    Column(Modifier.clearAndSetSemantics { contentDescription = label }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = suite(FontWeight.Bold, 13), color = gg.textMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            week.forEach { day ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(day.date.dayOfWeek.getDisplayName(DateTextStyle.NARROW_STANDALONE, locale),
                        style = suite(FontWeight.Bold, 11), color = if (day.isToday) gg.accent else gg.textMuted)
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(if (day.stamped) gg.warning else gg.surface2)
                            .then(if (day.isToday) Modifier.border(2.5.dp, gg.accent, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day.stamped) Icon(Icons.Filled.Star, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
