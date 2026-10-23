import SwiftUI

// 구구 팡팡 무대 렌더링 — SwiftUI Canvas (OrchardScene 과 같은 방식).
// 하늘에서 숫자 풍선이 내려오고, 아래 마을 지붕선 앞에서 토끼가 물총을 쏜다.
// 흔들림·둥실거림은 화면 연출로만 한다 — Core 판정은 풍선 중심 x 기준이고 탭 반경이 넉넉해 진폭 0.012 는 문제없다.

/// Core 좌표 ↔ 화면 좌표 변환 — 그리기와 탭 판정이 같은 값을 쓴다
struct PopLayout {
    let size: CGSize

    /// 지붕선 아래 마을·물총 자리 높이
    static let groundHeight: CGFloat = 96

    var width: CGFloat { size.width }
    /// 등장선(y 0) — 풍선이 화면 위에서 미끄러져 들어오게 풍선 반지름만큼 위로 둔다
    var top: CGFloat { -width * Pop.balloonDiameter / 2 }
    /// 지붕선(y 1) — 정답 풍선이 여기 닿으면 하트 -1
    var roofY: CGFloat { size.height - Self.groundHeight }
    /// 등장선~지붕선 높이 ÷ 폭 — Pop.tap 의 aspect
    var aspect: Double { Double((roofY - top) / max(width, 1)) }
    /// 토끼 얼굴 가운데 — 땅 위 가운데
    var rabbitHead: CGPoint { CGPoint(x: width / 2, y: size.height - 26) }

    /// 물총 끝 — 얼굴에서 겨눈 곳 쪽으로 38pt (아래쪽은 겨누지 않는다)
    func muzzle(aim target: CGPoint) -> CGPoint {
        let h = rabbitHead
        let dx = target.x - h.x, dy = min(-24, target.y - h.y)
        let len = max(1, (dx * dx + dy * dy).squareRoot())
        return CGPoint(x: h.x + dx / len * 38, y: h.y + dy / len * 38)
    }

    func point(_ x: Double, _ y: Double) -> CGPoint {
        CGPoint(x: CGFloat(x) * width, y: top + CGFloat(y) * (roofY - top))
    }

    /// 보스 그리기 위치 — 보스는 탭 대상이 아니라 화면 연출로만 위치를 정한다.
    /// 등장선(top)은 화면 위 밖이라 그대로 쓰면 처음 몇 초 동안 보스 수가 가려진다 → 처음부터 다 보이게 시작해 지붕선까지 내려온다
    func bossPoint(_ y: Double) -> CGPoint {
        let start = width * Pop.bossDiameter / 2 + 10
        return CGPoint(x: CGFloat(Pop.bossX) * width, y: start + CGFloat(y) * (roofY - start))
    }

    func normalized(_ p: CGPoint) -> (x: Double, y: Double) {
        (Double(p.x / max(width, 1)), Double((p.y - top) / max(roofY - top, 1)))
    }
}

struct PopScene: View {
    let game: PopState
    let effects: [PopEffect]
    let clockMs: Double
    let aim: (x: Double, y: Double)
    var reducedMotion: Bool = false

    private static let skyTop = Color(hex: "#bfe3ff")
    private static let skyBottom = Color(hex: "#eef8ff")
    private static let ink = Color(hex: "#1f2544")
    /// 풍선 색 — 정답·오답을 색으로 구분하지 않는다(숫자로만 판단)
    private static let palette = ["#ff8fab", "#7cc6fe", "#7ee0d6", "#95e1a3", "#c3a6ff", "#ffb38a"].map { Color(hex: $0) }
    private static let golden = Color(hex: "#ffc93c")
    private static let bossFill = Color(hex: "#6b4fd8")
    private static let shield = Color(hex: "#ffcf4a")
    private static let water = Color(hex: "#5ab8ff")

    var body: some View {
        Canvas { ctx, size in
            let layout = PopLayout(size: size)
            drawSky(&ctx, layout)
            drawVillage(&ctx, layout)
            if let boss = game.boss { drawBoss(&ctx, layout, boss) }
            for balloon in game.balloons { drawBalloon(&ctx, layout, balloon) }
            drawEffects(&ctx, layout)
            drawGun(&ctx, layout)
        }
        .background(Self.skyBottom)
    }

    // MARK: - 배경

    private func drawSky(_ ctx: inout GraphicsContext, _ l: PopLayout) {
        let rect = CGRect(origin: .zero, size: l.size)
        ctx.fill(Path(rect), with: .linearGradient(
            Gradient(colors: [Self.skyTop, Self.skyBottom]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: l.roofY)))
        // 구름 (고정)
        let cloud = Color.white.opacity(0.75)
        for (cx, cy, s) in [(0.18, 0.16, 1.0), (0.78, 0.30, 0.8), (0.42, 0.52, 0.6)] {
            let c = l.point(cx, cy)
            let k = CGFloat(s) * l.width / 390
            var p = Path()
            p.addEllipse(in: CGRect(x: c.x - 34 * k, y: c.y - 10 * k, width: 44 * k, height: 24 * k))
            p.addEllipse(in: CGRect(x: c.x - 14 * k, y: c.y - 22 * k, width: 40 * k, height: 34 * k))
            p.addEllipse(in: CGRect(x: c.x + 12 * k, y: c.y - 8 * k, width: 34 * k, height: 22 * k))
            ctx.fill(p, with: .color(cloud))
        }
        // 황금 풍선 느려짐 — 화면 톤만 바꾼다 (동작 줄이기에서도 색 변화는 남긴다)
        if game.slowMs > 0 {
            let fade = min(1, game.slowMs / 600)
            ctx.fill(Path(rect), with: .color(Self.golden.opacity(0.16 * fade)))
        }
    }

    /// 마을 지붕선 — 지붕 꼭대기가 지붕선(roofY)에 닿는다. 가운데는 토끼 물총 자리라 비워 둔다
    private func drawVillage(_ ctx: inout GraphicsContext, _ l: PopLayout) {
        let w = l.width, h = l.size.height, roof = l.roofY
        // 지붕선 안내 점선
        var line = Path()
        line.move(to: CGPoint(x: 0, y: roof))
        line.addLine(to: CGPoint(x: w, y: roof))
        ctx.stroke(line, with: .color(Color(hex: "#e2614b").opacity(0.35)), style: StrokeStyle(lineWidth: 2, dash: [6, 6]))

        // 땅
        ctx.fill(Path(CGRect(x: 0, y: roof + 34, width: w, height: h - roof - 34)), with: .color(Color(hex: "#9fd38a")))

        let houses: [(x: Double, width: Double, body: String, roof: String)] = [
            (0.02, 0.15, "#fff4dc", "#e2614b"), (0.18, 0.13, "#ffe9f0", "#7a6ad8"),
            (0.69, 0.13, "#e9f6ff", "#3e8fd8"), (0.83, 0.15, "#fff4dc", "#f08a3c"),
        ]
        for house in houses {
            let x0 = CGFloat(house.x) * w, hw = CGFloat(house.width) * w
            let bodyTop = roof + 18
            ctx.fill(Path(CGRect(x: x0 + 3, y: bodyTop, width: hw - 6, height: h - bodyTop)), with: .color(Color(hex: house.body)))
            var r = Path()
            r.move(to: CGPoint(x: x0 - 2, y: bodyTop + 2))
            r.addLine(to: CGPoint(x: x0 + hw / 2, y: roof))
            r.addLine(to: CGPoint(x: x0 + hw + 2, y: bodyTop + 2))
            r.closeSubpath()
            ctx.fill(r, with: .color(Color(hex: house.roof)))
            // 창문
            ctx.fill(Path(roundedRect: CGRect(x: x0 + hw / 2 - 7, y: bodyTop + 14, width: 14, height: 14), cornerRadius: 3),
                     with: .color(Color(hex: "#8cc8f0")))
        }
    }

    // MARK: - 풍선

    private func drawBalloon(_ ctx: inout GraphicsContext, _ l: PopLayout, _ b: PopBalloon) {
        let d = l.width * Pop.balloonDiameter
        var c = l.point(b.x + sway(b), b.y)
        if !reducedMotion, b.kind == .small {
            c.y += CGFloat(sin(clockMs / 800 + Double(b.id))) * 3
        }
        var wrongTint = 0.0
        if let shake = effects.last(where: { $0.kind == .shake(balloonId: b.id) }) {
            let t = min(1, (clockMs - shake.born) / shake.life)
            if reducedMotion {
                wrongTint = 1 - t
            } else {
                c.x += CGFloat(sin(t * .pi * 6) * (1 - t)) * 7
                wrongTint = (1 - t) * 0.6
            }
        }
        let fill = b.kind == .golden ? Self.golden : Self.palette[b.id % Self.palette.count]

        // 끈과 매듭
        var string = Path()
        string.move(to: CGPoint(x: c.x, y: c.y + d * 0.56))
        string.addQuadCurve(to: CGPoint(x: c.x, y: c.y + d * 0.95), control: CGPoint(x: c.x + d * 0.12, y: c.y + d * 0.75))
        ctx.stroke(string, with: .color(Self.ink.opacity(0.35)), lineWidth: 1.5)
        var knot = Path()
        knot.move(to: CGPoint(x: c.x - 5, y: c.y + d * 0.6))
        knot.addLine(to: CGPoint(x: c.x + 5, y: c.y + d * 0.6))
        knot.addLine(to: CGPoint(x: c.x, y: c.y + d * 0.5))
        knot.closeSubpath()
        ctx.fill(knot, with: .color(fill))

        let body = CGRect(x: c.x - d / 2, y: c.y - d * 0.56, width: d, height: d * 1.12)
        ctx.fill(Path(ellipseIn: body), with: .color(fill))
        if wrongTint > 0 {
            ctx.fill(Path(ellipseIn: body), with: .color(Color(hex: "#e5484d").opacity(wrongTint)))
        }
        ctx.stroke(Path(ellipseIn: body), with: .color(Self.ink.opacity(0.12)), lineWidth: 1.5)
        // 빛 반사
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - d * 0.3, y: c.y - d * 0.42, width: d * 0.2, height: d * 0.28)),
                 with: .color(.white.opacity(0.55)))
        if b.kind == .golden {
            ctx.stroke(Path(ellipseIn: body.insetBy(dx: -4, dy: -4)), with: .color(Color(hex: "#fff3b0")),
                       style: StrokeStyle(lineWidth: 2.5, dash: [4, 4]))
        }
        let size = max(20, d * 0.4)
        ctx.draw(Text(verbatim: "\(b.value)").font(.suiteNum(.extrabold, size)).foregroundColor(Self.ink),
                 at: CGPoint(x: c.x, y: c.y - d * 0.02))
    }

    /// 떨어지는 풍선의 좌우 흔들림 (화면 연출) — 동작 줄이기에서는 없다
    private func sway(_ b: PopBalloon) -> Double {
        guard !reducedMotion, b.kind != .small else { return 0 }
        return 0.012 * sin(clockMs / 650 + Double(b.id) * 1.7)
    }

    // MARK: - 보스

    private func drawBoss(_ ctx: inout GraphicsContext, _ l: PopLayout, _ boss: PopBoss) {
        let d = l.width * Pop.bossDiameter
        let c = l.bossPoint(boss.y)
        let body = CGRect(x: c.x - d / 2, y: c.y - d / 2, width: d, height: d)
        ctx.fill(Path(ellipseIn: body), with: .radialGradient(
            Gradient(colors: [Color(hex: "#8f78f0"), Self.bossFill]),
            center: CGPoint(x: c.x - d * 0.15, y: c.y - d * 0.15), startRadius: 2, endRadius: d * 0.7))
        // 방패 — 남은 짝꿍 수만큼 금색 고리 조각
        let segments = Pop.bossHits
        let gap = 0.12
        for i in 0..<segments {
            let start = -Double.pi / 2 + Double(i) * 2 * .pi / Double(segments) + gap
            let end = start + 2 * .pi / Double(segments) - 2 * gap
            var arc = Path()
            arc.addArc(center: c, radius: d / 2 + 7, startAngle: .radians(start), endAngle: .radians(end), clockwise: false)
            let alive = i < boss.hitsLeft
            ctx.stroke(arc, with: .color(alive ? Self.shield : Color.white.opacity(0.35)),
                       style: StrokeStyle(lineWidth: alive ? 6 : 3, lineCap: .round))
        }
        // 맞은 만큼 금
        for i in 0..<(segments - boss.hitsLeft) {
            var crack = Path()
            let k = CGFloat(i) - 0.5
            crack.move(to: CGPoint(x: c.x + d * (0.05 + 0.14 * k), y: c.y - d * 0.42))
            crack.addLine(to: CGPoint(x: c.x + d * (0.12 + 0.14 * k), y: c.y - d * 0.28))
            crack.addLine(to: CGPoint(x: c.x + d * (0.02 + 0.14 * k), y: c.y - d * 0.2))
            ctx.stroke(crack, with: .color(.white.opacity(0.7)), lineWidth: 2)
        }
        ctx.draw(Text(verbatim: "\(boss.value)").font(.suiteNum(.heavy, max(30, d * 0.34))).foregroundColor(.white),
                 at: CGPoint(x: c.x, y: c.y + d * 0.04))
    }

    // MARK: - 물총 토끼

    /// 땅 위 가운데 토끼 — 물총은 머리 뒤에서 마지막으로 쏜 곳을 향한다. 재장전 중이면 회색
    private func drawGun(_ ctx: inout GraphicsContext, _ l: PopLayout) {
        let head = l.rabbitHead
        let fur = Color(hex: "#fff7e5"), line = Color(hex: "#c9b48f")
        // 물총 (머리 뒤)
        let tip = l.muzzle(aim: l.point(aim.x, aim.y))
        var barrel = Path()
        barrel.move(to: head)
        barrel.addLine(to: tip)
        ctx.stroke(barrel, with: .color(game.reloadMs > 0 ? Color(hex: "#a3a9b8") : Self.water),
                   style: StrokeStyle(lineWidth: 10, lineCap: .round))
        // 귀
        for dx in [-10.0, 10.0] {
            let ear = CGRect(x: head.x + CGFloat(dx) - 6, y: head.y - 40, width: 12, height: 30)
            ctx.fill(Path(ellipseIn: ear), with: .color(fur))
            ctx.stroke(Path(ellipseIn: ear), with: .color(line), lineWidth: 1.5)
            ctx.fill(Path(ellipseIn: ear.insetBy(dx: 3.5, dy: 6)), with: .color(Color(hex: "#ffc9d6")))
        }
        // 얼굴
        let face = CGRect(x: head.x - 22, y: head.y - 18, width: 44, height: 36)
        ctx.fill(Path(ellipseIn: face), with: .color(fur))
        ctx.stroke(Path(ellipseIn: face), with: .color(line), lineWidth: 1.5)
        for dx in [-8.0, 8.0] {
            ctx.fill(Path(ellipseIn: CGRect(x: head.x + CGFloat(dx) - 2.5, y: head.y - 6, width: 5, height: 6)), with: .color(Self.ink))
        }
        ctx.fill(Path(ellipseIn: CGRect(x: head.x - 3, y: head.y + 3, width: 6, height: 4)), with: .color(Color(hex: "#f29bb0")))
    }

    // MARK: - 효과

    private func drawEffects(_ ctx: inout GraphicsContext, _ l: PopLayout) {
        let d = l.width * Pop.balloonDiameter
        for e in effects {
            let t = min(1, max(0, (clockMs - e.born) / e.life))
            let p = l.point(e.x, e.y)
            switch e.kind {
            case .shot:
                let from = l.muzzle(aim: p)
                if reducedMotion {
                    var beam = Path()
                    beam.move(to: from)
                    beam.addLine(to: p)
                    ctx.stroke(beam, with: .color(Self.water.opacity(0.5 * (1 - t))), lineWidth: 3)
                } else {
                    for k in 0..<3 {
                        let s = min(1, t + Double(k) * 0.12)
                        let q = CGPoint(x: from.x + (p.x - from.x) * s, y: from.y + (p.y - from.y) * s)
                        ctx.fill(Path(ellipseIn: CGRect(x: q.x - 4, y: q.y - 4, width: 8, height: 8)),
                                 with: .color(Self.water.opacity(0.9)))
                    }
                }
            case .burst(let golden, let big):
                let p = big ? l.bossPoint(e.y) : p
                let color = golden ? Self.golden : Self.palette[e.id % Self.palette.count]
                let radius = (big ? l.width * Pop.bossDiameter : d) / 2
                if reducedMotion {
                    ctx.fill(Path(ellipseIn: CGRect(x: p.x - radius, y: p.y - radius, width: radius * 2, height: radius * 2)),
                             with: .color(color.opacity(0.6 * (1 - t))))
                } else {
                    let count = big ? 16 : 8
                    for k in 0..<count {
                        let angle = Double(k) / Double(count) * 2 * .pi + Double(e.id)
                        let r = radius * (0.6 + 1.2 * t)
                        let q = CGPoint(x: p.x + CGFloat(cos(angle)) * r, y: p.y + CGFloat(sin(angle)) * r)
                        let dot = (big ? 8 : 6) * (1 - t * 0.5)
                        // 하늘색 배경에서 보이도록 진한 풍선색 조각 (황금 풍선은 금색)
                        let piece = golden && k % 2 == 0 ? Self.golden : Self.palette[(k + e.id) % Self.palette.count]
                        ctx.fill(Path(ellipseIn: CGRect(x: q.x - dot, y: q.y - dot, width: dot * 2, height: dot * 2)),
                                 with: .color(piece.opacity(1 - t)))
                    }
                }
            case .points(let points):
                let rise = reducedMotion ? 0 : CGFloat(t) * 28
                ctx.draw(Text(verbatim: "+\(points)").font(.suiteNum(.extrabold, 18)).foregroundColor(Color(hex: "#0b8a5f").opacity(1 - t)),
                         at: CGPoint(x: p.x, y: p.y - d * 0.7 - rise))
            case .wrong:
                ctx.draw(Text(verbatim: "✕").font(.system(size: 30, weight: .black)).foregroundColor(Color(hex: "#e5484d").opacity(1 - t)),
                         at: CGPoint(x: p.x, y: p.y - d * 0.75))
            case .shake:
                break   // 풍선 그리기에서 처리
            case .splash:
                let w = d * (reducedMotion ? 1 : 0.8 + CGFloat(t) * 0.6)
                ctx.fill(Path(ellipseIn: CGRect(x: p.x - w / 2, y: l.roofY - 8, width: w, height: 16)),
                         with: .color(Color(hex: "#e5484d").opacity(0.55 * (1 - t))))
            case .pair(let a, let b):
                // 식은 보스 아래에 띄운다
                let p = l.bossPoint(e.y)
                // 보스와 겹치지 않게 아래로 살짝 떨어진다
                let drift = reducedMotion ? 0 : CGFloat(t) * 12
                let label = Text(verbatim: "\(a) × \(b) = \(a * b)").font(.suiteNum(.heavy, 24))
                    .foregroundColor(Color(hex: "#5b3fd0").opacity(1 - t * t))
                ctx.draw(label, at: CGPoint(x: p.x, y: p.y + l.width * Pop.bossDiameter * 0.85 + drift))
            }
        }
    }
}
