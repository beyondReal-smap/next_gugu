import Foundation

// 학습 원장 API 클라이언트 (lib/api/learning.ts 의 네트워크 부분 이식).
//
// 주의: gugu.smap.site 는 Cloudflare 뒤에 있고 기본 User-Agent 로 호출하면
// 봇 차단(HTTP 403, error 1010)에 걸린다. 앱을 식별하는 UA 를 반드시 붙인다.

enum LearningApi {
    static let base = "https://gugu.smap.site/api"

    /// Cloudflare 봇 차단을 피하기 위한 앱 식별 UA
    static var userAgent: String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "0"
        return "GuguAdventure/\(version).\(build) (iOS)"
    }

    /// 서버가 업로드를 거부하는 사유 — 보호자 검증이 끝나지 않은 계정
    enum PermissionDenial: String {
        case childAccount = "CHILD_ACCOUNT_UPLOAD_FORBIDDEN"
        case roleUnverified = "ACCOUNT_ROLE_UNVERIFIED"
        case ageUnverified = "ACCOUNT_AGE_UNVERIFIED"

        /// 사용자에게 보여 줄 설명
        var message: String {
            switch self {
            case .childAccount:
                return "아이 계정의 학습 기록은 서버에 올리지 않아요."
            case .roleUnverified:
                return "보호자 계정으로 확인되면 기록을 안전하게 보관해요."
            case .ageUnverified:
                return "보호자 성인 확인이 끝나면 기록 보관이 켜져요."
            }
        }
    }

    /// claim 요청 본문은 문자열과 수가 섞여 있어 값 종류를 감싼다 (서버는 extra="forbid")
    enum AnyClaimValue: Encodable {
        case string(String)
        case int(Int)

        func encode(to encoder: Encoder) throws {
            var container = encoder.singleValueContainer()
            switch self {
            case let .string(value): try container.encode(value)
            case let .int(value): try container.encode(value)
            }
        }
    }

    struct GuardianVerifyResponse: Decodable {
        var guardian: Bool
        var products: [String]
        var verifiedBy: String
        var verifiedAt: String
    }

    /// 보호자 권한 설정이 거부되는 사유
    enum GuardianDenial: String {
        case anonymous = "ANONYMOUS_NOT_ELIGIBLE"
        case noPurchase = "NO_PURCHASE_FOUND"

        var message: String {
            switch self {
            case .anonymous:
                return "이메일을 먼저 연결하면 기록 보관을 켤 수 있어요."
            case .noPurchase:
                return "이용권을 구매한 계정에서 기록 보관을 켤 수 있어요."
            }
        }
    }

    enum ApiError: LocalizedError {
        /// 보호자 검증 전 — 재시도해도 통과하지 않는다
        case permissionDenied(PermissionDenial)
        case http(status: Int, code: String?, message: String)
        case malformedResponse
        case offline(underlying: String)

        var errorDescription: String? {
            switch self {
            case let .permissionDenied(denial):
                return denial.message
            case let .http(status, code, message):
                return "학습 서버 응답 \(status)\(code.map { " (\($0))" } ?? ""): \(message)"
            case .malformedResponse:
                return "학습 서버 응답을 해석하지 못했습니다"
            case let .offline(underlying):
                return "학습 서버에 연결하지 못했습니다: \(underlying)"
            }
        }

        /// 지금 재시도해도 소용없는 상태인지 (권한 문제는 큐를 그대로 두고 멈춘다)
        var isPermanent: Bool {
            if case .permissionDenied = self { return true }
            if case let .http(status, _, _) = self { return status == 400 || status == 404 }
            return false
        }
    }

    // MARK: - 엔드포인트

    /// 이벤트 배치 업로드
    static func uploadEvents(accessToken: String, events: [LearningEvent]) async throws -> LearningEventBatchResponse {
        let body = try JSONEncoder().encode(["events": events])
        return try await send(path: "/v1/learning/events:batch", method: "POST",
                              accessToken: accessToken, body: body)
    }

    /// 보호자 권한 설정 — 앱 내 구매 이력을 근거로 서버가 app_metadata 를 세운다.
    /// 성공 후에는 토큰을 새로 받아야 새 권한이 JWT 에 담긴다.
    static func verifyGuardian(accessToken: String) async throws -> GuardianVerifyResponse {
        try await send(path: "/account/guardian", method: "POST", accessToken: accessToken, body: Data())
    }

    /// 기기 기록의 귀속을 요청한다 — 서버가 학습자를 만들거나 기존 학습자를 알려 준다.
    /// 보호자 권한이 있는 계정만 호출할 수 있다.
    static func claim(accessToken: String, installId: String, localEventCount: Int) async throws -> LearningClaimResponse {
        let body = try JSONEncoder().encode([
            "installId": AnyClaimValue.string(installId),
            "localEventCount": AnyClaimValue.int(localEventCount),
        ])
        return try await send(path: "/v1/learning/claim", method: "POST", accessToken: accessToken, body: body)
    }

    /// 후보 중 하나를 골라 귀속을 확정한다
    static func confirmClaim(accessToken: String, installId: String, learnerId: String) async throws -> LearningClaimConfirmResponse {
        let body = try JSONEncoder().encode(["installId": installId, "learnerId": learnerId])
        return try await send(path: "/v1/learning/claim/confirm", method: "POST", accessToken: accessToken, body: body)
    }

    /// 서버 학습 상태 조회
    static func fetchState(accessToken: String, learnerId: String? = nil) async throws -> LearningStateResponse {
        let query = learnerId.map { "?learnerId=\($0)" } ?? ""
        return try await send(path: "/v1/learning/state\(query)", method: "GET", accessToken: accessToken, body: nil)
    }

    /// 계정과 서버에 보관된 학습 기록을 삭제한다 (App Store 5.1.1(v)).
    /// 구매 영수증은 환불·감사 대응을 위해 서버에 남고 계정 연결만 해제된다.
    static func deleteAccount(accessToken: String) async throws {
        let _: OkResponse = try await send(
            path: "/account", method: "DELETE", accessToken: accessToken, body: nil
        )
    }

    private struct OkResponse: Decodable { var ok: Bool }

    // MARK: - 내부

    private static func send<T: Decodable>(
        path: String, method: String, accessToken: String, body: Data?
    ) async throws -> T {
        guard let url = URL(string: base + path) else { throw ApiError.malformedResponse }

        var request = URLRequest(url: url)
        request.httpMethod = method
        request.timeoutInterval = 30
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        if body != nil {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = body
        }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            // 네트워크 실패는 일시적 — 큐를 유지하고 다음 기회에 재시도한다
            throw ApiError.offline(underlying: error.localizedDescription)
        }
        guard let http = response as? HTTPURLResponse else { throw ApiError.malformedResponse }

        guard (200..<300).contains(http.statusCode) else {
            let detail = errorDetail(from: data)
            if http.statusCode == 403, let code = detail.code,
               let denial = PermissionDenial(rawValue: code) {
                throw ApiError.permissionDenied(denial)
            }
            throw ApiError.http(status: http.statusCode, code: detail.code, message: detail.message)
        }
        do {
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            throw ApiError.malformedResponse
        }
    }

    /// FastAPI 는 {"detail": {"code": ..., "message": ...}} 로 오류를 준다
    private static func errorDetail(from data: Data) -> (code: String?, message: String) {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return (nil, String(data: data, encoding: .utf8)?.prefix(200).description ?? "")
        }
        if let detail = json["detail"] as? [String: Any] {
            return (detail["code"] as? String, detail["message"] as? String ?? "")
        }
        if let detail = json["detail"] as? String {
            return (nil, detail)
        }
        return (nil, String(data: data, encoding: .utf8)?.prefix(200).description ?? "")
    }
}
