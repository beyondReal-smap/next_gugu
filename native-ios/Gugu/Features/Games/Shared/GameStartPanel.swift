import SwiftUI

// 게임 시작 전 패널 — 이름·한 줄 설명·문제 순서 [순서대로]·[무작위]·무료 체험 안내·[시작].
// 게임 화면 위에 어두운 막과 함께 얹는다(뒤로 첫 장면이 비쳐 보임). 체험 종료 패널(TrialEndPanel)도 같은 카드에 담는다.

struct GameStartPanel: View {
    let title: String
    let summary: String
    /// SF Symbol — 이름 옆 아이콘
    let symbol: String
    let isPremium: Bool
    /// 지금 고른 문제 순서 — 누르면 onOrder 로 알리고, 엔진이 뒤의 첫 문제를 새 순서로 다시 만든다
    let order: ProblemOrder
    let onOrder: (ProblemOrder) -> Void
    let onStart: () -> Void
    let onExit: () -> Void

    var body: some View {
        GamePanelCard {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .top, spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill(Color.gg.accent.opacity(0.12))
                            .frame(width: 44, height: 44)
                        Image(systemName: symbol)
                            .font(.system(size: 20, weight: .bold))
                            .foregroundStyle(Color.gg.accent)
                    }
                    .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(title)
                            .font(.suite(.extrabold, 20)).foregroundStyle(Color.gg.text)
                        Text(summary)
                            .font(.suite(.medium, 14)).foregroundStyle(Color.gg.textMuted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                GameOrderPicker(order: order, onOrder: onOrder)
                if !isPremium { TrialNotice() }
                GGButton(variant: .primary, size: .lg, action: onStart) {
                    Image(systemName: "play.fill")
                    Text("시작")
                }
                Button(action: { Haptics.impact(.light); onExit() }) {
                    Text("나가기")
                        .font(.suite(.bold, 14)).foregroundStyle(Color.gg.textMuted)
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
            }
        }
    }
}

/// 문제 순서 두 버튼 — 고른 쪽은 채우고 다른 쪽은 테두리만. 아이 손가락으로 누르기 쉽게 높이 52
struct GameOrderPicker: View {
    let order: ProblemOrder
    let onOrder: (ProblemOrder) -> Void

    var body: some View {
        HStack(spacing: 10) {
            option(.sequential, title: "순서대로", symbol: "list.number", hint: "곱하는 수가 1부터 차례로 나와요")
            option(.random, title: "무작위", symbol: "shuffle", hint: "단과 곱하는 수가 섞여서 나와요")
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(Text("문제 순서"))
    }

    private func option(_ value: ProblemOrder, title: LocalizedStringKey, symbol: String,
                        hint: LocalizedStringKey) -> some View {
        let selected = value == order
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        return Button {
            guard !selected else { return }   // 이미 고른 쪽을 다시 누르면 아무 일 없음(뒤 문제를 새로 만들지 않음)
            Haptics.selection()
            onOrder(value)
        } label: {
            HStack(spacing: 6) {
                Image(systemName: symbol)
                    .font(.system(size: 16, weight: .bold))
                    .accessibilityHidden(true)
                Text(title)
                    .font(.suite(.extrabold, 16))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .foregroundStyle(selected ? Color.gg.accentFg : Color.gg.accent)
            .padding(.horizontal, 8)
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(selected ? Color.gg.accent : Color.gg.accent.opacity(0.06), in: shape)
            .overlay(shape.strokeBorder(Color.gg.accent, lineWidth: 2))
            .contentShape(shape)
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityHint(Text(hint))
        .accessibilityIdentifier("game.order.\(value.rawValue)")
    }
}

/// 게임 화면 위에 앱 표면 색 카드를 가운데 띄운다(어두운 막이 뒤 게임 입력을 막음)
struct GamePanelCard<Content: View>: View {
    @ViewBuilder var content: () -> Content

    var body: some View {
        ZStack {
            Color.black.opacity(0.45)
                .ignoresSafeArea()
                .contentShape(Rectangle())
                .onTapGesture {}
            content()
                .ggCard(padding: 20, radius: 24)
                .readableWidth(420)
                .padding(.horizontal, 24)
        }
    }
}
