package site.smap.gugudan.core.learning

import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.GivenAnswer
import site.smap.gugudan.core.SessionResult

// SessionResult → LearningEvent 변환 (lib/learning/record.ts / iOS 이식).
// eventId 는 아웃박스가 적재할 때 한 번만 발급한다.

/** 아직 eventId 가 붙지 않은 이벤트 — 아웃박스가 id 를 채워 확정한다 */
data class PendingLearningEvent(
    val learnerId: String,
    val deviceId: String,
    val sessionId: String,
    val sequenceNo: Int,
    val factId: String,
    val mode: LearningMode,
    val tableNo: Int?,
    val submittedAnswer: GivenAnswer,
    val correct: Boolean,
    val responseMs: Int,
    val attemptNo: Int,
    val occurredAt: String,
    val contentVersion: String,
) {
    fun withEventId(eventId: String) = LearningEvent(
        eventId = eventId, learnerId = learnerId, deviceId = deviceId, sessionId = sessionId,
        sequenceNo = sequenceNo, factId = factId, mode = mode, tableNo = tableNo,
        submittedAnswer = submittedAnswer, correct = correct, responseMs = responseMs,
        attemptNo = attemptNo, occurredAt = occurredAt, contentVersion = contentVersion,
    )
}

object LearningRecord {
    data class Ids(val learnerId: String, val deviceId: String, val sessionId: String)

    /** 서버 계약에 없는 모드(adventure)는 practice 로 보낸다 — 출제 단/식은 factId·tableNo 로 남는다 */
    fun apiMode(mode: GameMode): LearningMode = when (mode) {
        GameMode.PRACTICE -> LearningMode.PRACTICE
        GameMode.TIME_ATTACK -> LearningMode.TIME_ATTACK
        GameMode.CHALLENGE -> LearningMode.CHALLENGE
        GameMode.SURVIVAL -> LearningMode.SURVIVAL
        GameMode.MISSING -> LearningMode.MISSING
        GameMode.TRUEFALSE -> LearningMode.TRUEFALSE
        GameMode.ADVENTURE -> LearningMode.PRACTICE
    }

    /** 빈칸 추리는 b 가 정답, 나머지는 a×b */
    fun events(
        result: SessionResult,
        ids: Ids,
        occurredAt: String = LearningContract.nowIso(),
    ): List<PendingLearningEvent> {
        val mode = apiMode(result.mode)
        val seen = mutableMapOf<String, Int>()
        val events = mutableListOf<PendingLearningEvent>()

        result.answers.forEachIndexed { index, answer ->
            val factId = LearningContract.factId(answer.a, answer.b)
            // factId 계약은 2~9단 × 1~9. 범위를 벗어나면 이 문항만 건너뛴다(세션 전체 적재 실패 방지).
            if (!LearningContract.isInFactRange(answer.a, answer.b)) {
                println("[LearningRecord] 이벤트 생략: factId 범위 밖 $factId")
                return@forEachIndexed
            }
            seen[factId] = (seen[factId] ?: 0) + 1

            // given 이 없으면 제출 자체가 없었던 문항(배틀 시간 초과 등)이다.
            // 예전에는 오답일 때 0 을 넣었는데, 이는 "아이가 0 이라고 답했다"는
            // 사실이 아닌 기록을 만들어 낸다 — 명시적인 미응답 값으로 남긴다.
            val submitted = answer.given ?: GivenAnswer.NoAnswer

            events += PendingLearningEvent(
                learnerId = ids.learnerId,
                deviceId = ids.deviceId,
                sessionId = ids.sessionId,
                sequenceNo = index,
                factId = factId,
                mode = mode,
                // tableNo 는 세션 단이 아니라 문항 단 — 세션 table 과 다르면 서버가 거부한다
                tableNo = answer.a,
                submittedAnswer = submitted,
                correct = answer.correct,
                responseMs = answer.ms.coerceIn(0, LearningContract.MAX_RESPONSE_MS),
                attemptNo = seen[factId] ?: 1,
                occurredAt = occurredAt,
                contentVersion = LearningContract.CONTENT_VERSION,
            )
        }
        return events
    }
}
