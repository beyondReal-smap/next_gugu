import Foundation
import Observation

// 환경설정 — 효과음·진동·문제 읽어주기·곱하는 수 상한 (웹 PrefsProvider 대응).
// 실제 동작은 각 서비스(Sound/Haptics/Speech)가 하고, 이 스토어는 설정 화면이 관찰할 상태와 저장을 맡는다.

@Observable
final class PrefsStore {
    static let readAloudKey = "gugu.readAloud"
    static let maxMultiplierKey = "gugu.maxMultiplier"

    var soundOn: Bool {
        didSet { Sound.shared.setEnabled(soundOn) }
    }

    var hapticsOn: Bool {
        didSet {
            Haptics.enabled = hapticsOn
            Persistence.setString(hapticsOn ? "1" : "0", key: Haptics.enabledKey)
        }
    }

    /// 새 문제가 나오면 소리 내어 읽어 준다
    var readAloud: Bool {
        didSet {
            Persistence.setString(readAloud ? "1" : "0", key: Self.readAloudKey)
            if !readAloud { Speech.shared.stop() }
        }
    }

    /// 각 단에서 몇까지 곱할지 (Problems.maxBOptions 중 하나) — 출제·학습표·게임이 Problems.maxB 로 따른다
    var maxMultiplier: Int {
        didSet {
            Problems.maxB = maxMultiplier
            Persistence.setString(String(maxMultiplier), key: Self.maxMultiplierKey)
        }
    }

    init() {
        soundOn = Sound.shared.enabled
        hapticsOn = Haptics.enabled
        readAloud = Persistence.string(Self.readAloudKey) == "1"
        maxMultiplier = Self.loadMaxMultiplier()
        Problems.maxB = maxMultiplier
    }

    /// 저장값이 없으면 기본값(9). 선택지에 없는 저장값(다음 버전에서 선택지가 바뀐 경우 등)은
    /// 실행할 때마다 멈추지 않도록 로그를 남기고 기본값으로 되돌린다 — 저장소 경계의 정규화
    private static func loadMaxMultiplier() -> Int {
        guard let raw = Persistence.string(maxMultiplierKey) else { return Problems.defaultMaxB }
        guard let value = Int(raw), Problems.maxBOptions.contains(value) else {
            print("[Prefs] 곱하는 수 상한 저장값이 선택지에 없음 — 기본값으로:", raw)
            return Problems.defaultMaxB
        }
        return value
    }
}
