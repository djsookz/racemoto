package com.revix.app.track

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.revix.app.GeoPoint
import com.revix.app.LapData
import com.revix.app.RoutePoint
import java.io.File
import java.util.concurrent.Executors

/**
 * Persists lap telemetry to files instead of SharedPreferences XML.
 * Durable `.bin` is the fast source of truth; JSON is an async compatibility mirror.
 */
object TrackLapDataStore {
    private const val TAG = "TrackLapDataStore"
    private const val DIR = "track_lap_data"
    private val gson = Gson()
    private val jsonMirrorExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "track-lap-json-mirror").apply { isDaemon = true }
    }

    fun prefsKey(sessionId: String, outingNumber: Int, lapIndex: Int): String =
        "${sessionId}_outing_${outingNumber}_lap_data_${lapIndex}"

    private fun dir(context: Context): File {
        val dir = File(context.filesDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun safeSession(sessionId: String): String =
        sessionId.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun baseName(sessionId: String, outingNumber: Int, lapIndex: Int): String =
        "${safeSession(sessionId)}_o${outingNumber}_l${lapIndex}"

    fun jsonFileFor(context: Context, sessionId: String, outingNumber: Int, lapIndex: Int): File =
        File(dir(context), "${baseName(sessionId, outingNumber, lapIndex)}.json")

    fun binFileFor(context: Context, sessionId: String, outingNumber: Int, lapIndex: Int): File =
        File(dir(context), "${baseName(sessionId, outingNumber, lapIndex)}.bin")

    private fun metaFileFor(context: Context, sessionId: String, outingNumber: Int, lapIndex: Int): File =
        File(dir(context), "${baseName(sessionId, outingNumber, lapIndex)}.meta.json")

    private fun fileFor(context: Context, sessionId: String, outingNumber: Int, lapIndex: Int): File =
        jsonFileFor(context, sessionId, outingNumber, lapIndex)

    fun saveLap(
        context: Context,
        editor: SharedPreferences.Editor,
        sessionId: String,
        outingNumber: Int,
        lapIndex: Int,
        lap: LapData
    ) {
        val key = prefsKey(sessionId, outingNumber, lapIndex)
        try {
            val json = gson.toJson(lap)
            fileFor(context, sessionId, outingNumber, lapIndex).writeText(json)
            writeMeta(
                context,
                sessionId,
                outingNumber,
                lapIndex,
                TrackLapStreamWriter.LapBinMeta(
                    lapNumber = lap.lapNumber.coerceAtLeast(lapIndex),
                    startTime = lap.startTime,
                    endTime = lap.endTime,
                    maxSpeedKmh = lap.routePoints.maxOfOrNull { it.speed } ?: 0f
                )
            )
            editor.putString(key, "file")
            Log.d(TAG, "Saved lap $lapIndex to JSON (${json.length} chars, ${lap.routePoints.size} pts)")
        } catch (e: Exception) {
            Log.e(TAG, "File save failed for lap $lapIndex — falling back to prefs", e)
            try {
                editor.putString(key, gson.toJson(lap))
            } catch (prefsErr: Exception) {
                Log.e(TAG, "Prefs fallback also failed for lap $lapIndex", prefsErr)
            }
        }
    }

    /**
     * Persist a sealed live stream `.bin` immediately (no Gson / no full RAM materialize).
     * JSON mirror is scheduled in the background for compatibility.
     */
    fun saveLapBin(
        context: Context,
        editor: SharedPreferences.Editor,
        sessionId: String,
        outingNumber: Int,
        lapIndex: Int,
        sealedBin: File,
        meta: TrackLapStreamWriter.LapBinMeta
    ): Boolean {
        val key = prefsKey(sessionId, outingNumber, lapIndex)
        val target = binFileFor(context, sessionId, outingNumber, lapIndex)
        return try {
            target.parentFile?.mkdirs()
            if (sealedBin.absolutePath != target.absolutePath) {
                if (target.exists()) target.delete()
                if (!sealedBin.renameTo(target)) {
                    sealedBin.copyTo(target, overwrite = true)
                    sealedBin.delete()
                }
            }
            if (!target.exists()) return false
            writeMeta(context, sessionId, outingNumber, lapIndex, meta)
            editor.putString(key, "file")
            scheduleJsonMirror(context, sessionId, outingNumber, lapIndex)
            Log.d(TAG, "Saved lap $lapIndex binary (${target.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Binary save failed for lap $lapIndex", e)
            false
        }
    }

    fun loadLap(
        context: Context,
        sharedPrefs: SharedPreferences,
        sessionId: String,
        outingNumber: Int,
        lapIndex: Int
    ): LapData? = loadLapInternal(
        context = context,
        sharedPrefs = sharedPrefs,
        sessionId = sessionId,
        outingNumber = outingNumber,
        lapIndex = lapIndex,
        mode = TrackLapStreamWriter.ReadMode.FULL
    )

    /**
     * Faster open path for map: route + lean/timestamps from `.bin` when available.
     * Falls back to full JSON load for legacy sessions.
     */
    fun loadLapForMap(
        context: Context,
        sharedPrefs: SharedPreferences,
        sessionId: String,
        outingNumber: Int,
        lapIndex: Int
    ): LapData? = loadLapInternal(
        context = context,
        sharedPrefs = sharedPrefs,
        sessionId = sessionId,
        outingNumber = outingNumber,
        lapIndex = lapIndex,
        mode = TrackLapStreamWriter.ReadMode.MAP
    )

    private fun loadLapInternal(
        context: Context,
        sharedPrefs: SharedPreferences,
        sessionId: String,
        outingNumber: Int,
        lapIndex: Int,
        mode: TrackLapStreamWriter.ReadMode
    ): LapData? {
        val bin = binFileFor(context, sessionId, outingNumber, lapIndex)
        val meta = readMeta(context, sessionId, outingNumber, lapIndex)
        if (bin.exists() && meta != null) {
            return try {
                TrackLapStreamWriter.readFromFile(bin, meta, mode)
            } catch (e: Exception) {
                Log.e(TAG, "Failed reading lap bin $lapIndex", e)
                null
            }
        }

        // MAP mode still OK from JSON (legacy) — full parse once, charts reuse cache later.
        val jsonFile = fileFor(context, sessionId, outingNumber, lapIndex)
        if (jsonFile.exists()) {
            return try {
                parseLapJson(jsonFile.readText())
            } catch (e: Exception) {
                Log.e(TAG, "Failed reading lap JSON $lapIndex", e)
                null
            }
        }

        val raw = sharedPrefs.getString(prefsKey(sessionId, outingNumber, lapIndex), null) ?: return null
        if (raw == "file" || raw.isBlank()) return null
        return try {
            parseLapJson(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing legacy prefs lap $lapIndex", e)
            null
        }
    }

    fun scheduleJsonMirror(
        context: Context,
        sessionId: String,
        outingNumber: Int,
        lapIndex: Int
    ) {
        val appContext = context.applicationContext
        jsonMirrorExecutor.execute {
            try {
                val jsonFile = jsonFileFor(appContext, sessionId, outingNumber, lapIndex)
                if (jsonFile.exists() && jsonFile.length() > 2L) return@execute
                val bin = binFileFor(appContext, sessionId, outingNumber, lapIndex)
                val meta = readMeta(appContext, sessionId, outingNumber, lapIndex) ?: return@execute
                if (!bin.exists()) return@execute
                val lap = TrackLapStreamWriter.readFromFile(
                    bin,
                    meta,
                    TrackLapStreamWriter.ReadMode.FULL
                )
                jsonFile.writeText(gson.toJson(lap))
                Log.d(TAG, "JSON mirror ready for lap $lapIndex")
            } catch (e: Exception) {
                Log.e(TAG, "JSON mirror failed for lap $lapIndex", e)
            }
        }
    }

    private fun writeMeta(
        context: Context,
        sessionId: String,
        outingNumber: Int,
        lapIndex: Int,
        meta: TrackLapStreamWriter.LapBinMeta
    ) {
        try {
            metaFileFor(context, sessionId, outingNumber, lapIndex).writeText(gson.toJson(meta))
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing meta for lap $lapIndex", e)
        }
    }

    private fun readMeta(
        context: Context,
        sessionId: String,
        outingNumber: Int,
        lapIndex: Int
    ): TrackLapStreamWriter.LapBinMeta? {
        val metaFile = metaFileFor(context, sessionId, outingNumber, lapIndex)
        if (!metaFile.exists()) return null
        return try {
            gson.fromJson(metaFile.readText(), TrackLapStreamWriter.LapBinMeta::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading meta for lap $lapIndex", e)
            null
        }
    }

    /**
     * Gson type-erases List&lt;Long&gt;/List&lt;Float&gt; and often puts [Double] in the lists.
     * Opening lap details then crashes with ClassCastException (Double → Long/Float).
     */
    fun fromJson(json: String): LapData? = parseLapJson(json)

    private fun parseLapJson(json: String): LapData? {
        val lap = gson.fromJson(json, LapData::class.java) ?: return null
        return normalizeNumericLists(lap)
    }

    @Suppress("UNCHECKED_CAST")
    private fun normalizeNumericLists(lap: LapData): LapData {
        return lap.copy(
            speedData = coerceFloatList(lap.speedData as MutableList<*>?),
            accelerationData = coerceFloatList(lap.accelerationData as MutableList<*>?),
            leanAngleData = coerceFloatList(lap.leanAngleData as MutableList<*>?),
            gyroscopeData = coerceFloatList(lap.gyroscopeData as MutableList<*>?),
            longitudinalGData = coerceFloatList(lap.longitudinalGData as MutableList<*>?),
            lateralGData = coerceFloatList(lap.lateralGData as MutableList<*>?),
            timestamps = coerceLongList(lap.timestamps as MutableList<*>?),
            displayLeanAngleData = coerceFloatList(lap.displayLeanAngleData as MutableList<*>?),
            maxBrakingData = coerceFloatList(lap.maxBrakingData as MutableList<*>?),
            maxAccelData = coerceFloatList(lap.maxAccelData as MutableList<*>?),
            maxCorneringLeftData = coerceFloatList(lap.maxCorneringLeftData as MutableList<*>?),
            maxCorneringRightData = coerceFloatList(lap.maxCorneringRightData as MutableList<*>?),
            maxResultGData = coerceFloatList(lap.maxResultGData as MutableList<*>?),
            routePoints = coerceRoutePoints(lap.routePoints as MutableList<*>?),
            sensorData = (lap.sensorData as MutableList<*>?)?.filterNotNull()?.toMutableList() ?: mutableListOf()
        )
    }

    private fun coerceRoutePoints(raw: MutableList<*>?): MutableList<RoutePoint> {
        if (raw == null) return mutableListOf()
        val out = ArrayList<RoutePoint>(raw.size)
        for (item in raw) {
            val point = item as? RoutePoint ?: continue
            if ((point.geoPoint as GeoPoint?) == null) continue
            out.add(point)
        }
        return out
    }

    private fun coerceLongList(raw: MutableList<*>?): MutableList<Long> {
        if (raw == null) return mutableListOf()
        val out = ArrayList<Long>(raw.size)
        for (item in raw) {
            when (item) {
                is Long -> out.add(item)
                is Int -> out.add(item.toLong())
                is Number -> out.add(item.toLong())
                is String -> item.toLongOrNull()?.let { out.add(it) }
            }
        }
        return out
    }

    private fun coerceFloatList(raw: MutableList<*>?): MutableList<Float> {
        if (raw == null) return mutableListOf()
        val out = ArrayList<Float>(raw.size)
        for (item in raw) {
            when (item) {
                is Float -> out.add(item)
                is Double -> out.add(item.toFloat())
                is Number -> out.add(item.toFloat())
                is String -> item.toFloatOrNull()?.let { out.add(it) }
            }
        }
        return out
    }

    fun deleteLap(context: Context, sessionId: String, outingNumber: Int, lapIndex: Int) {
        try {
            jsonFileFor(context, sessionId, outingNumber, lapIndex).delete()
            binFileFor(context, sessionId, outingNumber, lapIndex).delete()
            metaFileFor(context, sessionId, outingNumber, lapIndex).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed deleting lap files $lapIndex", e)
        }
    }

    fun lapFileExists(context: Context, sessionId: String, outingNumber: Int, lapIndex: Int): Boolean {
        val bin = binFileFor(context, sessionId, outingNumber, lapIndex)
        val json = jsonFileFor(context, sessionId, outingNumber, lapIndex)
        return (bin.exists() && bin.length() > 8L) || (json.exists() && json.length() > 2L)
    }
}
