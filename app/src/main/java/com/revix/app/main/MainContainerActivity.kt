package com.revix.app.main

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import com.revix.app.R
import com.revix.app.RacesFragment
import com.revix.app.BatteryOptimizationHelper
import com.revix.app.OptimizationSetupActivity
import com.revix.app.WelcomeActivity
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.revix.app.settings.LanguageManager
import android.graphics.Typeface
import androidx.preference.PreferenceManager
import android.view.WindowManager
import com.revix.app.data.CalibrationReminderStore
import com.revix.app.data.ProfileStorage
import com.revix.app.garage.VehicleSelectionActivity
import com.revix.app.main.map.MapFragment
import com.revix.app.main.navigation.NavigationController
import com.revix.app.main.tour.FeatureTourController
import com.revix.app.main.tour.FeatureTourStore
import com.revix.app.reports.ReportAlertsCoordinator
import com.revix.app.main.state.MainUiStateViewModel
import com.revix.app.update.AppUpdateGate
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
    
    private val uiStateViewModel: MainUiStateViewModel by viewModels()
    private var featureTourController: FeatureTourController? = null
    private var currentPage: Int
        get() = uiStateViewModel.uiState.value.navigation.currentPage
        set(value) {
            uiStateViewModel.setCurrentPage(value)
        }

    private var lastBackPressTime = 0L
    
    companion object {
        const val EXTRA_INITIAL_PAGE = "extra_initial_page"
        const val EXTRA_NAV_ITEM_ID = "extra_nav_item_id"

        fun launchIntent(context: Context): Intent {
            return Intent(context, MainContainerActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            }
        }

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
        
        if (routeToOnboardingIfNeeded()) {
            return
        }
        
        setContentView(R.layout.activity_main_container)

        AppUpdateGate.check(this)
        
        viewPager = findViewById(R.id.viewPager)
        
        // Handle system bars insets - добавяме padding само на ViewPager за да не покрива navigation bar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(viewPager) { v, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.setPadding(
                maxOf(systemInsets.left, cutoutInsets.left),
                maxOf(systemInsets.top, cutoutInsets.top),
                maxOf(systemInsets.right, cutoutInsets.right),
                maxOf(systemInsets.bottom, cutoutInsets.bottom)
            )
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
        updateSettingsReminderBadge()
        
        android.util.Log.d("MainContainerActivity", "✅ Navigation items loaded successfully")
        
        // Създаваме ViewPager2 adapter - Fragments се създават динамично
        viewPager.adapter = MainPagerAdapter(this)

        // Keep all main tabs alive so MapFragment (and its MapView) is not destroyed when
        // switching Garage/Settings/etc. — avoids globe flash + full map reinits.
        viewPager.offscreenPageLimit = 5
        
        // Изключваме user input за swipe (само bottom navigation работи)
        viewPager.isUserInputEnabled = false
        
        val initialPage = NavigationController.resolveInitialPage(intent)
        viewPager.setCurrentItem(initialPage, false)
        currentPage = initialPage
        highlightActiveNavItem(getItemIdForPage(initialPage))
        updateRequestedOrientationForPage(initialPage)
        if (initialPage == PAGE_MAP) {
            ReportAlertsCoordinator.onMapTabSelected(this)
        } else {
            ReportAlertsCoordinator.onMapTabHidden(this)
        }
        
        // Setup bottom navigation
        setupBottomNavigation()

        maybeStartFeatureTour()
        
        // Използваме OnBackPressedDispatcher вместо onBackPressed() за да позволим на Fragments да обработват back първо
        // Важно: Fragment callbacks се изпълняват ПРЕДИ Activity callbacks, защото се регистрират по-късно
        // Така че ако Fragment callback-ът е enabled и се изпълни, той ще спре цепочката
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (featureTourController?.isRunning() == true) {
                    featureTourController?.cancel()
                    return
                }
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
                    ReportAlertsCoordinator.disarmAll(this@MainContainerActivity)
                    finishAffinity()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(this@MainContainerActivity, getString(R.string.back_press_exit), Toast.LENGTH_SHORT).show()
                }
            }
        })
        
        // Listener за ViewPager промени (ако някой промени страницата програмно)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPage = position
                highlightActiveNavItem(getItemIdForPage(position))
                updateRequestedOrientationForPage(position)
                if (position == PAGE_MAP) {
                    ReportAlertsCoordinator.onMapTabSelected(this@MainContainerActivity)
                } else {
                    ReportAlertsCoordinator.onMapTabHidden(this@MainContainerActivity)
                }
                if (position == PAGE_RACES) {
                    reloadRacesFragment()
                }
            }
        })
    }

    private fun updateRequestedOrientationForPage(page: Int) {
        requestedOrientation = when (page) {
            PAGE_TRACK, PAGE_DRAG, PAGE_RACES -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        relayoutBottomNavLabels()
    }
    
    private fun getItemIdForPage(page: Int): Int {
        return NavigationController.pageToNavItemId(page)
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
        updateSettingsReminderBadge()
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
        }}
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
            if (page == PAGE_RACES) {
                reloadRacesFragment()
            }
        }
    }

    private fun maybeStartFeatureTour() {
        if (!FeatureTourStore.shouldShow(this)) return
        featureTourController = FeatureTourController(this)
        // Wait for MapFragment + idle buttons to finish first layout.
        viewPager.postDelayed({
            if (!isFinishing) {
                featureTourController?.startIfNeeded()
            }
        }, 900L)
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        highlightActiveNavItem(getItemIdForPage(currentPage))
        relayoutBottomNavLabels()
    }
    
    override fun onResume() {
        super.onResume()
        AppUpdateGate.check(this)
        highlightActiveNavItem(getItemIdForPage(currentPage))
        relayoutBottomNavLabels()
        if (::navOptions.isInitialized) {
            updateSettingsReminderBadge()
        }
        com.revix.app.billing.ProAccess.reconcile(this)
        com.revix.app.billing.PlayBillingManager.refreshEntitlements(this) {
            if (!isFinishing) {
                com.revix.app.billing.FreeProfilePickerCoordinator.promptIfNeeded(this)
            }
        }
        com.revix.app.billing.FreeProfilePickerCoordinator.promptIfNeeded(this)
    }
    
    private fun relayoutBottomNavLabels() {
        val container = findViewById<View>(R.id.bottomNavigationContainer) ?: return
        container.post {
            container.requestLayout()
            listOf(
                R.id.tvNavDrag,
                R.id.tvNavTrack,
                R.id.tvNavMap,
                R.id.tvNavGarage,
                R.id.tvNavOptions
            ).forEach { textViewId ->
                findViewById<TextView>(textViewId)?.requestLayout()
            }
        }
    }

    private fun routeToOnboardingIfNeeded(): Boolean {
        if (ProfileStorage.loadProfiles(this).isNotEmpty()) {
            return false
        }

        val intent = when {
            WelcomeActivity.needsOnboardingPermissionFlow(this) -> Intent(this, WelcomeActivity::class.java)
            BatteryOptimizationHelper.shouldShowOptimizationSetup(this) -> {
                Intent(this, OptimizationSetupActivity::class.java).apply {
                    putExtra("IS_FIRST_LAUNCH", true)
                }
            }
            else -> {
                Intent(this, VehicleSelectionActivity::class.java).apply {
                    putExtra("IS_FIRST_LAUNCH", true)
                }
            }
        }

        startActivity(intent)
        finish()
        return true
    }

    private fun updateSettingsReminderBadge() {
        findViewById<TextView>(R.id.tvNavOptionsBadge)?.visibility =
            if (CalibrationReminderStore.needsSelectedProfileDragCalibrationReminder(this)) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isFinishing || !::viewPager.isInitialized) return

        val page = NavigationController.resolveInitialPage(intent)
        if (page != currentPage) {
            viewPager.setCurrentItem(page, false)
        }
        if (page == PAGE_RACES) {
            reloadRacesFragment()
        }
        ReportAlertsCoordinator.onAppReturnedToForeground(this)
    }

    private fun reloadRacesFragment() {
        val racesFragment = supportFragmentManager.fragments.find { it is RacesFragment } as? RacesFragment
        racesFragment?.reloadSessionsFromStorage()
    }

    override fun onPause() {
        if (!isChangingConfigurations) {
            if (isFinishing) {
                ReportAlertsCoordinator.disarmAll(this)
            } else {
                ReportAlertsCoordinator.onAppMovedToBackground(this)
            }
        }
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        if (!isFinishing) {
            ReportAlertsCoordinator.onAppReturnedToForeground(this)
        }
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        ReportAlertsCoordinator.disarmAll(this)
        super.onDestroy()
    }
}

