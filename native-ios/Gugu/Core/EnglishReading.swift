import Foundation

// 영어 읽기 — 문제 낭독(음성 팩)과 화면 낭독 라벨을 만든다.
// 영어권에는 한국어 "칠 팔 오십육" 같은 전통 낭송이 없어 "Seven times eight is fifty-six." 문장형으로 읽는다.
// 음성 파일 문장과 글자 단위로 같아야 하므로(tools/voice/phrases.py) 규칙을 바꾸면 음성도 다시 만든다.

enum EnglishReading {
    private static let ones = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
                               "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
                               "seventeen", "eighteen", "nineteen"]
    private static let tens = ["", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"]

    /// 기수 (0~99) — 56 → "fifty-six", 40 → "forty", 13 → "thirteen"
    static func cardinal(_ n: Int) -> String {
        precondition((0..<100).contains(n), "읽을 수 없는 수: \(n)")
        if n < 20 { return ones[n] }
        let unit = n % 10
        return tens[n / 10] + (unit == 0 ? "" : "-" + ones[unit])
    }

    /// 문장 첫 글자만 대문자로
    private static func sentence(_ text: String) -> String {
        text.prefix(1).uppercased() + text.dropFirst()
    }

    /// 세션 문제 낭독문
    /// - 일반: "What is seven times eight?"
    /// - 빈칸 추리: "Fifty-six is seven times what?"
    /// - OX(True or False): "Seven times eight is fifty-four. True or false?"
    static func question(_ p: Problem, mode: GameMode, statement: Statement?) -> String {
        let a = cardinal(p.a)
        let b = cardinal(p.b)
        switch mode {
        case .missing:
            return sentence("\(cardinal(p.a * p.b)) is \(a) times what?")
        case .truefalse:
            guard let statement else { return "What is \(a) times \(b)?" }
            return sentence("\(a) times \(b) is \(cardinal(statement.shown)). True or false?")
        default:
            return "What is \(a) times \(b)?"
        }
    }

    /// 구구단 외우기 한 줄 (음성) — "Seven times eight is fifty-six."
    static func chant(_ a: Int, _ b: Int) -> String {
        sentence("\(cardinal(a)) times \(cardinal(b)) is \(cardinal(a * b)).")
    }

    // MARK: - 화면 문구용 (숫자는 아라비아 숫자)

    /// "What is 7 times 8?"
    static func questionLabel(_ a: Int, _ b: Int) -> String { "What is \(a) times \(b)?" }

    /// "7 times 8 is 56"
    static func equationLabel(_ a: Int, _ b: Int) -> String { "\(a) times \(b) is \(a * b)" }

    /// 정답 안내 — "The answer is 56."
    static func answerIs(_ n: Int) -> String { "The answer is \(n)." }

    /// 오답 설명 — "Not 54. It's 56."
    static func notButIs(given: Int, answer: Int) -> String { "Not \(given). It's \(answer)." }
}
