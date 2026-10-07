package com.revix.app.utils

import android.location.Location
import com.revix.app.RoutePoint

/**
 * Phone GNSS Doppler often reports a few km/h while fully stopped.
 * Zero residual crawl at capture/playback without inventing motion between sparse fixes.
 */
object GnssSpeedSanitizer {
    /** Speeds below this are treated as stopped (typical phone residual). */
    const val STATIONARY_FLOOR_KMH = 3f

    /** When IMU says stationary, allow zeroing residual Doppler up to this. */
    const val STATIONARY_HINT_MAX_KMH = 10f

    /** If ground distance implies slower than this over a gap, ignore Doppler. */
    const val KINEMATIC_STOP_KMH = 3f

    const val KINEMATIC_MIN_GAP_SEC = 1.5f

    /** Only override Doppler with kinematics when reported speed is still "crawl-like". */
    const val KINEMATIC_DOPPLER_CAP_KMH = 12f

    fun sanitizeReportedKmh(
        speedKmh: Float,
        isStationaryHint: Boolean = false
    ): Float {
        val speed = speedKmh.coerceAtLeast(0f)
        if (speed < STATIONARY_FLOOR_KMH) return 0f
        if (isStationaryHint && speed < STATIONARY_HINT_MAX_KMH) return 0f
        return speed
    }

    fun sanitizeFromLocation(
        location: Location,
        previous: Location? = null,
        isStationaryHint: Boolean = false
    ): Float {
        val reported = if (location.hasSpeed()) {
            location.speed.coerceAtLeast(0f) * 3.6f
        } else {
            0f
        }
        var speed = sanitizeReportedKmh(reported, isStationaryHint)
        if (speed == 0f || previous == null) return speed

        val dtSec = ((location.time - previous.time) / 1000.0).toFloat()
        if (dtSec < KINEMATIC_MIN_GAP_SEC || dtSec > 30f) return speed

        val kinematicKmh = (previous.distanceTo(location) / dtSec) * 3.6f
        if (kinematicKmh < KINEMATIC_STOP_KMH && speed < KINEMATIC_DOPPLER_CAP_KMH) {
            return 0f
        }
        return speed
    }

    /** Display/chart: floor residuals and zero crawl that disagrees with almost-no movement. */
    fun sanitizeRouteSpeedsKmh(route: List<RoutePoint>): List<Float> {
        if (route.isEmpty()) return emptyList()
        val out = FloatArray(route.size) { i -> sanitizeReportedKmh(route[i].speed) }
        for (i in 1 until route.size) {
            val p1 = route[i - 1]
            val p2 = route[i]
            val dtSec = (p2.timestamp - p1.timestamp) / 1000f
            if (dtSec < KINEMATIC_MIN_GAP_SEC || dtSec > 60f) continue
            val kinematicKmh = (distanceMeters(p1, p2) / dtSec) * 3.6f
            if (kinematicKmh >= KINEMATIC_STOP_KMH) continue
            if (out[i - 1] < KINEMATIC_DOPPLER_CAP_KMH) out[i - 1] = 0f
            if (out[i] < KINEMATIC_DOPPLER_CAP_KMH) out[i] = 0f
        }
        return out.toList()
    }

    /**
     * Scrubber interpolation: do not draw a slow ramp across a near-stationary GPS gap.
     */
    fun interpolateBetween(p1: RoutePoint, p2: RoutePoint, factor: Float): Float {
        val dtSec = ((p2.timestamp - p1.timestamp) / 1000f).coerceAtLeast(0f)
        val kinematicKmh = if (dtSec >= KINEMATIC_MIN_GAP_SEC) {
            (distanceMeters(p1, p2) / dtSec) * 3.6f
        } else {
            Float.POSITIVE_INFINITY
        }

        if (dtSec >= KINEMATIC_MIN_GAP_SEC && kinematicKmh < KINEMATIC_STOP_KMH) {
            return 0f
        }

        val s1 = sanitizeReportedKmh(p1.speed)
        val s2 = sanitizeReportedKmh(p2.speed)
        if (s1 == 0f && s2 == 0f) return 0f
        if (s1 == 0f || s2 == 0f) {
            return if (factor < 0.5f) s1 else s2
        }
        return s1 + (s2 - s1) * factor.coerceIn(0f, 1f)
    }

    private fun distanceMeters(a: RoutePoint, b: RoutePoint): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            a.geoPoint.latitude,
            a.geoPoint.longitude,
            b.geoPoint.latitude,
            b.geoPoint.longitude,
            results
        )
        return results[0]
    }
}
