package com.example.clinometer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.clinometer.settings.LanguageManager
import com.example.clinometer.main.MainContainerActivity

class SplashActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        
        // Скриваме бутона START за loading screen
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStart).visibility = android.view.View.GONE
        
        // След 1.5 секунди продължаваме към MainContainerActivity
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainContainerActivity::class.java)
            intent.putExtra("NAV_ITEM_ID", R.id.navMap) // Отваряме Map страницата по подразбиране
            startActivity(intent)
            overridePendingTransition(0, 0) // Премахваме анимацията
            finish()
        }, 1500)
    }
}

