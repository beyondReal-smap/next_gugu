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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import site.smap.gugudan.R
import site.smap.gugudan.core.Problem
import site.smap.gugudan.core.Strings
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.store.PlayRoundRecord

// 판이 끝난 뒤 결과 창 (Unity 시험판 ResultPanel.cs 이식, iOS GameResultPanel.swift 대응): 젤리 카드 — 메모·오늘의 미션 한 줄 / 주 점수 /
// 보조 점수·받은 별(좌우 두 칸)·받은 XP·모은 별 / 다시 볼 식 하나 / [다시 하기](0.6초 뒤 활성 — 화면을 끌던 손가락이 바로 누르지 않게) /
// 카드 밖 아래 작은 [나가기].

data class GameResultContent(
    /** 예: "새 기록!", "최고 기록 23" (없으면 빈 문자열) */
    val note: String,
    /** 오늘의 미션 한 줄 — "미션 완료! 계단 15칸 오르기" 등 (MissionText.resultNote, 없으면 빈 문자열) */
    val missionNote: String,
    /** 예: "오른 계단" */
    val mainLabel: String,
    val mainValue: Int,
    /** 예: "보물", "최고 연속" */
    val subLabel: String,
    val subValue: Int,
    /** 다시 볼 식(없으면 null) */
    val miss: Problem?,
    /** 그 아래 아이가 낸 답 한 줄 — 완성된 문구(A "고른 답 7", B "고른 카드 2 × 6 = 12"), 없으면 null */
    val missGiven: String?,
    /** 이번 판에 받은 별 */
    val stars: Int,
    /** 받은 별을 넣은 뒤 모은 별 */
    val starTotal: Int,
    /** 학습 기록으로 받은 XP — 답이 없어 기록하지 않은 판은 null(줄을 그리지 않음) */
    val xpEarned: Int?,
) {
    companion object {
        /** 결과 메모: 새 기록이면 "새 기록!", 지난 기록이 있으면 "최고 기록 N" */
        fun note(record: PlayRoundRecord, strings: Strings): String = when {
            record.isRecord -> strings.get(R.string.new_best)
            record.previousBest > 0 -> strings.get(R.string.game_result_best, record.previousBest)
            else -> ""
        }
    }
}

object GameResultLayout {
    /** 결과 창이 뜬 뒤 [다시 하기]가 눌리기까지(초) */
    const val ARM_DELAY_SEC = 0.6
}

/** restartArmed = [다시 하기]를 누를 수 있는지(결과가 뜬 뒤 0.6초) */
@Composable
fun GameResultPanel(
    viewport: GameViewport,
    content: GameResultContent,
    restartArmed: Boolean,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    val images = LocalGameImages.current
    val measurer = rememberGameTextMeasurer()
    val u = viewport.unit
    val card = viewport.screenCenter(0f, 60f)
    val starsEarned = stringResource(R.string.game_result_stars_earned)
    val starTotal = stringResource(R.string.game_result_star_total, content.starTotal)
    val review = stringResource(R.string.game_result_review)
    val missText = content.miss?.let { "${it.a} × ${it.b} = ${it.a * it.b}" }
    val picked = content.missGiven
    val noMiss = stringResource(R.string.game_result_no_miss)
    // 숫자와 XP 단위는 번역하지 않는다(iOS 결과 창 "+N XP" 와 같음)
    val xpText = content.xpEarned?.let { "+$it XP" }
    val summary = listOfNotNull(
        content.note.ifEmpty { null }, content.missionNote.ifEmpty { null }, "${content.mainLabel} ${content.mainValue}", "${content.subLabel} ${content.subValue}",
        "$starsEarned +${content.stars}", starTotal, xpText, review, missText ?: noMiss, picked,
    ).joinToString(". ")
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).blockTouches())
        Box(
            Modifier
                .placed(card, viewport.px(920f, 1260f))
                .semantics { contentDescription = summary }
                .drawBehind {
                    // 아래 입술(72px)이 [다시 하기]에 걸리지 않게 1260 높이
                    drawJelly(images, Rect(Offset.Zero, size), GamePalette.card, u)
                    val c = Offset(size.width / 2, size.height / 2)
                    // 카드 가운데 기준 설계 좌표(위가 +)에 한 줄 글자 — 칸 폭(가운데 860·좌우 420)을 넘으면 절반까지 줄임
                    fun label(text: String, px: Float, color: Color, x: Float = 0f, y: Float) = drawGameText(
                        measurer, text, Offset(c.x + x * u, c.y - y * u), px * u, color,
                        maxWidth = (if (x == 0f) 860f else 420f) * u, minScale = 0.5f,
                    )
                    if (content.missionNote.isEmpty()) {
                        label(content.note, 48f, GamePalette.gold, y = 545f)
                        label(content.mainLabel, 56f, GamePalette.textDim, y = 450f)
                    } else {
                        // 미션 줄이 있으면 메모를 위로 올리고 그 아래(주 점수 이름 위)에 한 줄 — 위 테두리·이름과 간격을 둔다
                        label(content.note, 48f, GamePalette.gold, y = 570f)
                        label(content.missionNote, 40f, GamePalette.correct, y = 512f)
                        label(content.mainLabel, 56f, GamePalette.textDim, y = 445f)
                    }
                    label("${content.mainValue}", 190f, GamePalette.text, y = 310f)

                    // 왼쪽 칸: 보조 점수 / 오른쪽 칸: 받은 별(코인 + "+N") 과 모은 별
                    label(content.subLabel, 48f, GamePalette.textDim, x = -220f, y = 175f)
                    label("${content.subValue}", 96f, GamePalette.gold, x = -220f, y = 80f)
                    label(starsEarned, 48f, GamePalette.textDim, x = 220f, y = 175f)
                    drawGameImageFit(images[R.drawable.game_icon_coin], Offset(c.x + 135 * u, c.y - 80 * u), Size(88 * u, 88 * u))
                    drawLeadingText(measurer, "+${content.stars}", left = c.x + 185 * u, centerY = c.y - 80 * u, px = 96 * u,
                        color = GamePalette.gold, maxWidth = 260 * u)
                    label(starTotal, 38f, GamePalette.textDim, x = 220f, y = 5f)
                    // 왼쪽 칸 아래: 학습 기록 XP(앱 레벨) — 오른쪽 "모은 별" 과 같은 줄
                    if (xpText != null) label(xpText, 38f, GamePalette.textDim, x = -220f, y = 5f)

                    drawRect(GamePalette.wall, Offset(c.x - 380 * u, c.y + 50 * u - 2 * u), Size(760 * u, 4 * u))

                    label(review, 44f, GamePalette.textDim, y = -120f)
                    if (missText != null) {
                        label(missText, 92f, GamePalette.correct, y = -215f)
                        if (picked != null) label(picked, 44f, GamePalette.wrong, y = -300f)
                    } else {
                        label(noMiss, 92f, GamePalette.correct, y = -215f)
                    }
                },
        )
        JellyButton(
            stringResource(R.string.game_play_again), GamePalette.correct, GamePalette.background,
            fontSize = 84f, jellyScale = 0.9f, unit = u, enabled = restartArmed,
            modifier = Modifier.placed(Offset(card.x, card.y + 455 * u), viewport.px(760f, 200f)),
            onClick = onRestart,
        )
        // 카드 밖 아래 작은 버튼 — 아이 손이 닿는 [다시 하기]와 떨어뜨려 둔다
        val exit = stringResource(R.string.exit)
        Box(
            Modifier
                .placed(viewport.screenCenter(0f, -640f), viewport.px(420f, 110f))
                .semantics { contentDescription = exit }
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                    Haptics.impactLight()
                    onExit()
                }
                .drawBehind {
                    // 진한 둥근 받침 — 뒤에 깔린 답 카드 위에서도 글자가 겹쳐 보이지 않게
                    val pill = CornerRadius(size.height / 2)
                    drawRoundRect(GamePalette.background.copy(alpha = 0.95f), cornerRadius = pill)
                    val line = 4 * u
                    drawRoundRect(
                        GamePalette.wall, topLeft = Offset(line / 2, line / 2), size = Size(size.width - line, size.height - line),
                        cornerRadius = CornerRadius((size.height - line) / 2), style = Stroke(line),
                    )
                    drawGameText(measurer, exit, Offset(size.width / 2, size.height / 2), 46 * u, GamePalette.text, maxWidth = size.width)
                },
        )
    }
}

/** 왼쪽 끝을 맞춘 한 줄 글자(결과 창 "+N") — 칸 폭을 넘으면 절반까지 줄임(왼쪽 끝 기준) */
private fun DrawScope.drawLeadingText(
    measurer: TextMeasurer,
    text: String,
    left: Float,
    centerY: Float,
    px: Float,
    color: Color,
    maxWidth: Float,
) {
    val layout = measureGameText(measurer, text, gameTextStyle(px, color))
    val w = layout.size.width.toFloat()
    val fit = if (w > maxWidth) maxOf(0.5f, maxWidth / w) else 1f
    drawGameText(measurer, text, Offset(left + w * fit / 2, centerY), px, color, maxWidth = maxWidth, minScale = 0.5f)
}
