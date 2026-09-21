import SwiftUI

// 공통 UI 컴포넌트 (components/ui 이식)

// MARK: - Button

enum GGButtonVariant {
    case primary, surface, ghost, danger
}
enum GGButtonSize {
    case sm, md, lg
    var height: CGFloat { self == .sm ? 36 : self == .md ? 44 : 56 }
    var font: CGFloat { self == .sm ? 14 : self == .md ? 15 : 16 }
    var hPad: CGFloat { self == .sm ? 12 : self == .md ? 20 : 24 }
}

struct GGButton<Label: View>: View {
    var variant: GGButtonVariant = .primary
    var size: GGButtonSize = .md
    var action: () -> Void
    @ViewBuilder var label: () -> Label
    @Environment(\.isEnabled) private var isEnabled

    private var bg: Color {
        switch variant {
        case .primary: return .gg.accent
        case .surface: return .gg.surface2
        case .ghost: return .clear
        case .danger: return .gg.danger
        }
    }
    private var fg: Color {
        switch variant {
        case .primary: return .gg.accentFg
        case .surface: return .gg.text
        case .ghost: return .gg.textMuted
        case .danger: return .white
        }
    }

    var body: some View {
        Button(action: {
            Haptics.impact(.light)
            action()
        }) {
            HStack(spacing: 8) { label() }
                .font(.suite(.bold, size.font))
                .foregroundStyle(fg)
                .frame(maxWidth: .infinity)
                .frame(height: size.height)
                .padding(.horizontal, size.hPad)
                .background(bg, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(PressScaleStyle())
        .opacity(isEnabled ? 1 : 0.4)
        .allowsHitTesting(isEnabled)
    }
}

/// whileTap scale 0.97 대응
struct PressScaleStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.6), value: configuration.isPressed)
    }
}

// MARK: - ProgressRing

struct ProgressRing<Content: View>: View {
    var value: Double            // 0~1
    var size: CGFloat = 120
    var stroke: CGFloat = 10
    var track: Color = .gg.surface2
    var bar: Color = .gg.accent
    @ViewBuilder var content: () -> Content

    var body: some View {
        ZStack {
            Circle()
                .stroke(track, lineWidth: stroke)
            Circle()
                .trim(from: 0, to: max(0, min(1, value)))
                .stroke(bar, style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.spring(response: 0.6, dampingFraction: 0.7), value: value)
            content()
        }
        .frame(width: size, height: size)
    }
}

// MARK: - Stars (마스터리 별점)

struct StarsView: View {
    var count: Int          // 0~3
    var max: Int = 3
    var size: CGFloat = 14
    var body: some View {
        HStack(spacing: 2) {
            ForEach(0..<max, id: \.self) { i in
                Image(systemName: i < count ? "star.fill" : "star")
                    .font(.system(size: size))
                    .foregroundStyle(i < count ? Color.gg.warning : Color.gg.border)
            }
        }
    }
}

// MARK: - Pill / Chip

struct Pill<Content: View>: View {
    var bg: Color
    var fg: Color
    @ViewBuilder var content: () -> Content
    var body: some View {
        HStack(spacing: 6) { content() }
            .font(.suite(.extrabold, 12))
            .foregroundStyle(fg)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(bg, in: Capsule())
    }
}

// MARK: - 모드 아이콘/틴트 (modeIcons.ts 이식)

enum ModeStyle {
    static func icon(_ mode: GameMode) -> String {
        switch mode {
        case .practice: return "book.fill"
        case .timeAttack: return "timer"
        case .challenge: return "bolt.fill"
        case .survival: return "heart.fill"
        case .missing: return "puzzlepiece.fill"
        case .truefalse: return "scalemass.fill"
        case .adventure: return "shield.lefthalf.filled"
        }
    }
    static func tint(_ mode: GameMode) -> Color {
        switch mode {
        case .practice: return .gg.accent
        case .timeAttack: return .gg.sky
        case .challenge: return .gg.amber
        case .survival: return .gg.rose
        case .missing: return .gg.violet
        case .truefalse: return .gg.emerald
        case .adventure: return .gg.indigo
        }
    }
}
