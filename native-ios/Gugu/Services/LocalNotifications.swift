import Foundation
import UserNotifications

// 로컬 알림 예약 — UNUserNotificationCenter 래퍼.
// 무엇을 언제 보낼지는 Core/ReminderPlanner 가 정하고, 여기서는 예약·취소만 한다.
// 서버를 거치지 않으므로 기기 토큰 같은 새 개인정보를 수집하지 않는다.

enum LocalNotifications {
    private static var center: UNUserNotificationCenter { .current() }

    /// 권한을 요청한다. 이미 결정된 상태면 시스템 팝업 없이 현재 결과만 돌려준다.
    /// 배지는 요청하지 않는다 — 아동 앱에서 빨간 숫자로 재촉하지 않는다.
    static func requestAuthorization() async -> Bool {
        do {
            return try await center.requestAuthorization(options: [.alert, .sound])
        } catch {
            print("[Reminder] 권한 요청 실패:", error.localizedDescription)
            return false
        }
    }

    static func isAuthorized() async -> Bool {
        let status = await center.notificationSettings().authorizationStatus
        return status == .authorized || status == .provisional
    }

    /// 이 앱이 예약한 학습 알림을 모두 지우고 새 계획으로 교체한다.
    /// 다른 목적의 알림(향후 추가될 수 있는)은 건드리지 않도록 접두어로 골라 지운다.
    static func replace(with reminders: [PlannedReminder]) async {
        let pending = await center.pendingNotificationRequests()
        let ours = pending.map(\.identifier).filter { $0.hasPrefix(ReminderPlanner.idPrefix) }
        center.removePendingNotificationRequests(withIdentifiers: ours)

        for reminder in reminders {
            let content = UNMutableNotificationContent()
            content.title = reminder.title
            content.body = reminder.body
            content.sound = .default

            let components = Calendar.current.dateComponents(
                [.year, .month, .day, .hour, .minute], from: reminder.fireDate
            )
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            do {
                try await center.add(
                    UNNotificationRequest(identifier: reminder.id, content: content, trigger: trigger)
                )
            } catch {
                print("[Reminder] 예약 실패 \(reminder.id):", error.localizedDescription)
            }
        }
        print("[Reminder] 예약 교체 — 지움 \(ours.count)건, 새로 \(reminders.count)건")
    }

    /// 현재 예약된 학습 알림 (확인·디버깅용)
    static func pending() async -> [(id: String, date: Date?, body: String)] {
        await center.pendingNotificationRequests()
            .filter { $0.identifier.hasPrefix(ReminderPlanner.idPrefix) }
            .map { request in
                let date = (request.trigger as? UNCalendarNotificationTrigger)?.nextTriggerDate()
                return (request.identifier, date, request.content.body)
            }
    }
}
