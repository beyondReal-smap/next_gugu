import Foundation
import StoreKit

// 구매를 서버에 등록한다 (POST /api/iap/verify).
//
// 서버는 purchases 테이블에 (transaction_id, product_id, user_id, environment) 를 남기고,
// 보호자 권한(POST /api/account/guardian)이 이 기록을 근거로 삼는다.
// 계정 토큰 없이 부르면 게스트 구매로 기록되고 user_id 가 비므로, 반드시 토큰을 붙인다.
//
// 검증 근거는 StoreKit 2 의 `jwsRepresentation` 을 쓴다. Apple 이 직접 서명한 토큰이라
// 설치 경로와 무관하게 기기에 존재한다 — 개발 서명으로 직접 설치한 빌드에도 있다.
// 반면 레거시 `appStoreReceipt` 파일은 App Store·TestFlight 설치본에만 있어서,
// 직접 설치 빌드에서는 등록 자체가 불가능했다.
// 서버도 Apple 공식 app-store-server-library 로 JWS 를 검증한다(apple_jws.py).
// receipt 경로는 심사 중인 2.1.0 Capacitor 빌드 호환을 위해 폴백으로만 남긴다.

enum PurchaseRegistry {
    struct VerifyResponse: Decodable {
        var ok: Bool
        var premium: Bool
        var transactionId: String?
    }

    enum RegistryError: LocalizedError {
        case noPurchase
        /// 서버는 검증 실패에도 ok=true 를 주고 premium 으로 결과를 알린다
        case notVerified
        case http(status: Int, message: String)
        case malformedResponse

        var errorDescription: String? {
            switch self {
            case .noPurchase:
                return "기기에서 확인된 구매를 찾지 못했습니다"
            case .notVerified:
                return "스토어가 이 구매를 확인하지 못했습니다"
            case let .http(status, message):
                return "구매 등록 응답 \(status): \(message)"
            case .malformedResponse:
                return "구매 등록 응답을 해석하지 못했습니다"
            }
        }
    }

    /// 보유 중인 상품의 Apple 서명 트랜잭션. 설치 경로와 무관하게 존재한다.
    static func signedTransaction() async -> String? {
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result,
                  transaction.productID == PremiumConfig.productID,
                  transaction.revocationDate == nil else { continue }
            return result.jwsRepresentation
        }
        return nil
    }

    /// 앱 번들의 영수증 파일 — App Store·TestFlight 설치본에만 있다 (레거시 폴백)
    static func localReceipt() -> String? {
        guard let url = Bundle.main.appStoreReceiptURL,
              let data = try? Data(contentsOf: url), !data.isEmpty else { return nil }
        return data.base64EncodedString()
    }

    /// 구매를 계정에 연결한다. 실패해도 프리미엄 자체는 로컬 판정이라 영향이 없다.
    @discardableResult
    static func register(accessToken: String) async throws -> VerifyResponse {
        var body: [String: String] = [
            "platform": "ios",
            "productId": PremiumConfig.productID,
        ]
        if let jws = await signedTransaction() {
            body["jws"] = jws
        } else if let receipt = localReceipt() {
            // 서명 트랜잭션이 없는 구형 구매 — 레거시 영수증으로 시도한다
            body["receipt"] = receipt
        } else {
            throw RegistryError.noPurchase
        }

        guard let url = URL(string: LearningApi.base + "/iap/verify") else {
            throw RegistryError.malformedResponse
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 30
        // Cloudflare 봇 차단 회피 — 기본 UA 는 403(error 1010)을 받는다
        request.setValue(LearningApi.userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw RegistryError.malformedResponse }
        guard (200..<300).contains(http.statusCode) else {
            let detail = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["detail"]
            throw RegistryError.http(
                status: http.statusCode,
                message: (detail as? String) ?? String(data: data, encoding: .utf8)?.prefix(200).description ?? ""
            )
        }
        guard let decoded = try? JSONDecoder().decode(VerifyResponse.self, from: data) else {
            throw RegistryError.malformedResponse
        }
        // 서버는 요청을 처리하기만 해도 ok=true 를 준다. 실제 검증 결과는 premium 이다.
        guard decoded.premium else { throw RegistryError.notVerified }
        return decoded
    }
}
