import CoreGraphics

// 구구 몬스터 월드 ↔ 화면 변환 (Unity 직교 카메라 + TableLayout.OrthoSizeFor — 카메라는 (0, 0) 고정)
// 가로 9유닛이 늘 보이고(세로로 긴 폰은 위아래가 더 보임), 화면이 9:16 보다 넓으면 세로 16유닛에 맞춘다.
// 월드 y 는 위가 +, 화면 y 는 아래가 +. (구구 계단 StairsCamera 와 같은 계산에서 카메라 이동만 뺀 것)

struct MonsterCamera {
    static let minVisibleWidth = 9.0
    static let minVisibleHeight = 16.0

    /// 전체 화면 크기(pt)
    let size: CGSize

    /// 월드 1유닛의 pt
    var unitPoints: CGFloat {
        min(size.width / CGFloat(Self.minVisibleWidth), size.height / CGFloat(Self.minVisibleHeight))
    }

    /// 화면 반폭·반높이(월드 유닛)
    var halfWidth: Double { Double(size.width / 2 / unitPoints) }
    var halfHeight: Double { Double(size.height / 2 / unitPoints) }

    func point(_ x: Double, _ y: Double) -> CGPoint {
        CGPoint(x: size.width / 2 + CGFloat(x) * unitPoints,
                y: size.height / 2 - CGFloat(y) * unitPoints)
    }
}
