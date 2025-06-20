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
        strokeWidth = 20f
        color = Color.LTGRAY
    }
    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 20f
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.DKGRAY
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
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
        val radius = min(w, h) / 2 * 0.8f
        arcRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        val centerY = h / 2f
        val radius = min(w, h) / 2 * 0.8f

        if (arcRect.isEmpty) {
            arcRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        }

        // Нарисуваме фон-дуга
        canvas.drawArc(arcRect, 180f + (180f - 140f) / 2, 140f, false, bgPaint)

        // Нарисуваме активната дъга
        val sweep = angle.coerceIn(-70f, 70f)
        val fraction = abs(sweep) / 70f
        val red = (255 * fraction).toInt().coerceIn(0, 255)
        val green = (255 * (1 - fraction)).toInt().coerceIn(0, 255)
        fgPaint.color = Color.rgb(red, green, 0)
        canvas.drawArc(arcRect, 270f, sweep, false, fgPaint)

        // Нарисуваме деления на всеки 10° и цифри
        for (tick in -70..70 step 10) {
            val angleDeg = 180f + (tick + 90f)
            val rad = Math.toRadians(angleDeg.toDouble())
            // къси маркировки
            val xStart = (centerX + cos(rad) * radius).toFloat()
            val yStart = (centerY + sin(rad) * radius).toFloat()
            val xEnd   = (centerX + cos(rad) * (radius - 20)).toFloat()
            val yEnd   = (centerY + sin(rad) * (radius - 20)).toFloat()
            canvas.drawLine(xStart, yStart, xEnd, yEnd, tickPaint)

            // цифрите малко по-навън
            val xText = (centerX + cos(rad) * (radius - 50)).toFloat()
            val yText = (centerY + sin(rad) * (radius - 50) + textPaint.textSize / 3).toFloat()
            canvas.drawText("${tick}", xText, yText, textPaint)
        }

        // Нарисуваме маркери за историческите максимуми
        // Ляв максимум
        if (maxLeftAngle < 0f) {
            val deg = 180f + (maxLeftAngle.coerceIn(-70f, 0f) + 90f)
            val r = Math.toRadians(deg.toDouble())
            val x1 = (centerX + cos(r) * radius).toFloat()
            val y1 = (centerY + sin(r) * radius).toFloat()
            val x2 = (centerX + cos(r) * (radius - 30)).toFloat()
            val y2 = (centerY + sin(r) * (radius - 30)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, markerPaint)
        }
        // Десен максимум
        if (maxRightAngle > 0f) {
            val deg = 180f + (maxRightAngle.coerceIn(0f, 70f) + 90f)
            val r = Math.toRadians(deg.toDouble())
            val x1 = (centerX + cos(r) * radius).toFloat()
            val y1 = (centerY + sin(r) * radius).toFloat()
            val x2 = (centerX + cos(r) * (radius - 30)).toFloat()
            val y2 = (centerY + sin(r) * (radius - 30)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, markerPaint)
        }
    }
}