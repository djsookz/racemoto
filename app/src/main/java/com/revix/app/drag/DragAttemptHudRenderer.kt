package com.revix.app.drag

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.revix.app.DragAttempt
import com.revix.app.HudBottomBar
import com.revix.app.HudVehicleIdentity
import com.revix.app.R
import com.revix.app.settings.UnitsManager
import com.revix.app.video.VideoLaunchCountdown
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class DragAttemptHudRenderer(
    context: Context,
    private val attempt: DragAttempt?,
    private val mode: MeasurementMode,
    private val logo: Bitmap?,
    vehicle: HudVehicleIdentity? = null
) {
    private val appContext = context.applicationContext
    private val identity = vehicle ?: HudVehicleIdentity.resolve(appContext)
    private val speedUnit = UnitsManager.getSpeedUnit(appContext)
    private val speedUnitLabel = speedUnit.symbol.uppercase(Locale.getDefault())
    private val accelLabel = appContext.getString(R.string.drag_camera_accel_label).uppercase(Locale.getDefault())
    private val gUnit = appContext.getString(R.string.track_hud_g_unit)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
    }
    private val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        isFakeBoldText = true
    }
    private val chipRect = RectF()
    private val barRect = RectF()
    private val identityTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    data class Chip(
        val label: String,
        val value: String,
        val color: Int
    )

    data class Frame(
        val chips: List<Chip>,
        val speedKmh: Float,
        val accelG: Float,
        val chronoSec: Double,
        val showChrono: Boolean,
        val modeLabel: String,
        val countdownDigit: Int? = null
    )

    fun draw(canvas: Canvas, width: Float, height: Float, positionMs: Long) {
        val current = attempt ?: return
        val t0Ms = current.videoT0OffsetMs.coerceAtLeast(0L)
        val countdownDigit = VideoLaunchCountdown.digit(
            prerollAvailableMs = t0Ms,
            timeUntilStartMs = t0Ms - positionMs
        )
        val measureNs = if (positionMs >= t0Ms) (positionMs - t0Ms) * 1_000_000L else -1L
        val chrono = if (measureNs >= 0L) measureNs / 1_000_000_000.0 else 0.0
        val speed = if (measureNs >= 0L) {
            sampleAt(current.speedSamples, current.speedTimeStamps, measureNs)
        } else {
            0f
        }
        val accel = if (measureNs >= 0L) accelAt(current, measureNs) else 0f
        drawFrame(
            canvas,
            width,
            height,
            Frame(
                chips = chipsForAttempt(current, measureNs),
                speedKmh = speed,
                accelG = accel,
                chronoSec = chrono,
                showChrono = measureNs >= 0L,
                modeLabel = modeLabel(mode),
                countdownDigit = countdownDigit
            )
        )
    }

    fun drawFrame(canvas: Canvas, width: Float, height: Float, frame: Frame) {
        if (width < 8f || height < 8f) return
        val density = HudBottomBar.density(width, height)
        fun dp(value: Float) = value * density
        val pad = dp(8f)
        val barHeight = HudBottomBar.barHeight(density)
        val lineHeight = HudBottomBar.lineHeight(density)
        val identityBottom = drawIdentityBar(canvas, width, barHeight, frame.modeLabel)
        drawChips(canvas, width, pad, dp(6f), dp(12f), identityBottom, frame.chips)
        drawBottomBar(
            canvas = canvas,
            width = width,
            height = height,
            barHeight = barHeight,
            lineHeight = lineHeight,
            speed = frame.speedKmh,
            accel = frame.accelG,
            chronoSec = frame.chronoSec
        )
        VideoLaunchCountdown.draw(canvas, width, height, frame.countdownDigit)
    }

    private fun drawIdentityBar(
        canvas: Canvas,
        width: Float,
        barHeight: Float,
        modeLabel: String
    ): Float {
        val identityH = barHeight * 0.70f
        fillPaint.shader = null
        fillPaint.color = HudBottomBar.BAR_BG
        canvas.drawRect(0f, 0f, width, identityH, fillPaint)
        val padX = barHeight * 0.28f
        val gap = barHeight * 0.18f
        val logoPad = barHeight * 0.16f
        var logoLeft = width
        logo?.takeIf { it.width > 0 && it.height > 0 }?.let { bitmap ->
            val maxInnerHeight = barHeight * 0.62f
            val maxInnerWidth = (width * 0.30f).coerceAtLeast(1f)
            val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            var innerHeight = maxInnerHeight
            var innerWidth = innerHeight * aspect
            if (innerWidth > maxInnerWidth) {
                innerWidth = maxInnerWidth
                innerHeight = innerWidth / aspect
            }
            val right = width - logoPad
            val top = identityH / 2f - innerHeight / 2f
            logoLeft = right - innerWidth
            canvas.drawBitmap(bitmap, null, RectF(logoLeft, top, right, top + innerHeight), fillPaint)
        }
        identityTextPaint.textAlign = Paint.Align.LEFT
        identityTextPaint.letterSpacing = 0.02f
        val vehicleMax = (width / 2f - padX - gap)
            .coerceAtMost(width * 0.36f)
            .coerceAtLeast(8f)
        identityTextPaint.color = HudBottomBar.TEXT_PRIMARY
        identityTextPaint.textSize = barHeight * 0.26f
        val title = if (identity.title.isNotBlank()) {
            TextUtils.ellipsize(identity.title, identityTextPaint, vehicleMax, TextUtils.TruncateAt.END).toString()
        } else {
            ""
        }
        identityTextPaint.color = HudBottomBar.PRIMARY
        identityTextPaint.textSize = barHeight * 0.19f
        val specs = if (identity.specs.isNotBlank()) {
            TextUtils.ellipsize(identity.specs, identityTextPaint, vehicleMax, TextUtils.TruncateAt.END).toString()
        } else {
            ""
        }
        identityTextPaint.color = HudBottomBar.TEXT_PRIMARY
        identityTextPaint.textSize = barHeight * 0.26f
        val titleH = if (title.isNotEmpty()) textHeight(identityTextPaint) else 0f
        identityTextPaint.textSize = barHeight * 0.19f
        val specsH = if (specs.isNotEmpty()) textHeight(identityTextPaint) else 0f
        val stackGap = if (titleH > 0f && specsH > 0f) barHeight * 0.035f else 0f
        var top = identityH / 2f - (titleH + stackGap + specsH) / 2f
        if (title.isNotEmpty()) {
            identityTextPaint.color = HudBottomBar.TEXT_PRIMARY
            identityTextPaint.textSize = barHeight * 0.26f
            identityTextPaint.letterSpacing = 0.02f
            drawTextTop(canvas, title, padX, top, identityTextPaint)
            top += titleH + stackGap
        }
        if (specs.isNotEmpty()) {
            identityTextPaint.color = HudBottomBar.PRIMARY
            identityTextPaint.textSize = barHeight * 0.19f
            identityTextPaint.letterSpacing = 0.02f
            drawTextTop(canvas, specs, padX, top, identityTextPaint)
        }
        val vehicleRight = padX + max(
            if (title.isNotEmpty()) {
                identityTextPaint.textSize = barHeight * 0.26f
                identityTextPaint.measureText(title)
            } else {
                0f
            },
            if (specs.isNotEmpty()) {
                identityTextPaint.textSize = barHeight * 0.19f
                identityTextPaint.measureText(specs)
            } else {
                0f
            }
        )
        if (modeLabel.isNotEmpty()) {
            identityTextPaint.textAlign = Paint.Align.CENTER
            identityTextPaint.color = HudBottomBar.TEXT_PRIMARY
            identityTextPaint.textSize = barHeight * 0.28f
            identityTextPaint.letterSpacing = 0.04f
            val leftBound = if (title.isNotEmpty() || specs.isNotEmpty()) vehicleRight + gap else padX
            val rightBound = logoLeft - gap
            val centerX = width / 2f
            val maxMode = (
                2f * min((centerX - leftBound).coerceAtLeast(0f), (rightBound - centerX).coerceAtLeast(0f))
                ).coerceAtLeast(8f)
            val label = TextUtils.ellipsize(modeLabel, identityTextPaint, maxMode, TextUtils.TruncateAt.END).toString()
            val modeTop = identityH / 2f - textHeight(identityTextPaint) / 2f
            drawTextTop(canvas, label, centerX, modeTop, identityTextPaint)
        }
        identityTextPaint.textAlign = Paint.Align.LEFT
        identityTextPaint.letterSpacing = 0.02f
        return identityH
    }

    fun liveFrame(state: DragRunVideoHudState): Frame {
        val chrono = if (state.officiallyStarted && state.chronoNs >= 0L) {
            state.chronoNs / 1_000_000_000.0
        } else {
            0.0
        }
        return Frame(
            chips = chipsForTimes(
                mode = state.measurementMode,
                time0to100Ns = state.time0to100Ns,
                time0to200Ns = state.time0to200Ns,
                time100to200Ns = state.time100to200Ns,
                time0to402Ns = state.time0to402Ns
            ),
            speedKmh = state.speedKmh,
            accelG = state.accelG.coerceAtLeast(0f),
            chronoSec = chrono,
            showChrono = state.officiallyStarted,
            modeLabel = modeLabel(state.measurementMode)
        )
    }

    private fun drawChips(
        canvas: Canvas,
        width: Float,
        pad: Float,
        gap: Float,
        radius: Float,
        topOffset: Float,
        chips: List<Chip>
    ) {
        if (chips.isEmpty()) return
        val top = topOffset + pad * 0.22f
        val chipHeight = pad * 3.85f
        val usable = width - pad * 2f
        val chipWidth = (usable - gap * (chips.size - 1)) / chips.size
        chips.forEachIndexed { index, chip ->
            val left = pad + index * (chipWidth + gap)
            chipRect.set(left, top, left + chipWidth, top + chipHeight)
            fillPaint.shader = null
            fillPaint.color = CHIP_BG
            canvas.drawRoundRect(chipRect, radius, radius, fillPaint)

            textPaint.color = LABEL_COLOR
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = pad * 1.08f
            canvas.drawText(chip.label, chipRect.centerX(), chipRect.top + pad * 1.18f, textPaint)

            textPaint.color = chip.color
            textPaint.textSize = pad * 1.72f
            canvas.drawText(chip.value, chipRect.centerX(), chipRect.bottom - pad * 0.48f, textPaint)
        }
    }

    private fun drawBottomBar(
        canvas: Canvas,
        width: Float,
        height: Float,
        barHeight: Float,
        lineHeight: Float,
        speed: Float,
        accel: Float,
        chronoSec: Double
    ) {
        val barTop = height - barHeight
        val midY = barTop + barHeight / 2f
        fillPaint.shader = null
        fillPaint.color = HudBottomBar.BAR_BG
        canvas.drawRect(0f, barTop - lineHeight, width, height, fillPaint)
        fillPaint.color = HudBottomBar.LINE_COLOR
        canvas.drawRect(0f, barTop - lineHeight, width, barTop, fillPaint)

        val colWidth = width / 3f
        val converted = UnitsManager.convertSpeed(speed, speedUnit).toInt()
        val gap = barHeight * HudBottomBar.STACK_GAP

        monoPaint.color = HudBottomBar.TEXT_PRIMARY
        monoPaint.textAlign = Paint.Align.CENTER
        monoPaint.textSize = barHeight * HudBottomBar.SPEED_TEXT
        textPaint.color = HudBottomBar.PRIMARY
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = barHeight * HudBottomBar.UNIT_TEXT
        val speedH = textHeight(monoPaint)
        val unitH = textHeight(textPaint)
        var cursor = midY - (speedH + gap + unitH) / 2f
        drawTextTop(canvas, converted.toString(), colWidth * 0.5f, cursor, monoPaint)
        cursor += speedH + gap
        drawTextTop(canvas, speedUnitLabel, colWidth * 0.5f, cursor, textPaint)

        val chronoText = String.format(Locale.US, "%.2f", chronoSec)
        monoPaint.color = HudBottomBar.PRIMARY
        monoPaint.textAlign = Paint.Align.CENTER
        monoPaint.textSize = barHeight * HudBottomBar.SPEED_TEXT
        val chronoH = textHeight(monoPaint)
        drawTextTop(canvas, chronoText, width / 2f, midY - chronoH / 2f, monoPaint)

        val accelCenterX = width - colWidth * 0.5f
        textPaint.color = HudBottomBar.LABEL_COLOR
        textPaint.textSize = barHeight * HudBottomBar.ACCEL_LABEL
        val labelH = textHeight(textPaint)
        monoPaint.color = HudBottomBar.PRIMARY
        monoPaint.textSize = barHeight * HudBottomBar.ACCEL_VALUE
        val valueH = textHeight(monoPaint)
        val progressHeight = barHeight * HudBottomBar.ACCEL_PROGRESS
        val rightStack = labelH + gap + valueH + gap + progressHeight
        cursor = midY - rightStack / 2f
        drawTextTop(canvas, accelLabel, accelCenterX, cursor, textPaint)
        cursor += labelH + gap
        val valueText = String.format(Locale.US, "%.2f", accel)
        monoPaint.textAlign = Paint.Align.CENTER
        val valueWidth = monoPaint.measureText(valueText)
        textPaint.color = HudBottomBar.TEXT_SECONDARY
        textPaint.textSize = barHeight * HudBottomBar.ACCEL_UNIT
        val gWidth = textPaint.measureText(gUnit)
        val valueGap = barHeight * 0.045f
        val valueBlock = valueWidth + valueGap + gWidth
        val valueLeft = accelCenterX - valueBlock / 2f
        val valueBaseline = cursor - monoPaint.fontMetrics.ascent
        monoPaint.textAlign = Paint.Align.LEFT
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(valueText, valueLeft, valueBaseline, monoPaint)
        canvas.drawText(gUnit, valueLeft + valueWidth + valueGap, valueBaseline, textPaint)
        cursor += valueH + gap
        val progressWidth = colWidth - barHeight * 0.36f
        barRect.set(accelCenterX - progressWidth / 2f, cursor, accelCenterX + progressWidth / 2f, cursor + progressHeight)
        fillPaint.shader = null
        fillPaint.color = 0xFF1A2634.toInt()
        canvas.drawRoundRect(barRect, progressHeight, progressHeight, fillPaint)
        val progress = (accel / ACCEL_MAX_G).coerceIn(0f, 1f)
        if (progress > 0f) {
            fillPaint.shader = LinearGradient(
                barRect.left,
                0f,
                barRect.right,
                0f,
                intArrayOf(0xFF0AB7B5.toInt(), 0xFFFF8F1F.toInt(), 0xFFF84D53.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
            val filled = RectF(barRect.left, barRect.top, barRect.left + barRect.width() * progress, barRect.bottom)
            canvas.drawRoundRect(filled, progressHeight, progressHeight, fillPaint)
            fillPaint.shader = null
        }
        textPaint.textAlign = Paint.Align.CENTER
        monoPaint.textAlign = Paint.Align.CENTER
    }

    private fun textHeight(paint: Paint): Float {
        val metrics = paint.fontMetrics
        return metrics.descent - metrics.ascent
    }

    private fun drawTextTop(canvas: Canvas, text: String, x: Float, top: Float, paint: Paint) {
        canvas.drawText(text, x, top - paint.fontMetrics.ascent, paint)
    }

    private fun modeLabel(mode: MeasurementMode): String {
        return when (mode) {
            MeasurementMode.ZERO_TO_100 -> UnitsManager.formatDragSpeedIntervalLabel(0, 100, appContext)
            MeasurementMode.ZERO_TO_200 -> UnitsManager.formatDragSpeedIntervalLabel(0, 200, appContext)
            MeasurementMode.HUNDRED_TO_200 -> UnitsManager.formatDragSpeedIntervalLabel(100, 200, appContext)
            MeasurementMode.QUARTER_MILE -> UnitsManager.formatDragZeroTo402IntervalLabel(appContext)
            MeasurementMode.ALL -> "ALL"
        }
    }

    private fun chipsForAttempt(current: DragAttempt, measureNs: Long): List<Chip> {
        return chipsForTimes(
            mode = mode,
            time0to100Ns = current.time0to100,
            time0to200Ns = current.time0to200,
            time100to200Ns = current.time100to200,
            time0to402Ns = current.time0to402,
            elapsedNs = measureNs,
            lock100to200Ns = reveal100to200Ns(current)
        )
    }

    private fun chipsForTimes(
        mode: MeasurementMode,
        time0to100Ns: Long,
        time0to200Ns: Long,
        time100to200Ns: Long,
        time0to402Ns: Long,
        elapsedNs: Long = Long.MAX_VALUE,
        lock100to200Ns: Long = time100to200Ns
    ): List<Chip> {
        val items = mutableListOf<Chip>()
        fun chip(label: String, timeNs: Long, color: Int, lockAtNs: Long = timeNs): Chip {
            return Chip(label, lockedTime(timeNs, elapsedNs, lockAtNs), color)
        }
        val label100 = UnitsManager.formatDragSpeedIntervalLabel(0, 100, appContext)
        val label200 = UnitsManager.formatDragSpeedIntervalLabel(0, 200, appContext)
        val labelSplit = UnitsManager.formatDragSpeedIntervalLabel(100, 200, appContext)
        val label402 = UnitsManager.formatDragZeroTo402IntervalLabel(appContext)
        when (mode) {
            MeasurementMode.ZERO_TO_100 -> items += chip(label100, time0to100Ns, COLOR_100)
            MeasurementMode.HUNDRED_TO_200 -> items += chip(labelSplit, time100to200Ns, COLOR_100_200, lock100to200Ns)
            MeasurementMode.QUARTER_MILE -> items += chip(label402, time0to402Ns, COLOR_402)
            MeasurementMode.ZERO_TO_200 -> {
                items += chip(label100, time0to100Ns, COLOR_100)
                items += chip(label200, time0to200Ns, COLOR_200)
                items += chip(labelSplit, time100to200Ns, COLOR_100_200, lock100to200Ns)
            }
            MeasurementMode.ALL -> {
                items += chip(label100, time0to100Ns, COLOR_100)
                items += chip(label200, time0to200Ns, COLOR_200)
                items += chip(labelSplit, time100to200Ns, COLOR_100_200, lock100to200Ns)
                items += chip(label402, time0to402Ns, COLOR_402)
            }
        }
        return items
    }

    private fun reveal100to200Ns(current: DragAttempt): Long {
        val split = current.time100to200
        if (split <= 0L) return -1L
        val at200 = current.time0to200.takeIf { it > 0L }
            ?: current.time0to100.takeIf { it > 0L }?.let { it + split }
        return at200 ?: split
    }

    private fun lockedTime(timeNs: Long, elapsedNs: Long, lockAtNs: Long = timeNs): String {
        return if (timeNs > 0L && elapsedNs >= lockAtNs && lockAtNs > 0L) {
            String.format(Locale.US, "%.2f", timeNs / 1_000_000_000.0)
        } else {
            "--"
        }
    }

    private fun accelAt(current: DragAttempt, elapsedNs: Long): Float {
        val live = sampleAtLerp(current.liveAccelDisplaySamples, current.liveAccelDisplayTimeStamps, elapsedNs)
        if (live > 0f || current.liveAccelDisplaySamples.isNotEmpty()) return live
        return sampleAtLerp(current.longitudinalAccelSamples, current.longitudinalAccelTimeStamps, elapsedNs)
    }

    private fun sampleAt(samples: List<Float>, times: List<Long>, elapsedNs: Long): Float {
        val limit = minOf(samples.size, times.size)
        if (limit <= 0 || elapsedNs < 0L) return 0f
        var value = samples[0]
        for (i in 0 until limit) {
            if (times[i] <= elapsedNs) {
                value = samples[i]
            } else {
                break
            }
        }
        return value.coerceAtLeast(0f)
    }

    private fun sampleAtLerp(samples: List<Float>, times: List<Long>, elapsedNs: Long): Float {
        val limit = minOf(samples.size, times.size)
        if (limit <= 0 || elapsedNs < 0L) return 0f
        var index = 0
        while (index < limit - 1 && times[index + 1] <= elapsedNs) {
            index++
        }
        val current = samples[index]
        if (index >= limit - 1 || times[index + 1] <= times[index] || elapsedNs <= times[index]) {
            return current.coerceAtLeast(0f)
        }
        val span = (times[index + 1] - times[index]).toFloat()
        val progress = ((elapsedNs - times[index]).toFloat() / span).coerceIn(0f, 1f)
        return (current + (samples[index + 1] - current) * progress).coerceAtLeast(0f)
    }

    companion object {
        private const val CHIP_BG = 0x99000000.toInt()
        private const val LABEL_COLOR = 0xFF9CA3AF.toInt()
        private const val COLOR_100 = 0xFF66BB6A.toInt()
        private const val COLOR_200 = 0xFF39E4DB.toInt()
        private const val COLOR_100_200 = 0xFFAB47BC.toInt()
        private const val COLOR_402 = 0xFFF44336.toInt()
        private const val ACCEL_MAX_G = 1.5f
    }
}
