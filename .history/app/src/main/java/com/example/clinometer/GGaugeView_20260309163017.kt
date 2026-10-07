package com.example.clinometer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class GGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class TrailPoint(
        val x: Float,
        val y: Float,
        val timestampMs: Long
    )

    var gForceX: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var gForceY: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var peakGForce: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val arcRect = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#33ADB5BD")
    }
    
    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
    }

    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
    }

    private val trailLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        color = Color.RED
    }

    private val trailPoints = ArrayDeque<TrailPoint>()
    private val maxTrailPoints = 20
    private val minTrailStepPx = 1.5f
    private val trailFadeDurationMs = 900L

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }

    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ADB5BD")
        textSize = 14f
        textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        trailPoints.clear()
        val centerX = w / 2f
        val centerY = h / 2f

        val minDimension = min(w, h).toFloat()
        val strokeWidth = bgPaint.strokeWidth
        val radius = minDimension / 2f - strokeWidth - 30f

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
        val graphRadius = min(w, h) / 2f - 20f

        // Draw background circles (concentric circles for G-force levels)
        bgPaint.strokeWidth = 2f
        bgPaint.color = Color.WHITE
        
        // Draw concentric circles for 0.5g, 1.0g, 2.0g
        canvas.drawCircle(centerX, centerY, graphRadius * 0.5f, bgPaint)
        canvas.drawCircle(centerX, centerY, graphRadius, bgPaint)
        canvas.drawCircle(centerX, centerY, graphRadius * 1.5f, bgPaint)

        // Draw cross lines
        canvas.drawLine(centerX - graphRadius, centerY, centerX + graphRadius, centerY, bgPaint)
        canvas.drawLine(centerX, centerY - graphRadius, centerX, centerY + graphRadius, bgPaint)

        // Draw labels
        smallTextPaint.textSize = 18f
        smallTextPaint.color = Color.WHITE
        canvas.drawText("0.5g", centerX + graphRadius * 0.5f + 8f, centerY - 8f, smallTextPaint)
        canvas.drawText("1.0g", centerX + graphRadius + 8f, centerY - 8f, smallTextPaint)
        canvas.drawText("2.0g", centerX + graphRadius * 1.5f + 8f, centerY - 8f, smallTextPaint)

        // Draw current G-force point with proper scaling
        // Scale G-forces to fit within the graph (max 2.5g)
        // NOTE: Service-ът вече ни подава инерционната сила (знаците са обърнати там)
        // - gForceY > 0 (backward force) → точка надолу (positive Y в координатната система)
        // - gForceY < 0 (forward force) → точка нагоре (negative Y в координатната система)
        // - gForceX > 0 (left force) → точка надясно (positive X)
        // - gForceX < 0 (right force) → точка наляво (negative X)
        val maxG = 2.5f
        val threshold = 0.10f // align with deadband to eliminate rest jumps
        
        val rawCorner = gForceX
        val rawLong = gForceY
        val scaledCorneringG = if (abs(rawCorner) <= threshold) 0f else (rawCorner / maxG).coerceIn(-1f, 1f)
        val scaledAccelG = if (abs(rawLong) <= threshold) 0f else (rawLong / maxG).coerceIn(-1f, 1f)
        
        // Директна визуализация (без обръщане, знаците вече са правилни от Service-а)
        val gX = centerX - scaledCorneringG * graphRadius
        val gY = centerY - scaledAccelG * graphRadius

        val now = System.currentTimeMillis()
        appendTrailPoint(gX, gY, now)
        val hasActiveTrail = drawTrail(canvas, now)
        if (hasActiveTrail) {
            postInvalidateOnAnimation()
        }

        // Draw current position
        centerDotPaint.color = Color.RED
        canvas.drawCircle(gX, gY, 12f, centerDotPaint)

    }

    private fun appendTrailPoint(x: Float, y: Float, now: Long) {
        val last = trailPoints.lastOrNull()
        if (last != null) {
            val dx = x - last.x
            val dy = y - last.y
            if (hypot(dx, dy) < minTrailStepPx) {
                return
            }
        }
        if (trailPoints.size >= maxTrailPoints) {
            trailPoints.removeFirst()
        }
        trailPoints.addLast(TrailPoint(x, y, now))
    }

    private fun drawTrail(canvas: Canvas, now: Long): Boolean {
        trimExpiredTrailPoints(now)
        if (trailPoints.size < 2) return false

        val points = trailPoints.toList()
        for (i in points.indices) {
            val p = points[i]
            val ageMs = (now - p.timestampMs).coerceAtLeast(0L)
            val lifeT = 1f - (ageMs.toFloat() / trailFadeDurationMs.toFloat())
            if (lifeT <= 0f) continue

            val alpha = (20f + 170f * lifeT).toInt().coerceIn(0, 255)
            val radius = 3f + 7f * t
            val t = (i + 1).toFloat() / points.size.toFloat()

            trailPaint.color = Color.argb(alpha, 255, 70, 70)
            canvas.drawCircle(p.x, p.y, radius, trailPaint)

            if (i > 0) {
                val prev = points[i - 1]
                val prevAgeMs = (now - prev.timestampMs).coerceAtLeast(0L)
                val prevLifeT = 1f - (prevAgeMs.toFloat() / trailFadeDurationMs.toFloat())
                val lineAlpha = (min(lifeT, prevLifeT) * 120f).toInt().coerceIn(0, 255)
                if (lineAlpha > 0) {
                    trailLinePaint.color = Color.argb(lineAlpha, 255, 70, 70)
                    canvas.drawLine(prev.x, prev.y, p.x, p.y, trailLinePaint)
                }
            }
        }
        return trailPoints.isNotEmpty()
    }

    private fun trimExpiredTrailPoints(now: Long) {
        while (trailPoints.isNotEmpty()) {
            val oldest = trailPoints.first()
            if (now - oldest.timestampMs > trailFadeDurationMs) {
                trailPoints.removeFirst()
            } else {
                break
            }
        }
    }
}
