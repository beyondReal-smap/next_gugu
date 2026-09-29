package site.smap.gugudan.features.session

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import site.smap.gugudan.core.*
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Sound
import site.smap.gugudan.services.Speech
import site.smap.gugudan.store.GameStore

// 세션 상태 머신 (iOS SessionEngine.swift 이식)

/** 세션 정답 슬롯의 자릿수 — 정답 자릿수만큼 폭을 예약, 초과 입력 시 입력 길이 (테스트 스펙) */
fun sessionAnswerSlotDigits(problem: Problem, mode: GameMode, input: String): Int {
    val expected = if (mode == GameMode.MISSING) problem.b else problem.a * problem.b
    return maxOf(expected.toString().length, input.length)
}

enum class AnswerFeedback { CORRECT, WRONG }

data class SessionDone(
    val result: SessionResult,
    val commit: CommitResult,
    val wrongCount: Int,
)

class SessionEngine(
    val mode: GameMode,
    val table: Int?,
    private val game: GameStore,
    /** 취약 문제 복습 세션 — 오답 풀의 문제를 먼저 낸다 */
    val review: Boolean = false,
    // 세션 결과를 밖으로 넘긴다 (학습 원장 적재).
    // 엔진이 SyncStore/AuthStore 를 직접 알면 의존이 역류하므로 화면 경계에서 잇는다.
    private val onCommit: (SessionResult) -> Unit = {},
) {
    companion object {
        /** 코치 힌트를 띄우는 기준 — 이번 판에서 같은 단을 이만큼 틀리면 그 단 문제에 풀이 힌트를 붙인다 */
        const val COACH_AFTER_MISSES = 2
        /** 복습 세션 문제 수 — 오답 풀 크기를 따르되 너무 짧거나 길지 않게 */
        const val REVIEW_MIN = 5
        const val REVIEW_MAX = 10
    }

    private val def: ModeDef = Modes.def(mode)
    private val handler = Handler(Looper.getMainLooper())

    // 진행 상태 (비표시) — 첫 문제를 정하기 전에 복습 큐를 만든다
    private var queue: MutableList<Problem> =
        if (review) Problems.reviewQueue(game.state.wrongPool, REVIEW_MAX).toMutableList() else mutableListOf()
    private val sessionTotal: Int =
        if (review) maxOf(REVIEW_MIN, minOf(REVIEW_MAX, queue.size)) else def.total

    // 표시 상태
    var problem: Problem by mutableStateOf(
        if (queue.isNotEmpty()) queue.removeAt(0) else Problems.pick(table, game.state.wrongPool, emptyList()))
        private set
    var statement: Statement? by mutableStateOf(
        if (mode == GameMode.TRUEFALSE) Problems.makeStatement(problem) else null)
        private set
    var input: String by mutableStateOf("")
        private set
    var combo: Int by mutableStateOf(0); private set
    var score: Int by mutableStateOf(0); private set
    var lives: Int by mutableStateOf(def.lives ?: 0); private set
    var feedback: AnswerFeedback? by mutableStateOf(null); private set
    var idx: Int by mutableStateOf(0); private set
    var done: SessionDone? by mutableStateOf(null); private set
    /** 오답일 때 아이가 낸 수 — "35가 아니라 36이에요" 설명에 쓴다 */
    var lastGiven: Int? by mutableStateOf(null); private set
    /** 새 문제가 나올 때마다 1씩 오른다 (문제 낭독 트리거) */
    var problemSerial: Int by mutableStateOf(0); private set
    /** 확인 시트가 떠 있는 동안 시간을 멈춘 시각 */
    var pausedAt: Long? by mutableStateOf(null); private set
    /** 이번 판에서 풀어 낸 문제 수 — 0이면 그만둘 때 확인 없이 닫는다 */
    var answeredCount: Int by mutableStateOf(0); private set

    private var wrongPool = game.state.wrongPool
    private var lock = false
    private val answers = mutableListOf<AnswerRecord>()
    private val wrongList = mutableListOf<Problem>()
    private var recent = listOf<String>()
    private var maxCombo = 0
    private var qStart = 0L
    private var sessionStart = 0L
    /** 대기 중인 문제 전환 콜백을 무효화하는 세대 번호 (재시작·이탈) */
    private var sessionId = 0
    /** 60초 챌린지 만료 예약을 무효화하는 세대 번호 (일시정지·재시작·이탈) */
    private var timerToken = 0
    /** 마지막 답을 내서 판의 결과가 정해졌다 — 전환을 기다리는 사이에 그만둬도 다 푼 판으로 기록한다 */
    private var outcomeDecided = false
    /** 그만두기로 이미 기록했다 — 두 번 불려도 한 번만 기록한다 */
    private var closed = false
    private val missesByTable = mutableMapOf<Int, Int>()

    private fun now() = SystemClock.elapsedRealtime()

    // 뷰 편의
    val modeName get() = if (review) "취약 문제 복습" else def.name
    val modeKind get() = def.kind
    val total get() = sessionTotal
    val maxLives get() = def.lives ?: 3
    val timeLimitMs get() = def.timeLimitMs

    /** 지금 문제에 붙일 코치 힌트 — 학습 모드에서 같은 단을 여러 번 틀렸을 때만 */
    val coachHint: CoachHint?
        get() {
            if (mode != GameMode.PRACTICE) return null
            if ((missesByTable[problem.a] ?: 0) < COACH_AFTER_MISSES) return null
            return Hints.coach(problem.a, problem.b)
        }

    fun start() {
        sessionStart = now()
        qStart = now()
        problemSerial += 1
        scheduleExpiry(def.timeLimitMs?.toLong() ?: 0L)
    }

    /** 60초 챌린지 만료 예약 — 일시정지 후에는 남은 시간만큼 다시 건다 */
    private fun scheduleExpiry(afterMs: Long) {
        if (def.kind != ModeKind.TIMED || def.timeLimitMs == null) return
        timerToken += 1
        val token = timerToken
        handler.postDelayed({
            if (token == timerToken && done == null && pausedAt == null) finish()
        }, maxOf(0L, afterMs))
    }

    fun teardown() { sessionId += 1; timerToken += 1; handler.removeCallbacksAndMessages(null) }

    private fun expected(p: Problem): Int = if (mode == GameMode.MISSING) p.b else p.a * p.b

    private val acceptsInput get() = !lock && done == null && pausedAt == null

    // MARK: 입력

    fun handleInput(n: Int) {
        if (!acceptsInput || input.length >= 3) return
        Sound.tap()
        input += n.toString()
        val ans = if (mode == GameMode.MISSING) problem.b else problem.a * problem.b
        if (input.length >= ans.toString().length) {
            lock = true
            val captured = input
            handler.postDelayed({ submit(captured) }, 90)
        }
    }

    fun handleDelete() {
        if (!acceptsInput) return
        Sound.tap()
        if (input.isNotEmpty()) input = input.dropLast(1)
    }

    fun handleManualSubmit() {
        if (!acceptsInput || input.isEmpty()) return
        Sound.tap()
        lock = true
        submit(input)
    }

    fun handleOX(choice: Boolean) {
        val st = statement ?: return
        if (!acceptsInput) return
        Sound.tap()
        lock = true
        resolve(choice == st.isTrue, GivenAnswer.Boolean(choice))
    }

    // MARK: 일시정지 / 중도 이탈

    /** 그만둘지 묻는 동안 시간을 멈춘다 — 제한 시간·응답 시간에서 확인 시간을 뺀다 */
    fun pause() {
        if (done != null || pausedAt != null) return
        pausedAt = now()
        timerToken += 1   // 만료 예약 무효화
    }

    fun resume() {
        val at = pausedAt ?: return
        val paused = now() - at
        sessionStart += paused
        qStart += paused
        pausedAt = null
        def.timeLimitMs?.let { limit ->
            if (def.kind == ModeKind.TIMED) scheduleExpiry(limit - (now() - sessionStart))
        }
    }

    /** 표시용 경과 시간 — 일시정지 중에는 멈춘 시각에서 고정된다 */
    fun elapsedMs(at: Long = now()): Long = maxOf(0L, minOf(at, pausedAt ?: at) - sessionStart)

    /** 중도 이탈 — 푼 문제만 부분 커밋한다(XP·오답 풀·오늘 정답). 별·신기록·추이 같은
     *  한 판 완료 통계는 건드리지 않는다. 푼 문제가 없으면 기록할 것이 없다. */
    fun abandon() {
        if (done != null || closed) return
        closed = true
        sessionId += 1    // 대기 중인 문제 전환 무효화
        timerToken += 1   // 만료 예약 무효화
        lock = true
        Speech.stop()
        if (answers.isEmpty()) return
        // 시간은 확인 창을 띄운 시각까지만 잰다. 마지막 답까지 냈으면(전환만 남았으면) 완료로 기록한다
        val result = SessionResult(
            mode, table, answers.toList(), maxCombo, elapsedMs().toInt(), partial = !outcomeDecided,
        )
        game.commitSession(result)
        onCommit(result)
    }

    // MARK: 코어

    private fun submit(value: String) {
        val parsed = value.toIntOrNull() ?: run { lock = false; return }
        resolve(parsed == expected(problem), GivenAnswer.Number(parsed))
    }

    /** given 은 아이가 실제로 제출한 답 — 학습 원장에 그대로 남는다.
     *  여기서 빠뜨리면 원장의 submittedAnswer 가 전부 꾸며진 값이 된다. */
    private fun resolve(correct: Boolean, given: GivenAnswer) {
        val ms = (now() - qStart).toInt()
        answers.add(AnswerRecord(problem.a, problem.b, correct, ms, given))
        answeredCount = answers.size
        lastGiven = (given as? GivenAnswer.Number)?.value

        if (correct) {
            combo += 1
            if (combo > maxCombo) maxCombo = combo
            score += 1
            feedback = AnswerFeedback.CORRECT
            Sound.correct()
            if (combo >= 3) Sound.combo(combo)
            Haptics.success()
        } else {
            combo = 0
            wrongList.add(problem)
            missesByTable[problem.a] = (missesByTable[problem.a] ?: 0) + 1
            feedback = AnswerFeedback.WRONG
            Sound.wrong()
            Haptics.error()
            if (def.kind == ModeKind.LIVES) lives -= 1
        }

        val last = def.kind == ModeKind.FIXED && idx + 1 >= sessionTotal
        val outOfLives = def.kind == ModeKind.LIVES && lives <= 0
        if (last || outOfLives) outcomeDecided = true
        val sid = sessionId
        handler.postDelayed({
            if (done != null || sid != sessionId) return@postDelayed
            if (last || outOfLives) finish()
            else { idx += 1; goNext() }
        }, if (correct) 380 else 1050)
    }

    private fun goNext() {
        recent = (recent + Problems.key(problem.a, problem.b)).takeLast(4)
        val p = if (queue.isEmpty()) Problems.pick(table, wrongPool, recent) else queue.removeAt(0)
        problem = p
        statement = if (mode == GameMode.TRUEFALSE) Problems.makeStatement(p) else null
        input = ""
        feedback = null
        lastGiven = null
        // 확인 시트가 떠 있는 동안 넘어온 문제는 재개 시각부터 잰다 (resume 이 멈춘 시간만큼 민다)
        qStart = pausedAt ?: now()
        lock = false
        problemSerial += 1
    }

    private fun finish() {
        if (done != null) return
        timerToken += 1
        // 확인 창이 떠 있는 동안 끝나면 멈춘 시각까지만 잰다
        val result = SessionResult(mode, table, answers.toList(), maxCombo, elapsedMs().toInt())
        val commit = game.commitSession(result)
        onCommit(result)
        Sound.complete()
        if (commit.leveledUp) handler.postDelayed({ Sound.levelUp() }, 600)
        done = SessionDone(result, commit, wrongList.size)
    }

    // MARK: 재시작

    fun restart(retryWrong: Boolean) {
        val retained = if (retryWrong) wrongList.toList() else emptyList()
        answers.clear()
        answeredCount = 0
        wrongList.clear()
        recent = emptyList()
        maxCombo = 0
        combo = 0
        score = 0
        lives = def.lives ?: 0
        idx = 0
        lock = false
        pausedAt = null
        lastGiven = null
        missesByTable.clear()
        outcomeDecided = false
        closed = false
        sessionId += 1
        wrongPool = game.state.wrongPool
        // 복습 세션의 "한 판 더"는 갱신된 오답 풀로 새 복습 큐를 만든다
        val next = if (retained.isEmpty() && review) Problems.reviewQueue(wrongPool, REVIEW_MAX) else retained
        val first = next.firstOrNull() ?: Problems.pick(table, wrongPool, emptyList())
        queue = next.drop(1).toMutableList()
        problem = first
        statement = if (mode == GameMode.TRUEFALSE) Problems.makeStatement(first) else null
        input = ""
        feedback = null
        done = null
        start()
    }
}
