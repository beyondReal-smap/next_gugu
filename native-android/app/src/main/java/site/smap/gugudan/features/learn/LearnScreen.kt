package site.smap.gugudan.features.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import site.smap.gugudan.R
import site.smap.gugudan.core.Achievements
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.Hints
import site.smap.gugudan.core.Modes
import site.smap.gugudan.core.Problems
import site.smap.gugudan.designsystem.*
import site.smap.gugudan.store.LocalGame
import site.smap.gugudan.store.LocalPremium
import site.smap.gugudan.store.LocalRouter
import site.smap.gugudan.store.LocalSession
import site.smap.gugudan.store.MinigameStart

// 학습 탭 — "단 먼저": 오늘 추천 카드 + 3×3 단 지도(2~9단 + '섞어서').
// 단을 누르면 "어떻게 연습할까요?" 시트가 열리고, 시트의 행을 누르면 바로 시작한다 — 화면에 '고른 모드' 상태를 두지 않는다.

/** 단 시트로 연 대상 — Mix = '섞어서' */
private sealed class SheetTarget {
    data class Table(val table: Int) : SheetTarget()
    data object Mix : SheetTarget()
}

@Composable
fun LearnScreen() {
    val gg = LocalGG.current
    val game = LocalGame.current
    val router = LocalRouter.current
    val session = LocalSession.current
    val premium = LocalPremium.current

    var sheet by remember { mutableStateOf<SheetTarget?>(null) }
    val totalStars = Achievements.totalStars(game.state)
    val tables = (Problems.MIN_TABLE..Problems.MAX_TABLE).toList()
    // 로드맵상 다음에 익힐 단 — 모두 별 3개면 null (추천 카드는 '섞어서'로 바뀐다)
    val recommended = Hints.nextRoadmapTable(game.state.tableMastery)

    Column(
        Modifier.fillMaxSize().background(gg.bg)
            // 상태바 영역은 스크롤 밖에 둔다 — 스크롤한 내용이 시계·배터리 밑으로 비치지 않게
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .readableWidth()
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 헤더
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.learn), style = suite(FontWeight.ExtraBold, 24), color = gg.text,
                modifier = Modifier.weight(1f).semantics { heading() })
            Pill(bg = gg.warning.copy(alpha = 0.12f), fg = gg.warning) {
                Icon(Icons.Filled.EmojiEvents, null, Modifier.size(14.dp))
                Text("$totalStars/${tables.size * 3}")
            }
        }

        RecommendCard()

        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.choose_a_table), style = suite(FontWeight.Bold, 14), color = gg.textMuted,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.tap_a_table_to_choose_how_to_practice), style = suite(FontWeight.Medium, 11),
                color = gg.textMuted.copy(alpha = 0.85f))
        }
        // 3×3 — 2~9단 + '섞어서'(9번째 칸)
        val cells: List<SheetTarget> = tables.map { SheetTarget.Table(it) } + SheetTarget.Mix
        cells.chunked(3).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { cell ->
                    val cellModifier = Modifier.weight(1f).fillMaxHeight()
                    when (cell) {
                        is SheetTarget.Table -> TableTile(cell.table, cell.table == recommended, cellModifier) { sheet = cell }
                        SheetTarget.Mix -> MixTile(cellModifier) { sheet = cell }
                    }
                }
            }
        }
    }

    sheet?.let { target ->
        val table = (target as? SheetTarget.Table)?.table
        PracticeSheet(
            table = table,
            onDismiss = { sheet = null },
            // 시트를 먼저 닫아야 세션·결제·게임 화면이 시트 창 밑에 깔리지 않는다
            onChoose = { choice ->
                sheet = null
                when (choice) {
                    is PracticeChoice.Session -> session.start(choice.mode, table)
                    PracticeChoice.Locked -> premium.openPaywall()
                    is PracticeChoice.Game -> router.openMinigame(choice.game, MinigameStart.Table(table))
                }
            },
        )
    }
}

/**
 * 오늘 추천 단 — 로드맵(2·5·3·4·6·9·7·8단)에서 다음에 익힐 단을 '차근차근'으로 바로 시작한다 (학습 탭 맨 위·홈 공통).
 * 모든 단이 별 3개면 '섞어서'로 바뀐다.
 */
@Composable
fun RecommendCard() {
    val gg = LocalGG.current
    val game = LocalGame.current
    val session = LocalSession.current
    val table = Hints.nextRoadmapTable(game.state.tableMastery)
    val stars = table?.let { game.state.tableMastery[it]?.stars } ?: 0
    val practice = Modes.def(GameMode.PRACTICE)
    val title = table?.let { stringResource(R.string.table, it) } ?: stringResource(R.string.mix_tables)
    val rule = stringResource(practice.nameRes) + " · " + pluralStringResource(R.plurals.problems, practice.total, practice.total)
    val subtitle = if (table == null) stringResource(R.string.s_3_stars_on_every_table_2, rule) else rule
    val label = stringResource(R.string.today_s_pick, title, subtitle)
    PressableCard(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = label
            onClick { session.start(GameMode.PRACTICE, table); true }
        },
        onClick = { session.start(GameMode.PRACTICE, table) },
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(gg.accent.copy(alpha = 0.08f))
                .border(1.5.dp, gg.accent.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    Modifier.clip(CircleShape).background(gg.accent).padding(horizontal = 9.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = gg.accentFg, modifier = Modifier.size(11.dp))
                    Text(stringResource(R.string.todays_pick), style = suite(FontWeight.ExtraBold, 11), color = gg.accentFg)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = suite(FontWeight.ExtraBold, 26), color = gg.text, maxLines = 1)
                    if (table != null) StarsView(stars, size = 15.dp)
                }
                Text(subtitle, style = suite(FontWeight.Bold, 13), color = gg.textMuted)
            }
            Box(Modifier.size(52.dp).clip(CircleShape).background(gg.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = gg.accentFg, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun TableTile(table: Int, recommended: Boolean, modifier: Modifier, onOpen: () -> Unit) {
    val gg = LocalGG.current
    val game = LocalGame.current
    val stars = game.state.tableMastery[table]?.stars ?: 0
    val label = stringResource(
        if (recommended) R.string.times_table_stars_recommended else R.string.times_table_stars, table, stars,
    )
    PressableCard(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = label
            onClick { onOpen(); true }
        },
        onClick = onOpen,
    ) {
        // '추천' 배지는 테두리 위에 걸친다 — 칸 안에 넣으면 그 줄만 키가 커지고, en "Table 2"·ja 처럼 긴 글자와 겹쳤다
        Box {
            Column(
                Modifier.fillMaxSize().heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (recommended) gg.accent.copy(alpha = 0.08f) else gg.surface)
                    .border(if (recommended) 1.5.dp else 1.dp,
                        if (recommended) gg.accent.copy(alpha = 0.6f) else gg.border, RoundedCornerShape(20.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.table, table), style = suite(FontWeight.ExtraBold, 24), color = gg.text,
                    maxLines = 1, softWrap = false)
                StarsView(stars, size = 13.dp)
            }
            if (recommended) {
                Text(stringResource(R.string.pick), style = suite(FontWeight.ExtraBold, 10), color = gg.accentFg, maxLines = 1,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-8).dp).clip(CircleShape).background(gg.accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun MixTile(modifier: Modifier, onOpen: () -> Unit) {
    val gg = LocalGG.current
    val label = stringResource(R.string.mixed_tables_2_9_evenly)
    PressableCard(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = label
            onClick { onOpen(); true }
        },
        onClick = onOpen,
    ) {
        Column(
            Modifier.fillMaxSize().heightIn(min = 64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(gg.accent.copy(alpha = 0.05f))
                .border(1.dp, gg.accent.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.height(30.dp), contentAlignment = Alignment.CenterStart) {
                Icon(Icons.Filled.Shuffle, null, tint = gg.accent, modifier = Modifier.size(22.dp))
            }
            Text(stringResource(R.string.mix_tables), style = suite(FontWeight.ExtraBold, 15), color = gg.text, maxLines = 1)
        }
    }
}
