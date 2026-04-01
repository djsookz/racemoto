package com.example.clinometer.garage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import com.example.clinometer.DragCalibrationActivity
import com.example.clinometer.Profile
import com.example.clinometer.R
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.data.VehicleData
import com.example.clinometer.settings.LanguageManager
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
                VehicleData.carBrands.toList()
            } else {
                VehicleData.motorcycleBrands.toList()
            }
            val brands = brandsRaw.filterNot {
                it.equals(getString(R.string.popular_brands_header), true) || it.contains("Най", true)
            }
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
                VehicleData.carModels[selectedBrand]?.toList() ?: emptyList()
            } else {
                VehicleData.motorcycleModels[selectedBrand]?.toList() ?: emptyList()
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_search_picker, null)
        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvPickerTitle)
        val btnClose = dialogView.findViewById<android.widget.ImageButton>(R.id.btnClosePicker)
        val etSearch = dialogView.findViewById<TextInputEditText>(R.id.etSearch)
        val listView = dialogView.findViewById<android.widget.ListView>(R.id.lvOptions)
        val tvEmpty = dialogView.findViewById<android.widget.TextView>(R.id.tvEmpty)

        tvTitle.text = title

        val adapter = android.widget.ArrayAdapter<String>(this, R.layout.dropdown_item_normal, R.id.text1, options)
        listView.adapter = adapter
        listView.emptyView = tvEmpty

        etSearch.addTextChangedListener { text ->
            adapter.filter.filter(text?.toString() ?: "")
        }

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }

        listView.setOnItemClickListener { _, _, position, _ ->
            adapter.getItem(position)?.let { onSelect(it) }
            dialog.dismiss()
        }

        dialog.show()
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

