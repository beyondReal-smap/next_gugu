import SwiftUI

// 구구 점프·구구 레인이 함께 쓰는 무대 아트 (웹 features/runner/art.tsx 대응)
// 웹은 SVG path 로 그린다. 여기서는 같은 팔레트·실루엣을 Canvas 도형으로 재구성했다
// (패스 단위 1:1 복제는 아니다). 좌표는 모두 웹 viewBox 기준.

enum RunnerArt {
    // 팔레트 — 웹 SVG 그라디언트 색상값 그대로
    static let sky = [Color(hex: "#bde5e6"), Color(hex: "#e4f0d9"), Color(hex: "#f9edbc")]
    static let hillFar = Color(hex: "#8fc79b")
    static let hillNear = Color(hex: "#6fae7f")
    static let grass = Color(hex: "#b4ca80")
    static let soil = [Color(hex: "#deb886"), Color(hex: "#bf9267")]
    static let dinoBody = [Color(hex: "#8bce78"), Color(hex: "#4eab72"), Color(hex: "#237b63")]
    static let dinoLine = Color(hex: "#28634e")
    static let belly = Color(hex: "#f3f1b2")
    static let rock = Color(hex: "#a98a73")
    static let stump = Color(hex: "#a46d47")

    // MARK: - 배경

    /// 하늘 + 해. groundY 위쪽을 채운다.
    static func backdrop(_ ctx: inout GraphicsContext, width: CGFloat, groundY: CGFloat) {
        ctx.fill(
            Path(CGRect(x: 0, y: 0, width: width, height: groundY + 1)),
            with: .linearGradient(Gradient(colors: sky), startPoint: .zero, endPoint: CGPoint(x: 0, y: groundY))
        )
        ctx.fill(
            Path(ellipseIn: CGRect(x: 548, y: 22, width: 108, height: 108)),
            with: .radialGradient(
                Gradient(colors: [Color(hex: "#fff7ce").opacity(0.95), Color(hex: "#fff7ce").opacity(0)]),
                center: CGPoint(x: 602, y: 76), startRadius: 4, endRadius: 54
            )
        )
    }

    /// 원경/근경 언덕을 서로 다른 속도로 흘려 시차(parallax)를 만든다
    static func hills(_ ctx: inout GraphicsContext, width: CGFloat, groundY: CGFloat, travel: Double) {
        func band(offset: Double, step: CGFloat, height: CGFloat, color: Color) {
            var path = Path()
            let shift = -CGFloat(offset.truncatingRemainder(dividingBy: Double(step)))
            var x = shift - step
            while x < width + step {
                path.move(to: CGPoint(x: x, y: groundY))
                path.addQuadCurve(to: CGPoint(x: x + step, y: groundY), control: CGPoint(x: x + step / 2, y: groundY - height))
                x += step
            }
            ctx.fill(path, with: .color(color))
        }
        band(offset: travel * 0.18, step: 300, height: 96, color: hillFar)
        band(offset: travel * 0.34, step: 210, height: 62, color: hillNear)
    }

    /// 잔디 띠 + 흙바닥. height 는 흙 영역 높이.
    static func ground(_ ctx: inout GraphicsContext, width: CGFloat, groundY: CGFloat, height: CGFloat) {
        ctx.fill(Path(CGRect(x: 0, y: groundY, width: width, height: 12)), with: .color(grass))
        ctx.fill(
            Path(CGRect(x: 0, y: groundY + 1, width: width, height: height)),
            with: .linearGradient(
                Gradient(colors: soil),
                startPoint: CGPoint(x: 0, y: groundY), endPoint: CGPoint(x: 0, y: groundY + height)
            )
        )
    }

    // MARK: - 캐릭터

    /// 공룡. 호출 전에 ctx 를 머리 위 왼쪽(몸통 로컬 원점)으로 옮겨 둔다.
    /// - Parameters:
    ///   - stride: -1~1 보행 위상
    ///   - airborne: 점프/레인 전환 중이면 다리를 접는다
    ///   - tilt: 레인 전환 시 기울기(도)
    static func dino(_ ctx: inout GraphicsContext, stride: Double, airborne: Bool, hurt: Bool, tilt: Double = 0) {
        var body = ctx
        if tilt != 0 {
            body.translateBy(x: 32, y: 32)
            body.rotate(by: .degrees(tilt))
            body.translateBy(x: -32, y: -32)
        }

        // 꼬리
        var tail = Path()
        tail.move(to: CGPoint(x: 19, y: 39))
        tail.addQuadCurve(to: CGPoint(x: -7, y: 30), control: CGPoint(x: 3, y: 43))
        tail.addQuadCurve(to: CGPoint(x: 15, y: 53), control: CGPoint(x: -7, y: 48))
        tail.closeSubpath()
        body.fill(tail, with: .color(Color(hex: "#388d65")))
        body.stroke(tail, with: .color(dinoLine), lineWidth: 1.5)

        // 뒷다리 (보행 위상 반대)
        leg(&body, hipX: 18, hipY: 44, angle: -stride * 24 - (airborne ? 18 : 0), fill: Color(hex: "#2b795c"))

        // 몸통
        var torso = Path()
        torso.addRoundedRect(in: CGRect(x: 14, y: 14, width: 46, height: 42), cornerSize: CGSize(width: 18, height: 18))
        body.fill(torso, with: .linearGradient(
            Gradient(colors: dinoBody), startPoint: CGPoint(x: 14, y: 14), endPoint: CGPoint(x: 55, y: 56)
        ))
        body.stroke(torso, with: .color(dinoLine), lineWidth: 1.8)

        // 배
        var bellyPath = Path()
        bellyPath.addRoundedRect(in: CGRect(x: 30, y: 30, width: 20, height: 22), cornerSize: CGSize(width: 10, height: 10))
        body.fill(bellyPath, with: .color(belly))

        // 머리
        var head = Path()
        head.addRoundedRect(in: CGRect(x: 36, y: 2, width: 26, height: 24), cornerSize: CGSize(width: 11, height: 11))
        body.fill(head, with: .linearGradient(
            Gradient(colors: [dinoBody[0], dinoBody[1]]), startPoint: CGPoint(x: 36, y: 2), endPoint: CGPoint(x: 62, y: 26)
        ))
        body.stroke(head, with: .color(dinoLine), lineWidth: 1.8)

        // 눈 — 피격 시 X 표시
        if hurt {
            var cross = Path()
            cross.move(to: CGPoint(x: 44, y: 11)); cross.addLine(to: CGPoint(x: 50, y: 17))
            cross.move(to: CGPoint(x: 50, y: 11)); cross.addLine(to: CGPoint(x: 44, y: 17))
            body.stroke(cross, with: .color(Color(hex: "#294e3c")), lineWidth: 2)
        } else {
            body.fill(Path(ellipseIn: CGRect(x: 45, y: 10, width: 6.6, height: 9.6)), with: .color(Color(hex: "#243f32")))
            body.fill(Path(ellipseIn: CGRect(x: 46.5, y: 11, width: 2.4, height: 2.4)), with: .color(.white))
        }

        // 입 — 피격 시 아래로
        var mouth = Path()
        mouth.move(to: CGPoint(x: 49, y: hurt ? 29 : 28))
        mouth.addQuadCurve(to: CGPoint(x: 56, y: hurt ? 28 : 27), control: CGPoint(x: 52.5, y: hurt ? 26 : 31))
        body.stroke(mouth, with: .color(Color(hex: "#2e6449")), lineWidth: 1.4)

        // 등 무늬
        var spikes = Path()
        spikes.move(to: CGPoint(x: 21, y: 27))
        spikes.addQuadCurve(to: CGPoint(x: 28, y: 19), control: CGPoint(x: 21, y: 20))
        body.stroke(spikes, with: .color(Color(hex: "#b2e598").opacity(0.8)), lineWidth: 3)

        // 앞다리
        leg(&body, hipX: 25, hipY: 46, angle: stride * 25 + (airborne ? 14 : 0), fill: Color(hex: "#4eaa70"))
    }

    private static func leg(_ ctx: inout GraphicsContext, hipX: CGFloat, hipY: CGFloat, angle: Double, fill: Color) {
        var path = Path()
        path.addRoundedRect(in: CGRect(x: hipX, y: hipY, width: 11, height: 19), cornerSize: CGSize(width: 4, height: 4))
        path.addRoundedRect(in: CGRect(x: hipX, y: hipY + 14, width: 16, height: 6), cornerSize: CGSize(width: 3, height: 3))
        var layer = ctx
        layer.translateBy(x: hipX + 4, y: hipY + 3)
        layer.rotate(by: .degrees(angle))
        layer.translateBy(x: -(hipX + 4), y: -(hipY + 3))
        layer.fill(path, with: .color(fill))
        layer.stroke(path, with: .color(dinoLine), lineWidth: 1.5)
    }

    /// 달릴 때 발밑 먼지 (무대 좌표 기준)
    static func dust(_ ctx: inout GraphicsContext, footX: CGFloat, footY: CGFloat, travel: Double, opacity: Double = 0.45) {
        var path = Path()
        for i in 0..<3 {
            let age = ((travel + Double(i) * 16).truncatingRemainder(dividingBy: 48)) / 48
            let r = 2 + age * 4
            path.addEllipse(in: CGRect(
                x: Double(footX) + 8 - age * 37 - r, y: Double(footY) - sin(age * .pi) * 5,
                width: r * 2, height: (1 + age * 2) * 2
            ))
        }
        ctx.fill(path, with: .color(Color(hex: "#f7e2b6").opacity(opacity)))
    }

    // MARK: - 장애물

    /// 바위/그루터기. 호출 전에 ctx 를 장애물 x 로 옮겨 두고, 발이 닿는 y 를 baseY 로 준다.
    static func obstacle(_ ctx: inout GraphicsContext, kind: LaneObstacleKind, baseY: CGFloat) {
        var layer = ctx
        layer.fill(
            Path(ellipseIn: CGRect(x: -7, y: baseY, width: 48, height: 8)),
            with: .color(Color(hex: "#655845").opacity(0.2))
        )

        switch kind {
        case .rock:
            var path = Path()
            path.move(to: CGPoint(x: 0, y: baseY))
            path.addLine(to: CGPoint(x: 4, y: baseY - 32))
            path.addQuadCurve(to: CGPoint(x: 23, y: baseY - 36), control: CGPoint(x: 13, y: baseY - 46))
            path.addLine(to: CGPoint(x: 34, y: baseY))
            path.closeSubpath()
            layer.fill(path, with: .linearGradient(
                Gradient(colors: [Color(hex: "#c4a68d"), rock]),
                startPoint: CGPoint(x: 0, y: baseY - 46), endPoint: CGPoint(x: 34, y: baseY)
            ))
            layer.stroke(path, with: .color(Color(hex: "#785444")), lineWidth: 1.8)
            var shine = Path()
            shine.move(to: CGPoint(x: 9, y: baseY - 26))
            shine.addLine(to: CGPoint(x: 16, y: baseY - 34))
            shine.addLine(to: CGPoint(x: 21, y: baseY - 27))
            layer.fill(shine, with: .color(Color(hex: "#ebc69a").opacity(0.85)))
        case .stump:
            var trunk = Path()
            trunk.addRoundedRect(in: CGRect(x: 4, y: baseY - 30, width: 30, height: 30), cornerSize: CGSize(width: 4, height: 4))
            layer.fill(trunk, with: .color(stump))
            layer.stroke(trunk, with: .color(Color(hex: "#775137")), lineWidth: 1.8)
            layer.fill(Path(ellipseIn: CGRect(x: 5.5, y: baseY - 35, width: 26, height: 12)), with: .color(Color(hex: "#eac596")))
            layer.stroke(
                Path(ellipseIn: CGRect(x: 11.5, y: baseY - 32, width: 14, height: 6)),
                with: .color(Color(hex: "#bc935e")), lineWidth: 1.3
            )
            var sprout = Path()
            sprout.move(to: CGPoint(x: 29, y: baseY - 31))
            sprout.addLine(to: CGPoint(x: 29, y: baseY - 40))
            layer.stroke(sprout, with: .color(Color(hex: "#527d46")), lineWidth: 1.5)
            layer.fill(Path(ellipseIn: CGRect(x: 22, y: baseY - 44, width: 8, height: 6)), with: .color(Color(hex: "#84b269")))
        }
    }

    /// 피격 반짝임
    static func sparkles(_ ctx: inout GraphicsContext, centerX: CGFloat, centerY: CGFloat) {
        for i in -1...1 {
            let cx = centerX + CGFloat(i) * 19
            let cy = centerY - (i == 0 ? 8 : 0)
            ctx.fill(Path(ellipseIn: CGRect(x: cx - 4, y: cy - 4, width: 8, height: 8)), with: .color(Color(hex: "#f8d170")))
        }
    }
}
