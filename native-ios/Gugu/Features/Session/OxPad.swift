import SwiftUI

// OX 퀴즈 입력 패드 (OxPad.tsx 이식)

struct OxPad: View {
    var onAnswer: (Bool) -> Void

    var body: some View {
        HStack(spacing: 12) {
            // 영어권은 O/X 관습이 없어 ✓/✗ 로 보인다
            choice(isTrue: true, color: .gg.success, icon: ![AppLanguage.ko, .ja].contains(AppLanguage.current) ? "checkmark" : "circle",
                   label: String(localized: "맞아요", comment: "OX 퀴즈 버튼 — 식이 맞다"))
            choice(isTrue: false, color: .gg.danger, icon: "xmark",
                   label: String(localized: "아니에요", comment: "OX 퀴즈 버튼 — 식이 틀리다"))
        }
    }

    /// 고른 쪽 기호 — 한국어 O/X, 일본어 ○/×, 영어 ✓/✗
    static func mark(_ isTrue: Bool) -> String {
        switch AppLanguage.current {
        case .ko: return isTrue ? "O" : "X"
        case .ja: return isTrue ? "○" : "×"
        case .en, .hi, .vi, .id: return isTrue ? "✓" : "✗"
        }
    }

    private func choice(isTrue: Bool, color: Color, icon: String, label: String) -> some View {
        Button {
            Haptics.impact(.light)
            onAnswer(isTrue)
        } label: {
            VStack(spacing: 6) {
                Image(systemName: icon).font(.system(size: 44, weight: .heavy)).foregroundStyle(color)
                Text(label).font(.suite(.extrabold, 14)).foregroundStyle(color)
            }
            .frame(maxWidth: .infinity).frame(height: 128)
            .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(PressScaleStyle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(isTrue ? String(localized: "맞아요, 맞는 식") : String(localized: "아니에요, 틀린 식"))
        .accessibilityAddTraits(.isButton)
    }
}
