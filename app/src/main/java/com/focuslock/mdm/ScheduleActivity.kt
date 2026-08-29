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

    override fun screenTitle(): String = "Schedules"

    override fun screenSubtitle(): String =
        "Windows that start themselves. Useful for the hours you already know are hard."

    override fun buildContent(column: LinearLayout) {
        column.addView(buildToggle())
        if (!CapabilityRegistry.isEnabled(this, Capabilities.SCHEDULES)) return

        column.addView(sectionLabel("Windows"))
        column.addView(buildWindowList())
        column.addView(sectionLabel("Plan"))
        column.addView(buildPlanCard())
    }

    private fun buildToggle(): View = card { card ->
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Schedules",
                "With this off, the windows below are kept but none of them run.",
                CapabilityRegistry.isEnabled(this, Capabilities.SCHEDULES)
            ) { value ->
                CapabilityRegistry.setEnabled(this, Capabilities.SCHEDULES, value)
                refresh()
            }
        )

        ScheduleManager.activeWindowIfEnabled(this)?.let { window ->
            card.addView(FocusUi.spacer(this, 8))
            card.addView(
                FocusUi.pill(
                    this,
                    tokens,
                    "Running until " + ScheduleManager.formatTime(window.endMinutes),
                    tokens.accent
                )
            )
        }

        card.addView(FocusUi.spacer(this, 10))
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                "Always-allowed apps",
                AppRules.alwaysAllowedRaw(this).size.toString() +
                    " apps stay open inside every window",
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
                        ScheduleManager.formatTime(schedule.startMinutes) + " to " +
                            ScheduleManager.formatTime(schedule.endMinutes),
                        describe(schedule),
                        trailing = if (schedule.id == active) {
                            FocusUi.pill(this, tokens, "Now", tokens.accent)
                        } else {
                            FocusUi.chevron(this, tokens)
                        }
                    ) { editWindow(schedule) }
                )
                if (index < schedules.size - 1) card.addView(FocusUi.divider(this, tokens))
            }
        }

        card.addView(FocusUi.spacer(this, 12))
        card.addView(FocusUi.primaryButton(this, tokens, "Add a window") { editWindow(null) })
    }

    private fun describe(schedule: ScheduleWindow): String {
        val repeat = when (schedule.repeat) {
            RepeatType.DAILY -> "Every day"
            RepeatType.WEEKLY ->
                if (schedule.daysOfWeek.isEmpty()) {
                    "Every day"
                } else {
                    schedule.daysOfWeek.sorted().joinToString { dayName(it) }
                }
            RepeatType.MONTHLY -> "Day " + schedule.dayOfMonth + " each month"
        }
        val extras = if (schedule.allowedApps.isEmpty()) {
            "essentials only"
        } else {
            schedule.allowedApps.size.toString() + " extra apps allowed"
        }
        val message = if (schedule.message.isBlank()) "" else schedule.message + " · "
        return message + repeat + " · " + extras
    }

    private fun dayName(day: Int): String = when (day) {
        Calendar.MONDAY -> "Mon"
        Calendar.TUESDAY -> "Tue"
        Calendar.WEDNESDAY -> "Wed"
        Calendar.THURSDAY -> "Thu"
        Calendar.FRIDAY -> "Fri"
        Calendar.SATURDAY -> "Sat"
        else -> "Sun"
    }

    // ── Editing ───────────────────────────────────────────────────

    private fun editWindow(existing: ScheduleWindow?) {
        var start = existing?.startMinutes ?: (9 * 60)
        var end = existing?.endMinutes ?: (11 * 60)
        var repeat = existing?.repeat ?: RepeatType.DAILY
        var days = existing?.daysOfWeek?.toSet() ?: emptySet()
        var dayOfMonth = existing?.dayOfMonth ?: 1
        var message = existing?.message.orEmpty()
        var allowedApps = existing?.allowedApps ?: emptySet()

        FocusDialog.custom(
            this,
            title = if (existing == null) "New window" else "Edit window",
            subtitle = "Inside a window, only your always-allowed apps and the extras you pick will open.",
            confirmLabel = "Save",
            cancelLabel = "Cancel",
            onConfirm = {
                if (repeat == RepeatType.WEEKLY && days.isEmpty()) {
                    FocusDialog.toast(this, "Pick at least one day.")
                } else {
                    val window = existing?.copy(
                        startMinutes = start,
                        endMinutes = end,
                        repeat = repeat,
                        daysOfWeek = days.toList(),
                        dayOfMonth = dayOfMonth,
                        message = message,
                        allowedApps = allowedApps
                    ) ?: ScheduleManager.newSchedule(
                        startMinutes = start,
                        endMinutes = end,
                        repeat = repeat,
                        daysOfWeek = days.toList(),
                        dayOfMonth = dayOfMonth,
                        message = message,
                        allowedApps = allowedApps
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
                "Starts",
                ScheduleManager.formatTime(start),
                trailing = FocusUi.chevron(this, dialogTokens)
            ) {
                FocusDialog.timePicker(this, "Starts at", start) { value ->
                    start = value
                    FocusDialog.toast(this, "Starts " + ScheduleManager.formatTime(value))
                }
            }
            body.addView(startRow)

            body.addView(
                FocusUi.listRow(
                    this,
                    dialogTokens,
                    "Ends",
                    ScheduleManager.formatTime(end),
                    trailing = FocusUi.chevron(this, dialogTokens)
                ) {
                    FocusDialog.timePicker(this, "Ends at", end) { value ->
                        end = value
                        FocusDialog.toast(this, "Ends " + ScheduleManager.formatTime(value))
                    }
                }
            )

            body.addView(FocusUi.divider(this, dialogTokens, 8))
            body.addView(FocusUi.caption(this, dialogTokens, "REPEATS"))
            RepeatType.values().forEach { type ->
                val marker = FocusUi.pill(
                    this,
                    dialogTokens,
                    if (type == repeat) "Now" else "Set",
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
                    "Extra apps allowed",
                    allowedApps.size.toString() + " chosen",
                    trailing = FocusUi.chevron(this, dialogTokens)
                ) {
                    pickApps(
                        title = "Allowed inside this window",
                        subtitle = "Your always-allowed apps are open here regardless.",
                        selected = allowedApps
                    ) { selected -> allowedApps = selected }
                }
            )

            body.addView(
                FocusUi.listRow(
                    this,
                    dialogTokens,
                    "What it says",
                    message.ifBlank { "Nothing yet" },
                    trailing = FocusUi.chevron(this, dialogTokens)
                ) {
                    FocusDialog.textInput(
                        this,
                        "What should this window say?",
                        "It appears on the block screen. Something you would want to read.",
                        "For example: this is study time",
                        message
                    ) { value -> message = value }
                }
            )

            if (existing != null) {
                body.addView(FocusUi.divider(this, dialogTokens, 8))
                body.addView(
                    FocusUi.dangerButton(this, dialogTokens, "Delete this window") {
                        ScheduleManager.removeSchedule(this, existing.id)
                        refresh()
                    }
                )
            }
        }
    }

    private fun repeatLabel(type: RepeatType): String = when (type) {
        RepeatType.DAILY -> "Every day"
        RepeatType.WEEKLY -> "Certain days"
        RepeatType.MONTHLY -> "Once a month"
    }

    private fun pickDays(current: Set<Int>, onSave: (Set<Int>) -> Unit) {
        val days = listOf(
            Calendar.MONDAY to "Monday",
            Calendar.TUESDAY to "Tuesday",
            Calendar.WEDNESDAY to "Wednesday",
            Calendar.THURSDAY to "Thursday",
            Calendar.FRIDAY to "Friday",
            Calendar.SATURDAY to "Saturday",
            Calendar.SUNDAY to "Sunday"
        )
        FocusDialog.multiChoice(
            this,
            "Which days?",
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
            "Which day of the month?",
            "1 to 31.",
            "Day",
            current.toString(),
            numeric = true
        ) { value ->
            val parsed = value.toIntOrNull()
            if (parsed == null || parsed !in 1..31) {
                FocusDialog.toast(this, "Pick a day between 1 and 31.")
            } else {
                onSave(parsed)
            }
        }
    }

    // ── Plan ──────────────────────────────────────────────────────

    /** A place to write down what the windows are actually for. */
    private fun buildPlanCard(): View = card { card ->
        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                "What are you actually doing with this time? Writing it down is the difference " +
                    "between a blocked phone and a plan."
            )
        )
        card.addView(FocusUi.spacer(this, 10))

        val field = FocusUi.input(
            this,
            tokens,
            "This week I want to...",
            FocusStore.getString(this, Constants.KEY_PLAN_TEXT, ""),
            multiline = true
        )
        card.addView(field)
        card.addView(
            FocusUi.secondaryButton(this, tokens, "Save the plan") {
                FocusStore.setString(this, Constants.KEY_PLAN_TEXT, field.text.toString())
                FocusDialog.toast(this, "Saved.")
            }
        )
    }
}
