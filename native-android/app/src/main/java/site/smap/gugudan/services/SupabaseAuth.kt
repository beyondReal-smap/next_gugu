package site.smap.gugudan.services

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import site.smap.gugudan.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// Supabase GoTrue REST 호출 (익명 로그인 / 토큰 갱신 / 이메일 연결·로그인 / 세션 해제) — iOS SupabaseAuth 이식.
// SDK 를 붙이지 않고 필요한 엔드포인트만 직접 호출한다 — 의존성 0.

/** Supabase 접속 설정 — BuildConfig(local.properties) 주입값 */
object SupabaseConfig {
    private val host: String = BuildConfig.SUPABASE_HOST.trim()
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()

    val baseUrl: String? =
        if (host.isEmpty() || host.startsWith("<")) null else "https://$host"

    /** 빌드에 Supabase 값이 들어 있는지 — false 면 인증을 시도하지 않는다 */
    val isConfigured: Boolean
        get() = baseUrl != null && anonKey.isNotEmpty() && !anonKey.startsWith("<")
}

@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    /** 만료 시각 (epoch 초) */
    val expiresAt: Double,
    val userId: String,
    val isAnonymous: Boolean,
    /** 계정에 붙은 이메일 (익명이면 null). 이 필드가 없던 예전 저장본도 기본값 null 로 그대로 읽힌다. */
    val email: String? = null,
) {
    /** 만료 60초 전부터는 갱신 대상으로 본다 (시계 오차·요청 지연 여유) */
    val isExpired: Boolean
        get() = System.currentTimeMillis() / 1000.0 >= expiresAt - 60
}

class SupabaseAuthException(
    val status: Int,
    val code: String?,
    message: String,
) : IOException(message) {
    /** 재로그인이 필요한 상태 — 리프레시 토큰 만료/폐기 */
    val needsFreshSignIn: Boolean get() = status == 400 || status == 401
}

object SupabaseAuth {
    private const val TAG = "SupabaseAuth"
    private val json = Json { ignoreUnknownKeys = true }

    /** 익명 계정 생성 + 세션 발급 (대시보드에서 Anonymous sign-ins 활성화 필요) */
    suspend fun signInAnonymously(): AuthSession = post("/auth/v1/signup", "{}")

    /** 리프레시 토큰으로 세션 갱신 */
    suspend fun refresh(refreshToken: String): AuthSession =
        post("/auth/v1/token?grant_type=refresh_token", """{"refresh_token":"$refreshToken"}""")

    /**
     * 익명 계정에 이메일을 붙인다 — 확인 코드가 그 주소로 발송된다.
     * 이 호출만으로는 승격되지 않고, verifyEmailChange 까지 끝나야 영구 계정이 된다.
     */
    suspend fun requestEmailPromotion(accessToken: String, email: String) {
        send(
            "/auth/v1/user", "PUT",
            buildJsonObject { put("email", email) }.toString(),
            accessToken = accessToken,
        )
    }

    /** 메일로 받은 6자리 코드로 이메일 확인을 끝내고 새 세션을 받는다 */
    suspend fun verifyEmailChange(email: String, token: String): AuthSession {
        val body = buildJsonObject {
            put("type", "email_change")
            put("email", email)
            put("token", token)
        }.toString()
        return parse(json.parseToJsonElement(send("/auth/v1/verify", "POST", body)).jsonObject)
    }

    /**
     * 이미 있는 이메일 계정으로 로그인 코드를 보낸다 (Magic link or OTP 메일 템플릿).
     * create_user=false — 없는 주소면 계정을 만들지 않고 422(otp_disabled)로 거부된다.
     */
    suspend fun requestLoginCode(email: String) {
        val body = buildJsonObject {
            put("email", email)
            put("create_user", false)
        }.toString()
        send("/auth/v1/otp", "POST", body)
    }

    /** 메일로 받은 6자리 코드로 그 계정에 로그인한다 */
    suspend fun verifyLoginCode(email: String, token: String): AuthSession {
        val body = buildJsonObject {
            put("type", "email")
            put("email", email)
            put("token", token)
        }.toString()
        return parse(json.parseToJsonElement(send("/auth/v1/verify", "POST", body)).jsonObject)
    }

    /** 이 기기의 세션만 서버에서 폐기한다 (다른 기기의 로그인은 그대로) */
    suspend fun signOut(accessToken: String) {
        send("/auth/v1/logout?scope=local", "POST", "{}", accessToken = accessToken)
    }

    private suspend fun post(path: String, body: String): AuthSession =
        parse(json.parseToJsonElement(send(path, "POST", body)).jsonObject)

    /** GoTrue 공통 호출 — apikey 는 항상, Authorization 은 사용자 토큰이 있을 때만 붙인다 */
    private suspend fun send(
        path: String,
        method: String,
        body: String,
        accessToken: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val base = SupabaseConfig.baseUrl
            ?: throw IllegalStateException("Supabase 설정이 빌드에 없습니다")

        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("apikey", SupabaseConfig.anonKey)
            setRequestProperty("Content-Type", "application/json")
            accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""

            if (status !in 200..299) {
                val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                throw SupabaseAuthException(
                    status,
                    obj?.get("error_code")?.jsonPrimitive?.contentOrNullSafe(),
                    obj?.get("msg")?.jsonPrimitive?.contentOrNullSafe()
                        ?: obj?.get("error_description")?.jsonPrimitive?.contentOrNullSafe()
                        ?: text.take(200),
                )
            }
            text
        } finally {
            conn.disconnect()
        }
    }

    /** 토큰 응답을 세션으로 바꾼다 (테스트에서 직접 부른다) */
    internal fun parse(obj: JsonObject): AuthSession {
        val accessToken = obj["access_token"]?.jsonPrimitive?.contentOrNullSafe()
        val refreshToken = obj["refresh_token"]?.jsonPrimitive?.contentOrNullSafe()
        val user = obj["user"]?.jsonObject
        val userId = user?.get("id")?.jsonPrimitive?.contentOrNullSafe()
        if (accessToken == null || refreshToken == null || userId == null) {
            Log.w(TAG, "응답에 토큰/사용자 정보가 없습니다")
            throw IOException("Supabase 응답을 해석하지 못했습니다")
        }
        // expires_at(절대시각)을 주면 그대로 쓰고, 없으면 expires_in(상대초)으로 계산한다
        val expiresAt = obj["expires_at"]?.jsonPrimitive?.doubleOrNullSafe()
            ?: (System.currentTimeMillis() / 1000.0 +
                (obj["expires_in"]?.jsonPrimitive?.doubleOrNullSafe() ?: 3600.0))

        return AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt,
            userId = userId,
            isAnonymous = user["is_anonymous"]?.jsonPrimitive?.booleanOrNull ?: true,
            // 익명 사용자는 email 을 빈 문자열로 준다
            email = (user["email"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() },
        )
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    runCatching { content }.getOrNull()

private fun kotlinx.serialization.json.JsonPrimitive.doubleOrNullSafe(): Double? =
    runCatching { content.toDouble() }.getOrNull()
