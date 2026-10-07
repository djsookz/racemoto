package com.revix.app.drag

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.revix.app.DragStorage
import com.revix.app.R
import com.revix.app.main.ui.ScreenKeepOnController
import com.revix.app.settings.LanguageManager
import com.revix.app.video.ConcurrentPhoneCameras
import com.revix.app.video.DualCameraHolder
import com.revix.app.video.DualCameraPipLayout
import com.revix.app.video.PhoneDualCameraBinder
import com.revix.app.video.VideoClipTrimmer
import com.revix.app.video.VideoLaunchCountdown
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DragRunVideoActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var hudView: DragLiveHudView
    private lateinit var pipSwapButton: ImageButton
    private lateinit var topBar: View
    private lateinit var recRow: View
    private var clipAttemptId = 0L
    private var clipT0Ms = 0L
    private var clipLastMetricMs = 0L
    private var clipSaved = false

    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var previewContentAspect = 16f / 9f
    private var activeRecording: Recording? = null
    private var cameraExecutor: ExecutorService? = null
    private var recordingFile: File? = null
    private var recordingAttemptId: Long = 0L
    private var recordingSessionId: Long = 0L
    private var keepCurrentRecording = false
    private var orientationLocked = false
    private var recordingStartedElapsedMs = 0L
    private var videoT0OffsetMs = 0L
    private var recordingFinalizing = false
    private var pendingRebindAfterFinalize = false
    private val attemptT0Ms = linkedMapOf<Long, Long>()
    private val keepAttemptIds = linkedSetOf<Long>()
    private var postRollScheduled = false
    private var pendingLiveFile: File? = null
    private var ignoreSavedAttemptId = 0L
    private val postRollHandler = Handler(Looper.getMainLooper())
    private val postRollRunnable = Runnable {
        postRollScheduled = false
        if (activeRecording != null) {
            stopRecording(keep = shouldKeepRecording() || clipSaved, force = true)
        }
    }

    private val hudListener: (DragRunVideoHudState) -> Unit = { state ->
        runOnUiThread {
            bindHud(state)
            syncRecording(state)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val cameraOk = granted[Manifest.permission.CAMERA] == true || hasPermission(Manifest.permission.CAMERA)
        if (cameraOk) {
            bindCamera()
        } else {
            Toast.makeText(this, getString(R.string.drag_camera_permission_denied), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_drag_run_video)
        ScreenKeepOnController.setKeepOnOverride(this, true)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        cameraExecutor = Executors.newSingleThreadExecutor()

        previewView = findViewById(R.id.dragVideoPreview)
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        hudView = findViewById(R.id.flDragVideoHud)
        pipSwapButton = findViewById(R.id.btnDragPipSwap)
        pipSwapButton.setOnClickListener { swapDualCameras() }
        topBar = findViewById(R.id.llDragVideoTopBar)
        recRow = findViewById(R.id.llDragVideoRec)
        previewView.previewStreamState.observe(this) { state ->
            if (state == PreviewView.StreamState.STREAMING) {
                refreshHudPreviewAspect()
            }
        }
        previewView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            layoutHudToPreview()
        }

        findViewById<ImageButton>(R.id.btnDragVideoBack).setOnClickListener { requestLeaveCamera() }
        findViewById<ImageButton>(R.id.btnDragVideoSettings).setOnClickListener { showSettingsSheet() }
        bindCameraWindowInsets()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                requestLeaveCamera()
            }
        })

        bindHud(DragRunVideoBridge.latest)
        DragRunVideoBridge.addHudListener(hudListener)
        ensurePermissionsAndBind()
    }

    override fun onDestroy() {
        DragRunVideoBridge.removeHudListener(hudListener)
        cancelPostRoll()
        stopRecording(keep = shouldKeepRecording(), force = true)
        DualCameraHolder.release()
        cameraProvider?.unbindAll()
        cameraExecutor?.shutdown()
        unlockOrientation()
        ScreenKeepOnController.setKeepOnOverride(this, false)
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPhonePreviewAspectForCurrentOrientation()
        applyCameraRotation()
        if (!::previewView.isInitialized) return
        val host = previewHost()
        host.requestLayout()
        host.post {
            applyCameraRotation()
            previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
            if (activeRecording == null && !recordingFinalizing) {
                cameraProvider?.let { bindUseCases(it) }
            }
            refreshHudPreviewAspect()
            layoutHudToPreview()
        }
    }

    private fun shouldKeepRecording(): Boolean {
        if (keepCurrentRecording || keepAttemptIds.isNotEmpty()) return true
        val state = DragRunVideoBridge.latest
        return state.officiallyStarted && state.attemptId > 0L
    }

    private fun isLiveUnsavedRun(state: DragRunVideoHudState = DragRunVideoBridge.latest): Boolean {
        return state.sessionActive &&
            state.officiallyStarted &&
            !state.attemptSaved &&
            isCurrentClipAttempt(state.attemptId)
    }

    private fun isCurrentClipAttempt(attemptId: Long): Boolean {
        return attemptId > 0L && attemptId != ignoreSavedAttemptId
    }

    private fun canChangeCamera(): Boolean {
        val state = DragRunVideoBridge.latest
        return !(state.officiallyStarted && state.chronoNs > 0L)
    }

    private fun ensurePermissionsAndBind() {
        val needed = mutableListOf<String>()
        if (!hasPermission(Manifest.permission.CAMERA)) {
            needed += Manifest.permission.CAMERA
        }
        if (DragRunVideoSettings.micEnabled(this) && !hasPermission(Manifest.permission.RECORD_AUDIO)) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (needed.isEmpty()) {
            bindCamera()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun bindCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                bindUseCases(provider)
            } catch (error: Exception) {
                Log.e(TAG, "Unable to start drag camera", error)
                Toast.makeText(this, getString(R.string.drag_camera_unavailable), Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases(provider: ProcessCameraProvider) {
        if (activeRecording != null || recordingFinalizing) return
        val rotation = currentDisplayRotation()
        val requestedLens = DragRunVideoSettings.lens(this)
        val caps = DragRunVideoCapabilities.query(provider, requestedLens)
        val lens = requestedLens.takeIf { it in caps.lenses } ?: caps.lenses.first()
        val (qualityOption, fps) = caps.sanitize(
            DragRunVideoSettings.quality(this),
            DragRunVideoSettings.fps(this)
        )
        if (
            qualityOption != DragRunVideoSettings.quality(this) ||
            fps != DragRunVideoSettings.fps(this) ||
            lens != requestedLens
        ) {
            DragRunVideoSettings.save(this, quality = qualityOption, fps = fps, lens = lens)
        }
        val quality = qualityOption.cameraQuality
        val dual = DragRunVideoSettings.dualEnabled(this) &&
            ConcurrentPhoneCameras.advertised(this, provider)
        if (DragRunVideoSettings.dualEnabled(this) &&
            ConcurrentPhoneCameras.isProbed(this) &&
            !ConcurrentPhoneCameras.isSupported(this, provider)
        ) {
            DragRunVideoSettings.save(this, dualEnabled = false)
        }
        try {
            DualCameraPipLayout.hud = DualCameraPipLayout.Hud.DRAG
            val bound = PhoneDualCameraBinder.bind(
                context = this,
                owner = this,
                provider = provider,
                previewView = previewView,
                rotation = rotation,
                lens = lens,
                dual = dual,
                quality = quality,
                fps = fps
            )
            previewUseCase = bound.first
            videoCapture = bound.second
            if (dual && !ConcurrentPhoneCameras.isSupported(this, provider)) {
                DragRunVideoSettings.save(this, dualEnabled = false)
            }
            previewView.post { layoutHudToPreview() }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to bind drag camera", error)
            DualCameraHolder.release()
            previewUseCase = null
            videoCapture = null
            Toast.makeText(this, getString(R.string.drag_camera_unavailable), Toast.LENGTH_LONG).show()
        }
        previewView.post { refreshHudPreviewAspect() }
        syncRecording(DragRunVideoBridge.latest)
    }

    private fun currentDisplayRotation(): Int {
        return previewView.display?.rotation
            ?: window.decorView.display?.rotation
            ?: @Suppress("DEPRECATION") windowManager.defaultDisplay.rotation
    }

    private fun applyCameraRotation() {
        val rotation = currentDisplayRotation()
        previewUseCase?.targetRotation = rotation
        videoCapture?.targetRotation = rotation
    }

    private fun applyPhonePreviewAspectForCurrentOrientation() {
        val info = previewUseCase?.resolutionInfo
        val sensorW = (info?.resolution?.width ?: 1920).coerceAtLeast(1)
        val sensorH = (info?.resolution?.height ?: 1080).coerceAtLeast(1)
        val sensorAspect = sensorW / sensorH.toFloat()
        val wide = maxOf(sensorAspect, 1f / sensorAspect)
        previewContentAspect = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            wide
        } else {
            1f / wide
        }
    }

    private fun bindCameraWindowInsets() {
        val root = findViewById<View>(R.id.dragVideoRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val pad = (10 * resources.displayMetrics.density).toInt()
            if (::topBar.isInitialized) {
                topBar.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, 0)
            }
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun refreshHudPreviewAspect() {
        val info = previewUseCase?.resolutionInfo
        if (info == null) {
            applyPhonePreviewAspectForCurrentOrientation()
            layoutHudToPreview()
            return
        }
        val size = info.resolution
        val aspect = displayedAspect(size.width, size.height, info.rotationDegrees)
        if (aspect <= 0.05f) return
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape != (aspect > 1f)) {
            applyPhonePreviewAspectForCurrentOrientation()
            layoutHudToPreview()
            return
        }
        previewContentAspect = aspect
        layoutHudToPreview()
    }

    private fun layoutHudToPreview() {
        if (!::hudView.isInitialized) return
        val host = previewHost()
        val parentW = host.width
        val parentH = host.height
        if (parentW <= 1 || parentH <= 1 || previewContentAspect <= 0.05f) return
        val (width, height) = fitCenterSize(parentW, parentH, previewContentAspect)
        val params = hudView.layoutParams as FrameLayout.LayoutParams
        params.width = width
        params.height = height
        params.gravity = Gravity.CENTER
        hudView.layoutParams = params
        hudView.invalidate()
        layoutPipSwapButton(parentW, parentH, width, height)
    }

    private fun layoutPipSwapButton(parentW: Int, parentH: Int, videoW: Int, videoH: Int) {
        if (!::pipSwapButton.isInitialized) return
        val dual = DualCameraHolder.isActive()
        pipSwapButton.visibility = if (dual) View.VISIBLE else View.GONE
        if (!dual) return
        val videoLeft = (parentW - videoW) / 2f
        val videoTop = (parentH - videoH) / 2f
        val pip = DualCameraPipLayout.pipInDisplay(videoW.toFloat(), videoH.toFloat())
        DualCameraPipLayout.layoutSwapButton(pipSwapButton, videoLeft, videoTop, pip)
    }

    private fun swapDualCameras() {
        if (!DualCameraHolder.isActive()) return
        if (!canChangeCamera()) {
            Toast.makeText(this, getString(R.string.drag_camera_settings_while_recording), Toast.LENGTH_SHORT).show()
            return
        }
        val next = ConcurrentPhoneCameras.otherLens(DragRunVideoSettings.lens(this))
        DragRunVideoSettings.save(this, lens = next, dualEnabled = true)
        if (recordingFinalizing) {
            pendingRebindAfterFinalize = true
            return
        }
        if (activeRecording != null) {
            pendingRebindAfterFinalize = true
            stopRecording(keep = shouldKeepRecording() || clipSaved, force = true)
            return
        }
        cameraProvider?.let { bindUseCases(it) } ?: bindCamera()
    }

    private fun previewHost(): View {
        return previewView
    }

    private fun fitCenterSize(parentW: Int, parentH: Int, aspect: Float): Pair<Int, Int> {
        val parentAspect = parentW.toFloat() / parentH.toFloat()
        return if (parentAspect > aspect) {
            val height = parentH
            val width = (height * aspect).toInt().coerceAtLeast(1)
            width to height
        } else {
            val width = parentW
            val height = (width / aspect).toInt().coerceAtLeast(1)
            width to height
        }
    }

    private fun displayedAspect(contentWidth: Int, contentHeight: Int, rotationDegrees: Int): Float {
        val rotated = rotationDegrees % 180 != 0
        val width = if (rotated) contentHeight else contentWidth
        val height = if (rotated) contentWidth else contentHeight
        if (height <= 0) return 0f
        return width / height.toFloat()
    }

    private fun syncRecording(state: DragRunVideoHudState) {
        if (!state.sessionActive) {
            cancelPostRoll()
            if (activeRecording != null) {
                if (state.attemptSaved && state.attemptId > 0L && state.attemptId in attemptT0Ms) {
                    rememberSavedClip(state)
                }
                stopRecording(keep = shouldKeepRecording() || state.attemptSaved, force = true)
            }
            if (!isFinishing) finish()
            return
        }
        startRecordingIfNeeded(state)
        if (isCurrentClipAttempt(state.attemptId) &&
            state.officiallyStarted &&
            !state.attemptSaved &&
            recordingStartedElapsedMs > 0L
        ) {
            recordingAttemptId = state.attemptId
            recordingSessionId = state.sessionId.takeIf { it > 0L } ?: recordingSessionId
            markMeasurementStart(state.attemptId)
        }
        if (isCurrentClipAttempt(state.attemptId) && state.attemptSaved && state.attemptId > 0L) {
            if (state.attemptId !in attemptT0Ms && recordingStartedElapsedMs > 0L) {
                markMeasurementStart(state.attemptId)
            }
            if (state.attemptId in attemptT0Ms) {
                rememberSavedClip(state)
                scheduleClipEnd()
            }
        }
        updateRecVisibility()
    }

    private fun rememberSavedClip(state: DragRunVideoHudState) {
        clipAttemptId = state.attemptId
        clipT0Ms = attemptT0Ms[state.attemptId] ?: videoT0OffsetMs
        val lastNs = DragAttemptMetrics.lastSuccessfulMetricElapsedNs(
            mode = state.measurementMode,
            time0to100Ns = state.time0to100Ns,
            time0to200Ns = state.time0to200Ns,
            time100to200Ns = state.time100to200Ns,
            time0to402Ns = state.time0to402Ns
        )
        clipLastMetricMs = if (lastNs > 0L) lastNs / 1_000_000L else 0L
        clipSaved = true
        keepAttemptIds += state.attemptId
        keepCurrentRecording = true
    }

    private fun scheduleClipEnd() {
        if (postRollScheduled || activeRecording == null || recordingFinalizing) return
        if (!clipSaved || clipAttemptId <= 0L) return
        val endAtElapsed = clipT0Ms + clipLastMetricMs + VideoLaunchCountdown.POST_ROLL_MS
        val alreadyElapsed = if (recordingStartedElapsedMs > 0L) {
            SystemClock.elapsedRealtime() - recordingStartedElapsedMs
        } else {
            0L
        }
        val delayMs = (endAtElapsed - alreadyElapsed).coerceAtLeast(0L)
        postRollScheduled = true
        postRollHandler.removeCallbacks(postRollRunnable)
        postRollHandler.postDelayed(postRollRunnable, delayMs)
    }

    private fun cancelPostRoll() {
        postRollScheduled = false
        postRollHandler.removeCallbacks(postRollRunnable)
    }

    private fun updateRecVisibility() {
        if (!::recRow.isInitialized) return
        val recording = activeRecording != null &&
            recordingStartedElapsedMs > 0L &&
            !recordingFinalizing
        recRow.visibility = if (recording) View.VISIBLE else View.INVISIBLE
    }

    private fun requestLeaveCamera() {
        if (isLiveUnsavedRun() && activeRecording != null) {
            Toast.makeText(this, getString(R.string.drag_camera_cannot_leave_live), Toast.LENGTH_SHORT).show()
            return
        }
        finish()
    }

    private fun isClipRecording(): Boolean {
        return activeRecording != null &&
            recordingStartedElapsedMs > 0L &&
            !recordingFinalizing
    }

    private fun markMeasurementStart(attemptId: Long = recordingAttemptId) {
        if (recordingStartedElapsedMs <= 0L) return
        videoT0OffsetMs = (SystemClock.elapsedRealtime() - recordingStartedElapsedMs).coerceAtLeast(0L)
        if (attemptId > 0L && attemptId !in attemptT0Ms) {
            attemptT0Ms[attemptId] = videoT0OffsetMs
            clipAttemptId = attemptId
            clipT0Ms = videoT0OffsetMs
        }
    }

    private fun startRecordingIfNeeded(state: DragRunVideoHudState) {
        if (activeRecording != null || recordingFinalizing) return
        if (!state.sessionActive) return
        val capture = videoCapture ?: return
        val sessionId = state.sessionId.takeIf { it > 0L } ?: state.attemptId
        if (sessionId <= 0L) return
        val file = File(DragStorage.videoDir(this, sessionId), "session_${sessionId}_${System.currentTimeMillis()}.mp4")
        runCatching { file.parentFile?.mkdirs() }
        recordingFile = file
        recordingAttemptId = state.attemptId
        recordingSessionId = sessionId
        keepCurrentRecording = false
        recordingStartedElapsedMs = 0L
        videoT0OffsetMs = 0L
        clipAttemptId = 0L
        clipT0Ms = 0L
        clipLastMetricMs = 0L
        clipSaved = false
        ignoreSavedAttemptId = if (state.attemptSaved) state.attemptId else 0L
        DragRunVideoBridge.markRecordingBusy()
        try {
            lockOrientation()
            capture.targetRotation = currentDisplayRotation()
            var pending = capture.output.prepareRecording(this, FileOutputOptions.Builder(file).build())
            if (DragRunVideoSettings.micEnabled(this) && hasPermission(Manifest.permission.RECORD_AUDIO)) {
                pending = pending.withAudioEnabled()
            }
            activeRecording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        recordingStartedElapsedMs = SystemClock.elapsedRealtime()
                        updateRecVisibility()
                        val latest = DragRunVideoBridge.latest
                        if (latest.officiallyStarted &&
                            !latest.attemptSaved &&
                            isCurrentClipAttempt(latest.attemptId)
                        ) {
                            recordingAttemptId = latest.attemptId
                            markMeasurementStart(latest.attemptId)
                        }
                    }
                    is VideoRecordEvent.Finalize -> handleFinalize(event)
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start drag video", error)
            unlockOrientation()
            runCatching { file.delete() }
            activeRecording = null
            recordingFile = null
            recordingAttemptId = 0L
            DragRunVideoBridge.markRecordingIdle()
            Toast.makeText(this, getString(R.string.drag_camera_record_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording(keep: Boolean, force: Boolean = false) {
        if (!force && isLiveUnsavedRun() && activeRecording != null) {
            cancelPostRoll()
            keepCurrentRecording = true
            Log.w(TAG, "Keeping drag video running while measurement is live")
            return
        }
        cancelPostRoll()
        keepCurrentRecording = keep
        val recording = activeRecording ?: return
        recordingFinalizing = true
        updateRecVisibility()
        try {
            recording.stop()
        } catch (error: Exception) {
            Log.w(TAG, "Stop drag video failed", error)
            recordingFinalizing = false
            DragRunVideoBridge.markRecordingIdle()
        }
        activeRecording = null
        updateRecVisibility()
    }

    private fun handleFinalize(event: VideoRecordEvent.Finalize) {
        cancelPostRoll()
        unlockOrientation()
        val file = recordingFile
        val fallbackAttemptId = recordingAttemptId.takeIf { it > 0L } ?: clipAttemptId
        val sessionId = recordingSessionId
        val fallbackT0 = if (clipT0Ms > 0L) clipT0Ms else videoT0OffsetMs
        val lastMetricMs = clipLastMetricMs
        val savedClip = clipSaved
        val latest = DragRunVideoBridge.latest
        val attachAttemptId = when {
            savedClip && clipAttemptId > 0L -> clipAttemptId
            keepAttemptIds.firstOrNull { isCurrentClipAttempt(it) } != null ->
                keepAttemptIds.first { isCurrentClipAttempt(it) }
            latest.officiallyStarted && isCurrentClipAttempt(latest.attemptId) -> latest.attemptId
            keepCurrentRecording && isCurrentClipAttempt(fallbackAttemptId) -> fallbackAttemptId
            else -> 0L
        }
        val attachT0 = when {
            attachAttemptId == clipAttemptId && clipT0Ms > 0L -> clipT0Ms
            attachAttemptId > 0L -> attemptT0Ms[attachAttemptId] ?: fallbackT0
            else -> fallbackT0
        }
        val leaving = isDestroyed || isFinishing || !latest.sessionActive
        val unexpectedLiveDrop = !leaving &&
            latest.officiallyStarted &&
            !latest.attemptSaved &&
            latest.sessionActive &&
            attachAttemptId > 0L &&
            latest.attemptId == attachAttemptId
        val attachFile = file?.takeIf { it.exists() && it.length() > 0L } ?: pendingLiveFile
        val keep = !unexpectedLiveDrop &&
            attachAttemptId > 0L &&
            !event.hasError() &&
            attachFile != null
        activeRecording = null
        recordingFinalizing = false
        recordingFile = null
        recordingAttemptId = 0L
        recordingSessionId = 0L
        recordingStartedElapsedMs = 0L
        videoT0OffsetMs = 0L
        clipAttemptId = 0L
        clipT0Ms = 0L
        clipLastMetricMs = 0L
        clipSaved = false
        keepCurrentRecording = unexpectedLiveDrop
        attemptT0Ms.clear()
        keepAttemptIds.clear()
        if (unexpectedLiveDrop) {
            if (file != null && file.exists() && file.length() > 0L) {
                pendingLiveFile = file
            } else {
                file?.let { runCatching { if (it.exists()) it.delete() } }
            }
            Log.w(TAG, "Drag video dropped during live measurement, resuming")
        } else if (!keep) {
            file?.let { runCatching { if (it.exists()) it.delete() } }
            pendingLiveFile?.takeIf { it != file }?.let { runCatching { if (it.exists()) it.delete() } }
            pendingLiveFile = null
        } else {
            persistRecordingAttachment(
                file = attachFile!!,
                sessionId = sessionId,
                attemptId = attachAttemptId,
                t0Ms = attachT0,
                lastMetricMs = lastMetricMs,
                trimTail = savedClip || lastMetricMs > 0L
            )
            pendingLiveFile?.takeIf { it != attachFile }?.let { runCatching { if (it.exists()) it.delete() } }
            pendingLiveFile = null
        }
        if (unexpectedLiveDrop) {
            DragRunVideoBridge.markRecordingBusy()
        } else {
            DragRunVideoBridge.markRecordingIdle()
        }
        updateRecVisibility()
        if (isDestroyed || isFinishing) return
        if (pendingRebindAfterFinalize && !unexpectedLiveDrop) {
            pendingRebindAfterFinalize = false
            cameraProvider?.let { bindUseCases(it) } ?: bindCamera()
            return
        }
        if (latest.sessionActive) {
            startRecordingIfNeeded(latest)
        }
    }

    private fun persistRecordingAttachment(
        file: File,
        sessionId: Long,
        attemptId: Long,
        t0Ms: Long,
        lastMetricMs: Long,
        trimTail: Boolean
    ) {
        val app = applicationContext
        Thread({
            val startMs = (t0Ms - VideoLaunchCountdown.PRE_ROLL_MS).coerceAtLeast(0L)
            val endMs = if (trimTail) {
                t0Ms + lastMetricMs.coerceAtLeast(0L) + VideoLaunchCountdown.POST_ROLL_MS
            } else {
                0L
            }
            val output = File(file.parentFile, "attempt_${attemptId}_${System.currentTimeMillis()}.mp4")
            val trimmed = VideoClipTrimmer.trim(file, output, startMs, endMs)
            val resultFile: File
            val resultT0: Long
            if (trimmed != null && trimmed.file.exists() && trimmed.file.length() > 0L) {
                resultFile = trimmed.file
                resultT0 = (t0Ms - trimmed.actualStartMs).coerceAtLeast(0L)
                if (trimmed.file != file) {
                    runCatching { if (file.exists()) file.delete() }
                }
            } else {
                resultFile = file
                resultT0 = t0Ms.coerceAtLeast(0L)
                runCatching { if (output.exists() && output != file) output.delete() }
            }
            DragRunVideoBridge.attachVideo(sessionId, attemptId, resultFile.absolutePath, resultT0)
            DragStorage.attachAttemptVideo(app, sessionId, attemptId, resultFile.absolutePath, resultT0)
        }, "drag-video-clip").start()
    }

    private fun bindHud(state: DragRunVideoHudState) {
        if (::hudView.isInitialized) hudView.update(state)
        updateRecVisibility()
    }

    private fun showSettingsSheet() {
        if (!canChangeCamera() || recordingFinalizing) {
            Toast.makeText(this, getString(R.string.drag_camera_settings_while_recording), Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_drag_video_settings, null)
        dialog.setContentView(view)
        CameraSettingsSheet.prepare(dialog)

        val chipHd = view.findViewById<TextView>(R.id.chipQualityHd)
        val chipFhd = view.findViewById<TextView>(R.id.chipQualityFhd)
        val chipUhd = view.findViewById<TextView>(R.id.chipQualityUhd)
        val chip30 = view.findViewById<TextView>(R.id.chipFps30)
        val chip60 = view.findViewById<TextView>(R.id.chipFps60)
        val chipRear = view.findViewById<TextView>(R.id.chipLensRear)
        val chipFront = view.findViewById<TextView>(R.id.chipLensFront)
        val chipBoth = view.findViewById<TextView>(R.id.chipLensBoth)
        val chipMicOn = view.findViewById<TextView>(R.id.chipMicOn)
        val chipMicOff = view.findViewById<TextView>(R.id.chipMicOff)
        view.findViewById<View>(R.id.llPhoneCameraSettings).visibility = View.VISIBLE

        fun currentCaps(): DragRunVideoCaps? {
            val provider = cameraProvider ?: return null
            return DragRunVideoCapabilities.query(provider, DragRunVideoSettings.lens(this))
        }

        fun refreshChips() {
            val caps = currentCaps()
            val quality = DragRunVideoSettings.quality(this)
            val fps = DragRunVideoSettings.fps(this)
            val lens = DragRunVideoSettings.lens(this)
            val dualSupported = ConcurrentPhoneCameras.isSupported(this, cameraProvider)
            val dual = dualSupported && DragRunVideoSettings.dualEnabled(this)
            val mic = DragRunVideoSettings.micEnabled(this)
            val allowedFps = caps?.fpsFor(quality) ?: setOf(30, 60)
            chipHd.visibility = chipVisibility(caps == null || DragRunVideoSettings.QualityOption.HD in caps.qualities)
            chipFhd.visibility = chipVisibility(caps == null || DragRunVideoSettings.QualityOption.FHD in caps.qualities)
            chipUhd.visibility = chipVisibility(
                !dual && (caps == null || DragRunVideoSettings.QualityOption.UHD in caps.qualities)
            )
            chip30.visibility = chipVisibility(30 in allowedFps)
            chip60.visibility = chipVisibility(60 in allowedFps)
            chipRear.visibility = chipVisibility(caps == null || DragRunVideoSettings.LensOption.REAR in caps.lenses)
            chipFront.visibility = chipVisibility(caps == null || DragRunVideoSettings.LensOption.FRONT in caps.lenses)
            chipBoth.visibility = chipVisibility(dualSupported)
            CameraSettingsSheet.compactChipRow(chipHd, chipFhd, chipUhd)
            CameraSettingsSheet.compactChipRow(chip30, chip60)
            CameraSettingsSheet.compactChipRow(chipRear, chipFront, chipBoth)
            selectChip(chipHd, quality == DragRunVideoSettings.QualityOption.HD)
            selectChip(chipFhd, quality == DragRunVideoSettings.QualityOption.FHD)
            selectChip(chipUhd, quality == DragRunVideoSettings.QualityOption.UHD)
            selectChip(chip30, fps == 30)
            selectChip(chip60, fps == 60)
            selectChip(chipRear, !dual && lens == DragRunVideoSettings.LensOption.REAR)
            selectChip(chipFront, !dual && lens == DragRunVideoSettings.LensOption.FRONT)
            selectChip(chipBoth, dual)
            selectChip(chipMicOn, mic)
            selectChip(chipMicOff, !mic)
        }

        fun applyAndRebind(
            quality: DragRunVideoSettings.QualityOption = DragRunVideoSettings.quality(this),
            fps: Int = DragRunVideoSettings.fps(this),
            lens: DragRunVideoSettings.LensOption = DragRunVideoSettings.lens(this),
            mic: Boolean = DragRunVideoSettings.micEnabled(this),
            dual: Boolean = DragRunVideoSettings.dualEnabled(this)
        ) {
            if (!canChangeCamera()) {
                Toast.makeText(this, getString(R.string.drag_camera_settings_while_recording), Toast.LENGTH_SHORT).show()
                return
            }
            val caps = cameraProvider?.let { DragRunVideoCapabilities.query(it, lens) }
            val (safeQuality, safeFps) = caps?.sanitize(quality, fps) ?: (quality to fps)
            val dualOk = dual && ConcurrentPhoneCameras.isSupported(this, cameraProvider)
            DragRunVideoSettings.save(
                this,
                quality = safeQuality,
                fps = safeFps,
                lens = lens,
                micEnabled = mic,
                dualEnabled = dualOk
            )
            refreshChips()
            if (activeRecording != null || recordingFinalizing) {
                pendingRebindAfterFinalize = true
                if (activeRecording != null) {
                    stopRecording(keep = false, force = !isLiveUnsavedRun())
                }
                return
            }
            cameraProvider?.let { bindUseCases(it) } ?: bindCamera()
        }

        chipHd.setOnClickListener { applyAndRebind(quality = DragRunVideoSettings.QualityOption.HD) }
        chipFhd.setOnClickListener { applyAndRebind(quality = DragRunVideoSettings.QualityOption.FHD) }
        chipUhd.setOnClickListener { applyAndRebind(quality = DragRunVideoSettings.QualityOption.UHD) }
        chip30.setOnClickListener { applyAndRebind(fps = 30) }
        chip60.setOnClickListener { applyAndRebind(fps = 60) }
        chipRear.setOnClickListener {
            applyAndRebind(lens = DragRunVideoSettings.LensOption.REAR, dual = false)
        }
        chipFront.setOnClickListener {
            applyAndRebind(lens = DragRunVideoSettings.LensOption.FRONT, dual = false)
        }
        chipBoth.setOnClickListener { applyAndRebind(dual = true) }
        chipMicOn.setOnClickListener {
            DragRunVideoSettings.save(this, micEnabled = true)
            refreshChips()
            if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            }
        }
        chipMicOff.setOnClickListener { applyAndRebind(mic = false) }
        refreshChips()
        dialog.show()
    }

    private fun selectChip(view: TextView, selected: Boolean) {
        view.setBackgroundResource(
            if (selected) R.drawable.bg_drag_video_chip_selected else R.drawable.bg_drag_video_chip
        )
    }

    private fun chipVisibility(visible: Boolean): Int {
        return if (visible) View.VISIBLE else View.GONE
    }

    private fun lockOrientation() {
        if (orientationLocked) return
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        orientationLocked = true
    }

    private fun unlockOrientation() {
        if (!orientationLocked) return
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        orientationLocked = false
    }

    companion object {
        private const val TAG = "DragRunVideo"
    }
}
