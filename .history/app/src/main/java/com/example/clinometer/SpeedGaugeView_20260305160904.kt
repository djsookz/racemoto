package com.example.clinometer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class SpeedGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    private var currentSpeed = 0f
    private var maxSpeed = 280f

    var gForceX: Float = 0f
        set(value) {
            field = value
            updateCarTelemetryState()
            invalidate()
        }

    var gForceY: Float = 0f
        set(value) {
            field = value
            updateCarTelemetryState()
            invalidate()
        }

    private var currentLapTime = 0f
    private var targetLapTime = 0f
    private var lockedGapSign: Int? = null

    private var leanAngle = 0f
    private var maxLeanLeft = 0f
    private var maxLeanRight = 0f
    private var isMotorcycle = false

    private val gTrail = ArrayDeque<PointF>()
    private val maxTrailPoints = 28
    private var peakBrakeG = 0f
    private var peakAccelG = 0f
    private var peakLatLeftG = 0f
    private var peakLatRightG = 0f
    private var peakTotalG = 0f

    init {
        setupPaints()
        isClickable = false
    }

    private fun setupPaints() {
        arcPaint.style = Paint.Style.STROKE
        arcPaint.strokeCap = Paint.Cap.ROUND

        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        fillPaint.style = Paint.Style.FILL
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h * 0.42f
        radius = min(w * 0.40f, h * 0.45f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawPanelSurface(canvas)
        drawCarTelemetry(canvas)
    }

    private fun drawPanelSurface(canvas: Canvas) {
        val inset = dp(4f)
        val rect = RectF(inset, inset, width - inset, height - inset)
        val corner = dp(18f)

        fillPaint.shader = LinearGradient(
            0f,
            rect.top,
            0f,
            rect.bottom,
            Color.parseColor("#1C2128"),
            Color.parseColor("#1C2128"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, corner, corner, fillPaint)
        fillPaint.shader = null
    }

    private fun drawLeanAngleGauge(canvas: Canvas) {
        // Arc sits on the top half of the speed circle
        // 180° arc: from 180° (left) to 0° (right), i.e. the upper semicircle
        // Center of arc = top of circle (270° = 12 o'clock)
        // Lean -90° maps to 180° (9 o'clock), 0° maps to 270° (12 o'clock), +90° maps to 360°/0° (3 o'clock)
        val gaugeRadius = radius * 0.86f
        val gaugeRect = RectF(
            centerX - gaugeRadius,
            centerY - gaugeRadius,
            centerX + gaugeRadius,
            centerY + gaugeRadius
        )

        // Gray background arc: upper semicircle (180° sweep from startAngle 180°)
        arcPaint.strokeWidth = dp(13f)
        arcPaint.color = Color.parseColor("#4A5060")
        canvas.drawArc(gaugeRect, 180f, 180f, false, arcPaint)

        // Blue: max left lean (from 270° going counter-clockwise toward 180°)
        if (maxLeanLeft > 0f) {
            val leftSweep = (maxLeanLeft / 90f).coerceIn(0f, 1f) * 90f
            arcPaint.color = Color.parseColor("#FF6020")
            canvas.drawArc(gaugeRect, 270f, -leftSweep, false, arcPaint)
        }

        // Blue: max right lean (from 270° going clockwise toward 360°)
        if (maxLeanRight > 0f) {
            val rightSweep = (maxLeanRight / 90f).coerceIn(0f, 1f) * 90f
            arcPaint.color = Color.parseColor("#FF6020")
            canvas.drawArc(gaugeRect, 270f, rightSweep, false, arcPaint)
        }

        // White marker dot at live lean angle position
        // leanAngle: -90 (left) → 0 (top) → +90 (right)
        // Map to drawing angle: -90 → 180°, 0 → 270°, +90 → 360°
        val drawAngle = 270.0 + leanAngle.toDouble()
        val markerRad = Math.toRadians(drawAngle)
        val markerX = centerX + gaugeRadius * cos(markerRad).toFloat()
        val markerY = centerY + gaugeRadius * sin(markerRad).toFloat()

        fillPaint.color = Color.parseColor("#F2F4F8")
        canvas.drawCircle(markerX, markerY, dp(5f), fillPaint)
    }

    private fun drawCenterDisk(canvas: Canvas) {
        fillPaint.color = Color.parseColor("#2A323B")
        canvas.drawCircle(centerX, centerY, radius, fillPaint)

        if (!isMotorcycle) {
            val totalG = sqrt(gForceX * gForceX + gForceY * gForceY)
            val normalized = (totalG / 1.8f).coerceIn(0f, 1f)
            val ringRadius = radius * 0.93f
            val ringRect = RectF(
                centerX - ringRadius,
                centerY - ringRadius,
                centerX + ringRadius,
                centerY + ringRadius
            )

            arcPaint.style = Paint.Style.STROKE
            arcPaint.strokeCap = Paint.Cap.ROUND
            arcPaint.strokeWidth = dp(5f)
            arcPaint.color = Color.parseColor("#4A5060")
            canvas.drawArc(ringRect, -90f, 360f, false, arcPaint)

            arcPaint.color = Color.argb((90 + normalized * 150f).roundToInt(), 255, 96, 32)
            canvas.drawArc(ringRect, -90f, 360f * normalized, false, arcPaint)
        }

        textPaint.textAlign = Paint.Align.CENTER
        if (!isMotorcycle) {
            textPaint.color = Color.WHITE
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.textSize = dp(62f)
            canvas.drawText(currentSpeed.roundToInt().toString(), centerX, centerY + radius * 0.18f, textPaint)

            textPaint.textSize = dp(24f)
            textPaint.color = Color.parseColor("#E0E6EF")
            canvas.drawText("kph", centerX, centerY + radius * 0.48f, textPaint)
        }
    }

    private fun drawGapText(canvas: Canvas) {
        textPaint.textAlign = Paint.Align.CENTER
        val gap = currentLapTime - targetLapTime
        val color = if (targetLapTime <= 0f) {
            Color.parseColor("#8C97AA")
        } else if ((lockedGapSign ?: if (gap <= 0f) -1 else 1) <= 0) {
            Color.parseColor("#11CC56")
        } else {
            Color.parseColor("#EB3E23")
        }
        val text = if (targetLapTime > 0f) {
            if (gap >= 0f) String.format("GAP +%.2fs", gap) else String.format("GAP %.2fs", gap)
        } else {
            "GAP --"
        }

        textPaint.textSize = dp(10f)
        textPaint.color = color
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(text, centerX, centerY + radius * 0.85f, textPaint)
    }

    private fun drawMotoTelemetry(canvas: Canvas) {
    }

    private fun drawCarTelemetry(canvas: Canvas) {
        val graphCenterX = centerX
        val graphCenterY = height * 0.80f
        val graphRadius = dp(45f)

        arcPaint.style = Paint.Style.STROKE
        arcPaint.strokeCap = Paint.Cap.ROUND
        arcPaint.strokeWidth = dp(1.4f)
        arcPaint.color = Color.parseColor("#5D6473")

        canvas.drawCircle(graphCenterX, graphCenterY, graphRadius, arcPaint)
        canvas.drawCircle(graphCenterX, graphCenterY, graphRadius * 0.5f, arcPaint)

        canvas.drawLine(
            graphCenterX - graphRadius,
            graphCenterY,
            graphCenterX + graphRadius,
            graphCenterY,
            arcPaint
        )
        canvas.drawLine(
            graphCenterX,
            graphCenterY - graphRadius,
            graphCenterX,
            graphCenterY + graphRadius,
            arcPaint
        )

        if (gTrail.isNotEmpty()) {
            val maxChartG = 1.6f
            gTrail.forEachIndexed { index, point ->
                val progress = (index + 1).toFloat() / gTrail.size.toFloat()
                val normalizedX = (point.x / maxChartG).coerceIn(-1f, 1f)
                val normalizedY = (point.y / maxChartG).coerceIn(-1f, 1f)
                val trailX = graphCenterX - normalizedX * graphRadius
                val trailY = graphCenterY - normalizedY * graphRadius
                fillPaint.color = Color.argb((30 + progress * 150f).roundToInt(), 255, 96, 32)
                canvas.drawCircle(trailX, trailY, dp(1.6f + progress * 2.6f), fillPaint)
            }
        }

        val maxChartG = 1.6f
        val normalizedX = (gForceX / maxChartG).coerceIn(-1f, 1f)
        val normalizedY = (gForceY / maxChartG).coerceIn(-1f, 1f)
        val dotX = graphCenterX - normalizedX * graphRadius
        val dotY = graphCenterY - normalizedY * graphRadius

        fillPaint.color = Color.parseColor("#FF6020")
        canvas.drawCircle(dotX, dotY, dp(5.4f), fillPaint)

        val peakVectorNorm = (peakTotalG / maxChartG).coerceIn(0f, 1f)
        arcPaint.strokeWidth = dp(3f)
        arcPaint.color = Color.parseColor("#FF8B5B")
        canvas.drawArc(
            RectF(
                graphCenterX - graphRadius * peakVectorNorm,
                graphCenterY - graphRadius * peakVectorNorm,
                graphCenterX + graphRadius * peakVectorNorm,
                graphCenterY + graphRadius * peakVectorNorm
            ),
            -90f,
            360f,
            false,
            arcPaint
        )

        val totalG = sqrt(gForceX * gForceX + gForceY * gForceY)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = dp(12f)
        textPaint.color = Color.parseColor("#F2F5FA")
        canvas.drawText(String.format("%.2f G", totalG), graphCenterX, graphCenterY - graphRadius - dp(10f), textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = dp(9f)
        textPaint.color = Color.parseColor("#A8B2C5")
        canvas.drawText("Peak ${String.format("%.2f", peakTotalG)} G", graphCenterX, graphCenterY + graphRadius + dp(14f), textPaint)

        val metricsY = height * 0.90f
        drawCarMetric(canvas, width * 0.16f, metricsY, max(0f, gForceY), peakBrakeG, "Brake")
        drawCarMetric(canvas, width * 0.39f, metricsY, max(0f, -gForceY), peakAccelG, "Accel")
        drawCarMetric(canvas, width * 0.62f, metricsY, max(0f, gForceX), peakLatLeftG, "Lat L")
        drawCarMetric(canvas, width * 0.85f, metricsY, max(0f, -gForceX), peakLatRightG, "Lat R")
    }

    private fun drawCarMetric(
        canvas: Canvas,
        x: Float,
        y: Float,
        value: Float,
        peak: Float,
        label: String
    ) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = dp(14f)
        textPaint.color = Color.parseColor("#F2F5FA")
        canvas.drawText(String.format("%.1f G", value), x, y, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = dp(9f)
        textPaint.color = Color.parseColor("#D3DAE7")
        canvas.drawText(label, x, y + dp(12f), textPaint)

        textPaint.textSize = dp(8f)
        textPaint.color = Color.parseColor("#9AA4B7")
        canvas.drawText("Pk ${String.format("%.1f", peak)}", x, y + dp(23f), textPaint)

        drawMiniBar(canvas, x, y + dp(29f), value)
    }

    private fun drawMiniBar(canvas: Canvas, x: Float, y: Float, value: Float) {
        val widthBar = dp(58f)
        val heightBar = dp(4.5f)
        val normalized = (value / 1.6f).coerceIn(0f, 1f)
        val left = x - widthBar / 2f
        val right = x + widthBar / 2f

        fillPaint.color = Color.parseColor("#4A5060")
        canvas.drawRoundRect(RectF(left, y, right, y + heightBar), dp(3f), dp(3f), fillPaint)

        fillPaint.color = Color.parseColor("#FF6020")
        canvas.drawRoundRect(
            RectF(left, y, left + widthBar * normalized, y + heightBar),
            dp(3f),
            dp(3f),
            fillPaint
        )
    }

    private fun updateCarTelemetryState() {
        val brake = max(0f, gForceY)
        val accel = max(0f, -gForceY)
        val latLeft = max(0f, gForceX)
        val latRight = max(0f, -gForceX)

        peakBrakeG = max(peakBrakeG, brake)
        peakAccelG = max(peakAccelG, accel)
        peakLatLeftG = max(peakLatLeftG, latLeft)
        peakLatRightG = max(peakLatRightG, latRight)
        peakTotalG = max(peakTotalG, sqrt(gForceX * gForceX + gForceY * gForceY))

        gTrail.addLast(PointF(gForceX, gForceY))
        while (gTrail.size > maxTrailPoints) {
            gTrail.removeFirst()
        }
    }

    private fun drawCompactGBar(
        canvas: Canvas,
        x: Float,
        y: Float,
        value: Float,
        fillFromStart: Boolean
    ) {
        val barWidth = dp(96f)
        val barHeight = dp(7f)
        val maxValue = 1.5f
        val normalized = (value / maxValue).coerceIn(0f, 1f)

        val left = x - barWidth / 2f
        val right = x + barWidth / 2f
        val top = y
        val bottom = y + barHeight

        fillPaint.color = Color.parseColor("#4A5060")
        canvas.drawRoundRect(RectF(left, top, right, bottom), dp(4f), dp(4f), fillPaint)

        val fillWidth = barWidth * normalized
        val fillRect = if (fillFromStart) {
            RectF(left, top, left + fillWidth, bottom)
        } else {
            RectF(right - fillWidth, top, right, bottom)
        }
        fillPaint.color = Color.parseColor("#FF6020")
        canvas.drawRoundRect(fillRect, dp(4f), dp(4f), fillPaint)
    }

    private fun drawBottomMetric(
        canvas: Canvas,
        x: Float,
        y: Float,
        align: Paint.Align,
        valueText: String,
        title: String
    ) {
        textPaint.textAlign = align
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = dp(19f)
        textPaint.color = Color.parseColor("#F2F5FA")
        canvas.drawText(valueText, x, y, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = dp(10f)
        textPaint.color = Color.parseColor("#E3E8F3")
        canvas.drawText(title, x, y + dp(18f), textPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun Canvas.drawText(text: String, x: Float, y: Float, paint: Paint = textPaint) {
        drawText(text, x, y, paint)
    }

    // Public methods
    fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0f, maxSpeed)
        invalidate()
    }

    fun setPredictiveGap(currentLap: Float, targetLap: Float) {
        currentLapTime = currentLap
        targetLapTime = targetLap
        invalidate()
    }

    fun lockPredictiveColor(isSlower: Boolean) {
        lockedGapSign = if (isSlower) 1 else -1
        invalidate()
    }

    fun unlockPredictiveColor() {
        lockedGapSign = null
        invalidate()
    }

    fun setLeanAngle(angle: Float) {
        leanAngle = angle.coerceIn(-90f, 90f)
        if (leanAngle < 0f) {
            maxLeanLeft = max(maxLeanLeft, abs(leanAngle))
        } else {
            maxLeanRight = max(maxLeanRight, leanAngle)
        }
        invalidate()
    }

    fun setDotByNormalizedG(normX: Float, normY: Float) {
        gForceX = normX
        gForceY = normY
        invalidate()
    }

    fun setMotorcycleMode(motorcycle: Boolean) {
        isMotorcycle = motorcycle
        if (!motorcycle) {
            leanAngle = 0f
            maxLeanLeft = 0f
            maxLeanRight = 0f
            updateCarTelemetryState()
        } else {
            gTrail.clear()
            peakBrakeG = 0f
            peakAccelG = 0f
            peakLatLeftG = 0f
            peakLatRightG = 0f
            peakTotalG = 0f
        }
        invalidate()
    }

    fun resetGForceHistory() {
        maxLeanLeft = 0f
        maxLeanRight = 0f
        gTrail.clear()
        peakBrakeG = 0f
        peakAccelG = 0f
        peakLatLeftG = 0f
        peakLatRightG = 0f
        peakTotalG = 0f
        invalidate()
    }
}