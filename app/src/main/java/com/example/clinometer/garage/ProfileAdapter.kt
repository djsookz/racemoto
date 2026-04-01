package com.example.clinometer.garage

import android.graphics.Color
import android.content.res.ColorStateList
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.Profile
import com.example.clinometer.R
import com.example.clinometer.data.ProfileStorage
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.io.File

class ProfileAdapter(
    private var profiles: MutableList<Profile>,
    private val context: Context,
    private val onProfileClick: (Profile) -> Unit,
    private val onEditClick: (Profile) -> Unit,
    private val onDeleteClick: (Profile) -> Unit,
    private val onActivateClick: (Profile) -> Unit
) : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

    private val imageCache = mutableMapOf<String, Bitmap?>()
    private val imageLoadHandler = Handler(Looper.getMainLooper())
    private var sessionCounts: Map<Long, Int> = emptyMap()
    private var calibrationStatuses: Map<Long, Boolean> = emptyMap()

    inner class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardProfile: MaterialCardView = itemView.findViewById(R.id.cardProfile)
        val ivProfileIcon: ImageView = itemView.findViewById(R.id.ivProfileIcon)
        val tvProfileName: TextView = itemView.findViewById(R.id.tvProfileName)
        val tvVehicleType: TextView = itemView.findViewById(R.id.tvVehicleType)
        val tvSessionCount: TextView = itemView.findViewById(R.id.tvSessionCount)
        val tvCalibrationStatus: TextView = itemView.findViewById(R.id.tvCalibrationStatus)
        val btnActivate: MaterialButton = itemView.findViewById(R.id.btnDetails)
        val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = profiles[position]
        val selectedProfileId = ProfileStorage.getSelectedProfileId(context)

        // Име и тип
        holder.tvProfileName.text = getGarageDisplayName(profile)
        val typeText = when (profile.vehicleType) {
            Profile.VehicleType.CAR -> context.getString(R.string.garage_vehicle_car)
            Profile.VehicleType.MOTORCYCLE -> context.getString(R.string.garage_vehicle_motorcycle)
        }
        holder.tvVehicleType.text = typeText

        // Статистики
        val sessionCount = sessionCounts[profile.id]
        holder.tvSessionCount.text = if (sessionCount != null) {
            context.getString(R.string.garage_sessions_template, sessionCount)
        } else {
            "…"
        }

        val isCalibrated = calibrationStatuses[profile.id]
        if (isCalibrated == null) {
            holder.tvCalibrationStatus.visibility = View.GONE
        } else if (isCalibrated) {
            holder.tvCalibrationStatus.visibility = View.GONE
        } else {
            holder.tvCalibrationStatus.visibility = View.VISIBLE
            holder.tvCalibrationStatus.text = context.getString(R.string.garage_not_calibrated)
        }

        // Зареждане на снимка или икона
        loadProfileImage(holder, profile)

        // Кликване на картата - отваря ProfileDetailActivity
        holder.itemView.setOnClickListener {
            onProfileClick(profile)
        }

        val isActiveProfile = profile.id == selectedProfileId
        if (isActiveProfile) {
            holder.btnActivate.text = context.getString(R.string.garage_active_action)
            holder.btnActivate.isEnabled = true
            holder.btnActivate.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            holder.btnActivate.strokeColor = ColorStateList.valueOf(Color.TRANSPARENT)
            holder.btnActivate.setTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
        } else {
            holder.btnActivate.text = context.getString(R.string.garage_activate_action)
            holder.btnActivate.isEnabled = true
            holder.btnActivate.backgroundTintList = ContextCompat.getColorStateList(context, R.color.accent_color)
            holder.btnActivate.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.accent_color))
            holder.btnActivate.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }
        holder.btnActivate.setOnClickListener {
            if (!isActiveProfile) {
                onActivateClick(profile)
            }
        }
        holder.btnEdit.setOnClickListener { onEditClick(profile) }
        holder.btnDelete.setOnClickListener { onDeleteClick(profile) }

        // Еднакъв тъмен фон за всички карти; active се отличава само с бордер
        val accent = ContextCompat.getColor(context, R.color.accent_color)
        val unifiedDarkBackground = ColorUtils.setAlphaComponent(accent, 20)
        holder.cardProfile.setCardBackgroundColor(unifiedDarkBackground)

        if (profile.id == selectedProfileId) {
            holder.cardProfile.strokeColor = ColorUtils.setAlphaComponent(accent, 120)
        } else {
            val white = ContextCompat.getColor(context, android.R.color.white)
            holder.cardProfile.strokeColor = ColorUtils.setAlphaComponent(white, 20)
        }
    }

    override fun getItemCount(): Int = profiles.size

    private fun getGarageDisplayName(profile: Profile): String {
        val prefs = context.getSharedPreferences("garage_display_names", Context.MODE_PRIVATE)
        val key = "profile_${profile.id}_display_name"
        return prefs.getString(key, null).orEmpty().ifBlank { profile.name }
    }

    fun setSessionCounts(counts: Map<Long, Int>) {
        sessionCounts = counts
    }

    fun setCalibrationStatuses(statuses: Map<Long, Boolean>) {
        calibrationStatuses = statuses
    }

    private fun loadProfileImage(holder: ProfileViewHolder, profile: Profile) {
        if (!profile.imagePath.isNullOrEmpty()) {
            val imagePath = profile.imagePath ?: ""
            val cachedBitmap = imageCache[imagePath]

            if (cachedBitmap != null) {
                // Use cached bitmap (already scaled appropriately)
                setupImageForBitmap(holder.ivProfileIcon, cachedBitmap)
            } else {
                // Зареждаме асинхронно
                val imageFile = File(context.getExternalFilesDir(null), imagePath)
                if (imageFile.exists()) {
                    // Зареждаме в background thread
                    Thread {
                        try {
                            // Image is already scaled on disk, just load it
                            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                            if (bitmap != null) {
                                // Remove old bitmap from cache if exists (don't recycle - might still be in use)
                                imageCache.remove(imagePath)
                                
                                imageCache[imagePath] = bitmap
                                imageLoadHandler.post {
                                    // Проверяваме дали holder все още е валиден
                                    if (holder.adapterPosition != RecyclerView.NO_POSITION &&
                                        profiles.getOrNull(holder.adapterPosition)?.id == profile.id) {
                                        setupImageForBitmap(holder.ivProfileIcon, bitmap)
                                    }
                                }
                            } else {
                                imageLoadHandler.post {
                                    showDefaultIcon(holder, profile.vehicleType)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("ProfileAdapter", "Error loading profile image", e)
                            imageCache.remove(imagePath)
                            imageLoadHandler.post {
                                showDefaultIcon(holder, profile.vehicleType)
                            }
                        }
                    }.start()
                } else {
                    // Файлът не съществува, показваме иконка
                    showDefaultIcon(holder, profile.vehicleType)
                    imageCache[imagePath] = null
                }
            }
        } else {
            // Няма снимка, показваме иконка
            showDefaultIcon(holder, profile.vehicleType)
        }
    }
    
    private fun setupImageForBitmap(imageView: ImageView, bitmap: Bitmap) {
        // Validate view is still attached
        if (imageView.parent == null) {
            return
        }
        
        // Reset any previous state
        imageView.clipToOutline = false
        imageView.outlineProvider = null
        
        // Ensure layout params are set correctly - always reset and set
        val parent = imageView.parent as? FrameLayout
        if (parent != null) {
            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = android.view.Gravity.FILL
            }
            imageView.layoutParams = layoutParams
        } else {
            // Fallback if parent is not FrameLayout
            val existingParams = imageView.layoutParams
            if (existingParams is FrameLayout.LayoutParams) {
                existingParams.width = FrameLayout.LayoutParams.MATCH_PARENT
                existingParams.height = FrameLayout.LayoutParams.MATCH_PARENT
                existingParams.gravity = android.view.Gravity.FILL
                imageView.layoutParams = existingParams
            }
        }
        
        // Set image and scale type
        imageView.setImageBitmap(bitmap)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.imageTintList = null
        
        // Function to apply circular outline with validation
        fun applyCircularOutline(): Boolean {
            // Validate view is still attached and has valid dimensions
            if (imageView.parent == null) {
                return false
            }
            
            val width = imageView.width
            val height = imageView.height
            
            if (width > 0 && height > 0) {
                imageView.clipToOutline = true
                imageView.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        // Use actual measured dimensions for perfect circle
                        val w = view.width
                        val h = view.height
                        if (w > 0 && h > 0) {
                            outline.setOval(0, 0, w, h)
                        }
                    }
                }
                // Force redraw to apply outline
                imageView.invalidate()
                return true
            }
            return false
        }
        
        // Try to apply outline immediately if view is already measured
        if (!applyCircularOutline()) {
            // Use ViewTreeObserver to wait for layout to be complete
            val viewTreeObserver = imageView.viewTreeObserver
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        // Remove listener to avoid multiple calls
                        val observer = imageView.viewTreeObserver
                        if (observer.isAlive) {
                            observer.removeOnGlobalLayoutListener(this)
                        }
                        applyCircularOutline()
                    }
                })
            } else {
                // Fallback: use post with multiple attempts
                var attempts = 0
                val maxAttempts = 10
                fun tryApply() {
                    attempts++
                    if (imageView.parent != null) {
                        if (!applyCircularOutline() && attempts < maxAttempts) {
                            imageView.postDelayed({ tryApply() }, 30)
                        }
                    }
                }
                imageView.post { tryApply() }
            }
        }
    }

    private fun showDefaultIcon(holder: ProfileViewHolder, vehicleType: Profile.VehicleType) {
        val iconRes = when (vehicleType) {
            Profile.VehicleType.CAR -> R.drawable.ic_car
            Profile.VehicleType.MOTORCYCLE -> R.drawable.ic_motorcycle
        }
        holder.ivProfileIcon.setImageResource(iconRes)
        holder.ivProfileIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
        holder.ivProfileIcon.clipToOutline = false
        holder.ivProfileIcon.outlineProvider = null
        holder.ivProfileIcon.imageTintList = ContextCompat.getColorStateList(context, R.color.primary_color)
    }

    fun clearImageCacheForPath(imagePath: String?) {
        // Just remove from cache, don't recycle - bitmap might still be in use by ImageView
        imagePath?.let {
            imageCache.remove(it)
        }
    }

    fun cleanup() {
        // Recycle all bitmaps before clearing cache
        imageCache.values.forEach { it?.recycle() }
        imageCache.clear()
    }
    
}

