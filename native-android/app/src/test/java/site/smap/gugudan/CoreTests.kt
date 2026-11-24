package site.smap.gugudan

import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import site.smap.gugudan.core.*
import site.smap.gugudan.core.games.StairsSession
import site.smap.gugudan.features.session.sessionAnswerSlotDigits
import java.time.LocalDate
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// A1 Core 도메인 검증 — iOS(GuguTests/CoreTests.swift) 20개 테스트 패리티

class CoreTests {

    // 이 클래스의 기대값은 곱하는 수 상한 19 기준이다 — 다른 테스트가 바꿔 둔 전역 설정에 기대지 않게 매번 고정한다
    @Before fun fixMaxMultiplier() { Problems.maxB = 19 }

    @After fun restoreMaxMultiplier() { Problems.maxB = Problems.DEFAULT_MAX_B }

    @Test fun readAloudIsAvailableOnlyForKoreanEnglishAndJapanese() {
        assertEquals(
            setOf(AppLanguage.KO, AppLanguage.EN, AppLanguage.JA),
            AppLanguage.entries.filter { it.supportsReadAloud }.toSet(),
        )
        for (language in listOf(AppLanguage.HI, AppLanguage.VI, AppLanguage.ID)) {
            assertFalse(language.supportsReadAloud)
        }
    }


    @Test fun newLanguagesReadEveryNineteenTableProblem() {
        for (code in listOf("hi", "vi", "id")) {
            val language = AppLanguage.of(code)
            for (problem in Problems.candidatesAll()) {
                for (mode in listOf(GameMode.PRACTICE, GameMode.MISSING, GameMode.TRUEFALSE)) {
                    val line = VoiceLines.question(problem, mode, Statement(problem.a * problem.b, true), language)
                    assertTrue(line.text.isNotBlank())
                    assertFalse(VoiceLines.usesRecordedAudio(line, language))
                }
                assertTrue(VoiceLines.chant(problem.a, problem.b, language).text.contains((problem.a * problem.b).toString()))
            }
        }
        assertEquals("19 nhân 19 bằng bao nhiêu?", SpokenMath.questionLabel(19, 19, AppLanguage.VI))
        assertEquals("Berapa 19 kali 19?", SpokenMath.questionLabel(19, 19, AppLanguage.ID))
        assertEquals("19 गुणा 19 कितना होता है?", SpokenMath.questionLabel(19, 19, AppLanguage.HI))
        assertTrue(VoiceLines.usesRecordedAudio(VoiceLines.chant(9, 9, AppLanguage.KO), AppLanguage.KO))
        assertTrue(VoiceLines.usesRecordedAudio(VoiceLines.chant(9, 19, AppLanguage.KO), AppLanguage.KO))
        assertTrue(VoiceLines.usesRecordedAudio(VoiceLines.chant(9, 19, AppLanguage.JA), AppLanguage.JA))
        assertTrue(VoiceLines.usesRecordedAudio(VoiceLines.chant(9, 19, AppLanguage.EN), AppLanguage.EN))
    }

    /** 설정 「곱하는 수」 9 — 19단도 19 × 9까지만 나오고, 이미 쌓인 × 10 이상 기록은 복원·표시만 한다 (iOS 와 같은 기대값) */
    @Test fun maxMultiplierNineLimitsQuestionsButKeepsRecords() {
        Problems.maxB = 9
        val pool = Problems.candidatesAll()
        assertEquals(18 * 9, pool.size)
        assertEquals(Problem(19, 9), pool.last())
        assertEquals((1..9).toList(), Problems.candidates(19).map { it.b })
        assertEquals((1..9).toList(), Problems.candidates(9).map { it.b })

        // 오답 가중이 큰 범위 밖 문제가 있어도 출제는 상한 안에서만
        val heavy = mapOf("19x15" to 6, "19x19" to 6)
        val seed = SeededRandom.mulberry32(7u)
        repeat(300) {
            assertTrue(Problems.pick(19, heavy, emptyList(), seed).b <= 9)
            assertTrue(Problems.pick(null, heavy, emptyList(), seed).b <= 9)
        }

        // 복습 큐는 상한 밖 문제를 건너뛰고, 기록 계약(parseKey)은 19까지 그대로
        assertEquals(listOf(Problem(7, 8)), Problems.reviewQueue(mapOf("7x8" to 2, "7x15" to 6, "19x19" to 6), 10) { 0.5 })
        assertEquals(Problem(7, 15), Problems.parseKey("7x15"))
        assertEquals(Problem(19, 19), Problems.parseKey("19x19"))

        Problems.maxB = 12
        assertEquals(Problem(19, 12), Problems.candidates(19).last())
        assertEquals(18 * 12, Problems.candidatesAll().size)
    }

    @Test fun maxMultiplierRejectsValuesOutsideOptions() {
        assertTrue(runCatching { Problems.maxB = 10 }.isFailure)
        assertEquals(19, Problems.maxB)
    }

    @Test fun nineteenTimesTableRange() {
        val pool = Problems.candidatesAll()
        assertEquals(342, pool.size)
        assertEquals(Problem(19, 19), pool.last())
        assertEquals(Problem(19, 19), Problems.parseKey("19x19"))
        assertNull(Problems.parseKey("20x19"))
        assertNull(Problems.parseKey("19x20"))
        assertEquals(19, Problems.candidates(19).size)
    }

    @Test fun nineteenTimesTableChoicesAndReading() {
        // 구구 계단 19단 꼭대기(「곱하는 수」 19) 발판 — 정답 361 포함 서로 다른 3개, 보기 최대값(19 × 19 + 19) 안
        val choices = StairsSession.makeChoices(Problem(19, 19), standing = 19 * 18, topStep = 19) { 0.999 }
        assertTrue(361 in choices)
        assertEquals(3, choices.distinct().size)
        assertTrue(choices.all { it in 1..StairsSession.maxChoice(table = 19, topStep = 19) })
        assertEquals("three hundred sixty-one", EnglishReading.cardinal(361))
        assertEquals("さんびゃくろくじゅういち", JapaneseReading.number(361))
        assertEquals("じゅうきゅう かける じゅうきゅう は さんびゃくろくじゅういち", JapaneseReading.chant(19, 19))
        assertTrue(site.smap.gugudan.core.learning.LearningContract.isFactId("19x19"))
        assertFalse(site.smap.gugudan.core.learning.LearningContract.isFactId("20x19"))
        assertFalse(site.smap.gugudan.core.learning.LearningContract.isFactId("019x19"))
        assertTrue(site.smap.gugudan.core.learning.LearningContract.isInFactRange(19, 19))
        assertEquals(3, sessionAnswerSlotDigits(Problem(19, 19), GameMode.PRACTICE, ""))
    }


    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // MARK: Level / XP

    @Test fun levelThresholds() {
        assertEquals(60, Level.xpToReach(2))
        assertEquals(155, Level.xpToReach(3))
        assertEquals(1, Level.info(0).level)
        assertEquals(1, Level.info(59).level)
        assertEquals(2, Level.info(60).level)
        assertEquals(3, Level.info(155).level)
    }

    @Test fun levelProgressBounds() {
        val info = Level.info(60)
        assertEquals(0, info.currentLevelXp)
        assertEquals(Level.xpForSpan(2), info.xpForNextLevel)
        assertTrue(info.progress in 0.0..1.0)
    }

    @Test fun xpForAnswer() {
        assertEquals(15, Level.xpForAnswer(GameMode.PRACTICE, combo = 1, ms = 1000))
        assertEquals(17, Level.xpForAnswer(GameMode.PRACTICE, combo = 3, ms = 1000))
        assertEquals(21, Level.xpForAnswer(GameMode.MISSING, combo = 1, ms = 1000))
        assertEquals(10, Level.xpForAnswer(GameMode.PRACTICE, combo = 1, ms = 4000))
    }

    // MARK: Commit

    @Test fun applySessionBasic() {
        val result = SessionResult(GameMode.PRACTICE, 2,
            listOf(AnswerRecord(2, 3, correct = true, ms = 1000)), maxCombo = 1, durationMs = 1000)
        val (next, commit) = Commit.applySession(Commit.defaultState, result)
        assertEquals(15, commit.xpEarned)
        assertEquals(1, next.totalCorrect)
        assertEquals(0, next.totalWrong)
        assertEquals(1, next.dailyCorrect)
        assertTrue("first_correct" in commit.unlocked)
        assertEquals(3, commit.newStars)   // 정확도 1.0, 1000ms → 3별
        assertEquals(3, next.tableMastery[2]?.stars)
    }

    @Test fun applySessionScoredMode() {
        val answers = List(5) { AnswerRecord(3, 4, correct = true, ms = 800) }
        val result = SessionResult(GameMode.CHALLENGE, null, answers, 5, 60000)
        val (next, commit) = Commit.applySession(Commit.defaultState, result)
        assertEquals(5, commit.score)
        assertTrue(commit.isNewBest)
        assertEquals(5, next.bestScores[GameMode.CHALLENGE])
    }

    /** 방문만으로는 스트릭이 오르지 않는다 (학습 자격은 applySession 이 부여) */
    @Test fun applyVisitDoesNotTouchStreak() {
        val today = LocalDate.of(2026, 7, 10)
        val yesterday = Commit.todayStr(today.minusDays(1))
        val s = Commit.defaultState.copy(
            lastPlayedDate = yesterday, streak = 4, dailyDate = yesterday, dailyCorrect = 7,
        )
        val visited = Commit.applyVisit(s, today)
        assertEquals(4, visited.streak)                  // 소급 증가 없음
        assertEquals(yesterday, visited.lastPlayedDate)  // 소급 삭감 없음
        assertEquals(Commit.todayStr(today), visited.dailyDate)
        assertEquals(0, visited.dailyCorrect)            // 날짜 경계에서 일일 정답 초기화
    }

    /** 세션을 한 판 끝내면 스트릭 자격 — 어제 학습했으면 +1, 끊겼으면 1로 리셋 */
    @Test fun qualifyStreakOnSessionComplete() {
        val today = LocalDate.of(2026, 7, 10)
        val result = SessionResult(GameMode.PRACTICE, 2, listOf(AnswerRecord(2, 3, true, 900)), 1, 1000)

        val s = Commit.defaultState.copy(lastPlayedDate = Commit.todayStr(today.minusDays(1)), streak = 4)
        assertEquals(5, Commit.applySession(s, result, today).first.streak)

        val s2 = Commit.defaultState.copy(lastPlayedDate = Commit.todayStr(today.minusDays(2)), streak = 9)
        assertEquals(1, Commit.applySession(s2, result, today).first.streak)
    }

    /** 부분 커밋은 한 판 완료 통계를 건드리지 않는다. 단 일일 정답 하한을 넘기면 스트릭 자격은 준다. */
    @Test fun partialCommitSkipsCompletionStats() {
        val today = LocalDate.of(2026, 7, 10)
        val answers = List(3) { AnswerRecord(3, 4, correct = true, ms = 800) }
        val partial = SessionResult(GameMode.PRACTICE, 3, answers, 3, 3000, partial = true)
        // dailyDate 를 오늘로 맞춰야 날짜 경계 초기화에 걸리지 않는다. streakFloor = min(20, 10) = 10
        val base = Commit.defaultState.copy(dailyGoal = 20, dailyDate = Commit.todayStr(today))

        val (next, commit) = Commit.applySession(base, partial, today)
        assertTrue(commit.partial)
        assertTrue(commit.xpEarned > 0)                  // XP 는 반영
        assertEquals(3, next.dailyCorrect)               // 일일 정답도 반영
        assertTrue(next.recentAccuracy.isEmpty())        // 추이 미반영
        assertNull(next.tableMastery[3])                 // 별점 미반영
        assertTrue(next.modesPlayed.isEmpty())           // 모드 탐험 미반영
        assertEquals(0, next.streak)                     // 하한(10) 미달 → 자격 없음

        // 하한 도달 시엔 부분 커밋도 스트릭 자격
        val (next2, _) = Commit.applySession(base.copy(dailyCorrect = 9), partial, today)
        assertEquals(1, next2.streak)
        assertEquals(Commit.todayStr(today), next2.lastPlayedDate)
    }

    /** XP 배율(xpScale) — 0.3배면 XP 도 그만큼 줄어든다 (예전 어드벤처 재대결이 쓰던 값) */
    @Test fun rematchXpScale() {
        val xpScale = 0.3
        val answers = List(4) { AnswerRecord(6, 7, correct = true, ms = 1200) }
        val full = SessionResult(GameMode.PRACTICE, 6, answers, 4, 5000)
        val scaled = full.copy(xpScale = xpScale)
        val base = Commit.applySession(Commit.defaultState, full).second.xpEarned
        val cut = Commit.applySession(Commit.defaultState, scaled).second.xpEarned
        assertTrue(base > cut, "base=$base cut=$cut")
        // 문항별 반올림 누적 오차를 감안해 ±문항 수 범위로 검증
        assertTrue(abs(cut - Math.round(base * xpScale).toInt()) <= answers.size)
    }

    /** 날짜별 링버퍼 — 같은 날은 한 버킷에 누적, 오답은 식별 키로 집계 */
    @Test fun dayLogAccumulates() {
        val today = LocalDate.of(2026, 7, 10)
        val answers = listOf(
            AnswerRecord(2, 3, correct = true, ms = 1000),
            AnswerRecord(4, 5, correct = false, ms = 2000),
        )
        val result = SessionResult(GameMode.PRACTICE, null, answers, 1, 3000)
        val (first, _) = Commit.applySession(Commit.defaultState, result, today)
        val (second, _) = Commit.applySession(first, result, today)

        assertEquals(1, second.dayLog.size)
        val day = second.dayLog.first()
        assertEquals(Commit.todayStr(today), day.date)
        assertEquals(2, day.correct)
        assertEquals(2, day.wrong)
        assertEquals(2, day.misses["4x5"])
        assertEquals(6000, day.msSum)
        assertEquals(
            mapOf(
                2 to TableTally(correct = 2, wrong = 0, msSum = 2000),
                4 to TableTally(correct = 0, wrong = 2, msSum = 4000),
            ),
            day.tables,
        )
    }

    /** 세션·게임(계단·몬스터)·중도 이탈 부분 기록이 모두 AnswerRecord.a 단별로 같은 날에 합산된다 */
    @Test fun dayLogTablesAccumulateAcrossSessionGameAndPartial() {
        val today = LocalDate.of(2026, 11, 24)
        var state = Commit.defaultState

        // 1. 일반 세션 (7단 1정답 1오답)
        state = Commit.applySession(
            state,
            SessionResult(
                GameMode.PRACTICE, 7,
                listOf(
                    AnswerRecord(7, 3, correct = true, ms = 1200),
                    AnswerRecord(7, 8, correct = false, ms = 3500),
                ),
                maxCombo = 1, durationMs = 4700,
            ),
            today,
        ).first

        // 2. 구구 계단 게임 (7단 1정답, 8단 1정답 — 판 중 단이 섞임)
        state = Commit.applySession(
            state,
            SessionResult(
                GameMode.STAIRS, null,
                listOf(
                    AnswerRecord(7, 4, correct = true, ms = 1000, GivenAnswer.Number(28)),
                    AnswerRecord(8, 6, correct = true, ms = 1500, GivenAnswer.Number(48)),
                ),
                maxCombo = 2, durationMs = 2500,
            ),
            today,
        ).first

        // 3. 구구 몬스터 중도 이탈 부분 기록 (8단 1오답, 12단 1정답)
        state = Commit.applySession(
            state,
            SessionResult(
                GameMode.MONSTER, null,
                listOf(
                    AnswerRecord(8, 7, correct = false, ms = 2200, GivenAnswer.Number(54)),
                    AnswerRecord(12, 3, correct = true, ms = 1800, GivenAnswer.Number(36)),
                ),
                maxCombo = 1, durationMs = 4000, partial = true,
            ),
            today,
        ).first

        val day = state.dayLog.single()
        assertEquals(Commit.todayStr(today), day.date)
        assertEquals(
            mapOf(
                7 to TableTally(correct = 2, wrong = 1, msSum = 5700),
                8 to TableTally(correct = 1, wrong = 1, msSum = 3700),
                12 to TableTally(correct = 1, wrong = 0, msSum = 1800),
            ),
            day.tables,
        )
    }

    /** 날짜 중복 병합은 단별 합계(tables)도 합친다 — 둘 다 null 이면 null 유지, 한쪽만 있으면 보존 */
    @Test fun dayLogMergeCombinesTables() {
        val merged = Commit.mergeDayLog(
            listOf(
                DayLogEntry(
                    "2026-11-24", correct = 2, wrong = 1, msSum = 3000,
                    misses = mapOf("7x8" to 1),
                    tables = mapOf(7 to TableTally(2, 1, 3000)),
                ),
                DayLogEntry(
                    "2026-11-24", correct = 3, wrong = 0, msSum = 4000,
                    tables = mapOf(7 to TableTally(1, 0, 1500), 5 to TableTally(2, 0, 2500)),
                ),
                DayLogEntry("2026-11-23", correct = 4, wrong = 0, msSum = 2000, tables = null),
                DayLogEntry("2026-11-23", correct = 1, wrong = 1, msSum = 1000, tables = null),
                DayLogEntry("2026-11-22", correct = 5, wrong = 0, msSum = 2000, tables = null),
                DayLogEntry(
                    "2026-11-22", correct = 2, wrong = 0, msSum = 1000,
                    tables = mapOf(3 to TableTally(2, 0, 1000)),
                ),
            ),
        )
        assertEquals(
            mapOf(
                7 to TableTally(3, 1, 4500),
                5 to TableTally(2, 0, 2500),
            ),
            merged["2026-11-24"]?.tables,
        )
        assertNull(merged["2026-11-23"]?.tables)
        assertEquals(mapOf(3 to TableTally(2, 0, 1000)), merged["2026-11-22"]?.tables)
    }

    /** 구버전 저장본(dayLog 키 없음)도 디코딩되고 구기록은 보존된다 */
    @Test fun dayLogMigrationFromLegacyPayload() {
        val legacy = """{"version":1,"totalXp":120,"streak":3,"lastPlayedDate":"2026-01-01","dailyGoal":20,"dailyDate":"2026-01-01","dailyCorrect":5,"totalCorrect":10,"totalWrong":2,"maxCombo":4,"recentAccuracy":[90],"recentAvgMs":[1200],"wrongPool":{"2x3":2},"modesPlayed":["practice"],"onboarded":true}"""
        val decoded = json.decodeFromString(GameState.serializer(), legacy)
        assertTrue(decoded.dayLog.isEmpty())
        assertEquals(120, decoded.totalXp)
        assertEquals(3, decoded.streak)   // 구기록 보존
        val visited = Commit.applyVisit(decoded)
        assertTrue(visited.dayLog.isEmpty())
        assertEquals(3, visited.streak)
    }

    /** 모드 탐험가 — 학습 탭 모드 전부를 플레이해야 해금. ADVENTURE 는 조건에 포함되지 않는다. */
    @Test fun modeExplorerRequiresEveryLearningMode() {
        val partial = Commit.defaultState.copy(modesPlayed = listOf(
            GameMode.PRACTICE, GameMode.TIME_ATTACK, GameMode.CHALLENGE,
            GameMode.SURVIVAL, GameMode.MISSING, GameMode.ADVENTURE,
        ))
        assertFalse("mode_explorer" in Achievements.newlyUnlocked(partial))  // TRUEFALSE 미플레이

        val complete = Commit.defaultState.copy(modesPlayed = Modes.list.map { it.id })
        assertTrue("mode_explorer" in Achievements.newlyUnlocked(complete))
    }

    // MARK: Problems

    /** 오답 가중치가 가장 많이 쌓인 단 — 범위 밖/깨진 키는 무시 */
    @Test fun dominantWrongTable() {
        assertNull(Problems.dominantWrongTable(emptyMap()))
        assertEquals(7, Problems.dominantWrongTable(mapOf("2x3" to 2, "7x8" to 4, "7x2" to 2)))
        assertNull(Problems.dominantWrongTable(mapOf("x3" to 5, "99x9" to 6, "3x4" to 0)))
    }

    @Test fun updateWrongPool() {
        var pool: Map<String, Int> = emptyMap()
        pool = Problems.updateWrongPool(pool, 2, 3, correct = false)
        assertEquals(2, pool["2x3"])
        pool = Problems.updateWrongPool(pool, 2, 3, correct = false)
        assertEquals(4, pool["2x3"])
        pool = Problems.updateWrongPool(pool, 2, 3, correct = true)
        assertEquals(3, pool["2x3"])
        pool = mapOf("2x3" to 6)
        pool = Problems.updateWrongPool(pool, 2, 3, correct = false)
        assertEquals(6, pool["2x3"])   // cap 6
    }

    @Test fun pickDeterministicWithInjectedRandom() {
        val p = Problems.pick(table = 2, wrongPool = emptyMap(), recentKeys = emptyList(), random = { 0.0 })
        assertEquals(2, p.a)
        assertEquals(1, p.b)
    }

    @Test fun makeStatementTrueBranch() {
        val st = Problems.makeStatement(Problem(3, 4), random = { 0.0 })
        assertTrue(st.isTrue)
        assertEquals(12, st.shown)
    }

    @Test fun sessionAnswerSlotDigitsStayAtAnswerWidth() {
        val twoDigit = Problem(9, 9)
        assertEquals(2, sessionAnswerSlotDigits(twoDigit, GameMode.PRACTICE, input = ""))
        assertEquals(2, sessionAnswerSlotDigits(twoDigit, GameMode.PRACTICE, input = "8"))
        assertEquals(2, sessionAnswerSlotDigits(twoDigit, GameMode.PRACTICE, input = "81"))
        assertEquals(3, sessionAnswerSlotDigits(twoDigit, GameMode.PRACTICE, input = "100"))

        val missing = Problem(9, 9)
        assertEquals(1, sessionAnswerSlotDigits(missing, GameMode.MISSING, input = ""))
    }

    // MARK: 영속화 라운드트립

    @Test fun gameStateJsonRoundTrip() {
        val s = GameState(
            version = 1,
            totalXp = 500,
            streak = 4,
            lastPlayedDate = "2026-11-24",
            dailyGoal = 30,
            dailyDate = "2026-11-24",
            dailyCorrect = 12,
            tableMastery = mapOf(2 to TableMastery(3, 1.0, 900, 4)),
            unlockedAchievements = listOf("first_correct", "streak_3"),
            totalCorrect = 120,
            totalWrong = 15,
            maxCombo = 18,
            recentAccuracy = listOf(80, 95),
            recentAvgMs = listOf(2100, 1400),
            wrongPool = mapOf("2x3" to 4, "7x8" to 2),
            bestScores = mapOf(GameMode.CHALLENGE to 15, GameMode.SURVIVAL to 9),
            modesPlayed = listOf(GameMode.PRACTICE, GameMode.CHALLENGE, GameMode.STAIRS),
            onboarded = true,
            dayLog = listOf(
                DayLogEntry("2026-11-23", correct = 10, wrong = 1, msSum = 18_000, misses = mapOf("7x8" to 1), tables = null),
                DayLogEntry(
                    "2026-11-24", correct = 12, wrong = 2, msSum = 24_000,
                    misses = mapOf("7x8" to 1, "8x7" to 1),
                    tables = mapOf(
                        7 to TableTally(correct = 7, wrong = 1, msSum = 14_000),
                        8 to TableTally(correct = 5, wrong = 1, msSum = 10_000),
                    ),
                ),
            ),
        )
        val decoded = json.decodeFromString<GameState>(json.encodeToString(GameState.serializer(), s))
        assertEquals(s, decoded)
    }

    // MARK: 직렬화 키 호환 (iOS/웹과 동일 문자열)

    @Test fun gameModeSerialNames() {
        val encoded = json.encodeToString(GameState.serializer(),
            Commit.defaultState.copy(bestScores = mapOf(GameMode.TIME_ATTACK to 3), modesPlayed = listOf(GameMode.TRUEFALSE)))
        assertTrue("timeAttack" in encoded, encoded)
        assertTrue("truefalse" in encoded)
    }
}
