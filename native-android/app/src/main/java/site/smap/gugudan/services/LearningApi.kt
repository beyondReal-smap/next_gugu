package site.smap.gugudan.services

import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import site.smap.gugudan.BuildConfig
import site.smap.gugudan.R
import site.smap.gugudan.core.learning.LearningEvent
import site.smap.gugudan.core.learning.LearningClaimConfirmResponse
import site.smap.gugudan.core.learning.LearningClaimResponse
import site.smap.gugudan.core.learning.LearningEventBatchResponse
import site.smap.gugudan.core.learning.LearningStateResponse
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// 학습 원장 API 클라이언트 (iOS LearningApi 이식).
//
// 주의: gugu.smap.site 는 Cloudflare 뒤에 있고 기본 User-Agent 로 호출하면
// 봇 차단(HTTP 403, error 1010)에 걸린다. 앱을 식별하는 UA 를 반드시 붙인다.

@kotlinx.serialization.Serializable
data class GuardianVerifyResponse(
    val guardian: Boolean = false,
    val products: List<String> = emptyList(),
    val verifiedBy: String = "",
    val verifiedAt: String = "",
)

/** GET /api/premium/status — 서버 구매 원장(purchases) 기준 이 계정의 프리미엄 여부 */
@kotlinx.serialization.Serializable
data class PremiumStatusResponse(
    val premium: Boolean = false,
    val products: List<String> = emptyList(),
)

/** 보호자 권한 설정이 거부되는 사유 — 안내 문구는 리소스 ID (화면에서 읽는다) */
enum class GuardianDenial(val code: String, @StringRes val messageRes: Int) {
    ANONYMOUS("ANONYMOUS_NOT_ELIGIBLE", R.string.link_an_email_first_to_turn_on_record),
    NO_PURCHASE("NO_PURCHASE_FOUND", R.string.record_backup_is_available_on_the);

    companion object {
        fun of(code: String?): GuardianDenial? = entries.firstOrNull { it.code == code }
    }
}

/** 서버가 업로드를 거부하는 사유 — 보호자 검증이 끝나지 않은 계정. 안내 문구는 리소스 ID (화면에서 읽는다) */
enum class PermissionDenial(val code: String, @StringRes val messageRes: Int) {
    CHILD_ACCOUNT("CHILD_ACCOUNT_UPLOAD_FORBIDDEN", R.string.learning_records_from_a_child_s_account),
    ROLE_UNVERIFIED("ACCOUNT_ROLE_UNVERIFIED", R.string.once_this_is_confirmed_as_a_parent),
    AGE_UNVERIFIED("ACCOUNT_AGE_UNVERIFIED", R.string.record_backup_turns_on_once_the_parent_s);

    companion object {
        fun of(code: String?): PermissionDenial? = entries.firstOrNull { it.code == code }
    }
}

sealed class LearningApiException(message: String) : IOException(message) {
    /** 보호자 검증 전 — 재시도해도 통과하지 않는다 */
    class PermissionDenied(val denial: PermissionDenial) : LearningApiException(denial.code)
    class Http(val status: Int, val code: String?, message: String) :
        LearningApiException("학습 서버 응답 $status${code?.let { " ($it)" } ?: ""}: $message")  // i18n:skip
    class Malformed : LearningApiException("학습 서버 응답을 해석하지 못했습니다")  // i18n:skip
    class Offline(cause: String) : LearningApiException("학습 서버에 연결하지 못했습니다: $cause")  // i18n:skip

    /** 지금 재시도해도 소용없는 상태인지 (권한 문제는 큐를 그대로 두고 멈춘다) */
    val isPermanent: Boolean
        get() = this is PermissionDenied || (this is Http && (status == 400 || status == 404))
}

object LearningApi {
    const val BASE = "https://gugu.smap.site/api"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Cloudflare 봇 차단을 피하기 위한 앱 식별 UA */
    val userAgent: String
        get() = "GuguAdventure/${BuildConfig.VERSION_NAME}.${BuildConfig.VERSION_CODE} (Android)"

    /** 이벤트 배치 업로드 */
    suspend fun uploadEvents(accessToken: String, events: List<LearningEvent>): LearningEventBatchResponse {
        val body = buildJsonObject {
            put("events", json.encodeToJsonElement(events))
        }.toString()
        val text = send("/v1/learning/events:batch", "POST", accessToken, body)
        return runCatching { json.decodeFromString(LearningEventBatchResponse.serializer(), text) }
            .getOrElse { throw LearningApiException.Malformed() }
    }

    /** 서버가 아는 이 계정의 프리미엄 여부 — 운영자가 DB 에서 부여·회수한 값도 여기로 온다. */
    suspend fun premiumStatus(accessToken: String): PremiumStatusResponse {
        val text = send("/premium/status", "GET", accessToken, null)
        return runCatching { json.decodeFromString(PremiumStatusResponse.serializer(), text) }
            .getOrElse { throw LearningApiException.Malformed() }
    }

    /**
     * 보호자 권한 설정 — 앱 내 구매 이력을 근거로 서버가 app_metadata 를 세운다.
     * 성공 후에는 토큰을 새로 받아야 새 권한이 JWT 에 담긴다.
     */
    suspend fun verifyGuardian(accessToken: String): GuardianVerifyResponse {
        val text = send("/account/guardian", "POST", accessToken, "")
        return runCatching { json.decodeFromString(GuardianVerifyResponse.serializer(), text) }
            .getOrElse { throw LearningApiException.Malformed() }
    }

    /**
     * 기기 기록의 귀속을 요청한다 — 서버가 학습자를 만들거나 기존 학습자를 알려 준다.
     * 보호자 권한이 있는 계정만 호출할 수 있다.
     */
    suspend fun claim(accessToken: String, installId: String, localEventCount: Int): LearningClaimResponse {
        val body = buildJsonObject {
            put("installId", installId)
            put("localEventCount", localEventCount)
        }.toString()
        val text = send("/v1/learning/claim", "POST", accessToken, body)
        return runCatching { json.decodeFromString(LearningClaimResponse.serializer(), text) }
            .getOrElse { throw LearningApiException.Malformed() }
    }

    /** 후보 중 하나를 골라 귀속을 확정한다 */
    suspend fun confirmClaim(accessToken: String, installId: String, learnerId: String): LearningClaimConfirmResponse {
        val body = buildJsonObject {
            put("installId", installId)
            put("learnerId", learnerId)
        }.toString()
        val text = send("/v1/learning/claim/confirm", "POST", accessToken, body)
        return runCatching { json.decodeFromString(LearningClaimConfirmResponse.serializer(), text) }
            .getOrElse { throw LearningApiException.Malformed() }
    }

    /** 서버 학습 상태 조회 */
    suspend fun fetchState(accessToken: String, learnerId: String? = null): LearningStateResponse {
        val query = learnerId?.let { "?learnerId=$it" } ?: ""
        val text = send("/v1/learning/state$query", "GET", accessToken, null)
        return runCatching { json.decodeFromString(LearningStateResponse.serializer(), text) }
            .getOrElse { throw LearningApiException.Malformed() }
    }

    /**
     * 계정과 서버에 보관된 학습 기록을 삭제한다 (App Store 5.1.1(v) / Play 데이터 삭제 요건).
     * 구매 영수증은 환불·감사 대응을 위해 서버에 남고 계정 연결만 해제된다.
     */
    suspend fun deleteAccount(accessToken: String) {
        send("/account", "DELETE", accessToken, null)
    }

    /** 공통 전송 — PushApi 도 같은 UA·인증·오류 해석을 쓴다. timeoutMs 는 연결·읽기 각각의 제한 */
    internal suspend fun send(
        path: String,
        method: String,
        accessToken: String,
        body: String?,
        timeoutMs: Int = 30_000,
    ): String = withContext(Dispatchers.IO) {
        val conn = try {
            (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Authorization", "Bearer $accessToken")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
        } catch (e: Exception) {
            throw LearningApiException.Offline(e.message ?: e.toString())
        }

        try {
            if (body != null) conn.outputStream.use { it.write(body.toByteArray()) }
            val status = try {
                conn.responseCode
            } catch (e: Exception) {
                // 네트워크 실패는 일시적 — 큐를 유지하고 다음 기회에 재시도한다
                throw LearningApiException.Offline(e.message ?: e.toString())
            }
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""

            if (status !in 200..299) {
                val (code, message) = errorDetail(text)
                if (status == 403) {
                    PermissionDenial.of(code)?.let { throw LearningApiException.PermissionDenied(it) }
                }
                throw LearningApiException.Http(status, code, message)
            }
            text
        } finally {
            conn.disconnect()
        }
    }

    /** FastAPI 는 {"detail": {"code": ..., "message": ...}} 로 오류를 준다 */
    private fun errorDetail(text: String): Pair<String?, String> {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return null to text.take(200)
        val detail = obj["detail"]
        val asObject = runCatching { detail?.jsonObject }.getOrNull()
        if (asObject != null) {
            return asObject["code"]?.jsonPrimitive?.contentOrNull() to
                (asObject["message"]?.jsonPrimitive?.contentOrNull() ?: "")
        }
        val asString = runCatching { detail?.jsonPrimitive?.contentOrNull() }.getOrNull()
        return null to (asString ?: text.take(200))
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        runCatching { content }.getOrNull()
}
