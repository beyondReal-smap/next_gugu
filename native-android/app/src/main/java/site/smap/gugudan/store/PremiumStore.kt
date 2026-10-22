package site.smap.gugudan.store

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.GetBillingConfigParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.PremiumConfig
import site.smap.gugudan.services.DevProps
import site.smap.gugudan.services.LearningApi
import site.smap.gugudan.services.LearningApiException
import site.smap.gugudan.services.Persistence

// 프리미엄 — Google Play Billing 8 (iOS PremiumStore/StoreKit2 대응).
// 검증: Play 결제는 스토어 서명 기반 — acknowledge + queryPurchases 엔타이틀먼트로 판정 (iOS 온디바이스 검증과 동일 정책)

enum class PurchaseResult { OK, CANCELLED, PENDING, ERROR }

private const val TAG = "Premium"

class PremiumStore(
    context: Context,
    private val persistence: Persistence,
    private val devForcePremium: Boolean = false,
) {
    /** 스토어(Play) 기준 보유. 로컬 플래그로 캐시해 오프라인으로 시작해도 유지된다. */
    private var storeOwned = persistence.getString(Persistence.PREMIUM_KEY) == "1"
    private var storeSource = "local_flag"

    /**
     * 서버 구매 원장 기준 부여 — 운영자가 DB 에서 유료로 바꾼 계정도 여기로 들어온다.
     * 스토어 보유와 따로 둔다: Play 재확인(owned=false)이 서버 부여를 지우면 안 되고,
     * 서버 응답(premium=false)이 실제 Play 구매를 지워도 안 된다.
     */
    var serverGranted: Boolean by mutableStateOf(persistence.getString(Persistence.SERVER_PREMIUM_KEY) == "1")
        private set

    var isPremium: Boolean by mutableStateOf(devForcePremium || storeOwned || serverGranted)
        private set
    var paywallOpen: Boolean by mutableStateOf(false)
    /** 스토어 표시 가격 — 상품을 불러오기 전에는 null (화면은 가격 자리를 비우거나 로딩 표시, iOS 와 같다).
     *  고정 원화 폴백을 두면 해외에서 상품을 불러오기 전 원화가 보인다 */
    var price: String? by mutableStateOf(null)
        private set
    /** Play 결제 국가(ISO 3166-1 alpha-2, 예: "KR") — 광고 알림 토글 노출 판정(PromoVisibility)에 쓴다. 모르면 null */
    var billingCountry: String? by mutableStateOf(null)
        private set
    var storeReady: Boolean by mutableStateOf(false)
        private set

    private var productDetails: ProductDetails? = null
    private var pendingResult: ((PurchaseResult) -> Unit)? = null
    /** 상품 조회가 동시에 두 번 나가지 않게 한다 (메인 스레드에서만 다룬다) */
    private var queryingProduct = false
    /** DEBUG 전용 — adb shell setprop debug.gugu.fail_first_products 1 이면 첫 상품 조회를 강제로 실패시켜 재조회 경로를 재현한다 */
    private var failNextProductQuery = DevProps.flag("debug.gugu.fail_first_products")
    private val main = Handler(Looper.getMainLooper())

    /** 서버 구매 등록에 쓰는 Play 구매 토큰 (검증된 구매가 있을 때만) */
    var lastPurchaseToken: String? = null
        private set

    /** 프리미엄 판정 근거 — 로컬 플래그인지 Play 보유 확인인지 서버 부여인지 구분한다 */
    var premiumSource: String = "none"
        private set

    private val billing: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        premiumSource = if (storeOwned) "local_flag" else if (serverGranted) "server_flag" else "none"
        Log.i(TAG, "시작 판정 isPremium=$isPremium source=$premiumSource")
        if (!devForcePremium) connect()
    }

    private fun connect() {
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    refreshEntitlements()
                    queryBillingCountry()
                } else {
                    Log.w("Premium", "Billing 연결 실패: ${result.debugMessage}")
                }
            }
            override fun onBillingServiceDisconnected() {
                // 끊긴 채로 두면 구매가 계속 실패한다 — 준비 안 됨으로 돌리고, 페이월 진입·구매 탭 때 ensureStoreReady() 가 재연결한다.
                // 진행 중이던 조회는 끊긴 연결에서 끝나지 못할 수 있으므로 중복 방지 가드도 풀어, 재연결 뒤 재조회가 막히지 않게 한다
                main.post { storeReady = false; queryingProduct = false }
            }
        })
    }

    /**
     * 스토어를 다시 준비시킨다 — 시작 시 오프라인이었거나 연결이 끊긴 뒤에도 페이월에서 복구한다.
     * 끊겼으면 재연결(연결되면 상품 조회까지 이어진다), 연결됐는데 상품이 없으면 재조회한다. 메인 스레드에서 호출한다.
     */
    fun ensureStoreReady() {
        if (devForcePremium || storeReady) return
        Log.i(TAG, "스토어 재준비 시도 connectionState=${billing.connectionState}")
        when (billing.connectionState) {
            BillingClient.ConnectionState.DISCONNECTED -> connect()
            BillingClient.ConnectionState.CONNECTED -> queryProduct()
            else -> Unit   // CONNECTING(진행 중) — 연결이 끝나면 조회가 이어진다 / CLOSED(쓰지 않음)
        }
    }

    private fun queryProduct() {
        if (queryingProduct) return
        if (failNextProductQuery) {
            failNextProductQuery = false
            Log.w(TAG, "DEBUG 훅: 첫 상품 조회를 강제로 실패시킴")
            return
        }
        queryingProduct = true
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PremiumConfig.PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()))
            .build()
        billing.queryProductDetailsAsync(params) { result, productDetailsResult ->
            main.post {
                queryingProduct = false
                // PBL 8: 콜백 2번째 인자가 List<ProductDetails> → QueryProductDetailsResult 로 변경.
                val products = productDetailsResult.productDetailsList
                if (result.responseCode == BillingClient.BillingResponseCode.OK && products.isNotEmpty()) {
                    productDetails = products.first()
                    productDetails?.oneTimePurchaseOfferDetails?.formattedPrice?.let { price = it }
                    storeReady = true
                } else {
                    Log.w("Premium", "상품 조회 실패: ${result.debugMessage}")
                }
            }
        }
    }

    /** Play 결제 국가 조회 — iOS Storefront.countryCode 대응. 실패하면 null 로 두어 광고 토글을 숨긴다(알 수 없으면 숨김) */
    private fun queryBillingCountry() {
        billing.getBillingConfigAsync(GetBillingConfigParams.newBuilder().build()) { result, config ->
            main.post {
                if (result.responseCode == BillingClient.BillingResponseCode.OK && config != null) {
                    billingCountry = config.countryCode
                    Log.i(TAG, "결제 국가 ${config.countryCode}")
                } else {
                    Log.w(TAG, "결제 국가 조회 실패: ${result.debugMessage}")
                }
            }
        }
    }

    /** 엔타이틀먼트 재확인 — 구매/환불 반영 (iOS refreshEntitlements 대응) */
    fun refreshEntitlements(onDone: ((Boolean) -> Unit)? = null) {
        billing.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            main.post {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    onDone?.invoke(isPremium)
                    return@post
                }
                val owned = purchases.any {
                    PremiumConfig.PRODUCT_ID in it.products &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                // 서버 구매 등록(POST /api/iap/verify)에 쓸 토큰을 남긴다
                lastPurchaseToken = purchases.firstOrNull {
                    PremiumConfig.PRODUCT_ID in it.products &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                }?.purchaseToken
                purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
                    .forEach { acknowledge(it) }
                if (owned) grant("play_entitlement") else revoke()
                Log.i(TAG, "Play 보유 확인 owned=$owned -> isPremium=$isPremium source=$premiumSource token=${lastPurchaseToken?.take(12)}")
                onDone?.invoke(owned)
            }
        }
    }

    fun purchase(activity: Activity, onResult: (PurchaseResult) -> Unit) {
        val details = productDetails ?: run { onResult(PurchaseResult.ERROR); return }
        pendingResult = onResult
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details).build()))
            .build()
        val result = billing.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            pendingResult = null
            onResult(PurchaseResult.ERROR)
        }
    }

    /** 복원 — 스토어에 명시적으로 재조회 */
    fun restore(onResult: (Boolean) -> Unit) {
        refreshEntitlements(onResult)
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        main.post {
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val purchased = purchases.orEmpty().any {
                        PremiumConfig.PRODUCT_ID in it.products &&
                            it.purchaseState == Purchase.PurchaseState.PURCHASED
                    }
                    purchases.orEmpty()
                        .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
                        .forEach { acknowledge(it) }
                    if (purchased) {
                        grant()
                        pendingResult?.invoke(PurchaseResult.OK)
                    } else if (purchases.orEmpty().any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
                        pendingResult?.invoke(PurchaseResult.PENDING)
                    } else {
                        pendingResult?.invoke(PurchaseResult.ERROR)
                    }
                }
                BillingClient.BillingResponseCode.USER_CANCELED ->
                    pendingResult?.invoke(PurchaseResult.CANCELLED)
                else -> {
                    Log.w("Premium", "구매 실패: ${result.debugMessage}")
                    pendingResult?.invoke(PurchaseResult.ERROR)
                }
            }
            pendingResult = null
        }
    }

    private fun acknowledge(purchase: Purchase) {
        billing.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        ) { r ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w("Premium", "acknowledge 실패: ${r.debugMessage}")
            }
        }
    }

    // MARK: 상태/게이트

    private fun grant(source: String = "play_entitlement") {
        storeOwned = true
        storeSource = source
        persistence.putString(Persistence.PREMIUM_KEY, "1")
        updatePremium()
    }
    private fun revoke() {
        if (devForcePremium) return
        storeOwned = false
        persistence.remove(Persistence.PREMIUM_KEY)
        updatePremium()
    }

    /**
     * 서버 구매 원장 기준 프리미엄을 확인한다 — 운영자가 DB 에서 부여·회수하면 여기서 반영된다.
     * 앱 시작·앱으로 돌아올 때·계정이 바뀔 때 부른다(메인 스레드).
     * 실패(오프라인·서버 장애·토큰 없음)면 마지막 값을 그대로 둔다 — 로컬 우선, 네트워크 없이도 동작해야 한다.
     */
    suspend fun refreshServerEntitlement(auth: AuthStore) {
        if (devForcePremium) return
        val userId = auth.userId
        val token = auth.accessToken() ?: return
        try {
            val status = LearningApi.premiumStatus(token)
            // 응답을 기다리는 사이 계정이 바뀌었으면(삭제·로그아웃 후 새 익명 계정) 예전 계정 결과는 버린다
            if (auth.userId != userId) return
            applyServerGrant(status.premium)
            Log.i(TAG, "서버 확인 premium=${status.premium} -> isPremium=$isPremium source=$premiumSource")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "서버 프리미엄 확인 실패(마지막 값 유지): ${e.message}")
        }
    }

    /**
     * 계정이 바뀔 때(로그인·로그아웃·계정 삭제) 예전 계정의 서버 부여분을 버린다.
     * 스토어 구매는 Google 계정 기준이라 그대로 둔다. 새 계정 값은 refreshServerEntitlement 가 다시 받는다.
     */
    fun clearServerGrant() {
        applyServerGrant(false)
    }

    private fun applyServerGrant(granted: Boolean) {
        serverGranted = granted
        if (granted) persistence.putString(Persistence.SERVER_PREMIUM_KEY, "1")
        else persistence.remove(Persistence.SERVER_PREMIUM_KEY)
        updatePremium()
    }

    /** 두 근거(스토어 보유 · 서버 부여) 중 하나라도 있으면 프리미엄 */
    private fun updatePremium() {
        isPremium = devForcePremium || storeOwned || serverGranted
        premiumSource = if (storeOwned) storeSource else if (serverGranted) "server" else "none"
    }

    fun openPaywall() { paywallOpen = true }
    fun closePaywall() { paywallOpen = false }

    fun isPremiumMode(mode: GameMode): Boolean = PremiumConfig.isPremiumMode(mode)

    /** 프리미엄 모드 접근 게이트 — 미보유 시 페이월을 열고 false 반환 */
    fun gate(mode: GameMode): Boolean {
        if (!PremiumConfig.isPremiumMode(mode) || isPremium) return true
        openPaywall()
        return false
    }
}
