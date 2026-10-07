package com.revix.app.track

import android.graphics.Color
import com.revix.app.RoutePoint
import com.mapbox.geojson.Point as MapboxPoint
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Accel/brake coloring for track route polylines (phone GPS and RaceBox IMU).
 *
 * Green = throttle, orange→red = brake (clear transitions), soft green = maintain.
 * Light smoothing only — enough to avoid 25 Hz zebra, not enough to wash out braking.
 */
object TrackLapMapLogic {

    /** Near-zero noise floor. ~0.04 g */
    private const val DEADZONE_MPS2 = 0.40f
    /** Extra beyond deadzone to enter accel/brake. Enter ≈ 0.07 g */
    private const val ENTER_EXTRA_MPS2 = 0.30f
    private const val SPEED_SMOOTH_ALPHA = 0.30f
    private const val ACCEL_SMOOTH_ALPHA = 0.32f
    private const val ACCEL_SMOOTH_ALPHA_IMU = 0.28f
    /** |a| for full green / full red. ~0.22 g — moderate brake already reads red. */
    private const val FULL_INTENSITY_MPS2 = 2.2f
    private const val INTENSITY_STEPS = 8

    private val COLOR_SOFT_GREEN = floatArrayOf(45f, 165f, 90f)
    private val COLOR_BRIGHT_GREEN = floatArrayOf(25f, 230f, 75f)
    private val COLOR_ORANGE = floatArrayOf(255f, 145f, 35f)
    private val COLOR_BRIGHT_RED = floatArrayOf(240f, 40f, 35f)

    private enum class DrivePhase { HOLD, ACCEL, BRAKE }

    private var cachedColors: IntArray? = null
    private var cacheRouteSize: Int = -1
    private var cacheFirstTs: Long = Long.MIN_VALUE
    private var cacheLastTs: Long = Long.MIN_VALUE
    private var cacheImuLen: Int = -1

    fun getSegmentColorHex(
        routePoints: List<RoutePoint>,
        segmentIndex: Int,
        longitudinalGByRouteIndex: FloatArray? = null
    ): String? {
        if (routePoints.size < 2) return null
        if (segmentIndex !in 0 until routePoints.size - 1) return null
        val colors = colorsFor(routePoints, longitudinalGByRouteIndex)
        return toHexColor(colors[segmentIndex])
    }

    fun buildFullSegmentOptions(
        routePoints: List<RoutePoint>,
        longitudinalGByRouteIndex: FloatArray? = null
    ): List<PolylineAnnotationOptions> {
        if (routePoints.size < 2) return emptyList()

        val colors = colorsFor(routePoints, longitudinalGByRouteIndex)
        val segmentOptions = ArrayList<PolylineAnnotationOptions>(colors.size)
        for (i in colors.indices) {
            val startPoint = routePoints[i]
            val endPoint = routePoints[i + 1]
            segmentOptions.add(
                PolylineAnnotationOptions()
                    .withPoints(
                        listOf(
                            MapboxPoint.fromLngLat(startPoint.geoPoint.longitude, startPoint.geoPoint.latitude),
                            MapboxPoint.fromLngLat(endPoint.geoPoint.longitude, endPoint.geoPoint.latitude)
                        )
                    )
                    .withLineColor(toHexColor(colors[i]))
                    .withLineWidth(6.5)
            )
        }
        return segmentOptions
    }

    private fun colorsFor(
        routePoints: List<RoutePoint>,
        longitudinalGByRouteIndex: FloatArray?
    ): IntArray {
        val imuUsable = longitudinalGByRouteIndex != null &&
            longitudinalGByRouteIndex.size >= routePoints.size &&
            hasUsableImuSignal(longitudinalGByRouteIndex)
        val imuLen = if (imuUsable) longitudinalGByRouteIndex!!.size else -1
        val firstTs = routePoints.first().timestamp
        val lastTs = routePoints.last().timestamp
        val cached = cachedColors
        if (cached != null &&
            cacheRouteSize == routePoints.size &&
            cacheFirstTs == firstTs &&
            cacheLastTs == lastTs &&
            cacheImuLen == imuLen &&
            cached.size == routePoints.size - 1
        ) {
            return cached
        }
        val built = buildSegmentColors(routePoints, longitudinalGByRouteIndex)
        cachedColors = built
        cacheRouteSize = routePoints.size
        cacheFirstTs = firstTs
        cacheLastTs = lastTs
        cacheImuLen = imuLen
        return built
    }

    private fun buildSegmentColors(
        routePoints: List<RoutePoint>,
        longitudinalGByRouteIndex: FloatArray?
    ): IntArray {
        val segmentCount = routePoints.size - 1
        val colors = IntArray(segmentCount)
        val useImu = longitudinalGByRouteIndex != null &&
            longitudinalGByRouteIndex.size >= routePoints.size &&
            hasUsableImuSignal(longitudinalGByRouteIndex)

        val smoothedSpeedKmh = FloatArray(routePoints.size)
        var speedEma = routePoints.first().speed
        for (i in routePoints.indices) {
            speedEma = SPEED_SMOOTH_ALPHA * routePoints[i].speed + (1f - SPEED_SMOOTH_ALPHA) * speedEma
            smoothedSpeedKmh[i] = speedEma
        }

        val rawAccel = FloatArray(segmentCount)
        for (i in 0 until segmentCount) {
            rawAccel[i] = if (useImu) {
                val g0 = longitudinalGByRouteIndex!![i]
                val g1 = longitudinalGByRouteIndex[i + 1]
                -((g0 + g1) * 0.5f) * 9.81f
            } else {
                val dtSec = ((routePoints[i + 1].timestamp - routePoints[i].timestamp).coerceAtLeast(1L)) / 1000f
                val startMs = smoothedSpeedKmh[i] / 3.6f
                val endMs = smoothedSpeedKmh[i + 1] / 3.6f
                (endMs - startMs) / dtSec
            }
        }

        // Light window only — keep brake/throttle zones readable.
        val window = if (useImu) 3 else 2
        val windowed = FloatArray(segmentCount)
        for (i in 0 until segmentCount) {
            var sum = 0f
            var n = 0
            val from = (i - window / 2).coerceAtLeast(0)
            val to = (i + window / 2).coerceAtMost(segmentCount - 1)
            for (j in from..to) {
                sum += rawAccel[j]
                n++
            }
            windowed[i] = sum / n.coerceAtLeast(1)
        }

        val smoothAlpha = if (useImu) ACCEL_SMOOTH_ALPHA_IMU else ACCEL_SMOOTH_ALPHA
        var phase = DrivePhase.HOLD
        var accelEma = 0f
        var signedForColor = 0f

        for (i in 0 until segmentCount) {
            accelEma = smoothAlpha * windowed[i] + (1f - smoothAlpha) * accelEma
            phase = nextPhase(phase, accelEma)
            signedForColor = signedAccelForColor(phase, accelEma, signedForColor)
            colors[i] = colorFromSignedAccel(signedForColor)
        }
        return colors
    }

    internal fun hasUsableImuSignal(values: FloatArray): Boolean {
        var minG = Float.POSITIVE_INFINITY
        var maxG = Float.NEGATIVE_INFINITY
        for (value in values) {
            if (!value.isFinite()) continue
            if (value < minG) minG = value
            if (value > maxG) maxG = value
        }
        if (!minG.isFinite() || !maxG.isFinite()) return false
        return (maxG - minG) >= 0.12f
    }

    private fun nextPhase(current: DrivePhase, accelMps2: Float): DrivePhase {
        val enter = DEADZONE_MPS2 + ENTER_EXTRA_MPS2
        val exit = DEADZONE_MPS2 * 0.75f
        return when (current) {
            DrivePhase.HOLD -> when {
                accelMps2 >= enter -> DrivePhase.ACCEL
                accelMps2 <= -enter -> DrivePhase.BRAKE
                else -> DrivePhase.HOLD
            }
            DrivePhase.ACCEL -> when {
                accelMps2 <= -enter -> DrivePhase.BRAKE
                accelMps2 < exit -> DrivePhase.HOLD
                else -> DrivePhase.ACCEL
            }
            DrivePhase.BRAKE -> when {
                accelMps2 >= enter -> DrivePhase.ACCEL
                accelMps2 > -exit -> DrivePhase.HOLD
                else -> DrivePhase.BRAKE
            }
        }
    }

    private fun signedAccelForColor(
        phase: DrivePhase,
        accelMps2: Float,
        previous: Float
    ): Float {
        val target = when (phase) {
            DrivePhase.HOLD -> 0f
            DrivePhase.ACCEL -> {
                val excess = (accelMps2 - DEADZONE_MPS2).coerceAtLeast(0f)
                (excess / FULL_INTENSITY_MPS2).coerceIn(0f, 1f)
            }
            DrivePhase.BRAKE -> {
                val excess = ((-accelMps2) - DEADZONE_MPS2).coerceAtLeast(0f)
                -(excess / FULL_INTENSITY_MPS2).coerceIn(0f, 1f)
            }
        }
        // Follow target closely so brake/throttle show up immediately.
        return previous * 0.22f + target * 0.78f
    }

    /**
     * Accel (signed > 0): soft green → bright green.
     * Brake (signed < 0): soft green → orange → bright red.
     */
    private fun colorFromSignedAccel(signed: Float): Int {
        val clamped = signed.coerceIn(-1f, 1f)
        // Boost mid-range so normal braking already looks clearly orange/red.
        val intensity = abs(clamped).toDouble().pow(0.72).toFloat()
        val stepped = (intensity * INTENSITY_STEPS).roundToInt()
            .coerceIn(0, INTENSITY_STEPS)
            .toFloat() / INTENSITY_STEPS

        val rgb = if (clamped >= 0f) {
            lerpRgb(COLOR_SOFT_GREEN, COLOR_BRIGHT_GREEN, stepped)
        } else {
            // 0..0.4 → green→orange, 0.4..1 → orange→red
            if (stepped <= 0.40f) {
                lerpRgb(COLOR_SOFT_GREEN, COLOR_ORANGE, (stepped / 0.40f).coerceIn(0f, 1f))
            } else {
                lerpRgb(COLOR_ORANGE, COLOR_BRIGHT_RED, ((stepped - 0.40f) / 0.60f).coerceIn(0f, 1f))
            }
        }
        return Color.rgb(
            rgb[0].roundToInt().coerceIn(0, 255),
            rgb[1].roundToInt().coerceIn(0, 255),
            rgb[2].roundToInt().coerceIn(0, 255)
        )
    }

    private fun lerpRgb(from: FloatArray, to: FloatArray, t: Float): FloatArray {
        val u = t.coerceIn(0f, 1f)
        return floatArrayOf(
            from[0] + (to[0] - from[0]) * u,
            from[1] + (to[1] - from[1]) * u,
            from[2] + (to[2] - from[2]) * u
        )
    }

    private fun toHexColor(colorInt: Int): String {
        return String.format(Locale.US, "#%06X", 0xFFFFFF and colorInt)
    }
}
