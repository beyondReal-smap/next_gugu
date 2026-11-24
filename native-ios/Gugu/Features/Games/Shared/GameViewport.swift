import SwiftUI

// 게임 화면 좌표 — Unity 시험판의 캔버스(CanvasScaler 1080×1920, match width)를 그대로 옮긴다.
// 설계 좌표 1px = 화면 폭 / 1080 pt. 위치는 Unity 처럼 기준점(안전 영역 위 가운데·가운데, 화면 가운데)에서의
// 오프셋(오른쪽 +x, 위 +y)으로 적고, 여기서 화면 좌표(왼쪽 위 원점, 아래 +y, pt)로 바꾼다.
// 모든 좌표는 안전 영역을 무시한 전체 화면 기준이다(배경·먹구름·결과 창 어두운 막은 시스템 영역까지 덮음).

struct GameViewport: Equatable {
    /// Unity 기준 해상도 폭(설계 px)
    static let referenceWidth: CGFloat = 1080

    /// 전체 화면 크기(pt)
    let size: CGSize
    /// 안전 영역(pt) — 상태 표시줄·홈 표시줄을 뺀 곳
    let safeArea: EdgeInsets

    /// 설계 px 하나가 몇 pt 인지
    var unit: CGFloat { size.width / Self.referenceWidth }

    var safeRect: CGRect {
        CGRect(x: safeArea.leading, y: safeArea.top,
               width: size.width - safeArea.leading - safeArea.trailing,
               height: size.height - safeArea.top - safeArea.bottom)
    }

    func px(_ value: CGFloat) -> CGFloat { value * unit }

    func px(_ size: CGSize) -> CGSize { CGSize(width: size.width * unit, height: size.height * unit) }

    /// 안전 영역 위 가운데 기준(Unity 앵커 (0.5, 1)) — 배지 줄·문제 판
    func safeTop(x: CGFloat, y: CGFloat) -> CGPoint {
        CGPoint(x: safeRect.midX + x * unit, y: safeRect.minY - y * unit)
    }

    /// 안전 영역 아래 가운데 기준(Unity 앵커 (0.5, 0)) — B 카드·안내 글자, 옷장 카드·버튼 (y 는 위로 +)
    func safeBottom(x: CGFloat, y: CGFloat) -> CGPoint {
        CGPoint(x: safeRect.midX + x * unit, y: safeRect.maxY - y * unit)
    }

    /// 안전 영역 가운데 기준(Unity 앵커 (0.5, 0.5)) — 알림·"잡혔어요!"
    func safeCenter(x: CGFloat, y: CGFloat) -> CGPoint {
        CGPoint(x: safeRect.midX + x * unit, y: safeRect.midY - y * unit)
    }

    /// 전체 화면 가운데 기준 — 쉬는 창·결과 창(Unity 에서 안전 영역 밖 캔버스에 둔 것)
    func screenCenter(x: CGFloat, y: CGFloat) -> CGPoint {
        CGPoint(x: size.width / 2 + x * unit, y: size.height / 2 - y * unit)
    }

    /// 설계 px 로 적은 상자(가운데·크기)의 화면 사각형
    func rect(center: CGPoint, designSize: CGSize) -> CGRect {
        let s = px(designSize)
        return CGRect(x: center.x - s.width / 2, y: center.y - s.height / 2, width: s.width, height: s.height)
    }
}

extension View {
    /// 화면 좌표의 가운데·pt 크기로 놓는다 (전체 화면 ZStack 안에서)
    func placed(at center: CGPoint, size: CGSize) -> some View {
        frame(width: size.width, height: size.height).position(center)
    }
}
