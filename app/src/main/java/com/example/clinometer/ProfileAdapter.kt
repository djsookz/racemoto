package com.example.clinometer

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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.data.ProfileStorage
import java.io.File

class ProfileAdapter(
    private var profiles: MutableList<Profile>,
    private val context: Context,
    private val onProfileClick: (Profile) -> Unit,
    private val onEditClick: (Profile) -> Unit,
    private val onDeleteClick: (Profile) -> Unit
) : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

    private val imageCache = mutableMapOf<String, Bitmap?>()
    private val imageLoadHandler = Handler(Looper.getMainLooper())

    inner class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivProfileIcon: ImageView = itemView.findViewById(R.id.ivProfileIcon)
        val tvProfileName: TextView = itemView.findViewById(R.id.tvProfileName)
        val tvVehicleType: TextView = itemView.findViewById(R.id.tvVehicleType)
        val tvSessionCount: TextView = itemView.findViewById(R.id.tvSessionCount)
        val tvMaxSpeed: TextView = itemView.findViewById(R.id.tvMaxSpeed)
        val btnOptions: ImageButton = itemView.findViewById(R.id.btnOptions)
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
        holder.tvProfileName.text = profile.name
        val typeText = when (profile.vehicleType) {
            Profile.VehicleType.CAR -> context.getString(R.string.garage_vehicle_car)
            Profile.VehicleType.MOTORCYCLE -> context.getString(R.string.garage_vehicle_motorcycle)
        }
        holder.tvVehicleType.text = typeText

        // Статистики
        val sessionCount = getProfileSessionCount(profile.id)
        holder.tvSessionCount.text = context.getString(R.string.garage_sessions_template, sessionCount)

        val maxSpeedText = if (profile.maxSpeed > 0) {
            String.format("%.0f km/h", profile.maxSpeed)
        } else {
            "-- km/h"
        }
        holder.tvMaxSpeed.text = maxSpeedText

        // Зареждане на снимка или икона
        loadProfileImage(holder, profile)

        // Кликване на картата - отваря ProfileDetailActivity
        holder.itemView.setOnClickListener {
            onProfileClick(profile)
        }

        // Бутон за опции - показва PopupMenu с Edit и Delete
        holder.btnOptions.setOnClickListener { view ->
            android.widget.PopupMenu(view.context, view).apply {
                menu.add(0, 1, 0, context.getString(R.string.profile_edit_text))
                menu.add(0, 2, 0, context.getString(R.string.profile_delete_button))
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> {
                            onEditClick(profile)
                            true
                        }
                        2 -> {
                            onDeleteClick(profile)
                            true
                        }
                        else -> false
                    }
                }
            }.show()
        }

        // Визуална индикация за активния профил
        if (profile.id == selectedProfileId) {
            holder.itemView.alpha = 1.0f
        } else {
            holder.itemView.alpha = 0.7f
        }
    }

    override fun getItemCount(): Int = profiles.size

    private fun getProfileSessionCount(profileId: Long): Int {
        val allRaces = RouteStorage.loadRaces(context)
        return allRaces.count { it.profileId == profileId }
    }

    private fun loadProfileImage(holder: ProfileViewHolder, profile: Profile) {
        if (!profile.imagePath.isNullOrEmpty()) {
            val imagePath = profile.imagePath ?: ""
            val cachedBitmap = imageCache[imagePath]

            if (cachedBitmap != null) {
                // Използваме кеширания bitmap
                holder.ivProfileIcon.setImageBitmap(cachedBitmap)
                holder.ivProfileIcon.scaleType = ImageView.ScaleType.CENTER_CROP
                holder.ivProfileIcon.clipToOutline = true
                holder.ivProfileIcon.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                holder.ivProfileIcon.imageTintList = null
            } else {
                // Зареждаме асинхронно
                val imageFile = File(context.getExternalFilesDir(null), imagePath)
                if (imageFile.exists()) {
                    // Зареждаме в background thread
                    Thread {
                        try {
                            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                            imageCache[imagePath] = bitmap
                            imageLoadHandler.post {
                                // Проверяваме дали holder все още е валиден
                                if (holder.adapterPosition != RecyclerView.NO_POSITION &&
                                    profiles.getOrNull(holder.adapterPosition)?.id == profile.id) {
                                    holder.ivProfileIcon.setImageBitmap(bitmap)
                                    holder.ivProfileIcon.scaleType = ImageView.ScaleType.CENTER_CROP
                                    holder.ivProfileIcon.clipToOutline = true
                                    holder.ivProfileIcon.outlineProvider = object : ViewOutlineProvider() {
                                        override fun getOutline(view: View, outline: android.graphics.Outline) {
                                            outline.setOval(0, 0, view.width, view.height)
                                        }
                                    }
                                    holder.ivProfileIcon.imageTintList = null
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("ProfileAdapter", "Error loading profile image", e)
                            imageCache[imagePath] = null
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
        imagePath?.let {
            imageCache.remove(it)
        }
    }

    fun cleanup() {
        imageCache.clear()
    }
}

