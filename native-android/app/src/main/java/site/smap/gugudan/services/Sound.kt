package site.smap.gugudan.services

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.pow
import kotlin.math.sin

// 효과음 — 오실레이터 톤 실시간 합성 (iOS Sound.swift / web sound.ts 이식).
// 스트리밍 AudioTrack에 백그라운드 스레드가 활성 보이스를 믹싱해 쓴다.

object Sound {
    private const val RATE = 44100

    private data class Voice(
        val freq: Double,
        val startFrame: Long,
        val durFrames: Long,
        val gain: Double,
        val triangle: Boolean,
    )

    private val lock = Any()
    private val voices = mutableListOf<Voice>()
    private var frame = 0L
    private var thread: Thread? = null

    var enabled = true
        private set
    private var persistence: Persistence? = null

    fun init(p: Persistence) {
        persistence = p
        enabled = p.getString(Persistence.SOUND_KEY) != "0"
    }

    fun setEnabled(v: Boolean) {
        enabled = v
        persistence?.putString(Persistence.SOUND_KEY, if (v) "1" else "0")
        if (v) tone(660.0, 0.0, 0.08, gain = 0.1)
    }

    private fun ensureThread() {
        if (thread?.isAlive == true) return
        thread = Thread {
            val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minBuf, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            val buf = ShortArray(1024)
            while (true) {
                val localVoices: List<Voice>
                val base: Long
                synchronized(lock) {
                    voices.removeAll { frame - it.startFrame > it.durFrames }
                    localVoices = voices.toList()
                    base = frame
                    frame += buf.size
                }
                for (i in buf.indices) {
                    val now = base + i
                    var sample = 0.0
                    for (v in localVoices) {
                        val rel = now - v.startFrame
                        if (rel < 0 || rel > v.durFrames) continue
                        val t = rel.toDouble() / RATE
                        val phase = 2 * PI * v.freq * t
                        val w = if (v.triangle) 2 / PI * asin(sin(phase)) else sin(phase)
                        // 엔벨로프: attack 0.01s → 지수 감쇠 (iOS와 동일)
                        val dur = v.durFrames.toDouble() / RATE
                        val a = 0.01
                        val env = if (t < a) t / a else 0.0001.pow((t - a) / maxOf(0.0001, dur - a))
                        sample += w * v.gain * env
                    }
                    buf[i] = (sample.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
                }
                track.write(buf, 0, buf.size)
            }
        }.apply { isDaemon = true; name = "gugu-sound"; start() }
    }

    private fun tone(freq: Double, at: Double, dur: Double, triangle: Boolean = false, gain: Double = 0.12) {
        if (!enabled) return
        ensureThread()
        synchronized(lock) {
            voices += Voice(freq, frame + (at * RATE).toLong(), (dur * RATE).toLong(), gain, triangle)
        }
    }

    // MARK: Public API (sound.ts 주파수 테이블 동일)

    fun tap() = tone(330.0, 0.0, 0.04, gain = 0.05)
    fun correct() {
        tone(587.33, 0.0, 0.09, gain = 0.12)   // D5
        tone(880.0, 0.07, 0.12, gain = 0.1)    // A5
    }
    fun wrong() {
        tone(220.0, 0.0, 0.14, gain = 0.09)
        tone(174.61, 0.09, 0.18, gain = 0.08)
    }
    fun combo(combo: Int) {
        val step = minOf(combo, 12).toDouble()
        tone(523.25 * 2.0.pow(step / 12), 0.0, 0.1, triangle = true, gain = 0.1)
    }
    fun collect() {
        tone(1046.5, 0.0, 0.08, triangle = true, gain = 0.1)
        tone(1318.5, 0.06, 0.12, triangle = true, gain = 0.09)
    }
    fun levelUp() {
        listOf(523.25, 659.25, 783.99, 1046.5).forEachIndexed { i, f ->
            tone(f, i * 0.09, 0.18, triangle = true, gain = 0.12)
        }
    }
    fun complete() {
        listOf(523.25, 659.25, 783.99).forEachIndexed { i, f ->
            tone(f, i * 0.1, 0.2, gain = 0.11)
        }
    }
}
