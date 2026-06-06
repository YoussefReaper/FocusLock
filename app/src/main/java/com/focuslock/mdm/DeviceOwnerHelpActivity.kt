package com.focuslock.mdm

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DeviceOwnerHelpActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var content: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_owner_help)

        root = findViewById(R.id.deviceOwnerRoot)
        content = findViewById(R.id.deviceOwnerContent)
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

        UiStyler.applyWallpaperOrColor(root, theme, wallpaper)
        UiStyler.applyTypefaceRecursive(root, font.typeface)

        val padding = UiStyler.dpToPx(this, density.contentPaddingDp)
        content.setPadding(padding, padding, padding, padding)

        window.statusBarColor = theme.background
        window.navigationBarColor = theme.background

        findViewById<TextView>(R.id.tvDeviceOwnerTitle).setTextColor(theme.textPrimary)
        findViewById<TextView>(R.id.tvDeviceOwnerSubtitle).setTextColor(theme.textSecondary)
        findViewById<TextView>(R.id.tvDeviceOwnerStepsTitle).setTextColor(theme.textPrimary)
        findViewById<TextView>(R.id.tvDeviceOwnerSteps).setTextColor(theme.textSecondary)
        findViewById<TextView>(R.id.tvDeviceOwnerCommand).setTextColor(theme.textPrimary)
        findViewById<TextView>(R.id.tvDeviceOwnerNotesTitle).setTextColor(theme.textPrimary)
        findViewById<TextView>(R.id.tvDeviceOwnerNotes).setTextColor(theme.textSecondary)
        findViewById<TextView>(R.id.tvDeviceAdminTitle).setTextColor(theme.textPrimary)
        findViewById<TextView>(R.id.tvDeviceAdminSteps).setTextColor(theme.textSecondary)

        UiStyler.applyCardBackground(findViewById(R.id.tvDeviceOwnerCommand), theme.card)
    }
}
