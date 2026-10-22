import Foundation

// 소리 내어 읽을 문장 → 미리 만든 음성 파일(키) 연결.
// 문장이 552개로 한정돼 있어 ElevenLabs 로 한 번 생성해 앱에 넣었다 (tools/voice).
// 키 규칙은 tools/voice/phrases.py 와 같고, 두 쪽이 어긋나면 VoiceLinesTests 가 실패한다.

/// 읽을 한 줄 — key 로 음성 파일을 찾고, text 는 그 파일이 읽는 문장이다
struct VoiceLine: Equatable {
    let key: String
    let text: String
}

enum VoiceLines {
    /// 세션 문제 낭독 — 모드별 문장 (SpokenMath.question 과 같은 글). 키는 언어와 무관하고, 파일은 언어별 폴더에 있다
    static func question(_ p: Problem, mode: GameMode, statement: Statement?,
                         language: AppLanguage = .current) -> VoiceLine {
        switch mode {
        case .missing:
            return VoiceLine(key: "m-\(p.a)x\(p.b)",
                             text: SpokenMath.question(p, mode: .missing, statement: nil, language: language))
        case .truefalse:
            if let statement {
                return VoiceLine(key: "ox-\(p.a)x\(p.b)-\(statement.shown)",
                                 text: SpokenMath.question(p, mode: .truefalse, statement: statement, language: language))
            }
            return plain(p, language)
        default:
            return plain(p, language)
        }
    }

    /// 구구단 외우기 한 줄 — "칠 팔은 오십육." / "Seven times eight is fifty-six." / "しちは ごじゅうろく。"
    /// 한국어는 화면 글(KoreanReading.chant)과 달리 마침표로 끝낸다 — 줄마다 끝을 내려 읽는 억양이 나오게 한다.
    /// 곱하는 수가 2면 "삼, 이는 육."처럼 쉼표로 끊는다 — 붙여 읽으면 '이'가 앞 숫자와 섞여 "삼위는"으로 들린다(음성 검수에서 확인)
    static func chant(_ a: Int, _ b: Int, language: AppLanguage = .current) -> VoiceLine {
        let key = "c-\(a)x\(b)"
        switch language {
        case .ko:
            let head = KoreanReading.sino(a) + (b == 2 ? "," : "")
            return VoiceLine(key: key,
                             text: "\(head) \(KoreanReading.topic(KoreanReading.sino(b))) \(KoreanReading.sino(a * b)).")
        case .en:
            return VoiceLine(key: key, text: EnglishReading.chant(a, b))
        case .ja:
            return VoiceLine(key: key, text: JapaneseReading.chant(a, b) + "。")
        }
    }

    private static func plain(_ p: Problem, _ language: AppLanguage) -> VoiceLine {
        VoiceLine(key: "q-\(p.a)x\(p.b)", text: SpokenMath.question(p, mode: .practice, statement: nil, language: language))
    }
}
