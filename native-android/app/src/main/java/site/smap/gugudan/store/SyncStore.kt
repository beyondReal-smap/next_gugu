package site.smap.gugudan.store

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import site.smap.gugudan.core.SessionResult
import site.smap.gugudan.core.learning.LearningClaimAction
import site.smap.gugudan.core.learning.LearningClaimCandidate
import site.smap.gugudan.core.learning.LearningFactStats
import site.smap.gugudan.core.learning.LearningOutbox
import site.smap.gugudan.core.learning.LearningRecord
import site.smap.gugudan.services.LearningApi
import site.smap.gugudan.services.GuardianDenial
import site.smap.gugudan.services.LearningApiException
import site.smap.gugudan.services.LearningIdentityStore
import site.smap.gugudan.services.LearningOutboxStore
import site.smap.gugudan.services.PurchaseRegistry
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
        /** 보호자 검증 전이라 보관이 꺼져 있다 — 안내 문구는 리소스 ID (화면에서 읽는다) */
        data class WaitingForGuardian(@StringRes val messageRes: Int) : State
        /** 귀속 후보가 여럿 — 사용자가 골라야 한다 */
        data class NeedsLearnerChoice(val candidates: List<LearningClaimCandidate>) : State
        data class Failed(val message: String) : State
    }

    var state: State by mutableStateOf(State.Idle)
        private set
    var pendingCount: Int by mutableStateOf(0)
        private set
    var lastServerCursor: Int by mutableStateOf(0)
        private set

    private var flushJob: Job? = null
    /** 보호자 권한·귀속 흐름은 한 번에 하나만 — 겹치면 서버가 학습자를 둘 만들어 이후 계속 conflict 가 난다 */
    private var guardianJob: Job? = null

    /** 권한 거부를 받은 뒤에는 세션이 바뀔 때까지 업로드를 멈춘다 */
    private var blockedUserId: String? = null

    /** 서버 기록을 받아왔을 때 기기 진행도에 반영하는 곳 (MainActivity 가 GameStore 로 연결한다) */
    var onServerRestore: ((Map<String, LearningFactStats>) -> Unit)? = null
    /** 서버 기록 복원에 실패했는지 — 업로드 상태(state)와 따로 둬야 뒤이은 flush 가 덮어쓰지 않는다 */
    var restoreFailed: Boolean by mutableStateOf(false)
        private set

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
        // learnerId 는 서버가 배정한다 — 귀속 전에는 올릴 수 없으므로 적재도 하지 않는다.
        // 조용히 return 하면 "왜 아무 것도 안 올라가는지" 추적할 단서가 없다 — 반드시 남긴다.
        val learnerId = identity.learnerId
        if (learnerId == null) {
            Log.i(TAG, "적재 건너뜀: 학습자 귀속 전 (문항 ${result.answers.size}개 버림)")
            return
        }
        Log.i(TAG, "세션 적재 learner=${learnerId.take(8)} 문항=${result.answers.size}개")

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
        // 재로그인 전에는 올릴 계정이 없다 — 큐를 그대로 두고 같은 계정으로 돌아오면 올린다
        if (auth.needsReauth) {
            Log.i(TAG, "업로드 보류: 재로그인 대기 (대기 ${pendingCount}건)")
            return
        }
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
            state = State.Failed("계정 세션이 없어 업로드를 건너뜁니다")  // i18n:skip
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
            state = State.WaitingForGuardian(e.denial.messageRes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state = State.Failed(e.message ?: e.toString())
            Log.w(TAG, "업로드 실패: ${e.message}")
        }
    }

    /** 업로드를 한 번 끝까지 기다린다 (로그아웃 전에 남은 기록을 올려 보기 위해) */
    suspend fun flushNow(auth: AuthStore) {
        flushJob?.join()
        flush(auth)
        flushJob?.join()
    }

    /**
     * 계정이 바뀔 때(삭제·로그아웃·다른 계정 로그인) 기기에 남은 귀속과 큐를 버린다.
     * learnerId·installId 는 예전 계정 소유라 새 계정으로 쓰면 서버가 conflict/소유 불일치로 거부하고,
     * 올리지 못한 이벤트도 예전 학습자 것이다.
     */
    fun resetForAccountChange() {
        flushJob?.cancel()
        flushJob = null
        guardianJob?.cancel()
        guardianJob = null
        identityStore.clear()
        outboxStore.clear()
        pendingCount = 0
        lastServerCursor = 0
        blockedUserId = null
        restoreFailed = false
        state = State.Idle
    }

    /** 보호자 검증이 켜졌을 수 있는 시점(로그인/승격 직후)에 차단을 푼다 */
    fun clearGuardianBlock() {
        blockedUserId = null
        if (state is State.WaitingForGuardian) state = State.Idle
    }

    /**
     * 이 기기 기록을 어느 학습자에 붙일지 정한다. learnerId 가 이미 있으면 아무 것도 하지 않는다.
     *
     * 서버는 세 가지로 답한다.
     * - CREATE_LEARNER: 학습자를 새로 만들어 줬다 — 그대로 저장
     * - ATTACH_TO_EXISTING: 기존 학습자에 붙인다. learnerId 가 없으면 후보 1명을 confirm 으로 확정
     * - CONFLICT: 후보가 여럿이거나 다른 계정이 쓰던 기기 — 사용자가 골라야 한다
     */
    suspend fun ensureLearner(auth: AuthStore) {
        val identity = identityStore.read()
        if (identity.learnerId != null) return
        if (!auth.isPermanent) return
        val userId = auth.userId ?: return
        val token = auth.accessToken() ?: return

        val pending = outboxStore.read().events.size
        try {
            val response = LearningApi.claim(token, identity.installId, pending)
            when (response.action) {
                LearningClaimAction.CREATE_LEARNER -> {
                    val learnerId = response.learnerId
                    if (learnerId == null) {
                        state = State.Failed("서버가 학습자 아이디를 주지 않았습니다")  // i18n:skip
                        return
                    }
                    bind(learnerId, userId, auth)
                }
                LearningClaimAction.ATTACH_TO_EXISTING -> {
                    val learnerId = response.learnerId
                    when {
                        learnerId != null -> bind(learnerId, userId, auth)
                        response.candidates.size == 1 ->
                            confirm(auth, userId, token, identity.installId, response.candidates[0].learnerId)
                        // 붙일 대상을 특정할 수 없다 — 고르게 한다
                        else -> state = State.NeedsLearnerChoice(response.candidates)
                    }
                }
                LearningClaimAction.CONFLICT -> state = State.NeedsLearnerChoice(response.candidates)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "귀속 실패: ${e.message}")
            state = State.Failed(e.message ?: e.toString())
        }
    }

    /** 후보 중 하나를 골라 귀속을 확정한다 (conflict 화면에서 호출) */
    suspend fun chooseLearner(candidate: LearningClaimCandidate, auth: AuthStore) {
        val userId = auth.userId ?: return
        val token = auth.accessToken() ?: return
        try {
            confirm(auth, userId, token, identityStore.read().installId, candidate.learnerId)
            restoreIfNeeded(auth)
            flush(auth)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "귀속 확정 실패: ${e.message}")
            state = State.Failed(e.message ?: e.toString())
        }
    }

    private suspend fun confirm(auth: AuthStore, userId: String, token: String, installId: String, learnerId: String) {
        val confirmed = LearningApi.confirmClaim(token, installId, learnerId)
        bind(confirmed.learnerId, userId, auth)
    }

    /**
     * 요청을 보낸 계정 그대로일 때만 묶는다 — 기다리는 사이 다른 계정으로 바뀌었으면
     * 예전 계정의 학습자를 새 계정에 붙이게 된다.
     */
    private fun bind(learnerId: String, userId: String, auth: AuthStore) {
        if (auth.userId != userId) {
            Log.w(TAG, "귀속 응답 폐기: 요청 중 계정이 바뀜")
            return
        }
        identityStore.bindLearner(userId, learnerId)
        state = State.Idle
    }

    /**
     * 새로 귀속한 학습자의 서버 기록(문제별 통계)을 한 번 받아와 기기에 반영한다.
     * 실패하면 표시를 남겨 다음 기회(앱 시작·구매 복원)에 다시 시도한다.
     */
    suspend fun restoreIfNeeded(auth: AuthStore) {
        val identity = identityStore.read()
        if (!identity.restorePending) return
        val learnerId = identity.learnerId ?: return
        val userId = auth.userId ?: return
        if (identity.claimedUserId != userId) return
        val token = auth.accessToken() ?: return
        try {
            val response = LearningApi.fetchState(token, learnerId)
            if (auth.userId != userId) return
            // 스냅샷이 없으면 아직 서버에 올린 기록이 없는 학습자다 — 복원할 것이 없는 정상 상태
            val facts = response.snapshots.firstOrNull { it.learnerId == learnerId }?.state?.facts ?: emptyMap()
            onServerRestore?.invoke(facts)
            identityStore.markRestored()
            restoreFailed = false
            Log.i(TAG, "서버 기록 복원: 문제 ${facts.size}개")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "서버 기록 복원 실패: ${e.message}")
            restoreFailed = true
        }
    }

    /**
     * 구매 이력을 근거로 보호자 권한을 켜고 업로드를 재개한다.
     *
     * 순서가 중요하다 — 서버가 app_metadata 를 바꿔도 손에 든 JWT 는 예전 권한이므로,
     * 토큰을 새로 받은 뒤에야 업로드가 통과한다.
     */
    suspend fun enableGuardianSync(auth: AuthStore, purchaseToken: String? = null) {
        // 시작·구매·계정 변경 트리거가 겹쳐도 한 번만 돈다
        guardianJob?.let { running ->
            if (running.isActive) {
                running.join()
                return
            }
        }
        val job = scope.launch { runGuardianSync(auth, purchaseToken) }
        guardianJob = job
        job.join()
        if (guardianJob === job) guardianJob = null
    }

    private suspend fun runGuardianSync(auth: AuthStore, purchaseToken: String?) {
        // 이메일이 붙지 않은 익명 계정은 서버가 거부한다 — 요청하지 않는다
        if (!auth.isPermanent) return
        val token = auth.accessToken() ?: return

        // 구매를 먼저 계정에 연결한다. 이게 없으면 서버가 구매 내역을 못 찾아 거부한다.
        if (purchaseToken != null) {
            try {
                val registered = PurchaseRegistry.register(token, purchaseToken)
                Log.i(TAG, "구매 등록 완료 premium=${registered.premium}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 이미 등록됐거나 토큰이 만료됐을 수 있다 — guardian 요청은 그대로 시도한다
                Log.w(TAG, "구매 등록 건너뜀: ${e.message}")
            }
        }

        try {
            val response = LearningApi.verifyGuardian(token)
            if (!response.guardian) return
            // 새 권한이 담긴 토큰을 받아야 업로드가 통과한다
            auth.forceRefresh()
            clearGuardianBlock()
            // 학습자 귀속이 끝나야 이벤트를 만들 수 있다
            ensureLearner(auth)
            restoreIfNeeded(auth)
            flush(auth)
        } catch (e: LearningApiException.Http) {
            val denial = if (e.status == 403) GuardianDenial.of(e.code) else null
            if (denial != null) {
                // 구매 없음/익명 — 정상적인 거부다. 사용자에게 사유를 보여 준다.
                state = State.WaitingForGuardian(denial.messageRes)
                blockedUserId = auth.userId
            } else {
                Log.w(TAG, "보호자 권한 설정 실패: ${e.message}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "보호자 권한 설정 실패: ${e.message}")
        }
    }
}
