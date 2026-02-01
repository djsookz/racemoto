package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.data.VehicleData
import com.example.clinometer.settings.LanguageManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout

class VehicleSelectionActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private var isFirstLaunch: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicle_selection)
        
        // Проверяваме дали е първо влизане
        isFirstLaunch = intent.getBooleanExtra("IS_FIRST_LAUNCH", false)
        
        setupUi()
    }

    private fun setupUi() {
        val brandInput = findViewById<TextInputLayout>(R.id.brandInput)
        val modelInput = findViewById<TextInputLayout>(R.id.modelInput)
        val brandDropdown = findViewById<MaterialAutoCompleteTextView>(R.id.brandDropdown)
        val modelDropdown = findViewById<MaterialAutoCompleteTextView>(R.id.modelDropdown)
        val btnReady = findViewById<MaterialButton>(R.id.btnReady)
        val carCard = findViewById<LinearLayout>(R.id.carCard)
        val motorcycleCard = findViewById<LinearLayout>(R.id.motorcycleCard)

        var selectedVehicleType = Profile.VehicleType.CAR
        var selectedBrand = ""
        var selectedModel = ""

        val carBrands = VehicleData.carBrands
        val carModels = VehicleData.carModels
        val motorcycleBrands = VehicleData.motorcycleBrands
        val motorcycleModels = VehicleData.motorcycleModels

        // Vehicle type selection
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

        // Brand dropdown
        brandDropdown.setOnItemClickListener { _, _, position, _ ->
            val brands = if (selectedVehicleType == Profile.VehicleType.CAR) {
                carBrands
            } else {
                motorcycleBrands
            }
            
            if (brands[position] == getString(R.string.popular_brands_header)) {
                return@setOnItemClickListener
            }
            
            selectedBrand = brands[position]
            updateModelDropdown(modelDropdown, selectedBrand, if (selectedVehicleType == Profile.VehicleType.CAR) carModels else motorcycleModels)
        }

        // Model dropdown
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

        // Initial selection
        updateSelection(carCard, motorcycleCard)
        updateBrandDropdown(brandDropdown, carBrands)
        updateVehicleIcon(brandInput, selectedVehicleType)

        // READY button
        btnReady.setOnClickListener {
            if (selectedBrand.isNotEmpty() && selectedModel.isNotEmpty()) {
                val vehicleName = "$selectedBrand $selectedModel"
                
                val newProfile = Profile(
                    name = vehicleName,
                    vehicleType = selectedVehicleType
                )

                ProfileStorage.saveNewProfile(this, newProfile)
                ProfileStorage.saveSelectedProfile(this, newProfile.id)

                // Navigate to calibration
                val intent = Intent(this, DragCalibrationActivity::class.java).apply {
                    putExtra("PROFILE_ID", newProfile.id)
                    putExtra("IS_FIRST_PROFILE", true)
                    putExtra("IS_FIRST_LAUNCH", isFirstLaunch)
                }
                startActivity(intent)
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
        selectedCard.background = getDrawable(R.drawable.vehicle_option_selected_background)
        unselectedCard.background = getDrawable(R.drawable.vehicle_option_background)
    }

    private fun updateBrandDropdown(dropdown: MaterialAutoCompleteTextView, brands: Array<String>) {
        val popularIndex = brands.indexOf(getString(R.string.popular_brands_header))
        val firstRegularBrandIndex = if (popularIndex >= 0) popularIndex + 8 else 0
        
        val adapter = object : ArrayAdapter<String>(this, 0, brands) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val layoutInflater = LayoutInflater.from(context)
                val view: android.view.View
                
                when {
                    brands[position] == getString(R.string.popular_brands_header) -> {
                        view = layoutInflater.inflate(R.layout.dropdown_item_popular, parent, false)
                        val textView = view.findViewById<android.widget.TextView>(R.id.text1)
                        textView.text = getString(R.string.popular_brands_header)
                    }
                    position == firstRegularBrandIndex && popularIndex >= 0 -> {
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
                    brands[position] == getString(R.string.popular_brands_header) -> 0
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
                val view = layoutInflater.inflate(R.layout.dropdown_item_normal, parent, false)
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
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // При първо влизане - не позволяваме back, не прави нищо
        if (isFirstLaunch) {
            return
        }
        // В останалите случаи - нормално затваряне
        super.onBackPressed()
    }
}

