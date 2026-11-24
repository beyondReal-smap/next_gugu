import Foundation
import QuartzCore
import Observation

// 구구 꾸미기(옷장) 화면 상태 (Unity 시험판 WardrobeScreen.cs 의 규칙 부분 이식)
// 장신구 카드를 누르면 먼저 큰 구구에게 씌워 보여 주고(써 보기 — 별은 안 씀), 아래 큰 버튼으로 사기·쓰기·벗기.
// 한 번 잘못 눌러 별이 빠지지 않게 두 단계로 나눴다. 저장은 PlayRewardsStore(사면 바로 씀).
// 시험판에서 고친 규칙(2026-11-23):
//  ① 사기·쓰기·벗기 직후 0.4초는 버튼 잠금 — 같은 버튼이 곧바로 [벗기]·[쓰기]로 바뀌어, 연타하면 사자마자 벗겨졌음
//  ② 큰 구구는 "카드를 눌러 써 보는 중"이면 고른 것, 사기·쓰기·벗기 뒤에는 저장된 모습 — 벗기면 맨머리(대표님 지적)
//  ③ 별이 모자라면 버튼이 흔들리고 거절음 ④ 다시 열면 쓰고 있는 것이 골라져 있음

@Observable
final class WardrobeModel {
    /// 아래 큰 버튼이 할 일
    enum Action: Equatable {
        case none                 // 아직 아무 카드도 안 고름
        case buy(price: Int)
        case needMore(lack: Int)  // 별이 모자람 — 누르면 흔들림
        case wear
        case takeOff
    }

    /// 사기·쓰기·벗기 직후 버튼 잠금(초)
    static let actionLockSec = 0.4
    /// 큰 구구 통통(초)
    static let popSec = 0.35
    /// 별 부족 흔들림(초)
    static let shakeSec = 0.35

    /// 고른 카드(처음엔 쓰고 있는 것)
    private(set) var selected: Accessory?
    /// 큰 구구가 고른 카드를 써 보는 중인지 — 카드를 누르면 켜지고, 사기·쓰기·벗기를 하면 꺼져 저장된 모습을 보여 줌
    private(set) var tryingOn = false
    private(set) var pop: Double = 0
    private(set) var shake: Double = 0
    private(set) var actionLock: Double = 0
    /// 위 모은 별 배지
    private(set) var starsBadge = HudBadgeState()

    @ObservationIgnored private let rewards: PlayRewardsStore
    @ObservationIgnored private let runsLoop: Bool
    /// 배지 통통이 남은 시간 — 그동안만 배지를 움직인다(가만히 있을 때 프레임마다 화면을 다시 그리지 않게)
    @ObservationIgnored private var badgeLeft: Double = 0
    @ObservationIgnored private var frameTimer: Timer?
    @ObservationIgnored private var lastTick: CFTimeInterval?

    /// runsLoop: 테스트는 false 로 두고 advance(_:) 를 직접 부른다
    init(rewards: PlayRewardsStore, runsLoop: Bool = true) {
        self.rewards = rewards
        self.runsLoop = runsLoop
        selected = rewards.equipped   // 쓰고 있는 것을 골라 둔 채로 시작
        starsBadge.reset(rewards.stars)
    }

    // MARK: - 화면에서 읽는 값

    var stars: Int { rewards.stars }

    /// 큰 구구가 쓰고 있을 것 — 써 보는 중이면 고른 것, 아니면 저장된 것(없으면 맨머리 nil)
    var previewWearing: Accessory? { tryingOn ? selected : rewards.equipped }

    var action: Action {
        guard let item = selected else { return .none }
        if rewards.owns(item) { return rewards.equipped == item ? .takeOff : .wear }
        let lack = Wardrobe.shortfall(item, balance: rewards.stars)
        return lack > 0 ? .needMore(lack: lack) : .buy(price: item.price)
    }

    var actionTitle: String {
        switch action {
        case .none: return String(localized: "장신구를 골라 보세요")
        case .buy(let price): return String(localized: "별 \(price)개로 사기")
        case .needMore(let lack): return String(localized: "별 \(lack)개 더 모아요")
        case .wear: return String(localized: "쓰기")
        case .takeOff: return String(localized: "벗기")
        }
    }

    func owns(_ item: Accessory) -> Bool { rewards.owns(item) }
    func isEquipped(_ item: Accessory) -> Bool { rewards.equipped == item }
    /// 별이 모자라 아직 못 사는 것(값을 빨갛게)
    func cannotAfford(_ item: Accessory) -> Bool {
        !owns(item) && Wardrobe.shortfall(item, balance: rewards.stars) > 0
    }

    /// 큰 구구 통통 배율
    var previewScale: Double { 1 + 0.12 * sin(pop * .pi) }
    /// 별 부족 흔들림(설계 px)
    var actionShakeX: Double { sin(shake * 60) * 18 * (shake / Self.shakeSec) }

    // MARK: - 조작

    /// 카드 누름: 써 보기(별은 아직 안 씀)
    func select(_ item: Accessory) {
        selected = item
        tryingOn = true
        pop = 1
        Sound.shared.tap()
        Haptics.selection()
        startLoopIfNeeded()
    }

    /// 아래 큰 버튼. 방금 바뀐 버튼을 연타로 또 누른 것(잠금 중)은 무시
    func pressAction() {
        guard actionLock <= 0 else { return }
        switch action {
        case .none:
            return
        case .buy:
            guard let item = selected else { preconditionFailure("사기 버튼인데 고른 장신구가 없음") }
            do {
                try rewards.buy(item)   // 사면 바로 씀
            } catch {
                // action 이 가진 것·별 부족을 이미 걸렀으므로 여기 오면 화면 규칙 버그
                preconditionFailure("화면이 막았어야 하는 사기: \(error)")
            }
            Sound.shared.correct()
            Haptics.success()
            pop = 1
        case .wear:
            guard let item = selected else { preconditionFailure("쓰기 버튼인데 고른 장신구가 없음") }
            rewards.equip(item)
            Sound.shared.correct()
            Haptics.impact(.light)
            pop = 1
        case .takeOff:
            rewards.equip(nil)
            Sound.shared.tap()
            Haptics.impact(.light)
        case .needMore:
            Sound.shared.deny()
            Haptics.warning()
            shake = Self.shakeSec
            startLoopIfNeeded()
            return
        }
        actionLock = Self.actionLockSec
        tryingOn = false   // 저장이 바뀌었으니 큰 구구는 저장된 모습(벗기 → 맨머리)
        if starsBadge.value != rewards.stars {
            starsBadge.setValue(rewards.stars)   // 값이 바뀌면 통통
            badgeLeft = HudBadgeState.popSec
        }
        startLoopIfNeeded()
    }

    /// 한 프레임 — 통통·흔들림·잠금 시간을 줄인다. 루프가 부르고, 테스트는 직접 부른다
    func advance(_ dt: Double) {
        precondition(dt >= 0, "dt 는 0 이상이어야 함: \(dt)")
        if pop > 0 { pop = max(0, pop - dt / Self.popSec) }
        if shake > 0 { shake = max(0, shake - dt) }
        if actionLock > 0 { actionLock = max(0, actionLock - dt) }
        if badgeLeft > 0 {
            badgeLeft = max(0, badgeLeft - dt)
            starsBadge.update(dt)
        }
        if !animating { stopLoop() }
    }

    func teardown() { stopLoop() }

    // MARK: - 프레임 루프 (움직이는 것이 있을 때만)

    private var animating: Bool { pop > 0 || shake > 0 || actionLock > 0 || badgeLeft > 0 }

    private func startLoopIfNeeded() {
        guard runsLoop, frameTimer == nil else { return }
        lastTick = nil
        let timer = Timer(timeInterval: 1.0 / 60.0, repeats: true) { [weak self] _ in
            self?.tickFromTimer()
        }
        RunLoop.main.add(timer, forMode: .common)
        frameTimer = timer
    }

    private func stopLoop() {
        frameTimer?.invalidate()
        frameTimer = nil
        lastTick = nil
    }

    private func tickFromTimer() {
        let now = CACurrentMediaTime()
        defer { lastTick = now }
        guard let last = lastTick else { return }
        advance(min(now - last, 0.1))
    }
}
