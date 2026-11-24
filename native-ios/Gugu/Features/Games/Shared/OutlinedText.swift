import SwiftUI

// 밝은 하늘·숲 위 글자를 또렷하게: 진한 남보라 테두리 (Unity UiFactory.AddOutline — Outline 효과 이식)
// Unity Outline 은 글자를 네 대각선 방향으로 한 번씩 더 그린다. 같은 방식으로 그린다.

struct OutlinedText: View {
    let text: String
    let font: Font
    var color: Color = GamePalette.gold
    var outline: Color = GamePalette.ink
    /// 테두리 거리(pt)
    let width: CGFloat

    var body: some View {
        ZStack {
            ForEach(0..<4, id: \.self) { i in
                Text(text)
                    .font(font)
                    .foregroundStyle(outline)
                    .offset(x: i % 2 == 0 ? width : -width, y: i < 2 ? width : -width)
            }
            Text(text)
                .font(font)
                .foregroundStyle(color)
        }
        .fixedSize()
        // 바깥에서 투명도를 주면(깜빡이는 안내) 테두리·글자가 한 장처럼 함께 옅어지게 — 겹친 자리가 탁해지지 않음
        .compositingGroup()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text)
    }
}
