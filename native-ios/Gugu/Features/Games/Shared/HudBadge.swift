import SwiftUI

// 위쪽 아이콘 배지 (Unity 시험판 HudBadge.cs 이식): 크림 알약 + 왼쪽 아이콘 + 숫자(또는 막대).
// "계단 12" 같은 글자 대신 그림으로 읽히게 하고, 값이 바뀌면 통통 튀어 "방금 올랐다"를 보여 준다.
// 상태(HudBadgeState)는 엔진이 프레임마다 update 하고, 뷰(HudBadgeView)는 그 값을 그리기만 한다.

struct HudBadgeState: Equatable {
    /// 알약 높이(설계 px) — 배지 폭은 이 2배 이상이어야 아이콘 옆 숫자 칸이 남음
    static let height: CGFloat = 110
    static let popSec = 0.28

    private(set) var value: Int?
    /// 막대형(A 먹구름 거리): 0~1
    private(set) var fill: Double = 0
    /// 위험(빨강) 동안 계속 두근거림
    private(set) var danger = false
    private var pop: Double = 0
    private var pulse: Double = 0

    /// 새 판: 통통 없이 값만 맞춤
    mutating func reset(_ value: Int?) {
        self.value = value
        fill = 0
        danger = false
        pop = 0
        pulse = 0
    }

    mutating func setValue(_ newValue: Int) {
        guard value != newValue else { return }
        let first = value == nil
        value = newValue
        if !first { pop = 1 }
    }

    mutating func setBar(fill: Double, danger: Bool) {
        self.fill = min(1, max(0, fill))
        setDanger(danger)
    }

    /// 숫자형도 위험 표시(B 남은 10초): 숫자를 빨갛게 + 두근거림
    mutating func setDanger(_ on: Bool) {
        danger = on
        if !on { pulse = 0 }
    }

    mutating func update(_ dt: Double) {
        pop = max(0, pop - dt / Self.popSec)
        if danger { pulse += dt }
    }

    /// 아이콘·숫자 크기 배율(커졌다 제자리 + 위험 두근거림)
    var scale: CGFloat {
        var s = 1 + 0.18 * sin(pop * .pi)
        if danger { s *= 1 + 0.06 * abs(sin(pulse * 7)) }
        return CGFloat(s)
    }
}

struct HudBadgeView: View {
    enum Content: Equatable {
        case number
        case bar
    }

    let state: HudBadgeState
    /// 아이콘 그림 파일 이름(GameArt)
    let icon: String
    /// 배지 폭(설계 px)
    let width: CGFloat
    var content: Content = .number
    /// 캐릭터 그림처럼 여백이 큰 정사각형 그림은 키워서 다른 아이콘과 크기를 맞춤
    var iconScale: CGFloat = 1
    /// 설계 px → pt
    let unit: CGFloat

    private static let iconSize: CGFloat = 124   // 알약보다 살짝 크게 — 아이콘이 테두리 밖으로 조금 나와 또렷함
    private static let barHeight: CGFloat = 38

    var body: some View {
        let h = HudBadgeState.height
        let left = -width / 2
        // 아이콘 오른쪽 ~ 오른쪽 끝 반원 안쪽까지가 숫자(또는 막대) 자리
        let x0 = left + h + 2
        let x1 = width / 2 - h * 0.3
        let scale = state.scale
        ZStack {
            PillBackground()
                .frame(width: width * unit, height: h * unit)
                .scaleEffect(content == .bar && state.danger ? scale : 1)
            GameArt.image(icon)
                .resizable()
                .scaledToFit()
                .frame(width: Self.iconSize * iconScale * unit, height: Self.iconSize * iconScale * unit)
                .scaleEffect(scale)
                .offset(x: (left + h / 2) * unit)
            switch content {
            case .number:
                Text(state.value.map(String.init) ?? "")
                    .font(.suiteNum(.extrabold, 66 * unit))
                    .foregroundStyle(state.danger ? GamePalette.wrong : GamePalette.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(34.0 / 66.0)   // 세 자리(별 100+)는 칸에 맞게 줄임
                    .frame(width: (x1 - x0) * unit, height: h * unit)
                    .scaleEffect(scale)
                    .offset(x: (x0 + x1) / 2 * unit, y: -2 * unit)
            case .bar:
                bar(width: (x1 - x0) * unit)
                    .offset(x: (x0 + x1) / 2 * unit)
            }
        }
        .frame(width: width * unit, height: h * unit)
        .allowsHitTesting(false)
    }

    private func bar(width: CGFloat) -> some View {
        let height = Self.barHeight * unit
        return ZStack(alignment: .leading) {
            PillBackground(color: GamePalette.ink)
                .opacity(0.22)
            if state.fill > 0 {
                PillBackground(color: state.danger ? GamePalette.wrong : GamePalette.correct)
                    .frame(width: width * state.fill)
            }
        }
        .frame(width: width, height: height)
    }
}
