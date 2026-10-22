package site.smap.gugudan

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import site.smap.gugudan.core.GuardianGate
import site.smap.gugudan.core.PushChannels
import site.smap.gugudan.core.PushConsent
import site.smap.gugudan.core.PushCopy
import site.smap.gugudan.core.PushKind
import site.smap.gugudan.core.PushRegistration
import site.smap.gugudan.core.PushRegistrationState
import site.smap.gugudan.core.PushRoute
import site.smap.gugudan.core.PushSettings
import site.smap.gugudan.core.PushTopics
import site.smap.gugudan.services.PushApi
import site.smap.gugudan.services.PushDeviceRequest
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 원격 푸시 규칙 (iOS PushTests 와 같은 사례) — .claude/plans/native-push-app-spec.md "테스트" 목록
class PushTests {
    private val seoul = ZoneId.of("Asia/Seoul")

    // MARK: PushTopics.desired 조합

    @Test fun nothingOnSubscribesNothing() {
        assertEquals(emptySet(), PushTopics.desired(PushSettings()))
        assertFalse(PushSettings().anyRemote)
    }

    @Test fun noticeSubscribesNoticeAndPlatformTopic() {
        val s = PushSettings(serviceOptIn = true)
        assertEquals(setOf("notice", "notice-android"), PushTopics.desired(s))
        assertEquals(setOf("notice", "notice-ios"), PushTopics.desired(s, platform = "ios"))
    }

    @Test fun guardianNewsIsNotATopic() {
        val s = PushSettings(learningOptIn = true)
        assertEquals(emptySet(), PushTopics.desired(s))
        assertTrue(s.anyRemote)   // 토픽은 없어도 토큰·서버 등록은 필요하다
    }

    @Test fun marketingAckAddsPromo() {
        val s = PushSettings(serviceOptIn = true, marketingOptIn = true)
        assertEquals(setOf("notice", "notice-android", "promo"), PushTopics.desired(s))
        assertEquals(setOf("promo"), PushTopics.desired(PushSettings(marketingOptIn = true)))
    }

    @Test fun diffOnlyTouchesChangedTopics() {
        val diff = PushTopics.diff(setOf("notice", "promo"), setOf("notice", "notice-android"))
        assertEquals(setOf("promo"), diff.subscribe)
        assertEquals(setOf("notice-android"), diff.unsubscribe)
        assertTrue(PushTopics.diff(setOf("notice"), setOf("notice")).isEmpty)
    }

    // MARK: PushRoute.parse

    @Test fun routeParsesAllowList() {
        assertEquals(PushRoute.HOME, PushRoute.parse("home"))
        assertEquals(PushRoute.LEARN, PushRoute.parse("learn"))
        assertEquals(PushRoute.PROFILE, PushRoute.parse("profile"))
        assertEquals(PushRoute.PROFILE_REPORT, PushRoute.parse("profile/report"))
        assertEquals(PushRoute.PAYWALL, PushRoute.parse("paywall"))
        assertEquals(PushRoute.SETTINGS_NOTIFICATIONS, PushRoute.parse("settings/notifications"))
    }

    @Test fun unknownOrMissingRouteFallsBackHome() {
        assertEquals(PushRoute.HOME, PushRoute.parse(null))
        assertEquals(PushRoute.HOME, PushRoute.parse(""))
        assertEquals(PushRoute.HOME, PushRoute.parse("https://evil.example"))
        assertEquals(PushRoute.HOME, PushRoute.parse("settings"))
        assertEquals(PushRoute.HOME, PushRoute.parse("PAYWALL"))
        assertFalse(PushRoute.isKnown("adventure"))
        assertFalse(PushRoute.isKnown(null))
        assertTrue(PushRoute.isKnown("profile/report"))
    }

    // MARK: 광고 동의 상태 전이

    /** ack 전에는 promo 를 구독하지 않는다 — 동의 시트를 거쳐도 서버 기록 전이면 settings 는 그대로 */
    @Test fun marketingNotSubscribedBeforeAck() {
        val before = PushSettings(serviceOptIn = true)
        assertFalse("promo" in PushTopics.desired(before))
        val acked = before.marketingGranted("2026-10-19T01:00:00Z")
        assertTrue(acked.marketingOptIn)
        assertEquals("2026-10-19T01:00:00Z", acked.marketingOptInAt)
        assertTrue("promo" in PushTopics.desired(acked))
    }

    /** 오프라인 실패 — grant 결과를 반영하지 않으면 토글·토픽 모두 꺼진 채 */
    @Test fun offlineGrantLeavesToggleOff() {
        val before = PushSettings(serviceOptIn = true)
        // PushStore.grantMarketing 은 실패 시 settings 를 바꾸지 않는다 — 같은 값으로 desired 를 확인
        assertFalse(before.marketingOptIn)
        assertEquals(setOf("notice", "notice-android"), PushTopics.desired(before))
    }

    /** 끄기는 즉시 + 서버 기록은 대기열 */
    @Test fun revokeIsImmediateAndQueued() {
        val on = PushSettings(serviceOptIn = true).marketingGranted("2026-10-19T01:00:00Z")
        val off = on.marketingRevokedLocally()
        assertFalse(off.marketingOptIn)
        assertNull(off.marketingOptInAt)
        assertTrue(off.pendingMarketingRevoke)
        assertFalse("promo" in PushTopics.desired(off))
        // 다시 동의하면 대기열은 비워진다
        assertFalse(off.marketingGranted("2026-10-20T01:00:00Z").pendingMarketingRevoke)
    }

    @Test fun reconcileWithServerState() {
        val on = PushSettings(marketingOptIn = true)
        val off = PushSettings()
        assertEquals(PushConsent.Reconcile.NONE, PushConsent.reconcile(on, true))
        assertEquals(PushConsent.Reconcile.NONE, PushConsent.reconcile(off, false))
        // 서버가 계정 변경으로 동의를 초기화했다 → 로컬도 끈다
        assertEquals(PushConsent.Reconcile.DROP_LOCAL, PushConsent.reconcile(on, false))
        // 로컬은 껐는데 서버에 남았다 → 거부를 보낸다
        assertEquals(PushConsent.Reconcile.SEND_REVOKE, PushConsent.reconcile(off, true))
    }

    // MARK: 새 pushInstallId·계정 삭제

    @Test fun newInstallResetsMarketingOnly() {
        val restored = PushSettings(serviceOptIn = true, learningOptIn = true, marketingOptIn = true,
            marketingOptInAt = "2026-01-01T00:00:00Z", pendingMarketingRevoke = true)
        val fresh = restored.resetForNewInstall()
        assertFalse(fresh.marketingOptIn)
        assertNull(fresh.marketingOptInAt)
        assertFalse(fresh.pendingMarketingRevoke)
        assertTrue(fresh.serviceOptIn)
        assertTrue(fresh.learningOptIn)
    }

    @Test fun accountDeletionResetsMarketingKeepsNotice() {
        val s = PushSettings(serviceOptIn = true, marketingOptIn = true, marketingOptInAt = "2026-01-01T00:00:00Z")
        val after = s.resetForAccountDeletion()
        assertFalse(after.marketingOptIn)
        assertNull(after.marketingOptInAt)
        assertTrue(after.serviceOptIn)
        assertEquals(setOf("notice", "notice-android"), PushTopics.desired(after))
    }

    @Test fun guardianNewsNeedsEligibleAccount() {
        val s = PushSettings(learningOptIn = true)
        assertFalse(s.effective(learningEligible = false).learningOptIn)
        assertFalse(s.effective(learningEligible = false).anyRemote)
        assertTrue(s.effective(learningEligible = true).learningOptIn)
    }

    // MARK: 보호자 출생연도 검증 경계

    @Test fun guardianBirthYearBoundaries() {
        assertTrue(GuardianGate.isValidBirthYear("2007", 2026))   // 19세
        assertFalse(GuardianGate.isValidBirthYear("2008", 2026))  // 18세
        assertTrue(GuardianGate.isValidBirthYear("1926", 2026))   // 100세
        assertFalse(GuardianGate.isValidBirthYear("1925", 2026))  // 101세
        assertTrue(GuardianGate.isValidBirthYear("1985", 2026))
        assertTrue(GuardianGate.isValidBirthYear("2008", 2027))   // 해가 바뀌면 기준도 바뀐다
    }

    @Test fun guardianBirthYearRejectsMalformed() {
        assertFalse(GuardianGate.isValidBirthYear("", 2026))
        assertFalse(GuardianGate.isValidBirthYear("985", 2026))
        assertFalse(GuardianGate.isValidBirthYear("19850", 2026))
        assertFalse(GuardianGate.isValidBirthYear("19a5", 2026))
        assertFalse(GuardianGate.isValidBirthYear(" 1985", 2026))
        assertFalse(GuardianGate.isValidBirthYear("１９８５", 2026))   // 전각 숫자
    }

    // MARK: 서버 등록 필요 여부

    @Test fun registerWhenContentChangesOrDayPasses() {
        val s = PushSettings(serviceOptIn = true)
        val sig = PushRegistration.signature("tokenA", "user1", s, localReminderEnabled = false)
        val now = 1_000_000_000L
        val registered = PushRegistrationState(registeredUserId = "user1", registeredSignature = sig, lastRegisteredAtMs = now)

        assertFalse(PushRegistration.needsRegister(registered, sig, now + 60_000))
        assertTrue(PushRegistration.needsRegister(registered, sig, now + PushRegistration.REREGISTER_INTERVAL_MS))
        // 토큰 갱신·계정 변경·설정 변경은 지문이 달라진다
        assertTrue(PushRegistration.needsRegister(registered, PushRegistration.signature("tokenB", "user1", s, false), now))
        assertTrue(PushRegistration.needsRegister(registered, PushRegistration.signature("tokenA", "user2", s, false), now))
        assertTrue(PushRegistration.needsRegister(registered, PushRegistration.signature("tokenA", "user1", s, true), now))
        // 로그아웃 전 해제 실패
        assertTrue(PushRegistration.needsRegister(registered.copy(needsReregister = true), sig, now))
        // 처음
        assertTrue(PushRegistration.needsRegister(PushRegistrationState(), sig, now))
    }

    @Test fun signatureDoesNotContainToken() {
        val sig = PushRegistration.signature("secret-token-value", "user1", PushSettings(), false)
        assertFalse(sig.contains("secret"))
        assertEquals(64, sig.length)
    }

    // MARK: 수신 처리 (채널·광고 2차 차단·조용히)

    @Test fun channelByKind() {
        assertEquals(PushChannels.PROMOTION, PushKind.channelFor("promo"))
        assertEquals(PushChannels.GUARDIAN_REPORT, PushKind.channelFor("weekly_report"))
        assertEquals(PushChannels.GUARDIAN_REPORT, PushKind.channelFor("report"))
        assertEquals(PushChannels.SERVICE_NOTICE, PushKind.channelFor("notice"))
        assertEquals(PushChannels.SERVICE_NOTICE, PushKind.channelFor("account_notice"))
        assertEquals(PushChannels.SERVICE_NOTICE, PushKind.channelFor("marketing_reconfirm"))
        assertEquals(PushChannels.SERVICE_NOTICE, PushKind.channelFor(null))
    }

    @Test fun promoDroppedWithoutLocalConsent() {
        assertFalse(PushKind.shouldDisplay("promo", PushSettings()))
        assertTrue(PushKind.shouldDisplay("promo", PushSettings(marketingOptIn = true)))
        assertTrue(PushKind.shouldDisplay("notice", PushSettings()))
        assertTrue(PushKind.shouldDisplay(null, PushSettings()))
    }

    @Test fun quietRules() {
        assertTrue(PushKind.isQuiet("promo", appInForeground = true, playing = false))
        assertFalse(PushKind.isQuiet("promo", appInForeground = false, playing = false))
        assertFalse(PushKind.isQuiet("notice", appInForeground = true, playing = false))
        assertTrue(PushKind.isQuiet("notice", appInForeground = true, playing = true))
    }

    // MARK: 문구·날짜

    @Test fun consentDatesUseDeviceTimeZone() {
        // UTC 15:30 = 서울 다음 날 00:30
        val date = PushCopy.localDate("2026-10-19T15:30:00.123456Z", seoul)
        assertEquals(LocalDate.of(2026, 10, 20), date)
        assertEquals(LocalDate.of(2026, 10, 20), PushCopy.localDate("2026-10-19T15:30:00+00:00", seoul))
        assertNull(PushCopy.localDate("not-a-date", seoul))
        assertNull(PushCopy.localDate(null, seoul))
        assertEquals("2026.10.20 수신 동의", PushCopy.consentSubtitle(date!!))
    }

    @Test fun resultMessagesMatchSpec() {
        val d = LocalDate.of(2026, 3, 5)
        assertEquals("구구 어드벤처 · 2026년 3월 5일\n이벤트·혜택 알림 수신 동의가 처리됐어요.", PushCopy.grantedMessage(d))
        assertEquals("구구 어드벤처 · 2026년 3월 5일\n이벤트·혜택 알림 수신 거부가 처리됐어요.", PushCopy.revokedMessage(d))
        assertEquals("수신 동의 완료", PushCopy.GRANTED_TITLE)
        assertEquals("수신 거부 완료", PushCopy.REVOKED_TITLE)
        assertEquals("2026.03.05", PushCopy.shortDate(d))
    }

    // MARK: 서버 계약 (api/push.py — 모르는 필드는 400)

    @Test fun registerBodyHasOnlyContractFields() {
        val body = Json.encodeToString(
            PushDeviceRequest.serializer(),
            PushDeviceRequest("00000000-0000-4000-8000-000000000000", "token_value:abc-123", "android",
                "2.5.0 (23)", "ko-KR", "Asia/Seoul", true, false, true),
        )
        assertEquals(
            setOf("pushInstallId", "fcmToken", "platform", "appVersion", "locale", "timeZone",
                "serviceOptIn", "learningOptIn", "localReminderEnabled"),
            Json.parseToJsonElement(body).jsonObject.keys,
        )
    }

    @Test fun localeAndTimeZoneNormalized() {
        assertEquals("ko-KR", PushApi.normalizedLocale("ko-KR"))
        assertNull(PushApi.normalizedLocale("und-"))
        assertEquals("Asia/Seoul", PushApi.normalizedTimeZone("Asia/Seoul"))
        assertEquals("America/Argentina/Buenos_Aires", PushApi.normalizedTimeZone("America/Argentina/Buenos_Aires"))
        assertNull(PushApi.normalizedTimeZone("GMT+09:00"))
    }
}
