package com.revix.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import com.revix.app.main.MainContainerActivity
import com.revix.app.settings.LanguageManager
import com.revix.app.update.AppUpdateGate

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var proceeded = false
    private var splashStartedAt = 0L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStart).visibility =
            android.view.View.GONE

        splashStartedAt = SystemClock.elapsedRealtime()
        runVersionCheck()
    }

    override fun onResume() {
        super.onResume()
        // Re-check after returning from Play Store while still on splash.
        if (!proceeded) {
            runVersionCheck()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun runVersionCheck() {
        AppUpdateGate.check(this) {
            scheduleProceedToMain()
        }
    }

    private fun scheduleProceedToMain() {
        if (proceeded || isFinishing) return
        val elapsed = SystemClock.elapsedRealtime() - splashStartedAt
        val remainingDelayMs = (SPLASH_DELAY_MS - elapsed).coerceAtLeast(0L)
        handler.postDelayed({ proceedToMain() }, remainingDelayMs)
    }

    private fun proceedToMain() {
        if (proceeded || isFinishing) return
        proceeded = true
        val intent = Intent(this, MainContainerActivity::class.java)
        intent.putExtra(MainContainerActivity.EXTRA_NAV_ITEM_ID, R.id.navMap)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    companion object {
        private const val SPLASH_DELAY_MS = 1500L
    }
}
