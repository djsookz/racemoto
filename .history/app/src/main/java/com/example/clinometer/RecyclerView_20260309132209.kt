package com.example.clinometer

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Outline
import android.text.InputType
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.data.ProfileStorage
import com.example.clinometer.main.map.SaveSessionActivity
import java.util.Calendar
import java.util.Date

class RaceAdapter(
    races: MutableList<Race>,
    private val onItemClick: (Race) -> Unit,
    private val onDeleteClick: (Race) -> Unit,
    private val onRename: (Race, String) -> Unit,
    private val onFavoriteToggle: (Race) -> Unit,
    private val onMultiDeleteClick: (List<Race>) -> Unit = {},
    private val onLongClick: (Race, Int) -> Unit = { _, _ -> } // position за да знаем коя сесия е
) : RecyclerView.Adapter<RaceAdapter.RaceViewHolder>() {

    // Режим на избор за множествено изтриване
    private var isSelectionMode = false
    private val selectedRaces = mutableSetOf<Long>()

    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) {
                selectedRaces.clear()
            }
            notifyDataSetChanged()
        }
    }

    fun isSelectionModeEnabled(): Boolean = isSelectionMode

    fun getSelectedRaces(): List<Race> {
        return races.filter { selectedRaces.contains(it.id) }
    }

    fun toggleSelection(raceId: Long) {
        val position = races.indexOfFirst { it.id == raceId }
        if (position >= 0) {
            if (selectedRaces.contains(raceId)) {
                selectedRaces.remove(raceId)
            } else {
                selectedRaces.add(raceId)
            }
            // Използваме payload за частично обновяване - НЕ презареждаме картите!
            notifyItemChanged(position, "selection_changed")
        }
    }

    fun clearSelection() {
        selectedRaces.clear()
        notifyDataSetChanged()
    }

    private var races: MutableList<Race> = races.sortedByDescending { it.absoluteTimestamp }.toMutableList()

    // Кеш за профили - зарежда се предварително с preloadProfiles()
    private var profilesCache: List<Profile>? = null
    private var profilesCacheContext: android.content.Context? = null
    private var profilesLoading = false

    // Кеш за bitmap-и на профилни снимки
    private val profileImageCache = mutableMapOf<String, android.graphics.Bitmap?>()

    // Кеш за форматирани дати
    private val dateFormatCache = mutableMapOf<Long, String>()

    // Executor за background задачи
    private val routePointsExecutor = java.util.concurrent.Executors.newFixedThreadPool(4)

    fun updateRaces(newRaces: List<Race>) {
        races.clear()
        races.addAll(newRaces)
        notifyDataSetChanged()
    }
    
    fun preloadProfiles(context: android.content.Context) {
        if (profilesCache == null || profilesCacheContext != context) {
            if (!profilesLoading) {
                profilesLoading = true
                routePointsExecutor.execute {
                    val loadStart = System.currentTimeMillis()
                    val profiles = ProfileStorage.loadProfiles(context)
                    val loadTime = System.currentTimeMillis() - loadStart
                    if (loadTime > 10) {
                        Log.d("RaceAdapter", "⚠️ ProfileStorage.loadProfiles took ${loadTime}ms")
                    }
                    
                    // Зареждаме всички профилни снимки в background
                    profiles.forEach { profile ->
                        val imagePath = profile.imagePath // Запазваме в локална променлива за smart cast
                        if (!imagePath.isNullOrEmpty()) {
                            try {
                                val imageFile = java.io.File(context.getExternalFilesDir(null), imagePath)
                                if (imageFile.exists() && !profileImageCache.containsKey(imagePath)) {
                                    // Image is already scaled on disk, just load it
                                    val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                                    if (bitmap != null) {
                                        profileImageCache[imagePath] = bitmap
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("RaceAdapter", "Error preloading profile image: $imagePath", e)
                            }
                        }
                    }
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        profilesCache = profiles
                        profilesCacheContext = context
                        profilesLoading = false
                        // КРИТИЧНО: Обновяваме всички видими holders след зареждане на профилите и снимките
                        notifyDataSetChanged()
                    }
                }
            }
        }
    }
    
    fun clearImageCacheForPath(imagePath: String?) {
        imagePath?.let {
            profileImageCache.remove(it)
        }
    }
    
    fun preloadDateFormats(context: android.content.Context, racesToPreload: List<Race>) {
        racesToPreload.forEach { race ->
            val dateCacheKey = race.absoluteTimestamp
            if (!dateFormatCache.containsKey(dateCacheKey)) {
                val nowMidnight = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val thenMidnight = Calendar.getInstance().apply {
                    timeInMillis = race.absoluteTimestamp
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val diffMillis = nowMidnight.timeInMillis - thenMidnight.timeInMillis
                val days = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
                val formattedDate = when {
                    days < 0 -> DateFormat.format("dd.MM.yyyy", Date(race.absoluteTimestamp)).toString()
                    days == 0 -> context.getString(R.string.session_today)
                    days == 1 -> context.getString(R.string.session_yesterday)
                    else -> context.resources.getQuantityString(R.plurals.session_days, days, days)
                }
                dateFormatCache[dateCacheKey] = formattedDate
            }
        }
    }

    fun updateFavoriteStatus(raceId: Long, isFavorite: Boolean) {
        val position = races.indexOfFirst { it.id == raceId }
        if (position >= 0) {
            races[position].isFavorite = isFavorite
            races[position].favoriteTimestamp = if (isFavorite) System.currentTimeMillis() else null
            notifyItemChanged(position, "favorite_changed")
        }
    }

    companion object {
        private const val MENU_RENAME = 1
        private const val MENU_DELETE = 2
    }

    inner class RaceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvName)
        val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        val tvDuration: TextView = itemView.findViewById(R.id.tvNumber)
        val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        val tvVehicleName: TextView = itemView.findViewById(R.id.tvVehicleName)
        val ivProfileImage: android.widget.ImageView = itemView.findViewById(R.id.ivProfileImage)
        val btnFavorite: ImageButton = itemView.findViewById(R.id.btnFavorite)
        val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
        val miniMapContainer: FrameLayout = itemView.findViewById(R.id.miniMapContainer)
        val layoutMapPlaceholder: LinearLayout = itemView.findViewById(R.id.layoutMapPlaceholder)
        val checkboxSelect: android.widget.CheckBox = itemView.findViewById(R.id.checkboxSelect)
        val miniMapSnapshot: android.widget.ImageView = itemView.findViewById(R.id.miniMapSnapshot)

        // Намираме CardView-а - той е родителя на itemView
        val cardView: androidx.cardview.widget.CardView? by lazy {
            var parent = itemView.parent
            while (parent != null && parent !is androidx.cardview.widget.CardView) {
                parent = (parent as? ViewGroup)?.parent
            }
            parent as? androidx.cardview.widget.CardView
        }

        var isRouteLoaded = false  // Flag to prevent reloading route on scroll
        var loadedRaceId: Long? = null  // Track which race is currently loaded
        var boundRaceId: Long? = null
        var loadSnapshotRunnable: Runnable? = null  // Runnable за отменяне на заявките при скрол

        /**
         * 🔥 ХИБРИДЕН МОДЕЛ: Работи само с файлове, никога не блокира main thread
         * Принцип: Първо проверяваме файла, ако го няма - пускаме в опашка за генериране
         * Това е като стария OSMDroid код - бърз и лесен, без тежки Mapbox операции
         */
        fun loadMiniMapSnapshot(race: Race) {
            val context = itemView.context
            val raceId = race.id
            miniMapSnapshot.setTag(R.id.tag_route_snapshot_race_id, raceId)

            // Проверяваме дали snapshot вече е зареден за този race
            if (isRouteLoaded && loadedRaceId == raceId) {
                miniMapSnapshot.visibility = View.VISIBLE
                layoutMapPlaceholder.visibility = View.GONE
                return
            }

            // 1. Първо проверяваме дали файлът вече съществува (асинхронно, не блокира)
            routePointsExecutor.execute {
                try {
                    val snapshotFile = RouteSnapshotGenerator.getSnapshotFile(context, raceId)
                    val fileExists = snapshotFile.exists()
                    
                    if (fileExists) {
                        // Snapshot съществува - използваме displaySnapshot с празен списък точки
                        // displaySnapshot ще зареди от диска или memory cache
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if (itemView.isAttachedToWindow && boundRaceId == raceId) {
                                miniMapSnapshot.visibility = View.VISIBLE
                                layoutMapPlaceholder.visibility = View.GONE
                                // Подаваме празен списък - displaySnapshot няма да генерира, ще зареди от диска
                                RouteSnapshotGenerator.displaySnapshot(miniMapSnapshot, raceId, emptyList())
                                isRouteLoaded = true
                                loadedRaceId = raceId
                            }
                        }

                        // Еднократен refresh за стари/грешни snapshot-и.
                        // Показваме кеша веднага, после регенерираме във фон и подменяме миникартата.
                        if (RouteSnapshotGenerator.markOneTimeRefreshRequested(raceId)) {
                            val allRoutePoints = RouteStorage.loadRoutePoints(context, raceId)
                            if (allRoutePoints.isNotEmpty()) {
                                val sampled = sampleRoutePoints(allRoutePoints, maxPoints = 200)
                                RouteSnapshotGenerator.generateAndSaveSnapshot(
                                    context = context,
                                    raceId = raceId,
                                    routePoints = sampled,
                                    forceRegenerate = true
                                ) { success ->
                                    if (!success) return@generateAndSaveSnapshot
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        if (itemView.isAttachedToWindow && boundRaceId == raceId) {
                                            miniMapSnapshot.visibility = View.VISIBLE
                                            layoutMapPlaceholder.visibility = View.GONE
                                            RouteSnapshotGenerator.displaySnapshot(miniMapSnapshot, raceId, emptyList())
                                            isRouteLoaded = true
                                            loadedRaceId = raceId
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // 2. Ако го няма, показваме placeholder и пускаме генератора в background
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if (itemView.isAttachedToWindow && boundRaceId == raceId) {
                                miniMapSnapshot.visibility = View.GONE
                                layoutMapPlaceholder.visibility = View.VISIBLE
                            }
                        }
                        
                        // Зареждаме точките само веднъж, ако файлът липсва
                        val allRoutePoints = RouteStorage.loadRoutePoints(context, raceId)
                        if (allRoutePoints.isNotEmpty()) {
                            val sampled = sampleRoutePoints(allRoutePoints, maxPoints = 200)
                            
                            // Използваме displaySnapshot - тя автоматично ще генерира snapshot
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                if (itemView.isAttachedToWindow && boundRaceId == raceId) {
                                    miniMapSnapshot.visibility = View.VISIBLE
                                    layoutMapPlaceholder.visibility = View.GONE
                                    RouteSnapshotGenerator.displaySnapshot(miniMapSnapshot, raceId, sampled)
                                    isRouteLoaded = true
                                    loadedRaceId = raceId
                                }
                            }
                        } else {
                            // Няма route points - показваме placeholder
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                if (itemView.isAttachedToWindow && boundRaceId == raceId) {
                                    miniMapSnapshot.visibility = View.GONE
                                    layoutMapPlaceholder.visibility = View.VISIBLE
                                    isRouteLoaded = false
                                    loadedRaceId = null
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RaceAdapter", "Error loading snapshot: raceId=$raceId", e)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (itemView.isAttachedToWindow && boundRaceId == raceId) {
                            miniMapSnapshot.visibility = View.GONE
                            layoutMapPlaceholder.visibility = View.VISIBLE
                        }
                    }
                }
                
            }
        }

        // ПРЕМАХНАТО: loadMapboxMiniMap и createMarkerIcon - използваме RouteSnapshotGenerator вместо това
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RaceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_race, parent, false)
        return RaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: RaceViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(holder: RaceViewHolder, position: Int, payloads: MutableList<Any>) {
        val startTime = System.currentTimeMillis()
        // КРИТИЧНО: Използваме adapterPosition вместо position за да избегнем проблеми при скрол
        val adapterPosition = holder.adapterPosition
        if (adapterPosition == RecyclerView.NO_POSITION) {
            return
        }
        val race = races.getOrNull(adapterPosition) ?: return
        holder.boundRaceId = race.id
        holder.miniMapSnapshot.setTag(R.id.tag_route_snapshot_race_id, race.id)
        Log.d("RaceAdapter", "🔵 onBindViewHolder START: position=$position, adapterPosition=$adapterPosition, raceId=${race.id}, payloads=$payloads")
        
        // Ако има payload "favorite_changed", обновяваме само favorite иконата
        if (payloads.isNotEmpty() && payloads.contains("favorite_changed")) {
            val race = races.getOrNull(adapterPosition) ?: return
            // Обновяваме само favorite иконата - НЕ презареждаме картата!
            if (race.isFavorite) {
                holder.btnFavorite.setImageResource(R.drawable.ic_favorite)
                holder.btnFavorite.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF7A18"))
            } else {
                holder.btnFavorite.setImageResource(R.drawable.ic_favorite_border)
                holder.btnFavorite.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            }
            return
        }

        if (payloads.isNotEmpty() && payloads.contains("selection_changed")) {
            val race = races.getOrNull(adapterPosition) ?: return
            val isSelected = selectedRaces.contains(race.id)
            holder.checkboxSelect.isChecked = isSelected
            
            // Обновяваме фона на CardView
            var parent = holder.itemView.parent
            while (parent != null && parent !is androidx.cardview.widget.CardView) {
                parent = (parent as? ViewGroup)?.parent
            }
            val cardView = parent as? androidx.cardview.widget.CardView
            if (cardView != null) {
                if (isSelected) {
                    cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#5A5D60"))
                } else {
                    cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#3A3D40"))
                }
            }
            return
        }

        // Нормално bind-ване
        val dateCacheKey = race.absoluteTimestamp
        val formattedDate = dateFormatCache.getOrPut(dateCacheKey) {
            val ctx = holder.itemView.context
            val nowMidnight = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val thenMidnight = Calendar.getInstance().apply {
                timeInMillis = race.absoluteTimestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val diffMillis = nowMidnight.timeInMillis - thenMidnight.timeInMillis
            val days = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
            when {
                days < 0 -> DateFormat.format("dd.MM.yyyy", Date(race.absoluteTimestamp)).toString()
                days == 0 -> ctx.getString(R.string.session_today)
                days == 1 -> ctx.getString(R.string.session_yesterday)
                else -> ctx.resources.getQuantityString(R.plurals.session_days, days, days)
            }
        }
        
        holder.tvTitle.text = race.name
            ?: holder.itemView.context.getString(R.string.session_title, adapterPosition + 1)
        holder.dateTextView.text = formattedDate
        holder.tvDistance.text = String.format("%.2f km", race.distance)
        holder.tvDuration.text = formatTime(race.duration)

        // === ПРОФИЛ И СНИМКА – чиста, надеждна логика ===
        // Профилите трябва да са заредени чрез preloadProfiles() преди updateRaces()
        // Ако кешът е празен, показваме placeholder (НЕ зареждаме тук - това създава стотици задачи при скрол!)
        val profile = profilesCache?.find { it.id == race.profileId }

        if (profile != null) {
            // Име на превозно
            holder.tvVehicleName.text = profile.name ?: ""

            // Снимка на профила - САМО от memory cache (0ms, без асинхронни задачи)
            if (!profile.imagePath.isNullOrEmpty()) {
                val imagePath = profile.imagePath!!
                profileImageCache[imagePath]?.let { bitmap ->
                    // Снимката е в кеша - показваме веднага (синхронно, 0ms)
                    holder.ivProfileImage.setImageBitmap(bitmap)
                    holder.ivProfileImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    holder.ivProfileImage.clipToOutline = true
                    holder.ivProfileImage.outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setOval(0, 0, view.width, view.height)
                        }
                    }
                } ?: run {
                    // Няма в кеша - показваме placeholder (НЕ зареждаме тук - това се прави в preloadProfiles!)
                    val placeholderRes = when (profile.vehicleType) {
                        Profile.VehicleType.CAR -> R.drawable.ic_car
                        Profile.VehicleType.MOTORCYCLE -> R.drawable.ic_motorcycle
                        else -> R.drawable.ic_car
                    }
                    holder.ivProfileImage.setImageResource(placeholderRes)
                    holder.ivProfileImage.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    holder.ivProfileImage.clipToOutline = false
                }
            } else {
                // Няма imagePath – показваме иконка според типа
                val iconRes = when (profile.vehicleType) {
                    Profile.VehicleType.CAR -> R.drawable.ic_car
                    Profile.VehicleType.MOTORCYCLE -> R.drawable.ic_motorcycle
                    else -> R.drawable.ic_car
                }
                holder.ivProfileImage.setImageResource(iconRes)
                holder.ivProfileImage.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                holder.ivProfileImage.clipToOutline = false
            }
        } else {
            // Няма профил или кешът е празен – показваме placeholder
            holder.tvVehicleName.text = ""
            holder.ivProfileImage.setImageResource(R.drawable.ic_car)
            holder.ivProfileImage.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            holder.ivProfileImage.clipToOutline = false
        }

        // Зареждане на snapshot (остава непроменено - работи перфектно)
        if (holder.isRouteLoaded && holder.loadedRaceId == race.id) {
            holder.miniMapSnapshot.visibility = View.VISIBLE
            holder.layoutMapPlaceholder.visibility = View.GONE
        } else {
            val cacheKey = race.id.toString()
            val cachedBitmap = RouteSnapshotGenerator.getCachedBitmap(cacheKey)
            if (cachedBitmap != null) {
                holder.miniMapSnapshot.setImageBitmap(cachedBitmap)
                holder.miniMapSnapshot.visibility = View.VISIBLE
                holder.layoutMapPlaceholder.visibility = View.GONE
                holder.isRouteLoaded = true
                holder.loadedRaceId = race.id
            } else {
                holder.miniMapSnapshot.visibility = View.GONE
                holder.layoutMapPlaceholder.visibility = View.VISIBLE
                
                holder.loadSnapshotRunnable?.let { oldRunnable ->
                    holder.itemView.removeCallbacks(oldRunnable)
                }
                
                val layoutManager = (holder.itemView.parent as? RecyclerView)?.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                val firstVisible = layoutManager?.findFirstVisibleItemPosition() ?: 0
                val lastVisible = layoutManager?.findLastVisibleItemPosition() ?: Int.MAX_VALUE
                val isVisible = adapterPosition in firstVisible..lastVisible
                
                val delay = if (isVisible || adapterPosition < 5) 0L
                else if (adapterPosition < 10) 50L
                else 200L
                
                val runnable = Runnable {
                    val currentAdapterPosition = holder.adapterPosition
                    if (currentAdapterPosition != RecyclerView.NO_POSITION &&
                        races.getOrNull(currentAdapterPosition)?.id == race.id &&
                        holder.boundRaceId == race.id &&
                        holder.itemView.parent != null &&
                        holder.itemView.isAttachedToWindow) {
                        if (!holder.isRouteLoaded || holder.loadedRaceId != race.id) {
                            holder.loadMiniMapSnapshot(race)
                        }
                    }
                    holder.loadSnapshotRunnable = null
                }
                holder.loadSnapshotRunnable = runnable
                
                if (delay == 0L) {
                    holder.itemView.post(runnable)
                } else {
                    holder.itemView.postDelayed(runnable, delay)
                }
            }
        }

        holder.miniMapContainer.isClickable = false
        holder.miniMapContainer.isFocusable = false
        
        val mapTouchBlocker = holder.itemView.findViewById<View>(R.id.mapTouchBlocker)
        mapTouchBlocker?.setOnClickListener {
            holder.itemView.performClick()
        }
        if (isSelectionMode) {
            holder.checkboxSelect.visibility = View.VISIBLE
            val isSelected = selectedRaces.contains(race.id)
            holder.checkboxSelect.isChecked = isSelected

            // Визуален индикатор за избрана картела - променяме фона на CardView
            var parent = holder.itemView.parent
            while (parent != null && parent !is androidx.cardview.widget.CardView) {
                parent = (parent as? ViewGroup)?.parent
            }
            val cardView = parent as? androidx.cardview.widget.CardView
            if (cardView != null) {
                if (isSelected) {
                    // Избрана - по-светъл фон за визуален индикатор
                    cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#5A5D60")) // По-светъл фон
                } else {
                    // Неизбрана - нормален фон
                    cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#3A3D40"))
                }
            }

            // В режим на избор, кликването toggle-ва избора
            holder.itemView.isClickable = true
            holder.itemView.setOnClickListener {
                toggleSelection(race.id)
            }
            // Скриваме action бутоните в режим на избор
            holder.btnFavorite.visibility = View.GONE
            holder.btnEdit.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE
        } else {
            holder.checkboxSelect.visibility = View.GONE
            holder.checkboxSelect.isChecked = false

            // Връщаме нормалния фон
            var parent = holder.itemView.parent
            while (parent != null && parent !is androidx.cardview.widget.CardView) {
                parent = (parent as? ViewGroup)?.parent
            }
            val cardView = parent as? androidx.cardview.widget.CardView
            if (cardView != null) {
                cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#3A3D40"))
            }

            // Нормален режим - кликването отваря сесията
            holder.itemView.isClickable = true
            holder.itemView.setOnClickListener {
                if (!isSelectionMode) {
                    onItemClick(race)
                }
            }
            
            // Long press на целия контейнер за да влезем в режим на избор
            holder.itemView.setOnLongClickListener {
                if (!isSelectionMode) {
                    onLongClick(race, adapterPosition)
                    true // Consume the event
                } else {
                    false // Не правим нищо ако вече сме в selection mode
                }
            }
            // Показваме action бутоните
            holder.btnFavorite.visibility = View.VISIBLE
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnDelete.visibility = View.VISIBLE
        }

        // Показваме правилната икона според дали е любимо
        if (race.isFavorite) {
            holder.btnFavorite.setImageResource(R.drawable.ic_favorite)
            holder.btnFavorite.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF7A18"))
        } else {
            holder.btnFavorite.setImageResource(R.drawable.ic_favorite_border)
            holder.btnFavorite.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        }

        // Бутон за любими - toggle любими
        holder.btnFavorite.setOnClickListener {
            onFavoriteToggle(race)
        }

        // Бутон за редактиране - отваря SaveSessionActivity
        holder.btnEdit.setOnClickListener {
            val intent = Intent(holder.itemView.context, SaveSessionActivity::class.java).apply {
                putExtra("raceId", race.id)
            }
            holder.itemView.context.startActivity(intent)
        }

        // Бутон за триене
        holder.btnDelete.setOnClickListener {
            onDeleteClick(race)
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        if (totalTime > 20) {
            Log.d("RaceAdapter", "⚠️ onBindViewHolder TOTAL took ${totalTime}ms for adapterPosition=$adapterPosition, raceId=${race.id}")
        } else {
            Log.d("RaceAdapter", "✅ onBindViewHolder END: adapterPosition=$adapterPosition, raceId=${race.id}, time=${totalTime}ms")
        }
    }

    override fun getItemCount(): Int = races.size

    override fun onViewRecycled(holder: RaceViewHolder) {
        super.onViewRecycled(holder)
        // КРИТИЧНО: Отменяме всички pending заявки за зареждане на карти
        // Това предотвратява натрупването на заявки при скрол
        holder.loadSnapshotRunnable?.let { runnable ->
            holder.itemView.removeCallbacks(runnable)
            holder.loadSnapshotRunnable = null
        }
        holder.boundRaceId = null
        holder.miniMapSnapshot.setTag(R.id.tag_route_snapshot_race_id, null)
        // НЕ нулираме isRouteLoaded и loadedRaceId - оставяме картите заредени за да не се презареждат при скрол
    }
    

    /**
     * Sampling на route points за оптимизация на мини картите.
     * За големи сесии с хиляди точки, вземаме само проби за по-бързо зареждане и изчисления.
     * Винаги запазваме първата и последната точка за да виждаме началото и края.
     */
    private fun sampleRoutePoints(points: List<RoutePoint>, maxPoints: Int = 300): List<RoutePoint> {
        if (points.size <= maxPoints) {
            return points // Няма нужда от sampling
        }

        val sampled = mutableListOf<RoutePoint>()

        // Винаги добавяме първата точка
        sampled.add(points.first())

        // Изчисляваме стъпката за sampling
        val step = (points.size - 2) / (maxPoints - 2).toDouble() // -2 защото вече сме добавили първата

        // Добавяме проби с изчислената стъпка
        var currentIndex = step
        while (currentIndex < points.size - 1) {
            sampled.add(points[currentIndex.toInt()])
            currentIndex += step
        }

        // Винаги добавяме последната точка
        if (points.size > 1) {
            sampled.add(points.last())
        }

        return sampled
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun showRenameDialog(holder: RaceViewHolder, race: Race) {
        val input = EditText(holder.itemView.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(race.name ?: "")
            setSelection(text.length)
        }
        AlertDialog.Builder(holder.itemView.context)
            .setTitle(holder.itemView.context.getString(R.string.session_options_rename_popup_header))
            .setView(input)
            .setPositiveButton(holder.itemView.context.getString(R.string.session_options_rename_popup_ok)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    race.name = newName
                    onRename(race, newName)
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos)
                }
            }
            .setNegativeButton(holder.itemView.context.getString(R.string.dialog_cancel_button)) { dialog, _ -> dialog.dismiss() }
            .show()
    }
}

