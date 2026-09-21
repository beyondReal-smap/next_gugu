package site.smap.gugudan.services

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

// 햅틱 (iOS Haptics.swift 대응) — 전역 탭 피드백은 GGButton 등 공통 컴포넌트가 호출

object Haptics {
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private fun oneShot(ms: Long, amplitude: Int) {
        vibrator?.vibrate(VibrationEffect.createOneShot(ms, amplitude))
    }
    private fun waveform(timings: LongArray, amplitudes: IntArray) {
        vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    fun impactLight() = oneShot(12, 70)
    fun impactMedium() = oneShot(18, 140)
    fun success() = waveform(longArrayOf(0, 20, 60, 30), intArrayOf(0, 120, 0, 200))
    fun warning() = waveform(longArrayOf(0, 25, 50, 25), intArrayOf(0, 150, 0, 150))
    fun error() = waveform(longArrayOf(0, 35, 60, 35, 60, 35), intArrayOf(0, 180, 0, 180, 0, 180))
    fun selection() = oneShot(8, 50)
}
