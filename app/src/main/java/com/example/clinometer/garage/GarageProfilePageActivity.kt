package com.example.clinometer.garage

import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.Insets
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.example.clinometer.DragStorage
import com.example.clinometer.LapData
import com.example.clinometer.Profile
import com.example.clinometer.Race
import com.example.clinometer.R
import com.example.clinometer.RouteStorage
import com.example.clinometer.DragSession
import com.example.clinometer.data.GarageDocumentEntry
import com.example.clinometer.data.GarageDocumentEntryStorage
import com.example.clinometer.data.GarageDocumentReceiptStorage
import com.example.clinometer.data.GarageFuelEntry
import com.example.clinometer.data.GarageFuelEntryStorage
import com.example.clinometer.data.GarageFuelReceiptStorage
import com.example.clinometer.data.GarageMaintenanceEntry
import com.example.clinometer.data.GarageMaintenanceEntryStorage
import com.example.clinometer.data.GarageMaintenanceReceiptStorage
import com.example.clinometer.data.GarageOdometerSource
import com.example.clinometer.data.GarageOdometerTimeline
import com.example.clinometer.data.ProfileSessionSummary
import com.example.clinometer.data.ProfileSessionSummaryStore
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.data.VehicleData
import com.example.clinometer.settings.LanguageManager
import com.google.gson.Gson
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class GarageProfilePageActivity : AppCompatActivity() {

    private var profileId: Long = -1L
    private var currentProfile: Profile? = null
    private var currentFuelEntries: List<GarageFuelEntry> = emptyList()
    private var currentMaintenanceEntries: List<GarageMaintenanceEntry> = emptyList()
    private var currentDocumentEntries: List<GarageDocumentEntry> = emptyList()
    private val selectedFuelEntryIds = linkedSetOf<Long>()
    private val selectedMaintenanceEntryIds = linkedSetOf<Long>()
    private val selectedDocumentEntryIds = linkedSetOf<Long>()
    private var selectedMaintenanceReminderFilter = MaintenanceReminderFilter.ALL
    private var selectedDocumentHistoryFilter = DocumentHistoryFilter.ALL

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
    private lateinit var btnDeleteFuelHistorySelection: ImageButton
    private lateinit var tvProfileMaintenanceTotalSpentValue: TextView
    private lateinit var tvProfileMaintenanceYearSpentValue: TextView
    private lateinit var tvProfileMaintenancePartsSpentValue: TextView
    private lateinit var tvProfileMaintenanceLaborSpentValue: TextView
    private lateinit var tvProfileMaintenanceFilterAll: TextView
    private lateinit var tvProfileMaintenanceFilterActive: TextView
    private lateinit var tvProfileMaintenanceFilterOverdue: TextView
    private lateinit var llProfileMaintenanceEntriesPreview: LinearLayout
    private lateinit var btnDeleteMaintenanceHistorySelection: ImageButton
    private lateinit var tvProfileDocumentsExpiringSoonValue: TextView
    private lateinit var tvProfileDocumentsExpiredValue: TextView
    private lateinit var tvProfileDocumentsFilterAll: TextView
    private lateinit var tvProfileDocumentsFilterActive: TextView
    private lateinit var tvProfileDocumentsFilterOverdue: TextView
    private lateinit var llProfileDocumentEntriesPreview: LinearLayout
    private lateinit var btnDeleteDocumentHistorySelection: ImageButton
    private lateinit var tvTabPlaceholder: TextView
    private var latestWindowInsets: WindowInsetsCompat? = null
    private var selectedTabPosition: Int = 0
    private var contentBaseTopPadding: Int = 0
    private var contentBaseBottomPadding: Int = 0
    private var addFuelButtonBaseBottomMargin: Int = 0
    private var addFuelButtonBaseEndMargin: Int = 0
    private var profileRefreshJob: Job? = null
    private var profileRefreshRequestId: Long = 0L

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_garage_profile_page)

        bindViews()
        applySystemInsets()
        setupBackNavigation()
        setupProfileTabs()
        setupClickListeners()

        profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
        if (profileId == -1L) {
            finish()
            return
        }
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
        btnDeleteFuelHistorySelection = findViewById(R.id.btnDeleteFuelHistorySelection)
        tvProfileMaintenanceTotalSpentValue = findViewById(R.id.tvProfileMaintenanceTotalSpentValue)
        tvProfileMaintenanceYearSpentValue = findViewById(R.id.tvProfileMaintenanceYearSpentValue)
        tvProfileMaintenancePartsSpentValue = findViewById(R.id.tvProfileMaintenancePartsSpentValue)
        tvProfileMaintenanceLaborSpentValue = findViewById(R.id.tvProfileMaintenanceLaborSpentValue)
        tvProfileMaintenanceFilterAll = findViewById(R.id.tvProfileMaintenanceFilterAll)
        tvProfileMaintenanceFilterActive = findViewById(R.id.tvProfileMaintenanceFilterActive)
        tvProfileMaintenanceFilterOverdue = findViewById(R.id.tvProfileMaintenanceFilterOverdue)
        llProfileMaintenanceEntriesPreview = findViewById(R.id.llProfileMaintenanceEntriesPreview)
        btnDeleteMaintenanceHistorySelection = findViewById(R.id.btnDeleteMaintenanceHistorySelection)
        tvProfileDocumentsExpiringSoonValue = findViewById(R.id.tvProfileDocumentsExpiringSoonValue)
        tvProfileDocumentsExpiredValue = findViewById(R.id.tvProfileDocumentsExpiredValue)
        tvProfileDocumentsFilterAll = findViewById(R.id.tvProfileDocumentsFilterAll)
        tvProfileDocumentsFilterActive = findViewById(R.id.tvProfileDocumentsFilterActive)
        tvProfileDocumentsFilterOverdue = findViewById(R.id.tvProfileDocumentsFilterOverdue)
        llProfileDocumentEntriesPreview = findViewById(R.id.llProfileDocumentEntriesPreview)
        btnDeleteDocumentHistorySelection = findViewById(R.id.btnDeleteDocumentHistorySelection)
        tvTabPlaceholder = findViewById(R.id.tvProfileTabPlaceholder)

        updateMaintenanceReminderFilterUi()
        updateDocumentHistoryFilterUi()

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

        val extraBottomContentPadding = if (selectedTabPosition == 1 || selectedTabPosition == 2 || selectedTabPosition == 3) dpToPx(92) else 0
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

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (clearActiveHistorySelectionModeIfNeeded()) {
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    private fun updateTabPlaceholder(position: Int) {
        selectedTabPosition = position
        clearSelectionsForInactiveTabs(position)

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
        refreshHistoryActionChrome()

        updateInsetsUi()

        val shouldShowPlaceholder = false
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
        btnDeleteFuelHistorySelection.setOnClickListener { confirmDeleteSelectedFuelEntries() }
        btnDeleteMaintenanceHistorySelection.setOnClickListener { confirmDeleteSelectedMaintenanceEntries() }
        btnDeleteDocumentHistorySelection.setOnClickListener { confirmDeleteSelectedDocumentEntries() }
        tvProfileMaintenanceFilterAll.setOnClickListener {
            setMaintenanceReminderFilter(MaintenanceReminderFilter.ALL)
        }
        tvProfileMaintenanceFilterActive.setOnClickListener {
            setMaintenanceReminderFilter(MaintenanceReminderFilter.ACTIVE)
        }
        tvProfileMaintenanceFilterOverdue.setOnClickListener {
            setMaintenanceReminderFilter(MaintenanceReminderFilter.OVERDUE)
        }
        tvProfileDocumentsFilterAll.setOnClickListener {
            setDocumentHistoryFilter(DocumentHistoryFilter.ALL)
        }
        tvProfileDocumentsFilterActive.setOnClickListener {
            setDocumentHistoryFilter(DocumentHistoryFilter.ACTIVE)
        }
        tvProfileDocumentsFilterOverdue.setOnClickListener {
            setDocumentHistoryFilter(DocumentHistoryFilter.OVERDUE)
        }
        btnAddFuelEntry.setOnClickListener {
            when (selectedTabPosition) {
                1 -> {
                    if (profileId == -1L) {
                        return@setOnClickListener
                    }

                    startActivity(Intent(this, GarageFuelEntryActivity::class.java).apply {
                        putExtra(GarageFuelEntryActivity.EXTRA_PROFILE_ID, profileId)
                    })
                }

                2 -> {
                    if (profileId == -1L) {
                        return@setOnClickListener
                    }

                    startActivity(GarageMaintenanceEntryActivity.createIntent(this, profileId))
                }

                3 -> {
                    if (profileId == -1L) {
                        return@setOnClickListener
                    }

                    startActivity(GarageDocumentEntryActivity.createIntent(this, profileId))
                }
            }
        }
    }

    private fun setMaintenanceReminderFilter(filter: MaintenanceReminderFilter) {
        if (selectedMaintenanceReminderFilter == filter) {
            return
        }

        selectedMaintenanceReminderFilter = filter
        selectedMaintenanceEntryIds.clear()
        updateMaintenanceReminderFilterUi()
        bindMaintenanceHistory(currentMaintenanceEntries)
        refreshHistoryActionChrome()
    }

    private fun updateMaintenanceReminderFilterUi() {
        updateMaintenanceReminderFilterTab(
            tvProfileMaintenanceFilterAll,
            selectedMaintenanceReminderFilter == MaintenanceReminderFilter.ALL
        )
        updateMaintenanceReminderFilterTab(
            tvProfileMaintenanceFilterActive,
            selectedMaintenanceReminderFilter == MaintenanceReminderFilter.ACTIVE
        )
        updateMaintenanceReminderFilterTab(
            tvProfileMaintenanceFilterOverdue,
            selectedMaintenanceReminderFilter == MaintenanceReminderFilter.OVERDUE
        )
    }

    private fun updateMaintenanceReminderFilterTab(tabView: TextView, isSelected: Boolean) {
        tabView.isSelected = isSelected
        tabView.isActivated = isSelected
    }

    private fun setDocumentHistoryFilter(filter: DocumentHistoryFilter) {
        if (selectedDocumentHistoryFilter == filter) {
            return
        }

        selectedDocumentHistoryFilter = filter
        selectedDocumentEntryIds.clear()
        updateDocumentHistoryFilterUi()
        bindDocumentHistory(currentDocumentEntries)
        refreshHistoryActionChrome()
    }

    private fun updateDocumentHistoryFilterUi() {
        updateMaintenanceReminderFilterTab(
            tvProfileDocumentsFilterAll,
            selectedDocumentHistoryFilter == DocumentHistoryFilter.ALL
        )
        updateMaintenanceReminderFilterTab(
            tvProfileDocumentsFilterActive,
            selectedDocumentHistoryFilter == DocumentHistoryFilter.ACTIVE
        )
        updateMaintenanceReminderFilterTab(
            tvProfileDocumentsFilterOverdue,
            selectedDocumentHistoryFilter == DocumentHistoryFilter.OVERDUE
        )
    }

    private fun updateAddActionButton(isFuelTab: Boolean, isMaintenanceTab: Boolean, isDocumentsTab: Boolean) {
        if ((!isFuelTab && !isMaintenanceTab && !isDocumentsTab) || isSelectionModeActiveForCurrentTab()) {
            btnAddFuelEntry.visibility = View.GONE
            return
        }

        btnAddFuelEntry.visibility = View.VISIBLE
        if (isMaintenanceTab) {
            btnAddFuelEntry.setIconResource(R.drawable.ic_wrench)
            btnAddFuelEntry.contentDescription = getString(R.string.garage_profile_maintenance_add_button)
        } else if (isDocumentsTab) {
            btnAddFuelEntry.setIconResource(R.drawable.ic_tab_document)
            btnAddFuelEntry.contentDescription = getString(R.string.garage_profile_documents_add_button)
        } else {
            btnAddFuelEntry.setIconResource(R.drawable.gas_station)
            btnAddFuelEntry.contentDescription = getString(R.string.garage_profile_fuel_add_button)
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

        GarageMaintenanceReminderManager.evaluateDueRemindersForProfile(this, profile.id)
        GarageDocumentReminderManager.evaluateDueRemindersForProfile(this, profile.id)
        currentProfile = profile
        bindProfileHeader(profile)
        bindProfileSummary(
            profile,
            ProfileSessionSummaryStore.loadSummary(this, profile.id),
            GarageOdometerTimeline.latestAddedOdometer(this, profile.id)
        )

        val requestId = ++profileRefreshRequestId
        profileRefreshJob?.cancel()
        profileRefreshJob = lifecycleScope.launch {
            val profilePageData = withContext(Dispatchers.IO) {
                buildProfilePageData(profile)
            }

            if (requestId != profileRefreshRequestId || isFinishing || isDestroyed) {
                return@launch
            }

            bindProfile(profile, profilePageData)
        }
    }

    private fun bindProfileHeader(profile: Profile) {
        tvVehicleName.text = getGarageDisplayName(profile)

        val isActive = profile.id == ProfileStorage.getSelectedProfileId(this)
        if (isActive) {
            tvProfileStatus.text = getString(R.string.garage_active_badge)
            tvProfileStatus.setBackgroundResource(R.drawable.bg_profile_status_active)
        } else {
            tvProfileStatus.text = getString(R.string.garage_profile_status_inactive)
            tvProfileStatus.setBackgroundResource(R.drawable.bg_profile_status_inactive)
        }
    }

    private fun bindProfileSummary(
        profile: Profile,
        summary: ProfileSessionSummary,
        latestAddedOdometerKm: Long?
    ) {
        val typeText = when (profile.vehicleType) {
            Profile.VehicleType.CAR -> getString(R.string.garage_vehicle_car).uppercase(Locale.getDefault())
            Profile.VehicleType.MOTORCYCLE -> getString(R.string.garage_vehicle_motorcycle).uppercase(Locale.getDefault())
        }
        val odometerText = formatGarageMetaOdometer(latestAddedOdometerKm)
        tvVehicleMeta.text = getString(R.string.garage_profile_meta_format, typeText, odometerText)
        bindOverviewMetrics(summary.totalDistanceKm, summary.totalTimeMs)
        tvSessionsTotal.text = summary.totalSessions.toString()
        tvSessionsSplit.text = getString(
            R.string.garage_profile_sessions_split,
            summary.routeSessionCount,
            summary.trackSessionCount,
            summary.dragSessionCount
        )
    }

    private fun bindProfile(profile: Profile, profilePageData: ProfilePageData) {
        bindProfileHeader(profile)
        bindProfileSummary(profile, profilePageData.summary, profilePageData.latestAddedOdometerKm)

        currentFuelEntries = profilePageData.fuelEntries
        currentMaintenanceEntries = profilePageData.maintenanceEntries
        currentDocumentEntries = profilePageData.documentEntries
        pruneHistorySelections()
        bindOverviewPerformance(profile, profilePageData.overviewBestTimes)
        bindFuelData(profilePageData.fuelEntries)
        bindMaintenanceData(profilePageData.maintenanceEntries)
        bindDocumentData(profilePageData.documentEntries)
        tvFuelLogsCount.text = profilePageData.fuelEntries.size.toString()
        tvMaintenanceCount.text = profilePageData.maintenanceEntries.size.toString()
        tvDocumentsCount.text = profilePageData.documentEntries.size.toString()
        refreshHistoryActionChrome()
    }

    private fun buildProfilePageData(profile: Profile): ProfilePageData {
        ProfileSessionSummaryStore.ensureInitialized(this)
        val summary = ProfileSessionSummaryStore.loadSummary(this, profile.id)
        val profileRaces = RouteStorage.loadRaces(this).filter { it.profileId == profile.id }
        val profileDragSessions = DragStorage.loadDragSessions(this).filter { it.profileId == profile.id }
        val fuelEntries = GarageFuelEntryStorage.loadEntries(this, profile.id)
            .sortedByDescending { it.createdAt.takeIf { createdAt -> createdAt > 0L } ?: it.id }
        val maintenanceEntries = GarageMaintenanceEntryStorage.loadEntries(this, profile.id)
            .sortedByDescending { resolveGarageEntryTimestamp(it.date, it.createdAt) }
        val documentEntries = GarageDocumentEntryStorage.loadEntries(this, profile.id)
            .sortedByDescending { resolveGarageEntryTimestamp(it.date, it.createdAt) }
        val latestAddedOdometerKm = GarageOdometerTimeline.latestAddedOdometer(this, profile.id)
        val overviewBestTimes = computeOverviewBestTimes(profile.id, profileRaces, profileDragSessions)

        return ProfilePageData(
            summary = summary,
            latestAddedOdometerKm = latestAddedOdometerKm,
            fuelEntries = fuelEntries,
            maintenanceEntries = maintenanceEntries,
            documentEntries = documentEntries,
            overviewBestTimes = overviewBestTimes
        )
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
        tvProfileFuelLastPriceValue.text = lastPrice?.let(::formatPricePerLitre)
            ?: getString(R.string.garage_profile_fuel_last_price_placeholder)
        tvProfileFuelTotalLitresValue.text = formatTotalLitres(totalLitres)
        bindFuelHistory(entries)
    }

    private fun bindFuelHistory(entries: List<GarageFuelEntry>) {
        llProfileFuelEntriesPreview.removeAllViews()

        if (entries.isEmpty()) {
            llProfileFuelEntriesPreview.addView(
                createHistoryEmptyStateView(R.string.garage_profile_fuel_empty_state)
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

            itemView.findViewById<ImageView>(R.id.ivProfileHistoryEntryIcon).apply {
                setColorFilter(ContextCompat.getColor(this@GarageProfilePageActivity, R.color.accent_green))
            }
            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryDate).text = formatFuelEntryDate(entry)
            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryMeta).text = buildFuelEntryMeta(entry)
            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryAmount).text = formatCurrency(entry.totalAmount)
            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryStats).text = buildFuelEntryStats(entry)
            applyHistorySelectionState(itemView, entry.id in selectedFuelEntryIds)
            itemView.setOnLongClickListener {
                handleFuelHistoryLongPress(entry.id)
                true
            }
            itemView.setOnClickListener {
                if (selectedFuelEntryIds.isNotEmpty()) {
                    toggleFuelHistorySelection(entry.id)
                } else {
                    startActivity(Intent(this, GarageFuelEntryActivity::class.java).apply {
                        putExtra(GarageFuelEntryActivity.EXTRA_PROFILE_ID, profileId)
                        putExtra(GarageFuelEntryActivity.EXTRA_ENTRY_ID, entry.id)
                    })
                }
            }

            llProfileFuelEntriesPreview.addView(itemView)
        }
    }

    private fun bindMaintenanceData(entries: List<GarageMaintenanceEntry>) {
        updateMaintenanceReminderFilterUi()

        if (entries.isEmpty()) {
            tvProfileMaintenanceTotalSpentValue.text = getString(R.string.garage_profile_maintenance_spend_placeholder)
            tvProfileMaintenanceYearSpentValue.text = getString(R.string.garage_profile_maintenance_spend_placeholder)
            tvProfileMaintenancePartsSpentValue.text = getString(R.string.garage_profile_maintenance_spend_placeholder)
            tvProfileMaintenanceLaborSpentValue.text = getString(R.string.garage_profile_maintenance_spend_placeholder)
            bindMaintenanceHistory(entries)
            return
        }

        val totalSpent = entries.sumOf(::calculateMaintenanceEntryTotal)
        val partsSpent = entries.sumOf { it.partsCost.coerceAtLeast(0.0) }
        val laborSpent = entries.sumOf { it.laborCost.coerceAtLeast(0.0) }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val yearSpent = entries
            .filter { resolveGarageEntryYear(it.date, it.createdAt) == currentYear }
            .sumOf(::calculateMaintenanceEntryTotal)

        tvProfileMaintenanceTotalSpentValue.text = formatCurrency(totalSpent)
        tvProfileMaintenanceYearSpentValue.text = formatCurrency(yearSpent)
        tvProfileMaintenancePartsSpentValue.text = formatCurrency(partsSpent)
        tvProfileMaintenanceLaborSpentValue.text = formatCurrency(laborSpent)
        bindMaintenanceHistory(entries)
    }

    private fun bindDocumentData(entries: List<GarageDocumentEntry>) {
        updateDocumentHistoryFilterUi()

        if (entries.isEmpty()) {
            tvProfileDocumentsExpiringSoonValue.text = getString(R.string.garage_profile_documents_spend_placeholder)
            tvProfileDocumentsExpiredValue.text = getString(R.string.garage_profile_documents_spend_placeholder)
            bindDocumentHistory(entries)
            return
        }

        val totalSpent = entries.sumOf { it.amount.coerceAtLeast(0.0) }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val yearSpent = entries
            .filter { resolveGarageEntryYear(it.date, it.createdAt) == currentYear }
            .sumOf { it.amount.coerceAtLeast(0.0) }

        tvProfileDocumentsExpiringSoonValue.text = formatCurrency(totalSpent)
        tvProfileDocumentsExpiredValue.text = formatCurrency(yearSpent)

        bindDocumentHistory(entries)
    }

    private fun bindDocumentHistory(entries: List<GarageDocumentEntry>) {
        llProfileDocumentEntriesPreview.removeAllViews()
        val nowMillis = System.currentTimeMillis()
        val visibleItems = resolveVisibleDocumentHistoryItems(entries, nowMillis)
        val selectionUpdated = selectedDocumentEntryIds.retainAll(visibleItems.mapTo(mutableSetOf()) { it.entry.id })

        if (visibleItems.isEmpty()) {
            llProfileDocumentEntriesPreview.addView(
                createHistoryEmptyStateView(resolveDocumentHistoryEmptyState(entries))
            )
            if (selectionUpdated) {
                refreshHistoryActionChrome()
            }
            return
        }

        visibleItems.forEachIndexed { index, historyItem ->
            val entry = historyItem.entry
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_profile_fuel_entry, llProfileDocumentEntriesPreview, false)
            val presentation = historyItem.presentation

            itemView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (index == 0) 0 else dpToPx(8)
            }

            itemView.findViewById<ImageView>(R.id.ivProfileHistoryEntryIcon).apply {
                setImageResource(
                    GarageDocumentTypeIcons.resolveIconRes(entry.documentType) ?: R.drawable.ic_tab_document
                )
                setColorFilter(ContextCompat.getColor(this@GarageProfilePageActivity, R.color.accent_purple))
            }
            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryDate).text = formatDocumentEntryDate(entry)
            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryMeta).text = buildDocumentEntryMeta(entry)
            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryAmount).apply {
                text = formatCurrency(entry.amount.coerceAtLeast(0.0))
                setTextColor(ContextCompat.getColor(this@GarageProfilePageActivity, R.color.drag_run_green))
            }
            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryStats).text = buildDocumentEntryStats(presentation)
            bindDocumentReminderProgress(itemView, presentation)
            applyHistorySelectionState(itemView, entry.id in selectedDocumentEntryIds)
            itemView.setOnLongClickListener {
                handleDocumentHistoryLongPress(entry.id)
                true
            }
            itemView.setOnClickListener {
                if (selectedDocumentEntryIds.isNotEmpty()) {
                    toggleDocumentHistorySelection(entry.id)
                } else {
                    startActivity(GarageDocumentEntryActivity.createIntent(this, profileId, entry.id))
                }
            }

            llProfileDocumentEntriesPreview.addView(itemView)
        }

        if (selectionUpdated) {
            refreshHistoryActionChrome()
        }
    }

    private fun resolveVisibleDocumentHistoryItems(
        entries: List<GarageDocumentEntry>,
        nowMillis: Long
    ): List<DocumentHistoryItem> {
        val allItems = entries.map { entry ->
            DocumentHistoryItem(entry, resolveDocumentExpiryPresentation(entry, nowMillis))
        }

        return when (selectedDocumentHistoryFilter) {
            DocumentHistoryFilter.ALL -> allItems
            DocumentHistoryFilter.ACTIVE -> allItems
                .filter {
                    it.presentation.status == DocumentHistoryStatus.DUE ||
                        it.presentation.status == DocumentHistoryStatus.UPCOMING
                }
                .sortedWith(
                    compareBy<DocumentHistoryItem> { it.presentation.status.sortOrder }
                        .thenByDescending { resolveGarageEntryTimestamp(it.entry.date, it.entry.createdAt) }
                )

            DocumentHistoryFilter.OVERDUE -> allItems
                .filter { it.presentation.status == DocumentHistoryStatus.OVERDUE }
                .sortedByDescending { resolveGarageEntryTimestamp(it.entry.date, it.entry.createdAt) }
        }
    }

    private fun resolveDocumentHistoryEmptyState(entries: List<GarageDocumentEntry>): Int {
        if (entries.isEmpty()) {
            return R.string.garage_profile_documents_empty_state
        }

        return when (selectedDocumentHistoryFilter) {
            DocumentHistoryFilter.ALL -> R.string.garage_profile_documents_empty_state
            DocumentHistoryFilter.ACTIVE -> R.string.garage_profile_documents_active_empty_state
            DocumentHistoryFilter.OVERDUE -> R.string.garage_profile_documents_overdue_empty_state
        }
    }

    private fun bindMaintenanceHistory(entries: List<GarageMaintenanceEntry>) {
        llProfileMaintenanceEntriesPreview.removeAllViews()
        val nowMillis = System.currentTimeMillis()
        val visibleItems = resolveVisibleMaintenanceHistoryItems(entries, nowMillis)
        val selectionUpdated = selectedMaintenanceEntryIds.retainAll(
            visibleItems.mapTo(mutableSetOf()) { it.entry.id }
        )

        if (visibleItems.isEmpty()) {
            llProfileMaintenanceEntriesPreview.addView(
                createHistoryEmptyStateView(resolveMaintenanceHistoryEmptyState(entries))
            )
            if (selectionUpdated) {
                refreshHistoryActionChrome()
            }
            return
        }

        visibleItems.forEachIndexed { index, historyItem ->
            val entry = historyItem.entry
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_profile_fuel_entry, llProfileMaintenanceEntriesPreview, false)

            itemView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (index == 0) 0 else dpToPx(8)
            }

            itemView.findViewById<ImageView>(R.id.ivProfileHistoryEntryIcon).apply {
                setImageResource(GarageMaintenanceServiceIcons.resolveIconRes(entry.serviceType) ?: R.drawable.ic_wrench)
                setColorFilter(ContextCompat.getColor(this@GarageProfilePageActivity, R.color.accent_color))
            }
            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryDate).text = formatMaintenanceEntryDate(entry)
            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryMeta).text = buildMaintenanceEntryMeta(entry)
            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryAmount).text = formatCurrency(calculateMaintenanceEntryTotal(entry))
            itemView.findViewById<TextView>(R.id.tvFuelHistoryEntryStats).text = buildMaintenanceEntryStats(entry)
            bindMaintenanceReminderProgress(itemView, historyItem.presentation)
            applyHistorySelectionState(itemView, entry.id in selectedMaintenanceEntryIds)
            itemView.setOnLongClickListener {
                handleMaintenanceHistoryLongPress(entry.id)
                true
            }
            itemView.setOnClickListener {
                if (selectedMaintenanceEntryIds.isNotEmpty()) {
                    toggleMaintenanceHistorySelection(entry.id)
                } else {
                    startActivity(GarageMaintenanceEntryActivity.createIntent(this, profileId, entry.id))
                }
            }

            llProfileMaintenanceEntriesPreview.addView(itemView)
        }

        if (selectionUpdated) {
            refreshHistoryActionChrome()
        }
    }

    private fun resolveVisibleMaintenanceHistoryItems(
        entries: List<GarageMaintenanceEntry>,
        nowMillis: Long
    ): List<MaintenanceHistoryItem> {
        val allItems = entries.map { entry ->
            MaintenanceHistoryItem(entry, resolveMaintenanceReminderPresentation(entry, nowMillis))
        }

        return when (selectedMaintenanceReminderFilter) {
            MaintenanceReminderFilter.ALL -> allItems
            MaintenanceReminderFilter.ACTIVE -> allItems
                .filter {
                    it.presentation?.status == MaintenanceReminderStatus.DUE ||
                        it.presentation?.status == MaintenanceReminderStatus.UPCOMING
                }
                .sortedWith(
                    compareBy<MaintenanceHistoryItem> { it.presentation?.status?.sortOrder ?: Int.MAX_VALUE }
                        .thenByDescending { it.presentation?.progressPercent ?: -1 }
                        .thenByDescending { resolveGarageEntryTimestamp(it.entry.date, it.entry.createdAt) }
                )

            MaintenanceReminderFilter.OVERDUE -> allItems
                .filter { it.presentation?.status == MaintenanceReminderStatus.OVERDUE }
                .sortedByDescending { resolveGarageEntryTimestamp(it.entry.date, it.entry.createdAt) }
        }
    }

    private fun resolveMaintenanceHistoryEmptyState(entries: List<GarageMaintenanceEntry>): Int {
        if (entries.isEmpty()) {
            return R.string.garage_profile_maintenance_empty_state
        }

        return when (selectedMaintenanceReminderFilter) {
            MaintenanceReminderFilter.ALL -> R.string.garage_profile_maintenance_empty_state
            MaintenanceReminderFilter.ACTIVE -> R.string.garage_profile_maintenance_active_empty_state
            MaintenanceReminderFilter.OVERDUE -> R.string.garage_profile_maintenance_overdue_empty_state
        }
    }

    private fun applyHistorySelectionState(itemView: View, isSelected: Boolean) {
        itemView.findViewById<View>(R.id.llProfileHistoryCardContent).foreground =
            if (isSelected) {
                ContextCompat.getDrawable(itemView.context, R.drawable.bg_history_selection_overlay)
            } else {
                null
            }
        itemView.findViewById<View>(R.id.flProfileHistorySelectedBadge).visibility =
            if (isSelected) View.VISIBLE else View.GONE
    }

    private fun handleFuelHistoryLongPress(entryId: Long) {
        if (selectedFuelEntryIds.isEmpty()) {
            clearMaintenanceSelectionMode(rebindHistory = true)
            clearDocumentSelectionMode(rebindHistory = true)
        }
        toggleFuelHistorySelection(entryId, forceSelect = selectedFuelEntryIds.isEmpty())
    }

    private fun handleMaintenanceHistoryLongPress(entryId: Long) {
        if (selectedMaintenanceEntryIds.isEmpty()) {
            clearFuelSelectionMode(rebindHistory = true)
            clearDocumentSelectionMode(rebindHistory = true)
        }
        toggleMaintenanceHistorySelection(entryId, forceSelect = selectedMaintenanceEntryIds.isEmpty())
    }

    private fun handleDocumentHistoryLongPress(entryId: Long) {
        if (selectedDocumentEntryIds.isEmpty()) {
            clearFuelSelectionMode(rebindHistory = true)
            clearMaintenanceSelectionMode(rebindHistory = true)
        }
        toggleDocumentHistorySelection(entryId, forceSelect = selectedDocumentEntryIds.isEmpty())
    }

    private fun toggleFuelHistorySelection(entryId: Long, forceSelect: Boolean = false) {
        val shouldSelect = forceSelect || entryId !in selectedFuelEntryIds
        if (shouldSelect) {
            selectedFuelEntryIds.add(entryId)
        } else {
            selectedFuelEntryIds.remove(entryId)
        }

        if (selectedFuelEntryIds.isEmpty()) {
            clearFuelSelectionMode(rebindHistory = true)
            return
        }

        bindFuelHistory(currentFuelEntries)
        refreshHistoryActionChrome()
    }

    private fun toggleMaintenanceHistorySelection(entryId: Long, forceSelect: Boolean = false) {
        val shouldSelect = forceSelect || entryId !in selectedMaintenanceEntryIds
        if (shouldSelect) {
            selectedMaintenanceEntryIds.add(entryId)
        } else {
            selectedMaintenanceEntryIds.remove(entryId)
        }

        if (selectedMaintenanceEntryIds.isEmpty()) {
            clearMaintenanceSelectionMode(rebindHistory = true)
            return
        }

        bindMaintenanceHistory(currentMaintenanceEntries)
        refreshHistoryActionChrome()
    }

    private fun toggleDocumentHistorySelection(entryId: Long, forceSelect: Boolean = false) {
        val shouldSelect = forceSelect || entryId !in selectedDocumentEntryIds
        if (shouldSelect) {
            selectedDocumentEntryIds.add(entryId)
        } else {
            selectedDocumentEntryIds.remove(entryId)
        }

        if (selectedDocumentEntryIds.isEmpty()) {
            clearDocumentSelectionMode(rebindHistory = true)
            return
        }

        bindDocumentHistory(currentDocumentEntries)
        refreshHistoryActionChrome()
    }

    private fun clearFuelSelectionMode(rebindHistory: Boolean = false) {
        if (selectedFuelEntryIds.isEmpty()) {
            if (rebindHistory) {
                bindFuelHistory(currentFuelEntries)
            }
            refreshHistoryActionChrome()
            return
        }

        selectedFuelEntryIds.clear()
        if (rebindHistory) {
            bindFuelHistory(currentFuelEntries)
        }
        refreshHistoryActionChrome()
    }

    private fun clearMaintenanceSelectionMode(rebindHistory: Boolean = false) {
        if (selectedMaintenanceEntryIds.isEmpty()) {
            if (rebindHistory) {
                bindMaintenanceHistory(currentMaintenanceEntries)
            }
            refreshHistoryActionChrome()
            return
        }

        selectedMaintenanceEntryIds.clear()
        if (rebindHistory) {
            bindMaintenanceHistory(currentMaintenanceEntries)
        }
        refreshHistoryActionChrome()
    }

    private fun clearDocumentSelectionMode(rebindHistory: Boolean = false) {
        if (selectedDocumentEntryIds.isEmpty()) {
            if (rebindHistory) {
                bindDocumentHistory(currentDocumentEntries)
            }
            refreshHistoryActionChrome()
            return
        }

        selectedDocumentEntryIds.clear()
        if (rebindHistory) {
            bindDocumentHistory(currentDocumentEntries)
        }
        refreshHistoryActionChrome()
    }

    private fun clearAllHistorySelections() {
        val hadFuelSelection = selectedFuelEntryIds.isNotEmpty()
        val hadMaintenanceSelection = selectedMaintenanceEntryIds.isNotEmpty()
        val hadDocumentSelection = selectedDocumentEntryIds.isNotEmpty()
        selectedFuelEntryIds.clear()
        selectedMaintenanceEntryIds.clear()
        selectedDocumentEntryIds.clear()
        if (hadFuelSelection) {
            bindFuelHistory(currentFuelEntries)
        }
        if (hadMaintenanceSelection) {
            bindMaintenanceHistory(currentMaintenanceEntries)
        }
        if (hadDocumentSelection) {
            bindDocumentHistory(currentDocumentEntries)
        }
        refreshHistoryActionChrome()
    }

    private fun clearActiveHistorySelectionModeIfNeeded(): Boolean {
        val hasSelection = selectedFuelEntryIds.isNotEmpty() ||
            selectedMaintenanceEntryIds.isNotEmpty() ||
            selectedDocumentEntryIds.isNotEmpty()
        if (!hasSelection) {
            return false
        }

        clearAllHistorySelections()
        return true
    }

    private fun clearSelectionsForInactiveTabs(activeTabPosition: Int) {
        var shouldRebindFuel = false
        var shouldRebindMaintenance = false
        var shouldRebindDocuments = false

        if (activeTabPosition != 1 && selectedFuelEntryIds.isNotEmpty()) {
            selectedFuelEntryIds.clear()
            shouldRebindFuel = true
        }
        if (activeTabPosition != 2 && selectedMaintenanceEntryIds.isNotEmpty()) {
            selectedMaintenanceEntryIds.clear()
            shouldRebindMaintenance = true
        }
        if (activeTabPosition != 3 && selectedDocumentEntryIds.isNotEmpty()) {
            selectedDocumentEntryIds.clear()
            shouldRebindDocuments = true
        }

        if (shouldRebindFuel) {
            bindFuelHistory(currentFuelEntries)
        }
        if (shouldRebindMaintenance) {
            bindMaintenanceHistory(currentMaintenanceEntries)
        }
        if (shouldRebindDocuments) {
            bindDocumentHistory(currentDocumentEntries)
        }
    }

    private fun pruneHistorySelections() {
        selectedFuelEntryIds.retainAll(currentFuelEntries.mapTo(mutableSetOf()) { it.id })
        selectedMaintenanceEntryIds.retainAll(currentMaintenanceEntries.mapTo(mutableSetOf()) { it.id })
        selectedDocumentEntryIds.retainAll(currentDocumentEntries.mapTo(mutableSetOf()) { it.id })
    }

    private fun refreshHistoryActionChrome() {
        btnDeleteFuelHistorySelection.visibility = if (selectedFuelEntryIds.isNotEmpty()) View.VISIBLE else View.GONE
        btnDeleteMaintenanceHistorySelection.visibility = if (selectedMaintenanceEntryIds.isNotEmpty()) View.VISIBLE else View.GONE
        btnDeleteDocumentHistorySelection.visibility = if (selectedDocumentEntryIds.isNotEmpty()) View.VISIBLE else View.GONE
        updateAddActionButton(selectedTabPosition == 1, selectedTabPosition == 2, selectedTabPosition == 3)
    }

    private fun isSelectionModeActiveForCurrentTab(): Boolean {
        return when (selectedTabPosition) {
            1 -> selectedFuelEntryIds.isNotEmpty()
            2 -> selectedMaintenanceEntryIds.isNotEmpty()
            3 -> selectedDocumentEntryIds.isNotEmpty()
            else -> false
        }
    }

    private fun confirmDeleteSelectedFuelEntries() {
        val entryIds = selectedFuelEntryIds.toSet()
        if (entryIds.isEmpty()) {
            return
        }

        showDeleteConfirmationDialog(
            titleResId = R.string.garage_profile_history_delete_fuel_title,
            message = getString(R.string.garage_profile_history_delete_fuel_message, entryIds.size)
        ) {
            deleteSelectedFuelEntries(entryIds)
        }
    }

    private fun confirmDeleteSelectedMaintenanceEntries() {
        val entryIds = selectedMaintenanceEntryIds.toSet()
        if (entryIds.isEmpty()) {
            return
        }

        showDeleteConfirmationDialog(
            titleResId = R.string.garage_profile_history_delete_maintenance_title,
            message = getString(R.string.garage_profile_history_delete_maintenance_message, entryIds.size)
        ) {
            deleteSelectedMaintenanceEntries(entryIds)
        }
    }

    private fun confirmDeleteSelectedDocumentEntries() {
        val entryIds = selectedDocumentEntryIds.toSet()
        if (entryIds.isEmpty()) {
            return
        }

        showDeleteConfirmationDialog(
            titleResId = R.string.garage_profile_history_delete_documents_title,
            message = getString(R.string.garage_profile_history_delete_documents_message, entryIds.size)
        ) {
            deleteSelectedDocumentEntries(entryIds)
        }
    }

    private fun showDeleteConfirmationDialog(
        titleResId: Int,
        message: String,
        onConfirm: () -> Unit
    ) {
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(titleResId)
            .setMessage(message)
            .setPositiveButton(R.string.garage_delete_button) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.garage_cancel_button, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setTextColor(ContextCompat.getColor(this, R.color.white))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                ?.setTextColor(ContextCompat.getColor(this, R.color.white))
        }

        dialog.show()
    }

    private fun deleteSelectedFuelEntries(entryIds: Set<Long>) {
        val removedEntries = GarageFuelEntryStorage.removeEntries(this, profileId, entryIds)
        if (removedEntries.isEmpty()) {
            clearFuelSelectionMode(rebindHistory = true)
            return
        }

        removedEntries.forEach { entry ->
            GarageFuelReceiptStorage.deleteReceipt(this, entry.receiptImagePath)
        }

        selectedFuelEntryIds.clear()
        syncFuelLogCount()
        refreshProfileUi()
        Toast.makeText(
            this,
            getString(R.string.garage_profile_history_delete_fuel_success, removedEntries.size),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun deleteSelectedMaintenanceEntries(entryIds: Set<Long>) {
        val removedEntries = GarageMaintenanceEntryStorage.removeEntries(this, profileId, entryIds)
        if (removedEntries.isEmpty()) {
            clearMaintenanceSelectionMode(rebindHistory = true)
            return
        }

        removedEntries.forEach { entry ->
            GarageMaintenanceReminderManager.cancelReminder(this, entry)
            GarageMaintenanceReceiptStorage.deleteReceipt(this, entry.receiptImagePath)
        }

        selectedMaintenanceEntryIds.clear()
        syncMaintenanceCount()
        refreshProfileUi()
        Toast.makeText(
            this,
            getString(R.string.garage_profile_history_delete_maintenance_success, removedEntries.size),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun deleteSelectedDocumentEntries(entryIds: Set<Long>) {
        val removedEntries = GarageDocumentEntryStorage.removeEntries(this, profileId, entryIds)
        if (removedEntries.isEmpty()) {
            clearDocumentSelectionMode(rebindHistory = true)
            return
        }

        removedEntries.forEach { entry ->
            GarageDocumentReminderManager.cancelReminder(this, entry)
            GarageDocumentReceiptStorage.deleteReceipt(this, entry.imagePath)
        }

        selectedDocumentEntryIds.clear()
        syncDocumentCount()
        refreshProfileUi()
        Toast.makeText(
            this,
            getString(R.string.garage_profile_history_delete_documents_success, removedEntries.size),
            Toast.LENGTH_SHORT
        ).show()
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
        return formatGarageEntryDate(entry.date, entry.createdAt)
    }

    private fun formatMaintenanceEntryDate(entry: GarageMaintenanceEntry): String {
        return formatGarageEntryDate(entry.date, entry.createdAt)
    }

    private fun formatDocumentEntryDate(entry: GarageDocumentEntry): String {
        val displayDate = entry.date.trim().ifBlank {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(
                Date(entry.createdAt.takeIf { createdAt -> createdAt > 0L } ?: System.currentTimeMillis())
            )
        }
        return displayDate.uppercase(Locale.getDefault())
    }

    private fun formatGarageEntryDate(rawDate: String, createdAt: Long): String {
        val displayDate = rawDate.trim().ifBlank {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(
                Date(createdAt.takeIf { it > 0L } ?: System.currentTimeMillis())
            )
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
            formatPricePerLitre(entry.pricePerLitre)
        ).joinToString(" • ")
    }

    private fun buildMaintenanceEntryMeta(entry: GarageMaintenanceEntry): String {
        val parts = mutableListOf<String>()
        if (entry.serviceType.isNotBlank()) {
            parts += entry.serviceType
        }
        if (entry.odometerKm > 0L) {
            parts += formatOdometer(entry.odometerKm)
        }
        return parts.joinToString(" • ")
    }

    private fun buildMaintenanceEntryStats(entry: GarageMaintenanceEntry): String {
        return listOf(
            "${getString(R.string.garage_maintenance_entry_parts_cost)} ${formatCurrency(entry.partsCost.coerceAtLeast(0.0))}",
            "${getString(R.string.garage_maintenance_entry_labor_cost)} ${formatCurrency(entry.laborCost.coerceAtLeast(0.0))}"
        ).joinToString(" • ")
    }

    private fun buildDocumentEntryMeta(entry: GarageDocumentEntry): String {
        val parts = mutableListOf(
            entry.documentType.trim().ifBlank {
                getString(R.string.garage_document_entry_title)
            }
        )
        if (entry.odometerKm > 0L) {
            parts += formatOdometer(entry.odometerKm)
        }
        return parts.joinToString(" • ")
    }

    private fun buildDocumentEntryStats(presentation: DocumentExpiryPresentation): String {
        return presentation.secondaryText
    }

    private fun bindDocumentReminderProgress(
        itemView: View,
        presentation: DocumentExpiryPresentation
    ) {
        val reminderRow = itemView.findViewById<LinearLayout>(R.id.llProfileHistoryReminderRow)
        val reminderBadge = itemView.findViewById<TextView>(R.id.tvProfileHistoryReminderBadge)
        val reminderPercent = itemView.findViewById<TextView>(R.id.tvProfileHistoryReminderPercent)
        val reminderProgress = itemView.findViewById<ProgressBar>(R.id.progressProfileHistoryReminder)

        if (!presentation.showReminderRow) {
            reminderRow.visibility = View.GONE
            return
        }

        reminderRow.visibility = View.VISIBLE
        reminderBadge.text = presentation.badgeLabel
        reminderBadge.setBackgroundResource(presentation.badgeBackgroundRes)
        reminderBadge.setTextColor(ContextCompat.getColor(this, presentation.badgeTextColorRes))
        reminderPercent.text = presentation.compactStatusText
        reminderPercent.setTextColor(ContextCompat.getColor(this, presentation.compactTextColorRes))
        reminderPercent.visibility = if (presentation.compactStatusText.isBlank()) View.GONE else View.VISIBLE

        if (presentation.progressPercent != null) {
            reminderProgress.visibility = View.VISIBLE
            reminderProgress.progress = presentation.progressPercent
            reminderProgress.progressTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, presentation.progressBarColorRes)
            )
        } else {
            reminderProgress.visibility = View.GONE
        }
    }

    private fun resolveDocumentExpiryPresentation(
        entry: GarageDocumentEntry,
        nowMillis: Long
    ): DocumentExpiryPresentation {
        val referenceTimestamp = resolveGarageEntryTimestamp(entry.date, entry.createdAt)
        val reminderTargetDateMillis = GarageDocumentReminderRules.resolveTargetDate(entry, referenceTimestamp)
        val reminderDateMillis = GarageDocumentReminderRules.resolveReminderDate(entry, referenceTimestamp)
        val statusDateMillis = reminderTargetDateMillis

        if (entry.reminderCompletedAt != null) {
            return DocumentExpiryPresentation(
                status = DocumentHistoryStatus.COMPLETED,
                secondaryText = getString(R.string.garage_profile_document_target_reached),
                showReminderRow = true,
                badgeLabel = getString(R.string.garage_profile_document_status_completed),
                badgeBackgroundRes = R.drawable.bg_garage_reminder_completed_badge,
                badgeTextColorRes = R.color.white,
                compactStatusText = "",
                compactTextColorRes = R.color.success_color,
                progressPercent = null,
                progressBarColorRes = R.color.success_color
            )
        }

        if (statusDateMillis == null) {
            return DocumentExpiryPresentation(
                status = DocumentHistoryStatus.NO_REMINDER,
                secondaryText = getString(R.string.garage_profile_document_no_expiry_meta),
                showReminderRow = false,
                badgeLabel = getString(R.string.garage_profile_maintenance_reminder_badge),
                badgeBackgroundRes = R.drawable.bg_garage_reminder_badge,
                badgeTextColorRes = R.color.accent_color,
                compactStatusText = "",
                compactTextColorRes = R.color.text_tertiary,
                progressPercent = null,
                progressBarColorRes = R.color.accent_color
            )
        }

        val reminderIsRelevant = reminderDateMillis != null && reminderDateMillis < statusDateMillis
        val isOverdue = nowMillis >= statusDateMillis
        val isDue = !isOverdue &&
            (entry.reminderTriggeredAt != null || (reminderDateMillis != null && nowMillis >= reminderDateMillis))

        if (isOverdue) {
            val overdueDays = ((nowMillis - statusDateMillis) / MILLIS_PER_DAY).toInt().coerceAtLeast(0)
            return DocumentExpiryPresentation(
                status = DocumentHistoryStatus.OVERDUE,
                secondaryText = getString(
                    R.string.garage_profile_document_target_on,
                    formatDocumentShortDate(statusDateMillis)
                ),
                showReminderRow = true,
                badgeLabel = getString(R.string.garage_profile_document_status_expired),
                badgeBackgroundRes = R.drawable.bg_garage_reminder_overdue_badge,
                badgeTextColorRes = R.color.white,
                compactStatusText = if (overdueDays == 0) {
                    getString(R.string.garage_profile_document_due_today)
                } else {
                    getString(R.string.garage_profile_document_days_overdue, overdueDays)
                },
                compactTextColorRes = R.color.error_color,
                progressPercent = null,
                progressBarColorRes = R.color.error_color
            )
        }

        if (isDue) {
            return DocumentExpiryPresentation(
                status = DocumentHistoryStatus.DUE,
                secondaryText = getString(
                    R.string.garage_profile_document_target_on,
                    formatDocumentShortDate(statusDateMillis)
                ),
                showReminderRow = true,
                badgeLabel = getString(R.string.garage_profile_document_status_due),
                badgeBackgroundRes = R.drawable.bg_garage_reminder_due_badge,
                badgeTextColorRes = R.color.white,
                compactStatusText = getString(R.string.garage_profile_document_history_due_now),
                compactTextColorRes = R.color.accent_color,
                progressPercent = null,
                progressBarColorRes = R.color.accent_color
            )
        }

        val progressPercent = when {
            reminderDateMillis != null && reminderDateMillis > referenceTimestamp -> {
                (((nowMillis - referenceTimestamp).toDouble() / (reminderDateMillis - referenceTimestamp).toDouble()) * 100.0)
                    .roundToInt()
                    .coerceIn(0, 100)
            }
            else -> null
        }
        val compactText = if (progressPercent != null) {
            getString(R.string.garage_profile_maintenance_progress_percent, progressPercent)
        } else {
            ""
        }

        return DocumentExpiryPresentation(
            status = DocumentHistoryStatus.UPCOMING,
            secondaryText = when {
                reminderIsRelevant && reminderDateMillis < statusDateMillis -> getString(
                    R.string.garage_profile_document_reminder_on,
                    formatDocumentShortDate(reminderDateMillis)
                )
                else -> getString(
                    R.string.garage_profile_document_target_on,
                    formatDocumentShortDate(statusDateMillis)
                )
            },
            showReminderRow = true,
            badgeLabel = getString(R.string.garage_profile_maintenance_reminder_badge),
            badgeBackgroundRes = R.drawable.bg_garage_reminder_badge,
            badgeTextColorRes = R.color.accent_color,
            compactStatusText = compactText,
            compactTextColorRes = R.color.text_tertiary,
            progressPercent = progressPercent,
            progressBarColorRes = R.color.accent_purple
        )
    }

    private fun bindMaintenanceReminderProgress(
        itemView: View,
        presentation: MaintenanceReminderPresentation?
    ) {
        val reminderRow = itemView.findViewById<LinearLayout>(R.id.llProfileHistoryReminderRow)
        val reminderBadge = itemView.findViewById<TextView>(R.id.tvProfileHistoryReminderBadge)
        val reminderPercent = itemView.findViewById<TextView>(R.id.tvProfileHistoryReminderPercent)
        val reminderProgress = itemView.findViewById<ProgressBar>(R.id.progressProfileHistoryReminder)

        if (presentation == null) {
            reminderRow.visibility = View.GONE
            return
        }

        reminderRow.visibility = View.VISIBLE
        reminderBadge.text = presentation.badgeLabel
        reminderBadge.setBackgroundResource(presentation.badgeBackgroundRes)
        reminderBadge.setTextColor(ContextCompat.getColor(this, presentation.badgeTextColorRes))
        reminderPercent.text = presentation.compactStatusText
        reminderPercent.setTextColor(ContextCompat.getColor(this, presentation.compactTextColorRes))

            if (presentation.compactStatusText.isBlank()) {
                reminderPercent.visibility = View.GONE
            } else {
                reminderPercent.visibility = View.VISIBLE
            }

        if (presentation.progressPercent != null) {
            reminderProgress.visibility = View.VISIBLE
            reminderProgress.progress = presentation.progressPercent
            reminderProgress.progressTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, presentation.progressBarColorRes)
            )
        } else {
            reminderProgress.visibility = View.GONE
        }
    }

    private fun resolveMaintenanceReminderPresentation(
        entry: GarageMaintenanceEntry,
        nowMillis: Long
    ): MaintenanceReminderPresentation? {
        if (!entry.reminderEnabled) {
            return null
        }

        if (entry.reminderCompletedAt != null) {
            return MaintenanceReminderPresentation(
                entry = entry,
                status = MaintenanceReminderStatus.COMPLETED,
                badgeLabel = getString(R.string.garage_profile_maintenance_status_completed),
                badgeBackgroundRes = R.drawable.bg_garage_reminder_completed_badge,
                badgeTextColorRes = R.color.white,
                primaryText = getString(R.string.garage_profile_maintenance_status_completed),
                secondaryText = getString(R.string.garage_profile_maintenance_target_reached),
                primaryTextColorRes = R.color.success_color,
                compactStatusText = "",
                compactTextColorRes = R.color.success_color,
                progressPercent = null,
                progressBarColorRes = R.color.success_color
            )
        }

        val serviceTimestamp = resolveGarageEntryTimestamp(entry.date, entry.createdAt)
        val reminderKm = GarageMaintenanceReminderRules.resolveKmReminder(entry)
        val targetKm = GarageMaintenanceReminderRules.resolveKmTarget(entry)
        val reminderDateMillis = GarageMaintenanceReminderRules.resolveDateReminder(entry, serviceTimestamp)
        val targetDateMillis = GarageMaintenanceReminderRules.resolveDateTarget(entry, serviceTimestamp)
        val reminderKmReachedAt = reminderKm?.let {
            GarageOdometerTimeline.firstReachedTargetTimestampAfter(
                context = this,
                profileId = entry.profileId,
                source = GarageOdometerSource.MAINTENANCE,
                entryId = entry.id,
                targetOdometerKm = it,
                dateText = entry.date,
                fallbackTimestamp = entry.createdAt
            )
        }
        val targetKmReachedAt = targetKm?.let {
            GarageOdometerTimeline.firstReachedTargetTimestampAfter(
                context = this,
                profileId = entry.profileId,
                source = GarageOdometerSource.MAINTENANCE,
                entryId = entry.id,
                targetOdometerKm = it,
                dateText = entry.date,
                fallbackTimestamp = entry.createdAt
            )
        }
        val latestRecordedOdometerKm = GarageOdometerTimeline.latestRecordedOdometerFrom(
            context = this,
            profileId = entry.profileId,
            source = GarageOdometerSource.MAINTENANCE,
            entryId = entry.id,
            dateText = entry.date,
            fallbackTimestamp = entry.createdAt
        )

        val isOverdueByKm = targetKmReachedAt != null
        val isOverdueByDate = targetDateMillis != null && nowMillis >= targetDateMillis
        val isDueByKm = reminderKmReachedAt != null
        val isDueByDate = reminderDateMillis != null && nowMillis >= reminderDateMillis

        return when {
            isOverdueByKm || isOverdueByDate -> {
                val (primaryText, compactText) = buildMaintenanceOverdueTexts(
                    targetKm = targetKm,
                    targetDateMillis = targetDateMillis,
                    latestRecordedOdometerKm = latestRecordedOdometerKm,
                    targetKmReachedAt = targetKmReachedAt,
                    nowMillis = nowMillis
                )
                MaintenanceReminderPresentation(
                    entry = entry,
                    status = MaintenanceReminderStatus.OVERDUE,
                    badgeLabel = getString(R.string.garage_profile_maintenance_status_overdue),
                    badgeBackgroundRes = R.drawable.bg_garage_reminder_overdue_badge,
                    badgeTextColorRes = R.color.white,
                    primaryText = primaryText,
                    secondaryText = buildMaintenanceTargetSummary(targetKm, targetDateMillis),
                    primaryTextColorRes = R.color.error_color,
                    compactStatusText = compactText,
                    compactTextColorRes = R.color.error_color,
                    progressPercent = null,
                    progressBarColorRes = R.color.error_color
                )
            }

            isDueByKm || isDueByDate || entry.reminderTriggeredAt != null -> MaintenanceReminderPresentation(
                entry = entry,
                status = MaintenanceReminderStatus.DUE,
                badgeLabel = getString(R.string.garage_profile_maintenance_status_due),
                badgeBackgroundRes = R.drawable.bg_garage_reminder_due_badge,
                badgeTextColorRes = R.color.white,
                primaryText = getString(R.string.garage_profile_maintenance_due_now),
                secondaryText = buildMaintenanceTargetSummary(targetKm, targetDateMillis),
                primaryTextColorRes = R.color.accent_color,
                compactStatusText = getString(R.string.garage_profile_maintenance_history_due_now),
                compactTextColorRes = R.color.accent_color,
                progressPercent = null,
                progressBarColorRes = R.color.accent_color
            )

            else -> {
                val progressPercent = resolveMaintenanceReminderProgress(entry, nowMillis)
                    ?.times(100f)
                    ?.roundToInt()
                    ?.coerceIn(0, 100)
                MaintenanceReminderPresentation(
                    entry = entry,
                    status = MaintenanceReminderStatus.UPCOMING,
                    badgeLabel = getString(R.string.garage_profile_maintenance_status_upcoming),
                    badgeBackgroundRes = R.drawable.bg_garage_reminder_badge,
                    badgeTextColorRes = R.color.accent_color,
                    primaryText = buildUpcomingReminderPrimaryText(reminderKm, reminderDateMillis),
                    secondaryText = buildMaintenanceTargetSummary(targetKm, targetDateMillis),
                    primaryTextColorRes = R.color.text_primary,
                    compactStatusText = getString(
                        R.string.garage_profile_maintenance_progress_percent,
                        progressPercent ?: 0
                    ),
                    compactTextColorRes = R.color.text_tertiary,
                    progressPercent = progressPercent,
                    progressBarColorRes = R.color.accent_color
                )
            }
        }
    }

    private fun resolveMaintenanceReminderProgress(
        entry: GarageMaintenanceEntry,
        nowMillis: Long
    ): Float? {
        if (!entry.reminderEnabled) {
            return null
        }

        if (entry.reminderTriggeredAt != null) {
            return 1f
        }

        val progressCandidates = mutableListOf<Float>()
        val serviceTimestamp = resolveGarageEntryTimestamp(entry.date, entry.createdAt)
        val latestRecordedOdometerKm = GarageOdometerTimeline.latestRecordedOdometerFrom(
            context = this,
            profileId = entry.profileId,
            source = GarageOdometerSource.MAINTENANCE,
            entryId = entry.id,
            dateText = entry.date,
            fallbackTimestamp = entry.createdAt
        )

        GarageMaintenanceReminderRules.resolveKmReminder(entry)?.let { reminderKm ->
            val startKm = entry.odometerKm
            if (startKm > 0L && reminderKm > startKm) {
                val currentKm = (latestRecordedOdometerKm ?: startKm).coerceAtLeast(startKm)
                val kmProgress = (currentKm - startKm).toFloat() / (reminderKm - startKm).toFloat()
                progressCandidates += kmProgress.coerceIn(0f, 1f)
            }
        }

        GarageMaintenanceReminderRules.resolveDateReminder(entry, serviceTimestamp)?.let { reminderDateMillis ->
            if (reminderDateMillis > serviceTimestamp) {
                val dateProgress = (nowMillis - serviceTimestamp).toDouble() /
                    (reminderDateMillis - serviceTimestamp).toDouble()
                progressCandidates += dateProgress.toFloat().coerceIn(0f, 1f)
            }
        }

        return progressCandidates.maxOrNull()
    }

    private fun buildUpcomingReminderPrimaryText(
        reminderKm: Long?,
        reminderDateMillis: Long?
    ): String {
        return when {
            reminderKm != null && reminderDateMillis != null -> getString(
                R.string.garage_profile_maintenance_reminder_at_km_or_date,
                formatOdometer(reminderKm),
                formatReminderShortDate(reminderDateMillis)
            )

            reminderKm != null -> getString(
                R.string.garage_profile_maintenance_reminder_at_km,
                formatOdometer(reminderKm)
            )

            reminderDateMillis != null -> getString(
                R.string.garage_profile_maintenance_reminder_on_date,
                formatReminderShortDate(reminderDateMillis)
            )

            else -> getString(R.string.garage_profile_maintenance_reminder_badge)
        }
    }

    private fun buildMaintenanceTargetSummary(targetKm: Long?, targetDateMillis: Long?): String {
        val parts = mutableListOf<String>()
        if (targetKm != null) {
            parts += formatOdometer(targetKm)
        }
        if (targetDateMillis != null) {
            parts += formatReminderShortDate(targetDateMillis)
        }

        return if (parts.isEmpty()) {
            getString(R.string.garage_profile_maintenance_target_reached)
        } else {
            buildString {
                append(getString(R.string.garage_profile_maintenance_target_summary_prefix))
                append(' ')
                append(parts.joinToString(" • "))
            }
        }
    }

    private fun buildMaintenanceOverdueTexts(
        targetKm: Long?,
        targetDateMillis: Long?,
        latestRecordedOdometerKm: Long?,
        targetKmReachedAt: Long?,
        nowMillis: Long
    ): Pair<String, String> {
        val currentKm = latestRecordedOdometerKm ?: 0L
        val kmOverdueValue = targetKm?.let { (currentKm - it).coerceAtLeast(0L) }
        val daysOverdue = targetDateMillis?.let {
            ((nowMillis - it) / MILLIS_PER_DAY).toInt().coerceAtLeast(0)
        }

        val preferKm = when {
            targetKmReachedAt != null && targetDateMillis != null && nowMillis >= targetDateMillis -> targetKmReachedAt <= targetDateMillis
            targetKmReachedAt != null -> true
            else -> false
        }

        return if (preferKm && targetKm != null) {
            val primaryText = if ((kmOverdueValue ?: 0L) > 0L) {
                getString(
                    R.string.garage_profile_maintenance_km_overdue,
                    NumberFormat.getIntegerInstance(Locale.US).format(kmOverdueValue)
                )
            } else {
                getString(R.string.garage_profile_maintenance_target_km_reached)
            }
            val compactText = if ((kmOverdueValue ?: 0L) > 0L) {
                getString(
                    R.string.garage_profile_maintenance_history_overdue_km_short,
                    NumberFormat.getIntegerInstance(Locale.US).format(kmOverdueValue)
                )
            } else {
                getString(R.string.garage_profile_maintenance_status_overdue)
            }
            primaryText to compactText
        } else {
            val primaryText = if ((daysOverdue ?: 0) > 0) {
                getString(R.string.garage_profile_maintenance_days_overdue, daysOverdue ?: 0)
            } else {
                getString(R.string.garage_profile_maintenance_target_date_reached)
            }
            val compactText = if ((daysOverdue ?: 0) > 0) {
                getString(R.string.garage_profile_maintenance_history_overdue_days_short, daysOverdue ?: 0)
            } else {
                getString(R.string.garage_profile_maintenance_status_overdue)
            }
            primaryText to compactText
        }
    }

    private fun createHistoryEmptyStateView(textResId: Int): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = getString(textResId)
            setTextColor(ContextCompat.getColor(this@GarageProfilePageActivity, R.color.text_tertiary))
            textSize = 12f
            setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6))
        }
    }

    private fun calculateMaintenanceEntryTotal(entry: GarageMaintenanceEntry): Double {
        return entry.partsCost.coerceAtLeast(0.0) + entry.laborCost.coerceAtLeast(0.0)
    }

    private fun resolveGarageEntryTimestamp(rawDate: String, fallbackCreatedAt: Long): Long {
        parseGarageEntryDate(rawDate)?.let { return it.time }
        return fallbackCreatedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
    }

    private fun resolveGarageEntryYear(rawDate: String, fallbackCreatedAt: Long): Int {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = resolveGarageEntryTimestamp(rawDate, fallbackCreatedAt)
        }
        return calendar.get(Calendar.YEAR)
    }

    private fun parseGarageEntryDate(rawDate: String): Date? {
        val value = rawDate.trim()
        if (value.isBlank()) {
            return null
        }

        val locales = linkedSetOf(Locale.getDefault(), Locale.ENGLISH, Locale.US, Locale("bg"))
        locales.forEach { locale ->
            val patterns = listOf("dd MMM yyyy, HH:mm", "dd MMM yyyy")
            patterns.forEach { pattern ->
                val parser = SimpleDateFormat(pattern, locale).apply {
                    isLenient = false
                }
                val parsed = runCatching { parser.parse(value) }.getOrNull()
                if (parsed != null) {
                    return parsed
                }
            }
        }
        return null
    }

    private fun formatCurrency(value: Double): String {
        return String.format(Locale.getDefault(), "%.2f €", value)
    }

    private fun formatReminderShortDate(valueMillis: Long): String {
        return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(valueMillis))
    }

    private fun formatDocumentShortDate(valueMillis: Long): String {
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(valueMillis))
    }

    private fun formatPricePerLitre(value: Double): String {
        return String.format(Locale.getDefault(), "%.2f €/L", value)
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

    private enum class DocumentHistoryFilter {
        ALL,
        ACTIVE,
        OVERDUE
    }

    private enum class DocumentHistoryStatus(val sortOrder: Int) {
        DUE(0),
        UPCOMING(1),
        COMPLETED(2),
        NO_REMINDER(3),
        OVERDUE(4)
    }

    private data class DocumentHistoryItem(
        val entry: GarageDocumentEntry,
        val presentation: DocumentExpiryPresentation
    )

    private enum class MaintenanceReminderFilter {
        ALL,
        ACTIVE,
        OVERDUE
    }

    private enum class MaintenanceReminderStatus(val sortOrder: Int) {
        OVERDUE(0),
        DUE(1),
        UPCOMING(2),
        COMPLETED(3)
    }

    private data class MaintenanceHistoryItem(
        val entry: GarageMaintenanceEntry,
        val presentation: MaintenanceReminderPresentation?
    )

    private data class MaintenanceReminderPresentation(
        val entry: GarageMaintenanceEntry,
        val status: MaintenanceReminderStatus,
        val badgeLabel: String,
        val badgeBackgroundRes: Int,
        val badgeTextColorRes: Int,
        val primaryText: String,
        val secondaryText: String,
        val primaryTextColorRes: Int,
        val compactStatusText: String,
        val compactTextColorRes: Int,
        val progressPercent: Int?,
        val progressBarColorRes: Int
    )

    private data class DocumentExpiryPresentation(
        val status: DocumentHistoryStatus,
        val secondaryText: String,
        val showReminderRow: Boolean,
        val badgeLabel: String,
        val badgeBackgroundRes: Int,
        val badgeTextColorRes: Int,
        val compactStatusText: String,
        val compactTextColorRes: Int,
        val progressPercent: Int?,
        val progressBarColorRes: Int
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

    private fun bindOverviewPerformance(profile: Profile, bestTimes: OverviewBestTimes) {
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

    private fun calculateRaceDistanceKm(race: Race): Double {
        if (race.distance > 0.0) {
            return race.distance
        }

        val points = if (race.routePoints.isNotEmpty()) {
            race.routePoints
        } else {
            RouteStorage.loadRoutePoints(this, race.id)
        }
        return calculateRoutePointsDistanceKm(points)
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

    private fun calculateTrackOutingDistanceKm(
        prefs: android.content.SharedPreferences,
        sessionId: String,
        outingNumber: Int
    ): Double {
        var totalDistanceKm = 0.0
        forEachTrackLapData(prefs, sessionId, outingNumber) { lapData ->
            if (lapData.routePoints.size > 1) {
                totalDistanceKm += calculateRoutePointsDistanceKm(lapData.routePoints)
            }
        }
        return totalDistanceKm
    }

    private fun calculateTrackOutingDurationMs(
        prefs: android.content.SharedPreferences,
        sessionId: String,
        outingNumber: Int
    ): Long {
        var totalDurationMs = 0L
        forEachTrackLapData(prefs, sessionId, outingNumber) { lapData ->
            totalDurationMs += calculateLapDataDurationMs(lapData)
        }
        return totalDurationMs
    }

    private fun calculateDragAttemptDistanceKm(attempt: com.example.clinometer.DragAttempt): Double {
        val sampleLimit = minOf(attempt.speedSamples.size, attempt.speedTimeStamps.size)
        if (sampleLimit < 2) {
            return if (attempt.time0to402 > 0L) 0.402 else 0.0
        }

        var totalMeters = 0.0
        for (index in 1 until sampleLimit) {
            val t0 = attempt.speedTimeStamps[index - 1]
            val t1 = attempt.speedTimeStamps[index]
            val deltaNs = t1 - t0
            if (deltaNs <= 0L) continue

            val deltaSec = deltaNs / 1_000_000_000.0
            val v0 = attempt.speedSamples[index - 1].coerceAtLeast(0f) / 3.6
            val v1 = attempt.speedSamples[index].coerceAtLeast(0f) / 3.6
            totalMeters += ((v0 + v1) * 0.5) * deltaSec
        }

        if (totalMeters > 0.0) {
            return totalMeters / 1000.0
        }
        return if (attempt.time0to402 > 0L) 0.402 else 0.0
    }

    private fun calculateDragAttemptDurationMs(attempt: com.example.clinometer.DragAttempt): Long {
        if (attempt.duration > 0L) {
            return attempt.duration / 1_000_000L
        }

        val sampleLimit = minOf(attempt.speedSamples.size, attempt.speedTimeStamps.size)
        if (sampleLimit >= 2) {
            val firstTime = attempt.speedTimeStamps.first()
            val lastTime = attempt.speedTimeStamps[sampleLimit - 1]
            if (lastTime > firstTime) {
                return (lastTime - firstTime) / 1_000_000L
            }
        }

        return 0L
    }

    private fun calculateRoutePointsDistanceKm(points: List<com.example.clinometer.RoutePoint>): Double {
        if (points.size < 2) return 0.0

        var meters = 0.0
        for (index in 1 until points.size) {
            val previous = points[index - 1].geoPoint
            val current = points[index].geoPoint
            val results = FloatArray(1)
            Location.distanceBetween(
                previous.latitude,
                previous.longitude,
                current.latitude,
                current.longitude,
                results
            )
            meters += results[0].toDouble()
        }
        return meters / 1000.0
    }

    private fun calculateLapDataDurationMs(lapData: LapData): Long {
        val dataDuration = lapData.endTime - lapData.startTime
        if (dataDuration > 0L) {
            return dataDuration
        }

        val points = lapData.routePoints
        if (points.size < 2) {
            return 0L
        }

        val firstPoint = points.first()
        val lastPoint = points.last()
        if (lastPoint.absoluteTime > firstPoint.absoluteTime && firstPoint.absoluteTime > 0L) {
            return lastPoint.absoluteTime - firstPoint.absoluteTime
        }

        return if (lastPoint.timestamp > firstPoint.timestamp) {
            lastPoint.timestamp - firstPoint.timestamp
        } else {
            0L
        }
    }

    private inline fun forEachTrackLapData(
        prefs: android.content.SharedPreferences,
        sessionId: String,
        outingNumber: Int,
        action: (LapData) -> Unit
    ) {
        val gson = Gson()
        val lapDataCount = prefs.getInt("${sessionId}_outing_${outingNumber}_lap_data_count", 0)
        for (lapIndex in 1..lapDataCount) {
            val lapJson = prefs.getString("${sessionId}_outing_${outingNumber}_lap_data_${lapIndex}", null)
                ?: continue
            val lapData = try {
                gson.fromJson(lapJson, LapData::class.java)
            } catch (_: Exception) {
                null
            } ?: continue
            action(lapData)
        }
    }

    private fun parseTrackDurationMs(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.contains("--")) return null

        val match = Regex("^(\\d+):(\\d{1,2})\\.(\\d{1,3})$").find(trimmed) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        val fraction = match.groupValues[3]
        val millis = when (fraction.length) {
            1 -> "${fraction}00"
            2 -> "${fraction}0"
            else -> fraction.take(3)
        }.toLongOrNull() ?: return null

        return (minutes * 60_000L) + (seconds * 1_000L) + millis
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

    private fun formatGarageMetaOdometer(valueKm: Long?): String {
        return NumberFormat.getIntegerInstance(Locale.getDefault())
            .format((valueKm ?: 0L).coerceAtLeast(0L))
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

    private data class ProfilePageData(
        val summary: ProfileSessionSummary,
        val latestAddedOdometerKm: Long?,
        val fuelEntries: List<GarageFuelEntry>,
        val maintenanceEntries: List<GarageMaintenanceEntry>,
        val documentEntries: List<GarageDocumentEntry>,
        val overviewBestTimes: OverviewBestTimes
    )

    private fun syncFuelLogCount() {
        val count = GarageFuelEntryStorage.getCount(this, profileId)
        getSharedPreferences(EXTRA_STATS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("profile_${profileId}_fuel_logs_count", count)
            .apply()
    }

    private fun syncMaintenanceCount() {
        val count = GarageMaintenanceEntryStorage.getCount(this, profileId)
        getSharedPreferences(EXTRA_STATS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("profile_${profileId}_maintenance_count", count)
            .apply()
    }

    private fun syncDocumentCount() {
        val count = GarageDocumentEntryStorage.getCount(this, profileId)
        getSharedPreferences(EXTRA_STATS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("profile_${profileId}_documents_count", count)
            .apply()
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
            refreshProfileUi()
            dialog.dismiss()
        }

        dialog.show()
    }

    companion object {
        const val EXTRA_PROFILE_ID = "PROFILE_ID"
        private const val EXTRA_STATS_PREFS = "garage_profile_extra_stats"
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
