package com.revix.app.drag

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.net.Uri
import com.revix.app.DragAttempt
import com.revix.app.DragSessionDetailsActivity
import com.revix.app.DragStorage
import com.revix.app.R
import java.io.File

class DragVideoOverlayService : Service() {

    private data class Job(
        val sessionId: Long,
        val mode: MeasurementMode,
        val forceAttemptId: Long = 0L
    )

    private var exporter: DragAttemptHudExporter? = null
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
        if (jobQueue.none { it.matches(job) } && currentJob?.matches(job) != true) {
            jobQueue.addLast(job)
        }
        if (isExporting) return START_STICKY
        val notification = buildProgressNotification(0, indeterminate = true)
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

    private fun jobFromIntent(intent: Intent?): Job? {
        if (intent == null) return null
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, 0L)
        if (sessionId <= 0L) return null
        val mode = runCatching {
            MeasurementMode.valueOf(intent.getStringExtra(EXTRA_MODE).orEmpty())
        }.getOrDefault(MeasurementMode.ALL)
        return Job(
            sessionId = sessionId,
            mode = mode,
            forceAttemptId = intent.getLongExtra(EXTRA_FORCE_ATTEMPT_ID, 0L)
        )
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
        updateForegroundNotification(0, true)
        exportSession(job)
    }

    private fun exportSession(job: Job) {
        val session = DragStorage.getDragSession(this, job.sessionId)
        val pending = session?.attempts
            ?.sortedBy { it.timestamp }
            ?.mapIndexed { index, attempt -> Triple(index + 1, attempt, session.name) }
            ?.filter { (_, attempt, _) ->
                val path = attempt.videoPath.orEmpty()
                val playable = path.isNotBlank() && File(path).let { it.exists() && it.length() > 0L }
                if (!playable) return@filter false
                if (job.forceAttemptId > 0L) {
                    attempt.id == job.forceAttemptId
                } else {
                    !alreadyExported(job.sessionId, attempt)
                }
            }
            .orEmpty()
        if (pending.isEmpty()) {
            processNextJob()
            return
        }
        exportNext(job, ArrayDeque(pending), pending.size)
    }

    private fun exportNext(
        job: Job,
        remaining: ArrayDeque<Triple<Int, DragAttempt, String?>>,
        total: Int
    ) {
        val next = remaining.removeFirstOrNull()
        if (next == null) {
            notifyFinished(job, success = true)
            processNextJob()
            return
        }
        val (runNumber, attempt, sessionName) = next
        val videoFile = File(attempt.videoPath.orEmpty())
        val inputUri = runCatching {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", videoFile)
        }.getOrElse { Uri.fromFile(videoFile) }
        val outputFile = File(cacheDir, "drag_gallery_${attempt.id}.mp4")
        val completed = total - remaining.size - 1
        val baseline = if (total <= 0) 0 else (completed * 100) / total
        val activeExporter = DragAttemptHudExporter(this)
        exporter = activeExporter
        activeExporter.export(
            request = DragAttemptHudExporter.Request(
                inputUri = inputUri,
                outputFile = outputFile,
                attempt = attempt,
                mode = job.mode,
                hudOnly = false
            ),
            onSuccess = { file ->
                exporter = null
                val displayName = DragVideoLibrary.buildDisplayName(
                    context = this,
                    sessionName = sessionName,
                    runNumber = runNumber,
                    kind = DragVideoLibrary.Kind.RECORDING,
                    timestampMs = attempt.timestamp
                )
                val saved = DragVideoLibrary.saveVideo(this, file, displayName) != null
                runCatching { if (file.exists()) file.delete() }
                if (saved) {
                    markExported(job.sessionId, attempt)
                }
                exportNext(job, remaining, total)
            },
            onError = { error ->
                Log.e(TAG, "Background drag overlay failed for attempt ${attempt.id}", error)
                exporter = null
                runCatching { if (outputFile.exists()) outputFile.delete() }
                exportNext(job, remaining, total)
            },
            onProgress = { percent ->
                val overall = if (total <= 0) percent else baseline + (percent / total)
                updateForegroundNotification(overall.coerceIn(0, 99), false)
            }
        )
    }

    private fun alreadyExported(sessionId: Long, attempt: DragAttempt): Boolean {
        val path = attempt.videoPath.orEmpty()
        return prefs().getString(exportedKey(sessionId, attempt.id), null) == path
    }

    private fun markExported(sessionId: Long, attempt: DragAttempt) {
        prefs().edit()
            .putString(exportedKey(sessionId, attempt.id), attempt.videoPath.orEmpty())
            .apply()
    }

    private fun prefs() = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    private fun Job.matches(other: Job): Boolean {
        return sessionId == other.sessionId && forceAttemptId == other.forceAttemptId
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
            Log.e(TAG, "Unable to start drag overlay service", error)
            false
        }
    }

    private fun updateForegroundNotification(progressPercent: Int, indeterminate: Boolean) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildProgressNotification(progressPercent, indeterminate))
    }

    private fun buildProgressNotification(progressPercent: Int, indeterminate: Boolean): Notification {
        val text = if (indeterminate || progressPercent <= 0) {
            getString(R.string.drag_video_overlay_notification_text)
        } else {
            getString(R.string.drag_video_overlay_notification_progress, progressPercent)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.drag_video_overlay_notification_title))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, progressPercent.coerceIn(0, 100), indeterminate)
            .setContentIntent(detailPendingIntent(currentJob?.sessionId ?: 0L))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun notifyFinished(job: Job, success: Boolean) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(
                getString(
                    if (success) R.string.drag_video_overlay_ready_title
                    else R.string.drag_video_overlay_failed_title
                )
            )
            .setContentText(
                getString(
                    if (success) R.string.drag_video_overlay_ready_text
                    else R.string.drag_video_overlay_failed_text
                )
            )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(detailPendingIntent(job.sessionId))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(FINISHED_NOTIFICATION_ID, notification)
    }

    private fun detailPendingIntent(sessionId: Long): PendingIntent {
        val intent = Intent(this, DragSessionDetailsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SESSION_ID", sessionId)
        }
        return PendingIntent.getActivity(
            this,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.drag_video_overlay_channel_name),
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

    companion object {
        private const val TAG = "DragVideoOverlay"
        private const val PREFS_NAME = "drag_video_overlay"
        private const val CHANNEL_ID = "revix_drag_video_overlay"
        private const val NOTIFICATION_ID = 71301
        private const val FINISHED_NOTIFICATION_ID = 71302
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_MODE = "measurement_mode"
        private const val EXTRA_FORCE_ATTEMPT_ID = "force_attempt_id"

        fun start(
            context: Context,
            sessionId: Long,
            mode: MeasurementMode,
            forceAttemptId: Long = 0L
        ) {
            if (sessionId <= 0L) return
            val intent = Intent(context, DragVideoOverlayService::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_FORCE_ATTEMPT_ID, forceAttemptId)
            }
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        private fun exportedKey(sessionId: Long, attemptId: Long): String {
            return "exported_${sessionId}_$attemptId"
        }
    }
}
