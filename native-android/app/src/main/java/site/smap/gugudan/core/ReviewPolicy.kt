package site.smap.gugudan.core

import kotlinx.serialization.Serializable

// 앱 평가 요청 시점 판단 — 순수 로직 (iOS ReviewPolicy.swift 이식, 화면은 ResultView 가 Play In-App Review 로 띄운다).
//
// 왜 필요한가: 일본 스토어 평점이 1건(1점)뿐인데 검색 결과에 그대로 노출돼 진입률이 낮다. 평가가 쌓일 통로가 앱 안에 없었다.
// 원칙: 아이가 잘 해낸 순간에만, 충분히 써 본 뒤에, 버전당 한 번. Play 도 자체 할당량으로 노출 횟수를 제한한다.
// 사전 질문으로 만족한 사람만 거르는 방식은 Play 정책(리뷰 유도 금지) 위반이라 쓰지 않는다.

/** 평가 요청 이력 — Persistence(SharedPreferences JSON)에 저장 */
@Serializable
data class ReviewPromptLog(
    val lastAskedAtMillis: Long? = null,
    val lastAskedVersion: String? = null,
    val askCount: Int = 0,
)

/** 방금 끝난 한 판의 결과 요약 — ResultView 의 값을 그대로 옮긴다 */
data class ReviewMoment(
    val partial: Boolean,       // 중간에 그만둔 판
    val accuracy: Int,          // 0~100
    val isNewBest: Boolean,
    val improvedStars: Boolean,
    val goalReached: Boolean,
)

object ReviewPolicy {
    /** 끝까지 푼 판이 이만큼 쌓인 뒤에 묻는다 (GameState.recentAccuracy 는 최대 20개라 그 안의 값이어야 한다) */
    const val MIN_SESSIONS = 4
    /** 서로 다른 날 이만큼 써 본 뒤에 묻는다 */
    const val MIN_ACTIVE_DAYS = 2
    /** 정확도가 이 이상이면 "잘 해낸 판"으로 본다 */
    const val GOOD_ACCURACY = 90
    /** 마지막 요청 뒤 최소 간격 — 새 버전이 나와도 이 기간 안에는 다시 묻지 않는다 */
    const val MIN_DAYS_BETWEEN_ASKS = 60
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000

    /** 잘 해낸 순간인가 — 신기록·별 상승·오늘 목표 달성·높은 정확도 중 하나 */
    fun isHappyMoment(m: ReviewMoment): Boolean {
        if (m.partial) return false
        return m.isNewBest || m.improvedStars || m.goalReached || m.accuracy >= GOOD_ACCURACY
    }

    /** 문제를 하나라도 푼 날의 수 */
    fun activeDays(state: GameState): Int =
        state.dayLog.filter { it.correct + it.wrong > 0 }.map { it.date }.toSet().size

    /** 지금 평가를 요청해도 되는가 */
    fun shouldAsk(moment: ReviewMoment, state: GameState, log: ReviewPromptLog, appVersion: String, nowMillis: Long): Boolean {
        if (!isHappyMoment(moment)) return false
        if (state.recentAccuracy.size < MIN_SESSIONS) return false
        if (activeDays(state) < MIN_ACTIVE_DAYS) return false
        if (log.lastAskedVersion == appVersion) return false
        val last = log.lastAskedAtMillis
        if (last != null && nowMillis - last < MIN_DAYS_BETWEEN_ASKS * DAY_MILLIS) return false
        return true
    }

    /** 요청했다고 기록한 새 이력 */
    fun recordAsk(log: ReviewPromptLog, appVersion: String, nowMillis: Long): ReviewPromptLog =
        ReviewPromptLog(lastAskedAtMillis = nowMillis, lastAskedVersion = appVersion, askCount = log.askCount + 1)
}
