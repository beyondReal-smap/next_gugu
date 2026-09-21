package site.smap.gugudan.core.adventure

// 꾸미기 상점 카탈로그 (Shop.swift 이식 — 가격/구성 동일)

data class ShopColor(val id: String, val name: String, val hex: String, val price: Int)
data class ShopHat(val id: String, val name: String, val emoji: String, val price: Int)

object Shop {
    const val DEFAULT_COLOR_ID = "indigo"

    val colors: List<ShopColor> = listOf(
        ShopColor("indigo", "인디고", "#6366f1", 0),
        ShopColor("rose", "로즈", "#f43f5e", 8),
        ShopColor("amber", "앰버", "#f59e0b", 8),
        ShopColor("emerald", "에메랄드", "#10b981", 8),
        ShopColor("sky", "하늘", "#0ea5e9", 8),
        ShopColor("violet", "바이올렛", "#8b5cf6", 8),
    )

    val hats: List<ShopHat> = listOf(
        ShopHat("sprout", "새싹", "🌱", 10),
        ShopHat("straw", "밀짚모자", "👒", 15),
        ShopHat("tophat", "신사 모자", "🎩", 25),
        ShopHat("crown", "황금 왕관", "👑", 40),
    )

    fun color(id: String): ShopColor? = colors.firstOrNull { it.id == id }
    fun hat(id: String): ShopHat? = hats.firstOrNull { it.id == id }
}
