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

    /**
     * Hands every suspended and hidden app straight back, ahead of any pending
     * debounce.
     *
     * Used by [SessionManager.end] and by the guard service's own startup path.
     * Leaving an app suspended is the one failure a person reads as "FocusLock
     * broke my phone", so this jumps the 250ms queue that everything else
     * waits in - the moment a session ends is exactly when the process is most
     * likely to be killed, and a release still sitting in a debounce when that
     * happens never runs at all.
     *
     * Posted to the worker rather than run inline, even though the caller is
     * usually in a hurry. `setPackagesSuspended` is a synchronous binder call
     * made once per package, and both callers are on the main thread - the End
     * button, and Service.onCreate - so a phone with thirty blocked apps would
     * be doing thirty round trips to system_server with the UI frozen behind
     * them. The remaining gap is covered: the guard service releases on startup
     * whenever it finds nothing to do, and MainActivity re-syncs on every
     * resume, both of which retry anything left behind.
     */
    @Synchronized
    fun releaseManagedAppsNow(context: Context) {
        val appContext = context.applicationContext
        // Cancel the pending debounce first: it was scheduled from the state
        // *before* the session ended, and letting it run after this would be
        // harmless but pointless duplicate work.
        pending?.let { worker.removeCallbacks(it) }
        pending = null
        worker.post {
            val dpm = appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                ?: return@post
            if (!SetupChecks.isDeviceOwner(appContext)) return@post
            try {
                KioskPolicy.releaseAllManagedApps(
                    appContext,
                    dpm,
                    ComponentName(appContext, AdminReceiver::class.java)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Immediate release of managed apps failed", e)
            }
        }
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
