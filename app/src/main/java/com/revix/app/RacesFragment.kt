package com.revix.app

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
import com.revix.app.data.ProfileStorage
import com.revix.app.main.MainContainerActivity
import com.revix.app.main.map.MapActivity
import com.revix.app.settings.LanguageManager
import com.google.android.material.button.MaterialButton

/**
 * Fragment за страницата с сесии от нормалното каране
 * Използва се в MainContainerActivity за instant navigation без презареждане
 */
class RacesFragment : Fragment() {
    
    fun isSelectionModeEnabled(): Boolean {
        return ::adapter.isInitialized && adapter.isSelectionModeEnabled()
    }

    private lateinit var adapter: RaceAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var btnBack: View
    private lateinit var btnAll: MaterialButton
    private lateinit var btnFavorites: MaterialButton
    private lateinit var btnDeleteSelected: MaterialButton
    private val racesList = mutableListOf<Race>()
    private val allRacesCache = mutableListOf<Race>() // Кеш за всички сесии (включително от други профили)
    private var backPressedTime: Long = 0
    private lateinit var backToast: Toast
    private var isShowingFavorites = false
    
    // Executor за последователно запазване/зареждане (queue)
    private val saveExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var loadGeneration = 0
    
    private val longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressListener: androidx.recyclerview.widget.RecyclerView.OnItemTouchListener? = null
    private var backPressedCallback: androidx.activity.OnBackPressedCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_races, container, false)
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
                val favoriteTimestamp = if (newFavoriteStatus) System.currentTimeMillis() else null
                adapter.updateFavoriteStatus(race.id, newFavoriteStatus)
                
                // Обновяваме локално в кеша веднага (без да чакаме storage)
                val cacheIdx = allRacesCache.indexOfFirst { it.id == race.id }
                if (cacheIdx >= 0) {
                    allRacesCache[cacheIdx].isFavorite = newFavoriteStatus
                    allRacesCache[cacheIdx].favoriteTimestamp = favoriteTimestamp
                }
                
                // Обновяваме и в racesList ако е там
                val localIdx = racesList.indexOfFirst { it.id == race.id }
                if (localIdx >= 0) {
                    racesList[localIdx].isFavorite = newFavoriteStatus
                    racesList[localIdx].favoriteTimestamp = favoriteTimestamp
                }
                
                // Запазваме в background thread за да не блокираме UI.
                // Винаги load+update от диска — кешът може да е остарял след нова сесия.
                val appContext = context?.applicationContext
                saveExecutor.execute {
                    try {
                        appContext ?: return@execute
                        val all = RouteStorage.loadRaces(appContext).toMutableList()
                        val idx = all.indexOfFirst { it.id == race.id }
                        if (idx < 0) return@execute
                        all[idx].isFavorite = newFavoriteStatus
                        all[idx].favoriteTimestamp = favoriteTimestamp
                        RouteStorage.saveRaces(appContext, all)
                    } catch (e: Exception) {
                        android.util.Log.e("RacesFragment", "Error updating favorite", e)
                        // При грешка, връщаме UI обратно
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if (!isAdded || view == null) return@post
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
                    context?.let { applyRacesToAdapter(it) }
                }
            },
            onLongClick = { race, position ->
                // Long press на целия контейнер - влизаме в режим на избор
                enterSelectionMode(position)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        recyclerView.isNestedScrollingEnabled = true

        // Настройка на бутона за назад - винаги видим
        btnBack.setOnClickListener {
            if (adapter.isSelectionModeEnabled()) {
                // Ако сме в режим на избор, деактивираме го
                exitSelectionMode()
            } else {
                // Иначе навигираме към Map
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
        
        // Long press listener за активиране на режим на избор (старият подход - може да се премахне)
        longPressListener = object : androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN) {
                    val view = rv.findChildViewUnder(e.x, e.y)
                    if (view != null) {
                        val position = rv.getChildAdapterPosition(view)
                        if (position != RecyclerView.NO_POSITION) {
                            val holder = rv.findViewHolderForAdapterPosition(position) as? RaceAdapter.RaceViewHolder
                            if (holder != null) {
                                longPressHandler.postDelayed({
                                    // Проверяваме дали fragment-ът все още е активен
                                    if (isAdded && view.isAttachedToWindow && !adapter.isSelectionModeEnabled() && 
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
        }
        recyclerView.addOnItemTouchListener(longPressListener!!)

        checkEmptyList()
        
        // Зареждаме данните в background след като adapter-ът е готов
        loadRaces()
        
        // Обработка на back бутона - ако сме в selection mode, излизаме от него
        // Регистрираме callback-а тук, но ще го активираме/деактивираме в enterSelectionMode/exitSelectionMode
        if (backPressedCallback == null) {
            backPressedCallback = object : androidx.activity.OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    // Ако сме в режим на избор, излизаме от него
                    exitSelectionMode()
                }
            }
            requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback!!)
        }
    }
    
    private fun enterSelectionMode(initialPosition: Int) {
        adapter.setSelectionMode(true)
        adapter.toggleSelection(racesList[initialPosition].id)
        btnDeleteSelected.visibility = View.VISIBLE
        // Активираме back callback за да излизаме от selection mode с back бутона
        // Уверяваме се че callback-ът е регистриран (ако не е, го регистрираме)
        if (backPressedCallback == null && view != null) {
            backPressedCallback = object : androidx.activity.OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    // Ако сме в режим на избор, излизаме от него
                    exitSelectionMode()
                }
            }
            requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback!!)
        }
        backPressedCallback?.isEnabled = true
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
        // Деактивираме back callback - back бутонът работи нормално
        backPressedCallback?.isEnabled = false
    }
    
    // Публичен метод за извикване от Activity callback-а
    fun exitSelectionModeDirectly() {
        exitSelectionMode()
    }

    private fun setupTabButtons() {
        // Начално състояние - All е активен
        updateTabButtons(false)

        btnAll.setOnClickListener {
            if (isShowingFavorites) {
                isShowingFavorites = false
                updateTabButtons(false)
                context?.let { applyRacesToAdapter(it) }
            }
        }

        btnFavorites.setOnClickListener {
            if (!isShowingFavorites) {
                isShowingFavorites = true
                updateTabButtons(true)
                context?.let { applyRacesToAdapter(it) }
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

    fun reloadSessionsFromStorage() {
        if (!isAdded || view == null || !::adapter.isInitialized) return
        loadRaces()
    }

    private fun loadRaces() {
        val appContext = context?.applicationContext ?: return
        if (saveExecutor.isShutdown) return
        val generation = ++loadGeneration

        try {
            saveExecutor.execute {
                val startTime = System.currentTimeMillis()
                val races = RouteStorage.loadRaces(appContext)
                val loadTime = System.currentTimeMillis() - startTime
                android.util.Log.d("RacesFragment", "📂 Loaded ${races.size} races in ${loadTime}ms")

                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (generation != loadGeneration) return@post
                    if (!isAdded || view == null) return@post
                    val currentContext = context ?: return@post

                    allRacesCache.clear()
                    allRacesCache.addAll(races)
                    applyRacesToAdapter(currentContext)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
        }
    }

    private fun applyRacesToAdapter(currentContext: Context) {
        refreshRacesListFromCache()
        adapter.preloadProfiles(currentContext)
        adapter.preloadDateFormats(currentContext, racesList)
        adapter.updateRaces(racesList)
        checkEmptyList()
        recyclerView.post {
            if (!isAdded || view == null) return@post
            recyclerView.requestLayout()
        }
    }
    
    /**
     * Обновява racesList базирайки се на кеширания allRacesCache.
     * Това е много по-бързо от зареждане от storage всеки път.
     */
    private fun refreshRacesListFromCache() {
        val currentContext = context ?: return

        racesList.clear()
        val currentProfileId = ProfileStorage.getSelectedProfileId(currentContext)
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
        // Изчистваме всички pending callbacks при resume
        longPressHandler.removeCallbacksAndMessages(null)
        
        // Уверяваме се че back callback-ът е регистриран (ако view-то вече съществува, onViewCreated не се извиква отново)
        if (backPressedCallback == null && view != null) {
            backPressedCallback = object : androidx.activity.OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    // Ако сме в режим на избор, излизаме от него
                    exitSelectionMode()
                }
            }
            requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback!!)
        }
        
        // Уверяваме се че long press listener е добавен
        if (longPressListener != null && ::recyclerView.isInitialized) {
            try {
                recyclerView.removeOnItemTouchListener(longPressListener!!)
            } catch (e: Exception) {
                // Игнорираме ако listener-ът не е бил добавен
            }
            recyclerView.addOnItemTouchListener(longPressListener!!)
        }
        
        // Винаги от диска: фрагментът остава жив в ViewPager и кешът пропуска
        // новозаписани сесии след Save → Details → Back.
        if (::adapter.isInitialized) {
            loadRaces()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Изчистваме всички pending callbacks при pause
        longPressHandler.removeCallbacksAndMessages(null)
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
                putExtra(MainContainerActivity.EXTRA_NAV_ITEM_ID, R.id.navMap)
            }
            startActivity(intent)
            activity.finish()
        }
    }

    private fun showDeleteConfirmation(race: Race) {
        DialogHelper.showDeleteConfirmation(
            context = requireContext(),
            title = getString(R.string.races_delete_session_title),
            message = getString(R.string.delete_confirmation, race.name ?: getString(R.string.races_session_fallback_name)),
            positiveButtonText = getString(R.string.delete_button),
            negativeButtonText = getString(R.string.cancel_button)
        ) {
            val all = RouteStorage.loadRaces(requireContext()).toMutableList()
            all.removeAll { it.id == race.id }
            RouteStorage.saveRaces(requireContext(), all)
            loadRaces()
            Toast.makeText(requireContext(), getString(R.string.races_toast_session_deleted), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showMultiDeleteConfirmation(races: List<Race>) {
        val count = races.size
        DialogHelper.showDeleteConfirmation(
            context = requireContext(),
            title = getString(R.string.races_delete_sessions_title),
            message = getString(R.string.races_delete_selected_message, count),
            positiveButtonText = getString(R.string.delete_button),
            negativeButtonText = getString(R.string.cancel_button)
        ) {
            val all = RouteStorage.loadRaces(requireContext()).toMutableList()
            val raceIds = races.map { it.id }.toSet()
            all.removeAll { raceIds.contains(it.id) }
            RouteStorage.saveRaces(requireContext(), all)
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
            exitSelectionMode()
            loadRaces()
            Toast.makeText(requireContext(), getString(R.string.races_toast_deleted_selected, count), Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Премахваме listener при destroy на view
        if (longPressListener != null && ::recyclerView.isInitialized) {
            try {
                recyclerView.removeOnItemTouchListener(longPressListener!!)
            } catch (e: Exception) {
                // Игнорираме ако listener-ът не е бил добавен
            }
        }
        longPressHandler.removeCallbacksAndMessages(null)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        saveExecutor.shutdown()
        longPressHandler.removeCallbacksAndMessages(null)
    }
}

