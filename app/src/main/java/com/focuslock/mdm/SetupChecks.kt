package com.focuslock.mdm

import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils

/**
 * What Android has actually granted.
 *
 * Each check maps to one capability, so the app can say "this needs that
 * permission" rather than demanding every permission up front. Asking for
 * notification access from someone who never turned the shield on is how a
 * setup flow loses people.
 */
object SetupChecks {

    fun hasUsageAccess(context: Context): Boolean = try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        !usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now).isNullOrEmpty()
    } catch (_: Exception) {
        false
    }

    fun isNotificationAccessGranted(context: Context): Boolean {
        val component = ComponentName(context, FocusNotificationService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return enabled?.contains(component.flattenToString()) == true
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) {
        false
    }

    fun isDeviceAdminActive(context: Context): Boolean = try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.isAdminActive(ComponentName(context, AdminReceiver::class.java))
    } catch (_: Exception) {
        false
    }

    /**
     * Does this build hold, and use, Device-Owner powers?
     *
     * The demo build answers no unconditionally. Its manifest does not even
     * register [AdminReceiver], so it cannot be provisioned in the first place;
     * this flag is the second lock on that door, and it is what every apply
     * path in [KioskPolicy] and [PolicySync] asks before touching policy.
     *
     * Release paths deliberately do not consult this. They ask the framework
     * directly, so that a build flag can never leave a phone stranded with
     * policy applied and nothing willing to take it off.
     */
    fun isDeviceOwner(context: Context): Boolean {
        if (!BuildConfig.ENFORCEMENT) return false
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.isDeviceOwnerApp(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    /** The accessibility service behind every content guard. */
    fun isContentGuardEnabled(context: Context): Boolean {
        val enabled = try {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        } catch (_: Exception) {
            false
        }
        if (!enabled) return false

        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val serviceId = ComponentName(context, ContentGuardService::class.java).flattenToString()
        return TextUtils.split(services, ":").any { it.equals(serviceId, ignoreCase = true) }
    }

    fun hasLocationAccess(context: Context): Boolean = PlaceRules.hasLocationPermission(context)

    /**
     * Only the permissions the user's own capability choices actually require.
     * A capability the user left off never blocks anything.
     */
    fun missingForCurrentCapabilities(context: Context): List<CapabilitySpec> =
        Capabilities.all.filter { spec ->
            if (!CapabilityRegistry.isEnabled(context, spec.id)) return@filter false
            when {
                spec.needsUsageAccess && !hasUsageAccess(context) -> true
                spec.needsAccessibility && !isContentGuardEnabled(context) -> true
                spec.needsNotificationAccess && !isNotificationAccessGranted(context) -> true
                spec.needsDeviceOwner && !isDeviceOwner(context) -> true
                spec.needsLocation && !hasLocationAccess(context) -> true
                else -> false
            }
        }

    /** Everything a kiosk session needs before it can honestly claim to hold. */
    fun isKioskReady(context: Context): Boolean =
        hasUsageAccess(context) &&
            isIgnoringBatteryOptimizations(context) &&
            isDeviceAdminActive(context) &&
            isDeviceOwner(context)

    fun isSetupComplete(context: Context): Boolean =
        isKioskReady(context) && missingForCurrentCapabilities(context).isEmpty()
}
