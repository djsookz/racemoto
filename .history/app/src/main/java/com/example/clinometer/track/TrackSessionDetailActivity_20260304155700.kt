package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.preference.PreferenceManager
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.main.MainContainerActivity
import com.example.clinometer.main.map.MapActivity
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.settings.UnitsManager
import com.example.clinometer.track.TrackMapExtras
import com.google.gson.Gson
import com.google.android.material.button.MaterialButton

class TrackSessionDetailActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    private lateinit var btnBack: MaterialButton
    private lateinit var tvSessionDate: TextView
    private lateinit var tvSessionTime: TextView
    private lateinit var tvTrackName: TextView
    private lateinit var tvTotalLaps: TextView
    private lateinit var tvVehicleName: TextView
    private lateinit var tvBestLapTime: TextView
    private lateinit var tvMinSpeed: TextView
    private lateinit var tvMaxSpeed: TextView
    private lateinit var tvAvgSpeed: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvMaxAcceleration: TextView
    private lateinit var tvMaxBraking: TextView
    private lateinit var tvMaxCorneringLeftLabel: TextView
    private lateinit var tvMaxCorneringRightLabel: TextView
    private lateinit var tvMaxCorneringLeft: TextView
    private lateinit var tvMaxCorneringRight: TextView
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
        tvTrackName = findViewById(R.id.tvTrackName)
        tvTotalLaps = findViewById(R.id.tvTotalLaps)
        tvVehicleName = findViewById(R.id.tvVehicleName)
        tvBestLapTime = findViewById(R.id.tvBestLapTime)
        tvMinSpeed = findViewById(R.id.tvMinSpeed)
        tvMaxSpeed = findViewById(R.id.tvMaxSpeed)
        tvAvgSpeed = findViewById(R.id.tvAvgSpeed)
        tvDistance = findViewById(R.id.tvDistance)
        tvMaxAcceleration = findViewById(R.id.tvMaxAcceleration)
        tvMaxBraking = findViewById(R.id.tvMaxBraking)
        tvMaxCorneringLeftLabel = findViewById(R.id.tvMaxCorneringLeftLabel)
        tvMaxCorneringRightLabel = findViewById(R.id.tvMaxCorneringRightLabel)
        tvMaxCorneringLeft = findViewById(R.id.tvMaxCorneringLeft)
        tvMaxCorneringRight = findViewById(R.id.tvMaxCorneringRight)
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
        val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val parsedLapData = loadLapDataForDetails(sharedPrefs, lapNumber)

        if (parsedLapData == null || parsedLapData.routePoints.isEmpty()) {
            Toast.makeText(this, "Lap data unavailable for this run", Toast.LENGTH_SHORT).show()
            return
        }

        val normalizedPoints = normalizeRoutePointsForMap(parsedLapData.routePoints)
        if (normalizedPoints.isEmpty()) {
            Toast.makeText(this, "Lap data unavailable for this run", Toast.LENGTH_SHORT).show()
            return
        }

        val currentProfileId = ProfileStorage.getSelectedProfileId(this@TrackSessionDetailActivity)
        val profiles = ProfileStorage.loadProfiles(this@TrackSessionDetailActivity)
        val profile = profiles.find { it.id == currentProfileId }
        val isMotorcycle = profile?.vehicleType == Profile.VehicleType.MOTORCYCLE
        val trackIdForName = extractTrackIdFromSessionId(trackId)
        val trackDisplayName = getTrackName(trackIdForName)
        val durationMs = resolveDurationMs(parsedLapData, normalizedPoints, lapTime)
        val distanceKm = calculateDistanceKm(normalizedPoints)
        val maxSpeed = normalizedPoints.maxOfOrNull { it.speed } ?: 0f
        val maxLeftAngle = normalizedPoints.filter { it.angle < 0f }.minByOrNull { it.angle }?.angle?.let { kotlin.math.abs(it) } ?: 0f
        val maxRightAngle = normalizedPoints.filter { it.angle > 0f }.maxByOrNull { it.angle }?.angle ?: 0f
        val title = if (isPointToPointSession) {
            "Run #$lapNumber"
        } else {
            "Lap #$lapNumber"
        }

        val raceForMap = Race(
            id = -((System.currentTimeMillis() % 1_000_000_000L) + lapNumber),
            profileId = currentProfileId,
            routePoints = normalizedPoints,
            timestamp = normalizedPoints.firstOrNull()?.absoluteTime ?: System.currentTimeMillis(),
            duration = durationMs,
            absoluteTimestamp = normalizedPoints.firstOrNull()?.absoluteTime ?: System.currentTimeMillis(),
            maxLeftAngle = maxLeftAngle,
            maxRightAngle = maxRightAngle,
            maxSpeed = maxSpeed,
            name = title,
            trackName = trackDisplayName,
            distance = distanceKm
        )

        val intent = Intent(this, MapActivity::class.java).apply {
            putExtra(MapActivity.EXTRA_INLINE_RACE, raceForMap)
            putParcelableArrayListExtra(MapActivity.EXTRA_INLINE_ROUTE_POINTS, ArrayList(normalizedPoints))
            putExtra(MapActivity.EXTRA_RETURN_TO_PREVIOUS, true)
            putExtra(TrackMapExtras.EXTRA_TRACK_CONTEXT, true)
            putExtra(TrackMapExtras.EXTRA_TRACK_ID, trackIdForName)
            putExtra(TrackMapExtras.EXTRA_TRACK_NAME, trackDisplayName)
            putExtra(TrackMapExtras.EXTRA_TRACK_IS_MOTORCYCLE, isMotorcycle)
            putExtra(TrackMapExtras.EXTRA_TRACK_SESSION_ID, trackId)
            putExtra(TrackMapExtras.EXTRA_TRACK_LAP_NUMBER, lapNumber)
            putExtra(TrackMapExtras.EXTRA_TRACK_OUTING_NUMBER, outingNumber)
            putExtra(TrackMapExtras.EXTRA_TRACK_IS_POINT_TO_POINT, isPointToPointSession)
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private fun loadLapDataForDetails(sharedPrefs: android.content.SharedPreferences, requestedLapNumber: Int): LapData? {
        val currentProfileId = ProfileStorage.getSelectedProfileId(this)
        val baseTrackId = extractTrackIdFromSessionId(trackId)
        var actualSessionId = "${currentProfileId}_${baseTrackId}"
        var lapDataCount = sharedPrefs.getInt("${actualSessionId}_outing_${outingNumber}_lap_data_count", 0)

        if (lapDataCount == 0 && trackId.isNotEmpty()) {
            val fullCount = sharedPrefs.getInt("${trackId}_outing_${outingNumber}_lap_data_count", 0)
            if (fullCount > 0) {
                actualSessionId = trackId
                lapDataCount = fullCount
            }
        }

        if (lapDataCount <= 0) return null

        val safeLapNumber = requestedLapNumber.coerceIn(1, lapDataCount)
        val lapJson = sharedPrefs.getString("${actualSessionId}_outing_${outingNumber}_lap_data_${safeLapNumber}", null) ?: return null

        return try {
            Gson().fromJson(lapJson, LapData::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeRoutePointsForMap(points: List<RoutePoint>): List<RoutePoint> {
        if (points.isEmpty()) return emptyList()

        val baseTimestamp = points.first().timestamp
        var normalized = points.map { point ->
            point.copy(
                timestamp = point.timestamp - baseTimestamp,
                absoluteTime = if (point.absoluteTime > 0L) point.absoluteTime else point.timestamp
            )
        }

        if (normalized.all { it.timestamp == normalized.first().timestamp } && normalized.size > 1) {
            normalized = normalized.mapIndexed { index, point ->
                point.copy(timestamp = (index * 100L))
            }
        }

        val span = normalized.last().timestamp - normalized.first().timestamp
        if (span <= 500L && normalized.size > 1) {
            val step = 1000f / normalized.size.toFloat()
            normalized = normalized.mapIndexed { index, point ->
                point.copy(timestamp = (index * step).toLong())
            }
        }

        return normalized
    }

    private fun resolveDurationMs(lapData: LapData, points: List<RoutePoint>, lapTime: String): Long {
        val dataDuration = lapData.endTime - lapData.startTime
        if (dataDuration > 0L) return dataDuration

        val pointSpan = points.last().timestamp - points.first().timestamp
        if (pointSpan > 0L) return pointSpan

        val parsed = parseLapTime(lapTime)
        return if (parsed != Long.MAX_VALUE) parsed else 0L
    }

    private fun calculateDistanceKm(points: List<RoutePoint>): Double {
        if (points.size < 2) return 0.0

        var meters = 0.0
        for (index in 1 until points.size) {
            val prev = points[index - 1].geoPoint
            val current = points[index].geoPoint
            val results = FloatArray(1)
            Location.distanceBetween(
                prev.latitude,
                prev.longitude,
                current.latitude,
                current.longitude,
                results
            )
            meters += results[0].toDouble()
        }
        return meters / 1000.0
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
        
        val sessionProfile = resolveSessionProfile()
        val isMotorcycleSession = sessionProfile?.vehicleType == Profile.VehicleType.MOTORCYCLE

        // Get vehicle name from session profile
        val vehicleName = getActiveVehicleName(sessionProfile)
        android.util.Log.d("TrackSessionDetailActivity", "loadSessionData: vehicleName='$vehicleName'")
        
        val bestLapTime = prefs.getString("${trackId}_outing_${outingNumber}_best_lap", "--:--.---") ?: "--:--.---"
        val computedMinSpeed = computeSessionMinSpeed(prefs)
        val minSpeed = if (computedMinSpeed != null) {
            String.format(java.util.Locale.getDefault(), "%.1f km/h", computedMinSpeed)
        } else {
            "0.0 km/h"
        }
        val maxSpeed = prefs.getString("${trackId}_outing_${outingNumber}_max_speed", "0.0 km/h") ?: "0.0 km/h"
        val maxAcceleration = prefs.getString("${trackId}_outing_${outingNumber}_max_acceleration", "0.00 G") ?: "0.00 G"
        val maxBraking = prefs.getString("${trackId}_outing_${outingNumber}_max_braking", "0.00 G") ?: "0.00 G"
        val totalDistanceKm = computeSessionDistanceKm(prefs)
        val sessionDurationMs = parseSessionDurationMs(sessionDuration)
        val avgSpeedKmh = if (sessionDurationMs > 0L) {
            (totalDistanceKm * 3_600_000.0) / sessionDurationMs.toDouble()
        } else {
            0.0
        }
        val displayDistance = UnitsManager.formatDistance(totalDistanceKm, this, 2)
        val displayAvgSpeed = UnitsManager.formatSpeed(avgSpeedKmh.toFloat(), this, 0)
        val maxCorneringLegacy = prefs.getString("${trackId}_outing_${outingNumber}_max_cornering", "0.00 G") ?: "0.00 G"
        val maxCorneringLeft = prefs.getString("${trackId}_outing_${outingNumber}_max_cornering_left", maxCorneringLegacy) ?: maxCorneringLegacy
        val maxCorneringRight = prefs.getString("${trackId}_outing_${outingNumber}_max_cornering_right", maxCorneringLegacy) ?: maxCorneringLegacy
        val maxLeanDisplay = prefs.getString("${trackId}_outing_${outingNumber}_max_lean_angle", "0.0°") ?: "0.0°"
        val maxLeanLeftStored = prefs.getString("${trackId}_outing_${outingNumber}_max_lean_left", null)
        val maxLeanRightStored = prefs.getString("${trackId}_outing_${outingNumber}_max_lean_right", null)

        // Update title
        findViewById<TextView>(R.id.tvTitle).text = if (isPointToPointSession) {
            "Run #$outingNumber"
        } else {
            getString(R.string.track_session_title, outingNumber)
        }

        // Update session info
        tvSessionDate.text = sessionDate
        tvSessionTime.text = sessionTime
        tvTrackName.text = trackName
        tvTotalLaps.text = if (isPointToPointSession) "1" else totalLaps.toString()
        tvVehicleName.text = vehicleName
        tvBestLapTime.text = bestLapTime
        tvMinSpeed.text = minSpeed
        tvMaxSpeed.text = maxSpeed
        tvAvgSpeed.text = displayAvgSpeed
        tvDistance.text = displayDistance
        tvMaxAcceleration.text = maxAcceleration
        tvMaxBraking.text = maxBraking

        if (isMotorcycleSession) {
            tvMaxCorneringLeftLabel.text = getString(R.string.track_max_lean_left)
            tvMaxCorneringRightLabel.text = getString(R.string.track_max_lean_right)

            val storedLeanLeft = maxLeanLeftStored?.let { parseDisplayedNumeric(it) }
            val storedLeanRight = maxLeanRightStored?.let { parseDisplayedNumeric(it) }

            if ((storedLeanLeft ?: 0f) > 0f || (storedLeanRight ?: 0f) > 0f) {
                tvMaxCorneringLeft.text = formatLeanAngleRounded(storedLeanLeft ?: 0f)
                tvMaxCorneringRight.text = formatLeanAngleRounded(storedLeanRight ?: 0f)
            } else {
                val leanExtremes = computeSessionLeanExtremes(prefs)
                if (leanExtremes != null) {
                    tvMaxCorneringLeft.text = formatLeanAngleRounded(leanExtremes.first)
                    tvMaxCorneringRight.text = formatLeanAngleRounded(leanExtremes.second)
                } else {
                    val fallbackLean = parseDisplayedNumeric(maxLeanDisplay) ?: 0f
                    val fallbackLeanText = formatLeanAngleRounded(fallbackLean)
                    tvMaxCorneringLeft.text = fallbackLeanText
                    tvMaxCorneringRight.text = fallbackLeanText
                }
            }
        } else {
            tvMaxCorneringLeftLabel.text = getString(R.string.track_max_cornering_left)
            tvMaxCorneringRightLabel.text = getString(R.string.track_max_cornering_right)
            tvMaxCorneringLeft.text = maxCorneringLeft
            tvMaxCorneringRight.text = maxCorneringRight
        }

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

    private fun computeSessionMinSpeed(prefs: android.content.SharedPreferences): Float? {
        val lapDataCount = prefs.getInt("${trackId}_outing_${outingNumber}_lap_data_count", 0)
        if (lapDataCount <= 0) return null

        var minSpeed = Float.POSITIVE_INFINITY
        val gson = Gson()

        for (lapIndex in 1..lapDataCount) {
            val lapJson = prefs.getString("${trackId}_outing_${outingNumber}_lap_data_${lapIndex}", null) ?: continue
            val lapData = try {
                gson.fromJson(lapJson, LapData::class.java)
            } catch (_: Exception) {
                null
            } ?: continue

            lapData.routePoints.forEach { point ->
                val speed = point.speed
                if (speed.isFinite() && speed >= 0f) {
                    minSpeed = kotlin.math.min(minSpeed, speed)
                }
            }
        }

        return if (minSpeed.isFinite()) minSpeed else null
    }

    private fun computeSessionDistanceKm(prefs: android.content.SharedPreferences): Double {
        val lapDataCount = prefs.getInt("${trackId}_outing_${outingNumber}_lap_data_count", 0)
        if (lapDataCount <= 0) return 0.0

        var totalDistanceKm = 0.0
        val gson = Gson()

        for (lapIndex in 1..lapDataCount) {
            val lapJson = prefs.getString("${trackId}_outing_${outingNumber}_lap_data_${lapIndex}", null) ?: continue
            val lapData = try {
                gson.fromJson(lapJson, LapData::class.java)
            } catch (_: Exception) {
                null
            } ?: continue

            if (lapData.routePoints.size > 1) {
                totalDistanceKm += calculateDistanceKm(lapData.routePoints)
            }
        }

        return totalDistanceKm
    }

    private fun parseSessionDurationMs(duration: String): Long {
        val trimmed = duration.trim()
        if (trimmed.isEmpty() || trimmed == "--:--") return 0L

        return try {
            val mainParts = trimmed.split(":")
            val minutes = mainParts.getOrNull(0)?.toLongOrNull() ?: return 0L
            val secParts = (mainParts.getOrNull(1) ?: return 0L).split(".")
            val seconds = secParts.getOrNull(0)?.toLongOrNull() ?: return 0L
            val centiseconds = secParts.getOrNull(1)?.toLongOrNull() ?: 0L

            (minutes * 60_000L) + (seconds * 1_000L) + (centiseconds * 10L)
        } catch (_: Exception) {
            0L
        }
    }

    private fun extractTrackIdFromSessionId(sessionId: String): String {
        return TrackSessionIdUtils.extractTrackIdFromSessionId(this, sessionId)
    }
    
    private fun getTrackName(trackId: String): String {
        android.util.Log.d("TrackSessionDetailActivity", "getTrackName: trackId='$trackId'")
        val normalizedTrackId = extractTrackIdFromSessionId(trackId)
        val officialName = TrackManager(this).getTrackById(normalizedTrackId)?.name

        val name = when {
            officialName != null -> officialName
            normalizedTrackId == "custom_track" -> getString(R.string.track_name_custom)
            normalizedTrackId.startsWith("custom_") -> {
                val customTrack = com.example.clinometer.tracking.CustomTrackStorage.loadCustomTrack(this, normalizedTrackId)
                customTrack?.name ?: getString(R.string.track_name_unknown)
            }
            else -> getString(R.string.track_name_unknown)
        }
        android.util.Log.d("TrackSessionDetailActivity", "getTrackName: returning '$name'")
        return name
    }
    
    private fun getActiveVehicleName(sessionProfile: Profile?): String {
        sessionProfile?.name?.takeIf { it.isNotEmpty() }?.let {
            return it
        }

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

    private fun resolveSessionProfile(): Profile? {
        val allProfiles = ProfileStorage.loadProfiles(this)
        if (allProfiles.isEmpty()) return null

        val sessionProfileId = trackId.substringBefore("_", "").toLongOrNull()
        if (sessionProfileId != null) {
            allProfiles.find { it.id == sessionProfileId }?.let { return it }
        }

        val selectedProfileId = ProfileStorage.getSelectedProfileId(this)
        return allProfiles.find { it.id == selectedProfileId } ?: allProfiles.firstOrNull()
    }

    private fun computeSessionLeanExtremes(prefs: android.content.SharedPreferences): Pair<Float, Float>? {
        val lapDataCount = prefs.getInt("${trackId}_outing_${outingNumber}_lap_data_count", 0)
        if (lapDataCount <= 0) return null

        val gson = Gson()
        var maxLeanLeft = 0f
        var maxLeanRight = 0f

        for (lapIndex in 1..lapDataCount) {
            val lapJson = prefs.getString("${trackId}_outing_${outingNumber}_lap_data_${lapIndex}", null) ?: continue
            val lapData = try {
                gson.fromJson(lapJson, LapData::class.java)
            } catch (_: Exception) {
                null
            } ?: continue

            lapData.routePoints.forEach { point ->
                val angle = point.angle
                if (!angle.isFinite()) return@forEach

                if (angle < 0f) {
                    maxLeanLeft = kotlin.math.max(maxLeanLeft, kotlin.math.abs(angle))
                } else if (angle > 0f) {
                    maxLeanRight = kotlin.math.max(maxLeanRight, angle)
                }
            }
        }

        return if (maxLeanLeft > 0f || maxLeanRight > 0f) {
            maxLeanLeft to maxLeanRight
        } else {
            null
        }
    }

    private fun parseDisplayedNumeric(value: String): Float? {
        val normalized = value
            .replace("km/h", "", ignoreCase = true)
            .replace("G", "", ignoreCase = true)
            .replace("°", "")
            .replace(",", ".")
            .trim()
        return normalized.toFloatOrNull()
    }

    private fun formatLeanAngleRounded(value: Float): String {
        val rounded = kotlin.math.roundToInt(value).coerceAtLeast(0)
        return "$rounded°"
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
