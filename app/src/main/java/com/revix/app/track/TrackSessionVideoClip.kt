package com.revix.app

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class TrackSessionVideoKind {
    RECORDING,
    SESSION,
    LAPS,
    HUD;

    fun storageKey(): String = name.lowercase()

    companion object {
        fun fromStorage(raw: String?, overlayExported: Boolean): TrackSessionVideoKind {
            return when (raw?.lowercase()) {
                "recording", "raw" -> RECORDING
                "session" -> SESSION
                "laps" -> LAPS
                "hud" -> HUD
                else -> if (overlayExported) SESSION else RECORDING
            }
        }
    }
}

data class TrackSessionVideoClip(
    val uri: String = "",
    val path: String = "",
    val camera: String = "",
    val sessionStartOffsetMs: Long = 0L,
    val sessionElapsedAtStartMs: Long = 0L,
    val overlayExported: Boolean = false,
    val kind: TrackSessionVideoKind = TrackSessionVideoKind.RECORDING,
    val lapFrom: Int = 0,
    val lapTo: Int = 0,
    val sourceTrimStartMs: Long = 0L
) {
    fun hasPlayableContent(): Boolean = uri.isNotBlank() || path.isNotBlank()
}

object TrackSessionVideoClipsCodec {
    private const val KEY_URI = "uri"
    private const val KEY_PATH = "path"
    private const val KEY_CAMERA = "camera"
    private const val KEY_OFFSET = "sessionStartOffsetMs"
    private const val KEY_ELAPSED = "sessionElapsedAtStartMs"
    private const val KEY_OVERLAY = "overlayExported"
    private const val KEY_KIND = "kind"
    private const val KEY_LAP_FROM = "lapFrom"
    private const val KEY_LAP_TO = "lapTo"
    private const val KEY_SOURCE_TRIM = "sourceTrimStartMs"

    fun encode(clips: List<TrackSessionVideoClip>): String {
        val array = JSONArray()
        clips.forEach { clip ->
            array.put(
                JSONObject().apply {
                    put(KEY_URI, clip.uri)
                    put(KEY_PATH, clip.path)
                    put(KEY_CAMERA, clip.camera)
                    put(KEY_OFFSET, clip.sessionStartOffsetMs)
                    put(KEY_ELAPSED, clip.sessionElapsedAtStartMs)
                    put(KEY_OVERLAY, clip.overlayExported || clip.kind != TrackSessionVideoKind.RECORDING)
                    put(KEY_KIND, clip.kind.storageKey())
                    put(KEY_LAP_FROM, clip.lapFrom)
                    put(KEY_LAP_TO, clip.lapTo)
                    put(KEY_SOURCE_TRIM, clip.sourceTrimStartMs.coerceAtLeast(0L))
                }
            )
        }
        return array.toString()
    }

    fun decode(json: String?): List<TrackSessionVideoClip> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val overlayExported = obj.optBoolean(KEY_OVERLAY, false)
                    val clip = TrackSessionVideoClip(
                        uri = obj.optString(KEY_URI).orEmpty(),
                        path = obj.optString(KEY_PATH).orEmpty(),
                        camera = obj.optString(KEY_CAMERA).orEmpty(),
                        sessionStartOffsetMs = obj.optLong(KEY_OFFSET, 0L).coerceAtLeast(0L),
                        sessionElapsedAtStartMs = if (obj.has(KEY_ELAPSED)) {
                            obj.optLong(KEY_ELAPSED, 0L)
                        } else {
                            -obj.optLong(KEY_OFFSET, 0L).coerceAtLeast(0L)
                        },
                        overlayExported = overlayExported,
                        kind = TrackSessionVideoKind.fromStorage(
                            raw = if (obj.has(KEY_KIND)) obj.optString(KEY_KIND) else null,
                            overlayExported = overlayExported
                        ),
                        lapFrom = obj.optInt(KEY_LAP_FROM, 0),
                        lapTo = obj.optInt(KEY_LAP_TO, 0),
                        sourceTrimStartMs = obj.optLong(KEY_SOURCE_TRIM, 0L).coerceAtLeast(0L)
                    )
                    if (clip.hasPlayableContent()) {
                        add(clip)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun loadFromPrefs(
        prefs: SharedPreferences,
        sessionId: String,
        outingNumber: Int
    ): List<TrackSessionVideoClip> {
        val prefix = "${sessionId}_outing_${outingNumber}"
        val multi = decode(prefs.getString("${prefix}_video_clips_json", null))
        if (multi.isNotEmpty()) return multi

        val uri = prefs.getString("${prefix}_video_uri", "").orEmpty()
        val path = prefs.getString("${prefix}_video_path", "").orEmpty()
        if (uri.isBlank() && path.isBlank()) return emptyList()

        val offsetKey = "${prefix}_video_session_start_offset_ms"
        val elapsedKey = "${prefix}_video_session_elapsed_at_start_ms"
        val offsetMs = prefs.getLong(offsetKey, 0L).coerceAtLeast(0L)
        val elapsedMs = if (prefs.contains(elapsedKey)) {
            prefs.getLong(elapsedKey, 0L)
        } else {
            -offsetMs
        }
        val overlayExported = prefs.getBoolean("${prefix}_video_overlay_exported", false)
        return listOf(
            TrackSessionVideoClip(
                uri = uri,
                path = path,
                camera = prefs.getString("${prefix}_video_camera", "").orEmpty(),
                sessionStartOffsetMs = offsetMs,
                sessionElapsedAtStartMs = elapsedMs,
                overlayExported = overlayExported,
                kind = TrackSessionVideoKind.fromStorage(null, overlayExported)
            )
        )
    }

    fun writeToEditor(
        editor: SharedPreferences.Editor,
        sessionId: String,
        outingNumber: Int,
        clips: List<TrackSessionVideoClip>
    ) {
        val prefix = "${sessionId}_outing_${outingNumber}"
        val playable = clips.filter { it.hasPlayableContent() }
        if (playable.isEmpty()) {
            editor.remove("${prefix}_video_uri")
            editor.remove("${prefix}_video_path")
            editor.remove("${prefix}_video_camera")
            editor.remove("${prefix}_video_session_start_offset_ms")
            editor.remove("${prefix}_video_session_elapsed_at_start_ms")
            editor.remove("${prefix}_video_overlay_exported")
            editor.remove("${prefix}_video_clips_json")
            return
        }

        val primary = playable.firstOrNull { clip -> clip.kind == TrackSessionVideoKind.RECORDING }
            ?: playable.first()
        if (primary.uri.isNotBlank()) {
            editor.putString("${prefix}_video_uri", primary.uri)
        } else {
            editor.remove("${prefix}_video_uri")
        }
        if (primary.path.isNotBlank()) {
            editor.putString("${prefix}_video_path", primary.path)
        } else {
            editor.remove("${prefix}_video_path")
        }
        if (primary.camera.isNotBlank()) {
            editor.putString("${prefix}_video_camera", primary.camera)
        } else {
            editor.remove("${prefix}_video_camera")
        }
        editor.putLong("${prefix}_video_session_start_offset_ms", primary.sessionStartOffsetMs)
        editor.putLong("${prefix}_video_session_elapsed_at_start_ms", primary.sessionElapsedAtStartMs)
        editor.putBoolean("${prefix}_video_overlay_exported", primary.overlayExported)
        editor.putString("${prefix}_video_clips_json", encode(playable))
    }
}
