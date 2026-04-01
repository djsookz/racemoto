package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.clinometer.track.CustomTrackBuilderActivity
import com.example.clinometer.track.custom.CustomTrack
import com.google.android.material.button.MaterialButton

class TrackTypeSelectionActivity : AppCompatActivity() {
    
    private lateinit var btnCircuit: androidx.cardview.widget.CardView
    private lateinit var btnPointToPoint: androidx.cardview.widget.CardView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_type_selection)
        applySystemBarsPaddingToRoot()
        
        initializeViews()
        setupClickListeners()
    }
    
    private fun initializeViews() {
        btnCircuit = findViewById(R.id.btnCircuit)
        btnPointToPoint = findViewById(R.id.btnPointToPoint)
        
        // Back button
        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupClickListeners() {
        btnCircuit.setOnClickListener {
            startCustomTrackBuilder(CustomTrack.TrackType.CIRCUIT)
        }
        
        btnPointToPoint.setOnClickListener {
            startCustomTrackBuilder(CustomTrack.TrackType.POINT_TO_POINT)
        }
    }
    
    private fun startCustomTrackBuilder(trackType: CustomTrack.TrackType) {
        val intent = Intent(this, CustomTrackBuilderActivity::class.java).apply {
            putExtra("track_type", trackType.name)
        }
        startActivity(intent)
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, 0)
    }
}
