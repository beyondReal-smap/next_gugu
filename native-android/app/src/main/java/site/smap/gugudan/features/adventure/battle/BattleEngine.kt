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

    // MARK: 입력

    fun handleInput(n: Int) {
        if (phase != BattlePhase.PLAY || lock || done || input.length >= 3) return
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
        if (phase != BattlePhase.PLAY || lock || done) return
        Sound.tap()
        if (input.isNotEmpty()) input = input.dropLast(1)
    }
    fun handleManualSubmit() {
        if (phase != BattlePhase.PLAY || lock || done || input.isEmpty()) return
        Sound.tap(); lock = true
        submit(input)
    }
    private fun timeout() {
        if (phase != BattlePhase.PLAY || lock || done) return
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
        qIdx += 1
        qStartClock = now()
        lock = false
        scheduleCounterIfNeeded()
    }

    private fun finish(won: Boolean) {
        if (done) return
        done = true
        handler.removeCallbacksAndMessages(null)
        val result = Battle.toSessionResult(
            npc, answers.toList(), maxCombo, (now() - battleStart).toInt(), rematch = rematch,
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

    private fun scheduleCounterIfNeeded() {
        if (style != BattleStyle.COUNTER) return
        val idx = qIdx
        handler.postDelayed({
            if (!done && phase == BattlePhase.PLAY && qIdx == idx && !lock) timeout()
        }, counterLimit.toLong())
    }
}
