package com.revix.app.main.map

import com.revix.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.revix.app.navigation.GeocodingFeature
import com.revix.app.navigation.CategoryFeature
import com.revix.app.navigation.CategoryResponse
import com.revix.app.navigation.MapboxGeocodingService
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
    
    // New UI elements
    private lateinit var layoutHome: LinearLayout
    private lateinit var layoutWork: LinearLayout
    private lateinit var tvHomeAddress: TextView
    private lateinit var tvWorkAddress: TextView
    private lateinit var btnCategoryFavorites: View
    private lateinit var btnCategoryGas: View
    private lateinit var btnCategoryParking: View
    private lateinit var btnCategoryFood: View
    private lateinit var btnCategoryCoffee: View
    
    private lateinit var sharedPrefs: SharedPreferences
    
    companion object {
        private const val PREFS_NAME = "REVIXPrefs"
        private const val LEGACY_PREFS_NAME = "RaceMotoPrefs"
        private const val KEY_PREFS_MIGRATED = "revix_prefs_migrated"
        private const val KEY_HOME_ADDRESS = "home_address"
        private const val KEY_HOME_LAT = "home_lat"
        private const val KEY_HOME_LON = "home_lon"
        private const val KEY_WORK_ADDRESS = "work_address"
        private const val KEY_WORK_LAT = "work_lat"
        private const val KEY_WORK_LON = "work_lon"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.revix.app.settings.LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_destination_search)
        
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        migrateLegacyPreferencesIfNeeded()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        getCurrentLocation()
        
        // Get Mapbox access token
        try {
            val resources: Resources = resources
            val resourceId = resources.getIdentifier("mapbox_access_token", "string", packageName)
            accessToken = resources.getString(resourceId)
        } catch (e: Resources.NotFoundException) {
            Toast.makeText(this, getString(R.string.mapbox_token_missing), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Initialize Retrofit for Geocoding API
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.mapbox.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        geocodingService = retrofit.create(MapboxGeocodingService::class.java)
        
        // Initialize views
        searchEditText = findViewById(R.id.etDestinationSearch)
        resultsRecyclerView = findViewById(R.id.rvSearchResults)
        layoutHome = findViewById(R.id.layoutHome)
        layoutWork = findViewById(R.id.layoutWork)
        tvHomeAddress = findViewById(R.id.tvHomeAddress)
        tvWorkAddress = findViewById(R.id.tvWorkAddress)
        btnCategoryFavorites = findViewById(R.id.btnCategoryFavorites)
        btnCategoryGas = findViewById(R.id.btnCategoryGas)
        btnCategoryParking = findViewById(R.id.btnCategoryParking)
        btnCategoryFood = findViewById(R.id.btnCategoryFood)
        btnCategoryCoffee = findViewById(R.id.btnCategoryCoffee)
        
        // Load saved addresses
        loadSavedAddresses()
        
        resultsAdapter = SearchResultsAdapter { feature ->
            // When a feature is clicked, navigate to route preview
            selectFeature(feature)
        }
        
        resultsRecyclerView.layoutManager = LinearLayoutManager(this)
        resultsRecyclerView.adapter = resultsAdapter
        
        // Setup Home/Work click listeners
        layoutHome.setOnClickListener {
            val homeAddress = sharedPrefs.getString(KEY_HOME_ADDRESS, null)
            if (homeAddress != null) {
                val lat = sharedPrefs.getFloat(KEY_HOME_LAT, 0f).toDouble()
                val lon = sharedPrefs.getFloat(KEY_HOME_LON, 0f).toDouble()
                navigateToDestination(homeAddress, lat, lon)
            } else {
                Toast.makeText(this, getString(R.string.search_set_home_address_prompt), Toast.LENGTH_SHORT).show()
                // TODO: Open address setting dialog
            }
        }
        
        layoutWork.setOnClickListener {
            val workAddress = sharedPrefs.getString(KEY_WORK_ADDRESS, null)
            if (workAddress != null) {
                val lat = sharedPrefs.getFloat(KEY_WORK_LAT, 0f).toDouble()
                val lon = sharedPrefs.getFloat(KEY_WORK_LON, 0f).toDouble()
                navigateToDestination(workAddress, lat, lon)
            } else {
                Toast.makeText(this, getString(R.string.search_set_work_address_prompt), Toast.LENGTH_SHORT).show()
                // TODO: Open address setting dialog
            }
        }
        
        // Setup category button listeners
        btnCategoryFavorites.setOnClickListener {
            Toast.makeText(this, getString(R.string.search_favorites_coming_soon), Toast.LENGTH_SHORT).show()
        }
        
        btnCategoryGas.setOnClickListener {
            searchPOICategory("gas_station", getString(R.string.search_category_gas_stations_plain))
        }
        
        btnCategoryParking.setOnClickListener {
            searchPOICategory("parking", getString(R.string.search_category_parking))
        }
        
        btnCategoryFood.setOnClickListener {
            searchPOICategory("restaurant", getString(R.string.search_category_food))
        }
        
        btnCategoryCoffee.setOnClickListener {
            searchPOICategory("coffee", getString(R.string.search_category_coffee))
        }
        
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
    
    private fun loadSavedAddresses() {
        val homeAddress = sharedPrefs.getString(KEY_HOME_ADDRESS, null)
        val workAddress = sharedPrefs.getString(KEY_WORK_ADDRESS, null)
        
        tvHomeAddress.text = homeAddress ?: getString(R.string.search_set_location)
        tvWorkAddress.text = workAddress ?: getString(R.string.search_set_location)
    }

    private fun migrateLegacyPreferencesIfNeeded() {
        if (sharedPrefs.getBoolean(KEY_PREFS_MIGRATED, false)) return

        val legacyPrefs = getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val hasLegacyData =
            legacyPrefs.contains(KEY_HOME_ADDRESS) ||
                legacyPrefs.contains(KEY_WORK_ADDRESS)

        if (hasLegacyData) {
            sharedPrefs.edit().apply {
                if (!sharedPrefs.contains(KEY_HOME_ADDRESS) && legacyPrefs.contains(KEY_HOME_ADDRESS)) {
                    putString(KEY_HOME_ADDRESS, legacyPrefs.getString(KEY_HOME_ADDRESS, null))
                }
                if (!sharedPrefs.contains(KEY_HOME_LAT) && legacyPrefs.contains(KEY_HOME_LAT)) {
                    putFloat(KEY_HOME_LAT, legacyPrefs.getFloat(KEY_HOME_LAT, 0f))
                }
                if (!sharedPrefs.contains(KEY_HOME_LON) && legacyPrefs.contains(KEY_HOME_LON)) {
                    putFloat(KEY_HOME_LON, legacyPrefs.getFloat(KEY_HOME_LON, 0f))
                }

                if (!sharedPrefs.contains(KEY_WORK_ADDRESS) && legacyPrefs.contains(KEY_WORK_ADDRESS)) {
                    putString(KEY_WORK_ADDRESS, legacyPrefs.getString(KEY_WORK_ADDRESS, null))
                }
                if (!sharedPrefs.contains(KEY_WORK_LAT) && legacyPrefs.contains(KEY_WORK_LAT)) {
                    putFloat(KEY_WORK_LAT, legacyPrefs.getFloat(KEY_WORK_LAT, 0f))
                }
                if (!sharedPrefs.contains(KEY_WORK_LON) && legacyPrefs.contains(KEY_WORK_LON)) {
                    putFloat(KEY_WORK_LON, legacyPrefs.getFloat(KEY_WORK_LON, 0f))
                }

                putBoolean(KEY_PREFS_MIGRATED, true)
                apply()
            }
        } else {
            sharedPrefs.edit().putBoolean(KEY_PREFS_MIGRATED, true).apply()
        }
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
                    getString(R.string.search_error_with_message, e.message ?: getString(R.string.error_unknown)), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun selectFeature(feature: GeocodingFeature) {
        // Navigate to route preview with the selected destination
        val center = feature.center
        if (center != null && center.size >= 2) {
            navigateToDestination(feature.placeName, center[1], center[0])
        }
    }
    
    private fun navigateToDestination(name: String, latitude: Double, longitude: Double) {
        val intent = Intent(this, RoutePreviewActivity::class.java).apply {
            putExtra("destination_name", name)
            putExtra("destination_latitude", latitude)
            putExtra("destination_longitude", longitude)
            currentLocation?.let {
                putExtra("origin_latitude", it.latitude)
                putExtra("origin_longitude", it.longitude)
            }
        }
        startActivity(intent)
        finish()
    }
    
    private fun searchPOICategory(categoryId: String, displayName: String) {
        if (currentLocation == null) {
            Toast.makeText(this, getString(R.string.search_wait_for_location_loading), Toast.LENGTH_SHORT).show()
            return
        }
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val proximity = "${currentLocation!!.longitude},${currentLocation!!.latitude}"
                
                Toast.makeText(this@DestinationSearchActivity, 
                    getString(R.string.search_searching_nearby, displayName), Toast.LENGTH_SHORT).show()
                
                // Use Category Search API with canonical category ID
                val response = withContext(Dispatchers.IO) {
                    geocodingService.searchCategory(
                        category = categoryId,
                        proximity = proximity,
                        accessToken = accessToken,
                        limit = 25,
                        language = "en"
                    )
                }
                
                if (response.isSuccessful && response.body() != null) {
                    val categoryFeatures = response.body()!!.features
                    if (categoryFeatures.isNotEmpty()) {
                        // Convert CategoryFeature to GeocodingFeature for adapter
                        val geocodingFeatures = categoryFeatures.map { categoryFeature ->
                            GeocodingFeature(
                                id = categoryFeature.properties.mapboxId,
                                placeName = categoryFeature.properties.fullAddress 
                                    ?: categoryFeature.properties.name,
                                center = categoryFeature.geometry.coordinates,
                                text = categoryFeature.properties.name,
                                properties = null
                            )
                        }
                        
                        resultsAdapter.updateResults(geocodingFeatures)
                        Toast.makeText(this@DestinationSearchActivity, 
                            getString(R.string.search_results_found_count, geocodingFeatures.size), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@DestinationSearchActivity, 
                            getString(R.string.search_no_results_nearby, displayName), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@DestinationSearchActivity, 
                        getString(R.string.search_error_with_code, response.code()), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@DestinationSearchActivity, 
                    getString(R.string.error_with_message, e.message ?: getString(R.string.error_unknown)), Toast.LENGTH_SHORT).show()
            }
        }
    }
}


