package site.smap.gugudan.core

import java.time.LocalDate

// 세션 결과를 게임 상태에 반영하는 순수 계산 로직 (Commit.swift 이식)

object Commit {
    /** YYYY-MM-DD (로컬 날짜) */
    fun todayStr(date: LocalDate = LocalDate.now()): String =
        "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)

    val defaultState = GameState()

    /** 날짜별 링버퍼 보관 기간 (8주) */
    const val DAY_LOG_CAP = 56

    private val DATE_RE = Regex("""^\d{4}-\d{2}-\d{2}$""")

    /** 저장본의 날짜 로그 정리 — 날짜 형식이 깨진 항목과 0 이하 오답 집계를 버리고 최근 56일만 남긴다. */
    fun normalizeDayLog(raw: List<DayLogEntry>): List<DayLogEntry> =
        raw.filter { DATE_RE.matches(it.date) }
            .map { it.copy(misses = it.misses.filterValues { v -> v > 0 }) }
            .takeLast(DAY_LOG_CAP)

    /** 오늘 버킷에 세션 답안을 누적하고 날짜 순으로 정렬해 최근 56일만 남긴다. */
    private fun appendDayLog(log: List<DayLogEntry>, result: SessionResult, date: String): List<DayLogEntry> {
        val next = log.toMutableList()
        var i = next.indexOfFirst { it.date == date }
        if (i < 0) {
            next.add(DayLogEntry(date = date))
            i = next.size - 1
        }
        var correct = next[i].correct
        var wrong = next[i].wrong
        var msSum = next[i].msSum
        val misses = next[i].misses.toMutableMap()
        for (ans in result.answers) {
            if (ans.correct) {
                correct += 1
            } else {
                wrong += 1
                val key = Problems.key(ans.a, ans.b)
                misses[key] = (misses[key] ?: 0) + 1
            }
            msSum += ans.ms
        }
        next[i] = DayLogEntry(date = date, correct = correct, wrong = wrong, msSum = msSum, misses = misses.toMap())
        return next.sortedBy { it.date }.takeLast(DAY_LOG_CAP)
    }

    /**
     * 앱 진입 시 데일리 골 날짜만 갱신.
     * 스트릭은 학습 자격(세션 완료 또는 일일 정답 하한)이 있을 때만 qualifyStreak 로 올린다.
     * 기존 lastPlayedDate/streak 값은 소급해서 깎지 않는다.
     */
    fun applyVisit(state: GameState, now: LocalDate = LocalDate.now()): GameState {
        val today = todayStr(now)
        var s = state.copy(dayLog = normalizeDayLog(state.dayLog))
        if (s.dailyDate != today) {
            s = s.copy(dailyDate = today, dailyCorrect = 0)
        }
        return s
    }

    /** 오늘 스트릭 자격 부여. 이미 오늘 자격이면 그대로. */
    private fun qualifyStreak(s: GameState, now: LocalDate): GameState {
        val today = todayStr(now)
        if (s.lastPlayedDate == today) return s
        return if (s.lastPlayedDate == todayStr(now.minusDays(1))) {
            s.copy(streak = s.streak + 1, lastPlayedDate = today)
        } else {
            s.copy(streak = 1, lastPlayedDate = today)
        }
    }

    private fun starsFor(accuracy: Double, avgMs: Int, count: Int): Int = when {
        count == 0 -> 0
        accuracy >= 0.95 && avgMs in 1..2999 -> 3
        accuracy >= 0.8 -> 2
        else -> 1
    }

    /**
     * 세션 결과 반영 → 새 상태 + 부가 정보(CommitResult)
     * partial=true 이면 XP·오답풀·일일 정답만 반영하고, 한 판 완료 통계(별/신기록/추이/모드 탐험)는 건너뛴다.
     */
    fun applySession(state: GameState, result: SessionResult, now: LocalDate = LocalDate.now()): Pair<GameState, CommitResult> {
        val today = todayStr(now)
        var s = state
        val partial = result.partial
        // 날짜 경계
        if (s.dailyDate != today) s = s.copy(dailyDate = today, dailyCorrect = 0)

        val prevLevel = Level.info(s.totalXp).level
        val prevDailyCorrect = s.dailyCorrect

        // XP (콤보 재구성) + 통계 + 오답풀
        var xpEarned = 0
        var combo = 0
        var correctCount = 0
        var wrongPool = s.wrongPool
        var msSum = 0
        val scale = result.xpScale?.takeIf { it >= 0 } ?: 1.0
        for (ans in result.answers) {
            if (ans.correct) {
                combo += 1
                correctCount += 1
                val raw = Level.xpForAnswer(result.mode, combo, ans.ms)
                xpEarned += maxOf(0, Math.round(raw * scale).toInt())
            } else {
                combo = 0
            }
            msSum += ans.ms
            wrongPool = Problems.updateWrongPool(wrongPool, ans.a, ans.b, ans.correct)
        }
        val wrongCount = result.answers.size - correctCount
        val accuracy = if (result.answers.isEmpty()) 0.0 else correctCount.toDouble() / result.answers.size
        val avgMs = if (result.answers.isEmpty()) 0 else Math.round(msSum.toDouble() / result.answers.size).toInt()

        s = s.copy(
            totalXp = s.totalXp + xpEarned,
            totalCorrect = s.totalCorrect + correctCount,
            totalWrong = s.totalWrong + wrongCount,
            maxCombo = maxOf(s.maxCombo, result.maxCombo),
            wrongPool = wrongPool,
            dailyCorrect = s.dailyCorrect + correctCount,
            dayLog = appendDayLog(normalizeDayLog(s.dayLog), result, today),
        )

        var newStars = 0
        var improvedStars = false
        var score: Int? = null
        var isNewBest = false
        val def = Modes.def(result.mode)

        if (!partial) {
            // 최근 추이 (cap 20) — 한 판을 끝냈을 때만
            s = s.copy(
                recentAccuracy = (s.recentAccuracy + Math.round(accuracy * 100).toInt()).takeLast(20),
                recentAvgMs = (s.recentAvgMs + avgMs).takeLast(20),
            )

            // 마스터리 (단 집중 세션)
            if (result.table != null) {
                val prev = s.tableMastery[result.table] ?: TableMastery()
                val sessionStars = starsFor(accuracy, avgMs, result.answers.size)
                val stars = maxOf(prev.stars, sessionStars)
                improvedStars = stars > prev.stars
                newStars = stars
                s = s.copy(tableMastery = s.tableMastery + (result.table to TableMastery(
                    stars = stars,
                    bestAccuracy = maxOf(prev.bestAccuracy, accuracy),
                    bestAvgMs = if (prev.bestAvgMs == 0) avgMs else minOf(prev.bestAvgMs, avgMs),
                    plays = prev.plays + 1,
                )))
            }

            // 모드 기록 — 점수형 모드 최고 기록 + 플레이한 모드
            if (def.scored) {
                score = correctCount
                val prevBest = s.bestScores[result.mode] ?: 0
                if (correctCount > prevBest) {
                    isNewBest = true
                    s = s.copy(bestScores = s.bestScores + (result.mode to correctCount))
                }
            }
            if (result.mode !in s.modesPlayed) s = s.copy(modesPlayed = s.modesPlayed + result.mode)

            // 세션 1회 완료 → 스트릭 자격
            s = qualifyStreak(s, now)
        }

        // 부분 커밋이어도 오늘 정답이 하한에 도달하면 스트릭 자격
        val streakFloor = minOf(s.dailyGoal, 10)
        if (s.dailyCorrect >= streakFloor) s = qualifyStreak(s, now)

        // 업적
        val unlocked = Achievements.newlyUnlocked(s)
        if (unlocked.isNotEmpty()) s = s.copy(unlockedAchievements = s.unlockedAchievements + unlocked)

        val newLevel = Level.info(s.totalXp).level
        val goalReached = prevDailyCorrect < s.dailyGoal && s.dailyCorrect >= s.dailyGoal

        return s to CommitResult(
            xpEarned = xpEarned,
            leveledUp = newLevel > prevLevel,
            newLevel = newLevel,
            unlocked = unlocked,
            table = result.table,
            newStars = newStars,
            improvedStars = improvedStars,
            goalReached = goalReached,
            score = score,
            isNewBest = isNewBest,
            partial = partial,
        )
    }
}
