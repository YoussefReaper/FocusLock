package com.focuslock.mdm

import android.view.View
import android.widget.LinearLayout

/**
 * Bedtime.
 *
 * It runs on the clock, not on a session, because the whole point is that it
 * happens on the nights you would not have thought to start one. Three levers:
 * what goes quiet, how dark the screen gets, and when it lifts — and it always
 * says when it lifts, so it never feels like a trap.
 */
class BedtimeActivity : FocusScreenActivity() {

    override fun screenTitle(): String = "Bedtime"

    override fun screenSubtitle(): String =
        "A quiet window that starts itself, every night."

    override fun buildContent(column: LinearLayout) {
        column.addView(buildToggle())

        if (!CapabilityRegistry.isEnabled(this, Capabilities.BEDTIME_MODE)) return

        column.addView(sectionLabel("When"))
        column.addView(buildWindowCard())

        column.addView(sectionLabel("What goes quiet"))
        column.addView(buildCategoryCard())

        column.addView(sectionLabel("How the screen looks"))
        column.addView(buildAppearanceCard())
    }

    private fun buildToggle(): View = card { card ->
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Bedtime",
                "Dims the screen, switches to the quiet theme and holds back the categories below.",
                CapabilityRegistry.isEnabled(this, Capabilities.BEDTIME_MODE)
            ) { value ->
                CapabilityRegistry.setEnabled(this, Capabilities.BEDTIME_MODE, value)
                refresh()
            }
        )

        if (Bedtime.isActive(this)) {
            card.addView(FocusUi.spacer(this, 8))
            card.addView(
                FocusUi.pill(
                    this,
                    tokens,
                    "Running now, lifts at " + Bedtime.formatTime(Bedtime.endMinutes(this)),
                    tokens.accent
                )
            )
        }
    }

    private fun buildWindowCard(): View = card { card ->
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                "Starts",
                Bedtime.formatTime(Bedtime.startMinutes(this)),
                trailing = FocusUi.chevron(this, tokens)
            ) {
                FocusDialog.timePicker(this, "Bedtime starts", Bedtime.startMinutes(this)) { minutes ->
                    Bedtime.setWindow(this, minutes, Bedtime.endMinutes(this))
                    refresh()
                }
            }
        )
        card.addView(FocusUi.divider(this, tokens))
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                "Lifts",
                Bedtime.formatTime(Bedtime.endMinutes(this)),
                trailing = FocusUi.chevron(this, tokens)
            ) {
                FocusDialog.timePicker(this, "Bedtime lifts", Bedtime.endMinutes(this)) { minutes ->
                    Bedtime.setWindow(this, Bedtime.startMinutes(this), minutes)
                    refresh()
                }
            }
        )
    }

    private fun buildCategoryCard(): View = card { card ->
        val blocked = Bedtime.blockedCategories(this)
        val everything = Bedtime.blocksEverything(this)

        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                "Always-allowed apps still work. An alarm is not a distraction."
            )
        )
        card.addView(FocusUi.spacer(this, 8))

        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Block everything instead",
                "Stops every app except the ones you marked always-allowed. Pick this if " +
                    "things have been slipping through the categories below.",
                everything
            ) { checked ->
                if (!Bedtime.setBlocksEverything(this, checked)) {
                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                }
                refresh()
            }
        )

        // Categories are meaningless while everything is blocked; showing them
        // live and editable would suggest they still decide something.
        if (everything) return@card

        card.addView(FocusUi.divider(this, tokens))
        card.addView(FocusUi.sectionLabel(this, tokens, "Or pick what to block"))

        AppCategory.ruleTargets.forEach { category ->
            card.addView(
                FocusUi.toggleRow(
                    this,
                    tokens,
                    category.label,
                    category.blurb,
                    category in blocked
                ) { checked ->
                    val next = if (checked) blocked + category else blocked - category
                    Bedtime.setBlockedCategories(this, next)
                }
            )
        }
    }

    private fun buildAppearanceCard(): View = card { card ->
        card.addView(
            FocusUi.sliderRow(
                this,
                tokens,
                "Dim the screen by",
                0,
                80,
                Bedtime.dimPercent(this),
                { if (it == 0) "Not at all" else it.toString() + "%" }
            ) { value ->
                Bedtime.setDimPercent(this, value)
            }
        )
        card.addView(
            FocusUi.smallButton(this, tokens, "Preview the dimming") { refresh() }
        )
        card.addView(FocusUi.spacer(this, 8))
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Switch to the quiet theme",
                "Near-black with a soft accent while bedtime runs. Your daytime theme is untouched.",
                Bedtime.forcesDarkTheme(this)
            ) { value ->
                Bedtime.setForcesDarkTheme(this, value)
                refresh()
            }
        )
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Remind me about greyscale",
                "Android's own greyscale setting takes most of the pull out of a screen at night. " +
                    "FocusLock cannot flip it for you, but it can remind you.",
                Bedtime.showsGrayscaleHint(this)
            ) { value ->
                Bedtime.setShowsGrayscaleHint(this, value)
            }
        )
    }
}
