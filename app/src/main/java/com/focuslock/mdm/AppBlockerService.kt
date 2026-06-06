package com.focuslock.mdm

import android.app.*
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import android.util.Log

class AppBlockerService : Service() {

    private data class ForegroundApp(
        val packageName: String,
        val className: String?
    )

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var blockerOverlay: View? = null
    private var blockerOverlayMessage: TextView? = null
    private var blockerWindowManager: WindowManager? = null
    private var lastBringToFrontAt = 0L
    private var lastPolicyEnforcementAt = 0L
    private var lastPermissionCheckAt = 0L
    private var activeScheduleId: String? = null
    private var lastWasScheduleEditor = false
    private var lastScheduleEnforcedAt = 0L
    private var lastTaskTickAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Lifecycle ─────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        dpm            = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)

        // Service can be started before MainActivity; seed install_time defensively.
        LockManager.initLock(this)

        startForeground(NOTIFICATION_ID, buildNotification())

        if (!LockManager.isKioskActive(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        enforceOwnerPolicies()
        suspendKillList()
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY   // OS will restart us if killed

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        hideBlockerOverlay()

        // Only resurrect if the kiosk is STILL supposed to be active.
        if (LockManager.isKioskActive(this) && !LockManager.hasKioskExpired(this)) {
            sendBroadcast(Intent(this, BootReceiver::class.java).apply {
                action = Intent.ACTION_BOOT_COMPLETED
            })
        }
    }

    // ── Kill-list suspension ──────────────────────────────────────

    private fun suspendKillList() {
        if (!dpm.isDeviceOwnerApp(packageName)) return

        val toSuspend = getManagedPackagesForSuspension()
            .filter { pkg -> isPackageInstalled(pkg) }
            .toTypedArray()

        if (toSuspend.isNotEmpty()) {
            try {
                dpm.setPackagesSuspended(adminComponent, toSuspend, true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isPackageInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) { false }

    // ── Foreground-app monitoring ─────────────────────────────────

    private fun startMonitoring() {
        scope.launch {
            Log.d("FocusLockDebug", "Service started. Monitoring active.")

            while (isActive) {
                val now = System.currentTimeMillis()

                if (!LockManager.isKioskActive(this@AppBlockerService) ||
                    LockManager.hasKioskExpired(this@AppBlockerService)
                ) {
                    Log.d("FocusLockDebug", "Kiosk expired or inactive. Ending session.")
                    endKioskAndTerminate()
                    break
                }

                val pendingTasks = TaskManager.getPendingTasks(this@AppBlockerService)
                val taskWindow = TaskManager.getActiveTaskWindow(this@AppBlockerService)
                val taskLockActive = pendingTasks.isNotEmpty()
                val activeWindow = if (taskLockActive) null else ScheduleManager.getActiveWindow(this@AppBlockerService)
                val scheduleActive = activeWindow != null
                val fg = getForegroundApp()
                val isScheduleEditor = fg?.packageName == packageName &&
                    (fg.className?.contains("ScheduleActivity") == true)

                if (scheduleActive) {
                    if (activeWindow?.id != activeScheduleId) {
                        activeScheduleId = activeWindow?.id
                    }

                    val shouldShowNothingness =
                        !isScheduleEditor &&
                        (lastWasScheduleEditor || now - lastScheduleEnforcedAt >= 5_000)

                    if (shouldShowNothingness) {
                        showNothingness(activeWindow)
                        lastScheduleEnforcedAt = now
                    }
                } else if (activeScheduleId != null) {
                    activeScheduleId = null
                    bringOurAppToFront()
                }

                lastWasScheduleEditor = isScheduleEditor

                if (now - lastPolicyEnforcementAt >= POLICY_REFRESH_MS) {
                    enforceOwnerPolicies()
                    suspendKillList()
                    lastPolicyEnforcementAt = now
                }

                if (isSecurityBaselineReady() && now - lastPermissionCheckAt >= PERMISSION_CHECK_MS) {
                    if (!Settings.canDrawOverlays(this@AppBlockerService)) {
                        Log.w("FocusLockDebug", "Overlay permission appears revoked. Continuing enforcement without overlay UI.")
                    }
                    lastPermissionCheckAt = now
                }

                // 2. --- ENFORCE THE LOCK ---
                if (fg != null) {
                    val timerTask = when {
                        taskWindow != null -> taskWindow.task
                        taskLockActive -> TaskManager.pickTimerTask(pendingTasks, fg.packageName)
                        else -> null
                    }

                    if (timerTask != null && timerTask.validationMode == TaskValidationMode.APP_TIMER) {
                        val isAllowedTaskApp = fg.packageName in timerTask.allowedApps
                        val delta = if (lastTaskTickAt == 0L) 0L else now - lastTaskTickAt
                        if (isAllowedTaskApp && delta > 0L) {
                            TaskManager.recordFocusTime(this@AppBlockerService, timerTask.id, delta)
                        }
                        lastTaskTickAt = now
                    } else if (taskLockActive) {
                        lastTaskTickAt = now
                    } else {
                        lastTaskTickAt = 0L
                    }

                    val isOverlaySurface = isOverlayPermissionSurface(fg)
                    val isUsbSurface = isUsbSettingsSurface(fg)
                    val whatsappRestriction = if (fg.packageName in Constants.WHATSAPP_PACKAGES) {
                        WhatsAppGuardState.getActiveRestriction()
                    } else {
                        null
                    }
                    val whatsappRestricted = whatsappRestriction != null
                    val taskAllowed = if (taskLockActive) {
                        isAllowedDuringTaskLock(pendingTasks, fg.packageName)
                    } else {
                        true
                    }
                    val allowed =
                        !isOverlaySurface &&
                        (isUsbSurface ||
                        !whatsappRestricted &&
                        taskAllowed &&
                        isAllowed(fg.packageName, scheduleActive))
                    val isSystemSurface = fg.packageName in Constants.SYSTEM_USAGE_SURFACES
                    Log.d(
                        "FocusLockDebug",
                        "Foreground: ${fg.packageName}/${fg.className ?: "?"} | Allowed: $allowed"
                    )

                    if (!allowed) {
                        if (whatsappRestricted) {
                            Log.d(
                                "FocusLockDebug",
                                "WhatsApp restricted surface: ${whatsappRestriction?.reason}"
                            )
                        }
                        Log.d(
                            "FocusLockDebug",
                            "BLOCKING APP: ${fg.packageName}/${fg.className ?: "?"} - Launching MainActivity!"
                        )
                        val isEscapeSurface =
                            whatsappRestricted ||
                            fg.packageName in Constants.KIOSK_ESCAPE_SURFACES ||
                            isOverlaySurface

                        if (!scheduleActive) {
                            if (!whatsappRestricted && Settings.canDrawOverlays(this@AppBlockerService)) {
                                showBlockerOverlay(fg.packageName)
                            } else {
                                hideBlockerOverlay()
                                if (!whatsappRestricted) {
                                    Log.w(
                                        "FocusLockDebug",
                                        "Blocking ${fg.packageName} without overlay permission."
                                    )
                                }
                            }
                        } else {
                            hideBlockerOverlay()
                        }

                        if (scheduleActive) {
                            showNothingness(activeWindow)
                        } else if (isSystemSurface) {
                            // Allow system UI panels without forcing a return to the app.
                            hideBlockerOverlay()
                        } else if (isEscapeSurface) {
                            // Escape surfaces should be countered instantly (launcher/home).
                            bringOurAppToFront()
                        } else {
                            bringOurAppToFrontThrottled()
                        }
                    } else {
                        hideBlockerOverlay()
                    }
                } else {
                    Log.d("FocusLockDebug", "Foreground app is null (Usage Access might be failing).")
                }

                delay(ENFORCEMENT_TICK_MS)
            }
        }
    }

    // ── The Vault Door Opener ─────────────────────────────────────
    @Suppress("DEPRECATION")
    private fun endKioskAndTerminate() {
        hideBlockerOverlay()

        if (dpm.isDeviceOwnerApp(packageName)) {
            val toUnsuspend = getManagedPackagesForUnlock()
                .filter { isPackageInstalled(it) }
                .toTypedArray()

            if (toUnsuspend.isNotEmpty()) {
                try {
                    dpm.setPackagesSuspended(adminComponent, toUnsuspend, false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            OWNER_RESTRICTIONS.forEach { restriction ->
                try {
                    dpm.clearUserRestriction(adminComponent, restriction)
                } catch (e: Exception) {
                    Log.w("FocusLockPolicy", "Failed to clear restriction: $restriction", e)
                }
            }

            try {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
            } catch (e: Exception) {
                Log.w("FocusLockPolicy", "Failed to clear debugging restriction", e)
            }

            try {
                dpm.setUninstallBlocked(adminComponent, packageName, false)
            } catch (e: Exception) {
                Log.w("FocusLockPolicy", "Failed to clear uninstall block", e)
            }

            KioskPolicy.clearDeviceOwnerKioskPolicies(this, dpm, adminComponent)

            // --- THE KILL SWITCH ---
            // Strip Device Owner rights when the kiosk timer ends.
            try {
                dpm.clearDeviceOwnerApp(packageName)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE).edit()
            .putBoolean(Constants.KEY_SECURITY_BASELINE_READY, false)
            .putBoolean(Constants.KEY_ADB_DISABLED, false)
            .putBoolean(Constants.KEY_APP_ALLOWLIST_LOCKED, false)
            .putBoolean(Constants.KEY_WEB_ALLOWLIST_LOCKED, false)
            .apply()

        LockManager.stopKiosk(this)

        // Shut down the guard dog gracefully
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Upgraded to use queryEvents instead of queryUsageStats.
     * This checks real-time kernel activity resumes, making it
     * impossible for an app to slip by undetected.
     */
    private fun getForegroundApp(): ForegroundApp? {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 15_000, now)

        val event = UsageEvents.Event()
        var currentApp: ForegroundApp? = null
        var lastEventTime = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForegroundEvent =
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND

            // Keep the latest foreground event in the queried window.
            if (isForegroundEvent && event.timeStamp >= lastEventTime) {
                lastEventTime = event.timeStamp
                val pkg = event.packageName
                if (!pkg.isNullOrBlank()) {
                    currentApp = ForegroundApp(pkg, event.className)
                }
            }
        }

        if (currentApp != null) return currentApp

        // Fallback path for OEMs where queryEvents can be sparse.
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 60_000,
            now
        )

        val fallbackPackage = stats
            ?.asSequence()
            ?.filter { !it.packageName.isNullOrBlank() }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName

        return if (fallbackPackage.isNullOrBlank()) null else ForegroundApp(fallbackPackage, null)
    }

    private fun isOverlayPermissionSurface(fg: ForegroundApp): Boolean {
        if (!LockManager.shouldEnforceKiosk(this)) return false
        if (LockManager.isSettingsAccessAllowed(this)) return false
        if (fg.packageName !in Constants.OVERLAY_PERMISSION_SURFACE_PACKAGES) return false

        val className = fg.className?.lowercase() ?: return false
        val normalized = className.replace("_", "").replace("-", "")
        return Constants.OVERLAY_PERMISSION_CLASS_KEYWORDS.any { keyword ->
            normalized.contains(keyword)
        }
    }

    private fun isUsbSettingsSurface(fg: ForegroundApp): Boolean {
        if (!LockManager.shouldEnforceKiosk(this)) return false
        if (fg.packageName !in Constants.USB_SETTINGS_PACKAGES) return false

        val className = fg.className?.lowercase() ?: return false
        val normalized = className.replace("_", "").replace("-", "")
        return Constants.USB_SETTINGS_CLASS_KEYWORDS.any { keyword ->
            normalized.contains(keyword)
        }
    }

    private fun isAllowedDuringTask(task: TaskItem, pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg in Constants.SYSTEM_USAGE_SURFACES) return true
        if (pkg in Constants.KIOSK_ESCAPE_SURFACES) return true
        if (pkg in Constants.SETTINGS_SHORTCUT_PACKAGES && LockManager.isSettingsAccessAllowed(this)) {
            return true
        }
        if (task.allowedApps.isEmpty()) {
            return false
        }
        return pkg in task.allowedApps
    }

    private fun isAllowedDuringTaskLock(tasks: List<TaskItem>, pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg in Constants.SYSTEM_USAGE_SURFACES) return true
        if (pkg in Constants.KIOSK_ESCAPE_SURFACES) return true
        if (pkg in Constants.SETTINGS_SHORTCUT_PACKAGES && LockManager.isSettingsAccessAllowed(this)) {
            return true
        }

        val allowedApps = TaskManager.getAllowedAppsForTasks(tasks)
        if (allowedApps.isEmpty()) return false
        return pkg in allowedApps
    }

    private fun isAllowed(pkg: String, scheduleActive: Boolean): Boolean {
        val kioskEnforced = LockManager.shouldEnforceKiosk(this)
        val baselineReady = LockManager.isSecurityBaselineReady(this)
        val allowlist = AllowlistStore.getAppAllowlist(this)

        // 1. Always allow our own app
        if (pkg == packageName) return true

        // 2. During schedule windows, allow only FocusLock.
        if (scheduleActive) return false

        // 3. Always allow core system surfaces for daily usage.
        if (pkg in Constants.SYSTEM_USAGE_SURFACES) return true
        if (pkg in Constants.KIOSK_ESCAPE_SURFACES) return true

        // 4. Before baseline completion, allow required onboarding settings routes.
        if (!baselineReady && pkg in Constants.ONBOARDING_SETTINGS_PACKAGES) {
            return true
        }

        // 5. Allow limited settings access when explicitly requested.
        if (LockManager.isSettingsAccessAllowed(this) && pkg in Constants.SETTINGS_SHORTCUT_PACKAGES) {
            return true
        }

        // 6. User-selected allowlist is always launchable.
        if (pkg in allowlist) return true

        // 7. Explicit block list for non-whitelisted apps.
        if (pkg in Constants.KILL_LIST && pkg !in allowlist) {
            return false
        }

        // 8. In true kiosk mode, everything outside whitelist is blocked.
        if (kioskEnforced) {
            return false
        }

        // 9. Outside kiosk enforcement, keep safe system fallback.
        return try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) { true }
    }

    private fun isSecurityBaselineReady(): Boolean {
        return LockManager.isSecurityBaselineReady(this)
    }

    private fun isAdbDisabled(): Boolean {
        val prefs = getSharedPreferences(Constants.PREFS_MAIN, Context.MODE_PRIVATE)
        return prefs.getBoolean(Constants.KEY_ADB_DISABLED, false)
    }

    private fun getManagedPackagesForSuspension(): List<String> {
        val allowlist = AllowlistStore.getAppAllowlist(this)
        return Constants.KILL_LIST
            .distinct()
            .filterNot { it in allowlist }
    }

    private fun getManagedPackagesForUnlock(): List<String> {
        val allowlist = AllowlistStore.getAppAllowlist(this)
        return Constants.KILL_LIST
            .distinct()
            .filterNot { it in allowlist }
    }

    private fun enforceOwnerPolicies() {
        if (!dpm.isDeviceOwnerApp(packageName)) return

        try {
            dpm.setUninstallBlocked(adminComponent, packageName, true)
        } catch (e: Exception) {
            Log.w("FocusLockPolicy", "Failed to set uninstall block", e)
        }

        OWNER_RESTRICTIONS.forEach { restriction ->
            try {
                dpm.addUserRestriction(adminComponent, restriction)
            } catch (e: Exception) {
                Log.w("FocusLockPolicy", "Failed to apply restriction: $restriction", e)
            }
        }

        try {
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
        } catch (e: Exception) {
            Log.w("FocusLockPolicy", "Failed to clear factory reset restriction", e)
        }

        if (isAdbDisabled()) {
            try {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
            } catch (e: Exception) {
                Log.w("FocusLockPolicy", "Failed to apply debugging restriction", e)
            }
        }

        KioskPolicy.applyDeviceOwnerKioskPolicies(this, dpm, adminComponent)
    }

    private fun bringOurAppToFrontThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastBringToFrontAt < 1_500) return
        lastBringToFrontAt = now
        bringOurAppToFront()
    }

    private fun bringOurAppToFront() {
        runOnMain {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }
    }

    private fun showNothingness(window: ScheduleWindow?) {
        if (window == null) return
        runOnMain {
            val intent = Intent(this, NothingnessActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(NothingnessActivity.EXTRA_MESSAGE, window.message)
            }
            startActivity(intent)
        }
    }

    private fun showBlockerOverlay(blockedPackage: String) {
        runOnMain {
            if (!Settings.canDrawOverlays(this)) return@runOnMain

            if (blockerOverlay != null) {
                blockerOverlayMessage?.text = "Blocked app: $blockedPackage"
                return@runOnMain
            }

            val wm = (getSystemService(WINDOW_SERVICE) as? WindowManager) ?: return@runOnMain
            blockerWindowManager = wm

            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.parseColor("#D9000000"))
                setPadding(48, 48, 48, 48)
            }

            val title = TextView(this).apply {
                text = "FocusLock"
                textSize = 26f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }

            val message = TextView(this).apply {
                text = "Blocked app: $blockedPackage"
                textSize = 16f
                setTextColor(Color.parseColor("#E6FFFFFF"))
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 24)
            }

            val button = Button(this).apply {
                text = "Return to FocusLock"
                setOnClickListener {
                    bringOurAppToFront()
                }
            }

            root.addView(title)
            root.addView(message)
            root.addView(button)

            val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            try {
                wm.addView(root, params)
                blockerOverlay = root
                blockerOverlayMessage = message
            } catch (_: Exception) {
                blockerOverlay = null
                blockerOverlayMessage = null
            }
        }
    }

    private fun hideBlockerOverlay() {
        runOnMain {
            val view = blockerOverlay ?: return@runOnMain
            val wm = blockerWindowManager ?: return@runOnMain
            try {
                wm.removeView(view)
            } catch (_: Exception) {
            } finally {
                blockerOverlay = null
                blockerOverlayMessage = null
                blockerWindowManager = null
            }
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    // ── Notification (required for foreground service) ────────────

    private fun buildNotification(): Notification {
        val channelId = "focuslock_guard"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "FocusLock Guard", NotificationManager.IMPORTANCE_MIN)
                    .also { it.setShowBadge(false) }
            )
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("FocusLock is active")
            .setContentText("Vault locked.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 9001
        private const val ENFORCEMENT_TICK_MS = 200L
        private const val POLICY_REFRESH_MS = 10_000L
        private const val PERMISSION_CHECK_MS = 2_500L
        private val OWNER_RESTRICTIONS = arrayOf(
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_APPS_CONTROL,
            UserManager.DISALLOW_SAFE_BOOT
        )

        fun start(context: Context) {
            if (!LockManager.isKioskActive(context)) return
            context.startForegroundService(Intent(context, AppBlockerService::class.java))
        }
    }
}