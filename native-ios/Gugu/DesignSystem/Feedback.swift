import SwiftUI

// 피드백 오버레이 — Confetti / LevelUpOverlay / AchievementToast (components/feedback 이식)

// MARK: - lucide 아이콘명 → SF Symbols 매핑 (업적 아이콘)

enum IconMap {
    static func sf(_ lucide: String) -> String {
        switch lucide {
        case "Check": return "checkmark"
        case "Target": return "target"
        case "Award": return "rosette"
        case "Zap": return "bolt.fill"
        case "Flame": return "flame.fill"
        case "CalendarCheck": return "calendar.badge.checkmark"
        case "CalendarHeart": return "calendar"
        case "Crown": return "crown.fill"
        case "Star": return "star.fill"
        case "GraduationCap": return "graduationcap.fill"
        case "Sparkles": return "sparkles"
        case "Trophy": return "trophy.fill"
        case "TrendingUp": return "chart.line.uptrend.xyaxis"
        case "Rocket": return "airplane"
        case "Timer": return "timer"
        case "HeartPulse": return "heart.fill"
        case "Compass": return "safari.fill"
        case "Swords": return "shield.lefthalf.filled"
        case "Gauge": return "gauge.medium"
        case "Medal": return "medal.fill"
        case "Flag": return "flag.fill"
        case "Gem": return "diamond.fill"
        default: return "star.fill"
        }
    }

    /// 업적 id → (이름, SF 아이콘) — 코어/어드벤처 업적 통합 조회
    static func achievement(_ id: String) -> (name: String, icon: String)? {
        if let a = Achievements.get(id) { return (a.name, sf(a.icon)) }
        if let a = AdvAchievements.get(id) { return (a.name, sf(a.icon)) }
        return nil
    }
}

// MARK: - Confetti

struct ConfettiView: View {
    var trigger: Int
    private let colors: [Color] = [.gg.accent, .gg.warning, .gg.success, .gg.danger, .gg.sky, .gg.violet]

    struct Piece: Identifiable {
        let id = UUID()
        let x: CGFloat
        let color: Color
        let delay: Double
        let rotation: Double
        let size: CGFloat
    }
    @State private var pieces: [Piece] = []
    @State private var animate = false

    var body: some View {
        GeometryReader { geo in
            ZStack {
                ForEach(pieces) { p in
                    Rectangle()
                        .fill(p.color)
                        .frame(width: p.size, height: p.size * 1.6)
                        .rotationEffect(.degrees(animate ? p.rotation + 360 : p.rotation))
                        .position(x: p.x * geo.size.width,
                                  y: animate ? geo.size.height + 40 : -40)
                        .opacity(animate ? 0 : 1)
                        .animation(.easeIn(duration: 1.8).delay(p.delay), value: animate)
                }
            }
        }
        .allowsHitTesting(false)
        .onChange(of: trigger) { _, newValue in
            guard newValue > 0 else { return }
            spawn()
        }
    }

    private func spawn() {
        pieces = (0..<40).map { _ in
            Piece(x: CGFloat.random(in: 0...1), color: colors.randomElement()!,
                  delay: Double.random(in: 0...0.4), rotation: Double.random(in: 0...360),
                  size: CGFloat.random(in: 6...11))
        }
        animate = false
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.02) {
            animate = true
        }
    }
}

// MARK: - LevelUpOverlay

struct LevelUpOverlay: View {
    var show: Bool
    var level: Int
    var onClose: () -> Void

    var body: some View {
        if show {
            ZStack {
                Color.black.opacity(0.6).ignoresSafeArea().onTapGesture(perform: onClose)
                VStack(spacing: 12) {
                    Image(systemName: "sparkles").font(.system(size: 44)).foregroundStyle(Color.gg.warning)
                    Text("레벨 업!").font(.suite(.extrabold, 28)).foregroundStyle(.white)
                    Text("Lv.\(level)").font(.suite(.heavy, 48)).foregroundStyle(Color.gg.accent).monospacedDigit()
                    Text(Level.title(level)).font(.suite(.bold, 15)).foregroundStyle(.white.opacity(0.8))
                }
                .padding(40)
                .background(Color.gg.surface, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
                .transition(.scale.combined(with: .opacity))
            }
            .onAppear { Haptics.success() }
        }
    }
}

// MARK: - AchievementToast

struct AchievementToast: View {
    var ids: [String]
    @State private var visible: [String] = []

    var body: some View {
        VStack(spacing: 8) {
            ForEach(visible, id: \.self) { id in
                if let a = IconMap.achievement(id) {
                    HStack(spacing: 10) {
                        Image(systemName: a.icon).font(.system(size: 18, weight: .bold))
                            .foregroundStyle(Color.gg.warning)
                            .frame(width: 40, height: 40)
                            .background(Color.gg.warning.opacity(0.15), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        VStack(alignment: .leading, spacing: 1) {
                            Text("업적 달성").font(.suite(.bold, 11)).foregroundStyle(Color.gg.textMuted)
                            Text(a.name).font(.suite(.extrabold, 15)).foregroundStyle(Color.gg.text)
                        }
                        Spacer()
                    }
                    .padding(12)
                    .background(Color.gg.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Color.gg.border, lineWidth: 1))
                    .shadow(color: .black.opacity(0.15), radius: 8, y: 4)
                    .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
        }
        .onChange(of: ids) { _, _ in showAll() }
        .onAppear { showAll() }
    }

    private func showAll() {
        guard !ids.isEmpty else { return }
        withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) { visible = ids }
        Haptics.success()
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.8) {
            withAnimation { visible = [] }
        }
    }
}
