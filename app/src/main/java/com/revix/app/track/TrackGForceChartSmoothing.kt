package com.revix.app.track

import kotlin.math.abs

/**
 * Drag-style G chart smoothing (same params as [com.revix.app.drag.DragSessionDetailsActivity]):
 * rate-limit → median → EMA. Used so Track longitudinal/lateral curves match Drag accuracy.
 */
object TrackGForceChartSmoothing {
    const val DISPLAY_EMA_ALPHA = 0.22f
    const val DISPLAY_MAX_DELTA_G_PER_SEC = 7f
    const val DISPLAY_CLAMP_G = 3.5f
    const val NEAR_ZERO_DEADBAND_G = 0.03f

    /** Chart-only G: a little extra rounding, then put real peaks back. */
    private const val G_CHART_EMA_ALPHA = 0.14f
    private const val G_CHART_ZERO_PHASE_ALPHA = 0.22f
    private const val G_CHART_MAX_DELTA_G_PER_SEC = 5.8f
    private const val G_CHART_MEDIAN_PASSES = 2
    private const val G_CHART_PEAK_RESTORE_DELTA_G = 0.16f

    /**
     * Chart-only smoothing for RaceBox ~25 Hz Doppler speed.
     * Stronger than before: kills sawtooth while keeping real accel/brake shape.
     */
    const val SPEED_CHART_EMA_ALPHA = 0.18f
    const val SPEED_CHART_MAX_DELTA_KMH_PER_SEC = 28f
    private const val SPEED_CHART_MEDIAN_PASSES = 2
    private const val SPEED_CHART_NEAR_ZERO_KMH = 0.6f

    /** ~5 Hz low-pass cutoff — same idea as ForegroundService drag longitudinal filter. */
    const val CAPTURE_LOWPASS_CUTOFF_HZ = 5.0f
    private const val FILTER_MIN_DT_SEC = 0.004f
    private const val FILTER_MAX_DT_SEC = 0.08f

    /**
     * Smooth high-rate GNSS speed for chart display only.
     * Rate-limit → median → forward+backward EMA → round to whole units.
     * Input zeros (already sanitized stops) are locked through so the line matches the scrubber.
     */
    fun smoothSpeedSeriesForChart(
        speeds: List<Float>,
        timestampsMs: List<Long>
    ): List<Float> {
        if (speeds.isEmpty()) return speeds
        val limit = minOf(speeds.size, timestampsMs.size)
        if (limit < 3) {
            return speeds.take(limit).map { kotlin.math.round(it.coerceAtLeast(0f)) }
        }

        val source = speeds.take(limit).map { it.coerceAtLeast(0f) }
        val forward = smoothSeriesForChart(
            values = source,
            timestampsMs = timestampsMs.take(limit),
            maxDeltaPerSecond = SPEED_CHART_MAX_DELTA_KMH_PER_SEC,
            emaAlpha = SPEED_CHART_EMA_ALPHA,
            medianPasses = SPEED_CHART_MEDIAN_PASSES
        )
        // Zero-phase second pass: smooth reverse then reverse back — less lag, fewer teeth.
        val backward = exponentialSmoothingPass(forward.asReversed(), SPEED_CHART_EMA_ALPHA)
            .asReversed()
        return backward.mapIndexed { index, value ->
            // Keep true stops from sanitizer; do not let EMA lift a parked segment above 0.
            if (source[index] <= 0f) {
                0f
            } else {
                val cleaned = if (abs(value) < SPEED_CHART_NEAR_ZERO_KMH) 0f else value
                kotlin.math.round(cleaned.coerceAtLeast(0f))
            }
        }
    }

    fun smoothGSeriesForChart(
        values: List<Float>,
        timestampsMs: List<Long>
    ): List<Float> {
        val limit = minOf(values.size, timestampsMs.size)
        if (limit < 3) return values.take(limit)
        val source = values.take(limit).map { value ->
            value.coerceIn(-DISPLAY_CLAMP_G, DISPLAY_CLAMP_G)
        }
        val forward = smoothSeriesForChart(
            values = source,
            timestampsMs = timestampsMs.take(limit),
            maxDeltaPerSecond = G_CHART_MAX_DELTA_G_PER_SEC,
            emaAlpha = G_CHART_EMA_ALPHA,
            medianPasses = G_CHART_MEDIAN_PASSES
        )
        val zeroPhase = exponentialSmoothingPass(forward.asReversed(), G_CHART_ZERO_PHASE_ALPHA)
            .asReversed()
        return restoreSignificantExtrema(source, zeroPhase, G_CHART_PEAK_RESTORE_DELTA_G)
    }

    fun smoothGSeriesForChartNs(
        values: List<Float>,
        timestampsNs: List<Long>
    ): List<Float> {
        val limit = minOf(values.size, timestampsNs.size)
        if (limit < 3) return values.take(limit)
        val timestampsMs = timestampsNs.take(limit).map { stampNs -> stampNs / 1_000_000L }
        return smoothGSeriesForChart(values.take(limit), timestampsMs)
    }

    fun smoothSeriesForChart(
        values: List<Float>,
        timestampsMs: List<Long>,
        maxDeltaPerSecond: Float = DISPLAY_MAX_DELTA_G_PER_SEC,
        emaAlpha: Float = DISPLAY_EMA_ALPHA,
        medianPasses: Int = 2
    ): List<Float> {
        if (values.size < 3 || values.size != timestampsMs.size) return values

        val bounded = ArrayList<Float>(values.size)
        var previous = values.first()
        bounded.add(previous)

        for (i in 1 until values.size) {
            val raw = values[i]
            val dtSec = ((timestampsMs[i] - timestampsMs[i - 1]).coerceAtLeast(20L)) / 1000f
            val maxDelta = maxDeltaPerSecond * dtSec
            val clipped = raw.coerceIn(previous - maxDelta, previous + maxDelta)
            bounded.add(clipped)
            previous = clipped
        }

        var filtered: List<Float> = bounded
        repeat(medianPasses.coerceAtLeast(0)) {
            filtered = medianFilterPass(filtered)
        }

        return exponentialSmoothingPass(filtered, emaAlpha).map { value ->
            if (abs(value) < NEAR_ZERO_DEADBAND_G) 0f else value
        }
    }

    fun applyLowPassMps2(
        sampleMps2: Float,
        sensorTimestampNs: Long,
        state: LowPassState
    ): Float {
        if (!state.initialized || state.timestampNs <= 0L) {
            state.valueMps2 = sampleMps2
            state.initialized = true
            state.timestampNs = sensorTimestampNs
            return state.valueMps2
        }

        val rawDtSec = ((sensorTimestampNs - state.timestampNs).toDouble() / 1_000_000_000.0)
            .coerceAtLeast(0.0)
        state.timestampNs = sensorTimestampNs

        val dtSec = rawDtSec.coerceIn(FILTER_MIN_DT_SEC.toDouble(), FILTER_MAX_DT_SEC.toDouble())
        val rc = 1.0 / (2.0 * Math.PI * CAPTURE_LOWPASS_CUTOFF_HZ.toDouble())
        val alpha = (dtSec / (rc + dtSec)).toFloat().coerceIn(0.01f, 1f)

        state.valueMps2 += alpha * (sampleMps2 - state.valueMps2)
        return state.valueMps2
    }

    private fun restoreSignificantExtrema(
        original: List<Float>,
        smoothed: List<Float>,
        minPeakDelta: Float
    ): List<Float> {
        if (original.size != smoothed.size || original.size < 3) return smoothed
        val out = smoothed.toMutableList()
        for (i in 1 until original.lastIndex) {
            val prev = original[i - 1]
            val curr = original[i]
            val next = original[i + 1]
            val isPeak = curr >= prev && curr >= next && (curr - maxOf(prev, next)) >= minPeakDelta
            val isTrough = curr <= prev && curr <= next && (minOf(prev, next) - curr) >= minPeakDelta
            if (isPeak && curr > out[i]) out[i] = curr
            if (isTrough && curr < out[i]) out[i] = curr
        }
        return out
    }

    private fun medianFilterPass(values: List<Float>): List<Float> {
        if (values.size < 3) return values
        val out = values.toMutableList()
        for (i in 1 until values.lastIndex) {
            val a = values[i - 1]
            val b = values[i]
            val c = values[i + 1]
            out[i] = when {
                (a <= b && b <= c) || (c <= b && b <= a) -> b
                (b <= a && a <= c) || (c <= a && a <= b) -> a
                else -> c
            }
        }
        return out
    }

    private fun exponentialSmoothingPass(values: List<Float>, alpha: Float): List<Float> {
        if (values.isEmpty()) return values
        val safeAlpha = alpha.coerceIn(0.01f, 1f)
        val out = ArrayList<Float>(values.size)
        var ema = values.first()
        out.add(ema)
        for (i in 1 until values.size) {
            ema = safeAlpha * values[i] + (1f - safeAlpha) * ema
            out.add(ema)
        }
        return out
    }

    class LowPassState {
        var valueMps2: Float = 0f
        var timestampNs: Long = 0L
        var initialized: Boolean = false

        fun reset() {
            valueMps2 = 0f
            timestampNs = 0L
            initialized = false
        }
    }
}
