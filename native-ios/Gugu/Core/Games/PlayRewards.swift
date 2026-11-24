import Foundation

// A·B 게임 보상 저장본 (키 gugu.play.v1, PlayRewardsStore 가 읽고 씀)
// 모은 별(옷장 잔고)·가진/쓰는 장신구·몬스터 도감·게임별 최고 기록·어드벤처 별 조각 이관 표시·오늘의 미션·도장을 한 덩어리로 둔다.
// Unity 시험판은 PlayerPrefs 키 여러 개(stars.total·wardrobe.*·dex.*·best.*)였는데, 앱은 GameState 처럼 Codable 하나로 묶는다.

/// 최고 기록을 따로 두는 게임
enum PlayGame: Equatable {
    case stairs   // 구구 계단 — 오른 계단
    case monster  // 구구 몬스터 — 물리친 몬스터
}

struct PlayRewards: Codable, Equatable {
    var version: Int = 1
    /// 모은 별 잔고 — 결과 화면 때 쌓이고 옷장에서 쓰면 줄어든다
    var stars: Int = 0
    /// 가진 장신구 비트 묶음(Accessory.bit)
    var ownedMask: Int = 0
    /// 쓰고 있는 장신구(Accessory.rawValue). nil = 맨머리
    var equipped: Int? = nil
    /// 구구 몬스터 누적 처치(판을 넘어 쌓임 — 도감 해금 기준)
    var dexTotal: Int = 0
    /// 한 번이라도 물리친 종 비트 묶음(MonsterKind.bit)
    var dexSeenMask: Int = 0
    /// 구구 계단 최고 기록(오른 계단)
    var stairsBest: Int = 0
    /// 구구 몬스터 최고 기록(물리친 몬스터)
    var monsterBest: Int = 0
    /// 어드벤처 별 조각을 별로 옮겼는지 — 처음 한 번만 옮긴다
    var adventureMigrated: Bool = false
    /// 오늘의 미션 기록 — 마지막으로 게임 결과·보물상자가 있던 날 것(오늘이 아니면 진행도 0 으로 본다). nil = 아직 없음(구버전 저장본)
    var missionLog: DailyMissionLog? = nil
    /// 보물상자를 연 날 — 이번 주 도장. 오름차순·중복 없음·최근 DailyMissionPlan.chestDayCap 개
    var chestDays: [String] = []

    init() {}

    /// 쓰고 있는 장신구
    var equippedAccessory: Accessory? {
        equipped.map { raw in
            guard let item = Accessory(rawValue: raw) else { preconditionFailure("검증된 저장본에 없는 장신구 번호: \(raw)") }
            return item
        }
    }

    func best(_ game: PlayGame) -> Int {
        switch game {
        case .stairs: return stairsBest
        case .monster: return monsterBest
        }
    }
}

extension PlayRewards {
    /// 필드를 나중에 늘려도 구버전 저장본이 읽히게 — 누락 키는 기본값(GameState 와 같은 방식).
    /// 타입이 틀리거나 값이 규칙 밖이면 throw 해서 Persistence 가 원본을 격리하게 둔다(조용히 고치지 않음)
    init(from decoder: Decoder) throws {
        self.init()
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? version
        stars = try c.decodeIfPresent(Int.self, forKey: .stars) ?? stars
        ownedMask = try c.decodeIfPresent(Int.self, forKey: .ownedMask) ?? ownedMask
        equipped = try c.decodeIfPresent(Int.self, forKey: .equipped)
        dexTotal = try c.decodeIfPresent(Int.self, forKey: .dexTotal) ?? dexTotal
        dexSeenMask = try c.decodeIfPresent(Int.self, forKey: .dexSeenMask) ?? dexSeenMask
        stairsBest = try c.decodeIfPresent(Int.self, forKey: .stairsBest) ?? stairsBest
        monsterBest = try c.decodeIfPresent(Int.self, forKey: .monsterBest) ?? monsterBest
        adventureMigrated = try c.decodeIfPresent(Bool.self, forKey: .adventureMigrated) ?? adventureMigrated
        missionLog = try c.decodeIfPresent(DailyMissionLog.self, forKey: .missionLog)
        chestDays = try c.decodeIfPresent([String].self, forKey: .chestDays) ?? chestDays

        let allAccessories = Accessory.allCases.reduce(0) { $0 | $1.bit }
        let allKinds = MonsterKind.allCases.reduce(0) { $0 | $1.bit }
        guard stars >= 0, dexTotal >= 0, stairsBest >= 0, monsterBest >= 0 else {
            throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "음수 값이 있는 보상 저장본"))  // i18n:skip — 개발용 디코딩 오류
        }
        guard ownedMask & ~allAccessories == 0, dexSeenMask & ~allKinds == 0 else {
            throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "알 수 없는 장신구·몬스터 비트"))  // i18n:skip — 개발용 디코딩 오류
        }
        guard chestDays.allSatisfy({ DailyMissionPlan.dayNumber($0) != nil }),
              zip(chestDays, chestDays.dropFirst()).allSatisfy({ $0 < $1 }),
              chestDays.count <= DailyMissionPlan.chestDayCap else {
            throw DecodingError.dataCorrupted(.init(codingPath: [CodingKeys.chestDays],
                                                    debugDescription: "도장 날짜가 형식·순서·개수 규칙 밖: \(chestDays)"))  // i18n:skip — 개발용 디코딩 오류
        }
        if let equipped {
            guard let item = Accessory(rawValue: equipped), Wardrobe.owns(item, ownedMask: ownedMask) else {
                throw DecodingError.dataCorrupted(.init(codingPath: [CodingKeys.equipped],
                                                        debugDescription: "갖지 않은 장신구를 쓰고 있음: \(equipped)"))  // i18n:skip — 개발용 디코딩 오류
            }
        }
    }
}
