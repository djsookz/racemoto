package com.revix.app.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.preference.PreferenceManager
import java.util.*

/**
 * Manager за език на апликацията
 */
object LanguageManager {
    
    private const val PREF_LANGUAGE = "app_language"
    private const val PREF_LANGUAGE_INITIALIZED = "app_language_initialized"
    
    enum class Language(val displayName: String, val localeCode: String) {
        ENGLISH("English", "en"),
        BULGARIAN("Български", "bg"),
        GREEK("Ελληνικά", "el")
    }
    
    fun getLocaleForLanguage(language: Language): Locale {
        return when (language) {
            Language.ENGLISH -> Locale.ENGLISH
            Language.BULGARIAN -> Locale("bg")
            Language.GREEK -> Locale("el")
        }
    }
    
    fun getLanguage(context: Context): Language {
        ensureInitialLanguage(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val value = prefs.getString(PREF_LANGUAGE, Language.ENGLISH.name)
        return try {
            Language.valueOf(value ?: Language.ENGLISH.name)
        } catch (e: IllegalArgumentException) {
            Language.ENGLISH // Default на английски ако има грешка
        }
    }
    
    fun setLanguage(context: Context, language: Language) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_LANGUAGE, language.name)
            .putBoolean(PREF_LANGUAGE_INITIALIZED, true)
            .apply()
    }

    /**
     * On first launch, match Bulgarian/Greek system language; otherwise keep English.
     * Skipped once the user has an explicit saved language choice.
     */
    fun ensureInitialLanguage(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(PREF_LANGUAGE_INITIALIZED, false)) return

        val editor = prefs.edit().putBoolean(PREF_LANGUAGE_INITIALIZED, true)
        if (!prefs.contains(PREF_LANGUAGE)) {
            val systemLocale = getSystemLocale(context)
            editor.putString(PREF_LANGUAGE, resolveLanguageFromSystemLocale(systemLocale).name)
        }
        editor.apply()
    }

    private fun getSystemLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }

    private fun resolveLanguageFromSystemLocale(locale: Locale): Language {
        return when (locale.language.lowercase(Locale.ROOT)) {
            Language.BULGARIAN.localeCode -> Language.BULGARIAN
            Language.GREEK.localeCode -> Language.GREEK
            else -> Language.ENGLISH
        }
    }
    
    /**
     * Прилага избрания език към контекста
     */
    fun applyLanguage(context: Context): Context {
        ensureInitialLanguage(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val value = prefs.getString(PREF_LANGUAGE, Language.ENGLISH.name)
        val language = try {
            Language.valueOf(value ?: Language.ENGLISH.name)
        } catch (e: IllegalArgumentException) {
            Language.ENGLISH
        }
        val locale = getLocaleForLanguage(language)
        return updateResources(context, locale)
    }
    
    private fun updateResources(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)
        
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }
    
    /**
     * Проверява дали е нужен рестарт на activity за да се приложи новия език
     */
    fun getCurrentLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }
}

