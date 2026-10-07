package com.example.clinometer.main

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clinometer.Profile
import com.example.clinometer.R
import com.example.clinometer.data.ProfileStorage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainContainerActivitySmokeTest {

    @Test
    fun launch_withMapPage_andSwitchToTrackPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val profile = Profile(
            id = 1L,
            name = "Test User",
            vehicleType = Profile.VehicleType.CAR
        )
        ProfileStorage.saveProfiles(context, listOf(profile))
        ProfileStorage.saveSelectedProfile(context, profile.id)

        val intent = Intent(context, MainContainerActivity::class.java).apply {
            putExtra(MainContainerActivity.EXTRA_INITIAL_PAGE, MainContainerActivity.PAGE_MAP)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<MainContainerActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val viewPager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                assertEquals(MainContainerActivity.PAGE_MAP, viewPager.currentItem)

                activity.findViewById<android.view.View>(R.id.navTrack).performClick()
            }

            Thread.sleep(300)

            scenario.onActivity { activity ->
                val viewPager = activity.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.viewPager)
                assertEquals(MainContainerActivity.PAGE_TRACK, viewPager.currentItem)
            }
        }
    }
}
