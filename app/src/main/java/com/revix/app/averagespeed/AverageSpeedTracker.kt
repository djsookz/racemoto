package com.revix.app.averagespeed

import android.location.Location
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point

internal enum class AverageSpeedVoiceEvent {
    APPROACH,
    ENTER,
    INSIDE_MID,
    EXIT
}

internal data class AverageSpeedUiState(
    val section: AverageSpeedSection? = null,
    val showProgressBar: Boolean = false,
    val showSign: Boolean = false,
    val signApproachMeters: Int = 0,
    val speedLimitKmh: Int = 0,
    val progress01: Float = 0f,
    val remainingMeters: Float = 0f,
    val voiceDistanceMeters: Float = 0f,
    val remainingLine: List<Point> = emptyList(),
    val voice: AverageSpeedVoiceEvent? = null,
    val instantAverageKmh: Float? = null
)

/**
 * Detects certified average-speed corridors.
 *
 * Progress bar when a guided route (navigation or follow-line) includes the official start.
 * Voice only in turn-by-turn navigation. Follow-line, free-ride and idle are visual only.
 */
internal class AverageSpeedTracker {

    private data class RouteBinding(
        val section: AverageSpeedSection,
        val startAlong: Double,
        val endAlong: Double,
        val includesOfficialStart: Boolean,
        val includesOfficialEnd: Boolean
    )

    private var sections: List<AverageSpeedSection> = emptyList()
    private var routeIndex: RouteIndex? = null
    private var guidedRouteActive = false
    private var voiceEnabled = false
    private var bindings: List<RouteBinding> = emptyList()

    private var activeId: String? = null
    private var inside = false
    private var approachedId: String? = null
    private var announceInsideOnNavStart = false
    private var enteredId: String? = null
    private var enteredFromOfficialStart = false
    private var lastAlongT = -1.0
    private var outsideStreak = 0
    private var avgLastLat = Double.NaN
    private var avgLastLon = Double.NaN
    private var avgLastElapsedNanos = 0L
    private var avgDistanceM = 0.0
    private var avgDurationNs = 0L

    fun setSections(newSections: List<AverageSpeedSection>) {
        if (newSections.isEmpty() && sections.isNotEmpty()) {
            return
        }
        sections = newSections
        rebuildBindings()
        if (activeId != null && sections.none { it.id == activeId }) {
            resetVisit()
        }
    }

    fun setGuidedRoute(line: LineString?, active: Boolean, voiceEnabled: Boolean = false) {
        val nowGuided = active && line != null
        val startedGuided = nowGuided && !guidedRouteActive
        guidedRouteActive = nowGuided
        this.voiceEnabled = nowGuided && voiceEnabled
        routeIndex = line?.let { RouteIndex(it) }?.takeIf { it.isValid() }
        if (!guidedRouteActive) {
            routeIndex = null
        }
        rebuildBindings()
        if (!this.voiceEnabled) {
            approachedId = null
            announceInsideOnNavStart = false
        } else if (startedGuided && inside) {
            announceInsideOnNavStart = true
        }
    }

    fun clearGuidedRoute() {
        setGuidedRoute(null, false, voiceEnabled = false)
    }

    fun update(location: Location): AverageSpeedUiState {
        if (sections.isEmpty()) return AverageSpeedUiState()

        val lat = location.latitude
        val lon = location.longitude
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        val heading = if (location.hasBearing()) location.bearing.toDouble() else null
        val routeProj = routeIndex?.project(lat, lon)

        val routeCandidate = pickRouteCandidate(routeProj)
        val corridorCandidate = pickCorridorCandidate(lat, lon, heading, speedKmh)
        // Sign/inside always follow the catalog polyline (same as free-ride / home).
        // Route bindings are only for voice, progress bar, and remaining-line geometry.
        if (corridorCandidate == null && routeCandidate == null) {
            return handleExitIfNeeded()
        }

        val section = corridorCandidate?.section ?: routeCandidate!!.section
        val alongMeters = corridorCandidate?.alongMeters ?: routeCandidate!!.alongMeters
        val remainingMeters = corridorCandidate?.remainingMeters ?: routeCandidate!!.remainingMeters
        val includesStart = corridorCandidate?.includesStart
            ?: routeCandidate?.includesStart
            ?: false
        val length = section.lengthMeters.coerceAtLeast(1.0)
        val t01 = (alongMeters / length).coerceIn(0.0, 1.0)
        val remainingLine = if (routeIndex != null && routeCandidate != null) {
            val binding = bindings.firstOrNull { it.section.id == section.id }
            if (binding != null && routeProj != null) {
                val fromAlong = maxOf(routeProj.alongMeters, binding.startAlong)
                routeIndex!!.remainingBetween(fromAlong, binding.endAlong)
                    .ifEmpty { AverageSpeedGeometry.remainingPolyline(section, alongMeters) }
            } else {
                AverageSpeedGeometry.remainingPolyline(section, alongMeters)
            }
        } else {
            AverageSpeedGeometry.remainingPolyline(section, alongMeters)
        }

        val corridorDistanceToStart = remainingToStart(corridorCandidate?.alongMeters ?: alongMeters)
        val inSignApproach = !guidedRouteActive &&
            !inside &&
            corridorCandidate != null &&
            corridorDistanceToStart in 1.0..APPROACH_SIGN_MAX_M

        var voice: AverageSpeedVoiceEvent? = null
        val nowInside = corridorCandidate != null && isPhysicallyInside(corridorCandidate)
        if (nowInside) {
            outsideStreak = 0
            val firstEntry = !inside || activeId != section.id
            inside = true
            activeId = section.id
            if (firstEntry) {
                enteredFromOfficialStart = includesStart &&
                    (lastAlongT < 0.0 || lastAlongT < START_T_GRACE || alongMeters < START_GRACE_M)
                resetAverage()
                if (enteredFromOfficialStart) {
                    sampleAverage(location)
                }
                if (enteredId != section.id) {
                    enteredId = section.id
                    if (voiceEnabled) {
                        voice = AverageSpeedVoiceEvent.ENTER
                    }
                }
            } else if (enteredFromOfficialStart) {
                sampleAverage(location)
            }
            if (announceInsideOnNavStart && voiceEnabled && voice == null) {
                announceInsideOnNavStart = false
                voice = AverageSpeedVoiceEvent.ENTER
            } else if (announceInsideOnNavStart) {
                announceInsideOnNavStart = false
            }
        } else if (inside && (corridorCandidate == null || activeId == section.id)) {
            outsideStreak++
            if (outsideStreak >= OUTSIDE_STREAK_TO_EXIT) {
                return finishExit(section, remainingLine)
            }
        }

        lastAlongT = t01
        val routeIncludesStart = routeCandidate?.includesStart == true ||
            bindings.any { it.section.id == section.id && it.includesOfficialStart }
        val showBar = guidedRouteActive && inside && enteredFromOfficialStart && routeIncludesStart
        val roundedApproach = ((corridorDistanceToStart / 50.0).toInt() * 50).coerceIn(50, 500)
        return AverageSpeedUiState(
            section = if (inside || inSignApproach) section else null,
            showProgressBar = showBar,
            showSign = inside || inSignApproach,
            signApproachMeters = if (inSignApproach) roundedApproach else 0,
            speedLimitKmh = section.speedLimitKmh,
            progress01 = t01.toFloat(),
            remainingMeters = remainingMeters.toFloat().coerceAtLeast(0f),
            remainingLine = if (inside || inSignApproach) remainingLine else emptyList(),
            voice = if (voiceEnabled) voice else null,
            instantAverageKmh = if (inside && enteredFromOfficialStart) instantAverageKmh() else null
        )
    }

    private data class Candidate(
        val section: AverageSpeedSection,
        val alongMeters: Double,
        val remainingMeters: Double,
        val includesStart: Boolean,
        val fromRoute: Boolean
    )

    private fun pickRouteCandidate(routeProj: RouteProjection?): Candidate? {
        if (!guidedRouteActive || routeProj == null) return null
        if (routeProj.distanceMeters > ROUTE_USER_SNAP_M) return null
        var best: Candidate? = null
        for (binding in bindings) {
            if (!binding.includesOfficialEnd && !binding.includesOfficialStart) continue
            val alongOnSection = routeProj.alongMeters - binding.startAlong
            val remaining = binding.endAlong - routeProj.alongMeters
            val beforeStart = routeProj.alongMeters < binding.startAlong - 30.0
            val afterEnd = routeProj.alongMeters > binding.endAlong + EXIT_AFTER_END_M
            val inSpan = routeProj.alongMeters >= binding.startAlong - APPROACH_MAX_M &&
                routeProj.alongMeters <= binding.endAlong + EXIT_AFTER_END_M
            if (!inSpan || afterEnd) continue
            if (beforeStart && !binding.includesOfficialStart) continue
            val candidate = Candidate(
                section = binding.section,
                alongMeters = alongOnSection,
                remainingMeters = remaining.coerceAtLeast(0.0),
                includesStart = binding.includesOfficialStart,
                fromRoute = true
            )
            if (best == null || candidate.remainingMeters < best.remainingMeters) {
                best = candidate
            }
        }
        return best
    }

    private fun pickCorridorCandidate(
        lat: Double,
        lon: Double,
        heading: Double?,
        speedKmh: Float
    ): Candidate? {
        var best: Candidate? = null
        var bestCross = Double.MAX_VALUE
        for (section in sections) {
            val proj = AverageSpeedGeometry.projectOnSection(section, lat, lon)
            val width = if (inside && activeId == section.id) CORRIDOR_HOLD_M else CORRIDOR_ENTER_M
            if (proj.crossTrackMeters > width) continue
            if (proj.alongMeters < -APPROACH_SIGN_MAX_M) continue
            if (proj.t01 > 1.04) continue
            if (heading != null && speedKmh >= MIN_HEADING_SPEED_KMH) {
                val sectionBearing = AverageSpeedGeometry.bearingDegrees(section.start, section.end)
                if (AverageSpeedGeometry.headingDeltaDegrees(heading, sectionBearing) > MAX_HEADING_DELTA) {
                    continue
                }
            }
            val remaining = (proj.lengthMeters - proj.alongMeters).coerceAtLeast(0.0)
            val candidate = Candidate(
                section = section,
                alongMeters = proj.alongMeters,
                remainingMeters = remaining,
                includesStart = proj.alongMeters <= START_GRACE_M,
                fromRoute = false
            )
            if (proj.crossTrackMeters < bestCross) {
                bestCross = proj.crossTrackMeters
                best = candidate
            }
        }
        return best
    }

    private fun isPhysicallyInside(candidate: Candidate): Boolean {
        val alongMeters = candidate.alongMeters
        val remainingMeters = candidate.remainingMeters
        if (remainingMeters <= EXIT_REMAINING_M && alongMeters > START_GRACE_M) return false
        val t = alongMeters / candidate.section.lengthMeters.coerceAtLeast(1.0)
        return t in 0.0..0.98
    }

    private fun remainingToStart(alongMeters: Double): Double {
        return if (alongMeters >= 0.0) 0.0 else -alongMeters
    }

    private fun handleExitIfNeeded(): AverageSpeedUiState {
        outsideStreak++
        val section = sections.firstOrNull { it.id == activeId }
        if (inside && section != null && outsideStreak >= OUTSIDE_STREAK_TO_EXIT) {
            return finishExit(section, emptyList())
        }
        if (!inside) {
            outsideStreak = 0
            approachedId = null
        }
        return AverageSpeedUiState()
    }

    private fun finishExit(section: AverageSpeedSection, remainingLine: List<Point>): AverageSpeedUiState {
        val voice = if (voiceEnabled && enteredId == section.id) {
            AverageSpeedVoiceEvent.EXIT
        } else {
            null
        }
        resetVisit()
        return AverageSpeedUiState(
            remainingLine = remainingLine,
            voice = voice
        )
    }

    private fun resetVisit() {
        inside = false
        activeId = null
        enteredId = null
        approachedId = null
        enteredFromOfficialStart = false
        announceInsideOnNavStart = false
        lastAlongT = -1.0
        outsideStreak = 0
        resetAverage()
    }

    private fun resetAverage() {
        avgLastLat = Double.NaN
        avgLastLon = Double.NaN
        avgLastElapsedNanos = 0L
        avgDistanceM = 0.0
        avgDurationNs = 0L
    }

    private fun sampleAverage(location: Location) {
        val elapsed = if (location.elapsedRealtimeNanos != 0L) {
            location.elapsedRealtimeNanos
        } else {
            location.time * 1_000_000L
        }
        if (!avgLastLat.isNaN() && avgLastElapsedNanos > 0L) {
            val delta = FloatArray(1)
            Location.distanceBetween(
                avgLastLat,
                avgLastLon,
                location.latitude,
                location.longitude,
                delta
            )
            val dt = elapsed - avgLastElapsedNanos
            val dist = delta[0].toDouble()
            if (dt in 50_000_000L..4_000_000_000L && dist in 0.3..80.0) {
                avgDistanceM += dist
                avgDurationNs += dt
            }
        }
        avgLastLat = location.latitude
        avgLastLon = location.longitude
        avgLastElapsedNanos = elapsed
    }

    private fun instantAverageKmh(): Float? {
        if (avgDurationNs < 2_500_000_000L || avgDistanceM < 40.0) return null
        val hours = avgDurationNs / 1_000_000_000.0 / 3600.0
        if (hours <= 0.0) return null
        val kmh = (avgDistanceM / 1000.0 / hours).toFloat()
        if (!kmh.isFinite() || kmh < 1f || kmh > 250f) return null
        return kmh
    }

    private fun rebuildBindings() {
        val index = routeIndex
        if (index == null) {
            bindings = emptyList()
            return
        }
        bindings = sections.mapNotNull { section ->
            val startSnap = index.project(section.start.latitude(), section.start.longitude()) ?: return@mapNotNull null
            val endSnap = index.project(section.end.latitude(), section.end.longitude()) ?: return@mapNotNull null
            val includesStart = startSnap.distanceMeters <= START_ON_ROUTE_M
            val includesEnd = endSnap.distanceMeters <= END_ON_ROUTE_M
            if (!includesStart && !includesEnd) return@mapNotNull null
            if (includesStart && includesEnd && endSnap.alongMeters <= startSnap.alongMeters + 80.0) {
                return@mapNotNull null
            }
            val startAlong = if (includesStart) startSnap.alongMeters else {
                (endSnap.alongMeters - section.lengthMeters).coerceAtLeast(0.0)
            }
            val endAlong = if (includesEnd) endSnap.alongMeters else {
                (startAlong + section.lengthMeters).coerceAtMost(index.lengthMeters)
            }
            if (endAlong <= startAlong + 80.0) return@mapNotNull null
            RouteBinding(
                section = section,
                startAlong = startAlong,
                endAlong = endAlong,
                includesOfficialStart = includesStart,
                includesOfficialEnd = includesEnd
            )
        }
    }

    private companion object {
        const val START_ON_ROUTE_M = 550.0
        const val END_ON_ROUTE_M = 550.0
        const val ROUTE_USER_SNAP_M = 80.0
        const val CORRIDOR_ENTER_M = 280.0
        const val CORRIDOR_HOLD_M = 420.0
        const val MAX_HEADING_DELTA = 55.0
        const val MIN_HEADING_SPEED_KMH = 15f
        const val APPROACH_SIGN_MAX_M = 420.0
        const val APPROACH_MAX_M = 420.0
        const val START_GRACE_M = 450.0
        const val START_T_GRACE = 0.08
        const val EXIT_REMAINING_M = 70.0
        const val EXIT_AFTER_END_M = 120.0
        const val OUTSIDE_STREAK_TO_EXIT = 3
    }
}
