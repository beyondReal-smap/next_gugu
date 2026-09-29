package site.smap.gugudan

import org.junit.Test
import site.smap.gugudan.core.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// UI/UX 개선에 쓰는 Core 로직 — iOS(GuguTests/UxCoreTests.swift)와 같은 케이스 미러링

class UxCoreTests {

    // MARK: KoreanReading

    @Test fun sinoNumbers() {
        assertEquals("영", KoreanReading.sino(0))
        assertEquals("일", KoreanReading.sino(1))
        assertEquals("십", KoreanReading.sino(10))
        assertEquals("십이", KoreanReading.sino(12))
        assertEquals("오십육", KoreanReading.sino(56))
        assertEquals("팔십일", KoreanReading.sino(81))
        assertEquals("구십", KoreanReading.sino(90))
        assertEquals("백", KoreanReading.sino(100))
        assertEquals("천이백삼십사", KoreanReading.sino(1234))
    }

    @Test fun topicParticle() {
        assertEquals("일은", KoreanReading.topic("일"))
        assertEquals("삼은", KoreanReading.topic("삼"))
        assertEquals("육은", KoreanReading.topic("육"))
        assertEquals("칠은", KoreanReading.topic("칠"))
        assertEquals("팔은", KoreanReading.topic("팔"))
        assertEquals("이는", KoreanReading.topic("이"))
        assertEquals("사는", KoreanReading.topic("사"))
        assertEquals("오는", KoreanReading.topic("오"))
        assertEquals("구는", KoreanReading.topic("구"))
        assertEquals("새싹 들판으로", KoreanReading.toward("새싹 들판"))
        assertEquals("학교로", KoreanReading.toward("학교"))
        assertEquals("서울로", KoreanReading.toward("서울"))
        assertFalse(KoreanReading.hasBatchim("abc"))
        assertFalse(KoreanReading.hasBatchim(""))
    }

    @Test fun questionPhrases() {
        val p = Problem(4, 9)
        assertEquals("사 곱하기 구는?", KoreanReading.question(p, GameMode.PRACTICE, null))
        assertEquals("삼십육은 사 곱하기 몇일까요?", KoreanReading.question(p, GameMode.MISSING, null))
        assertEquals("십이는 사 곱하기 몇일까요?", KoreanReading.question(Problem(4, 3), GameMode.MISSING, null))
        assertEquals("이십은 사 곱하기 몇일까요?", KoreanReading.question(Problem(4, 5), GameMode.MISSING, null))
        assertEquals("사 곱하기 구는 삼십이. 맞을까요?",
            KoreanReading.question(p, GameMode.TRUEFALSE, Statement(32, false)))
        assertEquals("칠 곱하기 일은?", KoreanReading.question(Problem(7, 1), GameMode.CHALLENGE, null))
    }

    @Test fun particlesForDigits() {
        assertEquals("1은", KoreanReading.withTopic(1))
        assertEquals("2는", KoreanReading.withTopic(2))
        assertEquals("36은", KoreanReading.withTopic(36))
        assertEquals("35가", KoreanReading.withSubject(35))
        assertEquals("36이", KoreanReading.withSubject(36))
        assertEquals("10이", KoreanReading.withSubject(10))
        assertEquals("20이에요", KoreanReading.withCopula(20))
        assertEquals("12예요", KoreanReading.withCopula(12))
        assertEquals("0이에요", KoreanReading.withCopula(0))
        assertEquals("35가 아니라 36이에요", KoreanReading.notButIs(35, 36))
        assertEquals("28이 아니라 24예요", KoreanReading.notButIs(28, 24))
    }

    @Test fun chantLines() {
        assertEquals("칠 팔은 오십육", KoreanReading.chant(7, 8))
        assertEquals("이 일은 이", KoreanReading.chant(2, 1))
        assertEquals("이 오는 십", KoreanReading.chant(2, 5))
        assertEquals("구 구는 팔십일", KoreanReading.chant(9, 9))
    }

    // MARK: Hints

    @Test fun coachHintStrategies() {
        val nine = Hints.coach(9, 7)
        assertEquals("10단에서 한 번 빼기", nine.title)
        assertEquals("70 − 7 = ?", nine.scaffold)
        assertEquals("9 × 7 = 70 − 7 = 63", nine.full)

        val swapped = Hints.coach(6, 2)
        assertEquals("순서를 바꿔 두 번 더하기", swapped.title)
        assertEquals("6 + 6 = ?", swapped.scaffold)
        assertEquals("6 × 2 = 6 + 6 = 12", swapped.full)

        assertEquals("두 번 더하기", Hints.coach(2, 2).title)
        assertEquals("2 + 2 = ?", Hints.coach(2, 2).scaffold)

        val one = Hints.coach(8, 1)
        assertEquals("1을 곱하면 그대로예요", one.title)
        assertEquals("8 × 1 = 8", one.full)

        val five = Hints.coach(5, 3)
        assertEquals("5씩 3번 세어 봐요", five.scaffold)
        assertEquals("5 × 3 → 5, 10, 15", five.full)

        assertEquals("30 + 12 = ?", Hints.coach(7, 6).scaffold)
        assertEquals("7 × 6 = 30 + 12 = 42", Hints.coach(7, 6).full)
        assertEquals("3 × 4 = 8 + 4 = 12", Hints.coach(3, 4).full)
        assertEquals("12 + 12 = ?", Hints.coach(4, 6).scaffold)
        assertEquals("28 + 28 = ?", Hints.coach(8, 7).scaffold)
        assertEquals("35 + 7 = ?", Hints.coach(6, 7).scaffold)
        assertEquals(Hints.coach(9, 7), Hints.tableTip(9))
    }

    /** 전 범위에서 풀이의 답이 맞고, 푸는 중에 보여 주는 scaffold 에는 정답이 드러나지 않는다 */
    @Test fun coachHintsNeverLeakAnswerAndAreCorrect() {
        for (a in Problems.MIN_TABLE..Problems.MAX_TABLE) {
            for (b in Problems.MIN_B..Problems.MAX_B) {
                val hint = Hints.coach(a, b)
                assertTrue(hint.full.endsWith((a * b).toString()), "$a×$b full=${hint.full}")
                val numbers = Regex("""\d+""").findAll(hint.scaffold).map { it.value.toInt() }.toList()
                assertFalse((a * b) in numbers, "$a×$b scaffold 에 정답 노출: ${hint.scaffold}")
            }
        }
    }

    @Test fun nextRoadmapTable() {
        assertEquals(2, Hints.nextRoadmapTable(emptyMap()))
        val m1 = TableMastery(stars = 1, bestAccuracy = 0.8, bestAvgMs = 3000, plays = 1)
        assertEquals(5, Hints.nextRoadmapTable(mapOf(2 to m1)))

        // 7단만 별이 없다 → 7
        assertEquals(7, Hints.nextRoadmapTable(listOf(2, 3, 4, 5, 6, 8, 9).associateWith { m1 }))

        // 전부 별이 있으면 가장 적은 단(로드맵 순) — 3단·4단이 1개 → 로드맵상 3이 먼저
        fun m(s: Int) = TableMastery(stars = s, bestAccuracy = 1.0, bestAvgMs = 1000, plays = 1)
        val mixed = mapOf(2 to m(3), 5 to m(2), 3 to m(1), 4 to m(1), 6 to m(3), 9 to m(3), 7 to m(3), 8 to m(3))
        assertEquals(3, Hints.nextRoadmapTable(mixed))

        assertNull(Hints.nextRoadmapTable(Hints.ROADMAP_TABLES.associateWith { m(3) }))
    }

    // MARK: Problems.reviewQueue

    @Test fun parseKey() {
        assertEquals(Problem(7, 8), Problems.parseKey("7x8"))
        assertNull(Problems.parseKey("1x5"))
        assertNull(Problems.parseKey("7x0"))
        assertNull(Problems.parseKey("ab"))
        assertNull(Problems.parseKey("7x"))
        assertNull(Problems.parseKey("x8"))
    }

    @Test fun reviewQueuePicksHeaviest() {
        val pool = mapOf("7x8" to 6, "6x9" to 4, "2x3" to 2, "9x9" to 0, "bad" to 5)
        val top2 = Problems.reviewQueue(pool, 2) { 0.0 }
        assertEquals(setOf("7x8", "6x9"), top2.map { Problems.key(it.a, it.b) }.toSet())

        // 가중치 0·형식 오류는 제외 — 남는 것은 3개뿐
        val all = Problems.reviewQueue(pool, 10) { 0.5 }
        assertEquals(setOf("7x8", "6x9", "2x3"), all.map { Problems.key(it.a, it.b) }.toSet())
        assertTrue(Problems.reviewQueue(pool, 0).isEmpty())
        assertTrue(Problems.reviewQueue(emptyMap(), 10).isEmpty())
    }

    @Test fun reviewQueueTieBreakUsesRandom() {
        // 키 정렬 순서(2x2, 3x3, 4x4)대로 난수가 배정된다 → 가장 작은 난수(0.1)의 3x3 이 뽑힌다
        val seq = ArrayDeque(listOf(0.9, 0.1, 0.5))
        val picked = Problems.reviewQueue(mapOf("2x2" to 3, "3x3" to 3, "4x4" to 3), 1) { seq.removeFirst() }
        assertEquals(listOf(Problem(3, 3)), picked)
    }

    // MARK: WeeklyReport

    @Test fun dateShiftAndWeekday() {
        assertEquals("2026-02-28", WeeklyReport.shift("2026-03-01", -1))
        assertEquals("2024-02-29", WeeklyReport.shift("2024-03-01", -1))
        assertEquals("2025-12-26", WeeklyReport.shift("2026-01-01", -6))
        assertEquals("화", WeeklyReport.weekdayLabel("2026-09-29"))
        assertEquals("일", WeeklyReport.weekdayLabel("2026-03-01"))
        assertEquals("목", WeeklyReport.weekdayLabel("2024-02-29"))
    }

    @Test fun weeklyReportAggregates() {
        val log = listOf(
            DayLogEntry("2026-09-15", correct = 100, msSum = 1),                       // 14일 전 — 제외
            DayLogEntry("2026-09-16", correct = 4, msSum = 1),                         // 13일 전 — 지난주
            DayLogEntry("2026-09-22", correct = 7, wrong = 1, msSum = 1, misses = mapOf("2x2" to 1)), // 7일 전
            DayLogEntry("2026-09-23", correct = 3, msSum = 9_000),                     // 6일 전 — 이번 주 첫날
            DayLogEntry("2026-09-27", correct = 5, wrong = 1, msSum = 12_000, misses = mapOf("6x9" to 1, "7x8" to 1)),
            DayLogEntry("2026-09-29", correct = 10, wrong = 2, msSum = 30_000, misses = mapOf("7x8" to 2)),
        )
        val r = WeeklyReport.build(log, "2026-09-29")
        assertEquals(7, r.days.size)
        assertEquals("2026-09-23", r.days.first().date)
        assertEquals("2026-09-29", r.days.last().date)
        assertEquals(listOf(3, 0, 0, 0, 5, 0, 10), r.days.map { it.correct })
        assertEquals(18, r.correct)
        assertEquals(3, r.wrong)
        assertEquals(3, r.activeDays)
        assertEquals(51_000, r.studyMs)
        assertEquals(86, r.accuracy)   // 18/21 = 85.7%
        assertEquals(11, r.prevCorrect)
        assertEquals(7, r.correctDelta)
        assertEquals(listOf(MissCount("7x8", 3), MissCount("6x9", 1)), r.topMisses)
    }

    @Test fun weeklyReportMergesDuplicateDatesAndHandlesEmpty() {
        val dup = listOf(
            DayLogEntry("2026-09-29", correct = 2, wrong = 1, msSum = 100, misses = mapOf("3x4" to 1)),
            DayLogEntry("2026-09-29", correct = 3, wrong = 1, msSum = 200, misses = mapOf("3x4" to 1)),
        )
        val r = WeeklyReport.build(dup, "2026-09-29")
        assertEquals(5, r.correct)
        assertEquals(2, r.wrong)
        assertEquals(listOf(MissCount("3x4", 2)), r.topMisses)

        val empty = WeeklyReport.build(emptyList(), "2026-09-29")
        assertEquals(7, empty.days.size)
        assertNull(empty.accuracy)
        assertEquals(0, empty.activeDays)
        assertTrue(empty.topMisses.isEmpty())
    }

    @Test fun weeklyParentTip() {
        val today = "2026-09-29"
        assertEquals("이번 주는 아직 기록이 없어요. 하루 5분, 한 판부터 함께 시작해 보세요.",
            WeeklyReport.build(emptyList(), today).parentTip)
        // 같은 문제를 2번 이상 틀렸으면 그 단 외우기를 권한다 (8=팔 → "8이에요")
        val missed = listOf(DayLogEntry(today, correct = 5, wrong = 2, msSum = 1, misses = mapOf("7x8" to 2)))
        assertEquals("자주 헷갈리는 문제는 7 × 8이에요. 7단 표를 소리 내어 함께 외워 보세요.",
            WeeklyReport.build(missed, today).parentTip)
        // 하루만 했으면 습관을 권한다
        val once = listOf(DayLogEntry(today, correct = 9, wrong = 1, msSum = 1, misses = mapOf("6x9" to 1)))
        assertTrue(WeeklyReport.build(once, today).parentTip.startsWith("짧게라도 매일"))
        // 사흘 이상 + 정확도 90% 이상이면 속도 도전을 권한다
        val steady = listOf("2026-09-27", "2026-09-28", "2026-09-29").map { DayLogEntry(it, correct = 10, msSum = 1) }
        assertTrue(WeeklyReport.build(steady, today).parentTip.startsWith("정확도가 아주 좋아요"))
    }

    // MARK: Commit — 빈 판

    /** 한 문제도 풀지 않고 끝난 판은 정확도 추이·스트릭·모드 탐험에 남지 않는다 */
    @Test fun emptySessionIsNotCompletion() {
        val today = java.time.LocalDate.of(2026, 9, 29)
        val s = Commit.defaultState.copy(streak = 4, lastPlayedDate = Commit.todayStr(today.minusDays(1)))
        val empty = SessionResult(GameMode.CHALLENGE, null, emptyList(), 0, 60_000)
        val (next, commit) = Commit.applySession(s, empty, today)
        assertTrue(next.recentAccuracy.isEmpty())
        assertEquals(4, next.streak)   // 오늘 자격 없음 → 그대로
        assertFalse(GameMode.CHALLENGE in next.modesPlayed)
        assertNull(commit.score)
        assertFalse(commit.isNewBest)
    }

    /** 끊긴 스트릭은 0 으로 보여 준다 — 저장값은 그대로 둔다 */
    @Test fun activeStreak() {
        val today = java.time.LocalDate.of(2026, 9, 29)
        val s = Commit.defaultState.copy(streak = 4, lastPlayedDate = Commit.todayStr(today))
        assertEquals(4, Commit.activeStreak(s, today))
        assertEquals(4, Commit.activeStreak(s.copy(lastPlayedDate = Commit.todayStr(today.minusDays(1))), today))
        assertEquals(0, Commit.activeStreak(s.copy(lastPlayedDate = Commit.todayStr(today.minusDays(2))), today))
        assertEquals(0, Commit.activeStreak(Commit.defaultState, today))
    }

    // MARK: Achievements.progress

    @Test fun achievementProgress() {
        val s = Commit.defaultState.copy(
            totalCorrect = 142,
            modesPlayed = listOf(GameMode.PRACTICE, GameMode.CHALLENGE),
            tableMastery = mapOf(
                2 to TableMastery(stars = 3, bestAccuracy = 1.0, bestAvgMs = 1000, plays = 2),
                3 to TableMastery(stars = 2, bestAccuracy = 0.9, bestAvgMs = 2000, plays = 1),
            ),
        )
        assertEquals(AchievementProgress(142, 500), Achievements.progress("correct_500", s))
        assertEquals(AchievementProgress(1, 1), Achievements.progress("first_correct", s))
        assertEquals(AchievementProgress(2, 6), Achievements.progress("mode_explorer", s))
        assertEquals(AchievementProgress(5, 12), Achievements.progress("stars_12", s))
        assertNull(Achievements.progress("unknown", s))
    }

    /** 판정과 진행도는 같은 기준 — 진행도가 목표에 닿은 업적만 해금 대상이다 */
    @Test fun achievementCheckMatchesProgress() {
        val s = Commit.defaultState.copy(
            totalCorrect = 120, maxCombo = 12, streak = 7,
            bestScores = mapOf(GameMode.CHALLENGE to 15),
        )
        for (def in Achievements.all) {
            val p = Achievements.progress(def.id, s)!!
            assertEquals(p.current >= p.target, def.check(s), def.id)
        }
        assertEquals(
            setOf("first_correct", "correct_100", "combo_10", "streak_3", "streak_7", "challenge_15"),
            Achievements.newlyUnlocked(s).toSet(),
        )
    }
}
