package com.example.clinometer

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ProfileActivity : AppCompatActivity() {

    private lateinit var adapter: ProfileAdapter
    private lateinit var btnAddProfile: Button
    private val profiles = mutableListOf<Profile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        btnAddProfile = findViewById(R.id.btnAddProfile)
        val recyclerView = findViewById<RecyclerView>(R.id.rvProfiles)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ProfileAdapter(
            profiles,
            onEditClick = { profile -> showEditProfileDialog(profile) },
            onDeleteClick = { profile ->
                profiles.remove(profile)
                ProfileStorage.saveProfiles(this, profiles)
                adapter.notifyDataSetChanged()
                updateAddButtonState() // Обновяваме състоянието на бутона след изтриване

                if (profiles.isEmpty()) {
                    finish()
                }
            }
        )
        recyclerView.adapter = adapter

        btnAddProfile.setOnClickListener {
            showCreateProfileDialog()
        }

        loadProfiles()
        updateAddButtonState() // Инициализираме състоянието на бутона
    }

    private fun loadProfiles() {
        profiles.clear()
        profiles.addAll(ProfileStorage.loadProfiles(this))
        adapter.notifyDataSetChanged()
    }

    // Функция за актуализиране на състоянието на бутона
    private fun updateAddButtonState() {
        btnAddProfile.isEnabled = profiles.size < 5
        if (!btnAddProfile.isEnabled) {
            btnAddProfile.alpha = 0.5f // Намаляване на прозрачността за визуален индикатор
        } else {
            btnAddProfile.alpha = 1f
        }
    }

    private fun showCreateProfileDialog() {
        // Проверка дали сме достигнали лимита
        if (profiles.size >= 5) {
            Toast.makeText(this, "Достигнат е максималният брой профили (5)", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_profile, null)
        val etName = dialogView.findViewById<EditText>(R.id.etProfileName)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinnerVehicleType)

        ArrayAdapter.createFromResource(
            this,
            R.array.vehicle_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerType.adapter = adapter
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_create_profile))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.dialog_save_button)) { _, _ ->
                val name = etName.text.toString()
                val type = if (spinnerType.selectedItemPosition == 0)
                    Profile.VehicleType.CAR else Profile.VehicleType.MOTORCYCLE

                if (name.isNotBlank()) {
                    val newProfile = Profile(name = name, vehicleType = type)
                    profiles.add(newProfile)
                    ProfileStorage.saveProfiles(this, profiles)
                    adapter.notifyDataSetChanged()
                    updateAddButtonState() // Обновяваме състоянието на бутона след създаване
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel_button), null)
            .show()
    }

    private fun showEditProfileDialog(profile: Profile) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_profile, null)
        val etName = dialogView.findViewById<EditText>(R.id.etProfileName)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinnerVehicleType)

        etName.setText(profile.name)
        spinnerType.setSelection(if (profile.vehicleType == Profile.VehicleType.CAR) 0 else 1)

        ArrayAdapter.createFromResource(
            this,
            R.array.vehicle_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerType.adapter = adapter
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.profile_edit_text))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.profile_save_button)) { _, _ ->
                val name = etName.text.toString()
                val type = if (spinnerType.selectedItemPosition == 0)
                    Profile.VehicleType.CAR else Profile.VehicleType.MOTORCYCLE

                if (name.isNotBlank()) {
                    profile.name = name
                    profile.vehicleType = type
                    ProfileStorage.saveProfiles(this, profiles)
                    adapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel_button), null)
            .show()
    }
}

class ProfileAdapter(
    private val profiles: List<Profile>,
    private val onEditClick: (Profile) -> Unit,
    private val onDeleteClick: (Profile) -> Unit
) : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

    class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvProfileName)
        val tvType: TextView = itemView.findViewById(R.id.tvVehicleType)
        val btnOptions: ImageButton = itemView.findViewById(R.id.btnOptions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile, parent, false)
        return ProfileViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = profiles[position]
        holder.tvName.text = profile.name
        holder.tvType.text = when (profile.vehicleType) {
            Profile.VehicleType.CAR -> holder.itemView.context.getString(R.string.vehicle_type_car)
            Profile.VehicleType.MOTORCYCLE -> holder.itemView.context.getString(R.string.vehicle_type_motorcycle)
        }

        holder.btnOptions.setOnClickListener { view ->
            showPopupMenu(view, profile)
        }
    }

    private fun showPopupMenu(view: View, profile: Profile) {
        PopupMenu(view.context, view).apply {
            menu.add(0, 1, 0, view.context.getString(R.string.profile_edit_button))
            menu.add(0, 2, 1, view.context.getString(R.string.profile_delete_button))
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> onEditClick(profile)
                    2 -> onDeleteClick(profile)
                }
                true
            }
            show()
        }
    }

    override fun getItemCount() = profiles.size
}

object ProfileStorage {
    private const val PREFS_KEY = "profiles"
    private const val SELECTED_PROFILE_KEY = "selected_profile_id"

    fun saveProfiles(context: Context, profiles: List<Profile>) {
        val gson = Gson()
        val json = gson.toJson(profiles)
        context.getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE).edit()
            .putString(PREFS_KEY, json)
            .apply()

        if (profiles.isEmpty()) {
            context.getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE).edit()
                .remove(SELECTED_PROFILE_KEY)
                .apply()
        }
    }

    fun loadProfiles(context: Context): MutableList<Profile> {
        val prefs = context.getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE)
        val json = prefs.getString(PREFS_KEY, null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<Profile>>() {}.type
            Gson().fromJson(json, type) ?: mutableListOf()
        } else {
            mutableListOf()
        }
    }

    fun saveSelectedProfile(context: Context, profileId: Long) {
        context.getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE).edit()
            .putLong(SELECTED_PROFILE_KEY, profileId)
            .apply()
    }

    fun getSelectedProfileId(context: Context): Long {
        return context.getSharedPreferences("ProfilePrefs", Context.MODE_PRIVATE)
            .getLong(SELECTED_PROFILE_KEY, -1)
    }

    fun saveNewProfile(context: Context, profile: Profile) {
        val profiles = loadProfiles(context).toMutableList()
        profiles.add(profile)
        saveProfiles(context, profiles)
    }
}