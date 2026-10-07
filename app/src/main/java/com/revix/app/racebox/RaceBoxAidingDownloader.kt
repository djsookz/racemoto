package com.revix.app.racebox

import android.content.Context
import android.util.Log
import com.revix.app.BuildConfig
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Downloads a pre-built UBX-MGA aiding blob (from free IGS/BKG RINEX via CI) for RaceBox inject.
 */
internal object RaceBoxAidingDownloader {
    private const val TAG = "RaceBoxAiding"
    private const val CACHE_FILE = "racebox_aiding.ubx"
    private const val CACHE_META = "racebox_aiding_fetched_at"
    private val maxCacheAgeMs = TimeUnit.HOURS.toMillis(3)

    data class Result(
        val bytes: ByteArray,
        val fromCache: Boolean,
        val url: String
    )

    fun resolveUrl(): String {
        val override = BuildConfig.RACEBOX_AIDING_URL.trim()
        return override.ifBlank {
            "https://raw.githubusercontent.com/djsookz/racemoto/racebox-aiding/aiding.ubx"
        }
    }

    fun load(context: Context, forceRefresh: Boolean = false): Result {
        val url = resolveUrl()
        val cacheFile = File(context.cacheDir, CACHE_FILE)
        val prefs = context.getSharedPreferences("racebox_aiding", Context.MODE_PRIVATE)
        val fetchedAt = prefs.getLong(CACHE_META, 0L)
        val ageOk = fetchedAt > 0L && (System.currentTimeMillis() - fetchedAt) < maxCacheAgeMs

        if (!forceRefresh && ageOk && cacheFile.isFile && cacheFile.length() >= 8L) {
            val cached = cacheFile.readBytes()
            if (looksLikeUbx(cached)) {
                Log.i(TAG, "Using cached aiding ${cached.size} bytes age=${System.currentTimeMillis() - fetchedAt}ms")
                return Result(bytes = cached, fromCache = true, url = url)
            }
        }

        val downloaded = httpGetBinary(url)
        if (!looksLikeUbx(downloaded)) {
            throw IllegalStateException("Aiding URL did not return UBX data")
        }
        cacheFile.writeBytes(downloaded)
        prefs.edit().putLong(CACHE_META, System.currentTimeMillis()).apply()
        Log.i(TAG, "Downloaded aiding ${downloaded.size} bytes from $url")
        return Result(bytes = downloaded, fromCache = false, url = url)
    }

    private fun looksLikeUbx(bytes: ByteArray): Boolean {
        return bytes.size >= 8 &&
            bytes[0] == 0xB5.toByte() &&
            bytes[1] == 0x62.toByte()
    }

    private fun httpGetBinary(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 45_000
            setRequestProperty("Accept", "application/octet-stream,*/*")
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.let { BufferedInputStream(it).readBytes() } ?: ByteArray(0)
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code fetching aiding")
            }
            return bytes
        } finally {
            conn.disconnect()
        }
    }
}
