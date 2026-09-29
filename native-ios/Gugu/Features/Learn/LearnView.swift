import SwiftUI

// 학습 탭 (Learn.tsx 이식) — 모드 선택 + 단 맵/마스터리

struct LearnView: View {
    @Environment(GameStore.self) private var game
    @Environment(SessionStore.self) private var session
    @Environment(PremiumStore.self) private var premium
    @Environment(Router.self) private var router

    @State private var mode: GameMode = .practice
    /// 표 보기 시트로 연 단
    @State private var sheetTable: TableChoice?
    /// 표 시트에서 "연습"을 누른 단 — 시트가 완전히 닫힌 뒤 세션을 띄운다
    /// (닫히는 도중에 전체화면을 띄우면 표시가 무시될 수 있다)
    @State private var pendingPractice: Int?

    private struct TableChoice: Identifiable {
        let table: Int
        var id: Int { table }
    }

    private let tables = Array(Problems.minTable...Problems.maxTable)
    private var def: ModeDef { Modes.def(mode) }
    private var totalStars: Int { Achievements.totalStars(game.state) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header
                modeCards
                Text(def.detail).font(.suite(.medium, 14)).foregroundStyle(Color.gg.textMuted)
                // 모드를 고르면 바로 단을 고르게 — 미니게임 링크가 이 사이에 끼어 흐름이 끊기던 것을 하단으로 옮겼다
                if def.supportsTable { tableSection } else { challengeCard }
                gameLinks
                    .padding(.top, 12)
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 24)
            .readableWidth()
        }
        .background(Color.gg.bg.ignoresSafeArea())
        .scrollIndicators(.hidden)
        .statusBarBackdrop()
        .sheet(item: $sheetTable, onDismiss: {
            guard let t = pendingPractice else { return }
            pendingPractice = nil
            session.start(.practice, table: t)
        }) { choice in
            TableSheet(table: choice.table) {
                pendingPractice = choice.table
                sheetTable = nil
            }
        }
    }

    private var header: some View {
        HStack {
            Text("학습").font(.suite(.extrabold, 24)).foregroundStyle(Color.gg.text)
            Spacer()
            Pill(bg: Color.gg.warning.opacity(0.12), fg: .gg.warning) {
                Image(systemName: "trophy.fill")
                Text("\(totalStars)/24").monospacedDigit()
            }
        }
    }

    // 달리기·받기 미니게임 진입 (웹 Learn 의 게임 링크 대응)
    private var gameLinks: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("움직이며 연습하기").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
                .accessibilityAddTraits(.isHeader)
            gameLink("구구 점프", "정답을 골라 장애물 넘기", "figure.run") { router.runnerOpen = true }
            gameLink("구구 레인", "길을 바꿔 피하고 정답 길로", "rectangle.split.1x2.fill") { router.laneOpen = true }
            gameLink("구구 바구니", "정답 열매를 바구니로 쏙", "basket.fill",
                     bg: Color(hex: "#794124"), fg: Color(hex: "#ffe7a3"), tint: Color.gg.warning) { router.basketOpen = true }
        }
    }

    private func gameLink(
        _ title: String, _ desc: String, _ icon: String,
        bg: Color = Color(hex: "#153f35"), fg: Color = Color(hex: "#dbef9e"), tint: Color = Color.gg.emerald,
        action: @escaping () -> Void
    ) -> some View {
        Button {
            Haptics.impact(.light)
            action()
        } label: {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous).fill(bg).frame(width: 40, height: 40)
                    Image(systemName: icon).font(.system(size: 18, weight: .bold)).foregroundStyle(fg)
                }
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(title).font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.text)
                        // 무료 사용자에게 체험이라는 것을 미리 알린다
                        if let badge = MinigameTrial.badge(isPremium: premium.isPremium) {
                            Text(badge)
                                .font(.suite(.extrabold, 10)).foregroundStyle(tint)
                                .padding(.horizontal, 7).padding(.vertical, 2)
                                .background(tint.opacity(0.12), in: Capsule())
                        }
                    }
                    Text(desc).font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                }
                Spacer(minLength: 4)
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .bold)).foregroundStyle(Color.gg.textMuted)
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(tint.opacity(0.06), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(tint.opacity(0.25), lineWidth: 1))
        }
        .buttonStyle(PressScaleStyle())
    }

    private var modeCards: some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
            ForEach(Modes.list, id: \.id) { m in
                let active = m.id == mode
                let best = m.scored ? (game.state.bestScores[m.id] ?? 0) : 0
                let locked = PremiumConfig.isPremiumMode(m.id) && !premium.isPremium
                Button {
                    Haptics.impact(.light)
                    if locked { premium.openPaywall() } else { mode = m.id }
                } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        Image(systemName: ModeStyle.icon(m.id))
                            .font(.system(size: 18, weight: .bold)).foregroundStyle(ModeStyle.tint(m.id))
                            .frame(width: 36, height: 36)
                            .background(ModeStyle.tint(m.id).opacity(0.15), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        Text(m.name).font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.text)
                        Text(m.tagline).font(.suite(.regular, 12)).foregroundStyle(Color.gg.textMuted).lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(14)
                    .background(active ? Color.gg.accent.opacity(0.1) : Color.gg.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .strokeBorder(active ? Color.gg.accent : Color.gg.border, lineWidth: 1))
                    .overlay(alignment: .topTrailing) {
                        if locked {
                            Image(systemName: "lock.fill").font(.system(size: 10, weight: .bold)).foregroundStyle(Color.gg.accent)
                                .frame(width: 20, height: 20).background(Color.gg.accent.opacity(0.12), in: Circle()).padding(10)
                        } else if m.scored && best > 0 {
                            Text("최고 \(best)").font(.suite(.extrabold, 10)).foregroundStyle(Color.gg.warning)
                                .padding(.horizontal, 8).padding(.vertical, 2)
                                .background(Color.gg.warning.opacity(0.15), in: Capsule()).padding(10)
                        }
                    }
                }
                .buttonStyle(PressScaleStyle())
            }
        }
    }

    private var tableSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            // 전체 랜덤
            Button { session.start(mode, table: nil) } label: {
                HStack(spacing: 12) {
                    Image(systemName: "shuffle").font(.system(size: 18, weight: .bold)).foregroundStyle(Color.gg.accent)
                        .frame(width: 40, height: 40)
                        .background(Color.gg.accent.opacity(0.15), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("전체 랜덤").font(.suite(.bold, 15)).foregroundStyle(Color.gg.text)
                        Text("2~9단을 골고루 섞어서").font(.suite(.regular, 13)).foregroundStyle(Color.gg.textMuted)
                    }
                    Spacer()
                }
                .padding(.horizontal, 20).padding(.vertical, 16)
                .ggCard(padding: 0)
                .padding(.vertical, 0)
            }
            .buttonStyle(PressScaleStyle())

            VStack(alignment: .leading, spacing: 2) {
                Text("단 선택").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
                    .accessibilityAddTraits(.isHeader)
                Text("추천 순서 \(Hints.roadmapTables.map(String.init).joined(separator: " → "))단 · 책 버튼을 누르면 표를 볼 수 있어요")
                    .font(.suite(.medium, 11)).foregroundStyle(Color.gg.textMuted.opacity(0.85))
                    .fixedSize(horizontal: false, vertical: true)
            }
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                ForEach(tables, id: \.self) { t in
                    tableCard(t, recommended: t == recommendedTable)
                }
            }
        }
    }

    /// 로드맵상 다음에 익힐 단 — 모두 별 3개면 nil
    private var recommendedTable: Int? { Hints.nextRoadmapTable(game.state.tableMastery) }

    private func tableCard(_ t: Int, recommended: Bool) -> some View {
        let stars = game.state.tableMastery[t]?.stars ?? 0
        return Button { session.start(mode, table: t) } label: {
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .firstTextBaseline, spacing: 2) {
                    Text("\(t)").font(.suite(.extrabold, 30)).foregroundStyle(Color.gg.text).monospacedDigit()
                    Text("단").font(.suite(.bold, 18)).foregroundStyle(Color.gg.textMuted)
                }
                HStack(spacing: 6) {
                    StarsView(count: stars, size: 16)
                    if recommended {
                        Text("추천").font(.suite(.extrabold, 10)).foregroundStyle(Color.gg.accentFg)
                            .padding(.horizontal, 7).padding(.vertical, 2)
                            .background(Color.gg.accent, in: Capsule())
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .background(recommended ? Color.gg.accent.opacity(0.08) : Color.gg.surface,
                        in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous)
                .strokeBorder(recommended ? Color.gg.accent.opacity(0.6) : Color.gg.border, lineWidth: recommended ? 1.5 : 1))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityLabel("\(t)단 \(Modes.def(mode).name) 시작, 별 \(stars)개\(recommended ? ", 추천" : "")")
        .overlay(alignment: .topTrailing) {
            Button {
                Haptics.impact(.light)
                sheetTable = TableChoice(table: t)
            } label: {
                Image(systemName: "book.pages.fill")
                    .font(.system(size: 15, weight: .bold)).foregroundStyle(Color.gg.accent)
                    .frame(width: 34, height: 34)
                    .background(Color.gg.accent.opacity(0.12), in: Circle())
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .padding(6)
            .accessibilityLabel("\(t)단 표 보기")
        }
    }

    private var challengeCard: some View {
        VStack(spacing: 16) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("내 최고 기록").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
                    Text((game.state.bestScores[mode] ?? 0) > 0 ? "\(game.state.bestScores[mode]!)점" : "—")
                        .font(.suite(.extrabold, 36)).foregroundStyle(Color.gg.text).monospacedDigit()
                }
                Spacer()
                Image(systemName: ModeStyle.icon(mode)).font(.system(size: 28, weight: .bold)).foregroundStyle(ModeStyle.tint(mode))
                    .frame(width: 56, height: 56)
                    .background(ModeStyle.tint(mode).opacity(0.15), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            GGButton(variant: .primary, size: .lg, action: { session.start(mode, table: nil) }) {
                Image(systemName: "play.fill"); Text("도전 시작")
            }
        }
        .padding(20)
        .ggCard(padding: 0)
    }
}
