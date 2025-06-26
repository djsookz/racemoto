package com.example.clinometer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class GaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var angle: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var maxLeftAngle: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var maxRightAngle: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val arcRect = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 28f
        color = Color.parseColor("#b1a7a6")
    }
    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 28f
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.RED
    }

    /** Нулира максималните маркери */
    fun resetMaxima() {
        maxLeftAngle = 0f
        maxRightAngle = 0f
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val centerX = w / 2f
        val centerY = h / 2f

        val minDimension = min(w, h).toFloat()
        val strokeWidth = bgPaint.strokeWidth
        val radius = minDimension / 2f - strokeWidth

        arcRect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        val centerY = h / 2f
        val currentRadius = arcRect.width() / 2

        if (arcRect.isEmpty) {
            arcRect.set(centerX - currentRadius, centerY - currentRadius, centerX + currentRadius, centerY + currentRadius)
        }

        // Нарисуваме фон-дуга
        canvas.drawArc(arcRect, 180f, 180f, false, bgPaint)

        // Нарисуваме активната дъга
        val sweep = angle.coerceIn(-90f, 90f)
        val fraction = abs(sweep) / 90f
        val red = (255 * fraction).toInt().coerceIn(0, 255)
        val green = (255 * (1 - fraction)).toInt().coerceIn(0, 255)

        fgPaint.color = Color.rgb(red, green, 0)
        canvas.drawArc(arcRect, 270f, sweep, false, fgPaint)

        // Нарисуваме маркери за историческите максимуми
        // Ляв максимум
        if (maxLeftAngle < 0f) {
            val deg = 270f + maxLeftAngle.coerceIn(-90f, 0f)
            val r = Math.toRadians(deg.toDouble())
            val x1 = (centerX + cos(r) * currentRadius).toFloat()
            val y1 = (centerY + sin(r) * currentRadius).toFloat()
            val x2 = (centerX + cos(r) * (currentRadius - 28)).toFloat()
            val y2 = (centerY + sin(r) * (currentRadius - 28)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, markerPaint)
        }
        // Десен максимум
        if (maxRightAngle > 0f) {
            val deg = 270f + maxRightAngle.coerceIn(0f, 90f)
            val r = Math.toRadians(deg.toDouble())
            val x1 = (centerX + cos(r) * currentRadius).toFloat()
            val y1 = (centerY + sin(r) * currentRadius).toFloat()
            val x2 = (centerX + cos(r) * (currentRadius - 28)).toFloat()
            val y2 = (centerY + sin(r) * (currentRadius - 28)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, markerPaint)
        }
    }
}