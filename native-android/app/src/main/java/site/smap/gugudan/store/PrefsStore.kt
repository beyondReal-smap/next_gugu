package site.smap.gugudan.store

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import site.smap.gugudan.core.Problems
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Persistence
import site.smap.gugudan.services.Sound
import site.smap.gugudan.services.Speech

// 환경설정 — 효과음·진동·문제 읽어주기·곱하는 수 상한 (iOS PrefsStore.swift / 웹 PrefsProvider 대응).
// 실제 동작은 각 서비스(Sound/Haptics/Speech)가 하고, 이 스토어는 설정 화면이 관찰할 상태와 저장을 맡는다.

class PrefsStore(private val persistence: Persistence) {
    companion object {
        const val READ_ALOUD_KEY = "gugu.readAloud"
        const val MAX_MULTIPLIER_KEY = "gugu.maxMultiplier"
    }

    var soundOn by mutableStateOf(Sound.enabled)
        private set
    var hapticsOn by mutableStateOf(Haptics.enabled)
        private set

    /** 새 문제가 나오면 소리 내어 읽어 준다 */
    var readAloud by mutableStateOf(persistence.getString(READ_ALOUD_KEY) == "1")
        private set

    /** 각 단에서 몇까지 곱할지 (Problems.MAX_B_OPTIONS 중 하나) — 출제·학습표·게임이 Problems.maxB 로 따른다 */
    var maxMultiplier by mutableStateOf(loadMaxMultiplier())
        private set

    init {
        Problems.maxB = maxMultiplier
    }

    /**
     * 저장값이 없으면 기본값(9). 선택지에 없는 저장값(다음 버전에서 선택지가 바뀐 경우 등)은
     * 실행할 때마다 멈추지 않도록 로그를 남기고 기본값으로 되돌린다 — 저장소 경계의 정규화
     */
    private fun loadMaxMultiplier(): Int {
        val raw = persistence.getString(MAX_MULTIPLIER_KEY) ?: return Problems.DEFAULT_MAX_B
        val value = raw.toIntOrNull()
        if (value == null || value !in Problems.MAX_B_OPTIONS) {
            Log.w("Prefs", "곱하는 수 상한 저장값이 선택지에 없음 — 기본값으로: $raw")
            return Problems.DEFAULT_MAX_B
        }
        return value
    }

    fun updateMaxMultiplier(value: Int) {
        Problems.maxB = value   // 선택지 밖이면 여기서 멈춘다 (화면은 선택지만 보여 준다)
        maxMultiplier = value
        persistence.putString(MAX_MULTIPLIER_KEY, value.toString())
    }

    fun updateSound(on: Boolean) {
        soundOn = on
        Sound.setEnabled(on)
    }

    fun updateHaptics(on: Boolean) {
        hapticsOn = on
        Haptics.enabled = on
        persistence.putString(Haptics.ENABLED_KEY, if (on) "1" else "0")
    }

    fun updateReadAloud(on: Boolean) {
        readAloud = on
        persistence.putString(READ_ALOUD_KEY, if (on) "1" else "0")
        if (!on) Speech.stop()
    }
}
