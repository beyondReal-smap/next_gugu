import Foundation

// 로컬 학습 알림 계획 — 무엇을 언제 보낼지 계산만 한다.
// 예약·취소는 Services/LocalNotifications 가 맡는다. 시각과 달력을 주입받는 순수 로직이라
// 단위 테스트로 규칙을 고정한다.
//
// 규칙 (2026-09-22 합의)
// - 하루 1회, 사용자가 고른 시각. 야간(21~08시)에는 보내지 않으므로 8~20시로 제한한다.
// - 오늘 목표를 달성했으면 오늘 알림은 보내지 않는다. 일부만 했으면 남은 개수를 알려 준다.
// - 마지막 학습 후 7일까지는 매일, 그 뒤로는 주 1회씩 최대 4번. 계속 보내면 알림을 끈다.
// - 학습 알림만 보낸다. 이용권 구매 권유 같은 광고성 문구는 넣지 않는다
//   (정보통신망법 §50 광고성 정보는 별도 사전 동의 대상, Apple 지침 4.5.4).

struct ReminderSettings: Codable, Equatable {
    var enabled: Bool = false
    /// 알림 시각(시). ReminderPlanner.allowedHours 범위로 제한된다.
    var hour: Int = ReminderPlanner.defaultHour
    /// 결과 화면에서 권한을 한 번이라도 물었는지 — 거절한 사용자에게 반복해서 묻지 않는다
    var askedPermission: Bool = false
}

struct PlannedReminder: Equatable {
    var id: String
    var fireDate: Date
    var title: String
    var body: String
}

enum ReminderPlanner {
    static let defaultHour = 18
    /// 야간 발송 금지(21~08시)를 지키는 선택 가능 범위
    static let allowedHours = 8...20
    /// 마지막 학습 후 이 기간까지는 매일 보낸다
    static let dailyWindowDays = 7
    /// 그 뒤로 주 1회씩 보내는 최대 횟수
    static let weeklyMaxCount = 4
    /// 예약 식별자 접두어 — 이 앱이 예약한 알림만 골라 지우기 위함
    static let idPrefix = "gugu.reminder."

    static func clampHour(_ hour: Int) -> Int {
        min(max(hour, allowedHours.lowerBound), allowedHours.upperBound)
    }

    /// "오전 9시" · "낮 12시" · "오후 6시"
    static func hourLabel(_ hour: Int) -> String {
        switch hour {
        case 12: return "낮 12시"
        case 13...: return "오후 \(hour - 12)시"
        default: return "오전 \(hour)시"
        }
    }

    /// 앞으로 보낼 알림 목록. 앱을 열거나 학습할 때마다 통째로 다시 계산해 교체한다.
    static func plan(
        state: GameState,
        settings: ReminderSettings,
        now: Date,
        calendar: Calendar
    ) -> [PlannedReminder] {
        guard settings.enabled else { return [] }

        let hour = clampHour(settings.hour)
        let today = calendar.startOfDay(for: now)
        let todayKey = dateKey(today, calendar: calendar)
        let studiedToday = state.dailyDate == todayKey && state.dailyCorrect > 0
        let goalMetToday = studiedToday && state.dailyCorrect >= state.dailyGoal
        // 학습 기록이 없으면 오늘을 기준으로 삼는다
        let anchor = parseDate(state.lastPlayedDate, calendar: calendar) ?? today
        let weakFacts = weakestFacts(state.wrongPool)

        let lastWeeklyDay = dailyWindowDays + weeklyMaxCount * 7
        let horizon = lastWeeklyDay + max(0, days(from: anchor, to: today, calendar: calendar))

        var result: [PlannedReminder] = []
        for offset in 0...horizon {
            guard let day = calendar.date(byAdding: .day, value: offset, to: today),
                  let fire = calendar.date(bySettingHour: hour, minute: 0, second: 0, of: day),
                  fire > now else { continue }
            if offset == 0 && goalMetToday { continue }

            let since = max(0, days(from: anchor, to: day, calendar: calendar))
            if since > lastWeeklyDay { break }
            // 7일이 지나면 마지막 학습일 기준 7일 배수 날에만 보낸다
            if since > dailyWindowDays && since % 7 != 0 { continue }

            let content = message(
                state: state,
                isToday: offset == 0,
                studiedToday: studiedToday,
                daysSinceLastPlay: since,
                weakFacts: weakFacts,
                index: result.count
            )
            result.append(PlannedReminder(
                id: idPrefix + dateKey(day, calendar: calendar),
                fireDate: fire,
                title: content.title,
                body: content.body
            ))
        }
        return result
    }

    // MARK: - 문구

    private static func message(
        state: GameState,
        isToday: Bool,
        studiedToday: Bool,
        daysSinceLastPlay: Int,
        weakFacts: [String],
        index: Int
    ) -> (title: String, body: String) {
        // 오늘 조금 했지만 목표 전 — 남은 개수가 가장 구체적인 동기다
        if isToday && studiedToday {
            let remaining = max(1, state.dailyGoal - state.dailyCorrect)
            return ("조금만 더!", "오늘 목표까지 정답 \(remaining)개 남았어요.")
        }
        // 어제 학습했다면 오늘 하면 연속 기록이 이어진다
        if daysSinceLastPlay == 1 && state.streak > 0 {
            return ("연속 기록 이어가기",
                    "\(state.streak)일 연속 중이에요! 오늘도 한 판 하면 \(state.streak + 1)일째예요.")
        }
        // 한동안 쉬었다면 부담 없이 다시 시작하자는 말로 — 죄책감을 주는 문구는 쓰지 않는다
        if daysSinceLastPlay > dailyWindowDays {
            return ("다시 시작해요", "오랜만이에요! 구구단 한 판으로 가볍게 시작해 볼까요?")
        }
        // 약점 복습과 일반 권유를 번갈아 보내 같은 문구가 반복되지 않게 한다
        if !weakFacts.isEmpty && index % 2 == 0 {
            let key = weakFacts[(index / 2) % weakFacts.count]
            return ("약점 복습", "\(key.replacingOccurrences(of: "x", with: " × ")), 다시 도전해 볼까요?")
        }
        return ("오늘의 구구단", "오늘도 한 판 어때요? 목표는 정답 \(state.dailyGoal)개예요.")
    }

    /// 많이 틀린 순서(같으면 식 이름순)로 정렬한 식 목록 — 순서가 결정적이어야 테스트가 안정된다
    private static func weakestFacts(_ pool: [String: Int]) -> [String] {
        let wrong: [(key: String, value: Int)] = pool.filter { $0.value > 0 }.map { ($0.key, $0.value) }
        let sorted = wrong.sorted { lhs, rhs in
            if lhs.value != rhs.value { return lhs.value > rhs.value }
            return lhs.key < rhs.key
        }
        return sorted.map { $0.key }
    }

    // MARK: - 날짜 (GameState 의 YYYY-MM-DD 와 같은 형식)

    static func dateKey(_ date: Date, calendar: Calendar) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    static func parseDate(_ key: String, calendar: Calendar) -> Date? {
        let parts = key.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
    }

    private static func days(from: Date, to: Date, calendar: Calendar) -> Int {
        calendar.dateComponents(
            [.day], from: calendar.startOfDay(for: from), to: calendar.startOfDay(for: to)
        ).day ?? 0
    }
}
