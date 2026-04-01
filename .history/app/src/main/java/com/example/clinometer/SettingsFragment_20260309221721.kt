package com.example.clinometer

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.DialogHelper
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.settings.SoundManager
import com.example.clinometer.settings.UnitsManager
import com.google.android.material.card.MaterialCardView

/**
 * Fragment за Settings страницата - конвертиран от SettingsActivity с ПЪЛНА функционалност
 */
class SettingsFragment : Fragment() {
    
    private lateinit var prefs: SharedPreferences
    private lateinit var soundManager: SoundManager
    
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
    private lateinit var cardLeanCalibration: MaterialCardView
    private lateinit var cardTrackEditor: MaterialCardView
    private lateinit var cardBatteryOptimization: MaterialCardView
    private lateinit var dividerLeanCalibration: View
    
    private lateinit var tvLanguageValue: TextView
    private lateinit var tvSpeedUnitValue: TextView
    private lateinit var tvDistanceUnitValue: TextView
    private lateinit var tvTemperatureUnitValue: TextView
    private lateinit var tvDragCalibrationStatus: TextView
    private lateinit var tvLeanCalibrationStatus: TextView
    private lateinit var tvBatteryOptimizationStatus: TextView
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_settings, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        soundManager = SoundManager(requireContext())
        
        initializeViews(view)
        setupListeners()
        updateUI()
    }
    
    private fun initializeViews(view: View) {
        switchAlwaysOn = view.findViewById(R.id.switchAlwaysOn)
        switchSound100 = view.findViewById(R.id.switchSound100)
        switchSound200 = view.findViewById(R.id.switchSound200)
        switchSound402 = view.findViewById(R.id.switchSound402)
        switchVoiceCountdown = view.findViewById(R.id.switchVoiceCountdown)
        switchSoundLapComplete = view.findViewById(R.id.switchSoundLapComplete)
        switchSoundPersonalBest = view.findViewById(R.id.switchSoundPersonalBest)
        
        cardLanguage = view.findViewById(R.id.cardLanguage)
        cardSpeedUnit = view.findViewById(R.id.cardSpeedUnit)
        cardDistanceUnit = view.findViewById(R.id.cardDistanceUnit)
        cardTemperatureUnit = view.findViewById(R.id.cardTemperatureUnit)
        cardDragCalibration = view.findViewById(R.id.cardDragCalibration)
        cardLeanCalibration = view.findViewById(R.id.cardLeanCalibration)
        cardTrackEditor = view.findViewById(R.id.cardTrackEditor)
        cardBatteryOptimization = view.findViewById(R.id.cardBatteryOptimization)
        dividerLeanCalibration = view.findViewById(R.id.dividerLeanCalibration)
        
        tvLanguageValue = view.findViewById(R.id.tvLanguageValue)
        tvSpeedUnitValue = view.findViewById(R.id.tvSpeedUnitValue)
        tvDistanceUnitValue = view.findViewById(R.id.tvDistanceUnitValue)
        tvTemperatureUnitValue = view.findViewById(R.id.tvTemperatureUnitValue)
        tvDragCalibrationStatus = view.findViewById(R.id.tvDragCalibrationStatus)
        tvLeanCalibrationStatus = view.findViewById(R.id.tvLeanCalibrationStatus)
        tvBatteryOptimizationStatus = view.findViewById(R.id.tvBatteryOptimizationStatus)
    }
    
    private fun setupListeners() {
        switchAlwaysOn.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("always_on_display", isChecked).apply()
        }
        
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
        
        switchSoundLapComplete.setOnCheckedChangeListener { _, isChecked ->
            soundManager.setLapCompleteEnabled(isChecked)
        }
        
        switchSoundPersonalBest.setOnCheckedChangeListener { _, isChecked ->
            soundManager.setPersonalBestEnabled(isChecked)
        }
        
        cardBatteryOptimization.setOnClickListener {
            val intent = Intent(requireContext(), OptimizationSetupActivity::class.java).apply {
                putExtra("FROM_SETTINGS", true)
            }
            startActivity(intent)
        }
        
        cardDragCalibration.setOnClickListener {
            val profileId = ProfileStorage.getSelectedProfileId(requireContext())
            val intent = Intent(requireContext(), DragCalibrationActivity::class.java).apply {
                putExtra("PROFILE_ID", profileId)
            }
            startActivity(intent)
        }

        cardLeanCalibration.setOnClickListener {
            val profileId = ProfileStorage.getSelectedProfileId(requireContext())
            val intent = Intent(requireContext(), LeanCalibrationActivity::class.java).apply {
                putExtra("PROFILE_ID", profileId)
            }
            startActivity(intent)
        }
        
        cardLanguage.setOnClickListener { showLanguageDialog() }
        
        // Track editor removed - SDK handles map matching
        cardTrackEditor.visibility = View.GONE
        
        cardSpeedUnit.setOnClickListener { showSpeedUnitDialog() }
        cardTemperatureUnit.setOnClickListener { showTemperatureUnitDialog() }
    }
    
    private fun updateUI() {
        switchAlwaysOn.isChecked = prefs.getBoolean("always_on_display", false)
        switchSound100.isChecked = soundManager.is100SoundEnabled()
        switchSound200.isChecked = soundManager.is200SoundEnabled()
        switchSound402.isChecked = soundManager.is402SoundEnabled()
        switchVoiceCountdown.isChecked = soundManager.isVoiceCountdownEnabled()
        switchSoundLapComplete.isChecked = soundManager.isLapCompleteEnabled()
        switchSoundPersonalBest.isChecked = soundManager.isPersonalBestEnabled()
        
        tvLanguageValue.text = LanguageManager.getLanguage(requireContext()).displayName
        tvSpeedUnitValue.text = getString(UnitsManager.getSpeedUnit(requireContext()).displayNameResId)
        tvDistanceUnitValue.text = getString(UnitsManager.getDistanceUnit(requireContext()).displayNameResId)
        tvTemperatureUnitValue.text = getString(UnitsManager.getTemperatureUnit(requireContext()).displayNameResId)
        
        updateDragCalibrationStatus()
        updateLeanCalibrationStatus()
        updateVehicleSpecificCards()
        updateBatteryOptimizationStatus()
    }

    private fun updateVehicleSpecificCards() {
        val selectedProfileId = ProfileStorage.getSelectedProfileId(requireContext())
        val selectedProfile = ProfileStorage.loadProfiles(requireContext()).find { it.id == selectedProfileId }
        val isMotorcycle = selectedProfile?.vehicleType == Profile.VehicleType.MOTORCYCLE
        // Lean Calibration е излишна когато DragCalibration е направена (леан остава автоматично на 0°)
        val hasDragCalibration = DragCalibration.isUniversalCalibrated
        val showLeanCalib = isMotorcycle && !hasDragCalibration
        cardLeanCalibration.visibility = if (showLeanCalib) View.VISIBLE else View.GONE
        dividerLeanCalibration.visibility = if (showLeanCalib) View.VISIBLE else View.GONE
    }
    
    private fun updateBatteryOptimizationStatus() {
        val isOptimized = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(requireContext())
        if (isOptimized) {
            tvBatteryOptimizationStatus.text = "✅ Optimized - GPS tracking will work correctly"
            tvBatteryOptimizationStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
        } else {
            tvBatteryOptimizationStatus.text = "⚠️ Not optimized - tap to configure for better GPS accuracy"
            tvBatteryOptimizationStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light))
        }
    }
    
    private fun updateDragCalibrationStatus() {
        val profileId = ProfileStorage.getSelectedProfileId(requireContext())
        DragCalibration.setProfile(profileId)
        
        val profiles = ProfileStorage.loadProfiles(requireContext())
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

    private fun updateLeanCalibrationStatus() {
        val profileId = ProfileStorage.getSelectedProfileId(requireContext())
        val snapshot = LeanCalibrationStore.loadSnapshot(requireContext(), profileId)

        val portraitStatus = if (snapshot.portraitCalibrated) "P:OK" else "P:--"
        val landscapeStatus = if (snapshot.landscapeCalibrated) "L:OK" else "L:--"
        val statusPrefix = "$portraitStatus  $landscapeStatus"

        if (snapshot.hasAnyCalibration()) {
            val latestTime = maxOf(snapshot.portraitTimestamp, snapshot.landscapeTimestamp)
            val dateFormat = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())
            tvLeanCalibrationStatus.text = "$statusPrefix - ${dateFormat.format(latestTime)}"
            tvLeanCalibrationStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
        } else {
            tvLeanCalibrationStatus.text = "$statusPrefix - ${getString(R.string.calibration_not_calibrated)}"
            tvLeanCalibrationStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light))
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateDragCalibrationStatus()
        updateLeanCalibrationStatus()
        updateVehicleSpecificCards()
        updateBatteryOptimizationStatus()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        soundManager.release()
    }
    
    private fun showLanguageDialog() {
        val languages = LanguageManager.Language.values()
        val languageNames = languages.map { it.displayName }.toTypedArray()
        val currentLanguage = LanguageManager.getLanguage(requireContext())
        val selectedIndex = languages.indexOf(currentLanguage)
        
        val title = when (currentLanguage) {
            LanguageManager.Language.ENGLISH -> "Select Language"
            LanguageManager.Language.BULGARIAN -> "Изберете език"
            LanguageManager.Language.GREEK -> "Εпиλέξτε γλώσσα"
        }
        
        val cancelButton = when (currentLanguage) {
            LanguageManager.Language.ENGLISH -> "Cancel"
            LanguageManager.Language.BULGARIAN -> "Отказ"
            LanguageManager.Language.GREEK -> "Ακύρωση"
        }
        
        val langAdapter = object : android.widget.ArrayAdapter<String>(
            requireContext(),
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
        
        val langDialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle(title)
            .setSingleChoiceItems(langAdapter, selectedIndex) { dialog, which ->
                val selectedLanguage = languages[which]
                LanguageManager.setLanguage(requireContext(), selectedLanguage)
                tvLanguageValue.text = selectedLanguage.displayName
                dialog.dismiss()
                
                requireActivity().recreate()
            }
            .setNegativeButton(cancelButton, null)
            .create()
        
        langDialog.show()
        DialogHelper.styleDialogButtons(langDialog)
    }
    
    private fun showSpeedUnitDialog() {
        val units = UnitsManager.SpeedUnit.values()
        val unitNames = units.map { getString(it.displayNameResId) }.toTypedArray()
        val currentUnit = UnitsManager.getSpeedUnit(requireContext())
        val selectedIndex = units.indexOf(currentUnit)
        
        val speedAdapter = object : android.widget.ArrayAdapter<String>(
            requireContext(),
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
        
        val speedDialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle("Единица за скорост")
            .setSingleChoiceItems(speedAdapter, selectedIndex) { dialog, which ->
                val selectedUnit = units[which]
                UnitsManager.setSpeedUnit(requireContext(), selectedUnit)
                
                when (selectedUnit) {
                    UnitsManager.SpeedUnit.KMH -> UnitsManager.setDistanceUnit(requireContext(), UnitsManager.DistanceUnit.KILOMETERS)
                    UnitsManager.SpeedUnit.MPH -> UnitsManager.setDistanceUnit(requireContext(), UnitsManager.DistanceUnit.MILES)
                    UnitsManager.SpeedUnit.MS -> UnitsManager.setDistanceUnit(requireContext(), UnitsManager.DistanceUnit.METERS)
                }
                
                updateUI()
                dialog.dismiss()
            }
            .setNegativeButton("Отказ", null)
            .create()
        
        speedDialog.show()
        DialogHelper.styleDialogButtons(speedDialog)
    }
    
    
    private fun showTemperatureUnitDialog() {
        val units = UnitsManager.TemperatureUnit.values()
        val unitNames = units.map { getString(it.displayNameResId) }.toTypedArray()
        val currentUnit = UnitsManager.getTemperatureUnit(requireContext())
        val selectedIndex = units.indexOf(currentUnit)
        
        val tempAdapter = object : android.widget.ArrayAdapter<String>(
            requireContext(),
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
        
        val tempDialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle("Единица за температура")
            .setSingleChoiceItems(tempAdapter, selectedIndex) { dialog, which ->
                val selectedUnit = units[which]
                UnitsManager.setTemperatureUnit(requireContext(), selectedUnit)
                tvTemperatureUnitValue.text = getString(selectedUnit.displayNameResId)
                dialog.dismiss()
            }
            .setNegativeButton("Отказ", null)
            .create()
        
        tempDialog.show()
        DialogHelper.styleDialogButtons(tempDialog)
    }
    
}
