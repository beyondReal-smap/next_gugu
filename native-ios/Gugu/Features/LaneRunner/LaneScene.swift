import SwiftUI

// 구구 레인 무대 렌더링 (LaneScene.tsx 대응)
// 웹은 720×360 viewBox 에 SVG 로 그리고 preserveAspectRatio="xMinYMax slice" 로 아래를 기준 삼는다.
// 여기서도 같은 좌표계를 쓰고, 공룡·장애물·배경은 RunnerArt 를 공유한다.

struct LaneScene: View {
    let game: LaneRunnerState
    var reducedMotion: Bool = false

    private static let sceneW: CGFloat = 720
    private static let sceneH: CGFloat = 360
    /// 레인별 발 기준선(y). 위 레인이 먼 쪽이다.
    private static let laneBase: [CGFloat] = [252, 300, 348]
    private static let laneTop: CGFloat = 214
    private static let laneHeight: CGFloat = 48
    private static let dinoX: CGFloat = 104
    private static let tileW: CGFloat = 84

    /// 레인 사이 실수 위치의 기준선 — 전환 애니메이션을 부드럽게 잇는다
    private static func baseAt(_ offset: Double) -> CGFloat {
        let lower = max(0, min(2, Int(offset.rounded(.down))))
        let upper = min(2, lower + 1)
        return laneBase[lower] + (laneBase[upper] - laneBase[lower]) * CGFloat(offset - Double(lower))
    }

    var body: some View {
        Canvas { ctx, size in
            // 아래를 기준으로 채운다 (xMinYMax slice)
            let s = max(size.width / Self.sceneW, size.height / Self.sceneH)
            ctx.translateBy(x: 0, y: size.height - Self.sceneH * s)
            ctx.scaleBy(x: s, y: s)
            ctx.clip(to: Path(CGRect(x: 0, y: 0, width: Self.sceneW, height: Self.sceneH)))

            let travel = reducedMotion ? 0 : game.distance * 20
            let running = game.phase == .running && game.hitMs == 0
            let walking = !reducedMotion && running

            RunnerArt.backdrop(&ctx, width: Self.sceneW, groundY: Self.laneTop)
            RunnerArt.hills(&ctx, width: Self.sceneW, groundY: Self.laneTop, travel: travel)
            drawLanes(&ctx, travel: travel)
            drawGate(&ctx)
            drawObstacles(&ctx)
            drawDino(&ctx, travel: travel, walking: walking)
            drawEffects(&ctx)
        }
        .background(RunnerArt.sky[0])
        .accessibilityLabel("구구 레인 무대")
        .accessibilityValue(game.segment == .quiz ? "정답 길 찾는 중" : "장애물 피하는 중")
    }

    // MARK: - 길

    private func drawLanes(_ ctx: inout GraphicsContext, travel: Double) {
        RunnerArt.ground(&ctx, width: Self.sceneW, groundY: Self.laneTop, height: 146)

        for lane in 0..<3 {
            let rect = CGRect(x: 0, y: Self.laneTop + CGFloat(lane) * Self.laneHeight, width: Self.sceneW, height: Self.laneHeight)
            let current = lane == game.lane && game.phase == .running
            let color = current ? Color(hex: "#fff6d8") : (lane % 2 == 0 ? Color.white : Color(hex: "#7a5a3c"))
            ctx.fill(Path(rect), with: .color(color.opacity(current ? 0.22 : 0.06)))
        }

        // 길 경계선 — 달리는 방향으로 흐른다
        var divider = Path()
        let offset = CGFloat(travel.truncatingRemainder(dividingBy: 48))
        for row in 1...2 {
            let y = Self.laneTop + CGFloat(row) * Self.laneHeight
            var x = -offset
            while x < Self.sceneW {
                divider.move(to: CGPoint(x: x, y: y))
                divider.addLine(to: CGPoint(x: min(x + 22, Self.sceneW), y: y))
                x += 48
            }
        }
        ctx.stroke(divider, with: .color(Color(hex: "#a98362").opacity(0.45)),
                   style: StrokeStyle(lineWidth: 3, lineCap: .round))
    }

    // MARK: - 정답 게이트

    private func drawGate(_ ctx: inout GraphicsContext) {
        guard let gate = game.gate else { return }
        let answer = game.question?.answer
        let reveal = game.outcome == .correct || game.outcome == .wrong

        for lane in 0..<3 {
            let value = gate.values[lane]
            let correct = reveal && value == answer
            let chosen = reveal && value == game.given && value != answer
            let fill = correct ? Color(hex: "#e3f6c6") : (chosen ? Color(hex: "#fbd9c4") : Color(hex: "#fff9e6"))
            let stroke = correct ? Color(hex: "#2f8a57") : (chosen ? Color(hex: "#c2562b") : Color(hex: "#b98a55"))
            let top = Self.laneTop + CGFloat(lane) * Self.laneHeight + 5
            let dim = reveal && !correct && !chosen ? 0.45 : 1.0

            var layer = ctx
            layer.opacity = dim
            layer.translateBy(x: CGFloat(gate.x) - 10, y: 0)
            let tile = Path(roundedRect: CGRect(x: 0, y: top, width: Self.tileW, height: Self.laneHeight - 10), cornerRadius: 10)
            layer.fill(tile, with: .color(fill))
            layer.stroke(tile, with: .color(stroke), lineWidth: 3)
            layer.draw(
                Text("\(value)").font(.system(size: 28, weight: .black, design: .rounded)).foregroundColor(Color(hex: "#27463a")),
                at: CGPoint(x: Self.tileW / 2, y: top + (Self.laneHeight - 10) / 2)
            )
        }
    }

    // MARK: - 장애물

    private func drawObstacles(_ ctx: inout GraphicsContext) {
        let ready: [LaneObstacle] = game.phase == .ready
            ? [LaneObstacle(id: -1, lane: 0, x: 470, kind: .rock), LaneObstacle(id: -2, lane: 2, x: 610, kind: .stump)]
            : []
        // 위 레인부터 그려야 가까운 레인이 앞에 온다
        for obstacle in (ready + game.obstacles).sorted(by: { $0.lane < $1.lane }) {
            var layer = ctx
            layer.translateBy(x: CGFloat(obstacle.x), y: 0)
            RunnerArt.obstacle(&layer, kind: obstacle.kind, baseY: Self.laneBase[obstacle.lane])
        }
    }

    // MARK: - 캐릭터

    private func drawDino(_ ctx: inout GraphicsContext, travel: Double, walking: Bool) {
        let stride = walking ? sin(travel / 18) : 0
        let bob = walking ? abs(stride) * 1.5 : 0
        let offset = reducedMotion ? Double(game.lane) : LaneRunner.offset(game)
        let base = Self.baseAt(offset)
        let hurt = game.hitMs > 0 || game.phase == .over
        let shifting = !reducedMotion && game.laneAnimMs > 0 ? (Double(game.lane) < game.laneFrom ? -6.0 : 6.0) : 0
        let impact = !reducedMotion && game.hitMs > 0
            ? sin((LaneRunner.hitMs - game.hitMs) / 45) * (game.hitMs / LaneRunner.hitMs) * 3
            : 0

        if walking {
            RunnerArt.dust(&ctx, footX: Self.dinoX, footY: base, travel: travel, opacity: 0.65)
        }
        ctx.fill(
            Path(ellipseIn: CGRect(x: Self.dinoX + 29 - 24, y: base + 3 - 4, width: 48, height: 8)),
            with: .color(Color(hex: "#355f45").opacity(0.23))
        )

        var body = ctx
        body.translateBy(x: Self.dinoX + CGFloat(impact), y: base - 63 - CGFloat(bob))
        RunnerArt.dino(&body, stride: stride, airborne: game.laneAnimMs > 0 && !reducedMotion, hurt: hurt, tilt: shifting)
    }

    // MARK: - 효과

    private func drawEffects(_ ctx: inout GraphicsContext) {
        let offset = reducedMotion ? Double(game.lane) : LaneRunner.offset(game)
        let base = Self.baseAt(offset)

        if game.hitMs > 0 {
            RunnerArt.sparkles(&ctx, centerX: Self.dinoX + 32, centerY: base - 77)
        }

        // 정답 통과 직후 떠오르는 +1 / 속도 UP
        guard game.outcome == .correct, let gate = game.gate, !reducedMotion else { return }
        let reward = max(0, min(1, (LaneRunner.judgeX - gate.x) / 120))
        guard reward > 0, reward < 1 else { return }

        ctx.opacity = 1 - reward
        ctx.draw(
            Text("+1").font(.system(size: 20, weight: .black, design: .rounded)).foregroundColor(Color(hex: "#316649")),
            at: CGPoint(x: Self.dinoX + 40, y: base - 78 - CGFloat(reward) * 25)
        )
        if LaneRunner.leveledUp(game) {
            ctx.opacity = min(1, (1 - reward) * 2)
            ctx.draw(
                Text("속도 UP!").font(.system(size: 36, weight: .black, design: .rounded)).foregroundColor(Color(hex: "#c2562b")),
                at: CGPoint(x: 360, y: 120 - CGFloat(reward) * 12)
            )
        }
    }
}
