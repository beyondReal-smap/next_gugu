package site.smap.gugudan.core

// 프리미엄(단건 결제) 설정 — 상품/무료 경계의 단일 출처 (PremiumConfig.swift 이식)

object PremiumConfig {
    /** 스토어 상품 ID — Google Play 1회성 인앱 상품 (기존 등록분 재사용) */
    const val PRODUCT_ID = "site.smap.next.premium"

    /** 프리미엄 전용 게임 모드 — 학습/스피드런은 무료 유지 */
    val premiumModes: Set<GameMode> = setOf(
        GameMode.CHALLENGE, GameMode.SURVIVAL, GameMode.MISSING, GameMode.TRUEFALSE)

    fun isPremiumMode(mode: GameMode): Boolean = mode in premiumModes

    /** 게임(구구 계단·구구 몬스터) 무료 체험 수 — 맞힌 문제(계단 칸·물리친 몬스터)가 이만큼이면 판을 멈추고 결제 화면으로 안내한다.
     *  3문제는 게임 맛을 보기엔 짧아서 10문제로 늘렸다(2026-11-24 대표님 결정, iOS 와 같은 값). 배지·안내·결제 화면 문구는 이 값을 끼워 쓴다 */
    const val MINIGAME_TRIAL_QUESTIONS = 10

    /** 일본 스토어 가격이 정확히 ¥500(500엔 동전 하나)인가 — 맞으면 "커피 한 잔 값" 대신 "ワンコイン(동전 하나)" 문구를 쓴다
     *  (iOS PremiumConfig.isOneCoinPrice 와 같은 규칙). 일본에서 ワンコイン은 부담 없는 가격을 뜻하는 관용 표현이다.
     *  스토어 가격이 바뀌면 문구가 거짓이 되므로 실제 가격(Play 는 1/1,000,000 단위 micros)으로 판단한다 */
    fun isOneCoinPrice(priceAmountMicros: Long, currencyCode: String): Boolean =
        currencyCode == "JPY" && priceAmountMicros == 500_000_000L

    /** 법적 고지 링크 — 앱 언어별 문서. 한국어는 기존 주소 그대로, 영어·일본어는 /en/·/ja/ 아래 (iOS 와 같은 규칙).
     *  앱별 언어를 바꿔도 프로세스가 살아 있으므로 열 때마다 현재 언어로 만든다 */
    object Legal {
        val TERMS: String get() = url("terms")
        val PRIVACY: String get() = url("privacy")
        val DELETE_ACCOUNT: String get() = url("delete-account")

        fun url(page: String, language: AppLanguage = AppLanguage.current): String {
            val base = "https://gugu.aiagnt.kr"
            return when (language) {
                AppLanguage.KO -> "$base/$page"
                AppLanguage.JA -> "$base/ja/$page"
                AppLanguage.EN, AppLanguage.HI, AppLanguage.VI, AppLanguage.ID -> "$base/en/$page"
            }
        }
    }
}
