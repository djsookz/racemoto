package com.revix.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Temporarily ducks other apps' audio (music, etc.) while REVIX speaks or plays guidance sounds.
 */
object AppAudioFocus {
    private const val TAG = "AppAudioFocus"

    private val lock = Any()
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var holders = 0

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { /* no-op */ }

    private val guidanceAttributes: AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    /** Request transient ducking focus. Safe to call nested (ref-counted). */
    fun request(context: Context) {
        synchronized(lock) {
            holders++
            if (holders > 1) return

            val am = context.applicationContext
                .getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager = am

            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(guidanceAttributes)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .setWillPauseWhenDucked(false)
                    .build()
                focusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }

            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "Audio focus not granted (result=$result)")
                holders = 0
                focusRequest = null
            }
        }
    }

    /** Release one hold; abandons system focus when the last holder finishes. */
    fun abandon() {
        synchronized(lock) {
            if (holders <= 0) return
            holders--
            if (holders > 0) return

            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(focusChangeListener)
            }
            focusRequest = null
        }
    }

    fun abandonAll() {
        synchronized(lock) {
            if (holders <= 0 && focusRequest == null) return
            holders = 1
            abandon()
        }
    }
}
