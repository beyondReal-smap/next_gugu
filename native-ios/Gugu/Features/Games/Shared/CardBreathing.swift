import SwiftUI

// 그림 숨쉬기 — 홈 게임 카드 그림·옷장 큰 구구가 천천히 위아래로 뜨고 살짝 커졌다 작아진다(공식은 Motion.cardBreath).
// 동작 줄이기면 제자리에 멈춘다. paused: 다른 전체 화면이 덮고 있을 때 등 — 보이지 않는 동안 다시 그리지 않게.

private struct CardBreathing: ViewModifier {
    let phase: Double
    let paused: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        // 느린 움직임이라 초당 30번이면 충분하다(게임 화면 뒤에서 일을 덜 하게)
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: paused || reduceMotion)) { context in
            let b = reduceMotion ? (lift: 0.0, scale: 1.0)
                : Motion.cardBreath(time: context.date.timeIntervalSinceReferenceDate, phase: phase)
            content
                .scaleEffect(b.scale)
                .offset(y: -CGFloat(b.lift))
        }
    }
}

extension View {
    /// 그림 숨쉬기. phase: 나란한 그림끼리 다르게(한 주기 대비)
    func cardBreathing(phase: Double, paused: Bool = false) -> some View {
        modifier(CardBreathing(phase: phase, paused: paused))
    }
}
