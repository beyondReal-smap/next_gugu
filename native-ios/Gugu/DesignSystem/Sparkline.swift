import SwiftUI

// 스파크라인 (Sparkline.tsx 이식) — 최근 추이 미니 라인차트

struct Sparkline: View {
    var data: [Int]
    var color: Color = .gg.accent
    /// 고정 y축 범위. nil 이면 데이터의 min~max 에 맞춘다.
    ///
    /// 정확도처럼 눈금 자체에 의미가 있는 값은 반드시 고정해야 한다. 자동 스케일은
    /// 90% 로 꾸준한 기록을 바닥에 붙여 0% 처럼 보이게 하고, 88·90·92 의 미세한
    /// 차이를 천장까지 과장한다 — 둘 다 사실과 다른 인상을 준다.
    var lowerBound: Double?
    var upperBound: Double?
    /// 해석 기준선 (예: 정확도 50%). 범위 안에 있을 때만 그린다.
    var baseline: Double?
    /// 마지막 값에 점을 찍어 "현재"를 집어 준다
    var markLast: Bool = false

    var body: some View {
        GeometryReader { geo in
            let pts = points(in: geo.size)
            ZStack {
                if let y = baselineY(in: geo.size) {
                    Path { p in
                        p.move(to: CGPoint(x: 0, y: y))
                        p.addLine(to: CGPoint(x: geo.size.width, y: y))
                    }
                    .stroke(Color.gg.border, style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                }
                if pts.count >= 2 {
                    // 채움 영역
                    Path { p in
                        p.move(to: CGPoint(x: pts[0].x, y: geo.size.height))
                        pts.forEach { p.addLine(to: $0) }
                        p.addLine(to: CGPoint(x: pts.last!.x, y: geo.size.height))
                        p.closeSubpath()
                    }
                    .fill(color.opacity(0.12))
                    // 라인
                    Path { p in
                        p.move(to: pts[0])
                        pts.dropFirst().forEach { p.addLine(to: $0) }
                    }
                    .stroke(color, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                    if markLast, let last = pts.last {
                        Circle().fill(color).frame(width: 7, height: 7).position(last)
                    }
                }
            }
        }
    }

    /// 실제로 쓰는 y축 범위 — 고정값이 없으면 데이터에 맞춘다
    private var bounds: (low: Double, high: Double) {
        let low = lowerBound ?? Double(data.min() ?? 0)
        let high = upperBound ?? Double(data.max() ?? 100)
        return (low, max(low + 1, high))
    }

    private func y(for value: Double, in size: CGSize) -> Double {
        let (low, high) = bounds
        let ratio = (value - low) / (high - low)
        return size.height - min(max(ratio, 0), 1) * size.height
    }

    private func baselineY(in size: CGSize) -> Double? {
        guard let baseline else { return nil }
        let (low, high) = bounds
        guard baseline > low, baseline < high else { return nil }
        return y(for: baseline, in: size)
    }

    private func points(in size: CGSize) -> [CGPoint] {
        guard data.count >= 2 else { return [] }
        let stepX = size.width / Double(data.count - 1)
        return data.enumerated().map { index, value in
            CGPoint(x: Double(index) * stepX, y: y(for: Double(value), in: size))
        }
    }
}
