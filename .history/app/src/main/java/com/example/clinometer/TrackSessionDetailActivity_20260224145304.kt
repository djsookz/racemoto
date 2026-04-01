package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.preference.PreferenceManager
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.main.MainContainerActivity
import com.example.clinometer.settings.LanguageManager
import com.google.android.material.button.MaterialButton

class TrackSessionDetailActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    private lateinit var btnBack: MaterialButton
    private lateinit var tvSessionDate: TextView
    private lateinit var tvSessionTime: TextView
    private lateinit var tvSessionDuration: TextView
    private lateinit var tvTrackName: TextView
    private lateinit var tvTotalLaps: TextView
    private lateinit var tvVehicleName: TextView
    private lateinit var tvBestLapTime: TextView
    private lateinit var tvMinSpeed: TextView
    private lateinit var tvMaxSpeed: TextView
    private lateinit var tvMaxAcceleration: TextView
    private lateinit var tvMaxCornering: TextView
    private lateinit var tvTotalLapsLabel: TextView
    private lateinit var tvBestTimeLabel: TextView
    private lateinit var tvLapsSectionTitle: TextView
    private lateinit var cardLaps: CardView
    
    // Lap click listeners
    private lateinit var llLapsContainer: LinearLayout
    private lateinit var tvNoLaps: TextView
    
    // Store data to survive activity recreation
    private var trackId: String = ""
    private var outingNumber: Int = 1
    private var totalLaps: Int = 0
    private var isPointToPointSession: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_session_detail)
        applySystemBarsPaddingToRoot()
        
        setupScreenKeepOn()
        
        // Load data from Intent first
        trackId = intent.getStringExtra("trackId") ?: ""
        outingNumber = intent.getIntExtra("outingNumber", 1)
        totalLaps = intent.getStringExtra("totalLaps")?.toIntOrNull() ?: 0
        
        android.util.Log.d("TrackSessionDetailActivity", "onCreate: trackId='$trackId', outingNumber=$outingNumber, totalLaps=$totalLaps")
        
        // If Intent has data, save it to SharedPreferences
        if (trackId.isNotEmpty()) {
            android.util.Log.d("TrackSessionDetailActivity", "Saving session data to prefs")
            saveSessionDataToPrefs()
        } else {
            // Load from SharedPreferences if Intent is empty
            android.util.Log.d("TrackSessionDetailActivity", "Loading session data from prefs")
            loadSessionDataFromPrefs()
        }
        
        initializeViews()
        setupClickListeners()
        loadSessionData()
        setupLapClickListeners()
    }
    
    private fun setupScreenKeepOn() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        updateScreenKeepOn(prefs.getBoolean("always_on_display", false))

        prefs.registerOnSharedPreferenceChangeListener { shared, key ->
            if (key == "always_on_display") {
                updateScreenKeepOn(shared.getBoolean(key, false))
            }
        }
    }

    private fun updateScreenKeepOn(keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Reload data every time the activity becomes visible
        android.util.Log.d("TrackSessionDetailActivity", "onResume: reloading data")
        loadSessionData()
        setupLapClickListeners()
    }
    
    private fun saveSessionDataToPrefs() {
        val prefs = getSharedPreferences("track_session_detail", MODE_PRIVATE)
        prefs.edit().apply {
            putString("trackId", trackId)
            putInt("outingNumber", outingNumber)
            putInt("totalLaps", totalLaps)
            putString("date", intent.getStringExtra("date") ?: "")
            putString("time", intent.getStringExtra("time") ?: "")
            putString("duration", intent.getStringExtra("duration") ?: "")
            putString("trackName", intent.getStringExtra("trackName") ?: "")
            putString("bestLapTime", intent.getStringExtra("bestLapTime") ?: "")
            putString("maxSpeed", intent.getStringExtra("maxSpeed") ?: "")
            putString("maxAcceleration", intent.getStringExtra("maxAcceleration") ?: "")
            putString("maxCorneringG", intent.getStringExtra("maxCorneringG") ?: "")
            apply()
        }
    }
    
    private fun loadSessionDataFromPrefs() {
        val prefs = getSharedPreferences("track_session_detail", MODE_PRIVATE)
        trackId = prefs.getString("trackId", "") ?: ""
        outingNumber = prefs.getInt("outingNumber", 1)
        totalLaps = prefs.getInt("totalLaps", 0)
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        tvSessionDate = findViewById(R.id.tvSessionDate)
        tvSessionTime = findViewById(R.id.tvSessionTime)
        tvSessionDuration = findViewById(R.id.tvSessionDuration)
        tvTrackName = findViewById(R.id.tvTrackName)
        tvTotalLaps = findViewById(R.id.tvTotalLaps)
        tvVehicleName = findViewById(R.id.tvVehicleName)
        tvBestLapTime = findViewById(R.id.tvBestLapTime)
        tvMinSpeed = findViewById(R.id.tvMinSpeed)
        tvMaxSpeed = findViewById(R.id.tvMaxSpeed)
        tvMaxAcceleration = findViewById(R.id.tvMaxAcceleration)
        tvMaxCornering = findViewById(R.id.tvMaxCornering)
        tvTotalLapsLabel = findViewById(R.id.tvTotalLapsLabel)
        tvBestTimeLabel = findViewById(R.id.tvBestTimeLabel)
        tvLapsSectionTitle = findViewById(R.id.tvLapsSectionTitle)
        cardLaps = findViewById(R.id.cardLaps)
        
        // Initialize lap views
        llLapsContainer = findViewById(R.id.llLapsContainer)
        tvNoLaps = findViewById(R.id.tvNoLaps)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupLapClickListeners() {
        if (isPointToPointSession) {
            setupPointToPointRunDetails()
            return
        }

        val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val lapTimes = mutableListOf<String>()
        
        // Load lap times from SharedPreferences
        for (i in 1..totalLaps) {
            val lapTime = sharedPrefs.getString("${trackId}_outing_${outingNumber}_lap_${i}", "--:--.---") ?: "--:--.---"
            lapTimes.add(lapTime)
        }
        
        // Clear existing lap views
        llLapsContainer.removeAllViews()
        
        if (lapTimes.isEmpty() || lapTimes.all { it == "--:--.---" }) {
            // Show "no laps" message
            tvNoLaps.visibility = android.view.View.VISIBLE
            return
        } else {
            tvNoLaps.visibility = android.view.View.GONE
        }
        
        // Find best and worst lap times
        val bestLapIndex = findBestLapIndex(lapTimes)
        val worstLapIndex = findWorstLapIndex(lapTimes)
        
        // Create dynamic lap views
        lapTimes.forEachIndexed { index, lapTime ->
            if (lapTime != "--:--.---") {
                val lapView = createLapView(index + 1, lapTime, index == bestLapIndex, index == worstLapIndex)
                llLapsContainer.addView(lapView)
            }
        }
    }

    private fun setupPointToPointRunDetails() {
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val runTime = prefs.getString("${trackId}_outing_${outingNumber}_best_lap", "--:--.---") ?: "--:--.---"

        llLapsContainer.removeAllViews()
        tvNoLaps.visibility = android.view.View.GONE

        if (runTime == "--:--.---") {
            tvNoLaps.text = "Няма run данни"
            tvNoLaps.visibility = android.view.View.VISIBLE
            return
        }

        val runView = createLapView(
            lapNumber = 1,
            lapTime = runTime,
            isBest = true,
            isWorst = false
        )
        llLapsContainer.addView(runView)
    }
    
    private fun createLapView(lapNumber: Int, lapTime: String, isBest: Boolean, isWorst: Boolean): LinearLayout {
        val inflater = layoutInflater
        val lapView = inflater.inflate(R.layout.lap_item_template, llLapsContainer, false) as LinearLayout
        
        val tvLapNumber = lapView.findViewById<TextView>(R.id.tvLapNumber)
        val tvLapTime = lapView.findViewById<TextView>(R.id.tvLapTime)
        val tvTrophy = lapView.findViewById<TextView>(R.id.tvTrophy)
        
        // Set lap number
        tvLapNumber.text = if (isPointToPointSession) "RUN" else lapNumber.toString()
        
        // Set lap time
        tvLapTime.text = lapTime
        
        // Set colors and trophy
        when {
            isPointToPointSession -> {
                tvLapNumber.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                tvLapTime.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                tvTrophy.visibility = android.view.View.GONE
            }
            isBest -> {
                tvLapNumber.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                tvLapTime.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                tvTrophy.visibility = android.view.View.VISIBLE
            }
            isWorst -> {
                tvLapNumber.setTextColor(android.graphics.Color.parseColor("#F44336"))
                tvLapTime.setTextColor(android.graphics.Color.parseColor("#F44336"))
                tvTrophy.visibility = android.view.View.GONE
            }
            else -> {
                tvLapNumber.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                tvLapTime.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                tvTrophy.visibility = android.view.View.GONE
            }
        }
        
        // Set click listener
        lapView.setOnClickListener {
            openLapDetail(lapNumber, lapTime)
        }
        
        return lapView
    }
    
    private fun openLapDetail(lapNumber: Int, lapTime: String) {
        val intent = Intent(this, TrackLapDetailActivity::class.java).apply {
            putExtra("lap_number", lapNumber)
            putExtra("lap_time", lapTime)
            putExtra("max_speed", "285 km/h")
            putExtra("max_g_force", "2.8g")
            putExtra("max_cornering", "45.2°")
            putExtra("track_id", extractTrackIdFromSessionId(trackId))
            putExtra("full_session_id", trackId)
            putExtra("outing_number", outingNumber)
            val currentProfileId = ProfileStorage.getSelectedProfileId(this@TrackSessionDetailActivity)
            val profiles = ProfileStorage.loadProfiles(this@TrackSessionDetailActivity)
            val profile = profiles.find { it.id == currentProfileId }
            val isMotorcycle = profile?.vehicleType == Profile.VehicleType.MOTORCYCLE
            putExtra("is_motorcycle", isMotorcycle)
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private fun loadSessionData() {
        // Load from track_outings SharedPreferences (where data is actually saved)
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        
        android.util.Log.d("TrackSessionDetailActivity", "loadSessionData: trackId='$trackId', outingNumber=$outingNumber")
        
        // Not used anymore - removed
        
        // Get data from track_outings using the full sessionId
        val sessionDate = prefs.getString("${trackId}_outing_${outingNumber}_date", "--.--.----") ?: "--.--.----"
        val sessionTime = prefs.getString("${trackId}_outing_${outingNumber}_time", "--:--") ?: "--:--"
        val sessionDuration = prefs.getString("${trackId}_outing_${outingNumber}_duration", "--:--") ?: "--:--"
        val mode = prefs.getString("${trackId}_outing_${outingNumber}_mode", "circuit") ?: "circuit"
        isPointToPointSession = mode == "point_to_point"
        
        android.util.Log.d("TrackSessionDetailActivity", "loadSessionData: sessionDate='$sessionDate', sessionTime='$sessionTime', sessionDuration='$sessionDuration'")
        
        // Extract trackId from sessionId (trackId might be full sessionId)
        val actualTrackId = extractTrackIdFromSessionId(trackId)
        android.util.Log.d("TrackSessionDetailActivity", "loadSessionData: original trackId='$trackId', extracted actualTrackId='$actualTrackId'")
        
        // Get track name from actual trackId
        val trackName = getTrackName(actualTrackId)
        android.util.Log.d("TrackSessionDetailActivity", "loadSessionData: trackName='$trackName'")
        
        // Get vehicle name from active profile
        val vehicleName = getActiveVehicleName()
        android.util.Log.d("TrackSessionDetailActivity", "loadSessionData: vehicleName='$vehicleName'")
        
        val bestLapTime = prefs.getString("${trackId}_outing_${outingNumber}_best_lap", "--:--.---") ?: "--:--.---"
        val minSpeed = "45 km/h" // TODO: Calculate from session data
        val maxSpeed = prefs.getString("${trackId}_outing_${outingNumber}_max_speed", "0.0 km/h") ?: "0.0 km/h"
        val maxAcceleration = prefs.getString("${trackId}_outing_${outingNumber}_max_acceleration", "0.00 G") ?: "0.00 G"
        val maxCornering = prefs.getString("${trackId}_outing_${outingNumber}_max_cornering", "0.00 G") ?: "0.00 G"

        // Update title
        findViewById<TextView>(R.id.tvTitle).text = if (isPointToPointSession) {
            "Run #$outingNumber"
        } else {
            getString(R.string.track_session_title, outingNumber)
        }

        // Update session info
        tvSessionDate.text = sessionDate
        tvSessionTime.text = sessionTime
        tvSessionDuration.text = sessionDuration
        tvTrackName.text = trackName
        tvTotalLaps.text = if (isPointToPointSession) "1" else totalLaps.toString()
        tvVehicleName.text = vehicleName
        tvBestLapTime.text = bestLapTime
        tvMinSpeed.text = minSpeed
        tvMaxSpeed.text = maxSpeed
        tvMaxAcceleration.text = maxAcceleration
        tvMaxCornering.text = maxCornering

        if (isPointToPointSession) {
            tvTotalLapsLabel.text = "Runs"
            tvBestTimeLabel.text = "Run Time"
            tvLapsSectionTitle.text = "Run Details"
            cardLaps.visibility = android.view.View.VISIBLE
        } else {
            tvTotalLapsLabel.text = getString(R.string.track_label_laps)
            tvBestTimeLabel.text = getString(R.string.track_best_time)
            tvLapsSectionTitle.text = getString(R.string.track_laps_section)
            cardLaps.visibility = android.view.View.VISIBLE
        }
    }

    private fun findBestLapIndex(lapTimes: List<String>): Int {
        var bestIndex = 0
        var bestTime = Long.MAX_VALUE
        
        lapTimes.forEachIndexed { index, lapTime ->
            if (lapTime != "--:--.---") {
                val timeInMs = parseLapTime(lapTime)
                if (timeInMs < bestTime) {
                    bestTime = timeInMs
                    bestIndex = index
                }
            }
        }
        
        return bestIndex
    }
    
    private fun findWorstLapIndex(lapTimes: List<String>): Int {
        var worstIndex = 0
        var worstTime = 0L
        
        lapTimes.forEachIndexed { index, lapTime ->
            if (lapTime != "--:--.---") {
                val timeInMs = parseLapTime(lapTime)
                if (timeInMs > worstTime) {
                    worstTime = timeInMs
                    worstIndex = index
                }
            }
        }
        
        return worstIndex
    }
    
    private fun parseLapTime(lapTime: String): Long {
        return try {
            val parts = lapTime.split(":")
            val minutes = parts[0].toLong()
            val secondsParts = parts[1].split(".")
            val seconds = secondsParts[0].toLong()
            val milliseconds = secondsParts[1].toLong()
            
            minutes * 60 * 1000 + seconds * 1000 + milliseconds * 10
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    private fun extractTrackIdFromSessionId(sessionId: String): String {
        android.util.Log.d("TrackSessionDetailActivity", "extractTrackIdFromSessionId: input='$sessionId'")
        
        // Handle different sessionId formats
        val result = when {
            // New format with profileId prefix: "123_serres_circuit_23.09.2025_2004_1758647057766"
            sessionId.matches(Regex("\\d+_.*")) -> {
                android.util.Log.d("TrackSessionDetailActivity", "extractTrackIdFromSessionId: matched profileId format")
                // Remove profileId prefix (everything before first underscore)
                val withoutProfileId = sessionId.substringAfter("_")
                android.util.Log.d("TrackSessionDetailActivity", "extractTrackIdFromSessionId: withoutProfileId='$withoutProfileId'")
                
                // Extract trackId by finding the first known track pattern
                when {
                    withoutProfileId.startsWith("serres_circuit") -> "serres_circuit"
                    withoutProfileId.startsWith("sofia_ring") -> "sofia_ring"
                    withoutProfileId.startsWith("custom_track") -> "custom_track"
                    else -> {
                        // Try to extract until first date pattern
                        val datePattern = Regex("_\\d{2}\\.\\d{2}\\.\\d{4}")
                        val match = datePattern.find(withoutProfileId)
                        if (match != null) {
                            val extracted = withoutProfileId.substring(0, match.range.first)
                            android.util.Log.d("TrackSessionDetailActivity", "extractTrackIdFromSessionId: extracted with date='$extracted'")
                            extracted
                        } else {
                            android.util.Log.d("TrackSessionDetailActivity", "extractTrackIdFromSessionId: no date match, returning withoutProfileId")
                            withoutProfileId
                        }
                    }
                }
            }
            else -> {
                // Simple format: "serres_circuit" or "serres_circuit_..."
                android.util.Log.d("TrackSessionDetailActivity", "extractTrackIdFromSessionId: matched simple format")
                when {
                    sessionId.startsWith("serres_circuit") -> "serres_circuit"
                    sessionId.startsWith("sofia_ring") -> "sofia_ring"
                    sessionId.startsWith("custom_track") -> "custom_track"
                    else -> sessionId
                }
            }
        }
        
        android.util.Log.d("TrackSessionDetailActivity", "extractTrackIdFromSessionId: final result='$result'")
        return result
    }
    
    private fun getTrackName(trackId: String): String {
        android.util.Log.d("TrackSessionDetailActivity", "getTrackName: trackId='$trackId'")
        val name = when (trackId) {
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
        android.util.Log.d("TrackSessionDetailActivity", "getTrackName: returning '$name'")
        return name
    }
    
    private fun getActiveVehicleName(): String {
        try {
            // Get active profile from ProfilePrefs
            val profilePrefs = getSharedPreferences("ProfilePrefs", MODE_PRIVATE)
            val activeProfileId = profilePrefs.getLong("selected_profile_id", -1L)
            
            android.util.Log.d("TrackSessionDetailActivity", "getActiveVehicleName: activeProfileId=$activeProfileId")
            
            if (activeProfileId != -1L) {
                // Load profiles from ProfilePrefs
                val profilesJson = profilePrefs.getString("profiles", null)
                if (profilesJson != null) {
                    val gson = com.google.gson.Gson()
                    val type = object : com.google.gson.reflect.TypeToken<MutableList<com.example.clinometer.Profile>>() {}.type
                    val profiles = gson.fromJson<MutableList<com.example.clinometer.Profile>>(profilesJson, type)
                    
                    val activeProfile = profiles.find { it.id == activeProfileId }
                    if (activeProfile != null && activeProfile.name.isNotEmpty()) {
                        android.util.Log.d("TrackSessionDetailActivity", "getActiveVehicleName: found active profile vehicle='${activeProfile.name}'")
                        return activeProfile.name
                    }
                }
            }
            
            // Fallback: get any profile with vehicle
            val profilesJson = profilePrefs.getString("profiles", null)
            if (profilesJson != null) {
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<MutableList<com.example.clinometer.Profile>>() {}.type
                val profiles = gson.fromJson<MutableList<com.example.clinometer.Profile>>(profilesJson, type)
                
                val profileWithVehicle = profiles.find { it.name.isNotEmpty() }
                if (profileWithVehicle != null) {
                    android.util.Log.d("TrackSessionDetailActivity", "getActiveVehicleName: found fallback vehicle='${profileWithVehicle.name}'")
                    return profileWithVehicle.name
                }
            }
            
            android.util.Log.d("TrackSessionDetailActivity", "getActiveVehicleName: no profiles found")
        } catch (e: Exception) {
            android.util.Log.e("TrackSessionDetailActivity", "getActiveVehicleName: error loading profiles", e)
        }
        
        return "Няма превозно средство"
    }

    override fun onBackPressed() {
        // ✅ Връщаме се към MainContainerActivity с правилния fragment (Track)
        val intent = Intent(this, MainContainerActivity::class.java).apply {
            putExtra(MainContainerActivity.EXTRA_INITIAL_PAGE, MainContainerActivity.PAGE_TRACK)
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }
}
