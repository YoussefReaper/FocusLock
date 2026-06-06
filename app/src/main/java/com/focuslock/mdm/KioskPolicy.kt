package com.focuslock.mdm

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

object KioskPolicy {

    fun applyDeviceOwnerKioskPolicies(
        context: Context,
        dpm: DevicePolicyManager,
        admin: ComponentName
    ) {
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        val baselineReady = LockManager.isSecurityBaselineReady(context)
        val userAllowlist = AllowlistStore.getAppAllowlist(context)
        val lockTaskPackages = (Constants.lockTaskPackagesForBaseline(
            baselineReady = baselineReady,
            ownPackage = context.packageName
        ) + userAllowlist)
            .filter { pkg ->
                pkg == context.packageName || isPackageInstalled(context, pkg)
            }
            .toTypedArray()

        try {
            dpm.setLockTaskPackages(admin, lockTaskPackages)
        } catch (e: Exception) {
            Log.w("FocusLockPolicy", "Failed to set lock task packages", e)
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
            Log.w("FocusLockPolicy", "Failed to lock task features", e)
        }

        try {
            dpm.setStatusBarDisabled(admin, false)
        } catch (e: Exception) {
            Log.w("FocusLockPolicy", "Failed to enable status bar", e)
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
            Log.w("FocusLockPolicy", "Failed to set persistent home", e)
        }
    }

    private fun isPackageInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun clearDeviceOwnerKioskPolicies(
        context: Context,
        dpm: DevicePolicyManager,
        admin: ComponentName
    ) {
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        try {
            dpm.setStatusBarDisabled(admin, false)
        } catch (e: Exception) {
            Log.w("FocusLockPolicy", "Failed to re-enable status bar", e)
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
            Log.w("FocusLockPolicy", "Failed to reset lock task features", e)
        }

        try {
            dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
        } catch (e: Exception) {
            Log.w("FocusLockPolicy", "Failed to clear persistent home", e)
        }

        try {
            dpm.setLockTaskPackages(admin, emptyArray())
        } catch (e: Exception) {
            Log.w("FocusLockPolicy", "Failed to clear lock task packages", e)
        }
    }

    fun syncLockTaskState(activity: Activity) {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return
        val kioskEnforced = LockManager.shouldEnforceKiosk(activity)
        val activityManager = activity.getSystemService(ActivityManager::class.java)
        val isInLockTask = activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        val canManageLockTask =
            dpm.isDeviceOwnerApp(activity.packageName) &&
            dpm.isLockTaskPermitted(activity.packageName)

        if (isInLockTask && !canManageLockTask) {
            try {
                activity.stopLockTask()
            } catch (e: Exception) {
                Log.w("FocusLockPolicy", "Failed to exit non-permitted lock task", e)
            }
            return
        }

        if (kioskEnforced && !isInLockTask && canManageLockTask) {
            try {
                activity.startLockTask()
            } catch (e: Exception) {
                Log.w("FocusLockPolicy", "Failed to enter lock task", e)
            }
            return
        }

        if (!kioskEnforced && isInLockTask) {
            try {
                activity.stopLockTask()
            } catch (e: Exception) {
                Log.w("FocusLockPolicy", "Failed to exit lock task", e)
            }
        }
    }
}
