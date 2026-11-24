import SwiftUI

// 정답 순간 그 자리(밟은 발판·몬스터)에서 별·반짝이가 사방으로 터진다 (Unity 시험판 Sparkles.cs 이식).
// 좌표는 게임 월드 단위(y 위가 +). 보상감용 연출이라 흩어지는 방향은 매번 무작위여도 된다(규칙·채점과 무관).

struct SparklesState: Equatable {
    static let poolSize = 40
    static let lifeSec = 0.55
    static let gravity = 9.0

    struct Particle: Equatable {
        /// 반짝이(4갈래)와 노란 별을 번갈아 — 풀 순번이 짝수면 반짝이
        let isSparkle: Bool
        var x = 0.0
        var y = 0.0
        var vx = 0.0
        var vy = 0.0
        var age = 0.0
        /// 월드 크기(유닛)
        var size = 0.0
        var spin = 0.0
        var angle = 0.0
        var alive = false
    }

    private(set) var particles: [Particle] = (0..<SparklesState.poolSize).map { Particle(isSparkle: $0 % 2 == 0) }

    var aliveCount: Int { particles.filter(\.alive).count }

    /// big: 콤보 5배수·보물처럼 큰 보상일 때 더 많이, 더 멀리
    mutating func burst(x: Double, y: Double, big: Bool) {
        let count = big ? 16 : 8
        let speed = big ? 6.5 : 4.5
        var started = 0
        for i in particles.indices where started < count && !particles[i].alive {
            let angle = Double.random(in: 0..<(2 * .pi))
            let k = speed * Double.random(in: 0.6...1)
            particles[i].vx = cos(angle) * k
            particles[i].vy = (sin(angle) * 0.8 + 0.6) * k
            particles[i].x = x
            particles[i].y = y
            particles[i].age = 0
            particles[i].size = Double.random(in: 0.28...(big ? 0.6 : 0.45))
            particles[i].spin = Double.random(in: -360...360)
            particles[i].angle = 0
            particles[i].alive = true
            started += 1
        }
    }

    mutating func reset() {
        for i in particles.indices { particles[i].alive = false }
    }

    mutating func update(_ dt: Double) {
        for i in particles.indices where particles[i].alive {
            particles[i].age += dt
            if particles[i].age >= Self.lifeSec {
                particles[i].alive = false
                continue
            }
            particles[i].vy -= Self.gravity * dt
            particles[i].x += particles[i].vx * dt
            particles[i].y += particles[i].vy * dt
            particles[i].angle += particles[i].spin * dt
        }
    }

    /// 살아 있는 입자를 그린다. toScreen = 월드 → 화면(pt), unitPoints = 월드 1유닛의 pt
    func draw(in ctx: inout GraphicsContext, sparkle: GraphicsContext.ResolvedImage, star: GraphicsContext.ResolvedImage,
              unitPoints: CGFloat, toScreen: (Double, Double) -> CGPoint) {
        for p in particles where p.alive {
            let k = p.age / Self.lifeSec
            let grow = min(1, k * 8)                    // 순간 커졌다가
            let width = CGFloat(p.size * grow * (1 - k * 0.6)) * unitPoints   // 줄어들며
            guard width > 0 else { continue }
            let image = p.isSparkle ? sparkle : star
            let px = p.isSparkle ? GameArt.sparklePixels : GameArt.starPixels
            let height = width * px.height / px.width
            var c = ctx
            c.opacity = 1 - k * k                      // 끝에서 빠르게 흐려짐
            let center = toScreen(p.x, p.y)
            c.translateBy(x: center.x, y: center.y)
            c.rotate(by: .degrees(-p.angle))
            c.draw(image, in: CGRect(x: -width / 2, y: -height / 2, width: width, height: height))
        }
    }
}
