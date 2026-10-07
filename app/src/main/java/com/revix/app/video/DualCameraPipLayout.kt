package com.revix.app.video

import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.revix.app.HudBottomBar
import kotlin.math.min

object DualCameraPipLayout {
    enum class Hud {
        DRAG,
        TRACK
    }

    @Volatile
    var hud: Hud = Hud.TRACK

    private const val SIZE_FRACTION = 0.26f
    private const val MARGIN_FRACTION = 0.04f

    fun pipInDisplay(displayW: Float, displayH: Float): RectF {
        val shortSide = min(displayW, displayH).coerceAtLeast(1f)
        val pip = shortSide * SIZE_FRACTION
        val margin = shortSide * MARGIN_FRACTION
        val left = displayW - margin - pip
        val top = belowSecondContainer(displayW, displayH)
        return RectF(left, top, left + pip, top + pip)
    }

    fun belowSecondContainer(displayW: Float, displayH: Float): Float {
        val density = HudBottomBar.density(displayW, displayH)
        val bar = HudBottomBar.barHeight(density)
        val gap = 8f * density
        val top = when (hud) {
            Hud.DRAG -> {
                val identityH = bar * 0.70f
                val pad = 8f * density
                identityH + pad * 0.22f + pad * 3.85f
            }
            Hud.TRACK -> {
                val portrait = displayH >= displayW
                val identityH = bar * if (portrait) 1.00f else 0.92f
                val timerLabel = bar * 0.16f
                val timerValue = bar * 0.27f
                val timerGap = bar * 0.016f
                val timerPadY = bar * 0.036f
                val timerH = timerPadY * 2f + timerLabel * 1.18f + timerGap + timerValue * 1.18f
                identityH + timerH
            }
        }
        return top + gap
    }

    fun layoutSwapButton(button: View, videoLeft: Float, videoTop: Float, pip: RectF) {
        val size = (min(pip.width(), pip.height()) * 0.20f).toInt().coerceAtLeast(1)
        val pad = (size * 0.16f).toInt()
        button.setPadding(pad, pad, pad, pad)
        val params = button.layoutParams as FrameLayout.LayoutParams
        params.width = size
        params.height = size
        params.gravity = Gravity.TOP or Gravity.START
        val inset = size * 0.10f
        params.leftMargin = (videoLeft + pip.right - size - inset).toInt().coerceAtLeast(0)
        params.topMargin = (videoTop + pip.bottom - size - inset).toInt().coerceAtLeast(0)
        button.layoutParams = params
    }

    fun pipInBuffer(
        crop: Rect,
        rotationDegrees: Int,
        mirrored: Boolean
    ): RectF {
        val cropW = crop.width().toFloat().coerceAtLeast(1f)
        val cropH = crop.height().toFloat().coerceAtLeast(1f)
        val rotated = rotationDegrees % 180 != 0
        val displayW = if (rotated) cropH else cropW
        val displayH = if (rotated) cropW else cropH
        val display = pipInDisplay(displayW, displayH)
        val pts = floatArrayOf(
            display.left, display.top,
            display.right, display.top,
            display.right, display.bottom,
            display.left, display.bottom
        )
        for (i in 0 until 4) {
            var x = pts[i * 2]
            var y = pts[i * 2 + 1]
            if (mirrored) {
                x = displayW - x
            }
            val cropX: Float
            val cropY: Float
            when (((rotationDegrees % 360) + 360) % 360) {
                90 -> {
                    cropX = y
                    cropY = cropH - x
                }
                180 -> {
                    cropX = cropW - x
                    cropY = cropH - y
                }
                270 -> {
                    cropX = cropW - y
                    cropY = x
                }
                else -> {
                    cropX = x
                    cropY = y
                }
            }
            pts[i * 2] = crop.left + cropX
            pts[i * 2 + 1] = crop.top + cropY
        }
        var minX = pts[0]
        var minY = pts[1]
        var maxX = pts[0]
        var maxY = pts[1]
        for (i in 1 until 4) {
            minX = minOf(minX, pts[i * 2])
            minY = minOf(minY, pts[i * 2 + 1])
            maxX = maxOf(maxX, pts[i * 2])
            maxY = maxOf(maxY, pts[i * 2 + 1])
        }
        return RectF(minX, minY, maxX, maxY)
    }
}
