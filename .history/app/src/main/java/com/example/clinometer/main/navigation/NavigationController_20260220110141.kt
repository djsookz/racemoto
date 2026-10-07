package com.example.clinometer.main.navigation

import android.content.Intent
import com.example.clinometer.R
import com.example.clinometer.main.MainContainerActivity

object NavigationController {
    fun resolveInitialPage(intent: Intent): Int {
        return if (intent.hasExtra(MainContainerActivity.EXTRA_INITIAL_PAGE) || intent.hasExtra("INITIAL_PAGE")) {
            intent.getIntExtra(
                MainContainerActivity.EXTRA_INITIAL_PAGE,
                intent.getIntExtra("INITIAL_PAGE", MainContainerActivity.PAGE_MAP)
            )
        } else if (intent.hasExtra(MainContainerActivity.EXTRA_NAV_ITEM_ID) || intent.hasExtra("NAV_ITEM_ID")) {
            val navItemId = intent.getIntExtra(
                MainContainerActivity.EXTRA_NAV_ITEM_ID,
                intent.getIntExtra("NAV_ITEM_ID", R.id.navMap)
            )
            navItemIdToPage(navItemId)
        } else {
            MainContainerActivity.PAGE_MAP
        }
    }

    fun pageToNavItemId(page: Int): Int {
        return when (page) {
            MainContainerActivity.PAGE_MAP -> R.id.navMap
            MainContainerActivity.PAGE_TRACK -> R.id.navTrack
            MainContainerActivity.PAGE_DRAG -> R.id.navDrag
            MainContainerActivity.PAGE_GARAGE -> R.id.navGarage
            MainContainerActivity.PAGE_SETTINGS -> R.id.navOptions
            else -> R.id.navMap
        }
    }

    fun navItemIdToPage(navItemId: Int): Int {
        return when (navItemId) {
            R.id.navMap -> MainContainerActivity.PAGE_MAP
            R.id.navTrack -> MainContainerActivity.PAGE_TRACK
            R.id.navDrag -> MainContainerActivity.PAGE_DRAG
            R.id.navGarage -> MainContainerActivity.PAGE_GARAGE
            R.id.navOptions -> MainContainerActivity.PAGE_SETTINGS
            else -> MainContainerActivity.PAGE_MAP
        }
    }
}
