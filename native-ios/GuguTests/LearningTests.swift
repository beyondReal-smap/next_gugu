import XCTest
@testable import Gugu

// 서버 학습 원장 이식 검증 — 계약 검증 / 이벤트 변환 / 아웃박스 상태 전이

final class LearningTests: XCTestCase {

    private func uuid() -> String { UUID().uuidString.lowercased() }

    private func event(
        eventId: String? = nil,
        sequenceNo: Int = 0,
        factId: String = "3x4",
        tableNo: Int? = 3,
        responseMs: Int = 1200,
        attemptNo: Int = 1,
        occurredAt: String = LearningContract.nowIso(),
        contentVersion: String = LearningContract.contentVersion
    ) -> LearningEvent {
        LearningEvent(
            eventId: eventId ?? uuid(), learnerId: uuid(), deviceId: uuid(), sessionId: uuid(),
            sequenceNo: sequenceNo, factId: factId, mode: .practice, tableNo: tableNo,
            submittedAnswer: .number(12), correct: true, responseMs: responseMs,
            attemptNo: attemptNo, occurredAt: occurredAt, contentVersion: contentVersion
        )
    }

    // MARK: - 계약 검증

    /// UUID 는 8-4-4-4-12 · 버전 1~8 · variant 8/9/a/b 만 통과
    func testUuidPattern() {
        XCTAssertTrue(LearningContract.isUuid(uuid()))
        XCTAssertTrue(LearningContract.isUuid("0191d0f3-1b2c-7abc-8def-0123456789ab"))
        XCTAssertFalse(LearningContract.isUuid("not-a-uuid"))
        XCTAssertFalse(LearningContract.isUuid(""))
        // 버전 자리 0, variant 자리 c — 둘 다 계약 밖
        XCTAssertFalse(LearningContract.isUuid("0191d0f3-1b2c-0abc-8def-0123456789ab"))
        XCTAssertFalse(LearningContract.isUuid("0191d0f3-1b2c-7abc-cdef-0123456789ab"))
    }

    /// factId 는 2~9단 × 1~9 만
    func testFactIdPattern() {
        XCTAssertTrue(LearningContract.isFactId("2x1"))
        XCTAssertTrue(LearningContract.isFactId("9x9"))
        XCTAssertFalse(LearningContract.isFactId("1x3"))   // 1단 제외
        XCTAssertFalse(LearningContract.isFactId("3x0"))   // 0 제외
        XCTAssertFalse(LearningContract.isFactId("10x2"))
        XCTAssertFalse(LearningContract.isFactId("3X4"))   // 대문자 X 는 계약 밖
        XCTAssertEqual(LearningContract.factId(a: 7, b: 8), "7x8")
        XCTAssertTrue(LearningContract.isInFactRange(a: 2, b: 1))
        XCTAssertFalse(LearningContract.isInFactRange(a: 1, b: 1))
        XCTAssertFalse(LearningContract.isInFactRange(a: 3, b: 0))
    }

    /// occurredAt 은 타임존 표기가 있어야 서버가 받는다
    func testIsoDateRequiresTimezone() {
        XCTAssertTrue(LearningContract.isIsoDate("2026-09-21T04:52:03Z"))
        XCTAssertTrue(LearningContract.isIsoDate("2026-09-21T13:52:03+09:00"))
        XCTAssertFalse(LearningContract.isIsoDate("2026-09-21T13:52:03"))  // 타임존 없음
        XCTAssertFalse(LearningContract.isIsoDate("어제"))
        XCTAssertTrue(LearningContract.isIsoDate(LearningContract.nowIso()))
    }

    /// 필드별 거부 조건 — 서버가 막기 전에 여기서 걸린다
    func testValidateRejectsBadFields() {
        XCTAssertNoThrow(try LearningContract.validate(event()))

        XCTAssertThrowsError(try LearningContract.validate(event(eventId: "bad")))
        XCTAssertThrowsError(try LearningContract.validate(event(sequenceNo: -1)))
        XCTAssertThrowsError(try LearningContract.validate(event(factId: "1x1")))
        XCTAssertThrowsError(try LearningContract.validate(event(tableNo: 1)))
        XCTAssertThrowsError(try LearningContract.validate(event(tableNo: 10)))
        XCTAssertThrowsError(try LearningContract.validate(event(responseMs: -1)))
        XCTAssertThrowsError(try LearningContract.validate(event(responseMs: 600_001)))
        XCTAssertThrowsError(try LearningContract.validate(event(attemptNo: 0)))
        XCTAssertThrowsError(try LearningContract.validate(event(occurredAt: "2026-09-21T04:52:03")))
        XCTAssertThrowsError(try LearningContract.validate(event(contentVersion: "")))
        XCTAssertThrowsError(try LearningContract.validate(event(contentVersion: "버전")))
        // tableNo 는 nil 허용
        XCTAssertNoThrow(try LearningContract.validate(event(tableNo: nil)))
    }

    // MARK: - 이벤트 변환

    private let ids = LearningRecord.Ids(
        learnerId: "0191d0f3-1b2c-7abc-8def-0123456789ab",
        deviceId: "0191d0f3-1b2c-7abc-8def-0123456789ac",
        sessionId: "0191d0f3-1b2c-7abc-8def-0123456789ad"
    )

    /// adventure 는 서버 계약에 없어 practice 로 보낸다
    func testApiModeFallsBackToPractice() {
        XCTAssertEqual(LearningRecord.apiMode(.practice), .practice)
        XCTAssertEqual(LearningRecord.apiMode(.truefalse), .truefalse)
        XCTAssertEqual(LearningRecord.apiMode(.timeAttack), .timeAttack)
        XCTAssertEqual(LearningRecord.apiMode(.adventure), .practice)
    }

    /// 문항 순서·attemptNo·tableNo(세션 단 기준)가 맞는지
    func testSessionToEvents() {
        let result = SessionResult(
            mode: .practice, table: nil,
            answers: [
                AnswerRecord(a: 3, b: 4, correct: true, ms: 1500, given: .number(12)),
                AnswerRecord(a: 7, b: 2, correct: false, ms: 2200, given: .number(15)),
                AnswerRecord(a: 3, b: 4, correct: true, ms: 900, given: .number(12)),
            ],
            maxCombo: 2, durationMs: 4600
        )
        let events = LearningRecord.events(from: result, ids: ids)
        XCTAssertEqual(events.count, 3)
        XCTAssertEqual(events.map(\.sequenceNo), [0, 1, 2])
        XCTAssertEqual(events.map(\.factId), ["3x4", "7x2", "3x4"])
        // 같은 식이 다시 나오면 attemptNo 가 올라간다
        XCTAssertEqual(events.map(\.attemptNo), [1, 1, 2])
        // 단이 섞인 세션은 tableNo 를 nil 로 보낸다 — 문항별 단을 넣으면
        // 서버 RPC 가 첫 문항과 단이 다른 이벤트를 SESSION_MISMATCH 로 전부 거부한다
        XCTAssertEqual(events.map(\.tableNo), [nil, nil, nil])
        XCTAssertEqual(events[1].submittedAnswer, .number(15))
        XCTAssertEqual(events[1].correct, false)
        for e in events { XCTAssertNoThrow(try LearningContract.validate(e.withEventId(uuid()))) }
    }

    /// given 이 없는 구기록 — 정답이면 기대값, 오답이면 0
    /// 모든 문항이 같은 단이면 tableNo 가 그 단으로 나간다 (서버 세션 일관성 규칙 충족)
    func testSessionTableNoForSingleTable() {
        let result = SessionResult(
            mode: .practice, table: 6,
            answers: [
                AnswerRecord(a: 6, b: 1, correct: true, ms: 1000, given: .number(6)),
                AnswerRecord(a: 6, b: 9, correct: false, ms: 1200, given: .number(50)),
            ],
            maxCombo: 1, durationMs: 2200
        )
        let events = LearningRecord.events(from: result, ids: ids)
        XCTAssertEqual(events.map(\.tableNo), [6, 6])
        // 한 세션 안에서 값이 모두 같아야 서버가 받는다
        XCTAssertEqual(Set(events.map(\.tableNo)).count, 1)
    }

    /// given 이 없으면(제출 자체가 없던 문항) 미응답으로 남긴다 — 0 을 만들어 내지 않는다
    func testSessionToEventsWithoutGiven() {
        let practice = SessionResult(
            mode: .practice, table: 6,
            answers: [
                AnswerRecord(a: 6, b: 3, correct: true, ms: 1000),
                AnswerRecord(a: 6, b: 4, correct: false, ms: 1000),
            ],
            maxCombo: 1, durationMs: 2000
        )
        let events = LearningRecord.events(from: practice, ids: ids)
        XCTAssertEqual(events[0].submittedAnswer, .noAnswer)
        XCTAssertEqual(events[1].submittedAnswer, .noAnswer)
        // 오답에 0 을 넣으면 "아이가 0 이라고 답했다"는 사실이 아닌 기록이 된다
        XCTAssertNotEqual(events[1].submittedAnswer, .number(0))

        // 빈칸 추리는 b 가 정답
        let missing = SessionResult(
            mode: .missing, table: 6,
            answers: [AnswerRecord(a: 6, b: 7, correct: true, ms: 1000)],
            maxCombo: 1, durationMs: 1000
        )
        XCTAssertEqual(LearningRecord.events(from: missing, ids: ids)[0].submittedAnswer, .noAnswer)
    }

    /// factId 범위를 벗어난 문항은 그 문항만 빠지고 나머지는 남는다
    func testSessionToEventsSkipsOutOfRange() {
        let result = SessionResult(
            mode: .practice, table: nil,
            answers: [
                AnswerRecord(a: 1, b: 5, correct: true, ms: 800),   // 1단 — 계약 밖
                AnswerRecord(a: 4, b: 5, correct: true, ms: 800),
                AnswerRecord(a: 12, b: 3, correct: true, ms: 800),  // 12단 — 계약 밖
            ],
            maxCombo: 3, durationMs: 2400
        )
        let events = LearningRecord.events(from: result, ids: ids)
        XCTAssertEqual(events.count, 1)
        XCTAssertEqual(events[0].factId, "4x5")
        // 건너뛴 문항의 원래 인덱스가 유지된다 (서버 중복 판정 기준)
        XCTAssertEqual(events[0].sequenceNo, 1)
    }

    /// 응답 시간은 0~600초로 잘린다
    func testResponseMsClamped() {
        let result = SessionResult(
            mode: .practice, table: 2,
            answers: [
                AnswerRecord(a: 2, b: 2, correct: true, ms: -50),
                AnswerRecord(a: 2, b: 3, correct: true, ms: 999_999),
            ],
            maxCombo: 2, durationMs: 100
        )
        let events = LearningRecord.events(from: result, ids: ids)
        XCTAssertEqual(events[0].responseMs, 0)
        XCTAssertEqual(events[1].responseMs, LearningContract.maxResponseMs)
    }

    // MARK: - 아웃박스

    /// 적재 — 검증 실패는 거부, 같은 id 재적재는 멱등, 내용이 다르면 오류
    func testEnqueue() throws {
        var state = LearningOutboxState()
        let first = event()
        state = try LearningOutbox.enqueue(state, event: first)
        XCTAssertEqual(state.events.count, 1)

        // 같은 이벤트 재적재 — 그대로
        state = try LearningOutbox.enqueue(state, event: first)
        XCTAssertEqual(state.events.count, 1)

        // 같은 id, 다른 내용 — 오류
        var conflicting = first
        conflicting.correct = false
        XCTAssertThrowsError(try LearningOutbox.enqueue(state, event: conflicting)) { error in
            XCTAssertEqual(error as? LearningOutboxError, .conflictingEvent(eventId: first.eventId))
        }

        // 계약 위반은 적재되지 않는다
        XCTAssertThrowsError(try LearningOutbox.enqueue(state, event: event(factId: "1x1")))
        XCTAssertEqual(state.events.count, 1)
    }

    /// 배치는 앞에서부터 batchSize 건
    func testNextBatch() throws {
        var state = LearningOutboxState()
        for i in 0..<(LearningOutbox.batchSize + 5) {
            state = try LearningOutbox.enqueue(state, event: event(sequenceNo: i))
        }
        XCTAssertEqual(state.events.count, LearningOutbox.batchSize + 5)
        XCTAssertEqual(LearningOutbox.nextBatch(state).count, LearningOutbox.batchSize)
        XCTAssertEqual(LearningOutbox.nextBatch(state).first?.eventId, state.events.first?.eventId)
    }

    /// 상한 초과
    func testEnqueueRespectsLimit() throws {
        var state = LearningOutboxState()
        state.events = (0..<LearningOutbox.maxEvents).map { event(sequenceNo: $0) }
        XCTAssertThrowsError(try LearningOutbox.enqueue(state, event: event())) { error in
            XCTAssertEqual(error as? LearningOutboxError, .full(limit: LearningOutbox.maxEvents))
        }
    }

    /// 응답 반영 — 수락·중복은 큐에서 빠지고, 거부는 남아 재시도된다
    func testApplyResponse() throws {
        var state = LearningOutboxState()
        let accepted = event(sequenceNo: 0)
        let duplicate = event(sequenceNo: 1)
        let rejected = event(sequenceNo: 2)
        for e in [accepted, duplicate, rejected] { state = try LearningOutbox.enqueue(state, event: e) }

        let batch = LearningOutbox.nextBatch(state)
        let response = LearningEventBatchResponse(
            acceptedEventIds: [accepted.eventId],
            duplicateEventIds: [duplicate.eventId],
            rejected: [RejectedLearningEvent(eventId: rejected.eventId, code: "OUT_OF_RANGE")],
            serverCursor: 42
        )
        let next = try LearningOutbox.applyResponse(current: state, batch: batch, response: response)

        XCTAssertEqual(next.events.map(\.eventId), [rejected.eventId])
        XCTAssertEqual(next.serverCursor, 42)
        XCTAssertEqual(next.rejected, [RejectedLearningEvent(eventId: rejected.eventId, code: "OUT_OF_RANGE")])
    }

    /// 업로드 중 새로 적재된 이벤트는 보존된다
    func testApplyResponseKeepsEventsAddedDuringUpload() throws {
        var state = LearningOutboxState()
        let inBatch = event(sequenceNo: 0)
        state = try LearningOutbox.enqueue(state, event: inBatch)
        let batch = LearningOutbox.nextBatch(state)

        // 업로드 중 새 이벤트 적재
        let added = event(sequenceNo: 1)
        let current = try LearningOutbox.enqueue(state, event: added)

        let response = LearningEventBatchResponse(
            acceptedEventIds: [inBatch.eventId], duplicateEventIds: [], rejected: [], serverCursor: 7
        )
        let next = try LearningOutbox.applyResponse(current: current, batch: batch, response: response)
        XCTAssertEqual(next.events.map(\.eventId), [added.eventId])
    }

    /// 서버 커서는 뒤로 가지 않는다
    func testServerCursorNeverGoesBack() throws {
        var state = LearningOutboxState()
        state.serverCursor = 100
        let e = event()
        state = try LearningOutbox.enqueue(state, event: e)
        let response = LearningEventBatchResponse(
            acceptedEventIds: [e.eventId], duplicateEventIds: [], rejected: [], serverCursor: 5
        )
        let next = try LearningOutbox.applyResponse(current: state, batch: [e], response: response)
        XCTAssertEqual(next.serverCursor, 100)
    }

    /// 배치에 없는 이벤트를 서버가 돌려주면 상태를 바꾸지 않고 오류
    func testApplyResponseRejectsUnknownIds() throws {
        var state = LearningOutboxState()
        let e = event()
        state = try LearningOutbox.enqueue(state, event: e)
        let stranger = uuid()

        let badAccept = LearningEventBatchResponse(
            acceptedEventIds: [stranger], duplicateEventIds: [], rejected: [], serverCursor: 1
        )
        XCTAssertThrowsError(try LearningOutbox.applyResponse(current: state, batch: [e], response: badAccept))

        let badReject = LearningEventBatchResponse(
            acceptedEventIds: [], duplicateEventIds: [],
            rejected: [RejectedLearningEvent(eventId: stranger, code: "X")], serverCursor: 1
        )
        XCTAssertThrowsError(try LearningOutbox.applyResponse(current: state, batch: [e], response: badReject))
    }

    // MARK: - 직렬화

    /// 서버로 나가는 JSON 키·값 형태 (submittedAnswer 는 수/참거짓 그대로)
    func testEventJsonShape() throws {
        var e = event()
        e.submittedAnswer = .boolean(true)
        let data = try JSONEncoder().encode(e)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(json["submittedAnswer"] as? Bool, true)
        XCTAssertEqual(json["mode"] as? String, "practice")
        XCTAssertEqual(json["factId"] as? String, "3x4")
        XCTAssertNotNil(json["occurredAt"] as? String)

        e.submittedAnswer = .number(24)
        let numeric = try JSONSerialization.jsonObject(with: try JSONEncoder().encode(e)) as? [String: Any]
        XCTAssertEqual(numeric?["submittedAnswer"] as? Int, 24)

        // 왕복
        XCTAssertEqual(try JSONDecoder().decode(LearningEvent.self, from: try JSONEncoder().encode(e)), e)
    }

    /// tableNo 가 nil 이면 키가 생략된다 — 서버 tableNo 는 기본값(None)이 있어 허용된다
    func testTableNoOmittedWhenNil() throws {
        let data = try JSONEncoder().encode(event(tableNo: nil))
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertNil(json["tableNo"])
        // 서버가 extra="forbid" 이므로 계약 밖 키가 섞이면 배치 전체가 거부된다
        XCTAssertEqual(Set(json.keys), Set([
            "eventId", "learnerId", "deviceId", "sessionId", "sequenceNo", "factId", "mode",
            "submittedAnswer", "correct", "responseMs", "attemptNo", "occurredAt", "contentVersion",
        ]))
    }

    /// 서버 상한 — sequenceNo 10000, attemptNo 100
    func testValidateUpperBounds() {
        XCTAssertNoThrow(try LearningContract.validate(event(sequenceNo: 10_000)))
        XCTAssertThrowsError(try LearningContract.validate(event(sequenceNo: 10_001)))
        XCTAssertNoThrow(try LearningContract.validate(event(attemptNo: 100)))
        XCTAssertThrowsError(try LearningContract.validate(event(attemptNo: 101)))
    }
}
