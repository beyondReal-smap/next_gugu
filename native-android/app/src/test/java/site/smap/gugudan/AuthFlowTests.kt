package site.smap.gugudan

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.smap.gugudan.core.learning.LearningFactStats
import site.smap.gugudan.core.learning.LearningIdentity
import site.smap.gugudan.core.learning.LearningRestore
import site.smap.gugudan.core.learning.LearningStateResponse
import site.smap.gugudan.services.AuthSession
import site.smap.gugudan.services.SupabaseAuth
import site.smap.gugudan.services.SupabaseAuthException
import site.smap.gugudan.store.AuthStore
import kotlin.test.assertFailsWith

// 이메일 로그인 흐름 검증 (iOS AuthFlowTests 미러링) — 세션 파싱 / 리프레시 거부 판정 / 연결·로그인 분기 / 오류 문구 / 서버 기록 복원

class AuthFlowTests {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun tokenResponse(user: String) = json.parseToJsonElement(
        """{"access_token":"a","refresh_token":"r","expires_at":2000000000.0,"user":$user}"""
    ).jsonObject

    // MARK: 세션 파싱

    /** 익명 사용자는 email 이 빈 문자열로 온다 — null 로 본다 */
    @Test fun parseAnonymousEmailIsNull() {
        val session = SupabaseAuth.parse(tokenResponse("""{"id":"u1","is_anonymous":true,"email":""}"""))
        assertNull(session.email)
        assertTrue(session.isAnonymous)
    }

    @Test fun parsePermanentEmail() {
        val session = SupabaseAuth.parse(tokenResponse("""{"id":"u1","is_anonymous":false,"email":"a@b.com"}"""))
        assertEquals("a@b.com", session.email)
        assertFalse(session.isAnonymous)
    }

    @Test fun parseMissingEmailKeyIsNull() {
        val session = SupabaseAuth.parse(tokenResponse("""{"id":"u1"}"""))
        assertNull(session.email)
    }

    /** email 필드가 없던 예전 저장본도 읽힌다 */
    @Test fun legacyStoredSessionDecodes() {
        val legacy = """{"accessToken":"a","refreshToken":"r","expiresAt":1,"userId":"u1","isAnonymous":false}"""
        val session = json.decodeFromString(AuthSession.serializer(), legacy)
        assertEquals("u1", session.userId)
        assertNull(session.email)
    }

    // MARK: 리프레시 거부

    @Test fun refreshRejectionReissuesAnonymous() {
        assertEquals(AuthStore.RefreshRejection.REISSUE_ANONYMOUS, AuthStore.onRefreshRejected(isAnonymous = true))
    }

    /** 이메일 계정은 익명으로 바꾸지 않고 재로그인을 받는다 */
    @Test fun refreshRejectionRequiresSignInForEmailAccount() {
        assertEquals(AuthStore.RefreshRejection.REQUIRE_SIGN_IN, AuthStore.onRefreshRejected(isAnonymous = false))
    }

    /** 네트워크 오류 등은 거부가 아니다 — 세션을 버리지 않는다 */
    @Test fun onlyAuthRejectionNeedsFreshSignIn() {
        assertTrue(SupabaseAuthException(400, "refresh_token_not_found", "").needsFreshSignIn)
        assertFalse(SupabaseAuthException(500, null, "").needsFreshSignIn)
    }

    // MARK: 연결·로그인 분기

    @Test fun existingAccountSwitchesToSignIn() {
        assertTrue(AuthStore.isExistingAccount(SupabaseAuthException(422, "email_exists", "")))
        assertTrue(AuthStore.isExistingAccount(SupabaseAuthException(422, "user_already_exists", "")))
    }

    @Test fun otherErrorsDoNotSwitch() {
        assertFalse(AuthStore.isExistingAccount(SupabaseAuthException(422, "email_address_invalid", "")))
        assertFalse(AuthStore.isExistingAccount(SupabaseAuthException(429, "over_email_send_rate_limit", "")))
        assertFalse(AuthStore.isExistingAccount(SupabaseAuthException(500, null, "")))
    }

    // MARK: 오류 문구 (iOS promotionMessage 와 글자까지 같아야 한다)

    @Test fun promotionMessages() {
        fun message(code: String) = AuthStore.promotionMessage(SupabaseAuthException(400, code, "raw"))
        assertEquals("확인 코드가 맞지 않거나 만료됐어요. 다시 확인해 주세요.", message("otp_expired"))
        assertEquals("이 이메일로 만든 계정이 없어요. 주소를 확인해 주세요.", message("otp_disabled"))
        assertEquals("확인 메일을 방금 보냈어요. 잠시 뒤 다시 시도해 주세요.", message("over_email_send_rate_limit"))
        assertEquals("요청이 많아요. 잠시 뒤 다시 시도해 주세요.", message("over_request_rate_limit"))
        assertEquals("raw", message("unknown_code"))
    }

    // MARK: 서버 기록 복원

    private fun stats(correct: Int, wrong: Int) = LearningFactStats(correct + wrong, correct, wrong)

    @Test fun weightMimicsLocalRule() {
        assertEquals(2, LearningRestore.weight(stats(0, 1)))
        assertEquals(1, LearningRestore.weight(stats(1, 1)))
        assertEquals(0, LearningRestore.weight(stats(5, 1)))
        assertEquals(6, LearningRestore.weight(stats(0, 10)))
    }

    @Test fun mergeKeepsMaxPerFact() {
        val merged = LearningRestore.merge(
            local = mapOf("7x8" to 4, "3x4" to 1),
            server = mapOf("7x8" to stats(0, 1), "3x4" to stats(0, 2), "6x6" to stats(0, 3)),
        )
        assertEquals(mapOf("7x8" to 4, "3x4" to 4, "6x6" to 6), merged)
    }

    @Test fun mergeSkipsInvalidKeysAndMasteredFacts() {
        val merged = LearningRestore.merge(
            local = mapOf("2x3" to 2),
            server = mapOf("bad" to stats(0, 5), "99x99" to stats(0, 5), "2x5" to stats(9, 0)),
        )
        assertEquals(mapOf("2x3" to 2), merged)
    }

    @Test fun mergeIsIdempotent() {
        val server = mapOf("7x8" to stats(1, 3), "4x6" to stats(0, 1))
        val once = LearningRestore.merge(mapOf("9x9" to 2), server)
        assertEquals(once, LearningRestore.merge(once, server))
    }

    @Test fun emptyServerKeepsLocal() {
        assertEquals(mapOf("7x8" to 3), LearningRestore.merge(mapOf("7x8" to 3), emptyMap()))
    }

    /** 2026-10-16 운영 DB 에서 조회한 실제 /learning/state 스냅샷 형태 그대로 */
    @Test fun decodesRealSnapshotState() {
        val raw = """
        {"snapshots":[{"learnerId":"7987cd43-84be-423e-a93f-a28d3e15aad9","revision":3,"serverCursor":12,
        "updatedAt":"2026-10-11T02:23:19+00:00",
        "state":{"facts":{"2x8":{"wrong":0,"correct":1,"accuracy":1.0000,"attempts":1,
        "lastAnsweredAt":"2026-10-11T02:23:18+00:00","averageResponseMs":1719}},
        "accuracy":1.0,"learnerId":"7987cd43-84be-423e-a93f-a28d3e15aad9","totalWrong":0,
        "lastEventAt":"2026-10-11T02:23:18+00:00","totalAnswers":2,"totalCorrect":2}}],
        "serverCursor":12}
        """
        val response = json.decodeFromString(LearningStateResponse.serializer(), raw)
        assertEquals(LearningFactStats(attempts = 1, correct = 1, wrong = 0), response.snapshots.first().state.facts["2x8"])
    }

    /** facts 가 빠지면 계약이 바뀐 것 — 빈 값으로 넘기지 않고 실패한다 */
    @Test fun snapshotWithoutFactsFails() {
        val raw = """{"snapshots":[{"learnerId":"x","revision":1,"serverCursor":1,"updatedAt":"t","state":{}}],"serverCursor":1}"""
        assertFailsWith<Exception> { json.decodeFromString(LearningStateResponse.serializer(), raw) }
    }

    // MARK: 학습 신원

    /** restorePending 이 없던 예전 저장본은 false — 이미 쓰던 기기에서 복원이 다시 돌지 않는다 */
    @Test fun legacyIdentityDecodesRestorePendingFalse() {
        val legacy = """{"version":1,"installId":"i","learnerId":"l","claimedUserId":"u"}"""
        val identity = json.decodeFromString(LearningIdentity.serializer(), legacy)
        assertFalse(identity.restorePending)
        assertEquals("l", identity.learnerId)
    }

    @Test fun identityRoundTripKeepsRestorePending() {
        val identity = LearningIdentity.fresh("i").copy(restorePending = true)
        val decoded = json.decodeFromString(
            LearningIdentity.serializer(), json.encodeToString(LearningIdentity.serializer(), identity),
        )
        assertEquals(identity, decoded)
    }
}
