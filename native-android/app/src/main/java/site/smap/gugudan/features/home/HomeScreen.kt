package site.smap.gugudan.features.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.ArrowOutward
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
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.Level
import site.smap.gugudan.core.ModeKind
import site.smap.gugudan.core.Modes
import site.smap.gugudan.core.Strings
import site.smap.gugudan.core.adventure.AdvProgress
import site.smap.gugudan.core.adventure.World
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.features.paywall.MinigameTrial
import site.smap.gugudan.services.rememberStrings
import site.smap.gugudan.store.*

// 홈 (iOS HomeView 이식)

// 놀이 방식별 묶음 (웹 /play 구성과 동일) — 세션 모드는 전체 랜덤으로 바로 시작한다
private val LEARN_MODES = listOf(GameMode.MISSING, GameMode.TRUEFALSE)
private val RECORD_MODES = listOf(GameMode.TIME_ATTACK, GameMode.CHALLENGE, GameMode.SURVIVAL)

fun modeIcon(mode: GameMode): ImageVector = when (mode) {
    GameMode.PRACTICE -> Icons.AutoMirrored.Filled.MenuBook
    GameMode.TIME_ATTACK -> Icons.Filled.Timer
    GameMode.CHALLENGE -> Icons.Filled.Bolt
    GameMode.SURVIVAL -> Icons.Filled.Favorite
    GameMode.MISSING -> Icons.Filled.Extension
    GameMode.TRUEFALSE -> Icons.Filled.Scale
    GameMode.ADVENTURE -> Icons.Filled.Shield
}

@Composable
fun HomeScreen() {
    val gg = LocalGG.current
    val game = LocalGame.current
    val session = LocalSession.current
    val adventure = LocalAdventure.current
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
                stringResource(R.string.from_learning_to_running_it_s_all_times),
                style = suite(FontWeight.Medium, 12), color = gg.textMuted,
            )
        }

        // 차근차근 배우기
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.learn_step_by_step),
                stringResource(R.string.learn_how_it_works_no_time_limit), gg.accent)
            val practiceTotal = Modes.def(GameMode.PRACTICE).total
            ModeRow(
                icon = modeIcon(GameMode.PRACTICE), tint = ModeStyle.tint(GameMode.PRACTICE),
                name = stringResource(Modes.def(GameMode.PRACTICE).nameRes),
                tagline = stringResource(R.string.pick_any_table_and_go_step_by_step),
                meta = pluralStringResource(R.plurals.problems_pick_a_table, practiceTotal, practiceTotal), locked = false,
                onClick = { router.tab = AppTab.LEARN },
            )
            LEARN_MODES.forEach { mode ->
                SessionModeRow(mode) { if (premium.gate(mode)) session.start(mode, null) }
            }
        }

        // 기록 도전
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader(Icons.Filled.EmojiEvents, stringResource(R.string.beat_your_best),
                stringResource(R.string.beat_your_best_with_speed_and_focus), gg.warning)
            RECORD_MODES.forEach { mode ->
                SessionModeRow(mode) { if (premium.gate(mode)) session.start(mode, null) }
            }
        }

        // 직접 움직이며 놀기
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader(Icons.Filled.DirectionsRun, stringResource(R.string.move_play),
                stringResource(R.string.run_dodge_and_catch_the_answers), GGColors.emerald)
            GameCard(
                title = stringResource(R.string.gugu_basket), badge = MinigameTrial.badge(premium.isPremium) ?: stringResource(R.string.new_mode),
                desc = stringResource(R.string.move_left_and_right_to_catch_the_right),
                icon = Icons.Filled.ShoppingBasket, iconBg = Color(0xFF794124), iconFg = Color(0xFFFFE7A3),
                tint = gg.warning, onClick = { router.basketOpen = true },
            )
            GameCard(
                title = stringResource(R.string.gugu_jump), badge = MinigameTrial.badge(premium.isPremium),
                desc = stringResource(R.string.pick_the_right_answer_and_hop_over_the),
                icon = Icons.Filled.DirectionsRun, iconBg = Color(0xFF153F35), iconFg = Color(0xFFDBEF9E),
                tint = GGColors.emerald, onClick = { router.runnerOpen = true },
            )
            GameCard(
                title = stringResource(R.string.gugu_lanes), badge = MinigameTrial.badge(premium.isPremium) ?: stringResource(R.string.new_mode),
                desc = stringResource(R.string.switch_lanes_to_dodge_then_zip_into_the),
                icon = Icons.Filled.ViewStream, iconBg = Color(0xFF153F35), iconFg = Color(0xFFDBEF9E),
                tint = GGColors.emerald, onClick = { router.laneOpen = true },
            )
        }

        // 모험
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GroupHeader(Icons.Filled.Map, stringResource(R.string.adventure_2),
                stringResource(R.string.explore_and_battle_in_a_3d_world), GGColors.indigo)
            AdventureHero(
                onClick = { if (premium.isPremium) adventure.openAdventure() else premium.openPaywall() },
            )
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

/** 러너·레인·바구니 진입 카드 */
@Composable
private fun GameCard(
    title: String,
    badge: String?,
    desc: String,
    icon: ImageVector,
    iconBg: Color,
    iconFg: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    val gg = LocalGG.current
    PressableCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(tint.copy(alpha = 0.06f))
                .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconFg, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = suite(FontWeight.ExtraBold, 16), color = gg.text)
                    if (badge != null) {
                        Text(
                            badge, style = suite(FontWeight.ExtraBold, 10), color = tint,
                            modifier = Modifier.clip(CircleShape).background(tint.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(desc, style = suite(FontWeight.Medium, 13), color = gg.textMuted)
            }
            Icon(Icons.Filled.ArrowOutward, null, tint = tint, modifier = Modifier.size(18.dp))
        }
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

/** 카드에 적는 규칙 요약 (문제 수 / 제한 시간 / 하트) */
private fun modeMeta(strings: Strings, mode: GameMode): String {
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

@Composable
private fun AdventureHero(onClick: () -> Unit) {
    val adventure = LocalAdventure.current
    val premium = LocalPremium.current
    val (defeated, total) = AdvProgress.totalStats(adventure.progress)
    val nextRegion = World.regions.firstOrNull {
        World.bossId(it.table) !in adventure.progress.defeatedNpcs
    }

    PressableCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(GGColors.indigo, GGColors.violet, GGColors.purple)))
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Map, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Text(stringResource(R.string.adventure), style = suite(FontWeight.ExtraBold, 11), color = Color.White)
                    if (!premium.isPremium) Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
                // 일본어는 글자 단위로 줄이 바뀌어 "たんけんし/て" 처럼 끊긴다 — 구(문절) 단위 줄바꿈 (CJK 에만 적용)
                Text(stringResource(R.string.explore_a_3d_world_and_battle_with_times),
                    style = suite(FontWeight.ExtraBold, 20).copy(
                        lineHeight = 26.sp, lineBreak = LineBreak.Simple.copy(wordBreak = LineBreak.WordBreak.Phrase),
                    ), color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(12.dp))
                    Text(
                        // iOS 와 같이 "격파 n/m" 뒤에 "· 다음 모험 …"을 한 칸 띄워 잇는다
                        stringResource(R.string.defeated, defeated, total) +
                            (nextRegion?.let { " " + stringResource(R.string.next, stringResource(it.nameRes)) } ?: ""),
                        style = suite(FontWeight.Bold, 12), color = Color.White.copy(alpha = 0.9f), maxLines = 1,
                    )
                }
                // 지역 진행 도트
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                    World.regions.forEach { r ->
                        val cleared = World.bossId(r.table) in adventure.progress.defeatedNpcs
                        Box(
                            Modifier.height(6.dp).width(if (cleared) 16.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (cleared) Color.White else Color.White.copy(alpha = 0.35f))
                        )
                    }
                }
            }
            // 마스코트 — 캡슐+눈
            Mascot()
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun Mascot() {
    Box(Modifier.size(width = 52.dp, height = 74.dp).clip(RoundedCornerShape(50)).background(Color.White),
        contentAlignment = Alignment.TopCenter) {
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(2) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF20242C)),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(4.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
    }
}
