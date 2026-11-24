package site.smap.gugudan.features.games.shared

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import site.smap.gugudan.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// 정답 순간 그 자리(밟은 발판·몬스터)에서 별·반짝이가 사방으로 터진다 (Unity 시험판 Sparkles.cs 이식, iOS Sparkles.swift 대응).
// 좌표는 게임 월드 단위(y 위가 +). 보상감용 연출이라 흩어지는 방향은 매번 무작위여도 된다(규칙·채점과 무관).

class SparklesState {
    class Particle(
        /** 반짝이(4갈래)와 노란 별을 번갈아 — 풀 순번이 짝수면 반짝이 */
        val isSparkle: Boolean,
    ) {
        var x = 0.0
        var y = 0.0
        var vx = 0.0
        var vy = 0.0
        var age = 0.0
        /** 월드 크기(유닛) */
        var size = 0.0
        var spin = 0.0
        var angle = 0.0
        var alive = false
    }

    val particles: List<Particle> = List(POOL_SIZE) { Particle(isSparkle = it % 2 == 0) }

    val aliveCount: Int get() = particles.count { it.alive }

    /** big: 콤보 5배수·보물처럼 큰 보상일 때 더 많이, 더 멀리 */
    fun burst(x: Double, y: Double, big: Boolean) {
        val count = if (big) 16 else 8
        val speed = if (big) 6.5 else 4.5
        var started = 0
        for (p in particles) {
            if (started >= count) break
            if (p.alive) continue
            val angle = Random.nextDouble(0.0, 2 * PI)
            val k = speed * Random.nextDouble(0.6, 1.0)
            p.vx = cos(angle) * k
            p.vy = (sin(angle) * 0.8 + 0.6) * k
            p.x = x
            p.y = y
            p.age = 0.0
            p.size = Random.nextDouble(0.28, if (big) 0.6 else 0.45)
            p.spin = Random.nextDouble(-360.0, 360.0)
            p.angle = 0.0
            p.alive = true
            started += 1
        }
    }

    fun reset() {
        particles.forEach { it.alive = false }
    }

    fun update(dt: Double) {
        for (p in particles) {
            if (!p.alive) continue
            p.age += dt
            if (p.age >= LIFE_SEC) {
                p.alive = false
                continue
            }
            p.vy -= GRAVITY * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
        }
    }

    companion object {
        const val POOL_SIZE = 40
        const val LIFE_SEC = 0.55
        const val GRAVITY = 9.0
    }
}

/** 살아 있는 입자를 그린다. toScreen = 월드 → 화면(px), unitPx = 월드 1유닛의 화면 px */
fun DrawScope.drawSparkles(state: SparklesState, images: GameImages, unitPx: Float, toScreen: (Double, Double) -> Offset) {
    val sparkle = images[R.drawable.game_sparkle]
    val star = images[R.drawable.game_star]
    for (p in state.particles) {
        if (!p.alive) continue
        val k = p.age / SparklesState.LIFE_SEC
        val grow = min(1.0, k * 8)                                  // 순간 커졌다가
        val width = (p.size * grow * (1 - k * 0.6)).toFloat() * unitPx   // 줄어들며
        if (width <= 0f) continue
        val image = if (p.isSparkle) sparkle else star
        val px = if (p.isSparkle) GameArt.SPARKLE_PIXELS else GameArt.STAR_PIXELS
        val height = width * px.height / px.width
        val center = toScreen(p.x, p.y)
        rotate(-p.angle.toFloat(), pivot = center) {
            drawGameImage(image, center, Size(width, height), alpha = (1 - k * k).toFloat())   // 끝에서 빠르게 흐려짐
        }
    }
}
