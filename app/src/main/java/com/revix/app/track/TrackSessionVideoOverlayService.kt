package com.revix.app.track

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.revix.app.R
import com.revix.app.TrackSessionDetailActivity
import com.revix.app.TrackSessionVideoClip
import com.revix.app.TrackSessionVideoClipsCodec
import com.revix.app.TrackSessionVideoExport
import com.revix.app.TrackSessionVideoKind
import com.revix.app.TrackSessionVideoOverlayExporter
import com.revix.app.TrackManager
import com.revix.app.TrackSessionIdUtils
import com.revix.app.tracking.CustomTrackStorage
import java.io.File

class TrackSessionVideoOverlayService : Service() {

    private var exporter: TrackSessionVideoOverlayExporter? = null
    private var currentJob: Job? = null
    private var isExporting = false
    private val jobQueue = ArrayDeque<Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val job = jobFromIntent(intent)
        if (job == null) {
            if (!isExporting) stopSelfSafely()
            return START_NOT_STICKY
        }

        if (!isDuplicate(job)) {
            jobQueue.addLast(job)
        }

        if (isExporting) {
            return START_STICKY
        }

        val notification = buildProgressNotification(job, progressPercent = 0, indeterminate = true)
        if (!startAsForeground(notification)) {
            stopSelf()
            return START_NOT_STICKY
        }
        processNextJob()
        return START_STICKY
    }

    override fun onDestroy() {
        exporter?.cancel()
        exporter = null
        super.onDestroy()
    }

    private fun processNextJob() {
        val job = jobQueue.removeFirstOrNull()
        if (job == null) {
            currentJob = null
            isExporting = false
            stopSelfSafely()
            return
        }
        currentJob = job
        isExporting = true
        writeFailed(job, false)
        writeProgress(job, 0)
        broadcast(job, STATE_PROCESSING, 0)
        updateForegroundNotification(job, 0, indeterminate = true)
        when (job.kind) {
            JobKind.SESSION -> exportMissingClips(job)
            JobKind.LAPS -> exportLapsClip(job)
            JobKind.HUD -> exportHudClip(job)
        }
    }

    private fun exportMissingClips(job: Job) {
        val sessionId = job.sessionId
        val outingNumber = job.outingNumber
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val clips = TrackSessionVideoClipsCodec.loadFromPrefs(prefs, sessionId, outingNumber).toMutableList()
        val sources = clips.filter { clip ->
            clip.kind == TrackSessionVideoKind.RECORDING &&
                clip.hasPlayableContent() &&
                (job.force || clips.none { session ->
                    session.kind == TrackSessionVideoKind.SESSION &&
                        session.sessionElapsedAtStartMs == clip.sessionElapsedAtStartMs
                })
        }
        if (sources.isEmpty()) {
            writePending(job, false)
            writeFailed(job, false)
            writeForce(job, false)
            writeProgress(job, 100)
            notifyFinished(job, success = true)
            broadcast(job, STATE_READY, 100)
            processNextJob()
            return
        }
        exportSessionClip(
            job = job,
            clips = clips,
            remaining = ArrayDeque(sources),
            total = sources.size
        )
    }

    private fun exportSessionClip(
        job: Job,
        clips: MutableList<TrackSessionVideoClip>,
        remaining: ArrayDeque<TrackSessionVideoClip>,
        total: Int
    ) {
        val source = remaining.removeFirstOrNull()
        if (source == null) {
            writePending(job, false)
            writeFailed(job, false)
            writeForce(job, false)
            writeProgress(job, 100)
            notifyFinished(job, success = true)
            broadcast(job, STATE_READY, 100)
            processNextJob()
            return
        }

        val sourceUri = playbackUriForClip(source)
        val overlayModel = TrackSessionVideoOverlayModels.build(
            context = this,
            sessionId = job.sessionId,
            outingNumber = job.outingNumber,
            videoStartSessionElapsedMs = source.sessionElapsedAtStartMs
        )
        if (sourceUri == null || overlayModel == null) {
            failCurrentJob(job)
            return
        }

        val completed = total - remaining.size - 1
        val outputFile = File(cacheDir, "track_session_overlay_${System.currentTimeMillis()}.mp4")
        val activeExporter = TrackSessionVideoOverlayExporter(this)
        exporter = activeExporter
        activeExporter.export(
            request = TrackSessionVideoOverlayExporter.ExportRequest(
                inputUri = sourceUri,
                outputFile = outputFile,
                trimStartMs = source.sourceTrimStartMs,
                trimEndMs = 0L,
                overlayModel = overlayModel
            ),
            onSuccess = { renderedFile ->
                exporter = null
                val saved = persistRenderedSessionClip(
                    job = job,
                    clips = clips,
                    source = source,
                    renderedFile = renderedFile,
                    partIndex = if (total > 1) completed + 1 else null
                )
                renderedFile.delete()
                if (!saved) {
                    failCurrentJob(job)
                    return@export
                }
                exportSessionClip(job, clips, remaining, total)
            },
            onError = { error ->
                Log.e(TAG, "Background telemetry overlay failed", error)
                exporter = null
                outputFile.delete()
                failCurrentJob(job)
            },
            onProgress = { percent ->
                val overall = ((completed * 100) + percent) / total.coerceAtLeast(1)
                writeProgress(job, overall)
                updateForegroundNotification(job, overall, indeterminate = false)
                broadcast(job, STATE_PROCESSING, overall)
            }
        )
    }

    private fun exportLapsClip(job: Job) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val clips = TrackSessionVideoClipsCodec.loadFromPrefs(prefs, job.sessionId, job.outingNumber).toMutableList()
        val source = resolveRecordingSource(clips)
        val sourceUri = source?.let(::playbackUriForClip)
        val overlayModel = source?.let {
            val extraClipMs = (job.trimStartMs - it.sourceTrimStartMs).coerceAtLeast(0L)
            TrackSessionVideoOverlayModels.build(
                context = this,
                sessionId = job.sessionId,
                outingNumber = job.outingNumber,
                videoStartSessionElapsedMs = it.sessionElapsedAtStartMs + extraClipMs
            )
        }
        if (source == null || sourceUri == null || overlayModel == null) {
            failCurrentJob(job)
            return
        }

        val outputFile = File(cacheDir, "track_laps_overlay_${System.currentTimeMillis()}.mp4")
        val activeExporter = TrackSessionVideoOverlayExporter(this)
        exporter = activeExporter
        activeExporter.export(
            request = TrackSessionVideoOverlayExporter.ExportRequest(
                inputUri = sourceUri,
                outputFile = outputFile,
                trimStartMs = job.trimStartMs,
                trimEndMs = job.trimEndMs,
                overlayModel = overlayModel
            ),
            onSuccess = { renderedFile ->
                exporter = null
                val saved = persistManualClip(
                    job = job,
                    clips = clips,
                    source = source,
                    renderedFile = renderedFile,
                    clipKind = TrackSessionVideoKind.LAPS,
                    exportKind = TrackSessionVideoExport.Kind.LAPS
                )
                renderedFile.delete()
                if (!saved) {
                    failCurrentJob(job)
                    return@export
                }
                finishManualJob(job)
            },
            onError = { error ->
                Log.e(TAG, "Background laps overlay failed", error)
                exporter = null
                outputFile.delete()
                failCurrentJob(job)
            },
            onProgress = { percent ->
                writeProgress(job, percent)
                updateForegroundNotification(job, percent, indeterminate = false)
                broadcast(job, STATE_PROCESSING, percent)
            }
        )
    }

    private fun exportHudClip(job: Job) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val clips = TrackSessionVideoClipsCodec.loadFromPrefs(prefs, job.sessionId, job.outingNumber).toMutableList()
        val source = resolveRecordingSource(clips)
        val sourceUri = source?.let(::playbackUriForClip)
        val overlayModel = source?.let {
            TrackSessionVideoOverlayModels.build(
                context = this,
                sessionId = job.sessionId,
                outingNumber = job.outingNumber,
                videoStartSessionElapsedMs = it.sessionElapsedAtStartMs
            )
        }
        if (source == null || sourceUri == null || overlayModel == null) {
            failCurrentJob(job)
            return
        }

        val stamp = System.currentTimeMillis()
        val outputFile = File(cacheDir, "track_hud_overlay_${stamp}.mp4")
        val alphaFile = File(cacheDir, "track_hud_overlay_${stamp}_alpha.mp4")
        val activeExporter = TrackSessionVideoOverlayExporter(this)
        exporter = activeExporter
        activeExporter.exportHudOnly(
            request = TrackSessionVideoOverlayExporter.HudOnlyExportRequest(
                inputUri = sourceUri,
                outputFile = outputFile,
                alphaOutputFile = alphaFile,
                trimStartMs = source.sourceTrimStartMs,
                overlayModel = overlayModel
            ),
            onSuccess = { renderedFile ->
                exporter = null
                val saved = persistManualClip(
                    job = job,
                    clips = clips,
                    source = source,
                    renderedFile = renderedFile,
                    clipKind = TrackSessionVideoKind.HUD,
                    exportKind = TrackSessionVideoExport.Kind.HUD
                )
                if (alphaFile.exists()) {
                    persistHudAlphaToLibrary(job, alphaFile)
                    alphaFile.delete()
                }
                renderedFile.delete()
                if (!saved) {
                    failCurrentJob(job)
                    return@exportHudOnly
                }
                finishManualJob(job)
            },
            onError = { error ->
                Log.e(TAG, "Background HUD overlay failed", error)
                exporter = null
                outputFile.delete()
                alphaFile.delete()
                failCurrentJob(job)
            },
            onProgress = { percent ->
                writeProgress(job, percent)
                updateForegroundNotification(job, percent, indeterminate = false)
                broadcast(job, STATE_PROCESSING, percent)
            }
        )
    }

    private fun persistRenderedSessionClip(
        job: Job,
        clips: MutableList<TrackSessionVideoClip>,
        source: TrackSessionVideoClip,
        renderedFile: File,
        partIndex: Int?
    ): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val sessionDate = prefs.getString("${job.sessionId}_outing_${job.outingNumber}_date", null)
        val sessionTime = prefs.getString("${job.sessionId}_outing_${job.outingNumber}_time", null)
        val exportFileName = TrackSessionVideoExport.buildDisplayName(
            context = this,
            trackName = resolveTrackName(job.sessionId),
            kind = TrackSessionVideoExport.Kind.SESSION,
            sessionDate = sessionDate,
            sessionTime = sessionTime,
            partIndex = partIndex
        )
        val savedUri = TrackSessionVideoExport.saveVideoToLibrary(this, renderedFile, exportFileName) ?: return false
        val newClip = TrackSessionVideoClip(
            uri = savedUri.toString(),
            path = "",
            camera = source.camera,
            sessionStartOffsetMs = source.sessionStartOffsetMs,
            sessionElapsedAtStartMs = source.sessionElapsedAtStartMs,
            overlayExported = true,
            kind = TrackSessionVideoKind.SESSION,
            sourceTrimStartMs = 0L
        )
        val existingIndex = clips.indexOfFirst { clip ->
            clip.kind == TrackSessionVideoKind.SESSION &&
                clip.sessionElapsedAtStartMs == source.sessionElapsedAtStartMs
        }
        if (existingIndex >= 0) {
            val previous = clips[existingIndex]
            clips[existingIndex] = newClip
            if (previous.uri.isNotBlank() && previous.uri != savedUri.toString()) {
                runCatching { contentResolver.delete(Uri.parse(previous.uri), null, null) }
            }
        } else {
            clips += newClip
        }
        prefs.edit().also { editor ->
            TrackSessionVideoClipsCodec.writeToEditor(editor, job.sessionId, job.outingNumber, clips)
            editor.putBoolean("${job.sessionId}_outing_${job.outingNumber}_telemetry_overlay_pending", true)
            editor.commit()
        }
        return true
    }

    private fun persistHudAlphaToLibrary(job: Job, renderedFile: File) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val sessionDate = prefs.getString("${job.sessionId}_outing_${job.outingNumber}_date", null)
        val sessionTime = prefs.getString("${job.sessionId}_outing_${job.outingNumber}_time", null)
        val exportFileName = TrackSessionVideoExport.buildDisplayName(
            context = this,
            trackName = resolveTrackName(job.sessionId),
            kind = TrackSessionVideoExport.Kind.HUD_ALPHA,
            sessionDate = sessionDate,
            sessionTime = sessionTime
        )
        TrackSessionVideoExport.saveHudOverlayVideoToLibrary(this, renderedFile, exportFileName)
    }

    private fun persistManualClip(
        job: Job,
        clips: MutableList<TrackSessionVideoClip>,
        source: TrackSessionVideoClip,
        renderedFile: File,
        clipKind: TrackSessionVideoKind,
        exportKind: TrackSessionVideoExport.Kind
    ): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val sessionDate = prefs.getString("${job.sessionId}_outing_${job.outingNumber}_date", null)
        val sessionTime = prefs.getString("${job.sessionId}_outing_${job.outingNumber}_time", null)
        val exportFileName = TrackSessionVideoExport.buildDisplayName(
            context = this,
            trackName = resolveTrackName(job.sessionId),
            kind = exportKind,
            sessionDate = sessionDate,
            sessionTime = sessionTime,
            lapFrom = job.lapFrom.takeIf { it > 0 },
            lapTo = job.lapTo.takeIf { it > 0 }
        )
        val savedUri = if (clipKind == TrackSessionVideoKind.HUD) {
            TrackSessionVideoExport.saveHudOverlayVideoToLibrary(this, renderedFile, exportFileName)
        } else {
            TrackSessionVideoExport.saveVideoToLibrary(this, renderedFile, exportFileName)
        } ?: return false

        val newClip = TrackSessionVideoClip(
            uri = savedUri.toString(),
            path = "",
            camera = source.camera,
            sessionStartOffsetMs = source.sessionStartOffsetMs,
            sessionElapsedAtStartMs = source.sessionElapsedAtStartMs,
            overlayExported = true,
            kind = clipKind,
            lapFrom = job.lapFrom,
            lapTo = job.lapTo
        )
        val existingIndex = clips.indexOfFirst { clip ->
            when (clipKind) {
                TrackSessionVideoKind.LAPS ->
                    clip.kind == clipKind && clip.lapFrom == job.lapFrom && clip.lapTo == job.lapTo
                else -> clip.kind == clipKind
            }
        }
        if (existingIndex >= 0) {
            val previous = clips[existingIndex]
            clips[existingIndex] = newClip
            if (previous.uri.isNotBlank() && previous.uri != savedUri.toString()) {
                runCatching { contentResolver.delete(Uri.parse(previous.uri), null, null) }
            }
        } else {
            clips += newClip
        }
        prefs.edit().also { editor ->
            TrackSessionVideoClipsCodec.writeToEditor(editor, job.sessionId, job.outingNumber, clips)
            if (clipKind == TrackSessionVideoKind.LAPS) {
                editor.putBoolean("${job.sessionId}_outing_${job.outingNumber}_laps_export_owned", true)
            }
            editor.commit()
        }
        return true
    }

    private fun finishManualJob(job: Job) {
        writePending(job, false)
        writeFailed(job, false)
        writeProgress(job, 100)
        notifyFinished(job, success = true)
        broadcast(job, STATE_READY, 100)
        processNextJob()
    }

    private fun failCurrentJob(job: Job) {
        writePending(job, job.kind == JobKind.SESSION && !job.force)
        writeFailed(job, true)
        notifyFinished(job, success = false)
        broadcast(job, STATE_FAILED, readProgress(job))
        processNextJob()
    }

    private fun resolveRecordingSource(clips: List<TrackSessionVideoClip>): TrackSessionVideoClip? {
        return clips.firstOrNull { clip ->
            clip.kind == TrackSessionVideoKind.RECORDING && clip.hasPlayableContent()
        }
    }

    private fun playbackUriForClip(clip: TrackSessionVideoClip): Uri? {
        clip.uri.takeIf { it.isNotBlank() }?.let { return Uri.parse(it) }
        val file = clip.path.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() } ?: return null
        return Uri.fromFile(file)
    }

    private fun resolveTrackName(sessionId: String): String {
        val trackId = TrackSessionIdUtils.extractTrackIdFromSessionId(this, sessionId)
        TrackManager(this).getTrackById(trackId)?.name?.let { return it }
        if (trackId.startsWith("custom_")) {
            CustomTrackStorage.loadCustomTrack(this, trackId)?.name?.let { return it }
        }
        return getString(R.string.track_session_video_title)
    }

    private fun startAsForeground(notification: Notification): Boolean {
        return try {
            when {
                Build.VERSION.SDK_INT >= 35 -> startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
                else -> startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start overlay foreground service", error)
            false
        }
    }

    private fun updateForegroundNotification(
        job: Job,
        progressPercent: Int,
        indeterminate: Boolean
    ) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(
            NOTIFICATION_ID,
            buildProgressNotification(job, progressPercent, indeterminate)
        )
    }

    private fun buildProgressNotification(
        job: Job,
        progressPercent: Int,
        indeterminate: Boolean
    ): Notification {
        val text = if (indeterminate || progressPercent <= 0) {
            getString(job.kind.notificationTextRes)
        } else {
            getString(job.kind.notificationProgressRes, progressPercent)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(job.kind.notificationTitleRes))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, progressPercent.coerceIn(0, 100), indeterminate)
            .setContentIntent(detailPendingIntent(job.sessionId, job.outingNumber))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun notifyFinished(job: Job, success: Boolean) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(
                getString(if (success) job.kind.readyTitleRes else job.kind.failedTitleRes)
            )
            .setContentText(
                getString(if (success) job.kind.readyTextRes else job.kind.failedTextRes)
            )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(detailPendingIntent(job.sessionId, job.outingNumber))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(finishedNotificationId(job.kind), notification)
    }

    private fun detailPendingIntent(sessionId: String, outingNumber: Int): PendingIntent {
        val intent = Intent(this, TrackSessionDetailActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("trackId", sessionId)
            putExtra("outingNumber", outingNumber)
        }
        return PendingIntent.getActivity(
            this,
            (sessionId + outingNumber).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun broadcast(job: Job, state: String, progressPercent: Int) {
        sendBroadcast(
            Intent(ACTION_UPDATE).setPackage(packageName).apply {
                putExtra(EXTRA_SESSION_ID, job.sessionId)
                putExtra(EXTRA_OUTING_NUMBER, job.outingNumber)
                putExtra(EXTRA_KIND, job.kind.extraValue)
                putExtra(EXTRA_STATE, state)
                putExtra(EXTRA_PROGRESS, progressPercent)
            }
        )
    }

    private fun writePending(job: Job, pending: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(pendingKey(job.sessionId, job.outingNumber, job.kind), pending)
            .commit()
    }

    private fun writeFailed(job: Job, failed: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(failedKey(job.sessionId, job.outingNumber, job.kind), failed)
            .commit()
    }

    private fun writeForce(job: Job, force: Boolean) {
        if (job.kind != JobKind.SESSION) return
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putBoolean(forceKey(job.sessionId, job.outingNumber), force)
            .commit()
    }

    private fun writeProgress(job: Job, progressPercent: Int) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putInt(progressKey(job.sessionId, job.outingNumber, job.kind), progressPercent.coerceIn(0, 100))
            .commit()
    }

    private fun readProgress(job: Job): Int {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getInt(progressKey(job.sessionId, job.outingNumber, job.kind), 0)
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.track_session_video_overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun isDuplicate(job: Job): Boolean {
        if (currentJob?.matches(job) == true) return true
        return jobQueue.any { queued -> queued.matches(job) }
    }

    private data class Job(
        val kind: JobKind,
        val sessionId: String,
        val outingNumber: Int,
        val trimStartMs: Long = 0L,
        val trimEndMs: Long = 0L,
        val lapFrom: Int = 0,
        val lapTo: Int = 0,
        val force: Boolean = false
    ) {
        fun matches(other: Job): Boolean {
            return kind == other.kind &&
                sessionId == other.sessionId &&
                outingNumber == other.outingNumber
        }
    }

    private enum class JobKind(
        val extraValue: String,
        val notificationTitleRes: Int,
        val notificationTextRes: Int,
        val notificationProgressRes: Int,
        val readyTitleRes: Int,
        val readyTextRes: Int,
        val readyNotificationId: Int,
        val failedTitleRes: Int,
        val failedTextRes: Int
    ) {
        SESSION(
            extraValue = KIND_SESSION,
            notificationTitleRes = R.string.track_session_video_overlay_notification_title,
            notificationTextRes = R.string.track_session_video_overlay_notification_text,
            notificationProgressRes = R.string.track_session_video_overlay_notification_progress,
            readyTitleRes = R.string.track_session_video_overlay_ready_title,
            readyTextRes = R.string.track_session_video_overlay_ready_text,
            readyNotificationId = FINISHED_NOTIFICATION_ID_SESSION,
            failedTitleRes = R.string.track_session_video_overlay_failed_title,
            failedTextRes = R.string.track_session_video_overlay_failed_text
        ),
        LAPS(
            extraValue = KIND_LAPS,
            notificationTitleRes = R.string.track_session_video_overlay_notification_title_laps,
            notificationTextRes = R.string.track_session_video_overlay_notification_text_laps,
            notificationProgressRes = R.string.track_session_video_overlay_notification_progress_laps,
            readyTitleRes = R.string.track_session_video_overlay_laps_ready_title,
            readyTextRes = R.string.track_session_video_overlay_laps_ready_text,
            readyNotificationId = FINISHED_NOTIFICATION_ID_LAPS,
            failedTitleRes = R.string.track_session_video_overlay_laps_failed_title,
            failedTextRes = R.string.track_session_video_overlay_laps_failed_text
        ),
        HUD(
            extraValue = KIND_HUD,
            notificationTitleRes = R.string.track_session_video_overlay_notification_title_hud,
            notificationTextRes = R.string.track_session_video_overlay_notification_text_hud,
            notificationProgressRes = R.string.track_session_video_overlay_notification_progress_hud,
            readyTitleRes = R.string.track_session_video_overlay_hud_ready_title,
            readyTextRes = R.string.track_session_video_overlay_hud_ready_text,
            readyNotificationId = FINISHED_NOTIFICATION_ID_HUD,
            failedTitleRes = R.string.track_session_video_overlay_hud_failed_title,
            failedTextRes = R.string.track_session_video_overlay_hud_failed_text
        );

        companion object {
            fun fromExtra(raw: String?): JobKind = when (raw) {
                KIND_LAPS -> LAPS
                KIND_HUD -> HUD
                else -> SESSION
            }
        }
    }

    companion object {
        const val ACTION_UPDATE = "com.revix.app.TRACK_VIDEO_OVERLAY_UPDATE"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_OUTING_NUMBER = "outing_number"
        const val EXTRA_KIND = "kind"
        const val EXTRA_STATE = "state"
        const val EXTRA_PROGRESS = "progress"
        const val KIND_SESSION = "session"
        const val KIND_LAPS = "laps"
        const val KIND_HUD = "hud"
        const val STATE_PROCESSING = "processing"
        const val STATE_READY = "ready"
        const val STATE_FAILED = "failed"

        private const val TAG = "TrackVideoOverlay"
        private const val PREFS_NAME = "track_outings"
        private const val CHANNEL_ID = "revix_track_video_overlay"
        private const val NOTIFICATION_ID = 71201
        private const val FINISHED_NOTIFICATION_ID_SESSION = 71202
        private const val FINISHED_NOTIFICATION_ID_LAPS = 71203
        private const val FINISHED_NOTIFICATION_ID_HUD = 71204
        private const val EXTRA_TRIM_START = "trim_start_ms"
        private const val EXTRA_TRIM_END = "trim_end_ms"
        private const val EXTRA_LAP_FROM = "lap_from"
        private const val EXTRA_LAP_TO = "lap_to"
        private const val EXTRA_FORCE = "force"

        fun start(context: Context, sessionId: String, outingNumber: Int, force: Boolean = false) {
            if (sessionId.isBlank() || outingNumber <= 0) return
            if (force) {
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putBoolean(pendingKey(sessionId, outingNumber, JobKind.SESSION), true)
                    .putBoolean(failedKey(sessionId, outingNumber, JobKind.SESSION), false)
                    .putInt(progressKey(sessionId, outingNumber, JobKind.SESSION), 0)
                    .putBoolean(forceKey(sessionId, outingNumber), true)
                    .commit()
            }
            val intent = Intent(context, TrackSessionVideoOverlayService::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_OUTING_NUMBER, outingNumber)
                putExtra(EXTRA_KIND, KIND_SESSION)
                putExtra(EXTRA_FORCE, force)
            }
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun startLaps(
            context: Context,
            sessionId: String,
            outingNumber: Int,
            trimStartMs: Long,
            trimEndMs: Long,
            lapFrom: Int,
            lapTo: Int
        ) {
            if (sessionId.isBlank() || outingNumber <= 0) return
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(pendingKey(sessionId, outingNumber, JobKind.LAPS), true)
                .putBoolean(failedKey(sessionId, outingNumber, JobKind.LAPS), false)
                .putInt(progressKey(sessionId, outingNumber, JobKind.LAPS), 0)
                .putLong(lapsTrimStartKey(sessionId, outingNumber), trimStartMs)
                .putLong(lapsTrimEndKey(sessionId, outingNumber), trimEndMs)
                .putInt(lapsFromKey(sessionId, outingNumber), lapFrom)
                .putInt(lapsToKey(sessionId, outingNumber), lapTo)
                .commit()
            val intent = Intent(context, TrackSessionVideoOverlayService::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_OUTING_NUMBER, outingNumber)
                putExtra(EXTRA_KIND, KIND_LAPS)
                putExtra(EXTRA_TRIM_START, trimStartMs)
                putExtra(EXTRA_TRIM_END, trimEndMs)
                putExtra(EXTRA_LAP_FROM, lapFrom)
                putExtra(EXTRA_LAP_TO, lapTo)
            }
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun startHud(context: Context, sessionId: String, outingNumber: Int) {
            if (sessionId.isBlank() || outingNumber <= 0) return
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(pendingKey(sessionId, outingNumber, JobKind.HUD), true)
                .putBoolean(failedKey(sessionId, outingNumber, JobKind.HUD), false)
                .putInt(progressKey(sessionId, outingNumber, JobKind.HUD), 0)
                .commit()
            val intent = Intent(context, TrackSessionVideoOverlayService::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_OUTING_NUMBER, outingNumber)
                putExtra(EXTRA_KIND, KIND_HUD)
            }
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun isPending(prefs: android.content.SharedPreferences, sessionId: String, outingNumber: Int): Boolean {
            return prefs.getBoolean(pendingKey(sessionId, outingNumber, JobKind.SESSION), false)
        }

        fun isFailed(prefs: android.content.SharedPreferences, sessionId: String, outingNumber: Int): Boolean {
            return prefs.getBoolean(failedKey(sessionId, outingNumber, JobKind.SESSION), false)
        }

        fun isForce(prefs: android.content.SharedPreferences, sessionId: String, outingNumber: Int): Boolean {
            return prefs.getBoolean(forceKey(sessionId, outingNumber), false)
        }

        fun progress(prefs: android.content.SharedPreferences, sessionId: String, outingNumber: Int): Int {
            return prefs.getInt(progressKey(sessionId, outingNumber, JobKind.SESSION), 0)
        }

        fun isLapsPending(prefs: android.content.SharedPreferences, sessionId: String, outingNumber: Int): Boolean {
            return prefs.getBoolean(pendingKey(sessionId, outingNumber, JobKind.LAPS), false)
        }

        fun isLapsFailed(prefs: android.content.SharedPreferences, sessionId: String, outingNumber: Int): Boolean {
            return prefs.getBoolean(failedKey(sessionId, outingNumber, JobKind.LAPS), false)
        }

        fun lapsProgress(prefs: android.content.SharedPreferences, sessionId: String, outingNumber: Int): Int {
            return prefs.getInt(progressKey(sessionId, outingNumber, JobKind.LAPS), 0)
        }

        fun isHudPending(prefs: android.content.SharedPreferences, sessionId: String, outingNumber: Int): Boolean {
            return prefs.getBoolean(pendingKey(sessionId, outingNumber, JobKind.HUD), false)
        }

        fun isHudFailed(prefs: android.content.SharedPreferences, sessionId: String, outingNumber: Int): Boolean {
            return prefs.getBoolean(failedKey(sessionId, outingNumber, JobKind.HUD), false)
        }

        fun hudProgress(prefs: android.content.SharedPreferences, sessionId: String, outingNumber: Int): Int {
            return prefs.getInt(progressKey(sessionId, outingNumber, JobKind.HUD), 0)
        }

        fun storedLapsTrimStart(prefs: android.content.SharedPreferences, sessionId: String, outingNumber: Int): Long {
            return prefs.getLong(lapsTrimStartKey(sessionId, outingNumber), 0L)
        }

        fun storedLapsTrimEnd(prefs: android.content.SharedPreferences, sessionId: String, outingNumber: Int): Long {
            return prefs.getLong(lapsTrimEndKey(sessionId, outingNumber), 0L)
        }

        fun storedLapsFrom(prefs: android.content.SharedPreferences, sessionId: String, outingNumber: Int): Int {
            return prefs.getInt(lapsFromKey(sessionId, outingNumber), 0)
        }

        fun storedLapsTo(prefs: android.content.SharedPreferences, sessionId: String, outingNumber: Int): Int {
            return prefs.getInt(lapsToKey(sessionId, outingNumber), 0)
        }

        private fun forceKey(sessionId: String, outingNumber: Int): String {
            return "${sessionId}_outing_${outingNumber}_telemetry_overlay_force"
        }

        private fun pendingKey(sessionId: String, outingNumber: Int, kind: JobKind): String {
            return "${sessionId}_outing_${outingNumber}_${kind.pendingSuffix()}"
        }

        private fun failedKey(sessionId: String, outingNumber: Int, kind: JobKind): String {
            return "${sessionId}_outing_${outingNumber}_${kind.failedSuffix()}"
        }

        private fun progressKey(sessionId: String, outingNumber: Int, kind: JobKind): String {
            return "${sessionId}_outing_${outingNumber}_${kind.progressSuffix()}"
        }

        private fun lapsTrimStartKey(sessionId: String, outingNumber: Int) =
            "${sessionId}_outing_${outingNumber}_laps_export_trim_start"

        private fun lapsTrimEndKey(sessionId: String, outingNumber: Int) =
            "${sessionId}_outing_${outingNumber}_laps_export_trim_end"

        private fun lapsFromKey(sessionId: String, outingNumber: Int) =
            "${sessionId}_outing_${outingNumber}_laps_export_from"

        private fun lapsToKey(sessionId: String, outingNumber: Int) =
            "${sessionId}_outing_${outingNumber}_laps_export_to"

        private fun JobKind.pendingSuffix(): String = when (this) {
            JobKind.SESSION -> "telemetry_overlay_pending"
            JobKind.LAPS -> "laps_export_pending"
            JobKind.HUD -> "hud_export_pending"
        }

        private fun JobKind.failedSuffix(): String = when (this) {
            JobKind.SESSION -> "telemetry_overlay_failed"
            JobKind.LAPS -> "laps_export_failed"
            JobKind.HUD -> "hud_export_failed"
        }

        private fun JobKind.progressSuffix(): String = when (this) {
            JobKind.SESSION -> "telemetry_overlay_progress"
            JobKind.LAPS -> "laps_export_progress"
            JobKind.HUD -> "hud_export_progress"
        }

        private fun jobFromIntent(intent: Intent?): Job? {
            val extras = intent ?: return null
            val sessionId = extras.getStringExtra(EXTRA_SESSION_ID).orEmpty()
            val outingNumber = extras.getIntExtra(EXTRA_OUTING_NUMBER, 0)
            if (sessionId.isBlank() || outingNumber <= 0) return null
            return Job(
                kind = JobKind.fromExtra(extras.getStringExtra(EXTRA_KIND)),
                sessionId = sessionId,
                outingNumber = outingNumber,
                trimStartMs = extras.getLongExtra(EXTRA_TRIM_START, 0L),
                trimEndMs = extras.getLongExtra(EXTRA_TRIM_END, 0L),
                lapFrom = extras.getIntExtra(EXTRA_LAP_FROM, 0),
                lapTo = extras.getIntExtra(EXTRA_LAP_TO, 0),
                force = extras.getBooleanExtra(EXTRA_FORCE, false)
            )
        }

        private fun finishedNotificationId(kind: JobKind): Int = kind.readyNotificationId
    }
}
