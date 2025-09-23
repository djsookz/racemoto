package com.example.clinometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Toast
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.example.clinometer.data.VehicleData

class FirstProfileActivity : AppCompatActivity() {
    private val PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_first_profile)

        if (!checkPermissions()) {
            requestPermissions()
            // Връщаме се тук след user action в onRequestPermissionsResult
            return
        }

        setupUi()
    }

    private fun setupUi() {
        val brandInput = findViewById<TextInputLayout>(R.id.brandInput)
        val modelInput = findViewById<TextInputLayout>(R.id.modelInput)
        val brandDropdown = findViewById<MaterialAutoCompleteTextView>(R.id.brandDropdown)
        val modelDropdown = findViewById<MaterialAutoCompleteTextView>(R.id.modelDropdown)
        val btnCreate = findViewById<MaterialButton>(R.id.btnCreateProfile)
        val carCard = findViewById<LinearLayout>(R.id.carCard)
        val motorcycleCard = findViewById<LinearLayout>(R.id.motorcycleCard)

        var selectedVehicleType = Profile.VehicleType.CAR // По подразбиране автомобил
        var selectedBrand = ""
        var selectedModel = ""

        // Използваме данните от VehicleData
        val carBrands = VehicleData.carBrands
        val carModels = VehicleData.carModels
        val motorcycleBrands = VehicleData.motorcycleBrands
        val motorcycleModels = VehicleData.motorcycleModels

        // Настройка на опциите за избор на тип превозно средство
        carCard.setOnClickListener {
            selectedVehicleType = Profile.VehicleType.CAR
            updateSelection(carCard, motorcycleCard)
            updateBrandDropdown(brandDropdown, carBrands)
            updateVehicleIcon(brandInput, selectedVehicleType)
            clearModelDropdown(modelDropdown)
        }

        motorcycleCard.setOnClickListener {
            selectedVehicleType = Profile.VehicleType.MOTORCYCLE
            updateSelection(motorcycleCard, carCard)
            updateBrandDropdown(brandDropdown, motorcycleBrands)
            updateVehicleIcon(brandInput, selectedVehicleType)
            clearModelDropdown(modelDropdown)
        }

        // Настройка на brand dropdown
        brandDropdown.setOnItemClickListener { _, _, position, _ ->
            val brands = if (selectedVehicleType == Profile.VehicleType.CAR) {
                carBrands
            } else {
                motorcycleBrands
            }
            
            // Пропускаме "Най-популярни" ако е избрано
            if (brands[position] == "Най-популярни") {
                return@setOnItemClickListener
            }
            
            selectedBrand = brands[position]
            updateModelDropdown(modelDropdown, selectedBrand, if (selectedVehicleType == Profile.VehicleType.CAR) carModels else motorcycleModels)
        }

        // Настройка на model dropdown
        modelDropdown.setOnItemClickListener { _, _, position, _ ->
            val models = if (selectedVehicleType == Profile.VehicleType.CAR) {
                carModels[selectedBrand] ?: emptyArray()
            } else {
                motorcycleModels[selectedBrand] ?: emptyArray()
            }
            if (position < models.size) {
                selectedModel = models[position]
            }
        }

        // Задаване на първоначално избрания тип
        updateSelection(carCard, motorcycleCard)
        updateBrandDropdown(brandDropdown, carBrands)
        updateVehicleIcon(brandInput, selectedVehicleType)

        btnCreate.setOnClickListener {
            if (selectedBrand.isNotEmpty() && selectedModel.isNotEmpty()) {
                val vehicleName = "$selectedBrand $selectedModel"
                
                // Създаване на нов профил
                val newProfile = Profile(
                    name = vehicleName,
                    vehicleType = selectedVehicleType
                )

                // Запазване на профила
                ProfileStorage.saveNewProfile(this, newProfile)
                ProfileStorage.saveSelectedProfile(this, newProfile.id)

                // Стартиране на основната активност (MainMapActivity) и завършване на текущата
                startActivity(Intent(this, MainMapActivity::class.java))
                finish()
            } else {
                if (selectedBrand.isEmpty()) {
                    brandInput.error = "Please select a brand"
                }
                if (selectedModel.isEmpty()) {
                    modelInput.error = "Please select a model"
                }
            }
        }
    }

    private fun updateSelection(selectedCard: LinearLayout, unselectedCard: LinearLayout) {
        // Обновяване на избраната опция
        selectedCard.background = getDrawable(R.drawable.vehicle_option_selected_background)
        
        // Обновяване на неизбраната опция
        unselectedCard.background = getDrawable(R.drawable.vehicle_option_background)
    }

    private fun updateBrandDropdown(dropdown: MaterialAutoCompleteTextView, brands: Array<String>) {
        // Намираме индекса на "Най-популярни" за да знаем къде да сложим разделителя
        val popularIndex = brands.indexOf("Най-популярни")
        val firstRegularBrandIndex = if (popularIndex >= 0) popularIndex + 8 else 0 // 7 популярни марки + 1 за "Най-популярни"
        
        val adapter = object : ArrayAdapter<String>(this, 0, brands) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val layoutInflater = LayoutInflater.from(context)
                val view: android.view.View
                
                when {
                    brands[position] == "Най-популярни" -> {
                        view = layoutInflater.inflate(R.layout.dropdown_item_popular, parent, false)
                        val textView = view.findViewById<android.widget.TextView>(R.id.text1)
                        textView.text = "Най-популярни"
                    }
                    position == firstRegularBrandIndex && popularIndex >= 0 -> {
                        // Добавяме разделител преди първата обикновена марка
                        view = layoutInflater.inflate(R.layout.dropdown_item_separator, parent, false)
                    }
                    else -> {
                        view = layoutInflater.inflate(R.layout.dropdown_item_normal, parent, false)
                        val textView = view.findViewById<android.widget.TextView>(R.id.text1)
                        textView.text = brands[position]
                    }
                }
                
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                return getView(position, convertView, parent)
            }
            
            override fun getItemViewType(position: Int): Int {
                return when {
                    brands[position] == "Най-популярни" -> 0
                    position == firstRegularBrandIndex && popularIndex >= 0 -> 1
                    else -> 2
                }
            }
            
            override fun getViewTypeCount(): Int = 3
        }
        dropdown.setAdapter(adapter)
        dropdown.setText("", false)
    }

    private fun updateModelDropdown(dropdown: MaterialAutoCompleteTextView, brand: String, modelsMap: Map<String, Array<String>>) {
        val models = modelsMap[brand] ?: emptyArray()
        
        val adapter = object : ArrayAdapter<String>(this, 0, models) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val layoutInflater = LayoutInflater.from(context)
                val view: android.view.View
                
                // За моделите използваме само нормалния layout
                view = layoutInflater.inflate(R.layout.dropdown_item_normal, parent, false)
                val textView = view.findViewById<android.widget.TextView>(R.id.text1)
                textView.text = models[position]
                
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                return getView(position, convertView, parent)
            }
        }
        
        dropdown.setAdapter(adapter)
        dropdown.setText("", false)
    }

    private fun clearModelDropdown(dropdown: MaterialAutoCompleteTextView) {
        dropdown.setText("", false)
        dropdown.setAdapter(null)
    }
    
    private fun updateVehicleIcon(brandInput: TextInputLayout, vehicleType: Profile.VehicleType) {
        when (vehicleType) {
            Profile.VehicleType.CAR -> {
                brandInput.startIconDrawable = getDrawable(R.drawable.ic_car)
            }
            Profile.VehicleType.MOTORCYCLE -> {
                brandInput.startIconDrawable = getDrawable(R.drawable.ic_motorcycle)
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