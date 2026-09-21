import Foundation

// 어드벤처 모드 — 도메인 타입 (adventure/types.ts 이식, UI 무의존)

enum NpcKind: String, Codable {
    case normal, boss
}

// 배틀 방식 — hp: 턴제 체력전 / speed: 목표 문제 수 레이스 / counter: 시간제한 반격전
enum BattleStyle: String, Codable {
    case hp, speed, counter
}

struct NpcDef: Identifiable, Equatable {
    let id: String          // 예: 'r2-n1', 'r2-boss'
    let name: String
    let kind: NpcKind
    let battle: BattleStyle
    let table: Int          // 담당 단 (2~9)
    let pos: SIMDPoint       // 필드 좌표 [x, z]
    let hp: Int             // NPC 체력 (speed에서는 미사용)
    let attack: Int         // 오답 시 플레이어가 받는 데미지 (speed에서는 미사용)
    let color: String       // 캐릭터 몸통 색 (#hex)
    let greeting: String    // 조우 대사
}

struct RegionTheme {
    let ground: String  // 지형 색
    let accent: String  // 소품(나무/바위) 색
    let sky: String     // 하늘/포그 색
}

enum DecoKind: String {
    case tree, rock, crystal
}

// 지역 상징물 — 지역마다 하나씩 배치되는 테마 구조물
enum LandmarkKind: String {
    case flowerbed    // 꽃밭 (들판)
    case oasis        // 오아시스 (사막)
    case sea          // 바다 (해변)
    case ancientTree  // 고목 (숲)
    case snowman      // 눈사람 (설원)
    case pond         // 반딧불 연못 (늪)
    case volcano      // 화산
    case pillars      // 성 기둥 (성문)
}

struct Landmark {
    let kind: LandmarkKind
    let pos: SIMDPoint
}

/// [x, z] 평면 좌표 + [x, z, scale] 소품 — three.js 튜플 대응 경량 타입
struct SIMDPoint: Equatable {
    var x: Double
    var z: Double
}

struct DecoItem: Equatable {
    var x: Double
    var z: Double
    var scale: Double
}

struct RegionDef {
    let table: Int          // 2~9 — 지역 식별자이자 담당 단
    let name: String
    let deco: DecoKind
    let decoItems: [DecoItem]  // 지역 시드 기반 생성
    let spawn: SIMDPoint       // 지역별 입장 위치
    let landmark: Landmark
    let theme: RegionTheme
    let npcs: [NpcDef]
    let starSpots: [SIMDPoint] // 별 조각 배치 (시드 기반, 방문마다 리스폰)
}

struct AdventureProgress: Codable, Equatable {
    var version: Int = 1
    var defeatedNpcs: [String] = []  // 격파한 NPC id 목록 (해금은 여기서 파생)
    var battlesWon: Int = 0
    var battlesLost: Int = 0
    var achievements: [String] = []  // 해금된 어드벤처 전용 업적 id
    var starShards: Int = 0          // 수집한 별 조각 잔액 (수집 +, 상점 구매 -)
    // 꾸미기 상점
    var ownedColors: [String] = []           // 구매한 색상 id (기본 인디고는 항상 보유)
    var ownedHats: [String] = []             // 구매한 모자 id
    var equippedColor: String = "indigo"     // Shop.defaultColorID
    var equippedHat: String? = nil

    init() {}

    // 구버전 저장소(신규 필드 없던 시절) 호환 — 누락 키는 기본값으로
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? 1
        defeatedNpcs = try c.decodeIfPresent([String].self, forKey: .defeatedNpcs) ?? []
        battlesWon = try c.decodeIfPresent(Int.self, forKey: .battlesWon) ?? 0
        battlesLost = try c.decodeIfPresent(Int.self, forKey: .battlesLost) ?? 0
        achievements = try c.decodeIfPresent([String].self, forKey: .achievements) ?? []
        starShards = try c.decodeIfPresent(Int.self, forKey: .starShards) ?? 0
        ownedColors = try c.decodeIfPresent([String].self, forKey: .ownedColors) ?? []
        ownedHats = try c.decodeIfPresent([String].self, forKey: .ownedHats) ?? []
        equippedColor = try c.decodeIfPresent(String.self, forKey: .equippedColor) ?? "indigo"
        equippedHat = try c.decodeIfPresent(String.self, forKey: .equippedHat)
    }
}
