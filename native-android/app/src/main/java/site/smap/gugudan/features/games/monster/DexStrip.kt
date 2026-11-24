package site.smap.gugudan.features.games.monster

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import site.smap.gugudan.R
import site.smap.gugudan.core.games.MonsterDex
import site.smap.gugudan.core.games.MonsterKind
import site.smap.gugudan.features.games.shared.GameImages
import site.smap.gugudan.features.games.shared.drawGameImage
import site.smap.gugudan.features.games.shared.drawPill
import site.smap.gugudan.features.games.shared.multiplyFilter
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// 구구 몬스터 도감 띠 (Unity 시험판 DexStrip.cs 이식, iOS DexStrip.swift 대응) — 위쪽 배지 줄 아래 8칸(MonsterKind 순서).
// - 물리친 종: 제 색
// - 해금됐지만 아직 못 물리친 종: 진한 실루엣("저 몬스터는 누구지?")
// - 아직 해금 안 된 종: 옅은 실루엣 + 자물쇠
// 처음 물리치면 그 칸이 통통 튀며 색이 채워진다. 상태(DexStripState)는 엔진이 update 하고 drawDexStrip 은 그리기만 한다.

class DexStripState {
    enum class Look { SEEN, SILHOUETTE, LOCKED }

    var seenMask = 0
        private set
    var total = 0
        private set
    var popKind: MonsterKind? = null
        private set
    private var pop = 0.0

    /** 저장소 값을 그대로 보여 줌(해금되면 자물쇠도 풀림) */
    fun show(seenMask: Int, total: Int) {
        this.seenMask = seenMask
        this.total = total
    }

    /** 처음 물리친 종: 칸이 통통 튐(색은 show 가 채움). 앞 칸 튐이 덜 끝났으면 그 칸은 제자리로 */
    fun reveal(kind: MonsterKind) {
        popKind = kind
        pop = 1.0
    }

    fun resetPop() {
        popKind = null
        pop = 0.0
    }

    fun update(dt: Double) {
        if (popKind == null) return
        pop = max(0.0, pop - dt / POP_SEC)
        if (pop <= 0) popKind = null
    }

    fun look(kind: MonsterKind): Look {
        if (MonsterDex.isSeen(kind, seenMask)) return Look.SEEN
        return if (MonsterDex.isUnlocked(kind, total)) Look.SILHOUETTE else Look.LOCKED
    }

    fun scale(kind: MonsterKind): Float = if (kind == popKind) (1 + 0.4 * sin(pop * PI)).toFloat() else 1f

    companion object {
        const val POP_SEC = 0.5
    }
}

/** 띠 크기(설계 px) */
object DexStripLayout {
    const val HEIGHT = 92f
    const val SLOT = 80f
    const val GAP = 14f
    /** 몬스터 그림은 둘레 여백이 있어 칸보다 살짝 크게 */
    const val ICON_SCALE = 1.15f
    const val LOCK_SIZE = 36f
    val WIDTH: Float get() = MonsterDex.KINDS * SLOT + (MonsterDex.KINDS - 1) * GAP + 36
    /** Ink(0.17, 0.14, 0.31) — 실루엣 색, 투명도는 따로 */
    val INK = Color(0.17f, 0.14f, 0.31f)
    val SLOT_COLOR = Color(1f, 0.97f, 0.88f, 0.92f)
    /** 숲 위 옅은 어둠 */
    val BACK_COLOR = Color(0.10f, 0.16f, 0.12f)
}

/** center = 띠 가운데(화면 px), unit = 설계 px → 화면 px */
fun DrawScope.drawDexStrip(state: DexStripState, images: GameImages, center: Offset, unit: Float) {
    val l = DexStripLayout
    drawPill(images, center, Size(l.WIDTH * unit, l.HEIGHT * unit), l.BACK_COLOR, alpha = 0.5f)
    val s = l.SLOT * unit
    for (kind in MonsterKind.entries) {
        val slotCenter = Offset(center.x + (kind.ordinal - (MonsterDex.KINDS - 1) / 2f) * (l.SLOT + l.GAP) * unit, center.y)
        scale(state.scale(kind), pivot = slotCenter) {
            drawCircle(l.SLOT_COLOR, radius = s / 2, center = slotCenter)
            // 그림은 칸 안에 비율 유지(scaledToFit)로, 살짝 아래로
            val image = images[MonsterArt.res(kind)]
            val box = s * l.ICON_SCALE
            val k = min(box / image.width, box / image.height)
            val iconCenter = Offset(slotCenter.x, slotCenter.y + 2 * unit)
            val iconSize = Size(image.width * k, image.height * k)
            when (state.look(kind)) {
                DexStripState.Look.SEEN -> drawGameImage(image, iconCenter, iconSize)
                DexStripState.Look.SILHOUETTE -> drawGameImage(image, iconCenter, iconSize, alpha = 0.9f, colorFilter = multiplyFilter(l.INK))
                DexStripState.Look.LOCKED -> {
                    drawGameImage(image, iconCenter, iconSize, alpha = 0.4f, colorFilter = multiplyFilter(l.INK))
                    val lock = images[R.drawable.game_icon_lock]
                    drawGameImage(
                        lock, Offset(slotCenter.x + s * 0.3f, slotCenter.y + s * 0.28f),
                        fitSize(lock.width.toFloat(), lock.height.toFloat(), l.LOCK_SIZE * unit),
                    )
                }
            }
        }
    }
}

/** 정사각 상자(side)에 비율 유지로 맞춘 크기 */
private fun fitSize(width: Float, height: Float, side: Float): Size {
    val k = min(side / width, side / height)
    return Size(width * k, height * k)
}
