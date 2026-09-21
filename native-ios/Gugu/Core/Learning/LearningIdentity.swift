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

    static func fresh(installId: String) -> LearningIdentity {
        LearningIdentity(version: 1, installId: installId, learnerId: nil, claimedUserId: nil)
    }
}
