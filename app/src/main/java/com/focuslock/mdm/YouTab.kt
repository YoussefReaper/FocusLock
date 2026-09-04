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
        return FocusUi.scroll(activity, container)
    }

    override fun onShow() {
        render()
    }

    private fun render() {
        container.removeAllViews()
        val added = ArrayList<View>()

        fun add(view: View) {
            container.addView(view)
            added.add(view)
        }

        add(FocusUi.pageHeader(activity, tokens, "You", "How FocusLock looks, and how it lets go."))

        add(buildProgressCard())

        add(FocusUi.sectionLabel(activity, tokens, "Make it yours"))
        add(buildAppearanceCard())

        add(FocusUi.sectionLabel(activity, tokens, "Setup and help"))
        add(buildHelpCard())

        add(buildAdvancedHeader())
        add(buildAdvancedCard())

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

        val row = FocusUi.row(activity)
        row.addView(
            FocusUi.statTile(
                activity,
                tokens,
                SessionManager.totalSessions(activity).toString(),
                "Sessions finished"
            )
        )
        row.addView(
            FocusUi.statTile(
                activity,
                tokens,
                UsageAnalytics.formatDuration(SessionManager.totalFocusMs(activity)),
                "Time held"
            )
        )
        if (Streaks.isEnabled(activity)) {
            row.addView(
                FocusUi.statTile(activity, tokens, Streaks.best(activity).toString(), "Best run (days)")
            )
        }
        card.addView(row)

        if (Streaks.isEnabled(activity) && Streaks.isPaused(activity)) {
            card.addView(FocusUi.spacer(activity, 10))
            card.addView(FocusUi.secondary(activity, tokens, Copy.relapseNote(activity)))
        }
        return card
    }

    // ── Appearance ────────────────────────────────────────────────

    /**
     * Live, in-place theming: every control here redraws the whole shell as
     * soon as it changes, so choosing a theme is a preview rather than a guess.
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

        card.addView(FocusUi.spacer(activity, 10))
        card.addView(FocusUi.caption(activity, tokens, "TYPE"))
        card.addView(
            FocusUi.chipStrip(
                activity,
                tokens,
                UiPrefs.fonts.map { it.label },
                UiPrefs.fonts.indexOfFirst { it.id == UiPrefs.getFont(activity).id }
            ) { index ->
                UiPrefs.setFontId(activity, UiPrefs.fonts[index].id)
                activity.requestShellRebuild()
            }
        )

        card.addView(FocusUi.spacer(activity, 10))
        card.addView(FocusUi.caption(activity, tokens, "SPACING"))
        card.addView(
            FocusUi.chipStrip(
                activity,
                tokens,
                UiPrefs.densities.map { it.label },
                UiPrefs.densities.indexOfFirst { it.id == UiPrefs.getDensity(activity).id }
            ) { index ->
                UiPrefs.setDensityId(activity, UiPrefs.densities[index].id)
                activity.requestShellRebuild()
            }
        )

        card.addView(
            FocusUi.sliderRow(
                activity,
                tokens,
                "Text size",
                80,
                140,
                (UiPrefs.getTextScale(activity) * 100).toInt(),
                { it.toString() + "%" }
            ) { value ->
                UiPrefs.setTextScale(activity, value / 100f)
            }
        )
        card.addView(
            FocusUi.sliderRow(
                activity,
                tokens,
                "Corner rounding",
                0,
                32,
                UiPrefs.getCardRadiusDp(activity),
                { it.toString() + "dp" }
            ) { value ->
                UiPrefs.setCardRadiusDp(activity, value)
            }
        )
        card.addView(
            FocusUi.smallButton(activity, tokens, "Apply size and rounding") {
                activity.requestShellRebuild()
            }
        )

        card.addView(FocusUi.spacer(activity, 8))
        card.addView(FocusUi.divider(activity, tokens))

        card.addView(
            FocusUi.toggleRow(
                activity,
                tokens,
                "Reduce motion",
                "Turns off fades and the breathing animation on the pause screen.",
                UiPrefs.reducedMotion(activity)
            ) { value ->
                UiPrefs.setReducedMotion(activity, value)
                activity.requestShellRebuild()
            }
        )
        card.addView(
            FocusUi.toggleRow(
                activity,
                tokens,
                "Higher contrast",
                "Pushes text and dividers further from the background.",
                UiPrefs.highContrast(activity)
            ) { value ->
                UiPrefs.setHighContrast(activity, value)
                activity.requestShellRebuild()
            }
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
                "Wallpaper, background and sections",
                "The rest of the appearance settings",
                trailing = FocusUi.chevron(activity, tokens)
            ) { activity.startActivity(Intent(activity, PersonalizationActivity::class.java)) }
        )
        return card
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

    // ── Advanced ──────────────────────────────────────────────────

    /** Section label with a "?" beside it, since this is the page that needs one. */
    private fun buildAdvancedHeader(): View {
        val row = FocusUi.row(activity)
        val icon = FocusUi.categoryIcon(activity, tokens, R.drawable.ic_glyph_focus, sizeDp = 16)
        (icon.layoutParams as LinearLayout.LayoutParams).marginEnd = FocusUi.dp(activity, 8)
        row.addView(icon)
        val label = FocusUi.sectionLabel(activity, tokens, "Advanced")
        label.layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
        row.addView(label)
        row.addView(
            FocusUi.smallButton(activity, tokens, "What is this?") {
                activity.startActivity(Intent(activity, AdvancedHelpActivity::class.java))
            }
        )
        return row
    }

    /**
     * The switchboard.
     *
     * A mode sets these; this is where you disagree with it. Every row says
     * what turns off with it, because a switch whose consequence you have to
     * discover by being locked out of your phone is not a real choice.
     */
    private fun buildAdvancedCard(): View {
        val card = FocusUi.card(activity, tokens)
        val mode = SessionManager.mode(activity)
        val sessionRunning = SessionManager.isActive(activity)

        card.addView(
            FocusUi.body(
                activity,
                tokens,
                if (sessionRunning && SessionLock.isFrozen(activity)) {
                    "A " + mode.label + " session is running, and you asked for your rules to be " +
                        "held still while it does. These come back when it ends, in " +
                        SessionManager.formatRemaining(activity) + "."
                } else if (sessionRunning) {
                    "A " + mode.label + " session is running. Changes here take effect straight away."
                } else {
                    "Your last template was " + mode.label + ". Picking a different mode loads that " +
                        "mode's version of these; anything you change here stays until then."
                }
            )
        )
        card.addView(FocusUi.spacer(activity, 14))

        card.addView(FocusUi.sectionLabel(activity, tokens, "Leaving a session"))
        card.addView(advancedToggle(Capabilities.CAN_END_EARLY, "End a session early"))

        card.addView(FocusUi.divider(activity, tokens))
        card.addView(FocusUi.sectionLabel(activity, tokens, "How hard it blocks"))
        card.addView(advancedToggle(Capabilities.HARD_BLOCK, "Actually stop blocked apps"))
        card.addView(advancedToggle(Capabilities.SUSPEND_BLOCKED_APPS, "Silence blocked apps"))
        card.addView(advancedToggle(Capabilities.HIDE_BLOCKED_APPS, "Hide them from the launcher"))

        card.addView(FocusUi.divider(activity, tokens))
        card.addView(FocusUi.sectionLabel(activity, tokens, "Breaks"))
        card.addView(
            FocusUi.toggleRow(
                activity,
                tokens,
                "Take a break",
                "Unlock one blocked app for a few minutes on purpose, instead of giving up on the whole session.",
                CapabilityRegistry.isEnabled(activity, Capabilities.TAKE_A_BREAK),
                enabled = !SessionLock.isFrozen(activity)
            ) { value ->
                if (!CapabilityRegistry.setEnabled(activity, Capabilities.TAKE_A_BREAK, value)) {
                    FocusDialog.toast(activity, SessionLock.refusalMessage(activity))
                }
                render()
            }
        )

        if (CapabilityRegistry.isEnabled(activity, Capabilities.TAKE_A_BREAK)) {
            card.addView(
                FocusUi.sliderRow(
                    activity,
                    tokens,
                    "How long a break lasts",
                    1,
                    30,
                    TakeABreak.breakMinutes(activity),
                    { it.toString() + " min" }
                ) { value -> TakeABreak.setBreakMinutes(activity, value) }
            )
            card.addView(
                FocusUi.sliderRow(
                    activity,
                    tokens,
                    "Breaks a day",
                    0,
                    10,
                    TakeABreak.dailyMax(activity),
                    { if (it == 0) "None" else it.toString() }
                ) { value -> TakeABreak.setDailyMax(activity, value) }
            )
            card.addView(
                FocusUi.caption(
                    activity,
                    tokens,
                    TakeABreak.remainingToday(activity).toString() + " left today. Taking one is not a failure."
                )
            )
        } else {
            card.addView(
                FocusUi.caption(
                    activity,
                    tokens,
                    "Off: a blocked app stays blocked for the whole session, with no exceptions."
                )
            )
        }

        if (!SessionManager.matchesPreset(activity, mode)) {
            card.addView(FocusUi.spacer(activity, 16))
            card.addView(
                FocusUi.secondaryButton(activity, tokens, "Put " + mode.label + "'s defaults back") {
                    if (SessionManager.resetToPreset(activity, mode)) {
                        FocusDialog.toast(activity, mode.label + " defaults restored.")
                    } else {
                        FocusDialog.toast(activity, SessionLock.refusalMessage(activity))
                    }
                    render()
                }
            )
            // Same reasoning as advancedToggle(): this button rewrites every
            // flag above it in one tap, so it must be just as frozen as they
            // are - the header text above already promises the rules are
            // held still, and this button was the one thing not honouring it.
            if (SessionLock.isFrozen(activity)) {
                card.addView(FocusUi.caption(activity, tokens, Copy.rulesFrozenHint(activity)))
            }
        }

        return card
    }

    /** One capability, its plain-language name, and what switching it off costs. */
    private fun advancedToggle(id: String, title: String): View {
        val spec = Capabilities.spec(id)
        val enabled = CapabilityRegistry.isEnabled(activity, id)
        val frozen = SessionLock.isFrozen(activity)
        val holder = FocusUi.column(activity, 0)

        holder.addView(
            FocusUi.toggleRow(
                activity,
                tokens,
                title,
                spec?.blurb,
                enabled,
                enabled = !frozen
            ) { value ->
                if (!CapabilityRegistry.setEnabled(activity, id, value)) {
                    FocusDialog.toast(activity, SessionLock.refusalMessage(activity))
                }
                render()
            }
        )

        // A greyed switch with no reason next to it reads as a bug. Say why.
        if (frozen) {
            val locked = FocusUi.caption(activity, tokens, Copy.rulesFrozenHint(activity))
            locked.setPadding(0, FocusUi.dp(activity, 2), 0, FocusUi.dp(activity, 8))
            holder.addView(locked)
        }

        // The consequence line, shown when the switch is off — the moment it
        // is actually load-bearing.
        val note = spec?.weakenNote
        if (!enabled && !note.isNullOrBlank()) {
            val caption = FocusUi.caption(activity, tokens, note)
            caption.setTextColor(tokens.warning)
            holder.addView(caption)
        }
        return holder
    }

    // ── Help ──────────────────────────────────────────────────────

    private fun buildHelpCard(): View {
        val card = FocusUi.card(activity, tokens)

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
