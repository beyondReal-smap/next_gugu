import Foundation
import Observation

// 학습 기록 동기화 (아웃박스 적재 + 업로드).
//
// 서버는 보호자 검증(app_metadata.account_role=guardian, age_verified=true)이 끝난 계정만
// 업로드를 허용한다. 검증 전에는 403 이 오므로, 한 번 거부되면 **업로드를 아예 시도하지 않고**
// 큐에 쌓아 둔다. 검증이 켜지면 그때 한꺼번에 올라간다.

@MainActor
@Observable
final class SyncStore {
    enum State: Equatable {
        case idle
        case uploading
        /// 보호자 검증 전 — 큐에 쌓아 두고 기다린다
        case waitingForGuardian(String)
        /// 귀속 후보가 여럿 — 사용자가 골라야 한다
        case needsLearnerChoice([LearningClaimCandidate])
        case failed(String)
    }

    private(set) var state: State = .idle
    private(set) var pendingCount: Int = 0
    private(set) var lastServerCursor: Int = 0

    private var flushTask: Task<Void, Never>?
    /// 권한 거부를 받은 뒤에는 세션이 바뀔 때까지 업로드를 멈춘다
    private var blockedUserId: String?

    init() {
        let stored = LearningOutboxStore.read()
        pendingCount = stored.events.count
        lastServerCursor = stored.serverCursor
    }

    /// 세션 결과를 큐에 적재한다. 네트워크와 무관하게 항상 성공해야 한다.
    func record(_ result: SessionResult) {
        let identity = LearningIdentityStore.read()
        // learnerId 는 서버가 배정한다 — 귀속 전에는 올릴 수 없으므로 적재도 하지 않는다.
        // 조용히 return 하면 "왜 아무 것도 안 올라가는지" 추적할 단서가 없다 — 반드시 남긴다.
        guard let learnerId = identity.learnerId else {
            print("[Sync] 적재 건너뜀: 학습자 귀속 전 (문항 \(result.answers.count)개 버림)")
            return
        }
        print("[Sync] 세션 적재 learner=\(learnerId.prefix(8)) 문항=\(result.answers.count)개")

        let ids = LearningRecord.Ids(
            learnerId: learnerId,
            deviceId: identity.installId,
            sessionId: UUID().uuidString.lowercased()
        )
        var state = LearningOutboxStore.read()
        for pending in LearningRecord.events(from: result, ids: ids) {
            let event = pending.withEventId(UUID().uuidString.lowercased())
            do {
                state = try LearningOutbox.enqueue(state, event: event)
            } catch {
                // 한 문항이 계약을 벗어나도 나머지는 적재한다
                print("[Sync] 이벤트 적재 실패:", error.localizedDescription)
            }
        }
        LearningOutboxStore.write(state)
        pendingCount = state.events.count
    }

    /// 큐를 업로드한다. 보호자 검증 전이면 아무 요청도 보내지 않는다.
    func flush(auth: AuthStore) {
        guard flushTask == nil else { return }
        // 같은 계정으로 이미 권한 거부를 받았다면 서버를 다시 부르지 않는다
        if case .waitingForGuardian = state, blockedUserId == auth.userId { return }
        flushTask = Task { [weak self] in
            await self?.runFlush(auth: auth)
            self?.flushTask = nil
        }
    }

    private func runFlush(auth: AuthStore) async {
        var outbox = LearningOutboxStore.read()
        guard !outbox.events.isEmpty else {
            state = .idle
            return
        }
        guard let token = await auth.accessToken() else {
            state = .failed("계정 세션이 없어 업로드를 건너뜁니다")
            return
        }

        state = .uploading
        let batch = LearningOutbox.nextBatch(outbox)
        do {
            let response = try await LearningApi.uploadEvents(accessToken: token, events: batch)
            // 업로드 중 적재된 이벤트를 잃지 않도록 최신 상태로 반영한다
            outbox = try LearningOutbox.applyResponse(
                current: LearningOutboxStore.read(), batch: batch, response: response
            )
            LearningOutboxStore.write(outbox)
            pendingCount = outbox.events.count
            lastServerCursor = outbox.serverCursor
            blockedUserId = nil
            state = .idle
            if !response.rejected.isEmpty {
                print("[Sync] 서버 거부 \(response.rejected.count)건:", response.rejected.map(\.code))
            }
        } catch let error as LearningApi.ApiError {
            switch error {
            case let .permissionDenied(denial):
                // 검증 전 — 큐를 그대로 두고 더 시도하지 않는다
                blockedUserId = auth.userId
                state = .waitingForGuardian(denial.message)
            default:
                state = .failed(error.localizedDescription)
                print("[Sync] 업로드 실패:", error.localizedDescription)
            }
        } catch {
            state = .failed(error.localizedDescription)
            print("[Sync] 업로드 실패:", error.localizedDescription)
        }
    }

    /// 계정 삭제 직후 기기에 남은 귀속과 큐를 버린다.
    /// 서버 계정이 사라졌으므로 learnerId 와 올리지 못한 이벤트는 모두 의미가 없다.
    func resetAfterAccountDeletion() {
        flushTask?.cancel()
        flushTask = nil
        LearningIdentityStore.clear()
        LearningOutboxStore.clear()
        pendingCount = 0
        lastServerCursor = 0
        blockedUserId = nil
        state = .idle
    }

    /// 보호자 검증이 켜졌을 수 있는 시점(로그인/승격 직후)에 차단을 푼다
    func clearGuardianBlock() {
        blockedUserId = nil
        if case .waitingForGuardian = state { state = .idle }
    }

    /// 이 기기 기록을 어느 학습자에 붙일지 정한다. learnerId 가 이미 있으면 아무 것도 하지 않는다.
    ///
    /// 서버는 세 가지로 답한다.
    /// - create_learner: 학습자를 새로 만들어 줬다 — 그대로 저장
    /// - attach_to_existing: 기존 학습자에 붙인다. learnerId 가 없으면 후보 1명을 confirm 으로 확정
    /// - conflict: 후보가 여럿이거나 다른 계정이 쓰던 기기 — 사용자가 골라야 한다
    func ensureLearner(auth: AuthStore) async {
        var identity = LearningIdentityStore.read()
        if identity.learnerId != nil { return }
        guard auth.isPermanent, let token = await auth.accessToken() else { return }

        let pending = LearningOutboxStore.read().events.count
        do {
            let response = try await LearningApi.claim(
                accessToken: token, installId: identity.installId, localEventCount: pending
            )
            switch response.action {
            case .createLearner:
                guard let learnerId = response.learnerId else {
                    state = .failed("서버가 학습자 아이디를 주지 않았습니다")
                    return
                }
                try bind(learnerId: learnerId, userId: auth.userId)
            case .attachToExisting:
                if let learnerId = response.learnerId {
                    try bind(learnerId: learnerId, userId: auth.userId)
                } else if response.candidates.count == 1 {
                    try await confirm(auth: auth, token: token,
                                      installId: identity.installId,
                                      learnerId: response.candidates[0].learnerId)
                } else {
                    // 붙일 대상을 특정할 수 없다 — 고르게 한다
                    state = .needsLearnerChoice(response.candidates)
                }
            case .conflict:
                state = .needsLearnerChoice(response.candidates)
            }
            identity = LearningIdentityStore.read()
        } catch {
            print("[Sync] 귀속 실패:", error.localizedDescription)
            state = .failed(errorMessage(error))
        }
    }

    /// 후보 중 하나를 골라 귀속을 확정한다 (conflict 화면에서 호출)
    func chooseLearner(_ candidate: LearningClaimCandidate, auth: AuthStore) async {
        guard let token = await auth.accessToken() else { return }
        let identity = LearningIdentityStore.read()
        do {
            try await confirm(auth: auth, token: token,
                              installId: identity.installId, learnerId: candidate.learnerId)
        } catch {
            print("[Sync] 귀속 확정 실패:", error.localizedDescription)
            state = .failed(errorMessage(error))
        }
    }

    private func confirm(auth: AuthStore, token: String, installId: String, learnerId: String) async throws {
        let confirmed = try await LearningApi.confirmClaim(
            accessToken: token, installId: installId, learnerId: learnerId
        )
        try bind(learnerId: confirmed.learnerId, userId: auth.userId)
    }

    private func bind(learnerId: String, userId: String?) throws {
        try LearningIdentityStore.bindLearner(userId: userId ?? "", learnerId: learnerId)
        state = .idle
    }

    private func errorMessage(_ error: Error) -> String {
        (error as? LearningApi.ApiError)?.localizedDescription ?? error.localizedDescription
    }

    /// 구매 이력을 근거로 보호자 권한을 켜고 업로드를 재개한다.
    ///
    /// 순서가 중요하다 — 서버가 app_metadata 를 바꿔도 손에 든 JWT 는 예전 권한이므로,
    /// 토큰을 새로 받은 뒤에야 업로드가 통과한다.
    func enableGuardianSync(auth: AuthStore) async {
        // 이메일이 붙지 않은 익명 계정은 서버가 거부한다 — 요청하지 않는다
        guard auth.isPermanent, let token = await auth.accessToken() else { return }

        // 구매를 먼저 계정에 연결한다. 이게 없으면 서버가 구매 내역을 못 찾아 거부한다.
        do {
            let registered = try await PurchaseRegistry.register(accessToken: token)
            print("[Sync] 구매 등록 완료 premium=\(registered.premium)")
        } catch {
            // 이미 등록됐거나 영수증이 없을 수 있다 — guardian 요청은 그대로 시도한다
            print("[Sync] 구매 등록 건너뜀:", error.localizedDescription)
        }

        do {
            let response = try await LearningApi.verifyGuardian(accessToken: token)
            guard response.guardian else { return }
            // 새 권한이 담긴 토큰을 받아야 업로드가 통과한다
            await auth.forceRefresh()
            clearGuardianBlock()
            // 학습자 귀속이 끝나야 이벤트를 만들 수 있다
            await ensureLearner(auth: auth)
            flush(auth: auth)
        } catch let error as LearningApi.ApiError {
            if case let .http(status, code, _) = error, status == 403,
               let denial = code.flatMap(LearningApi.GuardianDenial.init(rawValue:)) {
                // 구매 없음/익명 — 정상적인 거부다. 사용자에게 사유를 보여 준다.
                state = .waitingForGuardian(denial.message)
                blockedUserId = auth.userId
                return
            }
            print("[Sync] 보호자 권한 설정 실패:", error.localizedDescription)
        } catch {
            print("[Sync] 보호자 권한 설정 실패:", error.localizedDescription)
        }
    }
}
