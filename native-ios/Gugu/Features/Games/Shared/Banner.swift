import SwiftUI

// 화면에 잠깐 뜨는 큰 알림 글자 (Unity 시험판 Banner.cs 이식) — A 보물·높이 구간·신기록, B 도감 등록·새 몬스터.
// 떠 있는 동안 온 알림은 앞 알림이 끝난 뒤 보여 준다. 대기는 최신 1개 — 아주 빨리 풀 때 지난 알림이 줄줄이 밀려 나오지 않게.

struct BannerState: Equatable {
    static let showSec = 1.4

    /// 지금 떠 있는 알림(없으면 nil)
    private(set) var text: String?
    private var left: Double = 0
    private var pending: String?

    mutating func show(_ message: String) {
        if left > 0 {
            pending = message
            return
        }
        text = message
        left = Self.showSec
    }

    /// 판이 끝나거나 새 판: 떠 있는 것·기다리는 것 모두 지움
    mutating func clear() {
        text = nil
        left = 0
        pending = nil
    }

    mutating func update(_ dt: Double) {
        guard left > 0 else { return }
        left -= dt
        guard left <= 0 else { return }
        left = 0
        text = nil
        if let next = pending {
            pending = nil
            show(next)
        }
    }
}

struct BannerView: View {
    let state: BannerState
    /// 글자 크기(설계 px)
    var fontSize: CGFloat = 84
    let unit: CGFloat

    var body: some View {
        if let text = state.text {
            OutlinedText(text: text, font: .suite(.extrabold, fontSize * unit), color: GamePalette.gold, width: 6 * unit)
                .allowsHitTesting(false)
        }
    }
}
