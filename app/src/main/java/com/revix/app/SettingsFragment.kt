package com.revix.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.revix.app.BuildConfig
import com.revix.app.billing.PlayBillingManager
import com.revix.app.billing.ProAccess
import com.revix.app.billing.ProGate
import com.revix.app.data.CalibrationReminderStore
import com.revix.app.settings.LanguageManager
import com.revix.app.DialogHelper
import com.revix.app.data.ProfileStorage
import com.revix.app.racebox.RaceBoxDebugGate
import com.revix.app.racebox.RaceBoxManager
import com.revix.app.settings.DragRolloutSettings
import com.revix.app.settings.SoundManager
import com.revix.app.settings.UnitsManager
import com.revix.app.settings.VoiceAlertsSettings
import com.google.android.material.card.MaterialCardView

/**
 * Fragment за Settings страницата - конвертиран от SettingsActivity с ПЪЛНА функционалност
 */
class SettingsFragment : Fragment() {
    
    private lateinit var prefs: SharedPreferences
    private lateinit var soundManager: SoundManager
    
    private lateinit var switchAlwaysOn: SwitchCompat
    private lateinit var switchVoiceAlerts: SwitchCompat
    private lateinit var switchDrag1ftRollout: SwitchCompat
    private lateinit var switchRaceBox1ftRollout: SwitchCompat
    private lateinit var switchSound100: SwitchCompat
    private lateinit var switchSound200: SwitchCompat
    private lateinit var switchSound402: SwitchCompat
    private lateinit var switchSoundLapComplete: SwitchCompat
    private lateinit var switchSoundPersonalBest: SwitchCompat
    
    private lateinit var cardLanguage: MaterialCardView
    private lateinit var cardSpeedUnit: MaterialCardView
    private lateinit var cardDistanceUnit: MaterialCardView
    private lateinit var cardTemperatureUnit: MaterialCardView
    private lateinit var cardDragCalibration: MaterialCardView
    private lateinit var cardTrackEditor: MaterialCardView
    private lateinit var cardBatteryOptimization: MaterialCardView
    private lateinit var cardRevixPro: MaterialCardView
    private lateinit var cardRateApp: MaterialCardView
    private lateinit var cardShareApp: MaterialCardView
    private lateinit var cardContactSupport: MaterialCardView
    private lateinit var cardPrivacyPolicy: MaterialCardView
    private lateinit var cardTermsOfService: MaterialCardView
    private lateinit var cardOpenSourceLicenses: MaterialCardView
    private lateinit var cardProPreview: MaterialCardView
    private lateinit var cardProResetQuota: MaterialCardView
    private lateinit var cardRaceBoxDebug: MaterialCardView
    private lateinit var llRaceBox1ftRollout: LinearLayout
    
    private lateinit var tvLanguageValue: TextView
    private lateinit var tvSpeedUnitValue: TextView
    private lateinit var tvDistanceUnitValue: TextView
    private lateinit var tvTemperatureUnitValue: TextView
    private lateinit var tvDragCalibrationStatus: TextView
    private lateinit var tvDragCalibrationBadge: TextView
    private lateinit var tvBatteryOptimizationStatus: TextView
    private lateinit var tvAppVersionValue: TextView
    private lateinit var tvRevixProStatus: TextView
    private lateinit var tvProPreviewStatus: TextView
    private lateinit var tvRaceBoxDebugStatus: TextView
    private lateinit var tvRaceBoxBattery: TextView
    private lateinit var ivRaceBoxReady: ImageView

    private var syncingRolloutSwitches = false

    private val raceBoxStatusListener: (RaceBoxManager.Status) -> Unit = { status ->
        bindRaceBoxStatus(status)
        updateRaceBoxRolloutVisibility()
    }

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result.values.all { it }
            if (granted) {
                showRaceBoxDebugDialog()
            } else {
                Toast.makeText(requireContext(), R.string.settings_racebox_permission_needed, Toast.LENGTH_SHORT).show()
            }
        }
    
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
        switchVoiceAlerts = view.findViewById(R.id.switchVoiceAlerts)
        switchDrag1ftRollout = view.findViewById(R.id.switchDrag1ftRollout)
        switchRaceBox1ftRollout = view.findViewById(R.id.switchRaceBox1ftRollout)
        switchSound100 = view.findViewById(R.id.switchSound100)
        switchSound200 = view.findViewById(R.id.switchSound200)
        switchSound402 = view.findViewById(R.id.switchSound402)
        switchSoundLapComplete = view.findViewById(R.id.switchSoundLapComplete)
        switchSoundPersonalBest = view.findViewById(R.id.switchSoundPersonalBest)
        
        cardLanguage = view.findViewById(R.id.cardLanguage)
        cardSpeedUnit = view.findViewById(R.id.cardSpeedUnit)
        cardDistanceUnit = view.findViewById(R.id.cardDistanceUnit)
        cardTemperatureUnit = view.findViewById(R.id.cardTemperatureUnit)
        cardDragCalibration = view.findViewById(R.id.cardDragCalibration)
        cardTrackEditor = view.findViewById(R.id.cardTrackEditor)
        cardBatteryOptimization = view.findViewById(R.id.cardBatteryOptimization)
        cardRevixPro = view.findViewById(R.id.cardRevixPro)
        cardRateApp = view.findViewById(R.id.cardRateApp)
        cardShareApp = view.findViewById(R.id.cardShareApp)
        cardContactSupport = view.findViewById(R.id.cardContactSupport)
        cardPrivacyPolicy = view.findViewById(R.id.cardPrivacyPolicy)
        cardTermsOfService = view.findViewById(R.id.cardTermsOfService)
        cardOpenSourceLicenses = view.findViewById(R.id.cardOpenSourceLicenses)
        cardProPreview = view.findViewById(R.id.cardProPreview)
        cardProResetQuota = view.findViewById(R.id.cardProResetQuota)
        cardRaceBoxDebug = view.findViewById(R.id.cardRaceBoxDebug)
        llRaceBox1ftRollout = view.findViewById(R.id.llRaceBox1ftRollout)
        view.findViewById<LinearLayout>(R.id.llDebugProTools).visibility =
            if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        
        tvLanguageValue = view.findViewById(R.id.tvLanguageValue)
        tvSpeedUnitValue = view.findViewById(R.id.tvSpeedUnitValue)
        tvDistanceUnitValue = view.findViewById(R.id.tvDistanceUnitValue)
        tvTemperatureUnitValue = view.findViewById(R.id.tvTemperatureUnitValue)
        tvDragCalibrationStatus = view.findViewById(R.id.tvDragCalibrationStatus)
        tvDragCalibrationBadge = view.findViewById(R.id.tvDragCalibrationBadge)
        tvBatteryOptimizationStatus = view.findViewById(R.id.tvBatteryOptimizationStatus)
        tvAppVersionValue = view.findViewById(R.id.tvAppVersionValue)
        tvRevixProStatus = view.findViewById(R.id.tvRevixProStatus)
        tvProPreviewStatus = view.findViewById(R.id.tvProPreviewStatus)
        tvRaceBoxDebugStatus = view.findViewById(R.id.tvRaceBoxDebugStatus)
        tvRaceBoxBattery = view.findViewById(R.id.tvRaceBoxBattery)
        ivRaceBoxReady = view.findViewById(R.id.ivRaceBoxReady)
    }
    
    private fun setupListeners() {
        switchAlwaysOn.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("always_on_display", isChecked).apply()
        }

        switchVoiceAlerts.setOnCheckedChangeListener { _, isChecked ->
            VoiceAlertsSettings.setEnabled(requireContext(), isChecked)
        }

        switchDrag1ftRollout.setOnCheckedChangeListener { _, isChecked ->
            if (syncingRolloutSwitches) return@setOnCheckedChangeListener
            DragRolloutSettings.set1ftRolloutEnabled(requireContext(), isChecked)
            syncRolloutSwitchesFromPrefs()
        }

        switchRaceBox1ftRollout.setOnCheckedChangeListener { _, isChecked ->
            if (syncingRolloutSwitches) return@setOnCheckedChangeListener
            DragRolloutSettings.set1ftRolloutEnabled(requireContext(), isChecked)
            syncRolloutSwitchesFromPrefs()
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
        
        cardLanguage.setOnClickListener { showLanguageDialog() }
        
        // Track editor removed - SDK handles map matching
        cardTrackEditor.visibility = View.GONE
        
        cardSpeedUnit.setOnClickListener { showSpeedUnitDialog() }
        cardTemperatureUnit.setOnClickListener { showTemperatureUnitDialog() }

        cardRevixPro.setOnClickListener { onRevixProClicked() }
        cardRateApp.setOnClickListener { openPlayStoreListing() }
        cardShareApp.setOnClickListener { shareApp() }
        cardContactSupport.setOnClickListener { contactSupport() }
        cardPrivacyPolicy.setOnClickListener {
            openExternalUrl(getString(R.string.settings_privacy_policy_url))
        }
        cardTermsOfService.setOnClickListener {
            openExternalUrl(getString(R.string.settings_terms_of_service_url))
        }
        if (BuildConfig.DEBUG) {
            cardProPreview.setOnClickListener {
                val unlocked = !com.revix.app.billing.ProAccess.isPro(requireContext())
                com.revix.app.billing.ProAccess.setProPreview(requireContext(), unlocked)
                updateRevixProStatus()
                updateProPreviewStatus()
                Toast.makeText(
                    requireContext(),
                    if (unlocked) R.string.pro_preview_unlocked else R.string.pro_preview_locked,
                    Toast.LENGTH_SHORT
                ).show()
            }
            cardProResetQuota.setOnClickListener {
                com.revix.app.billing.ProAccess.expireTrialForPreview(requireContext())
                updateRevixProStatus()
                updateProPreviewStatus()
                Toast.makeText(requireContext(), R.string.settings_pro_expire_trial, Toast.LENGTH_SHORT).show()
                if (com.revix.app.billing.ProAccess.needsFreeProfileSelection(requireContext())) {
                    com.revix.app.billing.FreeProfilePickerCoordinator.promptIfNeeded(requireActivity())
                }
            }
            cardProPreview.setOnLongClickListener {
                com.revix.app.billing.ProAccess.resetTrialForPreview(requireContext())
                com.revix.app.billing.ProAccess.setProPreview(requireContext(), false)
                updateRevixProStatus()
                updateProPreviewStatus()
                Toast.makeText(requireContext(), R.string.settings_pro_reset_trial, Toast.LENGTH_SHORT).show()
                true
            }
        }
        cardRaceBoxDebug.setOnClickListener {
            openExternalDeviceConnect()
        }
        RaceBoxManager.ensureInitialized(requireContext())
        RaceBoxManager.addStatusListener(raceBoxStatusListener)
        cardOpenSourceLicenses.setOnClickListener { showOpenSourceLicenses() }
    }

    private fun openExternalDeviceConnect() {
        if (!ProGate.ensureFullAccess(requireContext(), ProAccess.Feature.DEVICES)) {
            if (RaceBoxManager.isConnected) {
                RaceBoxManager.disconnect()
            }
            return
        }
        ensureRaceBoxPermissionsThenOpen()
    }

    private fun ensureRaceBoxPermissionsThenOpen() {
        if (!RaceBoxDebugGate.canUse(requireContext())) {
            ProGate.openPaywall(requireContext(), ProAccess.Feature.DEVICES)
            return
        }
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(needed.toTypedArray())
        } else {
            showRaceBoxDebugDialog()
        }
    }

    private fun showRaceBoxDebugDialog() {
        if (!isAdded) return
        RaceBoxManager.ensureInitialized(requireContext())

        if (RaceBoxManager.isConnected) {
            AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
                .setTitle(R.string.settings_racebox_title)
                .setMessage(RaceBoxManager.currentStatus().message)
                .setPositiveButton(R.string.settings_racebox_disconnect) { dialog, _ ->
                    RaceBoxManager.disconnect()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.dialog_cancel_button, null)
                .show()
            return
        }

        Toast.makeText(requireContext(), R.string.settings_racebox_scan, Toast.LENGTH_SHORT).show()
        val devices = mutableListOf<RaceBoxManager.ScannedDevice>()
        var finished = false

        fun showPicker() {
            if (!isAdded || finished) return
            // Session may have become active while scan was running.
            if (RaceBoxManager.isConnected) {
                finished = true
                RaceBoxManager.stopScan(keepStatusIfIdle = false)
                showRaceBoxDebugDialog()
                return
            }
            finished = true
            RaceBoxManager.stopScan(keepStatusIfIdle = true)
            if (devices.isEmpty()) {
                Toast.makeText(requireContext(), R.string.settings_racebox_none_found, Toast.LENGTH_SHORT).show()
                return
            }
            val labels = devices.map { "${it.name}\n${it.address}" }.toTypedArray()
            AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
                .setTitle(R.string.settings_racebox_title)
                .setItems(labels) { _, which ->
                    if (which in devices.indices) {
                        RaceBoxManager.connect(requireContext(), devices[which].device)
                    }
                }
                .setNegativeButton(R.string.dialog_cancel_button, null)
                .show()
        }

        val scanListener = object : (List<RaceBoxManager.ScannedDevice>) -> Unit {
            override fun invoke(found: List<RaceBoxManager.ScannedDevice>) {
                devices.clear()
                devices.addAll(found)
                if (devices.isNotEmpty()) {
                    RaceBoxManager.removeScanListener(this)
                    showPicker()
                }
            }
        }
        RaceBoxManager.addScanListener(scanListener)
        RaceBoxManager.startScan(requireContext())
        view?.postDelayed({
            RaceBoxManager.removeScanListener(scanListener)
            if (!finished) showPicker()
        }, 8_000L)
    }
    
    private fun updateUI() {
        switchAlwaysOn.isChecked = prefs.getBoolean("always_on_display", true)
        switchVoiceAlerts.isChecked = VoiceAlertsSettings.isEnabled(requireContext())
        syncRolloutSwitchesFromPrefs()
        updateRaceBoxRolloutVisibility()
        switchSound100.isChecked = soundManager.is100SoundEnabled()
        switchSound200.isChecked = soundManager.is200SoundEnabled()
        switchSound402.isChecked = soundManager.is402SoundEnabled()
        switchSoundLapComplete.isChecked = soundManager.isLapCompleteEnabled()
        switchSoundPersonalBest.isChecked = soundManager.isPersonalBestEnabled()
        
        tvLanguageValue.text = LanguageManager.getLanguage(requireContext()).displayName
        tvSpeedUnitValue.text = getString(UnitsManager.getSpeedUnit(requireContext()).displayNameResId)
        tvDistanceUnitValue.text = getString(UnitsManager.getDistanceUnit(requireContext()).displayNameResId)
        tvTemperatureUnitValue.text = getString(UnitsManager.getTemperatureUnit(requireContext()).displayNameResId)
        tvAppVersionValue.text = resolveAppVersionLabel()
        updateRevixProStatus()
        if (BuildConfig.DEBUG) {
            updateProPreviewStatus()
        }
        if (::tvRaceBoxDebugStatus.isInitialized) {
            bindRaceBoxStatus(RaceBoxManager.currentStatus())
        }
        
        updateDragCalibrationStatus()
        updateBatteryOptimizationStatus()
    }

    private fun bindRaceBoxStatus(status: RaceBoxManager.Status) {
        if (!::tvRaceBoxDebugStatus.isInitialized) return

        val connected = status.state == RaceBoxManager.ConnectionState.CONNECTED
        val ready = connected && status.fixOk

        tvRaceBoxDebugStatus.text = when {
            !connected && status.state == RaceBoxManager.ConnectionState.SCANNING ->
                getString(R.string.settings_racebox_scanning)
            !connected && status.state == RaceBoxManager.ConnectionState.CONNECTING ->
                getString(R.string.settings_racebox_connecting)
            !connected ->
                status.message.takeIf { it.isNotBlank() && it != "Disconnected" }
                    ?: getString(R.string.settings_racebox_debug_idle)
            ready ->
                getString(
                    R.string.settings_racebox_ready_status,
                    status.deviceName ?: "RaceBox",
                    status.satellites,
                    status.updateHz
                )
            else ->
                getString(
                    R.string.settings_racebox_waiting_sats,
                    status.deviceName ?: "RaceBox",
                    status.satellites
                )
        }

        if (::ivRaceBoxReady.isInitialized) {
            ivRaceBoxReady.visibility = if (ready) View.VISIBLE else View.GONE
        }

        if (::tvRaceBoxBattery.isInitialized) {
            val batteryText = formatRaceBoxBattery(status)
            if (connected && batteryText != null) {
                tvRaceBoxBattery.visibility = View.VISIBLE
                tvRaceBoxBattery.text = batteryText
            } else {
                tvRaceBoxBattery.visibility = View.GONE
            }
        }
    }

    private fun formatRaceBoxBattery(status: RaceBoxManager.Status): String? {
        val percent = status.batteryPercent
        if (percent != null) {
            return if (status.batteryCharging) {
                getString(R.string.settings_racebox_battery_charging, percent)
            } else {
                getString(R.string.settings_racebox_battery_percent, percent)
            }
        }
        val deci = status.batteryVoltageDeci ?: return null
        return getString(R.string.settings_racebox_battery_voltage, deci / 10.0)
    }

    private fun syncRolloutSwitchesFromPrefs() {
        if (!::switchDrag1ftRollout.isInitialized) return
        val enabled = DragRolloutSettings.is1ftRolloutEnabled(requireContext())
        syncingRolloutSwitches = true
        switchDrag1ftRollout.isChecked = enabled
        if (::switchRaceBox1ftRollout.isInitialized) {
            switchRaceBox1ftRollout.isChecked = enabled
        }
        syncingRolloutSwitches = false
    }

    private fun updateRaceBoxRolloutVisibility() {
        if (!::llRaceBox1ftRollout.isInitialized) return
        val show = RaceBoxManager.isConnected
        llRaceBox1ftRollout.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            syncRolloutSwitchesFromPrefs()
        }
    }

    private fun updateProPreviewStatus() {
        if (!::tvProPreviewStatus.isInitialized) return
        tvProPreviewStatus.setText(
            if (com.revix.app.billing.ProAccess.isPro(requireContext())) {
                R.string.settings_pro_preview_on
            } else {
                R.string.settings_pro_preview_off
            }
        )
    }

    private fun resolveAppVersionLabel(): String {
        return try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    private fun openPlayStoreListing() {
        val packageName = requireContext().packageName
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        )
        try {
            startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            openExternalIntent(webIntent)
        }
    }

    private fun shareApp() {
        val packageName = requireContext().packageName
        val shareText = getString(R.string.settings_share_app_text, packageName)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.settings_share_app)))
    }

    private fun contactSupport() {
        val email = getString(R.string.settings_support_email)
        val subject = getString(R.string.settings_support_email_subject)
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        openExternalIntent(intent)
    }

    private fun openExternalUrl(url: String) {
        openExternalIntent(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun openExternalIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.settings_link_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showOpenSourceLicenses() {
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle(R.string.settings_open_source_licenses_title)
            .setMessage(R.string.settings_open_source_licenses_body)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.show()
        DialogHelper.styleDialogButtons(dialog)
    }

    private fun updateDragCalibrationStatus() {
        val profileId = ProfileStorage.getSelectedProfileId(requireContext())
        val needsReminder = CalibrationReminderStore.needsDragCalibrationReminder(requireContext(), profileId)
        val isCalibrated = DragCalibration.isProfileCalibrated(requireContext(), profileId)

        when {
            profileId == -1L -> {
                tvDragCalibrationStatus.text = getString(R.string.settings_drag_calibration_not_calibrated)
                tvDragCalibrationStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                tvDragCalibrationBadge.visibility = View.GONE
            }
            isCalibrated -> {
                tvDragCalibrationStatus.text = getString(R.string.settings_drag_calibration_done)
                tvDragCalibrationStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
                tvDragCalibrationBadge.visibility = View.GONE
            }
            needsReminder -> {
                tvDragCalibrationStatus.text = getString(R.string.settings_drag_calibration_required)
                tvDragCalibrationStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light))
                tvDragCalibrationBadge.visibility = View.VISIBLE
            }
            else -> {
                tvDragCalibrationStatus.text = getString(R.string.settings_drag_calibration_not_calibrated)
                tvDragCalibrationStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                tvDragCalibrationBadge.visibility = View.GONE
            }
        }
    }
    
    private fun updateBatteryOptimizationStatus() {
        val isOptimized = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(requireContext())
        if (isOptimized) {
            tvBatteryOptimizationStatus.text = getString(R.string.settings_battery_optimized_status)
            tvBatteryOptimizationStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_light))
        } else {
            tvBatteryOptimizationStatus.text = getString(R.string.settings_battery_not_optimized_status)
            tvBatteryOptimizationStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light))
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateDragCalibrationStatus()
        updateBatteryOptimizationStatus()
        if (::switchVoiceAlerts.isInitialized) {
            switchVoiceAlerts.isChecked = VoiceAlertsSettings.isEnabled(requireContext())
        }
        if (::tvRevixProStatus.isInitialized) {
            updateRevixProStatus()
            PlayBillingManager.refreshEntitlements(requireContext()) {
                if (isAdded) updateRevixProStatus()
            }
        }
    }

    private fun updateRevixProStatus() {
        if (!::tvRevixProStatus.isInitialized) return
        val context = requireContext()
        tvRevixProStatus.text = when {
            PlayBillingManager.hasCachedProEntitlement(context) ->
                getString(R.string.settings_pro_status_active)
            ProAccess.isPro(context) ->
                getString(R.string.settings_pro_status_preview)
            BuildConfig.DEBUG && ProAccess.isTrialActive(context) ->
                getString(R.string.settings_pro_status_trial, ProAccess.trialDaysRemaining(context))
            else ->
                getString(R.string.settings_pro_status_free)
        }
    }

    private fun onRevixProClicked() {
        val context = requireContext()
        if (PlayBillingManager.hasCachedProEntitlement(context)) {
            openPlaySubscriptions()
            return
        }
        if (!ProAccess.isPro(context)) {
            ProGate.openPaywall(context, ProAccess.Feature.SETTINGS)
        }
    }

    private fun openPlaySubscriptions() {
        val packageName = requireContext().packageName
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/account/subscriptions?package=$packageName")
        )
        openExternalIntent(webIntent)
    }
    
    override fun onDestroyView() {
        RaceBoxManager.removeStatusListener(raceBoxStatusListener)
        super.onDestroyView()
        soundManager.release()
    }
    
    private fun showLanguageDialog() {
        val languages = LanguageManager.Language.values()
        val languageNames = languages.map { it.displayName }.toTypedArray()
        val currentLanguage = LanguageManager.getLanguage(requireContext())
        val selectedIndex = languages.indexOf(currentLanguage)
        
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
            .setTitle(getString(R.string.settings_language_dialog_title))
            .setSingleChoiceItems(langAdapter, selectedIndex) { dialog, which ->
                val selectedLanguage = languages[which]
                LanguageManager.setLanguage(requireContext(), selectedLanguage)
                tvLanguageValue.text = selectedLanguage.displayName
                dialog.dismiss()
                
                requireActivity().recreate()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
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
            .setTitle(getString(R.string.settings_speed_unit_dialog_title))
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
            .setNegativeButton(getString(R.string.cancel_button), null)
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
            .setTitle(getString(R.string.settings_temperature_unit_dialog_title))
            .setSingleChoiceItems(tempAdapter, selectedIndex) { dialog, which ->
                val selectedUnit = units[which]
                UnitsManager.setTemperatureUnit(requireContext(), selectedUnit)
                tvTemperatureUnitValue.text = getString(selectedUnit.displayNameResId)
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .create()
        
        tempDialog.show()
        DialogHelper.styleDialogButtons(tempDialog)
    }
    
}
