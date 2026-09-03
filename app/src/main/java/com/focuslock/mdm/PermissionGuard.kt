package com.focuslock.mdm

import android.content.Context

/**
 * Watches the handful of permissions the whole enforcement model depends on,
 * and reacts when one of them is taken away mid-session.
 *
 * This is not about a permission FocusLock was never given - a fresh install
 * with Usage Access still pending is a setup step, not tampering, and is left
 * entirely alone. It is specifically about a permission that WAS granted,
 * disappearing while a session is running and something still needs it. That
 * is the exact escape route this exists to close: "turn off Usage Access" or
 * "disable the accessibility service" is the first thing anyone searches for
 * when they want a blocker gone without touching one of its own switches.
 *
 * Detection is edge-triggered (granted -> missing), not level-triggered, so
 * this never fires on a permission the person genuinely hasn't set up yet -
 * only on one that just changed under a running session.
 */
object PermissionGuard {

    enum class Guarded(val label: String, val explanation: String) {
        USAGE_ACCESS(
            "Usage access",
            "Every rule depends on knowing which app is in front. Without this, FocusLock cannot see anything running."
        ),
        OVERLAY(
            "Display over other apps",
            "This is what draws the block screen on top of a blocked app."
        ),
        ACCESSIBILITY(
            "Content guard",
            "The keyword and chat guards read a screen's text through this. Without it they stop watching."
        ),
        DEVICE_OWNER(
            "Device owner",
            "The strongest part of the lock - suspension, hiding apps, kiosk - runs on this."
        )
    }

    private fun lastKnownKey(g: Guarded) = "permguard_last_" + g.name
    private const val KEY_WAS_OWNER = "permguard_was_owner"
    private const val KEY_EMERGENCY = "permguard_emergency"

    /** Whether losing [g] right now would actually weaken something switched on. */
    private fun isRelevant(context: Context, g: Guarded): Boolean = when (g) {
        Guarded.USAGE_ACCESS, Guarded.OVERLAY -> true
        Guarded.ACCESSIBILITY -> listOf(
            Capabilities.CONTENT_GUARD,
            Capabilities.KEYWORD_BLOCK,
            Capabilities.WHATSAPP_GUARD,
            Capabilities.SHORTS_BLOCK,
            Capabilities.REELS_BLOCK,
            Capabilities.ADULT_BLOCK,
            Capabilities.TELEGRAM_GUARD
        ).any { CapabilityRegistry.isEnabled(context, it) }
        // Once granted, always worth watching - device owner isn't tied to a
        // capability switch, and losing it outside a factory reset is
        // significant on its own.
        Guarded.DEVICE_OWNER -> SetupChecks.isDeviceOwner(context) || FocusStore.getBool(context, KEY_WAS_OWNER, false)
    }

    private fun isGranted(context: Context, g: Guarded): Boolean = when (g) {
        Guarded.USAGE_ACCESS -> SetupChecks.hasUsageAccess(context)
        Guarded.OVERLAY -> SetupChecks.canDrawOverlays(context)
        Guarded.ACCESSIBILITY -> SetupChecks.isContentGuardEnabled(context)
        Guarded.DEVICE_OWNER -> SetupChecks.isDeviceOwner(context)
    }

    /**
     * Call every tick while a session is active. Returns whichever guarded
     * permissions were seen granted a moment ago and are missing now - a real
     * revocation, not just "never set up".
     */
    fun checkForRevocation(context: Context): Set<Guarded> {
        val revoked = LinkedHashSet<Guarded>()
        Guarded.values().forEach { g ->
            if (!isRelevant(context, g)) {
                // Not relevant right now (e.g. the guard capability it backs is
                // off) - forget the baseline, so re-enabling later starts fresh
                // instead of firing on stale state.
                FocusStore.remove(context, lastKnownKey(g))
                return@forEach
            }
            val granted = isGranted(context, g)
            if (g == Guarded.DEVICE_OWNER && granted) {
                FocusStore.setBool(context, KEY_WAS_OWNER, true)
            }
            val known = FocusStore.getString(context, lastKnownKey(g), "")
            if (known == "granted" && !granted) revoked.add(g)
            FocusStore.setString(context, lastKnownKey(g), if (granted) "granted" else "missing")
        }
        return revoked
    }

    // ── Emergency state ──────────────────────────────────────────────
    //
    // Persisted rather than kept in memory: the emergency has to survive the
    // service being killed and restarted, which is exactly the kind of thing
    // that happens around the moment someone is fighting the lock.

    fun activeEmergency(context: Context): Set<Guarded> =
        FocusStore.getSet(context, KEY_EMERGENCY).mapNotNull { id ->
            Guarded.values().firstOrNull { it.name == id }
        }.toSet()

    fun isEmergency(context: Context): Boolean = activeEmergency(context).isNotEmpty()

    fun declareEmergency(context: Context, revoked: Set<Guarded>) {
        if (revoked.isEmpty()) return
        val current = activeEmergency(context)
        FocusStore.setSet(context, KEY_EMERGENCY, (current + revoked).map { it.name })
    }

    /**
     * Ends the emergency outright, regardless of whether the permission was
     * ever restored. Only called when the session it was protecting is
     * itself over - naturally expiring or being ended is not a bypass of
     * this lock, it is the same ending that would have happened anyway.
     */
    fun clearEmergency(context: Context) {
        FocusStore.remove(context, KEY_EMERGENCY)
    }

    /** Call every tick: drops any guarded permission that has been granted again. */
    fun clearResolved(context: Context) {
        val current = activeEmergency(context)
        if (current.isEmpty()) return
        val stillMissing = current.filter { !isGranted(context, it) }.toSet()
        if (stillMissing.size != current.size) {
            FocusStore.setSet(context, KEY_EMERGENCY, stillMissing.map { it.name })
        }
    }
}
