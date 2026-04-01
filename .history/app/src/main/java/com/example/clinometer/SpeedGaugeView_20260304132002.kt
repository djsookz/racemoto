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
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val metricPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val helperPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    private var currentSpeed = 0f
    private var maxSpeed = 280f

    // G-force data (keep old fields for compatibility, but add direct access like GGaugeView)
    private var accelerationG = 0f
    private var brakingG = 0f
    private var corneringG = 0f
    // Direct g-force values (like GGaugeView) - use these instead of accelerationG - brakingG
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

    // Predictive gap data
    private var currentLapTime = 0f
    private var targetLapTime = 0f
    private var gapTime = 0f
    private var lockedGapSign: Int? = null // -1 = faster (green), +1 = slower (red)

    // Lean angle data (motorcycle only)
    private var leanAngle = 0f
    private var isMotorcycle = false

    init {
        setupPaints()
        isClickable = false
    }

    private fun setupPaints() {
        arcPaint.style = Paint.Style.STROKE
        arcPaint.strokeCap = Paint.Cap.ROUND

        tickPaint.style = Paint.Style.STROKE
        tickPaint.strokeWidth = 2f
        tickPaint.color = Color.parseColor("#B0B8C6")

        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        metricPaint.style = Paint.Style.FILL

        helperPaint.style = Paint.Style.STROKE
        helperPaint.strokeWidth = 2f
        helperPaint.color = Color.parseColor("#4A5568")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h * 0.58f
        radius = min(w * 0.44f, h * 0.47f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawGaugeArc(canvas)
        drawSpeedMarkings(canvas)
        drawSpeedDisplay(canvas)
        drawGapPill(canvas)

        if (isMotorcycle) {
            drawMotoTelemetry(canvas)
        } else {
            drawCarTelemetry(canvas)
        }
    }

    private fun drawGaugeArc(canvas: Canvas) {
        val rect = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        arcPaint.strokeWidth = 20f
        arcPaint.color = Color.parseColor("#5B6068")
        canvas.drawArc(rect, 150f, 240f, false, arcPaint)

        if (currentSpeed > 0) {
            arcPaint.color = Color.parseColor("#FF7A2F")
            val currentAngle = (currentSpeed / maxSpeed) * 240f
            canvas.drawArc(rect, 150f, currentAngle, false, arcPaint)
        }
    }

    private fun drawSpeedMarkings(canvas: Canvas) {
        val markings = (0..280 step 20)

        for (marking in markings) {
            val angle = 150f + (marking / maxSpeed) * 240f
            val angleRad = Math.toRadians(angle.toDouble())

            val tickLength = 9f
            val x1 = centerX + (radius - 26f) * cos(angleRad).toFloat()
            val y1 = centerY + (radius - 26f) * sin(angleRad).toFloat()
            val x2 = centerX + (radius - 26f - tickLength) * cos(angleRad).toFloat()
            val y2 = centerY + (radius - 26f - tickLength) * sin(angleRad).toFloat()

            canvas.drawLine(x1, y1, x2, y2, tickPaint)

            val textX = centerX + (radius - 52f) * cos(angleRad).toFloat()
            val textY = centerY + (radius - 52f) * sin(angleRad).toFloat()

            textPaint.textSize = 16f
            textPaint.color = Color.parseColor("#D7DBE0")
            canvas.drawText(marking.toString(), textX, textY + 8f, textPaint)
        }
    }

    private fun drawSpeedDisplay(canvas: Canvas) {
        textPaint.textSize = 98f
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(currentSpeed.roundToInt().toString(), centerX, centerY + 12f, textPaint)

        textPaint.textSize = 28f
        textPaint.color = Color.parseColor("#E2E8F0")
        canvas.drawText("kph", centerX, centerY + 54f, textPaint)
    }

    private fun drawGapPill(canvas: Canvas) {
        val gap = currentLapTime - targetLapTime
        val locked = lockedGapSign

        val gapColor = if (locked != null) {
            if (locked <= 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
        } else if (targetLapTime <= 0f) {
            Color.parseColor("#2B313A")
        } else {
            if (gap <= 0f) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
        }

        val gapText = if (targetLapTime > 0f) {
            if (gap >= 0f) String.format("GAP +%.2fs", gap)
            else String.format("GAP %.2fs", gap)
        } else {
            "GAP --"
        }

        val top = centerY + 74f
        val rect = RectF(centerX - 110f, top, centerX + 110f, top + 36f)
        metricPaint.color = gapColor
        canvas.drawRoundRect(rect, 18f, 18f, metricPaint)

        textPaint.textSize = 18f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.WHITE
        canvas.drawText(gapText, centerX, top + 24f, textPaint)
    }

    private fun drawMotoTelemetry(canvas: Canvas) {
        val maxG = 1.5f
        val decelG = max(0f, gForceY)
        val accelG = max(0f, -gForceY)
        val leanLeft = max(0f, -leanAngle)
        val leanRight = max(0f, leanAngle)

        val top = height * 0.15f
        val bottom = height * 0.80f

        drawSegmentColumn(
            canvas = canvas,
            centerX = width * 0.19f,
            top = top,
            bottom = bottom,
            value = decelG,
            maxValue = maxG,
            activeColor = Color.parseColor("#3D8BFF"),
            alignRight = false,
            title = "Decel",
            valuePrefix = "",
            valueSuffix = "G"
        )

        drawSegmentColumn(
            canvas = canvas,
            centerX = width * 0.81f,
            top = top,
            bottom = bottom,
            value = accelG,
            maxValue = maxG,
            activeColor = Color.parseColor("#3D8BFF"),
            alignRight = true,
            title = "Accel",
            valuePrefix = "",
            valueSuffix = "G"
        )

        textPaint.color = Color.parseColor("#E2E8F0")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = 22f
        canvas.drawText("${abs(leanAngle).roundToInt()}°", centerX, centerY - 46f, textPaint)

        textPaint.textSize = 16f
        canvas.drawText("${leanLeft.roundToInt()}°", centerX - radius * 0.45f, centerY - 4f, textPaint)
        canvas.drawText("${leanRight.roundToInt()}°", centerX + radius * 0.45f, centerY - 4f, textPaint)
    }

    private fun drawCarTelemetry(canvas: Canvas) {
        val maxGraphG = 1.5f
        val leftG = max(0f, gForceX)
        val rightG = max(0f, -gForceX)
        val decelG = max(0f, gForceY)
        val accelG = max(0f, -gForceY)

        val graphCenterY = height * 0.80f
        val graphRadius = min(width, height) * 0.15f

        helperPaint.color = Color.parseColor("#475569")
        canvas.drawCircle(centerX, graphCenterY, graphRadius, helperPaint)
        canvas.drawCircle(centerX, graphCenterY, graphRadius * 0.5f, helperPaint)
        canvas.drawLine(centerX - graphRadius, graphCenterY, centerX + graphRadius, graphCenterY, helperPaint)
        canvas.drawLine(centerX, graphCenterY - graphRadius, centerX, graphCenterY + graphRadius, helperPaint)

        val normalizedX = (gForceX / maxGraphG).coerceIn(-1f, 1f)
        val normalizedY = (gForceY / maxGraphG).coerceIn(-1f, 1f)
        val dotX = centerX + normalizedX * graphRadius
        val dotY = graphCenterY - normalizedY * graphRadius

        metricPaint.color = Color.parseColor("#FF5E2B")
        canvas.drawCircle(dotX, dotY, 9f, metricPaint)

        drawMetricChip(canvas, centerX - graphRadius * 1.35f, graphCenterY - graphRadius * 0.75f, "L", leftG, Color.parseColor("#4FC3F7"), Paint.Align.RIGHT)
        drawMetricChip(canvas, centerX + graphRadius * 1.35f, graphCenterY - graphRadius * 0.75f, "R", rightG, Color.parseColor("#4FC3F7"), Paint.Align.LEFT)
        drawMetricChip(canvas, centerX - graphRadius * 1.35f, graphCenterY + graphRadius * 0.85f, "DEC", decelG, Color.parseColor("#EF5350"), Paint.Align.RIGHT)
        drawMetricChip(canvas, centerX + graphRadius * 1.35f, graphCenterY + graphRadius * 0.85f, "ACC", accelG, Color.parseColor("#66BB6A"), Paint.Align.LEFT)

        textPaint.textSize = 12f
        textPaint.color = Color.parseColor("#94A3B8")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Decel", centerX, graphCenterY - graphRadius - 8f, textPaint)
        canvas.drawText("Accel", centerX, graphCenterY + graphRadius + 20f, textPaint)
    }

    private fun drawSegmentColumn(
        canvas: Canvas,
        centerX: Float,
        top: Float,
        bottom: Float,
        value: Float,
        maxValue: Float,
        activeColor: Int,
        alignRight: Boolean,
        title: String,
        valuePrefix: String,
        valueSuffix: String
    ) {
        val segmentCount = 11
        val barHeight = bottom - top
        val segmentGap = 5f
        val segmentHeight = (barHeight - (segmentGap * (segmentCount - 1))) / segmentCount
        val segmentWidth = 34f
        val fillCount = ((value / maxValue).coerceIn(0f, 1f) * segmentCount).roundToInt()

        for (index in 0 until segmentCount) {
            val yBottom = bottom - (index * (segmentHeight + segmentGap))
            val yTop = yBottom - segmentHeight

            val left = if (alignRight) centerX else centerX - segmentWidth
            val right = if (alignRight) centerX + segmentWidth else centerX

            metricPaint.color = if (index < fillCount) activeColor else Color.parseColor("#2E3B4E")
            canvas.drawRoundRect(RectF(left, yTop, right, yBottom), 6f, 6f, metricPaint)
        }

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = 13f
        textPaint.color = Color.parseColor("#8FA2BD")
        textPaint.textAlign = if (alignRight) Paint.Align.LEFT else Paint.Align.RIGHT

        val axisOffset = if (alignRight) 48f else -48f
        canvas.drawText("1.5", centerX + axisOffset, top + 6f)
        canvas.drawText("0.7", centerX + axisOffset, (top + bottom) / 2f + 4f)
        canvas.drawText("0.0", centerX + axisOffset, bottom + 4f)

        textPaint.color = Color.parseColor("#E2E8F0")
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 16f
        canvas.drawText("$valuePrefix${String.format("%.1f", value)} $valueSuffix", centerX, bottom + 28f, textPaint)

        textPaint.textSize = 13f
        textPaint.color = Color.parseColor("#AAB8CC")
        canvas.drawText(title, centerX, bottom + 46f, textPaint)
    }

    private fun drawMetricChip(
        canvas: Canvas,
        x: Float,
        y: Float,
        label: String,
        value: Float,
        color: Int,
        align: Paint.Align
    ) {
        textPaint.textAlign = align
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 15f
        textPaint.color = color
        canvas.drawText("$label ${String.format("%.1f", value)}G", x, y, textPaint)
    }

    private fun Canvas.drawText(text: String, x: Float, y: Float) {
        drawText(text, x, y, textPaint)
    }

    private fun drawLegacyPredictiveGap(canvas: Canvas) {
        textPaint.textSize = 72f
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val gap = currentLapTime - targetLapTime
        val gapText = if (targetLapTime > 0f) {
            if (gap >= 0f) String.format("+%.2f", gap) else String.format("%.2f", gap)
        } else {
            "0.00"
        }
        canvas.drawText(gapText, centerX, centerY, textPaint)
    }

    // Public methods
    fun setSpeed(speed: Float) {
        currentSpeed = speed.coerceIn(0f, maxSpeed)
        invalidate()
    }

    fun setGForces(acceleration: Float, braking: Float, cornering: Float) {
        accelerationG = acceleration
        brakingG = braking
        corneringG = cornering
        invalidate()
    }

    fun setPredictiveGap(currentLap: Float, targetLap: Float) {
        currentLapTime = currentLap
        targetLapTime = targetLap
        gapTime = currentLap - targetLap
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
        leanAngle = angle.coerceIn(-60f, 60f)
        invalidate()
    }

    fun setDotByNormalizedG(normX: Float, normY: Float) {
        gForceX = normX
        gForceY = normY
        invalidate()
    }

    fun setMotorcycleMode(motorcycle: Boolean) {
        isMotorcycle = motorcycle
        invalidate()
    }

    fun resetGForceHistory() {
        invalidate()
    }
}