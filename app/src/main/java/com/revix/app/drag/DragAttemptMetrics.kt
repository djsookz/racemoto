package com.revix.app.drag

import com.revix.app.DragAttempt
import com.revix.app.video.VideoLaunchCountdown

object DragAttemptMetrics {
    data class FinishedVideoWindow(
        val startMs: Long,
        val endMs: Long,
        val t0InClipMs: Long
    )
    fun hasSuccessful402(attempt: DragAttempt): Boolean {
        return attempt.time0to402 > 0L || attempt.distance402mTimeNs > 0L
    }

    fun resolveTrapSpeedKmh(attempt: DragAttempt): Float? {
        if (!hasSuccessful402(attempt)) return null
        return attempt.distance402mSpeedKmh.takeIf { it > 0f }
            ?: attempt.maxSpeed.takeIf { it > 0f }
    }

    fun hasCompletedMeasurement(attempt: DragAttempt): Boolean {
        return attempt.time0to100 > 0L ||
            attempt.time0to200 > 0L ||
            attempt.time100to200 > 0L ||
            attempt.time0to402 > 0L
    }

    fun resolve100To200SplitTimeNs(attempt: DragAttempt): Long? {
        attempt.time100to200.takeIf { it > 0L }?.let { return it }

        val time0to100 = attempt.time0to100.takeIf { it > 0L } ?: return null
        val time0to200 = attempt.time0to200.takeIf { it > 0L } ?: return null
        return (time0to200 - time0to100).takeIf { it > 0L }
    }

    fun lastSuccessfulMetricElapsedNs(
        mode: MeasurementMode,
        time0to100Ns: Long,
        time0to200Ns: Long,
        time100to200Ns: Long,
        time0to402Ns: Long
    ): Long {
        val times = ArrayList<Long>(4)
        if (time0to100Ns > 0L) times += time0to100Ns
        if (time0to200Ns > 0L) times += time0to200Ns
        if (time0to402Ns > 0L) times += time0to402Ns
        if (mode == MeasurementMode.HUNDRED_TO_200 && time100to200Ns > 0L) {
            times += time100to200Ns
        }
        return times.maxOrNull() ?: -1L
    }

    fun finishedVideoWindow(attempt: DragAttempt, mode: MeasurementMode): FinishedVideoWindow? {
        val t0Ms = attempt.videoT0OffsetMs
        if (t0Ms <= 0L) return null
        val lastNs = lastSuccessfulMetricElapsedNs(
            mode = mode,
            time0to100Ns = attempt.time0to100,
            time0to200Ns = attempt.time0to200,
            time100to200Ns = attempt.time100to200,
            time0to402Ns = attempt.time0to402
        )
        val lastMs = if (lastNs > 0L) lastNs / 1_000_000L else 0L
        val startMs = (t0Ms - VideoLaunchCountdown.PRE_ROLL_MS).coerceAtLeast(0L)
        val endMs = (t0Ms + lastMs + VideoLaunchCountdown.POST_ROLL_MS).coerceAtLeast(startMs + 200L)
        return FinishedVideoWindow(
            startMs = startMs,
            endMs = endMs,
            t0InClipMs = t0Ms - startMs
        )
    }
}
