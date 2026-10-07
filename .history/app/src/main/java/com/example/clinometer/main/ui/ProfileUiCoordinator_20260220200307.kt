package com.example.clinometer.main.ui

import android.view.View
import com.example.clinometer.R

object ProfileUiCoordinator {

    fun apply(
        isMotorcycle: Boolean,
        isLandscape: Boolean,
        isNavigationActive: Boolean,
        carModeContainer: View?,
        dashboardContainer: View?,
        speedOverlayContainer: View,
        angleContainerMoto: View?,
        angleTextMoto: View?,
        linearGaugeView: View?,
        zeroButton: View?,
        speedometerBackground: View?,
        gaugeView: View?,
        currentAngleText: View?,
        speedText: View?,
        chronometer: View?,
        distanceContainer: View?,
        contentArea: View?,
        tripProgressContainer: View?,
        maneuverContainer: View?,
        maneuverViewContainer: View?,
        zeroButtonOverlay: View?,
        angleLandscapeContainer: View?
    ) {
        if (!isLandscape) {
            if (isMotorcycle) {
                carModeContainer?.visibility = View.VISIBLE
                carModeContainer?.setBackgroundResource(R.drawable.bg_car_mode_stats)
                dashboardContainer?.visibility = View.VISIBLE
                speedOverlayContainer.visibility = View.VISIBLE
                angleContainerMoto?.visibility = View.VISIBLE
                angleTextMoto?.visibility = View.VISIBLE
                linearGaugeView?.visibility = View.VISIBLE
                zeroButton?.visibility = View.VISIBLE

                speedometerBackground?.visibility = View.GONE
                gaugeView?.visibility = View.GONE
                currentAngleText?.visibility = View.GONE
                speedText?.visibility = View.GONE
                chronometer?.visibility = View.GONE
                distanceContainer?.visibility = View.GONE
                contentArea?.visibility = View.GONE

                tripProgressContainer?.visibility = if (isNavigationActive) View.VISIBLE else View.GONE
            } else {
                carModeContainer?.visibility = View.VISIBLE
                carModeContainer?.setBackgroundResource(R.drawable.bg_car_mode_stats)
                dashboardContainer?.visibility = View.VISIBLE
                speedOverlayContainer.visibility = View.VISIBLE
                zeroButton?.visibility = View.GONE

                speedometerBackground?.visibility = View.GONE
                gaugeView?.visibility = View.GONE
                currentAngleText?.visibility = View.GONE
                speedText?.visibility = View.GONE
                chronometer?.visibility = View.GONE
                distanceContainer?.visibility = View.GONE
                contentArea?.visibility = View.GONE
                angleContainerMoto?.visibility = View.GONE
                angleTextMoto?.visibility = View.GONE
                linearGaugeView?.visibility = View.GONE

                tripProgressContainer?.visibility = if (isNavigationActive) View.VISIBLE else View.GONE
            }
            return
        }

        tripProgressContainer?.visibility = if (isNavigationActive) View.VISIBLE else View.GONE
        maneuverContainer?.visibility = if (isNavigationActive) View.VISIBLE else View.GONE
        maneuverViewContainer?.visibility = if (isNavigationActive) View.VISIBLE else View.GONE

        if (!isNavigationActive) {
            maneuverContainer?.setBackgroundResource(0)
        } else {
            maneuverContainer?.setBackgroundResource(R.drawable.bg_map_controls_pill)
        }

        if (!isMotorcycle) {
            zeroButtonOverlay?.visibility = View.GONE
            angleLandscapeContainer?.visibility = View.GONE
        } else {
            zeroButtonOverlay?.visibility = View.VISIBLE
            angleLandscapeContainer?.visibility = View.VISIBLE
        }
    }
}
