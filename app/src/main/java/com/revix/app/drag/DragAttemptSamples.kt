package com.revix.app

/**
 * Telemetry payload stored beside the light drag session index.
 * Kept as a top-level type so R8/Gson can deserialize it reliably in release.
 */
data class DragAttemptSamples(
    val gSamples: List<Float> = emptyList(),
    val timeStamps: List<Long> = emptyList(),
    val gpsAccelSamples: List<Float> = emptyList(),
    val gpsTimeStamps: List<Long> = emptyList(),
    val speedSamples: List<Float> = emptyList(),
    val speedTimeStamps: List<Long> = emptyList(),
    val longitudinalAccelSamples: List<Float> = emptyList(),
    val longitudinalAccelTimeStamps: List<Long> = emptyList(),
    val liveAccelDisplaySamples: List<Float> = emptyList(),
    val liveAccelDisplayTimeStamps: List<Long> = emptyList()
)
