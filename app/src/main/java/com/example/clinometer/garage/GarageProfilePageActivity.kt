package com.example.clinometer.garage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.Insets
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.example.clinometer.DragStorage
import com.example.clinometer.Profile
import com.example.clinometer.Race
import com.example.clinometer.R
import com.example.clinometer.RouteStorage
import com.example.clinometer.DragSession
import com.example.clinometer.data.GarageFuelEntry
import com.example.clinometer.data.GarageFuelEntryStorage
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.data.VehicleData
import com.example.clinometer.settings.LanguageManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GarageProfilePageActivity : AppCompatActivity() {

    private var profileId: Long = -1L
    private var currentProfile: Profile? = null

    private lateinit var btnBack: MaterialButton
    private lateinit var btnEdit: MaterialButton
    private lateinit var btnAddFuelEntry: MaterialButton
    private lateinit var tvVehicleName: TextView
    private lateinit var tvProfileStatus: TextView
    private lateinit var tvVehicleMeta: TextView
    private lateinit var tvSessionsTotal: TextView
    private lateinit var tvSessionsSplit: TextView
    private lateinit var tvFuelLogsCount: TextView
    private lateinit var tvMaintenanceCount: TextView
    private lateinit var tvDocumentsCount: TextView
    private lateinit var tabProfileSections: TabLayout
    private lateinit var svGarageProfilePage: ScrollView
    private lateinit var llGarageProfilePageContent: LinearLayout
    private lateinit var llProfileOverviewSummary: LinearLayout
    private lateinit var llProfileOverviewMetrics: LinearLayout
    private lateinit var llProfileOverviewRecords: LinearLayout
    private lateinit var llProfileFuelLogsSummary: LinearLayout
    private lateinit var llProfileFuelSummary: LinearLayout
    private lateinit var llProfileMaintenanceSummary: LinearLayout
    private lateinit var llProfileDocumentsSummary: LinearLayout
    private lateinit var llProfileOverviewMotoLeanRow: LinearLayout
    private lateinit var tvProfileOverviewDistanceValue: TextView
    private lateinit var tvProfileOverviewDurationValue: TextView
    private lateinit var tvProfileOverviewBest0to100Value: TextView
    private lateinit var tvProfileOverviewBest0to200Value: TextView
    private lateinit var tvProfileOverviewBest100to200Value: TextView
    private lateinit var tvProfileOverviewBest0to402Value: TextView
    private lateinit var tvProfileOverviewMaxSpeedValue: TextView
    private lateinit var tvProfileOverviewMaxLeanLeftValue: TextView
    private lateinit var tvProfileOverviewMaxLeanRightValue: TextView
    private lateinit var tvProfileOverviewGForceLabel: TextView
    private lateinit var tvProfileOverviewGForceValue: TextView
    private lateinit var tvProfileFuelTotalSpentValue: TextView
    private lateinit var tvProfileFuelAvgConsumptionValue: TextView
    private lateinit var tvProfileFuelAvgConsumptionMeta: TextView
    private lateinit var tvProfileFuelLastPriceValue: TextView
    private lateinit var tvProfileFuelTotalLitresValue: TextView
    private lateinit var llProfileFuelEntriesPreview: LinearLayout
    private lateinit var tvTabPlaceholder: TextView
    private var latestWindowInsets: WindowInsetsCompat? = null
    private var selectedTabPosition: Int = 0
    private var contentBaseTopPadding: Int = 0
    private var contentBaseBottomPadding: Int = 0
    private var addFuelButtonBaseBottomMargin: Int = 0
    private var addFuelButtonBaseEndMargin: Int = 0

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_garage_profile_page)

        bindViews()
        applySystemInsets()
        setupProfileTabs()
        setupClickListeners()

        profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        if (profileId == -1L) {
            finish()
            return
        }

        refreshProfileUi()
    }

    override fun onResume() {
        super.onResume()
        refreshProfileUi()
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btnBackToGarage)
        btnEdit = findViewById(R.id.btnEditProfilePage)
        btnAddFuelEntry = findViewById(R.id.btnAddFuelEntry)
        tvVehicleName = findViewById(R.id.tvProfilePageVehicleName)
        tvProfileStatus = findViewById(R.id.tvProfilePageStatus)
        tvVehicleMeta = findViewById(R.id.tvProfilePageMeta)
        tvSessionsTotal = findViewById(R.id.tvProfilePageSessionsValue)
        tvSessionsSplit = findViewById(R.id.tvProfilePageSessionsSubvalue)
        tvFuelLogsCount = findViewById(R.id.tvProfilePageFuelValue)
        tvMaintenanceCount = findViewById(R.id.tvProfilePageMaintenanceValue)
        tvDocumentsCount = findViewById(R.id.tvProfilePageDocumentsValue)
        svGarageProfilePage = findViewById(R.id.svGarageProfilePage)
        llGarageProfilePageContent = findViewById(R.id.llGarageProfilePageContent)
        tabProfileSections = findViewById(R.id.tabProfileSections)
        llProfileOverviewSummary = findViewById(R.id.llProfileOverviewSummary)
        llProfileOverviewMetrics = findViewById(R.id.llProfileOverviewMetrics)
        llProfileOverviewRecords = findViewById(R.id.llProfileOverviewRecords)
        llProfileFuelLogsSummary = findViewById(R.id.llProfileFuelLogsSummary)
        llProfileFuelSummary = findViewById(R.id.llProfileFuelSummary)
        llProfileMaintenanceSummary = findViewById(R.id.llProfileMaintenanceSummary)
        llProfileDocumentsSummary = findViewById(R.id.llProfileDocumentsSummary)
        llProfileOverviewMotoLeanRow = findViewById(R.id.llProfileOverviewMotoLeanRow)
        tvProfileOverviewDistanceValue = findViewById(R.id.tvProfileOverviewDistanceValue)
        tvProfileOverviewDurationValue = findViewById(R.id.tvProfileOverviewDurationValue)
        tvProfileOverviewBest0to100Value = findViewById(R.id.tvProfileOverviewBest0to100Value)
        tvProfileOverviewBest0to200Value = findViewById(R.id.tvProfileOverviewBest0to200Value)
        tvProfileOverviewBest100to200Value = findViewById(R.id.tvProfileOverviewBest100to200Value)
        tvProfileOverviewBest0to402Value = findViewById(R.id.tvProfileOverviewBest0to402Value)
        tvProfileOverviewMaxSpeedValue = findViewById(R.id.tvProfileOverviewMaxSpeedValue)
        tvProfileOverviewMaxLeanLeftValue = findViewById(R.id.tvProfileOverviewMaxLeanLeftValue)
        tvProfileOverviewMaxLeanRightValue = findViewById(R.id.tvProfileOverviewMaxLeanRightValue)
        tvProfileOverviewGForceLabel = findViewById(R.id.tvProfileOverviewGForceLabel)
        tvProfileOverviewGForceValue = findViewById(R.id.tvProfileOverviewGForceValue)
        tvProfileFuelTotalSpentValue = findViewById(R.id.tvProfileFuelTotalSpentValue)
        tvProfileFuelAvgConsumptionValue = findViewById(R.id.tvProfileFuelAvgConsumptionValue)
        tvProfileFuelAvgConsumptionMeta = findViewById(R.id.tvProfileFuelAvgConsumptionMeta)
        tvProfileFuelLastPriceValue = findViewById(R.id.tvProfileFuelLastPriceValue)
        tvProfileFuelTotalLitresValue = findViewById(R.id.tvProfileFuelTotalLitresValue)
        llProfileFuelEntriesPreview = findViewById(R.id.llProfileFuelEntriesPreview)
        tvTabPlaceholder = findViewById(R.id.tvProfileTabPlaceholder)

        contentBaseTopPadding = llGarageProfilePageContent.paddingTop
        contentBaseBottomPadding = llGarageProfilePageContent.paddingBottom
        val addFuelButtonLayoutParams = btnAddFuelEntry.layoutParams as ViewGroup.MarginLayoutParams
        addFuelButtonBaseBottomMargin = addFuelButtonLayoutParams.bottomMargin
        addFuelButtonBaseEndMargin = addFuelButtonLayoutParams.marginEnd
    }

    private fun applySystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(svGarageProfilePage) { _, insets ->
            latestWindowInsets = insets
            updateInsetsUi()
            insets
        }

        ViewCompat.requestApplyInsets(svGarageProfilePage)
    }

    private fun updateInsetsUi() {
        val systemBarsInsets = latestWindowInsets?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        ) ?: Insets.NONE

        val extraBottomContentPadding = if (selectedTabPosition == 1) dpToPx(92) else 0
        llGarageProfilePageContent.updatePadding(
            top = contentBaseTopPadding + systemBarsInsets.top,
            bottom = contentBaseBottomPadding + systemBarsInsets.bottom + extraBottomContentPadding
        )

        btnAddFuelEntry.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = addFuelButtonBaseBottomMargin + systemBarsInsets.bottom
            marginEnd = addFuelButtonBaseEndMargin + systemBarsInsets.right
        }
    }

    private fun setupProfileTabs() {
        tabProfileSections.removeAllTabs()
        tabProfileSections.addTab(
            tabProfileSections.newTab()
                .setText(getString(R.string.garage_profile_tab_overview))
                .setIcon(R.drawable.ic_sessions)
        )
        tabProfileSections.addTab(
            tabProfileSections.newTab()
                .setText(getString(R.string.garage_profile_tab_fuel))
                .setIcon(R.drawable.ic_tab_gas_station)
        )
        tabProfileSections.addTab(
            tabProfileSections.newTab()
                .setText(getString(R.string.garage_profile_tab_maintenance))
                .setIcon(R.drawable.ic_wrench)
        )
        tabProfileSections.addTab(
            tabProfileSections.newTab()
                .setText(getString(R.string.garage_profile_tab_documents))
                .setIcon(R.drawable.ic_tab_document)
        )

        tabProfileSections.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateTabPlaceholder(tab?.position ?: 0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit

            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })

        updateTabPlaceholder(0)
    }

    private fun updateTabPlaceholder(position: Int) {
        selectedTabPosition = position

        val isOverviewTab = position == 0
        val isFuelTab = position == 1
        val isMaintenanceTab = position == 2
        val isDocumentsTab = position == 3

        llProfileOverviewSummary.visibility = if (isOverviewTab) View.VISIBLE else View.GONE
        llProfileOverviewMetrics.visibility = if (isOverviewTab) View.VISIBLE else View.GONE
        llProfileOverviewRecords.visibility = if (isOverviewTab) View.VISIBLE else View.GONE
        llProfileFuelLogsSummary.visibility = if (isFuelTab) View.VISIBLE else View.GONE
        llProfileFuelSummary.visibility = if (isFuelTab) View.VISIBLE else View.GONE
        llProfileMaintenanceSummary.visibility = if (isMaintenanceTab) View.VISIBLE else View.GONE
        llProfileDocumentsSummary.visibility = if (isDocumentsTab) View.VISIBLE else View.GONE
        btnAddFuelEntry.visibility = if (isFuelTab) View.VISIBLE else View.GONE

        updateInsetsUi()

        val shouldShowPlaceholder = isMaintenanceTab || isDocumentsTab
        tvTabPlaceholder.visibility = if (shouldShowPlaceholder) View.VISIBLE else View.GONE

        if (!shouldShowPlaceholder) {
            return
        }

        val tabLabelRes = when (position) {
            1 -> R.string.garage_profile_tab_fuel
            2 -> R.string.garage_profile_tab_maintenance
            3 -> R.string.garage_profile_tab_documents
            else -> R.string.garage_profile_tab_overview
        }
        tvTabPlaceholder.text = getString(
            R.string.garage_profile_tab_placeholder_format,
            getString(tabLabelRes)
        )
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        btnEdit.setOnClickListener {
            currentProfile?.let { profile -> showEditProfileDialog(profile) }
        }
        btnAddFuelEntry.setOnClickListener {
            if (profileId == -1L) {
                return@setOnClickListener
            }

            startActivity(Intent(this, GarageFuelEntryActivity::class.java).apply {
                putExtra(GarageFuelEntryActivity.EXTRA_PROFILE_ID, profileId)
            })
        }
    }

    private fun dpToPx(valueDp: Int): Int {
        return (valueDp * resources.displayMetrics.density).toInt()
    }

    private fun refreshProfileUi() {
        val profile = ProfileStorage.loadProfiles(this).find { it.id == profileId }
        if (profile == null) {
            finish()
            return
        }

        currentProfile = profile
        bindProfile(profile)
    }

    private fun bindProfile(profile: Profile) {
        tvVehicleName.text = getGarageDisplayName(profile)

        val isActive = profile.id == ProfileStorage.getSelectedProfileId(this)
        if (isActive) {
            tvProfileStatus.text = getString(R.string.garage_active_badge)
            tvProfileStatus.setBackgroundResource(R.drawable.bg_profile_status_active)
        } else {
            tvProfileStatus.text = getString(R.string.garage_profile_status_inactive)
            tvProfileStatus.setBackgroundResource(R.drawable.bg_profile_status_inactive)
        }

        val profileRaces = RouteStorage.loadRaces(this).filter { it.profileId == profile.id }
        val profileDragSessions = DragStorage.loadDragSessions(this).filter { it.profileId == profile.id }
        val trackSessions = profileRaces.size
        val dragSessions = profileDragSessions.size
        val totalSessions = trackSessions + dragSessions

        val totalDistanceKm = profileRaces.sumOf { it.distance }
        val fuelEntries = GarageFuelEntryStorage.loadEntries(this, profile.id)
            .sortedByDescending { it.createdAt.takeIf { createdAt -> createdAt > 0L } ?: it.id }
        val typeText = when (profile.vehicleType) {
            Profile.VehicleType.CAR -> getString(R.string.garage_vehicle_car).uppercase(Locale.getDefault())
            Profile.VehicleType.MOTORCYCLE -> getString(R.string.garage_vehicle_motorcycle).uppercase(Locale.getDefault())
        }
        val distanceText = NumberFormat.getIntegerInstance(Locale.US)
            .format(totalDistanceKm.coerceAtLeast(0.0).toLong())
        tvVehicleMeta.text = getString(R.string.garage_profile_meta_format, typeText, distanceText)

        val totalTimeMs = calculateTotalProfileTimeMs(profileRaces, profileDragSessions)
        bindOverviewMetrics(totalDistanceKm, totalTimeMs)
        bindOverviewPerformance(profile, profileRaces, profileDragSessions)
        bindFuelData(fuelEntries)

        tvSessionsTotal.text = totalSessions.toString()
        tvSessionsSplit.text = getString(
            R.string.garage_profile_sessions_split,
            dragSessions,
            trackSessions
        )
        tvFuelLogsCount.text = fuelEntries.size.toString()
        tvMaintenanceCount.text = getExtraStatCount(profile.id, "maintenance_count").toString()
        tvDocumentsCount.text = getExtraStatCount(profile.id, "documents_count").toString()
    }

    private fun bindFuelData(entries: List<GarageFuelEntry>) {
        val consumptionSummary = buildFuelConsumptionSummary(entries)

        if (entries.isEmpty()) {
            tvProfileFuelTotalSpentValue.text = getString(R.string.garage_profile_fuel_total_spent_placeholder)
            tvProfileFuelAvgConsumptionValue.text = getString(R.string.garage_profile_fuel_avg_consumption_placeholder)
            tvProfileFuelAvgConsumptionMeta.text = getString(R.string.garage_profile_fuel_avg_consumption_meta_missing)
            tvProfileFuelLastPriceValue.text = getString(R.string.garage_profile_fuel_last_price_placeholder)
            tvProfileFuelTotalLitresValue.text = getString(R.string.garage_profile_fuel_total_litres_placeholder)
            bindFuelHistory(entries)
            return
        }

        val totalSpent = entries.sumOf { it.totalAmount.coerceAtLeast(0.0) }
        val totalLitres = entries.sumOf { it.litres.coerceAtLeast(0.0) }
        val lastPrice = entries.firstOrNull()?.pricePerLitre?.takeIf { it > 0.0 }
        val avgConsumption = consumptionSummary.averageLPer100Km

        tvProfileFuelTotalSpentValue.text = formatCurrency(totalSpent)
        tvProfileFuelAvgConsumptionValue.text = avgConsumption?.let {
            String.format(Locale.getDefault(), "%.1f", it)
        } ?: getString(R.string.garage_profile_fuel_avg_consumption_placeholder)
        tvProfileFuelAvgConsumptionMeta.text = resolveFuelConsumptionMeta(consumptionSummary)
        tvProfileFuelLastPriceValue.text = lastPrice?.let(::formatCurrency) ?: getString(R.string.garage_profile_fuel_last_price_placeholder)
        tvProfileFuelTotalLitresValue.text = formatTotalLitres(totalLitres)
        bindFuelHistory(entries)
    }

    private fun bindFuelHistory(entries: List<GarageFuelEntry>) {
        llProfileFuelEntriesPreview.removeAllViews()

        if (entries.isEmpty()) {
            llProfileFuelEntriesPreview.addView(
                TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = getString(R.string.garage_profile_fuel_empty_state)
                    setTextColor(ContextCompat.getColor(this@GarageProfilePageActivity, R.color.text_tertiary))
                    textSize = 12f
                    setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6))
                }
            )
            return
        }

        entries.forEachIndexed { index, entry ->
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_profile_fuel_entry, llProfileFuelEntriesPreview, false)

            itemView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (index == 0) 0 else dpToPx(8)
            }

            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryDate).text = formatFuelEntryDate(entry)
            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryMeta).text = buildFuelEntryMeta(entry)
            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryAmount).text = formatCurrency(entry.totalAmount)
            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryStats).text = buildFuelEntryStats(entry)
            itemView.setOnClickListener {
                startActivity(Intent(this, GarageFuelEntryActivity::class.java).apply {
                    putExtra(GarageFuelEntryActivity.EXTRA_PROFILE_ID, profileId)
                    putExtra(GarageFuelEntryActivity.EXTRA_ENTRY_ID, entry.id)
                })
            }

            llProfileFuelEntriesPreview.addView(itemView)
        }
    }

    private fun buildFuelConsumptionSummary(entries: List<GarageFuelEntry>): FuelConsumptionSummary {
        val odometerEntries = entries
            .filter { it.odometerKm > 0L }
            .sortedWith(compareBy<GarageFuelEntry> { it.odometerKm }.thenBy { it.createdAt.takeIf { createdAt -> createdAt > 0L } ?: it.id })

        var anchorFullTank: GarageFuelEntry? = null
        var litresSinceAnchor = 0.0
        var totalPeriodLitres = 0.0
        var totalPeriodDistanceKm = 0L
        var validPeriodsCount = 0

        odometerEntries.forEach { entry ->
            val currentAnchor = anchorFullTank

            if (currentAnchor == null) {
                if (entry.isFullTank) {
                    anchorFullTank = entry
                    litresSinceAnchor = 0.0
                }
                return@forEach
            }

            if (entry.odometerKm <= currentAnchor.odometerKm) {
                return@forEach
            }

            litresSinceAnchor += entry.litres.coerceAtLeast(0.0)

            if (!entry.isFullTank) {
                return@forEach
            }

            val distanceKm = entry.odometerKm - currentAnchor.odometerKm
            if (distanceKm > 0L && litresSinceAnchor > 0.0) {
                totalPeriodLitres += litresSinceAnchor
                totalPeriodDistanceKm += distanceKm
                validPeriodsCount += 1
            }

            anchorFullTank = entry
            litresSinceAnchor = 0.0
        }

        val averageConsumption = if (totalPeriodDistanceKm > 0L && totalPeriodLitres > 0.0) {
            (totalPeriodLitres * 100.0) / totalPeriodDistanceKm.toDouble()
        } else {
            null
        }

        return FuelConsumptionSummary(
            averageLPer100Km = averageConsumption,
            validPeriodsCount = validPeriodsCount,
            hasAnyFullTank = odometerEntries.any { it.isFullTank }
        )
    }

    private fun resolveFuelConsumptionMeta(summary: FuelConsumptionSummary): String {
        return when {
            summary.validPeriodsCount > 0 -> getString(R.string.garage_profile_fuel_avg_consumption_meta_real)
            summary.hasAnyFullTank -> getString(R.string.garage_profile_fuel_avg_consumption_meta_waiting)
            else -> getString(R.string.garage_profile_fuel_avg_consumption_meta_missing)
        }
    }

    private fun formatFuelEntryDate(entry: GarageFuelEntry): String {
        val rawDate = entry.date.trim()
        val displayDate = if (rawDate.isNotBlank()) {
            rawDate
        } else {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(entry.createdAt))
        }
        return displayDate.uppercase(Locale.getDefault())
    }

    private fun buildFuelEntryMeta(entry: GarageFuelEntry): String {
        val parts = mutableListOf<String>()
        if (entry.station.isNotBlank()) {
            parts += entry.station
        }
        if (entry.odometerKm > 0L) {
            parts += formatOdometer(entry.odometerKm)
        }
        return parts.joinToString(" • ")
    }

    private fun buildFuelEntryStats(entry: GarageFuelEntry): String {
        return listOf(
            formatLitres(entry.litres),
            String.format(Locale.getDefault(), "%.2f лв/L", entry.pricePerLitre)
        ).joinToString(" • ")
    }

    private fun formatCurrency(value: Double): String {
        return String.format(Locale.getDefault(), "%.2f лв", value)
    }

    private fun formatLitres(value: Double): String {
        return if (value % 1.0 == 0.0) {
            String.format(Locale.getDefault(), "%.0f L", value)
        } else {
            String.format(Locale.getDefault(), "%.1f L", value)
        }
    }

    private fun formatTotalLitres(value: Double): String {
        return String.format(Locale.getDefault(), "%.1f L", value)
    }

    private fun formatOdometer(valueKm: Long): String {
        return "${NumberFormat.getIntegerInstance(Locale.US).format(valueKm)} km"
    }

    private data class FuelConsumptionSummary(
        val averageLPer100Km: Double?,
        val validPeriodsCount: Int,
        val hasAnyFullTank: Boolean
    )

    private fun bindOverviewMetrics(totalDistanceKm: Double, totalTimeMs: Long) {
        tvProfileOverviewDistanceValue.text = String.format(
            Locale.getDefault(),
            "%.1f km",
            totalDistanceKm.coerceAtLeast(0.0)
        )

        val totalSeconds = totalTimeMs.coerceAtLeast(0L) / 1000
        val totalHours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        tvProfileOverviewDurationValue.text = getString(
            R.string.profile_detail_duration_format,
            totalHours,
            minutes
        )
    }

    private fun bindOverviewPerformance(
        profile: Profile,
        profileRaces: List<Race>,
        profileDragSessions: List<DragSession>
    ) {
        val bestTimes = computeOverviewBestTimes(profile.id, profileRaces, profileDragSessions)

        tvProfileOverviewBest0to100Value.text = formatNanosToSeconds(bestTimes.best0to100Ns)
        tvProfileOverviewBest0to200Value.text = formatNanosToSeconds(bestTimes.best0to200Ns)
        tvProfileOverviewBest100to200Value.text = formatNanosToSeconds(bestTimes.best100to200Ns)
        tvProfileOverviewBest0to402Value.text = formatNanosToSeconds(bestTimes.best0to402Ns)
        tvProfileOverviewMaxSpeedValue.text = if (bestTimes.maxSpeedKmh != null) {
            String.format(Locale.getDefault(), "%.0f km/h", bestTimes.maxSpeedKmh)
        } else {
            "--"
        }

        if (profile.vehicleType == Profile.VehicleType.MOTORCYCLE) {
            llProfileOverviewMotoLeanRow.visibility = View.VISIBLE
            tvProfileOverviewMaxLeanLeftValue.text = formatDegrees(bestTimes.maxLeanLeftDeg)
            tvProfileOverviewMaxLeanRightValue.text = formatDegrees(bestTimes.maxLeanRightDeg)
            tvProfileOverviewGForceLabel.text = getString(R.string.garage_profile_stat_max_accel_g_force)
            tvProfileOverviewGForceValue.text = formatG(bestTimes.maxAccelG ?: bestTimes.maxOverallG)
        } else {
            llProfileOverviewMotoLeanRow.visibility = View.GONE
            tvProfileOverviewGForceLabel.text = getString(R.string.garage_profile_stat_max_g_force)
            tvProfileOverviewGForceValue.text = formatG(bestTimes.maxOverallG ?: bestTimes.maxAccelG)
        }
    }

    private fun computeOverviewBestTimes(
        profileId: Long,
        profileRaces: List<Race>,
        profileDragSessions: List<DragSession>
    ): OverviewBestTimes {
        var best0to100 = Long.MAX_VALUE
        var best0to200 = Long.MAX_VALUE
        var best100to200 = Long.MAX_VALUE
        var best0to402 = Long.MAX_VALUE
        var maxSpeedKmh = 0f
        var maxLeanLeftDeg = 0f
        var maxLeanRightDeg = 0f
        var maxOverallG = 0f
        var maxAccelG = 0f

        fun considerBest(current: Long, candidate: Long): Long {
            return if (candidate > 0 && candidate < current) candidate else current
        }

        profileRaces.forEach { race ->
            best0to100 = considerBest(best0to100, race.time0to100)
            best0to200 = considerBest(best0to200, race.time0to200)
            best100to200 = considerBest(best100to200, race.time100to200)
            if (race.maxSpeed > maxSpeedKmh) {
                maxSpeedKmh = race.maxSpeed
            }
            if (race.maxLeftAngle > maxLeanLeftDeg) {
                maxLeanLeftDeg = race.maxLeftAngle
            }
            if (race.maxRightAngle > maxLeanRightDeg) {
                maxLeanRightDeg = race.maxRightAngle
            }
        }

        profileDragSessions.forEach { session ->
            best0to100 = considerBest(best0to100, session.best0to100)
            best0to200 = considerBest(best0to200, session.best0to200)
            best100to200 = considerBest(best100to200, session.best100to200)
            best0to402 = considerBest(best0to402, session.best0to402)

            session.attempts.forEach { attempt ->
                best0to100 = considerBest(best0to100, attempt.time0to100)
                best0to200 = considerBest(best0to200, attempt.time0to200)
                best100to200 = considerBest(best100to200, attempt.time100to200)
                if (attempt.time100to200 <= 0L && attempt.time0to200 > 0L && attempt.time0to100 > 0L && attempt.time0to200 > attempt.time0to100) {
                    best100to200 = considerBest(best100to200, attempt.time0to200 - attempt.time0to100)
                }
                best0to402 = considerBest(best0to402, attempt.time0to402)
                if (attempt.maxSpeed > maxSpeedKmh) {
                    maxSpeedKmh = attempt.maxSpeed
                }

                val attemptPeakG = attempt.gSamples.maxOrNull() ?: 0f
                if (attemptPeakG > maxOverallG) {
                    maxOverallG = attemptPeakG
                }

                val attemptMaxPositiveAccelMs2 = attempt.gpsAccelSamples
                    .asSequence()
                    .filter { it > 0f }
                    .maxOrNull()
                if (attemptMaxPositiveAccelMs2 != null) {
                    val accelG = attemptMaxPositiveAccelMs2 / 9.81f
                    if (accelG > maxAccelG) {
                        maxAccelG = accelG
                    }
                }
            }
        }

        val trackOutingMaxSpeed = findBestTrackOutingMaxSpeedKmh(profileId)
        if (trackOutingMaxSpeed != null && trackOutingMaxSpeed > maxSpeedKmh) {
            maxSpeedKmh = trackOutingMaxSpeed
        }

        return OverviewBestTimes(
            best0to100Ns = best0to100.takeIf { it != Long.MAX_VALUE },
            best0to200Ns = best0to200.takeIf { it != Long.MAX_VALUE },
            best100to200Ns = best100to200.takeIf { it != Long.MAX_VALUE },
            best0to402Ns = best0to402.takeIf { it != Long.MAX_VALUE },
            maxSpeedKmh = maxSpeedKmh.takeIf { it > 0f },
            maxLeanLeftDeg = maxLeanLeftDeg.takeIf { it > 0f },
            maxLeanRightDeg = maxLeanRightDeg.takeIf { it > 0f },
            maxOverallG = maxOverallG.takeIf { it > 0f },
            maxAccelG = maxAccelG.takeIf { it > 0f }
        )
    }

    private fun calculateTotalProfileTimeMs(
        profileRaces: List<Race>,
        profileDragSessions: List<DragSession>
    ): Long {
        val racesTimeMs = profileRaces.sumOf { race -> calculateRaceDurationMs(race) }
        val dragTimeMs = profileDragSessions.sumOf { session ->
            session.attempts.sumOf { attempt -> attempt.duration / 1_000_000 }
        }
        return racesTimeMs + dragTimeMs
    }

    private fun calculateRaceDurationMs(race: Race): Long {
        if (race.duration > 0) {
            return race.duration.toLong()
        }

        val points = if (race.routePoints.isNotEmpty()) {
            race.routePoints
        } else {
            val allPoints = RouteStorage.loadRoutePoints(this, race.id)
            if (allPoints.size >= 2) {
                listOf(allPoints.first(), allPoints.last())
            } else {
                allPoints
            }
        }

        if (points.isEmpty()) {
            return 0L
        }

        val firstPoint = points.first()
        val lastPoint = points.last()
        val firstAbsoluteTime = firstPoint.absoluteTime
        val lastAbsoluteTime = lastPoint.absoluteTime

        if (firstAbsoluteTime > 0 && lastAbsoluteTime > firstAbsoluteTime) {
            return lastAbsoluteTime - firstAbsoluteTime
        }

        val firstTimestamp = firstPoint.timestamp
        val lastTimestamp = lastPoint.timestamp
        return if (lastTimestamp > firstTimestamp) {
            lastTimestamp - firstTimestamp
        } else {
            0L
        }
    }

    private fun findBestTrackOutingMaxSpeedKmh(profileId: Long): Float? {
        var maxSpeed = 0f
        forEachTrackOuting(profileId) { prefs, sessionId, outingNumber ->
            val speedText = prefs.getString("${sessionId}_outing_${outingNumber}_max_speed", "").orEmpty()
            val parsed = parseDisplayedNumeric(speedText)
            if (parsed != null && parsed > maxSpeed) {
                maxSpeed = parsed
            }
        }
        return maxSpeed.takeIf { it > 0f }
    }

    private inline fun forEachTrackOuting(
        profileId: Long,
        action: (android.content.SharedPreferences, String, Int) -> Unit
    ) {
        val prefs = getSharedPreferences("track_outings", Context.MODE_PRIVATE)
        val sessionCountKeys = prefs.all.keys.filter {
            it.endsWith("_outing_count") && it.startsWith("${profileId}_")
        }

        sessionCountKeys.forEach { key ->
            val sessionId = key.removeSuffix("_outing_count")
            val outingCount = prefs.getInt(key, 0)
            for (outingNumber in 1..outingCount) {
                action(prefs, sessionId, outingNumber)
            }
        }
    }

    private fun formatNanosToSeconds(value: Long?): String {
        return if (value != null && value > 0L) {
            String.format(Locale.getDefault(), "%.3f s", value / 1_000_000_000.0)
        } else {
            "--"
        }
    }

    private fun parseDisplayedNumeric(value: String): Float? {
        val match = Regex("[-+]?\\d+(?:[\\.,]\\d+)?").find(value.trim()) ?: return null
        return match.value.replace(',', '.').toFloatOrNull()
    }

    private fun formatDegrees(value: Float?): String {
        return if (value != null && value > 0f) {
            String.format(Locale.getDefault(), "%.1f°", value)
        } else {
            "--"
        }
    }

    private fun formatG(value: Float?): String {
        return if (value != null && value > 0f) {
            String.format(Locale.getDefault(), "%.2fg", value)
        } else {
            "--"
        }
    }

    private data class OverviewBestTimes(
        val best0to100Ns: Long?,
        val best0to200Ns: Long?,
        val best100to200Ns: Long?,
        val best0to402Ns: Long?,
        val maxSpeedKmh: Float?,
        val maxLeanLeftDeg: Float?,
        val maxLeanRightDeg: Float?,
        val maxOverallG: Float?,
        val maxAccelG: Float?
    )

    private fun getExtraStatCount(profileId: Long, suffix: String): Int {
        val prefs = getSharedPreferences("garage_profile_extra_stats", Context.MODE_PRIVATE)
        return prefs.getInt("profile_${profileId}_$suffix", 0)
    }

    private fun getGarageDisplayName(profile: Profile): String {
        val prefs = getSharedPreferences("garage_display_names", Context.MODE_PRIVATE)
        val key = "profile_${profile.id}_display_name"
        return prefs.getString(key, null).orEmpty().ifBlank { profile.name }
    }

    private fun updateSelection(selectedCard: LinearLayout, unselectedCard: LinearLayout) {
        selectedCard.background = ContextCompat.getDrawable(this, R.drawable.vehicle_option_selected_background)
        unselectedCard.background = ContextCompat.getDrawable(this, R.drawable.vehicle_option_background)
    }

    private fun showSearchPicker(title: String, options: List<String>, onSelect: (String) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search_picker, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvPickerTitle)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnClosePicker)
        val etSearch = dialogView.findViewById<TextInputEditText>(R.id.etSearch)
        val listView = dialogView.findViewById<ListView>(R.id.lvOptions)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tvEmpty)

        tvTitle.text = title

        val adapter = object : ArrayAdapter<String>(this, R.layout.dropdown_item_normal, R.id.text1, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getView(position, convertView, parent)
            }
        }
        listView.adapter = adapter
        listView.emptyView = tvEmpty

        etSearch.addTextChangedListener { text ->
            adapter.filter.filter(text?.toString() ?: "")
        }

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position)
            if (item != null) {
                onSelect(item)
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEditProfileDialog(profile: Profile) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_profile, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etProfileName)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseEdit)
        val btnCar = dialogView.findViewById<LinearLayout>(R.id.btnEditTypeCar)
        val btnMotorcycle = dialogView.findViewById<LinearLayout>(R.id.btnEditTypeMotorcycle)
        val brandInput = dialogView.findViewById<TextInputLayout>(R.id.brandInput)
        val modelInput = dialogView.findViewById<TextInputLayout>(R.id.modelInput)
        val brandDropdown = dialogView.findViewById<TextInputEditText>(R.id.brandDropdown)
        val modelDropdown = dialogView.findViewById<TextInputEditText>(R.id.modelDropdown)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveEdit)

        var selectedType = profile.vehicleType
        var selectedBrand = ""
        var selectedModel = ""
        etName.setText(getGarageDisplayName(profile))
        if (selectedType == Profile.VehicleType.CAR) {
            updateSelection(btnCar, btnMotorcycle)
        } else {
            updateSelection(btnMotorcycle, btnCar)
        }

        fun updateModelEnabled() {
            val enabled = selectedBrand.isNotEmpty()
            modelInput.isEnabled = enabled
            modelDropdown.isEnabled = enabled
        }

        fun clearBrandAndModel() {
            selectedBrand = ""
            selectedModel = ""
            brandDropdown.setText("")
            modelDropdown.setText("")
            brandInput.error = null
            modelInput.error = null
            updateModelEnabled()
        }

        fun resolveBrandModelFromName(name: String, vehicleType: Profile.VehicleType): Pair<String, String> {
            val brands = if (vehicleType == Profile.VehicleType.CAR) {
                VehicleData.carBrands.toList()
            } else {
                VehicleData.motorcycleBrands.toList()
            }
            val sortedBrands = brands.sortedByDescending { it.length }
            val trimmedName = name.trim()
            val match = sortedBrands.firstOrNull { brand ->
                trimmedName.equals(brand, true) || trimmedName.startsWith("$brand ", true)
            }
            return if (match != null) {
                val model = trimmedName.removePrefix(match).trim()
                match to model
            } else {
                "" to ""
            }
        }

        btnCar.setOnClickListener {
            selectedType = Profile.VehicleType.CAR
            updateSelection(btnCar, btnMotorcycle)
            clearBrandAndModel()
        }

        btnMotorcycle.setOnClickListener {
            selectedType = Profile.VehicleType.MOTORCYCLE
            updateSelection(btnMotorcycle, btnCar)
            clearBrandAndModel()
        }

        brandDropdown.setOnClickListener {
            val brandsRaw = if (selectedType == Profile.VehicleType.CAR) {
                VehicleData.carBrands.toList()
            } else {
                VehicleData.motorcycleBrands.toList()
            }
            val brands = brandsRaw.filterNot {
                it.equals(getString(R.string.garage_most_popular), true) || it.contains("Най", true)
            }
            showSearchPicker(getString(R.string.garage_brand_label), brands) { selected ->
                selectedBrand = selected
                brandDropdown.setText(selected)
                selectedModel = ""
                modelDropdown.setText("")
                updateModelEnabled()
            }
        }

        modelDropdown.setOnClickListener {
            if (selectedBrand.isEmpty()) {
                Toast.makeText(this, getString(R.string.garage_select_brand), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val models = if (selectedType == Profile.VehicleType.CAR) {
                VehicleData.carModels[selectedBrand]?.toList() ?: emptyList()
            } else {
                VehicleData.motorcycleModels[selectedBrand]?.toList() ?: emptyList()
            }
            showSearchPicker(getString(R.string.garage_model_label), models) { selected ->
                selectedModel = selected
                modelDropdown.setText(selected)
            }
        }

        val (initialBrand, initialModel) = resolveBrandModelFromName(profile.name, selectedType)
        if (initialBrand.isNotEmpty()) {
            selectedBrand = initialBrand
            brandDropdown.setText(initialBrand)
        }
        if (initialModel.isNotEmpty()) {
            selectedModel = initialModel
            modelDropdown.setText(initialModel)
        }
        updateModelEnabled()

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val displayName = etName.text?.toString()?.trim().orEmpty()
            if (selectedBrand.isEmpty()) {
                brandInput.error = getString(R.string.garage_select_brand)
                return@setOnClickListener
            }
            if (selectedModel.isEmpty()) {
                modelInput.error = getString(R.string.garage_select_model)
                return@setOnClickListener
            }

            val prefs = getSharedPreferences("garage_display_names", Context.MODE_PRIVATE)
            val key = "profile_${profile.id}_display_name"
            if (displayName.isBlank()) {
                prefs.edit().remove(key).apply()
            } else {
                prefs.edit().putString(key, displayName).apply()
            }

            val profiles = ProfileStorage.loadProfiles(this).toMutableList()
            val profileIndex = profiles.indexOfFirst { it.id == profile.id }
            if (profileIndex == -1) {
                dialog.dismiss()
                return@setOnClickListener
            }

            val updatedProfile = profiles[profileIndex].copy(
                name = "$selectedBrand $selectedModel",
                vehicleType = selectedType
            )
            profiles[profileIndex] = updatedProfile
            ProfileStorage.saveProfiles(this, profiles)

            currentProfile = updatedProfile
            bindProfile(updatedProfile)
            dialog.dismiss()
        }

        dialog.show()
    }

    companion object {
        const val EXTRA_PROFILE_ID = "PROFILE_ID"
    }
}
