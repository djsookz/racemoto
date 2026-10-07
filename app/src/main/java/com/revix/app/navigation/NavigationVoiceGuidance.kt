package com.revix.app.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.revix.app.audio.AppAudioFocus
import com.revix.app.settings.LanguageManager
import com.revix.app.settings.VoiceAlertsSettings
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.trip.session.VoiceInstructionsObserver
import java.text.Normalizer
import java.util.Locale

/**
 * Keeps turn-by-turn voice guidance alive while navigation runs, even if the map UI
 * fragment is paused (screen locked or user switched apps).
 */
object NavigationVoiceGuidance {
    private const val TAG = "NavigationVoiceGuidance"

    @Volatile
    private var navigationActive = false

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var appContext: Context? = null
    private var pendingAnnouncement: String? = null
    private var lastAnnouncement: String? = null
    private var lastAnnouncementTimestampMs = 0L
    private var lastSpokenVoiceAnnouncement: String? = null
    private var registeredNavigation: MapboxNavigation? = null

    @Volatile
    private var reportAlertActive = false
    private var navSpeechFocusHeld = false

    private val voiceInstructionsObserver = VoiceInstructionsObserver { voiceInstructions ->
        if (!navigationActive || reportAlertActive) return@VoiceInstructionsObserver

        val rawAnnouncement = voiceInstructions.announcement()?.trim().orEmpty()
            .ifBlank {
                stripMarkup(voiceInstructions.ssmlAnnouncement()?.trim().orEmpty())
            }
        val announcement = normalizeAnnouncementForSpeech(rawAnnouncement)
        if (announcement.isBlank()) return@VoiceInstructionsObserver

        if (rawAnnouncement != announcement) {
            Log.i(TAG, "Voice normalized: [$rawAnnouncement] -> [$announcement]")
        } else {
            Log.d(TAG, "Voice raw (unchanged): [$rawAnnouncement]")
        }

        val now = SystemClock.elapsedRealtime()
        if (announcement == lastSpokenVoiceAnnouncement) {
            return@VoiceInstructionsObserver
        }
        val isDuplicate =
            announcement == lastAnnouncement &&
                now - lastAnnouncementTimestampMs < 4000L
        if (isDuplicate) return@VoiceInstructionsObserver

        lastAnnouncement = announcement
        lastAnnouncementTimestampMs = now
        lastSpokenVoiceAnnouncement = announcement
        speakInternal(announcement)
    }

    fun isRunning(): Boolean = navigationActive

    @SuppressLint("MissingPermission")
    fun start(context: Context, mapboxNavigation: MapboxNavigation) {
        navigationActive = true
        appContext = context.applicationContext
        initTts(context.applicationContext)

        if (registeredNavigation != mapboxNavigation) {
            registeredNavigation?.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
            mapboxNavigation.registerVoiceInstructionsObserver(voiceInstructionsObserver)
            registeredNavigation = mapboxNavigation
        }

        try {
            mapboxNavigation.startTripSession(withForegroundService = true)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start Mapbox foreground trip session", e)
            mapboxNavigation.startTripSession(withForegroundService = false)
        }
    }

    @SuppressLint("MissingPermission")
    fun stop(context: Context, mapboxNavigation: MapboxNavigation) {
        navigationActive = false
        mapboxNavigation.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
        if (registeredNavigation == mapboxNavigation) {
            registeredNavigation = null
        }
        stopPlayback()
        resetDuplicateState()
        reportAlertActive = false

        // End Navigation SDK billing session entirely. Do NOT fall back to Free Drive
        // (that still counts as a Navigation MAU / Free Drive trip).
        try {
            mapboxNavigation.stopTripSession()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to stop Mapbox trip session", e)
        }
    }

    fun speakManual(context: Context, rawText: String) {
        if (!navigationActive) return
        speakAlert(context, rawText)
    }

    /**
     * Spoken alert for navigation-only map features (average-speed sections).
     * Report alerts still take priority. Follow-line / free-ride should not call this.
     */
    fun speakAlert(context: Context, rawText: String) {
        if (reportAlertActive || !VoiceAlertsSettings.isEnabled(context)) return
        appContext = context.applicationContext
        initTts(context.applicationContext)

        val text = normalizeAnnouncementForSpeech(rawText)
        if (text.isBlank()) return

        val now = SystemClock.elapsedRealtime()
        val isDuplicate =
            text == lastAnnouncement &&
                now - lastAnnouncementTimestampMs < 4000L
        if (isDuplicate) return

        lastAnnouncement = text
        lastAnnouncementTimestampMs = now
        lastSpokenVoiceAnnouncement = text
        speakInternal(text)
    }

    fun stopPlayback() {
        tts?.stop()
        pendingAnnouncement = null
        lastAnnouncement = null
        lastAnnouncementTimestampMs = 0L
        releaseNavSpeechFocus()
    }

    /** Report map alerts take priority over turn-by-turn navigation voice. */
    fun yieldToReportAlert() {
        reportAlertActive = true
        stopPlayback()
    }

    fun onReportAlertFinished() {
        reportAlertActive = false
    }

    fun isReportAlertActive(): Boolean = reportAlertActive

    fun resetDuplicateState() {
        lastSpokenVoiceAnnouncement = null
        lastAnnouncement = null
        lastAnnouncementTimestampMs = 0L
    }

    fun shutdown() {
        navigationActive = false
        registeredNavigation?.unregisterVoiceInstructionsObserver(voiceInstructionsObserver)
        registeredNavigation = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        pendingAnnouncement = null
        resetDuplicateState()
        reportAlertActive = false
        releaseNavSpeechFocus()
    }

    private fun releaseNavSpeechFocus() {
        if (!navSpeechFocusHeld) return
        navSpeechFocusHeld = false
        AppAudioFocus.abandon()
    }

    private fun initTts(appContext: Context) {
        if (tts != null) {
            if (ttsReady) {
                applyTtsLanguage(appContext)
            }
            return
        }

        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                configureTtsAudioAttributes()
                installUtteranceListener()
                applyTtsLanguage(appContext)
                pendingAnnouncement?.let { queued ->
                    pendingAnnouncement = null
                    speakInternal(queued)
                }
            } else {
                ttsReady = false
                Log.e(TAG, "Navigation TTS init failed: status=$status")
            }
        }
    }

    private fun installUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                releaseNavSpeechFocus()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                releaseNavSpeechFocus()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                releaseNavSpeechFocus()
            }
        })
    }

    private fun configureTtsAudioAttributes() {
        val engine = tts ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }
    }

    private fun applyTtsLanguage(context: Context) {
        val engine = tts ?: return
        val selectedLanguage = LanguageManager.getLanguage(context)
        val preferredLocale = LanguageManager.getLocaleForLanguage(selectedLanguage)
        var result = engine.setLanguage(preferredLocale)

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = engine.setLanguage(Locale.ENGLISH)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.getDefault())
            }
        }
    }

    private fun speakInternal(text: String) {
        val context = appContext ?: return
        if (reportAlertActive || !VoiceAlertsSettings.isEnabled(context)) return

        initTts(context)
        if (!ttsReady) {
            pendingAnnouncement = text
            return
        }
        pendingAnnouncement = null

        applyTtsLanguage(context)
        if (!navSpeechFocusHeld) {
            AppAudioFocus.request(context)
            navSpeechFocusHeld = true
        }
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "nav_tts_${SystemClock.elapsedRealtime()}"
        )
    }

    private fun normalizeAnnouncementForSpeech(rawText: String): String {
        if (rawText.isBlank()) return rawText

        var text = stripMarkup(rawText)
            .replace(Regex("(?iu)\\bбул\\.\\s*"), "Булевард ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Mapbox mangles BG/EL roundabout exit ordinals. Fix only exit phrases for TTS.
        val language = appContext?.let { LanguageManager.getLanguage(it) }
        val foldedProbe = foldGreekForMatch(text)
        val looksLikeGreekExit =
            foldedProbe.contains("ξοδ") &&
                (foldedProbe.contains("την") || foldedProbe.contains(" τη ") ||
                    foldedProbe.contains(" η ") || Regex("""\d\s*η""").containsMatchIn(foldedProbe))

        text = when {
            looksLikeGreekExit || language == LanguageManager.Language.GREEK ->
                fixGreekRoundaboutExits(text)
            language == LanguageManager.Language.BULGARIAN ||
                text.contains(Regex("[А-Яа-яЁё]")) ->
                fixBulgarianRoundaboutExits(text)
            else -> text
        }

        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun stripMarkup(value: String): String {
        if (value.isBlank()) return value
        return value
            .replace(Regex("(?is)<[^>]+>"), " ")
            .replace(Regex("(?i)&nbsp;"), " ")
            .replace("&amp;", " ")
            .replace('\u00A0', ' ')
            .replace('\u200b', ' ')
    }

    private fun fixBulgarianRoundaboutExits(text: String): String {
        var result = text
        // Known broken Mapbox token for "2nd exit"
        result = result.replace(Regex("(?iu)\\bдверия\\b"), "втория изход")
        result = result.replace(Regex("(?iu)\\bтририя\\b"), "третия изход")
        result = result.replace(Regex("(?iu)\\bчетирия\\b"), "четвъртия изход")

        // "2 изход" / "2-ри изход" / "3-а изход" / "2рия изход"
        result = result.replace(
            Regex("(?iu)\\b(\\d{1,2})\\s*[-‑.]?\\s*(?:а|я|ви|ва|ри|рия|ти|тия|ми|мия)?\\s*изход(?:а|ът)?\\b")
        ) { match ->
            bulgarianExitPhrase(match.groupValues[1].toIntOrNull()) ?: match.value
        }

        // "две изход" / "два изход" / "три изход"
        result = result.replace(
            Regex(
                "(?iu)\\b(един|едно|първи|две|два|втори|три|трети|четири|четвърти|" +
                    "пет|пети|шест|шести|седем|седми|осем|осми|девет|девети|десет|десети)" +
                    "\\s+изход(?:а|ът)?\\b"
            )
        ) { match ->
            bulgarianExitPhrase(bulgarianCardinalToNumber(match.groupValues[1])) ?: match.value
        }

        // Avoid "втория изход изход" after replacements
        result = result.replace(Regex("(?iu)\\b(изход(?:а|ът)?)\\s+\\1\\b"), "$1")
        return result
    }

    /**
     * Match on accent-stripped Greek so "ένα/ενα", "έξοδο/εξοδο" all hit the same patterns.
     * Only the exit clause is rewritten to a correct accented phrase for TTS.
     */
    private fun fixGreekRoundaboutExits(text: String): String {
        val cleaned = stripMarkup(text).replace(Regex("\\s+"), " ").trim()
        var folded = foldGreekForMatch(cleaned)
        if (!folded.contains("ξοδ")) return cleaned

        val before = folded
        val token = """\d{1,2}|ενα|μια|δυο|τρια|τρεις|τεσσερα|τεσσερις|πεντε|εξι|επτα|εφτα|οκτω|οχτω|εννεα|εννια|δεκα"""

        fun exitReplacement(tokenValue: String): String? {
            val number = resolveGreekExitNumber(tokenValue) ?: return null
            return greekExitPhrase(number)?.let { "την $it" }
        }

        // "την ενα η εξοδο" / "τη δυο η εξοδο"  (the exact Mapbox form users hear)
        folded = Regex("""(την|τη)\s+($token)\s+η\s+\S*ξοδ\S*""").replace(folded) { match ->
            exitReplacement(match.groupValues[2]) ?: match.value
        }

        // "ενα η εξοδο" / "2 η εξοδο"
        folded = Regex("""(^|[\s,.;:])($token)\s+η\s+\S*ξοδ\S*""").replace(folded) { match ->
            val replacement = exitReplacement(match.groupValues[2]) ?: return@replace match.value
            match.groupValues[1] + replacement
        }

        // "την 2η εξοδο" / "2η εξοδο"
        folded = Regex("""(την|τη)\s+(\d{1,2})η\s*\S*ξοδ\S*""").replace(folded) { match ->
            exitReplacement(match.groupValues[2]) ?: match.value
        }
        folded = Regex("""(^|[\s,.;:])(\d{1,2})η\s*\S*ξοδ\S*""").replace(folded) { match ->
            val replacement = exitReplacement(match.groupValues[2]) ?: return@replace match.value
            match.groupValues[1] + replacement
        }

        // "την 2 εξοδο" / "εξοδο 3"
        folded = Regex("""(την|τη)\s+(\d{1,2})\s+\S*ξοδ\S*""").replace(folded) { match ->
            exitReplacement(match.groupValues[2]) ?: match.value
        }
        folded = Regex("""\S*ξοδ\S*\s+(?:αριθμοσ?\s+)?(\d{1,2})(?!\d)""").replace(folded) { match ->
            exitReplacement(match.groupValues[1]) ?: match.value
        }

        folded = folded.replace(Regex("""την\s+την\s+"""), "την ")

        return if (folded != before) {
            folded.replace(Regex("\\s+"), " ").trim()
        } else {
            cleaned
        }
    }

    private fun resolveGreekExitNumber(token: String): Int? {
        token.trim().toIntOrNull()?.let { return it }
        return greekTokenToNumber(token)
    }

    private fun foldGreekForMatch(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
    }

    private fun bulgarianExitPhrase(number: Int?): String? = when (number) {
        1 -> "първия изход"
        2 -> "втория изход"
        3 -> "третия изход"
        4 -> "четвъртия изход"
        5 -> "петия изход"
        6 -> "шестия изход"
        7 -> "седмия изход"
        8 -> "осмия изход"
        9 -> "деветия изход"
        10 -> "десетия изход"
        else -> null
    }

    private fun greekExitPhrase(number: Int?): String? = when (number) {
        1 -> "πρώτη έξοδο"
        2 -> "δεύτερη έξοδο"
        3 -> "τρίτη έξοδο"
        4 -> "τέταρτη έξοδο"
        5 -> "πέμπτη έξοδο"
        6 -> "έκτη έξοδο"
        7 -> "έβδομη έξοδο"
        8 -> "όγδοη έξοδο"
        9 -> "ένατη έξοδο"
        10 -> "δέκατη έξοδο"
        else -> null
    }

    private fun bulgarianCardinalToNumber(token: String): Int? = when (token.lowercase(Locale("bg"))) {
        "един", "едно", "първи" -> 1
        "две", "два", "втори" -> 2
        "три", "трети" -> 3
        "четири", "четвърти" -> 4
        "пет", "пети" -> 5
        "шест", "шести" -> 6
        "седем", "седми" -> 7
        "осем", "осми" -> 8
        "девет", "девети" -> 9
        "десет", "десети" -> 10
        else -> null
    }

    private fun greekTokenToNumber(token: String): Int? {
        token.trim().toIntOrNull()?.let { return it }
        return when (foldGreekForMatch(token)) {
            "ενα", "μια" -> 1
            "δυο" -> 2
            "τρια", "τρεις" -> 3
            "τεσσερα", "τεσσερις" -> 4
            "πεντε" -> 5
            "εξι" -> 6
            "επτα", "εφτα" -> 7
            "οκτω", "οχτω" -> 8
            "εννεα", "εννια" -> 9
            "δεκα" -> 10
            else -> null
        }
    }
}
