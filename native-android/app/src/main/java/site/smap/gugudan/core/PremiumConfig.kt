package site.smap.gugudan.core

// 프리미엄(단건 결제) 설정 — 상품/무료 경계의 단일 출처 (PremiumConfig.swift 이식)

object PremiumConfig {
    /** 스토어 상품 ID — Google Play 1회성 인앱 상품 (기존 등록분 재사용) */
    const val PRODUCT_ID = "site.smap.next.premium"

    /** 오퍼링 로드 전 표시용 가격 */
    const val FALLBACK_PRICE = "₩4,400"

    /** 프리미엄 전용 게임 모드 — 학습/스피드런은 무료 유지 */
    val premiumModes: Set<GameMode> = setOf(
        GameMode.CHALLENGE, GameMode.SURVIVAL, GameMode.MISSING, GameMode.TRUEFALSE)

    fun isPremiumMode(mode: GameMode): Boolean = mode in premiumModes

    /** 법적 고지 링크 */
    object Legal {
        const val TERMS = "https://gugu.smap.site/terms"
        const val PRIVACY = "https://gugu.smap.site/privacy"
        const val DELETE_ACCOUNT = "https://gugu.smap.site/delete-account"
    }
}
