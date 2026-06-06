package com.focuslock.mdm

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Full-screen landscape video player (ExoPlayer / Media3).
 *
 * Receives the video URI via [EXTRA_VIDEO_URI].
 * This activity is only reachable for unlocked videos — the gate is in
 * VideoLibraryActivity, not here.
 */
class DailyPlayerActivity : AppCompatActivity() {

    private lateinit var playerView : PlayerView
    private var player              : ExoPlayer? = null

    companion object {
        const val EXTRA_VIDEO_URI  = "video_uri"
        const val EXTRA_VIDEO_NAME = "video_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while watching
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_daily_player)
        playerView = findViewById(R.id.playerView)
    }

    override fun onStart() {
        super.onStart()
        initPlayer()
    }

    override fun onResume() {
        super.onResume()
        KioskPolicy.syncLockTaskState(this)
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun initPlayer() {
        val uriString = intent.getStringExtra(EXTRA_VIDEO_URI) ?: run {
            finish()
            return
        }

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            val mediaItem     = MediaItem.fromUri(Uri.parse(uriString))
            exo.setMediaItem(mediaItem)
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }
}