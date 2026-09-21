import SwiftUI

// 프로필 탭 (Profile.tsx 이식) — 레벨/통계/업적/이용권/설정

struct ProfileView: View {
    @Environment(GameStore.self) private var game
    @Environment(ThemeStore.self) private var theme
    @Environment(PremiumStore.self) private var premium
    @Environment(\.openURL) private var openURL

    @State private var soundOn = Sound.shared.enabled
    @State private var confirmReset = false

    private let scoredModes: [GameMode] = [.challenge, .survival]

    private var accuracyTotal: Int { game.state.totalCorrect + game.state.totalWrong }
    private var accuracy: Int { accuracyTotal > 0 ? Int((Double(game.state.totalCorrect) / Double(accuracyTotal) * 100).rounded()) : 0 }
    private var unlocked: Set<String> { Set(game.state.unlockedAchievements) }
    private var weakProblems: [String] {
        game.state.wrongPool.sorted { $0.value > $1.value }.prefix(6).map { $0.key }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                levelHeader
                statsRow
                bestRecords
                if game.state.recentAccuracy.count >= 2 { accuracyTrend }
                if !weakProblems.isEmpty { weakSection }
                achievements
                premiumSection
                settings
                resetSection
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 32)
        }
        .background(Color.gg.bg.ignoresSafeArea())
        .scrollIndicators(.hidden)
    }

    private var header: some View {
        Text("프로필").font(.suite(.extrabold, 24)).foregroundStyle(Color.gg.text)
    }

    private var levelHeader: some View {
        HStack(spacing: 16) {
            Text("\(game.levelInfo.level)")
                .font(.suite(.extrabold, 24)).foregroundStyle(Color.gg.accentFg)
                .frame(width: 64, height: 64)
                .background(Color.gg.accent, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .monospacedDigit()
            VStack(alignment: .leading, spacing: 6) {
                Text("Lv.\(game.levelInfo.level) · \(Level.title(game.levelInfo.level))")
                    .font(.suite(.extrabold, 17)).foregroundStyle(Color.gg.text)
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color.gg.surface2)
                        Capsule().fill(Color.gg.accent).frame(width: geo.size.width * game.levelInfo.progress)
                    }
                }
                .frame(height: 8)
                Text("\(game.levelInfo.totalXp) XP").font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted).monospacedDigit()
            }
        }
        .padding(20)
        .ggCard(padding: 0)
    }

    private var statsRow: some View {
        HStack(spacing: 12) {
            miniStat("정답", "\(game.state.totalCorrect)")
            miniStat("정확도", "\(accuracy)%")
            miniStat("최고 콤보", "\(game.state.maxCombo)")
        }
    }

    private func miniStat(_ label: String, _ value: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.suite(.extrabold, 20)).foregroundStyle(Color.gg.text).monospacedDigit()
            Text(label).font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .ggCard(padding: 0)
    }

    private var bestRecords: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("최고 기록").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
            HStack(spacing: 12) {
                ForEach(scoredModes, id: \.self) { id in
                    let best = game.state.bestScores[id] ?? 0
                    HStack(spacing: 12) {
                        Image(systemName: ModeStyle.icon(id)).font(.system(size: 18, weight: .bold)).foregroundStyle(ModeStyle.tint(id))
                            .frame(width: 40, height: 40)
                            .background(ModeStyle.tint(id).opacity(0.15), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        VStack(alignment: .leading, spacing: 1) {
                            Text(Modes.def(id).name).font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted)
                            Text(best > 0 ? "\(best)점" : "—").font(.suite(.extrabold, 17)).foregroundStyle(Color.gg.text).monospacedDigit()
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 16).padding(.vertical, 12)
                    .ggCard(padding: 0)
                }
            }
        }
    }

    private var accuracyTrend: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("최근 정확도 추이").font(.suite(.bold, 14)).foregroundStyle(Color.gg.text)
            Sparkline(data: game.state.recentAccuracy, color: .gg.accent)
                .frame(height: 48)
        }
        .padding(20)
        .ggCard(padding: 0)
    }

    private var weakSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("집중 공략 문제").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
            FlowRow(spacing: 8) {
                ForEach(weakProblems, id: \.self) { key in
                    Text(key.replacingOccurrences(of: "x", with: " × "))
                        .font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.danger).monospacedDigit()
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(Color.gg.danger.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(Color.gg.danger.opacity(0.2), lineWidth: 1))
                }
            }
        }
    }

    private var achievements: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("업적 (\(unlocked.count)/\(Achievements.all.count))").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 4), spacing: 10) {
                ForEach(Achievements.all) { a in
                    let on = unlocked.contains(a.id)
                    VStack(spacing: 4) {
                        Image(systemName: IconMap.sf(a.icon)).font(.system(size: 18, weight: .semibold))
                        Text(a.name).font(.suite(.bold, 9)).multilineTextAlignment(.center).lineLimit(2)
                    }
                    .foregroundStyle(on ? Color.gg.accent : Color.gg.textMuted)
                    .frame(maxWidth: .infinity)
                    .aspectRatio(1, contentMode: .fit)
                    .background(on ? Color.gg.accent.opacity(0.1) : Color.gg.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(on ? Color.gg.accent.opacity(0.3) : Color.gg.border, lineWidth: 1))
                    .opacity(on ? 1 : 0.5)
                }
            }
        }
    }

    private var premiumSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("이용권").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
            VStack(spacing: 0) {
                if premium.isPremium {
                    HStack(spacing: 12) {
                        Image(systemName: "sparkles").foregroundStyle(Color.gg.accent)
                        Text("평생 이용권").font(.suite(.bold, 15)).foregroundStyle(Color.gg.text)
                        Spacer()
                        Text("이용 중").font(.suite(.extrabold, 12)).foregroundStyle(Color.gg.accent)
                            .padding(.horizontal, 10).padding(.vertical, 4)
                            .background(Color.gg.accent.opacity(0.12), in: Capsule())
                    }
                    .padding(.horizontal, 20).padding(.vertical, 16)
                } else {
                    row(icon: "sparkles", label: "평생 이용권 · 커피 한 잔 값", action: premium.price) { premium.openPaywall() }
                }
                divider
                row(icon: "doc.text", label: "이용약관", action: "") { open(PremiumConfig.Legal.terms) }
                divider
                row(icon: "checkmark.shield", label: "개인정보처리방침", action: "") { open(PremiumConfig.Legal.privacy) }
            }
            .ggCard(padding: 0)
        }
    }

    private var settings: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("설정").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
            VStack(spacing: 0) {
                row(icon: theme.theme == .dark ? "moon.fill" : "sun.max.fill", label: "다크 모드",
                    action: theme.theme == .dark ? "켜짐" : "꺼짐") { theme.toggle() }
                divider
                row(icon: soundOn ? "speaker.wave.2.fill" : "speaker.slash.fill", label: "효과음",
                    action: soundOn ? "켜짐" : "꺼짐") {
                    soundOn.toggle(); Sound.shared.setEnabled(soundOn)
                }
            }
            .ggCard(padding: 0)
        }
    }

    private var resetSection: some View {
        Group {
            if !confirmReset {
                GGButton(variant: .ghost, size: .md, action: { confirmReset = true }) {
                    Image(systemName: "arrow.counterclockwise")
                    Text("기록 초기화").foregroundStyle(Color.gg.danger)
                }
            } else {
                HStack(spacing: 8) {
                    GGButton(variant: .surface, size: .md, action: { confirmReset = false }) { Text("취소") }
                    GGButton(variant: .danger, size: .md, action: { game.resetProgress(); confirmReset = false }) { Text("초기화 확인") }
                }
            }
        }
        .padding(.top, 4)
    }

    private var divider: some View { Rectangle().fill(Color.gg.border).frame(height: 1) }

    private func row(icon: String, label: String, action: String, onTap: @escaping () -> Void) -> some View {
        Button { Haptics.impact(.light); onTap() } label: {
            HStack(spacing: 12) {
                Image(systemName: icon).foregroundStyle(Color.gg.textMuted).frame(width: 24)
                Text(label).font(.suite(.bold, 15)).foregroundStyle(Color.gg.text)
                Spacer()
                Text(action).font(.suite(.bold, 14)).foregroundStyle(Color.gg.accent)
            }
            .padding(.horizontal, 20).padding(.vertical, 16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func open(_ url: String) {
        if let u = URL(string: url) { openURL(u) }
    }
}

// 간단한 플로우 레이아웃 (칩 줄바꿈)
struct FlowRow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0
        for sub in subviews {
            let size = sub.sizeThatFits(.unspecified)
            if x + size.width > maxWidth {
                x = 0; y += rowHeight + spacing; rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: maxWidth, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
        for sub in subviews {
            let size = sub.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX {
                x = bounds.minX; y += rowHeight + spacing; rowHeight = 0
            }
            sub.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
