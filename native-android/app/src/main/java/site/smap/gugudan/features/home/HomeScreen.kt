package site.smap.gugudan.features.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stairs
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import site.smap.gugudan.R
import site.smap.gugudan.core.Commit
import site.smap.gugudan.features.settings.DAILY_GOAL_OPTIONS
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.Level
import site.smap.gugudan.core.ModeKind
import site.smap.gugudan.core.Modes
import site.smap.gugudan.core.Strings
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.designsystem.rememberMotionSeconds
import site.smap.gugudan.designsystem.rememberReduceMotion
import site.smap.gugudan.features.games.Minigames
import site.smap.gugudan.features.games.shared.Motion
import site.smap.gugudan.features.learn.RecommendCard
import site.smap.gugudan.services.rememberStrings
import site.smap.gugudan.store.*

// 홈 (iOS HomeView 이식)

// 기록 도전 묶음 — 원래 단 없이 시작하는 모드라 전체 랜덤으로 바로 시작한다
private val RECORD_MODES = listOf(GameMode.TIME_ATTACK, GameMode.CHALLENGE, GameMode.SURVIVAL)

fun modeIcon(mode: GameMode): ImageVector = when (mode) {
    GameMode.PRACTICE -> Icons.AutoMirrored.Filled.MenuBook
    GameMode.TIME_ATTACK -> Icons.Filled.Timer
    GameMode.CHALLENGE -> Icons.Filled.Bolt
    GameMode.SURVIVAL -> Icons.Filled.Favorite
    GameMode.MISSING -> Icons.Filled.Extension
    GameMode.TRUEFALSE -> Icons.Filled.Scale
    GameMode.ADVENTURE -> Icons.Filled.Shield
    GameMode.STAIRS -> Icons.Filled.Stairs
    GameMode.MONSTER -> Icons.Filled.Pets
}

@Composable
fun HomeScreen() {
    val gg = LocalGG.current
    val game = LocalGame.current
    val session = LocalSession.current
    val premium = LocalPremium.current
    val router = LocalRouter.current

    val state = game.state
    val level = game.levelInfo
    val goalPct = if (state.dailyGoal > 0) state.dailyCorrect.toDouble() / state.dailyGoal else 0.0
    val weakCount = state.wrongPool.size

    var showGoalSheet by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(gg.bg)
            // 상태바 영역은 스크롤 밖에 둔다 — 스크롤한 내용이 시계·배터리 밑으로 비치지 않게
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .readableWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 헤더
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.hi_there), style = suite(FontWeight.Bold, 14), color = gg.textMuted)
                Text(stringResource(R.string.a_round_of_times_tables_today), style = suite(FontWeight.ExtraBold, 20), color = gg.text)
            }
            // 끊긴 연속 기록은 0으로 — 불이 꺼진 모습으로 오늘 다시 시작하게 한다
            val streak = Commit.activeStreak(state)
            val streakTint = if (streak > 0) gg.danger else gg.textMuted
            val streakLabel = if (streak > 0) pluralStringResource(R.plurals.practiced_days_in_a_row, streak, streak)
                else stringResource(R.string.no_streak_yet_let_s_start_today)
            Box(Modifier.clearAndSetSemantics {
                contentDescription = streakLabel
            }) {
                Pill(bg = streakTint.copy(alpha = 0.12f), fg = streakTint) {
                    Icon(
                        if (streak > 0) Icons.Filled.LocalFireDepartment else Icons.Outlined.LocalFireDepartment,
                        null, Modifier.size(14.dp),
                    )
                    Text(pluralStringResource(R.plurals.days, streak, streak))
                }
            }
        }

        // 오늘의 미션 — 날마다 바뀌는 미션 3개 + 보물상자 + 이번 주 도장. 매일 열 이유가 되게 헤더 바로 아래·게임 카드 위 (iOS HomeView 와 같은 순서)
        DailyMissionsCard()

        // 게임 — 구구 계단·구구 몬스터 + 구구 꾸미기(옷장). 순서·문구는 Minigames(features/games/GameStyle.kt) 정의 하나를 학습 탭 단 시트와 같이 쓴다.
        // 아이가 첫 화면에서 바로 누를 수 있게 헤더 바로 아래에 둔다 (2.9.0 — 예전엔 목록 맨 아래라 스크롤해야 보였다, iOS HomeView 와 같은 순서)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader(Icons.Filled.SportsEsports, stringResource(R.string.games_group_title),
                stringResource(R.string.games_group_desc), GGColors.emerald)
            // 큰 카드 두 장을 같은 폭·같은 키로 나란히 — 이름이 자동 축소돼도 두 카드 높이가 어긋나지 않게
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Minigame.entries.forEach { g ->
                    GameCard(g, isPremium = premium.isPremium, Modifier.weight(1f).fillMaxHeight()) { router.openMinigame(g) }
                }
            }
            WardrobeCard { router.openWardrobe() }
        }

        // 데일리 골 — 누르면 목표를 바꾼다. 낭독은 라벨 + TalkBack 동작 이름("두 번 탭하여 목표 바꾸기") —
        // 두 문장을 ". " 로 이어 붙이면 일본어에서 문장부호가 어긋나므로 힌트는 동작 이름으로 넘긴다
        val goalLabel = stringResource(R.string.today_s_goal_of_correct, state.dailyGoal, state.dailyCorrect)
        PressableCard(
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = goalLabel
            },
            onClickLabel = stringResource(R.string.change_goal),
            onClick = { showGoalSheet = true },
        ) {
        Row(
            Modifier.fillMaxWidth().ggCard().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(goalPct, size = 104.dp, stroke = 11.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.dailyCorrect}", style = suite(FontWeight.ExtraBold, 24), color = gg.text)
                    Text("/ ${state.dailyGoal}", style = suite(FontWeight.Bold, 11), color = gg.textMuted)
                }
            }
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.TrackChanges, null, tint = gg.accent, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.today_s_goal), style = suite(FontWeight.Bold, 14), color = gg.text)
                }
                val remaining = maxOf(0, state.dailyGoal - state.dailyCorrect)
                Text(
                    if (goalPct >= 1) stringResource(R.string.goal_reached_awesome)
                    else pluralStringResource(R.plurals.more_correct_answers_to_go, remaining, remaining),
                    style = suite(FontWeight.Medium, 14), color = gg.textMuted,
                )
                Text(stringResource(R.string.change_goal), style = suite(FontWeight.Bold, 12), color = gg.accent,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
        }

        // 레벨/XP
        Column(Modifier.fillMaxWidth().ggCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(gg.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${level.level}", style = suite(FontWeight.ExtraBold, 14), color = gg.accentFg)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Lv.${level.level}", style = suite(FontWeight.ExtraBold, 14), color = gg.text)
                    Text(stringResource(Level.titleRes(level.level)), style = suite(FontWeight.Bold, 12), color = gg.textMuted)
                }
                Text("${level.currentLevelXp}/${level.xpForNextLevel} XP", style = suite(FontWeight.Bold, 12), color = gg.textMuted)
            }
            GaugeBar(level.progress)
        }

        // 빠른 학습 시작
        GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG,
            onClick = { session.start(GameMode.PRACTICE, null) }) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(22.dp))
            Text(stringResource(R.string.quick_start))
        }

        // 취약 문제 복습
        if (weakCount > 0) {
            PressableCard(onClick = { session.startReview() }) {
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(gg.danger.copy(alpha = 0.1f))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(gg.danger.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = gg.danger, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.review_weak_spots), style = suite(FontWeight.Bold, 15), color = gg.text)
                        Text(pluralStringResource(R.plurals.retry_the_problems_you_found_tricky, weakCount, weakCount), style = suite(FontWeight.Normal, 13), color = gg.textMuted)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = gg.textMuted)
                }
            }
        }

        // 어떻게 놀아볼까요? — 놀이 방식별 묶음
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.how_do_you_want_to_play), style = suite(FontWeight.ExtraBold, 17), color = gg.text)
            Text(
                stringResource(R.string.from_learning_to_games_it_s_all_times),
                style = suite(FontWeight.Medium, 12), color = gg.textMuted,
            )
        }

        // 오늘 추천 단 — 예전 '차근차근 배우기' 묶음(학습/빈칸/OX)을 대신한다. 단별 연습 방식은 학습 탭 단 시트에서 고른다
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader(Icons.Filled.Star, stringResource(R.string.today_s_table),
                stringResource(R.string.one_table_at_a_time_in_roadmap_order), gg.accent)
            RecommendCard()
        }

        // 기록 도전
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader(Icons.Filled.EmojiEvents, stringResource(R.string.beat_your_best),
                stringResource(R.string.beat_your_best_with_speed_and_focus), gg.warning)
            RECORD_MODES.forEach { mode ->
                SessionModeRow(mode) { if (premium.gate(mode)) session.start(mode, null) }
            }
        }
    }

    if (showGoalSheet) DailyGoalSheet(onDismiss = { showGoalSheet = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyGoalSheet(onDismiss: () -> Unit) {
    val gg = LocalGG.current
    val game = LocalGame.current
    val reminder = LocalReminder.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val goal = game.state.dailyGoal
    val strings = rememberStrings()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = gg.bg) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.daily_goal), style = suite(FontWeight.ExtraBold, 22), color = gg.text)
                Text(stringResource(R.string.choose_how_many_correct_answers_to_aim),
                    style = suite(FontWeight.Medium, 14), color = gg.textMuted)
            }
            ChoiceChips(DAILY_GOAL_OPTIONS, goal, { strings.plural(R.plurals.answers, it, it) }) {
                game.setDailyGoal(it)
                reminder.reschedule(game.state)
            }
            Text(
                when {
                    goal < 15 -> stringResource(R.string.easy_one_round_10_problems_almost_does)
                    goal < 25 -> stringResource(R.string.normal_about_two_rounds_to_reach_it)
                    goal < 40 -> stringResource(R.string.hard_about_three_rounds_to_reach_it)
                    else -> stringResource(R.string.challenge_about_five_rounds_to_reach_it)
                },
                style = suite(FontWeight.Bold, 13), color = gg.accent,
            )
            GGButton(variant = GGButtonVariant.PRIMARY, size = GGButtonSize.LG, onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
    }
}

@Composable
private fun GroupHeader(icon: ImageVector, title: String, desc: String, tint: Color) {
    val gg = LocalGG.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Text(title, style = suite(FontWeight.Bold, 13), color = gg.textMuted)
        Text(desc, style = suite(FontWeight.Medium, 11), color = gg.textMuted.copy(alpha = 0.75f), maxLines = 1)
    }
}

/**
 * 게임 진입 카드 — 두 장을 나란히 놓는 큰 카드 (iOS gameCard). 글을 못 읽어도 고를 수 있게 게임 그림을 크게 두고,
 * 이름·배지(무료 "N문제 체험", 이용권 "새 게임")만 얹는다. 한 줄 소개는 화면에서 빼고 TalkBack 낭독으로 남긴다
 */
@Composable
private fun GameCard(game: Minigame, isPremium: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val gg = LocalGG.current
    val tint = Minigames.tint(game)
    val name = stringResource(Minigames.nameRes(game))
    val badge = Minigames.badge(rememberStrings(), isPremium)
    val desc = stringResource(Minigames.descRes(game))
    val label = "$name, $badge"
    val shape = RoundedCornerShape(22.dp)
    // 낭독 "이름, 배지" + 동작 이름(한 줄 소개) — iOS accessibilityLabel·Hint 와 같은 구성.
    // 두 문장을 ". " 로 이어 붙이지 않는다(일본어 문장부호가 어긋난다) — 데일리 골·구구 꾸미기 카드와 같은 방식
    PressableCard(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = label
            role = Role.Button
            onClick(label = desc) { onClick(); true }
        },
        onClick = onClick,
    ) {
        Column(
            Modifier.fillMaxSize()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.16f), tint.copy(alpha = 0.05f))))
                .border(1.5.dp, tint.copy(alpha = 0.35f), shape)
                .padding(horizontal = 10.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 그림이 천천히 숨 쉬듯 위아래·크기(두 카드 위상을 달리해 같이 움직이지 않게)
            GameThumbnail(
                Minigames.thumbnail(game), Minigames.iconBg(game), inset = 8.dp, size = 96.dp, radius = 24.dp,
                breathPhase = game.ordinal * Motion.CARD_BREATH_PHASE_STEP,
            )
            FitText(name, suite(FontWeight.ExtraBold, 17), gg.text, minScale = 0.6f)
            FitText(
                badge, suite(FontWeight.ExtraBold, 11), tint, minScale = 0.7f,
                Modifier.clip(CircleShape).background(tint.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** 구구 꾸미기 — 게임에서 모은 별로 장신구를 사서 씌운다(무료 사용자에게도 열림, 대표님 결정 Q5) */
@Composable
private fun WardrobeCard(onClick: () -> Unit) {
    val gg = LocalGG.current
    val stars = LocalPlayRewards.current.stars
    val tint = gg.warning
    val label = pluralStringResource(R.plurals.wardrobe_card_label, stars, stars)
    val openLabel = stringResource(R.string.wardrobe_card_open)
    // 낭독 "구구 꾸미기, 모은 별 N개" + 동작 이름 "옷장 열기" (iOS accessibilityLabel·Hint 와 같은 구성)
    PressableCard(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = label
            role = Role.Button
            onClick(label = openLabel) { onClick(); true }
        },
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().ggCard(18.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameThumbnail(R.drawable.game_icon_hanger, tint.copy(alpha = 0.15f), inset = 10.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.wardrobe_title), style = suite(FontWeight.ExtraBold, 17), color = gg.text)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.game_icon_coin), null, Modifier.size(16.dp), contentScale = ContentScale.Fit)
                    Text(pluralStringResource(R.plurals.wardrobe_card_stars, stars, stars),
                        style = suite(FontWeight.Bold, 13), color = gg.textMuted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.wardrobe_card_open), style = suite(FontWeight.ExtraBold, 13), color = tint,
                    maxLines = 1, softWrap = false)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = tint, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * 게임 그림 썸네일 — 둥근 받침 위에 그림을 맞춰 얹는다 (게임 큰 카드 96dp, 구구 꾸미기 60dp).
 * breathPhase 가 있으면 받침은 두고 그림만 숨 쉬듯 움직인다(Motion.cardBreath, 동작 줄이기면 멈춤)
 */
@Composable
private fun GameThumbnail(
    @DrawableRes res: Int,
    bg: Color,
    inset: Dp = 4.dp,
    size: Dp = 60.dp,
    radius: Dp = 16.dp,
    breathPhase: Double? = null,
) {
    val reduceMotion = rememberReduceMotion()
    val breathing = breathPhase != null && !reduceMotion
    val time = rememberMotionSeconds(running = breathing)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(bg).padding(inset),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(res), null,
            Modifier.fillMaxSize().graphicsLayer {
                if (!breathing) return@graphicsLayer
                val b = Motion.cardBreath(time.value, breathPhase)
                translationY = (-b.lift).toFloat().dp.toPx()
                scaleX = b.scale.toFloat()
                scaleY = b.scale.toFloat()
            },
            contentScale = ContentScale.Fit,
        )
    }
}

/** 세션 모드 한 줄 카드 (웹 모바일의 목록형과 동일) */
@Composable
private fun SessionModeRow(mode: GameMode, onClick: () -> Unit) {
    val game = LocalGame.current
    val premium = LocalPremium.current
    val def = Modes.def(mode)
    val best = if (def.scored) game.state.bestScores[mode] ?: 0 else 0
    val rule = modeMeta(rememberStrings(), mode)
    val meta = if (best > 0) pluralStringResource(R.plurals.best_pts, best, best, rule)
        else stringResource(R.string.random_mix, rule)

    ModeRow(
        icon = modeIcon(mode), tint = ModeStyle.tint(mode), name = stringResource(def.nameRes), tagline = stringResource(def.taglineRes),
        meta = meta, locked = !premium.isPremium && premium.isPremiumMode(mode), onClick = onClick,
    )
}

@Composable
private fun ModeRow(
    icon: ImageVector,
    tint: Color,
    name: String,
    tagline: String,
    meta: String,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val gg = LocalGG.current
    PressableCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().ggCard(16.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, style = suite(FontWeight.ExtraBold, 16), color = gg.text)
                Text(tagline, style = suite(FontWeight.Medium, 13), color = gg.textMuted, maxLines = 1)
                Text(meta, style = suite(FontWeight.Bold, 12), color = gg.textMuted.copy(alpha = 0.8f))
            }
            if (locked) {
                Icon(Icons.Filled.Lock, null, tint = gg.accent, modifier = Modifier.size(14.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = gg.textMuted, modifier = Modifier.size(16.dp))
        }
    }
}

/** 카드에 적는 규칙 요약 (문제 수 / 제한 시간 / 하트) — 학습 탭 단 시트 행 낭독에도 쓴다 */
fun modeMeta(strings: Strings, mode: GameMode): String {
    val def = Modes.def(mode)
    def.timeLimitMs?.let { if (def.kind == ModeKind.TIMED) return strings.plural(R.plurals.sec_limit, it / 1000, it / 1000) }
    def.lives?.let { if (def.kind == ModeKind.LIVES) return strings.plural(R.plurals.hearts, it, it) }
    return strings.plural(R.plurals.problems, def.total, def.total)
}

@Composable
fun GaugeBar(progress: Double, height: androidx.compose.ui.unit.Dp = 10.dp, color: Color? = null) {
    val gg = LocalGG.current
    val p by animateFloatAsState(progress.coerceIn(0.0, 1.0).toFloat(), spring(stiffness = 120f), label = "gauge")
    Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(gg.surface2)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(p).clip(CircleShape).background(color ?: gg.accent))
    }
}

@Composable
private fun ModeCard(mode: GameMode, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val gg = LocalGG.current
    val game = LocalGame.current
    val premium = LocalPremium.current
    val def = Modes.def(mode)
    val tint = ModeStyle.tint(mode)
    val best = if (def.scored) game.state.bestScores[mode] ?: 0 else 0

    PressableCard(modifier = modifier, onClick = onClick) {
        Box(Modifier.fillMaxWidth().ggCard(16.dp).padding(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center) {
                    Icon(modeIcon(mode), null, tint = tint, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(stringResource(def.nameRes), style = suite(FontWeight.ExtraBold, 14), color = gg.text)
                    Text(stringResource(def.taglineRes), style = suite(FontWeight.Normal, 12), color = gg.textMuted, maxLines = 1)
                }
            }
            if (!premium.isPremium) {
                Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape)
                    .background(gg.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, null, tint = gg.accent, modifier = Modifier.size(11.dp))
                }
            } else if (def.scored && best > 0) {
                Box(Modifier.align(Alignment.TopEnd).clip(CircleShape)
                    .background(gg.warning.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text(stringResource(R.string.best, best), style = suite(FontWeight.ExtraBold, 10), color = gg.warning)
                }
            }
        }
    }
}
