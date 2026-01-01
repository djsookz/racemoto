package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Outline
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.example.clinometer.data.VehicleData
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream

class GarageActivity :  BaseActivity() {

    override fun getLayoutResourceId(): Int = R.layout.activity_garage
    override fun getNavigationItemId(): Int = R.id.navGarage
    private lateinit var adapter: ProfileAdapter
    private lateinit var btnAddProfile: ExtendedFloatingActionButton
    private lateinit var cardActiveProfile: MaterialCardView
    private lateinit var tvActiveProfileName: TextView
    private lateinit var tvActiveProfileType: TextView
    private lateinit var ivActiveProfileIcon: ImageView
    private lateinit var flProfileImageContainer: FrameLayout
    private lateinit var vFadeOverlay: View
    private lateinit var vOrangeDivider: View
    private lateinit var btnChangeProfile: MaterialButton
    private lateinit var tvProfileCount: TextView
    private val profiles = mutableListOf<Profile>()
    private var backPressedTime: Long = 0
    private lateinit var backToast: Toast
    private var currentSelectedProfile: Profile? = null
    
    // Executor за background операции
    private val imageLoadExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    
    // Кеш за заредени bitmap-и
    private val imageCache = mutableMapOf<String, Bitmap>()
    
    // Activity result launcher за избор на снимка
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            saveProfileImage(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = ContextCompat.getColor(this, R.color.header_gradient_start)

        btnAddProfile = findViewById(R.id.btnAddProfile)
        cardActiveProfile = findViewById(R.id.cardActiveProfile)
        tvActiveProfileName = findViewById(R.id.tvActiveProfileName)
        tvActiveProfileType = findViewById(R.id.tvActiveProfileType)
        ivActiveProfileIcon = findViewById(R.id.ivActiveProfileIcon)
        flProfileImageContainer = findViewById(R.id.flProfileImageContainer)
        vFadeOverlay = findViewById(R.id.vFadeOverlay)
        vOrangeDivider = findViewById(R.id.vOrangeDivider)
        btnChangeProfile = findViewById(R.id.btnChangeProfile)
        tvProfileCount = findViewById(R.id.tvProfileCount)
        val recyclerView = findViewById<RecyclerView>(R.id.rvProfiles)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ProfileAdapter(
            profiles,
            this,
            onProfileClick = { profile ->
                val intent = Intent(this, ProfileDetailActivity::class.java).apply {
                    putExtra("profile_id", profile.id)
                }
                startActivity(intent)
            },
            onEditClick = { profile -> showEditProfileDialog(profile) },
            onDeleteClick = { profile -> deleteProfileWithAnimation(profile) }
        )
        recyclerView.adapter = adapter

        btnAddProfile.setOnClickListener { showCreateProfileDialog() }
        btnChangeProfile.setOnClickListener { showQuickProfileSelection() }

        // Click на снимката/иконката отваря диалог за избор на снимка
        flProfileImageContainer.setOnClickListener { showImageSelectionDialog() }

        loadProfiles()
        updateAddButtonState()
        updateActiveProfileCard()
        updateProfileCount()

        // Безопасно настройване на навигацията
        try {
            setupNavigationBar()
        } catch (e: Exception) {
            android.util.Log.w("ProfileActivity", "Navigation setup failed: ${e.message}")
        }
    }
    override fun onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            backToast.cancel()
            super.onBackPressed()
            return
        } else {
            backToast = Toast.makeText(baseContext, getString(R.string.garage_back_press), Toast.LENGTH_SHORT)
            backToast.show()
        }

        backPressedTime = System.currentTimeMillis()
    }

    private fun loadProfiles() {
        profiles.clear()
        profiles.addAll(ProfileStorage.loadProfiles(this))
        adapter.notifyDataSetChanged()
    }

    private fun updateAddButtonState() {
        if (profiles.size >= 5) {
            btnAddProfile.text = getString(R.string.garage_add_button)
            btnAddProfile.isEnabled = false
            btnAddProfile.alpha = 0.6f
            btnAddProfile.setIconResource(R.drawable.ic_block)
        } else {
            btnAddProfile.text = getString(R.string.garage_add_button)
            btnAddProfile.isEnabled = true
            btnAddProfile.alpha = 1f
            btnAddProfile.setIconResource(R.drawable.ic_add)
        }
    }

    private fun updateProfileCount() {
        tvProfileCount.text = "${profiles.size}/5"

        // Цветно кодиране според броя профили
        val color = when {
            profiles.size >= 5 -> ContextCompat.getColor(this, R.color.warning_color)
            profiles.size >= 3 -> ContextCompat.getColor(this, R.color.success_color)
            else -> ContextCompat.getColor(this, R.color.accent_color)
        }
        tvProfileCount.setTextColor(color)
    }

    private fun updateActiveProfileCard() {
        val selectedId = ProfileStorage.getSelectedProfileId(this)
        var selectedProfile = profiles.find { it.id == selectedId }

        if (selectedProfile == null && profiles.isNotEmpty()) {
            selectedProfile = profiles.first()
            ProfileStorage.saveSelectedProfile(this, selectedProfile.id)
        }

        selectedProfile?.let { profile ->
            currentSelectedProfile = profile
            tvActiveProfileName.text = profile.name

            val (iconRes, emoji, typeText) = when (profile.vehicleType) {
                Profile.VehicleType.CAR -> Triple(R.drawable.ic_car, "🚗", getString(R.string.garage_vehicle_car))
                Profile.VehicleType.MOTORCYCLE -> Triple(R.drawable.ic_motorcycle, "🏍️", getString(R.string.garage_vehicle_motorcycle))
            }

            tvActiveProfileType.text = typeText

            // Зареждаме снимка ако има, иначе показваме иконка
            val imagePath = profile.imagePath
            if (!imagePath.isNullOrEmpty()) {
                val imageFile = File(getExternalFilesDir(null), imagePath)
                if (imageFile.exists()) {
                    // Проверяваме кеша първо
                    val cachedBitmap = imageCache[imagePath]
                    if (cachedBitmap != null) {
                        // Използваме кеширания bitmap
                        setImageBitmap(cachedBitmap)
                    } else {
                        // Зареждаме в background thread
                        val currentProfileId = profile.id
                        imageLoadExecutor.execute {
                            try {
                                // Зареждаме с downsampling - максимум 800x800 за активния профил
                                val bitmap = decodeSampledBitmapFromFile(imageFile.absolutePath, 800, 800)
                                if (bitmap != null) {
                                    // Кешираме bitmap-а
                                    imageCache[imagePath] = bitmap
                                    // Обновяваме UI на main thread
                                    runOnUiThread {
                                        // Проверяваме дали все още е същият профил
                                        if (currentSelectedProfile?.id == currentProfileId) {
                                            setImageBitmap(bitmap)
                                        }
                                    }
                                } else {
                                    runOnUiThread {
                                        if (currentSelectedProfile?.id == currentProfileId) {
                                            showIcon(iconRes)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("GarageActivity", "Error loading image", e)
                                runOnUiThread {
                                    if (currentSelectedProfile?.id == currentProfileId) {
                                        showIcon(iconRes)
                                    }
                                }
                            }
                        }
                        // Показваме placeholder докато се зарежда
                        showIcon(iconRes)
                    }
                } else {
                    // Файлът не съществува, показваме иконка
                    showIcon(iconRes)
                }
            } else {
                // Няма снимка, показваме иконка
                showIcon(iconRes)
            }

            cardActiveProfile.visibility = View.VISIBLE
            btnChangeProfile.visibility = if (profiles.size > 1) View.VISIBLE else View.GONE

            // Анимация при обновяване
            cardActiveProfile.animate()
                .scaleX(1.02f)
                .scaleY(1.02f)
                .setDuration(100)
                .withEndAction {
                    cardActiveProfile.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
        } ?: run {
            cardActiveProfile.visibility = View.GONE
        }
    }

    private fun getProfileSessionCount(profileId: Long): Int {
        val allRaces = RouteStorage.loadRaces(this)
        return allRaces.count { it.profileId == profileId }
    }

    private fun deleteProfileWithAnimation(profile: Profile) {
        val selectedId = ProfileStorage.getSelectedProfileId(this)
        if (profile.id == selectedId) {
            Toast.makeText(this, "❌ Не можете да изтриете активния профил", Toast.LENGTH_LONG).show()
            return
        }

        val sessionCount = getProfileSessionCount(profile.id)
        val message = if (sessionCount > 0) {
            getString(R.string.garage_confirm_delete, profile.name, sessionCount)
        } else {
            getString(R.string.garage_confirm_delete_simple, profile.name)
        }

        val deleteDialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("🗑️ Изтриване на профил")
            .setMessage(message)
            .setPositiveButton(getString(R.string.garage_delete_button)) { _, _ ->
                // Изтриваме всички сесии за този профил
                if (sessionCount > 0) {
                    val allRaces = RouteStorage.loadRaces(this).toMutableList()
                    allRaces.removeAll { it.profileId == profile.id }
                    RouteStorage.saveRaces(this, allRaces)
                }

                // Изтриваме профила
                profiles.remove(profile)
                ProfileStorage.saveProfiles(this, profiles)
                adapter.notifyDataSetChanged()
                updateAddButtonState()
                updateProfileCount()
                updateActiveProfileCard()

                val successMessage = if (sessionCount > 0) {
                    "✅ Профилът и всички негови $sessionCount сесии са изтрити"
                } else {
                    "✅ Профилът е изтрит успешно"
                }
                Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()

                if (profiles.isEmpty()) {
                    startActivity(Intent(this, FirstProfileActivity::class.java))
                    finish()
                }
            }
            .setNegativeButton(getString(R.string.garage_cancel_button), null)
            .create()
        
        DialogHelper.styleDialogButtons(deleteDialog)
        deleteDialog.show()
    }

    private fun showQuickProfileSelection() {
        if (profiles.size <= 1) {
            Toast.makeText(this, "ℹ️ Няма други профили за избор", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedId = ProfileStorage.getSelectedProfileId(this)
        val otherProfiles = profiles.filter { it.id != selectedId }

        val options = otherProfiles.map { profile ->
            val emoji = when (profile.vehicleType) {
                Profile.VehicleType.CAR -> "🚗"
                Profile.VehicleType.MOTORCYCLE -> "🏍️"
            }
            "$emoji ${profile.name}"
        }.toTypedArray()

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("🔄 Смени превозното средство")
            .setItems(options) { _, which ->
                val newProfile = otherProfiles[which]
                ProfileStorage.saveSelectedProfile(this, newProfile.id)
                updateActiveProfileCard()
                adapter.notifyDataSetChanged()

                Toast.makeText(this, "✅ Сега караш: ${newProfile.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.garage_cancel_button), null)
            .show()
    }

    private fun showCreateProfileDialog() {
        if (profiles.size >= 5) {
            Toast.makeText(this, "⚠️ Максимум 5 профила са разрешени", Toast.LENGTH_LONG).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_profile, null)
        
        // Намираме UI елементите
        val carCard = dialogView.findViewById<LinearLayout>(R.id.carCard)
        val motorcycleCard = dialogView.findViewById<LinearLayout>(R.id.motorcycleCard)
        val brandInput = dialogView.findViewById<TextInputLayout>(R.id.brandInput)
        val modelInput = dialogView.findViewById<TextInputLayout>(R.id.modelInput)
        val brandDropdown = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.brandDropdown)
        val modelDropdown = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.modelDropdown)

        var selectedVehicleType = Profile.VehicleType.CAR
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
            if (brands[position] == getString(R.string.garage_most_popular)) {
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

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .setNegativeButton(getString(R.string.garage_cancel_button), null)
            .setPositiveButton(getString(R.string.garage_create_button), null)
            .create()

        DialogHelper.styleDialogButtons(dialog)
        
        dialog.setOnShowListener {
            val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            
            btnSave.setOnClickListener {
                if (selectedBrand.isEmpty()) {
                    brandInput.error = getString(R.string.garage_select_brand)
                    return@setOnClickListener
                }
                if (selectedModel.isEmpty()) {
                    modelInput.error = getString(R.string.garage_select_model)
                    return@setOnClickListener
                }

                val vehicleName = "$selectedBrand $selectedModel"
                val newProfile = Profile(name = vehicleName, vehicleType = selectedVehicleType)
                profiles.add(newProfile)
                ProfileStorage.saveProfiles(this, profiles)
                adapter.notifyDataSetChanged()
                updateAddButtonState()
                updateProfileCount()

                if (profiles.size == 1) {
                    ProfileStorage.saveSelectedProfile(this, newProfile.id)
                }

                updateActiveProfileCard()
                dialog.dismiss()
                
                // ВАЖНО: Редиректваме към калибрация ВЕДНАГА след създаване!
                val intent = Intent(this, DragCalibrationActivity::class.java).apply {
                    putExtra("PROFILE_ID", newProfile.id)
                    putExtra("IS_NEW_PROFILE", true) // Маркираме че е нов профил от Garage
                }
                startActivity(intent)
            }
        }
        dialog.show()
    }

    private fun updateSelection(selectedCard: LinearLayout, unselectedCard: LinearLayout) {
        // Обновяване на избраната опция
        selectedCard.background = getDrawable(R.drawable.vehicle_option_selected_background)
        
        // Обновяване на неизбраната опция
        unselectedCard.background = getDrawable(R.drawable.vehicle_option_background)
    }

    private fun updateBrandDropdown(dropdown: MaterialAutoCompleteTextView, brands: Array<String>) {
        // Намираме индекса на "Най-популярни" за да знаем къде да сложим разделителя
        val popularIndex = brands.indexOf(getString(R.string.garage_most_popular))
        val firstRegularBrandIndex = if (popularIndex >= 0) popularIndex + 8 else 0 // 7 популярни марки + 1 за "Най-популярни"
        
        val adapter = object : ArrayAdapter<String>(this, 0, brands) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val layoutInflater = LayoutInflater.from(context)
                val view: android.view.View
                
                when {
                    brands[position] == getString(R.string.garage_most_popular) -> {
                        view = layoutInflater.inflate(R.layout.dropdown_item_popular, parent, false)
                        val textView = view.findViewById<android.widget.TextView>(R.id.text1)
                        textView.text = getString(R.string.garage_most_popular)
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
                    brands[position] == getString(R.string.garage_most_popular) -> 0
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
    
    private fun showImageSelectionDialog() {
        val profile = currentSelectedProfile ?: return
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_selection, null)
        
        val llSelectImage = dialogView.findViewById<LinearLayout>(R.id.llSelectImage)
        val llRemoveImage = dialogView.findViewById<LinearLayout>(R.id.llRemoveImage)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)
        
        // Показваме опцията за премахване само ако има снимка
        if (!profile.imagePath.isNullOrEmpty()) {
            llRemoveImage.visibility = View.VISIBLE
        } else {
            llRemoveImage.visibility = View.GONE
        }
        
        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()
        
        // Обработка на кликвания
        llSelectImage.setOnClickListener {
            dialog.dismiss()
            imagePickerLauncher.launch("image/*")
        }
        
        llRemoveImage.setOnClickListener {
            dialog.dismiss()
            removeProfileImage(profile)
        }
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun saveProfileImage(uri: Uri) {
        val profile = currentSelectedProfile ?: return
        
        try {
            // Четем снимката от URI
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            // Коригираме ориентацията според EXIF данните
            val correctedBitmap = correctImageOrientation(uri, bitmap)
            
            // Създаваме директория за снимки ако не съществува
            val imagesDir = File(getExternalFilesDir(null), "profile_images")
            if (!imagesDir.exists()) {
                imagesDir.mkdirs()
            }
            
            // Запазваме снимката с име базирано на profile ID
            val imageFile = File(imagesDir, "profile_${profile.id}.jpg")
            val outputStream = FileOutputStream(imageFile)
            correctedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            
            // Запазваме пътя в профила
            val newImagePath = "profile_images/profile_${profile.id}.jpg"
            val oldImagePath = profile.imagePath
            profile.imagePath = newImagePath
            ProfileStorage.saveProfiles(this, profiles)
            
            // Изчистваме стария кеш (ако има)
            if (!oldImagePath.isNullOrEmpty()) {
                imageCache.remove(oldImagePath)
            }
            
            // Кешираме новата снимка веднага (resize за по-добра производителност с запазване на aspect ratio)
            val maxSize = 800
            val resizedBitmap = if (correctedBitmap.width > maxSize || correctedBitmap.height > maxSize) {
                // Запазваме aspect ratio
                val scale = minOf(maxSize.toFloat() / correctedBitmap.width, maxSize.toFloat() / correctedBitmap.height)
                val newWidth = (correctedBitmap.width * scale).toInt()
                val newHeight = (correctedBitmap.height * scale).toInt()
                Bitmap.createScaledBitmap(correctedBitmap, newWidth, newHeight, true)
            } else {
                correctedBitmap
            }
            imageCache[newImagePath] = resizedBitmap
            
            // Изчистваме стария кеш в GarageActivity
            if (!oldImagePath.isNullOrEmpty()) {
                imageCache.remove(oldImagePath)
            }
            
            // Обновяваме UI веднага с новата снимка на главния thread
            runOnUiThread {
                // Изчистваме кеша преди да обновим UI, за да се зареди отново от файла
                imageCache.remove(newImagePath)
                // Обновяваме активния профил карт - той ще зареди снимката отново от файла с правилните размери
                updateActiveProfileCard()
                
                // Изчистваме стария кеш в adapter-а
                adapter.clearImageCacheForPath(oldImagePath)
                // Изчистваме и новия кеш, за да се зареди отново от файла с правилните размери
                adapter.clearImageCacheForPath(newImagePath)
                
                // Обновяваме конкретния item в колекцията - той ще зареди снимката от файла с правилните размери
                val profileIndex = profiles.indexOfFirst { it.id == profile.id }
                if (profileIndex != -1) {
                    adapter.notifyItemChanged(profileIndex)
                }
            }
            
            Toast.makeText(this, "✅ Снимката е запазена", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("GarageActivity", "Error saving image: ${e.message}", e)
            Toast.makeText(this, "❌ Грешка при запазване на снимката", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun correctImageOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            var orientation = ExifInterface.ORIENTATION_NORMAL
            
            // Опитваме се да прочетем EXIF данните
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val exif = ExifInterface(inputStream)
                        orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("GarageActivity", "Could not read EXIF from stream, trying file path: ${e.message}")
                } finally {
                    inputStream.close()
                }
            }
            
            // Ако не успяхме от stream, опитваме се от файл път (за по-стари версии)
            if (orientation == ExifInterface.ORIENTATION_NORMAL) {
                val filePath = getRealPathFromURI(uri)
                if (filePath != null) {
                    try {
                        val exif = ExifInterface(filePath)
                        orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    } catch (e: Exception) {
                        android.util.Log.w("GarageActivity", "Could not read EXIF from file path: ${e.message}")
                    }
                }
            }
            
            // Ако ориентацията е нормална, връщаме оригиналния bitmap
            if (orientation == ExifInterface.ORIENTATION_NORMAL) {
                return bitmap
            }
            
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
                else -> return bitmap // Няма нужда от ротация
            }
            
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            android.util.Log.e("GarageActivity", "Error correcting orientation: ${e.message}", e)
            bitmap // Връщаме оригиналния bitmap при грешка
        }
    }
    
    private fun getRealPathFromURI(uri: Uri): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // За Android 10+ използваме директно URI
                null
            } else {
                val projection = arrayOf(MediaStore.Images.Media.DATA)
                val cursor = contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    it.moveToFirst()
                    it.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun removeProfileImage(profile: Profile) {
        if (!profile.imagePath.isNullOrEmpty()) {
            val oldImagePath = profile.imagePath
            val imageFile = File(getExternalFilesDir(null), oldImagePath)
            if (imageFile.exists()) {
                imageFile.delete()
            }
            // Изчистваме кеша
            imageCache.remove(oldImagePath)
            profile.imagePath = null
            ProfileStorage.saveProfiles(this, profiles)
            
            // Обновяваме UI веднага - показваме иконка
            runOnUiThread {
                val (iconRes, emoji, typeText) = when (profile.vehicleType) {
                    Profile.VehicleType.CAR -> Triple(R.drawable.ic_car, "🚗", getString(R.string.garage_vehicle_car))
                    Profile.VehicleType.MOTORCYCLE -> Triple(R.drawable.ic_motorcycle, "🏍️", getString(R.string.garage_vehicle_motorcycle))
                }
                showIcon(iconRes)
                tvActiveProfileName.text = profile.name
                tvActiveProfileType.text = typeText
            }
            
            adapter.notifyDataSetChanged() // Обновяваме и списъка с профили
            Toast.makeText(this, "✅ Снимката е премахната", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showIcon(iconRes: Int) {
        // За иконка - връщаме контейнера на оригиналния размер (48dp като в колекцията)
        val containerLayoutParams = flProfileImageContainer.layoutParams as android.widget.LinearLayout.LayoutParams
        val containerSizeInPx = (48 * resources.displayMetrics.density).toInt()
        containerLayoutParams.width = containerSizeInPx
        containerLayoutParams.height = containerSizeInPx
        // Връщаме marginTop на 30dp за иконката
        containerLayoutParams.topMargin = (30 * resources.displayMetrics.density).toInt()
        // ВАЖНО: Връщаме gravity на center_horizontal за да е центрирана иконката
        containerLayoutParams.gravity = android.view.Gravity.CENTER_HORIZONTAL
        flProfileImageContainer.layoutParams = containerLayoutParams
        // Добавяме бял кръгъл background като в колекцията
        flProfileImageContainer.background = ContextCompat.getDrawable(this, R.drawable.profile_icon_background)
        
        // За иконка - показваме иконката в центъра на контейнера (24dp като в колекцията)
        val layoutParams = ivActiveProfileIcon.layoutParams as android.widget.FrameLayout.LayoutParams
        val sizeInPx = (24 * resources.displayMetrics.density).toInt()
        layoutParams.width = sizeInPx
        layoutParams.height = sizeInPx
        layoutParams.gravity = android.view.Gravity.CENTER
        ivActiveProfileIcon.layoutParams = layoutParams
        ivActiveProfileIcon.setImageResource(iconRes)
        ivActiveProfileIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
        ivActiveProfileIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.primary_color)
        ivActiveProfileIcon.clipToOutline = false
        ivActiveProfileIcon.outlineProvider = null
        // Скриваме fade градиента когато показваме иконка
        vFadeOverlay.visibility = View.GONE
        // Скриваме оранжевата лента когато показваме иконка
        vOrangeDivider.visibility = View.GONE
    }
    
    // Функция за зареждане на bitmap с downsampling
    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            // Първо прочитаме размерите на снимката
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)
            
            // Изчисляваме inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            
            // Зареждаме bitmap-а с правилния размер
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565 // По-малко памет
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            android.util.Log.e("GarageActivity", "Error decoding bitmap", e)
            null
        }
    }
    
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    private fun setImageBitmap(bitmap: Bitmap) {
        ivActiveProfileIcon.setImageBitmap(bitmap)
        // За снимка - увеличаваме контейнера на 130dp и премахваме всички margins
        val containerLayoutParams = flProfileImageContainer.layoutParams as android.view.ViewGroup.MarginLayoutParams
        containerLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        containerLayoutParams.height = (130 * resources.displayMetrics.density).toInt()
        // Премахваме всички margins за да запълни целия контейнер
        containerLayoutParams.topMargin = 0
        containerLayoutParams.bottomMargin = 0
        containerLayoutParams.marginStart = 0
        containerLayoutParams.marginEnd = 0
        flProfileImageContainer.layoutParams = containerLayoutParams
        // Премахваме layout_gravity за да запълни цялата ширина
        (flProfileImageContainer.parent as? android.widget.LinearLayout)?.let { parent ->
            val parentLayoutParams = flProfileImageContainer.layoutParams as android.widget.LinearLayout.LayoutParams
            parentLayoutParams.gravity = android.view.Gravity.FILL_HORIZONTAL
            flProfileImageContainer.layoutParams = parentLayoutParams
        }
        // Премахваме бял background за снимката
        flProfileImageContainer.background = null
        // За снимка - пълна ширина и височина с CENTER_CROP за да запълни целия контейнер
        val layoutParams = ivActiveProfileIcon.layoutParams as android.widget.FrameLayout.LayoutParams
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.gravity = android.view.Gravity.FILL
        ivActiveProfileIcon.layoutParams = layoutParams
        ivActiveProfileIcon.scaleType = ImageView.ScaleType.CENTER_CROP
        ivActiveProfileIcon.imageTintList = null
        ivActiveProfileIcon.clipToOutline = false
        ivActiveProfileIcon.outlineProvider = null
        // Показваме fade градиента в долната част
        vFadeOverlay.visibility = View.VISIBLE
        // Показваме оранжевата лента
        vOrangeDivider.visibility = View.VISIBLE
    }

    private fun showEditProfileDialog(profile: Profile) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_profile, null)
        val etName = dialogView.findViewById<EditText>(R.id.etProfileName)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinnerVehicleType)

        etName.setText(profile.name)
        val adapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.vehicle_types)
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(ContextCompat.getColor(this@GarageActivity, R.color.white))
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(ContextCompat.getColor(this@GarageActivity, R.color.white))
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = adapter
        spinnerType.setSelection(if (profile.vehicleType == Profile.VehicleType.CAR) 0 else 1)

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(getString(R.string.profile_edit_text))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.dialog_cancel_button), null)
            .setPositiveButton(getString(R.string.profile_save_button), null)
            .create()

        DialogHelper.styleDialogButtons(dialog)
        
        dialog.setOnShowListener {
            val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnSave.setOnClickListener {
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    etName.error = getString(R.string.error_empty_name)
                    return@setOnClickListener
                }
                val type = if (spinnerType.selectedItemPosition == 0)
                    Profile.VehicleType.CAR else Profile.VehicleType.MOTORCYCLE

                profile.name = name
                profile.vehicleType = type
                ProfileStorage.saveProfiles(this, profiles)
                adapter.notifyDataSetChanged()

                // Ако редактираме активния профил, обновяваме картата
                val selectedId = ProfileStorage.getSelectedProfileId(this)
                if (profile.id == selectedId) {
                    updateActiveProfileCard()
                }

                dialog.dismiss()
            }
        }
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        // Оптимизация: зареждаме само ако има промени или ако няма текущ профил
        if (profiles.isEmpty() || currentSelectedProfile == null) {
            loadProfiles()
            updateActiveProfileCard()
            updateProfileCount()
        } else {
            // Само обновяваме брояча и UI ако е нужно
            updateProfileCount()
        }
        
        // Обновяваме статистиките в adapter-а
        adapter.notifyDataSetChanged()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Изчистваме executor-а
        imageLoadExecutor.shutdown()
        // Изчистваме adapter ресурсите
        adapter.cleanup()
    }

    // Навигационни функции
    private fun setupNavigationBar() {
        val navDrag = findViewById<LinearLayout>(R.id.navDrag)
        val navMap = findViewById<LinearLayout>(R.id.navMap)
        val navTrack = findViewById<LinearLayout>(R.id.navTrack)
        val navOptions = findViewById<LinearLayout>(R.id.navOptions)


        navDrag?.setOnClickListener {
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
                .withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    navigateToDrag()
                }.start()
        }

        navMap?.setOnClickListener {
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
                .withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    navigateToMap()
                }.start()
        }

        navTrack?.setOnClickListener {
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
                .withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    navigateToTrack()
                }.start()
        }
        navOptions?.setOnClickListener {
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
                .withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    navigateToSettings()
                }.start()
        }
    }

}

// ProfileAdapter с реални статистики
class ProfileAdapter(
    private val profiles: List<Profile>,
    private val context: Context,
    private val onProfileClick: (Profile) -> Unit,
    private val onEditClick: (Profile) -> Unit,
    private val onDeleteClick: (Profile) -> Unit
) : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {
    
    // Executor за асинхронно зареждане на снимки
    private val imageLoadExecutor = java.util.concurrent.Executors.newFixedThreadPool(2)
    
    // Кеш за bitmap-и
    private val imageCache = mutableMapOf<String, Bitmap>()
    
    // Кеш за статистики
    private var statsCache: Map<Long, ProfileStats>? = null

    inner class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvProfileName)
        val tvType: TextView = itemView.findViewById(R.id.tvVehicleType)
        val ivProfileIcon: ImageView = itemView.findViewById(R.id.ivProfileIcon)
        val btnOptions: ImageButton = itemView.findViewById(R.id.btnOptions)

        // Статистики чипове
        val tvSessionCount: TextView = itemView.findViewById(R.id.tvSessionCount)
        val tvMaxSpeed: TextView = itemView.findViewById(R.id.tvMaxSpeed)

        init {
            itemView.setOnClickListener {
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onProfileClick(profiles[bindingAdapterPosition])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = profiles[position]
        holder.tvName.text = profile.name

        // Задаваме икона според типа превозно средство
        val (iconRes, emoji) = when (profile.vehicleType) {
            Profile.VehicleType.CAR -> Pair(R.drawable.ic_car, "🚗")
            Profile.VehicleType.MOTORCYCLE -> Pair(R.drawable.ic_motorcycle, "🏍️")
        }

        // Зареждаме снимка асинхронно
        loadProfileImageAsync(holder, profile, iconRes)
        
        holder.tvType.text = "$emoji ${getVehicleTypeText(profile.vehicleType)}"

        // Зареждаме реални статистики за профила (с кеширане)
        val profileStats = getProfileStatisticsCached(profile.id)

        // Обновяваме статистика чиповете
        holder.tvSessionCount.text = context.getString(R.string.garage_sessions_template, profileStats.sessionCount)

        if (profileStats.maxSpeed > 0f) {
            holder.tvMaxSpeed.text = "🏁 ${profileStats.maxSpeed.toInt()} km/h"
        } else {
            holder.tvMaxSpeed.text = context.getString(R.string.garage_max_speed_template, "--")
        }

        holder.btnOptions.setOnClickListener { view ->
            PopupMenu(view.context, view).apply {
                menu.add(0, 1, 0, "✏️ ${view.context.getString(R.string.profile_edit_button)}")
                menu.add(0, 2, 1, "🗑️ ${view.context.getString(R.string.profile_delete_button)}")
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> onEditClick(profile)
                        2 -> onDeleteClick(profile)
                    }
                    true
                }
            }.show()
        }
    }

    private fun loadProfileImageAsync(holder: ProfileViewHolder, profile: Profile, iconRes: Int) {
        // Показваме иконката първо като placeholder
        showIconInAdapter(holder.ivProfileIcon, iconRes)
        
        val imagePath = profile.imagePath
        if (imagePath.isNullOrEmpty()) {
            return
        }
        
        val imageFile = File(context.getExternalFilesDir(null), imagePath)
        if (!imageFile.exists()) {
            return
        }
        
        // Проверяваме кеша
        val cachedBitmap = imageCache[imagePath]
        if (cachedBitmap != null) {
            setImageInAdapter(holder.ivProfileIcon, cachedBitmap)
            return
        }
        
        // Зареждаме асинхронно
        val currentPosition = holder.bindingAdapterPosition
        imageLoadExecutor.execute {
            try {
                val bitmap = decodeSampledBitmapFromFile(imageFile.absolutePath, 200, 200)
                if (bitmap != null) {
                    imageCache[imagePath] = bitmap
                    // Обновяваме UI само ако holder все още е на същата позиция
                    (context as? android.app.Activity)?.runOnUiThread {
                        if (holder.bindingAdapterPosition == currentPosition) {
                            setImageInAdapter(holder.ivProfileIcon, bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileAdapter", "Error loading image", e)
            }
        }
    }
    
    private fun setImageInAdapter(imageView: ImageView, bitmap: Bitmap) {
        imageView.setImageBitmap(bitmap)
        // Задаваме размерите да запълнят FrameLayout-а (48dp x 48dp)
        val layoutParams = imageView.layoutParams
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        imageView.layoutParams = layoutParams
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.imageTintList = null
        // Премахваме бялия бекграунд когато показваме снимка
        (imageView.parent as? ViewGroup)?.background = null
        imageView.clipToOutline = true
        imageView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }
    
    // Метод за изчистване на кеша за конкретен imagePath
    fun clearImageCacheForPath(imagePath: String?) {
        if (!imagePath.isNullOrEmpty()) {
            imageCache.remove(imagePath)
        }
    }
    
    private fun getProfileStatisticsCached(profileId: Long): ProfileStats {
        // Зареждаме статистиките веднъж и ги кешираме
        if (statsCache == null) {
            val allRaces = RouteStorage.loadRaces(context)
            val allDragSessions = DragStorage.loadDragSessions(context)
            
            statsCache = profiles.associate { profile ->
                val profileRaces = allRaces.filter { it.profileId == profile.id }
                val profileDragSessions = allDragSessions.filter { it.profileId == profile.id }
                
                val sessionCount = profileRaces.size + profileDragSessions.size
                
                val raceMaxSpeed = profileRaces.maxOfOrNull { it.maxSpeed } ?: 0f
                val dragMaxSpeed = profileDragSessions.flatMap { it.attempts }
                    .maxOfOrNull { it.maxSpeed } ?: 0f
                val maxSpeed = maxOf(raceMaxSpeed, dragMaxSpeed)
                
                profile.id to ProfileStats(sessionCount, maxSpeed)
            }
        }
        return statsCache?.get(profileId) ?: ProfileStats(0, 0f)
    }
    
    private fun getProfileStatistics(profileId: Long): ProfileStats {
        val allRaces = RouteStorage.loadRaces(context)
        val profileRaces = allRaces.filter { it.profileId == profileId }

        val allDragSessions = DragStorage.loadDragSessions(context)
        val profileDragSessions = allDragSessions.filter { it.profileId == profileId }

        val sessionCount = profileRaces.size + profileDragSessions.size

        val raceMaxSpeed = profileRaces.maxOfOrNull { it.maxSpeed } ?: 0f
        val dragMaxSpeed = profileDragSessions.flatMap { it.attempts }
            .maxOfOrNull { it.maxSpeed } ?: 0f
        val maxSpeed = maxOf(raceMaxSpeed, dragMaxSpeed)

        return ProfileStats(sessionCount, maxSpeed)
    }

    private fun getVehicleTypeText(vehicleType: Profile.VehicleType): String {
        return when (vehicleType) {
            Profile.VehicleType.CAR -> context.getString(R.string.vehicle_type_car)
            Profile.VehicleType.MOTORCYCLE -> context.getString(R.string.vehicle_type_motorcycle)
        }
    }

    override fun getItemCount() = profiles.size
    
    // Метод за изчистване на ресурсите
    fun cleanup() {
        imageLoadExecutor.shutdown()
        imageCache.clear()
        statsCache = null
    }
    
    // Функция за зареждане на bitmap с downsampling
    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            // Първо прочитаме размерите на снимката
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)
            
            // Изчисляваме inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            
            // Зареждаме bitmap-а с правилния размер
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565 // По-малко памет
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            android.util.Log.e("ProfileAdapter", "Error decoding bitmap", e)
            null
        }
    }
    
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }

    // Data class за статистиките
    data class ProfileStats(
        val sessionCount: Int,
        val maxSpeed: Float
    )
    
    private fun showIconInAdapter(imageView: ImageView, iconRes: Int) {
        // За иконка в adapter - 24dp ширина и височина с centerInside
        val layoutParams = imageView.layoutParams
        val sizeInPx = (24 * context.resources.displayMetrics.density).toInt()
        layoutParams.width = sizeInPx
        layoutParams.height = sizeInPx
        imageView.layoutParams = layoutParams
        imageView.setImageResource(iconRes)
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageView.imageTintList = ContextCompat.getColorStateList(context, R.color.primary_color)
        // Премахваме кръглия outline за иконката
        imageView.clipToOutline = false
        imageView.outlineProvider = null
    }
}



// ProfileStorage остава същия
object ProfileStorage {
    private const val PREFS_KEY = "profiles"
    private const val SELECTED_PROFILE_KEY = "selected_profile_id"

    fun saveProfiles(context: Context, profiles: List<Profile>) {
        val json = Gson().toJson(profiles)
        val prefs = context.getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(PREFS_KEY, json)

        val selectedId = prefs.getLong(SELECTED_PROFILE_KEY, -1)
        val selectedExists = profiles.any { it.id == selectedId }
        if (!selectedExists) {
            editor.remove(SELECTED_PROFILE_KEY)
        }

        editor.apply()
    }

    fun loadProfiles(context: Context): MutableList<Profile> {
        val prefs = context.getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE)
        val json = prefs.getString(PREFS_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<Profile>>() {}.type
            Gson().fromJson(json, type) ?: mutableListOf()
        } else mutableListOf()
    }

    fun saveSelectedProfile(context: Context, profileId: Long) {
        context.getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE)
            .edit().putLong(SELECTED_PROFILE_KEY, profileId).apply()
    }

    fun getSelectedProfileId(context: Context): Long =
        context.getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE)
            .getLong(SELECTED_PROFILE_KEY, -1)

    fun saveNewProfile(context: Context, profile: Profile) {
        val list = loadProfiles(context)
        list.add(profile)
        saveProfiles(context, list)
    }


}
