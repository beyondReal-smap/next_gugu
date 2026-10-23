package site.smap.gugudan.features.pop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import site.smap.gugudan.core.Pop
import site.smap.gugudan.core.PopEvent
import site.smap.gugudan.core.PopState
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.RunnerPhase
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Persistence
import site.smap.gugudan.services.Sound

// 구구 팡팡 진행 상태 관리 (BasketEngine 과 같은 구조)
// 게임 규칙은 core/Pop.kt 순수 함수가 담당하고, 여기서는 프레임 반영·탭 좌표 변환·연출·기록 저장만 맡는다.

/** 화면 연출 하나 — 규칙과 무관하고 그리기 전용이다. born 은 엔진 시계(ms) 기준 */
sealed class PopFx {
    abstract val born: Double
    abstract val lifeMs: Double

    /** 풍선이 터진 자리 (입자·고리). big = 보스 격파 */
    data class Burst(val x: Double, val y: Double, val golden: Boolean, val big: Boolean, override val born: Double) : PopFx() {
        override val lifeMs get() = if (big) 700.0 else 450.0
    }
    /** 얻은 점수 "+20" 또는 보스 식 "4 × 9 = 36" */
    data class Label(val x: Double, val y: Double, val text: String, override val born: Double) : PopFx() {
        override val lifeMs get() = 800.0
    }
    /** 물총 물방울 — 토끼에서 누른 풍선까지 */
    data class Shot(val x: Double, val y: Double, override val born: Double) : PopFx() {
        override val lifeMs get() = 140.0
    }
    /** 오답 풍선 흔들기 (동작 줄이기에서는 붉은 테두리만) */
    data class Shake(val id: Int, override val born: Double) : PopFx() {
        override val lifeMs get() = 360.0
    }
    /** 하트가 깨짐 — 지붕선이 붉게 번쩍인다 */
    data class Hurt(override val born: Double) : PopFx() {
        override val lifeMs get() = 500.0
    }
}

/** initialTable: 처음 골라 둘 단 (null = 섞어서). 단 시트에서 열 때 넘기고, 생략하면 기본값 */
class PopEngine(private val persistence: Persistence, initialTable: Int? = DEFAULT_TABLE) {
    var game: PopState by mutableStateOf(Pop.create(initialTable, RunnerPhase.READY))
        private set
    var bests: Map<String, Int> by mutableStateOf(emptyMap())
        private set

    /** 준비 화면에서 고른 단 (null = 섞어서) */
    var table: Int? by mutableStateOf(initialTable)

    /** 연출용 시계 — 진행 중에만 흐른다(일시정지하면 연출도 멈춘다) */
    var clock: Double by mutableStateOf(0.0)
        private set
    var fx: List<PopFx> by mutableStateOf(emptyList())
        private set

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val TABLES = (Problems.MIN_TABLE..Problems.MAX_TABLE).toList()

        /** 시작 단 기본값 — 홈에서 열면 섞어서 (iOS PopEngine.defaultTable 과 같음) */
        val DEFAULT_TABLE: Int? = null

        /** 프레임 간격 상한 — 기기가 잠깐 멈췄다 돌아와도 풍선이 한꺼번에 떨어지지 않게 */
        private const val MAX_DT = 80.0
    }

    init {
        require(initialTable == null || initialTable in TABLES) { "단 범위 밖: $initialTable" }
        bests = loadBests()
    }

    // MARK: 기록

    private fun loadBests(): Map<String, Int> {
        val raw = persistence.getString(Persistence.POP_BEST_KEY) ?: return emptyMap()
        val decoded = try {
            json.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), raw)
        } catch (e: Exception) {
            // 저장본이 깨졌으면 기록만 비우고 게임은 계속할 수 있게 둔다
            persistence.quarantine(Persistence.POP_BEST_KEY, raw, e)
            return emptyMap()
        }
        return Pop.sanitizeBests(decoded)
    }

    /** 시작 화면은 고른 단, 진행 중에는 그 판의 단 기준 최고 기록을 보여 준다 */
    val displayBest: Int
        get() {
            val playing = game.phase != RunnerPhase.READY && game.phase != RunnerPhase.OVER
            return bests[Pop.bestKey(if (playing) game.table else table)] ?: 0
        }

    fun best(table: Int?): Int = bests[Pop.bestKey(table)] ?: 0

    /** 이 판의 단 기준 최고 기록 — 종료 패널 "나의 최고 기록이에요!" 판단 */
    val runBest: Int get() = best(game.table)

    private fun recordBestIfNeeded() {
        val key = Pop.bestKey(game.table)
        if (game.score <= (bests[key] ?: 0)) return
        bests = bests + (key to game.score)
        persistence.putString(
            Persistence.POP_BEST_KEY,
            json.encodeToString(MapSerializer(String.serializer(), Int.serializer()), bests),
        )
    }

    // MARK: 조작

    /** trial: 무료 체험 웨이브 수 (null = 제한 없음) */
    fun start(trial: Int?) {
        game = Pop.create(table, trial = trial)
        fx = emptyList()
        Sound.tap()
    }

    fun togglePause() {
        game = when (game.phase) {
            RunnerPhase.RUNNING -> game.copy(phase = RunnerPhase.PAUSED)
            RunnerPhase.PAUSED -> game.copy(phase = RunnerPhase.RUNNING)
            else -> return
        }
    }

    /** 백그라운드 전환·화면 이탈 시 자동 일시정지 */
    fun pause() {
        if (game.phase != RunnerPhase.RUNNING) return
        game = game.copy(phase = RunnerPhase.PAUSED)
    }

    fun tick(dtMs: Double) {
        if (game.phase != RunnerPhase.RUNNING) return
        val dt = dtMs.coerceAtMost(MAX_DT)
        if (dt <= 0) return
        clock += dt
        val before = game
        game = Pop.advance(game, dt)
        handle(before, game.events)
        fx = fx.filter { clock - it.born < it.lifeMs }
        clearFxIfOver()
    }

    /** 판이 끝나면 시계가 멈추므로 남은 연출(붉은 지붕선·입자)을 지운다 — 그대로 두면 종료 화면에 멈춘 채 남는다 */
    private fun clearFxIfOver() {
        if (game.phase == RunnerPhase.OVER) fx = emptyList()
    }

    /**
     * 놀이 영역을 누름 — Core 좌표(x·y 0~1, aspect = 등장선~지붕선 높이 ÷ 폭)로 받는다. 화면 px 변환은 PopLayout.toCore.
     * 탭으로도 판이 끝날 수 있다(체험 마지막 정답) — handle 이 기록·소리를 맡는다.
     */
    fun tap(nx: Double, ny: Double, aspect: Double) {
        if (game.phase != RunnerPhase.RUNNING) return
        val target = Pop.target(game, nx, ny, aspect)
        val before = game
        game = Pop.tap(game, nx, ny, aspect)
        if (target != null && game.events.none { it is PopEvent.Reloading }) {
            before.balloons.firstOrNull { it.id == target }?.let { fx = fx + PopFx.Shot(it.x, it.y, clock) }
        }
        handle(before, game.events)
        clearFxIfOver()
    }

    // MARK: 이벤트 → 소리·햅틱·연출

    private fun handle(before: PopState, events: List<PopEvent>) {
        for (event in events) {
            when (event) {
                is PopEvent.WaveStarted -> Unit
                is PopEvent.Popped -> {
                    Sound.pop()
                    Haptics.impactLight()
                    // 콤보 소리는 5의 배수(배율이 오르는 때)에서만 — 매번 울리면 팡 소리와 겹친다 (iOS 와 같음)
                    if (game.combo > 0 && game.combo % 5 == 0) Sound.combo(game.combo)
                    before.balloons.firstOrNull { it.id == event.id }?.let {
                        fx = fx + PopFx.Burst(it.x, it.y, event.golden, big = false, born = clock) +
                            PopFx.Label(it.x, it.y, "+${event.points}", clock)
                    }
                    recordBestIfNeeded()
                }
                PopEvent.SlowMoStarted -> Sound.collect()
                is PopEvent.WrongTap -> {
                    Sound.wrong()
                    Haptics.error()
                    fx = fx + PopFx.Shake(event.id, clock)
                }
                PopEvent.Reloading -> Sound.tap()
                is PopEvent.Missed -> {
                    Sound.wrong()
                    Haptics.warning()
                    fx = fx + PopFx.Hurt(clock)
                }
                is PopEvent.Passed -> Unit
                is PopEvent.BossPick -> {
                    Sound.pop()
                    Haptics.impactLight()
                    before.balloons.firstOrNull { it.id == event.id }?.let {
                        fx = fx + PopFx.Burst(it.x, it.y, golden = false, big = false, born = clock) +
                            PopFx.Label(it.x, it.y, "+${event.points}", clock)
                    }
                    recordBestIfNeeded()
                }
                is PopEvent.BossHit -> {
                    Sound.correct()
                    Haptics.impactMedium()
                    val boss = before.boss
                    if (boss != null) {
                        // 짝꿍 식은 한 번에 하나만 — 새 식이 오면 이전 식을 지운다
                        fx = fx.filterNot { it is PopFx.Label && !it.text.startsWith("+") } +
                            PopFx.Label(Pop.BOSS_X, boss.y, "${event.a} × ${event.b} = ${boss.value}", clock)
                    }
                    recordBestIfNeeded()
                }
                PopEvent.BossDefeated -> {
                    Sound.levelUp()
                    Haptics.success()
                    before.boss?.let { fx = fx + PopFx.Burst(Pop.BOSS_X, it.y, golden = true, big = true, born = clock) }
                }
                PopEvent.BossEscaped -> {
                    Sound.wrong()
                    Haptics.warning()
                    fx = fx + PopFx.Hurt(clock)
                }
                // 웨이브 클리어 소리는 다른 큰 소리와 겹치면 생략 — 체험 종료(complete)·보스 격파(levelUp) 하나만 낸다 (iOS 와 같음)
                is PopEvent.WaveCleared ->
                    if (game.phase == RunnerPhase.RUNNING && events.none { it is PopEvent.BossDefeated }) Sound.collect()
                PopEvent.GameOver, PopEvent.TrialEnded -> {
                    recordBestIfNeeded()
                    Sound.complete()
                }
            }
        }
    }
}
