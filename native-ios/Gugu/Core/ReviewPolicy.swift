import Foundation

// 앱 평가 요청 시점 판단 — 순수 로직 (화면은 ResultView 가 SwiftUI requestReview 로 띄운다).
//
// 왜 필요한가: 일본 스토어 평점이 1건(1점)뿐인데 검색 결과에 그대로 노출돼, 노출 → 제품 페이지 진입률이
// 일본 2.9% · 한국 4.8%로 벌어졌다(2026-10-23~11-11 App Analytics). 평가가 쌓일 통로가 앱 안에 없었다.
// 원칙: 아이가 잘 해낸 순간에만, 충분히 써 본 뒤에, 버전당 한 번. Apple 도 365일에 3번까지만 실제로 띄운다.
// 사전 질문("마음에 드세요?")으로 만족한 사람만 거르는 방식은 App Store 심사 지침 5.6.1 위반이라 쓰지 않는다.

/// 평가 요청 이력 — Persistence(UserDefaults JSON)에 저장
struct ReviewPromptLog: Codable, Equatable {
    var lastAskedAt: Date?
    var lastAskedVersion: String?
    var askCount: Int = 0
}

/// 방금 끝난 한 판의 결과 요약 — ResultView 의 값을 그대로 옮긴다
struct ReviewMoment: Equatable {
    var partial: Bool          // 중간에 그만둔 판
    var accuracy: Int          // 0~100
    var isNewBest: Bool
    var improvedStars: Bool
    var goalReached: Bool
}

enum ReviewPolicy {
    static let storageKey = "gugu.review.v1"
    /// 끝까지 푼 판이 이만큼 쌓인 뒤에 묻는다 (GameState.recentAccuracy 는 최대 20개라 그 안의 값이어야 한다)
    static let minSessions = 4
    /// 서로 다른 날 이만큼 써 본 뒤에 묻는다 — 첫날 몰아서 한 사람은 아직 평가할 만큼 써 보지 않았다
    static let minActiveDays = 2
    /// 정확도가 이 이상이면 "잘 해낸 판"으로 본다
    static let goodAccuracy = 90
    /// 마지막 요청 뒤 최소 간격 — 새 버전이 나와도 이 기간 안에는 다시 묻지 않는다
    static let minDaysBetweenAsks = 60

    /// 잘 해낸 순간인가 — 신기록·별 상승·오늘 목표 달성·높은 정확도 중 하나
    static func isHappyMoment(_ m: ReviewMoment) -> Bool {
        guard !m.partial else { return false }
        return m.isNewBest || m.improvedStars || m.goalReached || m.accuracy >= goodAccuracy
    }

    /// 지금 평가를 요청해도 되는가
    static func shouldAsk(moment: ReviewMoment, state: GameState, log: ReviewPromptLog,
                          appVersion: String, now: Date) -> Bool {
        guard isHappyMoment(moment) else { return false }
        guard state.recentAccuracy.count >= minSessions else { return false }
        guard activeDays(state) >= minActiveDays else { return false }
        if log.lastAskedVersion == appVersion { return false }
        if let last = log.lastAskedAt,
           now.timeIntervalSince(last) < Double(minDaysBetweenAsks) * 86_400 { return false }
        return true
    }

    /// 문제를 하나라도 푼 날의 수 (dayLog 는 날짜별 링버퍼, 구버전 저장본은 nil)
    static func activeDays(_ state: GameState) -> Int {
        let entries: [DayLogEntry] = state.dayLog ?? []
        var dates = Set<String>()
        for entry in entries where entry.correct + entry.wrong > 0 { dates.insert(entry.date) }
        return dates.count
    }

    /// 요청했다고 기록한 새 이력
    static func recordAsk(_ log: ReviewPromptLog, appVersion: String, now: Date) -> ReviewPromptLog {
        ReviewPromptLog(lastAskedAt: now, lastAskedVersion: appVersion, askCount: log.askCount + 1)
    }
}
