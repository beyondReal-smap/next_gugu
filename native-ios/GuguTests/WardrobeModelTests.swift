import XCTest
@testable import Gugu

/// 구구 꾸미기(옷장) 화면 상태 — Unity 시험판 WardrobeScreen 에서 고친 규칙 회귀:
/// 사기·쓰기·벗기 직후 0.4초 잠금(연타로 사자마자 벗겨짐 방지), 벗기 → 큰 구구 맨머리(대표님 지적),
/// 써 보기는 별을 안 씀, 별 부족은 거절(흔들림), 다시 열면 쓰고 있는 것이 골라져 있음.
final class WardrobeModelTests: XCTestCase {

    private let frame = 1.0 / 60

    private func makeStore(stars: Int) -> PlayRewardsStore {
        let key = "gugu.test.play.\(UUID().uuidString)"
        let adventureKey = "gugu.test.adventure.\(UUID().uuidString)"
        addTeardownBlock {
            for k in [key, adventureKey] {
                UserDefaults.standard.removeObject(forKey: k)
                Persistence.clearQuarantine(key: k)
            }
        }
        let store = PlayRewardsStore(key: key, adventureKey: adventureKey)
        if stars > 0 { store.recordRound(.stairs, score: 1, stars: stars, bestStreak: 0) }
        return store
    }

    private func run(_ model: WardrobeModel, sec: Double) {
        for _ in 0..<Int((sec / frame).rounded(.up)) { model.advance(frame) }
    }

    /// 처음 열면 아무것도 안 고름 → 버튼은 안내, 눌러도 아무 일 없음
    func testNothingSelectedAtFirst() {
        let store = makeStore(stars: 62)
        let model = WardrobeModel(rewards: store, runsLoop: false)
        XCTAssertNil(model.selected)
        XCTAssertNil(model.previewWearing)
        XCTAssertEqual(model.action, .none)
        XCTAssertEqual(model.actionTitle, "장신구를 골라 보세요")
        model.pressAction()
        XCTAssertEqual(store.stars, 62)
        XCTAssertEqual(model.actionLock, 0)
    }

    /// 써 보기: 카드를 누르면 큰 구구가 쓰지만 별은 그대로, 저장도 그대로
    func testTryOnSpendsNoStars() {
        let store = makeStore(stars: 62)
        let model = WardrobeModel(rewards: store, runsLoop: false)
        model.select(.partyHat)
        XCTAssertEqual(model.previewWearing, .partyHat)
        XCTAssertEqual(model.action, .buy(price: 50))
        XCTAssertEqual(model.actionTitle, "별 50개로 사기")
        XCTAssertEqual(store.stars, 62)
        XCTAssertNil(store.equipped)
        XCTAssertFalse(store.owns(.partyHat))
    }

    /// 별 부족: 왕관을 써 볼 수는 있지만 버튼은 "별 N개 더 모아요" — 누르면 흔들리고 아무것도 안 바뀜
    func testNotEnoughStarsIsRefused() {
        let store = makeStore(stars: 62)
        let model = WardrobeModel(rewards: store, runsLoop: false)
        model.select(.crown)
        XCTAssertEqual(model.previewWearing, .crown)
        XCTAssertEqual(model.action, .needMore(lack: 138))
        XCTAssertEqual(model.actionTitle, "별 138개 더 모아요")
        XCTAssertTrue(model.cannotAfford(.crown))

        model.pressAction()
        XCTAssertEqual(store.stars, 62)
        XCTAssertFalse(store.owns(.crown))
        XCTAssertNil(store.equipped)
        XCTAssertGreaterThan(model.shake, 0)
        XCTAssertEqual(model.actionLock, 0, "거절은 잠그지 않음")
        XCTAssertEqual(model.previewWearing, .crown, "거절 뒤에도 계속 써 보는 중")
        run(model, sec: WardrobeModel.shakeSec + 0.02)
        XCTAssertEqual(model.shake, 0)
        XCTAssertEqual(model.actionShakeX, 0)
    }

    /// 사면 바로 씀 + 0.4초 잠금: 사자마자 연타해도 벗겨지지 않고, 잠금이 풀린 뒤에야 [벗기]가 먹힘 → 큰 구구 맨머리
    func testBuyLocksButtonThenTakeOffShowsBareHead() {
        let store = makeStore(stars: 62)
        let model = WardrobeModel(rewards: store, runsLoop: false)
        model.select(.partyHat)
        model.pressAction()

        XCTAssertEqual(store.stars, 12)
        XCTAssertTrue(store.owns(.partyHat))
        XCTAssertEqual(store.equipped, .partyHat)
        XCTAssertEqual(model.action, .takeOff)
        XCTAssertEqual(model.actionTitle, "벗기")
        XCTAssertEqual(model.previewWearing, .partyHat)
        XCTAssertEqual(model.starsBadge.value, 12)

        // 연타 — 잠금 중이라 무시
        model.pressAction()
        XCTAssertEqual(store.equipped, .partyHat)
        run(model, sec: WardrobeModel.actionLockSec - 0.05)
        model.pressAction()
        XCTAssertEqual(store.equipped, .partyHat, "0.4초 전에는 그대로")

        run(model, sec: 0.07)
        XCTAssertEqual(model.actionLock, 0)
        model.pressAction()
        XCTAssertNil(store.equipped)
        XCTAssertTrue(store.owns(.partyHat))
        XCTAssertEqual(model.selected, .partyHat, "카드는 골라진 채 — [쓰기]로 바로 되돌릴 수 있음")
        XCTAssertEqual(model.action, .wear)
        XCTAssertNil(model.previewWearing, "벗기 → 큰 구구는 맨머리")

        // 잠금이 풀린 뒤 다시 쓰기 → 씀
        run(model, sec: WardrobeModel.actionLockSec + 0.02)
        model.pressAction()
        XCTAssertEqual(store.equipped, .partyHat)
        XCTAssertEqual(model.previewWearing, .partyHat)
        XCTAssertEqual(store.stars, 12, "가진 것을 다시 쓰는 데 별은 안 씀")
    }

    /// 다른 장신구를 써 보는 동안은 그것, 쓰기를 누르면 저장된 모습으로 돌아옴
    func testTryOnOtherWhileWearingThenWear() {
        let store = makeStore(stars: 200)
        let model = WardrobeModel(rewards: store, runsLoop: false)
        model.select(.ribbon)
        model.pressAction()   // 리본 사서 씀
        run(model, sec: WardrobeModel.actionLockSec + 0.02)
        model.select(.flowerCrown)
        model.pressAction()   // 화관 사서 씀
        run(model, sec: WardrobeModel.actionLockSec + 0.02)
        XCTAssertEqual(store.equipped, .flowerCrown)

        model.select(.ribbon)
        XCTAssertEqual(model.previewWearing, .ribbon, "써 보는 중")
        XCTAssertEqual(model.action, .wear)
        XCTAssertEqual(store.equipped, .flowerCrown)
        model.pressAction()
        XCTAssertEqual(store.equipped, .ribbon)
        XCTAssertEqual(model.previewWearing, .ribbon)
        XCTAssertEqual(store.stars, 200 - 15 - 30)
    }

    /// 다시 열면(새 모델) 쓰고 있는 것이 골라진 채, 큰 구구는 저장된 모습
    func testReopenSelectsEquipped() {
        let store = makeStore(stars: 62)
        let first = WardrobeModel(rewards: store, runsLoop: false)
        first.select(.ribbon)
        first.pressAction()
        first.teardown()

        let reopened = WardrobeModel(rewards: store, runsLoop: false)
        XCTAssertEqual(reopened.selected, .ribbon)
        XCTAssertFalse(reopened.tryingOn)
        XCTAssertEqual(reopened.previewWearing, .ribbon)
        XCTAssertEqual(reopened.action, .takeOff)
        XCTAssertEqual(reopened.actionLock, 0, "새로 열면 잠금 없음")
    }
}
