package com.revix.app.averagespeed

import android.content.Context
import com.revix.app.R
import com.revix.app.navigation.NavigationVoiceGuidance
import com.revix.app.settings.LanguageManager
import com.revix.app.settings.VoiceAlertsSettings

internal class AverageSpeedAnnouncer(private val context: Context) {

    fun speak(event: AverageSpeedVoiceEvent) {
        if (!VoiceAlertsSettings.isEnabled(context)) return
        val localized = LanguageManager.applyLanguage(context)
        val text = when (event) {
            AverageSpeedVoiceEvent.APPROACH,
            AverageSpeedVoiceEvent.ENTER,
            AverageSpeedVoiceEvent.INSIDE_MID ->
                localized.getString(R.string.avg_speed_voice_enter)
            AverageSpeedVoiceEvent.EXIT ->
                localized.getString(R.string.avg_speed_voice_exit)
        }
        NavigationVoiceGuidance.speakAlert(context, text)
    }
}
