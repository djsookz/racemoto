package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.example.clinometer.settings.UnitsManager
import com.example.clinometer.network.WeatherApiService
import com.example.clinometer.network.OpenMeteoService
import com.example.clinometer.network.ElevationResponse
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.content.res.ColorStateList
import androidx.appcompat.app.AlertDialog

class TrackActivity : BaseActivity(), LocationListener {
    override fun getLayoutResourceId(): Int = R.layout.activity_track
    override fun getNavigationItemId(): Int = R.id.navTrack

    private lateinit var btnStartNewSession: android.widget.Button
    private lateinit var btnViewTrack: MaterialButton
    private lateinit var llEnvironment: LinearLayout
    private lateinit var tvTemperature: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var locationManager: LocationManager
    private var currentTemperature: Float? = null
    private var currentAltitude: Float? = null
    private lateinit var headerSofiaRing: LinearLayout
    private lateinit var contentSofiaRing: LinearLayout
    private lateinit var arrowSofiaRing: TextView
    private lateinit var headerCustomTrack: LinearLayout
    private lateinit var contentCustomTrack: LinearLayout
    private lateinit var arrowCustomTrack: TextView
    
    // Session click listeners
    private lateinit var session1SofiaRing: LinearLayout
    private lateinit var session2SofiaRing: LinearLayout
    private lateinit var session3SofiaRing: LinearLayout
    private lateinit var session1CustomTrack: LinearLayout
    private lateinit var session2CustomTrack: LinearLayout
    
    // No sessions message
    private lateinit var tvNoSessions: TextView
    
    // Dynamic sessions container
    private lateinit var llSessionsContainer: LinearLayout

    private var sofiaRingExpanded = true
    private var customTrackExpanded = true
    
    // Active session tracking
    private var hasActiveSession = false
    private var activeSessionTrackId: String? = null
    private var activeSessionTrackName: String? = null
    
        // Track management
        private lateinit var trackManager: TrackManager
        
        companion object {
            private const val LOCATION_PERMISSION_REQUEST = 1001
            private const val CACHE_LOCATION_THRESHOLD_KM = 5.0  // Кешът е валиден ако локацията е в радиус от 5км
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        trackManager = TrackManager(this)
        initializeViews()
        setupClickListeners()
        // Зареждаме кешираните данни веднага за моментално показване
        loadCachedWeatherData()
        updateEnvironmentDisplay() // Показваме кешираните стойности веднага
        setupLocation()
        checkActiveSessions()
        showNoSessionsMessage()
    }

    private fun initializeViews() {
        btnStartNewSession = findViewById(R.id.btnStartNewSession)
        btnViewTrack = findViewById(R.id.btnViewTrack)
        llEnvironment = findViewById(R.id.llEnvironment)
        tvTemperature = findViewById(R.id.tvTemperature)
        tvAltitude = findViewById(R.id.tvAltitude)
        headerSofiaRing = findViewById(R.id.headerSofiaRing)
        contentSofiaRing = findViewById(R.id.contentSofiaRing)
        arrowSofiaRing = findViewById(R.id.arrowSofiaRing)
        headerCustomTrack = findViewById(R.id.headerCustomTrack)
        contentCustomTrack = findViewById(R.id.contentCustomTrack)
        arrowCustomTrack = findViewById(R.id.arrowCustomTrack)
        
        // Session views
        session1SofiaRing = findViewById(R.id.session1SofiaRing)
        session2SofiaRing = findViewById(R.id.session2SofiaRing)
        session3SofiaRing = findViewById(R.id.session3SofiaRing)
        session1CustomTrack = findViewById(R.id.session1CustomTrack)
        session2CustomTrack = findViewById(R.id.session2CustomTrack)
        
        // No sessions message
        tvNoSessions = findViewById(R.id.tvNoSessions)
        
        // Dynamic sessions container
        llSessionsContainer = findViewById(R.id.llSessionsContainer)
    }

    private fun setupClickListeners() {
        btnStartNewSession.setOnClickListener {
            startNewSession()
        }
        
        btnViewTrack.setOnClickListener {
            if (hasActiveSession) {
                resumeSession()
            } else {
                openTrackMap()
            }
        }

        headerSofiaRing.setOnClickListener {
            toggleAccordion("sofiaRing")
        }

        headerCustomTrack.setOnClickListener {
            toggleAccordion("customTrack")
        }

        // Add click listeners for session items (example)
        setupSessionClickListeners()
    }

    private fun setupSessionClickListeners() {
        // Sofia Ring sessions
        session1SofiaRing.setOnClickListener {
            openSessionDetail(
                "Излизане #1", "23.12.2024", "14:30", "2:15:30", 
                "Sofia Ring", 15, "Kawasaki Ninja ZX-10R", 
                "1:23.456", "45 km/h", "285 km/h", "2.8g", "1.2g"
            )
        }
        
        session2SofiaRing.setOnClickListener {
            openSessionDetail(
                "Излизане #2", "23.12.2024", "16:45", "1:30:15", 
                "Sofia Ring", 8, "Kawasaki Ninja ZX-10R", 
                "1:25.123", "50 km/h", "275 km/h", "2.5g", "1.1g"
            )
        }
        
        session3SofiaRing.setOnClickListener {
            openSessionDetail(
                "Излизане #3", "23.12.2024", "19:20", "3:45:20", 
                "Sofia Ring", 22, "Kawasaki Ninja ZX-10R", 
                "1:18.234", "40 km/h", "290 km/h", "3.0g", "1.3g"
            )
        }
        
        // Custom Track sessions
        session1CustomTrack.setOnClickListener {
            openSessionDetail(
                "Излизане #1", "22.12.2024", "09:15", "1:45:20", 
                "Custom Track", 8, "Kawasaki Ninja ZX-10R", 
                "2:15.789", "35 km/h", "180 km/h", "1.8g", "0.9g"
            )
        }
        
        session2CustomTrack.setOnClickListener {
            openSessionDetail(
                "Излизане #2", "22.12.2024", "14:30", "2:30:45", 
                "Custom Track", 12, "Kawasaki Ninja ZX-10R", 
                "2:10.456", "40 km/h", "200 km/h", "2.0g", "1.0g"
            )
        }
    }

    private fun openSessionDetail(sessionNumber: String, sessionDate: String, sessionTime: String, 
                                sessionDuration: String, trackName: String, totalLaps: Int, 
                                vehicleName: String, bestLapTime: String, minSpeed: String, 
                                maxSpeed: String, maxAcceleration: String, maxCornering: String) {
        val intent = Intent(this, TrackSessionDetailActivity::class.java).apply {
            putExtra("session_number", sessionNumber)
            putExtra("session_date", sessionDate)
            putExtra("session_time", sessionTime)
            putExtra("session_duration", sessionDuration)
            putExtra("track_name", trackName)
            putExtra("total_laps", totalLaps)
            putExtra("vehicle_name", vehicleName)
            putExtra("best_lap_time", bestLapTime)
            putExtra("min_speed", minSpeed)
            putExtra("max_speed", maxSpeed)
            putExtra("max_acceleration", maxAcceleration)
            putExtra("max_cornering", maxCornering)
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private fun toggleAccordion(trackType: String) {
        when (trackType) {
            "sofiaRing" -> {
                sofiaRingExpanded = !sofiaRingExpanded
                if (sofiaRingExpanded) {
                    contentSofiaRing.visibility = View.VISIBLE
                    arrowSofiaRing.text = "▼"
                } else {
                    contentSofiaRing.visibility = View.GONE
                    arrowSofiaRing.text = "▶"
                }
            }
            "customTrack" -> {
                customTrackExpanded = !customTrackExpanded
                if (customTrackExpanded) {
                    contentCustomTrack.visibility = View.VISIBLE
                    arrowCustomTrack.text = "▼"
                } else {
                    contentCustomTrack.visibility = View.GONE
                    arrowCustomTrack.text = "▶"
                }
            }
        }
    }

    private fun startNewSession() {
        // Clear any active session when starting a new one
        clearActiveSession()
        
        
        // Go to track selection for new session
        val intent = Intent(this, TrackSelectionActivity::class.java)
        startActivity(intent)
        overridePendingTransition(0, 0)
    }
    
    private fun openTrackSelection() {
        // Clear any active session when starting a new one
        clearActiveSession()
        
        val intent = Intent(this, TrackSelectionActivity::class.java)
        startActivity(intent)
        overridePendingTransition(0, 0)
    }
    
    private fun openTrackMap() {
        val intent = Intent(this, TrackMapActivity::class.java).apply {
            putExtra("track_id", "serres_circuit")
            putExtra("track_name", getString(R.string.track_name_serres))
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }
    
    
    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun clearAllSessionData() {
        // Clear track_outings data
        val outingsPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        outingsPrefs.edit().clear().apply()
        
        // Clear track_sessions data
        val sessionsPrefs = getSharedPreferences("track_sessions", MODE_PRIVATE)
        sessionsPrefs.edit().clear().apply()
        
        // Clear track_session_detail data
        val detailPrefs = getSharedPreferences("track_session_detail", MODE_PRIVATE)
        detailPrefs.edit().clear().apply()
        
    }
    
    private fun showDeleteConfirmationDialog(sessionId: String, trackName: String, sessionCard: View) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.track_delete_session_title))
        builder.setMessage(getString(R.string.track_delete_session_message, trackName))
        
        builder.setPositiveButton(getString(R.string.track_delete_button)) { _, _ ->
            deleteSession(sessionId, sessionCard)
        }
        
        builder.setNegativeButton(getString(R.string.cancel), null)
        
        val dialog = builder.create()
        dialog.show()
        
        // Style the buttons
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(this, R.color.red))
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(this, R.color.accent_blue))
    }
    
    private fun deleteSession(sessionId: String, sessionCard: View) {
        val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        
        
        // Get all keys for this session
        val allKeys = sharedPrefs.all.keys
        val sessionKeys = allKeys.filter { it.startsWith("${sessionId}_") }
        
        
        // Delete all keys for this session
        for (key in sessionKeys) {
            editor.remove(key)
        }
        
        // Also clear any active session if it's the same
        if (activeSessionTrackId == sessionId) {
            clearActiveSession()
        }
        
        editor.apply()
        
        // Remove the session card from UI
        llSessionsContainer.removeView(sessionCard)
        
        // Show success message
        showToast(getString(R.string.track_session_deleted))
        
        // Check if no sessions left
        showNoSessionsMessage()
        
    }
    
    private fun checkActiveSessions() {
        val sharedPrefs = getSharedPreferences("track_sessions", MODE_PRIVATE)
        hasActiveSession = sharedPrefs.getBoolean("has_active_session", false)
        activeSessionTrackId = sharedPrefs.getString("active_track_id", null)
        activeSessionTrackName = sharedPrefs.getString("active_track_name", null)
        
        
        // Load all saved sessions from track_outings
        loadAllSessions()
        
        updateViewTrackButton()
    }
    
    private fun loadAllSessions() {
        val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val currentProfileId = ProfileStorage.getSelectedProfileId(this)
        val allKeys = sharedPrefs.all.keys
        
        // Debug: Print all keys to see what's in SharedPreferences
        
        // Clear existing session cards first
        llSessionsContainer.removeAllViews()
        
        // Get unique session IDs (trackId + date) from saved outings
        val sessionIds = mutableSetOf<String>()
        for (key in allKeys) {
            if (key.endsWith("_outing_count")) {
                val sessionIdFull = key.removeSuffix("_outing_count")
                // Filter by current profile: keys are stored as "<profileId>_<sessionId>_..."
                if (sessionIdFull.startsWith("${currentProfileId}_")) {
                    sessionIds.add(sessionIdFull)
                }
            }
        }
        
        
        // If no sessions found, show no sessions message
        if (sessionIds.isEmpty()) {
            showNoSessionsMessage()
        } else {
        // Sort sessions by date/time (newest first)
        val sortedSessionIds = sessionIds.sortedByDescending { sessionIdFull ->
            // Extract timestamp from sessionId for sorting
            val sessionId = sessionIdFull.substringAfter("_")
            val parts = sessionId.split("_")
            when {
                parts.size >= 4 -> {
                    // New format with timestamp: "serres_circuit_23.09.2025_1820_1727121627000"
                    parts.last().toLongOrNull() ?: 0L
                }
                parts.size >= 3 -> {
                    // Old format: "serres_circuit_23.09.2025_1820" - convert to timestamp
                    try {
                        val date = parts[parts.size - 2] // "23.09.2025"
                        val time = parts[parts.size - 1] // "1820"
                        val dateTime = "$date $time"
                        val formatter = java.text.SimpleDateFormat("dd.MM.yyyy HHmm", java.util.Locale.getDefault())
                        formatter.parse(dateTime)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }
                else -> 0L // Very old format
            }
        }
        
        
        // Create session cards for each session (now in sorted order)
        for (sessionIdFull in sortedSessionIds) {
            // Extract trackId from sessionId (same logic as in createSessionCard)
            val visibleId = sessionIdFull.substringAfter("_")
            val parts = visibleId.split("_")
            val trackId = if (parts.size >= 4) {
                // New format with timestamp: "serres_circuit_23.09.2025_1820_1727121627000"
                parts.dropLast(3).joinToString("_")
            } else if (parts.size >= 3) {
                // Old format: "serres_circuit_23.09.2025_1820"
                parts.dropLast(2).joinToString("_")
            } else {
                // Very old format: "serres_circuit"
                visibleId
            }
            val trackName = getTrackName(trackId)
            createSession(sessionIdFull, trackName)
        }
        }
    }
    
    private fun getTrackName(trackId: String): String {
        val result = when (trackId) {
            "serres_circuit" -> getString(R.string.track_name_serres)
            "sofia_ring" -> getString(R.string.track_name_sofia)
            "custom_track" -> getString(R.string.track_name_custom)
            else -> {
                // Check if it's a custom track by ID pattern
                if (trackId.startsWith("custom_")) {
                    // Try to load the custom track to get its actual name
                    val customTrack = com.example.clinometer.tracking.CustomTrackStorage.loadCustomTrack(this, trackId)
                    customTrack?.name ?: getString(R.string.track_name_unknown)
                } else {
                    getString(R.string.track_name_unknown)
                }
            }
        }
        return result
    }
    
    private fun showNoSessionsMessage() {
        // Check if there are any sessions
        if (llSessionsContainer.childCount == 0) {
            tvNoSessions.visibility = View.VISIBLE
        } else {
            tvNoSessions.visibility = View.GONE
        }
    }
    
    private fun updateViewTrackButton() {
        btnViewTrack.visibility = View.GONE
    }
    
    private fun resumeSession() {
        if (activeSessionTrackId != null && activeSessionTrackName != null) {
            // Extract trackId from sessionId for TrackSessionActivity
            val parts = activeSessionTrackId!!.split("_")
            val trackId = if (parts.size >= 4) {
                // New format with timestamp: "serres_circuit_23.09.2025_1820_1727121627000"
                parts.dropLast(3).joinToString("_")
            } else if (parts.size >= 3) {
                // Old format: "serres_circuit_23.09.2025_1820"
                parts.dropLast(2).joinToString("_")
            } else {
                // Very old format: "serres_circuit"
                activeSessionTrackId!!
            }
            
            
            val intent = Intent(this, TrackSessionActivity::class.java).apply {
                putExtra("track_id", trackId)
                putExtra("track_name", activeSessionTrackName)
                putExtra("resume_session", true) // ✅ RESUME FLAG
                putExtra("session_id", activeSessionTrackId) // ✅ EXISTING SESSION ID
                putExtra("is_motorcycle", true)
            }
            
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
    }
    
    private fun resumeSpecificSession(sessionId: String, trackName: String) {
        // Extract trackId from sessionId for TrackSessionActivity
        val parts = sessionId.split("_")
        val trackId = if (parts.size >= 4) {
            // New format with timestamp: "serres_circuit_23.09.2025_1820_1727121627000"
            parts.dropLast(3).joinToString("_")
        } else if (parts.size >= 3) {
            // Old format: "serres_circuit_23.09.2025_1820"
            parts.dropLast(2).joinToString("_")
        } else {
            // Very old format: "serres_circuit"
            sessionId
        }
        
        
        val intent = Intent(this, TrackSessionActivity::class.java).apply {
            putExtra("track_id", trackId)
            putExtra("track_name", trackName)
            putExtra("resume_session", true) // ✅ RESUME FLAG
            putExtra("session_id", sessionId) // ✅ SPECIFIC SESSION ID
            val currentProfileId = ProfileStorage.getSelectedProfileId(this@TrackActivity)
            val profiles = ProfileStorage.loadProfiles(this@TrackActivity)
            val profile = profiles.find { it.id == currentProfileId }
            val isMotorcycle = profile?.vehicleType == Profile.VehicleType.MOTORCYCLE
            putExtra("is_motorcycle", isMotorcycle)
        }
        
        startActivity(intent)
        overridePendingTransition(0, 0)
    }
    
    fun setActiveSession(trackId: String, trackName: String) {
        hasActiveSession = true
        activeSessionTrackId = trackId
        activeSessionTrackName = trackName
        
        val sharedPrefs = getSharedPreferences("track_sessions", MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean("has_active_session", true)
            .putString("active_track_id", trackId)
            .putString("active_track_name", trackName)
            .apply()
        
        updateViewTrackButton()
    }
    
    fun clearActiveSession() {
        hasActiveSession = false
        activeSessionTrackId = null
        activeSessionTrackName = null
        
        val sharedPrefs = getSharedPreferences("track_sessions", MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean("has_active_session", false)
            .remove("active_track_id")
            .remove("active_track_name")
            .apply()
        
        updateViewTrackButton()
    }
    
    fun createSession(sessionId: String, trackName: String) {
        
        // Check if session already exists
        if (sessionExists(sessionId)) {
            return
        }
        
        // Create session card
        val sessionCard = createSessionCard(sessionId, trackName)
        
        // Add to container
        llSessionsContainer.addView(sessionCard)
        
        // Hide no sessions message
        tvNoSessions.visibility = View.GONE
        
        // Set active session only if it's the current active one
        if (hasActiveSession && activeSessionTrackId == sessionId) {
            setActiveSession(sessionId, trackName)
        }
        
        // If this is a new session (not from SharedPreferences), set it as active
        if (!hasActiveSession) {
            setActiveSession(sessionId, trackName)
        }
        
    }
    
    private fun sessionExists(sessionId: String): Boolean {
        for (i in 0 until llSessionsContainer.childCount) {
            val child = llSessionsContainer.getChildAt(i)
            val tag = child.tag
            if (tag == sessionId) {
                return true
            }
        }
        return false
    }
    
    private fun createSessionCard(sessionIdFull: String, trackName: String): View {
        val inflater = layoutInflater
        val sessionCard = inflater.inflate(R.layout.session_card_template, llSessionsContainer, false)
        
        // Set tag for duplicate checking
        sessionCard.tag = sessionIdFull
        
        // Extract trackId and date from sessionId
        // sessionId format: "serres_circuit_23.09.2025_1820_1727121627000" or older formats
        val visibleId = sessionIdFull.substringAfter("_")
        val parts = visibleId.split("_")
        val trackId: String
        val sessionDate: String
        val sessionTime: String
        
        if (parts.size >= 4) {
            // New format with timestamp: "serres_circuit_23.09.2025_1820_1727121627000"
            trackId = parts.dropLast(3).joinToString("_")
            sessionDate = parts[parts.size - 3]
            sessionTime = parts[parts.size - 2]
        } else if (parts.size >= 3) {
            // Old format: "serres_circuit_23.09.2025_1820"
            trackId = parts.dropLast(2).joinToString("_")
            sessionDate = parts[parts.size - 2]
            sessionTime = parts[parts.size - 1]
        } else {
            // Very old format: "serres_circuit"
            trackId = visibleId
            sessionDate = ""
            sessionTime = ""
        }
        
        
        // Set track name
        val tvTrackName = sessionCard.findViewById<TextView>(R.id.tvTrackName)
        tvTrackName.text = trackName
        
        // Set session details (date and time instead of track specs)
        val tvTrackDetails = sessionCard.findViewById<TextView>(R.id.tvTrackDetails)
        if (sessionDate.isNotEmpty() && sessionTime.isNotEmpty()) {
            // Format time back to readable format (remove the replace(":", "") from saveOutingData)
            val formattedTime = if (sessionTime.length == 4) {
                "${sessionTime.substring(0, 2)}:${sessionTime.substring(2, 4)}"
            } else {
                sessionTime
            }
            tvTrackDetails.text = "$sessionDate • $formattedTime"
        } else {
            tvTrackDetails.text = "Нова сесия"
        }
        
        
        // Set up resume button
        val btnResume = sessionCard.findViewById<MaterialButton>(R.id.btnResume)
        btnResume.setOnClickListener {
            resumeSpecificSession(sessionIdFull, trackName)
        }
        
        // Set up delete button
        val btnDeleteSession = sessionCard.findViewById<ImageButton>(R.id.btnDeleteSession)
        btnDeleteSession.setOnClickListener {
            showDeleteConfirmationDialog(sessionIdFull, trackName, sessionCard)
        }
        
        // Set up expand/collapse
        val headerLayout = sessionCard.findViewById<LinearLayout>(R.id.headerLayout)
        val contentLayout = sessionCard.findViewById<LinearLayout>(R.id.contentLayout)
        val arrow = sessionCard.findViewById<TextView>(R.id.arrow)
        
        headerLayout.setOnClickListener {
            toggleSessionExpansion(contentLayout, arrow)
        }
        
        // Load and display outings
        loadOutingsForSession(sessionCard, sessionIdFull)
        
        return sessionCard
    }
    
    private fun loadOutingsForSession(sessionCard: View, sessionId: String) {
        val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val outingCount = sharedPrefs.getInt("${sessionId}_outing_count", 0)
        val contentLayout = sessionCard.findViewById<LinearLayout>(R.id.contentLayout)
        val tvNoOutings = sessionCard.findViewById<TextView>(R.id.tvNoOutings)
        
        if (outingCount == 0) {
            tvNoOutings.visibility = View.VISIBLE
        } else {
            tvNoOutings.visibility = View.GONE
            
            // Add outing views
            for (i in 1..outingCount) {
                val outingView = createOutingView(sessionId, i, sharedPrefs)
                contentLayout.addView(outingView)
            }
        }
    }
    
    private fun createOutingView(sessionId: String, outingNumber: Int, sharedPrefs: android.content.SharedPreferences): View {
        val outingView = layoutInflater.inflate(R.layout.outing_item_template, null)
        
        // Set outing data
        val tvOutingTitle = outingView.findViewById<TextView>(R.id.tvOutingTitle)
        val tvOutingTime = outingView.findViewById<TextView>(R.id.tvOutingTime)
        val tvOutingDuration = outingView.findViewById<TextView>(R.id.tvOutingDuration)
        val tvOutingLaps = outingView.findViewById<TextView>(R.id.tvOutingLaps)
        val tvOutingBestLap = outingView.findViewById<TextView>(R.id.tvOutingBestLap)
        
        tvOutingTitle.text = getString(R.string.track_session_title, outingNumber)
        tvOutingTime.text = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_time", "--:--")
        tvOutingDuration.text = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_duration", "--:--")
        tvOutingLaps.text = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_laps", "0")
        tvOutingBestLap.text = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_best_lap", "--:--.---")
        
        // Set click listener for outing details
        outingView.setOnClickListener {
            openOutingDetail(sessionId, outingNumber, sharedPrefs)
        }
        
        return outingView
    }
    
    private fun openOutingDetail(sessionIdFull: String, outingNumber: Int, sharedPrefs: android.content.SharedPreferences) {
        val intent = Intent(this, TrackSessionDetailActivity::class.java)
        
        // Extract trackId from sessionId
        val visibleId = sessionIdFull.substringAfter("_")
        val trackId = visibleId.split("_")[0]
        
        // Pass outing data
        intent.putExtra("trackName", getTrackName(trackId))
        intent.putExtra("trackId", sessionIdFull)
        intent.putExtra("outingNumber", outingNumber)
        intent.putExtra("date", sharedPrefs.getString("${sessionIdFull}_outing_${outingNumber}_date", ""))
        intent.putExtra("time", sharedPrefs.getString("${sessionIdFull}_outing_${outingNumber}_time", ""))
        intent.putExtra("duration", sharedPrefs.getString("${sessionIdFull}_outing_${outingNumber}_duration", ""))
        intent.putExtra("totalLaps", sharedPrefs.getString("${sessionIdFull}_outing_${outingNumber}_laps", "0"))
        intent.putExtra("bestLapTime", sharedPrefs.getString("${sessionIdFull}_outing_${outingNumber}_best_lap", "--:--.---"))
        intent.putExtra("maxSpeed", sharedPrefs.getString("${sessionIdFull}_outing_${outingNumber}_max_speed", "0.0 km/h"))
        intent.putExtra("maxAcceleration", sharedPrefs.getString("${sessionIdFull}_outing_${outingNumber}_max_acceleration", "0.00 G"))
        intent.putExtra("maxBraking", sharedPrefs.getString("${sessionIdFull}_outing_${outingNumber}_max_braking", "0.00 G"))
        intent.putExtra("maxCorneringG", sharedPrefs.getString("${sessionIdFull}_outing_${outingNumber}_max_cornering", "0.00 G"))
        intent.putExtra("maxLeanAngle", sharedPrefs.getString("${sessionIdFull}_outing_${outingNumber}_max_lean_angle", "0.0°"))
        
        startActivity(intent)
        overridePendingTransition(0, 0)
    }
    
    private fun toggleSessionExpansion(contentLayout: LinearLayout, arrow: TextView) {
        if (contentLayout.visibility == View.VISIBLE) {
            contentLayout.visibility = View.GONE
            arrow.text = "▼"
        } else {
            contentLayout.visibility = View.VISIBLE
            arrow.text = "▲"
        }
    }
    
    private var backPressedTime: Long = 0
    private val backPressedInterval: Long = 2000 // 2 секунди
    
    override fun onBackPressed() {
        if (backPressedTime + backPressedInterval > System.currentTimeMillis()) {
            // Двойно натискане - излизаме от приложението
            super.onBackPressed()
            finishAffinity() // Затваря всички activities
        } else {
            // Първо натискане - показваме съобщение
            android.widget.Toast.makeText(this, getString(R.string.back_press_exit), android.widget.Toast.LENGTH_SHORT).show()
        }
        backPressedTime = System.currentTimeMillis()
    }
    
    private fun setupLocation() {
        locationManager = getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0f, this)
        } else {
            androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
        }
    }
    
    private fun fetchWeatherData() {
        // Weather data will be fetched when location is available
    }
    
    private fun updateEnvironmentDisplay() {
        val tempText = if (currentTemperature != null) {
            UnitsManager.formatTemperature(currentTemperature!!, this, decimals = 0)
        } else {
            val unit = UnitsManager.getTemperatureUnit(this)
            "--${unit.symbol}"
        }
        
        val altText = if (currentAltitude != null) {
            String.format("%.0fm", currentAltitude)
        } else {
            "--m"
        }
        
        tvTemperature.text = tempText
        tvAltitude.text = altText
        
        // Show environment info if we have any data
        if (currentTemperature != null || currentAltitude != null) {
            llEnvironment.visibility = LinearLayout.VISIBLE
        }
    }
    
    private fun fetchWeatherFromAPI(location: Location) {
        android.util.Log.d("TrackActivity", "Fetching weather for location: ${location.latitude}, ${location.longitude}")
        lifecycleScope.launch {
            try {
                val weatherRetrofit = Retrofit.Builder()
                    .baseUrl("https://api.weatherapi.com/v1/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                
                val elevationRetrofit = Retrofit.Builder()
                    .baseUrl("https://api.open-meteo.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                
                val weatherApiService = weatherRetrofit.create(WeatherApiService::class.java)
                val openMeteoService = elevationRetrofit.create(OpenMeteoService::class.java)
                
                // Fetch weather from WeatherAPI.com
                val weatherResponse = weatherApiService.getCurrentWeather(
                    apiKey = "547cc84c36a447ab8fe131642251808",
                    location = "${location.latitude},${location.longitude}",
                    lang = "bg"
                )
                android.util.Log.d("TrackActivity", "Weather response: ${weatherResponse.isSuccessful}")
                if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                    val weather = weatherResponse.body()!!
                    currentTemperature = weather.current.temp_c.toFloat()
                    android.util.Log.d("TrackActivity", "Temperature: $currentTemperature")
                }
                
                // Fetch elevation
                val elevationResponse = openMeteoService.getElevation(
                    location.latitude,
                    location.longitude
                )
                android.util.Log.d("TrackActivity", "Elevation response: ${elevationResponse.isSuccessful}")
                if (elevationResponse.isSuccessful && elevationResponse.body() != null) {
                    val elevation = elevationResponse.body()!!
                    currentAltitude = elevation.elevation.firstOrNull()?.toFloat() ?: 0f
                    android.util.Log.d("TrackActivity", "Altitude: $currentAltitude")
                }
                
                // Кешираме новите данни
                cacheWeatherData(location)
                
                withContext(Dispatchers.Main) {
                    updateEnvironmentDisplay()
                }
            } catch (e: Exception) {
                android.util.Log.e("TrackActivity", "Error fetching weather data", e)
            }
        }
    }
    
    override fun onLocationChanged(location: Location) {
        // Проверяваме дали трябва да направим заявка
        val shouldFetch = shouldFetchWeatherData(location)
        if (shouldFetch) {
            fetchWeatherFromAPI(location)
        }
    }
    
    /**
     * Зарежда кешираните данни за температура и височина от SharedPreferences
     */
    private fun loadCachedWeatherData() {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val cachedTemp = prefs.getFloat("cached_temperature", Float.NaN)
        val cachedAlt = prefs.getFloat("cached_altitude", Float.NaN)
        val cachedLat = prefs.getFloat("cached_location_lat", Float.NaN)
        val cachedLon = prefs.getFloat("cached_location_lon", Float.NaN)
        
        if (!cachedTemp.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentTemperature = cachedTemp
            android.util.Log.d("TrackActivity", "✅ Loaded cached temperature: $currentTemperature°C")
        }
        
        if (!cachedAlt.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentAltitude = cachedAlt
            android.util.Log.d("TrackActivity", "✅ Loaded cached altitude: $currentAltitude m")
        }
    }
    
    /**
     * Кешира данните за температура и височина в SharedPreferences
     */
    private fun cacheWeatherData(location: Location) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val editor = prefs.edit()
        
        currentTemperature?.let {
            editor.putFloat("cached_temperature", it)
        }
        currentAltitude?.let {
            editor.putFloat("cached_altitude", it)
        }
        editor.putFloat("cached_location_lat", location.latitude.toFloat())
        editor.putFloat("cached_location_lon", location.longitude.toFloat())
        editor.apply()
        
        android.util.Log.d("TrackActivity", "💾 Cached weather data: temp=$currentTemperature, alt=$currentAltitude")
    }
    
    /**
     * Проверява дали трябва да направим заявка за данни
     */
    private fun shouldFetchWeatherData(location: Location): Boolean {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val cachedLat = prefs.getFloat("cached_location_lat", Float.NaN)
        val cachedLon = prefs.getFloat("cached_location_lon", Float.NaN)
        
        // Ако нямаме кеширани данни, правим заявка
        if (cachedLat.isNaN() || cachedLon.isNaN()) {
            android.util.Log.d("TrackActivity", "🔄 No cached data, fetching...")
            return true
        }
        
        // Проверяваме дали локацията е се променила значително
        val cachedLocation = Location("cached").apply {
            latitude = cachedLat.toDouble()
            longitude = cachedLon.toDouble()
        }
        val distanceKm = location.distanceTo(cachedLocation) / 1000.0
        
        if (distanceKm > CACHE_LOCATION_THRESHOLD_KM) {
            android.util.Log.d("TrackActivity", "🔄 Location changed significantly (${String.format("%.1f", distanceKm)}km), fetching...")
            return true
        }
        
        // Ако имаме кеширани данни и локацията е близо, не правим заявка
        android.util.Log.d("TrackActivity", "✅ Using cached data (location change: ${String.format("%.1f", distanceKm)}km)")
        return false
    }
    
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}