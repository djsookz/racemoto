package com.example.clinometer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class LeanVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val amber = ContextCompat.getColor(context, R.color.accent_color)

    private val groundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.argb(36, 255, 255, 255)
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    private val referenceArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.4f
        color = withAlpha(amber, 46)
    }

    private val centerDashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = withAlpha(amber, 70)
        pathEffect = DashPathEffect(floatArrayOf(4f, 6f), 0f)
    }

    private val leanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.7f
        color = withAlpha(amber, 130)
        pathEffect = DashPathEffect(floatArrayOf(3f, 5f), 0f)
    }

    private val angleArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        color = withAlpha(amber, 220)
    }

    private val motorcycleDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_motorcycle)
        ?.mutate()
        ?.also { DrawableCompat.setTint(it, withAlpha(amber, 235)) }

    private var leanAngleDeg: Float = 0f

    fun setLeanAngle(angleDeg: Float) {
        val bounded = angleDeg.coerceIn(-65f, 65f)
        if (bounded == leanAngleDeg) return
        leanAngleDeg = bounded
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val widthF = width.toFloat()
        val heightF = height.toFloat()
        if (widthF <= 0f || heightF <= 0f) return

        val centerX = widthF * 0.5f
        val safeInset = maxOf(referenceArcPaint.strokeWidth, leanLinePaint.strokeWidth)
        val topInset = safeInset
        val bottomInset = safeInset
        val groundY = heightF - bottomInset
        val radiusY = (groundY - topInset).coerceAtLeast(0f)
        val radiusX = min(widthF * 0.46f, radiusY)

        canvas.drawLine(widthF * 0.04f, groundY, widthF * 0.96f, groundY, groundPaint)

        val refOval = RectF(centerX - radiusX, groundY - radiusY, centerX + radiusX, groundY + radiusY)
        canvas.drawArc(refOval, 180f, 180f, false, referenceArcPaint)

        val guideLength = radiusY * 0.98f
        canvas.drawLine(centerX, groundY, centerX, groundY - guideLength, centerDashPaint)

        val leanRad = Math.toRadians(leanAngleDeg.toDouble()).toFloat()
        val lineLenX = radiusX * 1.02f
        val lineLenY = radiusY * 1.02f
        val leanX = centerX + sin(leanRad) * lineLenX
        val leanY = groundY - cos(leanRad) * lineLenY
        canvas.drawLine(centerX, groundY, leanX, leanY, leanLinePaint)

        val absLean = abs(leanAngleDeg)
        if (absLean > 1f) {
            val arcRX = radiusX * 0.44f
            val arcRY = radiusY * 0.44f
            val arcOval = RectF(centerX - arcRX, groundY - arcRY, centerX + arcRX, groundY + arcRY)
            canvas.drawArc(arcOval, -90f, leanAngleDeg, false, angleArcPaint)
        }

        val icon = motorcycleDrawable ?: return
        val intrinsicW = if (icon.intrinsicWidth > 0) icon.intrinsicWidth.toFloat() else 512f
        val intrinsicH = if (icon.intrinsicHeight > 0) icon.intrinsicHeight.toFloat() else 512f
        val iconRatio = intrinsicW / intrinsicH

        val availableHeight = (groundY - topInset).coerceAtLeast(1f)
        var iconHeight = availableHeight * 0.98f
        var iconWidth = iconHeight * iconRatio
        val maxWidth = widthF * 0.88f
        if (iconWidth > maxWidth) {
            val scale = maxWidth / iconWidth
            iconWidth = maxWidth
            iconHeight *= scale
        }

        val left = centerX - (iconWidth * 0.5f)
        val top = groundY - iconHeight
        val right = left + iconWidth

        canvas.save()
        canvas.rotate(leanAngleDeg, centerX, groundY)
        icon.setBounds(left.roundToInt(), top.roundToInt(), right.roundToInt(), groundY.roundToInt())
        icon.draw(canvas)
        canvas.restore()
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        val safeAlpha = alpha.coerceIn(0, 255)
        return Color.argb(safeAlpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
