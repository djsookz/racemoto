package com.example.clinometer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.clinometer.garage.VehicleSelectionActivity
import com.example.clinometer.settings.LanguageManager
import com.google.android.material.button.MaterialButton

class WelcomeActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    private val PERMISSION_REQUEST_CODE = 1001
    private var backPressedTime: Long = 0
    private lateinit var backToast: Toast

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        
        // START button → check permissions and navigate
        findViewById<MaterialButton>(R.id.btnStart).setOnClickListener {
            if (checkPermissions()) {
                navigateToVehicleSelection()
            } else {
                requestPermissions()
            }
        }
    }

    private fun navigateToVehicleSelection() {
        val intent = Intent(this, VehicleSelectionActivity::class.java).apply {
            putExtra("IS_FIRST_LAUNCH", true)
        }
        startActivity(intent)
        finish()
    }

    private fun checkPermissions(): Boolean {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return perms.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, perms, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (checkPermissions()) {
                // След като получим GPS permissions, проверяваме за Battery Optimization
                checkBatteryOptimization()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.error_permissions_required),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun checkBatteryOptimization() {
        if (BatteryOptimizationHelper.shouldShowBatteryOptimizationDialog(this)) {
            // Отваряме цяла страница за всички оптимизации
            val intent = Intent(this, OptimizationSetupActivity::class.java).apply {
                putExtra("IS_FIRST_LAUNCH", true)
            }
            startActivity(intent)
            finish()
        } else {
            // Ако не трябва да показваме, директно към Vehicle Selection
            navigateToVehicleSelection()
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Двойно натискане за изход (като в MainMapActivity, GarageActivity и т.н.)
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            backToast.cancel()
            super.onBackPressed()
            finish()
        } else {
            backToast = Toast.makeText(
                baseContext,
                getString(R.string.back_press_exit),
                Toast.LENGTH_SHORT
            )
            backToast.show()
        }
        backPressedTime = System.currentTimeMillis()
    }
}

