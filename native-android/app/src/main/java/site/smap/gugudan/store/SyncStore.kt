package site.smap.gugudan.store

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import site.smap.gugudan.core.SessionResult
import site.smap.gugudan.core.learning.LearningOutbox
import site.smap.gugudan.core.learning.LearningRecord
import site.smap.gugudan.services.LearningApi
import site.smap.gugudan.services.LearningApiException
import site.smap.gugudan.services.LearningIdentityStore
import site.smap.gugudan.services.LearningOutboxStore
import java.util.UUID

// 학습 기록 동기화 (iOS SyncStore 이식).
//
// 서버는 보호자 검증(app_metadata.account_role=guardian, age_verified=true)이 끝난 계정만
// 업로드를 허용한다. 검증 전에는 403 이 오므로, 한 번 거부되면 업로드를 아예 시도하지 않고
// 큐에 쌓아 둔다. 검증이 켜지면 그때 한꺼번에 올라간다.

class SyncStore(
    private val identityStore: LearningIdentityStore,
    private val outboxStore: LearningOutboxStore,
    private val scope: CoroutineScope,
) {
    sealed interface State {
        data object Idle : State
        data object Uploading : State
        /** 보호자 검증 전 — 큐에 쌓아 두고 기다린다 */
        data class WaitingForGuardian(val message: String) : State
        data class Failed(val message: String) : State
    }

    var state: State by mutableStateOf(State.Idle)
        private set
    var pendingCount: Int by mutableStateOf(0)
        private set
    var lastServerCursor: Int by mutableStateOf(0)
        private set

    private var flushJob: Job? = null

    /** 권한 거부를 받은 뒤에는 세션이 바뀔 때까지 업로드를 멈춘다 */
    private var blockedUserId: String? = null

    companion object {
        private const val TAG = "Sync"
    }

    init {
        val stored = outboxStore.read()
        pendingCount = stored.events.size
        lastServerCursor = stored.serverCursor
    }

    /** 세션 결과를 큐에 적재한다. 네트워크와 무관하게 항상 성공해야 한다. */
    fun record(result: SessionResult) {
        val identity = identityStore.read()
        // learnerId 는 서버가 배정한다 — 귀속 전에는 올릴 수 없으므로 적재도 하지 않는다
        val learnerId = identity.learnerId ?: return

        val ids = LearningRecord.Ids(
            learnerId = learnerId,
            deviceId = identity.installId,
            sessionId = UUID.randomUUID().toString().lowercase(),
        )
        var outbox = outboxStore.read()
        for (pending in LearningRecord.events(result, ids)) {
            val event = pending.withEventId(UUID.randomUUID().toString().lowercase())
            outbox = runCatching { LearningOutbox.enqueue(outbox, event) }
                // 한 문항이 계약을 벗어나도 나머지는 적재한다
                .onFailure { Log.w(TAG, "이벤트 적재 실패: ${it.message}") }
                .getOrDefault(outbox)
        }
        outboxStore.write(outbox)
        pendingCount = outbox.events.size
    }

    /** 큐를 업로드한다. 보호자 검증 전이면 아무 요청도 보내지 않는다. */
    fun flush(auth: AuthStore) {
        if (flushJob?.isActive == true) return
        // 같은 계정으로 이미 권한 거부를 받았다면 서버를 다시 부르지 않는다
        val current = state
        if (current is State.WaitingForGuardian && blockedUserId == auth.userId) return
        flushJob = scope.launch { runFlush(auth) }
    }

    private suspend fun runFlush(auth: AuthStore) {
        var outbox = outboxStore.read()
        if (outbox.events.isEmpty()) {
            state = State.Idle
            return
        }
        val token = auth.accessToken()
        if (token == null) {
            state = State.Failed("계정 세션이 없어 업로드를 건너뜁니다")
            return
        }

        state = State.Uploading
        val batch = LearningOutbox.nextBatch(outbox)
        try {
            val response = LearningApi.uploadEvents(token, batch)
            // 업로드 중 적재된 이벤트를 잃지 않도록 최신 상태로 반영한다
            outbox = LearningOutbox.applyResponse(outboxStore.read(), batch, response)
            outboxStore.write(outbox)
            pendingCount = outbox.events.size
            lastServerCursor = outbox.serverCursor
            blockedUserId = null
            state = State.Idle
            if (response.rejected.isNotEmpty()) {
                Log.w(TAG, "서버 거부 ${response.rejected.size}건: ${response.rejected.map { it.code }}")
            }
        } catch (e: LearningApiException.PermissionDenied) {
            // 검증 전 — 큐를 그대로 두고 더 시도하지 않는다
            blockedUserId = auth.userId
            state = State.WaitingForGuardian(e.denial.message)
        } catch (e: Exception) {
            state = State.Failed(e.message ?: e.toString())
            Log.w(TAG, "업로드 실패: ${e.message}")
        }
    }

    /** 보호자 검증이 켜졌을 수 있는 시점(로그인/승격 직후)에 차단을 푼다 */
    fun clearGuardianBlock() {
        blockedUserId = null
        if (state is State.WaitingForGuardian) state = State.Idle
    }
}
