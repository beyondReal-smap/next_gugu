import Foundation
import Observation

// 구구 계단·구구 몬스터 보상 저장소 — 모은 별·옷장·도감·최고 기록·오늘의 미션 (키 gugu.play.v1)
// 저장 시점은 시험판 그대로: 별·최고 기록·미션 진행도는 결과 화면을 띄울 때 한 번(판 도중 나가면 안 쌓임),
// 도감은 처치하는 순간(새 몬스터를 만난 경험은 판 결과와 묶지 않음). 옷장 사기·쓰기·벗기·보물상자 열기는 누르는 순간.
// "오늘"은 Commit.todayStr(기기 현지 달력)이다.
// 처음 한 번 어드벤처 별 조각(gugu.adventure.v1 의 starShards)을 별로 1:1 옮긴다(대표님 결정 Q2).
// 어드벤처 타입(Core/Adventure)에는 기대지 않는다 — 그 폴더는 2.9.0 에서 지웠다. 어드벤처 키는 지우지 않는다.

/// 결과 화면 한 번의 저장 결과
struct PlayRoundRecord: Equatable {
    /// 이번 판을 넣기 전의 최고 기록
    let previousBest: Int
    /// 이번 판이 새 기록인지(0점은 기록이 아님)
    let isRecord: Bool
    /// 받은 별을 넣은 뒤의 모은 별
    let starTotal: Int
    /// 이번 판으로 새로 목표를 넘은 오늘의 미션(미션 순서대로)
    let missionsCompleted: [Mission]
    /// 이번 판으로 오늘의 미션 3개가 처음 다 됨 → 홈에서 보물상자를 열 수 있음
    let allMissionsDone: Bool
}

/// 처치 한 번의 도감 변화
struct DexDefeat: Equatable {
    /// 이 종을 처음 물리침 → 도감 칸이 채워짐
    let newlySeen: Bool
    /// 이번 처치로 새로 해금된 종(없으면 nil) → 다음 몬스터로 등장
    let unlocked: MonsterKind?
}

@Observable
final class PlayRewardsStore {
    private(set) var rewards: PlayRewards
    @ObservationIgnored private let key: String

    /// key·adventureKey 는 테스트가 고유 키를 넘겨 실제 저장본과 섞이지 않게 한다
    init(key: String = Persistence.playRewardsKey, adventureKey: String = Persistence.adventureKey) {
        self.key = key
        // 못 읽는 저장본은 Persistence 가 원본을 격리하고 nil 을 준다 — 새 저장본으로 시작(GameStore 와 같은 관례)
        var r = Persistence.load(PlayRewards.self, key: key) ?? PlayRewards()
        if !r.adventureMigrated {
            r.stars += Self.adventureShards(key: adventureKey)
            r.adventureMigrated = true
        }
        #if DEBUG
        Self.applyDebugOverrides(&r, environment: ProcessInfo.processInfo.environment)
        #endif
        rewards = r
        persist()
    }

    // MARK: - 읽기

    var stars: Int { rewards.stars }
    var equipped: Accessory? { rewards.equippedAccessory }
    var dexTotal: Int { rewards.dexTotal }
    var dexSeenMask: Int { rewards.dexSeenMask }

    func best(_ game: PlayGame) -> Int { rewards.best(game) }

    func owns(_ item: Accessory) -> Bool { Wardrobe.owns(item, ownedMask: rewards.ownedMask) }

    // MARK: - 결과 화면

    /// 결과 화면을 띄우는 순간 한 번: 받은 별을 쌓고, 점수가 지난 최고 기록보다 높으면 새 기록으로 저장하고,
    /// 오늘의 미션에 이번 판(오른 계단·물리친 몬스터·받은 별·최고 연속)을 넣는다
    @discardableResult
    func recordRound(_ game: PlayGame, score: Int, stars earned: Int, bestStreak: Int, now: Date = Date()) -> PlayRoundRecord {
        precondition(score >= 0, "점수는 0 이상이어야 함: \(score)")
        precondition(earned >= 0, "받은 별은 0 이상이어야 함: \(earned)")
        precondition(bestStreak >= 0, "최고 연속은 0 이상이어야 함: \(bestStreak)")
        let previous = rewards.best(game)
        let isRecord = score > 0 && score > previous
        if isRecord {
            switch game {
            case .stairs: rewards.stairsBest = score
            case .monster: rewards.monsterBest = score
            }
        }
        rewards.stars += earned  // 넘치면 트랩 — 음수로 돌지 않게
        var log = DailyMissionLog.forDay(rewards.missionLog, day: Commit.todayStr(now))
        let wasAllDone = log.allDone
        let completed = log.record(game, score: score, stars: earned, bestStreak: bestStreak)
        rewards.missionLog = log
        persist()
        return PlayRoundRecord(previousBest: previous, isRecord: isRecord, starTotal: rewards.stars,
                               missionsCompleted: completed, allMissionsDone: !wasAllDone && log.allDone)
    }

    // MARK: - 오늘의 미션

    /// 홈 카드용 — 오늘 미션 3개의 진행도·보물상자·이번 주 도장
    func missionBoard(now: Date = Date()) -> MissionBoard {
        MissionBoard(log: DailyMissionLog.forDay(rewards.missionLog, day: Commit.todayStr(now)), chestDays: rewards.chestDays)
    }

    /// 보물상자 열기: 별 +chestStars, 오늘을 도장에. 받은 별 수를 돌려준다.
    /// 미션이 남았거나 이미 열었으면 ChestError — 화면이 [보물상자 열기!]를 보여 주지 않아야 하는 상황
    @discardableResult
    func openChest(now: Date = Date()) throws -> Int {
        let today = Commit.todayStr(now)
        var log = DailyMissionLog.forDay(rewards.missionLog, day: today)
        guard log.allDone else { throw ChestError.missionsLeft }
        guard !log.chestOpened else { throw ChestError.alreadyOpened }
        log.chestOpened = true
        rewards.missionLog = log
        rewards.chestDays = DailyMissionPlan.stamping(rewards.chestDays, today)
        rewards.stars += DailyMissionPlan.chestStars
        persist()
        return DailyMissionPlan.chestStars
    }

    // MARK: - 도감 (구구 몬스터)

    /// 처치하는 순간: 누적 처치 +1, 처음 만난 종이면 도감에 넣고 바로 저장
    @discardableResult
    func recordDefeat(_ kind: MonsterKind) -> DexDefeat {
        let before = rewards.dexTotal
        let after = before + 1
        let newlySeen = !MonsterDex.isSeen(kind, seenMask: rewards.dexSeenMask)
        rewards.dexTotal = after
        if newlySeen { rewards.dexSeenMask = MonsterDex.markSeen(kind, seenMask: rewards.dexSeenMask) }
        persist()
        return DexDefeat(newlySeen: newlySeen, unlocked: MonsterDex.newlyUnlocked(before: before, after: after))
    }

    // MARK: - 옷장

    /// 사면 바로 쓴다(시험판 WardrobeStore.Buy). 이미 가졌거나 별이 모자라면 WardrobeError — 화면이 먼저 막아야 하는 상황
    func buy(_ item: Accessory) throws {
        let next = try Wardrobe.buy(item, balance: rewards.stars, ownedMask: rewards.ownedMask)
        rewards.stars = next.balance
        rewards.ownedMask = next.ownedMask
        rewards.equipped = item.rawValue
        persist()
    }

    /// nil = 벗기. 갖지 않은 것을 쓰라는 호출은 화면 쪽 버그
    func equip(_ item: Accessory?) {
        if let item { precondition(owns(item), "갖지 않은 장신구는 쓸 수 없음: \(item)") }
        rewards.equipped = item?.rawValue
        persist()
    }

    // MARK: - 저장

    private func persist() {
        Persistence.save(rewards, key: key)
    }

    /// 어드벤처 저장본에서 별 조각 잔액만 읽는다. 키가 없으면(어드벤처를 안 한 기기) 0.
    /// 못 읽는 저장본은 Persistence 가 격리하고 nil 을 준다 — 2.8.x 어드벤처 화면도 같은 저장본을 초기값으로 덮었으므로 옮길 조각이 없다
    private static func adventureShards(key: String) -> Int {
        guard let legacy = Persistence.load(LegacyAdventureShards.self, key: key) else { return 0 }
        guard legacy.starShards >= 0 else {
            print("[PlayRewards] 어드벤처 별 조각이 음수라 옮기지 않음:", legacy.starShards)
            return 0
        }
        return legacy.starShards
    }

    #if DEBUG
    /// 시뮬레이터 스크린숏용 — SIMCTL_CHILD_GUGU_PLAY_STARS=N(모은 별), GUGU_PLAY_EQUIP=<장신구 이름>(가진 것으로 만들고 씀),
    /// GUGU_PLAY_STAIRS_BEST=N(구구 계단 최고 기록 → 깃발), GUGU_PLAY_DEX=N(몬스터 누적 처치 N, 그때까지 해금된 종은
    /// 모두 만난 것으로 — N=9 면 다음 처치로 10번째가 되며 새 종이 바로 등장),
    /// GUGU_PLAY_MISSIONS=half(첫 미션 완료·둘째 절반)|ready(3개 다 함·상자 안 엶 — 둘 다 오늘 도장은 뺌)|opened(다 함·연 상태 + 이번 주 월요일부터 오늘까지 도장).
    /// 잘못된 값은 조용히 넘기지 않고 멈춘다
    private static func applyDebugOverrides(_ r: inout PlayRewards, environment env: [String: String]) {
        if let raw = env["GUGU_PLAY_STARS"] {
            guard let n = Int(raw), n >= 0 else { preconditionFailure("GUGU_PLAY_STARS 는 0 이상 정수: \(raw)") }
            r.stars = n
        }
        if let raw = env["GUGU_PLAY_EQUIP"] {
            guard let item = Accessory.allCases.first(where: { String(describing: $0) == raw }) else {
                preconditionFailure("GUGU_PLAY_EQUIP 는 \(Accessory.allCases.map { String(describing: $0) }) 중 하나: \(raw)")
            }
            r.ownedMask |= item.bit
            r.equipped = item.rawValue
        }
        if let raw = env["GUGU_PLAY_STAIRS_BEST"] {
            guard let n = Int(raw), n >= 0 else { preconditionFailure("GUGU_PLAY_STAIRS_BEST 는 0 이상 정수: \(raw)") }
            r.stairsBest = n
        }
        if let raw = env["GUGU_PLAY_DEX"] {
            guard let n = Int(raw), n >= 0 else { preconditionFailure("GUGU_PLAY_DEX 는 0 이상 정수: \(raw)") }
            r.dexTotal = n
            r.dexSeenMask = MonsterKind.allCases.prefix(MonsterDex.unlockedCount(totalDefeated: n)).reduce(0) { $0 | $1.bit }
        }
        if let raw = env["GUGU_PLAY_MISSIONS"] {
            let today = Commit.todayStr()
            var log = DailyMissionLog(day: today)
            let missions = log.missions
            switch raw {
            case "half":
                debugFill(&log, missions[0], amount: missions[0].target)
                debugFill(&log, missions[1], amount: missions[1].target / 2)
                r.chestDays.removeAll { $0 == today }   // 안 연 오늘에 도장이 남지 않게(앞서 opened 로 심은 저장본 위에 덮을 때)
            case "ready":
                for m in missions { debugFill(&log, m, amount: m.target) }
                r.chestDays.removeAll { $0 == today }
            case "opened":
                for m in missions { debugFill(&log, m, amount: m.target) }
                log.chestOpened = true
                r.chestDays = DailyMissionPlan.week(of: today).filter { $0 <= today }
            default:
                preconditionFailure("GUGU_PLAY_MISSIONS 는 half|ready|opened 중 하나: \(raw)")
            }
            r.missionLog = log
        }
    }

    /// 미션 하나의 진행도를 amount 만큼 — 실제 판 기록(record)과 같은 길로 채운다
    private static func debugFill(_ log: inout DailyMissionLog, _ mission: Mission, amount: Int) {
        switch mission.kind {
        case .stairsSteps: log.record(.stairs, score: amount, stars: 0, bestStreak: 0)
        case .monstersDefeated: log.record(.monster, score: amount, stars: 0, bestStreak: 0)
        case .bestStreak: log.record(.stairs, score: 0, stars: 0, bestStreak: amount)
        case .starsEarned: log.record(.stairs, score: 0, stars: amount, bestStreak: 0)
        }
    }
    #endif
}

/// 어드벤처 저장본(gugu.adventure.v1)에서 별 조각만 읽는 최소 구조 — 나머지 필드는 무시한다.
/// 어드벤처도 starShards 가 없던 구버전 저장본은 0 으로 읽었다(2.8.x AdventureProgress 디코더와 같음)
private struct LegacyAdventureShards: Decodable {
    let starShards: Int

    private enum CodingKeys: String, CodingKey { case starShards }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        starShards = try c.decodeIfPresent(Int.self, forKey: .starShards) ?? 0
    }
}
