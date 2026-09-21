import SwiftUI

// OX 퀴즈 입력 패드 (OxPad.tsx 이식)

struct OxPad: View {
    var onAnswer: (Bool) -> Void

    var body: some View {
        HStack(spacing: 12) {
            choice(isTrue: true, color: .gg.success, icon: "circle", label: "맞아요")
            choice(isTrue: false, color: .gg.danger, icon: "xmark", label: "아니에요")
        }
    }

    private func choice(isTrue: Bool, color: Color, icon: String, label: String) -> some View {
        Button { onAnswer(isTrue) } label: {
            VStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 44, weight: .heavy)).foregroundStyle(color)
                Text(label).font(.suite(.extrabold, 14)).foregroundStyle(color)
            }
            .frame(maxWidth: .infinity).frame(height: 128)
            .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(PressScaleStyle())
    }
}
