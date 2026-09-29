import XCTest
@testable import Gugu

/// 내장 음성 팩 검사 — 앱이 읽을 수 있는 모든 줄에 음성 파일이 있고, 파일이 읽는 문장이 앱 문장과 같은지.
/// 음성 팩은 tools/voice/generate.py build 로 만든다. 규칙이 바뀌면 다시 만들어야 이 테스트가 통과한다.
final class VoiceLinesTests: XCTestCase {

    private struct Manifest: Decodable {
        let voice: String
        let model: String
        let lines: [String: String]
    }

    /// 앱이 읽을 수 있는 모든 줄 (문제·빈칸·OX 의 모든 보기 값·구구단 외우기)
    private var allLines: [VoiceLine] {
        var out: [VoiceLine] = []
        for a in Problems.minTable...Problems.maxTable {
            for b in Problems.minB...Problems.maxB {
                let p = Problem(a: a, b: b)
                out.append(VoiceLines.question(p, mode: .practice, statement: nil))
                out.append(VoiceLines.question(p, mode: .missing, statement: nil))
                for shown in Set([a * b] + Problems.statementCandidates(p)) {
                    let st = Statement(shown: shown, isTrue: shown == a * b)
                    out.append(VoiceLines.question(p, mode: .truefalse, statement: st))
                }
                out.append(VoiceLines.chant(a, b))
            }
        }
        return out
    }

    func testQuestionKeysPerMode() {
        let p = Problem(a: 7, b: 8)
        for mode in [GameMode.practice, .timeAttack, .challenge, .survival, .adventure] {
            XCTAssertEqual(VoiceLines.question(p, mode: mode, statement: nil),
                           VoiceLine(key: "q-7x8", text: "칠 곱하기 팔은?"))
        }
        XCTAssertEqual(VoiceLines.question(p, mode: .missing, statement: nil).key, "m-7x8")
        XCTAssertEqual(VoiceLines.question(p, mode: .truefalse, statement: Statement(shown: 49, isTrue: false)),
                       VoiceLine(key: "ox-7x8-49", text: "칠 곱하기 팔은 사십구. 맞을까요?"))
        XCTAssertEqual(VoiceLines.chant(7, 8), VoiceLine(key: "c-7x8", text: "칠 팔은 오십육."))
        XCTAssertEqual(VoiceLines.chant(3, 2), VoiceLine(key: "c-3x2", text: "삼, 이는 육."))
    }

    func testVoicePackCoversEveryLine() throws {
        let url = try XCTUnwrap(Bundle.main.url(forResource: "voice-manifest", withExtension: "json"),
                                "음성 팩이 없습니다 — python3 tools/voice/generate.py build 를 실행하세요")
        let manifest = try JSONDecoder().decode(Manifest.self, from: Data(contentsOf: url))
        let lines = allLines
        XCTAssertEqual(Set(lines.map(\.key)), Set(manifest.lines.keys), "앱 문장 목록과 음성 팩 목록이 다릅니다")
        for line in lines {
            XCTAssertEqual(manifest.lines[line.key], line.text, "문장이 바뀌었습니다 — 음성 팩을 다시 만드세요: \(line.key)")
            XCTAssertNotNil(Bundle.main.url(forResource: line.key, withExtension: "m4a"), "음성 파일 없음: \(line.key)")
        }
    }
}
