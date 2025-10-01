package com.example.clinometer.sparkline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.clinometer.R

class SimpleSparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val values = mutableListOf<Float>()
    private val timestamps = mutableListOf<Float>() // Време в секунди

    private var maxXValue: Float? = null // Фиксирана максимална X стойност
    
    // Маркери за ключови точки
    private val markers = mutableListOf<GraphMarker>()
    
    data class GraphMarker(
        val value: Float,
        val timestamp: Float,
        val color: Int,
        val label: String
    )
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.accent_blue)
    }

    fun addPoint(value: Float, timestamp: Float) {
        values.add(value)
        timestamps.add(timestamp)
        if (values.size > 200) {
            values.removeAt(0)
            timestamps.removeAt(0)
        }
        invalidate()
    }

    fun setMaxX(maxX: Float) {
        maxXValue = maxX
        invalidate()
    }

    fun clear() {
        values.clear()
        timestamps.clear()
        markers.clear()
        maxXValue = null
        invalidate()
    }
    
    fun addMarker(value: Float, timestamp: Float, color: Int, label: String) {
        markers.add(GraphMarker(value, timestamp, color, label))
        invalidate()
    }
    
    fun clearMarkers() {
        markers.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (values.isEmpty() || timestamps.isEmpty()) {
            // Draw placeholder
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.text_secondary)
                textSize = 24f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Няма данни", width / 2f, height / 2f, textPaint)
            return
        }

        val width = width.toFloat()
        val height = height.toFloat()
        val padding = 60f // Повече padding за заглавията
        val graphWidth = width - 2 * padding
        val graphHeight = height - 2 * padding

        // Намираме min/max стойности за Y оста
        val maxValue = values.maxOrNull() ?: 1f
        val minValue = values.minOrNull() ?: 0f
        val valueRange = (maxValue - minValue).coerceAtLeast(0.1f)

        // Намираме времевия диапазон за X оста
        val actualMaxTime = timestamps.maxOrNull() ?: 1f
        val minTime = 0f // Винаги започваме от 0

        // Използваме фиксираната максимална X стойност ако е зададена
        val maxTime = maxXValue ?: actualMaxTime
        val timeRange = (maxTime - minTime).coerceAtLeast(0.1f)

        // Премахваме заглавията на осите за професионален вид

        // Рисуваме осите
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.divider_gray)
            strokeWidth = 5f
        }

        canvas.drawLine(padding, height - padding, width - padding, height - padding, axisPaint)
        canvas.drawLine(padding, padding, padding, height - padding, axisPaint)

        // Рисуваме стойности по осите
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.white)
            textSize = 28f
        }

        // Y-axis labels
        val yStep = valueRange / 4
        for (i in 0..4) {
            val value = minValue + i * yStep
            val y = height - padding - (i * graphHeight / 4)
            canvas.drawText(String.format("%.1f", value), padding - 50f, y + 10f, labelPaint)
        }

        // X-axis labels (време) - използваме фиксирания диапазон
        val xStep = timeRange / 4
        for (i in 0..4) {
            val time = minTime + i * xStep
            val x = padding + (i * graphWidth / 4)
            canvas.drawText(String.format("%.1f", time), x - 20f, height - padding + 35f, labelPaint)
        }

        // Рисуваме графиката
        path.reset()

        // Филтрираме точките които са в рамките на максималното време
        val validPoints = values.zip(timestamps).filter { (_, timestamp) ->
            timestamp <= maxTime
        }

        if (validPoints.isNotEmpty()) {
            for ((index, pair) in validPoints.withIndex()) {
                val (value, timestamp) = pair
                val x = padding + ((timestamp - minTime) / timeRange * graphWidth)
                val y = height - padding - ((value - minValue) / valueRange * graphHeight)

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            canvas.drawPath(path, paint)
        }

        // Рисуваме маркерите за ключови точки
        drawMarkers(canvas, padding, height, graphWidth, graphHeight, minValue, valueRange, minTime, timeRange)
        
        // Добавяме маркер за края на измерването ако е зададен maxXValue
        if (maxXValue != null && actualMaxTime < maxTime) {
            val endLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.accent_red)
                strokeWidth = 2f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }

            val endX = padding + ((actualMaxTime - minTime) / timeRange * graphWidth)
            canvas.drawLine(endX, padding, endX, height - padding, endLinePaint)
        }
    }
    
    private fun drawMarkers(
        canvas: Canvas,
        padding: Float,
        height: Float,
        graphWidth: Float,
        graphHeight: Float,
        minValue: Float,
        valueRange: Float,
        minTime: Float,
        timeRange: Float
    ) {
        for (marker in markers) {
            val x = padding + ((marker.timestamp - minTime) / timeRange * graphWidth)
            val y = height - padding - ((marker.value - minValue) / valueRange * graphHeight)
            
            // Рисуваме вертикална линия
            val markerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = marker.color
                strokeWidth = 3f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 4f), 0f)
            }
            canvas.drawLine(x, padding, x, height - padding, markerLinePaint)
            
            // Рисуваме кръг в точката
            val markerPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = marker.color
                style = Paint.Style.FILL
            }
            val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.white)
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            
            val radius = 8f
            canvas.drawCircle(x, y, radius, markerPointPaint)
            canvas.drawCircle(x, y, radius, markerStrokePaint)
            
            // Рисуваме етикета
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = marker.color
                textSize = 20f
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            
            val labelY = if (y < height / 2) y - 15f else y + 25f
            canvas.drawText(marker.label, x, labelY, labelPaint)
        }
    }
}