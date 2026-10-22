import Foundation
import Observation
import CryptoKit

// 원격 푸시 수명주기 — 토큰·토픽·서버 등록·광고 동의를 맞춘다.
//
// 원칙
// - 원격 항목(공지·보호자 소식·광고)을 하나도 켜지 않았으면 토큰을 만들지 않는다. 모두 끄면
//   토픽 해제 → 서버 해제 → 토큰 삭제 순으로 지운다.
// - 광고는 서버가 동의를 기록(ack)한 뒤에만 켜고 promo 를 구독한다. 끌 때는 즉시 끄고 서버 기록은 대기열에 둔다.
//   서버에만 동의가 남아 있으면(기기는 꺼짐) 조용히 켜지 않고 서버에 철회를 보낸다. 철회는 시스템 알림이 꺼져 있어도 미루지 않는다.
// - 알림 거부·Firebase·서버 실패는 앱 기능을 막지 않는다 — 로그를 남기고 다음 실행(시작·복귀)에 다시 맞춘다.
// - 설치 식별자·등록 상태는 키체인(ThisDeviceOnly) — 기기 백업으로 옮겨지지 않아, 복원된 기기에서는
//   새 식별자를 만들고 광고 동의를 초기화한다. 설정은 Persistence gugu.push.v1.

@MainActor
@Observable
final class PushStore {
    /// 호출 시점의 계정·학습 알림 상태 — RootView·화면이 만들어 넘긴다 (스토어끼리 직접 묶지 않는다)
    struct Context {
        var accountKey: String
        /// 보호자 학습 소식을 받을 수 있는 계정인지 (이메일 계정 + 이용권 = 기록 보관)
        var guardianEligible: Bool
        var localReminderEnabled: Bool
        var accessToken: @MainActor () async -> String?
    }

    enum ToggleOutcome: Equatable {
        case applied
        /// 시스템 알림 권한이 꺼져 있다 — 설정 앱으로 안내한다
        case permissionDenied
        /// Firebase 구성 없음(구성 파일이 없는 디버그 빌드)
        case unavailable
    }

    enum ConsentOutcome: Equatable {
        case granted(Date)
        case permissionDenied
        /// 오프라인·서버 실패 — 토글은 꺼진 채 둔다
        case failed
    }

    /// 바깥 세계(Firebase·서버·저장소·권한) — 테스트에서 바꿔 끼운다
    struct Dependencies {
        var isAvailable: @MainActor () -> Bool
        var isAuthorized: @MainActor () async -> Bool
        var requestAuthorization: @MainActor () async -> Bool
        var setAutoInit: @MainActor (Bool) -> Void
        var token: @MainActor () async throws -> String
        var subscribe: @MainActor (String) async throws -> Void
        var unsubscribe: @MainActor (String) async throws -> Void
        var deleteToken: @MainActor () async throws -> Void
        /// 이 기기에 FCM 토큰이 이미 있는지 (새로 만들지 않는다)
        var hasToken: @MainActor () -> Bool
        var register: @MainActor (_ accessToken: String, _ body: PushApi.RegisterBody) async throws -> PushApi.DeviceState
        var setConsent: @MainActor (_ accessToken: String, _ pushInstallId: String, _ granted: Bool) async throws -> PushApi.ConsentResult
        var unregister: @MainActor (_ accessToken: String, _ pushInstallId: String, _ timeout: TimeInterval) async throws -> Void
        var loadInstall: @MainActor () -> PushInstall?
        var saveInstall: @MainActor (PushInstall) -> Void
        var loadSettings: @MainActor () -> PushSettings?
        var saveSettings: @MainActor (PushSettings) -> Void
        var appVersion: @MainActor () -> String
        var now: @MainActor () -> Date
        /// 토픽 구독·해제 한 번의 시간 제한 — Firebase 가 답하지 않아도 동기화가 멈추지 않게
        var topicTimeout: Duration = .seconds(10)
    }

    enum PushStoreError: LocalizedError {
        case topicTimeout

        var errorDescription: String? {
            switch self {
            case .topicTimeout: return "토픽 요청 시간 초과"  // i18n:skip
            }
        }
    }

    static let installKey = "gugu.push.install.v1"
    /// 로그아웃 직전 서버 해제는 이만큼만 기다린다 — 오프라인에서도 로그아웃이 바로 끝나야 한다
    static let signOutUnregisterTimeout: Duration = .seconds(5)

    private(set) var settings: PushSettings
    private(set) var install: PushInstall
    /// 원격 항목을 켰지만 시스템 설정에서 알림이 꺼져 있다 — 학습 알림과 같은 경고를 띄운다
    private(set) var deniedBySystem = false

    private let deps: Dependencies
    /// 동기화·동의·해제가 겹치면 구독·등록 순서가 섞인다 → 한 번에 하나씩
    private var tail: Task<Void, Never>?

    var isAvailable: Bool { deps.isAvailable() }

    convenience init() {
        self.init(deps: .live())
    }

    init(deps: Dependencies) {
        self.deps = deps
        var settings = deps.loadSettings() ?? PushSettings()
        if let install = deps.loadInstall() {
            self.install = install
        } else {
            // 새 설치 식별자 — 새 설치이거나 다른 기기에서 복원됐거나 키체인 기록을 못 읽었다.
            // 예전 광고 동의는 이 기기에 넘기지 않고, 남아 있을지 모르는 promo 구독은 첫 sync 에서 한 번 푼다
            var fresh = PushInstall.fresh()
            fresh.promoReleasePending = true
            deps.saveInstall(fresh)
            self.install = fresh
            if settings.marketing || settings.pendingMarketingRevoke {
                print("[Push] 새 설치 식별자 — 복원된 광고 동의를 초기화")
            }
            settings.resetMarketing()
            deps.saveSettings(settings)
        }
        self.settings = settings
    }

    // MARK: - 수명주기

    /// 앱 시작·복귀·계정 변경·토큰 갱신·설정 변경 때 — 토큰 확보, 서버 등록(하루 1회·변경 시), 대기 중 거부 처리, 토픽 맞춤
    func sync(_ ctx: Context) async {
        await serialized { await self.runSync(ctx) }
    }

    func setNotice(_ on: Bool, ctx: Context) async -> ToggleOutcome {
        let outcome = await setRemote(\.notice, on, ctx: ctx)
        // 공지를 켰으면 1회 안내는 할 일을 다 했다
        if on, outcome == .applied { dismissNoticeHint() }
        return outcome
    }

    /// 설정 알림 섹션의 기존 사용자 1회 안내를 닫는다 — 다시 보이지 않는다(기기 저장)
    func dismissNoticeHint() {
        guard !settings.noticeHintDismissed else { return }
        settings.noticeHintDismissed = true
        saveSettings()
    }

    func setGuardianReport(_ on: Bool, ctx: Context) async -> ToggleOutcome {
        await setRemote(\.guardianReport, on, ctx: ctx)
    }

    /// 광고 수신 동의 — 보호자 확인·동의 시트를 거친 뒤 부른다. 서버 ack 를 받아야만 켠다
    func grantMarketing(ctx: Context) async -> ConsentOutcome {
        guard deps.isAvailable() else { return .failed }
        guard await deps.requestAuthorization() else {
            deniedBySystem = true
            return .permissionDenied
        }
        deniedBySystem = false
        return await serialized {
            let outcome = await self.runGrant(ctx)
            // 실패했고 다른 원격 항목도 없으면 방금 만든 토큰·등록을 바로 정리한다
            if outcome == .failed { await self.runSync(ctx) }
            return outcome
        }
    }

    /// 광고 수신 거부 — 즉시 끄고 promo 를 해제한다. 서버 기록 실패는 대기열(다음 실행마다 재시도).
    /// 처리 결과 알림창에 쓸 날짜를 돌려준다 (서버 기록 시각, 실패하면 기기 날짜)
    func revokeMarketing(ctx: Context) async -> Date {
        settings.beginMarketingRevoke()
        saveSettings()
        let fallback = deps.now()
        guard deps.isAvailable() else { return fallback }
        return await serialized {
            await self.syncTopics()
            let recordedAt = await self.sendPendingRevoke(ctx)
            await self.runSync(ctx)
            return recordedAt ?? fallback
        }
    }

    /// 로그아웃 직전 — 세션을 지우기 **전에** 지금 계정의 토큰으로 서버 등록을 해제한다(최대 5초).
    /// 실패해도 로그아웃은 계속되고, 새 계정으로 다시 등록할 때 서버가 예전 행을 정리한다.
    func unregisterBeforeSignOut(accessToken: @escaping @MainActor () async -> String?) async {
        guard deps.isAvailable(), install.registeredAt != nil else { return }
        let work = Task { @MainActor in
            guard let jwt = await accessToken() else {
                self.install.needsReregister = true
                self.saveInstall()
                return
            }
            await self.serialized { await self.runUnregister(jwt) }
        }
        let finished = await Self.waitAtMost(Self.signOutUnregisterTimeout, for: work)
        if !finished {
            work.cancel()
            install.needsReregister = true
            saveInstall()
            print("[Push] 로그아웃 전 기기 해제 5초 초과 — 다음 등록 때 다시 맞춘다")
        }
    }

    /// 다른 계정으로 바뀜(로그아웃 후 새 익명·다른 이메일 로그인) — 광고 동의는 사람·계정에 묶이므로 끄고,
    /// 새 계정으로 다시 등록하게 한다. 공지·보호자 토글은 유지한다. 토픽 해제는 이어지는 sync 가 한다.
    func resetForAccountSwitch() {
        settings.resetMarketing()
        saveSettings()
        install.needsReregister = true
        saveInstall()
    }

    /// 계정 삭제 — 서버 purge 가 이 기기 행·동의 이력을 지웠다. 광고 동의를 초기화하고 promo 는 다음 sync 가 해제한다
    func resetForAccountDeletion() {
        settings.resetMarketing()
        saveSettings()
        clearRegistration()
        install.needsReregister = true
        saveInstall()
    }

    // MARK: - 내부 단계

    private func setRemote(_ key: WritableKeyPath<PushSettings, Bool>, _ on: Bool, ctx: Context) async -> ToggleOutcome {
        guard deps.isAvailable() else { return .unavailable }
        if on {
            // 권한 미결정이면 시스템 팝업, 이미 결정됐으면 현재 결과만 (LocalNotifications 와 같은 요청)
            guard await deps.requestAuthorization() else {
                deniedBySystem = true
                return .permissionDenied
            }
            deniedBySystem = false
        }
        settings[keyPath: key] = on
        saveSettings()
        await sync(ctx)
        return .applied
    }

    private func runSync(_ ctx: Context) async {
        guard deps.isAvailable() else { return }
        await releasePromoForFreshInstall()
        guard settings.anyRemote(guardianEligible: ctx.guardianEligible) else {
            deniedBySystem = false
            await teardown(ctx)
            return
        }
        guard await deps.isAuthorized() else {
            // 시스템 설정에서 꺼졌다 — 앱 설정은 그대로 두고 새 토큰도 만들지 않는다.
            // 광고 철회만은 미루지 않는다: 대기 중인 거부를 서버에 보내고 남은 promo 구독을 푼다
            deniedBySystem = true
            _ = await sendPendingRevoke(ctx)
            await releaseUnwantedPromo()
            return
        }
        deniedBySystem = false
        guard let token = await prepareToken() else { return }
        await registerIfNeeded(ctx, token: token, force: false)
        _ = await sendPendingRevoke(ctx)
        await syncTopics()
    }

    private func runGrant(_ ctx: Context) async -> ConsentOutcome {
        guard let token = await prepareToken() else { return .failed }
        guard await registerIfNeeded(ctx, token: token, force: true) else { return .failed }
        // 보내지 못한 거부가 남아 있으면 먼저 기록해야 동의 시각이 바르게 남는다
        _ = await sendPendingRevoke(ctx)
        guard !settings.pendingMarketingRevoke else { return .failed }
        guard let jwt = await ctx.accessToken() else { return .failed }
        do {
            let result: PushApi.ConsentResult
            do {
                result = try await deps.setConsent(jwt, install.pushInstallId, true)
            } catch let error as LearningApi.ApiError where Self.isNotFound(error) {
                // 서버에 이 기기 행이 없다 — 등록한 뒤 한 번 더
                guard await registerIfNeeded(ctx, token: token, force: true) else { return .failed }
                result = try await deps.setConsent(jwt, install.pushInstallId, true)
            }
            guard result.granted else { return .failed }
            settings.acceptMarketingAck(recordedAt: result.recordedAt)
            saveSettings()
            await syncTopics()
            print("[Push] 광고 수신 동의 기록 완료")
            return .granted(result.recordedAt)
        } catch {
            print("[Push] 광고 수신 동의 기록 실패:", error.localizedDescription)
            return .failed
        }
    }

    /// FCM 토큰을 확보한다. 토큰이 바뀌었으면 토픽은 새로 구독해야 한다
    private func prepareToken() async -> String? {
        deps.setAutoInit(true)
        do {
            let token = try await deps.token()
            let hash = Self.fingerprint(token)
            if install.topicsTokenHash != hash {
                install.subscribedTopics = []
                install.topicsTokenHash = hash
                saveInstall()
            }
            return token
        } catch {
            print("[Push] FCM 토큰 확보 실패(다음에 재시도):", error.localizedDescription)
            return nil
        }
    }

    /// 서버 등록 — 계정·토큰·항목이 바뀌었거나, 오늘 아직 안 했거나, 다시 하라는 표시가 있으면.
    /// 등록 상태가 최신이면 true
    @discardableResult
    private func registerIfNeeded(_ ctx: Context, token: String, force: Bool) async -> Bool {
        let body = PushApi.RegisterBody(
            pushInstallId: install.pushInstallId,
            fcmToken: token,
            platform: PushPlatform.ios.rawValue,
            appVersion: deps.appVersion(),
            locale: PushApi.localeTag(),
            timeZone: PushApi.timeZoneId(),
            serviceOptIn: settings.notice,
            learningOptIn: settings.guardianReportActive(eligible: ctx.guardianEligible),
            localReminderEnabled: ctx.localReminderEnabled)
        let fingerprint = Self.fingerprint([
            ctx.accountKey, token, "\(body.serviceOptIn)", "\(body.learningOptIn)",
            "\(body.localReminderEnabled)", body.appVersion, body.locale ?? "", body.timeZone ?? "",
        ].joined(separator: "|"))
        let now = deps.now()
        let registeredToday = install.registeredAt.map { Calendar.current.isDate($0, inSameDayAs: now) } ?? false
        if !force, !install.needsReregister, install.registeredFingerprint == fingerprint, registeredToday {
            return true
        }
        guard let jwt = await ctx.accessToken() else {
            install.needsReregister = true
            saveInstall()
            print("[Push] 계정 세션 없음 — 기기 등록 보류")
            return false
        }
        do {
            let state = try await deps.register(jwt, body)
            install.registeredAt = now
            install.registeredFingerprint = fingerprint
            install.needsReregister = false
            saveInstall()
            reconcileMarketing(with: state)
            print("[Push] 기기 등록 완료 (공지 \(body.serviceOptIn), 보호자 \(body.learningOptIn))")
            return true
        } catch {
            install.needsReregister = true
            saveInstall()
            print("[Push] 기기 등록 실패(다음에 재시도):", error.localizedDescription)
            return false
        }
    }

    /// 서버 등록 응답과 기기의 광고 동의가 다를 때. 켜는 쪽은 동의 시트 → 서버 ack 로만 바뀐다 (Android 와 같다)
    /// - 서버 켜짐 · 기기 꺼짐: 조용히 켜지 않는다. 서버에 철회(granted=false)를 대기열로 보내고 기기는 끈 채 둔다
    /// - 서버 꺼짐 · 기기 켜짐(계정 변경·재확인 만료): 기록 없는 동의로는 광고를 받지 않으므로 기기를 끈다
    /// 거부를 아직 못 보냈다면 이미 철회 중이다 — 기기 값(꺼짐)을 지킨다.
    private func reconcileMarketing(with state: PushApi.DeviceState) {
        guard !settings.pendingMarketingRevoke else { return }
        switch (state.marketingOptIn, settings.marketing) {
        case (true, false):
            settings.beginMarketingRevoke()
            print("[Push] 서버에만 광고 동의가 남아 있음 — 기기는 끈 채 서버에 철회를 보낸다")
        case (false, true):
            settings.resetMarketing()
            print("[Push] 서버 기준 광고 동의 꺼짐 — 기기도 끈다")
        case (true, true):
            // 같은 동의 — 설정 부제 날짜만 서버 기록 시각에 맞춘다
            guard let at = state.marketingOptInAt, settings.marketingAt != at else { return }
            settings.marketingAt = at
        case (false, false):
            return
        }
        saveSettings()
    }

    /// 대기 중인 광고 거부를 서버에 기록한다. 기록 시각(성공) 또는 nil(대기 유지·대기 없음)
    private func sendPendingRevoke(_ ctx: Context) async -> Date? {
        guard settings.pendingMarketingRevoke else { return nil }
        guard let jwt = await ctx.accessToken() else { return nil }
        do {
            let result = try await deps.setConsent(jwt, install.pushInstallId, false)
            settings.pendingMarketingRevoke = false
            saveSettings()
            print("[Push] 광고 수신 거부 기록 완료")
            return result.recordedAt
        } catch let error as LearningApi.ApiError where Self.isNotFound(error) {
            // 서버에 이 기기 행이 없다 = 광고를 받을 근거도 없다 (해제·계정 삭제로 이미 사라짐)
            settings.pendingMarketingRevoke = false
            saveSettings()
            print("[Push] 기기 행 없음 — 광고 거부 대기 해소")
            return nil
        } catch {
            print("[Push] 광고 수신 거부 기록 실패(대기열 유지):", error.localizedDescription)
            return nil
        }
    }

    /// 원하는 토픽과 구독한 토픽의 차이만 맞춘다 — 해제 먼저, 구독 나중(광고 해제가 늦지 않게).
    /// 호출마다 시간 제한(10초). 실패는 다음 실행에 재시도
    private func syncTopics() async {
        let desired = PushTopics.desired(settings, platform: .ios)
        let (subscribe, unsubscribe) = PushTopics.diff(desired: desired, subscribed: Set(install.subscribedTopics))
        for topic in unsubscribe {
            await unsubscribeTopic(topic)
        }
        for topic in subscribe {
            do {
                try await topicCall { try await self.deps.subscribe(topic) }
                install.subscribedTopics.append(topic)
                saveInstall()
            } catch {
                print("[Push] 토픽 구독 실패 \(topic):", error.localizedDescription)
            }
        }
        if !subscribe.isEmpty || !unsubscribe.isEmpty {
            print("[Push] 토픽 맞춤 — 해제 \(unsubscribe), 구독 \(subscribe)")
        }
    }

    /// 토픽 하나 해제 — 성공하면 구독 목록에서 뺀다. 실패는 다음 실행에 재시도
    private func unsubscribeTopic(_ topic: String) async {
        do {
            try await topicCall { try await self.deps.unsubscribe(topic) }
            install.subscribedTopics.removeAll { $0 == topic }
            saveInstall()
        } catch {
            print("[Push] 토픽 해제 실패 \(topic):", error.localizedDescription)
        }
    }

    /// 시스템 알림이 꺼져 새 토큰·구독은 만들지 않을 때도 — 원하지 않는 promo 구독은 남기지 않는다
    private func releaseUnwantedPromo() async {
        guard install.subscribedTopics.contains(PushTopics.promo),
              !PushTopics.desired(settings, platform: .ios).contains(PushTopics.promo) else { return }
        await unsubscribeTopic(PushTopics.promo)
    }

    /// 새 설치 식별자를 만든 설치 — 이 기기에 남아 있을지 모르는 FCM 토큰의 promo 구독을 한 번 푼다.
    /// 토큰이 없으면 풀 구독도 없다(해제를 부르면 Firebase 가 토큰이 생길 때까지 붙잡는다). 실패하면 다음 sync 에 다시
    private func releasePromoForFreshInstall() async {
        guard install.promoReleasePending else { return }
        if deps.hasToken() {
            do {
                try await topicCall { try await self.deps.unsubscribe(PushTopics.promo) }
                print("[Push] 새 설치 식별자 — 남은 promo 구독 해제")
            } catch {
                print("[Push] 새 설치 식별자 promo 해제 실패(다음에 재시도):", error.localizedDescription)
                return
            }
        }
        install.promoReleasePending = false
        install.subscribedTopics.removeAll { $0 == PushTopics.promo }
        saveInstall()
    }

    /// 원격 항목 모두 꺼짐 — 토픽 해제 → 서버 해제 → 토큰 삭제. 한 번도 켠 적 없으면 Firebase 를 건드리지 않는다
    private func teardown(_ ctx: Context) async {
        let hasServerRow = install.registeredAt != nil
        let hasToken = install.topicsTokenHash != nil || !install.subscribedTopics.isEmpty
        if !hasServerRow, settings.pendingMarketingRevoke {
            // 서버에 행이 없으면 거부를 기록할 곳도, 광고가 올 길도 없다
            settings.pendingMarketingRevoke = false
            saveSettings()
        }
        guard hasServerRow || hasToken else { return }
        // 토큰을 지운 뒤 자동 초기화가 새 토큰을 만들지 않도록 먼저 끈다
        deps.setAutoInit(false)
        print("[Push] 원격 알림 모두 꺼짐 — 토픽 해제·서버 해제·토큰 삭제")
        for topic in install.subscribedTopics.sorted() {
            await unsubscribeTopic(topic)
        }
        if hasServerRow, let jwt = await ctx.accessToken() {
            do {
                try await deps.unregister(jwt, install.pushInstallId, 30)
                clearRegistration()
            } catch let error as LearningApi.ApiError where Self.isNotFound(error) {
                // 다른 계정 소유로 남은 행 — 그 계정으로는 지울 수 없고, 다음 등록 때 서버가 정리한다
                clearRegistration()
            } catch {
                print("[Push] 서버 기기 해제 실패(다음에 재시도):", error.localizedDescription)
            }
            saveInstall()
            if install.registeredAt == nil, settings.pendingMarketingRevoke {
                // 서버 해제가 광고 동의도 함께 끈다 (해제 RPC 가 거부 이력을 남긴다)
                settings.pendingMarketingRevoke = false
                saveSettings()
            }
        }
        if hasToken {
            do {
                try await deps.deleteToken()
                install.subscribedTopics = []
                install.topicsTokenHash = nil
                saveInstall()
            } catch {
                print("[Push] FCM 토큰 삭제 실패(다음에 재시도):", error.localizedDescription)
            }
        }
    }

    private func runUnregister(_ jwt: String) async {
        guard install.registeredAt != nil else { return }
        do {
            try await deps.unregister(jwt, install.pushInstallId, 5)
            clearRegistration()
            // 서버 해제는 광고 동의 거부도 남긴다
            if settings.pendingMarketingRevoke {
                settings.pendingMarketingRevoke = false
                saveSettings()
            }
            print("[Push] 로그아웃 전 기기 해제 완료")
        } catch {
            print("[Push] 로그아웃 전 기기 해제 실패 — 다음 등록 때 다시 맞춘다:", error.localizedDescription)
        }
        install.needsReregister = true
        saveInstall()
    }

    private func clearRegistration() {
        install.registeredAt = nil
        install.registeredFingerprint = nil
        install.needsReregister = false
    }

    private func saveSettings() { deps.saveSettings(settings) }
    private func saveInstall() { deps.saveInstall(install) }

    private func serialized<T>(_ operation: @escaping @MainActor () async -> T) async -> T {
        let previous = tail
        let task = Task { @MainActor () -> T in
            await previous?.value
            return await operation()
        }
        tail = Task { @MainActor in _ = await task.value }
        return await task.value
    }

    /// 토픽 구독·해제 한 번 — 시간 제한을 넘기면 실패로 보고 동기화 잠금을 놓는다 (Firebase 쪽 작업은 취소만 요청)
    private func topicCall(_ operation: @escaping @MainActor () async throws -> Void) async throws {
        final class Once {
            var done = false
            var timer: Task<Void, Never>?
        }
        let once = Once()
        let timeout = deps.topicTimeout
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let work = Task { @MainActor in
                let result: Result<Void, Error>
                do {
                    try await operation()
                    result = .success(())
                } catch {
                    result = .failure(error)
                }
                guard !once.done else { return }
                once.done = true
                once.timer?.cancel()
                continuation.resume(with: result)
            }
            once.timer = Task { @MainActor in
                try? await Task.sleep(for: timeout)
                guard !once.done else { return }
                once.done = true
                work.cancel()
                continuation.resume(throwing: PushStoreError.topicTimeout)
            }
        }
    }

    /// 작업이 timeout 안에 끝나면 true. 늦으면 false 를 돌려주고 작업은 그대로 둔다(취소는 호출부가)
    private static func waitAtMost(_ timeout: Duration, for work: Task<Void, Never>) async -> Bool {
        final class Once { var done = false }
        let once = Once()
        return await withCheckedContinuation { (continuation: CheckedContinuation<Bool, Never>) in
            Task { @MainActor in
                await work.value
                guard !once.done else { return }
                once.done = true
                continuation.resume(returning: true)
            }
            Task { @MainActor in
                try? await Task.sleep(for: timeout)
                guard !once.done else { return }
                once.done = true
                continuation.resume(returning: false)
            }
        }
    }

    nonisolated static func isNotFound(_ error: LearningApi.ApiError) -> Bool {
        if case let .http(status, _, _) = error { return status == 404 }
        return false
    }

    /// 토큰·등록 값 지문 — 원문을 저장하지 않는다
    nonisolated static func fingerprint(_ value: String) -> String {
        SHA256.hash(data: Data(value.utf8)).prefix(12).map { String(format: "%02x", $0) }.joined()
    }

    /// 보호자 학습 소식을 받을 수 있는 계정 — 이메일 계정 + 이용권(기록 보관)
    static func guardianEligible(auth: AuthStore, premium: PremiumStore) -> Bool {
        auth.isPermanent && premium.isPremium
    }
}

@MainActor
extension PushStore.Context {
    /// 화면·RootView 에서 지금 상태로 만든다
    static func current(auth: AuthStore, premium: PremiumStore, reminder: ReminderStore) -> Self {
        Self(
            accountKey: "\(auth.userId ?? "-")|\(auth.isPermanent)",
            guardianEligible: PushStore.guardianEligible(auth: auth, premium: premium),
            localReminderEnabled: reminder.settings.enabled,
            accessToken: { await auth.accessToken() })
    }
}

@MainActor
extension PushStore.Dependencies {
    /// 실제 Firebase·서버·키체인·UserDefaults
    static func live() -> Self {
        Self(
            isAvailable: { RemotePush.isConfigured },
            isAuthorized: { await LocalNotifications.isAuthorized() },
            requestAuthorization: { await LocalNotifications.requestAuthorization() },
            setAutoInit: { RemotePush.setAutoInit($0) },
            token: { try await RemotePush.token() },
            subscribe: { try await RemotePush.subscribe($0) },
            unsubscribe: { try await RemotePush.unsubscribe($0) },
            deleteToken: { try await RemotePush.deleteToken() },
            hasToken: { RemotePush.hasToken },
            register: { try await PushApi.register(accessToken: $0, body: $1) },
            setConsent: { try await PushApi.setMarketingConsent(accessToken: $0, pushInstallId: $1, granted: $2) },
            unregister: { try await PushApi.unregister(accessToken: $0, pushInstallId: $1, timeout: $2) },
            loadInstall: {
                guard let data = Keychain.load(key: PushStore.installKey) else { return nil }
                do {
                    return try JSONDecoder().decode(PushInstall.self, from: data)
                } catch {
                    print("[Push] 설치 기록 해석 실패 — 새 식별자로 시작:", error)
                    return nil
                }
            },
            saveInstall: { install in
                do {
                    Keychain.save(try JSONEncoder().encode(install), key: PushStore.installKey)
                } catch {
                    print("[Push] 설치 기록 저장 실패:", error)
                }
            },
            loadSettings: { Persistence.load(PushSettings.self, key: Persistence.pushKey) },
            saveSettings: { Persistence.save($0, key: Persistence.pushKey) },
            appVersion: { PushApi.appVersion },
            now: { Date() })
    }
}
