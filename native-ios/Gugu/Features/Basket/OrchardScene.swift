import SwiftUI

// 구구 바구니 무대 렌더링 (BasketScreen.tsx 의 Orchard SVG 대응)
// 웹은 360×(가변 높이) viewBox 를 쓴다. 네이티브는 360×380 고정 좌표계로 그리고
// 토끼·열매는 같은 팔레트·실루엣의 Canvas 도형으로 재구성했다(패스 단위 1:1 복제는 아니다).

struct OrchardScene: View {
    let game: BasketState
    var reducedMotion: Bool = false

    static let sceneW: CGFloat = 360
    static let sceneH: CGFloat = 380

    private static let sky = Color(hex: "#e6f4ec")
    private static let leafDark = Color(hex: "#548d61")
    private static let leafLight = Color(hex: "#77a875")
    private static let trunk = Color(hex: "#90734e")
    private static let fruitFill = ["#ffcc75", "#f6ad92", "#d5de87"]
    private static let fruitLine = ["#d89a43", "#d48b73", "#a3b45c"]

    var body: some View {
        Canvas { ctx, size in
            let s = min(size.width / Self.sceneW, size.height / Self.sceneH)
            ctx.translateBy(x: (size.width - Self.sceneW * s) / 2, y: (size.height - Self.sceneH * s) / 2)
            ctx.scaleBy(x: s, y: s)

            drawBackground(&ctx)
            drawTrees(&ctx)
            drawRabbit(&ctx)
            drawFruits(&ctx)
            drawSparkles(&ctx)
        }
        .background(Self.sky)
        .accessibilityLabel("구구 바구니 과수원")
    }

    // MARK: - 배경

    private func drawBackground(_ ctx: inout GraphicsContext) {
        ctx.fill(Path(CGRect(x: 0, y: 0, width: Self.sceneW, height: Self.sceneH)), with: .color(Self.sky))
        // 해
        ctx.fill(Path(ellipseIn: CGRect(x: 262, y: 27, width: 60, height: 60)), with: .color(Color(hex: "#ffdb78")))
        // 구름
        ctx.fill(cloud(x: 23, y: 80, scale: 1), with: .color(Color(hex: "#fffdf3").opacity(0.85)))
        ctx.fill(cloud(x: 222, y: 130, scale: 0.8), with: .color(Color(hex: "#fffdf3").opacity(0.85)))

        // 잔디 언덕 3겹
        var far = Path()
        far.move(to: CGPoint(x: 0, y: 286))
        far.addQuadCurve(to: CGPoint(x: 156, y: 281), control: CGPoint(x: 70, y: 239))
        far.addQuadCurve(to: CGPoint(x: 360, y: 267), control: CGPoint(x: 260, y: 323))
        far.addLine(to: CGPoint(x: 360, y: 380)); far.addLine(to: CGPoint(x: 0, y: 380)); far.closeSubpath()
        ctx.fill(far, with: .color(Color(hex: "#b5d7a4")))

        var mid = Path()
        mid.move(to: CGPoint(x: 0, y: 322))
        mid.addQuadCurve(to: CGPoint(x: 245, y: 318), control: CGPoint(x: 117, y: 269))
        mid.addQuadCurve(to: CGPoint(x: 360, y: 304), control: CGPoint(x: 310, y: 332))
        mid.addLine(to: CGPoint(x: 360, y: 380)); mid.addLine(to: CGPoint(x: 0, y: 380)); mid.closeSubpath()
        ctx.fill(mid, with: .color(Color(hex: "#8fbd85")))

        var near = Path()
        near.move(to: CGPoint(x: 0, y: 350))
        near.addQuadCurve(to: CGPoint(x: 360, y: 352), control: CGPoint(x: 140, y: 310))
        near.addLine(to: CGPoint(x: 360, y: 380)); near.addLine(to: CGPoint(x: 0, y: 380)); near.closeSubpath()
        ctx.fill(near, with: .color(Color(hex: "#daebbb")))
    }

    private func cloud(x: CGFloat, y: CGFloat, scale: CGFloat) -> Path {
        var path = Path()
        path.addEllipse(in: CGRect(x: x, y: y - 14 * scale, width: 46 * scale, height: 28 * scale))
        path.addEllipse(in: CGRect(x: x + 24 * scale, y: y - 24 * scale, width: 44 * scale, height: 38 * scale))
        path.addEllipse(in: CGRect(x: x + 50 * scale, y: y - 12 * scale, width: 34 * scale, height: 26 * scale))
        return path
    }

    // MARK: - 나무

    private func drawTrees(_ ctx: inout GraphicsContext) {
        var trunks = Path()
        trunks.move(to: CGPoint(x: 13, y: 0)); trunks.addLine(to: CGPoint(x: 13, y: 223))
        trunks.move(to: CGPoint(x: 346, y: 0)); trunks.addLine(to: CGPoint(x: 346, y: 221))
        ctx.stroke(trunks, with: .color(Self.trunk), lineWidth: 17)

        var branches = Path()
        branches.move(to: CGPoint(x: 13, y: 87)); branches.addLine(to: CGPoint(x: 48, y: 63))
        branches.move(to: CGPoint(x: 346, y: 71)); branches.addLine(to: CGPoint(x: 307, y: 39))
        ctx.stroke(branches, with: .color(Self.trunk), style: StrokeStyle(lineWidth: 9, lineCap: .round))

        var dark = Path()
        for c in [(CGFloat(0), CGFloat(19), CGFloat(59)), (64, 0, 46), (316, -2, 55), (366, 35, 53)] {
            dark.addEllipse(in: CGRect(x: c.0 - c.2, y: c.1 - c.2, width: c.2 * 2, height: c.2 * 2))
        }
        ctx.fill(dark, with: .color(Self.leafDark))

        var light = Path()
        for c in [(CGFloat(-8), CGFloat(63), CGFloat(34)), (33, 24, 34), (307, 9, 32)] {
            light.addEllipse(in: CGRect(x: c.0 - c.2, y: c.1 - c.2, width: c.2 * 2, height: c.2 * 2))
        }
        ctx.fill(light, with: .color(Self.leafLight))

        // 바닥의 작은 꽃
        for (i, x) in [32, 90, 266, 328].enumerated() {
            let y = Self.sceneH - 25 + CGFloat(i % 2) * 12
            var stem = Path()
            stem.move(to: CGPoint(x: CGFloat(x), y: y)); stem.addLine(to: CGPoint(x: CGFloat(x), y: y - 12))
            ctx.stroke(stem, with: .color(Self.leafDark), lineWidth: 2)
            ctx.fill(Path(ellipseIn: CGRect(x: CGFloat(x) - 5, y: y - 18, width: 10, height: 10)),
                     with: .color(i % 2 == 1 ? Color(hex: "#fff9de") : Color(hex: "#f4bd97")))
            ctx.fill(Path(ellipseIn: CGRect(x: CGFloat(x) - 2, y: y - 15, width: 4, height: 4)),
                     with: .color(Color(hex: "#d99139")))
        }
    }

    // MARK: - 토끼 + 바구니

    private func drawRabbit(_ ctx: inout GraphicsContext) {
        let celebrating = game.outcome == .correct
        let sad = game.outcome == .wrong || game.outcome == .missed
        let fur = Color(hex: "#fff7e5")

        var layer = ctx
        layer.translateBy(x: CGFloat(game.x), y: 0)

        // 그림자
        layer.fill(Path(ellipseIn: CGRect(x: -38, y: 356, width: 76, height: 14)),
                   with: .color(Color(hex: "#4f754d").opacity(0.18)))

        // 귀
        for side in [-1.0, 1.0] {
            var ear = ctx
            ear.translateBy(x: CGFloat(game.x) + CGFloat(side) * 13, y: 280)
            ear.rotate(by: .degrees(side * 12))
            ear.fill(Path(ellipseIn: CGRect(x: -8, y: -22, width: 16, height: 44)), with: .color(fur))
            ear.fill(Path(ellipseIn: CGRect(x: -3.5, y: -15, width: 7, height: 28)), with: .color(Color(hex: "#efc0ad")))
        }

        // 몸통 · 머리
        layer.fill(Path(ellipseIn: CGRect(x: -24, y: 308, width: 48, height: 52)), with: .color(fur))
        layer.fill(Path(ellipseIn: CGRect(x: -27, y: 285, width: 54, height: 46)), with: .color(fur))
        layer.fill(Path(ellipseIn: CGRect(x: -23, y: 311, width: 10, height: 6)), with: .color(Color(hex: "#efb39f")))
        layer.fill(Path(ellipseIn: CGRect(x: 13, y: 311, width: 10, height: 6)), with: .color(Color(hex: "#efb39f")))

        // 눈 — 정답이면 웃는 눈
        if celebrating {
            var eyes = Path()
            eyes.move(to: CGPoint(x: -14, y: 307)); eyes.addLine(to: CGPoint(x: -10, y: 303)); eyes.addLine(to: CGPoint(x: -6, y: 307))
            eyes.move(to: CGPoint(x: 6, y: 307)); eyes.addLine(to: CGPoint(x: 10, y: 303)); eyes.addLine(to: CGPoint(x: 14, y: 307))
            layer.stroke(eyes, with: .color(Color(hex: "#543d2b")), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
        } else {
            layer.fill(Path(ellipseIn: CGRect(x: -12.5, y: 302.5, width: 5, height: 7)), with: .color(Color(hex: "#543d2b")))
            layer.fill(Path(ellipseIn: CGRect(x: 7.5, y: 302.5, width: 5, height: 7)), with: .color(Color(hex: "#543d2b")))
        }

        // 입
        var mouth = Path()
        mouth.move(to: CGPoint(x: -5, y: sad ? 320 : 316))
        mouth.addQuadCurve(to: CGPoint(x: 5, y: sad ? 320 : 316), control: CGPoint(x: 0, y: sad ? 314 : 323))
        layer.stroke(mouth, with: .color(Color(hex: "#543d2b")), style: StrokeStyle(lineWidth: 2, lineCap: .round))

        // 발
        layer.fill(Path(ellipseIn: CGRect(x: -27, y: 353, width: 22, height: 12)), with: .color(fur))
        layer.fill(Path(ellipseIn: CGRect(x: 5, y: 353, width: 22, height: 12)), with: .color(fur))

        // 바구니 손잡이
        var handle = Path()
        handle.move(to: CGPoint(x: -25, y: 332))
        handle.addQuadCurve(to: CGPoint(x: 25, y: 332), control: CGPoint(x: 0, y: 300))
        layer.stroke(handle, with: .color(Color(hex: "#966137")), lineWidth: 5)

        // 바구니
        var basket = Path()
        basket.move(to: CGPoint(x: -35, y: 329))
        basket.addLine(to: CGPoint(x: -28, y: 356))
        basket.addQuadCurve(to: CGPoint(x: 28, y: 356), control: CGPoint(x: 0, y: 366))
        basket.addLine(to: CGPoint(x: 35, y: 329))
        basket.closeSubpath()
        layer.fill(basket, with: .color(Color(hex: "#c8914d")))
        layer.stroke(basket, with: .color(Color(hex: "#8e5b32")), lineWidth: 2)

        var weave = Path()
        weave.move(to: CGPoint(x: -29, y: 338)); weave.addLine(to: CGPoint(x: 29, y: 338))
        weave.move(to: CGPoint(x: -26, y: 347)); weave.addLine(to: CGPoint(x: 26, y: 347))
        weave.move(to: CGPoint(x: -16, y: 332)); weave.addLine(to: CGPoint(x: -13, y: 357))
        weave.move(to: CGPoint(x: 0, y: 332)); weave.addLine(to: CGPoint(x: 0, y: 360))
        weave.move(to: CGPoint(x: 13, y: 332)); weave.addLine(to: CGPoint(x: 10, y: 357))
        layer.stroke(weave, with: .color(Color(hex: "#e6b973")), lineWidth: 3)

        var rim = Path()
        rim.move(to: CGPoint(x: -35, y: 329)); rim.addLine(to: CGPoint(x: 35, y: 329))
        layer.stroke(rim, with: .color(Color(hex: "#794b2a")), style: StrokeStyle(lineWidth: 6, lineCap: .round))
        layer.fill(Path(ellipseIn: CGRect(x: -38, y: 323, width: 14, height: 10)), with: .color(fur))
        layer.fill(Path(ellipseIn: CGRect(x: 24, y: 323, width: 14, height: 10)), with: .color(fur))
    }

    // MARK: - 열매

    private func drawFruits(_ ctx: inout GraphicsContext) {
        let duration = Basket.fallDurationMs(score: game.score)
        let y = game.outcome != nil
            ? Basket.fruitCatchY
            : Basket.fruitStartY + (Basket.fruitCatchY - Basket.fruitStartY) * min(1, game.elapsedMs / duration)

        for (index, value) in game.question.choices.enumerated() {
            let caught = game.caughtIndex == index && game.outcome != nil
            let correct = value == game.question.answer
            let x = Basket.fruitX(index: index, elapsedMs: game.elapsedMs)

            var layer = ctx
            layer.opacity = game.outcome != nil && !caught ? 0.35 : 1
            layer.translateBy(x: CGFloat(x), y: CGFloat(y))

            if caught {
                layer.stroke(
                    Path(ellipseIn: CGRect(x: -31, y: -31, width: 62, height: 62)),
                    with: .color(correct ? Color(hex: "#3d8256") : Color(hex: "#b36140")),
                    style: StrokeStyle(lineWidth: 3, dash: [5, 4])
                )
            }
            // 꼭지 · 잎
            var stem = Path()
            stem.move(to: CGPoint(x: 0, y: -21))
            stem.addQuadCurve(to: CGPoint(x: 5, y: -40), control: CGPoint(x: -4, y: -36))
            layer.stroke(stem, with: .color(Color(hex: "#735237")), style: StrokeStyle(lineWidth: 3, lineCap: .round))
            var leaf = Path()
            leaf.move(to: CGPoint(x: 2, y: -26))
            leaf.addQuadCurve(to: CGPoint(x: 21, y: -38), control: CGPoint(x: 7, y: -43))
            leaf.addQuadCurve(to: CGPoint(x: 2, y: -26), control: CGPoint(x: 18, y: -23))
            layer.fill(leaf, with: .color(Color(hex: "#598e56")))

            // 열매 몸통
            var fruit = Path()
            fruit.move(to: CGPoint(x: 0, y: -23))
            fruit.addCurve(to: CGPoint(x: -15, y: 20), control1: CGPoint(x: -32, y: -36), control2: CGPoint(x: -37, y: -1))
            fruit.addCurve(to: CGPoint(x: 16, y: 20), control1: CGPoint(x: -6, y: 28), control2: CGPoint(x: 6, y: 28))
            fruit.addCurve(to: CGPoint(x: 0, y: -23), control1: CGPoint(x: 38, y: -3), control2: CGPoint(x: 30, y: -36))
            fruit.closeSubpath()
            layer.fill(fruit, with: .color(Color(hex: Self.fruitFill[index % 3])))
            layer.stroke(fruit, with: .color(Color(hex: Self.fruitLine[index % 3])), lineWidth: 2)

            var shine = Path()
            shine.move(to: CGPoint(x: -16, y: -12))
            shine.addQuadCurve(to: CGPoint(x: -23, y: 2), control: CGPoint(x: -24, y: -7))
            layer.stroke(shine, with: .color(Color(hex: "#fff8dd").opacity(0.8)),
                         style: StrokeStyle(lineWidth: 4, lineCap: .round))

            layer.draw(
                Text("\(value)").font(.system(size: 25, weight: .black, design: .rounded))
                    .foregroundColor(Color(hex: "#553c27")),
                at: CGPoint(x: 0, y: 0)
            )
        }
    }

    // MARK: - 정답 반짝임

    private func drawSparkles(_ ctx: inout GraphicsContext) {
        guard game.outcome == .correct else { return }
        let burst = 1 - game.feedbackMs / Basket.correctFeedbackMs
        for i in -2...2 {
            let spread = reducedMotion ? 17.0 : 15 + burst * 16
            let x = game.x + Double(i) * spread
            let y = 260 - (reducedMotion ? 0 : sin(burst * .pi) * 45) + Double(abs(i)) * 9
            var star = Path()
            star.move(to: CGPoint(x: x, y: y - 8))
            star.addLine(to: CGPoint(x: x + 2.5, y: y - 3))
            star.addLine(to: CGPoint(x: x + 8, y: y - 2.2))
            star.addLine(to: CGPoint(x: x + 4, y: y + 1.8))
            star.addLine(to: CGPoint(x: x + 4.9, y: y + 7.3))
            star.addLine(to: CGPoint(x: x, y: y + 4.7))
            star.addLine(to: CGPoint(x: x - 4.9, y: y + 7.3))
            star.addLine(to: CGPoint(x: x - 4, y: y + 1.8))
            star.addLine(to: CGPoint(x: x - 8, y: y - 2.2))
            star.addLine(to: CGPoint(x: x - 2.5, y: y - 3))
            star.closeSubpath()
            ctx.fill(star, with: .color(Color(hex: "#fff5bc")))
            ctx.stroke(star, with: .color(Color(hex: "#dca84a")), lineWidth: 1.5)
        }
    }
}
