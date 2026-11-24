package site.smap.gugudan.features.games.shared

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import site.smap.gugudan.R
import site.smap.gugudan.core.games.StarRules
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// 연속 정답 불꽃 배지 "×N" (Unity 시험판 ComboBadge.cs 이식, iOS ComboBadge.swift 대응) — 2연속부터 캐릭터(A)·몬스터(B) 옆에 붙어 다닌다.
// 숫자가 오를 때마다 통통 튀고, 5의 배수(별 보너스)에서는 더 크게 튄다. 오답이면 쪼그라들며 사라진다.

class ComboState {
    var streak = 0
        private set
    /** 화면 좌표(px) — 게임이 프레임마다 캐릭터 옆으로 옮김 */
    var position: Offset = Offset.Zero
        private set
    /** 그리는 중인지(쪼그라드는 동안 포함) */
    var visible = false
        private set
    private var pop = 0.0
    private var popAmount = 0.0
    /** 1 = 완전히 숨김 */
    private var hide = 1.0
    private var time = 0.0

    fun reset() {
        streak = 0
        position = Offset.Zero
        visible = false
        pop = 0.0
        popAmount = 0.0
        hide = 1.0
        time = 0.0
    }

    fun setStreak(newStreak: Int) {
        if (newStreak == streak) return
        val up = newStreak > streak
        streak = newStreak
        if (newStreak < SHOW_FROM) return   // 숨김은 update 에서 쪼그라들며
        visible = true
        hide = 0.0
        if (up) {
            pop = 1.0
            popAmount = if (StarRules.isComboMilestone(newStreak)) 0.55 else 0.28
        }
    }

    fun follow(point: Offset) {
        if (!visible) return   // 숨어 있으면 계산하지 않음
        position = point
    }

    fun update(dt: Double) {
        if (!visible) return
        time += dt
        if (streak < SHOW_FROM) {
            hide = min(1.0, hide + dt / HIDE_SEC)
            if (hide >= 1) {
                visible = false
                return
            }
        }
        pop = max(0.0, pop - dt / POP_SEC)
    }

    val scale: Float get() = ((1 + popAmount * sin(pop * PI)) * (1 - hide)).toFloat()
    /** 불꽃이 살랑(도) */
    val wobbleDegrees: Float get() = (sin(time * 6) * 4).toFloat()

    companion object {
        const val SHOW_FROM = 2
        const val POP_SEC = 0.32
        const val HIDE_SEC = 0.2
    }
}

private const val COMBO_SIZE = 180f
/** 불꽃 그림 가운데 노란 원의 중심(그림 높이 대비, 실측 -0.078) — "×N" 을 원 안에 */
private const val COMBO_CIRCLE_CENTER_Y = -0.078f

fun DrawScope.drawComboBadge(state: ComboState, images: GameImages, measurer: TextMeasurer, unit: Float) {
    if (!state.visible || state.scale <= 0f) return
    val s = COMBO_SIZE * unit
    val c = state.position
    scale(state.scale, pivot = c) {
        rotate(-state.wobbleDegrees, pivot = c) {
            drawGameImageFit(images[R.drawable.game_combo_flame], c, Size(s, s))
            drawGameText(measurer, "×${state.streak}", Offset(c.x, c.y - COMBO_CIRCLE_CENTER_Y * s), 62 * unit, GamePalette.ink)
        }
    }
}
