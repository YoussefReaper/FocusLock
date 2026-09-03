package com.focuslock.mdm

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import java.util.Locale

/**
 * Per-app rules.
 *
 * This screen is the direct replacement for the old multi-choice dialog over
 * every installed package. Two things make it usable instead of merely present:
 * a real ladder of outcomes per app rather than a single on/off tick, and bulk
 * rules by category so nobody has to tap through two hundred rows to block
 * "social".
 */
class AppRulesActivity : FocusScreenActivity() {

    private var query: String = ""
    private var filter: Int = 0

    private val filters = listOf("All", "Blocked", "Paused", "Budgeted", "Open")

    override fun screenTitle(): String = "Apps"

    override fun screenSubtitle(): String =
        "Every app on this phone, and what FocusLock is allowed to do about it."

    override fun buildContent(column: LinearLayout) {
        column.addView(buildCategoryCard())
        column.addView(sectionLabel("Individual apps"))
        column.addView(buildSearch())
        column.addView(
            FocusUi.chipStrip(this, tokens, filters, filter) { index ->
                filter = index
                refresh()
            }
        )
        column.addView(buildAppList())
    }

    // ── Bulk by category ──────────────────────────────────────────

    private fun buildCategoryCard(): View = card { card ->
        card.addView(FocusUi.heading(this, tokens, "By category"))
        card.addView(FocusUi.spacer(this, 4))
        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                "A category rule covers every app in it, now and any you install later. " +
                    "A rule you set on one app always wins over its category."
            )
        )
        card.addView(FocusUi.spacer(this, 8))

        AppCategory.ruleTargets.forEach { category ->
            val current = AppRules.categoryPolicy(this, category)
            val count = AppCatalog.packagesInCategory(this, category).size
            card.addView(
                FocusUi.listRow(
                    this,
                    tokens,
                    category.label,
                    count.toString() + " apps - " + category.blurb,
                    trailing = FocusUi.pill(
                        this,
                        tokens,
                        current?.label ?: "No rule",
                        if (current == null) tokens.textMuted else policyColor(current)
                    )
                ) { pickCategoryPolicy(category, current) }
            )
        }
    }

    private fun pickCategoryPolicy(category: AppCategory, current: AppPolicy?) {
        val choices = listOf(
            FocusDialog.Choice("none", "No rule", "Apps in this category follow their own settings.")
        ) + AppPolicy.ladder.map { policy ->
            FocusDialog.Choice(policy.id, policy.label, policy.blurb)
        }

        FocusDialog.singleChoice(
            this,
            title = category.label,
            subtitle = category.blurb,
            choices = choices,
            selectedKey = current?.id ?: "none"
        ) { key ->
            if (!AppRules.setCategoryPolicy(
                    this,
                    category,
                    if (key == "none") null else AppPolicy.fromId(key)
                )
            ) {
                FocusDialog.toast(this, SessionLock.refusalMessage(this))
            }
            refresh()
        }
    }

    // ── Search and list ───────────────────────────────────────────

    private fun buildSearch(): View {
        val field = FocusUi.input(this, tokens, "Search apps", query)
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                renderList()
            }
        })
        return field
    }

    private lateinit var listHost: LinearLayout

    private fun buildAppList(): View {
        listHost = FocusUi.column(this)
        renderList()
        return listHost
    }

    private fun renderList() {
        if (!this::listHost.isInitialized) return
        listHost.removeAllViews()

        val needle = query.trim().lowercase(Locale.getDefault())
        val apps = AppCatalog.launchable(this)
            .filter { app ->
                needle.isEmpty() ||
                    app.label.lowercase(Locale.getDefault()).contains(needle) ||
                    app.packageName.lowercase(Locale.getDefault()).contains(needle)
            }
            .filter { app -> matchesFilter(AppRules.effectivePolicy(this, app.packageName)) }

        if (apps.isEmpty()) {
            listHost.addView(FocusUi.emptyState(this, tokens, "No apps match that."))
            return
        }

        val card = FocusUi.card(this, tokens)
        apps.forEachIndexed { index, app ->
            card.addView(buildAppRow(app))
            if (index < apps.size - 1) card.addView(FocusUi.divider(this, tokens))
        }
        listHost.addView(card)
    }

    private fun matchesFilter(policy: AppPolicy): Boolean = when (filter) {
        1 -> policy == AppPolicy.BLOCK || policy == AppPolicy.HIDE
        2 -> policy == AppPolicy.FRICTION
        3 -> policy == AppPolicy.LIMIT
        4 -> policy == AppPolicy.ALLOW
        else -> true
    }

    private fun buildAppRow(app: InstalledApp): View {
        val policy = AppRules.effectivePolicy(this, app.packageName)
        val explicit = AppRules.explicitPolicy(this, app.packageName) != null
        val alwaysAllowed = AppRules.isAlwaysAllowed(this, app.packageName)

        val subtitle = when {
            alwaysAllowed -> "Always allowed - nothing can lock this away"
            explicit -> app.category.label
            else -> app.category.label + " - following its category"
        }

        return FocusUi.listRow(
            this,
            tokens,
            app.label,
            subtitle,
            trailing = FocusUi.pill(this, tokens, policy.label, policyColor(policy)),
            leading = FocusUi.appIcon(this, tokens, app.packageName, 36)
        ) { openAppSheet(app) }
    }

    private fun policyColor(policy: AppPolicy): Int = when (policy) {
        AppPolicy.ALLOW -> tokens.success
        AppPolicy.FRICTION -> tokens.accent
        AppPolicy.LIMIT -> tokens.warning
        AppPolicy.BLOCK, AppPolicy.HIDE -> tokens.danger
    }

    /**
     * One sheet per app holding every decision about it: the ladder, the daily
     * budgets, the always-allowed exemption and the category correction. Having
     * them in one place is what stops "why is this still opening" confusion.
     */
    private fun openAppSheet(app: InstalledApp) {
        FocusDialog.custom(
            this,
            title = app.label,
            subtitle = app.packageName,
            confirmLabel = null,
            cancelLabel = "Done"
        ) { body, dialogTokens ->
            body.addView(FocusUi.caption(this, dialogTokens, "WHAT HAPPENS WHEN I OPEN IT"))

            val current = AppRules.effectivePolicy(this, app.packageName)
            AppPolicy.ladder.forEach { policy ->
                val marker = FocusUi.pill(
                    this,
                    dialogTokens,
                    if (policy == current) "Now" else "Set",
                    if (policy == current) dialogTokens.accent else dialogTokens.textMuted
                )
                body.addView(
                    FocusUi.listRow(this, dialogTokens, policy.label, policy.blurb, trailing = marker) {
                        val applied = AppRules.setPolicy(this, app.packageName, policy)
                        refresh()
                        FocusDialog.toast(
                            this,
                            if (applied) app.label + ": " + policy.label.lowercase()
                            else SessionLock.refusalMessage(this)
                        )
                    }
                )
            }

            body.addView(FocusUi.divider(this, dialogTokens, 8))

            val minuteLimit = AppLimits.allMinuteLimits(this)[app.packageName] ?: 0
            body.addView(
                FocusUi.listRow(
                    this,
                    dialogTokens,
                    "Daily minutes",
                    if (minuteLimit > 0) {
                        minuteLimit.toString() + " a day, " +
                            AppLimits.usedMinutesToday(this, app.packageName) + " used today"
                    } else {
                        "No budget"
                    },
                    trailing = FocusUi.chevron(this, dialogTokens)
                ) { askMinuteLimit(app) }
            )

            val openLimit = AppLimits.allOpenLimits(this)[app.packageName] ?: 0
            body.addView(
                FocusUi.listRow(
                    this,
                    dialogTokens,
                    "Daily opens",
                    if (openLimit > 0) {
                        openLimit.toString() + " a day, " +
                            AppLimits.opensToday(this, app.packageName) + " so far"
                    } else {
                        "No cap"
                    },
                    trailing = FocusUi.chevron(this, dialogTokens)
                ) { askOpenLimit(app) }
            )

            body.addView(FocusUi.divider(this, dialogTokens, 8))

            body.addView(
                FocusUi.toggleRow(
                    this,
                    dialogTokens,
                    "Always allowed",
                    "Outranks every rule, session and schedule.",
                    AppRules.isAlwaysAllowed(this, app.packageName)
                ) { checked ->
                    val current2 = AppRules.alwaysAllowedRaw(this).toMutableSet()
                    if (checked) current2.add(app.packageName) else current2.remove(app.packageName)
                    if (!AppRules.setAlwaysAllowed(this, current2)) {
                        FocusDialog.toast(this, SessionLock.refusalMessage(this))
                    }
                    refresh()
                }
            )

            body.addView(
                FocusUi.listRow(
                    this,
                    dialogTokens,
                    "Category",
                    app.category.label + " - tap to correct it",
                    trailing = FocusUi.chevron(this, dialogTokens)
                ) { pickCategory(app) }
            )

            if (AppRules.explicitPolicy(this, app.packageName) != null) {
                body.addView(
                    FocusUi.ghostButton(this, dialogTokens, "Clear this app's own rule") {
                        if (!AppRules.clearPolicy(this, app.packageName)) {
                            FocusDialog.toast(this, SessionLock.refusalMessage(this))
                        }
                        refresh()
                    }
                )
            }
        }
    }

    private fun askMinuteLimit(app: InstalledApp) {
        FocusDialog.textInput(
            this,
            title = "Daily minutes for " + app.label,
            subtitle = "Leave empty to remove the budget. It resets at midnight.",
            hint = "Minutes",
            value = (AppLimits.minuteLimit(this, app.packageName) ?: "").toString(),
            numeric = true
        ) { value ->
            AppLimits.setMinuteLimit(this, app.packageName, value.toIntOrNull())
            if (value.toIntOrNull() != null &&
                !CapabilityRegistry.isEnabled(this, Capabilities.PER_APP_LIMITS)
            ) {
                offerToEnable(Capabilities.PER_APP_LIMITS)
            }
            refresh()
        }
    }

    private fun askOpenLimit(app: InstalledApp) {
        FocusDialog.textInput(
            this,
            title = "Daily opens for " + app.label,
            subtitle = "Leave empty to remove the cap.",
            hint = "Opens",
            value = (AppLimits.openLimit(this, app.packageName) ?: "").toString(),
            numeric = true
        ) { value ->
            AppLimits.setOpenLimit(this, app.packageName, value.toIntOrNull())
            if (value.toIntOrNull() != null &&
                !CapabilityRegistry.isEnabled(this, Capabilities.OPEN_COUNT_LIMITS)
            ) {
                offerToEnable(Capabilities.OPEN_COUNT_LIMITS)
            }
            refresh()
        }
    }

    /**
     * Setting a budget while its capability is off would silently do nothing,
     * so ask rather than either failing quietly or flipping the switch for them.
     */
    private fun offerToEnable(capabilityId: String) {
        val spec = Capabilities.spec(capabilityId) ?: return
        FocusDialog.alert(
            this,
            title = "Turn on " + spec.label + "?",
            message = "You have set a budget, but " + spec.label.lowercase() +
                " is switched off, so nothing would enforce it.",
            confirmLabel = "Turn it on",
            cancelLabel = "Leave it off",
            onConfirm = {
                if (!CapabilityRegistry.setEnabled(this, capabilityId, true)) {
                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                }
                refresh()
            }
        )
    }

    private fun pickCategory(app: InstalledApp) {
        FocusDialog.singleChoice(
            this,
            title = "Category for " + app.label,
            subtitle = "Category rules and the time breakdown both use this.",
            choices = AppCategory.values().map { category ->
                FocusDialog.Choice(category.id, category.label, category.blurb)
            },
            selectedKey = app.category.id
        ) { key ->
            AppCatalog.setCategoryOverride(this, app.packageName, AppCategory.fromId(key))
            refresh()
        }
    }
}
