import XCTest
@testable import Gugu

/// 구구 계단·구구 몬스터 보상 저장소(gugu.play.v1).
/// 저장·읽기·손상 격리, 어드벤처 별 조각 1:1 이관(처음 한 번, 어드벤처 키는 지우지 않음),
/// 별·최고 기록은 결과 저장(recordRound) 때만 쌓이고 도감·옷장은 누르는 순간 저장.
final class PlayRewardsStoreTests: XCTestCase {

    // MARK: - 도우미

    /// 테스트 키는 고유하게 만들고, 끝나면 저장본·격리 파일을 정리한다(CoreTests 격리 테스트와 같은 관례)
    private func makeKey(_ name: String) -> String {
        let key = "gugu.test.\(name).\(UUID().uuidString)"
        addTeardownBlock {
            UserDefaults.standard.removeObject(forKey: key)
            Persistence.clearQuarantine(key: key)
        }
        return key
    }

    private struct Keys {
        let play: String
        let adventure: String
    }

    private func makeKeys() -> Keys {
        Keys(play: makeKey("play"), adventure: makeKey("adventure"))
    }

    private func makeStore(_ keys: Keys) -> PlayRewardsStore {
        PlayRewardsStore(key: keys.play, adventureKey: keys.adventure)
    }

    private func saved(_ keys: Keys) throws -> PlayRewards {
        try XCTUnwrap(Persistence.load(PlayRewards.self, key: keys.play))
    }

    /// 2.8.x 어드벤처 저장본(AdventureProgress JSON, 타입은 2.9.0 에서 제거) 모양 그대로 — 이관은 starShards 만 읽는다
    private func adventureJSON(shards: Int) -> Data {
        Data("""
        {"version":1,"defeatedNpcs":["r2-boss"],"battlesWon":3,"battlesLost":1,"achievements":[],
         "starShards":\(shards),"ownedColors":["mint"],"ownedHats":[],"equippedColor":"mint"}
        """.utf8)
    }

    // MARK: - 저장·읽기

    /// 저장본이 없으면 빈 보상으로 시작하고, 이관 표시까지 바로 저장한다
    func testFreshStoreStartsEmptyAndPersists() throws {
        let keys = makeKeys()
        let store = makeStore(keys)

        XCTAssertEqual(store.stars, 0)
        XCTAssertEqual(store.best(.stairs), 0)
        XCTAssertEqual(store.best(.monster), 0)
        XCTAssertNil(store.equipped)
        XCTAssertEqual(store.dexTotal, 0)
        XCTAssertEqual(try saved(keys).adventureMigrated, true)
        XCTAssertTrue(Persistence.quarantinedFiles(key: keys.play).isEmpty)
    }

    /// 별·기록·도감·옷장을 쓴 뒤 다시 열면 그대로 읽힌다
    func testRoundTripRestoresEverything() throws {
        let keys = makeKeys()
        let store = makeStore(keys)
        store.recordRound(.stairs, score: 12, stars: 30, bestStreak: 0)
        store.recordRound(.monster, score: 5, stars: 4, bestStreak: 0)
        store.recordDefeat(.orange)
        try store.buy(.ribbon)

        let reopened = makeStore(keys)

        XCTAssertEqual(reopened.rewards, store.rewards)
        XCTAssertEqual(reopened.stars, 30 + 4 - Accessory.ribbon.price)
        XCTAssertEqual(reopened.best(.stairs), 12)
        XCTAssertEqual(reopened.best(.monster), 5)
        XCTAssertEqual(reopened.equipped, .ribbon)
        XCTAssertTrue(reopened.owns(.ribbon))
        XCTAssertEqual(reopened.dexTotal, 1)
        XCTAssertTrue(MonsterDex.isSeen(.orange, seenMask: reopened.dexSeenMask))
    }

    /// 못 읽는 저장본은 원본을 격리하고 새 보상으로 시작한다(덮어쓴 뒤에는 정상 저장본)
    func testCorruptSaveIsQuarantinedAndReplaced() throws {
        let keys = makeKeys()
        let corrupt = Data("{깨진 보상".utf8)
        UserDefaults.standard.set(corrupt, forKey: keys.play)

        let store = makeStore(keys)

        XCTAssertEqual(store.stars, 0)
        let files = Persistence.quarantinedFiles(key: keys.play)
        XCTAssertEqual(files.count, 1)
        XCTAssertEqual(try Data(contentsOf: XCTUnwrap(files.first)), corrupt)
        XCTAssertEqual(try saved(keys), store.rewards)
    }

    /// 형식은 맞아도 규칙 밖(갖지 않은 장신구를 씀·음수 별)이면 조용히 고치지 않고 격리한다
    func testRuleBreakingSavesAreQuarantined() {
        let cases = [
            #"{"stars":3,"ownedMask":0,"equipped":0,"adventureMigrated":true}"#,
            #"{"stars":-1,"adventureMigrated":true}"#,
            #"{"stars":1,"ownedMask":1024,"adventureMigrated":true}"#,
        ]
        for json in cases {
            let keys = makeKeys()
            UserDefaults.standard.set(Data(json.utf8), forKey: keys.play)

            let store = makeStore(keys)

            XCTAssertEqual(store.stars, 0, json)
            XCTAssertNil(store.equipped, json)
            XCTAssertEqual(Persistence.quarantinedFiles(key: keys.play).count, 1, json)
        }
    }

    // MARK: - 어드벤처 별 조각 이관

    /// 별 조각은 1:1 로 처음 한 번만 옮긴다. 어드벤처 저장본은 그대로 둔다
    func testAdventureShardsMigrateOnlyOnce() throws {
        let keys = makeKeys()
        let adventure = adventureJSON(shards: 17)
        UserDefaults.standard.set(adventure, forKey: keys.adventure)

        let first = makeStore(keys)
        XCTAssertEqual(first.stars, 17)
        XCTAssertEqual(try saved(keys).adventureMigrated, true)

        // 다시 열어도(앱 재실행) 또 더하지 않는다 — 옷장에서 쓴 뒤에도 마찬가지
        let second = makeStore(keys)
        XCTAssertEqual(second.stars, 17)
        try second.buy(.ribbon)
        let third = makeStore(keys)
        XCTAssertEqual(third.stars, 17 - Accessory.ribbon.price)

        XCTAssertEqual(UserDefaults.standard.data(forKey: keys.adventure), adventure)
        XCTAssertTrue(Persistence.quarantinedFiles(key: keys.adventure).isEmpty)
    }

    /// 이관은 기존 별에 더한다(이관 표시가 없는 저장본 — 이관 전에 별을 받은 경우)
    func testMigrationAddsToExistingStars() {
        let keys = makeKeys()
        UserDefaults.standard.set(Data(#"{"stars":5}"#.utf8), forKey: keys.play)
        UserDefaults.standard.set(adventureJSON(shards: 8), forKey: keys.adventure)

        XCTAssertEqual(makeStore(keys).stars, 13)
    }

    /// 어드벤처를 안 한 기기(키 없음)는 0개 이관 — 이관 표시는 남겨 다음에 다시 보지 않는다
    func testMissingAdventureSaveMigratesNothing() throws {
        let keys = makeKeys()
        let store = makeStore(keys)

        XCTAssertEqual(store.stars, 0)
        XCTAssertEqual(try saved(keys).adventureMigrated, true)
        XCTAssertNil(UserDefaults.standard.data(forKey: keys.adventure))

        // 나중에 어드벤처 저장본이 생겨도 다시 옮기지 않는다
        UserDefaults.standard.set(adventureJSON(shards: 9), forKey: keys.adventure)
        XCTAssertEqual(makeStore(keys).stars, 0)
    }

    /// 못 읽는 어드벤처 저장본은 격리되고(Persistence 관례) 옮길 조각은 0
    func testCorruptAdventureSaveMigratesNothing() {
        let keys = makeKeys()
        UserDefaults.standard.set(Data("{깨진 어드벤처".utf8), forKey: keys.adventure)

        XCTAssertEqual(makeStore(keys).stars, 0)
        XCTAssertEqual(Persistence.quarantinedFiles(key: keys.adventure).count, 1)
    }

    // MARK: - 결과 저장(별·최고 기록)

    /// 별은 결과 저장 때만 쌓이고, 점수가 지난 기록보다 높을 때만 새 기록(0점은 기록 아님)
    func testRecordRoundAddsStarsAndKeepsBest() throws {
        let keys = makeKeys()
        let store = makeStore(keys)
        let now = localNoon("2026-11-24")   // 오늘의 미션: 계단 10 · 몬스터 5 · 별 15 — 셋째 판에서 계단 12칸으로 계단 미션 완료
        let stairs10 = Mission(kind: .stairsSteps, target: 10)

        XCTAssertEqual(store.recordRound(.stairs, score: 0, stars: 0, bestStreak: 0, now: now),
                       PlayRoundRecord(previousBest: 0, isRecord: false, starTotal: 0, missionsCompleted: [], allMissionsDone: false))
        XCTAssertEqual(store.recordRound(.stairs, score: 6, stars: 9, bestStreak: 0, now: now),
                       PlayRoundRecord(previousBest: 0, isRecord: true, starTotal: 9, missionsCompleted: [], allMissionsDone: false))
        XCTAssertEqual(store.recordRound(.stairs, score: 6, stars: 2, bestStreak: 0, now: now),
                       PlayRoundRecord(previousBest: 6, isRecord: false, starTotal: 11, missionsCompleted: [stairs10], allMissionsDone: false))
        XCTAssertEqual(store.recordRound(.stairs, score: 4, stars: 1, bestStreak: 0, now: now),
                       PlayRoundRecord(previousBest: 6, isRecord: false, starTotal: 12, missionsCompleted: [], allMissionsDone: false))

        XCTAssertEqual(store.best(.stairs), 6)
        XCTAssertEqual(store.best(.monster), 0)   // 게임마다 따로
        XCTAssertEqual(try saved(keys).stars, 12)
        XCTAssertEqual(try saved(keys).stairsBest, 6)
    }

    /// 도감 처치는 별을 건드리지 않고 바로 저장한다(처음 만난 종만 도감 칸이 채워짐)
    func testRecordDefeatSavesDexWithoutStars() throws {
        let keys = makeKeys()
        let store = makeStore(keys)

        XCTAssertEqual(store.recordDefeat(.green), DexDefeat(newlySeen: true, unlocked: nil))
        XCTAssertEqual(store.recordDefeat(.green), DexDefeat(newlySeen: false, unlocked: nil))
        XCTAssertEqual(store.stars, 0)
        XCTAssertEqual(try saved(keys).dexTotal, 2)
        XCTAssertEqual(MonsterDex.seenCount(try saved(keys).dexSeenMask), 1)
    }

    /// 열 번째 처치에서 분홍이 해금 — 해금 알림은 저장소가 돌려준다
    func testRecordDefeatReportsUnlock() {
        let store = makeStore(makeKeys())
        for _ in 0..<9 { store.recordDefeat(.sky) }
        XCTAssertEqual(store.recordDefeat(.sky).unlocked, .pink)
    }

    // MARK: - 옷장

    /// 사면 별이 줄고 바로 쓴다. 별이 모자라거나 이미 가졌으면 던지고 아무것도 바꾸지 않는다
    func testBuyEquipAndErrors() throws {
        let keys = makeKeys()
        let store = makeStore(keys)
        store.recordRound(.stairs, score: 1, stars: 40, bestStreak: 0)

        XCTAssertThrowsError(try store.buy(.partyHat)) { error in
            XCTAssertEqual(error as? WardrobeError, .notEnoughStars(.partyHat, lack: 10))
        }
        try store.buy(.flowerCrown)
        XCTAssertEqual(store.stars, 10)
        XCTAssertEqual(store.equipped, .flowerCrown)
        XCTAssertThrowsError(try store.buy(.flowerCrown)) { error in
            XCTAssertEqual(error as? WardrobeError, .alreadyOwned(.flowerCrown))
        }

        store.equip(nil)
        XCTAssertNil(try saved(keys).equippedAccessory)
        store.equip(.flowerCrown)
        XCTAssertEqual(try saved(keys).equippedAccessory, .flowerCrown)
        XCTAssertEqual(try saved(keys).stars, 10)
    }
}
