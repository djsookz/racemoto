package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class TrackSelectionActivity : AppCompatActivity() {

    private lateinit var cardSerresCircuit: CardView
    private lateinit var cardCustomTrack: CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_selection)

        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        cardSerresCircuit = findViewById(R.id.cardSerresCircuit)
        cardCustomTrack = findViewById(R.id.cardCustomTrack)
    }

    private fun setupClickListeners() {
        cardSerresCircuit.setOnClickListener {
            startTrackSession("serres_circuit", getString(R.string.track_name_serres))
        }

        cardCustomTrack.setOnClickListener {
            startCustomTrackSession()
        }
    }

    private fun startTrackSession(trackId: String, trackName: String) {
        android.util.Log.d("TrackSelectionActivity", "🆕 Starting NEW SESSION: trackId=$trackId, trackName=$trackName")
        
        val intent = Intent(this, TrackSessionActivity::class.java).apply {
            putExtra("track_id", trackId)
            putExtra("track_name", trackName)
            putExtra("resume_session", false) // ✅ NEW SESSION FLAG
            val currentProfileId = ProfileStorage.getSelectedProfileId(this@TrackSelectionActivity)
            val profiles = ProfileStorage.loadProfiles(this@TrackSelectionActivity)
            val profile = profiles.find { it.id == currentProfileId }
            val isMotorcycle = profile?.vehicleType == Profile.VehicleType.MOTORCYCLE
            putExtra("is_motorcycle", isMotorcycle)
        }
        
        android.util.Log.d("TrackSelectionActivity", "🆕 Starting TrackSessionActivity with resume_session=false")
        
        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private fun startCustomTrackSession() {
        // TODO: Implement custom track creation
        showToast(getString(R.string.track_custom_soon))
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
