import Foundation

// 서버 학습 원장 계약 (lib/api/learning.ts 이식).
// 검증 규칙은 서버가 거부하는 조건을 클라이언트에서 미리 걸러내기 위한 것으로,
// 웹 validateLearningEvent 와 같은 기준을 쓴다.

/// 서버가 받는 모드 — adventure 는 계약에 없어 practice 로 보낸다
enum LearningMode: String, Codable, CaseIterable {
    case practice, timeAttack, challenge, survival, missing, truefalse
}

struct LearningEvent: Codable, Equatable, Identifiable {
    var eventId: String
    var learnerId: String
    var deviceId: String
    var sessionId: String
    var sequenceNo: Int
    /// "2x3" ~ "9x9"
    var factId: String
    var mode: LearningMode
    /// 문항의 단 (2~9) 또는 nil
    var tableNo: Int?
    var submittedAnswer: GivenAnswer
    var correct: Bool
    var responseMs: Int
    var attemptNo: Int
    /// ISO8601 (타임존 표기 필수)
    var occurredAt: String
    var contentVersion: String

    var id: String { eventId }
}

// 서버는 extra="forbid" 이고 tableNo 에 기본값(None)이 있다.
// Swift 기본 인코더가 nil 옵셔널 키를 생략하는 동작이 서버 계약과 맞으므로 그대로 쓴다.

struct RejectedLearningEvent: Codable, Equatable {
    var eventId: String
    var code: String
}

struct LearningEventBatchResponse: Codable, Equatable {
    var acceptedEventIds: [String]
    var duplicateEventIds: [String]
    var rejected: [RejectedLearningEvent]
    var serverCursor: Int
}

struct LearningSnapshot: Codable, Equatable {
    var learnerId: String
    var revision: Int
    var serverCursor: Int
    var updatedAt: String
}

struct LearningStateResponse: Codable, Equatable {
    var snapshots: [LearningSnapshot]
    var serverCursor: Int
}

/// 귀속 후보 — 서버가 이 계정에 딸린 학습자 목록을 준다
struct LearningClaimCandidate: Codable, Equatable, Identifiable {
    var learnerId: String
    var displayName: String
    var updatedAt: String

    var id: String { learnerId }
}

/// 귀속 요청 결과.
/// - createLearner: 서버가 학습자를 새로 만들었다 (learnerId 있음)
/// - attachToExisting: 기존 학습자에 붙인다 (learnerId 가 없으면 후보 1명을 confirm 으로 확정)
/// - conflict: 후보가 여럿이거나 다른 계정이 쓰던 기기 — 사용자가 골라야 한다
struct LearningClaimResponse: Codable, Equatable {
    enum Action: String, Codable {
        case createLearner = "create_learner"
        case attachToExisting = "attach_to_existing"
        case conflict
    }

    var action: Action
    var candidates: [LearningClaimCandidate]
    var learnerId: String?
}

struct LearningClaimConfirmResponse: Codable, Equatable {
    var learnerId: String
}

enum LearningValidationError: LocalizedError, Equatable {
    case field(String)

    var errorDescription: String? {
        switch self {
        case let .field(name): return "학습 이벤트 \(name) 형식이 올바르지 않습니다"
        }
    }
}

enum LearningContract {
    static let contentVersion = "1.0.0"
    /// factId 계약 — 2~9단 × 1~9
    static let minTable = 2
    static let maxTable = 9
    static let minMultiplier = 1
    static let maxMultiplier = 9
    /// 서버가 받는 응답 시간 상한
    static let maxResponseMs = 600_000
    /// 서버 Field(ge=0, le=10_000)
    static let maxSequenceNo = 10_000
    /// 서버 Field(ge=1, le=100)
    static let maxAttemptNo = 100

    static func isUuid(_ value: String) -> Bool {
        // 8-4-4-4-12, 버전 1~8, variant 8/9/a/b
        let pattern = "^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
        return value.range(of: pattern, options: [.regularExpression, .caseInsensitive]) != nil
    }

    static func isFactId(_ value: String) -> Bool {
        value.range(of: "^[2-9]x[1-9]$", options: .regularExpression) != nil
    }

    static func factId(a: Int, b: Int) -> String { "\(a)x\(b)" }

    /// factId 로 보낼 수 있는 범위인지 (범위 밖 문항은 이벤트에서 제외한다)
    static func isInFactRange(a: Int, b: Int) -> Bool {
        (minTable...maxTable).contains(a) && (minMultiplier...maxMultiplier).contains(b)
    }

    static func isContentVersion(_ value: String) -> Bool {
        value.range(of: "^[A-Za-z0-9._-]{1,64}$", options: .regularExpression) != nil
    }

    /// 타임존 표기가 있는 ISO8601 인지 (서버가 Z 또는 ±HH:MM 을 요구)
    static func isIsoDate(_ value: String) -> Bool {
        value.range(of: "(?:Z|[+-]\\d{2}:\\d{2})$", options: [.regularExpression, .caseInsensitive]) != nil
            && ISO8601DateFormatter().date(from: value) != nil
    }

    /// 지금 시각을 서버 계약 형식으로
    static func nowIso() -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.string(from: Date())
    }

    /// 서버가 거부할 이벤트를 미리 걸러낸다 — 통과하면 그대로 업로드 가능
    static func validate(_ event: LearningEvent) throws {
        guard isUuid(event.eventId) else { throw LearningValidationError.field("eventId") }
        guard isUuid(event.learnerId) else { throw LearningValidationError.field("learnerId") }
        guard isUuid(event.deviceId) else { throw LearningValidationError.field("deviceId") }
        guard isUuid(event.sessionId) else { throw LearningValidationError.field("sessionId") }
        guard event.sequenceNo >= 0, event.sequenceNo <= maxSequenceNo else {
            throw LearningValidationError.field("sequenceNo")
        }
        guard isFactId(event.factId) else { throw LearningValidationError.field("factId") }
        if let tableNo = event.tableNo {
            guard (minTable...maxTable).contains(tableNo) else { throw LearningValidationError.field("tableNo") }
        }
        guard event.responseMs >= 0, event.responseMs <= maxResponseMs else {
            throw LearningValidationError.field("responseMs")
        }
        guard event.attemptNo >= 1, event.attemptNo <= maxAttemptNo else {
            throw LearningValidationError.field("attemptNo")
        }
        guard isIsoDate(event.occurredAt) else { throw LearningValidationError.field("occurredAt") }
        guard isContentVersion(event.contentVersion) else {
            throw LearningValidationError.field("contentVersion")
        }
    }
}
