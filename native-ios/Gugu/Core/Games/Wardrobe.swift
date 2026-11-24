import Foundation

// 구구 꾸미기(옷장): 모은 별로 사는 머리 장신구 (Unity 시험판 Core/Wardrobe.cs 이식)
// 옷장 화면에서 사고 하나를 골라 쓰면 A 게임 속 구구가 쓰고 나온다.
// 값은 A 한 판 별 20~40개 기준 — 첫 장신구는 한 판, 왕관은 열 판 남짓. 아이 관찰 전 초안이다.
// 표시 이름은 UI 층에서 현지화한다(Core 에는 문자열을 두지 않음).

/// 장신구 — 순서는 값 순서이자 저장 비트 위치라 바꾸지 말 것
enum Accessory: Int, CaseIterable {
    case ribbon       // 빨간 리본
    case flowerCrown  // 꽃 화관
    case partyHat     // 파티 모자
    case pirateHat    // 해적 모자
    case wizardHat    // 마법사 모자
    case crown        // 황금 왕관

    /// 값(별)
    var price: Int {
        switch self {
        case .ribbon: return 15
        case .flowerCrown: return 30
        case .partyHat: return 50
        case .pirateHat: return 80
        case .wizardHat: return 120
        case .crown: return 200
        }
    }

    /// 가진 목록 비트
    var bit: Int { 1 << rawValue }
}

/// 사기가 막힌 이유 — 화면이 먼저 막아야 하는 상황(연타 등)이라 조용히 넘기지 않고 던진다
enum WardrobeError: Error, Equatable {
    case alreadyOwned(Accessory)
    case notEnoughStars(Accessory, lack: Int)
}

enum Wardrobe {
    static let count = Accessory.allCases.count

    static func owns(_ item: Accessory, ownedMask: Int) -> Bool {
        ownedMask & item.bit != 0
    }

    /// 사려면 별이 몇 개 더 필요한지(0 = 살 수 있음)
    static func shortfall(_ item: Accessory, balance: Int) -> Int {
        precondition(balance >= 0, "잔고는 0 이상이어야 함: \(balance)")
        return max(0, item.price - balance)
    }

    /// 사기: 새 잔고와 가진 목록. 이미 가졌거나 별이 모자라면 WardrobeError
    static func buy(_ item: Accessory, balance: Int, ownedMask: Int) throws -> (balance: Int, ownedMask: Int) {
        if owns(item, ownedMask: ownedMask) { throw WardrobeError.alreadyOwned(item) }
        let lack = shortfall(item, balance: balance)
        if lack > 0 { throw WardrobeError.notEnoughStars(item, lack: lack) }
        return (balance - item.price, ownedMask | item.bit)
    }
}
