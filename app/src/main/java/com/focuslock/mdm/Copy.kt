package com.focuslock.mdm

import android.content.Context

/**
 * Every word the user reads at a hard moment.
 *
 * Two registers. **Kind** is the default: plain, warm, second-person, and it
 * never implies the person failed — shame reliably backfires on exactly the
 * behaviour a blocker is trying to change, and "you failed" is the sentence
 * that gets apps uninstalled. **Plain** is the same information with the warmth
 * removed, for people who find encouragement patronising.
 *
 * Nothing here scolds, counts failures, or threatens a streak.
 */
object Copy {

    private fun kind(context: Context): Boolean =
        CapabilityRegistry.isEnabled(context, Capabilities.SELF_COMPASSION_COPY)

    /**
     * Picks a line that stays the same for a few minutes at a time, so a screen
     * that redraws every tick does not shuffle its words under the reader.
     */
    private fun pick(options: List<String>): String {
        if (options.isEmpty()) return ""
        val bucket = (System.currentTimeMillis() / 300_000L).toInt()
        val index = ((bucket % options.size) + options.size) % options.size
        return options[index]
    }

    private fun appLabel(context: Context, packageName: String): String =
        AppCatalog.label(context, packageName)

    // ── Blocking ──────────────────────────────────────────────────

    fun blockHeadline(context: Context, packageName: String): String {
        val label = appLabel(context, packageName)
        return if (kind(context)) {
            pick(
                listOf(
                    label + " is resting",
                    "Not " + label + " right now",
                    label + " is put away for now"
                )
            )
        } else {
            label + " is blocked"
        }
    }

    fun blockDetail(context: Context, packageName: String): String {
        if (!kind(context)) return "Blocked by your app rules."
        return pick(
            listOf(
                "You decided this one gets in the way. That decision is still good.",
                "Earlier you asked for this to be out of reach. Here it is, out of reach.",
                "This was your call, made calmly, ahead of time."
            )
        )
    }

    fun pauseHeadline(context: Context, packageName: String): String {
        val label = appLabel(context, packageName)
        return if (kind(context)) {
            pick(
                listOf(
                    "Before " + label + " opens",
                    "One moment first",
                    "A short pause"
                )
            )
        } else {
            "Pause before " + label
        }
    }

    fun pauseDetail(context: Context): String {
        if (!kind(context)) return "Wait for the timer, then continue or go back."
        return pick(
            listOf(
                "Take a breath. If you still want it after this, it opens.",
                "No judgement either way. Just a few seconds between the reach and the app.",
                "Most of the time the urge passes in less time than this takes."
            )
        )
    }

    fun softDetail(context: Context): String {
        if (!kind(context)) return "Soft mode. Continue or go back."
        return "Soft mode does not hold anything shut. This is only a reminder of what you asked for."
    }

    fun kioskDetail(context: Context): String {
        val remaining = SessionManager.formatRemaining(context)
        if (!kind(context)) return "Kiosk session active. " + remaining + " remaining."
        return pick(
            listOf(
                "Kiosk is running for another " + remaining + ". The phone comes back on its own.",
                remaining + " left in this session. Nothing here needs you to be strong right now.",
                "You set this up so you would not have to decide again. " + remaining + " to go."
            )
        )
    }

    fun kioskHomeHeadline(context: Context): String =
        if (kind(context)) "This is home for now" else "Launcher blocked"

    fun kioskHomeDetail(context: Context): String {
        if (!kind(context)) return "The launcher is disabled during a kiosk session."
        return "Your usual home screen is waiting. It comes back when the session ends."
    }

    fun overlayGuardHeadline(context: Context): String =
        if (kind(context)) "That page can switch FocusLock off" else "Permission page blocked"

    fun overlayGuardDetail(context: Context): String {
        if (!kind(context)) return "Overlay permission settings are blocked during a session."
        return "It is the one screen that could end the session early by accident, so it stays shut until the session does."
    }

    // ── Schedules, bedtime, place ─────────────────────────────────

    fun scheduleHeadline(context: Context, window: ScheduleWindow): String {
        if (window.message.isNotBlank()) return window.message
        return if (kind(context)) "This time is spoken for" else "Scheduled block"
    }

    fun scheduleDetail(context: Context, window: ScheduleWindow): String {
        val until = ScheduleManager.formatTime(window.endMinutes)
        if (!kind(context)) return "Window ends at " + until + "."
        return pick(
            listOf(
                "You put this window in the calendar yourself. It opens up again at " + until + ".",
                "Until " + until + ", the phone is holding the line for you.",
                "Back to normal at " + until + "."
            )
        )
    }

    fun scheduleOverlayHeadline(context: Context, window: ScheduleWindow): String {
        if (window.message.isNotBlank()) return window.message
        return if (kind(context)) "This time is locked in" else "Locked schedule window"
    }

    /** Same shape as [scheduleDetail], but never offers a break - this window doesn't bend. */
    fun scheduleOverlayDetail(context: Context, window: ScheduleWindow): String {
        val until = ScheduleManager.formatTime(window.endMinutes)
        if (!kind(context)) return "Locked until " + until + ". No break, no exceptions."
        return pick(
            listOf(
                "You set this one to overlay, so nothing - not even a break - talks you out of it. " +
                    "Opens up again at " + until + ".",
                "This window doesn't bend. Back to normal at " + until + ".",
                "No exceptions until " + until + " - that was the whole point of locking it."
            )
        )
    }

    fun bedtimeHeadline(context: Context): String =
        if (kind(context)) "It is night" else "Bedtime block"

    fun bedtimeDetail(context: Context): String {
        val end = Bedtime.formatTime(Bedtime.endMinutes(context))
        if (!kind(context)) return "Bedtime runs until " + end + "."
        return pick(
            listOf(
                "This comes back at " + end + ". Sleep is the thing that makes tomorrow work.",
                "Nothing on there needs an answer tonight. Back at " + end + ".",
                "You asked for quiet after this hour. Quiet until " + end + "."
            )
        )
    }

    fun placeHeadline(context: Context, place: Place): String {
        if (!kind(context)) return "Blocked at " + place.label
        return if (place.trigger == PlaceTrigger.INSIDE) {
            "Not while you are at " + place.label
        } else {
            "Only at " + place.label
        }
    }

    fun placeDetail(context: Context, place: Place): String {
        if (!kind(context)) return "Place rule: " + place.label
        return "You set this up for " + place.label + ". It lifts by itself when you move."
    }

    fun limitHeadline(context: Context, packageName: String): String {
        val label = appLabel(context, packageName)
        return if (kind(context)) label + " has had its time today" else label + ": limit reached"
    }

    fun ruleHeadline(context: Context, rule: Rule): String {
        if (rule.label.isNotBlank()) return rule.label
        return if (kind(context)) "One of your rules caught this" else "Blocked by rule"
    }

    fun ruleDetail(context: Context, rule: Rule): String {
        val condition = rule.conditionType.label.lowercase()
        if (!kind(context)) return "Rule condition: " + condition + "."
        return "Your rule for " + condition + " is holding. You can change it in Rules any time."
    }

    // ── Earn mode ─────────────────────────────────────────────────
    //
    // The tone here does more work than anywhere else in the app. A reward
    // system that sounds like a slot machine trains the wrong thing, and one
    // that sounds like a supervisor is exactly the "controlling" framing the
    // Self-Determination research says turns a reward into a demotivator. So:
    // the time is described as already the person's, the deal as one they wrote,
    // and a miss as a fact rather than a verdict.

    fun earnHeadline(context: Context, packageName: String): String {
        val label = appLabel(context, packageName)
        return if (kind(context)) {
            pick(
                listOf(
                    label + " is waiting on the task",
                    "Task first, then " + label,
                    "Not yet, " + label
                )
            )
        } else {
            label + " is blocked during this task"
        }
    }

    fun earnDetail(context: Context, task: FocusTask): String {
        if (!kind(context)) return "Active task: " + task.title
        val reward = task.rewardMin
        val tail = when {
            task.enjoyable -> "No minutes attached to this one, by your choice."
            reward != null && reward > 0 -> "Finish it and " + reward + " minutes come back."
            else -> "Finish it and the time you put in comes back at your rate."
        }
        return "You are on " + task.title + ". " + tail
    }

    fun earnCompleted(context: Context, minutes: Int): String {
        if (minutes <= 0) {
            return if (kind(context)) {
                "Done. No minutes on this one, which was the point."
            } else {
                "Task complete. No reward configured."
            }
        }
        if (!kind(context)) return minutes.toString() + " minutes credited."
        return pick(
            listOf(
                "You earned " + minutes + " minutes. It was your time to begin with.",
                minutes.toString() + " minutes back. Spend them or bank them.",
                "That is " + minutes + " minutes, on the deal you set yourself."
            )
        )
    }

    fun earnCapReached(context: Context): String {
        if (!kind(context)) return "Daily earning cap reached."
        return "That is the cap you set for today. The work still counted."
    }

    fun earnMissed(context: Context, task: FocusTask): String {
        if (!kind(context)) return "Deadline passed: " + task.title
        return pick(
            listOf(
                "The deadline on " + task.title + " went by. Pick it up today.",
                task.title + " did not happen in time. Nothing is lost but the date.",
                "That one slipped. It is still here when you want it."
            )
        )
    }

    fun earnSpending(context: Context, minutes: Int): String {
        if (!kind(context)) return minutes.toString() + " minutes unlocked."
        return "The next " + minutes + " minutes are yours. Nothing is watching how you use them."
    }

    fun earnSpendBlockedInKiosk(context: Context): String {
        if (!kind(context)) return "Cannot spend earned time during a kiosk session."
        return "A kiosk session runs to the end, so earned minutes wait for it. They do not expire."
    }

    fun earnPhotoRetry(context: Context, attempts: Int): String {
        if (!kind(context)) return "Not accepted. Attempt " + attempts + "."
        return pick(
            listOf(
                "Have another go. Nothing was recorded about that one.",
                "Try a different angle.",
                "That did not pass. No harm done, take another."
            )
        )
    }

    fun earnDeal(context: Context): String =
        if (kind(context)) {
            "Your deal, your numbers: " + EarnMode.describeDeal(context)
        } else {
            EarnMode.describeDeal(context)
        }

    /** Shown wherever tasks or proof photos are handled. */
    fun onDeviceFooter(context: Context): String =
        if (kind(context)) {
            "Tasks, notes, attachments and proof photos stay on this phone. There is no account and " +
                "nowhere for them to go."
        } else {
            "All task data is local. No network calls."
        }

    // ── Sessions ──────────────────────────────────────────────────

    fun sessionStarted(context: Context, mode: FocusMode): String {
        if (!kind(context)) return mode.label + " session started."
        return pick(
            listOf(
                "Session running. Nothing else to decide for a while.",
                mode.label + " is on. The phone will hold this for you.",
                "You are in. Go do the thing."
            )
        )
    }

    fun sessionEnded(context: Context): String {
        if (!kind(context)) return "Session ended."
        return pick(
            listOf(
                "Session done. That counted.",
                "Finished. However it went, you showed up for it.",
                "That is the end of it. The phone is yours again."
            )
        )
    }

    fun sessionEndedEarly(context: Context): String {
        if (!kind(context)) return "Session ended early."
        return "Ended early, and that is allowed. The time before you stopped still happened."
    }

    /**
     * Shown when a rule change is refused because a session is holding.
     *
     * The person is not being told off for trying. They are being reminded that
     * a calmer version of them already made this call, which is the whole point
     * of setting it up in advance.
     */
    fun rulesFrozen(context: Context, remaining: String): String {
        if (!kind(context)) return "Rules are locked during a session. " + remaining + " remaining."
        return "Your rules are held still until this session ends, in " + remaining +
            ". You decided that ahead of time, back when it was an easy call."
    }

    /** Same refusal, for an overlay schedule window rather than a session - see [ScheduleWindow.overlay]. */
    fun rulesFrozenBySchedule(context: Context, until: String): String {
        if (!kind(context)) return "Rules are locked by an overlay window. Opens up again at " + until + "."
        return "Your rules are held still until this window opens up again, at " + until +
            ". You set it to overlay yourself, back when it was an easy call."
    }

    /** The line under a frozen switch, explaining why it will not move. */
    fun rulesFrozenHint(context: Context): String {
        if (!kind(context)) return "Locked right now."
        return "Held still right now."
    }

    fun breakStarted(context: Context, minutes: Int): String {
        if (!kind(context)) return minutes.toString() + " minute pass started."
        return "Take " + minutes + " minutes. Choosing a break on purpose is not the same as losing the thread."
    }

    fun breakUnavailable(context: Context): String {
        if (!kind(context)) return "No breaks left today."
        return "That is the last of today's breaks. Tomorrow the count starts again."
    }

    fun relapseNote(context: Context): String {
        if (!kind(context)) return "Session interrupted."
        return pick(
            listOf(
                "A gap is not a collapse. Pick it up from here.",
                "Nothing to make up for. Just carry on.",
                "One day off the pattern does not undo the pattern."
            )
        )
    }

    // ── Onboarding and settings ───────────────────────────────────

    fun weakenWarning(spec: CapabilitySpec): String =
        spec.weakenNote ?: "This leaves an escape route open. That is your call."

    fun emptyRules(context: Context): String =
        if (kind(context)) {
            "No rules yet. That is a fine place to start."
        } else {
            "No rules."
        }

    fun emptySchedules(context: Context): String =
        if (kind(context)) {
            "No windows yet. Add one for the hours you already know are hard."
        } else {
            "No schedule windows."
        }

    fun emptyKeywords(context: Context): String =
        if (kind(context)) {
            "No words of your own yet. The built-in guards are still running."
        } else {
            "No custom keywords."
        }

    fun analyticsIntro(context: Context): String =
        if (kind(context)) {
            "This is information, not a verdict. It only leaves this phone if you carry it out yourself."
        } else {
            "Local usage data. Nothing is uploaded."
        }
}
