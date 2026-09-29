import Foundation

// 한국어 읽기 — 문제 낭독(TTS)과 구구단 외우기 문장을 만든다.
// 숫자를 그대로 음성 엔진에 넘기면 "4"를 "사"/"넷"으로 제각각 읽고 조사(은/는)도 틀리므로
// 한자어 수사와 받침 조사를 여기서 직접 만든다.

enum KoreanReading {
    private static let digits = ["", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"]

    private static let units: [(value: Int, name: String)] = [(1000, "천"), (100, "백"), (10, "십")]

    /// 한자어 수사 (0~9999) — 12 → "십이", 56 → "오십육", 100 → "백", 0 → "영".
    /// 자리 값이 1이면 "일"을 생략한다 (십, 백, 천).
    static func sino(_ n: Int) -> String {
        precondition((0..<10_000).contains(n), "읽을 수 없는 수: \(n)")
        if n == 0 { return "영" }
        var s = ""
        var rest = n
        for unit in units {
            let q = rest / unit.value
            if q > 0 { s += (q == 1 ? "" : digits[q]) + unit.name }
            rest %= unit.value
        }
        if rest > 0 { s += digits[rest] }
        return s
    }

    /// 마지막 음절에 받침이 있는지 — 한글 음절(가~힣)이 아니면 false
    static func hasBatchim(_ word: String) -> Bool {
        guard let last = word.unicodeScalars.last else { return false }
        let v = Int(last.value)
        guard (0xAC00...0xD7A3).contains(v) else { return false }
        return (v - 0xAC00) % 28 != 0
    }

    /// 방향 조사 로/으로 — 받침이 없거나 ㄹ 받침이면 "로" ("학교로", "서울로"), 그 밖은 "으로" ("들판으로")
    static func toward(_ word: String) -> String {
        guard let last = word.unicodeScalars.last else { return word }
        let v = Int(last.value)
        let rieul = (0xAC00...0xD7A3).contains(v) && (v - 0xAC00) % 28 == 8
        return word + (!hasBatchim(word) || rieul ? "로" : "으로")
    }

    /// 주제 조사 은/는을 붙인다 — "삼" → "삼은", "구" → "구는"
    static func topic(_ word: String) -> String {
        word + (hasBatchim(word) ? "은" : "는")
    }

    /// 세션 문제 낭독문
    /// - 일반: "사 곱하기 구는?"
    /// - 빈칸 추리: "삼십육은 사 곱하기 몇일까요?"
    ///   숫자 바로 뒤에 '이'로 시작하는 말을 붙이지 않는다 — "십이일까요"는 "십일까요"로, "십이 될까요"(10+이)는 12로 들린다
    /// - OX 퀴즈: "사 곱하기 구는 삼십육. 맞을까요?"
    static func question(_ p: Problem, mode: GameMode, statement: Statement?) -> String {
        let a = sino(p.a)
        let b = sino(p.b)
        switch mode {
        case .missing:
            return "\(topic(sino(p.a * p.b))) \(a) 곱하기 몇일까요?"
        case .truefalse:
            guard let statement else { return "\(a) 곱하기 \(topic(b))?" }
            return "\(a) 곱하기 \(topic(b)) \(sino(statement.shown)). 맞을까요?"
        default:
            return "\(a) 곱하기 \(topic(b))?"
        }
    }

    /// 구구단 외우기 한 줄 — "칠 팔은 오십육"
    static func chant(_ a: Int, _ b: Int) -> String {
        "\(sino(a)) \(topic(sino(b))) \(sino(a * b))"
    }

    // MARK: - 화면 문구용 (숫자는 아라비아 숫자, 조사는 한자어 읽기 기준)

    /// 주제 조사 — "1" → "1은", "2" → "2는" (화면 낭독용 라벨)
    static func withTopic(_ n: Int) -> String {
        "\(n)" + (hasBatchim(sino(n)) ? "은" : "는")
    }

    /// 주격 조사 — "36" → "36이", "35" → "35가"
    static func withSubject(_ n: Int) -> String {
        "\(n)" + (hasBatchim(sino(n)) ? "이" : "가")
    }

    /// 서술형 — "36" → "36이에요", "35" → "35예요"
    static func withCopula(_ n: Int) -> String {
        "\(n)" + (hasBatchim(sino(n)) ? "이에요" : "예요")
    }

    /// 오답 설명 — "35가 아니라 36이에요"
    static func notButIs(given: Int, answer: Int) -> String {
        "\(withSubject(given)) 아니라 \(withCopula(answer))"
    }
}
