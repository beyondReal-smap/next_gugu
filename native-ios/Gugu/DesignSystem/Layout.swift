import SwiftUI

// 레이아웃 보조 모디파이어 — 화면들이 같은 규칙으로 상태바·태블릿 폭·고정 위치 오버레이를 다룬다

extension View {
    /// 스크롤한 콘텐츠가 상태바 글자 밑으로 비치지 않도록 상태바 영역에 배경을 깐다.
    /// 높이 0 짜리 inset 의 배경만 위쪽 안전 영역까지 늘려 그린다 (콘텐츠 위치는 바뀌지 않는다).
    func statusBarBackdrop() -> some View {
        safeAreaInset(edge: .top, spacing: 0) {
            Color.clear
                .frame(height: 0)
                .background {
                    Rectangle()
                        .fill(.ultraThinMaterial)
                        .overlay(Color.gg.bg.opacity(0.78))
                        .ignoresSafeArea(edges: .top)
                }
        }
    }

    /// 태블릿에서 한 줄이 지나치게 길어지지 않게 콘텐츠 폭을 제한하고 가운데 둔다
    func readableWidth(_ max: CGFloat = 640) -> some View {
        frame(maxWidth: max).frame(maxWidth: .infinity)
    }

    /// 레이아웃을 밀지 않고 이 뷰 바로 위에 붙는 오버레이 (정답/오답 표시용).
    /// 정렬 가이드는 조건부(if) 콘텐츠 바깥에 걸어야 overlay 배치에 반영된다.
    func pinnedAbove<Content: View>(
        spacing: CGFloat = 12,
        @ViewBuilder _ content: () -> Content
    ) -> some View {
        overlay(alignment: .top) {
            content()
                .fixedSize()
                .alignmentGuide(.top) { d in d[.bottom] + spacing }
        }
    }

    /// 레이아웃을 밀지 않고 이 뷰 바로 아래에 붙는 오버레이.
    /// 오답 설명·힌트가 나타나도 문제 식이 제자리에 있게 한다 (식 위치가 튀면 아이가 혼란스러워한다).
    func pinnedBelow<Content: View>(
        spacing: CGFloat = 12,
        width: CGFloat,
        @ViewBuilder _ content: () -> Content
    ) -> some View {
        overlay(alignment: .bottom) {
            content()
                .frame(width: width)
                .fixedSize(horizontal: false, vertical: true)
                .alignmentGuide(.bottom) { d in d[.top] - spacing }
        }
    }
}

/// 칩 줄바꿈 레이아웃 — 한 줄에 안 들어가면 다음 줄로 넘긴다(계획서 redesign-v1 3-0: 글자를 줄이거나 자르지 않고 감싼다).
/// 칩 하나가 줄 폭보다 넓으면 줄 폭까지만 제안해 칩 안에서 글이 두 줄로 감기게 한다(넘쳐 나가지 않는다)
struct FlowRow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0, widest: CGFloat = 0
        for sub in subviews {
            let size = sub.sizeThatFits(ProposedViewSize(width: maxWidth, height: nil))
            if x > 0, x + size.width > maxWidth {
                x = 0; y += rowHeight + spacing; rowHeight = 0
            }
            x += size.width + spacing
            widest = max(widest, x - spacing)
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: proposal.width ?? widest, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
        for sub in subviews {
            let size = sub.sizeThatFits(ProposedViewSize(width: bounds.width, height: nil))
            if x > bounds.minX, x + size.width > bounds.maxX {
                x = bounds.minX; y += rowHeight + spacing; rowHeight = 0
            }
            sub.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
