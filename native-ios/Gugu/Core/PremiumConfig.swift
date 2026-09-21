import Foundation

// 프리미엄(단건 결제) 설정 — 상품/무료 경계의 단일 출처 (premiumConfig.ts 이식)

enum PremiumConfig {
    /// 스토어 상품 ID — 비소모성(iOS)
    static let productID = "site.smap.next.premium"

    /// 자체 영수증 검증 서버 (P5에서 서버 vs StoreKit2 온디바이스 최종 결정)
    static let validatorURL = "https://gugu.smap.site/api/iap/cdv-validate"

    /// 오퍼링 로드 전 표시용 가격 (스토어 연결되면 실제 가격으로 대체)
    static let fallbackPrice = "₩4,400"

    /// 프리미엄 전용 게임 모드 — 학습/스피드런은 무료 유지
    static let premiumModes: Set<GameMode> = [.challenge, .survival, .missing, .truefalse]

    static func isPremiumMode(_ mode: GameMode) -> Bool { premiumModes.contains(mode) }

    /// 법적 고지 링크 (페이월·프로필에서 사용, Apple 심사 필수)
    enum Legal {
        static let terms = "https://gugu.smap.site/terms"
        static let privacy = "https://gugu.smap.site/privacy"
        static let deleteAccount = "https://gugu.smap.site/delete-account"
    }
}
