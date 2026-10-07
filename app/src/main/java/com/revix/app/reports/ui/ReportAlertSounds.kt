package com.revix.app.reports.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.revix.app.audio.AppAudioFocus
import com.revix.app.reports.data.ReportType
import com.revix.app.settings.LanguageManager

/**
 * Custom voice clips for report alerts.
 *
 * Place files in: app/src/main/res/raw/
 * Names (lowercase, .ogg or .mp3):
 *   alert_police, alert_camera, alert_accident, alert_hazard, alert_traffic, alert_roadwork
 */
object ReportAlertSounds {
    private const val TAG = "ReportAlertSounds"

    @Volatile
    private var player: MediaPlayer? = null
    private var soundFocusHeld = false

    fun play(context: Context, reportType: ReportType, onComplete: (() -> Unit)? = null): Boolean {
        val resId = context.resources.getIdentifier(
            rawAssetName(reportType),
            "raw",
            context.packageName
        )
        if (resId == 0) {
            Log.w(TAG, "Missing raw sound for ${reportType.name}")
            return false
        }

        return try {
            stop()
            AppAudioFocus.request(context)
            soundFocusHeld = true
            val mediaPlayer = MediaPlayer.create(context, resId) ?: run {
                releaseSoundFocus()
                return false
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            mediaPlayer.setOnCompletionListener {
                it.release()
                if (player === it) {
                    player = null
                }
                releaseSoundFocus()
                onComplete?.invoke()
            }
            mediaPlayer.setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                mp.release()
                if (player === mp) {
                    player = null
                }
                releaseSoundFocus()
                onComplete?.invoke()
                true
            }
            player = mediaPlayer
            mediaPlayer.start()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alert sound for ${reportType.name}", e)
            releaseSoundFocus()
            false
        }
    }

    fun stop() {
        player?.let { active ->
            try {
                if (active.isPlaying) {
                    active.stop()
                }
            } catch (_: IllegalStateException) {
            }
            active.release()
        }
        player = null
        releaseSoundFocus()
    }

    private fun releaseSoundFocus() {
        if (!soundFocusHeld) return
        soundFocusHeld = false
        AppAudioFocus.abandon()
    }

    fun fallbackMessage(
        reportType: ReportType,
        language: LanguageManager.Language
    ): String {
        return when (language) {
            LanguageManager.Language.BULGARIAN -> when (reportType) {
                ReportType.POLICE -> "Докладвана полиция напред"
                ReportType.CAMERA -> "Докладвана камера напред"
                ReportType.ACCIDENT -> "Докладван инцидент напред"
                ReportType.HAZARD -> "Докладвана опасност напред"
                ReportType.TRAFFIC -> "Докладван трафик напред"
                ReportType.ROADWORK -> "Докладван ремонт на пътя напред"
            }
            LanguageManager.Language.GREEK -> when (reportType) {
                ReportType.POLICE -> "Αναφερόμενη αστυνομία μπροστά"
                ReportType.CAMERA -> "Αναφερόμενη κάμερα μπροστά"
                ReportType.ACCIDENT -> "Αναφερόμενο ατύχημα μπροστά"
                ReportType.HAZARD -> "Αναφερόμενος κίνδυνος μπροστά"
                ReportType.TRAFFIC -> "Αναφερόμενη κίνηση μπροστά"
                ReportType.ROADWORK -> "Αναφερόμενα έργα στον δρόμο μπροστά"
            }
            LanguageManager.Language.ENGLISH -> when (reportType) {
                ReportType.POLICE -> "Reported police ahead"
                ReportType.CAMERA -> "Reported camera ahead"
                ReportType.ACCIDENT -> "Reported accident ahead"
                ReportType.HAZARD -> "Reported hazard ahead"
                ReportType.TRAFFIC -> "Reported traffic ahead"
                ReportType.ROADWORK -> "Reported road work ahead"
            }
        }
    }

    private fun rawAssetName(reportType: ReportType): String = when (reportType) {
        ReportType.POLICE -> "alert_police"
        ReportType.CAMERA -> "alert_camera"
        ReportType.ACCIDENT -> "alert_accident"
        ReportType.HAZARD -> "alert_hazard"
        ReportType.TRAFFIC -> "alert_traffic"
        ReportType.ROADWORK -> "alert_roadwork"
    }
}
