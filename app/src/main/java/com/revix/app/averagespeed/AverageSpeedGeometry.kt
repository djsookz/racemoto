package com.revix.app.averagespeed

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class PolylineProjection(
    val alongMeters: Double,
    val crossTrackMeters: Double,
    val t01: Double,
    val lengthMeters: Double
)

internal data class RouteProjection(
    val alongMeters: Double,
    val distanceMeters: Double
)

internal class RouteIndex(line: LineString) {
    val points: List<Point> = line.coordinates().orEmpty()
    val lengthMeters: Double
    private val cumulative: DoubleArray

    init {
        cumulative = DoubleArray(points.size)
        var total = 0.0
        for (i in 1 until points.size) {
            total += AverageSpeedGeometry.distanceMeters(points[i - 1], points[i])
            cumulative[i] = total
        }
        lengthMeters = total
    }

    fun isValid(): Boolean = points.size >= 2 && lengthMeters > 10.0

    fun project(lat: Double, lon: Double): RouteProjection? {
        if (!isValid()) return null
        return AverageSpeedGeometry.projectOnPoints(points, cumulative, lat, lon)
    }

    fun remainingFrom(alongMeters: Double): List<Point> {
        if (!isValid()) return emptyList()
        return AverageSpeedGeometry.sliceFromAlong(points, cumulative, alongMeters)
    }

    fun remainingBetween(fromAlong: Double, toAlong: Double): List<Point> {
        if (!isValid()) return emptyList()
        return AverageSpeedGeometry.sliceBetween(points, cumulative, fromAlong, toAlong)
    }
}

internal object AverageSpeedGeometry {
    private const val EARTH_RADIUS_M = 6371000.0

    fun distanceMeters(a: Point, b: Point): Double {
        return distanceMeters(a.latitude(), a.longitude(), b.latitude(), b.longitude())
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2.0 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    fun lengthMeters(points: List<Point>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += distanceMeters(points[i - 1], points[i])
        }
        return total
    }

    fun bearingDegrees(from: Point, to: Point): Double {
        val lat1 = Math.toRadians(from.latitude())
        val lat2 = Math.toRadians(to.latitude())
        val dLon = Math.toRadians(to.longitude() - from.longitude())
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun headingDeltaDegrees(a: Double, b: Double): Double {
        val delta = ((a - b + 540.0) % 360.0) - 180.0
        return kotlin.math.abs(delta)
    }

    fun projectOnSection(section: AverageSpeedSection, lat: Double, lon: Double): PolylineProjection {
        val points = section.points
        val cumulative = DoubleArray(points.size)
        var total = 0.0
        for (i in 1 until points.size) {
            total += distanceMeters(points[i - 1], points[i])
            cumulative[i] = total
        }
        val projected = projectOnPoints(points, cumulative, lat, lon)
            ?: return PolylineProjection(0.0, Double.MAX_VALUE, 0.0, total)
        val length = total.coerceAtLeast(1.0)
        return PolylineProjection(
            alongMeters = projected.alongMeters,
            crossTrackMeters = projected.distanceMeters,
            t01 = (projected.alongMeters / length).coerceIn(0.0, 1.0),
            lengthMeters = length
        )
    }

    fun remainingPolyline(section: AverageSpeedSection, alongMeters: Double): List<Point> {
        val points = section.points
        val cumulative = DoubleArray(points.size)
        var total = 0.0
        for (i in 1 until points.size) {
            total += distanceMeters(points[i - 1], points[i])
            cumulative[i] = total
        }
        return sliceFromAlong(points, cumulative, alongMeters)
    }

    fun projectOnPoints(
        points: List<Point>,
        cumulative: DoubleArray,
        lat: Double,
        lon: Double
    ): RouteProjection? {
        if (points.size < 2) return null
        var bestDist = Double.MAX_VALUE
        var bestAlong = 0.0
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val segLen = (cumulative[i] - cumulative[i - 1]).coerceAtLeast(0.001)
            val ax = a.longitude()
            val ay = a.latitude()
            val bx = b.longitude()
            val by = b.latitude()
            val abx = bx - ax
            val aby = by - ay
            val abLenSq = abx * abx + aby * aby
            val t = if (abLenSq <= 1e-18) {
                0.0
            } else {
                (((lon - ax) * abx + (lat - ay) * aby) / abLenSq).coerceIn(0.0, 1.0)
            }
            val px = ax + abx * t
            val py = ay + aby * t
            val dist = distanceMeters(lat, lon, py, px)
            if (dist < bestDist) {
                bestDist = dist
                bestAlong = cumulative[i - 1] + t * segLen
            }
        }
        return RouteProjection(alongMeters = bestAlong, distanceMeters = bestDist)
    }

    fun sliceFromAlong(
        points: List<Point>,
        cumulative: DoubleArray,
        alongMeters: Double
    ): List<Point> {
        return sliceBetween(points, cumulative, alongMeters, cumulative.last())
    }

    fun sliceBetween(
        points: List<Point>,
        cumulative: DoubleArray,
        fromAlong: Double,
        toAlong: Double
    ): List<Point> {
        if (points.size < 2) return emptyList()
        val startAlong = fromAlong.coerceIn(0.0, cumulative.last())
        val endAlong = toAlong.coerceIn(startAlong, cumulative.last())
        if (endAlong - startAlong < 5.0) return emptyList()
        val out = ArrayList<Point>(points.size)
        for (i in 1 until points.size) {
            val aAlong = cumulative[i - 1]
            val bAlong = cumulative[i]
            if (bAlong < startAlong) continue
            if (aAlong > endAlong) break
            if (out.isEmpty()) {
                val segLen = (bAlong - aAlong).coerceAtLeast(0.001)
                val t = ((startAlong - aAlong) / segLen).coerceIn(0.0, 1.0)
                out.add(interpolate(points[i - 1], points[i], t))
            }
            if (bAlong <= endAlong) {
                out.add(points[i])
            } else {
                val segLen = (bAlong - aAlong).coerceAtLeast(0.001)
                val t = ((endAlong - aAlong) / segLen).coerceIn(0.0, 1.0)
                out.add(interpolate(points[i - 1], points[i], t))
                break
            }
        }
        return if (out.size >= 2) out else emptyList()
    }

    /** True when [other] is a rigid ~constant offset of [line] (fake second carriageway). */
    fun isRigidParallelOffset(line: List<Point>, other: List<Point>): Boolean {
        val distances = sampleDistances(other, line)
        if (distances.size < 5) return false
        val min = distances.minOrNull() ?: return false
        val max = distances.maxOrNull() ?: return false
        val median = distances.sorted()[distances.size / 2]
        return median in 9.0..26.0 && (max - min) < 8.0
    }

    /**
     * Keep only stretches that sit a dual-carriageway distance from [reference].
     * Drops merged single carriageway (< min) and off-road ghosts (> max).
     */
    fun clipToSeparationBand(
        points: List<Point>,
        reference: List<Point>,
        minMeters: Double = 8.0,
        maxMeters: Double = 30.0
    ): List<List<Point>> {
        if (points.size < 2 || reference.size < 2) return emptyList()
        val cumulative = DoubleArray(reference.size)
        for (i in 1 until reference.size) {
            cumulative[i] = cumulative[i - 1] + distanceMeters(reference[i - 1], reference[i])
        }
        val stretches = ArrayList<List<Point>>()
        var current = ArrayList<Point>()
        fun flush() {
            if (current.size >= 2) stretches.add(ArrayList(current))
            current.clear()
        }
        for (point in points) {
            val distance = projectOnPoints(
                reference,
                cumulative,
                point.latitude(),
                point.longitude()
            )?.distanceMeters ?: Double.MAX_VALUE
            if (distance in minMeters..maxMeters) {
                current.add(point)
            } else {
                flush()
            }
        }
        flush()
        return stretches
    }

    private fun sampleDistances(points: List<Point>, reference: List<Point>): List<Double> {
        if (points.size < 2 || reference.size < 2) return emptyList()
        val cumulative = DoubleArray(reference.size)
        for (i in 1 until reference.size) {
            cumulative[i] = cumulative[i - 1] + distanceMeters(reference[i - 1], reference[i])
        }
        val out = ArrayList<Double>(8)
        val last = points.lastIndex
        for (step in 0..7) {
            val index = (last * step) / 7
            val distance = projectOnPoints(
                reference,
                cumulative,
                points[index].latitude(),
                points[index].longitude()
            )?.distanceMeters ?: continue
            out.add(distance)
        }
        return out
    }

    /**
     * Drop a vertex that jogs off the corridor into a ramp/parking gore.
     * Display-only — detection still uses the original gantry polyline.
     */
    fun flattenSpikes(
        points: List<Point>,
        minTurnDegrees: Double = 40.0,
        maxLegMeters: Double = 120.0,
        minXtrackMeters: Double = 12.0
    ): List<Point> {
        if (points.size < 3) return points
        val out = ArrayList<Point>(points.size)
        var i = 0
        while (i < points.size) {
            if (i in 1 until points.lastIndex) {
                val turn = headingDeltaDegrees(
                    bearingDegrees(points[i - 1], points[i]),
                    bearingDegrees(points[i], points[i + 1])
                )
                val legIn = distanceMeters(points[i - 1], points[i])
                val legOut = distanceMeters(points[i], points[i + 1])
                val chord = distanceMeters(points[i - 1], points[i + 1])
                val extra = (legIn + legOut) - chord
                val spike = extra >= minXtrackMeters &&
                    turn >= minTurnDegrees &&
                    (legIn <= maxLegMeters || legOut <= maxLegMeters)
                if (spike) {
                    i += 1
                    continue
                }
            }
            out.add(points[i])
            i += 1
        }
        return if (out.size >= 2) out else points
    }

    /**
     * Drop rest-area / ramp loops: if a short chord hides a long detour, skip the middle.
     * Display-only — detection still uses the original gantry polyline.
     */
    fun collapseDetours(
        points: List<Point>,
        minExtraMeters: Double = 70.0,
        maxChordMeters: Double = 140.0,
        maxWindow: Int = 24
    ): List<Point> {
        if (points.size < 5) return points
        val cum = DoubleArray(points.size)
        for (i in 1 until points.size) {
            cum[i] = cum[i - 1] + distanceMeters(points[i - 1], points[i])
        }
        val out = ArrayList<Point>(points.size)
        var i = 0
        while (i < points.size) {
            var skipTo = -1
            val last = minOf(points.lastIndex, i + maxWindow)
            for (j in last downTo i + 3) {
                val chord = distanceMeters(points[i], points[j])
                val path = cum[j] - cum[i]
                if (chord <= maxChordMeters && path - chord >= minExtraMeters) {
                    skipTo = j
                    break
                }
            }
            out.add(points[i])
            i = if (skipTo > i) skipTo else i + 1
        }
        return if (out.size >= 2) out else points
    }

    private fun interpolate(a: Point, b: Point, t: Double): Point {
        return Point.fromLngLat(
            a.longitude() + (b.longitude() - a.longitude()) * t,
            a.latitude() + (b.latitude() - a.latitude()) * t
        )
    }
}
