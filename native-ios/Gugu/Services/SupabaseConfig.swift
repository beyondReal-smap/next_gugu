import Foundation

// Supabase 접속 설정 — Config.xcconfig → Info.plist 로 주입된 값을 읽는다.
// 값이 비어 있으면 인증 기능 전체를 끈다(앱은 게스트로 그대로 동작).

enum SupabaseConfig {
    /// xcconfig 가 // 를 주석으로 잘라내므로 호스트만 주입받고 스킴은 여기서 붙인다
    static let url: URL? = {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "SupabaseHost") as? String else { return nil }
        let host = raw.trimmingCharacters(in: .whitespaces)
        guard !host.isEmpty, !host.hasPrefix("<") else { return nil }
        return URL(string: "https://" + host)
    }()

    static let anonKey: String? = {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "SupabaseAnonKey") as? String,
              !raw.isEmpty, !raw.hasPrefix("<") else { return nil }
        return raw.trimmingCharacters(in: .whitespaces)
    }()

    /// 빌드에 Supabase 값이 들어 있는지 — false 면 인증을 시도하지 않는다
    static var isConfigured: Bool { url != nil && anonKey != nil }
}
