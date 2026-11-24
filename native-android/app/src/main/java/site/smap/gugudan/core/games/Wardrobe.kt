package site.smap.gugudan.core.games

import kotlin.math.max

// 구구 꾸미기(옷장): 모은 별로 사는 머리 장신구 (Unity 시험판 Core/Wardrobe.cs 이식, iOS Wardrobe.swift 와 1:1)
// 옷장 화면에서 사고 하나를 골라 쓰면 A 게임 속 구구가 쓰고 나온다.
// 값은 A 한 판 별 20~40개 기준 — 첫 장신구는 한 판, 왕관은 열 판 남짓. 아이 관찰 전 초안이다.
// 표시 이름은 UI 층에서 현지화한다(Core 에는 문자열을 두지 않음).

/** 장신구 — 순서는 값 순서이자 저장 비트 위치라 바꾸지 말 것. price = 값(별) */
enum class Accessory(val price: Int) {
    /** 빨간 리본 */
    RIBBON(15),
    /** 꽃 화관 */
    FLOWER_CROWN(30),
    /** 파티 모자 */
    PARTY_HAT(50),
    /** 해적 모자 */
    PIRATE_HAT(80),
    /** 마법사 모자 */
    WIZARD_HAT(120),
    /** 황금 왕관 */
    CROWN(200);

    /** 가진 목록 비트 */
    val bit: Int get() = 1 shl ordinal
}

/** 사기가 막힌 이유 — 화면이 먼저 막아야 하는 상황(연타 등)이라 조용히 넘기지 않고 던진다 */
sealed class WardrobeError(message: String) : Exception(message) {
    data class AlreadyOwned(val item: Accessory) : WardrobeError("이미 가진 장신구: $item")  // i18n:skip
    data class NotEnoughStars(val item: Accessory, val lack: Int) : WardrobeError("별 $lack 개 모자람: $item")  // i18n:skip
}

/** buy 결과 (Swift 는 튜플) — 새 잔고와 가진 목록 */
data class WardrobePurchase(val balance: Int, val ownedMask: Int)

object Wardrobe {
    val COUNT = Accessory.entries.size

    fun owns(item: Accessory, ownedMask: Int): Boolean = (ownedMask and item.bit) != 0

    /** 사려면 별이 몇 개 더 필요한지(0 = 살 수 있음) */
    fun shortfall(item: Accessory, balance: Int): Int {
        require(balance >= 0) { "잔고는 0 이상이어야 함: $balance" }
        return max(0, item.price - balance)
    }

    /** 사기: 새 잔고와 가진 목록. 이미 가졌거나 별이 모자라면 WardrobeError */
    fun buy(item: Accessory, balance: Int, ownedMask: Int): WardrobePurchase {
        if (owns(item, ownedMask)) throw WardrobeError.AlreadyOwned(item)
        val lack = shortfall(item, balance)
        if (lack > 0) throw WardrobeError.NotEnoughStars(item, lack)
        return WardrobePurchase(balance - item.price, ownedMask or item.bit)
    }
}
