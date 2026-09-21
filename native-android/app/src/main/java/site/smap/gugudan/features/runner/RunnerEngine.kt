package site.smap.gugudan.features.runner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import site.smap.gugudan.core.Problems
import site.smap.gugudan.core.Runner
import site.smap.gugudan.core.RunnerOutcome
import site.smap.gugudan.core.RunnerPhase
import site.smap.gugudan.core.RunnerState
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Persistence
import site.smap.gugudan.services.Sound

// 구구 점프 진행 상태 관리 (iOS RunnerEngine 이식)
// 게임 규칙은 core/Runner.kt 순수 함수가 담당하고, 여기서는 프레임 반영과 기록 저장만 맡는다.

class RunnerEngine(private val persistence: Persistence) {
    var game: RunnerState by mutableStateOf(Runner.create(null, RunnerPhase.READY))
        private set
    var bests: Map<String, Int> by mutableStateOf(emptyMap())
        private set

    /** 선택한 단 (null = 전체) */
    var table: Int? by mutableStateOf(null)

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val TABLES = (Problems.MIN_TABLE..Problems.MAX_TABLE).toList()
    }

    init {
        bests = loadBests()
    }

    // MARK: 기록

    private fun loadBests(): Map<String, Int> {
        val raw = persistence.getString(Persistence.RUNNER_BEST_KEY) ?: return emptyMap()
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

    /** 선택한 단의 최고 기록 (시작 화면 표시용) */
    val selectedBest: Int get() = bests[bestKey(table)] ?: 0

    /** 진행 중인 판의 단 기준 최고 기록 */
    val runBest: Int get() = bests[bestKey(game.table)] ?: 0

    private fun recordBestIfNeeded() {
        val key = bestKey(game.table)
        if (game.score <= (bests[key] ?: 0)) return
        bests = bests + (key to game.score)
        persistence.putString(
            Persistence.RUNNER_BEST_KEY,
            json.encodeToString(MapSerializer(String.serializer(), Int.serializer()), bests),
        )
    }

    // MARK: 조작

    fun start() {
        game = Runner.create(table)
        Sound.tap()
    }

    fun answer(index: Int) {
        val current = game
        if (current.phase != RunnerPhase.RUNNING || current.outcome != null) return
        val choice = current.question.choices.getOrNull(index) ?: return
        val next = Runner.answer(current, choice)
        if (next.outcome == current.outcome) return
        game = next
        if (next.outcome == RunnerOutcome.CORRECT) {
            Sound.correct()
            Haptics.impactLight()
        } else {
            Sound.wrong()
            Haptics.error()
        }
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

    /**
     * 프레임 진행. dt(ms) 는 화면 쪽 withFrameMillis 루프가 넘긴다.
     * 복귀 직후나 긴 프레임 때문에 장애물이 한 번에 통과하지 않도록 상한을 둔다.
     */
    fun tick(dtMs: Double) {
        if (game.phase != RunnerPhase.RUNNING) return
        val dt = dtMs.coerceAtMost(80.0)
        if (dt <= 0) return

        val before = game
        game = Runner.advance(before, dt)

        if (game.score > before.score) {
            Sound.tap()
            recordBestIfNeeded()
        }
        if (game.hitMs > 0 && before.hitMs == 0.0) Haptics.error()
        if (game.phase == RunnerPhase.OVER) {
            recordBestIfNeeded()
            Sound.complete()
        }
    }
}
