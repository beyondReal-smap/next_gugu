package site.smap.gugudan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import site.smap.gugudan.core.PushCopy
import site.smap.gugudan.core.PushRegistrationState
import site.smap.gugudan.core.PushRoute
import site.smap.gugudan.core.PushSettings
import site.smap.gugudan.services.LearningApiException
import site.smap.gugudan.services.PushConsentResponse
import site.smap.gugudan.services.PushDeviceRequest
import site.smap.gugudan.services.PushDeviceResponse
import site.smap.gugudan.store.AppTab
import site.smap.gugudan.store.PushAccount
import site.smap.gugudan.store.PushLogger
import site.smap.gugudan.store.PushMessaging
import site.smap.gugudan.store.PushServer
import site.smap.gugudan.store.PushStorage
import site.smap.gugudan.store.PushStore
import site.smap.gugudan.store.Router
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

// PushStore 수명주기 (iOS GuguTests/PushTests.swift FakePushWorld 와 같은 케이스) — .claude/plans/native-push-app-spec.md
// "테스트(양 플랫폼 같은 케이스)" + "점검 후 추가 결정": ack 전 미구독 / 오프라인 실패 / 끄기 즉시+대기열 / 계정 삭제 초기화 /
// 로그아웃 전 DELETE 타임아웃(잠금 대기 포함) / 서버 ON·기기 OFF 시 철회 전송 / 철회 우선 / 해제 먼저 / 새 식별자 promo 해제 1회.
class PushStoreTests {

    // MARK: 광고 동의 전이

    @Test fun grantSubscribesPromoOnlyAfterAck() = pushTest { world, store ->
        val result = store.grantMarketing()
        assertTrue(result is PushStore.MarketingResult.Recorded)
        assertTrue(store.settings.marketingOptIn)
        assertEquals(PushCopy.localDate(RECORDED_AT, ZoneId.systemDefault()), result.date)  // 서버 기록 시각
        val ack = world.log.indexOf("consent:true")
        val promo = world.log.indexOf("subscribe:promo")
        assertTrue(ack >= 0 && promo > ack, "ack 뒤에만 promo 구독: ${world.log}")
    }

    @Test fun grantOfflineFailsAndStaysOff() = pushTest { world, store ->
        world.consentError = LearningApiException.Offline("network down")
        val result = store.grantMarketing()
        assertEquals(PushStore.MarketingResult.Failed, result)
        assertFalse(store.settings.marketingOptIn)
        assertFalse("subscribe:promo" in world.log, "ack 전 미구독: ${world.log}")
        // 다른 항목이 모두 꺼져 있으니 방금 만든 등록·토큰을 정리한다
        world.settle()
        assertTrue("unregister:jwt-u1" in world.log && "deleteToken" in world.log, world.log.toString())
    }

    @Test fun grantSendsPendingRevokeFirst() = pushTest(
        settings = PushSettings(serviceOptIn = true, pendingMarketingRevoke = true),
        state = registeredState(setOf("notice", "notice-android")),
    ) { world, store ->
        val result = store.grantMarketing()
        assertTrue(result is PushStore.MarketingResult.Recorded)
        val revoke = world.log.indexOf("consent:false")
        val grant = world.log.indexOf("consent:true")
        assertTrue(revoke >= 0 && grant > revoke, "철회 먼저: ${world.log}")
    }

    @Test fun grantWaitsWhilePendingRevokeCannotBeSent() = pushTest(
        settings = PushSettings(serviceOptIn = true, pendingMarketingRevoke = true),
        state = registeredState(setOf("notice", "notice-android")),
    ) { world, store ->
        world.consentError = LearningApiException.Offline("network down")
        assertEquals(PushStore.MarketingResult.Failed, store.grantMarketing())
        assertFalse("consent:true" in world.log, world.log.toString())
        assertTrue(store.settings.pendingMarketingRevoke)
    }

    @Test fun revokeImmediateThenQueued() = pushTest(
        settings = PushSettings(serviceOptIn = true, marketingOptIn = true, marketingOptInAt = RECORDED_AT),
        state = registeredState(setOf("notice", "notice-android", "promo")),
    ) { world, store ->
        world.consentError = LearningApiException.Offline("network down")
        val done = store.revokeMarketing()
        assertFalse(store.settings.marketingOptIn)
        assertTrue(store.settings.pendingMarketingRevoke)
        assertTrue("unsubscribe:promo" in world.log, world.log.toString())
        assertEquals(LocalDate.now(), done.date)  // 서버 실패 — 기기 날짜

        // 이어지는 동기화(또는 다음 앱 실행)에서 대기열을 보낸다
        world.consentError = null
        world.settle()
        assertFalse(store.settings.pendingMarketingRevoke)
        assertEquals(2, world.log.count { it == "consent:false" }, world.log.toString())
    }

    @Test fun revokeDoesNotWaitForSyncLock() = pushTest(
        settings = PushSettings(serviceOptIn = true, marketingOptIn = true, marketingOptInAt = RECORDED_AT),
        state = registeredState(setOf("notice", "notice-android", "promo")),
    ) { world, store ->
        world.tokenHangs = true
        store.requestSync()
        yield()   // 동기화가 잠금을 잡고 토큰에서 멈춘다
        assertEquals("token", world.log.last())

        val started = System.nanoTime()
        val done = store.revokeMarketing()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue(elapsedMs < 1_000, "잠금을 기다리지 않는다: ${elapsedMs}ms")
        assertFalse(store.settings.marketingOptIn)
        assertTrue(store.settings.pendingMarketingRevoke, "서버 기록은 대기열로")
        assertTrue("unsubscribe:promo" in world.log, world.log.toString())
        assertEquals(LocalDate.now(), done.date)
    }

    // MARK: 서버·기기 불일치

    @Test fun serverOnDeviceOffSendsRevokeAndKeepsOff() = pushTest(
        settings = PushSettings(serviceOptIn = true),
    ) { world, store ->
        world.serverMarketing = true
        store.sync()
        assertTrue("consent:false" in world.log, world.log.toString())
        assertFalse(store.settings.marketingOptIn)
        assertFalse(store.settings.pendingMarketingRevoke)
        assertFalse("subscribe:promo" in world.log)
    }

    @Test fun serverOffDeviceOnTurnsDeviceOff() = pushTest(
        settings = PushSettings(serviceOptIn = true, marketingOptIn = true, marketingOptInAt = RECORDED_AT),
        state = PushRegistrationState(lastSubscribed = setOf("notice", "notice-android", "promo"), tokenIssued = true),
    ) { world, store ->
        world.serverMarketing = false
        store.sync()
        assertFalse(store.settings.marketingOptIn)
        assertTrue("unsubscribe:promo" in world.log, world.log.toString())
        assertFalse("consent:false" in world.log)
    }

    @Test fun deniedBySystemStillSendsRevokeAndDropsPromo() = pushTest(
        settings = PushSettings(serviceOptIn = true, pendingMarketingRevoke = true),
        state = registeredState(setOf("notice", "notice-android", "promo")),
    ) { world, store ->
        world.authorized = false
        store.sync()
        assertTrue(store.deniedBySystem)
        assertTrue("consent:false" in world.log, world.log.toString())
        assertTrue("unsubscribe:promo" in world.log, world.log.toString())
        assertFalse(world.log.any { it.startsWith("subscribe:") || it == "token" }, world.log.toString())
    }

    // MARK: 계정 변화

    @Test fun accountDeletionResetsMarketingKeepsNotice() = pushTest(
        settings = PushSettings(serviceOptIn = true, marketingOptIn = true, marketingOptInAt = RECORDED_AT),
        state = registeredState(setOf("notice", "notice-android", "promo")),
    ) { world, store ->
        store.onAccountDeleted()
        assertFalse(store.settings.marketingOptIn)
        assertNull(store.settings.marketingOptInAt)
        assertTrue(store.settings.serviceOptIn)
        assertNull(world.state?.registeredUserId)

        world.userId = "u2"
        store.sync()
        assertTrue("register:u2" in world.log, world.log.toString())
        assertTrue("unsubscribe:promo" in world.log)
        assertFalse(world.log.any { it.startsWith("consent:") }, "purge 가 지웠다 — 철회 전송 없음")
        assertEquals(setOf("notice", "notice-android"), world.state?.lastSubscribed)
    }

    @Test fun signOutUnregistersWithCurrentJwt() = pushTest(
        settings = PushSettings(serviceOptIn = true),
        state = registeredState(setOf("notice", "notice-android")),
    ) { world, store ->
        store.unregisterBeforeSignOut()
        assertEquals("unregister:jwt-u1", world.log.last())
        assertNull(world.state?.registeredUserId)
        assertFalse(world.state!!.needsReregister)
    }

    @Test fun signOutUnregisterTimesOut() = pushTest(
        settings = PushSettings(serviceOptIn = true),
        state = registeredState(setOf("notice", "notice-android")),
    ) { world, store ->
        world.unregisterHangs = true
        val elapsedMs = timed { store.unregisterBeforeSignOut() }
        assertTrue(elapsedMs < SIGN_OUT_TIMEOUT_MS + 700, "5초(테스트 ${SIGN_OUT_TIMEOUT_MS}ms) 안에 끝난다: ${elapsedMs}ms")
        assertTrue(world.state!!.needsReregister)
        assertEquals("u1", world.state?.registeredUserId)
    }

    @Test fun signOutTimeoutIncludesSyncLockWait() = pushTest(
        settings = PushSettings(serviceOptIn = true),
        state = registeredState(setOf("notice", "notice-android")),
    ) { world, store ->
        world.tokenHangs = true
        store.requestSync()
        yield()   // 동기화가 잠금을 잡고 토큰에서 멈춘다
        val elapsedMs = timed { store.unregisterBeforeSignOut() }
        assertTrue(elapsedMs < SIGN_OUT_TIMEOUT_MS + 700, "잠금 대기 포함 제한: ${elapsedMs}ms")
        assertTrue(world.state!!.needsReregister)
        assertFalse(world.log.any { it.startsWith("unregister") }, world.log.toString())
    }

    // MARK: 토픽

    @Test fun topicsUnsubscribeBeforeSubscribe() = pushTest(
        settings = PushSettings(serviceOptIn = true),
        state = registeredState(setOf("promo")),
    ) { world, store ->
        store.sync()
        val unsub = world.log.indexOf("unsubscribe:promo")
        val sub = world.log.indexOf("subscribe:notice")
        assertTrue(unsub >= 0 && sub > unsub, "해제 먼저: ${world.log}")
    }

    @Test fun topicTimeoutIsRetriedNotFatal() = pushTest(
        settings = PushSettings(serviceOptIn = true),
    ) { world, store ->
        world.hangingTopic = "notice"
        val elapsedMs = timed { store.sync() }
        assertTrue(elapsedMs < TOPIC_TIMEOUT_MS * 2 + 700, "토픽 호출마다 제한: ${elapsedMs}ms")
        assertEquals(setOf("notice-android"), world.state?.lastSubscribed)

        world.hangingTopic = null
        store.sync()
        assertEquals(setOf("notice", "notice-android"), world.state?.lastSubscribed)
    }

    @Test fun newInstallIdUnsubscribesPromoOnce() = pushTest(
        installId = null,
        // 백업 복원 등으로 설정만 남은 경우 — 광고 동의는 이어받지 않는다
        settings = PushSettings(serviceOptIn = true, marketingOptIn = true, marketingOptInAt = RECORDED_AT),
    ) { world, store ->
        assertFalse(store.settings.marketingOptIn)
        assertTrue(world.state!!.promoCleanupPending)
        store.sync()
        assertEquals(1, world.log.count { it == "unsubscribe:promo" }, world.log.toString())
        assertFalse(world.state!!.promoCleanupPending)

        store.setService(false)
        world.settle()
        store.setService(true)
        world.settle()
        assertEquals(1, world.log.count { it == "unsubscribe:promo" }, "1회만: ${world.log}")
    }

    @Test fun existingInstallIdDoesNotTouchPromo() = pushTest(
        settings = PushSettings(serviceOptIn = true),
    ) { world, store ->
        store.sync()
        assertFalse("unsubscribe:promo" in world.log, world.log.toString())
    }

    @Test fun allOffNeverTouchesFirebase() = pushTest(installId = null) { world, store ->
        store.sync()
        assertTrue(world.log.isEmpty(), "켠 적 없으면 토큰을 만들지 않는다: ${world.log}")
    }

    // MARK: 기존 사용자 1회 안내

    @Test fun noticeHintShowsUntilDismissedOrNoticeOn() = pushTest { world, store ->
        assertTrue(store.settings.showsNoticeHint)
        store.dismissNoticeHint()
        assertFalse(store.settings.showsNoticeHint)
        assertTrue(world.settings!!.noticeHintDismissed, "기기에 저장")
    }

    @Test fun turningNoticeOnHidesHintForGood() = pushTest { world, store ->
        store.setService(true)
        store.setService(false)
        world.settle()
        assertFalse(store.settings.showsNoticeHint)
        assertTrue(world.settings!!.noticeHintDismissed)
    }

    @Test fun noticeAlreadyOnBeforeHintFieldNeverShowsHint() = pushTest(
        settings = PushSettings(serviceOptIn = true),
    ) { world, store ->
        store.setService(false)
        world.settle()
        assertFalse(store.settings.showsNoticeHint, "이미 켠 적이 있다")
    }

    // MARK: 딥링크 지연

    @Test fun routeWaitsWhilePlaying() {
        val router = Router()
        var paywall = 0
        router.pendingRoute = PushRoute.PROFILE_REPORT
        assertFalse(router.applyPendingRoute(onboarded = true, playing = true, isPremium = false) { paywall++ })
        assertEquals(PushRoute.PROFILE_REPORT, router.pendingRoute)
        assertEquals(AppTab.HOME, router.tab)
        assertFalse(router.applyPendingRoute(onboarded = false, playing = false, isPremium = false) { paywall++ })
        assertEquals(PushRoute.PROFILE_REPORT, router.pendingRoute)

        assertTrue(router.applyPendingRoute(onboarded = true, playing = false, isPremium = false) { paywall++ })
        assertNull(router.pendingRoute)
        assertEquals(AppTab.PROFILE, router.tab)
        assertEquals(0, paywall)
    }

    @Test fun paywallRouteGoesHomeWhenPremium() {
        val router = Router()
        var paywall = 0
        router.tab = AppTab.PROFILE
        router.settingsOpen = true
        router.open(PushRoute.PAYWALL, isPremium = true) { paywall++ }
        assertEquals(0, paywall)
        assertEquals(AppTab.HOME, router.tab)
        assertFalse(router.settingsOpen)

        router.open(PushRoute.PAYWALL, isPremium = false) { paywall++ }
        assertEquals(1, paywall)
    }

    @Test fun settingsRouteFocusesNotifications() {
        val router = Router()
        router.open(PushRoute.SETTINGS_NOTIFICATIONS, isPremium = false) { fail("페이월 아님") }
        assertTrue(router.settingsOpen)
        assertTrue(router.settingsFocusNotifications)
    }

    // MARK: 가짜 세계

    private fun registeredState(subscribed: Set<String>) = PushRegistrationState(
        lastSubscribed = subscribed, tokenIssued = true, registeredUserId = "u1",
        registeredSignature = "stale", lastRegisteredAtMs = 0,
    )

    private suspend fun timed(block: suspend () -> Unit): Long {
        val started = System.nanoTime()
        block()
        return (System.nanoTime() - started) / 1_000_000
    }

    /**
     * 한 스레드(runBlocking)에서 돈다 — requestSync 가 띄운 작업도 같은 스레드에서 차례로 돈다.
     * 스토어 scope 는 runBlocking 의 자식이 아니다 — 멈춘 가짜 호출(awaitCancellation)이 테스트 종료를 막지 않게 끝에서 취소한다.
     */
    private fun pushTest(
        installId: String? = UUID.randomUUID().toString(),
        settings: PushSettings? = null,
        state: PushRegistrationState? = null,
        block: suspend CoroutineScope.(FakePushWorld, PushStore) -> Unit,
    ) = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val world = FakePushWorld(scope, installId, settings, state)
        try {
            block(world, world.store())
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        const val SIGN_OUT_TIMEOUT_MS = 300L
        const val TOPIC_TIMEOUT_MS = 200L
        const val RECORDED_AT = "2026-10-19T03:00:00Z"
    }

    private class FakePushWorld(
        val scope: CoroutineScope,
        var installId: String?,
        var settings: PushSettings?,
        var state: PushRegistrationState?,
    ) {
        val log = mutableListOf<String>()
        var authorized = true
        var tokenHangs = false
        var hangingTopic: String? = null
        var unregisterHangs = false
        var consentError: Exception? = null
        var serverMarketing = false
        var userId: String? = "u1"

        /** requestSync 로 띄운 작업이 모두 끝날 때까지 기다린다 */
        suspend fun settle() {
            while (true) {
                val running = scope.coroutineContext.job.children.filter { it.isActive }.toList()
                if (running.isEmpty()) return
                running.forEach { it.join() }
            }
        }

        fun store() = PushStore(
            storage = object : PushStorage {
                override fun loadInstallId() = installId
                override fun saveInstallId(id: String) { installId = id }
                override fun loadSettings() = settings
                override fun saveSettings(settings: PushSettings) { this@FakePushWorld.settings = settings }
                override fun loadState() = state
                override fun saveState(state: PushRegistrationState) { this@FakePushWorld.state = state }
            },
            messaging = object : PushMessaging {
                override suspend fun token(): String {
                    log += "token"
                    if (tokenHangs) awaitCancellation()
                    return "fcm-token-A"
                }
                override suspend fun subscribe(topic: String) {
                    log += "subscribe:$topic"
                    if (topic == hangingTopic) awaitCancellation()
                }
                override suspend fun unsubscribe(topic: String) {
                    log += "unsubscribe:$topic"
                    if (topic == hangingTopic) awaitCancellation()
                }
                override suspend fun deleteToken() { log += "deleteToken" }
            },
            server = object : PushServer {
                override suspend fun register(jwt: String, request: PushDeviceRequest): PushDeviceResponse {
                    log += "register:${userId}"
                    return PushDeviceResponse(
                        pushInstallId = request.pushInstallId,
                        serviceOptIn = request.serviceOptIn,
                        learningOptIn = request.learningOptIn,
                        localReminderEnabled = request.localReminderEnabled,
                        marketingOptIn = serverMarketing,
                        marketingOptInAt = if (serverMarketing) RECORDED_AT else null,
                        updatedAt = RECORDED_AT,
                    )
                }
                override suspend fun recordConsent(jwt: String, pushInstallId: String, granted: Boolean): PushConsentResponse {
                    log += "consent:$granted"
                    consentError?.let { throw it }
                    serverMarketing = granted
                    return PushConsentResponse("marketing", granted, RECORDED_AT, "구구 어드벤처")
                }
                override suspend fun unregister(jwt: String, pushInstallId: String, timeoutMs: Int) {
                    if (unregisterHangs) awaitCancellation()
                    log += "unregister:$jwt"
                    serverMarketing = false
                }
            },
            permission = { authorized },
            account = object : PushAccount {
                override val userId: String? get() = this@FakePushWorld.userId
                override suspend fun accessToken(): String? = userId?.let { "jwt-$it" }
            },
            scope = scope,
            learningEligible = { false },
            localReminderEnabled = { false },
            log = object : PushLogger {
                override fun info(message: String) = Unit
                override fun warn(message: String) = Unit
            },
            signOutTimeoutMs = SIGN_OUT_TIMEOUT_MS,
            topicTimeoutMs = TOPIC_TIMEOUT_MS,
        )
    }
}
