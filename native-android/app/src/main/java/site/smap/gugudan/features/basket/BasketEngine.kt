package site.smap.gugudan.features.basket

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import site.smap.gugudan.core.Basket
import site.smap.gugudan.core.BasketOutcome
import site.smap.gugudan.core.BasketState
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.RunnerPhase
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Persistence
import site.smap.gugudan.services.Sound

// 구구 바구니 진행 상태 관리 (iOS BasketEngine 이식)
// 게임 규칙은 core/Basket.kt 순수 함수가 담당하고, 여기서는 프레임 반영과 기록 저장만 맡는다.

class BasketEngine(private val persistence: Persistence) {
    var game: BasketState by mutableStateOf(Basket.create(2, RunnerPhase.READY))
        private set
    var bests: Map<String, Int> by mutableStateOf(emptyMap())
        private set

    /** 선택한 단 (null = 전체). 처음에는 2단부터 천천히. */
    var table: Int? by mutableStateOf(2)

    /** 방향 버튼을 누르고 있는 동안의 이동 방향 (-1 왼쪽 / 1 오른쪽 / 0 정지) */
    var direction: Int by mutableStateOf(0)

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val TABLES = (Problems.MIN_TABLE..Problems.MAX_TABLE).toList()

        /** 방향 버튼을 누르고 있을 때의 이동 속도(px/ms) — 웹과 동일 */
        private const val HOLD_SPEED = 0.28
    }

    init {
        bests = loadBests()
    }

    // MARK: 기록

    private fun loadBests(): Map<String, Int> {
        val raw = persistence.getString(Persistence.BASKET_BEST_KEY) ?: return emptyMap()
        val decoded = try {
            json.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), raw)
        } catch (e: Exception) {
            // 저장본이 깨졌으면 기록만 비우고 게임은 계속할 수 있게 둔다
            return emptyMap()
        }
        val allowed = (listOf("all") + TABLES.map { it.toString() }).toSet()
        return decoded.filter { it.key in allowed && it.value >= 0 }
    }

    private fun bestKey(t: Int?): String = t?.toString() ?: "all"

    /** 시작 화면은 고른 단, 진행 중에는 그 판의 단 기준 최고 기록을 보여 준다 */
    val displayBest: Int
        get() {
            val playing = game.phase != RunnerPhase.READY && game.phase != RunnerPhase.OVER
            return bests[bestKey(if (playing) game.table else table)] ?: 0
        }

    val runBest: Int get() = bests[bestKey(game.table)] ?: 0

    private fun recordBestIfNeeded() {
        val key = bestKey(game.table)
        if (game.score <= (bests[key] ?: 0)) return
        bests = bests + (key to game.score)
        persistence.putString(
            Persistence.BASKET_BEST_KEY,
            json.encodeToString(MapSerializer(String.serializer(), Int.serializer()), bests),
        )
    }

    // MARK: 조작

    fun start() {
        direction = 0
        game = Basket.create(table)
        Sound.tap()
    }

    /** 무대를 끌어서 옮기기 — 0~1 비율을 바구니 좌표로 바꾼다 */
    fun drag(ratio: Float) {
        game = Basket.move(game, ratio * Basket.WIDTH)
    }

    /** 방향 버튼 한 번 탭 (보조 입력) */
    fun nudge(delta: Int) {
        game = Basket.move(game, game.x + delta * 24.0)
    }

    fun togglePause() {
        game = when (game.phase) {
            RunnerPhase.RUNNING -> {
                direction = 0
                game.copy(phase = RunnerPhase.PAUSED)
            }
            RunnerPhase.PAUSED -> game.copy(phase = RunnerPhase.RUNNING)
            else -> return
        }
    }

    /** 백그라운드 전환·화면 이탈 시 자동 일시정지 */
    fun pause() {
        direction = 0
        if (game.phase != RunnerPhase.RUNNING) return
        game = game.copy(phase = RunnerPhase.PAUSED)
    }

    fun tick(dtMs: Double) {
        if (game.phase != RunnerPhase.RUNNING) return
        val dt = dtMs.coerceAtMost(80.0)
        if (dt <= 0) return

        val before = game
        if (direction != 0) game = Basket.move(game, game.x + direction * dt * HOLD_SPEED)
        game = Basket.advance(game, dt)

        val outcome = game.outcome
        if (before.outcome == null && outcome != null) {
            if (outcome == BasketOutcome.CORRECT) {
                Sound.correct()
                Haptics.impactLight()
                if (game.combo >= 3) Sound.combo(game.combo)
                recordBestIfNeeded()
            } else {
                Sound.wrong()
                Haptics.error()
            }
        }
        if (game.phase == RunnerPhase.OVER) {
            recordBestIfNeeded()
            Sound.complete()
        }
    }
}
