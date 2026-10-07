package com.revix.app.settings

import android.content.Context
import androidx.preference.PreferenceManager
import com.revix.app.navigation.NavigationVoiceGuidance
import com.revix.app.reports.ui.ReportAlertSounds

/**
 * Global toggle for spoken alerts (map reports + navigation voice guidance).
 */
object VoiceAlertsSettings {
    private const val PREF_VOICE_ALERTS_ENABLED = "voice_alerts_enabled"
    private const val DEFAULT_ENABLED = true

    fun isEnabled(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .getBoolean(PREF_VOICE_ALERTS_ENABLED, DEFAULT_ENABLED)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putBoolean(PREF_VOICE_ALERTS_ENABLED, enabled)
            .apply()
        if (!enabled) {
            NavigationVoiceGuidance.stopPlayback()
            NavigationVoiceGuidance.onReportAlertFinished()
            ReportAlertSounds.stop()
        }
    }
}
