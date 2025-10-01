package com.example.clinometer

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceManager
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.settings.SoundManager
import com.example.clinometer.settings.UnitsManager
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
    
    private lateinit var tvLanguageValue: TextView
    private lateinit var tvSpeedUnitValue: TextView
    private lateinit var tvDistanceUnitValue: TextView
    private lateinit var tvTemperatureUnitValue: TextView
    
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
        
        // Text views
        tvLanguageValue = findViewById(R.id.tvLanguageValue)
        tvSpeedUnitValue = findViewById(R.id.tvSpeedUnitValue)
        tvDistanceUnitValue = findViewById(R.id.tvDistanceUnitValue)
        tvTemperatureUnitValue = findViewById(R.id.tvTemperatureUnitValue)
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
            if (isChecked) {
                Toast.makeText(this, "Уверете се, че TTS е инсталиран на устройството", Toast.LENGTH_LONG).show()
            }
        }
        
        // Track sound switches
        switchSoundLapComplete.setOnCheckedChangeListener { _, isChecked ->
            soundManager.setLapCompleteEnabled(isChecked)
        }
        
        switchSoundPersonalBest.setOnCheckedChangeListener { _, isChecked ->
            soundManager.setPersonalBestEnabled(isChecked)
        }
        
        // Language selection
        cardLanguage.setOnClickListener { showLanguageDialog() }
        
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
    }
    
    // === DIALOG METHODS ===
    
    private fun showLanguageDialog() {
        val languages = LanguageManager.Language.values()
        val languageNames = languages.map { it.displayName }.toTypedArray()
        val currentLanguage = LanguageManager.getLanguage(this)
        val selectedIndex = languages.indexOf(currentLanguage)
        
        AlertDialog.Builder(this)
            .setTitle("Изберете език / Select Language / Επιλέξτε γλώσσα")
            .setSingleChoiceItems(languageNames, selectedIndex) { dialog, which ->
                val selectedLanguage = languages[which]
                LanguageManager.setLanguage(this, selectedLanguage)
                tvLanguageValue.text = selectedLanguage.displayName
                dialog.dismiss()
                
                // Recreate activity to apply language immediately
                recreate()
            }
            .setNegativeButton("Отказ / Cancel / Ακύρωση", null)
            .show()
    }
    
    private fun showSpeedUnitDialog() {
        val units = UnitsManager.SpeedUnit.values()
        val unitNames = units.map { getString(it.displayNameResId) }.toTypedArray()
        val currentUnit = UnitsManager.getSpeedUnit(this)
        val selectedIndex = units.indexOf(currentUnit)
        
        AlertDialog.Builder(this)
            .setTitle("Единица за скорост")
            .setSingleChoiceItems(unitNames, selectedIndex) { dialog, which ->
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
            .show()
    }
    
    private fun showDistanceUnitDialog() {
        val units = UnitsManager.DistanceUnit.values()
        val unitNames = units.map { getString(it.displayNameResId) }.toTypedArray()
        val currentUnit = UnitsManager.getDistanceUnit(this)
        val selectedIndex = units.indexOf(currentUnit)
        
        AlertDialog.Builder(this)
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
        
        AlertDialog.Builder(this)
            .setTitle("Единица за температура")
            .setSingleChoiceItems(unitNames, selectedIndex) { dialog, which ->
                val selectedUnit = units[which]
                UnitsManager.setTemperatureUnit(this, selectedUnit)
                tvTemperatureUnitValue.text = getString(selectedUnit.displayNameResId)
                dialog.dismiss()
            }
            .setNegativeButton("Отказ", null)
            .show()
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