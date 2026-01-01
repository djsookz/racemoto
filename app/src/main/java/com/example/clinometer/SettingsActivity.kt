package com.example.clinometer

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.DialogHelper
import com.example.clinometer.settings.SoundManager
import com.example.clinometer.settings.UnitsManager
import com.example.clinometer.settings.MapProviderManager
import com.google.android.material.card.MaterialCardView

class SettingsActivity : BaseActivity() {
    override fun getLayoutResourceId(): Int = R.layout.activity_settings
    override fun getNavigationItemId(): Int = R.id.navOptions

    private lateinit var prefs: SharedPreferences
    private lateinit var soundManager: SoundManager
    
    // UI elements
    private lateinit var switchAlwaysOn: SwitchCompat
    private lateinit var switchSound100: SwitchCompat
    private lateinit var switchSound200: SwitchCompat
    private lateinit var switchSound402: SwitchCompat
    private lateinit var switchVoiceCountdown: SwitchCompat
    private lateinit var switchSoundLapComplete: SwitchCompat
    private lateinit var switchSoundPersonalBest: SwitchCompat
    
    private lateinit var cardLanguage: MaterialCardView
    private lateinit var cardSpeedUnit: MaterialCardView
    private lateinit var cardDistanceUnit: MaterialCardView
    private lateinit var cardTemperatureUnit: MaterialCardView
    private lateinit var cardDragCalibration: MaterialCardView
    private lateinit var cardTrackEditor: MaterialCardView
    private lateinit var cardBatteryOptimization: MaterialCardView
    private lateinit var cardMapProvider: MaterialCardView
    private lateinit var cardTestMapbox: MaterialCardView
    
    private lateinit var tvLanguageValue: TextView
    private lateinit var tvSpeedUnitValue: TextView
    private lateinit var tvDistanceUnitValue: TextView
    private lateinit var tvTemperatureUnitValue: TextView
    private lateinit var tvDragCalibrationStatus: TextView
    private lateinit var tvBatteryOptimizationStatus: TextView
    private lateinit var tvMapProviderValue: TextView
    
    private var backPressedTime: Long = 0
    private lateinit var backToast: Toast

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        soundManager = SoundManager(this)
        
        initializeViews()
        setupListeners()
        updateUI()
    }
    
    private fun initializeViews() {
        // Switches
        switchAlwaysOn = findViewById(R.id.switchAlwaysOn)
        switchSound100 = findViewById(R.id.switchSound100)
        switchSound200 = findViewById(R.id.switchSound200)
        switchSound402 = findViewById(R.id.switchSound402)
        switchVoiceCountdown = findViewById(R.id.switchVoiceCountdown)
        switchSoundLapComplete = findViewById(R.id.switchSoundLapComplete)
        switchSoundPersonalBest = findViewById(R.id.switchSoundPersonalBest)
        
        // Cards
        cardLanguage = findViewById(R.id.cardLanguage)
        cardSpeedUnit = findViewById(R.id.cardSpeedUnit)
        cardDistanceUnit = findViewById(R.id.cardDistanceUnit)
        cardTemperatureUnit = findViewById(R.id.cardTemperatureUnit)
        cardDragCalibration = findViewById(R.id.cardDragCalibration)
        cardTrackEditor = findViewById(R.id.cardTrackEditor)
        cardBatteryOptimization = findViewById(R.id.cardBatteryOptimization)
        cardMapProvider = findViewById(R.id.cardMapProvider)
        cardTestMapbox = findViewById(R.id.cardTestMapbox)
        
        // Text views
        tvLanguageValue = findViewById(R.id.tvLanguageValue)
        tvSpeedUnitValue = findViewById(R.id.tvSpeedUnitValue)
        tvDistanceUnitValue = findViewById(R.id.tvDistanceUnitValue)
        tvTemperatureUnitValue = findViewById(R.id.tvTemperatureUnitValue)
        tvDragCalibrationStatus = findViewById(R.id.tvDragCalibrationStatus)
        tvBatteryOptimizationStatus = findViewById(R.id.tvBatteryOptimizationStatus)
        tvMapProviderValue = findViewById(R.id.tvMapProviderValue)
    }
    
    private fun setupListeners() {
        // Always On Display
        switchAlwaysOn.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("always_on_display", isChecked).apply()
        }
        
        // Sound switches
        switchSound100.setOnCheckedChangeListener { _, isChecked ->
            soundManager.set100SoundEnabled(isChecked)
        }
        
        switchSound200.setOnCheckedChangeListener { _, isChecked ->
            soundManager.set200SoundEnabled(isChecked)
        }
        
        switchSound402.setOnCheckedChangeListener { _, isChecked ->
            soundManager.set402SoundEnabled(isChecked)
        }
        
        switchVoiceCountdown.setOnCheckedChangeListener { _, isChecked ->
            soundManager.setVoiceCountdownEnabled(isChecked)
        }
        
        // Track sound switches
        switchSoundLapComplete.setOnCheckedChangeListener { _, isChecked ->
            soundManager.setLapCompleteEnabled(isChecked)
        }
        
        switchSoundPersonalBest.setOnCheckedChangeListener { _, isChecked ->
            soundManager.setPersonalBestEnabled(isChecked)
        }
        
        // Battery Optimization
        cardBatteryOptimization.setOnClickListener {
            // Отваряме същата страница която се показва при първото влизане
            // Маркираме че е от настройки за да не продължава към Vehicle Selection
            val intent = Intent(this, OptimizationSetupActivity::class.java).apply {
                putExtra("FROM_SETTINGS", true)
            }
            startActivity(intent)
        }
        
        // Drag calibration
        cardDragCalibration.setOnClickListener {
            val profileId = ProfileStorage.getSelectedProfileId(this)
            val intent = Intent(this, DragCalibrationActivity::class.java).apply {
                putExtra("PROFILE_ID", profileId)
            }
            startActivity(intent)
        }
        
        // Language selection
        cardLanguage.setOnClickListener { showLanguageDialog() }
        
        // Track Editor - Direct to centerline editor
        cardTrackEditor.setOnClickListener {
            showOfficialTrackSelection()
        }
        
        // Map Provider selection
        cardMapProvider.setOnClickListener { showMapProviderDialog() }
        
        // Test Mapbox
        cardTestMapbox.setOnClickListener {
            val intent = Intent(this, MapboxTestActivity::class.java)
            startActivity(intent)
        }
        
        // Unit selections
        cardSpeedUnit.setOnClickListener { showSpeedUnitDialog() }
        // Премахваме Distance настройката - тя се синхронизира автоматично със скоростта
        // cardDistanceUnit.setOnClickListener { showDistanceUnitDialog() }
        cardTemperatureUnit.setOnClickListener { showTemperatureUnitDialog() }
    }
    
    private fun updateUI() {
        // Load current values
        switchAlwaysOn.isChecked = prefs.getBoolean("always_on_display", false)
        switchSound100.isChecked = soundManager.is100SoundEnabled()
        switchSound200.isChecked = soundManager.is200SoundEnabled()
        switchSound402.isChecked = soundManager.is402SoundEnabled()
        switchVoiceCountdown.isChecked = soundManager.isVoiceCountdownEnabled()
        switchSoundLapComplete.isChecked = soundManager.isLapCompleteEnabled()
        switchSoundPersonalBest.isChecked = soundManager.isPersonalBestEnabled()
        
        // Update unit displays
        tvLanguageValue.text = LanguageManager.getLanguage(this).displayName
        tvSpeedUnitValue.text = getString(UnitsManager.getSpeedUnit(this).displayNameResId)
        tvDistanceUnitValue.text = getString(UnitsManager.getDistanceUnit(this).displayNameResId)
        tvTemperatureUnitValue.text = getString(UnitsManager.getTemperatureUnit(this).displayNameResId)
        
        // Update map provider display
        val currentProvider = MapProviderManager.getMapProvider(this)
        tvMapProviderValue.text = MapProviderManager.getProviderDisplayName(currentProvider)
        
        // Update drag calibration status
        updateDragCalibrationStatus()
        
        // Update battery optimization status
        updateBatteryOptimizationStatus()
    }
    
    private fun updateBatteryOptimizationStatus() {
        val isOptimized = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)
        if (isOptimized) {
            tvBatteryOptimizationStatus.text = "✅ Optimized - GPS tracking will work correctly"
            tvBatteryOptimizationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
            tvBatteryOptimizationStatus.text = "⚠️ Not optimized - tap to configure for better GPS accuracy"
            tvBatteryOptimizationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        }
    }
    
    private fun updateDragCalibrationStatus() {
        val profileId = ProfileStorage.getSelectedProfileId(this)
        DragCalibration.setProfile(profileId)
        
        val profiles = ProfileStorage.loadProfiles(this)
        val profileName = profiles.find { it.id == profileId }?.name ?: "Unknown"
        
        val portraitStatus = if (DragCalibration.isPortraitCalibrated) "📱✅" else "📱⚠️"
        val landscapeStatus = if (DragCalibration.isLandscapeCalibrated) "🔄✅" else "🔄⚠️"
        
        val statusText = "$portraitStatus $landscapeStatus $profileName"
        
        if (DragCalibration.hasAnyCalibration()) {
            val dateFormat = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())
            val latestTime = maxOf(DragCalibration.portraitCalibrationTime, DragCalibration.landscapeCalibrationTime)
            tvDragCalibrationStatus.text = "$statusText - ${dateFormat.format(latestTime)}"
        } else {
            tvDragCalibrationStatus.text = "$statusText - ${getString(R.string.calibration_not_calibrated)}"
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateDragCalibrationStatus()  // Обновяваме статуса когато се връщаме от DragCalibrationActivity
        updateBatteryOptimizationStatus()  // Обновяваме статуса когато се връщаме от Battery Optimization Settings
    }
    
    // === DIALOG METHODS ===
    
    private fun showLanguageDialog() {
        val languages = LanguageManager.Language.values()
        val languageNames = languages.map { it.displayName }.toTypedArray()
        val currentLanguage = LanguageManager.getLanguage(this)
        val selectedIndex = languages.indexOf(currentLanguage)
        
        // Заглавие и бутон според текущия език
        val title = when (currentLanguage) {
            LanguageManager.Language.ENGLISH -> "Select Language"
            LanguageManager.Language.BULGARIAN -> "Изберете език"
            LanguageManager.Language.GREEK -> "Επιλέξτε γλώσσα"
        }
        
        val cancelButton = when (currentLanguage) {
            LanguageManager.Language.ENGLISH -> "Cancel"
            LanguageManager.Language.BULGARIAN -> "Отказ"
            LanguageManager.Language.GREEK -> "Ακύρωση"
        }
        
        // Create custom adapter with white text
        val langAdapter = object : android.widget.ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_single_choice,
            languageNames.toList()
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<android.widget.TextView>(android.R.id.text1)
                textView?.setTextColor(android.graphics.Color.WHITE)
                return view
            }
        }
        
        val langDialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(title)
            .setSingleChoiceItems(langAdapter, selectedIndex) { dialog, which ->
                val selectedLanguage = languages[which]
                LanguageManager.setLanguage(this, selectedLanguage)
                tvLanguageValue.text = selectedLanguage.displayName
                dialog.dismiss()
                
                // Recreate activity to apply language immediately
                recreate()
            }
            .setNegativeButton(cancelButton, null)
            .create()
        
        langDialog.show()
        DialogHelper.styleDialogButtons(langDialog)
    }
    
    private fun showSpeedUnitDialog() {
        val units = UnitsManager.SpeedUnit.values()
        val unitNames = units.map { getString(it.displayNameResId) }.toTypedArray()
        val currentUnit = UnitsManager.getSpeedUnit(this)
        val selectedIndex = units.indexOf(currentUnit)
        
        // Create custom adapter with white text
        val speedAdapter = object : android.widget.ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_single_choice,
            unitNames.toList()
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<android.widget.TextView>(android.R.id.text1)
                textView?.setTextColor(android.graphics.Color.WHITE)
                return view
            }
        }
        
        val speedDialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Единица за скорост")
            .setSingleChoiceItems(speedAdapter, selectedIndex) { dialog, which ->
                val selectedUnit = units[which]
                UnitsManager.setSpeedUnit(this, selectedUnit)
                
                // Автоматично синхронизираме разстоянието
                when (selectedUnit) {
                    UnitsManager.SpeedUnit.KMH -> UnitsManager.setDistanceUnit(this, UnitsManager.DistanceUnit.KILOMETERS)
                    UnitsManager.SpeedUnit.MPH -> UnitsManager.setDistanceUnit(this, UnitsManager.DistanceUnit.MILES)
                    UnitsManager.SpeedUnit.MS -> UnitsManager.setDistanceUnit(this, UnitsManager.DistanceUnit.METERS)
                }
                
                updateUI()
                dialog.dismiss()
            }
            .setNegativeButton("Отказ", null)
            .create()
        
        speedDialog.show()
        DialogHelper.styleDialogButtons(speedDialog)
    }
    
    private fun showOfficialTrackSelection() {
        val tracks = arrayOf("Серес", "Sofia Ring", "Отмени")
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Избери писта")
            .setItems(tracks) { _, which ->
                when (which) {
                    0 -> {
                        val intent = Intent(this, OfficialTrackCenterlineEditorActivity::class.java).apply {
                            putExtra("track_id", "serres_circuit")
                            putExtra("track_name", getString(R.string.track_name_serres))
                        }
                        startActivity(intent)
                    }
                    1 -> {
                        val intent = Intent(this, OfficialTrackCenterlineEditorActivity::class.java).apply {
                            putExtra("track_id", "sofia_ring")
                            putExtra("track_name", "Sofia Ring")
                        }
                        startActivity(intent)
                    }
                }
            }
            .show()
    }
    
    private fun showDistanceUnitDialog() {
        val units = UnitsManager.DistanceUnit.values()
        val unitNames = units.map { getString(it.displayNameResId) }.toTypedArray()
        val currentUnit = UnitsManager.getDistanceUnit(this)
        val selectedIndex = units.indexOf(currentUnit)
        
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Единица за разстояние")
            .setSingleChoiceItems(unitNames, selectedIndex) { dialog, which ->
                val selectedUnit = units[which]
                UnitsManager.setDistanceUnit(this, selectedUnit)
                tvDistanceUnitValue.text = getString(selectedUnit.displayNameResId)
                dialog.dismiss()
            }
            .setNegativeButton("Отказ", null)
            .show()
    }
    
    private fun showTemperatureUnitDialog() {
        val units = UnitsManager.TemperatureUnit.values()
        val unitNames = units.map { getString(it.displayNameResId) }.toTypedArray()
        val currentUnit = UnitsManager.getTemperatureUnit(this)
        val selectedIndex = units.indexOf(currentUnit)
        
        // Create custom adapter with white text
        val tempAdapter = object : android.widget.ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_single_choice,
            unitNames.toList()
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<android.widget.TextView>(android.R.id.text1)
                textView?.setTextColor(android.graphics.Color.WHITE)
                return view
            }
        }
        
        val tempDialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Единица за температура")
            .setSingleChoiceItems(tempAdapter, selectedIndex) { dialog, which ->
                val selectedUnit = units[which]
                UnitsManager.setTemperatureUnit(this, selectedUnit)
                tvTemperatureUnitValue.text = getString(selectedUnit.displayNameResId)
                dialog.dismiss()
            }
            .setNegativeButton("Отказ", null)
            .create()
        
        tempDialog.show()
        DialogHelper.styleDialogButtons(tempDialog)
    }
    
    private fun showMapProviderDialog() {
        val providers = MapProviderManager.MapProvider.values()
        val providerNames = providers.map { MapProviderManager.getProviderDisplayName(it) }
        val currentProvider = MapProviderManager.getMapProvider(this)
        val selectedIndex = providers.indexOf(currentProvider)
        
        // Create custom adapter with white text
        val adapter = object : android.widget.ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_single_choice,
            providerNames
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<android.widget.TextView>(android.R.id.text1)
                textView?.setTextColor(android.graphics.Color.WHITE)
                return view
            }
        }
        
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Map Provider")
            .setSingleChoiceItems(adapter, selectedIndex) { dialog, which ->
                val selectedProvider = providers[which]
                MapProviderManager.setMapProvider(this, selectedProvider)
                tvMapProviderValue.text = MapProviderManager.getProviderDisplayName(selectedProvider)
                dialog.dismiss()
                
                Toast.makeText(this, "Map provider променен на: ${MapProviderManager.getProviderDisplayName(selectedProvider)}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отказ", null)
            .create()
        
        dialog.show()
        DialogHelper.styleDialogButtons(dialog)
    }
    
    
    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }

    override fun onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            backToast.cancel()
            super.onBackPressed()
            return
        } else {
            backToast = Toast.makeText(baseContext, getString(R.string.back_press_exit), Toast.LENGTH_SHORT)
            backToast.show()
        }
        backPressedTime = System.currentTimeMillis()
    }
}