package site.smap.gugudan.store

import android.content.Context
import android.util.Log
import site.smap.gugudan.core.PushRegistrationState
import site.smap.gugudan.core.PushSettings
import site.smap.gugudan.services.LocalNotifications
import site.smap.gugudan.services.Persistence
import site.smap.gugudan.services.PushApi
import site.smap.gugudan.services.PushConsentResponse
import site.smap.gugudan.services.PushDeviceRequest
import site.smap.gugudan.services.PushDeviceResponse
import site.smap.gugudan.services.RemotePush

// PushStore 가 기대는 바깥 세계 — 저장소·Firebase·서버·권한·계정·기록.
// 앱은 아래 실제 구현을 쓰고, JVM 단위 테스트(PushStoreTests)는 가짜로 바꿔 수명주기(동의 전이·타임아웃·순서)를 검증한다.

/** 기기 전용 저장 (DEVICE_KEYS — gugu_device.xml, 백업 제외) */
interface PushStorage {
    fun loadInstallId(): String?
    fun saveInstallId(id: String)
    fun loadSettings(): PushSettings?
    fun saveSettings(settings: PushSettings)
    fun loadState(): PushRegistrationState?
    fun saveState(state: PushRegistrationState)
}

/** FCM 토큰·토픽 */
interface PushMessaging {
    suspend fun token(): String
    suspend fun subscribe(topic: String)
    suspend fun unsubscribe(topic: String)
    suspend fun deleteToken()
}

/** 서버 기기 등록·광고 동의 (api/push.py) */
interface PushServer {
    suspend fun register(jwt: String, request: PushDeviceRequest): PushDeviceResponse
    suspend fun recordConsent(jwt: String, pushInstallId: String, granted: Boolean): PushConsentResponse
    suspend fun unregister(jwt: String, pushInstallId: String, timeoutMs: Int = 30_000)
}

/** 시스템 알림 허용 여부 */
fun interface PushPermission {
    fun isAuthorized(): Boolean
}

/** 서버 호출에 쓸 계정 */
interface PushAccount {
    val userId: String?
    suspend fun accessToken(): String?
}

/** 기록 — android.util.Log 는 JVM 단위 테스트에서 쓸 수 없어 따로 둔다 */
interface PushLogger {
    fun info(message: String)
    fun warn(message: String)
}

// MARK: 실제 구현

class PersistencePushStorage(private val persistence: Persistence) : PushStorage {
    override fun loadInstallId(): String? = persistence.getString(Persistence.PUSH_INSTALL_KEY)
    override fun saveInstallId(id: String) = persistence.putString(Persistence.PUSH_INSTALL_KEY, id)
    override fun loadSettings(): PushSettings? = persistence.loadPushSettings()
    override fun saveSettings(settings: PushSettings) = persistence.savePushSettings(settings)
    override fun loadState(): PushRegistrationState? = persistence.loadPushState()
    override fun saveState(state: PushRegistrationState) = persistence.savePushState(state)
}

object FirebasePushMessaging : PushMessaging {
    override suspend fun token(): String = RemotePush.token()
    override suspend fun subscribe(topic: String) = RemotePush.subscribe(topic)
    override suspend fun unsubscribe(topic: String) = RemotePush.unsubscribe(topic)
    override suspend fun deleteToken() = RemotePush.deleteToken()
}

object ApiPushServer : PushServer {
    override suspend fun register(jwt: String, request: PushDeviceRequest): PushDeviceResponse =
        PushApi.register(jwt, request)

    override suspend fun recordConsent(jwt: String, pushInstallId: String, granted: Boolean): PushConsentResponse =
        PushApi.recordConsent(jwt, pushInstallId, granted)

    override suspend fun unregister(jwt: String, pushInstallId: String, timeoutMs: Int) =
        PushApi.unregister(jwt, pushInstallId, timeoutMs)
}

class SystemPushPermission(private val context: Context) : PushPermission {
    override fun isAuthorized(): Boolean = LocalNotifications.isAuthorized(context)
}

class AuthPushAccount(private val auth: AuthStore) : PushAccount {
    override val userId: String? get() = auth.userId
    override suspend fun accessToken(): String? = auth.accessToken()
}

object AndroidPushLogger : PushLogger {
    private const val TAG = "Push"
    override fun info(message: String) { Log.i(TAG, message) }
    override fun warn(message: String) { Log.w(TAG, message) }
}
