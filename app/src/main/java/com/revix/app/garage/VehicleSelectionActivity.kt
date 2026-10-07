package com.revix.app.garage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.revix.app.DragCalibrationActivity
import com.revix.app.Profile
import com.revix.app.R
import com.revix.app.applySystemBarsPaddingToRoot
import com.revix.app.data.ProfileStorage
import com.revix.app.data.VehicleData
import com.revix.app.settings.LanguageManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class VehicleSelectionActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    private var isFirstLaunch: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vehicle_selection)
        applySystemBarsPaddingToRoot()
        
        // Проверяваме дали е първо влизане
        isFirstLaunch = intent.getBooleanExtra("IS_FIRST_LAUNCH", false)
        
        setupUi()
    }

    private fun setupUi() {
        val brandInput = findViewById<TextInputLayout>(R.id.brandInput)
        val modelInput = findViewById<TextInputLayout>(R.id.modelInput)
        val brandDropdown = findViewById<TextInputEditText>(R.id.brandDropdown)
        val modelDropdown = findViewById<TextInputEditText>(R.id.modelDropdown)
        val btnReady = findViewById<MaterialButton>(R.id.btnReady)
        val carCard = findViewById<LinearLayout>(R.id.carCard)
        val motorcycleCard = findViewById<LinearLayout>(R.id.motorcycleCard)

        var selectedVehicleType = Profile.VehicleType.CAR
        var selectedBrand = ""
        var selectedModel = ""

        fun clearBrandAndModel() {
            selectedBrand = ""
            selectedModel = ""
            brandDropdown.setText("")
            modelDropdown.setText("")
            modelInput.isEnabled = false
            modelDropdown.isEnabled = false
            brandInput.error = null
            modelInput.error = null
        }

        fun updateModelEnabled() {
            val enabled = selectedBrand.isNotEmpty()
            modelInput.isEnabled = enabled
            modelDropdown.isEnabled = enabled
        }

        // Vehicle type selection
        carCard.setOnClickListener {
            selectedVehicleType = Profile.VehicleType.CAR
            updateSelection(carCard, motorcycleCard)
            clearBrandAndModel()
        }

        motorcycleCard.setOnClickListener {
            selectedVehicleType = Profile.VehicleType.MOTORCYCLE
            updateSelection(motorcycleCard, carCard)
            clearBrandAndModel()
        }

        brandDropdown.setOnClickListener {
            val brandsRaw = if (selectedVehicleType == Profile.VehicleType.CAR) {
                VehicleData.carBrands
            } else {
                VehicleData.motorcycleBrands
            }
            val brands = VehicleData.localizedBrands(this, brandsRaw)
                .filterNot { VehicleData.isPopularBrandsHeader(this, it) }
            showSearchPicker(getString(R.string.garage_brand_label), brands) { selected ->
                selectedBrand = selected
                brandDropdown.setText(selected)
                selectedModel = ""
                modelDropdown.setText("")
                updateModelEnabled()
            }
        }

        modelDropdown.setOnClickListener {
            if (selectedBrand.isEmpty()) {
                brandInput.error = getString(R.string.garage_select_brand)
                return@setOnClickListener
            }
            val models = if (selectedVehicleType == Profile.VehicleType.CAR) {
                VehicleData.carModelsFor(selectedBrand).toList()
            } else {
                VehicleData.motorcycleModelsFor(selectedBrand).toList()
            }
            showSearchPicker(getString(R.string.garage_model_label), models) { selected ->
                selectedModel = selected
                modelDropdown.setText(selected)
            }
        }

        updateSelection(carCard, motorcycleCard)
        updateModelEnabled()

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
                    brandInput.error = getString(R.string.garage_select_brand)
                }
                if (selectedModel.isEmpty()) {
                    modelInput.error = getString(R.string.garage_select_model)
                }
            }
        }
    }

    private fun updateSelection(selectedCard: LinearLayout, unselectedCard: LinearLayout) {
        selectedCard.background = getDrawable(R.drawable.vehicle_option_selected_background)
        unselectedCard.background = getDrawable(R.drawable.vehicle_option_background)
    }

    private fun showSearchPicker(title: String, options: List<String>, onSelect: (String) -> Unit) {
        VehicleSearchPicker.show(this, title, options, onSelect)
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

