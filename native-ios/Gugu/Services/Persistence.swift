import Foundation

// 영속화 — 웹 localStorage(JSON) → UserDefaults(JSON) 이식.
// 저장 키는 웹과 동일 명칭 유지(향후 서버 동기화/디버깅 일관성).

enum Persistence {
    static let progressKey = "gugu.progress.v1"
    /// 3D 어드벤처(2.9.0 에서 제거) 저장본 — PlayRewardsStore 가 별 조각 이관 원본으로 읽는다. 키·값 모두 지우지 않는다
    static let adventureKey = "gugu.adventure.v1"
    static let themeKey = "gugu.theme"
    static let premiumKey = "gugu.premium.v1"
    /// 서버(구매 원장)가 부여한 프리미엄 — 스토어 구매와 따로 둬야 스토어 재확인이 이 값을 지우지 않는다
    static let serverPremiumKey = "gugu.premium.server.v1"
    /// 구구 계단·구구 몬스터 보상 — 모은 별·옷장·도감·최고 기록 (PlayRewardsStore)
    static let playRewardsKey = "gugu.play.v1"
    /// 구구 계단·구구 몬스터 문제 순서 선택 — 게임마다 마지막 [순서대로]·[무작위] (GameOrderStore)
    static let gameOrderKey = "gugu.gameOrder.v1"
    /// 서버 동기화 — 학습 신원 / 오프라인 아웃박스
    static let learningIdentityKey = "gugu.learning.identity.v1"
    static let learningOutboxKey = "gugu.learning.outbox.v1"
    /// 학습 알림 설정 (켜짐·시각·권유 여부)
    static let reminderKey = "gugu.reminder.v1"
    /// 원격 푸시 설정 (공지·보호자 소식·광고 동의). 설치 식별자·등록 상태는 키체인(PushStore)에 둔다
    static let pushKey = "gugu.push.v1"

    private static let defaults = UserDefaults.standard

    /// 저장본을 읽는다. 키가 없으면 nil(신규 설치). 있는데 못 읽으면 원본을 격리한 뒤 nil —
    /// 호출부가 `?? 기본값`으로 시작해 곧바로 저장하면 원본이 덮어써지기 때문이다.
    static func load<T: Decodable>(_ type: T.Type, key: String) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        do {
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            print("[Persistence] 로드 실패(\(key)):", error)
            quarantine(data, key: key)
            return nil
        }
    }

    static func save<T: Encodable>(_ value: T, key: String) {
        do {
            let data = try JSONEncoder().encode(value)
            defaults.set(data, forKey: key)
        } catch {
            print("[Persistence] 저장 실패(\(key)):", error)
        }
    }

    static func string(_ key: String) -> String? { defaults.string(forKey: key) }
    static func setString(_ value: String?, key: String) { defaults.set(value, forKey: key) }
    /// 의도적 삭제 — 우리가 만든 손상 저장본 사본도 함께 지운다 (계정 삭제 뒤에 사본이 남지 않게)
    static func remove(_ key: String) {
        defaults.removeObject(forKey: key)
        clearQuarantine(key: key)
    }

    // MARK: - 손상 저장본 격리
    //
    // 저장본을 못 읽었을 때 호출부는 `?? 기본값`으로 시작하고 곧바로 저장한다 — 원본이 그대로 덮어써진다.
    // 서버에는 진행도가 없어 되살릴 곳이 없으므로, 덮어쓰기 전에 원본 바이트를 파일로 남겨 둔다.
    // 사용자는 이 파일에 접근할 수 없다 — 다음 빌드가 읽어 들여 복구하는 경로를 위한 보존이다.

    /// 격리 폴더 — Application Support 아래 (사용자에게 보이지 않는다)
    static var quarantineDirectory: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("quarantine", isDirectory: true)
    }

    /// 키마다 남기는 격리본 상한 — 실패가 반복돼도 쌓이지 않게 하고, 원본에 가장 가까운 앞선 것을 보존한다
    static let maxQuarantinePerKey = 3

    private static func quarantine(_ data: Data, key: String) {
        do {
            var dir = quarantineDirectory
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            // 손상 원문(학습 기록·식별자)이 iCloud/iTunes 백업으로 새 기기에 복원되지 않게 한다 (Android noBackupFilesDir 와 동일).
            // 이건 프라이버시 강화일 뿐이라 실패해도 원본 보존(아래 쓰기)을 막지 않는다 — 로그만 남기고 계속한다
            do {
                var values = URLResourceValues()
                values.isExcludedFromBackup = true
                try dir.setResourceValues(values)
            } catch {
                print("[Persistence] 격리 폴더 백업 제외 설정 실패(원본 보존은 계속):", error)
            }
            guard quarantinedFiles(key: key).count < maxQuarantinePerKey else {
                print("[Persistence] 격리 상한(\(maxQuarantinePerKey)) 도달 — 새 격리본은 남기지 않음(\(key))")
                return
            }
            let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "0"
            let name = "\(key)-b\(build)-\(Int(Date().timeIntervalSince1970))-\(UUID().uuidString.prefix(8)).json"
            try data.write(to: dir.appendingPathComponent(name), options: .atomic)
            print("[Persistence] 손상 저장본 격리: \(name) (\(data.count)B)")
        } catch {
            // 격리가 실패해도 앱은 계속 진행한다 — 디스크가 가득 찬 채 매번 죽는 상황을 만들지 않는다
            print("[Persistence] 격리 실패(\(key)):", error)
        }
    }

    /// 이 키의 격리본을 모두 지운다 — 기록 초기화·계정 삭제 뒤에 사본이 남지 않게
    static func clearQuarantine(key: String) {
        for file in quarantinedFiles(key: key) {
            do {
                try FileManager.default.removeItem(at: file)
            } catch {
                print("[Persistence] 격리본 삭제 실패(\(file.lastPathComponent)):", error)
            }
        }
    }

    static func quarantinedFiles(key: String) -> [URL] {
        let dir = quarantineDirectory
        guard FileManager.default.fileExists(atPath: dir.path) else { return [] }
        do {
            return try FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)
                .filter { $0.lastPathComponent.hasPrefix(key + "-") }
        } catch {
            print("[Persistence] 격리 폴더 조회 실패:", error)
            return []
        }
    }

    /// 격리본 보존 기한(30일) — 손상 원문에는 학습 기록·식별자가 들어 있으므로 복구 핫픽스에 필요한 기간만 둔다
    static let quarantineRetention: TimeInterval = 30 * 24 * 60 * 60

    /// 보존 기한이 지난 격리본을 지운다 (키와 무관하게 폴더 전체) — 앱 시작 때 한 번 부른다.
    /// 격리 파일은 한 번 쓰고 고치지 않으므로 수정 시각이 곧 격리한 시각이다.
    /// 시각을 알 수 없는 파일은 지우지 않고 남긴다 — 모르면 보존이 안전하다. 폴더가 없으면(대부분의 실행) 아무 일도 하지 않는다.
    /// 지운 개수를 돌려준다.
    @discardableResult
    static func purgeExpiredQuarantine(now: Date = Date(), maxAge: TimeInterval = quarantineRetention) -> Int {
        let dir = quarantineDirectory
        guard FileManager.default.fileExists(atPath: dir.path) else { return 0 }
        do {
            let files = try FileManager.default.contentsOfDirectory(
                at: dir, includingPropertiesForKeys: [.contentModificationDateKey])
            var removed = 0
            for file in files {
                let modified = try file.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate
                guard let modified, now.timeIntervalSince(modified) > maxAge else { continue }
                do {
                    try FileManager.default.removeItem(at: file)
                    removed += 1
                } catch {
                    print("[Persistence] 만료 격리본 삭제 실패(\(file.lastPathComponent)):", error)
                }
            }
            if removed > 0 {
                print("[Persistence] 보존 기한(\(Int(maxAge / 86_400))일)이 지난 격리본 \(removed)개 삭제")
            }
            return removed
        } catch {
            print("[Persistence] 격리 폴더 정리 실패:", error)
            return 0
        }
    }
}
