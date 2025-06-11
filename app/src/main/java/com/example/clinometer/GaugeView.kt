package com.example.clinometer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.min

class GaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var angle: Float = 0f

    private val arcRect = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
        color = Color.LTGRAY
    }
    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = min(w, h) / 2 * 0.8f
        val cx = w / 2
        val cy = h / 2
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(arcRect, 180f, 180f, false, bgPaint)
        val sweep = (angle.coerceIn(-90f, 90f) / 90f) * 90f
        val fraction = abs(angle) / 90f
        val red = (255 * fraction).toInt().coerceIn(0, 255)
        val green = (255 * (1 - fraction)).toInt().coerceIn(0, 255)
        fgPaint.color = Color.rgb(red, green, 0)
        canvas.drawArc(arcRect, 270f, sweep, false, fgPaint)
    }
}