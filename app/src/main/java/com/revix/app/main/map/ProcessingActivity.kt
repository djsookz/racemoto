package com.revix.app.main.map

import com.revix.app.*
import com.revix.app.main.MainContainerActivity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.revix.app.RoutePoint
import kotlinx.coroutines.*

class ProcessingActivity : AppCompatActivity() {
    
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var progressText: TextView
    
    private var processingJob: Job? = null

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.revix.app.settings.LanguageManager.applyLanguage(newBase))
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_processing)
        
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        progressText = findViewById(R.id.progressText)
        
        // Get raceId from intent
        val raceId = intent.getLongExtra("raceId", 0L)
        
        if (raceId == 0L) {
            android.util.Log.e("ProcessingActivity", "❌ No raceId provided!")
            showErrorAndNavigateHome(getString(R.string.processing_error_missing_route_id))
            return
        }
        
        // Load data from RouteStorage (avoiding Intent size limits!)
        val rawRoutePoints = RouteStorage.loadRoutePoints(this, raceId)
        
        // Професионална проверка: Минимум 3 точки са необходими за валиден маршрут
        if (rawRoutePoints.isEmpty()) {
            android.util.Log.e("ProcessingActivity", "❌ No route points found for raceId $raceId!")
            showErrorAndNavigateHome(getString(R.string.processing_error_no_route_data))
            return
        }
        
        if (rawRoutePoints.size < 3) {
            android.util.Log.w("ProcessingActivity", "⚠️ Insufficient route points: ${rawRoutePoints.size} (minimum: 3)")
            showErrorAndNavigateHome(getString(R.string.processing_error_min_points))
            return
        }
        
        android.util.Log.d("ProcessingActivity", "✅ Loaded ${rawRoutePoints.size} points from storage for raceId $raceId")
        
        // КРИТИЧЕН ЛОГ: Проверка на angle данните!
        val angleData = rawRoutePoints.map { it.angle }
        val nonZeroAngles = angleData.count { it != 0f }
        val minAngle = angleData.minOrNull() ?: 0f
        val maxAngle = angleData.maxOrNull() ?: 0f
        android.util.Log.d("ProcessingActivity", "📐 ANGLE DATA CHECK:")
        android.util.Log.d("ProcessingActivity", "   Total points: ${angleData.size}")
        android.util.Log.d("ProcessingActivity", "   Non-zero angles: $nonZeroAngles")
        android.util.Log.d("ProcessingActivity", "   Min angle: $minAngle°")
        android.util.Log.d("ProcessingActivity", "   Max angle: $maxAngle°")
        android.util.Log.d("ProcessingActivity", "   First 5 angles: ${angleData.take(5)}")
        android.util.Log.d("ProcessingActivity", "   Last 5 angles: ${angleData.takeLast(5)}")
        
        // Start processing
        startProcessing(ArrayList(rawRoutePoints), raceId)
    }
    
    private var processingStartTime = 0L
    
    private fun startProcessing(
        rawRoutePoints: ArrayList<RoutePoint>,
        raceId: Long
    ) {
        processingStartTime = System.currentTimeMillis()
        statusText.text = getString(R.string.processing_status_processing)
        progressText.text = ""
        
        processingJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                // Step 1: Initialize
                updateProgress(10, "")
                delay(500)
                
                // Step 2: Return raw GPS points (no filtering)
                updateProgress(30, "")
                
                // Important: Process in IO dispatcher to avoid blocking UI
                val processedPoints = withContext(Dispatchers.IO) {
                    // Return raw GPS points without any filtering (for all providers)
                    android.util.Log.d("ProcessingActivity", "📊 Returning RAW GPS points (no filtering)")
                    
                    withContext(Dispatchers.Main) {
                        updateProgress(50, "")
                    }
                    
                    // Return raw points - SDK handles map matching
                    rawRoutePoints
                }
                    
                
                // Step 3: Convert back to RoutePoint
                updateProgress(60, "")
                delay(300)
                
                android.util.Log.d("ProcessingActivity", "🔄 Converting ${processedPoints.size} processed points back to RoutePoints")
                android.util.Log.d("ProcessingActivity", "   Raw route points: ${rawRoutePoints.size}")
                
                val enhancedRoutePoints = mutableListOf<RoutePoint>()
                
                // SDK handles map matching - use raw route points directly
                enhancedRoutePoints.addAll(processedPoints)
                android.util.Log.d("ProcessingActivity", "✅ Using ${enhancedRoutePoints.size} raw route points (SDK handles map matching)")
                
                // ФАЛБЕК: Ако enhancedRoutePoints е празен, използвай RAW данните!
                if (enhancedRoutePoints.isEmpty() && rawRoutePoints.isNotEmpty()) {
                    android.util.Log.w("ProcessingActivity", "⚠️ Enhanced points are empty, using RAW data!")
                    enhancedRoutePoints.addAll(rawRoutePoints)
                }
                
                // Step 4: Final processing
                updateProgress(80, "")
                delay(200)
                
                // Step 5: Complete
                updateProgress(100, "")
                runOnUiThread {
                    statusText.text = "✅ Complete!"
                }

                android.util.Log.d("ProcessingActivity", "✅ Processing complete! RaceId: $raceId, Points: ${enhancedRoutePoints.size}")
                
                // Показваме резултат на потребителя
                runOnUiThread {
                    statusText.text = getString(R.string.processing_status_complete)
                    progressText.text = "" // Премахваме "Processed X points in Ys"
                    
                    // Snapping status logged (no UI notification)
                }
                delay(800)
                
                // Професионална проверка: Уверяваме се че имаме достатъчно данни преди навигация
                if (enhancedRoutePoints.isEmpty()) {
                    android.util.Log.e("ProcessingActivity", "❌ No processed points available after processing!")
                    showErrorAndNavigateHome(getString(R.string.processing_error_insufficient_after_processing))
                    return@launch
                }
                
                if (enhancedRoutePoints.size < 3) {
                    android.util.Log.w("ProcessingActivity", "⚠️ Insufficient processed points: ${enhancedRoutePoints.size}")
                    showErrorAndNavigateHome(getString(R.string.processing_error_min_points))
                    return@launch
                }
                
                // Save processed data and navigate to results
                saveProcessedData(enhancedRoutePoints, raceId)
                android.util.Log.d("ProcessingActivity", "✅ Data saved, navigating to MapActivity...")
                navigateToResults(enhancedRoutePoints, raceId)
                
            } catch (e: Exception) {
                android.util.Log.e("ProcessingActivity", "❌ Error during processing: ${e.message}", e)
                
                val errorMessage = e.message ?: getString(R.string.error_unknown)
                runOnUiThread {
                    statusText.text = getString(R.string.processing_status_error_with_type, e.javaClass.simpleName)
                    progressText.text = errorMessage
                }
                
                // Професионална обработка на грешка: Показваме съобщение и връщаме потребителя в началната страница
                Handler(Looper.getMainLooper()).postDelayed({
                    showErrorAndNavigateHome(getString(R.string.processing_error_with_message, errorMessage))
                }, 2000)
            }
        }
    }
    
    private fun updateProgress(progress: Int, message: String) {
        runOnUiThread {
            progressBar.progress = progress
            progressText.text = message
        }
    }
    
    private fun saveProcessedData(routePoints: List<RoutePoint>, raceId: Long) {
        try {
            android.util.Log.d("ProcessingActivity", "💾 Saving processed data: raceId=$raceId, points=${routePoints.size}")
            
            // КРИТИЧНА ПРОВЕРКА: Не презаписвай с празен списък!
            if (routePoints.isEmpty()) {
                android.util.Log.e("ProcessingActivity", "❌ REFUSING to save empty list! This would delete existing data!")
                android.util.Log.e("ProcessingActivity", "   HMM processing FAILED - keeping original data!")
                return
            }
            
            // Points were already written at session stop; processing is currently a no-op
            // (raw GPS). Avoid rewriting multi-MB JSON. Keep races.json metadata-only.
            val allRaces = RouteStorage.loadRaces(this).toMutableList()
            val raceIndex = allRaces.indexOfFirst { it.id == raceId }
            if (raceIndex != -1) {
                allRaces[raceIndex] = allRaces[raceIndex].copy(routePoints = emptyList())
                RouteStorage.saveRaces(this, allRaces)
                android.util.Log.d("ProcessingActivity", "✅ Updated race metadata for raceId=$raceId")
                
                // 🔥 Генерирай snapshot ВЕДНАГА след запис (background task)
                java.util.concurrent.Executors.newSingleThreadExecutor().execute {
                    try {
                        RouteSnapshotGenerator.generateAndSaveSnapshot(
                            context = this@ProcessingActivity,
                            raceId = raceId,
                            routePoints = routePoints,
                            // Overwrite snapshot generated earlier from raw points.
                            // The list must reflect the final processed route shown in details.
                            forceRegenerate = true
                        ) { success ->
                            android.util.Log.d("ProcessingActivity", if (success) "✅ Snapshot generated for race $raceId" else "❌ Failed to generate snapshot for race $raceId")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ProcessingActivity", "Error generating snapshot", e)
                    }
                }
            } else {
                android.util.Log.w("ProcessingActivity", "⚠️ Race not found in storage for raceId=$raceId")
            }
        } catch (e: Exception) {
            android.util.Log.e("ProcessingActivity", "❌ Error saving processed data: ${e.message}", e)
        }
    }
    
    private fun navigateToResults(routePoints: List<RoutePoint>, raceId: Long) {
        android.util.Log.d("ProcessingActivity", "🚀 Navigating to MapActivity with raceId=$raceId, points=${routePoints.size}")
        
        // Don't pass routePoints via Intent (Binder 1MB limit!)
        // MapActivity will load them from RouteStorage using raceId
        val intent = Intent(this, MapActivity::class.java).apply {
            putExtra("RACE_ID", raceId)  // MapActivity expects "RACE_ID" not "raceId"
            putExtra("fromProcessing", true)
        }
        
        android.util.Log.d("ProcessingActivity", "📤 Starting MapActivity...")
        startActivity(intent)
        
        android.util.Log.d("ProcessingActivity", "🏁 Finishing ProcessingActivity...")
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        processingJob?.cancel()
    }
    
    override fun onBackPressed() {
        // Prevent going back during processing
        // User must wait for processing to complete
    }
    
    /**
     * Професионална обработка на грешки: Показва съобщение и връща потребителя в началната страница
     */
    private fun showErrorAndNavigateHome(errorMessage: String) {
        android.util.Log.e("ProcessingActivity", "🚨 Error handled: $errorMessage")
        
        runOnUiThread {
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            
            // Връщаме потребителя в началната страница (MainContainerActivity)
            val intent = Intent(this, MainContainerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainContainerActivity.EXTRA_INITIAL_PAGE, MainContainerActivity.PAGE_MAP)
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }
    }
}
