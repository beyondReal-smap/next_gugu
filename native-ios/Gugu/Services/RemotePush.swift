import Foundation
import UIKit
import FirebaseCore
import FirebaseMessaging

// Firebase Messaging 래퍼 — 구성·APNs 토큰 전달·FCM 토큰·토픽·토큰 삭제·자동 초기화.
// 무엇을 언제 할지는 PushStore 가 정하고, 여기서는 Firebase 호출만 한다.
// 알림을 켜기 전에는 토큰을 만들지 않는다 (Info FirebaseMessagingAutoInitEnabled=false).

enum RemotePushError: LocalizedError {
    case notConfigured
    case apnsTimeout
    case apnsFailed(String)

    var errorDescription: String? {
        switch self {
        case .notConfigured: return "Firebase 가 구성되지 않았습니다 (GoogleService-Info.plist 없음)"  // i18n:skip
        case .apnsTimeout: return "APNs 기기 토큰을 받지 못했습니다 (시간 초과)"  // i18n:skip
        case let .apnsFailed(message): return "APNs 등록 실패: \(message)"  // i18n:skip
        }
    }
}

@MainActor
enum RemotePush {
    /// FirebaseApp.configure() 를 마쳤는지 — 구성 파일이 없는 디버그 빌드는 원격 푸시 없이 동작한다
    private(set) static var isConfigured = false

    private static var apnsToken: Data?
    private static var apnsWaiters: [UUID: CheckedContinuation<Void, Error>] = [:]

    /// 앱 시작 때 한 번 (AppDelegate). 구성 파일이 번들에 없으면 Firebase 를 건드리지 않는다 —
    /// configure() 는 파일이 없으면 예외로 앱을 멈추기 때문이다.
    /// 번들 복사는 빌드 스크립트가 한다(project.yml): 파일이 있으면 복사, 없으면 Debug 는 원격 푸시 없이 빌드되고
    /// Release 는 빌드가 실패한다. 그래서 이 분기는 구성 파일 없는 디버그 빌드에서만 탄다.
    static func configure() {
        guard !isConfigured else { return }
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
            print("[Push] GoogleService-Info.plist 없음 — 원격 푸시 없이 동작")
            return
        }
        FirebaseApp.configure()
        isConfigured = true
    }

    static func setDelegate(_ delegate: MessagingDelegate) {
        guard isConfigured else { return }
        Messaging.messaging().delegate = delegate
    }

    /// 자동 초기화 — 원격 항목을 켜면 true(토큰 자동 갱신), 모두 끄면 false(토큰 삭제 전에)
    static func setAutoInit(_ enabled: Bool) {
        guard isConfigured else { return }
        if Messaging.messaging().isAutoInitEnabled != enabled {
            Messaging.messaging().isAutoInitEnabled = enabled
        }
    }

    // MARK: - APNs (스위즐링을 껐으므로 직접 넘긴다)

    static func didRegisterAPNs(_ token: Data) {
        apnsToken = token
        if isConfigured { Messaging.messaging().apnsToken = token }
        resumeWaiters(with: nil)
    }

    static func didFailAPNs(_ error: Error) {
        print("[Push] APNs 등록 실패:", error.localizedDescription)
        resumeWaiters(with: RemotePushError.apnsFailed(error.localizedDescription))
    }

    /// APNs 토큰이 있어야 FCM 토큰을 받을 수 있다. 실행마다 한 번 등록을 요청한다 (APNs 토큰은 바뀔 수 있다)
    private static func ensureAPNsToken(timeout: Duration = .seconds(15)) async throws {
        if apnsToken != nil { return }
        let id = UUID()
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            apnsWaiters[id] = continuation
            UIApplication.shared.registerForRemoteNotifications()
            Task { @MainActor in
                try? await Task.sleep(for: timeout)
                apnsWaiters.removeValue(forKey: id)?.resume(throwing: RemotePushError.apnsTimeout)
            }
        }
    }

    private static func resumeWaiters(with error: Error?) {
        let waiters = apnsWaiters
        apnsWaiters.removeAll()
        for waiter in waiters.values {
            if let error { waiter.resume(throwing: error) } else { waiter.resume() }
        }
    }

    // MARK: - FCM

    /// 이 기기에 FCM 토큰이 이미 있는지 — 새로 만들지 않고 저장된 값만 본다
    static var hasToken: Bool {
        isConfigured && Messaging.messaging().fcmToken != nil
    }

    /// FCM 토큰 — 없으면 이때 만든다
    static func token() async throws -> String {
        guard isConfigured else { throw RemotePushError.notConfigured }
        try await ensureAPNsToken()
        let token = try await Messaging.messaging().token()
        #if DEBUG
        // Firebase 콘솔 "테스트 메시지 보내기"용 — 디버그 빌드에서만 원문을 찍는다
        print("[Push] FCM 토큰(디버그 전용):", token)
        #endif
        return token
    }

    static func subscribe(_ topic: String) async throws {
        guard isConfigured else { throw RemotePushError.notConfigured }
        try await Messaging.messaging().subscribe(toTopic: topic)
    }

    static func unsubscribe(_ topic: String) async throws {
        guard isConfigured else { throw RemotePushError.notConfigured }
        try await Messaging.messaging().unsubscribe(fromTopic: topic)
    }

    /// 토큰 삭제 — 이 토큰으로 맺은 토픽 구독도 함께 사라진다
    static func deleteToken() async throws {
        guard isConfigured else { throw RemotePushError.notConfigured }
        try await Messaging.messaging().deleteToken()
    }
}
