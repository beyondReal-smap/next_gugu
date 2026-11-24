import Foundation

// 오늘의 섬 — 홈 말풍선, 지도 탭 강조, 보호자 리포트 권유가 같이 쓰는 "오늘 할 단" 하나 (계획서 redesign-v1 2-6).
// 저장하지 않고 상태에서 매번 계산한다 — 아이가 그 단을 연습해 잘해요가 되면 말풍선이 다음 섬으로 넘어가 진도가 보인다.

enum TodayIsland: Equatable {
    enum Reason: Equatable {
        /// 최근 기록이 약해요인 단 — 리포트 한마디 weak(first:) 와 같은 단
        case weak
        /// 로드맵 순서의 다음 단
        case next
    }

    case table(Int, reason: Reason)
    /// 남은 단이 모두 별 3개 — 무지개 "섞어서"
    case mixed

    /// 1) 약한 단이 있으면 가장 약한 단.
    /// 2) 없으면 로드맵 순서(Hints.roadmapTables)에서 최근 기록이 잘해요인 단을 뺀 뒤 별이 가장 적은 단(같으면 로드맵 순).
    ///    게임만 하는 아이는 별이 생기지 않아, 잘해요 제외가 없으면 영원히 2단에 머문다. 기록이 쌓이기 전에는 예전 규칙과 같은 단이 나온다.
    /// 3) 남은 단이 모두 별 3개면(또는 남은 단이 없으면) 섞어서.
    /// skills 에 없는 단은 기록 없음으로 본다
    static func pick(mastery: [Int: TableMastery], skills: [Int: TableSkill]) -> TodayIsland {
        if let weakest = TableSkills.weak(skills).first { return .table(weakest, reason: .weak) }
        let candidates = Hints.roadmapTables.filter { skills[$0]?.status != .strong }
        func stars(_ table: Int) -> Int { mastery[table]?.stars ?? 0 }
        guard let fewest = candidates.map(stars).min(), fewest < 3,
              let next = candidates.first(where: { stars($0) == fewest })
        else { return .mixed }
        return .table(next, reason: .next)
    }
}
