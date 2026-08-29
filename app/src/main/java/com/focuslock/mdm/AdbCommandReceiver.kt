package com.focuslock.mdm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * The computer-side control surface.
 *
 * Every command here is something the user can already do in the app; this is
 * for setting a phone up from a desk, or recovering one whose screen is
 * awkward to use. It is guarded by the DUMP permission, which only ADB and
 * system apps hold, so a normal app cannot send these.
 *
 * Deliberately absent: any command that turns a capability on. Provisioning a
 * phone should not be able to enable a guard the person never chose.
 */
class AdbCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        Migration.run(context)

        val command = intent.getStringExtra(EXTRA_COMMAND)?.trim()?.lowercase().orEmpty()
        val value = intent.getStringExtra(EXTRA_VALUE)?.trim().orEmpty()
        val confirm = intent.getBooleanExtra(EXTRA_CONFIRM, false)

        when (command) {
            "allow_app" -> withValue(value) { setPolicy(context, value, AppPolicy.ALLOW) }
            "block_app" -> withValue(value) { setPolicy(context, value, AppPolicy.BLOCK) }
            "pause_app" -> withValue(value) { setPolicy(context, value, AppPolicy.FRICTION) }
            "clear_app" -> withValue(value) {
                AppRules.clearPolicy(context, value)
                log("Cleared rule for " + value)
            }

            "always_allow" -> withValue(value) {
                AppRules.addAlwaysAllowed(context, value)
                log("Always-allowed " + value)
            }

            "add_website" -> withValue(value) {
                AllowlistStore.addWebUrl(context, value)
                log("Allowed site " + value)
            }
            "remove_website" -> withValue(value) {
                AllowlistStore.removeWebUrl(context, value)
                log("Removed site " + value)
            }

            "add_keyword" -> withValue(value) {
                KeywordRules.add(context, KeywordRules.newRule(phrase = value))
                log("Watching phrase " + value)
            }

            // Legacy names from the previous build, kept so old scripts still run.
            "add_app" -> withValue(value) { setPolicy(context, value, AppPolicy.ALLOW) }
            "remove_app" -> withValue(value) { setPolicy(context, value, AppPolicy.BLOCK) }

            "stop_session", "stop_kiosk" -> {
                if (!confirm) {
                    log("Refused: pass --ez " + EXTRA_CONFIRM + " true to end a session from a computer")
                } else {
                    endSession(context)
                }
            }

            "status" -> logStatus(context)
            "sync_policy" -> PolicySync.applyNow(context)
            "start_service" -> AppBlockerService.start(context)

            else -> log("Unknown command: " + command)
        }
    }

    private fun setPolicy(context: Context, packageName: String, policy: AppPolicy) {
        AppRules.setPolicy(context, packageName, policy)
        log(packageName + " -> " + policy.id)
    }

    /**
     * The recovery hatch. It ends the session and releases every managed app,
     * but it cannot silently do so: without the confirm flag it refuses, and it
     * always leaves a log line saying it happened.
     */
    private fun endSession(context: Context) {
        SessionManager.end(context)

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
            as? android.app.admin.DevicePolicyManager
        val admin = android.content.ComponentName(context, AdminReceiver::class.java)
        if (dpm != null && dpm.isDeviceOwnerApp(context.packageName)) {
            KioskPolicy.releaseAllManagedApps(context, dpm, admin)
            KioskPolicy.clearDeviceOwnerKioskPolicies(context, dpm, admin)
        }
        log("Session ended from a computer")
    }

    private fun logStatus(context: Context) {
        log("Session active: " + SessionManager.isActive(context))
        log("Mode: " + SessionManager.mode(context).label)
        log("Remaining: " + SessionManager.formatRemaining(context))
        log("Device owner: " + SetupChecks.isDeviceOwner(context))
        log("Blocked apps: " + AppRules.blockedPackages(context).size)
        log("Always allowed: " + AppRules.alwaysAllowedRaw(context).size)
        log("Keyword rules active: " + KeywordRules.activeRules(context).size)
        Capabilities.all.forEach { spec ->
            log("  " + spec.id + " = " + CapabilityRegistry.isEnabled(context, spec.id))
        }
    }

    private inline fun withValue(value: String, action: () -> Unit) {
        if (value.isBlank()) {
            log("Ignored: missing --es " + EXTRA_VALUE)
            return
        }
        action()
    }

    private fun log(message: String) {
        Log.i("FocusLockAdb", message)
    }

    companion object {
        const val ACTION = "com.focuslock.mdm.ADB_COMMAND"
        const val EXTRA_COMMAND = "cmd"
        const val EXTRA_VALUE = "value"
        const val EXTRA_CONFIRM = "confirm"
    }
}
