package com.focuslock.mdm

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

/**
 * A schedule window, edited in place on one screen instead of a
 * [FocusDialog.custom] sheet - the design doc's "input panels" direction,
 * and the actual fix for a Save button that dialog's growing body could
 * push off screen (see [FocusEditorActivity]).
 *
 * "When" is the one field worth an accordion: tapping it opens a Starts/Ends
 * selector and a stepper right under the row, instead of handing off to a
 * separate time-picker dialog. Every other field here already updates the
 * moment it changes, since there is no sheet to go stale underneath it.
 */
class ScheduleEditorActivity : FocusEditorActivity() {

    private var existingId: String? = null
    private var start = 9 * 60
    private var end = 11 * 60
    private var repeat = RepeatType.DAILY
    private var days = emptySet<Int>()
    private var dayOfMonth = 1
    private var message = ""
    private var allowedApps = emptySet<String>()
    private var overlay = false

    private var whenExpanded = false
    private var editingEnd = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (SessionLock.isFrozen(this)) {
            super.onCreate(savedInstanceState)
            FocusDialog.toast(this, SessionLock.refusalMessage(this))
            finish()
            return
        }
        existingId = intent.getStringExtra(EXTRA_SCHEDULE_ID)
        existingId?.let { id ->
            ScheduleManager.getSchedules(this).firstOrNull { it.id == id }?.let { window ->
                start = window.startMinutes
                end = window.endMinutes
                repeat = window.repeat
                days = window.daysOfWeek.toSet()
                dayOfMonth = window.dayOfMonth
                message = window.message
                allowedApps = window.allowedApps
                overlay = window.overlay
            }
        }
        super.onCreate(savedInstanceState)
    }

    override fun editorTitle(): String =
        if (existingId == null) getString(R.string.schedule_new_window_title) else getString(R.string.schedule_edit_window_title)

    override fun canSave(): Boolean = repeat != RepeatType.WEEKLY || days.isNotEmpty()

    override fun onSave() {
        if (!canSave()) {
            FocusDialog.toast(this, getString(R.string.schedule_pick_day_toast))
            return
        }
        val existing = existingId?.let { id -> ScheduleManager.getSchedules(this).firstOrNull { it.id == id } }
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
        val saved = if (existing == null) ScheduleManager.addSchedule(this, window) else ScheduleManager.updateSchedule(this, window)
        if (!saved) {
            FocusDialog.toast(this, SessionLock.refusalMessage(this))
            return
        }
        finish()
    }

    override fun buildContent(column: LinearLayout) {
        val messageField = FocusUi.input(this, tokens, getString(R.string.schedule_what_says_hint), message)
        messageField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                message = s?.toString().orEmpty()
            }
        })
        column.addView(messageField)

        val caption = FocusUi.caption(this, tokens, getString(R.string.schedule_shows_on_block_caption))
        caption.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = FocusUi.dp(this@ScheduleEditorActivity, 4) }
        column.addView(caption)

        column.addView(FocusUi.spacer(this, 14))
        column.addView(buildWhenCard())
        column.addView(buildSettingsCard())

        if (existingId != null) {
            column.addView(FocusUi.spacer(this, 8))
            column.addView(
                FocusUi.ghostButton(this, tokens, getString(R.string.schedule_delete_window)) {
                    ScheduleManager.removeSchedule(this, existingId!!)
                    finish()
                }.apply { setTextColor(tokens.danger) }
            )
        }
    }

    // ── When (edit in place) ─────────────────────────────────────

    private fun buildWhenCard(): View = card { c ->
        c.addView(
            FocusUi.listRow(
                this,
                tokens,
                getString(R.string.common_when_label),
                null,
                trailing = whenTrailing()
            ) {
                whenExpanded = !whenExpanded
                refresh()
            }
        )

        if (whenExpanded) {
            c.addView(FocusUi.divider(this, tokens, 6))
            c.addView(FocusUi.spacer(this, 12))
            c.addView(buildStartsEndsRow())
            c.addView(FocusUi.spacer(this, 10))
            c.addView(buildTimeStepperRow())
            if (repeat == RepeatType.WEEKLY) {
                c.addView(FocusUi.spacer(this, 12))
                c.addView(buildDayPills())
            }
        }
    }

    private fun whenTrailing(): View {
        val wrap = FocusUi.row(this)
        wrap.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        wrap.addView(
            FocusUi.valueLabel(
                this,
                tokens,
                getString(R.string.schedule_window_time_range, ScheduleManager.formatTime(start), ScheduleManager.formatTime(end))
            )
        )
        wrap.addView(FocusUi.spacerH(this, 8))
        wrap.addView(FocusUi.chevron(this, tokens))
        return wrap
    }

    private fun buildStartsEndsRow(): View {
        val row = FocusUi.row(this)
        row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        row.addView(buildTimeTargetCard(getString(R.string.common_starts_label), start, !editingEnd) { editingEnd = false; refresh() })
        row.addView(FocusUi.spacerH(this, 10))
        row.addView(buildTimeTargetCard(getString(R.string.common_ends_label), end, editingEnd) { editingEnd = true; refresh() })
        return row
    }

    private fun buildTimeTargetCard(label: String, minutes: Int, active: Boolean, onClick: () -> Unit): View {
        val box = FocusUi.column(this)
        box.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        val pad = FocusUi.dp(this, 13)
        box.setPadding(pad, pad, pad, pad)
        box.background = if (active) {
            FocusUi.roundedShape(this, tokens.accentSoft, 15, tokens.accent, strokeWidthDp = 2)
        } else {
            FocusUi.roundedShape(this, tokens.surfaceAlt, 15)
        }
        box.isClickable = true
        box.isFocusable = true
        box.setOnClickListener { onClick() }

        val overline = TextView(this)
        overline.text = label.uppercase()
        overline.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(10.5f))
        overline.setTextColor(if (active) tokens.accent else tokens.textMuted)
        FocusUi.applyFont(overline, tokens, mono = true, weight = 600)
        overline.letterSpacing = 0.09f
        box.addView(overline)

        val value = TextView(this)
        value.text = ScheduleManager.formatTime(minutes)
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(24f))
        value.setTextColor(if (active) tokens.textPrimary else tokens.textSecondary)
        FocusUi.applyFont(value, tokens, mono = true, weight = 500)
        value.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = FocusUi.dp(this@ScheduleEditorActivity, 6) }
        box.addView(value)

        return box
    }

    private fun buildTimeStepperRow(): View {
        val row = FocusUi.row(this)
        row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val current = if (editingEnd) end else start
        row.addView(buildStepperBox((current / 60).toString().padStart(2, '0'), 60))
        row.addView(FocusUi.spacerH(this, 8))
        val colon = TextView(this)
        colon.text = ":"
        colon.gravity = Gravity.CENTER
        colon.setTextColor(tokens.textMuted)
        FocusUi.applyFont(colon, tokens, mono = true, weight = 500)
        colon.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(18f))
        row.addView(colon)
        row.addView(FocusUi.spacerH(this, 8))
        row.addView(buildStepperBox((current % 60).toString().padStart(2, '0'), 5))
        return row
    }

    private fun buildStepperBox(valueText: String, stepMinutes: Int): View {
        val box = FocusUi.row(this)
        box.layoutParams = LinearLayout.LayoutParams(0, FocusUi.dp(this, 52), 1f)
        box.background = FocusUi.roundedShape(this, tokens.surfaceAlt, 14)
        val pad = FocusUi.dp(this, 10)
        box.setPadding(pad, 0, pad, 0)
        box.addView(stepGlyph("▾") { adjustEditingTime(-stepMinutes) })
        val value = TextView(this)
        value.text = valueText
        value.gravity = Gravity.CENTER
        value.setTextColor(tokens.textPrimary)
        FocusUi.applyFont(value, tokens, mono = true, weight = 500)
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(20f))
        value.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        box.addView(value)
        box.addView(stepGlyph("▴") { adjustEditingTime(stepMinutes) })
        return box
    }

    private fun stepGlyph(glyph: String, onClick: () -> Unit): View {
        val view = TextView(this)
        view.text = glyph
        view.gravity = Gravity.CENTER
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(12f))
        view.setTextColor(tokens.textSecondary)
        view.isClickable = true
        view.isFocusable = true
        val pad = FocusUi.dp(this, 8)
        view.setPadding(pad, pad, pad, pad)
        view.setOnClickListener { onClick() }
        return view
    }

    /** Wraps within the day, so stepping past midnight rolls to the other end instead of going negative. */
    private fun adjustEditingTime(deltaMinutes: Int) {
        val current = if (editingEnd) end else start
        val next = ((current + deltaMinutes) % 1_440 + 1_440) % 1_440
        if (editingEnd) end = next else start = next
        refresh()
    }

    private fun buildDayPills(): View {
        val row = FocusUi.row(this)
        row.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        val order = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )
        order.forEachIndexed { index, day ->
            val active = day in days
            val pill = TextView(this)
            pill.text = dayName(day)
            pill.gravity = Gravity.CENTER
            pill.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(11.5f))
            pill.setTextColor(if (active) tokens.onAccent else tokens.textMuted)
            FocusUi.applyFont(pill, tokens, weight = if (active) 600 else 500)
            pill.background = FocusUi.roundedShape(this, if (active) tokens.accent else tokens.surfaceAlt, 11)
            pill.layoutParams = LinearLayout.LayoutParams(0, FocusUi.dp(this, 36), 1f).apply {
                if (index < order.size - 1) marginEnd = FocusUi.dp(this@ScheduleEditorActivity, 6)
            }
            pill.isClickable = true
            pill.isFocusable = true
            pill.setOnClickListener {
                days = if (active) days - day else days + day
                refresh()
            }
            row.addView(pill)
        }
        return row
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

    // ── Everything else ──────────────────────────────────────────

    private fun buildSettingsCard(): View = card { c ->
        c.addView(
            FocusUi.listRow(
                this,
                tokens,
                getString(R.string.schedule_extra_apps_allowed_title),
                getString(R.string.common_chosen_count, allowedApps.size),
                trailing = FocusUi.chevron(this, tokens)
            ) {
                pickApps(
                    title = getString(R.string.schedule_pick_allowed_title),
                    subtitle = getString(R.string.schedule_pick_allowed_subtitle),
                    selected = allowedApps
                ) { selected -> allowedApps = selected; refresh() }
            }
        )

        c.addView(FocusUi.divider(this, tokens))
        c.addView(
            FocusUi.listRow(
                this,
                tokens,
                getString(R.string.schedule_repeats_row_title),
                repeatLabel(repeat),
                trailing = FocusUi.chevron(this, tokens)
            ) { pickRepeat() }
        )

        c.addView(FocusUi.divider(this, tokens))
        c.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                getString(R.string.schedule_overlay_toggle_title),
                getString(R.string.schedule_overlay_toggle_subtitle),
                overlay
            ) { value -> overlay = value }
        )
    }

    private fun repeatLabel(type: RepeatType): String = when (type) {
        RepeatType.DAILY -> getString(R.string.common_every_day)
        RepeatType.WEEKLY -> getString(R.string.schedule_repeat_certain_days)
        RepeatType.MONTHLY -> getString(R.string.schedule_repeat_once_a_month)
    }

    private fun pickRepeat() {
        val choices = RepeatType.values().map { type ->
            FocusDialog.Choice(type.name, repeatLabel(type))
        }
        FocusDialog.singleChoice(
            this,
            title = getString(R.string.schedule_caption_repeats),
            subtitle = null,
            choices = choices,
            selectedKey = repeat.name
        ) { key ->
            repeat = RepeatType.valueOf(key)
            if (repeat == RepeatType.MONTHLY) {
                pickDayOfMonth()
            } else {
                if (repeat == RepeatType.WEEKLY) whenExpanded = true
                refresh()
            }
        }
    }

    private fun pickDayOfMonth() {
        FocusDialog.textInput(
            this,
            getString(R.string.schedule_which_day_of_month_title),
            getString(R.string.schedule_day_of_month_subtitle),
            getString(R.string.schedule_day_hint),
            dayOfMonth.toString(),
            numeric = true
        ) { value ->
            val parsed = value.toIntOrNull()
            if (parsed == null || parsed !in 1..31) {
                FocusDialog.toast(this, getString(R.string.schedule_day_range_toast))
            } else {
                dayOfMonth = parsed
            }
            refresh()
        }
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"
    }
}
