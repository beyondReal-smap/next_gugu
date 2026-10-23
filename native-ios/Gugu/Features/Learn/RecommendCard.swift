import SwiftUI

// 오늘 추천 단 카드 — 로드맵(2·5·3·4·6·9·7·8단)에서 다음에 익힐 단을 '차근차근'으로 한 번에 시작한다.
// 학습 탭 맨 위와 홈 "어떻게 놀아볼까요?"에 같은 카드를 둔다. 모든 단이 별 3개면 '섞어서'로 바뀐다.

struct RecommendCard: View {
    @Environment(GameStore.self) private var game
    @Environment(SessionStore.self) private var session

    /// 다음에 익힐 단 — 모두 별 3개면 nil(섞어서)
    private var table: Int? { Hints.nextRoadmapTable(game.state.tableMastery) }
    private var stars: Int { table.flatMap { game.state.tableMastery[$0]?.stars } ?? 0 }
    private var practice: ModeDef { Modes.def(.practice) }

    var body: some View {
        Button {
            Haptics.impact(.light)
            session.start(.practice, table: table)
        } label: {
            HStack(spacing: 14) {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 4) {
                        Image(systemName: "sparkles").font(.system(size: 10, weight: .bold))
                        Text("오늘 추천").font(.suite(.extrabold, 11))
                    }
                    .foregroundStyle(Color.gg.accentFg)
                    .padding(.horizontal, 9).padding(.vertical, 3)
                    .background(Color.gg.accent, in: Capsule())

                    HStack(alignment: .center, spacing: 8) {
                        Text(title)
                            .font(.suite(.extrabold, 26)).foregroundStyle(Color.gg.text).monospacedDigit()
                            .lineLimit(1).minimumScaleFactor(0.7)
                        if table != nil { StarsView(count: stars, size: 15) }
                    }
                    Text(subtitle)
                        .font(.suite(.bold, 13)).foregroundStyle(Color.gg.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 4)
                Image(systemName: "play.fill")
                    .font(.system(size: 20, weight: .bold)).foregroundStyle(Color.gg.accentFg)
                    .frame(width: 52, height: 52)
                    .background(Color.gg.accent, in: Circle())
                    .accessibilityHidden(true)
            }
            .padding(18)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.gg.accent.opacity(0.08), in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous)
                .strokeBorder(Color.gg.accent.opacity(0.45), lineWidth: 1.5))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityLabel(String(localized: "오늘 추천 \(title), \(subtitle)"))
        .accessibilityHint("눌러서 바로 시작")
    }

    private var title: String {
        table.map { String(localized: "\($0)단") } ?? String(localized: "섞어서")
    }

    /// "차근차근 · 10문제" — 모두 마스터했으면 섞어서 도전 안내
    private var subtitle: String {
        let rule = "\(practice.name) · \(ModeStyle.meta(.practice))"
        return table == nil ? String(localized: "모든 단 별 3개! \(rule)") : rule
    }
}
