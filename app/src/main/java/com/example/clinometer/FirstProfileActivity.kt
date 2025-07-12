package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout

class FirstProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_first_profile)

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

                // Стартиране на основната активност и завършване на текущата
                startActivity(Intent(this, StartActivity::class.java))
                finish()
            } else {
                nameInput.error = getString(R.string.error_empty_name)
            }
        }
    }
}