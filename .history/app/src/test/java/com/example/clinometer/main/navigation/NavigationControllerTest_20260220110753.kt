package com.example.clinometer.main.navigation

import com.example.clinometer.R
import com.example.clinometer.main.MainContainerActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationControllerTest {

    @Test
    fun navItemIdToPage_mapsTrack() {
        val page = NavigationController.navItemIdToPage(R.id.navTrack)
        assertEquals(MainContainerActivity.PAGE_TRACK, page)
    }

    @Test
    fun navItemIdToPage_mapsDrag() {
        val page = NavigationController.navItemIdToPage(R.id.navDrag)
        assertEquals(MainContainerActivity.PAGE_DRAG, page)
    }

    @Test
    fun pageAndNavMapping_areConsistent() {
        val navId = NavigationController.pageToNavItemId(MainContainerActivity.PAGE_GARAGE)
        val page = NavigationController.navItemIdToPage(navId)

        assertEquals(MainContainerActivity.PAGE_GARAGE, page)
    }
}
