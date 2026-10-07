package com.example.clinometer.track.session

class TrackLapTimingEngine(
    val minLapTimeMs: Long = 10_000L
) {
    fun lapElapsedMs(
        lapStartTimeMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        if (lapStartTimeMs <= 0L) return 0L
        return (nowMs - lapStartTimeMs).coerceAtLeast(0L)
    }

    fun canCompleteLap(
        lapStartTimeMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        return lapElapsedMs(lapStartTimeMs, nowMs) >= minLapTimeMs
    }
}
