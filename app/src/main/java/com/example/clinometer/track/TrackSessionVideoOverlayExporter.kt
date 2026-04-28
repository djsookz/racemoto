package com.example.clinometer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@OptIn(UnstableApi::class)
class TrackSessionVideoOverlayExporter(context: Context) {

    data class ExportRequest(
        val inputUri: Uri,
        val outputFile: File,
        val trimStartMs: Long,
        val overlayModel: OverlayModel
    )

    data class OverlayModel(
        val isMotorcycle: Boolean,
        val videoStartSessionElapsedMs: Long,
        val lapSegments: List<LapSegment>,
        val routeSamples: List<RouteSample>,
        val gSamples: List<GSample>,
        val leanSamples: List<LeanSample>,
        val miniMapPoints: List<GeoPoint>
    )

    data class LapSegment(
        val lapNumber: Int,
        val startMs: Long,
        val durationMs: Long,
        val isCompleted: Boolean
    )

    data class RouteSample(
        val timeMs: Long,
        val geoPoint: GeoPoint,
        val speedKmh: Float
    )

    data class GSample(
        val timeMs: Long,
        val longitudinalG: Float,
        val lateralG: Float,
        val maxBraking: Float? = null,
        val maxAccel: Float? = null,
        val maxLeft: Float? = null,
        val maxRight: Float? = null,
        val maxResultG: Float? = null
    )

    data class LeanSample(
        val timeMs: Long,
        val angleDeg: Float
    )

    private data class SourceVideoSpec(
        val displayWidth: Int,
        val displayHeight: Int
    )

    private data class VideoExportProfile(
        val mimeType: String,
        val requestedBitrate: Int,
        val label: String
    )

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var exportThread: HandlerThread? = null
    private var transformer: Transformer? = null
    private var isFinishing = false

    fun export(
        request: ExportRequest,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        cancel()
        isFinishing = false

        val thread = HandlerThread("TrackSessionVideoOverlayExport").apply { start() }
        exportThread = thread
        val sourceVideoSpec = resolveSourceVideoSpec(request.inputUri)

        Handler(thread.looper).post {
            startExport(
                request = request,
                sourceVideoSpec = sourceVideoSpec,
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    private fun startExport(
        request: ExportRequest,
        sourceVideoSpec: SourceVideoSpec?,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        try {
            val videoExportProfile = resolveVideoExportProfile(sourceVideoSpec)
            if (videoExportProfile == null) {
                finishError(IllegalStateException("No compatible video encoder available on this device"), onError)
                return
            }

            if (request.outputFile.exists()) {
                request.outputFile.delete()
            }

            val overlay = SessionHudOverlay(request.overlayModel)
            val mediaItemBuilder = MediaItem.Builder().setUri(request.inputUri)
            if (request.trimStartMs > 0L) {
                mediaItemBuilder.setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(request.trimStartMs)
                        .build()
                )
            }

            val editedMediaItem = EditedMediaItem.Builder(mediaItemBuilder.build())
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(OverlayEffect(listOf(overlay)))
                    )
                )
                .build()

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    finishSuccess(request.outputFile, onSuccess)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    request.outputFile.delete()
                    transformer = null
                    android.util.Log.e(
                        "TrackVideoExport",
                        "Telemetry export failed (${exportException.getErrorCodeName()})",
                        exportException
                    )
                    finishError(exportException, onError)
                }
            }

            android.util.Log.i(
                "TrackVideoExport",
                "Starting telemetry export: container=mp4 codec=${videoExportProfile.label} audio=aac resolution=${sourceVideoSpec?.displayWidth ?: 0}x${sourceVideoSpec?.displayHeight ?: 0} bitrate=${videoExportProfile.requestedBitrate}"
            )

            val transformerBuilder = Transformer.Builder(appContext)
                .setLooper(Looper.myLooper() ?: exportThread?.looper ?: Looper.getMainLooper())
                .setVideoMimeType(videoExportProfile.mimeType)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setPortraitEncodingEnabled(true)
                .addListener(listener)
                .setEncoderFactory(buildEncoderFactory(videoExportProfile.requestedBitrate))

            transformer = transformerBuilder.build()

            transformer?.start(editedMediaItem, request.outputFile.absolutePath)
        } catch (error: Throwable) {
            request.outputFile.delete()
            transformer = null
            android.util.Log.e("TrackVideoExport", "Unable to start telemetry export", error)
            finishError(error, onError)
        }
    }

    private fun resolveSourceVideoSpec(inputUri: Uri): SourceVideoSpec? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(appContext, inputUri)
                val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                    ?: return@runCatching null
                val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                    ?: return@runCatching null
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull()
                    ?: 0
                val isQuarterTurn = rotation == 90 || rotation == 270
                val displayWidth = if (isQuarterTurn) rawHeight else rawWidth
                val displayHeight = if (isQuarterTurn) rawWidth else rawHeight
                SourceVideoSpec(displayWidth = displayWidth, displayHeight = displayHeight)
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    private fun resolveRequestedVideoBitrate(sourceVideoSpec: SourceVideoSpec?, mimeType: String): Int {
        val shortestEdge = sourceVideoSpec
            ?.let { min(it.displayWidth, it.displayHeight) }
            ?: 1080
        val isFullHd = shortestEdge >= 1080
        return when (mimeType) {
            MimeTypes.VIDEO_H265 -> if (isFullHd) EXPORT_VIDEO_BITRATE_HEVC_FHD else EXPORT_VIDEO_BITRATE_HEVC_HD
            else -> if (isFullHd) EXPORT_VIDEO_BITRATE_AVC_FHD else EXPORT_VIDEO_BITRATE_AVC_HD
        }
    }

    private fun resolveVideoExportProfile(sourceVideoSpec: SourceVideoSpec?): VideoExportProfile? {
        val hevcEncoder = findCompatibleVideoEncoder(MimeTypes.VIDEO_H265, sourceVideoSpec, requireHardware = true)
        if (hevcEncoder != null) {
            return VideoExportProfile(
                mimeType = MimeTypes.VIDEO_H265,
                requestedBitrate = resolveRequestedVideoBitrate(sourceVideoSpec, MimeTypes.VIDEO_H265),
                label = "hevc"
            )
        }

        val avcEncoder = findCompatibleVideoEncoder(MimeTypes.VIDEO_H264, sourceVideoSpec, requireHardware = false)
        if (avcEncoder != null) {
            android.util.Log.w(
                "TrackVideoExport",
                "No compatible hardware HEVC encoder for this format; falling back to AVC"
            )
            return VideoExportProfile(
                mimeType = MimeTypes.VIDEO_H264,
                requestedBitrate = resolveRequestedVideoBitrate(sourceVideoSpec, MimeTypes.VIDEO_H264),
                label = "avc"
            )
        }

        return null
    }

    private fun findCompatibleVideoEncoder(
        mimeType: String,
        sourceVideoSpec: SourceVideoSpec?,
        requireHardware: Boolean
    ): MediaCodecInfo? {
        return runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { codecInfo ->
                codecInfo.isEncoder &&
                    codecInfo.supportedTypes.any { type -> type.equals(mimeType, ignoreCase = true) } &&
                    (!requireHardware || isHardwareCodec(codecInfo)) &&
                    supportsRequestedVideoSize(codecInfo, mimeType, sourceVideoSpec)
            }
        }.getOrNull()
    }

    private fun supportsRequestedVideoSize(
        codecInfo: MediaCodecInfo,
        mimeType: String,
        sourceVideoSpec: SourceVideoSpec?
    ): Boolean {
        if (sourceVideoSpec == null) return true
        val videoCapabilities = runCatching {
            codecInfo.getCapabilitiesForType(mimeType).videoCapabilities
        }.getOrNull() ?: return false
        val width = sourceVideoSpec.displayWidth
        val height = sourceVideoSpec.displayHeight
        return videoCapabilities.isSizeSupported(width, height) ||
            videoCapabilities.isSizeSupported(height, width)
    }

    private fun isHardwareCodec(codecInfo: MediaCodecInfo): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> codecInfo.isHardwareAccelerated && !codecInfo.isSoftwareOnly
            else -> {
                val name = codecInfo.name.lowercase(Locale.US)
                !name.startsWith("omx.google.") &&
                    !name.startsWith("c2.android.") &&
                    !name.startsWith("c2.google.")
            }
        }
    }

    private fun buildEncoderFactory(requestedVideoBitrate: Int): DefaultEncoderFactory {
        val videoSettings = VideoEncoderSettings.Builder()
            .setBitrate(requestedVideoBitrate)
            .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            .setiFrameIntervalSeconds(EXPORT_I_FRAME_INTERVAL_SECONDS)
            .build()

        val audioSettings = AudioEncoderSettings.Builder()
            .setBitrate(EXPORT_AUDIO_BITRATE_AAC)
            .build()

        return DefaultEncoderFactory.Builder(appContext)
            .setRequestedVideoEncoderSettings(videoSettings)
            .setRequestedAudioEncoderSettings(audioSettings)
            .setEnableFallback(false)
            .build()
    }

    fun cancel() {
        val thread = exportThread ?: return
        Handler(thread.looper).post {
            try {
                transformer?.cancel()
            } catch (_: Throwable) {
            } finally {
                cleanupThread()
            }
        }
    }

    private fun finishSuccess(outputFile: File, onSuccess: (File) -> Unit) {
        if (markFinished()) return
        cleanupThread()
        mainHandler.post { onSuccess(outputFile) }
    }

    private fun finishError(error: Throwable, onError: (Throwable) -> Unit) {
        if (markFinished()) return
        cleanupThread()
        mainHandler.post { onError(error) }
    }

    @Synchronized
    private fun markFinished(): Boolean {
        if (isFinishing) return true
        isFinishing = true
        return false
    }

    private fun cleanupThread() {
        transformer = null
        exportThread?.quitSafely()
        exportThread = null
    }

    private class SessionHudOverlay(
        private val model: OverlayModel
    ) : CanvasOverlay(true) {

        private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(168, 7, 12, 18)
            style = Paint.Style.FILL
        }
        private val cardStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(184, 115, 204, 255)
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
            color = Color.argb(255, 107, 214, 255)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 38f
        }
        private val bestPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 115, 255, 170)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 38f
        }
        private val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 255, 191, 94)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 62f
        }
        private val leanGaugeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 94, 111, 129)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 10f
        }
        private val leanGaugeArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 44, 214, 110)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 12f
        }
        private val leanDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 44, 214, 110)
            style = Paint.Style.FILL
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
        private val footerMutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 138, 168, 196)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textSize = 20f
        }
        private val axisLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 65, 83, 104)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        private val progressTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 57, 71, 88)
            style = Paint.Style.FILL
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
            color = Color.argb(220, 131, 211, 255)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val mapDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 255, 120, 84)
            style = Paint.Style.FILL
        }
        private val mapDotGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(96, 255, 120, 84)
            style = Paint.Style.FILL
        }
        private val mapStartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 116, 255, 176)
            style = Paint.Style.FILL
        }

        private var canvasWidth = 0f
        private var canvasHeight = 0f
        private var margin = 0f
        private var cornerRadius = 0f
        private var timerCard = RectF()
        private var lapsCard = RectF()
        private var speedCard = RectF()
        private var leanCard = RectF()
        private var gCard = RectF()
        private var mapCard = RectF()
        private var mapInnerRect = RectF()
        private var mapPath = Path()
        private var mapProjector: MiniMapProjector? = null
        private var routeSampler = RouteSampler(model.routeSamples)
        private var gSampler = GSampler(model.gSamples)
        private var leanSampler = LeanSampler(model.leanSamples)
        private var gPeakTracker = GPeakTracker(model.gSamples)
        private var lastFrameTimeMs = Long.MIN_VALUE
        private var gaugeDotRadius = 10f

        override fun configure(videoSize: Size) {
            super.configure(videoSize)
            canvasWidth = videoSize.width.toFloat()
            canvasHeight = videoSize.height.toFloat()
            margin = min(canvasWidth, canvasHeight) * 0.026f
            cornerRadius = min(canvasWidth, canvasHeight) * 0.022f
            labelPaint.textSize = min(canvasWidth, canvasHeight) * 0.024f
            valuePaint.textSize = min(canvasWidth, canvasHeight) * 0.038f
            secondaryValuePaint.textSize = min(canvasWidth, canvasHeight) * 0.028f
            accentPaint.textSize = min(canvasWidth, canvasHeight) * 0.030f
            bestPaint.textSize = min(canvasWidth, canvasHeight) * 0.030f
            speedPaint.textSize = min(canvasWidth, canvasHeight) * 0.078f
            leanValuePaint.textSize = min(canvasWidth, canvasHeight) * 0.032f
            leanDirectionPaint.textSize = min(canvasWidth, canvasHeight) * 0.016f
            cardStrokePaint.strokeWidth = max(2f, min(canvasWidth, canvasHeight) * 0.0018f)
            leanGaugeTrackPaint.strokeWidth = max(7f, min(canvasWidth, canvasHeight) * 0.0075f)
            leanGaugeArcPaint.strokeWidth = leanGaugeTrackPaint.strokeWidth * 1.08f
            gaugeGridPaint.strokeWidth = max(1.6f, min(canvasWidth, canvasHeight) * 0.0018f)
            gaugeLabelPaint.textSize = min(canvasWidth, canvasHeight) * 0.015f
            gaugeValuePaint.textSize = min(canvasWidth, canvasHeight) * 0.020f
            gaugeValueOutlinePaint.textSize = gaugeValuePaint.textSize
            gaugeValueOutlinePaint.strokeWidth = max(3f, gaugeValuePaint.textSize * 0.16f)
            brakingPaint.textSize = min(canvasWidth, canvasHeight) * 0.023f
            accelPaint.textSize = min(canvasWidth, canvasHeight) * 0.023f
            footerMutedPaint.textSize = min(canvasWidth, canvasHeight) * 0.013f
            axisLinePaint.strokeWidth = max(1.5f, min(canvasWidth, canvasHeight) * 0.0018f)
            mapStrokePaint.strokeWidth = max(4f, min(canvasWidth, canvasHeight) * 0.006f)
            gaugeDotRadius = max(6f, min(canvasWidth, canvasHeight) * 0.0105f)
            rebuildLayout()
        }

        override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            val frameTimeMs = presentationTimeUs / 1000L
            if (frameTimeMs < lastFrameTimeMs) {
                routeSampler.reset()
                gSampler.reset()
                leanSampler.reset()
                gPeakTracker.reset()
            }
            lastFrameTimeMs = frameTimeMs

            val sessionElapsedMs = frameTimeMs + model.videoStartSessionElapsedMs
            val currentLap = resolveCurrentLap(sessionElapsedMs)
            val completedLaps = model.lapSegments.filter { segment ->
                segment.isCompleted && sessionElapsedMs >= segment.startMs + segment.durationMs
            }
            val lastLap = completedLaps.lastOrNull()
            val bestLap = completedLaps.minByOrNull { segment -> segment.durationMs }
            val routeState = routeSampler.sample(sessionElapsedMs)
            val gState = gSampler.sample(sessionElapsedMs)
            val leanState = leanSampler.sample(sessionElapsedMs)
            val gFrame = if (gState?.hasLiveMaxData() == true) {
                renderFrameFromLiveState(gState)
            } else {
                gPeakTracker.stateAt(sessionElapsedMs, gState)
            }

            drawCard(canvas, timerCard)

            drawTimerCard(canvas, currentLap, lastLap, bestLap)
            drawSpeedCard(canvas, routeState)
            drawMapCard(canvas, routeState)
            drawGCard(canvas, gFrame)
            if (model.isMotorcycle) {
                drawLeanGauge(canvas, leanState?.angleDeg ?: 0f)
            }
        }

        private fun rebuildLayout() {
            val landscape = canvasWidth >= canvasHeight
            val hudGap = margin * 0.55f
            val timerWidth = if (landscape) canvasWidth * 0.28f else canvasWidth * 0.39f
            val timerHeight = if (landscape) canvasHeight * 0.18f else canvasHeight * 0.14f
            val lapsWidth = if (landscape) canvasWidth * 0.28f else canvasWidth * 0.37f
            val lapsHeight = if (landscape) canvasHeight * 0.24f else canvasHeight * 0.18f
            val speedWidth = if (landscape) canvasWidth * 0.21f else canvasWidth * 0.28f
            val speedHeight = if (landscape) canvasHeight * 0.12f else canvasHeight * 0.11f
            val mapSize = min(
                canvasWidth * if (landscape) 0.28f else 0.36f,
                canvasHeight * if (landscape) 0.36f else 0.27f
            )
            val leanWidth = if (landscape) canvasWidth * 0.20f else canvasWidth * 0.28f
            val leanHeight = if (landscape) canvasHeight * 0.11f else canvasHeight * 0.095f
            val gWidth = when {
                landscape && model.isMotorcycle -> canvasWidth * 0.18f
                landscape -> canvasWidth * 0.22f
                model.isMotorcycle -> canvasWidth * 0.22f
                else -> canvasWidth * 0.28f
            }
            val gHeight = when {
                landscape && model.isMotorcycle -> canvasHeight * 0.20f
                landscape -> canvasHeight * 0.26f
                model.isMotorcycle -> canvasHeight * 0.18f
                else -> canvasHeight * 0.22f
            }

            timerCard = RectF(
                margin,
                margin,
                margin + timerWidth,
                margin + timerHeight
            )

            lapsCard = RectF(
                canvasWidth - margin - lapsWidth,
                margin,
                canvasWidth - margin,
                margin + lapsHeight
            )

            mapCard = RectF(
                canvasWidth - margin - mapSize,
                margin,
                canvasWidth - margin,
                margin + mapSize
            )

            val gBottom = canvasHeight - margin
            gCard = RectF(
                canvasWidth - margin - gWidth,
                gBottom - gHeight,
                canvasWidth - margin,
                gBottom
            )

            if (model.isMotorcycle) {
                leanCard = RectF(
                    canvasWidth - margin - leanWidth,
                    gCard.top - hudGap - leanHeight,
                    canvasWidth - margin,
                    gCard.top - hudGap
                )

                val centeredGLeft = leanCard.centerX() - gWidth / 2f
                gCard = RectF(
                    centeredGLeft,
                    gBottom - gHeight,
                    centeredGLeft + gWidth,
                    gBottom
                )
            } else {
                leanCard = RectF()
            }

            val speedLeft = if (landscape) {
                margin * 0.55f
            } else {
                margin * 0.75f
            }
            speedCard = RectF(
                speedLeft,
                canvasHeight - margin - speedHeight,
                speedLeft + speedWidth,
                canvasHeight - margin
            )

            val innerPadding = max(10f, min(canvasWidth, canvasHeight) * if (landscape) 0.022f else 0.020f)
            mapInnerRect = RectF(
                mapCard.left + innerPadding,
                mapCard.top + innerPadding,
                mapCard.right - innerPadding,
                mapCard.bottom - innerPadding
            )

            val mapPoints = if (model.miniMapPoints.size >= 2) {
                model.miniMapPoints
            } else {
                model.routeSamples.map { sample -> sample.geoPoint }
            }
            mapProjector = MiniMapProjector(mapPoints)
            mapPath = mapProjector?.createPath(mapInnerRect) ?: Path()
        }

        private fun drawCard(canvas: Canvas, rect: RectF, filled: Boolean = false) {
            if (filled) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cardPaint)
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, cardStrokePaint)
        }

        private fun drawTimerCard(
            canvas: Canvas,
            currentLap: LapSnapshot?,
            lastLap: LapSegment?,
            bestLap: LapSegment?
        ) {
            val left = timerCard.left + margin * 0.65f
            val right = timerCard.right - margin * 0.65f
            val currentLapText = currentLap?.currentLapTimeMs?.let(::formatTime) ?: "00:00.000"
            val lastLapText = lastLap?.let { segment -> formatTime(segment.durationMs) } ?: "--:--.---"
            val bestLapText = bestLap?.let { segment -> formatTime(segment.durationMs) } ?: "--:--.---"

            var top = timerCard.top + margin * 0.42f
            top = drawTopAlignedText(canvas, "CURRENT", left, top, labelPaint) + margin * 0.10f
            top = drawTopAlignedText(canvas, currentLapText, left, top, valuePaint) + margin * 0.22f

            canvas.drawLine(left, top, right, top, dividerPaint)
            top += margin * 0.28f

            val columnGap = margin * 0.9f
            val columnWidth = ((right - left) - columnGap) / 2f
            val lastColumnLeft = left
            val bestColumnLeft = left + columnWidth + columnGap
            val lastBottom = drawTopAlignedText(canvas, "LAST", lastColumnLeft, top, labelPaint)
            drawTopAlignedText(canvas, lastLapText, lastColumnLeft, lastBottom + margin * 0.08f, secondaryValuePaint)
            val bestBottom = drawTopAlignedText(canvas, "BEST", bestColumnLeft, top, labelPaint)
            drawTopAlignedText(canvas, bestLapText, bestColumnLeft, bestBottom + margin * 0.08f, bestPaint)
        }

        private fun drawSpeedCard(canvas: Canvas, routeState: RouteState?) {
            val right = speedCard.right - margin * 5.2f
            var top = speedCard.top + margin * 0.04f
            val speedValue = routeState?.speedKmh?.coerceAtLeast(0f) ?: 0f
            val speedText = String.format(Locale.getDefault(), "%.0f", speedValue)
            top = drawTopAlignedTextRight(canvas, speedText, right, top, speedPaint) + margin * 0.02f
            drawTopAlignedTextRight(canvas, "KM/H", right, top, labelPaint)
        }

        private fun drawGCard(canvas: Canvas, frame: GRenderFrame) {
            if (model.isMotorcycle) {
                drawMotorcycleGCard(canvas, frame)
            } else {
                drawCarGCard(canvas, frame)
            }
        }

        private fun renderFrameFromLiveState(state: GState): GRenderFrame {
            val currentResult = sqrt(
                state.longitudinalG * state.longitudinalG +
                    state.lateralG * state.lateralG
            )
            val peakResult = (state.maxResultG ?: currentResult).coerceAtLeast(currentResult)
            var visualMaxG = 1.5f
            while (visualMaxG < peakResult) {
                visualMaxG += 0.3f
            }

            return GRenderFrame(
                currentLongitudinalG = state.longitudinalG,
                currentLateralG = state.lateralG,
                currentResultG = currentResult,
                maxBraking = (state.maxBraking ?: max(0f, state.longitudinalG)).coerceAtLeast(0f),
                maxAccel = (state.maxAccel ?: max(0f, -state.longitudinalG)).coerceAtLeast(0f),
                maxLeft = (state.maxLeft ?: max(0f, state.lateralG)).coerceAtLeast(0f),
                maxRight = (state.maxRight ?: max(0f, -state.lateralG)).coerceAtLeast(0f),
                maxResultG = peakResult,
                visualMaxG = visualMaxG
            )
        }

        private fun drawMotorcycleGCard(canvas: Canvas, frame: GRenderFrame) {
            val brakingNow = max(0f, frame.currentLongitudinalG)
            val accelNow = max(0f, -frame.currentLongitudinalG)
            val liveMagnitude = abs(frame.currentLongitudinalG)
            val livePaint = if (brakingNow >= accelNow) brakingPaint else accelPaint
            var top = gCard.top + margin * 0.06f
            top = drawTopAlignedTextCentered(canvas, formatGCompact(liveMagnitude), gCard.centerX(), top, livePaint) + margin * 0.02f
            val brakeLabelTop = top + margin * 0.05f
            drawTopAlignedTextCentered(canvas, "BRAKE", gCard.centerX(), brakeLabelTop, footerMutedPaint)

            val accelLabelTop = gCard.bottom - margin * 0.14f - textHeight(footerMutedPaint)
            drawTopAlignedTextCentered(canvas, "ACCEL", gCard.centerX(), accelLabelTop, footerMutedPaint)

            val axisCenterX = gCard.centerX()
            val axisTop = brakeLabelTop + textHeight(footerMutedPaint) + margin * 0.08f
            val axisBottom = accelLabelTop - margin * 0.10f
            val axisHeight = (axisBottom - axisTop).coerceAtLeast(20f)
            val axisHalfTravel = axisHeight / 2f
            val axisMaxG = max(max(frame.maxBraking, frame.maxAccel), 0.9f).coerceAtMost(3.0f)
            val normalized = (frame.currentLongitudinalG / axisMaxG).coerceIn(-1f, 1f)
            val dotY = axisTop + axisHalfTravel - normalized * axisHalfTravel

            canvas.drawLine(axisCenterX, axisTop, axisCenterX, axisBottom, axisLinePaint)
            val tickWidth = max(8f, margin * 0.28f)
            val axisMidY = (axisTop + axisBottom) / 2f
            canvas.drawLine(axisCenterX - tickWidth, axisTop, axisCenterX + tickWidth, axisTop, axisLinePaint)
            canvas.drawLine(axisCenterX - tickWidth, axisMidY, axisCenterX + tickWidth, axisMidY, axisLinePaint)
            canvas.drawLine(axisCenterX - tickWidth, axisBottom, axisCenterX + tickWidth, axisBottom, axisLinePaint)
            canvas.drawCircle(axisCenterX, dotY, gaugeDotRadius * 1.65f, gaugeDotGlowPaint)
            canvas.drawCircle(axisCenterX, dotY, gaugeDotRadius, if (frame.currentLongitudinalG >= 0f) brakingBarPaint else accelBarPaint)
        }

        private fun drawCarGCard(canvas: Canvas, frame: GRenderFrame) {
            val cardContent = RectF(
                gCard.left + margin * 0.12f,
                gCard.top + margin * 0.08f,
                gCard.right - margin * 0.12f,
                gCard.bottom - margin * 0.12f
            )

            val visualMaxG = frame.visualMaxG
            val squareSize = min(cardContent.height(), cardContent.width()).coerceAtLeast(20f)
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
            val graphRadius = gaugeRect.width() / 2f - gaugeDotRadius * 1.55f - gaugeLabelPaint.textSize * 0.28f
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

        private fun drawGaugeGrid(
            canvas: Canvas,
            gaugeRect: RectF,
            visualMaxG: Float,
            showScaleLabels: Boolean = true
        ) {
            val centerX = gaugeRect.centerX()
            val centerY = gaugeRect.centerY()
            val graphRadius = gaugeRect.width() / 2f - gaugeDotRadius * 1.55f - gaugeLabelPaint.textSize * 0.28f
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

        private fun drawMapCard(canvas: Canvas, routeState: RouteState?) {
            if (!mapPath.isEmpty) {
                canvas.drawPath(mapPath, mapStrokePaint)
            }

            val projector = mapProjector ?: return
            if (projector.pointCount > 0) {
                val startPoint = projector.mapPoint(projector.pathPoints.first(), mapInnerRect)
                canvas.drawCircle(startPoint.x, startPoint.y, max(5f, mapStrokePaint.strokeWidth * 0.8f), mapStartPaint)
            }

            val routePoint = routeState?.geoPoint ?: return
            val dot = projector.projectToPolyline(routePoint, mapInnerRect)
            val dotRadius = max(8f, mapStrokePaint.strokeWidth * 1.05f)
            canvas.drawCircle(dot.x, dot.y, dotRadius * 1.9f, mapDotGlowPaint)
            canvas.drawCircle(dot.x, dot.y, dotRadius, mapDotPaint)
        }

        private fun drawLeanGauge(canvas: Canvas, leanAngleDeg: Float) {
            if (leanCard.isEmpty) return

            val gaugeRect = RectF(
                leanCard.left + margin * 0.10f,
                leanCard.top + margin * 0.04f,
                leanCard.right - margin * 0.10f,
                leanCard.bottom - margin * 0.08f
            )
            val leanAbs = abs(leanAngleDeg).coerceAtLeast(0f)
            val normalized = (leanAbs / 65f).coerceIn(0f, 1f)
            val currentColor = resolveLeanGaugeColor(normalized)
            val currentSweep = normalized * 90f

            leanGaugeArcPaint.color = currentColor
            leanValuePaint.color = currentColor
            leanDirectionPaint.color = Color.argb(220, 210, 220, 230)

            canvas.drawArc(gaugeRect, 180f, 180f, false, leanGaugeTrackPaint)

            val arcStart = if (leanAngleDeg >= 0f) 270f else 270f - currentSweep
            if (currentSweep > 0f) {
                canvas.drawArc(gaugeRect, arcStart, currentSweep, false, leanGaugeArcPaint)
            }

            val angleDeg = if (leanAngleDeg >= 0f) 270f + currentSweep else 270f - currentSweep
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val radiusX = gaugeRect.width() / 2f - leanGaugeArcPaint.strokeWidth * 0.5f
            val radiusY = gaugeRect.height() / 2f - leanGaugeArcPaint.strokeWidth * 0.5f
            val centerX = gaugeRect.centerX()
            val centerY = gaugeRect.centerY()
            val dotX = centerX + kotlin.math.cos(angleRad).toFloat() * radiusX
            val dotY = centerY + kotlin.math.sin(angleRad).toFloat() * radiusY
            leanDotPaint.color = currentColor
            canvas.drawCircle(dotX, dotY, gaugeDotRadius * 1.7f, gaugeDotGlowPaint)
            canvas.drawCircle(dotX, dotY, gaugeDotRadius, leanDotPaint)

            val valueTop = gaugeRect.centerY() - textHeight(leanValuePaint) * 0.08f
            drawTopAlignedTextCentered(
                canvas,
                String.format(Locale.getDefault(), "%.0f°", leanAbs),
                leanCard.centerX(),
                valueTop,
                leanValuePaint
            )
        }

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
            val startA = Color.alpha(startColor)
            val startR = Color.red(startColor)
            val startG = Color.green(startColor)
            val startB = Color.blue(startColor)
            val endA = Color.alpha(endColor)
            val endR = Color.red(endColor)
            val endG = Color.green(endColor)
            val endB = Color.blue(endColor)
            return Color.argb(
                lerp(startA.toFloat(), endA.toFloat(), clamped).toInt(),
                lerp(startR.toFloat(), endR.toFloat(), clamped).toInt(),
                lerp(startG.toFloat(), endG.toFloat(), clamped).toInt(),
                lerp(startB.toFloat(), endB.toFloat(), clamped).toInt()
            )
        }

        private fun drawTopAlignedText(
            canvas: Canvas,
            text: String,
            left: Float,
            top: Float,
            paint: Paint
        ): Float {
            val metrics = paint.fontMetrics
            val baseline = top - metrics.ascent
            canvas.drawText(text, left, baseline, paint)
            return baseline + metrics.descent
        }

        private fun drawTopAlignedTextRight(
            canvas: Canvas,
            text: String,
            right: Float,
            top: Float,
            paint: Paint
        ): Float {
            return drawTopAlignedText(canvas, text, right - paint.measureText(text), top, paint)
        }

        private fun drawTopAlignedTextCentered(
            canvas: Canvas,
            text: String,
            centerX: Float,
            top: Float,
            paint: Paint
        ): Float {
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

        private fun formatGaugeLabel(value: Float): String {
            return String.format(Locale.US, "%.1fg", value)
        }

        private fun formatGCompact(value: Float): String {
            return String.format(Locale.US, "%.1f", value.coerceAtLeast(0f))
        }

        private fun resolveCurrentLap(sessionElapsedMs: Long): LapSnapshot? {
            if (model.lapSegments.isEmpty()) return null
            val activeLap = model.lapSegments.lastOrNull { segment -> sessionElapsedMs >= segment.startMs } ?: return null
            val elapsedInLap = when {
                sessionElapsedMs < activeLap.startMs -> 0L
                activeLap.isCompleted -> min(activeLap.durationMs, sessionElapsedMs - activeLap.startMs)
                else -> max(0L, sessionElapsedMs - activeLap.startMs)
            }
            return LapSnapshot(activeLap.lapNumber, elapsedInLap)
        }

        private fun formatTime(timeMs: Long): String {
            val safeMs = timeMs.coerceAtLeast(0L)
            val totalSeconds = safeMs / 1000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            val millis = safeMs % 1000L
            return String.format(Locale.getDefault(), "%02d:%02d.%03d", minutes, seconds, millis)
        }

        private data class LapSnapshot(
            val lapNumber: Int,
            val currentLapTimeMs: Long
        )
    }

    private data class GRenderFrame(
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

    private class GPeakTracker(
        private val samples: List<GSample>
    ) {
        private var index = 0
        private var maxBraking = 0f
        private var maxAccel = 0f
        private var maxLeft = 0f
        private var maxRight = 0f
        private var maxResultG = 0f

        fun reset() {
            index = 0
            maxBraking = 0f
            maxAccel = 0f
            maxLeft = 0f
            maxRight = 0f
            maxResultG = 0f
        }

        fun stateAt(timeMs: Long, current: GState?): GRenderFrame {
            while (index < samples.size && samples[index].timeMs <= timeMs) {
                consumeSample(samples[index])
                index++
            }

            val currentLongitudinal = current?.longitudinalG ?: 0f
            val currentLateral = current?.lateralG ?: 0f
            val currentResult = sqrt(
                currentLongitudinal * currentLongitudinal +
                    currentLateral * currentLateral
            )

            maxBraking = max(maxBraking, max(0f, currentLongitudinal))
            maxAccel = max(maxAccel, max(0f, -currentLongitudinal))
            maxLeft = max(maxLeft, max(0f, currentLateral))
            maxRight = max(maxRight, max(0f, -currentLateral))
            maxResultG = max(maxResultG, currentResult)

            return GRenderFrame(
                currentLongitudinalG = currentLongitudinal,
                currentLateralG = currentLateral,
                currentResultG = currentResult,
                maxBraking = maxBraking,
                maxAccel = maxAccel,
                maxLeft = maxLeft,
                maxRight = maxRight,
                maxResultG = maxResultG,
                visualMaxG = resolveGaugeVisualMaxG(maxResultG)
            )
        }

        private fun consumeSample(sample: GSample) {
            maxBraking = max(maxBraking, sample.maxBraking?.coerceAtLeast(0f) ?: max(0f, sample.longitudinalG))
            maxAccel = max(maxAccel, sample.maxAccel?.coerceAtLeast(0f) ?: max(0f, -sample.longitudinalG))
            maxLeft = max(maxLeft, sample.maxLeft?.coerceAtLeast(0f) ?: max(0f, sample.lateralG))
            maxRight = max(maxRight, sample.maxRight?.coerceAtLeast(0f) ?: max(0f, -sample.lateralG))
            maxResultG = max(maxResultG, sample.maxResultG?.coerceAtLeast(0f) ?: sqrt(
                sample.longitudinalG * sample.longitudinalG +
                    sample.lateralG * sample.lateralG
            ))
        }

        private fun resolveGaugeVisualMaxG(maxResultG: Float): Float {
            var visualMaxG = 1.5f
            while (visualMaxG < maxResultG) {
                visualMaxG += 0.3f
            }
            return visualMaxG
        }
    }

    private class RouteSampler(
        private val samples: List<RouteSample>
    ) {
        private var index = 0

        fun reset() {
            index = 0
        }

        fun sample(timeMs: Long): RouteState? {
            if (samples.isEmpty()) return null
            if (timeMs < samples.first().timeMs) return null
            while (index < samples.lastIndex && samples[index + 1].timeMs <= timeMs) {
                index++
            }
            val current = samples[index]
            val next = samples.getOrNull(index + 1) ?: return RouteState(current.geoPoint, current.speedKmh)
            if (timeMs <= current.timeMs) {
                return RouteState(current.geoPoint, current.speedKmh)
            }

            val span = (next.timeMs - current.timeMs).coerceAtLeast(1L).toFloat()
            val progress = ((timeMs - current.timeMs).toFloat() / span).coerceIn(0f, 1f)
            return RouteState(
                geoPoint = GeoPoint(
                    latitude = lerp(current.geoPoint.latitude, next.geoPoint.latitude, progress),
                    longitude = lerp(current.geoPoint.longitude, next.geoPoint.longitude, progress)
                ),
                speedKmh = current.speedKmh
            )
        }
    }

    private class GSampler(
        private val samples: List<GSample>
    ) {
        private var index = 0

        fun reset() {
            index = 0
        }

        fun sample(timeMs: Long): GState? {
            if (samples.isEmpty()) return null
            if (timeMs < samples.first().timeMs) return null
            while (index < samples.lastIndex && samples[index + 1].timeMs <= timeMs) {
                index++
            }
            val current = samples[index]
            return GState(
                longitudinalG = current.longitudinalG,
                lateralG = current.lateralG,
                maxBraking = current.maxBraking,
                maxAccel = current.maxAccel,
                maxLeft = current.maxLeft,
                maxRight = current.maxRight,
                maxResultG = current.maxResultG
            )
        }
    }

    private class LeanSampler(
        private val samples: List<LeanSample>
    ) {
        private var index = 0

        fun reset() {
            index = 0
        }

        fun sample(timeMs: Long): LeanState? {
            if (samples.isEmpty()) return null
            if (timeMs < samples.first().timeMs) return null
            while (index < samples.lastIndex && samples[index + 1].timeMs <= timeMs) {
                index++
            }
            val current = samples[index]
            return LeanState(angleDeg = current.angleDeg)
        }
    }

    private data class RouteState(
        val geoPoint: GeoPoint,
        val speedKmh: Float
    )

    private data class GState(
        val longitudinalG: Float,
        val lateralG: Float,
        val maxBraking: Float? = null,
        val maxAccel: Float? = null,
        val maxLeft: Float? = null,
        val maxRight: Float? = null,
        val maxResultG: Float? = null
    ) {
        fun hasLiveMaxData(): Boolean {
            return maxBraking != null &&
                maxAccel != null &&
                maxLeft != null &&
                maxRight != null &&
                maxResultG != null
        }
    }

    private data class LeanState(
        val angleDeg: Float
    )

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
            }
        }

        fun createPath(target: RectF): Path {
            val path = Path()
            if (pathPoints.size < 2) return path
            pathPoints.forEachIndexed { index, point ->
                val mapped = mapPoint(point, target)
                if (index == 0) {
                    path.moveTo(mapped.x, mapped.y)
                } else {
                    path.lineTo(mapped.x, mapped.y)
                }
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

        fun projectToPolyline(point: GeoPoint, target: RectF): PointF {
            if (pathPoints.isEmpty()) {
                return PointF(target.centerX(), target.centerY())
            }
            if (pathPoints.size == 1) {
                return mapPoint(pathPoints.first(), target)
            }

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
        private const val EXPORT_VIDEO_BITRATE_HEVC_FHD = 6_500_000
        private const val EXPORT_VIDEO_BITRATE_HEVC_HD = 4_200_000
        private const val EXPORT_VIDEO_BITRATE_AVC_FHD = 9_000_000
        private const val EXPORT_VIDEO_BITRATE_AVC_HD = 5_500_000
        private const val EXPORT_AUDIO_BITRATE_AAC = 128_000
        private const val EXPORT_I_FRAME_INTERVAL_SECONDS = 2f

        private fun lerp(start: Double, end: Double, amount: Float): Double {
            return start + (end - start) * amount
        }

        private fun lerp(start: Float, end: Float, amount: Float): Float {
            return start + (end - start) * amount
        }
    }
}