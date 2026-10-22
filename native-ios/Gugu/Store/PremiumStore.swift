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
    /// 스토어에서 상품을 실제로 받아 왔는지 — 조회가 실패했거나 결과가 비었으면 준비된 것이 아니다.
    /// (예전에는 결과가 비어도 true 가 돼서 "준비 중" 안내도 재조회도 없이 구매가 실패로 끝났다)
    var storeReady: Bool { product != nil }

    /// 스토어가 알려 준 현지 가격 — 상품을 받기 전에는 nil.
    /// 고정 원화 값을 대신 보여 주면 해외 스토어에서 실제와 다른 통화·금액이 보이므로 쓰지 않는다
    var price: String? { product?.displayPrice }

    private var updatesTask: Task<Void, Never>?
    /// 상품 조회가 동시에 두 번 나가지 않게 한다 — 시작 시 조회와 페이월 진입 조회가 겹칠 수 있다
    @ObservationIgnored private var isLoadingProducts = false

    #if DEBUG
    /// DEBUG 전용 — SIMCTL_CHILD_GUGU_FAIL_FIRST_PRODUCTS=1 이면 첫 상품 조회를 강제로 실패시켜
    /// "오프라인으로 시작 → 페이월에서 재조회" 경로를 시뮬레이터에서도 재현한다
    @ObservationIgnored private var failNextProductLoad =
        ProcessInfo.processInfo.environment["GUGU_FAIL_FIRST_PRODUCTS"] == "1"
    #endif

    /// DEBUG 전용 — 시뮬레이터 테스트 시 SIMCTL_CHILD_GUGU_PREMIUM=1로 프리미엄 강제
    private let devForcePremium: Bool = {
        #if DEBUG
        return ProcessInfo.processInfo.environment["GUGU_PREMIUM"] == "1"
        #else
        return false
        #endif
    }()

    /// 프리미엄 판정 근거 — 로컬 플래그인지 StoreKit 엔타이틀먼트인지 서버 부여인지 구분한다
    private(set) var premiumSource: String = "none"

    /// 스토어(StoreKit) 기준 보유. 로컬 플래그로 캐시해 오프라인으로 시작해도 유지된다.
    @ObservationIgnored private var storeOwned: Bool
    @ObservationIgnored private var storeSource = "local_flag"
    /// 서버 구매 원장 기준 부여 — 운영자가 DB 에서 유료로 바꾼 계정도 여기로 들어온다.
    /// 스토어 보유와 따로 둔다: 스토어 재확인(owned=false)이 서버 부여를 지우면 안 되고,
    /// 서버 응답(premium=false)이 실제 스토어 구매를 지워도 안 된다.
    private(set) var serverGranted: Bool

    init() {
        // @Observable 프로퍼티는 모든 저장 프로퍼티가 초기화되기 전에 읽을 수 없어 지역 값으로 계산한다
        let owned = Persistence.string(Persistence.premiumKey) == "1"
        let granted = Persistence.string(Persistence.serverPremiumKey) == "1"
        storeOwned = owned
        serverGranted = granted
        isPremium = owned || granted
        premiumSource = owned ? "local_flag" : (granted ? "server_flag" : "none")
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

    /// 스토어에서 상품을 받아 온다. 이미 있거나 조회 중이면 건너뛴다 —
    /// 시작 시 1회에 더해 페이월 진입·구매 탭에서도 불러, 오프라인으로 시작한 뒤에도 복구된다.
    func loadProducts() async {
        guard product == nil, !isLoadingProducts else { return }
        isLoadingProducts = true
        defer { isLoadingProducts = false }
        do {
            #if DEBUG
            if failNextProductLoad {
                failNextProductLoad = false
                throw URLError(.notConnectedToInternet)
            }
            #endif
            let products = try await Product.products(for: [PremiumConfig.productID])
            product = products.first
            if let product {
                print("[Premium] 상품 로드 완료 id=\(product.id) 가격=\(product.displayPrice)")
            } else {
                print("[Premium] 상품 조회 결과가 비어 있음 id=\(PremiumConfig.productID)")
            }
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

    // MARK: - 서버 부여

    /// 서버 구매 원장 기준 프리미엄을 확인한다 — 운영자가 DB 에서 부여·회수하면 여기서 반영된다.
    /// 앱 시작·앱으로 돌아올 때·계정이 바뀔 때 부른다.
    /// 실패(오프라인·서버 장애·토큰 없음)면 마지막 값을 그대로 둔다 — 로컬 우선, 네트워크 없이도 동작해야 한다.
    func refreshServerEntitlement(auth: AuthStore) async {
        if devForcePremium { return }
        let userId = auth.userId
        guard let token = await auth.accessToken() else { return }
        do {
            let status = try await LearningApi.premiumStatus(accessToken: token)
            // 응답을 기다리는 사이 계정이 바뀌었으면(삭제·로그아웃 후 새 익명 계정) 예전 계정 결과는 버린다
            guard auth.userId == userId else { return }
            applyServerGrant(status.premium)
            print("[Premium] 서버 확인 premium=\(status.premium) -> isPremium=\(isPremium) source=\(premiumSource)")
        } catch {
            print("[Premium] 서버 프리미엄 확인 실패(마지막 값 유지):", error.localizedDescription)
        }
    }

    /// 계정이 바뀔 때(로그인·로그아웃·계정 삭제) 예전 계정의 서버 부여분을 버린다.
    /// 스토어 구매는 Apple ID 기준이라 그대로 둔다. 새 계정 값은 refreshServerEntitlement 가 다시 받는다.
    func clearServerGrant() {
        applyServerGrant(false)
    }

    private func applyServerGrant(_ granted: Bool) {
        serverGranted = granted
        Persistence.setString(granted ? "1" : nil, key: Persistence.serverPremiumKey)
        updatePremium()
    }

    // MARK: - 상태

    private func grant(source: String = "storekit_entitlement") {
        storeOwned = true
        storeSource = source
        Persistence.setString("1", key: Persistence.premiumKey)
        updatePremium()
    }
    private func revoke() {
        storeOwned = false
        Persistence.remove(Persistence.premiumKey)
        updatePremium()
    }

    /// 두 근거(스토어 보유 · 서버 부여) 중 하나라도 있으면 프리미엄
    private func updatePremium() {
        isPremium = devForcePremium || storeOwned || serverGranted
        premiumSource = storeOwned ? storeSource : (serverGranted ? "server" : "none")
        // 페이월은 여기서 닫지 않는다 — 결제 직후 로그인 권유를 띄워야 해서 PaywallView 가 닫기를 맡는다
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
