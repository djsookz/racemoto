package com.example.clinometer.reports

/**
 * ПРИМЕР КОД за интеграция на Reports системата в TrackSessionActivity
 * 
 * Копирай/адаптирай този код в TrackSessionActivity.kt за да активираш reports функционалността
 */

/*

// 1. Добави member variable в TrackSessionActivity:
private var reportsIntegration: ReportsIntegration? = null

// 2. В onCreate() след като MapView е инициализиран:
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_track_session)
    
    // ... existing initialization code ...
    
    // Инициализирай MapView
    mapView = findViewById(R.id.mapView)
    
    // Инициализирай Reports System
    try {
        reportsIntegration = ReportsIntegration(this, mapView)
        reportsIntegration?.initialize()
        Log.d("TrackSession", "Reports system initialized")
    } catch (e: Exception) {
        Log.e("TrackSession", "Failed to initialize reports system", e)
    }
}

// 3. В onDestroy() cleanup:
override fun onDestroy() {
    super.onDestroy()
    reportsIntegration?.cleanup()
    reportsIntegration = null
}

// 4. Когато получаваш GPS координати (в LocationCallback или onLocationChanged):
private fun onLocationUpdate(latitude: Double, longitude: Double) {
    // ... existing location handling ...
    
    // Start observing reports около текущата позиция
    reportsIntegration?.startObservingReports(
        centerLatitude = latitude,
        centerLongitude = longitude,
        radiusKm = 50.0
    )
}

// 5. Добави FAB (Floating Action Button) за докладване:
// В layout XML (activity_track_session.xml):
<com.google.android.material.floatingactionbutton.FloatingActionButton
    android:id="@+id/fabReport"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom|end"
    android:layout_margin="16dp"
    android:src="@android:drawable/ic_menu_report_image"
    android:contentDescription="Докладвай" />

// В onCreate() listener:
val fabReport = findViewById<FloatingActionButton>(R.id.fabReport)
fabReport.setOnClickListener {
    val currentLat = lastKnownLocation?.latitude ?: 0.0
    val currentLon = lastKnownLocation?.longitude ?: 0.0
    
    if (currentLat != 0.0 && currentLon != 0.0) {
        reportsIntegration?.showCreateReportDialog(currentLat, currentLon)
    } else {
        Toast.makeText(this, "Няма GPS координати", Toast.LENGTH_SHORT).show()
    }
}

// 6. OPTIONAL: Click на marker за voting:
// Ако искаш click detection на report markers, имплементирай:
mapView.getMapboxMap().addOnMapClickListener { point ->
    val clickedLat = point.latitude()
    val clickedLon = point.longitude()
    
    // TODO: Намери кой report marker е кликнат
    // Засега просто показваме toast
    Log.d("TrackSession", "Map clicked at: $clickedLat, $clickedLon")
    
    false // Return false за да не блокира други listeners
}

*/

// Това е само пример код. За реална интеграция трябва да адаптираш според твоя TrackSessionActivity.
