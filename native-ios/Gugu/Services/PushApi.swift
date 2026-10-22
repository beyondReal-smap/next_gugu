import Foundation

// 푸시 기기 등록·광고 동의 API (서버 api/push.py, /api/v1/push).
// 요청·오류 해석은 LearningApi.send 를 그대로 쓴다 (UA·Bearer·FastAPI 오류 형식이 같다).
// 서버 요청 모델은 extra="forbid" 라 여기 필드 이름이 서버와 글자 그대로 같아야 한다.

enum PushApi {
    /// POST /v1/push/devices 본문
    struct RegisterBody: Encodable, Equatable {
        var pushInstallId: String
        var fcmToken: String
        var platform: String
        var appVersion: String
        var locale: String?
        var timeZone: String?
        var serviceOptIn: Bool
        var learningOptIn: Bool
        var localReminderEnabled: Bool
    }

    /// 서버 기준 기기 상태 — 광고 동의는 이 값을 따른다 (계정이 바뀌면 서버가 끈다)
    struct DeviceState: Equatable {
        var marketingOptIn: Bool
        var marketingOptInAt: Date?
    }

    /// 광고 동의 기록 결과 — 처리 결과 알림창의 날짜는 recordedAt 을 쓴다
    struct ConsentResult: Equatable {
        var granted: Bool
        var recordedAt: Date
        var sender: String
    }

    /// 서버 날짜는 문자열로 받고 직접 해석한다 (LearningApi.send 는 기본 JSONDecoder 를 쓴다)
    private struct DeviceStateWire: Decodable {
        var marketingOptIn: Bool
        var marketingOptInAt: String?
    }

    private struct ConsentWire: Decodable {
        var kind: String
        var granted: Bool
        var recordedAt: String
        var sender: String
    }

    private struct ConsentBody: Encodable {
        var pushInstallId: String
        var kind: String
        var granted: Bool
        var source: String
        var appVersion: String
    }

    private struct UnregisterWire: Decodable { var deleted: Bool }

    // MARK: - 엔드포인트

    static func register(accessToken: String, body: RegisterBody) async throws -> DeviceState {
        let data = try JSONEncoder().encode(body)
        let wire: DeviceStateWire = try await LearningApi.send(
            path: "/v1/push/devices", method: "POST", accessToken: accessToken, body: data)
        return DeviceState(
            marketingOptIn: wire.marketingOptIn,
            marketingOptInAt: try wire.marketingOptInAt.map(parseServerDate))
    }

    /// 광고 동의·거부 기록. 이 기기 행이 없으면 404 (먼저 등록해야 한다)
    static func setMarketingConsent(accessToken: String, pushInstallId: String, granted: Bool) async throws -> ConsentResult {
        let data = try JSONEncoder().encode(ConsentBody(
            pushInstallId: pushInstallId, kind: "marketing", granted: granted,
            source: "settings", appVersion: appVersion))
        let wire: ConsentWire = try await LearningApi.send(
            path: "/v1/push/devices/consents", method: "POST", accessToken: accessToken, body: data)
        return ConsentResult(granted: wire.granted, recordedAt: try parseServerDate(wire.recordedAt), sender: wire.sender)
    }

    /// 기기 등록 해제 — 이미 없어도 성공(멱등). 남의 기기면 404
    static func unregister(accessToken: String, pushInstallId: String, timeout: TimeInterval = 30) async throws {
        let _: UnregisterWire = try await LearningApi.send(
            path: "/v1/push/devices/\(pushInstallId)", method: "DELETE",
            accessToken: accessToken, body: nil, timeout: timeout)
    }

    // MARK: - 값 만들기

    /// 서버 계약 예시 형식 "2.5.0 (27)"
    static var appVersion: String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "0"
        return "\(version) (\(build))"
    }

    /// 앱 언어·기기 지역 "ko-KR" / "en-US" — 서버가 알림 문구 언어를 고르는 값이라 기기 언어가 아닌
    /// 앱 언어(AppLanguage)를 쓴다 (fr 기기는 앱이 영어이므로 "en-FR"). 서버 형식에 맞지 않으면 보내지 않는다(선택 필드)
    static func localeTag(language: AppLanguage = .current, locale: Locale = .current) -> String? {
        let tag = locale.region.map { "\(language.rawValue)-\($0.identifier)" } ?? language.rawValue
        return tag.range(of: #"^[A-Za-z]{2,3}(?:[-_][A-Za-z0-9]{2,8}){0,3}$"#, options: .regularExpression) != nil ? tag : nil
    }

    /// 기기 시간대 "Asia/Seoul" — 서버 형식에 맞지 않으면 보내지 않는다(선택 필드)
    static func timeZoneId(_ timeZone: TimeZone = .current) -> String? {
        let id = timeZone.identifier
        guard id.count <= 64,
              id.range(of: #"^[A-Za-z][A-Za-z0-9_+\-]*(?:/[A-Za-z0-9_+\-]+){0,2}$"#, options: .regularExpression) != nil
        else { return nil }
        return id
    }

    /// 서버(Pydantic) 날짜 — "2026-10-19T03:12:45.123456Z" / "+00:00" / 소수 초 없음 모두 받는다
    static func parseServerDate(_ raw: String) throws -> Date {
        let withFraction = ISO8601DateFormatter()
        withFraction.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = withFraction.date(from: raw) { return date }
        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        if let date = plain.date(from: raw) { return date }
        throw LearningApi.ApiError.malformedResponse
    }
}
