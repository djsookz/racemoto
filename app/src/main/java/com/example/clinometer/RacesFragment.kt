package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.settings.LanguageManager
import com.google.android.material.button.MaterialButton

/**
 * Fragment за страницата с сесии от нормалното каране
 * Конвертиран от RacesActivity за instant navigation без презареждане
 */
class RacesFragment : Fragment() {

    private lateinit var adapter: RaceAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var btnBack: MaterialButton
    private lateinit var btnAll: MaterialButton
    private lateinit var btnFavorites: MaterialButton
    private lateinit var btnDeleteSelected: MaterialButton
    private val racesList = mutableListOf<Race>()
    private val allRacesCache = mutableListOf<Race>() // Кеш за всички сесии (включително от други профили)
    private var backPressedTime: Long = 0
    private lateinit var backToast: Toast
    private var isShowingFavorites = false
    
    // Executor за последователно запазване на промените (queue)
    private val saveExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    
    private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_races, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        recyclerView = view.findViewById(R.id.rvRaces)
        emptyView = view.findViewById(R.id.tvEmptyView)
        btnBack = view.findViewById(R.id.btnBack)
        btnAll = view.findViewById(R.id.btnAll)
        btnFavorites = view.findViewById(R.id.btnFavorites)
        btnDeleteSelected = view.findViewById(R.id.btnDeleteSelected)

        // Настройка на бутоните за табове
        setupTabButtons()

        loadRaces()

        adapter = RaceAdapter(
            races = racesList,
            onItemClick = { race ->
                val intent = Intent(requireContext(), MapActivity::class.java).apply {
                    putExtra("RACE_ID", race.id)
                }
                startActivity(intent)
            },
            onDeleteClick = { race ->
                showDeleteConfirmation(race)
            },
            onRename = { race, newName ->
                val all = RouteStorage.loadRaces(requireContext()).toMutableList()
                val idx = all.indexOfFirst { it.id == race.id }
                if (idx >= 0) {
                    all[idx].name = newName
                    RouteStorage.saveRaces(requireContext(), all)
                }
            },
            onFavoriteToggle = { race ->
                // Обновяваме UI веднага за instant feedback
                val newFavoriteStatus = !race.isFavorite
                adapter.updateFavoriteStatus(race.id, newFavoriteStatus)
                
                // Обновяваме локално в кеша веднага (без да чакаме storage)
                val cacheIdx = allRacesCache.indexOfFirst { it.id == race.id }
                if (cacheIdx >= 0) {
                    allRacesCache[cacheIdx].isFavorite = newFavoriteStatus
                    allRacesCache[cacheIdx].favoriteTimestamp = if (newFavoriteStatus) {
                        System.currentTimeMillis()
                    } else {
                        null
                    }
                }
                
                // Обновяваме и в racesList ако е там
                val localIdx = racesList.indexOfFirst { it.id == race.id }
                if (localIdx >= 0) {
                    racesList[localIdx].isFavorite = newFavoriteStatus
                    racesList[localIdx].favoriteTimestamp = if (newFavoriteStatus) {
                        System.currentTimeMillis()
                    } else {
                        null
                    }
                }
                
                // Запазваме в background thread за да не блокираме UI
                // Използваме saveExecutor за последователно запазване (queue)
                saveExecutor.execute {
                    try {
                        // Използваме кеширания списък вместо да зареждаме отново
                        RouteStorage.saveRaces(requireContext(), allRacesCache.toList())
                    } catch (e: Exception) {
                        android.util.Log.e("RacesFragment", "Error updating favorite", e)
                        // При грешка, връщаме UI обратно
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            adapter.updateFavoriteStatus(race.id, !newFavoriteStatus)
                            // Връщаме и кеша
                            if (cacheIdx >= 0) {
                                allRacesCache[cacheIdx].isFavorite = !newFavoriteStatus
                                allRacesCache[cacheIdx].favoriteTimestamp = if (!newFavoriteStatus) null else allRacesCache[cacheIdx].favoriteTimestamp
                            }
                            if (localIdx >= 0) {
                                racesList[localIdx].isFavorite = !newFavoriteStatus
                                racesList[localIdx].favoriteTimestamp = if (!newFavoriteStatus) null else racesList[localIdx].favoriteTimestamp
                            }
                        }
                    }
                }
                
                // Ако сме в "Favorites" таб, обновяваме списъка веднага (без да чакаме storage)
                if (isShowingFavorites) {
                    refreshRacesListFromCache()
                    adapter.updateRaces(racesList)
                    checkEmptyList()
                }
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // Настройка на бутона за назад
        btnBack.setOnClickListener {
            if (adapter.isSelectionModeEnabled()) {
                // Ако сме в режим на избор, деактивираме го
                exitSelectionMode()
            } else {
                navigateToMap()
            }
        }
        
        // Настройка на бутона за изтриване на избраните
        btnDeleteSelected.setOnClickListener {
            val selectedRaces = adapter.getSelectedRaces()
            if (selectedRaces.isNotEmpty()) {
                showMultiDeleteConfirmation(selectedRaces)
            }
        }
        
        // Long press listener за активиране на режим на избор
        recyclerView.addOnItemTouchListener(object : androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN) {
                    val view = rv.findChildViewUnder(e.x, e.y)
                    if (view != null) {
                        val position = rv.getChildAdapterPosition(view)
                        if (position != RecyclerView.NO_POSITION) {
                            val holder = rv.findViewHolderForAdapterPosition(position) as? RaceAdapter.RaceViewHolder
                            if (holder != null) {
                                longPressHandler.postDelayed({
                                    if (!adapter.isSelectionModeEnabled() && 
                                        rv.findChildViewUnder(e.x, e.y) == view &&
                                        rv.getChildAdapterPosition(view) == position) {
                                        // Активираме режим на избор
                                        enterSelectionMode(position)
                                    }
                                }, 500) // 500ms за long press
                            }
                        }
                    }
                } else if (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL) {
                    longPressHandler.removeCallbacksAndMessages(null)
                }
                return false
            }
        })

        checkEmptyList()
    }
    
    private fun enterSelectionMode(initialPosition: Int) {
        adapter.setSelectionMode(true)
        adapter.toggleSelection(racesList[initialPosition].id)
        btnDeleteSelected.visibility = View.VISIBLE
        // Вибрация за feedback (опционално - не крашва ако няма разрешение)
        try {
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(50)
                }
            }
        } catch (e: Exception) {
            // Игнорираме грешката ако няма разрешение за вибрация
            android.util.Log.d("RacesFragment", "Vibration not available: ${e.message}")
        }
    }
    
    private fun exitSelectionMode() {
        adapter.setSelectionMode(false)
        adapter.clearSelection()
        btnDeleteSelected.visibility = View.GONE
    }

    private fun setupTabButtons() {
        // Начално състояние - All е активен
        updateTabButtons(false)

        btnAll.setOnClickListener {
            if (isShowingFavorites) {
                isShowingFavorites = false
                updateTabButtons(false)
                // Използваме локалния списък вместо да зареждаме от storage - много по-бързо!
                refreshRacesListFromCache()
                adapter.updateRaces(racesList)
                checkEmptyList()
            }
        }

        btnFavorites.setOnClickListener {
            if (!isShowingFavorites) {
                isShowingFavorites = true
                updateTabButtons(true)
                // Използваме локалния списък вместо да зареждаме от storage - много по-бързо!
                refreshRacesListFromCache()
                adapter.updateRaces(racesList)
                checkEmptyList()
            }
        }
    }

    private fun updateTabButtons(showFavorites: Boolean) {
        val orangeColor = Color.parseColor("#FF7A18") // Оранжев цвят като в останалата част на приложението
        if (showFavorites) {
            // Favorites е активен
            btnAll.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#333333"))
            btnAll.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            btnFavorites.backgroundTintList = ColorStateList.valueOf(orangeColor)
            btnFavorites.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        } else {
            // All е активен
            btnAll.backgroundTintList = ColorStateList.valueOf(orangeColor)
            btnAll.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            btnFavorites.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#333333"))
            btnFavorites.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
        }
    }

    private fun loadRaces() {
        // Зареждаме всички сесии от storage и ги кешираме
        allRacesCache.clear()
        allRacesCache.addAll(RouteStorage.loadRaces(requireContext()))
        
        // Филтрираме и показваме само тези за текущия профил
        refreshRacesListFromCache()
    }
    
    /**
     * Обновява racesList базирайки се на кеширания allRacesCache.
     * Това е много по-бързо от зареждане от storage всеки път.
     */
    private fun refreshRacesListFromCache() {
        racesList.clear()
        val currentProfileId = ProfileStorage.getSelectedProfileId(requireContext())
        var filteredRaces = allRacesCache.filter { it.profileId == currentProfileId }

        // Филтриране по Favorites ако е избран този таб
        if (isShowingFavorites) {
            filteredRaces = filteredRaces.filter { it.isFavorite }
            // Сортираме по favoriteTimestamp (най-новите най-отгоре)
            filteredRaces = filteredRaces.sortedByDescending { it.favoriteTimestamp ?: 0L }
        } else {
            // За "All" сортираме по absoluteTimestamp (както преди)
            filteredRaces = filteredRaces.sortedByDescending { it.absoluteTimestamp }
        }

        racesList.addAll(filteredRaces)
    }

    override fun onResume() {
        super.onResume()
        // Обновяваме данните само ако е необходимо (при завръщане от друга страница)
        loadRaces()
        adapter.updateRaces(racesList)
        checkEmptyList()
    }

    private fun checkEmptyList() {
        if (racesList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
        }
    }

    private fun navigateToMap() {
        // Навигираме към MAP страницата в MainContainerActivity
        val activity = requireActivity()
        if (activity is MainContainerActivity) {
            // Плавно превключваме към MAP страницата
            activity.navigateToPage(MainContainerActivity.PAGE_MAP)
        } else {
            // Fallback ако не сме в MainContainerActivity
            val intent = Intent(requireContext(), MainContainerActivity::class.java).apply {
                putExtra("NAV_ITEM_ID", R.id.navMap)
            }
            startActivity(intent)
            activity.finish()
        }
    }

    private fun showDeleteConfirmation(race: Race) {
        val deleteDialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle("Изтриване на сесия")
            .setMessage(getString(R.string.delete_confirmation, race.name ?: "Session"))
            .setPositiveButton(getString(R.string.delete_button)) { _, _ ->
                // Изтриваме от базата данни
                val all = RouteStorage.loadRaces(requireContext()).toMutableList()
                all.removeAll { it.id == race.id }
                RouteStorage.saveRaces(requireContext(), all)
                
                // Презареждаме всичко
                loadRaces()
                adapter.updateRaces(racesList)
                checkEmptyList()
                
                Toast.makeText(requireContext(), "✅ Сесията е изтрита", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .create()
        DialogHelper.styleDialogButtons(deleteDialog)
        deleteDialog.show()
    }
    
    private fun showMultiDeleteConfirmation(races: List<Race>) {
        val count = races.size
        val deleteDialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog)
            .setTitle("Изтриване на сесии")
            .setMessage("Сигурни ли сте, че искате да изтриете $count ${if (count == 1) "сесия" else "сесии"}?")
            .setPositiveButton(getString(R.string.delete_button)) { _, _ ->
                // Изтриваме от базата данни
                val all = RouteStorage.loadRaces(requireContext()).toMutableList()
                val raceIds = races.map { it.id }.toSet()
                all.removeAll { raceIds.contains(it.id) }
                RouteStorage.saveRaces(requireContext(), all)
                
                // Изтриваме и route points файловете
                races.forEach { race ->
                    try {
                        val file = java.io.File(requireContext().filesDir, "route_points/points_${race.id}.json")
                        if (file.exists()) {
                            file.delete()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("RacesFragment", "Error deleting route points for race ${race.id}", e)
                    }
                }
                
                // Деактивираме режим на избор
                exitSelectionMode()
                
                // Презареждаме всичко
                loadRaces()
                adapter.updateRaces(racesList)
                checkEmptyList()
                
                Toast.makeText(requireContext(), "✅ $count ${if (count == 1) "сесия е изтрита" else "сесии са изтрити"}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .create()
        DialogHelper.styleDialogButtons(deleteDialog)
        deleteDialog.show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        saveExecutor.shutdown()
        longPressHandler.removeCallbacksAndMessages(null)
    }
}

