package com.focuslock.mdm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SetupPermissionsActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var content: View

    private lateinit var tvStatus: TextView

    private lateinit var tvUsageStatus: TextView
    private lateinit var tvNotificationStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvBatteryStatus: TextView
    private lateinit var tvDeviceAdminStatus: TextView
    private lateinit var tvDeviceOwnerStatus: TextView

    private lateinit var btnUsage: Button
    private lateinit var btnNotification: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnBattery: Button
    private lateinit var btnDeviceAdmin: Button
    private lateinit var btnDeviceOwner: Button
    private lateinit var btnRefresh: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_permissions)

        root = findViewById(R.id.setupRoot)
        content = findViewById(R.id.setupContent)

        tvStatus = findViewById(R.id.tvSetupStatus)

        tvUsageStatus = findViewById(R.id.tvUsageStatus)
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvBatteryStatus = findViewById(R.id.tvBatteryStatus)
        tvDeviceAdminStatus = findViewById(R.id.tvDeviceAdminStatus)
        tvDeviceOwnerStatus = findViewById(R.id.tvDeviceOwnerStatus)

        btnUsage = findViewById(R.id.btnUsage)
        btnNotification = findViewById(R.id.btnNotification)
        btnOverlay = findViewById(R.id.btnOverlay)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnBattery = findViewById(R.id.btnBattery)
        btnDeviceAdmin = findViewById(R.id.btnDeviceAdmin)
        btnDeviceOwner = findViewById(R.id.btnDeviceOwner)
        btnRefresh = findViewById(R.id.btnRefreshSetup)

        btnUsage.setOnClickListener {
            openWithAllowance(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        }

        btnNotification.setOnClickListener {
            openWithAllowance(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }

        btnOverlay.setOnClickListener {
            openWithAllowance(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            })
        }

        btnAccessibility.setOnClickListener {
            openWithAllowance(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnBattery.setOnClickListener {
            openWithAllowance(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        }

        btnDeviceAdmin.setOnClickListener {
            val admin = ComponentName(this, AdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "FocusLock requires Device Admin for kiosk mode.")
            }
            openWithAllowance(intent)
        }

        btnDeviceOwner.setOnClickListener {
            startActivity(Intent(this, DeviceOwnerHelpActivity::class.java))
        }

        btnRefresh.setOnClickListener {
            refreshStatuses()
        }
    }

    override fun onResume() {
        super.onResume()
        KioskPolicy.syncLockTaskState(this)
        applyPersonalization()
        refreshStatuses()
    }

    private fun refreshStatuses() {
        val usage = SetupChecks.hasUsageAccess(this)
        val notif = SetupChecks.isNotificationAccessGranted(this)
        val overlay = SetupChecks.canDrawOverlays(this)
        val accessibility = SetupChecks.isWhatsAppGuardEnabled(this)
        val battery = SetupChecks.isIgnoringBatteryOptimizations(this)
        val admin = SetupChecks.isDeviceAdminActive(this)
        val owner = SetupChecks.isDeviceOwner(this)

        val theme = UiPrefs.getTheme(this)
        val okColor = theme.accent
        val badColor = android.graphics.Color.parseColor("#EF4444")

        updateStatus(tvUsageStatus, usage, okColor, badColor)
        updateStatus(tvNotificationStatus, notif, okColor, badColor)
        updateStatus(tvOverlayStatus, overlay, okColor, badColor)
        updateStatus(tvAccessibilityStatus, accessibility, okColor, badColor)
        updateStatus(tvBatteryStatus, battery, okColor, badColor)
        updateStatus(tvDeviceAdminStatus, admin, okColor, badColor)
        updateStatus(tvDeviceOwnerStatus, owner, okColor, badColor)

        val grantedCount = listOf(usage, notif, overlay, accessibility, battery, admin, owner).count { it }
        tvStatus.text = "Status: $grantedCount / 7 granted"
    }

    private fun updateStatus(view: TextView, granted: Boolean, okColor: Int, badColor: Int) {
        view.text = if (granted) "Granted" else "Required"
        view.setTextColor(if (granted) okColor else badColor)
    }

    private fun openWithAllowance(intent: Intent) {
        LockManager.allowSettingsUntil(this, System.currentTimeMillis() + 2 * 60 * 1000)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            return
        }

        val fallback = Intent(Settings.ACTION_SETTINGS)
        if (fallback.resolveActivity(packageManager) != null) {
            startActivity(fallback)
            Toast.makeText(this, "Opened general settings", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Setting not available on this device", Toast.LENGTH_SHORT).show()
        }
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

        val cardIds = listOf(
            R.id.rowUsage,
            R.id.rowNotification,
            R.id.rowOverlay,
            R.id.rowAccessibility,
            R.id.rowBattery,
            R.id.rowDeviceAdmin,
            R.id.rowDeviceOwner
        )
        cardIds.forEach { id ->
            UiStyler.applyCardBackground(findViewById(id), theme.card)
        }

        val buttonIds = listOf(
            R.id.btnUsage,
            R.id.btnNotification,
            R.id.btnOverlay,
            R.id.btnAccessibility,
            R.id.btnBattery,
            R.id.btnDeviceAdmin,
            R.id.btnDeviceOwner
        )
        buttonIds.forEach { id ->
            val btn = findViewById<Button>(id)
            btn.backgroundTintList = ColorStateList.valueOf(theme.accent)
            btn.setTextColor(theme.textPrimary)
            setHeightDp(btn, density.buttonHeightDp)
        }

        btnRefresh.backgroundTintList = ColorStateList.valueOf(theme.card)
        btnRefresh.setTextColor(theme.textPrimary)
        setHeightDp(btnRefresh, density.buttonHeightDp)

        findViewById<TextView>(R.id.tvSetupTitle).setTextColor(theme.textPrimary)
        findViewById<TextView>(R.id.tvSetupSubtitle).setTextColor(theme.textSecondary)
        findViewById<TextView>(R.id.tvSetupStatus).setTextColor(theme.textSecondary)

        val labelIds = listOf(
            R.id.tvUsageLabel,
            R.id.tvNotificationLabel,
            R.id.tvOverlayLabel,
            R.id.tvAccessibilityLabel,
            R.id.tvBatteryLabel,
            R.id.tvDeviceAdminLabel,
            R.id.tvDeviceOwnerLabel
        )
        labelIds.forEach { id ->
            findViewById<TextView>(id).setTextColor(theme.textPrimary)
        }
    }

    private fun setHeightDp(view: View, heightDp: Int) {
        val params = view.layoutParams
        params.height = UiStyler.dpToPx(this, heightDp)
        view.layoutParams = params
    }
}
