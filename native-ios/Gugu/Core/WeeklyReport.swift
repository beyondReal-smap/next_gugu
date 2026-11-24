import Foundation

// 보호자 주간 리포트 — GameState.dayLog(최근 56일 링버퍼)로 이번 주(월~일)를 요약한다.
// 주는 홈 도장(DailyMissionPlan.week(of:))과 같은 월~일이다 — "이번 주"가 화면마다 다른 뜻이던 문제(audit P5)를 없앴다.
// 서버 없이 기기 기록만으로 만든다 (웹 lib/report/weekly.ts 대응).
// 날짜는 "YYYY-MM-DD" 문자열로 다뤄 기기 시간대·일광절약시간과 무관하게 하루씩 센다.

struct WeeklyDay: Equatable {
    let date: String   // YYYY-MM-DD
    let correct: Int
    let wrong: Int
    let msSum: Int

    var solved: Int { correct + wrong }
}

struct MissCount: Equatable {
    let key: String    // "7x8"
    let count: Int

    /// 많이 틀린 순(같으면 키 순)으로 최대 limit 개
    static func ranked(_ misses: [String: Int], limit: Int) -> [MissCount] {
        Array(misses
            .map { MissCount(key: $0.key, count: $0.value) }
            .sorted { $0.count != $1.count ? $0.count > $1.count : $0.key < $1.key }
            .prefix(limit))
    }
}

struct WeeklyReport: Equatable {
    /// 이번 주 월 → 일 7일. 오늘 뒤의 날은 0
    let days: [WeeklyDay]
    /// 지난주 월요일부터 지난주의 오늘과 같은 요일까지의 정답 수 — 화요일이면 지난주 월·화만 비교한다.
    /// 월요일에 꽉 찬 지난주와 비교하면 늘 지기 때문
    let prevCorrect: Int
    /// 이번 주(오늘까지) 자주 틀린 문제 (많이 틀린 순, 최대 3개)
    let topMisses: [MissCount]

    static let topMissLimit = 3

    var correct: Int { days.reduce(0) { $0 + $1.correct } }
    var wrong: Int { days.reduce(0) { $0 + $1.wrong } }
    var solved: Int { correct + wrong }
    var studyMs: Int { days.reduce(0) { $0 + $1.msSum } }
    /// 1문제 이상 푼 날 수
    var activeDays: Int { days.filter { $0.solved > 0 }.count }
    /// 정확도(%) — 푼 문제가 없으면 nil
    var accuracy: Int? {
        solved > 0 ? Int((Double(correct) / Double(solved) * 100).rounded()) : nil
    }
    var correctDelta: Int { correct - prevCorrect }

    /// 보호자에게 건네는 한마디 — 이번 주 기록에서 가장 도움이 될 행동 하나를 고른다
    var parentTip: String {
        if solved == 0 {
            return String(localized: "이번 주는 아직 기록이 없어요. 하루 5분, 한 판부터 함께 시작해 보세요.", comment: "주간 리포트 보호자 팁")
        }
        if let miss = topMisses.first, miss.count >= 2, let p = Problems.parseKey(miss.key) {
            // 한국어는 식 끝에 받침 맞춘 서술격 조사("7 × 8이에요")를 붙인다 — 다른 언어는 식만
            let fact = AppLanguage.current == .ko ? "\(p.a) × \(KoreanReading.withCopula(p.b))" : "\(p.a) × \(p.b)"
            return String(localized: "자주 헷갈리는 문제는 \(fact). \(p.a)단 표를 소리 내어 함께 외워 보세요.",
                          comment: "주간 리포트 보호자 팁 — %@ 는 식(7 × 8), %lld 는 단")
        }
        if activeDays < 3 {
            return String(localized: "짧게라도 매일 하는 습관이 가장 중요해요. 학습 알림을 켜 두면 도움이 돼요.", comment: "주간 리포트 보호자 팁")
        }
        if let accuracy, accuracy >= 90 {
            return String(localized: "정확도가 아주 좋아요! 60초 챌린지로 속도에도 도전해 보세요.", comment: "주간 리포트 보호자 팁")
        }
        return String(localized: "꾸준히 하고 있어요. 취약 문제 복습으로 헷갈리는 문제를 정리해 보세요.", comment: "주간 리포트 보호자 팁")
    }

    /// today 가 든 주(월~일) 리포트
    static func build(_ dayLog: [DayLogEntry]?, today: String) -> WeeklyReport {
        let byDate = Commit.dayLogByDate(dayLog)

        let week = DailyMissionPlan.week(of: today)
        // 날짜 문자열은 "yyyy-MM-dd" 라 사전 순이 날짜 순이다
        let soFar = week.filter { $0 <= today }
        let days = week.map { date -> WeeklyDay in
            guard date <= today, let e = byDate[date] else { return WeeklyDay(date: date, correct: 0, wrong: 0, msSum: 0) }
            return WeeklyDay(date: date, correct: e.correct, wrong: e.wrong, msSum: e.msSum)
        }
        let prevCorrect = soFar
            .map { shift($0, by: -7) }
            .reduce(0) { $0 + (byDate[$1]?.correct ?? 0) }

        var misses: [String: Int] = [:]
        for date in soFar {
            guard let e = byDate[date] else { continue }
            misses.merge(e.misses, uniquingKeysWith: +)
        }

        return WeeklyReport(days: days, prevCorrect: prevCorrect, topMisses: MissCount.ranked(misses, limit: topMissLimit))
    }

    // MARK: - 날짜 문자열 계산

    private static let calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }()

    private static func parse(_ date: String) -> Date {
        let parts = date.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3,
              let d = calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
        else { preconditionFailure("날짜 형식이 아님: \(date)") }
        return d
    }

    /// "2026-09-29" 를 days 만큼 옮긴 날짜
    static func shift(_ date: String, by days: Int) -> String {
        let moved = calendar.date(byAdding: .day, value: days, to: parse(date))!
        let c = calendar.dateComponents([.year, .month, .day], from: moved)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }

    /// 요일 한 글자 — "2026-09-29" → "화" (en "T", ja "火")
    static func weekdayLabel(_ date: String) -> String {
        weekdaySymbols[calendar.component(.weekday, from: parse(date)) - 1]
    }

    /// 요일 전체 이름 (화면 낭독용) — "화요일" / "Tuesday" / "火曜日"
    static func weekdayName(_ date: String) -> String {
        var c = calendar
        c.locale = AppLanguage.formatLocale
        return c.standaloneWeekdaySymbols[calendar.component(.weekday, from: parse(date)) - 1]
    }

    private static let weekdaySymbols: [String] = {
        var c = calendar
        c.locale = AppLanguage.formatLocale
        return c.veryShortStandaloneWeekdaySymbols
    }()
}
