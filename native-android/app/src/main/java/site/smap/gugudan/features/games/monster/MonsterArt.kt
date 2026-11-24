package site.smap.gugudan.features.games.monster

import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import site.smap.gugudan.R
import site.smap.gugudan.core.games.MonsterKind

// 구구 몬스터 그림 정보 (Unity 시험판 GameArt.Monster·MonsterTint·MonsterPanelCenter·Stump·Forest 이식, iOS MonsterArt.swift 대응)
// 몬스터 그림은 8종 × (보통·기절). 파일 이름 = game_monster_<종>(_defeated), 종 순서 = MonsterKind(도감 칸 순서).
// 원본은 512px 높이(Unity PPU 512 → 1유닛)이고, 빨강만 날개 때문에 캔버스가 가로로 넓다(몸 배율은 같음).

object MonsterArt {
    @DrawableRes
    fun res(kind: MonsterKind, defeated: Boolean = false): Int = when (kind) {
        MonsterKind.GREEN -> if (defeated) R.drawable.game_monster_green_defeated else R.drawable.game_monster_green
        MonsterKind.ORANGE -> if (defeated) R.drawable.game_monster_orange_defeated else R.drawable.game_monster_orange
        MonsterKind.PURPLE -> if (defeated) R.drawable.game_monster_purple_defeated else R.drawable.game_monster_purple
        MonsterKind.SKY -> if (defeated) R.drawable.game_monster_sky_defeated else R.drawable.game_monster_sky
        MonsterKind.PINK -> if (defeated) R.drawable.game_monster_pink_defeated else R.drawable.game_monster_pink
        MonsterKind.RED -> if (defeated) R.drawable.game_monster_red_defeated else R.drawable.game_monster_red
        MonsterKind.NIGHT -> if (defeated) R.drawable.game_monster_night_defeated else R.drawable.game_monster_night
        MonsterKind.GOLD -> if (defeated) R.drawable.game_monster_gold_defeated else R.drawable.game_monster_gold
    }

    /** 몬스터 그림 원본 px 크기 — 높이 512 기준으로 폭을 늘려 그린다 */
    fun pixels(kind: MonsterKind, defeated: Boolean = false): Size = when {
        kind == MonsterKind.RED && !defeated -> Size(580f, 512f)
        kind == MonsterKind.RED -> Size(560f, 512f)
        else -> Size(512f, 512f)
    }

    /** 터짐 고리처럼 몬스터 색이 필요한 곳 */
    fun tint(kind: MonsterKind): Color = when (kind) {
        MonsterKind.GREEN -> Color(0xFF3CC28A)
        MonsterKind.ORANGE -> Color(0xFFFF9A3C)
        MonsterKind.PURPLE -> Color(0xFFA77BFF)
        MonsterKind.SKY -> Color(0xFF4CC3F0)
        MonsterKind.PINK -> Color(0xFFFF8FC8)
        MonsterKind.RED -> Color(0xFFFF6B5B)
        MonsterKind.NIGHT -> Color(0xFF4B55D6)
        MonsterKind.GOLD -> Color(0xFFFFC93C)
    }

    /** 몬스터 배의 빈 크림색 패널 중심 — 그림 높이 대비, 중심 기준(y 위가 +). 8종 모두 같음(실측 오차 1% 이내) */
    val PANEL_CENTER = Offset(0f, -0.17f)

    val STUMP_PIXELS = Size(265f, 112f)
    val FOREST_PIXELS = Size(1024f, 1536f)

    /** 미리 풀어 둘 몬스터 그림(8종 × 보통·기절) */
    val ALL_MONSTERS: List<Int> = MonsterKind.entries.flatMap { listOf(res(it), res(it, defeated = true)) }
}
