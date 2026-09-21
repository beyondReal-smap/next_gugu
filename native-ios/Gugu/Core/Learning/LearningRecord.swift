import Foundation

// SessionResult → LearningEvent 변환 (lib/learning/record.ts 이식).
// eventId 는 아웃박스가 적재할 때 한 번만 발급한다.

/// 아직 eventId 가 붙지 않은 이벤트 — 아웃박스가 id 를 채워 확정한다
struct PendingLearningEvent: Equatable {
    var learnerId: String
    var deviceId: String
    var sessionId: String
    var sequenceNo: Int
    var factId: String
    var mode: LearningMode
    var tableNo: Int?
    var submittedAnswer: GivenAnswer
    var correct: Bool
    var responseMs: Int
    var attemptNo: Int
    var occurredAt: String
    var contentVersion: String

    func withEventId(_ eventId: String) -> LearningEvent {
        LearningEvent(
            eventId: eventId, learnerId: learnerId, deviceId: deviceId, sessionId: sessionId,
            sequenceNo: sequenceNo, factId: factId, mode: mode, tableNo: tableNo,
            submittedAnswer: submittedAnswer, correct: correct, responseMs: responseMs,
            attemptNo: attemptNo, occurredAt: occurredAt, contentVersion: contentVersion
        )
    }
}

enum LearningRecord {
    struct Ids {
        var learnerId: String
        var deviceId: String
        var sessionId: String
    }

    /// 서버 계약에 없는 모드(adventure)는 practice 로 보낸다 — 출제 단/식은 factId·tableNo 로 남는다
    static func apiMode(_ mode: GameMode) -> LearningMode {
        LearningMode(rawValue: mode.rawValue) ?? .practice
    }

    /// 빈칸 추리는 b 가 정답, 나머지는 a×b
    private static func expectedAnswer(mode: GameMode, a: Int, b: Int) -> Int {
        mode == .missing ? b : a * b
    }

    static func events(from result: SessionResult, ids: Ids, occurredAt: String = LearningContract.nowIso()) -> [PendingLearningEvent] {
        let mode = apiMode(result.mode)
        var seen: [String: Int] = [:]
        var events: [PendingLearningEvent] = []

        for (index, answer) in result.answers.enumerated() {
            let factId = LearningContract.factId(a: answer.a, b: answer.b)
            // factId 계약은 2~9단 × 1~9. 범위를 벗어나면 이 문항만 건너뛴다(세션 전체 적재 실패 방지).
            guard LearningContract.isInFactRange(a: answer.a, b: answer.b) else {
                print("[LearningRecord] 이벤트 생략: factId 범위 밖 \(factId)")
                continue
            }
            seen[factId, default: 0] += 1

            let submitted: GivenAnswer = answer.given
                ?? .number(answer.correct ? expectedAnswer(mode: result.mode, a: answer.a, b: answer.b) : 0)

            events.append(PendingLearningEvent(
                learnerId: ids.learnerId,
                deviceId: ids.deviceId,
                sessionId: ids.sessionId,
                sequenceNo: index,
                factId: factId,
                mode: mode,
                // tableNo 는 세션 단이 아니라 문항 단 — 세션 table 과 다르면 서버가 거부한다
                tableNo: answer.a,
                submittedAnswer: submitted,
                correct: answer.correct,
                responseMs: max(0, min(LearningContract.maxResponseMs, answer.ms)),
                attemptNo: seen[factId] ?? 1,
                occurredAt: occurredAt,
                contentVersion: LearningContract.contentVersion
            ))
        }
        return events
    }
}
