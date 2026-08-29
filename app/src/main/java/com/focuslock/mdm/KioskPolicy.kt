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
        val toRelease = previous.filterNot { it in targets }

        if (toSuspend.isNotEmpty()) {
            setSuspended(dpm, admin, toSuspend, true)
        }
        if (toRelease.isNotEmpty()) {
            setSuspended(dpm, admin, toRelease.filter { isPackageInstalled(context, it) }, false)
        }
        FocusStore.setSet(context, KEY_SUSPENDED, targets)
    }

    private fun setSuspended(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        packages: Collection<String>,
        suspended: Boolean
    ) {
        if (packages.isEmpty()) return
        try {
            dpm.setPackagesSuspended(admin, packages.toTypedArray(), suspended)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to change suspension state", e)
        }
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

        targets.filterNot { it in previous }.forEach { setHidden(dpm, admin, it, true) }
        previous.filterNot { it in targets }.forEach { setHidden(dpm, admin, it, false) }
        FocusStore.setSet(context, KEY_HIDDEN, targets)
    }

    private fun setHidden(
        dpm: DevicePolicyManager,
        admin: ComponentName,
        packageName: String,
        hidden: Boolean
    ) {
        try {
            dpm.setApplicationHidden(admin, packageName, hidden)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to change hidden state for " + packageName, e)
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

        val suspended = FocusStore.getSet(context, KEY_SUSPENDED).filter { isPackageInstalled(context, it) }
        if (suspended.isNotEmpty()) setSuspended(dpm, admin, suspended, false)
        FocusStore.setSet(context, KEY_SUSPENDED, emptySet())

        FocusStore.getSet(context, KEY_HIDDEN)
            .filter { isPackageInstalled(context, it) }
            .forEach { setHidden(dpm, admin, it, false) }
        FocusStore.setSet(context, KEY_HIDDEN, emptySet())
    }

    // ── Activity-side lock task ───────────────────────────────────

    fun syncLockTaskState(activity: Activity) {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return
        val kioskEnforced = SessionManager.shouldLockTask(activity)
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
