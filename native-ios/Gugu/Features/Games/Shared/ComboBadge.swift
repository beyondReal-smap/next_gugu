import SwiftUI

// 연속 정답 불꽃 배지 "×N" (Unity 시험판 ComboBadge.cs 이식) — 2연속부터 캐릭터(A)·몬스터(B) 옆에 붙어 다닌다.
// 숫자가 오를 때마다 통통 튀고, 5의 배수(별 보너스)에서는 더 크게 튄다. 오답이면 쪼그라들며 사라진다.

struct ComboState: Equatable {
    static let showFrom = 2
    static let popSec = 0.32
    static let hideSec = 0.2

    private(set) var streak = 0
    /// 화면 좌표(pt) — 게임이 프레임마다 캐릭터 옆으로 옮김
    private(set) var position: CGPoint = .zero
    /// 그리는 중인지(쪼그라드는 동안 포함)
    private(set) var visible = false
    private var pop: Double = 0
    private var popAmount: Double = 0
    /// 1 = 완전히 숨김
    private var hide: Double = 1
    private var time: Double = 0

    mutating func reset() {
        self = ComboState()
    }

    mutating func setStreak(_ newStreak: Int) {
        guard newStreak != streak else { return }
        let up = newStreak > streak
        streak = newStreak
        guard newStreak >= Self.showFrom else { return }  // 숨김은 update 에서 쪼그라들며
        visible = true
        hide = 0
        if up {
            pop = 1
            popAmount = StarRules.isComboMilestone(newStreak) ? 0.55 : 0.28
        }
    }

    mutating func follow(_ point: CGPoint) {
        guard visible else { return }  // 숨어 있으면 계산하지 않음
        position = point
    }

    mutating func update(_ dt: Double) {
        guard visible else { return }
        time += dt
        if streak < Self.showFrom {
            hide = min(1, hide + dt / Self.hideSec)
            if hide >= 1 {
                visible = false
                return
            }
        }
        pop = max(0, pop - dt / Self.popSec)
    }

    var scale: CGFloat { CGFloat((1 + popAmount * sin(pop * .pi)) * (1 - hide)) }
    /// 불꽃이 살랑(도)
    var wobbleDegrees: Double { sin(time * 6) * 4 }
}

struct ComboBadgeView: View {
    let state: ComboState
    let unit: CGFloat

    private static let size: CGFloat = 180
    /// 불꽃 그림 가운데 노란 원의 중심(그림 높이 대비, 실측 -0.078) — "×N" 을 원 안에
    private static let circleCenterY: CGFloat = -0.078

    var body: some View {
        if state.visible {
            let s = Self.size * unit
            ZStack {
                GameArt.image("combo_flame")
                    .resizable()
                    .scaledToFit()
                    .frame(width: s, height: s)
                Text(verbatim: "×\(state.streak)")
                    .font(.suiteNum(.extrabold, 62 * unit))
                    .foregroundStyle(GamePalette.ink)
                    .offset(y: -Self.circleCenterY * s)
            }
            .frame(width: s, height: s)
            .scaleEffect(state.scale)
            .rotationEffect(.degrees(-state.wobbleDegrees))
            .position(state.position)
            .allowsHitTesting(false)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("\(state.streak)연속 정답"))
        }
    }
}
