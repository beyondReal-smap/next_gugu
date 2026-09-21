import SwiftUI
import Observation

// 테마 (ThemeProvider.tsx 이식) — 기본 dark, 수동 토글, UserDefaults 저장.
// 웹과 동일하게 시스템 설정이 아닌 명시적 라이트/다크 선택.

@Observable
final class ThemeStore {
    var theme: Theme {
        didSet { Persistence.setString(theme.rawValue, key: Persistence.themeKey) }
    }

    init() {
        if let saved = Persistence.string(Persistence.themeKey), let t = Theme(rawValue: saved) {
            theme = t
        } else {
            theme = .dark   // 웹 기본값과 동일
        }
    }

    func toggle() {
        theme = (theme == .dark) ? .light : .dark
    }

    var colorScheme: ColorScheme { theme == .dark ? .dark : .light }
}
