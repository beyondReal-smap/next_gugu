import XCTest
@testable import Gugu

/// A·B 보상·수집 규칙 — 별 코인·연속 정답·높이 구간(1차), 몬스터 도감·옷장·고도계(2차).
/// Unity 시험판 RewardRulesTests·Round2RulesTests 이식 + Round2PlayTests 의 도감 순환(누적 처치 기준) 검사.
/// 원본에서 예외를 단언하던 잘못된 인자(연속 0·없는 종·음수 높이)는 Swift 에서 precondition 이라 정상 경로만 검사한다.
final class GameRewardRulesTests: XCTestCase {

    private func rng(_ seed: UInt32) -> () -> Double { SeededRandom.mulberry32(seed) }

    // MARK: - 별 코인·높이 구간 (원본 RewardRulesTests)

    func testStarRulesOnePerCorrectPlusBonusEveryFifthInARow() {
        XCTAssertEqual(StarRules.forCorrect(streak: 1), 1)
        XCTAssertEqual(StarRules.forCorrect(streak: 4), 1)
        XCTAssertEqual(StarRules.forCorrect(streak: 5), 1 + StarRules.comboBonus)
        XCTAssertEqual(StarRules.forCorrect(streak: 6), 1)
        XCTAssertEqual(StarRules.forCorrect(streak: 10), 1 + StarRules.comboBonus)
        XCTAssertFalse(StarRules.isComboMilestone(0))
        XCTAssertEqual([StarRules.perCorrect, StarRules.comboEvery, StarRules.comboBonus, StarRules.treasureBonus],
                       [1, 5, 2, 5])
    }

    func testSkyZonesChangeAtTenTwentyFiveForty() {
        XCTAssertEqual(SkyZone.at(height: 0), .meadow)
        XCTAssertEqual(SkyZone.at(height: 9), .meadow)
        XCTAssertEqual(SkyZone.at(height: 10), .skyVillage)
        XCTAssertEqual(SkyZone.at(height: 24), .skyVillage)
        XCTAssertEqual(SkyZone.at(height: 25), .rainbow)
        XCTAssertEqual(SkyZone.at(height: 39), .rainbow)
        XCTAssertEqual(SkyZone.at(height: 40), .space)
        XCTAssertEqual(SkyZone.at(height: 500), .space)
        XCTAssertEqual(SkyZone.at(height: -3), .meadow, "출발 아래(카메라가 아래를 비출 때)")
        // 구간 순서 = 화면 현지화·그림 순서 (원본 Name() 대신 UI 층에서 이름을 붙인다)
        XCTAssertEqual(SkyZone.allCases, [.meadow, .skyVillage, .rainbow, .space])
        XCTAssertEqual(SkyZone.allCases.map(\.startHeight), [0, 10, 25, 40])
    }

    private func stairsCorrect(_ s: StairsSession, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(s.jump(lane: s.current.correctLane).attempt?.correct, true, file: file, line: line)
    }

    private func stairsWrong(_ s: StairsSession, file: StaticString = #filePath, line: UInt = #line) {
        let lane = (s.current.correctLane + 1) % StairsSession.lanes
        XCTAssertEqual(s.jump(lane: lane).attempt?.correct, false, file: file, line: line)
        s.tick(RoundClock.revealSec + 0.01)  // 공개가 끝나야 다시 풀 수 있음
    }

    func testStairsStreakAndStarsComboBonusAndResetOnWrong() {
        let s = StairsSession(topStep: 9, order: .sequential, firstTable: 3, random: rng(21))
        for _ in 0..<5 { stairsCorrect(s) }
        XCTAssertEqual(s.streak, 5)
        XCTAssertEqual(s.stars, 5 + StarRules.comboBonus, "5연속에서 보너스")
        stairsWrong(s)
        XCTAssertEqual(s.streak, 0)
        XCTAssertEqual(s.bestStreak, 5)
        XCTAssertEqual(s.stars, 7, "오답은 별을 빼지 않음")
        stairsCorrect(s)
        XCTAssertEqual(s.streak, 1)
        XCTAssertEqual(s.stars, 8)
    }

    /// 꼭대기(×topStep) 보물 +5 — topStep 마다: 정답 수 + 5연속 보너스들 + 보물
    func testStairsTreasureGivesFiveStars() {
        for topStep in Problems.maxBOptions {
            let s = StairsSession(topStep: topStep, order: .sequential, firstTable: 4, random: rng(22))
            for _ in 0..<topStep { stairsCorrect(s) }
            XCTAssertEqual(s.treasures, 1)
            XCTAssertEqual(s.stars, topStep + (topStep / 5) * StarRules.comboBonus + StarRules.treasureBonus,
                           "topStep \(topStep)")
        }
    }

    private func monsterCorrect(_ s: MonsterSession, file: StaticString = #filePath, line: UInt = #line) {
        let q = s.current
        _ = s.tap(card: q.pairA)
        XCTAssertEqual(s.tap(card: q.pairB).attempt?.correct, true, file: file, line: line)
    }

    private func monsterWrong(_ s: MonsterSession, file: StaticString = #filePath, line: UInt = #line) {
        let q = s.current
        _ = s.tap(card: q.pairA)
        for i in 0..<MonsterSession.cardCount where i != q.pairA && q.cards[q.pairA] * q.cards[i] != q.target.a * q.target.b {
            XCTAssertEqual(s.tap(card: i).attempt?.correct, false, file: file, line: line)
            s.tick(RoundClock.revealSec + 0.01)
            return
        }
        XCTFail("오답 카드가 없음", file: file, line: line)
    }

    func testMonsterStreakAndStarsComboBonusAndResetOnWrong() {
        let s = MonsterSession(order: .random, random: rng(23))
        for _ in 0..<6 { monsterCorrect(s) }
        XCTAssertEqual(s.streak, 6)
        XCTAssertEqual(s.stars, 6 + StarRules.comboBonus)
        monsterWrong(s)
        XCTAssertEqual(s.streak, 0)
        XCTAssertEqual(s.bestStreak, 6)
        XCTAssertEqual(s.stars, 8)
    }

    func testSelectAndDeselectDoNotTouchStreakOrStars() {
        let s = MonsterSession(order: .random, random: rng(24))
        monsterCorrect(s)
        let q = s.current
        _ = s.tap(card: q.pairA)
        _ = s.tap(card: q.pairA)  // 고르기 취소
        XCTAssertEqual(s.streak, 1)
        XCTAssertEqual(s.stars, 1)
    }

    // MARK: - 몬스터 도감 (원본 Round2RulesTests)

    func testDexFirstFourUnlockedRestUnlockAt10_25_45_70() {
        XCTAssertEqual(MonsterDex.unlockedCount(totalDefeated: 0), 4)
        XCTAssertEqual(MonsterDex.unlockedCount(totalDefeated: 9), 4)
        XCTAssertEqual(MonsterDex.unlockedCount(totalDefeated: 10), 5)
        XCTAssertEqual(MonsterDex.unlockedCount(totalDefeated: 25), 6)
        XCTAssertEqual(MonsterDex.unlockedCount(totalDefeated: 45), 7)
        XCTAssertEqual(MonsterDex.unlockedCount(totalDefeated: 69), 7)
        XCTAssertEqual(MonsterDex.unlockedCount(totalDefeated: 70), MonsterDex.kinds)
        XCTAssertEqual(MonsterDex.newlyUnlocked(before: 9, after: 10), .pink)
        XCTAssertNil(MonsterDex.newlyUnlocked(before: 10, after: 11))
        XCTAssertEqual(MonsterDex.newlyUnlocked(before: 69, after: 70), .gold)
        XCTAssertEqual(MonsterKind.allCases.map(MonsterDex.unlockAt), [0, 0, 0, 0, 10, 25, 45, 70])
        for k in 1..<MonsterDex.kinds {
            XCTAssertGreaterThanOrEqual(MonsterDex.unlockAt(MonsterKind.allCases[k]),
                                        MonsterDex.unlockAt(MonsterKind.allCases[k - 1]), "해금 기준은 앞 종보다 작지 않음")
        }
        XCTAssertEqual(MonsterKind.allCases.map(\.bit), [1, 2, 4, 8, 16, 32, 64, 128], "저장 비트 위치 고정")
    }

    func testDexNewPlayerMeetsFirstFourInOrderThenCycles() {
        var total = 0, seen = 0
        var prev: MonsterKind?
        var order: [MonsterKind] = []
        for i in 0..<8 {
            let kind = MonsterDex.nextKind(totalDefeated: total, seenMask: seen, spawnIndex: i, previous: prev)
            order.append(kind)
            seen = MonsterDex.markSeen(kind, seenMask: seen)  // 모두 물리쳤다고 가정
            total += 1
            prev = kind
        }
        XCTAssertEqual(Array(order[0..<4]), [.green, .orange, .purple, .sky], "처음 4종을 차례로 만남")
        XCTAssertEqual(Array(order[4..<8]), [.green, .orange, .purple, .sky], "다 만난 뒤에는 해금된 4종을 돌림")
        XCTAssertEqual(MonsterDex.seenCount(seen), 4)
    }

    func testDexNewlyUnlockedKindAppearsNextAndNeverTwiceInARow() {
        // 9마리 물리쳐 4종을 다 만난 상태 → 10번째 처치로 분홍 해금 → 다음 몬스터는 분홍
        var seen = 0b1111
        XCTAssertEqual(MonsterDex.nextKind(totalDefeated: 10, seenMask: seen, spawnIndex: 3, previous: .sky), .pink)
        // 분홍을 물리친 뒤: 5종 순환. spawnIndex 9 → 9 % 5 = 4(분홍)인데 바로 앞이 분홍이면 다음 종
        seen = MonsterDex.markSeen(.pink, seenMask: seen)
        XCTAssertEqual(MonsterDex.nextKind(totalDefeated: 11, seenMask: seen, spawnIndex: 9, previous: .pink), .green)
        // 오래 돌려도 같은 종이 연달아 나오지 않음(해금이 바뀌는 순간 포함)
        var prev: MonsterKind?
        seen = 0
        for i in 0..<200 {
            let total = i
            let kind = MonsterDex.nextKind(totalDefeated: total, seenMask: seen, spawnIndex: i, previous: prev)
            XCTAssertTrue(MonsterDex.isUnlocked(kind, totalDefeated: total), "\(i)번째: 해금 안 된 종 \(kind)")
            if let prev { XCTAssertNotEqual(kind, prev, "\(i)번째: 같은 종 연속") }
            seen = MonsterDex.markSeen(kind, seenMask: seen)
            prev = kind
        }
        XCTAssertEqual(MonsterDex.seenCount(seen), MonsterDex.kinds, "200마리면 8종을 모두 만남")
    }

    // MARK: - 도감 순환 — 게임 흐름 (원본 Round2PlayTests)

    /// 게임 층이 할 일을 그대로 흉내: 새 몬스터(monstersIssued 가 바뀜)마다 누적 처치 수로 종을 고르고,
    /// 처치하는 순간 누적 처치·만난 종을 기록한다(도감은 처치 순간 저장 — 시험판 그대로)
    private struct DexPlay {
        var total: Int
        var seen: Int
        var kind: MonsterKind?
        var unlockedNow: MonsterKind?

        init(total: Int, seen: Int) {
            self.total = total
            self.seen = seen
        }

        /// 판 시작 — 바로 앞 몬스터 없음
        mutating func startRound() -> MonsterKind {
            kind = nil
            return spawn()
        }

        mutating func spawn() -> MonsterKind {
            let next = MonsterDex.nextKind(totalDefeated: total, seenMask: seen, spawnIndex: total, previous: kind)
            kind = next
            return next
        }

        /// 지금 몬스터를 물리침 → 다음 몬스터 종
        mutating func defeat(_ s: MonsterSession, file: StaticString = #filePath, line: UInt = #line) -> MonsterKind {
            guard let current = kind else {
                XCTFail("몬스터가 없음", file: file, line: line)
                return .green
            }
            let issued = s.monstersIssued
            let q = s.current
            _ = s.tap(card: q.pairA)
            XCTAssertEqual(s.tap(card: q.pairB).attempt?.correct, true, file: file, line: line)
            XCTAssertEqual(s.monstersIssued, issued + 1, "새 몬스터", file: file, line: line)
            unlockedNow = MonsterDex.newlyUnlocked(before: total, after: total + 1)
            total += 1
            seen = MonsterDex.markSeen(current, seenMask: seen)
            return spawn()
        }
    }

    func testTenthDefeatUnlocksPinkItAppearsNextThenJoinsDex() {
        var dex = DexPlay(total: 9, seen: 0b1111)
        let s = MonsterSession(order: .random, random: rng(77))
        XCTAssertEqual(dex.startRound(), MonsterKind.allCases[9 % 4], "4종을 다 만났으면 누적 처치 수 차례로(9마리 → 1번)")
        XCTAssertFalse(MonsterDex.isUnlocked(.pink, totalDefeated: dex.total))
        XCTAssertEqual(dex.defeat(s), .pink, "10마리째 → 분홍 해금, 새 몬스터가 바로 등장")
        XCTAssertEqual(dex.unlockedNow, .pink)
        XCTAssertFalse(MonsterDex.isSeen(.pink, seenMask: dex.seen), "아직 못 물리침 = 실루엣")
        _ = dex.defeat(s)
        XCTAssertTrue(MonsterDex.isSeen(.pink, seenMask: dex.seen), "도감에 등록")
        XCTAssertEqual(dex.total, 11)
    }

    func testAllEightSeenMonstersCycleNoSameKindTwiceInARow() {
        var dex = DexPlay(total: 100, seen: 0xFF)
        let s = MonsterSession(order: .random, random: rng(77))
        var kinds = [dex.startRound()]
        for _ in 0..<9 { kinds.append(dex.defeat(s)) }
        for i in 1..<kinds.count { XCTAssertNotEqual(kinds[i], kinds[i - 1]) }
        XCTAssertEqual(Set(kinds).count, MonsterDex.kinds, "10마리 안에 8종 모두")
    }

    /// 한 판에 3마리씩만 잡는 아이: 판마다 0번부터 다시 돌면 0~3번만 보게 됨 → 누적 처치 수로 이어 돈다
    func testShortRoundsCycleContinuesAcrossRoundsAllEightAppear() {
        var dex = DexPlay(total: 100, seen: 0xFF)
        var kinds = Set<MonsterKind>()
        for round: UInt32 in 0..<3 {
            let s = MonsterSession(order: .random, random: rng(77 + round))
            kinds.insert(dex.startRound())
            for _ in 0..<3 { kinds.insert(dex.defeat(s)) }
        }
        XCTAssertEqual(kinds.count, MonsterDex.kinds, "짧은 판 3번에 8종 모두 만남")
    }

    // MARK: - 옷장·고도계 (원본 Round2RulesTests)

    func testWardrobePricesRiseBuyNeedsEnoughStarsNoDoubleBuy() throws {
        XCTAssertEqual(Accessory.allCases.map(\.price), [15, 30, 50, 80, 120, 200], "뒤로 갈수록 비쌈")
        XCTAssertEqual(Accessory.allCases.map(\.bit), [1, 2, 4, 8, 16, 32], "저장 비트 위치 고정")
        XCTAssertEqual(Wardrobe.count, 6)
        XCTAssertEqual(Wardrobe.shortfall(.ribbon, balance: 10), 5)
        XCTAssertEqual(Wardrobe.shortfall(.ribbon, balance: 15), 0)
        XCTAssertThrowsError(try Wardrobe.buy(.ribbon, balance: 14, ownedMask: 0)) { error in
            XCTAssertEqual(error as? WardrobeError, .notEnoughStars(.ribbon, lack: 1))
        }
        XCTAssertEqual(try Wardrobe.buy(.ribbon, balance: 15, ownedMask: 0).balance, 0, "딱 맞으면 잔고 0 으로 살 수 있음")

        let (balance, owned) = try Wardrobe.buy(.ribbon, balance: 40, ownedMask: 0)
        XCTAssertEqual(balance, 25)
        XCTAssertTrue(Wardrobe.owns(.ribbon, ownedMask: owned))
        XCTAssertFalse(Wardrobe.owns(.crown, ownedMask: owned))
        XCTAssertThrowsError(try Wardrobe.buy(.ribbon, balance: balance, ownedMask: owned), "이미 가진 것") { error in
            XCTAssertEqual(error as? WardrobeError, .alreadyOwned(.ribbon))
        }
        // 하나 더 사면 가진 목록에 비트가 더해짐
        let (left, both) = try Wardrobe.buy(.crown, balance: 225, ownedMask: owned)
        XCTAssertEqual(left, 25)
        XCTAssertEqual(both, Accessory.ribbon.bit | Accessory.crown.bit)
    }

    func testAltimeterShowsMoonFromStartGrowsInStepsOfFive() {
        XCTAssertEqual(Altimeter.top(climbed: 0, best: 0), 45, "처음부터 달나라(40)까지 보임")
        XCTAssertEqual(Altimeter.top(climbed: 37, best: 0), 45)
        XCTAssertEqual(Altimeter.top(climbed: 38, best: 0), 50, "38 + 여유 8 = 46 → 5칸 단위로 50")
        XCTAssertEqual(Altimeter.top(climbed: 0, best: 60), 70, "최고 기록이 높으면 처음부터 깃발이 보이게")
        XCTAssertEqual(Altimeter.fraction(height: 0, top: 45), 0)
        XCTAssertEqual(Altimeter.fraction(height: -2, top: 45), 0, "출발 아래 먹구름은 막대 아래 끝")
        XCTAssertEqual(Altimeter.fraction(height: 45, top: 45), 1)
        XCTAssertEqual(Altimeter.fraction(height: 9, top: 45), 0.2, accuracy: 1e-9)
    }
}
