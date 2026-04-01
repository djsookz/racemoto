package com.example.clinometer.track

import com.example.clinometer.LapData
import com.example.clinometer.RoutePoint
import kotlin.math.abs

fun enrichRoutePointsWithLeanPeaks(lapData: LapData): List<RoutePoint> {
    if (lapData.routePoints.isEmpty()) return emptyList()

    val sampleCount = minOf(lapData.leanAngleData.size, lapData.timestamps.size)
    if (sampleCount <= 0 || lapData.startTime <= 0L) {
        return lapData.routePoints
    }

    val leanSamples = ArrayList<Pair<Long, Float>>(sampleCount)
    repeat(sampleCount) { index ->
        val angle = lapData.leanAngleData[index]
        if (!angle.isFinite()) return@repeat
        val relativeTimeMs = lapData.timestamps[index] - lapData.startTime
        leanSamples.add(relativeTimeMs to angle)
    }

    if (leanSamples.isEmpty()) {
        return lapData.routePoints
    }

    val sortedSamples = leanSamples.sortedBy { it.first }
    val routePoints = lapData.routePoints

    return routePoints.mapIndexed { index, point ->
        val currentTime = point.timestamp
        val prevTime = routePoints.getOrNull(index - 1)?.timestamp ?: currentTime
        val nextTime = routePoints.getOrNull(index + 1)?.timestamp ?: currentTime

        val startBound = if (index == 0) {
            currentTime - ((nextTime - currentTime).coerceAtLeast(0L) / 2L)
        } else {
            (prevTime + currentTime) / 2L
        }

        val endBound = if (index == routePoints.lastIndex) {
            currentTime + ((currentTime - prevTime).coerceAtLeast(0L) / 2L)
        } else {
            (currentTime + nextTime) / 2L
        }

        val minBound = minOf(startBound, endBound)
        val maxBound = maxOf(startBound, endBound)

        val peakSample = sortedSamples
            .asSequence()
            .filter { (time, _) -> time in minBound..maxBound }
            .maxByOrNull { (_, angle) -> abs(angle) }

        val nearestSample = if (peakSample == null) {
            sortedSamples.minByOrNull { (time, _) -> abs(time - currentTime).toFloat() }
        } else {
            null
        }

        val selectedAngle = peakSample?.second ?: nearestSample?.second ?: point.angle
        point.copy(angle = selectedAngle)
    }
}
