import Foundation

// 초 표시 — "1.5초" / "1.5s" / "1.5秒". 소수점 기호도 앱 언어 서식을 따른다.

enum Seconds {
    static func text(_ seconds: Double) -> String {
        let value = seconds.formatted(.number.precision(.fractionLength(1)).locale(AppLanguage.formatLocale))
        return String(localized: "\(value)초", comment: "소수 한 자리 초 — 남은 시간·평균 속도")
    }
}
