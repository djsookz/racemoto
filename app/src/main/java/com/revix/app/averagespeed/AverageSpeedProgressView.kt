package com.revix.app.averagespeed

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.revix.app.R

class AverageSpeedProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xCC1A1A1A.toInt()
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.primary_color)
    }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }

    private val trackRect = RectF()
    private val fillRect = RectF()

    var progress: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (clamped != field) {
                field = clamped
                invalidate()
            }
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val horizontal = w >= h
        val thickness = if (horizontal) h else w
        val pad = thickness * 0.18f
        val radius = (thickness - pad * 2f) / 2f
        trackRect.set(pad, pad, w - pad, h - pad)
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        if (horizontal) {
            val fillWidth = trackRect.width() * progress
            fillRect.set(
                trackRect.left,
                trackRect.top,
                trackRect.left + fillWidth,
                trackRect.bottom
            )
            if (fillWidth > 1f) {
                canvas.drawRoundRect(fillRect, radius, radius, fillPaint)
            }
            val capR = radius * 0.72f
            canvas.drawCircle(trackRect.left, trackRect.centerY(), capR, capPaint)
            canvas.drawCircle(trackRect.right, trackRect.centerY(), capR, capPaint)
        } else {
            val fillHeight = trackRect.height() * progress
            fillRect.set(
                trackRect.left,
                trackRect.bottom - fillHeight,
                trackRect.right,
                trackRect.bottom
            )
            if (fillHeight > 1f) {
                canvas.drawRoundRect(fillRect, radius, radius, fillPaint)
            }
            val capR = radius * 0.72f
            canvas.drawCircle(trackRect.centerX(), trackRect.bottom, capR, capPaint)
            canvas.drawCircle(trackRect.centerX(), trackRect.top, capR, capPaint)
        }
    }
}
