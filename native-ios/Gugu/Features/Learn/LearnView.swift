import SwiftUI

// 학습 탭 (Learn.tsx 이식) — 모드 선택 + 단 맵/마스터리

struct LearnView: View {
    @Environment(GameStore.self) private var game
    @Environment(SessionStore.self) private var session
    @Environment(PremiumStore.self) private var premium
    @Environment(Router.self) private var router

    @State private var mode: GameMode = .practice

    private let tables = Array(Problems.minTable...Problems.maxTable)
    private var def: ModeDef { Modes.def(mode) }
    private var totalStars: Int { Achievements.totalStars(game.state) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header
                modeCards
                Text(def.detail).font(.suite(.medium, 14)).foregroundStyle(Color.gg.textMuted)
                gameLinks
                if def.supportsTable { tableSection } else { challengeCard }
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
        .background(Color.gg.bg.ignoresSafeArea())
        .scrollIndicators(.hidden)
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
        VStack(spacing: 8) {
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
                    Text(title).font(.suite(.extrabold, 14)).foregroundStyle(Color.gg.text)
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

            Text("단 선택").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                ForEach(tables, id: \.self) { t in
                    let stars = game.state.tableMastery[t]?.stars ?? 0
                    Button { session.start(mode, table: t) } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(alignment: .firstTextBaseline, spacing: 2) {
                                Text("\(t)").font(.suite(.extrabold, 30)).foregroundStyle(Color.gg.text).monospacedDigit()
                                Text("단").font(.suite(.bold, 18)).foregroundStyle(Color.gg.textMuted)
                            }
                            StarsView(count: stars, size: 16)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                        .ggCard(padding: 0)
                    }
                    .buttonStyle(PressScaleStyle())
                }
            }
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
