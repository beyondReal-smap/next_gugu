import Foundation

// 학습 신원 / 아웃박스 영속화.
// Core/Learning 은 순수 로직만 두고, 저장(UserDefaults)은 이 계층이 맡는다
// — 의존성은 Services → Core 한 방향으로만 흐른다.

enum LearningIdentityStore {
    static var key: String { Persistence.learningIdentityKey }

    /// 저장본을 읽고, 없거나 깨졌으면 새로 발급해 저장한다.
    /// installId 가 UUID 형식이 아니면 재발급한다(서버가 형식을 검증하므로).
    static func read() -> LearningIdentity {
        guard let stored = Persistence.load(LearningIdentity.self, key: key) else {
            return createAndStore()
        }
        guard LearningContract.isUuid(stored.installId) else {
            print("[LearningIdentity] installId 형식 오류 — 재발급")
            return createAndStore()
        }
        var identity = stored
        identity.version = 1
        // learnerId 는 서버가 준 UUID 여야 한다. 형식이 깨졌으면 귀속 전으로 되돌린다.
        if let learnerId = identity.learnerId, !LearningContract.isUuid(learnerId) {
            print("[LearningIdentity] learnerId 형식 오류 — 귀속 해제")
            identity.learnerId = nil
            identity.claimedUserId = nil
            write(identity)
        }
        return identity
    }

    /// 계정 삭제 후 기기의 귀속 정보를 버린다 — 다음 실행에서 새 installId 가 발급된다
    static func clear() {
        Persistence.remove(key)
    }

    static func write(_ identity: LearningIdentity) {
        Persistence.save(identity, key: key)
    }

    /// 서버가 배정한 학습자를 계정에 묶는다
    @discardableResult
    static func bindLearner(userId: String, learnerId: String) throws -> LearningIdentity {
        guard LearningContract.isUuid(learnerId) else {
            throw LearningValidationError.field("learnerId")
        }
        var identity = read()
        identity.learnerId = learnerId
        identity.claimedUserId = userId
        write(identity)
        return identity
    }

    private static func createAndStore() -> LearningIdentity {
        let identity = LearningIdentity.fresh(installId: UUID().uuidString.lowercased())
        write(identity)
        return identity
    }
}

// MARK: - 저장

enum LearningOutboxStore {
    static var key: String { Persistence.learningOutboxKey }

    static func read() -> LearningOutboxState {
        guard let stored = Persistence.load(LearningOutboxState.self, key: key) else {
            return LearningOutboxState()
        }
        // 저장본이 계약을 벗어나면 그 이벤트만 버린다 — 큐 전체를 잃지 않게
        var state = stored
        state.version = 1
        state.events = stored.events.filter { event in
            do {
                try LearningContract.validate(event)
                return true
            } catch {
                print("[LearningOutbox] 저장본에서 잘못된 이벤트 제외:", error.localizedDescription)
                return false
            }
        }
        if state.serverCursor < 0 { state.serverCursor = 0 }
        return state
    }

    /// 계정 삭제 후 올릴 곳이 없어진 큐를 버린다
    static func clear() {
        Persistence.remove(key)
    }

    static func write(_ state: LearningOutboxState) {
        Persistence.save(state, key: key)
    }
}
