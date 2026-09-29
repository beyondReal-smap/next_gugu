import Foundation

// 보호자 주간 리포트 — GameState.dayLog(최근 56일 링버퍼)로 최근 7일을 요약한다.
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
}

struct WeeklyReport: Equatable {
    /// 7일 — 6일 전 → 오늘 순
    let days: [WeeklyDay]
    /// 직전 7일(13일 전 ~ 7일 전) 정답 수 — 지난주 대비 비교용
    let prevCorrect: Int
    /// 이번 7일 자주 틀린 문제 (많이 틀린 순, 최대 3개)
    let topMisses: [MissCount]

    static let span = 7
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
            return "이번 주는 아직 기록이 없어요. 하루 5분, 한 판부터 함께 시작해 보세요."
        }
        if let miss = topMisses.first, miss.count >= 2, let p = Problems.parseKey(miss.key) {
            return "자주 헷갈리는 문제는 \(p.a) × \(KoreanReading.withCopula(p.b)). \(p.a)단 표를 소리 내어 함께 외워 보세요."
        }
        if activeDays < 3 {
            return "짧게라도 매일 하는 습관이 가장 중요해요. 학습 알림을 켜 두면 도움이 돼요."
        }
        if let accuracy, accuracy >= 90 {
            return "정확도가 아주 좋아요! 60초 챌린지로 속도에도 도전해 보세요."
        }
        return "꾸준히 하고 있어요. 취약 문제 복습으로 헷갈리는 문제를 정리해 보세요."
    }

    /// today 를 마지막 날로 하는 최근 7일 리포트
    static func build(_ dayLog: [DayLogEntry]?, today: String) -> WeeklyReport {
        // 같은 날짜가 둘 이상 저장돼 있으면(손상된 저장본) 합쳐서 센다 — 한쪽을 버리면 기록이 사라진다
        var byDate: [String: DayLogEntry] = [:]
        for e in Commit.normalizeDayLog(dayLog) {
            if var merged = byDate[e.date] {
                merged.correct += e.correct
                merged.wrong += e.wrong
                merged.msSum += e.msSum
                merged.misses.merge(e.misses, uniquingKeysWith: +)
                byDate[e.date] = merged
            } else {
                byDate[e.date] = e
            }
        }

        let dates = (0..<span).reversed().map { shift(today, by: -$0) }
        let days = dates.map { date -> WeeklyDay in
            let e = byDate[date]
            return WeeklyDay(date: date, correct: e?.correct ?? 0, wrong: e?.wrong ?? 0, msSum: e?.msSum ?? 0)
        }
        let prevCorrect = (span..<(span * 2))
            .map { shift(today, by: -$0) }
            .reduce(0) { $0 + (byDate[$1]?.correct ?? 0) }

        var misses: [String: Int] = [:]
        for date in dates {
            guard let e = byDate[date] else { continue }
            misses.merge(e.misses, uniquingKeysWith: +)
        }
        let topMisses = misses
            .map { MissCount(key: $0.key, count: $0.value) }
            .sorted { $0.count != $1.count ? $0.count > $1.count : $0.key < $1.key }
            .prefix(topMissLimit)

        return WeeklyReport(days: days, prevCorrect: prevCorrect, topMisses: Array(topMisses))
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

    /// 요일 한 글자 — "2026-09-29" → "화"
    static func weekdayLabel(_ date: String) -> String {
        let labels = ["일", "월", "화", "수", "목", "금", "토"]
        return labels[calendar.component(.weekday, from: parse(date)) - 1]
    }
}
