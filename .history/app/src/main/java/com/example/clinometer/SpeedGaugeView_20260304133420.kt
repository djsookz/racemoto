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
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)

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

        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = dp(2f)
        linePaint.color = Color.parseColor("#8C95A5")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h * 0.43f
        radius = min(w * 0.24f, h * 0.24f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawPanelSurface(canvas)
        drawSideScales(canvas)
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
        val haloRadius = radius * 1.34f
        val haloRect = RectF(
            centerX - haloRadius,
            centerY - haloRadius,
            centerX + haloRadius,
            centerY + haloRadius
        )

        val start = 202f
        val sweep = 136f
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
        textPaint.textSize = dp(54f)
        canvas.drawText(currentSpeed.roundToInt().toString(), centerX, centerY + radius * 0.18f, textPaint)

        textPaint.textSize = dp(22f)
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

    private fun drawSideScales(canvas: Canvas) {
        val top = height * 0.12f
        val bottom = height * 0.62f
        val leftAxisX = width * 0.17f
        val rightAxisX = width * 0.83f

        drawScaleAxis(canvas, leftAxisX, top, bottom, left = true, topLabel = "0.0", midLabel = "0.7", bottomLabel = "1.4")
        drawScaleAxis(canvas, rightAxisX, top, bottom, left = false, topLabel = "1.5", midLabel = "0.7", bottomLabel = "0.0")

        if (isMotorcycle) {
            val decelG = max(0f, gForceY)
            val accelG = max(0f, -gForceY)
            drawSegmentRibbon(canvas, leftAxisX, top, bottom, decelG, 1.5f, left = true, fillFromBottom = true)
            drawSegmentRibbon(canvas, rightAxisX, top, bottom, accelG, 1.5f, left = false, fillFromBottom = false)
        } else {
            val leftG = max(0f, gForceX)
            val rightG = max(0f, -gForceX)
            drawSegmentRibbon(canvas, leftAxisX, top, bottom, leftG, 1.5f, left = true, fillFromBottom = false)
            drawSegmentRibbon(canvas, rightAxisX, top, bottom, rightG, 1.5f, left = false, fillFromBottom = false)
        }
    }

    private fun drawMotoTelemetry(canvas: Canvas) {
        val leanAbs = abs(leanAngle).roundToInt()

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = dp(24f)
        textPaint.color = Color.parseColor("#ECEFF5")
        canvas.drawText("${leanAbs}°", centerX - radius * 0.95f, centerY + radius * 0.14f, textPaint)
        canvas.drawText("${leanAbs}°", centerX + radius * 0.95f, centerY + radius * 0.14f, textPaint)

        val decelG = max(0f, gForceY)
        val decelG = max(0f, gForceY)
        val accelG = max(0f, -gForceY)

        drawBottomMetric(
            canvas = canvas,
            x = width * 0.08f,
            y = height * 0.80f,
            align = Paint.Align.LEFT,
            valueText = String.format("%.1f G", decelG),
            title = "Deceleration"
        )
        drawBottomMetric(
            canvas = canvas,
            x = width * 0.92f,
            y = height * 0.80f,
            align = Paint.Align.RIGHT,
            valueText = String.format("G %.1f", accelG),
            title = "Acceleration"
        )
    }

    private fun drawCarTelemetry(canvas: Canvas) {
        val leftG = max(0f, gForceX)
        val rightG = max(0f, -gForceX)
        val decelG = max(0f, gForceY)
        val accelG = max(0f, -gForceY)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.parseColor("#DFE5EF")
        textPaint.textSize = dp(15f)
        canvas.drawText(String.format("L %.1fG", leftG), centerX - radius * 0.95f, centerY + radius * 0.14f, textPaint)
        canvas.drawText(String.format("R %.1fG", rightG), centerX + radius * 0.95f, centerY + radius * 0.14f, textPaint)

        drawBottomMetric(
            canvas = canvas,
            x = width * 0.08f,
            y = height * 0.80f,
            align = Paint.Align.LEFT,
            valueText = String.format("DEC %.1fG", decelG),
            title = "Braking"
        )
        drawBottomMetric(
            canvas = canvas,
            x = width * 0.92f,
            y = height * 0.80f,
            align = Paint.Align.RIGHT,
            valueText = String.format("ACC %.1fG", accelG),
            title = "Acceleration"
        )
    }

    private fun drawScaleAxis(
        canvas: Canvas,
        axisX: Float,
        top: Float,
        bottom: Float,
        left: Boolean,
        topLabel: String,
        midLabel: String,
        bottomLabel: String
    ) {
        val outward = if (left) -1f else 1f
        val cap = dp(20f)
        val tick = dp(14f)

        linePaint.color = Color.parseColor("#8992A3")
        linePaint.strokeWidth = dp(2f)
        canvas.drawLine(axisX, top, axisX, bottom, linePaint)
        canvas.drawLine(axisX, top, axisX + outward * cap, top, linePaint)
        canvas.drawLine(axisX, bottom, axisX + outward * cap, bottom, linePaint)

        val y1 = top + (bottom - top) * 0.33f
        val y2 = top + (bottom - top) * 0.66f
        canvas.drawLine(axisX, y1, axisX + outward * tick, y1, linePaint)
        canvas.drawLine(axisX, y2, axisX + outward * tick, y2, linePaint)

        textPaint.textAlign = if (left) Paint.Align.RIGHT else Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = dp(10f)
        textPaint.color = Color.parseColor("#AAB4C4")

        val xLabel = axisX + outward * dp(28f)
        canvas.drawText(topLabel, xLabel, top + dp(6f), textPaint)
        canvas.drawText(midLabel, xLabel, (top + bottom) * 0.5f + dp(4f), textPaint)
        canvas.drawText(bottomLabel, xLabel, bottom + dp(4f), textPaint)
    }

    private fun drawSegmentRibbon(
        canvas: Canvas,
        axisX: Float,
        top: Float,
        bottom: Float,
        value: Float,
        maxValue: Float,
        left: Boolean,
        fillFromBottom: Boolean
    ) {
        val segments = 12
        val gap = dp(4f)
        val h = (bottom - top - gap * (segments - 1)) / segments
        val maxWidth = dp(46f)
        val minWidth = dp(20f)
        val fillCount = ((value / maxValue).coerceIn(0f, 1f) * segments).roundToInt()

        for (index in 0 until segments) {
            val t = index / (segments - 1f)
            val segWidth = maxWidth - (maxWidth - minWidth) * t
            val yTop = top + index * (h + gap)
            val yBottom = yTop + h

            val isFilled = if (fillFromBottom) {
                index >= (segments - fillCount)
            } else {
                index < fillCount
            }

            fillPaint.color = if (isFilled) Color.parseColor("#2E7CFF") else Color.parseColor("#19345D")

            val segmentRect = if (left) {
                RectF(axisX + dp(8f), yTop, axisX + dp(8f) + segWidth, yBottom)
            } else {
                RectF(axisX - dp(8f) - segWidth, yTop, axisX - dp(8f), yBottom)
            }
            canvas.drawRoundRect(segmentRect, dp(3f), dp(3f), fillPaint)
        }
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
        textPaint.textSize = dp(20f)
        textPaint.color = Color.parseColor("#F2F5FA")
        canvas.drawText(valueText, x, y, textPaint)

        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = dp(10f)
        textPaint.color = Color.parseColor("#E3E8F3")
        canvas.drawText(title, x, y + dp(20f), textPaint)
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