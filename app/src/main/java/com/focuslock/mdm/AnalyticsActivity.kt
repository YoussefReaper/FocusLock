package com.focuslock.mdm

import android.view.View
import android.widget.LinearLayout

/**
 * Where the time went.
 *
 * Information, not a verdict. There is no score, no comparison to anyone else,
 * and no leaderboard: competitive framing turns a bad week into a reason to
 * stop looking, which is the opposite of what this is for. Everything is
 * computed on the phone and stays there.
 */
class AnalyticsActivity : FocusScreenActivity() {

    private var windowIndex = 0
    private val windows = listOf("Today", "Last 7 days", "Last 30 days")

    override fun screenTitle(): String = "Your time"

    override fun screenSubtitle(): String = Copy.analyticsIntro(this)

    override fun buildContent(column: LinearLayout) {
        if (!UsageAnalytics.isEnabled(this)) {
            column.addView(buildDisabledCard())
            return
        }
        if (!SetupChecks.hasUsageAccess(this)) {
            column.addView(buildPermissionCard())
            return
        }

        column.addView(
            FocusUi.chipStrip(this, tokens, windows, windowIndex) { index ->
                windowIndex = index
                refresh()
            }
        )

        val report = currentReport()
        column.addView(buildHeadline(report))
        column.addView(sectionLabel("By category"))
        column.addView(buildCategoryCard(report))
        column.addView(sectionLabel("By app"))
        column.addView(buildAppCard(report))
        column.addView(sectionLabel("Sessions"))
        column.addView(buildSessionCard())

        if (EarnMode.isEnabled(this)) {
            column.addView(sectionLabel("Earning"))
            column.addView(buildEarnCard())
        }
    }

    /**
     * The Earn numbers, kept as flat facts. No "efficiency", no grade, no
     * comparison — a ratio of earned to spent is information about how you use
     * your own time, not a score to improve.
     */
    private fun buildEarnCard(): View = card { card ->
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                "Minutes earned",
                EarnBudget.totalEarned(this).toString() + " all time · " +
                    EarnBudget.earnedToday(this) + " today"
            )
        )
        card.addView(
            FocusUi.listRow(this, tokens, "Minutes spent", EarnBudget.totalSpent(this).toString())
        )
        card.addView(
            FocusUi.listRow(this, tokens, "Banked now", EarnBudget.balanceMinutes(this).toString())
        )
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                "Tasks finished",
                FocusTaskStore.all(this).count { it.completed }.toString() + " · " +
                    FocusTaskStore.completedToday(this).size + " today"
            )
        )

        if (EarnMode.showsCredibility(this)) {
            val trust = FocusTaskStore.credibility(this)
            card.addView(FocusUi.spacer(this, 6))
            card.addView(
                FocusUi.meter(
                    this,
                    tokens,
                    "Verification trust",
                    (trust * 100).toInt().toString() + "%",
                    trust,
                    if (trust < 0.6f) tokens.warning else tokens.success
                )
            )
            card.addView(
                FocusUi.caption(
                    this,
                    tokens,
                    "Drops when a proof photo is refused, recovers when one passes. It is a note to " +
                        "yourself, not a permission level."
                )
            )
        }

        card.addView(FocusUi.spacer(this, 8))
        card.addView(FocusUi.caption(this, tokens, EarnMode.describeDeal(this)))
    }

    private fun currentReport(): UsageReport = when (windowIndex) {
        1 -> UsageAnalytics.last7Days(this)
        2 -> UsageAnalytics.report(this, 30L * 86_400_000L, "Last 30 days")
        else -> UsageAnalytics.today(this)
    }

    private fun buildDisabledCard(): View = card { card ->
        card.addView(FocusUi.heading(this, tokens, "Analytics is off"))
        card.addView(FocusUi.spacer(this, 6))
        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                "Nothing is being recorded. Turn it on if you want to see the pattern; " +
                    "it stays entirely on this phone either way."
            )
        )
        card.addView(FocusUi.spacer(this, 12))
        card.addView(
            FocusUi.primaryButton(this, tokens, "Turn on analytics") {
                if (!CapabilityRegistry.setEnabled(this, Capabilities.ANALYTICS, true)) {
                    FocusDialog.toast(this, SessionLock.refusalMessage(this))
                }
                refresh()
            }
        )
    }

    private fun buildPermissionCard(): View = card { card ->
        card.addView(FocusUi.heading(this, tokens, "Usage access is needed"))
        card.addView(FocusUi.spacer(this, 6))
        card.addView(
            FocusUi.secondary(
                this,
                tokens,
                "The breakdown is read from Android's own records. FocusLock keeps no separate log."
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

    private fun buildHeadline(report: UsageReport): View {
        val row = FocusUi.row(this)
        row.addView(
            FocusUi.statTile(this, tokens, UsageAnalytics.formatDuration(report.totalMs), "Screen time")
        )
        row.addView(FocusUi.statTile(this, tokens, report.opens.toString(), "Opens"))
        row.addView(FocusUi.statTile(this, tokens, report.apps.size.toString(), "Apps used"))
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = FocusUi.dp(this@AnalyticsActivity, tokens.density.gapDp) }
        return row
    }

    /**
     * Proportion bars rather than a pie: the question people actually have is
     * "how much of my day was this", and a bar answers it without a legend.
     */
    private fun buildCategoryCard(report: UsageReport): View = card { card ->
        if (!report.hasData) {
            card.addView(FocusUi.emptyState(this, tokens, "No usage recorded for this stretch."))
            return@card
        }

        report.byCategory.take(8).forEach { entry ->
            val fraction = if (report.totalMs > 0) {
                entry.second.toFloat() / report.totalMs.toFloat()
            } else {
                0f
            }
            card.addView(
                FocusUi.meter(
                    this,
                    tokens,
                    entry.first.label,
                    UsageAnalytics.formatDuration(entry.second) +
                        "  ·  " + (fraction * 100).toInt() + "%",
                    fraction,
                    colorFor(entry.first)
                )
            )
        }
    }

    private fun colorFor(category: AppCategory): Int = when (category) {
        AppCategory.SOCIAL -> tokens.danger
        AppCategory.VIDEO -> tokens.warning
        AppCategory.GAMES -> tokens.accent
        AppCategory.PRODUCTIVITY -> tokens.success
        AppCategory.ESSENTIAL -> tokens.textSecondary
        else -> UiPrefs.blend(tokens.accent, tokens.background, 0.4f)
    }

    private fun buildAppCard(report: UsageReport): View = card { card ->
        if (report.apps.isEmpty()) {
            card.addView(FocusUi.emptyState(this, tokens, "Nothing to show yet."))
            return@card
        }

        val shown = report.apps.take(15)
        shown.forEachIndexed { index, slice ->
            val policy = AppRules.effectivePolicy(this, slice.packageName)
            card.addView(
                FocusUi.listRow(
                    this,
                    tokens,
                    slice.label,
                    UsageAnalytics.formatDuration(slice.totalMs) + " · " + slice.opens + " opens · " +
                        slice.category.label,
                    trailing = FocusUi.smallButton(
                        this,
                        tokens,
                        if (policy.stopsLaunch) "Blocked" else "Manage"
                    ) { manage(slice) },
                    leading = FocusUi.appIcon(this, tokens, slice.packageName, 34)
                ) { manage(slice) }
            )
            if (index < shown.size - 1) card.addView(FocusUi.divider(this, tokens))
        }
    }

    /** Seeing a number and acting on it should be one tap apart, not two screens. */
    private fun manage(slice: UsageSlice) {
        val current = AppRules.effectivePolicy(this, slice.packageName)
        FocusDialog.singleChoice(
            this,
            title = slice.label,
            subtitle = UsageAnalytics.formatDuration(slice.totalMs) + " in this stretch.",
            choices = AppPolicy.ladder.map { policy ->
                FocusDialog.Choice(policy.id, policy.label, policy.blurb)
            },
            selectedKey = current.id
        ) { key ->
            if (!AppRules.setPolicy(this, slice.packageName, AppPolicy.fromId(key))) {
                FocusDialog.toast(this, SessionLock.refusalMessage(this))
            }
            refresh()
        }
    }

    private fun buildSessionCard(): View = card { card ->
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                "Sessions finished",
                SessionManager.totalSessions(this).toString()
            )
        )
        card.addView(
            FocusUi.listRow(
                this,
                tokens,
                "Time held",
                UsageAnalytics.formatDuration(SessionManager.totalFocusMs(this))
            )
        )
        if (Streaks.isEnabled(this)) {
            card.addView(
                FocusUi.listRow(this, tokens, "Current run", Streaks.current(this).toString() + " days")
            )
            card.addView(
                FocusUi.listRow(this, tokens, "Best run", Streaks.best(this).toString() + " days")
            )
        }
        card.addView(FocusUi.spacer(this, 8))
        card.addView(
            FocusUi.caption(
                this,
                tokens,
                "No part of this is uploaded, scored or compared with anyone."
            )
        )
    }
}
