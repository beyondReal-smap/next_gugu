package site.smap.gugudan.core

// 프리미엄(단건 결제) 설정 — 상품/무료 경계의 단일 출처 (PremiumConfig.swift 이식)

object PremiumConfig {
    /** 스토어 상품 ID — Google Play 1회성 인앱 상품 (기존 등록분 재사용) */
    const val PRODUCT_ID = "site.smap.next.premium"

    /** 프리미엄 전용 게임 모드 — 학습/스피드런은 무료 유지 */
    val premiumModes: Set<GameMode> = setOf(
        GameMode.CHALLENGE, GameMode.SURVIVAL, GameMode.MISSING, GameMode.TRUEFALSE)

    fun isPremiumMode(mode: GameMode): Boolean = mode in premiumModes

    /** 움직이며 놀기(점프·레인·바구니) 무료 체험 문제 수 — 이만큼 풀면 게임을 멈추고 결제 화면으로 안내한다 */
    const val MINIGAME_TRIAL_QUESTIONS = 3

    /** 구구 팡팡 무료 체험 웨이브 수 — 첫 웨이브를 깨면 판을 멈추고 결제 화면으로 안내한다 (PopState.trialLeft 단위가 웨이브) */
    const val POP_TRIAL_WAVES = 1

    /** 법적 고지 링크 — 앱 언어별 문서. 한국어는 기존 주소 그대로, 영어·일본어는 /en/·/ja/ 아래 (iOS 와 같은 규칙).
     *  앱별 언어를 바꿔도 프로세스가 살아 있으므로 열 때마다 현재 언어로 만든다 */
    object Legal {
        val TERMS: String get() = url("terms")
        val PRIVACY: String get() = url("privacy")
        val DELETE_ACCOUNT: String get() = url("delete-account")

        fun url(page: String, language: AppLanguage = AppLanguage.current): String {
            val base = "https://gugu.aiagnt.kr"
            return if (language == AppLanguage.KO) "$base/$page" else "$base/${language.code}/$page"
        }
    }
}
