import SwiftUI

// 구구 계단 하늘 배경 (Unity 시험판 SkyView.cs 이식): 오를수록 바뀌는 그라데이션(풀밭 → 하늘 마을 → 무지개 → 달나라) +
// 천천히 흐르는 장식(구름·섬·풍선·새·무지개·달·행성·별) + 출발 풀 언덕.
// "높이 올라간다"를 단색 배경 대신 눈으로 보여 주는 것이 목적이다. 발판·캐릭터보다 항상 뒤에 그린다.
// 장식 배치는 칸 번호로 만든 고정 의사 난수라 다시 와도 같은 그림이다(Unity 해시를 비트 단위로 옮김 — 같은 자리에 같은 장식).

enum StairsSky {
    /// 장식은 앞쪽의 30% 속도로 흐름(멀리 있는 느낌)
    static let parallax = 0.3
    /// 장식 칸 간격(장식 층 좌표, 유닛)
    static let slotSpacing = 2.4
    /// 빈 칸 비율 — 너무 빽빽하면 발판 숫자를 가림
    static let skipChance = 0.25
    /// 다음 구간 색으로 미리 물드는 높이(칸)
    static let blendRows = 4.0
    /// 살짝 비쳐 보여 발판·숫자보다 뒤에 있는 느낌
    static let decorAlpha = 0.8
    static let hillTileWidth = 3.6
    /// 출발 발판 아래 끝에서 언덕 꼭대기까지(유닛) — 젤리 방울이 언덕에 닿지 않게
    static let hillGap = 0.3
    static let hillTiles = 5
    static let fillDepth = 60.0
    /// 언덕 그림 아래 가장자리 색(실측) — 아래 사각형과 이음매가 안 보이게
    static let hillFill = Color(hex: "#DAE67C")

    /// 구간별 하늘 위·아래 색
    static let skyTop = [GameRGB(hex: 0x74C6FF), GameRGB(hex: 0x8DB4FF), GameRGB(hex: 0xB4A6FF), GameRGB(hex: 0x10153A)]
    static let skyBottom = [GameRGB(hex: 0xDDF5FF), GameRGB(hex: 0xFFDCEB), GameRGB(hex: 0xFFF1C4), GameRGB(hex: 0x3A3478)]

    enum Decor: CaseIterable {
        case cloud, island, balloon, bird, rainbow, moon, planet

        var file: String {
            switch self {
            case .cloud: return "sky_cloud_puff"
            case .island: return "sky_island"
            case .balloon: return "sky_balloon"
            case .bird: return "sky_bird"
            case .rainbow: return "sky_rainbow"
            case .moon: return "sky_moon"
            case .planet: return "sky_planet"
            }
        }

        /// 월드 폭(유닛)
        var width: Double {
            switch self {
            case .cloud: return 2.4
            case .island: return 2.3
            case .balloon: return 1.1
            case .bird: return 0.9
            case .rainbow: return 3.0
            case .moon: return 1.5
            case .planet: return 1.7
            }
        }

        /// 원본 px 크기(가로세로 비율)
        var pixels: CGSize {
            switch self {
            case .cloud: return CGSize(width: 292, height: 162)
            case .island: return CGSize(width: 246, height: 235)
            case .balloon: return CGSize(width: 169, height: 227)
            case .bird: return CGSize(width: 196, height: 146)
            case .rainbow: return CGSize(width: 292, height: 191)
            case .moon: return CGSize(width: 178, height: 179)
            case .planet: return CGSize(width: 234, height: 168)
            }
        }
    }

    /// 구간별 장식 후보(같은 칸에는 하나). 달나라는 큰 장식 + 작은 별 여러 개
    static func options(_ zone: SkyZone) -> [Decor] {
        switch zone {
        case .meadow: return [.cloud, .bird, .cloud]
        case .skyVillage: return [.island, .balloon, .cloud, .bird]
        case .rainbow: return [.rainbow, .cloud, .balloon]
        case .space: return [.moon, .planet]
        }
    }

    /// 카메라가 비추는 높이(칸)
    static func height(cameraY: Double) -> Double { (cameraY - StairsStage.cameraLead) / StairsStage.rowHeight }

    /// 높이(칸) → 하늘 위·아래 색. 다음 구간 시작 blendRows 칸 전부터 서서히 물듦
    static func colors(height: Double) -> (top: GameRGB, bottom: GameRGB) {
        let zone = SkyZone.at(height: Int(height.rounded(.down)))
        var top = skyTop[zone.rawValue]
        var bottom = skyBottom[zone.rawValue]
        guard let next = SkyZone(rawValue: zone.rawValue + 1) else { return (top, bottom) }
        let end = Double(next.startHeight)
        let t = min(1, max(0, (height - (end - blendRows)) / blendRows))
        top = top.lerp(to: skyTop[next.rawValue], t)
        bottom = bottom.lerp(to: skyBottom[next.rawValue], t)
        return (top, bottom)
    }

    // MARK: - 장식 칸

    struct Item {
        let file: String
        let pixels: CGSize
        /// 장식 층 좌표(유닛)
        let x: Double
        let y: Double
        let width: Double
        let flip: Bool
        let alpha: Double
        /// 달나라 작은 별 — 별마다 고정 위상으로 반짝임
        let twinklePhase: Double?
    }

    /// 장식 칸 k: 화면 가운데에 보일 때의 높이로 구간을 정하고, k 로 만든 수로 종류·위치를 고정
    static func slot(_ k: Int, halfWidth: Double) -> [Item] {
        let localY = Double(k) * slotSpacing
        let h = hash(k)
        if k < 0 || unit(h, 0) < skipChance { return [] }  // 출발 아래(언덕 근처)는 비움
        let heightSeen = (localY / parallax - StairsStage.cameraLead) / StairsStage.rowHeight
        let zone = SkyZone.at(height: Int(heightSeen.rounded(.down)))
        let choices = options(zone)
        let decor = choices[Int(unit(h, 1) * Double(choices.count)) % choices.count]
        let x = lerp(-halfWidth + 1, halfWidth - 1, unit(h, 2))
        let width = decor.width * lerp(0.85, 1.15, unit(h, 3))
        let flip = decor == .bird && unit(h, 4) < 0.5
        var items = [Item(file: decor.file, pixels: decor.pixels, x: x, y: localY, width: width, flip: flip,
                          alpha: decorAlpha, twinklePhase: nil)]
        if zone == .space {
            for i in 0..<3 {
                let sx = lerp(-halfWidth + 0.4, halfWidth - 0.4, unit(h, 5 + i))
                let sy = localY + lerp(-slotSpacing * 0.5, slotSpacing * 0.5, unit(h, 8 + i))
                items.append(Item(file: "star", pixels: GameArt.starPixels, x: sx, y: sy,
                                  width: lerp(0.18, 0.32, unit(h, 11 + i)), flip: false, alpha: 0.9,
                                  twinklePhase: unit(h, 14 + i) * .pi * 2))
            }
        }
        return items
    }

    // MARK: - 그리기

    /// 하늘·장식·언덕을 그린다(발판보다 먼저). time = 반짝임용 흐른 시간(초)
    static func draw(in ctx: inout GraphicsContext, camera: StairsCamera, time: Double) {
        let size = camera.size
        let (top, bottom) = colors(height: height(cameraY: camera.centerY))
        ctx.fill(Path(CGRect(origin: .zero, size: size)),
                 with: .linearGradient(Gradient(colors: [top.color, bottom.color]),
                                       startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))

        // 장식 층: 카메라의 (1 - parallax) 만큼 따라 움직여, 화면에서는 parallax 속도로 흐름
        let layerY = camera.centerY * (1 - parallax)
        let center = camera.centerY * parallax
        let lo = Int(((center - camera.halfHeight - 2) / slotSpacing).rounded(.down))
        let hi = Int(((center + camera.halfHeight + 2) / slotSpacing).rounded(.up))
        var images: [String: GraphicsContext.ResolvedImage] = [:]
        for k in lo...hi {
            for item in slot(k, halfWidth: camera.halfWidth) {
                let image = images[item.file] ?? ctx.resolve(GameArt.image(item.file))
                images[item.file] = image
                let w = CGFloat(item.width) * camera.unitPoints
                let hgt = w * item.pixels.height / item.pixels.width
                let p = camera.point(item.x, layerY + item.y)
                var c = ctx
                if let phase = item.twinklePhase {
                    c.opacity = 0.55 + 0.45 * abs(sin(time * 2.2 + phase))   // 우주의 작은 별은 반짝반짝
                } else {
                    c.opacity = item.alpha
                }
                c.translateBy(x: p.x, y: p.y)
                if item.flip { c.scaleBy(x: -1, y: 1) }
                c.draw(image, in: CGRect(x: -w / 2, y: -hgt / 2, width: w, height: hgt))
            }
        }

        drawHill(in: &ctx, camera: camera)
    }

    /// 출발 발판 아래 풀 언덕: 화면 폭을 덮도록 여러 장 이어 붙이고, 그 아래는 같은 색 사각형(먹구름이 덮으며 올라옴)
    private static func drawHill(in ctx: inout GraphicsContext, camera: StairsCamera) {
        let tileH = hillTileWidth * Double(GameArt.grassHillPixels.height / GameArt.grassHillPixels.width)
        let topY = -StairsStage.platformHeight * 0.5 - hillGap
        let bottomY = topY - tileH
        let image = ctx.resolve(GameArt.image("grass_hill"))
        let w = CGFloat(hillTileWidth) * camera.unitPoints
        let h = CGFloat(tileH) * camera.unitPoints
        for i in 0..<hillTiles {
            let x = (Double(i) - Double(hillTiles - 1) * 0.5) * (hillTileWidth * 0.92)   // 살짝 겹쳐 틈 방지
            let p = camera.point(x, bottomY + tileH * 0.5)
            ctx.draw(image, in: CGRect(x: p.x - w / 2, y: p.y - h / 2, width: w, height: h))
        }
        let fillTop = camera.point(0, bottomY + 0.02)
        let fillWidth = CGFloat(hillTileWidth * Double(hillTiles)) * camera.unitPoints
        ctx.fill(Path(CGRect(x: fillTop.x - fillWidth / 2, y: fillTop.y, width: fillWidth,
                             height: CGFloat(fillDepth) * camera.unitPoints)),
                 with: .color(hillFill))
    }

    // MARK: - 고정 의사 난수 (Unity SkyView.Hash·Unit 와 비트 단위로 같음, 그림 배치용 — 규칙과 무관)

    static func hash(_ k: Int) -> UInt32 {
        var x = UInt32(truncatingIfNeeded: k) &* 2_654_435_761 &+ 0x9E37_79B9
        x ^= x >> 16
        x = x &* 0x7FEB_352D
        x ^= x >> 15
        x = x &* 0x846C_A68B
        x ^= x >> 16
        return x
    }

    static func unit(_ h: UInt32, _ salt: Int) -> Double {
        var x = h ^ (UInt32(truncatingIfNeeded: salt) &* 0x9E37_79B9)
        x ^= x >> 13
        x = x &* 0x5BD1_E995
        x ^= x >> 15
        return Double(x & 0xFF_FFFF) / Double(0x100_0000)
    }

    private static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
}
