import Foundation

// A 구구 계단의 시간 압박: 아래에서 계속 차오르는 먹구름 (Unity 시험판 Core/RisingCloud.cs 이식)
// 높이 단위는 "계단 칸"(출발 발판 = 0, 한 칸 오르면 +1).
// - 구름 윗면(top)이 캐릭터가 서 있는 칸에 닿으면 잡힘 → 판 끝
// - 오를수록 빨라짐(5초/칸 → 최소 1.8초/칸). 정답 = 한 칸 벌기, 오답 = 정답 공개 0.6초 + 다시 푸는 시간만큼 잃기
// - 너무 멀어지면(maxGap 칸 초과) 빠르게 따라옴 — 빨리 푸는 아이도 압박이 사라지지 않게
// 수치는 아이 관찰 전 초안이다(게임-고도화 docs/AB_PLAYTEST.md §2).

struct RisingCloud {
    /// 시작할 때 구름은 출발 발판 2칸 아래
    static let startGap: Double = 2
    static let maxGap: Double = 3
    static let catchUpFactor: Double = 3
    static let startSecPerStep: Double = 5
    static let minSecPerStep: Double = 1.8
    /// 한 칸 오를 때마다 칸당 시간이 이만큼 줄어듦
    static let speedupPerStep: Double = 0.07

    private(set) var top: Double = -RisingCloud.startGap

    /// 구름이 한 칸 오르는 데 걸리는 시간(초)
    static func secPerStep(standing: Int) -> Double {
        max(minSecPerStep, startSecPerStep - speedupPerStep * Double(max(0, standing)))
    }

    /// 서 있는 칸과 구름 윗면 사이 거리(칸). 0 이하 = 잡힘
    func gap(standing: Int) -> Double {
        Double(standing) - top
    }

    /// dt(초) 동안 차오르고, 이번에 잡혔으면 true. 잡히면 top 은 서 있는 칸에 멈춘다
    @discardableResult
    mutating func tick(_ dt: Double, standing: Int) -> Bool {
        precondition(dt >= 0, "dt 는 0 이상이어야 함: \(dt)")
        precondition(top < Double(standing), "이미 잡힌 구름 (top \(top) ≥ standing \(standing))")
        var speed = 1 / Self.secPerStep(standing: standing)
        if gap(standing: standing) > Self.maxGap { speed *= Self.catchUpFactor }
        top += speed * dt
        if top < Double(standing) { return false }
        top = Double(standing)
        return true
    }
}
