package com.focuslock.mdm

import android.content.Context

/**
 * The four strengths of lock, weakest first.
 *
 * Every mode says out loud how hard it is to leave, because a person choosing a
 * 90-day kiosk session deserves to know that before they tap, not after.
 */
enum class FocusMode(
    val id: String,
    val label: String,
    val oneLiner: String,
    val exitLine: String,
    val strength: Int,
    val capabilityId: String
) {
    SOFT(
        id = "soft",
        label = "Soft",
        oneLiner = "A pause and a nudge when you reach for something distracting.",
        exitLine = "Leave any time. Nothing is held shut.",
        strength = 1,
        capabilityId = Capabilities.ADVISORY_MODE
    ),
    BLOCK(
        id = "block",
        label = "Block",
        oneLiner = "Distracting apps stop opening. The rest of the phone works normally.",
        exitLine = "End the session from this screen whenever you want.",
        strength = 2,
        capabilityId = Capabilities.APP_BLOCK
    ),
    SANCTUARY(
        id = "sanctuary",
        label = "Sanctuary",
        oneLiner = "Distractions disappear from the launcher and go silent. Your home screen stays yours.",
        exitLine = "End the session here. Apps come back within a few seconds.",
        strength = 3,
        capabilityId = Capabilities.SANCTUARY_MODE
    ),
    KIOSK(
        id = "kiosk",
        label = "Kiosk",
        oneLiner = "FocusLock becomes the whole phone until the timer runs out.",
        exitLine = "Runs to the end. Only a factory reset stops it early, and that wipes the phone.",
        strength = 4,
        capabilityId = Capabilities.KIOSK_MODE
    );

    val isHard: Boolean get() = this == KIOSK

    companion object {
        fun fromId(id: String?): FocusMode = values().firstOrNull { it.id == id } ?: SOFT

        /** Modes the user has left switched on, weakest first. */
        fun available(context: Context): List<FocusMode> =
            values().filter { CapabilityRegistry.isEnabled(context, it.capabilityId) }
    }
}

/**
 * The running session.
 *
 * This is the generalisation of the old kiosk timer: same storage keys, so an
 * in-flight 90-day lock survives the upgrade, plus a mode and a few honest
 * options that used to be implicit.
 */
object SessionManager {

    private const val KEY_MODE = "session_mode"
    private const val KEY_RELEASE_OWNER_ON_END = "session_release_owner_on_end"
    private const val KEY_LAST_ENDED_AT = "session_last_ended_at"
    private const val KEY_TOTAL_SESSIONS = "session_total_count"
    private const val KEY_TOTAL_FOCUS_MS = "session_total_focus_ms"

    // ── State ─────────────────────────────────────────────────────

    fun isActive(context: Context): Boolean = LockManager.isKioskActive(context)

    fun mode(context: Context): FocusMode =
        FocusMode.fromId(FocusStore.getString(context, KEY_MODE, FocusMode.KIOSK.id))

    fun activeMode(context: Context): FocusMode? = if (isActive(context)) mode(context) else null

    fun startedAt(context: Context): Long =
        FocusStore.getLong(context, Constants.KEY_KIOSK_START_MS, 0L)

    fun durationMs(context: Context): Long = LockManager.getKioskDurationMs(context)

    fun remainingMs(context: Context): Long = LockManager.getKioskRemainingMs(context)

    fun elapsedMs(context: Context): Long {
        val duration = durationMs(context)
        if (duration <= 0L) return 0L
        return (duration - remainingMs(context)).coerceAtLeast(0L)
    }

    fun progressPercent(context: Context): Int {
        val duration = durationMs(context)
        if (duration <= 0L) return 0
        return ((elapsedMs(context).toFloat() / duration.toFloat()) * 100f).toInt().coerceIn(0, 100)
    }

    fun endsAt(context: Context): Long = startedAt(context) + durationMs(context)

    /**
     * Whether ending this session should also hand back Device-Owner rights.
     *
     * Off by default: sessions are meant to be run again next week. The original
     * one-shot 90-day sprint is still available by turning this on before start.
     */
    fun releasesOwnerOnEnd(context: Context): Boolean =
        FocusStore.getBool(context, KEY_RELEASE_OWNER_ON_END, false)

    fun setReleasesOwnerOnEnd(context: Context, value: Boolean) {
        FocusStore.setBool(context, KEY_RELEASE_OWNER_ON_END, value)
    }

    fun lastEndedAt(context: Context): Long = FocusStore.getLong(context, KEY_LAST_ENDED_AT, 0L)

    fun totalSessions(context: Context): Int = FocusStore.getInt(context, KEY_TOTAL_SESSIONS, 0)

    fun totalFocusMs(context: Context): Long = FocusStore.getLong(context, KEY_TOTAL_FOCUS_MS, 0L)

    // ── Transitions ───────────────────────────────────────────────

    fun start(context: Context, mode: FocusMode, durationMs: Long) {
        FocusStore.setString(context, KEY_MODE, mode.id)
        LockManager.startKiosk(context, durationMs)
        Streaks.recordActivity(context)
        PolicySync.request(context, "session:start")
        AppBlockerService.start(context)
    }

    /**
     * Ends a session that the user is allowed to end.
     *
     * Kiosk deliberately refuses: that is the whole contract of the mode, and
     * quietly allowing an exit would make every other mode meaningless too.
     */
    fun canEndEarly(context: Context): Boolean = mode(context) != FocusMode.KIOSK

    fun end(context: Context) {
        val elapsed = elapsedMs(context)
        FocusStore.setLong(context, KEY_LAST_ENDED_AT, System.currentTimeMillis())
        FocusStore.setInt(context, KEY_TOTAL_SESSIONS, totalSessions(context) + 1)
        FocusStore.setLong(context, KEY_TOTAL_FOCUS_MS, totalFocusMs(context) + elapsed)
        LockManager.stopKiosk(context)
        TakeABreak.clearAll(context)
        PolicySync.request(context, "session:end")
    }

    fun extend(context: Context, extraMs: Long) {
        val current = durationMs(context)
        FocusStore.setLong(context, Constants.KEY_KIOSK_DURATION_MS, current + extraMs)
        PolicySync.request(context, "session:extend")
    }

    // ── What the mode implies ─────────────────────────────────────

    /**
     * Kiosk pins FocusLock as the shell, and so does a standalone Earn task:
     * "the phone is only this until the work is done" is the same primitive
     * either way. A merged Earn task rides on whatever mode is already running
     * and does not turn lock-task on by itself.
     */
    fun shouldLockTask(context: Context): Boolean {
        if (isActive(context) &&
            mode(context) == FocusMode.KIOSK &&
            CapabilityRegistry.isEnabled(context, Capabilities.KIOSK_MODE)
        ) {
            return true
        }
        return EarnSession.requiresLockTask(context)
    }

    /** Sanctuary and Kiosk take apps off the launcher; Soft and Block do not. */
    fun shouldHideApps(context: Context): Boolean {
        if (!isActive(context)) return false
        val mode = mode(context)
        if (mode != FocusMode.SANCTUARY && mode != FocusMode.KIOSK) return false
        return CapabilityRegistry.isEnabled(context, Capabilities.HIDE_BLOCKED_APPS)
    }

    fun shouldSuspendApps(context: Context): Boolean {
        if (!isActive(context)) return false
        val mode = mode(context)
        if (mode == FocusMode.SOFT) return false
        return CapabilityRegistry.isEnabled(context, Capabilities.SUSPEND_BLOCKED_APPS)
    }

    /** Soft mode never hard-blocks: it pauses and lets you through. */
    fun blocksOutright(context: Context): Boolean =
        isActive(context) && mode(context) != FocusMode.SOFT

    fun formatRemaining(context: Context): String = formatDuration(remainingMs(context))

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0m"
        val totalMinutes = ms / 60_000L
        val days = totalMinutes / 1_440L
        val hours = (totalMinutes % 1_440L) / 60L
        val minutes = totalMinutes % 60L
        return when {
            days > 0L -> days.toString() + "d " + hours + "h"
            hours > 0L -> hours.toString() + "h " + minutes + "m"
            else -> minutes.toString() + "m"
        }
    }

    fun formatCountdown(ms: Long): String {
        if (ms <= 0L) return "0d 00h 00m 00s"
        val days = ms / 86_400_000L
        val hours = (ms % 86_400_000L) / 3_600_000L
        val minutes = (ms % 3_600_000L) / 60_000L
        val seconds = (ms % 60_000L) / 1_000L
        return String.format(
            java.util.Locale.getDefault(),
            "%dd %02dh %02dm %02ds",
            days, hours, minutes, seconds
        )
    }
}
