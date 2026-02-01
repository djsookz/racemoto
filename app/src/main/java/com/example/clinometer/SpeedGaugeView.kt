package com.example.clinometer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class SpeedGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

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
    private val gForceHistory = mutableListOf<PointF>()
    private val maxGForcePoints = 50

    // Predictive gap data
    private var currentLapTime = 0f
    private var targetLapTime = 0f
    private var gapTime = 0f
    private var lockedGapSign: Int? = null // -1 = faster (green), +1 = slower (red)

    // Tab system
    private var currentTab = 0 // 0: Speed, 1: Predictive Gap, 2: G-Forces, 3: Lean Angle (motorcycle only)
    private val tabNames = arrayOf("Speed", "Predictive Gap", "G-Forces", "Lean Angle")

    // Lean angle data (motorcycle only)
    private var leanAngle = 0f
    private var isMotorcycle = false

    init {
        setupPaints()
        isClickable = true
    }

    private fun setupPaints() {
        // Background paint
        paint.color = Color.parseColor("#1A1A1A")
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true

        // Text paint
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.isAntiAlias = true
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        // Needle paint
        needlePaint.color = Color.parseColor("#FF6B00")
        needlePaint.style = Paint.Style.STROKE
        needlePaint.strokeWidth = 4f
        needlePaint.strokeCap = Paint.Cap.ROUND
        needlePaint.isAntiAlias = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h * 0.6f
        radius = (w / 2f) * 0.9f // Use 90% of width for maximum horizontal space
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            // Check if tap is in center area
            val dx = event.x - centerX
            val dy = event.y - centerY
            val distance = sqrt(dx * dx + dy * dy)

            if (distance < radius * 0.7f) {
                // Cycle through tabs
                currentTab++
                if (isMotorcycle) {
                    if (currentTab > 3) currentTab = 0
                } else {
                    if (currentTab > 2) currentTab = 0
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // No background color - transparent

        // Draw gauge arc and markings
        drawGaugeArc(canvas)
        drawSpeedMarkings(canvas)

        // Draw center content based on current tab
        drawCenterContent(canvas)

        // Draw tab indicator at bottom
        drawTabIndicator(canvas)
    }

    private fun drawGaugeArc(canvas: Canvas) {
        val rect = RectF(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        // Draw grey background section (full arc)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 40f // Increase stroke width for bigger gauge
        paint.color = Color.parseColor("#4A4A4A")
        canvas.drawArc(rect, 150f, 240f, false, paint)

        // Draw orange section (0 to current speed)
        if (currentSpeed > 0) {
            paint.color = Color.parseColor("#FF8C00")
            val currentAngle = (currentSpeed / maxSpeed) * 240f
            canvas.drawArc(rect, 150f, currentAngle, false, paint)
        }
    }


    private fun drawSpeedMarkings(canvas: Canvas) {
        val markings = arrayOf(0, 20, 40, 60, 80, 100, 120, 140, 160, 180, 200, 220, 240, 260, 280)

        for (marking in markings) {
            val angle = 150f + (marking / maxSpeed) * 240f
            val angleRad = Math.toRadians(angle.toDouble())

            // Draw tick marks - much shorter
            val tickLength = 12f // Much shorter tick marks
            val x1 = centerX + (radius - 45f) * cos(angleRad).toFloat()
            val y1 = centerY + (radius - 45f) * sin(angleRad).toFloat()
            val x2 = centerX + (radius - 45f - tickLength) * cos(angleRad).toFloat()
            val y2 = centerY + (radius - 45f - tickLength) * sin(angleRad).toFloat()

            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f // Slightly thinner tick marks
            canvas.drawLine(x1, y1, x2, y2, paint)

            // Draw numbers
            val textX = centerX + (radius - 85f) * cos(angleRad).toFloat()
            val textY = centerY + (radius - 85f) * sin(angleRad).toFloat()

            textPaint.textSize = 24f // Slightly smaller text to fit more numbers
            textPaint.color = Color.parseColor("#AAAAAA")
            canvas.drawText(marking.toString(), textX, textY + 8f, textPaint)
        }
    }

    private fun drawCenterContent(canvas: Canvas) {
        when (currentTab) {
            0 -> drawSpeedDisplay(canvas)
            1 -> drawPredictiveGap(canvas)
            2 -> drawGForceGraph(canvas)
            3 -> if (isMotorcycle) drawLeanAngle(canvas)
        }
    }

    private fun drawSpeedDisplay(canvas: Canvas) {
        // Large speed number
        textPaint.textSize = 120f // Even bigger speed display
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(String.format("%.1f", currentSpeed), centerX, centerY + 20f, textPaint)

        // Unit
        textPaint.textSize = 40f // Much bigger unit text
        textPaint.color = Color.WHITE
        canvas.drawText("kph", centerX, centerY + 80f, textPaint)
    }

    private fun drawPredictiveGap(canvas: Canvas) {
        // Calculate predictive gap: predicted time - best time
        val gap = currentLapTime - targetLapTime
        
        // Choose background color based on prediction
        paint.style = Paint.Style.FILL
        val locked = lockedGapSign
        if (locked != null) {
            paint.color = if (locked <= 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        } else if (targetLapTime <= 0f) {
            // No best lap yet → neutral background
            paint.color = Color.parseColor("#4A4A4A")
        } else {
            val sign = if (gap <= 0) -1 else 1
            paint.color = if (sign <= 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        }
        canvas.drawCircle(centerX, centerY, radius * 0.5f, paint)

        // Gap time
        textPaint.textSize = 80f // Much bigger gap time display
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val gapText = if (targetLapTime > 0) {
            if (gap >= 0) String.format("+%.2f", gap)
            else String.format("%.2f", gap)
        } else {
            "0.00"
        }

        canvas.drawText(gapText, centerX, centerY, textPaint)

        // Target time label
        textPaint.textSize = 28f // Much bigger label text
        textPaint.typeface = Typeface.DEFAULT
        if (targetLapTime > 0) {
            val targetText = String.format("Target: %02d:%02d.%02d",
                (targetLapTime / 60).toInt(),
                (targetLapTime % 60).toInt(),
                ((targetLapTime * 100) % 100).toInt()
            )
            canvas.drawText(targetText, centerX, centerY + 35f, textPaint)
        } else {
            canvas.drawText("First Lap", centerX, centerY + 55f, textPaint) // Moved down by 20dp
        }
    }

    private fun drawGForceGraph(canvas: Canvas) {
        // Draw G-force circle graph
        val graphRadius = radius * 0.6f // Bigger graph

        // Draw background circles
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f // Thicker lines
        paint.color = Color.parseColor("#333333")

        // Draw concentric circles for 0.5g, 1.0g, 2.0g
        canvas.drawCircle(centerX, centerY, graphRadius * 0.5f, paint)
        canvas.drawCircle(centerX, centerY, graphRadius, paint)
        canvas.drawCircle(centerX, centerY, graphRadius * 1.5f, paint)

        // Draw cross lines
        canvas.drawLine(centerX - graphRadius, centerY, centerX + graphRadius, centerY, paint)
        canvas.drawLine(centerX, centerY - graphRadius, centerX, centerY + graphRadius, paint)

        // Draw labels
        textPaint.textSize = 18f // Bigger labels
        textPaint.color = Color.parseColor("#666666")
        canvas.drawText("0.5g", centerX + graphRadius * 0.5f + 8f, centerY - 8f, textPaint)
        canvas.drawText("1.0g", centerX + graphRadius + 8f, centerY - 8f, textPaint)
        canvas.drawText("2.0g", centerX + graphRadius * 1.5f + 8f, centerY - 8f, textPaint)
        
        // Axis labels removed per request

        // Draw current G-force point with proper scaling
        // Scale G-forces to fit within the graph (max 2.5g)
        // NOTE: Използваме директно gForceX и gForceY (как в drag сесиите)
        // Service-ът вече ни подава инерционната сила (знаците са обърнати там)
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

        // Draw trail
        if (gForceHistory.size > 1) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = Color.parseColor("#FF444488")

            val path = Path()
            path.moveTo(gForceHistory[0].x, gForceHistory[0].y)
            for (i in 1 until gForceHistory.size) {
                path.lineTo(gForceHistory[i].x, gForceHistory[i].y)
            }
            canvas.drawPath(path, paint)
        }

        // Draw current position
        paint.style = Paint.Style.FILL
        paint.color = Color.RED
        canvas.drawCircle(gX, gY, 12f, paint) // Bigger current position dot

        // Removed textual G-force values per request
    }

    private fun drawLeanAngle(canvas: Canvas) {
        // Large lean angle display (integer degrees)
        textPaint.textSize = 120f // Even bigger lean angle display
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val leanInt = abs(leanAngle).roundToInt()
        canvas.drawText("${leanInt}°", centerX, centerY + 20f, textPaint)

        // Direction indicator
        textPaint.textSize = 40f // Much bigger direction text
        textPaint.color = Color.WHITE
        val direction = when {
            leanAngle > 0 -> "Left"
            leanAngle < 0 -> "Right"
            else -> "Upright"
        }
        canvas.drawText(direction, centerX, centerY + 80f, textPaint)

        // Visual lean indicator
        val leanIndicatorLength = radius * 0.5f // Bigger lean indicator
        val leanRad = Math.toRadians(leanAngle.toDouble())

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f // Thicker lean indicator line
        paint.color = Color.parseColor("#FF8C00")

        val endX = centerX - leanIndicatorLength * sin(leanRad).toFloat()
        val endY = centerY - leanIndicatorLength * cos(leanRad).toFloat()
        canvas.drawLine(centerX, centerY + 100f, endX, endY + 100f, paint) // Move indicator down
    }

    private fun drawTabIndicator(canvas: Canvas) {
        val tabY = height - 80f // Move tab indicator down a bit

        // Draw tab background
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#222222")
        canvas.drawRoundRect(
            centerX - 120f, tabY - 30f, // Even bigger tab indicator
            centerX + 120f, tabY + 30f,
            30f, 30f, paint
        )

        // Draw tab text
        textPaint.textSize = 32f // Much bigger tab text
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val tabName = when (currentTab) {
            0 -> "Speed"
            1 -> "Predictive Gap"
            2 -> "G-Forces"
            3 -> "Lean Angle"
            else -> "Speed"
        }
        canvas.drawText(tabName, centerX, tabY + 10f, textPaint)
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

        // Add to history with proper scaling and noise filtering
        // Използваме директно gForceX и gForceY (ако са зададени), иначе fallback към старата логика
        val maxG = 2.0f
        val threshold = 0.1f // Ignore small values (noise)
        
        val rawCorner = if (gForceX != 0f || gForceY != 0f) gForceX else corneringG
        val rawLong = if (gForceX != 0f || gForceY != 0f) gForceY else (accelerationG - brakingG)
        
        val scaledCorneringG = if (abs(rawCorner) < threshold) 0f else (rawCorner / maxG).coerceIn(-1f, 1f)
        val scaledAccelG = if (abs(rawLong) < threshold) 0f else (rawLong / maxG).coerceIn(-1f, 1f)
        
        // Обръщаме знаците за canvas координати (как в drawGForceGraph)
        val gX = centerX - scaledCorneringG * (radius * 0.6f)
        val gY = centerY - scaledAccelG * (radius * 0.6f)
        gForceHistory.add(PointF(gX, gY))

        // Limit history size
        if (gForceHistory.size > maxGForcePoints) {
            gForceHistory.removeAt(0)
        }

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
        // Preserve sign: left negative, right positive
        leanAngle = angle.coerceIn(-60f, 60f)
        invalidate()
    }

    fun setDotByNormalizedG(normX: Float, normY: Float) {
        val maxOffsetX = (width * 0.35f).coerceAtLeast(1f)
        val maxOffsetY = (height * 0.35f).coerceAtLeast(1f)
        val targetX = (width / 2f) + normX * maxOffsetX
        val targetY = (height / 2f) + normY * maxOffsetY
        // Update the G-force graph position directly
        invalidate()
    }

    fun setMotorcycleMode(motorcycle: Boolean) {
        isMotorcycle = motorcycle
        if (!motorcycle && currentTab == 3) {
            currentTab = 0
        }
        invalidate()
    }

    fun resetGForceHistory() {
        gForceHistory.clear()
        invalidate()
    }
}