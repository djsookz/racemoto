package com.example.clinometer.main.navigation

import android.content.Intent
import com.example.clinometer.R
import com.example.clinometer.main.MainContainerActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationControllerTest {

    @Test
    fun resolveInitialPage_prefersTypedExtra() {
        val intent = Intent().apply {
            putExtra(MainContainerActivity.EXTRA_INITIAL_PAGE, MainContainerActivity.PAGE_TRACK)
        }

        val page = NavigationController.resolveInitialPage(intent)

        assertEquals(MainContainerActivity.PAGE_TRACK, page)
    }

    @Test
    fun resolveInitialPage_supportsLegacyNavItem() {
        val intent = Intent().apply {
            putExtra("NAV_ITEM_ID", R.id.navDrag)
        }

        val page = NavigationController.resolveInitialPage(intent)

        assertEquals(MainContainerActivity.PAGE_DRAG, page)
    }

    @Test
    fun pageAndNavMapping_areConsistent() {
        val navId = NavigationController.pageToNavItemId(MainContainerActivity.PAGE_GARAGE)
        val page = NavigationController.navItemIdToPage(navId)

        assertEquals(MainContainerActivity.PAGE_GARAGE, page)
    }
}
