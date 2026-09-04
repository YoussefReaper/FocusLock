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

    override fun screenTitle(): String = getString(R.string.bedtime_title)

    override fun screenSubtitle(): String = getString(R.string.bedtime_subtitle)

    override fun buildContent(column: LinearLayout) {
        column.addView(buildToggle())

        if (!CapabilityRegistry.isEnabled(this, Capabilities.BEDTIME_MODE)) return

        column.addView(sectionLabel(getString(R.string.bedtime_section_when)))
        column.addView(buildWindowCard())

        column.addView(sectionLabel(getString(R.string.bedtime_section_what_quiet)))
        column.addView(buildCategoryCard())

        column.addView(sectionLabel(getString(R.string.bedtime_section_appearance)))
        column.addView(buildAppearanceCard())
    }

    private fun buildToggle(): View = card { card ->
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                getString(R.string.bedtime_title),
                getString(R.string.bedtime_toggle_subtitle),
                CapabilityRegistry.isEnabled(this, Capabilities.BEDTIME_MODE)
            ) { value ->
                if (!CapabilityRegistry.setEnabled(this, Capabilities.BEDTIME_MODE, value)) {
                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                }
                refresh()
            }
        )

        if (Bedtime.isActive(this)) {
            card.addView(FocusUi.spacer(this, 8))
            card.addView(
                FocusUi.pill(
                    this,
                    tokens,
                    getString(R.string.bedtime_running_pill, Bedtime.formatTime(Bedtime.endMinutes(this))),
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
                getString(R.string.common_starts_label),
                Bedtime.formatTime(Bedtime.startMinutes(this)),
                trailing = FocusUi.chevron(this, tokens)
            ) {
                FocusDialog.timePicker(this, getString(R.string.bedtime_starts_picker_title), Bedtime.startMinutes(this)) { minutes ->
                    if (!Bedtime.setWindow(this, minutes, Bedtime.endMinutes(this))) {
                        FocusDialog.toast(this, SessionLock.refusalMessage(this))
                    }
                    refresh()
                }
            }
        )
        card.addView(FocusUi.divider(this, tokens))
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                getString(R.string.bedtime_lifts_label),
                Bedtime.formatTime(Bedtime.endMinutes(this)),
                trailing = FocusUi.chevron(this, tokens)
            ) {
                FocusDialog.timePicker(this, getString(R.string.bedtime_lifts_picker_title), Bedtime.endMinutes(this)) { minutes ->
                    if (!Bedtime.setWindow(this, Bedtime.startMinutes(this), minutes)) {
                        FocusDialog.toast(this, SessionLock.refusalMessage(this))
                    }
                    refresh()
                }
            }
        )
    }

    private fun buildCategoryCard(): View = card { card ->
        val blocked = Bedtime.blockedCategories(this)
        val everything = Bedtime.blocksEverything(this)

        card.addView(FocusUi.secondary(this, tokens, getString(R.string.bedtime_always_allowed_note)))
        card.addView(FocusUi.spacer(this, 8))

        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                getString(R.string.bedtime_block_everything_title),
                getString(R.string.bedtime_block_everything_subtitle),
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
        card.addView(FocusUi.sectionLabel(this, tokens, getString(R.string.bedtime_section_pick_categories)))

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
                    if (!Bedtime.setBlockedCategories(this, next)) {
                        FocusDialog.toast(this, SessionLock.refusalMessage(this))
                    }
                    refresh()
                }
            )
        }
    }

    private fun buildAppearanceCard(): View = card { card ->
        card.addView(
            FocusUi.sliderRow(
                this,
                tokens,
                getString(R.string.bedtime_dim_label),
                0,
                80,
                Bedtime.dimPercent(this),
                { if (it == 0) getString(R.string.bedtime_dim_not_at_all) else getString(R.string.bedtime_dim_percent, it) }
            ) { value ->
                Bedtime.setDimPercent(this, value)
            }
        )
        card.addView(
            FocusUi.smallButton(this, tokens, getString(R.string.bedtime_preview_dimming)) { refresh() }
        )
        card.addView(FocusUi.spacer(this, 8))
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                getString(R.string.bedtime_quiet_theme_title),
                getString(R.string.bedtime_quiet_theme_subtitle),
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
                getString(R.string.bedtime_greyscale_title),
                getString(R.string.bedtime_greyscale_subtitle),
                Bedtime.showsGrayscaleHint(this)
            ) { value ->
                Bedtime.setShowsGrayscaleHint(this, value)
            }
        )
    }
}
