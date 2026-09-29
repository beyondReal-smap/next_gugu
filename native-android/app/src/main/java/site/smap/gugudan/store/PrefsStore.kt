package site.smap.gugudan.store

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import site.smap.gugudan.services.Haptics
import site.smap.gugudan.services.Persistence
import site.smap.gugudan.services.Sound
import site.smap.gugudan.services.Speech

// 환경설정 — 효과음·진동·문제 읽어주기 (iOS PrefsStore.swift / 웹 PrefsProvider 대응).
// 실제 동작은 각 서비스(Sound/Haptics/Speech)가 하고, 이 스토어는 설정 화면이 관찰할 상태와 저장을 맡는다.

class PrefsStore(private val persistence: Persistence) {
    companion object {
        const val READ_ALOUD_KEY = "gugu.readAloud"
    }

    var soundOn by mutableStateOf(Sound.enabled)
        private set
    var hapticsOn by mutableStateOf(Haptics.enabled)
        private set

    /** 새 문제가 나오면 소리 내어 읽어 준다 */
    var readAloud by mutableStateOf(persistence.getString(READ_ALOUD_KEY) == "1")
        private set

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
