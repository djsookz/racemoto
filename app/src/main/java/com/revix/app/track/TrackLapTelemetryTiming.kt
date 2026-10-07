package com.revix.app.track

import com.revix.app.LapData

/**
 * Resolves lap sensor sample time relative to [LapData.startTime] in milliseconds.
 * Handles legacy rows where boot-time sensor stamps were stored instead of wall clock.
 */
fun resolveLapTelemetryRelativeMs(
    lapData: LapData,
    index: Int,
    sampleCount: Int
): Long {
    if (sampleCount <= 0 || index !in 0 until sampleCount) return 0L

    val timestamp = lapData.timestamps.getOrNull(index) ?: return 0L
    val baseTime = lapData.startTime.takeIf { it > 0L }
    if (baseTime != null) {
        val wallDelta = timestamp - baseTime
        val lastWallDelta = (lapData.timestamps.lastOrNull() ?: timestamp) - baseTime
        if (wallDelta >= 0L && lastWallDelta > 0L) {
            return wallDelta
        }
    }

    val lapDurationMs = when {
        lapData.endTime > lapData.startTime -> lapData.endTime - lapData.startTime
        lapData.routePoints.size >= 2 -> lapData.routePoints.last().timestamp.coerceAtLeast(1L)
        else -> (sampleCount * 20L).coerceAtLeast(1L)
    }
    return if (sampleCount <= 1) 0L else (lapDurationMs * index) / (sampleCount - 1)
}
