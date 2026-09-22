package site.smap.gugudan.store

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.serialization.json.Json
import site.smap.gugudan.services.AuthSession
import site.smap.gugudan.services.LearningApi
import site.smap.gugudan.services.Persistence
import site.smap.gugudan.services.SupabaseAuth
import site.smap.gugudan.services.SupabaseAuthException
import site.smap.gugudan.services.SupabaseConfig

// 익명 계정 세션 관리 (iOS AuthStore 이식, 1단계 — 익명만).
//
// 첫 실행에 조용히 익명 계정을 만들고 세션을 앱 전용 저장소에 보관한다. 화면이나 가입 절차가
// 없고, 실패해도 앱은 게스트로 그대로 동작한다 (학습 기능은 계정과 무관).
// 나중 단계에서 이 계정을 이메일로 승격하면 user id 가 유지돼 기록이 이어진다.

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
    }

    var state: State by mutableStateOf(State.Idle)
        private set
    var session: AuthSession? by mutableStateOf(null)
        private set

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 갱신이 겹치지 않도록 진행 중인 작업을 공유한다 */
    private var inflight: Deferred<AuthSession>? = null

    val userId: String? get() = session?.userId
    val isAnonymous: Boolean get() = session?.isAnonymous ?: false

    /** 이메일이 붙은 영구 계정인지 */
    val isPermanent: Boolean get() = session != null && !isAnonymous

    // MARK: 이메일 승격 (익명 -> 영구 계정)

    sealed interface Promotion {
        data object None : Promotion
        data object Sending : Promotion
        /** 확인 코드를 보낸 주소 — 이 화면에서 코드를 입력받는다 */
        data class CodeSent(val email: String) : Promotion
        data class Verifying(val email: String) : Promotion
        data class Done(val email: String) : Promotion
        data class Failed(val message: String) : Promotion
    }

    var promotion: Promotion by mutableStateOf(Promotion.None)
        private set

    /** 이메일로 확인 코드를 보낸다. 성공해도 아직 승격은 끝나지 않는다. */
    suspend fun startEmailPromotion(email: String) {
        val trimmed = email.trim().lowercase()
        if (!trimmed.contains("@") || trimmed.length < 5) {
            promotion = Promotion.Failed("이메일 주소를 확인해 주세요")
            return
        }
        val token = accessToken()
        if (token == null) {
            promotion = Promotion.Failed("계정 세션이 없어 연결할 수 없습니다")
            return
        }
        promotion = Promotion.Sending
        promotion = try {
            SupabaseAuth.requestEmailPromotion(token, trimmed)
            Promotion.CodeSent(trimmed)
        } catch (e: SupabaseAuthException) {
            Promotion.Failed(promotionMessage(e))
        } catch (e: Exception) {
            Promotion.Failed(e.message ?: e.toString())
        }
    }

    /** 메일로 받은 코드로 승격을 끝낸다 */
    suspend fun confirmEmailPromotion(code: String) {
        val email = (promotion as? Promotion.CodeSent)?.email ?: return
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            promotion = Promotion.Failed("확인 코드를 입력해 주세요")
            return
        }
        promotion = Promotion.Verifying(email)
        promotion = try {
            val fresh = SupabaseAuth.verifyEmailChange(email, trimmed)
            session = fresh
            store(fresh)
            state = State.Ready
            Promotion.Done(email)
        } catch (e: SupabaseAuthException) {
            Promotion.Failed(promotionMessage(e))
        } catch (e: Exception) {
            Promotion.Failed(e.message ?: e.toString())
        }
    }

    fun resetPromotion() { promotion = Promotion.None }

    /** 서버 오류 코드를 사용자 문구로 — 발송 제한이 가장 흔하다 */
    private fun promotionMessage(e: SupabaseAuthException): String = when (e.code) {
        "over_email_send_rate_limit" -> "확인 메일을 방금 보냈어요. 잠시 뒤 다시 시도해 주세요."
        "email_address_invalid" -> "이 이메일 주소는 쓸 수 없어요. 다른 주소를 넣어 주세요."
        "email_exists", "user_already_exists" -> "이미 연결된 이메일이에요."
        "otp_expired" -> "확인 코드가 만료됐어요. 다시 보내 주세요."
        else -> e.message ?: "연결에 실패했어요"
    }

    companion object {
        private const val TAG = "AuthStore"
    }

    init {
        session = loadStored()
    }

    /** 앱 시작 시 1회 호출. 저장된 세션이 있으면 필요할 때만 갱신하고, 없으면 익명 계정을 만든다. */
    suspend fun start() {
        if (!SupabaseConfig.isConfigured) {
            state = State.Disabled
            return
        }
        val current = session
        if (current != null && !current.isExpired) {
            state = State.Ready
            return
        }
        try {
            ensureSession()
        } catch (e: Exception) {
            // 인증은 학습 기능의 전제가 아니다 — 기록만 남기고 앱은 그대로 진행한다
            state = State.Failed(e.message ?: e.toString())
            Log.w(TAG, "익명 세션 준비 실패: ${e.message}")
        }
    }

    /** 서버 호출에 붙일 액세스 토큰. 만료됐으면 갱신한 뒤 돌려준다. */
    suspend fun accessToken(): String? {
        if (!SupabaseConfig.isConfigured) return null
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
            session = fresh
            store(fresh)
            state = State.Ready
            true
        } catch (e: Exception) {
            Log.w(TAG, "토큰 강제 갱신 실패: ${e.message}")
            false
        }
    }

    /**
     * 계정과 서버에 보관된 학습 기록을 삭제한다 (App Store 5.1.1(v) / Play 데이터 삭제 요건).
     *
     * 성공하면 기기 세션을 버리고 새 익명 계정으로 다시 시작해, 사용자가 앱을 그대로
     * 계속 쓸 수 있게 한다. 기기에 남은 귀속·큐 정리는 SyncStore 가 맡는다.
     */
    suspend fun deleteAccount(): Boolean {
        val token = accessToken() ?: return false
        try {
            LearningApi.deleteAccount(token)
        } catch (e: Exception) {
            Log.w(TAG, "계정 삭제 실패: ${e.message}")
            return false
        }
        signOutLocally()
        promotion = Promotion.None
        start()
        return true
    }

    /** 기기에서 계정 연결을 끊는다 (서버 계정은 남는다 — 삭제는 DELETE /api/account 담당) */
    fun signOutLocally() {
        inflight?.cancel()
        inflight = null
        session = null
        persistence.remove(Persistence.AUTH_SESSION_KEY)
        state = if (SupabaseConfig.isConfigured) State.Idle else State.Disabled
    }

    /** 유효한 세션을 보장한다. 동시에 여러 번 불려도 실제 호출은 한 번만 나간다. */
    private suspend fun ensureSession(): AuthSession {
        inflight?.let { return it.await() }

        val previous = session
        val task = scope.async {
            // 리프레시 토큰이 있으면 갱신을 먼저 시도하고, 거부당하면 새 익명 계정으로 넘어간다
            if (previous != null) {
                try {
                    return@async SupabaseAuth.refresh(previous.refreshToken)
                } catch (e: SupabaseAuthException) {
                    if (!e.needsFreshSignIn) throw e
                    Log.i(TAG, "리프레시 거부 — 익명 계정을 새로 만든다: ${e.message}")
                }
            }
            SupabaseAuth.signInAnonymously()
        }
        inflight = task
        state = State.Working
        try {
            val fresh = task.await()
            session = fresh
            store(fresh)
            state = State.Ready
            return fresh
        } catch (e: Exception) {
            state = State.Failed(e.message ?: e.toString())
            throw e
        } finally {
            inflight = null
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
            .onFailure { Log.w(TAG, "저장된 세션 해석 실패 — 새로 발급한다") }
            .getOrNull()
    }
}
