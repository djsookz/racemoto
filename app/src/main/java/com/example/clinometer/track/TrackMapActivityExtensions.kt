package com.example.clinometer.track

import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.clinometer.LapData
import com.example.clinometer.R
import com.example.clinometer.Race
import com.example.clinometer.RoutePoint
import com.example.clinometer.TrackLapCompareSelectionActivity
import com.example.clinometer.TrackSessionActivity
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.main.map.MapActivity
import com.github.mikephil.charting.charts.LineChart
import com.google.gson.Gson
import kotlin.math.abs

data class TrackMapIntentContext(
    val isTrackContext: Boolean,
    val trackId: String,
    val trackName: String,
    val isMotorcycle: Boolean,
    val sessionId: String,
    val lapNumber: Int,
    val outingNumber: Int,
    val isPointToPoint: Boolean
)

data class TrackLapNavState(
    var lapNumber: Int,
    var outingNumber: Int,
    var totalLaps: Int,
    var isPointToPoint: Boolean
)

object TrackMapNavigator {
    fun parseIntent(intent: Intent, defaultIsMotorcycle: Boolean): TrackMapIntentContext {
        return TrackMapIntentContext(
            isTrackContext = intent.getBooleanExtra(TrackMapExtras.EXTRA_TRACK_CONTEXT, false),
            trackId = intent.getStringExtra(TrackMapExtras.EXTRA_TRACK_ID).orEmpty(),
            trackName = intent.getStringExtra(TrackMapExtras.EXTRA_TRACK_NAME).orEmpty(),
            isMotorcycle = intent.getBooleanExtra(TrackMapExtras.EXTRA_TRACK_IS_MOTORCYCLE, defaultIsMotorcycle),
            sessionId = intent.getStringExtra(TrackMapExtras.EXTRA_TRACK_SESSION_ID).orEmpty(),
            lapNumber = intent.getIntExtra(TrackMapExtras.EXTRA_TRACK_LAP_NUMBER, -1),
            outingNumber = intent.getIntExtra(TrackMapExtras.EXTRA_TRACK_OUTING_NUMBER, -1),
            isPointToPoint = intent.getBooleanExtra(TrackMapExtras.EXTRA_TRACK_IS_POINT_TO_POINT, false)
        )
    }

    fun openSessionOrNewRoute(
        activity: AppCompatActivity,
        context: TrackMapIntentContext,
        onFallback: () -> Unit
    ) {
        if (context.isTrackContext && context.trackId.isNotEmpty() && context.sessionId.isNotEmpty()) {
            activity.startActivity(Intent(activity, TrackSessionActivity::class.java).apply {
                putExtra("track_id", context.trackId)
                putExtra("track_name", context.trackName)
                putExtra("is_motorcycle", context.isMotorcycle)
                putExtra("resume_session", true)
                putExtra("session_id", context.sessionId)
                putExtra("is_official", !context.trackId.startsWith("custom_"))
            })
            activity.finish()
            return
        }

        onFallback()
    }

    fun applyTrackLapLayoutAdjustments(activity: AppCompatActivity, chart: LineChart, isTrackContext: Boolean) {
        if (!isTrackContext) return

        activity.findViewById<View?>(R.id.tvStatisticsHeader)?.visibility = View.GONE
        activity.findViewById<View?>(R.id.gridStatistics)?.visibility = View.GONE
        activity.findViewById<View?>(R.id.cardStatistics)?.visibility = View.GONE

        val targetHeightDp = if (activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            220
        } else {
            250
        }
        val targetHeightPx = (targetHeightDp * activity.resources.displayMetrics.density).toInt()
        chart.layoutParams = chart.layoutParams.apply {
            height = targetHeightPx
        }
        chart.requestLayout()
    }

    fun setupTrackLapNavigation(
        activity: MapActivity,
        state: TrackLapNavState,
        context: TrackMapIntentContext,
        raceId: Long,
        race: Race?
    ): TrackLapNavState {
        val btnPreviousLap = activity.findViewById<View?>(R.id.btnPreviousLap)
        val btnNextLap = activity.findViewById<View?>(R.id.btnNextLap)
        val btnCompareLap = activity.findViewById<View?>(R.id.btnCompareLap)
        val tvSessionTitle = activity.findViewById<TextView>(R.id.tvSessionTitle)

        if (btnPreviousLap == null || btnNextLap == null) return state

        val isLapNavigationContext = context.isTrackContext &&
            state.lapNumber > 0 &&
            state.outingNumber > 0 &&
            context.sessionId.isNotEmpty()

        if (!isLapNavigationContext) {
            btnPreviousLap.visibility = View.GONE
            btnNextLap.visibility = View.GONE
            btnCompareLap?.visibility = View.GONE
            applySessionTitleMarginsForLapMode(activity, tvSessionTitle, isLapMode = false)
            return state
        }

        state.totalLaps = resolveTrackLapCount(activity, context.sessionId, state.outingNumber)
        if (state.totalLaps <= 0) {
            btnPreviousLap.visibility = View.GONE
            btnNextLap.visibility = View.GONE
            btnCompareLap?.visibility = View.GONE
            applySessionTitleMarginsForLapMode(activity, tvSessionTitle, isLapMode = false)
            return state
        }

        state.lapNumber = state.lapNumber.coerceIn(1, state.totalLaps)
        tvSessionTitle.text = if (state.isPointToPoint) {
            "Run #${state.lapNumber}"
        } else {
            "Lap #${state.lapNumber}"
        }

        btnPreviousLap.visibility = View.VISIBLE
        btnNextLap.visibility = View.VISIBLE
        btnCompareLap?.visibility = View.VISIBLE
        applySessionTitleMarginsForLapMode(activity, tvSessionTitle, isLapMode = true)

        updateLapNavigationButtons(btnPreviousLap, btnNextLap, state)

        btnPreviousLap.setOnClickListener {
            val targetLap = state.lapNumber - 1
            if (targetLap >= 1) {
                navigateToTrackLap(activity, state, context, targetLap, race)
            }
        }

        btnNextLap.setOnClickListener {
            val targetLap = state.lapNumber + 1
            if (targetLap <= state.totalLaps) {
                navigateToTrackLap(activity, state, context, targetLap, race)
            }
        }

        btnCompareLap?.setOnClickListener {
            activity.startActivity(Intent(activity, TrackLapCompareSelectionActivity::class.java).apply {
                putExtra("current_session_id", context.sessionId)
                putExtra("current_outing_number", state.outingNumber)
                putExtra("current_lap_number", state.lapNumber)
                putExtra("track_id", context.trackId)
                putExtra("track_name", context.trackName)
                putExtra("is_motorcycle", context.isMotorcycle)
                putExtra("origin_race_id", raceId)
                putExtra("origin_is_point_to_point", state.isPointToPoint)
            })
        }

        return state
    }

    private fun updateLapNavigationButtons(previousButton: View, nextButton: View, state: TrackLapNavState) {
        val isFirstLap = state.lapNumber <= 1
        val isLastLap = state.lapNumber >= state.totalLaps

        previousButton.isEnabled = !isFirstLap
        previousButton.alpha = if (isFirstLap) 0.3f else 1.0f

        nextButton.isEnabled = !isLastLap
        nextButton.alpha = if (isLastLap) 0.3f else 1.0f
    }

    private fun applySessionTitleMarginsForLapMode(activity: AppCompatActivity, titleView: TextView, isLapMode: Boolean) {
        val params = titleView.layoutParams
        val density = activity.resources.displayMetrics.density
        val marginPx = if (isLapMode) (112 * density).toInt() else (56 * density).toInt()

        when (params) {
            is android.widget.RelativeLayout.LayoutParams -> {
                params.width = if (isLapMode) {
                    ViewGroup.LayoutParams.WRAP_CONTENT
                } else {
                    ViewGroup.LayoutParams.MATCH_PARENT
                }
                if (isLapMode) {
                    params.marginStart = 0
                    params.marginEnd = 0
                } else {
                    params.marginStart = marginPx
                    params.marginEnd = marginPx
                }
                titleView.layoutParams = params
            }

            is androidx.constraintlayout.widget.ConstraintLayout.LayoutParams -> {
                if (isLapMode) {
                    params.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    params.marginStart = 0
                    params.marginEnd = 0
                } else {
                    params.width = 0
                    val defaultMargin = (16 * density).toInt()
                    params.marginStart = defaultMargin
                    params.marginEnd = defaultMargin
                }
                titleView.layoutParams = params
            }

            is ViewGroup.MarginLayoutParams -> {
                params.marginStart = marginPx
                params.marginEnd = marginPx
                titleView.layoutParams = params
            }
        }
    }

    private fun resolveTrackLapCount(activity: AppCompatActivity, sessionId: String, outingNumber: Int): Int {
        val sharedPrefs = activity.getSharedPreferences("track_outings", AppCompatActivity.MODE_PRIVATE)
        return sharedPrefs.getInt("${sessionId}_outing_${outingNumber}_lap_data_count", 0)
    }

    private fun navigateToTrackLap(
        activity: MapActivity,
        state: TrackLapNavState,
        context: TrackMapIntentContext,
        targetLap: Int,
        race: Race?
    ) {
        val sharedPrefs = activity.getSharedPreferences("track_outings", AppCompatActivity.MODE_PRIVATE)
        val lapData = loadLapDataForMap(sharedPrefs, context.sessionId, state.outingNumber, targetLap)
        if (lapData == null || lapData.routePoints.isEmpty()) return

        val enrichedRoutePoints = enrichRoutePointsWithLeanPeaks(lapData)
        val normalizedPoints = normalizeRoutePointsForMap(enrichedRoutePoints)
        if (normalizedPoints.isEmpty()) return

        val durationMs = (lapData.endTime - lapData.startTime).takeIf { it > 0L }
            ?: (normalizedPoints.last().timestamp - normalizedPoints.first().timestamp).coerceAtLeast(0L)
        val distanceKm = calculateDistanceKm(normalizedPoints)
        val maxSpeed = normalizedPoints.maxOfOrNull { it.speed } ?: 0f
        val maxLeftAngle = normalizedPoints
            .filter { it.angle < 0f }
            .minByOrNull { it.angle }
            ?.angle
            ?.let { abs(it) }
            ?: 0f
        val maxRightAngle = normalizedPoints
            .filter { it.angle > 0f }
            .maxByOrNull { it.angle }
            ?.angle
            ?: 0f

        val title = if (state.isPointToPoint) {
            "Run #$targetLap"
        } else {
            "Lap #$targetLap"
        }

        val raceForMap = Race(
            id = -((System.currentTimeMillis() % 1_000_000_000L) + targetLap),
            profileId = race?.profileId ?: ProfileStorage.getSelectedProfileId(activity),
            routePoints = normalizedPoints,
            timestamp = normalizedPoints.firstOrNull()?.absoluteTime ?: System.currentTimeMillis(),
            duration = durationMs,
            absoluteTimestamp = normalizedPoints.firstOrNull()?.absoluteTime ?: System.currentTimeMillis(),
            maxLeftAngle = maxLeftAngle,
            maxRightAngle = maxRightAngle,
            maxSpeed = maxSpeed,
            name = title,
            trackName = if (context.trackName.isNotEmpty()) context.trackName else race?.trackName,
            distance = distanceKm
        )

        activity.startActivity(Intent(activity, MapActivity::class.java).apply {
            putExtra(MapActivity.EXTRA_INLINE_RACE, raceForMap)
            putParcelableArrayListExtra(MapActivity.EXTRA_INLINE_ROUTE_POINTS, ArrayList(normalizedPoints))
            putExtra(MapActivity.EXTRA_RETURN_TO_PREVIOUS, true)
            putExtra(TrackMapExtras.EXTRA_TRACK_CONTEXT, true)
            putExtra(TrackMapExtras.EXTRA_TRACK_ID, context.trackId)
            putExtra(TrackMapExtras.EXTRA_TRACK_NAME, context.trackName)
            putExtra(TrackMapExtras.EXTRA_TRACK_IS_MOTORCYCLE, context.isMotorcycle)
            putExtra(TrackMapExtras.EXTRA_TRACK_SESSION_ID, context.sessionId)
            putExtra(TrackMapExtras.EXTRA_TRACK_LAP_NUMBER, targetLap)
            putExtra(TrackMapExtras.EXTRA_TRACK_OUTING_NUMBER, state.outingNumber)
            putExtra(TrackMapExtras.EXTRA_TRACK_IS_POINT_TO_POINT, state.isPointToPoint)
        })

        activity.overridePendingTransition(0, 0)
        activity.finish()
    }

    private fun loadLapDataForMap(
        sharedPrefs: SharedPreferences,
        sessionId: String,
        outingNumber: Int,
        requestedLapNumber: Int
    ): LapData? {
        val lapDataCount = sharedPrefs.getInt("${sessionId}_outing_${outingNumber}_lap_data_count", 0)
        if (lapDataCount <= 0) return null

        val safeLapNumber = requestedLapNumber.coerceIn(1, lapDataCount)
        val lapJson = sharedPrefs.getString("${sessionId}_outing_${outingNumber}_lap_data_${safeLapNumber}", null) ?: return null

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
                point.copy(timestamp = index * 100L)
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

    private fun calculateDistanceKm(points: List<RoutePoint>): Double {
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
}
