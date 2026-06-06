package com.focuslock.mdm

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * The dedicated video library screen.
 *
 * On first ever open: launches AnimeFolderActivity to pick the permanent folder.
 * After folder is set: shows all videos with lock/unlock/play state.
 *
 * Rules displayed here:
 *  - Gray + locked icon  → locked, no unlock available yet
 *  - Blue "Unlock" badge → locked, but today's 24h slot is available
 *  - Green "Play" badge  → unlocked forever, tap to watch
 */
class VideoLibraryActivity : AppCompatActivity() {

    private lateinit var tvHeader   : TextView
    private lateinit var tvSubtitle : TextView
    private lateinit var rvVideos   : RecyclerView

    private val countdownHandler  = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            refreshSubtitle()
            // Refresh every minute so the countdown ticks
            countdownHandler.postDelayed(this, 60_000)
        }
    }

    // Launched when we need the user to pick a folder
    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                loadLibrary()
            } else {
                // User somehow escaped folder picker — send back to home
                finish()
            }
        }

    // ── Lifecycle ─────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_library)

        tvHeader   = findViewById(R.id.tvLibraryHeader)
        tvSubtitle = findViewById(R.id.tvLibrarySubtitle)
        rvVideos   = findViewById(R.id.rvVideos)

        rvVideos.layoutManager = LinearLayoutManager(this)

        applyPersonalization()

        // First launch: pick folder
        if (!VideoManager.isFolderSelected(this)) {
            folderPicker.launch(Intent(this, AnimeFolderActivity::class.java))
        } else {
            loadLibrary()
        }
    }

    override fun onResume() {
        super.onResume()
        KioskPolicy.syncLockTaskState(this)
        applyPersonalization()
        loadLibrary()
        countdownHandler.post(countdownRunnable)
    }

    override fun onPause() {
        super.onPause()
        countdownHandler.removeCallbacks(countdownRunnable)
    }

    private fun applyPersonalization() {
        val theme = UiPrefs.getTheme(this)
        val font = UiPrefs.getFont(this)
        val density = UiPrefs.getDensity(this)
        val wallpaper = UiPrefs.getWallpaper(this)

        val root = findViewById<android.view.View>(R.id.videoLibraryRoot)
        val header = findViewById<android.view.View>(R.id.videoLibraryHeader)
        val divider = findViewById<android.view.View>(R.id.videoLibraryDivider)

        UiStyler.applyWallpaperOrColor(root, theme, wallpaper)
        UiStyler.applyTypefaceRecursive(root, font.typeface)

        window.statusBarColor = theme.background
        window.navigationBarColor = theme.background

        header.setBackgroundColor(theme.card)
        divider.setBackgroundColor(theme.divider)

        tvHeader.setTextColor(theme.textPrimary)
        tvSubtitle.setTextColor(theme.textSecondary)

        val padding = UiStyler.dpToPx(this, density.contentPaddingDp / 2)
        rvVideos.setPadding(padding, padding, padding, padding)
    }

    // ── Library loading ───────────────────────────────────────────

    private fun loadLibrary() {
        if (!VideoManager.isFolderSelected(this)) return

        val videos       = VideoManager.getAllVideos(this)
        val canUnlock    = VideoManager.canUnlockToday(this)
        val unlockedCnt  = VideoManager.unlockedCount(this)

        tvHeader.text = "Video Library  ·  ${videos.size} videos"
        refreshSubtitle()

        rvVideos.adapter = VideoAdapter(
            context      = this,
            items        = videos,
            canUnlockNow = canUnlock,
            onUnlock     = { item -> confirmUnlock(item) },
            onPlay       = { item -> openPlayer(item) }
        )
    }

    private fun refreshSubtitle() {
        val unlockedCnt = VideoManager.unlockedCount(this)
        tvSubtitle.text = if (VideoManager.canUnlockToday(this)) {
            "$unlockedCnt unlocked  ·  Unlock available now!"
        } else {
            "$unlockedCnt unlocked  ·  Next in ${VideoManager.nextUnlockFormatted(this)}"
        }
    }

    // ── Unlock confirmation ───────────────────────────────────────

    private fun confirmUnlock(item: VideoItem) {
        AlertDialog.Builder(this)
            .setTitle("Unlock this video?")
            .setMessage(
                "\"${item.name}\"\n\n" +
                        "This uses your unlock for the next 24 hours.\n" +
                        "Once unlocked it stays accessible for the rest of the 90-day period."
            )
            .setPositiveButton("Unlock") { _, _ ->
                VideoManager.unlockVideo(this, item.uri)
                loadLibrary()   // refresh the list
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Player launch ─────────────────────────────────────────────

    private fun openPlayer(item: VideoItem) {
        val intent = Intent(this, DailyPlayerActivity::class.java).apply {
            putExtra(DailyPlayerActivity.EXTRA_VIDEO_URI,  item.uri.toString())
            putExtra(DailyPlayerActivity.EXTRA_VIDEO_NAME, item.name)
        }
        startActivity(intent)
    }
}