package site.smap.gugudan.core.learning

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import site.smap.gugudan.core.GivenAnswer

// 서버 학습 원장 계약 (lib/api/learning.ts / iOS LearningModels.swift 이식).
// 검증 규칙은 서버가 거부하는 조건을 클라이언트에서 미리 걸러내기 위한 것이다.

/** 서버가 받는 모드 — adventure 는 계약에 없어 practice 로 보낸다 */
@Serializable
enum class LearningMode {
    @SerialName("practice") PRACTICE,
    @SerialName("timeAttack") TIME_ATTACK,
    @SerialName("challenge") CHALLENGE,
    @SerialName("survival") SURVIVAL,
    @SerialName("missing") MISSING,
    @SerialName("truefalse") TRUEFALSE,
}

// 서버는 extra="forbid" 이고 tableNo 에 기본값(None)이 있다.
// kotlinx 는 null 을 명시적으로 내보내고 Swift 는 키를 생략한다 — 서버는 둘 다 받는다.
@Serializable
data class LearningEvent(
    val eventId: String,
    val learnerId: String,
    val deviceId: String,
    val sessionId: String,
    val sequenceNo: Int,
    /** "2x3" ~ "9x9" */
    val factId: String,
    val mode: LearningMode,
    /** 문항의 단 (2~9) 또는 null */
    val tableNo: Int?,
    val submittedAnswer: GivenAnswer,
    val correct: Boolean,
    val responseMs: Int,
    val attemptNo: Int,
    /** ISO8601 (타임존 표기 필수) */
    val occurredAt: String,
    val contentVersion: String,
)

@Serializable
data class RejectedLearningEvent(val eventId: String, val code: String)

@Serializable
data class LearningEventBatchResponse(
    val acceptedEventIds: List<String> = emptyList(),
    val duplicateEventIds: List<String> = emptyList(),
    val rejected: List<RejectedLearningEvent> = emptyList(),
    val serverCursor: Int = 0,
)

@Serializable
data class LearningSnapshot(
    val learnerId: String,
    val revision: Int = 0,
    val serverCursor: Int = 0,
    val updatedAt: String = "",
)

@Serializable
data class LearningStateResponse(
    val snapshots: List<LearningSnapshot> = emptyList(),
    val serverCursor: Int = 0,
)

/** 귀속 후보 — 서버가 이 계정에 딸린 학습자 목록을 준다 */
@Serializable
data class LearningClaimCandidate(
    val learnerId: String,
    val displayName: String = "",
    val updatedAt: String = "",
)

/**
 * 귀속 요청 결과.
 * - CREATE_LEARNER: 서버가 학습자를 새로 만들었다 (learnerId 있음)
 * - ATTACH_TO_EXISTING: 기존 학습자에 붙인다 (learnerId 가 없으면 후보 1명을 confirm 으로 확정)
 * - CONFLICT: 후보가 여럿이거나 다른 계정이 쓰던 기기 — 사용자가 골라야 한다
 */
@Serializable
enum class LearningClaimAction {
    @SerialName("create_learner") CREATE_LEARNER,
    @SerialName("attach_to_existing") ATTACH_TO_EXISTING,
    @SerialName("conflict") CONFLICT,
}

@Serializable
data class LearningClaimResponse(
    val action: LearningClaimAction,
    val candidates: List<LearningClaimCandidate> = emptyList(),
    val learnerId: String? = null,
)

@Serializable
data class LearningClaimConfirmResponse(val learnerId: String)

class LearningValidationException(val field: String) :
    IllegalArgumentException("학습 이벤트 $field 형식이 올바르지 않습니다")

object LearningContract {
    const val CONTENT_VERSION = "1.0.0"

    /** factId 계약 — 2~9단 × 1~9 */
    const val MIN_TABLE = 2
    const val MAX_TABLE = 9
    const val MIN_MULTIPLIER = 1
    const val MAX_MULTIPLIER = 9

    /** 서버가 받는 응답 시간 상한 */
    const val MAX_RESPONSE_MS = 600_000

    /** 서버 Field(ge=0, le=10_000) */
    const val MAX_SEQUENCE_NO = 10_000

    /** 서버 Field(ge=1, le=100) */
    const val MAX_ATTEMPT_NO = 100

    // 8-4-4-4-12, 버전 1~8, variant 8/9/a/b
    private val UUID_RE =
        Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)
    private val FACT_RE = Regex("^[2-9]x[1-9]$")
    private val CONTENT_VERSION_RE = Regex("^[A-Za-z0-9._-]{1,64}$")
    private val TZ_SUFFIX_RE = Regex("(?:Z|[+-]\\d{2}:\\d{2})$", RegexOption.IGNORE_CASE)

    fun isUuid(value: String): Boolean = UUID_RE.matches(value)

    fun isFactId(value: String): Boolean = FACT_RE.matches(value)

    fun factId(a: Int, b: Int): String = "${a}x${b}"

    /** factId 로 보낼 수 있는 범위인지 (범위 밖 문항은 이벤트에서 제외한다) */
    fun isInFactRange(a: Int, b: Int): Boolean =
        a in MIN_TABLE..MAX_TABLE && b in MIN_MULTIPLIER..MAX_MULTIPLIER

    fun isContentVersion(value: String): Boolean = CONTENT_VERSION_RE.matches(value)

    /** 타임존 표기가 있는 ISO8601 인지 (서버가 Z 또는 ±HH:MM 을 요구) */
    fun isIsoDate(value: String): Boolean {
        if (!TZ_SUFFIX_RE.containsMatchIn(value)) return false
        return runCatching { java.time.OffsetDateTime.parse(value) }.isSuccess
    }

    /** 지금 시각을 서버 계약 형식으로 */
    fun nowIso(): String = java.time.OffsetDateTime.now()
        .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    /** 서버가 거부할 이벤트를 미리 걸러낸다 — 통과하면 그대로 업로드 가능 */
    fun validate(event: LearningEvent) {
        if (!isUuid(event.eventId)) throw LearningValidationException("eventId")
        if (!isUuid(event.learnerId)) throw LearningValidationException("learnerId")
        if (!isUuid(event.deviceId)) throw LearningValidationException("deviceId")
        if (!isUuid(event.sessionId)) throw LearningValidationException("sessionId")
        if (event.sequenceNo !in 0..MAX_SEQUENCE_NO) throw LearningValidationException("sequenceNo")
        if (!isFactId(event.factId)) throw LearningValidationException("factId")
        event.tableNo?.let { if (it !in MIN_TABLE..MAX_TABLE) throw LearningValidationException("tableNo") }
        if (event.responseMs !in 0..MAX_RESPONSE_MS) throw LearningValidationException("responseMs")
        if (event.attemptNo !in 1..MAX_ATTEMPT_NO) throw LearningValidationException("attemptNo")
        if (!isIsoDate(event.occurredAt)) throw LearningValidationException("occurredAt")
        if (!isContentVersion(event.contentVersion)) throw LearningValidationException("contentVersion")
    }
}
