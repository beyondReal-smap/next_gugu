package site.smap.gugudan.services

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import site.smap.gugudan.BuildConfig
import site.smap.gugudan.core.VoiceLine
import java.io.FileNotFoundException

// 문제 읽어주기 (iOS Speech.swift 대응) — 앱에 내장한 음성 파일(ElevenLabs 로 미리 생성, tools/voice)을 재생한다.
// 문장이 552개로 한정돼 있어 기기 TTS 대신 자연스러운 사람 목소리를 오프라인으로 쓴다.
// 네트워크를 쓰지 않으므로 지연·호출 비용이 없고, 아이의 데이터가 밖으로 나가지 않는다.

object Speech {
    private const val TAG = "Speech"
    /** 줄과 줄 사이 쉼 (구구단 외우기 리듬) */
    private const val GAP_MS = 120L

    /** 지금 읽고 있는 줄의 키 — 구구단 표에서 읽는 줄을 강조한다 */
    var speakingKey by mutableStateOf<String?>(null)
        private set
    var isSpeaking by mutableStateOf(false)
        private set

    private var app: Context? = null
    private var player: MediaPlayer? = null
    private val queue = ArrayDeque<VoiceLine>()
    /** stop()/새 낭독이 시작되면 올라간다 — 이전 낭독의 늦은 콜백을 무시하기 위해서다 */
    private var generation = 0
    private val main = Handler(Looper.getMainLooper())

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** 한 줄을 읽는다 — 읽던 것은 끊는다 */
    fun speak(line: VoiceLine) = speak(listOf(line))

    /** 여러 줄을 이어 읽는다 — 읽던 것은 끊는다 */
    fun speak(lines: List<VoiceLine>) {
        stop()
        queue.addAll(lines)
        playNext(generation)
    }

    fun stop() {
        generation += 1
        main.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        queue.clear()
        speakingKey = null
        isSpeaking = false
    }

    private fun playNext(gen: Int) {
        if (gen != generation) return
        val line = queue.removeFirstOrNull()
        if (line == null) {
            isSpeaking = false
            speakingKey = null
            return
        }
        val context = checkNotNull(app) { "Speech.init 전에 호출됐습니다" }
        val afd = try {
            context.assets.openFd("voice/${line.key}.m4a")
        } catch (e: FileNotFoundException) {
            // 음성 파일 누락은 빌드 결함이다 — VoiceLinesTests 가 모든 문장의 파일을 검사한다.
            // 디버그 빌드에서는 바로 멈춰 드러내고(iOS assertionFailure 와 같다), 출시 빌드에서는 그 줄만 건너뛴다
            check(!BuildConfig.DEBUG) { "음성 파일 없음: ${line.key}" }
            Log.e(TAG, "음성 파일 없음: ${line.key}", e)
            playNext(gen)
            return
        }
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(attributes)
            afd.use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            mp.setOnCompletionListener { done ->
                done.release()
                if (player === done) player = null
                main.postDelayed({ playNext(gen) }, GAP_MS)
            }
            mp.setOnErrorListener { failed, what, extra ->
                Log.e(TAG, "재생 오류(${line.key}): what=$what extra=$extra")
                failed.release()
                if (player === failed) player = null
                main.post { playNext(gen) }
                true
            }
            mp.prepare()   // 앱 내장 파일이라 동기 준비가 빠르다
            player = mp
            speakingKey = line.key
            isSpeaking = true
            mp.start()
        } catch (e: Exception) {
            Log.e(TAG, "재생 준비 실패(${line.key})", e)
            mp.release()
            playNext(gen)
        }
    }
}
