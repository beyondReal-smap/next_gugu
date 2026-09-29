package site.smap.gugudan.features.adventure.battle

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import site.smap.gugudan.core.AnswerRecord
import site.smap.gugudan.core.CommitResult
import site.smap.gugudan.core.GivenAnswer
import site.smap.gugudan.core.Problem
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.SessionResult
import site.smap.gugudan.core.adventure.AdvProgress
import site.smap.gugudan.core.adventure.Battle
import site.smap.gugudan.core.adventure.BattleStyle
import site.smap.gugudan.core.adventure.NpcDef
import site.smap.gugudan.core.adventure.NpcKind
import site.smap.gugudan.features.session.AnswerFeedback
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Sound
import site.smap.gugudan.store.AdventureStore
import site.smap.gugudan.store.GameStore

// 배틀 상태 머신 (iOS BattleEngine.swift 이식) — HP/SPEED/COUNTER + 보스 분노

enum class BattlePhase { INTRO, PLAY, END }

data class Floater(val id: Int, val text: String)

data class BattleEnd(val won: Boolean, val commit: CommitResult, val advUnlocked: List<String>)

class BattleEngine(
    val npc: NpcDef,
    private val game: GameStore,
    private val adventure: AdventureStore,
    // 전투 결과를 밖으로 넘긴다 (학습 원장 적재).
    // 엔진이 SyncStore/AuthStore 를 직접 알면 의존이 역류하므로 화면 경계에서 잇는다.
    private val onCommit: (SessionResult) -> Unit = {},
) {
    val isBoss = npc.kind == NpcKind.BOSS
    val style: BattleStyle = npc.battle
    val counterLimit = Battle.counterLimitMs(npc.table)
    val npcMaxHp = npc.hp

    /** 이미 격파한 상대와의 재대결 — 진입 시점에 확정한다 (이번 전투 기록 반영 전) */
    private val rematch = AdvProgress.isNpcDefeated(adventure.progress, npc.id)

    private val handler = Handler(Looper.getMainLooper())

    var phase by mutableStateOf(BattlePhase.INTRO); private set
    var problem by mutableStateOf(Problems.pick(npc.table, game.state.wrongPool, emptyList())); private set
    var input by mutableStateOf(""); private set
    var combo by mutableStateOf(0); private set
    var qIdx by mutableStateOf(0); private set
    var npcHp by mutableStateOf(npc.hp); private set
    var playerHp by mutableStateOf(Battle.PLAYER_MAX_HP); private set
    var npcScore by mutableStateOf(0); private set
    var playerScore by mutableStateOf(0); private set
    var enraged by mutableStateOf(false); private set
    var timedOut by mutableStateOf(false); private set
    var feedback by mutableStateOf<AnswerFeedback?>(null); private set
    var npcFloat by mutableStateOf<Floater?>(null); private set
    var playerFloat by mutableStateOf<Floater?>(null); private set
    var npcHit by mutableStateOf(0); private set
    var playerHit by mutableStateOf(0); private set
    var end by mutableStateOf<BattleEnd?>(null); private set
    var qStartClock = 0L; private set
    /** 도망 확인 시트가 떠 있는 동안 시간을 멈춘 시각 (반격전 타이머 표시도 여기서 멈춘다) */
    var pausedAt by mutableStateOf<Long?>(null); private set
    /** 오답일 때 아이가 낸 수 — "35가 아니라 36이에요" 설명에 쓴다 */
    var lastGiven by mutableStateOf<Int?>(null); private set
    /** 이번 대결에서 푼 문제 수 — 0이면 도망칠 때 확인 없이 닫는다 */
    var answeredCount by mutableStateOf(0); private set
    /** 반격전 예약을 무효화하는 세대 번호 (일시정지·다음 문제) */
    private var counterToken = 0

    private var lock = false
    private var done = false
    private var decided = false
    private val answers = mutableListOf<AnswerRecord>()
    private var recent = listOf<String>()
    var maxCombo = 0; private set
    private var floatId = 0
    private var wrongPool = game.state.wrongPool
    private var battleStart = 0L

    private fun now() = SystemClock.elapsedRealtime()

    fun start() {
        handler.postDelayed({
            if (done) return@postDelayed
            battleStart = now(); qStartClock = now()
            phase = BattlePhase.PLAY
            startRacePaceIfNeeded()
            scheduleCounterIfNeeded()
            startBotIfNeeded()
        }, 1500)
    }

    /** QA 훅 — 배틀 자동 진행: setprop debug.gugu.battle_bot win|lose */
    private fun startBotIfNeeded() {
        val mode = site.smap.gugudan.services.DevProps.get("debug.gugu.battle_bot")
        if (mode != "win" && mode != "lose") return
        val tick = object : Runnable {
            override fun run() {
                if (done) return
                if (phase == BattlePhase.PLAY && !lock) {
                    lock = true
                    resolve(mode == "win", 900, GivenAnswer.NoAnswer)
                }
                handler.postDelayed(this, 1200)
            }
        }
        handler.postDelayed(tick, 1200)
    }

    fun teardown() {
        done = true
        handler.removeCallbacksAndMessages(null)
    }

    // MARK: 일시정지 / 도망

    /** 도망칠지 묻는 동안 대결을 멈춘다 — 상대의 레이스 진행과 반격 카운트다운이 서고, 응답 시간에서도 빠진다 */
    fun pause() {
        if (phase != BattlePhase.PLAY || done || pausedAt != null) return
        pausedAt = now()
        counterToken += 1   // 반격 예약 무효화
    }

    fun resume() {
        val at = pausedAt ?: return
        val paused = now() - at
        qStartClock += paused
        battleStart += paused
        pausedAt = null
        // 멈추기 전에 남아 있던 반격 시간만큼 다시 건다 (답을 내고 넘어가는 중이면 goNext 가 새로 건다)
        if (!lock) scheduleCounterIfNeeded(counterLimit - (now() - qStartClock))
    }

    /** 표시용 시각 — 일시정지 중에는 멈춘 시각에서 고정된다 */
    fun displayClock(at: Long = now()): Long = minOf(at, pausedAt ?: at)

    /** 도망 — 푼 문제만 부분 커밋한다(XP·오답 풀·오늘 정답). 승패와 격파 기록은 남기지 않는다. */
    fun abandon() {
        if (done) return
        teardown()
        if (answers.isEmpty()) return
        val result = Battle.toSessionResult(
            npc, answers.toList(), maxCombo, (displayClock() - battleStart).toInt(), partial = true, rematch = rematch,
        )
        game.commitSession(result)
        onCommit(result)
    }

    // MARK: 입력

    fun handleInput(n: Int) {
        if (phase != BattlePhase.PLAY || lock || done || pausedAt != null || input.length >= 3) return
        Sound.tap()
        input += n.toString()
        val ans = problem.a * problem.b
        if (input.length >= ans.toString().length) {
            lock = true
            val captured = input
            handler.postDelayed({ submit(captured) }, 90)
        }
    }
    fun handleDelete() {
        if (phase != BattlePhase.PLAY || lock || done || pausedAt != null) return
        Sound.tap()
        if (input.isNotEmpty()) input = input.dropLast(1)
    }
    fun handleManualSubmit() {
        if (phase != BattlePhase.PLAY || lock || done || pausedAt != null || input.isEmpty()) return
        Sound.tap(); lock = true
        submit(input)
    }
    private fun timeout() {
        if (phase != BattlePhase.PLAY || lock || done || pausedAt != null) return
        lock = true
        timedOut = true
        resolve(false, counterLimit, GivenAnswer.NoAnswer)
    }

    // MARK: 코어

    private fun submit(value: String) {
        val parsed = value.toIntOrNull() ?: run { lock = false; return }
        resolve(parsed == problem.a * problem.b, (now() - qStartClock).toInt(),
                GivenAnswer.Number(parsed))
    }

    /** given 은 아이가 실제로 제출한 답 — 시간 초과처럼 제출이 없으면 NoAnswer */
    private fun resolve(correct: Boolean, ms: Int, given: GivenAnswer) {
        answers.add(AnswerRecord(problem.a, problem.b, correct, ms, given))
        answeredCount = answers.size
        lastGiven = (given as? GivenAnswer.Number)?.value
        floatId += 1

        if (correct) {
            combo += 1
            if (combo > maxCombo) maxCombo = combo
            feedback = AnswerFeedback.CORRECT
            Sound.correct()
            if (combo >= 3) Sound.combo(combo)
            Haptics.success()

            if (style == BattleStyle.SPEED) {
                playerScore += 1
                npcHit += 1
            } else {
                val dmg = Battle.damage(ms, combo)
                npcHp = maxOf(0, npcHp - dmg)
                npcFloat = Floater(floatId, "-$dmg")
                npcHit += 1
                if (isBoss && !enraged && npcHp > 0 && npcHp <= npc.hp * Battle.BOSS_ENRAGE_RATIO) {
                    enraged = true
                    handler.postDelayed({
                        if (!done) { Sound.wrong(); Haptics.error() }
                    }, 450)
                }
            }
        } else {
            combo = 0
            feedback = AnswerFeedback.WRONG
            Sound.wrong()
            Haptics.error()
            if (style != BattleStyle.SPEED) {
                val atk = if (isBoss && enraged) Battle.enragedAttack(npc.attack) else npc.attack
                playerHp = maxOf(0, playerHp - atk)
                playerFloat = Floater(floatId, "-$atk")
                playerHit += 1
            }
        }

        val playerWon = if (style == BattleStyle.SPEED) playerScore >= Battle.RACE_TARGET else npcHp <= 0
        val playerLost = if (style == BattleStyle.SPEED) false else playerHp <= 0
        if (playerWon || playerLost) decided = true

        handler.postDelayed({
            if (done) return@postDelayed
            when {
                playerWon -> finish(true)
                playerLost -> finish(false)
                else -> goNext()
            }
        }, if (correct) 550 else 1250)
    }

    private fun goNext() {
        recent = (recent + Problems.key(problem.a, problem.b)).takeLast(4)
        problem = Problems.pick(npc.table, wrongPool, recent)
        input = ""
        feedback = null
        timedOut = false
        lastGiven = null
        qIdx += 1
        // 확인 시트가 떠 있는 동안 넘어온 문제는 재개 시각부터 잰다 (resume 이 멈춘 시간만큼 민다)
        qStartClock = pausedAt ?: now()
        lock = false
        if (pausedAt == null) scheduleCounterIfNeeded()
    }

    private fun finish(won: Boolean) {
        if (done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        val result = Battle.toSessionResult(
            npc, answers.toList(), maxCombo, (displayClock() - battleStart).toInt(), rematch = rematch,
        )
        val commit = game.commitSession(result)
        onCommit(result)
        val advUnlocked = adventure.recordBattle(npc.id, won)
        end = BattleEnd(won, commit, advUnlocked)
        phase = BattlePhase.END
        if (won) {
            if (isBoss) Sound.levelUp() else Sound.complete()
            Haptics.success()
        }
    }

    // MARK: 방식별 타이머

    private fun startRacePaceIfNeeded() {
        if (style != BattleStyle.SPEED) return
        val interval = Battle.racePaceMs(npc.table).toLong()
        val tick = object : Runnable {
            override fun run() {
                if (done || decided) return
                if (pausedAt != null) { handler.postDelayed(this, interval); return }
                npcScore += 1
                if (npcScore >= Battle.RACE_TARGET) {
                    decided = true
                    finish(false)
                } else {
                    handler.postDelayed(this, interval)
                }
            }
        }
        handler.postDelayed(tick, interval)
    }

    private fun scheduleCounterIfNeeded(afterMs: Long = counterLimit.toLong()) {
        if (style != BattleStyle.COUNTER) return
        counterToken += 1
        val token = counterToken
        val idx = qIdx
        handler.postDelayed({
            if (!done && phase == BattlePhase.PLAY && qIdx == idx && !lock && token == counterToken && pausedAt == null) {
                timeout()
            }
        }, maxOf(0L, afterMs))
    }
}
