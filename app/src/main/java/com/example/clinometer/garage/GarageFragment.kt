package com.example.clinometer.garage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
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
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ListView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.*
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.data.VehicleData
import com.example.clinometer.main.MainContainerActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
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
    private lateinit var btnAddProfile: MaterialButton
    private var btnViewSessions: MaterialButton? = null
    private var btnEmptyAddProfile: MaterialButton? = null
    private var emptyStateContainer: LinearLayout? = null
    private lateinit var cardActiveProfile: MaterialCardView
    private lateinit var ivActiveProfileIcon: ImageView
    private lateinit var ivActivePlaceholderIcon: ImageView
    private lateinit var llEmptyPhoto: LinearLayout
    private lateinit var flProfileImageContainer: FrameLayout
    private var tvActiveProfileNameOverlay: TextView? = null
    private var tvActiveProfileMetaOverlay: TextView? = null
    private var tvActiveProfileBadge: TextView? = null
    private lateinit var tvProfileCount: TextView
    private var recyclerView: RecyclerView? = null
    
    private val profiles = mutableListOf<Profile>()
    private var currentSelectedProfile: Profile? = null
    private val sessionCounts = mutableMapOf<Long, Int>()
    private val calibrationStatuses = mutableMapOf<Long, Boolean>()
    
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
        loadProfileStatsAsync()
    }
    
    private fun initializeViews(view: View) {
        btnAddProfile = view.findViewById(R.id.btnAddProfile)
        btnViewSessions = view.findViewById(R.id.btnViewSessions)
        btnEmptyAddProfile = view.findViewById(R.id.btnEmptyAddProfile)
        emptyStateContainer = view.findViewById(R.id.emptyStateContainer)
        cardActiveProfile = view.findViewById(R.id.cardActiveProfile)
        ivActiveProfileIcon = view.findViewById(R.id.ivActiveProfileIcon)
        ivActivePlaceholderIcon = view.findViewById(R.id.ivActivePlaceholderIcon)
        llEmptyPhoto = view.findViewById(R.id.llEmptyPhoto)
        flProfileImageContainer = view.findViewById(R.id.flProfileImageContainer)
        tvProfileCount = view.findViewById(R.id.tvProfileCount)
        recyclerView = view.findViewById(R.id.rvProfiles)
        tvActiveProfileNameOverlay = view.findViewById(R.id.tvActiveProfileNameOverlay)
        tvActiveProfileMetaOverlay = view.findViewById(R.id.tvActiveProfileMetaOverlay)
        tvActiveProfileBadge = view.findViewById(R.id.tvActiveProfileBadge)
    }
    
    private fun setupRecyclerView() {
        val rv = recyclerView ?: return
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = ProfileAdapter(
            profiles,
            requireContext(),
            onProfileClick = { profile ->
                openProfilePage(profile)
            },
            onEditClick = { profile -> showEditProfileDialog(profile) },
            onDeleteClick = { profile -> deleteProfileWithAnimation(profile) },
            onActivateClick = { profile -> showChangeProfileConfirmation(profile) }
        )
        rv.adapter = adapter
    }
    
    private fun setupClickListeners() {
        btnAddProfile.setOnClickListener { showCreateProfileDialog() }
        btnEmptyAddProfile?.setOnClickListener { showCreateProfileDialog() }
        btnViewSessions?.setOnClickListener {
            val activity = requireActivity()
            if (activity is MainContainerActivity) {
                activity.navigateToPage(MainContainerActivity.PAGE_RACES)
            } else {
                val intent = Intent(requireContext(), MainContainerActivity::class.java).apply {
                    putExtra(MainContainerActivity.EXTRA_INITIAL_PAGE, MainContainerActivity.PAGE_RACES)
                }
                startActivity(intent)
            }
        }
        flProfileImageContainer.setOnClickListener { showImageSelectionDialog() }
    }
    
    private fun loadProfiles() {
        profiles.clear()
        profiles.addAll(ProfileStorage.loadProfiles(requireContext()))
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun loadProfileStatsAsync() {
        val appContext = requireContext().applicationContext
        val profilesSnapshot = profiles.toList()
        lifecycleScope.launch {
            val counts = withContext(Dispatchers.IO) {
                val allRaces = RouteStorage.loadRaces(appContext)
                val map = mutableMapOf<Long, Int>()
                profilesSnapshot.forEach { profile ->
                    map[profile.id] = allRaces.count { it.profileId == profile.id }
                }
                map
            }

            val calibrations = withContext(Dispatchers.IO) {
                profilesSnapshot.associate { profile ->
                    profile.id to DragCalibration.isProfileCalibrated(appContext, profile.id)
                }
            }

            sessionCounts.clear()
            sessionCounts.putAll(counts)
            calibrationStatuses.clear()
            calibrationStatuses.putAll(calibrations)
            adapter.setSessionCounts(sessionCounts)
            adapter.setCalibrationStatuses(calibrationStatuses)
            updateActiveProfileCard()
            adapter.notifyDataSetChanged()
        }
    }

    private fun updateEmptyState() {
        val isEmpty = profiles.isEmpty()
        emptyStateContainer?.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView?.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    private fun updateAddButtonState() {
        if (profiles.size >= 5) {
            btnAddProfile.text = ""
            btnAddProfile.isEnabled = false
            btnAddProfile.alpha = 0.6f
            btnAddProfile.setIconResource(R.drawable.ic_block)
            btnAddProfile.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.card_background)
        } else {
            btnAddProfile.text = ""
            btnAddProfile.isEnabled = true
            btnAddProfile.alpha = 1f
            btnAddProfile.setIconResource(R.drawable.ic_add)
            btnAddProfile.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.accent_color)
        }
    }
    
    private fun updateProfileCount() {
        tvProfileCount.text = "${profiles.size}/5"
        tvProfileCount.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_color))
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
            val (iconRes, typeText) = when (profile.vehicleType) {
                Profile.VehicleType.CAR -> Pair(R.drawable.ic_car, getString(R.string.garage_vehicle_car))
                Profile.VehicleType.MOTORCYCLE -> Pair(R.drawable.ic_motorcycle, getString(R.string.garage_vehicle_motorcycle))
            }

            val sessions = sessionCounts[profile.id]
            val sessionsText = if (sessions != null) {
                getString(R.string.garage_sessions_template, sessions)
            } else {
                "…"
            }

            tvActiveProfileNameOverlay?.text = getGarageDisplayName(profile)
            tvActiveProfileMetaOverlay?.text = "$typeText • $sessionsText"
            tvActiveProfileBadge?.visibility = View.VISIBLE
            
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
                                // Image is already scaled on disk, just load it
                                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
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
                updateEmptyState()
                updateActiveProfileCard()
                loadProfileStatsAsync()
                
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
    
    private fun showChangeProfileConfirmation(profile: Profile) {
        val currentProfileId = ProfileStorage.getSelectedProfileId(requireContext())
        if (profile.id == currentProfileId) {
            // Already selected, no need to show confirmation
            return
        }
        
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle(getString(R.string.garage_change_button))
            .setMessage("Сигурни ли сте, че искате да смените превозното средство на \"${profile.name}\"?")
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                ProfileStorage.saveSelectedProfile(requireContext(), profile.id)
                updateActiveProfileCard()
                adapter.notifyDataSetChanged()
                loadProfileStatsAsync()
                Toast.makeText(requireContext(), "✅ Сега караш: ${profile.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.no), null)
            .create()
        
        DialogHelper.styleDialogButtons(dialog)
        dialog.show()
    }

    private fun openProfilePage(profile: Profile) {
        val intent = Intent(requireContext(), GarageProfilePageActivity::class.java).apply {
            putExtra(GarageProfilePageActivity.EXTRA_PROFILE_ID, profile.id)
        }
        startActivity(intent)
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

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
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
        
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseCreate)
        val carCard = dialogView.findViewById<LinearLayout>(R.id.carCard)
        val motorcycleCard = dialogView.findViewById<LinearLayout>(R.id.motorcycleCard)
        val brandInput = dialogView.findViewById<TextInputLayout>(R.id.brandInput)
        val modelInput = dialogView.findViewById<TextInputLayout>(R.id.modelInput)
        val brandDropdown = dialogView.findViewById<TextInputEditText>(R.id.brandDropdown)
        val modelDropdown = dialogView.findViewById<TextInputEditText>(R.id.modelDropdown)
        val btnCreate = dialogView.findViewById<MaterialButton>(R.id.btnCreateProfile)
        
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
                it.equals(getString(R.string.garage_most_popular), true) || it.contains("Най", true)
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
                Toast.makeText(requireContext(), getString(R.string.garage_select_brand), Toast.LENGTH_SHORT).show()
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
        
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()
        
        btnClose.setOnClickListener { dialog.dismiss() }
        
        btnCreate.setOnClickListener {
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
            updateEmptyState()
            
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
        
        dialog.show()
    }
    
    private fun updateSelection(selectedCard: LinearLayout, unselectedCard: LinearLayout) {
        selectedCard.background = ContextCompat.getDrawable(requireContext(), R.drawable.vehicle_option_selected_background)
        unselectedCard.background = ContextCompat.getDrawable(requireContext(), R.drawable.vehicle_option_background)
    }

    private fun showSearchPicker(title: String, options: List<String>, onSelect: (String) -> Unit) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_search_picker, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvPickerTitle)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnClosePicker)
        val etSearch = dialogView.findViewById<TextInputEditText>(R.id.etSearch)
        val listView = dialogView.findViewById<ListView>(R.id.lvOptions)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tvEmpty)

        tvTitle.text = title

        val adapter = object : ArrayAdapter<String>(requireContext(), R.layout.dropdown_item_normal, R.id.text1, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getView(position, convertView, parent)
            }
        }
        listView.adapter = adapter
        listView.emptyView = tvEmpty

        etSearch.addTextChangedListener { text ->
            adapter.filter.filter(text?.toString() ?: "")
        }

        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position)
            if (item != null) {
                onSelect(item)
            }
            dialog.dismiss()
        }

        dialog.show()
    }
    
    private fun showImageSelectionDialog() {
        val profile = currentSelectedProfile ?: return
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_image_selection, null)
        
        val llSelectImage = dialogView.findViewById<MaterialButton>(R.id.llSelectImage)
        val llRemoveImage = dialogView.findViewById<MaterialButton>(R.id.llRemoveImage)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseImageDialog)
        
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
        
        btnClose.setOnClickListener { dialog.dismiss() }
        
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
                
                // Scale bitmap before saving to disk (max 800px for memory efficiency)
                val maxSize = 800
                val resizedBitmap = if (correctedBitmap.width > maxSize || correctedBitmap.height > maxSize) {
                    val scale = minOf(maxSize.toFloat() / correctedBitmap.width, maxSize.toFloat() / correctedBitmap.height)
                    val newWidth = (correctedBitmap.width * scale).toInt()
                    val newHeight = (correctedBitmap.height * scale).toInt()
                    Bitmap.createScaledBitmap(correctedBitmap, newWidth, newHeight, true)
                } else {
                    correctedBitmap
                }
                
                // Recycle original if it was scaled
                if (resizedBitmap != correctedBitmap) {
                    correctedBitmap.recycle()
                }
                
                val imagesDir = File(requireContext().getExternalFilesDir(null), "profile_images")
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }
                
                val imageFile = File(imagesDir, "profile_${profile.id}.jpg")
                val outputStream = FileOutputStream(imageFile)
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.flush()
                outputStream.close()
                
                val newImagePath = "profile_images/profile_${profile.id}.jpg"
                val oldImagePath = profile.imagePath
                
                // Remove old bitmap from cache (don't recycle - might still be in use)
                if (!oldImagePath.isNullOrEmpty()) {
                    imageCache.remove(oldImagePath)
                }
                
                profile.imagePath = newImagePath
                ProfileStorage.saveProfiles(requireContext(), profiles)
                
                // Remove any existing bitmap for new path (in case of re-upload)
                imageCache.remove(newImagePath)
                
                // Store scaled bitmap in cache for active profile card
                imageCache[newImagePath] = resizedBitmap
                
                withContext(Dispatchers.Main) {
                    // Clear adapter cache for old and new paths (adapter will reload from disk with proper scaling)
                    adapter.clearImageCacheForPath(oldImagePath)
                    adapter.clearImageCacheForPath(newImagePath)
                    
                    updateActiveProfileCard()
                    
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
            // Remove bitmap from cache (don't recycle - might still be in use)
            imageCache.remove(oldImagePath)
            
            profile.imagePath = null
            ProfileStorage.saveProfiles(requireContext(), profiles)
            
            // Clear adapter cache
            adapter.clearImageCacheForPath(oldImagePath)
            
            val (iconRes, typeText) = when (profile.vehicleType) {
                Profile.VehicleType.CAR -> Pair(R.drawable.ic_car, getString(R.string.garage_vehicle_car))
                Profile.VehicleType.MOTORCYCLE -> Pair(R.drawable.ic_motorcycle, getString(R.string.garage_vehicle_motorcycle))
            }
            showIcon(iconRes)
            val sessions = getProfileSessionCount(profile.id)
            val sessionsText = getString(R.string.garage_sessions_template, sessions)
            tvActiveProfileNameOverlay?.text = profile.name
            tvActiveProfileMetaOverlay?.text = "$typeText • $sessionsText"
            
            adapter.notifyDataSetChanged()
            Toast.makeText(requireContext(), "✅ Снимката е премахната", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showIcon(iconRes: Int) {
        ivActiveProfileIcon.visibility = View.GONE
        llEmptyPhoto.visibility = View.VISIBLE
        ivActivePlaceholderIcon.setImageResource(iconRes)
        ivActivePlaceholderIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.accent_color)
    }
    
    private fun setImageBitmap(bitmap: Bitmap) {
        ivActiveProfileIcon.visibility = View.VISIBLE
        ivActiveProfileIcon.setImageBitmap(bitmap)
        ivActiveProfileIcon.scaleType = ImageView.ScaleType.CENTER_CROP
        ivActiveProfileIcon.imageTintList = null
        llEmptyPhoto.visibility = View.GONE
    }
    
    private fun showEditProfileDialog(profile: Profile) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_profile, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etProfileName)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseEdit)
        val btnCar = dialogView.findViewById<LinearLayout>(R.id.btnEditTypeCar)
        val btnMotorcycle = dialogView.findViewById<LinearLayout>(R.id.btnEditTypeMotorcycle)
        val brandInput = dialogView.findViewById<TextInputLayout>(R.id.brandInput)
        val modelInput = dialogView.findViewById<TextInputLayout>(R.id.modelInput)
        val brandDropdown = dialogView.findViewById<TextInputEditText>(R.id.brandDropdown)
        val modelDropdown = dialogView.findViewById<TextInputEditText>(R.id.modelDropdown)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveEdit)
        
        var selectedType = profile.vehicleType
        var selectedBrand = ""
        var selectedModel = ""
        etName.setText(getGarageDisplayName(profile))
        if (selectedType == Profile.VehicleType.CAR) {
            updateSelection(btnCar, btnMotorcycle)
        } else {
            updateSelection(btnMotorcycle, btnCar)
        }

        fun updateModelEnabled() {
            val enabled = selectedBrand.isNotEmpty()
            modelInput.isEnabled = enabled
            modelDropdown.isEnabled = enabled
        }

        fun clearBrandAndModel() {
            selectedBrand = ""
            selectedModel = ""
            brandDropdown.setText("")
            modelDropdown.setText("")
            brandInput.error = null
            modelInput.error = null
            updateModelEnabled()
        }

        fun resolveBrandModelFromName(name: String, vehicleType: Profile.VehicleType): Pair<String, String> {
            val brands = if (vehicleType == Profile.VehicleType.CAR) {
                VehicleData.carBrands.toList()
            } else {
                VehicleData.motorcycleBrands.toList()
            }
            val sortedBrands = brands.sortedByDescending { it.length }
            val trimmedName = name.trim()
            val match = sortedBrands.firstOrNull { brand ->
                trimmedName.equals(brand, true) || trimmedName.startsWith("$brand ", true)
            }
            return if (match != null) {
                val model = trimmedName.removePrefix(match).trim()
                match to model
            } else {
                "" to ""
            }
        }
        
        btnCar.setOnClickListener {
            selectedType = Profile.VehicleType.CAR
            updateSelection(btnCar, btnMotorcycle)
            clearBrandAndModel()
        }
        
        btnMotorcycle.setOnClickListener {
            selectedType = Profile.VehicleType.MOTORCYCLE
            updateSelection(btnMotorcycle, btnCar)
            clearBrandAndModel()
        }

        brandDropdown.setOnClickListener {
            val brandsRaw = if (selectedType == Profile.VehicleType.CAR) {
                VehicleData.carBrands.toList()
            } else {
                VehicleData.motorcycleBrands.toList()
            }
            val brands = brandsRaw.filterNot {
                it.equals(getString(R.string.garage_most_popular), true) || it.contains("Най", true)
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
                Toast.makeText(requireContext(), getString(R.string.garage_select_brand), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val models = if (selectedType == Profile.VehicleType.CAR) {
                VehicleData.carModels[selectedBrand]?.toList() ?: emptyList()
            } else {
                VehicleData.motorcycleModels[selectedBrand]?.toList() ?: emptyList()
            }
            showSearchPicker(getString(R.string.garage_model_label), models) { selected ->
                selectedModel = selected
                modelDropdown.setText(selected)
            }
        }

        val (initialBrand, initialModel) = resolveBrandModelFromName(profile.name, selectedType)
        if (initialBrand.isNotEmpty()) {
            selectedBrand = initialBrand
            brandDropdown.setText(initialBrand)
        }
        if (initialModel.isNotEmpty()) {
            selectedModel = initialModel
            modelDropdown.setText(initialModel)
        }
        updateModelEnabled()
        
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()
        
        btnClose.setOnClickListener { dialog.dismiss() }
        
        btnSave.setOnClickListener {
            val displayName = etName.text?.toString()?.trim().orEmpty()
            if (selectedBrand.isEmpty()) {
                brandInput.error = getString(R.string.garage_select_brand)
                return@setOnClickListener
            }
            if (selectedModel.isEmpty()) {
                modelInput.error = getString(R.string.garage_select_model)
                return@setOnClickListener
            }
            val prefs = requireContext().getSharedPreferences("garage_display_names", Context.MODE_PRIVATE)
            val key = "profile_${profile.id}_display_name"
            if (displayName.isBlank()) {
                prefs.edit().remove(key).apply()
            } else {
                prefs.edit().putString(key, displayName).apply()
            }
            profile.name = "$selectedBrand $selectedModel"
            profile.vehicleType = selectedType
            ProfileStorage.saveProfiles(requireContext(), profiles)
            adapter.notifyDataSetChanged()
            
            val selectedId = ProfileStorage.getSelectedProfileId(requireContext())
            if (profile.id == selectedId) {
                updateActiveProfileCard()
            }
            
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun getGarageDisplayName(profile: Profile): String {
        val prefs = requireContext().getSharedPreferences("garage_display_names", Context.MODE_PRIVATE)
        val key = "profile_${profile.id}_display_name"
        return prefs.getString(key, null).orEmpty().ifBlank { profile.name }
    }

    private fun showProfileDetailsDialog(profile: Profile) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_profile_details, null)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseDetails)
        val tvName = dialogView.findViewById<TextView>(R.id.tvDetailsName)
        val tvMeta = dialogView.findViewById<TextView>(R.id.tvDetailsMeta)
        val tvCalibration = dialogView.findViewById<TextView>(R.id.tvDetailsCalibrationStatus)
        val btnOpenCalibration = dialogView.findViewById<MaterialButton>(R.id.btnOpenCalibration)
        val btnSetActive = dialogView.findViewById<MaterialButton>(R.id.btnSetActive)
        val tvSessionCount = dialogView.findViewById<TextView>(R.id.tvSessionCount)
        val tvTotalDistance = dialogView.findViewById<TextView>(R.id.tvTotalDistance)
        val tvTotalDuration = dialogView.findViewById<TextView>(R.id.tvTotalDuration)
        val tvBest0to100 = dialogView.findViewById<TextView>(R.id.tvBest0to100)
        val tvBest0to200 = dialogView.findViewById<TextView>(R.id.tvBest0to200)
        val tvBest100to200 = dialogView.findViewById<TextView>(R.id.tvBest100to200)
        val tvBest0to402 = dialogView.findViewById<TextView>(R.id.tvBest0to402)
        val tvMaxSpeed = dialogView.findViewById<TextView>(R.id.tvMaxSpeed)

        val typeText = when (profile.vehicleType) {
            Profile.VehicleType.CAR -> getString(R.string.garage_vehicle_car)
            Profile.VehicleType.MOTORCYCLE -> getString(R.string.garage_vehicle_motorcycle)
        }
        tvName.text = profile.name
        tvMeta.text = typeText

        val isCalibrated = DragCalibration.isProfileCalibrated(requireContext(), profile.id)
        tvCalibration.text = if (isCalibrated) {
            getString(R.string.garage_calibrated)
        } else {
            getString(R.string.garage_not_calibrated)
        }

        val bestTimes = getBestTimesFromAllRaces(profile.id)
        tvBest0to100.text = formatBestTime(bestTimes.best0to100)
        tvBest0to200.text = formatBestTime(bestTimes.best0to200)
        tvBest100to200.text = formatBestTime(bestTimes.best100to200)
        tvBest0to402.text = formatBestTime(bestTimes.best0to402)
        tvMaxSpeed.text = if (bestTimes.maxSpeed > 0f) {
            getString(R.string.max_speed_format, bestTimes.maxSpeed)
        } else {
            "--"
        }

        val allRaces = RouteStorage.loadRaces(requireContext())
        val profileRaces = allRaces.filter { it.profileId == profile.id }
        val allDragSessions = DragStorage.loadDragSessions(requireContext())
        val profileDragSessions = allDragSessions.filter { it.profileId == profile.id }

        val totalSessions = profileRaces.size + profileDragSessions.size
        tvSessionCount.text = totalSessions.toString()
        val sessionsText = getString(R.string.garage_sessions_template, totalSessions)
        tvMeta.text = "$typeText • $sessionsText"

        val totalDist = profileRaces.sumOf { it.distance }
        tvTotalDistance.text = String.format("%.1f km", totalDist)

        val racesTimeMs = profileRaces.sumOf { race ->
            if (race.duration > 0) {
                race.duration.toLong()
            } else {
                val points = if (race.routePoints.isNotEmpty()) {
                    race.routePoints
                } else {
                    val allPoints = RouteStorage.loadRoutePoints(requireContext(), race.id)
                    if (allPoints.size >= 2) listOf(allPoints.first(), allPoints.last()) else allPoints
                }

                if (points.isNotEmpty()) {
                    val firstPoint = points.first()
                    val lastPoint = points.last()
                    val firstTime = firstPoint.absoluteTime
                    val lastTime = lastPoint.absoluteTime

                    if (firstTime > 0 && lastTime > 0 && lastTime > firstTime) {
                        (lastTime - firstTime).coerceAtLeast(0L)
                    } else {
                        val firstTimestamp = firstPoint.timestamp
                        val lastTimestamp = lastPoint.timestamp
                        if (lastTimestamp > firstTimestamp) {
                            (lastTimestamp - firstTimestamp).coerceAtLeast(0L)
                        } else {
                            0L
                        }
                    }
                } else {
                    0L
                }
            }
        }

        val dragTimeMs = profileDragSessions.sumOf { session ->
            session.attempts.sumOf { attempt ->
                attempt.duration / 1_000_000
            }
        }

        val totalTimeMs = racesTimeMs + dragTimeMs
        val totalSeconds = totalTimeMs / 1000
        val totalHours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        tvTotalDuration.text = getString(R.string.profile_detail_duration_format, totalHours, minutes)

        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setView(dialogView)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }

        btnOpenCalibration.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(requireContext(), DragCalibrationActivity::class.java).apply {
                putExtra("PROFILE_ID", profile.id)
            }
            startActivity(intent)
        }

        btnSetActive.setOnClickListener {
            ProfileStorage.saveSelectedProfile(requireContext(), profile.id)
            updateActiveProfileCard()
            adapter.notifyDataSetChanged()
            Toast.makeText(requireContext(), "✅ Active profile updated", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun getBestTimesFromAllRaces(profileId: Long): BestTimes {
        val allRaces = RouteStorage.loadRaces(requireContext())
        val profileRaces = allRaces.filter { it.profileId == profileId }

        val allDragSessions = DragStorage.loadDragSessions(requireContext())
        val profileDragSessions = allDragSessions.filter { it.profileId == profileId }

        var best0to100 = Long.MAX_VALUE
        var best0to200 = Long.MAX_VALUE
        var best100to200 = Long.MAX_VALUE
        var maxSpeed = 0f
        var best0to402 = Long.MAX_VALUE

        profileRaces.forEach { race ->
            if (race.time0to100 > 0 && race.time0to100 < best0to100) best0to100 = race.time0to100
            if (race.time0to200 > 0 && race.time0to200 < best0to200) best0to200 = race.time0to200
            if (race.time100to200 > 0 && race.time100to200 < best100to200) best100to200 = race.time100to200
            if (race.maxSpeed > maxSpeed) maxSpeed = race.maxSpeed
        }

        profileDragSessions.forEach { session ->
            if (session.best0to100 > 0 && session.best0to100 < best0to100) best0to100 = session.best0to100
            if (session.best0to200 > 0 && session.best0to200 < best0to200) best0to200 = session.best0to200
            if (session.best100to200 > 0 && session.best100to200 < best100to200) best100to200 = session.best100to200
            if (session.best0to402 > 0 && session.best0to402 < best0to402) best0to402 = session.best0to402

            session.attempts.forEach { attempt ->
                if (attempt.maxSpeed > maxSpeed) maxSpeed = attempt.maxSpeed
            }
        }

        return BestTimes(
            best0to100 = if (best0to100 == Long.MAX_VALUE) 0L else best0to100,
            best0to200 = if (best0to200 == Long.MAX_VALUE) 0L else best0to200,
            best100to200 = if (best100to200 == Long.MAX_VALUE) 0L else best100to200,
            maxSpeed = maxSpeed,
            best0to402 = if (best0to402 == Long.MAX_VALUE) 0L else best0to402
        )
    }

    private fun formatBestTime(nanos: Long): String =
        if (nanos > 0) String.format("%.3f", nanos / 1_000_000_000.0) else "--"

    private data class BestTimes(
        val best0to100: Long,
        val best0to200: Long,
        val best100to200: Long,
        val best0to402: Long,
        val maxSpeed: Float,
    )
    
    override fun onResume() {
        super.onResume()
        if (profiles.isEmpty() || currentSelectedProfile == null) {
            loadProfiles()
            updateActiveProfileCard()
            updateProfileCount()
        } else {
            updateProfileCount()
        }

        updateEmptyState()
        
        adapter.notifyDataSetChanged()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        imageLoadExecutor.shutdown()
        adapter.cleanup()
    }
}
