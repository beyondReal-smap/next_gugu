import SwiftUI

// 구구 점프 무대 렌더링 (RunnerScreen.tsx 의 SVG 씬 대응)
// 웹은 720×260 viewBox 에 SVG path 로 그린다. 여기서는 같은 좌표계를 Canvas 로 옮겼고,
// 공룡·장애물·배경은 구구 레인과 공유하는 RunnerArt 가 그린다.

struct RunnerScene: View {
    let game: RunnerState
    var reducedMotion: Bool = false

    private static let sceneW: CGFloat = 720
    private static let sceneH: CGFloat = 260
    private static let groundY: CGFloat = 220
    private static let dinoX: CGFloat = 104

    var body: some View {
        Canvas { ctx, size in
            let s = min(size.width / Self.sceneW, size.height / Self.sceneH)
            ctx.translateBy(x: (size.width - Self.sceneW * s) / 2, y: (size.height - Self.sceneH * s) / 2)
            ctx.scaleBy(x: s, y: s)

            let travel = reducedMotion ? 0 : game.distance * 20
            let jump = Runner.jump(game)
            let walking = game.phase == .running && game.hitMs == 0

            RunnerArt.backdrop(&ctx, width: Self.sceneW, groundY: Self.groundY)
            RunnerArt.hills(&ctx, width: Self.sceneW, groundY: Self.groundY, travel: travel)
            drawGround(&ctx, travel: travel)
            drawObstacle(&ctx)
            drawDino(&ctx, jump: jump, travel: travel, walking: walking)
            drawEffects(&ctx, jump: jump)
        }
        .background(RunnerArt.sky[0])
        .accessibilityLabel("구구 점프 무대")
        .accessibilityValue(game.outcome == .correct ? "점프" : "달리는 중")
    }

    private func drawGround(_ ctx: inout GraphicsContext, travel: Double) {
        RunnerArt.ground(&ctx, width: Self.sceneW, groundY: Self.groundY, height: 39)
        // 흙길 위 자국 — 120 간격으로 반복해 달리는 느낌을 준다
        var marks = Path()
        var x = -CGFloat(travel.truncatingRemainder(dividingBy: 120))
        while x < Self.sceneW + 120 {
            marks.addRect(CGRect(x: x, y: Self.groundY + 16, width: 46, height: 4))
            x += 120
        }
        ctx.fill(marks, with: .color(Color(hex: "#a98362").opacity(0.35)))
    }

    private func drawObstacle(_ ctx: inout GraphicsContext) {
        var layer = ctx
        layer.translateBy(x: CGFloat(game.obstacleX), y: 0)
        RunnerArt.obstacle(&layer, kind: game.round % 2 == 0 ? .rock : .stump, baseY: Self.groundY)
    }

    private func drawDino(_ ctx: inout GraphicsContext, jump: Double, travel: Double, walking: Bool) {
        let stride = walking && !reducedMotion ? sin(travel / 16) : 0
        let bob = walking && !reducedMotion ? abs(sin(travel / 32)) * 2 : 0
        let baseY = Self.groundY - 63 - CGFloat(jump) - CGFloat(bob)

        // 그림자 — 점프 높이에 따라 작아진다
        let rx = 24 - CGFloat(jump) / 9
        ctx.fill(
            Path(ellipseIn: CGRect(
                x: 133 - rx, y: Self.groundY + 1 - 4,
                width: rx * 2, height: max(1, 4 - CGFloat(jump) / 60) * 2
            )),
            with: .color(Color(hex: "#355f45").opacity(max(0.04, 0.23 - jump / 850)))
        )

        var body = ctx
        body.translateBy(x: Self.dinoX, y: baseY)
        RunnerArt.dino(&body, stride: stride, airborne: jump > 0, hurt: game.hitMs > 0)

        if walking && !reducedMotion {
            RunnerArt.dust(&ctx, footX: Self.dinoX, footY: Self.groundY, travel: travel)
        }
    }

    private func drawEffects(_ ctx: inout GraphicsContext, jump: Double) {
        guard !reducedMotion else { return }
        // 점프 중 속도선
        if jump > 15 {
            var lines = Path()
            lines.move(to: CGPoint(x: 96, y: Self.groundY - 22 - CGFloat(jump)))
            lines.addLine(to: CGPoint(x: 81, y: Self.groundY - 22 - CGFloat(jump)))
            lines.move(to: CGPoint(x: 90, y: Self.groundY - 14 - CGFloat(jump)))
            lines.addLine(to: CGPoint(x: 71, y: Self.groundY - 14 - CGFloat(jump)))
            ctx.stroke(lines, with: .color(Color(hex: "#fff6ce")), style: StrokeStyle(lineWidth: 3, lineCap: .round))
        }
        if game.hitMs > 0 {
            RunnerArt.sparkles(&ctx, centerX: 136, centerY: Self.groundY - 77)
        }
    }
}
