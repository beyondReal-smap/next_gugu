package site.smap.gugudan.features.games.shared

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer

// 화면에 잠깐 뜨는 큰 알림 글자 (Unity 시험판 Banner.cs 이식, iOS Banner.swift 대응) — A 보물·높이 구간·신기록, B 도감 등록·새 몬스터.
// 떠 있는 동안 온 알림은 앞 알림이 끝난 뒤 보여 준다. 대기는 최신 1개 — 아주 빨리 풀 때 지난 알림이 줄줄이 밀려 나오지 않게.

class BannerState {
    /** 지금 떠 있는 알림(없으면 null) */
    var text: String? = null
        private set
    private var left = 0.0
    private var pending: String? = null

    fun show(message: String) {
        if (left > 0) {
            pending = message
            return
        }
        text = message
        left = SHOW_SEC
    }

    /** 판이 끝나거나 새 판: 떠 있는 것·기다리는 것 모두 지움 */
    fun clear() {
        text = null
        left = 0.0
        pending = null
    }

    fun update(dt: Double) {
        if (left <= 0) return
        left -= dt
        if (left > 0) return
        left = 0.0
        text = null
        val next = pending ?: return
        pending = null
        show(next)
    }

    companion object {
        const val SHOW_SEC = 1.4
    }
}

/** fontSize = 글자 크기(설계 px). 화면 폭을 넘는 긴 번역은 폭에 맞게 줄인다 */
fun DrawScope.drawBanner(state: BannerState, measurer: TextMeasurer, center: Offset, unit: Float, fontSize: Float = 84f) {
    val text = state.text ?: return
    drawOutlinedText(
        measurer, text, center, fontSize * unit, GamePalette.gold, GamePalette.ink, 6 * unit,
        maxWidth = size.width * 0.96f,
    )
}
