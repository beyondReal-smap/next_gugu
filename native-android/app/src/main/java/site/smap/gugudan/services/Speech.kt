package site.smap.gugudan.services

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import java.util.Locale
import site.smap.gugudan.R
import site.smap.gugudan.core.VoiceLines
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import site.smap.gugudan.BuildConfig
import site.smap.gugudan.core.AppLanguage
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
    private var synthesizer: TextToSpeech? = null
    private var synthesisReady = false
    private var synthesisFailed = false
    private var waitingForSynthesis = false
    private var activeUtterance: String? = null
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
        if (synthesizer != null) return
        synthesizer = TextToSpeech(context.applicationContext) { status ->
            main.post {
                synthesisReady = status == TextToSpeech.SUCCESS
                synthesisFailed = !synthesisReady
                if (synthesisReady) {
                    synthesizer?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) {
                            main.post {
                                if (utteranceId == null || utteranceId != activeUtterance) return@post
                                activeUtterance = null
                                val gen = generation
                                main.postDelayed({ playNext(gen) }, GAP_MS)
                            }
                        }
                        override fun onError(utteranceId: String?) {
                            main.post {
                                if (utteranceId != null && utteranceId == activeUtterance) synthesisError()
                            }
                        }
                    })
                }
                if (waitingForSynthesis) {
                    waitingForSynthesis = false
                    playNext(generation)
                }
            }
        }
    }

    /** 한 줄을 읽는다 — 읽던 것은 끊는다 */
    fun speak(line: VoiceLine) = speak(listOf(line))

    /** 여러 줄을 이어 읽는다 — 읽던 것은 끊는다 */
    fun speak(lines: List<VoiceLine>) {
        stop()
        if (!AppLanguage.current.supportsReadAloud) return
        queue.addAll(lines)
        playNext(generation)
    }

    fun stop() {
        generation += 1
        waitingForSynthesis = false
        activeUtterance = null
        synthesizer?.stop()
        player?.release()
        player = null
        queue.clear()
        speakingKey = null
        isSpeaking = false
    }

    private fun synthesisError() {
        Log.e(TAG, "기기 음성을 사용할 수 없습니다: ${AppLanguage.current.code}")
        stop()
        app?.let { Toast.makeText(it, R.string.speech_unavailable, Toast.LENGTH_LONG).show() }
    }

    private fun playNext(gen: Int) {
        if (gen != generation) return
        val next = queue.firstOrNull()
        if (next != null && !VoiceLines.usesRecordedAudio(next) && !synthesisReady) {
            if (synthesisFailed) synthesisError() else waitingForSynthesis = true
            return
        }
        val line = queue.removeFirstOrNull()
        if (line == null) {
            isSpeaking = false
            speakingKey = null
            return
        }
        val context = checkNotNull(app) { "Speech.init 전에 호출됐습니다" }
        if (!VoiceLines.usesRecordedAudio(line)) {
            val tts = checkNotNull(synthesizer)
            val locale = Locale.forLanguageTag(AppLanguage.current.code)
            if (tts.setLanguage(locale) < 0) {
                synthesisError()
                return
            }
            val voice = tts.voices?.filter { it.locale.language == locale.language && !it.isNetworkConnectionRequired }
                ?.sortedWith(compareByDescending<android.speech.tts.Voice> { it.quality }.thenBy { it.name })?.firstOrNull()
            if (voice == null || tts.setVoice(voice) == TextToSpeech.ERROR) {
                synthesisError()
                return
            }
            tts.setSpeechRate(0.85f)
            val id = "$gen:${line.key}"
            activeUtterance = id
            speakingKey = line.key
            isSpeaking = true
            if (tts.speak(line.text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR) synthesisError()
            return
        }
        val afd = try {
            // 음성 팩은 언어별 폴더 — 키는 언어와 무관하다 (VoiceLines)
            context.assets.openFd("voice/${AppLanguage.current.code}/${line.key}.m4a")
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
