package com.focuslock.mdm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.LinearLayout

/**
 * What the modes actually do, and how to disagree with them.
 *
 * This screen exists because the app changed shape: a mode used to be a cage,
 * and is now a template you can edit. That is a better deal, but only if the
 * person can find out how it works — including the parts that are unpleasant
 * to write down, like what happens when you lock yourself into Kiosk and mean
 * it. Bullets, not paragraphs: this gets read by someone who is mid-problem.
 */
class AdvancedHelpActivity : FocusScreenActivity() {

    private val stopCommand =
        "adb shell am broadcast -a com.focuslock.mdm.ADB_COMMAND " +
            "--es cmd stop_session --ez confirm true"

    override fun screenTitle(): String = "Advanced, explained"

    override fun screenSubtitle(): String =
        "What each mode sets, what you can change, and how to get out."

    override fun buildContent(column: LinearLayout) {
        column.addView(buildIdeaCard())
        column.addView(sectionLabel("The four modes"))
        FocusMode.values().forEach { column.addView(buildModeCard(it)) }
        column.addView(sectionLabel("Ending a session"))
        column.addView(buildEndEarlyCard())
        column.addView(sectionLabel("Breaks"))
        column.addView(buildBreakCard())
        column.addView(sectionLabel("If you are locked in and need out"))
        column.addView(buildEscapeCard())
    }

    private fun buildIdeaCard(): View = card { card ->
        card.addView(FocusUi.heading(this, tokens, "A mode is a starting point"))
        card.addView(FocusUi.spacer(this, 8))
        card.addView(
            FocusUi.body(
                this,
                tokens,
                "Picking a mode loads a set of switches. It does not lock those switches. " +
                    "Every one of them is in You → Advanced, and whatever you set there is what runs."
            )
        )
        card.addView(FocusUi.spacer(this, 10))
        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                "Your changes stick. The template is only re-loaded when you pick a different " +
                    "mode, which is the one moment you have clearly asked for it."
            )
        )
    }

    private fun buildModeCard(mode: FocusMode): View = card { card ->
        card.addView(FocusUi.heading(this, tokens, mode.label))
        card.addView(FocusUi.spacer(this, 6))
        card.addView(FocusUi.secondary(this, tokens, mode.oneLiner))
        card.addView(FocusUi.spacer(this, 8))
        val preset = FocusUi.caption(this, tokens, mode.presetSummary())
        preset.setTextColor(tokens.accent)
        card.addView(preset)

        if (mode.isHard) {
            card.addView(FocusUi.spacer(this, 8))
            card.addView(
                FocusUi.caption(
                    this,
                    tokens,
                    "Kiosk is the only mode that pins the phone to FocusLock and closes the " +
                        "launcher. It needs Device Owner."
                )
            )
        }
    }

    private fun buildEndEarlyCard(): View = card { card ->
        card.addView(
            FocusUi.body(
                this,
                tokens,
                "\"End a session early\" is a switch in You → Advanced, not a property of the mode."
            )
        )
        card.addView(FocusUi.spacer(this, 12))

        bullet(card, "On", "An \"End this session\" button appears on the Focus tab. Everything unlocks straight away, and the time you already did still counts.")
        bullet(card, "Off", "No button. The session runs until its timer ends.")
        bullet(
            card,
            "Kiosk",
            "Loads with it off, because that is what choosing Kiosk means. You can turn it back " +
                "on — the switch is not hidden from you — but be honest with yourself about " +
                "whether you are turning it on now or turning it on later at 1am."
        )

        card.addView(FocusUi.spacer(this, 10))
        card.addView(
            FocusUi.caption(
                this,
                tokens,
                "Turning it off does not survive being forgotten: the timer still ends on its own."
            )
        )
    }

    private fun buildBreakCard(): View = card { card ->
        card.addView(
            FocusUi.body(
                this,
                tokens,
                "A break unlocks one blocked app for a few minutes, then re-locks it by itself."
            )
        )
        card.addView(FocusUi.spacer(this, 12))
        bullet(card, "Why it exists", "The alternative is abandoning the whole session for one thing you needed. A bounded exception keeps the rest of the session alive.")
        bullet(card, "Taking one is not a failure", "It is a feature you switched on deliberately. Nothing counts it against you, and no streak breaks.")
        bullet(card, "Your numbers", "Length and how many a day are yours to set, in You → Advanced or on the Daily limits screen.")
    }

    private fun buildEscapeCard(): View = card { card ->
        card.addView(
            FocusUi.body(
                this,
                tokens,
                "In order, least destructive first."
            )
        )
        card.addView(FocusUi.spacer(this, 12))

        bullet(card, "1. Wait", "Every session has a timer. When it ends, everything unlocks on its own. This is the intended exit and costs you nothing.")
        bullet(card, "2. Turn the early exit back on", "You → Advanced → \"End a session early\". Works in every mode, including Kiosk.")
        bullet(card, "3. From a computer", "If the phone is pinned and you cannot reach Advanced, one ADB command ends the session. It refuses to run without the confirm flag, so it cannot happen by accident.")

        card.addView(FocusUi.spacer(this, 10))
        card.addView(commandBlock(stopCommand))
        card.addView(FocusUi.spacer(this, 12))

        bullet(
            card,
            "4. Factory reset",
            "The last resort, and the only one that always works. It releases Device Owner and " +
                "wipes the phone. FocusLock never blocks it — the button is on the You tab, " +
                "behind a countdown."
        )
    }

    private fun bullet(card: LinearLayout, title: String, body: String) {
        val heading = FocusUi.body(this, tokens, title)
        heading.setTextColor(tokens.textPrimary)
        card.addView(heading)
        card.addView(FocusUi.secondary(this, tokens, body))
        card.addView(FocusUi.spacer(this, 12))
    }

    private fun commandBlock(command: String): View {
        val holder = FocusUi.column(this, 0)
        val text = FocusUi.body(this, tokens, command)
        text.setTextColor(tokens.accent)
        text.background = FocusUi.roundedShape(this, tokens.input, tokens.radiusDp, tokens.divider, 1)
        val pad = FocusUi.dp(this, 12)
        text.setPadding(pad, pad, pad, pad)
        holder.addView(text)
        holder.addView(FocusUi.spacer(this, 8))
        holder.addView(
            FocusUi.secondaryButton(this, tokens, "Copy the command") {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("FocusLock", command))
                FocusDialog.toast(this, "Copied.")
            }
        )
        return holder
    }
}
