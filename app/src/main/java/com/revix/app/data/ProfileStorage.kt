package com.revix.app.data

import android.content.Context
import com.revix.app.Profile
import com.revix.app.garage.VehicleRegistrationPlate
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object ProfileStorage {
    private const val PREFS_KEY = "profiles"
    private const val SELECTED_PROFILE_KEY = "selected_profile_id"

    fun profileImageRelativePath(profileId: Long): String = "profile_images/profile_${profileId}.jpg"

    fun saveProfiles(context: Context, profiles: List<Profile>, sync: Boolean = false) {
        val json = Gson().toJson(profiles)
        val prefs = context.getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(PREFS_KEY, json)

        val selectedId = prefs.getLong(SELECTED_PROFILE_KEY, -1)
        val selectedExists = profiles.any { it.id == selectedId }
        if (!selectedExists) {
            editor.remove(SELECTED_PROFILE_KEY)
        }

        if (sync) editor.commit() else editor.apply()
    }

    fun loadProfiles(context: Context): MutableList<Profile> {
        val prefs = context.getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE)
        val json = prefs.getString(PREFS_KEY, null)
        val loaded = if (json != null) {
            val type = object : TypeToken<MutableList<Profile>>() {}.type
            Gson().fromJson<MutableList<Profile>>(json, type) ?: mutableListOf()
        } else {
            mutableListOf()
        }
        // Gson can deserialize null into Kotlin non-null fields (name, vehicleType).
        var mutated = false
        loaded.forEach { profile ->
            if ((profile.name as String?) == null) {
                profile.name = ""
                mutated = true
            }
            if ((profile.vehicleType as Profile.VehicleType?) == null) {
                profile.vehicleType = Profile.VehicleType.MOTORCYCLE
                mutated = true
            }
            val plate = VehicleRegistrationPlate.sanitize(profile.registrationPlate)
            if (profile.registrationPlate.orEmpty().isNotEmpty() && profile.registrationPlate != plate) {
                profile.registrationPlate = plate
                mutated = true
            }
            val storedPath = profile.imagePath
            val storedFile = storedPath?.takeIf { it.isNotBlank() }?.let { File(context.getExternalFilesDir(null), it) }
            if (storedFile?.exists() == true) {
                return@forEach
            }
            val fallbackPath = profileImageRelativePath(profile.id)
            val fallbackFile = File(context.getExternalFilesDir(null), fallbackPath)
            if (fallbackFile.exists() && storedPath != fallbackPath) {
                profile.imagePath = fallbackPath
                mutated = true
            }
        }
        if (mutated && loaded.isNotEmpty()) {
            saveProfiles(context, loaded)
        }
        return loaded
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

