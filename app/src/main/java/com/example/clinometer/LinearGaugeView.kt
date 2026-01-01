package com.example.clinometer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.animation.ValueAnimator
import kotlin.math.*

class LinearGaugeView @JvmOverloads constructor(
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

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        color = Color.parseColor("#66ADB5BD")  // По-ярък фон
        strokeCap = Paint.Cap.ROUND
    }
    
    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.RED
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    /** Нулира максималните маркери */
    fun resetMaxima() {
        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                maxLeftAngle = originalMaxLeft * fraction
                maxRightAngle = originalMaxRight * fraction
                invalidate()
            }
        }
        animator.start()
    }

    private var originalMaxLeft = 0f
    private var originalMaxRight = 0f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        val centerY = h / 2f
        val lineLength = w * 0.9f // 90% от ширината
        
        val startX = centerX - lineLength / 2f
        val endX = centerX + lineLength / 2f
        val y = centerY

        // Нарисуваме фон-линия
        canvas.drawLine(startX, y, endX, y, bgPaint)

        // Изчисляваме текущия ъгъл (-90 до +90 градуса)
        val currentAngle = angle.coerceIn(-90f, 90f)
        
        // Преобразуваме ъгъла в позиция на линията (0 = вляво, 1 = вдясно)
        // 0° = център, -90° = най-ляво, +90° = най-дясно
        val normalizedPosition = (currentAngle + 90f) / 180f // 0 до 1
        val currentX = startX + (lineLength * normalizedPosition)

        // Изчисляваме цвета (зелено в центъра, жълто към краищата, червено в крайностите)
        val fraction = abs(currentAngle) / 90f
        val red = (255 * min(fraction, 1f)).toInt().coerceIn(0, 255)
        val green = (255 * (1f - fraction * 0.7f)).toInt().coerceIn(0, 255)
        val blue = 0
        
        fgPaint.color = Color.rgb(red, green, blue)
        
        // Нарисуваме активната част на линията (от центъра до текущата позиция)
        val centerPosition = startX + lineLength / 2f
        if (currentAngle >= 0) {
            // Надясно от центъра
            canvas.drawLine(centerPosition, y, currentX, y, fgPaint)
        } else {
            // Наляво от центъра
            canvas.drawLine(currentX, y, centerPosition, y, fgPaint)
        }

        // Нарисуваме център маркер (малък бял кръг) - по-голям за по-добра видимост
        canvas.drawCircle(centerX, y, 5f, centerPaint)

        // Нарисуваме маркери за историческите максимуми
        // Ляв максимум
        if (abs(maxLeftAngle) > 0.1f && maxLeftAngle != 0f) {
            val leftPosition = (maxLeftAngle + 90f) / 180f
            val markerX = startX + (lineLength * leftPosition)
            canvas.drawCircle(markerX, y, 6f, markerPaint)
        }

        // Десен максимум
        if (abs(maxRightAngle) > 0.1f && maxRightAngle != 0f) {
            val rightPosition = (maxRightAngle + 90f) / 180f
            val markerX = startX + (lineLength * rightPosition)
            canvas.drawCircle(markerX, y, 6f, markerPaint)
        }
    }
}

