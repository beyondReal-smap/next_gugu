import SwiftUI

@main
struct GuguApp: App {
    /// Firebase 구성·APNs 토큰·알림 표시/탭 처리 (원격 푸시)
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    init() {
        // 보존 기한(30일)이 지난 손상 저장본 사본 정리 — 저장소를 여는 스토어들보다 먼저, 앱 시작 때 한 번
        Persistence.purgeExpiredQuarantine()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(appDelegate.events)
        }
    }
}
