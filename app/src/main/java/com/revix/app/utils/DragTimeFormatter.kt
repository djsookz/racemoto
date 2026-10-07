package com.revix.app.utils

import java.util.Locale

object DragTimeFormatter {
    private val LOCALE = Locale.US

    const val PLACEHOLDER = "--.--"

    fun formatSeconds(seconds: Double, decimals: Int = 2): String =
        String.format(LOCALE, "%.${decimals}f", seconds)

    fun formatSecondsFromNanos(nanos: Long?, decimals: Int = 2): String {
        if (nanos == null || nanos <= 0L) return PLACEHOLDER
        return formatSeconds(nanos / 1_000_000_000.0, decimals)
    }

    fun formatSecondsWithUnit(seconds: Double, decimals: Int = 2): String =
        String.format(LOCALE, "%.${decimals}f s", seconds)

    fun formatSecondsWithUnitFromNanos(nanos: Long?, decimals: Int = 2): String {
        if (nanos == null || nanos <= 0L) return "-"
        return formatSecondsWithUnit(nanos / 1_000_000_000.0, decimals)
    }
}
