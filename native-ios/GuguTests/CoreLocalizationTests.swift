import XCTest
@testable import Gugu

/// Core 문구가 앱 언어를 따르는지 — 실행 언어별로 기대값이 다르다.
/// 스킴 기본(ko) 실행 외에 `xcodebuild test-without-building -only-testing:GuguTests/CoreLocalizationTests
/// -testLanguage en -testRegion US` / `-testLanguage ja -testRegion JP` 로 돌린다.
final class CoreLocalizationTests: XCTestCase {
    /// 현재 앱 언어에 해당하는 기대값
    private func expected(ko: String, en: String, ja: String) -> String {
        switch AppLanguage.current {
        case .ko: return ko
        case .en: return en
        case .ja: return ja
        }
    }

    func testModeNames() {
        XCTAssertEqual(Modes.def(.practice).name, expected(ko: "학습", en: "Practice", ja: "れんしゅう"))
        XCTAssertEqual(Modes.def(.truefalse).name, expected(ko: "OX 퀴즈", en: "True or False", ja: "○×クイズ"))
    }

    func testAchievementAndLevelText() {
        let first = Achievements.get("first_correct")
        XCTAssertEqual(first?.name, expected(ko: "첫 정답", en: "First Correct", ja: "はじめての 正かい"))
        XCTAssertEqual(Level.title(1), expected(ko: "구구단 입문자", en: "Times Table Beginner", ja: "九九ビギナー"))
    }

    func testWorldNamesAndDefaultGreeting() {
        let meadow = World.region(for: 2)
        XCTAssertEqual(meadow?.name, expected(ko: "새싹 들판", en: "Sprout Meadow", ja: "ふたばの のはら"))
        XCTAssertEqual(meadow?.npcs.first?.greeting,
                       expected(ko: "2단으로 승부하자!", en: "Let's battle with the 2 times table!", ja: "2のだんで しょうぶだ！"))
    }

    /// 힌트 제목은 번역되고, 식 부분은 언어와 무관하게 같다
    func testHintTitleTranslatesButMathStays() {
        let hint = Hints.coach(a: 9, b: 7)
        XCTAssertEqual(hint.title, expected(ko: "10단에서 한 번 빼기", en: "Times 10, take one away", ja: "10のだんから 1回分 ひく"))
        XCTAssertEqual(hint.scaffold, "70 − 7 = ?")
        XCTAssertEqual(hint.full, "9 × 7 = 70 − 7 = 63")
        XCTAssertEqual(Hints.coach(a: 4, b: 3).title, expected(ko: "2단을 두 배로", en: "Double the 2s", ja: "2のだんを 2ばいに"))
    }

    func testWeekdayAndHourLabels() {
        // 2026-09-29 는 화요일. 영어 시각은 숫자와 AM/PM 사이에 좁은 줄바꿈 없는 공백(U+202F)을 쓴다
        XCTAssertEqual(WeeklyReport.weekdayLabel("2026-09-29"), expected(ko: "화", en: "T", ja: "火"))
        XCTAssertEqual(ReminderPlanner.hourLabel(18), expected(ko: "오후 6시", en: "6\u{202F}PM", ja: "18時"))
        XCTAssertEqual(ReminderPlanner.hourLabel(12), expected(ko: "낮 12시", en: "12\u{202F}PM", ja: "12時"))
    }

    func testPushDates() {
        let date = ISO8601DateFormatter().date(from: "2026-10-22T03:00:00Z")!
        let seoul = TimeZone(identifier: "Asia/Seoul")!
        XCTAssertEqual(PushDateText.long(date, timeZone: seoul),
                       expected(ko: "2026년 10월 22일", en: "October 22, 2026", ja: "2026年10月22日"))
    }

    /// 영어만 복수형이 갈린다 — 1개/여러 개
    func testEnglishPluralForms() {
        let one = String(localized: "오늘 목표까지 정답 \(1)개 남았어요.")
        let many = String(localized: "오늘 목표까지 정답 \(3)개 남았어요.")
        XCTAssertEqual(one, expected(ko: "오늘 목표까지 정답 1개 남았어요.",
                                     en: "Just 1 more correct answer to reach today's goal.",
                                     ja: "きょうの もくひょうまで あと 1もん！"))
        XCTAssertEqual(many, expected(ko: "오늘 목표까지 정답 3개 남았어요.",
                                      en: "Just 3 more correct answers to reach today's goal.",
                                      ja: "きょうの もくひょうまで あと 3もん！"))
    }

    func testReorderedArgumentsKeepMeaning() {
        let tip = String(localized: "\(7)씩 \(3)번 더해 봐요")
        XCTAssertEqual(tip, expected(ko: "7씩 3번 더해 봐요", en: "Try adding 7, 3 times", ja: "7を 3回 たしてみよう"))
    }
}
