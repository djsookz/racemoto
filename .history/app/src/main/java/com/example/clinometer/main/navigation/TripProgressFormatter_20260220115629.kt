package com.example.clinometer.main.navigation

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class TripProgressTextModel(
    val etaText: String,
    val timeRemainingText: String,
    val distanceRemainingText: String
)

object TripProgressFormatter {
    fun format(distanceRemainingMeters: Float, durationRemainingSeconds: Long): TripProgressTextModel {
        val distanceKm = (distanceRemainingMeters / 1000f).toInt().coerceAtLeast(0)
        val distanceRemainingText = distanceKm.toString()

        val totalMinutes = (durationRemainingSeconds / 60).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val timeRemainingText = if (hours > 0) "${hours}ч ${minutes}м" else "${minutes}м"

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.SECOND, durationRemainingSeconds.toInt())
        val etaText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)

        return TripProgressTextModel(
            etaText = etaText,
            timeRemainingText = timeRemainingText,
            distanceRemainingText = distanceRemainingText
        )
    }
}
