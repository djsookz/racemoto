package com.revix.app.drag

import java.util.concurrent.CopyOnWriteArrayList

data class DragRunVideoHudState(
    val sessionActive: Boolean = false,
    val sessionId: Long = 0L,
    val attemptId: Long = 0L,
    val measurementMode: MeasurementMode = MeasurementMode.ALL,
    val speedKmh: Float = 0f,
    val time0to100Ns: Long = -1L,
    val time0to200Ns: Long = -1L,
    val time100to200Ns: Long = -1L,
    val time0to402Ns: Long = -1L,
    val officiallyStarted: Boolean = false,
    val attemptSaved: Boolean = false,
    val statusText: String = "",
    val accelG: Float = 0f,
    val peakAccelG: Float = 0f,
    val chronoNs: Long = -1L
)

/**
 * Live HUD + per-attempt video attach between [DragRunPageActivity] and [DragRunVideoActivity].
 * Timing stays on the run page; the video page only observes and records.
 */
object DragRunVideoBridge {

    @Volatile
    var latest: DragRunVideoHudState = DragRunVideoHudState()
        private set

    private val recordingIdleLock = Object()
    @Volatile
    private var recordingBusy = false

    private val hudListeners = CopyOnWriteArrayList<(DragRunVideoHudState) -> Unit>()
    private val videoListeners = CopyOnWriteArrayList<(Long, Long, String, Long) -> Unit>()

    fun markRecordingBusy() {
        synchronized(recordingIdleLock) {
            recordingBusy = true
        }
    }

    fun markRecordingIdle() {
        synchronized(recordingIdleLock) {
            recordingBusy = false
            recordingIdleLock.notifyAll()
        }
    }

    fun awaitRecordingIdle(timeoutMs: Long) {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(0L)
        synchronized(recordingIdleLock) {
            while (recordingBusy) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) return
                runCatching { recordingIdleLock.wait(remaining) }
            }
        }
    }

    fun publish(state: DragRunVideoHudState) {
        latest = state
        hudListeners.forEach { listener ->
            runCatching { listener(state) }
        }
    }

    fun addHudListener(listener: (DragRunVideoHudState) -> Unit) {
        hudListeners.add(listener)
        listener(latest)
    }

    fun removeHudListener(listener: (DragRunVideoHudState) -> Unit) {
        hudListeners.remove(listener)
    }

    fun attachVideo(sessionId: Long, attemptId: Long, path: String, t0OffsetMs: Long = 0L) {
        if (attemptId <= 0L || path.isBlank()) return
        videoListeners.forEach { listener ->
            runCatching { listener(sessionId, attemptId, path, t0OffsetMs) }
        }
    }

    fun addVideoListener(listener: (Long, Long, String, Long) -> Unit) {
        videoListeners.add(listener)
    }

    fun removeVideoListener(listener: (Long, Long, String, Long) -> Unit) {
        videoListeners.remove(listener)
    }
}
