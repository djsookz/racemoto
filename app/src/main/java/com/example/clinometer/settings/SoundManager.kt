package com.example.clinometer.settings

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.preference.PreferenceManager
import java.util.*

/**
 * Manager за звукови ефекти в Drag режим
 */
class SoundManager(private val context: Context) {

    private var tts: TextToSpeech? = null

    private var ttsInitialized = false
    
    companion object {
        // Drag mode sounds
        private const val PREF_SOUND_100_ENABLED = "sound_100_enabled"
        private const val PREF_SOUND_200_ENABLED = "sound_200_enabled"
        private const val PREF_SOUND_402_ENABLED = "sound_402_enabled"
        private const val PREF_VOICE_COUNTDOWN_ENABLED = "voice_countdown_enabled"
        
        // Track mode sounds
        private const val PREF_SOUND_LAP_COMPLETE_ENABLED = "sound_lap_complete_enabled"
        private const val PREF_SOUND_PERSONAL_BEST_ENABLED = "sound_personal_best_enabled"
        
        // Default values
        private const val DEFAULT_SOUND_ENABLED = true
        private const val DEFAULT_VOICE_ENABLED = false
    }
    
    init {
        initializeTTS()
    }
    
    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = if (LanguageManager.getLanguage(context) == LanguageManager.Language.BULGARIAN) {
                    Locale("bg")
                } else {
                    Locale.ENGLISH
                }
                
                val result = tts?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to English
                    tts?.setLanguage(Locale.ENGLISH)
                }
                ttsInitialized = true
            }
        }
    }
    
    // === PREFERENCE GETTERS ===
    
    fun is100SoundEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_SOUND_100_ENABLED, DEFAULT_SOUND_ENABLED)
    }
    
    fun is200SoundEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_SOUND_200_ENABLED, DEFAULT_SOUND_ENABLED)
    }
    
    fun is402SoundEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_SOUND_402_ENABLED, DEFAULT_SOUND_ENABLED)
    }
    
    fun isVoiceCountdownEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_VOICE_COUNTDOWN_ENABLED, DEFAULT_VOICE_ENABLED)
    }
    
    fun isLapCompleteEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_SOUND_LAP_COMPLETE_ENABLED, DEFAULT_SOUND_ENABLED)
    }

    fun isPersonalBestEnabled(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PREF_SOUND_PERSONAL_BEST_ENABLED, DEFAULT_SOUND_ENABLED)
    }
    
    // === PREFERENCE SETTERS ===
    
    fun set100SoundEnabled(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_SOUND_100_ENABLED, enabled)
            .apply()
    }
    
    fun set200SoundEnabled(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_SOUND_200_ENABLED, enabled)
            .apply()
    }
    
    fun set402SoundEnabled(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_SOUND_402_ENABLED, enabled)
            .apply()
    }
    
    fun setVoiceCountdownEnabled(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_VOICE_COUNTDOWN_ENABLED, enabled)
            .apply()
    }
    
    fun setLapCompleteEnabled(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_SOUND_LAP_COMPLETE_ENABLED, enabled)
            .apply()
    }
    
    fun setPersonalBestEnabled(enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PREF_SOUND_PERSONAL_BEST_ENABLED, enabled)
            .apply()
    }
    
    // === SOUND PLAYBACK МЕТОДИ ===
    
    fun playSpeedReached100() {
        if (is100SoundEnabled()) {
            playBeep(1000, 200) // 1000 Hz, 200ms
        }
    }
    
    fun playSpeedReached200() {
        if (is200SoundEnabled()) {
            playBeep(1500, 300) // 1500 Hz, 300ms
        }
    }
    
    fun playQuarterMileReached() {
        if (is402SoundEnabled()) {
            playBeep(2000, 400) // 2000 Hz, 400ms
        }
    }
    
    fun playLapComplete() {
        if (isLapCompleteEnabled()) {
            playBeep(2000, 400) // Same as 402m - 2000 Hz, 400ms
        }
    }
    
    fun playPersonalBest() {
        if (isPersonalBestEnabled()) {
            playBeep(1500, 300) // Same as 200km/h - 1500 Hz, 300ms
        }
    }
    
    /**
     * Генерира beep звук с дадена честота и продължителност
     */
    private fun playBeep(frequency: Int, durationMs: Int) {
        try {
            val sampleRate = 8000
            val numSamples = (durationMs * sampleRate) / 1000
            val sample = DoubleArray(numSamples)
            val buffer = ByteArray(2 * numSamples)
            
            // Генерираме sin wave
            for (i in 0 until numSamples) {
                sample[i] = Math.sin(2.0 * Math.PI * i.toDouble() / (sampleRate.toDouble() / frequency.toDouble()))
            }
            
            // Конвертираме в 16-bit PCM
            var idx = 0
            for (i in 0 until numSamples) {
                val value = (sample[i] * 32767).toInt().toShort()
                buffer[idx++] = (value.toInt() and 0x00ff).toByte()
                buffer[idx++] = ((value.toInt() and 0xff00) ushr 8).toByte()
            }
            
            // Пускаме звука с AudioTrack
            val audioTrack = android.media.AudioTrack(
                android.media.AudioManager.STREAM_MUSIC,
                sampleRate,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                buffer.size,
                android.media.AudioTrack.MODE_STATIC
            )
            
            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            
            // Освобождаваме ресурсите след изпълнение
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                audioTrack.stop()
                audioTrack.release()
            }, (durationMs + 100).toLong())
            
        } catch (e: Exception) {
            // Fallback - използваме ToneGenerator
            val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
            toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, durationMs)
        }
    }
    
    /**
     * Гласово обратно броене (5, 4, 3, 2, 1, GO!)
     */
    fun speakCountdown(number: Int) {
        if (!isVoiceCountdownEnabled() || !ttsInitialized) return
        
        val language = LanguageManager.getLanguage(context)
        val text = when (number) {
            5 -> when (language) {
                LanguageManager.Language.BULGARIAN -> "Пет"
                LanguageManager.Language.GREEK -> "Πέντε"
                else -> "Five"
            }
            4 -> when (language) {
                LanguageManager.Language.BULGARIAN -> "Четири"
                LanguageManager.Language.GREEK -> "Τέσσερα"
                else -> "Four"
            }
            3 -> when (language) {
                LanguageManager.Language.BULGARIAN -> "Три"
                LanguageManager.Language.GREEK -> "Τρία"
                else -> "Three"
            }
            2 -> when (language) {
                LanguageManager.Language.BULGARIAN -> "Две"
                LanguageManager.Language.GREEK -> "Δύο"
                else -> "Two"
            }
            1 -> when (language) {
                LanguageManager.Language.BULGARIAN -> "Едно"
                LanguageManager.Language.GREEK -> "Ένα"
                else -> "One"
            }
            0 -> when (language) {
                LanguageManager.Language.BULGARIAN -> "Старт!"
                LanguageManager.Language.GREEK -> "Πάμε!"
                else -> "Go!"
            }
            else -> return
        }
        
        // Update TTS language before speaking
        val locale = LanguageManager.getLocaleForLanguage(language)
        tts?.language = locale
        
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "countdown_$number")
    }
    
    /**
     * Освобождава ресурсите
     */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

