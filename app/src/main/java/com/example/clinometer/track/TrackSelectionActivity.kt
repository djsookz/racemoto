package com.example.clinometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.track.CustomTrackBuilderActivity
import com.example.clinometer.track.catalog.OfficialTrackCatalog
import com.example.clinometer.track.catalog.TrackDefinition
import com.example.clinometer.tracking.CustomTrack
import com.example.clinometer.tracking.CustomTrackStorage
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout

class TrackSelectionActivity : AppCompatActivity() {
    
    private lateinit var rvTracks: RecyclerView
    private lateinit var tabTracks: TabLayout
    private lateinit var btnCreateCustom: MaterialButton
    private lateinit var llCreateCustomContainer: LinearLayout
    private lateinit var tvEmptyState: TextView
    
    private val officialTracks: List<TrackDefinition> = OfficialTrackCatalog.getAll()
    
    private var customTracks = mutableListOf<CustomTrack>()

    private enum class TrackTab {
        OFFICIAL,
        CUSTOM
    }

    private var currentTab: TrackTab = TrackTab.OFFICIAL
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_selection)
        applySystemBarsPaddingToRoot()
        
        initializeViews()
        setupClickListeners()
        loadCustomTracks()
        renderCurrentTab()
    }
    
    private fun initializeViews() {
        rvTracks = findViewById(R.id.rvTracks)
        tabTracks = findViewById(R.id.tabTracks)
        btnCreateCustom = findViewById(R.id.btnCreateCustom)
        llCreateCustomContainer = findViewById(R.id.llCreateCustomContainer)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        rvTracks.layoutManager = LinearLayoutManager(this)
        setupTabs()
        
        // Back button
        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupClickListeners() {
        btnCreateCustom.setOnClickListener {
            val intent = Intent(this, CustomTrackCreationModeActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupTabs() {
        tabTracks.removeAllTabs()
        tabTracks.addTab(tabTracks.newTab().setText("Официални"))
        tabTracks.addTab(tabTracks.newTab().setText("Custom"))
        tabTracks.selectTab(tabTracks.getTabAt(0))

        tabTracks.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = if (tab.position == 0) TrackTab.OFFICIAL else TrackTab.CUSTOM
                renderCurrentTab()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }
    
    private fun loadCustomTracks() {
        customTracks.clear()
        customTracks.addAll(CustomTrackStorage.loadCustomTracks(this))
        renderCurrentTab()
    }

    private fun renderCurrentTab() {
        when (currentTab) {
            TrackTab.OFFICIAL -> renderOfficialTab()
            TrackTab.CUSTOM -> renderCustomTab()
        }
    }

    private fun renderOfficialTab() {
        llCreateCustomContainer.visibility = View.GONE

        val sortedOfficialTracks = getOfficialTracksSortedByDistance()
        val items = sortedOfficialTracks.map { TrackSelectionAdapter.TrackItem.Official(it) }
        rvTracks.adapter = TrackSelectionAdapter(
            tracks = items,
            onTrackSelected = { trackItem ->
                if (trackItem is TrackSelectionAdapter.TrackItem.Official) {
                    if (trackItem.track.isReadyForSession()) {
                        startTrackSession(trackItem.track.id, trackItem.track.name, true)
                    } else {
                        Toast.makeText(this, "Тази official писта ще бъде налична скоро.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onTrackDeleted = { },
            onTrackEdited = { }
        )

        updateEmptyState(items.isEmpty(), "Няма налични official писти")
    }

    private fun getOfficialTracksSortedByDistance(): List<TrackDefinition> {
        val currentLocation = getCurrentLocationForSorting() ?: return officialTracks

        return officialTracks.withIndex()
            .map { indexedTrack ->
                val distanceMeters = getTrackAnchorPoint(indexedTrack.value)
                    ?.distanceToAsDouble(currentLocation)
                    ?: Double.MAX_VALUE
                indexedTrack to distanceMeters
            }
            .sortedWith(compareBy<Pair<IndexedValue<TrackDefinition>, Double>> { it.second }.thenBy { it.first.index })
            .map { it.first.value }
    }

    private fun getCurrentLocationForSorting(): GeoPoint? {
        if (!hasLocationPermission()) return null

        val locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return null
        val candidateProviders = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        val latestLocation = candidateProviders
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }

        return latestLocation?.let { GeoPoint(it.latitude, it.longitude) }
    }

    private fun hasLocationPermission(): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return hasFine || hasCoarse
    }

    private fun getTrackAnchorPoint(track: TrackDefinition): GeoPoint? {
        track.startFinishGate?.let { gate ->
            return midpoint(gate.start, gate.end)
        }
        track.startGate?.let { gate ->
            return midpoint(gate.start, gate.end)
        }
        track.finishGate?.let { gate ->
            return midpoint(gate.start, gate.end)
        }
        return track.lapSequence.firstOrNull()
    }

    private fun midpoint(a: GeoPoint, b: GeoPoint): GeoPoint {
        return GeoPoint(
            latitude = (a.latitude + b.latitude) / 2.0,
            longitude = (a.longitude + b.longitude) / 2.0
        )
    }

    private fun renderCustomTab() {
        llCreateCustomContainer.visibility = View.VISIBLE

        val items = customTracks.map { TrackSelectionAdapter.TrackItem.Custom(it) }
        rvTracks.adapter = TrackSelectionAdapter(
            tracks = items,
            onTrackSelected = { trackItem ->
                if (trackItem is TrackSelectionAdapter.TrackItem.Custom) {
                    startTrackSession(trackItem.track.id, trackItem.track.name, false)
                }
            },
            onTrackDeleted = { customTrack ->
                deleteCustomTrack(customTrack)
            },
            onTrackEdited = { customTrack ->
                editCustomTrack(customTrack)
            }
        )

        updateEmptyState(items.isEmpty(), "Няма създадена custom писта")
    }

    private fun updateEmptyState(isEmpty: Boolean, message: String) {
        tvEmptyState.text = message
        tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        rvTracks.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    private fun deleteCustomTrack(customTrack: com.example.clinometer.tracking.CustomTrack) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Изтриване на писта")
            .setMessage("Сигурни ли сте, че искате да изтриете пистата '${customTrack.name}'?")
            .setPositiveButton("Да") { _, _ ->
                com.example.clinometer.tracking.CustomTrackStorage.deleteCustomTrack(this, customTrack.id)
                loadCustomTracks()
                android.widget.Toast.makeText(this, "Пистата е изтрита", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отказ", null)
            .show()
    }

    private fun editCustomTrack(customTrack: CustomTrack) {
        val customTrackV2 = CustomTrackStorage.loadCustomTrackV2(this, customTrack.id)
        val creationMode = when (customTrackV2?.creationMode) {
            com.example.clinometer.tracking.CustomTrackCreationMode.DRIVING -> "DRIVING"
            com.example.clinometer.tracking.CustomTrackCreationMode.PHONE -> "PHONE"
            null -> {
                if ((customTrackV2?.measuredDistanceMeters ?: 0f) > 50f) "DRIVING" else "PHONE"
            }
        }

        val intent = Intent(this, CustomTrackBuilderActivity::class.java).apply {
            putExtra("track_type", customTrack.type.name)
            putExtra("edit_track_id", customTrack.id)
            putExtra("creation_mode", creationMode)
        }
        startActivity(intent)
    }
    
    private fun startTrackSession(trackId: String, trackName: String, isOfficial: Boolean) {
        val selectedProfileId = ProfileStorage.getSelectedProfileId(this)
        val selectedProfile = ProfileStorage.loadProfiles(this).find { it.id == selectedProfileId }
        val intentIsMotorcycle = intent.getBooleanExtra("is_motorcycle", true)
        val isMotorcycle = selectedProfile?.vehicleType == Profile.VehicleType.MOTORCYCLE
            ?: intentIsMotorcycle

        if (selectedProfile == null) {
            android.util.Log.w(
                "TrackSelectionActivity",
                "Selected profile '$selectedProfileId' not found; falling back to intent vehicle mode=$intentIsMotorcycle"
            )
        }

        val intent = Intent(this, TrackSessionActivity::class.java).apply {
            putExtra("track_id", trackId)
            putExtra("track_name", trackName)
            putExtra("is_official", isOfficial)
            putExtra("is_motorcycle", isMotorcycle)
        }
        startActivity(intent)
        finish()
    }
    
    override fun onResume() {
        super.onResume()
        loadCustomTracks()
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, 0)
    }
}
