package site.smap.gugudan

import kotlinx.serialization.json.Json
import org.junit.Test
import site.smap.gugudan.core.*
import site.smap.gugudan.core.adventure.*
import site.smap.gugudan.features.session.sessionAnswerSlotDigits
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// A1 Core 도메인 검증 — iOS(GuguTests/CoreTests.swift) 20개 테스트 패리티

class CoreTests {

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

    /** 재대결 XP 배율 — 원본 XP의 0.3배로 체감 */
    @Test fun rematchXpScale() {
        val answers = List(4) { AnswerRecord(6, 7, correct = true, ms = 1200) }
        val full = SessionResult(GameMode.ADVENTURE, 6, answers, 4, 5000)
        val scaled = full.copy(xpScale = Battle.REMATCH_XP_SCALE)
        val base = Commit.applySession(Commit.defaultState, full).second.xpEarned
        val cut = Commit.applySession(Commit.defaultState, scaled).second.xpEarned
        assertTrue(base > cut, "base=$base cut=$cut")
        // 문항별 반올림 누적 오차를 감안해 ±문항 수 범위로 검증
        assertTrue(abs(cut - Math.round(base * Battle.REMATCH_XP_SCALE).toInt()) <= answers.size)
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

    // MARK: Adventure

    @Test fun regionsStructure() {
        assertEquals(8, World.regions.size)
        for (r in World.regions) {
            assertEquals(4, r.npcs.size)
            assertEquals(1, r.npcs.count { it.kind == NpcKind.BOSS })
            assertTrue(r.decoItems.size in 12..16, "${r.name} 소품 수")
        }
        assertEquals((2..9).toList(), World.regions.map { it.table })
    }

    @Test fun regionUnlockChain() {
        var p = AdvProgress.defaultProgress
        assertTrue(AdvProgress.isRegionUnlocked(p, 2))
        assertFalse(AdvProgress.isRegionUnlocked(p, 3))
        p = p.copy(defeatedNpcs = p.defeatedNpcs + World.bossId(2))
        assertTrue(AdvProgress.isRegionUnlocked(p, 3))
    }

    /** 로드맵(2,5,3,4,6,9,7,8) 직전 보스 격파 또는 해당 단 별 1개로도 해금된다 */
    @Test fun regionUnlockByRoadmapOrStars() {
        var p = AdvProgress.defaultProgress
        assertFalse(AdvProgress.isRegionUnlocked(p, 5))
        // 로드맵상 5단의 직전은 2단 → 2단 보스 격파로 5단 해금
        p = p.copy(defeatedNpcs = p.defeatedNpcs + World.bossId(2))
        assertTrue(AdvProgress.isRegionUnlocked(p, 5))

        // 별점만으로도 해금 (직전 보스 미격파여도)
        val fresh = AdvProgress.defaultProgress
        assertFalse(AdvProgress.isRegionUnlocked(fresh, 8))
        assertTrue(AdvProgress.isRegionUnlocked(fresh, 8, tableStars = 1))

        assertEquals(2, AdvProgress.roadmapPrevTable(5))
        assertNull(AdvProgress.roadmapPrevTable(2))
    }

    /** 배틀 커밋은 adventure 모드로 들어가 학습 모드 기록과 섞이지 않는다 */
    @Test fun battleCommitsAsAdventureMode() {
        val npc = World.regions.first().npcs.first()
        val result = Battle.toSessionResult(npc, emptyList(), 0, 1000)
        assertEquals(GameMode.ADVENTURE, result.mode)
        assertFalse(Modes.def(GameMode.ADVENTURE).scored)
        assertFalse(Modes.list.any { it.id == GameMode.ADVENTURE })  // 학습 탭 목록엔 없음
    }

    @Test fun applyBattle() {
        val (next, unlocked) = AdvProgress.applyBattle(AdvProgress.defaultProgress, "r2-n1", won = true)
        assertEquals(1, next.battlesWon)
        assertTrue("r2-n1" in next.defeatedNpcs)
        assertTrue("adv_first_win" in unlocked)
        // 중복 격파는 목록에 다시 추가되지 않음
        val (again, _) = AdvProgress.applyBattle(next, "r2-n1", won = true)
        assertEquals(1, again.defeatedNpcs.count { it == "r2-n1" })
    }

    @Test fun battleDamage() {
        assertEquals(32, Battle.damage(ms = 1000, combo = 3))   // 20+8+4
        assertEquals(20, Battle.damage(ms = 5000, combo = 1))
    }

    // MARK: 시드 난수 결정성

    @Test fun mulberry32Deterministic() {
        val r1 = World.mulberry32(12345u)
        val r2 = World.mulberry32(12345u)
        repeat(50) { assertEquals(r1(), r2(), 0.0) }
        val a = World.genDecoItems(2, World.layouts.getValue(2))
        val b = World.genDecoItems(2, World.layouts.getValue(2))
        assertEquals(a, b)
    }

    // MARK: 영속화 라운드트립

    @Test fun gameStateJsonRoundTrip() {
        val s = Commit.defaultState.copy(
            totalXp = 500,
            tableMastery = mapOf(2 to TableMastery(3, 1.0, 900, 4)),
            bestScores = mapOf(GameMode.CHALLENGE to 15),
            modesPlayed = listOf(GameMode.PRACTICE, GameMode.CHALLENGE),
            wrongPool = mapOf("2x3" to 4),
        )
        val decoded = json.decodeFromString<GameState>(json.encodeToString(GameState.serializer(), s))
        assertEquals(s, decoded)
    }

    @Test fun adventureProgressJsonRoundTrip() {
        val p = AdvProgress.defaultProgress.copy(
            defeatedNpcs = listOf("r2-n1", "r2-boss"),
            battlesWon = 2,
            achievements = listOf("adv_first_win"),
            starShards = 7,
            ownedColors = listOf("rose"),
            ownedHats = listOf("sprout"),
            equippedColor = "rose",
            equippedHat = "sprout",
        )
        val decoded = json.decodeFromString<AdventureProgress>(json.encodeToString(AdventureProgress.serializer(), p))
        assertEquals(p, decoded)
    }

    @Test fun adventureProgressDecodesLegacyStorage() {
        // 구버전 저장소(신규 필드 없던 시절) — 누락 키는 기본값으로
        val legacy = """{"version":1,"defeatedNpcs":["r2-n1"],"battlesWon":1,"battlesLost":0,"achievements":[]}"""
        val p = json.decodeFromString<AdventureProgress>(legacy)
        assertEquals(listOf("r2-n1"), p.defeatedNpcs)
        assertEquals(0, p.starShards)
        assertEquals("indigo", p.equippedColor)
    }

    // MARK: 별 조각 / 상점

    @Test fun starSpots() {
        for (r in World.regions) {
            assertEquals(5, r.starSpots.size, "${r.name} 별 조각 수")
            for (s in r.starSpots) {
                assertTrue(abs(s.x) <= 13.5 && abs(s.z) <= 13.5)
                for (n in r.npcs) {
                    assertTrue(hypot(n.pos.x - s.x, n.pos.z - s.z) > 2.0)
                }
            }
        }
        val a = World.genStarSpots(2, World.layouts.getValue(2))
        val b = World.genStarSpots(2, World.layouts.getValue(2))
        assertEquals(a, b)
    }

    @Test fun shopCatalog() {
        assertEquals(Shop.colors.size, Shop.colors.map { it.id }.toSet().size)
        assertEquals(Shop.hats.size, Shop.hats.map { it.id }.toSet().size)
        val base = Shop.color(Shop.DEFAULT_COLOR_ID)
        assertNotNull(base)
        assertEquals(0, base.price)
        Shop.colors.filter { it.id != Shop.DEFAULT_COLOR_ID }.forEach { assertTrue(it.price > 0) }
        Shop.hats.forEach { assertTrue(it.price > 0) }
    }

    // MARK: Runner (구구 점프)

    /** 보기는 정답 1개 + 서로 다른 오답 2개, 모두 1..81 범위 */
    @Test fun runnerQuestionChoices() {
        repeat(50) {
            val q = Runner.question(null, emptyList())
            assertEquals(3, q.choices.size)
            assertEquals(3, q.choices.distinct().size)
            assertTrue(q.answer in q.choices)
            q.choices.forEach { assertTrue(it in 1..81, "보기 범위 이탈: $it") }
        }
        // 단 지정 시 해당 단만 출제
        repeat(20) { assertEquals(7, Runner.question(7, emptyList()).a) }
    }

    /** 제한 시간 — 5개마다 450ms 감소, 하한 3200ms */
    @Test fun runnerAnswerWindow() {
        assertEquals(5000.0, Runner.answerWindowMs(0))
        assertEquals(5000.0 - 500.0, Runner.answerWindowMs(3))      // 3개마다 한 단계
        assertEquals(5000.0 - 500.0 * 3, Runner.answerWindowMs(11))
        assertEquals(2800.0, Runner.answerWindowMs(200))   // 하한 고정
    }

    /** 정답/오답 판정 — 보기에 없는 값이나 이미 판정된 상태는 무시 */
    @Test fun runnerAnswerGuards() {
        val s = Runner.create(3)
        val correct = Runner.answer(s, s.question.answer)
        assertEquals(RunnerOutcome.CORRECT, correct.outcome)
        assertEquals(s.question.answer, correct.given)

        val wrongChoice = s.question.choices.first { it != s.question.answer }
        assertEquals(RunnerOutcome.WRONG, Runner.answer(s, wrongChoice).outcome)

        // 보기에 없는 값
        val absent = (1..81).first { it !in s.question.choices }
        assertEquals(null, Runner.answer(s, absent).outcome)
        // 이미 판정된 뒤에는 바뀌지 않는다
        assertEquals(RunnerOutcome.CORRECT, Runner.answer(correct, wrongChoice).outcome)
        // READY 상태에서는 입력을 받지 않는다
        val ready = Runner.create(3, RunnerPhase.READY)
        assertEquals(null, Runner.answer(ready, ready.question.choices.first()).outcome)
    }

    /** 시간 초과 → 하트 1개 감소 + 피격 연출, 콤보 초기화 */
    @Test fun runnerMissedCostsLife() {
        var s = Runner.create(2)
        s = s.copy(combo = 4)
        // 제한 시간을 한 번에 소진
        s = Runner.advance(s, Runner.answerWindowMs(0))
        assertEquals(RunnerOutcome.MISSED, s.outcome)
        assertEquals(Runner.MAX_LIVES - 1, s.lives)
        assertEquals(0, s.combo)
        assertEquals(Runner.COLLISION_X, s.obstacleX)
        assertEquals(Runner.HIT_DURATION_MS, s.hitMs)

        // 연출 중에는 진행만 흐르고, 끝나면 다음 장애물로 교체
        s = Runner.advance(s, Runner.HIT_DURATION_MS)
        assertEquals(null, s.outcome)
        assertEquals(Runner.OBSTACLE_START, s.obstacleX)
        assertEquals(1, s.round)
    }

    /** 하트를 모두 잃고 연출이 끝나면 OVER */
    @Test fun runnerGameOverAfterLastLife() {
        var s = Runner.create(2).copy(lives = 1)
        s = Runner.advance(s, Runner.answerWindowMs(0))
        assertEquals(0, s.lives)
        s = Runner.advance(s, Runner.HIT_DURATION_MS)
        assertEquals(RunnerPhase.OVER, s.phase)
    }

    /** 정답 후 장애물이 화면을 벗어나면 점수·콤보 증가 + 최고 콤보 갱신 */
    @Test fun runnerClearingObstacleScores() {
        var s = Runner.answer(Runner.create(2), Runner.create(2).question.answer)
        // 정답 상태를 직접 구성 (문제는 create 마다 달라지므로 재구성)
        val base = Runner.create(2)
        s = Runner.answer(base, base.question.answer)
        assertEquals(RunnerOutcome.CORRECT, s.outcome)
        var guard = 0
        while (s.score == 0 && guard++ < 10_000) s = Runner.advance(s, 16.0)
        assertEquals(1, s.score)
        assertEquals(1, s.combo)
        assertEquals(1, s.maxCombo)
        assertEquals(Runner.MAX_LIVES, s.lives)   // 하트 유지
        assertEquals(1, s.round)                  // 다음 장애물
    }

    /** 점프 높이 — 정답일 때만, 구간 안에서만 솟는다 */
    @Test fun runnerJumpArc() {
        val base = Runner.create(2)
        assertEquals(0.0, Runner.jump(base))   // 미판정 상태
        val correct = Runner.answer(base, base.question.answer)
        assertEquals(0.0, Runner.jump(correct.copy(obstacleX = Runner.JUMP_START + 1)))  // 아직 이름
        assertEquals(0.0, Runner.jump(correct.copy(obstacleX = Runner.JUMP_END - 1)))    // 이미 지남
        val mid = (Runner.JUMP_START + Runner.JUMP_END) / 2
        assertEquals(96.0, Runner.jump(correct.copy(obstacleX = mid)), 0.001)            // 정점
        val wrong = Runner.answer(base, base.question.choices.first { it != base.question.answer })
        assertEquals(0.0, Runner.jump(wrong.copy(obstacleX = mid)))                      // 오답은 못 넘는다
    }

    /** 일시정지/비정상 dt 는 상태를 바꾸지 않는다 */
    @Test fun runnerAdvanceGuards() {
        val paused = Runner.create(2).copy(phase = RunnerPhase.PAUSED)
        assertEquals(paused.obstacleX, Runner.advance(paused, 16.0).obstacleX)
        val running = Runner.create(2)
        assertEquals(running.obstacleX, Runner.advance(running, 0.0).obstacleX)
        assertEquals(running.obstacleX, Runner.advance(running, Double.NaN).obstacleX)
    }

    // MARK: LaneRunner (구구 레인)

    /** 속도 단계 — 정답 길 3개마다 한 단계, 각 구간 값은 하한에서 멈춘다 */
    @Test fun laneSpeedSteps() {
        assertEquals(0, LaneRunner.level(2))
        assertEquals(1, LaneRunner.level(3))
        assertEquals(3400.0, LaneRunner.travelMs(0))
        assertEquals(3400.0 - 250.0, LaneRunner.travelMs(3))
        assertEquals(1900.0, LaneRunner.travelMs(300))      // 하한 고정
        assertEquals(5000.0, LaneRunner.quizWindowMs(0))
        assertEquals(3500.0, LaneRunner.quizWindowMs(300))  // 하한 고정
        assertEquals(750.0, LaneRunner.spawnGapMs(300))     // 하한 고정
        assertEquals(4, LaneRunner.obstaclesPerCycle(300))  // 상한 고정
    }

    /** 레인 이동 — 범위 밖/같은 레인/비RUNNING 은 무시하고, 전환 중에는 보간 위치를 남긴다 */
    @Test fun laneMoveGuards() {
        val s = LaneRunner.create(2)
        assertEquals(1, s.lane)
        assertEquals(1, LaneRunner.setLane(s, 1).lane)                 // 같은 레인
        assertEquals(1, LaneRunner.setLane(s, 3).lane)                 // 범위 밖
        assertEquals(1, LaneRunner.move(LaneRunner.create(2, RunnerPhase.READY), -1).lane)

        val up = LaneRunner.move(s, -1)
        assertEquals(0, up.lane)
        assertEquals(1.0, up.laneFrom)
        assertEquals(LaneRunner.LANE_ANIM_MS, up.laneAnimMs)
        // 전환 직후에는 이전 레인에 가깝고, 애니메이션이 끝나면 새 레인에 정확히 놓인다
        assertEquals(1.0, LaneRunner.offset(up), 0.001)
        assertEquals(0.0, LaneRunner.offset(up.copy(laneAnimMs = 0.0)), 0.001)
    }

    /** 내 레인을 막은 장애물은 하트를 깎고, 다른 레인 장애물은 피한 수로 센다 */
    @Test fun laneObstacleJudgement() {
        val base = LaneRunner.create(2).copy(
            obstacles = listOf(
                LaneObstacle(0, 1, LaneRunner.JUDGE_X + 1, LaneObstacleKind.ROCK),
                LaneObstacle(1, 0, LaneRunner.JUDGE_X + 1, LaneObstacleKind.ROCK),
            ),
        )
        val hit = LaneRunner.advance(base, 16.0)
        assertEquals(LaneOutcome.HIT, hit.outcome)
        assertEquals(LaneRunner.MAX_LIVES - 1, hit.lives)
        assertEquals(LaneRunner.HIT_MS, hit.hitMs)
        // 부딪힌 열의 나머지 장애물은 피한 것으로 세지 않는다
        assertEquals(0, hit.dodged)

        val dodged = LaneRunner.advance(base.copy(lane = 2), 16.0)
        assertEquals(null, dodged.outcome)
        assertEquals(2, dodged.dodged)
        assertEquals(LaneRunner.MAX_LIVES, dodged.lives)
    }

    /** 게이트 판정 — 내 레인의 숫자가 정답이면 점수, 아니면 하트가 줄어든다 */
    @Test fun laneGateJudgement() {
        val question = RunnerQuestion(3, 4, listOf(12, 10, 14))
        val base = LaneRunner.create(3).copy(
            segment = LaneSegment.QUIZ,
            question = question,
            gate = LaneGate(LaneRunner.JUDGE_X + 1, 600.0, question.choices),
        )
        val correct = LaneRunner.advance(base.copy(lane = 0), 16.0)
        assertEquals(LaneOutcome.CORRECT, correct.outcome)
        assertEquals(1, correct.score)
        assertEquals(1, correct.combo)
        assertEquals(1, correct.maxCombo)
        assertEquals(LaneRunner.MAX_LIVES, correct.lives)
        assertEquals(12, correct.given)

        val wrong = LaneRunner.advance(base.copy(lane = 1, combo = 2), 16.0)
        assertEquals(LaneOutcome.WRONG, wrong.outcome)
        assertEquals(0, wrong.score)
        assertEquals(0, wrong.combo)
        assertEquals(LaneRunner.MAX_LIVES - 1, wrong.lives)
        assertEquals(10, wrong.given)
    }

    /** 속도 UP 판정 — 정답이고 누적 점수가 단계 배수일 때만 */
    @Test fun laneLeveledUp() {
        val s = LaneRunner.create(2)
        assertFalse(LaneRunner.leveledUp(s.copy(outcome = LaneOutcome.CORRECT, score = 0)))
        assertFalse(LaneRunner.leveledUp(s.copy(outcome = LaneOutcome.CORRECT, score = 2)))
        assertTrue(LaneRunner.leveledUp(s.copy(outcome = LaneOutcome.CORRECT, score = 3)))
        assertFalse(LaneRunner.leveledUp(s.copy(outcome = LaneOutcome.WRONG, score = 3)))
    }

    /** 하트를 모두 잃고 연출이 끝나면 OVER, 일시정지·비정상 dt 는 무시 */
    @Test fun laneGameOverAndGuards() {
        val hit = LaneRunner.create(2).copy(lives = 0, hitMs = 10.0, outcome = LaneOutcome.HIT)
        assertEquals(RunnerPhase.OVER, LaneRunner.advance(hit, 16.0).phase)

        val paused = LaneRunner.create(2).copy(phase = RunnerPhase.PAUSED)
        assertEquals(paused.distance, LaneRunner.advance(paused, 16.0).distance)
        val running = LaneRunner.create(2)
        assertEquals(running.distance, LaneRunner.advance(running, 0.0).distance)
        assertEquals(running.distance, LaneRunner.advance(running, Double.NaN).distance)
    }

    // MARK: Basket (구구 바구니)

    /** 낙하 시간 — 정답 3개마다 한 단계 빨라지고 2.2초가 하한 */
    @Test fun basketFallSteps() {
        assertEquals(1, Basket.level(0))
        assertEquals(2, Basket.level(3))
        assertEquals(3000.0, Basket.fallDurationMs(0))
        assertEquals(2700.0, Basket.fallDurationMs(3))
        assertEquals(2200.0, Basket.fallDurationMs(300))   // 하한 고정
    }

    /** 바구니 이동 — 좌우 끝을 넘지 않고, 판정 중/비RUNNING 은 무시 */
    @Test fun basketMoveGuards() {
        val s = Basket.create(2)
        assertEquals(Basket.MIN_X, Basket.move(s, -100.0).x)
        assertEquals(Basket.MAX_X, Basket.move(s, 9999.0).x)
        assertEquals(s.x, Basket.move(s, Double.NaN).x)
        assertEquals(s.x, Basket.move(s.copy(outcome = BasketOutcome.CORRECT), 100.0).x)
        assertEquals(s.x, Basket.move(Basket.create(2, RunnerPhase.READY), 100.0).x)
    }

    /** 열매 판정 — 정답을 받으면 점수, 다른 열매는 오답, 아무것도 못 받으면 놓침 */
    @Test fun basketCatchJudgement() {
        val question = RunnerQuestion(3, 4, listOf(12, 10, 14))
        val duration = Basket.fallDurationMs(0)
        val base = Basket.create(2).copy(question = question, elapsedMs = duration - 1)

        val correct = Basket.advance(base.copy(x = Basket.fruitX(0, duration)), 16.0)
        assertEquals(BasketOutcome.CORRECT, correct.outcome)
        assertEquals(1, correct.score)
        assertEquals(1, correct.combo)
        assertEquals(Basket.MAX_LIVES, correct.lives)
        assertEquals(Basket.CORRECT_FEEDBACK_MS, correct.feedbackMs)

        val wrong = Basket.advance(base.copy(x = Basket.fruitX(1, duration), combo = 2), 16.0)
        assertEquals(BasketOutcome.WRONG, wrong.outcome)
        assertEquals(0, wrong.score)
        assertEquals(0, wrong.combo)
        assertEquals(Basket.MAX_LIVES - 1, wrong.lives)

        // 어느 열매와도 겹치지 않는 열매 사이 — 놓침
        val gap = (Basket.fruitX(0, duration) + Basket.fruitX(1, duration)) / 2
        val missed = Basket.advance(base.copy(x = gap), 16.0)
        assertEquals(BasketOutcome.MISSED, missed.outcome)
        assertEquals(null, missed.caughtIndex)
        assertEquals(Basket.MAX_LIVES - 1, missed.lives)
    }

    /** 연출이 끝나면 다음 문제로 넘어가고, 하트가 없으면 OVER */
    @Test fun basketAdvancesRoundAndEnds() {
        val done = Basket.create(2).copy(outcome = BasketOutcome.CORRECT, feedbackMs = 10.0, elapsedMs = 500.0)
        val next = Basket.advance(done, 16.0)
        assertEquals(null, next.outcome)
        assertEquals(1, next.round)
        assertEquals(0.0, next.elapsedMs)
        assertEquals(1, next.recentKeys.size)

        val last = done.copy(lives = 0)
        assertEquals(RunnerPhase.OVER, Basket.advance(last, 16.0).phase)
    }

    // MARK: 직렬화 키 호환 (iOS/웹과 동일 문자열)

    @Test fun gameModeSerialNames() {
        val encoded = json.encodeToString(GameState.serializer(),
            Commit.defaultState.copy(bestScores = mapOf(GameMode.TIME_ATTACK to 3), modesPlayed = listOf(GameMode.TRUEFALSE)))
        assertTrue("timeAttack" in encoded, encoded)
        assertTrue("truefalse" in encoded)
    }
}
