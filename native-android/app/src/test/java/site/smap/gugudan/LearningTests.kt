package site.smap.gugudan

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.smap.gugudan.core.AnswerRecord
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.GivenAnswer
import site.smap.gugudan.core.SessionResult
import site.smap.gugudan.core.learning.*
import java.util.UUID

// 서버 학습 원장 이식 검증 (iOS LearningTests 미러링)

class LearningTests {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun uuid() = UUID.randomUUID().toString().lowercase()

    private fun event(
        eventId: String? = null,
        sequenceNo: Int = 0,
        factId: String = "3x4",
        tableNo: Int? = 3,
        responseMs: Int = 1200,
        attemptNo: Int = 1,
        occurredAt: String = LearningContract.nowIso(),
        contentVersion: String = LearningContract.CONTENT_VERSION,
    ) = LearningEvent(
        eventId = eventId ?: uuid(), learnerId = uuid(), deviceId = uuid(), sessionId = uuid(),
        sequenceNo = sequenceNo, factId = factId, mode = LearningMode.PRACTICE, tableNo = tableNo,
        submittedAnswer = GivenAnswer.Number(12), correct = true, responseMs = responseMs,
        attemptNo = attemptNo, occurredAt = occurredAt, contentVersion = contentVersion,
    )

    private val ids = LearningRecord.Ids(
        learnerId = "0191d0f3-1b2c-7abc-8def-0123456789ab",
        deviceId = "0191d0f3-1b2c-7abc-8def-0123456789ac",
        sessionId = "0191d0f3-1b2c-7abc-8def-0123456789ad",
    )

    // MARK: 계약 검증

    /** UUID 는 8-4-4-4-12 · 버전 1~8 · variant 8/9/a/b 만 통과 */
    @Test fun uuidPattern() {
        assertTrue(LearningContract.isUuid(uuid()))
        assertTrue(LearningContract.isUuid("0191d0f3-1b2c-7abc-8def-0123456789ab"))
        assertFalse(LearningContract.isUuid("not-a-uuid"))
        assertFalse(LearningContract.isUuid(""))
        assertFalse(LearningContract.isUuid("0191d0f3-1b2c-0abc-8def-0123456789ab"))  // 버전 0
        assertFalse(LearningContract.isUuid("0191d0f3-1b2c-7abc-cdef-0123456789ab"))  // variant c
    }

    /** factId 는 2~9단 × 1~9 만 */
    @Test fun factIdPattern() {
        assertTrue(LearningContract.isFactId("2x1"))
        assertTrue(LearningContract.isFactId("9x9"))
        assertFalse(LearningContract.isFactId("1x3"))
        assertFalse(LearningContract.isFactId("3x0"))
        assertFalse(LearningContract.isFactId("10x2"))
        assertFalse(LearningContract.isFactId("3X4"))
        assertEquals("7x8", LearningContract.factId(7, 8))
        assertTrue(LearningContract.isInFactRange(2, 1))
        assertFalse(LearningContract.isInFactRange(1, 1))
        assertFalse(LearningContract.isInFactRange(3, 0))
    }

    /** occurredAt 은 타임존 표기가 있어야 서버가 받는다 */
    @Test fun isoDateRequiresTimezone() {
        assertTrue(LearningContract.isIsoDate("2026-09-21T04:52:03Z"))
        assertTrue(LearningContract.isIsoDate("2026-09-21T13:52:03+09:00"))
        assertFalse(LearningContract.isIsoDate("2026-09-21T13:52:03"))
        assertFalse(LearningContract.isIsoDate("어제"))
        assertTrue(LearningContract.isIsoDate(LearningContract.nowIso()))
    }

    /** 필드별 거부 조건 */
    @Test fun validateRejectsBadFields() {
        LearningContract.validate(event())  // 통과
        for (bad in listOf(
            event(eventId = "bad"), event(sequenceNo = -1), event(factId = "1x1"),
            event(tableNo = 1), event(tableNo = 10), event(responseMs = -1),
            event(responseMs = 600_001), event(attemptNo = 0),
            event(occurredAt = "2026-09-21T04:52:03"), event(contentVersion = ""),
            event(contentVersion = "버전"),
        )) {
            runCatching { LearningContract.validate(bad) }
                .onSuccess { throw AssertionError("거부되어야 함: $bad") }
        }
        LearningContract.validate(event(tableNo = null))  // null 허용
    }

    /** 서버 상한 — sequenceNo 10000, attemptNo 100 */
    @Test fun validateUpperBounds() {
        LearningContract.validate(event(sequenceNo = 10_000))
        assertTrue(runCatching { LearningContract.validate(event(sequenceNo = 10_001)) }.isFailure)
        LearningContract.validate(event(attemptNo = 100))
        assertTrue(runCatching { LearningContract.validate(event(attemptNo = 101)) }.isFailure)
    }

    // MARK: 이벤트 변환

    /** adventure 는 서버 계약에 없어 practice 로 보낸다 */
    @Test fun apiModeFallsBackToPractice() {
        assertEquals(LearningMode.PRACTICE, LearningRecord.apiMode(GameMode.PRACTICE))
        assertEquals(LearningMode.TRUEFALSE, LearningRecord.apiMode(GameMode.TRUEFALSE))
        assertEquals(LearningMode.TIME_ATTACK, LearningRecord.apiMode(GameMode.TIME_ATTACK))
        assertEquals(LearningMode.PRACTICE, LearningRecord.apiMode(GameMode.ADVENTURE))
    }

    /** 문항 순서·attemptNo·tableNo(세션 단 기준) */
    @Test fun sessionToEvents() {
        val result = SessionResult(
            mode = GameMode.PRACTICE, table = null,
            answers = listOf(
                AnswerRecord(3, 4, true, 1500, GivenAnswer.Number(12)),
                AnswerRecord(7, 2, false, 2200, GivenAnswer.Number(15)),
                AnswerRecord(3, 4, true, 900, GivenAnswer.Number(12)),
            ),
            maxCombo = 2, durationMs = 4600,
        )
        val events = LearningRecord.events(result, ids)
        assertEquals(3, events.size)
        assertEquals(listOf(0, 1, 2), events.map { it.sequenceNo })
        assertEquals(listOf("3x4", "7x2", "3x4"), events.map { it.factId })
        assertEquals(listOf(1, 1, 2), events.map { it.attemptNo })   // 같은 식 재출현
        // 단이 섞인 세션은 tableNo 를 null 로 보낸다 — 문항별 단을 넣으면
        // 서버 RPC 가 첫 문항과 단이 다른 이벤트를 SESSION_MISMATCH 로 전부 거부한다
        assertEquals(listOf(null, null, null), events.map { it.tableNo })
        assertEquals(GivenAnswer.Number(15), events[1].submittedAnswer)
        assertFalse(events[1].correct)
        events.forEach { LearningContract.validate(it.withEventId(uuid())) }
    }

    /** 모든 문항이 같은 단이면 tableNo 가 그 단으로 나간다 (서버 세션 일관성 규칙 충족) */
    @Test fun sessionTableNoForSingleTable() {
        val result = SessionResult(
            mode = GameMode.PRACTICE, table = 6,
            answers = listOf(
                AnswerRecord(6, 1, true, 1000, GivenAnswer.Number(6)),
                AnswerRecord(6, 9, false, 1200, GivenAnswer.Number(50)),
            ),
            maxCombo = 1, durationMs = 2200,
        )
        val events = LearningRecord.events(result, ids)
        assertEquals(listOf(6, 6), events.map { it.tableNo })
        // 한 세션 안에서 값이 모두 같아야 서버가 받는다
        assertEquals(1, events.map { it.tableNo }.toSet().size)
    }

    /** given 이 없으면(제출 자체가 없던 문항) 미응답으로 남긴다 — 0 을 만들어 내지 않는다 */
    @Test fun sessionToEventsWithoutGiven() {
        val practice = SessionResult(
            mode = GameMode.PRACTICE, table = 6,
            answers = listOf(AnswerRecord(6, 3, true, 1000), AnswerRecord(6, 4, false, 1000)),
            maxCombo = 1, durationMs = 2000,
        )
        val events = LearningRecord.events(practice, ids)
        assertEquals(GivenAnswer.NoAnswer, events[0].submittedAnswer)
        assertEquals(GivenAnswer.NoAnswer, events[1].submittedAnswer)
        // 오답에 0 을 넣으면 "아이가 0 이라고 답했다"는 사실이 아닌 기록이 된다
        assertNotEquals(GivenAnswer.Number(0), events[1].submittedAnswer)

        val missing = SessionResult(
            mode = GameMode.MISSING, table = 6,
            answers = listOf(AnswerRecord(6, 7, true, 1000)),
            maxCombo = 1, durationMs = 1000,
        )
        assertEquals(GivenAnswer.NoAnswer, LearningRecord.events(missing, ids)[0].submittedAnswer)
    }

    /** factId 범위 밖 문항만 빠지고 원래 인덱스는 유지된다 */
    @Test fun sessionToEventsSkipsOutOfRange() {
        val result = SessionResult(
            mode = GameMode.PRACTICE, table = null,
            answers = listOf(
                AnswerRecord(1, 5, true, 800),    // 1단 — 계약 밖
                AnswerRecord(4, 5, true, 800),
                AnswerRecord(12, 3, true, 800),   // 12단 — 계약 밖
            ),
            maxCombo = 3, durationMs = 2400,
        )
        val events = LearningRecord.events(result, ids)
        assertEquals(1, events.size)
        assertEquals("4x5", events[0].factId)
        assertEquals(1, events[0].sequenceNo)
    }

    /** 응답 시간은 0~600초로 잘린다 */
    @Test fun responseMsClamped() {
        val result = SessionResult(
            mode = GameMode.PRACTICE, table = 2,
            answers = listOf(AnswerRecord(2, 2, true, -50), AnswerRecord(2, 3, true, 999_999)),
            maxCombo = 2, durationMs = 100,
        )
        val events = LearningRecord.events(result, ids)
        assertEquals(0, events[0].responseMs)
        assertEquals(LearningContract.MAX_RESPONSE_MS, events[1].responseMs)
    }

    // MARK: 아웃박스

    /** 적재 — 검증 실패 거부, 같은 id 재적재 멱등, 내용 다르면 오류 */
    @Test fun enqueue() {
        var state = LearningOutboxState()
        val first = event()
        state = LearningOutbox.enqueue(state, first)
        assertEquals(1, state.events.size)

        state = LearningOutbox.enqueue(state, first)   // 멱등
        assertEquals(1, state.events.size)

        val conflicting = first.copy(correct = false)
        assertTrue(runCatching { LearningOutbox.enqueue(state, conflicting) }.exceptionOrNull()
            is LearningOutboxException.ConflictingEvent)

        assertTrue(runCatching { LearningOutbox.enqueue(state, event(factId = "1x1")) }.isFailure)
        assertEquals(1, state.events.size)
    }

    /** 배치는 앞에서부터 BATCH_SIZE 건 */
    @Test fun nextBatch() {
        var state = LearningOutboxState()
        repeat(LearningOutbox.BATCH_SIZE + 5) { state = LearningOutbox.enqueue(state, event(sequenceNo = it)) }
        assertEquals(LearningOutbox.BATCH_SIZE + 5, state.events.size)
        assertEquals(LearningOutbox.BATCH_SIZE, LearningOutbox.nextBatch(state).size)
        assertEquals(state.events.first().eventId, LearningOutbox.nextBatch(state).first().eventId)
    }

    /** 상한 초과 */
    @Test fun enqueueRespectsLimit() {
        val state = LearningOutboxState(
            events = (0 until LearningOutbox.MAX_EVENTS).map { event(sequenceNo = it) }
        )
        assertTrue(runCatching { LearningOutbox.enqueue(state, event()) }.exceptionOrNull()
            is LearningOutboxException.Full)
    }

    /** 응답 반영 — 수락·중복은 빠지고 거부는 남아 재시도된다 */
    @Test fun applyResponse() {
        var state = LearningOutboxState()
        val accepted = event(sequenceNo = 0)
        val duplicate = event(sequenceNo = 1)
        val rejected = event(sequenceNo = 2)
        listOf(accepted, duplicate, rejected).forEach { state = LearningOutbox.enqueue(state, it) }

        val batch = LearningOutbox.nextBatch(state)
        val response = LearningEventBatchResponse(
            acceptedEventIds = listOf(accepted.eventId),
            duplicateEventIds = listOf(duplicate.eventId),
            rejected = listOf(RejectedLearningEvent(rejected.eventId, "OUT_OF_RANGE")),
            serverCursor = 42,
        )
        val next = LearningOutbox.applyResponse(state, batch, response)
        assertEquals(listOf(rejected.eventId), next.events.map { it.eventId })
        assertEquals(42, next.serverCursor)
        assertEquals(listOf(RejectedLearningEvent(rejected.eventId, "OUT_OF_RANGE")), next.rejected)
    }

    /** 업로드 중 새로 적재된 이벤트는 보존된다 */
    @Test fun applyResponseKeepsEventsAddedDuringUpload() {
        var state = LearningOutboxState()
        val inBatch = event(sequenceNo = 0)
        state = LearningOutbox.enqueue(state, inBatch)
        val batch = LearningOutbox.nextBatch(state)

        val added = event(sequenceNo = 1)
        val current = LearningOutbox.enqueue(state, added)

        val response = LearningEventBatchResponse(
            acceptedEventIds = listOf(inBatch.eventId), serverCursor = 7
        )
        val next = LearningOutbox.applyResponse(current, batch, response)
        assertEquals(listOf(added.eventId), next.events.map { it.eventId })
    }

    /** 서버 커서는 뒤로 가지 않는다 */
    @Test fun serverCursorNeverGoesBack() {
        val e = event()
        val state = LearningOutbox.enqueue(LearningOutboxState(serverCursor = 100), e)
        val next = LearningOutbox.applyResponse(
            state, listOf(e), LearningEventBatchResponse(acceptedEventIds = listOf(e.eventId), serverCursor = 5)
        )
        assertEquals(100, next.serverCursor)
    }

    /** 배치에 없는 이벤트를 서버가 돌려주면 오류 */
    @Test fun applyResponseRejectsUnknownIds() {
        val e = event()
        val state = LearningOutbox.enqueue(LearningOutboxState(), e)
        val stranger = uuid()

        assertTrue(runCatching {
            LearningOutbox.applyResponse(state, listOf(e),
                LearningEventBatchResponse(acceptedEventIds = listOf(stranger), serverCursor = 1))
        }.exceptionOrNull() is LearningOutboxException.ResponseOutOfBatch)

        assertTrue(runCatching {
            LearningOutbox.applyResponse(state, listOf(e),
                LearningEventBatchResponse(rejected = listOf(RejectedLearningEvent(stranger, "X")), serverCursor = 1))
        }.exceptionOrNull() is LearningOutboxException.ResponseOutOfBatch)
    }

    // MARK: 직렬화

    /** 서버로 나가는 JSON 키·값 형태 (submittedAnswer 는 수/참거짓 그대로) */
    @Test fun eventJsonShape() {
        val boolEvent = event().copy(submittedAnswer = GivenAnswer.Boolean(true))
        val obj = json.encodeToString(LearningEvent.serializer(), boolEvent).let {
            json.parseToJsonElement(it).jsonObject
        }
        assertEquals("true", obj["submittedAnswer"]!!.jsonPrimitive.content)
        assertEquals("practice", obj["mode"]!!.jsonPrimitive.content)
        assertEquals("3x4", obj["factId"]!!.jsonPrimitive.content)

        val numeric = event().copy(submittedAnswer = GivenAnswer.Number(24))
        val encoded = json.encodeToString(LearningEvent.serializer(), numeric)
        assertEquals("24", json.parseToJsonElement(encoded).jsonObject["submittedAnswer"]!!.jsonPrimitive.content)
        // 왕복
        assertEquals(numeric, json.decodeFromString(LearningEvent.serializer(), encoded))
    }

    /** 서버가 extra="forbid" 이므로 계약 밖 키가 섞이면 배치 전체가 거부된다 */
    @Test fun eventJsonKeysMatchContract() {
        val obj = json.parseToJsonElement(
            json.encodeToString(LearningEvent.serializer(), event(tableNo = null))
        ).jsonObject
        assertEquals(
            setOf("eventId", "learnerId", "deviceId", "sessionId", "sequenceNo", "factId", "mode",
                "tableNo", "submittedAnswer", "correct", "responseMs", "attemptNo",
                "occurredAt", "contentVersion"),
            obj.keys,
        )
        // tableNo 는 null 로 나가고, 서버는 기본값(None)이 있어 허용한다
        assertTrue(obj["tableNo"]!!.jsonPrimitive.content == "null")
        assertNull(obj["id"])
    }
}
