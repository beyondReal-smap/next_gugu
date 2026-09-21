import Foundation

// 세션 결과를 게임 상태에 반영하는 순수 계산 로직 (commit.ts 이식)

enum Commit {
    /// YYYY-MM-DD (로컬 시간대 기준, commit.ts todayStr 대응)
    static func todayStr(_ date: Date = Date()) -> String {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = .current
        let c = cal.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }

    private static func shift(_ days: Int, from date: Date = Date()) -> String {
        let d = Calendar.current.date(byAdding: .day, value: days, to: date) ?? date
        return todayStr(d)
    }

    static let defaultState = GameState()

    /// 날짜별 링버퍼 보관 기간 (8주)
    static let dayLogCap = 56

    /// 저장본의 날짜 로그 정리 — 날짜 형식이 깨진 항목과 0 이하 오답 집계를 버리고 최근 56일만 남긴다.
    static func normalizeDayLog(_ raw: [DayLogEntry]?) -> [DayLogEntry] {
        guard let raw else { return [] }
        let valid = raw.filter { isDateKey($0.date) }.map { entry -> DayLogEntry in
            var e = entry
            e.misses = entry.misses.filter { $0.value > 0 }
            return e
        }
        return Array(valid.suffix(dayLogCap))
    }

    private static func isDateKey(_ s: String) -> Bool {
        guard s.count == 10 else { return false }
        let parts = s.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.count == 3, parts[0].count == 4, parts[1].count == 2, parts[2].count == 2 else { return false }
        return parts.allSatisfy { $0.allSatisfy(\.isNumber) }
    }

    /// 오늘 버킷에 세션 답안을 누적하고 날짜 순으로 정렬해 최근 56일만 남긴다.
    private static func appendDayLog(_ log: [DayLogEntry], result: SessionResult, date: String) -> [DayLogEntry] {
        var next = log
        let i: Int
        if let found = next.firstIndex(where: { $0.date == date }) {
            i = found
        } else {
            next.append(DayLogEntry(date: date, correct: 0, wrong: 0, msSum: 0, misses: [:]))
            i = next.count - 1
        }
        var bucket = next[i]
        for ans in result.answers {
            if ans.correct {
                bucket.correct += 1
            } else {
                bucket.wrong += 1
                let key = Problems.key(ans.a, ans.b)
                bucket.misses[key] = (bucket.misses[key] ?? 0) + 1
            }
            bucket.msSum += ans.ms
        }
        next[i] = bucket
        next.sort { $0.date < $1.date }
        return Array(next.suffix(dayLogCap))
    }

    /// 앱 진입 시 데일리 골 날짜만 갱신.
    /// 스트릭은 학습 자격(세션 완료 또는 일일 정답 하한)이 있을 때만 qualifyStreak 로 올린다.
    /// 기존 lastPlayedDate/streak 값은 소급해서 깎지 않는다.
    static func applyVisit(_ state: GameState, now: Date = Date()) -> GameState {
        let today = todayStr(now)
        var s = state
        s.dayLog = normalizeDayLog(state.dayLog)
        if s.dailyDate != today {
            s.dailyDate = today
            s.dailyCorrect = 0
        }
        return s
    }

    /// 오늘 스트릭 자격 부여. 이미 오늘 자격이면 그대로.
    private static func qualifyStreak(_ s: GameState, now: Date) -> GameState {
        let today = todayStr(now)
        if s.lastPlayedDate == today { return s }
        var next = s
        if s.lastPlayedDate == shift(-1, from: now) {
            next.streak = s.streak + 1
        } else {
            next.streak = 1
        }
        next.lastPlayedDate = today
        return next
    }

    private static func starsFor(accuracy: Double, avgMs: Int, count: Int) -> Int {
        if count == 0 { return 0 }
        if accuracy >= 0.95 && avgMs > 0 && avgMs < 3000 { return 3 }
        if accuracy >= 0.8 { return 2 }
        return 1
    }

    /// 세션 결과 반영 → 새 상태 + 부가 정보(CommitResult)
    /// partial=true 이면 XP·오답풀·일일 정답만 반영하고, 한 판 완료 통계(별/신기록/추이/모드 탐험)는 건너뛴다.
    static func applySession(_ state: GameState, result: SessionResult, now: Date = Date()) -> (next: GameState, commit: CommitResult) {
        let today = todayStr(now)
        var s = state
        let partial = result.partial
        // 날짜 경계
        if s.dailyDate != today { s.dailyDate = today; s.dailyCorrect = 0 }

        let prevLevel = Level.info(totalXp: s.totalXp).level
        let prevDailyCorrect = s.dailyCorrect

        // XP (콤보 재구성) + 통계 + 오답풀
        var xpEarned = 0
        var combo = 0
        var correctCount = 0
        var wrongPool = s.wrongPool
        var msSum = 0
        let scale = (result.xpScale ?? 1) >= 0 ? (result.xpScale ?? 1) : 1
        for ans in result.answers {
            if ans.correct {
                combo += 1
                correctCount += 1
                let raw = Level.xpForAnswer(mode: result.mode, combo: combo, ms: ans.ms)
                xpEarned += max(0, Int((Double(raw) * scale).rounded()))
            } else {
                combo = 0
            }
            msSum += ans.ms
            wrongPool = Problems.updateWrongPool(wrongPool, a: ans.a, b: ans.b, correct: ans.correct)
        }
        let wrongCount = result.answers.count - correctCount
        let accuracy = result.answers.isEmpty ? 0 : Double(correctCount) / Double(result.answers.count)
        let avgMs = result.answers.isEmpty ? 0 : Int((Double(msSum) / Double(result.answers.count)).rounded())

        s.totalXp += xpEarned
        s.totalCorrect += correctCount
        s.totalWrong += wrongCount
        s.maxCombo = max(s.maxCombo, result.maxCombo)
        s.wrongPool = wrongPool
        s.dailyCorrect = s.dailyCorrect + correctCount
        s.dayLog = appendDayLog(normalizeDayLog(s.dayLog), result: result, date: today)

        var newStars = 0
        var improvedStars = false
        var score: Int? = nil
        var isNewBest = false
        let def = Modes.def(result.mode)

        if !partial {
            // 최근 추이 (cap 20) — 한 판을 끝냈을 때만
            s.recentAccuracy = Array((s.recentAccuracy + [Int((accuracy * 100).rounded())]).suffix(20))
            s.recentAvgMs = Array((s.recentAvgMs + [avgMs]).suffix(20))

            // 마스터리 (단 집중 세션)
            if let table = result.table {
                let prev = s.tableMastery[table] ?? .empty
                let sessionStars = starsFor(accuracy: accuracy, avgMs: avgMs, count: result.answers.count)
                let stars = max(prev.stars, sessionStars)
                improvedStars = stars > prev.stars
                newStars = stars
                s.tableMastery[table] = TableMastery(
                    stars: stars,
                    bestAccuracy: max(prev.bestAccuracy, accuracy),
                    bestAvgMs: prev.bestAvgMs == 0 ? avgMs : min(prev.bestAvgMs, avgMs),
                    plays: prev.plays + 1
                )
            }

            // 모드 기록 — 점수형 모드(챌린지/서바이벌) 최고 기록 + 플레이한 모드
            if def.scored {
                score = correctCount
                let prevBest = s.bestScores[result.mode] ?? 0
                if correctCount > prevBest {
                    isNewBest = true
                    s.bestScores[result.mode] = correctCount
                }
            }
            if !s.modesPlayed.contains(result.mode) { s.modesPlayed.append(result.mode) }

            // 세션 1회 완료 → 스트릭 자격
            s = qualifyStreak(s, now: now)
        }

        // 부분 커밋이어도 오늘 정답이 하한에 도달하면 스트릭 자격
        let streakFloor = min(s.dailyGoal, 10)
        if s.dailyCorrect >= streakFloor { s = qualifyStreak(s, now: now) }

        // 업적
        let unlocked = Achievements.newlyUnlocked(s)
        if !unlocked.isEmpty { s.unlockedAchievements.append(contentsOf: unlocked) }

        let newLevel = Level.info(totalXp: s.totalXp).level
        let goalReached = prevDailyCorrect < s.dailyGoal && s.dailyCorrect >= s.dailyGoal

        return (s, CommitResult(
            xpEarned: xpEarned,
            leveledUp: newLevel > prevLevel,
            newLevel: newLevel,
            unlocked: unlocked,
            table: result.table,
            newStars: newStars,
            improvedStars: improvedStars,
            goalReached: goalReached,
            score: score,
            isNewBest: isNewBest,
            partial: partial
        ))
    }
}
