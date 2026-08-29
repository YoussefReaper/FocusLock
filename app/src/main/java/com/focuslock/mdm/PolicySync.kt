package com.focuslock.mdm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

/**
 * The bridge between "the user flipped a switch" and "the phone behaves differently".
 *
 * Every store calls [request] after a write. Two things then happen:
 *
 *  1. A revision counter ticks. [AppBlockerService] compares it every tick, so
 *     the in-process enforcement loop picks the change up within ~200ms with no
 *     restart, which is what makes the Capabilities screen feel live.
 *  2. Device-Owner policy (lock-task packages, suspension, restrictions, Chrome
 *     sandbox) is re-applied on a background thread, debounced, because those
 *     calls are the slow part and must never run on the UI thread.
 */
object PolicySync {

    private const val TAG = "FocusLockPolicy"
    private const val DEBOUNCE_MS = 250L

    @Volatile
    private var revision: Long = 0L

    @Volatile
    private var lastReason: String = ""

    private val worker: Handler by lazy {
        val thread = HandlerThread("focuslock-policy")
        thread.start()
        Handler(thread.looper)
    }

    private var pending: Runnable? = null

    fun revision(): Long = revision

    fun lastReason(): String = lastReason

    @Synchronized
    fun request(context: Context, reason: String) {
        revision += 1
        lastReason = reason
        val appContext = context.applicationContext

        pending?.let { worker.removeCallbacks(it) }
        val runnable = Runnable { applyNow(appContext) }
        pending = runnable
        worker.postDelayed(runnable, DEBOUNCE_MS)
    }

    /** Re-applies every Device-Owner policy that the current capability set asks for. */
    fun applyNow(context: Context) {
        val appContext = context.applicationContext
        val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return
        if (!SetupChecks.isDeviceOwner(appContext)) return
        val admin = ComponentName(appContext, AdminReceiver::class.java)

        try {
            KioskPolicy.applyDeviceOwnerKioskPolicies(appContext, dpm, admin)
        } catch (e: Exception) {
            Log.w(TAG, "Lock-task sync failed", e)
        }

        try {
            KioskPolicy.applyRestrictions(appContext, dpm, admin)
        } catch (e: Exception) {
            Log.w(TAG, "Restriction sync failed", e)
        }

        try {
            KioskPolicy.syncSuspendedApps(appContext, dpm, admin)
        } catch (e: Exception) {
            Log.w(TAG, "Suspension sync failed", e)
        }

        try {
            KioskPolicy.syncHiddenApps(appContext, dpm, admin)
        } catch (e: Exception) {
            Log.w(TAG, "Hidden-app sync failed", e)
        }

        try {
            KioskPolicy.syncBrowserSandbox(appContext, dpm, admin)
        } catch (e: Exception) {
            Log.w(TAG, "Browser sandbox sync failed", e)
        }
    }
}
