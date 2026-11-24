package site.smap.gugudan

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Before
import org.junit.Test
import site.smap.gugudan.core.Achievements
import site.smap.gugudan.core.AnswerRecord
import site.smap.gugudan.core.AppLanguage
import site.smap.gugudan.core.Commit
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.GivenAnswer
import site.smap.gugudan.core.Modes
import site.smap.gugudan.core.PremiumConfig
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.SeededRandom
import site.smap.gugudan.core.SessionResult
import site.smap.gugudan.core.games.MonsterSession
import site.smap.gugudan.core.games.ProblemOrder
import site.smap.gugudan.core.games.RoundClock
import site.smap.gugudan.core.games.StairsSession
import site.smap.gugudan.core.learning.LearningContract
import site.smap.gugudan.core.learning.LearningMode
import site.smap.gugudan.core.learning.LearningRecord
import site.smap.gugudan.features.games.monster.MonsterEngine
import site.smap.gugudan.features.games.shared.GameInsets
import site.smap.gugudan.features.games.shared.GameViewport
import site.smap.gugudan.features.games.shared.RoundLearningLog
import site.smap.gugudan.features.games.stairs.StairsCamera
import site.smap.gugudan.features.games.stairs.StairsEngine
import site.smap.gugudan.features.paywall.MinigameTrial
import site.smap.gugudan.services.Sound
import site.smap.gugudan.store.MinigameStart
import site.smap.gugudan.store.PlayRewardsStorage
import site.smap.gugudan.store.PlayRewardsStore
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 구구 계단·구구 몬스터 판 → 학습 기록(XP·오늘 목표·틀린 문제·서버 원장) 연결 — iOS GameLearningTests.swift 와 같은 의미.
 * 판이 끝날 때(결과·체험 끝) 완료로 한 번, 판 중간에 나가면 부분으로 한 번, 답이 없으면 기록 없음.
 * 게임 판은 단 마스터리 별에 반영하지 않는다(GameMode.isGame).
 */
class GameLearningTests {

    /** 에뮬레이터(1280×2856 px) 크기·안전 영역 */
    private val viewport = GameViewport(Size(1280f, 2856f), GameInsets(top = 156f, bottom = 63f))
    private val frame = 1.0 / 60
    private val strings = TestStrings(AppLanguage.KO)

    @Before fun quiet() {
        Sound.setEnabled(false)   // JVM 테스트에는 AudioTrack 이 없다
    }

    // MARK: 도우미

    private class MemoryRewardsStorage : PlayRewardsStorage {
        val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            values[key] = value
        }
        override fun quarantine(key: String, raw: String, cause: Throwable) = error("격리될 저장본이 없어야 함: $key")
    }

    private fun makeStore() = PlayRewardsStore(MemoryRewardsStorage(), key = "gugu.test.play", adventureKey = "gugu.test.adventure")

    private fun stairs(learning: LearningSpy, trial: Int? = null, start: MinigameStart = MinigameStart.Table(2)): StairsEngine {
        val engine = StairsEngine(
            start, ProblemOrder.SEQUENTIAL, makeStore(), strings, learning::commit, topStep = 9, random = SeededRandom.mulberry32(7u),
        )
        engine.updateViewport(viewport)
        engine.start(trial)
        return engine
    }

    private fun monster(
        learning: LearningSpy,
        start: MinigameStart = MinigameStart.Default,
        trial: Int? = null,
        order: ProblemOrder = ProblemOrder.RANDOM,
    ): MonsterEngine {
        val engine = MonsterEngine(start, order, makeStore(), strings, learning::commit, random = SeededRandom.mulberry32(7u))
        engine.updateViewport(viewport)
        engine.start(trial)
        return engine
    }

    /** 계단 칸 가운데를 누르고 한 프레임 — 오답이면 정답 공개(0.6초)가 끝날 때까지 흘린다 */
    private fun jump(engine: StairsEngine, correct: Boolean) {
        val q = engine.session.current
        val lane = if (correct) q.correctLane else (q.correctLane + 1) % StairsSession.LANES
        engine.enqueueTap(Offset(StairsCamera(viewport.size, 0.0).laneCenterX(lane), 1500f))
        engine.advance(frame)
        if (!correct) run(RoundClock.REVEAL_SEC + 0.02) { engine.advance(it) }
    }

    /** 몬스터 카드 두 장 — 정답 짝 또는 곱이 목표가 아닌 짝. 오답이면 공개가 끝날 때까지 흘린다 */
    private fun pick(engine: MonsterEngine, correct: Boolean) {
        val q = engine.session.current
        val second = if (correct) q.pairB else (0 until MonsterSession.CARD_COUNT).firstOrNull {
            it != q.pairA && q.cards[q.pairA] * q.cards[it] != q.target.a * q.target.b
        } ?: fail("오답 카드가 없음 ${q.cards}")
        engine.enqueueTap(MonsterEngine.Cards.center(q.pairA, viewport))
        engine.enqueueTap(MonsterEngine.Cards.center(second, viewport))
        engine.advance(frame)
        if (!correct) run(RoundClock.REVEAL_SEC + 0.02) { engine.advance(it) }
    }

    private fun run(sec: Double, advance: (Double) -> Unit) {
        repeat(ceil(sec / frame).toInt()) { advance(frame) }
    }

    /** 결과(또는 체험 종료)가 뜰 때까지 — 먹구름·60초 시계 포함, 상한으로 무한 반복 방지 */
    private fun untilResult(hasResult: () -> Boolean, advance: (Double) -> Unit) {
        var i = 0
        while (!hasResult() && i < 10_000) {
            advance(0.05)
            i += 1
        }
        assertTrue(hasResult(), "결과가 뜨지 않음")
    }

    // MARK: 기록 한 번 규칙

    @Test fun logClosesOnceAndSkipsEmptyRounds() {
        val learning = LearningSpy()
        val log = RoundLearningLog(GameMode.STAIRS, table = null)
        assertNull(log.close(emptyList(), maxCombo = 0, durationMs = 0, partial = true, commitSession = learning::commit))
        assertTrue(log.closed)
        assertTrue(learning.results.isEmpty(), "답이 없으면 기록하지 않는다")

        log.reset()
        val answers = listOf(AnswerRecord(3, 4, true, 900, GivenAnswer.Number(12)))
        assertNotNull(log.close(answers, maxCombo = 1, durationMs = 900, partial = false, commitSession = learning::commit))
        assertNotNull(log.close(answers, maxCombo = 1, durationMs = 900, partial = true, commitSession = learning::commit))
        assertEquals(1, learning.results.size, "한 판에 한 번만")
        assertFalse(learning.results[0].partial)
    }

    /** 게임 판 기록은 게임 모드로만 — 학습 모드 세션을 이 길로 넘기면 마스터리 규칙이 섞이므로 만들 때 막는다 */
    @Test fun logAcceptsOnlyGameModes() {
        assertFailsWith<IllegalArgumentException> { RoundLearningLog(GameMode.PRACTICE, table = 7) }
        assertFailsWith<IllegalArgumentException> { RoundLearningLog(GameMode.ADVENTURE, table = null) }
        assertEquals(setOf(GameMode.STAIRS, GameMode.MONSTER), GameMode.entries.filter { it.isGame }.toSet())
    }

    // MARK: 구구 계단

    /** 오답이 섞인 판이 먹구름에 잡혀 끝나면 결과를 띄우는 순간 완료로 한 번 기록 — XP·오늘 정답·틀린 문제가 오른다 */
    @Test fun stairsRoundCommitsAtResult() {
        val learning = LearningSpy()
        val engine = stairs(learning)
        jump(engine, correct = true)
        jump(engine, correct = false)
        jump(engine, correct = true)
        val wrong = assertNotNull(engine.session.attempts.firstOrNull { !it.correct })
        assertTrue(learning.results.isEmpty(), "판 도중에는 기록하지 않는다")

        untilResult({ engine.result != null }) { engine.advance(it) }
        assertTrue(engine.session.caught)

        assertEquals(1, learning.results.size)
        val result = learning.results[0]
        assertEquals(GameMode.STAIRS, result.mode)
        assertNull(result.table, "계단은 판 중에 단이 바뀌어 단을 정하지 않는다")
        assertFalse(result.partial)
        assertEquals(engine.answerRecords, result.answers)
        assertEquals(listOf(true, false, true), result.answers.map { it.correct })
        assertEquals(engine.session.bestStreak, result.maxCombo)
        assertEquals(engine.roundDurationMs, result.durationMs)

        val state = learning.state
        assertTrue(state.totalXp > 0)
        assertEquals(2, state.dailyCorrect)
        assertEquals(1, state.totalWrong)
        // 틀린 칸은 같은 식으로 다시 나와 맞혔으므로 +2 → -1
        assertEquals(1, state.wrongPool[Problems.key(wrong.problem.a, wrong.problem.b)])
        assertTrue(GameMode.STAIRS in state.modesPlayed)
        assertTrue(state.tableMastery.isEmpty(), "단을 정하지 않은 판은 단 별에 반영하지 않는다")
        assertEquals(learning.commits[0].xpEarned, engine.result?.xpEarned)
        assertEquals(learning.commits[0].xpEarned, engine.learning.commit?.xpEarned)

        // 결과 창이 떠 있는 동안 프레임이 더 돌거나 [나가기]로 나가도 다시 기록하지 않는다
        repeat(60) { engine.advance(frame) }
        engine.abandon()
        assertEquals(1, learning.results.size)
    }

    /** 무료 체험 N칸(MinigameTrial.questions) — 체험 종료 패널을 띄울 때 완료로 기록 */
    @Test fun stairsTrialEndCommits() {
        val learning = LearningSpy()
        val n = MinigameTrial.questions
        val engine = stairs(learning, trial = MinigameTrial.limit(isPremium = false))
        repeat(n) { jump(engine, correct = true) }
        assertTrue(engine.trialFinished)
        untilResult({ engine.result != null }) { engine.advance(it) }

        assertEquals(1, learning.results.size)
        assertFalse(learning.results[0].partial)
        assertEquals(n, learning.results[0].answers.size)
        assertEquals(n, learning.state.dailyCorrect)
        assertEquals(learning.commits[0].xpEarned, engine.result?.xpEarned)
    }

    /** 판 중간에 쉬는 창에서 나가면 푼 답만 부분 기록(XP·오늘 정답·틀린 문제는 오르고, 한 판 완료 통계는 그대로) */
    @Test fun stairsQuitMidRoundCommitsPartial() {
        val learning = LearningSpy()
        val engine = stairs(learning)
        jump(engine, correct = true)
        jump(engine, correct = false)
        engine.openPause()
        engine.abandon()
        engine.abandon()   // [메뉴로 나가기]·화면 정리(onDispose)가 겹쳐도 한 번만

        assertEquals(1, learning.results.size)
        val result = learning.results[0]
        assertTrue(result.partial)
        assertEquals(2, result.answers.size)
        assertEquals(1, learning.state.dailyCorrect)
        assertTrue(learning.state.totalXp > 0, "부분 기록도 XP 는 오른다")
        assertEquals(1, learning.state.wrongPool.size)
        assertTrue(learning.state.recentAccuracy.isEmpty(), "부분 기록은 정확도 추이에 넣지 않는다")
        assertTrue(learning.state.modesPlayed.isEmpty())
    }

    /** 한 칸도 안 오르고 나가면(시작 전 패널·첫 탭 전) 기록할 것이 없다 */
    @Test fun stairsQuitWithoutAnswersCommitsNothing() {
        val learning = LearningSpy()
        val ready = StairsEngine(
            MinigameStart.Default, ProblemOrder.SEQUENTIAL, makeStore(), strings, learning::commit, topStep = 9,
            random = SeededRandom.mulberry32(1u),
        )
        ready.abandon()   // 시작 전 패널의 [나가기]
        val started = stairs(learning)
        repeat(30) { started.advance(frame) }
        started.openPause()
        started.abandon()
        assertTrue(learning.results.isEmpty())
    }

    /** [다시 하기]로 새 판이 되면 그 판도 따로 한 번 기록 */
    @Test fun stairsRestartCommitsEachRound() {
        val learning = LearningSpy()
        val engine = stairs(learning)
        jump(engine, correct = true)
        untilResult({ engine.result != null }) { engine.advance(it) }
        repeat(40) { engine.advance(frame) }
        engine.restart(trial = null)
        assertNull(engine.learning.commit)

        jump(engine, correct = true)
        jump(engine, correct = true)
        engine.openPause()
        engine.abandon()
        assertEquals(listOf(false, true), learning.results.map { it.partial })
        assertEquals(listOf(1, 2), learning.results.map { it.answers.size })
        assertEquals(3, learning.state.dailyCorrect)
    }

    // MARK: 구구 몬스터

    /** 단 지정(7단) 판이 60초로 끝나면 그 단으로 완료 기록 — 틀린 문제·오늘 정답에 반영, 7단 마스터리 별은 주지 않음 */
    @Test fun monsterTableRoundCommitsWithTable() {
        val learning = LearningSpy()
        val engine = monster(learning, start = MinigameStart.Table(7), order = ProblemOrder.SEQUENTIAL)
        pick(engine, correct = true)
        pick(engine, correct = false)
        pick(engine, correct = true)
        val wrong = assertNotNull(engine.session.attempts.firstOrNull { !it.correct })
        untilResult({ engine.result != null }) { engine.advance(it) }

        assertEquals(1, learning.results.size)
        val result = learning.results[0]
        assertEquals(GameMode.MONSTER, result.mode)
        assertEquals(7, result.table)
        assertFalse(result.partial)
        assertEquals(engine.answerRecords, result.answers)
        assertTrue(result.answers.all { it.a == 7 })
        assertEquals((RoundClock.DURATION_SEC * 1000).roundToInt(), result.durationMs)

        val state = learning.state
        assertEquals(2, state.dailyCorrect)
        assertTrue(state.totalXp > 0)
        // 틀린 몬스터는 같은 목표로 다시 나와 맞혔으므로 +2 → -1
        assertEquals(1, state.wrongPool[Problems.key(7, wrong.problem.b)])
        assertNull(state.tableMastery[7], "게임 판은 단 마스터리 별에 반영하지 않는다(체험 판만으로 별 3개 방지)")
        assertTrue(GameMode.MONSTER in state.modesPlayed)
        assertEquals(learning.commits[0].xpEarned, engine.result?.xpEarned)
    }

    /** 섞어서 판은 단 null, 판 중간에 나가면 부분 기록 */
    @Test fun monsterMixedQuitCommitsPartialWithoutTable() {
        val learning = LearningSpy()
        val engine = monster(learning, start = MinigameStart.Table(null))
        pick(engine, correct = true)
        pick(engine, correct = true)
        engine.openPause()
        engine.abandon()

        assertEquals(1, learning.results.size)
        assertNull(learning.results[0].table)
        assertTrue(learning.results[0].partial)
        assertEquals(2, learning.state.dailyCorrect)
        assertTrue(learning.state.tableMastery.isEmpty())
    }

    /** 무료 체험 N마리(MinigameTrial.questions) — 체험 종료 때 완료로 기록, 이후 [나가기]는 다시 기록하지 않음 */
    @Test fun monsterTrialEndCommitsOnce() {
        val learning = LearningSpy()
        val n = MinigameTrial.questions
        val engine = monster(learning, trial = MinigameTrial.limit(isPremium = false))
        repeat(n) { pick(engine, correct = true) }
        untilResult({ engine.result != null }) { engine.advance(it) }
        engine.abandon()

        assertEquals(1, learning.results.size)
        assertFalse(learning.results[0].partial)
        assertEquals(n, learning.results[0].answers.size)
    }

    /** 시작만 하고 카드를 안 고른 채 나가면 기록 없음 */
    @Test fun monsterQuitWithoutAnswersCommitsNothing() {
        val learning = LearningSpy()
        val engine = monster(learning)
        engine.openPause()
        engine.abandon()
        assertTrue(learning.results.isEmpty())
    }

    // MARK: 마스터리

    /** Commit 단에서도 — 단을 넘긴 게임 판은 그 단 별·플레이 수를 건드리지 않고, 같은 답의 학습 모드 판은 별을 준다 */
    @Test fun gameRoundsNeverTouchTableMastery() {
        val answers = (1..9).map { AnswerRecord(7, it, true, 1200, GivenAnswer.Number(7 * it)) }
        for (mode in listOf(GameMode.STAIRS, GameMode.MONSTER)) {
            val (state, _) = Commit.applySession(Commit.defaultState, SessionResult(mode, 7, answers, 9, 30_000))
            assertNull(state.tableMastery[7], "$mode")
            assertEquals(9, state.dailyCorrect, "$mode")
        }
        val (practice, _) = Commit.applySession(Commit.defaultState, SessionResult(GameMode.PRACTICE, 7, answers, 9, 30_000))
        assertEquals(1, practice.tableMastery[7]?.plays)
        assertTrue((practice.tableMastery[7]?.stars ?: 0) > 0)
    }

    // MARK: 서버 원장

    /**
     * 게임 판은 서버 계약에 없는 모드라 practice 로 보낸다. factId 계약이 2~19단 × 1~19 라 계단의 13단 문항도 그대로 간다.
     * (iOS 같은 이름 테스트는 13×1 이 빠진다고 기대하지만 iOS·Android 계약(LearningContract)이 같아 실제로는 남는다)
     */
    @Test fun gameEventsGoToServerAsPractice() {
        assertEquals(LearningMode.PRACTICE, LearningRecord.apiMode(GameMode.STAIRS))
        assertEquals(LearningMode.PRACTICE, LearningRecord.apiMode(GameMode.MONSTER))

        val ids = LearningRecord.Ids(
            learnerId = "0191d0f3-1b2c-7abc-8def-0123456789ab",
            deviceId = "0191d0f3-1b2c-7abc-8def-0123456789ac",
            sessionId = "0191d0f3-1b2c-7abc-8def-0123456789ad",
        )
        val stairs = SessionResult(
            GameMode.STAIRS, null,
            listOf(
                AnswerRecord(6, 9, true, 1200, GivenAnswer.Number(54)),
                AnswerRecord(13, 1, true, 1500, GivenAnswer.Number(13)),
                AnswerRecord(7, 2, false, 2100, GivenAnswer.Number(21)),
            ),
            maxCombo = 2, durationMs = 9000,
        )
        val events = LearningRecord.events(stairs, ids)
        assertEquals(List(3) { LearningMode.PRACTICE }, events.map { it.mode })
        assertEquals(
            listOf(LearningContract.factId(6, 9), LearningContract.factId(13, 1), LearningContract.factId(7, 2)),
            events.map { it.factId },
        )
        assertEquals(
            listOf<GivenAnswer>(GivenAnswer.Number(54), GivenAnswer.Number(13), GivenAnswer.Number(21)),
            events.map { it.submittedAnswer },
        )
        assertEquals(listOf<Int?>(null, null, null), events.map { it.tableNo }, "단이 섞인 판은 tableNo 없음")

        val monster = SessionResult(
            GameMode.MONSTER, 7,
            listOf(
                AnswerRecord(7, 6, true, 1800, GivenAnswer.Number(42)),
                AnswerRecord(7, 8, false, 2500, GivenAnswer.Number(54)),
            ),
            maxCombo = 1, durationMs = 60_000,
        )
        val monsterEvents = LearningRecord.events(monster, ids)
        assertEquals(listOf(LearningMode.PRACTICE, LearningMode.PRACTICE), monsterEvents.map { it.mode })
        assertEquals(listOf<Int?>(7, 7), monsterEvents.map { it.tableNo })
    }

    // MARK: 모드 목록

    /** 게임 모드는 학습 탭 모드 목록·업적 '모드 탐험가' 기준에 끼어들지 않는다(어드벤처와 같은 방식) */
    @Test fun gameModesStayOutOfLearningModeList() {
        val ids = Modes.list.map { it.id }
        assertEquals(
            listOf(GameMode.PRACTICE, GameMode.TIME_ATTACK, GameMode.CHALLENGE, GameMode.SURVIVAL, GameMode.MISSING, GameMode.TRUEFALSE),
            ids,
        )
        assertFalse(Modes.options(null).any { it.id.isGame })
        assertFalse(Modes.options(7).any { it.id.isGame })
        for (mode in listOf(GameMode.STAIRS, GameMode.MONSTER)) {
            assertEquals(mode, Modes.def(mode).id)
            assertFalse(Modes.def(mode).scored, "최고 기록은 게임 보상 저장소가 따로 맡는다")
            assertFalse(PremiumConfig.isPremiumMode(mode), "잠금은 N문제 체험이 맡는다 — 모드 잠금 아님")
        }
        assertEquals(setOf(GameMode.ADVENTURE, GameMode.STAIRS, GameMode.MONSTER), GameMode.entries.toSet() - ids.toSet())

        val s = Commit.defaultState.copy(
            modesPlayed = listOf(
                GameMode.PRACTICE, GameMode.TIME_ATTACK, GameMode.CHALLENGE, GameMode.SURVIVAL, GameMode.MISSING,
                GameMode.STAIRS, GameMode.MONSTER, GameMode.ADVENTURE,
            ),
        )
        assertFalse("mode_explorer" in Achievements.newlyUnlocked(s), "OX 퀴즈를 안 했으면 게임을 해도 해금 안 됨")
    }
}
