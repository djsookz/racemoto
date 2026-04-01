package com.example.clinometer

import android.content.Context
import com.example.clinometer.track.custom.CustomTrackStorage

object TrackSessionIdUtils {
    fun extractTrackIdFromSessionId(context: Context, sessionId: String): String {
        if (sessionId.isBlank()) {
            return ""
        }

        val withoutProfileId = if (sessionId.matches(Regex("\\d+_.*"))) {
            sessionId.substringAfter("_")
        } else {
            sessionId
        }

        val trackManager = TrackManager(context)
        val officialIds = trackManager.getAllTracks()
            .map { it.id }
            .sortedByDescending { it.length }

        val officialMatch = officialIds.firstOrNull { withoutProfileId.startsWith(it) }
        if (officialMatch != null) {
            return officialMatch
        }

        if (withoutProfileId.startsWith("custom_")) {
            val customTracks = CustomTrackStorage.loadCustomTracks(context)
            val customIdMatch = customTracks
                .map { it.id }
                .sortedByDescending { it.length }
                .firstOrNull { withoutProfileId.startsWith(it) }

            return customIdMatch ?: withoutProfileId
        }

        val datePattern = Regex("_\\d{2}\\.\\d{2}\\.\\d{4}")
        val match = datePattern.find(withoutProfileId)
        return if (match != null) {
            withoutProfileId.substring(0, match.range.first)
        } else {
            withoutProfileId
        }
    }
}