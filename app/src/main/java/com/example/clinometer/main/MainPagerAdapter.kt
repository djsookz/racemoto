package com.example.clinometer.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.clinometer.DragFragment
import com.example.clinometer.GarageFragment
import com.example.clinometer.RacesFragment
import com.example.clinometer.SettingsFragment
import com.example.clinometer.TrackFragment
import com.example.clinometer.main.map.MapFragment

/**
 * Adapter за ViewPager2 който държи всички основни Fragments
 * ViewPager2 автоматично кешира Fragments в паметта за instant navigation
 */
class MainPagerAdapter(
    fragmentActivity: FragmentActivity
) : FragmentStateAdapter(fragmentActivity) {
    
    override fun getItemCount(): Int = 6 // Map, Track, Drag, Garage, Settings, Races
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> MapFragment()
            1 -> TrackFragment()
            2 -> DragFragment()
            3 -> GarageFragment()
            4 -> SettingsFragment()
            5 -> RacesFragment()
            else -> MapFragment()
        }
    }
}

