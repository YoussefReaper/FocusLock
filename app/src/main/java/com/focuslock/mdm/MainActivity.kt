package com.focuslock.mdm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private enum class MainTab {
        DASHBOARD,
        TASKS,
        TOOLS,
        QUICK,
        MANAGE
    }

    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName
    private val deviceOwnerWarningShownKey = "device_owner_warning_shown"
    private var currentTab: MainTab = MainTab.DASHBOARD

    // Ticks the countdown every second while the screen is on
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            updateDashboardUI()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        onBackPressedDispatcher.addCallback(this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { /* trap */ }
            })

        dpm   = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        admin = ComponentName(this, AdminReceiver::class.java)

        LockManager.initLock(this)

        findViewById<LinearLayout>(R.id.btnTextSearch).setOnClickListener {
            startActivity(Intent(this, TextSearchActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnBrowse).setOnClickListener {
            startActivity(Intent(this, WebViewActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnPersonalize).setOnClickListener {
            startActivity(Intent(this, PersonalizationActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnVideoLibrary).setOnClickListener {
            startActivity(Intent(this, VideoLibraryActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnEditApps).setOnClickListener {
            openAppAllowlistDialog()
        }
        findViewById<LinearLayout>(R.id.btnEditWeb).setOnClickListener {
            startActivity(Intent(this, WebAllowlistEditorActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnSchedule).setOnClickListener {
            startActivity(Intent(this, ScheduleActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnFactoryReset).setOnClickListener {
            confirmFactoryReset()
        }

        findViewById<LinearLayout>(R.id.btnSetupPermissions).setOnClickListener {
            startActivity(Intent(this, SetupPermissionsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnDeviceOwnerHelp).setOnClickListener {
            startActivity(Intent(this, DeviceOwnerHelpActivity::class.java))
        }

        findViewById<android.widget.Button>(R.id.btnStartKiosk).setOnClickListener {
            startKioskFromInputs()
        }
        findViewById<android.widget.Button>(R.id.btnDisableAdb).setOnClickListener {
            disableAdbDebugging()
        }

        findViewById<android.widget.Button>(R.id.btnWifi).setOnClickListener {
            openQuickSetting(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        findViewById<android.widget.Button>(R.id.btnHotspot).setOnClickListener {
            openQuickSetting(Intent("android.settings.TETHER_SETTINGS"))
        }
        findViewById<android.widget.Button>(R.id.btnMobileData).setOnClickListener {
            openMobileDataSettings()
        }
        findViewById<android.widget.Button>(R.id.btnBluetooth).setOnClickListener {
            openQuickSetting(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
        findViewById<android.widget.Button>(R.id.btnSound).setOnClickListener {
            openQuickSetting(Intent(Settings.ACTION_SOUND_SETTINGS))
        }

        findViewById<android.widget.Button>(R.id.tabDashboard).setOnClickListener {
            selectTab(MainTab.DASHBOARD)
        }
        findViewById<android.widget.Button>(R.id.tabTasks).setOnClickListener {
            selectTab(MainTab.TASKS)
        }
        findViewById<android.widget.Button>(R.id.tabTools).setOnClickListener {
            selectTab(MainTab.TOOLS)
        }
        findViewById<android.widget.Button>(R.id.tabQuick).setOnClickListener {
            selectTab(MainTab.QUICK)
        }
        findViewById<android.widget.Button>(R.id.tabManage).setOnClickListener {
            selectTab(MainTab.MANAGE)
        }

        selectTab(MainTab.DASHBOARD)
    }

    override fun onResume() {
        super.onResume()
        applyPersonalization()
        val kioskActive = LockManager.isKioskActive(this)
        val isDeviceOwner = dpm.isDeviceOwnerApp(packageName)

        if (kioskActive) {
            AppBlockerService.start(this)

            if (isDeviceOwner) {
                applyDeviceOwnerPolicies()
            } else {
                maybeShowDeviceOwnerWarning()
            }

            KioskPolicy.syncLockTaskState(this)

            if (!runSecuritySweep()) {
                updateDashboardUI()
                loadAppGrid()
                handler.post(ticker)
                return
            }

            markSecurityBaselineReady()
            secureChromeSandbox()

            if (isDeviceOwner) {
                applyDeviceOwnerPolicies()
            }

            KioskPolicy.syncLockTaskState(this)
        } else {
            if (isDeviceOwner) {
                KioskPolicy.clearDeviceOwnerKioskPolicies(this, dpm, admin)
            }
            AllowlistStore.setAppAllowlistLocked(this, false)
            AllowlistStore.setWebAllowlistLocked(this, false)
            KioskPolicy.syncLockTaskState(this)
        }

        updateDashboardUI()
        loadAppGrid()
        handler.post(ticker)   // start live countdown
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)   // stop when screen is off
    }

    // ── Dashboard ─────────────────────────────────────────────────

    private fun updateDashboardUI() {
        val theme = UiPrefs.getTheme(this)
        val prefs = getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        val kioskActive = LockManager.isKioskActive(this)
        val durationMs = LockManager.getKioskDurationMs(this)
        val remaining = LockManager.getKioskRemainingMs(this)

        if (!kioskActive || durationMs <= 0L) {
            findViewById<TextView>(R.id.tvDaysLeft).text = "Not active"
            findViewById<TextView>(R.id.tvDaysDone).text = "-"
            findViewById<TextView>(R.id.tvProgress).text = "0%"
            findViewById<TextView>(R.id.tvUnlockDate).text = "-"
            val progressCircle = findViewById<CircularProgressIndicator>(R.id.progressCircle)
            progressCircle.progress = 0
            progressCircle.setIndicatorColor(theme.accent)
            progressCircle.setTrackColor(theme.track)
            updateAdbButtonState()
            return
        }

        val elapsed = durationMs - remaining
        val progressPercent = ((elapsed.toFloat() / durationMs.toFloat()) * 100)
            .toInt().coerceIn(0, 100)
        val startMs = prefs.getLong(Constants.KEY_KIOSK_START_MS, System.currentTimeMillis())
        val unlockTime = startMs + durationMs

        val daysRemaining = remaining / 86_400_000L
        val hoursRemaining = (remaining % 86_400_000L) / 3_600_000L
        val minutesRemaining = (remaining % 3_600_000L) / 60_000L
        val secondsRemaining = (remaining % 60_000L) / 1_000L
        val timeLeftFormatted = String.format(
            Locale.getDefault(),
            "%dd %02dh %02dm %02ds",
            daysRemaining, hoursRemaining, minutesRemaining, secondsRemaining
        )

        val daysDone = elapsed / 86_400_000L
        val unlockStr = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            .format(Date(unlockTime))

        findViewById<TextView>(R.id.tvDaysLeft).text = timeLeftFormatted
        findViewById<TextView>(R.id.tvDaysDone).text = daysDone.toString()
        findViewById<TextView>(R.id.tvProgress).text = "$progressPercent%"
        findViewById<TextView>(R.id.tvUnlockDate).text = unlockStr

        val progressCircle = findViewById<CircularProgressIndicator>(R.id.progressCircle)
        progressCircle.progress = progressPercent
        progressCircle.setIndicatorColor(theme.accent)
        progressCircle.setTrackColor(theme.track)

        updateAdbButtonState()
    }

    // ── Security sweep ────────────────────────────────────────────

    private fun runSecuritySweep(): Boolean {
        if (!SetupChecks.hasUsageAccess(this)) {
            return handleMissingSecurityRequirement(
                requiredMessage = "⚠️ REQUIRED: Grant Usage Access to FocusLock",
                settingsIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                }
            )
        }
        if (!SetupChecks.isNotificationAccessGranted(this)) {
            return handleMissingSecurityRequirement(
                requiredMessage = "⚠️ REQUIRED: Grant Notification Access",
                settingsIntent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            )
        }
        if (!SetupChecks.canDrawOverlays(this)) {
            return handleMissingSecurityRequirement(
                requiredMessage = "⚠️ REQUIRED: Allow Display Over Other Apps",
                settingsIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
                }
            )
        }
        if (!SetupChecks.isWhatsAppGuardEnabled(this)) {
            return handleMissingSecurityRequirement(
                requiredMessage = "⚠️ REQUIRED: Enable Accessibility (WhatsApp Guard)",
                settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        }
        if (!SetupChecks.isIgnoringBatteryOptimizations(this)) {
            return handleMissingSecurityRequirement(
                requiredMessage = "⚠️ REQUIRED: Allow Unrestricted Battery",
                settingsIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
                }
            )
        }
        if (!SetupChecks.isDeviceAdminActive(this)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "FocusLock requires Device Admin for kiosk mode.")
            }
            return handleMissingSecurityRequirement(
                requiredMessage = "⚠️ REQUIRED: Enable Device Admin",
                settingsIntent = intent
            )
        }
        if (!SetupChecks.isDeviceOwner(this)) {
            return handleMissingSecurityRequirement(
                requiredMessage = "⚠️ REQUIRED: Set Device Owner from a computer",
                settingsIntent = null,
                openHelp = true
            )
        }
        return true
    }

    private fun maybeShowDeviceOwnerWarning() {
        if (!LockManager.isKioskActive(this)) return

        val prefs = getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        if (prefs.getBoolean(deviceOwnerWarningShownKey, false)) return

        Toast.makeText(
            this,
            "True kiosk mode needs Device Owner. Without it, Android may still allow notification shade or launcher escape.",
            Toast.LENGTH_LONG
        ).show()

        prefs.edit().putBoolean(deviceOwnerWarningShownKey, true).apply()
    }

    private fun handleMissingSecurityRequirement(
        requiredMessage: String,
        settingsIntent: Intent?,
        openHelp: Boolean = false
    ): Boolean {
        Toast.makeText(this, requiredMessage, Toast.LENGTH_LONG).show()
        if (settingsIntent != null) {
            LockManager.allowSettingsUntil(this, System.currentTimeMillis() + 2 * 60 * 1000)
            startActivity(settingsIntent)
        } else if (openHelp) {
            startActivity(Intent(this, DeviceOwnerHelpActivity::class.java))
        }
        return false
    }

    // ── Device Owner policies ─────────────────────────────────────

    private fun markSecurityBaselineReady() {
        val prefs = getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Constants.KEY_SECURITY_BASELINE_READY, false)) {
            prefs.edit().putBoolean(Constants.KEY_SECURITY_BASELINE_READY, true).apply()
        }
    }

    private fun applyDeviceOwnerPolicies() {
        try {
            dpm.setUninstallBlocked(admin, packageName, true)
        } catch (e: Exception) {
            Log.w("FocusLockPolicy", "Failed to set uninstall block", e)
        }

        val restrictions = arrayOf(
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_APPS_CONTROL,
            UserManager.DISALLOW_SAFE_BOOT
        )

        restrictions.forEach { restriction ->
            try {
                dpm.addUserRestriction(admin, restriction)
            } catch (e: Exception) {
                Log.w("FocusLockPolicy", "Failed to apply restriction: $restriction", e)
            }
        }

        try {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
        } catch (e: Exception) {
            Log.w("FocusLockPolicy", "Failed to clear factory reset restriction", e)
        }

        val prefs = getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Constants.KEY_ADB_DISABLED, false)) {
            try {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            } catch (e: Exception) {
                Log.w("FocusLockPolicy", "Failed to apply debugging restriction", e)
            }
        }

        KioskPolicy.applyDeviceOwnerKioskPolicies(this, dpm, admin)
    }

    private fun secureChromeSandbox() {
        if (!dpm.isDeviceOwnerApp(packageName)) return
        val restrictions = android.os.Bundle().apply {
            putStringArray("URLBlocklist", arrayOf("*"))
            putStringArray("URLAllowlist", AllowlistStore.getWebAllowlistDomains(this@MainActivity))
        }
        dpm.setApplicationRestrictions(admin, "com.android.chrome", restrictions)
    }

    // ── App grid ──────────────────────────────────────────────────

    private fun loadAppGrid() {
        val rv = findViewById<RecyclerView>(R.id.rvApps)
        rv.layoutManager = GridLayoutManager(this, 4)

        val items = AllowlistStore.getAppAllowlist(this)
            .filter { it != packageName }
            .mapNotNull { pkg ->
                try {
                    val info    = packageManager.getApplicationInfo(pkg, 0)
                    val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val hasIcon  = packageManager.getLaunchIntentForPackage(pkg) != null
                    if (isSystem && !hasIcon) return@mapNotNull null
                    AppItem(
                        packageName = pkg,
                        label       = packageManager.getApplicationLabel(info).toString(),
                        icon        = packageManager.getApplicationIcon(info)
                    )
                } catch (e: Exception) { null }
            }
            .sortedBy { it.label }

        rv.adapter = AppAdapter(items) { pkg ->
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
            if (launchIntent == null) {
                Toast.makeText(this, "Cannot open $pkg", Toast.LENGTH_SHORT).show()
                return@AppAdapter
            }

            try {
                startActivity(launchIntent)
            } catch (_: SecurityException) {
                Toast.makeText(this, "Blocked by kiosk policy: $pkg", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startKioskFromInputs() {
        if (LockManager.isKioskActive(this)) {
            Toast.makeText(this, "Kiosk is already active", Toast.LENGTH_SHORT).show()
            return
        }

        val days = findViewById<android.widget.EditText>(R.id.etKioskDays)
            .text?.toString()?.trim()?.toLongOrNull() ?: 0L
        val hours = findViewById<android.widget.EditText>(R.id.etKioskHours)
            .text?.toString()?.trim()?.toLongOrNull() ?: 0L

        val durationMs = (days * 24L + hours) * 60L * 60L * 1000L
        if (durationMs <= 0L) {
            Toast.makeText(this, "Enter a valid duration", Toast.LENGTH_SHORT).show()
            return
        }

        if (!SetupChecks.isSetupComplete(this)) {
            Toast.makeText(this, "Complete setup permissions before starting kiosk", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SetupPermissionsActivity::class.java))
            return
        }

        if (!runSecuritySweep()) {
            updateDashboardUI()
            return
        }

        LockManager.startKiosk(this, durationMs)
        AllowlistStore.setAppAllowlistLocked(this, true)
        AllowlistStore.setWebAllowlistLocked(this, true)

        markSecurityBaselineReady()
        secureChromeSandbox()

        if (dpm.isDeviceOwnerApp(packageName)) {
            applyDeviceOwnerPolicies()
        } else {
            maybeShowDeviceOwnerWarning()
        }

        KioskPolicy.syncLockTaskState(this)
        AppBlockerService.start(this)
        updateDashboardUI()
    }

    private fun disableAdbDebugging() {
        val prefs = getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        if (prefs.getBoolean(Constants.KEY_ADB_DISABLED, false)) {
            Toast.makeText(this, "ADB debugging already disabled", Toast.LENGTH_SHORT).show()
            updateAdbButtonState()
            return
        }

        if (!dpm.isDeviceOwnerApp(packageName)) {
            Toast.makeText(this, "Device Owner required to disable ADB", Toast.LENGTH_LONG).show()
            return
        }

        prefs.edit().putBoolean(Constants.KEY_ADB_DISABLED, true).apply()
        try {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
        } catch (e: Exception) {
            Log.w("FocusLockPolicy", "Failed to apply debugging restriction", e)
        }
        updateAdbButtonState()
    }

    private fun updateAdbButtonState() {
        val prefs = getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        val disabled = prefs.getBoolean(Constants.KEY_ADB_DISABLED, false)
        val button = findViewById<android.widget.Button>(R.id.btnDisableAdb)
        button.isEnabled = !disabled
        button.text = if (disabled) "ADB Debugging Disabled" else "Disable ADB Debugging"
    }

    private fun openQuickSetting(intent: Intent) {
        LockManager.allowSettingsUntil(this, System.currentTimeMillis() + 2 * 60 * 1000)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Setting not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyPersonalization() {
        val theme = UiPrefs.getTheme(this)
        val font = UiPrefs.getFont(this)
        val density = UiPrefs.getDensity(this)
        val wallpaper = UiPrefs.getWallpaper(this)
        val accent = UiPrefs.getAccent(this).color
        val background = UiPrefs.getBackground(this).color
        val textScale = UiPrefs.getTextScale(this)
        val effectiveTheme = if (background == android.graphics.Color.TRANSPARENT) {
            theme.copy(accent = accent)
        } else {
            theme.copy(accent = accent, background = background)
        }

        val root = findViewById<View>(R.id.mainRoot)
        UiStyler.applyWallpaperOrColor(root, effectiveTheme, wallpaper)
        UiStyler.applyTypefaceRecursive(root, font.typeface)

        applyTheme(effectiveTheme)
        applyDensity(density)
        applySectionVisibility()

        findViewById<View>(R.id.mainContent).apply {
            scaleX = textScale
            scaleY = textScale
        }
    }

    private fun applyTheme(theme: UiPrefs.UiTheme) {
        window.statusBarColor = theme.background
        window.navigationBarColor = theme.background

        val cardIds = listOf(
            R.id.cardDaysDone,
            R.id.cardProgress,
            R.id.cardUnlock,
            R.id.kioskCard,
            R.id.btnTextSearch,
            R.id.btnBrowse,
            R.id.btnPersonalize
        )
        cardIds.forEach { id ->
            UiStyler.applyCardBackground(findViewById(id), theme.card)
        }

        val radiusPx = UiStyler.dpToPx(this, UiPrefs.getCardRadiusDp(this)).toFloat()
        cardIds.forEach { id ->
            UiStyler.applyCardRadius(findViewById(id), radiusPx)
        }

        val surfaceIds = listOf(
            R.id.btnVideoLibrary,
            R.id.btnSetupPermissions,
            R.id.btnDeviceOwnerHelp,
            R.id.btnEditApps,
            R.id.btnEditWeb,
            R.id.btnSchedule,
            R.id.btnFactoryReset
        )
        surfaceIds.forEach { id ->
            findViewById<View>(id).setBackgroundColor(theme.card)
        }

        surfaceIds.forEach { id ->
            UiStyler.applyCardRadius(findViewById(id), radiusPx)
        }

        val quickButtons = listOf(
            R.id.btnWifi,
            R.id.btnHotspot,
            R.id.btnMobileData,
            R.id.btnBluetooth,
            R.id.btnSound
        )
        quickButtons.forEach { id ->
            val btn = findViewById<android.widget.Button>(id)
            btn.backgroundTintList = ColorStateList.valueOf(theme.card)
            btn.setTextColor(theme.textPrimary)
        }

        val btnStart = findViewById<android.widget.Button>(R.id.btnStartKiosk)
        btnStart.backgroundTintList = ColorStateList.valueOf(theme.accent)
        btnStart.setTextColor(theme.textPrimary)

        UiStyler.applyCardRadius(btnStart, radiusPx)

        val btnAdb = findViewById<android.widget.Button>(R.id.btnDisableAdb)
        btnAdb.backgroundTintList = ColorStateList.valueOf(theme.card)
        btnAdb.setTextColor(theme.textPrimary)
        UiStyler.applyCardRadius(btnAdb, radiusPx)

        findViewById<View>(R.id.dividerAfterVideo).setBackgroundColor(theme.divider)

        val primaryTextIds = listOf(
            R.id.tvDaysLeft,
            R.id.tvDaysDone,
            R.id.tvProgress,
            R.id.tvUnlockDate,
            R.id.tvVideoLibraryTitle,
            R.id.tvSetupPermissionsTitle,
            R.id.tvDeviceOwnerHelpTitle,
            R.id.tvEditAppsTitle,
            R.id.tvEditWebTitle,
            R.id.tvScheduleTitle,
            R.id.tvFactoryResetTitle,
            R.id.tvPersonalizeTitle
        )
        primaryTextIds.forEach { id ->
            findViewById<TextView>(id).setTextColor(theme.textPrimary)
        }


        updateTabStyles(theme)
        val secondaryTextIds = listOf(
            R.id.tvTitle,
            R.id.tvDaysLeftLabel,
            R.id.tvDaysDoneLabel,
            R.id.tvProgressLabel,
            R.id.tvUnlockLabel,
            R.id.tvKioskTitle,
            R.id.tvQuickSettingsTitle,
            R.id.tvAllowedAppsTitle,
            R.id.tvMediaSection,
            R.id.tvSearchSection,
            R.id.tvPersonalizeSection,
            R.id.tvSetupSection,
            R.id.tvManageSection
        )
        secondaryTextIds.forEach { id ->
            findViewById<TextView>(id).setTextColor(theme.textSecondary)
        }

        findViewById<TextView>(R.id.tvBrowseTitle).setTextColor(theme.accent)
        findViewById<TextView>(R.id.tvTextSearchTitle).setTextColor(theme.accent)
        findViewById<TextView>(R.id.tvBrowseIcon).setTextColor(theme.accent)
        findViewById<TextView>(R.id.tvTextSearchIcon).setTextColor(theme.accent)

        val etDays = findViewById<android.widget.EditText>(R.id.etKioskDays)
        val etHours = findViewById<android.widget.EditText>(R.id.etKioskHours)
        listOf(etDays, etHours).forEach { input ->
            input.setBackgroundColor(theme.input)
            input.setTextColor(theme.textPrimary)
            input.setHintTextColor(theme.textSecondary)
        }
    }

    private fun applyDensity(density: UiPrefs.UiDensity) {
        val padding = UiStyler.dpToPx(this, density.contentPaddingDp)
        findViewById<View>(R.id.mainContent).setPadding(padding, padding, padding, padding)

        setHeightDp(R.id.btnStartKiosk, density.buttonHeightDp)
        setHeightDp(R.id.btnDisableAdb, density.buttonHeightDp)
        setHeightDp(R.id.btnVideoLibrary, density.buttonHeightDp)
        setHeightDp(R.id.btnTextSearch, density.browseHeightDp)
        setHeightDp(R.id.btnBrowse, density.browseHeightDp)
        setHeightDp(R.id.btnPersonalize, density.buttonHeightDp)
        setHeightDp(R.id.btnSetupPermissions, density.buttonHeightDp)
        setHeightDp(R.id.btnDeviceOwnerHelp, density.buttonHeightDp)
        setHeightDp(R.id.btnEditApps, density.buttonHeightDp)
        setHeightDp(R.id.btnEditWeb, density.buttonHeightDp)
        setHeightDp(R.id.btnSchedule, density.buttonHeightDp)
        setHeightDp(R.id.btnFactoryReset, density.buttonHeightDp)
        setHeightDp(R.id.btnWifi, density.quickButtonHeightDp)
        setHeightDp(R.id.btnHotspot, density.quickButtonHeightDp)
        setHeightDp(R.id.btnMobileData, density.quickButtonHeightDp)
        setHeightDp(R.id.btnBluetooth, density.quickButtonHeightDp)
        setHeightDp(R.id.btnSound, density.quickButtonHeightDp)

        val kioskCard = findViewById<View>(R.id.kioskCard)
        val cardPadding = UiStyler.dpToPx(this, density.cardPaddingDp)
        kioskCard.setPadding(cardPadding, cardPadding, cardPadding, cardPadding)
    }

    private fun setHeightDp(viewId: Int, heightDp: Int) {
        val view = findViewById<View>(viewId)
        val params = view.layoutParams
        params.height = UiStyler.dpToPx(this, heightDp)
        view.layoutParams = params
    }

    private fun applySectionVisibility() {
        val showKiosk = UiPrefs.showKiosk(this)
        val showQuick = UiPrefs.showQuickSettings(this)
        val showAllowedApps = UiPrefs.showAllowedApps(this)
        val showWeb = UiPrefs.showWebButton(this)
        val showVideo = UiPrefs.showVideoButton(this)
        val showEdit = UiPrefs.showEditButtons(this)
        val showSchedule = UiPrefs.showSchedule(this)

        findViewById<View>(R.id.kioskCard).visibility = if (showKiosk) View.VISIBLE else View.GONE
        findViewById<View>(R.id.quickSettingsSection).visibility = if (showQuick) View.VISIBLE else View.GONE
        findViewById<View>(R.id.allowedAppsSection).visibility = if (showAllowedApps) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnBrowse).visibility = if (showWeb) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnTextSearch).visibility = if (showWeb) View.VISIBLE else View.GONE

        findViewById<View>(R.id.btnVideoLibrary).visibility = if (showVideo) View.VISIBLE else View.GONE
        findViewById<View>(R.id.dividerAfterVideo).visibility = if (showVideo) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tvMediaSection).visibility = if (showVideo) View.VISIBLE else View.GONE

        val editSection = findViewById<View>(R.id.editButtonsSection)
        editSection.visibility = if (showEdit) View.VISIBLE else View.GONE

        findViewById<View>(R.id.btnSetupPermissions).visibility = if (showEdit) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnDeviceOwnerHelp).visibility = if (showEdit) View.VISIBLE else View.GONE
        findViewById<View>(R.id.tvSetupSection).visibility = if (showEdit) View.VISIBLE else View.GONE

        findViewById<View>(R.id.btnFactoryReset).visibility = View.VISIBLE
        findViewById<View>(R.id.btnSchedule).visibility = if (showSchedule) View.VISIBLE else View.GONE

        applyTabVisibility()
    }

    private fun selectTab(tab: MainTab) {
        currentTab = tab
        applyTabVisibility()
        updateTabStyles(UiPrefs.getTheme(this).copy(
            accent = UiPrefs.getAccent(this).color,
            background = UiPrefs.getBackground(this).color.takeIf { it != android.graphics.Color.TRANSPARENT }
                ?: UiPrefs.getTheme(this).background
        ))
    }

    private fun applyTabVisibility() {
        findViewById<View>(R.id.sectionDashboard).visibility =
            if (currentTab == MainTab.DASHBOARD) View.VISIBLE else View.GONE
        findViewById<View>(R.id.sectionTasks).visibility =
            if (currentTab == MainTab.TASKS) View.VISIBLE else View.GONE
        findViewById<View>(R.id.sectionTools).visibility =
            if (currentTab == MainTab.TOOLS) View.VISIBLE else View.GONE
        findViewById<View>(R.id.sectionQuick).visibility =
            if (currentTab == MainTab.QUICK) View.VISIBLE else View.GONE
        findViewById<View>(R.id.sectionManage).visibility =
            if (currentTab == MainTab.MANAGE) View.VISIBLE else View.GONE
    }

    private fun updateTabStyles(theme: UiPrefs.UiTheme) {
        val tabs = listOf(
            Pair(R.id.tabDashboard, MainTab.DASHBOARD),
            Pair(R.id.tabTasks, MainTab.TASKS),
            Pair(R.id.tabTools, MainTab.TOOLS),
            Pair(R.id.tabQuick, MainTab.QUICK),
            Pair(R.id.tabManage, MainTab.MANAGE)
        )

        val radiusPx = UiStyler.dpToPx(this, UiPrefs.getCardRadiusDp(this)).toFloat()

        tabs.forEach { (id, tab) ->
            val button = findViewById<android.widget.Button>(id)
            val active = currentTab == tab
            button.backgroundTintList = ColorStateList.valueOf(if (active) theme.accent else theme.card)
            button.setTextColor(if (active) theme.textPrimary else theme.textSecondary)
            UiStyler.applyCardRadius(button, radiusPx)
        }
    }

    private fun openMobileDataSettings() {
        LockManager.allowSettingsUntil(this, System.currentTimeMillis() + 2 * 60 * 1000)
        val intents = listOf(
            Intent("android.settings.MOBILE_NETWORK_SETTINGS"),
            Intent("android.settings.NETWORK_OPERATOR_SETTINGS"),
            Intent(Settings.ACTION_DATA_USAGE_SETTINGS),
            Intent("miui.intent.action.NETWORK_SETTINGS"),
            Intent("miui.intent.action.SIM_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        if (!tryStartAny(intents)) {
            Toast.makeText(this, "Setting not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmFactoryReset() {
        val countdownSeconds = 5
        var remaining = countdownSeconds
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Factory reset")
            .setMessage(factoryResetMessage(remaining))
            .setPositiveButton("Reset now", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val button = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            button.isEnabled = false

            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val ticker = object : Runnable {
                override fun run() {
                    remaining -= 1
                    if (remaining <= 0) {
                        dialog.setMessage(factoryResetMessage(0))
                        button.isEnabled = true
                        return
                    }
                    dialog.setMessage(factoryResetMessage(remaining))
                    handler.postDelayed(this, 1_000)
                }
            }

            handler.postDelayed(ticker, 1_000)
            dialog.setOnDismissListener { handler.removeCallbacks(ticker) }

            button.setOnClickListener {
                handler.removeCallbacks(ticker)
                dialog.dismiss()
                performFactoryReset()
            }
        }

        dialog.show()
    }

    private fun factoryResetMessage(remainingSeconds: Int): String {
        return if (remainingSeconds <= 0) {
            "This will erase all data on this device."
        } else {
            "This will erase all data on this device. Confirming in ${remainingSeconds}s..."
        }
    }

    private fun performFactoryReset() {
        if (dpm.isDeviceOwnerApp(packageName)) {
            try {
                dpm.wipeData(0)
                return
            } catch (e: Exception) {
                Log.w("FocusLockPolicy", "Failed to wipe data via Device Owner", e)
            }
        } else {
            Toast.makeText(this, "Device Owner required for instant reset", Toast.LENGTH_LONG).show()
        }
        openFactoryResetSettings()
    }

    private fun openFactoryResetSettings() {
        LockManager.allowSettingsUntil(this, System.currentTimeMillis() + 5 * 60 * 1000)
        val intents = listOf(
            Intent("android.settings.FACTORY_RESET"),
            Intent("android.settings.MASTER_CLEAR"),
            Intent("android.settings.PRIVACY_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        if (!tryStartAny(intents)) {
            Toast.makeText(this, "Setting not available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun tryStartAny(intents: List<Intent>): Boolean {
        intents.forEach { intent ->
            try {
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    return true
                }
            } catch (_: Exception) {
                // Try next intent
            }
        }
        return false
    }

    private fun openAppAllowlistDialog() {
        if (AllowlistStore.isAppAllowlistLocked(this) || LockManager.isKioskActive(this)) {
            Toast.makeText(this, "Allowlist is locked while kiosk is active", Toast.LENGTH_SHORT).show()
            return
        }

        val launchable = packageManager.getInstalledApplications(0)
            .filter { packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .mapNotNull { info ->
                try {
                    val label = packageManager.getApplicationLabel(info).toString()
                    Pair(info.packageName, label)
                } catch (_: Exception) {
                    null
                }
            }
            .sortedBy { it.second.lowercase(Locale.getDefault()) }

        val current = AllowlistStore.getAppAllowlist(this)
        val labels = launchable.map { it.second }.toTypedArray()
        val checked = launchable.map { current.contains(it.first) }.toBooleanArray()

        android.app.AlertDialog.Builder(this)
            .setTitle("Allowed apps")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                val selected = launchable
                    .filterIndexed { index, _ -> checked[index] }
                    .map { it.first }
                AllowlistStore.setAppAllowlist(this, selected)
                loadAppGrid()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}