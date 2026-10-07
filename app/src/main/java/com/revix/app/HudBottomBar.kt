package com.revix.app

import kotlin.math.min

internal object HudBottomBar {
    const val DENSITY_BASE = 360f
    const val HEIGHT_DP = 52f
    const val LINE_DP = 2f

    const val SPEED_TEXT = 0.50f
    const val UNIT_TEXT = 0.16f
    const val LOGO_HEIGHT = 0.64f
    const val STACK_GAP = 0.022f
    const val ACCEL_LABEL = 0.125f
    const val ACCEL_VALUE = 0.40f
    const val ACCEL_UNIT = 0.155f
    const val ACCEL_PROGRESS = 0.10f
    const val NAME_TEXT = 0.20f

    const val BAR_BG = 0xB306080C.toInt()
    const val LINE_COLOR = 0xFF73FFAA.toInt()
    const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    const val TEXT_SECONDARY = 0xFFB0B8C1.toInt()
    const val LABEL_COLOR = 0xFF9CA3AF.toInt()
    const val PRIMARY = 0xFFFF6020.toInt()

    fun density(width: Float, height: Float): Float = min(width, height) / DENSITY_BASE

    fun barHeight(density: Float): Float = HEIGHT_DP * density

    fun lineHeight(density: Float): Float = LINE_DP * density
}
