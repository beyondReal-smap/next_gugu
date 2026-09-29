import SwiftUI
import Observation

// 테마 (ThemeProvider.tsx 이식) — 시스템/라이트/다크 중 선택, UserDefaults 저장.
// 저장값이 없는 첫 실행은 웹과 같은 다크로 시작한다.

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

    /// nil 이면 기기 설정을 따른다
    var colorScheme: ColorScheme? {
        switch theme {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }

    static func label(_ theme: Theme) -> String {
        switch theme {
        case .system: return "기기 설정"
        case .light: return "라이트"
        case .dark: return "다크"
        }
    }
}
