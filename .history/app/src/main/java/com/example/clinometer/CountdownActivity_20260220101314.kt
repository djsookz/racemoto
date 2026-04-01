package com.example.clinometer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.CountDownTimer
import android.os.IBinder
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.clinometer.main.MainActivity

class CountdownActivity : AppCompatActivity() {

    private var isCountingDown = true
    private var hasFinished = false
    private lateinit var selectedProfile: Profile
    
    private var foregroundService: ForegroundService? = null
    private var serviceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as? ForegroundService.LocalBinder
            foregroundService = local?.getService()
            serviceBound = true
            
            // Linear Accel калибрация се прави САМО за DRAG режима, не за нормални сесии
            // foregroundService?.startLinearAccelCalibration()
            // GPS се подготвя в background (без визуален текст)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            foregroundService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_countdown)

        // Позволяваме свободно въртене, но обработваме промените без рестартиране

        // Вземане на избрания профил от StartActivity
        selectedProfile = intent.getSerializableExtra("SELECTED_PROFILE") as? Profile
            ?: Profile(name = "Моят профил", vehicleType = Profile.VehicleType.MOTORCYCLE)

        // Ако активността се рестартира, не започваме отново броенето
        if (savedInstanceState != null) {
            return
        }

        val countdownText = findViewById<TextView>(R.id.countdownText)

        // Стартиране на услугата ВЕДНАГА за да започне събирането на GPS данни
        // но с флаг че е в режим "pre-warming" - само GPS, без хронометър
        val serviceIntent = Intent(this, ForegroundService::class.java).apply {
            putExtra("PRE_WARMING_MODE", true)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        
        // Bind към service за калибрация
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                countdownText.text = "$secondsLeft"
            }

            override fun onFinish() {
                if (hasFinished) return // Предотвратяваме множествено изпълнение
                
                isCountingDown = false
                hasFinished = true
                
                // Linear Accel калибрация е САМО за DRAG режима
                // foregroundService?.stopLinearAccelCalibration()
                
                // За нормални сесии GPS е вече готов (без визуален индикатор)

                // Сигнализираме на услугата че вече може да премине в нормален режим
                val activateIntent = Intent(this@CountdownActivity, ForegroundService::class.java).apply {
                    putExtra("ACTIVATE_NORMAL_MODE", true)
                }
                startService(activateIntent)

                // Преминаване към MainActivity с избрания профил
                val mainIntent = Intent(this@CountdownActivity, MainActivity::class.java).apply {
                    putExtra("SELECTED_PROFILE", selectedProfile)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(mainIntent)
                finish()
            }
        }.start()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Не правим нищо - просто позволяваме ориентацията да се променя без рестартиране
        // Броенето продължава нормално
    }

    override fun onBackPressed() {
        if (!isCountingDown) {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: Exception) {
                // Ignore
            }
            serviceBound = false
        }
    }
}