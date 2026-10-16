import SwiftUI

@main
struct GuguApp: App {
    init() {
        // 보존 기한(30일)이 지난 손상 저장본 사본 정리 — 저장소를 여는 스토어들보다 먼저, 앱 시작 때 한 번
        Persistence.purgeExpiredQuarantine()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
