package com.revix.app

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.revix.app.garage.VehicleSelectionActivity
import com.revix.app.settings.LanguageManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class OptimizationSetupActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private lateinit var containerSteps: LinearLayout
    private lateinit var btnContinue: MaterialButton
    private lateinit var btnSkip: MaterialButton
    private var isFromSettings: Boolean = false
    private var isFirstLaunch: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        isFromSettings = intent.getBooleanExtra("FROM_SETTINGS", false)
        isFirstLaunch = intent.getBooleanExtra("IS_FIRST_LAUNCH", false)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_optimization_setup)
        val toolbar = findViewById<Toolbar>(R.id.optimizationToolbar)
        setSupportActionBar(toolbar)
        applySystemBarsPaddingToRoot()

        window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
        supportActionBar?.setBackgroundDrawable(
            ColorDrawable(ContextCompat.getColor(this, R.color.background_primary))
        )
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.background_primary))

        if (isFromSettings) {
            toolbar.visibility = View.VISIBLE
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
            supportActionBar?.title = getString(R.string.settings_battery_optimization)
            toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
            toolbar.navigationIcon?.mutate()?.setTint(ContextCompat.getColor(this, android.R.color.white))
        } else {
            toolbar.visibility = View.GONE
            supportActionBar?.setDisplayHomeAsUpEnabled(false)
        }

        containerSteps = findViewById(R.id.containerSteps)
        btnContinue = findViewById(R.id.btnContinue)
        btnSkip = findViewById(R.id.btnSkip)

        // Ако е от настройки - скриваме Continue и Skip бутоните
        if (isFromSettings) {
            btnContinue.visibility = android.view.View.GONE
            btnSkip.visibility = android.view.View.GONE
        } else {
            btnContinue.setOnClickListener {
                checkAndContinue()
            }

            btnSkip.setOnClickListener {
                continueToNextScreen()
            }
        }

        buildOptimizationSteps()
    }

    override fun onResume() {
        super.onResume()
        // Refresh при връщане от Settings
        buildOptimizationSteps()
    }

    private fun buildOptimizationSteps() {
        containerSteps.removeAllViews()
        
        // Step 1: Battery Optimization
        val batteryOptimizationDone = isBatteryOptimizationDisabled()
        addOptimizationStep(
            stepNumber = 1,
            title = getString(R.string.optimization_battery_title),
            description = getString(R.string.optimization_battery_desc, getString(R.string.app_name)),
            isCompleted = batteryOptimizationDone,
            onOpenSettings = { openAppBatterySettings() }
        )

        // Step 2: Power Saving Mode
        val powerSavingDone = isPowerSavingModeOff()
        addOptimizationStep(
            stepNumber = 2,
            title = getString(R.string.optimization_power_title),
            description = getString(R.string.optimization_power_desc),
            isCompleted = powerSavingDone,
            onOpenSettings = { openPowerSavingSettings() }
        )
        
        // Показваме Skip бутона само ако ПОНЕ ЕДИН от steps не е завършен
        // Но ако е от настройки - винаги е скрит
        if (isFromSettings) {
            btnSkip.visibility = android.view.View.GONE
        } else if (batteryOptimizationDone && powerSavingDone) {
            btnSkip.visibility = android.view.View.GONE
        } else {
            btnSkip.visibility = android.view.View.VISIBLE
        }
    }

    private fun addOptimizationStep(
        stepNumber: Int,
        title: String,
        description: String,
        isCompleted: Boolean,
        onOpenSettings: () -> Unit
    ) {
        val stepView = layoutInflater.inflate(R.layout.item_optimization_step, containerSteps, false)
        
        stepView.findViewById<TextView>(R.id.tvStepNumber).text = "$stepNumber"
        stepView.findViewById<TextView>(R.id.tvStepTitle).text = title
        stepView.findViewById<TextView>(R.id.tvStepDescription).text = description
        
        val statusIcon = stepView.findViewById<TextView>(R.id.tvStatusIcon)
        val btnOpenSettings = stepView.findViewById<Button>(R.id.btnStepOpenSettings)
        val cardView = stepView.findViewById<MaterialCardView>(R.id.cardStep)

        btnOpenSettings.text = getString(R.string.optimization_open_settings)
        
        if (isCompleted) {
            statusIcon.text = "✅"
            statusIcon.setTextColor(ContextCompat.getColor(this, R.color.success_color))
            btnOpenSettings.isEnabled = false
            btnOpenSettings.alpha = 0.5f
            cardView.strokeColor = ContextCompat.getColor(this, R.color.success_color)
        } else {
            statusIcon.text = "⚠️"
            statusIcon.setTextColor(ContextCompat.getColor(this, R.color.primary_color))
            btnOpenSettings.setOnClickListener { onOpenSettings() }
            cardView.strokeColor = ContextCompat.getColor(this, R.color.primary_color)
        }
        
        containerSteps.addView(stepView)
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }

    private fun isPowerSavingModeOff(): Boolean = BatteryOptimizationHelper.isPowerSavingModeOff(this)


    private fun openAppBatterySettings() {
        try {
            // Отваряме списъка с всички приложения за battery optimization
            // Потребителят избира REVIX и стига до настройките
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    private fun openPowerSavingSettings() {
        try {
            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun checkAndContinue() {
        if (isBatteryOptimizationDisabled()) {
            // Всичко е OK (или поне Battery Optimization е изключена)
            continueToNextScreen()
        } else {
            android.widget.Toast.makeText(
                this,
                getString(R.string.optimization_toast_battery_required),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun continueToNextScreen() {
        // Ако е от настройки - просто затваряме activity-то, не продължаваме към Vehicle Selection
        if (isFromSettings) {
            finish()
        } else {
            val intent = Intent(this, VehicleSelectionActivity::class.java).apply {
                putExtra("IS_FIRST_LAUNCH", isFirstLaunch)
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        // Обработваме back бутона от ActionBar
        if (isFromSettings) {
            onBackPressed()
            return true
        }
        return super.onSupportNavigateUp()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Ако е от настройки - позволяваме back
        if (isFromSettings) {
            super.onBackPressed()
        } else if (isFirstLaunch) {
            // При първо влизане - не позволяваме back, не прави нищо
        } else {
            // Не позволяваме back - трябва Continue или Skip
        }
    }
}

