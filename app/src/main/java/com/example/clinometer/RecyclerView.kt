package com.example.clinometer

import android.app.AlertDialog
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Marker
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
    
    // ExecutorService за background thread за зареждане на route points
    private val routePointsExecutor = java.util.concurrent.Executors.newFixedThreadPool(2)
    
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
        val miniMapView: MapView = itemView.findViewById(R.id.miniMapView)
        val miniMapContainer: FrameLayout = itemView.findViewById(R.id.miniMapContainer)
        val layoutMapPlaceholder: LinearLayout = itemView.findViewById(R.id.layoutMapPlaceholder)
        val checkboxSelect: android.widget.CheckBox = itemView.findViewById(R.id.checkboxSelect)
        
        // Намираме CardView-а - той е родителя на itemView
        val cardView: androidx.cardview.widget.CardView? by lazy {
            var parent = itemView.parent
            while (parent != null && parent !is androidx.cardview.widget.CardView) {
                parent = (parent as? ViewGroup)?.parent
            }
            parent as? androidx.cardview.widget.CardView
        }
        
        var isMapboxMode = false
        var mapboxMiniMapView: MapboxMapView? = null
        var mapboxPolylineManager: PolylineAnnotationManager? = null
        var mapboxCircleAnnotationManager: CircleAnnotationManager? = null
        var startFlagOverlay: android.widget.ImageView? = null
        var finishFlagOverlay: android.widget.ImageView? = null
        var flagLayerIds = mutableListOf<String>() // Track layer IDs for cleanup
        var flagSourceIds = mutableListOf<String>() // Track source IDs for cleanup
        var isRouteLoaded = false  // Flag to prevent reloading route on scroll
        var loadedRaceId: Long? = null  // Track which race is currently loaded

        init {
            setupMiniMap()
        }
        
        /**
         * Зарежда Mapbox стил от JSON файл (res/raw/mapbox_style.json)
         * Това заобикаля проблемите с кеширане на стилове
         */
        private fun loadMapboxStyleFromJson(context: android.content.Context, onStyleLoaded: (Style) -> Unit) {
            // Използваме директно URL с timestamp за да форсираме презареждане всеки път
            // Това гарантира че винаги се зарежда най-новия стил от Mapbox Studio
            val styleUri = "mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l"
            Log.d("RaceAdapter", "🔄 Зареждаме стил от URL: $styleUri")
            mapboxMiniMapView?.mapboxMap?.loadStyleUri(styleUri) { style ->
                Log.d("RaceAdapter", "✅ Стилът е зареден успешно от URL!")
                onStyleLoaded(style)
            }
        }

        private fun setupMiniMap() {
            try {
                // Check map provider
                val mapProvider = MapProviderManager.getMapProvider(itemView.context)
                isMapboxMode = mapProvider == MapProviderManager.MapProvider.MAPBOX
                
                if (isMapboxMode) {
                    setupMapboxMiniMap()
                } else {
                    setupOsmdroidMiniMap()
                }
            } catch (e: Exception) {
                Log.w("RaceAdapter", "Грешка при setup на мини карта", e)
            }
        }
        
        private fun setupOsmdroidMiniMap() {
            try {
                miniMapView.visibility = View.VISIBLE
                miniMapView.setTileSource(TileSourceFactory.MAPNIK)
                miniMapView.setMultiTouchControls(false)
                miniMapView.setBuiltInZoomControls(false)
                miniMapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                miniMapView.isTilesScaledToDpi = true
                miniMapView.setDestroyMode(false)
                miniMapView.setTilesScaledToDpi(true)
                miniMapView.minZoomLevel = 3.0
                miniMapView.maxZoomLevel = 19.0
                miniMapView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                miniMapView.isClickable = false
                miniMapView.isFocusable = false
                miniMapView.isEnabled = false
                miniMapView.setHorizontalMapRepetitionEnabled(false)
                miniMapView.setVerticalMapRepetitionEnabled(false)
                setupBlocker(miniMapView.parent as? ViewGroup)
            } catch (e: Exception) {
                Log.w("RaceAdapter", "Грешка при setup на OSMDroid мини карта", e)
            }
        }
        
        private fun setupMapboxMiniMap() {
            try {
                // Hide OSMDroid map
                miniMapView.visibility = View.GONE
                
                // Remove OSMDroid MapView from container
                miniMapContainer.removeView(miniMapView)
                
                // Create Mapbox MapView
                mapboxMiniMapView = MapboxMapView(itemView.context)
                miniMapContainer.addView(mapboxMiniMapView)
                
                // Disable interactions
                mapboxMiniMapView?.isClickable = false
                mapboxMiniMapView?.isFocusable = false
                mapboxMiniMapView?.isEnabled = false
                
                // Disable scale bar and attribution logo
                val scaleBarPlugin = mapboxMiniMapView?.scalebar
                scaleBarPlugin?.enabled = false
                
                val attributionPlugin = mapboxMiniMapView?.attribution
                attributionPlugin?.enabled = false
                
                // Load custom style from JSON (no caching issues!)
                loadMapboxStyleFromJson(itemView.context) { style ->
                    // Create polyline manager
                    val annotationApi = mapboxMiniMapView?.annotations
                    mapboxPolylineManager = annotationApi?.createPolylineAnnotationManager()
                }
                
                setupBlocker(miniMapContainer)
            } catch (e: Exception) {
                Log.w("RaceAdapter", "Грешка при setup на Mapbox мини карта", e)
            }
        }
        
        private fun setupBlocker(parent: ViewGroup?) {
            if (parent is ViewGroup) {
                val blockerTag = "mini_map_blocker"
                var blocker = parent.findViewWithTag<View>(blockerTag)
                if (blocker == null) {
                    val layoutParams = if (parent is FrameLayout) {
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    } else {
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    blocker = View(itemView.context).apply {
                        tag = blockerTag
                        this.layoutParams = layoutParams
                        isClickable = true
                        isFocusable = true
                        setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_UP) {
                                itemView.performClick()
                            }
                            true
                        }
                    }
                    parent.addView(blocker)
                    parent.bringChildToFront(blocker)
                    parent.requestLayout()
                    parent.invalidate()
                } else {
                    parent.bringChildToFront(blocker)
                }
            }
        }

        fun loadMiniMap(race: Race) {
            try {
                // If this race is already loaded, just make it visible - don't reload
                if (isRouteLoaded && loadedRaceId == race.id) {
                    if (isMapboxMode) {
                        mapboxMiniMapView?.visibility = View.VISIBLE
                    } else {
                        miniMapView.visibility = View.VISIBLE
                    }
                    layoutMapPlaceholder.visibility = View.GONE
                    return
                }
                
                // Зареждаме route points в background thread за да не блокираме UI thread
                // Използваме ExecutorService за истински background processing
                routePointsExecutor.execute {
                    try {
                        // Проверяваме кеша първо
                        val cachedPoints = sampledRoutePointsCache[race.id]
                        val routePoints = if (cachedPoints != null) {
                            // Използваме кешираните точки
                            cachedPoints
                        } else {
                            // Зареждаме и sampling-ваме в background thread
                            val allRoutePoints = RouteStorage.loadRoutePoints(itemView.context, race.id)
                            
                            // За мини картите не ни трябват всички точки - използваме sampling за оптимизация
                            // Намаляваме maxPoints до 300 за още по-бързо зареждане на големи сесии
                            val sampled = sampleRoutePoints(allRoutePoints, maxPoints = 300)
                            
                            // Кешираме резултата
                            sampledRoutePointsCache[race.id] = sampled
                            
                            sampled
                        }
                        
                        // Връщаме се на main thread за UI операции
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            // Проверяваме дали holder все още е валиден
                            if (loadedRaceId != race.id || !isRouteLoaded) {
                                if (routePoints.isEmpty()) {
                                    if (isMapboxMode) {
                                        mapboxMiniMapView?.visibility = View.GONE
                                    } else {
                                        miniMapView.visibility = View.GONE
                                    }
                                    layoutMapPlaceholder.visibility = View.VISIBLE
                                    isRouteLoaded = false
                                    loadedRaceId = null
                                    return@post
                                }

                                layoutMapPlaceholder.visibility = View.GONE
                                
                                if (isMapboxMode) {
                                    loadMapboxMiniMap(routePoints, race.id)
                                } else {
                                    loadOsmdroidMiniMap(routePoints, race.id)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("RaceAdapter", "Error loading route points", e)
                    }
                }
            } catch (e: Exception) {
                Log.w("RaceAdapter", "Грешка при зареждане на мини карта", e)
            }
        }
        
        private fun loadMapboxMiniMap(routePoints: List<RoutePoint>, raceId: Long) {
            try {
                if (mapboxMiniMapView == null) return
                
                mapboxMiniMapView?.visibility = View.VISIBLE
                
                // Wait for style to load before drawing
                mapboxMiniMapView?.mapboxMap?.getStyle { style ->
                    // ВАЖНО: Изчистваме старите слоеве и източници преди да добавяме нови
                    try {
                        flagLayerIds.forEach { layerId ->
                            try {
                                style.removeStyleLayer(layerId)
                            } catch (e: Exception) {
                                // Layer already removed or doesn't exist
                            }
                        }
                        flagSourceIds.forEach { sourceId ->
                            try {
                                style.removeStyleSource(sourceId)
                            } catch (e: Exception) {
                                // Source already removed or doesn't exist
                            }
                        }
                        flagLayerIds.clear()
                        flagSourceIds.clear()
                    } catch (e: Exception) {
                        Log.e("RaceAdapter", "Error cleaning up old layers/sources", e)
                    }
                    
                    // Clear existing managers and recreate them
                    mapboxPolylineManager?.deleteAll()
                    mapboxCircleAnnotationManager?.deleteAll()
                    
                    val annotationApi = mapboxMiniMapView?.annotations
                    if (annotationApi != null) {
                        mapboxPolylineManager = annotationApi.createPolylineAnnotationManager()
                        mapboxCircleAnnotationManager = annotationApi.createCircleAnnotationManager()
                    }
                    
                    // Draw route with ORANGE color
                    val orangeColor = Color.rgb(255, 122, 24) // #FF7A18 - orange
                    val mapboxPoints = routePoints.map { 
                        MapboxPoint.fromLngLat(it.geoPoint.longitude, it.geoPoint.latitude) 
                    }
                    
                    val polylineOptions = PolylineAnnotationOptions()
                        .withPoints(mapboxPoints)
                        .withLineColor(String.format("#%06X", 0xFFFFFF and orangeColor))
                        .withLineWidth(3.0)
                    
                    mapboxPolylineManager?.create(polylineOptions)
                    
                    // Add markers with icons inside circles
                    try {
                        val position = bindingAdapterPosition
                        val uniqueId = "${position}_${System.nanoTime()}"
                        
                        // Create start marker icon (white flag in orange circle)
                        if (routePoints.isNotEmpty()) {
                            val startPoint = routePoints.first().geoPoint
                            
                            // Create bitmap with circle and flag icon - MUCH LARGER SIZE
                            val size = 120 // Още по-голям размер за по-добра видимост
                            val startBitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(startBitmap)
                            
                            // Draw ORANGE circle
                            val circlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.parseColor("#FF7A18") // Orange
                                setStyle(android.graphics.Paint.Style.FILL)
                            }
                            val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.WHITE
                                setStyle(android.graphics.Paint.Style.STROKE)
                                strokeWidth = 8f // По-дебела граница
                            }
                            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 6f, circlePaint)
                            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 6f, strokePaint)
                            
                            // Draw WHITE start flag icon inside - ПО-МАЛЪК
                            val flagIcon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_flag_start)
                            flagIcon?.let {
                                // Tint the icon WHITE
                                it.setTint(Color.WHITE)
                                val iconSize = (size * 0.45).toInt() // По-малък флаг за по-добър баланс
                                val left = (size - iconSize) / 2
                                val top = (size - iconSize) / 2
                                it.setBounds(left, top, left + iconSize, top + iconSize)
                                it.draw(canvas)
                            }
                            
                            // Add to style and create symbol
                            val startImageId = "start-marker-$uniqueId"
                            style.addImage(startImageId, startBitmap)
                            
                            val startFeature = Feature.fromGeometry(
                                MapboxPoint.fromLngLat(startPoint.longitude, startPoint.latitude)
                            )
                            val startSourceId = "start-marker-source-$uniqueId"
                            style.addSource(geoJsonSource(startSourceId) {
                                featureCollection(FeatureCollection.fromFeatures(listOf(startFeature)))
                            })
                            flagSourceIds.add(startSourceId)
                            
                            val startLayerId = "start-marker-layer-$uniqueId"
                            style.addLayer(symbolLayer(startLayerId, startSourceId) {
                                iconImage(startImageId)
                                iconSize(0.8) // По-голям размер за видимост
                                iconAllowOverlap(true)
                                iconIgnorePlacement(true)
                            })
                            flagLayerIds.add(startLayerId)
                        }
                        
                        // Create finish marker icon (white checkered flag in orange circle)
                        if (routePoints.size > 1) {
                            val endPoint = routePoints.last().geoPoint
                            
                            // Create bitmap with circle and flag icon - MUCH LARGER SIZE
                            val size = 120 // Още по-голям размер за по-добра видимост
                            val finishBitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(finishBitmap)
                            
                            // Draw ORANGE circle
                            val circlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.parseColor("#FF7A18") // Orange
                                setStyle(android.graphics.Paint.Style.FILL)
                            }
                            val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.WHITE
                                setStyle(android.graphics.Paint.Style.STROKE)
                                strokeWidth = 8f // По-дебела граница
                            }
                            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 6f, circlePaint)
                            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 6f, strokePaint)
                            
                            // Draw WHITE checkered flag icon inside - ПО-МАЛЪК
                            val flagIcon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_flag_finish)
                            flagIcon?.let {
                                // Tint the icon WHITE
                                it.setTint(Color.WHITE)
                                val iconSize = (size * 0.45).toInt() // По-малък флаг за по-добър баланс
                                val left = (size - iconSize) / 2
                                val top = (size - iconSize) / 2
                                it.setBounds(left, top, left + iconSize, top + iconSize)
                                it.draw(canvas)
                            }
                            
                            // Add to style and create symbol
                            val finishImageId = "finish-marker-$uniqueId"
                            style.addImage(finishImageId, finishBitmap)
                            
                            val finishFeature = Feature.fromGeometry(
                                MapboxPoint.fromLngLat(endPoint.longitude, endPoint.latitude)
                            )
                            val finishSourceId = "finish-marker-source-$uniqueId"
                            style.addSource(geoJsonSource(finishSourceId) {
                                featureCollection(FeatureCollection.fromFeatures(listOf(finishFeature)))
                            })
                            flagSourceIds.add(finishSourceId)
                            
                            val finishLayerId = "finish-marker-layer-$uniqueId"
                            style.addLayer(symbolLayer(finishLayerId, finishSourceId) {
                                iconImage(finishImageId)
                                iconSize(0.8) // По-голям размер за видимост
                                iconAllowOverlap(true)
                                iconIgnorePlacement(true)
                            })
                            flagLayerIds.add(finishLayerId)
                        }
                    } catch (e: Exception) {
                        Log.e("RaceAdapter", "Error adding markers", e)
                    }
                
                    // Calculate bounds and set camera with padding from map edges
                    if (routePoints.size >= 2) {
                        val allGeoPoints = routePoints.map { it.geoPoint }
                        val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPointsSafe(allGeoPoints)
                        
                        // Get map view dimensions
                        val mapWidth = mapboxMiniMapView?.width ?: 0
                        val mapHeight = mapboxMiniMapView?.height ?: 0
                        
                        if (mapWidth > 0 && mapHeight > 0) {
                            // Използваме СЪЩАТА логика като в MapActivity за идентичен зум
                            // Използваме 20dp padding за по-близък зум (като в MapActivity)
                            val density = itemView.context.resources.displayMetrics.density
                            val paddingPx = 20.0 * density
                            
                            // Calculate padding as percentage of map size
                            val paddingWidthRatio = (paddingPx * 2) / mapWidth  // Left + right padding
                            val paddingHeightRatio = (paddingPx * 2) / mapHeight  // Top + bottom padding
                            
                            // Calculate bounding box dimensions in degrees
                            val latDiff = boundingBox.latNorth - boundingBox.latSouth
                            val lonDiff = boundingBox.lonEast - boundingBox.lonWest
                            
                            // Calculate required padding in degrees to achieve 20dp padding on screen
                            val latPadding = latDiff * paddingHeightRatio / (1.0 - paddingHeightRatio)
                            val lonPadding = lonDiff * paddingWidthRatio / (1.0 - paddingWidthRatio)
                            
                            // Use the larger padding to ensure padding on all sides
                            val padding = maxOf(latPadding, lonPadding)
                            
                            // Create adjusted bounding box with calculated padding
                            val adjustedBox = org.osmdroid.util.BoundingBox(
                                boundingBox.latNorth + padding,
                                boundingBox.lonEast + padding,
                                boundingBox.latSouth - padding,
                                boundingBox.lonWest - padding
                            )
                            
                            // Calculate center
                            val centerLat = (adjustedBox.latSouth + adjustedBox.latNorth) / 2.0
                            val centerLon = (adjustedBox.lonWest + adjustedBox.lonEast) / 2.0
                            
                            // Calculate zoom to fit the adjusted bounding box in the map view
                            val adjustedLatDiff = adjustedBox.latNorth - adjustedBox.latSouth
                            val adjustedLonDiff = adjustedBox.lonEast - adjustedBox.lonWest
                            
                            // Calculate zoom based on which dimension is larger (width or height)
                            val aspectRatio = mapWidth.toDouble() / mapHeight.toDouble()
                            val routeAspectRatio = adjustedLonDiff / adjustedLatDiff
                            
                            // СЪЩАТА ФОРМУЛА КАТО В MapActivity, но с малко повече out-zoom за по-добра видимост на старт/финиш
                            // Determine which dimension constrains the zoom
                            // За height (север-юг) използваме много по-малък коефициент за много по-отдалечен зум
                            val zoom = if (routeAspectRatio > aspectRatio) {
                                // Route is wider - constrained by width (изток-запад) - оставяме както е
                                kotlin.math.log2(360.0 / adjustedLonDiff) - kotlin.math.log2(aspectRatio) + 0.5 - 0.3
                            } else {
                                // Route is taller - constrained by height (север-юг) - намаляваме зума много повече за много по-отдалечено
                                // Повече padding за север-юг за по-добра видимост на старт/финиш
                                kotlin.math.log2(360.0 / adjustedLatDiff) - 1.5 - 0.5
                            }.coerceIn(3.0, 19.0)
                            
                            mapboxMiniMapView?.mapboxMap?.setCamera(
                                CameraOptions.Builder()
                                    .center(MapboxPoint.fromLngLat(centerLon, centerLat))
                                    .zoom(zoom)
                                    .build()
                            )
                        } else {
                            // Fallback if map dimensions not available yet - use simple calculation
                            val latDiff = boundingBox.latNorth - boundingBox.latSouth
                            val lonDiff = boundingBox.lonEast - boundingBox.lonWest
                            val padding = kotlin.math.max(latDiff, lonDiff) * 0.15
                            
                            val adjustedBox = org.osmdroid.util.BoundingBox(
                                boundingBox.latNorth + padding,
                                boundingBox.lonEast + padding,
                                boundingBox.latSouth - padding,
                                boundingBox.lonWest - padding
                            )
                            
                            val centerLat = (adjustedBox.latSouth + adjustedBox.latNorth) / 2.0
                            val centerLon = (adjustedBox.lonWest + adjustedBox.lonEast) / 2.0
                            
                            val maxDiff = maxOf(adjustedBox.latNorth - adjustedBox.latSouth, adjustedBox.lonEast - adjustedBox.lonWest)
                            // Професионална формула за fallback: Адаптивен коефициент според размера на маршрута
                            val zoom = if (maxDiff > 0.0) {
                                val baseZoom = kotlin.math.log2(360.0 / maxDiff)
                                // Адаптивен коефициент: по-малък за големи маршрути, по-голям за малки
                                val adaptiveFactor = if (maxDiff > 1.0) {
                                    -1.3 // За много големи маршрути (над 1 градус)
                                } else if (maxDiff > 0.1) {
                                    -2.0 // За средни маршрути (0.1-1 градус)
                                } else {
                                    -2.8 // За малки маршрути (под 0.1 градус)
                                }
                                baseZoom + adaptiveFactor
                            } else {
                                15.0
                            }.coerceIn(3.0, 19.0)
                            
                            mapboxMiniMapView?.mapboxMap?.setCamera(
                                CameraOptions.Builder()
                                    .center(MapboxPoint.fromLngLat(centerLon, centerLat))
                                    .zoom(zoom)
                                    .build()
                            )
                        }
                    } else if (routePoints.size == 1) {
                        // Single point - center on it with default zoom (same as OSMDroid)
                        val point = routePoints[0].geoPoint
                        mapboxMiniMapView?.mapboxMap?.setCamera(
                            CameraOptions.Builder()
                                .center(MapboxPoint.fromLngLat(point.longitude, point.latitude))
                                .zoom(15.0)
                                .build()
                        )
                    }
                    
                    // Mark as loaded to prevent redrawing on scroll
                    isRouteLoaded = true
                    loadedRaceId = raceId
                }
            } catch (e: Exception) {
                Log.w("RaceAdapter", "Грешка при зареждане на Mapbox мини карта", e)
            }
        }
        
        private fun loadOsmdroidMiniMap(routePoints: List<RoutePoint>, raceId: Long) {
            try {
                // Don't reload if already loaded (prevents redrawing on scroll)
                if (isRouteLoaded && miniMapView.overlays.isNotEmpty()) return
                
                miniMapView.visibility = View.VISIBLE
                miniMapView.overlays.clear()

                val polyline = Polyline().apply {
                    setPoints(routePoints.map { it.geoPoint })
                    color = Color.rgb(0, 25, 255) // Keep blue for OSMDroid
                    outlinePaint.strokeWidth = 3f
                    outlinePaint.alpha = 255
                }
                miniMapView.overlays.add(polyline)
                
                // Add start flag marker (green)
                if (routePoints.isNotEmpty()) {
                    val startMarker = Marker(miniMapView).apply {
                        position = routePoints.first().geoPoint
                        icon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_flag_start)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    miniMapView.overlays.add(startMarker)
                }
                
                // Add finish flag marker (checkered)
                if (routePoints.size > 1) {
                    val finishMarker = Marker(miniMapView).apply {
                        position = routePoints.last().geoPoint
                        icon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_flag_finish)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    miniMapView.overlays.add(finishMarker)
                }

                val allGeoPoints = routePoints.map { it.geoPoint }

                if (allGeoPoints.size >= 2) {
                    val boundingBox = org.osmdroid.util.BoundingBox.fromGeoPointsSafe(allGeoPoints)

                    val latDiff = boundingBox.latNorth - boundingBox.latSouth
                    val lonDiff = boundingBox.lonEast - boundingBox.lonWest
                    val padding = kotlin.math.max(latDiff, lonDiff) * 0.15

                    val adjustedBox = org.osmdroid.util.BoundingBox(
                        boundingBox.latNorth + padding,
                        boundingBox.lonEast + padding,
                        boundingBox.latSouth - padding,
                        boundingBox.lonWest - padding
                    )

                    miniMapView.post {
                        miniMapView.zoomToBoundingBox(adjustedBox, false)
                        miniMapView.invalidate()
                    }
                } else {
                    val point = allGeoPoints[0]
                    miniMapView.controller.setCenter(point)
                    miniMapView.controller.setZoom(15.0)
                }
                
                // Mark as loaded to prevent redrawing on scroll
                isRouteLoaded = true
                loadedRaceId = raceId

            } catch (e: Exception) {
                Log.e("RaceAdapter", "Грешка при зареждане на мини карта", e)
                miniMapView.visibility = View.GONE
                layoutMapPlaceholder.visibility = View.VISIBLE
            }
        }

        private fun createMarkerIcon(color: Int, text: String): android.graphics.drawable.Drawable {
            return object : android.graphics.drawable.Drawable() {
                override fun draw(canvas: android.graphics.Canvas) {
                    val paint = android.graphics.Paint().apply {
                        this.color = color
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.FILL
                    }

                    val strokePaint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.WHITE
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3f
                    }

                    val textPaint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.WHITE
                        textSize = 18f
                        isAntiAlias = true
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    val radius = 16f
                    val centerX = bounds.exactCenterX()
                    val centerY = bounds.exactCenterY()

                    canvas.drawCircle(centerX, centerY, radius, paint)
                    canvas.drawCircle(centerX, centerY, radius, strokePaint)
                    val textY = centerY + (textPaint.textSize / 3)
                    canvas.drawText(text, centerX, textY, textPaint)
                }

                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

                override fun getIntrinsicWidth(): Int = 32
                override fun getIntrinsicHeight(): Int = 32
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RaceViewHolder {
        try {
            Configuration.getInstance().load(
                parent.context,
                PreferenceManager.getDefaultSharedPreferences(parent.context)
            )
        } catch (e: Exception) {
            Log.w("RaceAdapter", "Грешка при инициализация на OSMDroid", e)
        }

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_race, parent, false)
        return RaceViewHolder(view)
    }

    override fun onBindViewHolder(holder: RaceViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }
    
    override fun onBindViewHolder(holder: RaceViewHolder, position: Int, payloads: MutableList<Any>) {
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
        
        // Нормално обновяване на целия holder
        val race = races[position]

        fun formatRelativeDate(timestamp: Long): String {
            val ctx = holder.itemView.context

            val nowMidnight = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val thenMidnight = Calendar.getInstance().apply {
                timeInMillis = timestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffMillis = nowMidnight.timeInMillis - thenMidnight.timeInMillis
            val days = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

            return when {
                days < 0 -> DateFormat.format("dd.MM.yyyy", Date(timestamp)).toString()
                days == 0 -> ctx.getString(R.string.session_today)
                days == 1 -> ctx.getString(R.string.session_yesterday)
                else -> ctx.resources.getQuantityString(R.plurals.session_days, days, days)
            }
        }

        holder.tvTitle.text = race.name
            ?: holder.itemView.context.getString(R.string.session_title, position + 1)
        holder.dateTextView.text = formatRelativeDate(race.absoluteTimestamp)
        
        // Показваме разстоянието (distance вече е в километри от MainActivity)
        holder.tvDistance.text = String.format("%.2f km", race.distance)
        
        // Показваме времетраенето
        holder.tvDuration.text = formatTime(race.duration)
        
        // Зареждаме профила от кеша (оптимизация за производителност)
        val context = holder.itemView.context
        if (profilesCache == null || profilesCacheContext != context) {
            profilesCache = ProfileStorage.loadProfiles(context)
            profilesCacheContext = context
        }
        val profile = profilesCache?.find { it.id == race.profileId }
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
                    // Зареждаме асинхронно
                    val imageFile = java.io.File(holder.itemView.context.getExternalFilesDir(null), imagePath)
                    if (imageFile.exists()) {
                        // Зареждаме в background thread
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            try {
                                val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                                profileImageCache[imagePath] = bitmap
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
                            } catch (e: Exception) {
                                Log.w("RaceAdapter", "Error loading profile image", e)
                                profileImageCache[imagePath] = null
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
                        profileImageCache[imagePath] = null
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

        // Lazy loading на картите - зареждаме само ако holder е видим И картата все още не е заредена за този race
        // ВАЖНО: Не извикваме loadMiniMap ако картата вече е заредена за този race - това предотвратява презареждане при скролване
        if (holder.isRouteLoaded && holder.loadedRaceId == race.id) {
            // Картата вече е заредена за този race - само я показваме (не презареждаме!)
            if (holder.isMapboxMode && holder.mapboxMiniMapView != null) {
                holder.mapboxMiniMapView?.visibility = View.VISIBLE
                holder.layoutMapPlaceholder.visibility = View.GONE
            } else if (!holder.isMapboxMode) {
                holder.miniMapView.visibility = View.VISIBLE
                holder.layoutMapPlaceholder.visibility = View.GONE
            }
        } else {
            // Картата все още не е заредена - зареждаме я асинхронно
            holder.itemView.post {
                // Проверяваме отново дали holder все още е валиден и видим (race може да е променен при скролване)
                if (holder.adapterPosition != RecyclerView.NO_POSITION && 
                    races.getOrNull(holder.adapterPosition)?.id == race.id &&
                    holder.itemView.parent != null) {
                    // Двойна проверка - може междувременно да се е заредила
                    if (!holder.isRouteLoaded || holder.loadedRaceId != race.id) {
                        holder.loadMiniMap(race)
                    }
                }
            }
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
            holder.itemView.setOnClickListener {
                toggleSelection(race.id)
            }
            holder.miniMapView.setOnClickListener {
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
            holder.itemView.setOnClickListener { onItemClick(race) }
            holder.miniMapView.setOnClickListener { onItemClick(race) }
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

        // Бутон за редактиране
        holder.btnEdit.setOnClickListener {
            showRenameDialog(holder, race)
        }

        // Бутон за триене
        holder.btnDelete.setOnClickListener {
            onDeleteClick(race)
        }
    }

    override fun getItemCount(): Int = races.size

    override fun onViewRecycled(holder: RaceViewHolder) {
        super.onViewRecycled(holder)
        try {
            // НЕ reset-ваме isRouteLoaded и НЕ изчистваме данните!
            // Това позволява картите да останат заредени и видими веднага
            // Данните ще се изчистят само когато се зареди различен race в loadMiniMap
            
            // НЕ правим нищо тук - оставяме картите заредени
            // holder.isRouteLoaded = false  // REMOVED - това караше картата да се презарежда
            
            // НЕ изчистваме layers/sources - оставяме ги там за моментално показване
            // Данните ще се изчистят в loadMapboxMiniMap преди да се заредят нови
            
        } catch (e: Exception) {
            Log.w("RaceAdapter", "Грешка при изчистване на overlay-и", e)
        }
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
