import Foundation

// 보호자 리포트 — 최근 14일 날짜 기록의 단별 합계(DayLogEntry.tables)로 단마다 "잘해요·연습 중·약해요"를 정한다.
// tableMastery 는 최고 정확도·별(최댓값)이라 한 번 잘하면 영원히 잘한다로 남고, 게임 판은 별을 주지 않아 비어 있다 —
// 그래서 날마다 쌓이는 단별 합계를 기간으로 잘라 본다(계획서 redesign-v1 2-4). Android core/guardian/GuardianReport.kt 와 같은 의미.

enum SkillStatus: Equatable {
    /// 기간 안에 이 단 문제를 하나도 안 풀었다
    case noRecord
    /// 문제 수가 판정 하한보다 적거나, 정확도가 약해요·잘해요 사이
    case practicing
    case strong
    case weak
}

/// 한 단의 최근 14일 실력
struct TableSkill: Equatable {
    let table: Int
    let attempts: Int
    let correct: Int
    let msSum: Int

    /// 정확도 0~1. 푼 문제가 없으면 nil
    var accuracy: Double? { attempts > 0 ? Double(correct) / Double(attempts) : nil }
    /// 평균 응답 시간(ms). 푼 문제가 없으면 nil — 판정에는 쓰지 않고 칸에 숫자로만 보인다
    var avgMs: Int? { attempts > 0 ? Int((Double(msSum) / Double(attempts)).rounded()) : nil }

    /// 정확도로만 정한다 — 게임 응답 시간에는 발판·카드를 찾는 시간까지 들어가 흔들려서, 시간을 넣으면 부모가 이유를 알기 어렵다
    var status: SkillStatus {
        guard let accuracy else { return .noRecord }
        if attempts < TableSkills.minAttempts { return .practicing }
        if accuracy >= TableSkills.strongAccuracy { return .strong }
        if accuracy < TableSkills.weakAccuracy { return .weak }
        return .practicing
    }
}

enum TableSkills {
    /// 오늘 포함 최근 14일 — 주 2~3번 하는 아이도 단마다 10문제 넘게 쌓이고, 연습해서 나아지면 2주 안에 "약해요"가 풀린다
    static let windowDays = 14
    /// 판정에 필요한 최소 문제 수 — 세 문제 틀린 단을 약하다고 부르지 않는다
    static let minAttempts = 10
    /// 이상이면 잘해요
    static let strongAccuracy = 0.90
    /// 미만이면 약해요
    static let weakAccuracy = 0.70

    /// 기간 첫날 — today 가 14일째인 첫날(today - 13일)
    static func windowStart(today: String) -> String { WeeklyReport.shift(today, by: -(windowDays - 1)) }

    /// today 를 마지막 날로 하는 14일 안의 날짜 기록(정리·손상 항목 제외). 기기 시계를 되돌려 생긴 오늘 뒤의 날은 넣지 않는다
    static func window(_ dayLog: [DayLogEntry]?, today: String) -> [DayLogEntry] {
        let start = windowStart(today: today)
        // 날짜 문자열은 "yyyy-MM-dd" 라 사전 순이 날짜 순이다
        return Commit.normalizeDayLog(dayLog).filter { $0.date >= start && $0.date <= today }
    }

    /// 2~19단 전부. 기록 없는 단은 attempts 0. 단별 합계가 없는 날(이 필드 전 저장본)은 더할 것이 없어 건너뛴다
    static func build(_ dayLog: [DayLogEntry]?, today: String) -> [Int: TableSkill] {
        var sums: [Int: TableTally] = [:]
        for entry in window(dayLog, today: today) {
            for (table, tally) in entry.tables ?? [:] { sums[table, default: .zero] = sums[table, default: .zero] + tally }
        }
        var skills: [Int: TableSkill] = [:]
        for table in Problems.minTable...Problems.maxTable {
            let sum = sums[table] ?? .zero
            skills[table] = TableSkill(table: table, attempts: sum.attempts, correct: sum.correct, msSum: sum.msSum)
        }
        return skills
    }

    /// 약한 단 — 약한 순(정확도 오름차순, 같으면 단 오름차순)
    static func weak(_ skills: [Int: TableSkill]) -> [Int] {
        skills.values
            .filter { $0.status == .weak }
            .sorted { l, r in
                let la = l.accuracy ?? 0, ra = r.accuracy ?? 0
                return la != ra ? la < ra : l.table < r.table
            }
            .map(\.table)
    }
}

/// 리포트 맨 위 한마디의 경우 — 문장은 U4 에서 붙인다(지금은 값만)
enum ReportHeadline: Equatable {
    /// 14일 동안 한 문제도 풀지 않았다
    case noRecord
    /// 약한 단 1~2개. 오늘 권하는 단 = first (오늘의 섬과 같다)
    case weak(first: Int, second: Int?)
    /// 약한 단 없음. next = 오늘의 섬
    case keepGoing(next: Int)
    /// 약한 단 없음, 오늘의 섬이 섞어서(남은 단이 모두 별 3개)
    case allStrong
}

struct GuardianReport: Equatable {
    /// 2~9단 항상 + 기간 안에 기록이 있는 10~19단
    let rows: [TableSkill]
    /// 약한 순
    let weak: [Int]
    /// 같은 14일, 많이 틀린 순, 최대 5개
    let topMisses: [MissCount]
    let headline: ReportHeadline
    /// 이번 주(월~일)
    let week: WeeklyReport

    static let topMissLimit = 5
    /// 기록이 없어도 늘 보여 주는 단
    static let baseTables = 2...9

    static func build(_ state: GameState, today: String) -> GuardianReport {
        let skills = TableSkills.build(state.dayLog, today: today)
        let rows = (Problems.minTable...Problems.maxTable).compactMap { table -> TableSkill? in
            guard let skill = skills[table] else { return nil }
            return baseTables.contains(table) || skill.attempts > 0 ? skill : nil
        }
        let weak = TableSkills.weak(skills)

        let window = TableSkills.window(state.dayLog, today: today)
        var misses: [String: Int] = [:]
        for entry in window {
            // 리포트가 식으로 그리므로 읽을 수 없는 키는 뺀다
            for (key, count) in entry.misses where Problems.parseKey(key) != nil { misses[key, default: 0] += count }
        }

        let headline: ReportHeadline
        if window.allSatisfy({ $0.correct + $0.wrong == 0 }) {
            headline = .noRecord
        } else if let first = weak.first {
            headline = .weak(first: first, second: weak.dropFirst().first)
        } else {
            switch TodayIsland.pick(mastery: state.tableMastery, skills: skills) {
            case .table(let next, _): headline = .keepGoing(next: next)
            case .mixed: headline = .allStrong
            }
        }

        return GuardianReport(rows: rows, weak: weak,
                              topMisses: MissCount.ranked(misses, limit: topMissLimit),
                              headline: headline,
                              week: WeeklyReport.build(state.dayLog, today: today))
    }
}
