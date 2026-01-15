package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.data.VehicleData
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Fragment за Garage страницата - конвертиран от GarageActivity с ПЪЛНА функционалност
 */
class GarageFragment : Fragment() {
    
    private lateinit var adapter: ProfileAdapter
    private lateinit var btnAddProfile: ExtendedFloatingActionButton
    private lateinit var cardActiveProfile: MaterialCardView
    private lateinit var tvActiveProfileName: TextView
    private lateinit var tvActiveProfileType: TextView
    private lateinit var ivActiveProfileIcon: ImageView
    private lateinit var flProfileImageContainer: FrameLayout
    private lateinit var btnChangeProfile: MaterialButton
    private lateinit var tvProfileCount: TextView
    private lateinit var recyclerView: RecyclerView
    
    private val profiles = mutableListOf<Profile>()
    private var currentSelectedProfile: Profile? = null
    
    private val imageLoadExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val imageCache = mutableMapOf<String, Bitmap>()
    
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { saveProfileImage(it) }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_garage, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        requireActivity().window.statusBarColor = ContextCompat.getColor(requireContext(), R.color.header_gradient_start)
        
        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()
        
        loadProfiles()
        updateAddButtonState()
        updateActiveProfileCard()
        updateProfileCount()
    }
    
    private fun initializeViews(view: View) {
        btnAddProfile = view.findViewById(R.id.btnAddProfile)
        cardActiveProfile = view.findViewById(R.id.cardActiveProfile)
        tvActiveProfileName = view.findViewById(R.id.tvActiveProfileName)
        tvActiveProfileType = view.findViewById(R.id.tvActiveProfileType)
        ivActiveProfileIcon = view.findViewById(R.id.ivActiveProfileIcon)
        flProfileImageContainer = view.findViewById(R.id.flProfileImageContainer)
        btnChangeProfile = view.findViewById(R.id.btnChangeProfile)
        tvProfileCount = view.findViewById(R.id.tvProfileCount)
        recyclerView = view.findViewById(R.id.rvProfiles)
    }
    
    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = ProfileAdapter(
            profiles,
            requireContext(),
            onProfileClick = { profile ->
                val intent = Intent(requireContext(), ProfileDetailActivity::class.java).apply {
                    putExtra("profile_id", profile.id)
                }
                startActivity(intent)
            },
            onEditClick = { profile -> showEditProfileDialog(profile) },
            onDeleteClick = { profile -> deleteProfileWithAnimation(profile) }
        )
        recyclerView.adapter = adapter
    }
    
    private fun setupClickListeners() {
        btnAddProfile.setOnClickListener { showCreateProfileDialog() }
        btnChangeProfile.setOnClickListener { showQuickProfileSelection() }
        flProfileImageContainer.setOnClickListener { showImageSelectionDialog() }
    }
    
    private fun loadProfiles() {
        profiles.clear()
        profiles.addAll(ProfileStorage.loadProfiles(requireContext()))
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
        
        val color = when {
            profiles.size >= 5 -> ContextCompat.getColor(requireContext(), R.color.warning_color)
            profiles.size >= 3 -> ContextCompat.getColor(requireContext(), R.color.success_color)
            else -> ContextCompat.getColor(requireContext(), R.color.accent_color)
        }
        tvProfileCount.setTextColor(color)
    }
    
    private fun updateActiveProfileCard() {
        val selectedId = ProfileStorage.getSelectedProfileId(requireContext())
        var selectedProfile = profiles.find { it.id == selectedId }
        
        if (selectedProfile == null && profiles.isNotEmpty()) {
            selectedProfile = profiles.first()
            ProfileStorage.saveSelectedProfile(requireContext(), selectedProfile.id)
        }
        
        selectedProfile?.let { profile ->
            currentSelectedProfile = profile
            tvActiveProfileName.text = profile.name
            
            val (iconRes, emoji, typeText) = when (profile.vehicleType) {
                Profile.VehicleType.CAR -> Triple(R.drawable.ic_car, "🚗", getString(R.string.garage_vehicle_car))
                Profile.VehicleType.MOTORCYCLE -> Triple(R.drawable.ic_motorcycle, "🏍️", getString(R.string.garage_vehicle_motorcycle))
            }
            
            tvActiveProfileType.text = typeText
            
            val imagePath = profile.imagePath
            if (!imagePath.isNullOrEmpty()) {
                val imageFile = File(requireContext().getExternalFilesDir(null), imagePath)
                if (imageFile.exists()) {
                    val cachedBitmap = imageCache[imagePath]
                    if (cachedBitmap != null) {
                        setImageBitmap(cachedBitmap)
                    } else {
                        val currentProfileId = profile.id
                        imageLoadExecutor.execute {
                            try {
                                val bitmap = decodeSampledBitmapFromFile(imageFile.absolutePath, 800, 800)
                                if (bitmap != null) {
                                    imageCache[imagePath] = bitmap
                                    requireActivity().runOnUiThread {
                                        if (currentSelectedProfile?.id == currentProfileId) {
                                            setImageBitmap(bitmap)
                                        }
                                    }
                                } else {
                                    requireActivity().runOnUiThread {
                                        if (currentSelectedProfile?.id == currentProfileId) {
                                            showIcon(iconRes)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("GarageFragment", "Error loading image", e)
                                requireActivity().runOnUiThread {
                                    if (currentSelectedProfile?.id == currentProfileId) {
                                        showIcon(iconRes)
                                    }
                                }
                            }
                        }
                        showIcon(iconRes)
                    }
                } else {
                    showIcon(iconRes)
                }
            } else {
                showIcon(iconRes)
            }
            
            cardActiveProfile.visibility = View.VISIBLE
            btnChangeProfile.visibility = if (profiles.size > 1) View.VISIBLE else View.GONE
            
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
        val allRaces = RouteStorage.loadRaces(requireContext())
        return allRaces.count { it.profileId == profileId }
    }
    
    private fun deleteProfileWithAnimation(profile: Profile) {
        val selectedId = ProfileStorage.getSelectedProfileId(requireContext())
        if (profile.id == selectedId) {
            Toast.makeText(requireContext(), "❌ Не можете да изтриете активния профил", Toast.LENGTH_LONG).show()
            return
        }
        
        val sessionCount = getProfileSessionCount(profile.id)
        val message = if (sessionCount > 0) {
            getString(R.string.garage_confirm_delete, profile.name, sessionCount)
        } else {
            getString(R.string.garage_confirm_delete_simple, profile.name)
        }
        
        val deleteDialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle("🗑️ Изтриване на профил")
            .setMessage(message)
            .setPositiveButton(getString(R.string.garage_delete_button)) { _, _ ->
                if (sessionCount > 0) {
                    val allRaces = RouteStorage.loadRaces(requireContext()).toMutableList()
                    allRaces.removeAll { it.profileId == profile.id }
                    RouteStorage.saveRaces(requireContext(), allRaces)
                }
                
                profiles.remove(profile)
                ProfileStorage.saveProfiles(requireContext(), profiles)
                adapter.notifyDataSetChanged()
                updateAddButtonState()
                updateProfileCount()
                updateActiveProfileCard()
                
                val successMessage = if (sessionCount > 0) {
                    "✅ Профилът и всички негови $sessionCount сесии са изтрити"
                } else {
                    "✅ Профилът е изтрит успешно"
                }
                Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show()
                
                if (profiles.isEmpty()) {
                    startActivity(Intent(requireContext(), FirstProfileActivity::class.java))
                    requireActivity().finish()
                }
            }
            .setNegativeButton(getString(R.string.garage_cancel_button), null)
            .create()
        
        DialogHelper.styleDialogButtons(deleteDialog)
        deleteDialog.show()
    }
    
    private fun showQuickProfileSelection() {
        if (profiles.size <= 1) {
            Toast.makeText(requireContext(), "ℹ️ Няма други профили за избор", Toast.LENGTH_SHORT).show()
            return
        }
        
        val selectedId = ProfileStorage.getSelectedProfileId(requireContext())
        val otherProfiles = profiles.filter { it.id != selectedId }
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_vehicle, null)
        val rvProfileOptions = dialogView.findViewById<RecyclerView>(R.id.rvProfileOptions)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)
        
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()
        
        rvProfileOptions.layoutManager = LinearLayoutManager(requireContext())
        val profileOptionsAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_profile_option, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }
            
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val profile = otherProfiles[position]
                val ivVehicleIcon = holder.itemView.findViewById<ImageView>(R.id.ivVehicleIcon)
                val tvProfileName = holder.itemView.findViewById<TextView>(R.id.tvProfileName)
                
                val iconRes = when (profile.vehicleType) {
                    Profile.VehicleType.CAR -> R.drawable.ic_car
                    Profile.VehicleType.MOTORCYCLE -> R.drawable.ic_motorcycle
                }
                ivVehicleIcon.setImageResource(iconRes)
                tvProfileName.text = profile.name
                
                holder.itemView.setOnClickListener {
                    dialog.dismiss()
                    val newProfile = otherProfiles[position]
                    ProfileStorage.saveSelectedProfile(requireContext(), newProfile.id)
                    updateActiveProfileCard()
                    adapter.notifyDataSetChanged()
                    Toast.makeText(requireContext(), "✅ Сега караш: ${newProfile.name}", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun getItemCount(): Int = otherProfiles.size
        }
        rvProfileOptions.adapter = profileOptionsAdapter
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showCreateProfileDialog() {
        if (profiles.size >= 5) {
            Toast.makeText(requireContext(), "⚠️ Максимум 5 профила са разрешени", Toast.LENGTH_LONG).show()
            return
        }
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_profile, null)
        
        val carCard = dialogView.findViewById<LinearLayout>(R.id.carCard)
        val motorcycleCard = dialogView.findViewById<LinearLayout>(R.id.motorcycleCard)
        val brandInput = dialogView.findViewById<TextInputLayout>(R.id.brandInput)
        val modelInput = dialogView.findViewById<TextInputLayout>(R.id.modelInput)
        val brandDropdown = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.brandDropdown)
        val modelDropdown = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.modelDropdown)
        
        var selectedVehicleType = Profile.VehicleType.CAR
        var selectedBrand = ""
        var selectedModel = ""
        
        val carBrands = VehicleData.carBrands
        val carModels = VehicleData.carModels
        val motorcycleBrands = VehicleData.motorcycleBrands
        val motorcycleModels = VehicleData.motorcycleModels
        
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
        
        brandDropdown.setOnItemClickListener { _, _, position, _ ->
            val brands = if (selectedVehicleType == Profile.VehicleType.CAR) {
                carBrands
            } else {
                motorcycleBrands
            }
            
            if (brands[position] == getString(R.string.garage_most_popular)) {
                return@setOnItemClickListener
            }
            
            selectedBrand = brands[position]
            updateModelDropdown(modelDropdown, selectedBrand, if (selectedVehicleType == Profile.VehicleType.CAR) carModels else motorcycleModels)
        }
        
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
        
        updateSelection(carCard, motorcycleCard)
        updateBrandDropdown(brandDropdown, carBrands)
        updateVehicleIcon(brandInput, selectedVehicleType)
        
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
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
                ProfileStorage.saveProfiles(requireContext(), profiles)
                adapter.notifyDataSetChanged()
                updateAddButtonState()
                updateProfileCount()
                
                if (profiles.size == 1) {
                    ProfileStorage.saveSelectedProfile(requireContext(), newProfile.id)
                }
                
                updateActiveProfileCard()
                dialog.dismiss()
                
                val intent = Intent(requireContext(), DragCalibrationActivity::class.java).apply {
                    putExtra("PROFILE_ID", newProfile.id)
                    putExtra("IS_NEW_PROFILE", true)
                }
                startActivity(intent)
            }
        }
        dialog.show()
    }
    
    private fun updateSelection(selectedCard: LinearLayout, unselectedCard: LinearLayout) {
        selectedCard.background = ContextCompat.getDrawable(requireContext(), R.drawable.vehicle_option_selected_background)
        unselectedCard.background = ContextCompat.getDrawable(requireContext(), R.drawable.vehicle_option_background)
    }
    
    private fun updateBrandDropdown(dropdown: MaterialAutoCompleteTextView, brands: Array<String>) {
        val popularIndex = brands.indexOf(getString(R.string.garage_most_popular))
        val firstRegularBrandIndex = if (popularIndex >= 0) popularIndex + 8 else 0
        
        val adapter = object : ArrayAdapter<String>(requireContext(), 0, brands) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val layoutInflater = LayoutInflater.from(context)
                val view: View
                
                when {
                    brands[position] == getString(R.string.garage_most_popular) -> {
                        view = layoutInflater.inflate(R.layout.dropdown_item_popular, parent, false)
                        val textView = view.findViewById<TextView>(R.id.text1)
                        textView.text = getString(R.string.garage_most_popular)
                    }
                    position == firstRegularBrandIndex && popularIndex >= 0 -> {
                        view = layoutInflater.inflate(R.layout.dropdown_item_separator, parent, false)
                    }
                    else -> {
                        view = layoutInflater.inflate(R.layout.dropdown_item_normal, parent, false)
                        val textView = view.findViewById<TextView>(R.id.text1)
                        textView.text = brands[position]
                    }
                }
                
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
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
        
        val adapter = object : ArrayAdapter<String>(requireContext(), 0, models) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val layoutInflater = LayoutInflater.from(context)
                val view = layoutInflater.inflate(R.layout.dropdown_item_normal, parent, false)
                val textView = view.findViewById<TextView>(R.id.text1)
                textView.text = models[position]
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
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
                brandInput.startIconDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_car)
            }
            Profile.VehicleType.MOTORCYCLE -> {
                brandInput.startIconDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_motorcycle)
            }
        }
    }
    
    private fun showImageSelectionDialog() {
        val profile = currentSelectedProfile ?: return
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_image_selection, null)
        
        val llSelectImage = dialogView.findViewById<LinearLayout>(R.id.llSelectImage)
        val llRemoveImage = dialogView.findViewById<LinearLayout>(R.id.llRemoveImage)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)
        
        if (!profile.imagePath.isNullOrEmpty()) {
            llRemoveImage.visibility = View.VISIBLE
        } else {
            llRemoveImage.visibility = View.GONE
        }
        
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()
        
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
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                
                val correctedBitmap = correctImageOrientation(uri, bitmap)
                
                val imagesDir = File(requireContext().getExternalFilesDir(null), "profile_images")
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }
                
                val imageFile = File(imagesDir, "profile_${profile.id}.jpg")
                val outputStream = FileOutputStream(imageFile)
                correctedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.flush()
                outputStream.close()
                
                val newImagePath = "profile_images/profile_${profile.id}.jpg"
                val oldImagePath = profile.imagePath
                profile.imagePath = newImagePath
                ProfileStorage.saveProfiles(requireContext(), profiles)
                
                if (!oldImagePath.isNullOrEmpty()) {
                    imageCache.remove(oldImagePath)
                }
                
                val maxSize = 800
                val resizedBitmap = if (correctedBitmap.width > maxSize || correctedBitmap.height > maxSize) {
                    val scale = minOf(maxSize.toFloat() / correctedBitmap.width, maxSize.toFloat() / correctedBitmap.height)
                    val newWidth = (correctedBitmap.width * scale).toInt()
                    val newHeight = (correctedBitmap.height * scale).toInt()
                    Bitmap.createScaledBitmap(correctedBitmap, newWidth, newHeight, true)
                } else {
                    correctedBitmap
                }
                imageCache[newImagePath] = resizedBitmap
                
                if (!oldImagePath.isNullOrEmpty()) {
                    imageCache.remove(oldImagePath)
                }
                
                withContext(Dispatchers.Main) {
                    imageCache.remove(newImagePath)
                    updateActiveProfileCard()
                    
                    adapter.clearImageCacheForPath(oldImagePath)
                    adapter.clearImageCacheForPath(newImagePath)
                    
                    val profileIndex = profiles.indexOfFirst { it.id == profile.id }
                    if (profileIndex != -1) {
                        adapter.notifyItemChanged(profileIndex)
                    }
                    
                    Toast.makeText(requireContext(), "✅ Снимката е запазена", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("GarageFragment", "Error saving image: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "❌ Грешка при запазване на снимката", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun correctImageOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            var orientation = ExifInterface.ORIENTATION_NORMAL
            
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val exif = ExifInterface(inputStream)
                        orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    }
                } catch (e: Exception) {
                    Log.w("GarageFragment", "Could not read EXIF from stream: ${e.message}")
                } finally {
                    inputStream.close()
                }
            }
            
            if (orientation == ExifInterface.ORIENTATION_NORMAL) {
                val filePath = getRealPathFromURI(uri)
                if (filePath != null) {
                    try {
                        val exif = ExifInterface(filePath)
                        orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    } catch (e: Exception) {
                        Log.w("GarageFragment", "Could not read EXIF from file path: ${e.message}")
                    }
                }
            }
            
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
                else -> return bitmap
            }
            
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Log.e("GarageFragment", "Error correcting orientation: ${e.message}", e)
            bitmap
        }
    }
    
    private fun getRealPathFromURI(uri: Uri): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                null
            } else {
                val projection = arrayOf(MediaStore.Images.Media.DATA)
                val cursor = requireContext().contentResolver.query(uri, projection, null, null, null)
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
            val imageFile = File(requireContext().getExternalFilesDir(null), oldImagePath)
            if (imageFile.exists()) {
                imageFile.delete()
            }
            imageCache.remove(oldImagePath)
            profile.imagePath = null
            ProfileStorage.saveProfiles(requireContext(), profiles)
            
            val (iconRes, emoji, typeText) = when (profile.vehicleType) {
                Profile.VehicleType.CAR -> Triple(R.drawable.ic_car, "🚗", getString(R.string.garage_vehicle_car))
                Profile.VehicleType.MOTORCYCLE -> Triple(R.drawable.ic_motorcycle, "🏍️", getString(R.string.garage_vehicle_motorcycle))
            }
            showIcon(iconRes)
            tvActiveProfileName.text = profile.name
            tvActiveProfileType.text = typeText
            
            adapter.notifyDataSetChanged()
            Toast.makeText(requireContext(), "✅ Снимката е премахната", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showIcon(iconRes: Int) {
        val containerLayoutParams = flProfileImageContainer.layoutParams as LinearLayout.LayoutParams
        val containerSizeInPx = (48 * resources.displayMetrics.density).toInt()
        containerLayoutParams.width = containerSizeInPx
        containerLayoutParams.height = containerSizeInPx
        containerLayoutParams.topMargin = (30 * resources.displayMetrics.density).toInt()
        containerLayoutParams.gravity = android.view.Gravity.CENTER_HORIZONTAL
        flProfileImageContainer.layoutParams = containerLayoutParams
        flProfileImageContainer.background = ContextCompat.getDrawable(requireContext(), R.drawable.profile_icon_background)
        
        val layoutParams = ivActiveProfileIcon.layoutParams as FrameLayout.LayoutParams
        val sizeInPx = (24 * resources.displayMetrics.density).toInt()
        layoutParams.width = sizeInPx
        layoutParams.height = sizeInPx
        layoutParams.gravity = android.view.Gravity.CENTER
        ivActiveProfileIcon.layoutParams = layoutParams
        ivActiveProfileIcon.setImageResource(iconRes)
        ivActiveProfileIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
        ivActiveProfileIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary_color)
        ivActiveProfileIcon.clipToOutline = false
        ivActiveProfileIcon.outlineProvider = null
    }
    
    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)
            
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565
            BitmapFactory.decodeFile(path, options)
        } catch (e: Exception) {
            Log.e("GarageFragment", "Error decoding bitmap", e)
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
        val containerLayoutParams = flProfileImageContainer.layoutParams as ViewGroup.MarginLayoutParams
        containerLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        containerLayoutParams.height = (130 * resources.displayMetrics.density).toInt()
        containerLayoutParams.topMargin = 0
        containerLayoutParams.bottomMargin = 0
        containerLayoutParams.marginStart = 0
        containerLayoutParams.marginEnd = 0
        flProfileImageContainer.layoutParams = containerLayoutParams
        
        (flProfileImageContainer.parent as? LinearLayout)?.let { parent ->
            val parentLayoutParams = flProfileImageContainer.layoutParams as LinearLayout.LayoutParams
            parentLayoutParams.gravity = android.view.Gravity.FILL_HORIZONTAL
            flProfileImageContainer.layoutParams = parentLayoutParams
        }
        
        flProfileImageContainer.background = null
        
        val layoutParams = ivActiveProfileIcon.layoutParams as FrameLayout.LayoutParams
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams.gravity = android.view.Gravity.FILL
        ivActiveProfileIcon.layoutParams = layoutParams
        ivActiveProfileIcon.scaleType = ImageView.ScaleType.CENTER_CROP
        ivActiveProfileIcon.imageTintList = null
        ivActiveProfileIcon.clipToOutline = false
        ivActiveProfileIcon.outlineProvider = null
    }
    
    private fun showEditProfileDialog(profile: Profile) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_profile, null)
        val etName = dialogView.findViewById<EditText>(R.id.etProfileName)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinnerVehicleType)
        
        etName.setText(profile.name)
        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.vehicle_types)
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                return view
            }
            
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerType.adapter = adapter
        spinnerType.setSelection(if (profile.vehicleType == Profile.VehicleType.CAR) 0 else 1)
        
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
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
                ProfileStorage.saveProfiles(requireContext(), profiles)
                adapter.notifyDataSetChanged()
                
                val selectedId = ProfileStorage.getSelectedProfileId(requireContext())
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
        if (profiles.isEmpty() || currentSelectedProfile == null) {
            loadProfiles()
            updateActiveProfileCard()
            updateProfileCount()
        } else {
            updateProfileCount()
        }
        
        adapter.notifyDataSetChanged()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        imageLoadExecutor.shutdown()
        adapter.cleanup()
    }
}
