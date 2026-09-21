import SwiftUI

// 스파크라인 (Sparkline.tsx 이식) — 최근 추이 미니 라인차트

struct Sparkline: View {
    var data: [Int]
    var color: Color = .gg.accent

    var body: some View {
        GeometryReader { geo in
            let pts = points(in: geo.size)
            ZStack {
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
                }
            }
        }
    }

    private func points(in size: CGSize) -> [CGPoint] {
        guard data.count >= 2 else { return [] }
        let maxV = Double(data.max() ?? 100)
        let minV = Double(data.min() ?? 0)
        let range = max(1, maxV - minV)
        let stepX = size.width / Double(data.count - 1)
        return data.enumerated().map { i, v in
            let y = size.height - (Double(v) - minV) / range * size.height
            return CGPoint(x: Double(i) * stepX, y: y)
        }
    }
}
