import XCTest
@testable import Gugu

// 이메일 로그인 흐름 검증 — 세션 파싱 / 리프레시 거부 판정 / 연결·로그인 분기 / 오류 문구 / 서버 기록 복원

final class AuthFlowTests: XCTestCase {

    private func tokenResponse(user: [String: Any]) throws -> Data {
        try JSONSerialization.data(withJSONObject: [
            "access_token": "a", "refresh_token": "r", "expires_at": 2_000_000_000.0, "user": user,
        ])
    }

    // MARK: - 세션 파싱

    /// 익명 사용자는 email 이 빈 문자열로 온다 — nil 로 본다
    func testParseAnonymousEmailIsNil() throws {
        let session = try SupabaseAuth.parse(tokenResponse(user: ["id": "u1", "is_anonymous": true, "email": ""]))
        XCTAssertNil(session.email)
        XCTAssertTrue(session.isAnonymous)
    }

    func testParsePermanentEmail() throws {
        let session = try SupabaseAuth.parse(tokenResponse(user: ["id": "u1", "is_anonymous": false, "email": "a@b.com"]))
        XCTAssertEqual(session.email, "a@b.com")
        XCTAssertFalse(session.isAnonymous)
    }

    func testParseMissingEmailKeyIsNil() throws {
        let session = try SupabaseAuth.parse(tokenResponse(user: ["id": "u1"]))
        XCTAssertNil(session.email)
    }

    /// email 필드가 없던 예전 키체인 저장본도 읽힌다
    func testLegacyStoredSessionDecodes() throws {
        let legacy = #"{"accessToken":"a","refreshToken":"r","expiresAt":1,"userId":"u1","isAnonymous":false}"#
        let session = try JSONDecoder().decode(AuthSession.self, from: Data(legacy.utf8))
        XCTAssertEqual(session.userId, "u1")
        XCTAssertNil(session.email)
    }

    // MARK: - 리프레시 거부

    func testRefreshRejectionReissuesAnonymous() {
        XCTAssertEqual(AuthStore.onRefreshRejected(isAnonymous: true), .reissueAnonymous)
    }

    /// 이메일 계정은 익명으로 바꾸지 않고 재로그인을 받는다
    func testRefreshRejectionRequiresSignInForEmailAccount() {
        XCTAssertEqual(AuthStore.onRefreshRejected(isAnonymous: false), .requireSignIn)
    }

    /// 네트워크 오류 등은 거부가 아니다 — 세션을 버리지 않는다
    func testOnlyAuthRejectionNeedsFreshSignIn() {
        XCTAssertTrue(SupabaseAuthError.http(status: 400, code: "refresh_token_not_found", message: "").needsFreshSignIn)
        XCTAssertFalse(SupabaseAuthError.http(status: 500, code: nil, message: "").needsFreshSignIn)
        XCTAssertFalse(SupabaseAuthError.malformedResponse.needsFreshSignIn)
    }

    // MARK: - 연결·로그인 분기

    func testExistingAccountSwitchesToSignIn() {
        XCTAssertTrue(AuthStore.isExistingAccount(.http(status: 422, code: "email_exists", message: "")))
        XCTAssertTrue(AuthStore.isExistingAccount(.http(status: 422, code: "user_already_exists", message: "")))
    }

    func testOtherErrorsDoNotSwitch() {
        XCTAssertFalse(AuthStore.isExistingAccount(.http(status: 422, code: "email_address_invalid", message: "")))
        XCTAssertFalse(AuthStore.isExistingAccount(.http(status: 429, code: "over_email_send_rate_limit", message: "")))
        XCTAssertFalse(AuthStore.isExistingAccount(.malformedResponse))
    }

    // MARK: - 오류 문구

    func testPromotionMessages() {
        func message(_ code: String) -> String {
            AuthStore.promotionMessage(for: .http(status: 400, code: code, message: "raw"))
        }
        XCTAssertEqual(message("otp_expired"), "확인 코드가 맞지 않거나 만료됐어요. 다시 확인해 주세요.")
        XCTAssertEqual(message("otp_disabled"), "이 이메일로 만든 계정이 없어요. 주소를 확인해 주세요.")
        XCTAssertEqual(message("over_email_send_rate_limit"), "확인 메일을 방금 보냈어요. 잠시 뒤 다시 시도해 주세요.")
        XCTAssertEqual(message("over_request_rate_limit"), "요청이 많아요. 잠시 뒤 다시 시도해 주세요.")
        XCTAssertEqual(message("unknown_code"), "raw")
    }

    // MARK: - 서버 기록 복원

    private func stats(_ correct: Int, _ wrong: Int) -> LearningFactStats {
        LearningFactStats(attempts: correct + wrong, correct: correct, wrong: wrong)
    }

    func testWeightMimicsLocalRule() {
        XCTAssertEqual(LearningRestore.weight(for: stats(0, 1)), 2)
        XCTAssertEqual(LearningRestore.weight(for: stats(1, 1)), 1)
        XCTAssertEqual(LearningRestore.weight(for: stats(5, 1)), 0)
        XCTAssertEqual(LearningRestore.weight(for: stats(0, 10)), 6)
    }

    func testMergeKeepsMaxPerFact() {
        let merged = LearningRestore.merge(
            local: ["7x8": 4, "3x4": 1],
            server: ["7x8": stats(0, 1), "3x4": stats(0, 2), "6x6": stats(0, 3)]
        )
        XCTAssertEqual(merged, ["7x8": 4, "3x4": 4, "6x6": 6])
    }

    func testMergeSkipsInvalidKeysAndMasteredFacts() {
        let merged = LearningRestore.merge(
            local: ["2x3": 2],
            server: ["bad": stats(0, 5), "99x99": stats(0, 5), "2x5": stats(9, 0)]
        )
        XCTAssertEqual(merged, ["2x3": 2])
    }

    func testMergeIsIdempotent() {
        let server = ["7x8": stats(1, 3), "4x6": stats(0, 1)]
        let once = LearningRestore.merge(local: ["9x9": 2], server: server)
        XCTAssertEqual(LearningRestore.merge(local: once, server: server), once)
    }

    func testEmptyServerKeepsLocal() {
        XCTAssertEqual(LearningRestore.merge(local: ["7x8": 3], server: [:]), ["7x8": 3])
    }

    /// 2026-10-16 운영 DB 에서 조회한 실제 /learning/state 스냅샷 형태 그대로
    func testDecodesRealSnapshotState() throws {
        let json = #"""
        {"snapshots":[{"learnerId":"7987cd43-84be-423e-a93f-a28d3e15aad9","revision":3,"serverCursor":12,
        "updatedAt":"2026-10-11T02:23:19+00:00",
        "state":{"facts":{"2x8":{"wrong":0,"correct":1,"accuracy":1.0000,"attempts":1,
        "lastAnsweredAt":"2026-10-11T02:23:18+00:00","averageResponseMs":1719}},
        "accuracy":1.0,"learnerId":"7987cd43-84be-423e-a93f-a28d3e15aad9","totalWrong":0,
        "lastEventAt":"2026-10-11T02:23:18+00:00","totalAnswers":2,"totalCorrect":2}}],
        "serverCursor":12}
        """#
        let response = try JSONDecoder().decode(LearningStateResponse.self, from: Data(json.utf8))
        XCTAssertEqual(response.snapshots.first?.state.facts["2x8"], LearningFactStats(attempts: 1, correct: 1, wrong: 0))
    }

    /// facts 가 빠지면 계약이 바뀐 것 — 빈 값으로 넘기지 않고 실패한다
    func testSnapshotWithoutFactsFails() {
        let json = #"{"snapshots":[{"learnerId":"x","revision":1,"serverCursor":1,"updatedAt":"t","state":{}}],"serverCursor":1}"#
        XCTAssertThrowsError(try JSONDecoder().decode(LearningStateResponse.self, from: Data(json.utf8)))
    }

    // MARK: - 학습 신원

    /// restorePending 이 없던 예전 저장본은 false — 이미 쓰던 기기에서 복원이 다시 돌지 않는다
    func testLegacyIdentityDecodesRestorePendingFalse() throws {
        let legacy = #"{"version":1,"installId":"i","learnerId":"l","claimedUserId":"u"}"#
        let identity = try JSONDecoder().decode(LearningIdentity.self, from: Data(legacy.utf8))
        XCTAssertFalse(identity.restorePending)
        XCTAssertEqual(identity.learnerId, "l")
    }

    func testIdentityRoundTripKeepsRestorePending() throws {
        var identity = LearningIdentity.fresh(installId: "i")
        identity.restorePending = true
        let decoded = try JSONDecoder().decode(LearningIdentity.self, from: JSONEncoder().encode(identity))
        XCTAssertEqual(decoded, identity)
    }

    /// 계정이 바뀌면 예전 계정의 귀속·큐를 버리고 새 installId 를 받는다
    @MainActor
    func testResetForAccountChangeClearsIdentityAndOutbox() throws {
        let before = LearningIdentityStore.read()
        try LearningIdentityStore.bindLearner(userId: "old-user", learnerId: UUID().uuidString.lowercased())
        let sync = SyncStore()
        sync.resetForAccountChange()
        let after = LearningIdentityStore.read()
        XCTAssertNil(after.learnerId)
        XCTAssertNil(after.claimedUserId)
        XCTAssertFalse(after.restorePending)
        XCTAssertNotEqual(after.installId, before.installId)
        XCTAssertEqual(sync.pendingCount, 0)
        XCTAssertEqual(LearningOutboxStore.read().events.count, 0)
    }
}
