package site.smap.gugudan.core.adventure

import androidx.annotation.StringRes
import site.smap.gugudan.R

// 꾸미기 상점 카탈로그 (Shop.swift 이식 — 가격/구성 동일). 이름은 리소스 ID — 화면에서 읽는다

data class ShopColor(val id: String, @StringRes val nameRes: Int, val hex: String, val price: Int)
data class ShopHat(val id: String, @StringRes val nameRes: Int, val emoji: String, val price: Int)

object Shop {
    const val DEFAULT_COLOR_ID = "indigo"

    val colors: List<ShopColor> = listOf(
        ShopColor("indigo", R.string.indigo, "#6366f1", 0),
        ShopColor("rose", R.string.rose, "#f43f5e", 8),
        ShopColor("amber", R.string.amber, "#f59e0b", 8),
        ShopColor("emerald", R.string.emerald, "#10b981", 8),
        ShopColor("sky", R.string.sky, "#0ea5e9", 8),
        ShopColor("violet", R.string.violet, "#8b5cf6", 8),
    )

    val hats: List<ShopHat> = listOf(
        ShopHat("sprout", R.string.sprout, "🌱", 10),
        ShopHat("straw", R.string.straw_hat, "👒", 15),
        ShopHat("tophat", R.string.top_hat, "🎩", 25),
        ShopHat("crown", R.string.golden_crown, "👑", 40),
    )

    fun color(id: String): ShopColor? = colors.firstOrNull { it.id == id }
    fun hat(id: String): ShopHat? = hats.firstOrNull { it.id == id }
}
