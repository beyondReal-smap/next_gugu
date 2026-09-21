import UIKit

// 햅틱 — @capacitor/haptics + AppShell 전역 탭 피드백 이식.
// 웹은 모든 인터랙티브 요소 탭에 IMPACT_LIGHT를 위임 처리했다.
// 네이티브에서는 GGButton 등 공통 컴포넌트가 직접 호출한다.

enum Haptics {
    enum ImpactKind { case light, medium, heavy, soft, rigid }

    static func impact(_ kind: ImpactKind = .light) {
        let style: UIImpactFeedbackGenerator.FeedbackStyle
        switch kind {
        case .light: style = .light
        case .medium: style = .medium
        case .heavy: style = .heavy
        case .soft: style = .soft
        case .rigid: style = .rigid
        }
        let gen = UIImpactFeedbackGenerator(style: style)
        gen.impactOccurred()
    }

    static func success() {
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }
    static func warning() {
        UINotificationFeedbackGenerator().notificationOccurred(.warning)
    }
    static func error() {
        UINotificationFeedbackGenerator().notificationOccurred(.error)
    }
    static func selection() {
        UISelectionFeedbackGenerator().selectionChanged()
    }
}
