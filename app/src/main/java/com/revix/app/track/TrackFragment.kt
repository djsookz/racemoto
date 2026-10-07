package com.revix.app

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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.revix.app.data.ProfileStorage
import com.google.android.material.button.MaterialButton
import com.revix.app.settings.UnitsManager
import com.revix.app.track.TrackOutingsRepository
import com.revix.app.track.TrackSessionsActivity
import com.revix.app.network.WeatherApiService
import com.revix.app.network.OpenMeteoService
import com.revix.app.utils.LapTimeFormatter
import com.revix.app.utils.WeatherIconMapper
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
    private lateinit var llEnvironment: LinearLayout
    private lateinit var tvTemperature: TextView
    private lateinit var tvWeatherHumidity: TextView
    private lateinit var tvWeatherWind: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvHeaderModelName: TextView
    private lateinit var ivWeatherCondition: ImageView
    private lateinit var ivHeaderProfileImage: android.widget.ImageView
    private lateinit var locationManager: LocationManager
    
    // Професионално решение: lazy initialization на SharedPreferences
    private val profilePrefs by lazy { requireContext().getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE) }
    
    // Създаваме слушателя като променлива на класа (ВАЖНО, за да не бъде изтрит от Garbage Collector)
    private val profileChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "selected_profile_id") {
            loadProfileInfo()
            checkActiveSessions()
        }
    }
    private var currentTemperature: Float? = null
    private var currentAltitude: Float? = null
    private var currentHumidity: Int? = null
    private var currentWindKph: Float? = null
    private var currentWeatherIcon: Int = R.drawable.ic_weather_cloudy
    
    private lateinit var tvNoSessions: TextView
    private lateinit var llSessionsContainer: LinearLayout
    
    private var hasActiveSession = false
    private var activeSessionTrackId: String? = null
    private var activeSessionTrackName: String? = null
    private var activeSessionId: String? = null
    
    private lateinit var trackManager: TrackManager
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
        private const val CACHE_LOCATION_THRESHOLD_KM = 5.0
        private const val WEATHER_REFRESH_INTERVAL_MS = 15 * 60 * 1000L
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
        llEnvironment = view.findViewById(R.id.llEnvironment)
        tvTemperature = view.findViewById(R.id.tvTemperature)
        tvWeatherHumidity = view.findViewById(R.id.tvWeatherHumidity)
        tvWeatherWind = view.findViewById(R.id.tvWeatherWind)
        tvAltitude = view.findViewById(R.id.tvAltitude)
        tvHeaderModelName = view.findViewById(R.id.tvHeaderModelName)
        ivWeatherCondition = view.findViewById(R.id.ivWeatherCondition)
        ivHeaderProfileImage = view.findViewById(R.id.ivHeaderProfileImage)
        tvNoSessions = view.findViewById(R.id.tvNoSessions)
        llSessionsContainer = view.findViewById(R.id.llSessionsContainer)
    }
    
    private fun setupClickListeners() {
        btnStartNewSession.setOnClickListener { startNewSession() }
    }
    
    private fun startNewSession() {
        if (!com.revix.app.billing.ProGate.ensureDragOrTrack(
                requireContext(),
                com.revix.app.billing.ProAccess.Feature.TRACK
            )
        ) {
            return
        }
        if (!verifyCalibrationBeforeTrackFlow()) {
            return
        }
        clearActiveSession()
        val intent = Intent(requireContext(), TrackSelectionActivity::class.java)
        startActivity(intent)
    }

    private fun verifyCalibrationBeforeTrackFlow(): Boolean {
        val selectedProfileId = ProfileStorage.getSelectedProfileId(requireContext())
        if (selectedProfileId != -1L) {
            DragCalibration.setProfile(selectedProfileId)
        }

        if (!DragCalibration.isCalibrated) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
                .setTitle(getString(R.string.track_calibration_missing_title))
                .setMessage(getString(R.string.track_calibration_missing_message))
                .setPositiveButton(getString(R.string.drag_calibration_open_button)) { _, _ ->
                    startActivity(
                        Intent(requireContext(), DragCalibrationActivity::class.java).apply {
                            putExtra("PROFILE_ID", selectedProfileId)
                            putExtra("IS_FIRST_PROFILE", false)
                            putExtra("IS_NEW_PROFILE", false)
                            putExtra("IS_FIRST_LAUNCH", false)
                        }
                    )
                }
                .setNegativeButton(getString(R.string.dialog_cancel_button), null)
                .show()
            return false
        }
        return true
    }
    
    private fun clearActiveSession() {
        val sharedPrefs = requireContext().getSharedPreferences("track_sessions", Context.MODE_PRIVATE)
        sharedPrefs.edit().apply {
            putBoolean("has_active_session", false)
            putBoolean("active_session_has_lap", false)
            remove("active_track_id")
            remove("active_track_name")
            remove("active_session_id")
            remove("pending_session_id_raw")
            apply()
        }
        hasActiveSession = false
        activeSessionTrackId = null
        activeSessionTrackName = null
        activeSessionId = null
    }
    
    private fun checkActiveSessions() {
        val sharedPrefs = requireContext().getSharedPreferences("track_sessions", Context.MODE_PRIVATE)
        hasActiveSession = sharedPrefs.getBoolean("has_active_session", false)
        // Drop ghost "In progress" cards from cancel-before-first-lap (legacy + current).
        if (hasActiveSession && !sharedPrefs.getBoolean("active_session_has_lap", false)) {
            clearActiveSession()
        } else {
            activeSessionTrackId = sharedPrefs.getString("active_track_id", null)
            activeSessionTrackName = sharedPrefs.getString("active_track_name", null)
            activeSessionId = sharedPrefs.getString("active_session_id", null)
        }
        
        loadAllSessions()
    }

    private fun loadAllSessions() {
        val context = requireContext()
        val currentProfileId = ProfileStorage.getSelectedProfileId(context)
        val sessionIds = TrackOutingsRepository.loadSessionIdsForProfile(context, currentProfileId)
            .sortedByDescending { TrackOutingsRepository.resolveSessionTimestamp(it) }

        llSessionsContainer.removeAllViews()

        for (sessionId in sessionIds) {
            val stats = TrackOutingsRepository.buildSessionBucketStats(context, sessionId)
            createTrackCard(sessionId, stats)
        }

        val activeBelongsToProfile = !activeSessionId.isNullOrBlank() &&
            activeSessionId!!.startsWith("${currentProfileId}_")
        if (hasActiveSession && activeBelongsToProfile && !sessionIds.contains(activeSessionId)) {
            val trackId = activeSessionTrackId
                ?: TrackOutingsRepository.extractTrackIdFromSessionId(context, activeSessionId!!)
            val trackName = activeSessionTrackName ?: getTrackName(trackId)
            createTrackCard(
                sessionIdFull = activeSessionId!!,
                trackAggregateStats = TrackOutingsRepository.TrackAggregateStats(
                    totalSessions = 0,
                    totalLaps = 0,
                    totalDurationMs = 0L,
                    bestLapMs = null,
                    bestLapSessionId = null,
                    bestLapOutingNumber = null
                ),
                trackNameOverride = trackName,
                isInProgress = true
            )
        }

        showNoSessionsMessage()
    }

    private fun getTrackName(trackId: String): String {
        return TrackOutingsRepository.getTrackName(requireContext(), trackId)
    }
    
    private fun showNoSessionsMessage() {
        if (llSessionsContainer.childCount == 0) {
            tvNoSessions.visibility = View.VISIBLE
        } else {
            tvNoSessions.visibility = View.GONE
        }
    }
    
    private fun resumeSpecificSession(sessionId: String, trackName: String) {
        if (!com.revix.app.billing.ProGate.ensureDragOrTrack(
                requireContext(),
                com.revix.app.billing.ProAccess.Feature.TRACK
            )
        ) {
            return
        }
        val trackId = TrackOutingsRepository.extractTrackIdFromSessionId(requireContext(), sessionId)
        val isMotorcycleProfile = isCurrentProfileMotorcycle()
        
        val intent = Intent(requireContext(), TrackSessionActivity::class.java).apply {
            putExtra("track_id", trackId)
            putExtra("track_name", trackName)
            putExtra("resume_session", true)
            putExtra("session_id", sessionId)
            putExtra("is_official", !trackId.startsWith("custom_"))
            putExtra("is_motorcycle", isMotorcycleProfile)
        }
        
        startActivity(intent)
    }

    private fun isCurrentProfileMotorcycle(): Boolean {
        val selectedProfileId = ProfileStorage.getSelectedProfileId(requireContext())
        val selectedProfile = ProfileStorage.loadProfiles(requireContext()).find { it.id == selectedProfileId }
        if (selectedProfile == null) {
            android.util.Log.w("TrackFragment", "Selected profile '$selectedProfileId' not found; defaulting to motorcycle mode")
            return true
        }
        return selectedProfile.vehicleType == Profile.VehicleType.MOTORCYCLE
    }
    
    private fun createTrackCard(
        sessionIdFull: String,
        trackAggregateStats: TrackOutingsRepository.TrackAggregateStats?,
        trackNameOverride: String? = null,
        isInProgress: Boolean = false
    ) {
        if (trackCardExists(sessionIdFull)) return

        val inflater = LayoutInflater.from(requireContext())
        val trackCard = inflater.inflate(R.layout.session_card_template, llSessionsContainer, false)
        trackCard.tag = sessionIdFull

        val trackId = TrackOutingsRepository.extractTrackIdFromSessionId(requireContext(), sessionIdFull)
        val trackName = trackNameOverride ?: getTrackName(trackId)
        val tvTrackName = trackCard.findViewById<TextView>(R.id.tvTrackName)
        tvTrackName.text = trackName

        val tvTrackDetails = trackCard.findViewById<TextView>(R.id.tvTrackDetails)
        tvTrackDetails.text = if (isInProgress) {
            getString(R.string.track_pista_in_progress)
        } else {
            resources.getQuantityString(
                R.plurals.track_sessions_meta,
                trackAggregateStats?.totalSessions ?: 0,
                trackAggregateStats?.totalSessions ?: 0
            )
        }

        val tvTrackAllTimeBestValue = trackCard.findViewById<TextView>(R.id.tvTrackAllTimeBestValue)
        val tvTrackPbBadge = trackCard.findViewById<TextView>(R.id.tvTrackPbBadge)
        val tvTrackSummarySessionsValue = trackCard.findViewById<TextView>(R.id.tvTrackSummarySessionsValue)
        val tvTrackSummaryLapsValue = trackCard.findViewById<TextView>(R.id.tvTrackSummaryLapsValue)
        val tvTrackSummaryTimeValue = trackCard.findViewById<TextView>(R.id.tvTrackSummaryTimeValue)

        tvTrackAllTimeBestValue.text = trackAggregateStats?.bestLapMs?.let {
            TrackOutingsRepository.formatTimeMs(it)
        } ?: LapTimeFormatter.PLACEHOLDER
        tvTrackPbBadge.text = getString(R.string.drag_run_indicator_pb)
        tvTrackPbBadge.visibility = if (trackAggregateStats?.bestLapMs != null) View.VISIBLE else View.GONE
        tvTrackSummarySessionsValue.text = (trackAggregateStats?.totalSessions ?: 0).toString()
        tvTrackSummaryLapsValue.text = (trackAggregateStats?.totalLaps ?: 0).toString()
        tvTrackSummaryTimeValue.text = TrackOutingsRepository.formatSummaryDuration(
            requireContext(),
            trackAggregateStats?.totalDurationMs ?: 0L
        )

        val btnResume = trackCard.findViewById<MaterialButton>(R.id.btnResume)
        btnResume.visibility = View.VISIBLE
        btnResume.setOnClickListener {
            resumeSpecificSession(sessionIdFull, trackName)
        }

        val btnDeleteSession = trackCard.findViewById<ImageButton>(R.id.btnDeleteSession)
        btnDeleteSession.visibility = View.GONE

        val headerLayout = trackCard.findViewById<LinearLayout>(R.id.headerLayout)
        val contentLayout = trackCard.findViewById<LinearLayout>(R.id.contentLayout)
        val arrow = trackCard.findViewById<TextView>(R.id.arrow)
        contentLayout.visibility = View.GONE
        arrow.visibility = View.GONE

        val openTrackSessions = View.OnClickListener {
            openTrackSessions(sessionIdFull, trackName)
        }
        headerLayout.setOnClickListener(openTrackSessions)
        trackCard.setOnClickListener(openTrackSessions)

        llSessionsContainer.addView(trackCard)
        showNoSessionsMessage()
    }

    private fun trackCardExists(sessionIdFull: String): Boolean {
        for (i in 0 until llSessionsContainer.childCount) {
            if (llSessionsContainer.getChildAt(i).tag == sessionIdFull) {
                return true
            }
        }
        return false
    }

    private fun openTrackSessions(sessionIdFull: String, trackName: String) {
        startActivity(Intent(requireContext(), TrackSessionsActivity::class.java).apply {
            putExtra(TrackSessionsActivity.EXTRA_SESSION_ID_FULL, sessionIdFull)
            putExtra(TrackSessionsActivity.EXTRA_TRACK_NAME, trackName)
        })
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

        val humidityText = currentHumidity?.let { "$it%" }
            ?: getString(R.string.drag_weather_humidity_placeholder)

        val windText = currentWindKph?.let {
            UnitsManager.formatStoredSpeed(it.toString(), requireContext())
        } ?: getString(R.string.drag_weather_wind_placeholder)

        val (weatherIconRes, weatherTintColor) = TrackOutingsRepository.resolveWeatherIconStyle(
            context,
            currentWeatherIcon,
            currentHumidity
        )
        
        tvTemperature.text = tempText
        tvAltitude.text = altText
        tvWeatherHumidity.text = humidityText
        tvWeatherWind.text = windText
        ivWeatherCondition.setImageResource(weatherIconRes)
        ivWeatherCondition.imageTintList = android.content.res.ColorStateList.valueOf(weatherTintColor)
        
        if (currentTemperature != null || currentAltitude != null) {
            llEnvironment.visibility = LinearLayout.VISIBLE
        }
    }

    private fun isWeatherCacheStale(): Boolean {
        val context = context ?: return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val cachedTime = prefs.getLong("cached_weather_time", 0L)
        if (cachedTime == 0L) return true
        val now = System.currentTimeMillis()
        return now - cachedTime > WEATHER_REFRESH_INTERVAL_MS
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
                    currentHumidity = weather.current.humidity
                    currentWindKph = weather.current.wind_kph.toFloat()
                    currentWeatherIcon = WeatherIconMapper.getWeatherApiIcon(
                        weather.current.condition.code,
                        weather.current.cloud,
                        weather.current.is_day == 1
                    )
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
        val cachedHumidity = prefs.getInt("cached_humidity", -1)
        val cachedWindKph = prefs.getFloat("cached_wind_kph", Float.NaN)
        val cachedWeatherIcon = prefs.getInt("cached_weather_icon", -1)
        val cachedLat = prefs.getFloat("cached_location_lat", Float.NaN)
        val cachedLon = prefs.getFloat("cached_location_lon", Float.NaN)
        
        if (!cachedTemp.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentTemperature = cachedTemp
        }
        
        if (!cachedAlt.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentAltitude = cachedAlt
        }

        if (cachedHumidity >= 0 && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentHumidity = cachedHumidity
        }

        if (!cachedWindKph.isNaN() && !cachedLat.isNaN() && !cachedLon.isNaN()) {
            currentWindKph = cachedWindKph
        }

        if (cachedWeatherIcon != -1) {
            currentWeatherIcon = cachedWeatherIcon
        }
    }
    
    private fun cacheWeatherData(location: Location) {
        val context = context ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        
        currentTemperature?.let { editor.putFloat("cached_temperature", it) }
        currentAltitude?.let { editor.putFloat("cached_altitude", it) }
        currentHumidity?.let { editor.putInt("cached_humidity", it) }
        currentWindKph?.let { editor.putFloat("cached_wind_kph", it) }
        editor.putInt("cached_weather_icon", currentWeatherIcon)
        editor.putLong("cached_weather_time", System.currentTimeMillis())
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

        if (isWeatherCacheStale()) {
            return true
        }
        
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
                val imagePath = activeProfile.imagePath.orEmpty()
                val imageFile = java.io.File(requireContext().getExternalFilesDir(null), imagePath)
                if (imageFile.exists()) {
                    val expectedProfileId = activeProfile.id
                    val expectedImagePath = imagePath
                    viewLifecycleOwner.lifecycleScope.launch {
                        val bitmap = withContext(Dispatchers.IO) {
                            android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                        }
                        if (!isAdded || view == null) return@launch

                        val selectedProfileId = ProfileStorage.getSelectedProfileId(requireContext())
                        val selectedProfile = ProfileStorage.loadProfiles(requireContext())
                            .find { it.id == selectedProfileId }
                        if (selectedProfile?.id != expectedProfileId || selectedProfile.imagePath != expectedImagePath) {
                            return@launch
                        }

                        if (bitmap != null) {
                            ivHeaderProfileImage.setImageBitmap(bitmap)
                            ivHeaderProfileImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            ivHeaderProfileImage.setPadding(0, 0, 0, 0)
                        } else {
                            showDefaultIcon(activeProfile.vehicleType)
                        }
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
