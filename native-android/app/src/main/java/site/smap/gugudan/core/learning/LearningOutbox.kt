package site.smap.gugudan.core.learning

import kotlinx.serialization.Serializable

// 오프라인 아웃박스 (lib/sync/outbox.ts / iOS 이식).
// 웹은 localStorage 를 함수 안에서 직접 읽고 쓴다. 여기서는 상태 전이를 순수 함수로 분리해
// 단위 테스트가 가능하게 했고, 저장은 LearningOutboxStore 가 맡는다.

@Serializable
data class LearningOutboxState(
    val version: Int = 1,
    val events: List<LearningEvent> = emptyList(),
    val serverCursor: Int = 0,
    /** 서버가 거부한 기록 — 자동 삭제하지 않고 남겨 둔다 */
    val rejected: List<RejectedLearningEvent> = emptyList(),
)

sealed class LearningOutboxException(message: String) : IllegalStateException(message) {
    class Full(limit: Int) : LearningOutboxException("학습 아웃박스 상한(${limit}건)을 초과했습니다")
    class ConflictingEvent(eventId: String) :
        LearningOutboxException("같은 아이디의 학습 이벤트 내용이 다릅니다 ($eventId)")
    class ResponseOutOfBatch(eventId: String) :
        LearningOutboxException("서버가 현재 배치에 없는 이벤트를 반환했습니다 ($eventId)")
}

object LearningOutbox {
    const val MAX_EVENTS = 5_000
    const val BATCH_SIZE = 100

    /** 다음 업로드 배치 (앞에서부터 BATCH_SIZE 건) */
    fun nextBatch(state: LearningOutboxState): List<LearningEvent> = state.events.take(BATCH_SIZE)

    /** 이벤트 적재. 같은 eventId 가 이미 있으면 내용이 같을 때만 통과시킨다(재적재 멱등). */
    fun enqueue(state: LearningOutboxState, event: LearningEvent): LearningOutboxState {
        LearningContract.validate(event)

        val existing = state.events.firstOrNull { it.eventId == event.eventId }
        if (existing != null) {
            if (existing != event) throw LearningOutboxException.ConflictingEvent(event.eventId)
            return state
        }
        if (state.events.size >= MAX_EVENTS) throw LearningOutboxException.Full(MAX_EVENTS)
        return state.copy(events = state.events + event)
    }

    /**
     * 업로드 응답 반영. 업로드 중 새로 적재된 이벤트를 보존하기 위해
     * 호출자가 최신 상태(current)를 넘긴다.
     */
    fun applyResponse(
        current: LearningOutboxState,
        batch: List<LearningEvent>,
        response: LearningEventBatchResponse,
    ): LearningOutboxState {
        val batchIds = batch.map { it.eventId }.toSet()
        val completed = (response.acceptedEventIds + response.duplicateEventIds).toSet()

        response.rejected.firstOrNull { it.eventId !in batchIds }?.let {
            throw LearningOutboxException.ResponseOutOfBatch(it.eventId)
        }
        completed.firstOrNull { it !in batchIds }?.let {
            throw LearningOutboxException.ResponseOutOfBatch(it)
        }

        // 거부 기록은 최신 것으로 덮고, 완료된 건은 거부 목록에서 지운다
        val rejections = LinkedHashMap<String, RejectedLearningEvent>()
        current.rejected.forEach { rejections[it.eventId] = it }
        response.rejected.forEach { rejections[it.eventId] = it }
        completed.forEach { rejections.remove(it) }

        // 적재 순서를 유지해 재시도 순서가 흔들리지 않게 한다
        val ordered = current.events.mapNotNull { rejections[it.eventId] }
        val orphans = rejections.values.filter { item ->
            current.events.none { it.eventId == item.eventId }
        }
        return current.copy(
            events = current.events.filter { it.eventId !in completed },
            serverCursor = maxOf(current.serverCursor, response.serverCursor),
            rejected = ordered + orphans,
        )
    }
}
