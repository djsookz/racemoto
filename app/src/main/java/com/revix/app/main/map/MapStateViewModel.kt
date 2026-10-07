package com.revix.app.main.map

import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import com.mapbox.navigation.base.route.NavigationRoute

/**
 * Survives MapFragment view / pager destroy when scoped to the Activity.
 * Also persists last camera + location so cold start never flashes the globe.
 */
class MapStateViewModel : ViewModel() {

    var hasInitializedCamera: Boolean = false

    var lastMapState: MapState? = null

    var lastKnownLocation: Location? = null

    /**
     * Last spoken voice instruction. Kept in the ViewModel so orientation changes
     * do not replay the same announcement.
     */
    var lastSpokenVoiceAnnouncement: String? = null

    /** Survives orientation recreate so follow-line can redraw the same route. */
    var followLineRoute: NavigationRoute? = null

    fun saveMapState(centerLat: Double, centerLon: Double, zoom: Double, pitch: Double? = null) {
        lastMapState = MapState(
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = zoom,
            pitch = pitch
        )
    }

    fun saveLastLocation(location: Location) {
        lastKnownLocation = location
    }

    fun clearMapState() {
        lastMapState = null
        hasInitializedCamera = false
    }

    fun hydrate(context: Context) {
        if (lastMapState != null || lastKnownLocation != null) return
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        if (!prefs.contains(PREF_LAT) || !prefs.contains(PREF_LON)) return

        val lat = prefs.getFloat(PREF_LAT, 0f).toDouble()
        val lon = prefs.getFloat(PREF_LON, 0f).toDouble()
        if (lat == 0.0 && lon == 0.0) return
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return

        val zoom = prefs.getFloat(PREF_ZOOM, DEFAULT_IDLE_ZOOM.toFloat()).toDouble()
            .coerceIn(MIN_RESTORE_ZOOM, MAX_RESTORE_ZOOM)
        val pitch = prefs.getFloat(PREF_PITCH, 0f).toDouble().coerceIn(0.0, 60.0)

        lastMapState = MapState(lat, lon, zoom, pitch)
        lastKnownLocation = Location("persisted").apply {
            latitude = lat
            longitude = lon
        }
    }

    fun persist(context: Context) {
        val state = lastMapState
        val loc = lastKnownLocation
        val lat = state?.centerLat ?: loc?.latitude ?: return
        val lon = state?.centerLon ?: loc?.longitude ?: return
        val zoom = (state?.zoom ?: DEFAULT_IDLE_ZOOM).coerceIn(MIN_RESTORE_ZOOM, MAX_RESTORE_ZOOM)
        val pitch = (state?.pitch ?: 0.0).coerceIn(0.0, 60.0)

        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putFloat(PREF_LAT, lat.toFloat())
            .putFloat(PREF_LON, lon.toFloat())
            .putFloat(PREF_ZOOM, zoom.toFloat())
            .putFloat(PREF_PITCH, pitch.toFloat())
            .apply()
    }

    data class MapState(
        val centerLat: Double,
        val centerLon: Double,
        val zoom: Double,
        val pitch: Double? = null
    )

    companion object {
        private const val PREF_LAT = "map_camera_last_lat"
        private const val PREF_LON = "map_camera_last_lon"
        private const val PREF_ZOOM = "map_camera_last_zoom"
        private const val PREF_PITCH = "map_camera_last_pitch"
        const val DEFAULT_IDLE_ZOOM = 14.5
        private const val MIN_RESTORE_ZOOM = 11.0
        private const val MAX_RESTORE_ZOOM = 19.5
    }
}
