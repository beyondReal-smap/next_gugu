import Foundation
import Observation

// 활성 세션 (SessionProvider.tsx 이식) — 세션 오버레이 표시 트리거

struct ActiveSession: Identifiable, Equatable {
    let mode: GameMode
    let table: Int?   // nil = 혼합
    /// 취약 문제 복습 — 오답 풀에서 가중치 큰 문제부터 출제한다
    var review: Bool = false
    // 재시작 시 뷰 재마운트를 위한 토큰 (web의 key 패턴 대응)
    var token: Int = 0
    var id: String { "\(mode.rawValue)-\(table.map(String.init) ?? "all")-\(review ? "review" : "normal")-\(token)" }
}

@Observable
final class SessionStore {
    var active: ActiveSession?

    func start(_ mode: GameMode, table: Int?) {
        active = ActiveSession(mode: mode, table: table)
    }
    /// 취약 문제 복습 — 학습(practice) 규칙으로 헷갈렸던 문제를 모아 푼다
    func startReview() {
        active = ActiveSession(mode: .practice, table: nil, review: true)
    }
    func end() {
        active = nil
    }
}
