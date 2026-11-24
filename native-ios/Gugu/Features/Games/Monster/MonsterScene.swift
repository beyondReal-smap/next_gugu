import SwiftUI

// 구구 몬스터 무대 그리기 (Unity 시험판 ForestStageView·MonsterView·Sparkles 의 그림 부분)
// 엔진이 프레임마다 움직인 상태(MonsterStage·SparklesState)를 Canvas 한 장에 그린다.
// 그리는 순서 = Unity 정렬 순서: 숲 배경 → 반딧불 → 그루터기 → 터짐 고리 → 기절 몬스터 → 몬스터 → 체력 숫자 → 반짝이

struct MonsterScene: View {
    let stage: MonsterStage
    let sparkles: SparklesState
    /// 통통·반딧불용 흐른 시간(초)
    let time: Double

    /// 그루터기 폭(유닛)과 윗면(나이테) 중심 위치(그림 위 끝에서, 높이 대비 — 몬스터 발이 여기에 닿게)
    private static let stumpWidth = 4.2
    private static let stumpTopFace = 0.27
    private static let fireflyCount = 7
    private static let fireflyWidth = 0.22
    private static let fireflyColor = Color(red: 1, green: 0.95, blue: 0.55)
    /// 오답 때 몸을 붉게(그림 색에 곱함)
    private static let hitTint = (g: 0.5, b: 0.5)

    var body: some View {
        Canvas { ctx, size in
            let camera = MonsterCamera(size: size)
            drawForest(&ctx, camera)
            // 처치 화면 흔들림 — 카메라가 움직인 만큼 숲 앞의 것(반딧불·그루터기·몬스터·반짝이)을 반대로 민다.
            // 숲 그림은 화면에 꼭 맞춰 그려 같이 밀면 가장자리가 비므로 고정(Android 와 같음)
            let shake = stage.cameraShake
            ctx.translateBy(x: -CGFloat(shake.x) * camera.unitPoints, y: CGFloat(shake.y) * camera.unitPoints)
            drawFireflies(&ctx, camera)
            drawStump(&ctx, camera)
            drawPop(&ctx, camera)
            drawMonster(&ctx, camera)
            sparkles.draw(in: &ctx, sparkle: ctx.resolve(GameArt.image("sparkle")), star: ctx.resolve(GameArt.image("star")),
                          unitPoints: camera.unitPoints, toScreen: camera.point)
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }

    // MARK: - 숲·반딧불·그루터기

    /// 화면을 꽉 덮도록(비율 유지, 넘치는 쪽은 잘림). 숲 그림 가운데 빈터가 몬스터 자리와 맞도록 화면 가운데
    private func drawForest(_ ctx: inout GraphicsContext, _ camera: MonsterCamera) {
        let px = GameArt.forestPixels
        let cover = max(camera.size.width / px.width, camera.size.height / px.height)
        let w = px.width * cover
        let h = px.height * cover
        ctx.draw(ctx.resolve(GameArt.image("forest")),
                 in: CGRect(x: (camera.size.width - w) / 2, y: (camera.size.height - h) / 2, width: w, height: h))
    }

    /// 화면 양옆 위주로 천천히 맴돌며 깜빡임(가운데 몬스터·문제 판을 가리지 않게)
    private func drawFireflies(_ ctx: inout GraphicsContext, _ camera: MonsterCamera) {
        let image = ctx.resolve(GameArt.image("sparkle"))
        let w = CGFloat(Self.fireflyWidth) * camera.unitPoints
        let h = w * GameArt.sparklePixels.height / GameArt.sparklePixels.width
        let halfW = camera.halfWidth
        let halfH = camera.halfHeight
        let t = time
        for i in 0..<Self.fireflyCount {
            let k = Double(i)
            let side = i % 2 == 0 ? -1.0 : 1.0
            let baseX = side * halfW * (0.55 + 0.3 * Self.frac(k * 0.37))
            let baseY = -halfH * 0.35 + (halfH * 0.45 + halfH * 0.35) * Self.frac(k * 0.61 + 0.2)
            let x = baseX + sin(t * (0.35 + 0.05 * k) + k) * 0.45
            let y = baseY + sin(t * (0.5 + 0.07 * k) + k * 2.1) * 0.35
            let p = camera.point(x, y)
            var c = ctx
            c.opacity = 0.25 + 0.75 * max(0, sin(t * (1.3 + 0.2 * k) + k * 1.3))
            c.addFilter(.colorMultiply(Self.fireflyColor))
            c.draw(image, in: CGRect(x: p.x - w / 2, y: p.y - h / 2, width: w, height: h))
        }
    }

    private func drawStump(_ ctx: inout GraphicsContext, _ camera: MonsterCamera) {
        let px = GameArt.stumpPixels
        let height = Self.stumpWidth * Double(px.height / px.width)
        // 윗면 중심 = 중심 + (0.5 − 윗면 비율)·h  →  중심 = 발 − (0.5 − 윗면 비율)·h
        let p = camera.point(0, MonsterStage.feetY - (0.5 - Self.stumpTopFace) * height)
        let w = CGFloat(Self.stumpWidth) * camera.unitPoints
        let h = CGFloat(height) * camera.unitPoints
        ctx.draw(ctx.resolve(GameArt.image("stump")), in: CGRect(x: p.x - w / 2, y: p.y - h / 2, width: w, height: h))
    }

    // MARK: - 몬스터

    /// 처치: 옅은 고리가 퍼지고, 그 위로 기절한 몬스터가 커지고 기울며 떠오르다 사라진다
    private func drawPop(_ ctx: inout GraphicsContext, _ camera: MonsterCamera) {
        guard let pop = stage.pop else { return }
        let u = camera.unitPoints
        let t = pop.t
        let origin = camera.point(pop.x, pop.y)
        var c = ctx
        c.translateBy(x: origin.x, y: origin.y)
        c.rotate(by: .degrees(12 * t))   // Unity Z −12° = 화면에서 시계 방향
        // 고리는 옅게 — 진하면 기절한 몬스터 뒤를 원판으로 덮어 그림이 탁해짐
        let ring = CGFloat(MonsterStage.bodySize * (1 + 0.8 * t)) * u
        var r = c
        r.opacity = 0.4 * (1 - t)
        r.fill(Path(ellipseIn: CGRect(x: -ring / 2, y: -ring / 2, width: ring, height: ring)),
               with: .color(GameArt.monsterTint(pop.kind)))
        // 기절한 몬스터: 처음엔 또렷하게 보이도록 제곱으로 흐려짐
        let px = GameArt.monsterPixels(pop.kind, defeated: true)
        let h = CGFloat(MonsterStage.bodySize * (1 + 0.25 * t)) * u
        let w = h * px.width / px.height
        let lift = CGFloat(0.8 * t) * u
        var b = c
        b.opacity = 1 - t * t
        let image = b.resolve(GameArt.image(GameArt.monsterFile(pop.kind, defeated: true)))
        let rect = CGRect(x: -w / 2, y: -lift - h / 2, width: w, height: h)
        let white = pop.whiteFlash
        guard white > 0 else {
            b.draw(image, in: rect)
            return
        }
        // 처치 직후 번쩍: 그림 모양 그대로 흰색을 덮는다(그림이 있는 곳에만 — sourceAtop)
        b.drawLayer { layer in
            layer.draw(image, in: rect)
            layer.blendMode = .sourceAtop
            layer.opacity = white
            layer.fill(Path(rect), with: .color(.white))
        }
    }

    /// 몬스터 + 배 패널 위 체력 숫자. 숨 쉬듯 찌그러짐·깡총 찌그러짐은 숫자까지 함께, 붉어짐은 몸에만.
    /// 깡총 배율은 발 기준(그루터기에 붙은 채), 숨쉬기는 지금처럼 몸 가운데 기준
    private func drawMonster(_ ctx: inout GraphicsContext, _ camera: MonsterCamera) {
        guard stage.visible, stage.hp > 0 else { return }
        let u = camera.unitPoints
        let center = camera.point(stage.x, stage.y)
        let feet = CGFloat(MonsterStage.bodySize * (0.5 - Double(GameArt.feetPad))) * u
        let hop = stage.hopScale
        var c = ctx
        c.translateBy(x: center.x, y: center.y + feet)
        c.scaleBy(x: CGFloat(hop.x), y: CGFloat(hop.y))
        c.translateBy(x: 0, y: -feet)
        c.scaleBy(x: CGFloat(stage.squash), y: CGFloat(2 - stage.squash))

        let px = GameArt.monsterPixels(stage.kind)
        let h = CGFloat(MonsterStage.bodySize) * u
        let w = h * px.width / px.height
        var body = c
        if stage.flash > 0 {
            body.addFilter(.colorMultiply(Color(red: 1, green: 1 - Self.hitTint.g * stage.flash,
                                                blue: 1 - Self.hitTint.b * stage.flash)))
        }
        body.draw(body.resolve(GameArt.image(GameArt.monsterFile(stage.kind))),
                  in: CGRect(x: -w / 2, y: -h / 2, width: w, height: h))

        let panel = CGPoint(x: GameArt.monsterPanelCenter.x * h, y: -GameArt.monsterPanelCenter.y * h)
        let font = Font.suiteNum(.extrabold, CGFloat(MonsterStage.hpEm) * u)
        c.draw(c.resolve(Text(verbatim: "\(stage.hp)").font(font).foregroundColor(GamePalette.background)),
               at: panel, anchor: .center)
    }

    private static func frac(_ v: Double) -> Double { v - floor(v) }
}
