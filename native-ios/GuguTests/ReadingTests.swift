import XCTest
@testable import Gugu

/// 영어·일본어 읽기 규칙 — 음성 대본(552줄)과 화면 낭독 라벨의 기준이다
final class ReadingTests: XCTestCase {
    func testEnglishCardinals() {
        XCTAssertEqual(EnglishReading.cardinal(0), "zero")
        XCTAssertEqual(EnglishReading.cardinal(13), "thirteen")
        XCTAssertEqual(EnglishReading.cardinal(40), "forty")
        XCTAssertEqual(EnglishReading.cardinal(56), "fifty-six")
        XCTAssertEqual(EnglishReading.cardinal(90), "ninety")
    }

    func testEnglishQuestions() {
        let p = Problem(a: 7, b: 8)
        XCTAssertEqual(EnglishReading.question(p, mode: .practice, statement: nil), "What is seven times eight?")
        XCTAssertEqual(EnglishReading.question(p, mode: .missing, statement: nil), "Fifty-six is seven times what?")
        XCTAssertEqual(EnglishReading.question(p, mode: .truefalse, statement: Statement(shown: 54, isTrue: false)),
                       "Seven times eight is fifty-four. True or false?")
        XCTAssertEqual(EnglishReading.chant(8, 1), "Eight times one is eight.")
    }

    func testJapaneseNumbers() {
        XCTAssertEqual(JapaneseReading.number(0), "ぜろ")
        XCTAssertEqual(JapaneseReading.number(4), "よん")
        XCTAssertEqual(JapaneseReading.number(10), "じゅう")
        XCTAssertEqual(JapaneseReading.number(14), "じゅうよん")
        XCTAssertEqual(JapaneseReading.number(56), "ごじゅうろく")
        XCTAssertEqual(JapaneseReading.number(79), "ななじゅうきゅう")
        XCTAssertEqual(JapaneseReading.number(90), "きゅうじゅう")
    }

    func testJapaneseQuestions() {
        let p = Problem(a: 7, b: 8)
        XCTAssertEqual(JapaneseReading.question(p, mode: .practice, statement: nil), "なな かける はち は？")
        XCTAssertEqual(JapaneseReading.question(p, mode: .missing, statement: nil), "ごじゅうろく は なな かける なに？")
        XCTAssertEqual(JapaneseReading.question(p, mode: .truefalse, statement: Statement(shown: 54, isTrue: false)),
                       "なな かける はち は、ごじゅうよん。まるかな？ ばつかな？")
    }

    /// 九九 표 — 대표 줄과 "곱이 한 자리면 が" 규칙을 확인한다 (전체 72줄은 원어민 검수)
    func testKukuTable() {
        XCTAssertEqual(JapaneseReading.chant(2, 2), "ににんが し")
        XCTAssertEqual(JapaneseReading.chant(3, 3), "さざんが く")
        XCTAssertEqual(JapaneseReading.chant(7, 8), "しちは ごじゅうろく")
        XCTAssertEqual(JapaneseReading.chant(8, 8), "はっぱ ろくじゅうし")
        XCTAssertEqual(JapaneseReading.chant(9, 9), "くく はちじゅういち")
        var lines = Set<String>()
        for a in Problems.minTable...Problems.maxTable {
            for b in Problems.minB...Problems.maxB {
                let line = JapaneseReading.chant(a, b)
                lines.insert(line)
                XCTAssertEqual(line.contains("が "), a * b < 10, "\(a) × \(b): \(line)")
            }
        }
        XCTAssertEqual(lines.count, 72)
    }

    func testSpokenMathPicksLanguage() {
        XCTAssertEqual(SpokenMath.questionLabel(7, 8, language: .ko), "7 곱하기 8은?")
        XCTAssertEqual(SpokenMath.questionLabel(7, 8, language: .en), "What is 7 times 8?")
        XCTAssertEqual(SpokenMath.questionLabel(7, 8, language: .ja), "7 かける 8 は？")
        XCTAssertEqual(SpokenMath.equationLabel(7, 8, language: .ko), "7 곱하기 8은 56")
        XCTAssertEqual(SpokenMath.notButIs(given: 54, answer: 56, language: .ko), "54가 아니라 56이에요")
        XCTAssertEqual(SpokenMath.notButIs(given: 54, answer: 56, language: .en), "Not 54. It's 56.")
        XCTAssertEqual(SpokenMath.answerIs(36, language: .ko), "정답은 36이에요")
        XCTAssertNil(SpokenMath.chantText(7, 8, language: .en))
        XCTAssertEqual(SpokenMath.chantText(7, 8, language: .ja), "しちは ごじゅうろく")
        XCTAssertEqual(VoiceLines.chant(7, 8, language: .ja), VoiceLine(key: "c-7x8", text: "しちは ごじゅうろく。"))
    }
}
