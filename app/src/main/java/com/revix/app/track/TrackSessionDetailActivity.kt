package com.revix.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
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
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.revix.app.data.ProfileStorage
import com.revix.app.main.MainContainerActivity
import com.revix.app.main.map.MapActivity
import com.revix.app.RouteStorage
import com.revix.app.settings.LanguageManager
import com.revix.app.settings.UnitsManager
import com.revix.app.track.TrackLapDataStore
import com.revix.app.track.TrackSessionVideoOverlayModels
import com.revix.app.track.TrackSessionVideoOverlayService
import com.revix.app.track.TrackMapExtras
import com.revix.app.track.enrichRoutePointsWithLeanPeaks
import com.revix.app.track.normalizeRoutePointsForMap
import com.google.gson.Gson
import com.google.android.material.button.MaterialButton
import com.revix.app.utils.LapTimeFormatter
import com.revix.app.utils.RoutePointDistance
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class TrackSessionDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUTO_EXPORT_TELEMETRY = "autoExportTelemetry"
        private const val STATE_AUTO_EXPORT_STARTED = "auto_export_started"
    }
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    private lateinit var btnBack: View
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
    private lateinit var btnSessionVideoExportHud: MaterialButton
    private lateinit var btnSessionVideoRetry: MaterialButton
    private lateinit var btnSessionVideoReexport: MaterialButton
    private lateinit var flSessionVideoPreview: View
    private lateinit var ivSessionVideoPlay: ImageView
    private lateinit var llSessionVideoProcessing: LinearLayout
    private lateinit var pbSessionVideoProcessing: ProgressBar
    private lateinit var tvSessionVideoProcessing: TextView
    private lateinit var hsvSessionVideoClips: View
    private lateinit var llSessionVideoClips: LinearLayout
    
    // Lap click listeners
    private lateinit var llLapsContainer: LinearLayout
    private lateinit var tvNoLaps: TextView
    
    // Store data to survive activity recreation
    private var trackId: String = ""
    private var outingNumber: Int = 1
    private var totalLaps: Int = 0
    private var sessionDateLabel: String = ""
    private var isPointToPointSession: Boolean = false
    private var sessionVideoClips: List<TrackSessionVideoClip> = emptyList()
    private var selectedSessionVideoClipIndex: Int = 0
    private var sessionVideoUri: Uri? = null
    private var sessionVideoFile: File? = null
    private var sessionVideoCameraLabel: String = ""
    private var sessionVideoStartOffsetMs: Long = 0L
    private var sessionVideoElapsedAtStartMs: Long = 0L
    private var sessionVideoOverlayExported: Boolean = true
    private var hasManualVideoExportMetadata = false
    private var sessionIsMotorcycle = false
    private var skipInitialResumeReload = false
    private var didAttemptAutoSessionExport = false
    private var videoBindGeneration = 0
    private var overlayProgressPercent = 0
    private val overlayUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TrackSessionVideoOverlayService.ACTION_UPDATE) return
            val sessionId = intent.getStringExtra(TrackSessionVideoOverlayService.EXTRA_SESSION_ID).orEmpty()
            val outing = intent.getIntExtra(TrackSessionVideoOverlayService.EXTRA_OUTING_NUMBER, 0)
            if (sessionId != trackId || outing != outingNumber) return
            val kind = intent.getStringExtra(TrackSessionVideoOverlayService.EXTRA_KIND)
                ?: TrackSessionVideoOverlayService.KIND_SESSION
            if (kind == TrackSessionVideoOverlayService.KIND_SESSION) {
                overlayProgressPercent = intent.getIntExtra(
                    TrackSessionVideoOverlayService.EXTRA_PROGRESS,
                    overlayProgressPercent
                )
            }
            when (intent.getStringExtra(TrackSessionVideoOverlayService.EXTRA_STATE)) {
                TrackSessionVideoOverlayService.STATE_READY,
                TrackSessionVideoOverlayService.STATE_FAILED -> reloadSessionContent()
                else -> applySessionVideoOverlayState()
            }
        }
    }
    private val lapDataGson = Gson()
    private var cachedLapDataSessionKey: String? = null
    private var cachedLapDataContext: LapDataSessionContext? = null
    private val cachedSessionLaps = linkedMapOf<Int, LapData>()

    private data class SessionVideoMetadata(
        val durationMs: Long,
        val thumbnail: Bitmap?
    )

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
        skipInitialResumeReload = true
        didAttemptAutoSessionExport = savedInstanceState?.getBoolean(STATE_AUTO_EXPORT_STARTED) ?: false
        ContextCompat.registerReceiver(
            this,
            overlayUpdateReceiver,
            IntentFilter(TrackSessionVideoOverlayService.ACTION_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        reloadSessionContent()
        maybeStartBackgroundOverlay()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_AUTO_EXPORT_STARTED, didAttemptAutoSessionExport)
    }
    
    override fun onResume() {
        super.onResume()
        if (skipInitialResumeReload) {
            skipInitialResumeReload = false
            return
        }
        invalidateLapDataCache()
        reloadSessionContent()
        maybeStartBackgroundOverlay()
    }

    private fun reloadSessionContent() {
        loadSessionData()
        setupLapClickListeners()
    }

    private fun invalidateLapDataCache() {
        cachedLapDataSessionKey = null
        cachedLapDataContext = null
        cachedSessionLaps.clear()
    }

    private fun lapDataCacheKey(context: LapDataSessionContext): String {
        return "${context.sessionId}|$outingNumber|${context.lapDataCount}"
    }

    /**
     * Resolves which session id holds lap telemetry. Does NOT preload all laps into memory —
     * RaceBox sessions are too large and caused white-screen / process death on open & back.
     */
    private fun ensureSessionLapDataLoaded(
        sharedPrefs: android.content.SharedPreferences
    ): LapDataSessionContext? {
        val context = resolveLapDataSessionContext(sharedPrefs) ?: return null
        val cacheKey = lapDataCacheKey(context)
        if (cachedLapDataSessionKey != cacheKey) {
            cachedSessionLaps.clear()
            cachedLapDataSessionKey = cacheKey
        }
        cachedLapDataContext = context
        return context
    }

    private fun loadSingleLapData(
        sharedPrefs: android.content.SharedPreferences,
        sessionId: String,
        lapIndex: Int
    ): LapData? {
        cachedSessionLaps[lapIndex]?.let { return it }
        val lap = TrackLapDataStore.loadLap(
            context = this,
            sharedPrefs = sharedPrefs,
            sessionId = sessionId,
            outingNumber = outingNumber,
            lapIndex = lapIndex
        ) ?: return null
        // Keep at most one heavy lap in memory (for map open). Overview streams without caching.
        cachedSessionLaps.clear()
        cachedSessionLaps[lapIndex] = lap
        return lap
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
        btnSessionVideoExportHud = findViewById(R.id.btnSessionVideoExportHud)
        flSessionVideoPreview = findViewById(R.id.flSessionVideoPreview)
        ivSessionVideoPlay = findViewById(R.id.ivSessionVideoPlay)
        llSessionVideoProcessing = findViewById(R.id.llSessionVideoProcessing)
        pbSessionVideoProcessing = findViewById(R.id.pbSessionVideoProcessing)
        tvSessionVideoProcessing = findViewById(R.id.tvSessionVideoProcessing)
        hsvSessionVideoClips = findViewById(R.id.hsvSessionVideoClips)
        llSessionVideoClips = findViewById(R.id.llSessionVideoClips)
        btnSessionVideoRetry = findViewById(R.id.btnSessionVideoRetry)
        btnSessionVideoReexport = findViewById(R.id.btnSessionVideoReexport)
        
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
        btnSessionVideoExportHud.setOnClickListener {
            exportHudOnlySessionVideo()
        }
        btnSessionVideoRetry.setOnClickListener {
            retryBackgroundOverlay()
        }
        btnSessionVideoReexport.setOnClickListener {
            reexportBackgroundOverlay()
        }
    }
    
    private fun setupLapClickListeners() {
        if (isPointToPointSession) {
            setupPointToPointRunDetails()
            return
        }

        val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val lapEntries = mutableListOf<LapRowEntry>()
        
        // Lap times / max-V from light prefs keys only — never preload full RaceBox telemetry.
        for (i in 1..totalLaps) {
            val lapTime = LapTimeFormatter.formatDurationDisplay(
                sharedPrefs.getString("${trackId}_outing_${outingNumber}_lap_${i}", LapTimeFormatter.PLACEHOLDER)
                    ?: LapTimeFormatter.PLACEHOLDER
            )
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
        val runTime = LapTimeFormatter.formatDurationDisplay(
            prefs.getString("${trackId}_outing_${outingNumber}_best_lap", LapTimeFormatter.PLACEHOLDER)
                ?: LapTimeFormatter.PLACEHOLDER
        )

        llLapsContainer.removeAllViews()
        tvNoLaps.visibility = android.view.View.GONE

        if (LapTimeFormatter.isPlaceholder(runTime)) {
            tvNoLaps.text = getString(R.string.track_no_run_data)
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
            tvLapNumber.text = getString(R.string.track_run_label)
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
        val lapDataContext = ensureSessionLapDataLoaded(sharedPrefs)
        val safeLapNumber = if (lapDataContext != null) {
            lapNumber.coerceIn(1, lapDataContext.lapDataCount)
        } else {
            lapNumber
        }
        val sessionId = lapDataContext?.sessionId ?: trackId
        // Map open: route + lean only from `.bin` when available (full telem loads in MapActivity charts).
        val parsedLapData = TrackLapDataStore.loadLapForMap(
            context = this,
            sharedPrefs = sharedPrefs,
            sessionId = sessionId,
            outingNumber = outingNumber,
            lapIndex = safeLapNumber
        )

        if (parsedLapData == null || parsedLapData.routePoints.isEmpty()) {
            Toast.makeText(this, getString(R.string.track_toast_lap_data_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        val enrichedRoutePoints = enrichRoutePointsWithLeanPeaks(parsedLapData)
        val normalizedPoints = normalizeRoutePointsForMap(enrichedRoutePoints)
        if (normalizedPoints.isEmpty()) {
            Toast.makeText(this, getString(R.string.track_toast_lap_data_unavailable), Toast.LENGTH_SHORT).show()
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
            routePoints = emptyList(),
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

        // Keep Intent small — RaceBox laps used to exceed binder limits (~668KB) and kill the process.
        RouteStorage.saveRoutePoints(this, raceForMap.id, normalizedPoints)

        val intent = Intent(this, MapActivity::class.java).apply {
            putExtra(MapActivity.EXTRA_INLINE_RACE, raceForMap)
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
        val lapDataContext = ensureSessionLapDataLoaded(sharedPrefs) ?: return null
        val safeLapNumber = requestedLapNumber.coerceIn(1, lapDataContext.lapDataCount)
        return loadSingleLapData(sharedPrefs, lapDataContext.sessionId, safeLapNumber)
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
        val lapDataContext = ensureSessionLapDataLoaded(sharedPrefs) ?: return false
        var foundLapData = false

        // Stream one lap at a time — do not retain the full session in RAM.
        for (lapIndex in 1..lapDataContext.lapDataCount) {
            val lapData = TrackLapDataStore.loadLap(
                context = this,
                sharedPrefs = sharedPrefs,
                sessionId = lapDataContext.sessionId,
                outingNumber = outingNumber,
                lapIndex = lapIndex
            ) ?: continue
            foundLapData = true
            onLapData(lapData)
        }

        return foundLapData
    }

    private fun resolveDurationMs(lapData: LapData, points: List<RoutePoint>, lapTime: String): Long {
        val dataDuration = lapData.endTime - lapData.startTime
        if (dataDuration > 0L) return dataDuration

        val pointSpan = points.last().timestamp - points.first().timestamp
        if (pointSpan > 0L) return pointSpan

        val parsed = parseLapTime(lapTime)
        return if (parsed != Long.MAX_VALUE) parsed else 0L
    }

    private fun calculateDistanceKm(points: List<RoutePoint>): Double =
        RoutePointDistance.distanceKm(points)

    private fun loadSessionData() {
        // Load from track_outings SharedPreferences (where data is actually saved)
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        
        android.util.Log.d("TrackSessionDetailActivity", "loadSessionData: trackId='$trackId', outingNumber=$outingNumber")
        
        // Not used anymore - removed
        
        // Get data from track_outings using the full sessionId
        val sessionDate = prefs.getString("${trackId}_outing_${outingNumber}_date", "--.--.----") ?: "--.--.----"
        sessionDateLabel = sessionDate
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
        val previousSelectedClipIndex = selectedSessionVideoClipIndex
        sessionVideoClips = TrackSessionVideoClipsCodec.loadFromPrefs(prefs, trackId, outingNumber)
        pruneUnownedLapVideoClips(prefs)
        overlayProgressPercent = TrackSessionVideoOverlayService.progress(prefs, trackId, outingNumber)
        selectedSessionVideoClipIndex = previousSelectedClipIndex.coerceIn(
            0,
            (sessionVideoClips.size - 1).coerceAtLeast(0)
        )
        preferTelemetryClipSelection()
        applySelectedSessionVideoClip()
        val sessionVideoElapsedAtStartKey = "${trackId}_outing_${outingNumber}_video_session_elapsed_at_start_ms"
        val sessionVideoStartOffsetKey = "${trackId}_outing_${outingNumber}_video_session_start_offset_ms"
        hasManualVideoExportMetadata = when {
            sessionVideoClips.size > 1 -> true
            sessionVideoClips.isEmpty() -> false
            else -> prefs.contains(sessionVideoElapsedAtStartKey) || prefs.contains(sessionVideoStartOffsetKey)
        }
        val loadedVideoUri = this.sessionVideoUri?.toString().orEmpty()
        val loadedVideoPath = this.sessionVideoFile?.absolutePath.orEmpty()
        val loadedVideoCamera = sessionVideoCameraLabel
        val mode = prefs.getString("${trackId}_outing_${outingNumber}_mode", "circuit") ?: "circuit"
        isPointToPointSession = mode == "point_to_point"
        prefs.getString("${trackId}_outing_${outingNumber}_laps", null)?.toIntOrNull()?.let { storedLaps ->
            totalLaps = storedLaps
        }
        
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
        
        val bestLapTime = LapTimeFormatter.formatDurationDisplay(
            prefs.getString("${trackId}_outing_${outingNumber}_best_lap", LapTimeFormatter.PLACEHOLDER)
                ?: LapTimeFormatter.PLACEHOLDER
        )
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
        // Prefer stored outing lean — avoid scanning all RaceBox points on every open.
        val leanExtremesFromLaps = if (maxLeanLeftStored == null && maxLeanRightStored == null) {
            computeSessionLeanExtremes(prefs)
        } else {
            null
        }

        val overviewLeanLeftDisplay = when {
            maxLeanLeftStored != null -> parseDisplayedNumeric(maxLeanLeftStored)?.let { formatLeanAngleRounded(it) }
            leanExtremesFromLaps != null -> formatLeanAngleRounded(leanExtremesFromLaps.first)
            else -> parseDisplayedNumeric(maxLeanDisplay)?.let { formatLeanAngleRounded(it) }
        } ?: "--"

        val overviewLeanRightDisplay = when {
            maxLeanRightStored != null -> parseDisplayedNumeric(maxLeanRightStored)?.let { formatLeanAngleRounded(it) }
            leanExtremesFromLaps != null -> formatLeanAngleRounded(leanExtremesFromLaps.second)
            else -> parseDisplayedNumeric(maxLeanDisplay)?.let { formatLeanAngleRounded(it) }
        } ?: "--"

        val allLapTimes = if (totalLaps > 0) {
            (1..totalLaps).map { lapNumber ->
                prefs.getString("${trackId}_outing_${outingNumber}_lap_${lapNumber}", LapTimeFormatter.PLACEHOLDER)
                    ?: LapTimeFormatter.PLACEHOLDER
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
        com.revix.app.racebox.RaceBoxSessionUi.bindLabel(
            findViewById(R.id.tvTitleRaceBoxBadge),
            prefs.getBoolean("${trackId}_outing_${outingNumber}_recorded_with_racebox", false)
        )
        tvHeaderProfileName.text = profileName

        tvHeaderBestLapTime.text = bestLapTime
        tvHeaderBestLapMeta.text = if (isPointToPointSession) {
            getString(R.string.track_header_lap_meta, 1, lapsTotalForHeader)
        } else if (bestLapNumberInSession != null && lapsTotalForHeader > 0) {
            highlightBestLapNumber(
                getString(R.string.track_header_lap_meta, bestLapNumberInSession, lapsTotalForHeader),
                bestLapNumberInSession
            )
        } else {
            getString(R.string.track_header_lap_meta_unknown, lapsTotalForHeader.coerceAtLeast(1))
        }
        tvHeaderMaxSpeedValue.text = compactSpeedForHeader(maxSpeed)
        tvHeaderAvgLapValue.text = avgLapMs?.let { formatTimeMs(it) } ?: LapTimeFormatter.PLACEHOLDER
        tvHeaderTotalTimeValue.text = sessionDuration
        tvWeatherSessionTemp.text = UnitsManager.formatStoredTemperature(sessionTemperature, this)
        tvWeatherSessionHumidity.text = sessionHumidity
        tvWeatherSessionWind.text = UnitsManager.formatStoredSpeed(sessionWindSpeed, this)
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
            tvLapsSectionTitle.text = getString(R.string.track_point_to_point_run_details)
            cardLaps.visibility = android.view.View.VISIBLE
        } else {
            tvLapsSectionTitle.text = getString(R.string.track_laps_section)
            cardLaps.visibility = android.view.View.VISIBLE
        }

        bindSessionVideoCard(
            sessionVideoUri?.toString().orEmpty(),
            sessionVideoFile?.absolutePath.orEmpty(),
            sessionVideoCameraLabel
        )
    }

    private fun applySelectedSessionVideoClip() {
        val clip = sessionVideoClips.getOrNull(selectedSessionVideoClipIndex)
        sessionVideoUri = clip?.uri
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
        sessionVideoFile = clip?.path
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.exists() }
        sessionVideoCameraLabel = clip?.camera.orEmpty()
        sessionVideoStartOffsetMs = clip?.sessionStartOffsetMs ?: 0L
        sessionVideoElapsedAtStartMs = clip?.sessionElapsedAtStartMs ?: -sessionVideoStartOffsetMs
        sessionVideoOverlayExported = clip?.overlayExported ?: false
    }

    private fun pruneUnownedLapVideoClips(prefs: android.content.SharedPreferences) {
        val ownedLaps = prefs.getBoolean("${trackId}_outing_${outingNumber}_laps_export_owned", false)
        if (ownedLaps) return
        val pruned = sessionVideoClips.filter { clip -> clip.kind != TrackSessionVideoKind.LAPS }
        if (pruned.size == sessionVideoClips.size) return
        sessionVideoClips = pruned
        prefs.edit().also { editor ->
            TrackSessionVideoClipsCodec.writeToEditor(editor, trackId, outingNumber, pruned)
            editor.commit()
        }
    }

    private fun preferTelemetryClipSelection() {
        if (sessionVideoClips.isEmpty()) return
        val selected = sessionVideoClips.getOrNull(selectedSessionVideoClipIndex)
        if (selected?.kind == TrackSessionVideoKind.RECORDING) {
            val sessionMatch = matchingSessionClip(selected)
            if (sessionMatch != null) {
                selectedSessionVideoClipIndex = sessionVideoClips.indexOf(sessionMatch).coerceAtLeast(0)
                return
            }
        }
        val sessionIndex = sessionVideoClips.indexOfFirst { clip -> clip.kind == TrackSessionVideoKind.SESSION }
        if (sessionIndex >= 0 && (selected == null || selected.kind == TrackSessionVideoKind.RECORDING)) {
            selectedSessionVideoClipIndex = sessionIndex
        }
    }

    private fun visibleSessionVideoClipIndices(): List<Int> {
        val hideRawWhileOverlayPending = isSessionVideoOverlayBlocking()
        return sessionVideoClips.indices.filter { index ->
            val clip = sessionVideoClips[index]
            when {
                hideRawWhileOverlayPending && clip.kind == TrackSessionVideoKind.RECORDING -> false
                clip.kind != TrackSessionVideoKind.RECORDING -> true
                else -> matchingSessionClip(clip) == null
            }
        }
    }

    private fun selectSessionVideoClip(index: Int) {
        if (index !in sessionVideoClips.indices) return
        if (index == selectedSessionVideoClipIndex && resolveSessionVideoPlaybackUri() != null) {
            updateSessionVideoClipChips()
            return
        }
        selectedSessionVideoClipIndex = index
        applySelectedSessionVideoClip()
        bindSessionVideoCard(
            sessionVideoUri?.toString().orEmpty(),
            sessionVideoFile?.absolutePath.orEmpty(),
            sessionVideoCameraLabel
        )
    }

    private fun updateSessionVideoClipChips() {
        val visibleIndices = visibleSessionVideoClipIndices()
        val showSwitcher = visibleIndices.size > 1
        hsvSessionVideoClips.visibility = if (showSwitcher) View.VISIBLE else View.GONE
        if (!showSwitcher) {
            llSessionVideoClips.removeAllViews()
            return
        }

        llSessionVideoClips.removeAllViews()
        sessionVideoClips.forEachIndexed { index, clip ->
            if (index !in visibleIndices) return@forEachIndexed
            val chip = layoutInflater.inflate(
                R.layout.item_session_video_clip_chip,
                llSessionVideoClips,
                false
            ) as MaterialButton
            chip.text = labelForSessionVideoClip(clip)
            val selected = index == selectedSessionVideoClipIndex
            val selectedColor = Color.parseColor("#FF6020")
            val idleColor = Color.parseColor("#9E9E9E")
            chip.setTextColor(if (selected) Color.WHITE else idleColor)
            chip.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (selected) selectedColor else Color.parseColor("#1C2128")
            )
            chip.setOnClickListener {
                selectSessionVideoClip(index)
            }
            llSessionVideoClips.addView(chip)
        }
    }

    private fun labelForSessionVideoClip(clip: TrackSessionVideoClip): String {
        val partCount = sessionVideoPartCount()
        val partNumber = recordingPartNumber(clip)
        return when (clip.kind) {
            TrackSessionVideoKind.RECORDING,
            TrackSessionVideoKind.SESSION -> {
                if (partCount > 1) {
                    getString(R.string.track_session_video_clip_part, partNumber)
                } else if (clip.kind == TrackSessionVideoKind.SESSION) {
                    getString(R.string.track_session_video_clip_session)
                } else {
                    getString(R.string.track_session_video_clip_recording)
                }
            }
            TrackSessionVideoKind.HUD -> getString(R.string.track_session_video_clip_hud)
            TrackSessionVideoKind.LAPS -> {
                val from = clip.lapFrom.takeIf { it > 0 } ?: 1
                val to = clip.lapTo.takeIf { it > 0 } ?: from
                if (from == to) {
                    getString(R.string.track_session_video_clip_lap, from)
                } else {
                    getString(R.string.track_session_video_clip_laps, from, to)
                }
            }
        }
    }

    private fun sessionVideoPartCount(): Int {
        return sessionVideoClips.count { clip -> clip.kind == TrackSessionVideoKind.RECORDING }
            .coerceAtLeast(sessionVideoClips.count { clip -> clip.kind == TrackSessionVideoKind.SESSION })
    }

    private fun recordingPartNumber(clip: TrackSessionVideoClip): Int {
        val recordings = sessionVideoClips.filter { item -> item.kind == TrackSessionVideoKind.RECORDING }
        val matchedIndex = recordings.indexOfFirst { recording ->
            recording.sessionElapsedAtStartMs == clip.sessionElapsedAtStartMs ||
                (recording.uri.isNotBlank() && recording.uri == clip.uri)
        }
        if (matchedIndex >= 0) return matchedIndex + 1
        if (clip.kind == TrackSessionVideoKind.SESSION) {
            val sessions = sessionVideoClips.filter { item -> item.kind == TrackSessionVideoKind.SESSION }
            val sessionIndex = sessions.indexOfFirst { item ->
                item.sessionElapsedAtStartMs == clip.sessionElapsedAtStartMs && item.uri == clip.uri
            }
            if (sessionIndex >= 0) return sessionIndex + 1
        }
        return 1
    }

    private fun matchingSessionClip(recording: TrackSessionVideoClip): TrackSessionVideoClip? {
        return sessionVideoClips.firstOrNull { clip ->
            clip.kind == TrackSessionVideoKind.SESSION &&
                clip.sessionElapsedAtStartMs == recording.sessionElapsedAtStartMs
        }
    }

    private fun recordingsWithoutSession(): List<TrackSessionVideoClip> {
        return sessionVideoClips.filter { clip ->
            clip.kind == TrackSessionVideoKind.RECORDING &&
                clip.hasPlayableContent() &&
                matchingSessionClip(clip) == null
        }
    }

    private fun bindSessionVideoCard(videoUriString: String, videoPath: String, videoCamera: String) {
        if (sessionVideoClips.isEmpty()) {
            sessionVideoUri = videoUriString
                .takeIf { it.isNotBlank() }
                ?.let(Uri::parse)

            sessionVideoFile = videoPath
                .takeIf { it.isNotBlank() }
                ?.let { File(it) }
                ?.takeIf { it.exists() }
            sessionVideoCameraLabel = videoCamera
        }

        val playbackUri = resolveSessionVideoPlaybackUri()

        if (playbackUri == null) {
            cardSessionVideo.visibility = View.GONE
            hsvSessionVideoClips.visibility = View.GONE
            return
        }

        cardSessionVideo.visibility = View.VISIBLE
        tvSessionVideoTitle.text = getString(R.string.track_session_video_title)
        updateSessionVideoClipChips()
        ivSessionVideoThumbnail.setImageDrawable(null)
        tvSessionVideoMeta.text = buildSessionVideoMetaLabel(sessionVideoCameraLabel, durationLabel = "")
        applySessionVideoOverlayState()
        updateSessionVideoRenderButton(hasPlayableVideo = resolvePublicSessionVideoUri() != null)

        val bindGeneration = ++videoBindGeneration
        lifecycleScope.launch {
            val metadata = withContext(Dispatchers.IO) {
                loadSessionVideoMetadata(playbackUri)
            }
            if (bindGeneration != videoBindGeneration || isFinishing || isDestroyed) {
                metadata?.thumbnail?.recycle()
                return@launch
            }
            if (metadata == null) {
                return@launch
            }

            val durationLabel = formatSessionVideoDuration(metadata.durationMs)
            tvSessionVideoMeta.text = buildSessionVideoMetaLabel(sessionVideoCameraLabel, durationLabel)
            val thumbnail = metadata.thumbnail
            if (thumbnail != null) {
                ivSessionVideoThumbnail.setImageBitmap(thumbnail)
            } else {
                ivSessionVideoThumbnail.setImageDrawable(null)
            }
        }
    }

    private fun buildSessionVideoMetaLabel(videoCamera: String, durationLabel: String): String {
        val metaParts = buildList {
            val date = sessionDateLabel.takeIf { label ->
                label.isNotBlank() && !label.contains("--")
            }
            if (date != null) add(date)
            if (videoCamera.isNotBlank()) add(videoCamera.uppercase(Locale.getDefault()))
            if (durationLabel.isNotBlank()) add(durationLabel)
        }
        return metaParts.joinToString(" • ")
    }

    private fun loadSessionVideoMetadata(playbackUri: Uri): SessionVideoMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, playbackUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            SessionVideoMetadata(
                durationMs = durationMs,
                thumbnail = retriever.getFrameAtTime(0L)
            )
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun updateSessionVideoRenderButton(hasPlayableVideo: Boolean) {
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val lapsPending = TrackSessionVideoOverlayService.isLapsPending(prefs, trackId, outingNumber)
        val hudPending = TrackSessionVideoOverlayService.isHudPending(prefs, trackId, outingNumber)
        val lapsProgress = TrackSessionVideoOverlayService.lapsProgress(prefs, trackId, outingNumber)
        val hudProgress = TrackSessionVideoOverlayService.hudProgress(prefs, trackId, outingNumber)
        val hasRecordingSource = resolveExportSourceClip() != null
        val canExportLaps = hasPlayableVideo &&
            hasRecordingSource &&
            hasManualVideoExportMetadata &&
            !isPointToPointSession &&
            totalLaps > 1 &&
            !isSessionVideoOverlayBlocking()
        btnSessionVideoRender.visibility = if (canExportLaps || lapsPending) {
            View.VISIBLE
        } else {
            View.GONE
        }
        btnSessionVideoRender.isEnabled = canExportLaps && !lapsPending
        btnSessionVideoRender.alpha = if (btnSessionVideoRender.isEnabled) 1f else 0.65f
        btnSessionVideoRender.text = when {
            lapsPending && lapsProgress in 1..99 ->
                getString(R.string.track_session_video_exporting_laps_progress, lapsProgress)
            lapsPending -> getString(R.string.track_session_video_exporting_laps_button)
            else -> getString(R.string.track_session_video_render_button)
        }

        val canExportHud = hasRecordingSource && hasManualVideoExportMetadata && !isSessionVideoOverlayBlocking()
        btnSessionVideoExportHud.visibility = if (canExportHud || hudPending) View.VISIBLE else View.GONE
        btnSessionVideoExportHud.isEnabled = canExportHud && !hudPending
        btnSessionVideoExportHud.alpha = if (btnSessionVideoExportHud.isEnabled) 1f else 0.65f
        btnSessionVideoExportHud.text = when {
            hudPending && hudProgress in 1..99 ->
                getString(R.string.track_session_video_exporting_hud_progress, hudProgress)
            hudPending -> getString(R.string.track_session_video_export_hud_running_button)
            else -> getString(R.string.track_session_video_export_hud_button)
        }

        val overlayPending = TrackSessionVideoOverlayService.isPending(prefs, trackId, outingNumber)
        val canReexport = hasRecordingSource &&
            hasManualVideoExportMetadata &&
            recordingsWithoutSession().isEmpty() &&
            !overlayPending &&
            !lapsPending &&
            !hudPending
        btnSessionVideoReexport.visibility = if (canReexport) View.VISIBLE else View.GONE
    }

    private fun formatSessionVideoDuration(durationMs: Long): String {
        val safeMs = durationMs.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }

    private fun openSessionVideo() {
        val playbackUri = resolvePublicSessionVideoUri()
        if (playbackUri == null) {
            Toast.makeText(
                this,
                if (isSessionVideoOverlayBlocking()) {
                    getString(R.string.track_session_video_overlay_processing)
                } else {
                    getString(R.string.track_session_video_unavailable)
                },
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        startActivity(Intent(this, TrackSessionVideoActivity::class.java).apply {
            putExtra("video_uri", sessionVideoUri?.toString())
            putExtra("video_path", sessionVideoFile?.absolutePath)
            putExtra(
                "video_title",
                currentSessionVideoClipLabel()
            )
        })
    }

    private fun exportSessionVideo() {
        val shareUri = resolvePublicSessionVideoUri()
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
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        if (TrackSessionVideoOverlayService.isLapsPending(prefs, trackId, outingNumber) ||
            isSessionVideoOverlayBlocking()
        ) {
            return
        }

        val sourceUri = resolveExportSourceUri()
        if (sourceUri == null) {
            Toast.makeText(this, getString(R.string.track_session_video_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasManualVideoExportMetadata) {
            Toast.makeText(this, getString(R.string.track_session_video_render_not_supported), Toast.LENGTH_SHORT).show()
            return
        }

        val overlayModel = buildStoredSessionVideoOverlayModel(prefs)
        if (overlayModel == null) {
            Toast.makeText(this, getString(R.string.track_session_video_render_failed), Toast.LENGTH_SHORT).show()
            return
        }

        val completedLaps = overlayModel.lapSegments.filter { segment -> segment.isCompleted }
        if (completedLaps.size <= 1) return
        showTelemetryExportLapPicker(overlayModel, completedLaps)
    }

    private fun maybeStartBackgroundOverlay() {
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val missing = recordingsWithoutSession()
        val force = TrackSessionVideoOverlayService.isForce(prefs, trackId, outingNumber)
        if (missing.isEmpty() && !force) {
            if (TrackSessionVideoOverlayService.isPending(prefs, trackId, outingNumber)) {
                prefs.edit()
                    .putBoolean("${trackId}_outing_${outingNumber}_telemetry_overlay_pending", false)
                    .apply()
            }
            maybeResumeManualExports()
            applySessionVideoOverlayState()
            return
        }
        if (TrackSessionVideoOverlayService.isFailed(prefs, trackId, outingNumber)) {
            maybeResumeManualExports()
            applySessionVideoOverlayState()
            return
        }
        if (TrackSessionVideoOverlayService.isPending(prefs, trackId, outingNumber) ||
            intent.getBooleanExtra(EXTRA_AUTO_EXPORT_TELEMETRY, false)
        ) {
            TrackSessionVideoOverlayService.start(this, trackId, outingNumber, force)
        }
        maybeResumeManualExports()
        applySessionVideoOverlayState()
    }

    private fun maybeResumeManualExports() {
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val lapFrom = TrackSessionVideoOverlayService.storedLapsFrom(prefs, trackId, outingNumber)
        val lapTo = TrackSessionVideoOverlayService.storedLapsTo(prefs, trackId, outingNumber)
        if (TrackSessionVideoOverlayService.isLapsPending(prefs, trackId, outingNumber) &&
            !TrackSessionVideoOverlayService.isLapsFailed(prefs, trackId, outingNumber) &&
            lapFrom > 0 &&
            lapTo > 0
        ) {
            TrackSessionVideoOverlayService.startLaps(
                context = this,
                sessionId = trackId,
                outingNumber = outingNumber,
                trimStartMs = TrackSessionVideoOverlayService.storedLapsTrimStart(prefs, trackId, outingNumber),
                trimEndMs = TrackSessionVideoOverlayService.storedLapsTrimEnd(prefs, trackId, outingNumber),
                lapFrom = lapFrom,
                lapTo = lapTo
            )
        }
        if (TrackSessionVideoOverlayService.isHudPending(prefs, trackId, outingNumber) &&
            !TrackSessionVideoOverlayService.isHudFailed(prefs, trackId, outingNumber)
        ) {
            TrackSessionVideoOverlayService.startHud(this, trackId, outingNumber)
        }
    }

    private fun retryBackgroundOverlay() {
        getSharedPreferences("track_outings", MODE_PRIVATE).edit()
            .putBoolean("${trackId}_outing_${outingNumber}_telemetry_overlay_failed", false)
            .putBoolean("${trackId}_outing_${outingNumber}_telemetry_overlay_pending", true)
            .putInt("${trackId}_outing_${outingNumber}_telemetry_overlay_progress", 0)
            .apply()
        overlayProgressPercent = 0
        TrackSessionVideoOverlayService.start(this, trackId, outingNumber)
        applySessionVideoOverlayState()
    }

    private fun reexportBackgroundOverlay() {
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        if (TrackSessionVideoOverlayService.isPending(prefs, trackId, outingNumber) ||
            TrackSessionVideoOverlayService.isLapsPending(prefs, trackId, outingNumber) ||
            TrackSessionVideoOverlayService.isHudPending(prefs, trackId, outingNumber)
        ) {
            return
        }
        if (resolveExportSourceClip() == null || !hasManualVideoExportMetadata) {
            Toast.makeText(this, getString(R.string.track_session_video_render_not_supported), Toast.LENGTH_SHORT).show()
            return
        }
        overlayProgressPercent = 0
        TrackSessionVideoOverlayService.start(this, trackId, outingNumber, force = true)
        Toast.makeText(this, getString(R.string.track_session_video_export_started), Toast.LENGTH_SHORT).show()
        applySessionVideoOverlayState()
    }

    private fun applySessionVideoOverlayState() {
        if (!::llSessionVideoProcessing.isInitialized) return
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val missing = recordingsWithoutSession()
        val force = TrackSessionVideoOverlayService.isForce(prefs, trackId, outingNumber)
        val pending = TrackSessionVideoOverlayService.isPending(prefs, trackId, outingNumber) &&
            (missing.isNotEmpty() || force)
        val failed = missing.isNotEmpty() &&
            !pending &&
            TrackSessionVideoOverlayService.isFailed(prefs, trackId, outingNumber)
        if (pending && overlayProgressPercent <= 0) {
            overlayProgressPercent = TrackSessionVideoOverlayService.progress(prefs, trackId, outingNumber)
        }
        val publicUri = resolvePublicSessionVideoUri()

        llSessionVideoProcessing.visibility = if (pending || failed) View.VISIBLE else View.GONE
        ivSessionVideoPlay.visibility = if (publicUri != null && !pending && !failed) View.VISIBLE else View.GONE
        btnSessionVideoRetry.visibility = if (failed) View.VISIBLE else View.GONE

        if (pending) {
            pbSessionVideoProcessing.visibility = View.VISIBLE
            if (overlayProgressPercent in 1..99) {
                pbSessionVideoProcessing.isIndeterminate = false
                pbSessionVideoProcessing.progress = overlayProgressPercent
                tvSessionVideoProcessing.text = getString(
                    R.string.track_session_video_overlay_processing_progress,
                    overlayProgressPercent
                )
            } else {
                pbSessionVideoProcessing.isIndeterminate = true
                tvSessionVideoProcessing.text = getString(R.string.track_session_video_overlay_processing)
            }
        } else if (failed) {
            pbSessionVideoProcessing.visibility = View.GONE
            tvSessionVideoProcessing.text = getString(R.string.track_session_video_overlay_failed)
        }

        val canOpen = publicUri != null
        btnSessionVideoOpen.isEnabled = canOpen
        btnSessionVideoExport.isEnabled = canOpen
        btnSessionVideoOpen.alpha = if (canOpen) 1f else 0.45f
        btnSessionVideoExport.alpha = if (canOpen) 1f else 0.45f
        updateSessionVideoRenderButton(hasPlayableVideo = canOpen)
    }

    private fun showTelemetryExportLapPicker(
        overlayModel: TrackSessionVideoOverlayExporter.OverlayModel,
        completedLaps: List<TrackSessionVideoOverlayExporter.LapSegment>
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_track_video_export_laps, null)
        val lapsContainer = dialogView.findViewById<LinearLayout>(R.id.llExportLaps)
        val checkBoxes = completedLaps.map { segment ->
            val checkBox = layoutInflater.inflate(
                R.layout.item_track_video_export_lap,
                lapsContainer,
                false
            ) as CheckBox
            checkBox.text = getString(
                R.string.track_session_video_export_lap_option,
                segment.lapNumber,
                LapTimeFormatter.formatMs(segment.durationMs)
            )
            lapsContainer.addView(checkBox)
            checkBox
        }
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<View>(R.id.btnExportSelectedLaps).setOnClickListener {
            val selected = checkBoxes.indices.filter { index -> checkBoxes[index].isChecked }
            val fromIndex = selected.minOrNull()
            val toIndex = selected.maxOrNull()
            if (fromIndex == null || toIndex == null) {
                Toast.makeText(this, getString(R.string.track_session_video_export_laps_none), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            val fromLap = completedLaps[fromIndex]
            val toLap = completedLaps[toIndex]
            val videoDurationMs = resolveSessionVideoPlaybackUri()
                ?.let { uri -> loadSessionVideoMetadata(uri)?.durationMs }
                ?: 0L
            val (trimStartMs, trimEndMs) = resolveExportTrimRange(
                overlayModel = overlayModel,
                fromLap = fromLap,
                toLap = toLap,
                videoDurationMs = videoDurationMs
            )
            val sourceTrimMs = resolveExportSourceClip()?.sourceTrimStartMs ?: 0L
            enqueueLapsExport(
                trimStartMs = trimStartMs + sourceTrimMs,
                trimEndMs = if (trimEndMs > 0L) trimEndMs + sourceTrimMs else 0L,
                lapFrom = fromLap.lapNumber,
                lapTo = toLap.lapNumber
            )
        }
        dialogView.findViewById<View>(R.id.btnExportLapsCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun resolveExportTrimRange(
        overlayModel: TrackSessionVideoOverlayExporter.OverlayModel,
        fromLap: TrackSessionVideoOverlayExporter.LapSegment,
        toLap: TrackSessionVideoOverlayExporter.LapSegment,
        videoDurationMs: Long
    ): Pair<Long, Long> {
        val videoStart = overlayModel.videoStartSessionElapsedMs
        val trimStartMs = (fromLap.startMs - videoStart).coerceAtLeast(0L)
        val rawEndMs = (toLap.startMs + toLap.durationMs - videoStart).coerceAtLeast(trimStartMs + 1L)
        val trimEndMs = if (videoDurationMs > 0L) {
            rawEndMs.coerceAtMost(videoDurationMs)
        } else {
            rawEndMs
        }
        val clampedStart = if (videoDurationMs > 1L) {
            trimStartMs.coerceAtMost(videoDurationMs - 1L)
        } else {
            trimStartMs
        }
        return clampedStart to trimEndMs.coerceAtLeast(clampedStart + 1L)
    }

    private fun enqueueLapsExport(
        trimStartMs: Long,
        trimEndMs: Long,
        lapFrom: Int,
        lapTo: Int
    ) {
        TrackSessionVideoOverlayService.startLaps(
            context = this,
            sessionId = trackId,
            outingNumber = outingNumber,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            lapFrom = lapFrom,
            lapTo = lapTo
        )
        Toast.makeText(this, getString(R.string.track_session_video_export_started), Toast.LENGTH_SHORT).show()
        applySessionVideoOverlayState()
    }

    private fun exportHudOnlySessionVideo() {
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        if (TrackSessionVideoOverlayService.isHudPending(prefs, trackId, outingNumber) ||
            isSessionVideoOverlayBlocking()
        ) {
            return
        }

        val sourceUri = resolveExportSourceUri()
        if (sourceUri == null) {
            Toast.makeText(this, getString(R.string.track_session_video_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasManualVideoExportMetadata) {
            Toast.makeText(this, getString(R.string.track_session_video_render_not_supported), Toast.LENGTH_SHORT).show()
            return
        }

        val overlayModel = buildStoredSessionVideoOverlayModel(prefs)
        if (overlayModel == null) {
            Toast.makeText(this, getString(R.string.track_session_video_export_hud_failed), Toast.LENGTH_SHORT).show()
            return
        }

        TrackSessionVideoOverlayService.startHud(this, trackId, outingNumber)
        Toast.makeText(this, getString(R.string.track_session_video_export_started), Toast.LENGTH_SHORT).show()
        applySessionVideoOverlayState()
    }

    private fun buildStoredSessionVideoOverlayModel(
        sharedPrefs: android.content.SharedPreferences
    ): TrackSessionVideoOverlayExporter.OverlayModel? {
        return TrackSessionVideoOverlayModels.build(
            context = this,
            prefs = sharedPrefs,
            sessionId = trackId,
            outingNumber = outingNumber,
            totalLaps = totalLaps,
            isMotorcycle = sessionIsMotorcycle,
            isPointToPoint = isPointToPointSession,
            videoStartSessionElapsedMs = sessionVideoElapsedAtStartMs
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

    private fun currentSessionVideoClipLabel(): String {
        val clip = sessionVideoClips.getOrNull(selectedSessionVideoClipIndex) ?: return tvTitle.text.toString()
        return labelForSessionVideoClip(clip)
    }

    private fun resolveExportSourceClip(): TrackSessionVideoClip? {
        val selected = sessionVideoClips.getOrNull(selectedSessionVideoClipIndex)
        if (selected != null && selected.kind == TrackSessionVideoKind.RECORDING && selected.hasPlayableContent()) {
            return selected
        }
        return sessionVideoClips.firstOrNull { clip ->
            clip.kind == TrackSessionVideoKind.RECORDING && clip.hasPlayableContent()
        }
    }

    private fun playbackUriForClip(clip: TrackSessionVideoClip?): Uri? {
        if (clip == null) return null
        clip.uri.takeIf { it.isNotBlank() }?.let { return Uri.parse(it) }
        return clip.path
            .takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.exists() }
            ?.let { file -> FileProvider.getUriForFile(this, "${packageName}.fileprovider", file) }
    }

    private fun resolveExportSourceUri(): Uri? {
        val clip = resolveExportSourceClip() ?: return resolveSessionVideoPlaybackUri()
        clip.uri.takeIf { it.isNotBlank() }?.let { return Uri.parse(it) }
        return clip.path
            .takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.exists() }
            ?.let { file -> FileProvider.getUriForFile(this, "${packageName}.fileprovider", file) }
    }

    private fun resolveSessionVideoPlaybackUri(): Uri? {
        sessionVideoUri?.let { return it }
        return sessionVideoFile
            ?.takeIf { it.exists() }
            ?.let { file ->
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            }
    }

    private fun resolvePublicSessionVideoUri(): Uri? {
        if (isSessionVideoOverlayBlocking()) return null
        val selected = sessionVideoClips.getOrNull(selectedSessionVideoClipIndex)
        if (selected?.kind == TrackSessionVideoKind.RECORDING) {
            val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
            if (TrackSessionVideoOverlayService.isPending(prefs, trackId, outingNumber) ||
                TrackSessionVideoOverlayService.isFailed(prefs, trackId, outingNumber)
            ) {
                return null
            }
        }
        return resolveSessionVideoPlaybackUri()
    }

    private fun isSessionVideoOverlayBlocking(): Boolean {
        val prefs = getSharedPreferences("track_outings", MODE_PRIVATE)
        val pending = TrackSessionVideoOverlayService.isPending(prefs, trackId, outingNumber)
        val failed = TrackSessionVideoOverlayService.isFailed(prefs, trackId, outingNumber)
        if (pending && (
                recordingsWithoutSession().isNotEmpty() ||
                    TrackSessionVideoOverlayService.isForce(prefs, trackId, outingNumber)
            )
        ) {
            return true
        }
        return recordingsWithoutSession().isNotEmpty() && failed
    }

    private fun resolveVideoMimeType(uri: Uri): String {
        return contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            )
            ?: "video/mp4"
    }

    override fun onStop() {
        // Free RaceBox telemetry as soon as we leave — prevents OOM/white screen on back.
        invalidateLapDataCache()
        super.onStop()
    }

    override fun onDestroy() {
        videoBindGeneration++
        runCatching { unregisterReceiver(overlayUpdateReceiver) }
        invalidateLapDataCache()
        super.onDestroy()
    }

    private fun parseLapTime(lapTime: String): Long {
        return parseFlexibleTimeToMs(lapTime) ?: Long.MAX_VALUE
    }

    private fun resolveLapMaxSpeedDisplay(
        sharedPrefs: android.content.SharedPreferences,
        lapNumber: Int
    ): String {
        // Lightweight prefs only in the lap list — never open RaceBox JSON here (ANR / white screen).
        val stored = sharedPrefs.getString(
            "${trackId}_outing_${outingNumber}_lap_${lapNumber}_max_speed",
            null
        ) ?: return "--"
        val rawMaxKmh = parseDisplayedNumeric(stored) ?: return "--"
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
        val rounded = ((deltaMs.coerceAtLeast(0L) + 5L) / 10L) * 10L
        val seconds = rounded / 1_000L
        val centis = (rounded % 1_000L) / 10L
        return String.format(java.util.Locale.US, "+%d.%02d", seconds, centis)
    }

    private fun parseFlexibleTimeToMs(value: String): Long? = LapTimeFormatter.parseToMs(value)

    private fun formatTimeMs(totalMs: Long): String = LapTimeFormatter.formatMs(totalMs)

    private fun compactSpeedForHeader(rawSpeed: String): String {
        if (rawSpeed.isBlank()) return "--"
        return UnitsManager.formatStoredSpeed(rawSpeed, this).replace(" ", "")
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
        val stored = prefs.getFloat("${trackId}_outing_${outingNumber}_distance_km", -1f)
        if (stored.isFinite() && stored >= 0f) {
            return stored.toDouble()
        }

        var totalDistanceKm = 0.0
        val foundLapData = forEachSessionLapData(prefs) { lapData ->
            if (lapData.routePoints.size > 1) {
                totalDistanceKm += calculateDistanceKm(lapData.routePoints)
            }
        }

        if (!foundLapData) return 0.0
        // Cache for next open so we don't re-parse all RaceBox points on every visit.
        prefs.edit().putFloat("${trackId}_outing_${outingNumber}_distance_km", totalDistanceKm.toFloat()).apply()
        return totalDistanceKm
    }

    private fun parseSessionDurationMs(duration: String): Long =
        parseFlexibleTimeToMs(duration) ?: 0L

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
                val customTrack = com.revix.app.tracking.CustomTrackStorage.loadCustomTrack(this, normalizedTrackId)
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
            val activeProfileId = ProfileStorage.getSelectedProfileId(this)

            android.util.Log.d("TrackSessionDetailActivity", "getActiveVehicleName: activeProfileId=$activeProfileId")

            val profiles = ProfileStorage.loadProfiles(this)
            if (activeProfileId != -1L) {
                val activeProfile = profiles.find { it.id == activeProfileId }
                if (activeProfile != null && activeProfile.name.isNotEmpty()) {
                    android.util.Log.d("TrackSessionDetailActivity", "getActiveVehicleName: found active profile vehicle='${activeProfile.name}'")
                    return activeProfile.name
                }
            }

            val profileWithVehicle = profiles.find { it.name.isNotEmpty() }
            if (profileWithVehicle != null) {
                android.util.Log.d("TrackSessionDetailActivity", "getActiveVehicleName: found fallback vehicle='${profileWithVehicle.name}'")
                return profileWithVehicle.name
            }
            
            android.util.Log.d("TrackSessionDetailActivity", "getActiveVehicleName: no profiles found")
        } catch (e: Exception) {
            android.util.Log.e("TrackSessionDetailActivity", "getActiveVehicleName: error loading profiles", e)
        }
        
        return getString(R.string.garage_no_vehicle)
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
        invalidateLapDataCache()
        finish()
    }
}
