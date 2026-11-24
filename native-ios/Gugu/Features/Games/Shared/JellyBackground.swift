import SwiftUI

// 둥근 젤리 판·알약 (Unity 시험판 JellyUi.cs·HudBadge.MakePill 이식)
// 그림을 9분할로 늘려 어떤 크기에서도 모서리가 찌그러지지 않게 하고, 색은 곱해 입힌다(무채색 몸 + 남색 외곽선).
// SwiftUI 의 resizable(capInsets:) 는 테두리를 그림 원래 크기로 그리므로, 테두리를 원하는 배율로 그리려고
// "크기 ÷ 배율" 판에 9분할을 한 뒤 배율만큼 키운다(scaleEffect) — Unity 의 pixelsPerUnitMultiplier 와 같은 효과.

/// 젤리 판(card_jelly 271×211) — 쉬는 창·결과 창·버튼
struct JellyBackground: View {
    /// 테두리 원본 px(Unity spriteBorder 왼100·아래72·오른70·위75)
    static let capInsets = EdgeInsets(top: 75, leading: 100, bottom: 72, trailing: 70)
    /// 아래 입술 + 외곽선 높이(원본 px) — 창 안 내용은 아래 끝에서 이만큼(× 배율) 위에 둬야 입술에 걸리지 않음
    static let lipHeight: CGFloat = 72
    /// 입술 때문에 앞면 가운데가 그림 가운데보다 위 — 버튼 글자를 이만큼(원본 px × 배율) 올린다
    static let labelLift: CGFloat = 20

    let color: Color
    /// 원본 px 하나를 몇 pt 로 그릴지 = Unity JellyUi 의 scale × 설계 배율(GameViewport.unit)
    let pointsPerPixel: CGFloat

    var body: some View {
        GeometryReader { geo in
            let k = pointsPerPixel
            Image(GameArt.name("card_jelly"))
                .resizable(capInsets: Self.capInsets, resizingMode: .stretch)
                .colorMultiply(color)
                .frame(width: geo.size.width / k, height: geo.size.height / k)
                .scaleEffect(k, anchor: .topLeading)
        }
        .accessibilityHidden(true)
    }
}

/// 크림 알약(badge_pill 298×108, 양 끝 반원 54px) — 배지·[⏸]·막대. 반원이 정확히 높이의 절반이 되게 그린다
struct PillBackground: View {
    static let capInsets = EdgeInsets(top: 0, leading: 54, bottom: 0, trailing: 54)
    static let pixelHeight: CGFloat = 108

    var color: Color = .white

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            let h = geo.size.height
            let k = h / Self.pixelHeight
            // 높이보다 좁으면(막대가 거의 빈 때) 동그라미를 가로로 눌러 그림 — Unity 9분할이 테두리를 줄이는 것과 같은 모양
            let squeeze = w < h ? w / h : 1
            Image(GameArt.name("badge_pill"))
                .resizable(capInsets: Self.capInsets, resizingMode: .stretch)
                .colorMultiply(color)
                .frame(width: max(w, h) / k, height: Self.pixelHeight)
                .scaleEffect(x: k * squeeze, y: k, anchor: .topLeading)
        }
        .accessibilityHidden(true)
    }
}
