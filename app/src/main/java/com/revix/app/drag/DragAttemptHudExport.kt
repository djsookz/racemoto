package com.revix.app.drag

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.revix.app.DialogHelper
import com.revix.app.DragAttempt
import com.revix.app.R
import java.io.File

object DragAttemptHudExport {
    private var activeExporter: DragAttemptHudExporter? = null

    fun cancel() {
        activeExporter?.cancel()
        activeExporter = null
    }

    fun start(
        activity: AppCompatActivity,
        videoFile: File?,
        attempt: DragAttempt?,
        mode: MeasurementMode,
        title: String
    ) {
        if (videoFile == null || !videoFile.exists() || attempt == null) {
            Toast.makeText(activity, activity.getString(R.string.drag_attempt_video_missing), Toast.LENGTH_SHORT).show()
            return
        }
        val inputUri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", videoFile)
        val outputFile = File(activity.cacheDir, "drag_hud_${attempt.id}.mp4")
        val alphaFile = File(activity.cacheDir, "drag_hud_${attempt.id}_alpha.mp4")
        val exporter = DragAttemptHudExporter(activity)
        activeExporter?.cancel()
        activeExporter = exporter

        val progress = DialogHelper.builder(activity)
            .setTitle(activity.getString(R.string.drag_attempt_video_export_hud_progress_title))
            .setMessage(activity.getString(R.string.drag_attempt_video_export_hud_progress_message))
            .setCancelable(false)
            .create()
        progress.show()

        exporter.export(
            request = DragAttemptHudExporter.Request(
                inputUri = inputUri,
                outputFile = outputFile,
                alphaOutputFile = alphaFile,
                attempt = attempt,
                mode = mode
            ),
            onSuccess = { file ->
                activeExporter = null
                val displayName = DragVideoLibrary.buildDisplayName(
                    context = activity,
                    sessionName = title,
                    runNumber = 0,
                    kind = DragVideoLibrary.Kind.HUD,
                    timestampMs = attempt.timestamp
                )
                val alphaName = DragVideoLibrary.buildDisplayName(
                    context = activity,
                    sessionName = title,
                    runNumber = 0,
                    kind = DragVideoLibrary.Kind.HUD_ALPHA,
                    timestampMs = attempt.timestamp
                )
                val libraryUri = DragVideoLibrary.saveVideo(activity.applicationContext, file, displayName)
                if (alphaFile.exists()) {
                    DragVideoLibrary.saveVideo(activity.applicationContext, alphaFile, alphaName)
                    alphaFile.delete()
                }
                if (activity.isFinishing) return@export
                runCatching { progress.dismiss() }
                Toast.makeText(activity, activity.getString(R.string.drag_attempt_video_export_hud_success), Toast.LENGTH_SHORT).show()
                share(activity, libraryUri ?: FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file), title)
            },
            onError = {
                activeExporter = null
                if (activity.isFinishing) return@export
                runCatching { progress.dismiss() }
                Toast.makeText(activity, activity.getString(R.string.drag_attempt_video_export_hud_failed), Toast.LENGTH_LONG).show()
            }
        )
    }

    fun showShareOrExport(
        activity: AppCompatActivity,
        videoFile: File?,
        attempt: DragAttempt?,
        mode: MeasurementMode,
        title: String
    ) {
        val options = arrayOf(
            activity.getString(R.string.drag_attempt_video_share),
            activity.getString(R.string.drag_attempt_video_export_hud)
        )
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.drag_attempt_video_export_choice_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> DragAttemptVideoActivity.shareVideo(activity, videoFile, title)
                    1 -> start(activity, videoFile, attempt, mode, title)
                }
            }
            .show()
    }

    private fun share(context: Context, uri: Uri, title: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = context.contentResolver.getType(uri)
                ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension("mp4")
                ?: "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(shareIntent, context.getString(R.string.drag_attempt_video_export_hud_share_title))
            )
        }
    }
}
