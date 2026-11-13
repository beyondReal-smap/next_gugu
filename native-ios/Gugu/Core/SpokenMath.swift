import Foundation

// 언어별 읽기 고르기 — 화면 낭독 라벨·오답 설명·문제 낭독문을 앱 언어에 맞는 읽기 규칙에서 가져온다.
// 언어가 셋뿐이라 프로토콜 없이 switch 하나로 고른다. 테스트는 language 를 넘겨 세 언어를 한 번에 확인한다.

enum SpokenMath {
    /// 세션 문제 낭독문 — 음성 파일 문장과 같고, 접근성 라벨로도 쓴다
    static func question(_ p: Problem, mode: GameMode, statement: Statement?,
                         language: AppLanguage = .current) -> String {
        switch language {
        case .ko: return KoreanReading.question(p, mode: mode, statement: statement)
        case .en: return EnglishReading.question(p, mode: mode, statement: statement)
        case .ja: return JapaneseReading.question(p, mode: mode, statement: statement)
        case .hi:
            if mode == .missing { return "\(p.a) को किस संख्या से गुणा करने पर \(p.a * p.b) मिलता है?" }
            if mode == .truefalse, let statement { return "\(p.a) गुणा \(p.b) बराबर \(statement.shown)। सही या गलत?" }
            return questionLabel(p.a, p.b, language: language)
        case .vi:
            if mode == .missing { return "\(p.a) nhân bao nhiêu bằng \(p.a * p.b)?" }
            if mode == .truefalse, let statement { return "\(p.a) nhân \(p.b) bằng \(statement.shown). Đúng hay sai?" }
            return questionLabel(p.a, p.b, language: language)
        case .id:
            if mode == .missing { return "\(p.a) dikali berapa hasilnya \(p.a * p.b)?" }
            if mode == .truefalse, let statement { return "\(p.a) kali \(p.b) sama dengan \(statement.shown). Benar atau salah?" }
            return questionLabel(p.a, p.b, language: language)
        }
    }

    /// 구구단 표 화면의 읽기 열 — 영어는 "7 × 8 = 56"과 같은 내용이라 숨긴다(nil)
    static func chantText(_ a: Int, _ b: Int, language: AppLanguage = .current) -> String? {
        switch language {
        case .ko: return KoreanReading.chant(a, b)
        case .en, .hi, .vi, .id: return nil
        case .ja: return JapaneseReading.chant(a, b)
        }
    }

    /// 문제 낭독 라벨 — "7 곱하기 8은?" / "What is 7 times 8?" / "7 かける 8 は？"
    static func questionLabel(_ a: Int, _ b: Int, language: AppLanguage = .current) -> String {
        switch language {
        case .ko: return "\(a) 곱하기 \(KoreanReading.withTopic(b))?"  // i18n:skip
        case .en: return EnglishReading.questionLabel(a, b)
        case .ja: return JapaneseReading.questionLabel(a, b)
        case .hi: return "\(a) गुणा \(b) कितना होता है?"
        case .vi: return "\(a) nhân \(b) bằng bao nhiêu?"
        case .id: return "Berapa \(a) kali \(b)?"
        }
    }

    /// 개수 뒤에 붙는 단위("12 개" / "12 こ") — 영어는 단위 없이 숫자만 쓴다(nil).
    /// 빈 번역("")은 iOS 가 번역 없음으로 보고 한국어 원문을 보여 주므로 화면에서 아예 그리지 않는다
    static var countSuffix: String? {
        AppLanguage.current == .en ? nil : String(localized: "개", comment: "개수 단위 — 점수 숫자 옆 작은 글씨 (영어는 쓰지 않음)")
    }

    /// 곱셈식 라벨 — "7 곱하기 8" / "7 times 8" / "7 かける 8"
    static func factLabel(_ a: Int, _ b: Int, language: AppLanguage = .current) -> String {
        switch language {
        case .ko: return "\(a) 곱하기 \(b)"  // i18n:skip
        case .en: return "\(a) times \(b)"
        case .ja: return "\(a) かける \(b)"
        case .hi: return "\(a) गुणा \(b)"
        case .vi: return "\(a) nhân \(b)"
        case .id: return "\(a) kali \(b)"
        }
    }

    /// 식 낭독 라벨 — "7 곱하기 8은 56" / "7 times 8 is 56" / "7 かける 8 は 56"
    static func equationLabel(_ a: Int, _ b: Int, language: AppLanguage = .current) -> String {
        switch language {
        case .ko: return "\(a) 곱하기 \(KoreanReading.withTopic(b)) \(a * b)"  // i18n:skip
        case .en: return EnglishReading.equationLabel(a, b)
        case .ja: return JapaneseReading.equationLabel(a, b)
        case .hi: return "\(a) गुणा \(b) बराबर \(a * b)"
        case .vi: return "\(a) nhân \(b) bằng \(a * b)"
        case .id: return "\(a) kali \(b) sama dengan \(a * b)"
        }
    }

    /// 정답 안내 — "정답은 56이에요"
    static func answerIs(_ n: Int, language: AppLanguage = .current) -> String {
        switch language {
        case .ko: return "정답은 \(KoreanReading.withCopula(n))"  // i18n:skip
        case .en: return EnglishReading.answerIs(n)
        case .ja: return JapaneseReading.answerIs(n)
        case .hi: return "उत्तर \(n) है।"
        case .vi: return "Đáp án là \(n)."
        case .id: return "Jawabannya \(n)."
        }
    }

    /// 오답 설명 — "54가 아니라 56이에요"
    static func notButIs(given: Int, answer: Int, language: AppLanguage = .current) -> String {
        switch language {
        case .ko: return KoreanReading.notButIs(given: given, answer: answer)
        case .en: return EnglishReading.notButIs(given: given, answer: answer)
        case .ja: return JapaneseReading.notButIs(given: given, answer: answer)
        case .hi: return "उत्तर \(given) नहीं, \(answer) है।"
        case .vi: return "Không phải \(given), mà là \(answer)."
        case .id: return "Bukan \(given), melainkan \(answer)."
        }
    }
}
