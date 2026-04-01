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

    // Legacy fields kept for API compatibility
    private var accelerationG = 0f
    private var brakingG = 0f
    private var corneringG = 0f

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

    private var currentLapTime = 0f
    private var targetLapTime = 0f
    private var gapTime = 0f
    private var lockedGapSign: Int? = null

    private var leanAngle = 0f
    private var maxLeanLeft = 0f
    private var maxLeanRight = 0f
    private var isMotorcycle = false

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
        drawCenterDisk(canvas)
        drawLeanAngleGauge(canvas)
        drawGapText(canvas)

        if (isMotorcycle) {
            drawMotoTelemetry(canvas)
        } else {
            drawCarTelemetry(canvas)
        }
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
            Color.parseColor("#2A2D30"),
            Color.parseColor("#2A2D30"),
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
        fillPaint.color = Color.parseColor("#383C40")
        canvas.drawCircle(centerX, centerY, radius, fillPaint)

        textPaint.textAlign = Paint.Align.CENTER

        // Live lean angle at top center (just below the arc)
        val currentLeanAbs = abs(leanAngle).roundToInt()
        textPaint.color = Color.parseColor("#DCE3EF")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = dp(20f)
        canvas.drawText("${currentLeanAbs}°", centerX, centerY - radius * 0.62f, textPaint)

        // Max left and max right under the arc, at left/right sides
        textPaint.textSize = dp(22f)
        textPaint.color = Color.parseColor("#ECEFF5")
        canvas.drawText("${maxLeanLeft.roundToInt()}°", centerX - radius * 0.70f, centerY + radius * 0.05f, textPaint)
        canvas.drawText("${maxLeanRight.roundToInt()}°", centerX + radius * 0.70f, centerY + radius * 0.05f, textPaint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = dp(66f)
        canvas.drawText(currentSpeed.roundToInt().toString(), centerX, centerY + radius * 0.18f, textPaint)

        textPaint.textSize = dp(24f)
        textPaint.color = Color.parseColor("#E0E6EF")
        canvas.drawText("kph", centerX, centerY + radius * 0.48f, textPaint)
    }

    private fun drawGapText(canvas: Canvas) {
        textPaint.textAlign = Paint.Align.CENTER
        val gap = currentLapTime - targetLapTime
        val color = if (targetLapTime <= 0f) {
            Color.parseColor("#8C97AA")
        } else if ((lockedGapSign ?: if (gap <= 0f) -1 else 1) <= 0) {
            Color.parseColor("#69D38F")
        } else {
            Color.parseColor("#FF8C7A")
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
        val decelG = max(0f, gForceY)
        val accelG = max(0f, -gForceY)

        val metricY = height * 0.83f

        drawBottomMetric(
            canvas = canvas,
            x = width * 0.08f,
            y = metricY,
            align = Paint.Align.LEFT,
            valueText = String.format("%.1f G", decelG),
            title = "Deceleration"
        )
        drawBottomMetric(
            canvas = canvas,
            x = width * 0.92f,
            y = metricY,
            align = Paint.Align.RIGHT,
            valueText = String.format("G %.1f", accelG),
            title = "Acceleration"
        )

        val barsY = metricY + dp(24f)
        drawCompactGBar(
            canvas = canvas,
            x = width * 0.17f,
            y = barsY,
            value = decelG,
            fillFromStart = false
        )
        drawCompactGBar(
            canvas = canvas,
            x = width * 0.83f,
            y = barsY,
            value = accelG,
            fillFromStart = true
        )
    }

    private fun drawCarTelemetry(canvas: Canvas) {
        drawMotoTelemetry(canvas)
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

        fillPaint.color = Color.parseColor("#1F3E69")
        canvas.drawRoundRect(RectF(left, top, right, bottom), dp(4f), dp(4f), fillPaint)

        val fillWidth = barWidth * normalized
        val fillRect = if (fillFromStart) {
            RectF(left, top, left + fillWidth, bottom)
        } else {
            RectF(right - fillWidth, top, right, bottom)
        }
        fillPaint.color = Color.parseColor("#2E7CFF")
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
        invalidate()
    }

    fun resetGForceHistory() {
        maxLeanLeft = 0f
        maxLeanRight = 0f
        invalidate()
    }
}