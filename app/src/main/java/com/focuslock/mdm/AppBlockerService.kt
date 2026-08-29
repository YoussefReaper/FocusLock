package com.focuslock.mdm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The enforcement loop.
 *
 * It does exactly two things: work out what is in front, and ask [RuleEngine]
 * what should happen about it. Every judgement about allow-lists, schedules,
 * bedtime, limits and breaks now lives in the engine reading user-owned stores,
 * so this file no longer contains any policy of its own.
 *
 * It re-reads policy the moment [PolicySync] ticks, which is what lets a switch
 * flipped in the Capabilities screen take effect without restarting anything.
 */
class AppBlockerService : Service() {

    private data class ForegroundApp(val packageName: String, val className: String?)

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var blockerOverlay: View? = null
    private var blockerOverlayMessage: TextView? = null
    private var blockerWindowManager: WindowManager? = null

    private var lastInterceptAt = 0L
    private var lastInterceptPackage: String? = null
    private var lastBringToFrontAt = 0L
    private var lastPolicyRevision = -1L
    private var lastPolicyRefreshAt = 0L
    private var lastNotificationText = ""

    override fun onCreate() {
        super.onCreate()
        dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminReceiver::class.java)

        LockManager.initLock(this)
        Migration.run(this)

        startForeground(NOTIFICATION_ID, buildNotification(statusLine()))

        if (!hasWorkToDo()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        refreshPolicy(force = true)
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        hideBlockerOverlay()

        // Only come back if there is still something to enforce.
        if (hasWorkToDo() && !LockManager.hasKioskExpired(this)) {
            sendBroadcast(
                Intent(this, BootReceiver::class.java).apply {
                    action = Intent.ACTION_BOOT_COMPLETED
                }
            )
        }
    }

    // ── Should we be running at all ───────────────────────────────

    /**
     * Whether anything the user has configured could need a watcher today.
     *
     * This is deliberately broader than "is something blocking right now": a
     * schedule window that starts at 8pm needs the service alive at 7:59, and
     * an alarm to wake it would be both less reliable and less honest than
     * simply staying up. The cost is paid back by [isEnforcingNow].
     */
    private fun hasWorkToDo(): Boolean {
        if (SessionManager.isActive(this)) return true
        if (EarnSession.isActive(this)) return true
        if (EarnBudget.isSpending(this)) return true
        if (Bedtime.isEnabled(this)) return true
        if (AppLimits.hasEnforceableBudgets(this)) return true
        if (CapabilityRegistry.isEnabled(this, Capabilities.SCHEDULES) &&
            ScheduleManager.getSchedules(this).isNotEmpty()
        ) {
            return true
        }
        if (CapabilityRegistry.isEnabled(this, Capabilities.LOCATION_BLOCK) &&
            PlaceRules.all(this).isNotEmpty()
        ) {
            return true
        }
        return false
    }

    /**
     * Whether something is actually holding the phone back at this moment.
     *
     * The loop runs four times a second while enforcing and once every few
     * seconds while merely waiting for a window to open, which keeps the idle
     * case from costing a noticeable amount of battery.
     */
    private fun isEnforcingNow(): Boolean {
        if (SessionManager.isActive(this)) return true
        if (EarnSession.isActive(this)) return true
        if (EarnBudget.isSpending(this)) return true
        if (ScheduleManager.activeWindowIfEnabled(this) != null) return true
        if (Bedtime.isActive(this)) return true
        if (AppLimits.hasEnforceableBudgets(this)) return true
        if (PlaceRules.activePlaces(this).isNotEmpty()) return true
        return false
    }

    // ── Loop ──────────────────────────────────────────────────────

    private fun startMonitoring() {
        scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()

                if (SessionManager.isActive(this@AppBlockerService) &&
                    LockManager.hasKioskExpired(this@AppBlockerService)
                ) {
                    finishSession()
                    break
                }

                if (!hasWorkToDo()) {
                    standDown()
                    break
                }

                refreshPolicy(force = false)
                updateNotificationIfChanged()

                val enforcing = isEnforcingNow()
                if (enforcing) {
                    val foreground = getForegroundApp()
                    if (foreground != null) {
                        handleForeground(foreground, now)
                    } else {
                        hideBlockerOverlay()
                    }
                } else {
                    hideBlockerOverlay()
                }

                delay(if (enforcing) ENFORCEMENT_TICK_MS else IDLE_TICK_MS)
            }
        }
    }

    private fun handleForeground(foreground: ForegroundApp, now: Long) {
        val packageName = foreground.packageName

        if (packageName == this.packageName) {
            hideBlockerOverlay()
            return
        }

        // The demo build's whole promise is "look, don't touch": every rule,
        // schedule and task can be configured for real, but nothing is ever
        // allowed to act on another app. Device Owner is gated at its own call
        // sites in KioskPolicy/PolicySync, but this loop needs its own gate —
        // it intercepts and overlays using only UsageStatsManager and
        // SYSTEM_ALERT_WINDOW, neither of which needs Device Owner at all.
        if (!BuildConfig.ENFORCEMENT) {
            hideBlockerOverlay()
            return
        }

        // A live content-guard hit outranks the app's own policy: the app is
        // allowed, this particular screen inside it is not.
        val guardHit = GuardState.activeFor(packageName)
        if (guardHit != null && guardHit.action != GuardAction.NUDGE) {
            if (guardHit.action == GuardAction.CLOSE_APP) {
                bringToFront(throttled = true)
            }
            return
        }

        val decision = RuleEngine.decide(this, packageName, foreground.className)

        when (decision.outcome) {
            GuardOutcome.ALLOW -> hideBlockerOverlay()

            GuardOutcome.PAUSE -> {
                if (shouldIntercept(packageName, now)) {
                    lastInterceptAt = now
                    lastInterceptPackage = packageName
                    showIntercept(decision)
                }
            }

            GuardOutcome.BLOCK -> {
                if (SystemSurfaces.isLauncher(packageName)) {
                    // Launcher escapes are countered instantly: a throttle here
                    // is a visible half-second of the home screen.
                    hideBlockerOverlay()
                    bringToFront(throttled = false)
                    return
                }
                if (SystemSurfaces.isCritical(packageName)) {
                    hideBlockerOverlay()
                    return
                }
                if (shouldIntercept(packageName, now)) {
                    lastInterceptAt = now
                    lastInterceptPackage = packageName
                    showIntercept(decision)
                } else {
                    bringToFront(throttled = true)
                }
            }
        }
    }

    private fun shouldIntercept(packageName: String, now: Long): Boolean {
        if (packageName != lastInterceptPackage) return true
        return now - lastInterceptAt >= INTERCEPT_COOLDOWN_MS
    }

    // ── Policy ────────────────────────────────────────────────────

    private fun refreshPolicy(force: Boolean) {
        val revision = PolicySync.revision()
        val now = System.currentTimeMillis()
        val stale = now - lastPolicyRefreshAt >= POLICY_REFRESH_MS
        if (!force && revision == lastPolicyRevision && !stale) return

        lastPolicyRevision = revision
        lastPolicyRefreshAt = now
        AppLimits.invalidate()
        PolicySync.applyNow(this)
    }

    // ── Ending ────────────────────────────────────────────────────

    /** A session that reached its own end. */
    private fun finishSession() {
        hideBlockerOverlay()
        val releaseOwner = SessionManager.releasesOwnerOnEnd(this)

        if (dpm.isDeviceOwnerApp(packageName)) {
            KioskPolicy.releaseAllManagedApps(this, dpm, adminComponent)
            KioskPolicy.clearDeviceOwnerKioskPolicies(this, dpm, adminComponent)
        }

        SessionManager.end(this)

        FocusStore.setBool(this, Constants.KEY_APP_ALLOWLIST_LOCKED, false)
        FocusStore.setBool(this, Constants.KEY_WEB_ALLOWLIST_LOCKED, false)

        if (releaseOwner && dpm.isDeviceOwnerApp(packageName)) {
            // The one-shot sprint: hand the phone back completely. Only ever
            // reached when the user asked for it before starting.
            FocusStore.setBool(this, Constants.KEY_SECURITY_BASELINE_READY, false)
            FocusStore.setBool(this, Constants.KEY_ADB_DISABLED, false)
            try {
                dpm.clearUserRestriction(adminComponent, android.os.UserManager.DISALLOW_DEBUGGING_FEATURES)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear debugging restriction", e)
            }
            try {
                dpm.clearDeviceOwnerApp(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to release device owner", e)
            }
        }

        standDown()
    }

    /** Nothing left to watch: release everything and go away quietly. */
    private fun standDown() {
        hideBlockerOverlay()
        if (dpm.isDeviceOwnerApp(packageName) && !SessionManager.isActive(this)) {
            KioskPolicy.releaseAllManagedApps(this, dpm, adminComponent)
            KioskPolicy.applyRestrictions(this, dpm, adminComponent)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ── Foreground detection ──────────────────────────────────────

    private fun getForegroundApp(): ForegroundApp? {
        val usm = getSystemService(USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()

        try {
            val events = usm.queryEvents(now - 15_000, now)
            val event = UsageEvents.Event()
            var current: ForegroundApp? = null
            var lastEventTime = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForeground = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                if (isForeground && event.timeStamp >= lastEventTime) {
                    lastEventTime = event.timeStamp
                    val pkg = event.packageName
                    if (!pkg.isNullOrBlank()) current = ForegroundApp(pkg, event.className)
                }
            }
            if (current != null) return current

            // Some OEMs report events sparsely; fall back to the usage table.
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
            val fallback = stats
                ?.asSequence()
                ?.filter { !it.packageName.isNullOrBlank() }
                ?.maxByOrNull { it.lastTimeUsed }
                ?.packageName
            return if (fallback.isNullOrBlank()) null else ForegroundApp(fallback, null)
        } catch (e: Exception) {
            Log.w(TAG, "Usage query failed", e)
            return null
        }
    }

    // ── Surfacing the block ───────────────────────────────────────

    private fun showIntercept(decision: GuardDecision) {
        runOnMain {
            try {
                startActivity(
                    Intent(this, InterceptActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                        putExtra(InterceptActivity.EXTRA_PACKAGE, decision.packageName)
                        putExtra(InterceptActivity.EXTRA_HEADLINE, decision.headline)
                        putExtra(InterceptActivity.EXTRA_DETAIL, decision.detail)
                        putExtra(InterceptActivity.EXTRA_SOURCE, decision.source)
                        putExtra(InterceptActivity.EXTRA_PAUSE, decision.isPause)
                        putExtra(InterceptActivity.EXTRA_OFFERS_BREAK, decision.offersBreak)
                    }
                )
                hideBlockerOverlay()
            } catch (e: Exception) {
                Log.w(TAG, "Could not show intercept screen", e)
                showBlockerOverlay(decision)
            }
        }
    }

    private fun bringToFront(throttled: Boolean) {
        val now = System.currentTimeMillis()
        if (throttled && now - lastBringToFrontAt < BRING_TO_FRONT_THROTTLE_MS) return
        lastBringToFrontAt = now
        runOnMain {
            try {
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not return to FocusLock", e)
            }
        }
    }

    /**
     * The last-resort surface, used only when Android refuses the activity
     * start. It is intentionally plain: it exists so a block is never silent.
     */
    private fun showBlockerOverlay(decision: GuardDecision) {
        runOnMain {
            if (!Settings.canDrawOverlays(this)) return@runOnMain

            if (blockerOverlay != null) {
                blockerOverlayMessage?.text = decision.headline
                return@runOnMain
            }

            val wm = (getSystemService(WINDOW_SERVICE) as? WindowManager) ?: return@runOnMain
            blockerWindowManager = wm

            val theme = UiPrefs.resolve(this)
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(scrimColor(theme.background))
                setPadding(48, 48, 48, 48)
            }

            val title = TextView(this).apply {
                text = decision.headline.ifBlank { "FocusLock" }
                textSize = 24f
                setTextColor(theme.textPrimary)
                gravity = Gravity.CENTER
                typeface = theme.typeface
            }

            val message = TextView(this).apply {
                text = decision.detail
                textSize = 15f
                setTextColor(theme.textSecondary)
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 24)
                typeface = theme.typeface
            }

            val button = Button(this).apply {
                text = "Back to FocusLock"
                setTextColor(theme.onAccent)
                backgroundTintList = android.content.res.ColorStateList.valueOf(theme.accent)
                typeface = theme.typeface
                setOnClickListener { bringToFront(throttled = false) }
            }

            root.addView(title)
            root.addView(message)
            root.addView(button)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START }

            try {
                wm.addView(root, params)
                blockerOverlay = root
                blockerOverlayMessage = title
            } catch (_: Exception) {
                blockerOverlay = null
                blockerOverlayMessage = null
            }
        }
    }

    private fun scrimColor(base: Int): Int = Color.argb(
        235,
        Color.red(base),
        Color.green(base),
        Color.blue(base)
    )

    private fun hideBlockerOverlay() {
        runOnMain {
            val view = blockerOverlay ?: return@runOnMain
            val wm = blockerWindowManager ?: return@runOnMain
            try {
                wm.removeView(view)
            } catch (_: Exception) {
                // Already gone.
            } finally {
                blockerOverlay = null
                blockerOverlayMessage = null
                blockerWindowManager = null
            }
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    // ── Notification ──────────────────────────────────────────────

    private fun statusLine(): String {
        EarnSession.activeTask(this)?.let { task ->
            return "On " + task.title + " · " +
                SessionManager.formatDuration(EarnSession.elapsedMs(this))
        }
        if (EarnBudget.isSpending(this)) {
            return "Earned time, " +
                SessionManager.formatDuration(EarnBudget.remainingSpendMs(this)) + " left"
        }
        if (SessionManager.isActive(this)) {
            val mode = SessionManager.mode(this)
            return mode.label + " session, " + SessionManager.formatRemaining(this) + " left"
        }
        ScheduleManager.activeWindowIfEnabled(this)?.let { window ->
            return "Scheduled window until " + ScheduleManager.formatTime(window.endMinutes)
        }
        if (Bedtime.isActive(this)) return "Bedtime until " + Bedtime.formatTime(Bedtime.endMinutes(this))
        if (AppLimits.hasEnforceableBudgets(this)) return "Keeping an eye on your daily limits"
        ScheduleManager.nextWindow(this)?.let { window ->
            return "Next quiet window at " + ScheduleManager.formatTime(window.startMinutes)
        }
        if (Bedtime.isEnabled(this)) return "Bedtime starts at " + Bedtime.formatTime(Bedtime.startMinutes(this))
        return "Waiting for your next window"
    }

    private fun updateNotificationIfChanged() {
        val line = statusLine()
        if (line == lastNotificationText) return
        lastNotificationText = line
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, buildNotification(line))
        } catch (_: Exception) {
            // A notification we cannot refresh is not worth crashing over.
        }
    }

    private fun buildNotification(text: String): Notification {
        val channelId = "focuslock_guard"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "FocusLock", NotificationManager.IMPORTANCE_MIN)
                    .also { it.setShowBadge(false) }
            )
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("FocusLock")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    companion object {
        private const val TAG = "FocusLockGuard"
        private const val NOTIFICATION_ID = 9001
        private const val ENFORCEMENT_TICK_MS = 250L
        private const val IDLE_TICK_MS = 5_000L
        private const val POLICY_REFRESH_MS = 15_000L
        private const val INTERCEPT_COOLDOWN_MS = 2_500L
        private const val BRING_TO_FRONT_THROTTLE_MS = 1_500L

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, AppBlockerService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Could not start guard service", e)
            }
        }
    }
}
