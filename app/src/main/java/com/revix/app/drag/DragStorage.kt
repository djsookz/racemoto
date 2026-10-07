package com.revix.app

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.revix.app.data.ProfileSessionSummaryStore
import java.io.File

/**
 * Drag session persistence.
 *
 * - Light session index on disk (metrics only, no raw telemetry)
 * - Per-attempt sample files under filesDir/drag_store/samples/
 * - One-time migration from the legacy SharedPreferences JSON blob
 *
 * Public API stays synchronous and thread-safe; UI callers should still use a
 * background dispatcher for large writes. Sample payloads are never stored in
 * the index file.
 */
object DragStorage {
    private const val TAG = "DragStorage"
    private const val STORE_DIR = "drag_store"
    private const val INDEX_FILE = "index.json"
    private const val SAMPLES_DIR = "samples"
    private const val VIDEO_DIR = "drag_videos"
    private const val LEGACY_PREFS_NAME = "drag_sessions_prefs"
    private const val LEGACY_KEY_SESSIONS = "drag_sessions"
    private const val META_PREFS = "drag_store_meta"
    private const val KEY_MIGRATED = "migrated_v2"

    private val gson = Gson()
    private val lock = Any()
    private val sessionsListType = object : TypeToken<MutableList<DragSession>>() {}.type

    fun saveDragSessions(context: Context, sessions: List<DragSession>) {
        synchronized(lock) {
            val app = context.applicationContext
            ensureMigrated(app)
            val lightSessions = sessions.map { persistSessionSamples(app, it) }
            writeIndex(app, lightSessions)
            ProfileSessionSummaryStore.updateDragSummaries(app, lightSessions)
        }
    }

    /**
     * Lightweight list for UI lists / PB lookups (sample arrays empty).
     */
    fun loadDragSessions(context: Context): MutableList<DragSession> {
        synchronized(lock) {
            val app = context.applicationContext
            ensureMigrated(app)
            return loadIndex(app).map { it.deepCopyLight() }.toMutableList()
        }
    }

    fun addDragSession(context: Context, session: DragSession) {
        synchronized(lock) {
            val app = context.applicationContext
            ensureMigrated(app)
            upsertSessionLocked(app, session)
        }
    }

    fun updateDragSession(context: Context, sessionId: Long, updatedSession: DragSession) {
        synchronized(lock) {
            val app = context.applicationContext
            ensureMigrated(app)
            upsertSessionLocked(app, updatedSession.copy(id = sessionId))
        }
    }

    fun deleteDragSession(context: Context, sessionId: Long) {
        synchronized(lock) {
            val app = context.applicationContext
            ensureMigrated(app)
            val sessions = loadIndex(app)
            if (!sessions.removeAll { it.id == sessionId }) return
            writeIndex(app, sessions)
            deleteSessionSampleDir(app, sessionId)
            deleteSessionVideos(app, sessionId)
            ProfileSessionSummaryStore.updateDragSummaries(app, sessions)
        }
    }

    /**
     * Full session with telemetry hydrated from sample files (for charts/details).
     */
    fun getDragSession(context: Context, sessionId: Long): DragSession? {
        synchronized(lock) {
            val app = context.applicationContext
            ensureMigrated(app)
            val light = loadIndex(app).find { it.id == sessionId } ?: return null
            return hydrateSession(app, light)
        }
    }

    private fun upsertSessionLocked(app: Context, session: DragSession) {
        val sessions = loadIndex(app)
        val light = persistSessionSamples(app, session)
        val index = sessions.indexOfFirst { it.id == session.id }
        if (index >= 0) {
            sessions[index] = light
        } else {
            sessions.add(light)
        }
        writeIndex(app, sessions)
        ProfileSessionSummaryStore.updateDragSummaries(app, sessions)
    }

    private fun persistSessionSamples(app: Context, session: DragSession): DragSession {
        val keptAttemptIds = HashSet<Long>()
        val lightAttempts = session.attempts.map { attempt ->
            keptAttemptIds.add(attempt.id)
            val reduced = DragSampleDownsampler.downsampleAttempt(attempt)
            if (hasAnySamples(reduced)) {
                writeAttemptSamples(app, session.id, reduced)
            }
            reduced.withoutSamples()
        }.toMutableList()
        pruneOrphanSampleFiles(app, session.id, keptAttemptIds)
        return session.copy(attempts = lightAttempts)
    }

    private fun pruneOrphanSampleFiles(app: Context, sessionId: Long, keepIds: Set<Long>) {
        val dir = File(samplesRoot(app), sessionId.toString())
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (!file.isFile || !file.name.endsWith(".json")) return@forEach
            val attemptId = file.name.removeSuffix(".json").toLongOrNull() ?: return@forEach
            if (attemptId !in keepIds) {
                runCatching { file.delete() }
            }
        }
    }

    private fun hydrateSession(app: Context, light: DragSession): DragSession {
        val attempts = light.attempts.map { attempt ->
            val samples = readAttemptSamples(app, light.id, attempt.id) ?: return@map attempt
            attempt.withSamples(samples)
        }.toMutableList()
        return light.copy(attempts = attempts)
    }

    private fun hasAnySamples(attempt: DragAttempt): Boolean {
        return attempt.gSamples.isNotEmpty() ||
            attempt.gpsAccelSamples.isNotEmpty() ||
            attempt.speedSamples.isNotEmpty() ||
            attempt.longitudinalAccelSamples.isNotEmpty() ||
            attempt.liveAccelDisplaySamples.isNotEmpty()
    }

    private fun DragAttempt.withoutSamples(): DragAttempt = copy(
        gSamples = emptyList(),
        timeStamps = emptyList(),
        gpsAccelSamples = emptyList(),
        gpsTimeStamps = emptyList(),
        speedSamples = emptyList(),
        speedTimeStamps = emptyList(),
        longitudinalAccelSamples = emptyList(),
        longitudinalAccelTimeStamps = emptyList(),
        liveAccelDisplaySamples = emptyList(),
        liveAccelDisplayTimeStamps = emptyList()
    )

    private fun DragAttempt.withSamples(samples: DragAttemptSamples): DragAttempt = copy(
        gSamples = samples.gSamples,
        timeStamps = samples.timeStamps,
        gpsAccelSamples = samples.gpsAccelSamples,
        gpsTimeStamps = samples.gpsTimeStamps,
        speedSamples = samples.speedSamples,
        speedTimeStamps = samples.speedTimeStamps,
        longitudinalAccelSamples = samples.longitudinalAccelSamples,
        longitudinalAccelTimeStamps = samples.longitudinalAccelTimeStamps,
        liveAccelDisplaySamples = samples.liveAccelDisplaySamples,
        liveAccelDisplayTimeStamps = samples.liveAccelDisplayTimeStamps
    )

    private fun DragAttempt.toSamples(): DragAttemptSamples = DragAttemptSamples(
        gSamples = gSamples,
        timeStamps = timeStamps,
        gpsAccelSamples = gpsAccelSamples,
        gpsTimeStamps = gpsTimeStamps,
        speedSamples = speedSamples,
        speedTimeStamps = speedTimeStamps,
        longitudinalAccelSamples = longitudinalAccelSamples,
        longitudinalAccelTimeStamps = longitudinalAccelTimeStamps,
        liveAccelDisplaySamples = liveAccelDisplaySamples,
        liveAccelDisplayTimeStamps = liveAccelDisplayTimeStamps
    )

    private fun DragSession.deepCopyLight(): DragSession {
        return copy(attempts = attempts.map { it.withoutSamples() }.toMutableList())
    }

    private fun storeDir(app: Context): File = File(app.filesDir, STORE_DIR).also { it.mkdirs() }

    private fun indexFile(app: Context): File = File(storeDir(app), INDEX_FILE)

    private fun samplesRoot(app: Context): File = File(storeDir(app), SAMPLES_DIR).also { it.mkdirs() }

    private fun sessionSampleDir(app: Context, sessionId: Long): File =
        File(samplesRoot(app), sessionId.toString()).also { it.mkdirs() }

    private fun attemptSampleFile(app: Context, sessionId: Long, attemptId: Long): File =
        File(sessionSampleDir(app, sessionId), "$attemptId.json")

    private fun writeAttemptSamples(app: Context, sessionId: Long, attempt: DragAttempt) {
        val file = attemptSampleFile(app, sessionId, attempt.id)
        try {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(gson.toJson(attempt.toSamples()))
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed writing samples session=$sessionId attempt=${attempt.id}", t)
        }
    }

    private fun readAttemptSamples(app: Context, sessionId: Long, attemptId: Long): DragAttemptSamples? {
        val file = attemptSampleFile(app, sessionId, attemptId)
        if (!file.exists()) return null
        return try {
            val samples = gson.fromJson(file.readText(), DragAttemptSamples::class.java) ?: return null
            sanitizeSamples(samples)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed reading samples session=$sessionId attempt=$attemptId", t)
            quarantine(file)
            null
        }
    }

    private fun sanitizeSamples(samples: DragAttemptSamples): DragAttemptSamples {
        return samples.copy(
            gSamples = (samples.gSamples as List<Float?>?)?.filterNotNull().orEmpty(),
            timeStamps = (samples.timeStamps as List<Long?>?)?.filterNotNull().orEmpty(),
            gpsAccelSamples = (samples.gpsAccelSamples as List<Float?>?)?.filterNotNull().orEmpty(),
            gpsTimeStamps = (samples.gpsTimeStamps as List<Long?>?)?.filterNotNull().orEmpty(),
            speedSamples = (samples.speedSamples as List<Float?>?)?.filterNotNull().orEmpty(),
            speedTimeStamps = (samples.speedTimeStamps as List<Long?>?)?.filterNotNull().orEmpty(),
            longitudinalAccelSamples = (samples.longitudinalAccelSamples as List<Float?>?)?.filterNotNull().orEmpty(),
            longitudinalAccelTimeStamps = (samples.longitudinalAccelTimeStamps as List<Long?>?)?.filterNotNull().orEmpty(),
            liveAccelDisplaySamples = (samples.liveAccelDisplaySamples as List<Float?>?)?.filterNotNull().orEmpty(),
            liveAccelDisplayTimeStamps = (samples.liveAccelDisplayTimeStamps as List<Long?>?)?.filterNotNull().orEmpty()
        )
    }

    fun attachAttemptVideo(
        context: Context,
        sessionId: Long,
        attemptId: Long,
        videoPath: String,
        videoT0OffsetMs: Long = 0L
    ) {
        if (sessionId <= 0L || attemptId <= 0L || videoPath.isBlank()) return
        synchronized(lock) {
            val app = context.applicationContext
            ensureMigrated(app)
            val sessions = loadIndex(app)
            val sessionIndex = sessions.indexOfFirst { it.id == sessionId }
            if (sessionIndex < 0) return
            val session = sessions[sessionIndex]
            val attemptIndex = session.attempts.indexOfFirst { it.id == attemptId }
            if (attemptIndex < 0) return
            val previous = session.attempts[attemptIndex].videoPath
            session.attempts[attemptIndex] = session.attempts[attemptIndex].copy(
                videoPath = videoPath,
                videoT0OffsetMs = videoT0OffsetMs.coerceAtLeast(0L)
            )
            sessions[sessionIndex] = session
            writeIndex(app, sessions)
            if (!previous.isNullOrBlank() && previous != videoPath) {
                runCatching { File(previous).takeIf { it.exists() }?.delete() }
            }
        }
    }

    fun videoDir(context: Context, sessionId: Long): File {
        val root = context.getExternalFilesDir(VIDEO_DIR) ?: File(context.filesDir, VIDEO_DIR)
        return File(root, sessionId.coerceAtLeast(0L).toString()).also { it.mkdirs() }
    }

    private fun deleteSessionVideos(app: Context, sessionId: Long) {
        val dir = videoDir(app, sessionId)
        if (!dir.exists()) return
        dir.walkBottomUp().forEach { child ->
            if (!child.delete()) {
                Log.w(TAG, "Could not delete ${child.absolutePath}")
            }
        }
    }

    private fun deleteSessionSampleDir(app: Context, sessionId: Long) {
        val dir = File(samplesRoot(app), sessionId.toString())
        if (!dir.exists()) return
        dir.walkBottomUp().forEach { child ->
            if (!child.delete()) {
                Log.w(TAG, "Could not delete ${child.absolutePath}")
            }
        }
    }

    private fun loadIndex(app: Context): MutableList<DragSession> {
        val file = indexFile(app)
        if (!file.exists()) return mutableListOf()
        return try {
            val json = file.readText()
            if (json.isBlank()) return mutableListOf()
            val loaded = gson.fromJson<MutableList<DragSession>>(json, sessionsListType) ?: mutableListOf()
            val sanitized = loaded.map { sanitizeSession(it) }.toMutableList()
            if (sanitized != loaded) {
                writeIndex(app, sanitized)
            }
            sanitized
        } catch (t: Throwable) {
            Log.e(TAG, "Failed loading drag index — quarantining", t)
            quarantine(file)
            mutableListOf()
        }
    }

    private fun sanitizeSession(session: DragSession): DragSession {
        val attempts = (session.attempts as MutableList<DragAttempt?>?)
            ?.mapNotNull { attempt -> attempt?.let { sanitizeAttempt(it) } }
            ?.toMutableList()
            ?: mutableListOf()
        return session.copy(attempts = attempts)
    }

    private fun sanitizeAttempt(attempt: DragAttempt): DragAttempt {
        return attempt.copy(
            gSamples = (attempt.gSamples as List<Float?>?)?.filterNotNull().orEmpty(),
            gpsAccelSamples = (attempt.gpsAccelSamples as List<Float?>?)?.filterNotNull().orEmpty(),
            timeStamps = (attempt.timeStamps as List<Long?>?)?.filterNotNull().orEmpty(),
            gpsTimeStamps = (attempt.gpsTimeStamps as List<Long?>?)?.filterNotNull().orEmpty(),
            speedSamples = (attempt.speedSamples as List<Float?>?)?.filterNotNull().orEmpty(),
            speedTimeStamps = (attempt.speedTimeStamps as List<Long?>?)?.filterNotNull().orEmpty(),
            longitudinalAccelSamples = (attempt.longitudinalAccelSamples as List<Float?>?)?.filterNotNull().orEmpty(),
            longitudinalAccelTimeStamps = (attempt.longitudinalAccelTimeStamps as List<Long?>?)?.filterNotNull().orEmpty(),
            liveAccelDisplaySamples = (attempt.liveAccelDisplaySamples as List<Float?>?)?.filterNotNull().orEmpty(),
            liveAccelDisplayTimeStamps = (attempt.liveAccelDisplayTimeStamps as List<Long?>?)?.filterNotNull().orEmpty()
        )
    }

    private fun writeIndex(app: Context, sessions: List<DragSession>) {
        val file = indexFile(app)
        val light = sessions.map { it.deepCopyLight() }
        try {
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(gson.toJson(light))
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed writing drag index", t)
        }
    }

    private fun quarantine(file: File) {
        try {
            val dest = File(file.parentFile, "${file.name}.bad.${System.currentTimeMillis()}")
            if (!file.renameTo(dest)) {
                file.delete()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Quarantine failed for ${file.absolutePath}", t)
            runCatching { file.delete() }
        }
    }

    private fun ensureMigrated(app: Context) {
        val meta = metaPrefs(app)
        if (meta.getBoolean(KEY_MIGRATED, false)) return
        migrateLegacyPrefs(app)
        meta.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun metaPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(META_PREFS, Context.MODE_PRIVATE)

    private fun migrateLegacyPrefs(app: Context) {
        val prefs = app.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(LEGACY_KEY_SESSIONS, null)
        if (json.isNullOrBlank()) return

        Log.i(TAG, "Migrating legacy drag_sessions prefs (${json.length} chars)")
        val legacySessions: List<DragSession> = try {
            val loaded = gson.fromJson<MutableList<DragSession>>(json, sessionsListType) ?: emptyList()
            loaded.map { sanitizeSession(it) }
        } catch (t: Throwable) {
            Log.e(TAG, "Legacy drag JSON unreadable — discarding to stop crash loop", t)
            prefs.edit().remove(LEGACY_KEY_SESSIONS).apply()
            return
        }

        try {
            val existing = loadIndex(app).associateBy { it.id }.toMutableMap()
            legacySessions.forEach { session ->
                existing[session.id] = persistSessionSamples(app, session)
            }
            val merged = existing.values.toList()
            writeIndex(app, merged)
            ProfileSessionSummaryStore.updateDragSummaries(app, merged)
            prefs.edit().remove(LEGACY_KEY_SESSIONS).apply()
            Log.i(TAG, "Legacy drag migration complete (${merged.size} sessions)")
        } catch (t: Throwable) {
            Log.e(TAG, "Legacy migration failed — clearing legacy blob to stop crash loop", t)
            prefs.edit().remove(LEGACY_KEY_SESSIONS).apply()
        }
    }
}
