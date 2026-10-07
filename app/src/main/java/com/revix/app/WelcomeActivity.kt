package com.revix.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.revix.app.garage.VehicleSelectionActivity
import com.revix.app.main.location.LocationPermissionHelper
import com.revix.app.settings.LanguageManager
import com.google.android.material.button.MaterialButton

class WelcomeActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1002
    private var backPressedTime: Long = 0
    private lateinit var backToast: Toast

    private val onboardingTutorialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK || OnboardingTutorialActivity.isCompleted(this)) {
            continuePermissionFlow()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        // Keep full-bleed background/logo placement; only lift START above nav bar.
        val btnStart = findViewById<MaterialButton>(R.id.btnStart)
        val initialBottomMargin =
            (btnStart.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(btnStart) { view, insets ->
            val bottomInset = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
            ).bottom
            val lp = view.layoutParams as android.view.ViewGroup.MarginLayoutParams
            lp.bottomMargin = initialBottomMargin + bottomInset
            view.layoutParams = lp
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(btnStart)

        btnStart.setOnClickListener {
            if (!OnboardingTutorialActivity.isCompleted(this)) {
                onboardingTutorialLauncher.launch(Intent(this, OnboardingTutorialActivity::class.java))
            } else {
                continuePermissionFlow()
            }
        }
    }

    private fun continuePermissionFlow() {
        when {
            !LocationPermissionHelper.hasAll(this) -> requestLocationPermissions()
            needsNotificationPermission() -> requestNotificationPermission()
            else -> {
                markCorePermissionsFlowDone()
                checkBatteryOptimization()
            }
        }
    }

    private fun navigateToVehicleSelection() {
        val intent = Intent(this, VehicleSelectionActivity::class.java).apply {
            putExtra("IS_FIRST_LAUNCH", true)
        }
        startActivity(intent)
        finish()
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            LocationPermissionHelper.requiredPermissions(),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun needsNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return false
        // Ask once during onboarding; contextual screens can ask again later.
        return !PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(PREF_NOTIFICATION_PERMISSION_ASKED, false)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            markNotificationPermissionAsked()
            markCorePermissionsFlowDone()
            checkBatteryOptimization()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(R.string.welcome_notification_permission_title)
            .setMessage(R.string.welcome_notification_permission_hint)
            .setPositiveButton(R.string.welcome_permission_allow) { _, _ ->
                markNotificationPermissionAsked()
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
            .setNegativeButton(R.string.welcome_permission_not_now) { _, _ ->
                markNotificationPermissionAsked()
                markCorePermissionsFlowDone()
                checkBatteryOptimization()
            }
            .setCancelable(false)
            .show()
            .also { DialogHelper.styleDialogButtons(it) }
    }

    private fun markNotificationPermissionAsked() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean(PREF_NOTIFICATION_PERMISSION_ASKED, true)
            .apply()
    }

    private fun markCorePermissionsFlowDone() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean(PREF_ONBOARDING_CORE_PERMISSIONS_DONE, true)
            .apply()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (LocationPermissionHelper.hasAll(this)) {
                    continuePermissionFlow()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.error_permissions_required),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                // Notifications are optional for core use — continue either way.
                markCorePermissionsFlowDone()
                checkBatteryOptimization()
            }
        }
    }

    private fun checkBatteryOptimization() {
        if (BatteryOptimizationHelper.shouldShowOptimizationSetup(this)) {
            val intent = Intent(this, OptimizationSetupActivity::class.java).apply {
                putExtra("IS_FIRST_LAUNCH", true)
            }
            startActivity(intent)
            finish()
        } else {
            navigateToVehicleSelection()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            backToast.cancel()
            super.onBackPressed()
            finish()
        } else {
            backToast = Toast.makeText(
                baseContext,
                getString(R.string.back_press_exit),
                Toast.LENGTH_SHORT
            )
            backToast.show()
        }
        backPressedTime = System.currentTimeMillis()
    }

    companion object {
        const val PREF_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
        const val PREF_ONBOARDING_CORE_PERMISSIONS_DONE = "onboarding_core_permissions_done"

        fun needsOnboardingPermissionFlow(context: Context): Boolean {
            if (!LocationPermissionHelper.hasAll(context)) return true
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) return false
            return !PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_NOTIFICATION_PERMISSION_ASKED, false)
        }
    }
}
