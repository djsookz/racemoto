package com.example.clinometer

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.MediaController
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import java.io.File
import java.util.Locale

class TrackSessionVideoActivity : AppCompatActivity() {

    private lateinit var btnBack: MaterialButton
    private lateinit var btnExport: MaterialButton
    private lateinit var tvVideoTitle: TextView
    private lateinit var tvVideoSubtitle: TextView
    private lateinit var videoView: VideoView
    private var videoUri: Uri? = null
    private var videoFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_track_session_video)

        btnBack = findViewById(R.id.btnVideoBack)
        btnExport = findViewById(R.id.btnVideoExport)
        tvVideoTitle = findViewById(R.id.tvVideoPlayerTitle)
        tvVideoSubtitle = findViewById(R.id.tvVideoPlayerSubtitle)
        videoView = findViewById(R.id.videoViewSession)

        videoUri = intent.getStringExtra("video_uri")
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
        videoFile = intent.getStringExtra("video_path")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }

        tvVideoTitle.text = intent.getStringExtra("video_title")
            ?: getString(R.string.track_session_video_player_title)

        btnBack.setOnClickListener {
            finish()
        }
        btnExport.setOnClickListener {
            shareVideo()
        }

        bindVideo()
    }

    override fun onPause() {
        super.onPause()
        if (videoView.isPlaying) {
            videoView.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        videoView.stopPlayback()
    }

    private fun bindVideo() {
        val playbackUri = resolvePlaybackUri()
        if (playbackUri == null) {
            Toast.makeText(this, getString(R.string.track_session_video_unavailable), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)

        videoView.setMediaController(mediaController)
        videoView.setVideoURI(playbackUri)
        videoView.setOnPreparedListener { player: MediaPlayer ->
            tvVideoSubtitle.text = formatDuration(player.duration.toLong())
            player.isLooping = false
            videoView.start()
        }
        videoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, getString(R.string.track_session_video_unavailable), Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun shareVideo() {
        val shareUri = resolvePlaybackUri()
        if (shareUri == null) {
            Toast.makeText(this, getString(R.string.track_session_video_unavailable), Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = resolveVideoMimeType(shareUri)
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_TITLE, tvVideoTitle.text.toString())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        runCatching {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.track_session_video_share_title)))
        }.onFailure {
            Toast.makeText(this, getString(R.string.track_session_video_share_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolvePlaybackUri(): Uri? {
        videoUri?.let { return it }
        return videoFile
            ?.takeIf { it.exists() }
            ?.let { file ->
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            }
    }

    private fun resolveVideoMimeType(uri: Uri): String {
        return contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            )
            ?: "video/mp4"
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}