import Foundation

// 어드벤처 월드 — 지역/NPC 정적 데이터의 단일 출처 (adventure/world.ts 이식)
// 지역마다 스폰·NPC 배치·보스 위치·랜드마크가 다르고, 소품은 지역 시드 기반으로 생성된다.

enum World {
    // 필드 경계 (±): 플레이어 이동 clamp와 지형 크기의 기준
    static let fieldBound: Double = 16
    // 조우 판정 거리 / 해제 거리 (히스테리시스)
    static let encounterDist: Double = 2.6
    static let encounterExitDist: Double = 3.6

    static func bossId(for table: Int) -> String { "r\(table)-boss" }

    // 난이도 스케일 — 단이 높을수록 튼튼하고 아프게
    static func npcHp(_ kind: NpcKind, _ table: Int) -> Int {
        kind == .boss ? 190 + (table - 2) * 8 : 110 + (table - 2) * 6
    }
    static func npcAttack(_ kind: NpcKind, _ table: Int) -> Int {
        kind == .boss ? 30 + (table - 2) : 22 + (table - 2)
    }

    // 슬롯별 배틀 방식 — 지역마다 체력전/스피드/반격전 하나씩 (보스는 항상 체력전)
    static let normalStyles: [BattleStyle] = [.hp, .speed, .counter]

    // MARK: - 지역별 지형 레이아웃

    struct RegionLayout {
        let spawn: SIMDPoint
        let slots: [SIMDPoint]           // 일반 NPC 3
        let boss: SIMDPoint
        let landmark: Landmark
    }

    private static func pt(_ x: Double, _ z: Double) -> SIMDPoint { SIMDPoint(x: x, z: z) }

    static let layouts: [Int: RegionLayout] = [
        2: RegionLayout(spawn: pt(0, 12), slots: [pt(-8, 3), pt(7, -1), pt(-2, -8)], boss: pt(0, -14), landmark: Landmark(kind: .flowerbed, pos: pt(6, 5))),
        3: RegionLayout(spawn: pt(-10, 12), slots: [pt(-11, 1), pt(-2, -4), pt(7, -9)], boss: pt(12, -13), landmark: Landmark(kind: .oasis, pos: pt(8, 4))),
        4: RegionLayout(spawn: pt(10, 12), slots: [pt(6, 2), pt(-1, -5), pt(8, -8)], boss: pt(-4, -13), landmark: Landmark(kind: .sea, pos: pt(-17, 0))),
        5: RegionLayout(spawn: pt(0, 13), slots: [pt(-9, 6), pt(9, 0), pt(-9, -7)], boss: pt(6, -13), landmark: Landmark(kind: .ancientTree, pos: pt(2, 3))),
        6: RegionLayout(spawn: pt(0, 12), slots: [pt(-11, -2), pt(0, -6), pt(11, -2)], boss: pt(0, -13), landmark: Landmark(kind: .snowman, pos: pt(6, 4))),
        7: RegionLayout(spawn: pt(-8, 13), slots: [pt(6, 7), pt(-7, -1), pt(7, -8)], boss: pt(-6, -13), landmark: Landmark(kind: .pond, pos: pt(0, 4))),
        8: RegionLayout(spawn: pt(0, 13), slots: [pt(-4, 6), pt(4, -1), pt(-4, -7)], boss: pt(2, -14), landmark: Landmark(kind: .volcano, pos: pt(-11, -11))),
        9: RegionLayout(spawn: pt(0, 13.5), slots: [pt(-6, 6), pt(6, 1), pt(-6, -5)], boss: pt(0, -13), landmark: Landmark(kind: .pillars, pos: pt(0, -9))),
    ]

    // MARK: - 결정적 시드 난수 (mulberry32) — 같은 지역은 항상 같은 소품 배치

    static func mulberry32(_ seed: UInt32) -> () -> Double {
        var a = seed
        return {
            a = a &+ 0x6d2b79f5
            var t = (a ^ (a >> 15)) &* (1 | a)
            t = (t &+ ((t ^ (t >> 7)) &* (61 | t))) ^ t
            return Double((t ^ (t >> 14))) / 4294967296
        }
    }

    // 지역 소품 생성 — NPC/스폰/랜드마크를 피해 12~16개 배치
    static func genDecoItems(table: Int, layout: RegionLayout) -> [DecoItem] {
        let rand = mulberry32(UInt32(truncatingIfNeeded: table * 1013 + 77))
        var blocked: [(p: SIMDPoint, r: Double)] = layout.slots.map { ($0, 3.4) }
        blocked.append((layout.boss, 3.8))
        blocked.append((layout.spawn, 3.4))
        blocked.append((layout.landmark.pos, 5))

        var items: [DecoItem] = []
        let count = 12 + Int(rand() * 5)  // 12~16
        var guardCount = 0
        while items.count < count && guardCount < 400 {
            guardCount += 1
            let x = (rand() * 2 - 1) * 15
            let z = (rand() * 2 - 1) * 15
            if blocked.contains(where: { hypot($0.p.x - x, $0.p.z - z) < $0.r }) { continue }
            if items.contains(where: { hypot($0.x - x, $0.z - z) < 2.2 }) { continue }
            items.append(DecoItem(
                x: (x * 10).rounded() / 10,
                z: (z * 10).rounded() / 10,
                scale: ((0.7 + rand() * 0.8) * 100).rounded() / 100
            ))
        }
        return items
    }

    // 별 조각 배치 — NPC/스폰/랜드마크를 피해 5개 (수집 동선이 탐험을 유도)
    static func genStarSpots(table: Int, layout: RegionLayout) -> [SIMDPoint] {
        let rand = mulberry32(UInt32(truncatingIfNeeded: table * 777 + 13))
        var blocked: [(p: SIMDPoint, r: Double)] = layout.slots.map { ($0, 2.6) }
        blocked.append((layout.boss, 3.0))
        blocked.append((layout.spawn, 2.6))
        blocked.append((layout.landmark.pos, 4.5))

        var spots: [SIMDPoint] = []
        var guardCount = 0
        while spots.count < 5 && guardCount < 300 {
            guardCount += 1
            let x = (rand() * 2 - 1) * 13
            let z = (rand() * 2 - 1) * 13
            if blocked.contains(where: { hypot($0.p.x - x, $0.p.z - z) < $0.r }) { continue }
            if spots.contains(where: { hypot($0.x - x, $0.z - z) < 3.0 }) { continue }
            spots.append(SIMDPoint(x: (x * 10).rounded() / 10, z: (z * 10).rounded() / 10))
        }
        return spots
    }

    // MARK: - 지역/NPC 빌드

    struct NpcSeed {
        let name: String
        let color: String
        var greeting: String? = nil
    }

    static func buildNpcs(table: Int, layout: RegionLayout, normals: [NpcSeed], boss: NpcSeed) -> [NpcDef] {
        var list: [NpcDef] = normals.enumerated().map { i, seed in
            NpcDef(
                id: "r\(table)-n\(i + 1)",
                name: seed.name,
                kind: .normal,
                battle: normalStyles[i],
                table: table,
                pos: layout.slots[i],
                hp: npcHp(.normal, table),
                attack: npcAttack(.normal, table),
                color: seed.color,
                greeting: seed.greeting ?? "\(table)단으로 승부하자!"
            )
        }
        list.append(NpcDef(
            id: bossId(for: table),
            name: boss.name,
            kind: .boss,
            battle: .hp,
            table: table,
            pos: layout.boss,
            hp: npcHp(.boss, table),
            attack: npcAttack(.boss, table),
            color: boss.color,
            greeting: boss.greeting ?? "이 지역의 보스다. \(table)단, 각오는 됐지?"
        ))
        return list
    }

    struct RegionSeed {
        let table: Int
        let name: String
        let deco: DecoKind
        let theme: RegionTheme
        let normals: [NpcSeed]
        let boss: NpcSeed
    }

    static func buildRegion(_ seed: RegionSeed) -> RegionDef {
        let layout = layouts[seed.table]!
        return RegionDef(
            table: seed.table,
            name: seed.name,
            deco: seed.deco,
            decoItems: genDecoItems(table: seed.table, layout: layout),
            spawn: layout.spawn,
            landmark: layout.landmark,
            theme: seed.theme,
            npcs: buildNpcs(table: seed.table, layout: layout, normals: seed.normals, boss: seed.boss),
            starSpots: genStarSpots(table: seed.table, layout: layout)
        )
    }

    // MARK: - 지역 데이터

    static let regions: [RegionDef] = [
        buildRegion(RegionSeed(
            table: 2, name: "새싹 들판", deco: .tree,
            theme: RegionTheme(ground: "#7ec850", accent: "#3e9b4f", sky: "#bfe3ff"),
            normals: [
                NpcSeed(name: "토끼 로로", color: "#f4f1ea"),
                NpcSeed(name: "다람쥐 콩이", color: "#c98a4b"),
                NpcSeed(name: "두더지 두두", color: "#8a6f5c"),
            ],
            boss: NpcSeed(name: "들판지기 곰곰", color: "#7a5230", greeting: "들판을 지나가려면 2단쯤은 술술 나와야지!")
        )),
        buildRegion(RegionSeed(
            table: 3, name: "노을 사막", deco: .rock,
            theme: RegionTheme(ground: "#e8c07a", accent: "#c98d52", sky: "#ffd9a0"),
            normals: [
                NpcSeed(name: "여우 사샤", color: "#e07b39"),
                NpcSeed(name: "도마뱀 리지", color: "#a3b86c"),
                NpcSeed(name: "미어캣 모모", color: "#d9b38c"),
            ],
            boss: NpcSeed(name: "사막의 왕 카오", color: "#b3541e", greeting: "사막의 태양보다 뜨거운 3단 대결이다!")
        )),
        buildRegion(RegionSeed(
            table: 4, name: "파도 해변", deco: .rock,
            theme: RegionTheme(ground: "#f2e2b8", accent: "#35a7c2", sky: "#a7dcf0"),
            normals: [
                NpcSeed(name: "게 집게", color: "#e2593b"),
                NpcSeed(name: "갈매기 끼룩", color: "#eef2f5"),
                NpcSeed(name: "거북 토토", color: "#4a9b6e"),
            ],
            boss: NpcSeed(name: "파도술사 옥토", color: "#5a6ec7", greeting: "파도처럼 밀려오는 4단을 견뎌봐라!")
        )),
        buildRegion(RegionSeed(
            table: 5, name: "버섯 숲", deco: .tree,
            theme: RegionTheme(ground: "#4e8f57", accent: "#c96f4a", sky: "#a8d8b0"),
            normals: [
                NpcSeed(name: "버섯돌이 뽀삐", color: "#d95d4e"),
                NpcSeed(name: "올빼미 부엉", color: "#8d7a63"),
                NpcSeed(name: "사슴 다다", color: "#b98a5a"),
            ],
            boss: NpcSeed(name: "숲의 현자 무무", color: "#5b7a4a", greeting: "숲의 지혜를 시험하마. 5단이다!")
        )),
        buildRegion(RegionSeed(
            table: 6, name: "눈꽃 설원", deco: .crystal,
            theme: RegionTheme(ground: "#e8f1f7", accent: "#9cc3d5", sky: "#d7e9f5"),
            normals: [
                NpcSeed(name: "펭귄 핑핑", color: "#3c4652"),
                NpcSeed(name: "물개 살살", color: "#9aa7b5"),
                NpcSeed(name: "눈토끼 하양", color: "#ffffff"),
            ],
            boss: NpcSeed(name: "얼음마녀 서리", color: "#6fa8dc", greeting: "6단이 얼어붙기 전에 답해 보시지!")
        )),
        buildRegion(RegionSeed(
            table: 7, name: "반딧불 늪", deco: .tree,
            theme: RegionTheme(ground: "#4f6b52", accent: "#86c06c", sky: "#55666e"),
            normals: [
                NpcSeed(name: "개구리 팝팝", color: "#6cbf5a"),
                NpcSeed(name: "반딧불 반반", color: "#ffe066"),
                NpcSeed(name: "수달 찰랑", color: "#8a6f52"),
            ],
            boss: NpcSeed(name: "늪의 마법사 그림", color: "#4a5d7a", greeting: "늪에서 빠져나가고 싶다면 7단을 외워라!")
        )),
        buildRegion(RegionSeed(
            table: 8, name: "용암 화산", deco: .rock,
            theme: RegionTheme(ground: "#6b4a44", accent: "#e2593b", sky: "#f0a884"),
            normals: [
                NpcSeed(name: "도롱뇽 라바", color: "#e2593b"),
                NpcSeed(name: "박쥐 까망", color: "#3c3744"),
                NpcSeed(name: "불꽃정령 화르", color: "#ff8c42"),
            ],
            boss: NpcSeed(name: "용암대장 부글", color: "#b3341e", greeting: "펄펄 끓는 8단 맛 좀 볼래?")
        )),
        buildRegion(RegionSeed(
            table: 9, name: "별빛 성", deco: .crystal,
            theme: RegionTheme(ground: "#5a5d8a", accent: "#ffd166", sky: "#2e3160"),
            normals: [
                NpcSeed(name: "기사 은별", color: "#c0c8d8"),
                NpcSeed(name: "마법사 루나", color: "#8a6fd1"),
                NpcSeed(name: "유령 휘휘", color: "#dfe6f0"),
            ],
            boss: NpcSeed(name: "구구단 대마왕", color: "#4a3670", greeting: "드디어 왔군. 9단… 최후의 대결이다!")
        )),
    ]

    static func region(for table: Int) -> RegionDef? {
        regions.first { $0.table == table }
    }
}
