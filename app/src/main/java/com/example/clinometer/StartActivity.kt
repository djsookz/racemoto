package com.example.clinometer

import android.Manifest
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout

class StartActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 1001
    private lateinit var spinnerProfiles: MaterialAutoCompleteTextView
    private lateinit var profileDropdownLayout: TextInputLayout
    private lateinit var backgroundImageView: ImageView // Ново: Референция към фоновото изображение
    private lateinit var btnSettings: MaterialButton
    private var profiles = mutableListOf<Profile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)

        btnSettings = findViewById(R.id.btnSettings)


        // Инициализация на фоновото изображение
        backgroundImageView = findViewById(R.id.backgroundImageView)

        // Инициализация на TextInputLayout за иконките
        profileDropdownLayout = findViewById(R.id.profileDropdownLayout)

        if (ProfileStorage.loadProfiles(this).isEmpty()) {
            startActivity(Intent(this, FirstProfileActivity::class.java))
            finish()
            return
        }

        // Инициализация на UI елементите
        spinnerProfiles = findViewById(R.id.spinnerProfiles)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnRaces = findViewById<Button>(R.id.btnRaces)
        val btnProfiles = findViewById<Button>(R.id.btnProfiles)

        // Зареждане на профили
        loadProfiles()
        if (profiles.isEmpty()) {
            startActivity(Intent(this, FirstProfileActivity::class.java))
            finish()
            return
        }
        setupProfileSpinner()

        // Настройка на бутона за стартиране
        btnStart.setOnClickListener {
            if (checkPermissions()) {
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
            } else {
                requestPermissions()
            }
        }

        // Настройка на бутона за сесии
        btnRaces.setOnClickListener {
            startActivity(Intent(this, RacesActivity::class.java))
        }

        // Настройка на бутона за профили
        btnProfiles.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }


    override fun onResume() {
        super.onResume()
        // При връщане към активността, презареждаме профилите
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
                updateBackground(it) // Актуализиране на фона
            } ?: run {
                val firstProfile = profiles.first()
                spinnerProfiles.setText(firstProfile.name, false)
                ProfileStorage.saveSelectedProfile(this, firstProfile.id)
                updateProfileIcon(firstProfile)
                updateBackground(firstProfile) // Актуализиране на фона
            }
        }

        spinnerProfiles.setOnItemClickListener { _, _, position, _ ->
            val selectedProfile = profiles[position]
            ProfileStorage.saveSelectedProfile(this, selectedProfile.id)
            updateProfileIcon(selectedProfile)
            updateBackground(selectedProfile) // Актуализиране на фона
        }
    }

    // Функция за актуализиране на иконката
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

    // Нова функция за актуализиране на фона
    private fun updateBackground(profile: Profile) {
        val backgroundRes = when (profile.vehicleType) {
            Profile.VehicleType.CAR -> R.drawable.car_background
            Profile.VehicleType.MOTORCYCLE -> R.drawable.motorcycle_background
        }
        val crossFade = CrossfadeTransition(backgroundImageView, 500)
        crossFade.start(backgroundRes)
    }

    private fun checkPermissions(): Boolean {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        ActivityCompat.requestPermissions(
            this,
            requiredPermissions,
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (!checkPermissions()) {
                Toast.makeText(
                    this,
                    "Разрешенията са задължителни за основната функционалност",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onBackPressed() {
        finishAffinity()
        System.exit(0)
    }
}