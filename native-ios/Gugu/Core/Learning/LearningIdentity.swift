import Foundation

// 기기 installId / 학습자 id — 서버 동기화용 (lib/learning/identity.ts 이식).
// 로컬 진행(gugu.progress.v1)과 분리해 따로 보관한다.

struct LearningIdentity: Codable, Equatable {
    var version: Int = 1
    /// 이 설치본을 가리키는 id — 서버의 deviceId 로 쓴다
    var installId: String
    /// 서버가 배정한 학습자 id (귀속 전에는 nil)
    var learnerId: String?
    /// 기록을 귀속한 계정 id (귀속 전에는 nil)
    var claimedUserId: String?
    /// 새로 귀속한 학습자의 서버 기록을 아직 받아오지 않았는지 — 받아오면 false 로 내려 한 번만 복원한다
    var restorePending: Bool = false

    static func fresh(installId: String) -> LearningIdentity {
        LearningIdentity(version: 1, installId: installId, learnerId: nil, claimedUserId: nil)
    }
}

extension LearningIdentity {
    private enum CodingKeys: String, CodingKey {
        case version, installId, learnerId, claimedUserId, restorePending
    }

    /// restorePending 이 없던 예전 저장본은 false 로 읽는다 — 이미 쓰던 기기에서 복원이 다시 돌지 않게
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 1
        installId = try c.decode(String.self, forKey: .installId)
        learnerId = try c.decodeIfPresent(String.self, forKey: .learnerId)
        claimedUserId = try c.decodeIfPresent(String.self, forKey: .claimedUserId)
        restorePending = try c.decodeIfPresent(Bool.self, forKey: .restorePending) ?? false
    }
}
