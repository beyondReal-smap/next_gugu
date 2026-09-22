import XCTest
@testable import Gugu

// 로컬 학습 알림 규칙 — 시각·달력을 고정해 결정적으로 검증한다
final class ReminderTests: XCTestCase {
    private var cal: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return c
    }()

    /// 2026-09-22 (화) 한국 시각 기준 시각
    private func at(_ day: Int, _ hour: Int, _ minute: Int = 0) -> Date {
        cal.date(from: DateComponents(year: 2026, month: 9, day: day, hour: hour, minute: minute))!
    }

    private func state(
        lastPlayed: String = "", streak: Int = 0,
        dailyDate: String = "", dailyCorrect: Int = 0, dailyGoal: Int = 20,
        wrongPool: [String: Int] = [:]
    ) -> GameState {
        var s = GameState()
        s.lastPlayedDate = lastPlayed
        s.streak = streak
        s.dailyDate = dailyDate
        s.dailyCorrect = dailyCorrect
        s.dailyGoal = dailyGoal
        s.wrongPool = wrongPool
        return s
    }

    private let on = ReminderSettings(enabled: true, hour: 18, askedPermission: true)

    private func plan(_ s: GameState, _ settings: ReminderSettings? = nil, now: Date) -> [PlannedReminder] {
        ReminderPlanner.plan(state: s, settings: settings ?? on, now: now, calendar: cal)
    }

    func testDisabledPlansNothing() {
        var off = on
        off.enabled = false
        XCTAssertTrue(plan(state(lastPlayed: "2026-09-22"), off, now: at(22, 10)).isEmpty)
    }

    /// 오늘 목표를 달성했으면 오늘은 보내지 않고, 내일 연속 기록을 이어가자고 알린다
    func testGoalMetTodaySkipsTodayAndNudgesStreakTomorrow() {
        let s = state(lastPlayed: "2026-09-22", streak: 3,
                      dailyDate: "2026-09-22", dailyCorrect: 20, dailyGoal: 20)
        let r = plan(s, now: at(22, 10))
        XCTAssertEqual(r.first?.fireDate, at(23, 18))
        XCTAssertEqual(r.first?.title, "연속 기록 이어가기")
        XCTAssertTrue(r.first?.body.contains("3일 연속") ?? false)
        XCTAssertTrue(r.first?.body.contains("4일째") ?? false)
        XCTAssertFalse(r.contains { cal.isDate($0.fireDate, inSameDayAs: at(22, 0)) })
    }

    /// 오늘 일부만 했다면 오늘 알림에 남은 개수를 알려 준다
    func testPartialTodayRemindsRemainingCount() {
        let s = state(lastPlayed: "2026-09-21", streak: 1,
                      dailyDate: "2026-09-22", dailyCorrect: 12, dailyGoal: 20)
        let first = plan(s, now: at(22, 10)).first
        XCTAssertEqual(first?.fireDate, at(22, 18))
        XCTAssertEqual(first?.body, "오늘 목표까지 정답 8개 남았어요.")
    }

    /// 알림 시각이 이미 지났으면 내일부터 보낸다
    func testPastFireTimeStartsTomorrow() {
        let s = state(lastPlayed: "2026-09-21", streak: 1)
        XCTAssertEqual(plan(s, now: at(22, 19)).first?.fireDate, at(23, 18))
    }

    /// 야간(21~08시)에는 보내지 않는다 — 범위 밖 시각은 8~20시로 당겨진다
    func testHourIsClampedOutOfQuietHours() {
        var late = on
        late.hour = 23
        XCTAssertEqual(plan(state(), late, now: at(22, 10)).first?.fireDate, at(22, 20))
        var early = on
        early.hour = 5
        XCTAssertEqual(plan(state(), early, now: at(22, 6)).first?.fireDate, at(22, 8))
        let hours = plan(state(), late, now: at(22, 10)).map { cal.component(.hour, from: $0.fireDate) }
        XCTAssertTrue(hours.allSatisfy { ReminderPlanner.allowedHours.contains($0) })
    }

    /// 마지막 학습 후 7일까지 매일, 그 뒤 7일 배수 날에 주 1회씩 최대 4번
    func testDailyThenWeeklyCadence() {
        let s = state(lastPlayed: "2026-09-19")   // 오늘(22일)이 3일째
        let r = plan(s, now: at(22, 10))
        let offsets = r.map { cal.dateComponents([.day], from: at(19, 0), to: cal.startOfDay(for: $0.fireDate)).day! }
        XCTAssertEqual(offsets, [3, 4, 5, 6, 7, 14, 21, 28, 35])
    }

    /// 5주 넘게 쉬었다면 더 보내지 않는다
    func testStopsAfterWeeklyWindow() {
        XCTAssertTrue(plan(state(lastPlayed: "2026-08-10"), now: at(22, 10)).isEmpty)
    }

    /// 한동안 쉰 뒤에는 부담 없는 복귀 문구
    func testComebackMessageAfterDailyWindow() {
        let r = plan(state(lastPlayed: "2026-09-10"), now: at(22, 10))   // 12일째 → 다음은 14일째
        XCTAssertEqual(r.first?.title, "다시 시작해요")
    }

    /// 약점 복습 문구는 가장 많이 틀린 식을 쓴다
    func testWeakFactMessage() {
        let s = state(lastPlayed: "2026-09-19", wrongPool: ["7x8": 3, "6x9": 1])
        let bodies = plan(s, now: at(22, 10)).map(\.body)
        XCTAssertTrue(bodies.contains("7 × 8, 다시 도전해 볼까요?"))
    }

    /// 학습 알림만 보낸다 — 광고성 문구(구매·결제·이용권)가 끼어들면 안 된다
    func testNoPromotionalWording() {
        let s = state(lastPlayed: "2026-09-19", streak: 2, wrongPool: ["7x8": 2])
        let text = plan(s, now: at(22, 10)).map { $0.title + $0.body }.joined()
        for banned in ["구매", "결제", "이용권", "할인", "프리미엄"] {
            XCTAssertFalse(text.contains(banned), "광고성 단어 포함: \(banned)")
        }
    }

    /// 식별자는 날짜별로 고유하고 앱 접두어를 가진다 — 우리 알림만 골라 교체하기 위함
    func testIdentifiersAreUniqueAndPrefixed() {
        let r = plan(state(lastPlayed: "2026-09-19"), now: at(22, 10))
        XCTAssertEqual(Set(r.map(\.id)).count, r.count)
        XCTAssertTrue(r.allSatisfy { $0.id.hasPrefix(ReminderPlanner.idPrefix) })
        XCTAssertLessThanOrEqual(r.count, 64)   // iOS 예약 한도
    }
}
