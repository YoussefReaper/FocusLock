package com.focuslock.mdm

import android.view.View
import android.widget.LinearLayout

/**
 * Daily budgets.
 *
 * Two different levers, because they catch two different habits: minutes catch
 * the evening that disappears, opens catch the twenty-times-an-hour reflex.
 * Both are read from Android's own usage stats, so the numbers match what the
 * phone itself reports and survive FocusLock being killed.
 */
class AppLimitsActivity : FocusScreenActivity() {

    override fun screenTitle(): String = "Daily limits"

    override fun screenSubtitle(): String =
        "Budgets that reset at midnight. Nothing is blocked until a budget runs out."

    override fun buildContent(column: LinearLayout) {
        column.addView(buildSwitches())

        if (!SetupChecks.hasUsageAccess(this)) {
            column.addView(buildUsageWarning())
        }

        column.addView(sectionLabel("Minute budgets"))
        column.addView(buildMinuteList())

        column.addView(sectionLabel("Open caps"))
        column.addView(buildOpenList())

        column.addView(sectionLabel("Take a break"))
        column.addView(buildBreakCard())
    }

    private fun buildSwitches(): View = card { card ->
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Time limits",
                "Blocks an app once its daily minutes are used.",
                CapabilityRegistry.isEnabled(this, Capabilities.PER_APP_LIMITS)
            ) { value ->
                if (!CapabilityRegistry.setEnabled(this, Capabilities.PER_APP_LIMITS, value)) {
                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                }
                refresh()
            }
        )
        card.addView(FocusUi.divider(this, tokens))
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Open caps",
                "Blocks an app once it has been opened that many times today.",
                CapabilityRegistry.isEnabled(this, Capabilities.OPEN_COUNT_LIMITS)
            ) { value ->
                if (!CapabilityRegistry.setEnabled(this, Capabilities.OPEN_COUNT_LIMITS, value)) {
                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                }
                refresh()
            }
        )
    }

    private fun buildUsageWarning(): View = card { card ->
        card.addView(FocusUi.heading(this, tokens, "Usage access is off"))
        card.addView(FocusUi.spacer(this, 6))
        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                "Budgets are measured from Android's usage records. Without access to them, " +
                    "these limits cannot count anything."
            )
        )
        card.addView(FocusUi.spacer(this, 12))
        card.addView(
            FocusUi.primaryButton(this, tokens, "Grant usage access") {
                LockManager.allowSettingsUntil(this, System.currentTimeMillis() + 2 * 60 * 1000)
                try {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (_: Exception) {
                    FocusDialog.toast(this, "That page is not available on this phone.")
                }
            }
        )
    }

    private fun buildMinuteList(): View = card { card ->
        val limits = AppLimits.allMinuteLimits(this)

        if (limits.isEmpty()) {
            card.addView(
                FocusUi.emptyState(this, tokens, "No minute budgets yet.")
            )
        } else {
            limits.entries.sortedBy { AppCatalog.label(this, it.key) }.forEach { entry ->
                val used = AppLimits.usedMinutesToday(this, entry.key)
                val fraction = if (entry.value > 0) used.toFloat() / entry.value.toFloat() else 0f
                val holder = FocusUi.column(this)
                holder.addView(
                    FocusUi.listRow(
                        this,
                        tokens,
                        AppCatalog.label(this, entry.key),
                        used.toString() + " of " + entry.value + " minutes used today",
                        trailing = FocusUi.smallButton(this, tokens, "Change") {
                            askMinutes(entry.key)
                        },
                        leading = FocusUi.appIcon(this, tokens, entry.key, 34)
                    )
                )
                holder.addView(
                    FocusUi.meter(
                        this,
                        tokens,
                        "",
                        "",
                        fraction,
                        if (fraction >= 1f) tokens.danger else tokens.accent
                    )
                )
                card.addView(holder)
            }
        }

        card.addView(FocusUi.spacer(this, 10))
        card.addView(
            FocusUi.primaryButton(this, tokens, "Add a minute budget") { pickAppFor(true) }
        )
    }

    private fun buildOpenList(): View = card { card ->
        val limits = AppLimits.allOpenLimits(this)

        if (limits.isEmpty()) {
            card.addView(FocusUi.emptyState(this, tokens, "No open caps yet."))
        } else {
            limits.entries.sortedBy { AppCatalog.label(this, it.key) }.forEach { entry ->
                card.addView(
                    FocusUi.listRow(
                        this,
                        tokens,
                        AppCatalog.label(this, entry.key),
                        AppLimits.opensToday(this, entry.key).toString() + " of " + entry.value + " opens today",
                        trailing = FocusUi.smallButton(this, tokens, "Change") { askOpens(entry.key) },
                        leading = FocusUi.appIcon(this, tokens, entry.key, 34)
                    )
                )
            }
        }

        card.addView(FocusUi.spacer(this, 10))
        card.addView(FocusUi.primaryButton(this, tokens, "Add an open cap") { pickAppFor(false) })
    }

    private fun pickAppFor(minutes: Boolean) {
        val choices = AppCatalog.launchable(this).map { app ->
            FocusDialog.Choice(app.packageName, app.label, app.category.label, app.packageName)
        }
        FocusDialog.singleChoice(
            this,
            title = if (minutes) "Which app gets a minute budget?" else "Which app gets an open cap?",
            subtitle = null,
            choices = choices,
            selectedKey = null
        ) { key ->
            if (minutes) askMinutes(key) else askOpens(key)
        }
    }

    private fun askMinutes(packageName: String) {
        FocusDialog.textInput(
            this,
            title = AppCatalog.label(this, packageName),
            subtitle = "Minutes a day. Leave empty to remove the budget.",
            hint = "Minutes",
            value = (AppLimits.allMinuteLimits(this)[packageName] ?: "").toString(),
            numeric = true
        ) { value ->
            AppLimits.setMinuteLimit(this, packageName, value.toIntOrNull())
            refresh()
        }
    }

    private fun askOpens(packageName: String) {
        FocusDialog.textInput(
            this,
            title = AppCatalog.label(this, packageName),
            subtitle = "Opens a day. Leave empty to remove the cap.",
            hint = "Opens",
            value = (AppLimits.allOpenLimits(this)[packageName] ?: "").toString(),
            numeric = true
        ) { value ->
            AppLimits.setOpenLimit(this, packageName, value.toIntOrNull())
            refresh()
        }
    }

    /**
     * The escape valve for budgets. A limit with no way to make an exception is
     * a limit people work around by uninstalling the app that set it.
     */
    private fun buildBreakCard(): View = card { card ->
        card.addView(
            FocusUi.toggleRow(
                this,
                tokens,
                "Allow breaks",
                "Lets you deliberately unlock a blocked app for a few minutes.",
                CapabilityRegistry.isEnabled(this, Capabilities.TAKE_A_BREAK)
            ) { value ->
                if (!CapabilityRegistry.setEnabled(this, Capabilities.TAKE_A_BREAK, value)) {
                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                }
                refresh()
            }
        )

        if (!CapabilityRegistry.isEnabled(this, Capabilities.TAKE_A_BREAK)) return@card

        card.addView(
            FocusUi.sliderRow(
                this,
                tokens,
                "How long a break lasts",
                1,
                30,
                TakeABreak.breakMinutes(this),
                { it.toString() + " min" }
            ) { value -> TakeABreak.setBreakMinutes(this, value) }
        )
        card.addView(
            FocusUi.sliderRow(
                this,
                tokens,
                "Breaks a day",
                0,
                10,
                TakeABreak.dailyMax(this),
                { if (it == 0) "None" else it.toString() }
            ) { value -> TakeABreak.setDailyMax(this, value) }
        )
        card.addView(
            FocusUi.caption(
                this,
                tokens,
                TakeABreak.remainingToday(this).toString() + " left today."
            )
        )
    }
}
