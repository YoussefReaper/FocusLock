package com.focuslock.mdm

import android.content.Context

/**
 * "Test the block" - a bounded, always-endable window that shows the real
 * intercept screen for whatever apps you actually open, computed by the real
 * [RuleEngine.decide] rather than a second copy of the same logic that could
 * drift out of sync with it.
 *
 * It is deliberately not a session. Nothing here ever touches
 * [SessionManager.isActive] (Test Mode passes its own `true` straight into
 * [RuleEngine.decide]'s `sessionActiveOverride` instead, at the one call
 * site in [AppBlockerService]), so lock-task pinning, app suspend/hide, the
 * rule freeze, and every one of those mechanisms' many other call sites stay
 * completely unaware a test is even happening - none of them can engage for
 * real no matter what a test decides to show. That is also why ending it is
 * never gated on [Capabilities.CAN_END_EARLY]: there is nothing here for
 * that gate to protect.
 */
object TestMode {

    private const val KEY_TEST_UNTIL_MS = "test_mode_until_ms"

    const val DEFAULT_MINUTES = 10
    private const val MAX_MINUTES = 60

    fun isActive(context: Context): Boolean = remainingMs(context) > 0L

    fun remainingMs(context: Context): Long {
        val until = FocusStore.getLong(context, KEY_TEST_UNTIL_MS, 0L)
        val remaining = until - System.currentTimeMillis()
        if (remaining <= 0L) {
            if (until != 0L) FocusStore.setLong(context, KEY_TEST_UNTIL_MS, 0L)
            return 0L
        }
        return remaining
    }

    /** Refused only when a real session is already running - decide() already reflects that for real. */
    fun canStart(context: Context): Boolean = !SessionManager.isActive(context)

    fun start(context: Context, minutes: Int = DEFAULT_MINUTES): Boolean {
        if (!canStart(context)) return false
        val bounded = minutes.coerceIn(1, MAX_MINUTES)
        FocusStore.setLong(context, KEY_TEST_UNTIL_MS, System.currentTimeMillis() + bounded * 60_000L)
        PolicySync.request(context, "testMode:start")
        return true
    }

    fun end(context: Context) {
        FocusStore.setLong(context, KEY_TEST_UNTIL_MS, 0L)
        PolicySync.request(context, "testMode:end")
    }

    /** What [RuleEngine.decide] should receive as `sessionActiveOverride` - null when there is nothing to override. */
    fun overrideFor(context: Context): Boolean? = if (isActive(context)) true else null

    fun formatRemaining(context: Context): String = SessionManager.formatDuration(remainingMs(context))
}
