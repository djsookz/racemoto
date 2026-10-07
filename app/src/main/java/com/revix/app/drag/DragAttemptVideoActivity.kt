package com.revix.app.drag

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.MimeTypeMap
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.revix.app.DragAttempt
import com.revix.app.R
import com.revix.app.settings.LanguageManager
import java.io.File
import java.util.Locale
import kotlin.math.max

class DragAttemptVideoActivity : AppCompatActivity() {

    private lateinit var rootView: View
    private lateinit var headerView: View
    private lateinit var btnBack: View
    private lateinit var btnExport: MaterialButton
    private lateinit var btnFullscreen: MaterialButton
    private lateinit var tvVideoTitle: TextView
    private lateinit var tvVideoSubtitle: TextView
    private lateinit var videoContainer: FrameLayout
    private lateinit var videoStage: FrameLayout
    private lateinit var videoView: VideoView
    private var videoFile: File? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private var playbackPositionMs = 0
    private var wasPlaying = false
    private var mediaController: MediaController? = null
    private var videoContainerPortraitTopMargin = 0
    private var immersiveFullscreen = false
    private var playbackAttempt: DragAttempt? = null
    private var playbackMode: MeasurementMode = MeasurementMode.ALL
    private var playbackHud: DragPlaybackHudView? = null
    private val hudHandler = Handler(Looper.getMainLooper())
    private val hudTick = object : Runnable {
        override fun run() {
            bindPlaybackHud()
            hudHandler.postDelayed(this, 50)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_session_video)
        rootView = (findViewById<ViewGroup>(android.R.id.content)).getChildAt(0)

        headerView = findViewById(R.id.llVideoHeader)
        btnBack = findViewById(R.id.btnVideoBack)
        btnExport = findViewById(R.id.btnVideoExport)
        btnFullscreen = findViewById(R.id.btnVideoFullscreen)
        tvVideoTitle = findViewById(R.id.tvVideoPlayerTitle)
        tvVideoSubtitle = findViewById(R.id.tvVideoPlayerSubtitle)
        videoContainer = findViewById(R.id.flVideoContainer)
        videoStage = findViewById(R.id.flVideoStage)
        videoView = findViewById(R.id.videoViewSession)
        videoContainerPortraitTopMargin =
            (videoContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0

        videoFile = intent.getStringExtra(EXTRA_PATH)
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() }

        tvVideoTitle.text = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
            ?: getString(R.string.drag_attempt_video_title)
        btnExport.text = getString(R.string.drag_attempt_video_share)

        playbackPositionMs = savedInstanceState?.getInt(STATE_POSITION_MS, 0) ?: 0
        wasPlaying = savedInstanceState?.getBoolean(STATE_WAS_PLAYING, true) ?: true

        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        btnExport.setOnClickListener {
            DragAttemptHudExport.showShareOrExport(
                activity = this,
                videoFile = videoFile,
                attempt = playbackAttempt,
                mode = playbackMode,
                title = tvVideoTitle.text.toString()
            )
        }
        btnFullscreen.setOnClickListener { toggleImmersiveFullscreen() }
        videoContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            layoutVideoToFitCenter()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (immersiveFullscreen) {
                    toggleImmersiveFullscreen()
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })

        applyPlayerChrome()
        attachPlaybackHud()
        bindVideo()
        hudHandler.post(hudTick)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPlayerChrome()
        videoContainer.post { layoutVideoToFitCenter() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_POSITION_MS, videoView.currentPosition.coerceAtLeast(0))
        outState.putBoolean(STATE_WAS_PLAYING, videoView.isPlaying)
    }

    override fun onPause() {
        super.onPause()
        playbackPositionMs = videoView.currentPosition.coerceAtLeast(0)
        wasPlaying = videoView.isPlaying
        if (videoView.isPlaying) {
            videoView.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        applyPlayerChrome()
        if (wasPlaying && videoView.currentPosition > 0) {
            videoView.start()
        }
    }

    override fun onDestroy() {
        hudHandler.removeCallbacks(hudTick)
        super.onDestroy()
        videoView.stopPlayback()
    }

    private fun attachPlaybackHud() {
        playbackAttempt = pendingAttempt
        playbackMode = pendingMode
        val hud = DragPlaybackHudView(this)
        hud.bind(playbackAttempt, playbackMode)
        playbackHud = hud
        videoStage.addView(
            hud,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        videoContainer.bringChildToFront(btnFullscreen)
        bindPlaybackHud()
    }

    private fun bindPlaybackHud() {
        playbackHud?.setPositionMs(videoView.currentPosition.coerceAtLeast(0).toLong())
    }

    private fun bindVideo() {
        val playbackUri = resolvePlaybackUri(this, videoFile)
        if (playbackUri == null) {
            Toast.makeText(this, getString(R.string.drag_attempt_video_missing), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val controller = MediaController(this)
        controller.setAnchorView(videoContainer)
        mediaController = controller
        videoView.setMediaController(controller)
        videoContainer.bringChildToFront(btnFullscreen)
        videoView.setVideoURI(playbackUri)
        videoView.setOnPreparedListener { player: MediaPlayer ->
            tvVideoSubtitle.text = formatDuration(player.duration.toLong())
            player.isLooping = false
            player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            videoWidth = player.videoWidth
            videoHeight = player.videoHeight
            layoutVideoToFitCenter()
            if (playbackPositionMs > 0) {
                videoView.seekTo(playbackPositionMs)
            }
            if (wasPlaying) {
                videoView.start()
            }
        }
        videoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, getString(R.string.drag_attempt_video_missing), Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun toggleImmersiveFullscreen() {
        immersiveFullscreen = !immersiveFullscreen
        applyPlayerChrome()
        videoContainer.post { layoutVideoToFitCenter() }
    }

    private fun applyPlayerChrome() {
        val immersive = immersiveFullscreen
        headerView.visibility = if (immersive) View.GONE else View.VISIBLE
        (videoContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.topMargin = if (immersive) 0 else videoContainerPortraitTopMargin
            videoContainer.layoutParams = params
        }
        btnFullscreen.setIconResource(
            if (immersive) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen
        )
        btnFullscreen.contentDescription = getString(
            if (immersive) {
                R.string.track_session_video_player_exit_fullscreen
            } else {
                R.string.track_session_video_player_fullscreen
            }
        )
        applyPlayerInsets(immersive)
        mediaController?.setAnchorView(videoContainer)
        videoContainer.bringChildToFront(btnFullscreen)
    }

    private fun applyPlayerInsets(immersive: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyDisplayCutoutMode(immersive)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (immersive) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            if (immersive) {
                view.setPadding(0, 0, 0, 0)
            } else {
                val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val cutoutInsets = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                view.setPadding(
                    20.dp + max(systemBarsInsets.left, cutoutInsets.left),
                    20.dp + max(systemBarsInsets.top, cutoutInsets.top),
                    20.dp + max(systemBarsInsets.right, cutoutInsets.right),
                    20.dp + max(systemBarsInsets.bottom, cutoutInsets.bottom)
                )
            }
            insets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    private fun applyDisplayCutoutMode(immersive: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val attrs = window.attributes
        attrs.layoutInDisplayCutoutMode = if (immersive) {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        } else {
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
        window.attributes = attrs
    }

    private fun layoutVideoToFitCenter() {
        val containerW = videoContainer.width
        val containerH = videoContainer.height
        if (containerW <= 1 || containerH <= 1 || videoWidth <= 0 || videoHeight <= 0) return
        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        val containerAspect = containerW.toFloat() / containerH.toFloat()
        val params = videoStage.layoutParams as FrameLayout.LayoutParams
        if (containerAspect > videoAspect) {
            params.height = containerH
            params.width = (containerH * videoAspect).toInt().coerceAtLeast(1)
        } else {
            params.width = containerW
            params.height = (containerW / videoAspect).toInt().coerceAtLeast(1)
        }
        params.gravity = Gravity.CENTER
        videoStage.layoutParams = params
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
        return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_PATH = "video_path"
        const val EXTRA_TITLE = "video_title"
        private const val STATE_POSITION_MS = "video_position_ms"
        private const val STATE_WAS_PLAYING = "video_was_playing"

        @Volatile
        var pendingAttempt: DragAttempt? = null
        @Volatile
        var pendingMode: MeasurementMode = MeasurementMode.ALL

        fun open(
            context: Context,
            path: String,
            title: String,
            attempt: DragAttempt? = null,
            mode: MeasurementMode = MeasurementMode.ALL
        ) {
            pendingAttempt = attempt
            pendingMode = mode
            context.startActivity(
                Intent(context, DragAttemptVideoActivity::class.java)
                    .putExtra(EXTRA_PATH, path)
                    .putExtra(EXTRA_TITLE, title)
            )
        }

        fun shareVideo(context: Context, file: File?, title: String) {
            val shareUri = resolvePlaybackUri(context, file)
            if (shareUri == null) {
                Toast.makeText(context, context.getString(R.string.drag_attempt_video_missing), Toast.LENGTH_SHORT).show()
                return
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = context.contentResolver.getType(shareUri)
                    ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        MimeTypeMap.getFileExtensionFromUrl(shareUri.toString())
                    )
                    ?: "video/mp4"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_TITLE, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                context.startActivity(
                    Intent.createChooser(shareIntent, context.getString(R.string.drag_attempt_video_share_title))
                )
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.drag_attempt_video_share_failed), Toast.LENGTH_SHORT).show()
            }
        }

        fun resolvePlaybackUri(context: Context, file: File?): Uri? {
            return file?.takeIf { it.exists() }?.let { video ->
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", video)
            }
        }
    }
}
