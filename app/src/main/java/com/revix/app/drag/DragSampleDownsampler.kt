package com.revix.app

/**
 * Reduces high-rate drag telemetry to a chart-friendly size before persistence.
 * Keeps first/last samples and evenly spaced points in between.
 */
object DragSampleDownsampler {
    const val MAX_SPEED_POINTS = 600
    const val MAX_SENSOR_POINTS = 400
    const val MAX_LIVE_ACCEL_POINTS = 400

    fun <T> downsample(values: List<T>, times: List<Long>, maxPoints: Int): Pair<List<T>, List<Long>> {
        val limit = minOf(values.size, times.size)
        if (limit <= 0) return emptyList<T>() to emptyList()
        if (limit <= maxPoints) {
            return values.take(limit) to times.take(limit)
        }

        val outValues = ArrayList<T>(maxPoints)
        val outTimes = ArrayList<Long>(maxPoints)
        val lastIndex = limit - 1
        for (i in 0 until maxPoints) {
            val index = if (i == maxPoints - 1) {
                lastIndex
            } else {
                ((i.toLong() * lastIndex) / (maxPoints - 1)).toInt()
            }
            outValues.add(values[index])
            outTimes.add(times[index])
        }
        return outValues to outTimes
    }

    fun downsampleAttempt(attempt: DragAttempt): DragAttempt {
        val (gSamples, gTimes) = downsample(attempt.gSamples, attempt.timeStamps, MAX_SENSOR_POINTS)
        val (gpsSamples, gpsTimes) = downsample(
            attempt.gpsAccelSamples,
            attempt.gpsTimeStamps,
            MAX_SENSOR_POINTS
        )
        val (speedSamples, speedTimes) = downsample(
            attempt.speedSamples,
            attempt.speedTimeStamps,
            MAX_SPEED_POINTS
        )
        val (longSamples, longTimes) = downsample(
            attempt.longitudinalAccelSamples,
            attempt.longitudinalAccelTimeStamps,
            MAX_SENSOR_POINTS
        )
        val (liveSamples, liveTimes) = downsample(
            attempt.liveAccelDisplaySamples,
            attempt.liveAccelDisplayTimeStamps,
            MAX_LIVE_ACCEL_POINTS
        )
        return attempt.copy(
            gSamples = gSamples,
            timeStamps = gTimes,
            gpsAccelSamples = gpsSamples,
            gpsTimeStamps = gpsTimes,
            speedSamples = speedSamples,
            speedTimeStamps = speedTimes,
            longitudinalAccelSamples = longSamples,
            longitudinalAccelTimeStamps = longTimes,
            liveAccelDisplaySamples = liveSamples,
            liveAccelDisplayTimeStamps = liveTimes
        )
    }
}
