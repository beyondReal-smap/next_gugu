import SwiftUI

// 구구 계단 고도계 (Unity 시험판 AltimeterView.cs 이식) — 화면 왼쪽 세로 막대.
// 아래 풀밭에서 위로 구간 아이콘(구름 = 하늘 마을, 무지개, 달)을 눈금으로 두고, 구구(지금 높이)·먹구름(구름 윗면)·
// 빨간 깃발(지난 최고 기록)이 오르내린다. "얼마나 올라왔고 구름이 얼마나 쫓아오나"를 한눈에.
// 막대 위 끝 높이는 Altimeter.top(5칸 단위로 늘어남) — 늘어날 때 눈금이 부드럽게 내려앉는다.

struct AltimeterState: Equatable {
    /// 화면에 보이는 위 끝(칸) — 목표로 부드럽게 따라감
    private(set) var top: Double = Double(Altimeter.minTop)
    /// 구구 표시 높이(점프하듯 부드럽게)
    private(set) var guguHeight: Double = 0

    /// 새 판: 위 끝·구구를 바로 제자리에
    mutating func reset(best: Int) {
        top = Double(Altimeter.top(climbed: 0, best: best))
        guguHeight = 0
    }

    mutating func update(_ dt: Double, climbed: Int, best: Int) {
        top += (Double(Altimeter.top(climbed: climbed, best: best)) - top) * (1 - exp(-4 * dt))
        guguHeight += (Double(climbed) - guguHeight) * (1 - exp(-10 * dt))
    }

    /// 높이(칸) → 막대 아래 0 ~ 위 1
    func fraction(_ height: Double) -> Double { Altimeter.fraction(height: height, top: top) }
}

struct AltimeterView: View {
    let state: AltimeterState
    /// 먹구름 윗면(칸)
    let cloudTop: Double
    /// 지난 최고 기록(0 = 깃발 없음)
    let best: Int
    let accessory: Accessory?
    let viewport: GameViewport

    /// 안전 영역 왼쪽 끝에서 막대 중심까지(설계 px) — 왼쪽 발판 바깥
    static let centerX: CGFloat = 40
    /// 안전 영역 위 끝에서 막대 위 끝까지(문제 판·안내 글자 아래)
    static let topInset: CGFloat = 600
    /// 안전 영역 아래 끝에서 막대 아래 끝까지
    static let bottomInset: CGFloat = 170
    private static let trackWidth: CGFloat = 22
    private static let zoneIcon: CGFloat = 50
    private static let guguIcon: CGFloat = 72
    private static let cloudIcon: CGFloat = 60
    private static let flagIcon: CGFloat = 58

    /// 구간 아이콘(구간이 시작되는 높이에 놓임)
    static func zoneArt(_ zone: SkyZone) -> String {
        switch zone {
        case .meadow: return "grass_hill"
        case .skyVillage: return "sky_cloud_puff"
        case .rainbow: return "sky_rainbow"
        case .space: return "sky_moon"
        }
    }

    var body: some View {
        let u = viewport.unit
        let safe = viewport.safeRect
        let x = safe.minX + Self.centerX * u
        let top = safe.minY + Self.topInset * u
        let bottom = safe.maxY - Self.bottomInset * u
        let height = bottom - top
        let y = { (h: Double) in bottom - CGFloat(state.fraction(h)) * height }
        ZStack {
            // 크림 알약을 세로로 세운 막대
            PillBackground()
                .opacity(0.85)
                .frame(width: height + Self.trackWidth * u, height: Self.trackWidth * u)
                .rotationEffect(.degrees(90))
                .position(x: x, y: (top + bottom) / 2)
            ForEach(SkyZone.allCases, id: \.self) { zone in
                icon(Self.zoneArt(zone), size: Self.zoneIcon * u)
                    .position(x: x, y: y(Double(zone.startHeight)))
            }
            if best > 0 {
                icon("flag", size: Self.flagIcon * u)
                    .position(x: x, y: y(Double(best)))
            }
            icon("icon_cloud", size: Self.cloudIcon * u)
                .position(x: x, y: y(cloudTop))
            gugu(size: Self.guguIcon * u)
                .position(x: x, y: y(state.guguHeight))   // 맨 앞
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func icon(_ file: String, size: CGFloat) -> some View {
        GameArt.image(file)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
    }

    /// 고도계 구구 — 옷장에서 고른 장신구도 씀(월드 구구와 같은 계산을 아이콘 크기로)
    private func gugu(size: CGFloat) -> some View {
        ZStack {
            icon(GameArt.GuguPose.idle.file, size: size)
            if let accessory {
                let place = GameArt.accessoryPlacement(accessory, pose: .idle)
                GameArt.image(GameArt.file(accessory))
                    .resizable()
                    .scaledToFit()
                    .frame(width: place.size.width * size, height: place.size.height * size)
                    .offset(x: place.center.x * size, y: -place.center.y * size)
            }
        }
        .frame(width: size, height: size)
    }
}
