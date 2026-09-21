import Foundation
import Observation

// 활성 세션 (SessionProvider.tsx 이식) — 세션 오버레이 표시 트리거

struct ActiveSession: Identifiable, Equatable {
    let mode: GameMode
    let table: Int?   // nil = 혼합
    // 재시작 시 뷰 재마운트를 위한 토큰 (web의 key 패턴 대응)
    var token: Int = 0
    var id: String { "\(mode.rawValue)-\(table.map(String.init) ?? "all")-\(token)" }
}

@Observable
final class SessionStore {
    var active: ActiveSession?

    func start(_ mode: GameMode, table: Int?) {
        active = ActiveSession(mode: mode, table: table)
    }
    func end() {
        active = nil
    }
}
