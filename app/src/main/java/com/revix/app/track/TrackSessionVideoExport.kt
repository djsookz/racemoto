package com.revix.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TrackSessionVideoExport {
    enum class Kind {
        RAW,
        SESSION,
        LAPS,
        HUD,
        HUD_ALPHA
    }

    private const val VIDEO_MIME_TYPE = "video/mp4"
    private const val VIDEO_LIBRARY_SUBDIRECTORY = "REVIX/Track"
    private const val MAX_TRACK_NAME_CHARS = 36

    fun buildDisplayName(
        context: Context,
        trackName: String?,
        kind: Kind,
        sessionDate: String? = null,
        sessionTime: String? = null,
        lapFrom: Int? = null,
        lapTo: Int? = null,
        partIndex: Int? = null
    ): String {
        val track = sanitizeTrackName(trackName)
            ?: context.getString(R.string.track_session_video_title)
        val date = resolveDateLabel(sessionDate)
        val time = resolveTimeLabel(sessionTime)
        val kindLabel = when (kind) {
            Kind.RAW -> context.getString(R.string.track_video_filename_raw)
            Kind.SESSION -> context.getString(R.string.track_video_filename_session)
            Kind.HUD -> context.getString(R.string.track_video_filename_hud)
            Kind.HUD_ALPHA -> context.getString(R.string.track_video_filename_hud_alpha)
            Kind.LAPS -> {
                val from = lapFrom ?: 1
                val to = lapTo ?: from
                if (from == to) {
                    context.getString(R.string.track_video_filename_lap, from)
                } else {
                    context.getString(R.string.track_video_filename_laps, from, to)
                }
            }
        }
        val part = if (partIndex != null && partIndex > 0) {
            " " + context.getString(R.string.track_video_filename_part, partIndex)
        } else {
            ""
        }
        return listOfNotNull(track, date, time, kindLabel).joinToString(" ") + "$part.mp4"
    }

    fun shareTitleFromFileName(displayName: String): String {
        return displayName.removeSuffix(".mp4").trim()
    }

    fun saveHudOverlayVideoToLibrary(context: Context, sourceFile: File, displayName: String): Uri? {
        return saveVideoToLibrary(context, sourceFile, displayName)
    }

    fun saveVideoToLibrary(context: Context, sourceFile: File, displayName: String): Uri? {
        if (!sourceFile.exists()) return null

        val contentResolver = context.contentResolver
        val fileName = ensureMp4Extension(displayName)
        val targetUri = contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            buildVideoContentValues(fileName)
        ) ?: return null

        return try {
            contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IllegalStateException("Could not open output stream for track session video")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.update(
                    targetUri,
                    ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    },
                    null,
                    null
                )
            }

            targetUri
        } catch (_: Exception) {
            contentResolver.delete(targetUri, null, null)
            null
        }
    }

    data class LibraryExport(
        val uri: Uri,
        val kind: TrackSessionVideoKind,
        val lapFrom: Int = 0,
        val lapTo: Int = 0
    )

    fun findLibraryExports(
        context: Context,
        trackName: String?,
        sessionDate: String?
    ): List<LibraryExport> {
        val dateToken = resolveDateLabel(sessionDate).lowercase(Locale.getDefault())
        val trackToken = sanitizeTrackName(trackName)?.lowercase(Locale.getDefault()).orEmpty()
        if (dateToken.isBlank()) return emptyList()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME
        )
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val (selection, selectionArgs) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?" to arrayOf("%$VIDEO_LIBRARY_SUBDIRECTORY%")
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.Video.Media.DATA} LIKE ?" to arrayOf("%$VIDEO_LIBRARY_SUBDIRECTORY%")
        }
        val results = mutableListOf<LibraryExport>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameColumn).orEmpty()
                    val lower = displayName.lowercase(Locale.getDefault())
                    if (!lower.contains(dateToken)) continue
                    if (trackToken.isNotBlank() &&
                        !lower.contains(trackToken) &&
                        !lower.contains(trackToken.replace(' ', '_'))
                    ) {
                        continue
                    }
                    val classified = classifyLibraryFileName(lower) ?: continue
                    val uri = Uri.withAppendedPath(collection, cursor.getLong(idColumn).toString())
                    results += LibraryExport(
                        uri = uri,
                        kind = classified.first,
                        lapFrom = classified.second,
                        lapTo = classified.third
                    )
                }
            }
        }
        if (results.isEmpty()) {
            runCatching {
                context.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$dateToken%"),
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val displayName = cursor.getString(nameColumn).orEmpty()
                        val lower = displayName.lowercase(Locale.getDefault())
                        if (trackToken.isNotBlank() &&
                            !lower.contains(trackToken) &&
                            !lower.contains(trackToken.replace(' ', '_'))
                        ) {
                            continue
                        }
                        val classified = classifyLibraryFileName(lower) ?: continue
                        val uri = Uri.withAppendedPath(collection, cursor.getLong(idColumn).toString())
                        if (results.none { item -> item.uri == uri }) {
                            results += LibraryExport(
                                uri = uri,
                                kind = classified.first,
                                lapFrom = classified.second,
                                lapTo = classified.third
                            )
                        }
                    }
                }
            }
        }
        return results
    }

    private fun classifyLibraryFileName(lowerName: String): Triple<TrackSessionVideoKind, Int, Int>? {
        if (
            lowerName.contains("hud") ||
            lowerName.contains("магента") ||
            lowerName.contains("magenta") ||
            lowerName.contains("алфа") ||
            lowerName.contains("alpha")
        ) {
            return Triple(TrackSessionVideoKind.HUD, 0, 0)
        }
        val lapsMatch = Regex("""(?:обик|laps?|γύροι|γύρος)\s*(\d+)(?:\s*-\s*(\d+))?""").find(lowerName)
        if (lapsMatch != null) {
            val from = lapsMatch.groupValues[1].toIntOrNull() ?: 1
            val to = lapsMatch.groupValues[2].toIntOrNull() ?: from
            return Triple(TrackSessionVideoKind.LAPS, from, to)
        }
        if (
            lowerName.contains("сесия") ||
            lowerName.contains("session") ||
            lowerName.contains("συνεδρία") ||
            lowerName.contains("έξοδος")
        ) {
            return Triple(TrackSessionVideoKind.SESSION, 0, 0)
        }
        return null
    }

    private fun ensureMp4Extension(displayName: String): String {
        val trimmed = displayName.trim().ifBlank { "REVIX.mp4" }
        return if (trimmed.endsWith(".mp4", ignoreCase = true)) trimmed else "$trimmed.mp4"
    }

    private fun sanitizeTrackName(raw: String?): String? {
        val cleaned = raw.orEmpty()
            .replace('_', ' ')
            .replace("[\\\\/:*?\"<>|]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .trim('.')
            .take(MAX_TRACK_NAME_CHARS)
            .trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun resolveDateLabel(sessionDate: String?): String {
        val cleaned = sessionDate.orEmpty()
            .trim()
            .replace('/', '.')
            .replace('-', '.')
            .replace("\\s+".toRegex(), "")
        val looksValid = cleaned.isNotBlank() &&
            !cleaned.contains("--") &&
            cleaned.any { it.isDigit() }
        return if (looksValid) {
            cleaned
        } else {
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
        }
    }

    private fun resolveTimeLabel(sessionTime: String?): String? {
        val cleaned = sessionTime.orEmpty()
            .trim()
            .replace(':', '.')
            .replace("\\s+".toRegex(), "")
        val looksValid = cleaned.isNotBlank() &&
            !cleaned.contains("--") &&
            cleaned.any { it.isDigit() }
        return cleaned.takeIf { looksValid }
    }

    private fun buildVideoContentValues(displayName: String): ContentValues {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, VIDEO_MIME_TYPE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$VIDEO_LIBRARY_SUBDIRECTORY")
            values.put(MediaStore.Video.Media.IS_PENDING, 1)
        } else {
            val legacyDirectory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                VIDEO_LIBRARY_SUBDIRECTORY
            )
            if (!legacyDirectory.exists()) {
                legacyDirectory.mkdirs()
            }
            @Suppress("DEPRECATION")
            values.put(MediaStore.Video.Media.DATA, File(legacyDirectory, displayName).absolutePath)
        }

        return values
    }
}
