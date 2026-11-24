package site.smap.gugudan.features.games.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import site.smap.gugudan.R
import site.smap.gugudan.core.Strings
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.rememberStrings

// 게임 중 나가기 (Unity 시험판 PausePanel.cs 이식, iOS PausePanel.swift 대응):
// 위 배지 줄 왼쪽 끝 [⏸] → "잠깐 쉬는 중" 젤리 창([계속 하기] / [메뉴로 나가기]).
// 바로 나가는 버튼이 아니라 창을 한 번 거치는 이유: 아이가 연타·실수로 판을 잃지 않게. Android 뒤로 가기도 이 창을 연다.
// 창이 열려 있는 동안 게임은 입력·시간(A 먹구름, B 시계)을 멈춘다(엔진 몫). 별·기록은 결과 화면 때만 저장되므로
// 나가면 이번 판 별·기록이 사라진다고 알려 준다.

object PauseLayout {
    /** [⏸] 크기 = 배지 높이(설계 px) */
    const val BUTTON_SIZE = HudBadgeState.HEIGHT
    /** 배지 줄 왼쪽 끝(-526 ~ -416) — 각 게임의 첫 배지는 이보다 오른쪽에서 시작 */
    const val BUTTON_X = -471f
    /** 안전 영역 위 끝에서 배지 줄 중심까지(설계 px, 아래가 -). 아이콘이 통통 튀어도(1.18배) 안전 영역 위로 안 나감 */
    const val HUD_ROW_Y = -80f

    /** [⏸] 의 화면 사각형 — 이 위 탭은 게임 입력에서 뺀다(A 는 화면 x 로 칸을 고르므로 왼쪽 칸 점프로 샘) */
    fun buttonFrame(viewport: GameViewport): Rect =
        viewport.rect(viewport.safeTop(BUTTON_X, HUD_ROW_Y), BUTTON_SIZE, BUTTON_SIZE)

    /** 창 문구 — 이번 판에 받은 별이 있으면 별 수도 */
    fun message(strings: Strings, starsThisRound: Int): String =
        if (starsThisRound > 0) strings.plural(R.plurals.game_pause_lose_stars, starsThisRound, starsThisRound)
        else strings.get(R.string.game_pause_lose_record)
}

/**
 * [⏸] — 배지와 같은 크림 알약(정사각 → 동그라미) + 남색 막대 2개. 글꼴에 ⏸ 기호가 없을 수 있어 도형으로 그림.
 * 전체 화면 Box 안에서 배지 줄 왼쪽 끝에 스스로 자리 잡는다
 */
@Composable
fun PauseButton(viewport: GameViewport, onClick: () -> Unit) {
    val images = LocalGameImages.current
    val label = stringResource(R.string.game_pause_button)
    val u = viewport.unit
    val side = PauseLayout.BUTTON_SIZE * u
    Box(
        Modifier
            .placed(viewport.safeTop(PauseLayout.BUTTON_X, PauseLayout.HUD_ROW_Y), Size(side, side))
            .semantics { contentDescription = label }
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                Haptics.impactLight()
                onClick()
            }
            .drawBehind {
                drawPill(images, Rect(Offset.Zero, size))
                val barW = 18 * u
                val barH = 48 * u
                val gap = 12 * u
                val top = (size.height - barH) / 2
                val left = (size.width - barW * 2 - gap) / 2
                drawRect(GamePalette.ink, Offset(left, top), Size(barW, barH))
                drawRect(GamePalette.ink, Offset(left + barW + gap, top), Size(barW, barH))
            },
    )
}

/** "잠깐 쉬는 중" 창 — 어두운 막이 화면 전체(시스템 영역 포함)를 덮어 뒤 게임이 눌리지 않게 한다 */
@Composable
fun PausePanel(viewport: GameViewport, starsThisRound: Int, onResume: () -> Unit, onQuit: () -> Unit) {
    val images = LocalGameImages.current
    val measurer = rememberGameTextMeasurer()
    val title = stringResource(R.string.game_pause_title)
    val message = PauseLayout.message(rememberStrings(), starsThisRound)
    val u = viewport.unit
    // 아래 입술(72px)에 [메뉴로 나가기]가 걸리지 않게 860 높이
    val card = viewport.screenCenter(0f, 60f)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).blockTouches())
        Box(
            Modifier
                .placed(card, viewport.px(880f, 860f))
                .semantics { contentDescription = "$title. $message" }
                .drawBehind {
                    drawJelly(images, Rect(Offset.Zero, size), GamePalette.card, u)
                    val c = Offset(size.width / 2, size.height / 2)
                    drawGameText(measurer, title, Offset(c.x, c.y - 300 * u), 88 * u, GamePalette.text, maxWidth = size.width * 0.9f, minScale = 0.5f)
                    drawGameParagraph(measurer, message, Offset(c.x, c.y - 155 * u), 46 * u, GamePalette.gold, 820 * u, 140 * u)
                },
        )
        JellyButton(
            stringResource(R.string.game_keep_going), GamePalette.correct, GamePalette.background,
            fontSize = 84f, jellyScale = 0.9f, unit = u,
            modifier = Modifier.placed(Offset(card.x, card.y + 50 * u), viewport.px(760f, 200f)),
            onClick = onResume,
        )
        JellyButton(
            stringResource(R.string.game_back_to_menu), GamePalette.quit, GamePalette.ink,
            fontSize = 56f, jellyScale = 0.7f, unit = u,
            modifier = Modifier.placed(Offset(card.x, card.y + 280 * u), viewport.px(760f, 150f)),
            onClick = onQuit,
        )
    }
}
