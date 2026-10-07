package com.revix.app.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play Billing for REVIX Pro subscriptions.
 * Product IDs must match Play Console: [PlayBillingProducts.MONTHLY], [PlayBillingProducts.YEARLY].
 */
object PlayBillingManager {
    private const val TAG = "PlayBillingManager"
    private const val PREFS_NAME = "revix_billing_entitlements"
    private const val PREF_PRO_ENTITLED = "revix_play_pro_entitled"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var billingClient: BillingClient? = null
    private var connecting = false
    private val productDetailsById = mutableMapOf<String, ProductDetails>()
    private val purchaseListeners = mutableSetOf<(Boolean) -> Unit>()
    private val entitlementListeners = mutableSetOf<(Boolean) -> Unit>()

    fun addEntitlementListener(listener: (Boolean) -> Unit) {
        synchronized(entitlementListeners) {
            entitlementListeners.add(listener)
        }
    }

    fun removeEntitlementListener(listener: (Boolean) -> Unit) {
        synchronized(entitlementListeners) {
            entitlementListeners.remove(listener)
        }
    }


    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasCachedProEntitlement(context: Context): Boolean {
        return prefs(context).getBoolean(PREF_PRO_ENTITLED, false)
    }

    fun start(context: Context) {
        ensureClient(context.applicationContext)
        connectAndRefresh(context.applicationContext)
    }

    fun refreshEntitlements(context: Context, onDone: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext
        ensureClient(appContext)
        if (onDone != null) {
            purchaseListeners.add(onDone)
        }
        connectAndRefresh(appContext)
    }

    fun formattedPrice(productId: String): String? {
        val details = productDetailsById[productId] ?: return null
        val offer = selectOffer(details) ?: return null
        val phases = offer.pricingPhases.pricingPhaseList
        // Prefer the paid recurring phase (skip free-trial €0 phase).
        return phases.lastOrNull { it.priceAmountMicros > 0L }?.formattedPrice
            ?: phases.lastOrNull()?.formattedPrice
    }

    fun hasFreeTrialOffer(productId: String): Boolean {
        val details = productDetailsById[productId] ?: return false
        return selectOffer(details)?.let { isFreeTrialOffer(it) } == true
    }

    fun launchSubscriptionPurchase(activity: Activity, productId: String): Boolean {
        val details = productDetailsById[productId]
        if (details == null) {
            Log.w(TAG, "Missing ProductDetails for $productId")
            refreshEntitlements(activity)
            return false
        }
        val offer = selectOffer(details)
        val offerToken = offer?.offerToken
        if (offerToken.isNullOrBlank()) {
            Log.w(TAG, "Missing offerToken for $productId")
            return false
        }
        val client = billingClient
        if (client == null || !client.isReady) {
            connectAndRefresh(activity.applicationContext)
            return false
        }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "launchBillingFlow failed: code=${result.responseCode} ${result.debugMessage}")
        }
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun selectOffer(
        details: ProductDetails
    ): ProductDetails.SubscriptionOfferDetails? {
        val offers = details.subscriptionOfferDetails.orEmpty()
        return offers.firstOrNull { isFreeTrialOffer(it) } ?: offers.firstOrNull()
    }

    private fun isFreeTrialOffer(offer: ProductDetails.SubscriptionOfferDetails): Boolean {
        return offer.pricingPhases.pricingPhaseList.any { phase ->
            phase.priceAmountMicros == 0L
        }
    }

    private fun ensureClient(appContext: Context) {
        if (billingClient != null) return
        billingClient = PlayBillingProducts.createClient(
            appContext,
            PurchasesUpdatedListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    handlePurchases(appContext, purchases.orEmpty())
                } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
                    Log.d(TAG, "Purchase canceled by user")
                } else {
                    Log.w(TAG, "Purchase update failed: ${billingResult.debugMessage}")
                    notifyPurchaseListeners(hasCachedProEntitlement(appContext))
                }
            }
        )
    }

    private fun connectAndRefresh(appContext: Context) {
        val client = billingClient ?: return
        if (client.isReady) {
            queryProductDetails(appContext)
            queryActivePurchases(appContext)
            return
        }
        if (connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                connecting = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProductDetails(appContext)
                    queryActivePurchases(appContext)
                } else {
                    Log.w(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                    notifyPurchaseListeners(hasCachedProEntitlement(appContext))
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                Log.w(TAG, "Billing service disconnected")
            }
        })
    }

    private fun queryProductDetails(appContext: Context) {
        val client = billingClient ?: return
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PlayBillingProducts.MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PlayBillingProducts.YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        client.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryProductDetails failed: ${billingResult.debugMessage}")
                return@queryProductDetailsAsync
            }
            productDetailsById.clear()
            queryProductDetailsResult.productDetailsList.forEach { details ->
                productDetailsById[details.productId] = details
            }
            if (queryProductDetailsResult.unfetchedProductList.isNotEmpty()) {
                Log.w(
                    TAG,
                    "Unfetched products: ${queryProductDetailsResult.unfetchedProductList.map { it.productId }}"
                )
            }
            Log.d(TAG, "Loaded ${productDetailsById.size} subscription products")
        }
    }

    private fun queryActivePurchases(appContext: Context) {
        val client = billingClient ?: return
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "queryPurchases failed: ${billingResult.debugMessage}")
                notifyPurchaseListeners(hasCachedProEntitlement(appContext))
                return@queryPurchasesAsync
            }
            handlePurchases(appContext, purchases)
        }
    }

    private fun handlePurchases(appContext: Context, purchases: List<Purchase>) {
        val entitled = purchases.any { purchase ->
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                purchase.products.any { it == PlayBillingProducts.MONTHLY || it == PlayBillingProducts.YEARLY }
        }
        purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach { acknowledge(it) }

        PreferenceManager.getDefaultSharedPreferences(appContext)
            .edit()
            .remove("revix_play_pro_entitled")
            .apply()
        prefs(appContext)
            .edit()
            .putBoolean(PREF_PRO_ENTITLED, entitled)
            .apply()
        notifyPurchaseListeners(entitled)
    }

    private fun acknowledge(purchase: Purchase) {
        val client = billingClient ?: return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledge failed: ${result.debugMessage}")
            }
        }
    }

    private fun notifyPurchaseListeners(entitled: Boolean) {
        mainHandler.post {
            val entitlementSnapshot = synchronized(entitlementListeners) {
                entitlementListeners.toList()
            }
            entitlementSnapshot.forEach { it(entitled) }
            val listeners = purchaseListeners.toList()
            purchaseListeners.clear()
            listeners.forEach { it(entitled) }
        }
    }
}
