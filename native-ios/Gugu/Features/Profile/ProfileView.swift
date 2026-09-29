import SwiftUI

// 프로필 탭 (Profile.tsx 이식) — 레벨/주간 리포트/통계/업적.
// 설정·계정·이용권은 톱니바퀴 → SettingsView 시트로 옮겼다 (한 화면에 기능이 너무 많이 섞여 있었다).

struct ProfileView: View {
    @Environment(GameStore.self) private var game
    @Environment(PremiumStore.self) private var premium
    @Environment(AuthStore.self) private var auth
    @Environment(SessionStore.self) private var session
    /// 창 전체의 테마 — 앱 테마가 '기기 설정'이면 곧 기기 테마다 (설정 시트에 넘긴다)
    @Environment(\.colorScheme) private var colorScheme

    @State private var showSettings = false
    @State private var selectedAchievement: AchievementDef?

    private let scoredModes: [GameMode] = [.challenge, .survival]

    private var accuracyTotal: Int { game.state.totalCorrect + game.state.totalWrong }
    private var accuracy: Int { accuracyTotal > 0 ? Int((Double(game.state.totalCorrect) / Double(accuracyTotal) * 100).rounded()) : 0 }
    private var unlocked: Set<String> { Set(game.state.unlockedAchievements) }
    private var weakProblems: [String] {
        game.state.wrongPool.sorted { $0.value != $1.value ? $0.value > $1.value : $0.key < $1.key }
            .prefix(6).map { $0.key }
    }
    private var report: WeeklyReport {
        WeeklyReport.build(game.state.dayLog, today: Commit.todayStr())
    }
    /// 이메일을 아직 안 붙인 익명 계정 — 기기를 잃으면 기록도 잃는다
    private var needsAccountLink: Bool {
        auth.state != .disabled && !auth.isPermanent
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                levelHeader
                statsRow
                WeeklyReportCard(
                    report: report,
                    onReview: game.state.wrongPool.isEmpty ? nil : { session.startReview() }
                )
                bestRecords
                if game.state.recentAccuracy.count >= 2 { accuracyTrend }
                if !weakProblems.isEmpty { weakSection }
                achievements
                if needsAccountLink { accountNudge }
                if !premium.isPremium { premiumNudge }
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 32)
            .readableWidth()
        }
        .background(Color.gg.bg.ignoresSafeArea())
        .scrollIndicators(.hidden)
        .statusBarBackdrop()
        .sheet(isPresented: $showSettings) { SettingsView(deviceScheme: colorScheme) }
        .sheet(item: $selectedAchievement) { AchievementDetailSheet(def: $0) }
    }

    private var header: some View {
        HStack {
            Text("프로필").font(.suite(.extrabold, 24)).foregroundStyle(Color.gg.text)
                .accessibilityAddTraits(.isHeader)
            Spacer()
            Button {
                Haptics.impact(.light)
                showSettings = true
            } label: {
                Image(systemName: "gearshape.fill")
                    .font(.system(size: 20, weight: .semibold)).foregroundStyle(Color.gg.textMuted)
                    .frame(width: 44, height: 44)
                    .background(Color.gg.surface2, in: Circle())
            }
            .accessibilityLabel("설정")
        }
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
                Text("\(game.levelInfo.totalXp) XP · 다음 레벨까지 \(game.levelInfo.xpForNextLevel - game.levelInfo.currentLevelXp) XP")
                    .font(.suite(.bold, 12)).foregroundStyle(Color.gg.textMuted).monospacedDigit()
            }
        }
        .padding(20)
        .ggCard(padding: 0)
        .accessibilityElement(children: .combine)
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
        .accessibilityElement(children: .combine)
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
                    .accessibilityElement(children: .combine)
                }
            }
        }
    }

    private var accuracyTrend: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("최근 정확도 추이").font(.suite(.bold, 14)).foregroundStyle(Color.gg.text)
                    Text("최근 \(game.state.recentAccuracy.count)판 · 점선은 50%")
                        .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                }
                Spacer(minLength: 8)
                // 그래프만으로는 현재 수준을 읽을 수 없어 최신값을 숫자로 집어 준다
                Text("\(game.state.recentAccuracy.last ?? 0)%")
                    .font(.suiteNum(.extrabold, 24)).foregroundStyle(Color.gg.accent)
            }
            HStack(spacing: 10) {
                // y축이 0~100 고정임을 눈으로 확인할 수 있게 눈금을 적는다
                ZStack {
                    Text("100").frame(maxHeight: .infinity, alignment: .top)
                    Text("50")
                    Text("0").frame(maxHeight: .infinity, alignment: .bottom)
                }
                .font(.suiteNum(.bold, 9)).foregroundStyle(Color.gg.textMuted)
                .frame(width: 24, height: 64)

                Sparkline(data: game.state.recentAccuracy, color: .gg.accent,
                          lowerBound: 0, upperBound: 100, baseline: 50, markLast: true)
                    .frame(height: 64)
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("최근 정확도 \(game.state.recentAccuracy.map { "\($0)%" }.joined(separator: ", "))")
        }
        .padding(20)
        .ggCard(padding: 0)
    }

    private var weakSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("집중 공략 문제").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
                Spacer()
                Button {
                    Haptics.impact(.light)
                    session.startReview()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "play.fill").font(.system(size: 10, weight: .bold))
                        Text("복습 시작").font(.suite(.extrabold, 13))
                    }
                    .foregroundStyle(Color.gg.accentFg)
                    .padding(.horizontal, 12).frame(height: 32)
                    .background(Color.gg.accent, in: Capsule())
                }
                .buttonStyle(PressScaleStyle())
            }
            FlowRow(spacing: 8) {
                ForEach(weakProblems, id: \.self) { key in
                    Text(key.replacingOccurrences(of: "x", with: " × "))
                        .font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.danger).monospacedDigit()
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(Color.gg.danger.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(Color.gg.danger.opacity(0.2), lineWidth: 1))
                        .accessibilityLabel(key.replacingOccurrences(of: "x", with: " 곱하기 "))
                }
            }
        }
    }

    private var achievements: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text("업적 (\(unlocked.count)/\(Achievements.all.count))").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
                Spacer()
                Text("눌러서 목표 보기").font(.suite(.medium, 11)).foregroundStyle(Color.gg.textMuted.opacity(0.8))
            }
            // 3열 — 4열에서는 "오백 문제 돌파" 같은 이름이 한 줄로 잘렸다.
            // 정사각 비율을 버리고 라벨 2줄을 항상 확보해 타일 높이를 맞춘다.
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                ForEach(Achievements.all) { a in
                    let on = unlocked.contains(a.id)
                    let fraction = Achievements.progress(a, game.state).fraction
                    Button {
                        Haptics.impact(.light)
                        selectedAchievement = a
                    } label: {
                        VStack(spacing: 8) {
                            Image(systemName: IconMap.sf(a.icon)).font(.system(size: 22, weight: .semibold))
                            Text(a.name)
                                .font(.suite(.bold, 11))
                                .multilineTextAlignment(.center)
                                .lineLimit(2, reservesSpace: true)
                                .minimumScaleFactor(0.85)
                            // 잠긴 업적은 얼마나 왔는지 가는 막대로 보여 준다
                            ZStack(alignment: .leading) {
                                Capsule().fill(Color.gg.surface2)
                                Capsule().fill(Color.gg.accent.opacity(0.7))
                                    .frame(width: 44 * fraction)
                            }
                            .frame(width: 44, height: 3)
                            .opacity(on ? 0 : 1)
                        }
                        .foregroundStyle(on ? Color.gg.accent : Color.gg.textMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .padding(.horizontal, 8)
                        .background(on ? Color.gg.accent.opacity(0.1) : Color.gg.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(on ? Color.gg.accent.opacity(0.3) : Color.gg.border, lineWidth: 1))
                        .opacity(on ? 1 : 0.7)
                    }
                    .buttonStyle(PressScaleStyle())
                    .accessibilityLabel("\(a.name), \(on ? "달성" : "진행 중")")
                }
            }
        }
    }

    /// 계정 연결 권유 — 전체 흐름은 설정에 있고, 여기서는 존재를 알린다
    private var accountNudge: some View {
        Button {
            Haptics.impact(.light)
            showSettings = true
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "person.badge.shield.checkmark")
                    .font(.system(size: 18, weight: .bold)).foregroundStyle(Color.gg.accent)
                    .frame(width: 40, height: 40)
                    .background(Color.gg.accent.opacity(0.15), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text("기록 지키기").font(.suite(.extrabold, 15)).foregroundStyle(Color.gg.text)
                    Text("보호자 이메일을 연결하면 기기를 바꿔도 기록이 남아요")
                        .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 4)
                Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold)).foregroundStyle(Color.gg.textMuted)
            }
            .padding(16)
            .ggCard(padding: 0)
        }
        .buttonStyle(PressScaleStyle())
    }

    private var premiumNudge: some View {
        Button {
            Haptics.impact(.light)
            premium.openPaywall()
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "sparkles")
                    .font(.system(size: 18, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 40, height: 40)
                    .background(Color.white.opacity(0.2), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text("평생 이용권").font(.suite(.extrabold, 15)).foregroundStyle(.white)
                    Text("모든 모드와 3D 어드벤처 · 한 번 구매로 계속")
                        .font(.suite(.medium, 12)).foregroundStyle(.white.opacity(0.85))
                }
                Spacer(minLength: 4)
                Text(premium.price).font(.suite(.extrabold, 14)).foregroundStyle(.white)
            }
            .padding(16)
            .background(
                LinearGradient(colors: [Color.gg.indigo, Color.gg.violet], startPoint: .topLeading, endPoint: .bottomTrailing),
                in: RoundedRectangle(cornerRadius: 20, style: .continuous)
            )
        }
        .buttonStyle(PressScaleStyle())
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
