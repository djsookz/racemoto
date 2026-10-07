package com.revix.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import com.revix.app.utils.LapTimeFormatter
import com.revix.app.video.VideoLaunchCountdown
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class TrackSessionHudRenderer(private val brandLogo: Bitmap?) {

    data class GRenderFrame(
        val currentLongitudinalG: Float,
        val currentLateralG: Float,
        val currentResultG: Float,
        val maxBraking: Float,
        val maxAccel: Float,
        val maxLeft: Float,
        val maxRight: Float,
        val maxResultG: Float,
        val visualMaxG: Float
    )

    data class RecentLap(
        val lapNumber: Int,
        val durationMs: Long
    )

    data class Frame(
        val currentLapNumber: Int?,
        val currentLapTimeMs: Long?,
        val lastLapTimeMs: Long?,
        val bestLapTimeMs: Long?,
        val recentLaps: List<RecentLap>,
        val speedKmh: Float,
        val geoPoint: GeoPoint?,
        val g: GRenderFrame,
        val leanDeg: Float,
        val countdownDigit: Int? = null
    )

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(178, 6, 8, 12)
        style = Paint.Style.FILL
    }
    private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 214, 222, 232)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 206, 217, 228)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 30f
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 44f
    }
    private val secondaryValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(232, 236, 244, 255)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 34f
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = APP_ACCENT_ORANGE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 38f
    }
    private val bestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 115, 255, 170)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 38f
    }
    private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 62f
    }
    private val speedUnitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = APP_ACCENT_ORANGE
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textSize = 30f
    }
    private val comboCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(178, 6, 8, 12)
        style = Paint.Style.FILL
    }
    private val comboStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 214, 222, 232)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val leanArcTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 200, 210, 220)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 12f
    }
    private val leanArcColorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeWidth = 11f
    }
    private val leanNeedlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 214, 64)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f
    }
    private val leanPivotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 214, 64)
        style = Paint.Style.FILL
    }
    private val gBarTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(36, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val gBarStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 214, 222, 232)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val gBarFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gBarThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gBarDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 214, 222, 232)
        strokeWidth = 2f
    }
    private val leanValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 34f
    }
    private val leanDirectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 210, 220, 230)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 20f
    }
    private val gaugeGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val gaugeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textSize = 20f
    }
    private val gaugeValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 26f
    }
    private val gaugeValueOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 7, 12, 18)
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeJoin = Paint.Join.ROUND
        strokeMiter = 10f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 26f
    }
    private val gaugeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 82, 62)
        style = Paint.Style.FILL
    }
    private val gaugeDotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 82, 62)
        style = Paint.Style.FILL
    }
    private val brakingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 235, 62, 35)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 26f
    }
    private val accelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 0, 233, 133)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 26f
    }
    private val metaNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 236, 244, 255)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 28f
    }
    private val footerMutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 138, 168, 196)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 20f
    }
    private val recentLapLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 255, 255)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textSize = 20f
    }
    private val recentLapTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(232, 236, 244, 255)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = 28f
    }
    private val axisLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 65, 83, 104)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val brakingBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 230, 69, 99)
        style = Paint.Style.FILL
    }
    private val accelBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 0, 204, 117)
        style = Paint.Style.FILL
    }
    private val mapStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val mapRibbonFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val mapRibbonEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val mapRibbonShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val mapDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 120, 84)
        style = Paint.Style.FILL
    }
    private val mapDotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(96, 255, 120, 84)
        style = Paint.Style.FILL
    }
    private val mapCheckWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 250, 250, 252)
        style = Paint.Style.FILL
    }
    private val mapCheckBlackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 16, 16, 18)
        style = Paint.Style.FILL
    }
    private val mapCheckPath = Path()
    private val brandLogoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        alpha = 235
    }
    private val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HudBottomBar.BAR_BG
        style = Paint.Style.FILL
    }
    private val barLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = HudBottomBar.LINE_COLOR
        style = Paint.Style.FILL
    }

    private var canvasWidth = 0f
    private var canvasHeight = 0f
    private var margin = 0f
    private var cornerRadius = 0f
    private var barHeightPx = 0f
    private var barLineHeight = 0f
    private var isMotorcycle = true
    private var speedUnitLabel = "KM/H"
    private var speedFactor = 1f
    private var speedCard = RectF()
    private var leanCard = RectF()
    private var gCard = RectF()
    private var bottomBar = RectF()
    private var bottomBarRadius = 0f
    private var identityBar = RectF()
    private var timerBar = RectF()
    private var mapCard = RectF()
    private var brandLogoCard = RectF()
    private var brandLogoInner = RectF()
    private var mapInnerRect = RectF()
    private var mapPath = Path()
    private var mapProjector: MiniMapProjector? = null
    private var perspectiveMap: PerspectiveTrackRibbon? = null
    private var gaugeDotRadius = 10f
    private var showBrandLogo = false
    private var trackName = ""
    private var vehicleName = ""
    private var vehicleSpecs = ""
    private var startMarker: GeoPoint? = null
    private var lastMiniMapPoints: List<GeoPoint>? = null
    private var followMiniMap = true

    fun configure(
        width: Float,
        height: Float,
        isMotorcycle: Boolean,
        speedUnitLabel: String,
        speedFactor: Float,
        miniMapPoints: List<GeoPoint>,
        trackName: String = "",
        vehicleName: String = "",
        vehicleSpecs: String = "",
        startMarker: GeoPoint? = null,
        followMiniMap: Boolean = true
    ) {
        if (width <= 1f || height <= 1f) return
        val trimmedTrack = trackName.trim()
        val trimmedName = vehicleName.trim()
        val trimmedSpecs = vehicleSpecs.trim()
        val sizeChanged = width != canvasWidth || height != canvasHeight
        val mapSame = miniMapPoints === lastMiniMapPoints &&
            (mapProjector != null || miniMapPoints.size < 12)
        val layoutSame = !sizeChanged &&
            this.isMotorcycle == isMotorcycle &&
            this.speedUnitLabel == speedUnitLabel &&
            this.speedFactor == speedFactor &&
            this.trackName == trimmedTrack &&
            this.vehicleName == trimmedName &&
            this.vehicleSpecs == trimmedSpecs &&
            this.startMarker == startMarker &&
            this.followMiniMap == followMiniMap
        if (layoutSame && mapSame) return
        canvasWidth = width
        canvasHeight = height
        this.isMotorcycle = isMotorcycle
        this.speedUnitLabel = speedUnitLabel
        this.speedFactor = speedFactor
        this.trackName = trimmedTrack
        this.vehicleName = trimmedName
        this.vehicleSpecs = trimmedSpecs
        this.startMarker = startMarker
        this.followMiniMap = followMiniMap
        margin = min(canvasWidth, canvasHeight) * 0.026f
        cornerRadius = min(canvasWidth, canvasHeight) * 0.022f
        labelPaint.textSize = min(canvasWidth, canvasHeight) * 0.024f
        valuePaint.textSize = min(canvasWidth, canvasHeight) * 0.038f
        secondaryValuePaint.textSize = min(canvasWidth, canvasHeight) * 0.028f
        accentPaint.textSize = min(canvasWidth, canvasHeight) * 0.030f
        bestPaint.textSize = min(canvasWidth, canvasHeight) * 0.030f
        speedPaint.color = HudBottomBar.TEXT_PRIMARY
        speedUnitPaint.color = HudBottomBar.PRIMARY
        leanValuePaint.textSize = min(canvasWidth, canvasHeight) * 0.040f
        leanDirectionPaint.textSize = min(canvasWidth, canvasHeight) * 0.015f
        cardStrokePaint.strokeWidth = max(1.6f, min(canvasWidth, canvasHeight) * 0.0016f)
        comboStrokePaint.strokeWidth = cardStrokePaint.strokeWidth
        leanArcTrackPaint.strokeWidth = max(6.5f, min(canvasWidth, canvasHeight) * 0.009f)
        leanArcColorPaint.strokeWidth = leanArcTrackPaint.strokeWidth * 0.82f
        leanNeedlePaint.strokeWidth = max(2f, min(canvasWidth, canvasHeight) * 0.0030f)
        gBarStrokePaint.strokeWidth = max(1.6f, min(canvasWidth, canvasHeight) * 0.0018f)
        gBarDividerPaint.strokeWidth = max(1.4f, min(canvasWidth, canvasHeight) * 0.0016f)
        gaugeGridPaint.strokeWidth = max(1.6f, min(canvasWidth, canvasHeight) * 0.0018f)
        gaugeLabelPaint.textSize = min(canvasWidth, canvasHeight) * 0.015f
        gaugeValuePaint.textSize = min(canvasWidth, canvasHeight) * 0.020f
        gaugeValueOutlinePaint.textSize = gaugeValuePaint.textSize
        gaugeValueOutlinePaint.strokeWidth = max(3f, gaugeValuePaint.textSize * 0.16f)
        brakingPaint.textSize = min(canvasWidth, canvasHeight) * 0.023f
        accelPaint.textSize = min(canvasWidth, canvasHeight) * 0.023f
        footerMutedPaint.textSize = min(canvasWidth, canvasHeight) * 0.013f
        recentLapLabelPaint.textSize = min(canvasWidth, canvasHeight) * 0.020f
        recentLapTimePaint.textSize = min(canvasWidth, canvasHeight) * 0.026f
        axisLinePaint.strokeWidth = max(1.5f, min(canvasWidth, canvasHeight) * 0.0018f)
        mapStrokePaint.strokeWidth = max(4f, min(canvasWidth, canvasHeight) * 0.006f)
        mapRibbonEdgePaint.strokeWidth = max(2.2f, min(canvasWidth, canvasHeight) * 0.0034f)
        gaugeDotRadius = max(6f, min(canvasWidth, canvasHeight) * 0.0105f)
        rebuildLayout(miniMapPoints, reuseMap = mapSame && perspectiveMap != null)
    }

    fun draw(canvas: Canvas, frame: Frame, clockMs: Long = SystemClock.elapsedRealtime()) {
        if (canvasWidth <= 1f || canvasHeight <= 1f) return
        drawPortraitIdentityBar(canvas)
        drawTimerBar(canvas, frame)
        drawBottomBar(canvas)
        drawSpeedCard(canvas, frame.speedKmh)
        drawMapCard(canvas, frame.geoPoint, frame.speedKmh, clockMs)
        if (isMotorcycle) {
            drawMotorcycleLeanGCombo(canvas, frame.g, frame.leanDeg)
        } else {
            drawGCard(canvas, frame.g)
        }
        drawBrandLogo(canvas)
        drawBottomBarNames(canvas)
        VideoLaunchCountdown.draw(canvas, canvasWidth, canvasHeight, frame.countdownDigit)
    }

    private fun rebuildLayout(miniMapPoints: List<GeoPoint>, reuseMap: Boolean = false) {
        val landscape = canvasWidth >= canvasHeight
        val portrait = !landscape
        val density = HudBottomBar.density(canvasWidth, canvasHeight)
        barHeightPx = HudBottomBar.barHeight(density)
        barLineHeight = HudBottomBar.lineHeight(density)
        val colWidth = canvasWidth / 3f
        val bandHeight = barHeightPx * if (portrait) 1.42f else 1.24f
        val bandTop = canvasHeight - bandHeight
        speedCard = RectF(0f, bandTop, colWidth, canvasHeight)
        gCard = RectF(colWidth * 2f, bandTop, canvasWidth, canvasHeight)
        bottomBar = RectF(0f, bandTop - barLineHeight, canvasWidth, canvasHeight)
        bottomBarRadius = 0f
        leanCard = RectF()

        val hasIdentity = vehicleName.isNotEmpty() || vehicleSpecs.isNotEmpty()
        val hasLogo = brandLogo != null && brandLogo.width > 0 && brandLogo.height > 0
        if (hasIdentity || hasLogo || trackName.isNotEmpty()) {
            val identityH = barHeightPx * if (portrait) 1.00f else 0.92f
            identityBar.set(0f, 0f, canvasWidth, identityH)
        } else {
            identityBar.setEmpty()
        }
        val timerLabel = barHeightPx * 0.16f
        val timerValue = barHeightPx * 0.27f
        val timerGap = barHeightPx * 0.016f
        val timerPadY = barHeightPx * 0.036f
        val timerH = timerPadY * 2f + timerLabel * 1.18f + timerGap + timerValue * 1.18f
        val timerTop = if (identityBar.isEmpty) 0f else identityBar.bottom
        timerBar.set(0f, timerTop, canvasWidth, timerTop + timerH)
        val mapPadX = colWidth * 0.06f
        val mapPadY = bandHeight * 0.07f
        mapCard = RectF(
            colWidth + mapPadX,
            bandTop + mapPadY,
            colWidth * 2f - mapPadX,
            canvasHeight - mapPadY * 0.55f
        )

        speedPaint.textSize = barHeightPx * HudBottomBar.SPEED_TEXT
        speedUnitPaint.textSize = barHeightPx * HudBottomBar.UNIT_TEXT
        metaNamePaint.textSize = barHeightPx * HudBottomBar.NAME_TEXT
        metaNamePaint.letterSpacing = 0.04f
        leanValuePaint.textSize = barHeightPx * 0.28f
        leanArcTrackPaint.strokeWidth = max(5f, barHeightPx * 0.068f)
        leanArcColorPaint.strokeWidth = leanArcTrackPaint.strokeWidth * 0.82f
        leanNeedlePaint.strokeWidth = max(2f, barHeightPx * 0.022f)
        gaugeDotRadius = max(6f, barHeightPx * 0.075f)
        gaugeValuePaint.textSize = barHeightPx * 0.16f
        gaugeValueOutlinePaint.textSize = gaugeValuePaint.textSize
        gaugeValueOutlinePaint.strokeWidth = max(3f, gaugeValuePaint.textSize * 0.16f)

        layoutBrandLogo()

        val innerPadding = max(4f, min(mapCard.width(), mapCard.height()) * 0.045f)
        mapInnerRect = RectF(
            mapCard.left + innerPadding,
            mapCard.top + innerPadding,
            mapCard.right - innerPadding,
            mapCard.bottom - innerPadding
        )
        if (!reuseMap) {
            lastMiniMapPoints = miniMapPoints
            mapProjector = if (miniMapPoints.size >= 12) {
                MiniMapProjector(miniMapPoints)
            } else {
                null
            }
            perspectiveMap = mapProjector?.let { projector ->
                PerspectiveTrackRibbon(projector.pathPoints)
            }
        }
        mapPath = mapProjector?.createPath(mapInnerRect) ?: Path()
    }

    private fun layoutBrandLogo() {
        val logo = brandLogo
        if (logo == null || logo.width <= 0 || logo.height <= 0) {
            showBrandLogo = false
            brandLogoCard.setEmpty()
            brandLogoInner.setEmpty()
            return
        }
        val host = if (!identityBar.isEmpty) {
            identityBar
        } else {
            RectF(0f, 0f, canvasWidth, barHeightPx * 1.05f)
        }
        val pad = host.height() * 0.16f
        val maxInnerHeight = min(host.height() * 0.78f, barHeightPx * 0.62f)
        val maxInnerWidth = (host.width() * 0.30f).coerceAtLeast(1f)
        val aspect = logo.width.toFloat() / logo.height.toFloat()
        var innerHeight = maxInnerHeight
        var innerWidth = innerHeight * aspect
        if (innerWidth > maxInnerWidth) {
            innerWidth = maxInnerWidth
            innerHeight = innerWidth / aspect
        }
        val right = host.right - pad
        val centerY = host.centerY()
        brandLogoInner = RectF(
            right - innerWidth,
            centerY - innerHeight / 2f,
            right,
            centerY + innerHeight / 2f
        )
        brandLogoCard = RectF(
            brandLogoInner.left - pad * 0.25f,
            host.top,
            host.right,
            host.bottom
        )
        showBrandLogo = brandLogoInner.width() > 4f && brandLogoInner.height() > 4f
    }

    private fun drawBrandLogo(canvas: Canvas) {
        val logo = brandLogo ?: return
        if (!showBrandLogo || brandLogoCard.isEmpty || brandLogoInner.isEmpty) return
        canvas.drawBitmap(logo, null, brandLogoInner, brandLogoPaint)
    }

    private fun drawBottomBarNames(canvas: Canvas) {
        if (bottomBar.isEmpty) return
        val reservedLeft = mapCard.left
        val reservedRight = mapCard.right
        if (canvasWidth >= canvasHeight && identityBar.isEmpty) {
            HudVehicleBadge.draw(
                canvas = canvas,
                identity = HudVehicleIdentity(title = vehicleName, specs = vehicleSpecs),
                logoLeft = reservedLeft,
                logoRight = reservedRight,
                rightColumnLeft = gCard.left,
                barTop = speedCard.top,
                barBottom = speedCard.bottom,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                barHeight = barHeightPx
            )
        }
    }

    private fun drawPortraitIdentityBar(canvas: Canvas) {
        if (identityBar.isEmpty) return
        canvas.drawRect(identityBar, barFillPaint)
        val padX = identityBar.height() * 0.28f
        val gap = identityBar.height() * 0.18f
        val leftX = identityBar.left + padX
        val logoLeft = if (!brandLogoInner.isEmpty) brandLogoInner.left else identityBar.right
        val titleSize = metaNamePaint.textSize
        val titleSpacing = metaNamePaint.letterSpacing
        val specsSize = footerMutedPaint.textSize
        val specsColor = footerMutedPaint.color
        metaNamePaint.textSize = barHeightPx * 0.26f
        metaNamePaint.letterSpacing = 0.02f
        footerMutedPaint.color = HudBottomBar.PRIMARY
        footerMutedPaint.textSize = barHeightPx * 0.19f
        val vehicleMax = (identityBar.centerX() - leftX - gap)
            .coerceAtMost(identityBar.width() * 0.36f)
            .coerceAtLeast(8f)
        val title = ellipsizeName(vehicleName, vehicleMax)
        val specs = TextUtils.ellipsize(
            vehicleSpecs,
            TextPaint(footerMutedPaint),
            vehicleMax,
            TextUtils.TruncateAt.END
        ).toString()
        val titleH = if (title.isNotEmpty()) textHeight(metaNamePaint) else 0f
        val specsH = if (specs.isNotEmpty()) textHeight(footerMutedPaint) else 0f
        val stackGap = if (titleH > 0f && specsH > 0f) barHeightPx * 0.035f else 0f
        var top = identityBar.centerY() - (titleH + stackGap + specsH) / 2f
        if (title.isNotEmpty()) {
            top = drawTopAlignedText(canvas, title, leftX, top, metaNamePaint) + stackGap
        }
        if (specs.isNotEmpty()) {
            drawTopAlignedText(canvas, specs, leftX, top, footerMutedPaint)
        }
        val vehicleRight = leftX + max(
            if (title.isNotEmpty()) metaNamePaint.measureText(title) else 0f,
            if (specs.isNotEmpty()) footerMutedPaint.measureText(specs) else 0f
        )
        if (trackName.isNotEmpty()) {
            val trackSize = metaNamePaint.textSize
            metaNamePaint.textSize = barHeightPx * 0.28f
            metaNamePaint.letterSpacing = 0.04f
            val leftBound = if (title.isNotEmpty() || specs.isNotEmpty()) vehicleRight + gap else leftX
            val rightBound = logoLeft - gap
            val centerX = identityBar.centerX()
            val maxTrack = (
                2f * min((centerX - leftBound).coerceAtLeast(0f), (rightBound - centerX).coerceAtLeast(0f))
                ).coerceAtLeast(8f)
            if (maxTrack >= 24f) {
                val label = ellipsizeName(trackName, maxTrack)
                val trackTop = identityBar.centerY() - textHeight(metaNamePaint) / 2f
                drawTopAlignedTextCentered(canvas, label, centerX, trackTop, metaNamePaint)
            }
            metaNamePaint.textSize = trackSize
        }
        metaNamePaint.textSize = titleSize
        metaNamePaint.letterSpacing = titleSpacing
        footerMutedPaint.textSize = specsSize
        footerMutedPaint.color = specsColor
    }

    private fun ellipsizeName(value: String, maxWidth: Float): String {
        return TextUtils.ellipsize(value, metaNamePaint, maxWidth, TextUtils.TruncateAt.END).toString()
    }

    private fun drawBottomBar(canvas: Canvas) {
        if (bottomBar.isEmpty) return
        canvas.drawRect(bottomBar, barFillPaint)
        canvas.drawRect(
            bottomBar.left,
            bottomBar.top,
            bottomBar.right,
            bottomBar.top + barLineHeight,
            barLinePaint
        )
    }

    private fun drawTimerBar(canvas: Canvas, frame: Frame) {
        if (timerBar.isEmpty) return
        canvas.drawRect(timerBar, barFillPaint)
        val splitH = max(1f, barLineHeight * 0.35f)
        canvas.drawRect(
            timerBar.left,
            timerBar.top,
            timerBar.right,
            timerBar.top + splitH,
            dividerPaint
        )
        val padX = timerBar.height() * 0.22f
        val inner = RectF(
            timerBar.left + padX,
            timerBar.top,
            timerBar.right - padX,
            timerBar.bottom
        )
        val currentW = inner.width() * 0.28f
        val lastW = inner.width() * 0.26f
        val bestW = inner.width() * 0.26f
        val currentRect = RectF(inner.left, inner.top, inner.left + currentW, inner.bottom)
        val lastRect = RectF(currentRect.right, inner.top, currentRect.right + lastW, inner.bottom)
        val bestRect = RectF(lastRect.right, inner.top, lastRect.right + bestW, inner.bottom)
        val lapRect = RectF(bestRect.right, inner.top, inner.right, inner.bottom)

        val labelSize = labelPaint.textSize
        val valueSize = valuePaint.textSize
        val secondarySize = secondaryValuePaint.textSize
        val bestSize = bestPaint.textSize
        val accentSize = accentPaint.textSize
        labelPaint.textSize = barHeightPx * 0.16f
        val baseValue = barHeightPx * 0.27f
        valuePaint.textSize = baseValue
        secondaryValuePaint.textSize = baseValue
        bestPaint.textSize = baseValue
        accentPaint.textSize = barHeightPx * 0.32f

        val currentText = frame.currentLapTimeMs?.let(::formatTime) ?: LapTimeFormatter.ZERO_PADDED
        val lastText = frame.lastLapTimeMs?.let(::formatTime) ?: LapTimeFormatter.PLACEHOLDER
        val bestText = frame.bestLapTimeMs?.let(::formatTime) ?: LapTimeFormatter.PLACEHOLDER
        val lapText = frame.currentLapNumber?.let { lap -> lap.coerceAtLeast(1).toString() } ?: "--"
        fitTimerValue(valuePaint, currentText, currentRect.width())
        fitTimerValue(secondaryValuePaint, lastText, lastRect.width())
        fitTimerValue(bestPaint, bestText, bestRect.width())
        fitTimerValue(accentPaint, lapText, lapRect.width())

        drawTimerColumn(canvas, "CURRENT", currentText, currentRect, labelPaint, valuePaint)
        drawTimerColumn(canvas, "LAST", lastText, lastRect, labelPaint, secondaryValuePaint)
        drawTimerColumn(canvas, "BEST", bestText, bestRect, labelPaint, bestPaint)
        drawTimerColumn(canvas, "LAP", lapText, lapRect, labelPaint, accentPaint)

        labelPaint.textSize = labelSize
        valuePaint.textSize = valueSize
        secondaryValuePaint.textSize = secondarySize
        bestPaint.textSize = bestSize
        accentPaint.textSize = accentSize
    }

    private fun drawTimerColumn(
        canvas: Canvas,
        label: String,
        value: String,
        column: RectF,
        labelPaint: Paint,
        valuePaint: Paint
    ) {
        val gap = barHeightPx * 0.016f
        val block = textHeight(labelPaint) + gap + textHeight(valuePaint)
        var top = column.centerY() - block / 2f
        top = drawTopAlignedTextCentered(canvas, label, column.centerX(), top, labelPaint) + gap
        drawTopAlignedTextCentered(canvas, value, column.centerX(), top, valuePaint)
    }

    private fun fitTimerValue(paint: Paint, text: String, columnWidth: Float) {
        val maxWidth = (columnWidth * 0.90f).coerceAtLeast(8f)
        val width = paint.measureText(text)
        if (width > maxWidth) {
            paint.textSize = paint.textSize * (maxWidth / width)
        }
    }

    private fun drawCard(canvas: Canvas, rect: RectF, filled: Boolean = false) {
        if (filled) {
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cardPaint)
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cardStrokePaint)
    }

    private fun drawSpeedCard(canvas: Canvas, speedKmh: Float) {
        val speedValue = speedKmh.coerceAtLeast(0f) * speedFactor
        val speedText = String.format(Locale.getDefault(), "%.0f", speedValue)
        val gap = barHeightPx * HudBottomBar.STACK_GAP
        val blockHeight = textHeight(speedPaint) + gap + textHeight(speedUnitPaint)
        var top = speedCard.centerY() - blockHeight / 2f
        top = drawTopAlignedTextCentered(canvas, speedText, speedCard.centerX(), top, speedPaint) + gap
        drawTopAlignedTextCentered(canvas, speedUnitLabel, speedCard.centerX(), top, speedUnitPaint)
    }

    private fun drawGCard(canvas: Canvas, frame: GRenderFrame) {
        drawCarGCard(canvas, frame)
    }

    private fun drawMotorcycleLeanGCombo(canvas: Canvas, frame: GRenderFrame, leanAngleDeg: Float) {
        if (gCard.isEmpty) return
        val widgetWidth = min(gCard.width() - barHeightPx * 0.08f, barHeightPx * 2.05f)
        val widgetHeight = min(gCard.height(), barHeightPx * 1.18f)
        val widget = RectF(
            gCard.centerX() - widgetWidth / 2f,
            gCard.centerY() - widgetHeight / 2f,
            gCard.centerX() + widgetWidth / 2f,
            gCard.centerY() + widgetHeight / 2f
        )
        val padX = widget.width() * 0.10f
        val padY = barHeightPx * 0.045f
        brakingPaint.textSize = barHeightPx * 0.125f
        accelPaint.textSize = brakingPaint.textSize
        val labelH = textHeight(brakingPaint)
        val gBarH = (barHeightPx * 0.09f).coerceAtLeast(4f)
        val barBottom = widget.bottom - padY - labelH
        val barTop = barBottom - gBarH
        val barRect = RectF(
            widget.left + padX,
            barTop,
            widget.right - padX,
            barBottom
        )

        val topInset = leanArcTrackPaint.strokeWidth * 0.55f
        val pivotY = barRect.top - barHeightPx * 0.03f
        val maxRadiusX = (widget.width() / 2f) - padX
        val maxRadiusY = (pivotY - (widget.top + topInset)).coerceAtLeast(18f)
        val radius = min(maxRadiusX, maxRadiusY)
        val oval = RectF(
            widget.centerX() - radius,
            pivotY - radius,
            widget.centerX() + radius,
            pivotY + radius
        )

        drawLeanArc(canvas, oval, leanAngleDeg)
        drawHorizontalGBar(canvas, barRect, frame)
    }

    private fun drawCarGCard(canvas: Canvas, frame: GRenderFrame) {
        val insetX = gCard.width() * 0.08f
        val insetY = gCard.height() * 0.05f
        val cardContent = RectF(
            gCard.left + insetX,
            gCard.top + insetY,
            gCard.right - insetX,
            gCard.bottom - insetY
        )
        val visualMaxG = frame.visualMaxG
        val squareSize = min(cardContent.width(), barHeightPx * 1.08f).coerceAtLeast(20f)
        val gaugeLeft = cardContent.left + (cardContent.width() - squareSize) / 2f
        val gaugeRect = RectF(
            gaugeLeft,
            cardContent.top + (cardContent.height() - squareSize) / 2f,
            gaugeLeft + squareSize,
            cardContent.top + (cardContent.height() - squareSize) / 2f + squareSize
        )
        drawGaugeGrid(canvas, gaugeRect, visualMaxG, showScaleLabels = false)
        val centerX = gaugeRect.centerX()
        val centerY = gaugeRect.centerY()
        val graphRadius = carHudGraphRadius(gaugeRect)
        val smoothLateral = applyGaugeSoftDeadband(frame.currentLateralG, 0.04f)
        val smoothLongitudinal = applyGaugeSoftDeadband(frame.currentLongitudinalG, 0.04f)
        val scaledLateral = (smoothLateral / visualMaxG).coerceIn(-1f, 1f)
        val scaledLongitudinal = (smoothLongitudinal / visualMaxG).coerceIn(-1f, 1f)
        val dotX = centerX - scaledLateral * graphRadius
        val dotY = centerY - scaledLongitudinal * graphRadius
        canvas.drawCircle(dotX, dotY, gaugeDotRadius * 1.8f, gaugeDotGlowPaint)
        canvas.drawCircle(dotX, dotY, gaugeDotRadius, gaugeDotPaint)
        val valueText = String.format(Locale.US, "%.2f", frame.currentResultG)
        val valueWidth = gaugeValuePaint.measureText(valueText)
        val valueTop = (dotY + gaugeDotRadius + margin * 0.10f)
            .coerceAtMost(cardContent.bottom - textHeight(gaugeValuePaint))
        val valueLeft = (dotX - valueWidth / 2f).coerceIn(cardContent.left, cardContent.right - valueWidth)
        drawTopAlignedText(canvas, valueText, valueLeft, valueTop, gaugeValueOutlinePaint)
        drawTopAlignedText(canvas, valueText, valueLeft, valueTop, gaugeValuePaint)
    }

    private fun carHudGraphRadius(gaugeRect: RectF): Float {
        return (gaugeRect.width() / 2f - max(gaugeGridPaint.strokeWidth, gaugeDotRadius * 0.40f))
            .coerceAtLeast(gaugeRect.width() * 0.38f)
    }

    private fun drawGaugeGrid(
        canvas: Canvas,
        gaugeRect: RectF,
        visualMaxG: Float,
        showScaleLabels: Boolean = true
    ) {
        val centerX = gaugeRect.centerX()
        val centerY = gaugeRect.centerY()
        val graphRadius = carHudGraphRadius(gaugeRect)
        val level1G = visualMaxG / 3f
        val level2G = visualMaxG * (2f / 3f)
        val level1Radius = graphRadius * (level1G / visualMaxG)
        val level2Radius = graphRadius * (level2G / visualMaxG)
        canvas.drawCircle(centerX, centerY, level1Radius, gaugeGridPaint)
        canvas.drawCircle(centerX, centerY, level2Radius, gaugeGridPaint)
        canvas.drawCircle(centerX, centerY, graphRadius, gaugeGridPaint)
        canvas.drawLine(centerX - graphRadius, centerY, centerX + graphRadius, centerY, gaugeGridPaint)
        canvas.drawLine(centerX, centerY - graphRadius, centerX, centerY + graphRadius, gaugeGridPaint)
        if (showScaleLabels) {
            val labelTop = centerY - textHeight(gaugeLabelPaint) - margin * 0.02f
            drawTopAlignedText(canvas, formatGaugeLabel(level1G), centerX + level1Radius + margin * 0.10f, labelTop, gaugeLabelPaint)
            drawTopAlignedText(canvas, formatGaugeLabel(level2G), centerX + level2Radius + margin * 0.10f, labelTop, gaugeLabelPaint)
            drawTopAlignedText(canvas, formatGaugeLabel(visualMaxG), centerX + graphRadius + margin * 0.10f, labelTop, gaugeLabelPaint)
        }
    }

    private fun drawMapCard(canvas: Canvas, routePoint: GeoPoint?, speedKmh: Float, clockMs: Long) {
        val ribbon = perspectiveMap
        val projector = mapProjector
        if (followMiniMap && ribbon != null && ribbon.isReady && projector != null) {
            val followLocal = routePoint?.let { projector.nearestLocal(it) }
                ?: projector.pathPoints.firstOrNull()
            val startLocal = startMarker?.let { projector.nearestLocal(it) }
                ?: projector.pathPoints.firstOrNull()
            if (followLocal != null) {
                ribbon.drawFollow(
                    canvas = canvas,
                    target = mapInnerRect,
                    follow = followLocal,
                    start = startLocal,
                    speedKmh = speedKmh,
                    clockMs = clockMs,
                    projector = projector,
                    fill = mapRibbonFillPaint,
                    edge = mapRibbonEdgePaint,
                    shadow = mapRibbonShadowPaint,
                    dot = mapDotPaint,
                    glow = mapDotGlowPaint,
                    checkWhite = mapCheckWhitePaint,
                    checkBlack = mapCheckBlackPaint
                )
                return
            }
        }
        if (!mapPath.isEmpty) {
            canvas.drawPath(mapPath, mapStrokePaint)
        }
        if (projector == null) return
        val startLocal = startMarker?.let { projector.nearestLocal(it) }
            ?: projector.pathPoints.firstOrNull()
        if (startLocal != null) {
            drawOverviewStartFinishLine(canvas, projector, startLocal, mapInnerRect)
        }
        val point = routePoint ?: return
        val dot = projector.projectToPolyline(point, mapInnerRect)
        val dotRadius = max(8f, mapStrokePaint.strokeWidth * 1.05f)
        canvas.drawCircle(dot.x, dot.y, dotRadius * 1.9f, mapDotGlowPaint)
        canvas.drawCircle(dot.x, dot.y, dotRadius, mapDotPaint)
    }

    private fun drawOverviewStartFinishLine(
        canvas: Canvas,
        projector: MiniMapProjector,
        startLocal: PointF,
        target: RectF
    ) {
        val heading = projector.headingAt(startLocal)
        val alongX = cos(heading)
        val alongY = sin(heading)
        val acrossX = -alongY
        val acrossY = alongX
        val origin = projector.mapPoint(startLocal, target)
        val alongProbe = projector.mapPoint(
            PointF(startLocal.x + alongX, startLocal.y + alongY),
            target
        )
        val pxPerMeter = hypot(alongProbe.x - origin.x, alongProbe.y - origin.y).coerceAtLeast(0.04f)
        val acrossPx = max(
            mapStrokePaint.strokeWidth * 2.8f,
            min(target.width(), target.height()) * 0.12f
        )
        val acrossHalf = (acrossPx / pxPerMeter) / 2f
        val cols = 8
        val rows = 2
        val colW = (acrossHalf * 2f) / cols
        val rowD = colW * 0.88f
        val along0 = -rowD
        val left = -acrossHalf
        val save = canvas.save()
        canvas.clipRect(target)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x0 = left + col * colW
                val x1 = x0 + colW
                val y0 = along0 + row * rowD
                val y1 = y0 + rowD
                val p00 = projector.mapPoint(
                    PointF(startLocal.x + acrossX * x0 + alongX * y0, startLocal.y + acrossY * x0 + alongY * y0),
                    target
                )
                val p10 = projector.mapPoint(
                    PointF(startLocal.x + acrossX * x1 + alongX * y0, startLocal.y + acrossY * x1 + alongY * y0),
                    target
                )
                val p11 = projector.mapPoint(
                    PointF(startLocal.x + acrossX * x1 + alongX * y1, startLocal.y + acrossY * x1 + alongY * y1),
                    target
                )
                val p01 = projector.mapPoint(
                    PointF(startLocal.x + acrossX * x0 + alongX * y1, startLocal.y + acrossY * x0 + alongY * y1),
                    target
                )
                mapCheckPath.reset()
                mapCheckPath.moveTo(p00.x, p00.y)
                mapCheckPath.lineTo(p10.x, p10.y)
                mapCheckPath.lineTo(p11.x, p11.y)
                mapCheckPath.lineTo(p01.x, p01.y)
                mapCheckPath.close()
                val paint = if ((row + col) % 2 == 0) mapCheckBlackPaint else mapCheckWhitePaint
                canvas.drawPath(mapCheckPath, paint)
            }
        }
        canvas.restoreToCount(save)
    }

    private fun drawLeanArc(canvas: Canvas, oval: RectF, leanAngleDeg: Float) {
        val leanAbs = abs(leanAngleDeg).coerceAtLeast(0f)
        val normalized = (leanAbs / LEAN_GAUGE_MAX_DEG).coerceIn(0f, 1f)
        val currentColor = resolveLeanGaugeColor(normalized)
        val centerX = oval.centerX()
        val radius = oval.width() / 2f - leanArcColorPaint.strokeWidth * 0.5f

        canvas.drawArc(oval, 180f, 180f, false, leanArcTrackPaint)
        leanArcColorPaint.shader = SweepGradient(
            oval.centerX(),
            oval.centerY(),
            intArrayOf(LEAN_RED, LEAN_RED, LEAN_YELLOW, LEAN_GREEN, LEAN_YELLOW, LEAN_RED),
            floatArrayOf(0f, 0.50f, 0.625f, 0.75f, 0.875f, 1f)
        )
        canvas.drawArc(oval, 180f, 180f, false, leanArcColorPaint)
        leanArcColorPaint.shader = null

        val needleSweep = normalized * 90f
        val needleDeg = if (leanAngleDeg >= 0f) 270f + needleSweep else 270f - needleSweep
        val needleRad = Math.toRadians(needleDeg.toDouble())
        val innerRadius = radius * 0.72f
        val outerRadius = radius * 0.98f
        val startX = oval.centerX() + cos(needleRad).toFloat() * innerRadius
        val startY = oval.centerY() + sin(needleRad).toFloat() * innerRadius
        val tipX = oval.centerX() + cos(needleRad).toFloat() * outerRadius
        val tipY = oval.centerY() + sin(needleRad).toFloat() * outerRadius
        leanNeedlePaint.color = currentColor
        canvas.drawLine(startX, startY, tipX, tipY, leanNeedlePaint)

        leanValuePaint.color = currentColor
        leanValuePaint.textSize = (oval.width() * 0.16f).coerceAtLeast(barHeightPx * 0.22f)
        val centerText = String.format(Locale.getDefault(), "%.0f°", leanAbs)
        val centerTop = oval.centerY() - textHeight(leanValuePaint) * 1.12f
        drawTopAlignedTextCentered(canvas, centerText, centerX, centerTop, leanValuePaint)
    }

    private fun drawHorizontalGBar(canvas: Canvas, barRect: RectF, frame: GRenderFrame) {
        val radius = barRect.height() / 2f
        canvas.drawRoundRect(barRect, radius, radius, gBarTrackPaint)
        canvas.drawRoundRect(barRect, radius, radius, gBarStrokePaint)
        canvas.drawLine(barRect.centerX(), barRect.top + 2f, barRect.centerX(), barRect.bottom - 2f, gBarDividerPaint)

        val brakingNow = max(0f, frame.currentLongitudinalG)
        val accelNow = max(0f, -frame.currentLongitudinalG)
        val scaleMax = max(max(frame.maxBraking, frame.maxAccel), 0.9f).coerceAtMost(3.0f)
        val inset = barRect.height() * 0.18f
        val inner = RectF(
            barRect.left + inset,
            barRect.top + inset,
            barRect.right - inset,
            barRect.bottom - inset
        )
        val halfWidth = inner.width() / 2f
        val centerX = inner.centerX()
        val fillRadius = inner.height() / 2f

        if (brakingNow > 0.02f) {
            val fillW = (brakingNow / scaleMax).coerceIn(0f, 1f) * halfWidth
            gBarFillPaint.color = Color.argb(150, 230, 69, 99)
            canvas.drawRoundRect(
                RectF(centerX - fillW, inner.top, centerX, inner.bottom),
                fillRadius,
                fillRadius,
                gBarFillPaint
            )
        }
        if (accelNow > 0.02f) {
            val fillW = (accelNow / scaleMax).coerceIn(0f, 1f) * halfWidth
            gBarFillPaint.color = Color.argb(150, 0, 204, 117)
            canvas.drawRoundRect(
                RectF(centerX, inner.top, centerX + fillW, inner.bottom),
                fillRadius,
                fillRadius,
                gBarFillPaint
            )
        }

        val thumbWidth = (inner.width() * 0.12f).coerceIn(inner.height() * 1.4f, inner.width() * 0.18f)
        val travel = (halfWidth - thumbWidth / 2f).coerceAtLeast(1f)
        val normalized = (frame.currentLongitudinalG / scaleMax).coerceIn(-1f, 1f)
        val thumbCx = centerX - normalized * travel
        val thumb = RectF(
            thumbCx - thumbWidth / 2f,
            inner.top,
            thumbCx + thumbWidth / 2f,
            inner.bottom
        )
        gBarThumbPaint.color = when {
            brakingNow >= accelNow && brakingNow > 0.02f -> Color.argb(255, 230, 69, 99)
            accelNow > 0.02f -> Color.argb(255, 0, 204, 117)
            else -> Color.argb(220, 210, 220, 230)
        }
        canvas.drawRoundRect(thumb, fillRadius, fillRadius, gBarThumbPaint)

        val labelTop = barRect.bottom + barHeightPx * 0.012f
        drawTopAlignedText(canvas, formatGWithUnit(brakingNow), barRect.left, labelTop, brakingPaint)
        drawTopAlignedTextRight(canvas, formatGWithUnit(accelNow), barRect.right, labelTop, accelPaint)
    }

    private fun formatGWithUnit(value: Float): String = String.format(Locale.US, "%.1fg", value.coerceAtLeast(0f))

    private fun resolveLeanGaugeColor(normalized: Float): Int {
        val clamped = normalized.coerceIn(0f, 1f)
        return when {
            clamped < 0.45f -> interpolateColor(
                Color.argb(255, 44, 214, 110),
                Color.argb(255, 255, 196, 79),
                clamped / 0.45f
            )
            else -> interpolateColor(
                Color.argb(255, 255, 196, 79),
                Color.argb(255, 255, 78, 78),
                (clamped - 0.45f) / 0.55f
            )
        }
    }

    private fun interpolateColor(startColor: Int, endColor: Int, progress: Float): Int {
        val clamped = progress.coerceIn(0f, 1f)
        return Color.argb(
            lerp(Color.alpha(startColor).toFloat(), Color.alpha(endColor).toFloat(), clamped).toInt(),
            lerp(Color.red(startColor).toFloat(), Color.red(endColor).toFloat(), clamped).toInt(),
            lerp(Color.green(startColor).toFloat(), Color.green(endColor).toFloat(), clamped).toInt(),
            lerp(Color.blue(startColor).toFloat(), Color.blue(endColor).toFloat(), clamped).toInt()
        )
    }

    private fun drawTopAlignedText(canvas: Canvas, text: String, left: Float, top: Float, paint: Paint): Float {
        val metrics = paint.fontMetrics
        val baseline = top - metrics.ascent
        canvas.drawText(text, left, baseline, paint)
        return baseline + metrics.descent
    }

    private fun drawTopAlignedTextRight(canvas: Canvas, text: String, right: Float, top: Float, paint: Paint): Float {
        return drawTopAlignedText(canvas, text, right - paint.measureText(text), top, paint)
    }

    private fun drawTopAlignedTextCentered(canvas: Canvas, text: String, centerX: Float, top: Float, paint: Paint): Float {
        return drawTopAlignedText(canvas, text, centerX - paint.measureText(text) / 2f, top, paint)
    }

    private fun textHeight(paint: Paint): Float {
        val metrics = paint.fontMetrics
        return metrics.descent - metrics.ascent
    }

    private fun applyGaugeSoftDeadband(value: Float, threshold: Float): Float {
        val magnitude = abs(value)
        if (magnitude <= 0.002f) return 0f
        if (threshold <= 0f || magnitude >= threshold) return value
        val t = (magnitude / threshold).coerceIn(0f, 1f)
        return value * t * t
    }

    private fun formatGaugeLabel(value: Float): String = String.format(Locale.US, "%.1fg", value)

    private fun formatGCompact(value: Float): String = String.format(Locale.US, "%.1f", value.coerceAtLeast(0f))

    private fun formatTime(timeMs: Long): String = LapTimeFormatter.formatMs(timeMs, padMinutes = true)

    private class PerspectiveTrackRibbon(worldPoints: List<PointF>) {
        val isReady: Boolean get() = samples.size >= 8

        private val samples: List<PointF> = downsample(worldPoints, 200)
        private val span: Float
        private var smoothHeading = Float.NaN
        private var followProgress = Float.NaN
        private var lastClockMs = 0L
        private var camX = 0f
        private var camY = 0f
        private var camZ = 1f
        private var fwdX = 0f
        private var fwdY = 1f
        private var fwdZ = 0f
        private var rightX = 1f
        private var rightY = 0f
        private var rightZ = 0f
        private var upX = 0f
        private var upY = 0f
        private var upZ = 1f
        private var near = 1f
        private var screenScale = 1f
        private var screenOffX = 0f
        private var screenOffY = 0f
        private val ribbonPath = Path()
        private val shadowPath = Path()
        private val centerPath = Path()
        private val markerPath = Path()
        private val checkPath = Path()
        private val leftPts = ArrayList<PointF>(220)
        private val rightPts = ArrayList<PointF>(220)

        init {
            if (samples.isEmpty()) {
                span = 80f
            } else {
                val minX = samples.minOf { it.x }
                val maxX = samples.maxOf { it.x }
                val minY = samples.minOf { it.y }
                val maxY = samples.maxOf { it.y }
                span = max(maxX - minX, maxY - minY).coerceAtLeast(40f)
            }
        }

        fun drawFollow(
            canvas: Canvas,
            target: RectF,
            follow: PointF,
            start: PointF?,
            speedKmh: Float,
            clockMs: Long,
            projector: MiniMapProjector,
            fill: Paint,
            edge: Paint,
            shadow: Paint,
            dot: Paint,
            glow: Paint,
            checkWhite: Paint,
            checkBlack: Paint
        ) {
            if (samples.size < 8 || target.width() < 8f || target.height() < 8f) return
            val dt = when {
                lastClockMs == 0L || clockMs < lastClockMs -> 1f / 60f
                else -> ((clockMs - lastClockMs) / 1000f).coerceIn(0.008f, 0.080f)
            }
            lastClockMs = clockMs
            val targetS = projector.progressAt(follow)
            if (followProgress.isNaN()) {
                followProgress = targetS
            } else {
                val speedMs = (speedKmh / 3.6f).coerceAtLeast(0f)
                val error = projector.progressDelta(followProgress, targetS)
                val blend = 1f - exp(-dt / 0.32f)
                val coast = speedMs * dt
                followProgress += coast + (error - coast) * blend
                followProgress = projector.wrapProgress(followProgress)
            }
            val followNow = projector.pointAtProgress(followProgress)
            val headingNow = projector.headingAt(followNow)
            val headingBlend = 1f - exp(-dt / 0.40f)
            smoothHeading = if (smoothHeading.isNaN()) {
                headingNow
            } else {
                lerpAngle(smoothHeading, headingNow, headingBlend)
            }
            val viewDist = (span * 0.26f).coerceIn(90f, 320f)
            val camBack = viewDist * 0.34f
            val camHeight = viewDist * 0.50f
            val lookAhead = viewDist * 0.18f
            val halfWidth = (viewDist * 0.042f).coerceIn(5.5f, 13f)
            val f2x = cos(smoothHeading)
            val f2y = sin(smoothHeading)
            camX = followNow.x - f2x * camBack
            camY = followNow.y - f2y * camBack
            camZ = camHeight
            val lookX = followNow.x + f2x * lookAhead
            val lookY = followNow.y + f2y * lookAhead
            val toX = lookX - camX
            val toY = lookY - camY
            val toZ = 0f - camZ
            val toLen = hypot3(toX, toY, toZ).coerceAtLeast(0.001f)
            fwdX = toX / toLen
            fwdY = toY / toLen
            fwdZ = toZ / toLen
            var rx = fwdY
            var ry = -fwdX
            var rz = 0f
            val rLen = hypot3(rx, ry, rz).coerceAtLeast(0.001f)
            rx /= rLen
            ry /= rLen
            rz /= rLen
            rightX = rx
            rightY = ry
            rightZ = rz
            upX = rightY * fwdZ - rightZ * fwdY
            upY = rightZ * fwdX - rightX * fwdZ
            upZ = rightX * fwdY - rightY * fwdX
            val uLen = hypot3(upX, upY, upZ).coerceAtLeast(0.001f)
            upX /= uLen
            upY /= uLen
            upZ /= uLen
            near = hypot(camBack, camHeight) * 0.14f

            val followNdc = projectNdc(followNow.x, followNow.y, 0f)
            val aheadNdc = projectNdc(followNow.x + f2x * viewDist, followNow.y + f2y * viewDist, 0f)
            if (followNdc == null || aheadNdc == null) return
            val ndcDy = (followNdc.y - aheadNdc.y).coerceAtLeast(0.001f)
            val followScreenY = target.bottom - target.height() * 0.18f
            val aheadScreenY = target.top + target.height() * 0.08f
            screenScale = (followScreenY - aheadScreenY) / ndcDy
            screenOffX = target.centerX() - followNdc.x * screenScale
            screenOffY = followScreenY - followNdc.y * screenScale

            ribbonPath.reset()
            shadowPath.reset()
            centerPath.reset()
            markerPath.reset()
            leftPts.clear()
            rightPts.clear()

            val count = samples.size
            val closed = isClosed(halfWidth)
            var centerStarted = false
            var runActive = false
            for (index in 0 until count) {
                val point = samples[index]
                val prev = samples[prevIndex(index, closed)]
                val next = samples[nextIndex(index, closed)]
                val dx = next.x - prev.x
                val dy = next.y - prev.y
                val len = hypot(dx, dy).coerceAtLeast(0.001f)
                val nx = -dy / len * halfWidth
                val ny = dx / len * halfWidth
                val left = toScreen(point.x + nx, point.y + ny, 0f)
                val right = toScreen(point.x - nx, point.y - ny, 0f)
                val center = toScreen(point.x, point.y, 0f)
                val visible = left != null && right != null && center != null
                if (visible) {
                    leftPts += left!!
                    rightPts += right!!
                    if (!centerStarted) {
                        centerPath.moveTo(center!!.x, center.y)
                        centerStarted = true
                    } else {
                        centerPath.lineTo(center!!.x, center.y)
                    }
                    runActive = true
                } else if (runActive) {
                    appendRibbonRun()
                    leftPts.clear()
                    rightPts.clear()
                    runActive = false
                    centerStarted = false
                }
            }
            if (runActive) appendRibbonRun()
            if (!ribbonPath.isEmpty) {
                shadowPath.set(ribbonPath)
                shadowPath.offset(0f, min(target.width(), target.height()) * 0.014f)
            }

            val save = canvas.save()
            canvas.clipRect(target)
            if (!shadowPath.isEmpty) canvas.drawPath(shadowPath, shadow)
            if (!ribbonPath.isEmpty) canvas.drawPath(ribbonPath, fill)
            if (!centerPath.isEmpty) canvas.drawPath(centerPath, edge)
            if (start != null) {
                drawStartFinishLine(canvas, start, projector, halfWidth, checkWhite, checkBlack)
            }
            drawGroundDot(canvas, target, followNow, f2x, f2y, halfWidth, dot, glow)
            canvas.restoreToCount(save)
        }

        private fun drawStartFinishLine(
            canvas: Canvas,
            start: PointF,
            projector: MiniMapProjector,
            halfWidth: Float,
            checkWhite: Paint,
            checkBlack: Paint
        ) {
            val heading = projector.headingAt(start)
            val alongX = cos(heading)
            val alongY = sin(heading)
            val acrossX = -alongY
            val acrossY = alongX
            val cols = 8
            val rows = 2
            val acrossHalf = halfWidth * 1.04f
            val colW = (acrossHalf * 2f) / cols
            val rowD = (colW * 0.88f).coerceIn(1.6f, 4.2f)
            val lift = halfWidth * 0.07f
            val along0 = -rowD
            val left = -acrossHalf
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val x0 = left + col * colW
                    val x1 = x0 + colW
                    val y0 = along0 + row * rowD
                    val y1 = y0 + rowD
                    val p00 = toScreen(start.x + acrossX * x0 + alongX * y0, start.y + acrossY * x0 + alongY * y0, lift)
                    val p10 = toScreen(start.x + acrossX * x1 + alongX * y0, start.y + acrossY * x1 + alongY * y0, lift)
                    val p11 = toScreen(start.x + acrossX * x1 + alongX * y1, start.y + acrossY * x1 + alongY * y1, lift)
                    val p01 = toScreen(start.x + acrossX * x0 + alongX * y1, start.y + acrossY * x0 + alongY * y1, lift)
                    if (p00 == null || p10 == null || p11 == null || p01 == null) continue
                    checkPath.reset()
                    checkPath.moveTo(p00.x, p00.y)
                    checkPath.lineTo(p10.x, p10.y)
                    checkPath.lineTo(p11.x, p11.y)
                    checkPath.lineTo(p01.x, p01.y)
                    checkPath.close()
                    val paint = if ((row + col) % 2 == 0) checkBlack else checkWhite
                    canvas.drawPath(checkPath, paint)
                }
            }
        }

        private fun drawGroundDot(
            canvas: Canvas,
            target: RectF,
            follow: PointF,
            f2x: Float,
            f2y: Float,
            halfWidth: Float,
            dot: Paint,
            glow: Paint
        ) {
            val lift = halfWidth * 0.08f
            val center = toScreen(follow.x, follow.y, lift) ?: return
            val probe = toScreen(follow.x - f2y, follow.y + f2x, lift)
            val desiredPx = max(9f, min(target.width(), target.height()) * 0.042f)
            val pixelsPerMeter = if (probe != null) {
                hypot(probe.x - center.x, probe.y - center.y).coerceAtLeast(0.08f)
            } else {
                desiredPx / 4f
            }
            val worldR = (desiredPx / pixelsPerMeter).coerceIn(1.4f, halfWidth * 0.9f)
            fun fillOval(radius: Float, paint: Paint) {
                markerPath.reset()
                val steps = 22
                for (index in 0..steps) {
                    val angle = (index.toFloat() / steps) * (Math.PI * 2.0).toFloat()
                    val mapped = toScreen(
                        follow.x + cos(angle) * radius,
                        follow.y + sin(angle) * radius,
                        lift
                    ) ?: continue
                    if (markerPath.isEmpty) {
                        markerPath.moveTo(mapped.x, mapped.y)
                    } else {
                        markerPath.lineTo(mapped.x, mapped.y)
                    }
                }
                markerPath.close()
                if (!markerPath.isEmpty) canvas.drawPath(markerPath, paint)
            }
            fillOval(worldR * 1.85f, glow)
            fillOval(worldR, dot)
        }

        private fun appendRibbonRun() {
            if (leftPts.size < 2 || rightPts.size < 2) return
            ribbonPath.moveTo(leftPts.first().x, leftPts.first().y)
            for (index in 1 until leftPts.size) {
                ribbonPath.lineTo(leftPts[index].x, leftPts[index].y)
            }
            for (index in rightPts.lastIndex downTo 0) {
                ribbonPath.lineTo(rightPts[index].x, rightPts[index].y)
            }
            ribbonPath.close()
        }

        private fun projectNdc(worldX: Float, worldY: Float, worldZ: Float): PointF? {
            val relX = worldX - camX
            val relY = worldY - camY
            val relZ = worldZ - camZ
            val depth = relX * fwdX + relY * fwdY + relZ * fwdZ
            if (depth < near) return null
            val cx = relX * rightX + relY * rightY + relZ * rightZ
            val cy = relX * upX + relY * upY + relZ * upZ
            return PointF(cx / depth, -cy / depth)
        }

        private fun toScreen(worldX: Float, worldY: Float, worldZ: Float): PointF? {
            val ndc = projectNdc(worldX, worldY, worldZ) ?: return null
            return PointF(ndc.x * screenScale + screenOffX, ndc.y * screenScale + screenOffY)
        }

        private fun isClosed(halfWidth: Float): Boolean {
            if (samples.size < 3) return false
            return hypot(samples.first().x - samples.last().x, samples.first().y - samples.last().y) < halfWidth * 6f
        }

        private fun prevIndex(index: Int, closed: Boolean): Int {
            if (index > 0) return index - 1
            return if (closed) (samples.lastIndex - 1).coerceAtLeast(0) else 0
        }

        private fun nextIndex(index: Int, closed: Boolean): Int {
            if (index < samples.lastIndex) return index + 1
            return if (closed) 1.coerceAtMost(samples.lastIndex) else samples.lastIndex
        }

        private fun hypot3(x: Float, y: Float, z: Float): Float = sqrt(x * x + y * y + z * z)

        private fun lerpAngle(from: Float, to: Float, t: Float): Float {
            var delta = to - from
            val pi = Math.PI.toFloat()
            while (delta > pi) delta -= pi * 2f
            while (delta < -pi) delta += pi * 2f
            return from + delta * t
        }

        companion object {
            private fun downsample(points: List<PointF>, maxCount: Int): List<PointF> {
                if (points.size <= maxCount) return points
                if (maxCount < 8) return points.take(8)
                val step = (points.size - 1).toFloat() / (maxCount - 1).toFloat()
                val sampled = ArrayList<PointF>(maxCount)
                for (index in 0 until maxCount) {
                    val source = (index * step).toInt().coerceIn(0, points.lastIndex)
                    sampled += points[source]
                }
                return sampled
            }
        }
    }

    private class MiniMapProjector(points: List<GeoPoint>) {
        val pathPoints: List<PointF>
        val pointCount: Int
        private val originLatitude: Double
        private val originLongitude: Double
        private val latScale: Double
        private val lonScale: Double
        private val minX: Float
        private val maxX: Float
        private val minY: Float
        private val maxY: Float
        private val prefix: FloatArray
        val pathLength: Float
        val loopClosed: Boolean
        private val loopWrap: Boolean

        init {
            if (points.isEmpty()) {
                pathPoints = emptyList()
                pointCount = 0
                originLatitude = 0.0
                originLongitude = 0.0
                latScale = 111_320.0
                lonScale = 111_320.0
                minX = 0f
                maxX = 1f
                minY = 0f
                maxY = 1f
                prefix = FloatArray(0)
                pathLength = 1f
                loopClosed = false
                loopWrap = false
            } else {
                val origin = points.first()
                originLatitude = origin.latitude
                originLongitude = origin.longitude
                latScale = 111_320.0
                lonScale = cos(Math.toRadians(origin.latitude)).coerceAtLeast(0.15) * 111_320.0
                val localPoints = points.map { point ->
                    PointF(
                        ((point.longitude - origin.longitude) * lonScale).toFloat(),
                        ((point.latitude - origin.latitude) * latScale).toFloat()
                    )
                }
                pathPoints = localPoints
                pointCount = localPoints.size
                minX = localPoints.minOfOrNull { point -> point.x } ?: 0f
                maxX = localPoints.maxOfOrNull { point -> point.x } ?: 1f
                minY = localPoints.minOfOrNull { point -> point.y } ?: 0f
                maxY = localPoints.maxOfOrNull { point -> point.y } ?: 1f
                val pref = FloatArray(localPoints.size)
                var acc = 0f
                for (index in 1 until localPoints.size) {
                    acc += hypot(
                        localPoints[index].x - localPoints[index - 1].x,
                        localPoints[index].y - localPoints[index - 1].y
                    )
                    pref[index] = acc
                }
                prefix = pref
                val closeDist = if (localPoints.size >= 2) {
                    hypot(
                        localPoints.first().x - localPoints.last().x,
                        localPoints.first().y - localPoints.last().y
                    )
                } else {
                    0f
                }
                loopClosed = localPoints.size >= 4 && closeDist < 28f
                loopWrap = loopClosed && closeDist > 8f
                pathLength = (if (loopWrap) acc + closeDist else acc).coerceAtLeast(1f)
            }
        }

        fun createPath(target: RectF): Path {
            val path = Path()
            if (pathPoints.size < 2) return path
            pathPoints.forEachIndexed { index, point ->
                val mapped = mapPoint(point, target)
                if (index == 0) path.moveTo(mapped.x, mapped.y) else path.lineTo(mapped.x, mapped.y)
            }
            return path
        }

        fun mapPoint(source: PointF, target: RectF): PointF {
            val width = (maxX - minX).takeIf { value -> abs(value) > 0.001f } ?: 1f
            val height = (maxY - minY).takeIf { value -> abs(value) > 0.001f } ?: 1f
            val scale = min(target.width() / width, target.height() / height)
            val mappedWidth = width * scale
            val mappedHeight = height * scale
            val offsetX = target.left + (target.width() - mappedWidth) / 2f
            val offsetY = target.top + (target.height() - mappedHeight) / 2f
            return PointF(
                offsetX + (source.x - minX) * scale,
                offsetY + mappedHeight - (source.y - minY) * scale
            )
        }

        fun nearestLocal(point: GeoPoint): PointF {
            if (pathPoints.isEmpty()) return PointF(0f, 0f)
            if (pathPoints.size == 1) return pathPoints.first()
            val projectedInput = toLocal(point)
            var bestPoint = pathPoints.first()
            var bestDistance = Float.MAX_VALUE
            for (index in 0 until pathPoints.lastIndex) {
                val segmentStart = pathPoints[index]
                val segmentEnd = pathPoints[index + 1]
                val candidate = projectPointToSegment(projectedInput, segmentStart, segmentEnd)
                val distance = hypot(projectedInput.x - candidate.x, projectedInput.y - candidate.y)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestPoint = candidate
                }
            }
            return bestPoint
        }

        fun headingAt(local: PointF): Float {
            if (pathPoints.size < 2) return 0f
            var bestIndex = 0
            var bestDistance = Float.MAX_VALUE
            for (index in 0 until pathPoints.lastIndex) {
                val segmentStart = pathPoints[index]
                val segmentEnd = pathPoints[index + 1]
                val candidate = projectPointToSegment(local, segmentStart, segmentEnd)
                val distance = hypot(local.x - candidate.x, local.y - candidate.y)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = index
                }
            }
            val start = pathPoints[bestIndex]
            var endIndex = bestIndex + 1
            while (
                endIndex < pathPoints.lastIndex &&
                hypot(pathPoints[endIndex].x - start.x, pathPoints[endIndex].y - start.y) < 2f
            ) {
                endIndex++
            }
            val end = pathPoints[endIndex]
            val dx = end.x - start.x
            val dy = end.y - start.y
            if (dx * dx + dy * dy < 0.0001f) return 0f
            return atan2(dy, dx)
        }

        fun progressAt(local: PointF): Float {
            if (pathPoints.size < 2) return 0f
            val segmentCount = if (loopWrap) pathPoints.size else pathPoints.lastIndex
            var bestDistance = Float.MAX_VALUE
            var bestProgress = 0f
            for (index in 0 until segmentCount) {
                val start = pathPoints[index]
                val end = pathPoints[(index + 1) % pathPoints.size]
                val candidate = projectPointToSegment(local, start, end)
                val distance = hypot(local.x - candidate.x, local.y - candidate.y)
                if (distance < bestDistance) {
                    bestDistance = distance
                    val segLen = hypot(end.x - start.x, end.y - start.y).coerceAtLeast(0.001f)
                    val along = hypot(candidate.x - start.x, candidate.y - start.y)
                    bestProgress = prefix[index] + (along / segLen) * segLen
                }
            }
            return wrapProgress(bestProgress)
        }

        fun pointAtProgress(progress: Float): PointF {
            if (pathPoints.isEmpty()) return PointF(0f, 0f)
            if (pathPoints.size == 1) return pathPoints.first()
            val s = wrapProgress(progress)
            val segmentCount = if (loopWrap) pathPoints.size else pathPoints.lastIndex
            for (index in 0 until segmentCount) {
                val start = pathPoints[index]
                val end = pathPoints[(index + 1) % pathPoints.size]
                val base = prefix[index]
                val segLen = hypot(end.x - start.x, end.y - start.y)
                if (s <= base + segLen || index == segmentCount - 1) {
                    val t = if (segLen <= 0.0001f) 0f else ((s - base) / segLen).coerceIn(0f, 1f)
                    return PointF(start.x + (end.x - start.x) * t, start.y + (end.y - start.y) * t)
                }
            }
            return pathPoints.last()
        }

        fun progressDelta(from: Float, to: Float): Float {
            var delta = to - from
            if (!loopClosed) return delta
            val length = pathLength.coerceAtLeast(0.001f)
            delta %= length
            if (delta > length * 0.5f) delta -= length
            if (delta < -length * 0.5f) delta += length
            return delta
        }

        fun wrapProgress(progress: Float): Float {
            val length = pathLength.coerceAtLeast(0.001f)
            if (!loopClosed) return progress.coerceIn(0f, length)
            var s = progress % length
            if (s < 0f) s += length
            return s
        }

        fun toLocal(point: GeoPoint): PointF {
            return PointF(
                ((point.longitude - originLongitude) * lonScale).toFloat(),
                ((point.latitude - originLatitude) * latScale).toFloat()
            )
        }

        fun projectToPolyline(point: GeoPoint, target: RectF): PointF {
            if (pathPoints.isEmpty()) return PointF(target.centerX(), target.centerY())
            if (pathPoints.size == 1) return mapPoint(pathPoints.first(), target)
            val projectedInput = PointF(
                ((point.longitude - originLongitude) * lonScale).toFloat(),
                ((point.latitude - originLatitude) * latScale).toFloat()
            )
            var bestPoint = pathPoints.first()
            var bestDistance = Float.MAX_VALUE
            for (index in 0 until pathPoints.lastIndex) {
                val segmentStart = pathPoints[index]
                val segmentEnd = pathPoints[index + 1]
                val candidate = projectPointToSegment(projectedInput, segmentStart, segmentEnd)
                val distance = hypot(projectedInput.x - candidate.x, projectedInput.y - candidate.y)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestPoint = candidate
                }
            }
            return mapPoint(bestPoint, target)
        }

        private fun projectPointToSegment(point: PointF, start: PointF, end: PointF): PointF {
            val dx = end.x - start.x
            val dy = end.y - start.y
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared <= 0.0001f) return PointF(start.x, start.y)
            val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared).coerceIn(0f, 1f)
            return PointF(start.x + dx * t, start.y + dy * t)
        }
    }

    companion object {
        private const val LEAN_GAUGE_MAX_DEG = 65f
        private val APP_ACCENT_ORANGE = Color.rgb(255, 96, 32)
        private val LEAN_GREEN = Color.argb(255, 44, 214, 110)
        private val LEAN_YELLOW = Color.argb(255, 255, 210, 70)
        private val LEAN_RED = Color.argb(255, 255, 78, 78)

        fun gFrameFromLive(
            longitudinalG: Float,
            lateralG: Float,
            maxBraking: Float,
            maxAccel: Float,
            maxLeft: Float,
            maxRight: Float,
            maxResultG: Float
        ): GRenderFrame {
            val currentResult = sqrt(longitudinalG * longitudinalG + lateralG * lateralG)
            val peakResult = maxResultG.coerceAtLeast(currentResult)
            var visualMaxG = 1.5f
            while (visualMaxG < peakResult) {
                visualMaxG += 0.3f
            }
            return GRenderFrame(
                currentLongitudinalG = longitudinalG,
                currentLateralG = lateralG,
                currentResultG = currentResult,
                maxBraking = max(0f, maxBraking),
                maxAccel = max(0f, maxAccel),
                maxLeft = max(0f, maxLeft),
                maxRight = max(0f, maxRight),
                maxResultG = peakResult,
                visualMaxG = visualMaxG
            )
        }

        private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount
    }
}
