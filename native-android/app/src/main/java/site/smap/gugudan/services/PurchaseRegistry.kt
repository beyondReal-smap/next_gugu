package site.smap.gugudan.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import site.smap.gugudan.core.PremiumConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// 구매를 서버에 등록한다 (POST /api/iap/verify) — iOS PurchaseRegistry 이식.
//
// 서버는 purchases 테이블에 (transaction_id, product_id, user_id) 를 남기고,
// 보호자 권한(POST /api/account/guardian)이 이 기록을 근거로 삼는다.
// 계정 토큰 없이 부르면 게스트 구매로 기록되고 user_id 가 비므로, 반드시 토큰을 붙인다.

@Serializable
data class PurchaseVerifyResponse(
    val ok: Boolean = false,
    val premium: Boolean = false,
    val transactionId: String? = null,
)

class PurchaseRegistryException(message: String) : IOException(message)

object PurchaseRegistry {
    private val json = Json { ignoreUnknownKeys = true }

    /** Play 구매 토큰을 계정에 연결한다. 실패해도 프리미엄 자체는 로컬 판정이라 영향이 없다. */
    suspend fun register(accessToken: String, purchaseToken: String): PurchaseVerifyResponse =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("platform", "android")
                put("productId", PremiumConfig.PRODUCT_ID)
                put("purchaseToken", purchaseToken)
            }.toString()

            val conn = (URL(LearningApi.BASE + "/iap/verify").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 30_000
                doOutput = true
                // Cloudflare 봇 차단 회피 — 기본 UA 는 403(error 1010)을 받는다
                setRequestProperty("User-Agent", LearningApi.userAgent)
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray()) }
                val status = conn.responseCode
                val text = (if (status in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                if (status !in 200..299) {
                    throw PurchaseRegistryException("구매 등록 응답 $status: ${text.take(200)}")
                }
                val decoded = runCatching { json.decodeFromString(PurchaseVerifyResponse.serializer(), text) }
                    .getOrElse { throw PurchaseRegistryException("구매 등록 응답을 해석하지 못했습니다") }
                // 서버는 요청을 처리하기만 해도 ok=true 를 준다. 실제 검증 결과는 premium 이다.
                if (!decoded.premium) {
                    throw PurchaseRegistryException("스토어가 이 영수증을 확인하지 못했습니다")
                }
                decoded
            } finally {
                conn.disconnect()
            }
        }
}
