import UIKit
import UserNotifications
import FirebaseMessaging

// 앱 델리게이트 — Firebase 구성, APNs 토큰 전달, 알림 표시·탭 처리.
// Firebase 스위즐링을 껐으므로(FirebaseAppDelegateProxyEnabled=false) 여기서 직접 넘긴다.
// 스토어는 RootView 가 가지므로, 델리게이트는 PushEvents 에 사건만 남기고 RootView 가 반영한다.

/// 델리게이트 → RootView 사건 전달
@MainActor
@Observable
final class PushEvents {
    /// 원격 알림을 눌러 들어온 이동 — RootView 가 Router.pendingRoute 로 옮기고 비운다
    var tappedRoute: PushRoute?
    /// FCM 토큰 발급·갱신 횟수 — 바뀌면 RootView 가 PushStore.sync 로 서버에 다시 등록한다
    var tokenRevision = 0
    /// 세션·게임 진행 중 — 이때 온 공지는 배너 없이 알림 목록에만 둔다 (RootView 가 갱신)
    var isImmersive = false
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    let events = PushEvents()

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        RemotePush.configure()
        RemotePush.setDelegate(self)
        // 앱이 꺼진 상태에서 알림을 눌러 시작해도 didReceive 가 오도록 실행 직후에 건다
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        RemotePush.didRegisterAPNs(deviceToken)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        RemotePush.didFailAPNs(error)
    }

    /// 원격 알림인지 — 학습 알림(로컬)은 예전처럼 다룬다
    nonisolated private static func isRemote(_ notification: UNNotification) -> Bool {
        notification.request.trigger is UNPushNotificationTrigger
    }
}

// MARK: - 알림 표시·탭

extension AppDelegate: UNUserNotificationCenterDelegate {
    /// 앱이 떠 있을 때 — 학습 알림(로컬)은 델리게이트가 없던 때와 같이 표시하지 않는다(회귀 방지).
    /// 원격: 광고는 목록만, 공지·보호자·계정은 배너+목록+소리, 진행 중이면 목록만.
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        guard Self.isRemote(notification) else {
            completionHandler([])
            return
        }
        let kind = PushKind.parse(notification.request.content.userInfo["kind"] as? String)
        Task { @MainActor in
            switch PushForegroundStyle.decide(kind: kind, immersive: self.events.isImmersive) {
            case .full: completionHandler([.banner, .list, .sound])
            case .listOnly: completionHandler([.list])
            }
        }
    }

    /// 알림을 눌렀을 때 — 원격 알림만 data link 로 이동한다 (학습 알림은 예전처럼 앱만 연다)
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        guard Self.isRemote(response.notification),
              response.actionIdentifier == UNNotificationDefaultActionIdentifier else {
            completionHandler()
            return
        }
        let route = PushRoute.parse(response.notification.request.content.userInfo["link"] as? String)
        Task { @MainActor in
            self.events.tappedRoute = route
            completionHandler()
        }
    }
}

// MARK: - FCM 토큰

extension AppDelegate: MessagingDelegate {
    nonisolated func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard fcmToken != nil else { return }
        Task { @MainActor in self.events.tokenRevision += 1 }
    }
}
