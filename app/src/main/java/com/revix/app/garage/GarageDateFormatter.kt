package com.revix.app.garage

import android.content.Context
import com.revix.app.settings.LanguageManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GarageDateFormatter {
    private const val DATE_TIME_PATTERN = "dd MMM yyyy, HH:mm"
    private const val DATE_PATTERN = "dd MMM yyyy"

    fun resolveTimestamp(context: Context, rawDate: String, createdAt: Long): Long {
        return parseStoredDate(context, rawDate)?.time
            ?: createdAt.takeIf { it > 0L }
            ?: System.currentTimeMillis()
    }

    fun formatDateTime(context: Context, rawDate: String, createdAt: Long): String {
        val locale = LanguageManager.getCurrentLocale(context)
        val formatted = SimpleDateFormat(DATE_TIME_PATTERN, locale)
            .format(Date(resolveTimestamp(context, rawDate, createdAt)))
        return formatted.uppercase(locale)
    }

    fun formatDate(context: Context, rawDate: String, createdAt: Long): String {
        val locale = LanguageManager.getCurrentLocale(context)
        val formatted = SimpleDateFormat(DATE_PATTERN, locale)
            .format(Date(resolveTimestamp(context, rawDate, createdAt)))
        return formatted.uppercase(locale)
    }

    fun createDateTimeFormatter(context: Context): SimpleDateFormat {
        return SimpleDateFormat(DATE_TIME_PATTERN, LanguageManager.getCurrentLocale(context))
    }

    fun createDateFormatter(context: Context): SimpleDateFormat {
        return SimpleDateFormat(DATE_PATTERN, LanguageManager.getCurrentLocale(context))
    }

    fun formatDateFromMillis(context: Context, millis: Long): String {
        val locale = LanguageManager.getCurrentLocale(context)
        return SimpleDateFormat(DATE_PATTERN, locale)
            .format(Date(millis))
            .uppercase(locale)
    }

    private fun parseStoredDate(context: Context, rawDate: String): Date? {
        val value = rawDate.trim()
        if (value.isBlank()) {
            return null
        }

        val locales = linkedSetOf(
            LanguageManager.getCurrentLocale(context),
            LanguageManager.getLocaleForLanguage(LanguageManager.Language.ENGLISH),
            LanguageManager.getLocaleForLanguage(LanguageManager.Language.BULGARIAN),
            LanguageManager.getLocaleForLanguage(LanguageManager.Language.GREEK),
            Locale.getDefault()
        )

        for (locale in locales) {
            for (candidate in listOf(value, value.lowercase(locale))) {
                for (pattern in listOf(DATE_TIME_PATTERN, DATE_PATTERN)) {
                    val parsed = runCatching {
                        SimpleDateFormat(pattern, locale).apply { isLenient = false }.parse(candidate)
                    }.getOrNull()
                    if (parsed != null) {
                        return parsed
                    }
                }
            }
        }
        return null
    }
}
