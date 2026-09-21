import SwiftUI
import UIKit

// 디자인 토큰 — globals.css의 라이트/다크 팔레트를 동적 컬러로 이식.
// UIColor dynamic provider로 콜러스킴에 따라 자동 전환(preferredColorScheme가 구동).

private func dyn(light: (Int, Int, Int), dark: (Int, Int, Int)) -> Color {
    Color(uiColor: UIColor { trait in
        let c = trait.userInterfaceStyle == .dark ? dark : light
        return UIColor(red: CGFloat(c.0) / 255, green: CGFloat(c.1) / 255, blue: CGFloat(c.2) / 255, alpha: 1)
    })
}

private func solid(_ r: Int, _ g: Int, _ b: Int) -> Color {
    Color(red: Double(r) / 255, green: Double(g) / 255, blue: Double(b) / 255)
}

/// 앱 시맨틱 컬러 — 뷰에서 `Color.gg.accent` 형태로 사용
enum GG {
    static let bg          = dyn(light: (248, 250, 252), dark: (8, 11, 22))
    static let surface     = dyn(light: (255, 255, 255), dark: (17, 23, 39))
    static let surface2    = dyn(light: (241, 245, 249), dark: (30, 39, 60))
    static let text        = dyn(light: (15, 23, 42),    dark: (241, 245, 249))
    static let textMuted   = dyn(light: (100, 116, 139), dark: (148, 163, 184))
    static let border      = dyn(light: (226, 232, 240), dark: (39, 49, 73))
    static let accent      = dyn(light: (99, 102, 241),  dark: (129, 140, 248))
    static let accentStrong = dyn(light: (79, 70, 229),  dark: (165, 180, 252))
    static let accentFg    = dyn(light: (255, 255, 255), dark: (10, 13, 25))
    static let success     = dyn(light: (16, 185, 129),  dark: (52, 211, 153))
    static let warning     = dyn(light: (245, 158, 11),  dark: (251, 191, 36))
    static let danger      = dyn(light: (244, 63, 94),   dark: (251, 113, 133))

    // 모드 틴트 (Tailwind 500) — 라이트/다크 공용
    static let sky     = solid(14, 165, 233)
    static let amber   = solid(245, 158, 11)
    static let rose    = solid(244, 63, 94)
    static let violet  = solid(139, 92, 246)
    static let emerald = solid(16, 185, 129)
    static let indigo  = solid(99, 102, 241)
    static let purple  = solid(147, 51, 234)
}

extension Color {
    static let gg = GG.self
}

// MARK: - 폰트 (SUITE)

enum SUITE: String {
    case light = "SUITE-Light"
    case regular = "SUITE-Regular"
    case medium = "SUITE-Medium"
    case semibold = "SUITE-SemiBold"
    case bold = "SUITE-Bold"
    case extrabold = "SUITE-ExtraBold"
    case heavy = "SUITE-Heavy"
}

extension Font {
    /// SUITE 커스텀 폰트 — 크기 지정
    static func suite(_ weight: SUITE, _ size: CGFloat) -> Font {
        .custom(weight.rawValue, size: size)
    }
    /// 숫자 강조(고정폭) — SUITE + tabular-nums 느낌은 .monospacedDigit 모디파이어로 보강
    static func suiteNum(_ weight: SUITE, _ size: CGFloat) -> Font {
        .custom(weight.rawValue, size: size)
    }
}

// MARK: - 공용 색/폰트 헬퍼 뷰 모디파이어

extension View {
    /// 카드형 표면 — border + surface + 라운드
    func ggCard(padding: CGFloat = 20, radius: CGFloat = 24) -> some View {
        self
            .padding(padding)
            .background(Color.gg.surface, in: RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: radius, style: .continuous)
                    .strokeBorder(Color.gg.border, lineWidth: 1)
            )
    }
}

/// hex 문자열(#rrggbb) → Color (어드벤처 NPC/테마 색용)
extension Color {
    init(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("#") { s.removeFirst() }
        var v: UInt64 = 0
        Scanner(string: s).scanHexInt64(&v)
        let r, g, b: Double
        if s.count == 6 {
            r = Double((v >> 16) & 0xff) / 255
            g = Double((v >> 8) & 0xff) / 255
            b = Double(v & 0xff) / 255
        } else {
            r = 0; g = 0; b = 0
        }
        self.init(red: r, green: g, blue: b)
    }
}
