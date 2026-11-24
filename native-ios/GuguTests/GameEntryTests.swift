import XCTest
@testable import Gugu

/// 게임 진입점 — 라우터(게임·옷장), 단 시트에서 고를 수 있는 단(구구 몬스터 2~9단), 카드 배지, 알림 딥링크.
final class GameEntryTests: XCTestCase {

    // MARK: - 라우터

    func testGamesAreStairsThenMonster() {
        XCTAssertEqual(Minigame.allCases, [.stairs, .monster])
        XCTAssertEqual(Minigame.allCases.map(\.title), ["구구 계단", "구구 몬스터"])
    }

    /// 홈은 단을 정하지 않고(.standard), 단 시트는 고른 단을 넘긴다. 같은 게임을 다시 열어도 새 판(새 id)
    func testRouterOpensGameWithStart() throws {
        let router = Router()
        XCTAssertNil(router.minigame)

        router.open(.stairs)
        let home = try XCTUnwrap(router.minigame)
        XCTAssertEqual(home.game, .stairs)
        XCTAssertEqual(home.start, .standard)

        router.open(.monster, start: .table(7))
        XCTAssertEqual(router.minigame?.game, .monster)
        XCTAssertEqual(router.minigame?.start, .table(7))

        router.open(.monster, start: .table(nil))
        XCTAssertEqual(router.minigame?.start, .table(nil))

        let first = try XCTUnwrap(router.minigame?.id)
        router.open(.monster, start: .table(nil))
        XCTAssertNotEqual(router.minigame?.id, first)
    }

    /// 옷장은 게임과 따로 띄운다
    func testRouterOpensWardrobe() {
        let router = Router()
        XCTAssertFalse(router.wardrobeOpen)
        router.openWardrobe()
        XCTAssertTrue(router.wardrobeOpen)
        XCTAssertNil(router.minigame)
    }

    /// 단을 정하지 않았으면 두 게임 모두 엔진 기본값(2~9단 섞어서)
    func testStartTableResolution() {
        XCTAssertNil(MinigameStart.standard.table(defaultTable: nil))
        XCTAssertEqual(MinigameStart.table(13).table(defaultTable: nil), 13)
        XCTAssertNil(MinigameStart.table(nil).table(defaultTable: 2), "섞어서는 기본값으로 바꾸지 않는다")
    }

    // MARK: - 단 시트 잠금

    /// 구구 계단은 2~19단 어디서나, 구구 몬스터는 2~9단만(카드가 1~9). 섞어서는 둘 다
    func testMonsterOnlySupportsTablesUpToNine() {
        for table in Problems.minTable...Problems.maxTable {
            XCTAssertTrue(Minigame.stairs.supports(table: table), "계단 \(table)단")
            XCTAssertEqual(Minigame.monster.supports(table: table), table <= 9, "몬스터 \(table)단")
        }
        XCTAssertTrue(Minigame.stairs.supports(table: nil))
        XCTAssertTrue(Minigame.monster.supports(table: nil))
        XCTAssertTrue(Minigame.monster.supports(table: 7))
        XCTAssertFalse(Minigame.monster.supports(table: 13))
    }

    // MARK: - 카드 배지

    /// 무료는 "3문제 체험", 이용권은 "새 게임"
    func testCardBadges() {
        for g in Minigame.allCases {
            XCTAssertEqual(g.badge(isPremium: false), "\(PremiumConfig.minigameTrialQuestions)문제 체험")
            XCTAssertEqual(g.badge(isPremium: true), "새 게임")
        }
    }

    // MARK: - 알림 딥링크

    /// 서버 허용 목록에는 게임·어드벤처 경로가 없다 — 옛 이름이 와도 홈으로
    func testRetiredGameLinksGoHome() {
        for link in ["adventure", "minigame", "pop", "stairs", "monster", "wardrobe"] {
            XCTAssertEqual(PushRoute.parse(link), .home, link)
        }
        XCTAssertEqual(Set(PushRoute.allCases.map(\.rawValue)),
                       ["home", "learn", "profile", "profile/report", "paywall", "settings/notifications"])
    }
}
