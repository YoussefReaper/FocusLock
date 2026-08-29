package com.focuslock.mdm

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Tasks tab.
 *
 * Two things live here and they are deliberately separable: a task list that
 * works whether or not Earn Mode is on, and the earning layer on top of it.
 * Tasks are useful on their own, and a person who wants the list without the
 * economy should not have to accept the economy to get it.
 *
 * Nothing on this screen is gamified. There are no points, no confetti, no
 * levels — the reward is minutes of your own phone, stated in minutes.
 */
class TasksTab(activity: MainActivity, tokens: UiPrefs.Tokens) : FocusTab(activity, tokens) {

    private lateinit var container: LinearLayout
    private var filter = 0
    private val filters = listOf("Today", "Overdue", "All", "Done")

    override fun build(): View {
        container = FocusUi.column(activity, tokens.density.contentPaddingDp)
        return FocusUi.scroll(activity, container)
    }

    override fun onShow() {
        sweepMissed()
        render()
    }

    override fun onTick() {
        if (EarnBudget.isSpending(activity) || EarnSession.isActive(activity)) render()
    }

    /** Deadlines that came and went are recorded once, quietly, and never punished. */
    private fun sweepMissed() {
        FocusTaskStore.overdue(activity)
            .filter { it.deadline != null && it.missedCount == 0 }
            .forEach { FocusTaskStore.markMissed(activity, it) }
    }

    private fun render() {
        container.removeAllViews()
        val added = ArrayList<View>()

        fun add(view: View) {
            container.addView(view)
            added.add(view)
        }

        add(
            FocusUi.pageHeader(
                activity,
                tokens,
                "Tasks",
                if (EarnMode.isEnabled(activity)) {
                    Copy.earnDeal(activity)
                } else {
                    "What you actually mean to do. Earn Mode can attach time to these."
                }
            )
        )

        if (!EarnMode.isEnabled(activity)) {
            add(buildEarnIntro())
        } else {
            EarnSession.activeTask(activity)?.let { add(buildActiveSessionCard(it)) }
            add(buildBudgetCard())
        }

        add(FocusUi.chipStrip(activity, tokens, filters, filter) { index ->
            filter = index
            render()
        })

        add(buildTaskList())

        add(FocusUi.spacer(activity, 4))
        add(FocusUi.primaryButton(activity, tokens, "Add a task") { openEditor(null) })

        add(FocusUi.spacer(activity, 16))
        val footer = FocusUi.caption(activity, tokens, Copy.onDeviceFooter(activity))
        footer.gravity = android.view.Gravity.CENTER
        add(footer)
        add(FocusUi.spacer(activity, 20))

        Motion.stagger(added, tokens)
    }

    // ── Earn Mode introduction ────────────────────────────────────

    /**
     * The honest pitch, including the part that argues against itself.
     *
     * Telling someone up front that rewards can undercut motivation for work
     * they already enjoy is the difference between a tool and a sales page, and
     * it is what makes the opt-in a real choice.
     */
    private fun buildEarnIntro(): View {
        val card = FocusUi.card(activity, tokens, elevated = true)
        card.addView(FocusUi.heading(activity, tokens, "Earn mode is off"))
        card.addView(FocusUi.spacer(activity, 8))
        card.addView(
            FocusUi.secondary(
                activity,
                tokens,
                "With it on, finishing a task unlocks leisure minutes and the rest of the phone. " +
                    "You set the rate, what counts as done, and which apps a task may use."
            )
        )
        card.addView(FocusUi.spacer(activity, 10))
        card.addView(
            FocusUi.caption(
                activity,
                tokens,
                "Worth knowing first: paying yourself for work you already enjoy tends to make it " +
                    "less enjoyable, not more. Mark those tasks as enjoyable and they earn nothing " +
                    "on purpose. Earn mode is best pointed at the dull, necessary work."
            )
        )
        card.addView(FocusUi.spacer(activity, 14))
        card.addView(
            FocusUi.primaryButton(activity, tokens, "Turn on Earn mode") {
                CapabilityRegistry.setEnabled(activity, Capabilities.EARN_MODE, true)
                render()
            }
        )

        if (!EarnMode.hasHardEnforcement(activity)) {
            card.addView(FocusUi.spacer(activity, 10))
            card.addView(
                FocusUi.pill(activity, tokens, "Needs Device Owner setup for the hard version", tokens.warning)
            )
        }
        return card
    }

    // ── Active session ────────────────────────────────────────────

    private fun buildActiveSessionCard(task: FocusTask): View {
        val card = FocusUi.card(activity, tokens, elevated = true) { openSession() }

        val header = FocusUi.row(activity)
        val title = FocusUi.heading(activity, tokens, task.title)
        title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        header.addView(title)
        header.addView(FocusUi.pill(activity, tokens, "Running", tokens.success))
        card.addView(header)

        card.addView(FocusUi.spacer(activity, 6))
        card.addView(
            FocusUi.secondary(
                activity,
                tokens,
                SessionManager.formatDuration(EarnSession.elapsedMs(activity)) + " in · " +
                    task.verification.label.lowercase() +
                    (if (EarnSession.isStandalone(activity)) " · standalone" else " · on top of your session")
            )
        )

        card.addView(FocusUi.spacer(activity, 14))
        card.addView(FocusUi.primaryButton(activity, tokens, "Open the session") { openSession() })
        return card
    }

    // ── Budget ────────────────────────────────────────────────────

    private fun buildBudgetCard(): View {
        val card = FocusUi.card(activity, tokens)

        if (EarnBudget.isSpending(activity)) {
            val remaining = EarnBudget.remainingSpendMs(activity)
            card.addView(FocusUi.heading(activity, tokens, "Earned time, running"))
            card.addView(FocusUi.spacer(activity, 6))
            card.addView(
                FocusUi.display(activity, tokens, SessionManager.formatCountdown(remaining))
            )
            card.addView(FocusUi.spacer(activity, 6))
            card.addView(
                FocusUi.secondary(
                    activity,
                    tokens,
                    "Blocked apps are open. Your schedules and bedtime still hold, because those " +
                        "were about when, not about work."
                )
            )
            card.addView(FocusUi.spacer(activity, 12))
            card.addView(
                FocusUi.secondaryButton(activity, tokens, "Stop and bank the rest") {
                    EarnBudget.stopSpending(activity)
                    render()
                }
            )
            return card
        }

        val balance = EarnBudget.balanceMinutes(activity)

        val header = FocusUi.row(activity)
        val title = FocusUi.heading(activity, tokens, EarnBudget.formatBalance(activity))
        title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        header.addView(title)
        if (EarnBudget.earnedToday(activity) > 0) {
            header.addView(
                FocusUi.pill(
                    activity,
                    tokens,
                    "+" + EarnBudget.earnedToday(activity) + " today",
                    tokens.accent
                )
            )
        }
        card.addView(header)

        card.addView(FocusUi.spacer(activity, 6))
        card.addView(
            FocusUi.secondary(
                activity,
                tokens,
                if (balance <= 0) {
                    "Finish a task with minutes on it and they land here."
                } else {
                    "Spend them when you want. They do not expire" +
                        (if (EarnMode.decaysUnspent(activity)) ", though you asked them to fade overnight." else ".")
                }
            )
        )

        if (balance > 0) {
            card.addView(FocusUi.spacer(activity, 14))
            if (SessionManager.shouldLockTask(activity)) {
                card.addView(
                    FocusUi.caption(activity, tokens, Copy.earnSpendBlockedInKiosk(activity))
                )
            } else {
                card.addView(
                    FocusUi.primaryButton(activity, tokens, "Use " + balance + " minutes now") {
                        confirmSpend(balance)
                    }
                )
                if (balance > 10) {
                    card.addView(FocusUi.spacer(activity, 8))
                    card.addView(
                        FocusUi.secondaryButton(activity, tokens, "Use 10 minutes") { confirmSpend(10) }
                    )
                }
            }
        }

        card.addView(FocusUi.spacer(activity, 10))
        card.addView(
            FocusUi.listRow(
                activity,
                tokens,
                "Your deal",
                EarnMode.describeDeal(activity),
                trailing = FocusUi.chevron(activity, tokens)
            ) { openDealSheet() }
        )
        return card
    }

    private fun confirmSpend(minutes: Int) {
        FocusDialog.alert(
            activity,
            title = "Use " + minutes + " minutes?",
            message = "Blocked apps open for that long, then close again. Stopping early banks " +
                "whatever is left.",
            confirmLabel = "Start",
            cancelLabel = "Not now",
            onConfirm = {
                if (EarnBudget.spend(activity, minutes)) {
                    FocusDialog.toast(activity, Copy.earnSpending(activity, minutes))
                }
                render()
            }
        )
    }

    /** Every number in the economy, in one place, all of them editable. */
    private fun openDealSheet() {
        FocusDialog.custom(
            activity,
            title = "Your deal",
            subtitle = "Nobody else set these, and nothing enforces them but you.",
            confirmLabel = null,
            cancelLabel = "Done"
        ) { body, dialogTokens ->
            body.addView(
                FocusUi.sliderRow(
                    activity,
                    dialogTokens,
                    "Minutes earned per hour of focus",
                    0,
                    60,
                    EarnMode.ratePercent(activity) * 60 / 100,
                    { if (it == 0) "Only per-task rewards" else it.toString() + " min" }
                ) { value -> EarnMode.setRatePercent(activity, value * 100 / 60) }
            )
            body.addView(
                FocusUi.sliderRow(
                    activity,
                    dialogTokens,
                    "Most you can earn in a day",
                    0,
                    360,
                    EarnMode.dailyCapMinutes(activity),
                    { if (it == 0) "No cap" else it.toString() + " min" }
                ) { value -> EarnMode.setDailyCapMinutes(activity, value) }
            )

            body.addView(FocusUi.divider(activity, dialogTokens, 8))

            body.addView(
                FocusUi.toggleRow(
                    activity,
                    dialogTokens,
                    "Photo proof",
                    "Lets a task ask for a photo, checked on this phone.",
                    CapabilityRegistry.getBoolParam(activity, Capabilities.EARN_MODE, EarnMode.PARAM_PHOTO_PROOF, true)
                ) { value ->
                    CapabilityRegistry.setBoolParam(activity, Capabilities.EARN_MODE, EarnMode.PARAM_PHOTO_PROOF, value)
                }
            )
            body.addView(
                FocusUi.toggleRow(
                    activity,
                    dialogTokens,
                    "Tasks cannot widen a running mode",
                    "A task's apps must also be on your standing allowlist. Turning this off is the " +
                        "one setting here that opens a way around your own rules.",
                    EarnMode.intersectsWithAllowlist(activity)
                ) { value ->
                    CapabilityRegistry.setBoolParam(activity, Capabilities.EARN_MODE, EarnMode.PARAM_INTERSECT, value)
                    if (!value) {
                        FocusDialog.toast(activity, "A task can now allow apps your modes block.")
                    }
                }
            )
            body.addView(
                FocusUi.toggleRow(
                    activity,
                    dialogTokens,
                    "Show the balance during a task",
                    null,
                    EarnMode.showsBudgetWhileActive(activity)
                ) { value ->
                    CapabilityRegistry.setBoolParam(activity, Capabilities.EARN_MODE, EarnMode.PARAM_SHOW_BUDGET, value)
                }
            )
            body.addView(
                FocusUi.toggleRow(
                    activity,
                    dialogTokens,
                    "Track verification trust",
                    "A quiet score that drops when photo proof is refused.",
                    EarnMode.showsCredibility(activity)
                ) { value ->
                    CapabilityRegistry.setBoolParam(activity, Capabilities.EARN_MODE, EarnMode.PARAM_CREDIBILITY, value)
                }
            )
            body.addView(
                FocusUi.toggleRow(
                    activity,
                    dialogTokens,
                    "Unspent minutes fade overnight",
                    "Halves the balance each day. Off by default: banking is not a problem.",
                    EarnMode.decaysUnspent(activity)
                ) { value ->
                    CapabilityRegistry.setBoolParam(activity, Capabilities.EARN_MODE, EarnMode.PARAM_DECAY, value)
                }
            )
        }
    }

    // ── The list ──────────────────────────────────────────────────

    private fun buildTaskList(): View {
        val tasks = when (filter) {
            1 -> FocusTaskStore.overdue(activity)
            2 -> FocusTaskStore.open(activity).sortedWith(
                compareByDescending<FocusTask> { it.priority.ordinal }.thenBy { it.title }
            )
            3 -> FocusTaskStore.all(activity).filter { it.completed }
                .sortedByDescending { it.completedAt ?: 0L }
            else -> FocusTaskStore.dueToday(activity)
        }

        val card = FocusUi.card(activity, tokens)
        if (tasks.isEmpty()) {
            card.addView(FocusUi.emptyState(activity, tokens, emptyMessage()))
            return card
        }

        tasks.forEachIndexed { index, task ->
            card.addView(buildTaskRow(task))
            if (index < tasks.size - 1) card.addView(FocusUi.divider(activity, tokens))
        }
        return card
    }

    private fun emptyMessage(): String = when (filter) {
        1 -> "Nothing overdue. That is worth noticing."
        3 -> "Nothing finished yet today."
        else -> "No tasks yet. One honest line is enough to start."
    }

    private fun buildTaskRow(task: FocusTask): View {
        val column = FocusUi.column(activity)

        val trailing = if (EarnMode.isEnabled(activity) && !task.completed) {
            FocusUi.smallButton(activity, tokens, "Start") { startTask(task) }
        } else {
            FocusUi.chevron(activity, tokens)
        }

        column.addView(
            FocusUi.listRow(
                activity,
                tokens,
                task.title,
                describe(task),
                trailing = trailing,
                leading = priorityDot(task.priority)
            ) { openEditor(task) }
        )

        if (task.subtasks.isNotEmpty() && !task.completed) {
            column.addView(
                FocusUi.meter(
                    activity,
                    tokens,
                    task.subtasks.count { it.done }.toString() + " of " + task.subtasks.size + " steps",
                    task.progressPercent.toString() + "%",
                    task.progressPercent / 100f,
                    tokens.accent
                )
            )
        }
        return column
    }

    private fun describe(task: FocusTask): String {
        val parts = ArrayList<String>()

        if (task.completed) {
            task.completedAt?.let {
                parts.add("Done " + SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(it)))
            }
        } else {
            (task.deadline ?: task.dueDate)?.let { due ->
                val label = if (task.deadline != null) "Due by " else "For "
                parts.add(label + SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(due)))
            }
            if (task.isOverdue) parts.add("overdue")
        }

        task.timeEstimateMin?.let { parts.add(it.toString() + " min") }
        parts.add(task.verification.label.lowercase())

        if (EarnMode.isEnabled(activity)) {
            when {
                task.enjoyable -> parts.add("no reward, by choice")
                task.rewardMin != null -> parts.add("+" + task.rewardMin + " min")
                else -> parts.add("earns at your rate")
            }
        }

        if (task.recurrence != Recurrence.NONE) parts.add(task.recurrence.label.lowercase())
        if (task.tags.isNotEmpty()) parts.add(task.tags.joinToString(" "))

        return parts.joinToString(" · ")
    }

    private fun priorityDot(priority: Priority): View {
        val dot = View(activity)
        val size = FocusUi.dp(activity, 10)
        val color = when (priority) {
            Priority.HIGH -> tokens.danger
            Priority.MED -> tokens.warning
            Priority.LOW -> tokens.accent
            Priority.NONE -> tokens.track
        }
        dot.background = FocusUi.roundedShape(activity, color, 5)
        dot.layoutParams = LinearLayout.LayoutParams(size, size)
        return dot
    }

    // ── Actions ───────────────────────────────────────────────────

    private fun openEditor(task: FocusTask?) {
        activity.startActivity(
            Intent(activity, TaskEditorActivity::class.java).apply {
                if (task != null) putExtra(TaskEditorActivity.EXTRA_TASK_ID, task.id)
            }
        )
    }

    private fun openSession() {
        activity.startActivity(Intent(activity, EarnSessionActivity::class.java))
    }

    /**
     * Starting a task is the moment the deal becomes real, so it states what is
     * about to shut and what is about to stay open before anything changes.
     */
    private fun startTask(task: FocusTask) {
        val sessionRunning = SessionManager.isActive(activity)
        val standalone = !sessionRunning

        FocusDialog.custom(
            activity,
            title = "Start " + task.title + "?",
            subtitle = null,
            confirmLabel = "Start",
            cancelLabel = "Cancel",
            onConfirm = {
                EarnSession.start(activity, task, standalone)
                openSession()
            }
        ) { body, dialogTokens ->
            body.addView(
                FocusUi.secondary(
                    activity,
                    dialogTokens,
                    if (standalone) {
                        if (EarnMode.hasHardEnforcement(activity)) {
                            "The phone locks to FocusLock and the apps this task needs, until it is done."
                        } else {
                            "Without Device Owner this runs as friction rather than a lock: distracting " +
                                "apps get intercepted, not held shut."
                        }
                    } else {
                        "Your " + SessionManager.mode(activity).label.lowercase() +
                            " session keeps running. This task narrows it further."
                    }
                )
            )

            body.addView(FocusUi.spacer(activity, 10))
            body.addView(FocusUi.caption(activity, dialogTokens, "OPEN DURING THIS TASK"))

            val allowed = EarnSession.allowedPackages(activity, task, standalone)
                .filter { it != activity.packageName }
                .filter { AppCatalog.isInstalled(activity, it) }
            body.addView(
                FocusUi.secondary(
                    activity,
                    dialogTokens,
                    if (allowed.isEmpty()) {
                        "FocusLock only."
                    } else {
                        allowed.joinToString { AppCatalog.label(activity, it) }
                    }
                )
            )

            // The loophole, made visible instead of silent.
            val rejected = EarnSession.rejectedPackages(activity, task, standalone)
            if (rejected.isNotEmpty()) {
                body.addView(FocusUi.spacer(activity, 10))
                val warning = FocusUi.caption(
                    activity,
                    dialogTokens,
                    "Held back because they are not on your standing allowlist: " +
                        rejected.joinToString { AppCatalog.label(activity, it) } +
                        ". Add them in Rules first if the task really needs them."
                )
                warning.setTextColor(dialogTokens.warning)
                body.addView(warning)
            }
        }
    }
}
