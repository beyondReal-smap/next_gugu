import Foundation

// 오프라인 아웃박스 (lib/sync/outbox.ts 이식).
// 웹은 localStorage 를 함수 안에서 직접 읽고 쓴다. 여기서는 상태 전이를 순수 함수로 분리해
// 단위 테스트가 가능하게 했고, 저장은 LearningOutboxStore 가 맡는다.

struct LearningOutboxState: Codable, Equatable {
    var version: Int = 1
    var events: [LearningEvent] = []
    var serverCursor: Int = 0
    /// 서버가 거부한 기록 — 자동 삭제하지 않고 남겨 둔다
    var rejected: [RejectedLearningEvent] = []
}

enum LearningOutboxError: LocalizedError, Equatable {
    case full(limit: Int)
    case conflictingEvent(eventId: String)
    case responseOutOfBatch(eventId: String)

    var errorDescription: String? {
        switch self {
        case let .full(limit):
            return "학습 아웃박스 상한(\(limit)건)을 초과했습니다"
        case let .conflictingEvent(eventId):
            return "같은 아이디의 학습 이벤트 내용이 다릅니다 (\(eventId))"
        case let .responseOutOfBatch(eventId):
            return "서버가 현재 배치에 없는 이벤트를 반환했습니다 (\(eventId))"
        }
    }
}

enum LearningOutbox {
    static let maxEvents = 5_000
    static let batchSize = 100

    /// 다음 업로드 배치 (앞에서부터 batchSize 건)
    static func nextBatch(_ state: LearningOutboxState) -> [LearningEvent] {
        Array(state.events.prefix(batchSize))
    }

    /// 이벤트 적재. 같은 eventId 가 이미 있으면 내용이 같을 때만 통과시킨다(재적재 멱등).
    static func enqueue(_ state: LearningOutboxState, event: LearningEvent) throws -> LearningOutboxState {
        try LearningContract.validate(event)

        if let existing = state.events.first(where: { $0.eventId == event.eventId }) {
            guard existing == event else {
                throw LearningOutboxError.conflictingEvent(eventId: event.eventId)
            }
            return state
        }
        guard state.events.count < maxEvents else {
            throw LearningOutboxError.full(limit: maxEvents)
        }
        var next = state
        next.events.append(event)
        return next
    }

    /// 업로드 응답 반영. 업로드 중 새로 적재된 이벤트를 보존하기 위해
    /// 호출자가 최신 상태(current)를 넘긴다.
    static func applyResponse(
        current: LearningOutboxState,
        batch: [LearningEvent],
        response: LearningEventBatchResponse
    ) throws -> LearningOutboxState {
        let batchIds = Set(batch.map(\.eventId))
        let completed = Set(response.acceptedEventIds + response.duplicateEventIds)

        for item in response.rejected where !batchIds.contains(item.eventId) {
            throw LearningOutboxError.responseOutOfBatch(eventId: item.eventId)
        }
        for eventId in completed where !batchIds.contains(eventId) {
            throw LearningOutboxError.responseOutOfBatch(eventId: eventId)
        }

        // 거부 기록은 최신 것으로 덮고, 완료된 건은 거부 목록에서 지운다
        var rejections: [String: RejectedLearningEvent] = [:]
        for item in current.rejected { rejections[item.eventId] = item }
        for item in response.rejected { rejections[item.eventId] = item }
        for eventId in completed { rejections.removeValue(forKey: eventId) }

        var next = current
        next.events = current.events.filter { !completed.contains($0.eventId) }
        next.serverCursor = max(current.serverCursor, response.serverCursor)
        // 적재 순서를 유지해 재시도 순서가 흔들리지 않게 한다
        next.rejected = current.events
            .compactMap { rejections[$0.eventId] }
            + rejections.values.filter { item in
                !current.events.contains { $0.eventId == item.eventId }
            }
        return next
    }
}
