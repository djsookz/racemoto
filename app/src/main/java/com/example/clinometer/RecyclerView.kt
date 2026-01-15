package com.example.clinometer

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.text.InputType
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.settings.MapProviderManager
import com.mapbox.maps.MapView as MapboxMapView
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.geojson.Point as MapboxPoint
import com.mapbox.maps.extension.style.expressions.dsl.generated.literal
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Geometry
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.plugin.attribution.attribution
import androidx.core.content.ContextCompat
import com.example.clinometer.data.ProfileStorage
import java.util.Calendar
import java.util.Date

class RaceAdapter(
    races: MutableList<Race>,
    private val onItemClick: (Race) -> Unit,
    private val onDeleteClick: (Race) -> Unit,
    private val onRename: (Race, String) -> Unit,
    private val onFavoriteToggle: (Race) -> Unit,
    private val onMultiDeleteClick: (List<Race>) -> Unit = {}
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

    // Кеш за профили - зареждаме веднъж и използваме навсякъде
    private var profilesCache: List<Profile>? = null
    private var profilesCacheContext: android.content.Context? = null

    // Кеш за bitmap-и на профилни снимки
    private val profileImageCache = mutableMapOf<String, android.graphics.Bitmap?>()

    // Кеш за sampled route points - избягваме повторно sampling на същите сесии
    private val sampledRoutePointsCache = mutableMapOf<Long, List<RoutePoint>>()

    // Кеш за форматирани дати - избягваме създаване на Calendar обекти при всеки bind
    private val dateFormatCache = mutableMapOf<Long, String>()

    // ExecutorService за background thread за зареждане на route points
    // ОПТИМИЗАЦИЯ: Увеличаваме thread pool за по-бързо паралелно зареждане
    private val routePointsExecutor = java.util.concurrent.Executors.newFixedThreadPool(4)

    fun updateRaces(newRaces: List<Race>) {
        races.clear()
        // Списъкът вече е сортиран в RacesActivity, просто го добавяме
        races.addAll(newRaces)
        notifyDataSetChanged()
    }

    /**
     * Обновява само favorite статуса на конкретен item без да презарежда всички view holders.
     * Това е много по-бързо от notifyDataSetChanged() защото не презарежда картите.
     */
    fun updateFavoriteStatus(raceId: Long, isFavorite: Boolean) {
        val position = races.indexOfFirst { it.id == raceId }
        if (position >= 0) {
            races[position].isFavorite = isFavorite
            races[position].favoriteTimestamp = if (isFavorite) System.currentTimeMillis() else null
            // Използваме payload за да кажем на onBindViewHolder да обнови само favorite иконата
            // Това е много по-бързо защото не презарежда картите
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
        var loadSnapshotRunnable: Runnable? = null  // Runnable за отменяне на заявките при скрол

        /**
         * 🔥 SNAPSHOT подход: Използва displaySnapshot с вградена memory cache
         * Проверява RAM кеша -> Диск -> Генерира (ако липсва)
         * ОПТИМИЗАЦИЯ: Всички проверки са асинхронни, не блокират UI нишката!
         * RecyclerView НИКОГА не трябва да знае за Mapbox
         */
        fun loadMiniMapSnapshot(race: Race) {
            val loadStart = System.currentTimeMillis()
            Log.d("RaceAdapter", "🔴 loadMiniMapSnapshot START: raceId=${race.id}")
            
            // Проверяваме дали snapshot вече е зареден за този race
            if (isRouteLoaded && loadedRaceId == race.id) {
                Log.d("RaceAdapter", "✅ Already loaded in holder: raceId=${race.id}")
                miniMapSnapshot.visibility = View.VISIBLE
                layoutMapPlaceholder.visibility = View.GONE
                return
            }

            // КРИТИЧНО: Проверяваме дали item-ът все още е видим преди да започнем зареждане
            if (!itemView.isAttachedToWindow || itemView.parent == null) {
                Log.d("RaceAdapter", "❌ Item not attached: raceId=${race.id}")
                return // Item-ът не е видим - не зареждаме
            }

            // КРИТИЧНО: Първо проверяваме memory cache (0ms, не блокира)
            // След това проверяваме файла АСИНХРОННО (не блокира UI)
            // И накрая зареждаме route points само ако snapshot липсва
            val executorStart = System.currentTimeMillis()
            routePointsExecutor.execute {
                val executorTime = System.currentTimeMillis() - executorStart
                if (executorTime > 10) {
                    Log.d("RaceAdapter", "⚠️ Executor queue wait: ${executorTime}ms for raceId=${race.id}")
                }
                
                val threadStart = System.currentTimeMillis()
                Log.d("RaceAdapter", "🟣 Background thread START: raceId=${race.id}")
                
                // Двойна проверка - може item-ът да е бил откачен междувременно
                if (!itemView.isAttachedToWindow) {
                    Log.d("RaceAdapter", "❌ Item detached in thread: raceId=${race.id}")
                    return@execute
                }
                try {
                    // Проверяваме дали snapshot файл съществува (асинхронно)
                    val fileCheckStart = System.currentTimeMillis()
                    val snapshotFile = RouteSnapshotGenerator.getSnapshotFile(itemView.context, race.id)
                    val fileExists = snapshotFile.exists()
                    val fileCheckTime = System.currentTimeMillis() - fileCheckStart
                    if (fileCheckTime > 10) {
                        Log.d("RaceAdapter", "⚠️ File exists check took ${fileCheckTime}ms for raceId=${race.id}")
                    }
                    Log.d("RaceAdapter", "📁 File exists: $fileExists for raceId=${race.id}")
                    
                    if (fileExists) {
                        // Snapshot съществува - използваме displaySnapshot с празен списък точки
                        // displaySnapshot ще зареди от диска или memory cache
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            miniMapSnapshot.visibility = View.VISIBLE
                            layoutMapPlaceholder.visibility = View.GONE
                            // Подаваме празен списък - displaySnapshot няма да генерира, ще зареди от диска
                            RouteSnapshotGenerator.displaySnapshot(miniMapSnapshot, race.id, emptyList())
                            isRouteLoaded = true
                            loadedRaceId = race.id
                        }
                    } else {
                        // Snapshot НЕ съществува - зареждаме route points за генериране
                        val routeLoadStart = System.currentTimeMillis()
                        Log.d("RaceAdapter", "📂 Loading route points: raceId=${race.id}")
                        val allRoutePoints = RouteStorage.loadRoutePoints(itemView.context, race.id)
                        val routeLoadTime = System.currentTimeMillis() - routeLoadStart
                        Log.d("RaceAdapter", "📂 Route points loaded: ${allRoutePoints.size} points in ${routeLoadTime}ms for raceId=${race.id}")
                        if (routeLoadTime > 100) {
                            Log.d("RaceAdapter", "⚠️ Route load took ${routeLoadTime}ms for raceId=${race.id}")
                        }
                        if (allRoutePoints.isNotEmpty()) {
                            val sampled = sampleRoutePoints(allRoutePoints, maxPoints = 300)
                            
                            // Използваме displaySnapshot - тя автоматично ще генерира snapshot
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                miniMapSnapshot.visibility = View.VISIBLE
                                layoutMapPlaceholder.visibility = View.GONE
                                RouteSnapshotGenerator.displaySnapshot(miniMapSnapshot, race.id, sampled)
                                isRouteLoaded = true
                                loadedRaceId = race.id
                            }
                        } else {
                            // Няма route points - показваме placeholder
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                miniMapSnapshot.visibility = View.GONE
                                layoutMapPlaceholder.visibility = View.VISIBLE
                                isRouteLoaded = false
                                loadedRaceId = null
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RaceAdapter", "❌ Error loading snapshot: raceId=${race.id}", e)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        miniMapSnapshot.visibility = View.GONE
                        layoutMapPlaceholder.visibility = View.VISIBLE
                    }
                }
                
                val threadTime = System.currentTimeMillis() - threadStart
                Log.d("RaceAdapter", "🟣 Background thread END: raceId=${race.id}, time=${threadTime}ms")
            }
            
            val loadTime = System.currentTimeMillis() - loadStart
            Log.d("RaceAdapter", "🔴 loadMiniMapSnapshot END (scheduled): raceId=${race.id}, time=${loadTime}ms")
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
        val race = races[position]
        Log.d("RaceAdapter", "🔵 onBindViewHolder START: position=$position, raceId=${race.id}, payloads=$payloads")
        
        // Ако има payload "favorite_changed", обновяваме само favorite иконата
        if (payloads.isNotEmpty() && payloads.contains("favorite_changed")) {
            val race = races[position]
            // Обновяваме само favorite иконата - НЕ презареждаме картата!
            if (race.isFavorite) {
                holder.btnFavorite.setImageResource(R.drawable.ic_favorite)
                holder.btnFavorite.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF7A18"))
            } else {
                holder.btnFavorite.setImageResource(R.drawable.ic_favorite_border)
                holder.btnFavorite.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            }
            return // Излизаме рано - не обновяваме нищо друго
        }

        // Ако има payload "selection_changed", обновяваме само checkbox и фона - НЕ презареждаме картата!
        if (payloads.isNotEmpty() && payloads.contains("selection_changed")) {
            val race = races[position]
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
            return // Излизаме рано - не обновяваме нищо друго, включително картите!
        }

        // Нормално обновяване на целия holder
        val bindStartTime = System.currentTimeMillis()

        // ОПТИМИЗАЦИЯ: Кешираме форматираните дати за да не създаваме Calendar обекти при всеки bind
        val dateCacheKey = race.absoluteTimestamp
        val dateFormatStart = System.currentTimeMillis()
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
        val dateFormatTime = System.currentTimeMillis() - dateFormatStart
        if (dateFormatTime > 5) {
            Log.d("RaceAdapter", "⚠️ Date format took ${dateFormatTime}ms for raceId=${race.id}")
        }

        val textSetStart = System.currentTimeMillis()
        holder.tvTitle.text = race.name
            ?: holder.itemView.context.getString(R.string.session_title, position + 1)
        holder.dateTextView.text = formattedDate

        // Показваме разстоянието (distance вече е в километри от MainActivity)
        holder.tvDistance.text = String.format("%.2f km", race.distance)

        // Показваме времетраенето
        holder.tvDuration.text = formatTime(race.duration)

        // Зареждаме профила от кеша (оптимизация за производителност)
        // ОПТИМИЗАЦИЯ: Зареждаме профилите веднъж при първо отваряне, не при всеки bind
        val profileLoadStart = System.currentTimeMillis()
        val context = holder.itemView.context
        if (profilesCache == null || profilesCacheContext != context) {
            // Зареждаме синхронно само веднъж (бързо, защото е малък файл)
            // Ако е бавно, може да се направи асинхронно, но за сега е по-просто така
            val loadStart = System.currentTimeMillis()
            profilesCache = ProfileStorage.loadProfiles(context)
            val loadTime = System.currentTimeMillis() - loadStart
            if (loadTime > 10) {
                Log.d("RaceAdapter", "⚠️ ProfileStorage.loadProfiles took ${loadTime}ms")
            }
            profilesCacheContext = context
        }
        val findStart = System.currentTimeMillis()
        val profile = profilesCache?.find { it.id == race.profileId }
        val findTime = System.currentTimeMillis() - findStart
        if (findTime > 5) {
            Log.d("RaceAdapter", "⚠️ Profile find took ${findTime}ms for raceId=${race.id}")
        }
        val profileLoadTime = System.currentTimeMillis() - profileLoadStart
        if (profileLoadTime > 10) {
            Log.d("RaceAdapter", "⚠️ Profile loading total took ${profileLoadTime}ms for raceId=${race.id}")
        }
        profile?.let { prof ->
            // Показваме името на профила
            holder.tvVehicleName.text = prof.name ?: ""

            // Зареждаме снимката на профила (с кеширане)
            if (!prof.imagePath.isNullOrEmpty()) {
                val imagePath = prof.imagePath ?: ""
                val cachedBitmap = profileImageCache[imagePath]

                if (cachedBitmap != null) {
                    // Използваме кеширания bitmap
                    holder.ivProfileImage.setImageBitmap(cachedBitmap)
                    holder.ivProfileImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    holder.ivProfileImage.clipToOutline = true
                    holder.ivProfileImage.outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setOval(0, 0, view.width, view.height)
                        }
                    }
                } else {
                    // Зареждаме асинхронно - КРИТИЧНО: проверката на файла е в background thread!
                    val imageFile = java.io.File(holder.itemView.context.getExternalFilesDir(null), imagePath)
                    // Показваме placeholder докато се зарежда
                    val iconRes = when (prof.vehicleType) {
                        Profile.VehicleType.CAR -> R.drawable.ic_car
                        Profile.VehicleType.MOTORCYCLE -> R.drawable.ic_motorcycle
                    }
                    holder.ivProfileImage.setImageResource(iconRes)
                    holder.ivProfileImage.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    holder.ivProfileImage.clipToOutline = false
                    
                    // Проверяваме файла и зареждаме bitmap в background thread
                    routePointsExecutor.execute {
                        try {
                            if (imageFile.exists()) {
                                val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                                profileImageCache[imagePath] = bitmap
                                // Обновяваме UI на главната нишка
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    // Проверяваме дали holder все още е валиден
                                    if (holder.adapterPosition != RecyclerView.NO_POSITION &&
                                        races.getOrNull(holder.adapterPosition)?.id == race.id) {
                                        holder.ivProfileImage.setImageBitmap(bitmap)
                                        holder.ivProfileImage.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                                        holder.ivProfileImage.clipToOutline = true
                                        holder.ivProfileImage.outlineProvider = object : ViewOutlineProvider() {
                                            override fun getOutline(view: View, outline: Outline) {
                                                outline.setOval(0, 0, view.width, view.height)
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Файлът не съществува - вече сме показали иконката
                                profileImageCache[imagePath] = null
                            }
                        } catch (e: Exception) {
                            Log.w("RaceAdapter", "Error loading profile image", e)
                            profileImageCache[imagePath] = null
                        }
                    }
                }
            } else {
                // Няма снимка, показваме иконка
                val iconRes = when (prof.vehicleType) {
                    Profile.VehicleType.CAR -> R.drawable.ic_car
                    Profile.VehicleType.MOTORCYCLE -> R.drawable.ic_motorcycle
                }
                holder.ivProfileImage.setImageResource(iconRes)
                holder.ivProfileImage.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                holder.ivProfileImage.clipToOutline = false
            }
        } ?: run {
            // Няма профил, скриваме
            holder.tvVehicleName.text = ""
            holder.ivProfileImage.setImageResource(R.drawable.ic_car)
        }

        // 🔥 SNAPSHOT подход: Зареждаме snapshot само ако не е вече зареден
        // КРИТИЧНА ОПТИМИЗАЦИЯ: Първо проверяваме memory cache синхронно (0ms)
        // Ако няма в memory cache, НЕ зареждаме веднага - чакаме item-ът да е стабилен
        if (holder.isRouteLoaded && holder.loadedRaceId == race.id) {
            // Snapshot вече е зареден за този race - само го показваме (не презареждаме!)
            holder.miniMapSnapshot.visibility = View.VISIBLE
            holder.layoutMapPlaceholder.visibility = View.GONE
        } else {
            // Първо проверяваме memory cache синхронно (0ms, не блокира)
            val cacheKey = race.id.toString()
            val cachedBitmap = RouteSnapshotGenerator.getCachedBitmap(cacheKey)
            if (cachedBitmap != null) {
                // Намерен в memory cache - показваме веднага (0ms)
                holder.miniMapSnapshot.setImageBitmap(cachedBitmap)
                holder.miniMapSnapshot.visibility = View.VISIBLE
                holder.layoutMapPlaceholder.visibility = View.GONE
                holder.isRouteLoaded = true
                holder.loadedRaceId = race.id
            } else {
                // Няма в memory cache - показваме placeholder и зареждаме LAZY
                // КРИТИЧНО: НЕ зареждаме веднага - чакаме item-ът да е стабилен (след layout)
                holder.miniMapSnapshot.visibility = View.GONE
                holder.layoutMapPlaceholder.visibility = View.VISIBLE
                
                // КРИТИЧНО: Отменяме старата заявка ако има такава (при скрол)
                holder.loadSnapshotRunnable?.let { oldRunnable ->
                    holder.itemView.removeCallbacks(oldRunnable)
                }
                
                // Зареждаме с малко забавяне
                val delay = if (position < 5) 50L else 200L
                val runnable = Runnable {
                    val runnableStart = System.currentTimeMillis()
                    Log.d("RaceAdapter", "🟢 Runnable START: position=$position, raceId=${race.id}")
                    // Проверяваме отново дали holder все още е валиден и видим
                    // КРИТИЧНО: Проверяваме дали position все още е същото (не е скролнато)
                    if (holder.adapterPosition != RecyclerView.NO_POSITION &&
                        holder.adapterPosition == position &&
                        races.getOrNull(holder.adapterPosition)?.id == race.id &&
                        holder.itemView.parent != null &&
                        holder.itemView.isAttachedToWindow) {
                        // Двойна проверка - може междувременно да се е заредил
                        if (!holder.isRouteLoaded || holder.loadedRaceId != race.id) {
                            Log.d("RaceAdapter", "🟡 Calling loadMiniMapSnapshot: position=$position, raceId=${race.id}")
                            holder.loadMiniMapSnapshot(race)
                        } else {
                            Log.d("RaceAdapter", "✅ Already loaded: position=$position, raceId=${race.id}")
                        }
                    } else {
                        Log.d("RaceAdapter", "❌ Holder invalid: position=$position, adapterPosition=${holder.adapterPosition}, raceId=${race.id}")
                    }
                    val runnableTime = System.currentTimeMillis() - runnableStart
                    if (runnableTime > 10) {
                        Log.d("RaceAdapter", "⚠️ Runnable took ${runnableTime}ms for raceId=${race.id}")
                    }
                    // Изчистваме runnable след изпълнение
                    holder.loadSnapshotRunnable = null
                }
                holder.loadSnapshotRunnable = runnable
                holder.itemView.postDelayed(runnable, delay)
                Log.d("RaceAdapter", "📅 Scheduled snapshot load: position=$position, raceId=${race.id}, delay=${delay}ms")
            }
        }

        // 🔒 КРИТИЧНО: Забраняваме click на децата - само CardView реагира
        holder.miniMapContainer.isClickable = false
        holder.miniMapContainer.isFocusable = false
        
        // Touch blocker над картата - клик върху него = клик върху CardView
        val mapTouchBlocker = holder.itemView.findViewById<View>(R.id.mapTouchBlocker)
        mapTouchBlocker?.setOnClickListener {
            holder.itemView.performClick()
        }

        // Показваме/скриваме checkbox според режима на избор
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
            Log.d("RaceAdapter", "⚠️ onBindViewHolder TOTAL took ${totalTime}ms for position=$position, raceId=${race.id}")
        } else {
            Log.d("RaceAdapter", "✅ onBindViewHolder END: position=$position, raceId=${race.id}, time=${totalTime}ms")
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
