import SwiftUI

// 숫자 키패드 (Keypad.tsx 이식)

struct Keypad: View {
    var onInput: (Int) -> Void
    var onDelete: () -> Void
    var onSubmit: () -> Void
    var canSubmit: Bool

    private let cols = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]

    var body: some View {
        LazyVGrid(columns: cols, spacing: 10) {
            ForEach(1...9, id: \.self) { n in
                key { onInput(n) } content: {
                    Text("\(n)").font(.suite(.bold, 24)).foregroundStyle(Color.gg.text).monospacedDigit()
                }
            }
            key(action: onDelete) {
                Image(systemName: "delete.left").font(.system(size: 22)).foregroundStyle(Color.gg.textMuted)
            }
            .accessibilityLabel("지우기")
            key { onInput(0) } content: {
                Text("0").font(.suite(.bold, 24)).foregroundStyle(Color.gg.text).monospacedDigit()
            }
            Button {
                Haptics.impact(.medium)
                onSubmit()
            } label: {
                Text("확인").font(.suite(.bold, 18)).foregroundStyle(Color.gg.accentFg)
                    .frame(maxWidth: .infinity).frame(height: 64)
                    .background(Color.gg.accent, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(KeyStyle())
            .opacity(canSubmit ? 1 : 0.4)
            .allowsHitTesting(canSubmit)
            .accessibilityHint(canSubmit ? "" : "숫자를 먼저 눌러 주세요")
        }
    }

    @ViewBuilder
    private func key<C: View>(action: @escaping () -> Void, @ViewBuilder content: () -> C) -> some View {
        Button {
            // 키 하나하나 손끝으로 눌림을 느끼게 — 가벼운 탭 진동
            Haptics.impact(.light)
            action()
        } label: {
            content()
                .frame(maxWidth: .infinity).frame(height: 64)
                .background(Color.gg.surface2, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(KeyStyle())
    }
}

private struct KeyStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.94 : 1)
            .animation(.spring(response: 0.2, dampingFraction: 0.6), value: configuration.isPressed)
    }
}
