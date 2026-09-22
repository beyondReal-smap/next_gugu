import Foundation
import Observation

// 학습 알림 설정 — 켜짐 여부·시각을 저장하고, 상태가 바뀔 때마다 예약을 교체한다.

@MainActor
@Observable
final class ReminderStore {
    private(set) var settings: ReminderSettings
    /// 앱에서는 켜 두었지만 시스템 설정에서 알림이 꺼진 상태 — 앱이 켤 수 없어 설정으로 안내한다
    private(set) var deniedBySystem = false

    /// 예약 교체가 겹치면 지우기·추가가 섞여 중복·누락이 생긴다 → 한 번에 하나씩 순서대로
    private var lastReschedule: Task<Void, Never>?

    init() {
        settings = Persistence.load(ReminderSettings.self, key: Persistence.reminderKey) ?? ReminderSettings()
    }

    /// 결과 화면에서 권유할지 — 한 번 물었으면(수락이든 거절이든) 다시 묻지 않는다
    var shouldOfferAfterSession: Bool { !settings.askedPermission && !settings.enabled }

    /// 알림을 켠다. 권한이 없으면 요청하고, 거절되면 false 를 돌려준다.
    @discardableResult
    func enable(hour: Int, state: GameState) async -> Bool {
        settings.askedPermission = true
        let granted = await LocalNotifications.requestAuthorization()
        settings.enabled = granted
        deniedBySystem = !granted
        if granted { settings.hour = ReminderPlanner.clampHour(hour) }
        save()
        await reschedule(state: state)
        return granted
    }

    /// 결과 화면 권유를 거절 — 다시 묻지 않지만 프로필에서 언제든 켤 수 있다
    func declineOffer() {
        settings.askedPermission = true
        save()
    }

    func disable() async {
        settings.enabled = false
        deniedBySystem = false
        save()
        await replace(with: [])
    }

    func setHour(_ hour: Int, state: GameState) async {
        settings.hour = ReminderPlanner.clampHour(hour)
        save()
        await reschedule(state: state)
    }

    /// 앱 진입·학습 직후 호출 — 계획을 새로 세워 예약을 통째로 교체한다.
    /// 오늘 목표를 달성했다면 이때 오늘 알림이 빠진다.
    func reschedule(state: GameState, now: Date = Date()) async {
        guard settings.enabled else {
            await replace(with: [])
            return
        }
        // 사용자가 시스템 설정에서 꺼 버렸다면 앱 설정은 그대로 두고 예약만 비운다
        guard await LocalNotifications.isAuthorized() else {
            deniedBySystem = true
            await replace(with: [])
            return
        }
        deniedBySystem = false
        let plan = ReminderPlanner.plan(state: state, settings: settings, now: now, calendar: .current)
        await replace(with: plan)
    }

    private func replace(with plan: [PlannedReminder]) async {
        let previous = lastReschedule
        let task = Task {
            await previous?.value
            await LocalNotifications.replace(with: plan)
        }
        lastReschedule = task
        await task.value
    }

    private func save() {
        Persistence.save(settings, key: Persistence.reminderKey)
    }
}
