package com.cptodix.rimalux.oled.wallpaper.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.cptodix.rimalux.oled.wallpaper.model.TipCatalogItem
import com.cptodix.rimalux.oled.wallpaper.model.TipProduct
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface TipBillingEvent {
    data class PurchaseCompleted(
        val productIds: List<String>,
        val quantity: Int = 1
    ) : TipBillingEvent

    data class PurchasePending(
        val productIds: List<String>
    ) : TipBillingEvent

    data object UserCanceled : TipBillingEvent

    data class PurchaseError(
        val responseCode: Int,
        val debugMessage: String
    ) : TipBillingEvent
}

sealed interface TipLaunchResult {
    data object BillingFlowLaunched : TipLaunchResult
    data object BillingUnavailable : TipLaunchResult
    data class ProductNotConfigured(val productId: String) : TipLaunchResult
    data class LaunchFailed(val debugMessage: String) : TipLaunchResult
}

interface TipBillingController {
    val catalogFlow: StateFlow<List<TipCatalogItem>>
    val eventsFlow: SharedFlow<TipBillingEvent>

    suspend fun refreshCatalogAndPendingPurchases()
    suspend fun launchTipPurchase(activity: Activity, product: TipProduct): TipLaunchResult
    fun close()
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Google Play Billing Library 8 adapter for Rimalux's consumable one-time tip products.
 *
 * - Queries localized prices (`formattedPrice`) from Google Play for all [TipProduct] IDs.
 * - Supports both multi-purchase-option (`oneTimePurchaseOfferDetailsList`) and single-offer
 *   (`oneTimePurchaseOfferDetails`) Play Console product configurations.
 * - Consumes `PURCHASED` tips immediately via `consumeAsync` (which both acknowledges the
 *   transaction with Google Play and enables repeat tipping).
 * - Respects `PENDING` transactions (never acknowledges/consumes until `PURCHASED`).
 */
class GooglePlayTipBillingManager(
    context: Context
) : TipBillingController, PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionMutex = Mutex()

    private val _catalogFlow = MutableStateFlow(TipCatalogItem.defaultCatalog)
    override val catalogFlow: StateFlow<List<TipCatalogItem>> = _catalogFlow.asStateFlow()

    private val _eventsFlow = MutableSharedFlow<TipBillingEvent>(extraBufferCapacity = 8)
    override val eventsFlow: SharedFlow<TipBillingEvent> = _eventsFlow.asSharedFlow()

    private val productDetailsMap = ConcurrentHashMap<String, ProductDetails>()
    private val inFlightConsumeTokens: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val consumedTokensInSession: MutableSet<String> = Collections.synchronizedSet(HashSet())

    @Volatile
    private var isClosed = false

    private val billingClientDelegate = lazy {
        BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()
    }
    private val billingClient: BillingClient by billingClientDelegate

    override suspend fun refreshCatalogAndPendingPurchases() {
        if (isClosed) return
        withContext(Dispatchers.IO) {
            if (!ensureConnected()) return@withContext
            queryTipProductDetailsInternal()
            processActivePurchasesInternal(emitCompletedEvent = true)
        }
    }

    private sealed interface ProductLookupResult {
        data class Ready(val details: ProductDetails) : ProductLookupResult
        data object BillingUnavailable : ProductLookupResult
        data object ProductMissing : ProductLookupResult
        data class Error(val responseCode: Int) : ProductLookupResult
    }

    override suspend fun launchTipPurchase(
        activity: Activity,
        product: TipProduct
    ): TipLaunchResult {
        if (isClosed) return TipLaunchResult.BillingUnavailable
        val lookup = withContext(Dispatchers.IO) {
            if (!ensureConnected()) {
                return@withContext ProductLookupResult.BillingUnavailable
            }
            val supportResult = billingClient.isFeatureSupported(BillingClient.FeatureType.PRODUCT_DETAILS)
            if (supportResult.responseCode != BillingResponseCode.OK) {
                return@withContext ProductLookupResult.BillingUnavailable
            }
            // Query fresh ProductDetails before launching per Play Billing best practices
            val queryCode = queryTipProductDetailsInternal()
            val resolved = productDetailsMap[product.productId]
            when {
                resolved != null -> ProductLookupResult.Ready(resolved)
                queryCode == BillingResponseCode.OK -> ProductLookupResult.ProductMissing
                queryCode == BillingResponseCode.BILLING_UNAVAILABLE ||
                    queryCode == BillingResponseCode.SERVICE_UNAVAILABLE ||
                    queryCode == BillingResponseCode.SERVICE_DISCONNECTED ||
                    queryCode == BillingResponseCode.NETWORK_ERROR ||
                    queryCode == BillingResponseCode.FEATURE_NOT_SUPPORTED -> ProductLookupResult.BillingUnavailable
                else -> ProductLookupResult.Error(queryCode)
            }
        }

        val details = when (lookup) {
            is ProductLookupResult.Ready -> lookup.details
            ProductLookupResult.BillingUnavailable -> return TipLaunchResult.BillingUnavailable
            ProductLookupResult.ProductMissing -> return TipLaunchResult.ProductNotConfigured(product.productId)
            is ProductLookupResult.Error -> return TipLaunchResult.LaunchFailed("Billing error (${lookup.responseCode})")
        }
        if (!billingClient.isReady) {
            return TipLaunchResult.BillingUnavailable
        }

        val selectedOffer = details.oneTimePurchaseOfferDetailsList?.firstOrNull()
            ?: details.oneTimePurchaseOfferDetails
        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply {
                val token = selectedOffer?.offerToken
                if (!token.isNullOrEmpty()) {
                    setOfferToken(token)
                }
            }
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val launchResult = withContext(Dispatchers.Main.immediate) {
            billingClient.launchBillingFlow(activity, flowParams)
        }

        return when (launchResult.responseCode) {
            BillingResponseCode.OK -> TipLaunchResult.BillingFlowLaunched
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                scope.launch {
                    processActivePurchasesInternal(emitCompletedEvent = false)
                }
                TipLaunchResult.LaunchFailed(launchResult.debugMessage.ifBlank { "Item already owned; refreshing…" })
            }
            BillingResponseCode.BILLING_UNAVAILABLE,
            BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingResponseCode.SERVICE_DISCONNECTED,
            BillingResponseCode.FEATURE_NOT_SUPPORTED -> TipLaunchResult.BillingUnavailable
            else -> TipLaunchResult.LaunchFailed(
                launchResult.debugMessage.ifBlank { "Billing error (${launchResult.responseCode})" }
            )
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingResponseCode.OK -> {
                val nonNullPurchases = purchases.orEmpty()
                if (nonNullPurchases.isNotEmpty()) {
                    scope.launch {
                        handlePurchasesList(nonNullPurchases, emitCompletedEvent = true)
                    }
                }
            }
            BillingResponseCode.USER_CANCELED -> {
                _eventsFlow.tryEmit(TipBillingEvent.UserCanceled)
            }
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                scope.launch {
                    processActivePurchasesInternal(emitCompletedEvent = true)
                }
            }
            else -> {
                _eventsFlow.tryEmit(
                    TipBillingEvent.PurchaseError(
                        responseCode = billingResult.responseCode,
                        debugMessage = billingResult.debugMessage
                    )
                )
            }
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        scope.cancel()
        runCatching {
            if (billingClientDelegate.isInitialized() && billingClient.isReady) {
                billingClient.endConnection()
            }
        }
    }

    private fun isPlayBillingServiceAvailable(): Boolean {
        val pm = appContext.packageManager
        val playStoreIntent = Intent("com.android.vending.billing.InAppBillingService.BIND")
            .setPackage("com.android.vending")
        if (pm.queryIntentServices(playStoreIntent, 0).isNotEmpty()) {
            return true
        }
        val billingLabIntent = Intent(
            "com.google.android.apps.play.billingtestcompanion.BillingOverrideService.BIND"
        )
        return pm.queryIntentServices(billingLabIntent, 0).isNotEmpty()
    }

    private suspend fun ensureConnected(): Boolean {
        if (isClosed) return false
        if (!isPlayBillingServiceAvailable()) return false
        if (billingClient.isReady) return true
        return connectionMutex.withLock {
            if (isClosed) return@withLock false
            if (billingClient.isReady) return@withLock true
            withTimeoutOrNull(6_000L) {
                suspendCancellableCoroutine { cont ->
                    runCatching {
                        billingClient.startConnection(object : BillingClientStateListener {
                            override fun onBillingSetupFinished(billingResult: BillingResult) {
                                if (cont.isActive) {
                                    cont.resume(billingResult.responseCode == BillingResponseCode.OK)
                                }
                            }

                            override fun onBillingServiceDisconnected() {
                                if (cont.isActive) {
                                    cont.resume(false)
                                }
                            }
                        })
                    }.onFailure {
                        if (cont.isActive) {
                            cont.resume(false)
                        }
                    }
                }
            } ?: false
        }
    }

    private suspend fun queryTipProductDetailsInternal(): Int {
        val products = TipProduct.entries.map { tip ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(tip.productId)
                .setProductType(ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        val (responseCode, fetchedDetails) = withTimeoutOrNull(6_000L) {
            suspendCancellableCoroutine { cont ->
                billingClient.queryProductDetailsAsync(params) { billingResult, result ->
                    if (cont.isActive) {
                        if (billingResult.responseCode == BillingResponseCode.OK) {
                            cont.resume(billingResult.responseCode to result.productDetailsList)
                        } else {
                            cont.resume(billingResult.responseCode to emptyList())
                        }
                    }
                }
            }
        } ?: (BillingResponseCode.SERVICE_UNAVAILABLE to emptyList())

        if (fetchedDetails.isNotEmpty()) {
            for (detail in fetchedDetails) {
                productDetailsMap[detail.productId] = detail
            }
            _catalogFlow.value = TipProduct.entries.map { tip ->
                val detail = productDetailsMap[tip.productId]
                val offer = detail?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                    ?: detail?.oneTimePurchaseOfferDetails
                TipCatalogItem(
                    product = tip,
                    formattedPrice = offer?.formattedPrice,
                    isStoreReady = detail != null && offer != null
                )
            }
        }
        return responseCode
    }

    private suspend fun processActivePurchasesInternal(emitCompletedEvent: Boolean) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(ProductType.INAPP)
            .build()

        val activePurchases: List<Purchase> = withTimeoutOrNull(6_000L) {
            suspendCancellableCoroutine { cont ->
                billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
                    if (cont.isActive) {
                        if (billingResult.responseCode == BillingResponseCode.OK) {
                            cont.resume(purchasesList)
                        } else {
                            cont.resume(emptyList())
                        }
                    }
                }
            }
        }.orEmpty()

        if (activePurchases.isNotEmpty()) {
            handlePurchasesList(activePurchases, emitCompletedEvent = emitCompletedEvent)
        }
    }

    private suspend fun handlePurchasesList(
        purchases: List<Purchase>,
        emitCompletedEvent: Boolean
    ) {
        for (purchase in purchases) {
            val matchingTipIds = purchase.products.filter { TipProduct.fromProductId(it) != null }
            if (matchingTipIds.isEmpty()) continue

            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    val token = purchase.purchaseToken
                    if (token in consumedTokensInSession || !inFlightConsumeTokens.add(token)) {
                        continue
                    }
                    try {
                        val consumedOk = consumePurchaseToken(token)
                        if (consumedOk) {
                            consumedTokensInSession.add(token)
                            if (emitCompletedEvent) {
                                _eventsFlow.tryEmit(
                                    TipBillingEvent.PurchaseCompleted(
                                        productIds = matchingTipIds,
                                        quantity = purchase.quantity.coerceAtLeast(1)
                                    )
                                )
                            }
                        }
                    } finally {
                        inFlightConsumeTokens.remove(token)
                    }
                }

                Purchase.PurchaseState.PENDING -> {
                    // Never acknowledge or consume while PENDING per Google Play Billing policy
                    if (emitCompletedEvent) {
                        _eventsFlow.tryEmit(TipBillingEvent.PurchasePending(productIds = matchingTipIds))
                    }
                }

                else -> Unit
            }
        }
    }

    private suspend fun consumePurchaseToken(purchaseToken: String): Boolean {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        return withTimeoutOrNull(6_000L) {
            suspendCancellableCoroutine { cont ->
                billingClient.consumeAsync(params) { billingResult, _ ->
                    if (cont.isActive) {
                        cont.resume(billingResult.responseCode == BillingResponseCode.OK)
                    }
                }
            }
        } ?: false
    }
}
