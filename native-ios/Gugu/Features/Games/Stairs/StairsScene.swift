import SwiftUI

// 구구 계단 무대 그리기 (Unity 시험판 StairsView·SkyView·StormCloudView·BestFlagView·Sparkles 의 그림 부분)
// 엔진이 프레임마다 움직인 상태(StairsStage·SparklesState)를 Canvas 한 장에 그린다.
// 그리는 순서 = Unity 정렬 순서: 하늘·장식·언덕 → 깃발 점선 → 발판 → 깃발 → 숫자 → 보물 상자 → 착지 먼지 → 구구 → 장신구·반짝이 → 먹구름

struct StairsScene: View {
    let stage: StairsStage
    let sparkles: SparklesState
    /// 먹구름 윗면(칸)
    let cloudTop: Double
    /// 반짝임·일렁임용 흐른 시간(초)
    let time: Double

    /// 발판 숫자 글자 크기(유닛) — Unity TextMesh 글자 크기 96 × 0.075
    private static let labelEm = 0.72
    /// 먹구름 띠: 흔들려도 화면 양옆이 비지 않게 더 넓게(유닛)·좌우 일렁임(유닛)
    private static let cloudSideMargin = 1.2
    private static let cloudDrift = 0.25
    /// 띠 그림에서 "구름 윗면 선"의 위치(띠 높이 대비, 위에서부터) — 이 선이 발에 닿으면 잡힘
    private static let cloudTopLine = 0.25
    /// 띠 아래 단색(그림 아래 가장자리 색과 같아야 이음매가 안 보임)
    private static let cloudFill = Color(hex: "#5E667C")
    private static let dustFill = Color(hex: "#FFF3D6")
    private static let flagHeight = 1.1
    private static let flagEdgeInset = 0.3
    private static let dashes = 16
    private static let dashWidth = 0.32
    private static let dashHeight = 0.07

    var body: some View {
        Canvas { ctx, size in
            let camera = StairsCamera(size: size, centerY: stage.cameraY)
            StairsSky.draw(in: &ctx, camera: camera, time: time)
            drawFlagLine(&ctx, camera)
            drawPlatforms(&ctx, camera)
            drawFlag(&ctx, camera)
            drawLabels(&ctx, camera)
            drawChests(&ctx, camera)
            drawDust(&ctx, camera)
            drawCharacter(&ctx, camera)
            sparkles.draw(in: &ctx, sparkle: ctx.resolve(GameArt.image("sparkle")), star: ctx.resolve(GameArt.image("star")),
                          unitPoints: camera.unitPoints, toScreen: camera.point)
            drawStormCloud(&ctx, camera)
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }

    // MARK: - 발판·숫자

    private func drawPlatforms(_ ctx: inout GraphicsContext, _ camera: StairsCamera) {
        let image = ctx.resolve(GameArt.image("platform_jelly"))
        let w = CGFloat(StairsStage.platformWidth) * camera.unitPoints
        let h = CGFloat(StairsStage.platformHeight) * camera.unitPoints
        for row in stage.rows.values.sorted(by: { $0.height < $1.height }) {
            for (i, lane) in row.lanes.enumerated() where lane.visible && lane.alpha > 0 {
                let p = camera.point(StairsStage.laneX(i), StairsStage.platformY(row.height) - StairsStage.fallDrop(lane))
                var c = ctx
                c.opacity = lane.alpha
                // 무채색 발판에 단 색·정답 초록·오답 빨강을 곱해 입힌다
                c.addFilter(.colorMultiply(lane.color.color))
                c.draw(image, in: CGRect(x: p.x - w / 2, y: p.y - h / 2, width: w, height: h))
            }
        }
    }

    private func drawLabels(_ ctx: inout GraphicsContext, _ camera: StairsCamera) {
        let font = Font.suiteNum(.extrabold, CGFloat(Self.labelEm) * camera.unitPoints)
        for row in stage.rows.values {
            for (i, lane) in row.lanes.enumerated() where lane.visible && lane.alpha > 0 && !lane.label.isEmpty {
                let p = camera.point(StairsStage.laneX(i), StairsStage.labelY(row.height) - StairsStage.fallDrop(lane))
                var c = ctx
                c.opacity = lane.alpha
                c.draw(c.resolve(Text(verbatim: lane.label).font(font).foregroundColor(GamePalette.text)), at: p, anchor: .center)
            }
        }
    }

    // MARK: - 보물 상자·구구

    private func drawChests(_ ctx: inout GraphicsContext, _ camera: StairsCamera) {
        for chest in stage.chests {
            let center = StairsStage.chestCenter(chest)
            let p = camera.point(center.x, center.y)
            let s = CGFloat(StairsStage.chestSize * chest.scale) * camera.unitPoints
            guard s > 0 else { continue }
            let image = ctx.resolve(GameArt.image(chest.open ? "chest_open" : "chest_closed"))
            ctx.draw(image, in: CGRect(x: p.x - s / 2, y: p.y - s / 2, width: s, height: s))
        }
    }

    /// 착지 먼지: 발판 위 작은 크림색 원(구구 뒤) — 퍼지며 커지고 흐려짐. 흰색은 밝은 하늘에 묻혀 크림색
    private func drawDust(_ ctx: inout GraphicsContext, _ camera: StairsCamera) {
        for d in stage.dust where d.alive {
            let r = CGFloat(d.radius) * camera.unitPoints
            let p = camera.point(d.x, d.y)
            var c = ctx
            c.opacity = d.alpha
            c.fill(Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)), with: .color(Self.dustFill))
        }
    }

    /// 구구의 원점 = 발. 그림은 발이 원점에 닿도록 위로 올려 두어, 착지 찌그러짐·숨쉬기·점프 늘어남이 발판에 붙은 채 일어난다.
    /// 점프 기울기는 몸 가운데를 축으로(공중에서 몸이 가는 쪽으로 쏠림). 장신구도 같은 변환 안
    private func drawCharacter(_ ctx: inout GraphicsContext, _ camera: StairsCamera) {
        let u = camera.unitPoints
        let size = CGFloat(StairsStage.characterSize) * u
        let feet = camera.point(stage.characterX, stage.characterY)
        let scale = stage.characterScale
        // 그림 중심 = 발 위 (0.5 - feetPad) × 크기
        let bodyCenterY = -size * (0.5 - GameArt.feetPad)
        var c = ctx
        c.translateBy(x: feet.x, y: feet.y + bodyCenterY)
        c.rotate(by: .degrees(stage.characterTilt))
        c.translateBy(x: 0, y: -bodyCenterY)
        c.scaleBy(x: CGFloat(scale.x), y: CGFloat(scale.y))
        c.draw(c.resolve(GameArt.image(stage.pose.file)),
               in: CGRect(x: -size / 2, y: bodyCenterY - size / 2, width: size, height: size))
        guard let accessory = stage.accessory else { return }
        let place = GameArt.accessoryPlacement(accessory, pose: stage.pose)
        let w = place.size.width * size
        let h = place.size.height * size
        let cx = place.center.x * size
        let cy = bodyCenterY - place.center.y * size
        c.draw(c.resolve(GameArt.image(GameArt.file(accessory))), in: CGRect(x: cx - w / 2, y: cy - h / 2, width: w, height: h))
    }

    // MARK: - 최고 기록 깃발

    private func drawFlagLine(_ ctx: inout GraphicsContext, _ camera: StairsCamera) {
        let flag = stage.flag
        guard flag.visible, flag.lineAlpha > 0 else { return }
        let y = StairsStage.feetY(Double(flag.best))
        let step = camera.halfWidth * 2 / Double(Self.dashes)
        let w = CGFloat(Self.dashWidth) * camera.unitPoints
        let h = CGFloat(Self.dashHeight) * camera.unitPoints
        var dashes = Path()
        for i in 0..<Self.dashes {
            let p = camera.point(-camera.halfWidth + step * (Double(i) + 0.5), y)
            dashes.addRect(CGRect(x: p.x - w / 2, y: p.y - h / 2, width: w, height: h))
        }
        ctx.fill(dashes, with: .color(.white.opacity(flag.lineAlpha)))
    }

    /// 깃발: 깃대 아래 끝이 선에 닿게, 화면 오른쪽 끝에서 살짝 안쪽. 깃발이 화면 안쪽(왼쪽)으로 펄럭이게 뒤집음
    private func drawFlag(_ ctx: inout GraphicsContext, _ camera: StairsCamera) {
        let flag = stage.flag
        guard flag.visible else { return }
        let pop = flag.popScale
        let height = Self.flagHeight * pop
        let width = height * Double(GameArt.flagPixels.width / GameArt.flagPixels.height)
        let x = camera.halfWidth - Self.flagEdgeInset - width * 0.5
        let p = camera.point(x, StairsStage.feetY(Double(flag.best)) + height * 0.5)
        let w = CGFloat(width) * camera.unitPoints
        let h = CGFloat(height) * camera.unitPoints
        var c = ctx
        c.translateBy(x: p.x, y: p.y)
        c.scaleBy(x: -1, y: 1)
        c.draw(c.resolve(GameArt.image("flag")), in: CGRect(x: -w / 2, y: -h / 2, width: w, height: h))
    }

    // MARK: - 먹구름

    /// 화면 폭 전체 띠(몽실몽실한 윗부분 + 얼굴·번개) + 그 아래 같은 색 사각형. 잡히면 조금 더 차올라 구구를 삼킨다
    private func drawStormCloud(_ ctx: inout GraphicsContext, _ camera: StairsCamera) {
        let swallow = stage.swallow
        let lineY = StairsStage.feetY(cloudTop) + StairsStage.swallowRise * (1 - (1 - swallow) * (1 - swallow))
        let px = GameArt.stormCloudPixels
        let width = camera.halfWidth * 2 + Self.cloudSideMargin * 2
        let height = width * Double(px.height / px.width)
        let x = sin(time * 0.7) * Self.cloudDrift
        // 띠 위 끝 = 중심 + h/2, 윗면 선 = 위 끝 − f·h  →  중심 = 선 + h·(f − 0.5)
        let bandCenterY = lineY + height * (Self.cloudTopLine - 0.5)
        let p = camera.point(x, bandCenterY)
        let w = CGFloat(width) * camera.unitPoints
        let h = CGFloat(height) * camera.unitPoints
        ctx.draw(ctx.resolve(GameArt.image("storm_cloud")), in: CGRect(x: p.x - w / 2, y: p.y - h / 2, width: w, height: h))
        // 띠 아래 가장자리에서 아래로 화면 끝까지. 살짝 겹쳐 이음매 틈 방지
        let fillTop = p.y + h / 2 - 0.02 * camera.unitPoints
        if fillTop < camera.size.height {
            ctx.fill(Path(CGRect(x: 0, y: fillTop, width: camera.size.width, height: camera.size.height - fillTop)),
                     with: .color(Self.cloudFill))
        }
    }
}
