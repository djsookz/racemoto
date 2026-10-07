package com.revix.app.billing

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.revix.app.BuildConfig
import com.revix.app.R
import com.revix.app.applySystemBarsPaddingToRoot
import com.revix.app.settings.LanguageManager

class ProPaywallActivity : AppCompatActivity() {

    private lateinit var tvMonthlyPrice: TextView
    private lateinit var tvYearlyPrice: TextView
    private lateinit var btnMonthly: View
    private lateinit var btnYearly: View

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pro_paywall)
        applySystemBarsPaddingToRoot()

        if (ProAccess.isPro(this)) {
            setResult(RESULT_OK)
            finish()
            return
        }

        val feature = intent.getStringExtra(EXTRA_FEATURE) ?: FEATURE_GENERAL
        val title = findViewById<TextView>(R.id.tvPaywallTitle)
        val subtitle = findViewById<TextView>(R.id.tvPaywallSubtitle)
        val quota = findViewById<TextView>(R.id.tvPaywallQuota)
        btnMonthly = findViewById(R.id.btnPaywallMonthly)
        btnYearly = findViewById(R.id.btnPaywallYearly)
        tvMonthlyPrice = findViewById(R.id.tvPaywallMonthlyPrice)
        tvYearlyPrice = findViewById(R.id.tvPaywallYearlyPrice)
        val btnClose = findViewById<TextView>(R.id.btnPaywallClose)
        val btnCloseIcon = findViewById<ImageButton>(R.id.btnPaywallCloseIcon)
        val btnPreviewUnlock = findViewById<TextView>(R.id.btnPaywallPreviewUnlock)
        val btnStartTrial = findViewById<TextView>(R.id.btnPaywallStartTrial)

        when (feature) {
            FEATURE_TRACK -> {
                title.setText(R.string.pro_paywall_title_track)
                subtitle.setText(R.string.pro_paywall_body_track)
            }
            FEATURE_DEVICES -> {
                title.setText(R.string.pro_paywall_title_devices)
                subtitle.setText(R.string.pro_paywall_body_devices)
            }
            FEATURE_PROFILE -> {
                title.setText(R.string.pro_paywall_title_profile)
                subtitle.setText(R.string.pro_paywall_body_profile)
            }
            FEATURE_DRAG -> {
                title.setText(R.string.pro_paywall_title_drag)
                subtitle.setText(R.string.pro_paywall_body_drag)
            }
            FEATURE_NAVIGATION -> {
                title.setText(R.string.pro_paywall_title_navigation)
                subtitle.setText(R.string.pro_paywall_body_navigation)
            }
            FEATURE_SETTINGS -> {
                title.setText(R.string.pro_paywall_title_settings)
                subtitle.setText(R.string.pro_paywall_body_settings)
            }
            else -> {
                title.setText(R.string.pro_paywall_title_general)
                subtitle.setText(R.string.pro_paywall_body_general)
            }
        }

        if (BuildConfig.DEBUG && ProAccess.isTrialActive(this)) {
            quota.visibility = View.VISIBLE
            quota.text = getString(R.string.pro_paywall_trial_active, ProAccess.trialDaysRemaining(this))
        } else {
            quota.visibility = View.VISIBLE
            quota.setText(R.string.pro_paywall_trial_available)
        }

        btnMonthly.setOnClickListener { launchPurchase(PlayBillingProducts.MONTHLY) }
        btnYearly.setOnClickListener { launchPurchase(PlayBillingProducts.YEARLY) }

        val close = View.OnClickListener { finish() }
        btnClose.setOnClickListener(close)
        btnCloseIcon.setOnClickListener(close)

        // Free trial is a Play offer on the monthly subscription (account-bound).
        btnStartTrial.visibility = View.VISIBLE
        btnStartTrial.setText(R.string.pro_paywall_start_trial)
        btnStartTrial.setOnClickListener {
            launchPurchase(PlayBillingProducts.MONTHLY)
        }

        if (BuildConfig.DEBUG) {
            btnPreviewUnlock.visibility = View.VISIBLE
            btnPreviewUnlock.setOnClickListener {
                ProAccess.setProPreview(this, true)
                Toast.makeText(this, R.string.pro_preview_unlocked, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        } else {
            btnPreviewUnlock.visibility = View.GONE
        }

        refreshPricesFromPlay()
        PlayBillingManager.refreshEntitlements(this) {
            if (!isFinishing) refreshPricesFromPlay()
        }
    }

    private val entitlementListener: (Boolean) -> Unit = { entitled ->
        if (!isFinishing) {
            refreshPricesFromPlay()
            if (entitled) {
                Toast.makeText(this, R.string.pro_billing_purchase_success, Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        PlayBillingManager.addEntitlementListener(entitlementListener)
    }

    override fun onStop() {
        PlayBillingManager.removeEntitlementListener(entitlementListener)
        super.onStop()
    }

    private fun refreshPricesFromPlay() {
        PlayBillingManager.formattedPrice(PlayBillingProducts.MONTHLY)?.let { price ->
            tvMonthlyPrice.text = price
        }
        PlayBillingManager.formattedPrice(PlayBillingProducts.YEARLY)?.let { price ->
            tvYearlyPrice.text = price
        }
    }

    private fun launchPurchase(productId: String) {
        val launched = PlayBillingManager.launchSubscriptionPurchase(this, productId)
        if (!launched) {
            Toast.makeText(this, R.string.pro_billing_unavailable, Toast.LENGTH_LONG).show()
            PlayBillingManager.refreshEntitlements(this) {
                if (!isFinishing) refreshPricesFromPlay()
            }
        }
    }

    companion object {
        const val EXTRA_FEATURE = "feature"
        const val FEATURE_DRAG = "drag"
        const val FEATURE_TRACK = "track"
        const val FEATURE_DEVICES = "devices"
        const val FEATURE_PROFILE = "profile"
        const val FEATURE_NAVIGATION = "navigation"
        const val FEATURE_SETTINGS = "settings"
        const val FEATURE_GENERAL = "general"

        fun intent(context: Context, feature: String): Intent {
            return Intent(context, ProPaywallActivity::class.java).putExtra(EXTRA_FEATURE, feature)
        }
    }
}
