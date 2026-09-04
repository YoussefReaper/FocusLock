package com.focuslock.mdm

import android.view.View
import android.widget.LinearLayout
import java.util.Calendar

/**
 * Schedule windows.
 *
 * A window is a stretch of the week where the phone goes quiet on its own, with
 * no session to remember to start. Every window says what is still allowed
 * inside it, and always-allowed apps are open in all of them, so nobody
 * discovers at 3pm that they scheduled themselves out of a phone call.
 */
class ScheduleActivity : FocusScreenActivity() {

    override fun screenTitle(): String = getString(R.string.schedule_title)

    override fun screenSubtitle(): String = getString(R.string.schedule_subtitle)

    override fun buildContent(column: LinearLayout) {
        column.addView(buildToggle())
        if (!CapabilityRegistry.isEnabled(this, Capabilities.SCHEDULES)) return

        column.addView(sectionLabel(getString(R.string.schedule_section_windows)))
        column.addView(buildWindowList())
        column.addView(sectionLabel(getString(R.string.schedule_section_plan)))
        column.addView(buildPlanCard())
    }

    private fun buildToggle(): View = card { card ->
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                getString(R.string.schedule_title),
                getString(R.string.schedule_toggle_subtitle),
                CapabilityRegistry.isEnabled(this, Capabilities.SCHEDULES)
            ) { value ->
                if (!CapabilityRegistry.setEnabled(this, Capabilities.SCHEDULES, value)) {
                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                }
                refresh()
            }
        )

        ScheduleManager.activeWindowIfEnabled(this)?.let { window ->
            card.addView(FocusUi.spacer(this, 8))
            val label = if (window.overlay) {
                getString(R.string.schedule_overlaying_until, ScheduleManager.formatTime(window.endMinutes))
            } else {
                getString(R.string.schedule_running_until, ScheduleManager.formatTime(window.endMinutes))
            }
            card.addView(FocusUi.pill(this, tokens, label, if (window.overlay) tokens.warning else tokens.accent))
        }

        card.addView(FocusUi.spacer(this, 10))
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                getString(R.string.common_always_allowed_apps_title),
                getString(R.string.schedule_always_allowed_subtitle, AppRules.alwaysAllowedRaw(this).size),
                trailing = FocusUi.chevron(this, tokens)
            ) {
                startActivity(android.content.Intent(this, AlwaysAllowedActivity::class.java))
            }
        )
    }

    private fun buildWindowList(): View = card { card ->
        val schedules = ScheduleManager.getSchedules(this)

        if (schedules.isEmpty()) {
            card.addView(FocusUi.emptyState(this, tokens, Copy.emptySchedules(this)))
        } else {
            val active = ScheduleManager.activeWindowIfEnabled(this)?.id
            schedules.forEachIndexed { index, schedule ->
                card.addView(
                    FocusUi.listRow(
                        this,
                        tokens,
                        getString(
                            R.string.schedule_window_time_range,
                            ScheduleManager.formatTime(schedule.startMinutes),
                            ScheduleManager.formatTime(schedule.endMinutes)
                        ),
                        describe(schedule),
                        trailing = if (schedule.id == active) {
                            FocusUi.pill(this, tokens, getString(R.string.common_now), tokens.accent)
                        } else {
                            FocusUi.chevron(this, tokens)
                        }
                    ) { editWindow(schedule) }
                )
                if (index < schedules.size - 1) card.addView(FocusUi.divider(this, tokens))
            }
        }

        card.addView(FocusUi.spacer(this, 12))
        card.addView(FocusUi.primaryButton(this, tokens, getString(R.string.schedule_add_window)) { editWindow(null) })
    }

    private fun describe(schedule: ScheduleWindow): String {
        val repeat = when (schedule.repeat) {
            RepeatType.DAILY -> getString(R.string.common_every_day)
            RepeatType.WEEKLY ->
                if (schedule.daysOfWeek.isEmpty()) {
                    getString(R.string.common_every_day)
                } else {
                    schedule.daysOfWeek.sorted().joinToString { dayName(it) }
                }
            RepeatType.MONTHLY -> getString(R.string.schedule_monthly_repeat, schedule.dayOfMonth)
        }
        val extras = if (schedule.allowedApps.isEmpty()) {
            getString(R.string.schedule_extras_essentials_only)
        } else {
            getString(R.string.schedule_extras_count, schedule.allowedApps.size)
        }
        val overlay = if (schedule.overlay) " · " + getString(R.string.schedule_overlay_marker) else ""
        val message = if (schedule.message.isBlank()) "" else schedule.message + " · "
        return message + repeat + " · " + extras + overlay
    }

    private fun dayName(day: Int): String = when (day) {
        Calendar.MONDAY -> getString(R.string.common_day_mon)
        Calendar.TUESDAY -> getString(R.string.common_day_tue)
        Calendar.WEDNESDAY -> getString(R.string.common_day_wed)
        Calendar.THURSDAY -> getString(R.string.common_day_thu)
        Calendar.FRIDAY -> getString(R.string.common_day_fri)
        Calendar.SATURDAY -> getString(R.string.common_day_sat)
        else -> getString(R.string.common_day_sun)
    }

    // ── Editing ───────────────────────────────────────────────────

    private fun editWindow(existing: ScheduleWindow?) {
        if (SessionLock.isFrozen(this)) {
            FocusDialog.toast(this, SessionLock.refusalMessage(this))
            return
        }
        var start = existing?.startMinutes ?: (9 * 60)
        var end = existing?.endMinutes ?: (11 * 60)
        var repeat = existing?.repeat ?: RepeatType.DAILY
        var days = existing?.daysOfWeek?.toSet() ?: emptySet()
        var dayOfMonth = existing?.dayOfMonth ?: 1
        var message = existing?.message.orEmpty()
        var allowedApps = existing?.allowedApps ?: emptySet()
        var overlay = existing?.overlay ?: false

        FocusDialog.custom(
            this,
            title = if (existing == null) getString(R.string.schedule_new_window_title) else getString(R.string.schedule_edit_window_title),
            subtitle = getString(R.string.schedule_edit_subtitle),
            confirmLabel = getString(R.string.common_save),
            cancelLabel = getString(R.string.common_cancel),
            onConfirm = {
                if (repeat == RepeatType.WEEKLY && days.isEmpty()) {
                    FocusDialog.toast(this, getString(R.string.schedule_pick_day_toast))
                } else {
                    val window = existing?.copy(
                        startMinutes = start,
                        endMinutes = end,
                        repeat = repeat,
                        daysOfWeek = days.toList(),
                        dayOfMonth = dayOfMonth,
                        message = message,
                        allowedApps = allowedApps,
                        overlay = overlay
                    ) ?: ScheduleManager.newSchedule(
                        startMinutes = start,
                        endMinutes = end,
                        repeat = repeat,
                        daysOfWeek = days.toList(),
                        dayOfMonth = dayOfMonth,
                        message = message,
                        allowedApps = allowedApps,
                        overlay = overlay
                    )
                    if (existing == null) {
                        ScheduleManager.addSchedule(this, window)
                    } else {
                        ScheduleManager.updateSchedule(this, window)
                    }
                    refresh()
                }
            }
        ) { body, dialogTokens ->

            val startRow = FocusUi.listRow(
                this,
                dialogTokens,
                getString(R.string.common_starts_label),
                ScheduleManager.formatTime(start),
                trailing = FocusUi.chevron(this, dialogTokens)
            ) {
                FocusDialog.timePicker(this, getString(R.string.common_starts_at), start) { value ->
                    start = value
                    FocusDialog.toast(this, getString(R.string.schedule_starts_toast, ScheduleManager.formatTime(value)))
                }
            }
            body.addView(startRow)

            body.addView(
                FocusUi.listRow(
                    this,
                    dialogTokens,
                    getString(R.string.common_ends_label),
                    ScheduleManager.formatTime(end),
                    trailing = FocusUi.chevron(this, dialogTokens)
                ) {
                    FocusDialog.timePicker(this, getString(R.string.common_ends_at), end) { value ->
                        end = value
                        FocusDialog.toast(this, getString(R.string.schedule_ends_toast, ScheduleManager.formatTime(value)))
                    }
                }
            )

            body.addView(FocusUi.divider(this, dialogTokens, 8))
            body.addView(FocusUi.caption(this, dialogTokens, getString(R.string.schedule_caption_repeats)))
            RepeatType.values().forEach { type ->
                val marker = FocusUi.pill(
                    this,
                    dialogTokens,
                    if (type == repeat) getString(R.string.common_now) else getString(R.string.common_set),
                    if (type == repeat) dialogTokens.accent else dialogTokens.textMuted
                )
                body.addView(
                    FocusUi.listRow(this, dialogTokens, repeatLabel(type), null, trailing = marker) {
                        repeat = type
                        if (type == RepeatType.WEEKLY) pickDays(days) { days = it }
                        if (type == RepeatType.MONTHLY) pickDayOfMonth(dayOfMonth) { dayOfMonth = it }
                    }
                )
            }

            body.addView(FocusUi.divider(this, dialogTokens, 8))
            body.addView(
                FocusUi.listRow(
                    this,
                    dialogTokens,
                    getString(R.string.schedule_extra_apps_allowed_title),
                    getString(R.string.common_chosen_count, allowedApps.size),
                    trailing = FocusUi.chevron(this, dialogTokens)
                ) {
                    pickApps(
                        title = getString(R.string.schedule_pick_allowed_title),
                        subtitle = getString(R.string.schedule_pick_allowed_subtitle),
                        selected = allowedApps
                    ) { selected -> allowedApps = selected }
                }
            )

            body.addView(
                FocusUi.listRow(
                    this,
                    dialogTokens,
                    getString(R.string.schedule_what_it_says_title),
                    message.ifBlank { getString(R.string.schedule_nothing_yet) },
                    trailing = FocusUi.chevron(this, dialogTokens)
                ) {
                    FocusDialog.textInput(
                        this,
                        getString(R.string.schedule_what_says_title),
                        getString(R.string.schedule_what_says_subtitle),
                        getString(R.string.schedule_what_says_hint),
                        message
                    ) { value -> message = value }
                }
            )

            body.addView(FocusUi.divider(this, dialogTokens, 8))
            body.addView(
                FocusUi.toggleRow(
                    this,
                    dialogTokens,
                    getString(R.string.schedule_overlay_toggle_title),
                    getString(R.string.schedule_overlay_toggle_subtitle),
                    overlay
                ) { value -> overlay = value }
            )

            if (existing != null) {
                body.addView(FocusUi.divider(this, dialogTokens, 8))
                body.addView(
                    FocusUi.dangerButton(this, dialogTokens, getString(R.string.schedule_delete_window)) {
                        ScheduleManager.removeSchedule(this, existing.id)
                        refresh()
                    }
                )
            }
        }
    }

    private fun repeatLabel(type: RepeatType): String = when (type) {
        RepeatType.DAILY -> getString(R.string.common_every_day)
        RepeatType.WEEKLY -> getString(R.string.schedule_repeat_certain_days)
        RepeatType.MONTHLY -> getString(R.string.schedule_repeat_once_a_month)
    }

    private fun pickDays(current: Set<Int>, onSave: (Set<Int>) -> Unit) {
        val days = listOf(
            Calendar.MONDAY to getString(R.string.common_day_monday),
            Calendar.TUESDAY to getString(R.string.common_day_tuesday),
            Calendar.WEDNESDAY to getString(R.string.common_day_wednesday),
            Calendar.THURSDAY to getString(R.string.common_day_thursday),
            Calendar.FRIDAY to getString(R.string.common_day_friday),
            Calendar.SATURDAY to getString(R.string.common_day_saturday),
            Calendar.SUNDAY to getString(R.string.common_day_sunday)
        )
        FocusDialog.multiChoice(
            this,
            getString(R.string.common_which_days_title),
            null,
            days.map { FocusDialog.Choice(it.first.toString(), it.second) },
            current.map { it.toString() }.toSet()
        ) { selected ->
            onSave(selected.mapNotNull { it.toIntOrNull() }.toSet())
        }
    }

    private fun pickDayOfMonth(current: Int, onSave: (Int) -> Unit) {
        FocusDialog.textInput(
            this,
            getString(R.string.schedule_which_day_of_month_title),
            getString(R.string.schedule_day_of_month_subtitle),
            getString(R.string.schedule_day_hint),
            current.toString(),
            numeric = true
        ) { value ->
            val parsed = value.toIntOrNull()
            if (parsed == null || parsed !in 1..31) {
                FocusDialog.toast(this, getString(R.string.schedule_day_range_toast))
            } else {
                onSave(parsed)
            }
        }
    }

    // ── Plan ──────────────────────────────────────────────────────

    /** A place to write down what the windows are actually for. */
    private fun buildPlanCard(): View = card { card ->
        card.addView(FocusUi.secondary(this, tokens, getString(R.string.schedule_plan_intro)))
        card.addView(FocusUi.spacer(this, 10))

        val field = FocusUi.input(
            this,
            tokens,
            getString(R.string.schedule_plan_hint),
            FocusStore.getString(this, Constants.KEY_PLAN_TEXT, ""),
            multiline = true
        )
        card.addView(field)
        card.addView(
            FocusUi.secondaryButton(this, tokens, getString(R.string.schedule_save_plan_button)) {
                FocusStore.setString(this, Constants.KEY_PLAN_TEXT, field.text.toString())
                FocusDialog.toast(this, getString(R.string.common_saved_toast))
            }
        )
    }
}
