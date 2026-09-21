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
        // learnerId 는 서버가 배정한다 — 귀속 전에는 올릴 수 없으므로 적재도 하지 않는다
        guard let learnerId = identity.learnerId else { return }

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

    /// 보호자 검증이 켜졌을 수 있는 시점(로그인/승격 직후)에 차단을 푼다
    func clearGuardianBlock() {
        blockedUserId = nil
        if case .waitingForGuardian = state { state = .idle }
    }

    /// 구매 이력을 근거로 보호자 권한을 켜고 업로드를 재개한다.
    ///
    /// 순서가 중요하다 — 서버가 app_metadata 를 바꿔도 손에 든 JWT 는 예전 권한이므로,
    /// 토큰을 새로 받은 뒤에야 업로드가 통과한다.
    func enableGuardianSync(auth: AuthStore) async {
        // 이메일이 붙지 않은 익명 계정은 서버가 거부한다 — 요청하지 않는다
        guard auth.isPermanent, let token = await auth.accessToken() else { return }

        do {
            let response = try await LearningApi.verifyGuardian(accessToken: token)
            guard response.guardian else { return }
            // 새 권한이 담긴 토큰을 받아야 업로드가 통과한다
            await auth.forceRefresh()
            clearGuardianBlock()
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
