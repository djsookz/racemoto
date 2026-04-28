package com.example.clinometer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.Locale

object TrackSessionVideoExport {
    private const val VIDEO_MIME_TYPE = "video/mp4"
    private const val VIDEO_LIBRARY_SUBDIRECTORY = "RaceMoto/Track"

    fun buildExportFileName(baseTitle: String?): String {
        val sanitizedBase = baseTitle
            ?.trim()
            ?.lowercase(Locale.getDefault())
            ?.replace("[^a-z0-9]+".toRegex(), "_")
            ?.trim('_')
            ?.takeIf { it.isNotBlank() }
            ?: "track_session"

        return "${sanitizedBase}_${System.currentTimeMillis()}.mp4"
    }

    fun saveVideoToLibrary(context: Context, sourceFile: File, baseTitle: String?): Uri? {
        if (!sourceFile.exists()) return null

        val contentResolver = context.contentResolver
        val displayName = buildExportFileName(baseTitle)
        val targetUri = contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            buildVideoContentValues(displayName)
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