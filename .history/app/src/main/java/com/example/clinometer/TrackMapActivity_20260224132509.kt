package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import androidx.preference.PreferenceManager

class TrackMapActivity : AppCompatActivity() {
    
    private lateinit var map: MapView
    private lateinit var tvTrackTitle: TextView
    private lateinit var trackManager: TrackManager
    private var trackId: String = ""
    private var trackName: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(
            applicationContext,
            PreferenceManager.getDefaultSharedPreferences(applicationContext)
        )
        
        // Default OSMDroid configuration
        setContentView(R.layout.activity_track_map)
        applySystemBarsPaddingToRoot()
        
        // Get track data from intent
        trackId = intent.getStringExtra("track_id") ?: ""
        trackName = intent.getStringExtra("track_name") ?: "Track"
        
        initializeViews()
        setupClickListeners()
        setupMap()
    }
    
    private fun initializeViews() {
        map = findViewById(R.id.mapTrack)
        tvTrackTitle = findViewById(R.id.tvTrackTitle)
        trackManager = TrackManager(this)
    }
    
    private fun setupClickListeners() {
        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        
        // Set track title
        tvTrackTitle.text = trackName
        
        // Load and display track
        if (trackId.isNotEmpty()) {
            trackManager.setupTrackOnMap(map, trackId)
        }
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, 0)
    }
}
