import CoreGraphics

// 구구 계단 월드 ↔ 화면 변환 (Unity 직교 카메라 + TableLayout.OrthoSizeFor 이식)
// 가로 9유닛이 늘 보이고(세로로 긴 폰은 위아래가 더 보임), 화면이 9:16 보다 넓으면 세로 16유닛에 맞춘다.
// 카메라 가운데 = (0, centerY). 월드 y 는 위가 +, 화면 y 는 아래가 +.

struct StairsCamera {
    static let minVisibleWidth = 9.0
    static let minVisibleHeight = 16.0

    /// 전체 화면 크기(pt)
    let size: CGSize
    /// 카메라 가운데 높이(월드)
    let centerY: Double

    /// 월드 1유닛의 pt
    var unitPoints: CGFloat {
        min(size.width / CGFloat(Self.minVisibleWidth), size.height / CGFloat(Self.minVisibleHeight))
    }

    /// 화면 반폭·반높이(월드 유닛)
    var halfWidth: Double { Double(size.width / 2 / unitPoints) }
    var halfHeight: Double { Double(size.height / 2 / unitPoints) }

    func point(_ x: Double, _ y: Double) -> CGPoint {
        CGPoint(x: size.width / 2 + CGFloat(x) * unitPoints,
                y: size.height / 2 - CGFloat(y - centerY) * unitPoints)
    }

    func screenX(_ x: Double) -> CGFloat { size.width / 2 + CGFloat(x) * unitPoints }

    /// 화면 x → 칸. 화면이 9유닛보다 넓어도 실제 발판 사이 경계로 나눈다
    func lane(atScreenX x: CGFloat) -> Int {
        let half = StairsStage.laneSpacing * 0.5
        if x < screenX(-half) { return 0 }
        if x < screenX(half) { return 1 }
        return 2
    }

    /// 칸 가운데의 화면 x — 자동 정답(스크린숏용)이 실제 탭 경로로 누를 자리
    func laneCenterX(_ lane: Int) -> CGFloat { screenX(StairsStage.laneX(lane)) }
}
