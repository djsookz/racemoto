package com.example.clinometer.reports.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.example.clinometer.reports.data.PoliceReport
import com.example.clinometer.reports.data.ReportType
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.annotation.AnnotationPlugin
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager

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
    
    companion object {
        private const val TAG = "ReportsMapManager"
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
            
            // Добавяме click listener
            annotationManager?.addClickListener(
                com.mapbox.maps.plugin.annotation.generated.OnPointAnnotationClickListener { clickedAnnotation ->
                    val reportId = annotationToReportId[clickedAnnotation.id]
                    if (reportId != null) {
                        onReportClickListener?.invoke(reportId)
                        Log.d(TAG, "Report clicked: $reportId")
                        true // Consume click event
                    } else {
                        false
                    }
                }
            )
            
            Log.d(TAG, "ReportsMapManager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ReportsMapManager", e)
        }
    }
    
    /**
     * Обновява markers на картата с нови доклади
     */
    fun updateReports(reports: List<PoliceReport>) {
        val manager = annotationManager ?: run {
            Log.w(TAG, "AnnotationManager not initialized")
            return
        }
        
        try {
            // Намираме кои доклади са нови/променени/премахнати
            val currentReportIds = reports.map { it.id }.toSet()
            val existingReportIds = reportAnnotations.keys.toSet()
            
            // Махаме стари markers които вече не съществуват
            val toRemove = existingReportIds - currentReportIds
            toRemove.forEach { reportId ->
                reportAnnotations[reportId]?.let { annotation ->
                    annotationToReportId.remove(annotation.id)
                    manager.delete(annotation)
                    reportAnnotations.remove(reportId)
                }
            }
            
            // Добавяме/обновяваме markers
            reports.forEach { report ->
                if (reportAnnotations.containsKey(report.id)) {
                    // Обновяваме съществуващ marker (напр. upvotes се променят)
                    updateReportAnnotation(report)
                } else {
                    // Създаваме нов marker
                    addReportAnnotation(report)
                }
            }
            
            Log.d(TAG, "Updated map with ${reports.size} reports")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update reports on map", e)
        }
    }
    
    /**
     * Добавя нов marker за доклад
     */
    private fun addReportAnnotation(report: PoliceReport) {
        val manager = annotationManager ?: return
        
        try {
            val reportType = ReportType.fromString(report.type) ?: ReportType.POLICE
            
            // Създаваме custom икона
            val iconBitmap = createReportIcon(reportType, report.getScore())
            
            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(Point.fromLngLat(report.location.longitude, report.location.latitude))
                .withIconImage(iconBitmap)
                .withIconSize(1.0)
                .withTextField("${reportType.icon} ${report.getScore()}")
                .withTextSize(12.0)
                .withTextColor(Color.WHITE)
                .withTextOffset(listOf(0.0, 1.5))
            
            val annotation = manager.create(pointAnnotationOptions)
            reportAnnotations[report.id] = annotation
            annotationToReportId[annotation.id] = report.id // Запазваме mapping
            
            Log.d(TAG, "Added report annotation: ${report.id}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add report annotation", e)
        }
    }
    
    /**
     * Обновява съществуващ marker
     */
    private fun updateReportAnnotation(report: PoliceReport) {
        val annotation = reportAnnotations[report.id] ?: return
        
        try {
            val reportType = ReportType.fromString(report.type) ?: ReportType.POLICE
            
            // Обновяваме текста с новия score
            annotation.textField = "${reportType.icon} ${report.getScore()}"
            
            // Обновяваме annotation manager
            annotationManager?.update(annotation)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update report annotation", e)
        }
    }
    
    /**
     * Създава custom икона за доклад
     */
    private fun createReportIcon(type: ReportType, score: Int): Bitmap {
        val size = 120
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Фонов кръг с цвят според типа
        paint.style = Paint.Style.FILL
        paint.color = when (type) {
            ReportType.POLICE -> Color.parseColor("#E53935") // Червено
            ReportType.CAMERA -> Color.parseColor("#FB8C00") // Оранжево
            ReportType.ACCIDENT -> Color.parseColor("#FDD835") // Жълто
            ReportType.HAZARD -> Color.parseColor("#F4511E") // Тъмно оранжево
            ReportType.TRAFFIC -> Color.parseColor("#039BE5") // Синьо
            ReportType.ROADWORK -> Color.parseColor("#6D4C41") // Кафяво
        }
        
        // По-тъмен цвят ако има negative score
        if (score < 0) {
            paint.alpha = 150
        }
        
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 10, paint)
        
        // Бяла граница
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 10, paint)
        
        // Emoji в центъра
        paint.style = Paint.Style.FILL
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        paint.color = Color.WHITE
        
        val emoji = type.icon
        val textY = size / 2f - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(emoji, size / 2f, textY, paint)
        
        return bitmap
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
        clearAllReports()
        annotationManager = null
        onReportClickListener = null
        Log.d(TAG, "ReportsMapManager cleaned up")
    }
}
