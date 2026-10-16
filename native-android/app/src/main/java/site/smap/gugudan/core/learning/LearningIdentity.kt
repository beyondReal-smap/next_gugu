package site.smap.gugudan.core.learning

import kotlinx.serialization.Serializable
import java.util.UUID

// 기기 installId / 학습자 id — 서버 동기화용 (lib/learning/identity.ts / iOS 이식).
// 로컬 진행(gugu.progress.v1)과 분리해 따로 보관한다.

@Serializable
data class LearningIdentity(
    val version: Int = 1,
    /** 이 설치본을 가리키는 id — 서버의 deviceId 로 쓴다 */
    val installId: String,
    /** 서버가 배정한 학습자 id (귀속 전에는 null) */
    val learnerId: String? = null,
    /** 기록을 귀속한 계정 id (귀속 전에는 null) */
    val claimedUserId: String? = null,
    /**
     * 새로 귀속한 학습자의 서버 기록을 아직 받아오지 않았는지 — 받아오면 false 로 내려 한 번만 복원한다.
     * 이 필드가 없던 예전 저장본은 false 로 읽는다 — 이미 쓰던 기기에서 복원이 다시 돌지 않게.
     */
    val restorePending: Boolean = false,
) {
    companion object {
        fun fresh(installId: String) = LearningIdentity(installId = installId)
    }
}
