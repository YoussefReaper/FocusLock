package com.focuslock.mdm

import android.content.Context

/**
 * The freeze that makes a session mean something.
 *
 * A self-lock you can edit from inside is not a lock. The moment the urge
 * arrives is precisely the moment a person walks into settings and unblocks the
 * thing, and it feels perfectly reasonable while they are doing it. So while a
 * session runs, the rules stop moving: app policies, allowlists, schedules,
 * keywords, limits and the capability switches all hold still until it ends.
 *
 * Three deliberate holes, none of which is an escape route:
 *
 * - **Take a break** still works. It is a bounded, counted, already-agreed
 *   exception, and refusing it would push people into ending the whole session
 *   instead — the all-or-nothing relapse this app exists to avoid.
 * - **Ending the session** still works, when the person left themselves that
 *   door ([Capabilities.CAN_END_EARLY]). Freezing rules is not the same as
 *   trapping someone.
 * - **Migration and session cleanup** write through
 *   [CapabilityRegistry.writeEnabled], because neither is a person changing
 *   their mind mid-session.
 *
 * And the freeze itself is a capability. Someone who genuinely wants to steer
 * mid-session turns [Capabilities.LOCK_RULES_IN_SESSION] off *before* starting.
 * It cannot be turned off from inside a session, because a lock with the key
 * taped to it is just a sticker.
 */
object SessionLock {

    /** True when a running session is currently holding the rules still. */
    fun isFrozen(context: Context): Boolean = CapabilityRegistry.isFrozen(context)

    /**
     * The one line shown when something is refused.
     *
     * Says what is happening and when it lifts, never scolds, and never implies
     * the person did something wrong by trying.
     */
    fun refusalMessage(context: Context): String =
        Copy.rulesFrozen(context, SessionManager.formatRemaining(context))

    /**
     * Guards a mutation.
     *
     * Returns true when the caller may proceed. When it returns false it has
     * already told the user why, so callers just return.
     */
    fun allow(context: Context, onRefused: (String) -> Unit): Boolean {
        if (!isFrozen(context)) return true
        onRefused(refusalMessage(context))
        return false
    }
}
