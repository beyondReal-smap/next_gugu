import SwiftUI

// 가상 조이스틱 (Joystick.tsx 이식) — 드래그를 -1..1 벡터로 정규화해 콜백
struct Joystick: View {
    var onMove: (Float, Float) -> Void

    private let base: CGFloat = 118
    private let knob: CGFloat = 52
    private var radius: CGFloat { (base - knob) / 2 }
    private let deadzone: CGFloat = 0.16

    @State private var offset: CGSize = .zero

    var body: some View {
        ZStack {
            Circle()
                .fill(.black.opacity(0.2))
                .overlay(Circle().strokeBorder(.white.opacity(0.3), lineWidth: 1))
                .frame(width: base, height: base)
            Circle()
                .fill(.white.opacity(0.85))
                .frame(width: knob, height: knob)
                .offset(offset)
                .shadow(radius: 4)
        }
        .frame(width: base, height: base)
        .contentShape(Circle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    var dx = value.location.x - base / 2
                    var dy = value.location.y - base / 2
                    let len = sqrt(dx * dx + dy * dy)
                    if len > radius {
                        dx = dx / len * radius
                        dy = dy / len * radius
                    }
                    offset = CGSize(width: dx, height: dy)
                    let nx = dx / radius
                    let ny = dy / radius
                    let mag = sqrt(nx * nx + ny * ny)
                    if mag < deadzone {
                        onMove(0, 0)
                    } else {
                        onMove(Float(nx), Float(ny))
                    }
                }
                .onEnded { _ in
                    offset = .zero
                    onMove(0, 0)
                }
        )
    }
}
