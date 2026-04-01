package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.track.catalog.OfficialTrackCatalog
import com.example.clinometer.track.catalog.TrackDefinition
import com.example.clinometer.tracking.CustomTrack
import com.example.clinometer.tracking.CustomTrackStorage
import com.google.android.material.button.MaterialButton

class TrackSelectionActivity : AppCompatActivity() {
    
    private lateinit var rvOfficialTracks: RecyclerView
    private lateinit var rvCustomTracks: RecyclerView
    private lateinit var btnCreateCustom: MaterialButton
    private lateinit var tvNoCustomTracks: TextView
    private lateinit var llCustomTracksSection: LinearLayout
    
    private val officialTracks: List<TrackDefinition> = OfficialTrackCatalog.getAll()
    
    private var customTracks = mutableListOf<CustomTrack>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_selection)
        
        initializeViews()
        setupClickListeners()
        loadCustomTracks()
        setupRecyclerViews()
    }
    
    private fun initializeViews() {
        rvOfficialTracks = findViewById(R.id.rvOfficialTracks)
        rvCustomTracks = findViewById(R.id.rvCustomTracks)
        btnCreateCustom = findViewById(R.id.btnCreateCustom)
        tvNoCustomTracks = findViewById(R.id.tvNoCustomTracks)
        llCustomTracksSection = findViewById(R.id.llCustomTracksSection)
        
        // Back button
        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupClickListeners() {
        btnCreateCustom.setOnClickListener {
            // Navigate to track type selection
            val intent = Intent(this, TrackTypeSelectionActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun loadCustomTracks() {
        customTracks.clear()
        customTracks.addAll(CustomTrackStorage.loadCustomTracks(this))
        updateCustomTracksVisibility()
    }
    
    private fun updateCustomTracksVisibility() {
        if (customTracks.isEmpty()) {
            tvNoCustomTracks.visibility = View.VISIBLE
            rvCustomTracks.visibility = View.GONE
        } else {
            tvNoCustomTracks.visibility = View.GONE
            rvCustomTracks.visibility = View.VISIBLE
        }
    }
    
    private fun setupRecyclerViews() {
        // Official tracks adapter
        val officialAdapter = TrackSelectionAdapter(
            officialTracks.map { TrackSelectionAdapter.TrackItem.Official(it) },
            onTrackSelected = { trackItem ->
                when (trackItem) {
                    is TrackSelectionAdapter.TrackItem.Official -> {
                        startTrackSession(trackItem.track.id, trackItem.track.name, true)
                    }
                    is TrackSelectionAdapter.TrackItem.Custom -> {
                        startTrackSession(trackItem.track.id, trackItem.track.name, false)
                    }
                }
            },
            onTrackDeleted = { /* No delete for official tracks */ }
        )
        rvOfficialTracks.layoutManager = LinearLayoutManager(this)
        rvOfficialTracks.adapter = officialAdapter
        
        // Custom tracks adapter
        val customAdapter = TrackSelectionAdapter(
            customTracks.map { TrackSelectionAdapter.TrackItem.Custom(it) },
            onTrackSelected = { trackItem ->
                when (trackItem) {
                    is TrackSelectionAdapter.TrackItem.Official -> {
                        startTrackSession(trackItem.track.id, trackItem.track.name, true)
                    }
                    is TrackSelectionAdapter.TrackItem.Custom -> {
                        startTrackSession(trackItem.track.id, trackItem.track.name, false)
                    }
                }
            },
            onTrackDeleted = { customTrack ->
                deleteCustomTrack(customTrack)
            }
        )
        rvCustomTracks.layoutManager = LinearLayoutManager(this)
        rvCustomTracks.adapter = customAdapter
    }
    
    private fun deleteCustomTrack(customTrack: com.example.clinometer.tracking.CustomTrack) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Изтриване на писта")
            .setMessage("Сигурни ли сте, че искате да изтриете пистата '${customTrack.name}'?")
            .setPositiveButton("Да") { _, _ ->
                com.example.clinometer.tracking.CustomTrackStorage.deleteCustomTrack(this, customTrack.id)
                loadCustomTracks()
                setupRecyclerViews()
                updateCustomTracksVisibility()
                android.widget.Toast.makeText(this, "Пистата е изтрита", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отказ", null)
            .show()
    }
    
    private fun startTrackSession(trackId: String, trackName: String, isOfficial: Boolean) {
        val intent = Intent(this, TrackSessionActivity::class.java).apply {
            putExtra("track_id", trackId)
            putExtra("track_name", trackName)
            putExtra("is_official", isOfficial)
            putExtra("is_motorcycle", true) // Default to motorcycle, can be made dynamic
        }
        startActivity(intent)
        finish()
    }
    
    override fun onResume() {
        super.onResume()
        loadCustomTracks()
        setupRecyclerViews()
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, 0)
    }
}
