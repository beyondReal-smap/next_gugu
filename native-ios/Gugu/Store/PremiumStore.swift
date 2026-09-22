import Foundation
import StoreKit
import Observation

// 프리미엄 — StoreKit 2 (PremiumProvider.tsx 이식).
// 영수증 검증: StoreKit 2 온디바이스 서명검증(VerificationResult.verified)을 채택.
// 비소모성 상품에 대한 Apple 권장 방식으로, cordova 서버검증 대비 단순·안전하다.

enum PurchaseResult { case ok, cancelled, pending, error }

@MainActor
@Observable
final class PremiumStore {
    private(set) var isPremium: Bool
    var paywallOpen = false
    private(set) var product: Product?
    private(set) var storeReady = false

    /// 현지 가격 (스토어 연결 전 fallback)
    var price: String { product?.displayPrice ?? PremiumConfig.fallbackPrice }

    private var updatesTask: Task<Void, Never>?

    /// DEBUG 전용 — 시뮬레이터 테스트 시 SIMCTL_CHILD_GUGU_PREMIUM=1로 프리미엄 강제
    private let devForcePremium: Bool = {
        #if DEBUG
        return ProcessInfo.processInfo.environment["GUGU_PREMIUM"] == "1"
        #else
        return false
        #endif
    }()

    /// 프리미엄 판정 근거 — 로컬 플래그인지 StoreKit 엔타이틀먼트인지 구분한다
    private(set) var premiumSource: String = "none"

    init() {
        isPremium = Persistence.string(Persistence.premiumKey) == "1"
        if isPremium { premiumSource = "local_flag" }
        print("[Premium] 시작 판정 isPremium=\(isPremium) source=\(premiumSource)")
        if devForcePremium { isPremium = true; return }
        // 앱 외부 구매/환불 실시간 반영 (스토어는 앱 수명 동안 유지되므로 별도 취소 불필요)
        updatesTask = observeTransactions()
        Task {
            await loadProducts()
            await refreshEntitlements()
        }
    }

    // MARK: - 로드

    func loadProducts() async {
        do {
            let products = try await Product.products(for: [PremiumConfig.productID])
            product = products.first
            storeReady = true
        } catch {
            print("[Premium] 상품 로드 실패:", error)
        }
    }

    // MARK: - 구매 / 복원

    func purchase() async -> PurchaseResult {
        guard let product else { return .error }
        do {
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                switch verification {
                case .verified(let transaction):
                    await transaction.finish()
                    grant()
                    return .ok
                case .unverified:
                    print("[Premium] 서명 검증 실패 — 프리미엄 미부여")
                    return .error
                }
            case .userCancelled:
                return .cancelled
            case .pending:
                return .pending
            @unknown default:
                return .error
            }
        } catch {
            print("[Premium] 구매 실패:", error)
            return .error
        }
    }

    func restore() async -> Bool {
        try? await AppStore.sync()
        await refreshEntitlements()
        return isPremium
    }

    // MARK: - 엔타이틀먼트

    func refreshEntitlements() async {
        var owned = false
        for await result in Transaction.currentEntitlements {
            if case .verified(let t) = result,
               t.productID == PremiumConfig.productID,
               t.revocationDate == nil {
                owned = true
                print("[Premium] 보유 트랜잭션 id=\(t.id) 환경=\(t.environment.rawValue) 구매일=\(t.purchaseDate)")
            }
        }
        if owned { grant() } else { revoke() }
        print("[Premium] StoreKit 엔타이틀먼트 owned=\(owned) -> isPremium=\(isPremium) source=\(premiumSource)")
    }

    private func observeTransactions() -> Task<Void, Never> {
        Task { [weak self] in
            for await update in Transaction.updates {
                guard let self else { return }
                if case .verified(let t) = update {
                    await t.finish()
                    await self.refreshEntitlements()
                }
            }
        }
    }

    // MARK: - 상태

    private func grant(source: String = "storekit_entitlement") {
        premiumSource = source
        isPremium = true
        Persistence.setString("1", key: Persistence.premiumKey)
        if paywallOpen { paywallOpen = false }
    }
    private func revoke() {
        premiumSource = "none"
        isPremium = false
        Persistence.remove(Persistence.premiumKey)
    }

    // MARK: - 게이트

    func openPaywall() { paywallOpen = true }
    func closePaywall() { paywallOpen = false }

    func isPremiumMode(_ mode: GameMode) -> Bool { PremiumConfig.isPremiumMode(mode) }

    /// 프리미엄 모드 접근 게이트 — 미보유 시 페이월을 열고 false 반환
    func gate(_ mode: GameMode) -> Bool {
        if !PremiumConfig.isPremiumMode(mode) || isPremium { return true }
        openPaywall()
        return false
    }
}
