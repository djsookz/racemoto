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
        centerY = h * 0.41f
        radius = min(w * 0.34f, h * 0.38f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawPanelSurface(canvas)
        drawTopHalo(canvas)
        drawCenterDisk(canvas)
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
            Color.parseColor("#0C2448"),
            Color.parseColor("#061731"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, corner, corner, fillPaint)
        fillPaint.shader = null
    }

    private fun drawTopHalo(canvas: Canvas) {
        val haloRadius = radius * 1.16f
        val haloRect = RectF(
            centerX - haloRadius,
            centerY - haloRadius,
            centerX + haloRadius,
            centerY + haloRadius
        )

        val start = 205f
        val sweep = 130f
        val endPadding = 12f
        val activeSweep = ((sweep - endPadding * 2f) * (currentSpeed / maxSpeed).coerceIn(0f, 1f))

        arcPaint.strokeWidth = dp(14f)
        arcPaint.color = Color.parseColor("#6D737F")
        canvas.drawArc(haloRect, start, sweep, false, arcPaint)

        arcPaint.color = Color.parseColor("#2E7CFF")
        canvas.drawArc(haloRect, start + endPadding, activeSweep, false, arcPaint)

        val marks = listOf(0, 70, 140, 210, 280)
        textPaint.textSize = dp(9f)
        textPaint.color = Color.parseColor("#C3CBD9")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        for (m in marks) {
            val angle = start + (m / maxSpeed) * sweep
            val r = haloRadius + dp(8f)
            val x = centerX + r * cos(Math.toRadians(angle.toDouble())).toFloat()
            val y = centerY + r * sin(Math.toRadians(angle.toDouble())).toFloat()
            canvas.drawText(m.toString(), x, y, textPaint)
        }

        fillPaint.color = Color.parseColor("#F2F4F8")
        canvas.drawCircle(centerX, centerY - haloRadius - dp(4f), dp(3f), fillPaint)
    }

    private fun drawCenterDisk(canvas: Canvas) {
        fillPaint.color = Color.parseColor("#142E5D")
        canvas.drawCircle(centerX, centerY, radius, fillPaint)

        val leanAbs = abs(leanAngle).roundToInt()
        textPaint.color = Color.parseColor("#DCE3EF")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = dp(22f)
        canvas.drawText("${leanAbs}°", centerX, centerY - radius * 0.56f, textPaint)

        fillPaint.color = Color.parseColor("#9AA7BB")
        canvas.drawCircle(centerX, centerY - radius * 0.72f, dp(2.5f), fillPaint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = dp(64f)
        canvas.drawText(currentSpeed.roundToInt().toString(), centerX, centerY + radius * 0.18f, textPaint)

        textPaint.textSize = dp(24f)
        textPaint.color = Color.parseColor("#E0E6EF")
        canvas.drawText("kph", centerX, centerY + radius * 0.60f, textPaint)
    }

    private fun drawGapText(canvas: Canvas) {
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
        val leanAbs = abs(leanAngle).roundToInt()

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = dp(22f)
        textPaint.color = Color.parseColor("#ECEFF5")
        canvas.drawText("${leanAbs}°", centerX - radius * 0.57f, centerY + radius * 0.12f, textPaint)
        canvas.drawText("${leanAbs}°", centerX + radius * 0.57f, centerY + radius * 0.12f, textPaint)

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