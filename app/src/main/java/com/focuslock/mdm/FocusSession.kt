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

    /**
     * The switches this mode turns on, as a starting point.
     *
     * A mode is a template, not a cage. Picking one writes these flags; every
     * one of them is then editable in You -> Advanced, including Kiosk's
     * "no early exit". The preset is re-applied only when you pick a
     * *different* mode, so an edit you make afterwards survives the next start
     * of the same mode.
     *
     * Deliberately narrow: these three flags are what the mode is *about*.
     * It does not touch which modes are available, app rules, schedules or
     * anything else the person configured, because a template that quietly
     * rewrote unrelated settings would be a trap.
     */
    fun preset(): Map<String, Boolean> = when (this) {
        SOFT -> mapOf(
            Capabilities.CAN_END_EARLY to true,
            Capabilities.HARD_BLOCK to false,
            Capabilities.SUSPEND_BLOCKED_APPS to false,
            Capabilities.HIDE_BLOCKED_APPS to false
        )
        BLOCK -> mapOf(
            Capabilities.CAN_END_EARLY to true,
            Capabilities.HARD_BLOCK to true,
            Capabilities.SUSPEND_BLOCKED_APPS to true,
            Capabilities.HIDE_BLOCKED_APPS to false
        )
        SANCTUARY -> mapOf(
            Capabilities.CAN_END_EARLY to true,
            Capabilities.HARD_BLOCK to true,
            Capabilities.SUSPEND_BLOCKED_APPS to true,
            Capabilities.HIDE_BLOCKED_APPS to true
        )
        KIOSK -> mapOf(
            Capabilities.CAN_END_EARLY to false,
            Capabilities.HARD_BLOCK to true,
            Capabilities.SUSPEND_BLOCKED_APPS to true,
            Capabilities.HIDE_BLOCKED_APPS to true
        )
    }

    /** The preset in words, for the line under each mode card. */
    fun presetSummary(): String {
        val parts = ArrayList<String>()
        parts.add(if (preset()[Capabilities.HARD_BLOCK] == true) "stops blocked apps" else "nudges, never blocks")
        if (preset()[Capabilities.HIDE_BLOCKED_APPS] == true) parts.add("hides them from the launcher")
        if (preset()[Capabilities.SUSPEND_BLOCKED_APPS] == true) parts.add("silences them")
        parts.add(if (preset()[Capabilities.CAN_END_EARLY] == true) "you can end it early" else "no early exit")
        return "Sets: " + parts.joinToString(" · ")
    }

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
    private const val KEY_PRESET_APPLIED_FOR = "session_preset_applied_for"
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

    /**
     * Loads a mode's template, unless it is already loaded.
     *
     * The guard is the whole point. If this ran on every start it would undo
     * the person's own edits every time they pressed Start, which would make
     * You -> Advanced feel broken. Picking a *different* mode is the deliberate
     * act that says "give me that template again".
     *
     * Returns true if anything actually changed, so the caller can say so.
     */
    fun applyPresetIfModeChanged(context: Context, mode: FocusMode): Boolean {
        if (FocusStore.getString(context, KEY_PRESET_APPLIED_FOR, "") == mode.id) return false
        // Always unfrozen in practice: this runs from start(), before the
        // session is marked active. Checking the result anyway rather than
        // assuming it, so a future caller can't silently skip the guard.
        if (!CapabilityRegistry.applyPreset(context, mode.preset())) return false
        FocusStore.setString(context, KEY_PRESET_APPLIED_FOR, mode.id)
        return true
    }

    /** Whether starting this mode would load a new template. Reads only. */
    fun presetPending(context: Context, mode: FocusMode): Boolean =
        FocusStore.getString(context, KEY_PRESET_APPLIED_FOR, "") != mode.id

    /** Records a mode as the loaded template without touching any flag. */
    fun markPresetApplied(context: Context, mode: FocusMode) {
        FocusStore.setString(context, KEY_PRESET_APPLIED_FOR, mode.id)
    }

    /** Puts a mode's template back after the person has edited it. */
    fun resetToPreset(context: Context, mode: FocusMode): Boolean {
        if (!CapabilityRegistry.applyPreset(context, mode.preset())) return false
        FocusStore.setString(context, KEY_PRESET_APPLIED_FOR, mode.id)
        return true
    }

    /** Whether the live flags still match the mode's template. */
    fun matchesPreset(context: Context, mode: FocusMode): Boolean =
        mode.preset().all { CapabilityRegistry.isEnabled(context, it.key) == it.value }

    fun start(context: Context, mode: FocusMode, durationMs: Long) {
        applyPresetIfModeChanged(context, mode)
        FocusStore.setString(context, KEY_MODE, mode.id)
        LockManager.startKiosk(context, durationMs)
        Streaks.recordActivity(context)
        PolicySync.request(context, "session:start")
        AppBlockerService.start(context)
    }

    /**
     * Whether the running session offers an early exit.
     *
     * This used to be `mode != KIOSK`, hardcoded. It is a switch now. Kiosk's
     * preset still turns it off — that is what choosing Kiosk means — but the
     * person can turn it back on in You -> Advanced, and the help screen says
     * so. A lock you cannot inspect or undo is not a tool, it is a trap, and
     * the honest version of "hard mode" is one you had to deliberately keep.
     */
    fun canEndEarly(context: Context): Boolean =
        CapabilityRegistry.isEnabled(context, Capabilities.CAN_END_EARLY)

    fun end(context: Context) {
        val elapsed = elapsedMs(context)
        FocusStore.setLong(context, KEY_LAST_ENDED_AT, System.currentTimeMillis())
        FocusStore.setInt(context, KEY_TOTAL_SESSIONS, totalSessions(context) + 1)
        FocusStore.setLong(context, KEY_TOTAL_FOCUS_MS, totalFocusMs(context) + elapsed)
        // The one place every "a session just ended" path passes through,
        // called directly by the UI's own End button as well as by
        // AppBlockerService on natural expiry - so this is where a permission
        // emergency has to be released too. The session it was protecting is
        // over; holding the lock past that point would be punishing someone
        // for something that would have ended on its own regardless.
        PermissionGuard.clearEmergency(context)
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

    /**
     * Whether blocked apps come off the launcher.
     *
     * The mode no longer decides this; the flag does. Sanctuary and Kiosk set
     * it on by preset, but Block can have it too if that is what you want.
     */
    fun shouldHideApps(context: Context): Boolean =
        isActive(context) && CapabilityRegistry.isEnabled(context, Capabilities.HIDE_BLOCKED_APPS)

    fun shouldSuspendApps(context: Context): Boolean =
        isActive(context) && CapabilityRegistry.isEnabled(context, Capabilities.SUSPEND_BLOCKED_APPS)

    /**
     * Whether a blocked app is stopped or merely paused.
     *
     * Soft mode's preset turns this off, which is what makes Soft soft. It is
     * a flag rather than an enum check so any mode can be softened.
     */
    fun blocksOutright(context: Context): Boolean =
        isActive(context) && CapabilityRegistry.isEnabled(context, Capabilities.HARD_BLOCK)

    /**
     * Kiosk's allowlist inversion: only the apps you named stay open.
     *
     * This is part of the lock-task primitive, which is the one place the enum
     * legitimately still decides something. Kept separate from
     * [shouldLockTask] on purpose: that one is also true for a standalone Earn
     * task, and an Earn task must not silently inherit Kiosk's allowlist model.
     */
    fun usesAllowlistModel(context: Context): Boolean =
        isActive(context) &&
            mode(context) == FocusMode.KIOSK &&
            CapabilityRegistry.isEnabled(context, Capabilities.KIOSK_MODE)

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
