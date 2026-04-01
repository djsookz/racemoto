package com.example.clinometer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
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

    private val wheelGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = withAlpha(amber, 72)
    }

    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        color = withAlpha(amber, 210)
    }

    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = withAlpha(amber, 132)
    }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.8f
        strokeCap = Paint.Cap.ROUND
        color = withAlpha(amber, 220)
    }

    private val subFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        color = withAlpha(amber, 168)
    }

    private val riderStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = withAlpha(amber, 220)
    }

    private val riderFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = withAlpha(amber, 56)
    }

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
        val groundY = heightF * 0.84f
        val radius = min(widthF * 0.42f, heightF * 0.60f)

        canvas.drawLine(widthF * 0.08f, groundY, widthF * 0.92f, groundY, groundPaint)

        val refOval = RectF(centerX - radius, groundY - radius, centerX + radius, groundY + radius)
        canvas.drawArc(refOval, 180f, 180f, false, referenceArcPaint)

        val guideLength = radius * 0.98f
        canvas.drawLine(centerX, groundY, centerX, groundY - guideLength, centerDashPaint)

        val leanRad = Math.toRadians(leanAngleDeg.toDouble()).toFloat()
        val lineLen = radius * 1.02f
        val leanX = centerX + sin(leanRad) * lineLen
        val leanY = groundY - cos(leanRad) * lineLen
        canvas.drawLine(centerX, groundY, leanX, leanY, leanLinePaint)

        val absLean = abs(leanAngleDeg)
        if (absLean > 1f) {
            val arcR = radius * 0.44f
            val arcOval = RectF(centerX - arcR, groundY - arcR, centerX + arcR, groundY + arcR)
            canvas.drawArc(arcOval, -90f, leanAngleDeg, false, angleArcPaint)
        }

        val rearDist = radius * 0.24f
        val frontDist = radius * 0.76f
        val rearX = centerX + sin(leanRad) * rearDist
        val rearY = groundY - cos(leanRad) * rearDist
        val frontX = centerX + sin(leanRad) * frontDist
        val frontY = groundY - cos(leanRad) * frontDist

        val rearRadius = radius * 0.20f
        val frontRadius = radius * 0.18f

        canvas.drawCircle(rearX, rearY, rearRadius, wheelGlowPaint)
        canvas.drawCircle(frontX, frontY, frontRadius, wheelGlowPaint)
        canvas.drawCircle(rearX, rearY, rearRadius, wheelPaint)
        canvas.drawCircle(frontX, frontY, frontRadius, wheelPaint)
        canvas.drawCircle(rearX, rearY, rearRadius * 0.25f, hubPaint)
        canvas.drawCircle(frontX, frontY, frontRadius * 0.25f, hubPaint)

        val frameStartX = rearX + (frontX - rearX) * 0.20f
        val frameStartY = rearY + (frontY - rearY) * 0.20f
        val frameEndX = rearX + (frontX - rearX) * 0.78f
        val frameEndY = rearY + (frontY - rearY) * 0.78f
        canvas.drawLine(frameStartX, frameStartY, frameEndX, frameEndY, framePaint)

        val swingEndX = rearX + (frontX - rearX) * 0.10f
        val swingEndY = rearY + (frontY - rearY) * 0.10f
        canvas.drawLine(frameStartX, frameStartY, swingEndX, swingEndY, subFramePaint)

        val forkStartX = rearX + (frontX - rearX) * 0.70f
        val forkStartY = rearY + (frontY - rearY) * 0.70f
        val forkEndX = rearX + (frontX - rearX) * 0.92f
        val forkEndY = rearY + (frontY - rearY) * 0.92f
        canvas.drawLine(forkStartX, forkStartY, forkEndX, forkEndY, subFramePaint)

        val riderCenterX = rearX + (frontX - rearX) * 0.63f
        val riderCenterY = rearY + (frontY - rearY) * 0.63f
        val riderHalfW = radius * 0.07f
        val riderHalfH = radius * 0.13f
        canvas.drawOval(
            riderCenterX - riderHalfW,
            riderCenterY - riderHalfH,
            riderCenterX + riderHalfW,
            riderCenterY + riderHalfH,
            riderFillPaint
        )
        canvas.drawOval(
            riderCenterX - riderHalfW,
            riderCenterY - riderHalfH,
            riderCenterX + riderHalfW,
            riderCenterY + riderHalfH,
            riderStrokePaint
        )

        val helmetX = rearX + (frontX - rearX) * 0.82f
        val helmetY = rearY + (frontY - rearY) * 0.82f
        canvas.drawCircle(helmetX, helmetY, radius * 0.08f, riderStrokePaint)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        val safeAlpha = alpha.coerceIn(0, 255)
        return Color.argb(safeAlpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
