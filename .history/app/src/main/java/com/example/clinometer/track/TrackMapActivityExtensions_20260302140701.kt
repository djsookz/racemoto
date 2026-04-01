package com.example.clinometer.track

import android.content.Intent
import android.location.Location
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.clinometer.GeoPoint
import com.example.clinometer.LapData
import com.example.clinometer.Profile
import com.example.clinometer.R
import com.example.clinometer.Race
import com.example.clinometer.RoutePoint
import com.example.clinometer.TrackLapCompareSelectionActivity
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.main.map.MapActivity
import com.google.gson.Gson
import kotlin.math.abs

internal fun MapActivity.applyTrackLapLayoutAdjustments(isTrackContext: Boolean) {
    if (!isTrackContext) return

    findViewById<View?>(R.id.tvStatisticsHeader)?.visibility = View.GONE
    findViewById<View?>(R.id.gridStatistics)?.visibility = View.GONE
    findViewById<View?>(R.id.cardStatistics)?.visibility = View.GONE

    val targetHeightDp = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
        300
    } else {
        340
    }
    val targetHeightPx = (targetHeightDp * resources.displayMetrics.density).toInt()
    chart.layoutParams = chart.layoutParams.apply {
        height = targetHeightPx
    }
    chart.requestLayout()
}

internal fun MapActivity.setupTrackLapNavigation(
    isTrackContext: Boolean,
    trackContextTrackId: String,
    trackContextTrackName: String,
    trackContextIsMotorcycle: Boolean,
    trackContextSessionId: String
) {
    val btnPreviousLap = findViewById<View?>(R.id.btnPreviousLap)
    val btnNextLap = findViewById<View?>(R.id.btnNextLap)
    val btnCompareLap = findViewById<View?>(R.id.btnCompareLap)
    val tvSessionTitle = findViewById<TextView>(R.id.tvSessionTitle)

    if (btnPreviousLap == null || btnNextLap == null) return

    val isLapNavigationContext = isTrackContext &&
        currentTrackLapNumber > 0 &&
        currentTrackOutingNumber > 0 &&
        trackContextSessionId.isNotEmpty()

    if (!isLapNavigationContext) {
        btnPreviousLap.visibility = View.GONE
        btnNextLap.visibility = View.GONE
        btnCompareLap?.visibility = View.GONE
        applySessionTitleMarginsForLapMode(tvSessionTitle, isLapMode = false)
        return
    }

    currentTrackTotalLaps = resolveTrackLapCount(trackContextSessionId, currentTrackOutingNumber)
    if (currentTrackTotalLaps <= 0) {
        btnPreviousLap.visibility = View.GONE
        btnNextLap.visibility = View.GONE
        btnCompareLap?.visibility = View.GONE
        applySessionTitleMarginsForLapMode(tvSessionTitle, isLapMode = false)
        return
    }

    currentTrackLapNumber = currentTrackLapNumber.coerceIn(1, currentTrackTotalLaps)
    tvSessionTitle.text = if (isPointToPointLapContext) {
        "Run #$currentTrackLapNumber"
    } else {
        "Lap #$currentTrackLapNumber"
    }

    btnPreviousLap.visibility = View.VISIBLE
    btnNextLap.visibility = View.VISIBLE
    btnCompareLap?.visibility = View.VISIBLE
    applySessionTitleMarginsForLapMode(tvSessionTitle, isLapMode = true)

    updateLapNavigationButtons(btnPreviousLap, btnNextLap)

    btnPreviousLap.setOnClickListener {
        val targetLap = currentTrackLapNumber - 1
        if (targetLap >= 1) {
            navigateToTrackLap(
                targetLap = targetLap,
                trackContextTrackId = trackContextTrackId,
                trackContextTrackName = trackContextTrackName,
                trackContextIsMotorcycle = trackContextIsMotorcycle,
                trackContextSessionId = trackContextSessionId
            )
        }
    }

    btnNextLap.setOnClickListener {
        val targetLap = currentTrackLapNumber + 1
        if (targetLap <= currentTrackTotalLaps) {
            navigateToTrackLap(
                targetLap = targetLap,
                trackContextTrackId = trackContextTrackId,
                trackContextTrackName = trackContextTrackName,
                trackContextIsMotorcycle = trackContextIsMotorcycle,
                trackContextSessionId = trackContextSessionId
            )
        }
    }

    btnCompareLap?.setOnClickListener {
        showTrackLapComparePicker(
            trackContextSessionId = trackContextSessionId,
            trackContextTrackId = trackContextTrackId,
            trackContextTrackName = trackContextTrackName,
            trackContextIsMotorcycle = trackContextIsMotorcycle
        )
    }
}

private fun MapActivity.showTrackLapComparePicker(
    trackContextSessionId: String,
    trackContextTrackId: String,
    trackContextTrackName: String,
    trackContextIsMotorcycle: Boolean
) {
    startActivity(Intent(this, TrackLapCompareSelectionActivity::class.java).apply {
        putExtra("current_session_id", trackContextSessionId)
        putExtra("current_outing_number", currentTrackOutingNumber)
        putExtra("current_lap_number", currentTrackLapNumber)
        putExtra("track_id", trackContextTrackId)
        putExtra("track_name", trackContextTrackName)
        putExtra("is_motorcycle", trackContextIsMotorcycle)
        putExtra("origin_race_id", raceId)
        putExtra("origin_is_point_to_point", isPointToPointLapContext)
    })
}

private fun MapActivity.parseLapTime(lapTimeText: String): Long {
    val parts = lapTimeText.split(":", ".")
    if (parts.size != 3) return Long.MAX_VALUE

    val minutes = parts[0].toLongOrNull() ?: return Long.MAX_VALUE
    val seconds = parts[1].toLongOrNull() ?: return Long.MAX_VALUE
    val millis = parts[2].toLongOrNull() ?: return Long.MAX_VALUE

    return minutes * 60_000L + seconds * 1_000L + millis
}

private fun MapActivity.updateLapNavigationButtons(previousButton: View, nextButton: View) {
    val isFirstLap = currentTrackLapNumber <= 1
    val isLastLap = currentTrackLapNumber >= currentTrackTotalLaps

    previousButton.isEnabled = !isFirstLap
    previousButton.alpha = if (isFirstLap) 0.3f else 1.0f

    nextButton.isEnabled = !isLastLap
    nextButton.alpha = if (isLastLap) 0.3f else 1.0f
}

private fun MapActivity.applySessionTitleMarginsForLapMode(titleView: TextView, isLapMode: Boolean) {
    val params = titleView.layoutParams
    val density = resources.displayMetrics.density
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

private fun MapActivity.resolveTrackLapCount(trackContextSessionId: String, outingNumber: Int): Int {
    val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
    return sharedPrefs.getInt("${trackContextSessionId}_outing_${outingNumber}_lap_data_count", 0)
}

private fun MapActivity.navigateToTrackLap(
    targetLap: Int,
    trackContextTrackId: String,
    trackContextTrackName: String,
    trackContextIsMotorcycle: Boolean,
    trackContextSessionId: String
) {
    val sharedPrefs = getSharedPreferences("track_outings", MODE_PRIVATE)
    val lapData = loadLapDataForMap(sharedPrefs, trackContextSessionId, currentTrackOutingNumber, targetLap)
    if (lapData == null || lapData.routePoints.isEmpty()) return

    val normalizedPoints = normalizeRoutePointsForMap(lapData.routePoints)
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

    val title = if (isPointToPointLapContext) {
        "Run #$targetLap"
    } else {
        "Lap #$targetLap"
    }

    val raceForMap = Race(
        id = -((System.currentTimeMillis() % 1_000_000_000L) + targetLap),
        profileId = race?.profileId ?: ProfileStorage.getSelectedProfileId(this),
        routePoints = normalizedPoints,
        timestamp = normalizedPoints.firstOrNull()?.absoluteTime ?: System.currentTimeMillis(),
        duration = durationMs,
        absoluteTimestamp = normalizedPoints.firstOrNull()?.absoluteTime ?: System.currentTimeMillis(),
        maxLeftAngle = maxLeftAngle,
        maxRightAngle = maxRightAngle,
        maxSpeed = maxSpeed,
        name = title,
        trackName = if (trackContextTrackName.isNotEmpty()) trackContextTrackName else race?.trackName,
        distance = distanceKm
    )

    val intent = Intent(this, MapActivity::class.java).apply {
        putExtra(MapActivity.EXTRA_INLINE_RACE, raceForMap)
        putParcelableArrayListExtra(MapActivity.EXTRA_INLINE_ROUTE_POINTS, ArrayList(normalizedPoints))
        putExtra(MapActivity.EXTRA_RETURN_TO_PREVIOUS, true)
        putExtra(TrackMapExtras.EXTRA_TRACK_CONTEXT, true)
        putExtra(TrackMapExtras.EXTRA_TRACK_ID, trackContextTrackId)
        putExtra(TrackMapExtras.EXTRA_TRACK_NAME, trackContextTrackName)
        putExtra(TrackMapExtras.EXTRA_TRACK_IS_MOTORCYCLE, trackContextIsMotorcycle)
        putExtra(TrackMapExtras.EXTRA_TRACK_SESSION_ID, trackContextSessionId)
        putExtra(TrackMapExtras.EXTRA_TRACK_LAP_NUMBER, targetLap)
        putExtra(TrackMapExtras.EXTRA_TRACK_OUTING_NUMBER, currentTrackOutingNumber)
        putExtra(TrackMapExtras.EXTRA_TRACK_IS_POINT_TO_POINT, isPointToPointLapContext)
    }

    startActivity(intent)
    overridePendingTransition(0, 0)
    finish()
}

private fun MapActivity.loadLapDataForMap(
    sharedPrefs: android.content.SharedPreferences,
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
