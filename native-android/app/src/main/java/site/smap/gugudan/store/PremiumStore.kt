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
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import site.smap.gugudan.core.GameMode
import site.smap.gugudan.core.PremiumConfig
import site.smap.gugudan.services.Persistence

// 프리미엄 — Google Play Billing 8 (iOS PremiumStore/StoreKit2 대응).
// 검증: Play 결제는 스토어 서명 기반 — acknowledge + queryPurchases 엔타이틀먼트로 판정 (iOS 온디바이스 검증과 동일 정책)

enum class PurchaseResult { OK, CANCELLED, PENDING, ERROR }

class PremiumStore(
    context: Context,
    private val persistence: Persistence,
    private val devForcePremium: Boolean = false,
) {
    var isPremium: Boolean by mutableStateOf(
        devForcePremium || persistence.getString(Persistence.PREMIUM_KEY) == "1")
        private set
    var paywallOpen: Boolean by mutableStateOf(false)
    var price: String by mutableStateOf(PremiumConfig.FALLBACK_PRICE)
        private set
    var storeReady: Boolean by mutableStateOf(false)
        private set

    private var productDetails: ProductDetails? = null
    private var pendingResult: ((PurchaseResult) -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    private val billing: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    init {
        if (!devForcePremium) connect()
    }

    private fun connect() {
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    refreshEntitlements()
                } else {
                    Log.w("Premium", "Billing 연결 실패: ${result.debugMessage}")
                }
            }
            override fun onBillingServiceDisconnected() {
                // 다음 구매 시도 시 재연결
            }
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PremiumConfig.PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()))
            .build()
        billing.queryProductDetailsAsync(params) { result, productDetailsResult ->
            main.post {
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
                purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
                    .forEach { acknowledge(it) }
                if (owned) grant() else revoke()
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

    private fun grant() {
        isPremium = true
        persistence.putString(Persistence.PREMIUM_KEY, "1")
        if (paywallOpen) paywallOpen = false
    }
    private fun revoke() {
        if (devForcePremium) return
        isPremium = false
        persistence.remove(Persistence.PREMIUM_KEY)
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
