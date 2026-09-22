import Foundation

// 영속화 — 웹 localStorage(JSON) → UserDefaults(JSON) 이식.
// 저장 키는 웹과 동일 명칭 유지(향후 서버 동기화/디버깅 일관성).

enum Persistence {
    static let progressKey = "gugu.progress.v1"
    static let adventureKey = "gugu.adventure.v1"
    static let themeKey = "gugu.theme"
    static let premiumKey = "gugu.premium.v1"
    /// 구구 점프 단별 최고 기록 (웹 localStorage 키와 동일)
    static let runnerBestKey = "gugu.runner.best.v1"
    static let laneBestKey = "gugu.lane.best.v1"
    static let basketBestKey = "gugu.basket.best.v1"
    /// 서버 동기화 — 학습 신원 / 오프라인 아웃박스
    static let learningIdentityKey = "gugu.learning.identity.v1"
    static let learningOutboxKey = "gugu.learning.outbox.v1"

    private static let defaults = UserDefaults.standard

    static func load<T: Decodable>(_ type: T.Type, key: String) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        do {
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            print("[Persistence] 로드 실패(\(key)):", error)
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
    static func remove(_ key: String) { defaults.removeObject(forKey: key) }
}
