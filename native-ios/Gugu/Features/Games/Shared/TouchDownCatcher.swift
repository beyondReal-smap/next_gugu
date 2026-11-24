import SwiftUI
import UIKit

// 닿는 순간 반응하는 멀티터치 입력 (Unity 시험판 TapInput.cs 대응)
// SwiftUI 탭 제스처는 손가락을 뗄 때 반응하고, 두 손가락 번갈아 치기·아주 짧은 톡 치기를 놓칠 수 있다.
// UIKit touchesBegan 은 손가락이 닿는 사건마다 한 번씩 불리므로, 손가락마다 한 번 화면 좌표를 넘긴다.
// 게임은 받은 좌표를 큐에 쌓았다가 다음 프레임에 꺼내 처리한다(입력 → 시간 → 그리기 순서).

struct TouchDownCatcher: UIViewRepresentable {
    /// 손가락이 닿은 곳(이 뷰 좌표, pt). 손가락마다 한 번
    let onTouchDown: (CGPoint) -> Void

    func makeUIView(context: Context) -> TouchDownView {
        let view = TouchDownView()
        view.onTouchDown = onTouchDown
        return view
    }

    func updateUIView(_ view: TouchDownView, context: Context) {
        view.onTouchDown = onTouchDown
    }

    final class TouchDownView: UIView {
        var onTouchDown: ((CGPoint) -> Void)?

        override init(frame: CGRect) {
            super.init(frame: frame)
            isMultipleTouchEnabled = true
            backgroundColor = .clear
            isAccessibilityElement = false
        }

        @available(*, unavailable)
        required init?(coder: NSCoder) {
            fatalError("코드로만 만든다")
        }

        override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
            for touch in touches {
                onTouchDown?(touch.location(in: self))
            }
        }
    }
}
