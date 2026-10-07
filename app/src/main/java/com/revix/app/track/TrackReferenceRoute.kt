package com.revix.app.track

import android.location.Location
import com.revix.app.GeoPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Track centerline / reference path with arc-length coordinates for distance-based lap compare.
 */
class TrackReferenceRoute private constructor(
    val points: List<GeoPoint>,
    private val cumulativeMeters: FloatArray,
    val totalLengthMeters: Float,
    val isCircuit: Boolean,
    private val startFinishOffsetMeters: Float
) {
    fun distanceFromStartFinish(latitude: Double, longitude: Double): Float {
        val raw = projectOntoRoute(latitude, longitude)
        return normalizeFromStartFinish(raw - startFinishOffsetMeters)
    }

    /**
     * Continuity-aware SF distance for lap profiles.
     * Keeps successive GPS samples on the same along-track progression so nearest-segment
     * projection cannot jump to a geometrically closer but wrong part of the circuit.
     * Returned values are unwrapped (may exceed [0, totalLength) on a forward lap).
     */
    fun distanceFromStartFinishContinuing(
        latitude: Double,
        longitude: Double,
        previousFromStartFinish: Float?,
        maxForwardMeters: Float,
        maxBackwardMeters: Float = DEFAULT_MAX_BACKWARD_METERS
    ): Float {
        if (previousFromStartFinish == null || points.size < 2) {
            return distanceFromStartFinish(latitude, longitude)
        }

        val forward = maxForwardMeters.coerceAtLeast(1f)
        val backward = maxBackwardMeters.coerceAtLeast(0f)

        var bestWindowDistSq = Double.POSITIVE_INFINITY
        var bestWindowSf = previousFromStartFinish
        var foundWindow = false

        var bestFallbackAlongDelta = Float.POSITIVE_INFINITY
        var bestFallbackDistSq = Double.POSITIVE_INFINITY
        var bestFallbackSf = previousFromStartFinish

        forEachProjection(latitude, longitude) { alongRaw, distSq ->
            val sfWrapped = normalizeFromStartFinish(alongRaw - startFinishOffsetMeters)
            val sfUnwrapped = unwrapToNear(sfWrapped, previousFromStartFinish)
            val delta = sfUnwrapped - previousFromStartFinish
            val alongDelta = abs(delta)

            if (alongDelta < bestFallbackAlongDelta - 1e-3f ||
                (abs(alongDelta - bestFallbackAlongDelta) <= 1e-3f && distSq < bestFallbackDistSq)
            ) {
                bestFallbackAlongDelta = alongDelta
                bestFallbackDistSq = distSq
                bestFallbackSf = sfUnwrapped
            }

            if (delta in -backward..forward && distSq < bestWindowDistSq) {
                bestWindowDistSq = distSq
                bestWindowSf = sfUnwrapped
                foundWindow = true
            }
        }

        val chosen = if (foundWindow) bestWindowSf else bestFallbackSf
        val minAllowed = previousFromStartFinish - backward
        val maxAllowed = previousFromStartFinish + forward
        return chosen.coerceIn(minAllowed, maxAllowed)
    }

    fun pointAtDistanceFromStartFinish(distanceMeters: Float): GeoPoint {
        val target = normalizeFromStartFinish(startFinishOffsetMeters + distanceMeters)
        return pointAtRawDistance(target)
    }

    private fun normalizeFromStartFinish(distanceMeters: Float): Float {
        if (!isCircuit || totalLengthMeters <= 1f) {
            return distanceMeters.coerceAtLeast(0f)
        }
        var normalized = distanceMeters % totalLengthMeters
        if (normalized < 0f) normalized += totalLengthMeters
        return normalized
    }

    private fun unwrapToNear(wrappedFromStartFinish: Float, near: Float): Float {
        if (!isCircuit || totalLengthMeters <= 1f) return wrappedFromStartFinish
        val length = totalLengthMeters
        val baseK = floor(((near - wrappedFromStartFinish) / length).toDouble()).toInt()
        var best = wrappedFromStartFinish
        var bestDelta = abs(wrappedFromStartFinish - near)
        for (k in (baseK - 1)..(baseK + 1)) {
            val candidate = wrappedFromStartFinish + k * length
            val delta = abs(candidate - near)
            if (delta < bestDelta) {
                bestDelta = delta
                best = candidate
            }
        }
        return best
    }

    private fun pointAtRawDistance(distanceMeters: Float): GeoPoint {
        if (points.isEmpty()) return GeoPoint(0.0, 0.0)
        if (points.size == 1) return points.first()

        val clamped = if (isCircuit && totalLengthMeters > 1f) {
            var value = distanceMeters % totalLengthMeters
            if (value < 0f) value += totalLengthMeters
            value
        } else {
            distanceMeters.coerceIn(0f, totalLengthMeters)
        }

        var segmentIndex = 0
        while (segmentIndex < cumulativeMeters.lastIndex &&
            cumulativeMeters[segmentIndex + 1] < clamped
        ) {
            segmentIndex++
        }

        val segmentStart = cumulativeMeters[segmentIndex]
        val segmentEnd = cumulativeMeters.getOrElse(segmentIndex + 1) { totalLengthMeters }
        val segmentLength = (segmentEnd - segmentStart).coerceAtLeast(1e-3f)
        val factor = ((clamped - segmentStart) / segmentLength).coerceIn(0f, 1f)

        val start = points[segmentIndex]
        val end = points.getOrElse(segmentIndex + 1) { points.first() }
        return GeoPoint(
            latitude = start.latitude + (end.latitude - start.latitude) * factor,
            longitude = start.longitude + (end.longitude - start.longitude) * factor
        )
    }

    private fun projectOntoRoute(latitude: Double, longitude: Double): Float {
        var bestAlong = 0f
        var bestDistanceSq = Double.POSITIVE_INFINITY
        forEachProjection(latitude, longitude) { alongRaw, distSq ->
            if (distSq < bestDistanceSq) {
                bestDistanceSq = distSq
                bestAlong = alongRaw
            }
        }
        return bestAlong
    }

    private fun forEachProjection(
        latitude: Double,
        longitude: Double,
        onProjection: (alongRaw: Float, distanceSq: Double) -> Unit
    ) {
        if (points.size < 2) return

        val origin = points.first()
        val refLatRad = Math.toRadians(origin.latitude)
        val metersPerDegLat = 111_132.0
        val metersPerDegLon = 111_320.0 * cos(refLatRad)
        val originLon = origin.longitude
        val originLat = origin.latitude

        val px = (longitude - originLon) * metersPerDegLon
        val py = (latitude - originLat) * metersPerDegLat
        val segmentCount = points.lastIndex

        for (index in 0 until segmentCount) {
            val a = points[index]
            val b = points[index + 1]
            val ax = (a.longitude - originLon) * metersPerDegLon
            val ay = (a.latitude - originLat) * metersPerDegLat
            val bx = (b.longitude - originLon) * metersPerDegLon
            val by = (b.latitude - originLat) * metersPerDegLat

            val dx = bx - ax
            val dy = by - ay
            val segLenSq = dx * dx + dy * dy
            if (segLenSq <= 1e-6) continue

            val t = (((px - ax) * dx + (py - ay) * dy) / segLenSq).coerceIn(0.0, 1.0)
            val projX = ax + t * dx
            val projY = ay + t * dy
            val distSq = (px - projX) * (px - projX) + (py - projY) * (py - projY)
            val segLen = sqrt(segLenSq).toFloat()
            val alongRaw = cumulativeMeters[index] + (t.toFloat() * segLen)
            onProjection(alongRaw, distSq)
        }
    }

    companion object {
        private const val DEFAULT_MAX_BACKWARD_METERS = 40f

        fun fromPoints(
            points: List<GeoPoint>,
            closeLoop: Boolean,
            startFinishAnchor: GeoPoint? = null
        ): TrackReferenceRoute? {
            if (points.size < 2) return null

            val routePoints = points.toMutableList()
            if (closeLoop) {
                val first = routePoints.first()
                val last = routePoints.last()
                if (first.distanceToAsDouble(last) > 2.0) {
                    routePoints.add(first)
                }
            }

            if (routePoints.size < 2) return null

            val cumulative = FloatArray(routePoints.size)
            var total = 0f
            cumulative[0] = 0f
            for (index in 0 until routePoints.lastIndex) {
                total += routePoints[index].distanceToAsDouble(routePoints[index + 1]).toFloat()
                cumulative[index + 1] = total
            }

            val anchor = startFinishAnchor ?: routePoints.first()
            val tempRoute = TrackReferenceRoute(
                points = routePoints,
                cumulativeMeters = cumulative,
                totalLengthMeters = total.coerceAtLeast(1f),
                isCircuit = closeLoop,
                startFinishOffsetMeters = 0f
            )
            val startFinishOffset = tempRoute.projectOntoRoute(anchor.latitude, anchor.longitude)

            return TrackReferenceRoute(
                points = routePoints,
                cumulativeMeters = cumulative,
                totalLengthMeters = total.coerceAtLeast(1f),
                isCircuit = closeLoop,
                startFinishOffsetMeters = startFinishOffset
            )
        }
    }
}

internal data class GateCrossingTiming(
    val location: Location,
    /** Wall-clock ms of the interpolated gate crossing (for lap start/end). */
    val crossingWallTimeMs: Long,
    val ratio: Float
)

/**
 * Linear interpolation of gate-crossing wall time between two GPS fixes.
 * Prefers [Location.getElapsedRealtimeNanos] (monotonic); falls back to [Location.getTime].
 */
internal fun interpolateGateCrossingWallTimeMs(
    previous: Location,
    current: Location,
    ratio: Float,
    nowWallMs: Long = System.currentTimeMillis(),
    nowElapsedRealtimeNs: Long = android.os.SystemClock.elapsedRealtimeNanos()
): Long {
    val t = ratio.coerceIn(0f, 1f)
    val prevNs = previous.elapsedRealtimeNanos
    val currNs = current.elapsedRealtimeNanos
    if (prevNs > 0L && currNs > prevNs) {
        val crossingNs = prevNs + ((currNs - prevNs).toDouble() * t).toLong()
        val ageMs = ((nowElapsedRealtimeNs - crossingNs).coerceAtLeast(0L)) / 1_000_000L
        return nowWallMs - ageMs
    }

    val prevTime = previous.time
    val currTime = current.time
    if (prevTime > 0L && currTime > prevTime) {
        return prevTime + ((currTime - prevTime).toDouble() * t).toLong()
    }

    return nowWallMs
}

internal fun resolveGateCrossingTiming(
    previous: Location,
    current: Location,
    lineStartLat: Double,
    lineStartLon: Double,
    lineEndLat: Double,
    lineEndLon: Double,
    nowWallMs: Long = System.currentTimeMillis(),
    nowElapsedRealtimeNs: Long = android.os.SystemClock.elapsedRealtimeNanos()
): GateCrossingTiming? {
    val ratio = TrackGateLineCrossing.interpolateSegmentRatio(
        previousLat = previous.latitude,
        previousLon = previous.longitude,
        currentLat = current.latitude,
        currentLon = current.longitude,
        lineStartLat = lineStartLat,
        lineStartLon = lineStartLon,
        lineEndLat = lineEndLat,
        lineEndLon = lineEndLon
    ) ?: return null

    val location = Location(current).apply {
        latitude = previous.latitude + (current.latitude - previous.latitude) * ratio
        longitude = previous.longitude + (current.longitude - previous.longitude) * ratio
        time = interpolateGateCrossingWallTimeMs(
            previous = previous,
            current = current,
            ratio = ratio,
            nowWallMs = nowWallMs,
            nowElapsedRealtimeNs = nowElapsedRealtimeNs
        )
        val prevNs = previous.elapsedRealtimeNanos
        val currNs = current.elapsedRealtimeNanos
        if (prevNs > 0L && currNs >= prevNs) {
            elapsedRealtimeNanos = prevNs + ((currNs - prevNs).toDouble() * ratio).toLong()
        }
        if (previous.hasSpeed() && current.hasSpeed()) {
            speed = previous.speed + (current.speed - previous.speed) * ratio
        }
        if (previous.hasBearing() && current.hasBearing()) {
            val delta = shortestBearingDelta(previous.bearing, current.bearing)
            bearing = normalizeBearing(previous.bearing + delta * ratio)
        }
    }

    return GateCrossingTiming(
        location = location,
        crossingWallTimeMs = location.time,
        ratio = ratio
    )
}

internal fun interpolateLocationAtGateCrossing(
    previous: Location,
    current: Location,
    lineStartLat: Double,
    lineStartLon: Double,
    lineEndLat: Double,
    lineEndLon: Double
): Location? {
    return resolveGateCrossingTiming(
        previous = previous,
        current = current,
        lineStartLat = lineStartLat,
        lineStartLon = lineStartLon,
        lineEndLat = lineEndLat,
        lineEndLon = lineEndLon
    )?.location
}

private fun shortestBearingDelta(fromDeg: Float, toDeg: Float): Float {
    var delta = (toDeg - fromDeg) % 360f
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return delta
}

private fun normalizeBearing(bearingDeg: Float): Float {
    var value = bearingDeg % 360f
    if (value < 0f) value += 360f
    return value
}
