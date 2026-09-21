import Foundation

// 꾸미기 상점 — 별 조각으로 구매하는 플레이어 커스터마이징 카탈로그 (순수 데이터, UI 무의존)

struct ShopColor: Identifiable, Equatable {
    let id: String
    let name: String
    let hex: String
    let price: Int   // 0 = 기본 제공
}

struct ShopHat: Identifiable, Equatable {
    let id: String
    let name: String
    let emoji: String  // 2D 프리뷰/목록용
    let price: Int
}

enum Shop {
    static let defaultColorID = "indigo"

    static let colors: [ShopColor] = [
        ShopColor(id: "indigo", name: "인디고", hex: "#6366f1", price: 0),
        ShopColor(id: "rose", name: "로즈", hex: "#f43f5e", price: 8),
        ShopColor(id: "amber", name: "앰버", hex: "#f59e0b", price: 8),
        ShopColor(id: "emerald", name: "에메랄드", hex: "#10b981", price: 8),
        ShopColor(id: "sky", name: "하늘", hex: "#0ea5e9", price: 8),
        ShopColor(id: "violet", name: "바이올렛", hex: "#8b5cf6", price: 8),
    ]

    static let hats: [ShopHat] = [
        ShopHat(id: "sprout", name: "새싹", emoji: "🌱", price: 10),
        ShopHat(id: "straw", name: "밀짚모자", emoji: "👒", price: 15),
        ShopHat(id: "tophat", name: "신사 모자", emoji: "🎩", price: 25),
        ShopHat(id: "crown", name: "황금 왕관", emoji: "👑", price: 40),
    ]

    static func color(_ id: String) -> ShopColor? { colors.first { $0.id == id } }
    static func hat(_ id: String) -> ShopHat? { hats.first { $0.id == id } }
}
