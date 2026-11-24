import XCTest
@testable import Gugu

// 원격 푸시 규칙 — 토픽 조합 / 딥링크 / 표시 정책 / 보호자 출생연도 / 동의 문구 날짜 /
// 동의 상태 전이(ack 전 미구독·오프라인 실패·끄기 즉시+대기열·서버 불일치) / 새 설치 식별자 / 계정 삭제 초기화 /
// 시스템 알림 꺼짐 중 철회 / 토픽 순서·시간 제한 / 기존 사용자 1회 안내.
// Firebase·서버는 가짜(FakePushWorld)로 바꿔 끼워 결정적으로 검증한다.

final class PushTests: XCTestCase {

    // MARK: - 토픽

    func testDesiredTopicsAllOffIsEmpty() {
        XCTAssertEqual(PushTopics.desired(PushSettings(), platform: .ios), [])
    }

    func testDesiredTopicsNoticeAddsPlatformTopic() {
        var s = PushSettings()
        s.notice = true
        XCTAssertEqual(PushTopics.desired(s, platform: .ios), ["notice", "notice-ios"])
        XCTAssertEqual(PushTopics.desired(s, platform: .android), ["notice", "notice-android"])
    }

    /// 보호자 학습 소식은 서버 기기별 발송 — 토픽이 아니다
    func testDesiredTopicsGuardianReportIsNotATopic() {
        var s = PushSettings()
        s.guardianReport = true
        XCTAssertEqual(PushTopics.desired(s, platform: .ios), [])
        XCTAssertTrue(s.anyRemote(guardianEligible: true))
        // 기록 보관 계정이 아니면 켜진 것으로 보지 않는다
        XCTAssertFalse(s.anyRemote(guardianEligible: false))
    }

    func testDesiredTopicsMarketingOnlyAfterAck() {
        var s = PushSettings()
        s.notice = true
        XCTAssertFalse(PushTopics.desired(s, platform: .ios).contains("promo"))
        s.acceptMarketingAck(recordedAt: Date())
        XCTAssertEqual(PushTopics.desired(s, platform: .ios), ["notice", "notice-ios", "promo"])
        s.beginMarketingRevoke()
        XCTAssertEqual(PushTopics.desired(s, platform: .ios), ["notice", "notice-ios"])
    }

    func testTopicDiffOnlyChanges() {
        let d = PushTopics.diff(desired: ["notice", "notice-ios"], subscribed: ["notice", "promo"])
        XCTAssertEqual(d.subscribe, ["notice-ios"])
        XCTAssertEqual(d.unsubscribe, ["promo"])
        let none = PushTopics.diff(desired: ["notice"], subscribed: ["notice"])
        XCTAssertTrue(none.subscribe.isEmpty && none.unsubscribe.isEmpty)
    }

    // MARK: - 딥링크

    func testRouteParseAllowList() {
        XCTAssertEqual(PushRoute.parse("home"), .home)
        XCTAssertEqual(PushRoute.parse("learn"), .learn)
        XCTAssertEqual(PushRoute.parse("profile"), .profile)
        XCTAssertEqual(PushRoute.parse("profile/report"), .profileReport)
        XCTAssertEqual(PushRoute.parse("paywall"), .paywall)
        XCTAssertEqual(PushRoute.parse("settings/notifications"), .settingsNotifications)
        XCTAssertEqual(PushRoute.parse(" learn\n"), .learn)
    }

    /// 알림 이동 6종 × 이용권 유무 — 보호자 대상은 3초 문부터(계획서 2-9). 결제 이동은 이용권이 있으면 홈
    func testNavTargetForEveryRoute() {
        for premium in [false, true] {
            XCTAssertEqual(NavTarget.from(.home, isPremium: premium), .tab(.home))
            XCTAssertEqual(NavTarget.from(.learn, isPremium: premium), .tab(.map))
            XCTAssertEqual(NavTarget.from(.profile, isPremium: premium), .guardian(.report))
            XCTAssertEqual(NavTarget.from(.profileReport, isPremium: premium), .guardian(.report))
            XCTAssertEqual(NavTarget.from(.settingsNotifications, isPremium: premium),
                           .guardian(.settings(focus: .notifications)))
        }
        XCTAssertEqual(NavTarget.from(.paywall, isPremium: false), .guardian(.paywall))
        XCTAssertEqual(NavTarget.from(.paywall, isPremium: true), .tab(.home))
        XCTAssertEqual(PushRoute.allCases.count, 6, "이동을 더하면 이 표에도 더할 것")
    }

    func testRouteParseUnknownGoesHome() {
        XCTAssertEqual(PushRoute.parse(nil), .home)
        XCTAssertEqual(PushRoute.parse(""), .home)
        XCTAssertEqual(PushRoute.parse("https://evil.example"), .home)
        XCTAssertEqual(PushRoute.parse("settings"), .home)
        XCTAssertEqual(PushRoute.parse("PROFILE"), .home)
    }

    // MARK: - 표시 정책

    func testKindParseAcceptsSpecAndServerTemplateKeys() {
        XCTAssertEqual(PushKind.parse("promo"), .promo)
        XCTAssertEqual(PushKind.parse("notice"), .notice)
        XCTAssertEqual(PushKind.parse("weekly_report"), .report)
        XCTAssertEqual(PushKind.parse("premium_granted"), .account)
        XCTAssertEqual(PushKind.parse("marketing_reconfirm"), .reconfirm)
        XCTAssertEqual(PushKind.parse(nil), .unknown)
    }

    func testForegroundStyle() {
        XCTAssertEqual(PushForegroundStyle.decide(kind: .promo, immersive: false), .listOnly)
        XCTAssertEqual(PushForegroundStyle.decide(kind: .notice, immersive: false), .full)
        XCTAssertEqual(PushForegroundStyle.decide(kind: .report, immersive: false), .full)
        XCTAssertEqual(PushForegroundStyle.decide(kind: .account, immersive: true), .listOnly)
        XCTAssertEqual(PushForegroundStyle.decide(kind: .unknown, immersive: false), .full)
    }

    // MARK: - 보호자 출생연도

    private let seoul: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return c
    }()

    private func seoulDate(_ y: Int, _ m: Int, _ d: Int, _ h: Int = 12, _ min: Int = 0) -> Date {
        seoul.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!
    }

    func testGuardianBirthYearBoundaries2026() {
        let now = seoulDate(2026, 10, 19)
        func ok(_ t: String) -> Bool { GuardianGate.isValidBirthYear(t, now: now, calendar: seoul) }
        XCTAssertTrue(ok("1926"))   // 100세
        XCTAssertFalse(ok("1925"))  // 101세
        XCTAssertTrue(ok("2007"))   // 19세
        XCTAssertFalse(ok("2008"))  // 18세
        XCTAssertTrue(ok("1985"))
    }

    func testGuardianBirthYearRejectsMalformed() {
        let now = seoulDate(2026, 10, 19)
        for bad in ["", "985", "19850", "19a5", "-985", "１９８５", "1985 1"] {
            XCTAssertFalse(GuardianGate.isValidBirthYear(bad, now: now, calendar: seoul), bad)
        }
    }

    // MARK: - 날짜 문구

    func testConsentDateText() {
        let seoulTZ = TimeZone(identifier: "Asia/Seoul")!
        // UTC 10월 18일 15:30 = 한국 10월 19일 00:30
        let date = try! PushApi.parseServerDate("2026-10-18T15:30:00Z")
        XCTAssertEqual(PushDateText.short(date, timeZone: seoulTZ), "2026.10.19")
        XCTAssertEqual(PushDateText.long(date, timeZone: seoulTZ), "2026년 10월 19일")
        XCTAssertEqual(PushDateText.long(seoulDate(2026, 3, 5), timeZone: seoulTZ), "2026년 3월 5일")
    }

    func testParseServerDateFormats() throws {
        let a = try PushApi.parseServerDate("2026-10-19T03:12:45.123456Z")
        let b = try PushApi.parseServerDate("2026-10-19T03:12:45.123456+00:00")
        let c = try PushApi.parseServerDate("2026-10-19T03:12:45Z")
        XCTAssertEqual(a.timeIntervalSince1970, b.timeIntervalSince1970, accuracy: 0.001)
        XCTAssertEqual(a.timeIntervalSince1970, c.timeIntervalSince1970, accuracy: 1)
        XCTAssertThrowsError(try PushApi.parseServerDate("2026/10/19"))
    }

    // MARK: - 등록 본문

    func testRegisterBodyEncodesServerFieldNamesOnly() throws {
        let body = PushApi.RegisterBody(
            pushInstallId: "id", fcmToken: "t", platform: "ios", appVersion: "2.5.0 (27)",
            locale: nil, timeZone: "Asia/Seoul", serviceOptIn: true, learningOptIn: false, localReminderEnabled: true)
        let json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(body)) as! [String: Any]
        // 서버는 extra="forbid" — 모르는 필드(marketingOptIn 등)를 보내면 400
        XCTAssertEqual(Set(json.keys), ["pushInstallId", "fcmToken", "platform", "appVersion", "timeZone",
                                        "serviceOptIn", "learningOptIn", "localReminderEnabled"])
        XCTAssertEqual(PushApi.localeTag(language: .ko, locale: Locale(identifier: "ko_KR")), "ko-KR")
        // 서버는 이 값으로 알림 언어를 고른다 — 프랑스 기기는 앱이 영어라 en
        XCTAssertEqual(PushApi.localeTag(language: .en, locale: Locale(identifier: "fr_FR")), "en-FR")
        XCTAssertEqual(PushApi.localeTag(language: .ja, locale: Locale(identifier: "ja_JP")), "ja-JP")
        XCTAssertEqual(PushApi.timeZoneId(TimeZone(identifier: "Asia/Seoul")!), "Asia/Seoul")
    }

    // MARK: - 수명주기 (PushStore)

    @MainActor
    func testNewInstallIdResetsRestoredMarketingConsent() {
        let world = FakePushWorld()
        var restored = PushSettings()
        restored.notice = true
        restored.acceptMarketingAck(recordedAt: Date())
        world.settings = restored
        world.install = nil   // 키체인 기록 없음 = 새 설치 또는 다른 기기에서 복원
        let store = PushStore(deps: world.deps())
        XCTAssertFalse(store.settings.marketing)
        XCTAssertNil(store.settings.marketingAt)
        XCTAssertTrue(store.settings.notice, "공지 토글은 유지")
        XCTAssertNotNil(world.install, "새 식별자를 키체인에 저장")
        XCTAssertFalse(world.settings!.marketing, "초기화한 설정을 저장")
    }

    @MainActor
    func testExistingInstallIdKeepsMarketingConsent() {
        let world = FakePushWorld()
        var s = PushSettings()
        s.acceptMarketingAck(recordedAt: Date())
        world.settings = s
        world.install = PushInstall.fresh()
        let store = PushStore(deps: world.deps())
        XCTAssertTrue(store.settings.marketing)
    }

    /// 아무것도 켜지 않았으면 Firebase 를 건드리지 않는다 (토큰 미발급)
    @MainActor
    func testSyncWithEverythingOffTouchesNothing() async {
        let world = FakePushWorld()
        let store = PushStore(deps: world.deps())
        await store.sync(world.ctx)
        XCTAssertEqual(world.log, [])
    }

    @MainActor
    func testNoticeOnRegistersAndSubscribes() async {
        let world = FakePushWorld()
        let store = PushStore(deps: world.deps())
        let outcome = await store.setNotice(true, ctx: world.ctx)
        XCTAssertEqual(outcome, .applied)
        XCTAssertTrue(world.log.contains("token"))
        XCTAssertTrue(world.log.contains("register:service=true"))
        XCTAssertEqual(Set(store.install.subscribedTopics), ["notice", "notice-ios"])
        // 같은 날 다시 맞춰도 서버 등록·구독을 반복하지 않는다
        world.log.removeAll()
        await store.sync(world.ctx)
        XCTAssertEqual(world.log, ["autoInit:true", "token"])
    }

    @MainActor
    func testNoticeDeniedBySystemStaysOff() async {
        let world = FakePushWorld()
        world.authorized = false
        let store = PushStore(deps: world.deps())
        let outcome = await store.setNotice(true, ctx: world.ctx)
        XCTAssertEqual(outcome, .permissionDenied)
        XCTAssertFalse(store.settings.notice)
        XCTAssertTrue(store.deniedBySystem)
        XCTAssertFalse(world.log.contains("token"))
    }

    /// 광고는 서버 ack 뒤에만 promo 를 구독한다
    @MainActor
    func testMarketingGrantSubscribesPromoOnlyAfterAck() async {
        let world = FakePushWorld()
        let store = PushStore(deps: world.deps())
        let outcome = await store.grantMarketing(ctx: world.ctx)
        XCTAssertEqual(outcome, .granted(world.recordedAt))
        XCTAssertTrue(store.settings.marketing)
        XCTAssertEqual(store.settings.marketingAt, world.recordedAt)
        let consent = world.log.firstIndex(of: "consent:true")
        let promo = world.log.firstIndex(of: "subscribe:promo")
        XCTAssertNotNil(consent)
        XCTAssertNotNil(promo)
        XCTAssertLessThan(consent!, promo!)
        // 동의 전에 기기 등록을 먼저 한다 (행이 있어야 동의를 기록한다)
        XCTAssertLessThan(world.log.firstIndex(where: { $0.hasPrefix("register") })!, consent!)
    }

    /// 오프라인 — 토글은 꺼진 채, promo 미구독, 다른 항목이 없으면 방금 만든 토큰도 정리
    @MainActor
    func testMarketingGrantOfflineFailsAndStaysOff() async {
        let world = FakePushWorld()
        world.consentError = LearningApi.ApiError.offline(underlying: "offline")
        let store = PushStore(deps: world.deps())
        let outcome = await store.grantMarketing(ctx: world.ctx)
        XCTAssertEqual(outcome, .failed)
        XCTAssertFalse(store.settings.marketing)
        XCTAssertFalse(world.log.contains("subscribe:promo"))
        XCTAssertTrue(world.log.contains("deleteToken"))
        XCTAssertTrue(store.install.subscribedTopics.isEmpty)
    }

    /// 동의 요청이 404(기기 행 없음)면 다시 등록하고 한 번 더 보낸다
    @MainActor
    func testMarketingGrantRetriesAfterNotFound() async {
        let world = FakePushWorld()
        world.consentNotFoundOnce = true
        let store = PushStore(deps: world.deps())
        let outcome = await store.grantMarketing(ctx: world.ctx)
        XCTAssertEqual(outcome, .granted(world.recordedAt))
        XCTAssertEqual(world.log.filter { $0 == "consent:true" }.count, 2)
        XCTAssertEqual(world.log.filter { $0.hasPrefix("register") }.count, 2)
    }

    /// 끄기 — 즉시 꺼지고 promo 해제. 서버 실패는 대기열에 남았다가 다음 sync 에서 보낸다
    @MainActor
    func testMarketingRevokeImmediateThenQueued() async {
        let world = FakePushWorld()
        let store = PushStore(deps: world.deps())
        _ = await store.setNotice(true, ctx: world.ctx)
        _ = await store.grantMarketing(ctx: world.ctx)
        XCTAssertTrue(store.install.subscribedTopics.contains("promo"))

        world.consentError = LearningApi.ApiError.offline(underlying: "offline")
        world.log.removeAll()
        let shown = await store.revokeMarketing(ctx: world.ctx)
        XCTAssertFalse(store.settings.marketing)
        XCTAssertTrue(store.settings.pendingMarketingRevoke)
        XCTAssertEqual(shown, world.now, "서버 실패면 결과창 날짜는 기기 날짜")
        XCTAssertTrue(world.log.contains("unsubscribe:promo"))
        XCTAssertFalse(store.install.subscribedTopics.contains("promo"))

        world.consentError = nil
        await store.sync(world.ctx)
        XCTAssertFalse(store.settings.pendingMarketingRevoke)
        XCTAssertFalse(world.serverMarketing)
        XCTAssertFalse(store.settings.marketing)
    }

    @MainActor
    func testMarketingRevokeSuccessUsesServerDate() async {
        let world = FakePushWorld()
        let store = PushStore(deps: world.deps())
        _ = await store.setNotice(true, ctx: world.ctx)
        _ = await store.grantMarketing(ctx: world.ctx)
        let shown = await store.revokeMarketing(ctx: world.ctx)
        XCTAssertEqual(shown, world.recordedAt)
        XCTAssertFalse(store.settings.pendingMarketingRevoke)
    }

    /// 대기 중인 거부가 있어도 서버 등록 응답이 광고를 다시 켜지 않는다
    @MainActor
    func testPendingRevokeIsNotOverriddenByServerState() async {
        let world = FakePushWorld()
        let store = PushStore(deps: world.deps())
        _ = await store.setNotice(true, ctx: world.ctx)
        _ = await store.grantMarketing(ctx: world.ctx)
        world.consentError = LearningApi.ApiError.offline(underlying: "offline")
        _ = await store.revokeMarketing(ctx: world.ctx)
        // 다음 날 재등록 — 서버는 아직 true 라고 답한다
        world.now = world.now.addingTimeInterval(86_400 * 2)
        await store.sync(world.ctx)
        XCTAssertFalse(store.settings.marketing)
        XCTAssertFalse(store.install.subscribedTopics.contains("promo"))
    }

    /// 서버 켜짐 · 기기 꺼짐 — 조용히 켜지 않고 서버에 철회를 보낸다. 기기는 끈 채
    @MainActor
    func testServerMarketingOnDeviceOffSendsRevoke() async {
        let world = FakePushWorld()
        world.serverMarketing = true
        world.serverMarketingAt = world.recordedAt
        let store = PushStore(deps: world.deps())
        _ = await store.setNotice(true, ctx: world.ctx)
        XCTAssertFalse(store.settings.marketing, "서버 값으로 켜지 않는다")
        XCTAssertNil(store.settings.marketingAt)
        XCTAssertTrue(world.log.contains("consent:false"))
        XCTAssertLessThan(world.log.firstIndex(where: { $0.hasPrefix("register") })!,
                          world.log.firstIndex(of: "consent:false")!)
        XCTAssertFalse(store.settings.pendingMarketingRevoke)
        XCTAssertFalse(world.serverMarketing)
        XCTAssertFalse(world.log.contains("subscribe:promo"))
    }

    /// 서버 켜짐 · 기기 꺼짐 + 철회 전송 실패 — 대기열에 남고, 다음 sync 에서 보낸다. 그 사이에도 켜지 않는다
    @MainActor
    func testServerMarketingOnDeviceOffQueuesRevokeWhenOffline() async {
        let world = FakePushWorld()
        world.serverMarketing = true
        world.consentError = LearningApi.ApiError.offline(underlying: "offline")
        let store = PushStore(deps: world.deps())
        _ = await store.setNotice(true, ctx: world.ctx)
        XCTAssertFalse(store.settings.marketing)
        XCTAssertTrue(store.settings.pendingMarketingRevoke)
        XCTAssertFalse(store.install.subscribedTopics.contains("promo"))

        world.consentError = nil
        world.log.removeAll()
        await store.sync(world.ctx)
        XCTAssertEqual(world.log.filter { $0 == "consent:false" }.count, 1)
        XCTAssertFalse(store.settings.pendingMarketingRevoke)
        XCTAssertFalse(store.settings.marketing)
        XCTAssertFalse(world.serverMarketing)
    }

    /// 광고 켜기 직전 서버에 동의가 남아 있으면(기기 꺼짐) 철회를 먼저 보낸 뒤 새로 동의를 기록한다
    @MainActor
    func testGrantSendsMismatchRevokeFirst() async {
        let world = FakePushWorld()
        world.serverMarketing = true
        let store = PushStore(deps: world.deps())
        let outcome = await store.grantMarketing(ctx: world.ctx)
        XCTAssertEqual(outcome, .granted(world.recordedAt))
        let revoke = world.log.firstIndex(of: "consent:false")
        let grant = world.log.firstIndex(of: "consent:true")
        XCTAssertNotNil(revoke)
        XCTAssertNotNil(grant)
        XCTAssertLessThan(revoke!, grant!)
        XCTAssertTrue(store.settings.marketing)
    }

    /// 시스템 알림이 꺼져 있어도 대기 중인 철회를 보내고 남은 promo 구독을 푼다 (새 토큰은 만들지 않는다)
    @MainActor
    func testDeniedBySystemStillSendsPendingRevokeAndReleasesPromo() async {
        let world = FakePushWorld()
        var s = PushSettings()
        s.notice = true
        s.beginMarketingRevoke()
        world.settings = s
        var install = PushInstall.fresh()
        install.subscribedTopics = ["notice", "notice-ios", "promo"]
        install.topicsTokenHash = PushStore.fingerprint(world.tokenValue)
        install.registeredAt = world.now
        world.install = install
        world.authorized = false
        let store = PushStore(deps: world.deps())
        await store.sync(world.ctx)
        XCTAssertTrue(store.deniedBySystem)
        XCTAssertEqual(world.log, ["consent:false", "unsubscribe:promo"])
        XCTAssertFalse(store.settings.pendingMarketingRevoke)
        XCTAssertEqual(Set(store.install.subscribedTopics), ["notice", "notice-ios"], "공지 구독은 그대로")
    }

    /// 새 설치 식별자(키체인 기록 없음·해석 실패) — 남은 FCM 토큰의 promo 구독을 첫 sync 에서 한 번만 푼다
    @MainActor
    func testFreshInstallReleasesPromoOnce() async {
        let world = FakePushWorld()
        world.install = nil
        world.hasFCMToken = true
        let store = PushStore(deps: world.deps())
        XCTAssertTrue(world.install!.promoReleasePending)
        await store.sync(world.ctx)
        XCTAssertEqual(world.log, ["unsubscribe:promo"])
        XCTAssertFalse(world.install!.promoReleasePending)
        world.log.removeAll()
        await store.sync(world.ctx)
        XCTAssertEqual(world.log, [], "두 번째부터는 부르지 않는다")
    }

    /// 토큰이 없으면 풀 구독도 없다 — Firebase 를 부르지 않고 표시만 지운다
    @MainActor
    func testFreshInstallWithoutTokenSkipsPromoRelease() async {
        let world = FakePushWorld()
        world.install = nil
        let store = PushStore(deps: world.deps())
        await store.sync(world.ctx)
        XCTAssertEqual(world.log, [])
        XCTAssertFalse(world.install!.promoReleasePending)
        XCTAssertFalse(store.install.promoReleasePending)
    }

    /// promo 해제가 실패하면 다음 sync 에서 다시 (성공할 때까지 1회분)
    @MainActor
    func testFreshInstallPromoReleaseRetriesAfterTimeout() async {
        let world = FakePushWorld()
        world.install = nil
        world.hasFCMToken = true
        world.hangingTopics = ["promo"]
        world.topicTimeout = .milliseconds(50)
        let store = PushStore(deps: world.deps())
        await store.sync(world.ctx)
        XCTAssertTrue(store.install.promoReleasePending)
        world.hangingTopics = []
        world.log.removeAll()
        await store.sync(world.ctx)
        XCTAssertEqual(world.log, ["unsubscribe:promo"])
        XCTAssertFalse(store.install.promoReleasePending)
    }

    /// 토픽 동기화 — 해제 먼저, 구독 나중
    @MainActor
    func testTopicSyncUnsubscribesBeforeSubscribing() async {
        let world = FakePushWorld()
        var s = PushSettings()
        s.notice = true
        s.beginMarketingRevoke()
        world.settings = s
        var install = PushInstall.fresh()
        install.subscribedTopics = ["promo"]
        install.topicsTokenHash = PushStore.fingerprint(world.tokenValue)
        world.install = install
        let store = PushStore(deps: world.deps())
        await store.sync(world.ctx)
        let unsub = world.log.firstIndex(of: "unsubscribe:promo")!
        XCTAssertLessThan(unsub, world.log.firstIndex(of: "subscribe:notice")!)
        XCTAssertLessThan(unsub, world.log.firstIndex(of: "subscribe:notice-ios")!)
        XCTAssertEqual(Set(store.install.subscribedTopics), ["notice", "notice-ios"])
    }

    /// 토픽 호출이 응답하지 않으면 시간 제한 뒤 실패로 보고 넘어간다 — 다음 sync 에서 다시 구독
    @MainActor
    func testTopicCallTimesOutAndRetriesNextSync() async {
        let world = FakePushWorld()
        world.hangingTopics = ["notice-ios"]
        world.topicTimeout = .milliseconds(50)
        let store = PushStore(deps: world.deps())
        let outcome = await store.setNotice(true, ctx: world.ctx)
        XCTAssertEqual(outcome, .applied)
        XCTAssertEqual(store.install.subscribedTopics, ["notice"])
        world.hangingTopics = []
        world.log.removeAll()
        await store.sync(world.ctx)
        XCTAssertEqual(world.log.filter { $0.hasPrefix("subscribe") }, ["subscribe:notice-ios"])
        XCTAssertEqual(Set(store.install.subscribedTopics), ["notice", "notice-ios"])
    }

    // MARK: - 기존 사용자 1회 안내

    @MainActor
    func testNoticeHintShownUntilDismissed() {
        let world = FakePushWorld()
        let store = PushStore(deps: world.deps())
        XCTAssertTrue(store.settings.showsNoticeHint)
        store.dismissNoticeHint()
        XCTAssertFalse(store.settings.showsNoticeHint)
        XCTAssertTrue(world.settings!.noticeHintDismissed, "기기에 저장")
        // 다시 만들어도(앱 재시작) 보이지 않는다
        XCTAssertFalse(PushStore(deps: world.deps()).settings.showsNoticeHint)
    }

    @MainActor
    func testNoticeOnDismissesHintForGood() async {
        let world = FakePushWorld()
        let store = PushStore(deps: world.deps())
        _ = await store.setNotice(true, ctx: world.ctx)
        XCTAssertTrue(store.settings.noticeHintDismissed)
        _ = await store.setNotice(false, ctx: world.ctx)
        XCTAssertFalse(store.settings.showsNoticeHint, "공지를 다시 꺼도 안내는 다시 안 보인다")
    }

    /// 권한 거부로 공지가 켜지지 않았으면 안내는 남는다
    @MainActor
    func testNoticeDeniedKeepsHint() async {
        let world = FakePushWorld()
        world.authorized = false
        let store = PushStore(deps: world.deps())
        _ = await store.setNotice(true, ctx: world.ctx)
        XCTAssertTrue(store.settings.showsNoticeHint)
    }

    // MARK: - 저장본 호환

    /// 필드가 늘기 전 저장본도 읽는다 (못 읽으면 설정이 초기화된다)
    func testDecodesStoredRecordsWithoutNewFields() throws {
        let oldSettings = Data(#"{"notice":false,"guardianReport":true,"marketing":false,"pendingMarketingRevoke":true}"#.utf8)
        let s = try JSONDecoder().decode(PushSettings.self, from: oldSettings)
        XCTAssertTrue(s.guardianReport)
        XCTAssertTrue(s.pendingMarketingRevoke)
        XCTAssertFalse(s.noticeHintDismissed)
        XCTAssertTrue(s.showsNoticeHint)
        let noticeOn = try JSONDecoder().decode(PushSettings.self, from: Data(#"{"notice":true}"#.utf8))
        XCTAssertTrue(noticeOn.noticeHintDismissed, "이미 공지를 켠 사용자에게는 안내를 띄우지 않는다")

        let oldInstall = Data(#"{"pushInstallId":"abc","subscribedTopics":["notice"],"needsReregister":false}"#.utf8)
        let i = try JSONDecoder().decode(PushInstall.self, from: oldInstall)
        XCTAssertEqual(i.pushInstallId, "abc")
        XCTAssertEqual(i.subscribedTopics, ["notice"])
        XCTAssertFalse(i.promoReleasePending)
        XCTAssertThrowsError(try JSONDecoder().decode(PushInstall.self, from: Data("{}".utf8)), "식별자 없는 기록은 거부")

        // 저장 → 읽기 왕복
        var full = PushSettings()
        full.acceptMarketingAck(recordedAt: Date(timeIntervalSince1970: 1_792_400_000))
        full.noticeHintDismissed = true
        XCTAssertEqual(try JSONDecoder().decode(PushSettings.self, from: JSONEncoder().encode(full)), full)
    }

    /// 서버 기준 광고 동의가 꺼져 있으면(계정 변경·재확인 만료) 기기도 끄고 promo 를 해제한다
    @MainActor
    func testServerMarketingOffTurnsDeviceOff() async {
        let world = FakePushWorld()
        let store = PushStore(deps: world.deps())
        _ = await store.setNotice(true, ctx: world.ctx)
        _ = await store.grantMarketing(ctx: world.ctx)
        world.serverMarketing = false
        var other = world.ctx
        other.accountKey = "u2|false"
        await store.sync(other)
        XCTAssertFalse(store.settings.marketing)
        XCTAssertFalse(store.install.subscribedTopics.contains("promo"))
        XCTAssertTrue(store.install.subscribedTopics.contains("notice"))
    }

    /// 계정 삭제 — 광고 동의 초기화·promo 해제, 공지는 유지하고 새 계정으로 다시 등록
    @MainActor
    func testAccountDeletionResetsMarketingKeepsNotice() async {
        let world = FakePushWorld()
        let store = PushStore(deps: world.deps())
        _ = await store.setNotice(true, ctx: world.ctx)
        _ = await store.grantMarketing(ctx: world.ctx)
        store.resetForAccountDeletion()
        XCTAssertFalse(store.settings.marketing)
        XCTAssertNil(store.settings.marketingAt)
        XCTAssertTrue(store.settings.notice)
        XCTAssertNil(store.install.registeredAt)
        XCTAssertTrue(store.install.needsReregister)

        world.serverMarketing = false   // purge 로 행이 사라졌다
        world.log.removeAll()
        var fresh = world.ctx
        fresh.accountKey = "anon2|false"
        await store.sync(fresh)
        XCTAssertTrue(world.log.contains("unsubscribe:promo"))
        XCTAssertTrue(world.log.contains("register:service=true"))
        XCTAssertFalse(world.log.contains("consent:false"), "purge 가 지웠으니 거부를 따로 보내지 않는다")
        XCTAssertEqual(Set(store.install.subscribedTopics), ["notice", "notice-ios"])
    }

    /// 원격 항목을 모두 끄면 토픽 해제 → 서버 해제 → 토큰 삭제 (자동 초기화도 끈다)
    @MainActor
    func testAllOffTearsDownInOrder() async {
        let world = FakePushWorld()
        let store = PushStore(deps: world.deps())
        _ = await store.setNotice(true, ctx: world.ctx)
        world.log.removeAll()
        _ = await store.setNotice(false, ctx: world.ctx)
        let unsub = world.log.firstIndex(of: "unsubscribe:notice")!
        let unregister = world.log.firstIndex(of: "unregister")!
        let delete = world.log.firstIndex(of: "deleteToken")!
        XCTAssertLessThan(world.log.firstIndex(of: "autoInit:false")!, delete)
        XCTAssertLessThan(unsub, unregister)
        XCTAssertLessThan(unregister, delete)
        XCTAssertNil(store.install.registeredAt)
        XCTAssertNil(store.install.topicsTokenHash)
        // 정리 뒤에는 다시 아무것도 하지 않는다
        world.log.removeAll()
        await store.sync(world.ctx)
        XCTAssertEqual(world.log, [])
    }

    /// 로그아웃 직전 — 지금 계정 토큰으로 해제하고, 새 계정으로 다시 등록하도록 표시
    @MainActor
    func testUnregisterBeforeSignOut() async {
        let world = FakePushWorld()
        let store = PushStore(deps: world.deps())
        _ = await store.setNotice(true, ctx: world.ctx)
        world.log.removeAll()
        await store.unregisterBeforeSignOut(accessToken: { "old-jwt" })
        XCTAssertEqual(world.log, ["unregister"])
        XCTAssertEqual(world.lastJWT, "old-jwt")
        XCTAssertNil(store.install.registeredAt)
        XCTAssertTrue(store.install.needsReregister)
    }

    /// 로그아웃 해제 실패(오프라인) — 로그아웃은 계속, 다음 등록 때 서버가 정리
    @MainActor
    func testUnregisterBeforeSignOutFailureMarksReregister() async {
        let world = FakePushWorld()
        let store = PushStore(deps: world.deps())
        _ = await store.setNotice(true, ctx: world.ctx)
        world.unregisterError = LearningApi.ApiError.offline(underlying: "offline")
        await store.unregisterBeforeSignOut(accessToken: { "old-jwt" })
        XCTAssertNotNil(store.install.registeredAt)
        XCTAssertTrue(store.install.needsReregister)
    }

    /// 토큰이 바뀌면 같은 토픽도 새로 구독한다
    @MainActor
    func testTokenChangeResubscribes() async {
        let world = FakePushWorld()
        let store = PushStore(deps: world.deps())
        _ = await store.setNotice(true, ctx: world.ctx)
        world.tokenValue = "fcm-token-BBBBBBBBBBBBBBBB"
        world.log.removeAll()
        await store.sync(world.ctx)
        XCTAssertTrue(world.log.contains("register:service=true"))
        XCTAssertTrue(world.log.contains("subscribe:notice"))
        XCTAssertTrue(world.log.contains("subscribe:notice-ios"))
    }
}

// MARK: - 가짜 Firebase·서버·저장소

@MainActor
final class FakePushWorld {
    var log: [String] = []
    var authorized = true
    var tokenValue = "fcm-token-AAAAAAAAAAAAAAAA"
    var consentError: Error?
    var consentNotFoundOnce = false
    var unregisterError: Error?
    var serverMarketing = false
    var serverMarketingAt: Date?
    var install: PushInstall? = PushInstall.fresh()
    var settings: PushSettings?
    var now = Date(timeIntervalSince1970: 1_792_400_000)
    let recordedAt = Date(timeIntervalSince1970: 1_792_400_123)
    var lastJWT: String?
    /// 이 기기에 FCM 토큰이 있는지 (token() 이 만들고 deleteToken() 이 지운다)
    var hasFCMToken = false
    /// 응답하지 않는 토픽 — 시간 제한 검증용
    var hangingTopics: Set<String> = []
    var topicTimeout: Duration = .seconds(10)

    var ctx: PushStore.Context {
        PushStore.Context(accountKey: "u1|false", guardianEligible: false, localReminderEnabled: false,
                          accessToken: { "jwt" })
    }

    func deps() -> PushStore.Dependencies {
        PushStore.Dependencies(
            isAvailable: { true },
            isAuthorized: { self.authorized },
            requestAuthorization: { self.authorized },
            setAutoInit: { self.log.append("autoInit:\($0)") },
            token: { self.log.append("token"); self.hasFCMToken = true; return self.tokenValue },
            subscribe: { topic in
                if self.hangingTopics.contains(topic) { try await Task.sleep(for: .seconds(60)) }
                self.log.append("subscribe:\(topic)")
            },
            unsubscribe: { topic in
                if self.hangingTopics.contains(topic) { try await Task.sleep(for: .seconds(60)) }
                self.log.append("unsubscribe:\(topic)")
            },
            deleteToken: { self.log.append("deleteToken"); self.hasFCMToken = false },
            hasToken: { self.hasFCMToken },
            register: { jwt, body in
                self.lastJWT = jwt
                self.log.append("register:service=\(body.serviceOptIn)")
                return PushApi.DeviceState(marketingOptIn: self.serverMarketing, marketingOptInAt: self.serverMarketingAt)
            },
            setConsent: { jwt, _, granted in
                self.lastJWT = jwt
                self.log.append("consent:\(granted)")
                if self.consentNotFoundOnce {
                    self.consentNotFoundOnce = false
                    throw LearningApi.ApiError.http(status: 404, code: "PUSH_DEVICE_NOT_FOUND", message: "")
                }
                if let error = self.consentError { throw error }
                self.serverMarketing = granted
                self.serverMarketingAt = granted ? self.recordedAt : nil
                return PushApi.ConsentResult(granted: granted, recordedAt: self.recordedAt, sender: "구구 어드벤처")
            },
            unregister: { jwt, _, _ in
                self.lastJWT = jwt
                self.log.append("unregister")
                if let error = self.unregisterError { throw error }
                self.serverMarketing = false
            },
            loadInstall: { self.install },
            saveInstall: { self.install = $0 },
            loadSettings: { self.settings },
            saveSettings: { self.settings = $0 },
            appVersion: { "2.5.0 (27)" },
            now: { self.now },
            topicTimeout: topicTimeout)
    }
}

/// 광고성 알림 토글은 한국 스토어에서만 — 이미 켠 사람은 끌 수 있게 계속 보인다
final class PromoVisibilityTests: XCTestCase {
    /// 공지 토픽 — 한국어는 기존 이름 유지(구버전 기기와 같은 토픽), 다른 언어는 언어별 토픽
    func testNoticeTopicsPerLanguage() {
        XCTAssertEqual(PushTopics.noticeTopics(platform: .ios, language: .ko), ["notice", "notice-ios"])
        XCTAssertEqual(PushTopics.noticeTopics(platform: .ios, language: .en), ["notice-en", "notice-en-ios"])
        XCTAssertEqual(PushTopics.noticeTopics(platform: .ios, language: .ja), ["notice-ja", "notice-ja-ios"])
    }

    func testShownOnlyInKoreanStorefront() {
        XCTAssertTrue(PromoVisibility.showsToggle(storefrontCountry: "KOR", marketingOn: false))
        XCTAssertFalse(PromoVisibility.showsToggle(storefrontCountry: "USA", marketingOn: false))
        XCTAssertFalse(PromoVisibility.showsToggle(storefrontCountry: "JPN", marketingOn: false))
        XCTAssertFalse(PromoVisibility.showsToggle(storefrontCountry: nil, marketingOn: false))
    }

    func testStaysVisibleWhileOptedInSoUserCanRevoke() {
        XCTAssertTrue(PromoVisibility.showsToggle(storefrontCountry: "USA", marketingOn: true))
        XCTAssertTrue(PromoVisibility.showsToggle(storefrontCountry: nil, marketingOn: true))
    }
}
