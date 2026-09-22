package site.smap.gugudan.services

import android.util.Log
import kotlinx.serialization.json.Json
import site.smap.gugudan.core.learning.LearningContract
import site.smap.gugudan.core.learning.LearningIdentity
import site.smap.gugudan.core.learning.LearningOutboxState
import site.smap.gugudan.core.learning.LearningEvent
import site.smap.gugudan.core.learning.LearningValidationException
import java.util.UUID

// 학습 신원 / 아웃박스 영속화.
// core/learning 은 순수 로직만 두고, 저장(SharedPreferences)은 이 계층이 맡는다
// — 의존성은 services -> core 한 방향으로만 흐른다.

class LearningIdentityStore(private val persistence: Persistence) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val TAG = "LearningIdentity"
    }

    /**
     * 저장본을 읽고, 없거나 깨졌으면 새로 발급해 저장한다.
     * installId 가 UUID 형식이 아니면 재발급한다(서버가 형식을 검증하므로).
     */
    fun read(): LearningIdentity {
        val raw = persistence.getString(Persistence.LEARNING_IDENTITY_KEY) ?: return createAndStore()
        val stored = runCatching { json.decodeFromString(LearningIdentity.serializer(), raw) }
            .onFailure { Log.w(TAG, "저장본 해석 실패 — 재발급") }
            .getOrNull() ?: return createAndStore()

        if (!LearningContract.isUuid(stored.installId)) {
            Log.w(TAG, "installId 형식 오류 — 재발급")
            return createAndStore()
        }
        // learnerId 는 서버가 준 UUID 여야 한다. 형식이 깨졌으면 귀속 전으로 되돌린다.
        if (stored.learnerId != null && !LearningContract.isUuid(stored.learnerId)) {
            Log.w(TAG, "learnerId 형식 오류 — 귀속 해제")
            val fixed = stored.copy(version = 1, learnerId = null, claimedUserId = null)
            write(fixed)
            return fixed
        }
        return stored.copy(version = 1)
    }

    /** 계정 삭제 후 기기의 귀속 정보를 버린다 — 다음 읽기에서 새 installId 가 발급된다 */
    fun clear() = persistence.remove(Persistence.LEARNING_IDENTITY_KEY)

    fun write(identity: LearningIdentity) {
        persistence.putString(
            Persistence.LEARNING_IDENTITY_KEY,
            json.encodeToString(LearningIdentity.serializer(), identity),
        )
    }

    /** 서버가 배정한 학습자를 계정에 묶는다 */
    fun bindLearner(userId: String, learnerId: String): LearningIdentity {
        if (!LearningContract.isUuid(learnerId)) throw LearningValidationException("learnerId")
        val next = read().copy(learnerId = learnerId, claimedUserId = userId)
        write(next)
        return next
    }

    private fun createAndStore(): LearningIdentity {
        val identity = LearningIdentity.fresh(UUID.randomUUID().toString().lowercase())
        write(identity)
        return identity
    }
}

class LearningOutboxStore(private val persistence: Persistence) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val TAG = "LearningOutbox"
    }

    fun read(): LearningOutboxState {
        val raw = persistence.getString(Persistence.LEARNING_OUTBOX_KEY) ?: return LearningOutboxState()
        val stored = runCatching { json.decodeFromString(LearningOutboxState.serializer(), raw) }
            .onFailure { Log.w(TAG, "저장본 해석 실패 — 큐를 비운다") }
            .getOrNull() ?: return LearningOutboxState()

        // 저장본이 계약을 벗어나면 그 이벤트만 버린다 — 큐 전체를 잃지 않게
        val events = stored.events.filter { event ->
            runCatching { LearningContract.validate(event) }
                .onFailure { Log.w(TAG, "저장본에서 잘못된 이벤트 제외: ${it.message}") }
                .isSuccess
        }
        return stored.copy(version = 1, events = events, serverCursor = maxOf(0, stored.serverCursor))
    }

    /** 계정 삭제 후 올릴 곳이 없어진 큐를 버린다 */
    fun clear() = persistence.remove(Persistence.LEARNING_OUTBOX_KEY)

    fun write(state: LearningOutboxState) {
        persistence.putString(
            Persistence.LEARNING_OUTBOX_KEY,
            json.encodeToString(LearningOutboxState.serializer(), state),
        )
    }
}
