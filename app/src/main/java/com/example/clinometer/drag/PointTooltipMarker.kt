package com.example.clinometer.drag

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.clinometer.R
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF

class PointTooltipMarker(
    context: Context,
    private val pointType: PointType
) : MarkerView(context, 0) {

    enum class PointType {
        SPEED_100,    // 0-100 km/h - зелен
        SPEED_200,    // 0-200 km/h - син
        DISTANCE_402  // 0-402m - червен
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundRect = RectF()
    private val textBounds = android.graphics.Rect()

    init {
        // Настройваме цвета според типа на точката
        paint.color = when (pointType) {
            PointType.SPEED_100 -> ContextCompat.getColor(context, R.color.accent_green)
            PointType.SPEED_200 -> ContextCompat.getColor(context, R.color.accent_blue)
            PointType.DISTANCE_402 -> ContextCompat.getColor(context, R.color.accent_red)
        }
        paint.textSize = 32f
        paint.textAlign = Paint.Align.CENTER
    }

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        super.refreshContent(e, highlight)
    }

    override fun draw(canvas: Canvas, posX: Float, posY: Float) {
        val label = when (pointType) {
            PointType.SPEED_100 -> "0-100 km/h"
            PointType.SPEED_200 -> "0-200 km/h"
            PointType.DISTANCE_402 -> "0-402m"
        }

        // Измерваме текста
        paint.getTextBounds(label, 0, label.length, textBounds)
        
        val padding = 16f
        val textWidth = textBounds.width().toFloat()
        val textHeight = textBounds.height().toFloat()
        
        val rectWidth = textWidth + padding * 2
        val rectHeight = textHeight + padding * 2
        
        // Позиционираме tooltip-а над точката
        val tooltipX = posX - rectWidth / 2
        val tooltipY = posY - rectHeight - 20f
        
        // Рисуваме закръглен правоъгълник
        backgroundRect.set(tooltipX, tooltipY, tooltipX + rectWidth, tooltipY + rectHeight)
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(backgroundRect, 8f, 8f, paint)
        
        // Рисуваме текста
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.WHITE
        canvas.drawText(label, posX, tooltipY + textHeight + padding / 2, paint)
        
        // Връщаме оригиналния цвят
        paint.color = when (pointType) {
            PointType.SPEED_100 -> ContextCompat.getColor(context, R.color.accent_green)
            PointType.SPEED_200 -> ContextCompat.getColor(context, R.color.accent_blue)
            PointType.DISTANCE_402 -> ContextCompat.getColor(context, R.color.accent_red)
        }
    }

    override fun getOffset(): MPPointF {
        return MPPointF(-width / 2f, -height.toFloat())
    }
}
