package com.revix.app.drag

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.revix.app.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DragVideoLibrary {
    const val SUBDIRECTORY = "REVIX/Drag"
    private const val VIDEO_MIME_TYPE = "video/mp4"

    enum class Kind {
        RECORDING,
        HUD,
        HUD_ALPHA
    }

    fun buildDisplayName(
        context: Context,
        sessionName: String?,
        runNumber: Int,
        kind: Kind,
        timestampMs: Long = System.currentTimeMillis()
    ): String {
        val session = sanitize(sessionName)
            ?: context.getString(R.string.drag_attempt_video_title)
        val run = runNumber.takeIf { it > 0 }?.let {
            context.getString(R.string.drag_run_short_format, it)
        }
        val date = SimpleDateFormat("dd.MM.yyyy HH.mm", Locale.getDefault()).format(Date(timestampMs))
        val kindLabel = when (kind) {
            Kind.RECORDING -> null
            Kind.HUD -> context.getString(R.string.drag_attempt_video_filename_hud)
            Kind.HUD_ALPHA -> context.getString(R.string.drag_attempt_video_filename_hud_alpha)
        }
        return listOfNotNull(session, run, kindLabel, date).joinToString(" ") + ".mp4"
    }

    fun saveVideo(context: Context, sourceFile: File, displayName: String): Uri? {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) return null
        val app = context.applicationContext
        val resolver = app.contentResolver
        val fileName = ensureMp4(displayName)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, VIDEO_MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$SUBDIRECTORY")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    SUBDIRECTORY
                )
                if (!directory.exists()) directory.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.Video.Media.DATA, File(directory, fileName).absolutePath)
            }
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("Could not write drag video")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                    null,
                    null
                )
            }
            uri
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun sanitize(raw: String?): String? {
        val cleaned = raw.orEmpty()
            .replace('_', ' ')
            .replace("[\\\\/:*?\"<>|]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .trim('.')
            .take(40)
            .trim()
        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun ensureMp4(displayName: String): String {
        val trimmed = displayName.trim().ifBlank { "REVIX Drag.mp4" }
        return if (trimmed.endsWith(".mp4", ignoreCase = true)) trimmed else "$trimmed.mp4"
    }
}
