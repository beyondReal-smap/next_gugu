package site.smap.gugudan.store

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import site.smap.gugudan.core.games.PlayGame
import site.smap.gugudan.core.games.ProblemOrder
import site.smap.gugudan.services.Persistence

// 게임 문제 순서 선택 기억 — 구구 계단·구구 몬스터마다 마지막으로 고른 [순서대로]·[무작위] (키 gugu.gameOrder.v1, iOS GameOrderStore.swift 와 같은 계약)
// 처음(저장본 없음)은 둘 다 순서대로. 보상(gugu.play.v1)·진행도(gugu.progress.v1) 저장본과 키를 나눠
// 이 형식이 바뀌어도 그쪽 구버전 호환에 영향을 주지 않는다.
// 못 읽는 저장본(모르는 순서 값·빠진 항목)은 원본을 격리하고 순서대로로 시작한다(PlayRewardsStore 와 같은 관례).
// 시작 때는 저장하지 않고, 시작 창에서 고를 때만 저장한다 — 고르기 전에는 원본을 덮어쓰지 않는다.
// 저장 위치는 보상과 같은 "gugu" prefs(백업 대상) — 기기에 묶인 값이 아니라 아이의 설정이라 기기를 바꿔도 따라간다.
// 원문 읽기·쓰기·격리는 게임 저장본이 함께 쓰는 PlayRewardsStorage(앱 = Persistence, 테스트 = 메모리)를 그대로 쓴다.

/** 저장본 — 두 게임 값이 모두 있어야 읽힌다(빠진 항목을 기본값으로 메우지 않도록 기본값을 두지 않음) */
@Serializable
data class GameOrderChoice(
    val stairs: ProblemOrder,
    val monster: ProblemOrder,
) {
    fun order(game: PlayGame): ProblemOrder = when (game) {
        PlayGame.STAIRS -> stairs
        PlayGame.MONSTER -> monster
    }

    companion object {
        /** 저장본이 없을 때 — 둘 다 순서대로(지금까지의 동작) */
        val DEFAULT = GameOrderChoice(ProblemOrder.SEQUENTIAL, ProblemOrder.SEQUENTIAL)
    }
}

/**
 * key 는 테스트가 고유 키를 넘겨 실제 저장본과 섞이지 않게 한다.
 * overrides 는 DEBUG 훅(debug.gugu.stairs_order·monster_order — debugOverrides) — 저장된 선택보다 우선하고 저장하지는 않는다
 */
class GameOrderStore(
    private val storage: PlayRewardsStorage,
    private val key: String = Persistence.GAME_ORDER_KEY,
    private val overrides: Map<PlayGame, ProblemOrder> = emptyMap(),
) {
    var saved: GameOrderChoice by mutableStateOf(load() ?: GameOrderChoice.DEFAULT)
        private set

    /** 이 게임을 열 때 쓸 순서 */
    fun order(game: PlayGame): ProblemOrder = overrides[game] ?: saved.order(game)

    /** 시작 창에서 고름 — 바로 저장한다 */
    fun setOrder(order: ProblemOrder, game: PlayGame) {
        saved = when (game) {
            PlayGame.STAIRS -> saved.copy(stairs = order)
            PlayGame.MONSTER -> saved.copy(monster = order)
        }
        storage.write(key, json.encodeToString(GameOrderChoice.serializer(), saved))
    }

    private fun load(): GameOrderChoice? {
        val raw = storage.read(key) ?: return null
        return try {
            json.decodeFromString(GameOrderChoice.serializer(), raw)
        } catch (e: Exception) {
            // 모르는 순서 값·빠진 항목·깨진 JSON(SerializationException) — 덮어쓰기 전에 원본을 남기고 순서대로로 시작
            storage.quarantine(key, raw, e)
            null
        }
    }

    companion object {
        /** Persistence 와 같은 설정 — 모르는 키는 건너뛴다(새 버전 저장본) */
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** DEBUG 훅 이름(iOS GUGU_STAIRS_ORDER·GUGU_MONSTER_ORDER 대응) — adb shell setprop debug.gugu.stairs_order random */
        val DEBUG_PROPS = mapOf(PlayGame.STAIRS to "debug.gugu.stairs_order", PlayGame.MONSTER to "debug.gugu.monster_order")

        /**
         * prop 은 시스템 프로퍼티를 읽는 함수(DevProps.get — 릴리스는 늘 null).
         * 값은 sequential|random. 잘못된 값은 조용히 넘기지 않고 멈춘다
         */
        fun debugOverrides(prop: (String) -> String?): Map<PlayGame, ProblemOrder> {
            val result = mutableMapOf<PlayGame, ProblemOrder>()
            for ((game, name) in DEBUG_PROPS) {
                val raw = prop(name) ?: continue
                result[game] = requireNotNull(ProblemOrder.fromRawValue(raw)) {
                    "$name 는 ${ProblemOrder.entries.map { it.rawValue }} 중 하나: $raw"  // i18n:skip — 단언 메시지(다음 줄로 넘긴 require)
                }
            }
            return result
        }
    }
}
