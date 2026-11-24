import Foundation

// 섬 시트 모델 — 단(또는 섞어서) 하나를 고른 뒤 배우기·풀기·놀기 타일에 쓰는 값 (계획서 redesign-v1 2-7).
// 풀기 타일 목록은 Modes.options(table:) 그대로다 — 모드 목록을 바꾸지 않아 업적 「모드 탐험가」 기준도 그대로.

/// 섬 시트에서 고르는 연습 방법
enum IslandMethod: Equatable {
    /// ① 배우기 "표 보고 외우기"
    case tableStudy
    /// ② 풀기 — 학습 모드 하나
    case mode(GameMode)
}

enum IslandPlan {
    /// "추천" 딱지 — 약한 단이거나 처음 가는 단(별 0, 최근 기록 0)이면 표 보고 외우기, 아니면 차근차근
    static func recommended(table: Int, stars: Int, skill: TableSkill) -> IslandMethod {
        precondition(skill.table == table, "다른 단의 실력: \(skill.table) ≠ \(table)")
        let firstVisit = stars == 0 && skill.attempts == 0
        return skill.status == .weak || firstVisit ? .tableStudy : .mode(.practice)
    }

    /// 타일에 그릴 예시 식 — 단이면 단 × 3, 섞어서(nil)면 7 × 8
    static func sample(table: Int?) -> Problem {
        guard let table else { return Problem(a: 7, b: 8) }
        return Problem(a: table, b: 3)
    }

    /// OX 퀴즈 타일이 보여 줄 틀린 답 — 곱 + 1 ("5 × 3 = 16" 과 O·X)
    static func oxShown(_ sample: Problem) -> Int { sample.a * sample.b + 1 }

    /// 배우기 타일의 식 칩 하나 — 단 × 2 (칩을 하나로 줄여 긴 수에서도 넘치지 않게, 계획서 3-0)
    static func studyChip(table: Int) -> Problem { Problem(a: table, b: 2) }
}
