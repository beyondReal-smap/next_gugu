import Foundation

// 앱 언어 — 화면 문구·음성 팩·폰트·서버 locale 이 모두 이 값을 따른다.
// iOS 가 기기 언어 목록과 앱 지원 언어(ko·en·ja)를 맞춰 고른 결과(Bundle.preferredLocalizations)를 쓴다.
// 맞는 언어가 없으면 Info.plist 개발 지역(en)이 골라지므로 지원하지 않는 언어(fr 등) 기기는 영어가 된다.
// iOS 설정에서 앱별 언어를 바꾸면 시스템이 앱을 다시 시작하므로 실행 중에는 값이 바뀌지 않는다.

enum AppLanguage: String, CaseIterable {
    case ko, en, ja

    static let current = resolve(Bundle.main.preferredLocalizations)

    /// 그 언어로 쓴 언어 이름 — 어느 언어 화면에서든 자기 언어를 알아볼 수 있게 번역하지 않는다
    var nativeName: String {
        switch self {
        case .ko: return "한국어"  // i18n:skip
        case .en: return "English"
        case .ja: return "日本語"
        }
    }

    /// 날짜·시각·요일 서식용 locale — 언어는 앱 언어, 지역은 기기 지역 (기기 언어가 fr 이어도 영어 서식)
    static let formatLocale: Locale = {
        var components = Locale.Components(locale: .current)
        components.languageComponents = Locale.Language.Components(identifier: current.rawValue)
        return Locale(components: components)
    }()

    /// 현지화 식별자 목록("ja", "en-GB" 등) 중 처음 나오는 지원 언어 — 하나도 없으면 en
    static func resolve(_ identifiers: [String]) -> AppLanguage {
        for id in identifiers {
            guard let code = Locale(identifier: id).language.languageCode?.identifier else { continue }
            if let language = AppLanguage(rawValue: code) { return language }
        }
        return .en
    }
}
