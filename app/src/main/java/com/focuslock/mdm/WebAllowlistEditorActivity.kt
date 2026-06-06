package com.focuslock.mdm

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WebAllowlistEditorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_allowlist_editor)

        val editText = findViewById<EditText>(R.id.etWebAllowlist)
        val saveButton = findViewById<Button>(R.id.btnSaveWebAllowlist)

        val locked = AllowlistStore.isWebAllowlistLocked(this) || LockManager.isKioskActive(this)
        if (locked) {
            editText.isEnabled = false
            saveButton.isEnabled = false
            Toast.makeText(this, "Web allowlist is locked while kiosk is active", Toast.LENGTH_LONG)
                .show()
        }

        val lines = AllowlistStore.getWebAllowlistUrls(this)
            .sorted()
            .joinToString("\n")
        editText.setText(lines)

        saveButton.setOnClickListener {
            val raw = editText.text?.toString().orEmpty()
            val urls = raw
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { normalizeUrl(it) }
                .filter { isValidUrl(it) }
                .toSet()

            if (urls.isEmpty()) {
                Toast.makeText(this, "Add at least one valid URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AllowlistStore.setWebAllowlistUrls(this, urls)
            Toast.makeText(this, "Web allowlist saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        applyPersonalization()
    }

    override fun onResume() {
        super.onResume()
        KioskPolicy.syncLockTaskState(this)
        applyPersonalization()
    }

    private fun applyPersonalization() {
        val theme = UiPrefs.getTheme(this)
        val font = UiPrefs.getFont(this)
        val density = UiPrefs.getDensity(this)
        val wallpaper = UiPrefs.getWallpaper(this)

        val root = findViewById<View>(R.id.webAllowlistRoot)
        val content = findViewById<View>(R.id.webAllowlistContent)
        UiStyler.applyWallpaperOrColor(root, theme, wallpaper)
        UiStyler.applyTypefaceRecursive(root, font.typeface)

        val padding = UiStyler.dpToPx(this, density.contentPaddingDp)
        content.setPadding(padding, padding, padding, padding)

        window.statusBarColor = theme.background
        window.navigationBarColor = theme.background

        findViewById<TextView>(R.id.tvWebAllowlistTitle).setTextColor(theme.textPrimary)
        findViewById<TextView>(R.id.tvWebAllowlistHint).setTextColor(theme.textSecondary)

        val editText = findViewById<EditText>(R.id.etWebAllowlist)
        editText.setBackgroundColor(theme.input)
        editText.setTextColor(theme.textPrimary)
        editText.setHintTextColor(theme.textSecondary)

        val saveButton = findViewById<Button>(R.id.btnSaveWebAllowlist)
        saveButton.backgroundTintList = android.content.res.ColorStateList.valueOf(theme.accent)
        saveButton.setTextColor(theme.textPrimary)
        setHeightDp(saveButton, density.buttonHeightDp)
    }

    private fun setHeightDp(view: View, heightDp: Int) {
        val params = view.layoutParams
        params.height = UiStyler.dpToPx(this, heightDp)
        view.layoutParams = params
    }

    private fun normalizeUrl(value: String): String {
        return if (value.startsWith("http://") || value.startsWith("https://")) value
        else "https://$value"
    }

    private fun isValidUrl(value: String): Boolean {
        return try {
            val uri = Uri.parse(value)
            !uri.host.isNullOrBlank() && (uri.scheme == "http" || uri.scheme == "https")
        } catch (_: Exception) {
            false
        }
    }
}
