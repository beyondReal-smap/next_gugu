import SwiftUI
import UIKit

// 재디자인 V1 「구구의 모험 지도」 아이 화면 토큰 (홈·지도·보물·섬 시트·잠김 카드·문 카드·탭 바, 계획서 redesign-v1 3-0).
// 낮·밤 두 벌이고 테마 선택(preferredColorScheme)을 따른다 — GG 토큰과 같은 UIColor 동적 색.
// 보호자 구역(문 카드 제외)은 새 토큰을 만들지 않고 GG 를 쓴다. Android designsystem/WorldTheme.kt(LocalWorld)와 같은 값.

/// 0xRRGGBB → 색
private func rgb(_ hex: UInt32) -> UIColor {
    UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
}

private func world(day: UInt32, night: UInt32) -> Color {
    let d = rgb(day), n = rgb(night)
    return Color(uiColor: UIColor { $0.userInterfaceStyle == .dark ? n : d })
}

private func world(_ both: UInt32) -> Color { Color(uiColor: rgb(both)) }

/// 아이 화면 색 — 뷰에서 `Color.world.sun` 형태로 쓴다
enum World {
    /// 테두리 3, 아래 그림자 5. 밤은 V1 값(#2B2D5E)이 바탕에 묻혀서 판보다 어두운 값
    static let line = world(day: 0x2B2D5E, night: 0x0F1236)
    /// sun·흰 숫자 원 위의 글과 ▶ — 낮·밤 모두 남색(밤 text 는 밝은 색이라 노란 바탕에서 안 읽힌다)
    static let ink = world(0x2B2D5E)

    static let text = world(day: 0x2B2D5E, night: 0xF4F6FF)
    static let text2 = world(day: 0x5B6092, night: 0xC3C9F2)
    /// 꺼진 탭
    static let text3 = world(day: 0x98A0C6, night: 0x8C94CC)

    /// 홈·지도 바탕 세로 그라데이션 위 → 아래
    static let sky1 = world(day: 0x79CBFF, night: 0x161A4A)
    static let sky2 = world(day: 0xBDE7FF, night: 0x2A3379)
    static let sky3 = world(day: 0xEDF9FF, night: 0x4352A6)

    /// 홈 언덕
    static let grass = world(day: 0x93DE72, night: 0x3E9A6C)
    static let grass2 = world(day: 0x62C760, night: 0x2B7756)

    /// 판, 시트, 탭 바
    static let paper = world(day: 0xFFFFFF, night: 0x283170)
    static let paper2 = world(day: 0xF3F7FD, night: 0x333E86)
    /// 보물 탭 바탕
    static let cream = world(day: 0xFFF5DC, night: 0x333E86)
    /// 미션 고리 바탕·점선 길
    static let track = world(day: 0xE1E8F4, night: 0x3D4892)

    /// 누르면 시작하는 것에만 — 게임 이름판, ▶, 오늘의 섬 숫자, 켜진 탭
    static let sun = world(0xFFD45C)
    /// 미션 고리·완료
    static let ok = world(0x1FAE6E)
    /// 도장
    static let pink = world(0xFF9DBB)
    /// 레벨
    static let lav = world(0xB3B1F7)

    /// 홈 하늘 바탕(위 → 아래)
    static let skyGradient = LinearGradient(colors: [sky1, sky2, sky3], startPoint: .top, endPoint: .bottom)
    /// 게임 문 바탕(위 → 아래) — 지금의 짙은 초록 받침(#1E3A2C) 대신
    static let stairsDoor = LinearGradient(colors: [world(day: 0xBFE6FF, night: 0x2F4FA0), world(day: 0xEAF7FF, night: 0x4366B8)],
                                           startPoint: .top, endPoint: .bottom)
    static let monsterDoor = LinearGradient(colors: [world(day: 0xBFF0D2, night: 0x256B58), world(day: 0xE9FBF0, night: 0x318469)],
                                            startPoint: .top, endPoint: .bottom)
}

extension Color {
    static let world = World.self
}

// MARK: - 젤리 판 모양 (V1 의 유일한 모양 규칙)

extension View {
    /// 젤리 판 — line 테두리 3 + 아래로 5 어긋난 line 그림자, 블러 없음. 그림자는 CSS box-shadow 처럼 자리를 차지하지 않는다
    func worldCard<S: ShapeStyle>(_ fill: S, radius: CGFloat = 24, border: CGFloat = 3, drop: CGFloat = 5) -> some View {
        background {
            let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
            ZStack {
                shape.fill(Color.world.line).offset(y: drop)
                shape.fill(fill)
                shape.strokeBorder(Color.world.line, lineWidth: border)
            }
        }
    }

    /// 종이(paper) 젤리 판
    func worldCard(radius: CGFloat = 24) -> some View {
        worldCard(Color.world.paper, radius: radius)
    }
}

/// 알약 칩 — 그림 + 글. 폭은 내용대로 커지고 글이 길면 두 줄로 감긴다(고정 폭·글자 줄이기 없음, 계획서 3-0).
/// 고정하는 것은 최소 높이와 안쪽 여백뿐이다
struct WorldChip<Icon: View>: View {
    let text: String
    var fill: Color = .world.paper
    var foreground: Color = .world.text
    @ViewBuilder var icon: () -> Icon

    static var minHeight: CGFloat { 36 }

    var body: some View {
        HStack(spacing: 5) {
            icon()
            Text(text)
                .font(.suite(.heavy, 16))
                .foregroundStyle(foreground)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.leading, 7)
        .padding(.trailing, 12)
        .padding(.vertical, 4)
        .frame(minHeight: Self.minHeight)
        .background {
            ZStack {
                Capsule().fill(Color.world.line).offset(y: 3)
                Capsule().fill(fill)
                Capsule().strokeBorder(Color.world.line, lineWidth: 2.5)
            }
        }
    }
}

extension WorldChip where Icon == EmptyView {
    init(text: String, fill: Color = .world.paper, foreground: Color = .world.text) {
        self.init(text: text, fill: fill, foreground: foreground) { EmptyView() }
    }
}

// MARK: - 그림

/// 재디자인 그림 — Assets.xcassets/World(이름공간 폴더, universal 1배). 크기는 화면에 쓰는 가장 큰 pt 의 3배 px 라
/// 뷰에서는 늘 resizable + frame 으로 크기를 정한다. 다시 만들기: tools/redesign/import-art-ios.py
/// 그림을 바꿔 끼울 일(자리표시 포함)이 생기면 여기 한 곳만 고친다
enum WorldArt: String, CaseIterable {
    case tabHome = "tab_home"
    case tabMap = "tab_map"
    case tabTreasure = "tab_treasure"
    case iconKey = "icon_key"
    case iconBook = "icon_book"
    case iconTrophy = "icon_trophy"
    case houseGugu = "house_gugu"
    case portalStairs = "portal_stairs"
    case portalForest = "portal_forest"
    case isle1 = "isle_1"
    case isle2 = "isle_2"
    case isle3 = "isle_3"
    case isle4 = "isle_4"
    case isle5 = "isle_5"
    case isle6 = "isle_6"

    var assetName: String { "World/\(rawValue)" }

    /// 크기를 정할 수 있는 그림 — 장식이라 화면 읽기에서 숨긴다(이름은 단추에 단다, 계획서 3-0)
    var image: some View {
        Image(assetName).resizable().scaledToFit().accessibilityHidden(true)
    }
}
