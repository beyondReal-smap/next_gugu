package site.smap.gugudan.features.learn

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import site.smap.gugudan.R
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.Hints
import site.smap.gugudan.core.ModeDef
import site.smap.gugudan.core.Modes
import site.smap.gugudan.core.PremiumConfig
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.features.games.Minigames
import site.smap.gugudan.features.home.modeIcon
import site.smap.gugudan.features.home.modeMeta
import site.smap.gugudan.services.rememberStrings
import site.smap.gugudan.store.LocalGame
import site.smap.gugudan.store.LocalPremium
import site.smap.gugudan.store.Minigame

// 단 시트 "7단, 어떻게 연습할까요?" / '섞어서' 시트 — 행을 누르면 바로 시작한다(고른 상태를 화면에 남기지 않는다).
// table null = '섞어서': 6개 모드 전부(60초·서바이벌은 여기에만)와 최고 기록을 보여 준다.
// 아래에 게임 2개(구구 계단·구구 몬스터) — 구구 몬스터는 2~9단만(10단 이상이면 회색 "9단까지", 눌러도 아무 일 없음).
// 안쪽 단계로 구구단 표(TableStudyContent)를 연다. 시스템 뒤로가기: 표 → 방식 고르기 → 시트 닫기.

/** 시트에서 고른 일 — 시트를 먼저 닫은 뒤 화면(세션·결제·미니게임)을 띄워야 시트 창 밑에 깔리지 않는다 */
sealed class PracticeChoice {
    data class Session(val mode: GameMode) : PracticeChoice()
    data object Locked : PracticeChoice()
    data class Game(val game: Minigame) : PracticeChoice()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeSheet(table: Int?, onDismiss: () -> Unit, onChoose: (PracticeChoice) -> Unit) {
    val gg = LocalGG.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    /** 안쪽 단계 — 구구단 표 보기·소리로 외우기 */
    var studying by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = gg.bg) {
        // 표 단계에서 뒤로가기 → 방식 고르기로 (시트 닫기는 시트가 맡는다)
        BackHandler(enabled = studying) { studying = false }
        if (studying && table != null) {
            TableStudyContent(
                table = table,
                onBack = { studying = false },
                onPractice = { onChoose(PracticeChoice.Session(GameMode.PRACTICE)) },
            )
        } else {
            PracticeOptions(table, onStudy = { studying = true }, onClose = onDismiss, onChoose = onChoose)
        }
    }
}

@Composable
private fun PracticeOptions(table: Int?, onStudy: () -> Unit, onClose: () -> Unit, onChoose: (PracticeChoice) -> Unit) {
    val gg = LocalGG.current
    val game = LocalGame.current
    val premium = LocalPremium.current
    val strings = rememberStrings()

    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().readableWidth(560.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 머리 — 단과 별(추천이면 배지) / '섞어서' 설명. 단이면 표·외우기, 오른쪽 끝은 닫기
        Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (table != null) {
                val stars = game.state.tableMastery[table]?.stars ?: 0
                val recommended = table == Hints.nextRoadmapTable(game.state.tableMastery)
                val headerLabel = stringResource(
                    if (recommended) R.string.times_table_stars_recommended else R.string.times_table_stars, table, stars,
                )
                Column(
                    Modifier.weight(1f).clearAndSetSemantics { contentDescription = headerLabel; heading() },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.table, table), style = suite(FontWeight.ExtraBold, 30), color = gg.text)
                        if (recommended) {
                            Text(stringResource(R.string.pick), style = suite(FontWeight.ExtraBold, 11), color = gg.accentFg,
                                modifier = Modifier.clip(CircleShape).background(gg.accent)
                                    .padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                    StarsView(stars, size = 15.dp)
                }
                PressableCard(onClick = onStudy) {
                    Row(
                        Modifier.heightIn(min = 44.dp).wrapContentHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.height(36.dp).clip(CircleShape).background(gg.accent.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = gg.accent, modifier = Modifier.size(15.dp))
                            Text(stringResource(R.string.table_and_recite), style = suite(FontWeight.ExtraBold, 13),
                                color = gg.accent, maxLines = 1)
                        }
                    }
                }
            } else {
                Row(
                    Modifier.weight(1f).semantics(mergeDescendants = true) { heading() },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(gg.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Shuffle, null, tint = gg.accent, modifier = Modifier.size(20.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.mix_tables), style = suite(FontWeight.ExtraBold, 24), color = gg.text)
                        Text(stringResource(R.string.an_even_mix_of_tables_2_9), style = suite(FontWeight.Medium, 13), color = gg.textMuted)
                    }
                }
            }
            PressableCard(onClick = onClose) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(gg.surface2), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, stringResource(R.string.close), tint = gg.textMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Text(
            stringResource(R.string.how_do_you_want_to_practice), style = suite(FontWeight.ExtraBold, 18), color = gg.text,
            modifier = Modifier.semantics { heading() },
        )
        // 맨 위(차근차근)가 기본이라 강조한다
        Modes.options(table).forEachIndexed { index, def ->
            val locked = PremiumConfig.isPremiumMode(def.id) && !premium.isPremium
            ModeOptionRow(
                def = def,
                meta = modeMeta(strings, def.id),
                // '섞어서' 시트는 기록 도전 모드의 최고 기록을 함께 보여 준다
                best = if (def.scored) game.state.bestScores[def.id] ?: 0 else 0,
                locked = locked,
                primary = index == 0,
                onClick = { onChoose(if (locked) PracticeChoice.Locked else PracticeChoice.Session(def.id)) },
            )
        }

        // 게임 — 이 단(또는 섞어서)을 골라 둔 채로 시작
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.games_group_title), style = suite(FontWeight.ExtraBold, 16), color = gg.text,
                modifier = Modifier.semantics { heading() })
            Text(
                table?.let { stringResource(R.string.starts_with_the_times_table, it) }
                    ?: stringResource(R.string.starts_with_mixed_tables),
                style = suite(FontWeight.Medium, 12), color = gg.textMuted,
            )
        }
        Minigame.entries.chunked(2).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { g ->
                    MinigameTile(g, table, Modifier.weight(1f).fillMaxHeight()) { onChoose(PracticeChoice.Game(g)) }
                }
            }
        }
    }
}

/** 모드 한 줄 — 누르면 바로 시작(잠겼으면 결제 화면) */
@Composable
private fun ModeOptionRow(def: ModeDef, meta: String, best: Int, locked: Boolean, primary: Boolean, onClick: () -> Unit) {
    val gg = LocalGG.current
    val tint = ModeStyle.tint(def.id)
    val name = stringResource(def.nameRes)
    // 낭독 — "이름, 규칙" + 잠김 / 최고 기록 (iOS rowLabel 과 같은 구성)
    val base = "$name, $meta"
    val label = when {
        locked -> stringResource(R.string.locked_lifetime_pass_needed, base)
        best > 0 -> pluralStringResource(R.plurals.best_points_2, best, base, best)
        else -> base
    }
    PressableCard(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = label
            role = Role.Button
            onClick { onClick(); true }
        },
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(if (primary) gg.accent.copy(alpha = 0.08f) else gg.surface)
                .border(if (primary) 1.5.dp else 1.dp, if (primary) gg.accent.copy(alpha = 0.6f) else gg.border, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center) {
                Icon(modeIcon(def.id), null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = suite(FontWeight.ExtraBold, 16), color = gg.text)
                Text(stringResource(def.taglineRes), style = suite(FontWeight.Medium, 12), color = gg.textMuted, maxLines = 2)
            }
            if (locked) {
                Row(
                    Modifier.clip(CircleShape).background(gg.accent.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Lock, null, tint = gg.accent, modifier = Modifier.size(11.dp))
                    Text(stringResource(R.string.lifetime_pass), style = suite(FontWeight.ExtraBold, 11), color = gg.accent, maxLines = 1)
                }
            } else {
                if (best > 0) {
                    Text(
                        stringResource(R.string.best, best), style = suite(FontWeight.ExtraBold, 11), color = gg.warning, maxLines = 1,
                        modifier = Modifier.clip(CircleShape).background(gg.warning.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(if (primary) gg.accent else tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = if (primary) gg.accentFg else tint, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * 게임 타일 — 고른 단을 못 쓰는 게임(구구 몬스터 10단 이상)은 회색으로 두고 "9단까지"만 알린다(누르면 아무 일 없음).
 * 쓸 수 있으면 배지(무료 "N문제 체험", 이용권 "새 게임")
 */
@Composable
private fun MinigameTile(game: Minigame, table: Int?, modifier: Modifier, onClick: () -> Unit) {
    val gg = LocalGG.current
    val enabled = game.supports(table)
    val name = stringResource(Minigames.nameRes(game))
    val note = if (enabled) Minigames.badge(rememberStrings(), LocalPremium.current.isPremium)
        else stringResource(R.string.monster_up_to_nine)
    val tint = if (enabled) Minigames.tint(game) else gg.textMuted
    val lockedLabel = stringResource(R.string.game_tile_up_to_nine_label, name)
    PressableCard(
        modifier = modifier.then(
            if (enabled) Modifier.semantics(mergeDescendants = true) {}
            else Modifier.clearAndSetSemantics {
                contentDescription = lockedLabel
                disabled()
            },
        ),
        enabled = enabled,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxSize().heightIn(min = 64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (enabled) tint.copy(alpha = 0.06f) else gg.surface2)
                .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Minigames.iconBg(game))
                    .alpha(if (enabled) 1f else 0.5f).padding(3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(Minigames.thumbnail(game)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                    colorFilter = if (enabled) null else GRAYSCALE,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, style = suite(FontWeight.ExtraBold, 14), color = if (enabled) gg.text else gg.textMuted, maxLines = 1)
                Text(
                    note, style = suite(FontWeight.ExtraBold, 10), color = tint, maxLines = 1,
                    modifier = Modifier.clip(CircleShape).background(tint.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** 잠긴 게임 타일 썸네일 — 채도 0(회색) */
private val GRAYSCALE = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
