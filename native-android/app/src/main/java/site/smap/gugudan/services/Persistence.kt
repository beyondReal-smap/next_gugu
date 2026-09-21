package site.smap.gugudan.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.json.Json
import site.smap.gugudan.core.GameState
import site.smap.gugudan.core.adventure.AdventureProgress

// 영속화 — iOS UserDefaults 대응(SharedPreferences + kotlinx JSON). 저장 키 동일 유지.

class Persistence(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gugu", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        const val PROGRESS_KEY = "gugu.progress.v1"
        const val ADVENTURE_KEY = "gugu.adventure.v1"
        const val THEME_KEY = "gugu.theme"
        const val PREMIUM_KEY = "gugu.premium.v1"
        const val SOUND_KEY = "gugu.sound"
        /** 구구 점프 단별 최고 기록 (웹 localStorage 키와 동일) */
        const val RUNNER_BEST_KEY = "gugu.runner.best.v1"
        const val LANE_BEST_KEY = "gugu.lane.best.v1"
        const val BASKET_BEST_KEY = "gugu.basket.best.v1"
    }

    fun loadGameState(): GameState? = decodeOrNull(PROGRESS_KEY) {
        json.decodeFromString(GameState.serializer(), it)
    }

    fun saveGameState(state: GameState) =
        putString(PROGRESS_KEY, json.encodeToString(GameState.serializer(), state))

    fun loadAdventure(): AdventureProgress? = decodeOrNull(ADVENTURE_KEY) {
        json.decodeFromString(AdventureProgress.serializer(), it)
    }

    fun saveAdventure(p: AdventureProgress) =
        putString(ADVENTURE_KEY, json.encodeToString(AdventureProgress.serializer(), p))

    private inline fun <T> decodeOrNull(key: String, decode: (String) -> T): T? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            decode(raw)
        } catch (e: Exception) {
            Log.e("Persistence", "로드 실패($key)", e)
            null
        }
    }

    fun getString(key: String): String? = prefs.getString(key, null)
    fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
    fun remove(key: String) = prefs.edit().remove(key).apply()
}
