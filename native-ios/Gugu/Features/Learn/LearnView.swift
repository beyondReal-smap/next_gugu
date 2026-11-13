import SwiftUI

// 학습 탭 — '단 먼저' (2.7.0 A안).
// 아이는 "7단 할래"처럼 단을 먼저 떠올린다. 맨 위 오늘 추천 카드로 한 번에 시작하고,
// 단 지도(2~9단 + 섞어서)에서 단을 누르면 "어떻게 연습할까요?" 시트에서 방식을 고른다.
// 예전 구성(모드 먼저 고르기)은 고른 모드가 카드 테두리 색으로만 보여, 단 카드가 무슨 모드로 시작할지 알기 어려웠다.

struct LearnView: View {
    @Environment(GameStore.self) private var game
    @Environment(SessionStore.self) private var session
    @Environment(Router.self) private var router

    /// 단 시트로 연 단 (table nil = 섞어서)
    @State private var sheet: TableChoice?
    /// 시트에서 고른 시작 — 시트가 완전히 닫힌 뒤 띄운다
    /// (닫히는 도중에 전체화면을 띄우면 표시가 무시될 수 있다)
    @State private var pendingStart: PracticeStart?

    private struct TableChoice: Identifiable {
        let table: Int?
        var id: Int { table ?? 0 }
    }

    private let tables = Array(Problems.minTable...Problems.maxTable)
    private var totalStars: Int { Achievements.totalStars(game.state) }
    /// 로드맵상 다음에 익힐 단 — 모두 별 3개면 nil
    private var recommendedTable: Int? { Hints.nextRoadmapTable(game.state.tableMastery) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                header
                RecommendCard()
                tableMap
                    .padding(.top, 8)
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
            .padding(.bottom, 24)
            .readableWidth()
        }
        .background(Color.gg.bg.ignoresSafeArea())
        .scrollIndicators(.hidden)
        .statusBarBackdrop()
        .sheet(item: $sheet, onDismiss: runPendingStart) { choice in
            PracticeSheet(table: choice.table) { start in
                pendingStart = start
                sheet = nil
            }
        }
    }

    private func runPendingStart() {
        guard let start = pendingStart else { return }
        pendingStart = nil
        switch start {
        case .session(let mode, let table):
            session.start(mode, table: table)
        case .minigame(let minigame, let table):
            router.open(minigame, start: .table(table))
        }
    }

    private var header: some View {
        HStack {
            Text("학습").font(.suite(.extrabold, 24)).foregroundStyle(Color.gg.text)
            Spacer()
            Pill(bg: Color.gg.warning.opacity(0.12), fg: .gg.warning) {
                Image(systemName: "trophy.fill")
                Text("\(totalStars)/\(tables.count * 3)").monospacedDigit()
            }
        }
    }

    // MARK: - 단 지도 (3×3: 2~9단 + 섞어서)

    private var tableMap: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("단 고르기").font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
                    .accessibilityAddTraits(.isHeader)
                Text("단을 누르면 연습 방법을 고를 수 있어요")
                    .font(.suite(.medium, 11)).foregroundStyle(Color.gg.textMuted.opacity(0.85))
                    .fixedSize(horizontal: false, vertical: true)
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                ForEach(tables, id: \.self) { t in
                    tableCard(t, recommended: t == recommendedTable)
                }
                mixedCard
            }
        }
    }

    private func tableCard(_ t: Int, recommended: Bool) -> some View {
        let stars = game.state.tableMastery[t]?.stars ?? 0
        return Button {
            Haptics.impact(.light)
            sheet = TableChoice(table: t)
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                Text("\(t)단")
                    .font(.suite(.extrabold, 24)).foregroundStyle(Color.gg.text).monospacedDigit()
                    .lineLimit(1).minimumScaleFactor(0.6)
                StarsView(count: stars, size: 13)
            }
            .frame(maxWidth: .infinity, minHeight: 64, alignment: .leading)
            .padding(12)
            .background(recommended ? Color.gg.accent.opacity(0.08) : Color.gg.surface,
                        in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(recommended ? Color.gg.accent.opacity(0.6) : Color.gg.border, lineWidth: recommended ? 1.5 : 1))
            // 추천 배지는 카드 테두리 위에 걸친다 — 카드 안에 두면 en "Table 2"·ja "おすすめ"처럼 긴 글자가 단 이름·별과 겹쳤다
            .overlay(alignment: .topTrailing) {
                if recommended {
                    Text("추천").font(.suite(.extrabold, 10)).foregroundStyle(Color.gg.accentFg)
                        .lineLimit(1).fixedSize()
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Color.gg.accent, in: Capsule())
                        .offset(x: 2, y: -8)
                }
            }
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityLabel(recommended ? String(localized: "\(t)단, 별 \(stars)개, 추천") : String(localized: "\(t)단, 별 \(stars)개"))
        .accessibilityHint("눌러서 연습 방법 고르기")
    }

    /// 섞어서 — 2~9단 전체. 60초 챌린지·서바이벌은 이 시트에만 있다
    private var mixedCard: some View {
        Button {
            Haptics.impact(.light)
            sheet = TableChoice(table: nil)
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                Image(systemName: "shuffle").font(.system(size: 20, weight: .bold)).foregroundStyle(Color.gg.accent)
                    .frame(height: 30)
                Text("섞어서").font(.suite(.extrabold, 15)).foregroundStyle(Color.gg.text)
                    .lineLimit(1).minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity, minHeight: 64, alignment: .leading)
            .padding(12)
            .background(Color.gg.accent.opacity(0.05), in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(Color.gg.accent.opacity(0.3), style: StrokeStyle(lineWidth: 1, dash: [5, 4])))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityLabel(String(localized: "섞어서, 2~9단 골고루"))
        .accessibilityHint("눌러서 연습 방법 고르기")
    }
}
