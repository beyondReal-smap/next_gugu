import Foundation
import Observation

// 계정 세션 관리 (웹 AuthProvider 의 네이티브 대응).
//
// 첫 실행에 조용히 익명 계정을 만들고 세션을 키체인에 보관한다. 가입 화면은 없고,
// 실패해도 앱은 게스트로 그대로 동작한다 (학습 기능은 계정과 무관).
// 보호자가 이메일을 넣으면 처음 쓰는 주소는 이 익명 계정을 승격하고(user id 유지),
// 이미 있는 주소는 그 계정으로 로그인한다 — 재설치·기기 변경 뒤에도 같은 계정으로 돌아온다.

@MainActor
@Observable
final class AuthStore {
    enum State: Equatable {
        case idle           // 아직 시도 전
        case disabled       // 빌드에 Supabase 설정 없음
        case working        // 발급/갱신 중
        case ready          // 사용 가능한 세션 보유
        case failed(String) // 실패 — 다음 실행 때 다시 시도
        /// 이메일 계정의 로그인이 만료됐다 — 익명 계정으로 바꾸지 않고 다시 로그인을 기다린다
        case needsReauth(email: String?)
    }

    private(set) var state: State = .idle
    private(set) var session: AuthSession?

    private static let sessionKey = "gugu.auth.session.v1"
    /// 재로그인 대기 표시 — 세션과 같은 키체인에 둬야 재설치 뒤에도 둘이 어긋나지 않는다
    private static let reauthKey = "gugu.auth.reauth.v1"
    /// 갱신이 겹치지 않도록 진행 중인 작업을 공유한다
    private var inflight: Task<AuthSession, Error>?
    /// 재로그인을 기다리는 계정 (만료된 이메일 계정)
    private var reauth: ReauthMarker?

    var userId: String? { session?.userId }
    var isAnonymous: Bool { session?.isAnonymous ?? false }
    /// 이메일이 붙은 영구 계정인지
    var isPermanent: Bool { session != nil && !isAnonymous }
    /// 로그인돼 있는 이메일 (예전 저장본은 다음 갱신 전까지 nil)
    var email: String? { session?.email }
    var needsReauth: Bool {
        if case .needsReauth = state { return true }
        return false
    }

    struct ReauthMarker: Codable, Equatable {
        var userId: String
        var email: String?
    }

    // MARK: - 이메일 연결·로그인

    /// 코드를 받는 목적 — 처음 쓰는 주소는 연결(승격), 이미 있는 주소는 로그인
    enum CodePurpose: Equatable {
        case link
        case signIn
    }

    enum Promotion: Equatable {
        case none
        case sending
        /// 확인 코드를 보낸 주소 — 이 화면에서 코드를 입력받는다. 코드가 틀리면 error 를 띄우고 화면은 유지한다.
        case codeSent(email: String, purpose: CodePurpose, error: String? = nil)
        case verifying(email: String, purpose: CodePurpose)
        case done(email: String, purpose: CodePurpose)
        case failed(String)
    }

    private(set) var promotion: Promotion = .none
    /// 마지막으로 코드를 보낸 시각 — 같은 주소 재발송은 60초 간격이 서버 제한이다
    private(set) var lastCodeSentAt: Date?
    static let resendInterval: TimeInterval = 60

    /// 이메일로 확인 코드를 보낸다. 처음 쓰는 주소면 이 계정에 연결하고,
    /// 이미 있는 계정의 주소면 그 계정으로 로그인하는 코드로 바꿔 보낸다.
    func requestEmailCode(email: String) async {
        let trimmed = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard trimmed.contains("@"), trimmed.count >= 5 else {
            promotion = .failed(String(localized: "이메일 주소를 확인해 주세요"))
            return
        }
        promotion = .sending
        do {
            // 재로그인 대기 중에는 붙일 익명 계정이 없다 — 바로 로그인 코드를 보낸다
            if needsReauth {
                try await SupabaseAuth.requestLoginCode(email: trimmed)
                codeSent(email: trimmed, purpose: .signIn)
                return
            }
            guard let token = await accessToken() else {
                promotion = .failed(String(localized: "계정 세션이 없어 연결할 수 없습니다"))
                return
            }
            do {
                try await SupabaseAuth.requestEmailPromotion(accessToken: token, email: trimmed)
                codeSent(email: trimmed, purpose: .link)
            } catch let error as SupabaseAuthError where Self.isExistingAccount(error) {
                // 이미 쓰던 계정의 주소 — 연결 메일은 나가지 않았으니 로그인 코드를 보낸다
                try await SupabaseAuth.requestLoginCode(email: trimmed)
                codeSent(email: trimmed, purpose: .signIn)
            }
        } catch let error as SupabaseAuthError {
            promotion = .failed(Self.promotionMessage(for: error))
        } catch {
            promotion = .failed(error.localizedDescription)
        }
    }

    /// 같은 주소로 코드를 다시 보낸다
    func resendCode() async {
        guard case let .codeSent(email, purpose, _) = promotion else { return }
        do {
            switch purpose {
            case .signIn:
                try await SupabaseAuth.requestLoginCode(email: email)
            case .link:
                guard let token = await accessToken() else {
                    promotion = .codeSent(email: email, purpose: purpose, error: String(localized: "계정 세션이 없어 다시 보낼 수 없습니다"))
                    return
                }
                try await SupabaseAuth.requestEmailPromotion(accessToken: token, email: email)
            }
            codeSent(email: email, purpose: purpose)
        } catch let error as SupabaseAuthError {
            promotion = .codeSent(email: email, purpose: purpose, error: Self.promotionMessage(for: error))
        } catch {
            promotion = .codeSent(email: email, purpose: purpose, error: error.localizedDescription)
        }
    }

    /// 메일로 받은 코드로 연결·로그인을 끝낸다.
    ///
    /// 다른 계정으로 바뀌는 경우(익명 → 기존 계정, 재로그인 중 다른 이메일) `onAccountSwitch` 가
    /// 새 세션을 저장하기 **전에** 돈다 — 예전 계정에 묶인 귀속·큐·서버 부여 이용권을 먼저 치워야
    /// 세션 변경에 반응하는 동기화가 예전 신원으로 새 계정에 요청하지 않는다.
    func confirmEmailCode(code: String, onAccountSwitch: @MainActor () -> Void) async {
        guard case let .codeSent(email, purpose, _) = promotion else { return }
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            promotion = .codeSent(email: email, purpose: purpose, error: String(localized: "확인 코드를 입력해 주세요"))
            return
        }
        promotion = .verifying(email: email, purpose: purpose)
        do {
            let fresh: AuthSession
            switch purpose {
            case .link: fresh = try await SupabaseAuth.verifyEmailChange(email: email, token: trimmed)
            case .signIn: fresh = try await SupabaseAuth.verifyLoginCode(email: email, token: trimmed)
            }
            let previous = session
            let previousUserId = previous?.userId ?? reauth?.userId
            if previousUserId != fresh.userId {
                print("[AuthStore] 계정 전환 — 기기 귀속·큐 정리 후 새 세션 저장")
                onAccountSwitch()
            }
            adopt(fresh)
            promotion = .done(email: email, purpose: purpose)
            // 버려지는 익명 세션은 서버에서도 폐기한다 (실패해도 토큰이 기기 밖으로 나가지 않으니 로그만)
            if let previous, previous.isAnonymous, previous.userId != fresh.userId {
                await revoke(previous)
            }
        } catch let error as SupabaseAuthError {
            promotion = .codeSent(email: email, purpose: purpose, error: Self.promotionMessage(for: error))
        } catch {
            promotion = .codeSent(email: email, purpose: purpose, error: error.localizedDescription)
        }
    }

    func resetPromotion() { promotion = .none }

    private func codeSent(email: String, purpose: CodePurpose) {
        lastCodeSentAt = Date()
        promotion = .codeSent(email: email, purpose: purpose)
    }

    /// 이 주소가 이미 다른 계정에 쓰이고 있다는 응답인지 — 로그인으로 넘어가는 신호
    nonisolated static func isExistingAccount(_ error: SupabaseAuthError) -> Bool {
        guard case let .http(_, code, _) = error else { return false }
        return code == "email_exists" || code == "user_already_exists"
    }

    /// 서버 오류 코드를 사용자 문구로 — 발송 제한이 가장 흔하다
    nonisolated static func promotionMessage(for error: SupabaseAuthError) -> String {
        guard case let .http(_, code, message) = error else { return error.localizedDescription }
        switch code {
        case "over_email_send_rate_limit":
            return String(localized: "확인 메일을 방금 보냈어요. 잠시 뒤 다시 시도해 주세요.")
        case "over_request_rate_limit":
            return String(localized: "요청이 많아요. 잠시 뒤 다시 시도해 주세요.")
        case "email_address_invalid":
            return String(localized: "이 이메일 주소는 쓸 수 없어요. 다른 주소를 넣어 주세요.")
        case "email_exists", "user_already_exists":
            return String(localized: "이미 연결된 이메일이에요.")
        case "otp_expired":
            // 코드를 틀려도 서버는 이 코드로 답한다
            return String(localized: "확인 코드가 맞지 않거나 만료됐어요. 다시 확인해 주세요.")
        case "otp_disabled":
            // create_user=false 로그인에서 없는 주소일 때
            return String(localized: "이 이메일로 만든 계정이 없어요. 주소를 확인해 주세요.")
        default:
            return message.isEmpty ? error.localizedDescription : message
        }
    }

    init() {
        session = Self.loadStored()
        reauth = Self.loadReauth()
    }

    /// 앱 시작 시 1회 호출. 저장된 세션이 있으면 필요할 때만 갱신하고, 없으면 익명 계정을 만든다.
    /// 재로그인을 기다리는 중이면 아무것도 발급하지 않는다 — 거부는 확정된 결과라 다시 시도해도 소용없다.
    func start() async {
        guard SupabaseConfig.isConfigured else {
            state = .disabled
            return
        }
        if session == nil, let reauth {
            state = .needsReauth(email: reauth.email)
            return
        }
        if let session, !session.isExpired {
            state = .ready
            return
        }
        do {
            _ = try await ensureSession()
        } catch AuthFlowError.reauthRequired {
            // ensureSession 이 상태를 needsReauth 로 바꿨다
        } catch {
            // 인증은 학습 기능의 전제가 아니다 — 기록만 남기고 앱은 그대로 진행한다
            state = .failed(error.localizedDescription)
            print("[AuthStore] 세션 준비 실패:", error.localizedDescription)
        }
    }

    /// 서버 호출에 붙일 액세스 토큰. 만료됐으면 갱신한 뒤 돌려준다.
    /// 재로그인 대기 중이면 nil — 익명 계정을 새로 만들어 서버 부여 이용권을 잃지 않게 한다.
    func accessToken() async -> String? {
        guard SupabaseConfig.isConfigured else { return nil }
        if session == nil, reauth != nil { return nil }
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
            adopt(fresh)
            return true
        } catch let error as SupabaseAuthError where error.needsFreshSignIn {
            if Self.onRefreshRejected(isAnonymous: current.isAnonymous) == .requireSignIn {
                enterReauth(from: current)
            }
            print("[AuthStore] 토큰 강제 갱신 거부:", error.localizedDescription)
            return false
        } catch {
            print("[AuthStore] 토큰 강제 갱신 실패:", error.localizedDescription)
            return false
        }
    }

    /// 계정과 서버에 보관된 학습 기록을 삭제한다 (App Store 5.1.1(v)).
    ///
    /// 성공하면 기기 세션을 버리고 새 익명 계정으로 다시 시작해, 사용자가 앱을
    /// 그대로 계속 쓸 수 있게 한다.
    ///
    /// 기기에 남은 귀속·큐 정리는 SyncStore 가 맡는데, AuthStore 는 SyncStore 를 모르므로
    /// 호출부가 `onLocalSessionCleared` 로 넘긴다. 이 콜백은 익명 재로그인(`start()`, 네트워크 대기)
    /// **보다 먼저** 돈다 — 대기 중에 앱이 종료돼도 삭제된 계정에 묶인 신원·아웃박스가 기기에 남지 않게 한다.
    func deleteAccount(onLocalSessionCleared: @MainActor () -> Void = {}) async -> Bool {
        guard let token = await accessToken() else { return false }
        do {
            try await LearningApi.deleteAccount(accessToken: token)
        } catch {
            print("[Auth] 계정 삭제 실패:", error.localizedDescription)
            return false
        }
        signOutLocally()
        promotion = .none
        print("[Auth] 계정 삭제: 서버 삭제 완료 — 기기 세션·기록 정리 후 익명 세션 재발급")
        onLocalSessionCleared()
        await start()
        return true
    }

    /// 이 기기에서 로그아웃한다 — 서버 계정과 기기 진행도는 남고, 새 익명 계정으로 다시 시작한다.
    /// 오프라인이어도 끝나야 하므로 서버 세션 폐기 실패는 로그만 남긴다.
    /// `onLocalSessionCleared` 는 deleteAccount 와 같은 이유로 익명 재발급보다 먼저 돈다.
    func signOut(onLocalSessionCleared: @MainActor () -> Void) async {
        let previous = session
        signOutLocally()
        promotion = .none
        print("[Auth] 로그아웃: 기기 세션 정리 후 익명 세션 재발급")
        onLocalSessionCleared()
        // 오프라인이면 서버 폐기가 타임아웃까지 걸린다 — 익명 재발급을 먼저 하고 폐기는 뒤에 시도한다
        await start()
        if let previous { await revoke(previous) }
    }

    /// 기기에서 계정 연결을 끊는다 (서버 계정은 남는다 — 삭제는 DELETE /api/account 담당)
    func signOutLocally() {
        inflight?.cancel()
        inflight = nil
        session = nil
        reauth = nil
        Keychain.remove(key: Self.sessionKey)
        Keychain.remove(key: Self.reauthKey)
        state = SupabaseConfig.isConfigured ? .idle : .disabled
    }

    // MARK: - 리프레시 거부

    enum RefreshRejection: Equatable {
        /// 익명 계정 — 잃을 것이 없으니 새 익명 계정을 만든다
        case reissueAnonymous
        /// 이메일 계정 — 익명으로 바꾸면 이용권·기록 연결이 조용히 사라진다. 다시 로그인을 받는다.
        case requireSignIn
    }

    nonisolated static func onRefreshRejected(isAnonymous: Bool) -> RefreshRejection {
        isAnonymous ? .reissueAnonymous : .requireSignIn
    }

    enum AuthFlowError: Error {
        case reauthRequired
    }

    // MARK: - 내부

    /// 유효한 세션을 보장한다. 동시에 여러 번 불려도 실제 호출은 한 번만 나간다.
    private func ensureSession() async throws -> AuthSession {
        if let inflight { return try await inflight.value }

        let task = Task<AuthSession, Error> { [session] in
            // 리프레시 토큰이 있으면 갱신을 먼저 시도하고, 거부당하면 계정 종류에 따라 처리한다
            if let session {
                do {
                    return try await SupabaseAuth.refresh(refreshToken: session.refreshToken)
                } catch let error as SupabaseAuthError where error.needsFreshSignIn {
                    switch Self.onRefreshRejected(isAnonymous: session.isAnonymous) {
                    case .requireSignIn:
                        print("[AuthStore] 리프레시 거부 — 이메일 계정이라 재로그인을 기다린다:", error.localizedDescription)
                        throw AuthFlowError.reauthRequired
                    case .reissueAnonymous:
                        print("[AuthStore] 리프레시 거부 — 익명 계정을 새로 만든다:", error.localizedDescription)
                    }
                }
            }
            return try await SupabaseAuth.signInAnonymously()
        }
        inflight = task
        state = .working
        defer { inflight = nil }

        do {
            let fresh = try await task.value
            adopt(fresh)
            return fresh
        } catch AuthFlowError.reauthRequired {
            if let session { enterReauth(from: session) }
            throw AuthFlowError.reauthRequired
        } catch {
            state = .failed(error.localizedDescription)
            throw error
        }
    }

    /// 새 세션을 쓰기 시작한다 — 재로그인 표시는 지운다
    private func adopt(_ fresh: AuthSession) {
        session = fresh
        store(fresh)
        if reauth != nil {
            reauth = nil
            Keychain.remove(key: Self.reauthKey)
        }
        state = .ready
    }

    /// 죽은 이메일 세션을 버리고 재로그인을 기다린다. 기기 귀속·큐는 그대로 둔다 —
    /// 같은 계정으로 다시 로그인하면 쌓인 기록이 이어서 올라간다.
    private func enterReauth(from expired: AuthSession) {
        let marker = ReauthMarker(userId: expired.userId, email: expired.email)
        if let data = try? JSONEncoder().encode(marker) {
            Keychain.save(data, key: Self.reauthKey)
        }
        reauth = marker
        inflight?.cancel()
        inflight = nil
        session = nil
        Keychain.remove(key: Self.sessionKey)
        state = .needsReauth(email: marker.email)
    }

    private func revoke(_ old: AuthSession) async {
        do {
            try await SupabaseAuth.signOut(accessToken: old.accessToken)
        } catch {
            print("[AuthStore] 서버 세션 폐기 실패(무시):", error.localizedDescription)
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

    private static func loadReauth() -> ReauthMarker? {
        guard let data = Keychain.load(key: reauthKey) else { return nil }
        return try? JSONDecoder().decode(ReauthMarker.self, from: data)
    }
}
