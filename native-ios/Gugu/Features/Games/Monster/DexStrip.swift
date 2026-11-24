import SwiftUI

// 구구 몬스터 도감 띠 (Unity 시험판 DexStrip.cs 이식) — 위쪽 배지 줄 아래 8칸(MonsterKind 순서).
// - 물리친 종: 제 색
// - 해금됐지만 아직 못 물리친 종: 진한 실루엣("저 몬스터는 누구지?")
// - 아직 해금 안 된 종: 옅은 실루엣 + 자물쇠
// 처음 물리치면 그 칸이 통통 튀며 색이 채워진다. 상태(DexStripState)는 엔진이 update 하고 뷰는 그리기만 한다.

struct DexStripState: Equatable {
    static let popSec = 0.5

    enum Look: Equatable {
        case seen
        case silhouette
        case locked
    }

    private(set) var seenMask = 0
    private(set) var total = 0
    private(set) var popKind: MonsterKind?
    private var pop: Double = 0

    /// 저장소 값을 그대로 보여 줌(해금되면 자물쇠도 풀림)
    mutating func show(seenMask: Int, total: Int) {
        self.seenMask = seenMask
        self.total = total
    }

    /// 처음 물리친 종: 칸이 통통 튐(색은 show 가 채움). 앞 칸 튐이 덜 끝났으면 그 칸은 제자리로
    mutating func reveal(_ kind: MonsterKind) {
        popKind = kind
        pop = 1
    }

    mutating func resetPop() {
        popKind = nil
        pop = 0
    }

    mutating func update(_ dt: Double) {
        guard popKind != nil else { return }
        pop = max(0, pop - dt / Self.popSec)
        if pop <= 0 { popKind = nil }
    }

    func look(_ kind: MonsterKind) -> Look {
        if MonsterDex.isSeen(kind, seenMask: seenMask) { return .seen }
        return MonsterDex.isUnlocked(kind, totalDefeated: total) ? .silhouette : .locked
    }

    func scale(_ kind: MonsterKind) -> CGFloat {
        kind == popKind ? CGFloat(1 + 0.4 * sin(pop * .pi)) : 1
    }
}

struct DexStripView: View {
    let state: DexStripState
    /// 설계 px → pt
    let unit: CGFloat

    static let height: CGFloat = 92
    private static let slot: CGFloat = 80
    private static let gap: CGFloat = 14
    /// 몬스터 그림은 둘레 여백이 있어 칸보다 살짝 크게
    private static let iconScale: CGFloat = 1.15
    private static let lockSize: CGFloat = 36
    /// Ink(0.17, 0.14, 0.31) — 실루엣 색, 투명도는 따로
    private static let ink = Color(red: 0.17, green: 0.14, blue: 0.31)
    private static let slotColor = Color(red: 1, green: 0.97, blue: 0.88).opacity(0.92)
    /// 숲 위 옅은 어둠
    private static let backColor = Color(red: 0.10, green: 0.16, blue: 0.12)

    static var width: CGFloat { CGFloat(MonsterDex.kinds) * slot + CGFloat(MonsterDex.kinds - 1) * gap + 36 }

    var body: some View {
        ZStack {
            PillBackground(color: Self.backColor)
                .opacity(0.5)
            ForEach(MonsterKind.allCases, id: \.self) { kind in
                slot(kind)
                    .offset(x: (CGFloat(kind.rawValue) - CGFloat(MonsterDex.kinds - 1) / 2) * (Self.slot + Self.gap) * unit)
            }
        }
        .frame(width: Self.width * unit, height: Self.height * unit)
        .allowsHitTesting(false)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("몬스터 도감 \(MonsterDex.seenCount(state.seenMask))/\(MonsterDex.kinds)"))
    }

    private func slot(_ kind: MonsterKind) -> some View {
        let look = state.look(kind)
        let s = Self.slot * unit
        let icon = GameArt.image(GameArt.monsterFile(kind))
            .resizable()
            .scaledToFit()
            .frame(width: s * Self.iconScale, height: s * Self.iconScale)
            .offset(y: 2 * unit)
        return ZStack {
            Circle()
                .fill(Self.slotColor)
            switch look {
            case .seen:
                icon
            case .silhouette:
                icon.colorMultiply(Self.ink).opacity(0.9)
            case .locked:
                icon.colorMultiply(Self.ink).opacity(0.4)
                GameArt.image("icon_lock")
                    .resizable()
                    .scaledToFit()
                    .frame(width: Self.lockSize * unit, height: Self.lockSize * unit)
                    .offset(x: s * 0.3, y: s * 0.28)
            }
        }
        .frame(width: s, height: s)
        .scaleEffect(state.scale(kind))
    }
}
