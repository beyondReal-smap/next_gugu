package site.smap.gugudan.features.home

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import site.smap.gugudan.R
import site.smap.gugudan.designsystem.GGColors
import site.smap.gugudan.designsystem.LocalGG
import site.smap.gugudan.features.paywall.MinigameTrial
import site.smap.gugudan.store.Minigame

// 움직이며 놀기 게임 표시 정보 — 홈 카드·단 시트·'섞어서' 시트가 같은 순서(Minigame 선언 순)·이름·아이콘·배지를 쓴다.
// 예전에는 홈(바구니→점프→레인)과 학습 탭(점프→레인→바구니)의 순서·설명이 달랐다.

object Minigames {
    @StringRes
    fun nameRes(game: Minigame): Int = when (game) {
        Minigame.POP -> R.string.gugu_pop
        Minigame.BASKET -> R.string.gugu_basket
        Minigame.RUNNER -> R.string.gugu_jump
        Minigame.LANE -> R.string.gugu_lanes
    }

    @StringRes
    fun descRes(game: Minigame): Int = when (game) {
        Minigame.POP -> R.string.pop_only_the_number_balloons_that_fit
        Minigame.BASKET -> R.string.move_left_and_right_to_catch_the_right
        Minigame.RUNNER -> R.string.pick_the_right_answer_and_hop_over_the
        Minigame.LANE -> R.string.switch_lanes_to_dodge_then_zip_into_the
    }

    fun icon(game: Minigame): ImageVector = when (game) {
        Minigame.POP -> Icons.Filled.BubbleChart
        Minigame.BASKET -> Icons.Filled.ShoppingBasket
        Minigame.RUNNER -> Icons.Filled.DirectionsRun
        Minigame.LANE -> Icons.Filled.ViewStream
    }

    fun iconBg(game: Minigame): Color = when (game) {
        Minigame.POP -> Color(0xFF2B3A7A)
        Minigame.BASKET -> Color(0xFF794124)
        Minigame.RUNNER, Minigame.LANE -> Color(0xFF153F35)
    }

    fun iconFg(game: Minigame): Color = when (game) {
        Minigame.POP -> Color(0xFFFFD6E7)
        Minigame.BASKET -> Color(0xFFFFE7A3)
        Minigame.RUNNER, Minigame.LANE -> Color(0xFFDBEF9E)
    }

    @Composable
    fun tint(game: Minigame): Color = when (game) {
        Minigame.POP -> GGColors.sky
        Minigame.BASKET -> LocalGG.current.warning
        Minigame.RUNNER, Minigame.LANE -> GGColors.emerald
    }

    /**
     * 카드 배지 — 무료 사용자에게는 체험 범위(팡팡은 첫 웨이브, 나머지는 N문제),
     * 이용권 사용자에게는 이번에 새로 나온 팡팡에만 "새 게임" (예전에는 이용권 사용자에게도 바구니·레인에 "새 모드"가 계속 붙었다)
     */
    @Composable
    fun badge(game: Minigame, isPremium: Boolean): String? = when {
        game == Minigame.POP && isPremium -> stringResource(R.string.new_game)
        game == Minigame.POP -> stringResource(R.string.first_wave_free)
        else -> MinigameTrial.badge(isPremium)
    }
}
