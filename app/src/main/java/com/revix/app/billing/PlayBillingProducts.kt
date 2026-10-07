package com.revix.app.billing

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.PurchasesUpdatedListener

/**
 * Play product IDs (must match Play Console subscriptions).
 * BillingClient bootstrap keeps the Billing Library in the release AAB so Console
 * unlocks subscription creation after this build is uploaded.
 */
object PlayBillingProducts {
    const val MONTHLY = "revix_pro_monthly"
    const val YEARLY = "revix_pro_yearly"

    fun createClient(
        context: Context,
        listener: PurchasesUpdatedListener = PurchasesUpdatedListener { _, _ -> }
    ): BillingClient {
        return BillingClient.newBuilder(context.applicationContext)
            .setListener(listener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()
    }
}
