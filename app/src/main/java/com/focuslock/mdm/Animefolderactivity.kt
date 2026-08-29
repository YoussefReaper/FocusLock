package com.focuslock.mdm

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * The one-time folder picker for the video library.
 *
 * Choosing the folder once and never again is the point: a reward you can
 * re-point at a different folder whenever you like is not a reward, it is a
 * browser. The screen says so plainly before anything is committed.
 *
 * The class name is kept to match the existing manifest entry.
 */
class AnimeFolderActivity : AppCompatActivity() {

    private lateinit var tokens: UiPrefs.Tokens
    private var pendingUri: Uri? = null

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            pendingUri = uri
            render()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anime_folder)

        if (VideoManager.isFolderSelected(this)) {
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        })

        render()
    }

    override fun onResume() {
        super.onResume()
        KioskPolicy.syncLockTaskState(this)
        render()
    }

    private fun render() {
        tokens = UiPrefs.resolve(this)
        FocusUi.applySystemBars(window, tokens)

        val root = findViewById<android.view.View>(R.id.folderRoot)
        if (tokens.wallpaperRes != 0) {
            root.setBackgroundResource(tokens.wallpaperRes)
        } else {
            root.setBackgroundColor(tokens.background)
        }

        val title = findViewById<TextView>(R.id.tvFolderTitle)
        title.setTextColor(tokens.textPrimary)
        title.typeface = android.graphics.Typeface.create(tokens.typeface, android.graphics.Typeface.BOLD)
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, tokens.scaled(24f))

        val subtitle = findViewById<TextView>(R.id.tvFolderSubtitle)
        subtitle.text = "Everything inside becomes your library. One new video unlocks every " +
            "24 hours, and stays unlocked for good.\n\nThe folder is fixed once you confirm."
        subtitle.setTextColor(tokens.textSecondary)
        subtitle.typeface = tokens.typeface
        subtitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, tokens.scaled(14f))

        val pick = findViewById<TextView>(R.id.btnPickFolder)
        styleButton(pick, tokens.surfaceAlt, tokens.textPrimary)
        pick.setOnClickListener {
            try {
                folderPicker.launch(null)
            } catch (_: Exception) {
                FocusDialog.toast(this, "No file picker available on this phone.")
            }
        }

        val chosen = findViewById<TextView>(R.id.tvChosenPath)
        chosen.setTextColor(tokens.accent)
        chosen.typeface = tokens.typeface
        chosen.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, tokens.scaled(13f))
        chosen.text = pendingUri?.let { "Chosen: " + (it.lastPathSegment ?: it.toString()) }.orEmpty()

        val confirm = findViewById<TextView>(R.id.btnConfirm)
        val ready = pendingUri != null
        styleButton(confirm, if (ready) tokens.accent else tokens.track, if (ready) tokens.onAccent else tokens.textMuted)
        confirm.isEnabled = ready
        confirm.alpha = if (ready) 1f else 0.6f
        confirm.setOnClickListener {
            val uri = pendingUri ?: return@setOnClickListener
            try {
                VideoManager.setFolder(this, uri)
            } catch (_: Exception) {
                FocusDialog.toast(this, "Android would not grant lasting access to that folder.")
                return@setOnClickListener
            }
            setResult(Activity.RESULT_OK)
            finish()
        }

        val warning = findViewById<TextView>(R.id.tvFolderWarning)
        warning.text = "This choice is permanent for the life of the install."
        warning.setTextColor(tokens.textMuted)
        warning.typeface = tokens.typeface
        warning.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, tokens.scaled(11.5f))
    }

    private fun styleButton(view: TextView, fill: Int, textColor: Int) {
        view.setTextColor(textColor)
        view.typeface = android.graphics.Typeface.create(tokens.typeface, android.graphics.Typeface.BOLD)
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, tokens.scaled(15f))
        view.background = FocusUi.roundedShape(this, fill, minOf(tokens.radiusDp, 20))
        val padding = FocusUi.dp(this, 16)
        view.setPadding(padding, padding, padding, padding)
        view.isClickable = true
        view.isFocusable = true
    }
}
