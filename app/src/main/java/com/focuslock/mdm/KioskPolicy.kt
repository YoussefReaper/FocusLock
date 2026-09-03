package com.focuslock.mdm

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.UserManager
import android.util.Log

/**
 * Everything FocusLock asks the Device-Owner API to do.
 *
 * Each call is gated on a capability the user can see and switch off in
 * Rules -> Capabilities. Nothing in here fires because the app decided it was a
 * good idea: if a restriction is applied, the person turned it on.
 */
object KioskPolicy {

    private const val TAG = "FocusLockPolicy"

    private val CHROME_PACKAGES = listOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser"
    )

    // ── Lock task ─────────────────────────────────────────────────

    /**
     * Which packages may run inside the kiosk shell.
     *
     * Pure so it can be reasoned about and tested without a device: the caller
     * supplies the user's lists and the answer is a function of them.
     */
    fun buildLockTaskPackages(
        ownPackage: String,
        userAllowed: Set<String>,
        alwaysAllowed: Set<String>,
        scheduleAllowed: Set<String>,
        baselineReady: Boolean,
        allowLauncherEscape: Boolean,
        earnAllowed: Set<String>? = null
    ): Set<String> {
        val allowed = LinkedHashSet<String>()
        allowed.add(ownPackage)

        if (earnAllowed != null) {
            // An Earn task narrows rather than widens. Whatever the standing
            // allowlist was, the live set is now only what the task needs, which
            // is the whole point: a task cannot open a door the running mode had
            // shut. The intersection with the standing allowlist happened in
            // EarnSession.allowedPackages before this was called.
            allowed.addAll(earnAllowed)
        } else {
            allowed.addAll(userAllowed)
            allowed.addAll(alwaysAllowed)
            allowed.addAll(scheduleAllowed)
        }

        allowed.addAll(SystemSurfaces.critical)

        // Before the permission baseline is complete the user still has to reach
        // Settings to grant things, so those routes stay open exactly until then.
        if (!baselineReady) {
            allowed.addAll(SystemSurfaces.settings)
        }

        if (allowLauncherEscape) {
            allowed.addAll(SystemSurfaces.launchers)
        }

        return allowed
    }

    fun applyDeviceOwnerKioskPolicies(
        context: Context,
        dpm: DevicePolicyManager,
        admin: ComponentName
    ) {
        if (!SetupChecks.isDeviceOwner(context)) return

        // A revoked permission the running session depends on wins over
        // every normal computation below - including whatever the user's own
        // allowlist says - until it is restored. Checked first and returns,
        // so nothing later in this function can ever re-widen the emergency
        // allowlist back out from underneath it on the next sync tick.
        if (PermissionGuard.isEmergency(context)) {
            applyPermissionEmergencyLock(context, dpm, admin)
            return
        }

        val kioskWanted = SessionManager.shouldLockTask(context)
        val persistentHome = kioskWanted &&
            CapabilityRegistry.isEnabled(context, Capabilities.PERSISTENT_HOME)

        val lockTaskPackages = buildLockTaskPackages(
            ownPackage = context.packageName,
            userAllowed = AppRules.kioskAllowlist(context),
            alwaysAllowed = AppRules.alwaysAllowed(context),
            scheduleAllowed = ScheduleManager.getAllScheduleAllowedApps(context),
            baselineReady = LockManager.isSecurityBaselineReady(context),
            allowLauncherEscape = !persistentHome,
            earnAllowed = earnNarrowing(context)
        ).filter { pkg ->
            pkg == context.packageName || isPackageInstalled(context, pkg)
        }.toTypedArray()

        try {
            dpm.setLockTaskPackages(admin, lockTaskPackages)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set lock task packages", e)
        }

        try {
            dpm.setLockTaskFeatures(admin, lockTaskFeatures(context))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set lock task features", e)
        }

        applyStatusBar(context, dpm, admin)
        applyPersistentHome(context, dpm, admin, persistentHome)
    }

    /**
     * The lock task allowlist while a required permission is missing:
     * FocusLock and the Settings family, nothing else. No always-allowed
     * apps, no schedule exceptions, no launcher escape - those all belong to
     * the normal running session, and the whole point here is that the
     * session's own rules do not get a vote on this one.
     */
    private fun applyPermissionEmergencyLock(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
        val packages = (setOf(context.packageName) + SystemSurfaces.settings)
            .filter { it == context.packageName || isPackageInstalled(context, it) }
            .toTypedArray()
        try {
            dpm.setLockTaskPackages(admin, packages)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set emergency lock task packages", e)
        }
        try {
            // Global actions keeps the power/volume keys and the emergency
            // dialler shortcut on the lock screen working; system info keeps
            // the clock. Nothing here offers an exit: no home, no overview,
            // no notifications to swipe into something else.
            dpm.setLockTaskFeatures(
                admin,
                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set emergency lock task features", e)
        }
        try {
            dpm.setStatusBarDisabled(admin, true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable status bar for emergency lock", e)
        }
    }

    /** Called from [PermissionRecoveryActivity] itself so it self-pins the moment it's on screen. */
    fun enterPermissionEmergency(activity: Activity) {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val admin = ComponentName(activity, AdminReceiver::class.java)
        if (dpm != null && SetupChecks.isDeviceOwner(activity)) {
            applyPermissionEmergencyLock(activity, dpm, admin)
        }
        syncLockTaskState(activity)
    }

    /** Releases the emergency pin immediately, rather than waiting on the normal debounce. */
    fun exitPermissionEmergency(activity: Activity) {
        PolicySync.applyNow(activity)
        val activityManager = activity.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (activityManager?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
            try {
                activity.stopLockTask()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to exit emergency lock task", e)
            }
        }
    }

    /**
     * The live narrowing an active Earn task imposes, or null when there is none.
     *
     * A merged task that names no apps returns null on purpose: the underlying
     * mode's allowlist then stands exactly as it was, which is what the user
     * asked for by leaving the field empty.
     */
    private fun earnNarrowing(context: Context): Set<String>? {
        val task = EarnSession.activeTask(context) ?: return null
        val standalone = EarnSession.isStandalone(context)
        if (!standalone && task.allowedApps.isEmpty()) return null
        return EarnSession.allowedPackages(context, task)
    }

    /**
     * The shade, overview and keyguard stay on unless the user asked for them
     * to go. Kiosk with a reachable shade is still a real kiosk; kiosk that
     * silently removed the clock and the notifications would be a surprise.
     */
    private fun lockTaskFeatures(context: Context): Int {
        var features = DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
            DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
            DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
            DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO

        if (!CapabilityRegistry.isEnabled(context, Capabilities.STATUS_BAR_LOCK)) {
            features = features or DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
        }
        if (!CapabilityRegistry.isEnabled(context, Capabilities.KEYGUARD_LOCK)) {
            features = features or DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
        }
        return features
    }

    private fun applyStatusBar(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
        val disable = SessionManager.shouldLockTask(context) &&
            CapabilityRegistry.isEnabled(context, Capabilities.STATUS_BAR_LOCK)
        try {
            dpm.setStatusBarDisabled(admin, disable)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set status bar state", e)
        }
    }

    private fun applyPersistentHome(
        context: Context,
        dpm: DevicePolicyManager,
        admin: ComponentName,
        enable: Boolean
    ) {
        if (!enable) {
            try {
                dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear persistent home", e)
            }
            return
        }

        val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        try {
            dpm.addPersistentPreferredActivity(
                admin,
                homeFilter,
                ComponentName(context, MainActivity::class.java)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set persistent home", e)
        }
    }

    // ── User restrictions ─────────────────────────────────────────

    fun applyRestrictions(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
        if (!SetupChecks.isDeviceOwner(context)) return

        val sessionActive = SessionManager.isActive(context)

        setRestriction(
            dpm, admin, UserManager.DISALLOW_SAFE_BOOT,
            sessionActive && CapabilityRegistry.isEnabled(context, Capabilities.SAFE_BOOT_BLOCK)
        )
        setRestriction(
            dpm, admin, UserManager.DISALLOW_UNINSTALL_APPS,
            sessionActive && CapabilityRegistry.isEnabled(context, Capabilities.UNINSTALL_PROTECTION)
        )
        setRestriction(
            dpm, admin, UserManager.DISALLOW_APPS_CONTROL,
            sessionActive && CapabilityRegistry.isEnabled(context, Capabilities.SUSPEND_BLOCKED_APPS)
        )

        // ADB stays exactly where the user's own button left it. Provisioning
        // never fires this, and neither does starting a session.
        setRestriction(
            dpm, admin, UserManager.DISALLOW_DEBUGGING_FEATURES,
            FocusStore.getBool(context, Constants.KEY_ADB_DISABLED, false)
        )

        // Factory reset is the ultimate exit and stays available by design.
        setRestriction(dpm, admin, UserManager.DISALLOW_FACTORY_RESET, false)

        try {
            dpm.setUninstallBlocked(
                admin,
                context.packageName,
                sessionActive && CapabilityRegistry.isEnabled(context, Capabilities.UNINSTALL_PROTECTION)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set uninstall block", e)
        }
    }

    private fun setRestriction(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        restriction: String,
        enabled: Boolean
    ) {
        try {
            if (enabled) dpm.addUserRestriction(admin, restriction)
            else dpm.clearUserRestriction(admin, restriction)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set restriction " + restriction, e)
        }
    }

    // ── Suspension and hiding ─────────────────────────────────────

    fun syncSuspendedApps(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
        if (!SetupChecks.isDeviceOwner(context)) return

        val shouldSuspend = SessionManager.shouldSuspendApps(context)
        val targets = if (shouldSuspend) blockedInstalled(context) else emptySet()
        val previous = FocusStore.getSet(context, KEY_SUSPENDED)

        val toSuspend = targets.filterNot { it in previous }
        val toRelease = previous.filterNot { it in targets }.filter { isPackageInstalled(context, it) }
        val unchanged = previous.intersect(targets)

        val newlySuspended = if (toSuspend.isNotEmpty()) setSuspended(dpm, admin, toSuspend, true) else emptySet()
        val actuallyReleased = if (toRelease.isNotEmpty()) setSuspended(dpm, admin, toRelease, false) else emptySet()

        // A package Android refused to release stays tracked so the next sync
        // (every app open triggers one via PolicySync.request("mainResume"))
        // tries again. Recording it as released here - which the old code did
        // unconditionally - is exactly what left a real user's app suspended
        // at the OS level forever while FocusLock believed it was clear: the
        // failure was real (setPackagesSuspended's own documented per-package
        // failure list), just silently discarded.
        val stillStuck = toRelease.toSet() - actuallyReleased
        if (stillStuck.isNotEmpty()) {
            Log.w(TAG, "Still suspended after a release attempt, will retry next sync: $stillStuck")
        }
        FocusStore.setSet(context, KEY_SUSPENDED, unchanged + newlySuspended + stillStuck)
    }

    /**
     * Returns the subset of [packages] Android actually confirmed changed.
     *
     * One package at a time, deliberately. A single batched call means one
     * package the OS won't touch (or a stale/uninstalled one that slipped
     * past the installed-check in a race) can throw for the whole array,
     * silently taking every other package in the batch down with it - a
     * second, independent way the earlier "not allowed by your organization"
     * bug could have happened even after the return-value fix above.
     */
    private fun setSuspended(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        packages: Collection<String>,
        suspended: Boolean
    ): Set<String> {
        if (packages.isEmpty()) return emptySet()
        val changed = LinkedHashSet<String>()
        for (pkg in packages) {
            try {
                val failed = dpm.setPackagesSuspended(admin, arrayOf(pkg), suspended)
                if (failed.isNullOrEmpty()) {
                    changed.add(pkg)
                } else {
                    Log.w(TAG, "Android refused to " + (if (suspended) "suspend " else "un-suspend ") + pkg)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to change suspension state for $pkg", e)
            }
        }
        return changed
    }

    fun syncHiddenApps(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
        if (!SetupChecks.isDeviceOwner(context)) return

        val shouldHide = SessionManager.shouldHideApps(context)
        val targets = if (shouldHide) {
            AppRules.hiddenPackages(context).filter { isPackageInstalled(context, it) }.toSet()
        } else {
            emptySet()
        }
        val previous = FocusStore.getSet(context, KEY_HIDDEN)

        val toHide = targets.filterNot { it in previous }
        val toShow = previous.filterNot { it in targets }
        val unchanged = previous.intersect(targets)

        val newlyHidden = toHide.filter { setHidden(dpm, admin, it, true) }.toSet()
        val actuallyShown = toShow.filter { setHidden(dpm, admin, it, false) }.toSet()

        // Same reasoning as syncSuspendedApps: a package the OS refused to
        // un-hide must stay tracked, not get quietly marked as done.
        val stillStuck = toShow.toSet() - actuallyShown
        if (stillStuck.isNotEmpty()) {
            Log.w(TAG, "Still hidden after a reveal attempt, will retry next sync: $stillStuck")
        }
        FocusStore.setSet(context, KEY_HIDDEN, unchanged + newlyHidden + stillStuck)
    }

    private fun setHidden(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        packageName: String,
        hidden: Boolean
    ): Boolean {
        return try {
            dpm.setApplicationHidden(admin, packageName, hidden)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to change hidden state for " + packageName, e)
            false
        }
    }

    /** Blocked apps that are actually on the phone, never including our own. */
    private fun blockedInstalled(context: Context): Set<String> =
        AppRules.blockedPackages(context)
            .filter { it != context.packageName && isPackageInstalled(context, it) }
            .toSet()

    // ── Browser sandbox ───────────────────────────────────────────

    /**
     * Holds every managed browser to the user's own allowlist.
     *
     * The safe browser inside FocusLock enforces the same list in-process; this
     * is the belt to that pair of braces, for the case where a stock browser is
     * still reachable.
     */
    fun syncBrowserSandbox(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
        if (!SetupChecks.isDeviceOwner(context)) return

        val enforce = CapabilityRegistry.isEnabled(context, Capabilities.WEB_BLOCK)
        val restrictions = Bundle()
        if (enforce) {
            restrictions.putStringArray("URLBlocklist", arrayOf("*"))
            restrictions.putStringArray("URLAllowlist", AllowlistStore.getWebAllowlistDomains(context))
        }

        CHROME_PACKAGES.forEach { pkg ->
            try {
                dpm.setApplicationRestrictions(admin, pkg, restrictions)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set browser restrictions for " + pkg, e)
            }
        }
    }

    // ── Teardown ──────────────────────────────────────────────────

    fun clearDeviceOwnerKioskPolicies(
        context: Context,
        dpm: DevicePolicyManager,
        admin: ComponentName
    ) {
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        try {
            dpm.setStatusBarDisabled(admin, false)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to re-enable status bar", e)
        }

        try {
            val features = DevicePolicyManager.LOCK_TASK_FEATURE_HOME or
                DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or
                DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or
                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or
                DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
            dpm.setLockTaskFeatures(admin, features)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reset lock task features", e)
        }

        try {
            dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear persistent home", e)
        }

        try {
            dpm.setLockTaskPackages(admin, emptyArray())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear lock task packages", e)
        }

        releaseAllManagedApps(context, dpm, admin)
    }

    /** Puts every suspended or hidden app back exactly as it was found. */
    fun releaseAllManagedApps(context: Context, dpm: DevicePolicyManager, admin: ComponentName) {
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        // Same rule as syncSuspendedApps/syncHiddenApps: don't mark a package
        // released just because we asked - only because Android confirmed it.
        // Unconditionally clearing to emptySet() here (the old behaviour) is
        // exactly how a package the OS refused to un-suspend got permanently
        // lost track of at session end, with no sync ever retrying it again.
        val suspended = FocusStore.getSet(context, KEY_SUSPENDED).filter { isPackageInstalled(context, it) }
        val stillSuspended = if (suspended.isNotEmpty()) {
            suspended.toSet() - setSuspended(dpm, admin, suspended, false)
        } else {
            emptySet()
        }
        FocusStore.setSet(context, KEY_SUSPENDED, stillSuspended)

        val hidden = FocusStore.getSet(context, KEY_HIDDEN).filter { isPackageInstalled(context, it) }
        val stillHidden = hidden.filterNot { setHidden(dpm, admin, it, false) }.toSet()
        FocusStore.setSet(context, KEY_HIDDEN, stillHidden)
    }

    // ── Activity-side lock task ───────────────────────────────────

    fun syncLockTaskState(activity: Activity) {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return
        val kioskEnforced = SessionManager.shouldLockTask(activity) || PermissionGuard.isEmergency(activity)
        val activityManager = activity.getSystemService(ActivityManager::class.java)
        val isInLockTask = activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        val canManageLockTask =
            SetupChecks.isDeviceOwner(activity) && dpm.isLockTaskPermitted(activity.packageName)

        if (isInLockTask && !canManageLockTask) {
            try {
                activity.stopLockTask()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to exit non-permitted lock task", e)
            }
            return
        }

        if (kioskEnforced && !isInLockTask && canManageLockTask) {
            try {
                activity.startLockTask()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enter lock task", e)
            }
            return
        }

        if (!kioskEnforced && isInLockTask) {
            try {
                activity.stopLockTask()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to exit lock task", e)
            }
        }
    }

    fun isPackageInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Exception) {
        false
    }

    private const val KEY_SUSPENDED = "policy_suspended_packages"
    private const val KEY_HIDDEN = "policy_hidden_packages"
}
