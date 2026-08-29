package com.focuslock.mdm

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * The video library: a reward that arrives on a timer.
 *
 * One new video unlocks every 24 hours and stays unlocked afterwards. Delayed
 * gratification is the whole mechanic, so the countdown is shown prominently
 * rather than hidden — waiting is easier when you can see the wait shrinking.
 */
class VideoLibraryActivity : AppCompatActivity() {

    private lateinit var tokens: UiPrefs.Tokens
    private lateinit var tvHeader: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var rvVideos: RecyclerView

    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            refreshSubtitle()
            countdownHandler.postDelayed(this, 60_000)
        }
    }

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) loadLibrary() else finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_library)

        tvHeader = findViewById(R.id.tvLibraryHeader)
        tvSubtitle = findViewById(R.id.tvLibrarySubtitle)
        rvVideos = findViewById(R.id.rvVideos)
        rvVideos.layoutManager = LinearLayoutManager(this)

        applyTheme()

        if (!VideoManager.isFolderSelected(this)) {
            folderPicker.launch(Intent(this, AnimeFolderActivity::class.java))
        } else {
            loadLibrary()
        }
    }

    override fun onResume() {
        super.onResume()
        KioskPolicy.syncLockTaskState(this)
        applyTheme()
        if (VideoManager.isFolderSelected(this)) loadLibrary()
        countdownHandler.post(countdownRunnable)
    }

    override fun onPause() {
        super.onPause()
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    private fun applyTheme() {
        tokens = UiPrefs.resolve(this)
        FocusUi.applySystemBars(window, tokens)

        val root = findViewById<View>(R.id.videoLibraryRoot)
        if (tokens.wallpaperRes != 0) {
            root.setBackgroundResource(tokens.wallpaperRes)
        } else {
            root.setBackgroundColor(tokens.background)
        }

        findViewById<View>(R.id.videoLibraryDivider).setBackgroundColor(tokens.divider)

        tvHeader.setTextColor(tokens.textPrimary)
        tvHeader.typeface = Typeface.create(tokens.typeface, Typeface.BOLD)
        tvHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(22f))

        tvSubtitle.setTextColor(tokens.textSecondary)
        tvSubtitle.typeface = tokens.typeface
        tvSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(13.5f))

        val padding = FocusUi.dp(this, tokens.density.contentPaddingDp - 4)
        rvVideos.setPadding(padding, FocusUi.dp(this, 8), padding, padding)
    }

    private fun loadLibrary() {
        if (!VideoManager.isFolderSelected(this)) return

        val videos = VideoManager.getAllVideos(this)
        val canUnlock = VideoManager.canUnlockToday(this)

        tvHeader.text = "Video library"
        refreshSubtitle()

        rvVideos.adapter = VideoAdapter(
            context = this,
            items = videos,
            canUnlockNow = canUnlock,
            onUnlock = { item -> confirmUnlock(item) },
            onPlay = { item -> openPlayer(item) }
        )
    }

    private fun refreshSubtitle() {
        if (!this::tvSubtitle.isInitialized) return
        val unlocked = VideoManager.unlockedCount(this)
        tvSubtitle.text = if (VideoManager.canUnlockToday(this)) {
            unlocked.toString() + " already yours. One more is ready to open."
        } else {
            unlocked.toString() + " already yours. The next one opens in " +
                VideoManager.nextUnlockFormatted(this) + "."
        }
    }

    private fun confirmUnlock(item: VideoItem) {
        FocusDialog.alert(
            this,
            title = "Open " + item.name + "?",
            message = "This uses the unlock for the next 24 hours. Once opened it stays yours, " +
                "so there is no rush and nothing expires.",
            confirmLabel = "Open it",
            cancelLabel = "Not yet",
            onConfirm = {
                VideoManager.unlockVideo(this, item.uri)
                loadLibrary()
            }
        )
    }

    private fun openPlayer(item: VideoItem) {
        startActivity(
            Intent(this, DailyPlayerActivity::class.java).apply {
                putExtra(DailyPlayerActivity.EXTRA_VIDEO_URI, item.uri.toString())
                putExtra(DailyPlayerActivity.EXTRA_VIDEO_NAME, item.name)
            }
        )
    }
}
