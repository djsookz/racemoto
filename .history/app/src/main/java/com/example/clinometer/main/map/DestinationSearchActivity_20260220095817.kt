package com.example.clinometer.main.map

import android.content.Intent
import android.content.res.Resources
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.clinometer.navigation.GeocodingFeature
import com.example.clinometer.navigation.MapboxGeocodingService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DestinationSearchActivity : AppCompatActivity() {
    
    private lateinit var searchEditText: EditText
    private lateinit var resultsRecyclerView: RecyclerView
    private lateinit var resultsAdapter: SearchResultsAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private lateinit var geocodingService: MapboxGeocodingService
    private var accessToken: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_destination_search)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        getCurrentLocation()
        
        // Get Mapbox access token
        try {
            val resources: Resources = resources
            val resourceId = resources.getIdentifier("mapbox_access_token", "string", packageName)
            accessToken = resources.getString(resourceId)
        } catch (e: Resources.NotFoundException) {
            Toast.makeText(this, "Mapbox token не е намерен", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize Retrofit for Geocoding API
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.mapbox.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        geocodingService = retrofit.create(MapboxGeocodingService::class.java)
        
        searchEditText = findViewById(R.id.etDestinationSearch)
        resultsRecyclerView = findViewById(R.id.rvSearchResults)
        
        resultsAdapter = SearchResultsAdapter { feature ->
            // When a feature is clicked, navigate to route preview
            selectFeature(feature)
        }
        
        resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        resultsRecyclerView.adapter = resultsAdapter
        
        // Auto-focus the search input and show keyboard
        searchEditText.requestFocus()
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        
        // Setup search text watcher
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.length >= 2) {
                    performSearch(query)
                } else {
                    resultsAdapter.updateResults(emptyList())
                }
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun getCurrentLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                currentLocation = location
            }
        } catch (e: SecurityException) {
            // Location permission not granted
        }
    }
    
    private fun performSearch(query: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Build proximity string if we have current location
                val proximity = currentLocation?.let {
                    "${it.longitude},${it.latitude}"
                }
                
                val response = withContext(Dispatchers.IO) {
                    geocodingService.searchPlaces(query, accessToken, proximity, 10)
                }
                
                if (response.isSuccessful && response.body() != null) {
                    val features = response.body()!!.features
                    resultsAdapter.updateResults(features)
                } else {
                    resultsAdapter.updateResults(emptyList())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@DestinationSearchActivity, 
                    "Грешка при търсене: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun selectFeature(feature: GeocodingFeature) {
        // Navigate to route preview with the selected destination
        val center = feature.center
        if (center.size >= 2) {
            val intent = Intent(this, RoutePreviewActivity::class.java).apply {
                putExtra("destination_name", feature.placeName)
                putExtra("destination_latitude", center[1]) // latitude
                putExtra("destination_longitude", center[0]) // longitude
                currentLocation?.let {
                    putExtra("origin_latitude", it.latitude)
                    putExtra("origin_longitude", it.longitude)
                }
            }
            startActivity(intent)
            finish()
        }
    }
}


