import Foundation

// 프리미엄(단건 결제) 설정 — 상품/무료 경계의 단일 출처 (premiumConfig.ts 이식)

enum PremiumConfig {
    /// 스토어 상품 ID — 비소모성(iOS)
    static let productID = "site.smap.next.premium"

    /// 자체 영수증 검증 서버 (P5에서 서버 vs StoreKit2 온디바이스 최종 결정)
    static let validatorURL = "https://gugu.smap.site/api/iap/cdv-validate"

    /// 프리미엄 전용 게임 모드 — 학습/스피드런은 무료 유지
    static let premiumModes: Set<GameMode> = [.challenge, .survival, .missing, .truefalse]

    static func isPremiumMode(_ mode: GameMode) -> Bool { premiumModes.contains(mode) }

    /// 움직이며 놀기(점프·레인·바구니) 무료 체험 문제 수 — 이만큼 풀면 게임을 멈추고 결제 화면으로 안내한다
    static let minigameTrialQuestions = 3

    /// 법적 고지 링크 (페이월·프로필에서 사용, Apple 심사 필수) — 앱 언어별 문서.
    /// 한국어는 기존 주소 그대로, 영어·일본어는 /en/·/ja/ 아래 (해외 법령 조항을 보강한 별도 문서)
    enum Legal {
        static let terms = url("terms")
        static let privacy = url("privacy")
        static let deleteAccount = url("delete-account")

        static func url(_ page: String, language: AppLanguage = .current) -> String {
            let base = "https://gugu.smap.site"
            return language == .ko ? "\(base)/\(page)" : "\(base)/\(language.rawValue)/\(page)"
        }
    }
}
