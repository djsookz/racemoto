package com.example.clinometer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.clinometer.settings.LanguageManager
import android.content.Context
import android.graphics.Typeface
import androidx.preference.PreferenceManager
import android.view.WindowManager
import com.example.clinometer.data.ProfileStorage
import android.widget.Toast

/**
 * Главна Container Activity която държи ViewPager2 с всички основни Fragments
 * Това позволява instant navigation без презареждания - всички Fragments остават в паметта
 */
class MainContainerActivity : AppCompatActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var navDrag: LinearLayout
    private lateinit var navGarage: LinearLayout
    private lateinit var navMap: LinearLayout
    private lateinit var navTrack: LinearLayout
    private lateinit var navOptions: LinearLayout
    
    private var currentPage = 0
    private var lastBackPressTime = 0L
    
    companion object {
        const val PAGE_MAP = 0
        const val PAGE_TRACK = 1
        const val PAGE_DRAG = 2
        const val PAGE_GARAGE = 3
        const val PAGE_SETTINGS = 4
        const val PAGE_RACES = 5 // Страница за сесии от нормалното каране
    }
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (ProfileStorage.loadProfiles(this).isEmpty()) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        
        setContentView(R.layout.activity_main_container)
        setupScreenKeepOn()
        
        viewPager = findViewById(R.id.viewPager)
        
        // Handle system bars insets - добавяме padding само на ViewPager за да не покрива navigation bar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(viewPager) { v, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemInsets.left, systemInsets.top, systemInsets.right, systemInsets.bottom)
            insets
        }
        
        // Уверяваме се че bottom navigation контейнерът е видим
        val bottomNavContainer = findViewById<View>(R.id.bottomNavigationContainer)
        bottomNavContainer?.visibility = View.VISIBLE
        bottomNavContainer?.bringToFront()
        
        // Уверяваме се че navigation елементите се зареждат
        navDrag = findViewById(R.id.navDrag)
        navGarage = findViewById(R.id.navGarage)
        navMap = findViewById(R.id.navMap)
        navTrack = findViewById(R.id.navTrack)
        navOptions = findViewById(R.id.navOptions)
        
        // Debug - проверяваме дали navigation елементите са заредени
        if (navDrag == null || navGarage == null || navMap == null || navTrack == null || navOptions == null) {
            android.util.Log.e("MainContainerActivity", "ERROR: Navigation items not found!")
        } else {
            android.util.Log.d("MainContainerActivity", "✅ All navigation items loaded successfully")
        }
        
        // Създаваме ViewPager2 adapter - Fragments се създават динамично
        viewPager.adapter = MainPagerAdapter(this)
        
        // Изключваме user input за swipe (само bottom navigation работи)
        viewPager.isUserInputEnabled = false
        
        // Задаваме начална страница - поддържаме и INITIAL_PAGE и NAV_ITEM_ID
        val initialPage = if (intent.hasExtra("INITIAL_PAGE")) {
            intent.getIntExtra("INITIAL_PAGE", PAGE_MAP)
        } else if (intent.hasExtra("NAV_ITEM_ID")) {
            val navItemId = intent.getIntExtra("NAV_ITEM_ID", R.id.navMap)
            navItemIdToPosition(navItemId)
        } else {
            PAGE_MAP
        }
        viewPager.setCurrentItem(initialPage, false)
        currentPage = initialPage
        highlightActiveNavItem(getItemIdForPage(initialPage))
        
        // Setup bottom navigation
        setupBottomNavigation()
        
        // Използваме OnBackPressedDispatcher вместо onBackPressed() за да позволим на Fragments да обработват back първо
        // Важно: Fragment callbacks се изпълняват ПРЕДИ Activity callbacks, защото се регистрират по-късно
        // Така че ако Fragment callback-ът е enabled и се изпълни, той ще спре цепочката
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentPage == PAGE_MAP) {
                    val mapFragment = supportFragmentManager.fragments.find { it is MapFragment } as? MapFragment
                    if (mapFragment?.handleBackPressedFromActivity() == true) {
                        return
                    }
                }
                // Проверяваме дали сме на RACES страницата и дали Fragment-ът е в selection mode
                if (currentPage == PAGE_RACES) {
                    val racesFragment = supportFragmentManager.fragments.find { 
                        it is RacesFragment 
                    } as? RacesFragment
                    if (racesFragment != null && racesFragment.isSelectionModeEnabled()) {
                        // Ако Fragment-ът е в selection mode, неговата callback-а трябва да се изпълни първо
                        // Но ако по някаква причина не се изпълни, извикваме exitSelectionMode директно
                        racesFragment.exitSelectionModeDirectly()
                        return
                    }
                    // Ако не сме в selection mode, връщаме се на MAP
                    viewPager.setCurrentItem(PAGE_MAP, false)
                    return
                }

                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000L) {
                    finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(this@MainContainerActivity, "Натиснете назад още веднъж за изход", Toast.LENGTH_SHORT).show()
                }
            }
        })
        
        // Listener за ViewPager промени (ако някой промени страницата програмно)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPage = position
                highlightActiveNavItem(getItemIdForPage(position))
            }
        })
    }
    
    private fun getItemIdForPage(page: Int): Int {
        return when (page) {
            PAGE_MAP -> R.id.navMap
            PAGE_TRACK -> R.id.navTrack
            PAGE_DRAG -> R.id.navDrag
            PAGE_GARAGE -> R.id.navGarage
            PAGE_SETTINGS -> R.id.navOptions
            else -> R.id.navMap
        }
    }
    
    private fun navItemIdToPosition(navItemId: Int): Int {
        return when (navItemId) {
            R.id.navMap -> PAGE_MAP
            R.id.navTrack -> PAGE_TRACK
            R.id.navDrag -> PAGE_DRAG
            R.id.navGarage -> PAGE_GARAGE
            R.id.navOptions -> PAGE_SETTINGS
            else -> PAGE_MAP
        }
    }
    
    private fun setupBottomNavigation() {
        navDrag.setOnClickListener {
            if (currentPage != PAGE_DRAG) {
                animateAndNavigate(it) {
                    viewPager.setCurrentItem(PAGE_DRAG, false)
                }
            }
        }
        
        navGarage.setOnClickListener {
            if (currentPage != PAGE_GARAGE) {
                animateAndNavigate(it) {
                    viewPager.setCurrentItem(PAGE_GARAGE, false)
                }
            }
        }
        
        navMap.setOnClickListener {
            if (currentPage != PAGE_MAP) {
                animateAndNavigate(it) {
                    viewPager.setCurrentItem(PAGE_MAP, false)
                }
            }
        }
        
        navTrack.setOnClickListener {
            if (currentPage != PAGE_TRACK) {
                animateAndNavigate(it) {
                    viewPager.setCurrentItem(PAGE_TRACK, false)
                }
            }
        }
        
        navOptions.setOnClickListener {
            if (currentPage != PAGE_SETTINGS) {
                animateAndNavigate(it) {
                    viewPager.setCurrentItem(PAGE_SETTINGS, false)
                }
            }
        }
    }
    
    private fun animateAndNavigate(view: View, navigation: () -> Unit) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
                navigation()
            }
            .start()
    }
    
    private fun highlightActiveNavItem(activeItemId: Int) {
        // Reset всички
        resetNavItemState(R.id.navDrag)
        resetNavItemState(R.id.navGarage)
        resetNavItemState(R.id.navMap)
        resetNavItemState(R.id.navTrack)
        resetNavItemState(R.id.navOptions)
        
        // Highlight активния
        setNavItemActive(activeItemId)
    }
    
    private fun resetNavItemState(itemId: Int) {
        val container = findViewById<LinearLayout>(itemId)
        val iconViewId = when (itemId) {
            R.id.navDrag -> R.id.ivNavDrag
            R.id.navTrack -> R.id.ivNavTrack
            R.id.navMap -> R.id.ivNavMap
            R.id.navGarage -> R.id.ivNavGarage
            R.id.navOptions -> R.id.ivNavOptions
            else -> null
        }
        val textViewId = when (itemId) {
            R.id.navDrag -> R.id.tvNavDrag
            R.id.navTrack -> R.id.tvNavTrack
            R.id.navMap -> R.id.tvNavMap
            R.id.navGarage -> R.id.tvNavGarage
            R.id.navOptions -> R.id.tvNavOptions
            else -> null
        }
        
        iconViewId?.let { container?.findViewById<ImageView>(it)?.apply {
            setColorFilter(ContextCompat.getColor(this@MainContainerActivity, R.color.nav_icon_inactive), android.graphics.PorterDuff.Mode.SRC_IN)
        }}
        
        textViewId?.let { container?.findViewById<TextView>(it)?.apply {
            setTextColor(ContextCompat.getColor(this@MainContainerActivity, R.color.nav_text_inactive))
            setTypeface(null, Typeface.NORMAL)
            // Принудително обновяване на текста за да се покаже изцяло при промяна на ориентацията
            val currentText = text.toString()
            text = ""
            text = currentText
        }}
    }
    
    private fun setNavItemActive(itemId: Int) {
        val container = findViewById<LinearLayout>(itemId)
        val iconViewId = when (itemId) {
            R.id.navDrag -> R.id.ivNavDrag
            R.id.navTrack -> R.id.ivNavTrack
            R.id.navMap -> R.id.ivNavMap
            R.id.navGarage -> R.id.ivNavGarage
            R.id.navOptions -> R.id.ivNavOptions
            else -> null
        }
        val textViewId = when (itemId) {
            R.id.navDrag -> R.id.tvNavDrag
            R.id.navTrack -> R.id.tvNavTrack
            R.id.navMap -> R.id.tvNavMap
            R.id.navGarage -> R.id.tvNavGarage
            R.id.navOptions -> R.id.tvNavOptions
            else -> null
        }
        
        iconViewId?.let { container?.findViewById<ImageView>(it)?.apply {
            setColorFilter(ContextCompat.getColor(this@MainContainerActivity, R.color.primary_color), android.graphics.PorterDuff.Mode.SRC_IN)
        }}
        
        textViewId?.let { container?.findViewById<TextView>(it)?.apply {
            setTextColor(ContextCompat.getColor(this@MainContainerActivity, R.color.primary_color))
            setTypeface(null, Typeface.BOLD)
            // Принудително обновяване на текста за да се покаже изцяло при промяна на ориентацията
            val currentText = text.toString()
            text = ""
            text = currentText
        }}
    }
    
    private fun setupScreenKeepOn() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        updateScreenKeepOn(prefs.getBoolean("always_on_display", false))
        
        prefs.registerOnSharedPreferenceChangeListener { shared, key ->
            if (key == "always_on_display") {
                updateScreenKeepOn(shared.getBoolean(key, false))
            }
        }
    }
    
    private fun updateScreenKeepOn(keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    
    /**
     * Публичен метод за навигация към конкретна страница
     * Използва се от Fragments за да навигират към други страници
     */
    fun navigateToPage(page: Int) {
        if (page in 0..5) { // Поддържаме всички страници включително RACES
            viewPager.setCurrentItem(page, false)
            currentPage = page
            // Ако не е една от стандартните страници (не е в bottom nav), не променяме highlight
            if (page <= PAGE_SETTINGS) {
                highlightActiveNavItem(getItemIdForPage(page))
            }
        }
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Принудително обновяване на текста на всички navigation items при промяна на ориентацията
        refreshNavigationTexts()
        // Обновяване на активния item
        highlightActiveNavItem(getItemIdForPage(currentPage))
    }
    
    override fun onResume() {
        super.onResume()
        // Принудително обновяване на текста на всички navigation items при resume
        refreshNavigationTexts()
        // Обновяване на активния item
        highlightActiveNavItem(getItemIdForPage(currentPage))
    }
    
    private fun refreshNavigationTexts() {
        // Принудително обновяване на текста на всички navigation items
        val navItems = listOf(
            R.id.navDrag to R.id.tvNavDrag,
            R.id.navTrack to R.id.tvNavTrack,
            R.id.navMap to R.id.tvNavMap,
            R.id.navGarage to R.id.tvNavGarage,
            R.id.navOptions to R.id.tvNavOptions
        )
        
        navItems.forEach { (containerId, textViewId) ->
            findViewById<TextView>(textViewId)?.let { textView ->
                val currentText = textView.text.toString()
                if (currentText.isNotEmpty()) {
                    textView.text = ""
                    textView.text = currentText
                }
            }
        }
    }
}

