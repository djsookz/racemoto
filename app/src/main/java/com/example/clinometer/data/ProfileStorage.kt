package com.example.clinometer.data

import android.content.Context
import com.example.clinometer.Profile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

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

