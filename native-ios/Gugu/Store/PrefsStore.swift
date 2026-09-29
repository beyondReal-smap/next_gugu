import Foundation
import Observation

// 환경설정 — 효과음·진동·문제 읽어주기 (웹 PrefsProvider 대응).
// 실제 동작은 각 서비스(Sound/Haptics/Speech)가 하고, 이 스토어는 설정 화면이 관찰할 상태와 저장을 맡는다.

@Observable
final class PrefsStore {
    static let readAloudKey = "gugu.readAloud"

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

    init() {
        soundOn = Sound.shared.enabled
        hapticsOn = Haptics.enabled
        readAloud = Persistence.string(Self.readAloudKey) == "1"
    }
}
