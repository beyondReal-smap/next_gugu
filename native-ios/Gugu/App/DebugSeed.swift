import Foundation

#if DEBUG
// 재디자인 캡처용 진행 상태 (계획서 redesign-v1 6-3) — SIMCTL_CHILD_GUGU_SEED=v1|max 이면 GameStore 가 저장본 대신 이 값으로 시작한다.
// 출시 빌드에는 없다. 옷장 코인·도감·미션은 PlayRewardsStore 의 기존 훅(GUGU_PLAY_STARS·GUGU_PLAY_DEX·GUGU_PLAY_MISSIONS)으로 같이 넣는다
// — tools/redesign/shoot.sh 가 seed 이름에 맞춰 함께 넘긴다.
//   v1 : V1 시제품 숫자 — Lv.8 38/305, 연속 3일, 2단 별 3·3단 별 2·4단 별 1, 배지 7/17, 헷갈린 문제 11개(7·8단),
//        14일 단별 2단 98%·3단 92%·4단 85%·5단 88%·6단 71%·7단 58%·8단 62%·9단 없음, 이번 주 월 32·화 45(오늘이 화요일일 때 V1 과 같다)
//   max: 가장 긴 내용 — Lv.99, 연속 365일, 최고 기록 999, 10~19단 모두 기록, 헷갈린 문제에 19×19, 약한 단 18·19단, 배지 전부
enum DebugSeed {
    static func state(_ name: String, today: String) -> GameState {
        switch name {
        case "v1": return v1(today: today)
        case "max": return longest(today: today)
        default: preconditionFailure("GUGU_SEED 는 v1|max 중 하나: \(name)")
        }
    }

    /// 단별 (정답, 오답) → 14일 기간 안의 날짜 기록. 이번 주 월요일·오늘 몫을 먼저 떼고 나머지는 지난주 수요일에 둔다.
    /// 오답은 그 단의 곱하는 수 6·7·8·9·4·3 순으로 나눠 misses 에 넣는다(그날 오답 합 = misses 합)
    private static func dayLog(_ totals: [Int: (correct: Int, wrong: Int)], monday: (table: Int, correct: Int),
                               todayShare: (table: Int, correct: Int), today: String) -> [DayLogEntry] {
        let week = DailyMissionPlan.week(of: today)
        let rest = WeeklyReport.shift(week[0], by: -5)   // 지난주 수요일 — 14일 안
        var byDate: [String: [Int: TableTally]] = [:]
        var left = totals
        for (date, share) in [(week[0], monday), (today, todayShare)] {
            guard let total = left[share.table] else { preconditionFailure("시드에 없는 단: \(share.table)") }
            let take = min(share.correct, total.correct)
            byDate[date, default: [:]][share.table, default: .zero] = byDate[date, default: [:]][share.table, default: .zero]
                + TableTally(correct: take, wrong: 0, msSum: take * 2600)
            left[share.table] = (total.correct - take, total.wrong)
        }
        for (table, total) in left where total.correct + total.wrong > 0 {
            byDate[rest, default: [:]][table] = TableTally(correct: total.correct, wrong: total.wrong,
                                                           msSum: (total.correct + total.wrong) * (2400 + table * 120))
        }
        return byDate.keys.sorted().map { date in
            let tables = byDate[date]!
            var misses: [String: Int] = [:]
            for (table, tally) in tables {
                let bs = [6, 7, 8, 9, 4, 3]
                for i in 0..<tally.wrong { misses[Problems.key(table, bs[i % bs.count]), default: 0] += 1 }
            }
            let sum = tables.values.reduce(TableTally.zero, +)
            return DayLogEntry(date: date, correct: sum.correct, wrong: sum.wrong, msSum: sum.msSum, misses: misses, tables: tables)
        }
    }

    private static func mastery(_ stars: Int) -> TableMastery {
        TableMastery(stars: stars, bestAccuracy: stars == 3 ? 1 : 0.85, bestAvgMs: 2200, plays: stars + 1)
    }

    private static func v1(today: String) -> GameState {
        var s = GameState()
        s.totalXp = Level.xpToReach(8) + 38
        s.streak = 3
        s.lastPlayedDate = today
        s.dailyDate = today
        s.dailyCorrect = 45
        s.onboarded = true
        s.tableMastery = [2: mastery(3), 3: mastery(2), 4: mastery(1)]
        s.unlockedAchievements = ["first_correct", "correct_100", "correct_500", "combo_10", "streak_3", "first_master", "level_5"]
        s.totalCorrect = 612
        s.totalWrong = 160
        s.maxCombo = 14
        s.recentAccuracy = [80, 86, 90, 84]
        s.recentAvgMs = [2900, 2700, 2600, 2500]
        s.modesPlayed = [.practice, .timeAttack, .stairs, .monster]
        s.wrongPool = ["7x3": 2, "7x4": 2, "7x6": 4, "7x7": 2, "7x8": 6, "7x9": 4,
                       "8x4": 2, "8x6": 4, "8x7": 6, "8x8": 2, "8x9": 4]
        let totals: [Int: (correct: Int, wrong: Int)] = [
            2: (98, 2), 3: (92, 8), 4: (85, 15), 5: (88, 12), 6: (71, 29), 7: (58, 42), 8: (62, 38),
        ]
        s.dayLog = dayLog(totals, monday: (2, 32), todayShare: (3, 45), today: today)
        return s
    }

    private static func longest(today: String) -> GameState {
        var s = GameState()
        s.totalXp = Level.xpToReach(99) + 1234
        s.streak = 365
        s.lastPlayedDate = today
        s.dailyDate = today
        s.dailyGoal = 50
        s.dailyCorrect = 999
        s.onboarded = true
        for table in Problems.minTable...Problems.maxTable { s.tableMastery[table] = mastery(table >= 18 ? 1 : 3) }
        s.unlockedAchievements = Achievements.all.map(\.id)
        s.totalCorrect = 99_999
        s.totalWrong = 9_999
        s.maxCombo = 999
        s.recentAccuracy = Array(repeating: 100, count: 20)
        s.recentAvgMs = Array(repeating: 1200, count: 20)
        s.bestScores = [.challenge: 999, .survival: 999]
        s.modesPlayed = Modes.list.map(\.id)
        s.wrongPool = ["19x19": 6, "18x19": 6, "19x17": 4, "18x17": 4, "17x19": 2, "12x13": 2]
        var totals: [Int: (correct: Int, wrong: Int)] = [:]
        for table in Problems.minTable...Problems.maxTable { totals[table] = (95, 5) }
        totals[18] = (55, 45)
        totals[19] = (50, 50)
        s.dayLog = dayLog(totals, monday: (2, 95), todayShare: (19, 50), today: today)
        return s
    }
}
#endif
