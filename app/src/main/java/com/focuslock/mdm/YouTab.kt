package com.focuslock.mdm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

/**
 * The You tab: the app itself, and the way out.
 *
 * Personalisation lives here rather than buried three levels down, because the
 * look of a tool you are asking to live inside for ninety days is not a footnote.
 * So does the release path: a factory reset is a legitimate decision and hiding
 * it would be its own kind of dishonesty.
 */
class YouTab(activity: MainActivity, tokens: UiPrefs.Tokens) : FocusTab(activity, tokens) {

    private lateinit var container: LinearLayout

    override fun build(): View {
        container = FocusUi.column(activity, tokens.density.contentPaddingDp)
        return hostScroll(FocusUi.scroll(activity, container), container)
    }

    override fun onShow() {
        render()
    }

    private fun render(): Unit = redraw {
        val added = ArrayList<View>()

        fun add(view: View) {
            container.addView(view)
            added.add(view)
        }

        add(FocusUi.pageHeader(activity, tokens, "You", "How FocusLock looks, and how it lets go."))

        add(buildProgressCard())

        if (UiPrefs.showStats(activity) && UsageAnalytics.isEnabled(activity)) {
            add(FocusUi.sectionLabel(activity, tokens, "Today"))
            add(buildTodayStats())
        }

        add(FocusUi.sectionLabel(activity, tokens, "Make it yours"))
        add(buildAppearanceCard())

        add(FocusUi.sectionLabel(activity, tokens, "Setup and help"))
        add(buildHelpCard())

        if (CapabilityRegistry.isEnabled(activity, Capabilities.SOCIAL)) {
            add(FocusUi.sectionLabel(activity, tokens, "Study friend"))
            add(buildSocialCard())
        }

        add(FocusUi.sectionLabel(activity, tokens, "Release"))
        add(buildReleaseCard())

        add(FocusUi.spacer(activity, 8))
        add(buildAbout())

        Motion.stagger(added, tokens)
    }

    // ── Progress ──────────────────────────────────────────────────

    private fun buildProgressCard(): View {
        val card = FocusUi.card(activity, tokens)
        card.addView(FocusUi.heading(activity, tokens, "So far"))
        card.addView(FocusUi.spacer(activity, 10))

        val tiles = ArrayList<View>()
        tiles.add(
            FocusUi.statTile(
                activity,
                tokens,
                SessionManager.totalSessions(activity).toString(),
                "Sessions finished"
            )
        )
        tiles.add(
            FocusUi.statTile(
                activity,
                tokens,
                UsageAnalytics.formatDuration(SessionManager.totalFocusMs(activity)),
                "Time held"
            )
        )
        if (Streaks.isEnabled(activity)) {
            tiles.add(
                FocusUi.statTile(activity, tokens, Streaks.best(activity).toString(), "Best run (days)")
            )
        }
        card.addView(FocusUi.tileRow(activity, tiles))

        if (Streaks.isEnabled(activity) && Streaks.isPaused(activity)) {
            card.addView(FocusUi.spacer(activity, 10))
            card.addView(FocusUi.secondary(activity, tokens, Copy.relapseNote(activity)))
        }
        return card
    }

    /**
     * Moved here from the Focus tab (design doc, "one shared line ... today's
     * stats move to Your time - they are not a decision"). "So far" above is
     * the lifetime total; this is just today, which is what most people
     * actually glance at.
     */
    private fun buildTodayStats(): View {
        val report = UsageAnalytics.today(activity)
        val row = FocusUi.tileRow(
            activity,
            listOf(
                FocusUi.statTile(activity, tokens, UsageAnalytics.formatDuration(report.totalMs), "Screen time"),
                FocusUi.statTile(activity, tokens, report.opens.toString(), "App opens"),
                FocusUi.statTile(
                    activity,
                    tokens,
                    AppRules.blockedPackages(activity).size.toString(),
                    "Apps blocked"
                )
            )
        )
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = FocusUi.dp(activity, tokens.density.gapDp) }

        val wrapper = FocusUi.column(activity)
        wrapper.addView(row)
        wrapper.addView(
            FocusUi.ghostButton(activity, tokens, "See where the time went") {
                activity.startActivity(Intent(activity, AnalyticsActivity::class.java))
            }
        )
        return wrapper
    }

    // ── Appearance ────────────────────────────────────────────────

    /**
     * Theme, accent, and a way into everything else.
     *
     * This was four stacked chip strips, two sliders, an Apply button and three
     * toggles - the tallest card in the app, for settings most people touch
     * once. Theme and accent stay inline because they are the two you actually
     * browse and they preview themselves live; type, spacing, text size,
     * rounding, motion and contrast are one row away, on the screen that
     * already existed for "the rest of the appearance settings".
     */
    private fun buildAppearanceCard(): View {
        val card = FocusUi.card(activity, tokens)

        card.addView(FocusUi.caption(activity, tokens, "THEME"))
        card.addView(
            FocusUi.chipStrip(
                activity,
                tokens,
                UiPrefs.themes.map { it.label },
                UiPrefs.themes.indexOfFirst { it.id == UiPrefs.getTheme(activity).id }
            ) { index ->
                UiPrefs.setThemeId(activity, UiPrefs.themes[index].id)
                activity.requestShellRebuild()
            }
        )

        card.addView(FocusUi.spacer(activity, 10))
        card.addView(FocusUi.caption(activity, tokens, "ACCENT"))
        card.addView(buildAccentRow())

        card.addView(FocusUi.spacer(activity, 12))
        card.addView(FocusUi.divider(activity, tokens))

        card.addView(
            FocusUi.listRow(
                activity,
                tokens,
                "Type and spacing",
                UiPrefs.getFont(activity).label + " · " + UiPrefs.getDensity(activity).label +
                    " · " + (UiPrefs.getTextScale(activity) * 100).toInt() + "%",
                trailing = FocusUi.chevron(activity, tokens)
            ) { activity.startActivity(Intent(activity, PersonalizationActivity::class.java)) }
        )

        card.addView(FocusUi.divider(activity, tokens))
        card.addView(
            FocusUi.listRow(
                activity,
                tokens,
                "Language",
                languageLabel(UiPrefs.getAppLanguageTag()),
                trailing = FocusUi.chevron(activity, tokens)
            ) { pickLanguage() }
        )

        card.addView(FocusUi.divider(activity, tokens))
        card.addView(
            FocusUi.listRow(
                activity,
                tokens,
                "Clock",
                clockLabel(UiPrefs.getClockFormat(activity)),
                trailing = FocusUi.chevron(activity, tokens)
            ) { pickClockFormat() }
        )
        return card
    }

    private fun clockLabel(value: String): String = when (value) {
        TimeText.CLOCK_12 -> "12-hour (2:30 PM)"
        TimeText.CLOCK_24 -> "24-hour (14:30)"
        else -> "Match the phone"
    }

    /**
     * The app used to print 24-hour times whatever the phone said, which is
     * what "why is it in weird 24 hours format" was about. It follows the
     * system setting now; this is for the case where the phone and the person
     * disagree.
     */
    private fun pickClockFormat() {
        FocusDialog.singleChoice(
            activity,
            "Clock",
            "How times read on schedules, bedtime and deadlines.",
            listOf(
                FocusDialog.Choice(TimeText.CLOCK_SYSTEM, clockLabel(TimeText.CLOCK_SYSTEM)),
                FocusDialog.Choice(TimeText.CLOCK_12, clockLabel(TimeText.CLOCK_12)),
                FocusDialog.Choice(TimeText.CLOCK_24, clockLabel(TimeText.CLOCK_24))
            ),
            UiPrefs.getClockFormat(activity)
        ) { selected ->
            UiPrefs.setClockFormat(activity, selected)
            render()
        }
    }

    private fun languageLabel(tag: String): String = when (tag) {
        "ar" -> "العربية"
        "en" -> "English"
        else -> "System default"
    }

    /**
     * The app's own switch, independent of system Settings -> App info ->
     * Language (which reaches the same place via `android:localeConfig` -
     * this is just the faster, in-app route to it).
     */
    private fun pickLanguage() {
        FocusDialog.singleChoice(
            activity,
            "Language",
            "Changes every screen in the app - the block screens included.",
            listOf(
                FocusDialog.Choice("system", "System default"),
                FocusDialog.Choice("en", "English"),
                FocusDialog.Choice("ar", "العربية")
            ),
            UiPrefs.getAppLanguageTag()
        ) { selected ->
            UiPrefs.setAppLanguage(selected)
        }
    }

    private fun buildAccentRow(): View {
        val strip = FocusUi.row(activity)
        strip.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val currentId = UiPrefs.getAccent(activity).id

        UiPrefs.accents.forEach { accent ->
            val swatch = View(activity)
            val size = FocusUi.dp(activity, 34)
            swatch.background = FocusUi.roundedShape(
                activity,
                accent.color,
                17,
                if (accent.id == currentId) tokens.textPrimary else null,
                2
            )
            swatch.isClickable = true
            swatch.isFocusable = true
            swatch.contentDescription = accent.label
            swatch.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = FocusUi.dp(activity, 10)
            }
            swatch.setOnClickListener {
                UiPrefs.setAccentId(activity, accent.id)
                activity.requestShellRebuild()
            }
            strip.addView(swatch)
        }
        return FocusUi.horizontalScroll(activity, strip)
    }

    // ── Help ──────────────────────────────────────────────────────

    private fun buildHelpCard(): View {
        val card = FocusUi.card(activity, tokens)

        // The switchboard, as one row.
        //
        // This tab used to carry its own copy of it: a prose paragraph, four
        // capability toggles each with its full blurb, the break settings with
        // two sliders, a consequence line under every switch that was off, and
        // a reset button - about two hundred lines of screen. Every one of
        // those switches already lives in CapabilitiesActivity, which is the
        // screen built for them. Two places to change the same flag is not a
        // convenience, it is a second place to look when one of them is wrong.
        val enabledCount = Capabilities.all.count { CapabilityRegistry.isEnabled(activity, it.id) }
        card.addView(
            FocusUi.listRow(
                activity,
                tokens,
                "What FocusLock may do",
                advancedSummary(enabledCount),
                trailing = FocusUi.chevron(activity, tokens)
            ) { activity.startActivity(Intent(activity, CapabilitiesActivity::class.java)) }
        )
        card.addView(FocusUi.divider(activity, tokens))

        card.addView(
            FocusUi.listRow(
                activity,
                tokens,
                "Permissions",
                permissionSummary(),
                trailing = FocusUi.chevron(activity, tokens)
            ) { activity.startActivity(Intent(activity, SetupPermissionsActivity::class.java)) }
        )
        card.addView(FocusUi.divider(activity, tokens))

        card.addView(
            FocusUi.listRow(
                activity,
                tokens,
                "Device Owner setup",
                if (SetupChecks.isDeviceOwner(activity)) {
                    "Active. Safe Mode and uninstall can be closed."
                } else {
                    "Not set. Kiosk and Safe-Mode blocking need this."
                },
                trailing = FocusUi.chevron(activity, tokens)
            ) { activity.startActivity(Intent(activity, DeviceOwnerHelpActivity::class.java)) }
        )
        card.addView(FocusUi.divider(activity, tokens))

        card.addView(
            FocusUi.listRow(
                activity,
                tokens,
                "Advanced, and how it works",
                "What each mode sets, what you can change, and every way out of a session.",
                trailing = FocusUi.chevron(activity, tokens)
            ) { activity.startActivity(Intent(activity, AdvancedHelpActivity::class.java)) }
        )
        card.addView(FocusUi.divider(activity, tokens))

        card.addView(
            FocusUi.listRow(
                activity,
                tokens,
                "Run the setup quiz again",
                "Rebuild a starting setup from a few questions. Nothing changes until you accept it.",
                trailing = FocusUi.chevron(activity, tokens)
            ) {
                if (SessionLock.isFrozen(activity)) {
                    FocusDialog.toast(activity, SessionLock.refusalMessage(activity))
                } else {
                    activity.startActivity(
                        Intent(activity, OnboardingActivity::class.java)
                            .putExtra(OnboardingActivity.EXTRA_RERUN, true)
                    )
                }
            }
        )
        card.addView(FocusUi.divider(activity, tokens))

        val adbDisabled = FocusStore.getBool(activity, Constants.KEY_ADB_DISABLED, false)
        card.addView(
            FocusUi.listRow(
                activity,
                tokens,
                if (adbDisabled) "ADB debugging is off" else "Turn off ADB debugging",
                if (adbDisabled) {
                    "Closed by you. It reopens when a session that hands the phone back ends."
                } else {
                    "Closes the computer-side route into the phone. This one is yours to fire, never automatic."
                },
                trailing = if (adbDisabled) {
                    FocusUi.pill(activity, tokens, "Done", tokens.success)
                } else {
                    FocusUi.chevron(activity, tokens)
                }
            ) { if (!adbDisabled) confirmDisableAdb() }
        )
        return card
    }

    /** One line where a paragraph used to be: the template, and whether it still matches. */
    private fun advancedSummary(enabledCount: Int): String {
        val mode = SessionManager.mode(activity)
        val base = enabledCount.toString() + " of " + Capabilities.all.size + " on · " + mode.label + " template"
        return when {
            SessionLock.isFrozen(activity) ->
                base + " · held until the session ends"
            !SessionManager.matchesPreset(activity, mode) -> base + " · edited"
            else -> base
        }
    }

    private fun permissionSummary(): String {
        val missing = SetupChecks.missingForCurrentCapabilities(activity)
        return if (missing.isEmpty()) {
            "Everything your settings need is granted."
        } else {
            missing.size.toString() + " capability" + (if (missing.size == 1) "" else " switches") +
                " still waiting on a permission"
        }
    }

    /**
     * Kept exactly as it was: an explicit, user-fired button. Provisioning never
     * triggers it, and starting a session never triggers it.
     */
    private fun confirmDisableAdb() {
        if (!SetupChecks.isDeviceOwner(activity)) {
            FocusDialog.info(
                activity,
                "Device Owner needed",
                "Only a Device Owner can close ADB. Set that up from a computer first."
            )
            return
        }

        FocusDialog.alert(
            activity,
            title = "Turn off ADB debugging?",
            message = "This closes the developer route into the phone while FocusLock is Device Owner. " +
                "You would need a factory reset, or a session that hands the phone back, to reopen it.",
            confirmLabel = "Turn it off",
            cancelLabel = "Cancel",
            destructive = true,
            onConfirm = {
                FocusStore.setBool(activity, Constants.KEY_ADB_DISABLED, true)
                try {
                    val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    dpm.addUserRestriction(
                        ComponentName(activity, AdminReceiver::class.java),
                        UserManager.DISALLOW_DEBUGGING_FEATURES
                    )
                } catch (_: Exception) {
                    FocusDialog.toast(activity, "Android refused that restriction.")
                }
                render()
            }
        )
    }

    // ── Social ────────────────────────────────────────────────────

    private fun buildSocialCard(): View {
        val card = FocusUi.card(activity, tokens)
        card.addView(
            FocusUi.secondary(
                activity,
                tokens,
                "Share a read-only summary of your sessions with one person you choose. " +
                    "It is generated on this phone and only leaves it when you send it."
            )
        )
        card.addView(FocusUi.spacer(activity, 12))
        card.addView(
            FocusUi.secondaryButton(activity, tokens, "Share this week's summary") { shareSummary() }
        )
        return card
    }

    private fun shareSummary() {
        val report = UsageAnalytics.last7Days(activity)
        val summary = buildString {
            append("FocusLock, last 7 days\n")
            append("Sessions finished: ").append(SessionManager.totalSessions(activity)).append("\n")
            if (Streaks.isEnabled(activity)) {
                append("Current run: ").append(Streaks.current(activity)).append(" days\n")
            }
            append("Screen time: ").append(UsageAnalytics.formatDuration(report.totalMs))
        }
        try {
            activity.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, summary)
                    },
                    "Share summary"
                )
            )
        } catch (_: Exception) {
            FocusDialog.toast(activity, "No app available to share that.")
        }
    }

    // ── Release ───────────────────────────────────────────────────

    private fun buildReleaseCard(): View {
        val card = FocusUi.card(activity, tokens)

        card.addView(
            FocusUi.secondary(
                activity,
                tokens,
                "A factory reset is the ultimate way out of a kiosk session, and FocusLock never " +
                    "blocks it. It erases the phone, so it stays behind a countdown."
            )
        )
        card.addView(FocusUi.spacer(activity, 14))
        card.addView(
            FocusUi.dangerButton(activity, tokens, "Factory reset this phone") { confirmFactoryReset() }
        )
        return card
    }

    private fun confirmFactoryReset() {
        FocusDialog.confirmWithCountdown(
            activity,
            title = "Erase everything on this phone?",
            message = "Every app, photo and message goes. This is not how a session is meant to end, " +
                "and there is no undo.",
            confirmLabel = "Erase the phone",
            seconds = 8
        ) {
            performFactoryReset()
        }
    }

    private fun performFactoryReset() {
        val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(activity.packageName)) {
            try {
                dpm.wipeData(0)
                return
            } catch (_: Exception) {
                // Fall through to the Settings route below.
            }
        }
        LockManager.allowSettingsUntil(activity, System.currentTimeMillis() + 5 * 60 * 1000)
        val intents = listOf(
            Intent("android.settings.FACTORY_RESET"),
            Intent("android.settings.MASTER_CLEAR"),
            Intent(Settings.ACTION_PRIVACY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        intents.forEach { intent ->
            try {
                if (intent.resolveActivity(activity.packageManager) != null) {
                    activity.startActivity(intent)
                    return
                }
            } catch (_: Exception) {
                // Try the next one.
            }
        }
        FocusDialog.toast(activity, "Reset is only reachable from Settings on this phone.")
    }

    private fun buildAbout(): View {
        val text = FocusUi.caption(
            activity,
            tokens,
            "FocusLock. Everything it knows about you stays on this phone."
        )
        text.gravity = android.view.Gravity.CENTER
        text.setPadding(0, FocusUi.dp(activity, 8), 0, FocusUi.dp(activity, 24))
        return text
    }
}
