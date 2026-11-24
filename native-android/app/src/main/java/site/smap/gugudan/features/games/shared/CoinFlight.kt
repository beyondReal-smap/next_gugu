package site.smap.gugudan.features.games.shared

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import site.smap.gugudan.R
import kotlin.math.min

// 별 코인이 정답 자리(발판·몬스터)에서 위쪽 별 배지로 휘어 날아간다 (Unity 시험판 CoinFlight.cs 이식, iOS CoinFlight.swift 대응).
// 좌표는 화면 px. 도착점(배지 아이콘)은 프레임마다 다시 받아, 배지가 통통 튀거나 화면이 바뀌어도 따라간다.

class CoinFlightState {
    class Coin(
        val from: Offset,
        val control: Offset,
        /** 비행 진행률(0~1). 0 미만 = 출발 대기 */
        var t: Double,
        /** 이번 묶음에서 몇 번째인지 — 닿는 음이 조금씩 높아짐 */
        val index: Int,
    )

    /** 코인의 지금 위치·크기 배율 */
    data class Pose(val point: Offset, val scale: Float)

    private val flying = mutableListOf<Coin>()
    val coins: List<Coin> get() = flying
    /** 마지막 프레임의 도착점 */
    var target: Offset = Offset.Zero
        private set

    val inFlight: Int get() = flying.size

    fun clear() = flying.clear()

    /** from 에서 count 개(최대 8개)가 to 로 날아간다. unit = 설계 px → 화면 px */
    fun launch(from: Offset, to: Offset, count: Int, unit: Float) {
        require(count > 0) { "날릴 코인은 1개 이상: $count" }
        val n = min(count, MAX_COINS_PER_BURST)
        target = to
        for (i in 0 until n) {
            // 휘는 경로: 출발점 위쪽으로 솟았다가 배지로. 여러 개면 좌우로 조금씩 벌림
            val offset = (i - (n - 1) / 2f) * SPREAD * unit
            val control = Offset(from.x + (to.x - from.x) * 0.15f + offset, from.y + (to.y - from.y) * 0.7f)
            // t 는 진행률이라 출발 대기(초)를 진행률로 바꿔 음수로 둠
            flying.add(Coin(from, control, -i * STAGGER_SEC / FLY_SEC, i))
        }
    }

    /** dt 만큼 날리고, 이번에 배지에 닿은 코인의 묶음 순번을 돌려준다 */
    fun update(dt: Double, target: Offset): List<Int> {
        this.target = target
        if (flying.isEmpty()) return emptyList()
        val arrived = mutableListOf<Int>()
        val iterator = flying.iterator()
        while (iterator.hasNext()) {
            val coin = iterator.next()
            coin.t += dt / FLY_SEC
            if (coin.t >= 1) {
                arrived.add(coin.index)
                iterator.remove()
            }
        }
        return arrived
    }

    /** 코인의 지금 위치·크기 배율(대기 중이면 null) */
    fun pose(coin: Coin): Pose? {
        if (coin.t < 0) return null
        val t = (coin.t * coin.t * (3 - 2 * coin.t)).toFloat()   // 천천히 떠올라 빨라졌다가 배지에 쏙
        fun lerp(a: Offset, b: Offset) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        val point = lerp(lerp(coin.from, coin.control), lerp(coin.control, target))
        val grow = min(1.0, coin.t * 6)
        return Pose(point, (grow * (1.15 + (0.75 - 1.15) * coin.t)).toFloat())
    }

    companion object {
        /** 한 번에 너무 많이 날리면 화면이 어지러움(나머지는 배지가 한 번에 맞춤) */
        const val MAX_COINS_PER_BURST = 8
        const val FLY_SEC = 0.6
        const val STAGGER_SEC = 0.08
        /** 코인 크기(설계 px) */
        const val COIN_SIZE = 86f
        /** 여러 개일 때 경로를 좌우로 벌리는 간격(설계 px) */
        const val SPREAD = 54f
    }
}

fun DrawScope.drawCoinFlight(state: CoinFlightState, images: GameImages, unit: Float) {
    if (state.inFlight == 0) return
    val size = CoinFlightState.COIN_SIZE * unit
    val image = images[R.drawable.game_icon_coin]
    for (coin in state.coins) {
        val pose = state.pose(coin) ?: continue
        drawGameImageFit(image, pose.point, Size(size, size), pose.scale)
    }
}
