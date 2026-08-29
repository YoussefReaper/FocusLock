package com.focuslock.mdm

import android.content.Intent
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The Focus tab: what is happening right now, and the one decision worth making.
 *
 * When a session is running this is a status screen and nothing else. When one
 * is not, it is a chooser with four clearly-described strengths — each stating
 * how hard it is to leave *before* it is picked, which is the single most
 * important honesty in the whole app.
 */
class FocusDashboardTab(activity: MainActivity, tokens: UiPrefs.Tokens) : FocusTab(activity, tokens) {

    private lateinit var container: LinearLayout
    private var ring: CircularProgressIndicator? = null
    private var countdownView: TextView? = null
    private var progressView: TextView? = null

    private var selectedMode: FocusMode = FocusMode.BLOCK
    private var selectedDurationMs: Long = 2L * 60 * 60 * 1000

    override fun build(): View {
        container = FocusUi.column(activity, tokens.density.contentPaddingDp)
        val scroll = FocusUi.scroll(activity, container)
        scroll.setPadding(0, 0, 0, FocusUi.dp(activity, 12))
        return scroll
    }

    override fun onShow() {
        render()
    }

    override fun onTick() {
        if (!SessionManager.isActive(activity)) return
        countdownView?.text = SessionManager.formatCountdown(SessionManager.remainingMs(activity))
        val percent = SessionManager.progressPercent(activity)
        progressView?.text = percent.toString() + "%"
        ring?.let { if (it.progress != percent) it.progress = percent }
    }

    // ── Render ────────────────────────────────────────────────────

    private fun render() {
        container.removeAllViews()
        val added = ArrayList<View>()

        fun add(view: View) {
            container.addView(view)
            added.add(view)
        }

        add(buildGreeting())

        setupIssues().takeIf { it.isNotEmpty() }?.let { issues -> add(buildSetupCard(issues)) }

        if (SessionManager.isActive(activity)) {
            add(buildActiveSession())
        } else {
            add(buildStarter())
        }

        buildContextCard()?.let { add(it) }

        if (UiPrefs.showStats(activity) && UsageAnalytics.isEnabled(activity)) {
            add(FocusUi.sectionLabel(activity, tokens, "Today"))
            add(buildTodayStats())
        }

        if (UiPrefs.showQuickSettings(activity)) {
            add(FocusUi.sectionLabel(activity, tokens, "Quick settings"))
            add(buildQuickSettings())
        }

        Motion.stagger(added, tokens)
    }

    private fun buildGreeting(): View {
        val column = FocusUi.column(activity)
        column.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = FocusUi.dp(activity, tokens.density.gapDp + 4) }

        column.addView(FocusUi.title(activity, tokens, greetingLine()))

        val sub = if (Streaks.isEnabled(activity)) Streaks.summary(activity) else ""
        if (sub.isNotBlank()) {
            val subView = FocusUi.secondary(activity, tokens, sub)
            subView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = FocusUi.dp(activity, 6) }
            column.addView(subView)
        }
        return column
    }

    private fun greetingLine(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            SessionManager.isActive(activity) -> "You are in a session"
            hour < 5 -> "Still up"
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    // ── Active session ────────────────────────────────────────────

    private fun buildActiveSession(): View {
        val card = FocusUi.card(activity, tokens)
        val mode = SessionManager.mode(activity)

        val header = FocusUi.row(activity)
        val modeName = FocusUi.heading(activity, tokens, mode.label + " mode")
        modeName.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        header.addView(modeName)
        header.addView(FocusUi.pill(activity, tokens, "Running", tokens.success))
        card.addView(header)

        card.addView(FocusUi.spacer(activity, 18))

        val ringHolder = android.widget.FrameLayout(activity)
        ringHolder.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val indicator = FocusUi.progressRing(activity, tokens, 208)
        indicator.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        ringHolder.addView(indicator)
        ring = indicator

        val centre = FocusUi.column(activity)
        centre.gravity = Gravity.CENTER
        centre.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }

        val percentView = FocusUi.display(activity, tokens, SessionManager.progressPercent(activity).toString() + "%")
        percentView.gravity = Gravity.CENTER
        percentView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        progressView = percentView

        val remainingView = FocusUi.caption(activity, tokens, "of the way through")
        remainingView.gravity = Gravity.CENTER
        remainingView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        centre.addView(percentView)
        centre.addView(remainingView)
        ringHolder.addView(centre)
        card.addView(ringHolder)

        Motion.animateProgress(indicator, SessionManager.progressPercent(activity), tokens)

        card.addView(FocusUi.spacer(activity, 18))

        val countdown = FocusUi.heading(
            activity,
            tokens,
            SessionManager.formatCountdown(SessionManager.remainingMs(activity))
        )
        countdown.gravity = Gravity.CENTER
        countdownView = countdown
        card.addView(countdown)

        val ends = FocusUi.caption(
            activity,
            tokens,
            "Ends " + SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
                .format(Date(SessionManager.endsAt(activity)))
        )
        ends.gravity = Gravity.CENTER
        ends.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = FocusUi.dp(activity, 6) }
        card.addView(ends)

        card.addView(FocusUi.spacer(activity, 20))

        if (SessionManager.canEndEarly(activity)) {
            card.addView(
                FocusUi.secondaryButton(activity, tokens, "End this session") { confirmEndSession() }
            )
        } else {
            val note = FocusUi.secondary(activity, tokens, mode.exitLine)
            note.gravity = Gravity.CENTER
            card.addView(note)
        }

        val passes = TakeABreak.activePasses(activity)
        if (passes.isNotEmpty()) {
            card.addView(FocusUi.spacer(activity, 14))
            card.addView(FocusUi.divider(activity, tokens))
            passes.forEach { entry ->
                card.addView(
                    FocusUi.listRow(
                        activity,
                        tokens,
                        AppCatalog.label(activity, entry.key),
                        "Break ends in " + SessionManager.formatDuration(entry.value),
                        trailing = FocusUi.smallButton(activity, tokens, "End now") {
                            TakeABreak.endEarly(activity, entry.key)
                            render()
                        },
                        leading = FocusUi.appIcon(activity, tokens, entry.key, 30)
                    )
                )
            }
        }

        return card
    }

    private fun confirmEndSession() {
        FocusDialog.alert(
            activity,
            title = "End the session?",
            message = "Everything unlocks straight away. The time you already did still counts.",
            confirmLabel = "End it",
            cancelLabel = "Keep going",
            onConfirm = {
                SessionManager.end(activity)
                FocusDialog.toast(activity, Copy.sessionEndedEarly(activity))
                render()
            }
        )
    }

    // ── Starting a session ────────────────────────────────────────

    private fun buildStarter(): View {
        val column = FocusUi.column(activity)

        column.addView(FocusUi.sectionLabel(activity, tokens, "Start a session"))

        val available = FocusMode.available(activity)
        if (available.isEmpty()) {
            val card = FocusUi.card(activity, tokens)
            card.addView(
                FocusUi.body(
                    activity,
                    tokens,
                    "Every mode is switched off right now. Turn one on in Rules to start a session."
                )
            )
            card.addView(FocusUi.spacer(activity, 12))
            card.addView(
                FocusUi.secondaryButton(activity, tokens, "Open Rules") {
                    activity.selectTab(MainActivity.TAB_RULES)
                }
            )
            column.addView(card)
            return column
        }

        if (selectedMode !in available) selectedMode = available.first()

        available.forEach { mode -> column.addView(buildModeCard(mode)) }

        column.addView(FocusUi.sectionLabel(activity, tokens, "For how long"))
        column.addView(buildDurationPicker())

        column.addView(FocusUi.spacer(activity, 6))
        column.addView(
            FocusUi.primaryButton(
                activity,
                tokens,
                "Start " + selectedMode.label + " for " + SessionManager.formatDuration(selectedDurationMs)
            ) { confirmStart() }
        )

        if (selectedMode == FocusMode.KIOSK) {
            column.addView(FocusUi.spacer(activity, 12))
            column.addView(buildKioskOptions())
        }

        return column
    }

    /**
     * Each mode states its strength, what it does, and how you get out. The
     * exit line is not fine print: it is the second line of the card.
     */
    private fun buildModeCard(mode: FocusMode): View {
        val selected = mode == selectedMode
        val card = FocusUi.card(activity, tokens, elevated = selected) {
            selectedMode = mode
            render()
        }
        if (selected) {
            card.background = FocusUi.roundedShape(
                activity,
                tokens.surfaceAlt,
                tokens.radiusDp,
                tokens.accent,
                2
            )
        }

        val header = FocusUi.row(activity)
        val name = FocusUi.heading(activity, tokens, mode.label)
        name.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        header.addView(name)
        header.addView(buildStrengthDots(mode.strength))
        card.addView(header)

        val blurb = FocusUi.secondary(activity, tokens, mode.oneLiner)
        blurb.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = FocusUi.dp(activity, 8) }
        card.addView(blurb)

        val exit = FocusUi.caption(activity, tokens, mode.exitLine)
        exit.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = FocusUi.dp(activity, 6) }
        card.addView(exit)

        if (mode.isHard && !SetupChecks.isDeviceOwner(activity)) {
            card.addView(FocusUi.spacer(activity, 10))
            card.addView(
                FocusUi.pill(activity, tokens, "Needs Device Owner setup", tokens.warning)
            )
        }
        return card
    }

    /** Four dots, filled to the mode's strength: readable at a glance, no icon to decode. */
    private fun buildStrengthDots(strength: Int): View {
        val row = FocusUi.row(activity)
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        for (i in 1..4) {
            val dot = View(activity)
            val size = FocusUi.dp(activity, 7)
            dot.background = FocusUi.roundedShape(
                activity,
                if (i <= strength) tokens.accent else tokens.track,
                4
            )
            dot.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = FocusUi.dp(activity, 4)
            }
            row.addView(dot)
        }
        return row
    }

    private fun buildDurationPicker(): View {
        val options = listOf(
            "25m" to 25L * 60 * 1000,
            "1h" to 60L * 60 * 1000,
            "2h" to 2L * 60 * 60 * 1000,
            "4h" to 4L * 60 * 60 * 1000,
            "Today" to 8L * 60 * 60 * 1000,
            "1 day" to 24L * 60 * 60 * 1000,
            "1 week" to 7L * 24 * 60 * 60 * 1000,
            "30 days" to 30L * 24 * 60 * 60 * 1000,
            "90 days" to 90L * 24 * 60 * 60 * 1000
        )
        val labels = options.map { it.first } + "Custom"
        val selectedIndex = options.indexOfFirst { it.second == selectedDurationMs }

        return FocusUi.chipStrip(activity, tokens, labels, selectedIndex) { index ->
            if (index >= options.size) {
                askCustomDuration()
            } else {
                selectedDurationMs = options[index].second
                render()
            }
        }
    }

    private fun askCustomDuration() {
        FocusDialog.textInput(
            activity,
            title = "How many hours?",
            subtitle = "Whole hours. For days, multiply by 24.",
            hint = "Hours",
            numeric = true,
            confirmLabel = "Set"
        ) { value ->
            val hours = value.toLongOrNull() ?: 0L
            if (hours <= 0L) {
                FocusDialog.toast(activity, "Enter a number of hours.")
                return@textInput
            }
            selectedDurationMs = hours * 60L * 60L * 1000L
            render()
        }
    }

    private fun buildKioskOptions(): View {
        val card = FocusUi.card(activity, tokens)
        card.addView(FocusUi.heading(activity, tokens, "Kiosk options"))
        card.addView(FocusUi.spacer(activity, 6))

        card.addView(
            FocusUi.toggleRow(
                activity,
                tokens,
                "Full-screen session surface",
                "Hides the tab bar. Your library and rules become unreachable until the session ends.",
                CapabilityRegistry.getBoolParam(activity, Capabilities.KIOSK_MODE, "fullScreenSurface", false)
            ) { value ->
                CapabilityRegistry.setBoolParam(activity, Capabilities.KIOSK_MODE, "fullScreenSurface", value)
            }
        )

        card.addView(
            FocusUi.toggleRow(
                activity,
                tokens,
                "Hand the phone back at the end",
                "Removes FocusLock's Device Owner powers when the timer runs out. You would need a computer to set it up again.",
                SessionManager.releasesOwnerOnEnd(activity)
            ) { value ->
                SessionManager.setReleasesOwnerOnEnd(activity, value)
            }
        )
        return card
    }

    private fun confirmStart() {
        val mode = selectedMode

        if (mode.isHard && !SetupChecks.isDeviceOwner(activity)) {
            FocusDialog.alert(
                activity,
                title = "Kiosk needs one more step",
                message = "Kiosk mode only holds if FocusLock is Device Owner, which is set from a computer over ADB. " +
                    "Without it, the launcher and Safe Mode stay open.",
                confirmLabel = "Show me how",
                cancelLabel = "Not now",
                onConfirm = {
                    activity.startActivity(Intent(activity, DeviceOwnerHelpActivity::class.java))
                }
            )
            return
        }

        val message = if (mode.isHard) {
            "Kiosk runs for " + SessionManager.formatDuration(selectedDurationMs) +
                " and cannot be ended early. " + mode.exitLine
        } else {
            mode.oneLiner + "\n\n" + mode.exitLine
        }

        FocusDialog.alert(
            activity,
            title = "Start " + mode.label + " for " + SessionManager.formatDuration(selectedDurationMs) + "?",
            message = message,
            confirmLabel = "Start",
            cancelLabel = "Cancel",
            onConfirm = {
                SessionManager.start(activity, mode, selectedDurationMs)
                FocusDialog.toast(activity, Copy.sessionStarted(activity, mode))
                render()
            }
        )
    }

    // ── Setup, context, stats ─────────────────────────────────────

    private data class SetupIssue(val label: String, val detail: String, val intent: Intent?)

    /**
     * Missing permissions are shown as a calm checklist that the user taps when
     * ready. The old build fired a toast and threw them into Settings on every
     * resume, which is disorienting and easy to mistake for a crash.
     */
    private fun setupIssues(): List<SetupIssue> {
        val issues = ArrayList<SetupIssue>()

        if (!SetupChecks.hasUsageAccess(activity)) {
            issues.add(
                SetupIssue(
                    "Usage access",
                    "Lets FocusLock see which app is in front. Nothing leaves the phone.",
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                )
            )
        }
        if (needsAccessibility() && !SetupChecks.isContentGuardEnabled(activity)) {
            issues.add(
                SetupIssue(
                    "Content guard",
                    "Needed for keyword, Shorts, Reels and WhatsApp guards.",
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            )
        }
        if (CapabilityRegistry.isEnabled(activity, Capabilities.NOTIFICATION_BLOCK) &&
            !SetupChecks.isNotificationAccessGranted(activity)
        ) {
            issues.add(
                SetupIssue(
                    "Notification access",
                    "Lets the shield hold back alerts from blocked apps.",
                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                )
            )
        }
        if (!SetupChecks.isIgnoringBatteryOptimizations(activity)) {
            issues.add(
                SetupIssue(
                    "Unrestricted battery",
                    "Stops Android putting the guard to sleep mid-session.",
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                )
            )
        }
        return issues
    }

    private fun needsAccessibility(): Boolean = listOf(
        Capabilities.CONTENT_GUARD,
        Capabilities.KEYWORD_BLOCK,
        Capabilities.SHORTS_BLOCK,
        Capabilities.REELS_BLOCK,
        Capabilities.ADULT_BLOCK,
        Capabilities.WHATSAPP_GUARD
    ).any { CapabilityRegistry.isEnabled(activity, it) }

    private fun buildSetupCard(issues: List<SetupIssue>): View {
        val card = FocusUi.card(activity, tokens, elevated = true)

        val header = FocusUi.row(activity)
        val title = FocusUi.heading(activity, tokens, "Finish setting up")
        title.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        header.addView(title)
        header.addView(FocusUi.pill(activity, tokens, issues.size.toString() + " left", tokens.warning))
        card.addView(header)

        card.addView(FocusUi.spacer(activity, 4))
        card.addView(
            FocusUi.secondary(
                activity,
                tokens,
                "Some things you switched on need a permission Android only grants by hand."
            )
        )
        card.addView(FocusUi.spacer(activity, 6))

        issues.forEach { issue ->
            card.addView(
                FocusUi.listRow(
                    activity,
                    tokens,
                    issue.label,
                    issue.detail,
                    trailing = FocusUi.smallButton(activity, tokens, "Grant") { openSetting(issue.intent) }
                ) { openSetting(issue.intent) }
            )
        }

        card.addView(FocusUi.spacer(activity, 6))
        card.addView(
            FocusUi.ghostButton(activity, tokens, "Open the full setup guide") {
                activity.startActivity(Intent(activity, SetupPermissionsActivity::class.java))
            }
        )
        return card
    }

    private fun openSetting(intent: Intent?) {
        if (intent == null) return
        LockManager.allowSettingsUntil(activity, System.currentTimeMillis() + 2 * 60 * 1000)
        try {
            activity.startActivity(intent)
        } catch (_: Exception) {
            FocusDialog.toast(activity, "That settings page is not available on this phone.")
        }
    }

    /** Whatever is shaping the phone right now that is not a session. */
    private fun buildContextCard(): View? {
        val lines = ArrayList<Pair<String, String>>()

        EarnSession.activeTask(activity)?.let { task ->
            lines.add(
                "Task running" to (task.title + " · " +
                    SessionManager.formatDuration(EarnSession.elapsedMs(activity)) + " in")
            )
        }
        if (EarnBudget.isSpending(activity)) {
            lines.add(
                "Earned time" to (SessionManager.formatDuration(
                    EarnBudget.remainingSpendMs(activity)
                ) + " left")
            )
        }

        ScheduleManager.activeWindowIfEnabled(activity)?.let { window ->
            lines.add(
                "Schedule running" to (window.message.ifBlank { "Quiet window" } +
                    " until " + ScheduleManager.formatTime(window.endMinutes))
            )
        }
        ScheduleManager.nextWindow(activity)?.let { window ->
            lines.add("Next window" to ScheduleManager.formatTime(window.startMinutes))
        }
        if (Bedtime.isActive(activity)) {
            lines.add(
                "Bedtime" to ("Until " + Bedtime.formatTime(Bedtime.endMinutes(activity)))
            )
        }
        PlaceRules.activePlaces(activity).forEach { place ->
            lines.add("Place rule" to place.label)
        }

        if (lines.isEmpty()) return null

        val card = FocusUi.card(activity, tokens)
        card.addView(FocusUi.heading(activity, tokens, "Also on right now"))
        lines.forEach { pair ->
            card.addView(
                FocusUi.listRow(
                    activity,
                    tokens,
                    pair.first,
                    pair.second,
                    trailing = FocusUi.pill(activity, tokens, "Active", tokens.accent)
                )
            )
        }
        return card
    }

    private fun buildTodayStats(): View {
        val report = UsageAnalytics.today(activity)
        val row = FocusUi.row(activity)
        row.addView(
            FocusUi.statTile(activity, tokens, UsageAnalytics.formatDuration(report.totalMs), "Screen time")
        )
        row.addView(FocusUi.statTile(activity, tokens, report.opens.toString(), "App opens"))
        row.addView(
            FocusUi.statTile(
                activity,
                tokens,
                AppRules.blockedPackages(activity).size.toString(),
                "Apps blocked"
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

    private fun buildQuickSettings(): View {
        val card = FocusUi.card(activity, tokens)
        val grid = FocusUi.row(activity)
        grid.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val entries = listOf(
            "Wi-Fi" to Intent(Settings.ACTION_WIFI_SETTINGS),
            "Data" to Intent("android.settings.MOBILE_NETWORK_SETTINGS"),
            "Bluetooth" to Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            "Sound" to Intent(Settings.ACTION_SOUND_SETTINGS),
            "Hotspot" to Intent("android.settings.TETHER_SETTINGS")
        )
        entries.forEach { entry ->
            grid.addView(FocusUi.smallButton(activity, tokens, entry.first) { openSetting(entry.second) })
        }

        val scroll = FocusUi.horizontalScroll(activity, grid)
        scroll.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        card.addView(scroll)
        return card
    }
}
