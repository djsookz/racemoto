package com.revix.app.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff

/**
 * HUD-only MP4 cannot keep real alpha, so we render two clips:
 * fill (HUD over black) and a grayscale matte from the original alpha.
 */
internal class HudOffscreenPass {
    enum class Mode {
        OVERLAY,
        FILL,
        MATTE
    }

    private var buffer: Bitmap? = null
    private val bufferCanvas = Canvas()
    private val mattePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    0f, 0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 0f, 255f
                )
            )
        )
    }

    fun draw(
        target: Canvas,
        mode: Mode,
        width: Float,
        height: Float,
        content: (Canvas) -> Unit
    ) {
        when (mode) {
            Mode.OVERLAY -> {
                target.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                content(target)
            }
            Mode.FILL, Mode.MATTE -> {
                val w = width.toInt().coerceAtLeast(1)
                val h = height.toInt().coerceAtLeast(1)
                val bmp = obtain(w, h)
                bmp.eraseColor(Color.TRANSPARENT)
                bufferCanvas.setBitmap(bmp)
                content(bufferCanvas)
                bufferCanvas.setBitmap(null)
                target.drawColor(Color.BLACK)
                target.drawBitmap(bmp, 0f, 0f, if (mode == Mode.MATTE) mattePaint else null)
            }
        }
    }

    fun release() {
        buffer?.recycle()
        buffer = null
    }

    private fun obtain(width: Int, height: Int): Bitmap {
        val current = buffer
        if (current != null && current.width == width && current.height == height && !current.isRecycled) {
            return current
        }
        current?.recycle()
        val created = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        buffer = created
        return created
    }
}
