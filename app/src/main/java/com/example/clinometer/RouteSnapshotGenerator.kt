package com.example.clinometer

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import java.io.File
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.math.*

/**
 * 🔥 SNAPSHOT Generator - създава bitmap snapshots на картите и ги записва на диск
 * Използва Snapshotter (headless) вместо MapView за стабилно генериране без UI
 * Използва чиста Web Mercator математика за прецизно рисуване на маршрута
 * Има Memory Cache (LruCache) за мигновено показване без лаг
 */
object RouteSnapshotGenerator {
    // Кеш в паметта - пази снимките, за да не се четат дори от диска всеки път
    private val memoryCache: LruCache<String, Bitmap> = LruCache(40)
    private val executor = Executors.newFixedThreadPool(4) // Бързо паралелно генериране
    private val runningTasks = Collections.synchronizedSet(mutableSetOf<Long>())

    // Смятаме мащаба на картата - стандарт за Web Mercator
    private const val TILE_SIZE = 256.0

    /**
     * Връща пътя към snapshot файла за даден race
     */
    fun getSnapshotFile(context: Context, raceId: Long): File {
        return File(context.filesDir, "snapshots/race_$raceId.png").apply {
            parentFile?.takeIf { !it.exists() }?.mkdirs()
        }
    }

    /**
     * Проверява дали snapshot вече съществува на диск
     */
    fun snapshotExists(context: Context, raceId: Long): Boolean = getSnapshotFile(context, raceId).exists()

    /**
     * Зарежда snapshot от диск
     */
    fun loadSnapshot(context: Context, raceId: Long): Bitmap? {
        val file = getSnapshotFile(context, raceId)
        return if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            null
        }
    }

    /**
     * Връща cached bitmap от memory cache (0ms, не блокира)
     * Използва се за бърза проверка преди асинхронно зареждане
     */
    fun getCachedBitmap(cacheKey: String): Bitmap? {
        return memoryCache.get(cacheKey)
    }

    /**
     * ТОВА Е ФУНКЦИЯТА ЗА АДАПТЕРА - ВИКАТЕ САМО НЕЯ
     * Проверява RAM кеша -> Диск -> Генерира (ако липсва)
     * ОПТИМИЗАЦИЯ: Ако points е празен списък, НЕ генерираме - само зареждаме от диска
     */
    fun displaySnapshot(imageView: ImageView, raceId: Long, points: List<RoutePoint>) {
        val startTime = System.currentTimeMillis()
        android.util.Log.d("RouteSnapshotGenerator", "🟦 displaySnapshot START: raceId=$raceId")
        
        val context = imageView.context
        val cacheKey = raceId.toString()

        // 1. Проверка в RAM кеша (Мигновено - 0ms)
        val cacheCheckStart = System.currentTimeMillis()
        val cachedBitmap = memoryCache.get(cacheKey)
        val cacheCheckTime = System.currentTimeMillis() - cacheCheckStart
        if (cachedBitmap != null) {
            android.util.Log.d("RouteSnapshotGenerator", "✅ Found in RAM cache: raceId=$raceId, time=${cacheCheckTime}ms")
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        // 2. Проверка на диска (КРИТИЧНО: Асинхронно, за да няма лаг на main thread!)
        // НЕ извикваме file.exists() на main thread - това блокира!
        val file = getSnapshotFile(context, raceId)
        val executorStart = System.currentTimeMillis()
        executor.execute {
            val executorWait = System.currentTimeMillis() - executorStart
            if (executorWait > 10) {
                android.util.Log.d("RouteSnapshotGenerator", "⚠️ Executor wait: ${executorWait}ms for raceId=$raceId")
            }
            
            val fileCheckStart = System.currentTimeMillis()
            val fileExists = file.exists()
            val fileCheckTime = System.currentTimeMillis() - fileCheckStart
            android.util.Log.d("RouteSnapshotGenerator", "📁 File exists check: $fileExists, time=${fileCheckTime}ms for raceId=$raceId")
            if (fileCheckTime > 10) {
                android.util.Log.d("RouteSnapshotGenerator", "⚠️ File exists took ${fileCheckTime}ms for raceId=$raceId")
            }
            
            Handler(Looper.getMainLooper()).post {
                if (fileExists) {
                    android.util.Log.d("RouteSnapshotGenerator", "📂 Loading from disk: raceId=$raceId")
                    loadFromDisk(file, cacheKey, imageView)
                } else {
                    // 3. Генериране (Само ако липсва И имаме route points)
                    if (points.isEmpty()) {
                        android.util.Log.d("RouteSnapshotGenerator", "❌ No points, no file: raceId=$raceId")
                        return@post
                    }

                    if (runningTasks.contains(raceId)) {
                        android.util.Log.d("RouteSnapshotGenerator", "⏸️ Already generating: raceId=$raceId")
                        return@post
                    }
                    
                    android.util.Log.d("RouteSnapshotGenerator", "🎨 Generating snapshot: raceId=$raceId")
                    imageView.setImageResource(android.R.color.transparent) // Placeholder
                    generate(context, raceId, points, imageView)
                }
            }
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        android.util.Log.d("RouteSnapshotGenerator", "🟦 displaySnapshot END (scheduled): raceId=$raceId, time=${totalTime}ms")
    }

    /**
     * Зарежда snapshot от диск асинхронно и го добавя в memory cache
     */
    private fun loadFromDisk(file: File, cacheKey: String, imageView: ImageView) {
        val loadStart = System.currentTimeMillis()
        android.util.Log.d("RouteSnapshotGenerator", "💾 loadFromDisk START: cacheKey=$cacheKey")
        
        executor.execute {
            try {
                val decodeStart = System.currentTimeMillis()
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                val decodeTime = System.currentTimeMillis() - decodeStart
                android.util.Log.d("RouteSnapshotGenerator", "🖼️ Bitmap decoded: ${bitmap != null}, time=${decodeTime}ms, size=${bitmap?.width}x${bitmap?.height} for cacheKey=$cacheKey")
                if (decodeTime > 50) {
                    android.util.Log.d("RouteSnapshotGenerator", "⚠️ Bitmap decode took ${decodeTime}ms for cacheKey=$cacheKey")
                }
                
                if (bitmap != null) {
                    memoryCache.put(cacheKey, bitmap)
                    val setImageStart = System.currentTimeMillis()
                    Handler(Looper.getMainLooper()).post {
                        // Проверяваме дали ImageView все още е валиден
                        if (imageView.parent != null) {
                            imageView.setImageBitmap(bitmap)
                            val setImageTime = System.currentTimeMillis() - setImageStart
                            android.util.Log.d("RouteSnapshotGenerator", "✅ Image set: time=${setImageTime}ms for cacheKey=$cacheKey")
                            if (setImageTime > 20) {
                                android.util.Log.d("RouteSnapshotGenerator", "⚠️ setImageBitmap took ${setImageTime}ms for cacheKey=$cacheKey")
                            }
                        } else {
                            android.util.Log.d("RouteSnapshotGenerator", "❌ ImageView detached: cacheKey=$cacheKey")
                        }
                    }
                } else {
                    android.util.Log.d("RouteSnapshotGenerator", "❌ Bitmap is null: cacheKey=$cacheKey")
                }
            } catch (e: Exception) {
                android.util.Log.e("RouteSnapshotGenerator", "❌ Error loading bitmap from disk: cacheKey=$cacheKey", e)
            }
            
            val loadTime = System.currentTimeMillis() - loadStart
            android.util.Log.d("RouteSnapshotGenerator", "💾 loadFromDisk END: cacheKey=$cacheKey, time=${loadTime}ms")
        }
    }

    /**
     * Генерира snapshot и го записва на диск + добавя в memory cache
     */
    private fun generate(context: Context, raceId: Long, points: List<RoutePoint>, imageView: ImageView) {
        runningTasks.add(raceId)
        
        Handler(Looper.getMainLooper()).post {
            val density = context.resources.displayMetrics.density
            val width = (context.resources.displayMetrics.widthPixels * 0.9).toInt()
            val height = (140 * density).toInt()

            val snapshotter = Snapshotter(context, MapSnapshotOptions.Builder()
                .size(Size(width.toFloat(), height.toFloat()))
                .pixelRatio(density)
                .build())

            val mapboxPoints = points.map { Point.fromLngLat(it.geoPoint.longitude, it.geoPoint.latitude) }
            
            val cameraOptions = try {
                snapshotter.cameraForCoordinates(
                    mapboxPoints, 
                    EdgeInsets(40.0 * density, 40.0 * density, 40.0 * density, 40.0 * density), 
                    null, 
                    null
                )
            } catch (e: Exception) {
                // Fallback: изчисляваме камерата ръчно
                android.util.Log.w("RouteSnapshotGenerator", "cameraForCoordinates failed, using manual calculation", e)
                calculateCameraOptions(mapboxPoints, width, height, 40.0 * density)
            }

            snapshotter.setCamera(cameraOptions)
            snapshotter.setStyleUri("mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l")

            snapshotter.start(
                overlayCallback = null,
                resultCallback = { bitmap, error ->
                    if (bitmap != null && error == null) {
                        val finalBitmap = drawRoute(bitmap, mapboxPoints, cameraOptions, width, height, density)
                        
                        // Запис и Кеш
                        executor.execute {
                            saveToFile(getSnapshotFile(context, raceId), finalBitmap)
                            memoryCache.put(raceId.toString(), finalBitmap)
                            Handler(Looper.getMainLooper()).post {
                                imageView.setImageBitmap(finalBitmap)
                                runningTasks.remove(raceId)
                            }
                        }
                    } else {
                        android.util.Log.e("RouteSnapshotGenerator", "Snapshot error: $error")
                        runningTasks.remove(raceId)
                    }
                    snapshotter.destroy()
                }
            )
        }
    }

    /**
     * Записва bitmap на диск
     */
    private fun saveToFile(file: File, bitmap: Bitmap) {
        try {
            file.outputStream().use { 
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) 
            }
        } catch (e: Exception) {
            android.util.Log.e("RouteSnapshotGenerator", "Error saving snapshot to file", e)
        }
    }

    /**
     * Рисува маршрута върху base bitmap
     * Използва чиста Web Mercator математика за прецизно изчисление
     */
    private fun drawRoute(
        base: Bitmap, 
        points: List<Point>, 
        camera: CameraOptions, 
        w: Int, 
        h: Int, 
        density: Float
    ): Bitmap {
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 122, 24) // #FF7A18
            strokeWidth = 5f * density
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        val zoom = camera.zoom ?: 0.0
        val center = camera.center ?: Point.fromLngLat(0.0, 0.0)
        val scale = TILE_SIZE * density * 2.0.pow(zoom)

        val path = Path()
        points.forEachIndexed { i, pt ->
            val x = (w / 2) + ((pt.longitude() + 180.0) / 360.0 * scale - (center.longitude() + 180.0) / 360.0 * scale).toFloat()
            val latRad = pt.latitude() * PI / 180.0
            val centerLatRad = center.latitude() * PI / 180.0
            val y = (h / 2) + ((0.5 - ln(tan(latRad) + 1.0/cos(latRad)) / (4.0 * PI)) * scale - (0.5 - ln(tan(centerLatRad) + 1.0/cos(centerLatRad)) / (4.0 * PI)) * scale).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, paint)
        
        // Рисуваме старт и финиш маркери
        if (points.isNotEmpty()) {
            try {
                val startX = (w / 2) + ((points.first().longitude() + 180.0) / 360.0 * scale - (center.longitude() + 180.0) / 360.0 * scale).toFloat()
                val startLatRad = points.first().latitude() * PI / 180.0
                val startY = (h / 2) + ((0.5 - ln(tan(startLatRad) + 1.0/cos(startLatRad)) / (4.0 * PI)) * scale - (0.5 - ln(tan(center.latitude() * PI / 180.0) + 1.0/cos(center.latitude() * PI / 180.0)) / (4.0 * PI)) * scale).toFloat()
                drawMarker(canvas, startX, startY, density, true)
            } catch (e: Exception) {
                android.util.Log.w("RouteSnapshotGenerator", "Error drawing start marker", e)
            }
            if (points.size > 1) {
                try {
                    val endX = (w / 2) + ((points.last().longitude() + 180.0) / 360.0 * scale - (center.longitude() + 180.0) / 360.0 * scale).toFloat()
                    val endLatRad = points.last().latitude() * PI / 180.0
                    val endY = (h / 2) + ((0.5 - ln(tan(endLatRad) + 1.0/cos(endLatRad)) / (4.0 * PI)) * scale - (0.5 - ln(tan(center.latitude() * PI / 180.0) + 1.0/cos(center.latitude() * PI / 180.0)) / (4.0 * PI)) * scale).toFloat()
                    drawMarker(canvas, endX, endY, density, false)
                } catch (e: Exception) {
                    android.util.Log.w("RouteSnapshotGenerator", "Error drawing finish marker", e)
                }
            }
        }
        
        return result
    }

    /**
     * Рисува маркер (start/finish flag) на дадена позиция
     */
    private fun drawMarker(canvas: Canvas, x: Float, y: Float, density: Float, isStart: Boolean) {
        val radius = 18f * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 122, 24)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f * density
        }

        canvas.drawCircle(x, y, radius, paint)
        canvas.drawCircle(x, y, radius, borderPaint)
    }

    /**
     * Fallback метод за изчисляване на камерата ръчно
     */
    private fun calculateCameraOptions(
        points: List<Point>,
        widthPx: Int,
        heightPx: Int,
        padding: Double
    ): CameraOptions {
        if (points.isEmpty()) {
            return CameraOptions.Builder()
                .center(Point.fromLngLat(0.0, 0.0))
                .zoom(10.0)
                .build()
        }

        if (points.size == 1) {
            return CameraOptions.Builder()
                .center(points[0])
                .zoom(15.0)
                .build()
        }

        // Изчисляваме bounding box
        var minLat = points[0].latitude()
        var maxLat = points[0].latitude()
        var minLon = points[0].longitude()
        var maxLon = points[0].longitude()

        for (point in points) {
            minLat = minOf(minLat, point.latitude())
            maxLat = maxOf(maxLat, point.latitude())
            minLon = minOf(minLon, point.longitude())
            maxLon = maxOf(maxLon, point.longitude())
        }

        val latDiff = maxLat - minLat
        val lonDiff = maxLon - minLon

        // Добавяме padding
        val latPadding = latDiff * padding / (heightPx - padding * 2)
        val lonPadding = lonDiff * padding / (widthPx - padding * 2)

        val centerLat = (minLat + maxLat) / 2.0
        val centerLon = (minLon + maxLon) / 2.0

        // Изчисляваме zoom
        val adjustedLatDiff = latDiff + latPadding * 2
        val adjustedLonDiff = lonDiff + lonPadding * 2

        val aspectRatio = widthPx.toDouble() / heightPx.toDouble()
        val routeAspectRatio = adjustedLonDiff / adjustedLatDiff

        val zoom = if (routeAspectRatio > aspectRatio) {
            log2(360.0 / adjustedLonDiff) - log2(aspectRatio) + 0.5 - 0.3
        } else {
            log2(360.0 / adjustedLatDiff) - 1.5 - 0.5
        }.coerceIn(3.0, 19.0)

        return CameraOptions.Builder()
            .center(Point.fromLngLat(centerLon, centerLat))
            .zoom(zoom)
            .build()
    }

    /**
     * Основна функция за генериране (за извикване след запис на race)
     * Извикай я веднага след края на записа на състезанието!
     */
    fun generateAndSaveSnapshot(
        context: Context,
        raceId: Long,
        routePoints: List<RoutePoint>,
        callback: ((Boolean) -> Unit)? = null
    ) {
        val file = getSnapshotFile(context, raceId)
        if (file.exists()) {
            callback?.invoke(true)
            return
        }

        if (routePoints.isEmpty()) {
            callback?.invoke(false)
            return
        }

        val mapboxPoints = routePoints.map { Point.fromLngLat(it.geoPoint.longitude, it.geoPoint.latitude) }
        if (mapboxPoints.size < 2) {
            callback?.invoke(false)
            return
        }

        // Генерираме snapshot в background thread
        executor.execute {
            Handler(Looper.getMainLooper()).post {
                val density = context.resources.displayMetrics.density
                val width = (context.resources.displayMetrics.widthPixels * 0.9).toInt()
                val height = (140 * density).toInt()

                val snapshotter = Snapshotter(context, MapSnapshotOptions.Builder()
                    .size(Size(width.toFloat(), height.toFloat()))
                    .pixelRatio(density)
                    .build())

                val cameraOptions = try {
                    snapshotter.cameraForCoordinates(
                        mapboxPoints,
                        EdgeInsets(40.0 * density, 40.0 * density, 40.0 * density, 40.0 * density),
                        null, null
                    )
                } catch (e: Exception) {
                    android.util.Log.w("RouteSnapshotGenerator", "cameraForCoordinates failed, using manual calculation", e)
                    calculateCameraOptions(mapboxPoints, width, height, 40.0 * density)
                }

                snapshotter.setCamera(cameraOptions)
                snapshotter.setStyleUri("mapbox://styles/djsookz/cmiyer8iu000101s84wqsav5l")

                snapshotter.start(
                    overlayCallback = null,
                    resultCallback = { bitmap, error ->
                        if (bitmap != null && error == null) {
                            val finalBitmap = drawRoute(bitmap, mapboxPoints, cameraOptions, width, height, density)
                            
                            executor.execute {
                                saveToFile(file, finalBitmap)
                                memoryCache.put(raceId.toString(), finalBitmap)
                                Handler(Looper.getMainLooper()).post {
                                    callback?.invoke(true)
                                }
                            }
                        } else {
                            android.util.Log.e("RouteSnapshotGenerator", "Snapshot error: $error")
                            callback?.invoke(false)
                        }
                        snapshotter.destroy()
                    }
                )
            }
        }
    }

    /**
     * Изтрива snapshot файл за конкретен race
     */
    fun deleteSnapshot(context: Context, raceId: Long) {
        val file = getSnapshotFile(context, raceId)
        if (file.exists()) {
            file.delete()
            memoryCache.remove(raceId.toString())
        }
    }
    
    /**
     * Изчиства всички snapshot файлове
     */
    fun clearAllSnapshots(context: Context) {
        val snapshotsDir = File(context.filesDir, "snapshots")
        if (snapshotsDir.exists()) {
            snapshotsDir.listFiles()?.forEach { it.delete() }
        }
        memoryCache.evictAll()
    }
}
