package site.smap.gugudan.services

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import site.smap.gugudan.BuildConfig
import site.smap.gugudan.core.AppLanguage
import java.time.ZoneId
import java.util.Locale

// 원격 푸시 기기 등록·광고 동의 API (서버 api/push.py, /api/v1/push).
// 전송·인증·오류 해석은 LearningApi.send 를 그대로 쓴다 (UA 없이 부르면 Cloudflare 가 막는다).

/** 서버는 모르는 필드를 거부한다(extra=forbid → 400) — 계약 필드만 둔다. 광고 동의는 consents 로만 바꾼다 */
@Serializable
data class PushDeviceRequest(
    val pushInstallId: String,
    val fcmToken: String,
    val platform: String,
    val appVersion: String,
    val locale: String?,
    val timeZone: String?,
    val serviceOptIn: Boolean,
    val learningOptIn: Boolean,
    val localReminderEnabled: Boolean,
)

/** 서버 기준 상태 — 앱은 marketingOptIn 으로 promo 구독을 맞춘다 (서버는 계정이 바뀌면 광고 동의를 false 로 초기화한다) */
@Serializable
data class PushDeviceResponse(
    val pushInstallId: String,
    val serviceOptIn: Boolean,
    val learningOptIn: Boolean,
    val localReminderEnabled: Boolean,
    val marketingOptIn: Boolean,
    val marketingOptInAt: String? = null,
    val marketingReconfirmedAt: String? = null,
    val updatedAt: String,
)

@Serializable
data class PushConsentRequest(
    val pushInstallId: String,
    val kind: String,
    val granted: Boolean,
    val source: String,
    val appVersion: String,
)

@Serializable
data class PushConsentResponse(
    val kind: String,
    val granted: Boolean,
    val recordedAt: String,
    val sender: String,
)

object PushApi {
    private const val PATH = "/v1/push/devices"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** 서버 appVersion 형식: "2.5.0 (23)" (api/push.py APP_VERSION_PATTERN, 1~32자) */
    val appVersion: String get() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    // 서버가 형식을 검증한다 (api/push.py LOCALE_PATTERN·TIME_ZONE_PATTERN). 어긋나면 등록 전체가 400 으로 실패하므로
    // 보내기 전에 걸러 null(선택 항목)로 보낸다.
    private val localePattern = Regex("^[A-Za-z]{2,3}(?:[-_][A-Za-z0-9]{2,8}){0,3}$")
    private val timeZonePattern = Regex("^[A-Za-z][A-Za-z0-9_+\\-]*(?:/[A-Za-z0-9_+\\-]+){0,2}$")

    fun normalizedLocale(tag: String): String? = tag.takeIf { it.length <= 35 && localePattern.matches(it) }
    fun normalizedTimeZone(id: String): String? = id.takeIf { it.length <= 64 && timeZonePattern.matches(it) }

    /**
     * 앱 언어·기기 지역 "ko-KR" / "en-FR" — 서버가 알림 문구(주간 리포트 등) 언어를 고르는 값이라 기기 언어가 아닌
     * 앱 언어(AppLanguage)를 쓴다 (fr 기기는 앱이 영어이므로 "en-FR", iOS PushApi.localeTag 와 같은 규칙).
     * 지역을 모르면 언어만, 서버 형식에 맞지 않으면 null(선택 항목)
     */
    fun localeTag(language: AppLanguage = AppLanguage.current, locale: Locale = AppLanguage.formatLocale): String? {
        val region = locale.country
        return normalizedLocale(if (region.isEmpty()) language.code else "${language.code}-$region")
    }

    /** 기기 시간대 "Asia/Seoul" — 서버 형식에 맞지 않으면 null(선택 항목) */
    fun timeZoneId(zone: ZoneId = ZoneId.systemDefault()): String? = normalizedTimeZone(zone.id)

    suspend fun register(accessToken: String, request: PushDeviceRequest): PushDeviceResponse {
        val body = json.encodeToString(PushDeviceRequest.serializer(), request)
        val text = LearningApi.send(PATH, "POST", accessToken, body)
        return runCatching { json.decodeFromString(PushDeviceResponse.serializer(), text) }
            .getOrElse { throw LearningApiException.Malformed() }
    }

    /** 광고 동의·거부 기록. 기기 행이 없으면 404(LearningApiException.Http) — 등록 후 다시 시도한다 */
    suspend fun recordConsent(accessToken: String, pushInstallId: String, granted: Boolean): PushConsentResponse {
        val body = json.encodeToString(
            PushConsentRequest.serializer(),
            PushConsentRequest(pushInstallId, "marketing", granted, "settings", appVersion),
        )
        val text = LearningApi.send("$PATH/consents", "POST", accessToken, body)
        return runCatching { json.decodeFromString(PushConsentResponse.serializer(), text) }
            .getOrElse { throw LearningApiException.Malformed() }
    }

    /** 기기 해제 (멱등 — 이미 없어도 200). timeoutMs 는 로그아웃 직전 짧게 기다릴 때 줄인다 */
    suspend fun unregister(accessToken: String, pushInstallId: String, timeoutMs: Int = 30_000) {
        LearningApi.send("$PATH/$pushInstallId", "DELETE", accessToken, null, timeoutMs)
    }
}
