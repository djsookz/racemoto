package com.revix.app.averagespeed

import android.content.Context
import android.util.Log
import androidx.annotation.RawRes
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.revix.app.R
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Bundled official BG TOLL list, with optional Firestore overlay.
 *
 * Firestore: collection `app_config`, document `average_speed_sections`
 * - version (number)
 * - json (string, same schema as the bundled file)
 *
 * Fail-open: missing/invalid remote data keeps the last good local catalog.
 */
object AverageSpeedCatalog {
    private const val TAG = "AvgSpeedCatalog"
    private const val PREFS = "average_speed_catalog"
    private const val KEY_VERSION = "version"
    private const val KEY_JSON = "json"
    private const val COLLECTION = "app_config"
    private const val DOCUMENT = "average_speed_sections"

    private val gson = Gson()
    private val sectionsRef = AtomicReference<List<AverageSpeedSection>>(emptyList())
    private val listeners = CopyOnWriteArrayList<(List<AverageSpeedSection>) -> Unit>()
    @Volatile
    private var loaded = false
    @Volatile
    private var currentVersion = 0

    fun sections(): List<AverageSpeedSection> = sectionsRef.get()

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val app = context.applicationContext
            val bundled = readBundled(app)
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val cachedVersion = prefs.getInt(KEY_VERSION, 0)
            val cachedJson = prefs.getString(KEY_JSON, null)
            val cached = cachedJson?.let { parse(it) }

            val chosen = when {
                cached != null && cachedVersion >= bundled.version -> cached
                else -> bundled
            }
            applyCatalog(chosen)
            loaded = true
        }
        refreshFromRemote(context)
    }

    fun addListener(listener: (List<AverageSpeedSection>) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (List<AverageSpeedSection>) -> Unit) {
        listeners.remove(listener)
    }

    fun refreshFromRemote(context: Context) {
        val app = context.applicationContext
        FirebaseFirestore.getInstance()
            .collection(COLLECTION)
            .document(DOCUMENT)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) return@addOnSuccessListener
                val remoteVersion = snapshot.getLong("version")?.toInt() ?: return@addOnSuccessListener
                if (remoteVersion <= currentVersion) return@addOnSuccessListener
                val json = snapshot.getString("json")?.takeIf { it.isNotBlank() }
                    ?: return@addOnSuccessListener
                val parsed = parse(json) ?: run {
                    Log.w(TAG, "Ignoring invalid remote catalog v$remoteVersion")
                    return@addOnSuccessListener
                }
                if (parsed.sections.isEmpty()) return@addOnSuccessListener
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_VERSION, parsed.version.coerceAtLeast(remoteVersion))
                    .putString(KEY_JSON, json)
                    .apply()
                applyCatalog(parsed.copy(version = parsed.version.coerceAtLeast(remoteVersion)))
                Log.i(TAG, "Updated catalog to v$currentVersion (${parsed.sections.size} sections)")
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Remote catalog refresh failed (keeping local)", error)
            }
    }

    private fun applyCatalog(file: AverageSpeedCatalogFile) {
        val mapped = file.sections.mapNotNull { it.toSection() }
        currentVersion = file.version
        sectionsRef.set(mapped)
        listeners.forEach { listener ->
            try {
                listener(mapped)
            } catch (e: Exception) {
                Log.w(TAG, "Catalog listener failed", e)
            }
        }
    }

    private fun readBundled(context: Context): AverageSpeedCatalogFile {
        return parseRaw(context, R.raw.average_speed_sections) ?: AverageSpeedCatalogFile()
    }

    private fun parseRaw(context: Context, @RawRes resId: Int): AverageSpeedCatalogFile? {
        return try {
            context.resources.openRawResource(resId).bufferedReader().use { parse(it.readText()) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read bundled catalog", e)
            null
        }
    }

    private fun parse(json: String): AverageSpeedCatalogFile? {
        return try {
            val parsed = gson.fromJson(json, AverageSpeedCatalogFile::class.java) ?: return null
            if (parsed.sections.isEmpty()) return null
            parsed
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse catalog JSON", e)
            null
        }
    }
}
