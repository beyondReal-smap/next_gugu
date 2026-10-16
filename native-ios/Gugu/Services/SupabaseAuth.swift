import Foundation

// Supabase GoTrue REST 호출 (익명 로그인 / 토큰 갱신 / 이메일 연결·로그인 / 세션 해제).
// SDK 를 붙이지 않고 필요한 엔드포인트만 직접 호출한다 — 의존성 0.

struct AuthSession: Codable, Equatable {
    var accessToken: String
    var refreshToken: String
    /// 만료 시각 (epoch 초)
    var expiresAt: Double
    var userId: String
    var isAnonymous: Bool
    /// 계정에 붙은 이메일 (익명이면 nil). 이 필드가 없던 예전 저장본도 그대로 읽힌다.
    var email: String?

    /// 만료 60초 전부터는 갱신 대상으로 본다 (시계 오차·요청 지연 여유)
    var isExpired: Bool { Date().timeIntervalSince1970 >= expiresAt - 60 }
}

enum SupabaseAuthError: LocalizedError {
    case notConfigured
    case http(status: Int, code: String?, message: String)
    case malformedResponse

    var errorDescription: String? {
        switch self {
        case .notConfigured:
            return "Supabase 설정이 빌드에 없습니다"
        case let .http(status, code, message):
            return "Supabase 응답 \(status)\(code.map { " (\($0))" } ?? ""): \(message)"
        case .malformedResponse:
            return "Supabase 응답을 해석하지 못했습니다"
        }
    }

    /// 재로그인이 필요한 상태 — 리프레시 토큰 만료/폐기
    var needsFreshSignIn: Bool {
        if case let .http(status, _, _) = self { return status == 400 || status == 401 }
        return false
    }
}

enum SupabaseAuth {
    /// 익명 계정 생성 + 세션 발급 (대시보드에서 Anonymous sign-ins 활성화 필요)
    static func signInAnonymously() async throws -> AuthSession {
        try await post(path: "/auth/v1/signup", body: [:])
    }

    /// 리프레시 토큰으로 세션 갱신
    static func refresh(refreshToken: String) async throws -> AuthSession {
        try await post(path: "/auth/v1/token?grant_type=refresh_token",
                       body: ["refresh_token": refreshToken])
    }

    /// 익명 계정에 이메일을 붙인다 — 확인 코드가 그 주소로 발송된다.
    /// 이 호출만으로는 승격되지 않고, verifyEmailChange 까지 끝나야 영구 계정이 된다.
    static func requestEmailPromotion(accessToken: String, email: String) async throws {
        _ = try await request(
            path: "/auth/v1/user", method: "PUT",
            body: ["email": email], accessToken: accessToken
        )
    }

    /// 메일로 받은 6자리 코드로 이메일 확인을 끝내고 새 세션을 받는다
    static func verifyEmailChange(email: String, token: String) async throws -> AuthSession {
        let data = try await request(
            path: "/auth/v1/verify", method: "POST",
            body: ["type": "email_change", "email": email, "token": token], accessToken: nil
        )
        return try parse(data)
    }

    /// 이미 있는 이메일 계정으로 로그인 코드를 보낸다 (Magic link or OTP 메일 템플릿).
    /// create_user=false — 없는 주소면 계정을 만들지 않고 422(otp_disabled)로 거부된다.
    static func requestLoginCode(email: String) async throws {
        _ = try await request(
            path: "/auth/v1/otp", method: "POST",
            body: ["email": email, "create_user": false], accessToken: nil
        )
    }

    /// 메일로 받은 6자리 코드로 그 계정에 로그인한다
    static func verifyLoginCode(email: String, token: String) async throws -> AuthSession {
        let data = try await request(
            path: "/auth/v1/verify", method: "POST",
            body: ["type": "email", "email": email, "token": token], accessToken: nil
        )
        return try parse(data)
    }

    /// 이 기기의 세션만 서버에서 폐기한다 (다른 기기의 로그인은 그대로)
    static func signOut(accessToken: String) async throws {
        _ = try await request(
            path: "/auth/v1/logout?scope=local", method: "POST", body: [:], accessToken: accessToken
        )
    }

    // MARK: - 내부

    private static func post(path: String, body: [String: Any]) async throws -> AuthSession {
        try parse(try await request(path: path, method: "POST", body: body, accessToken: nil))
    }

    /// GoTrue 공통 호출 — apikey 는 항상, Authorization 은 사용자 토큰이 있을 때만 붙인다
    private static func request(
        path: String, method: String, body: [String: Any], accessToken: String?
    ) async throws -> Data {
        guard let base = SupabaseConfig.url, let anonKey = SupabaseConfig.anonKey else {
            throw SupabaseAuthError.notConfigured
        }
        guard let url = URL(string: base.absoluteString + path) else {
            throw SupabaseAuthError.notConfigured
        }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 20
        request.setValue(anonKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        if let accessToken { request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization") }

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw SupabaseAuthError.malformedResponse }

        guard (200..<300).contains(http.statusCode) else {
            let payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
            throw SupabaseAuthError.http(
                status: http.statusCode,
                code: payload?["error_code"] as? String,
                message: (payload?["msg"] as? String)
                    ?? (payload?["error_description"] as? String)
                    ?? String(data: data, encoding: .utf8)?.prefix(200).description
                    ?? ""
            )
        }
        return data
    }

    /// 토큰 응답을 세션으로 바꾼다 (테스트에서 직접 부른다)
    static func parse(_ data: Data) throws -> AuthSession {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let accessToken = json["access_token"] as? String,
              let refreshToken = json["refresh_token"] as? String,
              let user = json["user"] as? [String: Any],
              let userId = user["id"] as? String
        else { throw SupabaseAuthError.malformedResponse }

        // expires_at(절대시각)을 주면 그대로 쓰고, 없으면 expires_in(상대초)으로 계산한다
        let expiresAt = (json["expires_at"] as? Double)
            ?? Date().timeIntervalSince1970 + ((json["expires_in"] as? Double) ?? 3600)

        return AuthSession(
            accessToken: accessToken,
            refreshToken: refreshToken,
            expiresAt: expiresAt,
            userId: userId,
            isAnonymous: (user["is_anonymous"] as? Bool) ?? true,
            // 익명 사용자는 email 을 빈 문자열로 준다
            email: (user["email"] as? String).flatMap { $0.isEmpty ? nil : $0 }
        )
    }
}
