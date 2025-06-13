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
            // Обновяваме максимумите
            if (value < maxLeftAngle)  maxLeftAngle = value
            if (value > maxRightAngle) maxRightAngle = value
            invalidate()
        }

    // Запазваме пиковете
    private var maxLeftAngle = 0f
    private var maxRightAngle = 0f

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

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // Радиус за стрелките и цифрите
        val radius = min(w, h) / 2 * 0.8f
        val cx = w / 2
        val cy = h / 2
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        // Нарисуваме фон-дуга
        canvas.drawArc(arcRect, 180f + (180f - 140f) / 2, 140f, false, bgPaint)

        // Нарисуваме активната дъга
        val sweep = (angle.coerceIn(-70f, 70f) / 70f) * 70f
        val fraction = abs(angle.coerceIn(-70f, 70f)) / 70f
        val red = (255 * fraction).toInt().coerceIn(0, 255)
        val green = (255 * (1 - fraction)).toInt().coerceIn(0, 255)
        fgPaint.color = Color.rgb(red, green, 0)
        canvas.drawArc(arcRect, 270f, sweep, false, fgPaint)

        // Нарисуваме деления на всеки 10° и цифри
        for (tick in -70..70 step 10) {
            val angleDeg = 180f + (tick + 90f)
            val rad = Math.toRadians(angleDeg.toDouble())
            // къси маркировки
            val xStart = (cx + cos(rad) * radius).toFloat()
            val yStart = (cy + sin(rad) * radius).toFloat()
            val xEnd   = (cx + cos(rad) * (radius - 20)).toFloat()
            val yEnd   = (cy + sin(rad) * (radius - 20)).toFloat()
            canvas.drawLine(xStart, yStart, xEnd, yEnd, tickPaint)

            // цифрите малко по-навън
            val xText = (cx + cos(rad) * (radius - 50)).toFloat()
            val yText = (cy + sin(rad) * (radius - 50) + textPaint.textSize / 3).toFloat()
            canvas.drawText("${tick}", xText, yText, textPaint)

        }

        // Нарисуваме маркери за историческите максимуми
        // Ляв максимум
        if (maxLeftAngle < 0f) {
            val deg = 180f + (maxLeftAngle.coerceIn(-70f, 0f) + 90f)
            val r = Math.toRadians(deg.toDouble())
            val x1 = (cx + cos(r) * radius).toFloat()
            val y1 = (cy + sin(r) * radius).toFloat()
            val x2 = (cx + cos(r) * (radius - 30)).toFloat()
            val y2 = (cy + sin(r) * (radius - 30)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, markerPaint)
        }
        // Десен максимум
        if (maxRightAngle > 0f) {
            val deg = 180f + (maxRightAngle.coerceIn(0f, 70f) + 90f)
            val r = Math.toRadians(deg.toDouble())
            val x1 = (cx + cos(r) * radius).toFloat()
            val y1 = (cy + sin(r) * radius).toFloat()
            val x2 = (cx + cos(r) * (radius - 30)).toFloat()
            val y2 = (cy + sin(r) * (radius - 30)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, markerPaint)
        }
    }
}
