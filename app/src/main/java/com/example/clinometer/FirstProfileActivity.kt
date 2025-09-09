package com.example.clinometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout

class FirstProfileActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!checkPermissions()) {
            requestPermissions()
            // Връщаме се тук след user action в onRequestPermissionsResult
            return
        }

        setupUi()
    }

    private fun setupUi() {
        val nameInput = findViewById<TextInputLayout>(R.id.nameInput)
        val vehicleTypeInput = findViewById<TextInputLayout>(R.id.vehicleTypeInput)
        val vehicleTypeDropdown = findViewById<MaterialAutoCompleteTextView>(R.id.vehicleTypeDropdown)
        val btnCreate = findViewById<MaterialButton>(R.id.btnCreateProfile)

        // Настройка на падащото меню за тип превозно средство
        val vehicleTypes = resources.getStringArray(R.array.vehicle_types)
        val adapter = ArrayAdapter(this, R.layout.dropdown_item, vehicleTypes)
        vehicleTypeDropdown.setAdapter(adapter)
        vehicleTypeDropdown.setText(vehicleTypes[0], false) // Задаване на стойност по подразбиране

        btnCreate.setOnClickListener {
            val name = nameInput.editText?.text.toString().trim()
            val selectedVehicleType = vehicleTypeDropdown.text.toString()

            if (name.isNotEmpty()) {
                // Конвертиране на текста към VehicleType enum
                val vehicleType = when (selectedVehicleType) {
                    getString(R.string.vehicle_type_car) -> Profile.VehicleType.CAR
                    else -> Profile.VehicleType.MOTORCYCLE
                }

                // Създаване на нов профил
                val newProfile = Profile(
                    name = name,
                    vehicleType = vehicleType
                )

                // Запазване на профила
                ProfileStorage.saveNewProfile(this, newProfile)
                ProfileStorage.saveSelectedProfile(this, newProfile.id)

                // Стартиране на основната активност (MainMapActivity) и завършване на текущата
                startActivity(Intent(this, MainMapActivity::class.java))
                finish()
            } else {
                nameInput.error = getString(R.string.error_empty_name)
            }
        }
    }

    private fun checkPermissions(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, perms, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (checkPermissions()) {
                // Сега вече имаме разрешения, продължаваме с UI
                setupUi()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.error_permissions_required),
                    Toast.LENGTH_LONG
                ).show()
                finish() // или пак да поискаш, според UX-а
            }
        }
    }
}