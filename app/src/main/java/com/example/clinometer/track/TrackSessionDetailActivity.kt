package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.location.Location
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.webkit.MimeTypeMap
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.main.MainContainerActivity
import com.example.clinometer.main.map.MapActivity
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.settings.UnitsManager
import com.example.clinometer.track.TrackMapExtras
import com.example.clinometer.track.enrichRoutePointsWithLeanPeaks
import com.google.gson.Gson
import com.google.android.material.button.MaterialButton
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class TrackSessionDetailActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    private lateinit var btnBack: MaterialButton
    private lateinit var tvTitle: TextView
    private lateinit var tvTitleMeta: TextView
    private lateinit var tvTitleSessionName: TextView
    private lateinit var tvHeaderProfileName: TextView
    private lateinit var tvHeaderBestLapTime: TextView
    private lateinit var tvHeaderBestLapMeta: TextView
    private lateinit var tvHeaderMaxSpeedValue: TextView
    private lateinit var tvHeaderAvgLapValue: TextView
    private lateinit var tvHeaderTotalTimeValue: TextView
    private lateinit var ivWeatherSessionTemp: ImageView
    private lateinit var ivWeatherSessionHumidity: ImageView
    private lateinit var ivWeatherSessionWind: ImageView
    private lateinit var tvWeatherSessionTemp: TextView
    private lateinit var tvWeatherSessionHumidity: TextView
    private lateinit var tvWeatherSessionWind: TextView
    private lateinit var tvLapTimesCount: TextView
    private lateinit var tvSessionOverviewDistance: TextView
    private lateinit var tvSessionOverviewAvgSpeed: TextView
    private lateinit var tvSessionOverviewConsistency: TextView
    private lateinit var tvSessionOverviewBestToAvg: TextView
    private lateinit var rowSessionOverviewSecondary: LinearLayout
    private lateinit var viewSessionOverviewBottomSeparator: View
    private lateinit var viewSessionOverviewMotoSeparator: View
    private lateinit var rowSessionOverviewMotoLeans: LinearLayout
    private lateinit var tvSessionOverviewMaxLeanLeft: TextView
    private lateinit var tvSessionOverviewMaxLeanRight: TextView
    private lateinit var tvMaxAcceleration: TextView
    private lateinit var tvMaxBraking: TextView
    private lateinit var tvMaxCorneringLeftLabel: TextView
    private lateinit var tvMaxCorneringRightLabel: TextView
    private lateinit var tvMaxCorneringLeft: TextView
    private lateinit var tvMaxCorneringRight: TextView
    private lateinit var tvLapsSectionTitle: TextView
    private lateinit var cardLaps: CardView
    private lateinit var cardSessionVideo: CardView
    private lateinit var ivSessionVideoThumbnail: ImageView
    private lateinit var tvSessionVideoTitle: TextView
    private lateinit var tvSessionVideoMeta: TextView
    private lateinit var btnSessionVideoOpen: MaterialButton
    private lateinit var btnSessionVideoExport: MaterialButton
    private lateinit var btnSessionVideoRender: MaterialButton
    private lateinit var flSessionVideoPreview: View
    
    // Lap click listeners
    private lateinit var llLapsContainer: LinearLayout
    private lateinit var tvNoLaps: TextView
    
    // Store data to survive activity recreation
    private var trackId: String = ""
    private var outingNumber: Int = 1
    private var totalLaps: Int = 0
    private var isPointToPointSession: Boolean = false
    private var sessionVideoUri: Uri? = null
    private var sessionVideoFile: File? = null
    private var sessionVideoStartOffsetMs: Long = 0L
    private var sessionVideoElapsedAtStartMs: Long = 0L
    private var sessionVideoOverlayExported: Boolean = true
    private var hasManualVideoExportMetadata = false
    private var isManualVideoExportRunning = false
    private var activeVideoOverlayExporter: TrackSessionVideoOverlayExporter? = null
    private var exportProgressDialog: AlertDialog? = null
    private var sessionIsMotorcycle = false

    private data class LapRowEntry(
        val lapNumber: Int,
        val lapTime: String,
        val lapMs: Long?,
        val lapMaxV: String
    )

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
        tvTitle = findViewById(R.id.tvTitle)
        tvTitleMeta = findViewById(R.id.tvTitleMeta)
        tvTitleSessionName = findViewById(R.id.tvTitleSessionName)
        tvHeaderProfileName = findViewById(R.id.tvHeaderProfileName)
        tvHeaderBestLapTime = findViewById(R.id.tvHeaderBestLapTime)
        tvHeaderBestLapMeta = findViewById(R.id.tvHeaderBestLapMeta)
        tvHeaderMaxSpeedValue = findViewById(R.id.tvHeaderMaxSpeedValue)
        tvHeaderAvgLapValue = findViewById(R.id.tvHeaderAvgLapValue)
        tvHeaderTotalTimeValue = findViewById(R.id.tvHeaderTotalTimeValue)
        ivWeatherSessionTemp = findViewById(R.id.ivWeatherSessionTemp)
        ivWeatherSessionHumidity = findViewById(R.id.ivWeatherSessionHumidity)
        ivWeatherSessionWind = findViewById(R.id.ivWeatherSessionWind)
        tvWeatherSessionTemp = findViewById(R.id.tvWeatherSessionTemp)
        tvWeatherSessionHumidity = findViewById(R.id.tvWeatherSessionHumidity)
        tvWeatherSessionWind = findViewById(R.id.tvWeatherSessionWind)
        tvLapTimesCount = findViewById(R.id.tvLapTimesCount)
        tvSessionOverviewDistance = findViewById(R.id.tvSessionOverviewDistance)
        tvSessionOverviewAvgSpeed = findViewById(R.id.tvSessionOverviewAvgSpeed)
        tvSessionOverviewConsistency = findViewById(R.id.tvSessionOverviewConsistency)
        tvSessionOverviewBestToAvg = findViewById(R.id.tvSessionOverviewBestToAvg)
        rowSessionOverviewSecondary = findViewById(R.id.rowSessionOverviewSecondary)
        viewSessionOverviewBottomSeparator = findViewById(R.id.viewSessionOverviewBottomSeparator)
        viewSessionOverviewMotoSeparator = findViewById(R.id.viewSessionOverviewMotoSeparator)
        rowSessionOverviewMotoLeans = findViewById(R.id.rowSessionOverviewMotoLeans)
        tvSessionOverviewMaxLeanLeft = findViewById(R.id.tvSessionOverviewMaxLeanLeft)
        tvSessionOverviewMaxLeanRight = findViewById(R.id.tvSessionOverviewMaxLeanRight)
        tvMaxAcceleration = findViewById(R.id.tvMaxAcceleration)
        tvMaxBraking = findViewById(R.id.tvMaxBraking)
        tvMaxCorneringLeftLabel = findViewById(R.id.tvMaxCorneringLeftLabel)
        tvMaxCorneringRightLabel = findViewById(R.id.tvMaxCorneringRightLabel)
        tvMaxCorneringLeft = findViewById(R.id.tvMaxCorneringLeft)
        tvMaxCorneringRight = findViewById(R.id.tvMaxCorneringRight)
        tvLapsSectionTitle = findViewById(R.id.tvLapsSectionTitle)
        cardLaps = findViewById(R.id.cardLaps)
        cardSessionVideo = findViewById(R.id.cardSessionVideo)
        ivSessionVideoThumbnail = findViewById(R.id.ivSessionVideoThumbnail)
        tvSessionVideoTitle = findViewById(R.id.tvSessionVideoTitle)
        tvSessionVideoMeta = findViewById(R.id.tvSessionVideoMeta)
        btnSessionVideoOpen = findViewById(R.id.btnSessionVideoOpen)
        btnSessionVideoExport = findViewById(R.id.btnSessionVideoExport)
        btnSessionVideoRender = findViewById(R.id.btnSessionVideoRender)
        flSessionVideoPreview = findViewById(R.id.flSessionVideoPreview)
        
        // Initialize lap views
        llLapsContainer = findViewById(R.id.llLapsContainer)
        tvNoLaps = findViewById(R.id.tvNoLaps)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            onBackPressed()
        }
        flSessionVideoPreview.setOnClickListener {
            openSessionVideo()
        }
        btnSessionVideoOpen.setOnClickListener {
            openSessionVideo()
        }
        btnSessionVideoExport.setOnClickListener {
            exportSessionVideo()
        }
        btnSessionVideoRender.setOnClickListener {
            renderSessionVideo()
        }
    }
    
    private fun setupLapClickListeners() {
        if (isPointToPointSession) {
            setupPointToPointRunDetails()
            return
        }

        val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val lapEntries = mutableListOf<LapRowEntry>()
        
        // Load lap times from SharedPreferences
        for (i in 1..totalLaps) {
            val lapTime = sharedPrefs.getString("${trackId}_outing_${outingNumber}_lap_${i}", "--:--.---") ?: "--:--.---"
            lapEntries.add(
                LapRowEntry(
                    lapNumber = i,
                    lapTime = lapTime,
                    lapMs = parseFlexibleTimeToMs(lapTime),
                    lapMaxV = resolveLapMaxSpeedDisplay(sharedPrefs, i)
                )
            )
        }
        
        // Clear existing lap views
        llLapsContainer.removeAllViews()

        val validLapEntries = lapEntries.filter { it.lapMs != null }
        
        if (validLapEntries.isEmpty()) {
            // Show "no laps" message
            tvNoLaps.visibility = android.view.View.VISIBLE
            return
        } else {
            tvNoLaps.visibility = android.view.View.GONE
        }

        val bestLapMs = validLapEntries.minOf { it.lapMs ?: Long.MAX_VALUE }
        val bestLapNumber = validLapEntries.firstOrNull { (it.lapMs ?: Long.MAX_VALUE) == bestLapMs }?.lapNumber
        
        // Create dynamic lap views
        lapEntries.forEach { lapEntry ->
            val lapMs = lapEntry.lapMs
            if (lapMs != null) {
                val isBest = bestLapNumber != null && lapEntry.lapNumber == bestLapNumber
                val deltaMs = if (isBest) 0L else (lapMs - bestLapMs).coerceAtLeast(0L)
                val lapView = createLapView(
                    lapNumber = lapEntry.lapNumber,
                    lapTime = lapEntry.lapTime,
                    lapMaxV = lapEntry.lapMaxV,
                    isBest = isBest,
                    deltaMs = deltaMs
                )
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
            lapMaxV = resolveOutingMaxSpeedDisplay(prefs),
            isBest = true,
            deltaMs = null
        )
        llLapsContainer.addView(runView)
    }
    
    private fun createLapView(
        lapNumber: Int,
        lapTime: String,
        lapMaxV: String,
        isBest: Boolean,
        deltaMs: Long?
    ): LinearLayout {
        val inflater = layoutInflater
        val lapView = inflater.inflate(R.layout.lap_item_template, llLapsContainer, false) as LinearLayout
        
        val tvLapNumber = lapView.findViewById<TextView>(R.id.tvLapNumber)
        val tvLapTime = lapView.findViewById<TextView>(R.id.tvLapTime)
        val tvLapMaxV = lapView.findViewById<TextView>(R.id.tvLapMaxV)
        val tvLapDelta = lapView.findViewById<TextView>(R.id.tvLapDelta)
        
        // Set lap time
        tvLapTime.text = lapTime
        tvLapMaxV.text = lapMaxV
        
        if (isPointToPointSession) {
            tvLapNumber.text = "RUN"
            tvLapNumber.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            tvLapTime.setTextColor(ContextCompat.getColor(this, R.color.track_neon_green))
            tvLapMaxV.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            tvLapDelta.text = "\u2014"
            tvLapDelta.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            lapView.background = ContextCompat.getDrawable(this, R.drawable.bg_stat_item)
        } else {
            tvLapNumber.text = lapNumber.toString()
            tvLapNumber.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary))
            tvLapMaxV.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

            if (isBest) {
                tvLapTime.setTextColor(ContextCompat.getColor(this, R.color.accent_purple))
                tvLapDelta.text = "\uD83D\uDC51"
                tvLapDelta.setTextColor(ContextCompat.getColor(this, R.color.accent_gold))
                lapView.background = ContextCompat.getDrawable(this, R.drawable.stat_item_background_highlight)
            } else {
                val safeDelta = deltaMs ?: 0L
                tvLapTime.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                tvLapDelta.text = formatLapDelta(safeDelta)
                tvLapDelta.setTextColor(
                    ContextCompat.getColor(
                        this,
                        if (safeDelta > 0L) R.color.accent_red else R.color.track_neon_green
                    )
                )
                lapView.background = ContextCompat.getDrawable(this, R.drawable.bg_stat_item)
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

        val enrichedRoutePoints = enrichRoutePointsWithLeanPeaks(parsedLapData)
        val normalizedPoints = normalizeRoutePointsForMap(enrichedRoutePoints)
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
        val lapDataContext = resolveLapDataSessionContext(sharedPrefs) ?: return null
        val safeLapNumber = requestedLapNumber.coerceIn(1, lapDataContext.lapDataCount)
        val lapJson = sharedPrefs.getString(
            "${lapDataContext.sessionId}_outing_${outingNumber}_lap_data_${safeLapNumber}",
            null
        ) ?: return null

        return try {
            Gson().fromJson(lapJson, LapData::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private data class LapDataSessionContext(
        val sessionId: String,
        val lapDataCount: Int
    )

    private fun resolveLapDataSessionContext(sharedPrefs: android.content.SharedPreferences): LapDataSessionContext? {
        val currentProfileId = ProfileStorage.getSelectedProfileId(this)
        val baseTrackId = extractTrackIdFromSessionId(trackId)
        val sessionCandidates = linkedSetOf<String>()

        if (trackId.isNotBlank()) {
            sessionCandidates += trackId
        }
        if (baseTrackId.isNotBlank()) {
            sessionCandidates += "${currentProfileId}_${baseTrackId}"
            sessionCandidates += baseTrackId
        }

        return sessionCandidates.firstNotNullOfOrNull { sessionIdCandidate ->
            val lapDataCount = sharedPrefs.getInt(
                "${sessionIdCandidate}_outing_${outingNumber}_lap_data_count",
                0
            )
            if (lapDataCount > 0) {
                LapDataSessionContext(sessionIdCandidate, lapDataCount)
            } else {
                null
            }
        }
    }

    private inline fun forEachSessionLapData(
        sharedPrefs: android.content.SharedPreferences,
        onLapData: (LapData) -> Unit
    ): Boolean {
        val lapDataContext = resolveLapDataSessionContext(sharedPrefs) ?: return false
        val gson = Gson()
        var foundLapData = false

        for (lapIndex in 1..lapDataContext.lapDataCount) {
            val lapJson = sharedPrefs.getString(
                "${lapDataContext.sessionId}_outing_${outingNumber}_lap_data_${lapIndex}",
                null
            ) ?: continue

            val lapData = try {
                gson.fromJson(lapJson, LapData::class.java)
            } catch (_: Exception) {
                null
            } ?: continue

            foundLapData = true
            onLapData(lapData)
        }

        return foundLapData
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
        val sessionTemperature = prefs.getString("${trackId}_outing_${outingNumber}_temperature", null)
            ?: run {
                val unit = UnitsManager.getTemperatureUnit(this)
                "--${unit.symbol}"
            }
        val sessionHumidity = prefs.getString("${trackId}_outing_${outingNumber}_humidity", "--%") ?: "--%"
        val sessionWindSpeed = prefs.getString("${trackId}_outing_${outingNumber}_wind_speed", "-- km/h") ?: "-- km/h"
        val sessionWeatherIcon = prefs.getInt("${trackId}_outing_${outingNumber}_weather_icon", -1)
        val sessionVideoUri = prefs.getString("${trackId}_outing_${outingNumber}_video_uri", "") ?: ""
        val sessionVideoPath = prefs.getString("${trackId}_outing_${outingNumber}_video_path", "") ?: ""
        val sessionVideoCamera = prefs.getString("${trackId}_outing_${outingNumber}_video_camera", "") ?: ""
        val sessionVideoStartOffsetKey = "${trackId}_outing_${outingNumber}_video_session_start_offset_ms"
        val sessionVideoElapsedAtStartKey = "${trackId}_outing_${outingNumber}_video_session_elapsed_at_start_ms"
        sessionVideoStartOffsetMs = prefs.getLong(sessionVideoStartOffsetKey, 0L).coerceAtLeast(0L)
        sessionVideoElapsedAtStartMs = if (prefs.contains(sessionVideoElapsedAtStartKey)) {
            prefs.getLong(sessionVideoElapsedAtStartKey, 0L)
        } else {
            -sessionVideoStartOffsetMs
        }
        sessionVideoOverlayExported = prefs.getBoolean("${trackId}_outing_${outingNumber}_video_overlay_exported", false)
        hasManualVideoExportMetadata = prefs.contains(sessionVideoElapsedAtStartKey) || prefs.contains(sessionVideoStartOffsetKey)
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
        sessionIsMotorcycle = isMotorcycleSession

        // Get vehicle name from session profile
        val vehicleName = getActiveVehicleName(sessionProfile)
        val profileName = sessionProfile?.name?.takeIf { it.isNotBlank() } ?: vehicleName
        android.util.Log.d("TrackSessionDetailActivity", "loadSessionData: vehicleName='$vehicleName'")
        
        val bestLapTime = prefs.getString("${trackId}_outing_${outingNumber}_best_lap", "--:--.---") ?: "--:--.---"
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
        val leanExtremesFromLaps = computeSessionLeanExtremes(prefs)

        val overviewLeanLeftDisplay = when {
            leanExtremesFromLaps != null -> formatLeanAngleRounded(leanExtremesFromLaps.first)
            maxLeanLeftStored != null -> parseDisplayedNumeric(maxLeanLeftStored)?.let { formatLeanAngleRounded(it) }
            else -> parseDisplayedNumeric(maxLeanDisplay)?.let { formatLeanAngleRounded(it) }
        } ?: "--"

        val overviewLeanRightDisplay = when {
            leanExtremesFromLaps != null -> formatLeanAngleRounded(leanExtremesFromLaps.second)
            maxLeanRightStored != null -> parseDisplayedNumeric(maxLeanRightStored)?.let { formatLeanAngleRounded(it) }
            else -> parseDisplayedNumeric(maxLeanDisplay)?.let { formatLeanAngleRounded(it) }
        } ?: "--"

        val allLapTimes = if (totalLaps > 0) {
            (1..totalLaps).map { lapNumber ->
                prefs.getString("${trackId}_outing_${outingNumber}_lap_${lapNumber}", "--:--.---") ?: "--:--.---"
            }
        } else {
            emptyList()
        }
        val validLapMs = allLapTimes.mapIndexedNotNull { index, lap ->
            parseFlexibleTimeToMs(lap)?.let { Triple(index + 1, lap, it) }
        }
        val validLapDurationsMs = validLapMs.map { it.third.toDouble() }
        val showConsistencyMetrics = validLapDurationsMs.size >= 3
        val bestLapNumberInSession = validLapMs.minByOrNull { it.third }?.first
        val bestLapMsValue = validLapMs.minOfOrNull { it.third }
        val avgLapMs = if (validLapMs.isNotEmpty()) {
            validLapMs.sumOf { it.third } / validLapMs.size
        } else {
            null
        }
        val consistencyDisplay = if (validLapDurationsMs.size >= 3) {
            val meanMs = validLapDurationsMs.average()
            val variance = validLapDurationsMs
                .map { sample ->
                    val diff = sample - meanMs
                    diff * diff
                }
                .average()
            val stdDevSec = sqrt(variance) / 1000.0
            String.format(java.util.Locale.getDefault(), "±%.2f s", stdDevSec)
        } else {
            getString(R.string.track_consistency_not_enough)
        }
        val bestToAvgDisplay = if (avgLapMs != null && bestLapMsValue != null && validLapDurationsMs.size >= 2) {
            val deltaSec = (avgLapMs - bestLapMsValue).coerceAtLeast(0L) / 1000.0
            String.format(java.util.Locale.getDefault(), "+%.2f s", deltaSec)
        } else {
            getString(R.string.track_metric_na)
        }
        val lapsTotalForHeader = if (isPointToPointSession) {
            1
        } else {
            totalLaps.coerceAtLeast(validLapMs.size)
        }

        // Header: track name + session date/time
        tvTitle.text = trackName
        tvTitleMeta.text = "${sessionDate.uppercase()}  •  ${sessionTime.uppercase()}"
        tvTitleSessionName.text = if (isPointToPointSession) {
            "Run #$outingNumber"
        } else {
            getString(R.string.track_session_title, outingNumber)
        }
        tvHeaderProfileName.text = profileName

        tvHeaderBestLapTime.text = bestLapTime
        tvHeaderBestLapMeta.text = if (isPointToPointSession) {
            getString(R.string.track_header_run_meta)
        } else if (bestLapNumberInSession != null && lapsTotalForHeader > 0) {
            highlightBestLapNumber(
                getString(R.string.track_header_lap_meta, bestLapNumberInSession, lapsTotalForHeader),
                bestLapNumberInSession
            )
        } else {
            getString(R.string.track_header_lap_meta_unknown, lapsTotalForHeader.coerceAtLeast(1))
        }
        tvHeaderMaxSpeedValue.text = compactSpeedForHeader(maxSpeed)
        tvHeaderAvgLapValue.text = avgLapMs?.let { formatTimeMs(it) } ?: "--:--.---"
        tvHeaderTotalTimeValue.text = sessionDuration
        tvWeatherSessionTemp.text = sessionTemperature
        tvWeatherSessionHumidity.text = sessionHumidity
        tvWeatherSessionWind.text = sessionWindSpeed
        tvSessionOverviewDistance.text = displayDistance
        tvSessionOverviewAvgSpeed.text = displayAvgSpeed
        tvSessionOverviewConsistency.text = consistencyDisplay
        tvSessionOverviewBestToAvg.text = bestToAvgDisplay
        rowSessionOverviewSecondary.visibility = if (showConsistencyMetrics) View.VISIBLE else View.GONE
        viewSessionOverviewBottomSeparator.visibility = if (showConsistencyMetrics) View.VISIBLE else View.GONE
        rowSessionOverviewMotoLeans.visibility = if (isMotorcycleSession) View.VISIBLE else View.GONE
        viewSessionOverviewMotoSeparator.visibility = if (isMotorcycleSession) View.VISIBLE else View.GONE
        tvSessionOverviewMaxLeanLeft.text = overviewLeanLeftDisplay
        tvSessionOverviewMaxLeanRight.text = overviewLeanRightDisplay
        tvLapTimesCount.text = resources.getQuantityString(
            R.plurals.track_lap_times_count,
            lapsTotalForHeader,
            lapsTotalForHeader
        ).uppercase()

        val humidityPercent = sessionHumidity.filter { it.isDigit() }.toIntOrNull()
        val (weatherIconRes, weatherTintRes) = resolveWeatherIconStyle(sessionWeatherIcon, humidityPercent)
        ivWeatherSessionTemp.setImageResource(weatherIconRes)
        ivWeatherSessionTemp.setColorFilter(ContextCompat.getColor(this, weatherTintRes))
        ivWeatherSessionHumidity.setImageResource(R.drawable.ic_humidity_drop)
        ivWeatherSessionHumidity.setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary))
        ivWeatherSessionWind.setImageResource(R.drawable.ic_wind)
        ivWeatherSessionWind.setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary))

        tvMaxAcceleration.text = maxAcceleration
        tvMaxBraking.text = maxBraking

        if (isMotorcycleSession) {
            tvMaxCorneringLeftLabel.text = getString(R.string.track_max_lean_left)
            tvMaxCorneringRightLabel.text = getString(R.string.track_max_lean_right)
            tvMaxCorneringLeft.text = overviewLeanLeftDisplay
            tvMaxCorneringRight.text = overviewLeanRightDisplay
        } else {
            tvMaxCorneringLeftLabel.text = getString(R.string.track_max_cornering_left)
            tvMaxCorneringRightLabel.text = getString(R.string.track_max_cornering_right)
            tvMaxCorneringLeft.text = maxCorneringLeft
            tvMaxCorneringRight.text = maxCorneringRight
        }

        if (isPointToPointSession) {
            tvLapsSectionTitle.text = "Run Details"
            cardLaps.visibility = android.view.View.VISIBLE
        } else {
            tvLapsSectionTitle.text = getString(R.string.track_laps_section)
            cardLaps.visibility = android.view.View.VISIBLE
        }

        bindSessionVideoCard(sessionVideoUri, sessionVideoPath, sessionVideoCamera)
    }

    private fun bindSessionVideoCard(videoUriString: String, videoPath: String, videoCamera: String) {
        sessionVideoUri = videoUriString
            .takeIf { it.isNotBlank() }
            ?.let(Uri::parse)

        sessionVideoFile = videoPath
            .takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.exists() }

        val playbackUri = resolveSessionVideoPlaybackUri()

        if (playbackUri == null) {
            cardSessionVideo.visibility = View.GONE
            return
        }

        cardSessionVideo.visibility = View.VISIBLE
        tvSessionVideoTitle.text = getString(R.string.track_session_video_title)

        var durationLabel = ""
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, playbackUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            durationLabel = formatSessionVideoDuration(durationMs)
            val frame = retriever.getFrameAtTime(0L)
            if (frame != null) {
                ivSessionVideoThumbnail.setImageBitmap(frame)
            } else {
                ivSessionVideoThumbnail.setImageDrawable(null)
            }
        } catch (_: Exception) {
            cardSessionVideo.visibility = View.GONE
            return
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        val metaParts = buildList {
            add(
                getString(
                    if (sessionVideoOverlayExported) {
                        R.string.track_session_video_meta_pro
                    } else {
                        R.string.track_session_video_meta_raw
                    }
                )
            )
            if (videoCamera.isNotBlank()) add(videoCamera.uppercase(Locale.getDefault()))
            if (durationLabel.isNotBlank()) add(durationLabel)
        }
        tvSessionVideoMeta.text = metaParts.joinToString(" • ")
        updateSessionVideoRenderButton(hasPlayableVideo = true)
    }

    private fun updateSessionVideoRenderButton(hasPlayableVideo: Boolean) {
        val canRender = hasPlayableVideo && hasManualVideoExportMetadata && !sessionVideoOverlayExported
        btnSessionVideoRender.visibility = if (hasPlayableVideo && (hasManualVideoExportMetadata || sessionVideoOverlayExported)) {
            View.VISIBLE
        } else {
            View.GONE
        }
        btnSessionVideoRender.isEnabled = canRender && !isManualVideoExportRunning
        btnSessionVideoRender.alpha = if (btnSessionVideoRender.isEnabled) 1f else 0.65f
        btnSessionVideoRender.text = when {
            isManualVideoExportRunning -> getString(R.string.track_session_video_rendering_button)
            sessionVideoOverlayExported -> getString(R.string.track_session_video_rendered_button)
            canRender -> getString(R.string.track_session_video_render_button)
            else -> getString(R.string.track_session_video_render_unavailable_button)
        }
    }

    private fun formatSessionVideoDuration(durationMs: Long): String {
        val safeMs = durationMs.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    private fun openSessionVideo() {
        if (resolveSessionVideoPlaybackUri() == null) {
            Toast.makeText(this, getString(R.string.track_session_video_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        startActivity(Intent(this, TrackSessionVideoActivity::class.java).apply {
            putExtra("video_uri", sessionVideoUri?.toString())
            putExtra("video_path", sessionVideoFile?.absolutePath)
            putExtra("video_title", tvTitle.text.toString())
        })
    }

    private fun exportSessionVideo() {
        val shareUri = resolveSessionVideoPlaybackUri()
        if (shareUri == null) {
            Toast.makeText(this, getString(R.string.track_session_video_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = resolveVideoMimeType(shareUri)
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_TITLE, tvTitle.text.toString())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        runCatching {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.track_session_video_share_title)))
        }.onFailure {
            Toast.makeText(this, getString(R.string.track_session_video_share_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderSessionVideo() {
        if (isManualVideoExportRunning) return

        val sourceUri = resolveSessionVideoPlaybackUri()
        if (sourceUri == null) {
            Toast.makeText(this, getString(R.string.track_session_video_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasManualVideoExportMetadata) {
            Toast.makeText(this, getString(R.string.track_session_video_render_not_supported), Toast.LENGTH_SHORT).show()
            return
        }
        if (sessionVideoOverlayExported) {
            Toast.makeText(this, getString(R.string.track_session_video_render_already_done), Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val overlayModel = buildStoredSessionVideoOverlayModel(prefs)
        if (overlayModel == null) {
            Toast.makeText(this, getString(R.string.track_session_video_render_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val outputFile = File(cacheDir, "track_session_overlay_${System.currentTimeMillis()}.mp4")
        isManualVideoExportRunning = true
        updateSessionVideoRenderButton(hasPlayableVideo = true)
        showVideoExportProgressDialog()

        val exporter = TrackSessionVideoOverlayExporter(this)
        activeVideoOverlayExporter?.cancel()
        activeVideoOverlayExporter = exporter
        exporter.export(
            request = TrackSessionVideoOverlayExporter.ExportRequest(
                inputUri = sourceUri,
                outputFile = outputFile,
                trimStartMs = 0L,
                overlayModel = overlayModel
            ),
            onSuccess = { renderedFile ->
                activeVideoOverlayExporter = null
                persistRenderedSessionVideo(renderedFile)
            },
            onError = { error ->
                android.util.Log.e("TrackSessionDetailActivity", "Manual telemetry export failed", error)
                activeVideoOverlayExporter = null
                outputFile.delete()
                isManualVideoExportRunning = false
                dismissVideoExportProgressDialog()
                updateSessionVideoRenderButton(hasPlayableVideo = resolveSessionVideoPlaybackUri() != null)
                Toast.makeText(this, getString(R.string.track_session_video_render_failed), Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showVideoExportProgressDialog() {
        if (isFinishing || isDestroyed) return
        if (exportProgressDialog?.isShowing == true) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_track_video_export_progress, null)
        exportProgressDialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()
            .also { dialog ->
                dialog.setCanceledOnTouchOutside(false)
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                dialog.show()
            }
    }

    private fun dismissVideoExportProgressDialog() {
        exportProgressDialog?.dismiss()
        exportProgressDialog = null
    }

    private fun buildStoredSessionVideoOverlayModel(
        sharedPrefs: android.content.SharedPreferences
    ): TrackSessionVideoOverlayExporter.OverlayModel? {
        val laps = mutableListOf<LapData>()
        forEachSessionLapData(sharedPrefs) { lapData ->
            laps += lapData
        }

        val extraLapJson = sharedPrefs.getString(
            "${trackId}_outing_${outingNumber}_video_export_lap_data",
            null
        )
        if (!extraLapJson.isNullOrBlank()) {
            runCatching { Gson().fromJson(extraLapJson, LapData::class.java) }
                .getOrNull()
                ?.takeIf { extraLap ->
                    val lastLap = laps.lastOrNull()
                    lastLap == null ||
                        lastLap.lapNumber != extraLap.lapNumber ||
                        lastLap.startTime != extraLap.startTime ||
                        lastLap.endTime != extraLap.endTime
                }
                ?.let { laps += it }
        }

        val orderedLaps = laps
            .filter { it.startTime > 0L }
            .sortedBy { it.startTime }
        if (orderedLaps.isEmpty()) return null

        val sessionStartWallTimeMs = orderedLaps.minOfOrNull { it.startTime } ?: return null
        val lapSegments = orderedLaps.mapIndexed { index, lap ->
            TrackSessionVideoOverlayExporter.LapSegment(
                lapNumber = if (lap.lapNumber > 0) lap.lapNumber else index + 1,
                startMs = lap.startTime - sessionStartWallTimeMs,
                durationMs = resolveStoredLapDurationMs(sharedPrefs, index + 1, lap),
                isCompleted = index < totalLaps
            )
        }

        val routeSamples = orderedLaps.flatMap { lap ->
            val lapOffsetMs = lap.startTime - sessionStartWallTimeMs
            lap.routePoints.map { point ->
                TrackSessionVideoOverlayExporter.RouteSample(
                    timeMs = lapOffsetMs + point.timestamp,
                    geoPoint = point.geoPoint,
                    speedKmh = point.speed
                )
            }
        }.sortedBy { it.timeMs }

        val gSamples = orderedLaps.flatMap { lap ->
            val sampleCount = minOf(lap.timestamps.size, lap.longitudinalGData.size, lap.lateralGData.size)
            (0 until sampleCount).map { sampleIndex ->
                TrackSessionVideoOverlayExporter.GSample(
                    timeMs = lap.timestamps[sampleIndex] - sessionStartWallTimeMs,
                    longitudinalG = lap.longitudinalGData[sampleIndex],
                    lateralG = lap.lateralGData[sampleIndex],
                    maxBraking = lap.maxBrakingData.getOrNull(sampleIndex)?.takeIf { it.isFinite() },
                    maxAccel = lap.maxAccelData.getOrNull(sampleIndex)?.takeIf { it.isFinite() },
                    maxLeft = lap.maxCorneringLeftData.getOrNull(sampleIndex)?.takeIf { it.isFinite() },
                    maxRight = lap.maxCorneringRightData.getOrNull(sampleIndex)?.takeIf { it.isFinite() },
                    maxResultG = lap.maxResultGData.getOrNull(sampleIndex)?.takeIf { it.isFinite() }
                )
            }
        }.sortedBy { it.timeMs }

        val leanSamples = orderedLaps.flatMap { lap ->
            val sampleCount = minOf(
                lap.timestamps.size,
                maxOf(lap.displayLeanAngleData.size, lap.leanAngleData.size)
            )
            (0 until sampleCount).mapNotNull { sampleIndex ->
                val angle = lap.displayLeanAngleData.getOrNull(sampleIndex)
                    ?: lap.leanAngleData.getOrNull(sampleIndex)?.let(::normalizeLegacyOverlayLeanAngle)
                    ?: return@mapNotNull null
                if (!angle.isFinite()) {
                    null
                } else {
                    TrackSessionVideoOverlayExporter.LeanSample(
                        timeMs = lap.timestamps[sampleIndex] - sessionStartWallTimeMs,
                        angleDeg = angle
                    )
                }
            }
        }.sortedBy { it.timeMs }

        val miniMapPoints = TrackMiniMapShapeResolver(this).resolveMiniMapPoints(
            trackId = extractTrackIdFromSessionId(trackId),
            orderedLaps = orderedLaps,
            routeFallback = routeSamples.map { it.geoPoint },
            isCircuit = !isPointToPointSession
        ).ifEmpty {
            routeSamples.map { it.geoPoint }.distinctBy { point ->
                String.format(Locale.US, "%.6f:%.6f", point.latitude, point.longitude)
            }
        }

        return TrackSessionVideoOverlayExporter.OverlayModel(
            isMotorcycle = sessionIsMotorcycle,
            videoStartSessionElapsedMs = sessionVideoElapsedAtStartMs,
            lapSegments = lapSegments,
            routeSamples = routeSamples,
            gSamples = gSamples,
            leanSamples = leanSamples,
            miniMapPoints = miniMapPoints
        )
    }

    private fun resolveStoredLapDurationMs(
        sharedPrefs: android.content.SharedPreferences,
        lapNumber: Int,
        lapData: LapData
    ): Long {
        val completedLapTime = sharedPrefs.getString(
            "${trackId}_outing_${outingNumber}_lap_${lapNumber}",
            null
        )
        val completedDuration = completedLapTime?.let(::parseFlexibleTimeToMs)
        if (completedDuration != null) {
            return completedDuration.coerceAtLeast(0L)
        }
        if (lapData.endTime > lapData.startTime) {
            return (lapData.endTime - lapData.startTime).coerceAtLeast(0L)
        }
        val routeDuration = lapData.routePoints.maxOfOrNull { it.timestamp }
        if (routeDuration != null) return routeDuration.coerceAtLeast(0L)
        val sensorDuration = lapData.timestamps.maxOrNull()?.let { it - lapData.startTime }
        return sensorDuration?.coerceAtLeast(0L) ?: 0L
    }

    private fun normalizeLegacyOverlayLeanAngle(angleDeg: Float): Float {
        return if (abs(angleDeg) < 1.8f) 0f else angleDeg
    }

    private fun persistRenderedSessionVideo(renderedFile: File) {
        val savedUri = TrackSessionVideoExport.saveVideoToLibrary(this, renderedFile, tvTitle.text?.toString())
        renderedFile.delete()

        if (savedUri == null) {
            isManualVideoExportRunning = false
            dismissVideoExportProgressDialog()
            updateSessionVideoRenderButton(hasPlayableVideo = resolveSessionVideoPlaybackUri() != null)
            Toast.makeText(this, getString(R.string.track_session_video_render_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val oldVideoUriString = sessionVideoUri?.toString().orEmpty()
        val oldVideoFilePath = sessionVideoFile?.absolutePath.orEmpty()
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        prefs.edit().apply {
            putString("${trackId}_outing_${outingNumber}_video_uri", savedUri.toString())
            remove("${trackId}_outing_${outingNumber}_video_path")
            putBoolean("${trackId}_outing_${outingNumber}_video_overlay_exported", true)
            apply()
        }

        if (oldVideoUriString.isNotBlank() && oldVideoUriString != savedUri.toString()) {
            runCatching { contentResolver.delete(Uri.parse(oldVideoUriString), null, null) }
        }
        if (oldVideoFilePath.isNotBlank()) {
            runCatching { File(oldVideoFilePath).delete() }
        }

        sessionVideoUri = savedUri
        sessionVideoFile = null
        sessionVideoOverlayExported = true
        isManualVideoExportRunning = false
        dismissVideoExportProgressDialog()
        loadSessionData()
        Toast.makeText(this, getString(R.string.track_session_video_render_success), Toast.LENGTH_SHORT).show()
    }

    private fun resolveSessionVideoPlaybackUri(): Uri? {
        sessionVideoUri?.let { return it }
        return sessionVideoFile
            ?.takeIf { it.exists() }
            ?.let { file ->
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            }
    }

    private fun resolveVideoMimeType(uri: Uri): String {
        return contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            )
            ?: "video/mp4"
    }

    override fun onDestroy() {
        activeVideoOverlayExporter?.cancel()
        activeVideoOverlayExporter = null
        dismissVideoExportProgressDialog()
        super.onDestroy()
    }

    private fun parseLapTime(lapTime: String): Long {
        return parseFlexibleTimeToMs(lapTime) ?: Long.MAX_VALUE
    }

    private fun resolveLapMaxSpeedDisplay(
        sharedPrefs: android.content.SharedPreferences,
        lapNumber: Int
    ): String {
        val lapData = loadLapDataForDetails(sharedPrefs, lapNumber) ?: return "--"
        val rawMaxKmh = lapData.routePoints.maxOfOrNull { it.speed } ?: 0f
        if (!rawMaxKmh.isFinite() || rawMaxKmh <= 0f) return "--"

        val converted = UnitsManager.convertSpeed(rawMaxKmh, UnitsManager.getSpeedUnit(this))
        return kotlin.math.round(converted.toDouble()).toInt().coerceAtLeast(0).toString()
    }

    private fun resolveOutingMaxSpeedDisplay(sharedPrefs: android.content.SharedPreferences): String {
        val raw = sharedPrefs.getString("${trackId}_outing_${outingNumber}_max_speed", "") ?: ""
        val valueKmh = parseDisplayedNumeric(raw) ?: return "--"
        if (!valueKmh.isFinite() || valueKmh <= 0f) return "--"

        val converted = UnitsManager.convertSpeed(valueKmh, UnitsManager.getSpeedUnit(this))
        return kotlin.math.round(converted.toDouble()).toInt().coerceAtLeast(0).toString()
    }

    private fun formatLapDelta(deltaMs: Long): String {
        val safeDelta = deltaMs.coerceAtLeast(0L)
        val seconds = safeDelta / 1_000L
        val millis = safeDelta % 1_000L
        return String.format(java.util.Locale.US, "+%d.%03d", seconds, millis)
    }

    private fun parseFlexibleTimeToMs(value: String): Long? {
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

    private fun formatTimeMs(totalMs: Long): String {
        val safeMs = totalMs.coerceAtLeast(0L)
        val minutes = safeMs / 60_000L
        val seconds = (safeMs % 60_000L) / 1_000L
        val millis = safeMs % 1_000L
        return String.format(java.util.Locale.getDefault(), "%d:%02d.%03d", minutes, seconds, millis)
    }

    private fun compactSpeedForHeader(rawSpeed: String): String {
        val speed = rawSpeed.trim()
        return if (speed.isEmpty()) "--" else speed.replace(" ", "")
    }

    private fun highlightBestLapNumber(text: String, lapNumber: Int): CharSequence {
        val lapToken = lapNumber.toString()
        val startIndex = text.indexOf(lapToken)
        if (startIndex < 0) return text

        val spannable = SpannableString(text)
        val highlightColor = ContextCompat.getColor(this, R.color.primary_color)
        spannable.setSpan(
            ForegroundColorSpan(highlightColor),
            startIndex,
            startIndex + lapToken.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    private fun resolveWeatherIconStyle(iconRes: Int, humidityPercent: Int?): Pair<Int, Int> {
        val baseIcon = when (iconRes) {
            R.drawable.ic_weather_sunny -> R.drawable.ic_weather_sunny
            R.drawable.ic_weather_clear_night -> R.drawable.ic_weather_clear_night
            R.drawable.ic_weather_partly_cloudy,
            R.drawable.ic_weather_partly_cloudy_night -> R.drawable.ic_weather_partly_cloudy
            R.drawable.ic_weather_cloudy -> R.drawable.ic_weather_cloudy
            R.drawable.ic_weather_rainy -> R.drawable.ic_weather_rainy
            R.drawable.ic_weather_snowy -> R.drawable.ic_weather_snowy
            else -> R.drawable.ic_weather_cloudy
        }

        val finalIcon = if (baseIcon == R.drawable.ic_weather_sunny && (humidityPercent ?: 0) >= 70) {
            R.drawable.ic_weather_cloudy
        } else {
            baseIcon
        }

        val tintRes = when (finalIcon) {
            R.drawable.ic_weather_sunny -> R.color.warning_color
            R.drawable.ic_weather_rainy -> R.color.accent_light
            R.drawable.ic_weather_snowy -> R.color.accent_light
            R.drawable.ic_weather_clear_night -> R.color.text_secondary_light
            R.drawable.ic_weather_cloudy,
            R.drawable.ic_weather_partly_cloudy -> R.color.text_tertiary
            else -> R.color.text_tertiary
        }

        return finalIcon to tintRes
    }

    private fun computeSessionDistanceKm(prefs: android.content.SharedPreferences): Double {
        var totalDistanceKm = 0.0
        val foundLapData = forEachSessionLapData(prefs) { lapData ->
            if (lapData.routePoints.size > 1) {
                totalDistanceKm += calculateDistanceKm(lapData.routePoints)
            }
        }

        if (!foundLapData) return 0.0
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
        var maxLeanLeft = 0f
        var maxLeanRight = 0f
        val foundLapData = forEachSessionLapData(prefs) { lapData ->
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

        if (!foundLapData) return null
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
        val rounded = kotlin.math.round(value.toDouble()).toInt().coerceAtLeast(0)
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
