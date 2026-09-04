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
    private val filters get() = activity.resources.getStringArray(R.array.tasks_filters).toList()

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
                activity.getString(R.string.tasks_page_title),
                if (EarnMode.isEnabled(activity)) {
                    Copy.earnDeal(activity)
                } else {
                    activity.getString(R.string.tasks_page_subtitle)
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
        add(FocusUi.primaryButton(activity, tokens, activity.getString(R.string.tasks_add_task_button)) { openEditor(null) })

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
        card.addView(FocusUi.heading(activity, tokens, activity.getString(R.string.tasks_earn_intro_heading)))
        card.addView(FocusUi.spacer(activity, 8))
        card.addView(
            FocusUi.secondary(
                activity,
                tokens,
                activity.getString(R.string.tasks_earn_intro_body)
            )
        )
        card.addView(FocusUi.spacer(activity, 10))
        card.addView(
            FocusUi.caption(
                activity,
                tokens,
                activity.getString(R.string.tasks_earn_intro_caption)
            )
        )
        card.addView(FocusUi.spacer(activity, 14))
        card.addView(
            FocusUi.primaryButton(activity, tokens, activity.getString(R.string.tasks_earn_intro_button)) {
                if (!CapabilityRegistry.setEnabled(activity, Capabilities.EARN_MODE, true)) {
                    FocusDialog.toast(activity, SessionLock.refusalMessage(activity))
                }
                render()
            }
        )

        if (!EarnMode.hasHardEnforcement(activity)) {
            card.addView(FocusUi.spacer(activity, 10))
            card.addView(
                FocusUi.pill(activity, tokens, activity.getString(R.string.tasks_earn_intro_needs_device_owner), tokens.warning)
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
        header.addView(FocusUi.pill(activity, tokens, activity.getString(R.string.tasks_active_session_running_pill), tokens.success))
        card.addView(header)

        card.addView(FocusUi.spacer(activity, 6))
        card.addView(
            FocusUi.secondary(
                activity,
                tokens,
                if (EarnSession.isStandalone(activity)) {
                    activity.getString(
                        R.string.tasks_active_session_summary_standalone,
                        SessionManager.formatDuration(EarnSession.elapsedMs(activity)),
                        task.verification.label.lowercase()
                    )
                } else {
                    activity.getString(
                        R.string.tasks_active_session_summary_linked,
                        SessionManager.formatDuration(EarnSession.elapsedMs(activity)),
                        task.verification.label.lowercase()
                    )
                }
            )
        )

        card.addView(FocusUi.spacer(activity, 14))
        card.addView(FocusUi.primaryButton(activity, tokens, activity.getString(R.string.tasks_active_session_open_button)) { openSession() })
        return card
    }

    // ── Budget ────────────────────────────────────────────────────

    private fun buildBudgetCard(): View {
        val card = FocusUi.card(activity, tokens)

        if (EarnBudget.isSpending(activity)) {
            val remaining = EarnBudget.remainingSpendMs(activity)
            card.addView(FocusUi.heading(activity, tokens, activity.getString(R.string.tasks_budget_running_heading)))
            card.addView(FocusUi.spacer(activity, 6))
            card.addView(
                FocusUi.display(activity, tokens, SessionManager.formatCountdown(remaining))
            )
            card.addView(FocusUi.spacer(activity, 6))
            card.addView(
                FocusUi.secondary(
                    activity,
                    tokens,
                    activity.getString(R.string.tasks_budget_running_body)
                )
            )
            card.addView(FocusUi.spacer(activity, 12))
            card.addView(
                FocusUi.secondaryButton(activity, tokens, activity.getString(R.string.tasks_budget_stop_button)) {
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
                    activity.getString(R.string.tasks_budget_earned_today_pill, EarnBudget.earnedToday(activity)),
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
                    activity.getString(R.string.tasks_budget_empty_hint)
                } else if (EarnMode.decaysUnspent(activity)) {
                    activity.getString(R.string.tasks_budget_spend_hint_decay)
                } else {
                    activity.getString(R.string.tasks_budget_spend_hint)
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
                    FocusUi.primaryButton(activity, tokens, activity.getString(R.string.tasks_budget_use_balance_button, balance)) {
                        confirmSpend(balance)
                    }
                )
                if (balance > 10) {
                    card.addView(FocusUi.spacer(activity, 8))
                    card.addView(
                        FocusUi.secondaryButton(activity, tokens, activity.getString(R.string.tasks_budget_use_ten_button)) { confirmSpend(10) }
                    )
                }
            }
        }

        card.addView(FocusUi.spacer(activity, 10))
        card.addView(
            FocusUi.listRow(
                activity,
                tokens,
                activity.getString(R.string.tasks_budget_deal_row_title),
                EarnMode.describeDeal(activity),
                trailing = FocusUi.chevron(activity, tokens)
            ) { openDealSheet() }
        )
        return card
    }

    private fun confirmSpend(minutes: Int) {
        FocusDialog.alert(
            activity,
            title = activity.getString(R.string.tasks_confirm_spend_title, minutes),
            message = activity.getString(R.string.tasks_confirm_spend_message),
            confirmLabel = activity.getString(R.string.tasks_confirm_spend_confirm),
            cancelLabel = activity.getString(R.string.tasks_confirm_spend_cancel),
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
            title = activity.getString(R.string.tasks_deal_sheet_title),
            subtitle = activity.getString(R.string.tasks_deal_sheet_subtitle),
            confirmLabel = null,
            cancelLabel = activity.getString(R.string.tasks_deal_sheet_done)
        ) { body, dialogTokens ->
            body.addView(
                FocusUi.sliderRow(
                    activity,
                    dialogTokens,
                    activity.getString(R.string.tasks_deal_rate_label),
                    0,
                    60,
                    EarnMode.ratePercent(activity) * 60 / 100,
                    { if (it == 0) activity.getString(R.string.tasks_deal_rate_zero) else activity.getString(R.string.tasks_deal_minutes_value, it) }
                ) { value -> EarnMode.setRatePercent(activity, value * 100 / 60) }
            )
            body.addView(
                FocusUi.sliderRow(
                    activity,
                    dialogTokens,
                    activity.getString(R.string.tasks_deal_cap_label),
                    0,
                    360,
                    EarnMode.dailyCapMinutes(activity),
                    { if (it == 0) activity.getString(R.string.tasks_deal_cap_zero) else activity.getString(R.string.tasks_deal_minutes_value, it) }
                ) { value -> EarnMode.setDailyCapMinutes(activity, value) }
            )

            body.addView(FocusUi.divider(activity, dialogTokens, 8))

            body.addView(
                FocusUi.toggleRow(
                    activity,
                    dialogTokens,
                    activity.getString(R.string.tasks_deal_photo_proof_label),
                    activity.getString(R.string.tasks_deal_photo_proof_desc),
                    CapabilityRegistry.getBoolParam(activity, Capabilities.EARN_MODE, EarnMode.PARAM_PHOTO_PROOF, true)
                ) { value ->
                    if (!CapabilityRegistry.setBoolParam(activity, Capabilities.EARN_MODE, EarnMode.PARAM_PHOTO_PROOF, value)) {
                        FocusDialog.toast(activity, SessionLock.refusalMessage(activity))
                    }
                }
            )
            body.addView(
                FocusUi.toggleRow(
                    activity,
                    dialogTokens,
                    activity.getString(R.string.tasks_deal_intersect_label),
                    activity.getString(R.string.tasks_deal_intersect_desc),
                    EarnMode.intersectsWithAllowlist(activity)
                ) { value ->
                    if (!CapabilityRegistry.setBoolParam(activity, Capabilities.EARN_MODE, EarnMode.PARAM_INTERSECT, value)) {
                        FocusDialog.toast(activity, SessionLock.refusalMessage(activity))
                    } else if (!value) {
                        FocusDialog.toast(activity, activity.getString(R.string.tasks_deal_intersect_off_toast))
                    }
                }
            )
            body.addView(
                FocusUi.toggleRow(
                    activity,
                    dialogTokens,
                    activity.getString(R.string.tasks_deal_show_budget_label),
                    null,
                    EarnMode.showsBudgetWhileActive(activity)
                ) { value ->
                    if (!CapabilityRegistry.setBoolParam(activity, Capabilities.EARN_MODE, EarnMode.PARAM_SHOW_BUDGET, value)) {
                        FocusDialog.toast(activity, SessionLock.refusalMessage(activity))
                    }
                }
            )
            body.addView(
                FocusUi.toggleRow(
                    activity,
                    dialogTokens,
                    activity.getString(R.string.tasks_deal_credibility_label),
                    activity.getString(R.string.tasks_deal_credibility_desc),
                    EarnMode.showsCredibility(activity)
                ) { value ->
                    if (!CapabilityRegistry.setBoolParam(activity, Capabilities.EARN_MODE, EarnMode.PARAM_CREDIBILITY, value)) {
                        FocusDialog.toast(activity, SessionLock.refusalMessage(activity))
                    }
                }
            )
            body.addView(
                FocusUi.toggleRow(
                    activity,
                    dialogTokens,
                    activity.getString(R.string.tasks_deal_decay_label),
                    activity.getString(R.string.tasks_deal_decay_desc),
                    EarnMode.decaysUnspent(activity)
                ) { value ->
                    if (!CapabilityRegistry.setBoolParam(activity, Capabilities.EARN_MODE, EarnMode.PARAM_DECAY, value)) {
                        FocusDialog.toast(activity, SessionLock.refusalMessage(activity))
                    }
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
        1 -> activity.getString(R.string.tasks_empty_overdue)
        3 -> activity.getString(R.string.tasks_empty_done)
        else -> activity.getString(R.string.tasks_empty_default)
    }

    private fun buildTaskRow(task: FocusTask): View {
        val column = FocusUi.column(activity)

        val trailing = if (EarnMode.isEnabled(activity) && !task.completed) {
            FocusUi.smallButton(activity, tokens, activity.getString(R.string.tasks_row_start_button)) { startTask(task) }
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
                    activity.getString(R.string.tasks_row_steps_meter, task.subtasks.count { it.done }, task.subtasks.size),
                    activity.getString(R.string.tasks_row_percent, task.progressPercent),
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
                parts.add(activity.getString(R.string.tasks_describe_done, SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(it))))
            }
        } else {
            (task.deadline ?: task.dueDate)?.let { due ->
                val formatted = SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(due))
                parts.add(
                    if (task.deadline != null) {
                        activity.getString(R.string.tasks_describe_due_by, formatted)
                    } else {
                        activity.getString(R.string.tasks_describe_for, formatted)
                    }
                )
            }
            if (task.isOverdue) parts.add(activity.getString(R.string.tasks_describe_overdue))
        }

        task.timeEstimateMin?.let { parts.add(activity.getString(R.string.tasks_describe_minutes, it)) }
        parts.add(task.verification.label.lowercase())

        if (EarnMode.isEnabled(activity)) {
            when {
                task.enjoyable -> parts.add(activity.getString(R.string.tasks_describe_no_reward))
                task.rewardMin != null -> parts.add(activity.getString(R.string.tasks_describe_reward_min, task.rewardMin))
                else -> parts.add(activity.getString(R.string.tasks_describe_earns_at_rate))
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
            title = activity.getString(R.string.tasks_start_title, task.title),
            subtitle = null,
            confirmLabel = activity.getString(R.string.tasks_start_confirm),
            cancelLabel = activity.getString(R.string.tasks_start_cancel),
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
                            activity.getString(R.string.tasks_start_hard_enforcement)
                        } else {
                            activity.getString(R.string.tasks_start_soft_enforcement)
                        }
                    } else {
                        activity.getString(R.string.tasks_start_linked_session, SessionManager.mode(activity).label.lowercase())
                    }
                )
            )

            body.addView(FocusUi.spacer(activity, 10))
            body.addView(FocusUi.caption(activity, dialogTokens, activity.getString(R.string.tasks_start_open_during_caption)))

            val allowed = EarnSession.allowedPackages(activity, task, standalone)
                .filter { it != activity.packageName }
                .filter { AppCatalog.isInstalled(activity, it) }
            body.addView(
                FocusUi.secondary(
                    activity,
                    dialogTokens,
                    if (allowed.isEmpty()) {
                        activity.getString(R.string.tasks_start_focuslock_only)
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
                    activity.getString(
                        R.string.tasks_start_rejected_warning,
                        rejected.joinToString { AppCatalog.label(activity, it) }
                    )
                )
                warning.setTextColor(dialogTokens.warning)
                body.addView(warning)
            }
        }
    }
}
