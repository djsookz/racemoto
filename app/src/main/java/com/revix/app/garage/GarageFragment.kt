package com.revix.app.garage

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
import com.revix.app.*
import com.revix.app.data.ProfileSessionSummaryStore
import com.revix.app.data.ProfileStorage
import com.revix.app.data.VehicleData
import com.revix.app.main.MainContainerActivity
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
                ProfileSessionSummaryStore.ensureInitialized(appContext)
                val map = mutableMapOf<Long, Int>()
                profilesSnapshot.forEach { profile ->
                    map[profile.id] = ProfileSessionSummaryStore.loadSummary(appContext, profile.id).totalSessions
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
        val atCap = !com.revix.app.billing.ProAccess.canCreateProfile(requireContext()) &&
            profiles.size >= com.revix.app.billing.ProAccess.maxProfilesAllowed(requireContext())
        // Keep button enabled so free users can open paywall when tapping add
        val hardFull = profiles.size >= com.revix.app.billing.ProAccess.MAX_PROFILES
        if (hardFull) {
            btnAddProfile.text = ""
            btnAddProfile.isEnabled = false
            btnAddProfile.alpha = 0.6f
            btnAddProfile.setIconResource(R.drawable.ic_block)
            btnAddProfile.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.card_background)
        } else {
            btnAddProfile.text = ""
            btnAddProfile.isEnabled = true
            btnAddProfile.alpha = if (atCap && !com.revix.app.billing.ProAccess.hasFullAccess(requireContext())) 1f else 1f
            btnAddProfile.setIconResource(R.drawable.ic_add)
            btnAddProfile.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.accent_color)
        }
    }
    
    private fun updateProfileCount() {
        val count = profiles.size
        tvProfileCount.text = when {
            com.revix.app.billing.ProAccess.hasFullAccess(requireContext()) ->
                getString(
                    R.string.garage_profile_count_pro,
                    count,
                    com.revix.app.billing.ProAccess.MAX_PROFILES
                )
            count <= 0 -> getString(R.string.garage_profile_count_empty)
            count == 1 -> getString(R.string.garage_profile_count_free_single)
            else -> getString(R.string.garage_profile_count_free_multi, count)
        }
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
            val (iconRes, typeText) = when (profile.vehicleType ?: Profile.VehicleType.MOTORCYCLE) {
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
            Toast.makeText(requireContext(), getString(R.string.garage_toast_cannot_delete_active_profile), Toast.LENGTH_LONG).show()
            return
        }
        
        val sessionCount = getProfileSessionCount(profile.id)
        val message = if (sessionCount > 0) {
            getString(R.string.garage_confirm_delete, profile.name, sessionCount)
        } else {
            getString(R.string.garage_confirm_delete_simple, profile.name)
        }
        
        DialogHelper.showDeleteConfirmation(
            context = requireContext(),
            title = getString(R.string.garage_delete_profile_title),
            message = message,
            positiveButtonText = getString(R.string.garage_delete_button),
            negativeButtonText = getString(R.string.garage_cancel_button)
        ) {
            if (sessionCount > 0) {
                val allRaces = RouteStorage.loadRaces(requireContext()).toMutableList()
                allRaces.removeAll { it.profileId == profile.id }
                RouteStorage.saveRaces(requireContext(), allRaces)
            }

            profiles.remove(profile)
            ProfileStorage.saveProfiles(requireContext(), profiles)
            ProfileSessionSummaryStore.clearProfile(requireContext(), profile.id)
            adapter.notifyDataSetChanged()
            updateAddButtonState()
            updateProfileCount()
            updateEmptyState()
            updateActiveProfileCard()
            loadProfileStatsAsync()

            val successMessage = if (sessionCount > 0) {
                getString(R.string.garage_toast_profile_deleted_with_sessions, sessionCount)
            } else {
                getString(R.string.garage_toast_profile_deleted_success)
            }
            Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show()

            if (profiles.isEmpty()) {
                startActivity(Intent(requireContext(), FirstProfileActivity::class.java))
                requireActivity().finish()
            }
        }
    }
    
    private fun showChangeProfileConfirmation(profile: Profile) {
        val currentProfileId = ProfileStorage.getSelectedProfileId(requireContext())
        if (profile.id == currentProfileId) {
            // Already selected, no need to show confirmation
            return
        }
        
        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle(getString(R.string.garage_change_button))
            .setMessage(getString(R.string.garage_change_vehicle_confirm, profile.name))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                ProfileStorage.saveSelectedProfile(requireContext(), profile.id)
                updateActiveProfileCard()
                adapter.notifyDataSetChanged()
                loadProfileStatsAsync()
                Toast.makeText(requireContext(), getString(R.string.garage_toast_now_driving, profile.name), Toast.LENGTH_SHORT).show()
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
    
    private fun showCreateProfileDialog() {
        if (!com.revix.app.billing.ProGate.ensureCanCreateProfile(requireContext())) {
            return
        }
        if (profiles.size >= com.revix.app.billing.ProAccess.MAX_PROFILES) {
            Toast.makeText(requireContext(), getString(R.string.garage_toast_max_profiles), Toast.LENGTH_LONG).show()
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
                VehicleData.carBrands
            } else {
                VehicleData.motorcycleBrands
            }
            val brands = VehicleData.localizedBrands(requireContext(), brandsRaw).toList()
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
        VehicleSearchPicker.show(requireContext(), title, options, onSelect)
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
        val profileId = currentSelectedProfile?.id ?: return
        val appContext = requireContext().applicationContext

        lifecycleScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    writeProfileImage(appContext, uri, profileId)
                }
                if (!isAdded) return@launch
                val profile = profiles.find { it.id == profileId } ?: currentSelectedProfile ?: return@launch
                val oldImagePath = profile.imagePath
                profile.imagePath = saved.path
                profiles.find { it.id == profileId }?.imagePath = saved.path
                ProfileStorage.saveProfiles(requireContext(), profiles, sync = true)

                if (!oldImagePath.isNullOrEmpty()) {
                    imageCache.remove(oldImagePath)
                }
                imageCache.remove(saved.path)
                imageCache[saved.path] = saved.bitmap
                adapter.clearImageCacheForPath(oldImagePath)
                adapter.clearImageCacheForPath(saved.path)
                updateActiveProfileCard()
                val profileIndex = profiles.indexOfFirst { it.id == profileId }
                if (profileIndex != -1) {
                    adapter.notifyItemChanged(profileIndex)
                }
                Toast.makeText(requireContext(), getString(R.string.garage_toast_photo_saved), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("GarageFragment", "Error saving image: ${e.message}", e)
                if (isAdded) {
                    Toast.makeText(requireContext(), getString(R.string.garage_toast_photo_save_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun writeProfileImage(context: Context, uri: Uri, profileId: Long): SavedProfileImage {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to open image")
        val bitmap = inputStream.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw IllegalStateException("Unable to decode image")
        val correctedBitmap = correctImageOrientation(context, uri, bitmap)

        val maxSize = 800
        val resizedBitmap = if (correctedBitmap.width > maxSize || correctedBitmap.height > maxSize) {
            val scale = minOf(maxSize.toFloat() / correctedBitmap.width, maxSize.toFloat() / correctedBitmap.height)
            val newWidth = (correctedBitmap.width * scale).toInt()
            val newHeight = (correctedBitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(correctedBitmap, newWidth, newHeight, true)
        } else {
            correctedBitmap
        }
        if (resizedBitmap != correctedBitmap) {
            correctedBitmap.recycle()
        }
        if (bitmap != resizedBitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }

        val relativePath = ProfileStorage.profileImageRelativePath(profileId)
        val imageFile = File(context.getExternalFilesDir(null), relativePath)
        imageFile.parentFile?.mkdirs()
        FileOutputStream(imageFile).use { outputStream ->
            if (!resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)) {
                throw IllegalStateException("Unable to compress image")
            }
            outputStream.flush()
        }
        return SavedProfileImage(relativePath, resizedBitmap)
    }
    
    private fun correctImageOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            var orientation = ExifInterface.ORIENTATION_NORMAL
            
            val inputStream = context.contentResolver.openInputStream(uri)
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
                val filePath = getRealPathFromURI(context, uri)
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
    
    private fun getRealPathFromURI(context: Context, uri: Uri): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                null
            } else {
                val projection = arrayOf(MediaStore.Images.Media.DATA)
                val cursor = context.contentResolver.query(uri, projection, null, null, null)
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
        val oldImagePath = profile.imagePath ?: return
        if (oldImagePath.isNotEmpty()) {
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
            
            val (iconRes, typeText) = when (profile.vehicleType ?: Profile.VehicleType.MOTORCYCLE) {
                Profile.VehicleType.CAR -> Pair(R.drawable.ic_car, getString(R.string.garage_vehicle_car))
                Profile.VehicleType.MOTORCYCLE -> Pair(R.drawable.ic_motorcycle, getString(R.string.garage_vehicle_motorcycle))
            }
            showIcon(iconRes)
            val sessions = getProfileSessionCount(profile.id)
            val sessionsText = getString(R.string.garage_sessions_template, sessions)
            tvActiveProfileNameOverlay?.text = profile.name
            tvActiveProfileMetaOverlay?.text = "$typeText • $sessionsText"
            
            adapter.notifyDataSetChanged()
            Toast.makeText(requireContext(), getString(R.string.garage_toast_photo_removed), Toast.LENGTH_SHORT).show()
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
        val etRegistrationPlate = dialogView.findViewById<TextInputEditText>(R.id.etRegistrationPlate)
        val etEngineCc = dialogView.findViewById<TextInputEditText>(R.id.etEngineCc)
        val etPowerHp = dialogView.findViewById<TextInputEditText>(R.id.etPowerHp)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveEdit)
        
        var selectedType = profile.vehicleType
        var selectedBrand = ""
        var selectedModel = ""
        etName.setText(getGarageDisplayName(profile))
        etRegistrationPlate.setText(VehicleRegistrationPlate.display(profile.registrationPlate))
        etEngineCc.setText(VehicleProfileSpecs.formatValue(profile.engineDisplacementCc))
        etPowerHp.setText(VehicleProfileSpecs.formatValue(profile.powerHp))
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
                VehicleData.carBrands
            } else {
                VehicleData.motorcycleBrands
            }
            val brands = VehicleData.localizedBrands(requireContext(), brandsRaw).toList()
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
                VehicleData.carModelsFor(selectedBrand).toList()
            } else {
                VehicleData.motorcycleModelsFor(selectedBrand).toList()
            }
            showSearchPicker(getString(R.string.garage_model_label), models) { selected ->
                selectedModel = selected
                modelDropdown.setText(selected)
            }
        }

        val (initialBrand, initialModel) = VehicleData.resolveBrandAndModel(
            profile.name,
            selectedType == Profile.VehicleType.MOTORCYCLE
        )
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
            profile.registrationPlate = VehicleRegistrationPlate.sanitize(
                etRegistrationPlate.text?.toString()
            ).ifBlank { null }
            profile.engineDisplacementCc = VehicleProfileSpecs.parseCc(etEngineCc.text?.toString())
            profile.powerHp = VehicleProfileSpecs.parseHp(etPowerHp.text?.toString())
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
    
    override fun onResume() {
        super.onResume()
        loadProfiles()
        updateActiveProfileCard()
        updateProfileCount()
        updateEmptyState()
        loadProfileStatsAsync()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        imageLoadExecutor.shutdown()
        adapter.cleanup()
    }

    private data class SavedProfileImage(
        val path: String,
        val bitmap: Bitmap
    )
}
