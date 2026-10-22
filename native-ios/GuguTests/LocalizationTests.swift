import XCTest
import UIKit
@testable import Gugu

/// 다국어 기반 — 앱 언어 결정과 언어별 폰트.
/// 스킴 기본 실행은 한국어(language: ko)이고, 다른 언어는 `xcodebuild test -testLanguage fr|en|ja` 로 돌려
/// 실제 번들 언어 선택(개발 지역 en 폴백 포함)을 확인한다.
final class LocalizationTests: XCTestCase {
    func testResolvePicksFirstSupportedLanguage() {
        XCTAssertEqual(AppLanguage.resolve(["ko"]), .ko)
        XCTAssertEqual(AppLanguage.resolve(["ja-JP"]), .ja)
        XCTAssertEqual(AppLanguage.resolve(["en-GB"]), .en)
        XCTAssertEqual(AppLanguage.resolve(["fr", "ja"]), .ja)
    }

    func testResolveFallsBackToEnglish() {
        XCTAssertEqual(AppLanguage.resolve(["fr", "de"]), .en)
        XCTAssertEqual(AppLanguage.resolve([]), .en)
    }

    /// 실행 언어 목록의 첫 지원 언어(없으면 en)와 앱 언어가 같아야 한다 — fr 로 실행하면 en 이 나와야 한다
    func testCurrentFollowsLaunchLanguages() {
        let expected = Locale.preferredLanguages.lazy
            .compactMap { Locale(identifier: $0).language.languageCode?.identifier }
            .compactMap(AppLanguage.init(rawValue:))
            .first ?? .en
        print("[i18n] 실행 언어 \(Locale.preferredLanguages) → 번들 \(Bundle.main.preferredLocalizations) → 앱 언어 \(AppLanguage.current)")
        XCTAssertEqual(AppLanguage.current, expected, "실행 언어 \(Locale.preferredLanguages)")
    }

    func testBundleDeclaresAllLanguages() {
        XCTAssertEqual(Set(Bundle.main.localizations).intersection(["ko", "en", "ja"]), ["ko", "en", "ja"])
        XCTAssertEqual(Bundle.main.developmentLocalization, "en")
    }

    /// 한국어 표가 번들에 있어야 한다 — 없으면 iOS 가 개발 지역(en) 표로 넘어가 한국어 기기에 영어가 보인다
    /// (카탈로그 원문 항목이 translated 가 아니면 생기는 문제, tools/i18n/catalog.py sync 가 채운다)
    func testEveryLanguageHasCompiledStringTable() {
        for language in AppLanguage.allCases {
            XCTAssertNotNil(Bundle.main.path(forResource: "Localizable", ofType: "strings", inDirectory: nil,
                                             forLocalization: language.rawValue), language.rawValue)
        }
    }

    /// 법적 문서는 언어별 주소 — 한국어는 기존 주소(심사·Android 와 같음)를 바꾸지 않는다
    func testLegalLinksPerLanguage() {
        XCTAssertEqual(PremiumConfig.Legal.url("privacy", language: .ko), "https://gugu.smap.site/privacy")
        XCTAssertEqual(PremiumConfig.Legal.url("privacy", language: .en), "https://gugu.smap.site/en/privacy")
        XCTAssertEqual(PremiumConfig.Legal.url("terms", language: .ja), "https://gugu.smap.site/ja/terms")
    }

    func testJapaneseFontsExistOnDevice() {
        for weight in [SUITE.light, .regular, .medium, .semibold, .bold, .extrabold, .heavy] {
            XCTAssertNotNil(UIFont(name: weight.japaneseFontName, size: 17), weight.japaneseFontName)
            XCTAssertNotNil(UIFont(name: weight.rawValue, size: 17), weight.rawValue)
        }
    }

    func testFontNameFollowsAppLanguage() {
        let expected = AppLanguage.current == .ja ? "RoundedMplus1c-Bold" : "SUITE-Bold"
        XCTAssertEqual(SUITE.bold.fontName, expected)
    }
}
