package com.revix.app.reports.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import androidx.core.content.ContextCompat
import com.revix.app.R
import com.revix.app.reports.data.PoliceReport
import com.revix.app.reports.data.ReportType
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.AnnotationPlugin
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.delegates.listeners.OnCameraChangeListener

/**
 * Управлява показването на доклади на Mapbox картата
 * Добавя/маха markers, управлява стилове и click events
 */
class ReportsMapManager(
    private val mapView: MapView
) {
    private var annotationManager: PointAnnotationManager? = null
    private val reportAnnotations = mutableMapOf<String, PointAnnotation>()
    private val annotationToReportId = mutableMapOf<String, String>() // Mapping annotation ID -> report ID
    
    // Callback за click на marker
    private var onReportClickListener: ((String) -> Unit)? = null

    /** Route preview: compact while zoomed out, normal size when zoomed in. */
    private var routePreviewAdaptive = false
    private var usingCompactScale = false
    private var cameraListenerAttached = false
    private val cameraChangeListener = OnCameraChangeListener {
        if (routePreviewAdaptive) {
            refreshScaleForCurrentZoom()
        }
    }
    
    companion object {
        private const val TAG = "ReportsMapManager"
        private const val MARKER_SIZE_PX = 128
        private const val NORMAL_ICON_SIZE = 1.0
        private const val PREVIEW_ICON_SIZE = 0.55
        private const val NORMAL_TEXT_SIZE = 12.0
        private const val PREVIEW_TEXT_SIZE = 9.0
        /** At/above this zoom in route preview, markers use full size. */
        private const val PREVIEW_FULL_SIZE_ZOOM = 11.0
    }
    
    /**
     * Set listener за click на report marker
     */
    fun setOnReportClickListener(listener: (String) -> Unit) {
        onReportClickListener = listener
    }
    
    /**
     * Инициализира annotation manager за markers
     */
    fun initialize() {
        try {
            val annotationApi: AnnotationPlugin = mapView.annotations
            annotationManager = annotationApi.createPointAnnotationManager()
            
            // Setup click listener
            annotationManager?.addClickListener { annotation ->
                val reportId = annotationToReportId[annotation.id]
                if (reportId != null) {
                    onReportClickListener?.invoke(reportId)
                    true
                } else {
                    false
                }
            }
            
            Log.d(TAG, "ReportsMapManager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize annotation manager", e)
        }
    }
    
    /**
     * Обновява всички доклади на картата.
     * @param routePreviewAdaptive when true, markers shrink on overview zoom and grow when zoomed in
     */
    fun updateReports(reports: List<PoliceReport>, routePreviewAdaptive: Boolean = false) {
        try {
            val manager = annotationManager ?: return
            setRoutePreviewAdaptive(routePreviewAdaptive)
            usingCompactScale = shouldUseCompactScale()
            val incomingIds = reports.map { it.id }.toSet()
            
            // Премахваме markers които вече ги няма
            val toRemove = reportAnnotations.keys.filter { it !in incomingIds }
            toRemove.forEach { reportId ->
                reportAnnotations.remove(reportId)?.let { annotation ->
                    annotationToReportId.remove(annotation.id)
                    manager.delete(annotation)
                }
            }
            
            // Добавяме/обновяваме markers
            reports.forEach { report ->
                if (reportAnnotations.containsKey(report.id)) {
                    updateReportAnnotation(report, forceResize = true)
                } else {
                    addReportAnnotation(report)
                }
            }
            
            Log.d(
                TAG,
                "Updated map with ${reports.size} reports " +
                    "(routePreviewAdaptive=$routePreviewAdaptive, compact=$usingCompactScale)"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update reports on map", e)
        }
    }

    /**
     * Preview: compact when zoomed out. Navigation: pass false for full-size markers.
     */
    fun setRoutePreviewAdaptiveSizing(enabled: Boolean) {
        setRoutePreviewAdaptive(enabled)
        usingCompactScale = shouldUseCompactScale()
        applyScaleToAllAnnotations()
    }

    private fun setRoutePreviewAdaptive(enabled: Boolean) {
        routePreviewAdaptive = enabled
        if (enabled) {
            attachCameraListener()
        } else {
            detachCameraListener()
        }
    }

    private fun attachCameraListener() {
        if (cameraListenerAttached) return
        mapView.mapboxMap.addOnCameraChangeListener(cameraChangeListener)
        cameraListenerAttached = true
    }

    private fun detachCameraListener() {
        if (!cameraListenerAttached) return
        mapView.mapboxMap.removeOnCameraChangeListener(cameraChangeListener)
        cameraListenerAttached = false
    }

    private fun shouldUseCompactScale(): Boolean {
        if (!routePreviewAdaptive) return false
        return mapView.mapboxMap.cameraState.zoom < PREVIEW_FULL_SIZE_ZOOM
    }

    private fun refreshScaleForCurrentZoom() {
        val wantCompact = shouldUseCompactScale()
        if (wantCompact == usingCompactScale) return
        usingCompactScale = wantCompact
        applyScaleToAllAnnotations()
        Log.d(TAG, "Route preview marker scale -> compact=$usingCompactScale")
    }

    private fun applyScaleToAllAnnotations() {
        val manager = annotationManager ?: return
        val iconSize = currentIconSize()
        val textSize = currentTextSize()
        val textOffsetY = currentTextOffsetY()
        reportAnnotations.values.forEach { annotation ->
            annotation.iconSize = iconSize
            annotation.textSize = textSize
            annotation.textOffset = listOf(0.0, textOffsetY)
            manager.update(annotation)
        }
    }

    private fun currentIconSize(): Double =
        if (usingCompactScale) PREVIEW_ICON_SIZE else NORMAL_ICON_SIZE

    private fun currentTextSize(): Double =
        if (usingCompactScale) PREVIEW_TEXT_SIZE else NORMAL_TEXT_SIZE

    private fun currentTextOffsetY(): Double =
        if (usingCompactScale) 1.2 else 1.6
    
    /**
     * Добавя нов marker за доклад
     */
    private fun addReportAnnotation(report: PoliceReport) {
        val manager = annotationManager ?: return
        
        try {
            val reportType = ReportType.fromString(report.type) ?: ReportType.POLICE
            val iconBitmap = createReportIcon(reportType, report.getScore())
            
            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(report.location.longitude, report.location.latitude))
                .withIconImage(iconBitmap)
                .withIconSize(currentIconSize())
                .withTextField(likeCountLabel(report))
                .withTextSize(currentTextSize())
                .withTextColor(Color.WHITE)
                .withTextHaloColor(Color.BLACK)
                .withTextHaloWidth(1.0)
                .withTextOffset(listOf(0.0, currentTextOffsetY()))
            
            val annotation = manager.create(pointAnnotationOptions)
            reportAnnotations[report.id] = annotation
            annotationToReportId[annotation.id] = report.id
            
            Log.d(TAG, "Added report annotation: ${report.id}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add report annotation", e)
        }
    }
    
    /**
     * Обновява съществуващ marker
     */
    private fun updateReportAnnotation(report: PoliceReport, forceResize: Boolean = false) {
        val annotation = reportAnnotations[report.id] ?: return
        
        try {
            val reportType = ReportType.fromString(report.type) ?: ReportType.POLICE
            annotation.iconImageBitmap = createReportIcon(reportType, report.getScore())
            annotation.textField = likeCountLabel(report)
            if (forceResize) {
                annotation.iconSize = currentIconSize()
                annotation.textSize = currentTextSize()
                annotation.textOffset = listOf(0.0, currentTextOffsetY())
            }
            annotationManager?.update(annotation)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update report annotation", e)
        }
    }
    
    private fun likeCountLabel(report: PoliceReport): String {
        return if (report.upvotes > 0) report.upvotes.toString() else ""
    }

    /**
     * Създава custom икона за доклад от drawable ресурсите.
     * Оранжев фон (primary) + бяла рамка, за да се отличават на тъмната карта.
     */
    private fun createReportIcon(type: ReportType, score: Int): Bitmap {
        val size = MARKER_SIZE_PX
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val primaryColor = ContextCompat.getColor(mapView.context, R.color.primary_color)

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = primaryColor
            if (score < 0) alpha = 170
        }
        val radius = size / 2f - 4f
        canvas.drawCircle(size / 2f, size / 2f, radius, circlePaint)

        val clip = Path().apply {
            addCircle(size / 2f, size / 2f, radius - 2f, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)

        val iconBitmap = loadIconBitmapWithoutBlackBackground(type.iconResId, size)
        if (iconBitmap != null) {
            val inset = (size * 0.10f).toInt()
            val dst = android.graphics.Rect(inset, inset, size - inset, size - inset)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = if (score < 0) 170 else 255
            }
            canvas.drawBitmap(iconBitmap, null, dst, paint)
            iconBitmap.recycle()
        }
        canvas.restore()

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            color = Color.WHITE
        }
        canvas.drawCircle(size / 2f, size / 2f, radius, borderPaint)

        return bitmap
    }

    /**
     * Зарежда drawable и прави почти черния фон прозрачен,
     * за да се вижда оранжевият marker фон.
     */
    private fun loadIconBitmapWithoutBlackBackground(iconResId: Int, size: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(mapView.context, iconResId) ?: return null
        val source = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val sourceCanvas = Canvas(source)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(sourceCanvas)

        val pixels = IntArray(size * size)
        source.getPixels(pixels, 0, size, 0, 0, size, size)
        for (i in pixels.indices) {
            val color = pixels[i]
            val alpha = Color.alpha(color)
            if (alpha == 0) continue
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            // Treat near-black artwork background as transparent.
            if (r < 28 && g < 28 && b < 28) {
                pixels[i] = Color.TRANSPARENT
            }
        }
        source.setPixels(pixels, 0, size, 0, 0, size, size)
        return source
    }
    
    /**
     * Маха всички markers от картата
     */
    fun clearAllReports() {
        try {
            annotationManager?.deleteAll()
            reportAnnotations.clear()
            annotationToReportId.clear()
            Log.d(TAG, "Cleared all report annotations")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear reports", e)
        }
    }
    
    /**
     * Почиства ресурси
     */
    fun cleanup() {
        detachCameraListener()
        routePreviewAdaptive = false
        usingCompactScale = false
        clearAllReports()
        annotationManager = null
        onReportClickListener = null
        Log.d(TAG, "ReportsMapManager cleaned up")
    }
}
