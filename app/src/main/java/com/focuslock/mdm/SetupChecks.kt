package com.focuslock.mdm

import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils

object SetupChecks {

    fun hasUsageAccess(context: Context): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 60_000,
            now
        )
        return !stats.isNullOrEmpty()
    }

    fun isNotificationAccessGranted(context: Context): Boolean {
        val cn = ComponentName(context, FocusNotificationService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return enabled?.contains(cn.flattenToString()) == true
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, AdminReceiver::class.java)
        return dpm.isAdminActive(admin)
    }

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(context.packageName)
    }

    fun isWhatsAppGuardEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!enabled) return false

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val serviceId = ComponentName(context, WhatsAppGuardService::class.java).flattenToString()
        return TextUtils.split(enabledServices, ":").any { it.equals(serviceId, ignoreCase = true) }
    }

    fun isSetupComplete(context: Context): Boolean {
        return hasUsageAccess(context) &&
            isNotificationAccessGranted(context) &&
            canDrawOverlays(context) &&
            isIgnoringBatteryOptimizations(context) &&
            isDeviceAdminActive(context) &&
            isDeviceOwner(context) &&
            isWhatsAppGuardEnabled(context)
    }
}
