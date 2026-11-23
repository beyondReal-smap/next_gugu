import Foundation

// 일본어 읽기 — 문제 낭독(음성 팩)·九九 낭송·화면 낭독 라벨을 만든다.
// 九九 낭송은 "しちは ごじゅうろく"처럼 수 읽기가 바뀌고(しち/なな, はっぱ, ごっく) 곱이 한 자리면 "が"가 붙는 등
// 규칙으로 만들기 어려운 관용 읽기라 72줄 표로 둔다 — 원어민 검수 대상.
// 문제 낭독은 현대 표준 수 읽기(よん·なな·きゅう)를 쓴다. 아이가 TTS 오독(七=しち/なな) 없이 듣도록
// 음성 대본은 히라가나만 쓴다 (tools/voice/phrases.py 와 글자 단위로 같아야 한다).

enum JapaneseReading {
    private static let digits = ["", "いち", "に", "さん", "よん", "ご", "ろく", "なな", "はち", "きゅう"]

    /// 수 읽기 (0~999, 히라가나) — 56 → "ごじゅうろく", 40 → "よんじゅう", 0 → "ぜろ"
    static func number(_ n: Int) -> String {
        precondition((0..<1000).contains(n), "읽을 수 없는 수: \(n)")
        if n == 0 { return "ぜろ" }
        if n >= 100 {
            let hundreds = ["", "ひゃく", "にひゃく", "さんびゃく", "よんひゃく", "ごひゃく", "ろっぴゃく", "ななひゃく", "はっぴゃく", "きゅうひゃく"]
            return hundreds[n / 100] + (n % 100 == 0 ? "" : number(n % 100))
        }
        let ten = n / 10
        let unit = n % 10
        let head = ten == 0 ? "" : (ten == 1 ? "" : digits[ten]) + "じゅう"
        return head + digits[unit]
    }

    /// 九九 낭송 표 — kuku[a][b] (a 2~9, b 1~9)
    private static let kuku: [Int: [String]] = [
        2: ["にいちが に", "ににんが し", "にさんが ろく", "にしが はち", "にご じゅう",
            "にろく じゅうに", "にしち じゅうし", "にはち じゅうろく", "にく じゅうはち"],
        3: ["さんいちが さん", "さんにが ろく", "さざんが く", "さんし じゅうに", "さんご じゅうご",
            "さぶろく じゅうはち", "さんしち にじゅういち", "さんぱ にじゅうし", "さんく にじゅうしち"],
        4: ["しいちが し", "しにが はち", "しさん じゅうに", "しし じゅうろく", "しご にじゅう",
            "しろく にじゅうし", "ししち にじゅうはち", "しは さんじゅうに", "しく さんじゅうろく"],
        5: ["ごいちが ご", "ごに じゅう", "ごさん じゅうご", "ごし にじゅう", "ごご にじゅうご",
            "ごろく さんじゅう", "ごしち さんじゅうご", "ごは しじゅう", "ごっく しじゅうご"],
        6: ["ろくいちが ろく", "ろくに じゅうに", "ろくさん じゅうはち", "ろくし にじゅうし", "ろくご さんじゅう",
            "ろくろく さんじゅうろく", "ろくしち しじゅうに", "ろくは しじゅうはち", "ろっく ごじゅうし"],
        7: ["しちいちが しち", "しちに じゅうし", "しちさん にじゅういち", "しちし にじゅうはち", "しちご さんじゅうご",
            "しちろく しじゅうに", "しちしち しじゅうく", "しちは ごじゅうろく", "しちく ろくじゅうさん"],
        8: ["はちいちが はち", "はちに じゅうろく", "はちさん にじゅうし", "はちし さんじゅうに", "はちご しじゅう",
            "はちろく しじゅうはち", "はちしち ごじゅうろく", "はっぱ ろくじゅうし", "はっく しちじゅうに"],
        9: ["くいちが く", "くに じゅうはち", "くさん にじゅうしち", "くし さんじゅうろく", "くご しじゅうご",
            "くろく ごじゅうし", "くしち ろくじゅうさん", "くは しちじゅうに", "くく はちじゅういち"],
    ]

    /// 九九 낭송 한 줄 — 7 × 8 → "しちは ごじゅうろく"
    static func chant(_ a: Int, _ b: Int) -> String {
        precondition((Problems.minTable...Problems.maxTable).contains(a) && (Problems.minB...Problems.supportedMaxB).contains(b))
        guard let row = kuku[a], (1...row.count).contains(b) else {
            return "\(number(a)) かける \(number(b)) は \(number(a * b))"
        }
        return row[b - 1]
    }

    /// 세션 문제 낭독문 — 조사 "は" 앞을 띄어 쓴다. 히라가나만 이어 쓰면 음성 엔진이 "ごは"를 "ご飯"처럼,
    /// "いちは さん"을 "じゅうさん"처럼 읽는 경우가 있었다(음성 검수에서 확인)
    /// - 일반: "なな かける はち は？"
    /// - 빈칸 추리: "ごじゅうろく は なな かける なに？"
    /// - ○×: "なな かける はち は、ごじゅうよん。まるかな？ ばつかな？"
    static func question(_ p: Problem, mode: GameMode, statement: Statement?) -> String {
        let a = number(p.a)
        let b = number(p.b)
        switch mode {
        case .missing:
            return "\(number(p.a * p.b)) は \(a) かける なに？"
        case .truefalse:
            guard let statement else { return "\(a) かける \(b) は？" }
            return "\(a) かける \(b) は、\(number(statement.shown))。まるかな？ ばつかな？"
        default:
            return "\(a) かける \(b) は？"
        }
    }

    // MARK: - 화면 문구용 (숫자는 아라비아 숫자)

    /// "7 かける 8 は？"
    static func questionLabel(_ a: Int, _ b: Int) -> String { "\(a) かける \(b) は？" }

    /// "7 かける 8 は 56"
    static func equationLabel(_ a: Int, _ b: Int) -> String { "\(a) かける \(b) は \(a * b)" }

    /// 정답 안내 — "こたえは 56"
    static func answerIs(_ n: Int) -> String { "こたえは \(n)" }

    /// 오답 설명 — "54 じゃなくて 56 だよ"
    static func notButIs(given: Int, answer: Int) -> String { "\(given) じゃなくて \(answer) だよ" }
}
