package com.example.clinometer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.TransitionDrawable
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.clinometer.settings.LanguageManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout

class StartActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    private val PERMISSION_REQUEST_CODE = 1001
    private lateinit var spinnerProfiles: MaterialAutoCompleteTextView
    private lateinit var profileDropdownLayout: TextInputLayout
    private lateinit var backgroundImageView: ImageView
    private lateinit var btnSettings: MaterialButton
    private var profiles = mutableListOf<Profile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        btnSettings = findViewById(R.id.btnSettings)

        backgroundImageView = findViewById(R.id.backgroundImageView)

        profileDropdownLayout = findViewById(R.id.profileDropdownLayout)

        if (ProfileStorage.loadProfiles(this).isEmpty()) {
            startActivity(Intent(this, FirstProfileActivity::class.java))
            finish()
            return
        }

        spinnerProfiles = findViewById(R.id.spinnerProfiles)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnRaces = findViewById<Button>(R.id.btnRaces)
        val btnProfiles = findViewById<Button>(R.id.btnProfiles)

        loadProfiles()
        if (profiles.isEmpty()) {
            startActivity(Intent(this, FirstProfileActivity::class.java))
            finish()
            return
        }
        setupProfileSpinner()

        btnStart.setOnClickListener {
            val selectedProfileName = spinnerProfiles.text.toString()
            val selectedIndex = profiles.indexOfFirst { it.name == selectedProfileName }

            if (selectedIndex >= 0) {
                val selectedProfile = profiles[selectedIndex]
                val intent = Intent(this, CountdownActivity::class.java).apply {
                    putExtra("SELECTED_PROFILE", selectedProfile)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please, chose a profile", Toast.LENGTH_SHORT).show()
            }
        }

        btnRaces.setOnClickListener {
            startActivity(Intent(this, RacesActivity::class.java))
        }

        btnProfiles.setOnClickListener {
            startActivity(Intent(this, GarageActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }


    override fun onResume() {
        super.onResume()
        loadProfiles()

        if (profiles.isEmpty()) {
            startActivity(Intent(this, FirstProfileActivity::class.java))
            finish()
            return
        }

        setupProfileSpinner()
    }

    private fun loadProfiles() {
        profiles.clear()
        profiles.addAll(ProfileStorage.loadProfiles(this))
    }

    private fun setupProfileSpinner() {
        val profileNames = profiles.map { it.name }

        val adapter = ArrayAdapter(
            this,
            R.layout.dropdown_item,
            profileNames
        )

        spinnerProfiles.setAdapter(adapter)
        if (profiles.isNotEmpty()) {
            val selectedProfileId = ProfileStorage.getSelectedProfileId(this)
            val selectedProfile = profiles.find { it.id == selectedProfileId }

            selectedProfile?.let {
                spinnerProfiles.setText(it.name, false)
                updateProfileIcon(it)
                updateBackground(it)
            } ?: run {
                val firstProfile = profiles.first()
                spinnerProfiles.setText(firstProfile.name, false)
                ProfileStorage.saveSelectedProfile(this, firstProfile.id)
                updateProfileIcon(firstProfile)
                updateBackground(firstProfile)
            }
        }

        spinnerProfiles.setOnItemClickListener { _, _, position, _ ->
            val selectedProfile = profiles[position]
            ProfileStorage.saveSelectedProfile(this, selectedProfile.id)
            updateProfileIcon(selectedProfile)
            updateBackground(selectedProfile)
        }
    }

    private fun updateProfileIcon(profile: Profile) {
        val iconRes = when (profile.vehicleType) {
            Profile.VehicleType.CAR -> R.drawable.ic_car
            Profile.VehicleType.MOTORCYCLE -> R.drawable.ic_motorcycle
        }
        profileDropdownLayout.startIconDrawable = ContextCompat.getDrawable(this, iconRes)
    }

    class CrossfadeTransition(private val imageView: ImageView, private val duration: Int) {
        fun start(newImageRes: Int) {
            val newDrawable = ContextCompat.getDrawable(imageView.context, newImageRes)

            if (newDrawable != null) {
                val transition = TransitionDrawable(
                    arrayOf(imageView.drawable, newDrawable)
                )

                imageView.setImageDrawable(transition)
                transition.startTransition(duration)
            }
        }
    }

    private fun updateBackground(profile: Profile) {
        val backgroundRes = when (profile.vehicleType) {
            Profile.VehicleType.CAR -> R.drawable.car_background
            Profile.VehicleType.MOTORCYCLE -> R.drawable.motorcycle_background
        }
        val crossFade = CrossfadeTransition(backgroundImageView, 500)
        crossFade.start(backgroundRes)
    }

    override fun onBackPressed() {
        finishAffinity()
        System.exit(0)
    }
}