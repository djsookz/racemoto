package com.example.clinometer.main.ui

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import com.example.clinometer.R
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object MainWindowInsetsApplier {

    fun apply(
        rootContainer: View,
        resources: Resources,
        mapControlsContainer: View,
        bottomHudRow: View,
        carStatsOverlay: View?,
        maneuverContainer: View?,
        carActionButtonsOverlay: View?,
        buttonContainer: View?,
        baseMapControlsMarginTop: Int,
        baseMapControlsMarginEnd: Int,
        baseButtonContainerPaddingLeft: Int,
        baseButtonContainerPaddingRight: Int,
        baseButtonContainerPaddingBottom: Int
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

            (mapControlsContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.topMargin = baseMapControlsMarginTop + systemBars.top
                lp.marginEnd = baseMapControlsMarginEnd + systemBars.right
                mapControlsContainer.layoutParams = lp
            }

            val baseBottomHudMarginStart = resources.getDimensionPixelSize(
                if (isLandscape) R.dimen.hud_bottom_row_margin_start_landscape else R.dimen.hud_bottom_row_margin_start_portrait
            )
            (bottomHudRow.layoutParams as? ViewGroup.MarginLayoutParams)?.let { mlp ->
                mlp.marginStart = baseBottomHudMarginStart + systemBars.left
                mlp.marginEnd = systemBars.right
                bottomHudRow.layoutParams = mlp
            }

            val basePaddingEnd = resources.getDimensionPixelSize(
                if (isLandscape) R.dimen.hud_bottom_row_padding_end_landscape else R.dimen.hud_bottom_row_padding_end_portrait
            )
            bottomHudRow.setPadding(
                bottomHudRow.paddingStart,
                bottomHudRow.paddingTop,
                basePaddingEnd + systemBars.right,
                bottomHudRow.paddingBottom
            )

            if (carStatsOverlay?.visibility == View.VISIBLE) {
                (carStatsOverlay.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    val baseCarStatsTopMargin = resources.getDimensionPixelSize(
                        if (isLandscape) R.dimen.hud_car_stats_margin_top_landscape else R.dimen.hud_car_stats_margin_top_portrait
                    )
                    val baseCarStatsStartMargin = resources.getDimensionPixelSize(
                        if (isLandscape) R.dimen.hud_car_stats_margin_start_landscape else R.dimen.hud_car_stats_margin_start_portrait
                    )
                    lp.topMargin = baseCarStatsTopMargin + systemBars.top
                    lp.marginStart = baseCarStatsStartMargin + systemBars.left
                    carStatsOverlay.layoutParams = lp
                }
            }

            if (maneuverContainer?.visibility == View.VISIBLE) {
                (maneuverContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    val baseManeuverMargin = resources.getDimensionPixelSize(
                        if (isLandscape) R.dimen.hud_maneuver_margin_start_landscape else R.dimen.hud_maneuver_margin_start_portrait
                    )
                    lp.marginStart = baseManeuverMargin + systemBars.left
                    maneuverContainer.layoutParams = lp
                }
            }

            carActionButtonsOverlay?.let { overlay ->
                if (overlay.visibility == View.VISIBLE) {
                    (overlay.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                        lp.bottomMargin = systemBars.bottom
                        lp.marginEnd = systemBars.right
                        overlay.layoutParams = lp
                    }
                }
            }

            buttonContainer?.let {
                it.setPadding(
                    baseButtonContainerPaddingLeft + systemBars.left,
                    it.paddingTop,
                    baseButtonContainerPaddingRight + systemBars.right,
                    baseButtonContainerPaddingBottom + systemBars.bottom
                )
            }

            insets
        }
    }
}
