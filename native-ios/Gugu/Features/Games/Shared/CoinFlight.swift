import SwiftUI

// 별 코인이 정답 자리(발판·몬스터)에서 위쪽 별 배지로 휘어 날아간다 (Unity 시험판 CoinFlight.cs 이식).
// 좌표는 화면 pt. 도착점(배지 아이콘)은 프레임마다 다시 받아, 배지가 통통 튀거나 화면이 바뀌어도 따라간다.

struct CoinFlightState: Equatable {
    /// 한 번에 너무 많이 날리면 화면이 어지러움(나머지는 배지가 한 번에 맞춤)
    static let maxCoinsPerBurst = 8
    static let flySec = 0.6
    static let staggerSec = 0.08
    /// 코인 크기(설계 px)
    static let coinSize: CGFloat = 86
    /// 여러 개일 때 경로를 좌우로 벌리는 간격(설계 px)
    static let spread: CGFloat = 54

    struct Coin: Equatable {
        let from: CGPoint
        let control: CGPoint
        /// 비행 진행률(0~1). 0 미만 = 출발 대기
        var t: Double
        /// 이번 묶음에서 몇 번째인지 — 닿는 음이 조금씩 높아짐
        let index: Int
    }

    private(set) var coins: [Coin] = []
    /// 마지막 프레임의 도착점
    private(set) var target: CGPoint = .zero

    var inFlight: Int { coins.count }

    mutating func clear() { coins.removeAll() }

    /// from 에서 count 개(최대 8개)가 to 로 날아간다. unit = 설계 px → pt
    mutating func launch(from: CGPoint, to: CGPoint, count: Int, unit: CGFloat) {
        precondition(count > 0, "날릴 코인은 1개 이상: \(count)")
        let n = min(count, Self.maxCoinsPerBurst)
        target = to
        for i in 0..<n {
            // 휘는 경로: 출발점 위쪽으로 솟았다가 배지로. 여러 개면 좌우로 조금씩 벌림
            let offset = (CGFloat(i) - CGFloat(n - 1) / 2) * Self.spread * unit
            let control = CGPoint(x: from.x + (to.x - from.x) * 0.15 + offset,
                                  y: from.y + (to.y - from.y) * 0.7)
            // t 는 진행률이라 출발 대기(초)를 진행률로 바꿔 음수로 둠
            coins.append(Coin(from: from, control: control, t: -Double(i) * Self.staggerSec / Self.flySec, index: i))
        }
    }

    /// dt 만큼 날리고, 이번에 배지에 닿은 코인의 묶음 순번을 돌려준다
    mutating func update(_ dt: Double, target: CGPoint) -> [Int] {
        self.target = target
        guard !coins.isEmpty else { return [] }
        var arrived: [Int] = []
        var flying: [Coin] = []
        for var coin in coins {
            coin.t += dt / Self.flySec
            if coin.t >= 1 {
                arrived.append(coin.index)
            } else {
                flying.append(coin)
            }
        }
        coins = flying
        return arrived
    }

    /// 코인의 지금 위치·크기 배율(대기 중이면 nil)
    func pose(_ coin: Coin) -> (point: CGPoint, scale: CGFloat)? {
        guard coin.t >= 0 else { return nil }
        let t = coin.t * coin.t * (3 - 2 * coin.t)   // 천천히 떠올라 빨라졌다가 배지에 쏙
        func lerp(_ a: CGPoint, _ b: CGPoint) -> CGPoint {
            CGPoint(x: a.x + (b.x - a.x) * t, y: a.y + (b.y - a.y) * t)
        }
        let point = lerp(lerp(coin.from, coin.control), lerp(coin.control, target))
        let grow = min(1, coin.t * 6)
        return (point, CGFloat(grow * (1.15 + (0.75 - 1.15) * coin.t)))
    }
}

struct CoinFlightView: View {
    let state: CoinFlightState
    let unit: CGFloat

    var body: some View {
        let size = CoinFlightState.coinSize * unit
        ZStack {
            ForEach(Array(state.coins.enumerated()), id: \.offset) { _, coin in
                if let pose = state.pose(coin) {
                    GameArt.image("icon_coin")
                        .resizable()
                        .scaledToFit()
                        .frame(width: size, height: size)
                        .scaleEffect(pose.scale)
                        .position(pose.point)
                }
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
