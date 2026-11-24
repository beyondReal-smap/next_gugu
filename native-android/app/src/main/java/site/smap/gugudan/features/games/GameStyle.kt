package site.smap.gugudan.features.games

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import site.smap.gugudan.R
import site.smap.gugudan.core.Strings
import site.smap.gugudan.core.games.MonsterKind
import site.smap.gugudan.designsystem.GGColors
import site.smap.gugudan.features.games.monster.MonsterArt
import site.smap.gugudan.features.games.shared.GameArt
import site.smap.gugudan.features.paywall.MinigameTrial
import site.smap.gugudan.store.Minigame

// 게임(구구 계단·구구 몬스터)의 이름·소개·썸네일·색·배지 (iOS GameStyle.swift 대응) — 홈 "게임" 묶음과 학습 탭 단 시트가
// 같은 순서(Minigame 선언 순)·정의를 쓴다. (2.9.0 에서 놀이 4종 정의를 이 두 게임으로 바꾸고 features/home/Minigames.kt 에서 옮겼다)

object Minigames {
    @StringRes
    fun nameRes(game: Minigame): Int = when (game) {
        Minigame.STAIRS -> R.string.stairs_title
        Minigame.MONSTER -> R.string.monster_title
    }

    /** 카드 한 줄 소개 */
    @StringRes
    fun descRes(game: Minigame): Int = when (game) {
        Minigame.STAIRS -> R.string.stairs_card_desc
        Minigame.MONSTER -> R.string.monster_card_desc
    }

    /** 카드 썸네일 — 게임 그림. 계단은 점프하는 구구, 몬스터는 처음 만나는 초록 몬스터 */
    @DrawableRes
    fun thumbnail(game: Minigame): Int = when (game) {
        Minigame.STAIRS -> GameArt.GuguPose.JUMP.res
        Minigame.MONSTER -> MonsterArt.res(MonsterKind.GREEN)
    }

    /** 썸네일 받침 색 — 각 게임 배경(하늘·숲)의 첫인상 */
    fun iconBg(game: Minigame): Color = when (game) {
        Minigame.STAIRS -> Color(0xFFCFE9FF)
        Minigame.MONSTER -> Color(0xFF1E3A2C)
    }

    fun tint(game: Minigame): Color = when (game) {
        Minigame.STAIRS -> GGColors.sky
        Minigame.MONSTER -> GGColors.emerald
    }

    /** 카드 배지 — 무료 사용자에게는 체험 범위("N문제 체험"), 이용권 사용자에게는 새 게임 표시 */
    fun badge(strings: Strings, isPremium: Boolean): String =
        MinigameTrial.badge(strings, isPremium) ?: strings.get(R.string.new_game)
}
