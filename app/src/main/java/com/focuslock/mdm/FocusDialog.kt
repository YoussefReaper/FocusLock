package com.focuslock.mdm

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Themed dialogs.
 *
 * The old build used `AlertDialog.Builder`, which meant every confirmation and
 * every app picker ignored the user's theme entirely — the single most visible
 * hole in the old design. These are plain [Dialog]s wearing a [FocusUi] card,
 * so a dialog looks like the screen that opened it, in every theme, at every
 * text scale.
 */
object FocusDialog {

    /**
     * A bottom sheet, not a centred card (2026-09 design system pass): pinned
     * to the bottom edge, top corners only. All four dialog "shapes" - alert,
     * singleChoice, the custom per-app sheet, textInput+banner+toast - ride
     * this one shell, so this is the one change that reshapes every dialog
     * in the app at once.
     */
    private fun shell(context: Context, tokens: UiPrefs.Tokens): Pair<Dialog, LinearLayout> {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val card = LinearLayout(context)
        card.orientation = LinearLayout.VERTICAL
        val padH = FocusUi.dp(context, 20)
        card.setPadding(padH, FocusUi.dp(context, 22), padH, FocusUi.dp(context, 20))
        card.background = FocusUi.cornersShape(
            context,
            tokens.surface,
            topLeftDp = 28,
            topRightDp = 28,
            bottomRightDp = 0,
            bottomLeftDp = 0
        )

        dialog.setContentView(card)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(if (tokens.isLight) 0.35f else 0.6f)
            setGravity(Gravity.BOTTOM)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            attributes = attributes.apply {
                windowAnimations = android.R.style.Animation_InputMethod // slides from the bottom, no platform dialog fade
            }
        }
        return dialog to card
    }

    /** Heading role (19/25/700) - the dialog title, distinct from a screen's own larger title. */
    private fun addTitle(context: Context, tokens: UiPrefs.Tokens, card: LinearLayout, title: String) {
        if (title.isBlank()) return
        card.addView(FocusUi.heading(context, tokens, title))
    }

    private fun addMessage(
        context: Context,
        tokens: UiPrefs.Tokens,
        card: LinearLayout,
        message: String
    ): TextView? {
        if (message.isBlank()) return null
        val view = FocusUi.secondary(context, tokens, message)
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = FocusUi.dp(context, 10) }
        card.addView(view)
        return view
    }

    private fun addActions(
        context: Context,
        tokens: UiPrefs.Tokens,
        card: LinearLayout,
        confirmLabel: String?,
        cancelLabel: String?,
        destructive: Boolean,
        onConfirm: (() -> Unit)?,
        onCancel: (() -> Unit)?,
        dialog: Dialog
    ): TextView? {
        val actions = LinearLayout(context)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.END
        actions.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = FocusUi.dp(context, 18) }

        if (cancelLabel != null) {
            val cancel = FocusUi.ghostButton(context, tokens, cancelLabel) {
                dialog.dismiss()
                onCancel?.invoke()
            }
            cancel.layoutParams = LinearLayout.LayoutParams(
                0,
                FocusUi.dp(context, tokens.density.buttonHeightDp),
                1f
            ).apply { marginEnd = FocusUi.dp(context, 10) }
            actions.addView(cancel)
        }

        var confirmView: TextView? = null
        if (confirmLabel != null) {
            val confirm = if (destructive) {
                FocusUi.dangerButton(context, tokens, confirmLabel) {
                    dialog.dismiss()
                    onConfirm?.invoke()
                }
            } else {
                FocusUi.primaryButton(context, tokens, confirmLabel) {
                    dialog.dismiss()
                    onConfirm?.invoke()
                }
            }
            confirm.layoutParams = LinearLayout.LayoutParams(
                0,
                FocusUi.dp(context, tokens.density.buttonHeightDp),
                1f
            )
            actions.addView(confirm)
            confirmView = confirm
        }

        card.addView(actions)
        return confirmView
    }

    // ── Alert ─────────────────────────────────────────────────────

    fun alert(
        context: Context,
        title: String,
        message: String,
        confirmLabel: String = "OK",
        cancelLabel: String? = null,
        destructive: Boolean = false,
        onConfirm: (() -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ): Dialog {
        val tokens = UiPrefs.resolve(context)
        val (dialog, card) = shell(context, tokens)
        addTitle(context, tokens, card, title)
        addMessage(context, tokens, card, message)
        addActions(context, tokens, card, confirmLabel, cancelLabel, destructive, onConfirm, onCancel, dialog)
        dialog.show()
        return dialog
    }

    fun info(context: Context, title: String, message: String) {
        alert(context, title, message, confirmLabel = "Got it")
    }

    /**
     * The deliberate-pause confirmation, used for anything irreversible.
     * The countdown is the point: it makes a destructive tap impossible to do
     * by reflex, without ever refusing the user their own decision.
     */
    fun confirmWithCountdown(
        context: Context,
        title: String,
        message: String,
        confirmLabel: String,
        seconds: Int,
        onConfirm: () -> Unit
    ): Dialog {
        val tokens = UiPrefs.resolve(context)
        val (dialog, card) = shell(context, tokens)
        addTitle(context, tokens, card, title)
        val messageView = addMessage(context, tokens, card, message)

        val countdownView = FocusUi.caption(context, tokens, "")
        countdownView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = FocusUi.dp(context, 12) }
        card.addView(countdownView)

        val confirm = addActions(
            context, tokens, card, confirmLabel, "Cancel", true, onConfirm, null, dialog
        )
        confirm?.isEnabled = false
        confirm?.alpha = 0.45f

        val handler = Handler(Looper.getMainLooper())
        var remaining = seconds
        val ticker = object : Runnable {
            override fun run() {
                remaining -= 1
                if (remaining <= 0) {
                    countdownView.text = "You can confirm now."
                    confirm?.isEnabled = true
                    confirm?.alpha = 1f
                    return
                }
                countdownView.text = "Confirm becomes available in " + remaining + "s."
                handler.postDelayed(this, 1_000L)
            }
        }
        countdownView.text = "Confirm becomes available in " + seconds + "s."
        handler.postDelayed(ticker, 1_000L)
        dialog.setOnDismissListener { handler.removeCallbacks(ticker) }

        messageView?.setTextColor(tokens.textSecondary)
        dialog.show()
        return dialog
    }

    // ── Choice ────────────────────────────────────────────────────

    data class Choice(
        val key: String,
        val label: String,
        val subtitle: String? = null,
        val leadingPackage: String? = null
    )

    /**
     * A searchable multi-select.
     *
     * The old picker was a raw `setMultiChoiceItems` over every installed app,
     * which on a real phone is a 200-row unlabelled list with no way to find
     * anything. Search, a live count and a select-all shortcut are what make
     * this usable rather than technically present.
     */
    fun multiChoice(
        context: Context,
        title: String,
        subtitle: String?,
        choices: List<Choice>,
        selected: Set<String>,
        confirmLabel: String = "Save",
        onSave: (Set<String>) -> Unit
    ): Dialog {
        val tokens = UiPrefs.resolve(context)
        val (dialog, card) = shell(context, tokens)
        addTitle(context, tokens, card, title)
        if (!subtitle.isNullOrBlank()) addMessage(context, tokens, card, subtitle)

        val working = selected.toMutableSet()

        val countView = FocusUi.caption(context, tokens, working.size.toString() + " selected")
        countView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = FocusUi.dp(context, 8) }

        val search = FocusUi.input(context, tokens, "Search")
        search.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = FocusUi.dp(context, 14) }
        card.addView(search)
        card.addView(countView)

        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL

        val scroll = FocusUi.scroll(context, list)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            FocusUi.dp(context, 360)
        ).apply { topMargin = FocusUi.dp(context, 6) }
        card.addView(scroll)

        fun render(query: String) {
            list.removeAllViews()
            val needle = query.trim().lowercase(Locale.getDefault())
            val visible = choices.filter { choice ->
                needle.isEmpty() ||
                    choice.label.lowercase(Locale.getDefault()).contains(needle) ||
                    choice.key.lowercase(Locale.getDefault()).contains(needle)
            }

            if (visible.isEmpty()) {
                list.addView(FocusUi.emptyState(context, tokens, "Nothing matches that."))
                return
            }

            visible.forEach { choice ->
                val control = FocusUi.switchControl(context, tokens, choice.key in working) { checked ->
                    if (checked) working.add(choice.key) else working.remove(choice.key)
                    countView.text = working.size.toString() + " selected"
                }
                val leading = choice.leadingPackage?.let { FocusUi.appIcon(context, tokens, it, 32) }
                list.addView(
                    FocusUi.listRow(
                        context,
                        tokens,
                        choice.label,
                        choice.subtitle,
                        trailing = control,
                        leading = leading
                    ) { control.isChecked = !control.isChecked }
                )
            }
        }

        render("")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                render(s?.toString().orEmpty())
            }
        })

        addActions(
            context, tokens, card, confirmLabel, "Cancel", false,
            { onSave(working.toSet()) }, null, dialog
        )
        dialog.show()
        return dialog
    }

    fun singleChoice(
        context: Context,
        title: String,
        subtitle: String?,
        choices: List<Choice>,
        selectedKey: String?,
        onSelect: (String) -> Unit
    ): Dialog {
        val tokens = UiPrefs.resolve(context)
        val (dialog, card) = shell(context, tokens)
        addTitle(context, tokens, card, title)
        if (!subtitle.isNullOrBlank()) addMessage(context, tokens, card, subtitle)

        // The policy ladder's own option row (2026-09 design system pass):
        // 62h/r16, current selection = accentSoft fill + a 1.5dp accent ring
        // + a "Now" pill, replacing the old bullet/circle glyph marker.
        val list = LinearLayout(context)
        list.orientation = LinearLayout.VERTICAL
        choices.forEach { choice ->
            val isCurrent = choice.key == selectedKey
            val option = LinearLayout(context)
            option.orientation = LinearLayout.HORIZONTAL
            option.gravity = Gravity.CENTER_VERTICAL
            val padH = FocusUi.dp(context, 15)
            option.setPadding(padH, FocusUi.dp(context, 13), padH, FocusUi.dp(context, 13))
            option.minimumHeight = FocusUi.dp(context, 62)
            option.background = FocusUi.withRipple(
                context,
                FocusUi.roundedShape(
                    context,
                    if (isCurrent) tokens.accentSoft else android.graphics.Color.TRANSPARENT,
                    tokens.rowRadiusDp,
                    if (isCurrent) tokens.accent else null,
                    strokeWidthDp = if (isCurrent) 2 else 1 // nearest whole dp to the doc's 1.5dp ring
                ),
                tokens
            )
            option.isClickable = true
            option.isFocusable = true
            option.setOnClickListener {
                dialog.dismiss()
                onSelect(choice.key)
            }

            val textColumn = LinearLayout(context)
            textColumn.orientation = LinearLayout.VERTICAL
            textColumn.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            val titleView = TextView(context)
            titleView.text = choice.label
            titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, tokens.scaled(15f))
            titleView.setTextColor(tokens.textPrimary)
            FocusUi.applyFont(titleView, tokens, weight = 600)
            textColumn.addView(titleView)
            if (!choice.subtitle.isNullOrBlank()) {
                val subtitleView = TextView(context)
                subtitleView.text = choice.subtitle
                subtitleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, tokens.scaled(13f))
                // The current option's subtitle is promoted to textSecondary
                // for extra emphasis; every other option stays at textMuted.
                subtitleView.setTextColor(if (isCurrent) tokens.textSecondary else tokens.textMuted)
                FocusUi.applyFont(subtitleView, tokens, weight = 400)
                subtitleView.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = FocusUi.dp(context, 2) }
                textColumn.addView(subtitleView)
            }
            option.addView(textColumn)

            if (isCurrent) {
                option.addView(FocusUi.spacerH(context, 10))
                option.addView(FocusUi.pill(context, tokens, "Now", tokens.accent))
            }

            list.addView(option)
            list.addView(FocusUi.spacer(context, 6))
        }

        val scroll = FocusUi.scroll(context, list)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = FocusUi.dp(context, 8) }
        card.addView(scroll)

        addActions(context, tokens, card, null, "Close", false, null, null, dialog)
        dialog.show()
        return dialog
    }

    // ── Input ─────────────────────────────────────────────────────

    fun textInput(
        context: Context,
        title: String,
        subtitle: String?,
        hint: String,
        value: String = "",
        multiline: Boolean = false,
        numeric: Boolean = false,
        confirmLabel: String = "Save",
        onSave: (String) -> Unit
    ): Dialog {
        val tokens = UiPrefs.resolve(context)
        val (dialog, card) = shell(context, tokens)
        addTitle(context, tokens, card, title)
        if (!subtitle.isNullOrBlank()) addMessage(context, tokens, card, subtitle)

        val field = FocusUi.input(context, tokens, hint, value, multiline, numeric)
        field.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = FocusUi.dp(context, 14) }
        card.addView(field)

        addActions(
            context, tokens, card, confirmLabel, "Cancel", false,
            { onSave(field.text?.toString()?.trim().orEmpty()) }, null, dialog
        )
        dialog.show()
        return dialog
    }

    /**
     * A time picker in the app's own language.
     *
     * The platform picker cannot be themed to arbitrary tokens, and a stock
     * blue wheel in the middle of a themed app is exactly the inconsistency
     * this refactor set out to remove.
     */
    fun timePicker(
        context: Context,
        title: String,
        initialMinutes: Int,
        onPick: (Int) -> Unit
    ): Dialog {
        val tokens = UiPrefs.resolve(context)
        val (dialog, card) = shell(context, tokens)
        addTitle(context, tokens, card, title)

        var hour = (initialMinutes / 60).coerceIn(0, 23)
        var minute = (initialMinutes % 60).coerceIn(0, 59)

        val display = FocusUi.display(context, tokens, format(hour, minute))
        display.gravity = Gravity.CENTER
        display.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = FocusUi.dp(context, 14)
            bottomMargin = FocusUi.dp(context, 6)
        }
        card.addView(display)

        card.addView(
            FocusUi.sliderRow(context, tokens, "Hour", 0, 23, hour, { it.toString().padStart(2, '0') }) {
                hour = it
                display.text = format(hour, minute)
            }
        )
        card.addView(
            FocusUi.sliderRow(context, tokens, "Minute", 0, 59, minute, { it.toString().padStart(2, '0') }) {
                minute = it
                display.text = format(hour, minute)
            }
        )

        addActions(
            context, tokens, card, "Set", "Cancel", false,
            { onPick(hour * 60 + minute) }, null, dialog
        )
        dialog.show()
        return dialog
    }

    private fun format(hour: Int, minute: Int): String =
        String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

    /**
     * A date and time picker in the app's own language.
     *
     * Built around "how many days from now" rather than a calendar grid, because
     * that is how people actually phrase a task deadline — tomorrow, the weekend,
     * next week — and because a themed calendar widget is a lot of surface for a
     * question with five common answers.
     */
    fun dateTimePicker(
        context: Context,
        title: String,
        initialMs: Long?,
        allowClear: Boolean = true,
        onPick: (Long?) -> Unit
    ): Dialog {
        val tokens = UiPrefs.resolve(context)
        val (dialog, card) = shell(context, tokens)
        addTitle(context, tokens, card, title)

        val calendar = java.util.Calendar.getInstance()
        if (initialMs != null && initialMs > 0L) calendar.timeInMillis = initialMs
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        val startOfToday = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        var offsetDays = (((calendar.timeInMillis - startOfToday) / 86_400_000L).toInt()).coerceIn(0, 180)
        var hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        var minute = calendar.get(java.util.Calendar.MINUTE)

        fun resolved(): Long {
            val out = java.util.Calendar.getInstance()
            out.timeInMillis = startOfToday
            out.add(java.util.Calendar.DAY_OF_YEAR, offsetDays)
            out.set(java.util.Calendar.HOUR_OF_DAY, hour)
            out.set(java.util.Calendar.MINUTE, minute)
            return out.timeInMillis
        }

        val display = FocusUi.title(context, tokens, "")
        display.gravity = Gravity.CENTER
        display.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = FocusUi.dp(context, 14)
            bottomMargin = FocusUi.dp(context, 4)
        }

        fun refreshDisplay() {
            display.text = java.text.SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault())
                .format(java.util.Date(resolved()))
        }
        refreshDisplay()
        card.addView(display)

        val quick = listOf(
            "Today" to 0,
            "Tomorrow" to 1,
            "In 3 days" to 3,
            "Next week" to 7,
            "In 2 weeks" to 14
        )
        card.addView(
            FocusUi.chipStrip(
                context,
                tokens,
                quick.map { it.first },
                quick.indexOfFirst { it.second == offsetDays }
            ) { index ->
                offsetDays = quick[index].second
                refreshDisplay()
            }
        )

        card.addView(
            FocusUi.sliderRow(context, tokens, "Days from today", 0, 180, offsetDays, {
                when (it) {
                    0 -> "Today"
                    1 -> "Tomorrow"
                    else -> "In " + it + " days"
                }
            }) { value ->
                offsetDays = value
                refreshDisplay()
            }
        )
        card.addView(
            FocusUi.sliderRow(context, tokens, "Hour", 0, 23, hour, { it.toString().padStart(2, '0') }) {
                hour = it
                refreshDisplay()
            }
        )
        card.addView(
            FocusUi.sliderRow(context, tokens, "Minute", 0, 55, minute - (minute % 5), {
                it.toString().padStart(2, '0')
            }) {
                minute = it
                refreshDisplay()
            }
        )

        if (allowClear) {
            card.addView(
                FocusUi.ghostButton(context, tokens, "No date at all") {
                    dialog.dismiss()
                    onPick(null)
                }
            )
        }

        addActions(context, tokens, card, "Set", "Cancel", false, { onPick(resolved()) }, null, dialog)
        dialog.show()
        return dialog
    }

    /** Free-form themed dialog for screens that need their own body. */
    fun custom(
        context: Context,
        title: String,
        subtitle: String? = null,
        confirmLabel: String? = "Save",
        cancelLabel: String? = "Cancel",
        onConfirm: (() -> Unit)? = null,
        build: (LinearLayout, UiPrefs.Tokens) -> Unit
    ): Dialog {
        val tokens = UiPrefs.resolve(context)
        val (dialog, card) = shell(context, tokens)
        addTitle(context, tokens, card, title)
        if (!subtitle.isNullOrBlank()) addMessage(context, tokens, card, subtitle)

        val body = LinearLayout(context)
        body.orientation = LinearLayout.VERTICAL
        build(body, tokens)

        val scroll = FocusUi.scroll(context, body)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = FocusUi.dp(context, 12) }
        card.addView(scroll)

        addActions(context, tokens, card, confirmLabel, cancelLabel, false, onConfirm, null, dialog)
        dialog.show()
        return dialog
    }

    /**
     * The one honest line shown when a safety capability is switched off.
     * It states the consequence, offers no argument, and never blocks the change.
     */
    fun weakenNotice(context: Context, spec: CapabilitySpec) {
        val tokens = UiPrefs.resolve(context)
        val (dialog, card) = shell(context, tokens)
        addTitle(context, tokens, card, spec.label + " is off")

        val note = FocusUi.body(context, tokens, Copy.weakenWarning(spec))
        note.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = FocusUi.dp(context, 12) }
        card.addView(note)

        addActions(context, tokens, card, "Understood", null, false, null, null, dialog)
        dialog.show()
    }

    fun toast(context: Context, message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    /** A themed snack-style banner, for confirmations that do not need a dialog. */
    fun banner(host: ViewGroup, tokens: UiPrefs.Tokens, message: String) {
        val context = host.context
        // r16 surfaceAlt, not a filled accent block - a banner reports
        // something, it isn't a call to action, so it shouldn't compete
        // with a primary button for the eye.
        val view = FocusUi.body(context, tokens, message)
        view.setTextColor(tokens.textSecondary)
        val padding = FocusUi.dp(context, 14)
        view.setPadding(padding, padding, padding, padding)
        view.background = FocusUi.roundedShape(context, tokens.surfaceAlt, tokens.rowRadiusDp)
        view.alpha = 0f
        host.addView(view)
        view.animate().alpha(1f).setDuration(180L).start()
        Handler(Looper.getMainLooper()).postDelayed({
            view.animate().alpha(0f).setDuration(180L).withEndAction {
                (view.parent as? ViewGroup)?.removeView(view)
            }.start()
        }, 2_600L)
    }
}
