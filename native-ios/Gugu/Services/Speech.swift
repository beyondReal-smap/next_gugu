import AVFoundation
import Observation

// 문제 읽어주기 — 앱에 내장한 음성 파일(ElevenLabs 로 미리 생성, tools/voice)을 재생한다.
// 문장이 552개로 한정돼 있어 기기 TTS 대신 자연스러운 사람 목소리를 오프라인으로 쓴다.
// 네트워크를 쓰지 않으므로 지연·호출 비용이 없고, 아이의 데이터가 밖으로 나가지 않는다.

@Observable
final class Speech: NSObject, AVAudioPlayerDelegate, AVSpeechSynthesizerDelegate {
    static let shared = Speech()

    /// 지금 읽고 있는 줄의 키 — 구구단 표에서 읽는 줄을 강조한다
    private(set) var speakingKey: String?
    private(set) var isSpeaking = false
    private(set) var errorMessage: String?

    @ObservationIgnored private var player: AVAudioPlayer?
    @ObservationIgnored private let synthesizer = AVSpeechSynthesizer()
    @ObservationIgnored private var utterance: AVSpeechUtterance?
    @ObservationIgnored private var queue: [VoiceLine] = []
    /// stop()/새 낭독이 시작되면 올라간다 — 이전 낭독의 늦은 콜백을 무시하기 위해서다
    @ObservationIgnored private var generation = 0

    /// 줄과 줄 사이 쉼 (구구단 외우기 리듬)
    private static let gap: TimeInterval = 0.12

    private override init() {
        super.init()
        synthesizer.delegate = self
    }

    /// 한 줄을 읽는다 — 읽던 것은 끊는다
    func speak(_ line: VoiceLine) {
        speak([line])
    }

    /// 여러 줄을 이어 읽는다 — 읽던 것은 끊는다
    func speak(_ lines: [VoiceLine]) {
        stop()
        errorMessage = nil
        guard AppLanguage.current.supportsReadAloud else { return }
        queue = lines
        playNext(generation)
    }

    func stop() {
        generation += 1
        utterance = nil
        synthesizer.stopSpeaking(at: .immediate)
        player?.stop()
        player = nil
        queue = []
        speakingKey = nil
        isSpeaking = false
    }

    private func playNext(_ gen: Int) {
        guard gen == generation else { return }
        guard !queue.isEmpty else {
            isSpeaking = false
            speakingKey = nil
            return
        }
        let line = queue.removeFirst()
        if !VoiceLines.usesRecordedAudio(line) {
            guard let voice = AVSpeechSynthesisVoice(language: AppLanguage.current.rawValue) else {
                print("[Speech] 지원 음성 없음:", AppLanguage.current.rawValue)
                stop()
                errorMessage = String(localized: "이 언어의 음성을 사용할 수 없습니다. 기기의 음성 설정을 확인해 주세요.")
                return
            }
            activateSession()
            let next = AVSpeechUtterance(string: line.text)
            next.voice = voice
            next.rate = AVSpeechUtteranceDefaultSpeechRate * 0.85
            utterance = next
            speakingKey = line.key
            isSpeaking = true
            synthesizer.speak(next)
            return
        }
        // 음성 파일 누락은 빌드 결함이다 — VoiceLinesTests 가 모든 문장의 파일을 검사한다
        guard let url = Self.url(for: line.key) else {
            assertionFailure("음성 파일 없음: \(line.key)")
            print("[Speech] 음성 파일 없음:", line.key)
            playNext(gen)
            return
        }
        do {
            activateSession()
            let p = try AVAudioPlayer(contentsOf: url)
            p.delegate = self
            p.prepareToPlay()
            player = p
            speakingKey = line.key
            isSpeaking = true
            p.play()
        } catch {
            print("[Speech] 재생 실패(\(line.key)):", error)
            playNext(gen)
        }
    }

    /// 앱 언어의 음성 파일 — Resources/Voice/{ko,en,ja}/ 폴더 참조로 번들에 들어간다
    static func url(for key: String, language: AppLanguage = .current) -> URL? {
        Bundle.main.url(forResource: key, withExtension: "m4a", subdirectory: "Voice/\(language.rawValue)")
    }

    /// 효과음(Sound)과 같은 ambient 세션 — 무음 스위치를 따르고 부모님의 음악을 끊지 않는다
    private func activateSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("[Speech] 오디오 세션 설정 실패:", error)
        }
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish finished: AVSpeechUtterance) {
        DispatchQueue.main.async { [weak self] in
            guard let self, finished === self.utterance else { return }
            self.utterance = nil
            let gen = self.generation
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.gap) { [weak self] in
                self?.playNext(gen)
            }
        }
    }

    // MARK: - AVAudioPlayerDelegate

    // 콜백은 지금 재생 중인 플레이어의 것만 받는다 — 끊긴 낭독의 콜백이 늦게 도착해
    // 새 낭독의 다음 줄을 당겨오면(첫 줄이 잘림) 안 되기 때문이다

    func audioPlayerDidFinishPlaying(_ finished: AVAudioPlayer, successfully flag: Bool) {
        DispatchQueue.main.async { [weak self] in
            guard let self, finished === self.player else { return }
            let gen = self.generation
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.gap) { [weak self] in
                self?.playNext(gen)
            }
        }
    }

    func audioPlayerDecodeErrorDidOccur(_ failed: AVAudioPlayer, error: Error?) {
        print("[Speech] 디코딩 오류:", error?.localizedDescription ?? "알 수 없음")
        DispatchQueue.main.async { [weak self] in
            guard let self, failed === self.player else { return }
            self.playNext(self.generation)
        }
    }
}
