package com.example.clinometer

import android.content.Context
import com.example.clinometer.track.catalog.OfficialTrackCatalog
import com.example.clinometer.track.catalog.TrackMode
import com.example.clinometer.tracking.CustomTrackMode
import com.example.clinometer.tracking.CustomTrackStorage
import com.google.gson.Gson
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class TrackMiniMapShapeResolver(private val context: Context) {

    private val gson = Gson()
    private val trackManager = TrackManager(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun resolveMiniMapPoints(
        trackId: String,
        orderedLaps: List<LapData>,
        routeFallback: List<GeoPoint>,
        isCircuit: Boolean
    ): List<GeoPoint> {
        val normalizedTrackId = trackId.trim()
        val expectedTrackDistanceMeters = resolveExpectedTrackDistanceMeters(normalizedTrackId)

        resolveOfficialTemplate(normalizedTrackId, isCircuit)?.let { return it }
        resolveCustomTemplate(normalizedTrackId)?.let { return it }

        val storedRepresentative = loadRepresentativeShape(normalizedTrackId)
            ?.let { stored ->
                normalizeShape(stored.points, closeLoop = stored.isCircuit)
                    .toRepresentativeCandidate()
            }
            ?.takeIf { candidate ->
                isUsableRepresentativeCandidate(
                    candidate = candidate,
                    expectedTrackDistanceMeters = expectedTrackDistanceMeters,
                    isCircuit = isCircuit
                )
            }

        val currentRepresentative = buildRepresentativeLapShape(
            orderedLaps = orderedLaps,
            isCircuit = isCircuit,
            expectedTrackDistanceMeters = expectedTrackDistanceMeters
        )

        if (currentRepresentative != null) {
            val shouldUseCurrent = storedRepresentative == null ||
                isCandidateBetter(
                    candidate = currentRepresentative,
                    reference = storedRepresentative,
                    expectedTrackDistanceMeters = expectedTrackDistanceMeters,
                    isCircuit = isCircuit
                )

            if (shouldUseCurrent) {
                saveRepresentativeShape(normalizedTrackId, currentRepresentative.points, isCircuit)
                return currentRepresentative.points
            }
        }

        storedRepresentative?.let { return it.points }

        return normalizeShape(routeFallback, closeLoop = false)
    }

    private fun resolveExpectedTrackDistanceMeters(trackId: String): Double? {
        if (trackId.isBlank()) return null

        OfficialTrackCatalog.tracks.firstOrNull { definition ->
            definition.id == trackId &&
                definition.mode == TrackMode.CIRCUIT &&
                definition.lengthKm > 0.0
        }?.let { return it.lengthKm * 1000.0 }

        val customTrack = CustomTrackStorage.loadCustomTrackV2(context, trackId) ?: return null
        if (customTrack.mode != CustomTrackMode.CIRCUIT) return null

        return customTrack.measuredDistanceMeters
            ?.toDouble()
            ?.takeIf { it >= MIN_PATH_DISTANCE_METERS }
    }

    private fun List<GeoPoint>.toRepresentativeCandidate(): RepresentativeShapeCandidate? {
        if (size < MIN_OUTPUT_POINTS) return null

        val pathDistanceMeters = calculatePathDistanceMeters(this)
        if (pathDistanceMeters < MIN_PATH_DISTANCE_METERS) return null

        val loopGapMeters = if (size > 1) {
            first().distanceToAsDouble(last())
        } else {
            Double.MAX_VALUE
        }

        return RepresentativeShapeCandidate(
            points = this,
            pathDistanceMeters = pathDistanceMeters,
            pointCount = size,
            loopGapMeters = loopGapMeters
        )
    }

    private fun isUsableRepresentativeCandidate(
        candidate: RepresentativeShapeCandidate,
        expectedTrackDistanceMeters: Double?,
        isCircuit: Boolean
    ): Boolean {
        if (candidate.points.size < MIN_OUTPUT_POINTS) return false
        if (candidate.pathDistanceMeters < MIN_PATH_DISTANCE_METERS) return false
        if (isCircuit && candidate.loopGapMeters > FULL_LAP_CLOSED_GAP_METERS) return false

        if (expectedTrackDistanceMeters != null && expectedTrackDistanceMeters >= MIN_PATH_DISTANCE_METERS) {
            val distanceRatio = candidate.pathDistanceMeters / expectedTrackDistanceMeters
            if (distanceRatio < MIN_EXPECTED_DISTANCE_RATIO || distanceRatio > MAX_EXPECTED_DISTANCE_RATIO) {
                return false
            }
        }

        return true
    }

    private fun resolveOfficialTemplate(trackId: String, isCircuit: Boolean): List<GeoPoint>? {
        if (trackId.isBlank()) return null
        val trackData = trackManager.loadTrackData(trackId) ?: return null
        return normalizeShape(trackData.trackPoints.map { it.geoPoint }, closeLoop = isCircuit)
            .takeIf { it.size >= MIN_OUTPUT_POINTS }
    }

    private fun resolveCustomTemplate(trackId: String): List<GeoPoint>? {
        if (trackId.isBlank()) return null
        val customTrack = CustomTrackStorage.loadCustomTrackV2(context, trackId) ?: return null
        if (customTrack.referencePath.size < 2) return null

        val isCircuit = customTrack.mode == CustomTrackMode.CIRCUIT
        return normalizeShape(customTrack.referencePath, closeLoop = isCircuit)
            .takeIf { it.size >= MIN_OUTPUT_POINTS }
    }

    private fun buildRepresentativeLapShape(
        orderedLaps: List<LapData>,
        isCircuit: Boolean,
        expectedTrackDistanceMeters: Double?
    ): RepresentativeShapeCandidate? {
        val candidates = orderedLaps.mapNotNull { lap ->
            val routePoints = lap.routePoints.map { it.geoPoint }
            if (routePoints.size < MIN_SOURCE_POINTS) {
                return@mapNotNull null
            }

            val normalized = normalizeShape(routePoints, closeLoop = isCircuit)
            val representative = normalized.toRepresentativeCandidate()
                ?: return@mapNotNull null

            if (representative.points.size < MIN_OUTPUT_POINTS) {
                return@mapNotNull null
            }

            LapShapeCandidate(
                points = representative.points,
                pathDistanceMeters = representative.pathDistanceMeters,
                pointCount = representative.pointCount,
                isCompleted = lap.endTime > lap.startTime,
                isFullLap = !isCircuit || representative.loopGapMeters <= FULL_LAP_CLOSED_GAP_METERS,
                loopGapMeters = representative.loopGapMeters
            )
        }

        val completedFullLapCandidates = candidates
            .filter { it.isCompleted && it.isFullLap }
            .filter { candidate ->
                isUsableRepresentativeCandidate(
                    candidate = RepresentativeShapeCandidate(
                        points = candidate.points,
                        pathDistanceMeters = candidate.pathDistanceMeters,
                        pointCount = candidate.pointCount,
                        loopGapMeters = candidate.loopGapMeters
                    ),
                    expectedTrackDistanceMeters = expectedTrackDistanceMeters,
                    isCircuit = isCircuit
                )
            }

        if (completedFullLapCandidates.isEmpty()) return null
        if (completedFullLapCandidates.size == 1) {
            return completedFullLapCandidates.first().toRepresentativeCandidate()
        }

        val referenceDistance = expectedTrackDistanceMeters
            ?.takeIf { it >= MIN_PATH_DISTANCE_METERS }
            ?: completedFullLapCandidates.minOf { it.pathDistanceMeters }
        val maxPointCount = completedFullLapCandidates.maxOf { it.pointCount }.toDouble()

        return completedFullLapCandidates.minByOrNull { candidate ->
            scoreCandidate(
                candidateDistanceMeters = candidate.pathDistanceMeters,
                candidatePointCount = candidate.pointCount,
                candidateLoopGapMeters = candidate.loopGapMeters,
                referenceDistanceMeters = referenceDistance,
                hasExpectedDistance = expectedTrackDistanceMeters != null,
                maxPointCount = maxPointCount,
                isCircuit = isCircuit
            )
        }?.toRepresentativeCandidate()
    }

    private fun isCandidateBetter(
        candidate: RepresentativeShapeCandidate,
        reference: RepresentativeShapeCandidate,
        expectedTrackDistanceMeters: Double?,
        isCircuit: Boolean
    ): Boolean {
        val referenceDistance = expectedTrackDistanceMeters
            ?.takeIf { it >= MIN_PATH_DISTANCE_METERS }
            ?: min(candidate.pathDistanceMeters, reference.pathDistanceMeters)
        val maxPointCount = max(candidate.pointCount.toDouble(), reference.pointCount.toDouble())

        val candidateScore = scoreCandidate(
            candidateDistanceMeters = candidate.pathDistanceMeters,
            candidatePointCount = candidate.pointCount,
            candidateLoopGapMeters = candidate.loopGapMeters,
            referenceDistanceMeters = referenceDistance,
            hasExpectedDistance = expectedTrackDistanceMeters != null,
            maxPointCount = maxPointCount,
            isCircuit = isCircuit
        )
        val referenceScore = scoreCandidate(
            candidateDistanceMeters = reference.pathDistanceMeters,
            candidatePointCount = reference.pointCount,
            candidateLoopGapMeters = reference.loopGapMeters,
            referenceDistanceMeters = referenceDistance,
            hasExpectedDistance = expectedTrackDistanceMeters != null,
            maxPointCount = maxPointCount,
            isCircuit = isCircuit
        )

        if (candidateScore + QUALITY_REPLACE_MARGIN < referenceScore) {
            return true
        }

        if (abs(candidateScore - referenceScore) <= QUALITY_REPLACE_MARGIN) {
            if (candidate.pointCount >= reference.pointCount + MIN_POINT_COUNT_REPLACE_DELTA) {
                return true
            }
            if (isCircuit && candidate.loopGapMeters + LOOP_GAP_REPLACE_MARGIN_METERS < reference.loopGapMeters) {
                return true
            }
        }

        return false
    }

    private fun scoreCandidate(
        candidateDistanceMeters: Double,
        candidatePointCount: Int,
        candidateLoopGapMeters: Double,
        referenceDistanceMeters: Double,
        hasExpectedDistance: Boolean,
        maxPointCount: Double,
        isCircuit: Boolean
    ): Double {
        val distancePenalty = abs(candidateDistanceMeters - referenceDistanceMeters) /
            max(referenceDistanceMeters, MIN_PATH_DISTANCE_METERS)
        val overweightPenalty = if (hasExpectedDistance) {
            0.0
        } else {
            max(0.0, candidateDistanceMeters - referenceDistanceMeters) /
                max(referenceDistanceMeters, MIN_PATH_DISTANCE_METERS)
        }
        val densityPenalty = if (maxPointCount <= 0.0) {
            0.0
        } else {
            (maxPointCount - candidatePointCount) / maxPointCount
        }
        val closurePenalty = if (isCircuit) {
            candidateLoopGapMeters / FULL_LAP_CLOSED_GAP_METERS
        } else {
            0.0
        }

        return distancePenalty * 6.0 + overweightPenalty * 3.0 + densityPenalty * 0.35 + closurePenalty
    }

    private fun normalizeShape(points: List<GeoPoint>, closeLoop: Boolean): List<GeoPoint> {
        val deduped = dedupeConsecutivePoints(points)
        if (deduped.size < 2) return deduped

        val normalizedPoints = if (closeLoop) closeLoopIfNeeded(deduped) else deduped
        val totalDistance = calculatePathDistanceMeters(normalizedPoints)
        if (totalDistance < MIN_PATH_DISTANCE_METERS) {
            return normalizedPoints
        }

        val maxPoints = when {
            totalDistance >= 15_000.0 -> 420
            totalDistance >= 7_000.0 -> 340
            totalDistance >= 2_500.0 -> 280
            else -> 220
        }

        return if (normalizedPoints.size > maxPoints) {
            resamplePolyline(normalizedPoints, maxPoints)
        } else {
            normalizedPoints
        }
    }

    private fun dedupeConsecutivePoints(points: List<GeoPoint>): List<GeoPoint> {
        if (points.isEmpty()) return emptyList()

        val deduped = ArrayList<GeoPoint>(points.size)
        points.forEach { point ->
            val last = deduped.lastOrNull()
            if (last == null || last.distanceToAsDouble(point) >= MIN_CONSECUTIVE_DISTANCE_METERS) {
                deduped += point
            }
        }
        return deduped
    }

    private fun closeLoopIfNeeded(points: List<GeoPoint>): List<GeoPoint> {
        if (points.size < 3) return points

        val start = points.first()
        val end = points.last()
        val loopGapMeters = start.distanceToAsDouble(end)
        if (loopGapMeters <= SAME_POINT_DISTANCE_METERS) {
            return points
        }

        val totalDistance = calculatePathDistanceMeters(points)
        val closureThreshold = min(
            LOOP_CLOSE_MAX_METERS,
            max(LOOP_CLOSE_MIN_METERS, totalDistance * LOOP_CLOSE_RATIO)
        )
        return if (loopGapMeters <= closureThreshold) {
            points + start
        } else {
            points
        }
    }

    private fun resamplePolyline(points: List<GeoPoint>, targetCount: Int): List<GeoPoint> {
        if (points.size <= 2 || targetCount >= points.size) return points

        val cumulativeDistances = DoubleArray(points.size)
        for (index in 1 until points.size) {
            cumulativeDistances[index] = cumulativeDistances[index - 1] +
                points[index - 1].distanceToAsDouble(points[index])
        }

        val totalDistance = cumulativeDistances.last()
        if (totalDistance <= 0.0) return points

        val sampled = ArrayList<GeoPoint>(targetCount)
        var segmentIndex = 1
        for (sampleIndex in 0 until targetCount) {
            val targetDistance = if (sampleIndex == targetCount - 1) {
                totalDistance
            } else {
                totalDistance * sampleIndex.toDouble() / (targetCount - 1).toDouble()
            }

            while (segmentIndex < cumulativeDistances.lastIndex && cumulativeDistances[segmentIndex] < targetDistance) {
                segmentIndex++
            }

            val leftIndex = (segmentIndex - 1).coerceAtLeast(0)
            val rightIndex = segmentIndex.coerceIn(0, points.lastIndex)
            val leftPoint = points[leftIndex]
            val rightPoint = points[rightIndex]
            val leftDistance = cumulativeDistances[leftIndex]
            val rightDistance = cumulativeDistances[rightIndex]
            val segmentDistance = (rightDistance - leftDistance).takeIf { it > 0.0 } ?: 1.0
            val factor = ((targetDistance - leftDistance) / segmentDistance).coerceIn(0.0, 1.0)

            sampled += GeoPoint(
                latitude = leftPoint.latitude + (rightPoint.latitude - leftPoint.latitude) * factor,
                longitude = leftPoint.longitude + (rightPoint.longitude - leftPoint.longitude) * factor
            )
        }
        return dedupeConsecutivePoints(sampled)
    }

    private fun loadRepresentativeShape(trackId: String): StoredMiniMapShape? {
        return loadStoredShape(trackId)?.takeIf { stored ->
            stored.source == SOURCE_REPRESENTATIVE_LAP &&
                stored.trackId == trackId &&
                stored.points.size >= MIN_OUTPUT_POINTS
        }
    }

    private fun loadStoredShape(trackId: String): StoredMiniMapShape? {
        if (trackId.isBlank()) return null
        val json = prefs.getString(shapeKey(trackId), null) ?: return null
        return runCatching {
            gson.fromJson(json, StoredMiniMapShape::class.java)
        }.getOrNull()
    }

    private fun saveRepresentativeShape(
        trackId: String,
        points: List<GeoPoint>,
        isCircuit: Boolean
    ) {
        if (trackId.isBlank() || points.size < MIN_OUTPUT_POINTS) return

        val payload = StoredMiniMapShape(
            trackId = trackId,
            isCircuit = isCircuit,
            source = SOURCE_REPRESENTATIVE_LAP,
            points = points
        )
        prefs.edit().putString(shapeKey(trackId), gson.toJson(payload)).apply()
    }

    private fun calculatePathDistanceMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0

        var total = 0.0
        for (index in 1 until points.size) {
            total += points[index - 1].distanceToAsDouble(points[index])
        }
        return total
    }

    private fun shapeKey(trackId: String): String = "shape_$trackId"

    private data class LapShapeCandidate(
        val points: List<GeoPoint>,
        val pathDistanceMeters: Double,
        val pointCount: Int,
        val isCompleted: Boolean,
        val isFullLap: Boolean,
        val loopGapMeters: Double
    )

    private fun LapShapeCandidate.toRepresentativeCandidate(): RepresentativeShapeCandidate {
        return RepresentativeShapeCandidate(
            points = points,
            pathDistanceMeters = pathDistanceMeters,
            pointCount = pointCount,
            loopGapMeters = loopGapMeters
        )
    }

    private data class RepresentativeShapeCandidate(
        val points: List<GeoPoint>,
        val pathDistanceMeters: Double,
        val pointCount: Int,
        val loopGapMeters: Double
    )

    private data class StoredMiniMapShape(
        val version: Int = 1,
        val trackId: String,
        val isCircuit: Boolean,
        val source: String,
        val createdAt: Long = System.currentTimeMillis(),
        val points: List<GeoPoint>
    )

    private companion object {
        const val PREFS_NAME = "track_minimap_shapes"
        const val SOURCE_REPRESENTATIVE_LAP = "representative_lap"
        const val MIN_SOURCE_POINTS = 16
        const val MIN_OUTPUT_POINTS = 12
        const val MIN_PATH_DISTANCE_METERS = 120.0
        const val MIN_CONSECUTIVE_DISTANCE_METERS = 2.0
        const val SAME_POINT_DISTANCE_METERS = 5.0
        const val FULL_LAP_CLOSED_GAP_METERS = 8.0
        const val MIN_EXPECTED_DISTANCE_RATIO = 0.55
        const val MAX_EXPECTED_DISTANCE_RATIO = 1.45
        const val LOOP_CLOSE_MIN_METERS = 45.0
        const val LOOP_CLOSE_MAX_METERS = 180.0
        const val LOOP_CLOSE_RATIO = 0.04
        const val QUALITY_REPLACE_MARGIN = 0.08
        const val MIN_POINT_COUNT_REPLACE_DELTA = 12
        const val LOOP_GAP_REPLACE_MARGIN_METERS = 4.0
    }
}