package com.revix.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.revix.app.video.HudOffscreenPass
import com.revix.app.video.VideoLaunchCountdown
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@OptIn(UnstableApi::class)
class TrackSessionVideoOverlayExporter(context: Context) {

    data class ExportRequest(
        val inputUri: Uri,
        val outputFile: File,
        val trimStartMs: Long,
        val trimEndMs: Long = 0L,
        val overlayModel: OverlayModel
    )

    data class HudOnlyExportRequest(
        val inputUri: Uri,
        val outputFile: File,
        val alphaOutputFile: File,
        val trimStartMs: Long,
        val overlayModel: OverlayModel
    )

    private enum class OverlayBackgroundMode {
        TRANSPARENT,
        FILL,
        MATTE
    }

    data class OverlayModel(
        val isMotorcycle: Boolean,
        val videoStartSessionElapsedMs: Long,
        val lapSegments: List<LapSegment>,
        val routeSamples: List<RouteSample>,
        val gSamples: List<GSample>,
        val leanSamples: List<LeanSample>,
        val miniMapPoints: List<GeoPoint>,
        val speedUnitLabel: String = "KM/H",
        val speedFactor: Float = 1f,
        val trackName: String = "",
        val vehicleName: String = "",
        val vehicleSpecs: String = "",
        val interpolatePhoneSpeed: Boolean = false,
        val startMarker: GeoPoint? = null,
        val followMiniMap: Boolean = true
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
    private val brandLogoBitmap: Bitmap? by lazy {
        runCatching {
            BitmapFactory.decodeResource(appContext.resources, R.drawable.revix_logo)
        }.getOrNull()
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var exportThread: HandlerThread? = null
    private var transformer: Transformer? = null
    private var isFinishing = false
    private var hudPassIndex = 0
    private var dualHudPass = false
    private var progressPollRunnable: Runnable? = null

    fun export(
        request: ExportRequest,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit,
        onProgress: (Int) -> Unit = {}
    ) {
        cancel()
        isFinishing = false
        dualHudPass = false
        hudPassIndex = 0

        val thread = HandlerThread("TrackSessionVideoOverlayExport").apply { start() }
        exportThread = thread
        val sourceVideoSpec = resolveSourceVideoSpec(request.inputUri)

        Handler(thread.looper).post {
            startExport(
                request = request,
                sourceVideoSpec = sourceVideoSpec,
                onSuccess = onSuccess,
                onError = onError,
                onProgress = onProgress
            )
        }
    }

    fun exportHudOnly(
        request: HudOnlyExportRequest,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit,
        onProgress: (Int) -> Unit = {}
    ) {
        cancel()
        isFinishing = false
        hudPassIndex = 0
        dualHudPass = true

        val thread = HandlerThread("TrackSessionHudOnlyExport").apply { start() }
        exportThread = thread
        val sourceVideoSpec = resolveSourceVideoSpec(request.inputUri)

        Handler(thread.looper).post {
            startHudOnlyVideoExport(
                request = request,
                sourceVideoSpec = sourceVideoSpec,
                onSuccess = onSuccess,
                onError = onError,
                onProgress = onProgress
            )
        }
    }

    private fun startHudOnlyVideoExport(
        request: HudOnlyExportRequest,
        sourceVideoSpec: SourceVideoSpec?,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit,
        onProgress: (Int) -> Unit
    ) {
        try {
            val pass = if (hudPassIndex == 0) OverlayBackgroundMode.FILL else OverlayBackgroundMode.MATTE
            val overlay = SessionHudOverlay(
                model = request.overlayModel,
                brandLogo = brandLogoBitmap,
                backgroundMode = pass
            )
            val mediaItemBuilder = MediaItem.Builder().setUri(request.inputUri)
            applyClip(mediaItemBuilder, request.trimStartMs, 0L)

            val outputSpec = resolveExportOutputSpec(sourceVideoSpec)
            val videoExportProfile = resolveVideoExportProfile(outputSpec)
            if (videoExportProfile == null) {
                finishError(IllegalStateException("No compatible H.264 encoder available on this device"), onError)
                return
            }
            val outputFile = if (pass == OverlayBackgroundMode.MATTE) {
                request.alphaOutputFile
            } else {
                request.outputFile
            }
            if (outputFile.exists()) outputFile.delete()
            val editedMediaItem = EditedMediaItem.Builder(mediaItemBuilder.build())
                .setRemoveAudio(true)
                .setEffects(
                    Effects(
                        emptyList(),
                        buildVideoEffects(overlay, sourceVideoSpec, outputSpec)
                    )
                )
                .build()

            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (pass == OverlayBackgroundMode.FILL) {
                        hudPassIndex = 1
                        transformer = null
                        startHudOnlyVideoExport(request, sourceVideoSpec, onSuccess, onError, onProgress)
                        return
                    }
                    finishSuccess(request.outputFile, onSuccess)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    request.outputFile.delete()
                    request.alphaOutputFile.delete()
                    transformer = null
                    android.util.Log.e(
                        "TrackVideoExport",
                        "HUD-only video export failed (${exportException.getErrorCodeName()})",
                        exportException
                    )
                    finishError(exportException, onError)
                }
            }

            android.util.Log.i(
                "TrackVideoExport",
                "Starting HUD-only video export: container=mp4 codec=${videoExportProfile.label} audio=none resolution=${outputSpec.displayWidth}x${outputSpec.displayHeight}"
            )

            val transformerBuilder = Transformer.Builder(appContext)
                .setLooper(Looper.myLooper() ?: exportThread?.looper ?: Looper.getMainLooper())
                .setVideoMimeType(videoExportProfile.mimeType)
                .setPortraitEncodingEnabled(true)
                .addListener(listener)
                .setEncoderFactory(buildEncoderFactory(videoExportProfile.requestedBitrate))

            transformer = transformerBuilder.build()
            transformer?.start(editedMediaItem, outputFile.absolutePath)
            if (hudPassIndex == 0) startProgressPolling(onProgress)
        } catch (error: Throwable) {
            request.outputFile.delete()
            request.alphaOutputFile.delete()
            transformer = null
            android.util.Log.e("TrackVideoExport", "Unable to start HUD-only video export", error)
            finishError(error, onError)
        }
    }

    private fun startExport(
        request: ExportRequest,
        sourceVideoSpec: SourceVideoSpec?,
        onSuccess: (File) -> Unit,
        onError: (Throwable) -> Unit,
        onProgress: (Int) -> Unit
    ) {
        try {
            if (request.outputFile.exists()) {
                request.outputFile.delete()
            }

            val overlay = SessionHudOverlay(
                model = request.overlayModel,
                brandLogo = brandLogoBitmap,
                backgroundMode = OverlayBackgroundMode.TRANSPARENT
            )
            val mediaItemBuilder = MediaItem.Builder().setUri(request.inputUri)
            applyClip(mediaItemBuilder, request.trimStartMs, request.trimEndMs)

            val outputSpec = resolveExportOutputSpec(sourceVideoSpec)
            val videoExportProfile = resolveVideoExportProfile(outputSpec)
            if (videoExportProfile == null) {
                finishError(IllegalStateException("No compatible H.264 encoder available on this device"), onError)
                return
            }

            val editedMediaItem = EditedMediaItem.Builder(mediaItemBuilder.build())
                .setEffects(
                    Effects(
                        emptyList(),
                        buildVideoEffects(overlay, sourceVideoSpec, outputSpec)
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
                "Starting telemetry export: container=mp4 codec=${videoExportProfile.label} audio=aac resolution=${outputSpec.displayWidth}x${outputSpec.displayHeight} bitrate=${videoExportProfile.requestedBitrate} trim=${request.trimStartMs}-${request.trimEndMs}"
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
            startProgressPolling(onProgress)
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

    private fun applyClip(
        mediaItemBuilder: MediaItem.Builder,
        trimStartMs: Long,
        trimEndMs: Long
    ) {
        if (trimStartMs <= 0L && trimEndMs <= 0L) return
        val clipping = MediaItem.ClippingConfiguration.Builder()
        if (trimStartMs > 0L) {
            clipping.setStartPositionMs(trimStartMs)
        }
        if (trimEndMs > trimStartMs.coerceAtLeast(0L)) {
            clipping.setEndPositionMs(trimEndMs)
        }
        mediaItemBuilder.setClippingConfiguration(clipping.build())
    }

    private fun buildVideoEffects(
        overlay: SessionHudOverlay,
        sourceVideoSpec: SourceVideoSpec?,
        outputSpec: SourceVideoSpec
    ): List<Effect> {
        val overlayEffect = OverlayEffect(listOf(overlay))
        val needsScale = sourceVideoSpec == null ||
            sourceVideoSpec.displayWidth != outputSpec.displayWidth ||
            sourceVideoSpec.displayHeight != outputSpec.displayHeight
        if (!needsScale) {
            return listOf(overlayEffect)
        }
        return listOf(
            Presentation.createForWidthAndHeight(
                outputSpec.displayWidth,
                outputSpec.displayHeight,
                Presentation.LAYOUT_SCALE_TO_FIT
            ),
            overlayEffect
        )
    }

    private fun resolveExportOutputSpec(sourceVideoSpec: SourceVideoSpec?): SourceVideoSpec {
        val width = sourceVideoSpec?.displayWidth ?: 1920
        val height = sourceVideoSpec?.displayHeight ?: 1080
        if (width <= 0 || height <= 0) {
            return SourceVideoSpec(displayWidth = 1920, displayHeight = 1080)
        }
        val shortEdge = min(width, height)
        if (shortEdge <= EXPORT_MAX_SHORT_EDGE) {
            return SourceVideoSpec(
                displayWidth = evenPx(width),
                displayHeight = evenPx(height)
            )
        }
        val scale = EXPORT_MAX_SHORT_EDGE.toFloat() / shortEdge.toFloat()
        return SourceVideoSpec(
            displayWidth = evenPx((width * scale).toInt()),
            displayHeight = evenPx((height * scale).toInt())
        )
    }

    private fun evenPx(value: Int): Int = (value / 2 * 2).coerceAtLeast(2)

    private fun resolveRequestedVideoBitrate(outputSpec: SourceVideoSpec?): Int {
        val shortestEdge = outputSpec
            ?.let { min(it.displayWidth, it.displayHeight) }
            ?: EXPORT_MAX_SHORT_EDGE
        return if (shortestEdge >= EXPORT_MAX_SHORT_EDGE) {
            EXPORT_VIDEO_BITRATE_AVC_FHD
        } else {
            EXPORT_VIDEO_BITRATE_AVC_HD
        }
    }

    private fun resolveVideoExportProfile(outputSpec: SourceVideoSpec?): VideoExportProfile? {
        val hardwareAvc = findCompatibleVideoEncoder(MimeTypes.VIDEO_H264, outputSpec, requireHardware = true)
        if (hardwareAvc != null) {
            return VideoExportProfile(
                mimeType = MimeTypes.VIDEO_H264,
                requestedBitrate = resolveRequestedVideoBitrate(outputSpec),
                label = "avc-hw"
            )
        }

        val softwareAvc = findCompatibleVideoEncoder(MimeTypes.VIDEO_H264, outputSpec, requireHardware = false)
        if (softwareAvc != null) {
            android.util.Log.w(
                "TrackVideoExport",
                "No compatible hardware H.264 encoder for this format; falling back to software AVC"
            )
            return VideoExportProfile(
                mimeType = MimeTypes.VIDEO_H264,
                requestedBitrate = resolveRequestedVideoBitrate(outputSpec),
                label = "avc-sw"
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
            .setEnableFallback(true)
            .build()
    }

    private fun startProgressPolling(onProgress: (Int) -> Unit) {
        val thread = exportThread ?: return
        val holder = ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                if (isFinishing) return
                val current = transformer ?: return
                val state = runCatching { current.getProgress(holder) }.getOrNull() ?: return
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val raw = holder.progress.coerceIn(0, 99)
                    val percent = if (dualHudPass) (hudPassIndex * 50) + (raw / 2) else raw
                    mainHandler.post { onProgress(percent.coerceIn(0, 99)) }
                }
                Handler(thread.looper).postDelayed(this, 500L)
            }
        }
        progressPollRunnable = runnable
        Handler(thread.looper).postDelayed(runnable, 500L)
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
        progressPollRunnable = null
        transformer = null
        exportThread?.quitSafely()
        exportThread = null
    }

    private class SessionHudOverlay(
        private val model: OverlayModel,
        brandLogo: Bitmap?,
        private val backgroundMode: OverlayBackgroundMode = OverlayBackgroundMode.TRANSPARENT
    ) : CanvasOverlay(true) {

        private val renderer = TrackSessionHudRenderer(brandLogo)
        private val offscreen = HudOffscreenPass()
        private var routeSampler = RouteSampler(
            samples = model.routeSamples,
            interpolateSpeed = model.interpolatePhoneSpeed
        )
        private var gSampler = GSampler(model.gSamples)
        private var leanSampler = LeanSampler(model.leanSamples)
        private var gPeakTracker = GPeakTracker(model.gSamples)
        private var lastFrameTimeMs = Long.MIN_VALUE
        private var overlayWidth = 0f
        private var overlayHeight = 0f

        override fun configure(videoSize: Size) {
            super.configure(videoSize)
            overlayWidth = videoSize.width.toFloat()
            overlayHeight = videoSize.height.toFloat()
            val mapPoints = if (model.miniMapPoints.size >= 2) {
                model.miniMapPoints
            } else {
                model.routeSamples.map { sample -> sample.geoPoint }
            }
            renderer.configure(
                width = videoSize.width.toFloat(),
                height = videoSize.height.toFloat(),
                isMotorcycle = model.isMotorcycle,
                speedUnitLabel = model.speedUnitLabel,
                speedFactor = model.speedFactor,
                miniMapPoints = mapPoints,
                trackName = model.trackName,
                vehicleName = model.vehicleName,
                vehicleSpecs = model.vehicleSpecs,
                startMarker = model.startMarker,
                followMiniMap = model.followMiniMap
            )
        }

        override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
            val frameTimeMs = presentationTimeUs / 1000L
            if (frameTimeMs < lastFrameTimeMs) {
                routeSampler.reset()
                gSampler.reset()
                leanSampler.reset()
                gPeakTracker.reset()
            }
            lastFrameTimeMs = frameTimeMs

            val sessionElapsedMs = frameTimeMs + model.videoStartSessionElapsedMs
            val prerollMs = if (model.videoStartSessionElapsedMs < 0L) {
                -model.videoStartSessionElapsedMs
            } else {
                0L
            }
            val countdownDigit = VideoLaunchCountdown.digit(
                prerollAvailableMs = prerollMs,
                timeUntilStartMs = -sessionElapsedMs
            )
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
                TrackSessionHudRenderer.gFrameFromLive(
                    longitudinalG = gState.longitudinalG,
                    lateralG = gState.lateralG,
                    maxBraking = gState.maxBraking ?: 0f,
                    maxAccel = gState.maxAccel ?: 0f,
                    maxLeft = gState.maxLeft ?: 0f,
                    maxRight = gState.maxRight ?: 0f,
                    maxResultG = gState.maxResultG ?: 0f
                )
            } else {
                gPeakTracker.stateAt(sessionElapsedMs, gState)
            }

            val pass = when (backgroundMode) {
                OverlayBackgroundMode.TRANSPARENT -> HudOffscreenPass.Mode.OVERLAY
                OverlayBackgroundMode.FILL -> HudOffscreenPass.Mode.FILL
                OverlayBackgroundMode.MATTE -> HudOffscreenPass.Mode.MATTE
            }
            offscreen.draw(canvas, pass, overlayWidth, overlayHeight) { target ->
            renderer.draw(
                target,
                TrackSessionHudRenderer.Frame(
                    currentLapNumber = currentLap?.lapNumber?.coerceAtLeast(1),
                    currentLapTimeMs = currentLap?.currentLapTimeMs,
                    lastLapTimeMs = lastLap?.durationMs,
                    bestLapTimeMs = bestLap?.durationMs,
                    recentLaps = completedLaps.takeLast(3).asReversed().map { segment ->
                        TrackSessionHudRenderer.RecentLap(segment.lapNumber.coerceAtLeast(1), segment.durationMs)
                    },
                    speedKmh = routeState?.speedKmh ?: 0f,
                    geoPoint = routeState?.geoPoint,
                    g = gFrame,
                    leanDeg = leanState?.angleDeg ?: 0f,
                    countdownDigit = countdownDigit
                ),
                sessionElapsedMs
            )
            }
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

        private data class LapSnapshot(
            val lapNumber: Int,
            val currentLapTimeMs: Long
        )
    }

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

        fun stateAt(timeMs: Long, current: GState?): TrackSessionHudRenderer.GRenderFrame {
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

            return TrackSessionHudRenderer.GRenderFrame(
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
        private val samples: List<RouteSample>,
        interpolateSpeed: Boolean
    ) {
        private var index = 0
        private val interpolateSpeed = interpolateSpeed && looksLikePhoneGps(samples)

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
                speedKmh = if (interpolateSpeed) {
                    lerp(current.speedKmh, next.speedKmh, progress)
                } else {
                    current.speedKmh
                }
            )
        }

        private fun looksLikePhoneGps(samples: List<RouteSample>): Boolean {
            if (samples.size < 3) return false
            val limit = min(samples.size, 48)
            val gaps = ArrayList<Long>(limit)
            for (i in 1 until limit) {
                val gap = samples[i].timeMs - samples[i - 1].timeMs
                if (gap > 0L) gaps += gap
            }
            if (gaps.isEmpty()) return false
            gaps.sort()
            return gaps[gaps.size / 2] >= PHONE_GPS_MIN_SAMPLE_GAP_MS
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
            val next = samples.getOrNull(index + 1)
            val liveLong: Float
            val liveLat: Float
            if (next == null || next.timeMs <= current.timeMs || timeMs <= current.timeMs) {
                liveLong = current.longitudinalG
                liveLat = current.lateralG
            } else {
                val span = (next.timeMs - current.timeMs).toFloat()
                val progress = ((timeMs - current.timeMs).toFloat() / span).coerceIn(0f, 1f)
                liveLong = lerp(current.longitudinalG, next.longitudinalG, progress)
                liveLat = lerp(current.lateralG, next.lateralG, progress)
            }
            return GState(
                longitudinalG = liveLong,
                lateralG = liveLat,
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
            val next = samples.getOrNull(index + 1)
            val angle = if (next == null || next.timeMs <= current.timeMs || timeMs <= current.timeMs) {
                current.angleDeg
            } else {
                val span = (next.timeMs - current.timeMs).toFloat()
                val progress = ((timeMs - current.timeMs).toFloat() / span).coerceIn(0f, 1f)
                lerp(current.angleDeg, next.angleDeg, progress)
            }
            return LeanState(angleDeg = angle)
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

    companion object {
        private const val EXPORT_MAX_SHORT_EDGE = 1080
        private const val EXPORT_VIDEO_BITRATE_AVC_FHD = 10_000_000
        private const val EXPORT_VIDEO_BITRATE_AVC_HD = 6_000_000
        private const val EXPORT_AUDIO_BITRATE_AAC = 128_000
        private const val EXPORT_I_FRAME_INTERVAL_SECONDS = 2f
        private const val PHONE_GPS_MIN_SAMPLE_GAP_MS = 350L

        private fun lerp(start: Double, end: Double, amount: Float): Double {
            return start + (end - start) * amount
        }

        private fun lerp(start: Float, end: Float, amount: Float): Float {
            return start + (end - start) * amount
        }
    }
}