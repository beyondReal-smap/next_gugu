package site.smap.gugudan.store

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import site.smap.gugudan.services.AuthSession
import site.smap.gugudan.services.LearningApi
import site.smap.gugudan.services.Persistence
import site.smap.gugudan.services.SupabaseAuth
import site.smap.gugudan.services.SupabaseAuthException
import site.smap.gugudan.services.SupabaseConfig

// 계정 세션 관리 (iOS AuthStore 이식).
//
// 첫 실행에 조용히 익명 계정을 만들고 세션을 앱 전용 저장소에 보관한다. 가입 화면은 없고,
// 실패해도 앱은 게스트로 그대로 동작한다 (학습 기능은 계정과 무관).
// 보호자가 이메일을 넣으면 처음 쓰는 주소는 이 익명 계정을 승격하고(user id 유지),
// 이미 있는 주소는 그 계정으로 로그인한다 — 재설치·기기 변경 뒤에도 같은 계정으로 돌아온다.

class AuthStore(
    private val persistence: Persistence,
    private val scope: CoroutineScope,
) {
    sealed interface State {
        data object Idle : State          // 아직 시도 전
        data object Disabled : State      // 빌드에 Supabase 설정 없음
        data object Working : State       // 발급/갱신 중
        data object Ready : State         // 사용 가능한 세션 보유
        data class Failed(val message: String) : State
        /** 이메일 계정의 로그인이 만료됐다 — 익명 계정으로 바꾸지 않고 다시 로그인을 기다린다 */
        data class NeedsReauth(val email: String?) : State
    }

    var state: State by mutableStateOf(State.Idle)
        private set
    var session: AuthSession? by mutableStateOf(null)
        private set

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 갱신이 겹치지 않도록 진행 중인 작업을 공유한다 */
    private var inflight: Deferred<AuthSession>? = null
    /** 재로그인을 기다리는 계정 (만료된 이메일 계정) */
    private var reauth: ReauthMarker? = null

    val userId: String? get() = session?.userId
    val isAnonymous: Boolean get() = session?.isAnonymous ?: false

    /** 이메일이 붙은 영구 계정인지 */
    val isPermanent: Boolean get() = session != null && !isAnonymous

    /** 로그인돼 있는 이메일 (예전 저장본은 다음 갱신 전까지 null) */
    val email: String? get() = session?.email

    val needsReauth: Boolean get() = state is State.NeedsReauth

    @Serializable
    data class ReauthMarker(val userId: String, val email: String? = null)

    // MARK: 이메일 연결·로그인

    /** 코드를 받는 목적 — 처음 쓰는 주소는 연결(승격), 이미 있는 주소는 로그인 */
    enum class CodePurpose { LINK, SIGN_IN }

    sealed interface Promotion {
        data object None : Promotion
        data object Sending : Promotion
        /** 확인 코드를 보낸 주소 — 이 화면에서 코드를 입력받는다. 코드가 틀리면 error 를 띄우고 화면은 유지한다. */
        data class CodeSent(val email: String, val purpose: CodePurpose, val error: String? = null) : Promotion
        data class Verifying(val email: String, val purpose: CodePurpose) : Promotion
        data class Done(val email: String, val purpose: CodePurpose) : Promotion
        data class Failed(val message: String) : Promotion
    }

    var promotion: Promotion by mutableStateOf(Promotion.None)
        private set

    /** 마지막으로 코드를 보낸 시각 (epoch 밀리초) — 같은 주소 재발송은 60초 간격이 서버 제한이다 */
    var lastCodeSentAt: Long? by mutableStateOf(null)
        private set

    /**
     * 이메일로 확인 코드를 보낸다. 처음 쓰는 주소면 이 계정에 연결하고,
     * 이미 있는 계정의 주소면 그 계정으로 로그인하는 코드로 바꿔 보낸다.
     */
    suspend fun requestEmailCode(email: String) {
        val trimmed = email.trim().lowercase()
        if (!trimmed.contains("@") || trimmed.length < 5) {
            promotion = Promotion.Failed("이메일 주소를 확인해 주세요")
            return
        }
        promotion = Promotion.Sending
        try {
            // 재로그인 대기 중에는 붙일 익명 계정이 없다 — 바로 로그인 코드를 보낸다
            if (needsReauth) {
                SupabaseAuth.requestLoginCode(trimmed)
                codeSent(trimmed, CodePurpose.SIGN_IN)
                return
            }
            val token = accessToken()
            if (token == null) {
                promotion = Promotion.Failed("계정 세션이 없어 연결할 수 없습니다")
                return
            }
            try {
                SupabaseAuth.requestEmailPromotion(token, trimmed)
                codeSent(trimmed, CodePurpose.LINK)
            } catch (e: SupabaseAuthException) {
                if (!isExistingAccount(e)) throw e
                // 이미 쓰던 계정의 주소 — 연결 메일은 나가지 않았으니 로그인 코드를 보낸다
                SupabaseAuth.requestLoginCode(trimmed)
                codeSent(trimmed, CodePurpose.SIGN_IN)
            }
        } catch (e: SupabaseAuthException) {
            promotion = Promotion.Failed(promotionMessage(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            promotion = Promotion.Failed(e.message ?: e.toString())
        }
    }

    /** 같은 주소로 코드를 다시 보낸다 */
    suspend fun resendCode() {
        val sent = promotion as? Promotion.CodeSent ?: return
        try {
            when (sent.purpose) {
                CodePurpose.SIGN_IN -> SupabaseAuth.requestLoginCode(sent.email)
                CodePurpose.LINK -> {
                    val token = accessToken()
                    if (token == null) {
                        promotion = Promotion.CodeSent(sent.email, sent.purpose, "계정 세션이 없어 다시 보낼 수 없습니다")
                        return
                    }
                    SupabaseAuth.requestEmailPromotion(token, sent.email)
                }
            }
            codeSent(sent.email, sent.purpose)
        } catch (e: SupabaseAuthException) {
            promotion = Promotion.CodeSent(sent.email, sent.purpose, promotionMessage(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            promotion = Promotion.CodeSent(sent.email, sent.purpose, e.message ?: e.toString())
        }
    }

    /**
     * 메일로 받은 코드로 연결·로그인을 끝낸다.
     *
     * 다른 계정으로 바뀌는 경우(익명 → 기존 계정, 재로그인 중 다른 이메일) [onAccountSwitch] 가
     * 새 세션을 저장하기 **전에** 돈다 — 예전 계정에 묶인 귀속·큐·서버 부여 이용권을 먼저 치워야
     * 세션 변경에 반응하는 동기화가 예전 신원으로 새 계정에 요청하지 않는다.
     */
    suspend fun confirmEmailCode(code: String, onAccountSwitch: () -> Unit) {
        val sent = promotion as? Promotion.CodeSent ?: return
        val email = sent.email
        val purpose = sent.purpose
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            promotion = Promotion.CodeSent(email, purpose, "확인 코드를 입력해 주세요")
            return
        }
        promotion = Promotion.Verifying(email, purpose)
        try {
            val fresh = when (purpose) {
                CodePurpose.LINK -> SupabaseAuth.verifyEmailChange(email, trimmed)
                CodePurpose.SIGN_IN -> SupabaseAuth.verifyLoginCode(email, trimmed)
            }
            val previous = session
            val previousUserId = previous?.userId ?: reauth?.userId
            if (previousUserId != fresh.userId) {
                Log.i(TAG, "계정 전환 — 기기 귀속·큐 정리 후 새 세션 저장")
                onAccountSwitch()
            }
            adopt(fresh)
            promotion = Promotion.Done(email, purpose)
            // 버려지는 익명 세션은 서버에서도 폐기한다 (실패해도 토큰이 기기 밖으로 나가지 않으니 로그만)
            if (previous != null && previous.isAnonymous && previous.userId != fresh.userId) {
                revoke(previous)
            }
        } catch (e: SupabaseAuthException) {
            promotion = Promotion.CodeSent(email, purpose, promotionMessage(e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            promotion = Promotion.CodeSent(email, purpose, e.message ?: e.toString())
        }
    }

    fun resetPromotion() { promotion = Promotion.None }

    private fun codeSent(email: String, purpose: CodePurpose) {
        lastCodeSentAt = System.currentTimeMillis()
        promotion = Promotion.CodeSent(email, purpose)
    }

    /** 리프레시 거부 처리 */
    enum class RefreshRejection {
        /** 익명 계정 — 잃을 것이 없으니 새 익명 계정을 만든다 */
        REISSUE_ANONYMOUS,
        /** 이메일 계정 — 익명으로 바꾸면 이용권·기록 연결이 조용히 사라진다. 다시 로그인을 받는다. */
        REQUIRE_SIGN_IN,
    }

    /** 이메일 계정의 리프레시가 거부돼 재로그인을 기다려야 한다 */
    class ReauthRequiredException : Exception("다시 로그인이 필요합니다")

    companion object {
        private const val TAG = "AuthStore"

        /** 같은 주소 재발송 간격 — Supabase 기본 제한(60초)과 맞춘다 */
        const val RESEND_INTERVAL_MILLIS = 60_000L

        fun onRefreshRejected(isAnonymous: Boolean): RefreshRejection =
            if (isAnonymous) RefreshRejection.REISSUE_ANONYMOUS else RefreshRejection.REQUIRE_SIGN_IN

        /** 이 주소가 이미 다른 계정에 쓰이고 있다는 응답인지 — 로그인으로 넘어가는 신호 */
        fun isExistingAccount(e: SupabaseAuthException): Boolean =
            e.code == "email_exists" || e.code == "user_already_exists"

        /** 서버 오류 코드를 사용자 문구로 — 발송 제한이 가장 흔하다 */
        fun promotionMessage(e: SupabaseAuthException): String = when (e.code) {
            "over_email_send_rate_limit" -> "확인 메일을 방금 보냈어요. 잠시 뒤 다시 시도해 주세요."
            "over_request_rate_limit" -> "요청이 많아요. 잠시 뒤 다시 시도해 주세요."
            "email_address_invalid" -> "이 이메일 주소는 쓸 수 없어요. 다른 주소를 넣어 주세요."
            "email_exists", "user_already_exists" -> "이미 연결된 이메일이에요."
            // 코드를 틀려도 서버는 이 코드로 답한다
            "otp_expired" -> "확인 코드가 맞지 않거나 만료됐어요. 다시 확인해 주세요."
            // create_user=false 로그인에서 없는 주소일 때
            "otp_disabled" -> "이 이메일로 만든 계정이 없어요. 주소를 확인해 주세요."
            else -> e.message?.takeIf { it.isNotEmpty() } ?: "연결에 실패했어요"
        }
    }

    init {
        session = loadStored()
        reauth = loadReauth()
    }

    /**
     * 앱 시작 시 1회 호출. 저장된 세션이 있으면 필요할 때만 갱신하고, 없으면 익명 계정을 만든다.
     * 재로그인을 기다리는 중이면 아무것도 발급하지 않는다 — 거부는 확정된 결과라 다시 시도해도 소용없다.
     */
    suspend fun start() {
        if (!SupabaseConfig.isConfigured) {
            state = State.Disabled
            return
        }
        val pending = reauth
        if (session == null && pending != null) {
            state = State.NeedsReauth(pending.email)
            return
        }
        val current = session
        if (current != null && !current.isExpired) {
            state = State.Ready
            return
        }
        try {
            ensureSession()
        } catch (e: ReauthRequiredException) {
            // ensureSession 이 상태를 NeedsReauth 로 바꿨다
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 인증은 학습 기능의 전제가 아니다 — 기록만 남기고 앱은 그대로 진행한다
            state = State.Failed(e.message ?: e.toString())
            Log.w(TAG, "세션 준비 실패: ${e.message}")
        }
    }

    /**
     * 서버 호출에 붙일 액세스 토큰. 만료됐으면 갱신한 뒤 돌려준다.
     * 재로그인 대기 중이면 null — 익명 계정을 새로 만들어 서버 부여 이용권을 잃지 않게 한다.
     */
    suspend fun accessToken(): String? {
        if (!SupabaseConfig.isConfigured) return null
        if (session == null && reauth != null) return null
        val current = session
        if (current != null && !current.isExpired) return current.accessToken
        return runCatching { ensureSession().accessToken }.getOrNull()
    }

    /**
     * 토큰을 강제로 새로 받는다.
     * app_metadata(보호자 권한)는 JWT 에 박혀 나가므로, 서버가 값을 바꾼 뒤에는
     * 갱신하지 않으면 계속 예전 권한으로 거부된다.
     */
    suspend fun forceRefresh(): Boolean {
        if (!SupabaseConfig.isConfigured) return false
        val current = session ?: return false
        return try {
            val fresh = SupabaseAuth.refresh(current.refreshToken)
            adopt(fresh)
            true
        } catch (e: SupabaseAuthException) {
            if (e.needsFreshSignIn && onRefreshRejected(current.isAnonymous) == RefreshRejection.REQUIRE_SIGN_IN) {
                enterReauth(current)
            }
            Log.w(TAG, "토큰 강제 갱신 ${if (e.needsFreshSignIn) "거부" else "실패"}: ${e.message}")
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "토큰 강제 갱신 실패: ${e.message}")
            false
        }
    }

    /**
     * 계정과 서버에 보관된 학습 기록을 삭제한다 (App Store 5.1.1(v) / Play 데이터 삭제 요건).
     *
     * 성공하면 기기 세션을 버리고 새 익명 계정으로 다시 시작해, 사용자가 앱을 그대로
     * 계속 쓸 수 있게 한다.
     *
     * 기기에 남은 귀속·큐 정리는 SyncStore 가 맡는데, AuthStore 는 SyncStore 를 모르므로
     * 호출부가 [onLocalSessionCleared] 로 넘긴다. 이 콜백은 익명 재로그인(`start()`, 네트워크 대기)
     * **보다 먼저** 돈다 — 대기 중에 앱이 종료돼도 삭제된 계정에 묶인 신원·아웃박스가 기기에 남지 않게 한다.
     */
    suspend fun deleteAccount(onLocalSessionCleared: () -> Unit = {}): Boolean {
        val token = accessToken() ?: return false
        try {
            LearningApi.deleteAccount(token)
        } catch (e: Exception) {
            Log.w(TAG, "계정 삭제 실패: ${e.message}")
            return false
        }
        signOutLocally()
        promotion = Promotion.None
        Log.i(TAG, "계정 삭제: 서버 삭제 완료 — 기기 세션·기록 정리 후 익명 세션 재발급")
        onLocalSessionCleared()
        start()
        return true
    }

    /**
     * 이 기기에서 로그아웃한다 — 서버 계정과 기기 진행도는 남고, 새 익명 계정으로 다시 시작한다.
     * 오프라인이어도 끝나야 하므로 서버 세션 폐기 실패는 로그만 남긴다.
     * [onLocalSessionCleared] 는 deleteAccount 와 같은 이유로 익명 재발급보다 먼저 돈다.
     */
    suspend fun signOut(onLocalSessionCleared: () -> Unit) {
        val previous = session
        signOutLocally()
        promotion = Promotion.None
        Log.i(TAG, "로그아웃: 기기 세션 정리 후 익명 세션 재발급")
        onLocalSessionCleared()
        start()
        if (previous != null) revoke(previous)
    }

    /** 기기에서 계정 연결을 끊는다 (서버 계정은 남는다 — 삭제는 DELETE /api/account 담당) */
    fun signOutLocally() {
        inflight?.cancel()
        inflight = null
        session = null
        reauth = null
        persistence.remove(Persistence.AUTH_SESSION_KEY)
        persistence.remove(Persistence.AUTH_REAUTH_KEY)
        state = if (SupabaseConfig.isConfigured) State.Idle else State.Disabled
    }

    /** 유효한 세션을 보장한다. 동시에 여러 번 불려도 실제 호출은 한 번만 나간다. */
    private suspend fun ensureSession(): AuthSession {
        inflight?.let { return it.await() }

        val previous = session
        val task = scope.async {
            // 리프레시 토큰이 있으면 갱신을 먼저 시도하고, 거부당하면 계정 종류에 따라 처리한다
            if (previous != null) {
                try {
                    return@async SupabaseAuth.refresh(previous.refreshToken)
                } catch (e: SupabaseAuthException) {
                    if (!e.needsFreshSignIn) throw e
                    when (onRefreshRejected(previous.isAnonymous)) {
                        RefreshRejection.REQUIRE_SIGN_IN -> {
                            Log.i(TAG, "리프레시 거부 — 이메일 계정이라 재로그인을 기다린다: ${e.message}")
                            throw ReauthRequiredException()
                        }
                        RefreshRejection.REISSUE_ANONYMOUS ->
                            Log.i(TAG, "리프레시 거부 — 익명 계정을 새로 만든다: ${e.message}")
                    }
                }
            }
            SupabaseAuth.signInAnonymously()
        }
        inflight = task
        state = State.Working
        try {
            val fresh = task.await()
            adopt(fresh)
            return fresh
        } catch (e: ReauthRequiredException) {
            session?.let { enterReauth(it) }
            throw e
        } catch (e: Exception) {
            state = State.Failed(e.message ?: e.toString())
            throw e
        } finally {
            inflight = null
        }
    }

    /** 새 세션을 쓰기 시작한다 — 재로그인 표시는 지운다 */
    private fun adopt(fresh: AuthSession) {
        session = fresh
        store(fresh)
        if (reauth != null) {
            reauth = null
            persistence.remove(Persistence.AUTH_REAUTH_KEY)
        }
        state = State.Ready
    }

    /**
     * 죽은 이메일 세션을 버리고 재로그인을 기다린다. 기기 귀속·큐는 그대로 둔다 —
     * 같은 계정으로 다시 로그인하면 쌓인 기록이 이어서 올라간다.
     */
    private fun enterReauth(expired: AuthSession) {
        val marker = ReauthMarker(expired.userId, expired.email)
        persistence.putString(
            Persistence.AUTH_REAUTH_KEY,
            json.encodeToString(ReauthMarker.serializer(), marker),
        )
        reauth = marker
        inflight?.cancel()
        inflight = null
        session = null
        persistence.remove(Persistence.AUTH_SESSION_KEY)
        state = State.NeedsReauth(marker.email)
    }

    private suspend fun revoke(old: AuthSession) {
        try {
            SupabaseAuth.signOut(old.accessToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "서버 세션 폐기 실패(무시): ${e.message}")
        }
    }

    private fun store(value: AuthSession) {
        persistence.putString(
            Persistence.AUTH_SESSION_KEY,
            json.encodeToString(AuthSession.serializer(), value),
        )
    }

    private fun loadStored(): AuthSession? {
        val raw = persistence.getString(Persistence.AUTH_SESSION_KEY) ?: return null
        return runCatching { json.decodeFromString(AuthSession.serializer(), raw) }
            .onFailure {
                Log.w(TAG, "저장된 세션 해석 실패 — 새로 발급한다")
                // 원인 예외는 넘기지 않는다 — 예외 메시지에 입력 JSON 조각(토큰)이 담겨 로그로 샌다
                persistence.quarantine(Persistence.AUTH_SESSION_KEY, raw)
            }
            .getOrNull()
    }

    private fun loadReauth(): ReauthMarker? {
        val raw = persistence.getString(Persistence.AUTH_REAUTH_KEY) ?: return null
        return runCatching { json.decodeFromString(ReauthMarker.serializer(), raw) }
            .onFailure {
                Log.w(TAG, "재로그인 표시 해석 실패 — 표시 없이 시작한다")
                // 원인 예외는 넘기지 않는다 — 예외 메시지에 입력 JSON 조각(이메일)이 담겨 로그로 샌다
                persistence.quarantine(Persistence.AUTH_REAUTH_KEY, raw)
            }
            .getOrNull()
    }
}
