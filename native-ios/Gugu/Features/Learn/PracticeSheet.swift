import SwiftUI

// 단 시트 "어떻게 연습할까요?" — 학습 탭 단 지도에서 단(또는 섞어서)을 누르면 열린다.
// 행 하나가 곧 시작 버튼이라 화면에 남는 '고른 모드' 상태가 없다(예전에는 모드 카드 테두리 색 하나로만 보였다).
// 단을 골랐으면 단을 지원하는 모드 4개, 섞어서면 6개 전부(60초·서바이벌 포함 + 최고 기록). 아래에 게임 2개(구구 몬스터는 2~9단만).
// 잠긴 행은 이 시트 위에 결제 화면을 띄우고, 구매하면 그대로 돌아와 행이 열린다.

/// 단 시트에서 고른 다음 행동 — 시트가 완전히 닫힌 뒤 학습 탭이 실행한다
/// (닫히는 도중에 전체화면을 띄우면 표시가 무시될 수 있다)
enum PracticeStart: Equatable {
    case session(GameMode, table: Int?)
    case minigame(Minigame, table: Int?)
}

struct PracticeSheet: View {
    /// 고른 단 (nil = 섞어서)
    let table: Int?
    var onStart: (PracticeStart) -> Void

    @Environment(GameStore.self) private var game
    @Environment(PremiumStore.self) private var premium
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// 안쪽 단계 '표·외우기'(TableSheet)를 보는 중
    @State private var showTable = false
    @State private var paywallOpen = false

    private var stars: Int { table.flatMap { game.state.tableMastery[$0]?.stars } ?? 0 }
    private var recommended: Bool { table != nil && table == Hints.nextRoadmapTable(game.state.tableMastery) }

    var body: some View {
        ZStack {
            if showTable, let table {
                TableSheet(table: table,
                           onPractice: { onStart(.session(.practice, table: table)) },
                           onBack: { showTable = false })
                    .transition(reduceMotion ? .opacity : .move(edge: .trailing))
            } else {
                options
                    .transition(reduceMotion ? .opacity : .move(edge: .leading))
            }
        }
        .animation(.easeInOut(duration: 0.25), value: showTable)
        .background(Color.gg.bg.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        // 루트의 결제 시트는 이 시트가 떠 있는 동안 뜨지 못한다 — 여기서 직접 띄운다
        .sheet(isPresented: $paywallOpen) { PaywallView() }
    }

    // MARK: - 방식 고르기

    private var options: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("어떻게 연습할까요?")
                            .font(.suite(.extrabold, 18)).foregroundStyle(Color.gg.text)
                            .accessibilityAddTraits(.isHeader)
                        VStack(spacing: 8) {
                            ForEach(Array(Modes.options(table: table).enumerated()), id: \.element.id) { index, m in
                                modeRow(m, primary: index == 0)
                            }
                        }
                    }
                    minigames
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 24)
            }
            .scrollIndicators(.hidden)
        }
        .readableWidth(560)
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 10) {
            if let table {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text("\(table)단").font(.suite(.extrabold, 30)).foregroundStyle(Color.gg.text).monospacedDigit()
                        if recommended {
                            Text("추천").font(.suite(.extrabold, 11)).foregroundStyle(Color.gg.accentFg)
                                .padding(.horizontal, 8).padding(.vertical, 2)
                                .background(Color.gg.accent, in: Capsule())
                        }
                    }
                    StarsView(count: stars, size: 15)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(recommended ? String(localized: "\(table)단, 별 \(stars)개, 추천") : String(localized: "\(table)단, 별 \(stars)개"))
            } else {
                HStack(spacing: 10) {
                    Image(systemName: "shuffle").font(.system(size: 18, weight: .bold)).foregroundStyle(Color.gg.accent)
                        .frame(width: 44, height: 44)
                        .background(Color.gg.accent.opacity(0.15), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("섞어서").font(.suite(.extrabold, 24)).foregroundStyle(Color.gg.text)
                        Text("2~9단을 골고루 섞어서").font(.suite(.medium, 13)).foregroundStyle(Color.gg.textMuted)
                    }
                }
                .accessibilityElement(children: .combine)
            }
            Spacer(minLength: 4)
            if table != nil {
                Button {
                    Haptics.impact(.light)
                    showTable = true
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "book.pages.fill").font(.system(size: 13, weight: .bold))
                        Text("표·외우기").font(.suite(.extrabold, 13)).lineLimit(1)
                    }
                    .foregroundStyle(Color.gg.accent)
                    .padding(.horizontal, 12)
                    .frame(height: 36)
                    .background(Color.gg.accent.opacity(0.12), in: Capsule())
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .accessibilityHint("구구단 표를 보고 소리로 외워요")
            }
            Button {
                Haptics.impact(.light)
                dismiss()
            } label: {
                Image(systemName: "xmark").font(.system(size: 15, weight: .bold)).foregroundStyle(Color.gg.textMuted)
                    .frame(width: 36, height: 36)
                    .background(Color.gg.surface2, in: Circle())
                    .frame(width: 44, height: 44)
            }
            .accessibilityLabel("닫기")
        }
        .padding(.leading, 20)
        .padding(.trailing, 12)
        .padding(.top, 24)
        .padding(.bottom, 12)
    }

    /// 모드 한 줄 — 누르면 바로 시작(잠겼으면 결제 화면). 맨 위(차근차근)는 기본이라 강조한다
    private func modeRow(_ m: ModeDef, primary: Bool) -> some View {
        let locked = PremiumConfig.isPremiumMode(m.id) && !premium.isPremium
        let best = m.scored ? (game.state.bestScores[m.id] ?? 0) : 0
        let tint = ModeStyle.tint(m.id)
        return Button {
            Haptics.impact(.light)
            if locked { paywallOpen = true } else { onStart(.session(m.id, table: table)) }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: ModeStyle.icon(m.id))
                    .font(.system(size: 19, weight: .bold)).foregroundStyle(tint)
                    .frame(width: 44, height: 44)
                    .background(tint.opacity(0.15), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(m.name).font(.suite(.extrabold, 16)).foregroundStyle(Color.gg.text)
                    Text(m.tagline)
                        .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
                        .lineLimit(2)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 4)
                if locked {
                    HStack(spacing: 4) {
                        Image(systemName: "lock.fill").font(.system(size: 10, weight: .bold))
                        Text("평생 이용권").font(.suite(.extrabold, 11))
                    }
                    .foregroundStyle(Color.gg.accent)
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(Color.gg.accent.opacity(0.12), in: Capsule())
                    .fixedSize()
                } else {
                    if best > 0 {
                        Text("최고 \(best)").font(.suite(.extrabold, 11)).foregroundStyle(Color.gg.warning)
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .background(Color.gg.warning.opacity(0.15), in: Capsule())
                            .fixedSize()
                    }
                    Image(systemName: "play.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(primary ? Color.gg.accentFg : tint)
                        .frame(width: 34, height: 34)
                        .background(primary ? Color.gg.accent : tint.opacity(0.15), in: Circle())
                }
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(primary ? Color.gg.accent.opacity(0.08) : Color.gg.surface,
                        in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(primary ? Color.gg.accent.opacity(0.6) : Color.gg.border, lineWidth: primary ? 1.5 : 1))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityLabel(rowLabel(m, best: best, locked: locked))
        .accessibilityHint(locked ? String(localized: "눌러서 평생 이용권 보기") : String(localized: "눌러서 바로 시작"))
    }

    private func rowLabel(_ m: ModeDef, best: Int, locked: Bool) -> String {
        let base = "\(m.name), \(ModeStyle.meta(m.id))"
        if locked { return String(localized: "\(base), 잠김, 평생 이용권 필요") }
        return best > 0 ? String(localized: "\(base), 최고 \(best)점") : base
    }

    // MARK: - 게임 (고른 단으로 시작)

    private var minigames: some View {
        VStack(alignment: .leading, spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text("게임").font(.suite(.extrabold, 16)).foregroundStyle(Color.gg.text)
                    .accessibilityAddTraits(.isHeader)
                Text(table.map { String(localized: "\($0)단으로 시작해요") } ?? String(localized: "섞어서 시작해요"))
                    .font(.suite(.medium, 12)).foregroundStyle(Color.gg.textMuted)
            }
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                ForEach(Minigame.allCases) { minigameTile($0) }
            }
        }
    }

    /// 게임 타일 — 고른 단을 못 쓰는 게임(구구 몬스터 10단 이상)은 회색으로 두고 "9단까지"만 알린다(누르면 아무 일 없음)
    private func minigameTile(_ g: Minigame) -> some View {
        let enabled = g.supports(table: table)
        let note = enabled ? g.badge(isPremium: premium.isPremium) : String(localized: "9단까지")
        let tint = enabled ? g.tint : Color.gg.textMuted
        return Button {
            Haptics.impact(.light)
            onStart(.minigame(g, table: table))
        } label: {
            HStack(spacing: 10) {
                GameArt.image(g.thumbnail)
                    .resizable()
                    .scaledToFit()
                    .padding(3)
                    .frame(width: 40, height: 40)
                    .background(g.iconBg, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .saturation(enabled ? 1 : 0)
                    .opacity(enabled ? 1 : 0.5)
                VStack(alignment: .leading, spacing: 3) {
                    Text(g.title).font(.suite(.extrabold, 14))
                        .foregroundStyle(enabled ? Color.gg.text : Color.gg.textMuted)
                        .lineLimit(1).minimumScaleFactor(0.75)
                    Text(note)
                        .font(.suite(.extrabold, 10)).foregroundStyle(tint)
                        .lineLimit(1).minimumScaleFactor(0.8)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(tint.opacity(0.12), in: Capsule())
                }
                Spacer(minLength: 0)
            }
            .padding(10)
            .frame(maxWidth: .infinity, minHeight: 64, alignment: .leading)
            .background(enabled ? g.tint.opacity(0.06) : Color.gg.surface2, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(tint.opacity(0.25), lineWidth: 1))
        }
        .buttonStyle(PressScaleStyle())
        .disabled(!enabled)
        .accessibilityLabel(enabled ? "\(g.title), \(note)" : String(localized: "\(g.title), 9단까지만 할 수 있어요"))
        .accessibilityHint(enabled ? (table.map { String(localized: "\($0)단으로 시작해요") } ?? String(localized: "섞어서 시작해요")) : "")
    }
}
