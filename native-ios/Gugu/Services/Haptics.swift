import UIKit

// 햅틱 — @capacitor/haptics + AppShell 전역 탭 피드백 이식.
// 웹은 모든 인터랙티브 요소 탭에 IMPACT_LIGHT를 위임 처리했다.
// 네이티브에서는 GGButton 등 공통 컴포넌트가 직접 호출한다.
// 설정의 「진동」을 끄면 모든 호출이 조용히 무시된다 (PrefsStore 가 enabled 를 바꾼다).

enum Haptics {
    enum ImpactKind { case light, medium, heavy, soft, rigid }

    static let enabledKey = "gugu.haptics"

    /// 진동 사용 여부 — 저장값이 없으면 켜짐
    static var enabled: Bool = (Persistence.string(enabledKey) ?? "1") == "1"

    static func impact(_ kind: ImpactKind = .light) {
        guard enabled else { return }
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
        guard enabled else { return }
        UINotificationFeedbackGenerator().notificationOccurred(.success)
    }
    static func warning() {
        guard enabled else { return }
        UINotificationFeedbackGenerator().notificationOccurred(.warning)
    }
    static func error() {
        guard enabled else { return }
        UINotificationFeedbackGenerator().notificationOccurred(.error)
    }
    static func selection() {
        guard enabled else { return }
        UISelectionFeedbackGenerator().selectionChanged()
    }
}
