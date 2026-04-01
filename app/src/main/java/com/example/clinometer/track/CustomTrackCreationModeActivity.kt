package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CustomTrackCreationModeActivity : AppCompatActivity() {

    private lateinit var btnPhoneMode: androidx.cardview.widget.CardView
    private lateinit var btnDrivingMode: androidx.cardview.widget.CardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_track_creation_mode)
        applySystemBarsPaddingToRoot()

        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        btnPhoneMode = findViewById(R.id.btnPhoneMode)
        btnDrivingMode = findViewById(R.id.btnDrivingMode)

        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupClickListeners() {
        btnPhoneMode.setOnClickListener {
            openTrackTypeSelection("PHONE")
        }

        btnDrivingMode.setOnClickListener {
            openTrackTypeSelection("DRIVING")
        }
    }

    private fun openTrackTypeSelection(creationMode: String) {
        val intent = Intent(this, TrackTypeSelectionActivity::class.java).apply {
            putExtra("creation_mode", creationMode)
        }
        startActivity(intent)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(0, 0)
    }
}
