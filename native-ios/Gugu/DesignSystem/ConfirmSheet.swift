import SwiftUI

// 확인 시트 (웹 components/ui/ConfirmSheet.tsx 대응) — 하단 카드 + 딤 배경.
// 아이가 실수로 닫기를 눌러도 쉽게 되돌아가도록 "계속하기"를 큰 기본 버튼으로 둔다.

struct ConfirmSheet: View {
    var icon: String = "pause.fill"
    var tint: Color = .gg.accent
    let title: String
    let message: String
    let cancelLabel: String
    let confirmLabel: String
    var onCancel: () -> Void
    var onConfirm: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var shown = false

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.black.opacity(shown ? 0.45 : 0)
                .ignoresSafeArea()
                .onTapGesture(perform: onCancel)
                .accessibilityHidden(true)

            VStack(spacing: 18) {
                Image(systemName: icon)
                    .font(.system(size: 26, weight: .bold))
                    .foregroundStyle(tint)
                    .frame(width: 56, height: 56)
                    .background(tint.opacity(0.15), in: Circle())
                    .accessibilityHidden(true)
                VStack(spacing: 6) {
                    Text(title)
                        .font(.suite(.extrabold, 20)).foregroundStyle(Color.gg.text)
                    Text(message)
                        .font(.suite(.medium, 14)).foregroundStyle(Color.gg.textMuted)
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                VStack(spacing: 8) {
                    GGButton(variant: .primary, size: .lg, action: onCancel) { Text(cancelLabel) }
                    GGButton(variant: .ghost, size: .md, action: onConfirm) {
                        Text(confirmLabel).foregroundStyle(Color.gg.danger)
                    }
                }
            }
            .padding(24)
            .frame(maxWidth: 480)
            .background(Color.gg.surface, in: RoundedRectangle(cornerRadius: 28, style: .continuous))
            .padding(.horizontal, 12)
            .padding(.bottom, 8)
            .offset(y: shown || reduceMotion ? 0 : 420)
        }
        .accessibilityAddTraits(.isModal)
        .onAppear {
            Haptics.warning()
            withAnimation(.spring(response: 0.35, dampingFraction: 0.86)) { shown = true }
        }
    }
}
