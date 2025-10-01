package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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

class GarageActivity :  BaseActivity() {

    override fun getLayoutResourceId(): Int = R.layout.activity_garage
    override fun getNavigationItemId(): Int = R.id.navGarage
    private lateinit var adapter: ProfileAdapter
    private lateinit var btnAddProfile: ExtendedFloatingActionButton
    private lateinit var cardActiveProfile: MaterialCardView
    private lateinit var tvActiveProfileName: TextView
    private lateinit var tvActiveProfileType: TextView
    private lateinit var ivActiveProfileIcon: ImageView
    private lateinit var btnChangeProfile: MaterialButton
    private lateinit var tvProfileCount: TextView
    private val profiles = mutableListOf<Profile>()
    private var backPressedTime: Long = 0
    private lateinit var backToast: Toast

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = ContextCompat.getColor(this, R.color.header_gradient_start)

        btnAddProfile = findViewById(R.id.btnAddProfile)
        cardActiveProfile = findViewById(R.id.cardActiveProfile)
        tvActiveProfileName = findViewById(R.id.tvActiveProfileName)
        tvActiveProfileType = findViewById(R.id.tvActiveProfileType)
        ivActiveProfileIcon = findViewById(R.id.ivActiveProfileIcon)
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

        // Click на цялата карта също отваря меню за смяна
        cardActiveProfile.setOnClickListener { showQuickProfileSelection() }

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
            backToast = Toast.makeText(baseContext, "Натиснете отново за изход", Toast.LENGTH_SHORT)
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
            btnAddProfile.text = "Добави"
            btnAddProfile.isEnabled = false
            btnAddProfile.alpha = 0.6f
            btnAddProfile.setIconResource(R.drawable.ic_block)
        } else {
            btnAddProfile.text = "Добави"
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
            tvActiveProfileName.text = profile.name

            val (iconRes, emoji, typeText) = when (profile.vehicleType) {
                Profile.VehicleType.CAR -> Triple(R.drawable.ic_car, "🚗", "Автомобил")
                Profile.VehicleType.MOTORCYCLE -> Triple(R.drawable.ic_motorcycle, "🏍️", "Мотоциклет")
            }

            ivActiveProfileIcon.setImageResource(iconRes)
            tvActiveProfileType.text = "$emoji $typeText"

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
            "Сигурни ли сте, че искате да изтриете \"${profile.name}\"?\n\nВсички $sessionCount сесии за този профил също ще бъдат изтрити."
        } else {
            "Сигурни ли сте, че искате да изтриете \"${profile.name}\"?"
        }

        AlertDialog.Builder(this)
            .setTitle("🗑️ Изтриване на профил")
            .setMessage(message)
            .setPositiveButton("Изтрий") { _, _ ->
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
            .setNegativeButton("Отказ", null)
            .show()
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

        AlertDialog.Builder(this)
            .setTitle("🔄 Смени превозното средство")
            .setItems(options) { _, which ->
                val newProfile = otherProfiles[which]
                ProfileStorage.saveSelectedProfile(this, newProfile.id)
                updateActiveProfileCard()
                adapter.notifyDataSetChanged()

                Toast.makeText(this, "✅ Сега караш: ${newProfile.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отказ", null)
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
            if (brands[position] == "Най-популярни") {
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
            .setNegativeButton("Отказ", null)
            .setPositiveButton("Създай", null)
            .create()

        dialog.setOnShowListener {
            val btnSave = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnCancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            
            // Прилагаме стила на бутоните - тъмен фон като картелата
            btnSave.setTextColor(ContextCompat.getColor(this, R.color.white))
            btnSave.backgroundTintList = ContextCompat.getColorStateList(this, R.color.dark_surface)
            
            btnCancel.setTextColor(ContextCompat.getColor(this, R.color.white))
            btnCancel.backgroundTintList = ContextCompat.getColorStateList(this, R.color.dark_surface)
            
            btnSave.setOnClickListener {
                if (selectedBrand.isEmpty()) {
                    brandInput.error = "Моля изберете марка"
                    return@setOnClickListener
                }
                if (selectedModel.isEmpty()) {
                    modelInput.error = "Моля изберете модел"
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
                Toast.makeText(this, "🎉 Превозното средство \"$vehicleName\" е добавено!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
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
        val popularIndex = brands.indexOf("Най-популярни")
        val firstRegularBrandIndex = if (popularIndex >= 0) popularIndex + 8 else 0 // 7 популярни марки + 1 за "Най-популярни"
        
        val adapter = object : ArrayAdapter<String>(this, 0, brands) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val layoutInflater = LayoutInflater.from(context)
                val view: android.view.View
                
                when {
                    brands[position] == "Най-популярни" -> {
                        view = layoutInflater.inflate(R.layout.dropdown_item_popular, parent, false)
                        val textView = view.findViewById<android.widget.TextView>(R.id.text1)
                        textView.text = "Най-популярни"
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
                    brands[position] == "Най-популярни" -> 0
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

    private fun showEditProfileDialog(profile: Profile) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_profile, null)
        val etName = dialogView.findViewById<EditText>(R.id.etProfileName)
        val spinnerType = dialogView.findViewById<Spinner>(R.id.spinnerVehicleType)

        etName.setText(profile.name)
        ArrayAdapter.createFromResource(
            this,
            R.array.vehicle_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerType.adapter = adapter
        }
        spinnerType.setSelection(if (profile.vehicleType == Profile.VehicleType.CAR) 0 else 1)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.profile_edit_text))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.dialog_cancel_button), null)
            .setPositiveButton(getString(R.string.profile_save_button), null)
            .create()

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
        loadProfiles()
        updateActiveProfileCard()
        updateProfileCount()

        // Обновяваме статистиките в adapter-а
        adapter.notifyDataSetChanged()
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

        holder.ivProfileIcon.setImageResource(iconRes)
        holder.tvType.text = "$emoji ${getVehicleTypeText(profile.vehicleType)}"

        // Зареждаме реални статистики за профила
        val profileStats = getProfileStatistics(profile.id)

        // Обновяваме статистика чиповете
        holder.tvSessionCount.text = "⚡ ${profileStats.sessionCount} сесии"

        if (profileStats.maxSpeed > 0f) {
            holder.tvMaxSpeed.text = "🏁 ${profileStats.maxSpeed.toInt()} км/ч"
        } else {
            holder.tvMaxSpeed.text = "🏁 -- км/ч макс"
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

    // Data class за статистиките
    data class ProfileStats(
        val sessionCount: Int,
        val maxSpeed: Float
    )
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
