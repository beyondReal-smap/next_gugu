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
    // 세션 결과를 밖으로 넘긴다 (학습 원장 적재).
    // 엔진이 SyncStore/AuthStore 를 직접 알면 의존이 역류하므로 화면 경계에서 잇는다.
    private val onCommit: (SessionResult) -> Unit = {},
) {
    private val def: ModeDef = Modes.def(mode)
    private val handler = Handler(Looper.getMainLooper())

    // 표시 상태
    var problem: Problem by mutableStateOf(
        Problems.pick(table, game.state.wrongPool, emptyList()))
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
    var sessionStartClock: Long = 0L; private set

    // 진행 상태 (비표시)
    private var wrongPool = game.state.wrongPool
    private var lock = false
    private val answers = mutableListOf<AnswerRecord>()
    private val wrongList = mutableListOf<Problem>()
    private var queue = mutableListOf<Problem>()
    private var recent = listOf<String>()
    private var maxCombo = 0
    private var qStart = 0L
    private var sessionId = 0

    private fun now() = SystemClock.elapsedRealtime()

    // 뷰 편의
    val modeName get() = def.name
    val modeKind get() = def.kind
    val total get() = def.total
    val maxLives get() = def.lives ?: 3
    val timeLimitMs get() = def.timeLimitMs

    fun start() {
        sessionStartClock = now()
        qStart = now()
        def.timeLimitMs?.let { limit ->
            if (def.kind == ModeKind.TIMED) {
                val sid = sessionId
                handler.postDelayed({
                    if (sid == sessionId && done == null) finish()
                }, limit.toLong())
            }
        }
    }

    fun teardown() { sessionId += 1; handler.removeCallbacksAndMessages(null) }

    private fun expected(p: Problem): Int = if (mode == GameMode.MISSING) p.b else p.a * p.b

    // MARK: 입력

    fun handleInput(n: Int) {
        if (lock || done != null || input.length >= 3) return
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
        if (lock || done != null) return
        Sound.tap()
        if (input.isNotEmpty()) input = input.dropLast(1)
    }

    fun handleManualSubmit() {
        if (lock || done != null || input.isEmpty()) return
        Sound.tap()
        lock = true
        submit(input)
    }

    fun handleOX(choice: Boolean) {
        val st = statement ?: return
        if (lock || done != null) return
        Sound.tap()
        lock = true
        resolve(choice == st.isTrue, GivenAnswer.Boolean(choice))
    }

    fun expire() = finish()

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
            feedback = AnswerFeedback.WRONG
            Sound.wrong()
            Haptics.error()
            if (def.kind == ModeKind.LIVES) lives -= 1
        }

        val last = def.kind == ModeKind.FIXED && idx + 1 >= def.total
        val outOfLives = def.kind == ModeKind.LIVES && lives <= 0
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
        qStart = now()
        lock = false
    }

    private fun finish() {
        if (done != null) return
        val result = SessionResult(mode, table, answers.toList(), maxCombo, (now() - sessionStartClock).toInt())
        val commit = game.commitSession(result)
        onCommit(result)
        Sound.complete()
        if (commit.leveledUp) handler.postDelayed({ Sound.levelUp() }, 600)
        done = SessionDone(result, commit, wrongList.size)
    }

    // MARK: 재시작

    fun restart(retryWrong: Boolean) {
        val retained = if (retryWrong) wrongList.toList() else emptyList()
        queue = retained.drop(1).toMutableList()
        answers.clear()
        wrongList.clear()
        recent = emptyList()
        maxCombo = 0
        combo = 0
        score = 0
        lives = def.lives ?: 0
        idx = 0
        lock = false
        sessionId += 1
        wrongPool = game.state.wrongPool
        sessionStartClock = now()
        qStart = now()
        val first = retained.firstOrNull() ?: Problems.pick(table, wrongPool, emptyList())
        problem = first
        statement = if (mode == GameMode.TRUEFALSE) Problems.makeStatement(first) else null
        input = ""
        feedback = null
        done = null
        start()
    }
}
