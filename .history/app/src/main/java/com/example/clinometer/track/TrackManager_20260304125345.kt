package com.example.clinometer

import android.content.Context
import com.example.clinometer.track.catalog.OfficialTrackCatalog
import com.example.clinometer.track.catalog.TrackDefinition

class TrackManager(private val context: Context) {
    
    private val gpxParser = GPXParser(context)
    
    fun getAllTracks(): List<TrackDefinition> = OfficialTrackCatalog.getAll()
    
    fun getTrackById(id: String): TrackDefinition? = OfficialTrackCatalog.getById(id)

    fun getTrackDefinition(id: String): TrackDefinition? = getTrackById(id)
    
    fun loadTrackData(trackId: String): TrackData? {
        val track = getTrackById(trackId) ?: return null
        val gpxResourceId = track.gpxResourceId ?: return null
        return gpxParser.parseTrack(gpxResourceId)
    }
}
