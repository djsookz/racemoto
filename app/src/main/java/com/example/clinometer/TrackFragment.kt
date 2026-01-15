package com.example.clinometer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.example.clinometer.data.ProfileStorage
import com.google.android.material.button.MaterialButton
import com.example.clinometer.settings.UnitsManager
import com.example.clinometer.network.WeatherApiService
import com.example.clinometer.network.OpenMeteoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Fragment за Track страницата - конвертиран от TrackActivity
 */
class TrackFragment : Fragment(), LocationListener {
    
    private lateinit var btnStartNewSession: android.widget.Button
    private lateinit var btnViewTrack: MaterialButton
    private lateinit var llEnvironment: LinearLayout
    private lateinit var tvTemperature: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvHeaderModelName: TextView
    private lateinit var ivHeaderProfileImage: android.widget.ImageView
    private lateinit var locationManager: LocationManager
    
    // Професионално решение: lazy initialization на SharedPreferences
    private val profilePrefs by lazy { requireContext().getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE) }
    
    // Създаваме слушателя като променлива на класа (ВАЖНО, за да не бъде изтрит от Garbage Collector)
    private val profileChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "selected_profile_id") {
            loadProfileInfo()
        }
    }
    private var currentTemperature: Float? = null
    private var currentAltitude: Float? = null
    private lateinit var headerSofiaRing: LinearLayout
    private lateinit var contentSofiaRing: LinearLayout
    private lateinit var arrowSofiaRing: TextView
    private lateinit var headerCustomTrack: LinearLayout
    private lateinit var contentCustomTrack: LinearLayout
    private lateinit var arrowCustomTrack: TextView
    
    private lateinit var session1SofiaRing: LinearLayout
    private lateinit var session2SofiaRing: LinearLayout
    private lateinit var session3SofiaRing: LinearLayout
    private lateinit var session1CustomTrack: LinearLayout
    private lateinit var session2CustomTrack: LinearLayout
    
    private lateinit var tvNoSessions: TextView
    private lateinit var llSessionsContainer: LinearLayout
    
    private var sofiaRingExpanded = true
    private var customTrackExpanded = true
    
    private var hasActiveSession = false
    private var activeSessionTrackId: String? = null
    private var activeSessionTrackName: String? = null
    
    private lateinit var trackManager: TrackManager
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val CACHE_LOCATION_THRESHOLD_KM = 5.0
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_track, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        trackManager = TrackManager(requireContext())
        initializeViews(view)
        setupClickListeners()
        
        // Регистрираме слушателя
        profilePrefs.registerOnSharedPreferenceChangeListener(profileChangeListener)
        
        // Първоначално зареждане
        view.post {
            loadProfileInfo()
        }
        
        loadCachedWeatherData()
        updateEnvironmentDisplay()
        setupLocation()
        checkActiveSessions()
        showNoSessionsMessage()
    }
    
    private fun initializeViews(view: View) {
        btnStartNewSession = view.findViewById(R.id.btnStartNewSession)
        btnViewTrack = view.findViewById(R.id.btnViewTrack)
        llEnvironment = view.findViewById(R.id.llEnvironment)
        tvTemperature = view.findViewById(R.id.tvTemperature)
        tvAltitude = view.findViewById(R.id.tvAltitude)
        tvHeaderModelName = view.findViewById(R.id.tvHeaderModelName)
        ivHeaderProfileImage = view.findViewById(R.id.ivHeaderProfileImage)
        headerSofiaRing = view.findViewById(R.id.headerSofiaRing)
        contentSofiaRing = view.findViewById(R.id.contentSofiaRing)
        arrowSofiaRing = view.findViewById(R.id.arrowSofiaRing)
        headerCustomTrack = view.findViewById(R.id.headerCustomTrack)
        contentCustomTrack = view.findViewById(R.id.contentCustomTrack)
        arrowCustomTrack = view.findViewById(R.id.arrowCustomTrack)
        
        session1SofiaRing = view.findViewById(R.id.session1SofiaRing)
        session2SofiaRing = view.findViewById(R.id.session2SofiaRing)
        session3SofiaRing = view.findViewById(R.id.session3SofiaRing)
        session1CustomTrack = view.findViewById(R.id.session1CustomTrack)
        session2CustomTrack = view.findViewById(R.id.session2CustomTrack)
        
        tvNoSessions = view.findViewById(R.id.tvNoSessions)
        llSessionsContainer = view.findViewById(R.id.llSessionsContainer)
    }
    
    private fun setupClickListeners() {
        btnStartNewSession.setOnClickListener { startNewSession() }
        btnViewTrack.setOnClickListener {
            if (hasActiveSession) {
                resumeSession()
            } else {
                openTrackMap()
            }
        }
        
        headerSofiaRing.setOnClickListener { toggleAccordion("sofiaRing") }
        headerCustomTrack.setOnClickListener { toggleAccordion("customTrack") }
        setupSessionClickListeners()
    }
    
    private fun setupSessionClickListeners() {
        // Sofia Ring sessions - placeholder implementation
        session1SofiaRing.setOnClickListener {
            openSessionDetail(
                "Излизане #1", "23.12.2024", "14:30", "2:15:30",
                "Sofia Ring", 15, "Kawasaki Ninja ZX-10R",
                "1:23.456", "45 km/h", "285 km/h", "2.8g", "1.2g"
            )
        }
        
        session2SofiaRing.setOnClickListener {
            openSessionDetail(
                "Излизане #2", "23.12.2024", "16:45", "1:45:20",
                "Sofia Ring", 12, "Kawasaki Ninja ZX-10R",
                "1:25.123", "52 km/h", "278 km/h", "2.6g", "1.1g"
            )
        }
        
        session3SofiaRing.setOnClickListener {
            openSessionDetail(
                "Излизане #3", "23.12.2024", "18:20", "2:00:10",
                "Sofia Ring", 18, "Kawasaki Ninja ZX-10R",
                "1:22.890", "48 km/h", "290 km/h", "2.9g", "1.3g"
            )
        }
        
        // Custom Track sessions
        session1CustomTrack.setOnClickListener {
            openSessionDetail(
                "Излизане #1", "22.12.2024", "15:00", "1:30:45",
                "Custom Track", 10, "Kawasaki Ninja ZX-10R",
                "1:18.567", "55 km/h", "275 km/h", "2.5g", "1.0g"
            )
        }
        
        session2CustomTrack.setOnClickListener {
            openSessionDetail(
                "Излизане #2", "22.12.2024", "17:15", "1:55:30",
                "Custom Track", 14, "Kawasaki Ninja ZX-10R",
                "1:19.234", "50 km/h", "280 km/h", "2.7g", "1.1g"
            )
        }
    }
    
    private fun openSessionDetail(
        sessionNumber: String, sessionDate: String, sessionTime: String,
        duration: String, trackName: String, laps: Int, vehicle: String,
        bestLapTime: String, minSpeed: String, maxSpeed: String,
        maxAcceleration: String, maxCornering: String
    ) {
        val intent = Intent(requireContext(), TrackSessionDetailActivity::class.java).apply {
            putExtra("session_number", sessionNumber)
            putExtra("session_date", sessionDate)
            putExtra("session_time", sessionTime)
            putExtra("duration", duration)
            putExtra("track_name", trackName)
            putExtra("laps", laps)
            putExtra("vehicle", vehicle)
            putExtra("best_lap_time", bestLapTime)
            putExtra("min_speed", minSpeed)
            putExtra("max_speed", maxSpeed)
            putExtra("max_acceleration", maxAcceleration)
            putExtra("max_cornering", maxCornering)
        }
        startActivity(intent)
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
        clearActiveSession()
        val intent = Intent(requireContext(), TrackSelectionActivity::class.java)
        startActivity(intent)
    }
    
    private fun openTrackMap() {
        val intent = Intent(requireContext(), TrackMapActivity::class.java).apply {
            putExtra("track_id", "serres_circuit")
            putExtra("track_name", getString(R.string.track_name_serres))
        }
        startActivity(intent)
    }
    
    private fun clearActiveSession() {
        val sharedPrefs = requireContext().getSharedPreferences("track_sessions", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putBoolean("has_active_session", false)
            remove("active_track_id")
            remove("active_track_name")
            apply()
        }
        hasActiveSession = false
        activeSessionTrackId = null
        activeSessionTrackName = null
    }
    
    private fun showDeleteConfirmationDialog(sessionId: String, trackName: String, sessionCard: View) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.track_delete_session_title))
        builder.setMessage(getString(R.string.track_delete_session_message, trackName))
        
        builder.setPositiveButton(getString(R.string.track_delete_button)) { _, _ ->
            deleteSession(sessionId, sessionCard)
        }
        
        builder.setNegativeButton(getString(R.string.cancel), null)
        
        val dialog = builder.create()
        dialog.show()
        
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(
            ContextCompat.getColor(requireContext(), R.color.red)
        )
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(
            ContextCompat.getColor(requireContext(), R.color.accent_blue)
        )
    }
    
    private fun deleteSession(sessionId: String, sessionCard: View) {
        val sharedPrefs = requireContext().getSharedPreferences("track_outings", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        
        val allKeys = sharedPrefs.all.keys
        val sessionKeys = allKeys.filter { it.startsWith("${sessionId}_") }
        
        for (key in sessionKeys) {
            editor.remove(key)
        }
        
        if (activeSessionTrackId == sessionId) {
            clearActiveSession()
        }
        
        editor.apply()
        llSessionsContainer.removeView(sessionCard)
        showToast(getString(R.string.track_session_deleted))
        showNoSessionsMessage()
    }
    
    private fun checkActiveSessions() {
        val sharedPrefs = requireContext().getSharedPreferences("track_sessions", Context.MODE_PRIVATE)
        hasActiveSession = sharedPrefs.getBoolean("has_active_session", false)
        activeSessionTrackId = sharedPrefs.getString("active_track_id", null)
        activeSessionTrackName = sharedPrefs.getString("active_track_name", null)
        
        loadAllSessions()
        updateViewTrackButton()
    }
    
    private fun loadAllSessions() {
        val sharedPrefs = requireContext().getSharedPreferences("track_outings", Context.MODE_PRIVATE)
        val currentProfileId = ProfileStorage.getSelectedProfileId(requireContext())
        val allKeys = sharedPrefs.all.keys
        
        llSessionsContainer.removeAllViews()
        
        val sessionIds = mutableSetOf<String>()
        for (key in allKeys) {
            if (key.endsWith("_outing_count")) {
                val sessionIdFull = key.removeSuffix("_outing_count")
                if (sessionIdFull.startsWith("${currentProfileId}_")) {
                    sessionIds.add(sessionIdFull)
                }
            }
        }
        
        if (sessionIds.isEmpty()) {
            showNoSessionsMessage()
        } else {
            val sortedSessionIds = sessionIds.sortedByDescending { sessionIdFull ->
                val sessionId = sessionIdFull.substringAfter("_")
                val parts = sessionId.split("_")
                when {
                    parts.size >= 4 -> parts.last().toLongOrNull() ?: 0L
                    parts.size >= 3 -> {
                        try {
                            val date = parts[parts.size - 2]
                            val time = parts[parts.size - 1]
                            val dateTime = "$date $time"
                            val formatter = java.text.SimpleDateFormat("dd.MM.yyyy HHmm", java.util.Locale.getDefault())
                            formatter.parse(dateTime)?.time ?: 0L
                        } catch (e: Exception) {
                            0L
                        }
                    }
                    else -> 0L
                }
            }
            
            for (sessionIdFull in sortedSessionIds) {
                val visibleId = sessionIdFull.substringAfter("_")
                val parts = visibleId.split("_")
                val trackId = when {
                    parts.size >= 4 -> parts.dropLast(3).joinToString("_")
                    parts.size >= 3 -> parts.dropLast(2).joinToString("_")
                    else -> visibleId
                }
                val trackName = getTrackName(trackId)
                createSession(sessionIdFull, trackName)
            }
        }
    }
    
    private fun getTrackName(trackId: String): String {
        return when (trackId) {
            "serres_circuit" -> getString(R.string.track_name_serres)
            "sofia_ring" -> getString(R.string.track_name_sofia)
            "custom_track" -> getString(R.string.track_name_custom)
            else -> {
                if (trackId.startsWith("custom_")) {
                    val customTrack = com.example.clinometer.tracking.CustomTrackStorage.loadCustomTrack(requireContext(), trackId)
                    customTrack?.name ?: getString(R.string.track_name_unknown)
                } else {
                    getString(R.string.track_name_unknown)
                }
            }
        }
    }
    
    private fun showNoSessionsMessage() {
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
            val parts = activeSessionTrackId!!.split("_")
            val trackId = when {
                parts.size >= 4 -> parts.dropLast(3).joinToString("_")
                parts.size >= 3 -> parts.dropLast(2).joinToString("_")
                else -> activeSessionTrackId!!
            }
            
            val intent = Intent(requireContext(), TrackSessionActivity::class.java).apply {
                putExtra("track_id", trackId)
                putExtra("track_name", activeSessionTrackName)
                putExtra("resume_session", true)
                putExtra("session_id", activeSessionTrackId)
                putExtra("is_motorcycle", true)
            }
            
            startActivity(intent)
        }
    }
    
    private fun resumeSpecificSession(sessionId: String, trackName: String) {
        val parts = sessionId.split("_")
        val trackId = when {
            parts.size >= 4 -> parts.dropLast(3).joinToString("_")
            parts.size >= 3 -> parts.dropLast(2).joinToString("_")
            else -> sessionId
        }
        
        val intent = Intent(requireContext(), TrackSessionActivity::class.java).apply {
            putExtra("track_id", trackId)
            putExtra("track_name", trackName)
            putExtra("resume_session", true)
            putExtra("session_id", sessionId)
            putExtra("is_motorcycle", true)
        }
        
        startActivity(intent)
    }
    
    private fun createSession(sessionIdFull: String, trackName: String) {
        if (!sessionExists(sessionIdFull)) {
            val sessionCard = createSessionCard(sessionIdFull, trackName)
            llSessionsContainer.addView(sessionCard)
            showNoSessionsMessage()
        }
    }
    
    private fun sessionExists(sessionId: String): Boolean {
        for (i in 0 until llSessionsContainer.childCount) {
            val child = llSessionsContainer.getChildAt(i)
            if (child.tag == sessionId) {
                return true
            }
        }
        return false
    }
    
    private fun createSessionCard(sessionIdFull: String, trackName: String): View {
        val inflater = LayoutInflater.from(requireContext())
        val sessionCard = inflater.inflate(R.layout.session_card_template, llSessionsContainer, false)
        
        sessionCard.tag = sessionIdFull
        
        val visibleId = sessionIdFull.substringAfter("_")
        val parts = visibleId.split("_")
        val trackId: String
        val sessionDate: String
        val sessionTime: String
        
        if (parts.size >= 4) {
            trackId = parts.dropLast(3).joinToString("_")
            sessionDate = parts[parts.size - 3]
            sessionTime = parts[parts.size - 2]
        } else if (parts.size >= 3) {
            trackId = parts.dropLast(2).joinToString("_")
            sessionDate = parts[parts.size - 2]
            sessionTime = parts[parts.size - 1]
        } else {
            trackId = visibleId
            sessionDate = ""
            sessionTime = ""
        }
        
        val tvTrackName = sessionCard.findViewById<TextView>(R.id.tvTrackName)
        tvTrackName.text = trackName
        
        val tvTrackDetails = sessionCard.findViewById<TextView>(R.id.tvTrackDetails)
        if (sessionDate.isNotEmpty() && sessionTime.isNotEmpty()) {
            val formattedTime = if (sessionTime.length == 4) {
                "${sessionTime.substring(0, 2)}:${sessionTime.substring(2, 4)}"
            } else {
                sessionTime
            }
            tvTrackDetails.text = "$sessionDate • $formattedTime"
        } else {
            tvTrackDetails.text = "Нова сесия"
        }
        
        val btnResume = sessionCard.findViewById<MaterialButton>(R.id.btnResume)
        btnResume.setOnClickListener {
            resumeSpecificSession(sessionIdFull, trackName)
        }
        
        val btnDeleteSession = sessionCard.findViewById<ImageButton>(R.id.btnDeleteSession)
        btnDeleteSession.setOnClickListener {
            showDeleteConfirmationDialog(sessionIdFull, trackName, sessionCard)
        }
        
        val headerLayout = sessionCard.findViewById<LinearLayout>(R.id.headerLayout)
        val contentLayout = sessionCard.findViewById<LinearLayout>(R.id.contentLayout)
        val arrow = sessionCard.findViewById<TextView>(R.id.arrow)
        
        headerLayout.setOnClickListener {
            toggleSessionExpansion(contentLayout, arrow)
        }
        
        loadOutingsForSession(sessionCard, sessionIdFull)
        
        return sessionCard
    }
    
    private fun loadOutingsForSession(sessionCard: View, sessionId: String) {
        val sharedPrefs = requireContext().getSharedPreferences("track_outings", Context.MODE_PRIVATE)
        val outingCount = sharedPrefs.getInt("${sessionId}_outing_count", 0)
        val contentLayout = sessionCard.findViewById<LinearLayout>(R.id.contentLayout)
        val tvNoOutings = sessionCard.findViewById<TextView>(R.id.tvNoOutings)
        
        if (outingCount == 0) {
            tvNoOutings.visibility = View.VISIBLE
        } else {
            tvNoOutings.visibility = View.GONE
            
            for (i in 1..outingCount) {
                val outingView = createOutingView(sessionId, i, sharedPrefs)
                contentLayout.addView(outingView)
            }
        }
    }
    
    private fun createOutingView(sessionId: String, outingNumber: Int, sharedPrefs: android.content.SharedPreferences): View {
        val outingView = LayoutInflater.from(requireContext()).inflate(R.layout.outing_item_template, null)
        
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
        
        outingView.setOnClickListener {
            openOutingDetail(sessionId, outingNumber, sharedPrefs)
        }
        
        return outingView
    }
    
    private fun openOutingDetail(sessionIdFull: String, outingNumber: Int, sharedPrefs: android.content.SharedPreferences) {
        val intent = Intent(requireContext(), TrackSessionDetailActivity::class.java)
        
        val visibleId = sessionIdFull.substringAfter("_")
        val trackId = visibleId.split("_")[0]
        
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
    
    private fun setupLocation() {
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0f, this)
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupLocation()
            }
        }
    }
    
    override fun onLocationChanged(location: Location) {
        // Check if fragment is attached before accessing context
        if (!isAdded || context == null) {
            return
        }
        val shouldFetch = shouldFetchWeatherData(location)
        if (shouldFetch) {
            fetchWeatherFromAPI(location)
        }
    }
    
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    
    override fun onResume() {
        super.onResume()
        loadProfileInfo()
        loadCachedWeatherData()
        updateEnvironmentDisplay()
        checkActiveSessions()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Важно: отписваме се, за да няма memory leaks
        profilePrefs.unregisterOnSharedPreferenceChangeListener(profileChangeListener)
    }
    
    override fun onPause() {
        super.onPause()
        locationManager.removeUpdates(this)
    }
    
    private fun updateEnvironmentDisplay() {
        val context = context ?: return
        val tempText = if (currentTemperature != null) {
            UnitsManager.formatTemperature(currentTemperature!!, context, decimals = 0)
        } else {
            val unit = UnitsManager.getTemperatureUnit(context)
            "--${unit.symbol}"
        }
        
        val altText = if (currentAltitude != null) {
            String.format("%.0fm", currentAltitude)
        } else {
            "--m"
        }
        
        tvTemperature.text = tempText
        tvAltitude.text = altText
        
        if (currentTemperature != null || currentAltitude != null) {
            llEnvironment.visibility = LinearLayout.VISIBLE
        }
    }
    
    private fun fetchWeatherFromAPI(location: Location) {
        // Check if fragment is attached before starting coroutine
        if (!isAdded || context == null) {
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
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
                
                val weatherResponse = weatherApiService.getCurrentWeather(
                    apiKey = "547cc84c36a447ab8fe131642251808",
                    location = "${location.latitude},${location.longitude}",
                    lang = "bg"
                )
                
                if (weatherResponse.isSuccessful && weatherResponse.body() != null) {
                    val weather = weatherResponse.body()!!
                    currentTemperature = weather.current.temp_c.toFloat()
                }
                
                val elevationResponse = openMeteoService.getElevation(
                    location.latitude,
                    location.longitude
                )
                
                if (elevationResponse.isSuccessful && elevationResponse.body() != null) {
                    val elevation = elevationResponse.body()!!
                    currentAltitude = elevation.elevation.firstOrNull()?.toFloat() ?: 0f
                }
                
                // Check again before accessing context in cacheWeatherData
                if (isAdded && context != null) {
                    cacheWeatherData(location)
                    
                    withContext(Dispatchers.Main) {
                        // Check again before updating UI
                        if (isAdded && view != null) {
                            updateEnvironmentDisplay()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TrackFragment", "Error fetching weather data", e)
            }
        }
    }
    
    private fun loadCachedWeatherData() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val cachedTemp = prefs.getFloat("cached_temperature", Float.NaN)
        val cachedAlt = prefs.getFloat("cached_altitude", Float.NaN)
        val cachedLat = prefs.getFloat("cached_location_lat", Float.NaN)
        val cachedLon = prefs.getFloat("cached_location_lon", Float.NaN)
        
        if (!cachedTemp.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentTemperature = cachedTemp
        }
        
        if (!cachedAlt.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentAltitude = cachedAlt
        }
    }
    
    private fun cacheWeatherData(location: Location) {
        val context = context ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        
        currentTemperature?.let { editor.putFloat("cached_temperature", it) }
        currentAltitude?.let { editor.putFloat("cached_altitude", it) }
        editor.putFloat("cached_location_lat", location.latitude.toFloat())
        editor.putFloat("cached_location_lon", location.longitude.toFloat())
        editor.apply()
    }
    
    private fun shouldFetchWeatherData(location: Location): Boolean {
        // Check if fragment is attached before accessing context
        val context = context ?: return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val cachedLat = prefs.getFloat("cached_location_lat", Float.NaN)
        val cachedLon = prefs.getFloat("cached_location_lon", Float.NaN)
        
        if (cachedLat.isNaN() || cachedLon.isNaN()) {
            return true
        }
        
        val cachedLocation = Location("cached").apply {
            latitude = cachedLat.toDouble()
            longitude = cachedLon.toDouble()
        }
        val distanceKm = location.distanceTo(cachedLocation) / 1000.0
        
        return distanceKm > CACHE_LOCATION_THRESHOLD_KM
    }
    
    private fun showToast(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }
    
    // ЕЛЕМЕНТАРНО: Зареждане на модела и снимката от активния профил
    private fun loadProfileInfo() {
        if (!isAdded || view == null) return
        
        val selectedId = ProfileStorage.getSelectedProfileId(requireContext())
        val profiles = ProfileStorage.loadProfiles(requireContext())
        val activeProfile = profiles.find { it.id == selectedId }

        if (activeProfile != null) {
            // 1. Зареждаме модела: "Audi A6" -> "A6"
            val fullName = activeProfile.name.trim()
            val modelName = if (fullName.contains(" ")) {
                fullName.substringAfterLast(" ")
            } else {
                fullName
            }
            tvHeaderModelName.text = modelName
            tvHeaderModelName.setTextColor(android.graphics.Color.WHITE)
            tvHeaderModelName.visibility = View.VISIBLE

            // 2. Зареждаме снимката или показваме иконка
            if (!activeProfile.imagePath.isNullOrEmpty()) {
                val imageFile = java.io.File(requireContext().getExternalFilesDir(null), activeProfile.imagePath)
                if (imageFile.exists()) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (bitmap != null) {
                        ivHeaderProfileImage.setImageBitmap(bitmap)
                        ivHeaderProfileImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        ivHeaderProfileImage.setPadding(0, 0, 0, 0)
                    } else {
                        showDefaultIcon(activeProfile.vehicleType)
                    }
                } else {
                    showDefaultIcon(activeProfile.vehicleType)
                }
            } else {
                showDefaultIcon(activeProfile.vehicleType)
            }
        } else {
            tvHeaderModelName.text = ""
            showDefaultIcon(Profile.VehicleType.CAR)
        }
    }
    
    private fun showDefaultIcon(type: Profile.VehicleType) {
        val icon = if (type == Profile.VehicleType.CAR) R.drawable.ic_car else R.drawable.ic_motorcycle
        ivHeaderProfileImage.setImageResource(icon)
        ivHeaderProfileImage.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        val padding = (6 * resources.displayMetrics.density).toInt()
        ivHeaderProfileImage.setPadding(padding, padding, padding, padding)
        ivHeaderProfileImage.visibility = View.VISIBLE
    }
}
