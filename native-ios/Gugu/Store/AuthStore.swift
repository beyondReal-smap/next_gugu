import Foundation
import Observation

// 익명 계정 세션 관리 (웹 AuthProvider 의 네이티브 대응, 1단계 — 익명만).
//
// 첫 실행에 조용히 익명 계정을 만들고 세션을 키체인에 보관한다. 화면이나 가입 절차가 없고,
// 실패해도 앱은 게스트로 그대로 동작한다 (학습 기능은 계정과 무관).
// 나중 단계에서 이 계정을 이메일로 승격하면 user id 가 유지돼 기록이 이어진다.

@MainActor
@Observable
final class AuthStore {
    enum State: Equatable {
        case idle           // 아직 시도 전
        case disabled       // 빌드에 Supabase 설정 없음
        case working        // 발급/갱신 중
        case ready          // 사용 가능한 세션 보유
        case failed(String) // 실패 — 다음 실행 때 다시 시도
    }

    private(set) var state: State = .idle
    private(set) var session: AuthSession?

    private static let sessionKey = "gugu.auth.session.v1"
    /// 갱신이 겹치지 않도록 진행 중인 작업을 공유한다
    private var inflight: Task<AuthSession, Error>?

    var userId: String? { session?.userId }
    var isAnonymous: Bool { session?.isAnonymous ?? false }
    /// 이메일이 붙은 영구 계정인지
    var isPermanent: Bool { session != nil && !isAnonymous }

    // MARK: - 이메일 승격 (익명 -> 영구 계정)

    enum Promotion: Equatable {
        case none
        case sending
        /// 확인 코드를 보낸 주소 — 이 화면에서 코드를 입력받는다
        case codeSent(email: String)
        case verifying(email: String)
        case done(email: String)
        case failed(String)
    }

    private(set) var promotion: Promotion = .none

    /// 이메일로 확인 코드를 보낸다. 성공해도 아직 승격은 끝나지 않는다.
    func startEmailPromotion(email: String) async {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard trimmed.contains("@"), trimmed.count >= 5 else {
            promotion = .failed("이메일 주소를 확인해 주세요")
            return
        }
        guard let token = await accessToken() else {
            promotion = .failed("계정 세션이 없어 연결할 수 없습니다")
            return
        }
        promotion = .sending
        do {
            try await SupabaseAuth.requestEmailPromotion(accessToken: token, email: trimmed)
            promotion = .codeSent(email: trimmed)
        } catch let error as SupabaseAuthError {
            promotion = .failed(Self.promotionMessage(for: error))
        } catch {
            promotion = .failed(error.localizedDescription)
        }
    }

    /// 메일로 받은 코드로 승격을 끝낸다
    func confirmEmailPromotion(code: String) async {
        guard case let .codeSent(email) = promotion else { return }
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            promotion = .failed("확인 코드를 입력해 주세요")
            return
        }
        promotion = .verifying(email: email)
        do {
            let fresh = try await SupabaseAuth.verifyEmailChange(email: email, token: trimmed)
            session = fresh
            store(fresh)
            state = .ready
            promotion = .done(email: email)
        } catch let error as SupabaseAuthError {
            promotion = .failed(Self.promotionMessage(for: error))
        } catch {
            promotion = .failed(error.localizedDescription)
        }
    }

    func resetPromotion() { promotion = .none }

    /// 서버 오류 코드를 사용자 문구로 — 발송 제한이 가장 흔하다
    private static func promotionMessage(for error: SupabaseAuthError) -> String {
        guard case let .http(_, code, message) = error else { return error.localizedDescription }
        switch code {
        case "over_email_send_rate_limit":
            return "확인 메일을 방금 보냈어요. 잠시 뒤 다시 시도해 주세요."
        case "email_address_invalid":
            return "이 이메일 주소는 쓸 수 없어요. 다른 주소를 넣어 주세요."
        case "email_exists", "user_already_exists":
            return "이미 연결된 이메일이에요."
        case "otp_expired":
            return "확인 코드가 만료됐어요. 다시 보내 주세요."
        default:
            return message.isEmpty ? error.localizedDescription : message
        }
    }

    init() {
        session = Self.loadStored()
    }

    /// 앱 시작 시 1회 호출. 저장된 세션이 있으면 필요할 때만 갱신하고, 없으면 익명 계정을 만든다.
    func start() async {
        guard SupabaseConfig.isConfigured else {
            state = .disabled
            return
        }
        if let session, !session.isExpired {
            state = .ready
            return
        }
        do {
            _ = try await ensureSession()
        } catch {
            // 인증은 학습 기능의 전제가 아니다 — 기록만 남기고 앱은 그대로 진행한다
            state = .failed(error.localizedDescription)
            print("[AuthStore] 익명 세션 준비 실패:", error.localizedDescription)
        }
    }

    /// 서버 호출에 붙일 액세스 토큰. 만료됐으면 갱신한 뒤 돌려준다.
    func accessToken() async -> String? {
        guard SupabaseConfig.isConfigured else { return nil }
        if let session, !session.isExpired { return session.accessToken }
        return try? await ensureSession().accessToken
    }

    /// 토큰을 강제로 새로 받는다.
    /// app_metadata(보호자 권한)는 JWT 에 박혀 나가므로, 서버가 값을 바꾼 뒤에는
    /// 갱신하지 않으면 계속 예전 권한으로 거부된다.
    @discardableResult
    func forceRefresh() async -> Bool {
        guard SupabaseConfig.isConfigured, let current = session else { return false }
        do {
            let fresh = try await SupabaseAuth.refresh(refreshToken: current.refreshToken)
            session = fresh
            store(fresh)
            state = .ready
            return true
        } catch {
            print("[AuthStore] 토큰 강제 갱신 실패:", error.localizedDescription)
            return false
        }
    }

    /// 계정과 서버에 보관된 학습 기록을 삭제한다 (App Store 5.1.1(v)).
    ///
    /// 성공하면 기기 세션을 버리고 새 익명 계정으로 다시 시작해, 사용자가 앱을
    /// 그대로 계속 쓸 수 있게 한다. 기기에 남은 귀속·큐 정리는 SyncStore 가 맡는다.
    func deleteAccount() async -> Bool {
        guard let token = await accessToken() else { return false }
        do {
            try await LearningApi.deleteAccount(accessToken: token)
        } catch {
            print("[Auth] 계정 삭제 실패:", error.localizedDescription)
            return false
        }
        signOutLocally()
        promotion = .none
        await start()
        return true
    }

    /// 기기에서 계정 연결을 끊는다 (서버 계정은 남는다 — 삭제는 DELETE /api/account 담당)
    func signOutLocally() {
        inflight?.cancel()
        inflight = nil
        session = nil
        Keychain.remove(key: Self.sessionKey)
        state = SupabaseConfig.isConfigured ? .idle : .disabled
    }

    // MARK: - 내부

    /// 유효한 세션을 보장한다. 동시에 여러 번 불려도 실제 호출은 한 번만 나간다.
    private func ensureSession() async throws -> AuthSession {
        if let inflight { return try await inflight.value }

        let task = Task<AuthSession, Error> { [session] in
            // 리프레시 토큰이 있으면 갱신을 먼저 시도하고, 거부당하면 새 익명 계정으로 넘어간다
            if let session {
                do {
                    return try await SupabaseAuth.refresh(refreshToken: session.refreshToken)
                } catch let error as SupabaseAuthError where error.needsFreshSignIn {
                    print("[AuthStore] 리프레시 거부 — 익명 계정을 새로 만든다:", error.localizedDescription)
                }
            }
            return try await SupabaseAuth.signInAnonymously()
        }
        inflight = task
        state = .working
        defer { inflight = nil }

        do {
            let fresh = try await task.value
            session = fresh
            store(fresh)
            state = .ready
            return fresh
        } catch {
            state = .failed(error.localizedDescription)
            throw error
        }
    }

    private func store(_ session: AuthSession) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        Keychain.save(data, key: Self.sessionKey)
    }

    private static func loadStored() -> AuthSession? {
        guard let data = Keychain.load(key: sessionKey) else { return nil }
        return try? JSONDecoder().decode(AuthSession.self, from: data)
    }
}
