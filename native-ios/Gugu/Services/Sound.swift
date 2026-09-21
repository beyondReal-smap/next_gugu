import Foundation
import AVFoundation

// 효과음 — Web Audio 합성(sound.ts) 이식. 외부 음원 없이 오실레이터 톤을 실시간 합성.
// AVAudioSourceNode 렌더 콜백에서 활성 보이스를 믹싱한다.

enum Waveform {
    case sine, triangle
}

private struct Voice {
    let freq: Double
    let startSample: Double   // 전역 샘플 클럭 기준 시작
    let durSamples: Double
    let gain: Double
    let type: Waveform
}

final class Sound {
    static let shared = Sound()

    private let engine = AVAudioEngine()
    private var source: AVAudioSourceNode!
    private let sampleRate: Double = 44_100
    private let lock = NSLock()
    private var voices: [Voice] = []
    private var clock: Double = 0        // 렌더 콜백이 증가시키는 전역 샘플 카운터
    private var started = false

    private(set) var enabled: Bool

    private init() {
        enabled = (Persistence.string("gugu.sound") ?? "1") == "1"
        setupEngine()
    }

    private func setupEngine() {
        let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1)!
        source = AVAudioSourceNode { [weak self] _, _, frameCount, audioBufferList -> OSStatus in
            guard let self else { return noErr }
            let abl = UnsafeMutableAudioBufferListPointer(audioBufferList)
            let ptr = abl[0].mData!.assumingMemoryBound(to: Float.self)
            self.lock.lock()
            let localVoices = self.voices
            var c = self.clock
            self.lock.unlock()

            for frame in 0..<Int(frameCount) {
                var sample: Double = 0
                let now = c + Double(frame)
                for v in localVoices {
                    let rel = now - v.startSample
                    if rel < 0 || rel > v.durSamples { continue }
                    let t = rel / self.sampleRate
                    let phase = 2 * Double.pi * v.freq * t
                    var w: Double
                    switch v.type {
                    case .sine: w = sin(phase)
                    case .triangle: w = 2 / Double.pi * asin(sin(phase))
                    }
                    // 지수 엔벨로프 근사 (attack 0.01s, 이후 감쇠)
                    let dur = v.durSamples / self.sampleRate
                    let env: Double
                    let a = 0.01
                    if t < a {
                        env = t / a
                    } else {
                        env = pow(0.0001, (t - a) / max(0.0001, dur - a))
                    }
                    sample += w * v.gain * env
                }
                ptr[frame] = Float(max(-1, min(1, sample)))
            }

            c += Double(frameCount)
            self.lock.lock()
            self.clock = c
            // 만료된 보이스 정리
            self.voices.removeAll { c - $0.startSample > $0.durSamples }
            self.lock.unlock()
            return noErr
        }

        engine.attach(source)
        engine.connect(source, to: engine.mainMixerNode, format: format)
        engine.mainMixerNode.outputVolume = 1.0
    }

    private func ensureRunning() {
        guard !started else { return }
        do {
            try AVAudioSession.sharedInstance().setCategory(.ambient, options: [.mixWithOthers])
            try AVAudioSession.sharedInstance().setActive(true)
            try engine.start()
            started = true
        } catch {
            print("[Sound] 엔진 시작 실패:", error)
        }
    }

    private func tone(_ freq: Double, at start: Double, dur: Double, type: Waveform = .sine, gain: Double = 0.12) {
        guard enabled else { return }
        ensureRunning()
        lock.lock()
        let startSample = clock + start * sampleRate
        voices.append(Voice(freq: freq, startSample: startSample, durSamples: dur * sampleRate, gain: gain, type: type))
        lock.unlock()
    }

    // MARK: - Public API (sound.ts 이식)

    func setEnabled(_ v: Bool) {
        enabled = v
        Persistence.setString(v ? "1" : "0", key: "gugu.sound")
        if v { tone(660, at: 0, dur: 0.08, type: .sine, gain: 0.1) }
    }

    func tap()      { tone(330, at: 0, dur: 0.04, type: .sine, gain: 0.05) }
    func correct() {
        tone(587.33, at: 0, dur: 0.09, gain: 0.12)   // D5
        tone(880, at: 0.07, dur: 0.12, gain: 0.1)    // A5
    }
    func wrong() {
        tone(220, at: 0, dur: 0.14, gain: 0.09)
        tone(174.61, at: 0.09, dur: 0.18, gain: 0.08)
    }
    func combo(_ combo: Int) {
        let base = 523.25
        let step = Double(min(combo, 12))
        tone(base * pow(2, step / 12), at: 0, dur: 0.1, type: .triangle, gain: 0.1)
    }
    /// 별 조각 수집 — 짧고 영롱한 상승 2음
    func collect() {
        tone(1046.5, at: 0, dur: 0.08, type: .triangle, gain: 0.1)    // C6
        tone(1318.5, at: 0.06, dur: 0.12, type: .triangle, gain: 0.09) // E6
    }

    func levelUp() {
        for (i, f) in [523.25, 659.25, 783.99, 1046.5].enumerated() {
            tone(f, at: Double(i) * 0.09, dur: 0.18, type: .triangle, gain: 0.12)
        }
    }
    func complete() {
        for (i, f) in [523.25, 659.25, 783.99].enumerated() {
            tone(f, at: Double(i) * 0.1, dur: 0.2, gain: 0.11)
        }
    }
}
