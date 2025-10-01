package com.example.clinometer.settings

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
    
    enum class Language(val displayName: String, val localeCode: String) {
        AUTO("Автоматичен / Auto / Αυτόματο", "auto"),
        ENGLISH("English", "en"),
        BULGARIAN("Български", "bg"),
        GREEK("Ελληνικά", "el")
    }
    
    fun getLocaleForLanguage(language: Language): Locale {
        return when (language) {
            Language.AUTO -> Locale.getDefault()
            Language.ENGLISH -> Locale.ENGLISH
            Language.BULGARIAN -> Locale("bg")
            Language.GREEK -> Locale("el")
        }
    }
    
    fun getLanguage(context: Context): Language {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val value = prefs.getString(PREF_LANGUAGE, Language.AUTO.name)
        return try {
            Language.valueOf(value ?: Language.AUTO.name)
        } catch (e: IllegalArgumentException) {
            Language.AUTO
        }
    }
    
    fun setLanguage(context: Context, language: Language) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_LANGUAGE, language.name)
            .apply()
    }
    
    /**
     * Прилага избрания език към контекста
     */
    fun applyLanguage(context: Context): Context {
        val language = getLanguage(context)
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

