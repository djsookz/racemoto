package com.revix.app.utils

import java.util.Locale
import kotlin.math.roundToLong

object LapTimeFormatter {
    private val LOCALE = Locale.US

    const val PLACEHOLDER = "--:--.--"
    const val PLACEHOLDER_LEGACY = "--:--.---"
    const val PLACEHOLDER_SHORT = "--:--"
    const val ZERO = "0:00.00"
    const val ZERO_PADDED = "00:00.00"

    fun isPlaceholder(raw: String?): Boolean {
        val normalized = normalizeDisplay(raw)
        return normalized.isBlank() ||
            normalized == PLACEHOLDER ||
            normalized == PLACEHOLDER_LEGACY ||
            normalized == PLACEHOLDER_SHORT
    }

    fun formatMs(totalMs: Long, padMinutes: Boolean = false): String {
        val rounded = ((totalMs.coerceAtLeast(0L) + 5L) / 10L) * 10L
        val minutes = rounded / 60_000L
        val seconds = (rounded % 60_000L) / 1_000L
        val centis = (rounded % 1_000L) / 10L
        return if (padMinutes) {
            String.format(LOCALE, "%02d:%02d.%02d", minutes, seconds, centis)
        } else {
            String.format(LOCALE, "%d:%02d.%02d", minutes, seconds, centis)
        }
    }

    fun formatMsTwoDecimalCentis(timeMs: Long): String = formatMs(timeMs, padMinutes = true)

    fun formatSeconds(seconds: Float): String {
        val ms = (seconds.coerceAtLeast(0f) * 1000f).roundToLong()
        return formatMs(ms, padMinutes = true)
    }

    fun normalizeDisplay(raw: String?): String {
        if (raw.isNullOrBlank()) return PLACEHOLDER
        return raw.trim().replace(',', '.')
    }

    fun formatDurationDisplay(rawDuration: String): String {
        val normalized = normalizeDisplay(rawDuration)
        if (isPlaceholder(normalized) || rawDuration.isBlank()) {
            return PLACEHOLDER
        }
        val ms = parseToMs(normalized) ?: return normalized
        return formatMs(ms, padMinutes = true)
    }

    fun parseToMs(value: String): Long? {
        val trimmed = normalizeDisplay(value)
        if (isPlaceholder(trimmed)) return null
        val match = Regex("""^(\d+):(\d{1,2})\.(\d{1,3})$""").find(trimmed) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        val fraction = match.groupValues[3]
        val millis = when (fraction.length) {
            1 -> "${fraction}00"
            2 -> "${fraction}0"
            else -> fraction.take(3)
        }.toLongOrNull() ?: return null
        return (minutes * 60_000L) + (seconds * 1_000L) + millis
    }
}
