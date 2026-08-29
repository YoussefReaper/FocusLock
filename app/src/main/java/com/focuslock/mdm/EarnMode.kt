package com.focuslock.mdm

import android.content.Context
import org.json.JSONObject

/**
 * Earn Mode: finish real work, unlock the rest of the phone.
 *
 * ## Why this shape
 *
 * The category is real and it works — StepPhone locks apps until you walk,
 * Achieve! sells "Earn Your Screen Time", Habit Doom keeps homework apps shut
 * behind on-device photo proof, Focus Lock Pomodoro hands back "the scroll time
 * you earned". What none of them have is Device Owner, so all of them can be
 * force-stopped, rebooted around, or uninstalled. FocusLock's version is the
 * only one where the lock is an OS policy.
 *
 * ## Why it is built carefully rather than enthusiastically
 *
 * Deci, Koestner and Ryan (1999, Psychological Bulletin, 128 experiments) found
 * tangible rewards undermine intrinsic motivation, and the breakdown matters
 * more than the headline figure:
 *
 *   - engagement-contingent (paid for merely doing it)   d = -0.40
 *   - completion-contingent (paid for finishing)         d = -0.36
 *   - performance-contingent (paid for how it went)      d = -0.28
 *
 * and the damage lands hardest on tasks the person already finds interesting.
 * Three design consequences, all of them load-bearing:
 *
 *  1. The default rate is **performance-contingent**: the reward scales with
 *     verified minutes actually spent, not with ticking a box. Least harmful of
 *     the three, and the only one that cannot be farmed by making tiny tasks.
 *  2. A task can be marked *enjoyable*, and then it pays nothing. Attaching an
 *     allowance to something you already like is the exact case the research
 *     says to avoid.
 *  3. Every number here is the user's. Self-Determination Theory is clear that
 *     an autonomy-supportive reward behaves differently from a controlling one,
 *     and in a self-lock app there is nobody else to set the terms anyway.
 *
 * Earn Mode is off by default and is never proposed as the primary way to use
 * FocusLock. Soft, Block and Sanctuary remain the defaults.
 */
object EarnMode {

    const val PARAM_PHOTO_PROOF = "photoProof"
    const val PARAM_INTERSECT = "intersectWithAllowlist"
    const val PARAM_SHOW_BUDGET = "showBudgetWhileActive"
    const val PARAM_CREDIBILITY = "credibilityMeter"
    const val PARAM_DECAY = "rewardDecay"
    const val PARAM_RATE = "ratePercent"
    const val PARAM_DAILY_CAP = "dailyCapMinutes"

    /** 25 minutes of leisure per 100 minutes of verified focus. The user's to change. */
    const val DEFAULT_RATE_PERCENT = 25
    const val DEFAULT_DAILY_CAP = 120

    fun isEnabled(context: Context): Boolean =
        CapabilityRegistry.isEnabled(context, Capabilities.EARN_MODE)

    fun photoProofEnabled(context: Context): Boolean =
        isEnabled(context) &&
            CapabilityRegistry.getBoolParam(context, Capabilities.EARN_MODE, PARAM_PHOTO_PROOF, true)

    /**
     * The loophole rule.
     *
     * When on, a task's allowed apps must also be in the user's standing
     * allowlist, so a task cannot smuggle a distracting app past the mode that
     * is already running. On by default, because off is the loophole.
     */
    fun intersectsWithAllowlist(context: Context): Boolean =
        CapabilityRegistry.getBoolParam(context, Capabilities.EARN_MODE, PARAM_INTERSECT, true)

    fun showsBudgetWhileActive(context: Context): Boolean =
        CapabilityRegistry.getBoolParam(context, Capabilities.EARN_MODE, PARAM_SHOW_BUDGET, true)

    fun showsCredibility(context: Context): Boolean =
        CapabilityRegistry.getBoolParam(context, Capabilities.EARN_MODE, PARAM_CREDIBILITY, true)

    /** Unspent minutes fading overnight. Off by default: hoarding is not a problem worth solving. */
    fun decaysUnspent(context: Context): Boolean =
        CapabilityRegistry.getBoolParam(context, Capabilities.EARN_MODE, PARAM_DECAY, false)

    fun ratePercent(context: Context): Int =
        CapabilityRegistry.getIntParam(context, Capabilities.EARN_MODE, PARAM_RATE, DEFAULT_RATE_PERCENT)
            .coerceIn(0, 200)

    fun setRatePercent(context: Context, value: Int) {
        CapabilityRegistry.setIntParam(context, Capabilities.EARN_MODE, PARAM_RATE, value.coerceIn(0, 200))
    }

    fun dailyCapMinutes(context: Context): Int =
        CapabilityRegistry.getIntParam(context, Capabilities.EARN_MODE, PARAM_DAILY_CAP, DEFAULT_DAILY_CAP)
            .coerceIn(0, 720)

    fun setDailyCapMinutes(context: Context, value: Int) {
        CapabilityRegistry.setIntParam(
            context,
            Capabilities.EARN_MODE,
            PARAM_DAILY_CAP,
            value.coerceIn(0, 720)
        )
    }

    /**
     * Device Owner buys the hard version. Without it Earn still runs, but as
     * friction rather than an OS-level lock, and the UI says so rather than
     * quietly pretending.
     */
    fun hasHardEnforcement(context: Context): Boolean = SetupChecks.isDeviceOwner(context)

    /** What a finished task is worth, before the daily cap. */
    fun rewardFor(context: Context, task: FocusTask, focusedMinutes: Int): Int {
        if (task.enjoyable) return 0
        task.rewardMin?.let { return it }
        val rate = ratePercent(context)
        if (rate <= 0) return 0
        return (focusedMinutes * rate) / 100
    }

    fun describeDeal(context: Context): String {
        val rate = ratePercent(context)
        val cap = dailyCapMinutes(context)
        val base = if (rate <= 0) {
            "Tasks pay only what you set on them individually."
        } else {
            "Every hour of verified focus earns " + (rate * 60 / 100) + " minutes back."
        }
        return if (cap <= 0) base else base + " Up to " + cap + " minutes a day."
    }
}

/**
 * The running Earn session: one task, one clock.
 *
 * Standalone means Earn is the lock. Merged means another mode is already
 * running and Earn narrows it further — the distinction changes which allowlist
 * the task intersects with, and nothing else.
 */
object EarnSession {

    private const val KEY_TASK_ID = "earn_task_id"
    private const val KEY_STARTED_AT = "earn_started_at"
    private const val KEY_STANDALONE = "earn_standalone"
    private const val KEY_PHOTO_ATTEMPTS = "earn_photo_attempts"

    fun activeTaskId(context: Context): String? =
        FocusStore.getString(context, KEY_TASK_ID, "").takeIf { it.isNotBlank() }

    fun activeTask(context: Context): FocusTask? {
        if (!EarnMode.isEnabled(context)) return null
        val id = activeTaskId(context) ?: return null
        val task = FocusTaskStore.find(context, id)
        if (task == null || task.completed) {
            stop(context)
            return null
        }
        return task
    }

    fun isActive(context: Context): Boolean = activeTask(context) != null

    fun isStandalone(context: Context): Boolean = FocusStore.getBool(context, KEY_STANDALONE, true)

    fun startedAt(context: Context): Long = FocusStore.getLong(context, KEY_STARTED_AT, 0L)

    fun elapsedMs(context: Context): Long {
        val started = startedAt(context)
        if (started <= 0L) return 0L
        return (System.currentTimeMillis() - started).coerceAtLeast(0L)
    }

    fun elapsedMinutes(context: Context): Int = (elapsedMs(context) / 60_000L).toInt()

    /** For TIMER verification: the goal, and how far along it is. */
    fun timerTargetMinutes(context: Context, task: FocusTask): Int =
        (task.timeEstimateMin ?: 25).coerceAtLeast(1)

    fun timerSatisfied(context: Context, task: FocusTask): Boolean =
        elapsedMinutes(context) >= timerTargetMinutes(context, task)

    fun start(context: Context, task: FocusTask, standalone: Boolean) {
        FocusStore.setString(context, KEY_TASK_ID, task.id)
        FocusStore.setLong(context, KEY_STARTED_AT, System.currentTimeMillis())
        FocusStore.setBool(context, KEY_STANDALONE, standalone)
        FocusStore.setInt(context, KEY_PHOTO_ATTEMPTS, 0)
        PolicySync.request(context, "earn:start")
        AppBlockerService.start(context)
    }

    fun stop(context: Context) {
        FocusStore.setString(context, KEY_TASK_ID, "")
        FocusStore.setLong(context, KEY_STARTED_AT, 0L)
        FocusStore.setInt(context, KEY_PHOTO_ATTEMPTS, 0)
        PolicySync.request(context, "earn:stop")
    }

    /**
     * A standalone Earn task is its own kiosk. A merged one is not: it narrows
     * whatever mode is already running rather than starting a new lock.
     *
     * Without Device Owner there is no lock-task to enter, so Earn falls back to
     * the soft variant — the enforcement loop and Intercept still hold the line,
     * just at friction strength rather than OS strength.
     */
    fun requiresLockTask(context: Context): Boolean =
        isActive(context) && isStandalone(context) && EarnMode.hasHardEnforcement(context)

    fun photoAttempts(context: Context): Int = FocusStore.getInt(context, KEY_PHOTO_ATTEMPTS, 0)

    fun recordPhotoAttempt(context: Context) {
        FocusStore.setInt(context, KEY_PHOTO_ATTEMPTS, photoAttempts(context) + 1)
    }

    /**
     * The apps that may run while this task is active.
     *
     * Standalone: FocusLock plus whatever the task names. An empty task list
     * means FocusLock alone — the pure "do the thing" state.
     *
     * Merged: the intersection with the user's standing allowlist, so a task
     * cannot widen a mode that is already running.
     *
     * Always-allowed apps are unioned back in either way. That is a deliberate
     * departure from a strict intersection: a task must never be able to lock
     * someone out of their own phone calls, and an escape route that requires
     * dialling 999 to use is not an escape route.
     */
    fun allowedPackages(
        context: Context,
        task: FocusTask,
        standalone: Boolean = isStandalone(context)
    ): Set<String> {
        val out = LinkedHashSet<String>()
        out.add(context.packageName)
        out.addAll(AppRules.alwaysAllowed(context))

        val requested = task.allowedApps
        if (requested.isEmpty()) {
            // Nothing named: standalone means FocusLock only, merged leaves the
            // underlying mode's allowlist untouched (handled by the caller).
            return out
        }

        if (standalone || !EarnMode.intersectsWithAllowlist(context)) {
            out.addAll(requested)
        } else {
            val global = AppRules.kioskAllowlist(context)
            out.addAll(requested.filter { it in global })
        }

        // Photo proof needs a camera, and a camera the lock task forbids is a
        // task that cannot be finished.
        if (task.verification == Verification.PHOTO && EarnMode.photoProofEnabled(context)) {
            PhotoProof.cameraPackage(context)?.let { out.add(it) }
        }
        return out
    }

    /**
     * Apps the task asked for that the standing allowlist refuses. Shown, never
     * hidden: a task that quietly loses half its apps is a task that looks
     * broken rather than one that looks governed.
     *
     * [standalone] defaults to the running session's flag, but callers
     * previewing a task before it starts pass what the session *would* be.
     */
    fun rejectedPackages(
        context: Context,
        task: FocusTask,
        standalone: Boolean = isStandalone(context)
    ): Set<String> {
        if (standalone || !EarnMode.intersectsWithAllowlist(context)) return emptySet()
        val global = AppRules.kioskAllowlist(context)
        return task.allowedApps.filterNot { it in global }.toSet()
    }
}

/**
 * The earned leisure balance.
 *
 * Minutes are earned by finishing verified work and spent in a deliberate,
 * countdown-limited window. Spending is an explicit act rather than an ambient
 * state, because "you have 40 minutes banked" is only motivating if using them
 * is a choice you make on purpose.
 */
object EarnBudget {

    private const val KEY_BALANCE = "earn_balance_minutes"
    private const val KEY_SPEND_UNTIL = "earn_spend_until_ms"
    private const val KEY_EARNED_TODAY = "earn_earned_today_json"
    private const val KEY_TOTAL_EARNED = "earn_total_earned_minutes"
    private const val KEY_TOTAL_SPENT = "earn_total_spent_minutes"
    private const val KEY_LAST_DECAY_DAY = "earn_last_decay_day"

    fun balanceMinutes(context: Context): Int {
        applyDecayIfDue(context)
        return FocusStore.getInt(context, KEY_BALANCE, 0).coerceAtLeast(0)
    }

    fun earnedToday(context: Context): Int {
        val record = FocusStore.getJsonObject(context, KEY_EARNED_TODAY)
        if (record.optString("day", "") != todayKey()) return 0
        return record.optInt("minutes", 0)
    }

    fun totalEarned(context: Context): Int = FocusStore.getInt(context, KEY_TOTAL_EARNED, 0)

    fun totalSpent(context: Context): Int = FocusStore.getInt(context, KEY_TOTAL_SPENT, 0)

    /**
     * Credits a reward, honouring the user's own daily cap.
     * Returns what was actually credited, which may be less than asked.
     */
    fun credit(context: Context, minutes: Int): Int {
        if (minutes <= 0) return 0

        val cap = EarnMode.dailyCapMinutes(context)
        val already = earnedToday(context)
        val granted = if (cap <= 0) minutes else minutes.coerceAtMost((cap - already).coerceAtLeast(0))
        if (granted <= 0) return 0

        FocusStore.setInt(context, KEY_BALANCE, balanceMinutes(context) + granted)
        FocusStore.setInt(context, KEY_TOTAL_EARNED, totalEarned(context) + granted)

        val record = JSONObject()
        record.put("day", todayKey())
        record.put("minutes", already + granted)
        FocusStore.setJsonObject(context, KEY_EARNED_TODAY, record)

        PolicySync.request(context, "earn:credit")
        return granted
    }

    fun capReachedToday(context: Context): Boolean {
        val cap = EarnMode.dailyCapMinutes(context)
        return cap > 0 && earnedToday(context) >= cap
    }

    // ── Spending ──────────────────────────────────────────────────

    fun isSpending(context: Context): Boolean = remainingSpendMs(context) > 0L

    fun remainingSpendMs(context: Context): Long {
        val until = FocusStore.getLong(context, KEY_SPEND_UNTIL, 0L)
        if (until <= 0L) return 0L
        val remaining = until - System.currentTimeMillis()
        if (remaining <= 0L) {
            FocusStore.setLong(context, KEY_SPEND_UNTIL, 0L)
            PolicySync.request(context, "earn:spendEnded")
            return 0L
        }
        return remaining
    }

    /** Spends [minutes] from the balance and opens a window of that length. */
    fun spend(context: Context, minutes: Int): Boolean {
        val available = balanceMinutes(context)
        if (minutes <= 0 || available < minutes) return false

        FocusStore.setInt(context, KEY_BALANCE, available - minutes)
        FocusStore.setInt(context, KEY_TOTAL_SPENT, totalSpent(context) + minutes)
        FocusStore.setLong(
            context,
            KEY_SPEND_UNTIL,
            System.currentTimeMillis() + minutes * 60_000L
        )
        PolicySync.request(context, "earn:spend")
        AppBlockerService.start(context)
        return true
    }

    /** Stops early and refunds the whole remaining minutes, rounded down. */
    fun stopSpending(context: Context) {
        val remaining = (remainingSpendMs(context) / 60_000L).toInt()
        FocusStore.setLong(context, KEY_SPEND_UNTIL, 0L)
        if (remaining > 0) {
            FocusStore.setInt(context, KEY_BALANCE, balanceMinutes(context) + remaining)
            FocusStore.setInt(context, KEY_TOTAL_SPENT, (totalSpent(context) - remaining).coerceAtLeast(0))
        }
        PolicySync.request(context, "earn:spendStopped")
    }

    /**
     * Optional overnight fade. Off by default, and even when on it halves
     * rather than clears: a balance that vanishes entirely reads as punishment
     * for not spending it, which is the wrong lesson.
     */
    private fun applyDecayIfDue(context: Context) {
        if (!EarnMode.decaysUnspent(context)) return
        val today = dayNumber()
        val last = FocusStore.getInt(context, KEY_LAST_DECAY_DAY, today)
        if (last >= today) {
            if (last > today) FocusStore.setInt(context, KEY_LAST_DECAY_DAY, today)
            return
        }
        val current = FocusStore.getInt(context, KEY_BALANCE, 0)
        FocusStore.setInt(context, KEY_BALANCE, current / 2)
        FocusStore.setInt(context, KEY_LAST_DECAY_DAY, today)
    }

    fun formatBalance(context: Context): String {
        val minutes = balanceMinutes(context)
        if (minutes <= 0) return "Nothing banked yet"
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours > 0) hours.toString() + "h " + rest + "m banked" else minutes.toString() + " min banked"
    }

    private fun todayKey(): String {
        val calendar = java.util.Calendar.getInstance()
        return calendar.get(java.util.Calendar.YEAR).toString() + "-" +
            calendar.get(java.util.Calendar.DAY_OF_YEAR)
    }

    private fun dayNumber(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()
}
